package com.brycewg.asrkb.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareVoiceRecordingFeedbackTest {
    private var visible = false
    private val levels = mutableListOf<Float>()
    private val feedback = HardwareVoiceRecordingFeedback(
        show = { visible = true },
        hide = { visible = false },
        amplitude = { levels.add(it) }
    )

    @Test
    fun normalMicrophoneRecordingDoesNotStartKeyboardAnimation() {
        feedback.onStateChanged(KeyboardState.Listening())
        feedback.onAmplitude(0.7f)
        assertFalse(visible)
        assertTrue(levels.isEmpty())
    }

    @Test
    fun shortcutWaitsForRecordingToActuallyStartAndFollowsItsAmplitude() {
        feedback.prepareForRecording()
        assertFalse(visible)
        feedback.onAmplitude(0.9f)
        assertTrue(levels.isEmpty())

        feedback.onStateChanged(KeyboardState.Listening())
        assertTrue(visible)
        feedback.onAmplitude(0.6f)
        feedback.onAmplitude(-0.3f)
        feedback.onAmplitude(1.2f)
        assertEquals(listOf(0.6f, 0f, 1f), levels)
    }

    @Test
    fun stopKeyEndsAnimationWhileRecognitionAndPolishingContinue() {
        feedback.prepareForRecording()
        feedback.onStateChanged(KeyboardState.Listening())
        feedback.onStateChanged(KeyboardState.Processing)
        assertFalse(visible)
        feedback.onStateChanged(KeyboardState.AiProcessing("text"))
        feedback.onAmplitude(0.8f)
        assertFalse(visible)
        assertTrue(levels.isEmpty())
    }

    @Test
    fun failedRecordingStartDoesNotLeaveAnArmedAnimation() {
        feedback.prepareForRecording()
        feedback.onStateChanged(KeyboardState.Idle)
        feedback.onStateChanged(KeyboardState.Listening())
        assertFalse(visible)
    }

    @Test
    fun engineErrorOrAutomaticStopAlsoEndsAnimation() {
        feedback.prepareForRecording()
        feedback.onStateChanged(KeyboardState.Listening())
        feedback.onStateChanged(KeyboardState.Idle)
        assertFalse(visible)
        feedback.onAmplitude(0.8f)
        assertTrue(levels.isEmpty())
    }

    @Test
    fun endingInputSessionClearsAnimationAndIgnoresLateCallbacks() {
        feedback.prepareForRecording()
        feedback.onStateChanged(KeyboardState.Listening())
        feedback.clear()
        feedback.onStateChanged(KeyboardState.Listening(partialText = "late result"))
        feedback.onAmplitude(0.8f)
        assertFalse(visible)
        assertTrue(levels.isEmpty())
    }

    @Test
    fun newShortcutRecordingCanStartAfterPreviousAnimationEnded() {
        feedback.prepareForRecording()
        feedback.onStateChanged(KeyboardState.Listening())
        feedback.onStateChanged(KeyboardState.Processing)
        feedback.onStateChanged(KeyboardState.Idle)

        feedback.prepareForRecording()
        feedback.onStateChanged(KeyboardState.Listening())
        assertTrue(visible)
    }
}
