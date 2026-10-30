package com.combo.settai.desktop

import com.combo.monkeycompanion.ai.Http
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Tiny client for the local Ollama server (model list + pull). Chat goes through its OpenAI-compatible /v1. */
object Ollama {
    private val client = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build()

    fun version(base: String): String? = runCatching {
        client.newCall(Request.Builder().url("$base/api/version").build()).execute().use {
            if (!it.isSuccessful) null else JSONObject(it.body!!.string()).optString("version")
        }
    }.getOrNull()

    /** Installed model names with sizes, e.g. "gemma3:4b" to 3.3e9. Throws when the server is unreachable. */
    fun models(base: String): List<Pair<String, Long>> =
        client.newCall(Request.Builder().url("$base/api/tags").build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val arr = JSONObject(r.body!!.string()).optJSONArray("models") ?: return emptyList()
            (0 until arr.length()).map { arr.getJSONObject(it) }.map { it.optString("name") to it.optLong("size") }.sortedBy { it.first }
        }

    /** Pulls a model, reporting (status, fraction or -1). Blocking. */
    fun pull(base: String, model: String, progress: (String, Float) -> Unit) {
        val body = JSONObject().put("model", model).put("name", model).put("stream", true).toString()
            .toRequestBody("application/json".toMediaType())
        client.newCall(Request.Builder().url("$base/api/pull").post(body).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}: ${r.body?.string()?.take(200)}")
            val src = r.body!!.source()
            while (!src.exhausted()) {
                val line = src.readUtf8Line() ?: break
                if (line.isBlank()) continue
                val o = JSONObject(line)
                if (o.has("error")) error(o.getString("error"))
                val total = o.optLong("total"); val done = o.optLong("completed")
                progress(o.optString("status"), if (total > 0) done.toFloat() / total else -1f)
            }
        }
    }

    init { Http.client } // make sure shared HTTP is initialised
}
