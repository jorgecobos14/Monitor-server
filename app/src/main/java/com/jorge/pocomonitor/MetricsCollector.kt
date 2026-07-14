package com.jorge.pocomonitor

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class MetricsCollector(private val ctx: Context) {

    private val sensors = SensorReader(ctx)

    fun start() = sensors.start()
    fun stop() = sensors.stop()

    fun collect(): JSONObject {
        val j = JSONObject()

        // ---- Sistema / hardware ----
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        j.put("ram_free", memInfo.availMem)
        j.put("ram_total", memInfo.totalMem)
        j.put("ram_free_pct", if (memInfo.totalMem > 0) (memInfo.availMem * 100.0 / memInfo.totalMem) else JSONObject.NULL)

        val stat = StatFs(android.os.Environment.getDataDirectory().path)
        val storageTotal = stat.blockCountLong * stat.blockSizeLong
        val storageFree = stat.availableBlocksLong * stat.blockSizeLong
        j.put("storage_total", storageTotal)
        j.put("storage_free", storageFree)

        val batteryStatus = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) j.put("battery_level", (level * 100.0 / scale).toInt()) else j.put("battery_level", JSONObject.NULL)
        val tempTenths = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        j.put("battery_temp", if (tempTenths >= 0) tempTenths / 10.0 else JSONObject.NULL)
        val voltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        j.put("battery_voltage", if (voltage >= 0) voltage else JSONObject.NULL)
        val health = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        j.put("battery_health", batteryHealthLabel(health))
        val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        j.put("is_charging", plugged != 0)
        j.put("charging_method", when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> "no"
        })

        j.put("cpu_cores", Runtime.getRuntime().availableProcessors())
        j.put("cpu_max_freq", readCpuMaxFreqMHz())

        j.put("uptime_human", formatUptime(SystemClock.elapsedRealtime()))
        j.put("android_version", Build.VERSION.RELEASE)
        j.put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")

        // ---- Red ----
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wm = ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }

        val wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        j.put("wifi_connected", wifiConnected)
        j.put("mobile_data_active", caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true)
        j.put("vpn_active", caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true)

        if (wifiConnected) {
            val info = wm.connectionInfo
            val hasLocationPerm = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasNearbyWifiPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
            } else true
            val canReadSsid = hasLocationPerm && hasNearbyWifiPerm
            j.put("wifi_ssid", if (canReadSsid) info.ssid?.trim('"') else "sin permiso")
            j.put("wifi_rssi", info.rssi)
            j.put("wifi_link_speed", info.linkSpeed)
            j.put("ip_local", intToIp(info.ipAddress))
        } else {
            j.put("wifi_ssid", JSONObject.NULL)
            j.put("wifi_rssi", JSONObject.NULL)
            j.put("wifi_link_speed", JSONObject.NULL)
            j.put("ip_local", JSONObject.NULL)
        }
        j.put("ip_public", fetchPublicIp())

        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        j.put("network_type", networkTypeLabel(caps))
        j.put("carrier", tm.networkOperatorName ?: "—")

        val btAdapter = BluetoothAdapter.getDefaultAdapter()
        val hasBtPerm = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        j.put("bluetooth_enabled", btAdapter?.isEnabled == true)
        j.put("bluetooth_paired_count", if (hasBtPerm && btAdapter?.isEnabled == true) btAdapter.bondedDevices?.size ?: 0 else JSONObject.NULL)

        val airplane = Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        j.put("airplane_mode", airplane)

        j.put("bytes_received", TrafficStats.getTotalRxBytes())
        j.put("bytes_sent", TrafficStats.getTotalTxBytes())

        // ---- Energía / pantalla ----
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        j.put("battery_saver", pm.isPowerSaveMode)
        j.put("screen_on", pm.isInteractive)
        val brightness = try {
            Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) { -1 }
        j.put("screen_brightness", if (brightness >= 0) brightness else JSONObject.NULL)
        j.put("doze_mode", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) pm.isDeviceIdleMode else JSONObject.NULL)

        // ---- Sensores ----
        j.put("accel_magnitude", sensors.accelMagnitude?.toDouble() ?: JSONObject.NULL)
        j.put("gyro_magnitude", sensors.gyroMagnitude?.toDouble() ?: JSONObject.NULL)
        j.put("light_lux", sensors.lightLux?.toDouble() ?: JSONObject.NULL)
        j.put("proximity_near", sensors.proximityNear)

        return j
    }

    private fun batteryHealthLabel(h: Int): String = when (h) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "buena"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "sobrecalentada"
        BatteryManager.BATTERY_HEALTH_DEAD -> "muerta"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "sobrevoltaje"
        BatteryManager.BATTERY_HEALTH_COLD -> "fría"
        else -> "desconocida"
    }

    private fun networkTypeLabel(caps: NetworkCapabilities?): String {
        if (caps == null) return "—"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "móvil"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "otro"
        }
    }

    private fun intToIp(ip: Int): String {
        return "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
    }

    private fun readCpuMaxFreqMHz(): Any {
        return try {
            val f = java.io.File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
            if (f.exists()) (f.readText().trim().toLong() / 1000) else JSONObject.NULL
        } catch (e: Exception) {
            JSONObject.NULL
        }
    }

    private fun formatUptime(ms: Long): String {
        val totalSec = TimeUnit.MILLISECONDS.toSeconds(ms)
        val days = totalSec / 86400
        val hours = (totalSec % 86400) / 3600
        val mins = (totalSec % 3600) / 60
        return "${days}d ${hours}h ${mins}m"
    }

    private var cachedPublicIp: String? = null
    private var lastPublicIpFetch = 0L
    private val PUBLIC_IP_TTL_MS = TimeUnit.MINUTES.toMillis(5)

    private fun fetchPublicIp(): Any {
        val now = System.currentTimeMillis()
        if (cachedPublicIp != null && now - lastPublicIpFetch < PUBLIC_IP_TTL_MS) {
            return cachedPublicIp!!
        }
        return try {
            val url = URL("https://api.ipify.org")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            val result = conn.inputStream.bufferedReader().readText().trim()
            conn.disconnect()
            cachedPublicIp = result
            lastPublicIpFetch = now
            result
        } catch (e: Exception) {
            cachedPublicIp ?: JSONObject.NULL
        }
    }
}
