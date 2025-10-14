package com.example.obstacleavoidancemobile

import android.Manifest
import android.content.pm.PackageManager
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
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var detector: Detector
    private lateinit var tts: TextToSpeech

    private var lastSpokenTime = 0L
    private val speakInterval = 3000L // ms (3 detik antar ucapan)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)
        overlay = findViewById(R.id.overlay)

        // Init detector (pastikan model & label ada di assets)
        detector = Detector(this, "best_float32.tflite", "labels.txt")

        // Init Text-to-Speech (English)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
            }
        }

        // Camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Request camera permission
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("MainActivity", "Camera binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            val bitmap = ImageUtils.imageProxyToBitmap(imageProxy)
            val detections = detector.detect(bitmap)

            // Jalankan NMS agar box tidak bertumpuk
            val filteredDetections = applyNMS(detections, 0.45f)

            runOnUiThread {
                overlay.setFrameSize(bitmap.width, bitmap.height)
                overlay.updateDetections(filteredDetections)

                // TTS — bicara hanya 1x tiap 3 detik
                if (filteredDetections.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    if (now - lastSpokenTime > speakInterval) {
                        lastSpokenTime = now

                        val det = filteredDetections[0]
                        val centerX = (det.xMin + det.xMax) / 2
                        val position = when {
                            centerX < bitmap.width / 3 -> "left"
                            centerX > bitmap.width * 2 / 3 -> "right"
                            else -> "center"
                        }
                        val message = "There is a ${det.label} in $position"
                        Log.d("Detection", message)
                        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "detect")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Processing frame error", e)
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Non-Maximum Suppression (NMS)
     * Menghapus deteksi yang saling tumpang tindih
     */
    private fun applyNMS(detections: List<Detection>, iouThreshold: Float): List<Detection> {
        val sorted = detections.sortedByDescending { it.score }.toMutableList()
        val result = mutableListOf<Detection>()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            result.add(best)

            val iterator = sorted.iterator()
            while (iterator.hasNext()) {
                val other = iterator.next()
                val iou = calculateIoU(best, other)
                if (iou > iouThreshold) iterator.remove()
            }
        }
        return result
    }

    private fun calculateIoU(a: Detection, b: Detection): Float {
        val x1 = max(a.xMin, b.xMin)
        val y1 = max(a.yMin, b.yMin)
        val x2 = min(a.xMax, b.xMax)
        val y2 = min(a.yMax, b.yMax)
        val intersection = max(0f, x2 - x1) * max(0f, y2 - y1)
        val areaA = (a.xMax - a.xMin) * (a.yMax - a.yMin)
        val areaB = (b.xMax - b.xMin) * (b.yMax - b.yMin)
        return intersection / (areaA + areaB - intersection)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        tts.stop()
        tts.shutdown()
    }
}
