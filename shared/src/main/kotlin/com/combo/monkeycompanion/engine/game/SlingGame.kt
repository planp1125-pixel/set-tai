package com.combo.monkeycompanion.engine.game

import com.combo.monkeycompanion.engine.feel.TouchFeel
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.random.Random

/**
 * v10.40 Slingshot game, the rules only (no Android, no drawing): the character you use is the stone of a slingshot,
 * a random other character moves across the screen, you pull back and let go. Hit = both fly on together and splat
 * on the screen edge (+1, streak grows). Miss = only the stone splats (streak back to 0). A round is [roundSec] long.
 *
 * Units: x and y are in SCREEN WIDTHS (x 0..1, y 0..[aspect]), y grows down, so speeds are the same in both
 * directions. Positions are the character's FEET; its body centre is [BODY_H]/2 above.
 * Has its own [Random] (seeded): it never touches the companion's frame-loop random numbers.
 */
class SlingGame(
    val aspect: Float,
    val bulletId: String,
    pool: List<String>,
    private val fliers: Set<String> = emptySet(),
    seed: Long = 1L,
    val roundSec: Float = ROUND_SEC,
    /** v10.41: how each character escapes (default [Trick.HOP]). */
    private val tricks: Map<String, Trick> = emptyMap(),
) {
    enum class Phase { AIM, FLY, SPLAT, RETURN, OVER }
    /**
     * HIT = the stone knocked the target (scores here). WALL_STONE / WALL_TARGET = that one slammed into an edge after
     * a hit. DOUBLE_SPLAT = both are down. SPLAT = a miss: the stone slammed alone.
     */
    enum class Event { SNAP, HIT, SPLAT, DOUBLE_SPLAT, TARGET_IN, TARGET_OUT, LAST_SECONDS, OVER, LEVEL_UP, TRICK, WALL_STONE, WALL_TARGET }
    /**
     * v10.41 each character's own escape. It always shows a short warning first ([Target.warning]), so a hit is skill:
     * FLY_UP shoots up and drifts back, VANISH disappears (can't be hit) and pops up somewhere else, DASH sprints,
     * HOP jumps, NONE never escapes (big and slow).
     */
    enum class Trick { NONE, HOP, DASH, FLY_UP, VANISH }
    enum class Wall { LEFT, RIGHT, TOP, FLOOR }

    class Target(val id: String, val flies: Boolean, var x: Float, var y: Float, var vx: Float) {
        var age = 0f
        /** Knocked by the stone (v10.42: it flies off on its own, tumbling, until it slams into an edge). */
        var carried = false
        /** v10.42 knock flight: speed, tumble angle (degrees) and its speed, and the slam. */
        var kvx = 0f; var kvy = 0f
        var spin = 0f; var omega = 0f
        var down = false
        var wall = Wall.TOP
        var splatT = 0f
        var leaving = false
        /** Offset from the stone while carried. */
        var offX = 0f; var offY = 0f
        var laughUntil = -1f
        var nextTurn = 0f
        // v10.41 escape trick
        var trick = Trick.HOP
        /** 0 = no trick now, 1 = warning, 2 = escaping. */
        var trickPhase = 0
        var trickT = 0f
        var nextTrick = 0f
        /** Extra height from a hop / fly-up (screen widths, negative = up). */
        var yOff = 0f
        /** 1 = solid, 0 = vanished. */
        var alpha = 1f
        var dash = 1f
        val warning: Boolean get() = trickPhase == 1
        val hittable: Boolean get() = alpha > 0.5f
        val facing: Float get() = if (vx >= 0f) 1f else -1f
    }

    private val rng = Random(seed)
    private val others = pool.distinct().filter { it != bulletId }.ifEmpty { listOf(bulletId) }

    val homeX = 0.5f
    val homeY = aspect * HOME_Y
    var phase = Phase.AIM; private set
    /** Stone feet position and speed. */
    var bx = homeX; private set
    var by = homeY; private set
    var bvx = 0f; private set
    var bvy = 0f; private set
    /** How far the stone is pulled from home while aiming (screen widths). */
    var pullX = 0f; private set
    var pullY = 0f; private set
    var t = 0f; private set
    var timeLeft = roundSec; private set
    var score = 0; private set
    var streak = 0; private set
    var bestStreak = 0; private set
    var shots = 0; private set
    /** v10.41: 1, 2, 3… one level every [HITS_PER_LEVEL] hits: faster targets, more escapes. */
    val level: Int get() = 1 + score / HITS_PER_LEVEL
    /** Seconds added to the clock so far by hits. */
    var bonus = 0f; private set
    var target: Target? = null; private set
    /** Where the stone slammed (valid once [stoneDown]). */
    var wall = Wall.TOP; private set
    /** Time since everything came to rest (the [Phase.SPLAT] clock). */
    var splatT = 0f; private set
    // v10.42 real collision: the stone tumbles after a hit and slams on its own
    /** Stone body angle in degrees (0 = upright). Follows the flight direction, then tumbles after a hit. */
    var stoneSpin = 0f; private set
    private var stoneOmega = 0f
    /** The stone has slammed into an edge; [stoneSplatT] counts from then. */
    var stoneDown = false; private set
    var stoneSplatT = 0f; private set
    /** The last knock: where the bodies met, and how hard (0..1). */
    var hitX = 0f; private set
    var hitY = 0f; private set
    var hitForce = 0f; private set
    /** How hard the last wall slam was (0..1), for the buzz. */
    var slamForce = 0f; private set
    /** The last splat carried the target (a hit). */
    var splatDouble = false; private set
    var returnT = 0f; private set
    private var lastTargetId: String? = null
    private var warned = false
    private val pending = ArrayList<Event>()

    /** Events since the last call (sounds, buzzes, words), oldest first. */
    fun drain(): List<Event> { if (pending.isEmpty()) return emptyList(); val out = ArrayList(pending); pending.clear(); return out }

    val pulling: Boolean get() = phase == Phase.AIM && (pullX != 0f || pullY != 0f)
    /** 0..1: how hard the band is pulled. */
    val tension: Float get() = (hypot(pullX, pullY) / MAX_PULL).coerceIn(0f, 1f)
    val over: Boolean get() = phase == Phase.OVER

    /** Finger moved: the stone follows by ([dx], [dy]) screen widths from home, up to [MAX_PULL]. */
    fun pull(dx: Float, dy: Float) {
        if (phase != Phase.AIM) return
        val len = hypot(dx, dy)
        val k = if (len > MAX_PULL) MAX_PULL / len else 1f
        pullX = dx * k; pullY = dy * k
        bx = homeX + pullX; by = homeY + pullY
    }

    /** Finger lifted: fire (true) or, if hardly pulled, snap back to home (false). */
    fun release(): Boolean {
        if (phase != Phase.AIM) return false
        val len = hypot(pullX, pullY)
        if (len < MIN_PULL) { resetStone(); return false }
        val sp = launchSpeed(tension)
        bvx = -pullX / len * sp; bvy = -pullY / len * sp
        pullX = 0f; pullY = 0f
        phase = Phase.FLY; shots++
        stoneDown = false; stoneSplatT = 0f; stoneOmega = 0f; knockT = 0f
        stoneSpin = Math.toDegrees(atan2(bvy, bvx).toDouble()).toFloat() + 90f
        pending.add(Event.SNAP)
        return true
    }

    /** Where the stone would be [s] seconds after letting go now (for the aim dots). null when not pulling enough. */
    fun aimPoint(s: Float): Pair<Float, Float>? {
        val len = hypot(pullX, pullY)
        if (phase != Phase.AIM || len < MIN_PULL) return null
        val sp = launchSpeed(tension)
        val vx = -pullX / len * sp; val vy = -pullY / len * sp
        return (bx + vx * s) to (by + vy * s + 0.5f * GRAVITY * s * s)
    }

    fun step(dt0: Float) {
        if (phase == Phase.OVER) return
        val dt = dt0.coerceIn(0f, 0.05f)
        t += dt
        timeLeft = (timeLeft - dt).coerceAtLeast(0f)
        if (!warned && timeLeft <= 5f) { warned = true; pending.add(Event.LAST_SECONDS) }
        stepTarget(dt)
        when (phase) {
            Phase.AIM -> if (timeLeft <= 0f) finish()
            Phase.FLY -> stepFlight(dt)
            Phase.SPLAT -> {
                splatT += dt; stoneSplatT += dt
                if (wall == Wall.LEFT || wall == Wall.RIGHT) by += SLIDE * dt
                target?.takeIf { it.down }?.let { it.splatT += dt; if (it.wall == Wall.LEFT || it.wall == Wall.RIGHT) it.y += SLIDE * dt }
                if (splatT >= (if (splatDouble) SPLAT_HIT_SEC else SPLAT_MISS_SEC)) {
                    if (splatDouble) target = null
                    resetStone(); phase = Phase.RETURN; returnT = 0f
                }
            }
            Phase.RETURN -> { returnT += dt; if (returnT >= RETURN_SEC) { if (timeLeft <= 0f) finish() else phase = Phase.AIM } }
            Phase.OVER -> Unit
        }
    }

    private fun finish() { phase = Phase.OVER; pending.add(Event.OVER) }
    private fun resetStone() { pullX = 0f; pullY = 0f; bx = homeX; by = homeY; bvx = 0f; bvy = 0f; stoneDown = false; stoneSpin = 0f; stoneOmega = 0f }
    private var knockT = 0f

    private fun stepFlight(dt: Float) {
        // small sub-steps so a fast stone can't jump over the target
        var left = dt
        while (left > 0f && phase == Phase.FLY) {
            val h = minOf(left, 1f / 240f); left -= h
            val tg = target
            val knocked = tg != null && tg.carried
            if (knocked) knockT += h
            if (!stoneDown) {
                bvy += (if (knocked) KNOCK_GRAVITY else GRAVITY) * h
                bx += bvx * h; by += bvy * h
                if (knocked) stoneSpin += stoneOmega * h else stoneSpin = Math.toDegrees(atan2(bvy, bvx).toDouble()).toFloat() + 90f
                wallOf(bx, by)?.let { stoneSlam(it) }
            } else stoneSplatT += h
            if (tg != null && knocked) {
                if (!tg.down) {
                    tg.kvy += KNOCK_GRAVITY * h
                    tg.x += tg.kvx * h; tg.y += tg.kvy * h; tg.spin += tg.omega * h
                    val w = wallOf(tg.x, tg.y) ?: if (knockT > KNOCK_MAX_SEC) Wall.FLOOR else null
                    if (w != null) targetSlam(tg, w)
                } else tg.splatT += h
            } else if (tg != null && !stoneDown && !tg.leaving && tg.hittable && hits(tg)) knock(tg)
            if (stoneDown && (tg == null || !tg.carried || tg.down)) rest()
        }
    }

    private fun wallOf(x: Float, y: Float): Wall? = when {
        x <= EDGE -> Wall.LEFT
        x >= 1f - EDGE -> Wall.RIGHT
        y - BODY_H / 2f <= TOP_EDGE -> Wall.TOP
        y >= aspect - FLOOR_EDGE -> Wall.FLOOR
        else -> null
    }

    private fun hits(tg: Target): Boolean = hypot(tg.x - bx, tg.y + tg.yOff - by) < HIT_R

    /**
     * v10.42 the knock: a real two-body bounce along the line between them. A heavy target (Gajju) hardly moves and
     * throws the stone back; a light one (Chittu) is sent flying. Both tumble. The hit scores here.
     */
    private fun knock(tg: Target) {
        tg.y += tg.yOff; tg.yOff = 0f; tg.trickPhase = 0; tg.alpha = 1f
        val tvx = tg.vx * tg.dash; tg.dash = 1f
        var nx = tg.x - bx; var ny = tg.y - by
        val d = hypot(nx, ny)
        if (d < 1e-4f) { val sp = hypot(bvx, bvy).coerceAtLeast(1e-4f); nx = bvx / sp; ny = bvy / sp } else { nx /= d; ny /= d }
        val ms = MASS_BASE + TouchFeel.weight(bulletId); val mt = MASS_BASE + TouchFeel.weight(tg.id)
        val speed = hypot(bvx, bvy)
        val vrel = ((bvx - tvx) * nx + bvy * ny).coerceAtLeast(speed * 0.5f)
        val j = (1f + RESTITUTION) * vrel / (1f / ms + 1f / mt)
        bvx -= j / ms * nx; bvy -= j / ms * ny
        tg.kvx = tvx + j / mt * nx; tg.kvy = j / mt * ny
        val ts = hypot(tg.kvx, tg.kvy)
        if (ts < KNOCK_MIN_SPEED) { val k = KNOCK_MIN_SPEED / ts.coerceAtLeast(1e-4f); tg.kvx *= k; tg.kvy *= k }
        val turn = if (nx >= 0f) 1f else -1f
        tg.omega = turn * (520f + 380f * rng.nextFloat()); stoneOmega = -turn * (300f + 260f * rng.nextFloat())
        tg.carried = true; tg.down = false; tg.splatT = 0f; tg.spin = 0f; knockT = 0f
        hitX = (bx + tg.x) / 2f; hitY = (by + tg.y) / 2f - BODY_H / 2f
        hitForce = (vrel / SPEED_MAX).coerceIn(0.3f, 1f)
        val before = level
        score++; streak++; if (streak > bestStreak) bestStreak = streak
        timeLeft = (timeLeft + BONUS_SEC).coerceAtMost(MAX_SEC); bonus += BONUS_SEC
        pending.add(Event.HIT)
        if (level > before) pending.add(Event.LEVEL_UP)
    }

    private fun stoneSlam(w: Wall) {
        slamForce = (hypot(bvx, bvy) / SPEED_MAX).coerceIn(0.35f, 1f)
        wall = w; stoneDown = true; stoneSplatT = 0f
        bx = bx.coerceIn(EDGE, 1f - EDGE); by = by.coerceIn(TOP_EDGE + BODY_H / 2f, aspect - FLOOR_EDGE)
        bvx = 0f; bvy = 0f; stoneSpin = 0f
        val tg = target
        if (tg != null && tg.carried) pending.add(Event.WALL_STONE)
        else { streak = 0; tg?.laughUntil = t + 1.2f; pending.add(Event.SPLAT) }
    }

    private fun targetSlam(tg: Target, w: Wall) {
        slamForce = (hypot(tg.kvx, tg.kvy) / SPEED_MAX).coerceIn(0.35f, 1f)
        tg.wall = w; tg.down = true; tg.splatT = 0f; tg.spin = 0f
        tg.x = tg.x.coerceIn(EDGE, 1f - EDGE); tg.y = tg.y.coerceIn(TOP_EDGE + BODY_H / 2f, aspect - FLOOR_EDGE)
        tg.kvx = 0f; tg.kvy = 0f
        pending.add(Event.WALL_TARGET)
    }

    /** Everything that was flying has slammed: hold the splat for a moment, then the stone goes home. */
    private fun rest() {
        phase = Phase.SPLAT; splatT = 0f
        val tg = target
        splatDouble = tg != null && tg.carried
        if (splatDouble) pending.add(Event.DOUBLE_SPLAT)
    }

    private fun stepTarget(dt: Float) {
        val tg = target
        if (tg == null) { if (phase == Phase.AIM || phase == Phase.RETURN) spawn(); return }
        if (tg.carried) return
        tg.age += dt
        tg.x += tg.vx * tg.dash * dt
        if (tg.leaving) {
            tg.trickPhase = 0; tg.dash = 1f; tg.alpha = 1f; tg.yOff *= 0.9f
            if (tg.x < -OFF || tg.x > 1f + OFF) { target = null; pending.add(Event.TARGET_OUT) }
            return
        }
        // inside the play area: bounce off the sides, change its mind now and then
        if (tg.x < LANE_MIN && tg.vx < 0f) tg.vx = -tg.vx
        if (tg.x > LANE_MAX && tg.vx > 0f) tg.vx = -tg.vx
        stepTrick(tg, dt)
        if (tg.age >= tg.nextTurn && tg.x in LANE_MIN..LANE_MAX) {
            tg.nextTurn = tg.age + (0.9f + rng.nextFloat() * 1.4f) / (1f + 0.1f * (level - 1))
            val sp = speedFor()
            tg.vx = if (rng.nextFloat() < 0.45f) -sign(tg.vx) * sp else sign(tg.vx) * sp
        }
        if (tg.age >= TARGET_LIFE && phase == Phase.AIM) { tg.leaving = true; tg.vx = (if (tg.x < 0.5f) -1f else 1f) * 0.6f }
    }

    /** v10.41 the escape: wait, warn ([TELL_SEC]), then do it. More often on higher levels. */
    private fun stepTrick(tg: Target, dt: Float) {
        if (tg.trick == Trick.NONE) return
        when (tg.trickPhase) {
            0 -> if (tg.age >= tg.nextTrick && tg.x in LANE_MIN..LANE_MAX) { tg.trickPhase = 1; tg.trickT = 0f }
            1 -> { tg.trickT += dt; if (tg.trickT >= TELL_SEC) { tg.trickPhase = 2; tg.trickT = 0f; pending.add(Event.TRICK) } }
            else -> {
                tg.trickT += dt
                val k = (tg.trickT / TRICK_SEC).coerceIn(0f, 1f)
                when (tg.trick) {
                    Trick.HOP -> tg.yOff = -0.20f * 4f * k * (1f - k)
                    Trick.FLY_UP -> tg.yOff = -0.30f * (if (k < 0.3f) k / 0.3f else 1f - (k - 0.3f) / 0.7f)
                    Trick.DASH -> tg.dash = if (k < 0.8f) 3f else 1f
                    Trick.VANISH -> {
                        tg.alpha = if (k < 0.15f) 1f - k / 0.15f else if (k > 0.85f) (k - 0.85f) / 0.15f else 0f
                        if (k >= 0.5f && tg.trickT - dt < TRICK_SEC * 0.5f) tg.x = LANE_MIN + rng.nextFloat() * (LANE_MAX - LANE_MIN)
                    }
                    Trick.NONE -> Unit
                }
                if (k >= 1f) { tg.trickPhase = 0; tg.yOff = 0f; tg.alpha = 1f; tg.dash = 1f; tg.nextTrick = tg.age + trickGap() }
            }
        }
    }

    /** Seconds until the next escape: about 3.5 s on level 1, down to about 1.6 s. */
    private fun trickGap(): Float = (3.5f - 0.45f * (level - 1)).coerceAtLeast(1.6f) * (0.8f + 0.4f * rng.nextFloat())

    private fun sign(v: Float) = if (v >= 0f) 1f else -1f
    /** Targets get faster with every level (never more than 2x). */
    private fun speedFor(): Float = (TARGET_SPEED_MIN + rng.nextFloat() * (TARGET_SPEED_MAX - TARGET_SPEED_MIN)) * (1f + 0.15f * (level - 1)).coerceAtMost(2f)

    private fun spawn() {
        val choices = others.filter { it != lastTargetId }.ifEmpty { others }
        val id = choices[rng.nextInt(choices.size)]
        lastTargetId = id
        val fromLeft = rng.nextBoolean()
        val y = aspect * (LANE_TOP + rng.nextFloat() * (LANE_BOTTOM - LANE_TOP))
        val tg = Target(id, id in fliers, if (fromLeft) -OFF else 1f + OFF, y, (if (fromLeft) 1f else -1f) * speedFor())
        tg.nextTurn = 1.2f + rng.nextFloat()
        tg.trick = tricks[id] ?: Trick.HOP
        tg.nextTrick = 1.5f + trickGap() * 0.5f
        target = tg
        pending.add(Event.TARGET_IN)
    }

    companion object {
        const val ROUND_SEC = 60f
        /** v10.41: every hit adds this to the clock (never above [MAX_SEC]). */
        const val BONUS_SEC = 3f
        const val MAX_SEC = 99f
        const val HITS_PER_LEVEL = 3
        /** The warning before an escape, and how long the escape lasts. */
        const val TELL_SEC = 0.35f
        const val TRICK_SEC = 0.9f
        /** Character height (screen widths). */
        const val BODY_H = 0.21f
        const val HOME_Y = 0.80f
        const val MAX_PULL = 0.30f
        const val MIN_PULL = 0.06f
        const val SPEED_MIN = 1.5f
        const val SPEED_MAX = 2.7f
        /** A little gravity: the flight is nearly straight, so aiming is fair. */
        const val GRAVITY = 0.35f
        const val HIT_R = 0.125f
        // v10.42 collision
        /** Body mass = this + TouchFeel.weight(id) (0..1). */
        const val MASS_BASE = 0.6f
        const val RESTITUTION = 0.6f
        /** A knocked target always leaves at least this fast, so it reaches an edge. */
        const val KNOCK_MIN_SPEED = 1.3f
        /** After a knock both bodies fall properly (a heavy tumble), unlike the nearly straight aimed flight. */
        const val KNOCK_GRAVITY = 2.4f
        /** Safety: a knocked target is put down after this long even if it found no edge. */
        const val KNOCK_MAX_SEC = 2.5f
        const val EDGE = 0.07f
        const val TOP_EDGE = 0.09f
        const val FLOOR_EDGE = 0.03f
        const val SLIDE = 0.06f
        const val SPLAT_HIT_SEC = 1.15f
        const val SPLAT_MISS_SEC = 0.7f
        const val RETURN_SEC = 0.35f
        const val TARGET_LIFE = 8f
        const val TARGET_SPEED_MIN = 0.16f
        const val TARGET_SPEED_MAX = 0.32f
        const val LANE_TOP = 0.20f
        const val LANE_BOTTOM = 0.52f
        const val LANE_MIN = 0.14f
        const val LANE_MAX = 0.86f
        const val OFF = 0.16f
        fun launchSpeed(tension: Float) = SPEED_MIN + (SPEED_MAX - SPEED_MIN) * tension.coerceIn(0f, 1f)
    }
}
