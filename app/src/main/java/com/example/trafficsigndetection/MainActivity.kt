package com.example.trafficsignapp

import android.Manifest
import android.speech.tts.TextToSpeech
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.example.trafficsigndetection.TFLiteHelper
import java.util.concurrent.Executors
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import android.widget.TextView
import com.example.trafficsigndetection.draw.OverlayView
import com.example.trafficsigndetection.utils.ImageProcessor
import java.io.ByteArrayOutputStream
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var previewView: PreviewView
    private lateinit var tfliteHelper: TFLiteHelper
    private lateinit var resultText: TextView
    private lateinit var overlayView: OverlayView
    //private lateinit var classifierInterpreter: TFLiteClassifier
    private lateinit var yoloInterpreter : TFLiteHelper

    //text to speech
    private lateinit var tts: TextToSpeech
    private var lastSpokenTime = 0L
    private var lastSpokenSign: String? = null
    private val recentSigns = mutableListOf<String>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        resultText= findViewById(R.id.resultText)
        overlayView = findViewById(R.id.overlayView)
        yoloInterpreter = TFLiteHelper(this)
        //classifierInterpreter = TFLiteClassifier(this)
        tfliteHelper = TFLiteHelper(this)



        // text to speech
        tts = TextToSpeech(this, this)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1001)
        }
    }

    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            var lastAnalyzedTime = 0L

            imageAnalyzer.setAnalyzer(Executors.newSingleThreadExecutor(), ImageAnalysis.Analyzer { imageProxy ->
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastAnalyzedTime >= 1000) {
                    lastAnalyzedTime = currentTime

                    val rawBitmap = imageProxyToBitmap(imageProxy) ?: run {
                        imageProxy.close(); return@Analyzer
                    }

                    // Xoay bitmap đúng chiều preview
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    val rotatedBitmap = rotateBitmap(rawBitmap, rotation)

                    // Letterbox + lấy tham số scale/padding
                    val (inputBitmap, lb) = letterbox(rotatedBitmap, 640)
                    val inputBuffer = ImageProcessor.bitmapToFloatBuffer(inputBitmap)

                    // Chạy model
                    val outputBuffer = Array(1) { Array(25200) { FloatArray(47) } }
                    tfliteHelper.getInterpreter().run(inputBuffer, outputBuffer)

                    // Parse kết quả -> chuyển về toạ độ của "rotatedBitmap"
                    val boxes = mutableListOf<RectF>()
                    val labels = mutableListOf<String>()
                    val modelInputSize = lb.size.toFloat()

                    for (i in 0 until 25200) {
                        val row = outputBuffer[0][i]
                        val objConf = row[4]
                        if (objConf < 0.1f) continue

                        val classScores = row.copyOfRange(5, row.size)
                        val maxClassScore = classScores.maxOrNull() ?: 0f
                        val classIndex = classScores.indices.maxByOrNull { classScores[it] } ?: -1
                        val finalScore = objConf * maxClassScore
                        if (finalScore < 0.1f) continue

                        // Toạ độ trong KHÔNG GIAN 640×640 (đÃ letterbox)
                        val cx = row[0] * modelInputSize
                        val cy = row[1] * modelInputSize
                        val w  = row[2] * modelInputSize
                        val h  = row[3] * modelInputSize

                        // BỎ padding + CHIA scale -> về không gian của rotatedBitmap
                        val x1 = ((cx - w/2f) - lb.dx) / lb.scale
                        val y1 = ((cy - h/2f) - lb.dy) / lb.scale
                        val x2 = ((cx + w/2f) - lb.dx) / lb.scale
                        val y2 = ((cy + h/2f) - lb.dy) / lb.scale

                        // Clamp vào ảnh xoay
                        val l = x1.coerceIn(0f, rotatedBitmap.width  - 1f)
                        val t = y1.coerceIn(0f, rotatedBitmap.height - 1f)
                        val r = x2.coerceIn(0f, rotatedBitmap.width  - 1f)
                        val b = y2.coerceIn(0f, rotatedBitmap.height - 1f)

                        if (r > l && b > t) {
                            boxes.add(RectF(l, t, r, b))
                            labels.add(CLASS_NAMES[classIndex] + " ${"%.0f".format(finalScore * 100)}%")
                        }
                    }

                    runOnUiThread {
                        // Truyền kích thước của ảnh ĐÃ XOAY (không phải imageProxy.width/height)
                        overlayView.setResults(boxes, labels, rotatedBitmap.width, rotatedBitmap.height)
                        resultText.text = if (labels.isNotEmpty()) labels.joinToString("\n") else "Không nhận diện được"
                    }
                }
                imageProxy.close()
            })



            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
        }, ContextCompat.getMainExecutor(this))
    }





    private fun argmax(array: FloatArray): Int {
        var maxIdx = 0
        var maxValue = array[0]
        for (i in array.indices) {
            if (array[i] > maxValue) {
                maxValue = array[i]
                maxIdx = i
            }
        }
        return maxIdx
    }


    // Chuyển ImageProxy sang Bitmap
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val yPlane = imageProxy.planes[0]
        val uPlane = imageProxy.planes[1]
        val vPlane = imageProxy.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        // NV21: Y + VU (V trước, U sau)
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        // Dùng RenderScript hoặc YuvImage để decode nhanh
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()

        // Giảm chất lượng xuống ~90 để tiết kiệm thời gian nhưng vẫn đẹp
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 90, out)

        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }








    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        yoloInterpreter.close()
//        tfliteHelper.close()

        // release text to speech
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
    }

    data class LetterboxParams(val scale: Float, val dx: Float, val dy: Float, val size: Int)

    private fun rotateBitmap(src: Bitmap, rotationDeg: Int): Bitmap {
        if (rotationDeg % 360 == 0) return src
        val m = Matrix()
        m.postRotate(rotationDeg.toFloat())
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun letterbox(src: Bitmap, size: Int = 640): Pair<Bitmap, LetterboxParams> {
        val sw = src.width.toFloat()
        val sh = src.height.toFloat()
        val scale = minOf(size / sw, size / sh)
        val nw = (sw * scale).toInt()
        val nh = (sh * scale).toInt()

        val resized = Bitmap.createScaledBitmap(src, nw, nh, true)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.BLACK)
        val dx = ((size - nw) / 2f)
        val dy = ((size - nh) / 2f)
        c.drawBitmap(resized, dx, dy, null)

        return out to LetterboxParams(scale, dx, dy, size)
    }


    companion object {
        // Danh sách tên class
        val CLASS_NAMES = listOf( "Giới hạn tốc độ 20",
            "Giới hạn tốc độ 30",
            "Giới hạn tốc độ 50",
            "Giới hạn tốc độ 60",
            "Giới hạn tốc độ 70",
            "Giới hạn tốc độ 80",
            "Hết giới hạn tốc độ 80",
            "Giới hạn tốc độ 100",
            "Giới hạn tốc độ 120",
            "Cấm vượt",
            "Cấm xe tải vượt",
            "Đường ưu tiên",
            "Nhường đường",
            "Dừng lại",
            "Cấm lưu thông hai chiều",
            "Cấm xe tải",
            "Cấm vào",
            "Chú ý chung",
            "Đường cong nguy hiểm bên trái",
            "Đường cong nguy hiểm bên phải",
            "Đường cong kép",
            "Đường gồ ghề",
            "Đường trơn trượt",
            "Đường bị thu hẹp",
            "Công trường đang thi công",
            "Có tín hiệu giao thông",
            "Có người đi bộ",
            "Khu vực trường học",
            "Có xe đạp qua đường",
            "Đường đóng băng",
            "Có động vật hoang dã",
            "Hết mọi hạn chế",
            "Rẽ phải phía trước",
            "Rẽ trái phía trước",
            "Chỉ đi thẳng",
            "Đi thẳng hoặc rẽ phải",
            "Đi thẳng hoặc rẽ trái",
            "Đi bên phải",
            "Đi bên trái",
            "Vòng xuyến",
            "Hết lệnh cấm vượt",
            "Hết lệnh cấm xe tải vượt")
    }
//    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
//        val yBuffer = image.planes[0].buffer
//        val uBuffer = image.planes[1].buffer
//        val vBuffer = image.planes[2].buffer
//
//        val ySize = yBuffer.remaining()
//        val uSize = uBuffer.remaining()
//        val vSize = vBuffer.remaining()
//
//        val nv21 = ByteArray(ySize + uSize + vSize)
//
//        yBuffer.get(nv21, 0, ySize)
//        vBuffer.get(nv21, ySize, vSize)
//        uBuffer.get(nv21, ySize + vSize, uSize)
//
//        val yuvImage = YuvImage(
//            nv21,
//            ImageFormat.NV21,
//            image.width, image.height,
//            null
//        )
//        val out = ByteArrayOutputStream()
//        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
//        val yuv = out.toByteArray()
//        return BitmapFactory.decodeByteArray(yuv, 0, yuv.size)
//    }

    // text to speech
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("vi", "VN")) // Đọc tiếng Việt
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Ngôn ngữ không hỗ trợ!")
            }
        } else {
            Log.e("TTS", "Khởi tạo TTS thất bại!")
        }
    }


}
