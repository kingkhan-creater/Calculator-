package com.example.feature.recording

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.camera.core.CameraControl
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.security.VaultFileEncryptor
import com.example.data.local.dao.MediaDao
import com.example.data.local.entity.VaultMediaEntity
import com.example.data.storage.VaultStorageManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class RecordingViewModel(
    private val mediaDao: MediaDao,
    private val storageManager: VaultStorageManager,
    private val fileEncryptor: VaultFileEncryptor
) : ViewModel() {

    val uiState: StateFlow<RecordingUiState> = RecordingSessionController.uiState

    private var currentTempFile: File? = null
    private var activeFolder: String? = null
    private var onFinalizeComplete: (() -> Unit)? = null

    init {
        if (!RecordingSessionController.uiState.value.isRecording) {
            RecordingSessionController.resetState()
        }
    }

    fun setCameraControl(control: CameraControl?) {
        RecordingSessionController.setCameraControl(control)
    }

    @SuppressLint("MissingPermission")
    fun startRecording(
        context: Context,
        videoCapture: VideoCapture<Recorder>,
        folderId: String?
    ) {
        if (uiState.value.isRecording) return

        try {
            activeFolder = folderId
            // Ensure private temp directory with .nomedia so Gallery never scans in-progress recordings
            val tempDir = File(context.cacheDir, "vault_temp_recordings").apply {
                if (!exists()) mkdirs()
                val noMedia = File(this, ".nomedia")
                if (!noMedia.exists()) {
                    try { noMedia.createNewFile() } catch (_: Exception) {}
                }
            }
            val tempFile = File(tempDir, "temp_rec_${System.currentTimeMillis()}.mp4")
            currentTempFile = tempFile

            val outputOptions = FileOutputOptions.Builder(tempFile).build()
            val pendingRecording = videoCapture.output.prepareRecording(context, outputOptions)

            val hasAudioPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasAudioPermission) {
                pendingRecording.withAudioEnabled()
            }

            val recording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
                RecordingSessionController.handleRecordEvent(event)
                when (event) {
                    is VideoRecordEvent.Start -> {
                        startForegroundService(context)
                    }
                    is VideoRecordEvent.Finalize -> {
                        stopForegroundService(context)
                        handleRecordingFinalized(context, event, tempFile)
                    }
                    else -> Unit
                }
            }

            RecordingSessionController.setActiveRecording(recording) {
                // On external stop invoked
                stopForegroundService(context)
            }
        } catch (e: Exception) {
            Log.e("RecordingViewModel", "Failed to start CameraX recording: ${e.message}")
            RecordingSessionController.setErrorMessage("Failed to start recording: ${e.message?.take(80)}")
            cleanupTempFile(currentTempFile)
        }
    }

    fun pauseRecording() {
        RecordingSessionController.pause()
    }

    fun resumeRecording() {
        RecordingSessionController.resume()
    }

    fun toggleTorch(): Boolean {
        return RecordingSessionController.toggleTorch()
    }

    fun stopRecording(context: Context, onComplete: (() -> Unit)? = null) {
        if (!uiState.value.isRecording) {
            onComplete?.invoke()
            return
        }
        onFinalizeComplete = onComplete
        RecordingSessionController.stop()
        stopForegroundService(context)
    }

    private fun startForegroundService(context: Context) {
        val serviceIntent = Intent(context, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    private fun stopForegroundService(context: Context) {
        val serviceIntent = Intent(context, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_STOP
        }
        context.startService(serviceIntent)
    }

    private val backgroundScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    private fun handleRecordingFinalized(
        context: Context,
        event: VideoRecordEvent.Finalize,
        tempFile: File
    ) {
        val callback = onFinalizeComplete
        onFinalizeComplete = null

        if (!event.hasError() && tempFile.exists() && tempFile.length() > 0) {
            backgroundScope.launch {
                try {
                    val mediaId = UUID.randomUUID().toString()
                    val fileName = "rec_${System.currentTimeMillis()}.mp4"
                    val relativePath = "$mediaId.mp4"
                    val targetVaultFile = storageManager.getMediaFile(relativePath)

                    // Extract frame for thumbnail before moving temp video
                    var thumbRelativePath: String? = null
                    try {
                        val retriever = android.media.MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(tempFile.absolutePath)
                            val frameBitmap = retriever.getFrameAtTime(1000000) ?: retriever.frameAtTime
                            if (frameBitmap != null) {
                                val thumbName = "thumb_$mediaId.jpg"
                                val thumbFile = storageManager.getMediaFile(thumbName)
                                java.io.FileOutputStream(thumbFile).use { thumbOut ->
                                    frameBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, thumbOut)
                                    thumbOut.flush()
                                }
                                thumbRelativePath = thumbName
                            }
                        } finally {
                            retriever.release()
                        }
                    } catch (e: Exception) {
                        Log.w("RecordingViewModel", "Failed to extract thumbnail: ${e.message}")
                    }

                    // Move/copy recorded video file directly into secure Vault private storage
                    tempFile.copyTo(targetVaultFile, overwrite = true)

                    val sizeBytes = targetVaultFile.length()
                    val durationMs = event.recordingStats.recordedDurationNanos / 1_000_000L

                    val entity = VaultMediaEntity(
                        id = mediaId,
                        folderId = activeFolder,
                        fileName = fileName,
                        mimeType = "video/mp4",
                        mediaType = "VIDEO",
                        sizeBytes = sizeBytes,
                        durationMs = durationMs,
                        relativePath = relativePath,
                        thumbnailPath = thumbRelativePath,
                        createdAt = System.currentTimeMillis(),
                        isDeleted = false,
                        deletedAt = null
                    )
                    mediaDao.insertMedia(entity)

                    // Auto upload to anonymous cloud quota (up to 10 recordings)
                    com.example.feature.backup.AnonymousCloudBackupManager.uploadRecordingAsync(
                        context = context.applicationContext,
                        mediaId = mediaId,
                        videoFile = targetVaultFile,
                        fileName = fileName,
                        durationMs = durationMs
                    )

                    cleanupTempFile(tempFile)
                    RecordingSessionController.setSuccessMessage("Video securely saved to Vault.")
                    callback?.invoke()
                } catch (e: Exception) {
                    Log.e("RecordingViewModel", "Failed to save video: ${e.message}")
                    cleanupTempFile(tempFile)
                    RecordingSessionController.setErrorMessage("Failed to save video: ${e.message?.take(60)}")
                    callback?.invoke()
                }
            }
        } else {
            val errorMsg = if (event.hasError()) "Recording error: ${event.error}" else "Empty recording file"
            Log.e("RecordingViewModel", errorMsg)
            cleanupTempFile(tempFile)
            RecordingSessionController.setErrorMessage(errorMsg)
            callback?.invoke()
        }
    }

    private fun cleanupTempFile(file: File?) {
        try {
            file?.let {
                if (it.exists()) it.delete()
            }
        } catch (_: Exception) {}
        currentTempFile = null
    }

    fun clearMessages() {
        RecordingSessionController.setErrorMessage(null)
        RecordingSessionController.setSuccessMessage(null)
    }

    override fun onCleared() {
        super.onCleared()
        cleanupTempFile(currentTempFile)
    }

    class Factory(
        private val mediaDao: MediaDao,
        private val storageManager: VaultStorageManager,
        private val fileEncryptor: VaultFileEncryptor
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RecordingViewModel::class.java)) {
                return RecordingViewModel(mediaDao, storageManager, fileEncryptor) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
