package com.example.trafficsigndetection

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import kotlin.math.max

class DetectionOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var detections: List<Tf1SsdDetector.Detection> = emptyList()
    var extraLabels: List<String> = emptyList() // NEW: nhãn classifier khớp theo index
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
        typeface = Typeface.SANS_SERIF
        isSubpixelText = true
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x80000000.toInt() // nền đen 50% cho text
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (detections.isEmpty() || srcW <= 0 || srcH <= 0) return

        val scale = max(width / srcW.toFloat(), height / srcH.toFloat())
        val dx = (width - srcW * scale) / 2f
        val dy = (height - srcH * scale) / 2f

        detections.forEachIndexed { i, d ->
            val l = d.xmin * srcW * scale + dx
            val t = d.ymin * srcH * scale + dy
            val r = d.xmax * srcW * scale + dx
            val b = d.ymax * srcH * scale + dy

            canvas.drawRect(l, t, r, b, boxPaint)

            val cls = extraLabels.getOrNull(i)
            val text = if (!cls.isNullOrBlank())
                cls
            else
                ""

            val bounds = Rect()
            textPaint.getTextBounds(text, 0, text.length, bounds)
            val pad = 8f
            val tx = l
            val ty = max(t - 8f, 24f)

            canvas.drawRect(
                tx - pad,
                ty - bounds.height() - pad,
                tx + bounds.width() + pad,
                ty + pad,
                bgPaint
            )
            canvas.drawText(text, tx, ty, textPaint)

            Log.d("DET", text)

            Log.d("DET", "${d.label} ${(d.score * 100).toInt()}% [$l,$t,$r,$b]")
        }
        Log.d("DET", "count=${detections.size}")
    }
}
