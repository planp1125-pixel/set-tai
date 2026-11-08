package com.combo.monkeycompanion.engine.touch

import com.combo.monkeycompanion.brain.TouchZone

/** v10.8 touch engine body parts: the rig's [TouchZone]s plus sub-parts the old zones lumped together. */
enum class BodyPart {
    HEAD, EAR, TRUNK, NECK, BELLY, TAIL, HAND, FOOT,
    /** A ridden / held prop (Skye's rocket). */
    PROP,
    PRIVATE, NONE;

    companion object {
        fun of(z: TouchZone): BodyPart = when (z) {
            TouchZone.HEAD -> HEAD; TouchZone.NECK -> NECK; TouchZone.BELLY -> BELLY; TouchZone.PRIVATE -> PRIVATE
            TouchZone.TAIL -> TAIL; TouchZone.HAND -> HAND; TouchZone.FOOT -> FOOT; TouchZone.NONE -> NONE
        }
    }
}

enum class TouchKind { TAP, HOLD, FLICK }

/** Everything the reaction picker knows about one touch. Speeds in dp/s, times in seconds. */
data class TouchInput(
    val part: BodyPart,
    /** The original rig zone (the legacy reaction uses it unchanged). */
    val zone: TouchZone,
    val side: Int,
    val kind: TouchKind,
    val speed: Float,
    val dirX: Float, val dirY: Float,
    /** 0..1: how hard it felt (pressure, speed, a short sharp jab). */
    val force: Float,
    val holdSec: Float,
    /** Same part touched again within [TouchTracker.REPEAT_WINDOW] (0 = first). */
    val repeatCount: Int,
    val sinceLastTouch: Float,
    val sinceLastSamePart: Float,
)

/** Builds [TouchInput]s: repeat counts and "time since" per part. Pure; the caller passes the clock. */
class TouchTracker {
    private val lastAt = FloatArray(BodyPart.values().size) { -1000f }
    private val reps = IntArray(BodyPart.values().size)
    private var lastAny = -1000f

    fun build(part: BodyPart, zone: TouchZone, side: Int, kind: TouchKind, speedDp: Float, dirX: Float, dirY: Float,
              pressure: Float, holdSec: Float, now: Float): TouchInput {
        val i = part.ordinal
        val sinceSame = now - lastAt[i]
        val sinceAny = now - lastAny
        reps[i] = if (sinceSame <= REPEAT_WINDOW) reps[i] + 1 else 0
        lastAt[i] = now; lastAny = now
        val jab = if (holdSec < 0.09f) 1f else (1f - (holdSec - 0.09f) / 0.25f).coerceIn(0f, 1f)
        val pr = if (pressure.isNaN() || pressure <= 0f) 0.5f else pressure.coerceIn(0f, 1f)
        val sp = (speedDp / 1500f).coerceIn(0f, 1f)
        val force = (0.45f * pr + 0.35f * sp + 0.2f * jab).coerceIn(0f, 1f)
        return TouchInput(part, zone, side, kind, speedDp, dirX, dirY, force, holdSec, reps[i], sinceAny, sinceSame)
    }

    companion object { const val REPEAT_WINDOW = 1.5f }
}
