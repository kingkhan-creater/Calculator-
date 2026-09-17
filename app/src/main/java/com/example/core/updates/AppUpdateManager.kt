package com.example.core.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.BuildConfig
import com.example.core.admin.AdminRepository
import com.example.core.admin.AppUpdateConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class AppUpdateState {
    data object Checking : AppUpdateState()
    data class UpdateAvailable(
        val config: AppUpdateConfig,
        val isMandatory: Boolean,
        val currentVersionCode: Int = BuildConfig.VERSION_CODE,
        val currentVersionName: String = BuildConfig.VERSION_NAME
    ) : AppUpdateState()
    data class UpToDate(
        val currentVersionCode: Int = BuildConfig.VERSION_CODE,
        val currentVersionName: String = BuildConfig.VERSION_NAME
    ) : AppUpdateState()
    data class Error(val message: String) : AppUpdateState()
}

class AppUpdateManager(
    private val adminRepository: AdminRepository
) {
    suspend fun checkForUpdates(): AppUpdateState = withContext(Dispatchers.IO) {
        val currentVersionCode = BuildConfig.VERSION_CODE
        val currentVersionName = BuildConfig.VERSION_NAME

        val result = adminRepository.getAppUpdateConfig()
        if (result.isSuccess) {
            val config = result.getOrNull()
            if (config != null && config.versionCode > currentVersionCode && config.apkUrl.isNotBlank()) {
                val isMandatory = config.isMandatory || currentVersionCode < config.minSupportedVersionCode
                return@withContext AppUpdateState.UpdateAvailable(
                    config = config,
                    isMandatory = isMandatory,
                    currentVersionCode = currentVersionCode,
                    currentVersionName = currentVersionName
                )
            } else {
                return@withContext AppUpdateState.UpToDate(
                    currentVersionCode = currentVersionCode,
                    currentVersionName = currentVersionName
                )
            }
        } else {
            return@withContext AppUpdateState.Error(
                result.exceptionOrNull()?.localizedMessage ?: "Failed to verify update status."
            )
        }
    }

    fun openApkDownloadLink(context: Context, apkUrl: String) {
        try {
            if (apkUrl.isBlank()) {
                Toast.makeText(context, "No download link configured.", Toast.LENGTH_SHORT).show()
                return
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open browser: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
