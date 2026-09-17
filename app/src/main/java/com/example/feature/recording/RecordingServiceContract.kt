package com.example.feature.recording

import kotlinx.coroutines.flow.Flow

interface RecordingServiceContract {
    val isRecordingFlow: Flow<Boolean>
    suspend fun startRecording(): Result<Unit>
    suspend fun stopRecording(): Result<String>
}
