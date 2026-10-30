package com.combo.monkeycompanion.agent

/** Finds one-time passwords in notification text (pure). Settai shows them; it never forwards them. */
object Otp {
    private val context = Regex("\\b(otp|one[- ]time|verification|verify|security code|login code|passcode|auth(entication)? code|code is|your code|pin)\\b", RegexOption.IGNORE_CASE)
    private val code = Regex("(?<![\\d₹$])(?<!\\d[.,:/-])(\\d{4,8})(?!\\d|%|[.,:/-]\\d)")
    private val spaced = Regex("(?<!\\d)(\\d{3})[ -](\\d{3})(?!\\d)")

    /** The code if [text] looks like an OTP message, else null. */
    fun find(text: String): String? {
        if (!context.containsMatchIn(text)) return null
        spaced.find(text)?.let { return it.groupValues[1] + it.groupValues[2] }
        // Prefer a code right after "is"/":"; skip years and amounts.
        val all = code.findAll(text).map { it.groupValues[1] }.filter { !(it.length == 4 && it.startsWith("20") && it.toInt() in 2000..2099) }.toList()
        return all.firstOrNull()
    }

    /** Replaces OTP codes with a placeholder (before anything goes to an online brain). */
    fun mask(text: String): String {
        if (find(text) == null) return text
        return spaced.replace(code.replace(text) { "[code hidden]" }) { "[code hidden]" }
    }
}
