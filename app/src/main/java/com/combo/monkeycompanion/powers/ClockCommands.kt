package com.combo.monkeycompanion.powers

import java.util.Calendar
import java.util.Locale

/** A timer/alarm request understood straight from what the user typed (works with every brain). */
sealed class ClockCommand {
    data class SetTimer(val seconds: Int, val label: String = "") : ClockCommand()
    /** [hour] 0-23. [explicitAmPm] false when the user said just "at 7" (we picked the next 7 o'clock). */
    data class SetAlarm(val hour: Int, val minute: Int, val label: String = "", val explicitAmPm: Boolean = true, val tomorrow: Boolean = false) : ClockCommand()
    data object List : ClockCommand()
    data class Cancel(val what: String = "all") : ClockCommand()   // "timer" | "alarm" | "all"
}

/**
 * Deterministic parser for "set a timer for 5 minutes", "wake me up at 6:30", "alarm at 7 pm",
 * "remind me in 20 min", "cancel my timer", "what timers do I have?"…
 * Returns null for anything that isn't clearly a timer/alarm command (then the brain answers).
 */
object ClockCommands {
    private val NUM_WORDS = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "fifteen" to 15, "twenty" to 20,
        "thirty" to 30, "forty" to 40, "forty-five" to 45, "fortyfive" to 45, "sixty" to 60, "ninety" to 90,
    )

    fun parse(input: String, now: Calendar = Calendar.getInstance()): ClockCommand? {
        val t = " " + normalise(input) + " "
        val timerWord = Regex("\\btimers?\\b|\\bcountdown\\b").containsMatchIn(t)
        val alarmWord = Regex("\\balarms?\\b|\\bwake me\\b|\\bwake up me\\b|\\bget me up\\b").containsMatchIn(t)
        val remindIn = Regex("\\bremind me in\\b|\\bping me in\\b|\\btell me in\\b").containsMatchIn(t)
        if (!timerWord && !alarmWord && !remindIn) return null

        // Cancel / list
        if (Regex("\\b(cancel|stop|delete|remove|clear|turn off|dismiss|kill)\\b").containsMatchIn(t)) {
            return ClockCommand.Cancel(if (timerWord && !alarmWord) "timer" else if (alarmWord && !timerWord) "alarm" else "all")
        }
        if (Regex("\\b(list|show|what|which|any|how (long|much)|time left|remaining|check)\\b").containsMatchIn(t) &&
            !Regex("\\bfor\\b|\\bin \\d|\\bat \\d").containsMatchIn(t)) return ClockCommand.List

        val label = Regex("\\b(?:for|to|called|named|labell?ed) (?:the |my )?([a-z][a-z '\\-]{1,30}?)(?: timer| alarm)? *$").find(t.trimEnd() + " ")
            ?.groupValues?.get(1)?.trim()?.takeIf { l -> !Regex("\\d|minute|second|hour|min\\b|sec\\b|hr\\b|am\\b|pm\\b|morning|evening|night|tomorrow|today").containsMatchIn(l) }
            ?: ""

        if (timerWord || remindIn) {
            durationSeconds(t)?.let { if (it in 1..(24 * 3600)) return ClockCommand.SetTimer(it, label) }
            if (timerWord && !alarmWord) return null   // "timer" without a length: let the brain ask
        }
        if (alarmWord) {
            val tomorrow = Regex("\\btomorrow\\b").containsMatchIn(t)
            clockTime(t, now)?.let { (h, m, explicit) -> return ClockCommand.SetAlarm(h, m, label, explicit, tomorrow) }
            // "alarm in 20 minutes" -> alarm at now + 20 min
            durationSeconds(t)?.let { s ->
                val c = (now.clone() as Calendar).apply { add(Calendar.SECOND, s) }
                return ClockCommand.SetAlarm(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), label, true)
            }
        }
        return null
    }

    /**
     * Lower-case, tidy punctuation, and map common Tamil-English (Tanglish) words:
     * "alarm vei/vai/vachidu" (set alarm), "ezhuppu/ezhupu" (wake me), "mani/manikku" (o'clock),
     * "kaalai/kaalaila" (morning), "saayangalam/raathiri/night" (evening), "nimisham" (minutes),
     * "naalaikku/naalai" (tomorrow), "maniநேரம்"-style hours "mani neram" (hours).
     */
    fun normalise(input: String): String {
        var t = " " + input.lowercase(Locale.ROOT).replace('’', '\'').replace(Regex("[!?,;]"), " ") + " "
        val map = listOf(
            "\\b(ezhuppu|ezhupu|ezhupi(du|vidu)?|ezhuppi(du|vidu)?|eluppu|elupu)\\b" to "wake me",
            "\\b(naalaikku|naalaiku|naalai|nalaiku|nalaikku)\\b" to "tomorrow",
            "\\b(kaalaila|kaalayila|kaalai|kalaila|kalai|morning la)\\b" to "am",
            "\\b(saayangalam|sayangalam|saayandhiram|raathiri|rathiri|iravu|night la)\\b" to "pm",
            "\\b(nimisham|nimisam|nimishathula|nimishathil|nimidam)\\b" to "minutes",
            "\\bmani neram\\b" to "hours",
            "\\b(\\d{1,2})\\s*(manikku|maniku|mani ku|mani)\\b" to "at $1",
            "\\b(vei|vai|vaikka|vachidu|vechidu|vachu|podu|pottu|set pannu|set panni|vaiyi)\\b" to "set",
        )
        for ((a, b) in map) t = t.replace(Regex(a), b)
        return t.replace(Regex("\\s+"), " ").trim()
    }

    /** "5 minutes", "1h 30m", "90 sec", "half an hour", "an hour and a half", "2 and a half minutes", "five min". */
    fun durationSeconds(text: String): Int? {
        var t = " " + text.lowercase(Locale.ROOT) + " "
        for ((w, n) in NUM_WORDS) t = t.replace(Regex("(?<=\\s)$w(?=\\s)"), n.toString())
        t = t.replace(Regex("\\bhalf (an?|1) hour\\b"), "30 minutes")
            .replace(Regex("\\ban? (hour|minute|second|min|sec)\\b"), "1 $1")
            .replace(Regex("\\b(an?|1) hour and a half\\b"), "90 minutes")
            .replace(Regex("\\bquarter of an hour\\b|\\ba quarter hour\\b"), "15 minutes")
        val unit = "(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\\b"
        val re = Regex("(\\d+(?:[.,]\\d+)?)(?: and a half| and half)? ?$unit")
        var total = 0.0; var found = false
        for (m in re.findAll(t)) {
            var n = m.groupValues[1].replace(',', '.').toDouble()
            if (m.value.contains("half")) n += 0.5
            val u = m.groupValues[2]
            total += n * when {
                u.startsWith("h") -> 3600.0
                u.startsWith("m") -> 60.0
                else -> 1.0
            }
            found = true
        }
        // "1:30 timer" / "timer 5" (bare number = minutes)
        if (!found) {
            Regex("\\b(\\d{1,2}):(\\d{2})\\b").find(t)?.let { return it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
            Regex("\\btimer (?:for )?(\\d{1,3})\\s*$|\\b(\\d{1,3}) ?timer\\b").find(t.trimEnd())?.let { m ->
                return (m.groupValues[1].ifEmpty { m.groupValues[2] }).toInt() * 60
            }
            return null
        }
        return Math.round(total).toInt().takeIf { it > 0 }
    }

    /** "at 6:30", "6.30 am", "7 pm", "18:45", "at 7" (next 7 o'clock), "noon", "midnight". Returns (hour, minute, explicit). */
    fun clockTime(text: String, now: Calendar = Calendar.getInstance()): Triple<Int, Int, Boolean>? {
        val t = " " + text.lowercase(Locale.ROOT) + " "
        if (Regex("\\bnoon\\b|\\bmidday\\b").containsMatchIn(t)) return Triple(12, 0, true)
        if (Regex("\\bmidnight\\b").containsMatchIn(t)) return Triple(0, 0, true)
        val m = Regex("(?:\\bat |\\bfor |\\bby |^|\\s)(\\d{1,2})(?:[:.](\\d{2}))? ?(a\\.?m\\.?|p\\.?m\\.?|in the morning|in the evening|at night|tonight)?(?=\\s|$)")
            .findAll(t).firstOrNull { mm ->
                val hasAt = mm.value.trimStart().let { it.startsWith("at ") || it.startsWith("for ") || it.startsWith("by ") }
                hasAt || mm.groupValues[2].isNotEmpty() || mm.groupValues[3].isNotEmpty()
            } ?: Regex("(?:^|\\s)(\\d{1,2})(?:[:.](\\d{2}))?(?=\\s|$)").findAll(t).toList().singleOrNull()
                ?.takeIf { !Regex("\\d\\s*(min|sec|hour|hr|h\\b|m\\b|s\\b)").containsMatchIn(t) }
            ?: return null
        // "07:00" (zero-padded, 24-hour style, as the AI writes tags) is exact: 7 AM, never 7 PM.
        val padded = m.groupValues[1].length == 2 && m.groupValues[1].startsWith("0") && m.groupValues[2].isNotEmpty() && m.groupValues[3].isEmpty()
        if (padded) return finish(m.groupValues[1].toInt(), m.groupValues[2], "am", t, now)
        return finish(m.groupValues[1].toInt(), m.groupValues[2], m.groupValues[3], t, now)
    }

    private fun finish(h0: Int, minS: String, ap0: String, t: String, now: Calendar): Triple<Int, Int, Boolean>? {
        var h = h0
        val min = minS.ifEmpty { "0" }.toInt()
        if (h > 23 || min > 59) return null
        val ap = ap0.ifEmpty {
            when {
                Regex("\\b(pm|p\\.m\\.|evening|tonight|night)\\b").containsMatchIn(t) -> "pm"
                Regex("\\b(am|a\\.m\\.|morning)\\b").containsMatchIn(t) -> "am"
                else -> ""
            }
        }
        val pm = ap.startsWith("p") || ap.contains("evening") || ap.contains("night")
        val am = ap.startsWith("a") || ap.contains("morning")
        if (pm && h in 1..11) h += 12
        if (am && h == 12) h = 0
        if (am || pm || h >= 13 || h == 0) return Triple(h, min, true)
        // Ambiguous 1-12 without am/pm: the next time the clock shows it.
        val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val a = (h % 12) * 60 + min; val b = a + 12 * 60
        val wake = Regex("\\bwake\\b|\\bmorning\\b").containsMatchIn(t)
        val pick = when {
            wake -> a                                  // "wake me up at 7" -> 7 AM
            Regex("\\btomorrow\\b").containsMatchIn(t) && h in 4..11 -> a   // "tomorrow 6" -> 6 AM
            a > nowMin -> a
            b > nowMin -> b
            else -> a
        }
        return Triple(pick / 60, pick % 60, false)
    }
}
