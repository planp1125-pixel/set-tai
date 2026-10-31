package com.combo.monkeycompanion.engine.anim

import com.combo.monkeycompanion.rig.Pose
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** What kind of wind-up to play before an action (Disney "anticipation"). */
enum class Antic {
    /** Crouch down before a jump / leap / take-off. */
    CROUCH,
    /** Lean back and pull the arms in before a launch, a power or a throw. */
    LEAN_BACK,
    /** Eyes first: the eyes dart toward where the body is about to go (small, no crouch). */
    EYES,
}

/**
 * v10.8 cross-window "staging" bus: when one character starts a reaction, the others (Duo / Trio, each in its own
 * overlay window but in the same process) briefly glance toward it and hold still, so only one thing reads at a time.
 * Main-thread only, no allocation.
 */
object StageBus {
    @Volatile var owner = 0; private set
    @Volatile var screenX = 0f; private set
    @Volatile var until = 0f; private set
    /** Monotonic seconds (any clock all views share; MonkeyView uses SystemClock.uptimeMillis() / 1000). */
    fun announce(who: Int, x: Float, now: Float, secs: Float) { owner = who; screenX = x; until = now + secs }
    /** True while someone other than [me] has the stage. */
    fun othersHaveStage(me: Int, now: Float) = owner != 0 && owner != me && now < until
    fun clear() { owner = 0; until = 0f }
}

/**
 * v10.8 "Disney principles part 2": the living visual layer drawn on top of the blended pose, after [AnimEngine.step].
 * It never touches physics bodies and never calls the shared frame-loop RNG: every choice comes from its own seeded
 * [Random] (one per overlay window), so golden traces and the v9.5 Random call order are unchanged.
 *
 * Covers: anticipation (wind-ups), secondary action (ear twitches, tail flicks, look-arounds with eyes leading the
 * head, a blink on big head turns), timing (per-character tempo), staging (glance at whoever has the stage, hold
 * still), solid drawing (lean into fast motion, head leads a turn), appeal (catch-light twinkle, happy squint on pet).
 * Breathing and the random blink already live in the base animation and the view; they are kept as they are.
 */
class LivingLayer(seed: Long = 1L) {
    private val rnd = Random(seed)
    var tuning: AnimTuning = AnimTuning.DEFAULT

    // secondary-action timers (seconds until the next one; NaN = not scheduled yet)
    private var earIn = Float.NaN; private var tailIn = Float.NaN; private var lookIn = Float.NaN
    private var lookTarget = 0f; private var lookLeft = 0f
    private var eyeLook = 0f; private var headLook = 0f
    private var blinkT = -1f

    // anticipation
    private var anticKind = Antic.CROUCH
    private var anticT = -1f; private var anticDur = 0.1f; private var anticDir = 0f

    // appeal
    private var squintLeft = 0f; private var squintK = 0f

    // solid drawing
    private val lean = Spring(120f, 0.7f)
    private var lastFacing = 0f; private var turnT = -1f; private var turnDir = 0f

    private var clock = 0f

    // v10.14 livelier behaviour (all on this layer's own RNG; visual only)
    /** Seconds calm in a row (boredom builds up). */
    var calmFor = 0f; private set
    /** The idle-variety bit playing now (-1 = none) and its clock. */
    var idleBit = -1; private set
    private var idleT = 0f; private var idleIn = Float.NaN
    /** Finger watching: screen direction of the user's finger (-1..1, -1..1) and how strongly (0..1). */
    private var fingerX = 0f; private var fingerY = 0f; private var fingerOn = false; private var fingerK = 0f
    /** Partner reaction: a quick "huh?!" pop when someone else grabs the stage. */
    private var wasStaged = false; private var noticeT = -1f
    /** Number of partner "huh?!" pops so far (tests). */
    var notices = 0; private set

    /** v10.14: eyes (then head) follow the user's finger while it is down; [dx],[dy] = finger direction from the face, -1..1. */
    fun watchFinger(dx: Float, dy: Float) { fingerX = dx.coerceIn(-1f, 1f); fingerY = dy.coerceIn(-1f, 1f); fingerOn = true }
    fun fingerUp() { fingerOn = false }

    /** True while a wind-up, look or squint is running (the view keeps drawing at full rate). */
    val busy: Boolean get() = anticT >= 0f || idleBit >= 0 || noticeT >= 0f || fingerK > 0.05f || lookLeft > 0f || squintLeft > 0f || turnT >= 0f || blinkT >= 0f || abs(lean.value) > 0.3f

    /** In an anticipation wind-up right now (the overlay renders at full rate). */
    val anticipating: Boolean get() = anticT >= 0f

    fun reset() {
        earIn = Float.NaN; tailIn = Float.NaN; lookIn = Float.NaN; lookLeft = 0f; eyeLook = 0f; headLook = 0f
        anticT = -1f; squintLeft = 0f; squintK = 0f; lean.reset(); lastFacing = 0f; turnT = -1f; blinkT = -1f
        calmFor = 0f; idleBit = -1; idleIn = Float.NaN; fingerOn = false; fingerK = 0f; wasStaged = false; noticeT = -1f
    }

    /** Start a wind-up of [kind]; [dir] = -1/+1 screen direction of the coming move (0 = straight up / unknown). */
    fun anticipate(kind: Antic, dir: Float = 0f) {
        anticKind = kind; anticDir = dir
        anticDur = (tuning.anticipationSec / tuning.tempo.coerceAtLeast(0.3f)).coerceIn(0.06f, 0.2f) * (if (kind == Antic.EYES) 0.8f else 1f)
        anticT = 0f
    }

    /** Appeal: ^ ^ eyes and a blush for [secs] (petting). */
    fun happySquint(secs: Float) { squintLeft = maxOf(squintLeft, secs) }

    /** Look toward screen direction [dir] (-1 left .. +1 right) for [secs], eyes first, the head follows. */
    fun glance(dir: Float, secs: Float) { lookTarget = dir.coerceIn(-1f, 1f); lookLeft = secs }

    private fun next(every: Float): Float = if (every <= 0f) Float.POSITIVE_INFINITY
        else every * (0.5f + rnd.nextFloat()) / tuning.tempo.coerceAtLeast(0.3f)

    /**
     * Apply to [p] (the pose about to be drawn). [calm] = idle on a surface (micro-actions allowed); [hold] = another
     * character has the stage or this one is mid-reaction (hold still: no new micro-actions); [stageDir] = which way
     * the staged character is (-1/0/+1); [facing] +1/-1; [vxDp] screen velocity (dp/s).
     */
    fun step(p: Pose, dt: Float, engine: AnimEngine?, calm: Boolean, hold: Boolean, stageDir: Float, facing: Float, vxDp: Float) {
        if (dt <= 0f) return
        clock += dt
        val tn = tuning
        val tempo = tn.tempo.coerceAtLeast(0.3f)
        if (earIn.isNaN()) { earIn = next(tn.earTwitchEvery); tailIn = next(tn.tailFlickEvery); lookIn = next(tn.lookAroundEvery) }

        // ---- secondary action: idle micro-actions (only when calm and nobody else has the stage) ----
        if (calm && !hold) {
            earIn -= dt; tailIn -= dt; lookIn -= dt
            if (earIn <= 0f) {
                earIn = next(tn.earTwitchEvery)
                engine?.kick(Appendage.EARS, (if (rnd.nextBoolean()) 1f else -1f) * (220f + 160f * rnd.nextFloat()))
            }
            if (tailIn <= 0f) {
                tailIn = next(tn.tailFlickEvery)
                engine?.kick(Appendage.TAIL, (if (rnd.nextBoolean()) 1f else -1f) * (260f + 200f * rnd.nextFloat()))
            }
            if (lookIn <= 0f) {
                lookIn = next(tn.lookAroundEvery)
                glance((rnd.nextFloat() * 2f - 1f).let { if (abs(it) < 0.35f) 0.6f * (if (it < 0f) -1f else 1f) else it }, (0.7f + 0.8f * rnd.nextFloat()) / tempo)
                if (rnd.nextFloat() < 0.5f) blinkT = 0f   // animators blink on a head turn
            }
        }
        // ---- staging: glance at whoever has the stage ----
        if (stageDir != 0f && lookLeft <= 0.05f) glance(stageDir, 0.5f)
        // v10.14 react to a partner: the moment someone else takes the stage, a quick "huh?!" (eyes pop, little hop)
        val staged = stageDir != 0f
        if (staged && !wasStaged && noticeT < 0f) { noticeT = 0f; notices++ }
        wasStaged = staged

        // ---- v10.14 boredom: idle variety after a while calm (stretch, foot tap, yawn, look at the user) ----
        if (calm && !hold && !fingerOn) calmFor += dt else { calmFor = 0f; idleBit = -1 }
        if (idleIn.isNaN()) idleIn = BORED_AFTER * (0.7f + 0.6f * rnd.nextFloat())
        if (idleBit < 0 && calmFor > idleIn) { idleBit = rnd.nextInt(IDLE_BITS); idleT = 0f; idleIn = calmFor + BORED_AFTER * (0.6f + 0.8f * rnd.nextFloat()) }
        if (idleBit >= 0) {
            idleT += dt * tempo
            val u = idleT / IDLE_SECS
            if (u >= 1f) idleBit = -1 else {
                val e = Easing.smoothstep((u / 0.25f).coerceIn(0f, 1f)) * (1f - Easing.smoothstep(((u - 0.75f) / 0.25f).coerceIn(0f, 1f)))
                when (idleBit) {
                    0 -> { p.armLRaise += 120f * e; p.armRRaise += 120f * e; squash(p, 1f + 0.08f * e); p.eyeOpen *= 1f - 0.6f * e }   // big stretch
                    1 -> { p.legRLift += 6f * e * abs(sin(idleT * 9f)); p.headRot += 3f * e * sin(idleT * 4.5f) }                       // foot tap
                    2 -> { p.mouthOpen = maxOf(p.mouthOpen, 0.9f * e); p.eyeOpen *= 1f - 0.8f * e; p.headRot -= 6f * e }              // yawn
                    else -> { p.lookX += 0f; p.lookY += 2.5f * e; p.mouthSmile = maxOf(p.mouthSmile, 0.7f * e); p.headRot += 4f * e * sin(idleT * 2f) }   // looks out at you
                }
            }
        }

        // eyes lead (fast), head follows (slower): "eyes first" anticipation for looks
        fingerK += ((if (fingerOn) 1f else 0f) - fingerK) * (1f - exp(-dt * 14f))
        val tgt = if (fingerK > 0.05f) fingerX * fingerK + (if (lookLeft > 0f) lookTarget else 0f) * (1f - fingerK) else if (lookLeft > 0f) lookTarget else 0f
        p.lookY += 3f * fingerY * fingerK
        if (lookLeft > 0f) lookLeft -= dt
        eyeLook += (tgt - eyeLook) * (1f - exp(-dt * 22f * tempo))
        headLook += (eyeLook - headLook) * (1f - exp(-dt * 7f * tempo))
        val localLook = eyeLook * facing          // rig space: + = the way it faces
        p.lookX += 4.2f * localLook
        p.headRot += 7f * headLook * facing
        if (blinkT >= 0f) {
            blinkT += dt
            val k = blinkT / 0.14f
            if (k >= 1f) blinkT = -1f else p.eyeOpen *= abs(1f - 2f * k).coerceAtLeast(0.05f)
        }

        // ---- solid drawing: lean into fast sideways motion; the head leads a turn ----
        val localVx = vxDp * (if (facing < 0f) -1f else 1f)
        val leanT = tn.leanDeg * (localVx / 700f).coerceIn(-1f, 1f)
        p.bodyRot += lean.step(dt, leanT)
        if (lastFacing != 0f && facing != lastFacing) { turnT = 0f; turnDir = facing }
        lastFacing = facing
        if (turnT >= 0f) {
            turnT += dt * tempo
            val k = turnT / 0.28f
            if (k >= 1f) turnT = -1f else {
                val e = sin(PI.toFloat() * k)
                p.lookX += 2.5f * e                 // pupils already in the new direction (3/4 hint)
                p.headRot += 5f * e
                p.bodyRot -= 2.5f * e               // the body lags behind the head
                p.bodyY += 1.6f * e                 // v10.14 arcs: a turn dips down and comes back up, never a flat swivel
            }
        }

        // ---- anticipation: crouch / lean back, eases in, snaps out (the move itself is the release) ----
        if (anticT >= 0f) {
            anticT += dt
            val u = anticT / anticDur
            if (u >= 1.35f) anticT = -1f else {
                val k = if (u < 0.75f) Easing.smoothstep(u / 0.75f) else 1f - Easing.easeOutCubic((u - 0.75f) / 0.6f)
                val rel = if (u >= 0.75f) sin(PI.toFloat() * ((u - 0.75f) / 0.6f).coerceIn(0f, 1f)) else 0f
                val depth = tn.anticipationDepth * tn.expressiveness
                when (anticKind) {
                    Antic.CROUCH -> {
                        squash(p, 1f - 0.28f * depth * k + 0.12f * depth * rel)
                        p.bodyY += 7f * depth * k
                        p.armLRaise -= 18f * k; p.armRRaise -= 18f * k
                        p.legLLift += 5f * k; p.legRLift += 5f * k
                    }
                    Antic.LEAN_BACK -> {
                        squash(p, 1f - 0.15f * depth * k)
                        p.bodyRot -= 9f * depth * k * (if (anticDir == 0f) 1f else anticDir * facing)
                        p.armLRaise -= 25f * k; p.armRRaise -= 35f * k; p.armRElbow -= 30f * k
                    }
                    Antic.EYES -> Unit
                }
                p.lookX += 3.5f * (if (anticDir == 0f) 0f else anticDir * facing) * minOf(1f, u * 3f)
                p.lookY -= 1.5f * k
                p.eyeOpen *= 1f + 0.12f * k
            }
        }

        // ---- v10.14 partner notice pop ("huh?!"): eyes wide, a small hop on an arc, settles ----
        if (noticeT >= 0f) {
            noticeT += dt * tempo
            val u = noticeT / 0.45f
            if (u >= 1f) noticeT = -1f else {
                val e = sin(PI.toFloat() * u)
                p.eyeOpen *= 1f + 0.25f * e; p.bodyY -= 6f * e; squash(p, 1f + 0.06f * e)
            }
        }

        // ---- appeal: happy squint + blush when petted; a soft catch-light twinkle ----
        if (squintLeft > 0f) squintLeft -= dt
        squintK += ((if (squintLeft > 0f) 1f else 0f) - squintK) * (1f - exp(-dt * 12f))
        if (squintK > 0.02f) {
            val s = squintK * tn.petSquint
            if (s > 0.5f) p.eyesHappy = 1f
            p.blush = maxOf(p.blush, 0.6f * s)
            p.mouthSmile = maxOf(p.mouthSmile, 0.6f + 0.35f * s)
        }
        p.glint = 0.5f + 0.5f * sin(clock * 1.7f)
    }

    companion object {
        /** Seconds of calm before the first idle-variety bit (randomised 0.7-1.3x). */
        const val BORED_AFTER = 18f
        const val IDLE_BITS = 4
        const val IDLE_SECS = 1.8f
    }

    /** Volume-preserving squash multiply on whatever the pose already has. */
    private fun squash(p: Pose, sy: Float) {
        val y = sy.coerceIn(maxOf(tuning.squashMax, AnimEngine.SQ_MIN), minOf(tuning.stretchMax, AnimEngine.SQ_MAX))
        p.squashY *= y; p.squashX /= y
    }
}
