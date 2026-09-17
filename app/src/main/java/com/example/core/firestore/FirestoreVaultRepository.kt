package com.example.core.firestore

import com.example.feature.media.VaultFolder
import com.example.feature.media.VaultMediaItem
import kotlinx.coroutines.flow.Flow

interface FirestoreVaultRepository {
    suspend fun syncUserProfile(uid: String, email: String, isEmailVerified: Boolean): Result<Unit>
    suspend fun getUserProfile(uid: String): Result<UserProfileDocument?>
    
    suspend fun syncVaultMetadata(
        uid: String,
        folders: List<VaultFolder>,
        mediaItems: List<VaultMediaItem>
    ): Result<Unit>

    fun observeFolders(uid: String): Flow<List<VaultFolderMetadataDocument>>
    fun observeMedia(uid: String): Flow<List<VaultMediaMetadataDocument>>
    fun observeRecordings(uid: String): Flow<List<RecordingMetadataDocument>>

    suspend fun saveRecordingMetadata(
        uid: String,
        recording: RecordingMetadataDocument
    ): Result<Unit>
}
