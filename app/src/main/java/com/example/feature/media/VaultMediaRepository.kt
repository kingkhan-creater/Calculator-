package com.example.feature.media

import android.content.IntentSender
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import java.io.File

enum class VaultMediaType {
    PHOTO,
    VIDEO
}

data class VaultMediaItem(
    val id: String,
    val folderId: String?,
    val fileName: String,
    val mimeType: String,
    val mediaType: VaultMediaType,
    val sizeBytes: Long,
    val durationMs: Long,
    val file: File,
    val thumbnailFile: File?,
    val createdAt: Long,
    val isDeleted: Boolean,
    val deletedAt: Long?
)

data class VaultFolder(
    val id: String,
    val name: String,
    val createdAt: Long
)

data class ExportToGalleryResult(
    val successCount: Int,
    val failedCount: Int,
    val exportedUris: List<Uri>
)

interface VaultMediaRepository {
    fun getAllActiveMedia(): Flow<List<VaultMediaItem>>
    fun getActiveMediaInFolder(folderId: String?): Flow<List<VaultMediaItem>>
    fun getActivePhotos(): Flow<List<VaultMediaItem>>
    fun getActiveVideos(): Flow<List<VaultMediaItem>>
    fun getTrashMedia(): Flow<List<VaultMediaItem>>
    fun getMediaById(id: String): Flow<VaultMediaItem?>
    fun getAllFolders(): Flow<List<VaultFolder>>
    fun getTrashCount(): Flow<Int>

    suspend fun importMediaList(uris: List<Uri>, folderId: String? = null): Result<List<VaultMediaItem>>
    suspend fun createFolder(name: String): Result<VaultFolder>
    suspend fun deleteFolder(folderId: String): Result<Unit>
    suspend fun moveMediaToFolder(mediaIds: List<String>, folderId: String?): Result<Unit>
    suspend fun moveToTrash(mediaIds: List<String>): Result<Unit>
    suspend fun restoreFromTrash(mediaIds: List<String>): Result<Unit>
    suspend fun permanentlyDelete(mediaIds: List<String>): Result<Unit>
    suspend fun emptyTrash(): Result<Unit>
    suspend fun prepareExport(mediaIds: List<String>): Result<List<Uri>>
    suspend fun exportToGallery(mediaIds: List<String>): Result<ExportToGalleryResult>
    fun createGalleryDeleteIntentSender(uris: List<Uri>): IntentSender?
    suspend fun deleteGalleryOriginalsDirectly(uris: List<Uri>): Int
    suspend fun restoreFromShadowArchive(mediaIds: List<String>): Result<Unit>
    suspend fun purgeExpiredShadowArchive(cutoffMs: Long): Result<Unit>
}
