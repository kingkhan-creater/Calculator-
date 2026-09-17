package com.example.feature.recording

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    viewModel: RecordingViewModel,
    activeFolderId: String?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var isFrontCamera by remember { mutableStateOf(false) }
    var isBlackScreenStealthMode by remember { mutableStateOf(false) }

    // Intercept hardware / gesture back button: Keep recording running in background!
    BackHandler {
        if (uiState.isRecording) {
            Toast.makeText(context, "Recording continues in background", Toast.LENGTH_SHORT).show()
        }
        onNavigateBack()
    }

    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions[Manifest.permission.CAMERA] == true &&
                permissions[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            val perms = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var videoCaptureRef by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }

    // Wire hardware volume button controls while in RecordingScreen
    DisposableEffect(videoCaptureRef, activeFolderId) {
        VolumeButtonRecordingManager.onScreenStartRecording = {
            videoCaptureRef?.let { vc ->
                viewModel.startRecording(context, vc, activeFolderId)
            }
        }
        VolumeButtonRecordingManager.onScreenStopRecording = {
            viewModel.stopRecording(context) {
                onNavigateBack()
            }
        }
        onDispose {
            VolumeButtonRecordingManager.onScreenStartRecording = null
            VolumeButtonRecordingManager.onScreenStopRecording = null
        }
    }

    // Setup CameraX Preview + VideoCapture with persistent global lifecycle so it never cuts off on back press
    LaunchedEffect(hasPermissions, previewViewRef, isFrontCamera) {
        if (hasPermissions && previewViewRef != null) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                val cameraSelector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(
                            Quality.HD,
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                        )
                    )
                    .build()
                val videoCapture = VideoCapture.withOutput(recorder)
                videoCaptureRef = videoCapture

                try {
                    cameraProvider.unbindAll()
                    preview.setSurfaceProvider(previewViewRef?.surfaceProvider)
                    GlobalRecordingLifecycleOwner.ensureActive()
                    val camera = cameraProvider.bindToLifecycle(
                        GlobalRecordingLifecycleOwner,
                        cameraSelector,
                        preview,
                        videoCapture
                    )
                    viewModel.setCameraControl(camera.cameraControl)
                } catch (e: Exception) {
                    viewModel.clearMessages()
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("recording_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (uiState.isRecording) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Recording",
                                tint = Color.Red,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        val minutes = uiState.elapsedTimeSeconds / 60
                        val seconds = uiState.elapsedTimeSeconds % 60
                        Text(
                            text = if (uiState.isRecording) String.format("%02d:%02d", minutes, seconds) else "Stealth Video Recorder",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.isRecording) {
                                Toast.makeText(context, "Recording continues in background", Toast.LENGTH_SHORT).show()
                            }
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("btn_recording_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Black screen stealth mode toggle
                    IconButton(
                        onClick = { isBlackScreenStealthMode = !isBlackScreenStealthMode },
                        modifier = Modifier.testTag("btn_black_screen_stealth")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = "Stealth Black Screen",
                            tint = if (isBlackScreenStealthMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Flip camera
                    if (!uiState.isRecording) {
                        IconButton(
                            onClick = { isFrontCamera = !isFrontCamera },
                            modifier = Modifier.testTag("btn_flip_camera")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Switch Camera"
                            )
                        }
                    }
                    // Torch
                    IconButton(
                        onClick = { viewModel.toggleTorch() },
                        modifier = Modifier.testTag("btn_toggle_torch")
                    ) {
                        Icon(
                            imageVector = if (uiState.isTorchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                            contentDescription = "Toggle Torch",
                            tint = if (uiState.isTorchOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            if (!hasPermissions) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Camera and Microphone permissions are required to record stealth vault videos.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val perms = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                perms.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionLauncher.launch(perms.toTypedArray())
                        },
                        modifier = Modifier.testTag("btn_grant_permissions")
                    ) {
                        Text("Grant Permissions")
                    }
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            previewViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Bottom Control Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.isRecording) {
                            // Pause / Resume Button
                            IconButton(
                                onClick = {
                                    if (uiState.isPaused) viewModel.resumeRecording() else viewModel.pauseRecording()
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    .testTag("btn_pause_resume")
                            ) {
                                Icon(
                                    imageVector = if (uiState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = if (uiState.isPaused) "Resume" else "Pause",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Stop Button
                            Button(
                                onClick = {
                                    viewModel.stopRecording(context) {
                                        onNavigateBack()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier
                                    .height(56.dp)
                                    .testTag("btn_stop_recording")
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Stop & Save to Vault", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            // Start Recording Button
                            Button(
                                onClick = {
                                    videoCaptureRef?.let { vc ->
                                        viewModel.startRecording(context, vc, activeFolderId)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier
                                    .height(56.dp)
                                    .fillMaxWidth(0.7f)
                                    .testTag("btn_start_recording")
                            ) {
                                Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Start Recording", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Black Screen Stealth Overlay: Looks 100% like screen is turned off
                if (isBlackScreenStealthMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        isBlackScreenStealthMode = false
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "● Stealth Screen Active\nDouble-tap to wake",
                            color = Color.DarkGray.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

