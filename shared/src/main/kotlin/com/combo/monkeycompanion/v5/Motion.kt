package com.combo.monkeycompanion.v5

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * v5 shake / tilt / face-down from the accelerometer (m/s², device axes). Fed at SENSOR_DELAY_UI (~60 ms) only
 * while the overlay is visible and the screen is on. Pure: returns at most one event per sample.
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

    companion object {
        const val SHAKE_G = 13f           // linear acceleration peak (m/s²)
        const val HARD_G = 26f
        const val SHAKE_PEAKS = 3
        const val WINDOW_MS = 900L
        const val COOLDOWN_MS = 2500L
        const val TILT_X = 4.2f           // ~25°
        const val TILT_HOLD_MS = 700L
        const val FACE_DOWN_Z = -8.5f
        const val FACE_HOLD_MS = 1500L
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
}
