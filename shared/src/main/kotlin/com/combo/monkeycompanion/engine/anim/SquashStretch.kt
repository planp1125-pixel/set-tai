package com.combo.monkeycompanion.engine.anim

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * v10.7 Disney "squash & stretch" + "solid drawing": turns the authored squashX/squashY (which were set
 * independently and changed the character's area) into a volume-preserving pair (sx * sy == 1), clamps it,
 * and adds a small stretch along the direction of fast motion. Pure functions, no allocation.
 */
object SquashStretch {
    /**
     * Volume-preserving y scale for an authored pair: keeps the authored aspect (sy/sx) but makes sx*sy = 1.
     * Result clamped to [minY, maxY]; the matching x scale is 1/sy.
     */
    fun preserveY(sx: Float, sy: Float, minY: Float, maxY: Float): Float {
        val ax = if (sx <= 0.01f) 0.01f else sx
        val ratio = (sy / ax).coerceAtLeast(0.0001f)
        return sqrt(ratio).coerceIn(minY, maxY)
    }

    /**
     * Stretch factor (>= 1 along the motion axis) for a speed in dp/s: 0 below [startSpeed], up to
     * 1 + [maxStretch] at [fullSpeed].
     */
    fun velocityStretch(speed: Float, maxStretch: Float, startSpeed: Float = 350f, fullSpeed: Float = 2600f): Float {
        if (speed <= startSpeed) return 1f
        val k = Easing.smoothstep((speed - startSpeed) / (fullSpeed - startSpeed))
        return 1f + maxStretch * k
    }

    /**
     * Combines the stretch with the axis-aligned rig: returns a y multiplier. Vertical motion stretches tall
     * (> 1), horizontal motion stretches wide (< 1, x becomes 1/y). Diagonals blend.
     */
    fun motionY(vx: Float, vy: Float, stretch: Float): Float {
        val s2 = vx * vx + vy * vy
        if (s2 < 1e-3f || stretch <= 1f) return 1f
        val dir = (vy * vy - vx * vx) / s2 // +1 vertical, -1 horizontal
        val amt = stretch - 1f
        return 1f + amt * dir * (if (dir < 0f) 0.8f else 1f)
    }

    /** Area of an sx, sy pair relative to 1 (for tests / debug). */
    fun area(sx: Float, sy: Float) = abs(sx * sy)
}
