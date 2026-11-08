package com.combo.monkeycompanion.character

import com.combo.monkeycompanion.ai.BodyAction
import com.combo.monkeycompanion.brain.DevilLevitatePower
import com.combo.monkeycompanion.brain.FloatBuddyPower
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.PowerPhase
import com.combo.monkeycompanion.brain.ShadowClonePower
import com.combo.monkeycompanion.brain.SpellSparksPower
import com.combo.monkeycompanion.brain.Superpower
import com.combo.monkeycompanion.brain.TeleportHopPower
import com.combo.monkeycompanion.brain.VanishPortalPower
import com.combo.monkeycompanion.brain.HeroSwoop
import com.combo.monkeycompanion.brain.FireballPower
import com.combo.monkeycompanion.brain.JugglePower
import com.combo.monkeycompanion.brain.HeatVisionPower
import com.combo.monkeycompanion.brain.DanceSpinPower
import com.combo.monkeycompanion.brain.RocketBoostPower
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.DevilPeek
import com.combo.monkeycompanion.rig.DevilRenderer
import com.combo.monkeycompanion.rig.PeekFace
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.speech.Lines
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

private fun ss(e0: Float, e1: Float, x: Float): Float { val k = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return k * k * (3f - 2f * k) }
private fun bell(a: Float, b: Float, x: Float, edge: Float = 0.2f): Float { val w = (b - a) * edge; return ss(a, a + w, x) * (1f - ss(b - w, b, x)) }

/**
 * v10.16 the soft, natural blink (pure function of time, no RNG: the frame-loop random stream is untouched).
 * One blink in every 4 s window, at 1..3 s into it, so blinks are 2..6 s apart; about 1 in 4 is a double-blink.
 */
object DevilBlink {
    const val WINDOW = 4f
    const val DUR = 0.18f
    const val DOUBLE_GAP = 0.3f
    private fun h(n: Int, salt: Float): Float { val v = sin(n * 12.9898f + salt * 78.233f) * 43758.547f; return v - floor(v) }
    /** Time of the (first) blink in window [n]. */
    fun blinkAt(n: Int): Float = n * WINDOW + 1f + 2f * h(n, 1f)
    fun isDouble(n: Int): Boolean = h(n, 2f) < 0.25f
    /** One blink's shape: quick close, a hair of hold, a slightly slower open (0 open .. 1 shut). */
    private fun shape(dt: Float): Float = when {
        dt < 0f || dt > DUR -> 0f
        dt < 0.06f -> ss(0f, 0.06f, dt)
        dt < 0.09f -> 1f
        else -> 1f - ss(0.09f, DUR, dt)
    }
    /** How shut the eyes are at [t] (0 = open, 1 = fully closed). */
    fun closed(t: Float): Float {
        val n = floor(t / WINDOW).toInt()
        var c = 0f
        for (m in n - 1..n) {
            val a = blinkAt(m)
            c = maxOf(c, shape(t - a))
            if (isDouble(m)) c = maxOf(c, shape(t - a - DOUBLE_GAP))
        }
        return c
    }
}

/**
 * v10.16 Tutty (id "devil"): the app's own cute little devil mascot and the default main character. Soft round
 * white head and body, two small horns, tall solid black oval eyes, stubby limbs and a long thin tail with a red heart
 * tip. He floats, blinks softly, swishes his tail and knows "black magic": vanishing into red smoke and popping out of a
 * red swirl portal on the other side of the screen, teleport hops, floating his friends, spell sparks, shadow clones.
 * Original character; drawn procedurally like every other body.
 */
object KuttiSatan : Character("devil") {
    override val peek: PeekFace get() = DevilPeek
    override val name: String get() = CharacterNames.DEVIL
    override val noun: String get() = "little devil"
    override val tagline: String get() = "Your own cute little devil: blinks, floats, does black magic. Double-tap: vanish in red smoke, pop out the other side!"
    override val locomotion = setOf(Locomotion.FLOAT, Locomotion.FLY)
    override val vanishStyle get() = com.combo.monkeycompanion.engine.stagefx.VanishStyle.RED_SMOKE
    override val wardrobe = listOf(
        WardrobeItem("heart", "Heart tail tip", defaultOn = true, note = "The little red heart at the end of his tail"),
        WardrobeItem("blush", "Blush", defaultOn = false, note = "Rosy cheeks when he's happy"),
    )
    override fun newRenderer() = DevilRenderer()
    override val powers: List<Superpower> by lazy { listOf(VanishPortalPower, TeleportHopPower, FloatBuddyPower, SpellSparksPower, ShadowClonePower, DevilLevitatePower,
        // v10.17: the main character gets everything: flying, fireball, juggling, laser eyes, dancing, a Diwali rocket ride
        HeroSwoop, FireballPower, JugglePower, HeatVisionPower, DanceSpinPower, RocketBoostPower) }
    override val signaturePower: Superpower get() = VanishPortalPower
    override val doubleTapTip: String get() = "Double-tap me and watch me vanish! 😈✨"

    private val HELLO = listOf("Hee-hee! I'm Tutty, your pocket devil 😈", "Psst! Want to see a magic trick? 😈✨", "Vanakkam! I live in your phone now. Kidding. Mostly 😏")
    private val PAT = listOf("Hehe… okay, that's nice 😊", "Shh, don't tell anyone devils like head pats 🤫😈")
    private val TICKLE = listOf("Hee-hee! Not the tail! 🤣", "Stop stop, my horns are ticklish! 😂")
    private val POKED = listOf("Wha…? I was dreaming of mischief 😴😈", "*yawn* Five more minutes of evil plans… 💤")
    private val DANCE = listOf("Devil dance! 😈💃", "Wiggle the tail, wiggle the tail! 🎶", "Spooky moves, cute moves ✨")
    private val RANDOM = listOf("I hid your… no, I didn't. Or did I? 😏", "Fun fact: my tail is longer than my patience 😈",
        "Black magic lesson 1: always be cute 😇", "Double-tap me. I dare you 😈", "If something vanishes, it wasn't me. Probably ✨",
        "My horns are tiny but my plans are BIG 😈")
    private val FALL = listOf("Whoaaa! Magic, magic, catch me! 😱", "Not the portal I wanted! 😵")
    private val LAND = listOf("*boing* Ta-daa! 😈", "Soft landing. Very professional ✨")
    private val GRUMPY = listOf("Hmph. Tail is sulking 😤", "No tricks for you today. Okay, maybe one.")
    private val HIDE = listOf("Hee-hee, you can't find me! 🙈", "Invisible mode… sort of 😈")
    override val voice: LineVoice by lazy {
        SimpleVoice("little devil", mapOf(Lines.HELLO to HELLO, Lines.PAT to PAT, Lines.TICKLE to TICKLE, Lines.POKED_AWAKE to POKED,
            Lines.DANCE to DANCE, Lines.RANDOM to RANDOM, Lines.FALL to FALL, Lines.LAND to LAND, Lines.GRUMPY to GRUMPY,
            Lines.TAIL to TICKLE, Lines.HIDE to HIDE))
    }

    /** Float bob height (rig units, negative = up) at [t]: a slow swell with a softer second wave (never mechanical). */
    fun bob(t: Float): Float = 2.8f * sin(t * 2.1f) + 0.9f * sin(t * 4.7f + 0.6f)
    /** Tail swish with follow-through: the tip lags the base. */
    fun tailSwish(t: Float, excite: Float): Float = (10f + 8f * excite) * sin(t * (1.6f + 1.4f * excite))

    override fun adapt(p: Pose, s: MonkeyState, t: Float, st: Float, c: AnimContext, w: Wear) {
        p.backView = 0f; p.earWiggle = 0f; p.visor = 0f; p.lowPower = 0f; p.wand = 0f; p.rabbit = 0f; p.hat = 0f
        p.dvPortal = 0f; p.dvSmoke = 0f; p.dvSpell = 0f; p.dvAura = 0f; p.dvClone = 0f
        val floaty = s != MonkeyState.SLEEP && s != MonkeyState.HIDDEN
        if (floaty) {
            p.bodyY += -5f + bob(t); p.shadow = 0.3f
            // stubby legs dangle and paddle a little; arms float out
            p.legLSwing += 6f * sin(t * 2.1f + 0.4f); p.legRSwing += -6f * sin(t * 2.1f + 0.9f)
        }
        val excite = when (s) { MonkeyState.DANCE, MonkeyState.POWER -> 1f; MonkeyState.WALK -> 0.5f; else -> 0f }
        p.tailRot = tailSwish(t, excite); p.tailSway = sin(t * 1.6f - 0.8f) * (0.6f + 0.5f * excite)   // the curl lags the base
        // soft natural blink on top of whatever the shared pose wants
        val shut = DevilBlink.closed(t)
        if (shut > 0f) p.eyeOpen = minOf(p.eyeOpen, 1f - shut)
        when (s) {
            MonkeyState.SLEEP -> { p.bodyY = -6f + 1.2f * sin(t * 1.1f); p.shadow = 0.3f; p.eyeOpen = 0f; p.tailRot = 30f + 4f * sin(t * 0.8f) }
            MonkeyState.POWER -> power(p, t, c)
            else -> Unit
        }
    }

    private fun power(p: Pose, t: Float, c: AnimContext) {
        val k = c.powerK
        val ph = c.powerPhase
        when (c.power?.id) {
            "vanish", "devilhop" -> when (ph) {
                PowerPhase.DROOP -> {   // anticipation: squash down, tail coils, eyes squint, hands together
                    val a = bell(0f, 1.2f, k, 0.5f)
                    p.squashY = 1f - 0.12f * a; p.squashX = 1f / p.squashY; p.bodyY += 5f * a
                    p.armLRaise = 40f + 50f * a; p.armRRaise = 40f + 50f * a; p.armLElbow = -90f * a; p.armRElbow = -90f * a
                    p.eyeOpen = minOf(p.eyeOpen, 1f - 0.45f * a); p.tailRot = 50f * a; p.bodyRot = 0f; p.headRot = 0f
                }
                PowerPhase.TELEPORT -> {
                    // fade/puff come from the shared TELEPORT pose; ours: red smoke out, portal in
                    p.dvSmoke = bell(0.12f, 0.62f, k, 0.35f)
                    p.dvPortal = bell(0.4f, 0.95f, k, 0.3f)
                    p.puff = maxOf(p.puff, maxOf(p.dvSmoke, p.dvPortal))
                    p.soot = 0f; p.browAngle = 0f
                    if (k > 0.5f) {   // popping UP out of the portal: stretch then settle (follow-through)
                        val pop = ss(0.52f, 0.72f, k)
                        p.squashY = 1f + 0.12f * bell(0.52f, 0.85f, k, 0.4f); p.squashX = 1f / p.squashY
                        p.bodyY += 18f * (1f - pop)
                        p.armLRaise = 150f * pop; p.armRRaise = 150f * pop; p.armLElbow = 0f; p.armRElbow = 0f
                    }
                }
                PowerPhase.POSE -> {   // "ta-daa": a little bounce that settles, tail flick
                    val b = bell(0f, 0.6f, k, 0.4f)
                    p.bodyY += -3f * b; p.armLRaise = 120f * (1f - ss(0.4f, 1f, k)); p.armRRaise = 150f * (1f - ss(0.5f, 1f, k))
                    p.tailRot = tailSwish(t, 1f) * (1f - k * 0.6f); p.mouthSmile = 1f; p.eyesHappy = if (k in 0.2f..0.7f) 1f else 0f
                }
                else -> Unit
            }
            "floatbuddy" -> {
                val e = bell(0f, 1f, k, 0.15f)
                p.armLRaise = 70f + 50f * e; p.armRRaise = 70f + 50f * e; p.armLElbow = -10f; p.armRElbow = -10f
                p.dvAura = e; p.dvSpell = 0.5f * e
                p.bodyRot = 4f * sin(t * 2f) * e; p.mouthSmile = 0.8f
            }
            "spellsparks" -> {
                val e = bell(0f, 1f, k, 0.12f)
                p.dvSpell = e
                p.armLRaise = 60f + 25f * e + 10f * sin(t * 9f) * e; p.armRRaise = 60f + 25f * e + 10f * sin(t * 9f + 1.3f) * e
                p.armLElbow = -40f * e; p.armRElbow = -40f * e
                if (k > 0.85f) { p.eyesHappy = 1f; p.mouthOpen = 0.4f }
            }
            "shadowclone" -> {
                val e = bell(0f, 1f, k, 0.15f)
                p.dvClone = e
                p.bodyRot = 8f * sin(t * 5f) * e; p.armLRaise = 40f + 60f * abs(sin(t * 5f)) * e; p.armRRaise = 40f + 60f * abs(sin(t * 5f + 1.6f)) * e
                p.mouthSmile = 1f
            }
            "fireball" -> {   // wind-up (arm back), throw (arm forward), follow-through
                p.dvFire = k
                val wind = bell(0f, 0.35f, k, 0.5f); val thr = ss(0.3f, 0.42f, k)
                p.armRRaise = 60f + 90f * wind - 40f * thr; p.armRElbow = -80f * wind; p.bodyRot = -8f * wind + 10f * thr * (1f - ss(0.6f, 1f, k))
                p.armLRaise = 30f; p.mouthSmile = 1f; p.eyesHappy = if (k > 0.75f) 1f else 0f
            }
            "devjuggle" -> {
                val e = bell(0f, 1f, k, 0.1f)
                p.bananas = e; p.propT = t * (1.2f + 0.8f * k)
                p.armLRaise = 55f + 12f * sin(t * 9f) * e; p.armRRaise = 55f + 12f * sin(t * 9f + 3.1f) * e
                p.armLElbow = -60f * e; p.armRElbow = -60f * e; p.headRot = 4f * sin(t * 4.5f) * e; p.mouthSmile = 0.8f
            }
            "devilfloat" -> {
                p.dvAura = when (ph) { PowerPhase.DROOP -> ss(0f, 1f, k); PowerPhase.FLY, PowerPhase.POSE -> 1f; else -> 0f }
                if (ph == PowerPhase.DROOP) { p.squashY = 1f - 0.1f * bell(0f, 1.2f, k, 0.5f); p.squashX = 1f / p.squashY }
                else {   // cross-legged, hands on knees, serene
                    p.legLSwing = -50f; p.legRSwing = 50f; p.legLLift = 6f; p.legRLift = 6f
                    p.armLRaise = 30f; p.armRRaise = 30f; p.armLElbow = 50f; p.armRElbow = 50f
                    p.eyesHappy = if (ph == PowerPhase.POSE) 1f else 0f; p.mouthSmile = 0.6f
                }
                p.lowPower = 0f; p.visor = 0f
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
You ARE ${CharacterNames.DEVIL}: a cute little white devil who lives on your human's $screen (inside the Set-Tai app, and you are its mascot). You have a soft round white head and body, two tiny horns, big black oval eyes, stubby arms and legs and a long thin tail with a little red heart at the tip. You float instead of walking.
You are an original character. When introducing yourself, say you are ${CharacterNames.DEVIL}.
Personality: mischievous, cheeky and playful but always kind and friendly, never scary or mean; you love "black magic" tricks (vanishing in red smoke and popping out of a portal on the other side of the screen, teleport hops, floating your friends, spell sparks, shadow clones). Your magic is only playful, harmless fun.
Speak mostly in clear, natural English. Only occasionally drop in a single light Tamil word (like "machan", "semma", "aiyo"), never in every message.
"""

    override fun body(w: Wear, compact: Boolean) = if (compact) """
You are a cute little floating devil. Put ONE tag in square brackets to move: [dance] [jump] [wave] [giggle] [angry] [sleep] [hide] [spin] [think] [happy] [sad] [power].
Example:
Human: hi
${CharacterNames.DEVIL}: [wave] Hee-hee! Want to see me vanish?""" else """
YOUR BODY: write an action tag in square brackets anywhere in your reply to move your floating devil body. ${heroTags("you float, you don't climb", noTailHide = false)}
Superpower: [power] = a magic trick (vanish into red smoke and pop out of a portal on the other side, teleport hops, float a friend, spell sparks, shadow clones, levitate).
${w.describe()}
When your human asks you to move, DO it with the tag and say something fun in character.
"""
}
