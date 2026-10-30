package com.combo.monkeycompanion.engine

import com.combo.monkeycompanion.brain.Landings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** What a physics step changed that the host must react to (haptics, lines, surfaces, mood...). */
enum class StepEvent { NONE, TWEEN_DONE, COYOTE_END, BOUNCED, LANDED, CRASH_PLUNGE, CRASH_TOUCHDOWN, CRASH_RECOVERED }

/**
 * v10 (M1): one character's motion state and its per-frame maths, moved out of MonkeyView with no behaviour change.
 * Plain fields, no allocation per frame, no randomness and no side effects: the host still decides what happens
 * and reacts to the returned [StepEvent]s in the same order as before.
 */
class PhysicsBody {
    /** Box top-left in screen px. */
    var x = 0f
    var y = 0f
    /** Fall speed, px/s (down is positive). */
    var vy = 0f
    var falling = false

    /** Eased move of the box from (fromX, fromY) to (toX, toY); [tweenT] < 0 = no tween. [tweenArc] = leap height in px. */
    var tweenT = -1f
    var tweenDur = 0.3f
    var tweenArc = 0f
    var fromX = 0f; var fromY = 0f; var toX = 0f; var toY = 0f

    var surface = Surface(SurfaceKind.FLOOR, 0f, 0f, 0f, 0f, 0f, 0f)

    /** v9.3 Tom & Jerry mid-air hold: seconds left frozen in the air before falling. */
    var coyoteT = 0f

    /** v9.3 flying hero edge crash: 0 = none, 1 = sputter at the edge, 2 = plunge, 3 = crash-landed with stars. */
    var crashPhase = 0
    var crashTimer = 0f
    var thrustMultiplier = 1f
    var crashRot = 0f

    /** Advance the tween; [StepEvent.TWEEN_DONE] when it reached its end (the host then snaps to the surface). */
    fun stepTween(dt: Float): StepEvent {
        tweenT += dt
        val k = (tweenT / tweenDur).coerceIn(0f, 1f)
        val e = k * k * (3f - 2f * k)
        x = fromX + (toX - fromX) * e
        y = fromY + (toY - fromY) * e - tweenArc * sin(k * PI.toFloat())
        if (k >= 1f) { tweenT = -1f; x = toX; y = toY; return StepEvent.TWEEN_DONE }
        return StepEvent.NONE
    }

    /** Hold frozen in mid-air (zero gravity); [StepEvent.COYOTE_END] on the frame gravity comes back. */
    fun stepCoyote(dt: Float): StepEvent {
        coyoteT = (coyoteT - dt).coerceAtLeast(0f)
        vy = 0f
        return if (coyoteT <= 0f) StepEvent.COYOTE_END else StepEvent.NONE
    }

    /**
     * Fall speed for this frame. SIMPLE drops use the profile gravity times [fallGravity] (< 1 floats down, capped);
     * landing styles use [Landings.fallStep], and scream-jets brake near the [ground].
     */
    fun fallSpeed(style: Landings.Style, dt: Float, density: Float, fallGravity: Float, ground: Float, p: PhysicsProfile) {
        if (style == Landings.Style.SIMPLE) {
            vy += p.gravityDp * density * dt * fallGravity
            if (fallGravity < 1f) vy = min(vy, p.floatTerminalDp * density)
        } else {
            vy = Landings.fallStep(style, vy, p.gravityDp * density * (if (style == Landings.Style.PARACHUTE) fallGravity.coerceAtLeast(0.6f) else 1f), dt, density)
            if (style == Landings.Style.SCREAM_JETS) vy = Landings.screamBrake(vy, ground - y, density, dt)
        }
    }

    /** Move by the fall speed and meet the [ground]: one cannonball bounce if [canBounce], else land. */
    fun fallMove(dt: Float, ground: Float, canBounce: Boolean, density: Float, p: PhysicsProfile): StepEvent {
        y += vy * dt
        if (y >= ground && canBounce && vy > p.bounceMinDp * density) {
            y = ground; vy = Landings.bounce(vy)
            return StepEvent.BOUNCED
        }
        if (y >= ground) {
            y = ground; falling = false; vy = 0f
            return StepEvent.LANDED
        }
        return StepEvent.NONE
    }

    /**
     * One frame of the edge crash. [t] = animation time (thruster flicker), [maxX] = right-most box x,
     * [facing] = drift direction while tumbling.
     */
    fun stepEdgeCrash(dt: Float, t: Float, ground: Float, maxX: Float, facing: Float, density: Float, p: PhysicsProfile): StepEvent {
        when (crashPhase) {
            1 -> {
                crashTimer -= dt
                thrustMultiplier = p.crashSputterBase + p.crashSputterFlicker * abs(sin(t * p.crashSputterHz))
                y += p.crashSputterSinkDp * density * dt
                if (crashTimer <= 0f) {
                    crashPhase = 2
                    vy = p.crashPlungeStartDp * density
                    return StepEvent.CRASH_PLUNGE
                }
            }
            2 -> {
                vy += p.crashGravityDp * density * dt
                y += vy * dt
                thrustMultiplier = (thrustMultiplier * p.crashThrustDecay).coerceAtLeast(p.crashThrustMin)
                val dir = if (facing >= 0f) 1f else -1f
                x = (x + dir * p.crashDriftDp * density * dt).coerceIn(0f, max(0f, maxX))
                crashRot += dir * p.crashSpinDegS * dt
                if (y >= ground) {
                    y = ground
                    vy = 0f
                    crashPhase = 3
                    crashTimer = p.crashDazedSecs
                    thrustMultiplier = 0f
                    crashRot = 0f
                    return StepEvent.CRASH_TOUCHDOWN
                }
            }
            3 -> {
                y = ground
                crashTimer -= dt
                if (crashTimer <= 0f) {
                    crashPhase = 0
                    thrustMultiplier = 1.0f
                    crashRot = 0f
                    return StepEvent.CRASH_RECOVERED
                }
            }
        }
        return StepEvent.NONE
    }
}
