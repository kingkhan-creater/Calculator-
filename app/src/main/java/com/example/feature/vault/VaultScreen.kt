package com.example.feature.vault

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.feature.media.VaultMediaUiEvent
import com.example.feature.media.VaultMediaViewModel
import com.example.feature.media.ui.CreateFolderDialog
import com.example.feature.media.ui.MediaGalleryContent
import com.example.feature.media.ui.MediaPreviewDialog
import com.example.feature.media.ui.MoveToFolderDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: VaultMediaViewModel,
    onLockVault: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToRecording: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    var isFabMenuExpanded by remember { mutableStateOf(false) }

    // Media Picker Launchers
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onImportMediaSelected(uris)
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onImportMediaSelected(uris)
        }
    }

    val anyMediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onImportMediaSelected(uris)
        }
    }

    val deleteOriginalsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onOriginalsDeletionCompleted(result.resultCode == Activity.RESULT_OK)
    }

    // Enforce FLAG_SECURE while Vault is active to prevent screenshot leakage and Recent-App preview leaks
    DisposableEffect(context) {
        var ctx = context
        while (ctx is ContextWrapper && ctx !is Activity) {
            ctx = ctx.baseContext
        }
        val activity = ctx as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            // Keep window secure so no animation/transition screenshot is taken by the OS
        }
    }

    // Handle ViewModel events (Export share intent, Gallery delete prompt, Snackbars)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is VaultMediaUiEvent.ShareExportUris -> {
                    val shareIntent = Intent().apply {
                        if (event.uris.size == 1) {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_STREAM, event.uris.first())
                            type = event.mimeType
                        } else {
                            action = Intent.ACTION_SEND_MULTIPLE
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(event.uris))
                            type = "*/*"
                        }
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(shareIntent, "Export Vault Media")
                    context.startActivity(chooser)
                }
                is VaultMediaUiEvent.RequestDeleteGalleryUris -> {
                    val intentSender = viewModel.createGalleryDeleteIntentSender(event.uris)
                    if (intentSender != null) {
                        try {
                            deleteOriginalsLauncher.launch(
                                IntentSenderRequest.Builder(intentSender).build()
                            )
                        } catch (_: Exception) {
                            val deleted = viewModel.deleteGalleryOriginalsDirectly(event.uris)
                            viewModel.onOriginalsDeletionCompleted(deleted > 0)
                        }
                    } else {
                        val deleted = viewModel.deleteGalleryOriginalsDirectly(event.uris)
                        viewModel.onOriginalsDeletionCompleted(deleted > 0)
                    }
                }
                is VaultMediaUiEvent.ShowToast -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    // Show success / error messages
    LaunchedEffect(uiState.successMessage, uiState.errorMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onClearMessages()
        }
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onClearMessages()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("vault_screen"),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Vault Gallery",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("btn_action_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = viewModel::onOpenChangePinDialog,
                        modifier = Modifier.testTag("btn_action_change_pin")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Change PIN"
                        )
                    }
                    IconButton(
                        onClick = onLockVault,
                        modifier = Modifier.testTag("btn_action_lock_vault")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Vault",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (!uiState.isSelectionMode) {
                Box {
                    FloatingActionButton(
                        onClick = { isFabMenuExpanded = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        modifier = Modifier.testTag("fab_import_media")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Media"
                        )
                    }

                    DropdownMenu(
                        expanded = isFabMenuExpanded,
                        onDismissRequest = { isFabMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Import Photos") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                isFabMenuExpanded = false
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("menu_import_photos")
                        )

                        DropdownMenuItem(
                            text = { Text("Record Video") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color.Red
                                )
                            },
                            onClick = {
                                isFabMenuExpanded = false
                                onNavigateToRecording()
                            },
                            modifier = Modifier.testTag("menu_record_video")
                        )

                        DropdownMenuItem(
                            text = { Text("Import Videos") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                isFabMenuExpanded = false
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            modifier = Modifier.testTag("menu_import_videos")
                        )

                        DropdownMenuItem(
                            text = { Text("New Folder") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CreateNewFolder,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                isFabMenuExpanded = false
                                viewModel.onCreateFolderRequested()
                            },
                            modifier = Modifier.testTag("menu_create_folder")
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            MediaGalleryContent(
                selectedTab = uiState.selectedTab,
                activeFolderId = uiState.activeFolderId,
                activeFolderName = uiState.activeFolderName,
                mediaItems = uiState.mediaItems,
                folders = uiState.folders,
                trashItems = uiState.trashItems,
                trashCount = uiState.trashCount,
                selectedMediaIds = uiState.selectedMediaIds,
                isSelectionMode = uiState.isSelectionMode,
                onTabSelected = viewModel::onTabSelected,
                onOpenFolder = viewModel::onOpenFolder,
                onExitFolder = viewModel::onExitFolder,
                onCreateFolderRequested = viewModel::onCreateFolderRequested,
                onDeleteFolder = viewModel::onDeleteFolder,
                onMediaClick = viewModel::onMediaItemClick,
                onMediaLongClick = viewModel::onMediaItemLongClick,
                onSelectAll = viewModel::onSelectAll,
                onClearSelection = viewModel::onClearSelection,
                onExportSelected = viewModel::onExportToGallerySelected,
                onShareSelected = viewModel::onShareSelected,
                onMoveSelectedToFolder = viewModel::onOpenMoveToFolderDialog,
                onDeleteSelected = viewModel::onDeleteSelectedMedia,
                onRestoreSelected = viewModel::onRestoreSelectedMedia,
                onPermanentlyDeleteSelected = viewModel::onPermanentlyDeleteSelected,
                onEmptyTrash = viewModel::onEmptyTrash
            )

            // Loading overlay indicator
            if (uiState.isLoading || uiState.isExporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 6.dp,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("loading_indicator")
                            )
                            Text(
                                text = if (uiState.isExporting) "Moving to Gallery..." else "Loading...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete Original Files from Gallery Confirmation Dialog
    if (uiState.isDeleteOriginalsPromptVisible) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissDeleteOriginalsPrompt,
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Free up device storage?",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Your ${uiState.deleteOriginalsCount} item(s) are now safely stored in the vault.\n\nDo you want to delete the original files from your Gallery so they don't take duplicate storage space on your device?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::onConfirmDeleteOriginals,
                    modifier = Modifier.testTag("btn_delete_originals_from_gallery")
                ) {
                    Text("Delete from Gallery")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::onDismissDeleteOriginalsPrompt,
                    modifier = Modifier.testTag("btn_keep_originals_in_gallery")
                ) {
                    Text("Keep in Gallery")
                }
            }
        )
    }

    // Media Preview Dialog (Photo Viewer or Video Player)
    uiState.previewMediaItem?.let { previewItem ->
        MediaPreviewDialog(
            item = previewItem,
            onDismiss = viewModel::onDismissPreview,
            onExport = viewModel::onExportToGallerySelected,
            onShare = viewModel::onShareSelected,
            onMoveToFolder = viewModel::onOpenMoveToFolderDialog,
            onDeleteOrTrash = if (previewItem.isDeleted) viewModel::onPermanentlyDeleteSelected else viewModel::onDeleteSelectedMedia,
            onRestore = if (previewItem.isDeleted) viewModel::onRestoreSelectedMedia else null
        )
    }

    // Create Folder Dialog
    if (uiState.isCreateFolderDialogOpen) {
        CreateFolderDialog(
            onDismiss = viewModel::onDismissCreateFolderDialog,
            onCreateFolder = viewModel::onCreateFolder
        )
    }

    // Move to Folder Dialog
    if (uiState.isMoveToFolderDialogOpen) {
        MoveToFolderDialog(
            folders = uiState.folders,
            onDismiss = viewModel::onDismissMoveToFolderDialog,
            onFolderSelected = viewModel::onMoveSelectedToFolder,
            onCreateNewFolderRequested = {
                viewModel.onDismissMoveToFolderDialog()
                viewModel.onCreateFolderRequested()
            }
        )
    }

    // Change PIN Dialog
    if (uiState.isChangePinDialogOpen) {
        ChangePinDialog(
            onChangePin = { currentPin, newPin, _ ->
                viewModel.onChangePin(currentPin, newPin)
            },
            onDismiss = viewModel::onDismissChangePinDialog
        )
    }
}
