package com.jorge.pocomonitor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private val requiredPermissions: Array<String>
        get() {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_CONNECT
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
                perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            return perms.toTypedArray()
        }

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { /* seguimos aunque no se concedan todos, cada métrica cae a "sin permiso" */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("poco_monitor", Context.MODE_PRIVATE)

        val editUrl = findViewById<EditText>(R.id.edit_server_url)
        val editToken = findViewById<EditText>(R.id.edit_token)
        val editInterval = findViewById<EditText>(R.id.edit_interval)
        val status = findViewById<TextView>(R.id.text_status)

        editUrl.setText(prefs.getString("server_url", ""))
        editToken.setText(prefs.getString("token", ""))
        editInterval.setText(prefs.getInt("interval_sec", 3).toString())

        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }

        findViewById<Button>(R.id.btn_start).setOnClickListener {
            val url = editUrl.text.toString().trim()
            val token = editToken.text.toString().trim()
            val interval = editInterval.text.toString().toIntOrNull() ?: 3

            prefs.edit()
                .putString("server_url", url)
                .putString("token", token)
                .putInt("interval_sec", interval)
                .putBoolean("auto_start", true)
                .apply()

            ContextCompat.startForegroundService(this, Intent(this, MonitorService::class.java))
            status.text = "servicio corriendo — reportando cada ${interval}s"
        }

        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            prefs.edit().putBoolean("auto_start", false).apply()
            stopService(Intent(this, MonitorService::class.java))
            status.text = "servicio detenido"
        }
    }
}
