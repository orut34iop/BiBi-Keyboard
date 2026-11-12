package com.brycewg.asrkb.ime

/** Only keyboard-triggered recordings own the extra screen animation. */
internal class HardwareVoiceRecordingFeedback(
    private val show: () -> Unit,
    private val hide: () -> Unit,
    private val amplitude: (Float) -> Unit
) {
    private var armed = false
    private var recording = false

    fun prepareForRecording() {
        armed = true
    }

    fun onStateChanged(state: KeyboardState) {
        if (!armed) return
        if (state is KeyboardState.Listening) {
            recording = true
            show()
        } else {
            clear()
        }
    }

    fun onAmplitude(level: Float) {
        if (recording) amplitude(level.coerceIn(0f, 1f))
    }

    fun clear() {
        armed = false
        recording = false
        hide()
    }
}
