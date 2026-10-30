package com.combo.monkeycompanion.agent

import java.util.Calendar

/** What starts a routine. */
sealed class Trigger {
    /** Time of day; [days] = Calendar.DAY_OF_WEEK values (empty = every day). [once] = delete after it runs. */
    data class At(val hour: Int, val minute: Int, val days: Set<Int> = emptySet(), val once: Boolean = false) : Trigger()
    data class On(val event: RoutineEvent) : Trigger()

    fun describe(): String = when (this) {
        is At -> "%s at %d:%02d %s".format(
            when {
                once -> "Once"
                days.isEmpty() || days.size == 7 -> "Every day"
                days == RoutineParse.WEEKDAYS -> "Weekdays"
                days == RoutineParse.WEEKEND -> "Weekends"
                else -> "Every " + days.sorted().joinToString("/") { RoutineParse.DAY_NAMES[it - 1] }
            }, if (hour % 12 == 0) 12 else hour % 12, minute, if (hour < 12) "am" else "pm")
        is On -> "When " + event.label
    }
}

enum class RoutineEvent(val label: String) {
    CHARGER("the charger is connected"), BATTERY_LOW("the battery gets low"), WIFI("Wi-Fi connects"), HEADPHONES("headphones are plugged in")
}

/** [say] = a line Settai says / posts; or [capId] + [args] = one of Settai's powers (asks Yes/No when it changes something). */
data class Routine(
    val id: Int, val trigger: Trigger, val say: String? = null, val capId: String? = null, val args: Map<String, Any?> = emptyMap(),
    val enabled: Boolean = true, val text: String = "",
) {
    fun describe(): String = trigger.describe() + " → " + (say?.let { "say “$it”" } ?: "${capId ?: "?"} ${args.filterKeys { !it.startsWith("_") }.values.joinToString(" ")}".trim())
}

object RoutineTime {
    /** Next time (millis) an [At] trigger fires strictly after [now]. */
    fun next(t: Trigger.At, now: Calendar): Long {
        val c = now.clone() as Calendar
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        c.set(Calendar.HOUR_OF_DAY, t.hour); c.set(Calendar.MINUTE, t.minute)
        repeat(8) {
            if (c.timeInMillis > now.timeInMillis && (t.days.isEmpty() || c.get(Calendar.DAY_OF_WEEK) in t.days)) return c.timeInMillis
            c.add(Calendar.DAY_OF_MONTH, 1)
            c.set(Calendar.HOUR_OF_DAY, t.hour); c.set(Calendar.MINUTE, t.minute)
        }
        return c.timeInMillis
    }
}

/**
 * Chat -> routine (pure): "every day at 9 pm remind me to drink water", "weekdays at 7:30 say good morning",
 * "when the charger is connected say thanks", "whenever wifi connects turn on media volume 40",
 * "routine: every night at 10 turn on dnd". [command] turns the action words into a power call.
 */
object RoutineParse {
    val WEEKDAYS = setOf(2, 3, 4, 5, 6)
    val WEEKEND = setOf(1, 7)
    val DAY_NAMES = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val DAY_RE = listOf("sun(day)?", "mon(day)?", "tue(s|sday)?", "wed(nesday)?", "thu(rs|rsday)?", "fri(day)?", "sat(urday)?")

    fun looksLikeRoutine(t: String): Boolean {
        val l = t.lowercase()
        return Regex("\\b(every ?day|everyday|daily|every (morning|night|evening|afternoon|week|mon|tue|wed|thu|fri|sat|sun)\\w*|weekdays?|weekends?|each (day|morning|night)|whenever|routine|thinamum|dhinamum|daily um)\\b").containsMatchIn(l) ||
            Regex("^\\s*(when|whenever)\\b.*\\b(charg|battery|wifi|wi-fi|headphone|earphone|headset|earbuds)").containsMatchIn(l)
    }

    fun event(l: String): RoutineEvent? = when {
        Regex("\\b(charger|charging|plugged in to charge|plug (it )?in)\\b").containsMatchIn(l) && !l.contains("headphone") -> RoutineEvent.CHARGER
        Regex("\\bbattery (is |gets |goes )?(low|below|dying|down)|low battery\\b").containsMatchIn(l) -> RoutineEvent.BATTERY_LOW
        Regex("\\b(wi-?fi)\\b").containsMatchIn(l) && Regex("\\b(connect|connects|connected|join|on)\\b").containsMatchIn(l) -> RoutineEvent.WIFI
        Regex("\\b(headphones?|earphones?|headset|earbuds|buds)\\b").containsMatchIn(l) -> RoutineEvent.HEADPHONES
        else -> null
    }

    private fun time(l: String): Pair<Int, Int>? {
        Regex("\\bat (\\d{1,2})(?:[:.](\\d{2}))? ?(am|pm|a\\.m\\.|p\\.m\\.)?").find(l)?.let { m ->
            var h = m.groupValues[1].toInt(); val min = m.groupValues[2].ifEmpty { "0" }.toInt(); val ap = m.groupValues[3]
            if (h > 23 || min > 59) return null
            if (ap.startsWith("p") && h < 12) h += 12
            if (ap.startsWith("a") && h == 12) h = 0
            if (ap.isEmpty() && h in 1..11) { if (Regex("\\b(night|evening|pm|raathiri|iravu)\\b").containsMatchIn(l)) h += 12 else if (Regex("\\bafternoon\\b").containsMatchIn(l) && h < 7) h += 12 }
            return h to min
        }
        Regex("\\b(\\d{1,2})(?:[:.](\\d{2}))? ?(am|pm)\\b").find(l)?.let { m ->
            var h = m.groupValues[1].toInt(); val min = m.groupValues[2].ifEmpty { "0" }.toInt()
            if (h > 12 || min > 59) return null
            if (m.groupValues[3] == "pm" && h < 12) h += 12; if (m.groupValues[3] == "am" && h == 12) h = 0
            return h to min
        }
        return when {
            Regex("\\bmorning|kaalai\\b").containsMatchIn(l) -> 8 to 0
            Regex("\\bafternoon\\b").containsMatchIn(l) -> 14 to 0
            Regex("\\bevening\\b").containsMatchIn(l) -> 18 to 0
            Regex("\\bnight|raathiri|iravu\\b").containsMatchIn(l) -> 21 to 0
            else -> null
        }
    }

    private fun days(l: String): Set<Int> = when {
        Regex("\\bweekdays?\\b").containsMatchIn(l) -> WEEKDAYS
        Regex("\\bweekends?\\b").containsMatchIn(l) -> WEEKEND
        else -> DAY_RE.mapIndexedNotNull { i, re -> if (Regex("\\b$re\\b").containsMatchIn(l) && !Regex("\\bevery ?day\\b").containsMatchIn(l)) i + 1 else null }.toSet()
    }

    /** The action part: strips the trigger words. */
    fun actionText(t: String): String {
        var a = t.trim().trimEnd('.', '!')
        a = a.replace(Regex("(?i)^(routine|make a routine|create a routine|add a routine|new routine)\\s*[:,-]?\\s*"), "")
        a = a.replace(Regex("(?i)\\b(every ?day|everyday|daily|thinamum|dhinamum|each day|every (morning|night|evening|afternoon)|on weekdays|weekdays?|on weekends|weekends?|every (sun|mon|tue|wed|thu|fri|sat)\\w*( and (sun|mon|tue|wed|thu|fri|sat)\\w*)*)\\b"), " ")
        a = a.replace(Regex("(?i)\\bat \\d{1,2}([:.]\\d{2})? ?(am|pm|a\\.m\\.|p\\.m\\.)?\\b"), " ")
        a = a.replace(Regex("(?i)\\b\\d{1,2}([:.]\\d{2})? ?(am|pm)\\b"), " ")
        a = a.replace(Regex("(?i)^\\s*(when(ever)?|if)\\s+(the\\s+|my\\s+)?(phone\\s+)?(charger|charging|battery|wi-?fi|headphones?|earphones?|headset|earbuds|buds)[^,]*?(connected|connects|plugged in|plugged|is low|gets low|goes low|low|in|joins|on)\\b\\s*,?\\s*(then\\s+)?"), "")
        a = a.replace(Regex("(?i)^\\s*(in the |at )?(morning|night|evening|afternoon)\\b\\s*,?"), "")
        return a.replace(Regex("\\s+"), " ").trim().trim(',').trim()
    }

    /** Builds a routine (id 0) or null when it isn't one / can't be understood. */
    fun parse(input: String, command: (String) -> Pair<String, Map<String, Any?>>?): Routine? {
        val t = input.trim()
        if (!looksLikeRoutine(t)) return null
        val l = t.lowercase()
        val ev = if (Regex("^\\s*(routine\\s*[:,-]?\\s*)?(when|whenever|if)\\b").containsMatchIn(l)) event(l) else null
        val trigger: Trigger = ev?.let { Trigger.On(it) } ?: time(l)?.let { (h, m) -> Trigger.At(h, m, days(l)) } ?: return null
        val act = actionText(t)
        if (act.isEmpty()) return null
        val al = act.lowercase()
        Regex("(?i)^(remind me (to |about |that )?|tell me (to |that )?|say |ask me (to |if )?)(.+)$").find(act)?.let { m ->
            val what = m.groupValues.last().trim()
            val line = when {
                al.startsWith("remind") -> "⏰ Reminder: $what"
                al.startsWith("ask") -> "Did you ${what.removePrefix("if ")}? 🐒"
                else -> what.replaceFirstChar { it.uppercase() }
            }
            return Routine(0, trigger, say = line, text = t)
        }
        command(act)?.let { (id, args) -> return Routine(0, trigger, capId = id, args = args, text = t) }
        return Routine(0, trigger, say = act.replaceFirstChar { it.uppercase() }, text = t)
    }
}
