package com.combo.monkeycompanion.v5

/** v5 weather reactions from Open-Meteo "current" (WMO weather code, °C). Refreshed at most every 3 h. Pure. */
object WeatherMood {
    enum class Mood(val prop: String) { RAIN("umbrella"), STORM("umbrella"), HOT("fan"), COLD("shawl"), CLOUDY("cloud"), FOG("cloud"), CLEAR("shades"), NONE("") }
    const val REFRESH_MS = 3 * 3600_000L

    fun mood(code: Int, tempC: Float): Mood = when {
        code in 95..99 -> Mood.STORM
        code in 51..67 || code in 80..82 -> Mood.RAIN
        code in 71..77 || code in 85..86 -> Mood.COLD
        tempC >= 35f -> Mood.HOT
        tempC <= 16f -> Mood.COLD
        code == 45 || code == 48 -> Mood.FOG
        code in 2..3 -> Mood.CLOUDY
        code in 0..1 -> Mood.CLEAR
        else -> Mood.NONE
    }

    fun due(nowMs: Long, lastFetchMs: Long) = nowMs - lastFetchMs >= REFRESH_MS

    /** https://api.open-meteo.com/v1/forecast?latitude=..&longitude=..&current=temperature_2m,weather_code (rounded, coarse). */
    fun url(lat: Double, lon: Double) = "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code&timezone=auto".format(java.util.Locale.US, lat, lon)

    fun parse(json: String): Pair<Int, Float>? {
        val cur = Regex("\"current\"\\s*:\\s*\\{([^}]*)\\}").find(json)?.groupValues?.get(1) ?: return null
        val code = Regex("\"weather_code\"\\s*:\\s*(\\d+)").find(cur)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val t = Regex("\"temperature_2m\"\\s*:\\s*(-?[\\d.]+)").find(cur)?.groupValues?.get(1)?.toFloatOrNull() ?: return null
        return code to t
    }

    fun line(m: Mood, tempC: Float, characterId: String): String? = when (m) {
        Mood.RAIN -> if (characterId == "mask") "Rain! Good thing I always carry my umbrella ☔" else "Mazhai! Umbrella out ☔ Bajji weather!"
        Mood.STORM -> "Thunder! ⛈️ I'm not scared. You're scared."
        Mood.HOT -> "${tempC.toInt()}°C?! Fanning myself… tender coconut please 🥥🌞"
        Mood.COLD -> "Brrr ${tempC.toInt()}°C! Shawl on 🧣"
        Mood.CLOUDY -> "Cloudy day… perfect for a nap ☁️"
        Mood.FOG -> "So foggy, I can't see my own tail 🌫️"
        Mood.CLEAR -> if (characterId == "superboy") "Clear skies — flying weather! ☀️" else null
        Mood.NONE -> null
    }
}
