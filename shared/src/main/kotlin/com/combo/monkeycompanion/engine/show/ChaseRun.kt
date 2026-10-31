package com.combo.monkeycompanion.engine.show

import com.combo.monkeycompanion.brain.MonkeyState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * v10.13.1: a real Tom-and-Jerry style chase over the WHOLE screen: along the floor, up the walls, across the top,
 * diagonal leaps through the middle. The mouse runs ahead on one continuous route; the cat runs the same route
 * close behind. Pure function of time: smooth, no per-frame state, no flip-flopping.
 * Coordinates are in screen-width units: x in 0..1, y in 0..aspect (aspect = height / width), y down.
 */
class ChaseRun private constructor(val aspect: Float, val legs: List<Leg>, val ending: EndKind, rnd: Random) {
    /** One stretch of the route. Surface legs: feet on a screen edge (angle = clockwise degrees of "up").
     *  Jump legs: a leap through the air from [a] to [b], turning from [ang0] to [ang1]. */
    class Leg(val ax: Float, val ay: Float, val bx: Float, val by: Float, val ang0: Float, val ang1: Float, val jump: Boolean, val hop: Float = 0f) {
        val len = hypot(bx - ax, by - ay).coerceAtLeast(1e-4f)
        var d0 = 0f
        /** Facing in the character's own frame (+1 = its local right), fixed for the whole leg. */
        val facing: Float = run {
            val a = Math.toRadians(ang0.toDouble()); val lx = cos(a).toFloat(); val ly = sin(a).toFloat()
            if ((bx - ax) * lx + (by - ay) * ly >= 0f) 1f else -1f
        }
    }

    class Pos {
        var x = 0f; var y = 0f; var ang = 0f; var facing = 1f; var state = MonkeyState.DASH
        var dist = 0f; var visible = true; var sx = 1f; var sy = 1f
    }

    val length: Float
    val intro = 0.9f
    val run: Float
    val endDur = 2.6f
    val total: Float
    val lines = ArrayList<Line>()
    private val tau = 0.35f
    private val v: Float

    init {
        var d = 0f
        for (l in legs) { l.d0 = d; d += l.len }
        length = d
        run = (length / 0.62f).coerceIn(8.5f, 11.5f)
        v = length / (run - tau)
        total = intro + run + endDur
        fun pick(vararg s: String) = s[rnd.nextInt(s.size)]
        lines += Line(0.0f, 1.2f, Role.RAT, pick("Pidi paakalam! 🐭", "Catch me if you can! 😜"))
        lines += Line(0.5f, 1.2f, Role.CAT, pick("Nillu da!! 😾", "Vidamaaten! 😼"))
        lines += Line(intro + run * 0.35f, 1.3f, Role.RAT, pick("Wall-la kooda odaren! 🧗", "Mela vaa poonai! 😝"))
        lines += Line(intro + run * 0.62f, 1.2f, Role.CAT, pick("Kitta vandhuten!! 😼", "Innum konjam!! 💢"))
        val e0 = intro + run
        when (ending) {
            EndKind.RAT_WINS -> { lines += Line(e0 + 0.1f, 1.4f, Role.RAT, pick("Chittu wins! 🏆", "Laddu enakku dhaan! 🏆")); lines += Line(e0 + 1.3f, 1.2f, Role.CAT, "Adutha thadava paaru! 😾") }
            EndKind.CAT_BONKED -> { lines += Line(e0 + 0.3f, 1.3f, Role.CAT, "DOING!! 💫"); lines += Line(e0 + 1.2f, 1.2f, Role.RAT, "Hehe, wall-ku sorry sollu! 😆") }
            EndKind.FRIENDS -> { lines += Line(e0 + 0.2f, 1.3f, Role.CAT, "Seri seri… friends? 🤝"); lines += Line(e0 + 1.2f, 1.2f, Role.RAT, "Friends! ❤️") }
            EndKind.CAT_BALL -> { lines += Line(e0 + 0.2f, 1.3f, Role.CAT, "AAAH en vaal!! 🙀"); lines += Line(e0 + 1.3f, 1.2f, Role.RAT, "Poonai ball! ⚽😂") }
        }
        lines.sortBy { it.at }
    }

    /** Distance the mouse has run at time [t]. Starts from rest (no jerk), then a steady sprint. */
    private fun ratDist(t: Float): Float {
        val u = (t - intro).coerceIn(0f, run)
        return (GAP0 + v * (u - tau * (1f - exp(-u / tau)))).coerceAtMost(length)
    }

    /** How far the cat trails: close behind, with near-misses. Smooth. */
    fun gap(t: Float): Float = 0.15f + 0.07f * sin(t * 1.25f) - 0.03f * sin(t * 2.9f + 1f)

    fun frame(role: Role, t: Float, out: Pos): Pos {
        val tc = t.coerceIn(0f, total)
        val rd = ratDist(tc)
        val d = if (role == Role.RAT) rd else {
            val u = (tc - intro).coerceIn(0f, run)
            // cat starts at the route start and keeps a smooth trailing gap (never ahead of the mouse)
            (rd - gap(tc) * (u / 0.8f).coerceAtMost(1f) - GAP0 * (1f - (u / 0.8f).coerceAtMost(1f))).coerceIn(0f, (length - 0.13f).coerceAtLeast(0f))
        }
        at(d, out)
        out.dist = d; out.visible = true; out.sx = 1f; out.sy = 1f
        val e = tc - intro - run
        out.state = when {
            tc < intro -> if (role == Role.RAT) MonkeyState.GIGGLE else MonkeyState.ANGRY
            e < 0f -> if (legAt(d).jump) MonkeyState.JUMP else MonkeyState.DASH
            else -> endState(role, e)
        }
        if (e >= 0f) endPose(role, e, out)
        return out
    }

    private fun endState(role: Role, e: Float): MonkeyState = when (ending) {
        EndKind.RAT_WINS -> if (role == Role.RAT) MonkeyState.DANCE else MonkeyState.SAD
        EndKind.CAT_BONKED -> if (role == Role.RAT) MonkeyState.GIGGLE else if (e < 0.35f) MonkeyState.DASH else MonkeyState.SAD
        EndKind.FRIENDS -> MonkeyState.HAPPY_PAT
        EndKind.CAT_BALL -> if (role == Role.RAT) MonkeyState.GIGGLE else MonkeyState.SLEEP
    }

    private fun endPose(role: Role, e: Float, out: Pos) {
        if (role != Role.CAT) return
        val k = (e / endDur).coerceIn(0f, 1f)
        when (ending) {
            EndKind.CAT_BONKED -> { val q = ((e - 0.35f) / 0.25f).coerceIn(0f, 1f); out.sx = 1f - 0.18f * q * (1f - k); out.sy = 1f + 0.15f * q * (1f - k) }
            EndKind.CAT_BALL -> { out.ang += 720f * smooth(k) * out.facing; out.sx = 0.85f; out.sy = 0.8f }
            else -> {}
        }
    }

    fun legAt(d: Float): Leg = legs.lastOrNull { d >= it.d0 } ?: legs.first()

    /** Point on the route at distance [d], with a smoothly blended body angle around corners. */
    fun at(d: Float, out: Pos) {
        val i = legs.indexOf(legAt(d)); val l = legs[i]
        val u = ((d - l.d0) / l.len).coerceIn(0f, 1f)
        out.x = l.ax + (l.bx - l.ax) * u
        out.y = l.ay + (l.by - l.ay) * u
        if (l.jump) {
            out.y -= l.hop * sin(PI.toFloat() * u)
            out.ang = l.ang0 + (l.ang1 - l.ang0) * smooth(u)
        } else {
            out.ang = l.ang0
            // round the corners: blend with the neighbour leg's angle over a short distance
            val bl = 0.07f
            val into = d - l.d0; val left = l.d0 + l.len - d
            if (into < bl && i > 0 && !legs[i - 1].jump) out.ang = blend(legs[i - 1].ang0, l.ang0, 0.5f + 0.5f * into / bl)
            else if (left < bl && i < legs.size - 1 && !legs[i + 1].jump) out.ang = blend(l.ang0, legs[i + 1].ang0, 0.5f - 0.5f * left / bl)
        }
        out.facing = l.facing
    }

    private fun blend(a: Float, b: Float, k: Float): Float {
        var db = b - a; while (db > 180f) db -= 360f; while (db < -180f) db += 360f
        return a + db * smooth(k.coerceIn(0f, 1f))
    }

    companion object {
        const val GAP0 = 0.2f
        const val M = 0.0f
        private fun smooth(k: Float) = k * k * (3f - 2f * k)

        fun random(rnd: Random, ending: EndKind, aspect: Float = 2.1f): ChaseRun {
            val A = aspect
            val B = 0f; val R = -90f; val T = 180f; val L = 90f
            fun s(ax: Float, ay: Float, bx: Float, by: Float, ang: Float) = Leg(ax, ay, bx, by, ang, ang, false)
            fun j(ax: Float, ay: Float, bx: Float, by: Float, a0: Float, a1: Float, hop: Float = 0.15f) = Leg(ax, ay, bx, by, a0, a1, true, hop)
            val routes = listOf(
                // floor → right wall up → ceiling → diagonal leap down → floor
                listOf(s(0.08f, A, 1f, A, B), s(1f, A, 1f, 0f, R), s(1f, 0f, 0.15f, 0f, T), j(0.15f, 0f, 0.8f, A, T, B + 360f, 0.1f), s(0.8f, A, 0.35f, A, B)),
                // floor → right wall halfway → leap across the middle to the left wall → up → ceiling → down the right wall
                listOf(s(0.08f, A, 1f, A, B), s(1f, A, 1f, A * 0.55f, R), j(1f, A * 0.55f, 0f, A * 0.4f, R, L, 0.12f), s(0f, A * 0.4f, 0f, 0f, L), s(0f, 0f, 1f, 0f, T), s(1f, 0f, 1f, A * 0.3f, R)),
                // floor → right wall → ceiling to the middle → drop with a spin → floor → left wall up a bit → leap down
                listOf(s(0.08f, A, 1f, A, B), s(1f, A, 1f, 0f, R), s(1f, 0f, 0.5f, 0f, T), j(0.5f, 0f, 0.5f, A, T, B + 360f, 0f), s(0.5f, A, 0f, A, B), s(0f, A, 0f, A * 0.6f, L), j(0f, A * 0.6f, 0.45f, A, L, B, 0.12f)),
                // zig-zag: floor → right wall up a third → diagonal to the left wall → up → diagonal to the right wall → ceiling
                listOf(s(0.08f, A, 1f, A, B), s(1f, A, 1f, A * 0.66f, R), j(1f, A * 0.66f, 0f, A * 0.45f, R, L, 0.1f), s(0f, A * 0.45f, 0f, A * 0.3f, L), j(0f, A * 0.3f, 1f, A * 0.12f, L, R, 0.1f), s(1f, A * 0.12f, 1f, 0f, R), s(1f, 0f, 0.4f, 0f, T)),
            )
            var legs = routes[rnd.nextInt(routes.size)]
            if (rnd.nextBoolean()) legs = legs.map { Leg(1f - it.ax, it.ay, 1f - it.bx, it.by, -it.ang0, -it.ang1, it.jump, it.hop) }
            return ChaseRun(A, legs, ending, rnd)
        }
    }
}
