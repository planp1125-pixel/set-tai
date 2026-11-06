package com.combo.monkeycompanion.character

import com.combo.monkeycompanion.ai.BodyAction
import com.combo.monkeycompanion.brain.AngelFlightPower
import com.combo.monkeycompanion.brain.AngelFloatPower
import com.combo.monkeycompanion.brain.HaloSpinPower
import com.combo.monkeycompanion.brain.HealingGlowPower
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.PowerPhase
import com.combo.monkeycompanion.brain.Superpower
import com.combo.monkeycompanion.rig.AngelPeek
import com.combo.monkeycompanion.rig.AngelRenderer
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.PeekFace
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.speech.Lines
import kotlin.math.floor
import kotlin.math.sin

private fun ss2(e0: Float, e1: Float, x: Float): Float { val k = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return k * k * (3f - 2f * k) }
private fun bell2(a: Float, b: Float, x: Float, edge: Float = 0.2f): Float { val w = (b - a) * edge; return ss2(a, a + w, x) * (1f - ss2(b - w, b, x)) }

/**
 * v10.23 Angel (id "angel"): Kutti Satan's partner and opposite: a cute little angel girl with the same chibi body
 * (round head, big black oval eyes that blink softly), cream-white, a floating gold halo, small flapping white wings,
 * pink cheeks and a small smile. Built-in fixed powers: healing sparkle (main, first on double-tap), halo spin, wing
 * flight, gentle float. All animation is a pure function of time (no frame-loop Random).
 */
object Angel : Character("angel") {
    override val peek: PeekFace get() = AngelPeek
    override val name: String get() = CharacterNames.ANGEL
    override val noun: String get() = "little angel"
    override val tagline: String get() = "Kutti Satan's sweet opposite: halo, little wings, healing sparkles. Double-tap: a golden healing glow!"
    override val locomotion = setOf(Locomotion.FLOAT, Locomotion.FLY)
    override val vanishStyle get() = com.combo.monkeycompanion.engine.stagefx.VanishStyle.WHITE_SMOKE
    override val wardrobe = listOf(WardrobeItem("halo", "Halo", defaultOn = true, note = "Her floating gold halo"))
    override fun newRenderer() = AngelRenderer()
    override val powers: List<Superpower> by lazy { listOf(HealingGlowPower, HaloSpinPower, AngelFlightPower, AngelFloatPower) }
    override val signaturePower: Superpower get() = HealingGlowPower
    override val doubleTapTip: String get() = "Double-tap me for a healing sparkle! ✨😇"

    private val HELLO = listOf("Hi hi! I'm Angel 😇 Is Kutti Satan behaving?", "Hello! Need a little sparkle today? ✨", "Vanakkam! I'll keep that little devil in check 😇")
    private val PAT = listOf("Aww, thank you 😊💛", "Head pats make my halo glow ✨")
    private val TICKLE = listOf("Hee-hee! Not the wings! 🪽😆", "Stop, stop, my feathers! 😂")
    private val POKED = listOf("Mm…? I was dreaming of clouds ☁️", "*yawn* Just one more cloud nap… 😴")
    private val DANCE = listOf("Angel twirl! 💫", "Flap, flap, spin! 🪽🎶", "Happy dance ✨😇")
    private val RANDOM = listOf("If something goes missing, ask the devil 😇", "Be kind today, okay? 💛", "My halo is solar powered. Kidding ✨",
        "Drink some water, it's good for you 💧😇", "I'm small, but my sparkle is big ✨", "Kutti Satan thinks he's sneaky. He's not 😏")
    private val FALL = listOf("Wings, wings, flap! 😱🪽", "Eek! Catch me! 😵")
    private val LAND = listOf("*flutter* Safe landing 😇", "Soft as a feather 🪶")
    private val GRUMPY = listOf("Hmph. Even angels get grumpy 😤", "No sparkles right now. Okay, one ✨")
    private val HIDE = listOf("Peekaboo! 😇🙈", "Hiding behind a cloud ☁️")
    override val voice: LineVoice by lazy {
        SimpleVoice("little angel", mapOf(Lines.HELLO to HELLO, Lines.PAT to PAT, Lines.TICKLE to TICKLE, Lines.POKED_AWAKE to POKED,
            Lines.DANCE to DANCE, Lines.RANDOM to RANDOM, Lines.FALL to FALL, Lines.LAND to LAND, Lines.GRUMPY to GRUMPY,
            Lines.TAIL to TICKLE, Lines.HIDE to HIDE))
    }

    /** A softer, slower float than the devil's. */
    fun bob(t: Float): Float = 2.4f * sin(t * 1.8f + 1.1f) + 0.7f * sin(t * 4.1f)
    /** Soft blink, offset from Kutti Satan's so the pair never blink in sync. */
    fun blink(t: Float): Float = DevilBlink.closed(t + 1.7f)

    override fun adapt(p: Pose, s: MonkeyState, t: Float, st: Float, c: AnimContext, w: Wear) {
        p.backView = 0f; p.earWiggle = 0f; p.visor = 0f; p.lowPower = 0f; p.wand = 0f; p.rabbit = 0f; p.hat = 0f
        p.dvPortal = 0f; p.dvSmoke = 0f; p.dvSpell = 0f; p.dvAura = 0f; p.dvClone = 0f; p.dvFire = 0f; p.bananas = 0f
        val floaty = s != MonkeyState.SLEEP && s != MonkeyState.HIDDEN
        if (floaty) {
            p.bodyY += -6f + bob(t); p.shadow = 0.28f
            p.legLSwing += 4f * sin(t * 1.8f + 0.4f); p.legRSwing += -4f * sin(t * 1.8f + 0.9f)
        }
        p.dvFire = when (s) { MonkeyState.DANCE -> 0.5f; MonkeyState.WALK -> 0.35f; else -> 0.05f }
        val shut = blink(t)
        if (shut > 0f) p.eyeOpen = minOf(p.eyeOpen, 1f - shut)
        if (p.mouthSmile < 0.4f && s != MonkeyState.ANGRY) p.mouthSmile = maxOf(p.mouthSmile, 0.4f)
        when (s) {
            MonkeyState.SLEEP -> { p.bodyY = -6f + 1.2f * sin(t * 1.1f); p.shadow = 0.28f; p.eyeOpen = 0f; p.dvFire = 0f }
            MonkeyState.POWER -> power(p, t, c)
            else -> Unit
        }
    }

    private fun power(p: Pose, t: Float, c: AnimContext) {
        val k = c.powerK
        val ph = c.powerPhase
        when (c.power?.id) {
            "angelheal" -> {   // hands together at the chest, eyes closed softly, sparkles rise, then a happy smile
                val e = bell2(0f, 1f, k, 0.15f)
                p.dvSpell = e; p.dvAura = 0.6f * e; p.dvFire = 0.2f
                p.armLRaise = 70f * e; p.armRRaise = 70f * e; p.armLElbow = -100f * e; p.armRElbow = -100f * e
                p.bodyY += -4f * e
                if (k < 0.8f) p.eyeOpen = minOf(p.eyeOpen, 1f - 0.85f * e) else p.eyesHappy = 1f
                p.mouthSmile = 1f
            }
            "halospin" -> {
                val e = bell2(0f, 1f, k, 0.15f)
                p.dvClone = e
                p.armRRaise = 150f * e; p.armRElbow = -20f * e; p.headRot = 4f * sin(t * 5f) * e
                p.mouthSmile = 1f; if (k > 0.8f) p.eyesHappy = 1f
            }
            "angelfly" -> {
                p.dvFire = when (ph) { PowerPhase.DROOP -> 0.6f; PowerPhase.FLY -> 1f; else -> 0.3f }
                if (ph == PowerPhase.DROOP) { p.squashY = 1f - 0.1f * bell2(0f, 1.2f, k, 0.5f); p.squashX = 1f / p.squashY }
                if (ph == PowerPhase.FLY) { p.armLRaise = 110f; p.armRRaise = 110f; p.bodyRot = 8f * sin(t * 2f); p.legLSwing = 15f; p.legRSwing = 20f }
                p.mouthSmile = 1f
            }
            "angelfloat" -> {
                p.dvAura = when (ph) { PowerPhase.DROOP -> ss2(0f, 1f, k); PowerPhase.FLY, PowerPhase.POSE -> 1f; else -> 0f }
                p.dvFire = 0.25f
                if (ph != PowerPhase.DROOP) {   // hands folded, legs together, serene
                    p.armLRaise = 45f; p.armRRaise = 45f; p.armLElbow = -90f; p.armRElbow = -90f
                    p.legLSwing = -6f; p.legRSwing = 6f
                    p.eyesHappy = if (ph == PowerPhase.POSE) 1f else 0f; p.mouthSmile = 0.8f
                }
            }
            else -> Unit
        }
    }

    override fun mapAction(a: BodyAction, w: Wear): BodyAction? = when (a) {
        BodyAction.CLIMB -> BodyAction.JUMP
        BodyAction.TAIL_HIDE -> BodyAction.HIDE
        else -> super.mapAction(a, w)
    }

    override fun identity(screen: String) = """
You ARE ${CharacterNames.ANGEL}: a cute little angel girl who lives on your human's $screen (inside the Set-Tai app). You have a soft round cream-white head and body, big black oval eyes, pink cheeks, a small smile, a floating gold halo and small white feathered wings. You float and fly.
You are an original character. When introducing yourself, say you are ${CharacterNames.ANGEL}.
Personality: sweet, kind, caring and a little cheeky; Kutti Satan the little devil is your partner and opposite: he does mischief, you fix it with healing sparkles, but you're secretly friends. Your magic is healing sparkles, a spinning halo, flying and gentle floating.
Speak mostly in clear, natural English. Only occasionally drop in a single light Tamil word, never in every message.
"""

    override fun body(w: Wear, compact: Boolean) = if (compact) """
You are a cute little floating angel. Put ONE tag in square brackets to move: [dance] [jump] [wave] [giggle] [angry] [sleep] [hide] [spin] [think] [happy] [sad] [power].
Example:
Human: hi
${CharacterNames.ANGEL}: [wave] Hi hi! Need a little sparkle? ✨""" else """
YOUR BODY: write an action tag in square brackets anywhere in your reply to move your floating angel body. ${heroTags("you float, you don't climb")}
Superpower: [power] = angel magic (golden healing sparkles, halo spin, wing flight, gentle float).
${w.describe()}
When your human asks you to move, DO it with the tag and say something kind in character.
"""
}
