package com.example.phantomtouch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

class PracticeView(context: Context) : View(context) {

    var onSize: ((Int, Int) -> Unit)? = null
    val target = RectF()
    var title = ""
    var body = ""
    var progress = 0
    var total = 1
    var showTarget = false
    var flashUntil = 0L
    var last = "-"

    private var curX = 0f
    private var curY = 0f
    private var visible = false
    private var paused = false
    private var pinched = false
    private var state = 0

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val bar = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        target.set(w * 0.2f, h * 0.50f, w * 0.8f, h * 0.50f + h * 0.14f)
        onSize?.invoke(w, h)
    }

    fun setFrame(f: CursorFrame) {
        visible = f.visible
        paused = f.paused
        pinched = f.pinched
        state = f.state
        curX = f.x * width
        curY = f.y * height
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()
        val cyan = Color.parseColor("#00F0FF")
        val purple = Color.parseColor("#B44CFF")
        val pink = Color.parseColor("#FF2BD6")
        canvas.drawColor(Color.parseColor("#05060A"))

        val m = width * 0.05f
        fill.color = Color.parseColor("#1C2A3A")
        bar.set(m, m, width - m, m + 12f)
        canvas.drawRoundRect(bar, 6f, 6f, fill)
        fill.color = cyan
        val pw = (width - 2 * m) * progress / total.coerceAtLeast(1)
        bar.set(m, m, m + pw, m + 12f)
        canvas.drawRoundRect(bar, 6f, 6f, fill)

        var y = m + width * 0.12f
        txt.color = cyan
        txt.textSize = width * 0.06f
        canvas.drawText(title, width / 2f, y, txt)

        txt.color = Color.WHITE
        txt.textSize = width * 0.04f
        val lh = txt.textSize * 1.5f
        for (line in body.split("\n")) {
            y += lh
            canvas.drawText(line, width / 2f, y, txt)
        }

        if (showTarget) {
            val ok = now < flashUntil
            fill.color = if (ok) cyan else Color.parseColor("#10202E")
            canvas.drawRoundRect(target, 36f, 36f, fill)
            stroke.color = purple
            canvas.drawRoundRect(target, 36f, 36f, stroke)
            txt.color = if (ok) Color.BLACK else Color.WHITE
            txt.textSize = width * 0.06f
            canvas.drawText(
                "TARGET",
                target.centerX(),
                target.centerY() - (txt.ascent() + txt.descent()) / 2f,
                txt
            )
        }

        if (now < flashUntil) {
            txt.color = Color.parseColor("#88FFBB")
            txt.textSize = width * 0.08f
            canvas.drawText("✓ GOOD", width / 2f, height * 0.78f, txt)
        }

        txt.color = Color.parseColor("#77AAAA")
        txt.textSize = width * 0.038f
        canvas.drawText("Seen: $last", width / 2f, height - m, txt)

        if (visible) {
            val ring = when {
                paused -> Color.parseColor("#888888")
                pinched -> pink
                else -> cyan
            }
            val rad = if (state == CursorController.ST_DRAG) 30f else 20f
            fill.color = ring
            fill.alpha = 70
            canvas.drawCircle(curX, curY, rad, fill)
            fill.alpha = 255
            stroke.color = ring
            stroke.strokeWidth = 6f
            canvas.drawCircle(curX, curY, rad, stroke)
            stroke.strokeWidth = 5f
        }

        if (now < flashUntil) postInvalidateOnAnimation()
    }
}