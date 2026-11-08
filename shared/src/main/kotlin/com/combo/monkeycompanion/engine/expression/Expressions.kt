package com.combo.monkeycompanion.engine.expression

import com.combo.monkeycompanion.rig.Pose
import kotlin.math.sin

/**
 * v10.32 Expression engine: named faces ANY body can show. A [Face] writes only the shared face slots of [Pose]
 * (eyes, brows, mouth, look, blush, anger, sweat, head tilt), blended by weight, so Tutty, Angel, Settai,
 * Gajju, heroes and studio characters all act the same expression with no per-character code.
 * NaN = leave that slot as the body's animation drew it. Pure functions of time: no Random (GoldenTrace-safe).
 * (Episode's older `Expr` stays for the Billa & Chittu dramas; new scripts use this catalog.)
 */
class Face(
    val eye: Float = Float.NaN, val happy: Float = Float.NaN, val brow: Float = Float.NaN, val browY: Float = Float.NaN,
    val mouth: Float = Float.NaN, val smile: Float = Float.NaN, val lookX: Float = Float.NaN, val lookY: Float = Float.NaN,
    val head: Float = Float.NaN, val blush: Float = Float.NaN, val anger: Float = Float.NaN, val sweat: Float = Float.NaN,
    /** Tremble amplitude in degrees (fear). */
    val shake: Float = 0f,
)

object Expressions {
    val catalog: Map<String, Face> = linkedMapOf(
        "neutral" to Face(eye = 1f, happy = 0f, brow = 0f, mouth = 0f, smile = 0.4f),
        "happy" to Face(eye = 1f, happy = 0.8f, mouth = 0.35f, smile = 1f, blush = 0.4f),
        "sleepy" to Face(eye = 0.15f, brow = -4f, mouth = 0.05f, smile = 0.1f),
        "wake" to Face(eye = 1.35f, brow = -8f, browY = -4f, mouth = 0.3f, smile = 0f),
        "look" to Face(eye = 1.1f, brow = -3f, mouth = 0f, smile = 0.2f),
        "curious" to Face(eye = 1.15f, brow = -5f, browY = -2.5f, mouth = 0.18f, smile = 0.25f, head = 8f),
        "angry" to Face(eye = 0.9f, brow = 14f, mouth = 0.1f, smile = -0.6f, anger = 0.6f),
        "evil" to Face(eye = 0.75f, brow = 16f, mouth = 0.2f, smile = 0.9f, anger = 0.3f),
        "determined" to Face(eye = 1f, brow = 10f, mouth = 0f, smile = -0.1f),
        "shocked" to Face(eye = 1.5f, brow = -10f, browY = -5f, mouth = 0.9f, smile = -0.2f),
        "scream" to Face(eye = 1.8f, brow = -14f, browY = -6f, mouth = 1.2f, smile = -0.3f, sweat = 0.5f, shake = 3f),
        "fear" to Face(eye = 1.4f, brow = -12f, browY = -3f, mouth = 0.55f, smile = -0.8f, sweat = 1f, shake = 2.5f),
        "sorry" to Face(eye = 1.1f, brow = -12f, browY = -2f, mouth = 0.3f, smile = -0.5f, blush = 0.5f, sweat = 0.6f, head = -6f, shake = 1.2f),
        "sad" to Face(eye = 0.8f, brow = -10f, mouth = 0.1f, smile = -0.9f),
        "smug" to Face(eye = 0.8f, brow = 6f, mouth = 0f, smile = 0.8f, lookX = 0.4f),
    )

    fun byName(name: String): Face? = catalog[name.lowercase()]

    /** Show [name] on [p] with weight [k] at time [t]. Unknown name = no-op (false). */
    fun apply(name: String, p: Pose, t: Float, k: Float): Boolean {
        val f = byName(name) ?: return false
        apply(f, p, t, k); return true
    }

    fun apply(f: Face, p: Pose, t: Float, k: Float) {
        val w = k.coerceIn(0f, 1f)
        if (w <= 0f) return
        fun mix(a: Float, b: Float) = if (b.isNaN()) a else a + (b - a) * w
        p.eyeOpen = mix(p.eyeOpen, f.eye); p.eyesHappy = mix(p.eyesHappy, f.happy)
        p.browAngle = mix(p.browAngle, f.brow); p.browY = mix(p.browY, f.browY)
        p.mouthOpen = mix(p.mouthOpen, f.mouth); p.mouthSmile = mix(p.mouthSmile, f.smile)
        p.lookX = mix(p.lookX, f.lookX); p.lookY = mix(p.lookY, f.lookY)
        p.headRot = mix(p.headRot, f.head); p.blush = mix(p.blush, f.blush)
        p.anger = mix(p.anger, f.anger); p.sweat = mix(p.sweat, f.sweat)
        if (f.shake > 0f) { p.bodyRot += f.shake * w * sin(t * 47f); p.headRot += f.shake * 0.6f * w * sin(t * 61f) }
    }
}
