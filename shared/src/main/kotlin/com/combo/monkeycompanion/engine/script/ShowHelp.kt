package com.combo.monkeycompanion.engine.script

import com.combo.monkeycompanion.engine.action.ActionLibrary
import com.combo.monkeycompanion.engine.expression.Expressions
import com.combo.monkeycompanion.engine.props.Props

/**
 * v10.39.1: show scripts for normal people. [friendly] turns a [ScriptError] into plain words with a hint;
 * [lint] finds things the player would silently ignore (a face, action or prop it doesn't know).
 * Pure: no Android.
 */
object ShowHelp {
    val MOVES = listOf("enter", "exit", "walk", "fly", "move", "hover", "vanish", "appear", "say", "face", "do",
        "look", "turn", "hold", "drop", "throw", "ray", "stop", "place")
    val FACES: List<String> get() = Expressions.catalog.keys.toList()
    val ACTIONS: List<String> get() = ActionLibrary.all.map { it.id }
    val PROPS: List<String> get() = Props.catalog.keys.toList()

    /** Plain message for a parse / cast error; keeps "line N" so people can find it. */
    fun friendly(e: ScriptError): String {
        val raw = e.message?.substringAfter(": ").orEmpty()
        val where = if (e.line > 0) "On line ${e.line}: " else ""
        val m = Regex("'([^']*)'").find(raw)?.groupValues?.get(1).orEmpty()
        val hint = when {
            raw.startsWith("unknown role") -> "\"$m\" isn't in the cast. Add it to the cast line, for example: cast: $m=billa"
            raw.startsWith("unknown verb") -> "\"$m\" isn't a move I know. Use one of: ${MOVES.joinToString(", ")}."
            raw.startsWith("unknown stage verb") -> "the stage can only: clash, drop, crack, shake."
            raw.startsWith("bad time") -> "a line must start with a time in seconds, like: at 2.5: billa walk to 0.5 0.86"
            raw.startsWith("bad end time") -> "write the end like: end at 12"
            raw.startsWith("cast wants") -> "the cast line looks like: cast: cat=billa, mouse=chittu"
            raw.startsWith("command needs") -> "each command is \"who what\", for example: chittu say \"Hello!\""
            raw.startsWith("don't understand") -> "every line starts with title:, cast:, at <seconds>:, when …, end at, or # for a note."
            raw.startsWith("no cast") -> "add a cast line first, for example: cast: hero=monkey"
            raw.startsWith("use: when") -> "write it like: when fireball near angel within 0.08: angel vanish"
            raw.contains("reserved") -> "\"both\", \"all\" and \"stage\" are special words; pick another name."
            raw.contains("character", ignoreCase = true) -> "that character id doesn't exist. Use one of your characters' ids (monkey, billa, chittu, gajju, devil, angel…)."
            else -> raw
        }
        return where + hint
    }

    class Problem(val line: Int, val text: String)

    /** Things that parse but would do nothing (unknown face / action / prop, say without quotes). */
    fun lint(text: String): List<Problem> {
        val out = ArrayList<Problem>()
        text.lines().forEachIndexed { i, raw ->
            val n = i + 1
            val line = raw.substringBefore('#').trim()
            if (!line.lowercase().startsWith("at ") && !line.lowercase().startsWith("when ")) return@forEachIndexed
            val body = line.substringAfter(':', "")
            for (c in body.split(';').map { it.trim() }.filter { it.isNotEmpty() }) {
                val t = ShowScript.tokens(c)
                if (t.size < 2) continue
                val verb = t[1].lowercase(); val arg = t.getOrNull(2)?.lowercase()
                when (verb) {
                    "face" -> if (arg != null && Expressions.byName(arg) == null) out.add(Problem(n, "\"$arg\" isn't a face. Faces: ${FACES.joinToString(", ")}."))
                    "do" -> if (arg != null && ActionLibrary.byId(arg) == null) out.add(Problem(n, "\"$arg\" isn't an action. Actions: ${ACTIONS.joinToString(", ")}."))
                    "hold", "throw" -> if (arg != null && Props.byId(arg) == null) out.add(Problem(n, "\"$arg\" isn't a prop. Props: ${PROPS.joinToString(", ")}."))
                    "say" -> if (!c.contains('"')) out.add(Problem(n, "put the words in quotes, like: say \"Hello!\""))
                }
            }
        }
        return out
    }
}

/**
 * v10.39.1 fill-in-the-blanks shows: pick characters and type a few lines, get a working script.
 * Every template only uses moves the player supports, so it always plays.
 */
object ShowTemplates {
    enum class Kind { CHARACTER, TEXT }
    class Field(val key: String, val label: String, val kind: Kind, val default: String)
    class Template(val id: String, val title: String, val blurb: String, val fields: List<Field>, private val body: String) {
        /** The script with every {key} filled in (quotes in typed text become ’ so they can't break the line). */
        fun render(values: Map<String, String>): String {
            var s = body
            for (f in fields) {
                val v = (values[f.key]?.takeIf { it.isNotBlank() } ?: f.default).replace('"', '’').replace("\n", " ").take(80)
                s = s.replace("{${f.key}}", v)
            }
            return s.trimIndent().trim() + "\n"
        }
        fun defaults(): Map<String, String> = fields.associate { it.key to it.default }
    }

    private fun ch(key: String, label: String, def: String) = Field(key, label, Kind.CHARACTER, def)
    private fun tx(key: String, label: String, def: String) = Field(key, label, Kind.TEXT, def)

    val all: List<Template> = listOf(
        Template("hello", "Say hello", "One character walks in, says your words and waves goodbye.",
            listOf(ch("a", "Who", "monkey"), tx("line", "What they say", "Hello! Have a super day! 😄"), tx("bye", "Goodbye line", "See you soon! 👋")), """
            title: Say hello
            cast: a={a}
            at 0: a enter left to 0.5 0.86 in 1.6
            at 1.6: a face happy; a turn right; a say "{line}" for 2.8
            at 4.6: a do point for 1.2; a say "{bye}" for 1.8
            at 6.6: a exit right in 1.2
            end at 8.2
            """),
        Template("chase", "The chase", "One chases, the other runs and disappears at the last second.",
            listOf(ch("chaser", "Who chases", "billa"), ch("runner", "Who runs", "chittu"),
                tx("runline", "Runner shouts", "Help! Can't catch me! 🏃"), tx("chaseline", "Chaser says at the end", "Where did it go?! 😾")), """
            title: The chase
            cast: chaser={chaser}, runner={runner}
            at 0: runner enter left to 0.62 0.86 in 1.2
            at 0.4: chaser enter left to 0.2 0.86 in 1.4
            at 1.8: runner look chaser; runner face fear; runner say "{runline}" for 1.6; chaser face angry; chaser look runner
            at 2.8: runner move to 0.9 0.86 in 1.0; chaser move to 0.68 0.86 in 1.2
            at 4.0: runner move to 0.12 0.86 in 1.4; runner turn left; chaser move to 0.34 0.86 in 1.6; chaser turn left
            at 5.6: runner vanish in 0.5
            at 6.2: chaser stop; chaser face shocked; chaser say "{chaseline}" for 2.2
            at 8.6: runner appear at 0.88 0.86 in 0.5; runner face smug; runner say "Hee hee!" for 1.4
            end at 10.6
            """),
        Template("duel", "Magic duel", "Fireballs, a shield and a big clash that cracks the screen.",
            listOf(ch("attacker", "Who attacks", "devil"), ch("defender", "Who defends", "angel"),
                tx("taunt", "Attacker says", "Take this! 😈"), tx("reply", "Defender says", "Not today! ✨")), """
            title: Magic duel
            cast: attacker={attacker}, defender={defender}
            at 0: defender enter right to 0.7 0.45 in 1.8
            at 0.6: attacker enter left to 0.25 0.6 in 1.4
            at 2.0: attacker hover; defender hover; attacker look defender; defender look attacker; attacker face evil
            at 2.6: attacker say "{taunt}" for 1.6; attacker do throw for 2.0; attacker throw fireball at defender every 0.3 x6 speed 1.4
            at 2.8: defender hold shield; defender do raise_shield for 2.2; defender face determined
            at 5.2: defender drop; defender say "{reply}" for 1.6
            at 6.0: attacker do cast_hands for 3.0; attacker ray hands to defender red for 3.0
            at 6.2: defender do cast_hands for 2.8; defender ray hands to attacker yellow for 2.8
            at 6.3: stage clash attacker defender for 2.8
            at 8.6: stage drop clash in 0.8
            at 9.4: stage crack at clash; stage shake for 0.6; both stop; both face shocked; both look crack
            end at 12
            """),
        Template("laddu", "Laddu fight", "Two friends throw laddus at each other.",
            listOf(ch("a", "First friend", "gajju"), ch("b", "Second friend", "rocketgirl"), tx("line", "Who started it says", "You started it! 😆")), """
            title: Laddu fight
            cast: a={a}, b={b}
            at 0: a enter left to 0.22 0.86 in 1.4
            at 0.3: b enter right to 0.78 0.86 in 1.4
            at 1.8: a look b; b look a; a face smug
            at 2.2: a do throw for 1.4; a throw laddu at b every 0.35 x3
            when laddu near b: b face shocked; b say "Hey!" for 1.2
            at 4.2: b face angry; b do throw for 1.6; b throw ball at a every 0.3 x4
            when ball near a: a face shocked; a say "{line}" for 1.8
            at 7.2: both face happy; both say "Ha ha ha!" for 2.0
            end at 9.8
            """),
        Template("surprise", "Surprise!", "One hides, the other walks in, then BOO!",
            listOf(ch("hider", "Who hides", "chittu"), ch("walker", "Who walks in", "billa"), tx("boo", "The surprise shout", "BOO! 👻")), """
            title: Surprise!
            cast: hider={hider}, walker={walker}
            at 0: walker enter left to 0.4 0.86 in 2.0
            at 2.0: walker face curious; walker say "Hello? Anyone here?" for 2.0
            at 4.2: hider appear at 0.62 0.86 in 0.3; hider turn left; hider face evil; hider say "{boo}" for 1.4
            at 4.4: walker face scream; walker do cower for 1.4
            at 6.0: walker face angry; walker say "Not funny!" for 1.4
            at 7.6: both face happy; hider say "Ok, it was a bit funny 😄" for 2.0
            end at 10
            """),
        Template("sorry", "Sorry, friend", "One is upset, the other says sorry and they make up.",
            listOf(ch("upset", "Who is upset", "gajju"), ch("friend", "Who says sorry", "monkey"), tx("sorry", "The sorry line", "I'm sorry I ate your snack 🙏")), """
            title: Sorry, friend
            cast: upset={upset}, friend={friend}
            at 0: upset enter left to 0.3 0.86 in 1.4; upset face sad; upset turn left
            at 1.6: friend enter right to 0.66 0.86 in 1.6
            at 3.4: friend look upset; friend face sorry; friend say "{sorry}" for 2.6
            at 6.2: upset turn right; upset look friend; upset face curious
            at 7.2: upset face happy; upset say "Okay, friends again! 💛" for 2.2; friend face happy
            end at 10
            """),
    )

    fun byId(id: String): Template? = all.firstOrNull { it.id == id }
}
