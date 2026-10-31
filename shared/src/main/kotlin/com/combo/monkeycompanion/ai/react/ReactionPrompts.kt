package com.combo.monkeycompanion.ai.react

/**
 * v10.18 what we ask the AI, and how we read its answers. The character's personality and live body note (BODY NOW)
 * are already in the system prompt (Persona + Body Sense); these are only the user-turn requests.
 */
object ReactionPrompts {
    /** Kinds of line, rotated so the SHAPE of the sentences keeps changing too (not just the words). */
    val STYLES = listOf(
        "a cheeky joke",
        "a dramatic over-reaction",
        "a question back to the human",
        "a complaint with attitude",
        "sweet and cosy",
        "a silly excuse or blame someone else",
        "a playful threat of revenge",
        "pretending to be offended",
        "a mention of the time of day or how your body feels",
        "a few Tamil words in English letters (Tanglish) mixed in",
        "a tiny sound effect plus a few words",
        "a callback to something that happened to you earlier",
    )

    fun style(n: Int): String = STYLES[((n % STYLES.size) + STYLES.size) % STYLES.size]

    /**
     * Fill the bank: [perKind] lines for each kind in [kinds], for this [key]. [avoid] = recent lines not to echo.
     * [styleStart] rotates which styles are asked for. Answer format: one line per row, "KIND | line".
     */
    fun refill(kinds: List<ReactKind>, perKind: Int, key: MoodKey, avoid: List<String>, styleStart: Int): String = buildString {
        append("PREPARE REACTIONS (not shown yet): your human will soon touch you. Write short things you'd blurt out for each touch below, ")
        append("in your own personality and from how your body feels now (").append(key.words()).append("). ")
        append("Each line max 12 words, first person, funny and specific to that touch. ")
        append("Make every line different in wording AND shape.\n")
        var s = styleStart
        for (k in kinds) {
            append("- ").append(k.id.uppercase()).append(" = you get ").append(k.words).append(". Styles: ")
            append((0 until perKind).joinToString(" / ") { style(s++) }).append('\n')
        }
        if (avoid.isNotEmpty()) {
            append("Never repeat or closely copy anything you said before, like: ")
            append(avoid.joinToString(" | ") { it.take(70) }).append('\n')
        }
        append("Answer ONLY lines in this exact format, nothing else:\n")
        append(kinds.first().id.uppercase()).append(" | your line\n")
    }

    /** A fresh thought right after a big moment / a streak. One line, may carry ONE action tag. */
    fun thought(feel: TouchFeel, key: MoodKey, avoid: List<String>, style: String): String = buildString {
        append("SPONTANEOUS MOMENT (nobody asked you anything): ").append(feel.describe()).append(' ')
        append("You feel: ").append(key.words()).append(". ")
        append("Say ONE short line (max 14 words) you'd naturally blurt out right now about exactly this, as ").append(style).append(". ")
        append("Be specific, funny, in character. Optional: ONE action tag like [angry] [giggle] [hide] [dance] [jump].")
        if (avoid.isNotEmpty()) append(" Don't repeat or copy: ").append(avoid.take(8).joinToString(" | ") { it.take(60) }).append('.')
    }

    /** Reads "KIND | line" rows (tolerant of "KIND: line", "1. KIND - line", quotes, bullets). Unknown kinds are skipped. */
    fun parseRefill(text: String, allowed: Collection<ReactKind>): List<Pair<ReactKind, String>> {
        val out = ArrayList<Pair<ReactKind, String>>()
        val row = Regex("^[\\s\\-*•\\d.)]*\\**([A-Za-z]+)\\**\\s*[|:–—-]\\s*(.+)$")
        for (raw in text.lineSequence()) {
            val m = row.find(raw.trim()) ?: continue
            val kind = ReactKind.byId(m.groupValues[1]) ?: continue
            if (kind !in allowed) continue
            val line = clean(m.groupValues[2]) ?: continue
            out.add(kind to line)
        }
        return out
    }

    /** Strips quotes / think blocks / action tags; null if empty, too long or not a real line. */
    fun clean(s: String, keepTags: Boolean = false): String? {
        var t = s.replace(Regex("(?s)<think>.*?</think>"), "").trim()
        if (!keepTags) t = t.replace(Regex("\\[[^\\]]*]"), " ")
        t = t.replace(Regex("\\s+"), " ").trim().trim('"', '\'', '“', '”', '`').trim()
        if (t.length < 2 || t.length > 140) return null
        if (LineHistory.words(t).isEmpty()) return null
        return t
    }
}
