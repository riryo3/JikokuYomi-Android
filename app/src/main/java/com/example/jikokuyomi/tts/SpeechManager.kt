package com.example.jikokuyomi.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.jikokuyomi.model.SpeechLanguage
import java.util.Locale

class SpeechManager(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val pendingQueue = mutableListOf<() -> Unit>()

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(audioAttributes)

                synchronized(pendingQueue) {
                    pendingQueue.forEach { it.invoke() }
                    pendingQueue.clear()
                }
            }
        }
    }

    fun speak(
        text: String,
        language: SpeechLanguage,
        rate: Float = 1.0f,
        onDone: (() -> Unit)? = null
    ) {
        val action: () -> Unit = {
            val engine = tts
            if (engine != null) {
                val locale = when (language) {
                    SpeechLanguage.JAPANESE -> Locale.JAPANESE
                    SpeechLanguage.ENGLISH -> Locale.US
                }
                engine.language = locale
                engine.setSpeechRate(rate)

                val utteranceId = "chime_${System.currentTimeMillis()}"
                if (onDone != null) {
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onDone(id: String?) {
                            if (id == utteranceId) onDone()
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(id: String?) {
                            if (id == utteranceId) onDone()
                        }
                    })
                }

                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                }
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            }
        }

        if (isInitialized) {
            action.invoke()
        } else {
            synchronized(pendingQueue) {
                pendingQueue.add(action)
            }
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
