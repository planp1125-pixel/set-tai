package com.combo.monkeycompanion.ai.react

/**
 * v10.18 "Feel the touch" reaction engine, step 1 (SENSE): what exactly happened to the body.
 * Pure Kotlin, no Android, no Random (GoldenTraceTest: the frame-loop random stream is never touched).
 * See docs/REACTION_ENGINE.md for the whole flow.
 */
enum class ReactKind(val id: String, val words: String, val big: Boolean) {
    TICKLE("tickle", "tickled", false),
    PAT("pat", "patted on the head", false),
    POKE("poke", "poked", false),
    TAIL("tail", "grabbed by the tail", false),
    HOLD("hold", "held by your finger", false),
    PICKUP("pickup", "picked up and dragged around", false),
    THROW("throw", "thrown across the screen", true),
    BUMP("bump", "smacked into the screen edge", true),
    BAD("bad", "touched somewhere private (rude!)", true),
    WAKE("wake", "woken up by a touch", true),
    POWER("power", "double-tapped to use a superpower", false);

    companion object {
        fun byId(id: String): ReactKind? = values().firstOrNull { it.id.equals(id.trim(), ignoreCase = true) }

        /**
         * Turns the overlay's touch facts into a kind. [zone] is the rig zone name (HEAD, NECK, BELLY, PRIVATE, TAIL,
         * HAND, FOOT, NONE), [force] 0..1, [hold] = a long press, [asleep] = he was sleeping when touched.
         */
        fun classify(zone: String, force: Float, hold: Boolean, asleep: Boolean): ReactKind = when {
            zone.equals("PRIVATE", true) -> BAD
            asleep -> WAKE
            hold -> HOLD
            zone.equals("TAIL", true) -> TAIL
            zone.equals("HEAD", true) && force < 0.6f -> PAT
            (zone.equals("BELLY", true) || zone.equals("NECK", true)) && force < 0.6f -> TICKLE
            else -> POKE
        }
    }
}

enum class Strength(val words: String) {
    GENTLE("gently"), FIRM("firmly"), ROUGH("roughly");
    companion object { fun of(force: Float) = when { force < 0.45f -> GENTLE; force < 0.8f -> FIRM; else -> ROUGH } }
}

/**
 * One touch, fully described. [where] = body part in plain words ("belly", "head"…; blank when not a spot touch),
 * [streak] = how many times in a row (1 = first), [doing] = what he was doing just before ("sleeping", "dancing"…),
 * [extra] = optional detail ("flung up-left at high speed").
 */
data class TouchFeel(
    val kind: ReactKind,
    val where: String = "",
    val strength: Strength = Strength.GENTLE,
    val streak: Int = 1,
    val doing: String = "",
    val extra: String = "",
) {
    /** "Your belly was tickled gently, 3 times in a row, while you were sleeping (flung up-left)." */
    fun describe(): String = buildString {
        append(if (where.isNotBlank() && kind in SPOT) "Your $where was just " else "You were just ")
        append(kind.words)
        if (kind in SPOT || kind == ReactKind.HOLD) append(' ').append(strength.words)
        if (streak >= 2) append(", ").append(streak).append(" times in a row")
        if (doing.isNotBlank()) append(", while you were ").append(doing)
        if (extra.isNotBlank()) append(" (").append(extra).append(')')
        append('.')
    }

    companion object {
        private val SPOT = setOf(ReactKind.TICKLE, ReactKind.PAT, ReactKind.POKE, ReactKind.TAIL, ReactKind.BAD, ReactKind.WAKE)
        /** Rig zone -> plain words for the AI. */
        fun partWords(zone: String): String = when (zone.uppercase()) {
            "HEAD" -> "head"; "NECK" -> "neck"; "BELLY" -> "belly"; "TAIL" -> "tail"; "HAND" -> "hand"; "FOOT" -> "foot"
            "PRIVATE" -> "private area"; else -> ""
        }
    }
}

/**
 * Step 2 (FEEL), the part that decides which prepared lines still fit: a coarse mood key. Lines the AI wrote for
 * "night | grumpy | tired | new friend" are not used at "morning | happy | full | best friends".
 */
data class MoodKey(val time: String, val mood: String, val energy: String, val bond: String) {
    override fun toString() = "$time|$mood|$energy|$bond"
    /** Plain words for the prompt. */
    fun words() = "time of day: $time; mood: $mood; energy: $energy; friendship: $bond"

    companion object {
        fun of(hour: Int, grumpy: Float, sleepy: Boolean, energy: Float, friendLevel: Int): MoodKey = MoodKey(
            time = when (hour) { in 5..11 -> "morning"; in 12..16 -> "afternoon"; in 17..21 -> "evening"; else -> "night" },
            mood = when { sleepy -> "sleepy"; grumpy > 0.5f -> "grumpy"; grumpy > 0.2f -> "a bit annoyed"; else -> "happy" },
            energy = when { energy < 0f -> "ok"; energy < 0.3f -> "tired"; energy < 0.7f -> "ok"; else -> "full of beans" },
            bond = when { friendLevel >= 7 -> "best friends"; friendLevel >= 4 -> "good friends"; friendLevel >= 2 -> "friends"; else -> "new friend" },
        )
        fun parse(s: String): MoodKey? = s.split('|').takeIf { it.size == 4 }?.let { MoodKey(it[0], it[1], it[2], it[3]) }
    }
}
