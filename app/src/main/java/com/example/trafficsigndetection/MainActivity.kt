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
import com.example.trafficsigndetection.*
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var previewView: PreviewView
    private lateinit var overlay: DetectionOverlay

    private var detector: Tf1SsdDetector? = null
    private var classifier: TFLiteClassifier? = null   // NEW
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        previewView = binding.previewView
        overlay = binding.overlay
        previewView.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

        cameraExecutor = Executors.newSingleThreadExecutor()

        cameraPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) startCamera() else
                    Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show()
            }

        try {
            detector = Tf1SsdDetector(this)
            classifier = TFLiteClassifier(this)   // NEW
        } catch (e: Throwable) {
            e.printStackTrace()
            Toast.makeText(this, "Init failed: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            // vẫn cho chạy detector nếu classifier lỗi
            if (detector == null) return
        }

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
            val useCases = mutableListOf<UseCase>(preview)

            detector?.let { det ->
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().apply {
                        setAnalyzer(
                            cameraExecutor,
                            RealtimeAnalyzer(det, overlay, classifier) // NEW
                        )
                    }
                useCases += analysis
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, selector, *useCases.toTypedArray())
        }, ContextCompat.getMainExecutor(this))
    }

    /** Analyzer: Detect → vẽ → (chọn bbox top) → crop → classify → cập nhật UI + log */
    private class RealtimeAnalyzer(
        val detector: Tf1SsdDetector,
        val overlay: DetectionOverlay,
        val classifier: TFLiteClassifier? = null
    ) : ImageAnalysis.Analyzer {

        private val busy = AtomicBoolean(false)
        private var reuseBitmap: Bitmap? = null
        private val matrix = Matrix()
        private val clsExecutor = Executors.newSingleThreadExecutor()
        private var lastClsAt = 0L
        private val minClsIntervalMs = 250L

        override fun analyze(image: ImageProxy) {
            if (!busy.compareAndSet(false, true)) { image.close(); return }

            try {
                val w = image.width
                val h = image.height
                val buffer: ByteBuffer = image.planes[0].buffer.also { it.rewind() }

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

                // 1) Detect
                val dets = detector.detect(srcBitmap!!, minScore = 0.2f)

                // 2) Vẽ ngay bbox + clear nhãn phụ
                overlay.post {
                    overlay.setSourceInfo(srcBitmap.width, srcBitmap.height)
                    overlay.detections = dets
                    overlay.extraLabels = List(dets.size) { "" }
                    overlay.invalidate()
                }

                // 3) Classify bbox tốt nhất định kỳ
                val now = System.currentTimeMillis()
                val top = dets.maxByOrNull { it.score }
                if (classifier != null && top != null && top.score >= 0.60f && now - lastClsAt >= minClsIntervalMs) {
                    lastClsAt = now
                    val idx = dets.indexOf(top)
                    val boxW = (top.xmax - top.xmin) * srcBitmap.width
                    val boxH = (top.ymax - top.ymin) * srcBitmap.height
                    if (boxW >= 48 && boxH >= 48 && idx >= 0) {
                        val crop = cropFromDetection(srcBitmap, top, padRatio = 0.12f)
                        // chạy classifier ở thread riêng để không drop FPS camera
                        clsExecutor.execute {
                            android.util.Log.d("TRACE", "CLASSIFY idx=$idx score=${"%.2f".format(top.score)} boxPx=${boxW.toInt()}x${boxH.toInt()}")

                            val (lab, probs) = classifier.classify(crop)
                            val conf = ((probs.maxOrNull() ?: 0f) * 100f).toInt()
                            android.util.Log.d("TRACE", "CLS DONE label=$lab conf=${((probs.maxOrNull()?:0f)*100).toInt()}%")

                            overlay.post {
                                overlay.setSourceInfo(srcBitmap.width, srcBitmap.height)
                                overlay.detections = dets

                                // Giữ lại nhãn cũ nếu còn khớp vị trí i; thêm "" khi thiếu
                                val old = overlay.extraLabels
                                val newList = MutableList(dets.size) { i -> if (i < old.size) old[i] else "" }
                                overlay.extraLabels = newList

                                overlay.invalidate()
                            }

                        }
                    }
                }
            } catch (_: Exception) {
                // optional log
            } finally {
                image.close()
                busy.set(false)
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        detector?.close()
        classifier?.close()   // NEW
    }
}
