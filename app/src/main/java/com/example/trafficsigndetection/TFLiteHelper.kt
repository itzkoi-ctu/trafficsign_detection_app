package com.example.trafficsigndetection

import android.content.Context
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TFLiteHelper(context: Context) {
    private val interpreter: Interpreter

//    init {
//        val modelFile = context.assets.openFd("newest.tflite")
//        val inputStream = FileInputStream(modelFile.fileDescriptor)
//        val fileChannel = inputStream.channel
//        val mappedByteBuffer: MappedByteBuffer = fileChannel.map(
//            FileChannel.MapMode.READ_ONLY,
//            modelFile.startOffset,
//            modelFile.declaredLength
//        )
//        interpreter = Interpreter(mappedByteBuffer)
//    }

//    fun runInference(input: Array<Array<Array<FloatArray>>>): Array<FloatArray> {
////        val output = Array(1) { FloatArray(43) }  // ✅ match shape [1, 43]
//        val output = Array(1) { Array(25200) { FloatArray(47) } }
//        interpreter.run(input, output)
//        return output[0] // Trả về [43] 1 chiều cho dễ xử lý
//    }

//    fun runInference(input: Array<Array<Array<FloatArray>>>): Array<FloatArray> {
//        val output = Array(1) { Array(25200) { FloatArray(47) } }
//        interpreter.run(input, output)
//        return output[0] // Trả về [25200][47] để xử lý YOLO
//    }

    init {
        val options = Interpreter.Options().apply { setNumThreads(4) }
        val model = FileUtil.loadMappedFile(context, "newest.tflite")
        interpreter = Interpreter(model, options)
    }

    fun runInference(input: Array<Array<Array<FloatArray>>>): Array<FloatArray> {
        val output = Array(1) { Array(25200) { FloatArray(47) } } // YOLOv5
        interpreter.run(input, output)
        return output[0]
    }


    fun close() = interpreter.close()
}
