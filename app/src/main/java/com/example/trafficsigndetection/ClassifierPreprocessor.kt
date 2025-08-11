package com.example.trafficsigndetection

import android.graphics.Bitmap
import android.graphics.Color

object ClassifierPreprocessor {
    fun preprocess(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val size = 48
        val input = Array(1) { Array(size) { Array(size) { FloatArray(3) } } }
        val resized = Bitmap.createScaledBitmap(bitmap, size, size, true)

        for (y in 0 until size) {
            for (x in 0 until size) {
                val pixel = resized.getPixel(x, y)
                input[0][y][x][0] = Color.red(pixel) / 255f
                input[0][y][x][1] = Color.green(pixel) / 255f
                input[0][y][x][2] = Color.blue(pixel) / 255f
            }
        }
        return input
    }
}
