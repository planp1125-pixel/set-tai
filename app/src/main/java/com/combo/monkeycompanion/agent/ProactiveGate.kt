package com.combo.monkeycompanion.agent

/**
 * v4.2 proactive buddy: the pure rules (unit-tested). Settai pipes up for meaningful moments only, and this
 * decides whether a moment may become a pipe: the level, the category switch, quiet hours, per-hour budget,
 * per-category / per-key cooldowns, and the "never now" situations (DND, typing, a call, fullscreen, a game).
 */
enum class ProLevel(val label: String, val perHour: Int, val quipChance: Float, val help: String) {
    OFF("Off", 0, 0f, "Never pipes up on its own."),
    CHILL("Chill", 2, 0.15f, "Only things that matter: OTPs, deliveries, important people, full battery, good morning."),
    BUDDY("Buddy", 4, 0.35f, "Chill + notification bursts, break nudges, the odd app quip and chat-list digests."),
    CHATTERBOX("Chatterbox", 8, 0.8f, "Everything, more often. Still never while you type, call, game or watch fullscreen.");

    companion object { fun of(s: String?) = entries.firstOrNull { it.name.equals(s, true) } ?: BUDDY }
}

enum class ProCat(
    val label: String, val minLevel: ProLevel, val cooldownMs: Long,
    /** Doesn't use the hourly budget (it's something you're waiting for). */
    val free: Boolean = false,
    /** Allowed in quiet hours (you're actively doing it right now). */
    val quietOk: Boolean = false,
) {
    OTP("OTP arrivals (shown only, never spoken)", ProLevel.CHILL, 0L, free = true, quietOk = true),
    DELIVERY("Deliveries & rides", ProLevel.CHILL, 2 * MIN, free = true),
    IMPORTANT("Messages from important people", ProLevel.CHILL, 5 * MIN),
    BURST("Notification bursts", ProLevel.BUDDY, 15 * MIN),
    WELLBEING("Long app sessions (break nudges)", ProLevel.BUDDY, 20 * MIN),
    APP_QUIP("App-opened quips", ProLevel.BUDDY, 10 * MIN),
    DIGEST("Chat-list digest on app switch", ProLevel.BUDDY, 10 * MIN),
    BATTERY("Charger & full battery", ProLevel.CHILL, 30 * MIN),
    GREETING("Good morning / good night", ProLevel.CHILL, 6 * 60 * MIN),
    JOKE("Unprompted power jokes (max 1 an hour)", ProLevel.BUDDY, 60 * MIN);
}

private const val MIN = 60_000L

/** What's going on right now that would make a pipe rude. */
data class ProSituation(
    val dnd: Boolean = false,
    val typing: Boolean = false,
    val inCall: Boolean = false,
    val fullscreen: Boolean = false,
    val game: Boolean = false,
    /** Settai can't show a bubble now (hidden, asleep, mid-question, chatting...). */
    val busy: Boolean = false,
) {
    fun reason(): String? = when {
        dnd -> "do not disturb"
        inCall -> "in a call"
        typing -> "you're typing"
        fullscreen -> "fullscreen"
        game -> "gaming"
        busy -> "${com.combo.monkeycompanion.character.CharacterRegistry.current.name} is busy"
        else -> null
    }
}

object QuietHours {
    /** True when [minuteOfDay] is inside [start]..[end) (minutes since midnight); wraps past midnight; start == end = never quiet. */
    fun inside(minuteOfDay: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> minuteOfDay in start until end
        else -> minuteOfDay >= start || minuteOfDay < end
    }
    fun parse(hhmm: String, def: Int): Int {
        val m = Regex("^(\\d{1,2})[:.](\\d{2})$").find(hhmm.trim()) ?: return def
        val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
        return if (h in 0..23 && mi in 0..59) h * 60 + mi else def
    }
    fun fmt(min: Int) = "%02d:%02d".format(min / 60, min % 60)
}

class ProactiveGate {
    private val sent = ArrayDeque<Long>()
    private val lastCat = HashMap<ProCat, Long>()
    private val lastKey = HashMap<String, Long>()

    /** null = go ahead; else the reason it's held back. [key] (an app, a sender) gets its own [keyCooldownMs]. */
    @Synchronized fun check(
        cat: ProCat, level: ProLevel, catOn: Boolean, now: Long, minuteOfDay: Int,
        quietStart: Int, quietEnd: Int, sit: ProSituation = ProSituation(),
        key: String? = null, keyCooldownMs: Long = 0L,
    ): String? {
        if (level == ProLevel.OFF) return "proactive is off"
        if (level.ordinal < cat.minLevel.ordinal) return "needs level ${cat.minLevel.label}"
        if (!catOn) return "category off"
        // OTPs only skip quiet hours, not DND / a call: the mouth-cloth means silence.
        sit.reason()?.let { r -> if (!(cat == ProCat.OTP && (r == "you're typing" || r == "fullscreen" || r == "gaming"))) return r }
        if (!cat.quietOk && QuietHours.inside(minuteOfDay, quietStart, quietEnd)) return "quiet hours"
        lastCat[cat]?.let { if (now - it < cat.cooldownMs) return "cooldown" }
        if (key != null) lastKey["${cat.name}|$key"]?.let { if (now - it < keyCooldownMs) return "cooldown for $key" }
        if (!cat.free) {
            while (sent.isNotEmpty() && now - sent.first() >= 60 * MIN) sent.removeFirst()
            if (sent.size >= level.perHour) return "hourly limit (${level.perHour})"
        }
        return null
    }

    @Synchronized fun record(cat: ProCat, now: Long, key: String? = null) {
        lastCat[cat] = now
        if (key != null) lastKey["${cat.name}|$key"] = now
        if (!cat.free) sent.addLast(now)
    }

    @Synchronized fun usedThisHour(now: Long): Int { while (sent.isNotEmpty() && now - sent.first() >= 60 * MIN) sent.removeFirst(); return sent.size }
    @Synchronized fun reset() { sent.clear(); lastCat.clear(); lastKey.clear() }
}

/** A proactive line with its quick-reply chips (1-3). */
data class Pipe(val cat: ProCat, val text: String, val chips: List<QuickChip>, val excited: Boolean = false,
                /** v4.2 power entrance kind for this pipe; null = never with a power. */
                val entrance: com.combo.monkeycompanion.brain.PowerEntrances.Kind? = Pipe.entranceFor(cat),
                /** Power jokes: always with the power (unless battery saver / entrances Off). */
                val alwaysPower: Boolean = false) {
    companion object {
        fun entranceFor(cat: ProCat): com.combo.monkeycompanion.brain.PowerEntrances.Kind? = when (cat) {
            ProCat.BURST, ProCat.DIGEST, ProCat.DELIVERY -> com.combo.monkeycompanion.brain.PowerEntrances.Kind.FLY
            ProCat.IMPORTANT, ProCat.BATTERY -> com.combo.monkeycompanion.brain.PowerEntrances.Kind.STRIKE
            ProCat.APP_QUIP, ProCat.GREETING, ProCat.JOKE -> com.combo.monkeycompanion.brain.PowerEntrances.Kind.JETS
            ProCat.OTP, ProCat.WELLBEING -> null   // OTPs right away; break nudges stay calm
        }
    }
}

/** Notification classification (pure). */
object NotifKinds {
    private val DELIVERY_APPS = setOf("in.swiggy.android", "com.application.zomato", "in.amazon.mShop.android.shopping", "com.flipkart.android", "com.ubercab", "com.olacabs.customer",
        "com.rapido.passenger", "com.grofers.customerapp", "com.zeptoconsumerapp", "com.bigbasket.mobileapp", "com.dunzo.user", "com.porter.customerapp", "com.myntra.android", "com.meesho.supply", "com.jiomart.app")
    private val DELIVERY = Regex("(?i)\\b(out for delivery|arriving (soon|in|now|today)|has been delivered|delivered|on (the|its) way|reached (your|the) (location|pickup)|is arriving|driver|captain|your ride|cab is|order (is|has been) (picked|placed|confirmed|ready)|delivery partner|will reach|ETA)\\b")
    val MESSAGING = setOf("com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "com.google.android.apps.messaging", "org.thoughtcrime.securesms",
        "com.instagram.android", "com.facebook.orca", "com.samsung.android.messaging", "com.truecaller", "com.discord", "com.Slack", "com.google.android.gm")

    fun isDelivery(pkg: String, text: String) = DELIVERY.containsMatchIn(text) && (pkg in DELIVERY_APPS || Regex("(?i)\\b(order|delivery|ride|cab|parcel|package|shipment)\\b").containsMatchIn(text))
    fun isMessage(pkg: String) = pkg in MESSAGING

    /** "Amma ×2, Ravi, Swiggy": who sent the burst, most first. */
    fun who(items: List<NotifItem>, max: Int = 3): String {
        val g = items.groupBy { it.title.ifBlank { it.app }.take(18) }.entries.sortedByDescending { it.value.size }
        val parts = g.take(max).map { (k, v) -> if (v.size > 1) "$k ×${v.size}" else k }
        return parts.joinToString(", ") + if (g.size > max) " +${g.size - max}" else ""
    }

    /** Important if a memory fact names them, or they're one of your most frequent senders. */
    fun important(sender: String, facts: List<String>, counts: Map<String, Int>): Boolean {
        val s = sender.trim().lowercase()
        if (s.length < 2 || s.contains(Regex("\\d{4,}"))) return false
        if (s in setOf("amma", "appa", "mom", "mum", "dad", "wife", "husband", "boss")) return true
        val w = Regex("\\b${Regex.escape(s)}\\b")
        if (facts.any { w.containsMatchIn(it.lowercase()) }) return true
        val top = counts.entries.filter { it.value >= 8 }.sortedByDescending { it.value }.take(5).map { it.key.lowercase() }
        return s in top
    }
}
