package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.VaultMediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Query("SELECT * FROM vault_media WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllActiveMedia(): Flow<List<VaultMediaEntity>>

    @Query("SELECT * FROM vault_media WHERE isDeleted = 0 AND (folderId = :folderId OR (:folderId IS NULL AND folderId IS NULL)) ORDER BY createdAt DESC")
    fun getActiveMediaByFolder(folderId: String?): Flow<List<VaultMediaEntity>>

    @Query("SELECT * FROM vault_media WHERE isDeleted = 0 AND mediaType = 'PHOTO' ORDER BY createdAt DESC")
    fun getActivePhotos(): Flow<List<VaultMediaEntity>>

    @Query("SELECT * FROM vault_media WHERE isDeleted = 0 AND mediaType = 'VIDEO' ORDER BY createdAt DESC")
    fun getActiveVideos(): Flow<List<VaultMediaEntity>>

    @Query("SELECT * FROM vault_media WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getTrashMedia(): Flow<List<VaultMediaEntity>>

    @Query("SELECT * FROM vault_media WHERE isDeleted = 1")
    suspend fun getTrashMediaSync(): List<VaultMediaEntity>

    @Query("SELECT * FROM vault_media WHERE id = :id LIMIT 1")
    fun getMediaById(id: String): Flow<VaultMediaEntity?>

    @Query("SELECT * FROM vault_media WHERE id = :id LIMIT 1")
    suspend fun getMediaByIdSync(id: String): VaultMediaEntity?

    @Query("SELECT * FROM vault_media WHERE id IN (:idList)")
    suspend fun getMediaListByIds(idList: List<String>): List<VaultMediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: VaultMediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaList(mediaList: List<VaultMediaEntity>)

    @Update
    suspend fun updateMedia(media: VaultMediaEntity)

    @Query("UPDATE vault_media SET isDeleted = 1, deletedAt = :timestamp WHERE id IN (:idList)")
    suspend fun softDeleteMedia(idList: List<String>, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE vault_media SET isDeleted = 0, deletedAt = NULL WHERE id IN (:idList)")
    suspend fun restoreMedia(idList: List<String>)

    @Query("UPDATE vault_media SET folderId = :folderId WHERE id IN (:idList)")
    suspend fun moveMediaToFolder(idList: List<String>, folderId: String?)

    @Query("DELETE FROM vault_media WHERE id IN (:idList)")
    suspend fun permanentlyDeleteMedia(idList: List<String>)

    @Query("DELETE FROM vault_media WHERE isDeleted = 1")
    suspend fun emptyTrash()

    @Query("SELECT COUNT(*) FROM vault_media WHERE isDeleted = 0 AND folderId = :folderId")
    fun getMediaCountInFolder(folderId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM vault_media WHERE isDeleted = 1")
    fun getTrashCount(): Flow<Int>
}
