
package com.example.trafficsigndetection

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.contrib.android.TensorFlowInferenceInterface

class Tf1SsdDetector(context: Context) {

    private val tf: TensorFlowInferenceInterface
    private val labels: List<String>

    // Tên input & kích thước model SSD (chỉnh nếu model của bạn khác)
    private val INPUT_TENSOR = "image_tensor"
    private val INPUT_W = 300
    private val INPUT_H = 300

    init {
        tf = TensorFlowInferenceInterface(context.assets, "frozen_inference_graph.pb")
        labels = context.assets.open("labels.txt").bufferedReader(Charsets.UTF_8).readLines()
    }

    fun close() { /* nothing */ }

    data class Detection(
        val xmin: Float, val ymin: Float, val xmax: Float, val ymax: Float,
        val score: Float, val classId: Int, val label: String
    )

    fun detect(src: Bitmap, minScore: Float = 0.4f, maxResults: Int = 20): List<Detection> {
        // Resize về input của model
        val resized = Bitmap.createScaledBitmap(src, INPUT_W, INPUT_H, true)
        val input = bitmapToNHWCUint8Array(resized)

        tf.feed(INPUT_TENSOR, input, 1L, INPUT_H.toLong(), INPUT_W.toLong(), 3L)
        tf.run(arrayOf("detection_boxes", "detection_scores", "detection_classes", "num_detections"), false)

        val outBoxes = FloatArray(100 * 4)
        val outScores = FloatArray(100)
        val outClasses = FloatArray(100)
        val outNum = FloatArray(1)

        tf.fetch("detection_boxes", outBoxes)
        tf.fetch("detection_scores", outScores)
        tf.fetch("detection_classes", outClasses)
        tf.fetch("num_detections", outNum)

        val num = outNum[0].toInt().coerceAtMost(100)

        val results = ArrayList<Detection>(num)
        for (i in 0 until num) {
            val score = outScores[i]
            if (score < minScore) continue
            val base = i * 4
            val ymin = outBoxes[base + 0]
            val xmin = outBoxes[base + 1]
            val ymax = outBoxes[base + 2]
            val xmax = outBoxes[base + 3]
            val cid = outClasses[i].toInt()
            val label = labelFor(cid)
            results.add(Detection(xmin, ymin, xmax, ymax, score, cid, label))
            if (results.size >= maxResults) break
        }
        return results
    }

    private fun labelFor(classId: Int): String {
        val idx = (classId - 1).coerceIn(0, labels.size - 1)
        return labels[idx]
    }

    private fun bitmapToNHWCUint8Array(bm: Bitmap): ByteArray {
        val w = bm.width
        val h = bm.height
        val out = ByteArray(1 * h * w * 3)
        val pixels = IntArray(w * h)
        bm.getPixels(pixels, 0, w, 0, 0, w, h)
        var pi = 0
        var bi = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = pixels[pi++]
                out[bi++] = ((p shr 16) and 0xFF).toByte()
                out[bi++] = ((p shr 8) and 0xFF).toByte()
                out[bi++] = (p and 0xFF).toByte()
            }
        }
        return out
    }
}
