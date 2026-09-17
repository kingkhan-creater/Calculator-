package com.example.core.security

import com.example.core.model.SecurityResult
import kotlinx.coroutines.flow.Flow

enum class PinVerificationResult {
    INVALID,
    MASTER,
    PANIC
}

interface SecurityRepository {
    fun isPinConfigured(): Flow<Boolean>
    suspend fun checkIsPinConfiguredSync(): Boolean
    suspend fun verifyPin(pin: String): Boolean
    suspend fun verifyPinType(pin: String): PinVerificationResult
    suspend fun setupPin(pin: String): SecurityResult
    suspend fun changePin(currentPin: String, newPin: String): SecurityResult
    
    // Panic PIN (Decoy Vault) operations
    fun isPanicPinConfigured(): Flow<Boolean>
    suspend fun checkIsPanicPinConfiguredSync(): Boolean
    fun isPanicPinEnabled(): Boolean
    fun setPanicPinEnabled(enabled: Boolean)
    suspend fun setupPanicPin(pin: String): SecurityResult
    suspend fun changePanicPin(currentPin: String, newPanicPin: String): SecurityResult
    suspend fun removePanicPin()
    
    suspend fun clearVaultCredentials()
}
