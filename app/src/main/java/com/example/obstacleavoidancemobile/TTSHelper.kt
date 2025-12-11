package com.example.obstacleavoidancemobile

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * TTSHelper
 * Helper class untuk Text-to-Speech (TTS).
 * Class ini bertanggung jawab untuk:
 * - Inisialisasi TTS secara asynchronous
 * - Menyimpan status apakah TTS sudah siap digunakan
 * - Menyediakan fungsi speak()
 * - Mengelola lifecycle TTS (stop & shutdown)
 */
class TTSHelper(private val ctx: Context) {

    // Instance TTS
    private var tts: TextToSpeech? = null

    // Flag untuk menandai apakah TTS sudah siap digunakan
    private var ready = false

    /**
     * Inisialisasi TTS secara asynchronous.
     * Callback TextToSpeech.OnInitListener akan dipanggil ketika engine sudah siap
     * Flag 'ready' diset TRUE hanya jika TTS berhasil diinisialisasi dan bahasa berhasil diset.
     */
    init {
        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("id", "ID") // bahasa Indonesia
                ready = true
            }
        }
    }

    /**
     * speak(text)
     * Mengucapkan teks menggunakan TTS dengan QUEUE_FLUSH
     */
    fun speak(text: String) {
        if (!ready) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId")
    }

    /**
     * shutdown()
     * Memastikan TTS dihentikan dan dilepas dari memori.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
