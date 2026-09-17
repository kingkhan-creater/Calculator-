package com.example.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Handles AES-256-GCM authenticated encryption for all vault media files (photos, videos, thumbnails).
 *
 * File Structure on Disk:
 * [4 bytes MAGIC ("VALT")] + [1 byte VERSION (0x01)] + [12 bytes IV] + [CIPHERTEXT + 16 bytes GCM AUTH TAG]
 */
class VaultFileEncryptor(
    private val keyStoreAlias: String = MEDIA_KEY_ALIAS
) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MEDIA_KEY_ALIAS = "CalculatorVaultMediaMasterKey_v1"
        private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val BUFFER_SIZE = 8192

        val MAGIC_HEADER = byteArrayOf('V'.code.toByte(), 'A'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte())
        const val VERSION_BYTE: Byte = 0x01
        val HEADER_SIZE = MAGIC_HEADER.size + 1 + GCM_IV_LENGTH // 4 + 1 + 12 = 17 bytes
    }

    private val secureRandom = SecureRandom()

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
        getOrCreateSecretKey()
    }

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        if (fallbackJvmKey != null) {
            return fallbackJvmKey!!
        }

        val existingKey = try {
            (keyStore.getEntry(keyStoreAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
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
                keyStoreAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(false) // We generate fresh 12-byte IVs explicitly with SecureRandom
                .build()

            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        } else {
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(256, secureRandom)
            val secretKey = keyGenerator.generateKey()
            fallbackJvmKey = secretKey
            secretKey
        }
    }

    /**
     * Checks whether a given file begins with the authenticated VAULT magic header and version.
     */
    fun isEncrypted(file: File): Boolean {
        if (!file.exists() || file.length() < HEADER_SIZE) return false
        return try {
            FileInputStream(file).use { input ->
                val magic = ByteArray(MAGIC_HEADER.size)
                val readMagic = input.read(magic)
                if (readMagic != MAGIC_HEADER.size || !magic.contentEquals(MAGIC_HEADER)) {
                    return false
                }
                val version = input.read()
                version == VERSION_BYTE.toInt()
            }
        } catch (_: Exception) {
            false
        }
    }

    fun isVaultOrEncryptedFile(file: File): Boolean {
        return file.parentFile?.name == "vault_media" || isEncrypted(file)
    }

    /**
     * Encrypts input stream content directly into destination file with AES-256-GCM.
     */
    fun encryptStreamToFile(inputStream: InputStream, destinationFile: File) {
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey(), spec)

        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.tmp_enc")
        try {
            FileOutputStream(tempFile).use { fileOut ->
                // Write Header: MAGIC (4 bytes) + VERSION (1 byte) + IV (12 bytes)
                fileOut.write(MAGIC_HEADER)
                fileOut.write(VERSION_BYTE.toInt())
                fileOut.write(iv)

                CipherOutputStream(fileOut, cipher).use { cipherOut ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        cipherOut.write(buffer, 0, bytesRead)
                    }
                    cipherOut.flush()
                }
                fileOut.flush()
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                if (destinationFile.exists()) {
                    destinationFile.delete()
                }
                if (!tempFile.renameTo(destinationFile)) {
                    tempFile.copyTo(destinationFile, overwrite = true)
                    tempFile.delete()
                }
            } else {
                throw SecurityException("Failed to produce valid encrypted output file.")
            }
        } catch (e: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            throw SecurityException("Encryption failed for ${destinationFile.name}: ${e.message}", e)
        }
    }

    /**
     * Encrypts an existing plain-text file in-place into an authenticated encrypted vault file.
     */
    fun encryptFileInPlace(file: File) {
        if (isEncrypted(file)) return // Already encrypted

        FileInputStream(file).use { input ->
            encryptStreamToFile(input, file)
        }
    }

    /**
     * Decrypts an encrypted vault file into memory as a ByteArray.
     * If the file is legacy unencrypted, safely returns raw bytes.
     */
    fun decryptFileToBytes(file: File): ByteArray {
        if (!file.exists()) {
            throw IllegalArgumentException("Vault file does not exist: ${file.absolutePath}")
        }

        if (!isEncrypted(file)) {
            // Safe fallback for legacy unencrypted file
            return file.readBytes()
        }

        try {
            return FileInputStream(file).use { fileIn ->
                // Skip Magic (4 bytes) and Version (1 byte)
                val headerPrefix = ByteArray(5)
                fileIn.read(headerPrefix)

                // Read IV (12 bytes)
                val iv = ByteArray(GCM_IV_LENGTH)
                val ivRead = fileIn.read(iv)
                if (ivRead != GCM_IV_LENGTH) {
                    throw SecurityException("Corrupted encrypted file header in ${file.name}")
                }

                val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)

                val outputStream = ByteArrayOutputStream()
                CipherInputStream(fileIn, cipher).use { cipherIn ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (cipherIn.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
                outputStream.toByteArray()
            }
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            throw SecurityException("Authenticated decryption failed for ${file.name}: ${e.message}", e)
        }
    }

    /**
     * Decrypts an encrypted vault file directly to an output stream.
     */
    fun decryptFileToStream(file: File, outputStream: OutputStream) {
        if (!file.exists()) {
            throw IllegalArgumentException("Vault file does not exist: ${file.absolutePath}")
        }

        if (!isEncrypted(file)) {
            FileInputStream(file).use { input ->
                input.copyTo(outputStream)
            }
            return
        }

        try {
            FileInputStream(file).use { fileIn ->
                val headerPrefix = ByteArray(5)
                fileIn.read(headerPrefix)

                val iv = ByteArray(GCM_IV_LENGTH)
                val ivRead = fileIn.read(iv)
                if (ivRead != GCM_IV_LENGTH) {
                    throw SecurityException("Corrupted encrypted file header in ${file.name}")
                }

                val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)

                CipherInputStream(fileIn, cipher).use { cipherIn ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (cipherIn.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
            }
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            throw SecurityException("Authenticated decryption stream failed for ${file.name}: ${e.message}", e)
        }
    }

    /**
     * Safely migrates all existing unencrypted vault files in the directory.
     */
    fun migrateDirectory(vaultDirectory: File): Int {
        if (!vaultDirectory.exists() || !vaultDirectory.isDirectory) return 0

        var migratedCount = 0
        val files = vaultDirectory.listFiles() ?: return 0
        for (file in files) {
            if (file.isFile && file.name != ".nomedia" && !file.name.endsWith(".tmp_enc")) {
                if (!isEncrypted(file)) {
                    try {
                        encryptFileInPlace(file)
                        migratedCount++
                    } catch (e: Exception) {
                        // Keep original file intact on failure
                    }
                }
            }
        }
        return migratedCount
    }
}
