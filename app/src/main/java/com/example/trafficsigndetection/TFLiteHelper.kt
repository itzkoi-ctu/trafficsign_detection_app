package com.example.trafficsigndetection

import android.content.Context
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.Interpreter

class TFLiteHelper(context: Context) {
    private val interpreter: Interpreter
    fun getInterpreter(): Interpreter {
        return interpreter
    }

    init {
        val options = Interpreter.Options().apply {
            setNumThreads(4)
            // setUseNNAPI(true)
        }
        val model = FileUtil.loadMappedFile(context, "newest.tflite")
        interpreter = Interpreter(model, options)

        val inputShape = interpreter.getInputTensor(0).shape()
        val outputShape = interpreter.getOutputTensor(0).shape()
        android.util.Log.d("TFLiteHelper", "Input shape: ${inputShape.contentToString()}")
        android.util.Log.d("TFLiteHelper", "Output shape: ${outputShape.contentToString()}")
    }

    fun runInference(input: Array<Array<Array<FloatArray>>>): Array<FloatArray> {
        val output = Array(1) { Array(25200) { FloatArray(47) } } // YOLOv5 640x640
        interpreter.run(input, output)
        return output[0]
    }

    fun close() = interpreter.close()
}
