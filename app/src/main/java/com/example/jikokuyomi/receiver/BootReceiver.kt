package com.example.jikokuyomi.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.jikokuyomi.data.ChimePreferences
import com.example.jikokuyomi.service.ChimeScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = ChimePreferences(context)
            val settings = prefs.loadSettings()
            if (settings.enabled) {
                ChimeScheduler.scheduleNext(context)
            }
        }
    }
}
