package com.example.phantomtouch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class KeyboardView(context: Context) : View(context) {

    var rows: List<List<Key>> = KeyLayout.letters
    var shift = 0
    var hover: Key? = null
    var flash: Key? = null
    var flashUntil = 0L

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()
        canvas.drawColor(Color.parseColor("#F2070A12"))
        val rh = height / rows.size.toFloat()
        val pad = rh * 0.06f
        val cyan = Color.parseColor("#00F0FF")
        val purple = Color.parseColor("#B44CFF")

        for ((r, row) in rows.withIndex()) {
            var total = 0f
            for (key in row) total += key.weight
            var x = 0f
            for (key in row) {
                val w = width * key.weight / total
                rect.set(x + pad, r * rh + pad, x + w - pad, (r + 1) * rh - pad)
                x += w
                if (key.code == "#NONE") continue

                val flashing = key === flash && now < flashUntil
                val special = key.code.startsWith("#")
                fill.color = when {
                    flashing -> cyan
                    key === hover -> Color.parseColor("#1E4A66")
                    else -> Color.parseColor("#10202E")
                }
                stroke.color = if (special) purple else cyan
                canvas.drawRoundRect(rect, 16f, 16f, fill)
                canvas.drawRoundRect(rect, 16f, 16f, stroke)

                var label = key.label
                if (key.code == "#SHIFT") {
                    label = when (shift) {
                        1 -> "SHIFT"
                        2 -> "CAPS"
                        else -> "shift"
                    }
                } else if (shift > 0 && label.length == 1 && label[0].isLetter()) {
                    label = label.uppercase()
                }
                txt.textSize = if (label.length > 1) rh * 0.26f else rh * 0.40f
                txt.color = if (flashing) Color.BLACK else Color.WHITE
                val ty = rect.centerY() - (txt.ascent() + txt.descent()) / 2f
                canvas.drawText(label, rect.centerX(), ty, txt)
            }
        }
        if (now < flashUntil) postInvalidateOnAnimation()
    }
}

class KeyboardOverlay(private val ctx: Context) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val view = KeyboardView(ctx)
    private var top = 0f
    private var sw = 1f
    private var hpx = 1f

    var visible = false
        private set

    fun show(screenW: Int, screenH: Int) {
        hide()
        val dp = ctx.resources.displayMetrics.density
        val keyH = when (Settings.kbSize) {
            0 -> 44f
            2 -> 68f
            else -> 56f
        }
        val h = (keyH * 5f * dp).toInt().coerceAtMost((screenH * 0.6f).toInt())
        val p = WindowManager.LayoutParams(
            screenW,
            h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = 0
        p.y = screenH - h
        if (Build.VERSION.SDK_INT >= 28) {
            p.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        view.rows = KeyLayout.letters
        view.shift = 0
        view.hover = null
        view.flash = null
        try {
            wm.addView(view, p)
            visible = true
        } catch (e: Exception) {
            visible = false
        }
        sw = screenW.toFloat()
        hpx = h.toFloat()
        top = (screenH - h).toFloat()
    }

    fun hide() {
        if (visible) {
            try {
                wm.removeView(view)
            } catch (e: Exception) {
            }
            visible = false
        }
    }

    fun contains(x: Float, y: Float): Boolean =
        visible && x >= 0f && x <= sw && y >= top && y <= top + hpx

    fun keyAt(x: Float, y: Float): Key? {
        if (!contains(x, y)) return null
        return KeyLayout.find(view.rows, x / sw, (y - top) / hpx)
    }

    fun hover(x: Float, y: Float) {
        val k = keyAt(x, y)
        if (k !== view.hover) {
            view.hover = k
            view.invalidate()
        }
    }

    fun flash(key: Key) {
        view.flash = key
        view.flashUntil = System.currentTimeMillis() + 200
        view.invalidate()
    }

    fun setState(sym: Boolean, shift: Int) {
        view.rows = KeyLayout.rows(sym)
        view.shift = shift
        view.hover = null
        view.invalidate()
    }
}