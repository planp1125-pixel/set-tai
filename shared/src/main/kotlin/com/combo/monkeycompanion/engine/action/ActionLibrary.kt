package com.combo.monkeycompanion.engine.action

import com.combo.monkeycompanion.rig.Pose
import kotlin.math.sin

/**
 * v10.31 shared Action Library: reusable pose overlays that ANY character plays from one place.
 *
 * Every built-in and studio (custom) character draws through the same [Pose] slots and the same
 * [com.combo.monkeycompanion.rig.MonkeyRig] skeleton, so one action written here works on Kutti Satan, Angel,
 * Settai, Gajju, Chittu, heroes and custom companions alike. No per-character copies, no bitmaps.
 *
 * An action is layered on top of whatever state pose is already in the frame ([apply] blends by weight [k]),
 * so it can run while the body idles, sits or floats. Pure function of (pose, time, weight): no RNG, so the
 * GoldenTrace frame-loop random stream is untouched.
 *
 * To add one: add an [Action] object here, register it in [all], call `ActionLibrary.apply(id, pose, t, k)`
 * from the host (see docs/LIBRARIES-AND-SHOWS.md).
 */
object ActionLibrary {
    /** One reusable library action. [loop]=true: holds while active (listening); false = one-shot. */
    abstract class Action(val id: String, val title: String, val loop: Boolean) {
        /** Blend this action onto [p] with weight [k] (0 = untouched, 1 = full pose), [t] = seconds into the action. */
        abstract fun apply(p: Pose, t: Float, k: Float)
        /** v10.32: [arg] = a direction / amount some actions use (look_at: -1 left .. 1 right). Default: ignore it. */
        open fun apply(p: Pose, t: Float, k: Float, arg: Float) = apply(p, t, k)
    }

    /**
     * "Listening for gossip": right hand cupped at the ear, head tilted toward it, eyes sliding sideways,
     * brows up, a tiny curious "o" mouth, slow lean-in nods. Played while the mic is actually listening.
     */
    object ListenEar : Action("listen_ear", "Listening for gossip (hand at ear)", loop = true) {
        const val ARM_RAISE = 165f
        const val ARM_ELBOW = -80f
        const val HEAD_TILT = 12f
        override fun apply(p: Pose, t: Float, k: Float) {
            if (k <= 0f) return
            val w = k.coerceIn(0f, 1f)
            val nod = sin(t * 2.6f)   // slow "mm-hmm… go on" lean
            p.armRRaise = lerp(p.armRRaise, ARM_RAISE + 3f * nod, w)
            p.armRElbow = lerp(p.armRElbow, ARM_ELBOW, w)
            p.headRot = lerp(p.headRot, HEAD_TILT + 2.5f * nod, w)
            p.bodyRot = lerp(p.bodyRot, 3f, w)
            p.lookX = lerp(p.lookX, 0.75f, w)
            p.lookY = lerp(p.lookY, -0.1f, w)
            p.browY = lerp(p.browY, -2.5f, w)
            p.browAngle = lerp(p.browAngle, -4f, w)
            p.mouthOpen = lerp(p.mouthOpen, 0.18f, w)
            p.mouthSmile = lerp(p.mouthSmile, 0.25f, w)
            p.earPerk = maxOf(p.earPerk, w)
        }
    }

    /** Turn the head and eyes toward a side: [arg] -1 = screen left .. 1 = screen right (in the body's facing). */
    object LookAt : Action("look_at", "Look at someone / something", loop = true) {
        override fun apply(p: Pose, t: Float, k: Float) = apply(p, t, k, 1f)
        override fun apply(p: Pose, t: Float, k: Float, arg: Float) {
            val w = k.coerceIn(0f, 1f); if (w <= 0f) return
            val a = arg.coerceIn(-1f, 1f)
            p.lookX = lerp(p.lookX, 0.9f * a, w); p.headRot = lerp(p.headRot, 9f * a, w)
        }
    }

    /** Left arm up in front, holding a shield (the Props engine draws the shield at the hand). */
    object RaiseShield : Action("raise_shield", "Raise a shield", loop = true) {
        override fun apply(p: Pose, t: Float, k: Float) {
            val w = k.coerceIn(0f, 1f); if (w <= 0f) return
            p.armLRaise = lerp(p.armLRaise, 95f, w); p.armLElbow = lerp(p.armLElbow, -55f, w)
            p.bodyRot = lerp(p.bodyRot, -4f + 1.2f * sin(t * 30f), w)
            p.browAngle = lerp(p.browAngle, 9f, w)
        }
    }

    /** Both hands thrust forward (rays / spells from the hands). */
    object CastHands : Action("cast_hands", "Both hands forward (cast)", loop = true) {
        override fun apply(p: Pose, t: Float, k: Float) {
            val w = k.coerceIn(0f, 1f); if (w <= 0f) return
            val jit = 2f * sin(t * 40f)
            p.armRRaise = lerp(p.armRRaise, 92f + jit, w); p.armRElbow = lerp(p.armRElbow, -6f, w)
            p.armLRaise = lerp(p.armLRaise, 78f - jit, w); p.armLElbow = lerp(p.armLElbow, -6f, w)
            p.bodyRot = lerp(p.bodyRot, 5f, w); p.browAngle = lerp(p.browAngle, 12f, w)
        }
    }

    /** Overarm throw, one swing per [PERIOD] seconds (fast fireballs, dosa pans, anything thrown). */
    object Throw : Action("throw", "Throw (overarm)", loop = true) {
        const val PERIOD = 0.35f
        override fun apply(p: Pose, t: Float, k: Float) {
            val w = k.coerceIn(0f, 1f); if (w <= 0f) return
            val ph = (t % PERIOD) / PERIOD
            val raise = if (ph < 0.55f) 120f + 50f * (ph / 0.55f) else 170f - 110f * ((ph - 0.55f) / 0.45f)
            p.armRRaise = lerp(p.armRRaise, raise, w); p.armRElbow = lerp(p.armRElbow, -30f, w)
            p.bodyRot = lerp(p.bodyRot, if (ph < 0.55f) -4f else 6f, w)
        }
    }

    /** Fear: hands up in front of the face, a small crouch, shaking. */
    object Cower : Action("cower", "Cower in fear", loop = true) {
        override fun apply(p: Pose, t: Float, k: Float) {
            val w = k.coerceIn(0f, 1f); if (w <= 0f) return
            val sh = 2.5f * sin(t * 45f)
            p.armRRaise = lerp(p.armRRaise, 140f + sh, w); p.armRElbow = lerp(p.armRElbow, -95f, w)
            p.armLRaise = lerp(p.armLRaise, 140f - sh, w); p.armLElbow = lerp(p.armLElbow, -95f, w)
            p.squashY = lerp(p.squashY, 0.93f, w); p.squashX = lerp(p.squashX, 1.04f, w)
        }
    }

    /** Point with the right arm. */
    object Point : Action("point", "Point", loop = true) {
        override fun apply(p: Pose, t: Float, k: Float) {
            val w = k.coerceIn(0f, 1f); if (w <= 0f) return
            p.armRRaise = lerp(p.armRRaise, 95f, w); p.armRElbow = lerp(p.armRElbow, 0f, w)
        }
    }

    val all: List<Action> = listOf(ListenEar, LookAt, RaiseShield, CastHands, Throw, Cower, Point)

    fun byId(id: String): Action? = all.firstOrNull { it.id == id }

    /** Play library action [id] on [p] (any character). Unknown id = no-op. */
    fun apply(id: String, p: Pose, t: Float, k: Float, arg: Float = Float.NaN): Boolean {
        val a = byId(id) ?: return false
        if (arg.isNaN()) a.apply(p, t, k) else a.apply(p, t, k, arg); return true
    }

    private fun lerp(a: Float, b: Float, w: Float) = a + (b - a) * w
}
