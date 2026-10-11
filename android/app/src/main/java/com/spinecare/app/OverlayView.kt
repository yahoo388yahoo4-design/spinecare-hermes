package com.spinecare.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Draws pose skeleton + framing status + cue highlights over the camera preview. */
class OverlayView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null)
    : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 5f; style = Paint.Style.STROKE }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    var landmarks: List<Metrics.LM>? = null
    var missing: List<String> = emptyList()
    var ready = false

    // MediaPipe connections (subset relevant to spine/neck)
    private val conns = listOf(
        intArrayOf(Metrics.L_SHO, Metrics.R_SHO),
        intArrayOf(Metrics.L_SHO, Metrics.L_HIP), intArrayOf(Metrics.R_SHO, Metrics.R_HIP),
        intArrayOf(Metrics.L_HIP, Metrics.R_HIP),
        intArrayOf(Metrics.L_SHO, Metrics.L_ELB), intArrayOf(Metrics.R_SHO, Metrics.R_ELB),
        intArrayOf(Metrics.L_ELB, Metrics.L_WR), intArrayOf(Metrics.R_ELB, Metrics.R_WR),
        intArrayOf(Metrics.L_HIP, Metrics.L_KNEE), intArrayOf(Metrics.R_HIP, Metrics.R_KNEE),
        intArrayOf(Metrics.L_KNEE, Metrics.L_ANK), intArrayOf(Metrics.R_KNEE, Metrics.R_ANK),
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val lms = landmarks ?: return
        val w = width.toFloat(); val h = height.toFloat()
        val color = if (ready) Color.parseColor("#34D399") else Color.parseColor("#FBBF24")
        paint.color = color
        for ((a, b) in conns) {
            if (a < lms.size && b < lms.size) {
                val la = lms[a]; val lb = lms[b]
                if (la.visibility > 0.5f && lb.visibility > 0.5f)
                    canvas.drawLine(la.x * w, la.y * h, lb.x * w, lb.y * h, paint)
            }
        }
        // neck line ear-mid -> shoulder-mid, highlighted
        if (lms.size > Metrics.R_HIP) {
            val ear = lms[Metrics.L_EAR]; val sho = lms[Metrics.L_SHO]
            paint.color = if (ready) Color.parseColor("#38BDF8") else Color.parseColor("#F87171")
            paint.strokeWidth = 8f
            canvas.drawLine(ear.x * w, ear.y * h, sho.x * w, sho.y * h, paint)
            paint.strokeWidth = 5f
        }
        dotPaint.color = color
        for ((i, lm) in lms.withIndex()) {
            if (lm.visibility > 0.5f) {
                val isMissing = missing.any { nameMatches(it, i) }
                dotPaint.color = if (isMissing) Color.parseColor("#F87171") else color
                canvas.drawCircle(lm.x * w, lm.y * h, if (isMissing) 14f else 7f, dotPaint)
            }
        }
    }

    private fun nameMatches(name: String, idx: Int): Boolean = when (name) {
        "lear" -> idx == Metrics.L_EAR; "rear" -> idx == Metrics.R_EAR
        "lsho" -> idx == Metrics.L_SHO; "rsho" -> idx == Metrics.R_SHO
        "lhip" -> idx == Metrics.L_HIP; "rhip" -> idx == Metrics.R_HIP
        "lkne" -> idx == Metrics.L_KNEE; "rkne" -> idx == Metrics.R_KNEE
        else -> false
    }
}
