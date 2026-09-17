package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.core.model.StoredCredential
import com.example.core.security.CryptoManager
import com.example.core.security.EncryptedPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

class VaultPreferencesDataSource(
    private val context: Context,
    private val cryptoManager: CryptoManager
) {
    companion object {
        private const val PREFS_NAME = "vault_security_storage"
        private const val KEY_ENCRYPTED_IV = "enc_credential_iv"
        private const val KEY_ENCRYPTED_DATA = "enc_credential_data"
        private const val KEY_PIN_SET = "vault_pin_is_set"
        private const val KEY_USE_EXTERNAL_VIDEO_PLAYER = "use_external_video_player"
        private const val KEY_PERSISTENT_RECORDING_NOTIFICATION = "persistent_recording_notification"

        private const val KEY_PANIC_PIN_ENABLED = "vault_panic_pin_enabled"
        private const val KEY_PANIC_PIN_SET = "vault_panic_pin_is_set"
        private const val KEY_PANIC_ENCRYPTED_IV = "enc_panic_credential_iv"
        private const val KEY_PANIC_ENCRYPTED_DATA = "enc_panic_credential_data"
    }

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _isPanicPinConfiguredFlow = MutableStateFlow(hasConfiguredPanicPin())
    val isPanicPinConfiguredFlow: Flow<Boolean> = _isPanicPinConfiguredFlow.asStateFlow()

    fun isPanicPinEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_PANIC_PIN_ENABLED, false)
    }

    fun setPanicPinEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_PANIC_PIN_ENABLED, enabled).apply()
        _isPanicPinConfiguredFlow.value = hasConfiguredPanicPin()
    }

    fun hasConfiguredPanicPin(): Boolean {
        return isPanicPinEnabled() &&
                sharedPreferences.getBoolean(KEY_PANIC_PIN_SET, false) &&
                sharedPreferences.contains(KEY_PANIC_ENCRYPTED_DATA)
    }

    suspend fun savePanicCredential(credential: StoredCredential) = withContext(Dispatchers.IO) {
        val buffer = ByteBuffer.allocate(4 + credential.salt.size + 4 + credential.pinHash.size)
        buffer.putInt(credential.salt.size)
        buffer.put(credential.salt)
        buffer.putInt(credential.pinHash.size)
        buffer.put(credential.pinHash)

        val encryptedPayload = cryptoManager.encryptWithKeystore(buffer.array())

        sharedPreferences.edit()
            .putString(KEY_PANIC_ENCRYPTED_IV, encryptedPayload.ivBase64)
            .putString(KEY_PANIC_ENCRYPTED_DATA, encryptedPayload.cipherBase64)
            .putBoolean(KEY_PANIC_PIN_SET, true)
            .putBoolean(KEY_PANIC_PIN_ENABLED, true)
            .apply()

        _isPanicPinConfiguredFlow.value = true
    }

    suspend fun getPanicCredential(): StoredCredential? = withContext(Dispatchers.IO) {
        if (!hasConfiguredPanicPin()) return@withContext null

        val iv = sharedPreferences.getString(KEY_PANIC_ENCRYPTED_IV, null) ?: return@withContext null
        val data = sharedPreferences.getString(KEY_PANIC_ENCRYPTED_DATA, null) ?: return@withContext null

        try {
            val decryptedBytes = cryptoManager.decryptWithKeystore(
                EncryptedPayload(ivBase64 = iv, cipherBase64 = data)
            )
            val buffer = ByteBuffer.wrap(decryptedBytes)
            val saltLen = buffer.int
            val salt = ByteArray(saltLen)
            buffer.get(salt)
            val hashLen = buffer.int
            val hash = ByteArray(hashLen)
            buffer.get(hash)
            StoredCredential(salt = salt, pinHash = hash)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun removePanicPin() = withContext(Dispatchers.IO) {
        sharedPreferences.edit()
            .remove(KEY_PANIC_ENCRYPTED_IV)
            .remove(KEY_PANIC_ENCRYPTED_DATA)
            .putBoolean(KEY_PANIC_PIN_SET, false)
            .putBoolean(KEY_PANIC_PIN_ENABLED, false)
            .apply()
        _isPanicPinConfiguredFlow.value = false
    }

    private val _isPersistentNotificationFlow = MutableStateFlow(isPersistentRecordingNotificationEnabled())
    val isPersistentNotificationFlow: Flow<Boolean> = _isPersistentNotificationFlow.asStateFlow()

    fun isPersistentRecordingNotificationEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_PERSISTENT_RECORDING_NOTIFICATION, false)
    }

    fun setPersistentRecordingNotificationEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_PERSISTENT_RECORDING_NOTIFICATION, enabled).apply()
        _isPersistentNotificationFlow.value = enabled
    }

    fun isUseExternalVideoPlayer(): Boolean {
        return sharedPreferences.getBoolean(KEY_USE_EXTERNAL_VIDEO_PLAYER, false)
    }

    fun setUseExternalVideoPlayer(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_USE_EXTERNAL_VIDEO_PLAYER, enabled).apply()
    }

    private val _isPinConfiguredFlow = MutableStateFlow(hasConfiguredPin())
    val isPinConfiguredFlow: Flow<Boolean> = _isPinConfiguredFlow.asStateFlow()

    fun hasConfiguredPin(): Boolean {
        return sharedPreferences.getBoolean(KEY_PIN_SET, false) &&
                sharedPreferences.contains(KEY_ENCRYPTED_DATA)
    }

    suspend fun saveCredential(credential: StoredCredential) = withContext(Dispatchers.IO) {
        // Serialize salt (16 bytes) + pinHash (32 bytes)
        val buffer = ByteBuffer.allocate(4 + credential.salt.size + 4 + credential.pinHash.size)
        buffer.putInt(credential.salt.size)
        buffer.put(credential.salt)
        buffer.putInt(credential.pinHash.size)
        buffer.put(credential.pinHash)

        val encryptedPayload = cryptoManager.encryptWithKeystore(buffer.array())

        sharedPreferences.edit()
            .putString(KEY_ENCRYPTED_IV, encryptedPayload.ivBase64)
            .putString(KEY_ENCRYPTED_DATA, encryptedPayload.cipherBase64)
            .putBoolean(KEY_PIN_SET, true)
            .apply()

        _isPinConfiguredFlow.value = true
    }

    suspend fun getCredential(): StoredCredential? = withContext(Dispatchers.IO) {
        if (!hasConfiguredPin()) return@withContext null

        val iv = sharedPreferences.getString(KEY_ENCRYPTED_IV, null) ?: return@withContext null
        val data = sharedPreferences.getString(KEY_ENCRYPTED_DATA, null) ?: return@withContext null

        try {
            val decryptedBytes = cryptoManager.decryptWithKeystore(
                EncryptedPayload(ivBase64 = iv, cipherBase64 = data)
            )
            val buffer = ByteBuffer.wrap(decryptedBytes)
            val saltLen = buffer.int
            val salt = ByteArray(saltLen)
            buffer.get(salt)
            val hashLen = buffer.int
            val hash = ByteArray(hashLen)
            buffer.get(hash)
            StoredCredential(salt = salt, pinHash = hash)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        sharedPreferences.edit().clear().apply()
        _isPinConfiguredFlow.value = false
    }
}
