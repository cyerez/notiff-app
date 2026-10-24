package com.rez.notif.notif

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.rez.notif.i18n.t
import com.rez.notif.ui.components.fa

/**
 * A small "copy code" box on top of the screen. Android decides how long a pop-up notification stays,
 * so this box (with "appear on top" permission) is how the code stays up for the user's chosen seconds.
 * Tap "copy" to copy, × or swipe up to close.
 */
object CodeOverlay {
    private val main = Handler(Looper.getMainLooper())
    private var shown: View? = null
    private val closeLater = Runnable { close() }

    fun allowed(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Returns false when the box can't be shown (no permission), so the caller falls back to a pop-up notification. */
    fun show(context: Context, code: String, from: String, seconds: Int): Boolean {
        if (!allowed(context)) return false
        // The box can't show on the lock screen; there the normal pop-up is better.
        if (context.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true) return false
        if (context.getSystemService(android.os.PowerManager::class.java)?.isInteractive == false) return false
        val app = context.applicationContext
        main.post { runCatching { open(app, code, from, seconds) } }
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun open(context: Context, code: String, from: String, seconds: Int) {
        close()
        val wm = context.getSystemService(WindowManager::class.java) ?: return
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()
        val night = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val fg = if (night) 0xFFF4F1FA.toInt() else 0xFF1E1A2B.toInt()
        val dim = if (night) 0xFFB8B2C8.toInt() else 0xFF6B6680.toInt()
        val accent = 0xFF7C5CFF.toInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(10), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(if (night) 0xF0221E2E.toInt() else 0xF7FFFFFF.toInt())
                setStroke(dp(1), 0x337C5CFF)
            }
            elevation = dp(10).toFloat()
        }
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(context).apply {
            text = fa(code)
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
        })
        texts.addView(TextView(context).apply {
            text = from
            setTextColor(dim)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 1
        })
        root.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(TextView(context).apply {
            text = t("کپی کد")
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(accent) }
            setOnClickListener { CodeNotifier.copy(context, code); close() }
        })
        root.addView(TextView(context).apply {
            text = "×"
            setTextColor(dim)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setPadding(dp(12), dp(4), dp(6), dp(4))
            setOnClickListener { close() }
        })
        // Swipe up to close.
        var downY = 0f
        root.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> downY = e.rawY
                MotionEvent.ACTION_UP -> if (downY - e.rawY > dp(40)) close()
            }
            true
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = dp(36)
            horizontalMargin = 0f
        }
        val frame = android.widget.FrameLayout(context).apply {
            setPadding(dp(12), 0, dp(12), dp(12))
            addView(root)
        }
        wm.addView(frame, lp)
        shown = frame
        main.postDelayed(closeLater, seconds.coerceAtLeast(5) * 1000L)
    }

    fun close() {
        main.removeCallbacks(closeLater)
        val v = shown ?: return
        shown = null
        runCatching { v.context.getSystemService(WindowManager::class.java)?.removeView(v) }
    }
}
