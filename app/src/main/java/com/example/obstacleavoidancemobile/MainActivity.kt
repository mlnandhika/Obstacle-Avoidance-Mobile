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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)
        overlay = findViewById(R.id.overlay)

        // Init detector
        detector = Detector(this, "best_float32.tflite", "labels.txt")

        // Init TTS
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale("id", "ID") // gunakan bahasa Indonesia
            }
        }

        // Camera Executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Minta izin kamera
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(this, "Izin kamera ditolak", Toast.LENGTH_SHORT).show()
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

    private var lastSpokenTime = 0L
    private var lastMessage = ""

    private fun speakIfNeeded(message: String) {
        val now = System.currentTimeMillis()
        if (message != lastMessage || now - lastSpokenTime > 1500) { // 1.5 detik
            lastSpokenTime = now
            lastMessage = message
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            val bitmap = ImageUtils.imageProxyToBitmap(imageProxy) // pastikan kamu punya ImageUtils
            val results = detector.detect(bitmap)

            runOnUiThread {
                overlay.updateDetections(results, bitmap.width, bitmap.height)

                if (results.isNotEmpty()) {
                    val det = results.maxByOrNull { it.score }!! // ambil deteksi paling yakin
                    val posisi = when {
                        (det.xMin + det.xMax) / 2 < bitmap.width / 3 -> "kiri"
                        (det.xMin + det.xMax) / 2 > bitmap.width * 2 / 3 -> "kanan"
                        else -> "tengah"
                    }
                    val message = "Ada ${det.label} di $posisi"
                    Log.d("Detection", message)
                    speakIfNeeded(message)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Processing frame error", e)
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
