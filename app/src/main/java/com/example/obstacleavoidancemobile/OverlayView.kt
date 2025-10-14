package com.example.obstacleavoidancemobile

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    // gunakan top-level Detection (sesuai Detector.kt terakhir)
    private val detections = mutableListOf<Detection>()

    // ukuran sumber (bitmap) tempat koordinat deteksi dihasilkan
    private var srcImageWidth = 0
    private var srcImageHeight = 0

    private val boxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        isAntiAlias = true
    }

    private val labelBgPaint = Paint().apply {
        color = Color.parseColor("#80000000") // semi-transparent black
        style = Paint.Style.FILL
    }

    /**
     * Perbarui deteksi.
     * @param newDetections list hasil deteksi (koordinat relatif pada ukuran bitmap sumber)
     * @param imageWidth width bitmap sumber (biasanya bitmap.width)
     * @param imageHeight height bitmap sumber
     */
    fun updateDetections(newDetections: List<Detection>, imageWidth: Int, imageHeight: Int) {
        synchronized(detections) {
            detections.clear()
            detections.addAll(newDetections)
            srcImageWidth = imageWidth
            srcImageHeight = imageHeight
        }
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (srcImageWidth == 0 || srcImageHeight == 0) return

        // ukuran view
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        // hitung skala sederhana (non-aspect-crop). Ini asumsi preview menampilkan full image scaled to fit.
        val scaleX = viewW / srcImageWidth.toFloat()
        val scaleY = viewH / srcImageHeight.toFloat()

        synchronized(detections) {
            for (det in detections) {
                // skala koordinat dari image -> view
                val left = det.xMin * scaleX
                val top = det.yMin * scaleY
                val right = det.xMax * scaleX
                val bottom = det.yMax * scaleY

                // gambar kotak
                canvas.drawRect(left, top, right, bottom, boxPaint)

                // teks label
                val labelText = "${det.label} ${(det.score * 100).toInt()}%"
                val textWidth = textPaint.measureText(labelText)
                val fm = textPaint.fontMetrics
                val textHeight = fm.bottom - fm.top

                // background rectangle sedikit di atas kotak
                val bgLeft = left
                val bgTop = top - textHeight - 8f
                val bgRight = left + textWidth + 12f
                val bgBottom = top

                canvas.drawRoundRect(RectF(bgLeft, bgTop, bgRight, bgBottom), 6f, 6f, labelBgPaint)
                // teks (posisikan sedikit melayang di dalam bg)
                canvas.drawText(labelText, bgLeft + 6f, bgBottom - 6f - fm.descent, textPaint)
            }
        }
    }
}
