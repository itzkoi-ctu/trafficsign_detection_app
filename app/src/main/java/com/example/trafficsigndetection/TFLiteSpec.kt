package com.example.trafficsigndetection

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class TFLiteSpec(
    val inputName: String,
    val inputShape: IntArray,   // [1,H,W,3]
    val inputDtype: String,     // FLOAT32/UINT8/INT8
    val inputScale: Float?,
    val inputZero: Int?,
    val outputName: String,
    val outputShape: IntArray,  // [1,N]
    val outputDtype: String,
    val outputScale: Float?,
    val outputZero: Int?,
    val numClasses: Int
)

fun loadModelFile(ctx: Context, assetName: String): MappedByteBuffer {
    ctx.assets.openFd(assetName).use { fd ->
        FileInputStream(fd.fileDescriptor).channel.use { ch ->
            return ch.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }
}

fun inspectTFLite(ctx: Context, assetName: String): TFLiteSpec {
    val itp = Interpreter(loadModelFile(ctx, assetName))
    val in0 = itp.getInputTensor(0)
    val out0 = itp.getOutputTensor(0)
    val inQ = in0.quantizationParams()
    val outQ = out0.quantizationParams()
    val inShape = in0.shape()
    val outShape = out0.shape()
    val numClasses = when {
        outShape.size == 2 -> outShape[1]
        outShape.size == 1 -> outShape[0]
        else -> outShape.last()
    }
    return TFLiteSpec(
        inputName = in0.name(),
        inputShape = inShape,
        inputDtype = in0.dataType().name,
        inputScale = inQ.scale.takeIf { it != 0f },
        inputZero = inQ.zeroPoint.takeIf { it != 0 },
        outputName = out0.name(),
        outputShape = outShape,
        outputDtype = out0.dataType().name,
        outputScale = outQ.scale.takeIf { it != 0f },
        outputZero = outQ.zeroPoint.takeIf { it != 0 },
        numClasses = numClasses
    )
}


