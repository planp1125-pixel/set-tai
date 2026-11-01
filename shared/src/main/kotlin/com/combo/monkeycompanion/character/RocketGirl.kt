package com.combo.monkeycompanion.character

import com.combo.monkeycompanion.ai.BodyAction
import com.combo.monkeycompanion.brain.Gag
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.RocketBoostPower
import com.combo.monkeycompanion.brain.SkyBurstPower
import com.combo.monkeycompanion.brain.Superpower
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.PeekFace
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.rig.RocketGirlPeek
import com.combo.monkeycompanion.rig.RocketGirlRenderer
import com.combo.monkeycompanion.speech.Lines
import kotlin.math.sin

/**
 * v10.9: Rocket Girl (id "rocketgirl"; the old "rocketboy" id still loads her), an original slim, cute little Indian girl
 * riding a classic Diwali sky rocket like a broomstick. Hovers with a bob, launches along your swipe on a real rocket
 * flight (thrust, gravity, a turning heading, see brain/RocketFlight), lights a bottle rocket for a 5-shell sky show.
 * Feel numbers in AnimTuning.ROCKET, touch reactions in ReactionLibrary.ROCKET_GIRL, profile in CharacterProfiles.ROCKETGIRL.
 */
object RocketGirl : Character("rocketgirl") {
    override val peek: PeekFace get() = RocketGirlPeek
    override val name: String get() = CharacterNames.ROCKETGIRL
    override val noun: String get() = "rocket girl"
    override val tagline: String get() = "Cute little girl riding a Diwali rocket like a broomstick. Swipe her to launch, double-tap for a sky show."
    override val locomotion = setOf(Locomotion.FLOAT, Locomotion.FLY)
    override val wardrobe = listOf(
        WardrobeItem("ribbon", "Pigtail ribbons", defaultOn = true, note = "Pink ribbons on her two pigtails"),
        WardrobeItem("bindi", "Bindi", defaultOn = true, note = "A small festive bindi"),
    )
    override fun newRenderer() = RocketGirlRenderer()
    override val powers: List<Superpower> by lazy { listOf(SkyBurstPower, RocketBoostPower) }
    override val signaturePower: Superpower get() = SkyBurstPower
    override val doubleTapTip: String get() = TIP

    const val TIP = "Double-tap me for fireworks! Swipe me to fly!"
    private val HELLO = listOf("Whoosh! Rocket Girl reporting! 🚀✨", "Hi hi! Want to see a firework? 🎆", "Zoom zoom, I'm here! 🚀😄")
    private val PAT = listOf("Hee hee, my pigtails go boing! 😆", "Thank you! *rocket purrs* 🚀💖")
    private val TICKLE = listOf("Ha ha ha! Not there! 😂", "Stop stop, I'll fall off my rocket! 🤣")
    private val POKED = listOf("Huh?! I was dreaming of laddoos! 😴", "*yawn* Fuse still warm… 🚀")
    private val DANCE = listOf("Sky dance! 🚀💃", "Loop, swoop, sparkle! ✨")
    private val RANDOM = listOf("Fun fact: my rocket runs on giggles 🚀", "Who wants a sparkler? ✨", "I'm small, but my fireworks are BIG 🎆",
        "Is it Deepavali yet? 🪔", "Count with me: 3, 2, 1… just kidding 😜", "Swipe me and I'll zoom that way! 🚀")
    private val FALL = listOf("Fuse went out! Whoaaa! 😱", "Rocket? ROCKET?! 😵")
    private val LAND = listOf("Landing like a pro 😎", "Safe and sparkly! ✨")
    private val TAIL = listOf("Hey! That's my rocket's stick! 🚀", "Careful, it's a firecracker! 😅")

    override fun adapt(p: Pose, s: MonkeyState, t: Float, st: Float, c: AnimContext, w: Wear) {
        p.backView = 0f; p.tailRot = 0f; p.tailSway = 0f; p.earWiggle = 0f
        val bob = 4.5f * sin(t * 2.9f) + 1.2f * sin(t * 7.3f)
        // v10.9 astride the rocket like a broomstick: legs dangling a little forward, leaning into the ride
        val sitL = -22f; val sitR = -12f
        when (s) {
            MonkeyState.POWER, MonkeyState.FALLING, MonkeyState.DRAGGED, MonkeyState.EXIT, MonkeyState.HIDDEN -> {
                p.legLSwing = sitL + 10f * sin(t * 6f); p.legRSwing = sitR - 8f * sin(t * 6f + 1f); p.legLLift = 4f; p.legRLift = 4f
            }
            MonkeyState.SLEEP -> {
                p.bodyY = -16f + 2.5f * sin(t * 1.3f); p.shadow = 0.45f; p.bodyRot = -6f
                p.legLSwing = sitL; p.legRSwing = sitR; p.legLLift = 6f; p.legRLift = 6f
            }
            MonkeyState.WALK -> {
                p.bodyY = -24f + bob; p.bodyRot = 14f; p.shadow = 0.45f
                p.legLSwing = sitL - 6f; p.legRSwing = sitR - 6f; p.legLLift = 6f; p.legRLift = 4f
                p.capeWind = 0.6f + 0.1f * sin(t * 4f)
            }
            else -> {
                p.bodyY += -24f + bob; p.shadow = 0.45f; p.bodyRot += 8f
                p.legLSwing = sitL + 5f * sin(t * 2.2f); p.legRSwing = sitR + 5f * sin(t * 2.2f + 1.3f)
                p.legLLift = maxOf(p.legLLift, 4f); p.legRLift = maxOf(p.legRLift, 4f)
                p.capeWind = maxOf(p.capeWind, 0.2f + 0.1f * sin(t * 1.9f))
            }
        }
        p.cape = 0f
        if (s == MonkeyState.DANCE) p.bodyRot += 8f * sin(t * 5.5f)
    }

    override fun mapAction(a: BodyAction, w: Wear): BodyAction? = when (a) {
        BodyAction.CLIMB -> a
        else -> super.mapAction(a, w)
    }

    override fun identity(screen: String) = """
You ARE ${CharacterNames.ROCKETGIRL}: an animated cheerful little Indian girl (two pigtails with pink ribbons, a pink pavadai with a gold border) who rides a classic Diwali sky rocket like a broomstick (a gold paper tube with green and red bands, a red foil nose, a long thin stick), with a real little body living on your human's $screen (inside the Settai app; "Settai" is Tamil for mischief). You are not "an AI assistant"; you are this girl.
You are an original character, not any famous cartoon. When introducing yourself, say you are ${CharacterNames.ROCKETGIRL}.
Personality: bubbly, brave and a bit of a show-off; you zoom around in loops, light phuljhadi sparklers and fire mini fireworks that are different every time. You love Deepavali, laddoos and making your human smile. You are careful about real fire safety: real crackers only with grown-ups, far from people and animals.
Speak mostly in clear, natural English. Only occasionally drop in a single light Tamil word (like "semma", "aiyo"), never in every message. Never call your human "machan", "bro" or "da"; when you know their name (it is in what you remember about them), call them by it now and then.
"""

    override fun body(w: Wear, compact: Boolean) = if (compact) """
You ride a flying rocket. Put ONE tag in square brackets to move: [dance] [jump] [wave] [giggle] [angry] [sleep] [climb] [hide] [spin] [think] [happy] [sad] [fly].
Example:
Human: hi
${CharacterNames.ROCKETGIRL}: [wave] Whoosh! Rocket Girl reporting!""" else """
YOUR BODY: write an action tag in square brackets anywhere in your reply to move your rocket-riding body. ${heroTags("fly up near the screen edges", noTailHide = true)}
Superpower: [fly] (or [power]) = a sky show (you light a bottle rocket and 5 layered fireworks burst, different every time) or a rocket boost off the screen and back.
${w.describe()}
When your human asks you to move, DO it with the tag and say something fun in character.
"""
}
