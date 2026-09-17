package com.example.feature.recording

data class RecordingUiState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val isTorchOn: Boolean = false,
    val elapsedTimeSeconds: Long = 0L,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
