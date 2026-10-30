package com.combo.monkeycompanion.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * v10.4: juggling balls that can be dropped (first M7 "shared physics toy").
 * A ball is HELD (in the juggling cascade, position worked out from the hands), LOOSE (a real body in screen px:
 * gravity, bounces off all four screen edges, rolls along the floor, settles, fades) or GONE.
 * Fixed arrays, no allocation per frame, no randomness: the host passes any random spread in.
 */
class JuggleBalls(val count: Int = 3) {
    enum class State { HELD, LOOSE, GONE }

    val state = Array(count) { State.HELD }
    val x = FloatArray(count); val y = FloatArray(count)
    val vx = FloatArray(count); val vy = FloatArray(count)
    /** Rolling angle (radians) for drawing the stripe. */
    val spin = FloatArray(count)
    /** 0..1 opacity: loose balls fade once they have rested on the floor a while. */
    val alpha = FloatArray(count) { 1f }
    private val rest = FloatArray(count)

    /** Cascade clock (seconds of juggling, sped up when wobbly). */
    var juggleT = 0f
    /** 0..1: how visible the held balls are (fade in after a refill / while juggling, out when put away). */
    var heldAlpha = 0f

    val heldCount get() = state.count { it == State.HELD }
    val looseActive get() = state.any { it == State.LOOSE }
    val allGone get() = state.all { it == State.GONE }

    val anyGone get() = state.any { it == State.GONE }

    /** Replace the balls that are gone (the ones still bouncing about stay loose). */
    fun refillGone() { for (i in 0 until count) if (state[i] == State.GONE) { state[i] = State.HELD; alpha[i] = 1f; rest[i] = 0f } }

    /** Fresh balls in the hands. */
    fun refill() { for (i in 0 until count) { state[i] = State.HELD; alpha[i] = 1f; rest[i] = 0f }; heldAlpha = 0f }

    /**
     * Where held ball [i] is in the cascade between the left hand ([lx], [ly]) and the right hand ([rx], [ry]):
     * a high arc left -> right, then a low pass back. [arc] = throw height in px; [wobble] 0..1 makes it ragged.
     */
    fun heldPosition(i: Int, lx: Float, ly: Float, rx: Float, ry: Float, arc: Float, wobble: Float, out: FloatArray) {
        val ph = (juggleT * 1.05f + i.toFloat() / count) % 1f
        val ragged = 1f + 0.35f * wobble * sin(juggleT * 13f + i * 2.1f)
        if (ph < 0.62f) {
            val k = ph / 0.62f
            out[0] = lx + (rx - lx) * k
            out[1] = ly + (ry - ly) * k - arc * ragged * 4f * k * (1f - k)
        } else {
            val k = (ph - 0.62f) / 0.38f
            out[0] = rx + (lx - rx) * k
            out[1] = ry + (ly - ry) * k - arc * 0.1f * 4f * k * (1f - k)
        }
        out[0] += wobble * 6f * sin(juggleT * 17f + i)
    }

    /** Let go of ball [i] at screen ([px], [py]) moving at ([pvx], [pvy]) px/s. */
    fun drop(i: Int, px: Float, py: Float, pvx: Float, pvy: Float) {
        if (state[i] != State.HELD) return
        state[i] = State.LOOSE; x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy; rest[i] = 0f; alpha[i] = 1f
    }

    /**
     * One frame for the loose balls inside the screen box ([left], [top], [right], [floor]) with radius [r] px.
     * Bounces off the edges, rolls and slows on the floor, rests [restSecs] then fades out over [fadeSecs].
     */
    fun stepLoose(dt: Float, left: Float, top: Float, right: Float, floor: Float, r: Float, density: Float,
                  gravityDp: Float = 2200f, restSecs: Float = 2.2f, fadeSecs: Float = 0.6f) {
        for (i in 0 until count) {
            if (state[i] != State.LOOSE) continue
            vy[i] += gravityDp * density * dt
            x[i] += vx[i] * dt; y[i] += vy[i] * dt
            spin[i] += vx[i] * dt / r
            if (x[i] < left + r) { x[i] = left + r; vx[i] = abs(vx[i]) * 0.62f }
            if (x[i] > right - r) { x[i] = right - r; vx[i] = -abs(vx[i]) * 0.62f }
            if (y[i] < top + r) { y[i] = top + r; vy[i] = abs(vy[i]) * 0.5f }
            if (y[i] >= floor - r) {
                y[i] = floor - r
                if (vy[i] > 0f) {
                    vy[i] = -vy[i] * 0.55f; vx[i] *= 0.88f
                    if (-vy[i] < 90f * density) vy[i] = 0f       // too slow to bounce again: rolling
                }
                if (vy[i] == 0f) {
                    vx[i] *= exp(-1.8f * dt)
                    if (abs(vx[i]) < 8f * density) rest[i] += dt
                }
            }
            if (rest[i] > restSecs) {
                alpha[i] -= dt / fadeSecs
                if (alpha[i] <= 0f) { alpha[i] = 0f; state[i] = State.GONE }
            }
        }
    }
}
