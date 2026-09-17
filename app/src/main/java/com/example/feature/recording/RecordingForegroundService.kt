package com.example.feature.recording

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.example.core.security.VaultFileEncryptor
import com.example.data.local.VaultDatabase
import com.example.data.local.entity.VaultMediaEntity
import com.example.data.storage.VaultStorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class RecordingForegroundService : Service(), LifecycleOwner {

    companion object {
        private const val TAG = "RecordingService"
        const val CHANNEL_ID = "vault_recording_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START_RECORDING"
        const val ACTION_START_HEADLESS_RECORDING = "ACTION_START_HEADLESS_RECORDING"
        const val ACTION_STOP = "ACTION_STOP_RECORDING"
        const val ACTION_PAUSE = "ACTION_PAUSE_RECORDING"
        const val ACTION_RESUME = "ACTION_RESUME_RECORDING"
        const val ACTION_TOGGLE_TORCH = "ACTION_TOGGLE_TORCH"
        const val ACTION_START_STANDBY = "ACTION_START_STANDBY"
        const val ACTION_STOP_STANDBY = "ACTION_STOP_STANDBY"
        const val EXTRA_STOPPED_BY_VOLUME = "EXTRA_STOPPED_BY_VOLUME"

        var isServiceRunning = false
            private set

        var isStandbyMode = false
            private set
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val backgroundScope = CoroutineScope(Dispatchers.IO + Job())
    private var wakeLock: PowerManager.WakeLock? = null
    private var pendingDismissNotificationAfterFinalize = false

    // Headless CameraX state
    private var cameraProvider: ProcessCameraProvider? = null
    private var headlessRecording: Recording? = null
    private var headlessCameraControl: CameraControl? = null
    private var headlessTempFile: File? = null

    // Standalone Torch state
    private var isStandaloneTorchOn = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        createNotificationChannel()
        initWakeLock()
        observeSessionState()
    }

    private fun initWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CalculatorVault:RecordingWakeLock"
            )?.apply {
                setReferenceCounted(false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize WakeLock: ${e.message}")
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(6 * 60 * 60 * 1000L) // 6 hours safety lock
                Log.d(TAG, "WakeLock acquired for background recording")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error acquiring wakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing wakeLock: ${e.message}")
        }
    }

    private fun observeSessionState() {
        serviceScope.launch {
            RecordingSessionController.uiState.collectLatest { state ->
                if (isServiceRunning && !isStandbyMode) {
                    if (state.isRecording) {
                        acquireWakeLock()
                        val minutes = state.elapsedTimeSeconds / 60
                        val seconds = state.elapsedTimeSeconds % 60
                        val timeStr = String.format("%02d:%02d", minutes, seconds)
                        val statusText = if (state.isPaused) "Recording Paused ($timeStr)" else "Recording Active ($timeStr)"
                        val torchState = state.isTorchOn || isStandaloneTorchOn
                        updateNotification(statusText, isPaused = state.isPaused, isTorchOn = torchState)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> {
                isServiceRunning = true
                isStandbyMode = false
                acquireWakeLock()
                val state = RecordingSessionController.uiState.value
                val minutes = state.elapsedTimeSeconds / 60
                val seconds = state.elapsedTimeSeconds % 60
                val timeStr = String.format("%02d:%02d", minutes, seconds)
                val statusText = if (state.isPaused) "Recording Paused ($timeStr)" else "Recording Active ($timeStr)"
                val notification = buildRecordingNotification(statusText, isPaused = state.isPaused, isTorchOn = state.isTorchOn || isStandaloneTorchOn)
                startForegroundWithProperType(notification)
            }
            ACTION_START_HEADLESS_RECORDING -> {
                isServiceRunning = true
                isStandbyMode = false
                acquireWakeLock()
                val initialNotification = buildRecordingNotification("Starting stealth recording...", isPaused = false, isTorchOn = isStandaloneTorchOn)
                startForegroundWithProperType(initialNotification)
                startHeadlessStealthRecording()
            }
            ACTION_START_STANDBY -> {
                isServiceRunning = true
                isStandbyMode = true
                releaseWakeLock()
                val notification = buildStandbyNotification()
                startForegroundWithProperType(notification)
            }
            ACTION_STOP_STANDBY -> {
                turnOffStandaloneTorchIfOn()
                isServiceRunning = false
                isStandbyMode = false
                releaseWakeLock()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_STOP -> {
                releaseWakeLock()
                val stoppedByVolume = intent?.getBooleanExtra(EXTRA_STOPPED_BY_VOLUME, false) ?: false
                val dismissOnVolumeStop = VolumeButtonRecordingManager.isDismissNotificationOnVolumeStop(this)
                val shouldDismissNotification = if (stoppedByVolume) {
                    dismissOnVolumeStop
                } else {
                    val prefs = getSharedPreferences("vault_security_storage", Context.MODE_PRIVATE)
                    !prefs.getBoolean("persistent_recording_notification", false)
                }

                if (headlessRecording != null) {
                    pendingDismissNotificationAfterFinalize = shouldDismissNotification
                    stopHeadlessRecording()
                } else {
                    RecordingSessionController.stop()
                    if (shouldDismissNotification) {
                        isServiceRunning = false
                        isStandbyMode = false
                        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        val manager = getSystemService(NotificationManager::class.java)
                        manager?.cancel(NOTIFICATION_ID)
                        stopSelf()
                    } else {
                        isStandbyMode = true
                        val notification = buildStandbyNotification("Video securely saved to Vault.")
                        val manager = getSystemService(NotificationManager::class.java)
                        manager?.notify(NOTIFICATION_ID, notification)
                    }
                }
            }
            ACTION_PAUSE -> {
                if (headlessRecording != null) {
                    try { headlessRecording?.pause() } catch (_: Exception) {}
                    RecordingSessionController.pause()
                } else {
                    RecordingSessionController.pause()
                }
                val state = RecordingSessionController.uiState.value
                val minutes = state.elapsedTimeSeconds / 60
                val seconds = state.elapsedTimeSeconds % 60
                val timeStr = String.format("%02d:%02d", minutes, seconds)
                updateNotification("Recording Paused ($timeStr)", isPaused = true, isTorchOn = state.isTorchOn || isStandaloneTorchOn)
            }
            ACTION_RESUME -> {
                if (headlessRecording != null) {
                    try { headlessRecording?.resume() } catch (_: Exception) {}
                    RecordingSessionController.resume()
                } else {
                    RecordingSessionController.resume()
                }
                val state = RecordingSessionController.uiState.value
                val minutes = state.elapsedTimeSeconds / 60
                val seconds = state.elapsedTimeSeconds % 60
                val timeStr = String.format("%02d:%02d", minutes, seconds)
                updateNotification("Recording Active ($timeStr)", isPaused = false, isTorchOn = state.isTorchOn || isStandaloneTorchOn)
            }
            ACTION_TOGGLE_TORCH -> {
                toggleTorchUniversal()
            }
        }
        return START_STICKY
    }

    /**
     * Starts background stealth recording completely headless (without opening any Activity or UI)
     */
    @SuppressLint("MissingPermission")
    private fun startHeadlessStealthRecording() {
        val hasCameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission) {
            updateNotification("Error: Camera permission required", isPaused = false, isTorchOn = false)
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                provider.unbindAll()

                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(
                            Quality.HD,
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                        )
                    )
                    .build()
                val videoCapture = VideoCapture.withOutput(recorder)
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                val camera = provider.bindToLifecycle(this, cameraSelector, videoCapture)
                headlessCameraControl = camera.cameraControl
                RecordingSessionController.setCameraControl(camera.cameraControl)

                val tempDir = File(cacheDir, "vault_temp_recordings").apply {
                    if (!exists()) mkdirs()
                    val noMedia = File(this, ".nomedia")
                    if (!noMedia.exists()) {
                        try { noMedia.createNewFile() } catch (_: Exception) {}
                    }
                }
                val tempFile = File(tempDir, "headless_${System.currentTimeMillis()}.mp4")
                headlessTempFile = tempFile

                val outputOptions = FileOutputOptions.Builder(tempFile).build()
                val pendingRecording = videoCapture.output.prepareRecording(this, outputOptions)

                val hasAudioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (hasAudioPermission) {
                    pendingRecording.withAudioEnabled()
                }

                val recording = pendingRecording.start(ContextCompat.getMainExecutor(this)) { event ->
                    RecordingSessionController.handleRecordEvent(event)
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            updateNotification("Recording Active (00:00)", isPaused = false, isTorchOn = isStandaloneTorchOn)
                        }
                        is VideoRecordEvent.Finalize -> {
                            handleHeadlessFinalize(event, tempFile)
                        }
                        else -> Unit
                    }
                }

                headlessRecording = recording
                RecordingSessionController.setActiveRecording(recording)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start headless recording: ${e.message}", e)
                updateNotification("Failed to start stealth recording", isPaused = false, isTorchOn = false)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopHeadlessRecording() {
        try {
            headlessRecording?.stop()
            headlessRecording = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping headless recording: ${e.message}")
        }
    }

    private fun handleHeadlessFinalize(event: VideoRecordEvent.Finalize, tempFile: File) {
        try {
            cameraProvider?.unbindAll()
            headlessCameraControl = null
            headlessRecording = null
        } catch (_: Exception) {}

        if (!event.hasError() && tempFile.exists() && tempFile.length() > 0) {
            backgroundScope.launch {
                try {
                    val mediaId = UUID.randomUUID().toString()
                    val storageManager = VaultStorageManager(applicationContext, VaultFileEncryptor())
                    val targetVaultFile = storageManager.getMediaFile("$mediaId.mp4")

                    // Extract frame thumbnail
                    var thumbRelativePath: String? = null
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(tempFile.absolutePath)
                        val frameBitmap = retriever.getFrameAtTime(1000000) ?: retriever.frameAtTime
                        if (frameBitmap != null) {
                            val thumbName = "thumb_$mediaId.jpg"
                            val thumbFile = storageManager.getMediaFile(thumbName)
                            FileOutputStream(thumbFile).use { thumbOut ->
                                frameBitmap.compress(Bitmap.CompressFormat.JPEG, 80, thumbOut)
                                thumbOut.flush()
                            }
                            thumbRelativePath = thumbName
                        }
                        retriever.release()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to extract thumbnail: ${e.message}")
                    }

                    // Copy recorded video into private vault
                    tempFile.copyTo(targetVaultFile, overwrite = true)
                    val sizeBytes = targetVaultFile.length()
                    val durationMs = event.recordingStats.recordedDurationNanos / 1_000_000L

                    val entity = VaultMediaEntity(
                        id = mediaId,
                        folderId = null,
                        fileName = "Stealth_Rec_${System.currentTimeMillis()}.mp4",
                        mimeType = "video/mp4",
                        mediaType = "VIDEO",
                        sizeBytes = sizeBytes,
                        durationMs = durationMs,
                        relativePath = "$mediaId.mp4",
                        thumbnailPath = thumbRelativePath,
                        createdAt = System.currentTimeMillis(),
                        isDeleted = false,
                        deletedAt = null
                    )

                    val db = VaultDatabase.getInstance(applicationContext)
                    db.mediaDao().insertMedia(entity)

                    if (tempFile.exists()) tempFile.delete()
                    Log.d(TAG, "Headless recording saved successfully: $mediaId")

                    // Auto upload to anonymous cloud quota (up to 10 recordings)
                    com.example.feature.backup.AnonymousCloudBackupManager.uploadRecordingAsync(
                        context = applicationContext,
                        mediaId = mediaId,
                        videoFile = targetVaultFile,
                        fileName = entity.fileName,
                        durationMs = durationMs
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving headless recording: ${e.message}", e)
                    if (tempFile.exists()) tempFile.delete()
                } finally {
                    withContext(Dispatchers.Main) {
                        if (pendingDismissNotificationAfterFinalize) {
                            pendingDismissNotificationAfterFinalize = false
                            isServiceRunning = false
                            isStandbyMode = false
                            ServiceCompat.stopForeground(this@RecordingForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                            val manager = getSystemService(NotificationManager::class.java)
                            manager?.cancel(NOTIFICATION_ID)
                            stopSelf()
                        } else {
                            val prefs = getSharedPreferences("vault_security_storage", Context.MODE_PRIVATE)
                            val isPersistentEnabled = prefs.getBoolean("persistent_recording_notification", false)
                            if (isPersistentEnabled) {
                                isStandbyMode = true
                                val notification = buildStandbyNotification("Video securely saved to Vault.")
                                val manager = getSystemService(NotificationManager::class.java)
                                manager?.notify(NOTIFICATION_ID, notification)
                            } else {
                                isServiceRunning = false
                                isStandbyMode = false
                                ServiceCompat.stopForeground(this@RecordingForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                                stopSelf()
                            }
                        }
                    }
                }
            }
        } else {
            if (tempFile.exists()) tempFile.delete()
            if (pendingDismissNotificationAfterFinalize) {
                pendingDismissNotificationAfterFinalize = false
                isServiceRunning = false
                isStandbyMode = false
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                val manager = getSystemService(NotificationManager::class.java)
                manager?.cancel(NOTIFICATION_ID)
                stopSelf()
            }
        }
    }

    /**
     * Universal Torch toggle: works standalone via CameraManager when not recording,
     * or via CameraControl when CameraX recording is active.
     */
    private fun toggleTorchUniversal() {
        if (headlessRecording != null && headlessCameraControl != null) {
            isStandaloneTorchOn = !isStandaloneTorchOn
            try { headlessCameraControl?.enableTorch(isStandaloneTorchOn) } catch (_: Exception) {}
        } else if (RecordingSessionController.uiState.value.isRecording) {
            isStandaloneTorchOn = RecordingSessionController.toggleTorch()
        } else {
            // Standalone torch mode using CameraManager without opening any camera session or app!
            try {
                val cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                if (cameraManager != null) {
                    val backCameraId = cameraManager.cameraIdList.firstOrNull { id ->
                        val chars = cameraManager.getCameraCharacteristics(id)
                        chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                        chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                    } ?: cameraManager.cameraIdList.firstOrNull()

                    if (backCameraId != null) {
                        isStandaloneTorchOn = !isStandaloneTorchOn
                        cameraManager.setTorchMode(backCameraId, isStandaloneTorchOn)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle standalone torch: ${e.message}")
            }
        }

        val state = RecordingSessionController.uiState.value
        if (isStandbyMode) {
            val notification = buildStandbyNotification()
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, notification)
        } else {
            val minutes = state.elapsedTimeSeconds / 60
            val seconds = state.elapsedTimeSeconds % 60
            val timeStr = String.format("%02d:%02d", minutes, seconds)
            val statusText = if (state.isPaused) "Recording Paused ($timeStr)" else "Recording Active ($timeStr)"
            updateNotification(statusText, isPaused = state.isPaused, isTorchOn = isStandaloneTorchOn)
        }
    }

    private fun turnOffStandaloneTorchIfOn() {
        if (isStandaloneTorchOn) {
            try {
                val cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                if (cameraManager != null) {
                    val backCameraId = cameraManager.cameraIdList.firstOrNull { id ->
                        val chars = cameraManager.getCameraCharacteristics(id)
                        chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    } ?: cameraManager.cameraIdList.firstOrNull()
                    if (backCameraId != null) {
                        cameraManager.setTorchMode(backCameraId, false)
                    }
                }
            } catch (_: Exception) {}
            isStandaloneTorchOn = false
        }
    }

    private fun startForegroundWithProperType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val serviceTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (hasMic) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                }
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                serviceTypes
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Vault Quick Recording Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Quick and background stealth video recording controls"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun buildRecordingNotification(statusText: String, isPaused: Boolean, isTorchOn: Boolean): Notification {
        val stopIntent = Intent(this, RecordingForegroundService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val pauseResumeAction = if (isPaused) {
            val resumeIntent = Intent(this, RecordingForegroundService::class.java).apply { action = ACTION_RESUME }
            val resumePendingIntent = PendingIntent.getService(this, 1, resumeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            NotificationCompat.Action.Builder(android.R.drawable.ic_media_play, "▶", resumePendingIntent).build()
        } else {
            val pauseIntent = Intent(this, RecordingForegroundService::class.java).apply { action = ACTION_PAUSE }
            val pausePendingIntent = PendingIntent.getService(this, 2, pauseIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            NotificationCompat.Action.Builder(android.R.drawable.ic_media_pause, "⏸", pausePendingIntent).build()
        }

        val stopAction = NotificationCompat.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "⏹", stopPendingIntent).build()

        val torchIntent = Intent(this, RecordingForegroundService::class.java).apply {
            action = ACTION_TOGGLE_TORCH
        }
        val torchPendingIntent = PendingIntent.getService(this, 3, torchIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val torchAction = NotificationCompat.Action.Builder(android.R.drawable.ic_menu_camera, "🔦", torchPendingIntent).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("● Stealth Video Recording Active")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(pauseResumeAction)
            .addAction(stopAction)
            .addAction(torchAction)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun buildStandbyNotification(customSubtitle: String? = null): Notification {
        // ACTION_START_HEADLESS_RECORDING starts background recording DIRECTLY without opening app UI or camera preview!
        val startRecordIntent = Intent(this, RecordingForegroundService::class.java).apply {
            action = ACTION_START_HEADLESS_RECORDING
        }
        val startRecordPendingIntent = PendingIntent.getService(this, 10, startRecordIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val startRecordAction = NotificationCompat.Action.Builder(
            android.R.drawable.presence_video_online,
            "●",
            startRecordPendingIntent
        ).build()

        val torchIntent = Intent(this, RecordingForegroundService::class.java).apply {
            action = ACTION_TOGGLE_TORCH
        }
        val torchPendingIntent = PendingIntent.getService(this, 3, torchIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val torchAction = NotificationCompat.Action.Builder(android.R.drawable.ic_menu_camera, "🔦", torchPendingIntent).build()

        val dismissIntent = Intent(this, RecordingForegroundService::class.java).apply {
            action = ACTION_STOP_STANDBY
        }
        val dismissPendingIntent = PendingIntent.getService(this, 4, dismissIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val dismissAction = NotificationCompat.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "✕", dismissPendingIntent).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vault Quick Stealth Recorder")
            .setContentText(customSubtitle ?: "Standby: Tap '●' to record silently in background")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(startRecordAction)
            .addAction(torchAction)
            .addAction(dismissAction)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String, isPaused: Boolean, isTorchOn: Boolean) {
        if (!isServiceRunning) return
        val notification = if (isStandbyMode) {
            buildStandbyNotification(statusText)
        } else {
            buildRecordingNotification(statusText, isPaused, isTorchOn)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        turnOffStandaloneTorchIfOn()
        try { cameraProvider?.unbindAll() } catch (_: Exception) {}
        headlessRecording?.stop()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        releaseWakeLock()
        isServiceRunning = false
        isStandbyMode = false
        serviceScope.cancel()
        backgroundScope.cancel()
        super.onDestroy()
    }
}

