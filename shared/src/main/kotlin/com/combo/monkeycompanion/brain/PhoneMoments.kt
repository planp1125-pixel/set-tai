package com.combo.monkeycompanion.brain

import kotlin.random.Random

/**
 * v4.4 "Phone moments": short reactions to things the phone does (event-driven only; pure rules, unit-tested).
 * Each can be switched off in Settings; all respect DND, quiet hours, calls, fullscreen and a per-moment cooldown;
 * battery saver plays the short version (no screen effects).
 */
object PhoneMoments {
    private const val MIN = 60_000L

    enum class Moment(val id: String, val label: String, val cooldownMs: Long, val defaultOn: Boolean, val secs: Float, val shortSecs: Float) {
        CHARGE("charge", "Charger in: zaps the battery icon ⚡", 10 * MIN, true, 2.8f, 1.2f),
        UNPLUG("unplug", "Charger out: sad wave, “Bye current”", 10 * MIN, true, 2.4f, 1.2f),
        FULL("full", "Fully charged: flexes 💪", 6 * 60 * MIN, true, 2.6f, 1.2f),
        LOW("low", "Battery 15%: collapses dramatically 🪫", 30 * MIN, true, 3.2f, 1.4f),
        WIFI("wifi", "Wi-Fi connected: catches the signal 📶", 15 * MIN, true, 2.4f, 1.2f),
        HEADPHONES("headphones", "Headphones in: dances 🎧", 15 * MIN, true, 3.2f, 1.4f),
        SCREENSHOT("screenshot", "Screenshot: says cheese 📸 (needs photo access)", 2 * MIN, false, 1.8f, 1.0f);

        companion object { fun of(id: String) = entries.firstOrNull { it.id == id } }
    }

    /** v6: the charger moment is a little trip: up under the battery icon (0.6 s), zap, back home. */
    const val CHARGE_TRIP_SECS = 3.6f

    /** How each character charges the phone. */
    enum class ChargeStyle { ZAP, HEAT_BEAM, CABLE, GIANT_PLUG, /** v10.14 Billa & Chittu: the plug-in current shocks THEM (fur spikes / zapped spin). */ SHOCK }

    fun chargeStyle(characterId: String) = when (characterId) {
        "superboy" -> ChargeStyle.HEAT_BEAM
        "armour" -> ChargeStyle.CABLE
        "mask" -> ChargeStyle.GIANT_PLUG
        "billa", "chittu" -> ChargeStyle.SHOCK
        else -> ChargeStyle.ZAP
    }

    /** Why a moment can't play now, or null to play. [sinceLastMs] = time since this moment last played (Long.MAX_VALUE = never). */
    fun check(m: Moment, enabled: Boolean, sinceLastMs: Long, dnd: Boolean, quiet: Boolean, inCall: Boolean, fullscreen: Boolean): String? = when {
        !enabled -> "off"
        dnd -> "do not disturb"
        inCall -> "in a call"
        fullscreen -> "fullscreen"
        quiet -> "quiet hours"
        sinceLastMs < m.cooldownMs -> "cooldown"
        else -> null
    }

    /** Battery saver: the short version (a pose and a line, no screen effects). */
    fun duration(m: Moment, saver: Boolean) = if (saver) m.shortSecs else m.secs
    fun effects(saver: Boolean) = !saver

    /**
     * Where the status-bar battery icon probably is, in screen px: the far end of the status bar
     * (top-right; top-left for right-to-left layouts), vertically centred in the bar.
     */
    fun batteryTarget(screenW: Float, statusBarH: Float, rtl: Boolean, density: Float): FloatArray {
        val bar = if (statusBarH > 0f) statusBarH else 24f * density
        val x = if (rtl) 34f * density else screenW - 34f * density
        return floatArrayOf(x, bar / 2f)
    }

    /** Which way the character faces to point at the battery: +1 right, -1 left. */
    fun facingToward(fromX: Float, targetX: Float) = if (targetX >= fromX) 1 else -1

    /** Charge beam: how far along it is (0..1) and how bright, from moment progress [k]. */
    fun beam(k: Float): Pair<Float, Float> {
        val grow = ((k - 0.25f) / 0.15f).coerceIn(0f, 1f)
        val fade = 1f - ((k - 0.72f) / 0.15f).coerceIn(0f, 1f)
        return grow to (if (k < 0.25f) 0f else fade)
    }
    /** True while sparks fly at the battery icon. */
    fun sparking(k: Float) = beam(k).first >= 1f && beam(k).second > 0.3f

    fun line(m: Moment, characterId: String, rnd: Random = Random): String = when (m) {
        Moment.CHARGE -> when (chargeStyle(characterId)) {
            ChargeStyle.ZAP -> if (characterId == "devil") listOf("Ooh, tingly! ⚡😈 Devil battery charging…", "*tail sparks* Zzzap! That tickles ⚡😆") else listOf("Charging machan! Full power coming ⚡", "Zap! Current is flowing, machan ⚡")
            ChargeStyle.HEAT_BEAM -> listOf("Heat-beam charging! Full power coming ⚡", "A little heat vision for the battery 🔥⚡")
            ChargeStyle.CABLE -> listOf("Core cable connected. Charging machan ⚡", "Plugged into my arc core! Power sharing ⚡")
            ChargeStyle.GIANT_PLUG -> listOf("Giant plug, engage! Charging machan ⚡", "Ta-daa! The biggest plug in town 🔌⚡")
            ChargeStyle.SHOCK -> if (characterId == "billa") listOf("MEOWWW! 🙀⚡ …my fur! Who plugged in the current?!", "Aiyo! Static fur mode ⚡🙀 Charging, I guess")
                else listOf("Eek! Zzzapped! 🐭⚡ …I'm spinning!", "Current-u! Tail is tingling ⚡🐭")
        }.random(rnd)
        Moment.UNPLUG -> when (characterId) {
            "billa" -> listOf("Bye current… I'll nap on the warm phone 🐱👋", "Current gone? Purr… bye bye 😿👋")
            "chittu" -> listOf("Bye current! *waves tail* 🐭👋", "Nooo, the tingle left 😢🐭")
            "hollowman" -> listOf("*tips hat to the cable* Bye current 🎩👋", "Current left… even I can see that 😢")
            "devil" -> listOf("*poof* Where did the current go? 😈👋", "Unplugged! Fine, I'll run on mischief 😏")
            "surfer" -> listOf("Bye current, see you next wave 🌊👋", "Unplugged! Paddling on my own now 🏄‍♀️")
            else -> listOf("Bye current 😢👋", "Nooo, the current left us 😢👋")
        }.random(rnd)
        Moment.FULL -> when (characterId) {
            "hollowman" -> listOf("100%! Invisible muscles, fully charged 💪🎩", "Full power! Moonwalk battery ready 🕺🔋")
            "devil" -> listOf("100%! Full black-magic power 😈💪", "Fully charged! Time for tricks ✨😈")
            "surfer" -> listOf("100%! Ready for the biggest wave 💪🌊", "Full charge! Surf's up all day 🏄‍♀️🔋")
            else -> listOf("100%! Look at these muscles 💪", "Full charge! Feeling strong machan 💪🔋")
        }.random(rnd)
        Moment.LOW -> when (characterId) {
            "hollowman" -> listOf("Battery low… I'm getting MORE invisible… 🪫👻", "15%… my hat is tired… 🪫🎩")
            "devil" -> listOf("Battery low… my magic is fizzling… 🪫😵", "15%… even my tail is drooping… 🪫")
            "surfer" -> listOf("Battery low… the waves are going flat… 🪫🌊", "15%… paddle… so… slow… 🪫")
            "billa" -> listOf("Battery low… too tired to chase Chittu… 🪫", "15%… *dramatic cat flop* charger, please… 🪫")
            "chittu" -> listOf("Battery… so low… even the cheese can't save us… 🪫", "15%… *dramatic flop* charge us… 🪫")
            "monkey" -> listOf("Battery… so… low… charge me… 🪫", "15%… tell my bananas I loved them… 🪫")
            else -> listOf("Battery… so… low… charge me… 🪫", "15%… my energy needs a charger… 🪫")
        }.random(rnd)
        Moment.WIFI -> listOf("Caught the Wi-Fi! Full bars 📶", "Wi-Fi! Signal caught, machan 📶").random(rnd)
        Moment.HEADPHONES -> listOf("Headphones! Play something good 🎧", "Ooh music time 🎧💃").random(rnd)
        Moment.SCREENSHOT -> listOf("Cheese! 📸 Did you get my good side?", "Screenshot? Wait, let me pose! 📸").random(rnd)
    }
}
