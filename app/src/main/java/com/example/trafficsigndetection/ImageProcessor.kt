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
        val targetSize = 640
        val input = Array(1) { Array(targetSize) { Array(targetSize) { FloatArray(3) } } }

        // 1. Giữ tỷ lệ gốc
        val scale = minOf(
            targetSize.toFloat() / bitmap.width,
            targetSize.toFloat() / bitmap.height
        )

        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()

        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

        // 2. Tạo ảnh mới 640x640 với nền đen
        val paddedBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(paddedBitmap)
        canvas.drawColor(Color.BLACK) // hoặc Color.rgb(114, 114, 114) nếu theo YOLOv5
        val dx = (targetSize - newWidth) / 2
        val dy = (targetSize - newHeight) / 2
        canvas.drawBitmap(resizedBitmap, dx.toFloat(), dy.toFloat(), null)

        // 3. Chuẩn hóa về 0-1
        for (y in 0 until targetSize) {
            for (x in 0 until targetSize) {
                val pixel = paddedBitmap.getPixel(x, y)
                input[0][y][x][0] = Color.red(pixel) / 255.0f
                input[0][y][x][1] = Color.green(pixel) / 255.0f
                input[0][y][x][2] = Color.blue(pixel) / 255.0f
            }
        }
        return input
    }

//    fun preprocess(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
//        val resized = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
//        val input = Array(1) { Array(640) { Array(640) { FloatArray(3) } } }
//
//        for (y in 0 until 640) {
//            for (x in 0 until 640) {
//                val pixel = resized.getPixel(x, y)
//                input[0][y][x][0] = Color.red(pixel) / 255.0f
//                input[0][y][x][1] = Color.green(pixel) / 255.0f
//                input[0][y][x][2] = Color.blue(pixel) / 255.0f
//            }
//        }
//        return input
//    }

}
