package com.example.trafficsigndetection.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

public class ImageProcessor {
    companion object {
        const val INPUT_SIZE = 640

        fun preprocessImage(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
            // Resize về đúng kích thước model (ví dụ YOLOv5 = 640x640)
            val inputSize = 640
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

            // Chuyển sang mảng float và chuẩn hóa [0,1]
            val input = Array(1) { Array(inputSize) { Array(inputSize) { FloatArray(3) } } }

            for (y in 0 until inputSize) {
                for (x in 0 until inputSize) {
                    val pixel = resizedBitmap.getPixel(x, y)
                    input[0][y][x][0] = (Color.red(pixel) / 255.0f)
                    input[0][y][x][1] = (Color.green(pixel) / 255.0f)
                    input[0][y][x][2] = (Color.blue(pixel) / 255.0f)
                }
            }
            return input
        }

        fun bitmapToFloatBuffer(bitmap: Bitmap): ByteBuffer {
            val imgData = ByteBuffer.allocateDirect(1 * 640 * 640 * 3 * 4)
            imgData.order(ByteOrder.nativeOrder())
            imgData.rewind()

            val intValues = IntArray(640 * 640)
            bitmap.getPixels(intValues, 0, 640, 0, 0, 640, 640)

            var pixel = 0
            for (y in 0 until 640) {
                for (x in 0 until 640) {
                    val value = intValues[pixel++]
                    imgData.putFloat(((value shr 16 and 0xFF) / 255.0f))
                    imgData.putFloat(((value shr 8 and 0xFF) / 255.0f))
                    imgData.putFloat(((value and 0xFF) / 255.0f))
                }
            }
            return imgData
        }

        /**
         * Letterbox resize để giữ tỉ lệ giống YOLOv5 gốc
         */
        fun letterboxResize(bitmap: Bitmap, size: Int): Pair<Bitmap, FloatArray> {
            val srcWidth = bitmap.width
            val srcHeight = bitmap.height
            val scale = min(size.toFloat() / srcWidth, size.toFloat() / srcHeight)
            val newWidth = (srcWidth * scale).toInt()
            val newHeight = (srcHeight * scale).toInt()

            val resized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val dx = (size - newWidth) / 2f
            val dy = (size - newHeight) / 2f
            canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(resized, dx, dy, null)

            return output to floatArrayOf(scale, dx, dy)
        }



        private fun resizeWithPadding(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
            val originalWidth = bitmap.width
            val originalHeight = bitmap.height

            // Tính scale factor để fit vào target size
            val scale = minOf(
                targetWidth.toFloat() / originalWidth,
                targetHeight.toFloat() / originalHeight
            )

            val scaledWidth = (originalWidth * scale).toInt()
            val scaledHeight = (originalHeight * scale).toInt()

            // Resize bitmap
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)

            // Tạo bitmap mới với padding
            val paddedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(paddedBitmap)

            // Fill với màu xám (128, 128, 128) - chuẩn YOLOv5
            canvas.drawColor(android.graphics.Color.rgb(128, 128, 128))

            // Vẽ scaled bitmap vào center
            val left = (targetWidth - scaledWidth) / 2
            val top = (targetHeight - scaledHeight) / 2
            canvas.drawBitmap(scaledBitmap, left.toFloat(), top.toFloat(), null)

            scaledBitmap.recycle()
            return paddedBitmap
        }

        fun getPaddingInfo(originalWidth: Int, originalHeight: Int): PaddingInfo {
            val scale = minOf(
                INPUT_SIZE.toFloat() / originalWidth,
                INPUT_SIZE.toFloat() / originalHeight
            )

            val scaledWidth = (originalWidth * scale).toInt()
            val scaledHeight = (originalHeight * scale).toInt()

            val padLeft = (INPUT_SIZE - scaledWidth) / 2
            val padTop = (INPUT_SIZE - scaledHeight) / 2

            return PaddingInfo(scale, padLeft, padTop, scaledWidth, scaledHeight)
        }
    }

    data class PaddingInfo(
        val scale: Float,
        val padLeft: Int,
        val padTop: Int,
        val scaledWidth: Int,
        val scaledHeight: Int
    )
}
