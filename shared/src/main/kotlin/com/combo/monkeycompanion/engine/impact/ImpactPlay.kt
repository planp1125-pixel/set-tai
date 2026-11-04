package com.combo.monkeycompanion.engine.impact

import com.combo.monkeycompanion.rig.Pose
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * v10.20 "the core": cartoon physical comedy after a throw, for EVERY character (it only drives the shared [Pose]
 * and a whole-body squash the overlay applies around a pivot, so every renderer gets it for free).
 *
 *  - SPLAT (tossed into the left/right screen edge): squash flat against the glass, wobble, slide down the glass,
 *    peel off top-first, fall, dizzy stars, shake it off.
 *  - THWACK (tossed up into the top edge): head-first squash under the edge, then drop, dizzy, shake it off.
 *  - PANCAKE (hits the floor very hard): flattened like a chapati, accordion spring back up, dizzy, shake it off.
 *
 * Phases are pure timing; the overlay owns the position (flight, slide, fall) and reports [hit], [slideAtFloor]
 * and [landed]. No Random (GoldenTraceTest), no Android.
 */
class ImpactPlay {
    enum class Kind { SPLAT, THWACK, PANCAKE }
    enum class Phase { FLIGHT, SQUASH, HOLD, SLIDE, PEEL, FALL, SPRING, DIZZY, SHAKE, DONE }
    enum class Event { START_SLIDE, START_FALL, DONE }
    /** Which point of the body box the squash is anchored to. */
    enum class Pivot { CENTER, WALL_LEFT, WALL_RIGHT, TOP, FEET }

    var kind: Kind? = null; private set
    /** -1 = left edge, 1 = right edge (SPLAT only). */
    var side = 0; private set
    var phase = Phase.FLIGHT; private set
    /** Seconds in the current phase. */
    var pt = 0f; private set
    /** Seconds since the impact (or since the throw while still in flight). */
    var total = 0f; private set

    // ---- outputs read by the overlay every frame ----
    var sx = 1f; private set
    var sy = 1f; private set
    /** Whole-body rotation in degrees (spin in flight, tipping while peeling, wobble while dizzy). */
    var rot = 0f; private set
    var pivot = Pivot.CENTER; private set
    /** Slide speed down the glass, dp/s (SLIDE only). */
    val slideDp get() = SLIDE_START_DP + SLIDE_ACCEL_DP * pt
    val stars get() = phase == Phase.DIZZY || (kind == Kind.THWACK && (phase == Phase.HOLD || phase == Phase.FALL))   // v10.25: clear top-hit gag
    /** 0..1 how strongly to show the comic "SPLAT!" word (first 0.9 s after the hit). */
    val bang get() = if (kind != null && total < BANG_SECS) 1f - total / BANG_SECS else 0f
    val done get() = phase == Phase.DONE

    /** Spin speed while flying, degrees per second (set from the throw). */
    var spin = 0f

    /** The body hit something. Returns false if it was already past flight. */
    fun hit(k: Kind, wallSide: Int = 0): Boolean {
        if (phase != Phase.FLIGHT) return false
        kind = k; side = wallSide; total = 0f; rot = 0f
        pivot = when (k) {
            Kind.SPLAT -> if (wallSide < 0) Pivot.WALL_LEFT else Pivot.WALL_RIGHT
            Kind.THWACK -> Pivot.TOP
            Kind.PANCAKE -> Pivot.FEET
        }
        go(Phase.SQUASH)
        return true
    }

    /** SLIDE reached the floor: stop sliding and peel off right away. */
    fun slideAtFloor() { if (phase == Phase.SLIDE) go(Phase.PEEL) }

    /** FALL touched the ground. */
    fun landed() { if (phase == Phase.FALL) { pivot = Pivot.FEET; go(Phase.DIZZY) } }

    /** Advance [dt] seconds. Returns an event when the overlay has to change what moves the body. */
    fun step(dt: Float): Event? {
        pt += dt; total += dt
        val k = kind
        if (phase == Phase.FLIGHT) { rot = (rot + spin * dt) % 360f; sx = 1f; sy = 1f; return null }
        when (phase) {
            Phase.SQUASH -> {
                val q = ease((pt / SQUASH_SECS).coerceIn(0f, 1f))
                when (k) {
                    Kind.SPLAT -> { sx = 1f - (1f - SPLAT_W) * q; sy = 1f + (SPLAT_H - 1f) * q }
                    Kind.THWACK -> { sy = 1f - (1f - THWACK_H) * q; sx = 1f + (THWACK_W - 1f) * q }
                    else -> { sy = 1f - (1f - PANCAKE_H) * q; sx = 1f + (PANCAKE_W - 1f) * q }
                }
                if (pt >= SQUASH_SECS) go(Phase.HOLD)
            }
            Phase.HOLD -> {
                val wob = 0.06f * sin(pt * 45f) * exp(-pt * 6f)
                when (k) {
                    Kind.SPLAT -> { sx = SPLAT_W + wob; sy = SPLAT_H }
                    Kind.THWACK -> { sy = THWACK_H + wob * 0.5f; sx = THWACK_W }
                    else -> { sy = PANCAKE_H; sx = PANCAKE_W + wob * 0.5f }
                }
                val hold = when (k) { Kind.SPLAT -> SPLAT_HOLD; Kind.THWACK -> THWACK_HOLD; else -> PANCAKE_HOLD }
                if (pt >= hold) {
                    return when (k) {
                        Kind.SPLAT -> { go(Phase.SLIDE); Event.START_SLIDE }
                        Kind.THWACK -> { sx = 1f; sy = 1f; pivot = Pivot.CENTER; go(Phase.FALL); Event.START_FALL }
                        else -> { go(Phase.SPRING); null }
                    }
                }
            }
            Phase.SLIDE -> {
                sx = SPLAT_W; sy = SPLAT_H + 0.02f * sin(pt * 30f)
                if (pt >= SLIDE_MAX_SECS) go(Phase.PEEL)
            }
            Phase.PEEL -> {
                val q = (pt / PEEL_SECS).coerceIn(0f, 1f)
                sx = SPLAT_W + (1f - SPLAT_W) * backOut(q); sy = SPLAT_H + (1f - SPLAT_H) * q
                rot = -side * PEEL_DEG * q                           // tips away from the glass, top first
                if (pt >= PEEL_SECS) { sx = 1f; sy = 1f; pivot = Pivot.CENTER; go(Phase.FALL); return Event.START_FALL }
            }
            Phase.FALL -> { rot *= exp(-4f * dt); sx = 1f; sy = 1f }
            Phase.SPRING -> {
                // accordion: a damped spring from flat back to normal
                val y = 1f - (1f - PANCAKE_H) * exp(-5f * pt) * cos(16f * pt)
                sy = y.coerceAtLeast(0.2f); sx = (1f / kotlin.math.sqrt(sy)).coerceIn(0.7f, 1.9f)
                if (pt >= SPRING_SECS) { sx = 1f; sy = 1f; go(Phase.DIZZY) }
            }
            Phase.DIZZY -> {
                val land = if (pt < 0.15f) sin(PI.toFloat() * pt / 0.15f) else 0f
                sy = 1f - 0.22f * land; sx = 1f + 0.16f * land
                rot = 7f * sin(pt * 5f)
                if (pt >= DIZZY_SECS) { rot = 0f; sx = 1f; sy = 1f; go(Phase.SHAKE) }
            }
            Phase.SHAKE -> {
                val f = 1f - (pt / SHAKE_SECS).coerceIn(0f, 1f)
                rot = 14f * sin(pt * 55f) * f; sx = 1f + 0.05f * sin(pt * 55f) * f; sy = 1f
                if (pt >= SHAKE_SECS) { rot = 0f; sx = 1f; go(Phase.DONE); return Event.DONE }
            }
            Phase.FLIGHT, Phase.DONE -> Unit
        }
        return null
    }

    /** The face and limbs for this moment (on top of whatever the animation system set). */
    fun applyPose(p: Pose, t: Float) {
        when (phase) {
            Phase.FLIGHT -> {
                p.armLRaise = 150f + 25f * sin(t * 22f); p.armRRaise = 150f - 25f * sin(t * 22f)
                p.legLSwing = 30f * sin(t * 18f); p.legRSwing = -30f * sin(t * 18f)
                p.eyeOpen = 1.4f; p.mouthOpen = 0.7f; p.mouthSmile = -0.2f; p.browAngle = -18f; p.shadow = 0f
            }
            Phase.SQUASH, Phase.HOLD -> {   // spread-eagle against the glass, eyes popping
                p.armLRaise = 160f; p.armRRaise = 160f; p.armLElbow = 0f; p.armRElbow = 0f
                p.legLSwing = -34f; p.legRSwing = 34f
                p.eyeOpen = if (kind == Kind.THWACK) 0.1f else 1.5f; p.mouthOpen = 0.45f; p.mouthSmile = -0.7f
                p.browAngle = -20f; p.shadow = if (kind == Kind.PANCAKE) 1f else 0f
            }
            Phase.SLIDE -> {   // squeaking down the glass, eyes squeezed shut
                p.armLRaise = 150f - 40f * (pt / SLIDE_MAX_SECS).coerceIn(0f, 1f); p.armRRaise = p.armLRaise
                p.legLSwing = -30f; p.legRSwing = 30f
                p.eyeOpen = 0.12f; p.mouthOpen = 0.2f; p.mouthSmile = -0.9f; p.browAngle = -24f; p.shadow = 0f
            }
            Phase.PEEL -> { p.armLRaise = 120f; p.armRRaise = 120f; p.eyeOpen = 0.1f; p.mouthOpen = 0.5f; p.shadow = 0f }
            Phase.FALL -> {
                p.armLRaise = 165f; p.armRRaise = 165f; p.legLSwing = 12f; p.legRSwing = -12f
                p.eyeOpen = 0.15f; p.mouthOpen = 0.6f; p.mouthSmile = -0.4f; p.shadow = 0f
            }
            Phase.SPRING -> { p.eyeOpen = 1.4f; p.mouthOpen = 0.5f; p.armLRaise = 110f; p.armRRaise = 110f; p.legLSwing = -20f; p.legRSwing = 20f }
            Phase.DIZZY -> {   // seeing stars: eyes rolling, wobbly arms
                p.eyeOpen = 0.55f; p.lookX = 4f * sin(t * 11f); p.lookY = 3f * cos(t * 11f)
                p.mouthOpen = 0.15f + 0.1f * abs(sin(t * 4f)); p.mouthSmile = 0.1f; p.browAngle = -10f
                p.armLRaise = 40f + 20f * sin(t * 5f); p.armRRaise = 40f - 20f * sin(t * 5f)
                p.headRot = 8f * sin(t * 5f)
            }
            Phase.SHAKE -> { p.eyeOpen = 0.1f; p.mouthSmile = -0.3f; p.headRot = 16f * sin(t * 55f); p.sweat = 1f }
            Phase.DONE -> Unit
        }
    }

    private fun go(ph: Phase) { phase = ph; pt = 0f }

    companion object {
        const val SQUASH_SECS = 0.1f
        const val SPLAT_HOLD = 0.75f
        const val THWACK_HOLD = 0.95f
        const val PANCAKE_HOLD = 0.7f
        const val SLIDE_MAX_SECS = 1.3f
        const val SLIDE_START_DP = 28f
        const val SLIDE_ACCEL_DP = 70f
        const val PEEL_SECS = 0.42f
        const val PEEL_DEG = 40f
        const val SPRING_SECS = 0.9f
        const val DIZZY_SECS = 1.9f
        const val SHAKE_SECS = 0.7f
        const val BANG_SECS = 0.9f
        const val SPLAT_W = 0.32f; const val SPLAT_H = 1.22f
        const val THWACK_H = 0.5f; const val THWACK_W = 1.35f
        const val PANCAKE_H = 0.3f; const val PANCAKE_W = 1.75f

        /** A landing this fast (dp/s, downward) becomes a pancake instead of a normal landing. */
        const val PANCAKE_SPEED_DP = 2300f
        /** Throw speed (dp/s) needed: released high on the screen (non-flyers) or very hard (flyers). */
        const val THROW_SPEED_DP = 1400f
        const val FLYER_THROW_SPEED_DP = 2800f
        /** Released at least this high above the floor (fraction of the screen height) = a toss, not a push. */
        const val TOSS_HEIGHT_FRAC = 0.25f
        /** Gravity in flight, dp/s². */
        const val GRAVITY_DP = 2600f

        private fun ease(k: Float) = 1f - (1f - k) * (1f - k) * (1f - k)
        private fun backOut(k: Float): Float { val c1 = 1.7f; val c3 = c1 + 1f; val x = k - 1f; return 1f + c3 * x * x * x + c1 * x * x }

        /**
         * Is this release a toss that should fly and hit things? Pushes from near the floor keep the old
         * edge-hide; flyers need a much harder throw (a normal fling makes them fly an arc).
         */
        fun isToss(speedDp: Float, heightAboveFloorPx: Float, screenH: Float, flyer: Boolean): Boolean =
            if (flyer) speedDp >= FLYER_THROW_SPEED_DP
            else speedDp >= THROW_SPEED_DP && heightAboveFloorPx >= screenH * TOSS_HEIGHT_FRAC

        /** The line after an impact (Brain off), picked by a counter, never Random. */
        fun line(k: Kind, n: Int): String {
            val pool = when (k) {
                Kind.SPLAT -> listOf("Aiyyo! Who threw me?! 😵", "My face is a dosa now 🫓", "This glass is harder than it looks da…", "Again?! I'm telling everyone 😤", "Note to self: the screen has walls 🤕")
                Kind.THWACK -> listOf("Ouch! The sky is made of glass?! 🤕", "Who put a ceiling there?!", "My head! My poor head 😵‍💫")
                Kind.PANCAKE -> listOf("Flat as a chapati 🫓", "My spine went boing 🪗", "Gravity 1, me 0", "I'm a pancake now. Happy? 😑")
            }
            return pool[Math.floorMod(n, pool.size)]
        }
    }
}
