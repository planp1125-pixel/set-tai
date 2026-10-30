package com.combo.monkeycompanion.fun5

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import com.combo.monkeycompanion.v5.MotionDetector

/**
 * Accelerometer only while Settai is visible and the screen is on (the overlay calls [setActive]); SENSOR_DELAY_UI
 * with batching, so the CPU sleeps between batches. Off when hidden, resting, paused or the feature is off.
 */
class MotionSensor(private val ctx: Context, private val onEvent: (MotionDetector.Event) -> Unit) : SensorEventListener {
    val detector = MotionDetector()
    private var on = false

    fun setActive(want: Boolean) {
        if (want == on) return
        val sm = ctx.getSystemService(SensorManager::class.java) ?: return
        if (want) {
            val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            on = runCatching { sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI, 200_000) }.getOrDefault(false)
        } else { runCatching { sm.unregisterListener(this) }; on = false }
    }
    val active get() = on

    override fun onSensorChanged(e: SensorEvent) {
        val t = SystemClock.elapsedRealtime() - (SystemClock.elapsedRealtimeNanos() - e.timestamp) / 1_000_000L
        detector.feed(e.values[0], e.values[1], e.values[2], t)?.let(onEvent)
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}
