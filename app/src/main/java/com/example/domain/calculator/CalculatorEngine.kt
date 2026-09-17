package com.example.domain.calculator

import com.example.core.model.CalculatorHistoryItem
import com.example.core.model.CalculatorOperation
import com.example.core.model.CalculatorState
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

class CalculatorEngine {

    private val decimalFormat = DecimalFormat("#,###.########", DecimalFormatSymbols(Locale.US)).apply {
        isGroupingUsed = false
    }

    fun onDigit(state: CalculatorState, digit: String): CalculatorState {
        if (state.hasError) {
            return state.copy(
                displayText = digit,
                expressionText = "",
                isNewNumber = false,
                hasError = false,
                isRawUserPinInput = true,
                rawTypedPinBuffer = digit
            )
        }

        val isFreshEntry = (state.isNewNumber || state.displayText == "0") && state.pendingOperation == null && state.previousNumber == null
        val newDisplay = if (state.isNewNumber || state.displayText == "0") {
            digit
        } else {
            if (state.displayText.replace(".", "").length >= 15) {
                state.displayText
            } else {
                state.displayText + digit
            }
        }

        val newRawPin = if (isFreshEntry) {
            digit
        } else if (state.isRawUserPinInput) {
            state.rawTypedPinBuffer + digit
        } else {
            ""
        }

        return state.copy(
            displayText = newDisplay,
            isNewNumber = false,
            isRawUserPinInput = isFreshEntry || state.isRawUserPinInput,
            rawTypedPinBuffer = newRawPin
        )
    }

    fun onDecimal(state: CalculatorState): CalculatorState {
        if (state.hasError) {
            return state.copy(
                displayText = "0.",
                expressionText = "",
                isNewNumber = false,
                hasError = false,
                isRawUserPinInput = false,
                rawTypedPinBuffer = ""
            )
        }

        val newDisplay = if (state.isNewNumber) {
            "0."
        } else if (!state.displayText.contains(".")) {
            state.displayText + "."
        } else {
            state.displayText
        }

        return state.copy(
            displayText = newDisplay,
            isNewNumber = false,
            isRawUserPinInput = false,
            rawTypedPinBuffer = ""
        )
    }

    fun onOperation(state: CalculatorState, operation: CalculatorOperation): CalculatorState {
        if (state.hasError) return state

        val currentVal = state.displayText.toDoubleOrNull() ?: 0.0

        if (state.previousNumber != null && state.pendingOperation != null && !state.isNewNumber) {
            val result = executeOperation(state.previousNumber, currentVal, state.pendingOperation)
            if (result.isNaN() || result.isInfinite()) {
                return state.copy(
                    displayText = "Error",
                    expressionText = "",
                    previousNumber = null,
                    pendingOperation = null,
                    hasError = true,
                    isNewNumber = true,
                    isRawUserPinInput = false,
                    rawTypedPinBuffer = ""
                )
            }

            val formatted = formatNumber(result)
            return state.copy(
                displayText = formatted,
                expressionText = "$formatted ${operation.symbol}",
                previousNumber = result,
                pendingOperation = operation,
                isNewNumber = true,
                isRawUserPinInput = false,
                rawTypedPinBuffer = ""
            )
        }

        return state.copy(
            expressionText = "${formatNumber(currentVal)} ${operation.symbol}",
            previousNumber = currentVal,
            pendingOperation = operation,
            isNewNumber = true,
            isRawUserPinInput = false,
            rawTypedPinBuffer = ""
        )
    }

    fun onEquals(state: CalculatorState): CalculatorState {
        if (state.hasError) return state

        val currentVal = state.displayText.toDoubleOrNull() ?: 0.0

        if (state.previousNumber != null && state.pendingOperation != null) {
            val result = executeOperation(state.previousNumber, currentVal, state.pendingOperation)
            if (result.isNaN() || result.isInfinite()) {
                return state.copy(
                    displayText = "Error",
                    expressionText = "${formatNumber(state.previousNumber)} ${state.pendingOperation.symbol} ${formatNumber(currentVal)} =",
                    previousNumber = null,
                    pendingOperation = null,
                    hasError = true,
                    isNewNumber = true,
                    isRawUserPinInput = false,
                    rawTypedPinBuffer = ""
                )
            }

            val formatted = formatNumber(result)
            val fullExpression = "${formatNumber(state.previousNumber)} ${state.pendingOperation.symbol} ${formatNumber(currentVal)}"
            val historyEntry = CalculatorHistoryItem(
                expression = fullExpression,
                result = formatted,
                timestamp = System.currentTimeMillis()
            )
            val updatedHistory = listOf(historyEntry) + state.history.take(49)

            return state.copy(
                displayText = formatted,
                expressionText = "$fullExpression =",
                previousNumber = null,
                pendingOperation = null,
                isNewNumber = true,
                isRawUserPinInput = false,
                rawTypedPinBuffer = "",
                history = updatedHistory
            )
        }

        return state.copy(
            expressionText = "${formatNumber(currentVal)} =",
            isNewNumber = true,
            isRawUserPinInput = false,
            rawTypedPinBuffer = ""
        )
    }

    fun onSquareRoot(state: CalculatorState): CalculatorState {
        if (state.hasError) return state
        val value = state.displayText.toDoubleOrNull() ?: return state
        if (value < 0) {
            return state.copy(
                displayText = "Error",
                hasError = true,
                isNewNumber = true,
                isRawUserPinInput = false,
                rawTypedPinBuffer = ""
            )
        }
        val result = sqrt(value)
        val formatted = formatNumber(result)
        val historyEntry = CalculatorHistoryItem(
            expression = "√(${formatNumber(value)})",
            result = formatted,
            timestamp = System.currentTimeMillis()
        )
        return state.copy(
            displayText = formatted,
            expressionText = "√(${formatNumber(value)}) =",
            isNewNumber = true,
            isRawUserPinInput = false,
            rawTypedPinBuffer = "",
            history = listOf(historyEntry) + state.history.take(49)
        )
    }

    fun onSquare(state: CalculatorState): CalculatorState {
        if (state.hasError) return state
        val value = state.displayText.toDoubleOrNull() ?: return state
        val result = value * value
        val formatted = formatNumber(result)
        val historyEntry = CalculatorHistoryItem(
            expression = "(${formatNumber(value)})²",
            result = formatted,
            timestamp = System.currentTimeMillis()
        )
        return state.copy(
            displayText = formatted,
            expressionText = "(${formatNumber(value)})² =",
            isNewNumber = true,
            isRawUserPinInput = false,
            rawTypedPinBuffer = "",
            history = listOf(historyEntry) + state.history.take(49)
        )
    }

    fun onReciprocal(state: CalculatorState): CalculatorState {
        if (state.hasError) return state
        val value = state.displayText.toDoubleOrNull() ?: return state
        if (value == 0.0) {
            return state.copy(
                displayText = "Error",
                hasError = true,
                isNewNumber = true,
                isRawUserPinInput = false,
                rawTypedPinBuffer = ""
            )
        }
        val result = 1.0 / value
        val formatted = formatNumber(result)
        val historyEntry = CalculatorHistoryItem(
            expression = "1 / (${formatNumber(value)})",
            result = formatted,
            timestamp = System.currentTimeMillis()
        )
        return state.copy(
            displayText = formatted,
            expressionText = "1 / (${formatNumber(value)}) =",
            isNewNumber = true,
            isRawUserPinInput = false,
            rawTypedPinBuffer = "",
            history = listOf(historyEntry) + state.history.take(49)
        )
    }

    fun onConstantPi(state: CalculatorState): CalculatorState {
        return state.copy(
            displayText = "3.14159265",
            isNewNumber = false,
            isRawUserPinInput = false,
            rawTypedPinBuffer = ""
        )
    }

    fun onConstantE(state: CalculatorState): CalculatorState {
        return state.copy(
            displayText = "2.71828182",
            isNewNumber = false,
            isRawUserPinInput = false,
            rawTypedPinBuffer = ""
        )
    }

    fun onClear(state: CalculatorState): CalculatorState {
        return state.copy(
            displayText = "0",
            expressionText = "",
            previousNumber = null,
            pendingOperation = null,
            isNewNumber = true,
            hasError = false,
            isRawUserPinInput = true,
            rawTypedPinBuffer = ""
        )
    }

    fun onBackspace(state: CalculatorState): CalculatorState {
        if (state.hasError || state.isNewNumber) {
            return state.copy(
                displayText = "0",
                isNewNumber = true,
                isRawUserPinInput = true,
                rawTypedPinBuffer = ""
            )
        }

        val newText = if (state.displayText.length > 1) {
            val cut = state.displayText.dropLast(1)
            if (cut == "-" || cut.isEmpty()) "0" else cut
        } else {
            "0"
        }

        val newRaw = if (state.isRawUserPinInput && state.rawTypedPinBuffer.isNotEmpty()) {
            state.rawTypedPinBuffer.dropLast(1)
        } else {
            ""
        }

        return state.copy(
            displayText = newText,
            isNewNumber = newText == "0",
            rawTypedPinBuffer = newRaw
        )
    }

    fun onToggleSign(state: CalculatorState): CalculatorState {
        if (state.hasError || state.displayText == "0") return state

        val newDisplay = if (state.displayText.startsWith("-")) {
            state.displayText.removePrefix("-")
        } else {
            "-${state.displayText}"
        }

        return state.copy(
            displayText = newDisplay,
            isRawUserPinInput = false,
            rawTypedPinBuffer = ""
        )
    }

    fun onPercentage(state: CalculatorState): CalculatorState {
        if (state.hasError) return state
        val value = state.displayText.toDoubleOrNull() ?: return state
        val percentValue = value / 100.0
        val formatted = formatNumber(percentValue)
        val historyEntry = CalculatorHistoryItem(
            expression = "${formatNumber(value)}%",
            result = formatted,
            timestamp = System.currentTimeMillis()
        )
        return state.copy(
            displayText = formatted,
            expressionText = "${formatNumber(value)}% =",
            isNewNumber = true,
            isRawUserPinInput = false,
            rawTypedPinBuffer = "",
            history = listOf(historyEntry) + state.history.take(49)
        )
    }

    fun onSelectHistoryItem(state: CalculatorState, item: CalculatorHistoryItem): CalculatorState {
        return state.copy(
            displayText = item.result,
            expressionText = "${item.expression} =",
            previousNumber = null,
            pendingOperation = null,
            isNewNumber = true,
            isRawUserPinInput = false,
            rawTypedPinBuffer = "",
            isHistoryOpen = false
        )
    }

    fun onDeleteHistoryItem(state: CalculatorState, itemId: String): CalculatorState {
        return state.copy(
            history = state.history.filter { it.id != itemId }
        )
    }

    fun onClearHistory(state: CalculatorState): CalculatorState {
        return state.copy(
            history = emptyList()
        )
    }

    fun toggleHistory(state: CalculatorState): CalculatorState {
        return state.copy(
            isHistoryOpen = !state.isHistoryOpen
        )
    }

    fun toggleScientific(state: CalculatorState): CalculatorState {
        return state.copy(
            isScientificMode = !state.isScientificMode
        )
    }

    private fun executeOperation(first: Double, second: Double, op: CalculatorOperation): Double {
        return when (op) {
            CalculatorOperation.ADD -> first + second
            CalculatorOperation.SUBTRACT -> first - second
            CalculatorOperation.MULTIPLY -> first * second
            CalculatorOperation.DIVIDE -> {
                if (second == 0.0) Double.NaN else first / second
            }
            CalculatorOperation.POWER -> first.pow(second)
        }
    }

    private fun formatNumber(num: Double): String {
        return if (num % 1.0 == 0.0 && num >= Long.MIN_VALUE.toDouble() && num <= Long.MAX_VALUE.toDouble()) {
            num.toLong().toString()
        } else {
            decimalFormat.format(num)
        }
    }
}

