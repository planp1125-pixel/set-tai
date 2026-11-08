package com.combo.monkeycompanion.engine.anim

/** One follow-through appendage's spring feel. stiffness ~ 120-180 (higher = snappier), damping ratio 0.35-0.5
 *  (lower = more overshoot/wobble), lag seconds per chain segment, gain = degrees of swing per (dp/s) of body speed. */
data class AppendageTuning(
    val enabled: Boolean = true,
    val stiffness: Float = 150f,
    val damping: Float = 0.42f,
    val lag: Float = 0.04f,
    val gain: Float = 1f,
    val maxDeg: Float = 28f,
)

enum class Appendage { TAIL, EARS, HAIR, CAPE, TRUNK }

/**
 * v10.7 per-character animation tuning for the Disney-principles engine ([AnimEngine]).
 * Lives on [com.combo.monkeycompanion.engine.CharacterProfile.anim]; tweak a character by editing its profile.
 */
data class AnimTuning(
    /** Master switch for this character (the global Settings flag still has to be on). */
    val enabled: Boolean = true,
    /** Lowest y scale a squash may reach (0.7 = 30% flatter). */
    val squashMax: Float = 0.75f,
    /** Highest y scale a stretch may reach. */
    val stretchMax: Float = 1.25f,
    /** Extra landing squash kick, 0..0.3 (spring settles with a little rebound stretch). */
    val landingSquash: Float = 0.14f,
    /** Max velocity stretch along the motion direction (0.12 = 12%). */
    val velocityStretch: Float = 0.10f,
    /** Landing-squash spring (lower damping = bouncier). */
    val squashStiffness: Float = 260f,
    val squashDamping: Float = 0.38f,
    /** Easing used for timed pose envelopes (props in/out). */
    val easeStyle: EaseStyle = EaseStyle.SMOOTHSTEP,
    /** easeOutBack strength for reaction pops (1.70158 = ~10% overshoot). */
    val overshoot: Float = 1.70158f,
    /** Second-order pose smoothing (slow in AND slow out): natural frequency. */
    val poseFrequency: Float = 30f,
    val tail: AppendageTuning = AppendageTuning(),
    val ears: AppendageTuning = AppendageTuning(stiffness = 170f, damping = 0.4f, lag = 0.03f, gain = 0.6f, maxDeg = 20f),
    val hair: AppendageTuning = AppendageTuning(stiffness = 140f, damping = 0.45f, lag = 0.04f, gain = 0.8f, maxDeg = 24f),
    val cape: AppendageTuning = AppendageTuning(stiffness = 130f, damping = 0.4f, lag = 0.05f, gain = 1f, maxDeg = 26f),
    val trunk: AppendageTuning = AppendageTuning(stiffness = 140f, damping = 0.4f, lag = 0.05f, gain = 0.7f, maxDeg = 18f),
    // ---- v10.8 Disney principles part 2 (LivingLayer + touch engine; visual only) ----
    /** Timing: tempo multiplier for reactions and idle micro-actions (>1 = quicker, e.g. mouse 1.3; <1 = heavier, Gajju 0.8). */
    val tempo: Float = 1f,
    /** Exaggeration: global scale on reaction amplitude (1 = as authored). */
    val expressiveness: Float = 1f,
    /** Anticipation: wind-up length in seconds (80-150 ms) before leaps, launches, powers and big reactions. */
    val anticipationSec: Float = 0.11f,
    /** Anticipation depth: crouch as a fraction of a full squash (0.25 = gentle). */
    val anticipationDepth: Float = 0.3f,
    /** Secondary action: mean seconds between idle ear twitches / tail flicks / look-arounds (0 = never). */
    val earTwitchEvery: Float = 7f,
    val tailFlickEvery: Float = 6f,
    val lookAroundEvery: Float = 9f,
    /** Solid drawing: max body lean (degrees) into fast sideways motion and turns. */
    val leanDeg: Float = 5f,
    /** Appeal: happy squint + blush strength when petted (0..1). */
    val petSquint: Float = 1f,
    /** The TAIL spring drives dangling legs instead (riders with no tail: Skye). */
    val legsDangle: Boolean = false,
) {
    fun of(a: Appendage): AppendageTuning = when (a) {
        Appendage.TAIL -> tail; Appendage.EARS -> ears; Appendage.HAIR -> hair; Appendage.CAPE -> cape; Appendage.TRUNK -> trunk
    }

    companion object {
        val DEFAULT = AnimTuning()
        /** Settai monkey: bouncy, big floppy tail. */
        val MONKEY = AnimTuning(squashMax = 0.72f, stretchMax = 1.28f, landingSquash = 0.18f, velocityStretch = 0.12f, squashDamping = 0.32f,
            easeStyle = EaseStyle.SMOOTHSTEP, overshoot = 1.9f, tempo = 1.1f, expressiveness = 1.15f, anticipationSec = 0.10f, tailFlickEvery = 5f,
            tail = AppendageTuning(stiffness = 150f, damping = 0.36f, lag = 0.045f, gain = 1.2f, maxDeg = 34f),
            ears = AppendageTuning(stiffness = 175f, damping = 0.38f, lag = 0.03f, gain = 0.7f, maxDeg = 22f))
        /** Billa the cat: springy, quick whippy tail. */
        val CAT = AnimTuning(squashMax = 0.74f, stretchMax = 1.26f, landingSquash = 0.15f, velocityStretch = 0.12f, squashStiffness = 320f, squashDamping = 0.4f,
            overshoot = 1.8f, tempo = 1.1f, anticipationSec = 0.12f, earTwitchEvery = 4.5f, tailFlickEvery = 4f,
            tail = AppendageTuning(stiffness = 180f, damping = 0.35f, lag = 0.035f, gain = 1.3f, maxDeg = 32f),
            ears = AppendageTuning(stiffness = 180f, damping = 0.45f, lag = 0.02f, gain = 0.5f, maxDeg = 14f))
        /** Chittu the mouse: light and quick. */
        val MOUSE = CAT.copy(landingSquash = 0.12f, squashStiffness = 360f, tempo = 1.3f, anticipationSec = 0.08f, earTwitchEvery = 3.5f, lookAroundEvery = 5f,
            tail = AppendageTuning(stiffness = 180f, damping = 0.4f, lag = 0.03f, gain = 1.1f, maxDeg = 28f))
        /** Zoom: firm hero body, cape trails. */
        val SUPERBOY = AnimTuning(squashMax = 0.82f, stretchMax = 1.18f, landingSquash = 0.10f, velocityStretch = 0.10f, squashDamping = 0.45f,
            easeStyle = EaseStyle.CUBIC, expressiveness = 1.05f, anticipationSec = 0.12f, earTwitchEvery = 0f, tailFlickEvery = 0f, leanDeg = 9f,
            cape = AppendageTuning(stiffness = 125f, damping = 0.38f, lag = 0.05f, gain = 1.2f, maxDeg = 30f),
            hair = AppendageTuning(stiffness = 160f, damping = 0.45f, lag = 0.03f, gain = 0.5f, maxDeg = 14f))
        /** Bolt: heavy metal suit, barely squashes. */
        val ARMOUR = AnimTuning(squashMax = 0.9f, stretchMax = 1.08f, landingSquash = 0.06f, velocityStretch = 0.05f, squashDamping = 0.5f,
            easeStyle = EaseStyle.CUBIC, overshoot = 1.3f, tempo = 0.85f, expressiveness = 0.8f, anticipationSec = 0.15f,
            earTwitchEvery = 0f, tailFlickEvery = 0f, lookAroundEvery = 11f, leanDeg = 4f, petSquint = 0.6f)
        /** Masko: rubbery comedy. */
        val MASK = AnimTuning(squashMax = 0.72f, stretchMax = 1.28f, landingSquash = 0.16f, velocityStretch = 0.12f, squashDamping = 0.34f, overshoot = 1.9f,
            expressiveness = 1.25f, anticipationSec = 0.13f, earTwitchEvery = 0f, tailFlickEvery = 0f, lookAroundEvery = 7f)
        /** Gajju: heavy and soft, slow floppy ears and a lazy trunk. */
        val GAJJU = AnimTuning(squashMax = 0.78f, stretchMax = 1.2f, landingSquash = 0.16f, velocityStretch = 0.08f, squashStiffness = 180f, squashDamping = 0.42f,
            easeStyle = EaseStyle.SINE, overshoot = 1.4f, poseFrequency = 26f,
            tempo = 0.8f, expressiveness = 1.1f, anticipationSec = 0.15f, earTwitchEvery = 5f, tailFlickEvery = 8f, lookAroundEvery = 10f, leanDeg = 6f,
            ears = AppendageTuning(stiffness = 120f, damping = 0.38f, lag = 0.07f, gain = 1f, maxDeg = 26f),
            trunk = AppendageTuning(stiffness = 120f, damping = 0.35f, lag = 0.08f, gain = 1f, maxDeg = 22f))
        /** Custom chibi look-alikes (long hair swings more). */
        val CHIBI = AnimTuning(hair = AppendageTuning(stiffness = 135f, damping = 0.4f, lag = 0.045f, gain = 1f, maxDeg = 26f),
            earTwitchEvery = 0f, tailFlickEvery = 0f)
        /** v10.8 Skye: light, zippy and bouncy; TAIL spring = his dangling legs, CAPE spring = his scarf. */
        val ROCKET = AnimTuning(squashMax = 0.74f, stretchMax = 1.26f, landingSquash = 0.16f, velocityStretch = 0.12f, squashStiffness = 300f, squashDamping = 0.34f,
            easeStyle = EaseStyle.SMOOTHSTEP, overshoot = 1.9f, poseFrequency = 34f,
            tempo = 1.25f, expressiveness = 1.2f, anticipationSec = 0.09f, earTwitchEvery = 0f, tailFlickEvery = 0f, lookAroundEvery = 6f, leanDeg = 12f, legsDangle = true,
            tail = AppendageTuning(stiffness = 120f, damping = 0.33f, lag = 0.05f, gain = 1.1f, maxDeg = 30f),
            hair = AppendageTuning(stiffness = 150f, damping = 0.36f, lag = 0.03f, gain = 1.1f, maxDeg = 24f),
            cape = AppendageTuning(stiffness = 110f, damping = 0.34f, lag = 0.05f, gain = 1.3f, maxDeg = 34f))
        /** v10.14 Echo: smooth, silky (long slow-in/out), a floppy fedora that trails his moves; squash kept small. */
        val HOLLOW = AnimTuning(squashMax = 0.88f, stretchMax = 1.15f, landingSquash = 0.12f, velocityStretch = 0.06f, squashDamping = 0.45f,
            easeStyle = EaseStyle.SMOOTHSTEP, overshoot = 1.5f, poseFrequency = 24f, tempo = 1.0f, expressiveness = 1.1f, anticipationSec = 0.13f,
            earTwitchEvery = 0f, tailFlickEvery = 0f, lookAroundEvery = 7f, leanDeg = 8f,
            hair = AppendageTuning(stiffness = 120f, damping = 0.34f, lag = 0.05f, gain = 1.2f, maxDeg = 22f))
        /** v10.16 Tutty: soft and floaty (long slow-in/out), a light squash, a whippy tail with follow-through. */
        val DEVIL = AnimTuning(squashMax = 0.88f, stretchMax = 1.14f, landingSquash = 0.12f, velocityStretch = 0.08f, squashDamping = 0.4f,
            easeStyle = EaseStyle.SMOOTHSTEP, overshoot = 1.6f, poseFrequency = 26f, tempo = 1.0f, expressiveness = 1.15f, anticipationSec = 0.12f,
            earTwitchEvery = 0f, tailFlickEvery = 0f, lookAroundEvery = 6f, leanDeg = 8f,
            tail = AppendageTuning(stiffness = 110f, damping = 0.3f, lag = 0.06f, gain = 1.3f, maxDeg = 36f))
        /** v10.14 Wavy: bouncy and quick, a whippy ponytail, leans hard into carves. */
        val SURF = AnimTuning(squashMax = 0.86f, stretchMax = 1.18f, landingSquash = 0.16f, velocityStretch = 0.1f, squashStiffness = 300f, squashDamping = 0.34f,
            easeStyle = EaseStyle.SMOOTHSTEP, overshoot = 1.8f, poseFrequency = 32f, tempo = 1.2f, expressiveness = 1.15f, anticipationSec = 0.1f,
            earTwitchEvery = 0f, tailFlickEvery = 0f, lookAroundEvery = 6f, leanDeg = 12f,
            hair = AppendageTuning(stiffness = 130f, damping = 0.32f, lag = 0.04f, gain = 1.4f, maxDeg = 30f))
    }
}
