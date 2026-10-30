package com.combo.monkeycompanion.ai

import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Chat Completions API with SSE streaming and function calling.
 * Works for OpenAI, xAI Grok, Google Gemini (OpenAI-compatible endpoint) and custom servers.
 */
class OpenAiCompatBrain(
    override val label: String,
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) : Brain {
    override val supportsTools = true
    @Volatile private var call: Call? = null

    override fun cancel() { call?.cancel() }

    override fun chat(system: String, history: List<ChatMsg>, user: String, tools: ToolHost?, onText: (String) -> Unit): String {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", system))
        for (m in history) messages.put(JSONObject().put("role", if (m.fromUser) "user" else "assistant").put("content", m.text))
        messages.put(JSONObject().put("role", "user").put("content", user))

        val toolArr = tools?.specs?.takeIf { it.isNotEmpty() }?.let { specs ->
            JSONArray().apply {
                for (s in specs) put(
                    JSONObject().put("type", "function").put(
                        "function", JSONObject().put("name", s.name).put("description", s.description).put("parameters", s.parameters)
                    )
                )
            }
        }

        var shown = ""
        val rounds = (tools?.maxRounds ?: 1).coerceIn(1, 24)
        repeat(rounds) { round ->
            val body = JSONObject().put("model", model).put("messages", messages).put("stream", true)
            if (toolArr != null) body.put("tools", toolArr)
            val turn = streamOnce(body) { delta ->
                shown += delta
                onText(cleanReply(shown))
            }
            if (turn.toolCalls.isEmpty() || tools == null) return cleanReply(shown)
            // Model wants tools: record its call, run each, feed results back.
            val calls = JSONArray()
            for (tc in turn.toolCalls) calls.put(
                JSONObject().put("id", tc.id).put("type", "function")
                    .put("function", JSONObject().put("name", tc.name).put("arguments", tc.args.ifBlank { "{}" }))
            )
            messages.put(JSONObject().put("role", "assistant").put("content", turn.text.ifEmpty { JSONObject.NULL }).put("tool_calls", calls))
            for (tc in turn.toolCalls) {
                val result = runCatching { tools.call(tc.name, tc.args.ifBlank { "{}" }) }.getOrElse { "error: ${it.message}" }
                messages.put(JSONObject().put("role", "tool").put("tool_call_id", tc.id).put("content", result))
            }
            if (shown.isNotEmpty() && !shown.endsWith(" ")) shown += " "
            if (round == rounds - 1) return cleanReply(shown)
        }
        return cleanReply(shown)
    }

    private class TC(var id: String = "", var name: String = "", val argsB: StringBuilder = StringBuilder()) {
        val args get() = argsB.toString()
    }
    private class Turn(val text: String, val toolCalls: List<TC>)

    private fun streamOnce(body: JSONObject, onDelta: (String) -> Unit): Turn {
        val rb = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/chat/completions")
            .header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        if (apiKey.isNotBlank()) rb.header("Authorization", "Bearer $apiKey")
        val c = Http.client.newCall(rb.build()); call = c
        val text = StringBuilder()
        val tcs = sortedMapOf<Int, TC>()
        c.execute().use { r ->
            if (!r.isSuccessful) throw Http.fail(r)
            val ct = r.header("Content-Type").orEmpty()
            val src = r.body!!.source()
            if (!ct.contains("event-stream")) {
                // Server ignored stream=true: parse a normal response.
                val o = JSONObject(src.readUtf8())
                val msg = o.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                val content = msg.optString("content").takeIf { it != "null" }.orEmpty()
                if (content.isNotEmpty()) { text.append(content); onDelta(content) }
                msg.optJSONArray("tool_calls")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val t = arr.getJSONObject(i); val f = t.getJSONObject("function")
                        tcs[i] = TC(t.optString("id", "call_$i"), f.getString("name")).also { it.argsB.append(f.optString("arguments")) }
                    }
                }
                return Turn(text.toString(), tcs.values.toList())
            }
            while (true) {
                val line = src.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.substring(5).trim()
                if (data == "[DONE]") break
                if (data.isEmpty()) continue
                val o = runCatching { JSONObject(data) }.getOrNull() ?: continue
                o.optJSONObject("error")?.let { throw BrainException(it.optString("message", "stream error")) }
                val ch = o.optJSONArray("choices")?.optJSONObject(0) ?: continue
                val delta = ch.optJSONObject("delta") ?: continue
                val content = delta.optString("content", "")
                if (content.isNotEmpty() && content != "null") { text.append(content); onDelta(content) }
                delta.optJSONArray("tool_calls")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val t = arr.getJSONObject(i)
                        val idx = t.optInt("index", i)
                        val tc = tcs.getOrPut(idx) { TC() }
                        t.optString("id").takeIf { it.isNotEmpty() && it != "null" }?.let { tc.id = it }
                        t.optJSONObject("function")?.let { f ->
                            f.optString("name").takeIf { it.isNotEmpty() && it != "null" }?.let { tc.name = it }
                            f.optString("arguments").takeIf { it != "null" }?.let { tc.argsB.append(it) }
                        }
                    }
                }
            }
        }
        tcs.forEach { (i, tc) -> if (tc.id.isBlank()) tc.id = "call_$i" }
        return Turn(text.toString(), tcs.values.filter { it.name.isNotBlank() })
    }
}
