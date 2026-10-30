package com.combo.monkeycompanion.speech

/**
 * How the monkey talks. V1 shows a cloud bubble; a later version can add a
 * text-to-speech implementation (or wrap this one) without touching the brain.
 */
interface Speech {
    fun say(text: String, durationMs: Long = defaultDuration(text))
    fun hush()
    val isSpeaking: Boolean

    companion object {
        fun defaultDuration(text: String): Long = (1800L + 55L * text.length).coerceAtMost(6000L)
    }
}
