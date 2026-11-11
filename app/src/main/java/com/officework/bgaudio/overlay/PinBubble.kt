package com.officework.bgaudio.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.officework.bgaudio.R
import com.officework.bgaudio.data.Prefs
import com.officework.bgaudio.keep.KeepCoordinator

object PinBubble {
    private var view: TextView? = null
    private var added = false

    fun hide(context: Context) {
        val bubble = view ?: return
        if (!added) return
        try {
            windowManager(context).removeView(bubble)
        } catch (_: IllegalArgumentException) {
        }
        added = false
    }

    fun sync(context: Context) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        if (!prefs.enabled || !Settings.canDrawOverlays(app)) {
            hide(app)
            return
        }
        val showWarn = prefs.warnPackage.isNotBlank() && !prefs.isAllowed(prefs.lastForeground)
        if (showWarn) show(app) else hide(app)
    }

    private fun show(context: Context) {
        val label = ensure(context)
        label.text = context.getString(R.string.not_allowed)
        label.setBackgroundResource(R.drawable.pin_bg_warn)
        label.setOnClickListener {
            Prefs(context).warnPackage = ""
            KeepCoordinator.publish(context)
        }
        if (added) return
        try {
            windowManager(context).addView(label, params(context))
            added = true
        } catch (e: RuntimeException) {
            Prefs(context).lastPlayResult = "overlay failed: ${e.message}"
        }
    }

    private fun ensure(context: Context): TextView {
        view?.let { return it }
        val density = context.resources.displayMetrics.density
        val bubble = TextView(context)
        bubble.setPadding((14 * density).toInt(), (8 * density).toInt(), (14 * density).toInt(), (8 * density).toInt())
        bubble.setTextColor(Color.parseColor("#102018"))
        bubble.textSize = 13f
        bubble.typeface = Typeface.DEFAULT_BOLD
        view = bubble
        return bubble
    }

    private fun params(context: Context): WindowManager.LayoutParams {
        val density = context.resources.displayMetrics.density
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (16 * density).toInt()
            y = (96 * density).toInt()
            title = "BgAudioPin"
        }
    }

    private fun windowManager(context: Context): WindowManager {
        return context.applicationContext.getSystemService(WindowManager::class.java)
    }
}
