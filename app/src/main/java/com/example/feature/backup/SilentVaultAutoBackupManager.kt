package com.example.feature.backup

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.data.local.VaultDatabase
import com.example.data.storage.VaultStorageManager
import com.example.feature.media.VaultMediaItem
import com.example.integration.cloudinary.CloudinaryServiceImpl
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Silent Background Auto-Backup Manager.
 * Features:
 * 1. Silently uploads newly added photos/videos to Cloudinary or Firebase in the background.
 * 2. Does NOT show any blocking UI or slow down the app.
 * 3. When the user later taps "Backup Now" in the UI, all silently uploaded media completes
 *    instantly with a smooth 1-1.5s visual progress animation ("Instant Backup Magic").
 * 4. Deleted items are marked in the 15-Day Shadow Archive so they remain recoverable for premium users.
 */
object SilentVaultAutoBackupManager {

    private const val TAG = "SilentAutoBackup"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun triggerSilentBackup(context: Context, items: List<VaultMediaItem>) {
        if (items.isEmpty()) return
        scope.launch {
            try {
                val auth = FirebaseAuth.getInstance()
                val user = auth.currentUser
                val uid = user?.uid ?: AnonymousCloudBackupManager.ensureAuthenticated()

                // Check Cloudinary remote config
                val firestore = FirebaseFirestore.getInstance()
                val remoteDoc = try {
                    firestore.collection("system_config").document("cloudinary").get().await()
                } catch (_: Exception) {
                    null
                }

                val hasCloudinary = remoteDoc?.exists() == true && 
                    !(remoteDoc.getString("cloudName").isNullOrBlank())

                val cloudinaryService = CloudinaryServiceImpl(context.applicationContext)

                for (item in items) {
                    try {
                        // Check if already backed up in Firestore
                        val existingDoc = firestore.collection("users")
                            .document(uid)
                            .collection("vault_media")
                            .document(item.id)
                            .get()
                            .await()

                        if (existingDoc.exists() && existingDoc.getString("backupStatus") == "SUCCESS") {
                            // Already backed up silently
                            continue
                        }

                        if (hasCloudinary) {
                            val rawBytes = withContext(Dispatchers.IO) {
                                item.file.readBytes()
                            }
                            val (fileBytes, uploadMimeType) = compressIfNeeded(rawBytes, item.mimeType)
                            val uploadSizeBytes = fileBytes.size.toLong()

                            val uploadResult = cloudinaryService.uploadMedia(
                                mediaId = item.id,
                                fileBytes = fileBytes,
                                mimeType = uploadMimeType,
                                fileName = item.fileName
                            )

                            if (uploadResult.isSuccess) {
                                val result = uploadResult.getOrThrow()
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
                                    "isDeletedByUser" to false,
                                    "visibility" to "VISIBLE",
                                    "updatedAtEpochMs" to System.currentTimeMillis()
                                )
                                firestore.collection("users")
                                    .document(uid)
                                    .collection("vault_media")
                                    .document(item.id)
                                    .set(metadataMap, SetOptions.merge())
                                    .await()
                            }
                        } else {
                            // Fallback to anonymous cloud quota if video
                            if (item.mediaType.name == "VIDEO") {
                                AnonymousCloudBackupManager.uploadRecording(
                                    context = context,
                                    mediaId = item.id,
                                    videoFile = item.file,
                                    fileName = item.fileName,
                                    durationMs = item.durationMs
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Silent upload skipped for item ${item.fileName}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Silent backup error: ${e.message}")
            }
        }
    }

    private fun compressIfNeeded(rawBytes: ByteArray, mimeType: String): Pair<ByteArray, String> {
        if (!mimeType.startsWith("image/", ignoreCase = true)) {
            return Pair(rawBytes, mimeType)
        }
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOptions)
            var sampleSize = 1
            while ((boundsOptions.outWidth / sampleSize) > 1920 || (boundsOptions.outHeight / sampleSize) > 1920) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions)
                ?: return Pair(rawBytes, mimeType)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
            bitmap.recycle()
            val compressed = stream.toByteArray()
            if (compressed.size < rawBytes.size) Pair(compressed, "image/jpeg") else Pair(rawBytes, mimeType)
        } catch (_: Exception) {
            Pair(rawBytes, mimeType)
        }
    }
}
