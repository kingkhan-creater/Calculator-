package com.example.core.model

data class CalculatorHistoryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val expression: String,
    val result: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class CalculatorOperation(val symbol: String) {
    ADD("+"),
    SUBTRACT("−"),
    MULTIPLY("×"),
    DIVIDE("÷"),
    POWER("^")
}

data class CalculatorState(
    val displayText: String = "0",
    val expressionText: String = "",
    val previousNumber: Double? = null,
    val pendingOperation: CalculatorOperation? = null,
    val isNewNumber: Boolean = true,
    val hasError: Boolean = false,
    val isRawUserPinInput: Boolean = true,
    val rawTypedPinBuffer: String = "",
    val isSetupPromptVisible: Boolean = false,
    val setupStep: SetupStep = SetupStep.ENTER_PIN,
    val setupFirstPin: String = "",
    val isVaultUnlocked: Boolean = false,
    val history: List<CalculatorHistoryItem> = emptyList(),
    val isHistoryOpen: Boolean = false,
    val isScientificMode: Boolean = false,
    val memoryValue: Double = 0.0
)

enum class SetupStep {
    ENTER_PIN,
    CONFIRM_PIN
}

