package com.example.feature.media.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.feature.media.VaultFolder

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onCreateFolder: (name: String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New Folder",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter a name for the folder:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = folderName,
                    onValueChange = {
                        folderName = it
                        if (isError && it.isNotBlank()) isError = false
                    },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("Folder name cannot be empty") }
                    } else null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_folder_name")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (folderName.isBlank()) {
                        isError = true
                    } else {
                        onCreateFolder(folderName)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("btn_confirm_create_folder")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_cancel_create_folder")
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun MoveToFolderDialog(
    folders: List<VaultFolder>,
    onDismiss: () -> Unit,
    onFolderSelected: (folderId: String?) -> Unit,
    onCreateNewFolderRequested: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("dialog_move_to_folder")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Move Media",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Select a destination folder:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option 1: Root / No folder
                    item {
                        FolderSelectRow(
                            name = "Vault Root (No Folder)",
                            icon = Icons.Default.Home,
                            onClick = { onFolderSelected(null) },
                            testTag = "folder_select_root"
                        )
                    }

                    // Existing Folders
                    items(folders) { folder ->
                        FolderSelectRow(
                            name = folder.name,
                            icon = Icons.Default.Folder,
                            onClick = { onFolderSelected(folder.id) },
                            testTag = "folder_select_${folder.id}"
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onCreateNewFolderRequested,
                        modifier = Modifier.testTag("btn_move_create_folder")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Folder",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("New Folder")
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_cancel_move")
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderSelectRow(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
