package com.example.phantomtouch

import android.content.Context
import android.content.SharedPreferences

object Settings {

    private var sp: SharedPreferences? = null

    fun init(ctx: Context) {
        if (sp == null) {
            sp = ctx.applicationContext.getSharedPreferences("phantomtouch", Context.MODE_PRIVATE)
        }
    }

    private fun f(k: String, d: Float): Float = sp?.getFloat(k, d) ?: d
    private fun i(k: String, d: Int): Int = sp?.getInt(k, d) ?: d
    private fun putF(k: String, v: Float) {
        sp?.edit()?.putFloat(k, v)?.apply()
    }
    private fun putI(k: String, v: Int) {
        sp?.edit()?.putInt(k, v)?.apply()
    }

    var sens: Float
        get() = f("sens", 1.0f)
        set(v) = putF("sens", v)

    var minCutoff: Float
        get() = f("minCutoff", 1.5f)
        set(v) = putF("minCutoff", v)

    var beta: Float
        get() = f("beta", 4f)
        set(v) = putF("beta", v)

    var pinchOn: Float
        get() = f("pinchOn", 0.25f)
        set(v) = putF("pinchOn", v)

    var pinchOff: Float
        get() = f("pinchOff", 0.40f)
        set(v) = putF("pinchOff", v)

    var dead: Float
        get() = f("dead", 6f)
        set(v) = putF("dead", v)

    var hold: Int
        get() = i("hold", 350)
        set(v) = putI("hold", v)

    var point: Int
        get() = i("point", 8)
        set(v) = putI("point", v)

    var calCx: Float
        get() = f("calCx", 0.5f)
        set(v) = putF("calCx", v)

    var calCy: Float
        get() = f("calCy", 0.5f)
        set(v) = putF("calCy", v)

    var calHw: Float
        get() = f("calHw", 0.28f)
        set(v) = putF("calHw", v)

    var calHh: Float
        get() = f("calHh", 0.28f)
        set(v) = putF("calHh", v)

    var skin: Int
        get() = i("skin", 0)
        set(v) = putI("skin", v)

    fun resetAll() {
        sp?.edit()?.clear()?.apply()
    }
}
