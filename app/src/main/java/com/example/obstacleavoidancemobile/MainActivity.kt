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

class MainActivity : ComponentActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var detector: Detector
    private lateinit var tts: TextToSpeech

    private var lastFrameTime = System.currentTimeMillis()
    private var fps = 0

    private var lastSpokenTime = 0L
    private val speakInterval = 3000L // 3 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)
        overlay = findViewById(R.id.overlay)

        detector = Detector(this, "best_float32.tflite", "labels.txt")

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }


    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }


    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)

        providerFuture.addListener({
            val cameraProvider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            val analyzer = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                processImage(imageProxy)
            }

            try {
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


    private fun processImage(imageProxy: ImageProxy) {
        try {
            val bitmap = ImageUtils.imageProxyToBitmap(imageProxy)

            val start = System.currentTimeMillis()
            val detections = detector.detect(bitmap)  // NMS SUDAH DIKERJAKAN DI SINI
            val inferenceMs = System.currentTimeMillis() - start

            // FPS
            val now = System.currentTimeMillis()
            fps = (1000 / (now - lastFrameTime + 1)).toInt()
            lastFrameTime = now

            runOnUiThread {
                overlay.setFrameSize(bitmap.width, bitmap.height)
                overlay.updateDetections(detections)
                overlay.updateStats(fps, inferenceMs)

                // TTS 3 seconds
                if (detections.isNotEmpty()) {
                    val t = System.currentTimeMillis()
                    if (t - lastSpokenTime > speakInterval) {
                        lastSpokenTime = t

                        val det = detections[0]
                        val centerX = (det.xMin + det.xMax) / 2f

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
            Log.e("MainActivity", "Error processing frame", e)
        } finally {
            imageProxy.close()
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        tts.stop()
        tts.shutdown()
    }
}
