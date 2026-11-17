package com.example.phantomtouch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class DashboardActivity : ComponentActivity() {

    companion object {
        const val SITE = "https://dwipayanmondal233-cloud.github.io/PhantomTouch/"
        const val APK =
            "https://github.com/dwipayanmondal233-cloud/PhantomTouch/releases/latest/download/PhantomTouch.apk"
    }

    private lateinit var statusBox: LinearLayout
    private lateinit var startBtn: Button
    private val handler = Handler(Looper.getMainLooper())

    private val camLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun mono(size: Float, color: String): TextView {
        val t = TextView(this)
        t.typeface = Typeface.MONOSPACE
        t.textSize = size
        t.setTextColor(Color.parseColor(color))
        return t
    }

    private fun neon(text: String, stroke: String, action: () -> Unit): Button {
        val b = Button(this)
        b.typeface = Typeface.MONOSPACE
        b.textSize = 12f
        b.isAllCaps = false
        b.setTextColor(Color.WHITE)
        b.text = text
        val bg = GradientDrawable()
        bg.cornerRadius = dp(14).toFloat()
        bg.setColor(Color.parseColor("#10202E"))
        bg.setStroke(dp(2), Color.parseColor(stroke))
        b.background = bg
        b.setOnClickListener { action() }
        return b
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable()
        bg.cornerRadius = dp(16).toFloat()
        bg.setColor(Color.parseColor("#0D1018"))
        bg.setStroke(dp(1), Color.parseColor("#1C2A3A"))
        c.background = bg
        c.setPadding(dp(12), dp(10), dp(12), dp(10))
        return c
    }

    private fun addCard(box: LinearLayout, c: LinearLayout) {
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, dp(12), 0, 0)
        box.addView(c, lp)
    }

    private fun pair(box: LinearLayout, a: Button, b: Button) {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        val lp = LinearLayout.LayoutParams(0, dp(54), 1f)
        lp.setMargins(dp(3), dp(6), dp(3), 0)
        r.addView(a, lp)
        r.addView(b, LinearLayout.LayoutParams(lp))
        box.addView(r)
    }

    private fun hasCam(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun canOverlay(): Boolean = android.provider.Settings.canDrawOverlays(this)

    private fun hasNotif(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }

    private fun camFree(): Boolean {
        if (HandService.running) {
            toast("Stop Air Touch first. It uses the camera.")
            return false
        }
        return true
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun share() {
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(
            Intent.EXTRA_TEXT,
            "PhantomTouch: control your Android with hand gestures.\n$SITE"
        )
        startActivity(Intent.createChooser(i, "Share PhantomTouch"))
    }

    private fun toggleService() {
        if (HandService.running) {
            stopService(Intent(this, HandService::class.java))
        } else if (!hasCam() || !canOverlay()) {
            toast("Fix the ✕ rows first (Camera and Overlay).")
        } else {
            ContextCompat.startForegroundService(this, Intent(this, HandService::class.java))
        }
        handler.postDelayed({ refresh() }, 700)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setBackgroundColor(Color.parseColor("#05060A"))
        box.setPadding(dp(14), dp(28), dp(14), dp(24))

        val title = mono(28f, "#00F0FF")
        title.text = "PHANTOMTOUCH"
        title.setShadowLayer(8f, 3f, 0f, Color.parseColor("#B44CFF"))
        box.addView(title)

        val tag = mono(12f, "#77AAAA")
        tag.text = "v1.0  |  your hands are the controller"
        tag.setPadding(0, dp(2), 0, 0)
        box.addView(tag)

        val sc = card()
        val sh = mono(13f, "#B44CFF")
        sh.text = "STATUS (tap a ✕ row to fix it)"
        sc.addView(sh)
        statusBox = LinearLayout(this)
        statusBox.orientation = LinearLayout.VERTICAL
        sc.addView(statusBox)
        addCard(box, sc)

        startBtn = neon("START AIR TOUCH", "#FF2BD6") { toggleService() }
        startBtn.textSize = 16f
        val slp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(60))
        slp.setMargins(0, dp(12), 0, 0)
        box.addView(startBtn, slp)

        val grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        pair(
            grid,
            neon("AIR TOUCH\nall controls", "#00F0FF") {
                startActivity(Intent(this, ControlActivity::class.java))
            },
            neon("PRACTICE\nsafe coach", "#B44CFF") {
                if (camFree()) startActivity(Intent(this, PracticeActivity::class.java))
            }
        )
        pair(
            grid,
            neon("GESTURE STUDIO\nrecord + assign", "#B44CFF") {
                startActivity(Intent(this, StudioActivity::class.java))
            },
            neon("EDIT WHEEL\nslices + apps", "#00F0FF") {
                startActivity(Intent(this, WheelActivity::class.java))
            }
        )
        pair(
            grid,
            neon("CALIBRATE\nair mouse test", "#00F0FF") {
                if (camFree()) startActivity(Intent(this, AirMouseActivity::class.java))
            },
            neon("TRACKING TEST\nskeleton", "#B44CFF") {
                if (camFree()) startActivity(Intent(this, MainActivity::class.java))
            }
        )
        pair(
            grid,
            neon("APP SETTINGS\nbattery, restricted", "#B44CFF") { openAppSettings() },
            neon("SHARE\napp link", "#00F0FF") { share() }
        )
        box.addView(grid)

        val hc = card()
        val help = mono(11f, "#77AAAA")
        help.text = "FIRST TIME\n" +
            "1. Fix every ✕ row above. Camera, Overlay and Accessibility are required.\n" +
            "2. Set Battery to Unrestricted (APP SETTINGS).\n" +
            "3. Run CALIBRATE once. Use PRACTICE to learn the gestures.\n" +
            "4. Press START AIR TOUCH while this app is open.\n\n" +
            "GESTURES\n" +
            "Pinch = tap. Pinch twice = double tap. Pinch and hold, move = drag.\n" +
            "Fist = pause. Fist 1.2 s = recents.\n" +
            "Peace sign + move up or down = scroll.\n" +
            "Open hand flick left = back. Flick up = home.\n" +
            "3 fingers 1 s = keyboard. Rock sign 1 s = wheel.\n\n" +
            "PROBLEMS AND FIXES\n" +
            "- Play Protect App blocked: Play Store, profile, Play Protect, gear, turn off scanning, install, turn it back on.\n" +
            "- Accessibility greyed out: Settings, Apps, PhantomTouch, menu, Allow restricted settings.\n" +
            "- After an update, turn the Accessibility service off and on.\n" +
            "- App not installed when updating: uninstall the old version first.\n" +
            "- Other overlay apps can block permission popups. Stop them first.\n" +
            "- Camera stops with the screen off. Battery must be Unrestricted.\n\n" +
            "PRIVACY\n" +
            "Everything runs on this device. No video or audio is saved or sent. " +
            "This app has no internet permission."
        hc.addView(help)
        addCard(box, hc)

        val sv = ScrollView(this)
        sv.setBackgroundColor(Color.parseColor("#05060A"))
        sv.addView(box)
        setContentView(sv)
    }

    private fun row(label: String, ok: Boolean, optional: Boolean, fix: (() -> Unit)?) {
        val t = mono(14f, if (ok) "#88FFBB" else if (optional) "#77AAAA" else "#FF2BD6")
        t.text = (if (ok) "✓ " else "✕ ") + label +
            (if (optional) " (optional)" else "") +
            (if (!ok && fix != null) "  <- tap" else "")
        t.setPadding(0, dp(8), 0, dp(8))
        if (!ok && fix != null) t.setOnClickListener { fix() }
        statusBox.addView(t)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        statusBox.removeAllViews()
        row("Camera", hasCam(), false) {
            camLauncher.launch(Manifest.permission.CAMERA)
        }
        row("Overlay", canOverlay(), false) {
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        row("Accessibility", PhantomAccess.instance != null, false) {
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        row("Notifications", hasNotif(), false) {
            if (Build.VERSION.SDK_INT >= 33) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        row("Brightness control", android.provider.Settings.System.canWrite(this), true) {
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        }
        row("Microphone", hasMic(), true) {
            startActivity(Intent(this, ControlActivity::class.java))
        }
        val t = mono(14f, if (HandService.running) "#00F0FF" else "#77AAAA")
        t.text = if (HandService.running) "● Service: RUNNING" else "○ Service: stopped"
        t.setPadding(0, dp(8), 0, 0)
        statusBox.addView(t)
        startBtn.text = if (HandService.running) "STOP AIR TOUCH" else "START AIR TOUCH"
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}