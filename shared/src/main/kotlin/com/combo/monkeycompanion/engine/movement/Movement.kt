package com.combo.monkeycompanion.engine.movement

import com.combo.monkeycompanion.character.Character
import com.combo.monkeycompanion.character.Locomotion
import kotlin.math.abs
import kotlin.math.sin

/**
 * v10.32 Movement engine: where a character is on a show stage and how it gets somewhere, for ANY body.
 * Coordinates are screen fractions (x 0..1 left→right, y 0..1 top→bottom; [FLOOR] = standing line).
 * The style comes from the body's [Character.locomotion]: FLY/FLOAT bodies fly in the air and hover with a bob;
 * walkers walk along the floor (a flier target in the air is dropped to the floor for them).
 * Pure: a function of time and the commands given, no Random (GoldenTrace-safe).
 */
enum class Edge { LEFT, RIGHT, TOP, BOTTOM }

class Mover(val canFly: Boolean) {
    companion object {
        const val FLOOR = 0.86f
        fun of(c: Character) = Mover(c.can(Locomotion.FLY) || c.can(Locomotion.FLOAT))
        private fun ease(k: Float) = k * k * (3f - 2f * k)
    }

    var x = 0.5f; private set
    var y = FLOOR; private set
    /** 1 = facing right, -1 = facing left. */
    var facing = 1f
    /** 0 invisible .. 1 fully there (vanish / appear). */
    var alpha = 1f; private set
    /** True while travelling (walk / fly animation). */
    var moving = false; private set
    var hovering = false; private set
    /** 0..1 vanish (or appear) progress, for the exit-style smoke puff. */
    var puff = 0f; private set

    private var fx = x; private var fy = y; private var tx = x; private var ty = y
    private var t0 = 0f; private var dur = 0f
    private var hoverT0 = 0f
    private var fadeT0 = -1f; private var fadeDur = 0.6f; private var fadeTo = 1f; private var fadeFrom = 1f

    fun place(x: Float, y: Float) { this.x = x; this.y = clampY(y); fx = this.x; fy = this.y; tx = this.x; ty = this.y; dur = 0f; moving = false }
    private fun clampY(y: Float) = if (canFly) y.coerceIn(0.08f, FLOOR) else FLOOR

    /** Travel to (x, y) over [secs] starting at [now]. */
    fun moveTo(now: Float, x: Float, y: Float, secs: Float) {
        fx = this.x; fy = this.y; tx = x; ty = clampY(y); t0 = now; dur = secs.coerceAtLeast(0.05f); moving = true; hovering = false
        if (abs(tx - fx) > 0.01f) facing = if (tx > fx) 1f else -1f
    }

    /** Come in from off-screen at [edge] to (x, y). */
    fun enterFrom(now: Float, edge: Edge, x: Float, y: Float, secs: Float) {
        val ny = clampY(y)
        when (edge) {
            Edge.LEFT -> place(-0.15f, ny); Edge.RIGHT -> place(1.15f, ny)
            Edge.TOP -> place(x, if (canFly) -0.15f else ny); Edge.BOTTOM -> place(x, 1.15f)
        }
        alpha = 1f; fadeT0 = -1f
        moveTo(now, x, y, secs)
    }

    fun exitTo(now: Float, edge: Edge, secs: Float) = when (edge) {
        Edge.LEFT -> moveTo(now, -0.2f, y, secs); Edge.RIGHT -> moveTo(now, 1.2f, y, secs)
        Edge.TOP -> moveTo(now, x, -0.2f, secs); Edge.BOTTOM -> moveTo(now, x, 1.2f, secs)
    }

    /** Stay still in the air (fliers bob gently; walkers just stand). */
    fun hover(now: Float) { if (moving) { x = tx; y = ty; moving = false }; hovering = canFly; hoverT0 = now }

    fun vanish(now: Float, secs: Float = 0.6f) { fadeFrom = alpha; fadeTo = 0f; fadeT0 = now; fadeDur = secs }
    fun appear(now: Float, secs: Float = 0.6f) { fadeFrom = alpha; fadeTo = 1f; fadeT0 = now; fadeDur = secs }
    val gone get() = alpha <= 0.02f

    /** Advance to show time [now]. */
    fun step(now: Float) {
        if (moving) {
            val k = ((now - t0) / dur).coerceIn(0f, 1f); val e = ease(k)
            x = fx + (tx - fx) * e; y = fy + (ty - fy) * e
            if (k >= 1f) { moving = false; hover(now) }
        }
        puff = 0f
        if (fadeT0 >= 0f) {
            val k = ((now - fadeT0) / fadeDur).coerceIn(0f, 1f)
            alpha = fadeFrom + (fadeTo - fadeFrom) * k
            puff = sin(k * Math.PI.toFloat())
            if (k >= 1f) fadeT0 = -1f
        }
    }

    /** Draw offset for the hover bob (screen fraction, negative = up). */
    fun bob(now: Float): Float = if (canFly && !moving) -0.006f * sin((now - hoverT0) * 2.4f) else 0f
}
