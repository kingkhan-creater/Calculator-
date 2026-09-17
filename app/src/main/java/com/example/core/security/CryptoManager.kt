package com.example.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * Handles cryptographic operations for the Vault PIN authentication:
 * - PBKDF2-HMAC-SHA256 password hashing with random cryptographic salt.
 * - Constant-time comparison to prevent timing side-channel attacks.
 * - Hardware-backed Android KeyStore AES-256-GCM encryption for persistent credentials.
 * - Never stores or exposes plain-text PINs.
 */
class CryptoManager {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "CalculatorVaultMasterKey_v1"
        private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val PBKDF2_ITERATIONS = 12000
        private const val PBKDF2_KEY_LENGTH = 256
        private const val SALT_LENGTH = 16
    }

    private val isAndroidKeyStoreAvailable: Boolean = try {
        KeyStore.getInstance(ANDROID_KEYSTORE)
        true
    } catch (_: Exception) {
        false
    }

    private val keyStore: KeyStore = if (isAndroidKeyStoreAvailable) {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    } else {
        KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
    }

    private var fallbackJvmKey: SecretKey? = null

    init {
        getOrCreateMasterKey()
    }

    @Synchronized
    private fun getOrCreateMasterKey(): SecretKey {
        if (fallbackJvmKey != null) {
            return fallbackJvmKey!!
        }

        val existingKey = try {
            (keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        } catch (_: Exception) {
            null
        }
        if (existingKey != null) {
            return existingKey
        }

        return if (isAndroidKeyStoreAvailable) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        } else {
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(256, SecureRandom())
            val secretKey = keyGenerator.generateKey()
            fallbackJvmKey = secretKey
            secretKey
        }
    }

    /**
     * Generates a secure random 16-byte salt.
     */
    fun generateSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH)
        random.nextBytes(salt)
        return salt
    }

    /**
     * Derives a cryptographic hash of the input PIN using PBKDF2WithHmacSHA256.
     */
    fun hashPin(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    /**
     * Constant-time comparison between entered PIN hash and stored PIN hash.
     */
    fun verifyPin(enteredPin: CharArray, salt: ByteArray, expectedHash: ByteArray): Boolean {
        val enteredHash = hashPin(enteredPin, salt)
        return MessageDigest.isEqual(enteredHash, expectedHash)
    }

    /**
     * Encrypts arbitrary bytes using the Keystore AES-GCM Master Key.
     */
    fun encryptWithKeystore(data: ByteArray): EncryptedPayload {
        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(data)
        return EncryptedPayload(
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            cipherBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        )
    }

    /**
     * Decrypts ciphertext using the Keystore AES-GCM Master Key.
     */
    fun decryptWithKeystore(payload: EncryptedPayload): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        val iv = Base64.decode(payload.ivBase64, Base64.NO_WRAP)
        val ciphertext = Base64.decode(payload.cipherBase64, Base64.NO_WRAP)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateMasterKey(), spec)
        return cipher.doFinal(ciphertext)
    }
}

data class EncryptedPayload(
    val ivBase64: String,
    val cipherBase64: String
)
