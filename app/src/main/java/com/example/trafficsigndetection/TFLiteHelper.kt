package com.example.trafficsigndetection

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TFLiteHelper(context: Context) {
    private val interpreter: Interpreter

    init {
        val modelFile = context.assets.openFd("model.tflite")
        val inputStream = FileInputStream(modelFile.fileDescriptor)
        val fileChannel = inputStream.channel
        val mappedByteBuffer: MappedByteBuffer = fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            modelFile.startOffset,
            modelFile.declaredLength
        )
        interpreter = Interpreter(mappedByteBuffer)
    }

    fun runInference(input: Array<Array<Array<FloatArray>>>): FloatArray {
        val output = Array(1) { FloatArray(43) }  // ✅ match shape [1, 43]
        interpreter.run(input, output)
        return output[0] // Trả về [43] 1 chiều cho dễ xử lý
    }

    fun close() = interpreter.close()
}
