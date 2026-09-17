package com.example.feature.calculator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.CalculatorOperation

@Composable
fun CalculatorKeypad(
    onDigitClick: (String) -> Unit,
    onDecimalClick: () -> Unit,
    onOperationClick: (CalculatorOperation) -> Unit,
    onClearClick: () -> Unit,
    onBackspaceClick: () -> Unit,
    onToggleSignClick: () -> Unit,
    onPercentageClick: () -> Unit,
    onEqualsClick: () -> Unit,
    isScientific: Boolean = false,
    onSquareRootClick: () -> Unit = {},
    onSquareClick: () -> Unit = {},
    onReciprocalClick: () -> Unit = {},
    onConstantPiClick: () -> Unit = {},
    onConstantEClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Scientific Functions Row (Collapsible)
        AnimatedVisibility(
            visible = isScientific,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScientificSmallButton(
                        text = "√x",
                        testTag = "btn_sqrt",
                        modifier = Modifier.weight(1f),
                        onClick = onSquareRootClick
                    )
                    ScientificSmallButton(
                        text = "x²",
                        testTag = "btn_square",
                        modifier = Modifier.weight(1f),
                        onClick = onSquareClick
                    )
                    ScientificSmallButton(
                        text = "1/x",
                        testTag = "btn_reciprocal",
                        modifier = Modifier.weight(1f),
                        onClick = onReciprocalClick
                    )
                    ScientificSmallButton(
                        text = "xʸ",
                        testTag = "btn_power",
                        modifier = Modifier.weight(1f),
                        onClick = { onOperationClick(CalculatorOperation.POWER) }
                    )
                    ScientificSmallButton(
                        text = "π",
                        testTag = "btn_pi",
                        modifier = Modifier.weight(1f),
                        onClick = onConstantPiClick
                    )
                    ScientificSmallButton(
                        text = "e",
                        testTag = "btn_e",
                        modifier = Modifier.weight(1f),
                        onClick = onConstantEClick
                    )
                }
            }
        }

        // Row 1: AC, +/-, %, ÷
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CalculatorButton(
                text = "AC",
                backgroundColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                textColor = MaterialTheme.colorScheme.error,
                testTag = "btn_clear",
                modifier = Modifier.weight(1f),
                onClick = onClearClick
            )
            CalculatorButton(
                text = "+/−",
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                textColor = MaterialTheme.colorScheme.primary,
                testTag = "btn_sign",
                modifier = Modifier.weight(1f),
                onClick = onToggleSignClick
            )
            CalculatorButton(
                text = "%",
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                textColor = MaterialTheme.colorScheme.primary,
                testTag = "btn_percent",
                modifier = Modifier.weight(1f),
                onClick = onPercentageClick
            )
            CalculatorButton(
                text = "÷",
                fontSize = 28.sp,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                testTag = "btn_divide",
                modifier = Modifier.weight(1f),
                onClick = { onOperationClick(CalculatorOperation.DIVIDE) }
            )
        }

        // Row 2: 7, 8, 9, ×
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CalculatorButton(
                text = "7",
                testTag = "btn_7",
                modifier = Modifier.weight(1f),
                onClick = { onDigitClick("7") }
            )
            CalculatorButton(
                text = "8",
                testTag = "btn_8",
                modifier = Modifier.weight(1f),
                onClick = { onDigitClick("8") }
            )
            CalculatorButton(
                text = "9",
                testTag = "btn_9",
                modifier = Modifier.weight(1f),
                onClick = { onDigitClick("9") }
            )
            CalculatorButton(
                text = "×",
                fontSize = 28.sp,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                testTag = "btn_multiply",
                modifier = Modifier.weight(1f),
                onClick = { onOperationClick(CalculatorOperation.MULTIPLY) }
            )
        }

        // Row 3: 4, 5, 6, −
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CalculatorButton(
                text = "4",
                testTag = "btn_4",
                modifier = Modifier.weight(1f),
                onClick = { onDigitClick("4") }
            )
            CalculatorButton(
                text = "5",
                testTag = "btn_5",
                modifier = Modifier.weight(1f),
                onClick = { onDigitClick("5") }
            )
            CalculatorButton(
                text = "6",
                testTag = "btn_6",
                modifier = Modifier.weight(1f),
                onClick = { onDigitClick("6") }
            )
            CalculatorButton(
                text = "−",
                fontSize = 28.sp,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                testTag = "btn_subtract",
                modifier = Modifier.weight(1f),
                onClick = { onOperationClick(CalculatorOperation.SUBTRACT) }
            )
        }

        // Row 4: 1, 2, 3, +
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CalculatorButton(
                text = "1",
                testTag = "btn_1",
                modifier = Modifier.weight(1f),
                onClick = { onDigitClick("1") }
            )
            CalculatorButton(
                text = "2",
                testTag = "btn_2",
                modifier = Modifier.weight(1f),
                onClick = { onDigitClick("2") }
            )
            CalculatorButton(
                text = "3",
                testTag = "btn_3",
                modifier = Modifier.weight(1f),
                onClick = { onDigitClick("3") }
            )
            CalculatorButton(
                text = "+",
                fontSize = 28.sp,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                testTag = "btn_add",
                modifier = Modifier.weight(1f),
                onClick = { onOperationClick(CalculatorOperation.ADD) }
            )
        }

        // Row 5: 0, ., ⌫, =
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CalculatorButton(
                text = "0",
                testTag = "btn_0",
                modifier = Modifier.weight(1f),
                onClick = { onDigitClick("0") }
            )
            CalculatorButton(
                text = ".",
                fontSize = 26.sp,
                testTag = "btn_dot",
                modifier = Modifier.weight(1f),
                onClick = onDecimalClick
            )
            CalculatorIconButton(
                testTag = "btn_backspace",
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                iconTint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                onClick = onBackspaceClick
            )
            CalculatorButton(
                text = "=",
                fontSize = 30.sp,
                backgroundColor = MaterialTheme.colorScheme.primary,
                textColor = MaterialTheme.colorScheme.onPrimary,
                testTag = "btn_equals",
                modifier = Modifier.weight(1f),
                onClick = onEqualsClick
            )
        }
    }
}

@Composable
private fun CalculatorButton(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 22.sp,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    testTag: String = "",
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .aspectRatio(1.15f)
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = 32.dp),
                onClick = onClick
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

@Composable
private fun ScientificSmallButton(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
    backgroundColor: Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
    textColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    testTag: String = "",
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = 24.dp),
                onClick = onClick
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

@Composable
private fun CalculatorIconButton(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    testTag: String = "",
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .aspectRatio(1.15f)
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = 32.dp),
                onClick = onClick
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = "Backspace",
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
    }
}


