package com.example.sim

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class TiltSteeringController(
    context: Context,
    private val onTiltChanged: (Float) -> Unit
) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var zeroOffset = 0f
    private var lastRawRoll = 0f
    private var filtered = 0f

    fun start() {
        accelSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    fun recalibrateZero() {
        zeroOffset = lastRawRoll
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.values.size < 2) return
        val raw = (-event.values[0] / 6.2f).coerceIn(-1.2f, 1.2f)
        lastRawRoll = raw
        val centered = raw - zeroOffset
        val deadzone = 0.05f
        val adjusted = when {
            centered > deadzone -> (centered - deadzone) / (1f - deadzone)
            centered < -deadzone -> (centered + deadzone) / (1f - deadzone)
            else -> 0f
        }.coerceIn(-1f, 1f)
        filtered += (adjusted - filtered) * 0.25f
        onTiltChanged(filtered)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
