package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.CalculatorOperation
import com.example.core.model.CalculatorState
import com.example.domain.calculator.CalculatorEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private val engine = CalculatorEngine()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Calculator", appName)
    }

    @Test
    fun `calculator engine handles basic addition`() {
        var state = CalculatorState()
        state = engine.onDigit(state, "5")
        state = engine.onOperation(state, CalculatorOperation.ADD)
        state = engine.onDigit(state, "7")
        state = engine.onEquals(state)
        assertEquals("12", state.displayText)
    }

    @Test
    fun `calculator engine handles division by zero safely`() {
        var state = CalculatorState()
        state = engine.onDigit(state, "9")
        state = engine.onOperation(state, CalculatorOperation.DIVIDE)
        state = engine.onDigit(state, "0")
        state = engine.onEquals(state)
        assertEquals("Error", state.displayText)
        assertTrue(state.hasError)
    }

    @Test
    fun `calculator engine handles decimal input correctly`() {
        var state = CalculatorState()
        state = engine.onDigit(state, "3")
        state = engine.onDecimal(state)
        state = engine.onDigit(state, "1")
        state = engine.onDigit(state, "4")
        assertEquals("3.14", state.displayText)
        // Adding second decimal does not duplicate
        state = engine.onDecimal(state)
        assertEquals("3.14", state.displayText)
    }

    @Test
    fun `calculator engine handles backspace`() {
        var state = CalculatorState()
        state = engine.onDigit(state, "1")
        state = engine.onDigit(state, "2")
        state = engine.onDigit(state, "3")
        assertEquals("123", state.displayText)
        state = engine.onBackspace(state)
        assertEquals("12", state.displayText)
    }
}
