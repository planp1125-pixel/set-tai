package com.combo.monkeycompanion.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File


/** Short, persisted conversation memory (last N turns) + the visible chat log. */
class ChatMemory(ctx: Context) {
    private val file = File(ctx.filesDir, "chat_history.json")
    private val log = ArrayList<ChatMsg>()

    init { load() }

    @Synchronized fun all(): List<ChatMsg> = ArrayList(log)

    /** Messages fed to the model: last [turns] user/monkey pairs, without UI notes. */
    @Synchronized fun recent(turns: Int): List<ChatMsg> {
        if (turns <= 0) return emptyList()
        val real = log.filter { !it.note }
        return real.takeLast(turns * 2)
    }

    @Synchronized fun add(m: ChatMsg) {
        log.add(m)
        while (log.size > 200) log.removeAt(0)
        save()
    }

    /** Replace the text of the last monkey message (streaming). Not persisted until [commit]. */
    @Synchronized fun updateLast(text: String) {
        val i = log.indexOfLast { !it.fromUser && !it.note }
        if (i >= 0) log[i] = log[i].copy(text = text)
    }

    @Synchronized fun commit() = save()

    @Synchronized fun clear() { log.clear(); save() }

    private fun load() {
        runCatching {
            if (!file.exists()) return
            val a = JSONArray(file.readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                log.add(ChatMsg(o.getBoolean("u"), o.getString("t"), o.optLong("ts"), o.optBoolean("n")))
            }
        }
    }

    private fun save() {
        runCatching {
            val a = JSONArray()
            for (m in log) a.put(JSONObject().put("u", m.fromUser).put("t", m.text).put("ts", m.time).put("n", m.note))
            file.writeText(a.toString())
        }
    }
}
