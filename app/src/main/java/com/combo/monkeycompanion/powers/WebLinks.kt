package com.combo.monkeycompanion.powers

import java.util.Locale

/**
 * v10.48 "open this website": turns what the user or the brain said ("youtube.com", "https://x.com/a",
 * "the wikipedia site") into a safe https link. Only web links: never javascript:, file:, intent:, content: ...
 */
object WebLinks {
    /** Well-known sites by name (so "open amazon website" works without typing .in / .com). */
    val KNOWN = linkedMapOf(
        "youtube" to "youtube.com", "google" to "google.com", "gmail" to "mail.google.com", "google maps" to "maps.google.com",
        "maps" to "maps.google.com", "google news" to "news.google.com", "news" to "news.google.com", "wikipedia" to "wikipedia.org",
        "amazon" to "amazon.in", "flipkart" to "flipkart.com", "myntra" to "myntra.com", "swiggy" to "swiggy.com", "zomato" to "zomato.com",
        "instagram" to "instagram.com", "facebook" to "facebook.com", "twitter" to "x.com", "x" to "x.com", "reddit" to "reddit.com",
        "linkedin" to "linkedin.com", "github" to "github.com", "netflix" to "netflix.com", "hotstar" to "hotstar.com", "jiohotstar" to "hotstar.com",
        "chatgpt" to "chatgpt.com", "whatsapp" to "web.whatsapp.com", "whatsapp web" to "web.whatsapp.com", "cricbuzz" to "cricbuzz.com",
        "irctc" to "irctc.co.in", "spotify" to "open.spotify.com", "pinterest" to "pinterest.com", "quora" to "quora.com",
        "stack overflow" to "stackoverflow.com", "stackoverflow" to "stackoverflow.com", "bbc" to "bbc.com", "the hindu" to "thehindu.com",
    )

    private val DOMAIN = Regex("^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,24}(?::\\d{2,5})?(?:[/?#][^\\s]*)?$", RegexOption.IGNORE_CASE)

    /** A safe https (or http) link for [input], or null when it isn't a website. */
    fun normalize(input: String): String? {
        var s = input.trim()
        repeat(3) { s = s.trim('"', '\'', '“', '”', '‘', '’', '<', '>', '(', ')', '[', ']', '.', ',', '!', '?', ' ') }
        if (s.isEmpty() || s.length > 2000 || s.any { it.isWhitespace() && s.contains("://") }) return null
        known(s)?.let { return it }
        val lower = s.lowercase(Locale.ROOT)
        val scheme = Regex("^([a-z][a-z0-9+.-]*):").find(lower)?.groupValues?.get(1)
        if (scheme != null && scheme != "http" && scheme != "https") {
            // "example.com:8080" looks like a scheme; anything else (javascript:, file:, intent:...) is refused
            if (!DOMAIN.matches(s)) return null
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            val rest = s.substringAfter("://")
            if (rest.isEmpty() || rest.contains('@') && rest.substringBefore('/').contains('@')) return null   // no user:pass@ tricks
            return if (DOMAIN.matches(rest)) s else null
        }
        if (s.startsWith("www.", true) || DOMAIN.matches(s)) {
            if (s.contains(' ')) return null
            return "https://$s"
        }
        return null
    }

    /** "youtube" / "the amazon website" -> https://amazon.in, else null. */
    fun known(name: String): String? {
        val n = name.lowercase(Locale.ROOT).trim()
            .replace(Regex("^(the |a |an )"), "").replace(Regex(" (website|web site|site|webpage|web page|page|dot com|\\.com)$"), "").trim()
        return KNOWN[n]?.let { "https://$it" }
    }

    /** The host to show in "Open youtube.com?". */
    fun host(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore('?').removePrefix("www.").ifBlank { url }
}
