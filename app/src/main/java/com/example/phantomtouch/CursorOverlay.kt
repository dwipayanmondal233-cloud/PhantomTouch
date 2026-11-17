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

class CursorView(context: Context) : View(context) {

    var paused = false
    var pinched = false
    var sleeping = false
    var slamUntil = 0L

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#00F0FF")
    }
    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()
        val cx = width / 2f
        val cy = height / 2f
        val r = width * 0.30f
        val slam = now < slamUntil
        if (Settings.skin == 1) {
            drawDot(canvas, cx, cy, r, slam)
        } else {
            drawMonkey(canvas, cx, cy, r, now, slam)
        }
        if (sleeping || slam) postInvalidateOnAnimation()
    }

    private fun drawDot(canvas: Canvas, cx: Float, cy: Float, r: Float, slam: Boolean) {
        val col = when {
            paused -> Color.parseColor("#888888")
            pinched -> Color.parseColor("#FF2BD6")
            else -> Color.parseColor("#00F0FF")
        }
        val rad = if (slam || pinched) r * 0.8f else r * 0.55f
        fill.color = col
        fill.alpha = 80
        canvas.drawCircle(cx, cy, rad, fill)
        fill.alpha = 255
        line.color = col
        line.strokeWidth = r * 0.14f
        canvas.drawCircle(cx, cy, rad, line)
        fill.color = Color.WHITE
        canvas.drawCircle(cx, cy, 3f, fill)
    }

    private fun drawMonkey(
        canvas: Canvas, cx: Float, cy: Float, r: Float, now: Long, slam: Boolean
    ) {
        val brown = if (paused) Color.parseColor("#6B6B6B") else Color.parseColor("#8B5A2B")
        val dark = Color.parseColor("#2A1A0A")

        canvas.save()
        if (slam) canvas.scale(1.1f, 0.78f, cx, cy + r)

        fill.color = brown
        canvas.drawCircle(cx - r * 1.0f, cy - r * 0.15f, r * 0.42f, fill)
        canvas.drawCircle(cx + r * 1.0f, cy - r * 0.15f, r * 0.42f, fill)
        fill.color = Color.parseColor("#E8A0A0")
        canvas.drawCircle(cx - r * 1.0f, cy - r * 0.15f, r * 0.24f, fill)
        canvas.drawCircle(cx + r * 1.0f, cy - r * 0.15f, r * 0.24f, fill)

        fill.color = brown
        canvas.drawCircle(cx, cy, r, fill)

        fill.color = Color.parseColor("#E8C79A")
        oval.set(cx - r * 0.62f, cy + r * 0.02f, cx + r * 0.62f, cy + r * 0.85f)
        canvas.drawOval(oval, fill)

        if (sleeping || paused || pinched) {
            line.color = dark
            line.strokeWidth = r * 0.10f
            canvas.drawLine(cx - r * 0.55f, cy - r * 0.2f, cx - r * 0.22f, cy - r * 0.2f, line)
            canvas.drawLine(cx + r * 0.22f, cy - r * 0.2f, cx + r * 0.55f, cy - r * 0.2f, line)
        } else {
            fill.color = Color.WHITE
            canvas.drawCircle(cx - r * 0.38f, cy - r * 0.2f, r * 0.2f, fill)
            canvas.drawCircle(cx + r * 0.38f, cy - r * 0.2f, r * 0.2f, fill)
            fill.color = Color.BLACK
            canvas.drawCircle(cx - r * 0.38f, cy - r * 0.2f, r * 0.1f, fill)
            canvas.drawCircle(cx + r * 0.38f, cy - r * 0.2f, r * 0.1f, fill)
        }

        fill.color = dark
        canvas.drawCircle(cx - r * 0.12f, cy + r * 0.3f, r * 0.05f, fill)
        canvas.drawCircle(cx + r * 0.12f, cy + r * 0.3f, r * 0.05f, fill)
        line.color = dark
        line.strokeWidth = r * 0.06f
        canvas.drawLine(cx - r * 0.2f, cy + r * 0.55f, cx + r * 0.2f, cy + r * 0.55f, line)
        canvas.restore()

        if (pinched) {
            line.color = Color.parseColor("#FF2BD6")
            line.strokeWidth = r * 0.1f
            canvas.drawCircle(cx, cy, r * 1.12f, line)
        }

        if (slam) {
            val p = 1f - (slamUntil - now) / 250f
            line.color = Color.WHITE
            line.alpha = (255 * (1f - p)).toInt().coerceIn(0, 255)
            line.strokeWidth = r * 0.1f
            canvas.drawCircle(cx, cy, r * (1.1f + 0.4f * p), line)
            line.alpha = 255
        }

        fill.color = Color.parseColor("#00F0FF")
        canvas.drawCircle(cx, cy, 4f, fill)

        if (sleeping) {
            val t = (now % 1500L) / 1500f
            txt.textSize = r * 0.7f
            txt.alpha = (255 * (1f - t)).toInt().coerceIn(0, 255)
            canvas.drawText("z", cx + r * 1.0f, cy - r * 0.9f - t * r * 0.4f, txt)
            txt.alpha = 255
        }
    }
}

class CursorOverlay(ctx: Context) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val size = (72 * ctx.resources.displayMetrics.density).toInt()
    private val view = CursorView(ctx)
    private val lp = WindowManager.LayoutParams(
        size,
        size,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    )
    private var added = false

    init {
        lp.gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        view.visibility = View.INVISIBLE
    }

    fun show() {
        if (!added) {
            wm.addView(view, lp)
            added = true
        }
    }

    fun setVisible(v: Boolean) {
        view.visibility = if (v) View.VISIBLE else View.INVISIBLE
    }

    fun slam(now: Long) {
        view.slamUntil = now + 250
        view.invalidate()
    }

    fun update(x: Float, y: Float, paused: Boolean, pinched: Boolean, sleeping: Boolean) {
        view.paused = paused
        view.pinched = pinched
        view.sleeping = sleeping
        lp.x = (x - size / 2f).toInt()
        lp.y = (y - size / 2f).toInt()
        if (added) {
            try {
                wm.updateViewLayout(view, lp)
            } catch (e: Exception) {
            }
        }
        view.invalidate()
    }

    fun remove() {
        if (added) {
            try {
                wm.removeView(view)
            } catch (e: Exception) {
            }
            added = false
        }
    }
}