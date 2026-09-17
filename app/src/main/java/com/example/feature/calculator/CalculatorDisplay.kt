package com.example.feature.calculator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CalculatorDisplay(
    expression: String,
    displayText: String,
    historyCount: Int = 0,
    isScientific: Boolean = false,
    onToggleScientific: () -> Unit = {},
    onToggleHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Toolbar: Scientific mode switch + History icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Scientific Mode Toggle Pill
            FilterChip(
                selected = isScientific,
                onClick = onToggleScientific,
                label = {
                    Text(
                        text = if (isScientific) "Scientific fx" else "Basic",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isScientific) FontWeight.Bold else FontWeight.Normal
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Functions,
                        contentDescription = "Scientific Toggle",
                        modifier = Modifier.size(16.dp)
                    )
                },
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.testTag("btn_toggle_scientific")
            )

            // History Button with Badge
            IconButton(
                onClick = onToggleHistory,
                modifier = Modifier.testTag("btn_toggle_history")
            ) {
                if (historyCount > 0) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Text(
                                    text = if (historyCount > 99) "99+" else "$historyCount",
                                    fontSize = 10.sp
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Calculation History",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Calculation History",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Calculation Expression and Value area
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom
        ) {
            // Expression History Line
            if (expression.isNotEmpty()) {
                Text(
                    text = expression,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-0.5).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .testTag("calculator_expression_text")
                )
            }

            // Main Result / Input Number Line
            val fontSize = when {
                displayText.length > 12 -> 34.sp
                displayText.length > 8 -> 44.sp
                else -> 56.sp
            }

            Text(
                text = displayText,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = fontSize,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = (-1.5).sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState, reverseScrolling = true)
                    .testTag("calculator_display_text")
            )

            // Subtle Divider Line
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}


