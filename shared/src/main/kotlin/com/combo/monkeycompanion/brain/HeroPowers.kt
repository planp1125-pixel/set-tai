package com.combo.monkeycompanion.brain

import com.combo.monkeycompanion.speech.HeroLines
import kotlin.random.Random

// v4.1 hero superpowers (Zoom, Bolt). Same sealed family as the monkey powers.

/** Zoom: cape swoops across the screen, heroic landing. */
object HeroSwoop : Superpower("swoop", "Flight swoop", "Cape-flapping swoops across the screen, heroic landing", setOf(PowerKit.CAPE, PowerKit.SPEED_LINES)) {
    override val startLines get() = HeroLines.SB_FLY
    override val landLines get() = HeroLines.SB_LAND
    override val crashLines get() = HeroLines.SB_CRASH
    override val crashChance get() = 0.15f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = Superpower.SuperBanana.plan(x0, y0, crash, rnd)
}

/** Zoom: hovers and sweeps two heat-vision beams across the screen (scorch marks fade). */
object HeatVisionPower : Superpower("laser", "Heat vision", "Laser eyes sweep across the screen", setOf(PowerKit.CAPE)) {
    override val startLines get() = HeroLines.SB_LASER
    override val landLines get() = HeroLines.SB_LAND
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(
        PowerStep(PowerPhase.TAKEOFF, 0.5f, x0, 0.08f),
        PowerStep(PowerPhase.LASER, 2.8f),
        PowerStep(PowerPhase.LAND, 0.5f, Float.NaN, 0f),
        PowerStep(PowerPhase.POSE, 1.5f),
    )
}

/** Zoom: a big frosty breath that ices the screen edge. */
object SuperBreathPower : Superpower("breath", "Super breath", "Blows a frosty gale across the screen", setOf(PowerKit.CAPE)) {
    override val startLines get() = HeroLines.SB_BREATH
    override val landLines get() = HeroLines.SB_LAND
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(
        PowerStep(PowerPhase.BREATH, 3.4f, x0, 0f),
        PowerStep(PowerPhase.POSE, 1.6f),
    )
}

/** Zoom: lifts a bus-sized notification card overhead (with your real count). */
object SuperStrengthPower : Superpower("lift", "Super strength", "Lifts a bus-sized notification card overhead", setOf(PowerKit.CAPE)) {
    override val startLines get() = HeroLines.SB_LIFT
    override val landLines get() = HeroLines.SB_LAND
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(
        PowerStep(PowerPhase.LIFT, 3.8f, x0, 0f),
        PowerStep(PowerPhase.POSE, 1.6f),
    )
}

/** Zoom: zips back and forth so fast he leaves after-images. */
object SpeedBlurPower : Superpower("blur", "Speed blur", "Zips across the screen leaving after-images", setOf(PowerKit.CAPE, PowerKit.SPEED_LINES)) {
    override val startLines get() = HeroLines.SB_SPEED
    override val landLines get() = HeroLines.SB_LAND
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random): List<PowerStep> {
        val far = if (x0 < 0.5f) 0.95f else 0.05f
        return listOf(
            PowerStep(PowerPhase.BLUR, 0.7f, far, 0f),
            PowerStep(PowerPhase.BLUR, 0.7f, 1f - far, 0f),
            PowerStep(PowerPhase.BLUR, 0.7f, far, 0f),
            PowerStep(PowerPhase.BLUR, 0.8f, 0.3f + rnd.nextFloat() * 0.4f, 0f),
            PowerStep(PowerPhase.POSE, 1.8f),
        )
    }
}

/** Bolt: hand + foot jets, palm blasters, thud landing (sputters when the suit runs low). */
object ArmourJetsPower : Superpower("jets", "Armour jets", "Hand and foot jets, palm blasters, heavy landing", setOf(PowerKit.FOOT_JETS, PowerKit.PALM_RINGS)) {
    override val startLines get() = HeroLines.AA_FLY
    override val landLines get() = HeroLines.AA_LAND
    override val crashLines get() = HeroLines.AA_CRASH
    override val fireLines get() = HeroLines.AA_FIRE
    override val crashChance get() = 0.2f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = Superpower.RocketMonkey.plan(x0, y0, crash, rnd)
}

/** Bolt: shoulder mini-rockets that fire... confetti. */
object ConfettiRocketsPower : Superpower("confetti", "Confetti rockets", "Shoulder mini-rockets fire party confetti", emptySet()) {
    override val startLines get() = HeroLines.AA_CONFETTI
    override val landLines get() = HeroLines.AA_LAND
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(
        PowerStep(PowerPhase.CONFETTI, 3.2f, x0, 0f),
        PowerStep(PowerPhase.POSE, 1.5f),
    )
}

/** Bolt: a glowing shield bubble. */
object ShieldBubblePower : Superpower("shield", "Shield bubble", "A glowing force-field bubble", emptySet()) {
    override val startLines get() = HeroLines.AA_SHIELD
    override val landLines get() = HeroLines.AA_LAND
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(
        PowerStep(PowerPhase.SHIELD, 3.4f, x0, 0f),
        PowerStep(PowerPhase.POSE, 1.4f),
    )
}

/** Bolt: the suit runs out of charge (the phone's battery is low): droops, reboots. Event only. */
object LowPowerPower : Superpower("lowpower", "Low power", "Suit runs out of charge when your phone does", emptySet()) {
    override val startLines get() = HeroLines.AA_LOW
    override val landLines get() = HeroLines.AA_REBOOT
    override val crashChance get() = 0f
    override val randomPick get() = false
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(
        PowerStep(PowerPhase.DROOP, 4.2f, x0, 0f),
        PowerStep(PowerPhase.POSE, 1.4f),
    )
}
