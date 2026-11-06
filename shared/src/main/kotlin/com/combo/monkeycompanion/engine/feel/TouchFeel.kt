package com.combo.monkeycompanion.engine.feel

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * v10.27 "Touch Feel": what the phone's vibration motor plays when you touch a character, so it feels like a
 * real little body in your hand. Pure (no Android, no Random): the app turns a [Buzz] into a vibration.
 *
 * A [Buzz] is a list of pulses: wait [Pulse.offMs], then buzz for [Pulse.onMs] at [Pulse.amp] (1..255).
 */
data class Pulse(val offMs: Int, val onMs: Int, val amp: Int)

data class Buzz(val name: String, val pulses: List<Pulse>) {
    /** Android waveform: timings = off, on, off, on…; amplitudes 0 for the gaps. */
    val timings: LongArray get() = LongArray(pulses.size * 2) { i -> (if (i % 2 == 0) pulses[i / 2].offMs else pulses[i / 2].onMs).toLong() }
    val amps: IntArray get() = IntArray(pulses.size * 2) { i -> if (i % 2 == 0) 0 else pulses[i / 2].amp.coerceIn(1, 255) }
    val totalMs: Int get() = pulses.sumOf { it.offMs + it.onMs }
}

object TouchFeel {
    /** How heavy each character feels in your hand, 0 (a feather) .. 1 (a baby elephant). */
    fun weight(id: String, heavy: Boolean = false): Float = when (id) {
        "gajju" -> 1f
        "armour" -> 0.9f
        "billa" -> 0.55f
        "monkey" -> 0.5f
        "maskman", "superboy" -> 0.6f
        "rocketgirl", "surfer", "skater" -> 0.45f
        "devil" -> 0.3f
        "angel" -> 0.22f
        "chittu" -> 0.08f
        else -> if (heavy) 0.9f else 0.5f
    }

    private fun amp(x: Float) = (x.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(1, 255)

    /** Picked up: a light tick for Chittu, a deep heavy "thud" (two beats) for Gajju. */
    fun grab(w: Float): Buzz = if (w < 0.3f) Buzz("grab_light", listOf(Pulse(0, 12, amp(0.35f + w))))
        else Buzz("grab_heavy", listOf(Pulse(0, (22 + 38 * w).roundToInt(), amp(0.45f + 0.55f * w)), Pulse(40, (10 + 20 * w).roundToInt(), amp(0.25f + 0.35f * w))))

    /** While you carry them: one small tick every [dragStepDp] of travel, deeper for heavy ones. */
    fun dragStepDp(w: Float): Float = 70f + 70f * w
    fun dragTick(w: Float): Buzz = Buzz("drag_tick", listOf(Pulse(0, (6 + 14 * w).roundToInt(), amp(0.18f + 0.4f * w))))

    /** Shaken: a rattle, harder the harder you shake. */
    fun rattle(level: Float): Buzz {
        val l = level.coerceIn(0f, 1f)
        return Buzz("rattle", List(3) { Pulse(if (it == 0) 0 else 18, 14, amp(0.35f + 0.6f * l)) })
    }

    /** One heartbeat: "lub-DUB". */
    fun heartbeat(): Buzz = Buzz("heartbeat", listOf(Pulse(0, 28, amp(0.55f)), Pulse(110, 40, amp(0.9f))))

    /** Heart speed: slow when sleepy, fast when scared (just thrown / shaken). */
    fun bpm(asleep: Boolean, scared: Boolean, w: Float): Int = when {
        scared -> 128
        asleep -> 52
        else -> (84 - 16 * w).roundToInt()   // small ones beat a little faster
    }

    /** A soft purr: quick gentle pulses (one stroke's worth). */
    fun purr(): Buzz = Buzz("purr", List(9) { Pulse(if (it == 0) 0 else 22, 16, amp(if (it % 2 == 0) 0.22f else 0.3f)) })

    /** Tickled: a fast fluttery burst. */
    fun tickle(): Buzz = Buzz("tickle", List(5) { Pulse(if (it == 0) 0 else 30, 9, amp(0.45f)) })

    /** Belly poke: soft squish (a low swell). */
    fun squish(w: Float): Buzz = Buzz("squish", listOf(Pulse(0, 18, amp(0.2f + 0.2f * w)), Pulse(0, (34 + 26 * w).roundToInt(), amp(0.35f + 0.25f * w))))

    /** Nose / head tap: a tiny sharp "boop". */
    fun boop(): Buzz = Buzz("boop", listOf(Pulse(0, 10, amp(0.75f))))

    /** Any other poke: a light tick. */
    fun tap(): Buzz = Buzz("tap", listOf(Pulse(0, 8, amp(0.4f))))

    /** Landing: always a bump; bigger from higher up and for heavier bodies. [force] 0..1 (fall speed). */
    fun thump(force: Float, w: Float): Buzz {
        val f = force.coerceIn(0f, 1f)
        if (f < 0.12f) return Buzz("thump", listOf(Pulse(0, 10, amp(0.18f + 0.2f * w))))   // a soft touch-down
        val k = (0.35f + 0.65f * f) * (0.6f + 0.4f * w)
        return Buzz("thump", listOf(Pulse(0, (14 + 46 * k).roundToInt(), amp(0.3f + 0.7f * k))) +
            if (f > 0.55f) listOf(Pulse(45, (8 + 16 * k).roundToInt(), amp(0.2f + 0.3f * k))) else emptyList())
    }

    /**
     * v10.30 glass slap: a VERY short hard knock when a body hits a wall / floor / ceiling hard.
     * Not the long landing [thump] rumble — that one feels like the phone buzzing, not a body hitting glass.
     */
    fun wallHit(force: Float, w: Float): Buzz {
        val f = force.coerceIn(0f, 1f)
        val k = (0.45f + 0.55f * f) * (0.55f + 0.45f * w)
        val on = (8 + 10 * k).roundToInt().coerceIn(8, 18)
        return Buzz("wall_hit", listOf(Pulse(0, on, amp(0.72f + 0.28f * k))))
    }

    /** v10.30 pinch-and-pop: a tiny sharp click when they spring back. */
    fun pop(): Buzz = Buzz("pop", listOf(Pulse(0, 10, amp(0.85f))))

    /** Slingshot: a click for every notch of stretch (faster, stronger as it tightens). */
    fun slingTick(tension: Float): Buzz = Buzz("sling_tick", listOf(Pulse(0, (6 + 10 * tension).roundToInt(), amp(0.2f + 0.7f * tension))))

    /** Let go: a strong snap. */
    fun slingSnap(tension: Float): Buzz = Buzz("sling_snap", listOf(Pulse(0, (20 + 40 * tension).roundToInt(), amp(0.6f + 0.4f * tension))))

    /** Slingshot rules (dp): pull at least [SLING_MIN_DP] to fire; the band is full at [SLING_MAX_DP]. */
    const val SLING_MIN_DP = 55f
    const val SLING_MAX_DP = 220f
    const val SLING_NOTCHES = 8
    fun tension(pullDp: Float): Float = (pullDp / SLING_MAX_DP).coerceIn(0f, 1f)
    fun notch(pullDp: Float): Int = (tension(pullDp) * SLING_NOTCHES).toInt()
    /** Launch speed (dp/s), straight away from the pull. */
    fun launchSpeedDp(pullDp: Float): Float = 1300f + 4700f * tension(pullDp)

    /**
     * v10.30 marble bounce: a slingshot with tension under this flies and bounces off edges like a marble
     * (full-power slingshot still does the comic SPLAT / THWACK / PANCAKE).
     */
    const val MARBLE_MAX_TENSION = 0.72f
    const val MARBLE_MAX_BOUNCES = 5
    /** Below this speed (dp/s) a marble settles into a normal landing. */
    const val MARBLE_SETTLE_DP = 280f
    /** Restitution: light bodies (Chittu) livelier, heavy (Gajju) duller. */
    fun marbleRestitution(w: Float): Float = when {
        w < 0.25f -> 0.70f
        w > 0.80f -> 0.55f
        else -> 0.62f
    }

    /** How long you keep holding still (after the menu opened) before you feel the heartbeat. */
    const val HEART_HOLD_MS = 1100L
}

/**
 * Reads a drag as SHAKE (big fast swings), STROKE (gentle back-and-forth petting while staying near where you
 * started) or TICKLE (small fast rubs). Feed finger points in dp with time in seconds; each direction change
 * that counts returns an event.
 */
class RubTracker {
    enum class Kind { NONE, SHAKE, STROKE, TICKLE }

    private var startX = 0f; private var startY = 0f
    private var lastX = 0f; private var lastY = 0f
    private var extX = 0f; private var extY = 0f; private var extT = 0f
    private var dirX = 0; private var dirY = 0
    private var started = false
    var strokes = 0; private set
    var shakes = 0; private set
    var tickles = 0; private set
    /** 0..1: how hard it's being shaken right now (decays). */
    var shakeLevel = 0f; private set
    private var lastT = 0f

    fun reset(x: Float, y: Float, t: Float) {
        startX = x; startY = y; lastX = x; lastY = y; extX = x; extY = y; extT = t; lastT = t
        dirX = 0; dirY = 0; started = true; strokes = 0; shakes = 0; tickles = 0; shakeLevel = 0f
    }

    /** Distance (dp) from where the finger went down. */
    fun net(x: Float, y: Float) = hypot(x - startX, y - startY)

    fun feed(x: Float, y: Float, t: Float): Kind {
        if (!started) { reset(x, y, t); return Kind.NONE }
        val dt = max(0f, t - lastT); lastT = t
        shakeLevel = max(0f, shakeLevel - dt * 1.4f)
        val sx = sign(x - lastX, 1.5f); val sy = sign(y - lastY, 1.5f)
        var out = Kind.NONE
        // A turn on the main axis of this swing ends one swing.
        val horiz = abs(x - extX) >= abs(y - extY)
        val s = if (horiz) sx else sy
        val d0 = if (horiz) dirX else dirY
        if (s != 0 && d0 != 0 && s != d0) {
            val amp = if (horiz) abs(lastX - extX) else abs(lastY - extY)
            val secs = max(0.016f, t - extT)
            val speed = amp / secs
            val near = net(x, y) < 70f
            out = when {
                amp >= 45f && speed >= 650f -> Kind.SHAKE
                near && amp in 10f..80f && speed < 520f -> Kind.STROKE
                near && amp in 8f..60f && speed >= 520f -> Kind.TICKLE
                else -> Kind.NONE
            }
            when (out) {
                Kind.SHAKE -> { shakes++; shakeLevel = min(1f, shakeLevel + 0.18f + speed / 6000f) }
                Kind.STROKE -> strokes++
                Kind.TICKLE -> tickles++
                Kind.NONE -> Unit
            }
            extX = lastX; extY = lastY; extT = t
        }
        if (sx != 0) dirX = sx
        if (sy != 0) dirY = sy
        lastX = x; lastY = y
        return out
    }

    private fun sign(v: Float, dead: Float) = if (v > dead) 1 else if (v < -dead) -1 else 0
}
