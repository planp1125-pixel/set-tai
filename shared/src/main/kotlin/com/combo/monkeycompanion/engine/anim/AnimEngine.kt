package com.combo.monkeycompanion.engine.anim

import com.combo.monkeycompanion.rig.Pose
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * v10.7 "Disney principles part 1" engine: one instance per on-screen character. It is a VISUAL post-pass on the
 * pose that is about to be drawn; it never touches physics bodies, never calls Random, and reads only the
 * character's on-screen position (so trajectories and RNG order are exactly as before).
 *
 * Per frame (only when "Smooth animation (beta)" is on):
 *  1. [smoothPose]  second-order (critically damped) pose blend = slow in AND slow out, frame-rate independent.
 *  2. [step]        measures screen velocity/acceleration, drives the appendage springs (tail, ears, hair, cape,
 *                   trunk), kicks the landing squash spring, and rewrites squashX/squashY volume-preserving.
 * Renderers read the outputs: [appendageAngle], [squashScaleX]/[squashScaleY], [squashPivot].
 */
class AnimEngine {
    var tuning: AnimTuning = AnimTuning.DEFAULT
        private set

    private val chains = Array(Appendage.values().size) { SpringChain(3) }
    private val out = FloatArray(Appendage.values().size)
    private val landing = Spring(260f, 0.38f)

    private var px = Float.NaN; private var py = Float.NaN
    private var vx = 0f; private var vy = 0f
    private var ax = 0f
    private var wasAir = false
    private var maxFallVy = 0f

    // pose smoother state (motion slots only)
    private val vel = FloatArray(MOTION_SLOTS)
    private var smoothInit = false

    /** Volume-preserving scale written into the pose this frame (x * y == 1 before the authored size). */
    var squashScaleX = 1f; private set
    var squashScaleY = 1f; private set
    /** Rig units above the feet to squash around: 0 on the ground (feet stay planted), ~body centre in flight. */
    var squashPivot = 0f; private set
    /** Measured screen velocity of the character, dp/s (debug / tests). */
    val velocityX get() = vx
    val velocityY get() = vy
    /** v10.22 where the feet are on the SCREEN (px), e.g. so a renderer can draw water that stays still while she moves. */
    val screenFeetX get() = if (px.isNaN()) 0f else px

    fun setTuning(t: AnimTuning) {
        if (t === tuning) return
        tuning = t
        for (a in Appendage.values()) { val at = t.of(a); chains[a.ordinal].tune(at.stiffness, at.damping, at.lag) }
        landing.stiffness = t.squashStiffness; landing.dampingRatio = t.squashDamping
    }

    fun reset() {
        chains.forEach { it.reset() }; out.fill(0f); landing.reset()
        px = Float.NaN; py = Float.NaN; vx = 0f; vy = 0f; ax = 0f; wasAir = false; maxFallVy = 0f
        smoothInit = false; vel.fill(0f)
    }

    /** v10.8 secondary action (ear twitch, tail flick, scarf snap): kick appendage [a]'s spring chain by [v] deg/s. */
    fun kick(a: Appendage, v: Float) { if (tuning.enabled && tuning.of(a).enabled) chains[a.ordinal].kick(v) }

    /** Follow-through deflection in degrees for [a] (0 when disabled). Positive = trails "up/back". */
    fun appendageAngle(a: Appendage): Float = if (tuning.enabled && tuning.of(a).enabled) out[a.ordinal] else 0f

    /**
     * Slow-in/slow-out pose blend: the body/head/limb/tail slots follow [target] as a critically damped spring
     * (no overshoot from rest, eases in AND out); every other slot (faces, props, flags) keeps the old
     * exponential blend [fallbackA] so nothing discrete ever overshoots.
     */
    fun smoothPose(current: Pose, target: Pose, dt: Float, fallbackA: Float) {
        val cv = current.v; val tv = target.v
        if (!smoothInit) { vel.fill(0f); smoothInit = true }
        // critically damped exact step (stable at any dt): x' = target + (x0 + (v0 + w x0) t) e^-wt, x0 = x - target
        val w = tuning.poseFrequency
        val e = exp(-w * dt)
        for (i in 0 until MOTION_SLOTS) {
            val x0 = cv[i] - tv[i]; val v0 = vel[i]
            val tmp = (v0 + w * x0) * dt
            cv[i] = tv[i] + (x0 + tmp) * e
            vel[i] = (v0 - w * tmp) * e
        }
        for (i in MOTION_SLOTS until cv.size) cv[i] += (tv[i] - cv[i]) * fallbackA
    }

    /**
     * Visual post-pass on [p] (the pose about to be drawn).
     * @param feetX feetY character feet on screen, px.  @param density px per dp.  @param facing +1 / -1.
     * @param airborne flying / falling / dragged (squash pivots at the body centre, not the feet).
     */
    fun step(p: Pose, dt: Float, feetX: Float, feetY: Float, density: Float, facing: Float, airborne: Boolean) {
        val tn = tuning
        if (dt <= 0f) return
        val d = if (density > 0f) density else 1f
        // 1. measure motion (dp/s), lightly filtered so 1-px window jitter doesn't wobble anything
        if (px.isNaN()) { px = feetX; py = feetY }
        val rawVx = ((feetX - px) / d / dt).coerceIn(-4000f, 4000f)
        val rawVy = ((feetY - py) / d / dt).coerceIn(-4000f, 4000f)
        px = feetX; py = feetY
        val f = 1f - exp(-dt / 0.035f)
        val nvx = vx + (rawVx - vx) * f; val nvy = vy + (rawVy - vy) * f
        ax = ((nvx - vx) / dt).coerceIn(-30000f, 30000f)
        vx = nvx; vy = nvy
        val localVx = vx * (if (facing < 0f) -1f else 1f)
        val speed = sqrt(vx * vx + vy * vy)

        // 2. follow-through springs: targets from body velocity (+ a bit of acceleration), springs add lag & overshoot
        fun drive(a: Appendage, target: Float) {
            val at = tn.of(a)
            out[a.ordinal] = if (!at.enabled) 0f else chains[a.ordinal].step(dt, (target * at.gain).coerceIn(-at.maxDeg, at.maxDeg))
        }
        val sway = -localVx * 0.012f - ax * (if (facing < 0f) -1f else 1f) * 0.0006f
        drive(Appendage.TAIL, vy * 0.010f + sway)          // falling lifts the tail, running streams it back
        drive(Appendage.EARS, -vy * 0.008f + sway * 0.5f)   // ears flop up when dropping, down when rising
        drive(Appendage.HAIR, sway)
        drive(Appendage.CAPE, sway * 1.2f - abs(vy) * 0.004f)
        drive(Appendage.TRUNK, sway * 0.8f + vy * 0.006f)

        // 3. landing kick: going from air to ground with a real fall speed squashes then rebounds
        if (airborne) { if (vy > maxFallVy) maxFallVy = vy } 
        if (wasAir && !airborne && maxFallVy > 250f) {
            val k = Easing.smoothstep((maxFallVy - 250f) / 1800f)
            landing.kick(-tn.landingSquash * (6f + 10f * k) * sqrt(tn.squashStiffness / 260f))
        }
        if (!airborne) maxFallVy = 0f
        wasAir = airborne
        val land = landing.step(dt, 0f)

        // 4. volume-preserving squash & stretch
        if (!tn.enabled) { squashScaleX = p.squashX; squashScaleY = p.squashY; squashPivot = 0f; return }
        var sy = SquashStretch.preserveY(p.squashX, p.squashY, tn.squashMax, tn.stretchMax)
        sy *= 1f + land
        if (airborne) sy *= SquashStretch.motionY(vx, vy, SquashStretch.velocityStretch(speed, tn.velocityStretch.coerceAtMost(0.12f)))
        sy = sy.coerceIn(maxOf(tn.squashMax, SQ_MIN), minOf(tn.stretchMax, SQ_MAX))   // v10.14: never rubbery (0.85..1.2)
        squashScaleY = sy; squashScaleX = 1f / sy
        squashPivot = if (airborne) PIVOT_CENTRE else 0f
        p.squashX = squashScaleX; p.squashY = squashScaleY

        // 5. feed the pose-driven appendages (shared rig tail, monkey ears) so every renderer gets them for free
        if (tn.legsDangle) {   // v10.8 riders: the legs swing behind the body instead of a tail
            val a = appendageAngle(Appendage.TAIL)
            p.legLSwing += a * 0.9f; p.legRSwing += a * 0.7f
        }
        p.tailRot += appendageAngle(Appendage.TAIL)
        p.tailSway += appendageAngle(Appendage.TAIL) / 45f
        p.earWiggle += appendageAngle(Appendage.EARS) / 12f
    }

    companion object {
        /** Pose slots bodyX..tailSway (body, head, arms, legs, tail): the ones that get slow-in/slow-out. */
        const val MOTION_SLOTS = 17
        /** About half the 165-unit rig height. */
        const val PIVOT_CENTRE = 80f
        /** v10.14 engine-wide squash/stretch window: characters stay on-model (no jelly, no pancake). */
        const val SQ_MIN = 0.85f
        const val SQ_MAX = 1.2f
    }
}
