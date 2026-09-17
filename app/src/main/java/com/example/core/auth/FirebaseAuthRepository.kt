package com.example.core.auth

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

interface FirebaseAuthRepository {
    val authState: StateFlow<AuthState>
    val currentUser: AuthUser?

    suspend fun signUpWithEmail(email: String, password: CharArray): Result<AuthUser>
    suspend fun signInWithEmail(email: String, password: CharArray): Result<AuthUser>
    suspend fun signInWithGoogle(context: Context): Result<AuthUser>
    suspend fun sendEmailVerification(): Result<Unit>
    suspend fun reloadUser(): Result<AuthUser?>
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
}
