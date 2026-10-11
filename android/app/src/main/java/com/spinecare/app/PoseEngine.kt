package com.spinecare.app

import android.content.Context
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.posedetection.PoseDetection
import com.google.mediapipe.tasks.vision.posedetection.PoseDetectorOptions

/** On-device pose via MediaPipe Tasks (BlazePose 33 landmarks, GPU delegate). */
class PoseEngine(context: Context) {
    private val detector: PoseDetection

    init {
        val base = BaseOptions.builder().setModelAssetPath("pose_landmarker_full.task").build()
        val opts = PoseDetectorOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.SINGLE_IMAGE)
            .setMinDetectionConfidence(0.5f)
            .build()
        detector = PoseDetection.createFromOptions(context, opts)
    }

    data class Result(val landmarks: List<Metrics.LM>?, val latencyMs: Long)

    fun detect(bitmap: android.graphics.Bitmap): Result {
        val t0 = System.nanoTime()
        val mpImg = BitmapImageBuilder(bitmap).build()
        val res = detector.detect(mpImg)
        val ms = (System.nanoTime() - t0) / 1_000_000
        val lms = res.landmarks().firstOrNull()?.map {
            Metrics.LM(it.x(), it.y(), it.z(), it.visibility())
        }
        return Result(lms, ms)
    }

    fun close() = detector.close()
}
