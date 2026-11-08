package com.combo.monkeycompanion.character

import com.combo.monkeycompanion.ai.BodyAction
import com.combo.monkeycompanion.brain.BoardSpinPower
import com.combo.monkeycompanion.brain.GrindPower
import com.combo.monkeycompanion.brain.KickflipShowPower
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.Superpower
import com.combo.monkeycompanion.engine.swipe.SwipeTravel.bell
import com.combo.monkeycompanion.engine.swipe.SwipeTravel.ss
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.PeekFace
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.rig.SkaterPeek
import com.combo.monkeycompanion.rig.SkaterRenderer
import com.combo.monkeycompanion.speech.Lines
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * v10.24 Rollie (id "skater"): an ORIGINAL cute chibi girl with dark brown curly hair, a lilac jacket over a mint
 * tee, denim shorts and pink high-tops, always with her coral skateboard. Rolls along the floor on it; swipe her and she
 * skates to the spot doing tricks. Fixed powers: kickflip show (main), grind, board spin. Time-based animation, no RNG.
 */
object SkaterGirl : Character("skater") {
    override val peek: PeekFace get() = SkaterPeek
    override val name: String get() = CharacterNames.SKATER
    override val noun: String get() = "skater girl"
    override val tagline: String get() = "Cute curly-haired skater girl. Swipe her: she skates there doing tricks! Double-tap: kickflip show!"
    override val locomotion = setOf(Locomotion.WALK)
    override val wardrobe = listOf(WardrobeItem("board", "Skateboard", defaultOn = true, note = "Her coral skateboard"))
    override fun newRenderer() = SkaterRenderer()
    override val powers: List<Superpower> by lazy { listOf(KickflipShowPower, GrindPower, BoardSpinPower) }
    override val signaturePower: Superpower get() = KickflipShowPower
    override val doubleTapTip: String get() = "Double-tap me for a kickflip show! 🛹"

    private val HELLO = listOf("Heyy! Rollie rolling in! 🛹", "Hi hi! Your screen is the smoothest skate park 😎", "Yo! Want to see a kickflip? 🛹✨")
    private val PAT = listOf("Hehe, careful, the curls bounce back! 😆", "Aww thanks! *does a tiny wheelie* 🛹")
    private val TICKLE = listOf("Haha no, I'll fall off my board! 🤣", "Stop stop, balance, balance! 😂")
    private val POKED = listOf("Huh?! I was dreaming of a mega ramp 😴", "*yawn* Five more minutes, then skating 🛹")
    private val DANCE = listOf("Board dance! 🛹💃", "Roll and wiggle! 🎶")
    private val RANDOM = listOf("My board's name is Vadai. Don't ask 🛹", "Helmet? Yes, in my heart 😅 Wear yours!",
        "Fun fact: my curls absorb crashes 😆", "This screen needs more ramps 🛹", "Swipe me somewhere, I'll skate there! ✨")
    private val FALL = listOf("Bail! Bail! 😱🛹", "Whoaaa, where's my board?! 😵")
    private val LAND = listOf("Landed it! 🛹", "Wheels down! 😎")
    override val voice: LineVoice by lazy {
        SimpleVoice("skater girl", mapOf(Lines.HELLO to HELLO, Lines.PAT to PAT, Lines.TICKLE to TICKLE, Lines.POKED_AWAKE to POKED,
            Lines.DANCE to DANCE, Lines.RANDOM to RANDOM, Lines.FALL to FALL, Lines.LAND to LAND, Lines.TAIL to TICKLE))
    }

    override fun adapt(p: Pose, s: MonkeyState, t: Float, st: Float, c: AnimContext, w: Wear) {
        p.backView = 0f; p.tailRot = 0f; p.tailSway = 0f; p.earWiggle = 0f; p.surf = 0f
        p.dvAura = if (w.has("board")) 1f else 0f; p.dvSpell = 0f; p.surfSpray = 0f
        fun stance(crouch: Float) {   // on the board: feet apart, knees soft, arms loose
            p.bodyY += -8f + 4f * crouch
            p.legLSwing = -14f - 4f * crouch; p.legRSwing = 16f + 4f * crouch
            p.squashY *= 1f - 0.05f * crouch; p.squashX /= 1f - 0.05f * crouch
        }
        when (s) {
            MonkeyState.SLEEP, MonkeyState.HIDDEN -> p.dvAura = 0f
            MonkeyState.WALK -> {   // rolling along: one foot pushes now and then
                stance(0.3f)
                val push = maxOf(0f, sin(t * 3f))
                p.legRSwing = 16f + 26f * push; p.legRLift = 4f * push
                p.armLRaise = 40f + 10f * sin(t * 3f); p.armRRaise = 30f; p.bodyRot = 5f
            }
            MonkeyState.POWER -> { stance(0.2f); power(p, t, c) }
            MonkeyState.FALLING, MonkeyState.DRAGGED -> { p.armLRaise = 120f + 20f * sin(t * 9f); p.armRRaise = 120f + 20f * sin(t * 9f + 1f); p.mouthOpen = 0.4f }
            else -> stance(0.1f + 0.05f * sin(t * 1.6f))
        }
    }

    private fun power(p: Pose, t: Float, c: AnimContext) {
        val k = c.powerK
        when (c.power?.id) {
            "kickflip" -> {
                val h1 = bell(0.1f, 0.3f, k); val h2 = bell(0.38f, 0.62f, k); val h3 = bell(0.68f, 0.9f, k)
                p.bodyY += -40f * h1 - 55f * h2 - 55f * h3
                p.dvSpell = if (k < 0.66f) ss(0.42f, 0.58f, k) else ss(0.72f, 0.86f, k)
                p.armLRaise = 60f + 70f * maxOf(h2, h3); p.armRRaise = 50f + 70f * maxOf(h1, h3)
                p.bodyRot = -10f * h1
                if (k > 0.9f) { p.armLRaise = 150f; p.armRRaise = 150f; p.eyesHappy = 1f }
                p.mouthSmile = 1f
            }
            "grind" -> {
                val e = bell(0f, 1f, k)
                p.bodyY += 6f * e; p.squashY = 1f - 0.12f * e; p.squashX = 1f / p.squashY
                p.armLRaise = 80f; p.armRRaise = 100f; p.bodyRot = 8f * e + 2f * sin(t * 20f) * e
                p.surfSpray = e; p.mouthSmile = 1f
            }
            "boardspin" -> {
                val h = bell(0.15f, 0.8f, k)
                p.bodyY += -50f * h
                p.spinX = if (k in 0.2f..0.75f) cos(ss(0.2f, 0.75f, k) * 2f * PI.toFloat()) else 1f
                p.armLRaise = 90f + 60f * h; p.armRRaise = 90f + 60f * h
                if (k > 0.85f) { p.eyesHappy = 1f; p.bodyRot = 6f * sin(t * 12f) * (1f - k) }
                p.mouthSmile = 1f
            }
        }
        if (abs(p.dvSpell) > 1f) p.dvSpell = 1f
    }

    override fun mapAction(a: BodyAction, w: Wear): BodyAction? = when (a) {
        BodyAction.CLIMB -> BodyAction.JUMP
        else -> super.mapAction(a, w)
    }

    override fun identity(screen: String) = """
You ARE ${CharacterNames.SKATER}: a cheerful little skater girl with dark brown curly hair, a lilac jacket over a mint tee, denim shorts and pink high-tops, who rides a coral skateboard around your human's $screen (inside the Set-Tai app). You are not "an AI assistant"; you are this girl.
You are an original character. When introducing yourself, say you are ${CharacterNames.SKATER}.
Personality: sporty, confident, funny, a bit of a show-off about tricks (ollies, kickflips, grinds, 360 spins) but always encouraging and kind; you remind people to stay safe.
Speak mostly in clear, natural English. Only occasionally drop in a single light Tamil word, never in every message.
"""

    override fun body(w: Wear, compact: Boolean) = if (compact) """
You are a cute little skater girl. Put ONE tag in square brackets to move: [dance] [jump] [wave] [giggle] [angry] [sleep] [hide] [spin] [think] [happy] [sad] [power].
Example:
Human: hi
${CharacterNames.SKATER}: [wave] Heyy! Want to see a kickflip? 🛹""" else """
YOUR BODY: write an action tag in square brackets anywhere in your reply to move your body. ${heroTags("you skate, you don't climb")}
Superpower: [power] = a skate trick (kickflip show, grind with sparks, 360 board spin).
${w.describe()}
When your human asks you to move, DO it with the tag and say something fun in character.
"""
}
