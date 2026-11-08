package com.combo.monkeycompanion.ai

import com.combo.monkeycompanion.character.CharacterRegistry
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Public gateway only: the service's Gemini key is NEVER shipped in an APK. */
class GatewayBrain(
    private val userId: String,
    private val enabled: () -> Boolean,
    private val bodySense: () -> String = { "" },
    private val endpoint: String = URL,
    private val client: OkHttpClient = CLIENT,
) : Brain {
    override val label = "Set-Tai Cloud (40 a day)"
    override val supportsTools = false
    @Volatile private var active: Call? = null
    @Volatile private var cancelled = false
    @Volatile var leftToday: Int? = null; private set
    /** v10.39.5: a special job for the gateway ("show" = write show story lines: no character voice, longer reply). */
    @Volatile var task: String? = null
    override fun cancel() { cancelled = true; active?.cancel() }
    override fun close() = cancel()

    internal fun payload(system: String, history: List<ChatMsg>, user: String): String {
        val turns = history.takeLast(6).toMutableList()
        var sys = system.take(4500)
        var message = user.take(1800)
        val body = runCatching { bodySense() }.getOrDefault("").take(600)
        while (true) {
            val json = JSONObject().put("userId", userId).put("characterId", CharacterRegistry.current.id)
                .put("system", sys).put("bodySense", body).put("message", message)
                .apply { task?.let { put("task", it) } }
                .put("history", JSONArray(turns.map { JSONObject().put("fromUser", it.fromUser).put("text", it.text.take(400)) }))
                .toString()
            if (json.toByteArray(Charsets.UTF_8).size <= 8192) return json
            // Fit BYTES, not just characters (Tamil/emoji take multiple UTF-8 bytes).
            when {
                turns.isNotEmpty() -> turns.removeAt(0)
                sys.length > 800 -> sys = sys.take(sys.length - 200)
                message.length > 200 -> message = message.take(message.length - 100)
                else -> throw BrainException("That message is too long. Try a shorter one.")
            }
        }
    }

    override fun chat(system: String, history: List<ChatMsg>, user: String, tools: ToolHost?, onText: (String) -> Unit): String {
        if (!enabled()) return CannedBrain().chat(system, history, user, null, onText)
        cancelled = false
        try {
            val request = Request.Builder().url(endpoint).post(payload(system, history, user).toRequestBody("application/json".toMediaType())).build()
            val call = client.newCall(request); active = call
            // Give Brain can be switched off while this job is queued/building its prompt.
            if (!enabled() || cancelled) { call.cancel(); throw BrainException("Chat cancelled.") }
            call.execute().use { response ->
                val result = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                result?.optInt("leftToday", -1)?.takeIf { it >= 0 }?.let { leftToday = it }
                if (response.code == 429) {
                    val text = "Today's cloud chats are used up. Local jokes, dance and phone commands still work. Try again after midnight UTC (5:30 AM in India)."
                    onText(text); return text
                }
                if (!response.isSuccessful) throw BrainException("Cloud unavailable.")
                val raw = result?.optString("text")?.trim().orEmpty()
                val text = raw.substring(0, raw.offsetByCodePoints(0, minOf(if (task == "show") 1400 else 560, raw.codePointCount(0, raw.length))))
                if (text.isBlank()) throw BrainException("Empty cloud reply.")
                if (!enabled() || cancelled) throw BrainException("Chat cancelled.")
                onText(text); return text
            }
        } catch (_: Exception) {
            if (cancelled || !enabled()) throw BrainException("Chat cancelled.")
            // No raw server exceptions, URLs or credentials in bubbles/logs.
            return CannedBrain("My cloud connection is resting. Local jokes, dance and phone commands still work.")
                .chat(system, history, user, null, onText)
        } finally { active = null }
    }
    companion object {
        const val URL = "https://settai-brain.settai-companion.workers.dev/v1/chat"
        private val CLIENT = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS).writeTimeout(6, TimeUnit.SECONDS)
            .callTimeout(18, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
    }
}
