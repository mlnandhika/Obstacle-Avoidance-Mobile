package com.example.obstacleavoidancemobile

import android.content.Context
import android.graphics.*
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

data class Detection(
    val label: String,
    val score: Float,
    val xMin: Float,
    val yMin: Float,
    val xMax: Float,
    val yMax: Float
)

@OptIn(ExperimentalStdlibApi::class)
class Detector(private val ctx: Context, modelPath: String, labelsPath: String) : AutoCloseable {

    private val CONFIDENCE_THRESHOLD = 0.45f
    private val IOU_THRESHOLD = 0.45f
    private val interpreter: Interpreter
    private val labels: List<String>
    private var inputWidth = 320
    private var inputHeight = 320
    private var inputChannels = 3
    private var isModelQuantized = false

    init {
        val modelBuffer = loadModelFile(modelPath)
        interpreter = Interpreter(modelBuffer)

        val tensor = interpreter.getInputTensor(0)
        val shape = tensor.shape()
        val dtype = tensor.dataType()
        if (shape.size == 4) {
            inputHeight = shape[1]
            inputWidth = shape[2]
            inputChannels = shape[3]
        }
        isModelQuantized = (dtype == DataType.UINT8)

        labels = try {
            ctx.assets.open(labelsPath).bufferedReader().readLines()
        } catch (e: Exception) {
            Log.w("Detector", "Labels file tidak ditemukan.")
            emptyList()
        }
    }

    override fun close() {
        interpreter.close()
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        val scaled = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        val input = convertBitmapToByteBuffer(scaled)

        val outShape = interpreter.getOutputTensor(0).shape()
        val output = Array(1) { Array(outShape[1]) { FloatArray(outShape[2]) } }
        interpreter.run(input, output)

        val detections = parseYoloOutput(output[0], bitmap.width, bitmap.height)
        return nonMaxSuppression(detections, IOU_THRESHOLD)
    }

    private fun parseYoloOutput(data: Array<FloatArray>, origW: Int, origH: Int): List<Detection> {
        val channels = data.size
        val numBoxes = data[0].size
        val numClasses = channels - 4
        val results = mutableListOf<Detection>()

        for (i in 0 until numBoxes) {
            val x = data[0][i]
            val y = data[1][i]
            val w = data[2][i]
            val h = data[3][i]

            var bestScore = 0f
            var bestCls = -1
            for (c in 0 until numClasses) {
                val score = data[4 + c][i]
                if (score > bestScore) {
                    bestScore = score
                    bestCls = c
                }
            }

            if (bestScore > CONFIDENCE_THRESHOLD && bestCls >= 0) {
                val xMin = max(0f, (x - w / 2f) * origW)
                val yMin = max(0f, (y - h / 2f) * origH)
                val xMax = min(origW.toFloat(), (x + w / 2f) * origW)
                val yMax = min(origH.toFloat(), (y + h / 2f) * origH)
                val label = labels.getOrNull(bestCls) ?: "class_$bestCls"
                results.add(Detection(label, bestScore, xMin, yMin, xMax, yMax))
            }
        }
        return results
    }

    private fun nonMaxSuppression(detections: List<Detection>, iouThreshold: Float): List<Detection> {
        val result = mutableListOf<Detection>()
        val sorted = detections.sortedByDescending { it.score }.toMutableList()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            result.add(best)

            val iterator = sorted.iterator()
            while (iterator.hasNext()) {
                val other = iterator.next()
                if (iou(best, other) > iouThreshold && best.label == other.label) {
                    iterator.remove()
                }
            }
        }
        return result
    }

    private fun iou(a: Detection, b: Detection): Float {
        val interLeft = max(a.xMin, b.xMin)
        val interTop = max(a.yMin, b.yMin)
        val interRight = min(a.xMax, b.xMax)
        val interBottom = min(a.yMax, b.yMax)

        val interArea = max(0f, interRight - interLeft) * max(0f, interBottom - interTop)
        val areaA = (a.xMax - a.xMin) * (a.yMax - a.yMin)
        val areaB = (b.xMax - b.xMin) * (b.yMax - b.yMin)

        return interArea / (areaA + areaB - interArea + 1e-6f)
    }

    private fun loadModelFile(path: String): MappedByteBuffer {
        val afd = ctx.assets.openFd(path)
        FileInputStream(afd.fileDescriptor).use { fis ->
            return fis.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        }
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val bytePerChannel = if (isModelQuantized) 1 else 4
        val buffer = ByteBuffer.allocateDirect(1 * inputWidth * inputHeight * inputChannels * bytePerChannel)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputWidth * inputHeight)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var idx = 0
        for (y in 0 until inputHeight) {
            for (x in 0 until inputWidth) {
                val v = pixels[idx++]
                val r = (v shr 16) and 0xFF
                val g = (v shr 8) and 0xFF
                val b = v and 0xFF

                if (isModelQuantized) {
                    buffer.put(r.toByte())
                    buffer.put(g.toByte())
                    buffer.put(b.toByte())
                } else {
                    buffer.putFloat(r / 255f)
                    buffer.putFloat(g / 255f)
                    buffer.putFloat(b / 255f)
                }
            }
        }
        buffer.rewind()
        return buffer
    }
}
