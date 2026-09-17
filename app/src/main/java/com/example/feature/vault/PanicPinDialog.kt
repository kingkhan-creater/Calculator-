package com.example.feature.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun PanicPinDialog(
    isAlreadyConfigured: Boolean,
    onSavePanicPin: (currentPin: String, newPanicPin: String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentMasterPin by remember { mutableStateOf("") }
    var newPanicPin by remember { mutableStateOf("") }
    var confirmPanicPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isAlreadyConfigured) "Change Panic PIN (Decoy Mode)" else "Set Panic PIN (Decoy Mode)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "When you enter this Panic PIN on the calculator, it will open an empty decoy vault. Your real photos, videos, and recordings will stay 100% hidden and safe.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = currentMasterPin,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 12) {
                            currentMasterPin = input
                            errorMessage = null
                        }
                    },
                    label = { Text("Current Master PIN") },
                    placeholder = { Text("Enter your Master PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_current_master_pin_for_panic")
                )

                OutlinedTextField(
                    value = newPanicPin,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 12) {
                            newPanicPin = input
                            errorMessage = null
                        }
                    },
                    label = { Text("New Panic PIN (4–12 digits)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_new_panic_pin")
                )

                OutlinedTextField(
                    value = confirmPanicPin,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 12) {
                            confirmPanicPin = input
                            errorMessage = null
                        }
                    },
                    label = { Text("Confirm Panic PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = errorMessage != null,
                    supportingText = {
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_confirm_panic_pin")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (currentMasterPin.isEmpty() || newPanicPin.isEmpty() || confirmPanicPin.isEmpty()) {
                        errorMessage = "All fields are required"
                    } else if (newPanicPin != confirmPanicPin) {
                        errorMessage = "Panic PIN and confirmation do not match"
                    } else if (newPanicPin.length < 4) {
                        errorMessage = "PIN must be at least 4 digits"
                    } else if (newPanicPin == currentMasterPin) {
                        errorMessage = "Panic PIN cannot be the same as your Master PIN"
                    } else {
                        onSavePanicPin(currentMasterPin, newPanicPin)
                    }
                },
                modifier = Modifier.testTag("btn_save_panic_pin")
            ) {
                Text("Save Panic PIN")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_cancel_panic_pin")
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
