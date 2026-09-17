package com.example.feature.recording

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * A persistent LifecycleOwner that maintains RESUMED state during video recording
 * so that navigating back, turning off the screen, or switching apps does not
 * interrupt or terminate CameraX background recording.
 */
object GlobalRecordingLifecycleOwner : LifecycleOwner {

    private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this).apply {
        currentState = Lifecycle.State.RESUMED
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    fun ensureActive() {
        if (lifecycleRegistry.currentState != Lifecycle.State.RESUMED) {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }
    }
}

