package com.example.trafficsigndetection


import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.*
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.exp

class OnnxClassifier(
    ctx: Context,
    private val modelAssetName: String,
    classFileName: String,
    private val normalizeMode: Normalize = Normalize.ZERO_TO_ONE, // đổi nếu bạn train theo [-1,1]
) {
    enum class Normalize { ZERO_TO_ONE, MINUS1_TO1 }

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String
    private val inputH: Int
    private val inputW: Int
    private val labels: List<String>

    init {
        // 1) copy onnx từ assets ra file
        val onnxFile = copyAssetToFile(ctx, modelAssetName)
        // 2) tạo session + chọn EP (NNAPI nếu có)
        val opts = OrtSession.SessionOptions()
        try { opts.addNnapi() } catch (_: Throwable) { /* fallback CPU */ }
        opts.setIntraOpNumThreads(2)
        session = env.createSession(onnxFile.absolutePath, opts)

        // 3) đọc input tensor shape
        val inInfo = session.inputInfo.values.first().info as TensorInfo
        // Expect: Float32[1, C/H, H, W] hoặc [1, H, W, 3]; lấy H,W, tên
        inputName = session.inputNames.first()
        val shape = inInfo.shape
        val isNCHW = (inInfo.shape.size == 4 && (inInfo.shape[1] == 3L || inInfo.shape[1] == 1L))
        inputH = if (isNCHW) shape[2].toInt() else shape[1].toInt()
        inputW = if (isNCHW) shape[3].toInt() else shape[2].toInt()

        labels = ctx.assets.open(classFileName).bufferedReader().readLines()
    }

    fun close() {
        try { session.close() } catch (_: Throwable) {}
        try { env.close() } catch (_: Throwable) {}
    }

    data class Result(val label: String, val prob: Float, val logits: FloatArray)

    fun classify(src: Bitmap): Result {
        // 1) resize về input
        val resized = Bitmap.createScaledBitmap(src, inputW, inputH, true)

        // 2) đổ vào tensor (Float32)
        val inInfo = session.inputInfo[inputName]!!.info as TensorInfo
        val isNCHW = (inInfo.shape.size == 4 && (inInfo.shape[1] == 3L || inInfo.shape[1] == 1L))

        val chw = FloatArray(1 * 3 * inputH * inputW)
        var idx = 0
        val pixels = IntArray(inputH * inputW)
        resized.getPixels(pixels, 0, inputW, 0, 0, inputW, inputH)

        fun norm(v: Int): Float = when (normalizeMode) {
            Normalize.ZERO_TO_ONE -> (v and 0xFF) / 255f
            Normalize.MINUS1_TO1  -> (v and 0xFF) / 127.5f - 1f
        }

        if (isNCHW) {
            // [1,3,H,W]
            val planeSize = inputH * inputW
            var rIdx = 0; var gIdx = planeSize; var bIdx = planeSize * 2
            for (y in 0 until inputH) {
                for (x in 0 until inputW) {
                    val p = pixels[y * inputW + x]
                    chw[rIdx++] = norm((p shr 16) and 0xFF)
                    chw[gIdx++] = norm((p shr  8) and 0xFF)
                    chw[bIdx++] = norm(p and 0xFF)
                }
            }
        } else {
            // [1,H,W,3] → vẫn nạp theo thứ tự R,G,B
            // ORT cho phép NHWC nếu model NHWC
            // nhưng để an toàn bạn nên export NCHW
            val nhwc = FloatArray(inputH * inputW * 3)
            for (y in 0 until inputH) {
                for (x in 0 until inputW) {
                    val p = pixels[y * inputW + x]
                    nhwc[idx++] = norm((p shr 16) and 0xFF)
                    nhwc[idx++] = norm((p shr  8) and 0xFF)
                    nhwc[idx++] = norm(p and 0xFF)
                }
            }
            // chuyển về chw để tạo tensor NCHW nếu cần
            // nhưng nếu model NHWC bạn có thể dùng nhwc trực tiếp.
            // Ở đây ta detect theo inInfo ở trên:
            val fb = FloatBuffer.wrap(nhwc)
            val tensor = OnnxTensor.createTensor(env, fb, longArrayOf(1, inputH.toLong(), inputW.toLong(), 3))
            session.run(mapOf(inputName to tensor)).use { outputs ->
                val out = (outputs[0].value as Array<FloatArray>)[0] // [numClasses]
                val probs = softmax(out)
                val k = probs.indices.maxBy { probs[it] }
                return Result(labels.getOrElse(k) { "$k" }, probs[k], out)
            }
        }

        val fb = FloatBuffer.wrap(chw)
        val tensor = OnnxTensor.createTensor(env, fb, longArrayOf(1, 3, inputH.toLong(), inputW.toLong()))
        session.run(mapOf(inputName to tensor)).use { outputs ->
            val out = (outputs[0].value as Array<FloatArray>)[0] // [numClasses]
            val probs = softmax(out)
            val k = probs.indices.maxBy { probs[it] }
            return Result(labels.getOrElse(k) { "$k" }, probs[k], out)
        }
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val m = logits.maxOrNull() ?: 0f
        var sum = 0.0
        val exps = DoubleArray(logits.size)
        for (i in logits.indices) { exps[i] = exp((logits[i] - m).toDouble()); sum += exps[i] }
        return FloatArray(logits.size) { (exps[it] / sum).toFloat() }
    }

    private fun copyAssetToFile(ctx: Context, name: String): File {
        val out = File(ctx.filesDir, name)
        if (!out.exists()) {
            ctx.assets.open(name).use { ins ->
                out.outputStream().use { outs -> ins.copyTo(outs) }
            }
        }
        return out
    }
}
