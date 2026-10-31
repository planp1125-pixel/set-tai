package com.combo.monkeycompanion.ai.react

/**
 * v10.18 "Feel the touch": one character's reaction engine (see docs/REACTION_ENGINE.md).
 *
 *  - REFLEX: a touch takes a ready line from the bank instantly (written earlier by the AI for this mood).
 *  - THINK: big moments / streaks / an empty bank ask the AI for a fresh line (at most one in flight, spaced out).
 *  - PREPARE: in quiet moments the AI refills the bank, avoiding everything said before.
 *  - REMEMBER: every shown line goes into [history], so nothing repeats.
 *
 * Pure Kotlin, no Android, no Random. All times are wall-clock milliseconds passed in by the caller.
 * The same engine serves every character; only the system prompt (Persona + Body Sense) differs.
 */
class ReactionEngine(
    val thoughtGapMs: Long = 8_000,
    val thoughtTimeoutMs: Long = 10_000,
    val refillGapMs: Long = 45_000,
    val refillTimeoutMs: Long = 90_000,
    val perKind: Int = 2,
    val maxPerKind: Int = 5,
    val maxKindsPerRefill: Int = 4,
    val lineMaxAgeMs: Long = 6 * 3_600_000L,
) {
    data class Banked(val text: String, val key: String, val at: Long)

    /** What to do right now for a touch. [line] = show instantly; [thoughtRequest] = ask the AI (token in [thoughtToken]). */
    data class Decision(val line: String?, val thoughtRequest: String?, val thoughtToken: Int)

    val history = LineHistory()
    private val bank = HashMap<ReactKind, ArrayDeque<Banked>>()
    private val used = IntArray(ReactKind.values().size)
    private var styleN = 0

    private var thoughtToken = 0
    private var thoughtInFlight = false
    private var thoughtAt = Long.MIN_VALUE / 2
    private var refillInFlight = false
    private var refillAt = Long.MIN_VALUE / 2
    private var refillKey = ""

    fun count(kind: ReactKind, key: MoodKey): Int = bank[kind]?.count { it.key == key.toString() } ?: 0
    fun total(): Int = bank.values.sumOf { it.size }

    /** REFLEX + THINK for one touch. */
    @Synchronized fun onTouch(feel: TouchFeel, key: MoodKey, now: Long): Decision {
        used[feel.kind.ordinal]++
        prune(now)
        val line = take(feel.kind, key)
        val wantThought = feel.kind.big || feel.streak >= 3 || line == null
        var req: String? = null
        if (wantThought && !thoughtInFlight && now - thoughtAt >= thoughtGapMs) {
            thoughtInFlight = true; thoughtAt = now; thoughtToken++
            req = ReactionPrompts.thought(feel, key, history.recent(10), ReactionPrompts.style(styleN++))
        } else if (thoughtInFlight && now - thoughtAt > thoughtTimeoutMs) {
            thoughtInFlight = false   // an answer that never came doesn't block forever
        }
        return Decision(line, req, thoughtToken)
    }

    /** The AI's fresh thought arrived. Returns the line to show (tags kept for the body), or null if late/stale/repeat. */
    @Synchronized fun acceptThought(token: Int, answer: String?, now: Long): String? {
        if (token != thoughtToken) return null
        thoughtInFlight = false
        if (now - thoughtAt > thoughtTimeoutMs) return null
        val line = ReactionPrompts.clean(answer ?: return null, keepTags = true) ?: return null
        if (!history.isFresh(line)) return null
        history.add(line)
        return line
    }

    /** PREPARE: a refill request when the bank is low for this mood, or null (spaced out, one at a time). */
    @Synchronized fun refillRequest(key: MoodKey, now: Long): String? {
        if (refillInFlight && now - refillAt < refillTimeoutMs) return null
        if (now - refillAt < refillGapMs) return null
        prune(now)
        val needing = ReactKind.values().filter { count(it, key) < perKind }
            .sortedWith(compareByDescending<ReactKind> { used[it.ordinal] }.thenBy { it.ordinal })
            .take(maxKindsPerRefill)
        if (needing.isEmpty()) return null
        refillInFlight = true; refillAt = now; refillKey = key.toString()
        val req = ReactionPrompts.refill(needing, perKind, key, history.recent(15), styleN)
        styleN += needing.size * perKind
        return req
    }

    /** The refill answer arrived: keep fresh lines (no repeats, no duplicates in the batch). Returns how many were kept. */
    @Synchronized fun acceptRefill(answer: String?, now: Long): Int {
        refillInFlight = false
        val key = refillKey
        if (answer.isNullOrBlank() || key.isEmpty()) return 0
        val batch = ArrayList<String>()
        var kept = 0
        for ((kind, line) in ReactionPrompts.parseRefill(answer, ReactKind.values().toList())) {
            val q = bank.getOrPut(kind) { ArrayDeque() }
            if (q.count { it.key == key } >= maxPerKind) continue
            val pending = q.map { it.text } + batch
            if (!history.isFresh(line, pending)) continue
            q.addLast(Banked(line, key, now)); batch.add(line); kept++
        }
        return kept
    }

    /** Forget the in-flight refill (e.g. the brain was switched off). */
    @Synchronized fun cancelRefill() { refillInFlight = false }

    /** Exact mood match first; else a line written for the same time of day and friendship (mood flips fast). */
    private fun take(kind: ReactKind, key: MoodKey): String? {
        val q = bank[kind] ?: return null
        val k = key.toString()
        return takeWhere(q) { it.key == k }
            ?: takeWhere(q) { b -> MoodKey.parse(b.key)?.let { it.time == key.time && it.bond == key.bond } == true }
    }

    private fun takeWhere(q: ArrayDeque<Banked>, ok: (Banked) -> Boolean): String? {
        val it = q.iterator()
        while (it.hasNext()) {
            val b = it.next()
            if (!ok(b)) continue
            it.remove()
            if (!history.isFresh(b.text)) continue
            history.add(b.text)
            return b.text
        }
        return null
    }

    private fun prune(now: Long) {
        for (q in bank.values) q.removeAll { now - it.at > lineMaxAgeMs }
    }

    // ---- saving (plain text, one record per line; tabs/newlines are never inside lines) ----

    @Synchronized fun save(): String = buildString {
        append("V1\n")
        append("S\t").append(styleN).append('\n')
        for (h in history.all()) append("H\t").append(h.oneLine()).append('\n')
        for ((k, q) in bank) for (b in q) append("B\t").append(k.id).append('\t').append(b.key).append('\t').append(b.at).append('\t').append(b.text.oneLine()).append('\n')
    }

    @Synchronized fun load(s: String) {
        if (!s.startsWith("V1")) return
        for (row in s.lineSequence()) {
            val p = row.split('\t')
            when (p.firstOrNull()) {
                "S" -> styleN = p.getOrNull(1)?.toIntOrNull() ?: 0
                "H" -> p.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { history.add(it) }
                "B" -> if (p.size >= 5) {
                    val k = ReactKind.byId(p[1]) ?: continue
                    bank.getOrPut(k) { ArrayDeque() }.addLast(Banked(p[4], p[2], p[3].toLongOrNull() ?: 0L))
                }
            }
        }
    }

    private fun String.oneLine() = replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
}
