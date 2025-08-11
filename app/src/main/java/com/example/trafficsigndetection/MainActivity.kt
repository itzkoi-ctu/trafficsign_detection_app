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
import com.example.trafficsigndetection.ImageProcessor
import com.example.trafficsigndetection.TFLiteHelper
import java.util.concurrent.Executors
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.widget.TextView
import com.example.trafficsigndetection.ClassifierPreprocessor
import com.example.trafficsigndetection.TFLiteClassifier
import com.example.trafficsigndetection.YoloPostProcessor
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var previewView: PreviewView
    private lateinit var yoloInterpreter: TFLiteHelper
    private lateinit var resultText: TextView
    private lateinit var classifierInterpreter: TFLiteClassifier

    //text to speech
    private lateinit var tts: TextToSpeech
    private var lastSpokenTime = 0L
    private var lastSpokenSign: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        resultText= findViewById(R.id.resultText)
        yoloInterpreter = TFLiteHelper(this)
        classifierInterpreter = TFLiteClassifier(this)
//        tfliteHelper = TFLiteHelper(this)



        // text to speech
        tts = TextToSpeech(this, this)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1001)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(findViewById<PreviewView>(R.id.previewView).surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("CameraX", "Binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        val yoloInput = ImageProcessor.preprocess(bitmap)
        val yoloOutput = yoloInterpreter.runInference(yoloInput)
        val boxes = YoloPostProcessor.process(yoloOutput, bitmap.width, bitmap.height)

        val resultBuilder = StringBuilder()

        for (box in boxes) {
            val cropped = Bitmap.createBitmap(
                bitmap,
                box.x1.coerceIn(0, bitmap.width - 1),
                box.y1.coerceIn(0, bitmap.height - 1),
                (box.x2 - box.x1).coerceAtLeast(1).coerceAtMost(bitmap.width - box.x1),
                (box.y2 - box.y1).coerceAtLeast(1).coerceAtMost(bitmap.height - box.y1)
            )

            val resized = Bitmap.createScaledBitmap(cropped, 48, 48, true)
            val classifierInput = ClassifierPreprocessor.preprocess(resized)

            val pred = classifierInterpreter.runInference(classifierInput)
            val classId = argmax(pred)
            val label = CLASS_NAMES[classId]

            resultBuilder.append("$label\n")
        }

        // Cập nhật UI trên main thread
        runOnUiThread {
            findViewById<TextView>(R.id.resultText).text =
                if (resultBuilder.isNotEmpty()) resultBuilder.toString() else "Không nhận dạng được"
        }

        imageProxy.close()
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
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
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
