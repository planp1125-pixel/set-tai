package com.combo.monkeycompanion.heart

import java.util.Locale
import kotlin.random.Random

/**
 * v8.0 Heart & Care Mode:
 *  - Detects the human's emotional tone from chat/voice messages (English + Tamil/Tanglish keywords & patterns)
 *    and from phone rhythms (e.g. 1–4 AM restless app-switching or late-night scrolling).
 *  - Extracts Next-Day Check-In Promises (interviews, exams, doctor visits, deadlines, rough days) so Settai
 *    remembers and checks in warmly the next morning.
 *  - Chooses physical comfort animations on the glass (SIGN_CARD, GLASS_KNOCK_HUG, OFFER_COFFEE).
 *  - Generates a compact `heartPromptNote` with a 1-line empathetic response example for small on-device models (Gemma 2B).
 */
enum class UserMood(val label: String, val needsComfort: Boolean, val empatheticExample: String) {
    NEUTRAL("calm / neutral", false, "Hey machan! What are we up to today? 🍌"),
    STRESSED("stressed / overwhelmed", true, "Ayyo machan, take a slow breath da—one step at a time, naan irukken (I'm right here with you) ❤️"),
    SAD("sad / feeling low", true, "Come here da 🫂 Some days are just heavy, and that's okay. I'm sitting right here on your glass with you."),
    ANXIOUS("anxious / big event ahead", true, "Don't worry da, you've prepared for this! Take a sip of water—you've totally got this 💪✨"),
    TIRED("tired / exhausted", true, "Romba vela paathuta (you've worked so hard today), machan. Rest your eyes for a bit, I'll guard the phone 😴☕"),
    HAPPY("happy / celebrating", false, "Semma machan!! 🎉 Let's gooo! I'm doing my happy dance for you right now 💃🍌");
}

enum class ComfortKind(val label: String, val secs: Float) {
    SIGN_CARD("hand-drawn cheer sign", 6.5f),
    GLASS_KNOCK_HUG("warm screen hug (*tap-tap*)", 6.0f),
    OFFER_COFFEE("steaming brass filter coffee", 6.5f);
}

data class CareCheckIn(
    val id: String,
    val kind: String,
    val topic: String,
    val createdAt: Long,
    val dueAfter: Long,
    val userQuote: String,
    var done: Boolean = false,
)

object HeartEngine {
    private val STRESSED_KEYS = listOf(
        "stressed", "overwhelmed", "burnout", "burned out", "too much work", "so much work", "under pressure",
        "headache", "frantic", "losing my mind", "can't cope", "cant cope", "mudiyala", "romba vela",
        "thalai vali", "thalaivali", "tension", "stressed out"
    )
    private val SAD_KEYS = listOf(
        "sad", "feeling low", "feeling down", "depressed", "lonely", "alone", "crying", "cried",
        "heartbroken", "hurt", "nobody cares", "rough day", "bad day", "worst day", "terrible day",
        "sogam", "sogama", "kastama iruku", "kashtama", "azhugai", "miss my", "missing my"
    )
    private val ANXIOUS_KEYS = listOf(
        "anxious", "anxiety", "nervous", "scared", "worried", "panicking", "panic", "terrified",
        "interview", "exam", "midterm", "finals", "viva", "deadline", "presentation", "pitch",
        "doctor", "hospital", "surgery", "results tomorrow", "bayam", "bayama", "பயமா"
    )
    private val TIRED_KEYS = listOf(
        "tired", "exhausted", "drained", "sleepy", "no energy", "so beat", "worn out", "long day",
        "barely awake", "insomnia", "can't sleep", "cant sleep", "thookam", "sornthu", "sorvu"
    )
    private val HAPPY_KEYS = listOf(
        "happy", "excited", "celebrating", "celebrate", "i won", "we won", "passed", "got the job",
        "got promoted", "promotion", "aced it", "nailed it", "santhosham", "semma", "jolly"
    )

    private val CHECKIN_PATTERNS: List<Pair<String, List<String>>> = listOf(
        "interview" to listOf("interview", "job call", "hr round", "coding round", "technical round"),
        "exam" to listOf("exam", "test tomorrow", "viva", "midterm", "finals", "quiz tomorrow"),
        "presentation" to listOf("presentation", "client demo", "big pitch", "stage talk", "meeting tomorrow"),
        "doctor" to listOf("doctor", "hospital", "checkup", "dentist", "clinic"),
        "deadline" to listOf("deadline", "submission", "launch tomorrow", "release tomorrow"),
        "rough_day" to listOf("rough day", "bad day", "worst day", "hard day", "feeling down", "feeling low", "kastama iruku", "sogama"),
    )

    private val SIGN_MESSAGES = mapOf(
        UserMood.STRESSED to listOf("1 step at a time ❤️", "Breathe da, you got this", "Drink water machan 💧", "Naan irukken da 💛"),
        UserMood.SAD to listOf("Sending a big hug 🫂", "I'm right here with you ❤️", "Proud of you always 💛", "Better days ahead 🌅"),
        UserMood.ANXIOUS to listOf("You got this, machan! 💪", "All the best da! 🌟", "Deep breath — you're ready", "Fingers crossed for you 🤞"),
        UserMood.TIRED to listOf("Rest your eyes da 😴", "Hot filter coffee for you ☕", "You worked hard today ❤️", "Take a 5m break 🌿"),
        UserMood.HAPPY to listOf("Proud of you machan! 🏆", "Semma!! Party time 🎉", "Champion energy! ⭐"),
        UserMood.NEUTRAL to listOf("You got this, machan ❤️", "1 step at a time", "Drink water da 💧", "Proud of you! 🌟"),
    )

    /** Detects emotional tone from a user's message (English + Tamil/Tanglish). */
    fun detectMoodFromText(text: String): UserMood {
        val t = text.lowercase(Locale.ROOT)
        if (t.isBlank()) return UserMood.NEUTRAL
        var stressed = 0; var sad = 0; var anxious = 0; var tired = 0; var happy = 0
        for (k in STRESSED_KEYS) if (k in t) stressed += 2
        for (k in SAD_KEYS) if (k in t) sad += 2
        for (k in ANXIOUS_KEYS) if (k in t) anxious += 2
        for (k in TIRED_KEYS) if (k in t) tired += 2
        for (k in HAPPY_KEYS) if (k in t) happy += 2
        if ("😢" in t || "😭" in t || "💔" in t || "🥺" in t) sad += 2
        if ("😰" in t || "😨" in t || "🤞" in t) anxious += 1
        if ("😫" in t || "😩" in t || "🤯" in t) stressed += 2
        if ("😴" in t || "🥱" in t) tired += 2
        if ("🎉" in t || "🥳" in t || "🏆" in t) happy += 2

        val best = listOf(
            UserMood.SAD to sad,
            UserMood.STRESSED to stressed,
            UserMood.ANXIOUS to anxious,
            UserMood.TIRED to tired,
            UserMood.HAPPY to happy,
        ).maxByOrNull { it.second } ?: return UserMood.NEUTRAL
        return if (best.second > 0) best.first else UserMood.NEUTRAL
    }

    /**
     * Detects emotional / restlessness state from phone rhythms:
     * e.g., 1–4 AM (`minuteOfDay in 60..260`) with restless app-switching (`>= 4` switches in 5 min) or
     * late-night scrolling (`>= 25` mins).
     */
    fun detectMoodFromRhythm(minuteOfDay: Int, appSwitchesIn5Min: Int, continuousNightMins: Int): UserMood? {
        val lateWeeHours = minuteOfDay in 60..260
        if (!lateWeeHours) return null
        return when {
            appSwitchesIn5Min >= 4 -> UserMood.STRESSED
            continuousNightMins >= 25 -> UserMood.TIRED
            else -> null
        }
    }

    /** Chooses the best physical comfort animation for [mood] or explicit user request. */
    fun pickComfort(mood: UserMood, text: String = "", rnd: Random = Random): ComfortKind {
        val t = text.lowercase(Locale.ROOT)
        if ("hug" in t || "knock" in t || "sad" in t || "lonely" in t || "crying" in t) return ComfortKind.GLASS_KNOCK_HUG
        if ("coffee" in t || "tired" in t || "sleepy" in t || "exhausted" in t) return ComfortKind.OFFER_COFFEE
        if ("sign" in t || "card" in t || "motivate" in t || "cheer" in t) return ComfortKind.SIGN_CARD
        return when (mood) {
            UserMood.SAD -> ComfortKind.GLASS_KNOCK_HUG
            UserMood.TIRED -> ComfortKind.OFFER_COFFEE
            UserMood.STRESSED, UserMood.ANXIOUS -> if (rnd.nextFloat() < 0.65f) ComfortKind.SIGN_CARD else ComfortKind.GLASS_KNOCK_HUG
            else -> ComfortKind.SIGN_CARD
        }
    }

    fun signTextFor(mood: UserMood, rnd: Random = Random): String {
        val pool = SIGN_MESSAGES[mood] ?: SIGN_MESSAGES[UserMood.NEUTRAL]!!
        return pool[rnd.nextInt(pool.size)]
    }

    fun comfortLine(characterId: String, kind: ComfortKind, mood: UserMood, signText: String): String = when (kind) {
        ComfortKind.SIGN_CARD -> when (characterId) {
            "superboy" -> "Holding up a hero banner for you: “$signText” 🦸🪧"
            "armour" -> "Displaying priority support placard: “$signText” 🛡️🪧"
            "mask" -> "Ta-da! Read the sign, my friend: “$signText” 🎩🪧"
            "billa" -> "*purrs softly and holds up a cardboard sign*: “$signText” 🐱🪧"
            "chittu" -> "*stands on tiptoe with a tiny sign*: “$signText” 🐭🪧"
            else -> "Made a little sign just for you, machan: “$signText” 🐒🪧"
        }
        ComfortKind.GLASS_KNOCK_HUG -> when (characterId) {
            "superboy" -> "*tap-tap on the glass* Sending you a super-hug right through the screen 🫂💛"
            "armour" -> "*gentle tap on the glass* Suit heaters on warm — screen hug engaged 🫂🛡️"
            "billa" -> "*headbutts the inside of the glass and purrs* Screen hug for you 🐱🫂"
            "chittu" -> "*presses tiny paws on the glass* Big mouse hug for you! 🐭🫂"
            else -> "*tap-tap* Pressing my paws on the glass — big screen hug for you, machan 🫂❤️"
        }
        ComfortKind.OFFER_COFFEE -> when (characterId) {
            "armour" -> "Fresh brass davara of filter coffee, brewed to 78°C. Sit and sip slowly ☕✨"
            else -> "Brought you a hot, frothy filter coffee in a brass davara ☕💛 Sit with me for a minute, machan."
        }
    }

    /**
     * Extracts a Next-Day Check-In Promise if the user mentions an upcoming interview, exam, presentation,
     * doctor visit, deadline, or a rough day.
     */
    fun extractCheckIn(text: String, now: Long): CareCheckIn? {
        val t = text.lowercase(Locale.ROOT).trim()
        if (t.length < 4) return null
        val match = CHECKIN_PATTERNS.firstOrNull { (_, keys) -> keys.any { it in t } } ?: return null
        val kind = match.first
        val topic = when (kind) {
            "interview" -> "your interview"
            "exam" -> "your exam"
            "presentation" -> "your presentation"
            "doctor" -> "your doctor visit"
            "deadline" -> "your deadline"
            else -> "yesterday's rough day"
        }
        val dueAfter = now + 12 * 3600_000L
        return CareCheckIn(
            id = "${kind}_${now / 60_000L}",
            kind = kind,
            topic = topic,
            createdAt = now,
            dueAfter = dueAfter,
            userQuote = text.take(90),
            done = false
        )
    }

    /** Warm in-character follow-up line when a scheduled CareCheckIn comes due the next morning. */
    fun checkInLine(c: CareCheckIn, characterId: String): String {
        val body = when (c.kind) {
            "interview" -> "Machan, yesterday you had that big interview—epdi pochu (how did it go)? I kept my fingers crossed all night! 🤞💛"
            "exam" -> "Hey machan! Yesterday you mentioned ${c.topic}—epdi eluthuna (how did it go)? Proud of you either way! 📚✨"
            "presentation" -> "Good morning machan! How did ${c.topic} go? Hope you knocked it out of the park! 🌟"
            "doctor" -> "Hey da, checking in after ${c.topic}—how are you feeling today? Remember to take rest and drink water 💧❤️"
            "deadline" -> "Machan! You were working through ${c.topic}—did you get some sleep? Hope the pressure eased up today ☕💛"
            else -> "Good morning machan 🌅 Yesterday was a heavy day for you—how is your heart feeling today? I'm right here with you 🫂💛"
        }
        return when (characterId) {
            "superboy" -> "Hero check-in! $body"
            "armour" -> "Morning wellness check: $body"
            "billa" -> "*gentle meow* $body"
            "chittu" -> "*squeak!* $body"
            else -> body
        }
    }

    /**
     * Compact prompt note injected into `ChatHub` so even the small on-device Gemma 2B model knows
     * the user's emotional tone, active check-in promises, and has a concrete 1-line warm Tamil Nadu friend example.
     */
    fun promptNote(mood: UserMood, pendingCheckIns: List<CareCheckIn>): String {
        val checkStr = if (pendingCheckIns.isEmpty()) ""
        else " Upcoming/recent user life events to care about: " + pendingCheckIns.take(2).joinToString("; ") { "${it.topic} (“${it.userQuote.take(45)}”)" } + "."
        return if (mood == UserMood.NEUTRAL && pendingCheckIns.isEmpty()) ""
        else "Heart & Care mode: your human currently seems ${mood.label}.$checkStr " +
            "Respond like a warm, caring Tamil Nadu best friend (short, gentle, never robotic or clinical). " +
            "Example warm tone: \"${mood.empatheticExample}\""
    }
}
