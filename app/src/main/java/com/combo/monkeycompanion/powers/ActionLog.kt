package com.combo.monkeycompanion.powers

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "Recent actions" log (last 60 entries) shown in Settings, so failures are never silent. */
object ActionLog {
    private const val PREFS = "settai_actions"
    private const val MAX = 60

    data class Entry(val at: Long, val what: String, val result: String, val ok: Boolean)

    @Synchronized fun add(ctx: Context, what: String, result: String, ok: Boolean = true) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = runCatching { JSONArray(p.getString("log", "[]")) }.getOrDefault(JSONArray())
        arr.put(JSONObject().put("at", System.currentTimeMillis()).put("what", what).put("result", result).put("ok", ok))
        val keep = JSONArray(); val from = maxOf(0, arr.length() - MAX)
        for (i in from until arr.length()) keep.put(arr.get(i))
        p.edit().putString("log", keep.toString()).apply()
        android.util.Log.i("SettaiActions", "$what -> $result")
    }

    @Synchronized fun all(ctx: Context): List<Entry> {
        val arr = runCatching { JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("log", "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { Entry(it.optLong("at"), it.optString("what"), it.optString("result"), it.optBoolean("ok", true)) }
    }

    fun clear(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("log").apply()

    fun format(ctx: Context): String {
        val f = SimpleDateFormat("d MMM HH:mm:ss", Locale.getDefault())
        val l = all(ctx)
        if (l.isEmpty()) return "Nothing yet."
        return l.asReversed().joinToString("\n\n") { "${f.format(Date(it.at))}  ${if (it.ok) "✓" else "✗"}  ${it.what}\n→ ${it.result}" }
    }
}
