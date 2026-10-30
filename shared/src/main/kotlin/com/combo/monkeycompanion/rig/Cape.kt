package com.combo.monkeycompanion.rig

import android.graphics.Path
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * v3.2 Super Banana cape: a verlet chain pinned at the shoulders, simulated in hips space so it
 * follows the body. Gravity is rotated into body space (so it hangs down when flying level), the
 * flight wind pushes it toward the feet and adds a flutter. No allocation per frame.
 */
class Cape(private val n: Int = 7, private val seg: Float = 11.5f) {
    private val x = FloatArray(n); private val y = FloatArray(n)
    private val px = FloatArray(n); private val py = FloatArray(n)
    private var lastT = Float.NaN
    private var inited = false

    private val ax = 0f
    private val ay = MonkeyRig.SHOULDER_Y + 3f

    fun reset() { inited = false; lastT = Float.NaN }

    /** Advance to time [t] (seconds); [bodyRot] degrees, [wind] 0..1. */
    fun step(t: Float, bodyRot: Float, wind: Float) {
        if (!inited) {
            for (i in 0 until n) { x[i] = ax; y[i] = ay + i * seg; px[i] = x[i]; py[i] = y[i] }
            inited = true
            // Settle into shape (first frame / preview stills).
            repeat(90) { sim(1f / 60f, t - (90 - it) / 60f, bodyRot, wind) }
            lastT = t
            return
        }
        var dt = if (lastT.isNaN()) 1f / 60f else t - lastT
        lastT = t
        if (dt <= 0f) return
        if (dt > 0.05f) dt = 0.05f
        sim(dt, t, bodyRot, wind)
    }

    private fun sim(dt: Float, t: Float, bodyRot: Float, wind: Float) {
        val r = Math.toRadians(bodyRot.toDouble())
        val gx = (sin(r) * G).toFloat(); val gy = (cos(r) * G).toFloat()
        val w = wind.coerceIn(0f, 1.2f)
        for (i in 1 until n) {
            val f = i / (n - 1f)
            val flutter = sin(t * 15f + i * 1.3f) * 520f * w * f + sin(t * 4f + i) * 120f * f
            val axx = gx + flutter
            val ayy = gy + WIND * w
            val vx = (x[i] - px[i]) * DAMP; val vy = (y[i] - py[i]) * DAMP
            px[i] = x[i]; py[i] = y[i]
            x[i] += vx + axx * dt * dt
            y[i] += vy + ayy * dt * dt
        }
        x[0] = ax; y[0] = ay
        repeat(4) {
            for (i in 1 until n) {
                val dx = x[i] - x[i - 1]; val dy = y[i] - y[i - 1]
                val d = hypot(dx, dy).coerceAtLeast(0.001f)
                val diff = (d - seg) / d
                if (i == 1) { x[i] -= dx * diff; y[i] -= dy * diff }
                else { x[i] -= dx * diff * 0.5f; y[i] -= dy * diff * 0.5f; x[i - 1] += dx * diff * 0.5f; y[i - 1] += dy * diff * 0.5f }
            }
            x[0] = ax; y[0] = ay
            // Keep it behind the back, not swinging up through the head.
            for (i in 1 until n) if (y[i] < ay - 4f) y[i] = ay - 4f
        }
    }

    /** The cape outline (hips space) into [out]: a ribbon widening toward the hem. */
    fun outline(out: Path) {
        out.reset()
        for (i in 0 until n) {
            val j0 = if (i == 0) 0 else i - 1; val j1 = if (i == n - 1) n - 1 else i + 1
            var tx = x[j1] - x[j0]; var ty = y[j1] - y[j0]
            val l = hypot(tx, ty).coerceAtLeast(0.001f); tx /= l; ty /= l
            val hw = halfWidth(i)
            val lx = x[i] + ty * hw; val ly = y[i] - tx * hw
            if (i == 0) out.moveTo(lx, ly) else out.lineTo(lx, ly)
        }
        for (i in n - 1 downTo 0) {
            val j0 = if (i == 0) 0 else i - 1; val j1 = if (i == n - 1) n - 1 else i + 1
            var tx = x[j1] - x[j0]; var ty = y[j1] - y[j0]
            val l = hypot(tx, ty).coerceAtLeast(0.001f); tx /= l; ty /= l
            val hw = halfWidth(i)
            out.lineTo(x[i] - ty * hw, y[i] + tx * hw)
        }
        out.close()
    }

    private fun halfWidth(i: Int) = 15f + 25f * (i / (n - 1f))

    /** Hem (last point) for tests. */
    fun hem(out: FloatArray) { out[0] = x[n - 1]; out[1] = y[n - 1] }

    companion object {
        private const val G = 900f
        private const val WIND = 2600f
        private const val DAMP = 0.965f
    }
}
