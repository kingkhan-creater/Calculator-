package com.example.feature.recording

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.camera.core.CameraControl
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Centralized singleton controller coordinating CameraX video recording,
 * hardware torch state, and foreground service notification updates.
 */
object RecordingSessionController {

    private const val TAG = "RecordingSessionCtrl"

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    private var activeRecording: Recording? = null
    private var cameraControl: CameraControl? = null
    private var onStopCallback: (() -> Unit)? = null

    fun setCameraControl(control: CameraControl?) {
        cameraControl = control
    }

    fun setActiveRecording(recording: Recording?, onStop: (() -> Unit)? = null) {
        activeRecording = recording
        onStopCallback = onStop
        if (recording != null) {
            _uiState.update {
                it.copy(
                    isRecording = true,
                    isPaused = false,
                    elapsedTimeSeconds = 0L,
                    errorMessage = null
                )
            }
        }
    }

    fun handleRecordEvent(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Start -> {
                Log.d(TAG, "Recording started")
                _uiState.update { it.copy(isRecording = true, isPaused = false) }
            }
            is VideoRecordEvent.Pause -> {
                Log.d(TAG, "Recording paused")
                _uiState.update { it.copy(isPaused = true) }
            }
            is VideoRecordEvent.Resume -> {
                Log.d(TAG, "Recording resumed")
                _uiState.update { it.copy(isPaused = false) }
            }
            is VideoRecordEvent.Status -> {
                val seconds = event.recordingStats.recordedDurationNanos / 1_000_000_000L
                _uiState.update { it.copy(elapsedTimeSeconds = seconds) }
            }
            is VideoRecordEvent.Finalize -> {
                Log.d(TAG, "Recording finalized, hasError=${event.hasError()}")
                _uiState.update { it.copy(isRecording = false, isPaused = false, isTorchOn = false) }
                activeRecording = null
            }
        }
    }

    fun pause() {
        try {
            activeRecording?.pause()
            _uiState.update { it.copy(isPaused = true) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause: ${e.message}")
        }
    }

    fun resume() {
        try {
            activeRecording?.resume()
            _uiState.update { it.copy(isPaused = false) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume: ${e.message}")
        }
    }

    fun toggleTorch(): Boolean {
        val newState = !_uiState.value.isTorchOn
        try {
            cameraControl?.enableTorch(newState)
            _uiState.update { it.copy(isTorchOn = newState) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle torch: ${e.message}")
        }
        return newState
    }

    fun stop() {
        try {
            activeRecording?.stop()
            activeRecording = null
            onStopCallback?.invoke()
            onStopCallback = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording: ${e.message}")
        }
    }

    fun resetState() {
        activeRecording = null
        cameraControl = null
        onStopCallback = null
        _uiState.update {
            RecordingUiState(
                isRecording = false,
                isPaused = false,
                isTorchOn = false,
                elapsedTimeSeconds = 0L
            )
        }
    }

    fun setErrorMessage(msg: String?) {
        _uiState.update { it.copy(errorMessage = msg) }
    }

    fun setSuccessMessage(msg: String?) {
        _uiState.update { it.copy(successMessage = msg) }
    }
}
