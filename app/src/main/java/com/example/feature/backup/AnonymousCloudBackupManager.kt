package com.example.feature.backup

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.core.auth.GuestCloudIdentityManager
import com.example.core.subscription.LicenseKeyManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Dynamic quotas configuration loaded from Firestore (system_config/app_limits).
 * Changeable anytime from the Web Admin Dashboard.
 */
data class CloudQuotaLimits(
    val freeMaxRecordings: Int = 5,
    val freeStorageLimitBytes: Long = 500L * 1024L * 1024L, // 500 MB
    val premiumMaxRecordings: Int = 1000,
    val premiumStorageLimitBytes: Long = 50L * 1024L * 1024L * 1024L // 50 GB
)

/**
 * Quota state model tracking cloud recordings count and consumed storage bytes.
 */
data class CloudQuotaState(
    val uploadedCount: Int = 0,
    val maxQuota: Int = 5,
    val usedBytes: Long = 0L,
    val maxStorageBytes: Long = 500L * 1024L * 1024L,
    val isQuotaExceeded: Boolean = false,
    val isStorageExceeded: Boolean = false,
    val isUploading: Boolean = false,
    val currentUploadProgress: Float = 0f,
    val lastUploadedFileName: String? = null,
    val lastErrorMessage: String? = null,
    val lastSuccessMessage: String? = null
)

/**
 * Guest and User Cloud Recording Manager.
 * Features:
 * - Invisible Anonymous Auth via [GuestCloudIdentityManager]
 * - Server-enforced 5 recording quota & 500MB storage limit for Free users
 * - Dynamic quota limits controlled remotely via Web Admin Panel (system_config/app_limits)
 * - Path: guest_recordings/{anonymousAccountReference}/{recordingId}.mp4
 * - Safe offline handling: Local recording is ALWAYS preserved if quota is full or upload fails.
 */
object AnonymousCloudBackupManager {

    private const val TAG = "GuestCloudBackup"
    private const val PREFS_NAME = "vault_cloud_backup_prefs"
    private const val KEY_AUTO_UPLOAD = "auto_cloud_upload_enabled"
    private const val KEY_DEVICE_ID = "guest_device_unique_id"

    const val DEFAULT_MAX_FREE_RECORDINGS_QUOTA = 5
    const val DEFAULT_MAX_FREE_STORAGE_BYTES = 500L * 1024L * 1024L // 500 MB

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _quotaState = MutableStateFlow(CloudQuotaState())
    val quotaState: StateFlow<CloudQuotaState> = _quotaState.asStateFlow()

    private var cachedLimits = CloudQuotaLimits()
    private var lastLimitsFetchTime = 0L

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val storage by lazy {
        try {
            FirebaseStorage.getInstance("gs://sleathcam1.firebasestorage.app")
        } catch (e: Exception) {
            FirebaseStorage.getInstance()
        }
    }

    /**
     * Fetches dynamic limits set by Admin from Firestore (cached for 60 seconds).
     */
    suspend fun getDynamicLimits(): CloudQuotaLimits {
        val now = System.currentTimeMillis()
        if (now - lastLimitsFetchTime < 60_000L && lastLimitsFetchTime > 0L) {
            return cachedLimits
        }
        return try {
            val doc = firestore.collection("system_config").document("app_limits").get().await()
            if (doc.exists()) {
                val freeCount = doc.getLong("freeMaxRecordings")?.toInt() ?: DEFAULT_MAX_FREE_RECORDINGS_QUOTA
                val freeBytes = doc.getLong("freeStorageLimitBytes") ?: DEFAULT_MAX_FREE_STORAGE_BYTES
                val premCount = doc.getLong("premiumMaxRecordings")?.toInt() ?: 1000
                val premBytes = doc.getLong("premiumStorageLimitBytes") ?: (50L * 1024L * 1024L * 1024L)
                cachedLimits = CloudQuotaLimits(freeCount, freeBytes, premCount, premBytes)
                lastLimitsFetchTime = now
            }
            cachedLimits
        } catch (_: Exception) {
            cachedLimits
        }
    }

    /**
     * Obtains or generates a stable unique hardware/app device ID.
     */
    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId.isNullOrBlank()) {
            deviceId = "dev_" + UUID.randomUUID().toString().replace("-", "").take(16)
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    fun isAutoUploadEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_UPLOAD, true)
    }

    fun setAutoUploadEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_UPLOAD, enabled).apply()
    }

    /**
     * Ensures an authenticated anonymous Firebase session exists.
     */
    suspend fun ensureAuthenticated(context: Context? = null): String {
        return GuestCloudIdentityManager.getOrCreateGuestIdentity(context)
    }

    /**
     * Refreshes the count of active recordings currently stored in the cloud for this guest/user.
     * Checks Firestore authoritatively and computes total storage consumed.
     */
    suspend fun refreshCloudQuota(context: Context): CloudQuotaState = withContext(Dispatchers.IO) {
        try {
            val uid = ensureAuthenticated(context)
            val deviceId = getDeviceId(context)
            val isPremium = LicenseKeyManager.isCachedPremium(context)
            val limits = getDynamicLimits()

            // Authoritative query by anonymousAccountReference or deviceId
            val querySnapshot = try {
                firestore.collection("cloud_recordings")
                    .whereEqualTo("anonymousAccountReference", uid)
                    .whereEqualTo("status", "ACTIVE")
                    .get()
                    .await()
            } catch (e: Exception) {
                firestore.collection("cloud_recordings")
                    .whereEqualTo("deviceId", deviceId)
                    .whereEqualTo("status", "ACTIVE")
                    .get()
                    .await()
            }

            val count = querySnapshot.size()
            var totalBytes = 0L
            for (doc in querySnapshot.documents) {
                val b = doc.getLong("fileSize") ?: doc.getLong("sizeBytes") ?: 0L
                totalBytes += b
            }

            val maxQuota = if (isPremium) limits.premiumMaxRecordings else limits.freeMaxRecordings
            val maxBytes = if (isPremium) limits.premiumStorageLimitBytes else limits.freeStorageLimitBytes

            val countExceeded = !isPremium && count >= maxQuota
            val storageExceeded = !isPremium && totalBytes >= maxBytes

            val state = CloudQuotaState(
                uploadedCount = count,
                maxQuota = maxQuota,
                usedBytes = totalBytes,
                maxStorageBytes = maxBytes,
                isQuotaExceeded = countExceeded || storageExceeded,
                isStorageExceeded = storageExceeded
            )
            _quotaState.value = state
            return@withContext state
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query authoritative cloud quota: ${e.message}")
            return@withContext _quotaState.value
        }
    }

    /**
     * Non-blocking background trigger to upload a finalized recording.
     */
    fun uploadRecordingAsync(
        context: Context,
        mediaId: String,
        videoFile: File,
        fileName: String,
        durationMs: Long
    ) {
        if (!isAutoUploadEnabled(context)) {
            Log.d(TAG, "Auto upload is disabled in settings. Skipping cloud upload for $mediaId")
            return
        }

        scope.launch {
            uploadRecording(context, mediaId, videoFile, fileName, durationMs)
        }
    }

    /**
     * Primary upload function. Checks quota (<= 5 recordings & <= 500 MB for Free users),
     * uploads the video to Firebase Storage, and saves document in Firestore 'cloud_recordings'.
     * If limit is reached, local recording is safely preserved.
     */
    suspend fun uploadRecording(
        context: Context,
        mediaId: String,
        videoFile: File,
        fileName: String,
        durationMs: Long
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!videoFile.exists() || videoFile.length() <= 0) {
            val err = "Video file does not exist or is empty"
            Log.e(TAG, err)
            return@withContext Result.failure(Exception(err))
        }

        try {
            val uid = ensureAuthenticated(context)
            val deviceId = getDeviceId(context)
            val isPremium = LicenseKeyManager.isCachedPremium(context)
            val limits = getDynamicLimits()
            val ownerType = if (isPremium) "PREMIUM" else GuestCloudIdentityManager.currentOwnerType
            val ownerEmail = GuestCloudIdentityManager.currentOwnerEmail

            // Authoritative server-side quota check
            val currentQuota = refreshCloudQuota(context)
            val maxCount = if (isPremium) limits.premiumMaxRecordings else limits.freeMaxRecordings
            val maxStorage = if (isPremium) limits.premiumStorageLimitBytes else limits.freeStorageLimitBytes

            if (!isPremium) {
                if (currentQuota.uploadedCount >= maxCount) {
                    val quotaErrMsg = "Free cloud limit reached (${currentQuota.uploadedCount}/$maxCount recordings). Upgrade to Premium or delete older recordings to upload new ones."
                    Log.w(TAG, quotaErrMsg)
                    _quotaState.value = _quotaState.value.copy(
                        isQuotaExceeded = true,
                        lastErrorMessage = quotaErrMsg
                    )
                    return@withContext Result.failure(Exception(quotaErrMsg))
                }

                if (currentQuota.usedBytes + videoFile.length() > maxStorage) {
                    val storageMb = maxStorage / (1024 * 1024)
                    val quotaErrMsg = "Free cloud storage limit reached (${storageMb} MB). Upgrade to Premium or delete older recordings to free space."
                    Log.w(TAG, quotaErrMsg)
                    _quotaState.value = _quotaState.value.copy(
                        isQuotaExceeded = true,
                        isStorageExceeded = true,
                        lastErrorMessage = quotaErrMsg
                    )
                    return@withContext Result.failure(Exception(quotaErrMsg))
                }
            }

            _quotaState.value = _quotaState.value.copy(
                isUploading = true,
                currentUploadProgress = 0.1f,
                lastErrorMessage = null
            )

            // Storage Path: guest_recordings/{anonymousAccountReference}/{recordingId}.mp4
            val storagePath = "guest_recordings/$uid/$mediaId.mp4"
            val storageRef = storage.reference.child(storagePath)

            val metadata = StorageMetadata.Builder()
                .setContentType("video/mp4")
                .setCustomMetadata("recordingId", mediaId)
                .setCustomMetadata("anonymousAccountReference", uid)
                .setCustomMetadata("deviceId", deviceId)
                .setCustomMetadata("durationMs", durationMs.toString())
                .setCustomMetadata("originalName", fileName)
                .setCustomMetadata("isPremium", isPremium.toString())
                .build()

            Log.d(TAG, "Uploading ${videoFile.name} (${videoFile.length()} bytes) to $storagePath...")
            val uploadTask = storageRef.putFile(android.net.Uri.fromFile(videoFile), metadata)

            uploadTask.addOnProgressListener { taskSnapshot ->
                if (taskSnapshot.totalByteCount > 0) {
                    val progress = taskSnapshot.bytesTransferred.toFloat() / taskSnapshot.totalByteCount.toFloat()
                    _quotaState.value = _quotaState.value.copy(currentUploadProgress = progress)
                }
            }

            uploadTask.await()

            val downloadUrl = try {
                storageRef.downloadUrl.await().toString()
            } catch (e: Exception) {
                ""
            }

            val now = System.currentTimeMillis()
            val docData = mapOf(
                "recordingId" to mediaId,
                "id" to mediaId,
                "mediaId" to mediaId,
                "ownerType" to ownerType,
                "ownerEmail" to ownerEmail,
                "anonymousAccountReference" to uid,
                "userId" to uid,
                "deviceId" to deviceId,
                "deviceModel" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "androidVersion" to Build.VERSION.RELEASE,
                "fileName" to fileName,
                "fileSize" to videoFile.length(),
                "sizeBytes" to videoFile.length(),
                "duration" to durationMs,
                "durationMs" to durationMs,
                "mimeType" to "video/mp4",
                "cloudStoragePath" to storagePath,
                "storagePath" to storagePath,
                "downloadUrl" to downloadUrl,
                "isPremium" to isPremium,
                "status" to "ACTIVE",
                "createdAt" to now,
                "updatedAt" to now
            )

            firestore.collection("cloud_recordings")
                .document(mediaId)
                .set(docData, SetOptions.merge())
                .await()

            // Also mirror under user's private media collection
            try {
                firestore.collection("users")
                    .document(uid)
                    .collection("vault_media")
                    .document(mediaId)
                    .set(docData, SetOptions.merge())
                    .await()
            } catch (_: Exception) {}

            Log.d(TAG, "Successfully backed up recording $mediaId to cloud! URL: $downloadUrl")

            val newCount = currentQuota.uploadedCount + 1
            val newBytes = currentQuota.usedBytes + videoFile.length()
            val isCountNowExceeded = !isPremium && newCount >= maxCount
            val isStorageNowExceeded = !isPremium && newBytes >= maxStorage

            _quotaState.value = CloudQuotaState(
                uploadedCount = newCount,
                maxQuota = maxCount,
                usedBytes = newBytes,
                maxStorageBytes = maxStorage,
                isQuotaExceeded = isCountNowExceeded || isStorageNowExceeded,
                isStorageExceeded = isStorageNowExceeded,
                isUploading = false,
                currentUploadProgress = 1.0f,
                lastUploadedFileName = fileName,
                lastSuccessMessage = "Uploaded to Cloud ($newCount/$maxCount)."
            )

            return@withContext Result.success(downloadUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Cloud upload error: ${e.message}", e)
            _quotaState.value = _quotaState.value.copy(
                isUploading = false,
                lastErrorMessage = "Cloud upload failed: ${e.message?.take(90)}"
            )
            return@withContext Result.failure(e)
        }
    }

    /**
     * Delete a recording from the Cloud (both Storage and Firestore).
     */
    suspend fun deleteCloudRecording(mediaId: String, storagePath: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!storagePath.isNullOrBlank()) {
                try {
                    storage.reference.child(storagePath).delete().await()
                } catch (e: Exception) {
                    Log.w(TAG, "Storage deletion notice for $storagePath: ${e.message}")
                }
            }

            firestore.collection("cloud_recordings")
                .document(mediaId)
                .delete()
                .await()

            Log.d(TAG, "Deleted cloud recording $mediaId")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete cloud recording $mediaId: ${e.message}")
            return@withContext false
        }
    }
}
