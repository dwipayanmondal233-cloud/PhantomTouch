package com.example.phantomtouch

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker

class Hand(
    val x: FloatArray,
    val y: FloatArray,
    val label: String
)

class HandEngine(context: Context) {

    private val landmarker: HandLandmarker

    init {
        val base = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.IMAGE)
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build()
        landmarker = HandLandmarker.createFromOptions(context, options)
    }

    fun detect(bitmap: Bitmap): List<Hand> {
        val result = landmarker.detect(BitmapImageBuilder(bitmap).build())
        val out = ArrayList<Hand>()
        val all = result.landmarks()
        val sides = result.handedness()
        for (i in all.indices) {
            val pts = all[i]
            val xs = FloatArray(pts.size)
            val ys = FloatArray(pts.size)
            for (j in pts.indices) {
                xs[j] = pts[j].x()
                ys[j] = pts[j].y()
            }
            var name = "?"
            if (i < sides.size && sides[i].isNotEmpty()) {
                val raw = sides[i][0].categoryName()
                // Frames are not mirrored, so MediaPipe's side is flipped.
                name = if (raw == "Left") "Right" else "Left"
            }
            out.add(Hand(xs, ys, name))
        }
        return out
    }

    fun close() {
        landmarker.close()
    }
}