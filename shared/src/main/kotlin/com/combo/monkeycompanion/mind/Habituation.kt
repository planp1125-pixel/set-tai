package com.combo.monkeycompanion.mind

import kotlin.math.exp
import kotlin.math.pow

/**
 * Gets used to things: every exposure to an app adds 1, and the count fades with a half-life, so an app it hasn't
 * seen for days becomes interesting again. Novelty 1 = never seen, ~0 = seen a lot lately.
 */
class Habituation(private val halfLifeMs: Long = 36 * 3600_000L) {
    private val seen = HashMap<String, Pair<Float, Long>>()   // key -> (count, last time)

    fun count(key: String, now: Long): Float {
        val (c, at) = seen[key] ?: return 0f
        return c * 0.5f.pow(((now - at).coerceAtLeast(0L)).toFloat() / halfLifeMs)
    }
    fun novelty(key: String, now: Long) = exp(-0.7f * count(key, now))
    fun expose(key: String, now: Long) { seen[key] = (count(key, now) + 1f) to now }

    fun encode() = seen.entries.joinToString(";") { "${it.key}=${"%.3f".format(java.util.Locale.ROOT, it.value.first)}@${it.value.second}" }
    fun decode(s: String?) {
        seen.clear()
        s?.split(';')?.forEach { e -> runCatching {
            val (k, v) = e.split('='); val (c, at) = v.split('@'); seen[k] = c.toFloat() to at.toLong()
        } }
        // keep it small: the 80 most familiar keys
        if (seen.size > 80) seen.entries.sortedBy { it.value.first }.take(seen.size - 80).forEach { seen.remove(it.key) }
    }
}
