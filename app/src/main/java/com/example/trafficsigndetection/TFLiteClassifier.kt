package com.example.trafficsigndetection

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
data class ClassificationResult(val label: String, val prob: Float)

class TFLiteClassifier(private val context: Context) {
    private val interpreter: Interpreter
    private val spec: TFLiteSpec
    private val inputSize: Int
    private val labels: List<String>
    // Đặt true nếu output là logits (cần softmax); nếu đã là prob 0..1 thì để false
    private val applySoftmax = true

    init {
        spec = inspectTFLite(context, "traffic_sign_classifier_final2.tflite")

        interpreter = Interpreter(
            loadModelFile(context, "traffic_sign_classifier_final2.tflite"),
            Interpreter.Options().apply { setNumThreads(4) }
        )

        inputSize = spec.inputShape[1]   // giả định H=W

        // Đọc labels 1 lần, lọc BOM + trim + bỏ dòng rỗng
        val loadedLabels = context.assets.open("class.txt")
            .bufferedReader(Charsets.UTF_8)
            .lineSequence()
            .map { it.removePrefix("\uFEFF").trim() }
            .filter { it.isNotEmpty() }
            .toList()

        require(loadedLabels.size == spec.numClasses) {
            "Số nhãn trong class.txt (${loadedLabels.size}) không khớp với số lớp trong model (${spec.numClasses})"
        }

        labels = loadedLabels
        android.util.Log.i("CLS", "classes=${spec.numClasses}, labels=${labels}")
    }


    fun classify(cropped: Bitmap): ClassificationResult {
        val inputImg = centerCropResize(cropped, inputSize)
        val input = makeInputBuffer(inputImg)
        val out: Any = when (spec.outputDtype) {
            "FLOAT32" -> Array(1) { FloatArray(spec.numClasses) }
            "UINT8", "INT8" -> Array(1) { ByteArray(spec.numClasses) }
            else -> error("Unsupported output dtype: ${spec.outputDtype}")
        }
        interpreter.run(input, out)

        val probs: FloatArray = when (out) {
            is Array<*> -> when (val v = out[0]) {
                is FloatArray -> {
                    if (applySoftmax && (v.any { it < 0f } || v.any { it > 1f })) softmax(v) else v
                }
                is ByteArray -> {
                    val arr = FloatArray(v.size)
                    val scale = spec.outputScale ?: 1f
                    val zero = spec.outputZero ?: 0
                    for (i in v.indices) arr[i] = (v[i].toInt() - zero) * scale
                    if (applySoftmax) softmax(arr) else arr
                }
                else -> error("Unknown output array")
            }
            else -> error("Unknown output")
        }

        val top = probs.indices.maxByOrNull { probs[it] } ?: 0
        val label = labels.getOrElse(top) { "class_$top" }
        val prob = probs[top]
        return ClassificationResult(label, prob)
    }

    private fun makeInputBuffer(bm: Bitmap): ByteBuffer {
        val w = inputSize; val h = inputSize
        return when (spec.inputDtype) {
            "FLOAT32" -> {
                val buf = ByteBuffer.allocateDirect(1 * w * h * 3 * 4).order(ByteOrder.nativeOrder())
                val px = IntArray(w * h)
                bm.getPixels(px, 0, w, 0, 0, w, h)
                for (p in px) {
                    val r = (p shr 16 and 0xFF)
                    val g = (p shr 8 and 0xFF)
                    val b = (p and 0xFF)
                    // CHỌN 1: [0,1]
                    buf.putFloat(r / 255f); buf.putFloat(g / 255f); buf.putFloat(b / 255f)
                    // CHỌN 2: [-1,1]  (bật cái nào đúng với training của bạn)
//                    buf.putFloat(r / 127.5f - 1f); buf.putFloat(g / 127.5f - 1f); buf.putFloat(b / 127.5f - 1f)
                }
                buf.rewind(); buf
            }
            "UINT8", "INT8" -> {
                val buf = ByteBuffer.allocateDirect(1 * w * h * 3).order(ByteOrder.nativeOrder())
                val px = IntArray(w * h)
                bm.getPixels(px, 0, w, 0, 0, w, h)
                for (p in px) {
                    buf.put((p shr 16 and 0xFF).toByte())
                    buf.put((p shr 8 and 0xFF).toByte())
                    buf.put((p and 0xFF).toByte())
                }
                buf.rewind(); buf
            }
            else -> error("Unsupported dtype: ${spec.inputDtype}")
        }
    }

    fun close() = interpreter.close()
}
