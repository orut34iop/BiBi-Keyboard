package com.brycewg.asrkb.store

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HardwareVoiceShortcutPrefsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("asr_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun vendorScanCodeAndModifiersSurviveBackupRestoreAndCanBeRemoved() {
        val prefs = Prefs(context)
        assertEquals(0, prefs.hardwareVoiceKeyCode)
        assertEquals(0, prefs.hardwareVoiceScanCode)
        prefs.setHardwareVoiceShortcut(KeyEvent.KEYCODE_UNKNOWN, 0x2f0, KeyEvent.META_META_ON)
        val backup = prefs.exportJsonString()

        setUp()
        val restored = Prefs(context)
        assertTrue(restored.importJsonString(backup))
        assertEquals(KeyEvent.KEYCODE_UNKNOWN, restored.hardwareVoiceKeyCode)
        assertEquals(0x2f0, restored.hardwareVoiceScanCode)
        assertEquals(KeyEvent.META_META_ON, restored.hardwareVoiceMetaState)

        restored.setHardwareVoiceShortcut(0, 0, 0)
        val reloaded = Prefs(context)
        assertEquals(0, reloaded.hardwareVoiceKeyCode)
        assertEquals(0, reloaded.hardwareVoiceScanCode)
        assertEquals(0, reloaded.hardwareVoiceMetaState)
    }
}
