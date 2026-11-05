package com.example.jikokuyomi.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.jikokuyomi.data.ChimePreferences
import com.example.jikokuyomi.model.ChimeSettings
import com.example.jikokuyomi.model.Slot
import com.example.jikokuyomi.receiver.ChimeReceiver
import java.util.Calendar

object ChimeScheduler {
    private const val REQUEST_CODE = 1001

    fun getNextChimeTime(settings: ChimeSettings): Calendar? {
        if (!settings.enabled) return null

        val cal = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, 1) // Next minute or later
        }

        // Search for the next valid slot within 48 hours
        val limit = 48 * 60
        var count = 0
        while (count < limit) {
            val minute = cal.get(Calendar.MINUTE)
            if (minute % settings.interval == 0) {
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val slot = Slot(hour, minute)
                if (settings.isSlotEnabled(slot)) {
                    return cal
                }
            }
            cal.add(Calendar.MINUTE, 1)
            count++
        }
        return null
    }

    fun scheduleNext(context: Context) {
        val prefs = ChimePreferences(context)
        val settings = prefs.loadSettings()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ChimeReceiver::class.java).apply {
            action = ChimeReceiver.ACTION_CHIME
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (!settings.enabled) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val nextTime = getNextChimeTime(settings) ?: return

        // Set exact alarm that triggers even in Doze mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextTime.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                nextTime.timeInMillis,
                pendingIntent
            )
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ChimeReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
