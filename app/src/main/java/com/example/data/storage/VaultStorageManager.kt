package com.example.data.storage

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.core.security.VaultFileEncryptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ImportedMediaFileResult(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val mediaType: String, // "PHOTO" or "VIDEO"
    val sizeBytes: Long,
    val durationMs: Long,
    val relativePath: String,
    val thumbnailRelativePath: String?
)

data class MediaExportTarget(
    val id: String = "",
    val relativePath: String,
    val fileName: String,
    val mimeType: String,
    val mediaType: String // "PHOTO" or "VIDEO"
)

data class GalleryExportSummary(
    val successCount: Int,
    val failedCount: Int,
    val exportedUris: List<Uri>,
    val exportedIds: List<String> = emptyList(),
    val exportedPaths: List<String> = emptyList()
)

class VaultStorageManager(
    private val context: Context,
    private val fileEncryptor: VaultFileEncryptor = VaultFileEncryptor()
) {

    init {
        ensureVaultDirectory()
    }

    private fun ensureVaultDirectory(): File {
        val dir = File(context.filesDir, "vault_media")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val noMedia = File(dir, ".nomedia")
        if (!noMedia.exists()) {
            try {
                noMedia.createNewFile()
            } catch (_: Exception) {}
        }
        return dir
    }

    private val vaultDirectory: File by lazy {
        ensureVaultDirectory()
    }

    private val exportDirectory: File by lazy {
        val dir = File(context.filesDir, "exports")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    private val tempPlaybackDirectory: File by lazy {
        val dir = File(context.cacheDir, "temp_vault_playback")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    fun getMediaFile(relativePath: String): File {
        return File(vaultDirectory, relativePath)
    }

    fun getThumbnailFile(thumbnailRelativePath: String?): File? {
        if (thumbnailRelativePath == null) return null
        val file = File(vaultDirectory, thumbnailRelativePath)
        return if (file.exists()) file else null
    }

    fun isFileEncrypted(file: File): Boolean {
        return fileEncryptor.isEncrypted(file)
    }

    /**
     * Safe import flow into private app-internal sandbox storage with .nomedia protection.
     * High performance direct streaming without heavy CPU encryption overhead.
     */
    suspend fun importMedia(uri: Uri, folderId: String? = null): ImportedMediaFileResult = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: getMimeTypeFromUri(uri)
        val isVideo = mimeType.startsWith("video/")
        val mediaType = if (isVideo) "VIDEO" else "PHOTO"

        val originalFileName = queryFileName(contentResolver, uri)
            ?: if (isVideo) "video_${System.currentTimeMillis()}.mp4" else "photo_${System.currentTimeMillis()}.jpg"
        val extension = originalFileName.substringAfterLast(".", if (isVideo) "mp4" else "jpg")
        val mediaId = UUID.randomUUID().toString()
        val targetFileName = "$mediaId.$extension"
        val targetFile = File(vaultDirectory, targetFileName)

        var sizeBytes = 0L
        var durationMs = 0L
        var thumbnailRelativePath: String? = null

        // Direct stream copy into internal private vault directory
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { out ->
                val buffer = ByteArray(32768)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                    sizeBytes += bytesRead
                }
                out.flush()
            }
        } ?: throw IllegalStateException("Unable to read input stream from URI: $uri")

        if (isVideo) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(targetFile.absolutePath)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationMs = durationStr?.toLongOrNull() ?: 0L

                // Extract frame for thumbnail
                val frameBitmap = retriever.getFrameAtTime(1000000) ?: retriever.frameAtTime
                if (frameBitmap != null) {
                    val thumbName = "thumb_$mediaId.jpg"
                    val thumbFile = File(vaultDirectory, thumbName)

                    FileOutputStream(thumbFile).use { thumbOut ->
                        frameBitmap.compress(Bitmap.CompressFormat.JPEG, 80, thumbOut)
                        thumbOut.flush()
                    }
                    thumbnailRelativePath = thumbName
                }
            } catch (_: Exception) {
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }
        }

        ImportedMediaFileResult(
            id = mediaId,
            fileName = originalFileName,
            mimeType = mimeType,
            mediaType = mediaType,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            relativePath = targetFileName,
            thumbnailRelativePath = thumbnailRelativePath
        )
    }

    /**
     * Reads media bytes, supporting both plain files and legacy encrypted files.
     */
    suspend fun getDecryptedMediaBytes(relativePath: String): ByteArray = withContext(Dispatchers.IO) {
        val file = File(vaultDirectory, relativePath)
        if (fileEncryptor.isEncrypted(file)) {
            fileEncryptor.decryptFileToBytes(file)
        } else {
            file.readBytes()
        }
    }

    /**
     * Exports media files to export directory for external sharing via FileProvider.
     */
    suspend fun exportMediaFiles(relativePaths: List<Pair<String, String>>): List<Uri> = withContext(Dispatchers.IO) {
        val exportedUris = mutableListOf<Uri>()
        for ((relativePath, originalName) in relativePaths) {
            val sourceFile = File(vaultDirectory, relativePath)
            if (sourceFile.exists()) {
                val exportFile = File(exportDirectory, originalName)
                if (fileEncryptor.isEncrypted(sourceFile)) {
                    FileOutputStream(exportFile).use { out ->
                        fileEncryptor.decryptFileToStream(sourceFile, out)
                        out.flush()
                    }
                } else {
                    sourceFile.copyTo(exportFile, overwrite = true)
                }
                val authority = "${context.packageName}.fileprovider"
                val uri = FileProvider.getUriForFile(context, authority, exportFile)
                exportedUris.add(uri)
            }
        }
        exportedUris
    }

    /**
     * Decrypts and saves media files directly into the Android device's public Gallery
     * (MediaStore Images/Videos collections on modern Android, or public Pictures/Movies folders
     * with MediaScanner on older Android).
     */
    suspend fun exportToGallery(items: List<MediaExportTarget>): GalleryExportSummary = withContext(Dispatchers.IO) {
        var successCount = 0
        var failedCount = 0
        val exportedUris = mutableListOf<Uri>()
        val exportedIds = mutableListOf<String>()
        val exportedPaths = mutableListOf<String>()

        for (item in items) {
            try {
                val sourceFile = File(vaultDirectory, item.relativePath)
                if (!sourceFile.exists()) {
                    failedCount++
                    continue
                }

                val isVideo = item.mediaType.equals("VIDEO", ignoreCase = true) || item.mimeType.startsWith("video/")
                val isPhoto = item.mediaType.equals("PHOTO", ignoreCase = true) || item.mimeType.startsWith("image/")
                val defaultExt = if (isVideo) "mp4" else "jpg"
                val defaultMime = if (isVideo) "video/mp4" else "image/jpeg"

                val sanitizedName = item.fileName.ifBlank {
                    if (isVideo) "video_${System.currentTimeMillis()}.$defaultExt" else "photo_${System.currentTimeMillis()}.$defaultExt"
                }
                val mimeType = item.mimeType.ifBlank { defaultMime }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val collectionUri = when {
                        isVideo -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        isPhoto -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    }
                    val relativeFolder = when {
                        isVideo -> "${Environment.DIRECTORY_MOVIES}/Vault"
                        isPhoto -> "${Environment.DIRECTORY_PICTURES}/Vault"
                        else -> "${Environment.DIRECTORY_DOWNLOADS}/Vault"
                    }

                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, sanitizedName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relativeFolder)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }

                    val contentResolver = context.contentResolver
                    val insertedUri = contentResolver.insert(collectionUri, contentValues)
                        ?: throw IllegalStateException("MediaStore returned null URI for $sanitizedName")

                    try {
                        contentResolver.openOutputStream(insertedUri, "w")?.use { outputStream ->
                            if (fileEncryptor.isEncrypted(sourceFile)) {
                                fileEncryptor.decryptFileToStream(sourceFile, outputStream)
                            } else {
                                sourceFile.inputStream().use { inputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                            outputStream.flush()
                        } ?: throw IllegalStateException("Failed to open output stream for $insertedUri")

                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        contentResolver.update(insertedUri, contentValues, null, null)

                        try {
                            val scanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                                data = insertedUri
                            }
                            context.sendBroadcast(scanIntent)
                        } catch (_: Exception) {}

                        exportedUris.add(insertedUri)
                        if (item.id.isNotEmpty()) exportedIds.add(item.id)
                        exportedPaths.add(item.relativePath)
                        successCount++
                    } catch (e: Exception) {
                        try {
                            contentResolver.delete(insertedUri, null, null)
                        } catch (_: Exception) {}
                        throw e
                    }
                } else {
                    val publicFolder = when {
                        isVideo -> File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Vault")
                        isPhoto -> File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Vault")
                        else -> File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Vault")
                    }
                    if (!publicFolder.exists()) {
                        publicFolder.mkdirs()
                    }

                    var targetFile = File(publicFolder, sanitizedName)
                    var index = 1
                    val namePart = sanitizedName.substringBeforeLast(".")
                    val extPart = sanitizedName.substringAfterLast(".", "")
                    while (targetFile.exists()) {
                        val newName = if (extPart.isNotEmpty()) "${namePart}_$index.$extPart" else "${namePart}_$index"
                        targetFile = File(publicFolder, newName)
                        index++
                    }

                    FileOutputStream(targetFile).use { outputStream ->
                        if (fileEncryptor.isEncrypted(sourceFile)) {
                            fileEncryptor.decryptFileToStream(sourceFile, outputStream)
                        } else {
                            sourceFile.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        outputStream.flush()
                    }

                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(targetFile.absolutePath),
                        arrayOf(mimeType),
                        null
                    )

                    val fileUri = Uri.fromFile(targetFile)
                    exportedUris.add(fileUri)
                    if (item.id.isNotEmpty()) exportedIds.add(item.id)
                    exportedPaths.add(item.relativePath)
                    successCount++
                }
            } catch (_: Exception) {
                failedCount++
            }
        }

        GalleryExportSummary(
            successCount = successCount,
            failedCount = failedCount,
            exportedUris = exportedUris,
            exportedIds = exportedIds,
            exportedPaths = exportedPaths
        )
    }

    /**
     * Resolves a media Uri (such as from PhotoPicker or external storage) to a standard MediaStore content Uri.
     */
    fun resolveToMediaStoreUri(uri: Uri, mimeType: String? = null): Uri {
        val uriStr = uri.toString()
        if (uriStr.startsWith("content://media/external/")) {
            return uri
        }
        val id = uri.lastPathSegment?.toLongOrNull()
        if (id != null) {
            val isVideo = mimeType?.startsWith("video/") == true ||
                context.contentResolver.getType(uri)?.startsWith("video/") == true
            return if (isVideo) {
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            } else {
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            }
        }
        return uri
    }

    /**
     * Creates an IntentSender for user confirmation to delete items from shared MediaStore on Android 11+ (API 30+).
     */
    fun createGalleryDeleteIntentSender(uris: List<Uri>): IntentSender? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uris.isNotEmpty()) {
            return try {
                val mediaStoreUris = uris.map { resolveToMediaStoreUri(it) }
                MediaStore.createDeleteRequest(context.contentResolver, mediaStoreUris).intentSender
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    /**
     * Deletes media directly from Gallery/device storage (pre-Android 11 or files where app has direct delete access).
     */
    suspend fun deleteGalleryUrisDirectly(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var count = 0
        val contentResolver = context.contentResolver
        for (uri in uris) {
            try {
                val resolved = resolveToMediaStoreUri(uri)
                val rows = contentResolver.delete(resolved, null, null)
                if (rows > 0) {
                    count++
                } else {
                    val originalRows = contentResolver.delete(uri, null, null)
                    if (originalRows > 0) count++
                }
            } catch (_: Exception) {}
        }
        count
    }

    /**
     * Prepares temporary file for Android VideoView playback if legacy encrypted,
     * or returns the direct file if already stored plainly in vault.
     */
    suspend fun prepareTemporaryPlaybackFile(relativePath: String): File = withContext(Dispatchers.IO) {
        val sourceFile = File(vaultDirectory, relativePath)
        if (!fileEncryptor.isEncrypted(sourceFile)) {
            return@withContext sourceFile
        }
        cleanupTemporaryPlaybackFiles()
        val extension = relativePath.substringAfterLast(".", "mp4")
        val playbackFile = File(tempPlaybackDirectory, "play_${UUID.randomUUID()}.$extension")
        FileOutputStream(playbackFile).use { out ->
            fileEncryptor.decryptFileToStream(sourceFile, out)
            out.flush()
        }
        playbackFile
    }

    /**
     * Cleans up any temporary video playback files immediately.
     */
    fun cleanupTemporaryPlaybackFiles() {
        try {
            val files = tempPlaybackDirectory.listFiles() ?: return
            for (file in files) {
                if (file.isFile) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun deletePhysicalFiles(relativePaths: List<String>) = withContext(Dispatchers.IO) {
        for (relPath in relativePaths) {
            val file = File(vaultDirectory, relPath)
            if (file.exists()) {
                file.delete()
            }
            val thumbName = "thumb_${relPath.substringBeforeLast(".")}.jpg"
            val thumbFile = File(vaultDirectory, thumbName)
            if (thumbFile.exists()) {
                thumbFile.delete()
            }
        }
    }

    private fun queryFileName(contentResolver: ContentResolver, uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getMimeTypeFromUri(uri: Uri): String {
        val path = uri.toString().lowercase()
        return when {
            path.endsWith(".png") -> "image/png"
            path.endsWith(".gif") -> "image/gif"
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".mp4") -> "video/mp4"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".mov") -> "video/quicktime"
            path.endsWith(".3gp") -> "video/3gpp"
            else -> if (path.contains("video")) "video/mp4" else "image/jpeg"
        }
    }
}
