package com.combo.monkeycompanion.heart

/**
 * Pure-Kotlin store for Next-Day Check-In Promises ([CareCheckIn]) and recent user mood.
 * Persists as a compact TSV/JSON-safe blob in SharedPreferences via [MindHub].
 */
class CareCheckInStore(private val cap: Int = 20) {
    var currentMood: UserMood = UserMood.NEUTRAL
    var moodUpdatedAt: Long = 0L
    private val items = ArrayList<CareCheckIn>()

    val all: List<CareCheckIn> get() = items.toList()
    val pending: List<CareCheckIn> get() = items.filter { !it.done }

    fun recordMessage(text: String, now: Long): Pair<UserMood, CareCheckIn?> {
        val detected = HeartEngine.detectMoodFromText(text)
        if (detected != UserMood.NEUTRAL) {
            currentMood = detected
            moodUpdatedAt = now
        } else if (moodUpdatedAt > 0L && now - moodUpdatedAt > 4 * 3600_000L) {
            currentMood = UserMood.NEUTRAL
        }
        val checkIn = HeartEngine.extractCheckIn(text, now)
        if (checkIn != null) addOrReplace(checkIn)
        return currentMood to checkIn
    }

    fun recordRhythm(minuteOfDay: Int, appSwitchesIn5Min: Int, continuousNightMins: Int, now: Long): UserMood? {
        val m = HeartEngine.detectMoodFromRhythm(minuteOfDay, appSwitchesIn5Min, continuousNightMins) ?: return null
        currentMood = m
        moodUpdatedAt = now
        return m
    }

    fun addOrReplace(c: CareCheckIn) {
        items.removeAll { !it.done && it.kind == c.kind }
        items.add(0, c)
        while (items.size > cap) items.removeAt(items.lastIndex)
    }

    /**
     * Returns a pending check-in that is ready to ask about now:
     *  - either `now >= dueAfter` (~12+ hours after the user mentioned it), OR
     *  - at least 6 hours have passed and it is now morning (`05:30..11:59`).
     */
    fun popDueCheckIn(now: Long, minuteOfDay: Int): CareCheckIn? {
        val isMorning = minuteOfDay in (5 * 60 + 30)..(11 * 60 + 59)
        val c = items.firstOrNull { item ->
            !item.done && (now >= item.dueAfter || (isMorning && now - item.createdAt >= 6 * 3600_000L)) &&
                now - item.createdAt <= 48 * 3600_000L
        } ?: return null
        val idx = items.indexOf(c)
        if (idx >= 0) items[idx] = c.copy(done = true)
        return items.getOrNull(idx) ?: c.copy(done = true)
    }

    fun clear() {
        currentMood = UserMood.NEUTRAL
        moodUpdatedAt = 0L
        items.clear()
    }

    fun encode(): String = buildString {
        appendLine("mood=${currentMood.name}@$moodUpdatedAt")
        for (c in items) {
            val cleanQuote = c.userQuote.replace('\t', ' ').replace('\n', ' ')
            val cleanTopic = c.topic.replace('\t', ' ').replace('\n', ' ')
            appendLine(listOf(c.id, c.kind, cleanTopic, c.createdAt.toString(), c.dueAfter.toString(), if (c.done) "1" else "0", cleanQuote).joinToString("\t"))
        }
    }

    fun decode(raw: String?) {
        items.clear()
        if (raw.isNullOrBlank()) return
        for (line in raw.lineSequence()) {
            if (line.isBlank()) continue
            if (line.startsWith("mood=")) {
                val p = line.removePrefix("mood=").split('@')
                currentMood = runCatching { UserMood.valueOf(p[0]) }.getOrDefault(UserMood.NEUTRAL)
                moodUpdatedAt = p.getOrNull(1)?.toLongOrNull() ?: 0L
            } else {
                val p = line.split('\t')
                if (p.size >= 7) {
                    runCatching {
                        items += CareCheckIn(
                            id = p[0],
                            kind = p[1],
                            topic = p[2],
                            createdAt = p[3].toLong(),
                            dueAfter = p[4].toLong(),
                            done = p[5] == "1",
                            userQuote = p[6]
                        )
                    }
                }
            }
        }
    }
}
