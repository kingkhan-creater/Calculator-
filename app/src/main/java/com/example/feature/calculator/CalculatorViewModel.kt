package com.example.feature.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.model.CalculatorOperation
import com.example.core.model.CalculatorState
import com.example.core.model.SecurityResult
import com.example.core.model.SetupStep
import com.example.domain.calculator.CalculatorEngine
import com.example.domain.usecase.CheckPinSetupUseCase
import com.example.domain.usecase.SetupPinUseCase
import com.example.domain.usecase.VerifyPinUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CalculatorViewModel(
    private val calculatorEngine: CalculatorEngine,
    private val checkPinSetupUseCase: CheckPinSetupUseCase,
    private val verifyPinUseCase: VerifyPinUseCase,
    private val setupPinUseCase: SetupPinUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalculatorState())
    val uiState: StateFlow<CalculatorState> = _uiState.asStateFlow()

    private val _unlockVaultEvent = MutableSharedFlow<Unit>()
    val unlockVaultEvent: SharedFlow<Unit> = _unlockVaultEvent.asSharedFlow()

    private val _messageEvent = MutableSharedFlow<String>()
    val messageEvent: SharedFlow<String> = _messageEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            checkPinSetupUseCase().collect {
                // Keep reactive track of setup status
            }
        }
    }

    fun onDigitClick(digit: String) {
        _uiState.update { calculatorEngine.onDigit(it, digit) }
    }

    fun onDecimalClick() {
        _uiState.update { calculatorEngine.onDecimal(it) }
    }

    fun onOperationClick(operation: CalculatorOperation) {
        _uiState.update { calculatorEngine.onOperation(it, operation) }
    }

    fun onClearClick() {
        _uiState.update { calculatorEngine.onClear(it) }
    }

    fun onBackspaceClick() {
        _uiState.update { calculatorEngine.onBackspace(it) }
    }

    fun onToggleSignClick() {
        _uiState.update { calculatorEngine.onToggleSign(it) }
    }

    fun onPercentageClick() {
        _uiState.update { calculatorEngine.onPercentage(it) }
    }

    fun onSquareRootClick() {
        _uiState.update { calculatorEngine.onSquareRoot(it) }
    }

    fun onSquareClick() {
        _uiState.update { calculatorEngine.onSquare(it) }
    }

    fun onReciprocalClick() {
        _uiState.update { calculatorEngine.onReciprocal(it) }
    }

    fun onConstantPiClick() {
        _uiState.update { calculatorEngine.onConstantPi(it) }
    }

    fun onConstantEClick() {
        _uiState.update { calculatorEngine.onConstantE(it) }
    }

    fun onToggleScientificMode() {
        _uiState.update { calculatorEngine.toggleScientific(it) }
    }

    fun onToggleHistory() {
        _uiState.update { calculatorEngine.toggleHistory(it) }
    }

    fun onSelectHistoryItem(item: com.example.core.model.CalculatorHistoryItem) {
        _uiState.update { calculatorEngine.onSelectHistoryItem(it, item) }
    }

    fun onDeleteHistoryItem(itemId: String) {
        _uiState.update { calculatorEngine.onDeleteHistoryItem(it, itemId) }
    }

    fun onClearHistory() {
        _uiState.update { calculatorEngine.onClearHistory(it) }
    }

    fun onEqualsClick() {
        val currentState = _uiState.value
        val candidatePin = currentState.displayText.trim()

        viewModelScope.launch {
            val isConfigured = checkPinSetupUseCase.isConfiguredSync()

            // Vault PIN check is ONLY triggered if the user directly typed this numeric sequence
            // without any preceding math operations, and NOT as a result of an arithmetic calculation (like 1111+1111=2222)
            val isEligiblePinAttempt = currentState.isRawUserPinInput &&
                    currentState.pendingOperation == null &&
                    currentState.previousNumber == null &&
                    currentState.rawTypedPinBuffer == candidatePin &&
                    candidatePin.matches(Regex("^[0-9]{4,12}$"))

            if (isConfigured) {
                if (isEligiblePinAttempt) {
                    val pinType = verifyPinUseCase.verifyPinType(candidatePin)
                    if (pinType == com.example.core.security.PinVerificationResult.MASTER) {
                        // Master PIN matched! Unlock the full real vault.
                        com.example.core.security.VaultSessionManager.setDecoyMode(false)
                        _uiState.update { it.copy(displayText = "0", expressionText = "", isNewNumber = true, isRawUserPinInput = true, rawTypedPinBuffer = "") }
                        _unlockVaultEvent.emit(Unit)
                        return@launch
                    } else if (pinType == com.example.core.security.PinVerificationResult.PANIC) {
                        // Panic PIN matched! Unlock the safe Decoy vault without exposing real files.
                        com.example.core.security.VaultSessionManager.setDecoyMode(true)
                        _uiState.update { it.copy(displayText = "0", expressionText = "", isNewNumber = true, isRawUserPinInput = true, rawTypedPinBuffer = "") }
                        _unlockVaultEvent.emit(Unit)
                        return@launch
                    }
                }
                // If not eligible PIN or not matching PIN, behave purely as a normal calculator
                _uiState.update { calculatorEngine.onEquals(it) }
            } else {
                // No PIN is configured yet (first-time launch)
                if (isEligiblePinAttempt) {
                    // Start secret PIN setup flow
                    _uiState.update {
                        it.copy(
                            isSetupPromptVisible = true,
                            setupStep = SetupStep.CONFIRM_PIN,
                            setupFirstPin = candidatePin
                        )
                    }
                } else {
                    // Normal math
                    _uiState.update { calculatorEngine.onEquals(it) }
                }
            }
        }
    }

    fun onConfirmSetupPin(confirmedPin: String) {
        val firstPin = _uiState.value.setupFirstPin
        if (confirmedPin != firstPin) {
            _uiState.update { it.copy(isSetupPromptVisible = false, setupFirstPin = "") }
            viewModelScope.launch {
                _messageEvent.emit("PINs do not match. Please try again.")
            }
            return
        }

        viewModelScope.launch {
            when (val result = setupPinUseCase(confirmedPin)) {
                is SecurityResult.Success -> {
                    _uiState.update {
                        it.copy(
                            displayText = "0",
                            expressionText = "",
                            isNewNumber = true,
                            isSetupPromptVisible = false,
                            setupFirstPin = ""
                        )
                    }
                    _messageEvent.emit("Vault PIN successfully created!")
                    _unlockVaultEvent.emit(Unit)
                }
                is SecurityResult.Error -> {
                    _uiState.update { it.copy(isSetupPromptVisible = false, setupFirstPin = "") }
                    _messageEvent.emit(result.message)
                }
            }
        }
    }

    fun onCancelSetup() {
        _uiState.update {
            it.copy(
                isSetupPromptVisible = false,
                setupFirstPin = ""
            )
        }
    }

    class Factory(
        private val calculatorEngine: CalculatorEngine,
        private val checkPinSetupUseCase: CheckPinSetupUseCase,
        private val verifyPinUseCase: VerifyPinUseCase,
        private val setupPinUseCase: SetupPinUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CalculatorViewModel(
                calculatorEngine,
                checkPinSetupUseCase,
                verifyPinUseCase,
                setupPinUseCase
            ) as T
        }
    }
}
