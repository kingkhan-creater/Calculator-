package com.example.feature.recording

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.KeyEvent

/**
 * Android Accessibility Service that intercepts hardware volume keys globally,
 * allowing recording to be started, paused, resumed, or stopped discreetly
 * even when the application is in the background or completely closed.
 */
class VolumeButtonAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VolumeAccessibility"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.eventTypes = android.view.accessibility.AccessibilityEvent.TYPES_ALL_MASK
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON
            serviceInfo = info
            VolumeButtonRecordingManager.isAccessibilityServiceRunning = true
            Log.d(TAG, "Accessibility service connected with hardware key filtering")

            // Initialize Flashlight hardware manager
            FlashlightManager.init(applicationContext)

            // Register Accessibility Button Controller Callback (Android 8.0+)
            // Tapping the floating accessibility shortcut toggles the Flashlight ON / OFF
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    accessibilityButtonController.registerAccessibilityButtonCallback(
                        object : android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback() {
                            override fun onClicked(controller: android.accessibilityservice.AccessibilityButtonController) {
                                super.onClicked(controller)
                                Log.d(TAG, "Accessibility button clicked -> Toggling Flashlight")
                                if (!FlashlightFloatingOverlayManager.isControlsLocked(applicationContext)) {
                                    FlashlightManager.toggleTorch(applicationContext)
                                }
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error registering accessibility button callback: ${e.message}")
                }
            }

            // Show custom floating flashlight overlay button
            FlashlightFloatingOverlayManager.show(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring service info: ${e.message}", e)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Intercept volume buttons if enabled in settings
        if (VolumeButtonRecordingManager.handleKeyEvent(applicationContext, event)) {
            return true // Event consumed: prevents phone volume slider from appearing
        }
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        // No UI accessibility inspection needed
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        FlashlightFloatingOverlayManager.hide()
        VolumeButtonRecordingManager.isAccessibilityServiceRunning = false
        Log.d(TAG, "Accessibility service destroyed")
    }
}
