package com.example.data

import android.content.IntentSender
import android.net.Uri
import com.example.core.security.VaultSessionManager
import com.example.data.local.dao.FolderDao
import com.example.data.local.dao.MediaDao
import com.example.data.local.entity.VaultFolderEntity
import com.example.data.local.entity.VaultMediaEntity
import com.example.data.storage.MediaExportTarget
import com.example.data.storage.VaultStorageManager
import com.example.feature.media.ExportToGalleryResult
import com.example.feature.media.VaultFolder
import com.example.feature.media.VaultMediaItem
import com.example.feature.media.VaultMediaRepository
import com.example.feature.media.VaultMediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VaultMediaRepositoryImpl(
    private val mediaDao: MediaDao,
    private val folderDao: FolderDao,
    private val storageManager: VaultStorageManager
) : VaultMediaRepository {

    override fun getAllActiveMedia(): Flow<List<VaultMediaItem>> {
        return VaultSessionManager.isDecoyMode.flatMapLatest { isDecoy ->
            if (isDecoy) flowOf(emptyList())
            else mediaDao.getAllActiveMedia().map { list -> list.map { it.toDomain() } }
        }
    }

    override fun getActiveMediaInFolder(folderId: String?): Flow<List<VaultMediaItem>> {
        return VaultSessionManager.isDecoyMode.flatMapLatest { isDecoy ->
            if (isDecoy) flowOf(emptyList())
            else mediaDao.getActiveMediaByFolder(folderId).map { list -> list.map { it.toDomain() } }
        }
    }

    override fun getActivePhotos(): Flow<List<VaultMediaItem>> {
        return VaultSessionManager.isDecoyMode.flatMapLatest { isDecoy ->
            if (isDecoy) flowOf(emptyList())
            else mediaDao.getActivePhotos().map { list -> list.map { it.toDomain() } }
        }
    }

    override fun getActiveVideos(): Flow<List<VaultMediaItem>> {
        return VaultSessionManager.isDecoyMode.flatMapLatest { isDecoy ->
            if (isDecoy) flowOf(emptyList())
            else mediaDao.getActiveVideos().map { list -> list.map { it.toDomain() } }
        }
    }

    override fun getTrashMedia(): Flow<List<VaultMediaItem>> {
        return VaultSessionManager.isDecoyMode.flatMapLatest { isDecoy ->
            if (isDecoy) flowOf(emptyList())
            else mediaDao.getTrashMedia().map { list -> list.map { it.toDomain() } }
        }
    }

    override fun getMediaById(id: String): Flow<VaultMediaItem?> {
        return VaultSessionManager.isDecoyMode.flatMapLatest { isDecoy ->
            if (isDecoy) flowOf(null)
            else mediaDao.getMediaById(id).map { it?.toDomain() }
        }
    }

    override fun getAllFolders(): Flow<List<VaultFolder>> {
        return VaultSessionManager.isDecoyMode.flatMapLatest { isDecoy ->
            if (isDecoy) flowOf(emptyList())
            else folderDao.getAllFolders().map { list ->
                list.map { VaultFolder(it.id, it.name, it.createdAt) }
            }
        }
    }

    override fun getTrashCount(): Flow<Int> {
        return VaultSessionManager.isDecoyMode.flatMapLatest { isDecoy ->
            if (isDecoy) flowOf(0)
            else mediaDao.getTrashCount()
        }
    }

    override suspend fun importMediaList(uris: List<Uri>, folderId: String?): Result<List<VaultMediaItem>> {
        return try {
            val entities = mutableListOf<VaultMediaEntity>()
            for (uri in uris) {
                val imported = storageManager.importMedia(uri, folderId)
                val entity = VaultMediaEntity(
                    id = imported.id,
                    folderId = folderId,
                    fileName = imported.fileName,
                    mimeType = imported.mimeType,
                    mediaType = imported.mediaType,
                    sizeBytes = imported.sizeBytes,
                    durationMs = imported.durationMs,
                    relativePath = imported.relativePath,
                    thumbnailPath = imported.thumbnailRelativePath,
                    createdAt = System.currentTimeMillis(),
                    isDeleted = false,
                    deletedAt = null
                )
                entities.add(entity)
            }
            mediaDao.insertMediaList(entities)
            Result.success(entities.map { it.toDomain() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createFolder(name: String): Result<VaultFolder> {
        return try {
            val folder = VaultFolderEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                createdAt = System.currentTimeMillis()
            )
            folderDao.insertFolder(folder)
            Result.success(VaultFolder(folder.id, folder.name, folder.createdAt))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteFolder(folderId: String): Result<Unit> {
        return try {
            folderDao.deleteFolder(folderId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun moveMediaToFolder(mediaIds: List<String>, folderId: String?): Result<Unit> {
        return try {
            mediaDao.moveMediaToFolder(mediaIds, folderId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun moveToTrash(mediaIds: List<String>): Result<Unit> {
        return try {
            mediaDao.softDeleteMedia(mediaIds, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun restoreFromTrash(mediaIds: List<String>): Result<Unit> {
        return try {
            mediaDao.restoreMedia(mediaIds)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun permanentlyDelete(mediaIds: List<String>): Result<Unit> {
        return try {
            val entities = mediaDao.getMediaListByIds(mediaIds)
            val paths = entities.map { it.relativePath }
            storageManager.deletePhysicalFiles(paths)
            mediaDao.permanentlyDeleteMedia(mediaIds)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun emptyTrash(): Result<Unit> {
        return try {
            val trashItems = mediaDao.getTrashMediaSync()
            val paths = trashItems.map { it.relativePath }
            storageManager.deletePhysicalFiles(paths)
            mediaDao.emptyTrash()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun prepareExport(mediaIds: List<String>): Result<List<Uri>> {
        return try {
            val items = mediaDao.getMediaListByIds(mediaIds)
            val exportList = items.map { Pair(it.relativePath, it.fileName) }
            val uris = storageManager.exportMediaFiles(exportList)
            Result.success(uris)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exportToGallery(mediaIds: List<String>): Result<ExportToGalleryResult> {
        return try {
            val items = mediaDao.getMediaListByIds(mediaIds)
            val exportTargets = items.map {
                MediaExportTarget(
                    id = it.id,
                    relativePath = it.relativePath,
                    fileName = it.fileName,
                    mimeType = it.mimeType,
                    mediaType = it.mediaType
                )
            }
            val summary = storageManager.exportToGallery(exportTargets)
            
            // Professional Move-to-Gallery:
            // Files successfully restored/exported to the public Gallery are removed from the Vault
            // so they do not duplicate, don't occupy double storage, and cannot be exported multiple times.
            if (summary.exportedIds.isNotEmpty()) {
                storageManager.deletePhysicalFiles(summary.exportedPaths)
                mediaDao.permanentlyDeleteMedia(summary.exportedIds)
            }

            Result.success(
                ExportToGalleryResult(
                    successCount = summary.successCount,
                    failedCount = summary.failedCount,
                    exportedUris = summary.exportedUris
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun createGalleryDeleteIntentSender(uris: List<Uri>): IntentSender? {
        return storageManager.createGalleryDeleteIntentSender(uris)
    }

    override suspend fun deleteGalleryOriginalsDirectly(uris: List<Uri>): Int {
        return storageManager.deleteGalleryUrisDirectly(uris)
    }

    private fun VaultMediaEntity.toDomain(): VaultMediaItem {
        return VaultMediaItem(
            id = this.id,
            folderId = this.folderId,
            fileName = this.fileName,
            mimeType = this.mimeType,
            mediaType = if (this.mediaType == "VIDEO") VaultMediaType.VIDEO else VaultMediaType.PHOTO,
            sizeBytes = this.sizeBytes,
            durationMs = this.durationMs,
            file = storageManager.getMediaFile(this.relativePath),
            thumbnailFile = storageManager.getThumbnailFile(this.thumbnailPath),
            createdAt = this.createdAt,
            isDeleted = this.isDeleted,
            deletedAt = this.deletedAt
        )
    }
}
