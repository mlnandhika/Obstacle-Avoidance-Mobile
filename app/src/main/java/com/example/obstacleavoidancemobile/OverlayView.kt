package com.example.obstacleavoidancemobile

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * OverlayView
 *
 * View ini bertanggung jawab untuk menggambar:
 * - Bounding box hasil deteksi YOLO
 * - Label kelas + confidence
 * - Posisi objek (Left/Center/Right)
 * - Statistik performa (FPS & inference time)
 */
class OverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    // List hasil deteksi yang akan digambar
    private val detections = mutableListOf<Detection>()

    // Ukuran frame asli dari kamera (sebelum scaling)
    private var frameWidth = 0
    private var frameHeight = 0

    // Variabel untuk menampilkan FPS dan inference time
    private var fpsText: String = ""
    private var inferenceText: String = ""

    /**
     * updateStats()
     * Mengupdate teks FPS dan inference time lalu trigger redraw overlay.
     */
    fun updateStats(fps: Int, inferenceMs: Long) {
        fpsText = "FPS: $fps"
        inferenceText = "Inference: ${inferenceMs}ms"
        postInvalidate()
    }

    // Warna untuk tiap kelas
    private val classColorMap = mutableMapOf<String, Int>()
    private val baseColors = listOf(
        Color.GREEN, Color.RED, Color.BLUE,
        Color.CYAN, Color.MAGENTA, Color.YELLOW,
        Color.rgb(255,140,0),
        Color.rgb(128,0,128),
        Color.rgb(0,128,128),
        Color.rgb(255,20,147)
    )

    /**
     * getColorForClass()
     * Menghasilkan warna yang konsisten untuk setiap kelas berdasarkan hash label.
     */
    private fun getColorForClass(label: String): Int {
        return classColorMap.getOrPut(label) {
            val idx = (label.hashCode().absoluteValue % baseColors.size)
            baseColors[idx]
        }
    }

    // Konfigurasi teks
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

    // Background semi-transparan untuk label
    private val textBgPaint = Paint().apply {
        color = Color.argb(180, 0, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    /**
     * setFrameSize()
     * Dipanggil oleh MainActivity untuk mengirim ukuran frame kamera asli.
     * Dibutuhkan agar bounding box dapat disesuaikan secara proporsional.
     */
    fun setFrameSize(width: Int, height: Int) {
        frameWidth = width
        frameHeight = height
        invalidate()
    }

    /**
     * updateDetections()
     * Menyetel daftar deteksi baru dan trigger redraw.
     */
    fun updateDetections(newDetections: List<Detection>) {
        detections.clear()
        detections.addAll(newDetections)
        postInvalidate()
    }

    /**
     * onDraw()
     * Method utama yang menggambar bounding box, label, posisi objek, dan statistik performa di layar.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (frameWidth == 0 || frameHeight == 0) return

        // Hitung skala mapping dari ukuran frame kamera → ukuran layar
        val scaleX = width / frameWidth.toFloat()
        val scaleY = height / frameHeight.toFloat()

        // Draw FPS and Inference Time
        drawPerformanceStats(canvas)

        // Draw Bounding Boxes
        for (det in detections) {

            val left = det.xMin * scaleX
            val top = det.yMin * scaleY
            val right = det.xMax * scaleX
            val bottom = det.yMax * scaleY

            val boxColor = getColorForClass(det.label)

            // Paint untuk bounding box
            val boxPaint = Paint().apply {
                color = boxColor
                style = Paint.Style.STROKE
                strokeWidth = 3f
                isAntiAlias = true
            }

            val rect = RectF(left, top, right, bottom)
            canvas.drawRect(rect, boxPaint)

            // Hitung posisi objek berdasarkan pusat bounding box
            val centerX = (left + right) / 2f
            val positionText = when {
                centerX < width * 0.33f -> "Left"
                centerX > width * 0.66f -> "Right"
                else -> "Center"
            }

            // Label teks final: class, score, posisi
            val text = "${det.label} ${(det.score * 100).toInt()}% - $positionText"
            val textWidth = textPaint.measureText(text)
            val textHeight = textPaint.textSize

            // Background untuk label
            val bgRect = RectF(
                left,
                top - textHeight - 16f,
                left + textWidth + 20f,
                top
            )

            // Draw label final
            canvas.drawRoundRect(bgRect, 8f, 8f, textBgPaint)
            canvas.drawText(text, left + 10f, top - 10f, textPaint)
        }
    }

    /**
     * drawPerformanceStats()
     * Draw FPS dan inference time pada pojok kiri atas layar.
     */
    private fun drawPerformanceStats(canvas: Canvas) {
        val padding = 20f
        val lineHeight = smallTextPaint.textSize + 10f

        val statsText = "$fpsText\n$inferenceText"
        val widthText = smallTextPaint.measureText("Inference: 000ms") + 40f
        val heightText = lineHeight * 2 + 30f

        // Background
        val bgRect = RectF(10f, 10f, 10f + widthText, 10f + heightText)
        canvas.drawRoundRect(bgRect, 12f, 12f, textBgPaint)

        // Draw text
        canvas.drawText(fpsText, 30f, 40f, smallTextPaint)
        canvas.drawText(inferenceText, 30f, 40f + lineHeight, smallTextPaint)
    }

    private val Int.absoluteValue: Int
        get() = if (this < 0) -this else this
}
