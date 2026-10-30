package com.combo.monkeycompanion.agent

import android.content.Context
import com.combo.monkeycompanion.brain.CareTools
import java.time.LocalDate
import java.time.LocalTime

/** v10.13 persisted ⏰ Tools (survive restarts, kills and skits/shows: everything is timestamps). */
object ToolStore {
    private const val PREFS = "settai_tools"
    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(c: Context, t: CareTools.Tool): CareTools.State {
        val p = p(c); val k = t.id
        return CareTools.State(p.getBoolean("$k.on", false), p.getInt("$k.v", t.defaultValue), p.getLong("$k.last", 0L), p.getLong("$k.at", 0L), p.getString("$k.text", "") ?: "")
    }
    fun put(c: Context, t: CareTools.Tool, s: CareTools.State) {
        val k = t.id
        p(c).edit().putBoolean("$k.on", s.on).putInt("$k.v", s.value).putLong("$k.last", s.lastFiredMs).putLong("$k.at", s.atMs).putString("$k.text", s.text).apply()
    }
    private fun lastDay(c: Context, t: CareTools.Tool) = p(c).getLong(t.id + ".day", -1L)

    /** Lines due now (and marks them fired). */
    fun collectDue(c: Context, now: Long = System.currentTimeMillis()): List<String> {
        val out = ArrayList<String>()
        val today = LocalDate.now().toEpochDay(); val t = LocalTime.now(); val mod = t.hour * 60 + t.minute
        for (tool in CareTools.Tool.entries) {
            val s = get(c, tool)
            if (CareTools.due(tool, s, now, mod, today, lastDay(c, tool))) {
                out += CareTools.line(tool, s)
                put(c, tool, CareTools.fired(tool, s, now))
                p(c).edit().putLong(tool.id + ".day", today).apply()
            }
        }
        return out
    }
}
