package com.example.data

import com.example.core.model.SecurityResult
import com.example.core.model.StoredCredential
import com.example.core.security.CryptoManager
import com.example.core.security.PinVerificationResult
import com.example.core.security.SecurityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SecurityRepositoryImpl(
    private val preferencesDataSource: VaultPreferencesDataSource,
    private val cryptoManager: CryptoManager
) : SecurityRepository {

    override fun isPinConfigured(): Flow<Boolean> {
        return preferencesDataSource.isPinConfiguredFlow
    }

    override suspend fun checkIsPinConfiguredSync(): Boolean {
        return preferencesDataSource.hasConfiguredPin()
    }

    override suspend fun verifyPin(pin: String): Boolean {
        return verifyPinType(pin) != PinVerificationResult.INVALID
    }

    override suspend fun verifyPinType(pin: String): PinVerificationResult = withContext(Dispatchers.Default) {
        val pinChars = pin.toCharArray()
        try {
            // 1. Check Master PIN
            val masterCred = preferencesDataSource.getCredential()
            if (masterCred != null) {
                val isMaster = cryptoManager.verifyPin(
                    enteredPin = pinChars,
                    salt = masterCred.salt,
                    expectedHash = masterCred.pinHash
                )
                if (isMaster) {
                    return@withContext PinVerificationResult.MASTER
                }
            }

            // 2. Check Panic PIN (if enabled)
            if (preferencesDataSource.isPanicPinEnabled()) {
                val panicCred = preferencesDataSource.getPanicCredential()
                if (panicCred != null) {
                    val isPanic = cryptoManager.verifyPin(
                        enteredPin = pinChars,
                        salt = panicCred.salt,
                        expectedHash = panicCred.pinHash
                    )
                    if (isPanic) {
                        return@withContext PinVerificationResult.PANIC
                    }
                }
            }

            PinVerificationResult.INVALID
        } finally {
            pinChars.fill('\u0000')
        }
    }

    override suspend fun setupPin(pin: String): SecurityResult = withContext(Dispatchers.Default) {
        if (pin.length < 4) {
            return@withContext SecurityResult.Error("PIN must be at least 4 digits")
        }
        if (pin.length > 12) {
            return@withContext SecurityResult.Error("PIN cannot exceed 12 digits")
        }

        val salt = cryptoManager.generateSalt()
        val pinChars = pin.toCharArray()
        val hash = try {
            cryptoManager.hashPin(pinChars, salt)
        } finally {
            pinChars.fill('\u0000')
        }

        preferencesDataSource.saveCredential(
            StoredCredential(salt = salt, pinHash = hash)
        )
        SecurityResult.Success
    }

    override suspend fun changePin(currentPin: String, newPin: String): SecurityResult = withContext(Dispatchers.Default) {
        val isValid = verifyPinType(currentPin) == PinVerificationResult.MASTER
        if (!isValid) {
            return@withContext SecurityResult.Error("Current Master PIN is incorrect")
        }
        setupPin(newPin)
    }

    override fun isPanicPinConfigured(): Flow<Boolean> {
        return preferencesDataSource.isPanicPinConfiguredFlow
    }

    override suspend fun checkIsPanicPinConfiguredSync(): Boolean {
        return preferencesDataSource.hasConfiguredPanicPin()
    }

    override fun isPanicPinEnabled(): Boolean {
        return preferencesDataSource.isPanicPinEnabled()
    }

    override fun setPanicPinEnabled(enabled: Boolean) {
        preferencesDataSource.setPanicPinEnabled(enabled)
    }

    override suspend fun setupPanicPin(pin: String): SecurityResult = withContext(Dispatchers.Default) {
        if (pin.length < 4) {
            return@withContext SecurityResult.Error("Panic PIN must be at least 4 digits")
        }
        if (pin.length > 12) {
            return@withContext SecurityResult.Error("Panic PIN cannot exceed 12 digits")
        }

        // Ensure Panic PIN is not identical to Master PIN
        val isMaster = verifyPinType(pin) == PinVerificationResult.MASTER
        if (isMaster) {
            return@withContext SecurityResult.Error("Panic PIN cannot be the same as your Master PIN")
        }

        val salt = cryptoManager.generateSalt()
        val pinChars = pin.toCharArray()
        val hash = try {
            cryptoManager.hashPin(pinChars, salt)
        } finally {
            pinChars.fill('\u0000')
        }

        preferencesDataSource.savePanicCredential(
            StoredCredential(salt = salt, pinHash = hash)
        )
        SecurityResult.Success
    }

    override suspend fun changePanicPin(currentPin: String, newPanicPin: String): SecurityResult = withContext(Dispatchers.Default) {
        val result = verifyPinType(currentPin)
        if (result == PinVerificationResult.INVALID) {
            return@withContext SecurityResult.Error("Current PIN is incorrect")
        }
        setupPanicPin(newPanicPin)
    }

    override suspend fun removePanicPin() {
        preferencesDataSource.removePanicPin()
    }

    override suspend fun clearVaultCredentials() {
        preferencesDataSource.clear()
    }
}
