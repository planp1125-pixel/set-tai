package com.combo.monkeycompanion.brain

import kotlin.random.Random

// v10.24 Rollie's fixed powers. Pure data plans; poses in SkaterGirl.adapt(), board fx in SkaterRenderer. No cooldowns.

/** Her signature: an ollie, then a kickflip (the board rolls a full turn under her), then a second one, ta-da. */
object KickflipShowPower : Superpower("kickflip", "Kickflip show", "Ollie, kickflip, double kickflip, then a ta-da", emptySet()) {
    override val startLines get() = listOf("Watch this! 🛹", "Kickflip time! ✨", "One, two… flip! 🛹")
    override val landLines get() = listOf("Landed it! 😎🛹", "Clean! Did you see that? ✨", "Ten out of ten, thank you 🙌")
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(PowerStep(PowerPhase.POSE, 3.2f))
}

/** A low crouch grind on the board, sparks flying off the truck. */
object GrindPower : Superpower("grind", "Grind", "A low crouching grind with sparks off the truck", emptySet()) {
    override val startLines get() = listOf("Grind! 🔥🛹", "Sparks incoming ✨")
    override val landLines get() = listOf("Smooth as butter 😎", "Rail? What rail? I made my own 😆")
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(PowerStep(PowerPhase.POSE, 3.0f))
}

/** Hops and spins a full 360 with the board stuck to her feet. */
object BoardSpinPower : Superpower("boardspin", "Board spin", "Jumps and spins a full 360 with her board", emptySet()) {
    override val startLines get() = listOf("Spin it! 🌀🛹", "360, here we go!")
    override val landLines get() = listOf("Whoa, dizzy… but landed! 😵‍💫😄", "Full circle! 🛹")
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(PowerStep(PowerPhase.POSE, 2.6f))
}
