package com.example.phantomtouch

import kotlin.math.hypot

class HandPose(
    val thumb: Boolean,
    val index: Boolean,
    val middle: Boolean,
    val ring: Boolean,
    val pinky: Boolean,
    val pinchRatio: Float
) {
    fun isFist(): Boolean = !index && !middle && !ring && !pinky

    fun isOpen(): Boolean = index && middle && ring && pinky

    fun name(): String {
        if (isFist()) return "FIST"
        if (pinchRatio < 0.25f) return "PINCH"
        if (isOpen()) return "OPEN"
        if (index && !middle && !ring && !pinky) return "POINT"
        return "OTHER"
    }

    fun fingerString(): String {
        val sb = StringBuilder()
        sb.append(if (thumb) "T " else "- ")
        sb.append(if (index) "I " else "- ")
        sb.append(if (middle) "M " else "- ")
        sb.append(if (ring) "R " else "- ")
        sb.append(if (pinky) "P" else "-")
        return sb.toString()
    }
}

object GestureEngine {

    fun analyze(h: Hand, w: Int, ht: Int): HandPose {
        fun d(a: Int, b: Int): Float {
            val dx = (h.x[a] - h.x[b]) * w
            val dy = (h.y[a] - h.y[b]) * ht
            return hypot(dx, dy)
        }

        fun ext(tip: Int, pip: Int): Boolean = d(tip, 0) > d(pip, 0)

        val thumb = d(4, 17) > d(3, 17)
        val index = ext(8, 6)
        val middle = ext(12, 10)
        val ring = ext(16, 14)
        val pinky = ext(20, 18)

        val palm = d(0, 9)
        val ratio = if (palm > 1f) d(4, 8) / palm else 1f

        return HandPose(thumb, index, middle, ring, pinky, ratio)
    }
}