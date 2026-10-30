package com.combo.monkeycompanion.engine.touch

import com.combo.monkeycompanion.mind.Traits
import kotlin.math.exp
import kotlin.random.Random

/** Read-only mood numbers the picker needs (MoodController keeps ownership). */
data class MoodSnapshot(val grumpy: Float = 0f, val energy: Float = 1f)

/** One picked reaction with its jittered parameters. */
class ReactionInstance(
    val entry: ReactionEntry,
    val amp: Float,
    val dur: Float,
    /** -1 / +1: which way the reaction goes (from the finger's side / direction, never random). */
    val dir: Float,
    val seed: Int,
    val input: TouchInput,
    /** Say a line this time? */
    val talk: Boolean,
) { val verb get() = entry.verb }

/**
 * v10.8 M4 weighted reaction picker. Each candidate's weight is
 *   base * forceCurve(force) * moodMul(mood) * traitMul(traits) * novelty(habituation)
 * and the last 2 verbs are never picked again (if nothing else is possible, the 2nd-last is allowed, never the last).
 * Uses its OWN seeded [Random] (never the frame-loop RNG), so the same seed + the same touches = the same reactions.
 */
class ReactionPicker(seed: Long) {
    private val rnd = Random(seed)
    private val last = arrayOfNulls<Verb>(2)
    private val habit = HashMap<String, Float>()
    private var habitAt = 0f
    private val cand = ArrayList<ReactionEntry>(32)
    private val w = FloatArray(64)

    /** The last verbs picked, newest first (tests / debug). */
    val recent: List<Verb?> get() = listOf(last[0], last[1])

    fun pick(lib: List<ReactionEntry>, inp: TouchInput, mood: MoodSnapshot, traits: Traits, expressiveness: Float, tempo: Float, now: Float): ReactionInstance? {
        decay(now)
        cand.clear()
        for (e in lib) if (inp.part in e.parts && inp.kind in e.kinds && inp.repeatCount >= e.minRepeats) cand += e
        if (cand.isEmpty() && inp.kind == TouchKind.HOLD) for (e in lib) if (inp.part in e.parts && TouchKind.TAP in e.kinds && inp.repeatCount >= e.minRepeats) cand += e
        if (cand.isEmpty()) return null
        var total = 0f
        for (pass in 0..1) {
            total = 0f
            for (i in cand.indices) {
                val e = cand[i]
                val blocked = e.verb == last[0] || (pass == 0 && e.verb == last[1])
                w[i] = if (blocked) 0f else weight(e, inp, mood, traits)
                total += w[i]
            }
            if (total > 0f) break
        }
        if (total <= 0f) return null
        var r = rnd.nextFloat() * total
        var chosen = cand[0]
        for (i in cand.indices) if (w[i] > 0f) chosen = cand[i]   // fallback for float rounding: the last allowed one
        for (i in cand.indices) { r -= w[i]; if (r <= 0f && w[i] > 0f) { chosen = cand[i]; break } }
        last[1] = last[0]; last[0] = chosen.verb
        val key = "${inp.part}:${chosen.verb}"
        habit[key] = (habit[key] ?: 0f) + 1f

        // procedural variation: amplitude follows the touch (+/-15% jitter), timing +/-12%, scaled by tempo
        val f = (0.6f * inp.force + 0.4f * (inp.speed / 1500f).coerceIn(0f, 1f))
        val baseAmp = chosen.amp.start + (chosen.amp.endInclusive - chosen.amp.start) * f
        val drama = traits.drama
        val amp = (baseAmp * (1f + (rnd.nextFloat() - 0.5f) * 0.3f) * expressiveness * (0.85f + 0.3f * drama)).coerceIn(0.2f, 1.8f)
        val d0 = chosen.dur.start + (chosen.dur.endInclusive - chosen.dur.start) * rnd.nextFloat()
        val dur = (d0 * (1f + (rnd.nextFloat() - 0.5f) * 0.24f) * (1f + 0.15f * inp.repeatCount.coerceAtMost(4)) / tempo.coerceAtLeast(0.3f)).coerceAtLeast(0.15f)
        val dir = when {
            inp.dirX > 0.3f -> 1f
            inp.dirX < -0.3f -> -1f
            else -> if (inp.side < 0) -1f else 1f
        }
        val talk = chosen.lines != null && rnd.nextFloat() < chosen.talkChance * (0.6f + 0.8f * traits.chattiness)
        return ReactionInstance(chosen, amp, dur, dir, rnd.nextInt(), inp, talk)
    }

    /** Pick one line from [pool] with this picker's own RNG. */
    fun line(pool: List<String>): String = pool[rnd.nextInt(pool.size)]

    private fun weight(e: ReactionEntry, inp: TouchInput, mood: MoodSnapshot, t: Traits): Float {
        val force = (1f + e.forceBias * (inp.force - 0.5f) * 1.6f).coerceAtLeast(0.1f)
        val m = when (e.verb.mood) {
            Verb.Mood.PLAYFUL -> (0.5f + mood.energy) * (1f - 0.7f * mood.grumpy)
            Verb.Mood.AFFECTION -> (1f - 0.8f * mood.grumpy)
            Verb.Mood.ANNOYED -> 1f + 2f * mood.grumpy + 0.25f * inp.repeatCount.coerceAtMost(4)
            Verb.Mood.STARTLED -> 1f + 0.5f * mood.grumpy
            Verb.Mood.NEUTRAL -> 1f
        }
        val tr = when (e.verb) {
            Verb.FLINCH, Verb.TAIL_YELP, Verb.SPIN_LOOK -> 0.6f + 0.8f * t.drama
            Verb.DODGE -> 1.4f - 0.8f * t.boldness
            Verb.PAW_BAT, Verb.SHOW_OFF, Verb.HOP, Verb.LOOP, Verb.EAR_WIGGLE, Verb.TRUNK_BOOP -> 0.6f + 0.8f * t.playfulness
            else -> 1f
        }
        val novelty = 1f / (1f + 0.6f * (habit["${inp.part}:${e.verb}"] ?: 0f))
        return (e.weight * force * m * tr * novelty).coerceAtLeast(0f)
    }

    /** Habituation fades with a ~10 minute half-life. */
    private fun decay(now: Float) {
        val dt = now - habitAt
        if (dt <= 0f) return
        habitAt = now
        if (habit.isEmpty()) return
        val k = exp(-dt * 0.693f / HALF_LIFE)
        val it = habit.entries.iterator()
        while (it.hasNext()) { val e = it.next(); val v = e.value * k; if (v < 0.05f) it.remove() else e.setValue(v) }
    }

    companion object { const val HALF_LIFE = 600f }
}
