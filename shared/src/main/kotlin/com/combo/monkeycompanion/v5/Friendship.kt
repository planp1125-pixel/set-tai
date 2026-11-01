package com.combo.monkeycompanion.v5

/**
 * v5 friendship level: XP from pets, chats, games and the daily streak. Levels unlock tricks / gags / wardrobe
 * items / lines. Never sold, never bought: you just hang out. Pure (persisted by AppSettings as a string).
 */
object Friendship {
    enum class Source(val xp: Int, val dailyCap: Int) {
        PET(2, 60), CHAT(5, 100), GAME(10, 120), GAME_WIN(5, 60), STREAK_DAY(15, 15), CLIP(8, 24), PAIR(10, 30),
        /** v10.20 the daily mischief challenge, once a day. */
        CHALLENGE(25, 25)
    }

    /** Total XP needed to reach level n (1-based): 0, 60, 160, 300, 480, 700, ... (grows gently). */
    fun xpFor(level: Int): Int = if (level <= 1) 0 else (level - 1) * 40 + (level - 1) * (level - 1) * 20
    fun level(xp: Int): Int { var l = 1; while (xpFor(l + 1) <= xp) l++; return l }
    /** 0..1 within the current level. */
    fun progress(xp: Int): Float { val l = level(xp); val a = xpFor(l); val b = xpFor(l + 1); return ((xp - a).toFloat() / (b - a)).coerceIn(0f, 1f) }

    data class Unlock(val level: Int, val id: String, val title: String, val kind: String)

    val unlocks = listOf(
        Unlock(2, "lines_close", "Closer-friend lines", "lines"),
        Unlock(3, "gag_airguitar", "Air guitar gag", "gag"),
        Unlock(4, "trick_backflip", "Backflip trick", "trick"),
        Unlock(5, "wear_crown", "Friendship crown", "wardrobe"),
        Unlock(6, "gag_selfie", "Selfie gag", "gag"),
        Unlock(7, "trick_moonwalk", "Moonwalk", "trick"),
        Unlock(8, "wear_garland", "Flower garland", "wardrobe"),
        Unlock(10, "lines_bestie", "Best-friend lines", "lines"),
    )
    fun unlocked(xp: Int) = unlocks.filter { it.level <= level(xp) }
    fun has(xp: Int, id: String) = unlocked(xp).any { it.id == id }
    fun newAt(level: Int) = unlocks.filter { it.level == level }

    fun title(level: Int) = when {
        level >= 10 -> "Best friends forever"
        level >= 7 -> "Thick friends"
        level >= 4 -> "Good friends"
        level >= 2 -> "Friends"
        else -> "Just met"
    }

    /** Persisted state. [day] = yyyy-MM-dd of the last activity; [today] counts per source (daily caps). */
    data class State(val xp: Int = 0, val streak: Int = 0, val day: String = "", val today: Map<Source, Int> = emptyMap()) {
        fun encode() = "$xp|$streak|$day|" + today.entries.joinToString(",") { "${it.key.name}=${it.value}" }
        companion object {
            fun decode(s: String?): State {
                if (s.isNullOrBlank()) return State()
                val p = s.split('|')
                val m = p.getOrNull(3).orEmpty().split(',').mapNotNull { kv ->
                    val (k, v) = kv.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
                    runCatching { Source.valueOf(k) to v.toInt() }.getOrNull()
                }.toMap()
                return State(p.getOrNull(0)?.toIntOrNull() ?: 0, p.getOrNull(1)?.toIntOrNull() ?: 0, p.getOrNull(2).orEmpty(), m)
            }
        }
    }

    data class Result(val state: State, val gained: Int, val levelUp: Int?)

    /**
     * Add XP for [src] on [day] (yyyy-MM-dd); [yesterday] = the day before. The first activity of a new day
     * counts the streak (+bonus), daily caps keep it fair.
     */
    fun add(s: State, src: Source, day: String, yesterday: String): Result {
        var st = s
        var gained = 0
        if (st.day != day) {
            val streak = if (st.day == yesterday) st.streak + 1 else 1
            st = st.copy(streak = streak, day = day, today = emptyMap())
            if (src != Source.STREAK_DAY) {
                val bonus = Source.STREAK_DAY.xp + minOf(streak - 1, 6) * 5
                gained += bonus
                st = st.copy(today = mapOf(Source.STREAK_DAY to bonus))
            }
        }
        val used = st.today[src] ?: 0
        val add = minOf(src.xp, maxOf(0, src.dailyCap - used))
        gained += add
        val before = level(s.xp)
        val ns = st.copy(xp = s.xp + gained, today = st.today + (src to used + add))
        val after = level(ns.xp)
        return Result(ns, gained, if (after > before) after else null)
    }

    fun levelUpLine(level: Int, characterId: String): String {
        val n = newAt(level).joinToString(", ") { it.title }
        val who = when (characterId) { "superboy" -> "Hero bond"; "armour" -> "Suit sync"; "mask" -> "Mask Man trust"; else -> "Friendship" }
        return "$who level $level! 🎉" + if (n.isNotEmpty()) " Unlocked: $n" else ""
    }
}
