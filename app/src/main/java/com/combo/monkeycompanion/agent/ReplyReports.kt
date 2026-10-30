package com.combo.monkeycompanion.agent

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * v4 "Report this reply": AI replies the user flags as wrong, rude or unsafe. Saved only in a file on
 * this phone (nothing is uploaded); the user can share one from the Reported replies screen if they choose.
 */
data class ReplyReport(val id: Int, val at: Long, val reply: String, val prompt: String, val brain: String)

class ReplyReportStore(private val file: File) {
    @Synchronized fun all(): List<ReplyReport> = runCatching {
        val a = JSONArray(file.readText())
        (0 until a.length()).map { i -> a.getJSONObject(i).let { o ->
            ReplyReport(o.getInt("id"), o.getLong("at"), o.getString("reply"), o.optString("prompt"), o.optString("brain")) } }
    }.getOrDefault(emptyList())

    @Synchronized fun add(reply: String, prompt: String, brain: String, now: Long = System.currentTimeMillis()): ReplyReport? {
        val r = Otp.mask(reply.trim()).take(2000); if (r.isEmpty()) return null
        val cur = all()
        cur.firstOrNull { it.reply == r }?.let { return it }          // same reply reported twice: keep one
        val rep = ReplyReport((cur.maxOfOrNull { it.id } ?: 0) + 1, now, r, Otp.mask(prompt.trim()).take(1000), brain)
        save((cur + rep).takeLast(MAX))
        return rep
    }

    @Synchronized fun remove(id: Int) = save(all().filter { it.id != id })
    @Synchronized fun clear() = save(emptyList())

    private fun save(l: List<ReplyReport>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("at", it.at).put("reply", it.reply).put("prompt", it.prompt).put("brain", it.brain)) }
        runCatching { file.writeText(a.toString()) }
    }

    companion object {
        const val MAX = 100
        fun shareText(r: ReplyReport) = "Settai reply report\nBrain: ${r.brain}\n\nI asked:\n${r.prompt.ifBlank { "(not recorded)" }}\n\nSettai said:\n${r.reply}"
    }
}

object ReplyReports {
    @Volatile private var store: ReplyReportStore? = null
    fun store(ctx: android.content.Context): ReplyReportStore =
        store ?: synchronized(this) { store ?: ReplyReportStore(File(ctx.filesDir, "settai_reports.json")).also { store = it } }

    /** Report [reply] with the user message just before it in the chat history. */
    fun report(ctx: android.content.Context, reply: String): ReplyReport? {
        val hist = runCatching { com.combo.monkeycompanion.ai.ChatHub.memory.all() }.getOrDefault(emptyList())
        val i = hist.indexOfLast { !it.fromUser && it.text.trim() == reply.trim() }
        val prompt = (if (i > 0) hist.subList(0, i) else hist).lastOrNull { it.fromUser }?.text ?: ""
        val brain = runCatching { com.combo.monkeycompanion.ai.ChatHub.settings.provider.name }.getOrDefault("?")
        return store(ctx).add(reply, prompt, brain)
    }
}
