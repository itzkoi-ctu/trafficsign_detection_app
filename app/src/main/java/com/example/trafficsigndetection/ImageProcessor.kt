package com.example.trafficsigndetection

import android.graphics.*

object ImageProcessor {

    fun toGrayscale(bitmap: Bitmap): Bitmap {
        val grayscale = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(grayscale)
        val paint = Paint()
        val matrix = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return grayscale
    }

    fun preprocess(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val resized = Bitmap.createScaledBitmap(toGrayscale(bitmap), 32, 32, true)
        val input = Array(1) { Array(32) { Array(32) { FloatArray(1) } } }

        for (y in 0 until 32) {
            for (x in 0 until 32) {
                val pixel = resized.getPixel(x, y)
                val gray = Color.red(pixel) / 255.0f
                input[0][y][x][0] = gray
            }
        }
        return input
    }
}
