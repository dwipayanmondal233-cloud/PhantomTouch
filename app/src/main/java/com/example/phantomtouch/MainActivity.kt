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
import android.view.Gravity
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

class MainActivity : ComponentActivity() {

    private lateinit var skeleton: SkeletonView
    private lateinit var statusText: TextView
    private lateinit var fingerText: TextView
    private lateinit var permText: TextView
    private lateinit var button: Button

    private var engine: HandEngine? = null
    private var provider: ProcessCameraProvider? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var running = false

    private var frames = 0
    private var lastFpsTime = 0L
    private var fps = 0

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            updatePermText()
            if (granted) {
                startCamera()
            } else {
                statusText.text = "Camera permission denied"
            }
        }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun mono(tv: TextView, size: Float, color: String) {
        tv.typeface = Typeface.MONOSPACE
        tv.textSize = size
        tv.setTextColor(Color.parseColor(color))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#05060A"))
            setPadding(dp(12), dp(24), dp(12), dp(12))
        }

        val title = TextView(this)
        mono(title, 24f, "#00F0FF")
        title.text = "PHANTOMTOUCH"
        title.setShadowLayer(8f, 3f, 0f, Color.parseColor("#B44CFF"))
        root.addView(title)

        permText = TextView(this)
        mono(permText, 12f, "#8FB")
        permText.setPadding(0, dp(4), 0, dp(4))
        root.addView(permText)

        statusText = TextView(this)
        mono(statusText, 14f, "#00F0FF")
        statusText.text = "Tap START"
        root.addView(statusText)

        fingerText = TextView(this)
        mono(fingerText, 13f, "#CFEFFF")
        fingerText.text = " \n "
        root.addView(fingerText)

        skeleton = SkeletonView(this)
        val skelParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        skelParams.setMargins(0, dp(8), 0, dp(8))
        root.addView(skeleton, skelParams)

        button = Button(this)
        button.typeface = Typeface.MONOSPACE
        button.textSize = 16f
        button.setTextColor(Color.WHITE)
        button.text = "START CAMERA"
        val bg = GradientDrawable()
        bg.cornerRadius = dp(14).toFloat()
        bg.setColor(Color.parseColor("#10202E"))
        bg.setStroke(dp(2), Color.parseColor("#B44CFF"))
        button.background = bg
        button.setOnClickListener {
            if (running) stopCamera() else requestAndStart()
        }
        root.addView(
            button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
            )
        )

        val note = TextView(this)
        mono(note, 11f, "#7AA")
        note.gravity = Gravity.CENTER
        note.setPadding(0, dp(6), 0, 0)
        note.text = "Runs on this device. No video is saved or sent."
        root.addView(note)

        setContentView(root)

        try {
            engine = HandEngine(this)
        } catch (e: Exception) {
            statusText.text = "Model error: " + e.message
        }
        updatePermText()
    }

    override fun onResume() {
        super.onResume()
        updatePermText()
    }

    private fun hasCamera(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun updatePermText() {
        permText.text = if (hasCamera()) "Camera ✓" else "Camera ✕ (needed)"
    }

    private fun requestAndStart() {
        if (hasCamera()) {
            startCamera()
        } else {
            permLauncher.launch(Manifest.permission.CAMERA)
        }
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
                running = true
                frames = 0
                lastFpsTime = System.currentTimeMillis()
                button.text = "STOP CAMERA"
                statusText.text = "Show your hand"
            } catch (e: Exception) {
                statusText.text = "Camera error: " + e.message
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        provider?.unbindAll()
        running = false
        button.text = "START CAMERA"
        statusText.text = "Stopped"
        fingerText.text = " \n "
        skeleton.update(emptyList(), 3, 4)
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            val eng = engine
            if (eng == null) return
            val raw = proxy.toBitmap()
            val rot = proxy.imageInfo.rotationDegrees
            val matrix = Matrix()
            matrix.postRotate(rot.toFloat())
            val upright = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            val w = upright.width
            val h = upright.height
            val hands = eng.detect(upright)

            frames++
            val now = System.currentTimeMillis()
            if (now - lastFpsTime >= 1000) {
                fps = frames
                frames = 0
                lastFpsTime = now
            }

            val status: String
            val lines = StringBuilder()
            if (hands.isEmpty()) {
                status = "No hand | " + fps + " fps"
                lines.append("-\n-")
            } else {
                status = "TRACKING | hands: " + hands.size + " | " + fps + " fps"
                for ((i, hand) in hands.withIndex()) {
                    val pose = GestureEngine.analyze(hand, w, h)
                    lines.append("H").append(i + 1).append(" ").append(hand.label)
                    lines.append("  ").append(pose.fingerString()).append("\n")
                    lines.append("   pinch ").append(String.format("%.2f", pose.pinchRatio))
                    lines.append(" | ").append(pose.name()).append("\n")
                }
            }

            runOnUiThread {
                if (running) {
                    skeleton.update(hands, w, h)
                    statusText.text = status
                    fingerText.text = lines.toString()
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