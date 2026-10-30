package com.combo.monkeycompanion.ai

import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Turn a failed HTTP response into a friendly-ish error. */
    fun fail(r: Response): BrainException {
        val body = runCatching { r.body?.string() }.getOrNull().orEmpty()
        val msg = runCatching {
            val o = org.json.JSONObject(body)
            o.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: o.optString("message").takeIf { it.isNotBlank() }
                ?: o.optString("error")
        }.getOrNull().orEmpty().ifBlank { body.take(200) }
        val hint = when (r.code) {
            401, 403 -> "Key rejected (HTTP ${r.code})"
            404 -> "Model or URL not found (HTTP 404)"
            429 -> "Rate limit or quota reached (HTTP 429)"
            else -> "HTTP ${r.code}"
        }
        return BrainException(if (msg.isBlank()) hint else "$hint: $msg")
    }
}
