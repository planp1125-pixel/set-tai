package com.combo.monkeycompanion.ai

import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Anthropic Messages API with SSE streaming and tool use. */
class AnthropicBrain(private val apiKey: String, private val model: String) : Brain {
    override val label = "Claude ($model)"
    override val supportsTools = true
    @Volatile private var call: Call? = null

    override fun cancel() { call?.cancel() }

    override fun chat(system: String, history: List<ChatMsg>, user: String, tools: ToolHost?, onText: (String) -> Unit): String {
        // Messages must alternate and start with the user.
        val msgs = JSONArray()
        var lastRole = ""
        for (m in history + ChatMsg(true, user)) {
            val role = if (m.fromUser) "user" else "assistant"
            if (msgs.length() == 0 && role != "user") continue
            if (role == lastRole) {
                val prev = msgs.getJSONObject(msgs.length() - 1)
                prev.put("content", prev.getString("content") + "\n" + m.text)
            } else msgs.put(JSONObject().put("role", role).put("content", m.text))
            lastRole = role
        }
        val toolArr = tools?.specs?.takeIf { it.isNotEmpty() }?.let { specs ->
            JSONArray().apply { for (s in specs) put(JSONObject().put("name", s.name).put("description", s.description).put("input_schema", s.parameters)) }
        }
        var shown = ""
        repeat((tools?.maxRounds ?: 1).coerceIn(1, 24)) {
            val body = JSONObject().put("model", model).put("max_tokens", 700).put("system", system)
                .put("messages", msgs).put("stream", true)
            if (toolArr != null) body.put("tools", toolArr)
            val blocks = streamOnce(body) { d -> shown += d; onText(cleanReply(shown)) }
            val uses = blocks.filter { it.type == "tool_use" }
            if (uses.isEmpty() || tools == null) return cleanReply(shown)
            val assistant = JSONArray()
            for (b in blocks) {
                if (b.type == "text" && b.buf.isNotEmpty()) assistant.put(JSONObject().put("type", "text").put("text", b.buf.toString()))
                if (b.type == "tool_use") assistant.put(
                    JSONObject().put("type", "tool_use").put("id", b.id).put("name", b.name)
                        .put("input", runCatching { JSONObject(b.buf.toString().ifBlank { "{}" }) }.getOrElse { JSONObject() })
                )
            }
            msgs.put(JSONObject().put("role", "assistant").put("content", assistant))
            val results = JSONArray()
            for (u in uses) {
                val res = runCatching { tools.call(u.name, u.buf.toString().ifBlank { "{}" }) }.getOrElse { "error: ${it.message}" }
                results.put(JSONObject().put("type", "tool_result").put("tool_use_id", u.id).put("content", res))
            }
            msgs.put(JSONObject().put("role", "user").put("content", results))
            if (shown.isNotEmpty() && !shown.endsWith(" ")) shown += " "
        }
        return cleanReply(shown)
    }

    private class Block(val type: String, val id: String, val name: String) { val buf = StringBuilder() }

    private fun streamOnce(body: JSONObject, onDelta: (String) -> Unit): List<Block> {
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val c = Http.client.newCall(req); call = c
        val blocks = sortedMapOf<Int, Block>()
        c.execute().use { r ->
            if (!r.isSuccessful) throw Http.fail(r)
            val src = r.body!!.source()
            while (true) {
                val line = src.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val o = runCatching { JSONObject(line.substring(5).trim()) }.getOrNull() ?: continue
                when (o.optString("type")) {
                    "content_block_start" -> {
                        val cb = o.getJSONObject("content_block")
                        blocks[o.getInt("index")] = Block(cb.optString("type"), cb.optString("id"), cb.optString("name"))
                    }
                    "content_block_delta" -> {
                        val b = blocks[o.getInt("index")] ?: continue
                        val d = o.getJSONObject("delta")
                        when (d.optString("type")) {
                            "text_delta" -> { val t = d.optString("text"); b.buf.append(t); onDelta(t) }
                            "input_json_delta" -> b.buf.append(d.optString("partial_json"))
                        }
                    }
                    "error" -> throw BrainException(o.optJSONObject("error")?.optString("message") ?: "stream error")
                    "message_stop" -> break
                }
            }
        }
        return blocks.values.toList()
    }
}
