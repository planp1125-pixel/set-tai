package com.combo.monkeycompanion.ai

/**
 * v10.15 Body Sense: the ONE place that turns the character's live animated body into a short note for
 * every brain (on-device Gemma/Qwen and every online API), plus the safe action-tag gate for replies.
 * Pure Kotlin (no Android) so it is unit-tested; the overlay only feeds it events and a snapshot.
 */
object BodySense {
    const val WINDOW_MS = 120_000L
    const val MARKER = "BODY NOW:"
    const val MAX_CHARS = 420

    /** Bodily feelings; each fades out over [fadeMs] after the event that caused it. */
    enum class Feeling(val word: String, val fadeMs: Long) {
        DIZZY("a bit dizzy", 45_000), SORE("a little sore", 90_000), ZAPPED("tingly from the zap", 60_000),
        ENERGISED("energised", 120_000), WARM("warm and cosy", 90_000), PUFFED("out of breath", 40_000),
        TIRED("tired", 0), SLEEPY("sleepy", 0)
    }

    data class Event(val at: Long, val text: String, val feeling: Feeling? = null)

    data class Snapshot(
        val name: String, val position: String, val state: String, val power: String? = null,
        val battery: Int = -1, val charging: Boolean = false, val hour: Int = 12,
        val mood: String = "cheerful", val friendship: String = "", val asleep: Boolean = false,
    )

    private val log = ArrayDeque<Event>()

    @Synchronized fun record(text: String, feeling: Feeling? = null, now: Long = System.currentTimeMillis()) {
        log.addLast(Event(now, text, feeling))
        while (log.size > 12) log.removeFirst()
    }
    @Synchronized fun clear() = log.clear()
    @Synchronized fun recent(now: Long = System.currentTimeMillis()): List<Event> = log.filter { now - it.at in 0..WINDOW_MS }

    /** A fling's direction in plain words (screen y grows downward). */
    fun direction(vx: Float, vy: Float): String {
        val ax = kotlin.math.abs(vx); val ay = kotlin.math.abs(vy)
        val v = if (vy < 0) "up" else "down"; val h = if (vx < 0) "left" else "right"
        return when { ay > ax * 2 -> v; ax > ay * 2 -> h; else -> "$v-$h" }
    }

    /** Feelings right now: fading event feelings (strongest = newest) + ones from the body state. */
    fun feelings(s: Snapshot, events: List<Event>, now: Long): List<String> {
        val out = LinkedHashSet<String>()
        for (e in events.asReversed()) {
            val f = e.feeling ?: continue
            val age = now - e.at
            if (age < 0 || age > f.fadeMs) continue
            out.add(if (age > f.fadeMs / 2) "still slightly ${f.word.removePrefix("a ").removePrefix("bit ")}" else f.word)
        }
        if (s.asleep || s.hour in 0..5) out.add(Feeling.SLEEPY.word)
        if (s.battery in 0..19 && !s.charging) out.add(Feeling.TIRED.word + " (low battery)")
        else if (s.hour >= 23) out.add(Feeling.TIRED.word)
        if (s.charging && s.battery >= 0) out.add(Feeling.ENERGISED.word)
        return out.toList().take(3)
    }

    private fun agoText(ms: Long) = if (ms < 60_000) "${(ms / 1000).coerceAtLeast(1)}s ago" else "${ms / 60_000}m ago"

    fun timeOfDay(h: Int) = when (h) { in 5..11 -> "morning"; in 12..16 -> "afternoon"; in 17..20 -> "evening"; else -> "night" }

    /** The compact live block (one line, capped to [maxChars]). Newest events are kept first when capping. */
    fun build(s: Snapshot, events: List<Event> = recent(), now: Long = System.currentTimeMillis(), maxChars: Int = MAX_CHARS): String {
        val head = buildString {
            append(MARKER).append(" I am ").append(s.name).append(", ").append(s.position).append("; ").append(s.state)
            if (s.power != null) append(" (using ").append(s.power).append(")")
            val f = feelings(s, events, now)
            if (f.isNotEmpty()) append("; I feel ").append(f.joinToString(", "))
            append("; mood ").append(s.mood)
            if (s.friendship.isNotBlank()) append("; friendship ").append(s.friendship)
            if (s.battery >= 0) append("; battery ").append(s.battery).append('%').append(if (s.charging) " charging" else "")
            append("; ").append(timeOfDay(s.hour))
        }
        var evs = events.asReversed().take(4).map { "${it.text} ${agoText(now - it.at)}" }
        fun full() = if (evs.isEmpty()) "$head. Nothing else happened to my body lately." else "$head. Just now: ${evs.joinToString("; ")}."
        while (full().length > maxChars && evs.isNotEmpty()) evs = evs.dropLast(1)
        val r = full()
        return if (r.length <= maxChars) r else r.take(maxChars - 1) + "…"
    }

    /** Rules for every brain: speak from the real body, no inventions. */
    const val RULES = "Speak in first person from this real body experience (only what BODY NOW says happened); you may mention a feeling briefly and naturally, never list stats. Never invent events like rockets, fireworks or light shows that didn't happen. Optional: add ONE action tag like [jump] [wave] [dance] [sleep] [spin] [hide]."

    // ---- action tags: allow-list + rate limit ----

    /** Safe tags any character may be asked to do by the AI (FLY/powers are never AI-triggered here). */
    val SAFE = setOf(BodyAction.JUMP, BodyAction.WAVE, BodyAction.DANCE, BodyAction.SLEEP, BodyAction.SPIN, BodyAction.HIDE,
        BodyAction.GIGGLE, BodyAction.HAPPY, BodyAction.SAD, BodyAction.THINK, BodyAction.ANGRY, BodyAction.CLIMB)

    /** Per-character allow-list: SAFE actions this body can actually do (via its own mapAction). */
    fun allowList(c: com.combo.monkeycompanion.character.Character, w: com.combo.monkeycompanion.character.Wear): Set<BodyAction> =
        SAFE.filter { runCatching { c.mapAction(it, w) }.getOrNull() != null }.toSet()

    /** Keep only allowed actions (unknown already dropped by the parser), at most [max]. */
    fun gate(actions: List<BodyAction>, allowed: Set<BodyAction>, max: Int = 1): List<BodyAction> = actions.filter { it in allowed }.take(max)

    /** At most [perMinute] AI actions per minute and [minGapMs] apart. */
    class Limiter(private val minGapMs: Long = 4_000, private val perMinute: Int = 4) {
        private val times = ArrayDeque<Long>()
        @Synchronized fun allow(now: Long = System.currentTimeMillis()): Boolean {
            while (times.isNotEmpty() && now - times.first() > 60_000) times.removeFirst()
            if (times.isNotEmpty() && now - times.last() < minGapMs) return false
            if (times.size >= perMinute) return false
            times.addLast(now); return true
        }
    }
    val limiter = Limiter()
}
