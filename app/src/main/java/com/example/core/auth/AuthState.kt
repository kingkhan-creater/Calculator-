package com.example.core.auth

sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(
        val uid: String,
        val email: String,
        val isEmailVerified: Boolean,
        val displayName: String? = null
    ) : AuthState
}

data class AuthUser(
    val uid: String,
    val email: String,
    val isEmailVerified: Boolean,
    val displayName: String? = null
)
