package com.example.trafficsigndetection

import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil

class TFLiteClassifier(context: Context) {
    private val interpreter: Interpreter

    init {
        val options = Interpreter.Options().apply { setNumThreads(4) }
        val model = FileUtil.loadMappedFile(context, "model.tflite")
        interpreter = Interpreter(model, options)
    }

    fun runInference(input: Array<Array<Array<FloatArray>>>): FloatArray {
        val output = Array(1) { FloatArray(43) }
        interpreter.run(input, output)
        return output[0]
    }
}
