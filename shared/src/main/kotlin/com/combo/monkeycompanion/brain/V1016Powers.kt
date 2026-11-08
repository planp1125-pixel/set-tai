package com.combo.monkeycompanion.brain

import kotlin.random.Random

// v10.16 Tutty (the little devil) black-magic powers. Pure data plans, same sealed family as the other powers.
// Poses and the red smoke / portal / sparks are drawn by the devil's own adapt() + renderer (keyed on the power id + phase).

/** Where the devil reappears for a vanish from [x0]: always the OTHER half of the screen, a little in from the edge. */
fun devilOppositeX(x0: Float): Float = if (x0 < 0.5f) 0.88f else 0.12f

/** His signature: crouch (anticipation), POOF into red smoke, then pop out of a red swirl portal on the other side. */
object VanishPortalPower : Superpower("vanish", "Vanish & portal", "Vanishes in a puff of red smoke and pops out of a portal on the other side", emptySet()) {
    override val startLines get() = listOf("Now you see me… 😈", "Abra-ka-DEVIL! ✨", "Watch closely… *snap* 😈", "Tutty magic, one… two…")
    override val landLines get() = listOf("…now you DON'T! Over here! 😈✨", "Ta-daa! Missed me? 😏", "Portal travel, zero traffic 😎", "Hee-hee! Other side! 👋😈")
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random): List<PowerStep> {
        val y = y0.coerceIn(0f, 0.8f)
        return listOf(
            PowerStep(PowerPhase.DROOP, 0.45f, x0, y),                     // anticipation: crouch, tail coils, eyes squint
            PowerStep(PowerPhase.TELEPORT, 1.7f, devilOppositeX(x0), y),   // smoke out (first half), portal in (second half)
            PowerStep(PowerPhase.POSE, 1.2f),                              // follow-through: little bounce + tail flick, "ta-daa"
        )
    }
}

/** Blink-hops: three quick teleport hops to random spots, then a bow. */
object TeleportHopPower : Superpower("devilhop", "Teleport hops", "Pops between random spots on the screen", emptySet()) {
    override val startLines get() = listOf("Catch me if you can! 😈", "Here… there… everywhere! ✨", "Hop hop POP! 😈")
    override val landLines get() = listOf("Dizzy? Not me 😏", "Okay, I'm here now. Probably 😈")
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random): List<PowerStep> = listOf(
        PowerStep(PowerPhase.TELEPORT, 0.9f, 0.1f + rnd.nextFloat() * 0.8f, rnd.nextFloat() * 0.7f),
        PowerStep(PowerPhase.TELEPORT, 0.9f, 0.1f + rnd.nextFloat() * 0.8f, rnd.nextFloat() * 0.7f),
        PowerStep(PowerPhase.TELEPORT, 0.9f, 0.15f + rnd.nextFloat() * 0.7f, rnd.nextFloat() * 0.4f),
        PowerStep(PowerPhase.POSE, 1.0f),
    )
}

/** Lifts a nearby crew buddy into the air with red sparkles (floats himself when he's alone). */
object FloatBuddyPower : Superpower("floatbuddy", "Float a buddy", "Lifts a nearby friend into the air with red sparkles", emptySet()) {
    override val startLines get() = listOf("Levitato-buddy-o! ✨😈", "Up you go, friend! 🪄", "Hold still… floaty time ✨")
    override val landLines get() = listOf("And gently… down. Safe landing 😇", "See? Floating is fun 😈✨")
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(PowerStep(PowerPhase.POSE, 3.6f))
}

/** Crackling red spell sparks from both hands, a little fizzle-pop at the end. */
object SpellSparksPower : Superpower("spellsparks", "Spell sparks", "Red magic sparks crackle from his fingers", emptySet()) {
    override val startLines get() = listOf("Fizz… crackle… ✨😈", "Secret spell, don't tell anyone 🤫", "Finger fireworks! ✨")
    override val landLines get() = listOf("*pop* …oops, that one tickled 😆", "Spell complete! I think 😏")
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(PowerStep(PowerPhase.POSE, 3.0f))
}

/** Two little shadow clones slide out beside him, copy his wiggle, then melt away. */
object ShadowClonePower : Superpower("shadowclone", "Shadow clones", "Two shadow copies copy his dance, then melt away", emptySet()) {
    override val startLines get() = listOf("Three of me? Trouble x3 😈😈😈", "Shadow friends, come out! 🌑", "Me, myself and me ✨")
    override val landLines get() = listOf("Shadows back to bed 🌑😴", "Only one of me now. For now 😏")
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(PowerStep(PowerPhase.POSE, 3.2f))
}

/** Floats up high (no hat, no broom, just magic), cross-legged, then drifts back down. */
object DevilLevitatePower : Superpower("devilfloat", "Levitate", "Floats high up, cross-legged, then drifts back down", emptySet()) {
    override val startLines get() = listOf("Who needs wings? 😈", "Floaty floaty… ✨", "Gravity is just a suggestion 😏")
    override val landLines get() = listOf("Soft landing, like a feather 🪶", "Back down. Did you miss me? 😈")
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(
        PowerStep(PowerPhase.DROOP, 0.4f, x0, y0.coerceIn(0f, 0.8f)),
        PowerStep(PowerPhase.FLY, 1.6f, x0.coerceIn(0.15f, 0.85f), 0.55f, 0.08f),
        PowerStep(PowerPhase.POSE, 1.4f),
        PowerStep(PowerPhase.FLY, 1.2f, x0.coerceIn(0.15f, 0.85f), y0.coerceIn(0f, 0.8f), -0.05f),
        PowerStep(PowerPhase.POSE, 0.6f),
    )
}

/** v10.17: winds up and throws a red-orange fireball across the screen (it fizzles into sparks). */
object FireballPower : Superpower("fireball", "Fireball throw", "Winds up and hurls a little fireball across the screen", emptySet()) {
    override val startLines get() = listOf("Hot hot HOT! 🔥😈", "Fireball, coming through! 🔥", "Catch! …no, don't catch 🔥")
    override val landLines get() = listOf("Fizzle! Nobody got burnt 😇🔥", "Okay, that was a warm one 😏")
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(PowerStep(PowerPhase.POSE, 2.6f))
}

/** v10.17: juggles three little balls, faster and faster, then catches them all. */
object JugglePower : Superpower("devjuggle", "Juggling balls", "Juggles three balls and catches them all", emptySet()) {
    override val startLines get() = listOf("Watch this! 🤹😈", "One, two, three… magic hands! 🤹")
    override val landLines get() = listOf("Didn't drop a single one 😎", "Ta-daa! Clap now 👏😈")
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(PowerStep(PowerPhase.POSE, 3.4f))
}
