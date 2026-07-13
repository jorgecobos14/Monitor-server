package com.jorge.pocomonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("poco_monitor", Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean("auto_start", false)
            if (autoStart) {
                val serviceIntent = Intent(context, MonitorService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
