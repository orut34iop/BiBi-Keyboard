package com.brycewg.asrkb.ime

import android.view.InputDevice
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class HardwareVoiceShortcutTest {
    private val shortcut = HardwareVoiceShortcut()
    private var toggleCount = 0

    @Test
    fun twoPressesToggleTwiceWhileRepeatsAndReleasesAreConsumed() {
        assertTrue(send(key(KeyEvent.ACTION_DOWN)))
        assertTrue(send(key(KeyEvent.ACTION_DOWN, repeat = 1)))
        assertTrue(send(key(KeyEvent.ACTION_DOWN, repeat = 5)))
        assertTrue(send(key(KeyEvent.ACTION_UP)))
        assertEquals(1, toggleCount)

        assertTrue(send(key(KeyEvent.ACTION_DOWN, downTime = 200)))
        assertTrue(send(key(KeyEvent.ACTION_UP, downTime = 200)))
        assertEquals(2, toggleCount)
    }

    @Test
    fun nonKeyboardAndMissingEditorAndDisabledBindingDoNotTrigger() {
        assertFalse(send(key(KeyEvent.ACTION_DOWN), external = false))
        assertFalse(send(key(KeyEvent.ACTION_DOWN), connected = false))
        assertFalse(send(key(KeyEvent.ACTION_DOWN), configuredKeyCode = 0))
        assertFalse(send(key(KeyEvent.ACTION_DOWN, code = KeyEvent.KEYCODE_A)))
        assertEquals(0, toggleCount)
    }

    @Test
    fun unknownVendorAiKeyCanBeBoundByScanCode() {
        val down = key(KeyEvent.ACTION_DOWN, code = KeyEvent.KEYCODE_UNKNOWN, scan = 0x2f0)
        assertTrue(send(down, configuredKeyCode = 0, scan = 0x2f0))
        assertTrue(send(key(KeyEvent.ACTION_UP, code = 0, scan = 0x2f0), configuredKeyCode = 0, scan = 0x2f0))
        assertEquals(1, toggleCount)
        assertFalse(send(key(KeyEvent.ACTION_DOWN, code = 0, scan = 0x2f1), configuredKeyCode = 0, scan = 0x2f0))
    }

    @Test
    fun physicalBindingSurvivesAndroidKeyCodeRemapping() {
        assertTrue(send(key(KeyEvent.ACTION_DOWN, code = KeyEvent.KEYCODE_F9, scan = 66), scan = 66))
        assertEquals(1, toggleCount)
    }

    @Test
    fun modifiedKeyOnlyMatchesItsRecordedModifiersAndIgnoresCapsLock() {
        assertFalse(send(key(KeyEvent.ACTION_DOWN, meta = KeyEvent.META_CTRL_ON)))
        assertTrue(send(key(KeyEvent.ACTION_DOWN, meta = KeyEvent.META_CAPS_LOCK_ON)))
        send(key(KeyEvent.ACTION_UP))
        assertTrue(send(key(KeyEvent.ACTION_DOWN, meta = KeyEvent.META_CTRL_RIGHT_ON), meta = KeyEvent.META_CTRL_ON))
        assertEquals(2, toggleCount)
    }

    @Test
    fun releasingCapturedKeyIsConsumedAfterPreferenceAndModifierChanges() {
        assertTrue(send(key(KeyEvent.ACTION_DOWN)))
        assertTrue(send(key(KeyEvent.ACTION_UP, meta = KeyEvent.META_ALT_ON), configuredKeyCode = 0, connected = false))
        assertFalse(send(key(KeyEvent.ACTION_UP)))
        assertEquals(1, toggleCount)
    }

    @Test
    fun boundRightCtrlPassesThroughAndTogglesOnlyOnSoloRelease() {
        val ctrl = KeyEvent.KEYCODE_CTRL_RIGHT
        assertFalse(send(key(KeyEvent.ACTION_DOWN, code = ctrl, meta = KeyEvent.META_CTRL_RIGHT_ON), configuredKeyCode = ctrl))
        assertFalse(send(key(KeyEvent.ACTION_DOWN, code = ctrl, repeat = 2), configuredKeyCode = ctrl))
        assertEquals(0, toggleCount)
        assertFalse(send(key(KeyEvent.ACTION_UP, code = ctrl), configuredKeyCode = ctrl))
        assertEquals(1, toggleCount)
    }

    @Test
    fun rightCtrlChordDoesNotToggleOrConsumeNormalTyping() {
        val ctrl = KeyEvent.KEYCODE_CTRL_RIGHT
        assertFalse(send(key(KeyEvent.ACTION_DOWN, code = ctrl), configuredKeyCode = ctrl))
        assertFalse(send(key(KeyEvent.ACTION_DOWN, code = KeyEvent.KEYCODE_C, meta = KeyEvent.META_CTRL_ON), configuredKeyCode = ctrl))
        assertFalse(send(key(KeyEvent.ACTION_UP, code = KeyEvent.KEYCODE_C), configuredKeyCode = ctrl))
        assertFalse(send(key(KeyEvent.ACTION_UP, code = ctrl), configuredKeyCode = ctrl))
        assertEquals(0, toggleCount)
    }

    @Test
    fun canceledOrUnfocusedModifierReleaseDoesNotStartRecording() {
        val ctrl = KeyEvent.KEYCODE_CTRL_RIGHT
        send(key(KeyEvent.ACTION_DOWN, code = ctrl), configuredKeyCode = ctrl)
        send(key(KeyEvent.ACTION_UP, code = ctrl, flags = KeyEvent.FLAG_CANCELED), configuredKeyCode = ctrl)
        send(key(KeyEvent.ACTION_DOWN, code = ctrl), configuredKeyCode = ctrl)
        send(key(KeyEvent.ACTION_UP, code = ctrl), configuredKeyCode = ctrl, connected = false)
        assertEquals(0, toggleCount)
    }

    @Test
    fun lostReleaseAndBluetoothReconnectDoNotLeaveShortcutStuck() {
        send(key(KeyEvent.ACTION_DOWN))
        send(key(KeyEvent.ACTION_DOWN, downTime = 200))
        assertEquals(2, toggleCount)
        shortcut.reset()
        send(key(KeyEvent.ACTION_UP, downTime = 200))
        send(key(KeyEvent.ACTION_DOWN, downTime = 300))
        assertEquals(3, toggleCount)
    }

    @Test
    fun keyReleaseOnOtherKeyboardDoesNotReleaseCapturedPress() {
        send(key(KeyEvent.ACTION_DOWN))
        assertFalse(send(key(KeyEvent.ACTION_UP, deviceId = 8)))
        assertTrue(send(key(KeyEvent.ACTION_DOWN, repeat = 3)))
        assertTrue(send(key(KeyEvent.ACTION_UP)))
        assertEquals(1, toggleCount)
    }

    private fun send(
        event: KeyEvent,
        configuredKeyCode: Int = KeyEvent.KEYCODE_F8,
        external: Boolean = true,
        connected: Boolean = true,
        scan: Int = 0,
        meta: Int = 0
    ): Boolean = shortcut.onKeyEvent(
        event = event,
        configuredKeyCode = configuredKeyCode,
        isExternalKeyboard = external,
        hasInputConnection = connected,
        configuredScanCode = scan,
        configuredMetaState = meta
    ) { toggleCount++ }

    private fun key(
        action: Int,
        code: Int = KeyEvent.KEYCODE_F8,
        repeat: Int = 0,
        meta: Int = 0,
        scan: Int = 66,
        deviceId: Int = 7,
        downTime: Long = 100,
        flags: Int = 0
    ): KeyEvent = KeyEvent(downTime, downTime + 10, action, code, repeat, meta, deviceId, scan, flags, InputDevice.SOURCE_KEYBOARD)
}
