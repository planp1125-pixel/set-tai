package com.combo.monkeycompanion.ai

import org.json.JSONObject

/** v10.13.2: parse providers' "list models" replies and pick a sensible default (newest Flash for Gemini). */
object ModelList {
    /** Gemini GET /v1beta/models: only models that support generateContent, without the "models/" prefix. */
    fun parseGemini(body: String): List<String> {
        val arr = JSONObject(body).optJSONArray("models") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val m = o.optJSONArray("supportedGenerationMethods")
            val ok = m != null && (0 until m.length()).any { m.getString(it) == "generateContent" }
            if (ok) o.optString("name").removePrefix("models/").takeIf { it.isNotBlank() } else null
        }
    }

    /** OpenAI-style GET /models (OpenAI, Groq, OpenRouter, xAI, Anthropic): data[].id */
    fun parseOpenAi(body: String): List<String> {
        val arr = JSONObject(body).optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.getJSONObject(it).optString("id").takeIf { s -> s.isNotBlank() } }
    }

    private val SPECIAL = Regex("tts|image|live|embed|audio|transcribe|translate|banana|veo|imagen|aqa|robotics|computer|whisper|dall-e|moderation|realtime|search|guard")

    /** Version number inside an id, e.g. "gemini-3.8-flash" -> 3.8 (0 if none). */
    fun version(id: String): Double = Regex("(\\d+(?:\\.\\d+)?)").find(id.substringAfter("gemini-", id))?.value?.toDoubleOrNull() ?: 0.0

    /** Chat-capable models first, newest version first. */
    fun sortForChat(ids: List<String>): List<String> =
        ids.distinct().filterNot { SPECIAL.containsMatchIn(it) }.sortedWith(compareByDescending<String> { version(it) }.thenBy { it.contains("preview") || it.contains("exp") }.thenBy { it.length })

    /** Newest stable "flash" (not lite) if there is one, else the first chat model. */
    fun pickDefault(ids: List<String>): String? {
        val chat = sortForChat(ids)
        return chat.firstOrNull { it.contains("flash") && !it.contains("lite") && !it.contains("preview") && !it.contains("latest") }
            ?: chat.firstOrNull { it.contains("flash") }
            ?: chat.firstOrNull()
    }
}
