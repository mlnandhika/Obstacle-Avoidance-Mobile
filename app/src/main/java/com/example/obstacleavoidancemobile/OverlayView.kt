package com.example.obstacleavoidancemobile

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val detections = mutableListOf<Detection>()

    // Dimensi frame kamera
    private var frameWidth = 640f
    private var frameHeight = 480f

    // Warna dan style bounding box
    private val boxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val labelBgPaint = Paint().apply {
        color = Color.argb(160, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    /**
     * Digunakan oleh MainActivity untuk mengatur ukuran frame kamera
     * agar proporsional dengan preview di layar.
     */
    fun setFrameSize(width: Int, height: Int) {
        frameWidth = width.toFloat()
        frameHeight = height.toFloat()
    }

    /**
     * Update hasil deteksi dan redraw overlay
     */
    fun updateDetections(newDetections: List<Detection>) {
        detections.clear()
        detections.addAll(newDetections)
        postInvalidate() // trigger redraw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Scaling faktor agar bounding box sesuai ukuran layar
        val scaleX = width / frameWidth
        val scaleY = height / frameHeight

        canvas.save()
        canvas.scale(scaleX, scaleY)

        for (det in detections) {
            val rect = RectF(det.xMin, det.yMin, det.xMax, det.yMax)
            canvas.drawRect(rect, boxPaint)

            // Label + score
            val labelText = "${det.label} ${(det.score * 100).toInt()}%"
            val textWidth = textPaint.measureText(labelText)
            val textHeight = textPaint.textSize

            // Background untuk label
            val bgRect = RectF(
                det.xMin,
                det.yMin - textHeight - 8,
                det.xMin + textWidth + 10,
                det.yMin
            )
            canvas.drawRect(bgRect, labelBgPaint)
            canvas.drawText(labelText, det.xMin + 5, det.yMin - 10, textPaint)
        }

        canvas.restore()
    }
}
