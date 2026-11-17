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

class DrawView(context: Context) : View(context) {

    var pts: List<Pt2> = emptyList()
    var active = false
    var clearAt = 0L
    var banner = ""
    var msg = ""
    var msgUntil = 0L

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    private fun pill(canvas: Canvas, text: String, cy: Float, size: Float, border: Int) {
        txt.textSize = size
        val w = txt.measureText(text) + size * 1.2f
        val h = size * 1.9f
        rect.set(width / 2f - w / 2f, cy - h / 2f, width / 2f + w / 2f, cy + h / 2f)
        fill.color = Color.parseColor("#E005060A")
        canvas.drawRoundRect(rect, h / 2f, h / 2f, fill)
        line.color = border
        line.strokeWidth = 3f
        canvas.drawRoundRect(rect, h / 2f, h / 2f, line)
        txt.color = Color.WHITE
        canvas.drawText(text, width / 2f, cy - (txt.ascent() + txt.descent()) / 2f, txt)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()
        val showPath = active || now < clearAt
        if (showPath && pts.size > 1) {
            line.color = if (active) Color.parseColor("#00F0FF") else Color.parseColor("#B44CFF")
            line.strokeWidth = 12f
            for (i in 1 until pts.size) {
                canvas.drawLine(pts[i - 1].x, pts[i - 1].y, pts[i].x, pts[i].y, line)
            }
        }
        if (banner.isNotEmpty()) {
            pill(canvas, banner, height * 0.07f, width * 0.034f, Color.parseColor("#B44CFF"))
        }
        if (now < msgUntil && msg.isNotEmpty()) {
            pill(canvas, msg, height * 0.15f, width * 0.042f, Color.parseColor("#00F0FF"))
        }
        if (now < msgUntil || now < clearAt) postInvalidateOnAnimation()
    }
}

class DrawOverlay(ctx: Context) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val view = DrawView(ctx)

    var visible = false
        private set

    fun show(screenW: Int, screenH: Int, banner: String) {
        hide()
        val p = WindowManager.LayoutParams(
            screenW,
            screenH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = 0
        p.y = 0
        if (Build.VERSION.SDK_INT >= 28) {
            p.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        view.banner = banner
        view.pts = emptyList()
        view.active = false
        view.clearAt = 0L
        view.msg = ""
        view.msgUntil = 0L
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

    fun begin() {
        view.pts = emptyList()
        view.active = true
        view.invalidate()
    }

    fun setPoints(list: List<Pt2>) {
        view.pts = ArrayList(list)
        view.invalidate()
    }

    fun end() {
        view.active = false
        view.clearAt = System.currentTimeMillis() + 1200
        view.invalidate()
    }

    fun say(text: String) {
        view.msg = text
        view.msgUntil = System.currentTimeMillis() + 2500
        view.invalidate()
    }
}