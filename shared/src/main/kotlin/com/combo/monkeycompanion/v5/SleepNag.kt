package com.combo.monkeycompanion.v5

/**
 * v5 late-night sleep nag. Default (no setting beyond on/off): after 23:30, once the screen has been on for 10
 * minutes straight, the character yawns, lies down and nags, gently escalating every 12 minutes, until 05:00.
 * Snooze = 20 minutes. Respects DND (never nags in DND). Pure.
 */
object SleepNag {
    const val START_MIN = 23 * 60 + 30
    const val END_MIN = 5 * 60
    const val SCREEN_ON_MS = 10 * 60_000L
    const val STEP_MS = 12 * 60_000L
    const val SNOOZE_MS = 20 * 60_000L

    fun night(minuteOfDay: Int) = minuteOfDay >= START_MIN || minuteOfDay < END_MIN

    /**
     * Level to nag at now (1..4), or 0 = not now. [screenOnSince] = when the screen came on (ms), [lastNagAt] /
     * [lastLevel] = the previous nag tonight, [snoozedUntil].
     */
    fun level(nowMs: Long, minuteOfDay: Int, screenOnSince: Long, lastNagAt: Long, lastLevel: Int, snoozedUntil: Long, dnd: Boolean): Int {
        if (dnd || !night(minuteOfDay)) return 0
        if (nowMs - screenOnSince < SCREEN_ON_MS) return 0
        if (nowMs < snoozedUntil) return 0
        if (lastNagAt > 0 && nowMs - lastNagAt < STEP_MS) return 0
        // A nag long ago (a new night) starts over.
        val fresh = lastNagAt <= 0 || nowMs - lastNagAt > 4 * 3600_000L
        return if (fresh) 1 else (lastLevel + 1).coerceAtMost(4)
    }

    /** Next check delay (ms): only while it can matter, so nothing ticks during the day. */
    fun nextCheckMs(minuteOfDay: Int, screenOnFor: Long): Long = when {
        night(minuteOfDay) -> maxOf(60_000L, SCREEN_ON_MS - screenOnFor).coerceAtMost(STEP_MS)
        else -> ((START_MIN - minuteOfDay + 24 * 60) % (24 * 60)) * 60_000L
    }

    fun line(level: Int, characterId: String): String {
        val l = when (level) {
            1 -> listOf("*yaaawn* It's late machan… 😴", "Hmm, it's past 11:30. Sleepy-time?")
            2 -> listOf("I'm lying down now. Phone down also? 🛏️", "Even the reels are sleeping. You?")
            3 -> listOf("Tomorrow-you will be SO angry at tonight-you 😤😴", "Amma would scold us both. Sleep pannu!")
            else -> listOf("I'm calling the sleep police 🚨😴 Last warning!", "Okay I'm switching off the moon. Goodnight! 🌙")
        }
        val tag = when (characterId) { "superboy" -> " (Hero rule #1: sleep)"; "armour" -> " (Suit in night mode)"; "mask" -> " (Mask going to sleep too)"; else -> "" }
        return l.random() + tag
    }
}
