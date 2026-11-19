package com.combo.monkeycompanion.ai

import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * v10.46 "Sign in with ChatGPT": the user's own ChatGPT Plus / Pro plan is the brain.
 * OpenAI Responses API (https://api.openai.com/v1/responses) with the user's OAuth access token,
 * `store: false` + `stream: true` (both required for plan usage), the persona as `instructions`,
 * the chat history in `input`, and the phone powers / body actions as function tools.
 * No API key: [tokens] gives a fresh access token (it refreshes and signs out by itself).
 */
class ChatGptBrain(
    private val tokens: Tokens,
    /** The model the user picked ("" = choose one from the account's list). */
    private val preferredModel: String = "",
    /** OpenAI's API (tests point it at a local server). */
    private val api: String = API,
) : Brain {
    /** Where the access token comes from (the app's ChatGptAuth). */
    interface Tokens {
        /** A valid access token (refreshed if needed), or a [BrainException] when the user must sign in again. */
        fun accessToken(): String
        /** The server said this token is no longer valid: refresh once; false = sign in again. */
        fun onUnauthorized(): Boolean
        val accountLabel: String
    }

    override val label: String get() = "ChatGPT (${tokens.accountLabel})"
    override val supportsTools = true
    override val supportsAttachments = true
    @Volatile private var call: Call? = null
    @Volatile private var model: String? = preferredModel.takeIf { it.isNotBlank() }

    override fun cancel() { call?.cancel() }

    override fun chat(system: String, history: List<ChatMsg>, user: String, tools: ToolHost?, onText: (String) -> Unit): String =
        chat(system, history, user, tools, emptyList(), onText)

    override fun chat(system: String, history: List<ChatMsg>, user: String, tools: ToolHost?, attachments: List<Attachment>, onText: (String) -> Unit): String {
        val input = JSONArray()
        for (m in history) input.put(JSONObject().put("role", if (m.fromUser) "user" else "assistant").put("content", m.text))
        input.put(JSONObject().put("role", "user").put("content", userContent(user, attachments)))
        val toolArr = tools?.specs?.takeIf { it.isNotEmpty() }?.let { specs ->
            JSONArray().apply { for (s in specs) put(JSONObject().put("type", "function").put("name", s.name).put("description", s.description).put("parameters", s.parameters)) }
        }
        var shown = ""
        val rounds = (tools?.maxRounds ?: 1).coerceIn(1, 24)
        repeat(rounds) { round ->
            val body = JSONObject().put("model", pickModel()).put("instructions", system).put("input", input)
                .put("store", false).put("stream", true)
            if (toolArr != null) body.put("tools", toolArr)
            val turn = streamWithAuth(body) { delta -> shown += delta; onText(cleanReply(shown)) }
            if (turn.calls.isEmpty() || tools == null) return cleanReply(shown)
            // the model wants tools: give it back its calls and their results (no stored state: everything goes in input)
            for (c in turn.calls) {
                input.put(JSONObject().put("type", "function_call").put("call_id", c.callId).put("name", c.name).put("arguments", c.args.ifBlank { "{}" }))
                val result = runCatching { tools.call(c.name, c.args.ifBlank { "{}" }) }.getOrElse { "error: ${it.message}" }
                input.put(JSONObject().put("type", "function_call_output").put("call_id", c.callId).put("output", result))
            }
            if (shown.isNotEmpty() && !shown.endsWith(" ")) shown += " "
            if (round == rounds - 1) return cleanReply(shown)
        }
        return cleanReply(shown)
    }

    /** v10.48: plain text, or (with 📎) a list of input_text / input_image / input_file parts. */
    private fun userContent(user: String, attachments: List<Attachment>): Any {
        if (attachments.isEmpty()) return user
        val parts = JSONArray()
        var text = user
        for (a in attachments) if (a.kind == Attachment.Kind.TEXT && a.text != null)
            text += "\n\n[File: ${a.name}]\n" + a.text.take(Attachment.MAX_TEXT_CHARS)
        parts.put(JSONObject().put("type", "input_text").put("text", text))
        for (a in attachments) when (a.kind) {
            Attachment.Kind.IMAGE -> a.dataUrl?.let { parts.put(JSONObject().put("type", "input_image").put("image_url", it).put("detail", "auto")) }
            Attachment.Kind.PDF -> a.dataUrl?.let { parts.put(JSONObject().put("type", "input_file").put("filename", a.name).put("file_data", it)) }
            Attachment.Kind.TEXT -> {}
        }
        return parts
    }

    /** The account's models; a small fast one is best for a chatty character. */
    fun models(): List<Pair<String, String>> {
        val r = authed { tok -> Request.Builder().url("$api/models").header("Authorization", "Bearer $tok").get().build() }
        r.use {
            if (!it.isSuccessful) throw fail(it.code, it.body?.string().orEmpty())
            val o = JSONObject(it.body!!.string())
            val arr = o.optJSONArray("models") ?: o.optJSONArray("data") ?: JSONArray()
            val out = ArrayList<Pair<String, String>>()
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                if (m.has("visibility") && m.optString("visibility") != "list") continue
                val slug = m.optString("slug").ifBlank { m.optString("id") }
                if (slug.isNotBlank()) out.add(slug to m.optString("display_name").ifBlank { slug })
            }
            return out
        }
    }

    private fun pickModel(): String {
        model?.let { return it }
        val list = runCatching { models() }.getOrElse { if (it is BrainException) throw it else emptyList() }
        val slug = chooseModel(list.map { it.first }) ?: throw BrainException("Your ChatGPT account has no models I can use.")
        model = slug
        return slug
    }

    private class Call1(val callId: String, val name: String, val args: String)
    private class Turn(val text: String, val calls: List<Call1>)

    /** Runs a request with the access token; on 401 refreshes once and tries again. */
    private fun authed(build: (String) -> Request): okhttp3.Response {
        var tok = tokens.accessToken()
        val c1 = Http.client.newCall(build(tok)); call = c1
        val r = c1.execute()
        if (r.code != 401) return r
        r.close()
        if (!tokens.onUnauthorized()) throw BrainException(SIGN_IN_AGAIN)
        tok = tokens.accessToken()
        val c2 = Http.client.newCall(build(tok)); call = c2
        return c2.execute()
    }

    private fun streamWithAuth(body: JSONObject, onDelta: (String) -> Unit): Turn {
        val text = StringBuilder()
        val calls = ArrayList<Call1>()
        var completed = false
        authed { tok ->
            Request.Builder().url("$api/responses").header("Authorization", "Bearer $tok").header("Accept", "text/event-stream")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        }.use { r ->
            if (!r.isSuccessful) throw fail(r.code, r.body?.string().orEmpty())
            val src = r.body!!.source()
            while (true) {
                val line = src.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.substring(5).trim()
                if (data.isEmpty() || data == "[DONE]") continue
                val o = runCatching { JSONObject(data) }.getOrNull() ?: continue
                when (o.optString("type")) {
                    "response.output_text.delta" -> { val d = o.optString("delta"); if (d.isNotEmpty()) { text.append(d); onDelta(d) } }
                    "response.output_item.done" -> o.optJSONObject("item")?.takeIf { it.optString("type") == "function_call" }?.let { it2 ->
                        calls.add(Call1(it2.optString("call_id").ifBlank { "call_${calls.size}" }, it2.optString("name"), it2.optString("arguments")))
                    }
                    "response.completed" -> completed = true
                    "response.failed" -> {
                        val e = o.optJSONObject("response")?.optJSONObject("error")
                        throw BrainException(friendly(e?.optString("code").orEmpty(), e?.optString("message").orEmpty()))
                    }
                    "response.incomplete" -> completed = true   // cut short (length): keep what came
                    "error" -> throw BrainException(friendly(o.optString("code"), o.optString("message")))
                }
            }
        }
        if (!completed && text.isEmpty() && calls.isEmpty()) throw BrainException("ChatGPT stopped before answering. Try again.")
        return Turn(text.toString(), calls.filter { it.name.isNotBlank() })
    }

    companion object {
        const val API = "https://api.openai.com/v1"
        const val SIGN_IN_AGAIN = "Please sign in to ChatGPT again (Give me a brain › ChatGPT)."

        /** Prefer a small fast model ("mini" / "nano"), then anything with "chat", then the first. */
        fun chooseModel(slugs: List<String>): String? =
            slugs.firstOrNull { it.contains("mini") } ?: slugs.firstOrNull { it.contains("nano") }
                ?: slugs.firstOrNull { it.contains("chat") } ?: slugs.firstOrNull()

        /** OpenAI's plan-usage error codes in plain words. */
        fun friendly(code: String, message: String): String = when (code) {
            "subscription_sharing_usage_limit_exceeded" -> "My ChatGPT brain needs a rest: Set-Tai's share of your ChatGPT limit is used up for now. You can see it in ChatGPT › Settings › Usage."
            "subscription_sharing_user_not_eligible" -> "This ChatGPT account can't be used in other apps. It needs ChatGPT Plus or Pro."
            "subscription_sharing_usage_unavailable", "subscription_sharing_user_unavailable" -> "ChatGPT is busy right now. Try again in a moment."
            "subscription_sharing_invalid_user", "invalid_grant", "refresh_token_expired", "refresh_token_invalidated" -> SIGN_IN_AGAIN
            else -> if (message.isNotBlank()) "ChatGPT: ${message.take(160)}" else "ChatGPT had a problem ($code)."
        }

        private fun fail(status: Int, body: String): BrainException {
            val e = runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()
            val code = e?.optString("code").orEmpty()
            return BrainException(when {
                code.isNotBlank() -> friendly(code, e?.optString("message").orEmpty())
                status == 401 -> SIGN_IN_AGAIN
                status == 403 -> friendly("subscription_sharing_user_not_eligible", "")
                status == 429 -> friendly("subscription_sharing_usage_limit_exceeded", "")
                else -> "ChatGPT error (HTTP $status)"
            })
        }
    }
}
