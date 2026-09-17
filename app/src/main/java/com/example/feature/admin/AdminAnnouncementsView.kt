package com.example.feature.admin

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.admin.AdminAnnouncement
import com.example.core.admin.AnnouncementType
import com.example.core.admin.TargetAudience
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAnnouncementsView(
    announcements: List<AdminAnnouncement>,
    onCreateAnnouncement: (title: String, message: String, type: AnnouncementType, audience: TargetAudience, targetUserId: String?, actionLabel: String?, actionUrl: String?) -> Unit,
    onDeleteAnnouncement: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().testTag("admin_announcements_view")) {
        if (announcements.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Broadcast Notifications Sent Yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Send announcements or notices to all users or target specific groups. Once a user reads a notification, it is automatically removed and never shown to them again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Compose First Announcement")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Active Announcements (${announcements.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "One-time broadcast delivery (dismissed once, never seen again).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { showCreateDialog = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New")
                        }
                    }
                }

                items(announcements, key = { it.id }) { item ->
                    AnnouncementItemCard(
                        announcement = item,
                        onDelete = { onDeleteAnnouncement(item.id) }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateAnnouncementDialog(
            onDismiss = { showCreateDialog = false },
            onSend = { title, msg, type, aud, targetUid, actLabel, actUrl ->
                showCreateDialog = false
                onCreateAnnouncement(title, msg, type, aud, targetUid, actLabel, actUrl)
            }
        )
    }
}

@Composable
private fun AnnouncementItemCard(
    announcement: AdminAnnouncement,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateStr = remember(announcement.createdAtEpochMs) {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        sdf.format(Date(announcement.createdAtEpochMs))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (typeIcon, typeColor) = when (announcement.type) {
                        AnnouncementType.INFO -> Icons.Default.Info to MaterialTheme.colorScheme.primary
                        AnnouncementType.UPDATE -> Icons.Default.SystemUpdate to MaterialTheme.colorScheme.secondary
                        AnnouncementType.WARNING -> Icons.Default.Warning to MaterialTheme.colorScheme.error
                        AnnouncementType.PROMOTION -> Icons.Default.Stars to MaterialTheme.colorScheme.tertiary
                        AnnouncementType.SECURITY_ALERT -> Icons.Default.Warning to MaterialTheme.colorScheme.error
                    }

                    Icon(
                        imageVector = typeIcon,
                        contentDescription = null,
                        tint = typeColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = announcement.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete from Server",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = announcement.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = when (announcement.targetAudience) {
                            TargetAudience.ALL_USERS -> "Audience: All Users"
                            TargetAudience.FREE_ONLY -> "Audience: Free Users Only"
                            TargetAudience.PREMIUM_ONLY -> "Audience: Premium Users Only"
                            TargetAudience.SPECIFIC_USER -> "Audience: Target UID (${announcement.targetUserId?.take(6)}...)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateAnnouncementDialog(
    onDismiss: () -> Unit,
    onSend: (title: String, message: String, type: AnnouncementType, audience: TargetAudience, targetUserId: String?, actionLabel: String?, actionUrl: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AnnouncementType.INFO) }
    var selectedAudience by remember { mutableStateOf(TargetAudience.ALL_USERS) }
    var targetUserId by remember { mutableStateOf("") }
    var actionLabel by remember { mutableStateOf("") }
    var actionUrl by remember { mutableStateOf("") }

    var typeExpanded by remember { mutableStateOf(false) }
    var audienceExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Broadcast In-App Notification")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (e.g. Important Security Update)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_announcement_title"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message Body") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_announcement_message"),
                    minLines = 3,
                    maxLines = 5
                )

                // Type selector
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Notification Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        AnnouncementType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    selectedType = type
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                // Audience selector
                ExposedDropdownMenuBox(
                    expanded = audienceExpanded,
                    onExpandedChange = { audienceExpanded = !audienceExpanded }
                ) {
                    OutlinedTextField(
                        value = when (selectedAudience) {
                            TargetAudience.ALL_USERS -> "All Users (Broadcast)"
                            TargetAudience.FREE_ONLY -> "Free Users Only"
                            TargetAudience.PREMIUM_ONLY -> "Premium Users Only"
                            TargetAudience.SPECIFIC_USER -> "Specific User UID"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target Audience") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = audienceExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = audienceExpanded,
                        onDismissRequest = { audienceExpanded = false }
                    ) {
                        TargetAudience.entries.forEach { aud ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (aud) {
                                            TargetAudience.ALL_USERS -> "All Users (Broadcast)"
                                            TargetAudience.FREE_ONLY -> "Free Users Only"
                                            TargetAudience.PREMIUM_ONLY -> "Premium Users Only"
                                            TargetAudience.SPECIFIC_USER -> "Specific User UID"
                                        }
                                    )
                                },
                                onClick = {
                                    selectedAudience = aud
                                    audienceExpanded = false
                                }
                            )
                        }
                    }
                }

                if (selectedAudience == TargetAudience.SPECIFIC_USER) {
                    OutlinedTextField(
                        value = targetUserId,
                        onValueChange = { targetUserId = it },
                        label = { Text("Target Firebase User UID") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("input_announcement_target_uid"),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = actionUrl,
                    onValueChange = { actionUrl = it },
                    label = { Text("Optional Action Button URL (e.g. https://...)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (actionUrl.isNotBlank()) {
                    OutlinedTextField(
                        value = actionLabel,
                        onValueChange = { actionLabel = it },
                        label = { Text("Action Button Label (e.g. Download Now)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank() || message.isBlank()) return@Button
                    onSend(title, message, selectedType, selectedAudience, targetUserId, actionLabel, actionUrl)
                },
                enabled = title.isNotBlank() && message.isNotBlank(),
                modifier = Modifier.testTag("btn_confirm_send_announcement")
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Broadcast to Users")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
