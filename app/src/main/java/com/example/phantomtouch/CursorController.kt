package com.example.phantomtouch

import kotlin.math.hypot

class CursorFrame(
    val visible: Boolean,
    val paused: Boolean,
    val x: Float,
    val y: Float,
    val pinched: Boolean,
    val state: Int,
    val ratio: Float,
    val event: Int,
    val evX: Float,
    val evY: Float
)

class CursorController {

    companion object {
        const val EV_NONE = 0
        const val EV_CLICK = 1
        const val EV_DOUBLE = 2
        const val EV_DRAG_START = 3
        const val EV_DRAG_END = 4

        const val ST_IDLE = 0
        const val ST_PENDING = 1
        const val ST_DRAG = 2
    }

    private class Pt(val t: Long, val x: Float, val y: Float)

    var screenW = 1080f
    var screenH = 2000f

    var rawX = 0.5f
        private set
    var rawY = 0.5f
        private set

    private val fx = OneEuro()
    private val fy = OneEuro()

    private var mappedX = 0.5f
    private var mappedY = 0.5f
    private var curX = 0.5f
    private var curY = 0.5f

    private val hist = ArrayList<Pt>()

    private var state = ST_IDLE
    private var pinched = false
    private var flip = 0
    private var t0 = 0L
    private var anchorX = 0.5f
    private var anchorY = 0.5f

    private var lastClickT = 0L
    private var lastClickX = 0f
    private var lastClickY = 0f

    private fun endAll() {
        pinched = false
        flip = 0
        state = ST_IDLE
    }

    fun reset() {
        endAll()
        fx.reset()
        fy.reset()
        hist.clear()
    }

    private fun posAt(t: Long): Pt {
        for (i in hist.indices.reversed()) {
            if (hist[i].t <= t) return hist[i]
        }
        return hist[0]
    }

    fun process(hand: Hand?, w: Int, h: Int, now: Long): CursorFrame {
        if (hand == null) {
            val ev = if (state == ST_DRAG) EV_DRAG_END else EV_NONE
            endAll()
            fx.reset()
            fy.reset()
            hist.clear()
            return CursorFrame(false, false, curX, curY, false, ST_IDLE, 1f, ev, curX, curY)
        }

        val pose = GestureEngine.analyze(hand, w, h)
        val ratio = pose.pinchRatio
        val p = Settings.point
        rawX = 1f - hand.x[p]
        rawY = hand.y[p]

        if (pose.isFist()) {
            val ev = if (state == ST_DRAG) EV_DRAG_END else EV_NONE
            endAll()
            return CursorFrame(true, true, curX, curY, false, ST_IDLE, ratio, ev, curX, curY)
        }

        val sens = Settings.sens.coerceAtLeast(0.2f)
        val hw = Settings.calHw.coerceAtLeast(0.05f) / sens
        val hh = Settings.calHh.coerceAtLeast(0.05f) / sens
        val nx = ((rawX - Settings.calCx) / hw * 0.5f + 0.5f).coerceIn(0f, 1f)
        val ny = ((rawY - Settings.calCy) / hh * 0.5f + 0.5f).coerceIn(0f, 1f)
        val sx = fx.filter(nx, now, Settings.minCutoff, Settings.beta)
        val sy = fy.filter(ny, now, Settings.minCutoff, Settings.beta)

        val moved = hypot((sx - mappedX) * screenW, (sy - mappedY) * screenH)
        if (moved >= Settings.dead) {
            mappedX = sx
            mappedY = sy
        }

        hist.add(Pt(now, mappedX, mappedY))
        while (hist.size > 2 && now - hist[0].t > 700) hist.removeAt(0)

        val on = Settings.pinchOn
        val off = maxOf(Settings.pinchOff, on + 0.05f)
        val raw = if (pinched) ratio < off else ratio < on

        var ev = EV_NONE
        var evX = curX
        var evY = curY

        if (raw != pinched) {
            flip++
            if (flip >= 2) {
                pinched = raw
                flip = 0
                if (pinched) {
                    state = ST_PENDING
                    t0 = now
                    val a = posAt(now - 150)
                    anchorX = a.x
                    anchorY = a.y
                    curX = anchorX
                    curY = anchorY
                } else {
                    if (state == ST_PENDING) {
                        val near = hypot(
                            (anchorX - lastClickX) * screenW,
                            (anchorY - lastClickY) * screenH
                        ) < 80f
                        val dbl = (now - lastClickT < 450) && near
                        ev = if (dbl) EV_DOUBLE else EV_CLICK
                        lastClickT = if (dbl) 0L else now
                        lastClickX = anchorX
                        lastClickY = anchorY
                        evX = anchorX
                        evY = anchorY
                    } else if (state == ST_DRAG) {
                        ev = EV_DRAG_END
                        evX = curX
                        evY = curY
                    }
                    state = ST_IDLE
                }
            }
        } else {
            flip = 0
        }

        if (state == ST_PENDING && pinched && now - t0 > Settings.hold) {
            state = ST_DRAG
            ev = EV_DRAG_START
            evX = anchorX
            evY = anchorY
        }

        if (state != ST_PENDING) {
            curX = mappedX
            curY = mappedY
        }

        return CursorFrame(true, false, curX, curY, pinched, state, ratio, ev, evX, evY)
    }
}