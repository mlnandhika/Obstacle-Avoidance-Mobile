package com.example.obstacleavoidancemobile

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class OverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val detections = mutableListOf<Detection>()

    private var frameWidth = 0
    private var frameHeight = 0

    // ===== NEW: FPS & Inference Time =====
    private var fpsText: String = ""
    private var inferenceText: String = ""

    fun updateStats(fps: Int, inferenceMs: Long) {
        fpsText = "FPS: $fps"
        inferenceText = "Inference: ${inferenceMs}ms"
        postInvalidate()
    }

    // ===== Colors for classes =====
    private val classColorMap = mutableMapOf<String, Int>()
    private val baseColors = listOf(
        Color.GREEN, Color.RED, Color.BLUE,
        Color.CYAN, Color.MAGENTA, Color.YELLOW,
        Color.rgb(255,140,0),
        Color.rgb(128,0,128),
        Color.rgb(0,128,128),
        Color.rgb(255,20,147)
    )

    private fun getColorForClass(label: String): Int {
        return classColorMap.getOrPut(label) {
            val idx = (label.hashCode().absoluteValue % baseColors.size)
            baseColors[idx]
        }
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    private val smallTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 30f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    private val textBgPaint = Paint().apply {
        color = Color.argb(180, 0, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun setFrameSize(width: Int, height: Int) {
        frameWidth = width
        frameHeight = height
        invalidate()
    }

    fun updateDetections(newDetections: List<Detection>) {
        detections.clear()
        detections.addAll(newDetections)
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (frameWidth == 0 || frameHeight == 0) return

        val scaleX = width / frameWidth.toFloat()
        val scaleY = height / frameHeight.toFloat()

        // ===== FPS & Inference Time (KIRI ATAS) =====
        drawPerformanceStats(canvas)

        // ===== Draw Bounding Boxes =====
        for (det in detections) {

            val left = det.xMin * scaleX
            val top = det.yMin * scaleY
            val right = det.xMax * scaleX
            val bottom = det.yMax * scaleY

            val boxColor = getColorForClass(det.label)
            val boxPaint = Paint().apply {
                color = boxColor
                style = Paint.Style.STROKE
                strokeWidth = 3f
                isAntiAlias = true
            }

            val rect = RectF(left, top, right, bottom)
            canvas.drawRect(rect, boxPaint)

            val centerX = (left + right) / 2f
            val positionText = when {
                centerX < width * 0.33f -> "Left"
                centerX > width * 0.66f -> "Right"
                else -> "Center"
            }

            val text = "${det.label} ${(det.score * 100).toInt()}% - $positionText"
            val textWidth = textPaint.measureText(text)
            val textHeight = textPaint.textSize

            val bgRect = RectF(
                left,
                top - textHeight - 16f,
                left + textWidth + 20f,
                top
            )

            canvas.drawRoundRect(bgRect, 8f, 8f, textBgPaint)
            canvas.drawText(text, left + 10f, top - 10f, textPaint)
        }
    }

    private fun drawPerformanceStats(canvas: Canvas) {
        val padding = 20f
        val lineHeight = smallTextPaint.textSize + 10f

        val statsText = "$fpsText\n$inferenceText"
        val widthText = smallTextPaint.measureText("Inference: 000ms") + 40f
        val heightText = lineHeight * 2 + 30f

        // background
        val bgRect = RectF(10f, 10f, 10f + widthText, 10f + heightText)
        canvas.drawRoundRect(bgRect, 12f, 12f, textBgPaint)

        // draw text
        canvas.drawText(fpsText, 30f, 40f, smallTextPaint)
        canvas.drawText(inferenceText, 30f, 40f + lineHeight, smallTextPaint)
    }

    private val Int.absoluteValue: Int
        get() = if (this < 0) -this else this
}
