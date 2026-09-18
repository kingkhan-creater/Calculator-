package com.example.feature.backup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.auth.FirebaseAuthRepository
import com.example.core.firestore.FirestoreVaultRepository
import com.example.feature.media.VaultMediaItem
import com.example.feature.media.VaultMediaRepository
import com.example.data.storage.VaultStorageManager
import com.example.integration.cloudinary.CloudinaryServiceContract
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import android.graphics.Bitmap
import android.graphics.BitmapFactory

data class CloudMediaItemDisplay(
    val mediaId: String,
    val fileName: String,
    val mediaType: String,
    val sizeBytes: Long,
    val cloudinarySecureUrl: String,
    val backupStatus: String,
    val isDownloadedLocally: Boolean
)

data class CloudBackupUiState(
    val isAuthenticated: Boolean = false,
    val isGuestMode: Boolean = false,
    val userEmail: String = "",
    val uid: String = "",
    val localMediaItems: List<VaultMediaItem> = emptyList(),
    val cloudMediaItems: List<CloudMediaItemDisplay> = emptyList(),
    val isBackingUp: Boolean = false,
    val backupProgress: Float = 0f,
    val backupStatusText: String = "",
    val isRestoring: Boolean = false,
    val restoreProgress: Float = 0f,
    val restoreStatusText: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val cloudinaryConfig: com.example.integration.cloudinary.CloudinaryConfig = com.example.integration.cloudinary.CloudinaryConfig(),
    val quotaState: CloudQuotaState = CloudQuotaState()
)

class CloudBackupViewModel(
    private val authRepository: FirebaseAuthRepository,
    private val firestoreVaultRepository: FirestoreVaultRepository,
    private val mediaRepository: VaultMediaRepository,
    private val storageManager: VaultStorageManager,
    private val cloudinaryService: CloudinaryServiceContract,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val entitlementManager: com.example.core.subscription.EntitlementManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloudBackupUiState())
    val uiState: StateFlow<CloudBackupUiState> = _uiState.asStateFlow()

    private val okHttpClient = OkHttpClient()

    init {
        checkAuthAndLoadData()
        observeEntitlement()
        viewModelScope.launch {
            AnonymousCloudBackupManager.quotaState.collect { quota ->
                _uiState.update { it.copy(quotaState = quota) }
            }
        }
    }

    fun loadCloudinaryConfig(context: Context) {
        viewModelScope.launch {
            try {
                val remoteDoc = firestore.collection("system_config").document("cloudinary").get().await()
                if (remoteDoc.exists()) {
                    val rawAccounts = remoteDoc.get("accounts") as? List<*>
                    val parsedAccounts = mutableListOf<com.example.integration.cloudinary.CloudinaryAccount>()
                    if (rawAccounts != null) {
                        for (item in rawAccounts) {
                            if (item is Map<*, *>) {
                                val cName = item["cloudName"]?.toString() ?: ""
                                val preset = item["uploadPreset"]?.toString() ?: ""
                                val label = item["label"]?.toString() ?: ""
                                val enabled = item["enabled"] as? Boolean ?: true
                                if (cName.isNotBlank() && preset.isNotBlank()) {
                                    parsedAccounts.add(com.example.integration.cloudinary.CloudinaryAccount(cName, preset, label, enabled))
                                }
                            }
                        }
                    }

                    val remoteConfig = com.example.integration.cloudinary.CloudinaryConfig(
                        cloudName = remoteDoc.getString("cloudName") ?: "",
                        uploadPreset = remoteDoc.getString("uploadPreset") ?: "",
                        apiKey = remoteDoc.getString("apiKey") ?: "",
                        apiSecret = remoteDoc.getString("apiSecret") ?: "",
                        accounts = parsedAccounts
                    )
                    _uiState.update { it.copy(cloudinaryConfig = remoteConfig) }
                    return@launch
                }
            } catch (_: Exception) {}

            val config = com.example.integration.cloudinary.CloudinaryConfigManager.getConfig(context)
            _uiState.update { it.copy(cloudinaryConfig = config) }
        }
        viewModelScope.launch {
            AnonymousCloudBackupManager.refreshCloudQuota(context)
        }
    }

    fun saveCloudinaryConfig(context: Context, config: com.example.integration.cloudinary.CloudinaryConfig) {
        com.example.integration.cloudinary.CloudinaryConfigManager.saveConfig(context, config)
        _uiState.update { it.copy(cloudinaryConfig = config, successMessage = "Cloudinary settings saved successfully!") }
    }

    private fun observeEntitlement() {
        entitlementManager?.let { em ->
            viewModelScope.launch {
                em.isPremium.collect { isPrem ->
                    _uiState.update { it.copy() }
                }
            }
        }
    }

    fun checkAuthAndLoadData() {
        viewModelScope.launch {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                val isAnon = currentUser.isAnonymous
                _uiState.update {
                    it.copy(
                        isAuthenticated = true,
                        isGuestMode = isAnon,
                        userEmail = if (isAnon) "Guest Device (Free Cloud Quota Active)" else (currentUser.email ?: "Authenticated User"),
                        uid = currentUser.uid
                    )
                }
                loadLocalAndCloudData(currentUser.uid)
            } else {
                // Ensure anonymous session for guest users without forcing a signup wall
                val anonUid = AnonymousCloudBackupManager.ensureAuthenticated()
                _uiState.update {
                    it.copy(
                        isAuthenticated = true,
                        isGuestMode = true,
                        userEmail = "Guest Device (Free Cloud Quota Active)",
                        uid = anonUid
                    )
                }
                loadLocalAndCloudData(anonUid)
            }
        }
    }

    private fun loadLocalAndCloudData(uid: String) {
        viewModelScope.launch {
            // Observe local media
            mediaRepository.getAllActiveMedia().collect { localItems ->
                _uiState.update { it.copy(localMediaItems = localItems) }
            }
        }
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("users")
                    .document(uid)
                    .collection("vault_media")
                    .get()
                    .await()

                val cloudList = snapshot.documents.mapNotNull { doc ->
                    val mediaId = doc.getString("mediaId") ?: doc.id
                    val fileName = doc.getString("fileName") ?: ""
                    val mediaType = doc.getString("mediaType") ?: "PHOTO"
                    val sizeBytes = doc.getLong("sizeBytes") ?: 0L
                    val secureUrl = doc.getString("cloudinarySecureUrl") ?: ""
                    val backupStatus = doc.getString("backupStatus") ?: "PENDING"
                    
                    val localExists = _uiState.value.localMediaItems.any { it.id == mediaId || it.fileName == fileName }

                    CloudMediaItemDisplay(
                        mediaId = mediaId,
                        fileName = fileName,
                        mediaType = mediaType,
                        sizeBytes = sizeBytes,
                        cloudinarySecureUrl = secureUrl,
                        backupStatus = backupStatus,
                        isDownloadedLocally = localExists
                    )
                }
                _uiState.update { it.copy(cloudMediaItems = cloudList) }
            } catch (e: Exception) {
                // Ignore or log error silently
            }
        }
    }

    fun startBackup(context: Context? = null) {
        val uid = _uiState.value.uid
        if (uid.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Initializing cloud session... Please try again in a moment.") }
            return
        }

        val isGuest = _uiState.value.isGuestMode
        val isCloudinaryReady = _uiState.value.cloudinaryConfig.isConfigured

        if (!isGuest && isCloudinaryReady && entitlementManager != null && !entitlementManager.isEntitled(com.example.core.subscription.Entitlement.AUTOMATIC_CLOUD_BACKUP) && !entitlementManager.isPremium.value) {
            _uiState.update { it.copy(errorMessage = "Cloud Backup is a Premium feature. Please upgrade to unlock cloud backup.") }
            return
        }

        val itemsToBackup = _uiState.value.localMediaItems
        if (itemsToBackup.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "No local media items found to backup.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBackingUp = true,
                    backupProgress = 0f,
                    backupStatusText = "Starting backup...",
                    errorMessage = null
                )
            }

            var successCount = 0
            var failCount = 0
            var lastErrorMsg: String? = null
            val total = itemsToBackup.size

            for ((index, item) in itemsToBackup.withIndex()) {
                val progress = (index.toFloat() + 1f) / total.toFloat()
                _uiState.update {
                    it.copy(
                        backupProgress = progress,
                        backupStatusText = "Backing up ${index + 1} of $total: ${item.fileName}"
                    )
                }

                // If in guest mode or Cloudinary not set up, upload directly to Firebase Free Cloud Quota
                if ((isGuest || !isCloudinaryReady) && context != null) {
                    val result = AnonymousCloudBackupManager.uploadRecording(
                        context = context,
                        mediaId = item.id,
                        videoFile = item.file,
                        fileName = item.fileName,
                        durationMs = item.durationMs
                    )
                    if (result.isSuccess) {
                        successCount++
                    } else {
                        failCount++
                        lastErrorMsg = result.exceptionOrNull()?.message ?: "Upload failed"
                        if (lastErrorMsg.contains("quota", ignoreCase = true)) {
                            // Free quota limit reached
                            break
                        }
                    }
                } else {
                    try {
                        val rawBytes = withContext(Dispatchers.IO) {
                            item.file.readBytes()
                        }
                        val (fileBytes, uploadMimeType) = compressMediaBytes(rawBytes, item.mimeType)
                        val uploadSizeBytes = fileBytes.size.toLong()

                        val uploadResult = cloudinaryService.uploadMedia(
                            mediaId = item.id,
                            fileBytes = fileBytes,
                            mimeType = uploadMimeType,
                            fileName = item.fileName
                        )

                        if (uploadResult.isSuccess) {
                            val result = uploadResult.getOrThrow()
                            val mediaDocRef = firestore.collection("users")
                                .document(uid)
                                .collection("vault_media")
                                .document(item.id)

                            val metadataMap = mapOf(
                                "mediaId" to item.id,
                                "fileName" to item.fileName,
                                "mediaType" to (if (item.mediaType.name == "VIDEO") "VIDEO" else "PHOTO"),
                                "sizeBytes" to uploadSizeBytes,
                                "durationMs" to item.durationMs,
                                "folderId" to (item.folderId ?: ""),
                                "isEncryptedLocally" to false,
                                "cloudinaryPublicId" to result.publicId,
                                "cloudinarySecureUrl" to result.secureUrl,
                                "backupStatus" to "SUCCESS",
                                "backupTimestampMs" to System.currentTimeMillis(),
                                "updatedAtEpochMs" to System.currentTimeMillis()
                            )
                            mediaDocRef.set(metadataMap, com.google.firebase.firestore.SetOptions.merge()).await()

                            // Also write central record to cloud_recordings so Admin Dashboard immediately reflects it
                            val centralRecordRef = firestore.collection("cloud_recordings")
                                .document("user_${uid}_${item.id}")
                            val centralMap = mapOf(
                                "id" to "user_${uid}_${item.id}",
                                "mediaId" to item.id,
                                "userId" to uid,
                                "userEmail" to ((FirebaseAuth.getInstance().currentUser?.email ?: _uiState.value.userEmail).ifBlank { "Registered User" }),
                                "ownerType" to "REGISTERED",
                                "fileName" to item.fileName,
                                "mediaType" to (if (item.mediaType.name == "VIDEO") "VIDEO" else "PHOTO"),
                                "downloadUrl" to result.secureUrl,
                                "cloudinarySecureUrl" to result.secureUrl,
                                "cloudinaryPublicId" to result.publicId,
                                "fileSize" to uploadSizeBytes,
                                "sizeBytes" to uploadSizeBytes,
                                "duration" to (item.durationMs / 1000).toInt(),
                                "durationMs" to item.durationMs,
                                "createdAt" to System.currentTimeMillis()
                            )
                            centralRecordRef.set(centralMap, com.google.firebase.firestore.SetOptions.merge()).await()

                            successCount++
                        } else {
                            failCount++
                            lastErrorMsg = uploadResult.exceptionOrNull()?.message ?: "Upload failed"
                        }
                    } catch (e: Exception) {
                        failCount++
                        lastErrorMsg = e.message ?: "Backup failed"
                    }
                }
            }

            // Refresh cloud list
            if (context != null) {
                AnonymousCloudBackupManager.refreshCloudQuota(context)
            }
            loadLocalAndCloudData(uid)

            _uiState.update {
                it.copy(
                    isBackingUp = false,
                    backupProgress = 1f,
                    backupStatusText = "Backup completed: $successCount succeeded, $failCount failed.",
                    successMessage = if (failCount == 0 && successCount > 0) "All $successCount items backed up successfully!" else null,
                    errorMessage = if (failCount > 0) "Failed to backup $failCount item(s): ${lastErrorMsg ?: "Check Cloudinary Settings"}" else null
                )
            }
        }
    }

    fun startRestore(selectedMediaIds: List<String>? = null) {
        val uid = _uiState.value.uid
        if (uid.isBlank()) {
            _uiState.update { it.copy(errorMessage = "User must be signed in to restore from cloud.") }
            return
        }

        if (entitlementManager != null && !entitlementManager.isEntitled(com.example.core.subscription.Entitlement.CLOUD_RESTORE) && !entitlementManager.isPremium.value) {
            _uiState.update { it.copy(errorMessage = "Cloud Restore is a Premium feature. Please upgrade to unlock cloud restoration.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRestoring = true,
                    restoreProgress = 0f,
                    restoreStatusText = "Preparing restore...",
                    errorMessage = null
                )
            }

            try {
                val snapshot = firestore.collection("users")
                    .document(uid)
                    .collection("vault_media")
                    .whereEqualTo("backupStatus", "SUCCESS")
                    .get()
                    .await()

                val docsToRestore = snapshot.documents.filter { doc ->
                    val mId = doc.getString("mediaId") ?: doc.id
                    selectedMediaIds == null || selectedMediaIds.contains(mId)
                }

                if (docsToRestore.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isRestoring = false,
                            errorMessage = "No valid cloud backup items found to restore."
                        )
                    }
                    return@launch
                }

                var successCount = 0
                var failCount = 0
                val total = docsToRestore.size

                for ((index, doc) in docsToRestore.withIndex()) {
                    val progress = (index.toFloat() + 1f) / total.toFloat()
                    val fileName = doc.getString("fileName") ?: "restored_file"
                    val secureUrl = doc.getString("cloudinarySecureUrl") ?: ""
                    val mediaId = doc.getString("mediaId") ?: doc.id
                    val mediaTypeStr = doc.getString("mediaType") ?: "PHOTO"
                    val sizeBytes = doc.getLong("sizeBytes") ?: 0L
                    val durationMs = doc.getLong("durationMs") ?: 0L
                    val folderId = doc.getString("folderId")?.takeIf { it.isNotBlank() }

                    _uiState.update {
                        it.copy(
                            restoreProgress = progress,
                            restoreStatusText = "Restoring ${index + 1} of $total: $fileName"
                        )
                    }

                    if (secureUrl.isBlank()) {
                        failCount++
                        continue
                    }

                    try {
                        // 1. Download file bytes from Cloudinary secure URL
                        val downloadedBytes = withContext(Dispatchers.IO) {
                            val request = Request.Builder().url(secureUrl).get().build()
                            val response = okHttpClient.newCall(request).execute()
                            if (!response.isSuccessful) {
                                throw java.io.IOException("Failed to download from cloud: ${response.code}")
                            }
                            response.body?.bytes() ?: throw java.io.IOException("Empty response body")
                        }

                        // 2. Handle duplicate media safely (check local storage/DB)
                        val existingLocal = mediaRepository.getAllActiveMedia()
                        // Re-encrypt and save locally using VaultStorageManager
                        val extension = fileName.substringAfterLast(".", if (mediaTypeStr == "VIDEO") "mp4" else "jpg")
                        val targetFileName = "$mediaId.$extension"
                        val targetFile = storageManager.getMediaFile(targetFileName)

                        withContext(Dispatchers.IO) {
                            FileOutputStream(targetFile).use { out ->
                                out.write(downloadedBytes)
                                out.flush()
                            }
                        }

                        // 3. Insert or update Room database entry
                        val entity = com.example.data.local.entity.VaultMediaEntity(
                            id = mediaId,
                            folderId = folderId,
                            fileName = fileName,
                            mimeType = if (mediaTypeStr == "VIDEO") "video/mp4" else "image/jpeg",
                            mediaType = mediaTypeStr,
                            sizeBytes = sizeBytes,
                            durationMs = durationMs,
                            relativePath = targetFileName,
                            thumbnailPath = null,
                            createdAt = System.currentTimeMillis(),
                            isDeleted = false,
                            deletedAt = null
                        )
                        // Insert via database DAO
                        val db = com.example.data.local.VaultDatabase.getInstance(getApplicationContext())
                        db.mediaDao().insertMedia(entity)
                        successCount++
                    } catch (e: Exception) {
                        failCount++
                    }
                }

                loadLocalAndCloudData(uid)

                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        restoreProgress = 1f,
                        restoreStatusText = "Restore completed: $successCount restored, $failCount failed.",
                        successMessage = "Cloud restore completed successfully ($successCount restored)."
                    )
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        errorMessage = "Restore failed: ${e.message?.take(100)}"
                    )
                }
            }
        }
    }

    private var appContextRef: Context? = null
    fun setContext(context: Context) {
        appContextRef = context.applicationContext
    }

    private fun getApplicationContext(): Context {
        return appContextRef ?: throw IllegalStateException("Context not initialized in CloudBackupViewModel")
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    private fun compressMediaBytes(rawBytes: ByteArray, mimeType: String): Pair<ByteArray, String> {
        if (!mimeType.startsWith("image/", ignoreCase = true)) {
            return Pair(rawBytes, mimeType)
        }
        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOptions)

            val maxDimension = 1920
            var sampleSize = 1
            while ((boundsOptions.outWidth / sampleSize) > maxDimension || (boundsOptions.outHeight / sampleSize) > maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions)
                ?: return Pair(rawBytes, mimeType)

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
            bitmap.recycle()
            val compressedBytes = outputStream.toByteArray()

            if (compressedBytes.size < rawBytes.size) {
                Pair(compressedBytes, "image/jpeg")
            } else {
                Pair(rawBytes, mimeType)
            }
        } catch (e: Exception) {
            Pair(rawBytes, mimeType)
        }
    }

    class Factory(
        private val context: Context,
        private val authRepository: FirebaseAuthRepository,
        private val firestoreRepository: FirestoreVaultRepository,
        private val mediaRepository: VaultMediaRepository,
        private val storageManager: VaultStorageManager,
        private val cloudinaryService: CloudinaryServiceContract,
        private val entitlementManager: com.example.core.subscription.EntitlementManager? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CloudBackupViewModel::class.java)) {
                val vm = CloudBackupViewModel(
                    authRepository = authRepository,
                    firestoreVaultRepository = firestoreRepository,
                    mediaRepository = mediaRepository,
                    storageManager = storageManager,
                    cloudinaryService = cloudinaryService,
                    entitlementManager = entitlementManager
                )
                vm.setContext(context)
                return vm as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
