package com.example.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.auth.AuthState
import com.example.core.auth.FirebaseAuthRepository
import com.example.core.firestore.FirestoreVaultRepository
import com.example.core.firestore.UserProfileDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val authState: AuthState = AuthState.Loading,
    val userProfile: UserProfileDocument? = null,
    val isLoading: Boolean = false,
    val isSignUpMode: Boolean = false,
    val isResetPasswordMode: Boolean = false,
    val emailInput: String = "",
    val passwordInput: String = "",
    val confirmPasswordInput: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class AuthViewModel(
    private val authRepository: FirebaseAuthRepository,
    private val firestoreRepository: FirestoreVaultRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.authState.collect { state ->
                _uiState.update { it.copy(authState = state) }
                if (state is AuthState.Authenticated) {
                    loadUserProfile(state.uid, state.email, state.isEmailVerified)
                } else {
                    _uiState.update { it.copy(userProfile = null) }
                }
            }
        }
    }

    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(emailInput = email, errorMessage = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(passwordInput = password, errorMessage = null) }
    }

    fun onConfirmPasswordChanged(password: String) {
        _uiState.update { it.copy(confirmPasswordInput = password, errorMessage = null) }
    }

    fun toggleMode(isSignUp: Boolean) {
        _uiState.update {
            it.copy(
                isSignUpMode = isSignUp,
                isResetPasswordMode = false,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun openResetPasswordMode() {
        _uiState.update {
            it.copy(
                isResetPasswordMode = true,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun closeResetPasswordMode() {
        _uiState.update {
            it.copy(
                isResetPasswordMode = false,
                errorMessage = null
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun signIn() {
        val email = _uiState.value.emailInput.trim()
        val password = _uiState.value.passwordInput

        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter both email and password") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val pwdChars = password.toCharArray()
            val result = authRepository.signInWithEmail(email, pwdChars)
            _uiState.update { it.copy(isLoading = false) }

            result.onSuccess { user ->
                _uiState.update {
                    it.copy(
                        passwordInput = "",
                        confirmPasswordInput = "",
                        successMessage = "Signed in successfully"
                    )
                }
                loadUserProfile(user.uid, user.email, user.isEmailVerified)
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.localizedMessage ?: "Sign in failed") }
            }
        }
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.signInWithGoogle(context)
            _uiState.update { it.copy(isLoading = false) }

            result.onSuccess { user ->
                _uiState.update {
                    it.copy(
                        passwordInput = "",
                        confirmPasswordInput = "",
                        successMessage = "Signed in with Google successfully"
                    )
                }
                loadUserProfile(user.uid, user.email, user.isEmailVerified)
            }.onFailure { error ->
                if (error !is CancellationException) {
                    _uiState.update { it.copy(errorMessage = error.localizedMessage ?: "Google Sign-In failed") }
                }
            }
        }
    }

    fun signUp() {
        val email = _uiState.value.emailInput.trim()
        val password = _uiState.value.passwordInput
        val confirmPassword = _uiState.value.confirmPasswordInput

        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please fill in all fields") }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters") }
            return
        }
        if (password != confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Passwords do not match") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val pwdChars = password.toCharArray()
            val result = authRepository.signUpWithEmail(email, pwdChars)
            _uiState.update { it.copy(isLoading = false) }

            result.onSuccess { user ->
                _uiState.update {
                    it.copy(
                        passwordInput = "",
                        confirmPasswordInput = "",
                        successMessage = "Account created. Verification email sent!"
                    )
                }
                loadUserProfile(user.uid, user.email, user.isEmailVerified)
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.localizedMessage ?: "Sign up failed") }
            }
        }
    }

    fun sendEmailVerification() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.sendEmailVerification()
            _uiState.update { it.copy(isLoading = false) }

            result.onSuccess {
                _uiState.update { it.copy(successMessage = "Verification email sent to your inbox") }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.localizedMessage ?: "Failed to send email verification") }
            }
        }
    }

    fun checkEmailVerificationStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = authRepository.reloadUser()
            _uiState.update { it.copy(isLoading = false) }

            result.onSuccess { user ->
                if (user != null) {
                    if (user.isEmailVerified) {
                        _uiState.update { it.copy(successMessage = "Email verified successfully!") }
                        firestoreRepository.syncUserProfile(user.uid, user.email, isEmailVerified = true)
                    } else {
                        _uiState.update { it.copy(errorMessage = "Email not verified yet. Please check your inbox.") }
                    }
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.localizedMessage ?: "Failed to refresh user status") }
            }
        }
    }

    fun sendPasswordReset() {
        val email = _uiState.value.emailInput.trim()
        if (email.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your registered email") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.sendPasswordResetEmail(email)
            _uiState.update { it.copy(isLoading = false) }

            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isResetPasswordMode = false,
                        successMessage = "Password reset instructions sent to $email"
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.localizedMessage ?: "Failed to send password reset email") }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            authRepository.signOut()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    userProfile = null,
                    emailInput = "",
                    passwordInput = "",
                    confirmPasswordInput = "",
                    successMessage = "Logged out successfully"
                )
            }
        }
    }

    private fun loadUserProfile(uid: String, email: String, isEmailVerified: Boolean) {
        viewModelScope.launch {
            firestoreRepository.syncUserProfile(uid, email, isEmailVerified)
            val profileResult = firestoreRepository.getUserProfile(uid)
            profileResult.onSuccess { profile ->
                _uiState.update { it.copy(userProfile = profile) }
            }
        }
    }

    class Factory(
        private val authRepository: FirebaseAuthRepository,
        private val firestoreRepository: FirestoreVaultRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(authRepository, firestoreRepository) as T
        }
    }
}
