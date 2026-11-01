package com.combo.monkeycompanion.v5

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * v10.19 the Bond: one small, pure place for "how close are we today". It sits on top of [Friendship]
 * (XP, levels, unlocks and the daily streak stay there) and adds:
 *  - warmth 0..100: today's feeling toward you. It cools a little for each day you stay away and warms up with
 *    pets, chats and games. The friendship LEVEL never goes down; only warmth does.
 *  - the daily check-in: the first time you show up each day decides hello / sulk / welcome back.
 *    One free missed day per week keeps the streak (grace). After a long break (5+ days) he's just happy you're back.
 *  - the sulk: arms folded until you pat him (or it fades by itself after a few hours).
 *  - goodnight: once per night after 10 PM.
 * No Random anywhere: lines are picked by date, so GoldenTraceTest's random stream is never touched.
 */
object Bond {
    const val START_WARMTH = 60
    const val MIN_WARMTH = 10
    const val DECAY_PER_DAY = 15
    const val CHECKIN_WARMTH = 10
    const val LONG_ABSENCE_DAYS = 5
    const val SULK_FADES_MS = 3 * 3_600_000L
    const val NIGHT_FROM = 22

    enum class Moment { NONE, FIRST, HELLO, GRACE, SULK, WELCOME_BACK }

    data class State(
        val warmth: Int = START_WARMTH,
        /** yyyy-MM-dd of the last day you showed up. */
        val lastDay: String = "",
        /** Monday (yyyy-MM-dd) of the week the free missed-day pass was used. */
        val graceWeek: String = "",
        val sulking: Boolean = false,
        val sulkSince: Long = 0L,
        /** The night key (yyyy-MM-dd of the evening) goodnight was last said. */
        val nightDay: String = "",
    ) {
        fun encode() = "$warmth|$lastDay|$graceWeek|${if (sulking) 1 else 0}|$sulkSince|$nightDay"
        companion object {
            fun decode(s: String?): State {
                if (s.isNullOrBlank()) return State()
                val p = s.split('|')
                return State(
                    p.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 100) ?: START_WARMTH, p.getOrNull(1).orEmpty(), p.getOrNull(2).orEmpty(),
                    p.getOrNull(3) == "1", p.getOrNull(4)?.toLongOrNull() ?: 0L, p.getOrNull(5).orEmpty(),
                )
            }
        }
    }

    /** What the first visit of a day means. [saveStreak] = the friendship streak should be kept (grace). */
    data class CheckIn(val moment: Moment, val state: State, val gapDays: Int, val saveStreak: Boolean)

    fun weekKey(d: LocalDate): String = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()

    /**
     * The first visit of [today]. [streak] = the friendship streak before today. Returns null if today was
     * already checked in.
     */
    fun checkIn(s: State, today: LocalDate, streak: Int, nowMs: Long): CheckIn? {
        val t = today.toString()
        if (s.lastDay == t) return null
        val last = runCatching { LocalDate.parse(s.lastDay) }.getOrNull()
            ?: return CheckIn(Moment.FIRST, s.copy(lastDay = t, warmth = maxOf(s.warmth, START_WARMTH)), 0, false)
        val gap = ChronoUnit.DAYS.between(last, today).toInt()
        if (gap <= 0) return CheckIn(Moment.HELLO, s.copy(lastDay = t), 0, false)   // the clock went backwards
        val warmth = (s.warmth - DECAY_PER_DAY * (gap - 1)).coerceAtLeast(MIN_WARMTH).let { (it + CHECKIN_WARMTH).coerceAtMost(100) }
        val base = s.copy(lastDay = t, warmth = warmth, sulking = false)
        val week = weekKey(today)
        return when {
            gap == 1 -> CheckIn(Moment.HELLO, base, gap, false)
            gap >= LONG_ABSENCE_DAYS -> CheckIn(Moment.WELCOME_BACK, base.copy(warmth = maxOf(warmth, 40)), gap, false)
            gap == 2 && streak >= 2 && s.graceWeek != week -> CheckIn(Moment.GRACE, base.copy(graceWeek = week), gap, true)
            streak >= 2 -> CheckIn(Moment.SULK, base.copy(sulking = true, sulkSince = nowMs), gap, false)
            else -> CheckIn(Moment.HELLO, base, gap, false)
        }
    }

    /** Warmth from something you did together (pets, chats, games). */
    fun warmUp(s: State, src: Friendship.Source): State {
        val add = when (src) {
            Friendship.Source.PET -> 2
            Friendship.Source.CHAT -> 4
            Friendship.Source.GAME, Friendship.Source.GAME_WIN, Friendship.Source.PAIR -> 5
            Friendship.Source.CLIP -> 3
            Friendship.Source.STREAK_DAY -> 0
        }
        return s.copy(warmth = (s.warmth + add).coerceAtMost(100))
    }

    /** A pat ends a sulk. Returns the new state and whether a sulk just ended. */
    fun pat(s: State): Pair<State, Boolean> = if (s.sulking) s.copy(sulking = false) to true else s to false

    /** Sulks fade by themselves after a few hours (nobody should be punished all day). */
    fun fade(s: State, nowMs: Long): State = if (s.sulking && nowMs - s.sulkSince > SULK_FADES_MS) s.copy(sulking = false) else s

    /** The night this moment belongs to (yyyy-MM-dd of the evening), or null if it isn't goodnight time. */
    fun nightKey(now: LocalDateTime): String? = when {
        now.hour >= NIGHT_FROM -> now.toLocalDate().toString()
        now.hour < 2 -> now.toLocalDate().minusDays(1).toString()
        else -> null
    }

    fun goodnightDue(s: State, now: LocalDateTime): Boolean = nightKey(now)?.let { it != s.nightDay } ?: false

    // ---- the heart and the numbers people see ----

    /** Friendship points 0..100 (level 10 = 100; each level is about 11 points). */
    fun points(xp: Int): Int {
        val l = Friendship.level(xp)
        if (l >= 10) return 100
        return (((l - 1) + Friendship.progress(xp)) / 9f * 100f).toInt().coerceIn(0, 99)
    }

    fun warmthWord(w: Int) = when { w >= 85 -> "glowing"; w >= 65 -> "warm"; w >= 40 -> "okay"; w >= 25 -> "missing you"; else -> "lonely" }

    /** One short line for the AI (kept under [max] chars, separate from the Body Sense note). */
    fun promptLine(xp: Int, warmth: Int, streak: Int, sulking: Boolean, facts: List<String>, max: Int = 220): String {
        val l = Friendship.level(xp)
        val head = buildString {
            append("YOUR BOND: friendship level ").append(l).append(" (").append(Friendship.title(l)).append("), feeling ").append(warmthWord(warmth))
            if (streak >= 2) append(", ").append(streak).append("-day streak together")
            if (sulking) append(", you're sulking because they skipped days (a pat makes it all better)")
            append('.')
        }
        var out = head
        for (f in facts) {
            val add = (if (out == head) " You know about them: " else "; ") + f.trim().trimEnd('.')
            if (out.length + add.length + 1 > max) break
            out += add
        }
        return if (out == head) out else "$out."
    }

    // ---- what he says (Brain off). Picked by date, never by Random. ----

    private fun pick(list: List<String>, seed: Int) = list[Math.floorMod(seed, list.size)]

    fun hello(hour: Int, name: String?, seed: Int): String {
        val n = name?.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""
        return when (hour) {
            in 5..11 -> pick(listOf("Good morning$n! ☀️ New day, new mischief 😈", "Morning$n! Did you sleep well? 🌅", "Rise and shine$n! I was waiting for you 👀", "Kaalai vanakkam$n! ☕ Coffee first, then mischief"), seed)
            in 12..16 -> pick(listOf("Hey$n! There you are 👋", "Finally$n! I was getting bored 😅", "Good afternoon$n! What are we up to? 🌤️"), seed)
            in 17..21 -> pick(listOf("Good evening$n! 🌇 How was your day?", "Evening$n! Tell me something nice that happened 😊"), seed)
            else -> pick(listOf("Hey night owl$n 🦉", "Still awake$n? Me too 👀"), seed)
        }
    }

    fun graceLine(streak: Int) = "Phew 😅 You missed a day, but I used my free pass to save our $streak-day streak!"

    fun sulkLine(seed: Int) = pick(listOf("Hmph 😤 You forgot me yesterday…", "*turns away* Not talking to you… unless you pat me 😒", "I waited all day yesterday 😔 A pat might fix it…"), seed)

    fun makeUpLine(seed: Int) = pick(listOf("Okay okay… I forgive you 🥰", "Fine! Friends again 💛", "Hmph… that pat was nice. Forgiven 😌"), seed)

    fun welcomeBackLine(days: Int, seed: Int) = pick(listOf("You're back!! I missed you so much 🥹", "$days days! Where were you?! I counted every one 😭💛", "Is it really you?! Welcome back 🎉"), seed)

    fun goodnightLine(name: String?, seed: Int): String {
        val n = name?.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""
        return pick(listOf("Yaaawn… goodnight$n 🌙 See you tomorrow", "Sleepy time 😴 Goodnight$n, don't scroll too long!", "Iravu vanakkam$n 🌙 Sweet dreams"), seed)
    }

    // ---- what we ask the AI (Brain on) ----

    fun aiRequest(m: Moment, hour: Int, gapDays: Int, streak: Int): String {
        val part = when (hour) { in 5..11 -> "morning"; in 12..16 -> "afternoon"; in 17..21 -> "evening"; else -> "late night" }
        val what = when (m) {
            Moment.HELLO, Moment.FIRST -> "Your human just opened the phone for the first time today ($part). Greet them in a way that fits the time and how you feel"
            Moment.GRACE -> "Your human skipped a day, but you used your one free pass this week to save your $streak-day streak. Tell them, relieved and a bit cheeky"
            Moment.SULK -> "Your human skipped ${gapDays - 1} day(s) and broke your streak. Sulk a little, playfully (arms folded), and hint that a pat would fix it. Never guilt-trip"
            Moment.WELCOME_BACK -> "Your human is back after $gapDays days away. Be overjoyed, no sulking at all"
            Moment.NONE -> "Say hello"
        }
        return "DAILY MOMENT (nobody asked you anything): $what. ONE short line (max 14 words). If it fits naturally, mention one thing you know about them (YOUR BOND)."
    }

    const val AI_MAKE_UP = "DAILY MOMENT: your human just patted you and that ends your sulk. ONE short, happy line forgiving them (max 12 words)."
    const val AI_GOODNIGHT = "DAILY MOMENT: it's late at night. Yawn, wish your human goodnight in ONE short line (max 14 words), then you curl up to sleep."
}
