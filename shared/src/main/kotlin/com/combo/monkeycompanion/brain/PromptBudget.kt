package com.combo.monkeycompanion.brain

/**
 * v10.13.1: keeps on-device prompts inside the model's context window (e.g. "..._ekv1280.litertlm" = 1280 tokens).
 * Input is kept under [RATIO] of the context so the reply still fits. Oldest history goes first, then the system
 * prompt is compacted, then (last resort) the user text is cut.
 */
object PromptBudget {
    const val RATIO = 0.7f
    const val DEFAULT_CTX = 4096

    data class Turn(val fromUser: Boolean, val text: String)
    data class Fit(val system: String, val history: List<Turn>, val user: String, val tokens: Int, val trimmed: Boolean)

    /** Context size the model file was built for, read from its name ("ekv1280" → 1280). */
    fun contextFromName(name: String): Int =
        Regex("ekv(\\d{3,5})").find(name.lowercase())?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(256, 32768) ?: DEFAULT_CTX

    /** Conservative token estimate (~3 chars per token for Latin text, 1 per char for Tamil/emoji etc). */
    fun estimate(s: String): Int {
        var ascii = 0; var other = 0
        for (ch in s) if (ch.code < 128) ascii++ else other++
        return ascii / 3 + other + 4
    }

    private fun cut(s: String, maxTokens: Int): String {
        if (estimate(s) <= maxTokens) return s
        var lo = 0; var hi = s.length
        while (lo < hi) { val m = (lo + hi + 1) / 2; if (estimate(s.take(m)) <= maxTokens) lo = m else hi = m - 1 }
        return s.take(lo).trimEnd() + "…"
    }

    /** A short system prompt: the first lines of the full one (who I am, how to talk), capped to [maxTokens]. */
    fun compactSystem(system: String, maxTokens: Int): String {
        val head = system.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(6).joinToString("\n")
        return cut(head + "\nReply in 1-2 short, friendly sentences.", maxTokens)
    }

    fun fit(system: String, history: List<Turn>, user: String, contextTokens: Int, minimal: Boolean = false): Fit {
        val budget = (contextTokens * (if (minimal) 0.5f else RATIO)).toInt()
        var sys = if (minimal) compactSystem(system, budget / 3) else system
        var u = cut(user, budget / 3)
        val hist = ArrayList(if (minimal) emptyList() else history)
        fun total() = estimate(sys) + estimate(u) + hist.sumOf { estimate(it.text) }
        var trimmed = minimal
        if (estimate(sys) > budget / 2) { sys = compactSystem(system, budget / 2); trimmed = true }
        while (hist.isNotEmpty() && total() > budget) { hist.removeAt(0); trimmed = true }
        if (total() > budget) { sys = compactSystem(system, (budget - estimate(u)).coerceAtLeast(budget / 4)); trimmed = true }
        if (total() > budget) { u = cut(u, (budget - estimate(sys)).coerceAtLeast(16)); trimmed = true }
        return Fit(sys, hist, u, total(), trimmed)
    }

    /** The LiteRT-LM "Input token ids are too long" family of errors. */
    fun isTooLong(msg: String?): Boolean = msg != null && (msg.contains("too long", true) || Regex(">=\\s*\\d+").containsMatchIn(msg) && msg.contains("token", true))

    const val FRIENDLY = "Oops, my little on-device brain got overloaded 🙈 Say that again, a bit shorter?"
}
