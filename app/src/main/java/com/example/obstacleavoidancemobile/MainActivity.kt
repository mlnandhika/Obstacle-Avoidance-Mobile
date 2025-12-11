package com.example.obstacleavoidancemobile

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * MainActivity
 *
 * Activity utama yang menjalankan:
 * - CameraX (preview + image analysis)
 * - YOLO detection melalui Detector.kt
 * - Non-Maximum Suppression dari Detector.kt
 * - OverlayView untuk menggambar bounding box
 * - Text-to-Speech untuk memberikan feedback suara
 * - FPS & inference time display
 */
class MainActivity : ComponentActivity() {

    // View & Components
    private lateinit var viewFinder: PreviewView          // Surface preview kamera
    private lateinit var overlay: OverlayView             // Layer untuk menggambar bounding box
    private lateinit var cameraExecutor: ExecutorService  // Executor untuk image analysis
    private lateinit var detector: Detector               // YOLO model handler
    private lateinit var tts: TextToSpeech                // Text-to-Speech engine

    // Performance Tracking (FPS dan Inference time)
    private var lastFrameTime = System.currentTimeMillis()
    private var fps = 0

    // Text-to-Speech Timing
    private var lastSpokenTime = 0L
    private val speakInterval = 3000L // 3 second

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Landscape mode
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContentView(R.layout.activity_main)

        // Inisialisasi view
        viewFinder = findViewById(R.id.viewFinder)
        overlay = findViewById(R.id.overlay)

        // Load YOLO model dan label
        detector = Detector(this, "best_float32.tflite", "labels.txt")

        // Inisialisasi TTS
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
            }
        }

        // Executor untuk CameraX image analysis
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Minta izin kamera
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }


    /**
     * Permission launcher untuk meminta izin kamera secara runtime.
     */
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }


    /**
     * Menginisialisasi CameraX:
     * - Preview stream dari kamera belakang
     * - ImageAnalysis untuk menjalankan YOLO per frame
     */
    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)

        providerFuture.addListener({
            val cameraProvider = providerFuture.get()

            // Preview stream
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            // Analisis gambar (frame-by-frame YOLO)
            val analyzer = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480)) // resolusi frame analisis
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            // Set analyzer callback
            analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                processImage(imageProxy)
            }

            try {
                // Bind CameraX lifecycle
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analyzer
                )
            } catch (e: Exception) {
                Log.e("MainActivity", "Camera binding failed", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }


    /**
     * Memproses 1 frame kamera:
     * - Konversi YUV ke Bitmap
     * - Jalankan YOLO (detector.detect)
     * - Hitung fps & inference time
     * - Update bounding box di overlay
     * - Jalankan Text-to-Speech jika ada deteksi
     */
    private fun processImage(imageProxy: ImageProxy) {
        try {
            val bitmap = ImageUtils.imageProxyToBitmap(imageProxy)

            // Jalankan YOLO detection
            val start = System.currentTimeMillis()
            val detections = detector.detect(bitmap)
            val inferenceMs = System.currentTimeMillis() - start

            // Hitung FPS
            val now = System.currentTimeMillis()
            fps = (1000 / (now - lastFrameTime + 1)).toInt()
            lastFrameTime = now

            runOnUiThread {

                // Update bounding box di OverlayView
                overlay.setFrameSize(bitmap.width, bitmap.height)
                overlay.updateDetections(detections)
                overlay.updateStats(fps, inferenceMs)

                // Feedback TTS setiap 3 detik
                if (detections.isNotEmpty()) {
                    val t = System.currentTimeMillis()
                    if (t - lastSpokenTime > speakInterval) {
                        lastSpokenTime = t

                        val det = detections[0] // ambil deteksi yang memiliki nilai confidence score paling besar
                        val centerX = (det.xMin + det.xMax) / 2f

                        val position = when {
                            centerX < bitmap.width / 3 -> "left"
                            centerX > bitmap.width * 2 / 3 -> "right"
                            else -> "center"
                        }

                        val message = "There is a ${det.label} in $position"
                        Log.d("Detection", message)

                        // TTS
                        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "detect")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("MainActivity", "Error processing frame", e)
        } finally {
            imageProxy.close()
        }
    }


    /**
     * Membersihkan resource:
     * - Executor
     * - Text-to-Speech
     */
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        tts.stop()
        tts.shutdown()
    }
}
