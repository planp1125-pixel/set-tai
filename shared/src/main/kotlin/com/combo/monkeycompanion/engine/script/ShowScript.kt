package com.combo.monkeycompanion.engine.script

/**
 * v10.32 plain-text show scripts (see docs/LIBRARIES-AND-SHOWS.md and app/src/main/assets/shows/).
 *
 * ```
 * title: Satan vs Angel
 * cast: angel=angel, satan=devil          # role=character id (any built-in or studio id)
 * at 0.0: angel enter left to 0.62 0.36 in 2.5
 * at 2.5: angel hover; angel face sleepy
 * when fireball near angel within 0.08: angel vanish
 * end at 22
 * ```
 * A line is `at <seconds>: <cmd>; <cmd>…`, `when <prop> near <role> [within <r>]: <cmd>; …` (fires once, the
 * first time a flying <prop> comes within r screen widths of <role>), `title:`, `cast:`, `end at <s>`, or `#` comment.
 * A command is `<subject> <verb> <args…>`. Subject = a cast role, `both`/`all` (every role), or `stage`.
 * Parsing is pure and throws [ScriptError] with the line number for anything it does not understand.
 */
class ScriptError(val line: Int, msg: String) : IllegalArgumentException("line $line: $msg")

class Cmd(val line: Int, val subject: String, val verb: String, val args: List<String>) {
    override fun toString() = "$subject $verb ${args.joinToString(" ")}"
}
class Beat(val at: Float, val cmds: List<Cmd>)
class Rule(val prop: String, val target: String, val within: Float, val cmds: List<Cmd>)

class ShowScript(val title: String, val cast: Map<String, String>, val beats: List<Beat>, val rules: List<Rule>, val end: Float) {
    companion object {
        /** Verbs a role understands (the player implements each one for ANY character). */
        val ACTOR_VERBS = setOf("enter", "exit", "move", "fly", "walk", "hover", "vanish", "appear", "face", "do", "stop",
            "look", "turn", "say", "hold", "drop", "throw", "ray", "place")
        val STAGE_VERBS = setOf("clash", "drop", "crack", "shake")

        fun parse(text: String): ShowScript {
            var title = "Show"; val cast = LinkedHashMap<String, String>(); val beats = ArrayList<Beat>(); val rules = ArrayList<Rule>()
            var end = -1f
            text.lines().forEachIndexed { i, raw ->
                val n = i + 1
                val line = stripComment(raw).trim()
                if (line.isEmpty()) return@forEachIndexed
                val low = line.lowercase()
                when {
                    low.startsWith("title:") -> title = line.substringAfter(':').trim()
                    low.startsWith("cast:") -> line.substringAfter(':').split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { pair ->
                        val (r, id) = pair.split('=').map { it.trim().lowercase() }.let { if (it.size != 2 || it[0].isEmpty() || it[1].isEmpty()) throw ScriptError(n, "cast wants role=characterId, got '$pair'") else it[0] to it[1] }
                        if (r in setOf("both", "all", "stage")) throw ScriptError(n, "'$r' is reserved")
                        cast[r] = id
                    }
                    low.startsWith("end at ") -> end = low.removePrefix("end at ").trim().toFloatOrNull() ?: throw ScriptError(n, "bad end time")
                    low.startsWith("at ") -> {
                        val head = line.substringBefore(':'); val body = line.substringAfter(':', "")
                        val t = head.removePrefix("at ").removePrefix("At ").trim().toFloatOrNull() ?: throw ScriptError(n, "bad time '$head'")
                        beats.add(Beat(t, cmds(n, body, cast)))
                    }
                    low.startsWith("when ") -> {
                        val head = line.substringBefore(':').trim().split(Regex("\\s+")); val body = line.substringAfter(':', "")
                        // when <prop> near <role> [within <r>]
                        if (head.size < 4 || head[2].lowercase() != "near") throw ScriptError(n, "use: when <prop> near <role> [within <r>]: …")
                        val prop = head[1].lowercase(); val target = head[3].lowercase()
                        if (target !in cast) throw ScriptError(n, "unknown role '$target'")
                        val within = if (head.size >= 6 && head[4].lowercase() == "within") head[5].toFloatOrNull() ?: throw ScriptError(n, "bad within") else 0.08f
                        rules.add(Rule(prop, target, within, cmds(n, body, cast)))
                    }
                    else -> throw ScriptError(n, "don't understand '$line'")
                }
            }
            if (cast.isEmpty()) throw ScriptError(0, "no cast")
            beats.sortBy { it.at }
            val last = beats.maxOfOrNull { it.at } ?: 0f
            return ShowScript(title, cast, beats, rules, if (end > 0f) end else last + 3f)
        }

        private fun stripComment(s: String): String {
            var q = false
            for (i in s.indices) { if (s[i] == '"') q = !q; if (s[i] == '#' && !q) return s.substring(0, i) }
            return s
        }

        private fun cmds(n: Int, body: String, cast: Map<String, String>): List<Cmd> = splitCmds(body).map { c ->
            val toks = tokens(c)
            if (toks.size < 2) throw ScriptError(n, "command needs '<who> <verb>': '$c'")
            val who = toks[0].lowercase(); val verb = toks[1].lowercase()
            when {
                who == "stage" -> if (verb !in STAGE_VERBS) throw ScriptError(n, "unknown stage verb '$verb'")
                who in cast || who == "both" || who == "all" -> if (verb !in ACTOR_VERBS) throw ScriptError(n, "unknown verb '$verb'")
                else -> throw ScriptError(n, "unknown role '$who'")
            }
            Cmd(n, who, verb, toks.drop(2))
        }

        private fun splitCmds(s: String): List<String> {
            val out = ArrayList<String>(); val b = StringBuilder(); var q = false
            for (ch in s) { if (ch == '"') q = !q; if (ch == ';' && !q) { out.add(b.toString()); b.clear() } else b.append(ch) }
            out.add(b.toString())
            return out.map { it.trim() }.filter { it.isNotEmpty() }
        }

        /** Words, with "quoted text" kept whole (quotes removed). */
        fun tokens(s: String): List<String> {
            val out = ArrayList<String>(); val b = StringBuilder(); var q = false
            for (ch in s) {
                when {
                    ch == '"' -> { if (q) { out.add(b.toString()); b.clear() }; q = !q }
                    ch.isWhitespace() && !q -> { if (b.isNotEmpty()) { out.add(b.toString()); b.clear() } }
                    else -> b.append(ch)
                }
            }
            if (b.isNotEmpty()) out.add(b.toString())
            return out
        }
    }
}
