package com.combo.monkeycompanion.engine.touch

import com.combo.monkeycompanion.engine.anim.AnimEngine
import com.combo.monkeycompanion.engine.anim.Appendage
import com.combo.monkeycompanion.engine.anim.Easing
import com.combo.monkeycompanion.rig.Pose
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * v10.8 additive reaction layer (M3b): plays [ReactionInstance]s ON TOP of the base pose (the v9.5 state animation
 * stays underneath and untouched). Two slots: the current clip and the one fading out, so verb -> verb is a 120 ms
 * crossfade. Each clip is pose-to-pose keys (anticipation -> action -> hold -> follow-through) sampled procedurally,
 * with arcs (head / hops / loops move on curves) and per-clip noise so holds never freeze. No RNG calls at all:
 * variation comes from the instance's seed. No allocation per frame.
 */
class ReactionLayer {
    private class Slot { var inst: ReactionInstance? = null; var t = 0f; var fadeOut = -1f; var kicked = false }
    private val cur = Slot(); private val old = Slot()

    /** True while anything is playing (the view keeps 60 fps for it). */
    val busy: Boolean get() = cur.inst != null || old.inst != null
    val playing: Verb? get() = cur.inst?.verb
    /** 0..1 progress of the current clip (debug / interrupt rules). */
    val progress: Float get() = cur.inst?.let { (cur.t / it.dur).coerceIn(0f, 1f) } ?: 0f

    fun clear() { cur.inst = null; old.inst = null }

    /**
     * Start [r]. Interrupt rules: same verb still in its wind-up = restart (escalate, no fade);
     * otherwise the running clip crossfades out over 120 ms while the new one fades in.
     */
    fun play(r: ReactionInstance) {
        val c = cur.inst
        if (c != null && !(c.verb == r.verb && cur.t / c.dur < 0.4f)) {
            old.inst = c; old.t = cur.t; old.fadeOut = CROSSFADE; old.kicked = true
        }
        cur.inst = r; cur.t = 0f; cur.fadeOut = -1f; cur.kicked = false
    }

    fun step(dt: Float) {
        if (cur.inst != null) { cur.t += dt; if (cur.t >= cur.inst!!.dur + FADE_OUT) cur.inst = null }
        if (old.inst != null) { old.t += dt; old.fadeOut -= dt; if (old.fadeOut <= 0f) old.inst = null }
    }

    /** Add the playing clips to [p]. [facing] +1/-1 maps screen directions into rig space; [engine] (may be null) gets spring kicks. */
    fun apply(p: Pose, facing: Float, engine: AnimEngine?) {
        old.inst?.let { sample(it, old.t, p, facing, (old.fadeOut / CROSSFADE).coerceIn(0f, 1f), engine, old) }
        cur.inst?.let { sample(it, cur.t, p, facing, 1f, engine, cur) }
        // solid drawing: keep reaction poses inside sane limits
        p.headRot = p.headRot.coerceIn(-40f, 40f)
    }

    private fun sample(r: ReactionInstance, t: Float, p: Pose, facing: Float, mix: Float, engine: AnimEngine?, s: Slot) {
        val d = r.dur
        // weight: fast in (easeOutCubic, 80 ms), soft out (easeInOutSine, 200 ms after the clip)
        val wIn = Easing.easeOutCubic(t / FADE_IN)
        val wOut = if (t <= d) 1f else 1f - Easing.easeInOutSine((t - d) / FADE_OUT)
        val w = wIn * wOut * mix
        if (w <= 0.001f) return
        val u = (t / d).coerceIn(0f, 1f)
        val a = r.amp * w
        val dirL = r.dir * (if (facing < 0f) -1f else 1f)    // screen direction in rig space
        val e = keys(u, r.entry.antic)                         // -antic .. 1 .. follow-through
        val n1 = sin(t * 7.1f + r.seed * 0.37f); val n2 = sin(t * 4.3f + r.seed * 0.11f)
        val kickNow = !s.kicked && u >= r.entry.antic
        if (kickNow) s.kicked = true
        when (r.verb) {
            Verb.LEGACY -> Unit
            Verb.FLINCH -> {
                p.bodyX -= dirL * 12f * a * e; p.bodyY += 3f * a * e
                squash(p, 1f - 0.1f * a * e)
                p.eyeOpen += 0.5f * a * e; p.browY -= 3f * a * e; p.mouthOpen += 0.45f * a * e; p.mouthSmile -= 0.6f * a * e
                p.armLRaise += 35f * a * e; p.armRRaise += 35f * a * e
                p.headRot -= dirL * 6f * a * e
            }
            Verb.GIGGLE -> {
                val sh = sin(t * 2f * PI.toFloat() * 11f)
                p.bodyRot += 4f * a * sh * e; p.headRot += 5f * a * sin(t * 69f + 0.6f) * e
                p.bodyY -= 2f * a * abs(sh) * e
                if (e > 0.3f) p.eyesHappy = 1f
                p.mouthOpen += 0.4f * a * e; p.mouthSmile += 0.4f * a * e
                p.armLRaise += 20f * a * e; p.armRRaise += 20f * a * e
            }
            Verb.LEAN_IN -> {
                // the head rolls toward the finger on an arc around the neck (headRot + headY together)
                val k = e.coerceAtLeast(0f)
                p.headRot += dirL * 11f * a * k + 2f * n2 * k; p.headY += 2.5f * a * k
                p.bodyRot += dirL * 3f * a * k
                if (k > 0.4f) p.eyesHappy = 1f
                p.blush += 0.5f * a * k; p.mouthSmile += 0.35f * a * k
                p.tailSway += 0.4f * a * k * n1
            }
            Verb.NOD -> {
                val nod = sin(u * PI.toFloat() * 4f) * e.coerceAtLeast(0f)
                p.headRot += 4f * a * nod; p.headY += 3f * a * abs(nod)
                p.mouthSmile += 0.3f * a * e
            }
            Verb.SHOW_OFF -> {
                // a little backflip: the body turns 360 degrees along an arc (up, over, down)
                val k = Easing.easeInOutCubic(((u - r.entry.antic) / (1f - r.entry.antic - 0.15f)).coerceIn(0f, 1f))
                if (u < r.entry.antic) { squash(p, 1f - 0.18f * a * (u / r.entry.antic)); p.bodyY += 5f * a * (u / r.entry.antic) }
                p.bodyRot -= 360f * k * (if (k > 0f && k < 1f) 1f else 0f)
                p.bodyY -= 34f * a * sin(PI.toFloat() * k)
                if (k > 0.98f) { p.eyesHappy = 1f; p.armLRaise += 120f * e; p.armRRaise += 120f * e }
                p.mouthSmile += 0.5f * e
            }
            Verb.TAIL_YELP -> {
                val hop = arc(u, r.entry.antic)
                p.bodyY -= 18f * a * hop
                if (u < r.entry.antic) squash(p, 1f - 0.12f * a) else squash(p, 1f + 0.08f * a * hop)
                p.tailRot += 55f * a * e; p.eyeOpen += 0.55f * a * e; p.mouthOpen += 0.5f * a * e
                p.lookX -= 3.5f * e * (if (facing < 0f) -1f else 1f)      // look back at the finger
                p.headRot -= 8f * a * e
            }
            Verb.TAIL_SWAT -> {
                if (kickNow) engine?.kick(Appendage.TAIL, dirL * 520f * r.amp)
                p.tailRot += 25f * a * sin(u * PI.toFloat() * 3f) * e
                p.browAngle += 9f * a * e; p.mouthSmile -= 0.5f * a * e
                p.headRot -= 8f * a * e; p.lookX -= 3f * e
            }
            Verb.TAIL_CURL -> {
                p.tailSway += 0.8f * a * e * sin(t * 3f + r.seed); p.tailRot += 15f * a * e
                p.eyeOpen *= 1f - 0.3f * a * e.coerceAtLeast(0f); p.mouthSmile += 0.4f * a * e; p.blush += 0.3f * a * e
            }
            Verb.EAR_FLICK -> {
                if (kickNow) engine?.kick(Appendage.EARS, dirL * 480f * r.amp)
                p.earWiggle += sin(t * 42f) * 0.9f * a * e
                p.eyeOpen *= 1f - 0.35f * a * e.coerceAtLeast(0f)
                p.headRot -= dirL * 6f * a * e
            }
            Verb.EAR_WIGGLE -> {
                p.earWiggle += sin(t * 16f) * 0.7f * a * e
                if (e > 0.5f) p.eyesHappy = 1f
                p.mouthSmile += 0.4f * a * e
            }
            Verb.TRUNK_BOOP -> {
                if (kickNow) engine?.kick(Appendage.TRUNK, dirL * 420f * r.amp)
                p.eyeOpen += 0.35f * a * e; p.headRot -= dirL * 7f * a * e; p.headY += 2f * a * e
                p.mouthOpen += 0.3f * a * e; p.mouthSmile += 0.4f * a * e
            }
            Verb.SPIN_LOOK -> {
                p.bodyRot += dirL * 18f * a * e; p.headRot += dirL * 14f * a * e
                p.lookX += dirL * 4f * e; p.eyeOpen += 0.3f * a * e
            }
            Verb.PAW_BAT -> {
                // the arm on the finger's side swings up on an arc; contact frame at ~40%
                val k = e.coerceAtLeast(-0.3f)
                val raise = 95f * a * k; val elbow = -40f * a * sin(PI.toFloat() * u)
                if (dirL > 0f) { p.armRRaise += raise; p.armRElbow += elbow } else { p.armLRaise += raise; p.armLElbow += elbow }
                p.bodyRot += dirL * 4f * a * k; p.mouthSmile += 0.4f * a * e; p.mouthOpen += 0.25f * a * e
            }
            Verb.DODGE -> {
                // hop sideways AWAY from the finger along a parabola, squash on landing
                val hop = arc(u, r.entry.antic)
                val side = Easing.easeOutCubic(((u - r.entry.antic) / (1f - r.entry.antic)).coerceIn(0f, 1f))
                p.bodyX -= dirL * 26f * a * side * (1f - Easing.smoothstep((u - 0.7f) / 0.3f))
                p.bodyY -= 14f * a * hop
                p.bodyRot -= dirL * 8f * a * hop
                p.mouthSmile += 0.5f * a * e
            }
            Verb.HOP -> {
                val hop = arc(u, r.entry.antic)
                p.bodyY -= 20f * a * hop
                if (u < r.entry.antic) squash(p, 1f - 0.2f * a * (u / r.entry.antic)) else squash(p, 1f + 0.1f * a * hop)
                p.armLRaise += 60f * a * hop; p.armRRaise += 60f * a * hop
                p.legLLift += 6f * hop; p.legRLift += 6f * hop
                p.eyesHappy = if (hop > 0.4f) 1f else p.eyesHappy
            }
            Verb.DIZZY -> {
                p.bodyRot += 8f * a * sin(t * 9f) * e; p.headRot += 12f * a * sin(t * 7f + 1f) * e
                p.lookX += 3f * cos(t * 11f) * e; p.lookY += 2f * sin(t * 11f) * e
                p.eyeOpen *= 1f - 0.2f * e.coerceAtLeast(0f); p.mouthOpen += 0.3f * e
            }
            Verb.ROCKET_SPUTTER -> {
                // cough cough: the rocket sputters, shudders and rolls once
                val cough = abs(sin(t * 21f)) * (if (sin(t * 5.3f) > 0f) 1f else 0.35f)
                p.rbSputter = maxOf(p.rbSputter, e.coerceAtLeast(0f))
                p.rbSpin += 360f * Easing.easeInOutCubic(((u - 0.35f) / 0.5f).coerceIn(0f, 1f)) * (if (r.amp > 0f) 1f else 0f)
                p.bodyX += 2.5f * a * sin(t * 47f) * e; p.bodyY += 3f * a * cough * e
                p.bodyRot += 5f * a * sin(t * 13f) * e
                p.eyeOpen += 0.45f * a * e; p.mouthOpen += 0.4f * a * e; p.mouthSmile -= 0.3f * a * e
                p.armLRaise += 30f * a * e; p.armRRaise += 30f * a * e
            }
            Verb.LOOP -> {
                // a loop-de-loop: the body travels around a circle (a true arc) while turning a full 360
                val k = Easing.easeInOutSine(((u - r.entry.antic) / (1f - r.entry.antic)).coerceIn(0f, 1f))
                val ang = 2f * PI.toFloat() * k
                val rad = 26f * a
                p.bodyX += dirL * rad * sin(ang)
                p.bodyY -= rad * (1f - cos(ang))
                p.bodyRot -= dirL * 360f * k * (if (k < 1f) 1f else 0f)
                p.mouthOpen += 0.4f * e; p.mouthSmile += 0.5f * e; p.eyeOpen += 0.2f * e
                p.rbSputter = maxOf(p.rbSputter, 0f)
            }
            Verb.SPARKLER -> {
                val k = e.coerceAtLeast(0f)
                p.sparkler = maxOf(p.sparkler, k)
                p.armRRaise += 85f * k + 10f * sin(t * 6f) * k; p.armRElbow -= 20f * k
                if (k > 0.5f) p.eyesHappy = 1f
                p.mouthSmile += 0.5f * k; p.blush += 0.3f * k
                p.headRot += 4f * n2 * k
            }
            Verb.BOOST -> {
                // v10.10: just a happy rev in place (a tap never launches a flight)
                val k = e.coerceAtLeast(0f)
                squash(p, 1f - 0.15f * k); p.eyeOpen += 0.4f * k; p.mouthOpen += 0.5f * k
                p.rbSputter = maxOf(p.rbSputter, 0.5f * k)
            }
        }
    }

    private fun squash(p: Pose, sy: Float) { val y = sy.coerceIn(0.7f, 1.3f); p.squashY *= y; p.squashX /= y }

    companion object {
        const val FADE_IN = 0.08f
        const val FADE_OUT = 0.2f
        const val CROSSFADE = 0.12f

        /**
         * Pose-to-pose key envelope over clip time u (0..1): anticipation dips to -0.3 (the opposite move), the action
         * pops to 1 with a little overshoot (easeOutBack), holds alive, then follow-through settles back with a
         * damped wobble. [antic] = share of the clip spent winding up (0 = a surprise).
         */
        fun keys(u: Float, antic: Float): Float {
            val a = antic.coerceIn(0f, 0.5f)
            if (a > 0f && u < a) return -0.3f * Easing.smoothstep(u / a)
            val act = 0.25f
            val v = (u - a) / (1f - a)
            return when {
                v < act -> -0.3f * (1f - v / act) * (if (a > 0f) 1f else 0f) + Easing.easeOutBack(v / act, 1.4f)
                v < 0.7f -> 1f
                else -> { val f = (v - 0.7f) / 0.3f; (1f - Easing.smoothstep(f)) + 0.08f * sin(f * PI.toFloat() * 2f) * (1f - f) }
            }
        }

        /** Jump arc 0..1..0 after the wind-up (a parabola: ballistic, so hops read as arcs). */
        fun arc(u: Float, antic: Float): Float {
            if (u <= antic) return 0f
            val v = ((u - antic) / (1f - antic)).coerceIn(0f, 1f)
            return 4f * v * (1f - v)
        }

        @Suppress("unused") private fun damp(x: Float, k: Float) = x * exp(-k)
    }
}
