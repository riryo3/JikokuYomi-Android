package com.example.jikokuyomi.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.jikokuyomi.MainActivity
import com.example.jikokuyomi.R
import com.example.jikokuyomi.data.ChimePreferences
import com.example.jikokuyomi.model.Slot
import com.example.jikokuyomi.model.VoiceSource
import com.example.jikokuyomi.pack.VoicePackLibrary
import com.example.jikokuyomi.player.AudioPlayer
import com.example.jikokuyomi.service.ChimeScheduler
import com.example.jikokuyomi.tts.SpeechManager
import java.util.Calendar

class ChimeReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CHIME = "com.example.jikokuyomi.ACTION_CHIME"
        private const val CHANNEL_ID = "jikokuyomi_chime_channel"
        private const val NOTIFICATION_ID = 2001
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "JikokuYomi::ChimeWakeLock"
        ).apply {
            acquire(30_000L) // Safety timeout 30s
        }

        val prefs = ChimePreferences(context)
        val settings = prefs.loadSettings()

        if (!settings.enabled) {
            if (wakeLock.isHeld) wakeLock.release()
            return
        }

        // Get current time slot
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        val slot = Slot(hour, minute)

        if (!settings.isSlotEnabled(slot)) {
            if (wakeLock.isHeld) wakeLock.release()
            ChimeScheduler.scheduleNext(context)
            return
        }

        val minuteOnly = settings.omitMode.isMinuteOnly(minute, settings.interval)
        val spokenText = settings.resolveText(slot)

        // Show Notification
        showNotification(context, slot, spokenText)

        // Determine speaker (VoicePack or TTS)
        val slotKey = String.format(java.util.Locale.US, "t%02d%02d", slot.hour, slot.minute)
        val custom = settings.customizations[slotKey]
        val chosenSpeaker = custom?.speakerPackId ?: settings.defaultPackId
        val forceTts = (chosenSpeaker == "__tts__" || (chosenSpeaker == null && settings.defaultVoiceSource == VoiceSource.TTS))

        val library = VoicePackLibrary(context)
        val audioFile = if (!forceTts && chosenSpeaker != null) {
            library.pickAudioFile(chosenSpeaker, slot, minuteOnly, settings.randomPackIds)
        } else {
            null
        }

        if (audioFile != null && audioFile.exists()) {
            // Play character voice from Voice Pack
            val player = AudioPlayer(context)
            player.playFile(audioFile) {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            }
        } else {
            // Speak via TTS
            val speechManager = SpeechManager(context)
            speechManager.speak(
                text = spokenText,
                language = settings.language,
                rate = settings.speechRate,
                onDone = {
                    speechManager.shutdown()
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                    }
                }
            )
        }

        // Schedule next chime slot
        ChimeScheduler.scheduleNext(context)
    }

    private fun showNotification(context: Context, slot: Slot, spokenText: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.chime_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.chime_channel_description)
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("時報: ${slot.label}")
            .setContentText(spokenText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
