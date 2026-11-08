package com.combo.monkeycompanion.character

import com.combo.monkeycompanion.ai.BodyAction
import com.combo.monkeycompanion.brain.Gag
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.Superpower
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.GajjuPeek
import com.combo.monkeycompanion.rig.GajjuRenderer
import com.combo.monkeycompanion.rig.PeekFace
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.speech.Lines
import kotlin.math.sin

/**
 * v10.1: Gajju, an original chibi lavender baby elephant with big shiny eyes, huge flappy ears, a curly trunk, a hair
 * tuft and plump little wings. Floats everywhere (never walks), loops all over the screen and loves dancing in mid-air.
 */
object Gajju : Character("gajju") {
    override val peek: PeekFace get() = GajjuPeek
    override val name: String get() = CharacterNames.GAJJU
    override val noun: String get() = "baby elephant"
    override val tagline: String get() = "Lavender baby elephant with big shiny eyes and fluffy wings. Loops all over the screen and dances in mid-air."
    override val locomotion = setOf(Locomotion.FLOAT, Locomotion.FLY)
    override val wardrobe = listOf(
        WardrobeItem("bow", "Pink bow", defaultOn = false, note = "A pink polka-dot bow on the head"),
        WardrobeItem("crown", "Tiny crown", defaultOn = false, note = "A little golden crown"),
    )
    override fun newRenderer() = GajjuRenderer()
    override val powers: List<Superpower> by lazy { listOf(Superpower.IceMonkey, Superpower.NinjaSettai, Superpower.TimeFreeze) }

    private val HELLO = listOf("Paaaawoo! 🐘💙 Gajju flew in to say hi!", "*flap flap* Hello hello! Want to see me loop? 🐘✨", "Tiny wings, big heart. Hi, it's Gajju! 💙")
    private val PAT = listOf("*happy trunk wiggle* More pats please 🐘💕", "Ehehe, my ears go floppy when you do that 🥰")
    private val TICKLE = listOf("Hee hee hee! Not the tummy! 🐘😂", "*giggles so hard the wings flap* 🪽😆")
    private val POKED = listOf("Paaawoo?! I was dreaming of sugarcane! 🐘", "*yawns with the trunk* Five more minutes… 😴")
    private val DANCE = listOf("Dance break! Wings up! 🪽💃", "Watch my trunk twirl! 🐘🎶", "Sky disco time! ✨🐘")
    private val RANDOM = listOf("*loops around the screen* Wheee! 🐘🪽", "Elephants never forget… where the snacks are 🍌", "I'm small for an elephant, but my hugs are huge 💙")
    /** Belly poke: a trunk-full of water and off it flies in a huff. */
    val SPRAY = listOf("Don't touch that! 🐘💦", "Paaawoo! No belly touching! 💦💦", "Hey! That's MY tummy! *SPLASH* 💦", "Ticklish! TICKLISH! Take that! 💦😤")
    private val FALL = listOf("Wings! Flap! FLAP! 🪽😱", "Whoaaa— tiny wings, big elephant!")

    override val voice: LineVoice by lazy {
        SimpleVoice(
            "baby elephant",
            mapOf(
                Lines.HELLO to HELLO,
                Lines.PAT to PAT,
                Lines.TICKLE to TICKLE,
                Lines.POKED_AWAKE to POKED,
                Lines.DANCE to DANCE,
                Lines.RANDOM to RANDOM,
                Lines.FALL to FALL,
            ),
            mapOf(
                Gag.SNEEZE to listOf("Ah… ah… AH-CHOO! *trunk sneeze* 🐘💦 Sorry!!"),
                Gag.JUGGLE to listOf("Trunk juggling! One, two, three sugarcanes!"),
            )
        )
    }

    override fun adapt(p: Pose, s: MonkeyState, t: Float, st: Float, c: AnimContext, w: Wear) {
        val bob = 5f * sin(t * 2.4f)
        when (s) {
            MonkeyState.POWER, MonkeyState.FALLING, MonkeyState.DRAGGED, MonkeyState.EXIT, MonkeyState.HIDDEN -> Unit
            MonkeyState.SLEEP -> {
                // Floats low, curled up, wings folded like a blanket.
                p.bodyY = -16f + 3f * sin(t * 1.3f); p.shadow = 0.5f
                p.legLSwing = 35f; p.legRSwing = 35f; p.legLLift = 16f; p.legRLift = 16f
            }
            MonkeyState.WALK -> {
                // Glides instead of walking: a gentle lean, little legs dangling.
                p.bodyY = -20f + bob; p.bodyRot = 8f; p.shadow = 0.5f
                p.legLSwing = -14f; p.legRSwing = -6f; p.legLLift = 6f; p.legRLift = 3f
            }
            else -> {
                p.bodyY += -20f + bob; p.shadow = 0.5f
                p.legLSwing *= 0.4f; p.legRSwing *= 0.4f
                p.legLLift = maxOf(p.legLLift, 3f); p.legRLift = maxOf(p.legRLift, 6f)
            }
        }
        if (s == MonkeyState.DANCE) p.bodyRot += 6f * sin(t * 5f)   // wiggly sky-disco sway
        p.anger = 0f   // no red face flush on Gajju: grumpy shows in the brows only
    }

    override fun mapAction(a: BodyAction, w: Wear): BodyAction? = when (a) {
        BodyAction.CLIMB -> a                        // = flies up near an edge
        else -> super.mapAction(a, w)
    }

    override fun identity(screen: String) = """
You ARE ${CharacterNames.GAJJU}: an animated chibi lavender baby elephant with big shiny eyes, huge pink-lined flappy ears, a curly trunk, a little hair tuft and plump white wings, with a real little body living on your human's $screen (inside the Set-Tai app). You are not "an AI assistant"; you are this elephant.
You are an original character, NOT Dumbo or any famous cartoon elephant; never claim to be one. When introducing yourself, say you are ${CharacterNames.GAJJU}.
Personality: sweet, cheerful and a little clumsy; you float everywhere on your tiny wings, loop around the screen for fun and break into happy mid-air dances. You love sugarcane and hugs, and you trumpet "paaawoo!" when excited. You genuinely care about your human and cheer them on.
Speak mostly in clear, natural English. Only occasionally drop in a single light Tamil word (like "machan", "semma", "aiyo"), never in every message.
"""

    override fun body(w: Wear, compact: Boolean) = if (compact) """
You have a flying baby elephant body. Put ONE tag in square brackets to move: [dance] [jump] [wave] [giggle] [angry] [sleep] [climb] [hide] [spin] [think] [happy] [sad] [fly].
Example:
Human: hi
${CharacterNames.GAJJU}: [wave] Paaawoo! Gajju flew in to say hi!""" else """
YOUR BODY: write an action tag in square brackets anywhere in your reply to move your flying elephant body. ${heroTags("fly up near the screen edges", noTailHide = true)}
${w.describe()}
When your human asks you to move, DO it with the tag and say something fun in character.
"""
}
