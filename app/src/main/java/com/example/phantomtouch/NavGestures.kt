package com.example.phantomtouch

import kotlin.math.abs

class NavGestures {

    companion object {
        const val NONE = 0
        const val BACK = 1
        const val HOME = 2
        const val RECENTS = 3
        const val SCROLL_UP = 4
        const val SCROLL_DOWN = 5

        private const val FLICK_DIST = 0.28f
        private const val FLICK_MS = 300L
        private const val OPEN_MS = 250L
        private const val FIST_MS = 1200L
        private const val NAV_COOL = 1200L
        private const val SCROLL_STEP = 0.07f
        private const val SCROLL_COOL = 350L
    }

    private class Pt(val t: Long, val x: Float, val y: Float)

    private val hist = ArrayList<Pt>()
    private var openSince = 0L
    private var fistSince = 0L
    private var fistDone = false
    private var coolUntil = 0L
    private var quietUntil = 0L
    private var scrolling = false
    private var scrollAnchorY = 0f
    private var scrollCoolUntil = 0L

    fun reset() {
        hist.clear()
        openSince = 0L
        fistSince = 0L
        fistDone = false
        scrolling = false
    }

    fun process(hand: Hand?, w: Int, h: Int, now: Long): Int {
        if (hand == null) {
            reset()
            return NONE
        }

        val pose = GestureEngine.analyze(hand, w, h)

        if (pose.isFist()) {
            openSince = 0L
            scrolling = false
            if (fistSince == 0L) fistSince = now
            if (!fistDone && now - fistSince >= FIST_MS && now >= coolUntil) {
                fistDone = true
                coolUntil = now + NAV_COOL + 300L
                return RECENTS
            }
            return NONE
        }

        if (fistSince != 0L) {
            fistSince = 0L
            fistDone = false
            quietUntil = now + 600L
            hist.clear()
        }

        val cx = 1f - hand.x[9]
        val cy = hand.y[9]
        hist.add(Pt(now, cx, cy))
        while (hist.size > 2 && now - hist[0].t > 600L) hist.removeAt(0)

        if (now < quietUntil) return NONE

        val peace = pose.index && pose.middle && !pose.ring && !pose.pinky
        if (peace) {
            openSince = 0L
            if (!scrolling) {
                scrolling = true
                scrollAnchorY = cy
            }
            val dy = cy - scrollAnchorY
            if (abs(dy) > SCROLL_STEP && now >= scrollCoolUntil) {
                scrollAnchorY = cy
                scrollCoolUntil = now + SCROLL_COOL
                return if (dy < 0f) SCROLL_UP else SCROLL_DOWN
            }
            return NONE
        }
        scrolling = false

        if (pose.isOpen()) {
            if (openSince == 0L) openSince = now
        } else {
            openSince = 0L
        }

        if (openSince != 0L && now - openSince >= OPEN_MS && now >= coolUntil) {
            var old = hist[0]
            for (p in hist) {
                if (now - p.t <= FLICK_MS) {
                    old = p
                    break
                }
            }
            val dx = cx - old.x
            val dy = cy - old.y
            if (dx < -FLICK_DIST && abs(dx) > 2f * abs(dy)) {
                coolUntil = now + NAV_COOL
                hist.clear()
                return BACK
            }
            if (dy < -FLICK_DIST && abs(dy) > 2f * abs(dx)) {
                coolUntil = now + NAV_COOL
                hist.clear()
                return HOME
            }
        }
        return NONE
    }
}