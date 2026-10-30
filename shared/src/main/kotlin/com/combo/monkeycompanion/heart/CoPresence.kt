package com.combo.monkeycompanion.heart

import java.util.Locale

/**
 * v8.0 Co-Presence Rituals (pure Kotlin, deterministic, unit-tested):
 *  1. [FocusBuddy]: 25-minute (or custom) Pomodoro study/focus desk buddy.
 *  2. [BreatheBuddy]: 60-second guided calm breathing buddy (4s inhale · 4s hold · 6s exhale).
 *  3. [CampfireScene]: Late-night (>= 23:00 or < 05:00) or on-demand crackling campfire at the bottom edge.
 */
class FocusBuddy(
    /** Wall clock (ms). v10.9: the session is an END TIME on this clock, not a frame-counted float. */
    var clock: () -> Long = { System.currentTimeMillis() },
) {
    var active: Boolean = false; private set
    var totalSecs: Float = 25 * 60f; private set
    /** Wall-clock end of the session (ms); 0 when idle. Persisted by the app's ActivityScheduler. */
    var endAtMs: Long = 0L; private set
    /** Called on start / stop / finish / restore so the owner can persist or (re)schedule the alarm. */
    var onChange: ((FocusBuddy) -> Unit)? = null

    val remainingSecs: Float get() = if (!active) 0f else ((endAtMs - clock()) / 1000f).coerceAtLeast(0f)
    val elapsedSecs: Float get() = if (!active) 0f else (totalSecs - remainingSecs).coerceIn(0f, totalSecs)

    fun start(minutes: Int = 25) {
        val m = minutes.coerceIn(1, 120)
        totalSecs = m * 60f
        endAtMs = clock() + m * 60_000L
        active = true
        onChange?.invoke(this)
    }

    /** Re-attaches a session saved before an interruption (service restart, app killed). False if it already ended. */
    fun restore(endAt: Long, totalSecs: Float): Boolean {
        if (endAt <= clock() || totalSecs <= 0f) return false
        this.totalSecs = totalSecs; endAtMs = endAt; active = true
        return true
    }

    fun stop() {
        val was = active
        active = false
        endAtMs = 0L
        if (was) onChange?.invoke(this)
    }

    /** True exactly once, on the check where the wall-clock end time has passed. Frames, powers and chats can't change it. */
    fun tick(): Boolean {
        if (!active) return false
        if (clock() >= endAtMs) {
            active = false
            onChange?.invoke(this)
            return true
        }
        return false
    }

    /** Simulates [dt] seconds passing (tests / previews): moves the end time closer, then [tick]s. */
    fun step(dt: Float): Boolean {
        if (!active) return false
        endAtMs -= (dt * 1000f).toLong()
        return tick()
    }

    val progress: Float get() = if (totalSecs <= 0f) 0f else (elapsedSecs / totalSecs).coerceIn(0f, 1f)

    fun timerLabel(): String {
        val rem = kotlin.math.ceil(remainingSecs.toDouble()).toInt().coerceAtLeast(0)
        val mm = rem / 60
        val ss = rem % 60
        return "📚 %02d:%02d".format(Locale.ROOT, mm, ss)
    }

    companion object {
        fun parseMinutes(text: String): Int? {
            val t = text.lowercase(Locale.ROOT).trim()
            val wants = "focus" in t || "study" in t || "pomodoro" in t || "work with me" in t || "study with me" in t
            if (!wants) return null
            val num = Regex("""(\d{1,3})\s*(?:m|min|mins|minute|minutes)?""").find(t)?.groupValues?.get(1)?.toIntOrNull()
            return (num ?: 25).coerceIn(1, 120)
        }
    }
}

class BreatheBuddy {
    enum class Phase(val label: String, val cue: String) {
        INHALE("Breathe in…", "Breathe in gently (4s) 🫧"),
        HOLD("Hold…", "Hold softly (4s) ✨"),
        EXHALE("Breathe out…", "Breathe out slowly (6s) 🌿");
    }

    var active: Boolean = false; private set
    var elapsed: Float = 0f; private set
    val totalSecs: Float = 60f
    val cycleSecs: Float = 14f // 4s inhale + 4s hold + 6s exhale

    fun start() {
        elapsed = 0f
        active = true
    }

    fun stop() {
        active = false
        elapsed = 0f
    }

    /** Advances the 60s breathing exercise; returns true when the 60s session completes! */
    fun step(dt: Float): Boolean {
        if (!active) return false
        elapsed += dt
        if (elapsed >= totalSecs) {
            active = false
            return true
        }
        return false
    }

    fun phaseAt(t: Float = elapsed): Phase {
        val c = ((t % cycleSecs) + cycleSecs) % cycleSecs
        return when {
            c < 4f -> Phase.INHALE
            c < 8f -> Phase.HOLD
            else -> Phase.EXHALE
        }
    }

    /**
     * Smooth aura radius fraction in `0.35f..1.0f`:
     *  - 0..4s (INHALE): expands 0.35 -> 1.0
     *  - 4..8s (HOLD): stays at 1.0
     *  - 8..14s (EXHALE): contracts 1.0 -> 0.35
     */
    fun radiusFraction(t: Float = elapsed): Float {
        val c = ((t % cycleSecs) + cycleSecs) % cycleSecs
        return when {
            c < 4f -> {
                val u = (c / 4f).coerceIn(0f, 1f)
                val s = u * u * (3f - 2f * u)
                0.35f + 0.65f * s
            }
            c < 8f -> 1.0f
            else -> {
                val u = ((c - 8f) / 6f).coerceIn(0f, 1f)
                val s = u * u * (3f - 2f * u)
                1.0f - 0.65f * s
            }
        }
    }

    val secondsLeft: Int get() = (totalSecs - elapsed).toInt().coerceAtLeast(0)

    companion object {
        fun wantsBreathe(text: String): Boolean {
            val t = text.lowercase(Locale.ROOT)
            return "breathe" in t || "breathing" in t || "calm me" in t || "help me calm" in t || "panic" in t
        }
    }
}

class CampfireScene {
    var active: Boolean = false; private set
    var elapsed: Float = 0f; private set
    var manual: Boolean = false; private set

    fun start(byUser: Boolean = false) {
        active = true
        elapsed = 0f
        manual = byUser
    }

    fun stop() {
        active = false
        elapsed = 0f
        manual = false
    }

    fun step(dt: Float) {
        if (active) elapsed += dt
    }

    companion object {
        /** Late at night (23:00..04:59 IST) when calm/idle, Settai lights a cozy campfire at the bottom edge. */
        fun isNightCampfireHour(minuteOfDay: Int): Boolean {
            val h = ((minuteOfDay % 1440) + 1440) % 1440 / 60
            return h >= 23 || h < 5
        }

        fun wantsCampfire(text: String): Boolean {
            val t = text.lowercase(Locale.ROOT)
            return "campfire" in t || "bonfire" in t || "light a fire" in t
        }
    }
}
