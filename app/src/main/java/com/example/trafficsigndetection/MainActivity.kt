package com.example.trafficsignapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.Size
import android.view.WindowManager
import android.view.OrientationEventListener
import android.view.Surface
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
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var previewView: PreviewView
    private lateinit var overlay: DetectionOverlay

    private var detector: Tf1SsdDetector? = null
    private var classifier: TFLiteClassifier? = null

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var tts: TextToSpeech
    lateinit var labelTranslator: LabelTranslator

    private var lastSpokenLabel: String = ""
    private var lastSpokenAt: Long = 0L
    private val speakCooldownMs = 1200L

    // Theo dõi xoay để cập nhật targetRotation khi head-unit đổi hướng
    private var orientationListener: OrientationEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Màn hình xe: luôn sáng
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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
            } else Log.e("TTS", "Khởi tạo TTS thất bại")
        }

        cameraPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) startCamera() else
                    Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show()
            }

        try {
            detector = Tf1SsdDetector(this)
            classifier = TFLiteClassifier(this)
        } catch (e: Throwable) {
            e.printStackTrace()
            Toast.makeText(this, "Init failed: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) startCamera()
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun speakTranslated(rawLabel: String) {
        val translated = labelTranslator.translate(rawLabel)
        val now = System.currentTimeMillis()
        if (translated != lastSpokenLabel || now - lastSpokenAt >= speakCooldownMs) {
            tts.speak(translated, TextToSpeech.QUEUE_FLUSH, null, null)
            lastSpokenLabel = translated
            lastSpokenAt = now
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            if (isDestroyed || isFinishing) return@addListener

            val selector = CameraSelector.DEFAULT_BACK_CAMERA

            // FORCED rotation cho LDPlayer ở màn hình ngang
            val forcedRotation = Surface.ROTATION_90   // nếu vẫn bị ngược, đổi thành ROTATION_270

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setTargetRotation(forcedRotation)            // <-- ép xoay
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val analysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setTargetRotation(forcedRotation)            // <-- ép xoay
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply {
                    setAnalyzer(
                        cameraExecutor,
                        RealtimeAnalyzer(detector!!, overlay, classifier)
                    )
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, selector, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }


    private class RealtimeAnalyzer(
        val detector: Tf1SsdDetector,
        val overlay: DetectionOverlay,
        val classifier: TFLiteClassifier? = null
    ) : ImageAnalysis.Analyzer {

        // Khóa cho nhánh detect -> analyze (mỗi frame)
        private val busy = AtomicBoolean(false)

        // Khóa cho job phân loại (chỉ 1 job chạy tại một thời điểm)
        private val clsBusy = AtomicBoolean(false)

        // Vé đánh dấu "yêu cầu mới nhất"
        private val latestTicket = AtomicLong(0)

        private var reuseBitmap: Bitmap? = null
        private val clsExecutor = Executors.newSingleThreadExecutor()
        private var lastClsAt = 0L
        private val minClsIntervalMs = 100L

        private var lastLabel = ""
        private var stableCount = 0
        private val debounceThreshold = 1
        private var lastStableLabelTime = 0L
        private val displayDuration = 500L

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

                val srcBitmap = bmp

                val dets = detector.detect(srcBitmap, minScore = 0.3f)
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

                    val enoughSize = (boxW >= 24 && boxH >= 24) || top.score >= 0.4f
                    if (enoughSize && idx >= 0) {
                        val crop = safeCropFromDetection(srcBitmap, top, padRatio = 0.12f)
                        val resized = centerCropResize(crop, 224)

                        // Nếu job đang chạy, BỎ frame nhưng phải đóng image & unlock busy
                        if (!clsBusy.compareAndSet(false, true)) {
                            // đóng ở đây vì return giữa hàm sẽ không vào finally
                            image.close()
                            busy.set(false)
                            return
                        }

                        // Cấp vé mới nhất cho job này
                        val ticket = latestTicket.incrementAndGet()

                        clsExecutor.execute {
                            try {
                                val result = classifier.classify(resized)
                                // Nếu đã có yêu cầu mới hơn, bỏ kết quả cũ
                                if (ticket != latestTicket.get()) return@execute

                                val newLabel = "${result.label} ${(result.prob * 100).toInt()}%"

                                val activity = overlay.context as? MainActivity
                                activity?.runOnUiThread {
                                    // Kiểm tra lại lần cuối trước khi chạm UI/TTS
                                    if (ticket != latestTicket.get()) return@runOnUiThread

                                    activity.binding.imageDebug.setImageBitmap(crop)

                                    // Debounce nhãn ổn định
                                    val labelChanged = result.label != lastLabel
                                    if (labelChanged) {
                                        stableCount = 1
                                        lastLabel = result.label
                                    } else {
                                        stableCount++
                                    }

                                    val nowUi = System.currentTimeMillis()
                                    val shouldUpdate = labelChanged ||
                                            stableCount >= debounceThreshold ||
                                            nowUi - lastStableLabelTime >= displayDuration

                                    if (shouldUpdate) {
                                        lastStableLabelTime = nowUi

                                        activity.speakTranslated(result.label)

                                        val list = overlay.extraLabels.toMutableList().let {
                                            while (it.size < dets.size) it += ""
                                            it
                                        }
                                        if (idx in list.indices) list[idx] = newLabel
                                        overlay.extraLabels = list
                                        overlay.invalidate()
                                    }
                                }
                            } catch (e: Throwable) {
                                Log.e("RealtimeAnalyzer", "cls error", e)
                            } finally {
                                clsBusy.set(false)
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

        private fun safeCropFromDetection(
            frame: Bitmap,
            det: Tf1SsdDetector.Detection,
            padRatio: Float
        ): Bitmap {
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
        orientationListener?.disable()
        cameraExecutor.shutdown()
        detector?.close()
        try { classifier?.close() } catch (_: Throwable) {}
        tts.stop()
        tts.shutdown()
    }
}
