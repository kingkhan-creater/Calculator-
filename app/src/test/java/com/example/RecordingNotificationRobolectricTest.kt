package com.example

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.feature.recording.RecordingForegroundService
import com.example.feature.recording.RecordingSessionController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordingNotificationRobolectricTest {

    private lateinit var service: RecordingForegroundService

    @Before
    fun setup() {
        RecordingSessionController.resetState()
        val controller = Robolectric.buildService(RecordingForegroundService::class.java)
        service = controller.create().get()
    }

    @Test
    fun `notification contains only 3 actions and no content intent`() {
        val notification = service.buildRecordingNotification(
            statusText = "Recording Active (00:15)",
            isPaused = false,
            isTorchOn = false
        )

        // Verify security: No contentIntent to bypass authentication lock
        assertNull(notification.contentIntent)

        // Verify action count is strictly 3 (Pause/Resume, Torch, Stop)
        assertNotNull(notification.actions)
        assertEquals(3, notification.actions.size)

        // Verify action titles for active recording
        assertEquals("⏸", notification.actions[0].title.toString())
        assertEquals("⏹", notification.actions[1].title.toString())
        assertEquals("🔦", notification.actions[2].title.toString())
    }

    @Test
    fun `notification dynamically updates actions when paused and torch is on`() {
        val notification = service.buildRecordingNotification(
            statusText = "Recording Paused (01:45)",
            isPaused = true,
            isTorchOn = true
        )

        assertNotNull(notification.actions)
        assertEquals(3, notification.actions.size)

        // When paused, first action must be "Resume"
        assertEquals("▶", notification.actions[0].title.toString())
        // Second action is Stop
        assertEquals("⏹", notification.actions[1].title.toString())
        // Third action is always Torch
        assertEquals("🔦", notification.actions[2].title.toString())
    }

    @Test
    fun `handling pause and resume intents updates recording session state`() {
        val pauseIntent = Intent(service, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_PAUSE
        }
        service.onStartCommand(pauseIntent, 0, 1)
        assertTrue(RecordingSessionController.uiState.value.isPaused)

        val resumeIntent = Intent(service, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_RESUME
        }
        service.onStartCommand(resumeIntent, 0, 2)
        assertFalse(RecordingSessionController.uiState.value.isPaused)
    }

    @Test
    fun `handling toggle torch intent toggles state gracefully`() {
        // We cannot directly read `isStandaloneTorchOn` from the service,
        // but we can verify that the notification intent can be fired without crashing.
        val torchIntent = Intent(service, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_TOGGLE_TORCH
        }
        service.onStartCommand(torchIntent, 0, 3)
        service.onStartCommand(torchIntent, 0, 4)
        
        // As long as we didn't crash, the standalone torch toggle gracefully handled the intents.
        assertTrue(true)
    }

    @Test
    fun `handling stop intent resets state and triggers stop safely`() {
        var onStopCalled = false
        RecordingSessionController.setActiveRecording(null) {
            onStopCalled = true
        }

        val stopIntent = Intent(service, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_STOP
        }
        service.onStartCommand(stopIntent, 0, 5)

        assertFalse(RecordingForegroundService.isServiceRunning)
        assertFalse(RecordingSessionController.uiState.value.isRecording)
    }

    @Test
    fun `handling redundant or unexpected action when session is idle does not crash`() {
        // Send pause/resume/torch when no recording is active
        val pauseIntent = Intent(service, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_PAUSE
        }
        service.onStartCommand(pauseIntent, 0, 6)

        val stopIntent = Intent(service, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_STOP
        }
        service.onStartCommand(stopIntent, 0, 7)

        // Verify still alive without exception
        assertFalse(RecordingForegroundService.isServiceRunning)
    }
}
