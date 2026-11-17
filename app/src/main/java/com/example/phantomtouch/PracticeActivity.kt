package com.example.phantomtouch

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Size
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.hypot

class PracticeActivity : ComponentActivity() {

    private class Step(val title: String, val body: String, val target: Boolean)

    private val steps = listOf(
        Step("SHOW YOUR HAND", "Hold your hand 30 to 40 cm\nfrom the front camera.\nKeep the whole hand in view.", false),
        Step("MOVE THE CURSOR", "Move your index finger.\nKeep the ring on the target\nfor 1 second.", true),
        Step("CLICK", "Pinch thumb and index\nonce on the target.", true),
        Step("DOUBLE CLICK", "Pinch twice, fast,\non the target.", true),
        Step("DRAG", "Pinch and HOLD on the target,\nmove your hand sideways,\nthen open your fingers.", true),
        Step("PAUSE", "Make a fist.\nThe ring turns grey.\nHold it for about 1 second.", false),
        Step("SCROLL", "Peace sign: index and middle up.\nMove your hand up or down.", false),
        Step("BACK", "Open all five fingers,\nwait a moment, then\nflick left fast.", false),
        Step("HOME", "Open hand, wait a moment,\nthen flick up fast.", false),
        Step("RECENTS", "Make a fist and hold it\nfor 1.2 seconds.", false),
        Step("KEYBOARD SIGN", "Hold 3 fingers up\n(index, middle, ring)\nfor 1 second.", false),
        Step("WHEEL SIGN", "Rock sign: index and pinky up,\nmiddle and ring down.\nHold for 1 second.", false),
        Step("ALL DONE", "You finished the course.\nNothing real was triggered.", false)
    )

    private lateinit var view: PracticeView
    private lateinit var statusText: TextView
    private lateinit var startBtn: Button

    private val controller = CursorController()
    private val nav = NavGestures()
    private val threeHold = PoseHold()
    private val rockHold = PoseHold()
    private var engine: HandEngine? = null
    private var provider: ProcessCameraProvider? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    private var running = false

    private var step = 0
    private var stepAt = 0L
    private var seen = 0
    private var dwell = 0L
    private var dragOk = false
    private var dsx = 0f
    private var dsy = 0f

    private var frames = 0
    private var lastFpsTime = 0L
    private var fps = 0

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else statusText.text = "Camera permission denied"
        }

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#05060A"))
        root.setPadding(dp(10), dp(20), dp(10), dp(10))

        statusText = TextView(this)
        statusText.typeface = Typeface.MONOSPACE
        statusText.textSize = 13f
        statusText.setTextColor(Color.parseColor("#00F0FF"))
        statusText.text = "PRACTICE. Safe: nothing real is triggered. Tap START."
        root.addView(statusText)

        view = PracticeView(this)
        view.onSize = { w, h ->
            controller.screenW = w.toFloat()
            controller.screenH = h.toFloat()
        }
        root.addView(
            view,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        startBtn = neon("START", "#B44CFF")
        startBtn.setOnClickListener {
            if (running) stopCamera() else requestAndStart()
        }
        val skipBtn = neon("SKIP", "#00F0FF")
        skipBtn.setOnClickListener {
            if (step < steps.size - 1) advance(System.currentTimeMillis(), false)
        }
        val restartBtn = neon("RESTART", "#FF2BD6")
        restartBtn.setOnClickListener {
            step = 0
            stepAt = System.currentTimeMillis()
            resetStepState()
            showStep()
            view.invalidate()
        }
        val lp = LinearLayout.LayoutParams(0, dp(52), 1f)
        lp.setMargins(dp(3), dp(6), dp(3), 0)
        row.addView(startBtn, lp)
        row.addView(skipBtn, LinearLayout.LayoutParams(lp))
        row.addView(restartBtn, LinearLayout.LayoutParams(lp))
        root.addView(row)

        setContentView(root)
        showStep()

        try {
            engine = HandEngine(this)
        } catch (e: Exception) {
            statusText.text = "Model error: " + e.message
        }
    }

    private fun resetStepState() {
        seen = 0
        dwell = 0L
        dragOk = false
        nav.reset()
        threeHold.reset()
        rockHold.reset()
    }

    private fun showStep() {
        val s = steps[step]
        view.title = "STEP " + (step + 1) + " / " + steps.size + "  " + s.title
        view.body = s.body
        view.showTarget = s.target
        view.progress = step
        view.total = steps.size - 1
    }

    private fun advance(now: Long, good: Boolean) {
        step++
        stepAt = now
        resetStepState()
        if (good) view.flashUntil = now + 700L
        showStep()
        view.invalidate()
    }

    private fun hasCamera(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestAndStart() {
        if (HandService.running) {
            statusText.text = "Stop Air Touch first. It is using the camera."
            return
        }
        if (hasCamera()) startCamera() else permLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        if (engine == null) {
            statusText.text = "Model not loaded"
            return
        }
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
                controller.reset()
                resetStepState()
                stepAt = System.currentTimeMillis()
                running = true
                frames = 0
                lastFpsTime = System.currentTimeMillis()
                startBtn.text = "STOP"
                statusText.text = "Show your hand"
            } catch (e: Exception) {
                statusText.text = "Camera error: " + e.message
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        provider?.unbindAll()
        running = false
        controller.reset()
        startBtn.text = "START"
        statusText.text = "Stopped"
        view.setFrame(CursorFrame(false, false, 0.5f, 0.5f, false, 0, 1f, 0, 0f, 0f))
        view.invalidate()
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
            val now = System.currentTimeMillis()

            frames++
            if (now - lastFpsTime >= 1000) {
                fps = frames
                frames = 0
                lastFpsTime = now
            }

            val f = controller.process(hand, w, h, now)
            val navAct = nav.process(hand, w, h, now)
            var three = false
            var rock = false
            if (hand != null) {
                val p = GestureEngine.analyze(hand, w, h)
                three = threeHold.update(p.index && p.middle && p.ring && !p.pinky, now)
                rock = rockHold.update(p.index && !p.middle && !p.ring && p.pinky, now)
            } else {
                threeHold.reset()
                rockHold.reset()
            }
            val status = when {
                hand == null -> "No hand | $fps fps"
                f.paused -> "PAUSED (fist) | $fps fps"
                else -> "TRACKING | $fps fps"
            }
            runOnUiThread {
                if (running) handle(f, navAct, three, rock, now, status)
            }
        } catch (e: Exception) {
            runOnUiThread { statusText.text = "Frame error: " + e.message }
        } finally {
            proxy.close()
        }
    }

    private fun handle(
        f: CursorFrame, navAct: Int, three: Boolean, rock: Boolean, now: Long, status: String
    ) {
        statusText.text = status
        view.setFrame(f)

        val label = when {
            navAct == NavGestures.BACK -> "BACK flick"
            navAct == NavGestures.HOME -> "HOME flick"
            navAct == NavGestures.RECENTS -> "RECENTS (fist hold)"
            navAct == NavGestures.SCROLL_UP -> "SCROLL (hand up)"
            navAct == NavGestures.SCROLL_DOWN -> "SCROLL (hand down)"
            three -> "3 FINGERS"
            rock -> "ROCK"
            f.event == CursorController.EV_DOUBLE -> "DOUBLE click"
            f.event == CursorController.EV_CLICK -> "CLICK"
            f.event == CursorController.EV_DRAG_START -> "DRAG start"
            f.event == CursorController.EV_DRAG_END -> "DRAG end"
            else -> ""
        }
        if (label.isNotEmpty()) view.last = label

        if (step < steps.size - 1 && now - stepAt > 800L) {
            val w = view.width.toFloat()
            val h = view.height.toFloat()
            var ok = false
            when (step) {
                0 -> {
                    if (f.visible) seen++ else seen = 0
                    ok = seen >= 15
                }
                1 -> {
                    val inside = f.visible && !f.paused && view.target.contains(f.x * w, f.y * h)
                    if (inside) {
                        if (dwell == 0L) dwell = now
                        ok = now - dwell >= 1000L
                    } else {
                        dwell = 0L
                    }
                }
                2 -> ok = f.event == CursorController.EV_CLICK &&
                    view.target.contains(f.evX * w, f.evY * h)
                3 -> ok = f.event == CursorController.EV_DOUBLE &&
                    view.target.contains(f.evX * w, f.evY * h)
                4 -> {
                    if (f.event == CursorController.EV_DRAG_START &&
                        view.target.contains(f.evX * w, f.evY * h)
                    ) {
                        dragOk = true
                        dsx = f.evX * w
                        dsy = f.evY * h
                    }
                    if (f.event == CursorController.EV_DRAG_END && dragOk) {
                        dragOk = false
                        ok = hypot(f.evX * w - dsx, f.evY * h - dsy) > w * 0.15f
                    }
                }
                5 -> {
                    if (f.paused) {
                        if (dwell == 0L) dwell = now
                        ok = now - dwell >= 600L
                    } else {
                        dwell = 0L
                    }
                }
                6 -> ok = navAct == NavGestures.SCROLL_UP || navAct == NavGestures.SCROLL_DOWN
                7 -> ok = navAct == NavGestures.BACK
                8 -> ok = navAct == NavGestures.HOME
                9 -> ok = navAct == NavGestures.RECENTS
                10 -> ok = three
                11 -> ok = rock
            }
            if (ok) advance(now, true)
        }
        view.invalidate()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            provider?.unbindAll()
            executor.shutdown()
            executor.awaitTermination(500, TimeUnit.MILLISECONDS)
            engine?.close()
        } catch (e: Exception) {
        }
    }
}