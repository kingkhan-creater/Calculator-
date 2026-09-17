package com.example.feature.admin

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PublishedWithChanges
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.core.admin.AppUpdateConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminAppUpdatesView(
    currentConfig: AppUpdateConfig?,
    onPublishUpdate: (versionCode: Int, versionName: String, apkUrl: String, releaseNotes: String, isMandatory: Boolean, minVersionCode: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var versionCodeText by remember { mutableStateOf(currentConfig?.versionCode?.toString() ?: "2") }
    var versionNameText by remember { mutableStateOf(currentConfig?.versionName ?: "1.1.0") }
    var apkUrlText by remember { mutableStateOf(currentConfig?.apkUrl ?: "https://github.com/your-username/your-repo/releases/download/v1.1.0/app-release.apk") }
    var releaseNotesText by remember { mutableStateOf(currentConfig?.releaseNotes ?: "• Added fast unencrypted cloud backup\n• Added scientific calculation mode\n• Added calculation history\n• General bug fixes and stability improvements") }
    var isMandatory by remember { mutableStateOf(currentConfig?.isMandatory ?: false) }
    var minVersionCodeText by remember { mutableStateOf(currentConfig?.minSupportedVersionCode?.toString() ?: "1") }

    var showConfirmDialog by remember { mutableStateOf(false) }

    // Sync if remote config loads
    LaunchedEffect(currentConfig) {
        currentConfig?.let {
            versionCodeText = it.versionCode.toString()
            versionNameText = it.versionName
            apkUrlText = it.apkUrl
            releaseNotesText = it.releaseNotes
            isMandatory = it.isMandatory
            minVersionCodeText = it.minSupportedVersionCode.toString()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("admin_app_updates_view"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Active Status Overview Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.RocketLaunch,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Live Server Version",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Current app binary: v${BuildConfig.VERSION_NAME} (code: ${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (currentConfig != null && currentConfig.versionCode > BuildConfig.VERSION_CODE) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = if (currentConfig != null && currentConfig.versionCode > BuildConfig.VERSION_CODE) "Update Available" else "App In Sync",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (currentConfig != null && currentConfig.versionCode > BuildConfig.VERSION_CODE) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "Published Version", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "v${currentConfig?.versionName ?: "1.0"}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text(text = "Version Code", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "${currentConfig?.versionCode ?: 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text(text = "Mandatory Update", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (currentConfig?.isMandatory == true) "YES (Forced)" else "NO (Optional)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (currentConfig?.isMandatory == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (!currentConfig?.apkUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "APK URL: ${currentConfig?.apkUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        // Publish New Update Form
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Publish In-App Update",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Deploy GitHub APK link or direct download link to all app users in real-time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = versionNameText,
                        onValueChange = { versionNameText = it },
                        label = { Text("Version Name (e.g. 1.2.0)") },
                        modifier = Modifier.weight(1f).testTag("input_update_version_name"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = versionCodeText,
                        onValueChange = { versionCodeText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Version Code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("input_update_version_code"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = apkUrlText,
                    onValueChange = { apkUrlText = it },
                    label = { Text("Direct APK Download / GitHub Release URL") },
                    placeholder = { Text("https://github.com/.../release.apk") },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrlText)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Invalid link: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "Test Link")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("input_update_apk_url"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = releaseNotesText,
                    onValueChange = { releaseNotesText = it },
                    label = { Text("What's New / Release Changelog") },
                    placeholder = { Text("• Feature 1\n• Feature 2\n• Bug fixes") },
                    modifier = Modifier.fillMaxWidth().testTag("input_update_release_notes"),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 3,
                    maxLines = 6
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Mandatory toggle
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Force Mandatory Update",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "If enabled, users cannot skip the dialog and must update to open the app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isMandatory,
                            onCheckedChange = { isMandatory = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("switch_mandatory_update")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        val vCode = versionCodeText.toIntOrNull()
                        if (vCode == null || vCode <= 0) {
                            Toast.makeText(context, "Please enter a valid version code number.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (versionNameText.isBlank()) {
                            Toast.makeText(context, "Please enter a version name.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (apkUrlText.isBlank() || !apkUrlText.startsWith("http")) {
                            Toast.makeText(context, "Please enter a valid APK HTTP/HTTPS URL.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        showConfirmDialog = true
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("btn_publish_app_update")
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Publish Update to All Users",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showConfirmDialog) {
        val vCode = versionCodeText.toIntOrNull() ?: 2
        val minCode = minVersionCodeText.toIntOrNull() ?: 1
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PublishedWithChanges, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm Update Release")
                }
            },
            text = {
                Text("Are you sure you want to publish version v$versionNameText (Code: $vCode) to all users? Active app users will be prompted to update.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onPublishUpdate(vCode, versionNameText, apkUrlText, releaseNotesText, isMandatory, minCode)
                    }
                ) {
                    Text("Confirm & Publish")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
