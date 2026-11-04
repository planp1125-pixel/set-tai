package com.combo.monkeycompanion.brain

import kotlin.random.Random

// v10.23 Angel powers. Pure data plans like every other power; the glow / halo / wings are drawn by AngelRenderer
// from pose slots set in Angel.adapt(). No cooldowns, no frame-loop Random.

/** Her signature: hands together, golden healing sparkles rise around her and a soft light pulse spreads out. */
object HealingGlowPower : Superpower("angelheal", "Healing sparkle", "Golden healing sparkles and a soft pulse of light", emptySet()) {
    override val startLines get() = listOf("A little sparkle for you ✨😇", "Feel better, okay? 💛", "Healing hug, coming up! ✨")
    override val landLines get() = listOf("All better now 😇💛", "There! Shiny and happy ✨", "Glow complete. You're welcome 😊")
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(PowerStep(PowerPhase.POSE, 3.4f))
}

/** Lifts her halo, spins it like a golden ring with sparkles, and puts it back. */
object HaloSpinPower : Superpower("halospin", "Halo spin", "Spins her golden halo with sparkles", emptySet()) {
    override val startLines get() = listOf("Watch my halo! 😇", "Round and round ✨", "Halo trick! 💫")
    override val landLines get() = listOf("And… back on! 😇", "Didn't even drop it 💫")
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(PowerStep(PowerPhase.POSE, 2.8f))
}

/** Flies up and across with fast wing flaps, then glides back down. */
object AngelFlightPower : Superpower("angelfly", "Wing flight", "Flaps her little wings and flies across the screen", emptySet()) {
    override val startLines get() = listOf("Flap flap… up we go! 🪽", "Wings, go! 😇", "Sky time ✨")
    override val landLines get() = listOf("Soft as a feather 🪶", "Landed! Gently 😇")
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random): List<PowerStep> {
        val tx = if (x0 < 0.5f) 0.8f else 0.2f
        return listOf(
            PowerStep(PowerPhase.DROOP, 0.35f, x0, y0.coerceIn(0f, 0.8f)),
            PowerStep(PowerPhase.FLY, 1.6f, ((x0 + tx) / 2f).coerceIn(0.1f, 0.9f), 0.3f, 0.1f),
            PowerStep(PowerPhase.FLY, 1.4f, tx, y0.coerceIn(0f, 0.8f), -0.05f),
            PowerStep(PowerPhase.POSE, 0.6f),
        )
    }
}

/** Gentle float: rises a little in a soft warm glow, hands folded, and drifts back down. */
object AngelFloatPower : Superpower("angelfloat", "Gentle float", "Floats up in a soft warm glow, then drifts down", emptySet()) {
    override val startLines get() = listOf("Light as a cloud ☁️😇", "Floaty… peaceful… ✨")
    override val landLines get() = listOf("Back down, softly 🪶", "So calm up there 😌")
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(
        PowerStep(PowerPhase.DROOP, 0.4f, x0, y0.coerceIn(0f, 0.8f)),
        PowerStep(PowerPhase.FLY, 1.5f, x0.coerceIn(0.15f, 0.85f), 0.6f, 0.06f),
        PowerStep(PowerPhase.POSE, 1.4f),
        PowerStep(PowerPhase.FLY, 1.2f, x0.coerceIn(0.15f, 0.85f), y0.coerceIn(0f, 0.8f), -0.04f),
        PowerStep(PowerPhase.POSE, 0.5f),
    )
}
