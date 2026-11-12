package com.brycewg.asrkb.ime

import android.view.KeyEvent

/** Tracks physical presses, including vendor keys that only have a scan code. */
internal class HardwareVoiceShortcut {
    private data class Press(val deviceId: Int, val keyCode: Int, val scanCode: Int)

    private val heldKeys = mutableMapOf<Press, Long>()
    private val usedInChord = mutableSetOf<Press>()

    fun onKeyEvent(
        event: KeyEvent,
        configuredKeyCode: Int,
        isExternalKeyboard: Boolean,
        hasInputConnection: Boolean,
        configuredScanCode: Int = 0,
        configuredMetaState: Int = 0,
        onToggle: () -> Unit
    ): Boolean {
        val press = Press(event.deviceId, event.keyCode, event.scanCode)
        val isModifier = KeyEvent.isModifierKey(event.keyCode)
        if (event.action == KeyEvent.ACTION_DOWN) {
            heldKeys.keys.filter { it.deviceId == event.deviceId && it != press }
                .forEach { usedInChord.add(it) }
            // Recover if a previous UP was lost (e.g. Bluetooth disconnected mid-press).
            if (event.repeatCount == 0 && heldKeys[press] != event.downTime) {
                heldKeys.remove(press)
                usedInChord.remove(press)
            }
        }

        // Finish a captured press even if preferences, modifiers, or focus changed meanwhile.
        if (press in heldKeys) {
            if (event.action == KeyEvent.ACTION_UP) {
                heldKeys.remove(press)
                val chord = usedInChord.remove(press)
                if (isModifier && !chord && !event.isCanceled && hasInputConnection) {
                    onToggle()
                }
            }
            // Modifiers pass through so Ctrl/Shift/Alt combinations still work normally.
            return !isModifier
        }

        val matches = if (configuredScanCode > 0) {
            event.scanCode == configuredScanCode
        } else {
            configuredKeyCode != KeyEvent.KEYCODE_UNKNOWN && event.keyCode == configuredKeyCode
        }
        if (!isExternalKeyboard ||
            !hasInputConnection ||
            !matches ||
            event.action != KeyEvent.ACTION_DOWN ||
            event.isCanceled ||
            shortcutModifiers(event) != configuredMetaState
        ) {
            return false
        }
        heldKeys[press] = event.downTime
        if (event.repeatCount == 0 && !isModifier) onToggle()
        return !isModifier
    }

    fun reset() {
        heldKeys.clear()
        usedInChord.clear()
    }
}

/** Ignore lock states and left/right modifier bits; a bound modifier ignores its own bit. */
internal fun shortcutModifiers(event: KeyEvent): Int {
    val ownMask = when (event.keyCode) {
        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> KeyEvent.META_CTRL_ON
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> KeyEvent.META_ALT_ON
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> KeyEvent.META_SHIFT_ON
        KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> KeyEvent.META_META_ON
        KeyEvent.KEYCODE_SYM -> KeyEvent.META_SYM_ON
        KeyEvent.KEYCODE_FUNCTION -> KeyEvent.META_FUNCTION_ON
        else -> 0
    }
    val logicalModifiers = KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON or
        KeyEvent.META_SHIFT_ON or KeyEvent.META_META_ON or
        KeyEvent.META_SYM_ON or KeyEvent.META_FUNCTION_ON
    return KeyEvent.normalizeMetaState(event.metaState) and logicalModifiers and ownMask.inv()
}
