package com.example.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.R
import com.example.core.auth.AuthState
import com.example.core.auth.AuthUser
import com.example.core.auth.FirebaseAuthRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Production implementation of FirebaseAuthRepository.
 * Uses Firebase Authentication as the single source of truth for user identity.
 * Supports Email/Password, Email Verification, Password Reset, and Google Sign-In via Credential Manager.
 * Cleans in-memory password CharArrays after authentication.
 */
class FirebaseAuthRepositoryImpl(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) : FirebaseAuthRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val currentUser: AuthUser?
        get() = firebaseAuth.currentUser?.toDomainUser()

    init {
        // Attach Firebase Auth state listener
        firebaseAuth.addAuthStateListener { auth ->
            val user = auth.currentUser
            _authState.value = if (user != null) {
                AuthState.Authenticated(
                    uid = user.uid,
                    email = user.email ?: "",
                    isEmailVerified = user.isEmailVerified,
                    displayName = user.displayName
                )
            } else {
                AuthState.Unauthenticated
            }
        }
    }

    override suspend fun signUpWithEmail(email: String, password: CharArray): Result<AuthUser> =
        withContext(Dispatchers.IO) {
            try {
                val pwdString = String(password)
                val authResult = firebaseAuth.createUserWithEmailAndPassword(email.trim(), pwdString).await()
                // Clear password memory immediately
                password.fill('0')

                val user = authResult.user
                    ?: return@withContext Result.failure(IllegalStateException("User creation returned null"))
                
                // Automatically send verification email on signup
                try {
                    user.sendEmailVerification().await()
                } catch (_: Exception) {
                    // Non-fatal if verification email delivery fails immediately
                }

                Result.success(user.toDomainUser())
            } catch (e: Exception) {
                password.fill('0')
                Result.failure(e)
            }
        }

    override suspend fun signInWithEmail(email: String, password: CharArray): Result<AuthUser> =
        withContext(Dispatchers.IO) {
            try {
                val pwdString = String(password)
                val authResult = firebaseAuth.signInWithEmailAndPassword(email.trim(), pwdString).await()
                password.fill('0')

                val user = authResult.user
                    ?: return@withContext Result.failure(IllegalStateException("Sign in returned null user"))
                Result.success(user.toDomainUser())
            } catch (e: Exception) {
                password.fill('0')
                Result.failure(e)
            }
        }

    override suspend fun signInWithGoogle(context: Context): Result<AuthUser> {
        return try {
            val serverClientId = context.getString(R.string.default_web_client_id)

            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                withContext(Dispatchers.IO) {
                    try {
                        val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                        val authResult = firebaseAuth.signInWithCredential(authCredential).await()
                        val user = authResult.user
                        if (user != null) {
                            Log.i("AuthRepository", "Firebase authentication succeeded for user: ${user.uid}")
                            Result.success(user.toDomainUser())
                        } else {
                            Log.e("AuthRepository", "Firebase authentication failed: null user returned")
                            Result.failure(IllegalStateException("Google sign in returned null user"))
                        }
                    } catch (e: Exception) {
                        Log.e("AuthRepository", "Firebase authentication failed: class=${e::class.simpleName}, msg=${e.message?.take(100)}")
                        Result.failure(e)
                    }
                }
            } else {
                Log.e("AuthRepository", "Unrecognized credential type: ${credential.type}")
                Result.failure(IllegalStateException("Unrecognized credential type: ${credential.type}"))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("AuthRepository", "Google Sign-In cancelled: ${e::class.simpleName} - ${e.message?.take(100)}")
            Result.failure(CancellationException("Google Sign-In was cancelled"))
        } catch (e: GetCredentialException) {
            Log.e("AuthRepository", "Google Sign-In failed [Credential]: class=${e::class.simpleName}, type=${e.type}, msg=${e.message?.take(100)}")
            Result.failure(Exception("Google Sign-In failed: ${e.message?.take(100) ?: "Authentication error"}", e))
        } catch (e: CancellationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Google Sign-In failed [General]: class=${e::class.simpleName}, msg=${e.message?.take(100)}")
            Result.failure(e)
        }
    }

    override suspend fun sendEmailVerification(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = firebaseAuth.currentUser
                ?: return@withContext Result.failure(IllegalStateException("No authenticated user"))
            user.sendEmailVerification().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reloadUser(): Result<AuthUser?> = withContext(Dispatchers.IO) {
        try {
            val user = firebaseAuth.currentUser ?: return@withContext Result.success(null)
            user.reload().await()
            val reloadedUser = firebaseAuth.currentUser
            _authState.value = if (reloadedUser != null) {
                AuthState.Authenticated(
                    uid = reloadedUser.uid,
                    email = reloadedUser.email ?: "",
                    isEmailVerified = reloadedUser.isEmailVerified,
                    displayName = reloadedUser.displayName
                )
            } else {
                AuthState.Unauthenticated
            }
            Result.success(reloadedUser?.toDomainUser())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firebaseAuth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firebaseAuth.signOut()
            _authState.value = AuthState.Unauthenticated
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun FirebaseUser.toDomainUser(): AuthUser {
        return AuthUser(
            uid = uid,
            email = email ?: "",
            isEmailVerified = isEmailVerified,
            displayName = displayName
        )
    }
}
