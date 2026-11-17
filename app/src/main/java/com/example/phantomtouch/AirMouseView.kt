package com.example.phantomtouch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

class AirMouseView(context: Context) : View(context) {

    var onSize: ((Int, Int) -> Unit)? = null
    var message: String = ""

    private class Btn(val r: RectF, var label: String)
    private class Rip(val x: Float, val y: Float, val t: Long)

    private val btns = ArrayList<Btn>()
    private val dblRect = RectF()
    private val zone = RectF()
    private val box = RectF()
    private val rips = ArrayList<Rip>()

    private var count = 0
    private var toggle = false
    private var logText = "Log: -"
    private var dblUntil = 0L
    private var flashIdx = -1
    private var flashUntil = 0L

    private var curX = 0f
    private var curY = 0f
    private var visible = false
    private var paused = false
    private var pinched = false
    private var state = 0

    private var boxDrag = false
    private var lastDX = 0f
    private var lastDY = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val m = w * 0.04f
        val bw = (w - 3 * m) / 2f
        val bh = h * 0.11f
        btns.clear()
        btns.add(Btn(RectF(m, m, m + bw, m + bh), "Click me"))
        btns.add(Btn(RectF(2 * m + bw, m, 2 * m + 2 * bw, m + bh), "Toggle: OFF"))
        btns.add(Btn(RectF(m, 2 * m + bh, m + bw, 2 * m + 2 * bh), "Counter: 0"))
        btns.add(Btn(RectF(2 * m + bw, 2 * m + bh, 2 * m + 2 * bw, 2 * m + 2 * bh), "Reset"))
        val dTop = 3 * m + 2 * bh
        dblRect.set(m, dTop, w - m, dTop + h * 0.10f)
        val zTop = dblRect.bottom + m
        zone.set(m, zTop, w - m, zTop + h * 0.22f)
        initBox()
        onSize?.invoke(w, h)
    }

    private fun initBox() {
        val bw = width * 0.3f
        val bh = zone.height() * 0.6f
        val l = zone.left + width * 0.04f
        val t = zone.centerY() - bh / 2f
        box.set(l, t, l + bw, t + bh)
    }

    private fun press(i: Int) {
        when (i) {
            0 -> logText = "Log: Click me pressed"
            1 -> {
                toggle = !toggle
                btns[1].label = "Toggle: " + (if (toggle) "ON" else "OFF")
                logText = "Log: toggle"
            }
            2 -> {
                count++
                btns[2].label = "Counter: $count"
                logText = "Log: counter"
            }
            3 -> {
                count = 0
                btns[2].label = "Counter: 0"
                initBox()
                logText = "Log: reset"
            }
        }
    }

    private fun click(x: Float, y: Float, isDouble: Boolean) {
        val now = System.currentTimeMillis()
        rips.add(Rip(x, y, now))
        for ((i, b) in btns.withIndex()) {
            if (b.r.contains(x, y)) {
                press(i)
                flashIdx = i
                flashUntil = now + 200
            }
        }
        if (isDouble && dblRect.contains(x, y)) dblUntil = now + 900
        if (isDouble) logText = "Log: double click"
        else if (logText == "Log: -") logText = "Log: click"
    }

    fun handle(f: CursorFrame) {
        visible = f.visible
        paused = f.paused
        pinched = f.pinched
        state = f.state
        curX = f.x * width
        curY = f.y * height

        if (boxDrag && f.state == CursorController.ST_DRAG) {
            box.offset(curX - lastDX, curY - lastDY)
            if (box.left < zone.left) box.offset(zone.left - box.left, 0f)
            if (box.right > zone.right) box.offset(zone.right - box.right, 0f)
            if (box.top < zone.top) box.offset(0f, zone.top - box.top)
            if (box.bottom > zone.bottom) box.offset(0f, zone.bottom - box.bottom)
            lastDX = curX
            lastDY = curY
        }

        val ex = f.evX * width
        val ey = f.evY * height
        when (f.event) {
            CursorController.EV_CLICK -> {
                logText = "Log: click"
                click(ex, ey, false)
            }
            CursorController.EV_DOUBLE -> click(ex, ey, true)
            CursorController.EV_DRAG_START -> {
                if (box.contains(ex, ey)) {
                    boxDrag = true
                    lastDX = ex
                    lastDY = ey
                    logText = "Log: drag start"
                } else {
                    logText = "Log: hold (nothing to drag)"
                }
            }
            CursorController.EV_DRAG_END -> {
                if (boxDrag) logText = "Log: drag end"
                boxDrag = false
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()
        canvas.drawColor(Color.parseColor("#05060A"))
        val cyan = Color.parseColor("#00F0FF")
        val purple = Color.parseColor("#B44CFF")

        txt.textSize = width * 0.05f
        for ((i, b) in btns.withIndex()) {
            val flash = (i == flashIdx && now < flashUntil)
            fill.color = if (flash) cyan else Color.parseColor("#10202E")
            stroke.color = if (i % 2 == 0) cyan else purple
            canvas.drawRoundRect(b.r, 28f, 28f, fill)
            canvas.drawRoundRect(b.r, 28f, 28f, stroke)
            txt.color = if (flash) Color.BLACK else Color.WHITE
            canvas.drawText(b.label, b.r.centerX(), b.r.centerY() + txt.textSize * 0.35f, txt)
        }

        stroke.color = purple
        canvas.drawRoundRect(dblRect, 28f, 28f, stroke)
        txt.color = Color.WHITE
        val dtxt = if (now < dblUntil) "DOUBLE ✓" else "Double pinch here"
        canvas.drawText(dtxt, dblRect.centerX(), dblRect.centerY() + txt.textSize * 0.35f, txt)

        stroke.color = Color.parseColor("#2A4A66")
        canvas.drawRoundRect(zone, 28f, 28f, stroke)
        fill.color = if (boxDrag) purple else cyan
        canvas.drawRoundRect(box, 28f, 28f, fill)
        txt.color = Color.BLACK
        canvas.drawText("DRAG ME", box.centerX(), box.centerY() + txt.textSize * 0.35f, txt)

        txt.textSize = width * 0.04f
        txt.color = Color.parseColor("#77AAAA")
        canvas.drawText(logText, width / 2f, zone.bottom + width * 0.09f, txt)

        var animate = now < flashUntil
        val it = rips.iterator()
        while (it.hasNext()) {
            val r = it.next()
            val age = (now - r.t) / 500f
            if (age >= 1f) {
                it.remove()
            } else {
                stroke.color = Color.WHITE
                stroke.alpha = (255 * (1f - age)).toInt()
                canvas.drawCircle(r.x, r.y, 14f + 60f * age, stroke)
                stroke.alpha = 255
                animate = true
            }
        }

        if (visible) {
            val ring = when {
                paused -> Color.parseColor("#888888")
                pinched -> Color.parseColor("#FF2BD6")
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
            stroke.strokeWidth = 4f
        }

        if (message.isNotEmpty()) {
            val lines = message.split("\n")
            txt.textSize = width * 0.045f
            txt.color = Color.WHITE
            val lh = txt.textSize * 1.4f
            val bh = lh * lines.size + lh * 0.6f
            val top = height * 0.85f - bh / 2f
            val rect = RectF(width * 0.04f, top, width * 0.96f, top + bh)
            fill.color = Color.parseColor("#E005060A")
            canvas.drawRoundRect(rect, 24f, 24f, fill)
            stroke.color = purple
            canvas.drawRoundRect(rect, 24f, 24f, stroke)
            for ((i, l) in lines.withIndex()) {
                canvas.drawText(l, width / 2f, top + lh * (i + 1), txt)
            }
        }

        if (animate) postInvalidateOnAnimation()
    }
}