package com.example.phantomtouch

import android.content.Context

class WheelDef(val id: String, val full: String, val short: String)

object WheelItems {

    const val MAX = 8
    const val DEFAULT = "home,back,recents,shot,volup,voldn,music,kb"

    val all: List<WheelDef> = listOf(
        WheelDef("home", "Home", "HOME"),
        WheelDef("back", "Back", "BACK"),
        WheelDef("recents", "Recents", "RECENT"),
        WheelDef("shot", "Screenshot", "SHOT"),
        WheelDef("volup", "Volume up", "VOL+"),
        WheelDef("voldn", "Volume down", "VOL-"),
        WheelDef("brup", "Brightness up", "BRI+"),
        WheelDef("brdn", "Brightness down", "BRI-"),
        WheelDef("music", "Play / Pause", "PLAY"),
        WheelDef("next", "Next track", "NEXT"),
        WheelDef("kb", "Keyboard", "KEYS")
    )

    fun ids(): List<String> =
        Settings.wheel.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(MAX)

    fun save(list: List<String>) {
        Settings.wheel = list.joinToString(",")
    }

    fun appLabel(ctx: Context, pkg: String): String {
        return try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    fun shortLabel(ctx: Context, id: String): String {
        for (d in all) {
            if (d.id == id) return d.short
        }
        if (id.startsWith("app:")) {
            val n = appLabel(ctx, id.substring(4)).uppercase()
            return if (n.length > 6) n.substring(0, 6) else n
        }
        return "?"
    }
}