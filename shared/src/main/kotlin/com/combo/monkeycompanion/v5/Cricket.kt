package com.combo.monkeycompanion.v5

/**
 * v5 cricket, on demand only ("score enna?"): no polling. Source decision: the keyless public Cricinfo RSS
 * (https://static.cricinfo.com/rss/livescores.xml) is fetched once per ask; its titles look like
 * "India 245/6 * v Australia 240". It can be stale or change without notice, so the reply says where it came from,
 * and when nothing usable (or stale > 12 h) comes back the character says so instead of guessing.
 */
object Cricket {
    const val FEED = "https://static.cricinfo.com/rss/livescores.xml"
    const val STALE_MS = 12 * 3600_000L

    data class Match(val title: String, val teamA: String, val teamB: String, val battingA: Boolean, val battingB: Boolean)

    fun asks(text: String): Boolean {
        val t = text.lowercase()
        return listOf("score enna", "score ena", "cricket score", "match score", "what's the score", "whats the score", "india score", "live score").any { it in t }
    }

    fun parse(xml: String): List<Match> = Regex("<item>\\s*<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).findAll(xml).mapNotNull { m ->
        val title = m.groupValues[1].trim().replace("&amp;", "&")
        val parts = title.split(" v ", limit = 2)
        if (parts.size != 2) null else Match(title, parts[0].trim(), parts[1].trim(), parts[0].contains("*"), parts[1].contains("*"))
    }.toList()

    /** pubDate in RFC-1123 -> epoch ms, or null. */
    fun pubDate(xml: String): Long? = Regex("<pubDate>(.*?)</pubDate>").find(xml)?.groupValues?.get(1)?.let {
        runCatching { java.time.ZonedDateTime.parse(it.trim(), java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
    }

    /** India first (men's or women's), else a team the user named, else null. */
    fun pick(matches: List<Match>, want: String = "India"): Match? =
        matches.firstOrNull { it.teamA.startsWith(want, true) || it.teamB.startsWith(want, true) }

    enum class Feeling { CELEBRATE, CRY, NERVOUS, NEUTRAL }

    /** From a title alone we only know who's batting / the scores; "won"/"lost" words, when present, decide. */
    fun feeling(m: Match, team: String = "India"): Feeling {
        val t = m.title.lowercase(); val tm = team.lowercase()
        return when {
            "$tm won" in t || "$tm win" in t -> Feeling.CELEBRATE
            "won" in t && tm !in t.substringBefore("won") -> Feeling.CRY
            m.battingA || m.battingB -> Feeling.NERVOUS
            else -> Feeling.NEUTRAL
        }
    }

    fun reply(m: Match?, fresh: Boolean): String = when {
        m == null -> "Couldn't find an India match in the live feed right now 🏏 Ask me to search the web?"
        !fresh -> "The live feed looks old, so I won't guess 🏏 Last seen: ${m.title}"
        else -> "🏏 ${m.title} (Cricinfo live feed; * = batting)"
    }
}
