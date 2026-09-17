package com.example.feature.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.example.integration.cloudinary.CloudinaryConfig

@Composable
fun CloudinaryConfigDialog(
    initialConfig: CloudinaryConfig,
    onSave: (CloudinaryConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var cloudName by remember { mutableStateOf(initialConfig.cloudName) }
    var uploadPreset by remember { mutableStateOf(initialConfig.uploadPreset) }
    var apiKey by remember { mutableStateOf(initialConfig.apiKey) }
    var apiSecret by remember { mutableStateOf(initialConfig.apiSecret) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.CloudUpload,
                contentDescription = "Cloudinary Settings",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Cloudinary Storage Setup",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "To enable direct zero-server uploads, enter your Cloud Name and an Unsigned Upload Preset from your Cloudinary console.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                OutlinedTextField(
                    value = cloudName,
                    onValueChange = { cloudName = it },
                    label = { Text("Cloud Name *") },
                    placeholder = { Text("e.g. demo_cloud") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_cloudinary_cloud_name")
                )

                OutlinedTextField(
                    value = uploadPreset,
                    onValueChange = { uploadPreset = it },
                    label = { Text("Upload Preset (Recommended)") },
                    placeholder = { Text("e.g. vault_unsigned_preset") },
                    supportingText = {
                        Text("Cloudinary Dashboard → Settings → Upload → Add Upload Preset (Unsigned)")
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_cloudinary_upload_preset")
                )

                Text(
                    text = "Optional: API Key & Secret (for Signed Uploads)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key (Optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_cloudinary_api_key")
                )

                OutlinedTextField(
                    value = apiSecret,
                    onValueChange = { apiSecret = it },
                    label = { Text("API Secret (Optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_cloudinary_api_secret")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        CloudinaryConfig(
                            cloudName = cloudName.trim(),
                            uploadPreset = uploadPreset.trim(),
                            apiKey = apiKey.trim(),
                            apiSecret = apiSecret.trim()
                        )
                    )
                },
                modifier = Modifier.testTag("btn_save_cloudinary_config")
            ) {
                Text("Save Settings")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_cancel_cloudinary_config")
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
