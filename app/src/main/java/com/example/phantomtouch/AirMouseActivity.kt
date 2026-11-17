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
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
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
import kotlin.math.abs

class AirMouseActivity : ComponentActivity() {

    private lateinit var air: AirMouseView
    private lateinit var statusText: TextView
    private lateinit var tunePanel: ScrollView
    private lateinit var tuneBox: LinearLayout
    private lateinit var startBtn: Button

    private val controller = CursorController()
    private var engine: HandEngine? = null
    private var provider: ProcessCameraProvider? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile private var running = false
    @Volatile private var calStage = 0
    private var calStart = 0L
    private var sumX = 0f
    private var sumY = 0f
    private var sumN = 0
    private var p1x = 0f
    private var p1y = 0f
    private var noticeText = ""
    private var noticeUntil = 0L

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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#05060A"))
            setPadding(dp(10), dp(20), dp(10), dp(10))
        }

        statusText = TextView(this)
        statusText.typeface = Typeface.MONOSPACE
        statusText.textSize = 13f
        statusText.setTextColor(Color.parseColor("#00F0FF"))
        statusText.text = "Tap START. Pinch = click, hold = drag, fist = pause."
        root.addView(statusText)

        val frame = FrameLayout(this)
        air = AirMouseView(this)
        air.onSize = { w, h ->
            controller.screenW = w.toFloat()
            controller.screenH = h.toFloat()
        }
        frame.addView(
            air,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        tunePanel = ScrollView(this)
        tunePanel.setBackgroundColor(Color.parseColor("#F005060A"))
        tunePanel.visibility = View.GONE
        tuneBox = LinearLayout(this)
        tuneBox.orientation = LinearLayout.VERTICAL
        tuneBox.setPadding(dp(10), dp(10), dp(10), dp(10))
        tunePanel.addView(tuneBox)
        frame.addView(
            tunePanel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            frame,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL

        startBtn = neon("START", "#B44CFF")
        startBtn.setOnClickListener {
            if (running) stopCamera() else requestAndStart()
        }
        val calBtn = neon("CALIBRATE", "#00F0FF")
        calBtn.setOnClickListener {
            if (!running) {
                statusText.text = "Start the camera first"
            } else {
                tunePanel.visibility = View.GONE
                calStart = 0L
                calStage = 1
            }
        }
        val tuneBtn = neon("TUNE", "#B44CFF")
        tuneBtn.setOnClickListener {
            tunePanel.visibility =
                if (tunePanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        val lp = LinearLayout.LayoutParams(0, dp(52), 1f)
        lp.setMargins(dp(3), dp(6), dp(3), 0)
        row.addView(startBtn, lp)
        row.addView(calBtn, lp)
        row.addView(tuneBtn, lp)
        root.addView(row)

        setContentView(root)
        buildTune()

        try {
            engine = HandEngine(this)
        } catch (e: Exception) {
            statusText.text = "Model error: " + e.message
        }
    }

    private fun addSlider(
        label: String,
        min: Float,
        max: Float,
        step: Float,
        getter: () -> Float,
        setter: (Float) -> Unit
    ) {
        val lab = TextView(this)
        lab.typeface = Typeface.MONOSPACE
        lab.textSize = 12f
        lab.setTextColor(Color.parseColor("#88FFBB"))
        lab.text = label + ": " + String.format("%.2f", getter())
        lab.setPadding(0, dp(8), 0, 0)
        tuneBox.addView(lab)

        val bar = SeekBar(this)
        bar.max = Math.round((max - min) / step)
        bar.progress = Math.round((getter() - min) / step)
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                val v = min + p * step
                setter(v)
                lab.text = label + ": " + String.format("%.2f", v)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        tuneBox.addView(bar)
    }

    private fun buildTune() {
        tuneBox.removeAllViews()

        addSlider("Sensitivity (bigger = smaller hand area)", 0.5f, 3f, 0.05f,
            { Settings.sens }, { Settings.sens = it })
        addSlider("Smoothness cutoff (lower = smoother)", 0.2f, 5f, 0.1f,
            { Settings.minCutoff }, { Settings.minCutoff = it })
        addSlider("Speed response (higher = less lag)", 0f, 20f, 0.5f,
            { Settings.beta }, { Settings.beta = it })
        addSlider("Pinch ON ratio (smaller = harder)", 0.10f, 0.50f, 0.01f,
            { Settings.pinchOn }, { Settings.pinchOn = it })
        addSlider("Pinch OFF ratio (bigger than ON)", 0.20f, 0.80f, 0.01f,
            { Settings.pinchOff }, { Settings.pinchOff = it })
        addSlider("Dead zone (px)", 0f, 30f, 1f,
            { Settings.dead }, { Settings.dead = it })
        addSlider("Hold time for drag (ms)", 150f, 800f, 10f,
            { Settings.hold.toFloat() }, { Settings.hold = it.toInt() })

        val pt = neon("", "#00F0FF")
        fun ptText() {
            pt.text = "Pointer: " + (if (Settings.point == 8) "FINGERTIP" else "KNUCKLE (steadier)")
        }
        ptText()
        pt.setOnClickListener {
            Settings.point = if (Settings.point == 8) 5 else 8
            ptText()
        }
        val lp1 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
        )
        lp1.setMargins(0, dp(12), 0, 0)
        tuneBox.addView(pt, lp1)

        val rs = neon("RESET ALL (and calibration)", "#FF2BD6")
        rs.setOnClickListener {
            Settings.resetAll()
            controller.reset()
            buildTune()
        }
        val lp2 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
        )
        lp2.setMargins(0, dp(8), 0, dp(20))
        tuneBox.addView(rs, lp2)
    }

    private fun hasCamera(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestAndStart() {
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
        calStage = 0
        controller.reset()
        startBtn.text = "START"
        statusText.text = "Stopped"
        air.message = ""
        air.handle(CursorFrame(false, false, 0.5f, 0.5f, false, 0, 1f, 0, 0f, 0f))
    }

    private fun calStep(now: Long, hasHand: Boolean): String {
        val corner = if (calStage == 1) "TOP-LEFT" else "BOTTOM-RIGHT"
        if (!hasHand) {
            calStart = 0L
            return "CALIBRATE $corner\nShow your hand"
        }
        if (calStart == 0L) {
            calStart = now
            sumX = 0f
            sumY = 0f
            sumN = 0
        }
        val left = 4000L - (now - calStart)
        if (left <= 1500L) {
            sumX += controller.rawX
            sumY += controller.rawY
            sumN++
        }
        if (left <= 0L) {
            val n = if (sumN < 1) 1 else sumN
            val ax = sumX / n
            val ay = sumY / n
            if (calStage == 1) {
                p1x = ax
                p1y = ay
                calStage = 2
                calStart = 0L
                return "Got it. Next corner..."
            } else {
                Settings.calCx = (ax + p1x) / 2f
                Settings.calCy = (ay + p1y) / 2f
                Settings.calHw = maxOf(abs(ax - p1x) / 2f, 0.08f)
                Settings.calHh = maxOf(abs(ay - p1y) / 2f, 0.08f)
                controller.reset()
                calStage = 0
                calStart = 0L
                noticeText = "Calibration saved ✓"
                noticeUntil = now + 2500
                return noticeText
            }
        }
        val secs = (left.coerceAtLeast(0L) / 1000L) + 1L
        return "CALIBRATE $corner\nPoint here, hold still: $secs"
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

            val frame = controller.process(hand, w, h, now)

            if (calStage > 0) {
                val msg = calStep(now, hand != null)
                runOnUiThread {
                    if (running) {
                        air.message = msg
                        air.invalidate()
                    }
                }
                return
            }

            val stateName = when (frame.state) {
                CursorController.ST_PENDING -> "PINCH"
                CursorController.ST_DRAG -> "DRAG"
                else -> "idle"
            }
            val status = when {
                !frame.visible -> "No hand | $fps fps"
                frame.paused -> "PAUSED (fist) | $fps fps"
                else -> "TRACKING | $stateName | ratio " +
                    String.format("%.2f", frame.ratio) + " | $fps fps"
            }
            val note = if (now < noticeUntil) noticeText else ""

            runOnUiThread {
                if (running) {
                    air.message = note
                    air.handle(frame)
                    statusText.text = status
                }
            }
        } catch (e: Exception) {
            runOnUiThread { statusText.text = "Frame error: " + e.message }
        } finally {
            proxy.close()
        }
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