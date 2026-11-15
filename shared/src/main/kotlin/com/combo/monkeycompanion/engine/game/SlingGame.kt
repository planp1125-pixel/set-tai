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
    /**
     * The saved level being played (1..[MAX_STAGE]). Level 1: one target, no escapes. Level 2: two targets, and they
     * escape with their own powers. Level 3: three targets, the stone flies in its own style and throws its power.
     * Level 4 and up: four targets. Every level is a little faster.
     */
    val stage: Int = 1,
) {
    enum class Phase { AIM, FLY, SPLAT, RETURN, OVER }
    /**
     * HIT = the stone knocked the target (scores here). WALL_STONE / WALL_TARGET = that one slammed into an edge after
     * a hit. DOUBLE_SPLAT = both are down. SPLAT = a miss: the stone slammed alone.
     */
    enum class Event { SNAP, HIT, SPLAT, DOUBLE_SPLAT, TARGET_IN, TARGET_OUT, LAST_SECONDS, OVER, FASTER, TRICK, WALL_STONE, WALL_TARGET, BOUNCE_STONE, BOUNCE_TARGET, COMBO,
        /** v10.45 the stone threw its power / the power knocked a target / it bounced off an edge / a shot that hit nothing at all. */
        POWER_FIRE, POWER_HIT, POWER_BOUNCE, MISS }
    /**
     * v10.45 the stone character's own power, thrown by itself at its first edge bounce (from level [POWER_STAGE]):
     * a fire blast, a red beam, a fireball, a water splash… It flies straight at the nearest target, bounces off the
     * edges as often as the pull allows, and knocks whoever it touches. Position = its centre.
     */
    class Shot(var x: Float, var y: Float, var vx: Float, var vy: Float, var bounces: Int) { var age = 0f; var alive = true }
    /**
     * v10.43 / v10.44 every shot bounces off the edges like a carrom coin, and a harder pull has more energy: SOFT
     * bounces once, MEDIUM twice, HARD three times. After its last bounce the stone drops and the splat is that last
     * fall. A full pull never splats at once any more.
     */
    enum class Power(val bounces: Int) { SOFT(1), MEDIUM(2), HARD(3) }
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
        /** v10.43 edge bounces left before it slams. */
        var bounces = 0
        var knockT = 0f
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
    /** v10.44 (was "level"): 1, 2, 3… inside one round the targets speed up every [HITS_PER_PACE] hits. */
    val pace: Int get() = 1 + score / HITS_PER_PACE
    /** Seconds added to the clock so far by hits. */
    var bonus = 0f; private set
    /** v10.44 everyone roaming or knocked right now ([wanted] of them on this level). */
    val targets = ArrayList<Target>()
    /** The first target (kept for one-target code and tests). */
    val target: Target? get() = targets.firstOrNull()
    val wanted: Int get() = targetCountFor(stage)
    /** Stars earned so far this round (0..3) and the hits needed for the next one (null = all three). */
    val stars: Int get() = starsFor(score)
    val nextGoal: Int? get() = STAR_GOALS.firstOrNull { it > score }
    /** Targets knocked by this shot so far (2+ = a combo). The clock stands still from the first knock until the stone is back. */
    var shotHits = 0; private set
    /** The target of the last HIT / WALL_TARGET / BOUNCE_TARGET event. */
    var lastKnocked: Target? = null; private set
    var lastSlammed: Target? = null; private set
    /** After its last bounce (or when a knock took its speed) the stone just falls; the next edge is the splat. */
    var stoneFalling = false; private set
    private var spawnWait = 0f
    // v10.45 power shots
    val powerShots = ArrayList<Shot>()
    val powersOn: Boolean get() = stage >= POWER_STAGE
    private var powerFired = false
    private var missPending = false
    /** From level [POWER_STAGE] four smaller targets roam; this is their size (1 = full). */
    val targetScale: Float get() = targetScaleFor(stage)
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
    /** v10.43 the power of the pull right now (while aiming) or of the shot in the air. */
    val power: Power get() = if (phase == Phase.AIM) powerFor(tension) else shotPower
    private var shotPower = Power.HARD
    /** Edge bounces the stone still has in this shot. */
    var stoneBounces = 0; private set
    /** The stone bounced off an edge before it knocked the target: a bank shot. */
    var banked = false; private set
    private var stoneBounced = false
    private var flyT = 0f
    /** Where the last bounce was (screen widths; y is the body centre). */
    var bounceX = 0f; private set
    var bounceY = 0f; private set
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
        stoneDown = false; stoneSplatT = 0f; stoneOmega = 0f; shotHits = 0; stoneFalling = false
        powerShots.clear(); powerFired = false; missPending = false
        shotPower = powerFor((len / MAX_PULL).coerceIn(0f, 1f)); stoneBounces = shotPower.bounces; stoneBounced = false; banked = false; flyT = 0f
        stoneSpin = Math.toDegrees(atan2(bvy, bvx).toDouble()).toFloat() + 90f
        pending.add(Event.SNAP)
        return true
    }

    /**
     * Where the stone would be [s] seconds after letting go now (for the aim dots), following its edge bounces
     * (v10.43). null when not pulling enough, or when it would already have splatted by then.
     */
    fun aimPoint(s: Float): Pair<Float, Float>? {
        val len = hypot(pullX, pullY)
        if (phase != Phase.AIM || len < MIN_PULL) return null
        val sp = launchSpeed(tension)
        var vx = -pullX / len * sp; var vy = -pullY / len * sp
        var x = bx; var y = by; var left = powerFor(tension).bounces
        var t0 = 0f
        while (t0 < s) {
            val h = minOf(1f / 120f, s - t0); t0 += h
            vy += (if (left <= 0) KNOCK_GRAVITY else GRAVITY) * h; x += vx * h; y += vy * h
            val w = wallOf(x, y) ?: continue
            if (left <= 0) return null
            val v = reflect(w, x, y, vx, vy); x = v[0]; y = v[1]; vx = v[2]; vy = v[3]; left--
        }
        return x to y
    }

    fun step(dt0: Float) {
        if (phase == Phase.OVER) return
        val dt = dt0.coerceIn(0f, 0.05f)
        t += dt
        // v10.44: the clock stands still from a knock until the stone is back in the sling (no time lost to the tumble)
        if (phase == Phase.AIM || shotHits == 0) timeLeft = (timeLeft - dt).coerceAtLeast(0f)
        if (!warned && timeLeft <= 5f) { warned = true; pending.add(Event.LAST_SECONDS) }
        stepTargets(dt)
        when (phase) {
            Phase.AIM -> if (timeLeft <= 0f) finish()
            Phase.FLY -> stepFlight(dt)
            Phase.SPLAT -> {
                splatT += dt; stoneSplatT += dt
                if (wall == Wall.LEFT || wall == Wall.RIGHT) by += SLIDE * dt
                for (tg in targets) if (tg.down) { tg.splatT += dt; if (tg.wall == Wall.LEFT || tg.wall == Wall.RIGHT) tg.y += SLIDE * dt }
                if (splatT >= (if (splatDouble) SPLAT_HIT_SEC else SPLAT_MISS_SEC)) {
                    targets.removeAll { it.carried }; powerShots.clear()
                    resetStone(); phase = Phase.RETURN; returnT = 0f
                }
            }
            Phase.RETURN -> { returnT += dt; if (returnT >= RETURN_SEC) { shotHits = 0; if (timeLeft <= 0f) finish() else phase = Phase.AIM } }
            Phase.OVER -> Unit
        }
    }

    private fun finish() { phase = Phase.OVER; pending.add(Event.OVER) }
    private fun resetStone() { pullX = 0f; pullY = 0f; bx = homeX; by = homeY; bvx = 0f; bvy = 0f; stoneDown = false; stoneSpin = 0f; stoneOmega = 0f; stoneFalling = false }

    private fun stepFlight(dt: Float) {
        // small sub-steps so a fast stone can't jump over a target
        var left = dt
        while (left > 0f && phase == Phase.FLY) {
            val h = minOf(left, 1f / 240f); left -= h
            if (!stoneDown) {
                bvy += (if (stoneFalling) KNOCK_GRAVITY else GRAVITY) * h
                bx += bvx * h; by += bvy * h
                if (shotHits > 0 || stoneFalling) stoneSpin += stoneOmega * h else stoneSpin = Math.toDegrees(atan2(bvy, bvx).toDouble()).toFloat() + 90f
                flyT += h
                if (!stoneFalling && (flyT >= FLY_MAX_SEC || (shotHits > 0 && hypot(bvx, bvy) < FALL_BELOW))) startFalling()
                wallOf(bx, by)?.let { w -> if (!stoneFalling && stoneBounces > 0) bounceStone(w) else stoneSlam(w) }
            } else stoneSplatT += h
            for (k in targets.indices) {
                val tg = targets[k]
                if (tg.carried) {
                    if (!tg.down) {
                        tg.knockT += h
                        tg.kvy += KNOCK_GRAVITY * h
                        tg.x += tg.kvx * h; tg.y += tg.kvy * h; tg.spin += tg.omega * h
                        val w = wallOf(tg.x, tg.y) ?: if (tg.knockT > KNOCK_MAX_SEC) Wall.FLOOR else null
                        if (w != null) { if (tg.bounces > 0 && tg.knockT <= KNOCK_MAX_SEC) bounceTarget(tg, w) else targetSlam(tg, w) }
                    } else tg.splatT += h
                } else if (!stoneDown && !tg.leaving && tg.hittable && hits(tg)) knock(tg)
            }
            stepShots(h)
            if (stoneDown && targets.none { it.carried && !it.down } && powerShots.none { it.alive }) rest()
        }
    }

    /** The stone has no bounce or speed left: it tumbles down, and the next edge it meets is the splat. */
    private fun startFalling() {
        stoneFalling = true
        if (stoneOmega == 0f) stoneOmega = if (bvx >= 0f) 380f else -380f
    }

    private fun wallOf(x: Float, y: Float): Wall? = when {
        x <= EDGE -> Wall.LEFT
        x >= 1f - EDGE -> Wall.RIGHT
        y - BODY_H / 2f <= TOP_EDGE -> Wall.TOP
        y >= aspect - FLOOR_EDGE -> Wall.FLOOR
        else -> null
    }

    /** Centre of a target (it is drawn [targetScale] big, standing on its feet). */
    private fun tcy(tg: Target): Float = tg.y + tg.yOff - BODY_H * targetScale / 2f
    private fun hits(tg: Target): Boolean = hypot(tg.x - bx, tcy(tg) - (by - BODY_H / 2f)) < HIT_R * (1f + targetScale) / 2f

    // ---------------- v10.45 the power shot ----------------
    /**
     * Thrown by itself at the first bounce. v10.45.2: NOT aimed at anyone (aimed, it never missed, which was boring).
     * It flies back into the screen from the edge the stone bounced off, in a random direction up to
     * [POWER_SPREAD_DEG] either side of straight out, so it may hit somebody or nobody. More bounces (a harder pull)
     * give it more chances.
     */
    private fun firePower(w: Wall) {
        powerFired = true
        val sx = bx; val sy = by - BODY_H / 2f
        val sp = powerSpeedFor(bulletId)
        // straight out of the edge it bounced off
        val base = when (w) { Wall.LEFT -> 0.0; Wall.RIGHT -> Math.PI; Wall.TOP -> Math.PI / 2; Wall.FLOOR -> -Math.PI / 2 }
        val a = base + Math.toRadians(((rng.nextFloat() * 2f - 1f) * POWER_SPREAD_DEG).toDouble())
        powerShots.add(Shot(sx, sy, Math.cos(a).toFloat() * sp, Math.sin(a).toFloat() * sp, shotPower.bounces))
        pending.add(Event.POWER_FIRE)
    }

    private fun stepShots(h: Float) {
        for (i in powerShots.indices) {
            val sh = powerShots[i]
            if (!sh.alive) continue
            sh.age += h; sh.x += sh.vx * h; sh.y += sh.vy * h
            val hit = targets.firstOrNull { !it.carried && !it.leaving && it.hittable && hypot(it.x - sh.x, tcy(it) - sh.y) < SHOT_R + BODY_H * targetScale * 0.3f }
            if (hit != null) { powerKnock(hit, sh); continue }
            val out = when {
                sh.x <= SHOT_EDGE -> Wall.LEFT
                sh.x >= 1f - SHOT_EDGE -> Wall.RIGHT
                sh.y <= TOP_EDGE * 0.6f -> Wall.TOP
                sh.y >= aspect - SHOT_EDGE -> Wall.FLOOR
                else -> null
            }
            if (sh.age > SHOT_MAX_SEC) sh.alive = false
            else if (out != null) {
                if (sh.bounces <= 0) { sh.alive = false; continue }
                sh.bounces--
                when (out) {
                    Wall.LEFT -> { sh.x = SHOT_EDGE + 0.002f; sh.vx = kotlin.math.abs(sh.vx) }
                    Wall.RIGHT -> { sh.x = 1f - SHOT_EDGE - 0.002f; sh.vx = -kotlin.math.abs(sh.vx) }
                    Wall.TOP -> { sh.y = TOP_EDGE * 0.6f + 0.002f; sh.vy = kotlin.math.abs(sh.vy) }
                    Wall.FLOOR -> { sh.y = aspect - SHOT_EDGE - 0.002f; sh.vy = -kotlin.math.abs(sh.vy) }
                }
                bounceX = sh.x; bounceY = sh.y
                pending.add(Event.POWER_BOUNCE)
            }
        }
    }

    /** The power knocks a target away along its path. Scores like a hit, with a smaller time bonus. */
    private fun powerKnock(tg: Target, sh: Shot) {
        sh.alive = false
        tg.y += tg.yOff; tg.yOff = 0f; tg.trickPhase = 0; tg.alpha = 1f
        val tvx = tg.vx * tg.dash; tg.dash = 1f
        val sp = hypot(sh.vx, sh.vy).coerceAtLeast(1e-4f)
        tg.kvx = sh.vx / sp * POWER_KNOCK_SPEED + tvx * 0.3f; tg.kvy = sh.vy / sp * POWER_KNOCK_SPEED
        tg.omega = (if (sh.vx >= 0f) 1f else -1f) * (480f + 300f * rng.nextFloat())
        tg.carried = true; tg.down = false; tg.splatT = 0f; tg.spin = 0f; tg.knockT = 0f; tg.bounces = 1
        hitX = tg.x; hitY = tg.y - BODY_H * targetScale / 2f; hitForce = 0.6f
        val before = pace
        shotHits++; lastKnocked = tg
        score++; streak++; if (streak > bestStreak) bestStreak = streak
        val gain = POWER_BONUS_SEC * (if (shotHits >= 2) 2f else 1f)
        lastGain = gain
        timeLeft = (timeLeft + gain).coerceAtMost(MAX_SEC); bonus += gain
        pending.add(Event.POWER_HIT)
        if (shotHits >= 2) pending.add(Event.COMBO)
        if (pace > before) pending.add(Event.FASTER)
    }

    /**
     * v10.42 the knock: a real two-body bounce along the line between them. A heavy target (Gajju) hardly moves and
     * throws the stone back; a light one (Chittu) is sent flying. Both tumble. The hit scores here.
     */
    private fun knock(tg: Target) {
        tg.y += tg.yOff; tg.yOff = 0f; tg.trickPhase = 0; tg.alpha = 1f
        val tvx = tg.vx * tg.dash; tg.dash = 1f
        var nx = tg.x - bx; var ny = (tg.y - BODY_H * targetScale / 2f) - (by - BODY_H / 2f)
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
        tg.carried = true; tg.down = false; tg.splatT = 0f; tg.spin = 0f; tg.knockT = 0f
        // v10.43: a gentle knock lets the target bounce off one edge before it slams; a hard one slams it flat
        tg.bounces = if (hypot(tg.kvx, tg.kvy) < TARGET_BOUNCE_BELOW) 1 else 0
        banked = stoneBounced
        hitX = (bx + tg.x) / 2f; hitY = ((by - BODY_H / 2f) + (tg.y - BODY_H * targetScale / 2f)) / 2f
        hitForce = (vrel / SPEED_MAX).coerceIn(0.3f, 1f)
        val before = pace
        shotHits++; lastKnocked = tg
        score++; streak++; if (streak > bestStreak) bestStreak = streak
        // a bank shot gives double time; every extra target in the same shot (a combo) gives double too
        val gain = BONUS_SEC * (if (banked) 2f else 1f) * (if (shotHits >= 2) 2f else 1f)
        lastGain = gain
        timeLeft = (timeLeft + gain).coerceAtMost(MAX_SEC); bonus += gain
        pending.add(Event.HIT)
        if (shotHits >= 2) pending.add(Event.COMBO)
        if (pace > before) pending.add(Event.FASTER)
    }

    /** v10.43 bounce off an edge like a carrom coin: mirror the speed into the screen, lose a little of it. */
    private fun bounceStone(w: Wall) {
        val v = reflect(w, bx, by, bvx, bvy)
        bx = v[0]; by = v[1]; bvx = v[2]; bvy = v[3]
        stoneBounces--; stoneBounced = true
        if (stoneBounces <= 0) startFalling()   // v10.44: that was its last bounce; now it drops
        bounceX = bx; bounceY = by - BODY_H / 2f
        pending.add(Event.BOUNCE_STONE)
        if (powersOn && !powerFired) firePower(w)   // v10.45: the first bounce throws the power
    }
    private fun bounceTarget(tg: Target, w: Wall) {
        val v = reflect(w, tg.x, tg.y, tg.kvx, tg.kvy)
        tg.x = v[0]; tg.y = v[1]; tg.kvx = v[2]; tg.kvy = v[3]
        tg.bounces--; tg.omega = -tg.omega * 0.7f
        bounceX = tg.x; bounceY = tg.y - BODY_H / 2f; lastSlammed = tg
        pending.add(Event.BOUNCE_TARGET)
    }
    /** (x, y, vx, vy) after bouncing off [w]: back inside the screen, the speed into the edge turned around. */
    private fun reflect(w: Wall, x: Float, y: Float, vx: Float, vy: Float): FloatArray = when (w) {
        Wall.LEFT -> floatArrayOf(EDGE + 0.002f, y, kotlin.math.abs(vx) * BOUNCE_KEEP, vy * BOUNCE_SLIDE)
        Wall.RIGHT -> floatArrayOf(1f - EDGE - 0.002f, y, -kotlin.math.abs(vx) * BOUNCE_KEEP, vy * BOUNCE_SLIDE)
        Wall.TOP -> floatArrayOf(x, TOP_EDGE + BODY_H / 2f + 0.002f, vx * BOUNCE_SLIDE, kotlin.math.abs(vy) * BOUNCE_KEEP)
        Wall.FLOOR -> floatArrayOf(x, aspect - FLOOR_EDGE - 0.002f, vx * BOUNCE_SLIDE, -kotlin.math.abs(vy) * BOUNCE_KEEP)
    }

    private fun stoneSlam(w: Wall) {
        slamForce = (hypot(bvx, bvy) / SPEED_MAX).coerceIn(0.35f, 1f)
        wall = w; stoneDown = true; stoneSplatT = 0f
        bx = bx.coerceIn(EDGE, 1f - EDGE); by = by.coerceIn(TOP_EDGE + BODY_H / 2f, aspect - FLOOR_EDGE)
        bvx = 0f; bvy = 0f; stoneSpin = 0f
        if (shotHits > 0) pending.add(Event.WALL_STONE)
        else if (powerShots.any { it.alive }) { missPending = true; pending.add(Event.WALL_STONE) }   // its power is still flying: not a miss yet
        else { streak = 0; for (tg in targets) tg.laughUntil = t + 1.2f; pending.add(Event.SPLAT) }
    }

    private fun targetSlam(tg: Target, w: Wall) {
        slamForce = (hypot(tg.kvx, tg.kvy) / SPEED_MAX).coerceIn(0.35f, 1f)
        tg.wall = w; tg.down = true; tg.splatT = 0f; tg.spin = 0f
        tg.x = tg.x.coerceIn(EDGE, 1f - EDGE); tg.y = tg.y.coerceIn(TOP_EDGE + BODY_H / 2f, aspect - FLOOR_EDGE)
        tg.kvx = 0f; tg.kvy = 0f; lastSlammed = tg
        pending.add(Event.WALL_TARGET)
    }

    /** Everything that was flying has slammed: hold the splat for a moment, then the stone goes home. */
    private fun rest() {
        phase = Phase.SPLAT; splatT = 0f
        if (shotHits == 0 && missPending) { streak = 0; for (tg in targets) tg.laughUntil = t + 1.2f; pending.add(Event.MISS) }
        missPending = false
        splatDouble = shotHits > 0
        if (splatDouble) pending.add(Event.DOUBLE_SPLAT)
    }

    private fun stepTargets(dt: Float) {
        var k = 0
        while (k < targets.size) {
            val tg = targets[k]
            if (tg.carried) { k++; continue }
            tg.age += dt
            tg.x += tg.vx * tg.dash * dt
            if (tg.leaving) {
                tg.trickPhase = 0; tg.dash = 1f; tg.alpha = 1f; tg.yOff *= 0.9f
                if (tg.x < -OFF || tg.x > 1f + OFF) { targets.removeAt(k); pending.add(Event.TARGET_OUT); continue }
                k++; continue
            }
            // inside the play area: bounce off the sides, change its mind now and then
            if (tg.x < LANE_MIN && tg.vx < 0f) tg.vx = -tg.vx
            if (tg.x > LANE_MAX && tg.vx > 0f) tg.vx = -tg.vx
            stepTrick(tg, dt)
            if (tg.age >= tg.nextTurn && tg.x in LANE_MIN..LANE_MAX) {
                tg.nextTurn = tg.age + (0.9f + rng.nextFloat() * 1.4f) / (1f + 0.1f * (pace - 1))
                val sp = speedFor()
                tg.vx = if (rng.nextFloat() < 0.45f) -sign(tg.vx) * sp else sign(tg.vx) * sp
            }
            if (tg.age >= TARGET_LIFE && phase == Phase.AIM) { tg.leaving = true; tg.vx = (if (tg.x < 0.5f) -1f else 1f) * 0.6f }
            k++
        }
        // keep [wanted] of them roaming; new ones walk in one after another
        spawnWait -= dt
        if ((phase == Phase.AIM || phase == Phase.RETURN) && spawnWait <= 0f && targets.count { !it.carried } < wanted) { spawn(); spawnWait = SPAWN_GAP }
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

    /** Seconds until the next escape: about 3.5 s at first, down to about 1.6 s as the round and the levels go on. */
    private fun trickGap(): Float = (3.5f - 0.35f * (pace - 1) - 0.15f * (stage - 2)).coerceIn(1.6f, 3.5f) * (0.8f + 0.4f * rng.nextFloat())

    private fun sign(v: Float) = if (v >= 0f) 1f else -1f
    /** Targets get faster through the round (up to 1.5x) and clearly faster with every saved level ([stageSpeedFor]). */
    private fun speedFor(): Float = (TARGET_SPEED_MIN + rng.nextFloat() * (TARGET_SPEED_MAX - TARGET_SPEED_MIN)) *
        (1f + 0.10f * (pace - 1)).coerceAtMost(1.5f) * stageSpeedFor(stage)

    private fun spawn() {
        val here = targets.map { it.id }
        val choices = others.filter { it != lastTargetId && it !in here }.ifEmpty { others.filter { it !in here }.ifEmpty { others } }
        val id = choices[rng.nextInt(choices.size)]
        lastTargetId = id
        val fromLeft = rng.nextBoolean()
        // its own height, away from the others so two targets don't walk on top of each other
        var y = aspect * (LANE_TOP + rng.nextFloat() * (LANE_BOTTOM - LANE_TOP))
        repeat(6) { if (targets.any { !it.carried && kotlin.math.abs(it.y - y) < BODY_H * targetScale * 0.9f }) y = aspect * (LANE_TOP + rng.nextFloat() * (LANE_BOTTOM - LANE_TOP)) }
        val tg = Target(id, id in fliers, if (fromLeft) -OFF else 1f + OFF, y, (if (fromLeft) 1f else -1f) * speedFor())
        tg.nextTurn = 1.2f + rng.nextFloat()
        tg.trick = if (stage >= 2) tricks[id] ?: Trick.HOP else Trick.NONE   // level 1: no escapes, just aiming
        tg.nextTrick = 1.5f + trickGap() * 0.5f
        targets.add(tg)
        pending.add(Event.TARGET_IN)
    }

    /** Seconds the last knock added to the clock. */
    var lastGain = 0f; private set

    companion object {
        const val ROUND_SEC = 60f
        /** v10.41: every hit adds this to the clock (never above [MAX_SEC]). */
        const val BONUS_SEC = 3f
        const val MAX_SEC = 99f
        const val HITS_PER_PACE = 5
        // v10.44 saved levels
        const val MAX_STAGE = 10
        /** Hits in one round for 1, 2 and 3 stars. One star unlocks the next level. */
        val STAR_GOALS = intArrayOf(8, 15, 25)
        fun starsFor(score: Int): Int = STAR_GOALS.count { score >= it }
        /** v10.45.1: as many targets as the level number, up to four (level 1 = 1, 2 = 2, 3 = 3, 4 and up = 4). */
        fun targetCountFor(stage: Int): Int = stage.coerceIn(1, 4)
        /** More targets are drawn a little smaller so the screen is not crowded. */
        fun targetScaleFor(stage: Int): Float = when { stage <= 2 -> 1f; stage == 3 -> MID_TARGET; else -> SMALL_TARGET }
        const val MID_TARGET = 0.9f
        /**
         * v10.45.3 how much faster the targets are on a saved level: +18 % a level, up to 2x (level 1 = 1.0,
         * 2 = 1.18, 3 = 1.36, 4 = 1.54 … 7 and up = 2.0). It was +7 % a level, too little to notice.
         */
        fun stageSpeedFor(stage: Int): Float = (1f + 0.18f * (stage.coerceAtLeast(1) - 1)).coerceAtMost(2f)
        // v10.45 flying styles and power shots
        /** From this level the stone flies in its own style (looks only). */
        const val STYLE_STAGE = 3
        /** From this level the stone throws its power at its first bounce (v10.45.1: level 3, with the flying styles). */
        const val POWER_STAGE = 3
        const val SMALL_TARGET = 0.82f
        const val SHOT_R = 0.045f
        const val SHOT_EDGE = 0.03f
        const val SHOT_MAX_SEC = 3f
        const val POWER_KNOCK_SPEED = 1.6f
        /** The power leaves the edge within this many degrees either side of straight out. */
        const val POWER_SPREAD_DEG = 65f
        /** A power hit adds this much time (a body hit adds [BONUS_SEC]). */
        const val POWER_BONUS_SEC = 2f
        /** How fast each character's power flies (Tutty's fireball is the fast one). */
        fun powerSpeedFor(id: String): Float = if (id == "devil") 3.9f else 3.2f
        /** New targets walk in this many seconds apart. */
        const val SPAWN_GAP = 0.7f
        /** A stone slower than this after a knock has lost its energy and drops. */
        const val FALL_BELOW = 0.9f
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
        // v10.43 carrom bounces
        /** Pull below this (0..1) = SOFT, below [HARD_FROM] = MEDIUM, else HARD. */
        const val SOFT_BELOW = 0.45f
        const val HARD_FROM = 0.78f
        /** Speed kept across the edge / along it at a bounce. */
        const val BOUNCE_KEEP = 0.80f
        const val BOUNCE_SLIDE = 0.94f
        /** A knocked target slower than this bounces off one edge first. */
        const val TARGET_BOUNCE_BELOW = 1.75f
        /** Safety: a shot still in the air after this long splats at the next edge. */
        const val FLY_MAX_SEC = 5f
        fun powerFor(tension: Float): Power = when { tension < SOFT_BELOW -> Power.SOFT; tension < HARD_FROM -> Power.MEDIUM; else -> Power.HARD }
        const val EDGE = 0.07f
        const val TOP_EDGE = 0.09f
        const val FLOOR_EDGE = 0.03f
        const val SLIDE = 0.06f
        const val SPLAT_HIT_SEC = 0.8f
        const val SPLAT_MISS_SEC = 0.55f
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
