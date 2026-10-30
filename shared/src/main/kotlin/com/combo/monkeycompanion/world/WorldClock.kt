package com.combo.monkeycompanion.world

import com.combo.monkeycompanion.mind.Drives
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * What happened under the glass while the screen was dark (>= 30 min away, especially overnight).
 * Recorded in the character's Diary and shown as a gentle morning trace on wake.
 */
data class NightTrace(
    val ts: Long,
    val awayMinutes: Int,
    val line: String,
    val diarySubject: String,
    val companions: List<String> = listOf("monkey"),
)

/**
 * Event-driven clock & weather engine for the phone-as-a-world.
 * Zero background polling: updates only on system events (battery, charging, temp, notifications,
 * app switches, ringer, connectivity, storage, screen off/unlock) and piggybacks on MindHub's
 * visible-only heartbeat. Lasting conditions transition gradually via exponential smoothing.
 */
class WorldClock(val state: WorldState = WorldState()) {
    var lastTickAt: Long = 0L
    var screenOffAt: Long = 0L
    var lastPlaceChangeAt: Long = 0L
    val recentTraces = ArrayList<NightTrace>()
    private val notifTimes = ArrayDeque<Long>()
    private var batteryInitialized = false

    private fun ensureTime(now: Long) {
        if (lastTickAt == 0L) lastTickAt = now
    }

    /** Target energy from battery percentage and charging state. */
    fun energyTarget(pct: Int = state.batteryPct, charging: Boolean = state.charging): Float {
        val base = (pct.coerceIn(0, 100) / 100f)
        return if (charging) (base + 0.25f).coerceIn(0.35f, 1.0f) else base.coerceIn(0.05f, 1.0f)
    }

    /** Target heat (0..1) from battery temperature in °C. */
    fun heatTarget(tempC: Float = state.tempC): Float =
        ((tempC - 29f) / 13f).coerceIn(0f, 1f)

    /** Advance gradual conditions up to [now]. */
    fun onTick(now: Long, minuteOfDay: Int, quietHours: Boolean = false) {
        ensureTime(now)
        val dt = ((now - lastTickAt).coerceIn(0L, 6 * 3600_000L)) / 1000f
        lastTickAt = now
        state.timeOfDay = TimeOfDay.ofMinute(minuteOfDay)

        // Energy approaches battery level gradually (faster when feeding on current, slower when draining)
        val eTarget = energyTarget()
        val eTau = if (state.charging) 90f else 240f
        state.energy = Drives.approach(state.energy, eTarget, dt, eTau)

        // Weather (heat) approaches battery temperature gradually
        state.heat = Drives.approach(state.heat, heatTarget(), dt, 180f)

        // Prune notifications older than 10 minutes; busyness decays toward current arrival rate
        while (notifTimes.isNotEmpty() && now - notifTimes.first() > 10 * 60_000L) notifTimes.removeFirst()
        val rateTarget = (notifTimes.size / 6f).coerceIn(0f, 1f)
        state.busyness = Drives.approach(state.busyness, rateTarget, dt, 120f)
        if (notifTimes.isEmpty() && state.visitorsWaiting > 0 && dt > 60f) {
            state.visitorsWaiting = max(0, state.visitorsWaiting - (dt / 180f).toInt())
        }

        // Visitor presence decays slowly when staying in one app without interacting
        val awayInAppSec = if (lastPlaceChangeAt > 0L) (now - lastPlaceChangeAt) / 1000f else 0f
        val presTarget = when {
            screenOffAt > 0L -> 0.05f
            awayInAppSec > 600f -> 0.25f
            else -> 0.65f
        }
        state.visitorPresence = Drives.approach(state.visitorPresence, presTarget, dt, 300f)

        if (quietHours) state.quiet = max(state.quiet, 0.85f)
        state.clamp()
    }

    fun onBattery(pct: Int, now: Long) {
        val clean = pct.coerceIn(0, 100)
        state.batteryPct = clean
        if (!batteryInitialized) {
            batteryInitialized = true
            state.energy = energyTarget(clean, state.charging)
        } else {
            // Smooth partial step immediately + continued drift on ticks
            val target = energyTarget(clean, state.charging)
            state.energy = Drives.approach(state.energy, target, 60f, 180f)
        }
        state.clamp()
        ensureTime(now)
    }

    fun onCharging(plugged: Boolean, now: Long) {
        state.charging = plugged
        val target = energyTarget(state.batteryPct, plugged)
        state.energy = Drives.approach(state.energy, target, if (plugged) 75f else 30f, 150f)
        if (plugged) onInteraction(gentle = true, kind = "feed", now = now)
        state.clamp()
    }

    fun onTemp(celsius: Float, now: Long) {
        state.tempC = celsius.coerceIn(10f, 55f)
        state.heat = Drives.approach(state.heat, heatTarget(state.tempC), 60f, 120f)
        state.clamp()
        ensureTime(now)
    }

    fun onNotification(pkg: String, now: Long) {
        ensureTime(now)
        while (notifTimes.isNotEmpty() && now - notifTimes.first() > 10 * 60_000L) notifTimes.removeFirst()
        notifTimes.addLast(now)
        state.visitorsWaiting = (state.visitorsWaiting + 1).coerceAtMost(25)
        state.busyness = (state.busyness + 0.18f).coerceAtMost(1f)
        state.clamp()
    }

    fun onAppSwitch(pkg: String, isGame: Boolean = false, now: Long) {
        ensureTime(now)
        if (pkg != state.currentPlacePkg) {
            state.currentPlacePkg = pkg
            lastPlaceChangeAt = now
            // Visiting the app clears waiting couriers for that gate
            if (state.visitorsWaiting > 0) state.visitorsWaiting = max(0, state.visitorsWaiting - 1)
            state.visitorPresence = (state.visitorPresence + 0.12f).coerceAtMost(1f)
        }
        state.clamp()
    }

    fun onRinger(dndOrSilent: Boolean, quietHours: Boolean, now: Long) {
        ensureTime(now)
        state.quiet = when {
            dndOrSilent -> 1.0f
            quietHours -> 0.85f
            else -> 0.0f
        }
        state.clamp()
    }

    fun onConnectivity(connected: Boolean, now: Long) {
        ensureTime(now)
        state.connectivity = if (connected) 1.0f else 0.0f
        state.clamp()
    }

    fun onStorage(usedFraction: Float, lowRam: Boolean = false, now: Long) {
        ensureTime(now)
        val u = usedFraction.coerceIn(0f, 1f)
        val base = if (u < 0.65f) u * 0.3f else 0.2f + (u - 0.65f) / 0.35f * 0.8f
        state.clutter = (base + if (lowRam) 0.25f else 0f).coerceIn(0f, 1f)
        state.clamp()
    }

    /**
     * Visitor relationship trust (0..1): grows with gentle touches, head pats, chat and feeding;
     * drops with rough shakes or rude pokes.
     */
    fun onInteraction(gentle: Boolean, kind: String = "touch", now: Long) {
        ensureTime(now)
        state.visitorPresence = (state.visitorPresence + 0.2f).coerceAtMost(1f)
        val delta = when {
            !gentle && kind == "hard_shake" -> -0.06f
            !gentle -> -0.025f
            kind == "feed" -> +0.025f
            kind == "chat" -> +0.02f
            else -> +0.015f
        }
        state.visitorTrust = (state.visitorTrust + delta).coerceIn(0.05f, 1.0f)
        state.clamp()
    }

    fun onScreenOff(now: Long) {
        ensureTime(now)
        if (screenOffAt == 0L) screenOffAt = now
    }

    /**
     * When the human unlocks the phone: if the glass was dark for >= 30 min, generates a morning/night trace
     * describing what the creatures did under the glass while the visitor was gone.
     */
    fun onUnlock(
        now: Long,
        minuteOfDay: Int,
        companions: List<String> = listOf("monkey"),
        rnd: Random = Random,
    ): NightTrace? {
        ensureTime(now)
        state.timeOfDay = TimeOfDay.ofMinute(minuteOfDay)
        state.unlocksToday++
        state.visitorPresence = (state.visitorPresence + 0.3f).coerceAtMost(1f)
        val off = screenOffAt
        screenOffAt = 0L
        onTick(now, minuteOfDay)
        if (off <= 0L) return null
        val awayMs = (now - off).coerceAtLeast(0L)
        if (awayMs < 30 * 60_000L) return null
        val awayMins = (awayMs / 60_000L).toInt()
        val trace = buildTrace(now, awayMins, minuteOfDay, companions, rnd)
        recentTraces.add(0, trace)
        while (recentTraces.size > 12) recentTraces.removeAt(recentTraces.lastIndex)
        return trace
    }

    private fun charName(id: String) = when (id) {
        "superboy" -> "Superboy"
        "armour" -> "Anna"
        "mask" -> "Mask Man"
        else -> "Settai"
    }

    private fun buildTrace(now: Long, awayMins: Int, minuteOfDay: Int, companions: List<String>, rnd: Random): NightTrace {
        val lead = companions.firstOrNull() ?: "monkey"
        val buddy = companions.drop(1).firstOrNull()
        val leadName = charName(lead)
        val buddyName = buddy?.let { charName(it) } ?: if (lead == "monkey") "Anna" else "Settai"
        val overnight = awayMins >= 180 || minuteOfDay in (5 * 60)..(10 * 60 + 30)
        val options = ArrayList<Pair<String, String>>()
        if (overnight) {
            options += "While the glass was dark, $leadName & $buddyName shared a banana near the battery icon 🍌🔋" to "shared a midnight banana with $buddyName near the battery icon"
            options += "Found a shiny notification card at 3 AM and hid it under the corner 📬✨" to "hid a shiny 3 AM notification card under the corner"
            options += "While you slept, Superboy patrolled the top edge while Settai built a pillow fort by the clock 🌙🛡️" to "built a pillow fort by the clock while Superboy patrolled"
            options += "Anna oiled the battery pipes at midnight and tucked a blanket over $leadName ⚙️😴" to "Anna tuned the battery pipes and tucked everyone in"
            options += "Mask Man practised shadow puppets against the dimmed status bar until dawn 🎭🌙" to "practised shadow puppets against the status bar"
        } else {
            val hrs = if (awayMins >= 90) "${awayMins / 60}h" else "${awayMins}m"
            options += "While the glass was dark ($hrs), $leadName & $buddyName played rock-paper-scissors in the home clearing ✊✌️" to "played rock-paper-scissors with $buddyName while the screen was dark"
            options += "Swept a few stray notification crumbs out of the corner while you were away ($hrs) 🧹📬" to "tidied notification crumbs in the corner"
            options += "$leadName curled up by the warm battery pipe for a $hrs catnap 😴🔋" to "napped by the warm battery pipe"
        }
        if (state.hungry) {
            options += "While the glass was dark, $leadName rationed the last battery crumbs (${state.batteryPct}%) and waited for feeding time 🍌🪫" to "rationed the last battery crumbs (${state.batteryPct}%)"
        }
        val (line, subj) = options[rnd.nextInt(options.size)]
        return NightTrace(now, awayMins, line, subj, companions)
    }
}
