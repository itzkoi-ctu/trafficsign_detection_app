package com.example.trafficsignapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.trafficsignapp.databinding.ActivityMainBinding
import com.example.trafficsigndetection.DetectionOverlay
import com.example.trafficsigndetection.Tf1SsdDetector
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var previewView: PreviewView
    private lateinit var overlay: DetectionOverlay

    private var detector: Tf1SsdDetector? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Views
        previewView = binding.previewView
        overlay = binding.overlay
        previewView.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

        // Executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Đăng ký launcher xin quyền camera
        cameraPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) startCamera()
                else Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show()
            }

        // Khởi tạo detector (bọc try/catch để không crash app nếu lỗi model/ABI)
        try {
            detector = Tf1SsdDetector(this)
        } catch (e: Throwable) {
            e.printStackTrace()
            Toast.makeText(
                this,
                "Detector init failed: ${e.javaClass.simpleName}",
                Toast.LENGTH_LONG
            ).show()
            detector = null
        }

        // Xin quyền hoặc mở camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetResolution(Size(1280, 720))
                .build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val selector = CameraSelector.DEFAULT_BACK_CAMERA

            // Chỉ thêm analyzer nếu detector đã init thành công
            val useCases = mutableListOf<UseCase>(preview)
            detector?.let { det ->
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().apply {
                        setAnalyzer(cameraExecutor, RealtimeAnalyzer(det, overlay))
                    }
                useCases += analysis
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, selector, *useCases.toTypedArray())
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        detector?.close()
    }
}

/** Analyzer: nhận frame, xoay đúng chiều, detect, vẽ lên overlay */
private class RealtimeAnalyzer(
    val detector: Tf1SsdDetector,
    val overlay: DetectionOverlay
) : ImageAnalysis.Analyzer {

    private val busy = AtomicBoolean(false)
    private var reuseBitmap: Bitmap? = null
    private val matrix = Matrix()

    override fun analyze(image: ImageProxy) {
        if (!busy.compareAndSet(false, true)) { image.close(); return }

        try {
            val w = image.width
            val h = image.height
            val plane = image.planes[0] // RGBA
            val buffer: ByteBuffer = plane.buffer
            buffer.rewind()

            var bmp = reuseBitmap
            if (bmp == null || bmp.width != w || bmp.height != h) {
                bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                reuseBitmap = bmp
            }
            bmp!!.copyPixelsFromBuffer(buffer)

            val rotation = image.imageInfo.rotationDegrees
            val srcBitmap = if (rotation != 0) {
                matrix.reset(); matrix.postRotate(rotation.toFloat())
                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, false)
            } else bmp

            val dets = detector.detect(srcBitmap!!, minScore = 0.30f)

            overlay.post {
                overlay.setSourceInfo(srcBitmap.width, srcBitmap.height)
                overlay.detections = dets
                overlay.invalidate()
            }
        } catch (_: Exception) {
            // log nếu cần
        } finally {
            image.close()
            busy.set(false)
        }
    }
}
