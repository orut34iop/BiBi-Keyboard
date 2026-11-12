package com.brycewg.asrkb.ui.floatingball

import android.content.Context
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.brycewg.asrkb.store.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class KeyboardRecordingOverlayTest {
    @Test
    fun missingPermissionPromptsOncePerRecordingAndDoesNotInterruptInput() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ShadowSettings.setCanDrawOverlays(false)
        var prompts = 0
        val overlay = KeyboardRecordingOverlay(context, Prefs(context), { null }, { prompts++ })

        overlay.show()
        overlay.show()
        overlay.updateAmplitude(0.5f)
        assertEquals(1, prompts)

        overlay.hide()
        overlay.show()
        assertEquals(2, prompts)
        overlay.dispose()
    }

    @Test
    fun screenAnimationCannotTakeFocusOrBlockTouchInput() {
        val params = KeyboardRecordingOverlay.createWindowParams()
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, params.type)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertTrue(params.alpha <= 0.8f)
        assertTrue(params.alpha > 0f)
    }
}
