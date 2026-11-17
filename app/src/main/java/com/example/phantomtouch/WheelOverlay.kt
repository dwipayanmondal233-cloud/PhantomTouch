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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class WheelView(context: Context) : View(context) {

    var labels: List<String> = emptyList()
    var side = 1
    var hover = -1
    var flash = -1
    var flashUntil = 0L

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val oval = RectF()

    // -2 = outside, -1 = centre (close), 0.. = slice number
    fun indexAt(lx: Float, ly: Float): Int {
        val n = labels.size
        if (n == 0) return -2
        val c = side / 2f
        val dx = lx - c
        val dy = ly - c
        val d = hypot(dx, dy)
        val outer = c - 6f
        if (d > outer) return -2
        if (d < outer * 0.30f) return -1
        val step = 360f / n
        var a = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 90f + step / 2f
        a = ((a % 360f) + 360f) % 360f
        return (a / step).toInt().coerceIn(0, n - 1)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()
        val n = labels.size
        if (n == 0) return
        val c = width / 2f
        val outer = c - 6f
        val inner = outer * 0.30f
        val step = 360f / n
        val cyan = Color.parseColor("#00F0FF")
        val purple = Color.parseColor("#B44CFF")
        oval.set(c - outer, c - outer, c + outer, c + outer)
        txt.textSize = outer * 0.10f

        for (i in 0 until n) {
            val flashing = i == flash && now < flashUntil
            fill.color = when {
                flashing -> cyan
                i == hover -> Color.parseColor("#1E4A66")
                else -> Color.parseColor("#E610202E")
            }
            val start = -90f + i * step - step / 2f + 1.5f
            canvas.drawArc(oval, start, step - 3f, true, fill)
            stroke.color = if (i % 2 == 0) cyan else purple
            canvas.drawArc(oval, start, step - 3f, true, stroke)

            val ang = Math.toRadians((-90f + i * step).toDouble())
            val r = (outer + inner) / 2f
            val tx = c + (r * cos(ang)).toFloat()
            val ty = c + (r * sin(ang)).toFloat() - (txt.ascent() + txt.descent()) / 2f
            txt.color = if (flashing) Color.BLACK else Color.WHITE
            canvas.drawText(labels[i], tx, ty, txt)
        }

        fill.color = Color.parseColor("#F005060A")
        canvas.drawCircle(c, c, inner, fill)
        stroke.color = Color.parseColor("#FF2BD6")
        canvas.drawCircle(c, c, inner, stroke)
        txt.color = Color.parseColor("#FF2BD6")
        txt.textSize = inner * 0.35f
        canvas.drawText("CLOSE", c, c - (txt.ascent() + txt.descent()) / 2f, txt)

        if (now < flashUntil) postInvalidateOnAnimation()
    }
}

class WheelOverlay(private val ctx: Context) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val view = WheelView(ctx)
    private var ids: List<String> = emptyList()
    private var ox = 0f
    private var oy = 0f

    var visible = false
        private set

    fun show(screenW: Int, screenH: Int, list: List<String>) {
        hide()
        ids = list
        val side = (min(screenW, screenH) * 0.92f).toInt()
        val p = WindowManager.LayoutParams(
            side,
            side,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = (screenW - side) / 2
        p.y = (screenH - side) / 2
        if (Build.VERSION.SDK_INT >= 28) {
            p.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        view.labels = list.map { WheelItems.shortLabel(ctx, it) }
        view.side = side
        view.hover = -1
        view.flash = -1
        ox = p.x.toFloat()
        oy = p.y.toFloat()
        try {
            wm.addView(view, p)
            visible = true
        } catch (e: Exception) {
            visible = false
        }
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

    fun itemAt(x: Float, y: Float): Int {
        if (!visible) return -2
        return view.indexAt(x - ox, y - oy)
    }

    fun idAt(i: Int): String = ids.getOrElse(i) { "" }

    fun hover(x: Float, y: Float) {
        val i = itemAt(x, y)
        if (i != view.hover) {
            view.hover = i
            view.invalidate()
        }
    }

    fun flash(i: Int) {
        view.flash = i
        view.flashUntil = System.currentTimeMillis() + 200
        view.invalidate()
    }
}