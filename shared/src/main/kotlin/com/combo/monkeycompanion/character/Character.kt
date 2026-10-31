package com.combo.monkeycompanion.character

import com.combo.monkeycompanion.ai.BodyAction
import com.combo.monkeycompanion.brain.Gag
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.Superpower
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.Animations
import com.combo.monkeycompanion.rig.MonkeyRenderer
import com.combo.monkeycompanion.rig.Pose

/**
 * v4.1 swappable characters ("skins"). The shared brain, powers, chat, memory, routines, battery,
 * auto-rest, edge-hide, quit and wallpaper stay the same; a [Character] brings its own drawing,
 * pose adapter (how shared actions look on this body), locomotion, superpowers, personality
 * prompt, canned lines, name and an optional [wardrobe] of toggleable parts.
 *
 * All characters share the [com.combo.monkeycompanion.rig.MonkeyRig] biped skeleton (hips, two legs,
 * two-part arms, head); each draws its own body on it.
 */
enum class Locomotion { WALK, CLIMB, HANG, FLY, FLOAT }

/** Display names in one place so they can be renamed before any public release. */
object CharacterNames {
    const val MONKEY = "Settai"
    const val SUPERBOY = "Superboy"          // working name (could become "Super Payyan")
    const val ARMOUR = "Armour Anna"
    const val MASK = "Mask Man"
    const val BILLA = "Billa"
    const val CHITTU = "Chittu"
    const val GAJJU = "Gajju"
    const val ROCKETGIRL = "Rocket Girl"
    const val HOLLOWMAN = "Hollow Man"
    const val SURFER = "Surfing Girl"
    /** v10.16 the app's own little devil mascot ("Settai" was already the monkey's name). */
    const val DEVIL = "Kutti Satan"
}

/** One toggleable part. [options] = styles to pick from (first = default). */
class WardrobeItem(val id: String, val title: String, val defaultOn: Boolean = true, val options: List<String> = emptyList(), val note: String = "")

/** Which wardrobe parts are on for one character (and the chosen style of each). */
class Wear(val character: Character) {
    private val on = HashSet<String>()
    private val choice = HashMap<String, String>()
    init { reset() }

    fun reset() {
        on.clear(); choice.clear()
        for (i in character.wardrobe) { if (i.defaultOn) on.add(i.id); if (i.options.isNotEmpty()) choice[i.id] = i.options[0] }
    }
    fun has(id: String): Boolean = id in on && character.wardrobe.any { it.id == id }
    fun set(id: String, v: Boolean) { if (character.wardrobe.any { it.id == id }) { if (v) on.add(id) else on.remove(id) } }
    fun option(id: String): String = choice[id] ?: character.wardrobe.firstOrNull { it.id == id }?.options?.firstOrNull() ?: ""
    fun setOption(id: String, v: String) { if (character.wardrobe.firstOrNull { it.id == id }?.options?.contains(v) == true) choice[id] = v }
    val enabled: List<WardrobeItem> get() = character.wardrobe.filter { it.id in on }
    fun allOff() = on.clear()
    fun allOn() { character.wardrobe.forEach { on.add(it.id) } }

    /** "mask,cape,hat;hat=lungi" (stable, for preferences). */
    fun encode(): String = character.wardrobe.filter { it.id in on }.joinToString(",") { it.id } + ";" +
        choice.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }
    fun decode(s: String?) {
        if (s == null) return
        val (a, b) = (s.split(';', limit = 2) + "").let { it[0] to it[1] }
        on.clear(); a.split(',').map { it.trim() }.filter { id -> character.wardrobe.any { it.id == id } }.forEach { on.add(it) }
        b.split(',').mapNotNull { kv -> kv.split('=').takeIf { it.size == 2 } }.forEach { setOption(it[0].trim(), it[1].trim()) }
    }

    /** One line for the AI: what this body is wearing / carrying right now. */
    fun describe(): String = if (character.wardrobe.isEmpty()) "" else {
        val list = enabled.joinToString(", ") { i -> if (i.options.isNotEmpty()) "${i.title.lowercase()} (${option(i.id)})" else i.title.lowercase() }
        val off = character.wardrobe.filter { it.id !in on }.joinToString(", ") { it.title.lowercase() }
        "Wearing / carrying now: ${list.ifBlank { "nothing extra" }}." + if (off.isNotBlank()) " Switched OFF by your human (don't mention having these): $off." else ""
    }
}

/** A character's canned-line voice: replacement pools for some shared pools, plus word polish. */
interface LineVoice {
    /** Replacement for a shared [com.combo.monkeycompanion.speech.Lines] pool (by identity), or null. */
    fun pool(p: List<String>): List<String>?
    fun gag(g: Gag): List<String>? = null
    /** Final touch on any picked line (e.g. "monkey" -> "superhero"). */
    fun polish(s: String): String = s
}

/** Swaps given pools; polishes "monkey" into [noun]. */
open class SimpleVoice(private val noun: String, overrides: Map<List<String>, List<String>>, private val gags: Map<Gag, List<String>> = emptyMap()) : LineVoice {
    private val map = java.util.IdentityHashMap<List<String>, List<String>>().apply { putAll(overrides) }
    override fun pool(p: List<String>): List<String>? = map[p]
    override fun gag(g: Gag): List<String>? = gags[g]
    override fun polish(s: String): String = s
        .replace(Regex("\\bmonkeys\\b"), noun + "s").replace(Regex("\\bmonkey\\b"), noun)
        .replace(Regex("\\bMonkeys\\b"), noun.replaceFirstChar { it.uppercase() } + "s").replace(Regex("\\bMonkey\\b"), noun.replaceFirstChar { it.uppercase() })
        .replace("Settai", CharacterRegistry.current.name)
}

abstract class Character(val id: String) {
    abstract val name: String
    /** What it is, in a word ("monkey", "superhero"). */
    abstract val noun: String
    /** One line for the picker. */
    abstract val tagline: String
    abstract val locomotion: Set<Locomotion>
    fun can(l: Locomotion) = l in locomotion
    /** Heavy footsteps (thud + small shake while walking). */
    open val heavy: Boolean get() = false
    /** True when this character hovers/flies using boot & palm jet flames (like Armour Anna / Ironboy mode). */
    open val usesHoverJets: Boolean get() = id == "armour"
    /** True when this character flies/glides with a hero cape and speed streaks (like Superboy mode). */
    open val usesCapeFlight: Boolean get() = id == "superboy"
    /** Falling speed multiplier (an umbrella floats down). */
    open fun fallGravity(w: Wear): Float = 1f
    /** Toggleable parts (Settings > Wardrobe). */
    open val wardrobe: List<WardrobeItem> = emptyList()
    abstract fun newRenderer(): MonkeyRenderer
    /** v4.5: the face that peeks out of the edge-hide tab (rest, peeks, binoculars). */
    open val peek: com.combo.monkeycompanion.rig.PeekFace get() = com.combo.monkeycompanion.rig.MonkeyPeek
    /** Shared superpowers this body uses (random picks + Settings). */
    abstract val powers: List<Superpower>
    /** v10.10: the power this body is known for: the first double-tap always plays it, and the picker favours it. */
    open val signaturePower: Superpower? get() = powers.firstOrNull()
    /** v10.10: the one-time tip the first time this body is picked (with double-tap power on). */
    open val doubleTapTip: String get() = signaturePower?.let { "Double-tap me for my ${it.title}! ⚡ Tap 💬 to chat." } ?: "Double-tap me for a power! ⚡"
    open val voice: LineVoice? get() = null

    /** Pose for a shared state: the monkey animation library first, then this body's adapter. */
    fun fill(p: Pose, s: MonkeyState, t: Float, st: Float, c: AnimContext, w: Wear) {
        Animations.fill(p, s, t, st, c)
        adapt(p, s, t, st, c, w)
    }
    /** Re-shape a shared pose for this body (float instead of walk, cape blanket, visor...). */
    protected open fun adapt(p: Pose, s: MonkeyState, t: Float, st: Float, c: AnimContext, w: Wear) {}

    /**
     * A shared action on this body: the same action, a fallback (e.g. no skateboard -> walk), or null
     * when there's nothing sensible (the host then just reacts with a happy hop).
     */
    open fun mapAction(a: BodyAction, w: Wear): BodyAction? = when (a) {
        BodyAction.CLIMB -> if (can(Locomotion.CLIMB)) a else BodyAction.JUMP
        BodyAction.TAIL_HIDE -> if (id == "monkey") a else BodyAction.HIDE
        BodyAction.SKATE, BodyAction.CHICKEN, BodyAction.GADGET -> null
        else -> a
    }
    /** The gag that plays for [g] on this body (default: the same one). */
    open fun mapGag(g: Gag, w: Wear): Gag = if (g == Gag.TAIL_HIDE && id != "monkey") Gag.FAKE_SLEEP else g

    /** Who the AI is (identity paragraph). [screen] = where it lives. */
    abstract fun identity(screen: String): String
    /** What the body can do (tags), honest about this body and its wardrobe. */
    abstract fun body(w: Wear, compact: Boolean): String
}

object CharacterRegistry {
    val all: List<Character> by lazy { listOf(KuttiSatan, MonkeyCharacter, Superboy, ArmourAnna, MaskMan, Billa, Chittu, Gajju, RocketGirl, HollowMan, SurferGirl) }
    val allWithCustom: List<Character> get() = all + CustomCompanionRegistry.allCharacters()
    @Volatile var current: Character = MonkeyCharacter; private set   // engine fallback; the app picks AppSettings.characterId (v10.16: Kutti Satan on new installs)
    private val wears = HashMap<String, Wear>()

    fun byId(id: String?): Character? {
        val norm = when (id?.lowercase()?.trim()) {
            "chottu", "mouse", "eli" -> "chittu"
            "cat" -> "billa"
            "rocket", "rocket girl", "rocketgirl", "rocketboy", "rocket boy", "rocket_boy", "broomboy", "broom boy" -> "rocketgirl"
            "hollow", "hollow man", "hollowman", "invisible", "invisible man", "dancer" -> "hollowman"
            "devil", "satan", "kutti satan", "kuttisatan", "kutti", "little devil", "imp" -> "devil"
            "surfer", "surf", "surfing girl", "surfgirl", "surfer girl" -> "surfer"
            else -> id
        }
        return all.firstOrNull { it.id.equals(norm, ignoreCase = true) } ?: CustomCompanionRegistry.byId(norm)
    }
    @Synchronized fun wear(c: Character = current): Wear = wears.getOrPut(c.id) { Wear(c) }

    /** Switch the body (memory, settings, chat history all stay). */
    fun select(id: String?) {
        current = byId(id) ?: MonkeyCharacter
        com.combo.monkeycompanion.speech.Lines.voice = current.voice
    }
}
