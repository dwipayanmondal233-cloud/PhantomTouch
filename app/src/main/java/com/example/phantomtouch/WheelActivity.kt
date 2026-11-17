package com.example.phantomtouch

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class WheelActivity : ComponentActivity() {

    private lateinit var box: LinearLayout
    private var apps: List<Pair<String, String>> = emptyList()
    private var note = ""

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun neon(text: String, stroke: String): Button {
        val b = Button(this)
        b.typeface = Typeface.MONOSPACE
        b.textSize = 13f
        b.setTextColor(Color.WHITE)
        b.text = text
        b.isAllCaps = false
        val bg = GradientDrawable()
        bg.cornerRadius = dp(14).toFloat()
        bg.setColor(Color.parseColor("#10202E"))
        bg.setStroke(dp(2), Color.parseColor(stroke))
        b.background = bg
        return b
    }

    private fun mono(size: Float, color: String): TextView {
        val t = TextView(this)
        t.typeface = Typeface.MONOSPACE
        t.textSize = size
        t.setTextColor(Color.parseColor(color))
        return t
    }

    private fun loadApps() {
        val i = Intent(Intent.ACTION_MAIN)
        i.addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val list = packageManager.queryIntentActivities(i, 0)
        val out = ArrayList<Pair<String, String>>()
        val seen = HashSet<String>()
        for (ri in list) {
            val pkg = ri.activityInfo.packageName
            if (pkg == packageName || !seen.add(pkg)) continue
            out.add(Pair(ri.loadLabel(packageManager).toString(), pkg))
        }
        out.sortBy { it.first.lowercase() }
        apps = out
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)
        loadApps()

        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setBackgroundColor(Color.parseColor("#05060A"))
        box.setPadding(dp(14), dp(24), dp(14), dp(24))

        val sv = ScrollView(this)
        sv.setBackgroundColor(Color.parseColor("#05060A"))
        sv.addView(box)
        setContentView(sv)
        build()
    }

    private fun toggle(id: String) {
        val cur = WheelItems.ids().toMutableList()
        if (cur.contains(id)) {
            cur.remove(id)
            note = ""
        } else if (cur.size >= WheelItems.MAX) {
            note = "Max " + WheelItems.MAX + " slices. Remove one first."
        } else {
            cur.add(id)
            note = ""
        }
        WheelItems.save(cur)
        build()
    }

    private fun row(label: String, id: String) {
        val on = WheelItems.ids().contains(id)
        val b = neon((if (on) "✓ " else "   ") + label, if (on) "#00F0FF" else "#2A4A66")
        b.setOnClickListener { toggle(id) }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46))
        lp.setMargins(0, dp(5), 0, 0)
        box.addView(b, lp)
    }

    private fun build() {
        box.removeAllViews()

        val title = mono(24f, "#00F0FF")
        title.text = "EDIT WHEEL"
        title.setShadowLayer(8f, 3f, 0f, Color.parseColor("#B44CFF"))
        box.addView(title)

        val ids = WheelItems.ids()
        val info = mono(12f, "#CFEFFF")
        info.setPadding(0, dp(8), 0, 0)
        val names = ArrayList<String>()
        for (id in ids) names.add(WheelItems.shortLabel(this, id))
        info.text = "Slices: " + ids.size + " / " + WheelItems.MAX + "\nOrder (clockwise from top):\n" +
            names.joinToString("  ")
        box.addView(info)

        if (note.isNotEmpty()) {
            val n = mono(13f, "#FF2BD6")
            n.setPadding(0, dp(6), 0, 0)
            n.text = note
            box.addView(n)
        }

        val h1 = mono(13f, "#B44CFF")
        h1.setPadding(0, dp(14), 0, 0)
        h1.text = "ACTIONS (tap to add or remove)"
        box.addView(h1)
        for (d in WheelItems.all) row(d.full, d.id)

        val h2 = mono(13f, "#B44CFF")
        h2.setPadding(0, dp(14), 0, 0)
        h2.text = "YOUR APPS (tap to add or remove)"
        box.addView(h2)
        for (a in apps) row(a.first, "app:" + a.second)

        val rs = neon("RESET TO DEFAULT", "#FF2BD6")
        rs.setOnClickListener {
            WheelItems.save(WheelItems.DEFAULT.split(","))
            note = ""
            build()
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50))
        lp.setMargins(0, dp(16), 0, 0)
        box.addView(rs, lp)
    }
}