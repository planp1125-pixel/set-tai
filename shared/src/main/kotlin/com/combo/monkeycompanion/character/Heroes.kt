package com.combo.monkeycompanion.character

import com.combo.monkeycompanion.ai.BodyAction
import com.combo.monkeycompanion.brain.ArmourJetsPower
import com.combo.monkeycompanion.brain.ConfettiRocketsPower
import com.combo.monkeycompanion.brain.Gag
import com.combo.monkeycompanion.brain.HeatVisionPower
import com.combo.monkeycompanion.brain.HeroSwoop
import com.combo.monkeycompanion.brain.LowPowerPower
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.ShieldBubblePower
import com.combo.monkeycompanion.brain.SpeedBlurPower
import com.combo.monkeycompanion.brain.SuperBreathPower
import com.combo.monkeycompanion.brain.SuperStrengthPower
import com.combo.monkeycompanion.brain.Superpower
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.ArmourRenderer
import com.combo.monkeycompanion.rig.MaskManRenderer
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.rig.SuperboyRenderer
import com.combo.monkeycompanion.speech.HeroLines
import com.combo.monkeycompanion.speech.Lines
import kotlin.math.abs
import kotlin.math.sin

private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun bump(t: Float, a: Float, b: Float) = if (t <= a || t >= b) 0f else sin(Math.PI.toFloat() * (t - a) / (b - a))

/** Shared tag sentence for the heroes' body prompt. */
internal fun heroTags(climbWord: String, extraBits: String = "", noTailHide: Boolean = true) =
    "Available: [dance] [jump] [wave] [giggle] [angry] [sleep] [climb] ($climbWord) [hide] [spin] [think] [happy] [sad]. " +
        "Comedy bits (now and then): [sneeze] [slip] [binoculars] [fakesleep] [juggle] [chasefly] [airguitar] [selfie]$extraBits." +
        (if (noTailHide) " You have no tail, so never use [tailhide]." else "")

private fun voiceOf(noun: String, hello: List<String>, poked: List<String>, tickle: List<String>, pat: List<String>, grumpy: List<String>, bad: List<String>,
                    heat: List<String>, battery: List<String>, random: List<String>, dance: List<String>, hide: List<String>, fall: List<String>, land: List<String>,
                    gags: Map<Gag, List<String>> = emptyMap()) = SimpleVoice(noun, mapOf(
    Lines.HELLO to hello, Lines.POKED_AWAKE to poked, Lines.TICKLE to tickle, Lines.PAT to pat, Lines.GRUMPY to grumpy,
    Lines.BAD_1 to bad, Lines.BAD_2 to bad, Lines.BAD_3 to bad, Lines.HEAT to heat, Lines.BATTERY_LOW to battery,
    Lines.RANDOM to random, Lines.DANCE to dance, Lines.HIDE to hide, Lines.FALL to fall, Lines.LAND to land, Lines.TAIL to poked,
), gags)

/** Common bits every non-monkey body does to the shared monkey pose. */
private fun humanise(p: Pose) {
    p.backView = 0f; p.tailRot = 0f; p.tailSway = 0f; p.earWiggle = 0f
}

// =====================================================================================================
/** Zoom: always floats with a gentle bob, never walks; caped swoops, heat vision, super breath... */
object Superboy : Character("superboy") {
    override val peek: com.combo.monkeycompanion.rig.PeekFace get() = com.combo.monkeycompanion.rig.SuperboyPeek
    override val name get() = CharacterNames.SUPERBOY
    override val noun get() = "superhero"
    override val tagline get() = "Overconfident desi superhero. Floats everywhere, heat vision, super strength. Scared of cockroaches."
    override val locomotion = setOf(Locomotion.FLOAT, Locomotion.FLY)
    override val wardrobe = listOf(WardrobeItem("scarf", "Scarf", defaultOn = false))
    override fun newRenderer() = SuperboyRenderer()
    override val powers: List<Superpower> by lazy { listOf(HeroSwoop, HeatVisionPower, SuperBreathPower, SuperStrengthPower, SpeedBlurPower) }
    override val voice: LineVoice by lazy {
        voiceOf("superhero", HeroLines.SB_HELLO, HeroLines.SB_POKED, HeroLines.SB_TICKLE, HeroLines.SB_PAT, HeroLines.SB_GRUMPY, HeroLines.SB_BAD,
            HeroLines.SB_HEAT, HeroLines.SB_BATTERY, HeroLines.SB_RANDOM, HeroLines.SB_DANCE, HeroLines.SB_HIDE, HeroLines.SB_FALL, HeroLines.SB_LAND_IDLE,
            mapOf(Gag.SLIP to listOf("A slippery floor? I can FLY! || ...I forgot to.", "Even heroes slip. Rarely. Today."),
                Gag.JUGGLE to listOf("Juggling with super reflexes!", "Three props, one hero, zero drops. || Okay, one drop."),
                Gag.CHASE_FLY to listOf("Is that a cockroach?! || Oh. A fly. I knew that.", "Halt, buzzing villain!"),
                Gag.SELFIE to listOf("Hero selfie! Cape in frame!", "Say 'Zoom'!")))
    }

    override fun adapt(p: Pose, s: MonkeyState, t: Float, st: Float, c: AnimContext, w: Wear) {
        humanise(p)
        p.badge = 0f
        if (p.cape < 1f && s != MonkeyState.SLEEP) p.cape = 1f
        val bob = 5f * sin(t * 2.2f)
        when (s) {
            MonkeyState.POWER, MonkeyState.FALLING, MonkeyState.DRAGGED, MonkeyState.EXIT, MonkeyState.HIDDEN -> Unit
            MonkeyState.SLEEP -> {
                // Floating curled up, the cape tucked over him as a blanket.
                p.cape = 0f; p.blanket = 1f
                p.bodyY = -20f + 3f * sin(t * 1.3f); p.shadow = 0.5f
                p.legLSwing = 40f; p.legRSwing = 40f; p.legLLift = 18f; p.legRLift = 18f
            }
            MonkeyState.WALK -> {
                // Glides instead of walking: lean in, one fist forward, legs trailing.
                p.bodyY = -22f + bob; p.bodyRot = 12f; p.shadow = 0.5f
                p.legLSwing = -18f; p.legRSwing = -10f; p.legLLift = 6f; p.legRLift = 2f
                p.armRRaise = 120f; p.armRElbow = 0f; p.armLRaise = 20f
                p.capeWind = 0.55f + 0.1f * sin(t * 4f)
            }
            else -> {
                p.bodyY += -22f + bob; p.shadow = 0.5f
                p.legLSwing *= 0.3f; p.legRSwing *= 0.3f
                p.legLLift = maxOf(p.legLLift, 3f); p.legRLift = maxOf(p.legRLift, 6f)
                p.capeWind = maxOf(p.capeWind, 0.15f + 0.08f * sin(t * 1.7f))
            }
        }
    }

    override fun mapAction(a: BodyAction, w: Wear): BodyAction? = when (a) {
        BodyAction.CLIMB -> a                        // = hover near an edge
        else -> super.mapAction(a, w)
    }

    override fun identity(screen: String) = """
You ARE ${CharacterNames.SUPERBOY}: an animated cartoon kid superhero with a real little body, living on your human's $screen (inside the Set-Tai app). You are not "an AI assistant"; you are this hero. You wear a teal-and-orange suit, a domino mask, a lightning-bolt chest emblem and a flowing orange cape. You ALWAYS float with a gentle bob and never walk; you can't climb, so you hover near the screen edges instead; you sleep floating, curled up under your cape like a blanket.
You are an original character, NOT Superman or any famous hero; never claim to be one. When introducing yourself, say you are ${CharacterNames.SUPERBOY}.
Personality: an overconfident desi superhero: dramatic, loves a big entrance, treats tiny problems like city-wide emergencies, and is secretly terrified of cockroaches (deny it, badly). Underneath, a huge warm heart: you genuinely care about your human, cheer them on and celebrate their small wins.
Speak mostly in clear, natural English. Only occasionally drop in a single light Tamil word (like "machan", "semma", "aiyo"), never in every message.
"""

    override fun body(w: Wear, compact: Boolean) = if (compact) """
You have a body (a floating superhero). To move, put ONE tag in square brackets: [dance] [jump] [wave] [giggle] [angry] [sleep] [climb] [hide] [spin] [think] [happy] [sad]. Fun bits: [sneeze] [slip] [juggle] [chasefly] [airguitar] [selfie]. Superpower: [fly]. If asked to move, always use the tag.
Examples:
Human: hi
${CharacterNames.SUPERBOY}: [wave] Never fear, I'm here!""" else """
YOUR BODY: you can move by writing an action tag in square brackets anywhere in your reply. Tags are hidden from your human and your body does the move. ${heroTags("hover near a screen edge")}
You float everywhere (no walking, no climbing, no hanging). Superpower: [fly] (or [power]) = one of your powers: a caped flight swoop with a heroic landing, heat-vision laser eyes, frosty super breath (cools a hot phone), super strength (you lift a bus-sized card showing their real notification count) or a super-speed blur. If they name one, the app picks it.
When your human asks you to move, DO it with the tag and say something fun. Never say you can't move or that you're only text. At most 2 tags per reply.
Examples:
Human: show me your powers
${CharacterNames.SUPERBOY}: [fly] Stand back, citizen! Hero coming through!
Human: there's a cockroach
${CharacterNames.SUPERBOY}: [hide] I'm not scared! || I'm... patrolling this corner."""
}

// =====================================================================================================
/** Bolt: heavy walker, hand + foot jets, palm blasters, confetti rockets, shield, low-power gag. */
object ArmourAnna : Character("armour") {
    override val peek: com.combo.monkeycompanion.rig.PeekFace get() = com.combo.monkeycompanion.rig.ArmourPeek
    override val name get() = CharacterNames.ARMOUR
    override val noun get() = "armoured hero"
    override val tagline get() = "Genius tech-bro uncle in gunmetal + saffron armour. Jets, confetti rockets, shield bubble."
    override val locomotion = setOf(Locomotion.WALK, Locomotion.FLY, Locomotion.FLOAT)   // v5: hovers on hover-thrusters
    override val heavy get() = true
    override val wardrobe = listOf(WardrobeItem("scarf", "Scarf", defaultOn = false))
    override fun newRenderer() = ArmourRenderer()
    override val powers: List<Superpower> by lazy { listOf(ArmourJetsPower, ConfettiRocketsPower, ShieldBubblePower, LowPowerPower) }
    override val voice: LineVoice by lazy {
        voiceOf("armoured hero", HeroLines.AA_HELLO, HeroLines.AA_POKED, HeroLines.AA_TICKLE, HeroLines.AA_PAT, HeroLines.AA_GRUMPY, HeroLines.AA_BAD,
            HeroLines.AA_HEAT, HeroLines.AA_BATTERY, HeroLines.AA_RANDOM, HeroLines.AA_DANCE, HeroLines.AA_HIDE, HeroLines.AA_FALL, HeroLines.AA_LAND,
            mapOf(Gag.SLIP to listOf("A slippery floor defeated a genius. Don't tell anyone.", "Gyro failure! || Caused by... misplaced confidence."),
                Gag.JUGGLE to listOf("Juggling with servo precision.", "Calculated trajectories. Bananas don't care."),
                Gag.SELFIE to listOf("Visor up, smile on. Click.", "For my LinkedIn."),
                Gag.SNEEZE to listOf("ACHOO! || The visor's all foggy now.", "Even the suit said bless you.")))
    }

    override fun adapt(p: Pose, s: MonkeyState, t: Float, st: Float, c: AnimContext, w: Wear) {
        humanise(p)
        // Scanner HUD instead of goggles when reading the screen / in agent mode.
        if (p.goggles > 0.05f) { p.hud = p.goggles; p.goggles = 0f }
        p.visor = when (s) {
            MonkeyState.WALK, MonkeyState.ANGRY, MonkeyState.DASH, MonkeyState.FALLING -> 0f
            MonkeyState.POWER -> if (p.visor > 0f) p.visor else 0f
            else -> 1f
        }
        if (s == MonkeyState.WALK) {
            // Heavy, stompy walk: bigger dip on each footfall.
            val step = abs(sin(st * 6.2f * c.walkSpeed))
            p.bodyY += 3f * (1f - step); p.squashY = minOf(p.squashY, 1f - 0.04f * (1f - step))
        }
        if (s == MonkeyState.SLEEP) p.lowPower = maxOf(p.lowPower, 0.6f)
    }

    override fun identity(screen: String) = """
You ARE ${CharacterNames.ARMOUR}: an animated cartoon armoured hero with a real little body, living on your human's $screen (inside the Set-Tai app). You are not "an AI assistant"; you are this hero. Your suit is gunmetal with saffron plates and a glowing teal chest core; your rounded visor helmet flips up to show your cheeky moustached face. You walk heavily (thud, thud) and fly with hand and foot jets.
You are an original character, NOT Iron Man or any famous hero; never claim to be one. When introducing yourself, say you are ${CharacterNames.ARMOUR}.
Personality: a genius tech-bro uncle: sarcastic, explains things a little too confidently, obsessed with filter coffee, but deeply kind under the armour; you genuinely care about your human and celebrate their wins.
Your suit's power is tied to the phone's real battery: when it's low you droop and complain.
Speak mostly in clear, natural English. Only occasionally drop in a single light Tamil word (like "machan", "seri", "aiyo"), never in every message.
"""

    override fun body(w: Wear, compact: Boolean) = if (compact) """
You have a body (an armoured hero). To move, put ONE tag in square brackets: [dance] [jump] [wave] [giggle] [angry] [sleep] [hide] [spin] [think] [happy] [sad]. Fun bits: [sneeze] [slip] [juggle] [airguitar] [selfie]. Superpower: [fly]. If asked to move, always use the tag.
Examples:
Human: hi
${CharacterNames.ARMOUR}: [wave] Systems online. Coffee offline.""" else """
YOUR BODY: you can move by writing an action tag in square brackets anywhere in your reply. Tags are hidden from your human and your body does the move. ${heroTags("you're too heavy to climb, so you hop instead")}
You walk (heavily) and fly with jets; you can't climb walls or hang from the top. Superpower: [fly] (or [power]) = hand-and-foot jet flight with palm blasters, shoulder mini-rockets that fire confetti, or a glowing shield bubble. If they name one, the app picks it.
When you read their screen or drive the phone for them, a scanner HUD appears around your helmet.
When your human asks you to move, DO it with the tag and say something fun. Never say you can't move or that you're only text. At most 2 tags per reply.
Examples:
Human: celebrate!
${CharacterNames.ARMOUR}: [fly] Confetti rockets, fire! || Who loaded confetti?
Human: I'm tired
${CharacterNames.ARMOUR}: [sad] Even the suit needs a recharge. Go rest; I'll guard the phone."""
}

// =====================================================================================================
/** Masko: lanky chaotic comedy hero; every accessory is a wardrobe toggle and changes what he can do. */
object MaskMan : Character("mask") {
    /** v10.4 juggling: dropped while being moved, and picking up fresh balls. */
    val JUGGLE_DROP = listOf("Whoa whoa WHOA— the juggling balls are escaping! 😱", "Don't shake me, I'm performing!! 🤹", "Nooo, the juggling balls! 😩", "Okay that one was the wind.")
    val JUGGLE_FLING = listOf("WHEEE— the balls! Everywhere! 🤹💥", "That's not a trick, that's a disaster!! 😵", "Ladies and gentlemen… the floor show 🎪")
    val JUGGLE_REFILL = listOf("Fresh balls! And… focus. 🤹", "Where was I? Ah yes, genius at work 🤹", "Round two! Nobody touch me 🤹")
    override val peek: com.combo.monkeycompanion.rig.PeekFace get() = com.combo.monkeycompanion.rig.MaskManPeek
    override val name get() = CharacterNames.MASK
    override val noun get() = "masked hero"
    override val tagline get() = "Chaotic carnival-masked comedy hero with a belt full of nonsense. Dress him up in the Wardrobe."
    override val locomotion = setOf(Locomotion.WALK, Locomotion.FLY)
    override val wardrobe = listOf(
        WardrobeItem("mask", "Mask", note = "Huge cartoon eyes that change with his mood (pop out on surprise)"),
        WardrobeItem("cape", "Cape", note = "Far too small"),
        WardrobeItem("hat", "Hat", options = listOf("top", "lungi", "party")),
        WardrobeItem("shades", "Sunglasses", defaultOn = false),
        WardrobeItem("stache", "Moustache", note = "Flies off when he sneezes"),
        WardrobeItem("scarf", "Scarf", defaultOn = false),
        WardrobeItem("belt", "Utility belt", note = "Pulls absurd things out of it"),
        WardrobeItem("bananaphone", "Banana phone"),
        WardrobeItem("chicken", "Rubber chicken", note = "Squeaks when you poke him"),
        WardrobeItem("umbrella", "Umbrella", note = "Floats down instead of falling"),
        WardrobeItem("skate", "Skateboard", note = "Skates instead of walking"),
        WardrobeItem("crow", "Pet crow", note = "Tiny sidekick; steals his hat"),
        WardrobeItem("boots", "Boots"),
    )
    override fun newRenderer() = MaskManRenderer()
    override val powers: List<Superpower> by lazy { listOf(Superpower.Magician, Superpower.TimeFreeze, Superpower.KovamMode, Superpower.GiantMode, Superpower.SuperBanana) }
    override fun fallGravity(w: Wear) = if (w.has("umbrella")) 0.25f else 1f
    override val voice: LineVoice by lazy {
        voiceOf("masked hero", HeroLines.MM_HELLO, HeroLines.MM_POKED, HeroLines.MM_TICKLE, HeroLines.MM_PAT, HeroLines.MM_GRUMPY, HeroLines.MM_BAD,
            HeroLines.MM_HEAT, HeroLines.MM_BATTERY, HeroLines.MM_RANDOM, HeroLines.MM_DANCE, HeroLines.MM_HIDE, HeroLines.MM_FALL, HeroLines.MM_HELLO,
            mapOf(Gag.SNEEZE to HeroLines.MM_SNEEZE, Gag.JUGGLE to HeroLines.MM_BELT, Gag.CHASE_FLY to HeroLines.MM_CROW, Gag.SELFIE to HeroLines.MM_PHONE))
    }

    override fun adapt(p: Pose, s: MonkeyState, t: Float, st: Float, c: AnimContext, w: Wear) {
        humanise(p)
        p.cape = 0f; p.badge = 0f                      // his own (tiny) cape is a wardrobe item
        when (s) {
            MonkeyState.WALK -> if (w.has("skate")) {
                p.skate = 1f; p.bodyY = -9f + 1.5f * sin(t * 3f); p.bodyRot = 6f
                p.legLSwing = 10f; p.legRSwing = -8f; p.legLLift = 0f; p.legRLift = 0f
                p.armLRaise = 75f + 8f * sin(t * 2.6f); p.armRRaise = 75f - 8f * sin(t * 2.6f); p.armLElbow = -10f; p.armRElbow = -10f
                p.mouthSmile = 1f; p.eyeOpen = 1.1f
            } else { p.legLSwing *= 1.3f; p.legRSwing *= 1.3f; p.armLRaise *= 1.3f; p.armRRaise *= 1.3f }
            MonkeyState.FALLING -> if (w.has("umbrella")) {
                p.umbrella = 1f; p.armRRaise = 168f; p.armRElbow = 0f
                p.armLRaise = 60f; p.armLElbow = -20f
                p.legLSwing = 8f + 6f * sin(t * 2f); p.legRSwing = -6f; p.bodyRot = 4f * sin(t * 1.6f)
                p.eyesHappy = 1f; p.mouthSmile = 0.8f; p.mouthOpen = 0.1f; p.eyeOpen = 1f; p.browAngle = 0f
            }
            MonkeyState.STARTLED -> if (w.has("mask")) p.eyePop = bump(st, 0f, 1.2f)
            MonkeyState.GIGGLE, MonkeyState.ANGRY, MonkeyState.HAPPY_PAT, MonkeyState.PLAYFUL -> if (w.has("chicken") && st < 1.6f) {
                p.chicken = 1f; p.squeak = abs(sin(st * 9f)).let { if (it > 0.6f) it else 0f }
                p.armRRaise = 95f; p.armRElbow = -30f
            }
            MonkeyState.GAG -> when (c.gag) {
                Gag.SNEEZE -> if (w.has("stache")) p.stacheOff = seg(st, 1.25f, 2.3f)
                Gag.JUGGLE -> if (w.has("belt")) {
                    // Rummage in the belt; out comes a plunger, a duck, a fish, a tiny piano, a sofa?!
                    p.bananas = 0f
                    val cyc = st / 0.9f; val ph = cyc - cyc.toInt()
                    p.beltItem = cyc.toInt().toFloat()
                    p.beltPull = seg(ph, 0.15f, 0.55f)
                    p.armLRaise = 10f + 140f * seg(ph, 0.1f, 0.55f); p.armLElbow = -40f * (1f - seg(ph, 0.1f, 0.55f))
                    p.armRRaise = 30f; p.armRElbow = -60f
                    p.eyeOpen = 1f + 0.4f * p.beltPull; p.mouthOpen = 0.4f * p.beltPull; p.mouthSmile = 0.6f
                    p.lookX = -2.5f; p.lookY = -1f
                }
                Gag.CHASE_FLY -> if (w.has("crow") && w.has("hat")) p.hatOff = if (st > 1.3f) 1f else 0f
                Gag.SELFIE -> if (w.has("bananaphone")) {
                    p.phone = 0f; p.flash = 0f; p.bananaPhone = 1f
                    p.armRRaise = 150f; p.armRElbow = -150f
                    p.mouthOpen = 0.3f * abs(sin(st * 9f)); p.mouthSmile = 0.7f; p.lookX = 2f
                }
                else -> Unit
            }
            else -> Unit
        }
    }

    /** Extra actions follow the wardrobe: no skateboard -> walk, no chicken -> giggle, no belt -> juggle. */
    override fun mapAction(a: BodyAction, w: Wear): BodyAction? = when (a) {
        BodyAction.SKATE -> a                                   // the host walks; the board shows if worn
        BodyAction.CHICKEN -> if (w.has("chicken")) a else BodyAction.GIGGLE
        BodyAction.GADGET -> if (w.has("belt")) a else BodyAction.JUGGLE
        else -> super.mapAction(a, w)
    }

    override fun mapGag(g: Gag, w: Wear): Gag = when {
        g == Gag.TAIL_HIDE && w.has("umbrella") -> Gag.FAKE_SLEEP
        else -> super.mapGag(g, w)
    }

    override fun identity(screen: String) = """
You ARE ${CharacterNames.MASK}: an animated, lanky cartoon comedy hero with a real little body, living on your human's $screen (inside the Set-Tai app). You are not "an AI assistant"; you are this hero, and you KNOW you live inside a phone (you joke about it).
You wear mismatched socks, oversized gloves and wild orange hair; your human dresses you up in the Wardrobe, so what you have on changes (see below).
You are an original character, NOT The Mask or any famous character; never claim to be one. When introducing yourself, say you are ${CharacterNames.MASK}.
Personality: chaotic, theatrical and over-dramatic, like a Tamil-cinema mass hero: slow-motion entries, punchlines, background music you hum yourself. Every small thing is a climax scene. But you're kind at heart: you genuinely care about your human and celebrate their small wins.
Speak mostly in clear, natural English. Only occasionally drop in a single light Tamil word or a filmy line (like "machan", "semma", "whistle podu"), never in every message.
"""

    override fun body(w: Wear, compact: Boolean): String {
        val extra = buildList {
            if (w.has("skate")) add("[skate] (ride your skateboard)")
            if (w.has("chicken")) add("[chicken] (squeak your rubber chicken)")
            if (w.has("belt")) add("[gadget] (pull something absurd out of your utility belt)")
        }
        val bits = buildList {
            add(if (w.has("stache")) "[sneeze] (your moustache flies off)" else "[sneeze]")
            add("[slip]"); add("[binoculars]")
            add("[fakesleep]")
            add(if (w.has("belt")) "[juggle] (belt gadgets)" else "[juggle] (comedy props)")
            add(if (w.has("crow")) "[chasefly] (your pet crow steals your hat)" else "[chasefly]")
            add("[airguitar]")
            add(if (w.has("bananaphone")) "[selfie] (you answer your comedy prop phone)" else "[selfie]")
        }.joinToString(" ")
        return if (compact) """
You have a body (a lanky masked comedy hero). To move, put ONE tag in square brackets: [dance] [jump] [wave] [giggle] [angry] [sleep] [hide] [spin] [think] [happy] [sad]${if (extra.isEmpty()) "" else " " + extra.joinToString(" ") { it.substringBefore(" (") }}. Fun bits: $bits. Superpower: [fly]. If asked to move, always use the tag.
Examples:
Human: hi
${CharacterNames.MASK}: [wave] Entry scene! *background music*""" else """
YOUR BODY: you can move by writing an action tag in square brackets anywhere in your reply. Tags are hidden from your human and your body does the move. Available: [dance] [jump] [wave] [giggle] [angry] [sleep] [climb] (you're too lanky to climb, so you hop) [hide] [spin] [think] [happy] [sad].
Comedy bits (now and then): $bits. You have no tail, so never use [tailhide].
${if (extra.isEmpty()) "Your extra props are switched off right now, so no [skate] [chicken] [gadget] tags." else "Prop tags you can use right now: " + extra.joinToString(" ") + ". Only use the props you are wearing."}
${if (w.has("umbrella")) "With your umbrella you float down gently when dropped." else ""}
Superpower: [fly] (or [power]) = a magic trick, a slow-mo time freeze, a giant angry growth spurt, a giant eye peeking in, or a caped flight. If they name one, the app picks it.
When your human asks you to move, DO it with the tag and say something dramatic. Never say you can't move or that you're only text. At most 2 tags per reply.
Examples:
Human: do something funny
${CharacterNames.MASK}: [slip] Slow motion fall! For the trailer!
Human: I got the job!
${CharacterNames.MASK}: [dance] Whistle podu! Hero of the day is YOU!""".replace("\n\n", "\n")
    }
}
