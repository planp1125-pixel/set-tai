package com.combo.monkeycompanion.world

import java.util.Locale

enum class TimeOfDay(val label: String, val icon: String, val skyColor: Int) {
    DAWN("Dawn", "🌅", 0xFFFFD8A8.toInt()),
    MORNING("Morning", "🌤️", 0xFFFFF3D6.toInt()),
    AFTERNOON("Afternoon", "☀️", 0xFFFFE6B0.toInt()),
    EVENING("Evening", "🌇", 0xFFFFB48A.toInt()),
    NIGHT("Night", "🌙", 0xFF283252.toInt()),
    LATE_NIGHT("Late night", "🌌", 0xFF181E34.toInt());

    val isDark: Boolean get() = this == NIGHT || this == LATE_NIGHT

    companion object {
        fun ofMinute(minuteOfDay: Int): TimeOfDay {
            val h = ((minuteOfDay % 1440) + 1440) % 1440 / 60
            return when (h) {
                in 5..7 -> DAWN
                in 8..11 -> MORNING
                in 12..16 -> AFTERNOON
                in 17..20 -> EVENING
                in 21..23 -> NIGHT
                else -> LATE_NIGHT
            }
        }
    }
}

/**
 * Lasting conditions (all 0f..1f) of the phone-as-a-world:
 *  - [energy]: food/vitality from battery level + charging (low battery = lasting hungry/slow/huddled mood, charging = feeding time)
 *  - [heat]: weather from battery temperature (hot = fanning, seeking shade, Ice power)
 *  - [busyness]: crowd/mail flood from notification arrival rate
 *  - [quiet]: hush level from DND, silent ringer or quiet hours
 *  - [connectivity]: bridge to the outside world (1 = Wi-Fi/data bridge up, 0 = island mode)
 *  - [clutter]: house fullness from storage / memory pressure
 *  - [visitorPresence]: how present the human visitor is (unlocks, touches vs long absence in one app)
 *  - [visitorTrust]: relationship trust (0..1) that grows with gentle touches/chat/feeding and drops with rough shakes/pokes
 */
data class WorldState(
    var energy: Float = 0.78f,
    var heat: Float = 0.15f,
    var busyness: Float = 0.08f,
    var quiet: Float = 0.0f,
    var connectivity: Float = 1.0f,
    var clutter: Float = 0.15f,
    var visitorPresence: Float = 0.65f,
    var timeOfDay: TimeOfDay = TimeOfDay.MORNING,
    var batteryPct: Int = 78,
    var charging: Boolean = false,
    var tempC: Float = 30f,
    var currentPlacePkg: String = "",
    var visitorsWaiting: Int = 0,
    var visitorTrust: Float = 0.70f,
    var unlocksToday: Int = 0,
) {
    fun clamp(): WorldState {
        fun c(v: Float, d: Float = 0.5f) = if (v.isNaN()) d else v.coerceIn(0f, 1f)
        energy = c(energy, 0.75f); heat = c(heat, 0.15f); busyness = c(busyness, 0.1f)
        quiet = c(quiet, 0f); connectivity = c(connectivity, 1f); clutter = c(clutter, 0.15f)
        visitorPresence = c(visitorPresence, 0.6f); visitorTrust = c(visitorTrust, 0.7f)
        batteryPct = batteryPct.coerceIn(0, 100)
        visitorsWaiting = visitorsWaiting.coerceAtLeast(0)
        return this
    }

    /** Lasting world flags that drive posture, movement speed, powers and dialogue. */
    val hungry: Boolean get() = energy < 0.25f && !charging
    val wellFed: Boolean get() = energy >= 0.75f || charging
    val feeding: Boolean get() = charging
    val hot: Boolean get() = heat >= 0.62f
    val flooded: Boolean get() = busyness >= 0.60f || visitorsWaiting >= 4
    val hushed: Boolean get() = quiet >= 0.6f
    val bridgeUp: Boolean get() = connectivity >= 0.5f
    val cramped: Boolean get() = clutter >= 0.72f

    /** Movement speed multiplier from lasting world conditions (hungry/cramped = slower, well-fed = lively). */
    val speedMultiplier: Float get() = when {
        hungry -> 0.66f
        cramped -> 0.78f
        energy > 0.82f && !hushed -> 1.08f
        else -> 1.0f
    }

    val currentPlace: WorldPlaces.Place get() = WorldPlaces.of(currentPlacePkg)

    private fun f(v: Float) = "%.2f".format(Locale.ROOT, v)

    /**
     * One-line world summary injected into ChatHub prompts, e.g.:
     * "World: Night, energy low (hungry, 18%), warm weather, 4 visitors waiting at the WhatsApp market, bridge up, visitor trust 0.82"
     */
    fun promptLine(): String {
        val eStr = when {
            charging -> "feeding on current ($batteryPct%, energy ${f(energy)})"
            hungry -> "energy low (hungry, $batteryPct%)"
            energy >= 0.75f -> "energy full (well-fed, $batteryPct%)"
            else -> "energy steady ($batteryPct%)"
        }
        val wStr = when {
            heat >= 0.75f -> "sweltering weather (${tempC.toInt()}°C)"
            heat >= 0.45f -> "warm weather (${tempC.toInt()}°C)"
            else -> "breezy weather (${tempC.toInt()}°C)"
        }
        val pl = currentPlace
        val vStr = when {
            visitorsWaiting > 0 -> "$visitorsWaiting visitor${if (visitorsWaiting == 1) "" else "s"} waiting at ${pl.worldName}"
            pl.kind != WorldPlaces.Kind.HOME_CLEARING -> "inside ${pl.worldName}"
            else -> "in ${pl.worldName}"
        }
        val bStr = if (bridgeUp) "bridge up" else "bridge down (offline island)"
        val qStr = if (hushed) ", quiet hours (whispering)" else ""
        val cStr = if (cramped) ", cluttered rooms" else ""
        return "World: ${timeOfDay.label}, $eStr, $wStr, $vStr, $bStr$qStr$cStr, visitor trust ${f(visitorTrust)}"
    }

    /** Compact status line for the home screen's "Phone as a World" card. */
    fun homeSummary(): String {
        val food = when {
            charging -> "⚡ Feeding time ($batteryPct%)"
            hungry -> "🍌 Hungry & slow ($batteryPct%)"
            else -> "🍌 Well-fed ($batteryPct%)"
        }
        val wx = when {
            hot -> "🔥 Hot (${tempC.toInt()}°C, fanning)"
            heat >= 0.4f -> "🌤️ Warm (${tempC.toInt()}°C)"
            else -> "🍃 Cool (${tempC.toInt()}°C)"
        }
        val gate = if (visitorsWaiting > 0) "📬 $visitorsWaiting visitor${if (visitorsWaiting == 1) "" else "s"} at gate" else "📬 Gate calm"
        val bridge = if (bridgeUp) "🌉 Bridge up" else "🏝️ Bridge down"
        return "${timeOfDay.icon} ${timeOfDay.label} · $food · $wx\n" +
            "📍 At ${currentPlace.worldName} · $gate · $bridge · 🤝 Trust ${(visitorTrust * 100).toInt()}%"
    }
}
