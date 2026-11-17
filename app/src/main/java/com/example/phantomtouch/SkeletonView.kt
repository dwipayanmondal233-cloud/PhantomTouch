package com.example.phantomtouch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min

class SkeletonView(context: Context) : View(context) {

    private var hands: List<Hand> = emptyList()
    private var fw = 3
    private var fh = 4

    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#1C2A3A")
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val tip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF2BD6")
    }

    private val links = arrayOf(
        intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 4),
        intArrayOf(0, 5), intArrayOf(5, 6), intArrayOf(6, 7), intArrayOf(7, 8),
        intArrayOf(5, 9), intArrayOf(9, 10), intArrayOf(10, 11), intArrayOf(11, 12),
        intArrayOf(9, 13), intArrayOf(13, 14), intArrayOf(14, 15), intArrayOf(15, 16),
        intArrayOf(13, 17), intArrayOf(17, 18), intArrayOf(18, 19), intArrayOf(19, 20),
        intArrayOf(0, 17)
    )

    fun update(newHands: List<Hand>, w: Int, h: Int) {
        hands = newHands
        fw = if (w > 0) w else 3
        fh = if (h > 0) h else 4
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = min(width.toFloat() / fw, height.toFloat() / fh)
        val rw = fw * scale
        val rh = fh * scale
        val left = (width - rw) / 2f
        val top = (height - rh) / 2f
        canvas.drawRoundRect(RectF(left, top, left + rw, top + rh), 24f, 24f, frame)

        for ((i, hand) in hands.withIndex()) {
            line.color = if (i == 0) Color.parseColor("#00F0FF") else Color.parseColor("#B44CFF")
            for (l in links) {
                val x1 = left + (1f - hand.x[l[0]]) * rw
                val y1 = top + hand.y[l[0]] * rh
                val x2 = left + (1f - hand.x[l[1]]) * rw
                val y2 = top + hand.y[l[1]] * rh
                canvas.drawLine(x1, y1, x2, y2, line)
            }
            for (j in hand.x.indices) {
                val px = left + (1f - hand.x[j]) * rw
                val py = top + hand.y[j] * rh
                val isTip = j == 4 || j == 8 || j == 12 || j == 16 || j == 20
                canvas.drawCircle(px, py, if (isTip) 10f else 6f, if (isTip) tip else dot)
            }
        }
    }
}