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
fun ChangePinDialog(
    onChangePin: (currentPin: String, newPin: String, confirmPin: String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Change Vault PIN",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 12) {
                            currentPin = input
                            errorMessage = null
                        }
                    },
                    label = { Text("Current PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_current_pin")
                )

                OutlinedTextField(
                    value = newPin,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 12) {
                            newPin = input
                            errorMessage = null
                        }
                    },
                    label = { Text("New PIN (4–12 digits)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_new_pin")
                )

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 12) {
                            confirmPin = input
                            errorMessage = null
                        }
                    },
                    label = { Text("Confirm New PIN") },
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
                        .testTag("input_confirm_new_pin")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (currentPin.isEmpty() || newPin.isEmpty() || confirmPin.isEmpty()) {
                        errorMessage = "All fields are required"
                    } else if (newPin != confirmPin) {
                        errorMessage = "New PIN and confirmation do not match"
                    } else if (newPin.length < 4) {
                        errorMessage = "PIN must be at least 4 digits"
                    } else {
                        onChangePin(currentPin, newPin, confirmPin)
                    }
                },
                modifier = Modifier.testTag("btn_save_new_pin")
            ) {
                Text("Update PIN")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_cancel_change_pin")
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
