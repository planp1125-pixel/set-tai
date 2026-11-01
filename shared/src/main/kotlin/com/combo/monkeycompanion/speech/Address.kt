package com.combo.monkeycompanion.speech

/**
 * v10.21 how the characters address you. The old scripted lines say "machan" a lot; a friend calls you by your
 * NAME. Every line that's shown goes through [personalize]:
 *  - we know your name (you told a character "my name is …", Memory on): "machan" becomes your name, but only
 *    once in a line (and only the first time; any extra "machan" is dropped);
 *  - we don't know it: "machan" is simply dropped, and the sentence is tidied ("Bye bye, machan! 👋" → "Bye bye! 👋").
 * Pure, so it is tested without Android.
 */
object Address {
    private val WORD = Regex("(?i)\\bmachan\\b")
    // ", machan" / " machan" before punctuation or the end; "Machan, " / "Machan! " at the start; "machan " elsewhere
    private val COMMA_BEFORE = Regex("(?i),?\\s*\\bmachan\\b(?=\\s*([!?.…,:;)💪👋🎉🙏❤️✨☕🌙😴🥵📶⚡🔋👀👍🌿🫂💛🏆🍌🪧✍️]|$))")
    private val LEADING = Regex("(?i)^\\s*machan\\s*[,!.:]*\\s*")
    private val ANY = Regex("(?i)\\s*\\bmachan\\b")

    /** [name] = what the user told us to call them (null/blank = unknown). */
    fun personalize(text: String, name: String?): String {
        if (!WORD.containsMatchIn(text)) return text
        val n = name?.trim()?.takeIf { it.isNotEmpty() && it.length <= 24 }?.replaceFirstChar { it.uppercase() }
        if (n != null) {
            var used = false
            val out = WORD.replace(text) { m -> if (used) "\u0000" else { used = true; n } }
            return tidy(out.replace(Regex("[ ,]*\u0000"), ""))
        }
        var s = LEADING.replace(text) { "" }
        s = COMMA_BEFORE.replace(s, "")
        s = ANY.replace(s, "")
        return tidy(s).replaceFirstChar { it.uppercase() }
    }

    private fun tidy(s: String) = s.replace(Regex("\\s{2,}"), " ").replace(Regex("\\s+([!?,]|\\.(?!\\.))"), "$1").replace(Regex("^[,\\s]+"), "").trim()
}
