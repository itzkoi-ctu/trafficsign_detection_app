package com.example.trafficsigndetection

data class YoloBox(
    val x1: Int,
    val y1: Int,
    val x2: Int,
    val y2: Int,
    val score: Float,
    val classId: Int
)

object YoloPostProcessor {

    fun process(
        output: Array<FloatArray>, // [25200][47]
        imageWidth: Int,
        imageHeight: Int,
        confThreshold: Float = 0.25f,
        iouThreshold: Float = 0.45f
    ): List<YoloBox> {

        val boxes = mutableListOf<YoloBox>()

        for (row in output) {
            val conf = row[4]
            if (conf < confThreshold) continue

            val classScores = row.copyOfRange(5, row.size)
            val (classId, classConf) = argmaxWithValue(classScores)

            val finalScore = conf * classConf
            if (finalScore < confThreshold) continue

            val xCenter = row[0] * imageWidth
            val yCenter = row[1] * imageHeight
            val w = row[2] * imageWidth
            val h = row[3] * imageHeight

            val x1 = (xCenter - w / 2).toInt().coerceIn(0, imageWidth - 1)
            val y1 = (yCenter - h / 2).toInt().coerceIn(0, imageHeight - 1)
            val x2 = (xCenter + w / 2).toInt().coerceIn(0, imageWidth - 1)
            val y2 = (yCenter + h / 2).toInt().coerceIn(0, imageHeight - 1)

            boxes.add(YoloBox(x1, y1, x2, y2, finalScore, classId))
        }

        return nonMaxSuppression(boxes, iouThreshold)
    }

    private fun argmaxWithValue(array: FloatArray): Pair<Int, Float> {
        var maxIdx = 0
        var maxVal = array[0]
        for (i in 1 until array.size) {
            if (array[i] > maxVal) {
                maxVal = array[i]
                maxIdx = i
            }
        }
        return Pair(maxIdx, maxVal)
    }

    private fun iou(boxA: YoloBox, boxB: YoloBox): Float {
        val x1 = maxOf(boxA.x1, boxB.x1)
        val y1 = maxOf(boxA.y1, boxB.y1)
        val x2 = minOf(boxA.x2, boxB.x2)
        val y2 = minOf(boxA.y2, boxB.y2)

        val interArea = maxOf(0, x2 - x1) * maxOf(0, y2 - y1)
        val boxAArea = (boxA.x2 - boxA.x1) * (boxA.y2 - boxA.y1)
        val boxBArea = (boxB.x2 - boxB.x1) * (boxB.y2 - boxB.y1)

        return interArea.toFloat() / (boxAArea + boxBArea - interArea).toFloat()
    }

    private fun nonMaxSuppression(boxes: List<YoloBox>, iouThreshold: Float): List<YoloBox> {
        val sortedBoxes = boxes.sortedByDescending { it.score }.toMutableList()
        val keep = mutableListOf<YoloBox>()

        while (sortedBoxes.isNotEmpty()) {
            val box = sortedBoxes.removeAt(0)
            keep.add(box)

            val iterator = sortedBoxes.iterator()
            while (iterator.hasNext()) {
                val other = iterator.next()
                if (iou(box, other) > iouThreshold) {
                    iterator.remove()
                }
            }
        }
        return keep
    }
}

