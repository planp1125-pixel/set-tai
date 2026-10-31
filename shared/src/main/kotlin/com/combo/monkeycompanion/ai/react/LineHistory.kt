package com.combo.monkeycompanion.ai.react

/**
 * v10.18 step 5 (REMEMBER), anti-repeat memory: everything a character said in reactions, so he never says the same
 * thing (or nearly the same thing) again. Kept per character, last [max] lines. Pure; no Random.
 */
class LineHistory(private val max: Int = 300) {
    private val lines = ArrayDeque<String>()
    private val norms = ArrayDeque<List<String>>()

    val size get() = lines.size

    fun add(line: String) {
        lines.addLast(line); norms.addLast(words(line))
        while (lines.size > max) { lines.removeFirst(); norms.removeFirst() }
    }

    /** True if [line] is not a repeat or near-repeat of anything remembered (or of [also], e.g. the same batch). */
    fun isFresh(line: String, also: Collection<String> = emptyList()): Boolean {
        val w = words(line)
        if (w.isEmpty()) return false
        if (norms.any { similar(w, it) }) return false
        return also.none { similar(w, words(it)) }
    }

    /** The newest [n] lines, newest first (sent to the AI as "don't say anything like these"). */
    fun recent(n: Int): List<String> = lines.toList().asReversed().take(n)

    fun all(): List<String> = lines.toList()

    companion object {
        /** Lowercase words without emoji, punctuation or action tags. */
        fun words(s: String): List<String> =
            s.lowercase().replace(Regex("\\[[^\\]]*]"), " ").replace(Regex("\\*[^*]*\\*"), " ")
                .replace(Regex("[^\\p{L}\\p{N}' ]"), " ").split(' ').map { it.trim('\'') }.filter { it.isNotBlank() }

        /** Same words, same opening, or mostly the same word set. */
        fun similar(a: List<String>, b: List<String>): Boolean {
            if (a.isEmpty() || b.isEmpty()) return false
            if (a == b) return true
            if (a.size >= 3 && b.size >= 3 && a.take(3) == b.take(3)) return true
            val sa = a.toSet(); val sb = b.toSet()
            val inter = sa.intersect(sb).size.toFloat()
            val union = sa.union(sb).size.toFloat()
            return union > 0f && inter / union >= 0.6f && minOf(sa.size, sb.size) >= 2
        }
    }
}
