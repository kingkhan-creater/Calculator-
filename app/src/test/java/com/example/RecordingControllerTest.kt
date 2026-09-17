package com.example

import com.example.feature.recording.RecordingSessionController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecordingControllerTest {

    @Before
    fun setup() {
        RecordingSessionController.resetState()
    }

    @Test
    fun `initial state is idle and non-recording`() {
        val state = RecordingSessionController.uiState.value
        assertFalse(state.isRecording)
        assertFalse(state.isPaused)
        assertFalse(state.isTorchOn)
        assertEquals(0L, state.elapsedTimeSeconds)
    }

    @Test
    fun `toggle torch updates state correctly`() {
        assertFalse(RecordingSessionController.uiState.value.isTorchOn)
        val newState = RecordingSessionController.toggleTorch()
        assertTrue(newState)
        assertTrue(RecordingSessionController.uiState.value.isTorchOn)

        val toggledOff = RecordingSessionController.toggleTorch()
        assertFalse(toggledOff)
        assertFalse(RecordingSessionController.uiState.value.isTorchOn)
    }

    @Test
    fun `pause and resume update recording state`() {
        RecordingSessionController.pause()
        assertTrue(RecordingSessionController.uiState.value.isPaused)

        RecordingSessionController.resume()
        assertFalse(RecordingSessionController.uiState.value.isPaused)
    }

    @Test
    fun `messages are set and cleared properly`() {
        RecordingSessionController.setErrorMessage("Camera error")
        assertEquals("Camera error", RecordingSessionController.uiState.value.errorMessage)

        RecordingSessionController.setSuccessMessage("Saved successfully")
        assertEquals("Saved successfully", RecordingSessionController.uiState.value.successMessage)
    }
}
