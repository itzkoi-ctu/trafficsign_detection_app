package com.example.trafficsigndetection.draw

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class OverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val boxes = mutableListOf<RectF>()
    private val labels = mutableListOf<String>()
    private var imageWidth = 0
    private var imageHeight = 0

    fun setResults(newBoxes: List<RectF>, newLabels: List<String>, imgWidth: Int, imgHeight: Int) {
        boxes.clear()
        boxes.addAll(newBoxes)
        labels.clear()
        labels.addAll(newLabels)
        imageWidth = imgWidth
        imageHeight = imgHeight
        invalidate()
    }

    private val boxPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val textPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 40f
        typeface = Typeface.DEFAULT_BOLD
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (imageWidth == 0 || imageHeight == 0) return

        // Fit-center lên canvas
        val scale = min(canvas.width / imageWidth.toFloat(), canvas.height / imageHeight.toFloat())
        val dx = (canvas.width  - imageWidth  * scale) / 2f
        val dy = (canvas.height - imageHeight * scale) / 2f

        for (i in boxes.indices) {
            val box = boxes[i]
            val label = labels[i]

            val left   = box.left   * scale + dx
            val top    = box.top    * scale + dy
            val right  = box.right  * scale + dx
            val bottom = box.bottom * scale + dy

            canvas.drawRect(left, top, right, bottom, boxPaint)
            canvas.drawText(label, left, top - 10, textPaint)
        }
    }

}

