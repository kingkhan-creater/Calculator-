package com.example.feature.calculator

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    onNavigateToVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.unlockVaultEvent.collect {
            onNavigateToVault()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messageEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("calculator_screen"),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Display area (Takes flexible upper space)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                CalculatorDisplay(
                    expression = uiState.expressionText,
                    displayText = uiState.displayText,
                    historyCount = uiState.history.size,
                    isScientific = uiState.isScientificMode,
                    onToggleScientific = viewModel::onToggleScientificMode,
                    onToggleHistory = viewModel::onToggleHistory,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Keypad area
            CalculatorKeypad(
                onDigitClick = viewModel::onDigitClick,
                onDecimalClick = viewModel::onDecimalClick,
                onOperationClick = viewModel::onOperationClick,
                onClearClick = viewModel::onClearClick,
                onBackspaceClick = viewModel::onBackspaceClick,
                onToggleSignClick = viewModel::onToggleSignClick,
                onPercentageClick = viewModel::onPercentageClick,
                onEqualsClick = viewModel::onEqualsClick,
                isScientific = uiState.isScientificMode,
                onSquareRootClick = viewModel::onSquareRootClick,
                onSquareClick = viewModel::onSquareClick,
                onReciprocalClick = viewModel::onReciprocalClick,
                onConstantPiClick = viewModel::onConstantPiClick,
                onConstantEClick = viewModel::onConstantEClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }

        if (uiState.isHistoryOpen) {
            CalculatorHistorySheet(
                history = uiState.history,
                onDismiss = viewModel::onToggleHistory,
                onSelectItem = viewModel::onSelectHistoryItem,
                onDeleteItem = viewModel::onDeleteHistoryItem,
                onClearAll = viewModel::onClearHistory
            )
        }

        if (uiState.isSetupPromptVisible) {
            PinSetupDialog(
                initialPin = uiState.setupFirstPin,
                onConfirmPin = viewModel::onConfirmSetupPin,
                onDismiss = viewModel::onCancelSetup
            )
        }
    }
}
