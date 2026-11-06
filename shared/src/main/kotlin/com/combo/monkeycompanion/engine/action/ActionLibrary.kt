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

    val all: List<Action> = listOf(ListenEar)

    fun byId(id: String): Action? = all.firstOrNull { it.id == id }

    /** Play library action [id] on [p] (any character). Unknown id = no-op. */
    fun apply(id: String, p: Pose, t: Float, k: Float): Boolean {
        val a = byId(id) ?: return false
        a.apply(p, t, k); return true
    }

    private fun lerp(a: Float, b: Float, w: Float) = a + (b - a) * w
}
