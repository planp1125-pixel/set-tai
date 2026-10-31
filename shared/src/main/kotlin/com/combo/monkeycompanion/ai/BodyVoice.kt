package com.combo.monkeycompanion.ai

import kotlin.math.floor
import kotlin.math.sin

/**
 * v10.17 "the voice comes from the body": with Give Brain ON, scripted lines are replaced by short spontaneous lines the
 * AI writes from Body Sense (body state, position, action, mood, time, battery, friendship). This is the pure gate:
 *  - event lines (a moment that used to have a scripted line) at most once per [eventGap] seconds,
 *  - quiet-time lines once every 45..90 s (deterministic spacing, no RNG: the frame-loop random stream is untouched),
 *  - one request at a time; an answer slower than [timeoutSec] is dropped (never replaced by canned text).
 * Times are in seconds on one monotonic clock.
 */
class BodyVoice(val eventGap: Float = 20f, val idleMin: Float = 45f, val idleMax: Float = 90f, val timeoutSec: Float = 12f) {
    private var lastAt = Float.NEGATIVE_INFINITY
    private var startedAt = 0f
    var inFlight = false; private set
    private var token = 0
    private var count = 0

    /** Gap before the [n]-th quiet-time line, in [idleMin, idleMax]. */
    fun idleGap(n: Int): Float { val v = sin(n * 12.9898f + 4.1f) * 43758.547f; return idleMin + (idleMax - idleMin) * (v - floor(v)) }

    fun wantEvent(now: Float): Boolean { expire(now); return !inFlight && now - lastAt >= eventGap }
    fun wantIdle(now: Float): Boolean {
        expire(now)
        if (inFlight) return false
        if (lastAt == Float.NEGATIVE_INFINITY) { lastAt = now; return false }   // first quiet line only after a full gap
        return now - lastAt >= idleGap(count)
    }

    /** A request starts: returns its token. */
    fun begin(now: Float): Int { inFlight = true; startedAt = now; lastAt = now; count++; return ++token }

    /** The answer for [tok] arrived at [now]: true = show it (in time and still the current request). */
    fun accept(tok: Int, now: Float): Boolean {
        val ok = inFlight && tok == token && now - startedAt <= timeoutSec
        if (tok == token) inFlight = false
        return ok
    }

    /** Give up on a request that is taking too long (its late answer will be dropped). */
    fun expire(now: Float) { if (inFlight && now - startedAt > timeoutSec) { inFlight = false; token++ } }

    companion object {
        const val IDLE_EVENT = "a quiet moment: nothing much is happening, you're just hanging out on the screen"
        fun eventText(kind: String) = "something just happened to you ($kind)"
        /** The request sent to the brain (YOUR BODY NOW is already in the system prompt). */
        fun request(event: String) =
            "SPONTANEOUS MOMENT (nobody asked you anything): $event. Say ONE short line (max 14 words) that you would naturally blurt out right now, " +
                "coming from how your body actually feels and what really just happened to it (see YOUR BODY NOW: position, action, feelings, time, battery, mood, friendship). " +
                "Be specific to this moment, not generic. No lists, no quotes, no explanations."
    }
}
