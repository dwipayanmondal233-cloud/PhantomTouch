package com.example.phantomtouch

import kotlin.math.abs

class OneEuro {

    private var x = 0f
    private var dx = 0f
    private var t = 0L
    private var ready = false

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * Math.PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }

    fun filter(v: Float, now: Long, minCutoff: Float, beta: Float): Float {
        if (!ready) {
            x = v
            dx = 0f
            t = now
            ready = true
            return v
        }
        val dt = maxOf((now - t) / 1000f, 0.001f)
        val dv = (v - x) / dt
        dx += alpha(1f, dt) * (dv - dx)
        val c = minCutoff + beta * abs(dx)
        x += alpha(c, dt) * (v - x)
        t = now
        return x
    }

    fun reset() {
        ready = false
    }
}