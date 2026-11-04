package com.combo.monkeycompanion.overlay

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.combo.monkeycompanion.engine.feel.Buzz

/** v10.27 Touch Feel: plays a [Buzz] on the phone's vibration motor (Settings › "Touch feel" switches it off). */
object Feel {
    /** The last buzzes played (names), for tests. */
    val log = ArrayList<String>()

    fun on(): Boolean = runCatching { com.combo.monkeycompanion.ai.ChatHub.settings.touchFeel }.getOrDefault(true)

    fun play(ctx: Context, b: Buzz?) {
        if (b == null || b.pulses.isEmpty() || !on()) return
        synchronized(log) { log.add(b.name); if (log.size > 200) log.removeAt(0) }
        val v = vibrator(ctx) ?: return
        runCatching {
            if (!v.hasVibrator()) return
            val eff = if (v.hasAmplitudeControl()) VibrationEffect.createWaveform(b.timings, b.amps, -1)
                else VibrationEffect.createWaveform(b.timings, -1)
            v.vibrate(eff)
        }
    }

    private fun vibrator(ctx: Context): Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }.getOrNull()
}
