package com.example.phantomtouch

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class Pt2(val x: Float, val y: Float)

class Match(val gesture: Gesture, val score: Float, val second: Float) {
    val name: String
        get() = gesture.name
}

object Recognizer {

    const val N = 64
    const val ACCEPT = 0.80f
    private const val SIZE = 250f
    private const val MIN_LEN = 80f
    private val HALF_DIAG = 0.5f * sqrt(2f * SIZE * SIZE)
    private val PHI = 0.5f * (-1f + sqrt(5f))
    private val D45 = Math.toRadians(45.0).toFloat()
    private val D2 = Math.toRadians(2.0).toFloat()

    private fun dist(a: Pt2, b: Pt2): Float = hypot(a.x - b.x, a.y - b.y)

    private fun pathLength(p: List<Pt2>): Float {
        var d = 0f
        for (i in 1 until p.size) d += dist(p[i - 1], p[i])
        return d
    }

    private fun centroid(p: List<Pt2>): Pt2 {
        var x = 0f
        var y = 0f
        for (q in p) {
            x += q.x
            y += q.y
        }
        return Pt2(x / p.size, y / p.size)
    }

    private fun resample(p: List<Pt2>): List<Pt2> {
        val interval = pathLength(p) / (N - 1)
        val src = ArrayList<Pt2>(p)
        val out = ArrayList<Pt2>()
        out.add(src[0])
        var d = 0f
        var i = 1
        while (i < src.size) {
            val dd = dist(src[i - 1], src[i])
            if (dd > 0f && d + dd >= interval) {
                val t = (interval - d) / dd
                val q = Pt2(
                    src[i - 1].x + t * (src[i].x - src[i - 1].x),
                    src[i - 1].y + t * (src[i].y - src[i - 1].y)
                )
                out.add(q)
                src.add(i, q)
                d = 0f
            } else {
                d += dd
            }
            i++
        }
        while (out.size < N) out.add(src[src.size - 1])
        return out.subList(0, N).toList()
    }

    private fun rotateBy(p: List<Pt2>, a: Float): List<Pt2> {
        val c = centroid(p)
        val co = cos(a)
        val si = sin(a)
        val out = ArrayList<Pt2>(p.size)
        for (q in p) {
            val dx = q.x - c.x
            val dy = q.y - c.y
            out.add(Pt2(dx * co - dy * si + c.x, dx * si + dy * co + c.y))
        }
        return out
    }

    private fun scaleAndCenter(p: List<Pt2>): List<Pt2> {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (q in p) {
            if (q.x < minX) minX = q.x
            if (q.y < minY) minY = q.y
            if (q.x > maxX) maxX = q.x
            if (q.y > maxY) maxY = q.y
        }
        val s = SIZE / max(max(maxX - minX, maxY - minY), 1f)
        val scaled = ArrayList<Pt2>(p.size)
        for (q in p) scaled.add(Pt2(q.x * s, q.y * s))
        val c = centroid(scaled)
        val out = ArrayList<Pt2>(p.size)
        for (q in scaled) out.add(Pt2(q.x - c.x, q.y - c.y))
        return out
    }

    // Returns null if the stroke is too short.
    fun normalize(raw: List<Pt2>): List<Pt2>? {
        if (raw.size < 5) return null
        if (pathLength(raw) < MIN_LEN) return null
        val r = resample(raw)
        val c = centroid(r)
        val ang = atan2(c.y - r[0].y, c.x - r[0].x)
        return scaleAndCenter(rotateBy(r, -ang))
    }

    private fun pathDistance(a: List<Pt2>, b: List<Pt2>): Float {
        var d = 0f
        for (i in a.indices) d += dist(a[i], b[i])
        return d / a.size
    }

    private fun distAt(p: List<Pt2>, t: List<Pt2>, a: Float): Float =
        pathDistance(rotateBy(p, a), t)

    private fun bestDistance(p: List<Pt2>, t: List<Pt2>): Float {
        var a = -D45
        var b = D45
        var x1 = PHI * a + (1f - PHI) * b
        var f1 = distAt(p, t, x1)
        var x2 = (1f - PHI) * a + PHI * b
        var f2 = distAt(p, t, x2)
        while (kotlin.math.abs(b - a) > D2) {
            if (f1 < f2) {
                b = x2
                x2 = x1
                f2 = f1
                x1 = PHI * a + (1f - PHI) * b
                f1 = distAt(p, t, x1)
            } else {
                a = x1
                x1 = x2
                f1 = f2
                x2 = (1f - PHI) * a + PHI * b
                f2 = distAt(p, t, x2)
            }
        }
        return if (f1 < f2) f1 else f2
    }

    // 0..1, higher = more alike. Both lists must be normalized.
    fun similarity(a: List<Pt2>, b: List<Pt2>): Float {
        val d = bestDistance(a, b)
        return (1f - d / HALF_DIAG).coerceIn(0f, 1f)
    }

    fun recognize(c: List<Pt2>, gestures: List<Gesture>): Match? {
        var best: Gesture? = null
        var bestS = -1f
        var second = 0f
        for (g in gestures) {
            if (g.samples.isEmpty()) continue
            var gs = 0f
            for (s in g.samples) {
                val v = similarity(c, s)
                if (v > gs) gs = v
            }
            if (gs > bestS) {
                if (best != null) second = bestS
                best = g
                bestS = gs
            } else if (gs > second) {
                second = gs
            }
        }
        val b = best ?: return null
        return Match(b, bestS, second.coerceAtLeast(0f))
    }
}