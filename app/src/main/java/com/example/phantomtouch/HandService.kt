package com.example.phantomtouch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Size
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
    }

    private val controller = CursorController()
    private val nav = NavGestures()
    private val typer = KeyTyper()
    private var engine: HandEngine? = null
    private var overlay: CursorOverlay? = null
    private var kb: KeyboardOverlay? = null
    private var hint: HintOverlay? = null
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
    private var lastHint = ""
    private var lastSay = ""
    private var lastSayT = 0L

    private var threeSince = 0L
    private var threeCool = 0L

    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
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
        } catch (e: Exception) {
            Toast.makeText(this, "Start failed: " + e.message, Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        lastMoveT = System.currentTimeMillis()
        startCamera()
        return START_NOT_STICKY
    }

    private fun handleAction(action: String) {
        when (action) {
            ACT_KB -> toggleKeyboard()
            ACT_AIR -> setAir(!airType)
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

    private fun toggleKeyboard() {
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
            kb?.hide()
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
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
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
            main.post {
                applyFrame(f, sw, sh, now)
                applyNav(navAct, sw, sh)
                if (three && running) toggleKeyboard()
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
                acc.back()
                Toast.makeText(this, "BACK", Toast.LENGTH_SHORT).show()
            }
            NavGestures.HOME -> {
                acc.home()
                Toast.makeText(this, "HOME", Toast.LENGTH_SHORT).show()
            }
            NavGestures.RECENTS -> {
                acc.recents()
                Toast.makeText(this, "RECENTS", Toast.LENGTH_SHORT).show()
            }
            NavGestures.SCROLL_UP, NavGestures.SCROLL_DOWN -> {
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

        val kbo = kb
        val kbOn = kbo != null && kbo.visible
        if (kbOn && kbo != null) {
            if (f.visible && !f.paused) kbo.hover(x, y) else kbo.hover(-1f, -1f)
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
                if (airType) {
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
                if (airType) {
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
                if (kbDrag) {
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
        try {
            provider?.unbindAll()
            executor.shutdown()
            executor.awaitTermination(500, TimeUnit.MILLISECONDS)
            engine?.close()
        } catch (e: Exception) {
        }
        main.removeCallbacksAndMessages(null)
        kb?.hide()
        kb = null
        hint?.remove()
        hint = null
        overlay?.remove()
        overlay = null
        super.onDestroy()
    }
}