package com.jorge.pocomonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "poco_monitor_channel"
        const val NOTIF_ID = 1
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var collector: MetricsCollector
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("poco_monitor", Context.MODE_PRIVATE)
        collector = MetricsCollector(this)
        collector.start()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification("iniciando..."))
        startLoop()
        return START_STICKY
    }

    private fun startLoop() {
        job?.cancel()
        job = scope.launch {
            val serverUrl = prefs.getString("server_url", "") ?: ""
            val token = prefs.getString("token", "") ?: ""
            val intervalSec = prefs.getInt("interval_sec", 3).coerceAtLeast(2)

            while (isActive) {
                try {
                    val data = collector.collect()
                    val ok = sendReport(serverUrl, token, data.toString())
                    updateNotification(if (ok) "reportando cada ${intervalSec}s" else "error de conexión")
                } catch (e: Exception) {
                    updateNotification("error: ${e.message}")
                }
                delay(intervalSec * 1000L)
            }
        }
    }

    private fun sendReport(serverUrl: String, token: String, json: String): Boolean {
        if (serverUrl.isBlank()) return false
        return try {
            val url = URL("${serverUrl.trimEnd('/')}/api/report")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-Report-Token", token)
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(json) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            false
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Poco Monitor", NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Poco Monitor")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(status))
    }

    override fun onDestroy() {
        job?.cancel()
        collector.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
