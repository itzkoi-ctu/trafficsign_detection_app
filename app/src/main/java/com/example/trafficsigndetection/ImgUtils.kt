package com.example.trafficsigndetection


import android.graphics.Bitmap
import kotlin.math.*

/** det: xmin,ymin,xmax,ymax normalized [0..1]; thêm padding theo tỷ lệ. */
fun cropFromDetection(src: Bitmap, det: Tf1SsdDetector.Detection, padRatio: Float = 0.10f): Bitmap {
    val w = src.width.toFloat()
    val h = src.height.toFloat()

    var l = det.xmin * w
    var t = det.ymin * h
    var r = det.xmax * w
    var b = det.ymax * h

    val padX = (r - l) * padRatio
    val padY = (b - t) * padRatio
    l -= padX; t -= padY; r += padX; b += padY

    l = l.coerceIn(0f, w - 1f)
    t = t.coerceIn(0f, h - 1f)
    r = r.coerceIn(l + 1f, w)
    b = b.coerceIn(t + 1f, h)

    val x = floor(l).toInt()
    val y = floor(t).toInt()
    val cw = ceil(r - l).toInt().coerceAtLeast(1)
    val ch = ceil(b - t).toInt().coerceAtLeast(1)

    return Bitmap.createBitmap(src, x, y, cw, ch)
}

/** Center-crop giữ tỉ lệ rồi resize về outSize x outSize (cho classifier). */
fun centerCropResize(src: Bitmap, outSize: Int): Bitmap {
    val w = src.width
    val h = src.height
    if (w <= 0 || h <= 0 || outSize <= 0) {
        // tạo 1 bitmap rỗng để tránh crash (hiếm khi cần)
        return Bitmap.createBitmap(max(1, outSize), max(1, outSize), Bitmap.Config.ARGB_8888)
    }

    // scale: đảm bảo cả 2 chiều của ảnh scaled đều >= outSize
    val scale = max(outSize.toFloat() / w, outSize.toFloat() / h)
    val sw = max(1, floor(w * scale).toInt())
    val sh = max(1, floor(h * scale).toInt())
    val scaled = if (sw != w || sh != h) {
        Bitmap.createScaledBitmap(src, sw, sh, true)
    } else src

    // tâm ảnh → toạ độ cắt
    val maxX = max(0, scaled.width - outSize)
    val maxY = max(0, scaled.height - outSize)
    val cx = (scaled.width - outSize) / 2
    val cy = (scaled.height - outSize) / 2
    val x = cx.coerceIn(0, maxX)
    val y = cy.coerceIn(0, maxY)

    // kích thước cắt thực tế (không vượt biên)
    val cw = min(outSize, scaled.width - x)
    val ch = min(outSize, scaled.height - y)

    val cropped = Bitmap.createBitmap(scaled, x, y, cw, ch)

    // đảm bảo output đúng outSize
    return if (cropped.width != outSize || cropped.height != outSize) {
        Bitmap.createScaledBitmap(cropped, outSize, outSize, true)
    } else {
        cropped
    }
}

/** Softmax (nếu output classifier là logits). */
fun softmax(x: FloatArray): FloatArray {
    val m = x.maxOrNull() ?: 0f
    var s = 0f
    val e = FloatArray(x.size)
    for (i in x.indices) { e[i] = kotlin.math.exp((x[i] - m).toDouble()).toFloat(); s += e[i] }
    for (i in x.indices) e[i] /= s
    return e
}
