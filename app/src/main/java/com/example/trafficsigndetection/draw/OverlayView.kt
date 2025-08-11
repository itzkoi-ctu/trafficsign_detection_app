package com.example.trafficsigndetection.draw

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val boxes = mutableListOf<RectF>()
    private val labels = mutableListOf<String>()
    private val paintBox = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val paintText = Paint().apply {
        color = Color.GREEN
        textSize = 36f
        style = Paint.Style.FILL
    }

    fun setResults(newBoxes: List<RectF>, newLabels: List<String>) {
        boxes.clear()
        boxes.addAll(newBoxes)
        labels.clear()
        labels.addAll(newLabels)
        invalidate() // Vẽ lại
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
        for (i in boxes.indices) {
            val box = boxes[i]
            val label = labels[i]
            canvas.drawRect(box, boxPaint)
            canvas.drawText(label, box.left, box.top - 10, textPaint)
        }
    }

}
