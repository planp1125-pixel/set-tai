package com.combo.monkeycompanion.brain

import kotlin.random.Random

/**
 * v10.9 double-tap power shortcut: picks one of the character's powers at random, never the same one twice in a row
 * (a single power just fires every time). Its own seeded [Random], never the frame-loop's.
 */
class PowerShortcut(seed: Long = 7L) {
    private val rnd = Random(seed)
    var last: String? = null; private set

    fun pick(powers: List<Superpower>): Superpower? = pick(powers, null, true)

    /**
     * v10.10: [signature] (the body's main power) always plays on the very first double-tap ([seen] = false), and after
     * that it gets [SIGNATURE_WEIGHT] of the picks; still never the same power twice in a row.
     */
    fun pick(powers: List<Superpower>, signature: Superpower?, seen: Boolean): Superpower? {
        if (powers.isEmpty()) return null
        if (powers.size == 1) return powers[0].also { last = it.id }
        val sig = signature?.takeIf { s -> powers.any { it.id == s.id } }
        if (sig != null && !seen) return sig.also { last = it.id }
        val pool = powers.filter { it.id != last }
        if (sig != null && pool.any { it.id == sig.id }) {
            val others = pool.filter { it.id != sig.id }
            if (others.isEmpty() || rnd.nextFloat() < SIGNATURE_WEIGHT) return sig.also { last = it.id }
            return others[rnd.nextInt(others.size)].also { last = it.id }
        }
        return pool[rnd.nextInt(pool.size)].also { last = it.id }
    }

    companion object {
        /** Taps closer together than this (ms) and near each other = a double-tap. */
        const val WINDOW_MS = 250L
        /** v10.10: share of double-taps that play the signature power (when it isn't the last one). */
        const val SIGNATURE_WEIGHT = 0.5f
    }
}

/**
 * The small floating chat button beside a character.
 *
 * v10.11: it is ALWAYS faint ([IDLE_ALPHA]) - right after a move, drag, landing, flight, entrance or tap too. It turns
 * dark and solid only while a finger is physically on it ([hold]), and goes straight back to faint when the finger
 * lifts. There are no fade timers and no "just moved" state any more. Where it sits is chosen every frame by
 * [ChatBubbleSpot] (beside the feet, away from the face), so it never lags over the face either.
 */
class FloatButton {
    var x = Float.NaN; private set
    var y = Float.NaN; private set
    /** -1 = left of the body, 1 = right. */
    var side = 1; private set
    var alpha = IDLE_ALPHA; private set
    /** 1 = squashed by a press, eases back to 0 after the finger lifts. */
    var press = 0f; private set
    /** A finger is down on the button right now. */
    var held = false; private set

    /** Put the button at (px, py) on side [s] (no lag: it moves with the character). */
    fun place(px: Float, py: Float, s: Int) { x = px; y = py; side = s }

    fun step(dt: Float) {
        if (held) { press = 1f; alpha = 1f }
        else { alpha = IDLE_ALPHA; press = (press - dt * 5f).coerceAtLeast(0f) }
    }

    /** Finger down (true) / up or cancelled (false) on the button: dark + solid only in between. */
    fun hold(on: Boolean) { held = on; if (on) press = 1f; alpha = if (on) 1f else IDLE_ALPHA }

    /** v10.11: touching the character (or anything else) no longer lights the button up. Kept for callers. */
    fun wake() = Unit
    /** The tap went through (chat opens): a little squash, still faint. */
    fun pressed() { press = 1f }
    fun hit(px: Float, py: Float, r: Float) = !x.isNaN() && kotlin.math.hypot(px - x, py - y) <= r
    fun reset() { x = Float.NaN; y = Float.NaN; alpha = IDLE_ALPHA; press = 0f; held = false }

    companion object {
        const val IDLE_ALPHA = 0.18f
    }
}

/**
 * v10.11 where the chat button sits: tries candidate centres in order and takes the first one whose circle (radius
 * [r] + [gap]) stays clear of the face/head box and fully inside the allowed area; if none does, the one farthest
 * from the face. Pure geometry so it can be unit-tested.
 */
object ChatBubbleSpot {
    /** Distance from the circle centre (cx, cy) to the box [l, t, rr, b] (0 when inside). */
    fun distToBox(cx: Float, cy: Float, l: Float, t: Float, rr: Float, b: Float): Float {
        val dx = maxOf(l - cx, 0f, cx - rr); val dy = maxOf(t - cy, 0f, cy - b)
        return kotlin.math.hypot(dx, dy)
    }

    fun circleHitsBox(cx: Float, cy: Float, r: Float, l: Float, t: Float, rr: Float, b: Float) = distToBox(cx, cy, l, t, rr, b) < r

    /**
     * [cand] = x,y pairs in preference order; [face] = l,t,r,b; [area] = l,t,r,b the centre must keep [r] inside.
     * Returns the index of the chosen candidate.
     */
    fun choose(cand: FloatArray, face: FloatArray, area: FloatArray, r: Float, gap: Float): Int {
        var best = 0; var bestD = -1f
        val n = cand.size / 2
        for (i in 0 until n) {
            val cx = cand[2 * i]; val cy = cand[2 * i + 1]
            val inside = cx - r >= area[0] - 0.5f && cx + r <= area[2] + 0.5f && cy - r >= area[1] - 0.5f && cy + r <= area[3] + 0.5f
            val dist = distToBox(cx, cy, face[0], face[1], face[2], face[3])
            if (inside && dist >= r + gap) return i
            val score = dist + (if (inside) 10000f else 0f)
            if (score > bestD) { bestD = score; best = i }
        }
        return best
    }
}
