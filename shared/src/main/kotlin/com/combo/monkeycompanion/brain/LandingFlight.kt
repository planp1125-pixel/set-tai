package com.combo.monkeycompanion.brain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * v5 power-landing flight ([Landings.Style.ROCKET_ARC]): instead of a straight drop, a rocket-like curved flight
 * across the screen (loop-the-loop, corkscrew spiral, S-curve or big swoop), banking with the path, then each
 * character's own finish: Superboy's fist-first touchdown, Armour Anna's jet-brake hover + thud, the monkey's
 * leg-fire crash landing. A new random path every time, never the same shape twice in a row. Pure, unit-tested.
 */
object LandingFlight {
    enum class Path(val label: String) { LOOP("loop-the-loop"), SPIRAL("corkscrew"), S_CURVE("S-curve"), SWOOP("swoop") }
    enum class Finish(val label: String) { FIST("fist-first touchdown"), HOVER_THUD("jet-brake hover, then thud"), CRASH("crash landing") }

    fun finish(characterId: String) = when {
        characterId == "superboy" -> Finish.FIST
        characterId == "armour" -> Finish.HOVER_THUD
        characterId.startsWith("custom") -> {
            val fs = com.combo.monkeycompanion.character.CustomCompanionRegistry.specById(characterId)?.flightStyle
            if (fs == com.combo.monkeycompanion.character.FlightStyle.IRONBOY_JET_FLY) Finish.HOVER_THUD else Finish.FIST
        }
        else -> Finish.CRASH
    }

    /** Hover height above the floor for Anna's brake (dp). */
    const val HOVER_DP = 70f
    const val HOVER_SECS = 0.65f
    /** Seconds of the touchdown pose (fist / thud / tumble). */
    fun touchSecs(f: Finish) = when (f) { Finish.FIST -> 0.8f; Finish.HOVER_THUD -> 0.55f; Finish.CRASH -> 1.1f }

    fun pickPath(last: Path?, rnd: Random = Random): Path {
        val opts = Path.entries.filter { it != last }
        return opts[rnd.nextInt(opts.size)]
    }

    /** A planned flight of the box centre from (x0, y0) to (x1, y1) on a [w] x [h] screen, never above [top]. */
    class Plan(
        val path: Path, val finish: Finish, val dur: Float, val dir: Float,
        val x0: Float, val y0: Float, val x1: Float, val y1: Float,
        val c1x: Float, val c1y: Float, val c2x: Float, val c2y: Float,
        val r: Float, val turns: Float, val minX: Float, val maxX: Float, val minY: Float, val maxY: Float,
        /** v6 big flights: waypoints x0,y0,x1,y1,... (Catmull-Rom through them) instead of the cubic. */
        val pts: FloatArray? = null,
    )

    fun plan(path: Path, finish: Finish, x0: Float, y0: Float, x1: Float, y1: Float, w: Float, h: Float,
             top: Float, margin: Float, rnd: Random = Random, slow: Float = 1f): Plan {
        val dir = if (x1 >= x0) 1f else -1f
        val j = { a: Float -> 1f + (rnd.nextFloat() - 0.5f) * a }   // jitter: a random path each time
        val lift = (y0 - top).coerceAtLeast(0f)
        val (c1x, c1y, c2x, c2y) = when (path) {
            Path.SWOOP -> listOf(x0 + dir * 0.55f * w * j(0.3f), top + lift * 0.1f * j(0.5f), x1 + dir * 0.25f * w * j(0.4f), y1 - 0.25f * h * j(0.4f))
            Path.S_CURVE -> listOf(x0 + dir * 0.95f * w * j(0.2f), y0 - 0.32f * h * j(0.3f), x1 - dir * 0.95f * w * j(0.2f), y1 - 0.3f * h * j(0.3f))
            Path.LOOP -> listOf(x0 + dir * 0.3f * w * j(0.3f), y0 - 0.3f * h * j(0.3f), x1 - dir * 0.2f * w * j(0.3f), y1 - 0.3f * h * j(0.3f))
            Path.SPIRAL -> listOf(x0 + dir * 0.2f * w * j(0.3f), y0 - 0.38f * h * j(0.3f), x1 - dir * 0.1f * w * j(0.3f), y1 - 0.42f * h * j(0.3f))
        }
        val r = when (path) { Path.LOOP -> 0.15f * w * j(0.25f); Path.SPIRAL -> 0.11f * w * j(0.25f); else -> 0f }
        val turns = when (path) { Path.SPIRAL -> 2f + rnd.nextInt(2); Path.LOOP -> 1f; else -> 0f }
        val dur = when (path) { Path.SWOOP -> 1.9f; Path.S_CURVE -> 2.3f; Path.LOOP -> 2.5f; Path.SPIRAL -> 2.7f } * j(0.15f) * slow
        return Plan(path, finish, dur, dir, x0, y0, x1, y1, c1x, c1y.coerceAtLeast(top), c2x, c2y.coerceAtLeast(top), r, turns,
            margin, w - margin, top, maxOf(y1, y0))
    }

    /** Horizontal extent of a planned path (px), sampled. */
    fun xSpan(p: Plan, steps: Int = 80): Float {
        val o = FloatArray(2); var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
        for (i in 0..steps) { at(p, i / steps.toFloat(), o); lo = minOf(lo, o[0]); hi = maxOf(hi, o[0]) }
        return hi - lo
    }

    /**
     * v6 swipe-down landing: a BIG flight. A smooth curve through waypoints that cross the screen (far side high up,
     * back across, then down to the landing spot), plus the path's own flourish (loop-the-loop, corkscrew turns, an
     * extra S wiggle). Covers at least half the screen width; 1.8-3 s; then the character's own finish.
     */
    fun planBig(path: Path, finish: Finish, x0: Float, y0: Float, groundY: Float, w: Float, h: Float, top: Float, margin: Float,
                rnd: Random = Random): Plan {
        val lo = margin + 4f; val hi = (w - margin - 4f).coerceAtLeast(lo + 1f)
        val toRight = x0 < w / 2f                       // first head for the side with more room
        fun xs(f: Float) = (lo + (hi - lo) * (if (toRight) f else 1f - f))
        val high = top + (groundY - top) * (0.05f + 0.1f * rnd.nextFloat())
        val mid = top + (groundY - top) * (0.35f + 0.15f * rnd.nextFloat())
        val pts = ArrayList<Float>()
        fun p(x: Float, y: Float) { pts += x.coerceIn(lo, hi); pts += y.coerceIn(top, maxOf(groundY, y0)) }
        p(x0, y0)
        when (path) {
            Path.S_CURVE -> { p(xs(0.97f), mid); p(xs(0.03f), high + (mid - high) * 0.4f); p(xs(0.85f), mid + (groundY - mid) * 0.45f) }
            Path.SWOOP -> { p(xs(0.95f), high); p(xs(0.05f + 0.1f * rnd.nextFloat()), mid + (groundY - mid) * 0.5f) }
            else -> { p(xs(0.97f), high); p(xs(0.03f), mid) }
        }
        val x1 = xs(0.45f + 0.25f * rnd.nextFloat()); p(x1, groundY)
        val r = when (path) { Path.LOOP -> 0.13f * w * (0.9f + 0.2f * rnd.nextFloat()); Path.SPIRAL -> 0.08f * w * (0.9f + 0.2f * rnd.nextFloat()); else -> 0f }
        val turns = when (path) { Path.SPIRAL -> 2f + rnd.nextInt(2); Path.LOOP -> 1f; else -> 0f }
        val dur = (when (path) { Path.SWOOP -> 2.0f; Path.S_CURVE -> 2.6f; Path.LOOP -> 2.7f; Path.SPIRAL -> 2.9f } * (0.95f + 0.1f * rnd.nextFloat())).coerceIn(1.8f, 3f)
        val dir = if (toRight) 1f else -1f
        return Plan(path, finish, dur, dir, x0, y0, x1, groundY, 0f, 0f, 0f, 0f, r, turns,
            margin, w - margin, top, maxOf(groundY, y0), pts.toFloatArray())
    }

    private fun catmull(p: FloatArray, u: Float, out: FloatArray) {
        val n = p.size / 2 - 1
        val f = (u * n).coerceIn(0f, n - 1e-4f); val i = f.toInt(); val t = f - i
        fun px(k: Int) = p[2 * k.coerceIn(0, n)]
        fun py(k: Int) = p[2 * k.coerceIn(0, n) + 1]
        val t2 = t * t; val t3 = t2 * t
        fun cr(a: Float, b: Float, c: Float, d: Float) = 0.5f * (2f * b + (-a + c) * t + (2f * a - 5f * b + 4f * c - d) * t2 + (-a + 3f * b - 3f * c + d) * t3)
        out[0] = cr(px(i - 1), px(i), px(i + 1), px(i + 2)); out[1] = cr(py(i - 1), py(i), py(i + 1), py(i + 2))
    }

    /** Eased progress for flight time [k] 0..1: a rocket accelerates; Anna eases into her brake. */
    fun ease(f: Finish, k: Float): Float {
        val c = k.coerceIn(0f, 1f)
        return if (f == Finish.HOVER_THUD) c * c * (3f - 2f * c) else c.pow(1.2f)
    }

    /** Box-centre position at progress [u] 0..1 into [out]. */
    fun at(p: Plan, u: Float, out: FloatArray): FloatArray {
        val t = u.coerceIn(0f, 1f); val m = 1f - t
        var x: Float; var y: Float
        if (p.pts != null) { catmull(p.pts, t, out); x = out[0]; y = out[1] }
        else {
            x = m * m * m * p.x0 + 3 * m * m * t * p.c1x + 3 * m * t * t * p.c2x + t * t * t * p.x1
            y = m * m * m * p.y0 + 3 * m * m * t * p.c1y + 3 * m * t * t * p.c2y + t * t * t * p.y1
        }
        when (p.path) {
            Path.LOOP -> if (t in 0.3f..0.66f) {
                val v = (t - 0.3f) / 0.36f; val a = 2f * PI.toFloat() * v
                x += p.dir * p.r * sin(a); y -= p.r * (1f - cos(a))
            }
            Path.SPIRAL -> {
                val e = sin(PI.toFloat() * t); val a = 2f * PI.toFloat() * p.turns * t
                x += p.dir * p.r * e * sin(a); y -= p.r * e * (1f - cos(a)) * 0.8f
            }
            else -> Unit
        }
        // Stay on screen; the end point is exact.
        out[0] = if (t >= 1f) p.x1 else x.coerceIn(p.minX, p.maxX)
        out[1] = if (t >= 1f) p.y1 else y.coerceIn(p.minY, p.maxY)
        return out
    }

    private val a = FloatArray(2); private val b = FloatArray(2)

    /** Direction of travel in degrees (0 = right, 90 = down). */
    fun heading(p: Plan, u: Float): Float {
        val e = 0.012f
        at(p, (u - e).coerceAtLeast(0f), a); at(p, (u + e).coerceAtMost(1f), b)
        return Math.toDegrees(atan2((b[1] - a[1]).toDouble(), (b[0] - a[0]).toDouble())).toFloat()
    }

    /**
     * Body rotation for drawing (degrees, 0 = upright): head first along the path (a rocket banks with its
     * flight), easing back upright for the touchdown in the last 15 %.
     */
    fun bodyRot(p: Plan, u: Float): Float {
        var r = heading(p, u) + 90f
        while (r > 180f) r -= 360f
        while (r < -180f) r += 360f
        val settle = ((1f - u) / 0.15f).coerceIn(0f, 1f)
        val target = if (p.finish == Finish.FIST) 0f else 0f
        return target + (r - target) * settle
    }

    /** How far a path strays from the straight line between its ends (px): proves it's a curve. */
    fun maxDeviation(p: Plan, steps: Int = 60): Float {
        val o = FloatArray(2); var best = 0f
        val dx = p.x1 - p.x0; val dy = p.y1 - p.y0; val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
        for (i in 0..steps) { at(p, i / steps.toFloat(), o); best = maxOf(best, abs((o[0] - p.x0) * dy - (o[1] - p.y0) * dx) / len) }
        return best
    }
}
