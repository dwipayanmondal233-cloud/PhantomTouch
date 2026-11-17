package com.example.phantomtouch

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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

    // fingerUp = true means the finger swipes up (content moves up).
    fun scroll(fingerUp: Boolean, sw: Float, sh: Float) {
        val x = sw / 2f
        val from = if (fingerUp) sh * 0.65f else sh * 0.35f
        val to = if (fingerUp) sh * 0.35f else sh * 0.65f
        swipe(x, from, x, to, 250)
    }

    fun back() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun home() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun recents() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    // ---------- Typing ----------
    // Result codes: 0 = ok, 1 = no editable field focused, 2 = password field (blocked)

    private fun inputNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    }

    private fun textOf(n: AccessibilityNodeInfo): String {
        if (n.isShowingHintText) return ""
        return n.text?.toString() ?: ""
    }

    private fun applyText(n: AccessibilityNodeInfo, out: String, pos: Int): Boolean {
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, out)
        val ok = n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (ok) {
            val sel = Bundle()
            sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, pos)
            sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, pos)
            n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
        }
        return ok
    }

    fun typeText(s: String): Int {
        val n = inputNode() ?: return 1
        if (n.isPassword) return 2
        if (!n.isEditable) return 1
        val cur = textOf(n)
        var a = n.textSelectionStart
        var b = n.textSelectionEnd
        if (a < 0 || b < 0) {
            a = cur.length
            b = cur.length
        }
        val lo = minOf(a, b).coerceIn(0, cur.length)
        val hi = maxOf(a, b).coerceIn(0, cur.length)
        val out = cur.substring(0, lo) + s + cur.substring(hi)
        return if (applyText(n, out, lo + s.length)) 0 else 1
    }

    fun backspace(): Int {
        val n = inputNode() ?: return 1
        if (n.isPassword) return 2
        if (!n.isEditable) return 1
        val cur = textOf(n)
        var a = n.textSelectionStart
        var b = n.textSelectionEnd
        if (a < 0 || b < 0) {
            a = cur.length
            b = cur.length
        }
        val lo = minOf(a, b).coerceIn(0, cur.length)
        val hi = maxOf(a, b).coerceIn(0, cur.length)
        val out: String
        val pos: Int
        if (hi > lo) {
            out = cur.substring(0, lo) + cur.substring(hi)
            pos = lo
        } else if (lo > 0) {
            out = cur.substring(0, lo - 1) + cur.substring(lo)
            pos = lo - 1
        } else {
            return 0
        }
        return if (applyText(n, out, pos)) 0 else 1
    }

    fun enter(): Int {
        val n = inputNode() ?: return 1
        if (n.isPassword) return 2
        if (Build.VERSION.SDK_INT >= 30) {
            val id = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
            if (n.performAction(id)) return 0
        }
        if (n.isMultiLine) return typeText("\n")
        return 0
    }
}