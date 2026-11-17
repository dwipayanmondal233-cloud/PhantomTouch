package com.example.phantomtouch

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Size
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.hypot

class HandService : LifecycleService() {

    companion object {
        @Volatile
        var running = false
        const val CH = "phantomtouch"
        const val ACT_KB = "pt.kb"
        const val ACT_AIR = "pt.air"
        const val ACT_KBSIZE = "pt.kbsize"
        const val ACT_WHEEL = "pt.wheel"
        const val ACT_DRAW = "pt.draw"
        const val ACT_HUD = "pt.hud"
    }

    private val controller = CursorController()
    private val nav = NavGestures()
    private val typer = KeyTyper()
    private var engine: HandEngine? = null
    private var overlay: CursorOverlay? = null
    private var kb: KeyboardOverlay? = null
    private var hint: HintOverlay? = null
    private var wheel: WheelOverlay? = null
    private var draw: DrawOverlay? = null
    private var hud: HudOverlay? = null
    private var voice: VoiceControl? = null
    private var provider: ProcessCameraProvider? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var lastMoveT = 0L
    private var lastPx = 0f
    private var lastPy = 0f
    private var dragging = false
    private var dragSX = 0f
    private var dragSY = 0f

    private var airType = false
    private var kbDrag = false
    private var wheelDrag = false
    private var lastHint = ""
    private var lastSay = ""
    private var lastSayT = 0L

    private var drawOn = false
    private var drawing = false
    private var drawLastT = 0L
    private val strokePts = ArrayList<Pt2>()
    private var torchOn = false

    private var threeSince = 0L
    private var threeCool = 0L
    private var rockSince = 0L
    private var rockCool = 0L

    private var micOk = false
    private var voiceT = 0L
    private var gestureName = ""
    private var gestureUntil = 0L
    private var lastHud = ""

    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        GestureStore.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val action = intent?.action
        if (action != null) {
            if (running) {
                main.post { handleAction(action) }
            } else {
                stopSelf()
            }
            return START_NOT_STICKY
        }
        if (running) return START_NOT_STICKY
        startFg()
        try {
            engine = HandEngine(this)
            val ov = CursorOverlay(this)
            ov.show()
            overlay = ov
            kb = KeyboardOverlay(this)
            hint = HintOverlay(this)
            wheel = WheelOverlay(this)
            draw = DrawOverlay(this)
            val hu = HudOverlay(this)
            hud = hu
            if (Settings.hud == 1) hu.show()
        } catch (e: Exception) {
            Toast.makeText(this, "Start failed: " + e.message, Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        lastMoveT = System.currentTimeMillis()
        startCamera()
        if (micOk) startVoice()
        return START_NOT_STICKY
    }

    private fun handleAction(action: String) {
        when (action) {
            ACT_KB -> toggleKeyboard()
            ACT_AIR -> setAir(!airType)
            ACT_WHEEL -> toggleWheel()
            ACT_DRAW -> setDraw(!drawOn)
            ACT_HUD -> {
                if (Settings.hud == 1) hud?.show() else hud?.remove()
            }
            ACT_KBSIZE -> {
                val k = kb
                if (k != null && k.visible) {
                    val s = screenSize()
                    k.show(s[0], s[1])
                    typer.resetState()
                    bringCursorFront()
                }
            }
        }
    }

    private fun say(msg: String) {
        val now = System.currentTimeMillis()
        if (msg == lastSay && now - lastSayT < 1500L) return
        lastSay = msg
        lastSayT = now
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun flashGesture(name: String) {
        gestureName = name
        gestureUntil = System.currentTimeMillis() + 1500L
    }

    private fun screenSize(): IntArray {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(dm)
        return intArrayOf(dm.widthPixels, dm.heightPixels)
    }

    private fun bringCursorFront() {
        overlay?.remove()
        overlay?.show()
    }

    // ---------- HUD ----------

    private fun updateHud(f: CursorFrame, now: Long) {
        val h = hud ?: return
        if (Settings.hud != 1) return
        val track = when {
            !f.visible -> "NO HAND"
            f.paused -> "PAUSED"
            else -> "TRACK"
        }
        val mode = when {
            drawOn -> "DRAW"
            airType -> "AIR TYPE"
            wheel?.visible == true -> "WHEEL"
            kb?.visible == true -> "KEYS"
            else -> "CURSOR"
        }
        val g = if (now < gestureUntil) {
            gestureName
        } else {
            when {
                !f.visible -> "-"
                f.paused -> "FIST"
                f.state == CursorController.ST_DRAG -> "DRAG"
                f.pinched -> "PINCH"
                else -> "MOVE"
            }
        }
        val mic = if (voice?.listening == true) " | MIC" else ""
        val t = "$track | $mode | $g$mic"
        if (t != lastHud) {
            lastHud = t
            h.setText(t)
        }
    }

    // ---------- Voice ----------

    private fun startVoice() {
        val v = VoiceControl(
            this,
            { phrase -> onVoice(phrase) },
            { problem ->
                say(problem)
                voice = null
            }
        )
        val err = v.start()
        if (err != null) {
            say(err)
            return
        }
        voice = v
    }

    private fun onVoice(phrase: String) {
        val now = System.currentTimeMillis()
        if (now - voiceT < 1500L) return
        val t = phrase.lowercase().trim()
        val id: String? = when {
            t.contains("volume up") || t.contains("louder") -> "volup"
            t.contains("volume down") || t.contains("quieter") -> "voldn"
            t.contains("screenshot") || t.contains("screen shot") -> "shot"
            t.contains("brightness up") -> "brup"
            t.contains("brightness down") -> "brdn"
            t.contains("go home") || t.contains("home screen") -> "home"
            t.contains("go back") -> "back"
            t.contains("recent apps") || t.contains("recents") -> "recents"
            t.contains("keyboard") -> "kb"
            t.contains("flashlight") || t.contains("flash light") || t.contains("torch") -> "flash"
            t.contains("next song") || t.contains("next track") -> "next"
            t.contains("pause") || t.contains("play music") -> "music"
            t.contains("wheel") -> "wheel"
            t.contains("air draw") -> "draw"
            else -> null
        }
        if (id == null) {
            flashGesture("? " + t.take(14))
            return
        }
        voiceT = now
        flashGesture("VOICE")
        say("Voice: $t")
        if (id == "wheel") toggleWheel() else runWheelItem(id)
    }

    // ---------- Air Draw ----------

    private fun setDraw(on: Boolean) {
        if (on == drawOn) return
        if (on) {
            if (airType) setAir(false)
            wheel?.hide()
            kb?.hide()
            val d = draw ?: return
            val s = screenSize()
            val rec = GestureStore.recName
            val b = when {
                rec.isNotEmpty() -> "AIR REC: $rec"
                Settings.drawTest == 1 -> "AIR DRAW (TEST ONLY)"
                else -> "AIR DRAW"
            }
            d.show(s[0], s[1], "$b  |  ROCK = exit")
            drawOn = true
            drawing = false
            strokePts.clear()
            drawLastT = System.currentTimeMillis()
            bringCursorFront()
            say("AIR DRAW ON. Pinch and hold, draw, release.")
        } else {
            drawOn = false
            drawing = false
            strokePts.clear()
            draw?.hide()
            GestureStore.recName = ""
            say("AIR DRAW OFF")
        }
    }

    private fun handleDraw(
        f: CursorFrame, sw: Int, sh: Int, now: Long, ov: CursorOverlay, dro: DrawOverlay
    ) {
        val x = (f.x * sw).coerceIn(0f, sw.toFloat())
        val y = (f.y * sh).coerceIn(0f, sh.toFloat())

        if (f.event == CursorController.EV_DRAG_START) {
            ov.slam(now)
            drawing = true
            strokePts.clear()
            strokePts.add(
                Pt2((f.evX * sw).coerceIn(0f, sw.toFloat()), (f.evY * sh).coerceIn(0f, sh.toFloat()))
            )
            dro.begin()
            drawLastT = now
        }
        if (drawing && f.state == CursorController.ST_DRAG && strokePts.size < 600) {
            strokePts.add(Pt2(x, y))
            dro.setPoints(strokePts)
            drawLastT = now
        }
        if (f.event == CursorController.EV_DRAG_END && drawing) {
            drawing = false
            dro.end()
            drawLastT = now
            finishStroke(dro)
        }
        if (!drawing && now - drawLastT > 30000L) setDraw(false)
    }

    private fun finishStroke(dro: DrawOverlay) {
        val pts = ArrayList<Pt2>(strokePts)
        strokePts.clear()

        val rec = GestureStore.recName
        if (rec.isNotEmpty()) {
            val g = GestureStore.find(rec)
            if (g == null) {
                GestureStore.recName = ""
                dro.say("Recording target is gone")
                return
            }
            val ok = GestureStore.addSample(g, pts)
            dro.say(if (ok) "Sample saved for " + g.name + " (" + g.samples.size + ")" else "Too short. Draw bigger.")
            return
        }

        val n = Recognizer.normalize(pts)
        if (n == null) {
            dro.say("Too short. Draw bigger.")
            return
        }
        val m = Recognizer.recognize(n, GestureStore.all())
        if (m == null) {
            dro.say("No gestures saved")
            return
        }
        val pct = (m.score * 100).toInt()
        if (m.score < Recognizer.ACCEPT || m.score - m.second < 0.03f) {
            dro.say("? " + m.name + " " + pct + "% (not sure)")
            return
        }
        val g = m.gesture
        if (Settings.drawTest == 1) {
            dro.say("TEST: " + m.name + " " + pct + "%")
        } else if (g.action.isEmpty()) {
            dro.say(m.name + " " + pct + "% (no action)")
        } else {
            dro.say(m.name + " " + pct + "%")
            runWheelItem(g.action)
        }
    }

    // ---------- Keyboard ----------

    private fun toggleKeyboard() {
        if (drawOn) {
            say("Turn off air draw first")
            return
        }
        if (airType) {
            setAir(false)
            return
        }
        val k = kb ?: return
        if (k.visible) {
            k.hide()
            say("KEYBOARD OFF")
        } else {
            val s = screenSize()
            typer.resetState()
            k.show(s[0], s[1])
            bringCursorFront()
            say("KEYBOARD ON. Tap a text field first.")
        }
    }

    private fun setAir(on: Boolean) {
        if (on == airType) return
        if (on) {
            if (drawOn) setDraw(false)
            kb?.hide()
            wheel?.hide()
            typer.resetState()
            typer.resetCounts()
            airType = true
            lastHint = ""
            hint?.show()
            hint?.setText("AIR TYPE (experiment)")
            bringCursorFront()
            say("AIR TYPING ON. Tap a text field first.")
        } else {
            airType = false
            hint?.remove()
            val t = typer.typed
            val d = typer.deleted
            if (t > 0) {
                val kept = ((t - d).coerceAtLeast(0) * 100) / t
                say("Air typing OFF. Typed $t, deleted $d, kept about $kept%")
            } else {
                say("Air typing OFF")
            }
        }
    }

    private fun handleKey(key: Key) {
        val r = typer.press(key, airType)
        when (r) {
            "CLOSE" -> kb?.hide()
            "NOFIELD" -> say("Tap a text field first")
            "SECURE" -> say("Password field: blocked")
            "NOACC" -> say("Turn Accessibility on")
        }
        kb?.setState(typer.sym, typer.shift)
    }

    private fun kbPress(x: Float, y: Float) {
        val k = kb ?: return
        val key = k.keyAt(x, y) ?: return
        handleKey(key)
        k.flash(key)
    }

    private fun airPress(nx: Float, ny: Float) {
        val key = KeyLayout.find(typer.rows(), nx, ny) ?: return
        handleKey(key)
    }

    // ---------- Wheel and actions ----------

    private fun toggleWheel() {
        if (drawOn) {
            setDraw(false)
            return
        }
        val w = wheel ?: return
        if (w.visible) {
            w.hide()
            say("WHEEL OFF")
            return
        }
        if (airType) {
            say("Turn off air typing first")
            return
        }
        val ids = WheelItems.ids()
        if (ids.isEmpty()) {
            say("Wheel is empty. Use EDIT WHEEL.")
            return
        }
        val s = screenSize()
        w.show(s[0], s[1], ids)
        bringCursorFront()
        say("WHEEL ON")
    }

    private fun wheelPress(x: Float, y: Float) {
        val w = wheel ?: return
        val i = w.itemAt(x, y)
        if (i < 0) {
            w.hide()
            return
        }
        val id = w.idAt(i)
        w.flash(i)
        runWheelItem(id)
    }

    private fun runWheelItem(id: String) {
        val acc = PhantomAccess.instance
        when (id) {
            "volup" -> volume(AudioManager.ADJUST_RAISE)
            "voldn" -> volume(AudioManager.ADJUST_LOWER)
            "brup" -> brightness(25)
            "brdn" -> brightness(-25)
            "music" -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "next" -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            else -> {
                wheel?.hide()
                when (id) {
                    "home" -> if (acc != null) acc.home() else say("Turn Accessibility on")
                    "back" -> if (acc != null) acc.back() else say("Turn Accessibility on")
                    "recents" -> if (acc != null) acc.recents() else say("Turn Accessibility on")
                    "shot" -> shot()
                    "kb" -> toggleKeyboard()
                    "flash" -> toggleFlash()
                    "draw" -> setDraw(!drawOn)
                    else -> if (id.startsWith("app:")) launchApp(id.substring(4))
                }
            }
        }
    }

    private fun toggleFlash() {
        try {
            val cm = getSystemService(CameraManager::class.java)
            var id: String? = null
            for (c in cm.cameraIdList) {
                val ch = cm.getCameraCharacteristics(c)
                val hasFlash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val back = ch.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
                if (hasFlash && back) {
                    id = c
                    break
                }
            }
            if (id == null) {
                say("No flashlight found")
                return
            }
            torchOn = !torchOn
            cm.setTorchMode(id, torchOn)
            say(if (torchOn) "FLASHLIGHT ON" else "FLASHLIGHT OFF")
        } catch (e: Exception) {
            torchOn = false
            say("Flashlight failed")
        }
    }

    private fun shot() {
        if (PhantomAccess.instance == null) {
            say("Turn Accessibility on")
            return
        }
        if (Build.VERSION.SDK_INT < 28) {
            say("Screenshot needs Android 9 or newer")
            return
        }
        main.postDelayed({
            PhantomAccess.instance?.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
            )
        }, 500L)
    }

    private fun volume(dir: Int) {
        try {
            val am = getSystemService(AudioManager::class.java)
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {
            say("Volume failed")
        }
    }

    private fun brightness(delta: Int) {
        if (!android.provider.Settings.System.canWrite(this)) {
            say("Tap ALLOW BRIGHTNESS in the app first")
            return
        }
        try {
            val cr = contentResolver
            val cur = android.provider.Settings.System.getInt(
                cr, android.provider.Settings.System.SCREEN_BRIGHTNESS, 128
            )
            val v = (cur + delta).coerceIn(10, 255)
            android.provider.Settings.System.putInt(
                cr,
                android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            android.provider.Settings.System.putInt(
                cr, android.provider.Settings.System.SCREEN_BRIGHTNESS, v
            )
            say("Brightness " + (v * 100 / 255) + "%")
        } catch (e: Exception) {
            say("Brightness failed")
        }
    }

    private fun mediaKey(code: Int) {
        try {
            val am = getSystemService(AudioManager::class.java)
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        } catch (e: Exception) {
            say("Media key failed")
        }
    }

    private fun launchApp(pkg: String) {
        val li = packageManager.getLaunchIntentForPackage(pkg)
        if (li == null) {
            say("App not found")
            return
        }
        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(li)
        } catch (e: Exception) {
            say("Could not open app")
        }
    }

    // ---------- Service basics ----------

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH, "PhantomTouch", NotificationManager.IMPORTANCE_LOW)
        )
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, ControlActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CH)
            .setContentTitle("PhantomTouch is on")
            .setContentText("Hand tracking runs on this device only.")
            .setSmallIcon(R.drawable.ic_notif)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()

        val wantMic = Settings.voiceOn == 1 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (wantMic) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        try {
            ServiceCompat.startForeground(this, 1, n, type)
            micOk = wantMic
        } catch (e: SecurityException) {
            micOk = false
            ServiceCompat.startForeground(
                this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(480, 640))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(executor) { proxy -> analyze(proxy) }
                p.unbindAll()
                p.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera error: " + e.message, Toast.LENGTH_LONG).show()
                stopSelf()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun threeFingerHold(hand: Hand?, w: Int, h: Int, now: Long): Boolean {
        if (hand == null) {
            threeSince = 0L
            return false
        }
        val p = GestureEngine.analyze(hand, w, h)
        val three = p.index && p.middle && p.ring && !p.pinky
        if (!three) {
            threeSince = 0L
            return false
        }
        if (threeSince == 0L) threeSince = now
        if (now - threeSince >= 1000L && now >= threeCool) {
            threeSince = 0L
            threeCool = now + 2500L
            return true
        }
        return false
    }

    private fun rockHold(hand: Hand?, w: Int, h: Int, now: Long): Boolean {
        if (hand == null) {
            rockSince = 0L
            return false
        }
        val p = GestureEngine.analyze(hand, w, h)
        val rock = p.index && !p.middle && !p.ring && p.pinky
        if (!rock) {
            rockSince = 0L
            return false
        }
        if (rockSince == 0L) rockSince = now
        if (now - rockSince >= 1000L && now >= rockCool) {
            rockSince = 0L
            rockCool = now + 2500L
            return true
        }
        return false
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            val eng = engine ?: return
            val raw = proxy.toBitmap()
            val rot = proxy.imageInfo.rotationDegrees
            val matrix = Matrix()
            matrix.postRotate(rot.toFloat())
            val upright = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            val w = upright.width
            val h = upright.height
            val hands = eng.detect(upright)
            val hand = if (hands.isEmpty()) null else hands[0]

            val s = screenSize()
            val sw = s[0]
            val sh = s[1]
            controller.screenW = sw.toFloat()
            controller.screenH = sh.toFloat()

            val now = System.currentTimeMillis()
            val f = controller.process(hand, w, h, now)
            val navAct = if (Settings.navOn == 1) {
                nav.process(hand, w, h, now)
            } else {
                NavGestures.NONE
            }
            val three = threeFingerHold(hand, w, h, now)
            val rock = rockHold(hand, w, h, now)
            main.post {
                applyFrame(f, sw, sh, now)
                applyNav(navAct, sw, sh)
                if (three && running) {
                    flashGesture("3 FINGERS")
                    toggleKeyboard()
                }
                if (rock && running) {
                    flashGesture("ROCK")
                    toggleWheel()
                }
            }
        } catch (e: Exception) {
        } finally {
            proxy.close()
        }
    }

    private fun applyNav(a: Int, sw: Int, sh: Int) {
        if (a == NavGestures.NONE || !running) return
        val acc = PhantomAccess.instance ?: return
        when (a) {
            NavGestures.BACK -> {
                flashGesture("BACK")
                acc.back()
                Toast.makeText(this, "BACK", Toast.LENGTH_SHORT).show()
            }
            NavGestures.HOME -> {
                flashGesture("HOME")
                acc.home()
                Toast.makeText(this, "HOME", Toast.LENGTH_SHORT).show()
            }
            NavGestures.RECENTS -> {
                flashGesture("RECENTS")
                acc.recents()
                Toast.makeText(this, "RECENTS", Toast.LENGTH_SHORT).show()
            }
            NavGestures.SCROLL_UP, NavGestures.SCROLL_DOWN -> {
                flashGesture("SCROLL")
                var up = (a == NavGestures.SCROLL_UP)
                if (Settings.invert == 1) up = !up
                acc.scroll(up, sw.toFloat(), sh.toFloat())
            }
        }
    }

    private fun applyFrame(f: CursorFrame, sw: Int, sh: Int, now: Long) {
        val ov = overlay ?: return
        if (!running) return

        fun px(v: Float): Float = (v * sw).coerceIn(1f, sw - 1f)
        fun py(v: Float): Float = (v * sh).coerceIn(1f, sh - 1f)

        ov.setVisible(f.visible)
        val x = px(f.x)
        val y = py(f.y)
        if (hypot(x - lastPx, y - lastPy) > 20f) {
            lastPx = x
            lastPy = y
            lastMoveT = now
        }
        val sleeping = (now - lastMoveT > 5000L) && !f.pinched && !f.paused
        if (f.visible) ov.update(x, y, f.paused, f.pinched, sleeping)

        updateHud(f, now)

        val dro = draw
        if (drawOn && dro != null) {
            handleDraw(f, sw, sh, now, ov, dro)
            return
        }

        val kbo = kb
        val kbOn = kbo != null && kbo.visible
        if (kbOn && kbo != null) {
            if (f.visible && !f.paused) kbo.hover(x, y) else kbo.hover(-1f, -1f)
        }

        val wo = wheel
        val wheelOn = wo != null && wo.visible
        if (wheelOn && wo != null) {
            if (f.visible && !f.paused) wo.hover(x, y) else wo.hover(-1f, -1f)
        }

        if (airType) {
            val key = if (f.visible && !f.paused) KeyLayout.find(typer.rows(), f.x, f.y) else null
            val name = when {
                key == null -> "-"
                key.code == "#NONE" -> "-"
                key.code.startsWith("#") -> key.code.substring(1).lowercase()
                typer.shift > 0 && key.label.length == 1 -> key.label.uppercase()
                else -> key.label
            }
            val t = "AIR TYPE  key: $name  typed ${typer.typed}  del ${typer.deleted}"
            if (t != lastHint) {
                lastHint = t
                hint?.setText(t)
            }
        }

        val acc = PhantomAccess.instance
        val ex = px(f.evX)
        val ey = py(f.evY)
        when (f.event) {
            CursorController.EV_CLICK, CursorController.EV_DOUBLE -> {
                ov.slam(now)
                if (wheelOn) {
                    wheelPress(ex, ey)
                } else if (airType) {
                    airPress(f.evX, f.evY)
                } else if (kbOn && kbo != null && kbo.contains(ex, ey)) {
                    kbPress(ex, ey)
                } else if (f.event == CursorController.EV_CLICK) {
                    acc?.tap(ex, ey)
                } else {
                    acc?.doubleTap(ex, ey)
                }
            }
            CursorController.EV_DRAG_START -> {
                ov.slam(now)
                if (wheelOn) {
                    wheelDrag = true
                    wheelPress(ex, ey)
                } else if (airType) {
                    kbDrag = true
                    airPress(f.evX, f.evY)
                } else if (kbOn && kbo != null && kbo.contains(ex, ey)) {
                    kbDrag = true
                    kbPress(ex, ey)
                } else {
                    dragging = true
                    dragSX = ex
                    dragSY = ey
                }
            }
            CursorController.EV_DRAG_END -> {
                if (wheelDrag) {
                    wheelDrag = false
                } else if (kbDrag) {
                    kbDrag = false
                } else if (dragging) {
                    dragging = false
                    acc?.dragOrHold(dragSX, dragSY, ex, ey)
                }
            }
        }
    }

    override fun onDestroy() {
        running = false
        voice?.stop()
        voice = null
        try {
            provider?.unbindAll()
            executor.shutdown()
            executor.awaitTermination(500, TimeUnit.MILLISECONDS)
            engine?.close()
        } catch (e: Exception) {
        }
        main.removeCallbacksAndMessages(null)
        GestureStore.recName = ""
        hud?.remove()
        hud = null
        draw?.hide()
        draw = null
        wheel?.hide()
        wheel = null
        kb?.hide()
        kb = null
        hint?.remove()
        hint = null
        overlay?.remove()
        overlay = null
        super.onDestroy()
    }
}