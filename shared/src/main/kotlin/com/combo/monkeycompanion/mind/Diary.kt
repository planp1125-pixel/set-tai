package com.combo.monkeycompanion.mind

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.pow
import kotlin.random.Random

/**
 * The creature's own small diary (per character): what happened to *it*. Salience fades with age (some kinds stay
 * memorable longer), low-salience entries are forgotten, old repeats roll up into one line per day.
 *
 * v9: User-configurable diary limits (`maxEntries`, `retentionDays`), category toggles (`logPlaces`, `logChats`,
 * `logNight`, `logWorld`), pinned entries (`pinned = true` never get evicted), and custom user memories/rules
 * (`Kind.CUSTOM_NOTE`).
 */
class Diary(private val zone: ZoneId = ZoneId.systemDefault(), val cap: Int = 150) {
    enum class Kind(val base: Float, val halfLifeDays: Float) {
        SLEPT(0.25f, 2f), POKED(0.2f, 1.5f), SHAKEN(0.45f, 2f), PETTED(0.25f, 2f),
        GAME_WON(0.5f, 5f), GAME_LOST(0.45f, 4f), FESTIVAL(0.85f, 30f), USER_AWAY(0.5f, 4f),
        CHAT_LINE(0.55f, 7f), CRASH_LANDING(0.5f, 3f), POWER(0.3f, 2f), EXPLORED(0.4f, 3f),
        LEFT(0.3f, 2f), HABIT(0.2f, 3f), ROLLUP(0.35f, 6f),
        CUSTOM_NOTE(1.0f, 365f),
    }

    data class Entry(
        val ts: Long,
        val kind: Kind,
        val subject: String,
        val count: Int = 1,
        val salience: Float,
        val recalledAt: Long = 0L,
        val pinned: Boolean = false,
    )

    /** Configurable max entries (`0` = pause automatic recording, or `10`, `30`, `60`, `150`, `300`). */
    var maxEntries: Int = cap
    /** Auto-expire non-pinned entries older than N days (`0` = keep forever, or `1`, `7`, `30`). */
    var retentionDays: Int = 0
    /** Category toggles for what Settai is allowed to record in its diary. */
    var logPlaces: Boolean = true
    var logChats: Boolean = true
    var logNight: Boolean = true
    var logWorld: Boolean = true

    private val entries = ArrayList<Entry>()
    val all: List<Entry> get() = entries.toList()

    fun isKindAllowed(kind: Kind): Boolean = when (kind) {
        Kind.CUSTOM_NOTE -> true
        Kind.EXPLORED, Kind.LEFT -> logPlaces
        Kind.CHAT_LINE, Kind.PETTED, Kind.POKED, Kind.GAME_WON, Kind.GAME_LOST -> logChats
        Kind.SLEPT, Kind.USER_AWAY -> logNight
        Kind.FESTIVAL, Kind.POWER, Kind.CRASH_LANDING, Kind.SHAKEN, Kind.HABIT, Kind.ROLLUP -> logWorld
    }

    fun effective(e: Entry, now: Long): Float {
        if (e.pinned || e.kind == Kind.CUSTOM_NOTE) return 1.5f
        val ageDays = (now - e.ts).coerceAtLeast(0L) / 86_400_000f
        return e.salience * 0.5f.pow(ageDays / e.kind.halfLifeDays)
    }

    /**
     * Something happened. [subject]: the app / game / festival / line. Repeats of the same thing today bump the count
     * (and salience a little) instead of adding lines. Rare things are more salient.
     */
    fun add(kind: Kind, subject: String, now: Long, extra: Float = 0f, pinned: Boolean = false): Entry {
        if (!pinned && kind != Kind.CUSTOM_NOTE) {
            if (maxEntries <= 0 || !isKindAllowed(kind)) {
                return Entry(now, kind, subject.take(80), 1, 0f, 0L, false)
            }
        }
        val today = day(now)
        val i = if (kind == Kind.CUSTOM_NOTE) -1 else entries.indexOfLast { it.kind == kind && it.subject == subject && day(it.ts) == today }
        val e = if (i >= 0) {
            val o = entries[i]
            o.copy(ts = now, count = o.count + 1, salience = (o.salience + 0.05f).coerceAtMost(1f), pinned = o.pinned || pinned).also { entries[i] = it }
        } else {
            val rarity = if (entries.none { it.kind == kind && it.subject == subject }) 0.15f else 0f
            Entry(now, kind, subject.take(120), 1, (kind.base + rarity + extra).coerceIn(0f, 1f), 0L, pinned || kind == Kind.CUSTOM_NOTE).also { entries += it }
        }
        enforceLimits(now)
        return e
    }

    /** Adds a pinned user memory or behaviour rule directly into this character's diary. */
    fun addCustomNote(note: String, now: Long = System.currentTimeMillis(), pinned: Boolean = true): Entry =
        add(Kind.CUSTOM_NOTE, note.trim(), now, extra = 0.5f, pinned = pinned)

    /** Toggles the `pinned` state of the entry matching [ts]. Pinned entries are never evicted. */
    fun togglePin(ts: Long): Boolean {
        val idx = entries.indexOfFirst { it.ts == ts }
        if (idx < 0) return false
        val cur = entries[idx]
        entries[idx] = cur.copy(pinned = !cur.pinned)
        return entries[idx].pinned
    }

    /** Deletes a single diary entry by timestamp [ts]. */
    fun deleteEntry(ts: Long): Boolean {
        val idx = entries.indexOfFirst { it.ts == ts }
        if (idx < 0) return false
        entries.removeAt(idx)
        return true
    }

    /** Clears all non-pinned entries while keeping pinned entries safe. */
    fun clearUnpinned() {
        entries.removeAll { !it.pinned }
    }

    /** Enforces [retentionDays] and [maxEntries] while keeping all `pinned == true` entries intact. */
    fun enforceLimits(now: Long = System.currentTimeMillis()) {
        if (retentionDays > 0) {
            val maxAgeMs = retentionDays * 86_400_000L
            entries.removeAll { !it.pinned && (now - it.ts) > maxAgeMs }
        }
        val limit = maxEntries
        if (limit > 0 && entries.size > limit) {
            forget(now, limit)
        }
    }

    /** Roll old repeats into one line per day and drop what's no longer memorable (keeps at most [limit], never dropping pinned). */
    fun forget(now: Long, limit: Int = maxEntries.coerceAtLeast(1)) {
        rollup(now)
        entries.removeAll { !it.pinned && effective(it, now) < 0.03f }
        if (entries.size > limit) {
            val needDrop = entries.size - limit
            val droppable = entries.filter { !it.pinned }.sortedBy { effective(it, now) }.take(needDrop).toSet()
            entries.removeAll { it in droppable }
        }
    }

    /** Days older than yesterday: same-kind entries of a day become one ROLLUP line ("got poked 7 times"). */
    fun rollup(now: Long) {
        val cutoff = day(now).minusDays(1)
        val old = entries.filter { !it.pinned && day(it.ts).isBefore(cutoff) && it.kind in setOf(Kind.POKED, Kind.PETTED, Kind.SLEPT, Kind.POWER, Kind.EXPLORED) }
        old.groupBy { day(it.ts) to it.kind }.filter { it.value.size > 1 }.forEach { (key, list) ->
            entries.removeAll { it in list }
            val n = list.sumOf { it.count }
            entries += Entry(list.maxOf { it.ts }, Kind.ROLLUP, "${key.second.name}:$n:" + list.joinToString(",") { it.subject }.take(60), n, (list.maxOf { it.salience } + 0.1f).coerceAtMost(1f))
        }
        entries.sortBy { it.ts }
    }

    /**
     * Something worth bringing up now: matching [kinds] / [subject] first, weighted by salience, not recalled in the
     * last day. Null most of the time on purpose.
     */
    fun recall(now: Long, rnd: Random, subject: String? = null, kinds: Set<Kind>? = null, minSalience: Float = 0.2f): Entry? {
        val c = entries.filter {
            now - it.ts > 20 * 60_000L && now - it.recalledAt > 86_400_000L && effective(it, now) >= minSalience &&
                (kinds == null || it.kind in kinds) && (subject == null || it.subject.equals(subject, true))
        }
        if (c.isEmpty()) return null
        val w = c.map { effective(it, now) }
        var r = rnd.nextFloat() * w.sum()
        for ((i, e) in c.withIndex()) { r -= w[i]; if (r <= 0f) return e }
        return c.last()
    }

    fun markRecalled(e: Entry, now: Long) { val i = entries.indexOf(e); if (i >= 0) entries[i] = e.copy(recalledAt = now) }

    fun day(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()

    private fun whenWord(ts: Long, now: Long): String {
        val d = day(ts); val today = day(now)
        return when {
            d == today -> "today"
            d == today.minusDays(1) -> "yesterday"
            d.isAfter(today.minusDays(7)) -> "on ${d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"
            else -> "a while ago"
        }
    }
    private fun clock(ts: Long): String {
        val t = Instant.ofEpochMilli(ts).atZone(zone).toLocalTime()
        return when (t.hour) { in 0..4 -> "at midnight"; in 5..11 -> "in the morning"; in 12..16 -> "in the afternoon"; in 17..20 -> "in the evening"; else -> "late at night" }
    }

    /** A line to say out loud about a memory (template, in character; works fully offline). */
    fun recallLine(e: Entry, characterId: String, now: Long): String {
        val w = whenWord(e.ts, now); val at = clock(e.ts); val s = e.subject
        val line = when (e.kind) {
            Kind.SHAKEN -> "You shook me $w… I'm still a bit dizzy 😵‍💫"
            Kind.POKED -> if (e.count > 3) "You poked me ${e.count} times $w. I counted. 👀" else "Remember poking me $w? I do."
            Kind.PETTED -> "Those head pats $w were nice. Just saying 🥹"
            Kind.EXPLORED -> "Last time you opened $s $at I had a good sniff around 👃"
            Kind.GAME_WON -> "I beat you at $s $w. Rematch? 😏"
            Kind.GAME_LOST -> "You won at $s $w… I've been practising 🙃"
            Kind.FESTIVAL -> "Still thinking about $s celebrations 🪔"
            Kind.USER_AWAY -> "You disappeared $w for ages. I waited. Dramatically."
            Kind.CHAT_LINE -> "You said \"${s.take(40)}\" $w. Still funny 😂"
            Kind.CRASH_LANDING -> "Remember my crash landing $w? Let's not."
            Kind.POWER -> "My $s $w was legendary, admit it ⚡"
            Kind.SLEPT -> "I napped at the $s $w. Best spot."
            Kind.LEFT -> "I went for a $s $w. Good times."
            Kind.HABIT -> "It's kind of our thing now: $s."
            Kind.ROLLUP -> rollupLine(e, w)
            Kind.CUSTOM_NOTE -> "I always remember: $s 📌"
        }
        return voice(characterId, line)
    }

    private fun rollupLine(e: Entry, w: String): String {
        val p = e.subject.split(':')
        val n = p.getOrNull(1) ?: "${e.count}"
        return when (p.getOrNull(0)) {
            "POKED" -> "$w you poked me $n times. A personal record?"
            "PETTED" -> "$n head pats $w. I'm keeping score 🥰"
            "SLEPT" -> "I napped $n times $w. Growing creature."
            "POWER" -> "Used my powers $n times $w ⚡"
            else -> "I explored $n apps $w 🧭"
        }
    }

    /** The cute diary page text ("Dear diary…"), newest first (pinned first when viewing full pageEntries). */
    fun page(characterId: String, now: Long, limit: Int = 40): List<Pair<String, String>> =
        entries.sortedByDescending { it.ts }.take(limit).map { e ->
            val head = (if (e.pinned) "📌 " else "") + whenWord(e.ts, now).replaceFirstChar { it.uppercase() } + " " + clock(e.ts)
            head to diaryLine(e, characterId)
        }

    fun pageEntries(characterId: String, now: Long, limit: Int = 50): List<Triple<Entry, String, String>> =
        entries.sortedWith(compareByDescending<Entry> { it.pinned }.thenByDescending { it.ts }).take(limit).map { e ->
            val head = (if (e.pinned) "📌 Pinned · " else "") + whenWord(e.ts, now).replaceFirstChar { it.uppercase() } + " " + clock(e.ts)
            Triple(e, head, diaryLine(e, characterId))
        }

    private fun diaryLine(e: Entry, characterId: String): String {
        val s = e.subject; val x = if (e.count > 1) " (×${e.count})" else ""
        val me = when (characterId) { "superboy" -> "Hero log"; "armour" -> "Suit log"; "mask" -> "Secret mask diary"; else -> "Dear diary" }
        return "$me: " + when (e.kind) {
            Kind.SLEPT -> "napped at the $s$x. Zzz."
            Kind.POKED -> "got poked$x. Rude, but I'm a professional."
            Kind.SHAKEN -> "the whole world shook$x! My stomach is upside down."
            Kind.PETTED -> "head pats$x 🥰 best part of the day."
            Kind.GAME_WON -> "won at $s$x! Champion energy 🏆"
            Kind.GAME_LOST -> "lost at $s$x. Rigged. Definitely rigged."
            Kind.FESTIVAL -> "celebrated $s 🎉"
            Kind.USER_AWAY -> "my human vanished for $s. I guarded the screen."
            Kind.CHAT_LINE -> "funniest thing my human said: \"${s.take(60)}\""
            Kind.CRASH_LANDING -> "crash-landed$x. Nobody saw. (Everybody saw.)"
            Kind.POWER -> "used $s$x ⚡"
            Kind.EXPLORED -> "sniffed around $s$x 👃"
            Kind.LEFT -> "went for a $s. Came back refreshed."
            Kind.HABIT -> "did our ritual: $s."
            Kind.ROLLUP -> rollupLine(e, "that day").replaceFirstChar { it.lowercase() }
            Kind.CUSTOM_NOTE -> "important note from my human: \"$s\" 📌"
        }
    }

    /** A few lines for the chat prompt so conversations remember too. */
    fun promptSnippet(characterId: String, now: Long, n: Int = 5): String =
        entries.sortedByDescending { effective(it, now) }.take(n).joinToString("; ") { "${whenWord(it.ts, now)}: ${diaryLine(it, characterId).substringAfter(": ")}" }

    fun encode(): String {
        val header = "#cfg:$maxEntries,$retentionDays,$logPlaces,$logChats,$logNight,$logWorld"
        val body = entries.joinToString("\n") { e ->
            listOf(
                e.ts.toString(), e.kind.name, e.subject.replace('\t', ' ').replace('\n', ' '),
                e.count.toString(), e.salience.toString(), e.recalledAt.toString(), e.pinned.toString()
            ).joinToString("\t")
        }
        return if (body.isEmpty()) header else "$header\n$body"
    }

    fun decode(s: String?) {
        entries.clear()
        s?.lineSequence()?.forEach { l ->
            if (l.startsWith("#cfg:")) {
                runCatching {
                    val p = l.removePrefix("#cfg:").split(',')
                    maxEntries = p.getOrNull(0)?.toIntOrNull() ?: cap
                    retentionDays = p.getOrNull(1)?.toIntOrNull() ?: 0
                    logPlaces = p.getOrNull(2)?.toBooleanStrictOrNull() ?: true
                    logChats = p.getOrNull(3)?.toBooleanStrictOrNull() ?: true
                    logNight = p.getOrNull(4)?.toBooleanStrictOrNull() ?: true
                    logWorld = p.getOrNull(5)?.toBooleanStrictOrNull() ?: true
                }
            } else {
                runCatching {
                    val p = l.split('\t')
                    val pinned = p.getOrNull(6)?.toBooleanStrictOrNull() ?: false
                    entries += Entry(p[0].toLong(), Kind.valueOf(p[1]), p[2], p[3].toInt(), p[4].toFloat(), p[5].toLong(), pinned)
                }
            }
        }
    }

    companion object {
        fun voice(characterId: String, line: String) = when (characterId) {
            "superboy" -> "Hero memory: $line"
            "armour" -> "Log entry: $line"
            "mask" -> "Psst! $line"
            else -> line
        }
    }
}
