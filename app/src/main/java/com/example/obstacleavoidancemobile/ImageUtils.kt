package com.example.obstacleavoidancemobile

import android.graphics.*
import android.graphics.ImageFormat
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import android.graphics.BitmapFactory

/**
 * ImageUtils
 *
 * Utility object yang berfungsi untuk menangani konversi format gambar
 * dari CameraX (ImageProxy) ke Bitmap, sekaligus memastikan orientasi
 * hasil sesuai landscape mode aplikasi.
 *
 * CameraX memberikan frame dalam format YUV_420_888 (3-plane).
 * Fungsi ini:
 *  1. Menggabungkan Y, U, V buffer menjadi format NV21
 *  2. Mengonversi NV21 ke JPEG
 *  3. Decode JPEG ke Bitmap
 *  4. Melakukan rotasi mengikuti rotationDegrees dari CameraX
 *  5. Memastikan output selalu dalam orientasi landscape
 */
object ImageUtils {

    /**
     * Mengubah ImageProxy (YUV 420) menjadi Bitmap
     */
    fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        // Gabungkan Y, U, V menjadi array NV21
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        // Convert NV21 ke JPEG
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 90, out)
        val imageBytes = out.toByteArray()
        var bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        // Rotate agar landscape sesuai kamera belakang
        val rotationDegrees = image.imageInfo.rotationDegrees
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)

        // Pastikan orientasi landscape
        if (bmp.width < bmp.height) {
            val m = Matrix()
            m.postRotate(90f)
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }

        return bmp
    }
}
