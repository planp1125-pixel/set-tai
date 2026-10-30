package com.combo.monkeycompanion.mind

import kotlin.math.exp

/** The creature's inner state, every value 0..1. Evolves with time (lazily, on events) and with what happens to it. */
data class Drives(
    var energy: Float = 0.75f,
    var boredom: Float = 0.2f,
    var curiosity: Float = 0.4f,
    var social: Float = 0.3f,
    var annoyance: Float = 0f,
    var contentment: Float = 0.6f,
) {
    fun clamp(): Drives { energy = c(energy); boredom = c(boredom); curiosity = c(curiosity); social = c(social); annoyance = c(annoyance); contentment = c(contentment); return this }
    private fun c(v: Float) = if (v.isNaN()) 0.5f else v.coerceIn(0f, 1f)

    fun encode() = listOf(energy, boredom, curiosity, social, annoyance, contentment).joinToString(",") { "%.3f".format(java.util.Locale.ROOT, it) }

    companion object {
        fun decode(s: String?): Drives {
            val v = s?.split(',')?.mapNotNull { it.toFloatOrNull() } ?: return Drives()
            if (v.size < 6) return Drives()
            return Drives(v[0], v[1], v[2], v[3], v[4], v[5]).clamp()
        }

        /** Circadian energy target for a time of day (minutes since midnight). */
        fun energyTarget(minute: Int, batteryLow: Boolean): Float {
            val h = minute / 60f
            val t = when {
                h < 6f -> 0.12f
                h < 8f -> 0.12f + (h - 6f) / 2f * 0.6f
                h < 13f -> 0.85f
                h < 15f -> 0.55f          // after-lunch dip
                h < 21f -> 0.8f
                h < 23f -> 0.55f
                else -> 0.25f
            }
            return if (batteryLow) minOf(t, 0.4f) else t
        }

        /** Move [x] toward [target] with time constant [tau] seconds over [dt] seconds. */
        fun approach(x: Float, target: Float, dt: Float, tau: Float) = x + (target - x) * (1f - exp(-dt / tau))
    }

    /**
     * Time passes ([dt] seconds, [visible] = on screen). Pure: no randomness. Long gaps (phone in a pocket overnight)
     * are handled in one step thanks to the exponential approach.
     */
    fun tick(dt: Float, minute: Int, visible: Boolean, napping: Boolean, curiosityBase: Float, batteryLow: Boolean) {
        if (dt <= 0f) return
        energy = if (napping) approach(energy, 1f, dt, 2400f) else approach(energy, energyTarget(minute, batteryLow), dt, 3600f)
        // Boredom and wanting attention grow only while it's out and nothing happens; they fade while hidden / asleep.
        boredom = if (visible && !napping) approach(boredom, 1f, dt, 1500f) else approach(boredom, 0.1f, dt, 3000f)
        social = if (visible && !napping) approach(social, 1f, dt, 2700f) else approach(social, 0.45f, dt, 7200f)
        curiosity = approach(curiosity, curiosityBase, dt, 600f)
        annoyance = approach(annoyance, 0f, dt, 420f)
        contentment = approach(contentment, 0.55f - 0.4f * annoyance + 0.1f * (energy - 0.5f), dt, 1800f)
        clamp()
    }
}
