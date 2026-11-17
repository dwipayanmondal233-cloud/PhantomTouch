package com.example.phantomtouch

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class HudOverlay(ctx: Context) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val tv = TextView(ctx)
    private val lp = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    )
    private var added = false

    init {
        val d = ctx.resources.displayMetrics.density
        tv.typeface = Typeface.MONOSPACE
        tv.textSize = 11f
        tv.setTextColor(Color.parseColor("#00F0FF"))
        val padX = (8 * d).toInt()
        val padY = (3 * d).toInt()
        tv.setPadding(padX, padY, padX, padY)
        tv.text = "PHANTOM"
        val bg = GradientDrawable()
        bg.cornerRadius = 10 * d
        bg.setColor(Color.parseColor("#B005060A"))
        bg.setStroke((1.5f * d).toInt(), Color.parseColor("#B44CFF"))
        tv.background = bg
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = (8 * d).toInt()
        lp.y = (30 * d).toInt()
    }

    fun show() {
        if (!added) {
            try {
                wm.addView(tv, lp)
                added = true
            } catch (e: Exception) {
            }
        }
    }

    fun setText(s: String) {
        tv.text = s
    }

    fun remove() {
        if (added) {
            try {
                wm.removeView(tv)
            } catch (e: Exception) {
            }
            added = false
        }
    }
}