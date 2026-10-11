package com.spinecare.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.spinecare.app.databinding.ActivityExerciseBinding
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

class ExerciseActivity : AppCompatActivity() {
    private lateinit var b: ActivityExerciseBinding
    private lateinit var exercise: Exercise
    private lateinit var poseEngine: PoseEngine
    private lateinit var store: LocalStore
    private lateinit var repEngine: RepEngine
    private var server: ServerClient? = null
    private var mode = ServerConfig.MODE_AUTO
    private var serverMode = false
    private var running = false
    private var lastFrameT = 0L
    private var lastServerSend = 0L
    private var analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var lastCue = ""
    private var lastMetrics: Map<String, Double> = emptyMap()
    private var lastFramingMissing: List<String> = emptyList()
    private var lastFramingReady = false
    private var serverReps = 0
    private var serverCues: Map<String, Int> = emptyMap()
    private var framesProcessed = 0

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.CAMERA] == true) startCamera()
        else Toast.makeText(this, "Camera permission required for guided exercises", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityExerciseBinding.inflate(layoutInflater)
        setContentView(b.root)
        store = LocalStore(this)
        poseEngine = PoseEngine(this)

        val exId = intent.getStringExtra("exercise_id") ?: "chin_tuck"
        exercise = Plan.byId(exId) ?: Plan.exercises.first()
        repEngine = RepEngine(exercise.repLow, exercise.repHigh)

        b.tvExercise.text = "${exercise.name}  —  ${exercise.view.uppercase()} view\n${exercise.description}"
        b.btnStart.setOnClickListener { startSession() }
        b.btnStop.setOnClickListener { stopAndSave() }
        b.btnMode.setOnClickListener {
            mode = (mode + 1) % 3
            applyMode()
        }
        mode = ServerConfig.mode(this)
        applyMode()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            permLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        else startCamera()
    }

    private fun applyMode() {
        serverMode = mode != ServerConfig.MODE_ON_DEVICE
        b.btnMode.text = when (mode) {
            ServerConfig.MODE_ON_DEVICE -> "Mode: On-device (offline)"
            ServerConfig.MODE_SERVER -> "Mode: GPU server (required)"
            else -> "Mode: Auto (server if reachable)"
        }
        if (serverMode) connectServer() else { server?.close(); server = null }
    }

    private fun startCamera() {
        val provider = ProcessCameraProvider.getInstance(this).get()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(b.preview.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(analysisExecutor, ::analyze) }
        provider.unbindAll()
        provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
    }

    private fun analyze(proxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (!running || now - lastFrameT < ServerConfig.frameIntervalMs(this)) { proxy.close(); return }
        lastFrameT = now
        try {
            var bmp = proxy.toBitmap()
            val rot = proxy.imageInfo.rotationDegrees
            if (rot != 0) {
                val m = android.graphics.Matrix(); m.postRotate(rot.toFloat())
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            // Local pose always runs for the overlay + fallback
            val res = poseEngine.detect(bmp)
            framesProcessed++
            val lms = res.landmarks
            if (lms != null) {
                val metrics = Metrics.compute(lms)
                lastMetrics = metrics
                b.overlay.post {
                    b.overlay.landmarks = lms
                    b.overlay.missing = lastFramingMissing
                    b.overlay.ready = lastFramingReady
                    b.overlay.invalidate()
                }
                // AUTO/ON_DEVICE: local scoring always runs; in SERVER-required
                // mode local scoring is skipped only while the link is healthy.
                val serverHealthy = serverMode && server?.connected == true
                if (!serverHealthy || mode != ServerConfig.MODE_SERVER) processLocal(metrics, now)
                if (serverHealthy && now - lastServerSend > ServerConfig.serverFrameIntervalMs(this)) {
                    lastServerSend = now
                    server?.sendFrame(bmp, now, null)
                }
            }
        } catch (e: Exception) {
            // keep the loop alive on transient frame errors
        } finally {
            proxy.close()
        }
    }

    private fun framingCheck(): Pair<Boolean, List<String>> {
        val lms = b.overlay.landmarks ?: return false to exercise.requiredLandmarks
        val missing = ArrayList<String>()
        for (name in exercise.requiredLandmarks) {
            val idx = when (name) {
                "lear" -> Metrics.L_EAR; "rear" -> Metrics.R_EAR; "lsho" -> Metrics.L_SHO
                "rsho" -> Metrics.R_SHO; "lhip" -> Metrics.L_HIP; "rhip" -> Metrics.R_HIP
                "lkne" -> Metrics.L_KNEE; else -> -1
            }
            if (idx < 0 || idx >= lms.size || lms[idx].visibility < exercise.minConf) missing.add(name)
        }
        return missing.isEmpty() to missing
    }

    private fun processLocal(metrics: Map<String, Double>, now: Long) {
        val (ready, missing) = framingCheck()
        lastFramingReady = ready; lastFramingMissing = missing
        if (!ready) {
            postCue("Camera framing: " + missing.joinToString { FramingGuide.fixFor("$it:low_conf") })
            return
        }
        val sig = metrics[exercise.signal]
        val done = repEngine.update(sig, now)
        if (done) { vibrate(); postCue("Rep ${repEngine.reps}/${exercise.targetReps} ✓") }
        // form rules
        for (rule in exercise.formRules) {
            val v = metrics[rule.metric] ?: continue
            if (abs(v) > rule.maxAbs) { postCue(rule.cue); break }
        }
        b.tvReps.post { b.tvReps.text = "${repEngine.reps}/${exercise.targetReps}" }
        b.tvMetrics.post { b.tvMetrics.text = fmtMetrics(metrics) + "\nsignal=${fmt1(sig)} phase=${repEngine.phase} frames=$framesProcessed" }
    }

    private fun connectServer() {
        val url = ServerConfig.url(this)
        server = ServerClient(url).apply {
            connect(object : ServerClient.Listener {
                override fun onPose(msg: JSONObject) {
                    val framing = msg.getJSONObject("framing")
                    lastFramingReady = framing.getBoolean("ready")
                    lastFramingMissing = JSONArrayToList(framing.getJSONArray("missing"))
                    if (!lastFramingReady) {
                        val issues = JSONArrayToList(framing.getJSONArray("issues"))
                        postCue("Camera: " + issues.joinToString { FramingGuide.fixFor(it) })
                    }
                    serverReps = msg.getInt("reps")
                    b.tvReps.post { b.tvReps.text = "$serverReps/${msg.getInt("target_reps")}" }
                    val cues = msg.getJSONArray("cues")
                    if (cues.length() > 0) postCue(cues.getJSONObject(0).getString("cue"))
                    if (msg.getBoolean("rep_just_done")) vibrate()
                    val m = msg.getJSONObject("metrics")
                    b.tvMetrics.post {
                        b.tvMetrics.text = "GPU ${msg.getInt("latency_ms")}ms  reps=$serverReps\n" +
                            m.keys().asSequence().joinToString("  ") { k -> "$k=${m.optString(k, "-")}" }
                    }
                }
                override fun onSessionStarted(exercise: JSONObject) {}
                override fun onSessionSaved(summary: JSONObject) {
                    serverCues = HashMap()
                    val c = summary.getJSONObject("cue_counts")
                    for (k in c.keys()) serverCues[k] = c.getInt(k)
                }
                override fun onStatus(connected: Boolean) {
                    runOnUiThread {
                        if (!connected && serverMode) {
                            Toast.makeText(this@ExerciseActivity, "GPU server unreachable — using on-device", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        }
    }

    private fun JSONArrayToList(a: org.json.JSONArray) = (0 until a.length()).map { a.getString(it) }

    private fun startSession() {
        running = true
        repEngine = RepEngine(exercise.repLow, exercise.repHigh)
        framesProcessed = 0
        if (serverMode) { server?.connect(object : ServerClient.Listener {}) ; server?.startSession(exercise.id) }
        postCue("Calibrate: adopt neutral posture, then begin. Stop on: ${exercise.stopConditions.joinToString()}")
    }

    private fun stopAndSave() {
        running = false
        if (serverMode && server?.connected == true) {
            server?.endSession()   // server persists its own session row
        }
        store.saveSession(exercise.id, repEngine.reps, lastMetrics, serverCues, framesProcessed,
            if (serverMode) "gpu-server" else "on-device")
        Toast.makeText(this, "Session saved (${repEngine.reps} reps)", Toast.LENGTH_SHORT).show()
    }

    private fun postCue(text: String) {
        if (text == lastCue) return
        lastCue = text
        b.tvCue.post { b.tvCue.text = text }
    }

    private fun vibrate() {
        val v = if (Build.VERSION.SDK_INT >= 31)
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun fmtMetrics(m: Map<String, Double>) =
        m.entries.joinToString("  ") { "${it.key}=${fmt1(it.value)}" }
    private fun fmt1(v: Double?) = if (v == null || v.isNaN()) "-" else String.format("%.1f", v)

    override fun onDestroy() {
        super.onDestroy(); poseEngine.close(); server?.close(); analysisExecutor.shutdown()
    }
}
