package com.combo.monkeycompanion.overlay

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.combo.monkeycompanion.engine.feel.Buzz
import com.combo.monkeycompanion.engine.feel.TouchFeel

/** v10.27 Touch Feel: plays a [Buzz] on the phone's vibration motor (Settings › "Touch feel" switches it off). */
object Feel {
    /** The last buzzes played (names), for tests. */
    val log = ArrayList<String>()

    fun on(): Boolean = runCatching { com.combo.monkeycompanion.ai.ChatHub.settings.touchFeel }.getOrDefault(true)

    fun play(ctx: Context, b: Buzz?) {
        if (b == null || b.pulses.isEmpty() || !on()) return
        note(b.name)
        val v = vibrator(ctx) ?: return
        runCatching {
            if (!v.hasVibrator()) return
            val eff = if (v.hasAmplitudeControl()) VibrationEffect.createWaveform(b.timings, b.amps, -1)
                else VibrationEffect.createWaveform(b.timings, -1)
            vibrateTouch(v, eff)
        }
    }

    /** Stop any in-flight buzz so a wall slap is not smeared by a sling snap. */
    fun cancel(ctx: Context) {
        runCatching { vibrator(ctx)?.cancel() }
    }

    /**
     * v10.30 glass slap: cancel first, then a sharp knock (CLICK+THUD primitives when the phone has them,
     * else HEAVY_CLICK, else a very short waveform). Logs as `wall_hit` — never `thump`.
     */
    fun playSharpHit(ctx: Context, force: Float, weight: Float) {
        if (!on()) return
        note("wall_hit")
        val v = vibrator(ctx) ?: return
        runCatching {
            if (!v.hasVibrator()) return
            v.cancel()
            val f = force.coerceIn(0f, 1f)
            val w = weight.coerceIn(0f, 1f)
            if (Build.VERSION.SDK_INT >= 30 && playCompositionHit(v, f, w)) return
            if (Build.VERSION.SDK_INT >= 29) {
                vibrateTouch(v, VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                return
            }
            val b = TouchFeel.wallHit(f, w)
            val eff = if (v.hasAmplitudeControl()) VibrationEffect.createWaveform(b.timings, b.amps, -1)
                else VibrationEffect.createWaveform(b.timings, -1)
            vibrateTouch(v, eff)
        }
    }

    /** v10.30 pinch-and-pop spring-back click. */
    fun playPop(ctx: Context) {
        if (!on()) return
        note("pop")
        val v = vibrator(ctx) ?: return
        runCatching {
            if (!v.hasVibrator()) return
            v.cancel()
            if (Build.VERSION.SDK_INT >= 30 && playCompositionPop(v)) return
            if (Build.VERSION.SDK_INT >= 29) {
                vibrateTouch(v, VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                return
            }
            val b = TouchFeel.pop()
            val eff = if (v.hasAmplitudeControl()) VibrationEffect.createWaveform(b.timings, b.amps, -1)
                else VibrationEffect.createWaveform(b.timings, -1)
            vibrateTouch(v, eff)
        }
    }

    private fun note(name: String) {
        synchronized(log) { log.add(name); if (log.size > 200) log.removeAt(0) }
    }

    private fun playCompositionHit(v: Vibrator, force: Float, weight: Float): Boolean {
        // API 31+: CLICK + THUD. API 30: CLICK + QUICK_FALL (no PRIMITIVE_THUD yet).
        val second = if (Build.VERSION.SDK_INT >= 31) VibrationEffect.Composition.PRIMITIVE_THUD
            else VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
        if (!v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK, second)) return false
        val click = (0.55f + 0.45f * force).coerceIn(0.4f, 1f)
        val thud = ((0.45f + 0.55f * force) * (0.55f + 0.45f * weight)).coerceIn(0.35f, 1f)
        val eff = VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, click)
            .addPrimitive(second, thud)
            .compose()
        vibrateTouch(v, eff)
        return true
    }

    private fun playCompositionPop(v: Vibrator): Boolean {
        if (!v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) return false
        val eff = VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.85f)
            .compose()
        vibrateTouch(v, eff)
        return true
    }

    /** Prefer touch-priority so overlay hits aren't treated like a notification buzz. */
    private fun vibrateTouch(v: Vibrator, eff: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(eff, VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build())
        } else {
            v.vibrate(eff)
        }
    }

    private fun vibrator(ctx: Context): Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }.getOrNull()
}
