package com.combo.monkeycompanion.agent

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One thing Settai remembers about its human, stored in second person ("You like filter coffee"). */
data class Fact(val id: Long, val text: String, val kind: String, val at: Long)

/**
 * v4 memory: a small JSON file on the phone (never synced). Facts come from "remember that ...",
 * "my name is ...", birthdays, and the brain's remember tool. Relevant ones go into the prompt.
 */
class MemoryStore(private val file: File) {
    private val facts = ArrayList<Fact>()
    private var loaded = false

    @Synchronized private fun load() {
        if (loaded) return
        loaded = true
        runCatching {
            if (!file.exists()) return
            val a = JSONArray(file.readText())
            for (i in 0 until a.length()) a.getJSONObject(i).let { o -> facts.add(Fact(o.getLong("id"), o.getString("text"), o.optString("kind", "other"), o.optLong("at"))) }
        }
    }

    @Synchronized private fun save() {
        val a = JSONArray()
        facts.forEach { a.put(JSONObject().put("id", it.id).put("text", it.text).put("kind", it.kind).put("at", it.at)) }
        runCatching { file.parentFile?.mkdirs(); val tmp = File(file.path + ".tmp"); tmp.writeText(a.toString()); tmp.renameTo(file) }
    }

    @Synchronized fun all(): List<Fact> { load(); return facts.toList() }

    /** Adds a fact (a new name replaces the old one; exact repeats are ignored). */
    @Synchronized fun add(text: String, kind: String = MemoryParse.kindOf(text), now: Long = System.currentTimeMillis()): Fact {
        load()
        val t = text.trim().trimEnd('.').replaceFirstChar { it.uppercase() }
        facts.firstOrNull { MemoryParse.norm(it.text) == MemoryParse.norm(t) }?.let { return it }
        if (kind == "name") facts.removeAll { it.kind == "name" }
        val f = Fact(maxOf(now, (facts.maxOfOrNull { it.id } ?: 0L) + 1), t, kind, now)
        facts.add(f)
        while (facts.size > MAX) facts.removeAt(facts.indexOfFirst { it.kind != "name" }.coerceAtLeast(0))
        save()
        return f
    }

    @Synchronized fun remove(id: Long): Boolean { load(); val r = facts.removeAll { it.id == id }; if (r) save(); return r }

    /** Forgets every fact matching [what] (all its meaningful words). "everything" clears all. */
    @Synchronized fun forget(what: String): List<Fact> {
        load()
        val w = what.trim().lowercase()
        val gone = if (w in setOf("everything", "all", "all of it", "everything about me")) facts.toList()
        else { val keys = MemoryParse.words(w); if (keys.isEmpty()) emptyList() else facts.filter { f -> MemoryParse.words(f.text).containsAll(keys) } }
        if (gone.isNotEmpty()) { facts.removeAll(gone.toSet()); save() }
        return gone
    }

    @Synchronized fun clear() { load(); facts.clear(); save() }

    /** The facts worth putting in the prompt for [query]: the name, upcoming-ish birthdays, then by word overlap. */
    fun relevant(query: String, n: Int = 8): List<Fact> = MemoryParse.relevant(all(), query, n)

    fun name(): String? = all().firstOrNull { it.kind == "name" }?.text?.let { MemoryParse.nameOf(it) }

    companion object { const val MAX = 200 }
}

object MemoryParse {
    private val STOP = setOf("the", "a", "an", "is", "are", "am", "to", "of", "and", "my", "your", "you", "i", "me", "that", "it", "in", "on", "at", "for",
        "remember", "forget", "about", "please", "pls", "da", "machan", "this", "these", "be", "was", "do", "does", "like", "likes", "with", "what", "who")
    fun words(s: String): Set<String> = Regex("[\\p{L}\\p{N}']+").findAll(s.lowercase()).map { it.value.trim('\'').removeSuffix("'s") }
        .filter { it.length > 1 && it !in STOP }.map { if (it.length > 4 && it.endsWith("s")) it.dropLast(1) else it }.toSet()
    fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** "my birthday is in June" -> "your birthday is in June"; "I love dosa" -> "you love dosa". */
    fun toYou(s: String): String {
        var t = " " + s.trim() + " "
        val map = listOf("i am" to "you are", "i'm" to "you're", "im" to "you're", "i was" to "you were", "i have" to "you have", "i've" to "you've",
            "my" to "your", "mine" to "yours", "me" to "you", "myself" to "yourself", "i" to "you")
        for ((a, b) in map) t = t.replace(Regex("(?i)(?<=[\\s,])" + Regex.escape(a) + "(?=[\\s,.!?])"), b)
        return t.trim()
    }

    fun kindOf(t: String): String {
        val l = t.lowercase()
        return when {
            Regex("\\b(name is|called|call you|call me)\\b").containsMatchIn(l) && l.startsWith("your name") || l.startsWith("you are called") -> "name"
            l.contains("birthday") || l.contains("anniversary") || l.contains("born on") -> "birthday"
            Regex("\\b(like|love|favourite|favorite|enjoy|hate|don't like|can't stand|prefer)\\b").containsMatchIn(l) -> "like"
            Regex("\\b(every|always|usually|daily|each|wake up|sleep at|go to bed|habit|gym|walk)\\b").containsMatchIn(l) -> "habit"
            else -> "other"
        }
    }

    fun nameOf(fact: String): String? = Regex("(?i)^your name is (.+)$").find(fact.trim())?.groupValues?.get(1)?.trim()?.trimEnd('.')

    /** Explicit memory requests in chat. Returns the fact to store (second person) or null. */
    fun remember(input: String): String? {
        val t = input.trim().trimEnd('.', '!')
        Regex("(?i)^(?:please |pls |settai,? )?(?:remember|note|ninaivu vachuko|nyabagam vachuko|gyabagam vechuko)(?: that| this|:)?\\s+(.{3,160})$").find(t)?.let { return toYou(it.groupValues[1]) }
        Regex("(?i)^(.{3,160}?)[,.]?\\s+(?:remember (?:it|that)|ninaivu vachuko|nyabagam vachuko)$").find(t)?.let { return toYou(it.groupValues[1]) }
        Regex("(?i)^(?:my name is|call me|en peru|enoda peru|en per)\\s+([\\p{L} .'-]{1,40})$").find(t)?.let { return "Your name is " + it.groupValues[1].trim().split(' ').joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } } }
        Regex("(?i)^(my|[\\p{L}]+'s|[\\p{L}]+s) birthday is (?:on )?(.{3,40})$").find(t)?.let { m ->
            val who = m.groupValues[1]; return (if (who.equals("my", true)) "Your" else who.replaceFirstChar { it.uppercase() }) + " birthday is " + m.groupValues[2].trim()
        }
        return null
    }

    /** "forget that I like coffee" / "forget my birthday" / "forget everything". */
    fun forget(input: String): String? =
        Regex("(?i)^(?:please |pls )?(?:forget|delete the memory|marandhudu|marandhuru)(?: about| that| what i said about)?\\s+(.{2,80})$").find(input.trim().trimEnd('.', '!'))?.groupValues?.get(1)
            ?.let { toYou(it).trim() }

    fun relevant(all: List<Fact>, query: String, n: Int): List<Fact> {
        val q = words(query)
        val name = all.filter { it.kind == "name" }
        val scored = all.filter { it.kind != "name" }.map { f ->
            val overlap = words(f.text).count { it in q }
            f to overlap * 10 + (if (f.kind == "birthday") 2 else 0) + (if (f.kind == "like") 1 else 0)
        }.sortedWith(compareByDescending<Pair<Fact, Int>> { it.second }.thenByDescending { it.first.at })
        return (name + scored.map { it.first }).take(n)
    }
}
