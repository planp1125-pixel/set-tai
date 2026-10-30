package com.combo.monkeycompanion.v5

import kotlin.random.Random

/**
 * v5 "eats notifications": when a proactive message pipe fires, the character now and then grabs a fake banner
 * card, munches it and burps out a 1-line summary. Purely visual: the real notification is never touched.
 */
object NotifMunch {
    const val CHANCE = 0.3f
    const val COOLDOWN_MS = 20 * 60_000L
    const val SECS = 3.6f

    fun should(nowMs: Long, lastMs: Long, saver: Boolean, rnd: Float = Random.nextFloat()) =
        !saver && nowMs - lastMs >= COOLDOWN_MS && rnd < CHANCE

    /** Phases over [SECS]: card flies in (0-0.8), munch (0.8-2.4), burp (2.4+). */
    enum class Phase { FLY_IN, MUNCH, BURP }
    fun phase(t: Float) = when { t < 0.8f -> Phase.FLY_IN; t < 2.4f -> Phase.MUNCH; else -> Phase.BURP }
    /** How much of the card is left (bites). */
    fun cardLeft(t: Float) = when { t < 0.8f -> 1f; t < 2.4f -> 1f - ((t - 0.8f) / 1.6f * 4).toInt() / 4f; else -> 0f }

    fun burp(summary: String, characterId: String): String {
        val s = summary.trim().replace(Regex("\\s+"), " ").let { if (it.length > 80) it.take(77) + "…" else it }
        val pre = when (characterId) { "superboy" -> "*hero burp*"; "armour" -> "*metallic burp*"; "mask" -> "*burp behind the mask*"; else -> "*BURP*" }
        return "$pre $s"
    }
}
