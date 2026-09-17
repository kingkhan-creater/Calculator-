package com.example.core.firestore

data class UserProfileDocument(
    val uid: String = "",
    val email: String = "",
    val isEmailVerified: Boolean = false,
    val role: String = "USER", // "USER" or "ADMIN"
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val lastLoginEpochMs: Long = System.currentTimeMillis()
)

data class VaultFolderMetadataDocument(
    val folderId: String = "",
    val name: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

data class VaultMediaMetadataDocument(
    val mediaId: String = "",
    val fileName: String = "",
    val mediaType: String = "PHOTO", // "PHOTO" or "VIDEO"
    val sizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val folderId: String? = null,
    val isEncryptedLocally: Boolean = true,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

data class RecordingMetadataDocument(
    val recordingId: String = "",
    val title: String = "",
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val format: String = "m4a",
    val isEncryptedLocally: Boolean = true,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)
