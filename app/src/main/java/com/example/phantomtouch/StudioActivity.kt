package com.example.phantomtouch

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class StudioCanvas(context: Context) : View(context) {

    var onStroke: ((List<Pt2>) -> Unit)? = null

    private val pts = ArrayList<Pt2>()
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#2A4A66")
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#00F0FF")
    }
    private val rect = RectF()

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pts.clear()
                pts.add(Pt2(e.x, e.y))
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                pts.add(Pt2(e.x, e.y))
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                pts.add(Pt2(e.x, e.y))
                invalidate()
                onStroke?.invoke(ArrayList(pts))
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#0A0E16"))
        rect.set(4f, 4f, width - 4f, height - 4f)
        canvas.drawRoundRect(rect, 28f, 28f, frame)
        for (i in 1 until pts.size) {
            canvas.drawLine(pts[i - 1].x, pts[i - 1].y, pts[i].x, pts[i].y, line)
        }
    }
}

class StudioActivity : ComponentActivity() {

    private var inPane = false
    private var paneStatus: TextView? = null

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun neon(text: String, stroke: String): Button {
        val b = Button(this)
        b.typeface = Typeface.MONOSPACE
        b.textSize = 12f
        b.setTextColor(Color.WHITE)
        b.isAllCaps = false
        b.text = text
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

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }

    private fun full(box: LinearLayout, b: Button) {
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
        lp.setMargins(0, dp(8), 0, 0)
        box.addView(b, lp)
    }

    private fun pair(box: LinearLayout, a: Button, b: Button) {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        val lp = LinearLayout.LayoutParams(0, dp(44), 1f)
        lp.setMargins(dp(2), dp(4), dp(2), 0)
        r.addView(a, lp)
        r.addView(b, LinearLayout.LayoutParams(lp))
        box.addView(r)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)
        GestureStore.init(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (inPane) {
                    showList()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        showList()
    }

    // ---------- Gesture list ----------

    private fun showList() {
        inPane = false
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setBackgroundColor(Color.parseColor("#05060A"))
        box.setPadding(dp(14), dp(24), dp(14), dp(24))

        val title = mono(24f, "#00F0FF")
        title.text = "GESTURE STUDIO"
        title.setShadowLayer(8f, 3f, 0f, Color.parseColor("#B44CFF"))
        box.addView(title)

        val info = mono(11f, "#7AA")
        info.setPadding(0, dp(6), 0, dp(4))
        info.text = "Draw a shape 3 times, then pick an action. Good shapes: circle, V, Z, S, " +
            "triangle, star, heart, check mark. The recognizer ignores rotation, so a line " +
            "left and a line right look the same. Avoid plain lines.\n" +
            "Air draw: pinch and hold, draw, release."
        box.addView(info)

        val newBtn = neon("+ NEW GESTURE", "#00F0FF")
        newBtn.setOnClickListener { newGesture() }
        full(box, newBtn)

        val testBtn = neon("TEST BY FINGER (nothing runs)", "#B44CFF")
        testBtn.setOnClickListener {
            if (GestureStore.all().isEmpty()) toast("Make a gesture first") else showPane(null, true)
        }
        full(box, testBtn)

        val modeBtn = neon("", "#FF2BD6")
        fun modeText() {
            modeBtn.text = "Air draw: " + (if (Settings.drawTest == 1) "TEST ONLY (safe)" else "RUN ACTIONS") +
                " (tap)"
        }
        modeText()
        modeBtn.setOnClickListener {
            Settings.drawTest = if (Settings.drawTest == 1) 0 else 1
            modeText()
        }
        full(box, modeBtn)

        if (GestureStore.recName.isNotEmpty()) {
            val rec = mono(12f, "#FF2BD6")
            rec.setPadding(0, dp(10), 0, 0)
            rec.text = "AIR REC TARGET: " + GestureStore.recName +
                "\nTurn on AIR DRAW in AIR TOUCH. Each air stroke becomes a sample."
            box.addView(rec)
            val stop = neon("STOP AIR REC", "#FF2BD6")
            stop.setOnClickListener {
                GestureStore.recName = ""
                showList()
            }
            full(box, stop)
        }

        val list = GestureStore.all()
        if (list.isEmpty()) {
            val none = mono(13f, "#CFEFFF")
            none.setPadding(0, dp(16), 0, 0)
            none.text = "No gestures yet."
            box.addView(none)
        }
        for (g in list) addCard(box, g)

        val sv = ScrollView(this)
        sv.setBackgroundColor(Color.parseColor("#05060A"))
        sv.addView(box)
        setContentView(sv)
    }

    private fun addCard(box: LinearLayout, g: Gesture) {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable()
        bg.cornerRadius = dp(14).toFloat()
        bg.setColor(Color.parseColor("#0D1018"))
        bg.setStroke(dp(1), Color.parseColor("#1C2A3A"))
        c.background = bg
        c.setPadding(dp(10), dp(10), dp(10), dp(8))

        val name = mono(17f, "#00F0FF")
        name.text = g.name
        c.addView(name)

        val st = mono(12f, "#CFEFFF")
        st.text = "Samples: " + g.samples.size + " / 3\nAction: " + GestureStore.actionLabel(this, g.action)
        c.addView(st)

        val warns = ArrayList<String>()
        if (g.samples.size < 3) warns.add("Needs 3 samples.")
        if (g.samples.size >= 2 && GestureStore.consistency(g) < 0.75f) {
            warns.add("Your samples look different. Undo or re-record.")
        }
        if (g.samples.isNotEmpty()) {
            val cf = GestureStore.conflict(g)
            if (cf != null) warns.add(cf)
        }
        val sa = GestureStore.sameAction(g)
        if (sa != null) warns.add("Same action as \"" + sa + "\".")
        if (g.action.isEmpty()) warns.add("No action yet.")
        if (warns.isNotEmpty()) {
            val w = mono(12f, "#FF2BD6")
            w.setPadding(0, dp(4), 0, 0)
            w.text = "⚠ " + warns.joinToString("\n⚠ ")
            c.addView(w)
        }

        val b1 = neon("DRAW SAMPLES", "#00F0FF")
        b1.setOnClickListener { showPane(g, false) }
        val b2 = neon("AIR REC", "#B44CFF")
        b2.setOnClickListener {
            GestureStore.recName = g.name
            toast("Now turn on AIR DRAW and draw in the air")
            showList()
        }
        pair(c, b1, b2)

        val b3 = neon("ACTION", "#B44CFF")
        b3.setOnClickListener { pickAction(g) }
        val b4 = neon("DELETE", "#FF2BD6")
        b4.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete " + g.name + "?")
                .setPositiveButton("Delete") { _, _ ->
                    GestureStore.delete(g)
                    showList()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        pair(c, b3, b4)

        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, dp(12), 0, 0)
        box.addView(c, lp)
    }

    private fun newGesture() {
        if (GestureStore.all().size >= 12) {
            toast("Max 12 gestures")
            return
        }
        val et = EditText(this)
        et.typeface = Typeface.MONOSPACE
        et.hint = "name, for example Circle"
        et.setSingleLine(true)
        AlertDialog.Builder(this)
            .setTitle("New gesture")
            .setView(et)
            .setPositiveButton("OK") { _, _ ->
                val n = et.text.toString().trim()
                if (n.isEmpty() || n.length > 20) {
                    toast("Use 1 to 20 characters")
                } else if (GestureStore.find(n) != null) {
                    toast("Name already used")
                } else {
                    val g = GestureStore.add(n)
                    showPane(g, false)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickAction(g: Gesture) {
        val labels = ArrayList<String>()
        val ids = ArrayList<String>()
        labels.add("None")
        ids.add("")
        for (d in WheelItems.all) {
            if (d.id == "draw") continue
            labels.add(d.full)
            ids.add(d.id)
        }
        val i = Intent(Intent.ACTION_MAIN)
        i.addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val apps = packageManager.queryIntentActivities(i, 0)
        val seen = HashSet<String>()
        val found = ArrayList<Pair<String, String>>()
        for (ri in apps) {
            val pkg = ri.activityInfo.packageName
            if (pkg == packageName || !seen.add(pkg)) continue
            found.add(Pair(ri.loadLabel(packageManager).toString(), pkg))
        }
        found.sortBy { it.first.lowercase() }
        for (a in found) {
            labels.add("Open " + a.first)
            ids.add("app:" + a.second)
        }
        AlertDialog.Builder(this)
            .setTitle("Action for " + g.name)
            .setItems(labels.toTypedArray()) { _, which ->
                g.action = ids[which]
                GestureStore.save()
                showList()
            }
            .show()
    }

    // ---------- Drawing pane (finger) ----------

    private fun showPane(g: Gesture?, test: Boolean) {
        inPane = true
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setBackgroundColor(Color.parseColor("#05060A"))
        col.setPadding(dp(12), dp(20), dp(12), dp(12))

        val title = mono(18f, "#00F0FF")
        title.text = if (test) "TEST BY FINGER" else "DRAW: " + (g?.name ?: "")
        col.addView(title)

        val status = mono(12f, "#CFEFFF")
        status.setPadding(0, dp(4), 0, dp(6))
        status.text = if (test) {
            "Draw a shape. Nothing is triggered."
        } else {
            "Draw the shape 3 times, one stroke each. Samples: " + (g?.samples?.size ?: 0)
        }
        paneStatus = status
        col.addView(status)

        val canvas = StudioCanvas(this)
        canvas.onStroke = { pts -> handleStroke(pts, g, test) }
        col.addView(
            canvas,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val lp = LinearLayout.LayoutParams(0, dp(50), 1f)
        lp.setMargins(dp(2), dp(8), dp(2), 0)
        if (!test && g != null) {
            val undo = neon("UNDO LAST", "#B44CFF")
            undo.setOnClickListener {
                GestureStore.undo(g)
                status.text = "Undone. Samples: " + g.samples.size
            }
            row.addView(undo, LinearLayout.LayoutParams(lp))
        }
        val done = neon("DONE", "#00F0FF")
        done.setOnClickListener { showList() }
        row.addView(done, LinearLayout.LayoutParams(lp))
        col.addView(row)

        setContentView(col)
    }

    private fun handleStroke(pts: List<Pt2>, g: Gesture?, test: Boolean) {
        val st = paneStatus ?: return
        if (test) {
            val n = Recognizer.normalize(pts)
            if (n == null) {
                st.text = "Too short. Draw bigger."
                return
            }
            val m = Recognizer.recognize(n, GestureStore.all())
            if (m == null) {
                st.text = "No samples saved yet."
                return
            }
            val pct = (m.score * 100).toInt()
            val ok = m.score >= Recognizer.ACCEPT && m.score - m.second >= 0.03f
            st.text = m.name + "  " + pct + "%  " + (if (ok) "✓ would run" else "✕ not sure")
        } else if (g != null) {
            val ok = GestureStore.addSample(g, pts)
            st.text = if (ok) {
                "Saved. Samples: " + g.samples.size + " (6 max). Draw more or press DONE."
            } else {
                "Too short. Draw bigger."
            }
        }
    }
}