package com.combo.monkeycompanion.engine.touch

import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.speech.Lines

/**
 * v10.8 reaction verbs: short procedural clips with parameters (see [ReactionLayer] for how each one moves).
 * [LEGACY] = the exact pre-v10.8 per-zone reaction (MoodController.onTap), so the old feel keeps showing up.
 */
enum class Verb(val mood: Mood) {
    LEGACY(Mood.NEUTRAL),
    FLINCH(Mood.STARTLED), GIGGLE(Mood.PLAYFUL), LEAN_IN(Mood.AFFECTION), NOD(Mood.AFFECTION), SHOW_OFF(Mood.PLAYFUL),
    TAIL_YELP(Mood.STARTLED), TAIL_SWAT(Mood.ANNOYED), TAIL_CURL(Mood.AFFECTION),
    EAR_FLICK(Mood.ANNOYED), EAR_WIGGLE(Mood.PLAYFUL), TRUNK_BOOP(Mood.PLAYFUL),
    SPIN_LOOK(Mood.STARTLED), PAW_BAT(Mood.PLAYFUL), DODGE(Mood.STARTLED), HOP(Mood.PLAYFUL), DIZZY(Mood.STARTLED),
    // Rocket Girl
    ROCKET_SPUTTER(Mood.STARTLED), LOOP(Mood.PLAYFUL), SPARKLER(Mood.AFFECTION), BOOST(Mood.PLAYFUL),
    ;
    enum class Mood { NEUTRAL, PLAYFUL, AFFECTION, STARTLED, ANNOYED }
}

/**
 * One entry of a character's reaction library: a verb, where and how it can be triggered, its base weight and its
 * parameter ranges. [amp] scales the clip's authored amplitude, [dur] is seconds at tempo 1. [baseState] (optional)
 * also plays a body state on the controller for the base motion; [lines] is a Lines pool (null = no line);
 * [forceBias] > 0 prefers hard touches, < 0 gentle ones; [minRepeats] = only after that many quick repeats.
 */
class ReactionEntry(
    val verb: Verb,
    val parts: Set<BodyPart>,
    val weight: Float,
    val kinds: Set<TouchKind> = TAP,
    val amp: ClosedFloatingPointRange<Float> = 0.7f..1f,
    val dur: ClosedFloatingPointRange<Float> = 0.5f..0.8f,
    val baseState: MonkeyState? = null,
    val lines: List<String>? = null,
    val talkChance: Float = 0.45f,
    val forceBias: Float = 0f,
    val minRepeats: Int = 0,
    /** Anticipation share of the clip (0 = a surprise: no wind-up). */
    val antic: Float = 0.15f,
) {
    companion object {
        val TAP = setOf(TouchKind.TAP)
        val HOLD = setOf(TouchKind.HOLD)
        val FLICK = setOf(TouchKind.FLICK)
        val TAP_HOLD = setOf(TouchKind.TAP, TouchKind.HOLD)
    }
}

/** Which optional body bits a character has, so the shared library only offers verbs it can actually do. */
data class BodyFeatures(val tail: Boolean = false, val ears: Boolean = false, val trunk: Boolean = false, val prop: Boolean = false)

/** Short generic lines for the new verbs ("monkey" / "Settai" get polished into the character's own noun / name). */
object ReactionLines {
    val FLINCH = listOf("Eek! 😳", "Whoa! You scared me!", "Hey! Gentle! 😅", "Ayyo! 🙀")
    val LEAN_IN = listOf("Mmm… right there 🥰", "More head pats please ✨", "*melts* 😌")
    val NOD = listOf("Mm-hm! 😊", "Yes yes!", "Agreed, machan 👍")
    val SHOW_OFF = listOf("Watch this! ✨", "Ta-daaa! 🤩", "Did you see that?! 😎")
    val TAIL_SWAT = listOf("Not the tail again! 😤", "Tail is off limits! 🙅", "Swish! Hands off 😾")
    val TAIL_CURL = listOf("*happy tail curl* 💕", "Okay, that's nice 😊")
    val EAR = listOf("My ears are ticklish! 🙉", "*ear flick* Heh 😄", "Hey, those are my antennas!")
    val TRUNK = listOf("Boop! 🐘", "*trunk wiggle* Hee hee!", "My nose! 😂")
    val SPIN = listOf("Who's there?! 👀", "Huh?! Oh, it's you 😄")
    val PAW = listOf("High five! ✋", "Gotcha! 🐾", "Pat-a-cake! 😆")
    val DODGE = listOf("Missed me! 😜", "Too slow! 💨", "Hehe, nope! 😆")
    val HOP = listOf("Boing! 🐰", "Wheee! 😄")
    val DIZZY = listOf("Whoaaa, dizzy… 😵‍💫", "Everything is spinning! 🌀")
    // Rocket Girl
    val ROCKET_TAP = listOf("Hey! Don't poke the rocket! 🚀💨", "*pop pop sputter* Uh-oh! 😅", "It's ticklish, it coughs! 🎇")
    val LOOP = listOf("Loop-de-loop! 🎢", "Wheeeee! 🚀", "Did you see that loop?! 😎")
    val SPARKLER = listOf("Phuljhadi time! ✨🎇", "Look, sparkles! ✨", "Happy Deepavali vibes! 🪔")
    val BOOST = listOf("Vroom vroom! ✨🚀", "Sparkle sparkle! ✨")
    val HEAD = listOf("Hee hee! 😆", "Ha ha, stop it! 😂", "That tickles! 😄")

    /** All new pools (tests: every one non-empty). */
    val all = listOf(FLINCH, LEAN_IN, NOD, SHOW_OFF, TAIL_SWAT, TAIL_CURL, EAR, TRUNK, SPIN, PAW, DODGE, HOP, DIZZY, ROCKET_TAP, LOOP, SPARKLER, BOOST, HEAD)
}

/**
 * The shared, data-driven reaction library. A character's table = the LEGACY entry for each old zone (so the v9.5
 * reaction still appears) + the common verbs its [BodyFeatures] allow + the character's own extra entries from its
 * profile. No per-character code: everything is in these tables.
 */
object ReactionLibrary {
    private val HEADS = setOf(BodyPart.HEAD, BodyPart.EAR, BodyPart.TRUNK)
    private val TICKLY = setOf(BodyPart.NECK, BodyPart.BELLY)
    private val LIMBS = setOf(BodyPart.HAND, BodyPart.FOOT)
    private val ANY = setOf(BodyPart.HEAD, BodyPart.NECK, BodyPart.BELLY, BodyPart.TAIL, BodyPart.HAND, BodyPart.FOOT, BodyPart.EAR, BodyPart.TRUNK, BodyPart.PROP)

    /** The old per-zone reactions, one LEGACY entry per zone (weights keep them the single most likely pick). */
    val LEGACY = listOf(
        ReactionEntry(Verb.LEGACY, setOf(BodyPart.HEAD, BodyPart.EAR, BodyPart.TRUNK), 1.6f, ReactionEntry.TAP_HOLD),
        ReactionEntry(Verb.LEGACY, TICKLY, 1.6f, ReactionEntry.TAP_HOLD),
        ReactionEntry(Verb.LEGACY, setOf(BodyPart.TAIL), 1.5f, ReactionEntry.TAP_HOLD),
        ReactionEntry(Verb.LEGACY, LIMBS, 1.5f, ReactionEntry.TAP_HOLD),
    )

    val COMMON = listOf(
        ReactionEntry(Verb.FLINCH, ANY, 0.7f, amp = 0.6f..1f, dur = 0.24f..0.36f, lines = ReactionLines.FLINCH, talkChance = 0.3f, forceBias = 1f, antic = 0f),
        ReactionEntry(Verb.GIGGLE, TICKLY, 1.1f, ReactionEntry.TAP_HOLD, amp = 0.6f..1f, dur = 0.9f..1.8f, baseState = MonkeyState.GIGGLE, lines = Lines.TICKLE, talkChance = 0.6f),
        ReactionEntry(Verb.LEAN_IN, setOf(BodyPart.HEAD), 1f, ReactionEntry.TAP_HOLD, amp = 0.6f..1f, dur = 1.0f..1.6f, lines = ReactionLines.LEAN_IN, forceBias = -1f, antic = 0.1f),
        ReactionEntry(Verb.NOD, setOf(BodyPart.HEAD), 0.6f, amp = 0.6f..1f, dur = 0.6f..0.9f, lines = ReactionLines.NOD, talkChance = 0.3f),
        ReactionEntry(Verb.SHOW_OFF, setOf(BodyPart.HEAD), 1.4f, amp = 0.8f..1f, dur = 0.9f..1.2f, lines = ReactionLines.SHOW_OFF, talkChance = 0.6f, minRepeats = 2, antic = 0.2f),
        ReactionEntry(Verb.SPIN_LOOK, setOf(BodyPart.BELLY, BodyPart.FOOT, BodyPart.TAIL), 0.5f, amp = 0.6f..1f, dur = 0.4f..0.6f, lines = ReactionLines.SPIN, talkChance = 0.25f),
        ReactionEntry(Verb.PAW_BAT, LIMBS, 1f, amp = 0.6f..1f, dur = 0.5f..0.8f, lines = ReactionLines.PAW, talkChance = 0.4f, antic = 0.2f),
        ReactionEntry(Verb.DODGE, ANY, 0.5f, amp = 0.6f..1f, dur = 0.3f..0.45f, lines = ReactionLines.DODGE, talkChance = 0.35f, forceBias = 1.2f, antic = 0.12f),
        ReactionEntry(Verb.HOP, setOf(BodyPart.BELLY, BodyPart.FOOT), 0.6f, amp = 0.6f..1f, dur = 0.45f..0.65f, lines = ReactionLines.HOP, talkChance = 0.25f, antic = 0.25f),
    )
    val TAIL = listOf(
        ReactionEntry(Verb.TAIL_YELP, setOf(BodyPart.TAIL), 1.1f, amp = 0.6f..1f, dur = 0.4f..0.7f, baseState = MonkeyState.STARTLED, lines = Lines.TAIL, forceBias = 0.8f, antic = 0.1f),
        ReactionEntry(Verb.TAIL_SWAT, setOf(BodyPart.TAIL), 0.9f, amp = 0.6f..1f, dur = 0.4f..0.6f, lines = ReactionLines.TAIL_SWAT, minRepeats = 1),
        ReactionEntry(Verb.TAIL_CURL, setOf(BodyPart.TAIL), 0.8f, ReactionEntry.TAP_HOLD, amp = 0.6f..1f, dur = 0.9f..1.4f, lines = ReactionLines.TAIL_CURL, forceBias = -1f),
    )
    val EARS = listOf(
        ReactionEntry(Verb.EAR_FLICK, setOf(BodyPart.EAR), 1.2f, amp = 0.6f..1f, dur = 0.35f..0.55f, lines = ReactionLines.EAR, talkChance = 0.3f, antic = 0f),
        ReactionEntry(Verb.EAR_WIGGLE, setOf(BodyPart.EAR), 0.9f, ReactionEntry.TAP_HOLD, amp = 0.6f..1f, dur = 0.7f..1.1f, lines = ReactionLines.EAR, talkChance = 0.3f, forceBias = -0.6f),
    )
    val TRUNK = listOf(
        ReactionEntry(Verb.TRUNK_BOOP, setOf(BodyPart.TRUNK), 1.3f, amp = 0.6f..1f, dur = 0.5f..0.8f, lines = ReactionLines.TRUNK, talkChance = 0.5f),
    )

    /** Rocket Girl's own entries (referenced from his CharacterProfile). */
    val ROCKET_GIRL = listOf(
        ReactionEntry(Verb.GIGGLE, setOf(BodyPart.HEAD), 1.3f, amp = 0.7f..1f, dur = 0.9f..1.4f, baseState = MonkeyState.GIGGLE, lines = ReactionLines.HEAD, talkChance = 0.55f),
        ReactionEntry(Verb.ROCKET_SPUTTER, setOf(BodyPart.PROP, BodyPart.FOOT), 1.6f, amp = 0.7f..1f, dur = 0.9f..1.3f, lines = ReactionLines.ROCKET_TAP, talkChance = 0.55f, antic = 0.05f),
        ReactionEntry(Verb.SPIN_LOOK, setOf(BodyPart.PROP), 0.6f, amp = 0.6f..1f, dur = 0.4f..0.6f, lines = ReactionLines.SPIN, talkChance = 0.3f),
        ReactionEntry(Verb.LOOP, ANY, 2f, ReactionEntry.FLICK, amp = 0.8f..1f, dur = 0.85f..1.1f, lines = ReactionLines.LOOP, talkChance = 0.5f, antic = 0.1f),
        ReactionEntry(Verb.DIZZY, ANY, 0.6f, ReactionEntry.FLICK, amp = 0.7f..1f, dur = 0.9f..1.2f, lines = ReactionLines.DIZZY, talkChance = 0.4f),
        ReactionEntry(Verb.SPARKLER, ANY, 2.2f, ReactionEntry.HOLD, amp = 0.8f..1f, dur = 2.6f..3.4f, lines = ReactionLines.SPARKLER, talkChance = 0.6f, antic = 0.04f),
        ReactionEntry(Verb.BOOST, setOf(BodyPart.PROP, BodyPart.HEAD, BodyPart.BELLY), 0.06f, amp = 1f..1f, dur = 0.5f..0.5f, lines = ReactionLines.BOOST, talkChance = 1f),
    )

    /** Everything one character can do (no allocation after the first call per profile; cache the result). */
    fun build(features: BodyFeatures, extra: List<ReactionEntry>, includeCommon: Boolean = true): List<ReactionEntry> {
        val out = ArrayList<ReactionEntry>()
        out += LEGACY
        if (includeCommon) out += COMMON
        if (features.tail) out += TAIL
        if (features.ears) out += EARS
        if (features.trunk) out += TRUNK
        out += extra
        return out
    }
}
