package com.combo.monkeycompanion.engine.anim

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * v10.7 Disney "follow-through & overlapping action": a damped 1-D spring (angle in degrees or any unit) that
 * chases a target, lags behind it, overshoots and settles. Deterministic (no randomness), sub-stepped so it is
 * stable and frame-rate independent at any dt (8 fps idle to 120 Hz).
 */
class Spring(var stiffness: Float = 150f, var dampingRatio: Float = 0.42f) {
    var value = 0f
    var velocity = 0f

    fun reset(v: Float = 0f) { value = v; velocity = 0f }

    /** Advance [dt] seconds toward [target]; returns the new value. */
    fun step(dt: Float, target: Float): Float {
        if (dt <= 0f) return value
        val w = sqrt(stiffness.coerceAtLeast(1f))
        val c = 2f * dampingRatio * w
        var left = dt.coerceAtMost(0.25f)
        while (left > 0f) {
            val h = if (left > MAX_H) MAX_H else left
            // semi-implicit Euler: stable for w*h < ~1.4 (MAX_H keeps w*h <= 0.17 for k <= 900)
            velocity += ((target - value) * stiffness - c * velocity) * h
            value += velocity * h
            left -= h
        }
        if (!value.isFinite() || !velocity.isFinite()) reset(target)
        return value
    }

    /** Kick: add velocity (an impulse), e.g. on a landing. */
    fun kick(v: Float) { velocity += v }

    val isSettled: Boolean get() = kotlin.math.abs(velocity) < 0.05f

    companion object { const val MAX_H = 1f / 180f }
}

/**
 * A chain of springs (tail / hair / cape segments): each segment follows the previous one with a delay, so the
 * tip lags more than the root (overlapping action). [angle] returns the summed bend of the whole chain.
 */
class SpringChain(val segments: Int = 3, stiffness: Float = 150f, dampingRatio: Float = 0.42f, var lagPerSegment: Float = 0.04f) {
    val springs = Array(segments) { Spring(stiffness * (1f - 0.18f * it), dampingRatio) }
    private val lagged = FloatArray(segments)

    fun tune(stiffness: Float, dampingRatio: Float, lag: Float) {
        for (i in springs.indices) { springs[i].stiffness = stiffness * (1f - 0.18f * i); springs[i].dampingRatio = dampingRatio }
        lagPerSegment = lag
    }

    fun reset() { springs.forEach { it.reset() }; lagged.fill(0f) }

    fun step(dt: Float, target: Float): Float {
        var drive = target
        for (i in 0 until segments) {
            // first-order delay before each segment (frame-rate independent)
            val a = if (lagPerSegment <= 0f) 1f else 1f - exp(-dt / lagPerSegment)
            lagged[i] += (drive - lagged[i]) * a
            drive = springs[i].step(dt, lagged[i])
        }
        return drive
    }

    /** v10.8 secondary action: an impulse (deg/s) on every segment, growing toward the tip (a flick or twitch). */
    fun kick(v: Float) { for (i in springs.indices) springs[i].kick(v * (0.6f + 0.4f * i / (segments - 1).coerceAtLeast(1))) }

    /** Tip deflection (degrees). */
    val tip: Float get() = springs[segments - 1].value
}
