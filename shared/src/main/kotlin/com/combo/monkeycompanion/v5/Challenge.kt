package com.combo.monkeycompanion.v5

import java.time.LocalDate

/**
 * v10.20 daily mischief challenge: one small thing to do together each day, picked by the date (never Random, so
 * GoldenTraceTest's stream is untouched, and it's the same challenge all day). Finishing it gives friendship XP
 * ([Friendship.Source.CHALLENGE]). Most challenges use the physical comedy (tosses, splats, catches).
 */
object Challenge {
    enum class Kind(val id: String, val goal: Int, val title: String, val unit: String) {
        SPLAT("splat", 3, "Toss me at the screen edge 3 times (SPLAT!)", "splats"),
        THWACK("thwack", 2, "Throw me up into the top edge 2 times", "head bonks"),
        CRASH("crash", 4, "Make me crash 4 times: walls, top or a hard landing", "crashes"),
        CATCH("catch", 2, "Catch me in mid-air 2 times while I'm flying", "catches"),
        PET("pet", 10, "Give me 10 pats", "pats"),
        CHAT("chat", 3, "Chat with me 3 times", "chats"),
    }

    /** Something that happened that a challenge may count. */
    enum class Event { SPLAT, THWACK, PANCAKE, CATCH, PET, CHAT }

    data class State(val day: String = "", val kind: Kind = Kind.SPLAT, val progress: Int = 0, val done: Boolean = false) {
        fun encode() = "$day|${kind.id}|$progress|${if (done) 1 else 0}"
        val left get() = (kind.goal - progress).coerceAtLeast(0)
        /** "2/3 splats". */
        fun status() = if (done) "done ✅" else "$progress/${kind.goal} ${kind.unit}"
        companion object {
            fun decode(s: String?): State {
                if (s.isNullOrBlank()) return State()
                val p = s.split('|')
                return State(p.getOrNull(0).orEmpty(), Kind.values().firstOrNull { it.id == p.getOrNull(1) } ?: Kind.SPLAT,
                    p.getOrNull(2)?.toIntOrNull() ?: 0, p.getOrNull(3) == "1")
            }
        }
    }

    /** The order the challenges come in; the date picks the position, so neighbouring days differ. */
    private val ROTATION = listOf(Kind.SPLAT, Kind.PET, Kind.CATCH, Kind.CRASH, Kind.CHAT, Kind.THWACK)

    fun kindFor(day: LocalDate): Kind = ROTATION[Math.floorMod(day.toEpochDay(), ROTATION.size.toLong()).toInt()]

    /** Today's challenge (a fresh one when the saved state is from another day). */
    fun today(s: State, day: LocalDate): State = if (s.day == day.toString()) s else State(day.toString(), kindFor(day), 0, false)

    fun counts(k: Kind, e: Event): Boolean = when (k) {
        Kind.SPLAT -> e == Event.SPLAT
        Kind.THWACK -> e == Event.THWACK
        Kind.CRASH -> e == Event.SPLAT || e == Event.THWACK || e == Event.PANCAKE
        Kind.CATCH -> e == Event.CATCH
        Kind.PET -> e == Event.PET
        Kind.CHAT -> e == Event.CHAT
    }

    /** Count [e] on [day]. Returns the new state and whether the challenge was just completed. */
    fun note(s: State, e: Event, day: LocalDate): Pair<State, Boolean> {
        val cur = today(s, day)
        if (cur.done || !counts(cur.kind, e)) return cur to false
        val p = cur.progress + 1
        val done = p >= cur.kind.goal
        return cur.copy(progress = p, done = done) to done
    }

    fun doneLine(k: Kind, seed: Int): String = listOf(
        "Challenge done! 🎯 +${Friendship.Source.CHALLENGE.xp} friendship 💛",
        "You did it! Today's mischief: complete 🏆",
        "Yesss! ${k.goal} ${k.unit}, challenge cleared 🎉",
    )[Math.floorMod(seed, 3)]
}
