
package com.example.trafficsignapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
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
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var previewView: PreviewView
    private lateinit var overlay: DetectionOverlay

    private var detector: Tf1SsdDetector? = null
    private lateinit var onnxCls: OnnxClassifier

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var tts: TextToSpeech
    private lateinit var labelTranslator: LabelTranslator
    private var lastSpokenLabel: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        previewView = binding.previewView
        overlay = binding.overlay
        previewView.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

        cameraExecutor = Executors.newSingleThreadExecutor()
        labelTranslator = LabelTranslator(this)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts.setLanguage(Locale("vi", "VN"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Ngôn ngữ không được hỗ trợ")
                }
            } else {
                Log.e("TTS", "Khởi tạo TTS thất bại")
            }
        }

        cameraPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) startCamera() else
                    Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show()
            }

        try {
            detector = Tf1SsdDetector(this)
            onnxCls = OnnxClassifier(this, "traffic_sign_classifier.onnx", "class.txt", OnnxClassifier.Normalize.ZERO_TO_ONE)
        } catch (e: Throwable) {
            e.printStackTrace()
            Toast.makeText(this, "Init failed: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun speakTranslated(label: String) {
        val translated = labelTranslator.translate(label)
        if (translated != lastSpokenLabel) {
            tts.speak(translated, TextToSpeech.QUEUE_FLUSH, null, null)
            lastSpokenLabel = translated
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            if (isDestroyed || isFinishing) return@addListener

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
                        setAnalyzer(cameraExecutor, RealtimeAnalyzer(det, overlay, onnxCls))
                    }
                useCases += analysis
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, selector, *useCases.toTypedArray())
            } catch (e: Exception) {
                Log.e("Camera", "bindToLifecycle failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private class RealtimeAnalyzer(
        val detector: Tf1SsdDetector,
        val overlay: DetectionOverlay,
        val classifier: OnnxClassifier? = null
    ) : ImageAnalysis.Analyzer {

        private val busy = AtomicBoolean(false)
        private var reuseBitmap: Bitmap? = null
        private val matrix = Matrix()
        private val clsExecutor = Executors.newSingleThreadExecutor()
        private var lastClsAt = 0L
        private val minClsIntervalMs = 300L
        private var lastLabel = ""
        private var stableCount = 0
        private val debounceThreshold = 2
        private var lastStableLabelTime = 0L
        private val displayDuration = 1000L

        override fun analyze(image: ImageProxy) {
            if (!busy.compareAndSet(false, true)) {
                image.close(); return
            }

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

                val dets = detector.detect(srcBitmap!!, minScore = 0.18f)
                val now = System.currentTimeMillis()
                val top = dets.maxByOrNull { it.score }

                overlay.post {
                    overlay.setSourceInfo(srcBitmap.width, srcBitmap.height)
                    overlay.detections = dets
                    overlay.extraLabels = List(dets.size) { "" }
                    overlay.invalidate()
                }

                if (classifier != null && top != null && now - lastClsAt >= minClsIntervalMs) {
                    lastClsAt = now
                    val idx = dets.indexOf(top)
                    val boxW = (top.xmax - top.xmin) * srcBitmap.width
                    val boxH = (top.ymax - top.ymin) * srcBitmap.height

                    if ((boxW >= 32 && boxH >= 32 || top.score >= 0.6f) && idx >= 0){
                        val crop = safeCropFromDetection(srcBitmap, top, padRatio = 0.12f)
                        val resized = centerCropResize(crop, 224)

                        clsExecutor.execute {
                            val result = classifier.classify(resized)
                            val newLabel = "${result.label} ${(result.prob * 100).toInt()}%"

                            val activity = overlay.context as? MainActivity
                            activity?.runOnUiThread {
                                activity.binding.imageDebug.setImageBitmap(crop)
                            }

                            val labelChanged = result.label != lastLabel
                            if (labelChanged) {
                                stableCount = 1
                                lastLabel = result.label
                            } else {
                                stableCount++
                            }

                            val shouldUpdate = labelChanged || stableCount >= debounceThreshold || now - lastStableLabelTime >= displayDuration
                            if (shouldUpdate) {
                                lastStableLabelTime = now

                                activity?.runOnUiThread {
                                    val translated = activity.labelTranslator.translate(result.label)
                                    activity.speakTranslated(translated)

                                    val list = overlay.extraLabels.toMutableList().let {
                                        while (it.size < dets.size) it += ""
                                        it
                                    }
                                    if (idx in list.indices) list[idx] = newLabel
                                    overlay.extraLabels = list
                                    overlay.invalidate()
                                }
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("RealtimeAnalyzer", "Error in analyze", e)
            } finally {
                image.close()
                busy.set(false)
            }
        }


        private fun safeCropFromDetection(frame: Bitmap, det: Tf1SsdDetector.Detection, padRatio: Float): Bitmap {
            val w = frame.width.toFloat()
            val h = frame.height.toFloat()
            fun nx(v: Float) = if (det.xmax <= 1f && det.xmin <= 1f) v * w else v
            fun ny(v: Float) = if (det.ymax <= 1f && det.ymin <= 1f) v * h else v
            var l = nx(det.xmin); var t = ny(det.ymin); var r = nx(det.xmax); var b = ny(det.ymax)
            val padX = (r - l) * padRatio
            val padY = (b - t) * padRatio
            l -= padX; t -= padY; r += padX; b += padY
            l = l.coerceIn(0f, w - 1f); t = t.coerceIn(0f, h - 1f)
            r = r.coerceIn(l + 1f, w); b = b.coerceIn(t + 1f, h)
            val x = kotlin.math.floor(l).toInt()
            val y = kotlin.math.floor(t).toInt()
            val cw = kotlin.math.ceil(r - l).toInt().coerceAtLeast(1)
            val ch = kotlin.math.ceil(b - t).toInt().coerceAtLeast(1)
            return Bitmap.createBitmap(frame, x, y, cw, ch)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        detector?.close()
        tts.stop()
        tts.shutdown()
        try { onnxCls.close() } catch (_: Throwable) {}
    }
}

