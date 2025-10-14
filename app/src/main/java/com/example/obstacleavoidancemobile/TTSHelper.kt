package com.example.obstacleavoidancemobile

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TTSHelper(private val ctx: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("id", "ID") // bahasa Indonesia
                ready = true
            }
        }
    }

    fun speak(text: String) {
        if (!ready) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
