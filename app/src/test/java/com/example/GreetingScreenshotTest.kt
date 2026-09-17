package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.feature.calculator.CalculatorDisplay
import com.example.feature.calculator.CalculatorKeypad
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        Column(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
          CalculatorDisplay(
            expression = "12 × 8 =",
            displayText = "96"
          )
          CalculatorKeypad(
            onDigitClick = {},
            onDecimalClick = {},
            onOperationClick = {},
            onClearClick = {},
            onBackspaceClick = {},
            onToggleSignClick = {},
            onPercentageClick = {},
            onEqualsClick = {}
          )
        }
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
