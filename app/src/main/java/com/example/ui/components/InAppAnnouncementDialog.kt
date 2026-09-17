package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.admin.AdminAnnouncement
import com.example.core.admin.AnnouncementType

@Composable
fun InAppAnnouncementDialog(
    announcement: AdminAnnouncement,
    onDismissAndNeverShowAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val (icon, iconColor, containerColor) = when (announcement.type) {
        AnnouncementType.INFO -> Triple(
            Icons.Default.Info,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer
        )
        AnnouncementType.UPDATE -> Triple(
            Icons.Default.SystemUpdate,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.secondaryContainer
        )
        AnnouncementType.WARNING -> Triple(
            Icons.Default.Warning,
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer
        )
        AnnouncementType.PROMOTION -> Triple(
            Icons.Default.Stars,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.tertiaryContainer
        )
        AnnouncementType.SECURITY_ALERT -> Triple(
            Icons.Default.Security,
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer
        )
    }

    AlertDialog(
        onDismissRequest = onDismissAndNeverShowAgain,
        modifier = modifier.testTag("announcement_dialog_${announcement.id}"),
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = containerColor,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = announcement.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = announcement.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 22.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "This notice will not be shown again once dismissed.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            if (!announcement.actionUrl.isNullOrBlank()) {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(announcement.actionUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open link: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                        onDismissAndNeverShowAgain()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_announcement_action")
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = announcement.actionLabel?.ifBlank { "Learn More" } ?: "Learn More",
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onDismissAndNeverShowAgain,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_dismiss_announcement")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Got it, Dismiss",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            if (!announcement.actionUrl.isNullOrBlank()) {
                TextButton(
                    onClick = onDismissAndNeverShowAgain,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_announcement_secondary_dismiss")
                ) {
                    Text(
                        text = "Dismiss",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}
