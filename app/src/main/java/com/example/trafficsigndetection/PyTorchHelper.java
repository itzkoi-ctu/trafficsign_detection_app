//package com.example.trafficsigndetection;
//
//import android.content.Context;
//
//import org.pytorch.IValue;
//import org.pytorch.Module;
//import org.pytorch.Tensor;
//import java.nio.FloatBuffer;
//
//class PyTorchHelper(context:Context) {
//    private val module: Module = Module.load(
//            AssetFilePath.getAssetFilePath(context, "best_windows2.pt") // hoặc .pt
//            )
//
//    /**
//     * input4d: [1][H][W][C] (giống đầu ra preprocess hiện tại)
//     * Nếu model của bạn nhận [N,C,H,W], ta sẽ chuyển lại cho đúng.
//     */
//    fun runInference(input4d: Array<Array<Array<FloatArray>>>): FloatArray {
//        val h = input4d[0].size
//        val w = input4d[0][0].size
//        val c = input4d[0][0][0].size  // 1 nếu ảnh xám
//
//        // Chuyển NHWC -> NCHW và dồn vào Tensor Float32
//        val chwSize = c * h * w
//        val fb = FloatArray(chwSize)
//
//        // Nếu model kỳ vọng NCHW và channel-first:
//        // với ảnh xám C=1: fb[y*w + x] = input[0][y][x][0]
//        // Nếu C=3 thì điền theo thứ tự kênh.
//        var idx = 0
//        for (ci in 0 until c) {
//            for (y in 0 until h) {
//                for (x in 0 until w) {
//                    fb[idx++] = input4d[0][y][x][ci]
//                }
//            }
//        }
//
//        // Tạo Tensor với shape [1, C, H, W]
//        val inputTensor = Tensor.fromBlob(fb, longArrayOf(1, c.toLong(), h.toLong(), w.toLong()))
//
//        val output = module.forward(IValue.from(inputTensor)).toTensor()
//        return output.dataAsFloatArray // thường là [num_classes]
//    }
//
//    fun close() {
//        // PyTorch Module không cần close bắt buộc, để trống
//    }
//}
