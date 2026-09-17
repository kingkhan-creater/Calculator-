package com.example.feature.recording

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

/**
 * Manages device hardware flashlight / torch.
 * Used for the Flashlight Camouflage Floating Button.
 */
object FlashlightManager {
    private const val TAG = "FlashlightManager"

    @Volatile
    var isTorchOn: Boolean = false
        private set

    private var registeredCallback = false
    private var cachedCameraId: String? = null

    fun init(context: Context) {
        if (registeredCallback) return
        try {
            val cameraManager = context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return
            cameraManager.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    super.onTorchModeChanged(cameraId, enabled)
                    if (cameraId == cachedCameraId || cachedCameraId == null) {
                        isTorchOn = enabled
                        FlashlightFloatingOverlayManager.updateTorchVisual(enabled)
                    }
                }

                override fun onTorchModeUnavailable(cameraId: String) {
                    super.onTorchModeUnavailable(cameraId)
                    if (cameraId == cachedCameraId || cachedCameraId == null) {
                        isTorchOn = false
                        FlashlightFloatingOverlayManager.updateTorchVisual(false)
                    }
                }
            }, Handler(Looper.getMainLooper()))
            registeredCallback = true
        } catch (e: Exception) {
            Log.e(TAG, "Error registering torch callback: ${e.message}")
        }
    }

    fun toggleTorch(context: Context): Boolean {
        return setTorch(context, !isTorchOn)
    }

    fun setTorch(context: Context, turnOn: Boolean): Boolean {
        init(context)
        val appContext = context.applicationContext
        val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
        try {
            val cameraId = cachedCameraId ?: findBackCameraWithFlash(cameraManager) ?: return false
            cachedCameraId = cameraId
            cameraManager.setTorchMode(cameraId, turnOn)
            isTorchOn = turnOn
            FlashlightFloatingOverlayManager.updateTorchVisual(turnOn)
            vibrate(appContext, if (turnOn) 40L else 25L)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set torch mode ($turnOn): ${e.message}", e)
            return false
        }
    }

    private fun findBackCameraWithFlash(cameraManager: CameraManager): String? {
        try {
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (flashAvailable && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return id
                }
            }
            // Fallback to any camera with flash
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                if (chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                    return id
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying camera characteristics: ${e.message}")
        }
        return cameraManager.cameraIdList.firstOrNull()
    }

    private fun vibrate(context: Context, durationMs: Long) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            durationMs,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {}
    }
}
