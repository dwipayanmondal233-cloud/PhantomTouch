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
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class ControlActivity : ComponentActivity() {

    private lateinit var permText: TextView
    private lateinit var startBtn: Button
    private lateinit var skinBtn: Button
    private lateinit var sizeBtn: Button
    private lateinit var hudBtn: Button
    private lateinit var voiceBtn: Button
    private val handler = Handler(Looper.getMainLooper())

    private val camLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    private val micLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun neon(text: String, stroke: String): Button {
        val b = Button(this)
        b.typeface = Typeface.MONOSPACE
        b.textSize = 13f
        b.setTextColor(Color.WHITE)
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

    private fun add(box: LinearLayout, b: Button) {
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50))
        lp.setMargins(0, dp(6), 0, 0)
        box.addView(b, lp)
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

    private fun voiceEngine(): Boolean =
        Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)

    private fun poke(action: String) {
        if (!HandService.running) {
            Toast.makeText(this, "Start Air Touch first", Toast.LENGTH_SHORT).show()
            return
        }
        startService(Intent(this, HandService::class.java).setAction(action))
    }

    private fun sizeName(): String = when (Settings.kbSize) {
        0 -> "SMALL"
        2 -> "LARGE"
        else -> "MEDIUM"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setBackgroundColor(Color.parseColor("#05060A"))
        box.setPadding(dp(14), dp(24), dp(14), dp(20))

        val title = mono(24f, "#00F0FF")
        title.text = "AIR TOUCH"
        title.setShadowLayer(8f, 3f, 0f, Color.parseColor("#B44CFF"))
        box.addView(title)

        permText = mono(14f, "#CFEFFF")
        permText.setPadding(0, dp(8), 0, dp(4))
        box.addView(permText)

        val b1 = neon("1. GRANT CAMERA", "#00F0FF")
        b1.setOnClickListener { camLauncher.launch(Manifest.permission.CAMERA) }
        add(box, b1)

        val b2 = neon("2. ALLOW OVERLAY", "#B44CFF")
        b2.setOnClickListener {
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        add(box, b2)

        val b3 = neon("3. OPEN ACCESSIBILITY", "#00F0FF")
        b3.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        add(box, b3)

        val b4 = neon("4. ALLOW NOTIFICATIONS", "#B44CFF")
        b4.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        add(box, b4)

        val b5 = neon("APP SETTINGS (battery, restricted)", "#00F0FF")
        b5.setOnClickListener {
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        }
        add(box, b5)

        val practiceBtn = neon("PRACTICE MODE (safe coach)", "#FF2BD6")
        practiceBtn.setOnClickListener {
            if (HandService.running) {
                Toast.makeText(this, "Stop Air Touch first", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, PracticeActivity::class.java))
            }
        }
        add(box, practiceBtn)

        skinBtn = neon("", "#B44CFF")
        skinBtn.setOnClickListener {
            Settings.skin = if (Settings.skin == 0) 1 else 0
            refresh()
        }
        add(box, skinBtn)

        hudBtn = neon("", "#00F0FF")
        hudBtn.setOnClickListener {
            Settings.hud = if (Settings.hud == 1) 0 else 1
            if (HandService.running) {
                startService(Intent(this, HandService::class.java).setAction(HandService.ACT_HUD))
            }
            refresh()
        }
        add(box, hudBtn)

        val navBtn = neon("", "#00F0FF")
        fun navText() {
            navBtn.text = "Nav gestures: " + (if (Settings.navOn == 1) "ON" else "OFF")
        }
        navText()
        navBtn.setOnClickListener {
            Settings.navOn = if (Settings.navOn == 1) 0 else 1
            navText()
        }
        add(box, navBtn)

        val invBtn = neon("", "#B44CFF")
        fun invText() {
            invBtn.text = "Scroll direction: " + (if (Settings.invert == 1) "INVERTED" else "NORMAL")
        }
        invText()
        invBtn.setOnClickListener {
            Settings.invert = if (Settings.invert == 1) 0 else 1
            invText()
        }
        add(box, invBtn)

        val kbBtn = neon("KEYBOARD: SHOW / HIDE", "#00F0FF")
        kbBtn.setOnClickListener { poke(HandService.ACT_KB) }
        add(box, kbBtn)

        sizeBtn = neon("", "#B44CFF")
        sizeBtn.setOnClickListener {
            Settings.kbSize = (Settings.kbSize + 1) % 3
            refresh()
            if (HandService.running) {
                startService(
                    Intent(this, HandService::class.java).setAction(HandService.ACT_KBSIZE)
                )
            }
        }
        add(box, sizeBtn)

        val airBtn = neon("AIR TYPING (EXPERIMENT) ON / OFF", "#FF2BD6")
        airBtn.setOnClickListener { poke(HandService.ACT_AIR) }
        add(box, airBtn)

        val wheelBtn = neon("WHEEL: SHOW / HIDE", "#00F0FF")
        wheelBtn.setOnClickListener { poke(HandService.ACT_WHEEL) }
        add(box, wheelBtn)

        val wheelEdit = neon("EDIT WHEEL (slices and apps)", "#B44CFF")
        wheelEdit.setOnClickListener {
            startActivity(Intent(this, WheelActivity::class.java))
        }
        add(box, wheelEdit)

        val wsBtn = neon("ALLOW BRIGHTNESS (change settings)", "#00F0FF")
        wsBtn.setOnClickListener {
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        }
        add(box, wsBtn)

        val studioBtn = neon("GESTURE STUDIO (record + assign)", "#B44CFF")
        studioBtn.setOnClickListener {
            startActivity(Intent(this, StudioActivity::class.java))
        }
        add(box, studioBtn)

        val drawBtn = neon("AIR DRAW: ON / OFF", "#FF2BD6")
        drawBtn.setOnClickListener { poke(HandService.ACT_DRAW) }
        add(box, drawBtn)

        val micBtn = neon("ALLOW MICROPHONE (voice, optional)", "#00F0FF")
        micBtn.setOnClickListener { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
        add(box, micBtn)

        voiceBtn = neon("", "#B44CFF")
        voiceBtn.setOnClickListener {
            Settings.voiceOn = if (Settings.voiceOn == 1) 0 else 1
            if (Settings.voiceOn == 1 && !hasMic()) {
                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            refresh()
        }
        add(box, voiceBtn)

        startBtn = neon("START AIR TOUCH", "#FF2BD6")
        startBtn.setOnClickListener {
            if (HandService.running) {
                stopService(Intent(this, HandService::class.java))
            } else if (!hasCam() || !canOverlay()) {
                permText.text = permText.text.toString() + "\nGrant Camera and Overlay first."
            } else {
                ContextCompat.startForegroundService(this, Intent(this, HandService::class.java))
            }
            handler.postDelayed({ refresh() }, 700)
        }
        add(box, startBtn)

        val help = mono(11f, "#7AA")
        help.setPadding(0, dp(14), 0, 0)
        help.text = "Pinch = tap. Two pinches = double tap. Pinch and hold, then move = drag " +
            "(it happens when you let go). Pinch and hold without moving = long press. " +
            "Fist = pause.\n\n" +
            "Scroll: peace sign (index + middle up), move hand up or down.\n" +
            "Back: flick open hand left, fast.\n" +
            "Home: flick open hand up, fast.\n" +
            "Recents: hold a fist for 1.2 seconds.\n\n" +
            "Keyboard: hold 3 fingers up (index, middle, ring) for 1 second. " +
            "First tap a text field with a pinch, then pinch the keys. " +
            "Shift key: once = one capital, twice = CAPS, three times = off. " +
            "Never types into password fields. The same 3-finger hold also closes " +
            "the keyboard or air typing.\n\n" +
            "Air typing is an EXPERIMENT. No keyboard is drawn. Your hand area is the " +
            "keyboard: top = numbers, bottom = space. Watch the small box at the top. " +
            "It is not accurate. When you turn it off it shows how many letters you kept.\n\n" +
            "Wheel: hold the ROCK sign (index and pinky up, middle and ring down) " +
            "for 1 second. Move to a slice and pinch. Pinch the middle or outside to close. " +
            "Volume and brightness slices stay open so you can pinch again. " +
            "Change slices and apps with EDIT WHEEL.\n\n" +
            "Air Draw: open GESTURE STUDIO and make gestures first. Turn on AIR DRAW " +
            "(button or DRAW slice). Pinch and hold until the monkey slams, draw in the air, " +
            "then release. Test mode only shows the name. ROCK sign = exit.\n\n" +
            "HUD: the small box at the top left shows tracking, mode and the last gesture. " +
            "MIC appears when voice is listening. Turn it on or off with the HUD button.\n\n" +
            "Practice: PRACTICE MODE is a safe 12-step coach. Nothing real is triggered. " +
            "Stop Air Touch first, because both use the camera.\n\n" +
            "Voice (optional, on-device, Android 13+, English): allow the microphone, " +
            "switch Voice ON, then press START AIR TOUCH. Say: volume up, volume down, " +
            "screenshot, brightness up, brightness down, go home, go back, recent apps, " +
            "keyboard, flashlight, pause, next song, wheel, air draw. " +
            "Needs the offline English speech pack. Some phones beep between phrases.\n\n" +
            "Problems and fixes:\n" +
            "- Play Protect App blocked: Play Store, profile, Play Protect, gear, turn off scanning, install, turn it back on.\n" +
            "- Accessibility greyed out: Settings, Apps, PhantomTouch, menu, Allow restricted settings.\n" +
            "- After installing an update, turn the Accessibility service off and on.\n" +
            "- App not installed when updating: uninstall the old version first.\n" +
            "- Stop other overlay apps (like MyBubble) if permission popups do not show.\n" +
            "- Set Battery to Unrestricted. Camera stops when the screen is off.\n" +
            "- Calibrate first in AIR MOUSE TEST. The values are shared.\n\n" +
            "Everything runs on this device. No video or audio is saved or sent."
        box.addView(help)

        val sv = ScrollView(this)
        sv.setBackgroundColor(Color.parseColor("#05060A"))
        sv.addView(box)
        setContentView(sv)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val sb = StringBuilder()
        sb.append(if (hasCam()) "Camera ✓\n" else "Camera ✕\n")
        sb.append(if (canOverlay()) "Overlay ✓\n" else "Overlay ✕\n")
        sb.append(if (PhantomAccess.instance != null) "Accessibility ✓\n" else "Accessibility ✕\n")
        sb.append(if (hasNotif()) "Notifications ✓\n" else "Notifications ✕\n")
        sb.append(if (hasMic()) "Microphone ✓ (optional)\n" else "Microphone ✕ (optional)\n")
        sb.append(if (voiceEngine()) "Offline speech ✓ (optional)\n" else "Offline speech ✕ (optional)\n")
        sb.append(if (HandService.running) "Service: RUNNING" else "Service: stopped")
        permText.text = sb.toString()
        startBtn.text = if (HandService.running) "STOP AIR TOUCH" else "START AIR TOUCH"
        skinBtn.text = "Cursor: " + (if (Settings.skin == 0) "MONKEY" else "DOT") + " (tap to switch)"
        sizeBtn.text = "Keyboard size: " + sizeName() + " (tap to change)"
        hudBtn.text = "HUD: " + (if (Settings.hud == 1) "ON" else "OFF") + " (tap to switch)"
        voiceBtn.text = "Voice commands: " + (if (Settings.voiceOn == 1) "ON" else "OFF") +
            " (applies on next START)"
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}