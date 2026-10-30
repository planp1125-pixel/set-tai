package com.combo.monkeycompanion.brain

/** v10.13 ⏰ Tools: water, daily tablet, hourly eye break, neck exercise, custom reminder (pure, unit-tested). */
object CareTools {
    enum class Kind { INTERVAL, DAILY, ONCE }
    enum class Tool(val id: String, val icon: String, val label: String, val kind: Kind, val defaultValue: Int, val line: String) {
        WATER("tool_water", "💧", "Water", Kind.INTERVAL, 60, "💧 Water time! Oru glass thanni kudinga 😊"),
        TABLET("tool_tablet", "💊", "Tablet", Kind.DAILY, 9 * 60, "💊 Daily tablet time! Marakkaama saapdunga 🙏"),
        EYE("tool_eye", "👀", "Eye break", Kind.INTERVAL, 60, "👀 Eye break! Look 20 feet away for 20 seconds (20-20-20)"),
        NECK("tool_neck", "🧘", "Neck", Kind.INTERVAL, 60, "🧘 Neck stretch: tilt left 5s, right 5s, chin down 5s, slow circles 🙆"),
        CUSTOM("tool_custom", "📝", "Reminder", Kind.ONCE, 30, "⏰ Reminder!");
        companion object { fun of(id: String) = entries.firstOrNull { it.id == id } }
    }

    /** [value]: minutes (INTERVAL), minute of day (DAILY) or minutes-from-now (ONCE). [atMs]: next fire for ONCE / anchor for INTERVAL. */
    data class State(val on: Boolean = false, val value: Int, val lastFiredMs: Long = 0L, val atMs: Long = 0L, val text: String = "")

    fun default(t: Tool) = State(value = t.defaultValue)

    /** Switch on/off at [now]: intervals count from now; ONCE schedules now + value minutes. */
    fun toggle(t: Tool, s: State, on: Boolean, now: Long): State = when {
        !on -> s.copy(on = false)
        t.kind == Kind.ONCE -> s.copy(on = true, atMs = now + s.value * 60_000L)
        else -> s.copy(on = true, lastFiredMs = if (t.kind == Kind.INTERVAL) now else s.lastFiredMs)
    }

    fun due(t: Tool, s: State, now: Long, minuteOfDay: Int, dayIndex: Long, lastFiredDay: Long): Boolean {
        if (!s.on) return false
        return when (t.kind) {
            Kind.INTERVAL -> now - s.lastFiredMs >= s.value.coerceAtLeast(5) * 60_000L
            Kind.DAILY -> minuteOfDay >= s.value && lastFiredDay != dayIndex
            Kind.ONCE -> s.atMs in 1..now
        }
    }

    /** State after firing. ONCE switches itself off. */
    fun fired(t: Tool, s: State, now: Long): State = if (t.kind == Kind.ONCE) s.copy(on = false, lastFiredMs = now, atMs = 0L) else s.copy(lastFiredMs = now)

    fun line(t: Tool, s: State) = if (t == Tool.CUSTOM && s.text.isNotBlank()) "⏰ ${s.text}" else t.line

    fun describe(t: Tool, s: State): String = when (t.kind) {
        Kind.INTERVAL -> if (s.value % 60 == 0) "every ${s.value / 60} h" else "every ${s.value} min"
        Kind.DAILY -> "daily at %02d:%02d".format(s.value / 60, s.value % 60)
        Kind.ONCE -> "in ${s.value} min"
    }
}
