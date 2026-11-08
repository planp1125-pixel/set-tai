package com.combo.monkeycompanion.brain

import kotlin.random.Random

// v10.8/v10.9 Skye's powers (same sealed family as the other powers).

/**
 * Skye's sky show (v10.9): she drops low beside a glass bottle, lights the rocket standing in it with her
 * sparkler, and 5 bottle rockets whistle up on wobbly paths and burst into layered multi-ring fireworks at different
 * heights, a different recipe each (see rig/SkyShow, rig/Fireworks, rig/FireworkRecipes).
 */
object SkyBurstPower : Superpower("skyburst", "Sky show", "Lights a bottle rocket: 5 layered fireworks burst across the sky, different every time", emptySet()) {
    override val startLines get() = listOf("Light the fuse! 🎆", "Firework time! Look up! 🎇", "Pataka! Watch the sky! ✨", "3… 2… 1… BOOM! 🎆")
    override val landLines get() = listOf("Was that semma or what?! 🎆", "Happy Deepavali, every day! 🪔", "Again? I have more! 🎇")
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(
        PowerStep(PowerPhase.TAKEOFF, 0.5f, x0, 0.03f),
        PowerStep(PowerPhase.CONFETTI, com.combo.monkeycompanion.rig.SkyShow.SHOW_SECS),
        PowerStep(PowerPhase.LAND, 0.6f, Float.NaN, 0f),
        PowerStep(PowerPhase.POSE, 1.2f),
    )
}

/**
 * Skye: full throttle! Zooms off one side of the screen on a curved arc and comes whistling back in from the
 * other side (the rare "boost" touch reaction uses this too).
 */
object RocketBoostPower : Superpower("boost", "Rocket boost", "Zooms off the screen and whistles back in on a big arc", setOf(PowerKit.SPEED_LINES)) {
    override val startLines get() = listOf("BOOST! Hold on— 🚀💨", "Full throttle! Be right back! 🚀", "Zoooom! 🚀")
    override val landLines get() = listOf("…and I'm back! 😎🚀", "Did you miss me? 🚀✨", "Whistle-landing! 🎵")
    override val crashLines get() = listOf("Oops, too fast! 😵‍💫🚀")
    override val crashChance get() = 0.1f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random): List<PowerStep> {
        val right = x0 < 0.5f
        val off = if (right) 1.25f else -0.25f
        val back = if (right) -0.25f else 1.25f
        return listOf(
            PowerStep(PowerPhase.TAKEOFF, 0.35f, x0, 0.18f),
            PowerStep(PowerPhase.FLY, 0.9f, off, 0.55f + rnd.nextFloat() * 0.2f, 0.35f),
            PowerStep(PowerPhase.FLY, 0.05f, back, 0.6f, 0f),
            PowerStep(PowerPhase.FLY, 1.2f, 0.3f + rnd.nextFloat() * 0.4f, 0.3f, -0.4f),
            PowerStep(PowerPhase.LAND, 0.7f, Float.NaN, 0f),
            PowerStep(PowerPhase.POSE, 1.0f),
        )
    }
}
