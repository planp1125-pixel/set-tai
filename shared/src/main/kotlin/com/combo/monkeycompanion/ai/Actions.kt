package com.combo.monkeycompanion.ai

/** Things Settai's body can do when the AI (or the user) asks. */
enum class BodyAction(val tag: String, vararg val words: String) {
    DANCE("dance", "dance", "dances", "dancing", "danced", "boogie", "naach", "naachu", "aadu", "aatam", "aattam", "kuthu"),
    JUMP("jump", "jump", "jumps", "jumping", "hop", "hops", "bounce", "bounces"),
    WAVE("wave", "wave", "waves", "waving", "hi-five", "high five", "highfive"),
    GIGGLE("giggle", "giggle", "giggles", "giggling", "laugh", "laughs", "laughing", "chuckle", "chuckles", "hehe"),
    ANGRY("angry", "angry", "grumpy", "huff", "huffs", "stomp", "stomps", "mad"),
    SLEEP("sleep", "sleep", "sleeps", "sleeping", "nap", "naps", "napping", "snore", "snores", "yawn", "yawns", "thoongu"),
    CLIMB("climb", "climb", "climbs", "climbing", "hang", "hangs", "hanging", "swing", "swings"),
    HIDE("hide", "hide", "hides", "hiding", "duck", "ducks", "peekaboo", "peek-a-boo"),
    SPIN("spin", "spin", "spins", "spinning", "twirl", "twirls", "twirling", "cartwheel"),
    THINK("think", "think", "thinks", "thinking", "ponder", "ponders", "hmm", "scratches head"),
    HAPPY("happy", "happy", "smile", "smiles", "smiling", "grin", "grins", "yay", "cheer", "cheers", "celebrate", "celebrates"),
    SAD("sad", "sad", "sigh", "sighs", "cry", "cries", "sob", "sobs", "pout", "pouts"),
    // v3.2 gags (names match brain.Gag)
    SNEEZE("sneeze", "sneeze", "sneezes", "sneezing", "achoo"),
    SLIP("slip", "slip", "slips", "slipping", "banana peel"),
    BINOCULARS("binoculars", "binoculars", "binocular"),
    FAKE_SLEEP("fakesleep", "fakesleep", "fake-sleep", "fake sleep", "one eye open", "pretends to sleep"),
    JUGGLE("juggle", "juggle", "juggles", "juggling"),
    TAIL_HIDE("tailhide", "tailhide", "tail-hide", "tail hide", "hides behind tail", "hides behind my tail"),
    CHASE_FLY("chasefly", "chasefly", "chase-fly", "chase fly", "chases a fly", "chasing a fly"),
    AIR_GUITAR("airguitar", "airguitar", "air-guitar", "air guitar", "shreds", "rocks out"),
    SELFIE("selfie", "selfie", "selfies", "takes a selfie"),
    // v4.1 wardrobe actions (only when the character has that item on)
    SKATE("skate", "skate", "skates", "skating", "skateboard"),
    CHICKEN("chicken", "chicken", "rubber chicken", "squeak", "squeaks"),
    GADGET("gadget", "gadget", "gadgets", "utility belt"),
    // v3.2 superpowers ([fly] / [power]): flies around the screen with the chosen power
    FLY("fly", "fly", "flies", "flying", "takes off", "power", "powers", "superpower", "superpowers", "super power",
        "use power", "show power", "power up", "powers up", "rocket monkey", "super banana");

    /** The matching body gag, if this action is one. */
    val gag: com.combo.monkeycompanion.brain.Gag? get() = com.combo.monkeycompanion.brain.Gag.entries.firstOrNull { it.name == name }

    companion object {
        private val byWord: Map<String, BodyAction> by lazy { HashMap<String, BodyAction>().also { m -> BodyAction.entries.forEach { a -> a.words.forEach { w -> m[w] = a } } } }

        fun fromWord(w: String): BodyAction? = byWord[w.trim().lowercase().trimEnd('!', '.', ',')]
        /** User asks for a superpower: "fly", "can you fly?", "show your power", "use your superpower"... */
        /** v4: a show-off power asked for by name ("ninja mode", "do a magic trick", "kovam mode"). */
        val POWER_NAMED = Regex("""\b((thunder|lightning) (monkey|power|mode|strike)|ninja( mode| settai| power| clones?)?|shadow clones?|kovam mode|ice monkey|freeze (the|my) screen|giant mode|time freeze|matrix (dodge|mode)|slow ?mo(tion)? (mode|dodge)|magic trick|magician|do (some |a )?magic|pull a rabbit|heat ?vision|laser eyes|super ?breath|super ?strength|super ?speed|speed blur|confetti( rockets?)?|shield bubble|force ?field|armou?r jets)\b""")
        val POWER_ASK = Regex("""\b(fly|flying|superpowers?|super ?powers?|(show|use)( me)? (your|ur|ya) (super ?)?powers?|power ?up|rocket monkey|super banana)\b""")
    }
}

/** Reply text with action tags removed, plus the actions found (in order, de-duplicated). */
data class ParsedReply(val text: String, val actions: List<BodyAction>)

/**
 * Action protocol shared by every brain (works with small offline models too):
 * inline tags like `[dance]`, `*dances*`, `(spin)`, `<wave>`, `[action: jump]`, `[DANCE!]`.
 * Tags are removed from what the user sees; unknown bracketed words are left alone
 * (except `*...*` stage directions, which are always removed).
 */
object ActionParser {
    // [x] (x) <x> {x}: one to three words, optionally "action:" / "does a" prefix
    private val bracket = Regex("""[\[(<{]\s*(?:action\s*[:=]\s*|does\s+(?:a\s+)?)?([a-zA-Z][a-zA-Z\- ]{1,24}?)\s*[!.]*\s*[\])>}]""")
    // *dances* / *does a little dance* / _waves_
    private val stars = Regex("""(?<![*\w])[*_]{1,2}([^*_\n]{1,40}?)[*_]{1,2}(?![*\w])""")
    // an unfinished tag at the very end while streaming: "Sure! [dan"
    private val partialTail = Regex("""[\[(<{*][^\])>}*\n]{0,26}$""")

    fun parse(raw: String): ParsedReply {
        val found = LinkedHashSet<BodyAction>()
        var s = bracket.replace(raw) { m ->
            val a = actionIn(m.groupValues[1])
            if (a != null) { found.add(a); "" } else m.value
        }
        s = stars.replace(s) { m ->
            val a = actionIn(m.groupValues[1])
            when {
                a != null -> { found.add(a); "" }
                m.groupValues[1].trim().contains(' ') -> ""   // other stage directions (*adjusts hat*) are hidden
                else -> m.groupValues[1]                      // plain *emphasis* keeps its word
            }
        }
        return ParsedReply(tidy(s), found.toList())
    }

    /** For streaming display: strip complete tags and a half-typed one at the end. */
    fun visible(partial: String): String {
        val p = parse(partial).text
        val m = partialTail.find(p) ?: return p
        // only hide it if it looks like the start of a tag, not e.g. a smiley ":("
        val frag = m.value
        return if (frag.length >= 2 && frag[1].isLetter() && frag.drop(1).all { it.isLetter() || it == ' ' || it == ':' || it == '-' }) tidy(p.removeRange(m.range)) else p
    }

    private fun actionIn(phrase: String): BodyAction? {
        val words = phrase.lowercase().replace(Regex("[^a-z\\- ]"), " ").split(' ').filter { it.isNotBlank() }
        if (words.isEmpty() || words.size > 5) return null
        BodyAction.fromWord(words.joinToString(" "))?.let { return it }
        for (w in words) {
            if (w == "power" || w == "powers") continue     // "(power bank)" isn't a flight; only [power] on its own is
            BodyAction.fromWord(w)?.let { return it }
        }
        return null
    }

    private fun tidy(s: String): String = s
        .replace(Regex("[ \\t]{2,}"), " ")
        .replace(Regex(" +([,.!?])"), "$1")
        .replace(Regex("^[ ,]+"), "")
        .trim()

    /**
     * Fallback when the reply has no tag: did the *user* ask for a body action?
     * Requests like "dance", "can you dance", "naach", "aadu da", "go hide", "do a spin".
     */
    fun requestedByUser(userText: String): BodyAction? {
        val t = userText.lowercase()
        val words = t.split(Regex("[^a-z\\-]+")).filter { it.isNotBlank() }
        // Questions about the topic ("what is a dance?") still count: Settai loves to show off.
        val order = listOf(BodyAction.DANCE, BodyAction.SPIN, BodyAction.JUMP, BodyAction.WAVE, BodyAction.HIDE,
            BodyAction.SLEEP, BodyAction.CLIMB, BodyAction.GIGGLE)
        for (a in order) if (words.any { a.words.contains(it) }) return a
        if (t.contains("high five") || t.contains("hi five")) return BodyAction.WAVE
        if (t.contains("air guitar")) return BodyAction.AIR_GUITAR
        if (t.contains("banana peel")) return BodyAction.SLIP
        if (t.contains("chase") && t.contains("fly")) return BodyAction.CHASE_FLY
        if (t.contains("fake sleep") || t.contains("pretend to sleep")) return BodyAction.FAKE_SLEEP
        if (t.contains("rubber chicken") || Regex("\\bsqueak").containsMatchIn(t)) return BodyAction.CHICKEN
        if (BodyAction.POWER_NAMED.containsMatchIn(t) || BodyAction.POWER_ASK.containsMatchIn(t)) {
            com.combo.monkeycompanion.brain.Superpowers.requested = com.combo.monkeycompanion.brain.Superpowers.named(t)
            return BodyAction.FLY
        }
        for (a in listOf(BodyAction.JUGGLE, BodyAction.SELFIE, BodyAction.SNEEZE, BodyAction.BINOCULARS, BodyAction.SKATE, BodyAction.GADGET))
            if (words.any { a.words.contains(it) }) return a
        return null
    }
}
