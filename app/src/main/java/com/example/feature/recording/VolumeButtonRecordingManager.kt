package com.example.feature.recording

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Configurable actions that can be mapped to mobile hardware volume buttons.
 */
enum class VolumeKeyAction(val id: String, val title: String, val description: String) {
    START_PAUSE_RESUME(
        id = "START_PAUSE_RESUME",
        title = "Start / Pause / Resume",
        description = "Starts recording, or pauses/resumes if already recording"
    ),
    STOP(
        id = "STOP",
        title = "Stop Recording",
        description = "Stops recording and saves video securely to vault"
    ),
    NONE(
        id = "NONE",
        title = "Disabled",
        description = "Normal phone volume adjustment"
    );

    companion object {
        fun fromId(id: String?, default: VolumeKeyAction = START_PAUSE_RESUME): VolumeKeyAction {
            return entries.firstOrNull { it.id == id } ?: default
        }
    }
}

/**
 * Manages mobile hardware volume buttons for recording controls (Volume Up / Down).
 * Supports full user configuration in Settings.
 */
object VolumeButtonRecordingManager {

    const val PREFS_NAME = "vault_security_storage"
    const val KEY_ENABLED = "volume_button_controls_enabled"
    const val KEY_VOLUME_UP_ACTION = "volume_up_action"
    const val KEY_VOLUME_DOWN_ACTION = "volume_down_action"
    const val KEY_DISMISS_NOTIFICATION_ON_VOLUME_STOP = "volume_stop_dismiss_notification"

    // Set to true by VolumeButtonAccessibilityService when connected
    @Volatile
    var isAccessibilityServiceRunning: Boolean = false

    // Debounce tracking to prevent hardware double-firing
    private var lastActionTimeMs: Long = 0L
    private const val ACTION_DEBOUNCE_MS: Long = 500L

    // Volume Down long-press tracking for hiding flashlight and locking controls
    private var volumeDownPressStartTime: Long = 0L
    private var volumeDownLongPressTriggered: Boolean = false

    // Callbacks registered by active RecordingScreen composable
    var onScreenStartRecording: (() -> Unit)? = null
    var onScreenStopRecording: (() -> Unit)? = null

    fun isControlsLocked(context: Context): Boolean {
        return FlashlightFloatingOverlayManager.isControlsLocked(context)
    }

    /**
     * Called when the user opens the Calculator Vault (enters Master PIN).
     * Unlocks the button controls and restores the floating flashlight button.
     */
    fun onVaultUnlocked(context: Context) {
        FlashlightFloatingOverlayManager.unlockAndRestore(context)
        vibrate(context, 40L)
    }

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isDismissNotificationOnVolumeStop(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DISMISS_NOTIFICATION_ON_VOLUME_STOP, true)
    }

    fun setDismissNotificationOnVolumeStop(context: Context, dismiss: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DISMISS_NOTIFICATION_ON_VOLUME_STOP, dismiss).apply()
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (isAccessibilityServiceRunning) return true
        return try {
            val expectedServiceName = "${context.packageName}/${VolumeButtonAccessibilityService::class.java.name}"
            val shortServiceName = "${context.packageName}/.feature.recording.VolumeButtonAccessibilityService"
            val enabledServices = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedServiceName, ignoreCase = true) ||
                    componentName.equals(shortServiceName, ignoreCase = true) ||
                    componentName.contains("VolumeButtonAccessibilityService", ignoreCase = true)
                ) {
                    return true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun openAppDetailsSettings(context: Context) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppDetailsSettings(context)
        }
    }

    fun getVolumeUpAction(context: Context): VolumeKeyAction {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_VOLUME_UP_ACTION, VolumeKeyAction.START_PAUSE_RESUME.id)
        return VolumeKeyAction.fromId(raw, VolumeKeyAction.START_PAUSE_RESUME)
    }

    fun setVolumeUpAction(context: Context, action: VolumeKeyAction) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_VOLUME_UP_ACTION, action.id).apply()
    }

    fun getVolumeDownAction(context: Context): VolumeKeyAction {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_VOLUME_DOWN_ACTION, VolumeKeyAction.STOP.id)
        return VolumeKeyAction.fromId(raw, VolumeKeyAction.STOP)
    }

    fun setVolumeDownAction(context: Context, action: VolumeKeyAction) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_VOLUME_DOWN_ACTION, action.id).apply()
    }

    /**
     * Intercepts volume key events from ComponentActivity dispatchKeyEvent or AccessibilityService.
     * Returns true when the event is consumed for recording control.
     */
    fun handleKeyEvent(context: Context, event: KeyEvent): Boolean {
        if (!isEnabled(context)) return false

        val keyCode = event.keyCode
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }

        // 1. Long-Press Detection on Volume DOWN:
        // When held down (>850ms or repeatCount >= 3):
        // Hides/removes the floating flashlight button, and locks button controls until Vault is opened.
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) {
                    volumeDownPressStartTime = event.downTime
                    volumeDownLongPressTriggered = false
                } else if (!volumeDownLongPressTriggered &&
                    (event.repeatCount >= 3 || (event.eventTime - event.downTime >= 850L) || event.isLongPress)
                ) {
                    volumeDownLongPressTriggered = true
                    FlashlightFloatingOverlayManager.hideAndLock(context)
                    vibrate(context, 90L, doublePulse = true)
                    Toast.makeText(
                        context,
                        "Flashlight & button controls hidden. Unlock Vault to restore.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return true
                }
                return true // Consume down events to block volume slider
            } else if (event.action == KeyEvent.ACTION_UP) {
                if (volumeDownLongPressTriggered) {
                    volumeDownLongPressTriggered = false
                    return true
                }
                // Short press on Volume DOWN:
                val pressDuration = event.eventTime - volumeDownPressStartTime
                if (pressDuration < 800L) {
                    if (isControlsLocked(context)) {
                        return true // Controls locked until user opens the vault
                    }
                    val action = getVolumeDownAction(context)
                    if (action != VolumeKeyAction.NONE) {
                        val now = System.currentTimeMillis()
                        if (now - lastActionTimeMs >= ACTION_DEBOUNCE_MS) {
                            lastActionTimeMs = now
                            executeAction(context, action)
                        }
                    }
                }
                return true
            }
            return true
        }

        // 2. Volume UP Handling:
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (event.action != KeyEvent.ACTION_DOWN) {
                return true
            }
            if (event.repeatCount > 0) {
                return true
            }

            // Check if controls are locked until Vault open
            if (isControlsLocked(context)) {
                return true // Locked: consume without triggering recording
            }

            val action = getVolumeUpAction(context)
            if (action == VolumeKeyAction.NONE) {
                return false
            }

            val now = System.currentTimeMillis()
            if (now - lastActionTimeMs < ACTION_DEBOUNCE_MS) {
                return true
            }
            lastActionTimeMs = now

            executeAction(context, action)
            return true
        }

        return false
    }

    private fun executeAction(context: Context, action: VolumeKeyAction) {
        val uiState = RecordingSessionController.uiState.value
        val isServiceActive = RecordingForegroundService.isServiceRunning && !RecordingForegroundService.isStandbyMode
        val isRecording = uiState.isRecording || isServiceActive

        when (action) {
            VolumeKeyAction.START_PAUSE_RESUME -> {
                if (!isRecording) {
                    // Check Camera permission
                    val hasCameraPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!hasCameraPermission) {
                        Toast.makeText(
                            context,
                            "Camera permission required for recording",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }

                    vibrate(context, 70L)
                    val onScreen = onScreenStartRecording
                    if (onScreen != null) {
                        onScreen.invoke()
                        Toast.makeText(context, "● Recording started (Volume Up)", Toast.LENGTH_SHORT).show()
                    } else {
                        // Start background stealth recording directly
                        val serviceIntent = Intent(context, RecordingForegroundService::class.java).apply {
                            this.action = RecordingForegroundService.ACTION_START_HEADLESS_RECORDING
                        }
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                            Toast.makeText(context, "● Stealth recording started (Volume Up)", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            android.util.Log.e("VolumeButtonRecording", "Failed to start stealth recording: ${e.message}", e)
                        }
                    }
                } else if (!uiState.isPaused) {
                    // Pause active recording
                    vibrate(context, 50L, doublePulse = true)
                    RecordingSessionController.pause()
                    val serviceIntent = Intent(context, RecordingForegroundService::class.java).apply {
                        this.action = RecordingForegroundService.ACTION_PAUSE
                    }
                    try { context.startService(serviceIntent) } catch (_: Exception) {}
                    Toast.makeText(context, "❚❚ Recording paused (Volume Up)", Toast.LENGTH_SHORT).show()
                } else {
                    // Resume paused recording
                    vibrate(context, 70L)
                    RecordingSessionController.resume()
                    val serviceIntent = Intent(context, RecordingForegroundService::class.java).apply {
                        this.action = RecordingForegroundService.ACTION_RESUME
                    }
                    try { context.startService(serviceIntent) } catch (_: Exception) {}
                    Toast.makeText(context, "▶ Recording resumed (Volume Up)", Toast.LENGTH_SHORT).show()
                }
            }

            VolumeKeyAction.STOP -> {
                val canStop = uiState.isRecording || RecordingForegroundService.isServiceRunning
                if (canStop) {
                    vibrate(context, 120L)
                    val onScreen = onScreenStopRecording
                    if (onScreen != null) {
                        onScreen.invoke()
                    } else {
                        RecordingSessionController.stop()
                    }
                    val serviceIntent = Intent(context, RecordingForegroundService::class.java).apply {
                        this.action = RecordingForegroundService.ACTION_STOP
                        this.putExtra(RecordingForegroundService.EXTRA_STOPPED_BY_VOLUME, true)
                    }
                    try { context.startService(serviceIntent) } catch (_: Exception) {}
                    Toast.makeText(context, "■ Recording saved to Vault (Volume Down)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No active recording to stop", Toast.LENGTH_SHORT).show()
                }
            }

            VolumeKeyAction.NONE -> Unit
        }
    }

    private fun vibrate(context: Context, durationMs: Long, doublePulse: Boolean = false) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (doublePulse) {
                        vibrator.vibrate(
                            VibrationEffect.createWaveform(longArrayOf(0, 40, 50, 40), -1)
                        )
                    } else {
                        vibrator.vibrate(
                            VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                        )
                    }
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {}
    }
}
