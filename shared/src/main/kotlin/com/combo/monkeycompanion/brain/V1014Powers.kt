package com.combo.monkeycompanion.brain

import kotlin.random.Random

// v10.14 Hollow Man (invisible dancer) and Surfing Girl powers (same sealed family as the other powers).
// Each plan is pure data; the dance/surf poses live in the characters' adapt() (keyed on the power id + phase).

/** Hollow Man's signature: a hat-tip wind-up, then a long, smooth moonwalk glide across the floor and a toe-stand finish. */
object MoonwalkPower : Superpower("moonwalk", "Moonwalk", "Glides backwards across the whole screen, then freezes up on his toes", emptySet()) {
    override val startLines get() = listOf("Hee-hee! Watch the floor… 🕺", "Smooth mode: ON 🎩", "Shamone! Backwards we go ✨", "Floor is ice, I am nice 😎")
    override val landLines get() = listOf("*toe stand* …and FREEZE. 🕴️", "Semma smooth, no? 😎", "The floor is still shining ✨")
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random): List<PowerStep> {
        val far = if (x0 < 0.5f) 0.9f else 0.1f
        return listOf(
            PowerStep(PowerPhase.DROOP, 0.6f, x0, 0f),                // anticipation: crouch + hat tip (DROOP = stay put, no fx)
            PowerStep(PowerPhase.FLY, 3.4f, far, 0f, 0f),             // the glide (level, eased in and out)
            PowerStep(PowerPhase.POSE, 1.3f),                         // toe stand
        )
    }
}

/** Hollow Man: a fast double spin that ends on his toes (clothes swirl, hat stays put by a miracle). */
object DanceSpinPower : Superpower("dancespin", "Spin & freeze", "A double spin, a kick and a toe-stand freeze", emptySet()) {
    override val startLines get() = listOf("Spin time! 🌀", "Round and round! 🕺", "Hoo! 🌀🎩")
    override val landLines get() = listOf("*freeze* 🕴️", "Dizzy? Me? Never 😵‍💫… okay little bit")
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(PowerStep(PowerPhase.POSE, 2.9f))
}

/** Hollow Man: tips the hat to you (nobody under it), bows, puts it back. */
object HatTipPower : Superpower("hattip", "Hat tip", "Lifts his hat to you: there is nobody under it", emptySet()) {
    override val startLines get() = listOf("For you, my friend 🎩", "*tips hat* Vanakkam 🎩", "Gentleman mode 🎩✨")
    override val landLines get() = listOf("Yes, nobody inside. Surprise! 👻🎩", "Hat back on. Looking sharp 😎")
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(PowerStep(PowerPhase.POSE, 2.9f))
}

/** Hollow Man: the big impossible forward lean, feet glued to the floor, then he snaps back up. */
object GravityLeanPower : Superpower("lean", "Impossible lean", "Leans way forward on glued feet, then springs back", emptySet()) {
    override val startLines get() = listOf("Gravity? Never heard of it 😎", "Leeeean… 🕴️", "Physics, please look away 🙈")
    override val landLines get() = listOf("*snaps back* Ta-da! ✨", "Feet still glued 👞✨")
    override val crashChance get() = 0f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random) = listOf(PowerStep(PowerPhase.POSE, 2.9f))
}

/**
 * Surfing Girl's signature. v10.22: a REAL surf ride along the bottom of the screen (the water), not a thrust flight:
 * she paddles, pops up, gathers speed down the wave face, carves up and down it (leaning into each turn), cuts back,
 * and glides to a soft stop as the water slows her. One continuous glide per leg ([PowerStep.glide]); no speed lines.
 */
object BigWavePower : Superpower("bigwave", "Big wave ride", "Paddles in and surfs a long, carving ride across your screen", emptySet()) {
    override val startLines get() = listOf("Surf's UP! 🌊🏄‍♀️", "Here comes a big one! 🌊", "Paddle paddle… GO! 🏄‍♀️")
    override val landLines get() = listOf("Woohoo! Best wave ever! 🌊✨", "Again? Again! 🏄‍♀️", "Hair is wet, mood is set 😎🌊")
    override val crashLines get() = listOf("*SPLOOSH* …wipeout! I'm okay! 😵💦", "Board went left, I went right 🤣🌊")
    override val crashChance get() = 0.15f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random): List<PowerStep> {
        val right = x0 < 0.5f
        val far = if (right) 0.92f else 0.08f
        val back = 0.35f + rnd.nextFloat() * 0.3f   // where the cut-back ride ends
        return listOf(
            PowerStep(PowerPhase.TAKEOFF, 0.6f, x0 + (if (right) 0.02f else -0.02f), 0.03f),   // paddle + pop up
            PowerStep(PowerPhase.FLY, 5.0f, far, 0.03f, glide = 3f, swell = 0.13f),           // the ride: three carves
            PowerStep(PowerPhase.FLY, 3.8f, back, 0.03f, glide = 2f, swell = 0.10f),          // cut back, ride the other way
            PowerStep(PowerPhase.LAND, 0.6f, Float.NaN, 0f),
            PowerStep(PowerPhase.POSE, 1.0f),
        )
    }
}

/** Surfing Girl: an aerial off the lip, a full flip, and (sometimes) a splashy wipeout gag. */
object AerialFlipPower : Superpower("aerial", "Aerial flip", "Launches off a wave lip into a full flip; sometimes a wipeout", emptySet()) {
    override val startLines get() = listOf("Watch this trick! 🏄‍♀️", "Air time! ✨", "One… two… FLIP! 🤸‍♀️")
    override val landLines get() = listOf("Stuck it! 😎", "Ten out of ten, judges? 🏆", "Semma landing 🌊")
    override val crashLines get() = listOf("WIPEOUT! *glug glug* 😵💦", "I meant to do that… 🤣🌊", "Board 1, me 0 😅")
    override val crashChance get() = 0.3f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random): List<PowerStep> {
        val dx = if (x0 < 0.5f) 0.18f else -0.18f
        return listOf(
            PowerStep(PowerPhase.TAKEOFF, 0.4f, x0, 0.03f),
            PowerStep(PowerPhase.FLY, 1.6f, x0 + dx, 0.2f, glide = 0.5f, swell = 0.0f),   // v10.22: glide up the face to the lip
            PowerStep(PowerPhase.FLY, 0.9f, x0 + dx * 1.5f, 0.42f, 0.25f),   // the flip happens here
            PowerStep(PowerPhase.LAND, 0.6f, Float.NaN, 0f),
            PowerStep(PowerPhase.POSE, 1.1f),
        )
    }
}

/** Surfing Girl: carves a quick S across the screen, spraying a wall of water. */
object SprayCarvePower : Superpower("carve", "Spray carve", "Carves a deep turn on the water and throws a wall of spray", emptySet()) {
    override val startLines get() = listOf("Carve time! 🌊", "Spray for you! 💦", "Hold on to your phone! 🌊😜")
    override val landLines get() = listOf("Did you get wet? Hehe 💦", "Smooth like coconut water 🥥")
    override val crashChance get() = 0.1f
    override fun plan(x0: Float, y0: Float, crash: Boolean, rnd: Random): List<PowerStep> {
        val far = if (x0 < 0.5f) 0.8f else 0.2f
        return listOf(
            PowerStep(PowerPhase.TAKEOFF, 0.4f, x0, 0.03f),
            PowerStep(PowerPhase.FLY, 2.6f, far, 0.03f, glide = 1f, swell = 0.16f),   // v10.22: one deep carve, spray off the rail
            PowerStep(PowerPhase.LAND, 0.5f, Float.NaN, 0f),
            PowerStep(PowerPhase.POSE, 0.9f),
        )
    }
}
