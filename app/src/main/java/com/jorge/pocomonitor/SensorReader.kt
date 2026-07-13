package com.jorge.pocomonitor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class SensorReader(ctx: Context) : SensorEventListener {

    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    @Volatile var accelMagnitude: Float? = null
        private set
    @Volatile var gyroMagnitude: Float? = null
        private set
    @Volatile var lightLux: Float? = null
        private set
    @Volatile var proximityNear: Boolean? = null
        private set

    fun start() {
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        sm.getDefaultSensor(Sensor.TYPE_LIGHT)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        sm.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
                accelMagnitude = sqrt(x * x + y * y + z * z)
            }
            Sensor.TYPE_GYROSCOPE -> {
                val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
                gyroMagnitude = sqrt(x * x + y * y + z * z)
            }
            Sensor.TYPE_LIGHT -> {
                lightLux = event.values[0]
            }
            Sensor.TYPE_PROXIMITY -> {
                val maxRange = event.sensor.maximumRange
                proximityNear = event.values[0] < maxRange
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}
