package com.combo.monkeycompanion.v5

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * v5 shake / tilt / face-down from the accelerometer (m/s², device axes). Fed at SENSOR_DELAY_UI (~60 ms) only
 * while the overlay is visible and the screen is on. Pure: returns at most one event per sample.
 *
 * v10.30: also exposes filtered gravity and [tiltDeg] so the overlay can slide continuously (~45°)
 * and fall when the phone is tipped on its side / upside-down in hand (~90° / ~180°). Table face-down
 * (screen flat on a table) stays a sleep-mask, not a mid-air fall.
 */
class MotionDetector {
    enum class Event { SHAKE, HARD_SHAKE, TILT_LEFT, TILT_RIGHT, LEVEL, FACE_DOWN, FACE_UP }

    private var gx = 0f; private var gy = 9.8f; private var gz = 0f
    private var lastShakeAt = -10_000L
    private val peaks = ArrayDeque<Long>()
    private var tilt = 0                 // -1 left low, 1 right low, 0 level
    private var tiltSince = 0L
    private var faceDown = false
    private var downSince = -1L
    private var upSince = -1L
    private var inited = false

    /** Filtered gravity (m/s²). */
    val gravityX get() = gx
    val gravityY get() = gy
    val gravityZ get() = gz
    val isFaceDown get() = faceDown

    companion object {
        const val SHAKE_G = 13f           // linear acceleration peak (m/s²)
        const val HARD_G = 26f
        const val SHAKE_PEAKS = 3
        const val WINDOW_MS = 900L
        const val COOLDOWN_MS = 2500L
        const val TILT_X = 4.2f           // ~25° (discrete event threshold)
        const val TILT_HOLD_MS = 700L
        const val FACE_DOWN_Z = -8.5f
        const val FACE_HOLD_MS = 1500L
        /** Continuous slide starts around here (degrees from upright). */
        const val SLIDE_DEG = 32f
        /** Fall when |tilt| reaches about this (heavier bodies use a slightly lower bar). */
        const val FALL_DEG = 90f
    }

    fun feed(x: Float, y: Float, z: Float, tMs: Long): Event? {
        if (!inited) { gx = x; gy = y; gz = z; inited = true }
        val a = 0.85f
        gx = a * gx + (1 - a) * x; gy = a * gy + (1 - a) * y; gz = a * gz + (1 - a) * z
        val lx = x - gx; val ly = y - gy; val lz = z - gz
        val lin = sqrt(lx * lx + ly * ly + lz * lz)

        // Shake: several peaks in a short window.
        if (lin > SHAKE_G) {
            peaks.addLast(tMs)
            while (peaks.isNotEmpty() && tMs - peaks.first() > WINDOW_MS) peaks.removeFirst()
            if (peaks.size >= SHAKE_PEAKS && tMs - lastShakeAt > COOLDOWN_MS) {
                lastShakeAt = tMs; peaks.clear()
                return if (lin > HARD_G) Event.HARD_SHAKE else Event.SHAKE
            }
        }
        if (tMs - lastShakeAt < 600L) return null

        // Face down / up (screen towards the table).
        if (gz < FACE_DOWN_Z) { upSince = -1L; if (downSince < 0) downSince = tMs; if (!faceDown && tMs - downSince > FACE_HOLD_MS) { faceDown = true; return Event.FACE_DOWN } }
        else if (gz > 3f) { downSince = -1L; if (upSince < 0) upSince = tMs; if (faceDown && tMs - upSince > 400L) { faceDown = false; return Event.FACE_UP } }
        if (faceDown) return null

        // Tilt (portrait, roughly upright or flat): gravity along x. Device x+ = right; gx > 0 means the left side is lower.
        val want = when { gx > TILT_X && abs(gz) < 9f -> -1; gx < -TILT_X && abs(gz) < 9f -> 1; abs(gx) < 1.5f -> 0; else -> tilt }
        if (want != tilt) {
            if (tiltSince == 0L) tiltSince = tMs
            if (tMs - tiltSince > TILT_HOLD_MS) {
                tilt = want; tiltSince = 0L
                return when (want) { -1 -> Event.TILT_LEFT; 1 -> Event.TILT_RIGHT; else -> Event.LEVEL }
            }
        } else tiltSince = 0L
        return null
    }

    /** -1..1 for the banana game (tilt steering). */
    fun steer(): Float = (-gx / 6f).coerceIn(-1f, 1f)

    /**
     * Signed roll from upright portrait, degrees.
     * 0 ≈ level, ±45 ≈ gentle downhill, ±90 ≈ on its side, near ±180 ≈ upside-down in hand.
     * Uses atan2(gx, gy): gx>0 (left side lower) → positive.
     */
    /** v10.36: filtered gravity (device axes) for screen-space tilt rolling. */
    fun gravX(): Float = gx
    fun gravY(): Float = gy

    fun tiltDeg(): Float = Math.toDegrees(atan2(gx.toDouble(), gy.toDouble())).toFloat()

    /** Screen flat on a table (or already in face-down sleep) — not a mid-air tip-over. */
    fun isTableFaceDown(): Boolean = faceDown || (gz < FACE_DOWN_Z && abs(gx) < 4f && abs(gy) < 4f)

    /** Upside-down in hand (gy strongly negative) without being flat on a table. */
    fun isUpsideDownInHand(): Boolean = !isTableFaceDown() && gy < -3.5f && abs(gz) < 8f
}
