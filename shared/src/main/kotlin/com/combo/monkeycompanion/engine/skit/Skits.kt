package com.combo.monkeycompanion.engine.skit

import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.world.Population
import kotlin.random.Random

/**
 * v10.12 Tom-and-Jerry style skit engine (pure Kotlin, unit-tested).
 * Skits are data: a cast of roles plus timed beats. The [SkitConductor] uses its OWN seeded Random,
 * never the frame-loop RNG, so golden traces stay identical.
 */
enum class Move { STAY, TOWARD, AWAY, HOP }

/** One beat: actor index in the cast does [state] and says [line] ({0},{1},{2} = cast names). */
data class Beat(val actor: Int, val state: MonkeyState, val line: String, val dur: Float, val prop: String? = null, val move: Move = Move.STAY)

/** A role: allowed ids (empty = anyone). */
data class Role(val ids: Set<String> = emptySet()) { fun fits(id: String) = ids.isEmpty() || id in ids }

data class Ending(val weight: Float, val beats: List<Beat>)

data class Skit(val id: String, val title: String, val cast: List<Role>, val beats: List<Beat>, val endings: List<Ending>, val weight: Float = 1f)

/** A cue sent to one window: "you, do this now". */
data class SkitCue(val seq: Int, val skitId: String, val actorId: String, val targetId: String?, val state: MonkeyState, val line: String, val durSec: Float, val prop: String?, val move: Move, val at: Float)

object SkitLibrary {
    private val ANY = Role()
    private fun r(vararg ids: String) = Role(ids.toSet())

    val all: List<Skit> = listOf(
        Skit("cheese_heist", "Cheese Heist", listOf(r("billa"), r("chittu")),
            listOf(
                Beat(1, MonkeyState.PEEK, "Psst… a cheese wedge! Tiptoe, tiptoe 🧀", 2.2f, "🧀"),
                Beat(1, MonkeyState.WALK, "*grab* Mine now! 🐭", 1.6f, "🧀", Move.AWAY),
                Beat(0, MonkeyState.ANGRY, "CHITTUUU! Drop my cheese! 😾", 1.8f, null, Move.TOWARD),
                Beat(0, MonkeyState.WALK, "Got you… almost… *whoosh* missed!", 2.0f, null, Move.TOWARD),
            ),
            listOf(
                Ending(0.55f, listOf(Beat(1, MonkeyState.GIGGLE, "Watch out for the tawa! 🍳", 1.4f, "🍳"), Beat(0, MonkeyState.STARTLED, "*BOING* …pancake cat 🥞💫", 2.2f, "💫"))),
                Ending(0.30f, listOf(Beat(1, MonkeyState.GIGGLE, "Cheese on a string! Hehe 🧵", 1.6f, "🧀"), Beat(0, MonkeyState.ANGRY, "Fooled AGAIN?! 😤", 1.8f))),
                Ending(0.15f, listOf(Beat(0, MonkeyState.SIT, "Okay okay… half each? 🤝", 1.8f, "🧀"), Beat(1, MonkeyState.HAPPY_PAT, "Truce! Nom nom 😋", 1.8f))),
            )),
        Skit("dosa_pan", "Dosa Pan Ambush", listOf(r("billa"), r("chittu")),
            listOf(
                Beat(0, MonkeyState.PEEK, "Hehe… hiding in the corner with my dosa pan 🍳", 2.2f, "🍳"),
                Beat(1, MonkeyState.WALK, "La la la… just walking by 🎵", 1.8f, null, Move.TOWARD),
                Beat(0, MonkeyState.ANGRY, "NOW! *swiiing*", 1.2f, "🍳", Move.TOWARD),
            ),
            listOf(
                Ending(0.7f, listOf(Beat(0, MonkeyState.STARTLED, "*BONK* …the pan came back 😵💫", 2.2f, "💫"), Beat(1, MonkeyState.GIGGLE, "Hahaha! Nice swing, {0}! 🐭", 1.8f))),
                Ending(0.3f, listOf(Beat(1, MonkeyState.PLAYFUL, "*ducks* Too slow! 🐭💨", 1.6f, null, Move.HOP), Beat(0, MonkeyState.STARTLED, "Ouch, my paw! 🙀", 1.8f))),
            )),
        Skit("banana_heist", "Banana Heist", listOf(r("monkey"), ANY),
            listOf(
                Beat(1, MonkeyState.HAPPY_PAT, "Ahh, my lovely banana 🍌", 2.0f, "🍌"),
                Beat(0, MonkeyState.PEEK, "*sneaks closer* Shhh… 🙈", 1.8f, null, Move.TOWARD),
                Beat(0, MonkeyState.GIGGLE, "*SNATCH* Banana is mine! 🐒🍌", 1.4f, "🍌", Move.AWAY),
                Beat(1, MonkeyState.ANGRY, "Settai! Come back here! 😤", 1.8f, null, Move.TOWARD),
            ),
            listOf(
                Ending(0.5f, listOf(Beat(0, MonkeyState.STARTLED, "*slips on own peel* WAAAH 🍌💫", 2.0f, "💫"), Beat(1, MonkeyState.GIGGLE, "Ha! Karma, monkey 😂", 1.6f))),
                Ending(0.3f, listOf(Beat(1, MonkeyState.STARTLED, "*trips over the peel* Oof! 😵", 1.8f, "💫"), Beat(0, MonkeyState.DANCE, "Juggling 3 bananas, thank you! 🍌🍌🍌", 2.0f))),
                Ending(0.2f, listOf(Beat(0, MonkeyState.HAPPY_PAT, "Fine… half for you, {1} 🍌🤝", 1.8f), Beat(1, MonkeyState.HAPPY_PAT, "Best neighbour ever 💛", 1.6f))),
            )),
        Skit("gajju_shower", "Gajju Shower", listOf(r("gajju"), ANY),
            listOf(
                Beat(0, MonkeyState.PLAYFUL, "*slurrrp* cheeks full of water 🐘💧", 2.0f, "💧"),
                Beat(1, MonkeyState.STARTLED, "Uh oh… {0}, don't you dare! 😨", 1.6f, null, Move.AWAY),
                Beat(0, MonkeyState.GIGGLE, "*PFFFT* missed… *PFFFT* missed again!", 2.0f, "💦"),
            ),
            listOf(
                Ending(0.6f, listOf(Beat(1, MonkeyState.STARTLED, "*SPLASH* …I'm soaked 🥶💦", 2.0f, "💦"), Beat(0, MonkeyState.DANCE, "Bath time! Hehe 🐘", 1.6f))),
                Ending(0.4f, listOf(Beat(1, MonkeyState.GIGGLE, "*tickles the trunk* Kitchi kitchi!", 1.6f, null, Move.TOWARD), Beat(0, MonkeyState.STARTLED, "Ah… ahh… ACHOO! *splashes himself* 💦😅", 2.2f, "💦"))),
            )),
        Skit("firework_prank", "Firework Prank", listOf(r("rocketgirl"), ANY),
            listOf(
                Beat(0, MonkeyState.PEEK, "*sneaks a mini rocket behind {1}* 🤫🚀", 2.0f, "🚀", Move.TOWARD),
                Beat(0, MonkeyState.GIGGLE, "3… 2… 1… *fsssst* 🔥", 1.6f, "🎇"),
            ),
            listOf(
                Ending(0.6f, listOf(Beat(1, MonkeyState.STARTLED, "WAAAAH! I'm flyyyying! 🚀😱", 2.0f, "🚀", Move.HOP), Beat(1, MonkeyState.ANGRY, "*lands in a pile* …{0}!! 😤💫", 1.8f, "💫"))),
                Ending(0.4f, listOf(Beat(1, MonkeyState.GIGGLE, "*turns the rocket around* Your turn! 😏", 1.6f, "🚀"), Beat(0, MonkeyState.STARTLED, "Nooo, not me! 🎆😵", 2.0f, "🎆", Move.AWAY))),
            )),
        Skit("hero_showdown", "Hero Showdown", listOf(r("superboy", "mask"), ANY),
            listOf(
                Beat(1, MonkeyState.DANCE, "Look at me! I'm the star today ✨", 1.8f, "✨"),
                Beat(0, MonkeyState.ANGRY, "Hey! That's MY spotlight! 🦸", 1.6f, null, Move.TOWARD),
                Beat(1, MonkeyState.GIGGLE, "*pulls a pigeon from a hat* 🎩🕊️", 1.6f, "🕊️"),
            ),
            listOf(
                Ending(0.7f, listOf(Beat(0, MonkeyState.STARTLED, "*face full of pigeon* Mmmf! *crash* 💫", 2.0f, "💫"), Beat(1, MonkeyState.WAVE, "*takes a bow* Thank you, thank you 🎭", 1.6f))),
                Ending(0.3f, listOf(Beat(0, MonkeyState.HAPPY_PAT, "…okay, that was a good trick 😅", 1.6f), Beat(1, MonkeyState.HAPPY_PAT, "Team show next time! 🤝", 1.6f))),
            )),
        Skit("laddu_race", "Laddu Race", listOf(ANY, ANY),
            listOf(
                Beat(0, MonkeyState.STARTLED, "A laddu fell from the top! 🟠", 1.6f, "🟠"),
                Beat(1, MonkeyState.WALK, "It's MINE! *runs* 💨", 1.4f, null, Move.TOWARD),
                Beat(0, MonkeyState.WALK, "No, mine! *runs faster* 💨", 1.4f, null, Move.TOWARD),
            ),
            listOf(
                Ending(0.6f, listOf(Beat(0, MonkeyState.STARTLED, "*BONK* heads crash 💫", 1.8f, "💫"), Beat(1, MonkeyState.STARTLED, "Ow ow… where did the laddu go? 😵", 1.8f))),
                Ending(0.4f, listOf(Beat(1, MonkeyState.HAPPY_PAT, "*grabs it* Sharing is caring? 🟠", 1.6f, "🟠"), Beat(0, MonkeyState.HAPPY_PAT, "Half-half! 😋", 1.4f))),
            ), weight = 0.6f),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}

/** One playing skit: cues come out in time order as [step] advances. */
class SkitRun(val skit: Skit, val cast: List<String>, val cues: List<SkitCue>) {
    var t = 0f; private set
    private var next = 0
    val totalSec: Float = cues.lastOrNull()?.let { it.at + it.durSec } ?: 0f
    val done get() = next >= cues.size && t >= totalSec
    fun step(dt: Float): List<SkitCue> {
        t += dt
        val out = ArrayList<SkitCue>()
        while (next < cues.size && cues[next].at <= t) out += cues[next++]
        return out
    }
}

class SkitConductor(seed: Long = 1012L) {
    /** Separate RNG: never shares the frame-loop RNG. */
    val rnd = Random(seed)
    private val recent = ArrayDeque<String>()
    private var seq = 0

    /** Fill every role from [crew] (on-screen ids only), or null. */
    fun cast(skit: Skit, crew: List<String>): List<String>? {
        val out = ArrayList<String>()
        fun fill(i: Int): Boolean {
            if (i == skit.cast.size) return true
            for (id in crew) if (id !in out && skit.cast[i].fits(id)) { out += id; if (fill(i + 1)) return true; out.removeAt(out.size - 1) }
            return false
        }
        return if (fill(0)) out.toList() else null
    }

    fun eligible(crew: List<String>): List<Skit> = if (crew.distinct().size < 2) emptyList() else SkitLibrary.all.filter { cast(it, crew) != null }

    fun start(crew: List<String>, forceId: String? = null): SkitRun? {
        val ok = eligible(crew)
        if (ok.isEmpty()) return null
        val pool = forceId?.let { f -> ok.filter { it.id == f } }?.ifEmpty { null }
            ?: run { val block = recent.toList().takeLast(minOf(4, ok.size - 1)); ok.filter { it.id !in block }.ifEmpty { ok } }
        // Specific (named-cast) skits beat the generic one.
        val w = pool.map { s -> s.weight * (if (s.cast.any { it.ids.isNotEmpty() }) 2f else 1f) }
        var r = rnd.nextFloat() * w.sum()
        var skit = pool.last()
        for (i in pool.indices) { r -= w[i]; if (r <= 0f) { skit = pool[i]; break } }
        recent.addLast(skit.id); while (recent.size > 4) recent.removeFirst()
        val cast = cast(skit, crew)!!
        val ew = skit.endings.sumOf { it.weight.toDouble() }.toFloat()
        var er = rnd.nextFloat() * ew
        var ending = skit.endings.last()
        for (e in skit.endings) { er -= e.weight; if (er <= 0f) { ending = e; break } }
        var at = 0f
        val names = cast.map { Population.displayName(it) }
        val cues = (skit.beats + ending.beats).map { b ->
            val jitter = 0.85f + rnd.nextFloat() * 0.3f
            val d = b.dur * jitter
            var line = b.line
            names.forEachIndexed { i, n -> line = line.replace("{$i}", n) }
            val target = cast.getOrNull(if (b.actor == 0) 1 else 0)
            SkitCue(++seq, skit.id, cast[b.actor], target, b.state, line, d, b.prop, b.move, at).also { at += d }
        }
        return SkitRun(skit, cast, cues)
    }
}

/** Same-process bus: each overlay window listens for cues addressed to its character. */
object SkitBus {
    private val listeners = LinkedHashMap<String, (SkitCue) -> Unit>()
    @Synchronized fun register(id: String, l: (SkitCue) -> Unit) { listeners[id] = l }
    @Synchronized fun unregister(id: String) { listeners.remove(id) }
    @Synchronized fun has(id: String) = id in listeners
    fun post(c: SkitCue): Boolean { val l = synchronized(this) { listeners[c.actorId] } ?: return false; l(c); return true }
}
