package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.security.VaultFileEncryptor
import com.example.data.storage.VaultStorageManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultSecurityHardeningTest {

    private lateinit var context: Context
    private lateinit var encryptor: VaultFileEncryptor
    private lateinit var tempDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        encryptor = VaultFileEncryptor("TestVaultMediaKey_${UUID.randomUUID()}")
        tempDir = File(context.cacheDir, "vault_test_${UUID.randomUUID()}").apply { mkdirs() }
    }

    @Test
    fun `encrypt and decrypt byte data returns original content`() {
        val originalText = "Super Secret Sensitive Vault Photo/Video Raw Binary Content 2026!"
        val originalBytes = originalText.toByteArray(Charsets.UTF_8)

        val encryptedFile = File(tempDir, "test_encrypted.bin")
        encryptor.encryptStreamToFile(ByteArrayInputStream(originalBytes), encryptedFile)

        // Verify encrypted file exists and has magic header
        assertTrue(encryptedFile.exists())
        assertTrue(encryptor.isEncrypted(encryptedFile))

        // Raw bytes on disk must NOT contain plaintext string
        val diskBytes = encryptedFile.readBytes()
        assertFalse(String(diskBytes).contains(originalText))

        // Decrypt and verify matching bytes
        val decryptedBytes = encryptor.decryptFileToBytes(encryptedFile)
        assertArrayEquals(originalBytes, decryptedBytes)
        assertEquals(originalText, String(decryptedBytes, Charsets.UTF_8))
    }

    @Test
    fun `tampered ciphertext throws security exception`() {
        val originalBytes = "Important Confidential Data".toByteArray(Charsets.UTF_8)
        val encryptedFile = File(tempDir, "tamper_test.bin")
        encryptor.encryptStreamToFile(ByteArrayInputStream(originalBytes), encryptedFile)

        val rawDiskBytes = encryptedFile.readBytes()
        // Tamper with the ciphertext byte (beyond the 17-byte header)
        if (rawDiskBytes.size > 20) {
            rawDiskBytes[20] = (rawDiskBytes[20].toInt() xor 0xFF).toByte()
        }
        encryptedFile.writeBytes(rawDiskBytes)

        // Attempting to decrypt tampered authenticated ciphertext must fail
        assertThrows(SecurityException::class.java) {
            encryptor.decryptFileToBytes(encryptedFile)
        }
    }

    @Test
    fun `legacy unencrypted files are safely migrated in-place`() {
        val legacyDir = File(tempDir, "legacy_vault").apply { mkdirs() }
        val legacyFile1 = File(legacyDir, "legacy1.jpg")
        val legacyFile2 = File(legacyDir, "legacy2.mp4")

        val content1 = "Legacy unencrypted photo bytes".toByteArray(Charsets.UTF_8)
        val content2 = "Legacy unencrypted video bytes".toByteArray(Charsets.UTF_8)

        legacyFile1.writeBytes(content1)
        legacyFile2.writeBytes(content2)

        assertFalse(encryptor.isEncrypted(legacyFile1))
        assertFalse(encryptor.isEncrypted(legacyFile2))

        // Run migration
        val migrated = encryptor.migrateDirectory(legacyDir)
        assertEquals(2, migrated)

        // Both files must now be encrypted with magic header
        assertTrue(encryptor.isEncrypted(legacyFile1))
        assertTrue(encryptor.isEncrypted(legacyFile2))

        // Decryption must return exact original contents
        assertArrayEquals(content1, encryptor.decryptFileToBytes(legacyFile1))
        assertArrayEquals(content2, encryptor.decryptFileToBytes(legacyFile2))
    }

    @Test
    fun `storage manager initializes with encrypted disk structure`() = runTest {
        val storageManager = VaultStorageManager(context, encryptor)
        val vaultDir = File(context.filesDir, "vault_media")
        assertTrue(vaultDir.exists())

        val noMedia = File(vaultDir, ".nomedia")
        assertTrue(noMedia.exists())
    }
}
