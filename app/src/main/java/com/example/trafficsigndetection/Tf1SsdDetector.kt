package com.example.trafficsigndetection
// build.gradle (app)
// dependencies { implementation "org.tensorflow:tensorflow-android:1.15.0" }

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.contrib.android.TensorFlowInferenceInterface
import java.util.ArrayList

class Tf1SsdDetector(context: Context) {

    private val tf: TensorFlowInferenceInterface
    private val labels: List<String>

    init {
        // Model & labels nằm trong app/src/main/assets/
        tf = TensorFlowInferenceInterface(context.assets, "frozen_inference_graph.pb")
        labels = context.assets.open("labels.txt").bufferedReader(Charsets.UTF_8).readLines()
    }

    fun close() { /* nothing */ }

    data class Detection(
        val xmin: Float, val ymin: Float, val xmax: Float, val ymax: Float,
        val score: Float, val classId: Int, val label: String
    )

    fun detect(src: Bitmap, minScore: Float = 0.3f, maxResults: Int = 20): List<Detection> {
        val w = src.width
        val h = src.height

        // Chuẩn bị input UINT8 theo NHWC -> byte[]
        val input = bitmapToNHWCUint8Array(src)

        // Feed vào "image_tensor" [1, H, W, 3] (DT_UINT8)
        tf.feed("image_tensor", input, 1L, h.toLong(), w.toLong(), 3L)

        // Chạy graph
        val fetches = arrayOf("detection_boxes", "detection_scores", "detection_classes", "num_detections")
        tf.run(fetches, /*enableStats=*/false)

        // Lấy output
        val outBoxes = FloatArray(100 * 4)   // [1,100,4] -> phẳng thành 400 phần tử
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

            // Lưu ý: boxes là normalized [0..1]
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
                out[bi++] = ((p shr 16) and 0xFF).toByte() // R
                out[bi++] = ((p shr 8) and 0xFF).toByte()  // G
                out[bi++] = (p and 0xFF).toByte()          // B
            }
        }
        return out
    }
}
