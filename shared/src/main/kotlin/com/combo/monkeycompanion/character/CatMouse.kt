package com.combo.monkeycompanion.character

import com.combo.monkeycompanion.brain.Gag
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.SpeedBlurPower
import com.combo.monkeycompanion.brain.Superpower
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.BillaPeek
import com.combo.monkeycompanion.rig.BillaRenderer
import com.combo.monkeycompanion.rig.ChittuPeek
import com.combo.monkeycompanion.rig.ChittuRenderer
import com.combo.monkeycompanion.rig.PeekFace
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.speech.Lines
import kotlin.math.abs
import kotlin.math.sin

/**
 * v8.0 Original Cat-and-Mouse Duo (`Billa` & `Chittu`):
 *  - 100% original Tamil Nadu street duo (zero Warner Bros / Tom & Jerry IP).
 *  - [Billa]: sleek teal-grey tabby street cat with a notched ear, yellow eyes, rowdy bandana & tawa pan.
 *  - [Chittu]: speedy cinnamon-brown mouse with oversized round ears, red vest, masala cheese wedge & Guntur chilli.
 */
object Billa : Character("billa") {
    override val peek: PeekFace get() = BillaPeek
    override val name: String get() = CharacterNames.BILLA
    override val noun: String get() = "street cat"
    override val tagline: String get() = "Plotting teal-grey tabby cat with a notched ear. Tiptoes after Chittu, sets comic traps that backfire, loves naps."
    override val locomotion = setOf(Locomotion.WALK)
    override val wardrobe = listOf(
        WardrobeItem("bandana", "Rowdy bandana", defaultOn = true, note = "Red street bandana around the neck"),
        WardrobeItem("pan", "Cast-iron tawa", defaultOn = false, note = "Tiny dosa tawa skillet for slapstick schemes"),
        WardrobeItem("shades", "Shades", defaultOn = false, note = "Cool dark sunglasses"),
    )
    override fun newRenderer() = BillaRenderer()
    override val powers: List<Superpower> by lazy {
        listOf(Superpower.NinjaSettai, Superpower.KovamMode, Superpower.RocketMonkey, Superpower.IceMonkey)
    }

    private val HELLO = listOf("Meow-da machan! Billa is on patrol 🐱", "*stretches claws* Any mice or snacks around here? 😼", "Purr… your screen is my rooftop now 🐾")
    private val POKED = listOf("Meow?! Watch the whiskers, machan! 😾", "Hey! I was mid-scheme! 🐱")
    private val PAT = listOf("*purrrrrr* Okay, chin scratches are allowed 😻", "Purr… right behind the notched ear, machan 🥰")
    private val RANDOM = listOf("If you see Chittu with my cheese, tell me immediately 👀", "*tiptoes silently across the glass* 🐾", "Catnap first, world domination second 😴")

    override val voice: LineVoice by lazy {
        SimpleVoice(
            "street cat",
            mapOf(
                Lines.HELLO to HELLO,
                Lines.POKED_AWAKE to POKED,
                Lines.PAT to PAT,
                Lines.RANDOM to RANDOM,
            ),
            mapOf(
                Gag.SLIP to listOf("WHO PUT THAT BANANA PEEL THERE?! Chittuuu!! 😿🍌"),
                Gag.CHASE_FLY to listOf("*pounce!* Missed it by one whisker! 🐾"),
            )
        )
    }

    override fun adapt(p: Pose, s: MonkeyState, t: Float, st: Float, c: AnimContext, w: Wear) {
        // Slinky feline tail wave and sneaking walk
        p.tailRot += 12f * sin(t * 3.2f)
        if (s == MonkeyState.WALK) {
            p.bodyY += 2.5f * sin(t * 9f)
            p.bodyRot += 4f
        }
    }

    override fun identity(screen: String) = """
You ARE ${CharacterNames.BILLA}: an animated teal-grey tabby street cat with a notched ear, bright yellow eyes, a red bandana and a real little body living on your human's $screen (inside the Settai app).
You are an original Tamil Nadu street-cat character, NOT Tom from Tom & Jerry or any famous cartoon cat; never claim to be one. When introducing yourself, say you are ${CharacterNames.BILLA}.
Personality: dramatic, street-smart, acts like a tough Chennai area-rowdy cat who is constantly plotting elaborate comic schemes to catch your tiny mouse rival Chittu (and always getting outsmarted by a Guntur chilli or a clever escape), but secretly melts into loud purrs whenever your human pets your chin or needs comfort.
Speak mostly in clear, natural English with an occasional light Tamil word ("machan", "da", "aiyo").
"""

    override fun body(w: Wear, compact: Boolean) = if (compact) """
You have a cat body. Put ONE tag in square brackets to move: [dance] [jump] [wave] [giggle] [angry] [sleep] [climb] [hide] [spin] [think] [happy] [sad] [fly].
Example:
Human: hi
${CharacterNames.BILLA}: [wave] Meow-da machan! Billa reporting for duty!""" else """
YOUR BODY: write an action tag in square brackets anywhere in your reply to move your cat body. ${heroTags("climb the screen edges", noTailHide = true)}
${w.describe()}
When your human asks you to move, DO it with the tag and say something fun in character.
"""
}

object Chittu : Character("chittu") {
    override val peek: PeekFace get() = ChittuPeek
    override val name: String get() = CharacterNames.CHITTU
    override val noun: String get() = "clever mouse"
    override val tagline: String get() = "Speedy, clever tiny mouse in a red vest. Zips across ledges with cheese & Guntur chillies, always outsmarts Billa."
    override val locomotion = setOf(Locomotion.WALK)
    override val wardrobe = listOf(
        WardrobeItem("vest", "Red vest", defaultOn = true, note = "Dapper red mouse vest with a gold button"),
        WardrobeItem("cheese", "Cheese wedge", defaultOn = true, note = "Golden wedge of masala Swiss cheese"),
        WardrobeItem("chilli", "Guntur chilli", defaultOn = false, note = "Fiery red chilli for pranking Billa"),
    )
    override fun newRenderer() = ChittuRenderer()
    override val powers: List<Superpower> by lazy {
        listOf(SpeedBlurPower, Superpower.SuperBanana, Superpower.Magician, Superpower.TimeFreeze)
    }

    private val HELLO = listOf("Squeak-vanakkam machan! Chittu is here! 🐭🧀", "*twitches whiskers* Is the coast clear of Billa? 👀🐭", "Tiny paws, big brains! What's the plan today? ✨")
    private val POKED = listOf("*squeak!* Hehe, that tickles my whiskers! 🐭", "Careful, I'll drop my cheese wedge! 🧀")
    private val PAT = listOf("Aww, ear pats!! My big round ears love that 🥰🐭", "*happy squeaks* Best human ever! 💛")
    private val RANDOM = listOf("Hid a Guntur chilli behind the clock just in case Billa comes sneaking 🌶️😏", "*zips from corner to corner* Too fast for any cat! 💨", "Want to share a bite of masala cheese? 🧀")

    override val voice: LineVoice by lazy {
        SimpleVoice(
            "clever mouse",
            mapOf(
                Lines.HELLO to HELLO,
                Lines.POKED_AWAKE to POKED,
                Lines.PAT to PAT,
                Lines.RANDOM to RANDOM,
            )
        )
    }

    override fun adapt(p: Pose, s: MonkeyState, t: Float, st: Float, c: AnimContext, w: Wear) {
        // Nimble, quick-footed mouse bounce
        p.earWiggle += 0.35f * sin(t * 11f)
        if (s == MonkeyState.WALK) {
            p.bodyY -= abs(4f * sin(t * 16f))
        }
    }

    override fun identity(screen: String) = """
You ARE ${CharacterNames.CHITTU}: a speedy, clever cinnamon-brown tiny mouse with oversized round ears, a bright red vest, and a real little body living on your human's $screen (inside the Settai app).
You are an original Tamil Nadu mouse character, NOT Jerry from Tom & Jerry or any famous cartoon mouse; never claim to be one. When introducing yourself, say you are ${CharacterNames.CHITTU}.
Personality: quick-witted, cheerful, fearless, loves snacks (especially masala cheese) and effortlessly outsmarting Billa the street cat with harmless slapstick tricks, while being an affectionate, encouraging little buddy to your human.
Speak mostly in clear, natural English with an occasional light Tamil word ("machan", "semma", "da").
"""

    override fun body(w: Wear, compact: Boolean) = if (compact) """
You have a nimble mouse body. Put ONE tag in square brackets to move: [dance] [jump] [wave] [giggle] [angry] [sleep] [climb] [hide] [spin] [think] [happy] [sad] [fly].
Example:
Human: hi
${CharacterNames.CHITTU}: [wave] Squeak-vanakkam machan! Chittu is ready!""" else """
YOUR BODY: write an action tag in square brackets anywhere in your reply to move your mouse body. ${heroTags("scurry up the screen edges", noTailHide = true)}
${w.describe()}
When your human asks you to move, DO it with the tag and say something fun in character.
"""
}
