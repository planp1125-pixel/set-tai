package com.combo.monkeycompanion.character

import com.combo.monkeycompanion.ai.BodyAction
import com.combo.monkeycompanion.brain.BubbleShowPower
import com.combo.monkeycompanion.brain.Gag
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.PearlGlowPower
import com.combo.monkeycompanion.brain.Superpower
import com.combo.monkeycompanion.brain.TailSplashPower
import com.combo.monkeycompanion.engine.swipe.SwipeTravel.bell
import com.combo.monkeycompanion.engine.swipe.SwipeTravel.ss
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.MermaidPeek
import com.combo.monkeycompanion.rig.MermaidRenderer
import com.combo.monkeycompanion.rig.PeekFace
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.speech.Lines
import kotlin.math.sin

/**
 * v10.55 Meenu (id "mermaid", from "meen" = fish): an ORIGINAL cute little mermaid girl. Your screen is her pool: she
 * floats and swims (never walks or climbs), her tail beats like a real swim stroke, her long hair flows, and bubbles
 * always rise from her (a trail when she swims fast). Fixed powers: bubble show (main), tail splash, pearl glow.
 *
 * Her tail is drawn on the rig's legs: the average leg swing bends it, the leg lift curls it (MermaidRenderer).
 * All animation is a pure function of time (no frame-loop Random).
 */
object Mermaid : Character("mermaid") {
    override val peek: PeekFace get() = MermaidPeek
    override val name: String get() = CharacterNames.MERMAID
    override val noun: String get() = "little mermaid"
    override val tagline: String get() = "Cute little mermaid. Your screen is her pool: she swims, bubbles rise. Double-tap: a giant bubble show!"
    override val locomotion = setOf(Locomotion.FLOAT, Locomotion.FLY)
    override val vanishStyle get() = com.combo.monkeycompanion.engine.stagefx.VanishStyle.WHITE_SMOKE
    override val wardrobe = listOf(
        WardrobeItem("pearls", "Pearl string", defaultOn = true, note = "A string of pearls in her hair"),
        WardrobeItem("starfish", "Starfish clip", defaultOn = true, note = "A little coral starfish hair clip"),
        WardrobeItem("bubbles", "Bubbles", defaultOn = true, note = "Bubbles rising from her as she swims"),
    )
    override fun newRenderer() = MermaidRenderer()
    override val powers: List<Superpower> by lazy { listOf(BubbleShowPower, TailSplashPower, PearlGlowPower) }
    override val signaturePower: Superpower get() = BubbleShowPower
    override val doubleTapTip: String get() = "Double-tap me for a giant bubble show! 🫧"

    private val HELLO = listOf("Hiii! Welcome to my aquarium! 🧜‍♀️", "Blub blub! I mean, hello! 🫧", "Hi hi! The water's lovely today 🌊")
    private val PAT = listOf("Hehe, my hair is all wet, sorry! 😆", "Aww thank you! *happy tail swish* 🧜‍♀️")
    private val TICKLE = listOf("Hahaha not the fins! 🤣", "Stop stop, I'll blow bubbles everywhere! 😂🫧")
    private val POKED = listOf("Huh?! I was dreaming of shiny shells 😴", "*yawn* Five more bubbles… 🫧")
    private val DANCE = listOf("Swim dance! 🧜‍♀️💃", "Swish swish, twirl! 🌊", "Bubble boogie! 🫧")
    private val RANDOM = listOf("Your phone is my aquarium. Please don't drop it 🧜‍♀️", "I collect shiny things. Your screen counts ✨",
        "Fun fact: I can't walk. I swim everywhere 🌊", "Is that a pearl or a notification? 🤍", "Bubbles are just tiny hugs from the sea 🫧",
        "Don't forget to drink water. I live in it, I know 💧")
    private val FALL = listOf("Whoaaa, where's the water?! 😱", "Help! I'm a fish out of water! 😵")
    private val LAND = listOf("*splash* Safe! 🌊", "Floating again 🫧")
    private val GRUMPY = listOf("Hmph. Even mermaids get grumpy 😤", "No bubbles for you. Okay, one 🫧")
    private val HIDE = listOf("Hiding behind a big wave! 🌊🙈", "You can't see me, I'm a rock 🪨")
    override val voice: LineVoice by lazy {
        SimpleVoice("little mermaid", mapOf(Lines.HELLO to HELLO, Lines.PAT to PAT, Lines.TICKLE to TICKLE, Lines.POKED_AWAKE to POKED,
            Lines.DANCE to DANCE, Lines.RANDOM to RANDOM, Lines.FALL to FALL, Lines.LAND to LAND, Lines.GRUMPY to GRUMPY,
            Lines.TAIL to TICKLE, Lines.HIDE to HIDE))
    }

    /** Floating in water: a slow, soft bob. */
    fun bob(t: Float): Float = 3f * sin(t * 1.6f + 0.4f) + 0.8f * sin(t * 3.7f)

    override fun adapt(p: Pose, s: MonkeyState, t: Float, st: Float, c: AnimContext, w: Wear) {
        p.backView = 0f; p.tailRot = 0f; p.tailSway = 0f; p.earWiggle = 0f; p.surf = 0f; p.skate = 0f
        p.cape = 0f; p.jets = 0f; p.flipS = 0f; p.flipC = 1f
        p.dvSpell = 0f; p.dvAura = 0f; p.surfSpray = 0f
        // the tail is one piece: both "legs" move together (renderer bends the tail by their average)
        var tail = 0.5f * (p.legLSwing + p.legRSwing)
        var curl = 0f
        val floaty = s != MonkeyState.HIDDEN
        if (floaty) { p.bodyY += -10f + bob(t); p.shadow = 0.25f }
        when (s) {
            MonkeyState.SLEEP -> {   // curled up, floating, tail tucked to the side
                p.bodyY = -8f + 1.5f * sin(t * 1.1f); p.shadow = 0.25f; p.eyeOpen = 0f
                tail = -40f; curl = 14f; p.bodyRot = -8f
            }
            MonkeyState.WALK -> {    // swimming: strong tail beats, arms stroke slowly
                tail = 22f * sin(t * 5f)
                p.armLRaise = 70f + 35f * sin(t * 2.5f); p.armRRaise = 70f + 35f * sin(t * 2.5f + 3.14f)
                p.armLElbow = -30f; p.armRElbow = -30f
            }
            MonkeyState.DANCE -> { tail = 30f * sin(t * 4f); p.bodyRot += 6f * sin(t * 2f) }
            MonkeyState.FALLING, MonkeyState.DRAGGED -> {
                tail = 28f * sin(t * 12f)
                p.armLRaise = 120f + 20f * sin(t * 9f); p.armRRaise = 120f + 20f * sin(t * 9f + 1f); p.mouthOpen = 0.5f
            }
            MonkeyState.POWER -> { val r = power(p, t, c); if (!r.isNaN()) tail = r; curl = p.legLLift }
            else -> tail = 0.4f * tail + 8f * sin(t * 1.4f)   // idle: a lazy swish
        }
        p.legLSwing = tail; p.legRSwing = tail
        p.legLLift = curl; p.legRLift = curl
    }

    /** Pose for a power; returns the tail swing to use (NaN = keep the usual one). */
    private fun power(p: Pose, t: Float, c: AnimContext): Float {
        val k = c.powerK
        p.legLLift = 0f
        return when (c.power?.id) {
            "bubbleshow" -> {
                p.dvSpell = k.coerceIn(0.01f, 1f)
                if (k < 0.55f) {   // hands at the mouth, cheeks puffed, blowing
                    val e = ss(0f, 0.12f, k)
                    p.armLRaise = 75f * e; p.armRRaise = 75f * e; p.armLElbow = -115f * e; p.armRElbow = -115f * e
                    p.mouthOpen = 0.25f; p.mouthSmile = 0.2f; p.blush = 0.5f; p.eyeOpen = 1.15f; p.lookY = -0.6f
                    10f * sin(t * 2f)
                } else {           // pop! arms up, happy
                    val e = ss(0.55f, 0.65f, k)
                    p.armLRaise = 75f + 75f * e; p.armRRaise = 75f + 75f * e; p.armLElbow = -115f + 95f * e; p.armRElbow = -115f + 95f * e
                    p.mouthSmile = 1f; p.eyesHappy = if (k > 0.62f) 1f else 0f
                    18f * sin(t * 6f)
                }
            }
            "tailsplash" -> {
                val wind = ss(0f, 0.35f, k) * (1f - ss(0.35f, 0.45f, k))
                val slap = ss(0.35f, 0.45f, k)
                p.surfSpray = bell(0.38f, 1f, k)
                p.legLLift = 22f * wind
                p.bodyRot = -10f * wind + 8f * slap * (1f - ss(0.7f, 1f, k))
                p.armLRaise = 100f; p.armRRaise = 100f; p.armLElbow = -15f; p.armRElbow = -15f
                p.mouthOpen = 0.35f * slap; p.mouthSmile = 1f
                if (k > 0.5f) p.eyesHappy = 1f
                55f * wind - 45f * slap * (1f - ss(0.6f, 1f, k))
            }
            "pearlglow" -> {
                val e = bell(0f, 1f, k).let { if (k > 0.15f && k < 0.85f) 1f else it }
                p.dvAura = e
                p.armLRaise = 150f * e; p.armRRaise = 150f * e; p.armLElbow = -40f * e; p.armRElbow = -40f * e
                p.bodyY += -6f * e
                if (k in 0.2f..0.7f) p.eyeOpen = 0.25f else if (k > 0.7f) p.eyesHappy = 1f
                p.mouthSmile = 1f
                6f * sin(t * 1.5f)
            }
            else -> Float.NaN
        }
    }

    override fun mapAction(a: BodyAction, w: Wear): BodyAction? = when (a) {
        BodyAction.CLIMB -> BodyAction.JUMP
        BodyAction.TAIL_HIDE -> BodyAction.HIDE
        BodyAction.SKATE, BodyAction.CHICKEN, BodyAction.GADGET -> null
        else -> a
    }
    override fun mapGag(g: Gag, w: Wear): Gag = when (g) {
        Gag.SLIP, Gag.TAIL_HIDE -> Gag.FAKE_SLEEP
        else -> g
    }

    override fun identity(screen: String) = """
You ARE ${CharacterNames.MERMAID}: a cute little mermaid girl who lives on your human's $screen (inside the Set-Tai app). The screen is your pool: you swim and float in it, and bubbles rise from you. You have long wavy black hair with a string of pearls and a little coral starfish clip, a sky-blue top, gold bangles, and a lavender-to-pink tail with golden fins.
You are an original character, not any famous cartoon mermaid. When introducing yourself, say you are ${CharacterNames.MERMAID}.
Personality: giggly, curious and a little dramatic; you collect shiny things (pearls, shells, buttons), love sea jokes and bubbles, and call the phone "my aquarium". You can't walk, only swim. You care about the ocean: no plastic in the sea.
Speak mostly in clear, simple English. Only occasionally drop in a single light Tamil word, never in every message.
"""

    override fun body(w: Wear, compact: Boolean) = if (compact) """
You are a cute little mermaid swimming in the screen. Put ONE tag in square brackets to move: [dance] [jump] [wave] [giggle] [angry] [sleep] [hide] [spin] [think] [happy] [sad] [power].
Example:
Human: hi
${CharacterNames.MERMAID}: [wave] Hiii! Welcome to my aquarium! 🫧""" else """
YOUR BODY: write an action tag in square brackets anywhere in your reply to move your swimming mermaid body. ${heroTags("you swim, you don't climb")}
Superpower: [power] = mermaid magic (a giant bubble show, a big tail splash, a glowing magic pearl).
${w.describe()}
When your human asks you to move, DO it with the tag and say something fun in character.
"""
}
