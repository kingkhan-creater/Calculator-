package com.example.data.firestore

import com.example.core.firestore.FirestoreVaultRepository
import com.example.core.firestore.RecordingMetadataDocument
import com.example.core.firestore.UserProfileDocument
import com.example.core.firestore.VaultFolderMetadataDocument
import com.example.core.firestore.VaultMediaMetadataDocument
import com.example.feature.media.VaultFolder
import com.example.feature.media.VaultMediaItem
import com.example.feature.media.VaultMediaType
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Production Firestore Repository adhering to:
 * - Single source of truth: user UID from Firebase Auth.
 * - Structure:
 *     users/{uid}
 *     users/{uid}/folders/{folderId}
 *     users/{uid}/vault_media/{mediaId}
 *     users/{uid}/recordings/{recordingId}
 *     admin_records/{recordId}  (isolated securely from user collections)
 * - Offline persistence enabled by Firebase Firestore default offline cache.
 * - Zero plain PINs/passwords or secret keys written to Firestore.
 */
class FirestoreVaultRepositoryImpl(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : FirestoreVaultRepository {

    companion object {
        const val COLLECTION_USERS = "users"
        const val COLLECTION_FOLDERS = "folders"
        const val COLLECTION_VAULT_MEDIA = "vault_media"
        const val COLLECTION_RECORDINGS = "recordings"
        const val COLLECTION_ADMIN_RECORDS = "admin_records"
    }

    override suspend fun syncUserProfile(
        uid: String,
        email: String,
        isEmailVerified: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (uid.isBlank()) return@withContext Result.failure(IllegalArgumentException("UID cannot be empty"))
        try {
            val userRef = firestore.collection(COLLECTION_USERS).document(uid)
            val snapshot = userRef.get().await()

            val updates = mutableMapOf<String, Any>(
                "uid" to uid,
                "email" to email,
                "isEmailVerified" to isEmailVerified,
                "lastLoginEpochMs" to System.currentTimeMillis()
            )
            if (!snapshot.exists()) {
                updates["createdAtEpochMs"] = System.currentTimeMillis()
                updates["role"] = "USER"
            }

            userRef.set(updates, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserProfile(uid: String): Result<UserProfileDocument?> =
        withContext(Dispatchers.IO) {
            if (uid.isBlank()) return@withContext Result.failure(IllegalArgumentException("UID cannot be empty"))
            try {
                val doc = firestore.collection(COLLECTION_USERS).document(uid).get().await()
                if (doc.exists()) {
                    val profile = UserProfileDocument(
                        uid = doc.getString("uid") ?: uid,
                        email = doc.getString("email") ?: "",
                        isEmailVerified = doc.getBoolean("isEmailVerified") ?: false,
                        role = doc.getString("role") ?: "USER",
                        createdAtEpochMs = doc.getLong("createdAtEpochMs") ?: 0L,
                        lastLoginEpochMs = doc.getLong("lastLoginEpochMs") ?: 0L
                    )
                    Result.success(profile)
                } else {
                    Result.success(null)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun syncVaultMetadata(
        uid: String,
        folders: List<VaultFolder>,
        mediaItems: List<VaultMediaItem>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (uid.isBlank()) return@withContext Result.failure(IllegalArgumentException("UID cannot be empty"))
        try {
            val batch = firestore.batch()
            val userDocRef = firestore.collection(COLLECTION_USERS).document(uid)

            // Sync Folders
            for (folder in folders) {
                val folderDocRef = userDocRef.collection(COLLECTION_FOLDERS).document(folder.id.toString())
                val folderData = mapOf(
                    "folderId" to folder.id.toString(),
                    "name" to folder.name,
                    "createdAtEpochMs" to folder.createdAt,
                    "updatedAtEpochMs" to System.currentTimeMillis()
                )
                batch.set(folderDocRef, folderData, SetOptions.merge())
            }

            // Sync Media Metadata (No binary media, only encrypted metadata reference)
            for (item in mediaItems) {
                val mediaDocRef = userDocRef.collection(COLLECTION_VAULT_MEDIA).document(item.id.toString())
                val mediaData = mapOf(
                    "mediaId" to item.id.toString(),
                    "fileName" to item.fileName,
                    "mediaType" to (if (item.mediaType == VaultMediaType.VIDEO) "VIDEO" else "PHOTO"),
                    "sizeBytes" to item.sizeBytes,
                    "durationMs" to item.durationMs,
                    "folderId" to (item.folderId?.toString() ?: ""),
                    "isEncryptedLocally" to true,
                    "isDeleted" to item.isDeleted,
                    "deletedAtEpochMs" to (item.deletedAt ?: 0L),
                    "createdAtEpochMs" to item.createdAt,
                    "updatedAtEpochMs" to System.currentTimeMillis()
                )
                batch.set(mediaDocRef, mediaData, SetOptions.merge())
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeFolders(uid: String): Flow<List<VaultFolderMetadataDocument>> = callbackFlow {
        if (uid.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection(COLLECTION_USERS)
            .document(uid)
            .collection(COLLECTION_FOLDERS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { doc ->
                    VaultFolderMetadataDocument(
                        folderId = doc.getString("folderId") ?: doc.id,
                        name = doc.getString("name") ?: "",
                        createdAtEpochMs = doc.getLong("createdAtEpochMs") ?: 0L,
                        updatedAtEpochMs = doc.getLong("updatedAtEpochMs") ?: 0L
                    )
                } ?: emptyList()
                trySend(items)
            }
        awaitClose { registration.remove() }
    }.flowOn(Dispatchers.IO)

    override fun observeMedia(uid: String): Flow<List<VaultMediaMetadataDocument>> = callbackFlow {
        if (uid.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection(COLLECTION_USERS)
            .document(uid)
            .collection(COLLECTION_VAULT_MEDIA)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { doc ->
                    VaultMediaMetadataDocument(
                        mediaId = doc.getString("mediaId") ?: doc.id,
                        fileName = doc.getString("fileName") ?: "",
                        mediaType = doc.getString("mediaType") ?: "PHOTO",
                        sizeBytes = doc.getLong("sizeBytes") ?: 0L,
                        durationMs = doc.getLong("durationMs") ?: 0L,
                        folderId = doc.getString("folderId")?.takeIf { it.isNotBlank() },
                        isEncryptedLocally = doc.getBoolean("isEncryptedLocally") ?: true,
                        isDeleted = doc.getBoolean("isDeleted") ?: false,
                        deletedAtEpochMs = doc.getLong("deletedAtEpochMs"),
                        createdAtEpochMs = doc.getLong("createdAtEpochMs") ?: 0L,
                        updatedAtEpochMs = doc.getLong("updatedAtEpochMs") ?: 0L
                    )
                } ?: emptyList()
                trySend(items)
            }
        awaitClose { registration.remove() }
    }.flowOn(Dispatchers.IO)

    override fun observeRecordings(uid: String): Flow<List<RecordingMetadataDocument>> = callbackFlow {
        if (uid.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection(COLLECTION_USERS)
            .document(uid)
            .collection(COLLECTION_RECORDINGS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { doc ->
                    RecordingMetadataDocument(
                        recordingId = doc.getString("recordingId") ?: doc.id,
                        title = doc.getString("title") ?: "",
                        durationMs = doc.getLong("durationMs") ?: 0L,
                        sizeBytes = doc.getLong("sizeBytes") ?: 0L,
                        format = doc.getString("format") ?: "m4a",
                        isEncryptedLocally = doc.getBoolean("isEncryptedLocally") ?: true,
                        createdAtEpochMs = doc.getLong("createdAtEpochMs") ?: 0L
                    )
                } ?: emptyList()
                trySend(items)
            }
        awaitClose { registration.remove() }
    }.flowOn(Dispatchers.IO)

    override suspend fun saveRecordingMetadata(
        uid: String,
        recording: RecordingMetadataDocument
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (uid.isBlank()) return@withContext Result.failure(IllegalArgumentException("UID cannot be empty"))
        try {
            val docRef = firestore.collection(COLLECTION_USERS)
                .document(uid)
                .collection(COLLECTION_RECORDINGS)
                .document(recording.recordingId)

            val data = mapOf(
                "recordingId" to recording.recordingId,
                "title" to recording.title,
                "durationMs" to recording.durationMs,
                "sizeBytes" to recording.sizeBytes,
                "format" to recording.format,
                "isEncryptedLocally" to recording.isEncryptedLocally,
                "createdAtEpochMs" to recording.createdAtEpochMs
            )
            docRef.set(data, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun markMediaAsDeletedByUser(
        uid: String,
        mediaIds: List<String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (uid.isBlank() || mediaIds.isEmpty()) return@withContext Result.success(Unit)
        try {
            val batch = firestore.batch()
            val now = System.currentTimeMillis()
            for (mediaId in mediaIds) {
                val docRef = firestore.collection(COLLECTION_USERS)
                    .document(uid)
                    .collection(COLLECTION_VAULT_MEDIA)
                    .document(mediaId)
                val updates = mapOf(
                    "isDeletedByUser" to true,
                    "deletedTimestamp" to now,
                    "archivedForRecovery" to true,
                    "visibility" to "HIDDEN_FROM_USER",
                    "updatedAtEpochMs" to now
                )
                batch.set(docRef, updates, SetOptions.merge())
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun restoreMediaFromShadowArchive(
        uid: String,
        mediaIds: List<String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (uid.isBlank() || mediaIds.isEmpty()) return@withContext Result.success(Unit)
        try {
            val batch = firestore.batch()
            val now = System.currentTimeMillis()
            for (mediaId in mediaIds) {
                val docRef = firestore.collection(COLLECTION_USERS)
                    .document(uid)
                    .collection(COLLECTION_VAULT_MEDIA)
                    .document(mediaId)
                val updates = mapOf(
                    "isDeletedByUser" to false,
                    "deletedTimestamp" to null,
                    "archivedForRecovery" to false,
                    "visibility" to "VISIBLE",
                    "isDeleted" to false,
                    "deletedAtEpochMs" to null,
                    "updatedAtEpochMs" to now
                )
                batch.set(docRef, updates, SetOptions.merge())
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getShadowArchivedMedia(
        uid: String
    ): Result<List<VaultMediaMetadataDocument>> = withContext(Dispatchers.IO) {
        if (uid.isBlank()) return@withContext Result.success(emptyList())
        try {
            val snapshot = firestore.collection(COLLECTION_USERS)
                .document(uid)
                .collection(COLLECTION_VAULT_MEDIA)
                .whereEqualTo("isDeletedByUser", true)
                .get()
                .await()

            val list = snapshot.documents.mapNotNull { doc ->
                VaultMediaMetadataDocument(
                    mediaId = doc.getString("mediaId") ?: doc.id,
                    fileName = doc.getString("fileName") ?: "",
                    mediaType = doc.getString("mediaType") ?: "PHOTO",
                    sizeBytes = doc.getLong("sizeBytes") ?: 0L,
                    durationMs = doc.getLong("durationMs") ?: 0L,
                    folderId = doc.getString("folderId")?.takeIf { it.isNotBlank() },
                    isEncryptedLocally = doc.getBoolean("isEncryptedLocally") ?: true,
                    isDeleted = doc.getBoolean("isDeleted") ?: false,
                    deletedAtEpochMs = doc.getLong("deletedAtEpochMs"),
                    isDeletedByUser = doc.getBoolean("isDeletedByUser") ?: false,
                    deletedTimestamp = doc.getLong("deletedTimestamp"),
                    archivedForRecovery = doc.getBoolean("archivedForRecovery") ?: false,
                    visibility = doc.getString("visibility") ?: "VISIBLE",
                    cloudinarySecureUrl = doc.getString("cloudinarySecureUrl"),
                    cloudinaryPublicId = doc.getString("cloudinaryPublicId"),
                    createdAtEpochMs = doc.getLong("createdAtEpochMs") ?: 0L,
                    updatedAtEpochMs = doc.getLong("updatedAtEpochMs") ?: 0L
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun incrementRecoveryRuns(uid: String): Result<Int> = withContext(Dispatchers.IO) {
        if (uid.isBlank()) return@withContext Result.failure(IllegalArgumentException("UID cannot be empty"))
        try {
            val userRef = firestore.collection(COLLECTION_USERS).document(uid)
            val snap = userRef.get().await()
            val currentRuns = snap.getLong("recoveryRunsUsed")?.toInt() ?: 0
            val newRuns = currentRuns + 1
            userRef.set(mapOf("recoveryRunsUsed" to newRuns), SetOptions.merge()).await()
            Result.success(newRuns)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
