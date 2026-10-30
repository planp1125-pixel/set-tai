package com.combo.monkeycompanion.rig

import com.combo.monkeycompanion.brain.MonkeyState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Extra inputs the poses need besides state and time. */
class AnimContext {
    var anger = 0f            // 0..1 lingering grumpiness
    var dragVx = 0f           // px/s while being dragged (limbs swing against motion)
    var talking = false       // flap the mouth
    var angryWalk = false     // stomping away
    var walkSpeed = 1f
    var playfulFoot = false
    var playfulSide = 1       // which hand/foot (+1 right, -1 left)
    /** Dance routine sequence for the current dance (indices into the move list). */
    var danceMoves = intArrayOf(0, 1, 2)
    /** Where the monkey is: [GROUND] (bottom / free), [TOP] (hanging) or [WALL] (clinging to a side). */
    var edge = GROUND
    /** v3.2: which comedy bit [MonkeyState.GAG] plays. */
    var gag = com.combo.monkeycompanion.brain.Gag.SNEEZE
    var exit = com.combo.monkeycompanion.brain.Exit.POOF
    /** v3.2 superpowers ([MonkeyState.POWER]): the power, its current phase and progress 0..1. */
    var power: com.combo.monkeycompanion.brain.Superpower? = null
    var powerPhase = com.combo.monkeycompanion.brain.PowerPhase.TAKEOFF
    var powerK = 0f
    /** Flight direction in degrees: 0 = straight up, 90 = level (forward), 180 = straight down. */
    var heading = 0f
    /** 0..1 flight speed (cape wind, lean). */
    var speed = 0f
    /** The routine crashed (sooty face / dazed for the rest of it). */
    var crashed = false
    /** Seconds since the last palm ring, and which palm fired it. */
    var sinceRing = 9f
    var ringLeft = false
    companion object { const val GROUND = 0; const val TOP = 1; const val WALL = 2 }
}

/** Procedural animation: fills a target [Pose] for a state at a given time. */
object Animations {
    private const val TAU = (2 * PI).toFloat()

    /**
     * @param t  global time in seconds (continuous, for idle cycles)
     * @param st seconds since the current state started
     */
    fun fill(p: Pose, s: MonkeyState, t: Float, st: Float, c: AnimContext) {
        p.reset()
        val e = c.edge
        when (s) {
            MonkeyState.IDLE -> when (e) { AnimContext.TOP -> hangIdle(p, t); AnimContext.WALL -> cling(p, t); else -> idle(p, t) }
            MonkeyState.WALK -> when (e) { AnimContext.TOP -> hangMove(p, t, c); AnimContext.WALL -> climb(p, t, c); else -> walk(p, t, c) }
            MonkeyState.DANCE -> when (e) {
                AnimContext.TOP -> { hangIdle(p, t * 2.2f); p.hangSwing *= 2f; p.notes = 1f; p.eyesHappy = 1f; p.mouthOpen = 0.4f }
                AnimContext.WALL -> { climb(p, t * 1.8f, c); p.backView = 0f; p.notes = 1f; p.eyesHappy = 1f; p.mouthOpen = 0.4f }
                else -> dance(p, st, c)
            }
            MonkeyState.FALLING -> falling(p, t)
            MonkeyState.SLEEP -> sleep(p, t)
            MonkeyState.WAKE -> wake(p, t, st)
            MonkeyState.ENTRANCE -> entrance(p, t, st)
            MonkeyState.PEEK -> peek(p, t, st)
            MonkeyState.HIDDEN -> idle(p, t)
            MonkeyState.SIT -> sit(p, t)
            MonkeyState.JUMP -> jump(p, st)
            MonkeyState.DASH -> dash(p, t)
            MonkeyState.SAD -> sad(p, t)
            MonkeyState.WAVE -> waveHello(p, t, st)
            MonkeyState.DRAGGED -> dragged(p, t, c)
            MonkeyState.LANDING -> landing(p, st)
            MonkeyState.GIGGLE -> giggle(p, st)
            MonkeyState.HAPPY_PAT -> happyPat(p, t, st)
            MonkeyState.STARTLED -> startled(p, st)
            MonkeyState.ANGRY -> angry(p, st)
            MonkeyState.PLAYFUL -> playful(p, t, st, c)
            MonkeyState.THINKING -> thinking(p, t, st)
            MonkeyState.TALKING -> talking(p, t, st)
            MonkeyState.GAG -> gag(p, t, st, c.gag)
            MonkeyState.EXIT -> exit(p, t, st, c.exit)
            MonkeyState.POWER -> power(p, t, st, c)
        }
        // Reactions while hanging / clinging: keep holding on with one hand and the feet.
        if (e != AnimContext.GROUND && s != MonkeyState.IDLE && s != MonkeyState.WALK && s != MonkeyState.DANCE &&
            s != MonkeyState.DRAGGED && s != MonkeyState.FALLING && s != MonkeyState.SLEEP && s != MonkeyState.WAKE
        ) grip(p, t, e)
        if (e != AnimContext.GROUND || s == MonkeyState.FALLING || s == MonkeyState.DRAGGED) p.shadow = 0f
        // Lingering grumpiness colours every other state.
        if (s != MonkeyState.ANGRY && c.anger > 0.01f &&
            s != MonkeyState.HAPPY_PAT && s != MonkeyState.GIGGLE
        ) {
            p.anger = max(p.anger, c.anger * 0.8f)
            p.browAngle = max(p.browAngle, 24f * c.anger)
            p.mouthSmile = min(p.mouthSmile, 0.6f - 1.4f * c.anger)
        }
        if ((c.talking || s == MonkeyState.TALKING) && s != MonkeyState.GIGGLE && !(s == MonkeyState.GAG && c.gag == com.combo.monkeycompanion.brain.Gag.FAKE_SLEEP) && s != MonkeyState.DRAGGED && s != MonkeyState.STARTLED) {
            p.mouthOpen = max(p.mouthOpen, 0.15f + 0.45f * abs(sin(t * 15f)))
        }
    }

    // ---------------- edges ----------------

    /** Hanging from the top edge like a branch: both hands up, body dangling and swinging, legs kicking. */
    private fun hangIdle(p: Pose, t: Float) {
        val sw = sin(t * 1.7f)
        p.hangSwing = 9f * sw
        p.armStretch = 2.25f; p.headY = 12f
        p.armLRaise = 168f; p.armRRaise = 168f
        p.armLElbow = 4f; p.armRElbow = 4f
        p.bodyRot = -3f * sw
        p.squashY = 1.04f; p.squashX = 0.97f   // stretched by gravity
        p.legLSwing = 14f * sin(t * 3.1f) - 6f
        p.legRSwing = 14f * sin(t * 3.1f + 2.2f) - 6f
        p.legLLift = 3f * max(0f, sin(t * 3.1f)); p.legRLift = 3f * max(0f, sin(t * 3.1f + 2.2f))
        p.tailRot = 60f + 18f * sin(t * 1.3f); p.tailSway = sin(t * 1.3f)
        p.headRot = 4f * sin(t * 0.9f)
        p.lookX = 2.5f * sin(t * 0.4f); p.lookY = 1.5f
        p.mouthSmile = 0.8f
        // Every ~7 s: a cheeky one-handed swing with a wave.
        val cyc = t % 7f
        if (cyc > 4.6f) {
            val k = sin(((cyc - 4.6f) / 2.4f) * PI.toFloat())
            p.armRRaise = 168f - 95f * k
            p.armRElbow = 4f - 50f * k * (0.6f + 0.4f * sin(t * 12f))
            p.hangSwing = 9f * sw + 10f * k
            p.mouthOpen = 0.35f * k; p.eyesHappy = k
        }
    }

    /** Hand-over-hand along the top edge. */
    private fun hangMove(p: Pose, t: Float, c: AnimContext) {
        val ph = t * 5f * c.walkSpeed
        val s = sin(ph)
        p.hangSwing = 7f * s
        p.armStretch = 2.25f; p.headY = 12f
        p.armLRaise = 160f + 14f * s; p.armRRaise = 160f - 14f * s
        p.armLElbow = -10f * max(0f, -s); p.armRElbow = -10f * max(0f, s)
        p.squashY = 1.03f
        p.legLSwing = 18f * s; p.legRSwing = -18f * s
        p.tailRot = 50f + 15f * s; p.tailSway = s
        p.lookX = 4f; p.lookY = 1f
        p.mouthSmile = 0.9f
    }

    /** Clinging to a side wall (seen from behind), now and then looking back at the user. */
    private fun cling(p: Pose, t: Float) {
        val b = sin(t * 1.6f)
        p.backView = 1f
        p.armLRaise = 150f; p.armLElbow = -25f
        p.armRRaise = 128f; p.armRElbow = -35f
        p.legLSwing = 28f; p.legRSwing = 22f; p.legLLift = 7f; p.legRLift = 3f
        p.bodyRot = -6f; p.bodyY = -2f * b
        p.tailRot = 30f + 12f * b; p.tailSway = b
        // Look back over the shoulder every ~6 s.
        val cyc = t % 6f
        if (cyc > 4.3f) {
            p.backView = 0f
            p.headRot = 14f; p.lookX = 4f; p.mouthSmile = 0.9f; p.eyesHappy = if (cyc > 5.2f) 1f else 0f
            p.armRRaise = 95f + 20f * sin(t * 10f)  // little wave with the free hand
        }
    }

    /** Climbing a wall like a tree trunk: alternating arm / leg cycle. */
    private fun climb(p: Pose, t: Float, c: AnimContext) {
        val ph = t * 6f * c.walkSpeed
        val s = sin(ph)
        p.backView = 1f
        p.armLRaise = 135f + 30f * s; p.armRRaise = 135f - 30f * s
        p.armLElbow = -30f + 25f * s; p.armRElbow = -30f - 25f * s
        p.legLSwing = 25f - 15f * s; p.legRSwing = 25f + 15f * s
        p.legLLift = 8f * max(0f, -s); p.legRLift = 8f * max(0f, s)
        p.bodyY = -3f * abs(s)
        p.bodyRot = -4f + 5f * s
        p.tailRot = 25f + 15f * s; p.tailSway = s
    }

    /** Reacting (face shown) while still holding on. */
    private fun grip(p: Pose, t: Float, e: Int) {
        p.backView = 0f
        p.bodyY = min(0f, p.bodyY) * 0.3f
        if (e == AnimContext.TOP) {
            p.armStretch = 2.25f; p.headY = max(p.headY, 12f)
            p.armLRaise = 168f; p.armLElbow = 4f
            p.armRRaise = max(p.armRRaise, 120f)
            p.legLSwing = 10f * sin(t * 3f); p.legRSwing = -10f * sin(t * 3f)
            p.legLLift = 0f; p.legRLift = 0f
            p.hangSwing = 4f * sin(t * 1.7f) + p.bodyRot * 0.5f
            p.bodyRot = 0f
        } else {
            p.armLRaise = 150f; p.armLElbow = -25f
            p.legLSwing = 28f; p.legLLift = 7f
            p.legRSwing = max(p.legRSwing, 15f)
        }
    }

    private fun falling(p: Pose, t: Float) {
        val w = sin(t * 16f)
        p.armLRaise = 150f + 20f * w; p.armRRaise = 150f - 20f * w
        p.armLElbow = 10f; p.armRElbow = 10f
        p.legLSwing = 20f * sin(t * 13f); p.legRSwing = -20f * sin(t * 13f)
        p.legLLift = 5f; p.legRLift = 5f
        p.eyeOpen = 1.35f; p.mouthOpen = 0.9f; p.mouthSmile = -0.2f
        p.browY = -5f; p.browAngle = -14f
        p.tailRot = 70f + 10f * w; p.tailSway = w
        p.squashY = 1.06f; p.squashX = 0.95f
    }

    /** Curled up asleep: sitting, head drooped, arms hugging knees, slow snoring breath. */
    private fun sleep(p: Pose, t: Float) {
        val br = sin(t * 1.4f)
        p.bodyY = 13f
        p.squashY = 0.93f + 0.025f * br; p.squashX = 1.03f - 0.015f * br
        p.legLSwing = 72f; p.legRSwing = 72f; p.legLLift = -2f; p.legRLift = -2f
        p.armLRaise = -14f; p.armLElbow = -78f
        p.armRRaise = -14f; p.armRElbow = -78f
        p.headRot = 16f + 2f * br; p.headY = 6f
        p.eyeOpen = 0f; p.eyesHappy = 0f
        p.mouthSmile = 0.25f
        p.mouthOpen = 0.08f + 0.1f * max(0f, br)
        p.browY = 1.5f
        p.tailRot = -30f; p.tailSway = 1f
        p.blush = 0.4f
        p.zzz = 1f
    }

    // ---------------- v3 ----------------

    /** Entrance phase boundaries (seconds). The view moves the body; these are the poses. */
    object Ent {
        const val DUCK = 0.35f; const val HIDDEN = 0.8f; const val PEEK = 2.6f; const val GRIP = 3.15f
        const val PULL = 4.05f; const val HOP = 4.6f; const val STRETCH = 6.0f; const val CRAZY = 7.1f; const val END = 8.4f
    }

    private fun peek(p: Pose, t: Float, st: Float) {
        idle(p, t)
        // Eyes darting left / right, ears twitching, a curious little head tilt.
        val step = (st / 0.45f).toInt()
        p.lookX = if (step % 2 == 0) -4f else 4f
        p.lookY = -0.5f
        p.headRot = (if (step % 2 == 0) -6f else 6f) + 2f * sin(t * 3f)
        p.earWiggle = if ((st * 3.3f) % 1f < 0.18f) 1f else 0f
        p.browY = -3f; p.browAngle = -6f
        p.eyeOpen = 1.15f
        p.mouthSmile = 0.3f
        p.armLRaise = 10f; p.armRRaise = 10f
    }

    private fun entrance(p: Pose, t: Float, st: Float) {
        when {
            st < Ent.HIDDEN -> { idle(p, t); p.eyeOpen = 0.2f }
            st < Ent.PEEK -> peek(p, t, st - Ent.HIDDEN)
            st < Ent.GRIP -> { // hands come up to grip the edge (the view solves the arms with IK)
                idle(p, t); p.eyeOpen = 1.1f; p.mouthSmile = 0.7f; p.lookX = 0f
                p.armLRaise = 160f; p.armRRaise = 160f
            }
            st < Ent.PULL -> { // pull up: effort face, stretched body
                idle(p, t)
                p.squashY = 1.05f; p.squashX = 0.97f
                p.eyeOpen = 0.55f; p.browAngle = 12f; p.mouthSmile = -0.3f; p.mouthOpen = 0.15f
                p.legLLift = 6f * sin(st * 14f).coerceAtLeast(0f); p.legRLift = 6f * (-sin(st * 14f)).coerceAtLeast(0f)
                p.legLSwing = 12f * sin(st * 14f); p.legRSwing = -12f * sin(st * 14f)
            }
            st < Ent.HOP -> { // hop up and over
                val k = (st - Ent.PULL) / (Ent.HOP - Ent.PULL)
                p.legLLift = 10f * sin(k * PI.toFloat()); p.legRLift = p.legLLift
                p.armLRaise = 120f - 90f * k; p.armRRaise = 120f - 90f * k
                p.squashY = 1f + 0.08f * sin(k * PI.toFloat()); p.mouthOpen = 0.4f; p.eyeOpen = 1.2f
                if (k > 0.8f) { p.squashY = 0.88f; p.squashX = 1.08f }  // landing squash
            }
            st < Ent.STRETCH -> wake(p, t, (st - Ent.HOP) / (Ent.STRETCH - Ent.HOP) * 1.7f)          // yawn + stretch
            st < Ent.CRAZY -> wake(p, t, 2.8f + (st - Ent.STRETCH) / (Ent.CRAZY - Ent.STRETCH) * 1.9f)  // jump + shake
            else -> wake(p, t, 4.7f + (st - Ent.CRAZY))                                                // wave
        }
    }

    private fun sit(p: Pose, t: Float) {
        idle(p, t)
        p.bodyY = 12f
        p.squashY = 0.95f
        p.legLSwing = 8f + 12f * sin(t * 2.1f); p.legRSwing = 8f - 12f * sin(t * 2.1f + 0.7f)
        p.armLRaise = 4f; p.armRRaise = 4f; p.armLElbow = -30f; p.armRElbow = -30f
        p.tailRot = -20f + 10f * sin(t)
        p.mouthSmile = 0.8f
    }

    private fun jump(p: Pose, st: Float) {
        when {
            st < 0.14f -> { p.squashY = 0.84f; p.squashX = 1.1f; p.armLRaise = 40f; p.armRRaise = 40f }
            else -> {
                p.squashY = 1.1f; p.squashX = 0.93f
                p.armLRaise = 165f; p.armRRaise = 165f; p.armLElbow = 0f; p.armRElbow = 0f
                p.legLLift = 9f; p.legRLift = 9f; p.legLSwing = -10f; p.legRSwing = -10f
                p.mouthOpen = 0.6f; p.eyesHappy = 1f; p.mouthSmile = 1f
            }
        }
        p.tailRot = 50f
    }

    private fun dash(p: Pose, t: Float) {
        val s = sin(t * 22f)
        p.bodyRot = 16f; p.headRot = 8f
        p.legLSwing = 30f * s; p.legRSwing = -30f * s; p.legLLift = 6f * max(0f, s); p.legRLift = 6f * max(0f, -s)
        p.armLRaise = 40f - 35f * s; p.armRRaise = 40f + 35f * s; p.armLElbow = -50f; p.armRElbow = -50f
        p.eyeOpen = 1.2f; p.mouthOpen = 0.5f; p.mouthSmile = 0.6f
        p.tailRot = 70f; p.tailSway = s
    }

    private fun sad(p: Pose, t: Float) {
        idle(p, t)
        p.browAngle = -20f; p.browY = 1f
        p.mouthSmile = -0.8f; p.lookY = 3f; p.eyeOpen = 0.8f
        p.headRot = 6f; p.headY = 3f
        p.armLRaise = 6f; p.armRRaise = 6f
        p.tailRot = -40f; p.squashY = 0.97f
    }

    private fun waveHello(p: Pose, t: Float, st: Float) {
        idle(p, t)
        p.armRRaise = 150f; p.armRElbow = -30f + 28f * sin(st * 11f)
        p.mouthSmile = 1f; p.mouthOpen = 0.35f; p.eyesHappy = 0.6f
    }

    /** Wake-up routine. Total [WAKE_SECONDS]. */
    const val WAKE_SECONDS = 6.2f
    private fun wake(p: Pose, t: Float, st: Float) {
        when {
            st < 1.7f -> { // big yawn + stretch
                val k = sin((st / 1.7f) * PI.toFloat())
                idle(p, t)
                p.armLRaise = 14f + 150f * k; p.armRRaise = 14f + 150f * k
                p.armLElbow = -20f * k; p.armRElbow = -20f * k
                p.squashY = 1f + 0.08f * k; p.squashX = 1f - 0.05f * k
                p.bodyY = -3f * k
                p.eyeOpen = 0.15f * (1f - k); p.mouthOpen = 1.05f * k; p.mouthSmile = 0f
                p.headRot = -6f * k; p.browY = -2f * k
            }
            st < 2.8f -> { // rub eyes
                idle(p, t)
                val r = sin((st - 1.7f) * 22f)
                p.armLRaise = 128f + 8f * r; p.armLElbow = -118f
                p.armRRaise = 128f - 8f * r; p.armRElbow = -118f
                p.eyeOpen = 0.25f; p.mouthSmile = 0.1f; p.headRot = 3f * r
            }
            st < 3.4f -> { // startled jump: wait, where am I?!
                val k = (st - 2.8f) / 0.6f
                startled(p, k * 0.6f)
                p.bodyY = -34f * sin(k * PI.toFloat())
                p.eyeOpen = 1.45f
            }
            st < 4.7f -> { // wild shake / spin (the controller flips facing)
                val k = st - 3.4f
                p.bodyRot = 14f * sin(k * 28f)
                p.headRot = 18f * sin(k * 31f)
                p.armLRaise = 100f + 50f * sin(k * 20f); p.armRRaise = 100f - 50f * sin(k * 20f)
                p.legLSwing = 18f * sin(k * 24f); p.legRSwing = -18f * sin(k * 24f)
                p.eyesHappy = 1f; p.mouthOpen = 0.7f; p.mouthSmile = 1f
                p.tailRot = 40f * sin(k * 15f); p.tailSway = sin(k * 15f)
            }
            else -> { // happy wave
                idle(p, t)
                val k = st - 4.7f
                p.armRRaise = 150f; p.armRElbow = -30f + 28f * sin(k * 11f)
                p.mouthSmile = 1f; p.mouthOpen = 0.35f; p.eyesHappy = 0.6f
                p.hearts = 0.7f
            }
        }
    }

    private fun idle(p: Pose, t: Float) {
        val b = sin(t * 2.4f)
        p.squashY = 1f + 0.018f * b
        p.squashX = 1f - 0.012f * b
        p.headRot = 3f * sin(t * 0.8f)
        p.armLRaise = 14f + 3f * b
        p.armRRaise = 14f + 3f * b
        p.tailRot = 8f * sin(t * 1.5f)
        p.tailSway = sin(t * 1.5f + 1f)
        p.lookX = 2.5f * sin(t * 0.37f)
        p.lookY = 0.8f * sin(t * 0.23f)
    }

    private fun walk(p: Pose, t: Float, c: AnimContext) {
        val ph = t * 9f * c.walkSpeed
        val s = sin(ph)
        p.legLSwing = 8f * s
        p.legRSwing = -8f * s
        p.legLLift = max(0f, s) * 7f
        p.legRLift = max(0f, -s) * 7f
        p.bodyY = -abs(s) * 3f
        p.bodyRot = 4f * s
        p.headRot = -3f * s
        p.armLRaise = 18f + 16f * s
        p.armRRaise = 18f - 16f * s
        p.tailRot = 10f + 10f * sin(ph * 0.5f)
        p.tailSway = sin(ph)
        p.lookX = 4f
        if (c.angryWalk) {
            p.legLLift *= 1.4f; p.legRLift *= 1.4f
            p.armLRaise = 30f; p.armRRaise = 30f
            p.armLElbow = -40f; p.armRElbow = -40f
            p.browAngle = 28f; p.browY = 2f
            p.mouthSmile = -0.9f
            p.anger = 1f
        }
    }

    /** Seconds each dance move lasts before switching to the next in the routine. */
    const val MOVE_SECONDS = 2.4f
    const val MOVE_COUNT = 7
    const val MOVE_SPIN = 6

    fun danceMove(st: Float, c: AnimContext): Int {
        val m = c.danceMoves
        return if (m.isEmpty()) 0 else m[(st / MOVE_SECONDS).toInt() % m.size]
    }

    private fun dance(p: Pose, st: Float, c: AnimContext) {
        val move = danceMove(st, c)
        val bounce = abs(sin(st * PI.toFloat() * 2.2f))
        p.bodyY = -12f * bounce
        p.squashY = 1f - 0.07f * (1f - bounce)
        p.squashX = 1f + 0.05f * (1f - bounce)
        p.notes = 1f
        p.mouthOpen = 0.45f
        p.mouthSmile = 1f
        val sw = sin(st * TAU * 1.1f)
        p.legLLift = max(0f, sw) * 8f
        p.legRLift = max(0f, -sw) * 8f
        p.tailRot = 20f + 20f * sw
        p.tailSway = sw
        when (move) {
            0 -> { // hands in the air, wave
                p.armLRaise = 150f + 20f * sin(st * 8f)
                p.armRRaise = 150f + 20f * sin(st * 8f + PI.toFloat())
                p.armLElbow = 20f; p.armRElbow = 20f
                p.bodyRot = 8f * sw
                p.headRot = 10f * sw
            }
            1 -> { // hands on hips, hip sway
                p.armLRaise = 45f; p.armRRaise = 45f
                p.armLElbow = -115f; p.armRElbow = -115f
                p.bodyX = 8f * sw
                p.bodyRot = -10f * sw
                p.headRot = 12f * sw
                p.legLSwing = 10f * sw; p.legRSwing = -10f * sw
                p.eyesHappy = 1f
            }
            3 -> { // the twist: crouch, swing hips and arms
                val tw = sin(st * TAU * 1.6f)
                p.bodyY = 5f - 4f * bounce
                p.bodyRot = 14f * tw
                p.headRot = -10f * tw
                p.armLRaise = 70f + 25f * tw; p.armRRaise = 70f - 25f * tw
                p.armLElbow = -90f; p.armRElbow = -90f
                p.legLSwing = 14f * tw; p.legRSwing = -14f * tw
                p.legLLift = 0f; p.legRLift = 0f
                p.squashY = 0.95f; p.squashX = 1.05f
                p.eyesHappy = 1f
            }
            4 -> { // robot: snaps between stiff poses
                val step = (st * 4f).toInt() % 4
                val armsL = floatArrayOf(90f, 90f, 20f, 160f)
                val armsR = floatArrayOf(20f, 90f, 90f, 160f)
                val elbL = floatArrayOf(-90f, 0f, 0f, 0f)
                val elbR = floatArrayOf(0f, 90f, -90f, 0f)
                p.armLRaise = armsL[step]; p.armRRaise = armsR[step]
                p.armLElbow = elbL[step]; p.armRElbow = elbR[step]
                p.headRot = if (step % 2 == 0) 12f else -12f
                p.bodyRot = 0f
                p.bodyY = -3f * bounce
                p.legLLift = 0f; p.legRLift = 0f
                p.mouthOpen = 0f; p.mouthSmile = 0.2f
                p.eyeOpen = 1.1f
            }
            5 -> { // jump & clap overhead
                val j = abs(sin(st * PI.toFloat() * 1.6f))
                p.bodyY = -30f * j
                p.squashY = 1f - 0.1f * (1f - j); p.squashX = 1f + 0.08f * (1f - j)
                val clap = abs(sin(st * PI.toFloat() * 3.2f))
                p.armLRaise = 165f; p.armRRaise = 165f
                p.armLElbow = -35f * clap; p.armRElbow = -35f * clap
                p.legLSwing = 18f * j; p.legRSwing = 18f * j
                p.legLLift = 0f; p.legRLift = 0f
                p.mouthOpen = 0.7f
                p.hearts = 0.6f
            }
            MOVE_SPIN -> { // spin (the brain flips facing quickly); arms out wide
                p.armLRaise = 95f + 10f * sw; p.armRRaise = 95f - 10f * sw
                p.armLElbow = 15f; p.armRElbow = 15f
                p.bodyY = -6f * bounce
                p.eyesHappy = 1f
                p.mouthOpen = 0.6f
            }
            else -> { // disco point, switching arms
                val right = sin(st * PI.toFloat() * 2f) > 0f
                p.armRRaise = if (right) 160f + 8f * sin(st * 12f) else 20f
                p.armRElbow = if (right) 0f else -60f
                p.armLRaise = if (!right) 160f + 8f * sin(st * 12f) else 20f
                p.armLElbow = if (!right) 0f else -60f
                p.bodyRot = if (right) -8f else 8f
                p.headRot = if (right) 10f else -10f
                p.eyesHappy = 0.8f
            }
        }
    }

    private fun thinking(p: Pose, t: Float, st: Float) {
        idle(p, t)
        // Right hand scratches the head.
        p.armRRaise = 150f
        p.armRElbow = -105f + 12f * sin(st * 18f)
        p.armLRaise = 20f; p.armLElbow = -40f
        p.headRot = -7f + 2f * sin(st * 2f)
        p.lookX = 2.5f; p.lookY = -2.5f
        p.browAngle = -10f; p.browY = -2f
        p.mouthSmile = 0.05f; p.mouthOpen = 0f
        p.tailRot = 15f + 15f * sin(st * 3f)
    }

    private fun talking(p: Pose, t: Float, st: Float) {
        idle(p, t)
        // Explaining gestures with one hand, little head bobs.
        val g = sin(st * 3.1f)
        p.armRRaise = 55f + 25f * g
        p.armRElbow = -60f + 20f * sin(st * 4.3f)
        p.armLRaise = 18f + 6f * sin(st * 2.2f)
        p.headRot = 5f * sin(st * 1.7f)
        p.mouthSmile = 0.7f
        p.lookX = 1.5f
    }

    private fun dragged(p: Pose, t: Float, c: AnimContext) {
        val w = sin(t * 7f)
        val lean = (-c.dragVx * 0.015f).coerceIn(-22f, 22f)
        p.armLRaise = 155f + 10f * w
        p.armRRaise = 155f - 10f * w
        p.armLElbow = 0f; p.armRElbow = 0f
        p.legLSwing = (8f + 10f * sin(t * 5f) - lean * 0.6f).coerceIn(-30f, 40f)
        p.legRSwing = (8f - 10f * sin(t * 5f) + lean * 0.6f).coerceIn(-30f, 40f)
        p.bodyRot = lean
        p.headRot = lean * 0.5f
        p.bodyY = -4f
        p.eyeOpen = 1.25f
        p.mouthOpen = 0.8f
        p.mouthSmile = 0f
        p.browY = -4f
        p.browAngle = -10f
        p.tailRot = -55f + 10f * sin(t * 4f)
        p.tailSway = sin(t * 4f)
    }

    private fun landing(p: Pose, st: Float) {
        val k = (st / 0.4f).coerceIn(0f, 1f)
        val s = sin(PI.toFloat() * k)
        p.squashY = 1f - 0.16f * s
        p.squashX = 1f + 0.12f * s
        p.armLRaise = 14f + 50f * (1f - k)
        p.armRRaise = 14f + 50f * (1f - k)
        p.mouthOpen = 0.3f * (1f - k)
    }

    private fun giggle(p: Pose, st: Float) {
        val shake = sin(st * 30f)
        p.bodyRot = 5f * shake
        p.bodyY = -3f * abs(sin(st * 14f))
        p.headRot = 6f * sin(st * 15f)
        p.eyesHappy = 1f
        p.mouthOpen = 0.6f + 0.25f * abs(sin(st * 14f))
        p.mouthSmile = 1f
        p.blush = 1f
        p.armLRaise = 22f; p.armRRaise = 22f
        p.armLElbow = -105f; p.armRElbow = -105f
        p.legLSwing = 4f * shake; p.legRSwing = -4f * shake
        p.tailRot = 20f * sin(st * 12f)
        p.tailSway = sin(st * 12f)
    }

    private fun happyPat(p: Pose, t: Float, st: Float) {
        val b = sin(t * 2.4f)
        p.squashY = 1f + 0.02f * b - 0.03f
        p.squashX = 1f + 0.02f
        p.headRot = 9f * sin(st * 3f)
        p.eyesHappy = 1f
        p.mouthSmile = 1f
        p.mouthOpen = 0.15f
        p.blush = 0.9f
        p.hearts = 1f
        p.armLRaise = 28f; p.armRRaise = 28f
        p.armLElbow = -30f; p.armRElbow = -30f
        p.tailRot = 20f + 25f * sin(st * 10f)
        p.tailSway = sin(st * 10f)
    }

    private fun startled(p: Pose, st: Float) {
        val k = (st / 0.55f).coerceIn(0f, 1f)
        p.bodyY = -44f * sin(PI.toFloat() * k)
        if (k >= 1f) {
            val k2 = ((st - 0.55f) / 0.3f).coerceIn(0f, 1f)
            val s = sin(PI.toFloat() * k2)
            p.squashY = 1f - 0.14f * s
            p.squashX = 1f + 0.1f * s
        }
        p.armLRaise = 135f; p.armRRaise = 135f
        p.armLElbow = 10f; p.armRElbow = 10f
        p.legLSwing = 22f * sin(PI.toFloat() * k); p.legRSwing = 22f * sin(PI.toFloat() * k)
        p.eyeOpen = 1.35f
        p.mouthOpen = 1f
        p.mouthSmile = -0.2f
        p.browY = -5f
        p.browAngle = -12f
        p.tailRot = 50f
        p.tailSway = 1f
        p.lookX = -3f
    }

    private fun angry(p: Pose, st: Float) {
        val stomp = sin(st * 12f)
        p.legLLift = max(0f, stomp) * 9f
        p.legRLift = max(0f, -stomp) * 9f
        p.squashY = 1f - 0.04f * abs(cos(st * 12f))
        p.squashX = 1f + 0.03f * abs(cos(st * 12f))
        p.bodyRot = 3f * sin(st * 24f)
        p.headRot = 4f * sin(st * 20f)
        p.armLRaise = 35f; p.armRRaise = 35f
        p.armLElbow = -45f; p.armRElbow = -45f
        p.browAngle = 28f
        p.browY = 2f
        p.mouthSmile = -1f
        p.mouthOpen = 0f
        p.anger = 1f
        p.tailRot = -10f + 20f * sin(st * 20f)
        p.tailSway = sin(st * 20f)
    }

    private fun playful(p: Pose, t: Float, st: Float, c: AnimContext) {
        idle(p, t)
        p.eyesHappy = 0.6f
        p.mouthOpen = 0.35f
        p.mouthSmile = 1f
        p.blush = 0.5f
        p.headRot = 8f * c.playfulSide
        p.tailRot = 15f + 20f * sin(st * 10f)
        if (c.playfulFoot) {
            val kick = abs(sin(st * 7f))
            if (c.playfulSide > 0) { p.legRSwing = 35f * kick; p.legRLift = 8f * kick }
            else { p.legLSwing = 35f * kick; p.legLLift = 8f * kick }
            p.armLRaise = 40f; p.armRRaise = 40f
        } else {
            val wave = 140f + 25f * sin(st * 12f)
            if (c.playfulSide > 0) { p.armRRaise = wave; p.armRElbow = 20f * sin(st * 12f) }
            else { p.armLRaise = wave; p.armLElbow = 20f * sin(st * 12f) }
        }
    }

    // ---------------- v3.2 comedy bits ----------------

    private val strollCtx = AnimContext()
    private fun seg(st: Float, a: Float, b: Float) = ((st - a) / (b - a)).coerceIn(0f, 1f)
    private fun bump(st: Float, a: Float, b: Float) = if (st < a || st > b) 0f else sin(PI.toFloat() * (st - a) / (b - a))
    /** Fade a prop in over the first 0.25 s and out over the last 0.3 s of [len]. */
    private fun env(st: Float, len: Float) = min(1f, min(st / 0.25f, (len - st) / 0.3f)).coerceIn(0f, 1f)

    // ---------------- v3.2 superpowers ----------------

    private fun power(p: Pose, t: Float, st: Float, c: AnimContext) {
        val pw = c.power ?: return idle(p, t)
        val jets = pw.has(com.combo.monkeycompanion.brain.PowerKit.FOOT_JETS)
        val caped = pw.has(com.combo.monkeycompanion.brain.PowerKit.CAPE)
        p.propT = st
        p.cape = if (caped) 1f else 0f
        p.badge = if (pw.has(com.combo.monkeycompanion.brain.PowerKit.BADGE)) 1f else 0f
        p.capeWind = c.speed
        p.shadow = 0f
        p.tailRot = 25f; p.tailSway = sin(t * 6f)
        val k = c.powerK
        p.staff = if (pw.has(com.combo.monkeycompanion.brain.PowerKit.STAFF)) 1f else 0f
        p.ninja = if (pw.has(com.combo.monkeycompanion.brain.PowerKit.NINJA)) 1f else 0f
        p.hat = if (pw.has(com.combo.monkeycompanion.brain.PowerKit.HAT)) 1f else 0f
        if (showOff(p, t, st, c, k)) return
        when (c.powerPhase) {
            com.combo.monkeycompanion.brain.PowerPhase.TAKEOFF -> {
                val crouch = if (k < 0.35f) smooth(k / 0.35f) else 1f - smooth((k - 0.35f) / 0.35f)
                p.squashY = 1f - 0.16f * crouch; p.squashX = 1f + 0.08f * crouch
                p.legLSwing = 18f * crouch; p.legRSwing = 18f * crouch; p.legLLift = 9f * crouch; p.legRLift = 9f * crouch
                p.shadow = 1f - k
                p.browAngle = 10f; p.mouthSmile = 0.9f; p.eyeOpen = 1.1f
                if (jets) {
                    p.jets = smooth(k / 0.45f)
                    p.armLRaise = 22f + 10f * crouch; p.armRRaise = 22f + 10f * crouch; p.armLElbow = -20f; p.armRElbow = -20f
                    p.bodyX = 1.5f * sin(t * 60f) * (1f - k)       // rumble
                } else {
                    val up = smooth((k - 0.3f) / 0.5f)
                    p.armRRaise = 20f + 152f * up; p.armRElbow = -40f * (1f - up)
                    p.armLRaise = 20f - 6f * up; p.armLElbow = -30f
                    p.capeWind = 0.3f + 0.5f * up
                }
            }
            com.combo.monkeycompanion.brain.PowerPhase.FLY, com.combo.monkeycompanion.brain.PowerPhase.SPUTTER -> flyPose(p, t, c, jets, caped)
            com.combo.monkeycompanion.brain.PowerPhase.CHARGE, com.combo.monkeycompanion.brain.PowerPhase.STRIKE,
            com.combo.monkeycompanion.brain.PowerPhase.TELEPORT, com.combo.monkeycompanion.brain.PowerPhase.CLONES,
            com.combo.monkeycompanion.brain.PowerPhase.GROW, com.combo.monkeycompanion.brain.PowerPhase.SLAM,
            com.combo.monkeycompanion.brain.PowerPhase.SHRINK, com.combo.monkeycompanion.brain.PowerPhase.FROST,
            com.combo.monkeycompanion.brain.PowerPhase.PEEK, com.combo.monkeycompanion.brain.PowerPhase.DODGE,
            com.combo.monkeycompanion.brain.PowerPhase.MAGIC,
            com.combo.monkeycompanion.brain.PowerPhase.LASER, com.combo.monkeycompanion.brain.PowerPhase.BREATH,
            com.combo.monkeycompanion.brain.PowerPhase.LIFT, com.combo.monkeycompanion.brain.PowerPhase.BLUR,
            com.combo.monkeycompanion.brain.PowerPhase.CONFETTI, com.combo.monkeycompanion.brain.PowerPhase.SHIELD,
            com.combo.monkeycompanion.brain.PowerPhase.DROOP -> Unit   // handled by showOff()
            com.combo.monkeycompanion.brain.PowerPhase.FIRE -> {
                // Hover and blast glowing rings from alternate palms (recoil on each shot).
                p.jets = 0.75f + 0.1f * sin(t * 25f)
                p.bodyY = -3f * sin(t * 5f); p.bodyRot = 4f * sin(t * 2.5f)
                p.legLSwing = 6f; p.legRSwing = 6f; p.legLLift = 4f
                val rec = (1f - (c.sinceRing / 0.25f).coerceIn(0f, 1f))
                p.armLRaise = 88f; p.armRRaise = 88f; p.armLElbow = 4f; p.armRElbow = 4f
                if (c.ringLeft) p.armLRaise -= 14f * rec else p.armRRaise -= 14f * rec
                p.bodyX = (if (c.ringLeft) 3f else -3f) * rec
                p.palmGlow = 0.7f + 0.3f * rec
                p.browAngle = 14f; p.browY = 1f; p.mouthOpen = 0.25f + 0.35f * rec; p.mouthSmile = 0.4f
                p.eyeOpen = 0.85f
            }
            com.combo.monkeycompanion.brain.PowerPhase.LAND -> {
                if (k < 0.5f) {
                    // Coming down: legs ready, arms out for balance, jets braking.
                    val d = k / 0.5f
                    p.jets = if (jets) 1f - 0.6f * d else 0f
                    p.armLRaise = 40f + 30f * d; p.armRRaise = if (caped) 172f - 120f * d else 40f + 30f * d
                    p.legLSwing = 8f; p.legRSwing = 8f
                    p.eyeOpen = 1.2f; p.mouthOpen = 0.3f
                    p.capeWind = 0.6f
                    p.shadow = d
                } else {
                    // THUD: big squash, then spring back.
                    val d = (k - 0.5f) / 0.5f
                    val sq = bumpK(d)
                    p.shadow = 1f
                    if (caped) {
                        // Super-landing: one knee down, fist to the floor, head down... then look up.
                        val hold = 1f - smooth((d - 0.65f) / 0.35f)
                        p.bodyY = 16f * hold; p.squashY = 1f - 0.12f * sq
                        p.legLSwing = 48f * hold; p.legLLift = 22f * hold; p.legRSwing = 10f * hold
                        p.armRRaise = 18f * hold + 30f * (1f - hold); p.armRElbow = -10f
                        p.armLRaise = 70f * hold + 30f * (1f - hold); p.armLElbow = -30f
                        p.headRot = 10f * hold; p.lookY = 2f * hold
                        p.capeWind = 0.2f
                    } else {
                        p.squashY = 1f - 0.24f * sq; p.squashX = 1f + 0.16f * sq
                        p.legLSwing = 22f * sq; p.legRSwing = 22f * sq; p.legLLift = 8f * sq; p.legRLift = 8f * sq
                        p.armLRaise = 70f - 30f * d; p.armRRaise = 70f - 30f * d
                        p.eyeOpen = 1f - 0.5f * sq; p.mouthSmile = 0.8f
                        p.jets = 0.2f * (1f - d)
                    }
                }
            }
            com.combo.monkeycompanion.brain.PowerPhase.CRASH -> {
                val hit = com.combo.monkeycompanion.brain.PowerFlight.CRASH_HIT
                val slide = com.combo.monkeycompanion.brain.PowerFlight.CRASH_SLIDE
                if (k < hit) {
                    flyPose(p, t, c, jets, caped)
                    p.eyeOpen = 1.6f; p.mouthOpen = 0.8f; p.mouthSmile = -0.6f; p.browAngle = -14f
                } else if (k < slide) {
                    // SPLAT against the screen edge: flattened, arms and legs spread like a starfish.
                    val d = (k - hit) / (slide - hit)
                    val sq = 1f - smooth(d * 3f) * 0.35f
                    p.squashX = 0.72f + 0.28f * (1f - sq) * 0.6f; p.squashY = 1.12f
                    p.armLRaise = 125f; p.armRRaise = 125f; p.armLElbow = 10f; p.armRElbow = 10f
                    p.legLSwing = 30f; p.legRSwing = 30f
                    p.eyeOpen = 0.2f; p.mouthOpen = 0.5f; p.mouthSmile = -0.3f
                    p.headRot = 6f * sin(t * 9f)
                    p.soot = if (jets) 1f else 0f
                    p.jets = if (jets) 0.15f * abs(sin(t * 31f)) else 0f
                } else {
                    // Slow slide down the edge, then plop on the floor.
                    val d = (k - slide) / (1f - slide)
                    p.squashX = 0.82f + 0.18f * d; p.squashY = 1.08f - 0.08f * d
                    p.armLRaise = 125f - 90f * d; p.armRRaise = 125f - 90f * d
                    p.legLSwing = 30f - 20f * d; p.legRSwing = 30f - 20f * d
                    p.eyeOpen = 0.35f; p.mouthOpen = 0.3f; p.mouthSmile = -0.2f
                    p.headRot = 8f * sin(t * 4f)
                    p.soot = if (jets) 1f else 0f
                    p.shadow = d
                    p.capeWind = 0.1f
                }
            }
            com.combo.monkeycompanion.brain.PowerPhase.POSE -> {
                p.shadow = 1f
                if (showOffPose(p, t, c, pw.id)) Unit
                else if (c.crashed) {
                    // Dazed: coughs, shakes the head, blinks; sooty if the engine blew.
                    p.soot = if (jets) 1f else 0f
                    val cough = bump(c.powerK, 0.1f, 0.3f) + bump(c.powerK, 0.4f, 0.55f)
                    p.headRot = 14f * sin(t * 13f) * (1f - c.powerK)
                    p.eyeOpen = 0.55f + 0.3f * c.powerK; p.browAngle = -10f
                    p.mouthOpen = 0.2f + 0.5f * cough; p.mouthSmile = -0.2f + 0.6f * c.powerK
                    p.armRRaise = 14f + 110f * cough; p.armRElbow = -8f - 100f * cough   // paw to mouth
                    p.squashY = 1f - 0.05f * cough
                    p.earWiggle = sin(t * 20f) * (1f - c.powerK)
                } else if (caped) {
                    // Heroic pose: fists on hips, chest out, chin up, cape rippling.
                    p.armLRaise = 46f; p.armRRaise = 46f; p.armLElbow = -112f; p.armRElbow = -112f
                    p.squashY = 1.04f; p.squashX = 0.98f
                    p.headRot = -5f; p.lookY = -1f; p.lookX = 1.5f
                    p.browAngle = 8f; p.mouthSmile = 1f; p.eyeOpen = 0.9f
                    p.legLSwing = 10f; p.legRSwing = 10f
                    p.capeWind = 0.45f + 0.25f * sin(t * 3.2f)
                } else {
                    p.armRRaise = 150f; p.armRElbow = -20f; p.eyesHappy = 1f; p.mouthOpen = 0.5f
                }
            }
        }
    }

    /** v4 show-off phases. True = handled. */
    private fun showOff(p: Pose, t: Float, st: Float, c: AnimContext, k: Float): Boolean {
        p.shadow = 1f
        when (c.powerPhase) {
            com.combo.monkeycompanion.brain.PowerPhase.CHARGE -> {
                // Staff raised overhead, crackling; fur rising; eyes glowing.
                val up = smooth(k / 0.3f)
                p.armRRaise = 20f + 150f * up; p.armRElbow = -10f * (1f - up)
                p.armLRaise = 50f; p.armLElbow = -60f
                p.staffGlow = (0.3f + 0.7f * k) * (0.75f + 0.25f * sin(t * 40f))
                p.frizz = 0.4f * k
                p.squashY = 1f - 0.06f * up; p.legLSwing = 14f; p.legRSwing = 14f
                p.bodyX = 1.2f * sin(t * 55f) * k
                p.browAngle = 16f; p.eyeOpen = 1.2f; p.mouthOpen = 0.15f; p.mouthSmile = 0.5f
                p.earWiggle = 0.5f * sin(t * 25f) * k
            }
            com.combo.monkeycompanion.brain.PowerPhase.STRIKE -> {
                val thrust = smooth(k / 0.15f)
                p.armRRaise = 170f - 60f * thrust; p.armRElbow = 0f
                p.armLRaise = 40f; p.armLElbow = -40f
                p.staffGlow = if (k < 0.5f) 1f else 1f - (k - 0.5f) * 2f
                p.bodyX = -5f * bumpK(k * 2f)
                p.frizz = 0.4f
                if (c.crashed) {
                    // His own bolt came back: zapped, frizzy, sooty, shaking.
                    p.frizz = 1f; p.soot = 1f
                    p.bodyX = 3f * sin(t * 70f); p.squashY = 0.95f + 0.05f * sin(t * 50f)
                    p.armLRaise = 120f; p.armRRaise = 120f; p.armLElbow = 20f; p.armRElbow = 20f
                    p.eyeOpen = 1.6f; p.mouthOpen = 0.7f; p.mouthSmile = -0.6f; p.browAngle = -12f
                } else { p.browAngle = 18f; p.mouthOpen = 0.6f; p.mouthSmile = 0.2f; p.eyeOpen = 0.9f }
            }
            com.combo.monkeycompanion.brain.PowerPhase.TELEPORT -> {
                // Throw a smoke bomb at the feet, vanish, reappear crouched in a ninja stance.
                p.fade = when {
                    k < 0.3f -> 1f
                    k < 0.48f -> 1f - smooth((k - 0.3f) / 0.18f)
                    k < 0.52f -> 0f
                    k < 0.7f -> smooth((k - 0.52f) / 0.18f)
                    else -> 1f
                }
                p.puff = if (k < 0.5f) smooth((k - 0.2f) / 0.3f) else 1f - smooth((k - 0.5f) / 0.4f)
                p.squashY = 1f - 0.12f; p.legLSwing = 28f; p.legRSwing = 20f; p.legLLift = 10f
                p.armRRaise = if (k < 0.25f) 40f + 300f * k else 70f; p.armRElbow = -100f
                p.armLRaise = 70f; p.armLElbow = -100f       // hand seal
                p.browAngle = 14f; p.eyeOpen = 0.75f; p.mouthSmile = 0.3f
                p.shadow = p.fade
                if (c.crashed && k > 0.6f) { p.soot = 1f; p.eyeOpen = 0.4f; p.mouthOpen = 0.4f; p.headRot = 10f * sin(t * 12f) }
            }
            com.combo.monkeycompanion.brain.PowerPhase.CLONES -> {
                dance(p, st, c)
                p.notes = 0f
                p.clones = smooth(k / 0.12f) * (1f - smooth((k - 0.88f) / 0.12f))
                p.browAngle = 8f
            }
            com.combo.monkeycompanion.brain.PowerPhase.GROW -> {
                val g = smooth(k / 0.85f)
                p.size = 1f + 0.8f * g
                p.anger = g; p.steam = g; p.browAngle = 20f; p.eyeOpen = 0.8f; p.mouthOpen = 0.35f; p.mouthSmile = -0.8f
                p.armLRaise = 30f + 20f * g; p.armRRaise = 30f + 20f * g; p.armLElbow = -95f; p.armRElbow = -95f   // fists
                p.bodyY = -2f * sin(t * 30f) * g; p.squashX = 1f + 0.06f * g
                p.legLSwing = 14f * g; p.legRSwing = 14f * g
            }
            com.combo.monkeycompanion.brain.PowerPhase.SLAM -> {
                p.size = 1.8f; p.anger = 1f; p.steam = 1f; p.browAngle = 22f
                val down = smooth((k - 0.1f) / 0.22f)
                p.armRRaise = 175f - 120f * down; p.armLRaise = 175f - 120f * down
                p.armRElbow = -20f; p.armLElbow = -20f
                val hit = bump(k, 0.3f, 0.55f)
                p.squashY = 1f - 0.12f * hit; p.squashX = 1f + 0.08f * hit
                p.mouthOpen = 0.9f; p.mouthSmile = -0.5f; p.eyeOpen = 0.7f + 0.4f * hit
            }
            com.combo.monkeycompanion.brain.PowerPhase.SHRINK -> {
                p.size = 1.8f - 1.25f * smooth(k / 0.45f)
                val s2 = smooth(k / 0.3f)
                p.anger = 1f - s2; p.steam = 1f - s2
                p.blush = s2; p.browAngle = -12f * s2; p.mouthSmile = -0.15f; p.mouthOpen = 0.1f
                p.armRRaise = 150f * s2; p.armRElbow = -130f * s2         // scratching the head
                p.armLRaise = 20f; p.lookX = -2f; p.lookY = 1.5f; p.headRot = 8f * s2
                p.sweat = s2
            }
            com.combo.monkeycompanion.brain.PowerPhase.FROST -> {
                val f = smooth(k / 0.25f)
                p.frost = f
                p.bodyX = 1.4f * sin(t * 48f) * f
                p.armLRaise = 40f; p.armRRaise = 40f; p.armLElbow = -125f; p.armRElbow = -125f    // hugging himself
                p.mouthOpen = 0.12f + 0.12f * abs(sin(t * 32f)); p.mouthSmile = -0.2f; p.eyeOpen = 0.8f
                p.legLSwing = 4f; p.legRSwing = 4f; p.squashY = 0.97f
                p.earWiggle = 0.3f * sin(t * 40f) * f; p.tailRot = 40f
            }
            com.combo.monkeycompanion.brain.PowerPhase.PEEK -> {
                // The normal-size body vanishes; the host draws a giant eye at the edge.
                p.fade = 1f - smooth(k / 0.07f) + smooth((k - 0.93f) / 0.07f)
                p.puff = bump(k, 0f, 0.15f) + bump(k, 0.88f, 1f)
                p.shadow = p.fade; p.eyesHappy = 1f
            }
            com.combo.monkeycompanion.brain.PowerPhase.DODGE -> {
                // Matrix lean: bend far back in slow motion, arms flowing; after-images trail.
                val lean = smooth((k - 0.1f) / 0.25f) * (1f - smooth((k - 0.8f) / 0.15f))
                p.bodyRot = -58f * lean; p.headRot = 20f * lean
                p.armLRaise = 70f + 30f * sin(t * 2.2f); p.armRRaise = 70f + 30f * sin(t * 2.2f + 1.3f)
                p.armLElbow = -10f; p.armRElbow = -10f
                p.legLSwing = 16f * lean; p.legRSwing = 6f * lean
                p.shades = smooth(k / 0.1f)
                p.clones = lean
                p.eyeOpen = 0.9f; p.mouthSmile = 0.7f; p.tailRot = 30f
                if (c.crashed && k > 0.45f) {
                    p.bodyRot = -20f * (1f - smooth((k - 0.45f) / 0.3f)); p.clones = 0f
                    p.eyeOpen = 0.3f; p.mouthOpen = 0.5f; p.mouthSmile = -0.5f; p.shades = 0.5f
                    p.headRot = 10f * sin(t * 10f)
                }
            }
            com.combo.monkeycompanion.brain.PowerPhase.MAGIC -> {
                // Hat in the left hand, wand taps three times, sparkles, the rabbit pops out with the count.
                p.armLRaise = 55f; p.armLElbow = -50f
                p.wand = 1f
                val tap = if (k < 0.45f) abs(sin(k / 0.45f * PI.toFloat() * 3f)) else 0f
                p.armRRaise = 80f + 30f * tap; p.armRElbow = -30f
                p.rabbit = smooth((k - 0.45f) / 0.15f)
                p.eyeOpen = 1f + 0.3f * p.rabbit; p.mouthOpen = 0.5f * p.rabbit; p.mouthSmile = 1f
                p.browAngle = -6f * p.rabbit; p.lookX = -2f * p.rabbit
                if (c.crashed && k > 0.62f) { p.eyeOpen = 1.5f; p.mouthSmile = -0.4f; p.browAngle = -14f; p.sweat = 1f }
            }
            // ---------------- v4.1 hero phases ----------------
            com.combo.monkeycompanion.brain.PowerPhase.LASER -> {
                // Hovering, fists clenched, eyes blazing; the head sweeps the beams across the screen.
                p.shadow = 0f; p.bodyY = -4f * sin(t * 3f)
                p.laser = smooth(k / 0.12f) * (1f - smooth((k - 0.9f) / 0.1f))
                p.headRot = -18f + 36f * smooth((k - 0.1f) / 0.8f)
                p.armLRaise = 40f; p.armRRaise = 40f; p.armLElbow = -100f; p.armRElbow = -100f
                p.legLSwing = 8f; p.legRLift = 6f
                p.browAngle = 18f; p.mouthSmile = 0.2f; p.eyeOpen = 1f; p.capeWind = 0.35f
            }
            com.combo.monkeycompanion.brain.PowerPhase.BREATH -> {
                // Big inhale (puffed, leaning back), then a long frosty blow.
                val inhale = smooth(k / 0.3f) * (1f - smooth((k - 0.3f) / 0.08f))
                val blow = if (k > 0.32f) 1f - smooth((k - 0.9f) / 0.1f) else 0f
                p.squashX = 1f + 0.1f * inhale; p.bodyRot = -8f * inhale + 6f * blow
                p.blush = inhale; p.mouthOpen = if (blow > 0f) 0.6f else 0.05f; p.mouthSmile = if (blow > 0f) -0.1f else 0.2f
                p.eyeOpen = 1.2f * (1f - blow) + 0.5f * blow; p.breath = blow
                p.armLRaise = 30f + 30f * inhale; p.armRRaise = 30f + 30f * inhale; p.armLElbow = -40f; p.armRElbow = -40f
                p.capeWind = 0.2f + 0.6f * blow
            }
            com.combo.monkeycompanion.brain.PowerPhase.LIFT -> {
                // Squat, strain, then the giant card goes overhead; a little wobble, then a show-off one-hand.
                val up = smooth((k - 0.15f) / 0.3f)
                p.lift = smooth(k / 0.15f)
                p.armLRaise = 40f + 112f * up; p.armRRaise = 40f + 112f * up; p.armLElbow = -30f * (1f - up); p.armRElbow = -30f * (1f - up)
                p.armStretch = 1f + 0.8f * up                  // arms reach over the head to hold the card
                if (k > 0.75f) { p.eyesHappy = 1f; p.bodyX = 2f * sin(t * 3f) }   // show-off sway
                p.squashY = 1f - 0.1f * (1f - up) * p.lift; p.legLSwing = 16f * (1f - up); p.legRSwing = 16f * (1f - up)
                p.bodyX = 1.5f * sin(t * 40f) * (1f - up) * p.lift
                p.browAngle = 14f * (1f - up); p.mouthSmile = if (up > 0.9f) 1f else -0.3f; p.mouthOpen = 0.2f
                p.sweat = (1f - up) * p.lift
            }
            com.combo.monkeycompanion.brain.PowerPhase.BLUR -> {
                // Leaning hard into the run, a blur of legs.
                p.bodyRot = 24f; p.headRot = -10f
                p.legLSwing = 40f * sin(t * 40f); p.legRSwing = -40f * sin(t * 40f); p.legLLift = 8f; p.legRLift = 8f
                p.armLRaise = 70f; p.armRRaise = -10f; p.armLElbow = -10f; p.armRElbow = -10f
                p.capeWind = 1f; p.mouthSmile = 1f; p.eyeOpen = 0.8f; p.browAngle = 10f
            }
            com.combo.monkeycompanion.brain.PowerPhase.CONFETTI -> {
                // Arms crossed, shoulders recoil on each pop; the face goes from cool to "...confetti?"
                val pop = abs(sin(k * PI.toFloat() * 4f))
                p.armLRaise = 50f; p.armRRaise = 50f; p.armLElbow = -120f; p.armRElbow = -120f
                p.squashY = 1f - 0.05f * pop; p.bodyY = 2f * pop
                p.browAngle = if (k < 0.4f) 12f else -10f; p.mouthSmile = if (k < 0.4f) 0.8f else 0.1f
                p.eyeOpen = if (k < 0.4f) 0.8f else 1.3f; p.mouthOpen = if (k < 0.4f) 0f else 0.4f
                p.visor = if (k < 0.4f) 0f else 1f
            }
            com.combo.monkeycompanion.brain.PowerPhase.SHIELD -> {
                // Palm forward, shield bubble grows, holds, pops.
                p.shield = smooth(k / 0.2f) * (1f - smooth((k - 0.88f) / 0.12f))
                p.armRRaise = 90f; p.armRElbow = 0f; p.armLRaise = 46f; p.armLElbow = -112f
                p.palmGlow = 0.5f * p.shield; p.browAngle = 10f; p.mouthSmile = 0.7f
                p.legLSwing = 14f; p.legRSwing = -6f
            }
            com.combo.monkeycompanion.brain.PowerPhase.DROOP -> {
                // Low power: slowly sags, arms dangle, head nods off... then a reboot jolt.
                val sag = smooth(k / 0.6f)
                val jolt = smooth((k - 0.8f) / 0.08f)
                val d = sag * (1f - jolt)
                p.lowPower = d.coerceAtLeast(if (k < 0.8f) 0.3f else 0f)
                p.bodyRot = 10f * d; p.headRot = 22f * d; p.squashY = 1f - 0.1f * d
                p.armLRaise = 10f - 4f * d; p.armRRaise = 10f - 4f * d; p.armLElbow = 10f * d
                p.eyeOpen = 1f - 0.85f * d + 0.4f * jolt; p.mouthOpen = 0.2f * d; p.visor = 1f - d
                p.legLSwing = 12f * d; p.legRSwing = 12f * d
                if (jolt > 0f) { p.squashY = 1f + 0.08f * bumpK(jolt); p.eyesHappy = if (jolt > 0.9f) 1f else 0f }
            }
            else -> return false
        }
        return true
    }

    /** Finishing poses for the v4 powers. True = handled. */
    private fun showOffPose(p: Pose, t: Float, c: AnimContext, id: String): Boolean {
        val k = c.powerK
        when (id) {
            "thunder" -> {
                if (c.crashed) { p.frizz = 1f - 0.6f * k; p.soot = 1f; p.eyeOpen = 0.55f + 0.3f * k; p.mouthSmile = -0.2f + 0.5f * k
                    p.armRRaise = 30f; p.headRot = 10f * sin(t * 12f) * (1f - k) }
                else { p.armRRaise = 100f; p.armRElbow = -60f; p.armLRaise = 46f; p.armLElbow = -112f; p.eyesHappy = 1f; p.mouthSmile = 1f; p.staffGlow = 0.2f }
            }
            "ninja" -> {
                // A small ninja bow, hands together.
                val bow = bumpK(k)
                p.headRot = 22f * bow; p.bodyRot = 8f * bow
                p.armLRaise = 70f; p.armRRaise = 70f; p.armLElbow = -110f; p.armRElbow = -110f
                p.eyesHappy = if (c.crashed) 0f else 1f; p.soot = if (c.crashed) 1f - k else 0f
            }
            "kovam" -> {
                p.size = 0.55f + 0.45f * smooth(k / 0.5f)
                p.blush = 1f - k; p.mouthSmile = 0.3f; p.mouthOpen = 0.2f; p.lookX = 2f; p.eyeOpen = 0.8f
                p.armLRaise = 20f; p.armRRaise = 20f; p.tailRot = 10f
            }
            "ice" -> {
                p.frost = 1f - smooth(k / 0.7f)
                p.armLRaise = 40f; p.armRRaise = 40f; p.armLElbow = -125f; p.armRElbow = -125f
                p.bodyX = 0.8f * sin(t * 40f) * (1f - k); p.mouthSmile = 0.6f; p.eyesHappy = k
            }
            "giant" -> { p.wink = 1f; p.mouthSmile = 1f; p.armRRaise = 150f; p.armRElbow = -20f; p.fade = smooth(k / 0.2f) }
            "freeze" -> {
                if (c.crashed) { p.eyeOpen = 0.45f; p.mouthSmile = -0.2f; p.headRot = 10f * sin(t * 11f) * (1f - k) }
                else { p.shades = 1f; p.armLRaise = 46f; p.armRRaise = 46f; p.armLElbow = -112f; p.armRElbow = -112f; p.mouthSmile = 1f; p.headRot = -6f }
            }
            "magic" -> {
                p.hat = 1f; p.wand = 1f; p.rabbit = 1f - smooth((k - 0.6f) / 0.3f)
                val bow = bumpK(k)
                p.headRot = 18f * bow; p.armLRaise = 55f; p.armLElbow = -50f; p.armRRaise = 60f
                p.eyesHappy = if (c.crashed) 0f else 1f; p.mouthSmile = 1f
            }
            else -> return false
        }
        return true
    }

    private fun flyPose(p: Pose, t: Float, c: AnimContext, jets: Boolean, caped: Boolean) {
        val sputter = c.powerPhase == com.combo.monkeycompanion.brain.PowerPhase.SPUTTER
        p.shadow = 0f
        if (caped) {
            // Fist-first: body along the flight path, front fist forward, back arm along the side.
            p.bodyRot = c.heading.coerceIn(-10f, 105f)
            p.armRRaise = 172f; p.armRElbow = 0f
            p.armLRaise = 10f; p.armLElbow = -6f
            p.legLSwing = 4f; p.legRSwing = 2f; p.legRLift = 9f
            p.headRot = -p.bodyRot * 0.35f
            p.browAngle = 12f; p.mouthSmile = 1f; p.eyeOpen = 0.95f
            p.earWiggle = 0.6f * sin(t * 28f) * c.speed
            p.capeWind = 0.35f + 0.65f * c.speed
        } else {
            // Jet boots: lean into the turn, arms back like stabilisers, ears flapping.
            p.bodyRot = (c.heading * 0.33f).coerceIn(-32f, 34f)
            p.jets = 1f
            p.armLRaise = 34f; p.armRRaise = 34f; p.armLElbow = -16f; p.armRElbow = -16f
            p.legLSwing = 3f; p.legRSwing = 3f
            p.headRot = -p.bodyRot * 0.3f
            p.eyeOpen = 1.15f; p.mouthSmile = 1f; p.mouthOpen = 0.25f
            p.earWiggle = 0.8f * sin(t * 30f)
        }
        p.tailRot = 10f; p.tailSway = sin(t * 9f)
        if (sputter) {
            // Engine coughing: flames cut out, wobble, panic face, arms flailing.
            val cut = sin(t * 37f) * sin(t * 13f)
            p.jets = if (cut > 0.35f) 0.12f else 0.45f + 0.5f * abs(sin(t * 21f))
            p.bodyRot += 13f * sin(t * 17f)
            p.armLRaise = 70f + 50f * sin(t * 15f); p.armRRaise = 70f + 50f * sin(t * 15f + 2f)
            p.legLSwing = 12f + 12f * sin(t * 19f); p.legRSwing = 12f - 12f * sin(t * 19f)
            p.eyeOpen = 1.5f; p.browAngle = -14f; p.mouthSmile = -0.7f; p.mouthOpen = 0.5f; p.sweat = 1f
        }
    }

    private fun smooth(k: Float): Float { val c = k.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }
    private fun bumpK(k: Float) = if (k <= 0f || k >= 1f) 0f else sin(PI.toFloat() * k)

    /** Rocket lift-off: rig units above the ground at [st] (accelerating). */
    fun rocketLift(st: Float): Float = if (st < 1.35f) 0f else { val k = st - 1.35f; 90f * k + 520f * k * k }

    private fun exit(p: Pose, t: Float, st: Float, e: com.combo.monkeycompanion.brain.Exit) {
        idle(p, t)
        p.propT = st
        when (e) {
            com.combo.monkeycompanion.brain.Exit.FAINT -> {
                // 1. "Oh no... I feel faint": back of the hand to the forehead, other arm flung out.
                val pose = seg(st, 0f, 0.45f)
                p.armRRaise = 14f + 138f * pose; p.armRElbow = -8f - 100f * pose
                p.armLRaise = 14f + 86f * pose; p.armLElbow = -8f + 18f * pose
                p.headRot = -14f * pose; p.headY = -2f * pose
                p.eyeOpen = 1f - 0.6f * pose; p.browAngle = 12f * pose; p.browY = -3f * pose
                p.mouthOpen = 0.45f * pose; p.mouthSmile = -0.4f * pose
                // 2. Wobble on jelly legs.
                if (st > 0.5f && st < 1.8f) {
                    val w = sin((st - 0.5f) * 10f) * min(1f, (st - 0.5f) * 2f)
                    p.bodyRot = 9f * w; p.bodyX = 4f * w; p.headRot += 7f * w
                    p.squashY = 1f - 0.04f * abs(w); p.legLSwing = 10f + 6f * w; p.legRSwing = 10f - 6f * w
                }
                // 3. Swoon: topple over backwards, flat out, eyes shut, one foot twitching.
                if (st >= 1.8f) {
                    val k = seg(st, 1.8f, 2.35f)
                    val fall = k * k
                    p.bodyRot = -82f * fall; p.bodyY = 34f * fall; p.bodyX = -10f * fall
                    p.headRot = -14f - 12f * fall
                    p.eyeOpen = 0.4f * (1f - k); p.mouthOpen = 0.35f; p.mouthSmile = -0.2f
                    p.armLRaise = 100f + 60f * fall; p.legLSwing = 20f + 25f * fall; p.legRSwing = 12f
                    if (st > 2.35f) { p.bodyY = 34f - 5f * bump(st, 2.35f, 2.55f); p.legRSwing = 12f + 18f * bump(st, 2.7f, 2.9f) }
                    p.fade = 1f - seg(st, 2.9f, 3.7f)
                }
            }
            com.combo.monkeycompanion.brain.Exit.ROCKET -> {
                p.rocket = seg(st, 0f, 0.3f)
                // Hug the rocket, grinning; then light the fuse; then whoosh.
                p.armLRaise = 55f; p.armLElbow = -70f; p.armRRaise = 55f; p.armRElbow = -70f
                p.legLSwing = 18f; p.legRSwing = 18f
                p.mouthSmile = 0.9f; p.lookY = 1f
                if (st in 0.55f..1.35f) {                       // fuse fizzing: shaking, eyes wide
                    p.flame = 0.18f + 0.1f * abs(sin(st * 40f))
                    p.bodyX = 1.5f * sin(st * 70f); p.eyeOpen = 1.4f; p.mouthOpen = 0.35f
                    p.lookY = 4f; p.lookX = -1f
                }
                if (st >= 1.35f) {
                    val k = st - 1.35f
                    p.flame = 0.8f + 0.2f * abs(sin(st * 33f))
                    p.smoke = min(1f, k * 3f) * (1f - seg(st, 3.0f, 3.6f))
                    p.bodyY = -rocketLift(st)
                    p.squashY = 1.06f; p.squashX = 0.95f
                    p.armRRaise = 160f + 15f * sin(st * 14f); p.armRElbow = 5f    // "wheee" wave
                    p.eyesHappy = 1f; p.mouthOpen = 0.7f; p.mouthSmile = 1f
                    p.tailRot = -20f; p.tailSway = sin(st * 20f)
                }
            }
            com.combo.monkeycompanion.brain.Exit.POOF -> {
                if (st < 1.45f) {                                // bye bye, machan!
                    val w = sin(st * 12f)
                    p.armRRaise = 145f + 22f * w; p.armRElbow = 18f * w
                    p.eyesHappy = 1f; p.mouthOpen = 0.45f + 0.15f * w; p.mouthSmile = 1f
                    p.headRot = 6f * w; p.blush = 0.5f
                } else {
                    val k = seg(st, 1.45f, 1.7f)                 // crouch... and poof
                    p.squashY = 1f - 0.16f * k; p.squashX = 1f + 0.1f * k; p.bodyY = 6f * k
                    p.eyeOpen = 0f; p.mouthSmile = 0.8f
                    p.puff = seg(st, 1.62f, 2.85f)
                    p.fade = 1f - seg(st, 1.68f, 1.9f)
                }
            }
        }
    }

    private fun gag(p: Pose, t: Float, st: Float, g: com.combo.monkeycompanion.brain.Gag) {
        idle(p, t)
        p.propT = st
        val len = g.seconds
        when (g) {
            com.combo.monkeycompanion.brain.Gag.SNEEZE -> {
                val build = seg(st, 0f, 1.2f)
                if (st < 1.2f) {                                   // "ah... ah..."
                    p.headRot = -12f * build; p.headY = -2f * build
                    p.eyeOpen = 1f - 0.75f * build; p.browY = -3f * build; p.browAngle = -8f * build
                    p.mouthOpen = 0.35f * build * (0.7f + 0.3f * sin(st * 9f)); p.mouthSmile = 0f
                    p.armLRaise = 20f + 10f * build; p.armRRaise = 20f + 10f * build
                    p.earWiggle = if (st > 0.9f) sin(st * 40f) else 0f
                } else if (st < 1.55f) {                           // ACHOO!
                    val k = bump(st, 1.2f, 1.55f)
                    p.headRot = 20f * k; p.headY = 3f * k; p.bodyY = 5f * k
                    p.squashY = 1f - 0.12f * k; p.squashX = 1f + 0.08f * k
                    p.eyeOpen = 0f; p.mouthOpen = 1f; p.mouthSmile = 0.1f
                    p.armLRaise = 60f; p.armRRaise = 60f; p.armLElbow = 20f; p.armRElbow = 20f
                    p.tailRot = 70f; p.tailSway = 1f
                    p.sneeze = 1f
                } else {                                           // sheepish nose wipe
                    val k = seg(st, 1.55f, 1.9f)
                    p.sneeze = max(0f, 1f - (st - 1.55f) / 0.5f)
                    p.armRRaise = 20f + 105f * k; p.armRElbow = -8f - 112f * k + 10f * sin(st * 16f) * k
                    p.headRot = 6f; p.lookX = -3f; p.lookY = 1f
                    p.blush = 0.8f; p.mouthSmile = 0.15f; p.browAngle = -10f
                }
            }
            com.combo.monkeycompanion.brain.Gag.SLIP -> {
                p.peel = env(st, len); p.peelX = 26f
                when {
                    st < 0.75f -> { walk(p, st * 1.2f, strollCtx); p.lookX = 4f; p.mouthSmile = 0.9f }   // strolling, whistling
                    st < 1.15f -> {                                // feet fly out
                        val k = seg(st, 0.75f, 1.0f)
                        p.bodyY = -30f * bump(st, 0.75f, 1.15f)
                        p.legLSwing = 85f * k; p.legRSwing = 85f * k; p.legLLift = 6f; p.legRLift = 6f
                        p.armLRaise = 150f + 20f * sin(st * 30f); p.armRRaise = 150f - 20f * sin(st * 30f)
                        p.armLElbow = 15f; p.armRElbow = 15f
                        p.eyeOpen = 1.45f; p.mouthOpen = 0.95f; p.mouthSmile = -0.2f; p.browY = -5f
                        p.tailRot = 80f; p.tailSway = 1f; p.bodyRot = 10f * sin(st * 25f)
                    }
                    st < 1.5f -> {                                 // THUD on the bum
                        val k = bump(st, 1.15f, 1.5f)
                        p.bodyY = 13f; p.squashY = 0.86f - 0.06f * k; p.squashX = 1.08f + 0.05f * k
                        p.legLSwing = 75f; p.legRSwing = 75f; p.legLLift = -2f; p.legRLift = -2f
                        p.armLRaise = 40f; p.armRRaise = 40f; p.armLElbow = -10f; p.armRElbow = -10f
                        p.eyeOpen = 0.1f; p.mouthOpen = 0.5f; p.mouthSmile = -0.5f
                    }
                    else -> {                                      // dazed, then "I meant to do that"
                        val daze = 1f - seg(st, 2.1f, 2.4f)
                        p.bodyY = 13f; p.squashY = 0.92f; p.squashX = 1.04f
                        p.legLSwing = 70f; p.legRSwing = 70f; p.legLLift = -2f; p.legRLift = -2f
                        p.armLRaise = 30f; p.armRRaise = 30f
                        p.headRot = 9f * sin(st * 7f) * daze
                        p.lookX = 4f * sin(st * 9f) * daze; p.lookY = 3f * cos(st * 9f) * daze
                        p.zzz = 0f
                        p.mouthSmile = -0.2f + 0.8f * (1f - daze); p.blush = 0.7f * (1f - daze)
                        if (daze < 0.5f) { p.armRRaise = 150f; p.armRElbow = -105f + 10f * sin(st * 18f) }   // scratches head
                    }
                }
            }
            com.combo.monkeycompanion.brain.Gag.BINOCULARS -> {
                val up = seg(st, 0f, 0.35f) * (1f - seg(st, len - 0.35f, len))
                p.binoculars = up
                p.armLRaise = 14f + 130f * up; p.armLElbow = -8f - 112f * up
                p.armRRaise = 14f + 130f * up; p.armRElbow = -8f - 112f * up
                val scan = seg(st, 0.4f, 2.3f)
                p.headRot = if (st < 2.3f) -14f + 28f * scan else -12f
                p.lookX = 0f
                if (st in 2.3f..2.9f) { p.headRot = -2f; p.headY = -3f * bump(st, 2.3f, 2.6f) }   // double take at YOU
                p.browY = -2f; p.mouthSmile = if (st > 2.5f) 0.9f else 0.2f
                p.blush = if (st > 2.6f) 0.6f else 0f
                p.tailRot = 20f + 20f * sin(st * 4f)
            }
            com.combo.monkeycompanion.brain.Gag.FAKE_SLEEP -> {
                sleep(p, t)
                p.zzz = if (st < 1.6f || st in 2.9f..3.6f) 1f else 0f
                if (st in 1.6f..2.8f) {                            // one eye opens to check on you...
                    p.wink = 1f; p.eyeOpen = 0.9f * seg(st, 1.6f, 1.8f); p.lookX = 3.5f; p.lookY = -1f
                    p.mouthSmile = 0.6f
                } else if (st in 2.8f..3.6f) {                     // ...snaps shut. *snore*
                    p.eyeOpen = 0f; p.mouthOpen = 0.3f
                }
                if (st > 3.6f) {                                   // busted: giggles
                    val k = seg(st, 3.6f, 3.9f)
                    p.headRot = 16f * (1f - k); p.bodyY = 13f * (1f - k) + 4f
                    p.eyesHappy = 1f; p.mouthOpen = 0.6f; p.mouthSmile = 1f; p.blush = 0.8f
                    p.squashY = 0.97f + 0.03f * sin(st * 22f)
                }
            }
            com.combo.monkeycompanion.brain.Gag.JUGGLE -> {
                p.bananas = if (st < len - 0.6f) min(1f, st / 0.2f) else 0f
                val ph = st * 6.3f
                p.armLRaise = 40f + 26f * sin(ph); p.armLElbow = -95f
                p.armRRaise = 40f - 26f * sin(ph); p.armRElbow = -95f
                p.lookY = -3.5f; p.lookX = 2.5f * sin(ph * 0.5f); p.headRot = 4f * sin(ph * 0.5f)
                p.mouthOpen = 0.15f; p.mouthSmile = 0.4f; p.browY = -2f
                p.bodyY = -2f * abs(sin(ph))
                p.tailRot = 30f + 20f * sin(ph * 0.5f); p.tailSway = sin(ph * 0.5f)
                if (st > len - 0.6f) {                             // ta-daa
                    p.armLRaise = 120f; p.armRRaise = 120f; p.armLElbow = 10f; p.armRElbow = 10f
                    p.eyesHappy = 1f; p.mouthOpen = 0.5f; p.mouthSmile = 1f; p.lookY = 0f
                }
            }
            com.combo.monkeycompanion.brain.Gag.TAIL_HIDE -> {
                val k = seg(st, 0f, 0.3f)
                p.tailFront = if (st < len - 0.25f) 1f else 0f
                p.tailRot = 8f + 112f * k; p.tailSway = -0.6f
                p.squashY = 1f - 0.06f * k; p.bodyY = 4f * k
                p.armLRaise = 40f; p.armLElbow = -95f; p.armRRaise = 40f; p.armRElbow = -95f
                val dart = ((st / 0.5f).toInt() % 2 == 0)
                p.lookX = if (dart) -4.5f else 4.5f; p.eyeOpen = 1.25f
                p.mouthSmile = 0.5f; p.browY = -3f
                p.bodyX = 3f * sin(st * 2f)
            }
            com.combo.monkeycompanion.brain.Gag.CHASE_FLY -> {
                p.fly = if (st < len - 0.5f) 1f else max(0f, 1f - (st - (len - 0.5f)) / 0.5f)
                p.flyX = 42f * sin(st * 3.3f) + 10f * sin(st * 11f)
                p.flyY = -150f + 26f * sin(st * 4.9f) - (if (st > len - 0.6f) (st - (len - 0.6f)) * 260f else 0f)
                p.lookX = (p.flyX / 9f).coerceIn(-4.5f, 4.5f); p.lookY = ((p.flyY + 130f) / 10f).coerceIn(-4f, 2f)
                p.headRot = p.flyX * 0.18f
                val swat = max(bump(st, 1.7f, 2.0f), bump(st, 3.0f, 3.3f))
                if (swat > 0f) {
                    p.armLRaise = 40f + 115f * swat; p.armRRaise = 40f + 115f * swat
                    p.armLElbow = -30f; p.armRElbow = -30f
                    p.bodyY = -18f * swat; p.eyeOpen = 0.2f; p.mouthOpen = 0.5f
                } else { p.armLRaise = 30f; p.armRRaise = 30f; p.armLElbow = -40f; p.armRElbow = -40f }
                if (st > len - 0.7f) { p.browAngle = 14f; p.mouthSmile = -0.4f }   // it got away. hmph.
            }
            com.combo.monkeycompanion.brain.Gag.AIR_GUITAR -> {
                val rock = st < len - 0.8f
                p.guitar = env(st, len)
                p.legLSwing = 16f; p.legRSwing = 16f
                p.bodyY = -3f * abs(sin(st * 8f)); p.bodyRot = -5f
                p.armLRaise = 72f; p.armLElbow = -38f + 6f * sin(st * 3f)
                p.armRRaise = 28f + 14f * sin(st * 21f); p.armRElbow = -72f
                p.headRot = 13f * sin(st * 8f); p.headY = 2f * abs(sin(st * 8f))
                p.eyesHappy = 1f; p.mouthOpen = 0.55f; p.mouthSmile = 0.6f
                p.notes = 1f; p.tailRot = 45f + 25f * sin(st * 8f); p.tailSway = sin(st * 8f)
                if (!rock) {                                       // the big finish: arm to the sky
                    p.armRRaise = 168f; p.armRElbow = 0f; p.headRot = -6f; p.eyesHappy = 0f
                    p.eyeOpen = 1.2f; p.mouthOpen = 0.9f; p.bodyY = -6f
                }
            }
            com.combo.monkeycompanion.brain.Gag.SELFIE -> {
                val up = seg(st, 0f, 0.35f)
                p.phone = env(st, len)
                p.armRRaise = 14f + 112f * up; p.armRElbow = -8f - 4f * up
                p.headRot = -9f; p.lookX = 3.5f; p.lookY = -2f
                p.flash = max(bump(st, 1.25f, 1.45f), bump(st, 2.35f, 2.55f))
                if (st < 1.3f) {                                   // duck face
                    p.mouthOpen = 0.18f; p.mouthSmile = -0.35f; p.browY = -3f; p.eyeOpen = 0.85f
                } else {                                           // peace sign + grin
                    p.armLRaise = 130f; p.armLElbow = -70f
                    p.eyesHappy = if (st > 2.3f) 1f else 0f; p.mouthSmile = 1f; p.mouthOpen = 0.35f; p.blush = 0.5f
                    p.bodyRot = -4f
                }
            }
        }
    }
}
