package com.example.jikokuyomi.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import java.io.File

class AudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    fun playFile(file: File, onDone: (() -> Unit)? = null) {
        stop()
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(context, Uri.fromFile(file))
                setOnCompletionListener {
                    stop()
                    onDone?.invoke()
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    onDone?.invoke()
                    true
                }
                prepare()
                start()
            }
        } catch (_: Exception) {
            stop()
            onDone?.invoke()
        }
    }

    fun stop() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                try { it.stop() } catch (_: Exception) {}
            }
            it.release()
        }
        mediaPlayer = null
    }
}
