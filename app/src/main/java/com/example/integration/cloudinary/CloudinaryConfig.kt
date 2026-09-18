package com.example.integration.cloudinary

import android.content.Context
import java.security.MessageDigest

data class CloudinaryAccount(
    val cloudName: String = "",
    val uploadPreset: String = "",
    val label: String = "",
    val enabled: Boolean = true
) {
    val isValid: Boolean
        get() = cloudName.isNotBlank() && uploadPreset.isNotBlank() && enabled
}

data class CloudinaryConfig(
    val cloudName: String = "",
    val uploadPreset: String = "",
    val apiKey: String = "",
    val apiSecret: String = "",
    val accounts: List<CloudinaryAccount> = emptyList()
) {
    val isConfigured: Boolean
        get() = (cloudName.isNotBlank() && uploadPreset.isNotBlank()) || accounts.any { it.isValid }

    /**
     * Returns an ordered list of active Cloudinary accounts for resilient failover.
     */
    fun getActiveAccountsPool(): List<CloudinaryAccount> {
        val pool = accounts.filter { it.isValid }.toMutableList()
        if (pool.isEmpty() && cloudName.isNotBlank() && uploadPreset.isNotBlank()) {
            pool.add(CloudinaryAccount(cloudName, uploadPreset, "Primary", true))
        }
        return pool
    }
}

object CloudinaryConfigManager {
    private const val PREFS_NAME = "vault_cloudinary_config"
    private const val KEY_CLOUD_NAME = "cloudinary_cloud_name"
    private const val KEY_UPLOAD_PRESET = "cloudinary_upload_preset"
    private const val KEY_API_KEY = "cloudinary_api_key"
    private const val KEY_API_SECRET = "cloudinary_api_secret"

    fun getConfig(context: Context): CloudinaryConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return CloudinaryConfig(
            cloudName = prefs.getString(KEY_CLOUD_NAME, "") ?: "",
            uploadPreset = prefs.getString(KEY_UPLOAD_PRESET, "") ?: "",
            apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
            apiSecret = prefs.getString(KEY_API_SECRET, "") ?: ""
        )
    }

    fun saveConfig(context: Context, config: CloudinaryConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_CLOUD_NAME, config.cloudName.trim())
            .putString(KEY_UPLOAD_PRESET, config.uploadPreset.trim())
            .putString(KEY_API_KEY, config.apiKey.trim())
            .putString(KEY_API_SECRET, config.apiSecret.trim())
            .apply()
    }

    /**
     * Computes SHA-1 hash for Cloudinary signed uploads
     */
    fun generateSha1(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
