package com.example.feature.media.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.feature.media.VaultFolder
import com.example.feature.media.VaultMediaItem
import com.example.feature.media.VaultTab

@Composable
fun MediaGalleryContent(
    selectedTab: VaultTab,
    activeFolderId: String?,
    activeFolderName: String?,
    mediaItems: List<VaultMediaItem>,
    folders: List<VaultFolder>,
    trashItems: List<VaultMediaItem>,
    trashCount: Int,
    selectedMediaIds: Set<String>,
    isSelectionMode: Boolean,
    onTabSelected: (VaultTab) -> Unit,
    onOpenFolder: (VaultFolder) -> Unit,
    onExitFolder: () -> Unit,
    onCreateFolderRequested: () -> Unit,
    onDeleteFolder: (String) -> Unit,
    onMediaClick: (VaultMediaItem) -> Unit,
    onMediaLongClick: (VaultMediaItem) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onExportSelected: () -> Unit,
    onShareSelected: () -> Unit = {},
    onMoveSelectedToFolder: () -> Unit,
    onDeleteSelected: () -> Unit,
    onRestoreSelected: () -> Unit,
    onPermanentlyDeleteSelected: () -> Unit,
    onEmptyTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Selection Toolbar (Shown when selection mode is active)
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SelectionActionBar(
                selectedCount = selectedMediaIds.size,
                totalCount = if (selectedTab == VaultTab.TRASH) trashItems.size else mediaItems.size,
                isTrash = selectedTab == VaultTab.TRASH,
                onClearSelection = onClearSelection,
                onSelectAll = onSelectAll,
                onExport = onExportSelected,
                onShare = onShareSelected,
                onMoveToFolder = onMoveSelectedToFolder,
                onDelete = onDeleteSelected,
                onRestore = onRestoreSelected,
                onPermanentlyDelete = onPermanentlyDeleteSelected
            )
        }

        // Active Folder Header OR Navigation Tabs
        if (activeFolderId != null) {
            ActiveFolderHeader(
                folderName = activeFolderName ?: "Folder",
                itemCount = mediaItems.size,
                onBack = onExitFolder,
                onDeleteFolder = { onDeleteFolder(activeFolderId) }
            )
        } else if (!isSelectionMode) {
            VaultTabsRow(
                selectedTab = selectedTab,
                trashCount = trashCount,
                onTabSelected = onTabSelected
            )
        }

        // Main Grid Content / View
        if (selectedTab == VaultTab.FOLDERS && activeFolderId == null) {
            // Folders List View
            FoldersGridView(
                folders = folders,
                onOpenFolder = onOpenFolder,
                onCreateFolderRequested = onCreateFolderRequested,
                onDeleteFolder = onDeleteFolder
            )
        } else if (selectedTab == VaultTab.TRASH) {
            // Trash Media View
            TrashGalleryView(
                trashItems = trashItems,
                selectedMediaIds = selectedMediaIds,
                isSelectionMode = isSelectionMode,
                onMediaClick = onMediaClick,
                onMediaLongClick = onMediaLongClick,
                onEmptyTrash = onEmptyTrash
            )
        } else {
            // Active Media Grid
            ActiveMediaGridView(
                mediaItems = mediaItems,
                selectedMediaIds = selectedMediaIds,
                isSelectionMode = isSelectionMode,
                selectedTab = selectedTab,
                onMediaClick = onMediaClick,
                onMediaLongClick = onMediaLongClick
            )
        }
    }
}

@Composable
private fun VaultTabsRow(
    selectedTab: VaultTab,
    trashCount: Int,
    onTabSelected: (VaultTab) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = selectedTab.ordinal,
        edgePadding = 16.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        divider = {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vault_tabs_row")
    ) {
        Tab(
            selected = selectedTab == VaultTab.ALL,
            onClick = { onTabSelected(VaultTab.ALL) },
            text = { Text("All", fontWeight = FontWeight.Medium) },
            modifier = Modifier.testTag("tab_all")
        )
        Tab(
            selected = selectedTab == VaultTab.PHOTOS,
            onClick = { onTabSelected(VaultTab.PHOTOS) },
            text = { Text("Photos", fontWeight = FontWeight.Medium) },
            modifier = Modifier.testTag("tab_photos")
        )
        Tab(
            selected = selectedTab == VaultTab.VIDEOS,
            onClick = { onTabSelected(VaultTab.VIDEOS) },
            text = { Text("Videos", fontWeight = FontWeight.Medium) },
            modifier = Modifier.testTag("tab_videos")
        )
        Tab(
            selected = selectedTab == VaultTab.FOLDERS,
            onClick = { onTabSelected(VaultTab.FOLDERS) },
            text = { Text("Folders", fontWeight = FontWeight.Medium) },
            modifier = Modifier.testTag("tab_folders")
        )
        Tab(
            selected = selectedTab == VaultTab.TRASH,
            onClick = { onTabSelected(VaultTab.TRASH) },
            text = {
                Text(
                    text = if (trashCount > 0) "Trash ($trashCount)" else "Trash",
                    fontWeight = FontWeight.Medium
                )
            },
            modifier = Modifier.testTag("tab_trash")
        )
    }
}

@Composable
private fun ActiveFolderHeader(
    folderName: String,
    itemCount: Int,
    onBack: () -> Unit,
    onDeleteFolder: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("header_active_folder")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("btn_folder_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back from folder",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column {
                    Text(
                        text = folderName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$itemCount item(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onDeleteFolder,
                modifier = Modifier.testTag("btn_delete_folder_current")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Folder",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    totalCount: Int,
    isTrash: Boolean,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit = {},
    onMoveToFolder: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onPermanentlyDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("selection_action_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onClearSelection,
                    modifier = Modifier.testTag("btn_selection_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear Selection",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onSelectAll,
                    modifier = Modifier.testTag("btn_select_all")
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Select All",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                if (isTrash) {
                    IconButton(
                        onClick = onRestore,
                        enabled = selectedCount > 0,
                        modifier = Modifier.testTag("btn_selection_restore")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = "Restore Selected",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    IconButton(
                        onClick = onPermanentlyDelete,
                        enabled = selectedCount > 0,
                        modifier = Modifier.testTag("btn_selection_permanent_delete")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Delete Permanently",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    IconButton(
                        onClick = onExport,
                        enabled = selectedCount > 0,
                        modifier = Modifier.testTag("btn_selection_export")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Save to Gallery",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    IconButton(
                        onClick = onShare,
                        enabled = selectedCount > 0,
                        modifier = Modifier.testTag("btn_selection_share")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Selected",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    IconButton(
                        onClick = onMoveToFolder,
                        enabled = selectedCount > 0,
                        modifier = Modifier.testTag("btn_selection_move")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                            contentDescription = "Move to Folder",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        enabled = selectedCount > 0,
                        modifier = Modifier.testTag("btn_selection_delete")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Selected",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveMediaGridView(
    mediaItems: List<VaultMediaItem>,
    selectedMediaIds: Set<String>,
    isSelectionMode: Boolean,
    selectedTab: VaultTab,
    onMediaClick: (VaultMediaItem) -> Unit,
    onMediaLongClick: (VaultMediaItem) -> Unit
) {
    if (mediaItems.isEmpty()) {
        EmptyMediaState(
            title = when (selectedTab) {
                VaultTab.PHOTOS -> "No photos in Vault"
                VaultTab.VIDEOS -> "No videos in Vault"
                else -> "Vault is empty"
            },
            subtitle = "Use the + button below to safely import photos and videos."
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 105.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("grid_vault_media")
        ) {
            items(mediaItems, key = { it.id }) { item ->
                MediaThumbnailItem(
                    item = item,
                    isSelected = selectedMediaIds.contains(item.id),
                    isSelectionMode = isSelectionMode,
                    onClick = { onMediaClick(item) },
                    onLongClick = { onMediaLongClick(item) }
                )
            }
        }
    }
}

@Composable
private fun FoldersGridView(
    folders: List<VaultFolder>,
    onOpenFolder: (VaultFolder) -> Unit,
    onCreateFolderRequested: () -> Unit,
    onDeleteFolder: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("view_folders_grid")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Folders (${folders.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Button(
                onClick = onCreateFolderRequested,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("btn_create_folder_action")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Folder",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text("New Folder")
            }
        }

        if (folders.isEmpty()) {
            EmptyMediaState(
                title = "No folders created",
                subtitle = "Create folders to organize your private photos and videos."
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(folders, key = { it.id }) { folder ->
                    FolderCard(
                        folder = folder,
                        onClick = { onOpenFolder(folder) },
                        onDelete = { onDeleteFolder(folder.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderCard(
    folder: VaultFolder,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("card_folder_${folder.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Folder",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp).testTag("btn_delete_folder_${folder.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TrashGalleryView(
    trashItems: List<VaultMediaItem>,
    selectedMediaIds: Set<String>,
    isSelectionMode: Boolean,
    onMediaClick: (VaultMediaItem) -> Unit,
    onMediaLongClick: (VaultMediaItem) -> Unit,
    onEmptyTrash: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("view_trash_gallery")
    ) {
        // Trash Notice Banner
        Surface(
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Recently Deleted",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Deleted files stay here until permanently erased.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }

                if (trashItems.isNotEmpty()) {
                    TextButton(
                        onClick = onEmptyTrash,
                        modifier = Modifier.testTag("btn_empty_trash")
                    ) {
                        Text(
                            text = "Empty Trash",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (trashItems.isEmpty()) {
            EmptyMediaState(
                title = "Trash is empty",
                subtitle = "Items deleted from the vault will appear here."
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 105.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(trashItems, key = { it.id }) { item ->
                    MediaThumbnailItem(
                        item = item,
                        isSelected = selectedMediaIds.contains(item.id),
                        isSelectionMode = isSelectionMode,
                        onClick = { onMediaClick(item) },
                        onLongClick = { onMediaLongClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyMediaState(
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
