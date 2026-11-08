package com.combo.monkeycompanion.engine.script

import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.engine.action.ActionLibrary
import com.combo.monkeycompanion.engine.expression.Expressions
import com.combo.monkeycompanion.engine.props.Props

/**
 * v10.39.5 "✨ Describe a show": the user types a show in normal words, any brain (Set-Tai Cloud, own key or
 * on-device Gemma) writes a few short STORY LINES, and [compile] turns them into a real show script with timings,
 * positions and endings. Short lines because the cloud reply is small and small models make fewer mistakes;
 * the app does the hard part (times, places, quotes). Pure: no Android.
 *
 * Story lines (one per line):
 * ```
 * title Tutty's prank
 * cast a=devil b=angel
 * a enter left
 * b say Hello!
 * a throw fireball at b
 * b vanish
 * ```
 */
object ShowWriter {
    /** Verbs a story line may use, with what they need. */
    val VERBS = listOf("enter", "exit", "walk", "fly", "say", "face", "do", "throw", "shield", "ray", "vanish", "appear",
        "look", "turn", "wait", "clash", "crack", "shake")

    /** The rule sheet sent as the system prompt. [cast] = (id, name) of characters the user can pick. Kept short. */
    fun rules(cast: List<Pair<String, String>>, maxLines: Int): String = """
You write tiny cartoon shows for the Set-Tai phone app. Answer ONLY with story lines, nothing else, no numbering.
Line 1: title <short title>
Line 2: cast <role>=<id> <role>=<id>   (1 to 3 roles; role = one short lowercase word)
Then at most $maxLines lines, each: <role> <verb> <words>
Verbs:
enter left|right, exit left|right, walk left|middle|right, fly left|middle|right|up,
say <words> (under 8 words), face <face>, do <action>, throw <prop> at <role>, shield, ray <role>,
vanish, appear left|middle|right, look <role>, turn left|right, wait
Stage lines: clash <role> <role>, crack, shake
Faces: ${Expressions.catalog.keys.joinToString(" ")}
Actions: ${ActionLibrary.all.joinToString(" ") { it.id }}
Props: ${Props.catalog.keys.joinToString(" ")}
Character ids: ${cast.joinToString(", ") { "${it.first} (${it.second})" }}
Every role must enter or appear first. Keep it family friendly and funny.
Example:
title Snack thief
cast cat=billa mouse=chittu
cat enter left
mouse enter right
mouse say That laddu is mine!
cat face angry
cat throw laddu at mouse
mouse vanish
cat face shocked
""".trim()

    fun request(description: String) = "Write a show: ${description.trim().take(400)}"

    /** Ask again with what went wrong (one retry). */
    fun fixRequest(description: String, lines: String, problems: List<String>) =
        "Write a show: ${description.trim().take(300)}\nYour last answer had problems: ${problems.take(4).joinToString("; ")}.\n" +
            "Answer again with correct story lines only:\n${lines.take(600)}"

    class Result(val script: String, val problems: List<String>) { val ok get() = problems.isEmpty() }

    private val SPOT = mapOf("left" to 0.22f, "middle" to 0.5f, "center" to 0.5f, "centre" to 0.5f, "right" to 0.78f)
    private const val FLOOR = 0.86f
    private const val SKY = 0.42f

    /**
     * Story lines (or a full script, if a brain wrote one) -> a playable script. Unknown words are skipped or
     * swapped for something close, and listed in [Result.problems] only when the show can't play at all.
     */
    fun compile(answer: String): Result {
        val raw = clean(answer)
        if (raw.lines().any { it.trim().lowercase().startsWith("at ") }) return checkFull(raw)
        var title = "My show"
        val cast = LinkedHashMap<String, String>()
        val out = ArrayList<String>()
        val problems = ArrayList<String>()
        val seen = HashSet<String>()
        val x = HashMap<String, Float>()
        var t = 0f
        fun fmt(v: Float) = String.format(java.util.Locale.US, "%.1f", v)
        fun at(cmds: String, dur: Float) { out.add("at ${fmt(t)}: $cmds"); t += dur }
        fun ensureIn(role: String) {
            if (role in seen) return
            seen.add(role); val side = if (seen.size % 2 == 1) "left" else "right"
            val to = if (side == "left") 0.3f else 0.7f; x[role] = to
            at("$role enter $side to ${fmt(to)} $FLOOR in 1.2", 1.3f)
        }
        for (line0 in raw.lines()) {
            val line = line0.trim().trimStart('-', '*', '•').trim().replace(Regex("^\\d+[.)]\\s*"), "")
            if (line.isEmpty()) continue
            val words = line.split(Regex("\\s+"))
            val head = words[0].lowercase().trimEnd(':')
            when (head) {
                "title" -> { title = line.substringAfter(' ', "").trim().trimStart(':').trim().replace('"', '’').take(40).ifBlank { title }; continue }
                "cast" -> {
                    for (p in line.substringAfter(' ', "").replace(",", " ").split(Regex("\\s+")).filter { '=' in it }) {
                        val (r, id) = p.split('=', limit = 2).map { it.trim().lowercase() }
                        val role = r.filter { it.isLetterOrDigit() }.take(12)
                        val real = CharacterRegistry.byId(id)?.id ?: CharacterRegistry.allWithCustom.firstOrNull { it.name.equals(id, true) }?.id
                        if (role.isEmpty() || role in setOf("both", "all", "stage")) continue
                        if (real == null) { problems.add("unknown character '$id'"); continue }
                        if (cast.size < 3) cast[role] = real
                    }
                    continue
                }
            }
            if (cast.isEmpty()) { problems.add("no cast line"); break }
            when (head) {
                "clash" -> { val a = words.getOrNull(1)?.lowercase(); val b = words.getOrNull(2)?.lowercase()
                    if (a in cast && b in cast) { ensureIn(a!!); ensureIn(b!!); at("$a do cast_hands for 2.4; $b do cast_hands for 2.4; $a ray hands to $b red for 2.4; $b ray hands to $a yellow for 2.4; stage clash $a $b for 2.4", 2.6f) }
                    continue }
                "crack" -> { at("stage crack at clash; stage shake for 0.6; both face shocked", 1.6f); continue }
                "shake" -> { at("stage shake for 0.8", 1.0f); continue }
                "wait" -> { t += 1f; continue }
            }
            val role = head
            if (role !in cast) { problems.add("'$role' is not in the cast"); continue }
            val verb = words.getOrNull(1)?.lowercase() ?: continue
            val rest = words.drop(2)
            val arg = rest.firstOrNull()?.lowercase()
            if (verb != "enter" && verb != "appear") ensureIn(role)
            when (verb) {
                "enter" -> { if (role !in seen) { seen.add(role); val side = if (arg == "right") "right" else "left"
                    val to = if (side == "left") 0.3f else 0.7f; x[role] = to; at("$role enter $side to ${fmt(to)} $FLOOR in 1.2", 1.3f) } }
                "appear" -> { val px = SPOT[arg] ?: x[role] ?: 0.5f; x[role] = px; seen.add(role); at("$role appear at ${fmt(px)} $FLOOR in 0.5", 0.7f) }
                "exit" -> at("$role exit ${if (arg == "right") "right" else "left"} in 1.0", 1.1f)
                "walk", "fly", "move" -> {
                    val up = arg == "up"
                    val px = SPOT[arg] ?: x[role] ?: 0.5f; x[role] = px
                    at("$role ${if (verb == "walk") "walk" else "fly"} to ${fmt(px)} ${if (up || verb == "fly") SKY else FLOOR} in 1.0", 1.1f)
                }
                "say" -> {
                    val text = rest.joinToString(" ").replace('"', '’').trim().take(60)
                    if (text.isNotEmpty()) { val d = (1.2f + text.split(' ').size * 0.3f).coerceIn(1.6f, 3.2f); at("$role say \"$text\" for ${fmt(d)}", d + 0.2f) }
                }
                "face" -> { val f = arg?.let { a -> Expressions.byName(a)?.let { a } } ?: nearest(arg, Expressions.catalog.keys)
                    if (f != null) at("$role face $f", 0.6f) }
                "do" -> { val a = arg?.let { a0 -> ActionLibrary.byId(a0)?.id } ?: nearest(arg, ActionLibrary.all.map { it.id })
                    if (a != null) at("$role do $a for 1.4", 1.5f) }
                "throw" -> {
                    val prop = arg?.let { p -> Props.byId(p)?.let { p } } ?: nearest(arg, Props.catalog.keys) ?: "ball"
                    val target = rest.dropWhile { it.lowercase() != "at" }.getOrNull(1)?.lowercase()?.takeIf { it in cast && it != role }
                        ?: cast.keys.firstOrNull { it != role }
                    if (target != null) { ensureIn(target); at("$role look $target; $role do throw for 1.6; $role throw $prop at $target every 0.35 x3", 1.8f) }
                }
                "shield" -> at("$role hold shield; $role do raise_shield for 1.8; $role face determined", 2.0f).also { out.add("at ${fmt(t)}: $role drop") }
                "ray" -> { val target = arg?.takeIf { it in cast && it != role } ?: cast.keys.firstOrNull { it != role }
                    if (target != null) { ensureIn(target); at("$role ray eyes to $target red for 1.6", 1.8f) } }
                "vanish" -> at("$role vanish in 0.5", 0.8f)
                "look" -> { val target = arg?.takeIf { it in cast }; if (target != null) at("$role look $target", 0.4f) }
                "turn" -> at("$role turn ${if (arg == "left") "left" else "right"}", 0.3f)
                else -> {}
            }
        }
        if (cast.isEmpty() && problems.none { it == "no cast line" }) problems.add("no cast line")
        if (out.isEmpty() && problems.isEmpty()) problems.add("no story lines")
        val script = buildString {
            append("title: $title\n")
            if (cast.isNotEmpty()) append("cast: ").append(cast.entries.joinToString(", ") { "${it.key}=${it.value}" }).append('\n')
            out.forEach { append(it).append('\n') }
            append("end at ${fmt(t + 1.5f)}\n")
        }
        return if (problems.isNotEmpty() && out.isEmpty()) Result(script, problems) else verify(script, emptyList())
    }

    /** A brain wrote a full script: check it with the real parser + lint. */
    private fun checkFull(text: String): Result = verify(text, emptyList())

    private fun verify(script: String, extra: List<String>): Result {
        val problems = ArrayList(extra)
        try {
            val s = ShowScript.parse(script)
            for ((role, id) in s.cast) if (CharacterRegistry.byId(id) == null) problems.add("unknown character '$id' for $role")
            if (s.beats.isEmpty()) problems.add("no story lines")
        } catch (e: ScriptError) { problems.add(ShowHelp.friendly(e)) }
        ShowHelp.lint(script).forEach { problems.add("line ${it.line}: ${it.text}") }
        return Result(script, problems)
    }

    /**
     * When the brain can't write a playable show (twice), pick the closest ready-made story and put in the characters
     * the user named, so they always get something that plays.
     */
    fun fallback(description: String): String {
        val d = description.lowercase()
        val id = when {
            Regex("chase|run|catch|race").containsMatchIn(d) -> "chase"
            Regex("fight|duel|fire|magic|battle|attack|ray|laser").containsMatchIn(d) -> "duel"
            Regex("laddu|throw|food|snack|sweet").containsMatchIn(d) -> "laddu"
            Regex("surprise|boo|scare|hide|prank").containsMatchIn(d) -> "surprise"
            Regex("sorry|upset|sad|angry|friends again").containsMatchIn(d) -> "sorry"
            else -> "hello"
        }
        val t = ShowTemplates.byId(id) ?: ShowTemplates.all.first()
        val named = mentioned(description)
        val values = t.defaults().toMutableMap()
        var i = 0
        for (f in t.fields) if (f.kind == ShowTemplates.Kind.CHARACTER && i < named.size) values[f.key] = named[i++]
        return t.render(values)
    }

    /** Character ids named in [text] (by id or name), in the order they appear. */
    fun mentioned(text: String): List<String> {
        val low = text.lowercase()
        return CharacterRegistry.allWithCustom.mapNotNull { c ->
            val at = listOf(c.name.lowercase(), c.id.lowercase()).map { n -> Regex("\\b${Regex.escape(n)}\\b").find(low)?.range?.first ?: -1 }
                .filter { it >= 0 }.minOrNull()
            at?.let { it to c.id }
        }.sortedBy { it.first }.map { it.second }.distinct()
    }

    /** Strip code fences, quotes around the whole answer and chatter lines. */
    fun clean(answer: String): String = answer.replace("```", "\n").lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("Here") && !it.startsWith("Sure") }
        .joinToString("\n")

    /** A close word from [options] (shared prefix of 3+), or null. */
    private fun nearest(word: String?, options: Collection<String>): String? {
        val w = word?.lowercase() ?: return null
        return options.firstOrNull { it.startsWith(w.take(3)) || w.startsWith(it.take(3)) }
    }
}
