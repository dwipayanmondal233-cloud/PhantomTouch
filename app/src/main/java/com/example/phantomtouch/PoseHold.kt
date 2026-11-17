package com.example.phantomtouch

class PoseHold {

    private var since = 0L
    private var cool = 0L

    // Returns true once after the pose is held for 1 second.
    fun update(on: Boolean, now: Long): Boolean {
        if (!on) {
            since = 0L
            return false
        }
        if (since == 0L) since = now
        if (now - since >= 1000L && now >= cool) {
            since = 0L
            cool = now + 2500L
            return true
        }
        return false
    }

    fun reset() {
        since = 0L
    }
}