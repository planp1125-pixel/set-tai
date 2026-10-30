package com.combo.monkeycompanion.agent

import com.combo.monkeycompanion.ai.Http
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/** v4 web brain: keyless lookups (DuckDuckGo Instant Answer + Wikipedia, or the user's own search URL) and Open-Meteo weather. */
object Web {
    private const val UA = "Settai/4.0 (Android phone companion)"

    fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", UA).header("Accept", "application/json, text/html;q=0.8").build()
        Http.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
            return r.body?.string().orEmpty().take(400_000)
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Plain text from HTML (rough, for the user's own search URL). */
    fun stripHtml(h: String): String = h.replace(Regex("(?is)<(script|style|noscript)[^>]*>.*?</\\1>"), " ")
        .replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace(Regex("\\s+"), " ").trim()

    /** DuckDuckGo Instant Answer JSON -> short text (or null). */
    fun parseDdg(json: String): String? = runCatching {
        val o = JSONObject(json)
        val parts = listOf(o.optString("Answer"), o.optString("AbstractText"), o.optString("Definition")).filter { it.isNotBlank() }
        if (parts.isNotEmpty()) return parts.joinToString(" ") + o.optString("AbstractURL").let { if (it.isNotBlank()) " (source: $it)" else "" }
        val rel = o.optJSONArray("RelatedTopics") ?: return null
        (0 until minOf(3, rel.length())).mapNotNull { rel.optJSONObject(it)?.optString("Text")?.takeIf { t -> t.isNotBlank() } }.joinToString(" | ").ifBlank { null }
    }.getOrNull()

    fun lookup(query: String, template: String): String {
        val q = query.trim()
        if (template.isNotBlank()) {
            val body = get(template.replace("{q}", enc(q)))
            val text = if (body.trimStart().startsWith("{") || body.trimStart().startsWith("[")) body else stripHtml(body)
            return "Search results for “$q” (from your search URL): " + text.take(1800)
        }
        parseDdg(get("https://api.duckduckgo.com/?q=${enc(q)}&format=json&no_html=1&skip_disambig=1&t=settai"))?.let { return "DuckDuckGo: " + it.take(1200) }
        val s = JSONObject(get("https://en.wikipedia.org/w/rest.php/v1/search/page?q=${enc(q)}&limit=1"))
        val page = s.optJSONArray("pages")?.optJSONObject(0) ?: return "I found nothing on DuckDuckGo or Wikipedia for “$q”. (For news and live results, set a search URL in Phone powers → Web brain.)"
        val sum = JSONObject(get("https://en.wikipedia.org/api/rest_v1/page/summary/" + enc(page.optString("key").ifBlank { page.optString("title") }).replace("+", "%20")))
        return "Wikipedia (${sum.optString("title")}): " + sum.optString("extract").take(1200)
    }

    // ---------------- weather ----------------
    fun weatherText(code: Int): String = when (code) {
        0 -> "clear sky"; 1 -> "mostly clear"; 2 -> "partly cloudy"; 3 -> "cloudy"
        45, 48 -> "foggy"; 51, 53, 55 -> "drizzle"; 56, 57 -> "freezing drizzle"
        61 -> "light rain"; 63 -> "rain"; 65 -> "heavy rain"; 66, 67 -> "freezing rain"
        71, 73, 75, 77 -> "snow"; 80 -> "light showers"; 81 -> "showers"; 82 -> "violent showers"
        85, 86 -> "snow showers"; 95 -> "thunderstorm"; 96, 99 -> "thunderstorm with hail"; else -> "weather code $code"
    }

    /** Open-Meteo forecast JSON -> one friendly line. */
    fun parseWeather(json: String, place: String): String {
        val o = JSONObject(json)
        val c = o.getJSONObject("current")
        val d = o.optJSONObject("daily")
        val t = c.optDouble("temperature_2m"); val feels = c.optDouble("apparent_temperature")
        val sb = StringBuilder("${place.ifBlank { "Here" }}: ${weatherText(c.optInt("weather_code"))}, ${Math.round(t)}°C (feels ${Math.round(feels)}°C)")
        if (c.has("relative_humidity_2m")) sb.append(", humidity ${c.optInt("relative_humidity_2m")}%")
        if (d != null) {
            val hi = d.optJSONArray("temperature_2m_max")?.optDouble(0); val lo = d.optJSONArray("temperature_2m_min")?.optDouble(0)
            val rain = d.optJSONArray("precipitation_probability_max")?.optInt(0)
            if (hi != null && lo != null && !hi.isNaN()) sb.append(". Today ${Math.round(lo)}–${Math.round(hi)}°C")
            if (rain != null) sb.append(", rain chance $rain%")
        }
        return sb.append('.').toString()
    }

    fun geocode(name: String): Triple<Double, Double, String>? {
        val o = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=${enc(name)}&count=1&language=en&format=json"))
        val r = o.optJSONArray("results")?.optJSONObject(0) ?: return null
        return Triple(r.getDouble("latitude"), r.getDouble("longitude"), listOf(r.optString("name"), r.optString("admin1")).filter { it.isNotBlank() }.distinct().joinToString(", "))
    }

    fun weather(lat: Double, lon: Double, place: String): String = parseWeather(get(
        "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,apparent_temperature,weather_code,relative_humidity_2m,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=1".format(java.util.Locale.US, lat, lon)), place)
}
