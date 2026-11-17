package com.example.phantomtouch

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Gesture(var name: String, var action: String, val samples: ArrayList<List<Pt2>>)

object GestureStore {

    @Volatile
    var recName: String = ""

    private var file: File? = null
    private var list: ArrayList<Gesture>? = null

    fun init(ctx: Context) {
        if (file == null) {
            file = File(ctx.applicationContext.filesDir, "gestures.json")
        }
    }

    fun all(): ArrayList<Gesture> {
        val cur = list
        if (cur != null) return cur
        val loaded = load()
        list = loaded
        return loaded
    }

    private fun load(): ArrayList<Gesture> {
        val out = ArrayList<Gesture>()
        try {
            val f = file
            if (f != null && f.exists()) {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val g = Gesture(o.getString("name"), o.optString("action", ""), ArrayList())
                    val ss = o.getJSONArray("samples")
                    for (j in 0 until ss.length()) {
                        val a = ss.getJSONArray(j)
                        val pts = ArrayList<Pt2>()
                        var k = 0
                        while (k + 1 < a.length()) {
                            pts.add(Pt2(a.getDouble(k).toFloat(), a.getDouble(k + 1).toFloat()))
                            k += 2
                        }
                        if (pts.size == Recognizer.N) g.samples.add(pts)
                    }
                    out.add(g)
                }
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun save() {
        try {
            val arr = JSONArray()
            for (g in all()) {
                val o = JSONObject()
                o.put("name", g.name)
                o.put("action", g.action)
                val ss = JSONArray()
                for (s in g.samples) {
                    val a = JSONArray()
                    for (p in s) {
                        a.put(Math.round(p.x * 10f) / 10.0)
                        a.put(Math.round(p.y * 10f) / 10.0)
                    }
                    ss.put(a)
                }
                o.put("samples", ss)
                arr.put(o)
            }
            file?.writeText(arr.toString())
        } catch (e: Exception) {
        }
    }

    fun find(name: String): Gesture? {
        for (g in all()) {
            if (g.name.equals(name, ignoreCase = true)) return g
        }
        return null
    }

    fun add(name: String): Gesture {
        val g = Gesture(name, "", ArrayList())
        all().add(g)
        save()
        return g
    }

    fun delete(g: Gesture) {
        all().remove(g)
        if (recName.equals(g.name, ignoreCase = true)) recName = ""
        save()
    }

    fun addSample(g: Gesture, raw: List<Pt2>): Boolean {
        val n = Recognizer.normalize(raw) ?: return false
        if (g.samples.size >= 6) g.samples.removeAt(0)
        g.samples.add(n)
        save()
        return true
    }

    fun undo(g: Gesture) {
        if (g.samples.isNotEmpty()) {
            g.samples.removeAt(g.samples.size - 1)
            save()
        }
    }

    fun actionLabel(ctx: Context, id: String): String {
        if (id.isEmpty()) return "none"
        for (d in WheelItems.all) {
            if (d.id == id) return d.full
        }
        if (id.startsWith("app:")) return "Open " + WheelItems.appLabel(ctx, id.substring(4))
        return id
    }

    // Lowest similarity between the gesture's own samples (1 if fewer than 2).
    fun consistency(g: Gesture): Float {
        var low = 1f
        for (i in g.samples.indices) {
            for (j in i + 1 until g.samples.size) {
                val s = Recognizer.similarity(g.samples[i], g.samples[j])
                if (s < low) low = s
            }
        }
        return low
    }

    // Most similar other gesture, or null if none is close.
    fun conflict(g: Gesture): String? {
        var best = 0f
        var bestName = ""
        for (o in all()) {
            if (o === g) continue
            for (a in g.samples) {
                for (b in o.samples) {
                    val s = Recognizer.similarity(a, b)
                    if (s > best) {
                        best = s
                        bestName = o.name
                    }
                }
            }
        }
        return if (best >= 0.80f) "Too similar to \"" + bestName + "\" (" + (best * 100).toInt() + "%)" else null
    }

    fun sameAction(g: Gesture): String? {
        if (g.action.isEmpty()) return null
        for (o in all()) {
            if (o !== g && o.action == g.action) return o.name
        }
        return null
    }
}