package com.example.phantomtouch

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import kotlin.math.hypot

class PhantomAccess : AccessibilityService() {

    companion object {
        @Volatile
        var instance: PhantomAccess? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    private fun stroke(
        x1: Float, y1: Float, x2: Float, y2: Float, start: Long, dur: Long
    ): GestureDescription.StrokeDescription {
        val p = Path()
        p.moveTo(x1, y1)
        if (x1 != x2 || y1 != y2) p.lineTo(x2, y2)
        return GestureDescription.StrokeDescription(p, start, dur)
    }

    private fun send(g: GestureDescription) {
        try {
            dispatchGesture(g, null, null)
        } catch (e: Exception) {
        }
    }

    fun tap(x: Float, y: Float) {
        send(GestureDescription.Builder().addStroke(stroke(x, y, x, y, 0, 60)).build())
    }

    fun doubleTap(x: Float, y: Float) {
        send(
            GestureDescription.Builder()
                .addStroke(stroke(x, y, x, y, 0, 50))
                .addStroke(stroke(x, y, x, y, 150, 50))
                .build()
        )
    }

    fun longPress(x: Float, y: Float) {
        send(GestureDescription.Builder().addStroke(stroke(x, y, x, y, 0, 700)).build())
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, dur: Long) {
        send(GestureDescription.Builder().addStroke(stroke(x1, y1, x2, y2, 0, dur)).build())
    }

    // Called when a pinch-hold ends. Small move = long press, big move = drag.
    fun dragOrHold(x1: Float, y1: Float, x2: Float, y2: Float) {
        val d = hypot(x2 - x1, y2 - y1)
        if (d < 40f) {
            longPress(x1, y1)
        } else {
            val dur = (d * 0.8f).toLong().coerceIn(250L, 900L)
            swipe(x1, y1, x2, y2, dur)
        }
    }
}