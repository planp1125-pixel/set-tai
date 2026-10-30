package com.combo.monkeycompanion.world

import java.util.Locale

/**
 * Small JSON persistence for [WorldClock] & [WorldState] (pure Kotlin so it runs identically on Android,
 * desktop and plain JUnit tests without Android stubbed org.json).
 */
object WorldStore {
    private fun f(v: Float) = "%.3f".format(Locale.ROOT, v)
    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\t", " ")
    private fun unesc(s: String) = s.replace("\\\"", "\"").replace("\\\\", "\\")

    fun encodeJson(clock: WorldClock, neighbourMode: String = "DUO"): String {
        val st = clock.state
        val traces = clock.recentTraces.take(8).joinToString(",") { t ->
            """{"ts":${t.ts},"away":${t.awayMinutes},"line":"${esc(t.line)}","subj":"${esc(t.diarySubject)}"}"""
        }
        return buildString {
            append("{")
            append(""""energy":${f(st.energy)},""")
            append(""""heat":${f(st.heat)},""")
            append(""""busyness":${f(st.busyness)},""")
            append(""""quiet":${f(st.quiet)},""")
            append(""""connectivity":${f(st.connectivity)},""")
            append(""""clutter":${f(st.clutter)},""")
            append(""""visitorPresence":${f(st.visitorPresence)},""")
            append(""""visitorTrust":${f(st.visitorTrust)},""")
            append(""""timeOfDay":"${st.timeOfDay.name}",""")
            append(""""batteryPct":${st.batteryPct},""")
            append(""""charging":${st.charging},""")
            append(""""tempC":${f(st.tempC)},""")
            append(""""currentPlacePkg":"${esc(st.currentPlacePkg)}",""")
            append(""""visitorsWaiting":${st.visitorsWaiting},""")
            append(""""unlocksToday":${st.unlocksToday},""")
            append(""""lastTickAt":${clock.lastTickAt},""")
            append(""""screenOffAt":${clock.screenOffAt},""")
            append(""""neighbourMode":"${esc(neighbourMode)}",""")
            append(""""traces":[$traces]""")
            append("}")
        }
    }

    private fun num(json: String, key: String): String? =
        Regex(""""$key"\s*:\s*([-0-9.]+)""").find(json)?.groupValues?.get(1)

    private fun bool(json: String, key: String): Boolean? =
        Regex(""""$key"\s*:\s*(true|false)""").find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull()

    private fun str(json: String, key: String): String? =
        Regex(""""$key"\s*:\s*"((?:\\.|[^"\\])*)"""").find(json)?.groupValues?.get(1)?.let { unesc(it) }

    /** Restores [clock] from a JSON string; returns the stored `neighbourMode` (default `"DUO"`). */
    fun decodeJson(clock: WorldClock, json: String?): String {
        if (json.isNullOrBlank()) return "DUO"
        val st = clock.state
        num(json, "energy")?.toFloatOrNull()?.let { st.energy = it }
        num(json, "heat")?.toFloatOrNull()?.let { st.heat = it }
        num(json, "busyness")?.toFloatOrNull()?.let { st.busyness = it }
        num(json, "quiet")?.toFloatOrNull()?.let { st.quiet = it }
        num(json, "connectivity")?.toFloatOrNull()?.let { st.connectivity = it }
        num(json, "clutter")?.toFloatOrNull()?.let { st.clutter = it }
        num(json, "visitorPresence")?.toFloatOrNull()?.let { st.visitorPresence = it }
        num(json, "visitorTrust")?.toFloatOrNull()?.let { st.visitorTrust = it }
        str(json, "timeOfDay")?.let { runCatching { TimeOfDay.valueOf(it) }.getOrNull()?.let { t -> st.timeOfDay = t } }
        num(json, "batteryPct")?.toIntOrNull()?.let { st.batteryPct = it }
        bool(json, "charging")?.let { st.charging = it }
        num(json, "tempC")?.toFloatOrNull()?.let { st.tempC = it }
        str(json, "currentPlacePkg")?.let { st.currentPlacePkg = it }
        num(json, "visitorsWaiting")?.toIntOrNull()?.let { st.visitorsWaiting = it }
        num(json, "unlocksToday")?.toIntOrNull()?.let { st.unlocksToday = it }
        num(json, "lastTickAt")?.toLongOrNull()?.let { clock.lastTickAt = it }
        num(json, "screenOffAt")?.toLongOrNull()?.let { clock.screenOffAt = it }
        st.clamp()

        clock.recentTraces.clear()
        val arrMatch = Regex(""""traces"\s*:\s*\[(.*)]""").find(json)?.groupValues?.get(1).orEmpty()
        Regex("""\{[^{}]*}""").findAll(arrMatch).forEach { m ->
            val obj = m.value
            val ts = num(obj, "ts")?.toLongOrNull() ?: 0L
            val away = num(obj, "away")?.toIntOrNull() ?: 0
            val line = str(obj, "line").orEmpty()
            val subj = str(obj, "subj").orEmpty()
            if (line.isNotEmpty()) clock.recentTraces.add(NightTrace(ts, away, line, subj))
        }
        return str(json, "neighbourMode") ?: "DUO"
    }
}
