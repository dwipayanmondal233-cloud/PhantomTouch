package com.example.phantomtouch

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#05060A"))
            setPadding(40, 40, 40, 40)
        }

        val title = TextView(this).apply {
            text = "PHANTOMTOUCH"
            textSize = 30f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#00F0FF"))
            setShadowLayer(8f, 3f, 0f, Color.parseColor("#B44CFF"))
            gravity = Gravity.CENTER
        }

        val sub = TextView(this).apply {
            text = "Milestone 1 OK\nThe app builds and runs."
            textSize = 16f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#CFEFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 0)
        }

        root.addView(title)
        root.addView(sub)
        setContentView(root)
    }
}