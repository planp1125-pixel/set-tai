package com.combo.monkeycompanion.brain

/**
 * v4.4 customisable long-press menu (pure; persisted by the app as plain strings, unit-tested).
 *
 *  - [order]: every item id in the user's order (global), incl. power pins as "power:<id>" (power ids are global).
 *  - [off]:   items switched off (global). Power pins are per character instead ([pins]).
 *  - The main ring holds at most [MAX_MAIN] items; when more are on, the last slot becomes "… More".
 *  - Quit can't vanish: switched off, it still sits in More.
 */
object LongPressMenu {
    const val MAX_MAIN = 6
    const val PER_PAGE = 4          // items per page on a second-level ring (+ back / random / next)
    const val MORE = "more"; const val BACK = "back"; const val NEXT = "next"; const val RANDOM = "random"
    const val POWER_PREFIX = "power:"

    enum class Item(val id: String, val icon: String, val label: String, val defaultOn: Boolean, val help: String) {
        CHAT("chat", "💬", "Chat", true, "Open the bubble chat (same as double-tap)"),
        TALK("talk", "🎤", "Talk", true, "Listen once; stops when you pause"),
        MISSED("missed", "🔔", "Missed?", true, "What did I miss? (notifications)"),
        SCREEN("screen", "👀", "Screen", true, "What's on screen?"),
        POWERS("powers", "⚡", "Powers", true, "All superpowers of the current character"),
        FUNNY("funny", "😂", "Funny", true, "Do something funny"),
        GAMES("games", "🎮", "Games", true, "Mini games: catch bananas, staring contest, rock-paper-scissors"),
        SMASH("smash", "💥", "Smash", true, "Screen Smash game: your character smashes a pretend copy of the screen"),
        CLIP("clip", "🎬", "Clip", true, "Make a 5-second funny clip to share"),
        CONTEXT("context", "💡", "App tip", true, "A tip for the current app (Worth it? / Cheaper? / How long?)"),
        QUIT("quit", "✕", "Quit", true, "Quit Settai (asks first)"),
        SETTINGS("settings", "⚙️", "Settings", false, "Open Settai's settings"),
        HIDE("hide", "🫣", "Hide", false, "Hide at the nearest edge"),
        SLEEP("sleep", "😴", "Sleep", false, "Take a nap"),
        DANCE("dance", "💃", "Dance", false, "Dance!"),
        WARDROBE("wardrobe", "👕", "Wardrobe", false, "Change outfit"),
        SWITCH("switch", "🔄", "Switch", false, "Switch to the next character"),
        THINK("think", "🧠", "Thinking?", false, "What's Settai thinking right now?"),
        FOCUS("focus", "📚", "Focus", false, "25-min study / focus desk buddy"),
        BREATHE("breathe", "🫧", "Breathe", false, "60-second calm breathing buddy"),
        SKIT("skit", "🎬", "Skit", false, "Start a funny skit between your Duo/Trio friends"),
        COMFORT("comfort", "🫂", "Comfort", false, "Warm screen hug, cheer sign or filter coffee");

        companion object { fun of(id: String) = entries.firstOrNull { it.id == id } }
    }

    data class Config(val order: List<String>, val off: Set<String>)

    fun defaults() = Config(Item.entries.map { it.id }, Item.entries.filter { !it.defaultOn }.map { it.id }.toSet())

    fun isPower(id: String) = id.startsWith(POWER_PREFIX)
    fun powerId(id: String) = id.removePrefix(POWER_PREFIX)
    fun pin(powerId: String) = POWER_PREFIX + powerId

    // ---- persistence (plain strings) ----
    fun encodeOrder(c: Config) = c.order.joinToString(",")
    fun encodeOff(c: Config) = c.off.sorted().joinToString(",")

    /** Unknown ids dropped, duplicates removed, missing built-in items appended (new items get their default state). */
    fun decode(order: String?, off: String?): Config {
        if (order.isNullOrBlank()) return defaults()
        val seen = LinkedHashSet<String>()
        for (raw in order.split(',')) {
            val id = raw.trim()
            if (id.isNotEmpty() && (Item.of(id) != null || (isPower(id) && powerId(id).isNotBlank()))) seen.add(id)
        }
        val offSet = off.orEmpty().split(',').map { it.trim() }.filter { Item.of(it) != null }.toMutableSet()
        for (it in Item.entries) if (seen.add(it.id) && !it.defaultOn) offSet.add(it.id)
        return Config(seen.toList(), offSet)
    }

    fun encodePins(p: List<String>) = p.joinToString(",")
    fun decodePins(s: String?) = s.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /** [order] plus any of [powerIds] not in it yet (as pins, at the end), so the editor can list them. */
    fun withPowers(c: Config, powerIds: List<String>): Config {
        val extra = powerIds.map { pin(it) }.filter { it !in c.order }
        return if (extra.isEmpty()) c else c.copy(order = c.order + extra)
    }

    fun toggle(c: Config, id: String, on: Boolean) = c.copy(off = if (on) c.off - id else c.off + id)

    /**
     * Move an item within the [visible] subsequence (the editor shows the built-ins plus this character's powers),
     * leaving other characters' power slots where they are.
     */
    fun move(c: Config, visible: List<String>, from: Int, to: Int): Config {
        if (from == to || from !in visible.indices || to !in visible.indices) return c
        val seq = visible.toMutableList(); val x = seq.removeAt(from); seq.add(to, x)
        val slots = c.order.withIndex().filter { it.value in visible.toSet() }.map { it.index }
        val out = c.order.toMutableList()
        slots.forEachIndexed { i, idx -> out[idx] = seq[i] }
        return c.copy(order = out)
    }

    data class Ring(val main: List<String>, val more: List<String>)

    /**
     * What's on the rings now. [pins]: this character's pinned power ids; [powerIds]: its powers (stale pins drop);
     * [context]: an app tip exists for the current app.
     */
    fun layout(c: Config, pins: Collection<String>, powerIds: Collection<String>, context: Boolean): Ring {
        val order = c.order + pins.map { pin(it) }.filter { it !in c.order }   // new pins start at the end
        val on = order.filter { id ->
            if (isPower(id)) powerId(id) in pins && powerId(id) in powerIds
            else id !in c.off && (id != Item.CONTEXT.id || context)
        }
        val forced = if (Item.QUIT.id in on) emptyList() else listOf(Item.QUIT.id)
        return when {
            forced.isEmpty() && on.size <= MAX_MAIN -> Ring(on, emptyList())
            on.size <= MAX_MAIN - 1 -> Ring(on + MORE, forced)
            else -> Ring(on.take(MAX_MAIN - 1) + MORE, on.drop(MAX_MAIN - 1) + forced)
        }
    }

    /** One page of a second-level ring: [BACK] + up to [PER_PAGE] items (+ [RANDOM] when asked) + [NEXT] if more pages. */
    fun page(items: List<String>, page: Int, random: Boolean = false): List<String> {
        val pages = pageCount(items)
        val p = page.coerceIn(0, pages - 1)
        val slice = items.drop(p * PER_PAGE).take(PER_PAGE)
        return buildList {
            add(BACK); addAll(slice)
            if (random) add(RANDOM)
            if (p < pages - 1) add(NEXT)
        }
    }
    fun pageCount(items: List<String>) = maxOf(1, (items.size + PER_PAGE - 1) / PER_PAGE)
    /** Next page index, wrapping to the first. */
    fun nextPage(items: List<String>, page: Int) = (page + 1) % pageCount(items)

    /** A power button's state. */
    sealed class PowerState {
        object Ready : PowerState()
        data class Cooling(val secs: Int) : PowerState()
    }
    fun powerState(cooldownLeft: Float): PowerState =
        if (cooldownLeft > 0f) PowerState.Cooling(kotlin.math.ceil(cooldownLeft).toInt().coerceAtLeast(1)) else PowerState.Ready
    fun powerLabel(name: String, s: PowerState) = when (s) { is PowerState.Cooling -> "${s.secs}s"; else -> name }
    /** Battery saver: manual powers are allowed, with a warning the first time only. */
    fun saverWarn(saver: Boolean, warnedBefore: Boolean) = saver && !warnedBefore

    // ---- short names + icons for powers (ring labels) ----
    private val POWER_ICONS = mapOf(
        "rocket" to "🚀", "banana" to "🍌", "thunder" to "⚡", "ninja" to "🥷", "kovam" to "😡", "ice" to "❄️", "giant" to "👁️",
        "freeze" to "⏱️", "magic" to "🎩", "swoop" to "🦸", "laser" to "🔥", "breath" to "🌬️", "lift" to "🏋️", "blur" to "💨",
        "jets" to "🛩️", "confetti" to "🎉", "shield" to "🛡️", "lowpower" to "🔋",
    )
    fun powerIcon(id: String) = POWER_ICONS[id] ?: "✨"
    private val SHORT = mapOf(
        "rocket" to "Rocket", "banana" to "Banana", "thunder" to "Thunder", "ninja" to "Ninja", "kovam" to "Kovam", "ice" to "Ice",
        "giant" to "Giant", "freeze" to "Freeze", "magic" to "Magic", "swoop" to "Swoop", "laser" to "Heat eyes", "breath" to "Breath",
        "lift" to "Strength", "blur" to "Speed", "jets" to "Jets", "confetti" to "Confetti", "shield" to "Shield", "lowpower" to "Low power",
    )
    /** Short ring label for a power. */
    fun shortName(id: String, title: String): String = SHORT[id] ?: title.substringBefore(' ').take(10)

    /** The powers ring for a character: every power it has, in its own order (ids), deduplicated. */
    fun powerRing(own: List<Superpower>): List<String> = own.map { it.id }.distinct()

    // ---- v10.13 grouped popup: exactly these top-level groups ----
    data class Group(val id: String, val icon: String, val label: String)
    val GROUPS = listOf(
        Group("g_powers", "⚡", "Powers"), Group("g_games", "🎮", "Games"), Group("g_show", "🎬", "Show"),
        Group("g_modes", "😊", "Modes"), Group("g_tools", "⏰", "Tools"), Group("g_access", "🔌", "Access"),
    )
    /** The top-level popup: 6 groups + Hide + Quit. No chat / settings clutter. */
    fun groupedMain(): List<String> = GROUPS.map { it.id } + listOf("hide", "quit")
    fun isGroup(id: String) = id.startsWith("g_")
    fun groupItems(id: String): List<String> = when (id) {
        "g_games" -> listOf("smash", "banana_game", "eater")
        "g_show" -> listOf("show", "clip", "selfie")
        "g_modes" -> listOf("sleep", "focus", "breathe", "comfort_hug", "comfort_sign", "comfort_coffee")
        "g_tools" -> CareTools.Tool.entries.map { it.id }
        "g_access" -> listOf("chat", "talk", "screen", "missed")
        else -> emptyList()
    }
    private val SUB = mapOf(
        "smash" to ("💥" to "Smash"), "banana_game" to ("🍌" to "Bananas"), "eater" to ("😋" to "Eat screen"),
        "show" to ("🐱🐭" to "Start show"), "clip" to ("🎥" to "Record"), "selfie" to ("🤳" to "Photobomb"),
        "sleep" to ("😴" to "Sleep"), "focus" to ("📚" to "Study"), "breathe" to ("🫧" to "Breathe"),
        "comfort_hug" to ("🫂" to "Hug"), "comfort_sign" to ("📣" to "Cheer"), "comfort_coffee" to ("☕" to "Coffee"),
        "chat" to ("💬" to "Chat"), "talk" to ("🎤" to "Mic"), "screen" to ("👀" to "Screen"), "missed" to ("🔔" to "Notifications"),
        "hide" to ("🙈" to "Hide"), "quit" to ("✕" to "Quit"),
    )
    /** Icon + label for a group or grouped item (null = not part of the grouped popup). */
    fun groupedEntry(id: String): Pair<String, String>? =
        GROUPS.firstOrNull { it.id == id }?.let { it.icon to it.label }
            ?: SUB[id] ?: CareTools.Tool.of(id)?.let { it.icon to it.label }
}
