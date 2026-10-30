package com.combo.monkeycompanion.brain

import kotlin.random.Random

/**
 * v4.2 "power entrances": now and then a proactive pipe arrives with a superpower first, then the line.
 *  - FLY    (notification summaries, digests, deliveries): caped flight in, then announce
 *  - STRIKE (important people, alerts): a power strike, then the message
 *  - JETS   (jokes, app quips, greetings): rocket / armour jets, then the joke
 * Each character uses its own equivalent power ([pick], with a fallback to any power it has).
 * Never with battery saver, never when superpowers are Off; the pipe itself already passed the proactive gate.
 */
object PowerEntrances {
    enum class Kind { FLY, STRIKE, JETS }

    /** Off / Sometimes (~30 %) / Often (~60 %). */
    fun chance(mode: String): Float = when (mode.lowercase()) { "often" -> 0.6f; "off" -> 0f; else -> 0.3f }

    fun roll(mode: String, saver: Boolean, powersOff: Boolean, rnd: Random = Random): Boolean =
        !saver && !powersOff && rnd.nextFloat() < chance(mode)

    /** Character id -> (kind -> power). */
    private val TABLE: Map<String, Map<Kind, Superpower>> by lazy { mapOf(
        "monkey" to mapOf(Kind.FLY to Superpower.SuperBanana, Kind.STRIKE to Superpower.ThunderMonkey, Kind.JETS to Superpower.RocketMonkey),
        "superboy" to mapOf(Kind.FLY to HeroSwoop, Kind.STRIKE to HeatVisionPower, Kind.JETS to SpeedBlurPower),
        "armour" to mapOf(Kind.FLY to ArmourJetsPower, Kind.STRIKE to ShieldBubblePower, Kind.JETS to ConfettiRocketsPower),
        "mask" to mapOf(Kind.FLY to Superpower.SuperBanana, Kind.STRIKE to Superpower.Magician, Kind.JETS to Superpower.TimeFreeze),
    ) }

    /** The power for [kind] for this character; falls back to the first of [own] when the table has none / it isn't theirs. */
    fun pick(kind: Kind, characterId: String, own: List<Superpower>): Superpower? {
        val p = TABLE[characterId]?.get(kind)
        if (p != null && (own.isEmpty() || p in own)) return p
        return own.firstOrNull { it != LowPowerPower } ?: p
    }

    /** A short herald line while the power plays (the real message comes after landing, with its chips). */
    fun herald(kind: Kind): String = when (kind) {
        Kind.FLY -> listOf("📣 Special delivery, incoming!", "📬 Mail-hero on the way!").random()
        Kind.STRIKE -> listOf("⚡ ALERT! ALERT!", "⚡ Important news, stand back!").random()
        Kind.JETS -> listOf("🚀 Joke delivery in 3, 2, 1...", "🚀 Hold on, I've got a good one!").random()
    }
}
