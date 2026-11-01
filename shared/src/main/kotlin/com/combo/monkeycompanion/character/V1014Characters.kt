package com.combo.monkeycompanion.character

import com.combo.monkeycompanion.ai.BodyAction
import com.combo.monkeycompanion.brain.AerialFlipPower
import com.combo.monkeycompanion.brain.BigWavePower
import com.combo.monkeycompanion.brain.DanceSpinPower
import com.combo.monkeycompanion.brain.GravityLeanPower
import com.combo.monkeycompanion.brain.HatTipPower
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.MoonwalkPower
import com.combo.monkeycompanion.brain.PowerPhase
import com.combo.monkeycompanion.brain.SprayCarvePower
import com.combo.monkeycompanion.brain.Superpower
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.HollowManPeek
import com.combo.monkeycompanion.rig.HollowManRenderer
import com.combo.monkeycompanion.rig.PeekFace
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.rig.SurferPeek
import com.combo.monkeycompanion.rig.SurferRenderer
import com.combo.monkeycompanion.speech.Lines
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private fun ss(e0: Float, e1: Float, x: Float): Float { val k = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return k * k * (3f - 2f * k) }
/** 0 -> 1 -> 0 bell over [a, b] with eased edges (slow in / slow out). */
private fun bell(a: Float, b: Float, x: Float, edge: Float = 0.2f): Float {
    val w = (b - a) * edge
    return ss(a, a + w, x) * (1f - ss(b - w, b, x))
}

/**
 * v10.14 Hollow Man (id "hollowman"): an ORIGINAL invisible dancer. Only his clothes float in the air (fedora, round
 * sunglasses, white gloves, a teal jacket, black trousers and shiny loafers); there is no visible body at all. Inspired
 * by the idea of an invisible man dancing to a fun Tamil film song, not by any film's costume or any real person.
 * Dances with original procedural moves: moonwalk glide, toe stand, spins, hat tip, the impossible lean.
 */
object HollowMan : Character("hollowman") {
    override val peek: PeekFace get() = HollowManPeek
    override val name: String get() = CharacterNames.HOLLOWMAN
    override val noun: String get() = "invisible dancer"
    override val tagline: String get() = "Invisible dancer: only his hat, shades, gloves and clothes float. Double-tap for a dance, first a moonwalk!"
    override val locomotion = setOf(Locomotion.WALK)
    override val wardrobe = listOf(
        WardrobeItem("hat", "Fedora", defaultOn = true, note = "Tilts and wobbles a beat after his moves"),
        WardrobeItem("shades", "Round shades", defaultOn = true, note = "Where his eyes would be"),
        WardrobeItem("gloves", "White gloves", defaultOn = true, note = "Sparkly, they snap to the beat"),
    )
    override fun newRenderer() = HollowManRenderer()
    override val powers: List<Superpower> by lazy { listOf(MoonwalkPower, DanceSpinPower, HatTipPower, GravityLeanPower) }
    override val signaturePower: Superpower get() = MoonwalkPower
    override val doubleTapTip: String get() = "Double-tap me for a dance! First one is a moonwalk 🕺"

    private val HELLO = listOf("Hee-hee! You can't see me, but I can see you 😎", "Hollow Man in the house! 🎩", "Vanakkam! Just my clothes came today 👔✨")
    private val PAT = listOf("Ooh, you found my shoulder! 😄", "Gentle, gentle, the jacket is ironed 👔")
    private val TICKLE = listOf("Hee-hee! Invisible people are ticklish too! 🤣", "Not the gloves! 😂")
    private val POKED = listOf("Huh? Who poked the air? Oh, me 😴", "*shades slide down* I was resting my nothing 😪")
    private val DANCE = listOf("Shamone! 🕺", "Mukkala… no no, my own song 🎶😄", "Feel the beat! 🎶✨", "Smooth! Smooth! 🕴️")
    private val RANDOM = listOf("Fun fact: I never have a bad hair day 🎩", "My shadow quit. Too much dancing 😎",
        "If you see a hat dancing, that's me 🎩", "Chai? I'll drink it. You'll watch it vanish ☕👻", "Tap me and I'll show you a move 🕺")
    private val FALL = listOf("Whoaaa, my trousers are falling without me! 😱", "Catch my hat! 🎩💨")
    private val LAND = listOf("*lands on toes* Perfect 🕴️", "Smooth landing ✨")
    private val GRUMPY = listOf("Hmph. Even my hat is sulking 🎩", "Not dancing now. Okay, maybe a little.")
    override val voice: LineVoice by lazy {
        SimpleVoice("invisible dancer", mapOf(Lines.HELLO to HELLO, Lines.PAT to PAT, Lines.TICKLE to TICKLE, Lines.POKED_AWAKE to POKED,
            Lines.DANCE to DANCE, Lines.RANDOM to RANDOM, Lines.FALL to FALL, Lines.LAND to LAND, Lines.GRUMPY to GRUMPY, Lines.TAIL to TICKLE))
    }

    /** Dance move index (0 heel-pop slide, 1 toe stand, 2 spin, 3 hat tip, 4 lean) for a dance clock. */
    fun danceMove(t: Float): Int = ((t / DANCE_MOVE_SECS).toInt() % 5 + 5) % 5
    const val DANCE_MOVE_SECS = 1.6f

    override fun adapt(p: Pose, s: MonkeyState, t: Float, st: Float, c: AnimContext, w: Wear) {
        p.backView = 0f; p.tailRot = 0f; p.tailSway = 0f; p.earWiggle = 0f
        p.hmMoon = 0f; p.hmHatTip = 0f; p.spinX = 1f
        // a subtle groove all the time (secondary action): shoulders bob to a beat only he hears
        if (s == MonkeyState.IDLE || s == MonkeyState.TALKING) {
            p.headRot += 3f * sin(t * 3.4f); p.bodyY += 0.8f * abs(sin(t * 3.4f))
        }
        when (s) {
            MonkeyState.DANCE -> dance(p, t, danceMove(t), (t % DANCE_MOVE_SECS) / DANCE_MOVE_SECS)
            MonkeyState.POWER -> power(p, t, c)
            else -> Unit
        }
    }

    private fun dance(p: Pose, t: Float, move: Int, k: Float) {
        when (move) {
            0 -> heelPops(p, t, 1f)
            1 -> toeStand(p, t, bell(0f, 1f, k, 0.25f))
            2 -> spin(p, ss(0.1f, 0.85f, k) * 2f, bell(0f, 1f, k, 0.15f))
            3 -> hatTip(p, t, bell(0f, 1f, k, 0.3f))
            else -> lean(p, bell(0f, 1f, k, 0.3f))
        }
    }

    /** Moonwalk feet: one flat foot slides back while the other pops up on its toe, alternating; the body glides level. */
    fun heelPops(p: Pose, t: Float, amt: Float) {
        val ph = t * 5.2f
        val a = sin(ph); val b = sin(ph + PI.toFloat())
        p.legLSwing = (-14f * a) * amt; p.legRSwing = (-14f * b) * amt
        p.legLLift = max(0f, 9f * a) * amt; p.legRLift = max(0f, 9f * b) * amt
        p.bodyY += 1.2f * abs(a) * amt
        p.armLRaise = 18f + 8f * b; p.armRRaise = 18f + 8f * a; p.armLElbow = -30f; p.armRElbow = -30f
        p.bodyRot += -3f * amt; p.headRot += 2f * sin(ph * 0.5f)
    }

    fun toeStand(p: Pose, t: Float, e: Float) {
        p.legLSwing = 4f * e; p.legRSwing = -4f * e; p.legLLift = 0f; p.legRLift = 0f
        p.bodyY -= 7f * e; p.squashY *= 1f + 0.08f * e; p.squashX /= 1f + 0.08f * e
        p.armLRaise = 20f + 40f * e; p.armRRaise = 20f + 150f * e; p.armRElbow = -20f * e
        p.headRot += 6f * e
    }

    /** [turns] full turns about the vertical axis (x scale = cos), arms tucked, a kick out of it. */
    fun spin(p: Pose, turns: Float, e: Float) {
        p.spinX = cos(turns * 2f * PI.toFloat())
        p.armLRaise = 10f + 20f * e; p.armRRaise = 10f + 20f * e; p.armLElbow = -80f * e; p.armRElbow = -80f * e
        p.legRSwing = -10f * e; p.legRLift = 6f * e
        p.bodyY -= 3f * e
    }

    fun hatTip(p: Pose, t: Float, e: Float) {
        p.hmHatTip = e
        p.armRRaise = 30f + 120f * e; p.armRElbow = -60f * e
        p.bodyRot += 10f * e; p.headRot += 8f * e            // a little bow
        p.legRSwing = -6f * e
    }

    fun lean(p: Pose, e: Float) {
        p.bodyRot += 30f * e                                   // feet glued, the whole body tilts forward
        p.legLSwing = -8f * e; p.legRSwing = -8f * e
        p.armLRaise = 8f; p.armRRaise = 8f
    }

    private fun power(p: Pose, t: Float, c: AnimContext) {
        val k = c.powerK
        when (c.power?.id) {
            "moonwalk" -> when (c.powerPhase) {
                PowerPhase.DROOP -> {                                       // anticipation: crouch, then the hat tip
                    val cr = bell(0f, 0.6f, k, 0.4f)
                    p.bodyY += 5f * cr; p.squashY *= 1f - 0.08f * cr; p.squashX /= 1f - 0.08f * cr
                    hatTip(p, t, bell(0.35f, 1f, k, 0.35f))
                }
                PowerPhase.POSE -> toeStand(p, t, bell(0f, 1f, k, 0.25f))
                else -> { p.hmMoon = 1f; p.bodyRot = 0f; p.bodyX = 0f; heelPops(p, t, 1f) }
            }
            "dancespin" -> {
                val cr = bell(0f, 0.12f, k, 0.5f)
                p.bodyY += 4f * cr
                spin(p, ss(0.12f, 0.67f, k) * 2f, bell(0.1f, 0.7f, k, 0.15f))
                toeStand(p, t, bell(0.67f, 1f, k, 0.25f))
            }
            "hattip" -> { hatTip(p, t, bell(0f, 1f, k, 0.25f)); if (k > 0.3f && k < 0.75f) p.mouthSmile = 1f }
            "lean" -> {
                val cr = bell(0f, 0.12f, k, 0.5f)
                p.bodyRot -= 6f * cr                                         // anticipation: rock back first
                lean(p, bell(0.1f, 1f, k, 0.25f))
            }
            else -> Unit
        }
    }

    override fun mapAction(a: BodyAction, w: Wear): BodyAction? = when (a) {
        BodyAction.CLIMB -> BodyAction.DANCE
        else -> super.mapAction(a, w)
    }

    override fun identity(screen: String) = """
You ARE ${CharacterNames.HOLLOWMAN}: an invisible dancer living on your human's $screen (inside the Set-Tai app). Nobody can see your body, only your clothes floating in the air: a charcoal fedora with a red band, round dark sunglasses, white gloves, a teal jacket, black trousers and shiny black loafers.
You are an original character, not any film character or real person. When introducing yourself, say you are ${CharacterNames.HOLLOWMAN}.
Personality: smooth, playful, a bit of a show-off; you love dancing (moonwalk glides, toe stands, spins, hat tips, impossible leans) and silly jokes about being invisible.
Speak mostly in clear, natural English. Only occasionally drop in a single light Tamil word (like "machan", "semma", "aiyo"), never in every message.
"""

    override fun body(w: Wear, compact: Boolean) = if (compact) """
You are an invisible dancer. Put ONE tag in square brackets to move: [dance] [jump] [wave] [giggle] [angry] [sleep] [hide] [spin] [think] [happy] [sad] [power].
Example:
Human: hi
${CharacterNames.HOLLOWMAN}: [wave] Hee-hee! You can't see me, but I'm waving!""" else """
YOUR BODY: write an action tag in square brackets anywhere in your reply to move your invisible body. ${heroTags("you can't climb; you dance instead", noTailHide = true)}
Superpower: [power] = a dance move (moonwalk across the screen, a spin and freeze, a hat tip, the impossible lean).
${w.describe()}
When your human asks you to move, DO it with the tag and say something fun in character.
"""
}

/**
 * v10.14 Surfing Girl (id "surfer"): an ORIGINAL cute girl on a surfboard. She floats on a procedurally drawn wave
 * wherever she is (screen edges, across the middle), leaves splash trails, does aerial flips and the odd wipeout.
 */
object SurferGirl : Character("surfer") {
    override val peek: PeekFace get() = SurferPeek
    override val name: String get() = CharacterNames.SURFER
    override val noun: String get() = "surfer girl"
    override val tagline: String get() = "Cute surfer girl riding waves around your screen. Double-tap: big wave ride, flips (and wipeouts)!"
    override val locomotion = setOf(Locomotion.FLOAT, Locomotion.FLY)
    override val wardrobe = listOf(
        WardrobeItem("flower", "Hair flower", defaultOn = true, note = "A hibiscus in her ponytail"),
        WardrobeItem("spray", "Splash trail", defaultOn = true, note = "Water spray behind her board"),
    )
    override fun newRenderer() = SurferRenderer()
    override val powers: List<Superpower> by lazy { listOf(BigWavePower, AerialFlipPower, SprayCarvePower) }
    override val signaturePower: Superpower get() = BigWavePower
    override val doubleTapTip: String get() = "Double-tap me to ride a big wave around your screen! 🌊"

    private val HELLO = listOf("Hiii! Surf's up on your screen! 🏄‍♀️", "Aloha-vanakkam! 🌊😄", "Did someone order a wave? 🌊")
    private val PAT = listOf("Hehe, salty hair, sorry! 😆", "Aww thanks! *wobbles on board* 🏄‍♀️")
    private val TICKLE = listOf("Haha stop, I'll fall in! 🤣🌊", "Not the feet, I need balance! 😂")
    private val POKED = listOf("Huh?! I was dreaming of mango ice cream 😴", "*yawn* Waiting for the next wave… 🌊")
    private val DANCE = listOf("Wave dance! 🌊💃", "Wiggle wiggle on the board! 🏄‍♀️")
    private val RANDOM = listOf("Fun fact: my board is named Thengai 🥥", "Your screen has the best waves 🌊",
        "Sunscreen? Done. Smile? Done 😎", "If you hear a splash, that's me 💦", "Want to see a flip? Double-tap! 🤸‍♀️")
    private val FALL = listOf("Wipeouuut! 😱🌊", "Where did my wave go?! 😵")
    private val LAND = listOf("Back on the board! 🏄‍♀️", "Splash landing! 💦")
    override val voice: LineVoice by lazy {
        SimpleVoice("surfer girl", mapOf(Lines.HELLO to HELLO, Lines.PAT to PAT, Lines.TICKLE to TICKLE, Lines.POKED_AWAKE to POKED,
            Lines.DANCE to DANCE, Lines.RANDOM to RANDOM, Lines.FALL to FALL, Lines.LAND to LAND, Lines.TAIL to TICKLE))
    }

    override fun adapt(p: Pose, s: MonkeyState, t: Float, st: Float, c: AnimContext, w: Wear) {
        p.backView = 0f; p.tailRot = 0f; p.tailSway = 0f; p.earWiggle = 0f
        p.flipS = 0f; p.flipC = 1f; p.surfSpray = 0f
        p.surf = 1f
        val bob = 3.5f * sin(t * 2.3f) + 1.2f * sin(t * 5.1f)        // riding the swell
        // surf stance: front foot forward, back foot back, knees soft, arms out for balance (eased, never stiff)
        fun stance(crouch: Float, lean: Float) {
            p.bodyY += -20f + bob + 6f * crouch; p.shadow = 0.35f
            p.legLSwing = -18f - 6f * crouch; p.legRSwing = 16f + 6f * crouch; p.legLLift = 3f * crouch; p.legRLift = 3f * crouch
            p.armLRaise = 60f + 10f * sin(t * 1.9f); p.armRRaise = 45f + 10f * sin(t * 1.9f + 1.2f)
            p.armLElbow = -10f; p.armRElbow = -18f
            p.bodyRot += lean + 4f * sin(t * 1.7f)                     // carving S-turns
            p.squashY *= 1f - 0.06f * crouch; p.squashX /= 1f - 0.06f * crouch
        }
        when (s) {
            MonkeyState.SLEEP -> { p.bodyY = -14f + 1.5f * sin(t * 1.2f); p.shadow = 0.3f; p.legLSwing = -40f; p.legRSwing = -36f; p.bodyRot = -8f }
            MonkeyState.WALK -> { stance(0.6f, 12f); p.surfSpray = 0.6f }
            MonkeyState.DANCE -> { stance(0.3f * abs(sin(t * 4f)), 8f * sin(t * 3.5f)); p.armLRaise += 60f * abs(sin(t * 3.5f)); p.armRRaise += 60f * abs(sin(t * 3.5f + 1.5f)) }
            MonkeyState.POWER -> power(p, t, c, ::stance)
            MonkeyState.FALLING, MonkeyState.DRAGGED -> { stance(0.2f, 0f); p.armLRaise = 120f + 20f * sin(t * 9f); p.armRRaise = 120f + 20f * sin(t * 9f + 1f); p.mouthOpen = 0.5f }
            else -> stance(0.15f, 0f)
        }
    }

    private fun power(p: Pose, t: Float, c: AnimContext, stance: (Float, Float) -> Unit) {
        val k = c.powerK
        val ph = c.powerPhase
        when (c.power?.id) {
            "bigwave" -> {
                when (ph) {
                    PowerPhase.TAKEOFF -> paddleUp(p, t, k, stance)   // lying on the board paddling, then the pop-up
                    PowerPhase.LAND -> { stance(1f - k * 0.7f, 0f); p.surfSpray = 1f - k }
                    PowerPhase.POSE -> { stance(0.1f, 0f); p.armLRaise = 150f; p.armRRaise = 150f; p.mouthSmile = 1f; p.eyesHappy = 1f }
                    else -> ride(p, t, c, stance)
                }
            }
            "aerial" -> {
                when (ph) {
                    PowerPhase.TAKEOFF -> { stance(bell(0f, 1f, k, 0.4f), -4f); p.surfSpray = 0.6f }
                    PowerPhase.FLY -> if (c.gliding) ride(p, t, c, stance) else {
                        stance(0.9f, 0f)
                        val a = ss(0.1f, 0.85f, k) * 2f * PI.toFloat()              // one full flip, eased in and out
                        p.flipS = sin(a); p.flipC = cos(a)
                        if (c.crashed) { p.eyeOpen = 0.3f; p.mouthOpen = 0.7f }
                    }
                    PowerPhase.LAND -> {
                        if (c.crashed) wipeout(p, t, k) else { stance(1f - k, 0f); p.surfSpray = 1f - k }
                    }
                    else -> if (c.crashed) wipeout(p, t, 1f) else { stance(0.1f, 0f); p.armRRaise = 160f; p.mouthSmile = 1f }
                }
            }
            "carve" -> when (ph) {
                PowerPhase.TAKEOFF -> paddleUp(p, t, k, stance)
                PowerPhase.FLY -> ride(p, t, c, stance)
                else -> { stance(0.4f, 0f); p.surfSpray = 0.3f }
            }
            else -> stance(0.2f, 0f)
        }
    }

    /**
     * v10.22 riding: weight low, knees bent more at speed, leaning INTO each carve (back while climbing the face,
     * forward and low dropping down it), arms out for balance swinging with the turn, spray with the speed.
     */
    private fun ride(p: Pose, t: Float, c: AnimContext, stance: (Float, Float) -> Unit) {
        val lean = c.carveLean; val v = c.glideSpeed
        stance(0.45f + 0.45f * v + 0.2f * abs(lean) / 26f, lean)
        p.bodyRot -= 4f * sin(t * 1.7f)                 // no extra wobble while carving: the turn IS the motion
        p.armLRaise = 70f + 1.2f * lean; p.armRRaise = 55f - 1.2f * lean   // arms balance against the lean
        p.headRot = -0.4f * lean                         // eyes stay level, looking down the line
        p.surfSpray = (0.25f + 0.75f * v) * (0.5f + 0.5f * abs(lean) / 26f)
        p.mouthSmile = 1f
    }

    /** v10.22 the take-off: low on the board paddling (arms windmill), then a quick pop-up to the stance. */
    private fun paddleUp(p: Pose, t: Float, k: Float, stance: (Float, Float) -> Unit) {
        if (k < 0.7f) {
            stance(1f, 10f)
            p.bodyY += 12f                                    // low on the board, paddling with both arms
            p.armLRaise = 70f + 60f * sin(t * 9f); p.armRRaise = 70f - 60f * sin(t * 9f)
            p.surfSpray = 0.25f
        } else {
            val u = (k - 0.7f) / 0.3f
            stance(1f - 0.4f * u, 10f * (1f - u))             // pops up into the riding crouch
            p.bodyY += 12f * (1f - u)
            p.surfSpray = 0.4f
        }
    }

    /** The wipeout gag: tumbles off, flails, pops up spluttering (still smiling). */
    fun wipeout(p: Pose, t: Float, k: Float) {
        p.surfSpray = 1f
        p.bodyRot = 40f * sin(t * 10f) * (1f - k * 0.6f)
        p.armLRaise = 140f + 30f * sin(t * 14f); p.armRRaise = 130f + 30f * sin(t * 13f + 1f)
        p.legLSwing = 30f * sin(t * 11f); p.legRSwing = -30f * sin(t * 12f)
        p.eyeOpen = 0.35f; p.mouthOpen = 0.6f; p.mouthSmile = 0.3f
        p.bodyY += 6f
    }

    override fun mapAction(a: BodyAction, w: Wear): BodyAction? = when (a) {
        BodyAction.CLIMB -> a
        else -> super.mapAction(a, w)
    }

    override fun identity(screen: String) = """
You ARE ${CharacterNames.SURFER}: a cheerful little surfer girl (a high ponytail with a hibiscus, a coral rash-guard top, aqua shorts) who rides a yellow-and-teal surfboard on cartoon waves all around your human's $screen (inside the Set-Tai app). You are not "an AI assistant"; you are this girl.
You are an original character, not any famous cartoon. When introducing yourself, say you are ${CharacterNames.SURFER}.
Personality: sunny, sporty, brave and a little goofy; you love big waves, flips, coconut water and mango ice cream, and you laugh off your wipeouts. You care about ocean safety: real surfing only with a lifeguard around.
Speak mostly in clear, natural English. Only occasionally drop in a single light Tamil word (like "machan", "semma", "aiyo"), never in every message.
"""

    override fun body(w: Wear, compact: Boolean) = if (compact) """
You surf on a board over waves. Put ONE tag in square brackets to move: [dance] [jump] [wave] [giggle] [angry] [sleep] [climb] [hide] [spin] [think] [happy] [sad] [fly].
Example:
Human: hi
${CharacterNames.SURFER}: [wave] Hiii! Surf's up!""" else """
YOUR BODY: write an action tag in square brackets anywhere in your reply to move your surfing body. ${heroTags("surf up the screen edges", noTailHide = true)}
Superpower: [fly] (or [power]) = a big wave ride all around the screen, an aerial flip (sometimes a splashy wipeout), or a spray carve.
${w.describe()}
When your human asks you to move, DO it with the tag and say something fun in character.
"""
}
