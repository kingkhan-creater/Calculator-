package com.example.feature.media.ui

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.core.security.VaultFileEncryptor
import com.example.feature.media.VaultMediaItem
import com.example.feature.media.VaultMediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun MediaPreviewDialog(
    item: VaultMediaItem,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit = onExport,
    onMoveToFolder: () -> Unit,
    onDeleteOrTrash: () -> Unit,
    onRestore: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isExportingOrDecExternal by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            securePolicy = SecureFlagPolicy.SecureOn
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("dialog_media_preview"),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Media Content (Photo or Video)
                if (item.mediaType == VaultMediaType.PHOTO) {
                    PhotoPreviewContent(item = item)
                } else {
                    VideoPreviewContent(item = item)
                }

                // Top Bar (Back / Close + Metadata + External Player Option)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_preview_close")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close preview",
                            tint = Color.White
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = item.fileName,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${formatFileSize(item.sizeBytes)} • ${formatDate(item.createdAt)}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }

                    if (item.mediaType == VaultMediaType.VIDEO) {
                        if (isExportingOrDecExternal) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    isExportingOrDecExternal = true
                                    coroutineScope.launch {
                                        launchExternalVideoPlayerAsync(context, item)
                                        isExportingOrDecExternal = false
                                    }
                                },
                                modifier = Modifier.testTag("btn_preview_external_player")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Play in external video player",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                // Bottom Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.isDeleted && onRestore != null) {
                        ActionButton(
                            icon = Icons.Default.Restore,
                            label = "Restore",
                            onClick = onRestore,
                            testTag = "btn_preview_restore"
                        )
                        ActionButton(
                            icon = Icons.Default.Delete,
                            label = "Delete Forever",
                            onClick = onDeleteOrTrash,
                            tint = MaterialTheme.colorScheme.error,
                            testTag = "btn_preview_delete_permanent"
                        )
                    } else {
                        if (item.mediaType == VaultMediaType.VIDEO) {
                            ActionButton(
                                icon = Icons.Default.PlayArrow,
                                label = "Ext Player",
                                onClick = {
                                    isExportingOrDecExternal = true
                                    coroutineScope.launch {
                                        launchExternalVideoPlayerAsync(context, item)
                                        isExportingOrDecExternal = false
                                    }
                                },
                                testTag = "btn_preview_ext_player"
                            )
                        }
                        ActionButton(
                            icon = Icons.Default.FileDownload,
                            label = "Save to Gallery",
                            onClick = onExport,
                            testTag = "btn_preview_export"
                        )
                        ActionButton(
                            icon = Icons.Default.Share,
                            label = "Share",
                            onClick = onShare,
                            testTag = "btn_preview_share"
                        )
                        ActionButton(
                            icon = Icons.AutoMirrored.Filled.DriveFileMove,
                            label = "Move",
                            onClick = onMoveToFolder,
                            testTag = "btn_preview_move"
                        )
                        ActionButton(
                            icon = Icons.Default.Delete,
                            label = "Trash",
                            onClick = onDeleteOrTrash,
                            tint = MaterialTheme.colorScheme.error,
                            testTag = "btn_preview_trash"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoPreviewContent(item: VaultMediaItem) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = item.file,
            contentDescription = item.fileName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .testTag("preview_photo_image")
        )
    }
}

@Composable
private fun VideoPreviewContent(item: VaultMediaItem) {
    val context = LocalContext.current
    var decryptedTempFile by remember { mutableStateOf<File?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    // Load video: directly for plain files, or decrypt asynchronously for legacy encrypted files
    LaunchedEffect(item) {
        val encryptor = VaultFileEncryptor()
        if (!encryptor.isEncrypted(item.file)) {
            // Direct playback with 0 delay and 0 memory overhead
            decryptedTempFile = item.file
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        errorMessage = null
        withContext(Dispatchers.IO) {
            try {
                val tempDir = File(context.cacheDir, "temp_vault_playback").apply {
                    if (!exists()) mkdirs()
                    // Add .nomedia to prevent gallery scanning
                    val noMedia = File(this, ".nomedia")
                    if (!noMedia.exists()) noMedia.createNewFile()
                }
                val ext = item.fileName.substringAfterLast(".", "mp4")
                val targetFile = File(tempDir, "play_${UUID.randomUUID()}.$ext")
                
                FileOutputStream(targetFile).use { outStream ->
                    encryptor.decryptFileToStream(item.file, outStream)
                    outStream.flush()
                }

                if (targetFile.exists() && targetFile.length() > 0) {
                    decryptedTempFile = targetFile
                } else {
                    errorMessage = "Video file is empty or corrupted."
                }
            } catch (e: Exception) {
                errorMessage = "Failed to load video: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // Cleanup decrypted temp file on dispose if it was a temporary file
    DisposableEffect(Unit) {
        onDispose {
            try {
                videoViewRef?.stopPlayback()
                decryptedTempFile?.let {
                    if (it != item.file && it.exists()) {
                        it.delete()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Decrypting video securely...",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            errorMessage != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = errorMessage ?: "Error playing video",
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            val coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main)
                            coroutineScope.launch {
                                launchExternalVideoPlayerAsync(context, item)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Try External Player")
                    }
                }
            }
            decryptedTempFile != null -> {
                val fileUri = Uri.fromFile(decryptedTempFile)

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT
                                )
                                setVideoURI(fileUri)
                                val mediaController = MediaController(ctx)
                                mediaController.setAnchorView(this)
                                setMediaController(mediaController)

                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                    isPlaying = true
                                }
                                setOnErrorListener { _, _, _ ->
                                    errorMessage = "Internal player error. Please use external player."
                                    true
                                }
                                videoViewRef = this
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("video_view_player")
                    )
                }
            }
        }
    }
}

suspend fun launchExternalVideoPlayerAsync(context: Context, item: VaultMediaItem) = withContext(Dispatchers.IO) {
    try {
        val encryptor = VaultFileEncryptor()
        val fileToPlay: File = if (encryptor.isEncrypted(item.file)) {
            val tempDir = File(context.cacheDir, "temp_vault_playback").apply {
                if (!exists()) mkdirs()
                val noMedia = File(this, ".nomedia")
                if (!noMedia.exists()) noMedia.createNewFile()
            }
            val ext = item.fileName.substringAfterLast(".", "mp4")
            val tempFile = File(tempDir, "ext_play_${UUID.randomUUID()}.$ext")
            FileOutputStream(tempFile).use { out ->
                encryptor.decryptFileToStream(item.file, out)
                out.flush()
            }
            tempFile
        } else {
            item.file
        }

        withContext(Dispatchers.Main) {
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, fileToPlay)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Play video with")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                "Could not open external player: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    testTag: String = ""
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.testTag(testTag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            text = label,
            color = tint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
    val value = sizeBytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups.coerceAtMost(units.size - 1)])
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

