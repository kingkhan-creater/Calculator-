package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.VaultFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Query("SELECT * FROM vault_folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<VaultFolderEntity>>

    @Query("SELECT * FROM vault_folders WHERE id = :folderId LIMIT 1")
    fun getFolderById(folderId: String): Flow<VaultFolderEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: VaultFolderEntity)

    @Update
    suspend fun updateFolder(folder: VaultFolderEntity)

    @Query("DELETE FROM vault_folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: String)
}
