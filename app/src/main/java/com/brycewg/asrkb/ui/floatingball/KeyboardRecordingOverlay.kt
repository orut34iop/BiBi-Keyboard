package com.brycewg.asrkb.ui.floatingball

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.brycewg.asrkb.store.Prefs
import com.brycewg.asrkb.ui.BibiViewThemes

/** Reuses the floating ball ribbon without creating a second recording session or floating ball. */
internal class KeyboardRecordingOverlay(
    private val context: Context,
    private val prefs: Prefs,
    private val imeDecor: () -> ViewGroup?,
    private val onMissingOverlayPermission: () -> Unit
) {
    private var ribbon: RecordingRibbonOverlayView? = null
    private var decorHost: ViewGroup? = null
    private var windowHost: WindowManager? = null
    private var permissionHintShown = false
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        layoutInDecor()
    }

    fun show() {
        val decor = imeDecor()
        if (ribbon != null && decorHost !== decor) dispose()
        val view = ribbon ?: attach(decor) ?: return
        view.setTheme(BibiViewThemes.resolve(view.context, prefs))
        view.beginRecording()
    }

    fun updateAmplitude(level: Float) {
        ribbon?.updateLevel(level)
    }

    fun hide() {
        permissionHintShown = false
        val view = ribbon ?: return
        view.playExit {
            if (ribbon === view) dispose()
        }
    }

    fun dispose() {
        val view = ribbon ?: return
        ribbon = null
        view.cancelMotion()
        val decor = decorHost
        decorHost = null
        decor?.removeOnLayoutChangeListener(layoutListener)
        try {
            if (decor != null) {
                decor.overlay.remove(view)
            } else {
                windowHost?.removeViewImmediate(view)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Failed to detach keyboard recording animation", e)
        }
        windowHost = null
    }

    private fun attach(decor: ViewGroup?): RecordingRibbonOverlayView? {
        if (decor == null && !Settings.canDrawOverlays(context)) {
            if (!permissionHintShown) {
                permissionHintShown = true
                onMissingOverlayPermission()
            }
            return null
        }
        return try {
            val viewContext = decor?.context ?: overlayContext()
            val view = RecordingRibbonOverlayView(viewContext)
            view.cancelMotion()
            if (decor != null) {
                decor.overlay.add(view)
                decorHost = decor
                ribbon = view
                decor.addOnLayoutChangeListener(layoutListener)
                layoutInDecor()
            } else {
                val manager = viewContext.getSystemService(WindowManager::class.java)
                manager.addView(view, createWindowParams())
                windowHost = manager
                ribbon = view
            }
            view
        } catch (e: RuntimeException) {
            Log.w(TAG, "Failed to show keyboard recording animation", e)
            dispose()
            null
        }
    }

    private fun layoutInDecor() {
        val decor = decorHost ?: return
        val view = ribbon ?: return
        view.measure(
            View.MeasureSpec.makeMeasureSpec(decor.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(decor.height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, decor.width, decor.height)
    }

    private fun overlayContext(): Context {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return context
        val display = requireNotNull(
            context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        )
        return context.createDisplayContext(display).createWindowContext(
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            null
        )
    }

    companion object {
        private const val TAG = "KeyboardRecordingOverlay"

        internal fun createWindowParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Android 12+ permits touches through non-touchable overlays below 0.8 opacity.
            alpha = 0.6f
            setTitle("WIZ keyboard recording animation")
        }
    }
}
