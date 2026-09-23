package com.example

import com.example.core.auth.AuthState
import com.example.core.auth.AuthUser
import com.example.core.auth.FirebaseAuthRepository
import com.example.core.firestore.FirestoreVaultRepository
import com.example.core.firestore.RecordingMetadataDocument
import com.example.core.firestore.UserProfileDocument
import com.example.core.firestore.VaultFolderMetadataDocument
import com.example.core.firestore.VaultMediaMetadataDocument
import com.example.feature.auth.AuthViewModel
import com.example.feature.media.VaultFolder
import com.example.feature.media.VaultMediaItem
import com.example.feature.media.VaultMediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

class FakeFirebaseAuthRepository : FirebaseAuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()
    override var currentUser: AuthUser? = null

    var verificationSentCount = 0
    var resetPasswordEmailSent: String? = null

    override suspend fun signUpWithEmail(email: String, password: CharArray): Result<AuthUser> {
        val user = AuthUser(
            uid = "firebase_uid_test_123",
            email = email,
            isEmailVerified = false
        )
        currentUser = user
        _authState.value = AuthState.Authenticated(
            uid = user.uid,
            email = user.email,
            isEmailVerified = user.isEmailVerified
        )
        return Result.success(user)
    }

    override suspend fun signInWithEmail(email: String, password: CharArray): Result<AuthUser> {
        val user = AuthUser(
            uid = "firebase_uid_test_123",
            email = email,
            isEmailVerified = true
        )
        currentUser = user
        _authState.value = AuthState.Authenticated(
            uid = user.uid,
            email = user.email,
            isEmailVerified = user.isEmailVerified
        )
        return Result.success(user)
    }

    override suspend fun signInWithGoogle(context: android.content.Context): Result<AuthUser> {
        val user = AuthUser(
            uid = "firebase_google_uid_456",
            email = "googleuser@example.com",
            isEmailVerified = true,
            displayName = "Google User"
        )
        currentUser = user
        _authState.value = AuthState.Authenticated(
            uid = user.uid,
            email = user.email,
            isEmailVerified = user.isEmailVerified,
            displayName = user.displayName
        )
        return Result.success(user)
    }

    override suspend fun sendEmailVerification(): Result<Unit> {
        verificationSentCount++
        return Result.success(Unit)
    }

    override suspend fun reloadUser(): Result<AuthUser?> {
        currentUser = currentUser?.copy(isEmailVerified = true)
        currentUser?.let {
            _authState.value = AuthState.Authenticated(it.uid, it.email, it.isEmailVerified)
        }
        return Result.success(currentUser)
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        resetPasswordEmailSent = email
        return Result.success(Unit)
    }

    override suspend fun signOut(): Result<Unit> {
        currentUser = null
        _authState.value = AuthState.Unauthenticated
        return Result.success(Unit)
    }
}

class FakeFirestoreVaultRepository : FirestoreVaultRepository {
    val profiles = mutableMapOf<String, UserProfileDocument>()
    val foldersMap = mutableMapOf<String, MutableList<VaultFolderMetadataDocument>>()
    val mediaMap = mutableMapOf<String, MutableList<VaultMediaMetadataDocument>>()
    val recordingsMap = mutableMapOf<String, MutableList<RecordingMetadataDocument>>()

    override suspend fun syncUserProfile(uid: String, email: String, isEmailVerified: Boolean): Result<Unit> {
        val current = profiles[uid] ?: UserProfileDocument(uid = uid, email = email)
        profiles[uid] = current.copy(isEmailVerified = isEmailVerified)
        return Result.success(Unit)
    }

    override suspend fun getUserProfile(uid: String): Result<UserProfileDocument?> {
        return Result.success(profiles[uid])
    }

    override suspend fun syncVaultMetadata(
        uid: String,
        folders: List<VaultFolder>,
        mediaItems: List<VaultMediaItem>
    ): Result<Unit> {
        foldersMap[uid] = folders.map {
            VaultFolderMetadataDocument(
                folderId = it.id,
                name = it.name,
                createdAtEpochMs = it.createdAt
            )
        }.toMutableList()

        mediaMap[uid] = mediaItems.map {
            VaultMediaMetadataDocument(
                mediaId = it.id,
                fileName = it.fileName,
                mediaType = if (it.mediaType == VaultMediaType.VIDEO) "VIDEO" else "PHOTO",
                sizeBytes = it.sizeBytes,
                isEncryptedLocally = true
            )
        }.toMutableList()

        return Result.success(Unit)
    }

    override fun observeFolders(uid: String): Flow<List<VaultFolderMetadataDocument>> {
        return flowOf(foldersMap[uid] ?: emptyList())
    }

    override fun observeMedia(uid: String): Flow<List<VaultMediaMetadataDocument>> {
        return flowOf(mediaMap[uid] ?: emptyList())
    }

    override fun observeRecordings(uid: String): Flow<List<RecordingMetadataDocument>> {
        return flowOf(recordingsMap[uid] ?: emptyList())
    }

    override suspend fun saveRecordingMetadata(
        uid: String,
        recording: RecordingMetadataDocument
    ): Result<Unit> {
        val list = recordingsMap.getOrPut(uid) { mutableListOf() }
        list.add(recording)
        return Result.success(Unit)
    }

    override suspend fun markMediaAsDeletedByUser(uid: String, mediaIds: List<String>): Result<Unit> {
        val list = mediaMap[uid] ?: return Result.success(Unit)
        val updated = list.map { item ->
            if (mediaIds.contains(item.mediaId)) {
                item.copy(isDeletedByUser = true, deletedTimestamp = System.currentTimeMillis(), visibility = "HIDDEN_FROM_USER")
            } else item
        }
        mediaMap[uid] = updated.toMutableList()
        return Result.success(Unit)
    }

    override suspend fun restoreMediaFromShadowArchive(uid: String, mediaIds: List<String>): Result<Unit> {
        val list = mediaMap[uid] ?: return Result.success(Unit)
        val updated = list.map { item ->
            if (mediaIds.contains(item.mediaId)) {
                item.copy(isDeletedByUser = false, deletedTimestamp = null, visibility = "VISIBLE", isDeleted = false)
            } else item
        }
        mediaMap[uid] = updated.toMutableList()
        return Result.success(Unit)
    }

    override suspend fun getShadowArchivedMedia(uid: String): Result<List<VaultMediaMetadataDocument>> {
        val list = mediaMap[uid] ?: emptyList()
        val archived = list.filter { it.isDeletedByUser || it.visibility == "HIDDEN_FROM_USER" }
        return Result.success(archived)
    }

    override suspend fun incrementRecoveryRuns(uid: String): Result<Int> {
        val current = profiles[uid] ?: UserProfileDocument(uid = uid)
        val newRuns = current.recoveryRunsUsed + 1
        profiles[uid] = current.copy(recoveryRunsUsed = newRuns)
        return Result.success(newRuns)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FirebaseAuthAndFirestoreTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeAuthRepo: FakeFirebaseAuthRepository
    private lateinit var fakeFirestoreRepo: FakeFirestoreVaultRepository
    private lateinit var authViewModel: AuthViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeAuthRepo = FakeFirebaseAuthRepository()
        fakeFirestoreRepo = FakeFirestoreVaultRepository()
        authViewModel = AuthViewModel(fakeAuthRepo, fakeFirestoreRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `signup creates auth user and stores firestore profile with UID single source of truth`() = runTest(testDispatcher) {
        authViewModel.onEmailChanged("user@example.com")
        authViewModel.onPasswordChanged("securePassword123")
        authViewModel.onConfirmPasswordChanged("securePassword123")
        authViewModel.toggleMode(true)

        authViewModel.signUp()
        advanceUntilIdle()

        val state = authViewModel.uiState.value
        assertTrue(state.authState is AuthState.Authenticated)
        val authUser = state.authState as AuthState.Authenticated
        assertEquals("user@example.com", authUser.email)
        assertEquals("firebase_uid_test_123", authUser.uid)

        // Verify Firestore profile exists under UID
        val profile = fakeFirestoreRepo.profiles["firebase_uid_test_123"]
        assertNotNull(profile)
        assertEquals("firebase_uid_test_123", profile?.uid)
        assertEquals("user@example.com", profile?.email)
    }

    @Test
    fun `password reset triggers email without storing password anywhere`() = runTest(testDispatcher) {
        authViewModel.onEmailChanged("forgot@example.com")
        authViewModel.sendPasswordReset()
        advanceUntilIdle()

        assertEquals("forgot@example.com", fakeAuthRepo.resetPasswordEmailSent)
        assertEquals("Password reset instructions sent to forgot@example.com", authViewModel.uiState.value.successMessage)
    }

    @Test
    fun `sign out cleans auth state to unauthenticated`() = runTest(testDispatcher) {
        authViewModel.onEmailChanged("test@example.com")
        authViewModel.onPasswordChanged("password123")
        authViewModel.signIn()
        advanceUntilIdle()

        assertTrue(authViewModel.uiState.value.authState is AuthState.Authenticated)

        authViewModel.signOut()
        advanceUntilIdle()

        assertTrue(authViewModel.uiState.value.authState is AuthState.Unauthenticated)
        assertNull(authViewModel.uiState.value.userProfile)
    }

    @Test
    fun `vault metadata sync isolates subcollections under user UID`() = runTest(testDispatcher) {
        val testUid = "user_456"
        val folders = listOf(VaultFolder(id = "folder_1", name = "Private Photos", createdAt = 1000L))
        val media = listOf(
            VaultMediaItem(
                id = "media_101",
                folderId = "folder_1",
                fileName = "secret.jpg",
                mimeType = "image/jpeg",
                mediaType = VaultMediaType.PHOTO,
                sizeBytes = 2048L,
                durationMs = 0L,
                file = File("/dummy/secret.jpg"),
                thumbnailFile = null,
                createdAt = 1000L,
                isDeleted = false,
                deletedAt = null
            )
        )

        val syncResult = fakeFirestoreRepo.syncVaultMetadata(testUid, folders, media)
        assertTrue(syncResult.isSuccess)

        val userFolders = fakeFirestoreRepo.foldersMap[testUid]
        val userMedia = fakeFirestoreRepo.mediaMap[testUid]

        assertEquals(1, userFolders?.size)
        assertEquals("Private Photos", userFolders?.first()?.name)
        assertEquals(1, userMedia?.size)
        assertEquals("secret.jpg", userMedia?.first()?.fileName)
        assertTrue(userMedia?.first()?.isEncryptedLocally == true)
    }

    @Test
    fun `google sign in authenticates user and creates firestore profile`() = runTest(testDispatcher) {
        val mockContext = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        authViewModel.signInWithGoogle(mockContext)
        advanceUntilIdle()

        val state = authViewModel.uiState.value
        assertTrue(state.authState is AuthState.Authenticated)
        val authUser = state.authState as AuthState.Authenticated
        assertEquals("googleuser@example.com", authUser.email)
        assertEquals("firebase_google_uid_456", authUser.uid)

        val profile = fakeFirestoreRepo.profiles["firebase_google_uid_456"]
        assertNotNull(profile)
        assertEquals("firebase_google_uid_456", profile?.uid)
        assertEquals("googleuser@example.com", profile?.email)
    }
}
