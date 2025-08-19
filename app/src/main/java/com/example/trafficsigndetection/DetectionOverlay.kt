package com.example.trafficsigndetection


import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import android.util.Log


class DetectionOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var detections: List<Tf1SsdDetector.Detection> = emptyList()
    private var srcW = 1
    private var srcH = 1

    fun setSourceInfo(w: Int, h: Int) {
        srcW = w.coerceAtLeast(1)
        srcH = h.coerceAtLeast(1)
    }

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.GREEN
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textSize = 36f
        color = Color.GREEN
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (detections.isEmpty() || srcW <= 0 || srcH <= 0) return

        // FILL_CENTER -> phóng theo max và căn giữa
        val scale = max(width / srcW.toFloat(), height / srcH.toFloat())
        val dx = (width - srcW * scale) / 2f
        val dy = (height - srcH * scale) / 2f

        for (d in detections) {
            // GIẢ ĐỊNH: xmin..xmax, ymin..ymax là toạ độ chuẩn hoá [0..1]
            val l = d.xmin * srcW * scale + dx
            val t = d.ymin * srcH * scale + dy
            val r = d.xmax * srcW * scale + dx
            val b = d.ymax * srcH * scale + dy

            canvas.drawRect(l, t, r, b, boxPaint)
            canvas.drawText(
                "${d.label} ${(d.score * 100).toInt()}%",
                l,
                max(t - 8f, 24f),
                textPaint
            )

            // (tuỳ chọn) log từng detection
             Log.d("DET", "${d.label} ${(d.score*100).toInt()}% [$l,$t,$r,$b]")
        }

        // (tuỳ chọn) log tổng quan khung hình
         Log.d("DET", "count=${detections.size}")
    }
}

