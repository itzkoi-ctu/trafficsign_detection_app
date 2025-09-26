//import android.graphics.Bitmap
//import android.util.Log
//import androidx.camera.core.ImageAnalysis
//import androidx.camera.core.ImageProxy
//import com.example.trafficsignapp.MainActivity
//import com.example.trafficsigndetection.DetectionOverlay
//import com.example.trafficsigndetection.TFLiteClassifier
//import com.example.trafficsigndetection.Tf1SsdDetector
//import com.example.trafficsigndetection.centerCropResize
//import java.nio.ByteBuffer
//import java.util.concurrent.Executors
//import java.util.concurrent.atomic.AtomicBoolean
//import java.util.concurrent.atomic.AtomicLong
//
//private class RealtimeAnalyzer(
//        val detector: Tf1SsdDetector,
//        val overlay: DetectionOverlay,
//        val classifier: TFLiteClassifier? = null,
//        ) : ImageAnalysis.Analyzer {
//
//    private val busy = AtomicBoolean(false)
//    private val latestTicket = AtomicLong(0)
//
//    private var reuseBitmap: Bitmap? = null
//    private val clsExecutor = Executors.newFixedThreadPool(2) // cho nhiều crop cùng lúc
//    private var lastClsAt = 0L
//    private val minClsIntervalMs = 100L
//
//    override fun analyze(image: ImageProxy) {
//        if (!busy.compareAndSet(false, true)) {
//            image.close(); return
//        }
//        try {
//            val w = image.width
//            val h = image.height
//            val buffer: ByteBuffer = image.planes[0].buffer.also { it.rewind() }
//
//            var bmp = reuseBitmap
//            if (bmp == null || bmp.width != w || bmp.height != h) {
//                bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
//                reuseBitmap = bmp
//            }
//            bmp!!.copyPixelsFromBuffer(buffer)
//            val srcBitmap = bmp
//
//            val dets = detector.detect(srcBitmap, minScore = 0.2f)
//            val now = System.currentTimeMillis()
//
//            // Overlay clean
//            val cleanDets = dets.map { d -> d.copy(label = "") }
//            overlay.post {
//                overlay.setSourceInfo(srcBitmap.width, srcBitmap.height)
//                overlay.detections = cleanDets
//                overlay.extraLabels = List(cleanDets.size) { "" }
//                overlay.invalidate()
//            }
//
//            if (classifier != null && dets.isNotEmpty() && now - lastClsAt >= minClsIntervalMs) {
//                lastClsAt = now
//                for ((idx, det) in dets.withIndex()) {
//                    if (det.score < 0.5f) continue
//
//                    val boxW = (det.xmax - det.xmin) * srcBitmap.width
//                    val boxH = (det.ymax - det.ymin) * srcBitmap.height
//                    if (boxW < 24 || boxH < 24) continue
//
//                    val crop = safeCropFromDetection(srcBitmap, det, padRatio = 0.12f)
//                    val resized = centerCropResize(crop, 224)
//
//                    val ticket = latestTicket.incrementAndGet()
//                    clsExecutor.execute {
//                        try {
//                            val result = classifier.classify(resized)
//                            val newLabel = result.label
//                            val activity = overlay.context as? MainActivity
//                            val translated = activity?.labelTranslator?.translate(newLabel) ?: newLabel
//
//                            activity?.runOnUiThread {
//                                if (ticket != latestTicket.get()) return@runOnUiThread
//                                        // đưa label vào hàng đợi TTS
//                                                activity.enqueueSpeak(newLabel)
//
//                                // update overlay text
//                                val list = overlay.extraLabels.toMutableList().let {
//                                    while (it.size < dets.size) it += ""
//                                    it
//                                }
//                                if (idx in list.indices) list[idx] = translated
//                                overlay.extraLabels = list
//                                overlay.invalidate()
//                            }
//                        } catch (e: Throwable) {
//                            Log.e("RealtimeAnalyzer", "cls error", e)
//                        }
//                    }
//                }
//            }
//
//        } catch (e: Exception) {
//            Log.e("RealtimeAnalyzer", "Error in analyze", e)
//        } finally {
//            image.close()
//            busy.set(false)
//        }
//    }
//
//    private fun safeCropFromDetection(
//            frame: Bitmap,
//            det: Tf1SsdDetector.Detection,
//            padRatio: Float
//    ): Bitmap {
//        val w = frame.width.toFloat()
//        val h = frame.height.toFloat()
//        fun nx(v: Float) = if (det.xmax <= 1f && det.xmin <= 1f) v * w else v
//        fun ny(v: Float) = if (det.ymax <= 1f && det.ymin <= 1f) v * h else v
//        var l = nx(det.xmin)
//        var t = ny(det.ymin)
//        var r = nx(det.xmax)
//        var b = ny(det.ymax)
//        val padX = (r - l) * padRatio
//        val padY = (b - t) * padRatio
//        l -= padX; t -= padY; r += padX; b += padY
//        l = l.coerceIn(0f, w - 1f); t = t.coerceIn(0f, h - 1f)
//        r = r.coerceIn(l + 1f, w); b = b.coerceIn(t + 1f, h)
//        val x = kotlin.math.floor(l).toInt()
//        val y = kotlin.math.floor(t).toInt()
//        val cw = kotlin.math.ceil(r - l).toInt().coerceAtLeast(1)
//        val ch = kotlin.math.ceil(b - t).toInt().coerceAtLeast(1)
//        return Bitmap.createBitmap(frame, x, y, cw, ch)
//    }
//
//
//}
