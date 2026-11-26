package com.combo.monkeycompanion.brain

import kotlin.random.Random

// v10.55 Meenu's fixed powers. Pure data plans; poses in Mermaid.adapt(), bubbles / splash / pearl fx in MermaidRenderer.
// No cooldowns, no frame-loop Random.

/** Her signature: blows a giant bubble that floats up and pops into a shower of tiny bubbles. */
object BubbleShowPower : Superpower("bubbleshow", "Bubble show", "Blows a giant bubble that floats up and pops into a shower of tiny bubbles", emptySet()) {
    override val startLines get() = listOf("Watch my biggest bubble! 🫧", "Bubble time! ✨", "Hold your breath… 🫧")
    override val landLines get() = listOf("Pop! Bubble shower! 🫧✨", "Did you see how big it was?! 😄", "Ten out of ten bubbles 🫧")
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(PowerStep(PowerPhase.POSE, 3.4f))
}

/** Curls her tail back and slaps it down: a big water splash with flying drops. */
object TailSplashPower : Superpower("tailsplash", "Tail splash", "Curls her tail back and slaps it down in a big splash", emptySet()) {
    override val startLines get() = listOf("Splash attack! 🌊", "Ready… get wet! 💦", "Tail power! 🧜‍♀️")
    override val landLines get() = listOf("Oops, did I wet your screen? 😆💦", "Best splash ever 🌊", "Hehe, sorry not sorry 💦")
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(PowerStep(PowerPhase.POSE, 2.6f))
}

/** Lifts a magic pearl over her head; it glows, with sparkles spinning around it. */
object PearlGlowPower : Superpower("pearlglow", "Pearl glow", "Lifts a magic pearl over her head and it glows with sparkles", emptySet()) {
    override val startLines get() = listOf("My magic pearl! ✨", "Shine, little pearl! 🤍", "Ooh, sparkly time ✨")
    override val landLines get() = listOf("Pretty, right? ✨", "My most precious treasure 🤍", "Sparkle mode: done 😌")
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(PowerStep(PowerPhase.POSE, 3.0f))
}
