package com.combo.monkeycompanion.engine.anim

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

/** v10.7 Disney "slow in & slow out": easing curves on 0..1. Pure, allocation-free. */
object Easing {
    fun clamp01(k: Float) = if (k < 0f) 0f else if (k > 1f) 1f else k
    fun linear(k: Float) = clamp01(k)
    fun smoothstep(k: Float): Float { val x = clamp01(k); return x * x * (3f - 2f * x) }
    fun easeInOutCubic(k: Float): Float { val x = clamp01(k); return if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f }
    fun easeOutCubic(k: Float): Float { val x = clamp01(k); return 1f - (1f - x).pow(3) }
    fun easeInOutSine(k: Float): Float = (-(cos(PI * clamp01(k)) - 1.0) / 2.0).toFloat()
    /** Pops past 1 then settles back; [s] 1.70158 = classic ~10% overshoot. */
    fun easeOutBack(k: Float, s: Float = 1.70158f): Float { val x = clamp01(k) - 1f; return 1f + (s + 1f) * x * x * x + s * x * x }

    fun apply(style: EaseStyle, k: Float, overshoot: Float = 1.70158f): Float = when (style) {
        EaseStyle.LINEAR -> linear(k)
        EaseStyle.SMOOTHSTEP -> smoothstep(k)
        EaseStyle.CUBIC -> easeInOutCubic(k)
        EaseStyle.SINE -> easeInOutSine(k)
        EaseStyle.BACK -> easeOutBack(k, overshoot)
    }
}

enum class EaseStyle { LINEAR, SMOOTHSTEP, CUBIC, SINE, BACK }
