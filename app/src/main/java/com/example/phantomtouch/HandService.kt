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
    }

    private val controller = CursorController()
    private var engine: HandEngine? = null
    private var overlay: CursorOverlay? = null
    private var provider: ProcessCameraProvider? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var lastMoveT = 0L
    private var lastPx = 0f
    private var lastPy = 0f
    private var dragging = false
    private var dragSX = 0f
    private var dragSY = 0f

    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (running) return START_NOT_STICKY
        startFg()
        try {
            engine = HandEngine(this)
            val ov = CursorOverlay(this)
            ov.show()
            overlay = ov
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

            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(dm)
            val sw = dm.widthPixels
            val sh = dm.heightPixels
            controller.screenW = sw.toFloat()
            controller.screenH = sh.toFloat()

            val now = System.currentTimeMillis()
            val f = controller.process(hand, w, h, now)
            main.post { applyFrame(f, sw, sh, now) }
        } catch (e: Exception) {
        } finally {
            proxy.close()
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

        val acc = PhantomAccess.instance
        when (f.event) {
            CursorController.EV_CLICK -> {
                ov.slam(now)
                acc?.tap(px(f.evX), py(f.evY))
            }
            CursorController.EV_DOUBLE -> {
                ov.slam(now)
                acc?.doubleTap(px(f.evX), py(f.evY))
            }
            CursorController.EV_DRAG_START -> {
                ov.slam(now)
                dragging = true
                dragSX = px(f.evX)
                dragSY = py(f.evY)
            }
            CursorController.EV_DRAG_END -> {
                if (dragging) {
                    dragging = false
                    acc?.dragOrHold(dragSX, dragSY, px(f.evX), py(f.evY))
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
        overlay?.remove()
        overlay = null
        super.onDestroy()
    }
}