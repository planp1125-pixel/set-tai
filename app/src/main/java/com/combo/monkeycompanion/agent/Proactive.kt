package com.combo.monkeycompanion.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.combo.monkeycompanion.ai.AppSettings
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.powers.CapGroup
import java.util.Calendar
import kotlin.random.Random

/**
 * v4.2 proactive buddy runtime. Event-driven only, no polling:
 *  - app switches: debounced accessibility window-state events ([onWindow]),
 *  - notifications: the notification listener ([onNotif]; only when the Notification reader power is on),
 *  - unlock (good morning / night), charger plugged / unplugged, battery level only while plugged,
 *  - one delayed runnable per watched app for the "long session" nudge.
 * The brain is never called here: every line is templated ([ProactiveLines]); the brain runs only if you tap a
 * chip. [ProactiveGate] decides if a moment may become a pipe; the overlay shows it via [ChatHub.pipe].
 */
object Proactive {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var app: Context
    private lateinit var st: AppSettings
    val gate = ProactiveGate()
    private var started = false

    /** Set by the overlay: DND / silent (the mouth cloth). */
    @Volatile var silent = false
    /** Set by the overlay: can it show a pipe right now (visible, awake, not mid-chat / question / drag)? */
    @Volatile var canShow: () -> Boolean = { false }

    fun start(ctx: Context) {
        if (started) return
        app = ctx.applicationContext; st = AppSettings(app); com.combo.monkeycompanion.mind.MindHub.init(app); started = true
        val f = IntentFilter().apply { addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED); addAction(Intent.ACTION_USER_PRESENT) }
        runCatching { ContextCompat.registerReceiver(app, sysReceiver, f, ContextCompat.RECEIVER_EXPORTED) }
        if (plugged()) watchLevel(true)
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { app.unregisterReceiver(sysReceiver) }
        watchLevel(false)
        main.removeCallbacksAndMessages(null)
    }

    // ---------------- emit ----------------
    private fun level() = ProLevel.of(st.proactiveLevel)
    private fun minuteNow(): Int { val c = Calendar.getInstance(); return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE) }

    /** What makes a pipe rude right now (cheap checks, only when something wants to pipe). */
    fun situation(): ProSituation {
        val sv = SettaiAccessibilityService.instance
        val am = app.getSystemService(AudioManager::class.java)
        val call = am != null && (am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION || am.mode == AudioManager.MODE_RINGTONE)
        val pkg = sv?.currentPkg?.takeIf { it.isNotBlank() } ?: ForegroundAppTracker.lastKnownPkg
        val game = pkg.isNotEmpty() && runCatching {
            val ai = app.packageManager.getApplicationInfo(pkg, 0)
            (Build.VERSION.SDK_INT >= 26 && ai.category == ApplicationInfo.CATEGORY_GAME) || (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0
        }.getOrDefault(false)
        return ProSituation(dnd = silent, typing = com.combo.monkeycompanion.fun5.Stage.imeVisible || sv?.imeVisible() == true, inCall = call, fullscreen = sv?.fullscreen() == true, game = game, busy = !canShow() || ChatHub.busy)
    }

    /** Try to pipe up. Returns true when shown. */
    private fun emit(cat: ProCat, key: String? = null, keyCooldownMs: Long = 0L, make: () -> Pipe?): Boolean {
        if (!started) return false
        val now = System.currentTimeMillis()
        val why = gate.check(cat, level(), st.proactiveCat(cat), now, minuteNow(), st.quietStart, st.quietEnd, situation(), key, keyCooldownMs)
        if (why != null) return false
        if (cat !in setOf(ProCat.OTP, ProCat.DELIVERY, ProCat.IMPORTANT, ProCat.BATTERY) &&
            !com.combo.monkeycompanion.mind.MindHub.permit(com.combo.monkeycompanion.mind.Proposal.PIPE)) return false
        val p = make() ?: return false
        gate.record(cat, now, key)
        ChatHub.pipe(p)
        return true
    }

    // ---------------- notifications ----------------
    private val recent = ArrayDeque<NotifItem>()
    private var burstCheck: Runnable? = null

    /** From the notification listener (only when the Notification reader power is on). */
    fun onNotif(n: NotifItem) {
        if (!started) return
        runCatching { com.combo.monkeycompanion.mind.MindHub.world.onNotification(n.pkg, System.currentTimeMillis()) }
        main.post { handleNotif(n) }
    }

    private fun handleNotif(n: NotifItem) {
        val otp = n.otp
        if (otp != null) {
            // Shown in the bubble only: never spoken, never sent to a brain, never stored.
            emit(ProCat.OTP) { Pipe(ProCat.OTP, ProactiveLines.otp(n.app, otp), listOf(QuickChip.OK)) }
            return
        }
        val text = "${n.title} ${n.text}"
        if (NotifKinds.isDelivery(n.pkg, text)) {
            emit(ProCat.DELIVERY, key = n.pkg + n.title, keyCooldownMs = 5 * 60_000L) { Pipe(ProCat.DELIVERY, ProactiveLines.delivery(n.app, Otp.mask(n.title.ifBlank { n.text }).take(70)), listOf(QuickChip.OK), excited = true) }
            return
        }
        if (!NotifKinds.isMessage(n.pkg)) return
        val sender = n.title.substringBefore(" (").substringBefore(":").trim()
        countSender(sender)
        val now = System.currentTimeMillis()
        recent.addLast(n)
        while (recent.isNotEmpty() && now - recent.first().time > 120_000L) recent.removeFirst()
        val facts = if (st.groupEnabled(CapGroup.MEMORY)) runCatching { com.combo.monkeycompanion.powers.ExtraCaps.memory(app).all().map { it.text } }.getOrDefault(emptyList()) else emptyList()
        if (NotifKinds.important(sender, facts, senderCounts())) {
            val shown = emit(ProCat.IMPORTANT, key = sender.lowercase(), keyCooldownMs = 15 * 60_000L) {
                Pipe(ProCat.IMPORTANT, ProactiveLines.important(sender, Otp.mask(n.text).take(60)), listOf(
                    QuickChip.NOTIF_CARD, QuickChip("prefill", "Reply", "reply to $sender saying "), QuickChip.LATER))
            }
            if (shown) return
        }
        val need = if (level() == ProLevel.CHATTERBOX) 3 else 4
        if (recent.size >= need && burstCheck == null) {
            // Wait a few seconds to let the burst finish, then sum it up once.
            val r = Runnable {
                burstCheck = null
                val items = recent.toList()
                if (items.size >= need) emit(ProCat.BURST) { Pipe(ProCat.BURST, ProactiveLines.burst(items.size, NotifKinds.who(items)), listOf(QuickChip.NOTIF_CARD, QuickChip.LATER)) }
            }
            burstCheck = r; main.postDelayed(r, 8_000L)
        }
    }

    private fun senderCounts(): Map<String, Int> {
        val p = app.getSharedPreferences("settai_proactive", Context.MODE_PRIVATE)
        return p.getString("senders", "").orEmpty().split('\n').mapNotNull { l -> l.substringBeforeLast('\t').takeIf { it.isNotBlank() }?.let { it to (l.substringAfterLast('\t').toIntOrNull() ?: 0) } }.toMap()
    }
    private fun countSender(s: String) {
        if (s.isBlank() || s.length > 40) return
        val m = senderCounts().toMutableMap()
        m[s] = (m[s] ?: 0) + 1
        val keep = m.entries.sortedByDescending { it.value }.take(30)
        app.getSharedPreferences("settai_proactive", Context.MODE_PRIVATE).edit().putString("senders", keep.joinToString("\n") { "${it.key}\t${it.value}" }).apply()
    }

    // ---------------- app switches ----------------
    private var fgPkg = ""
    private var fgSince = 0L
    private var pendingSwitch: Runnable? = null
    private var usageNudge: Runnable? = null

    /** From the accessibility service (window state changed), already filtered to real app packages. */
    fun onWindow(pkg: String) {
        if (!started || pkg == fgPkg) return
        pendingSwitch?.let { main.removeCallbacks(it) }
        val r = Runnable { pendingSwitch = null; switched(pkg) }
        pendingSwitch = r
        main.postDelayed(r, 1_500L)   // debounce: ignore flicks through apps
    }

    private fun switched(pkg: String) {
        if (pkg == fgPkg) return
        fgPkg = pkg; fgSince = System.currentTimeMillis()
        usageNudge?.let { main.removeCallbacks(it) }; usageNudge = null
        runCatching { com.combo.monkeycompanion.mind.MindHub.perceive(pkg) }
        if (level() == ProLevel.OFF) return
        if (pkg in st.usageAppList()) scheduleNudge(pkg, st.usageMinutes * 60_000L)
        val lvl = level()
        // A chat-list digest when opening a messenger with unread chats (needs the Screen agent power).
        if (pkg in DIGEST_APPS && lvl.ordinal >= ProLevel.BUDDY.ordinal && st.groupEnabled(CapGroup.SCREEN) && st.proactiveCat(ProCat.DIGEST)) {
            main.postDelayed({ if (fgPkg == pkg) digestNow(pkg) }, 1_200L)
            return
        }
        // Now and then, an unprompted power joke (max 1 an hour via the gate; only with power entrances on).
        if (st.powerEntrances != "off" && Random.nextFloat() < (if (lvl == ProLevel.CHATTERBOX) 0.12f else 0.06f) &&
            emit(ProCat.JOKE) { Pipe(ProCat.JOKE, ProactiveLines.joke(), listOf(QuickChip("funny", "Another! 😂"), QuickChip.OK), alwaysPower = true) }) return
        ProactiveLines.appQuip(pkg)?.let { line ->
            if (Random.nextFloat() < lvl.quipChance) emit(ProCat.APP_QUIP, key = pkg, keyCooldownMs = 3 * 60 * 60_000L) {
                Pipe(ProCat.APP_QUIP, line, listOfNotNull(ScreenTalk.contextChips(typeGuess(pkg)).firstOrNull(), QuickChip.ROAST).take(2) + QuickChip.LATER)
            }
        }
    }

    private val DIGEST_APPS = setOf("com.whatsapp", "com.whatsapp.w4b", "com.google.android.gm", "org.telegram.messenger", "com.google.android.apps.messaging")

    private fun digestNow(pkg: String) {
        val sv = SettaiAccessibilityService.instance ?: return
        if (situation().reason() != null) return
        val snap = runCatching { sv.snapshot(keepRefs = false) }.getOrNull() ?: return
        if (snap.pkg != pkg) return
        if (AgentSafety.screen(snap, app.packageName, st.agentBlockedList(), st.agentAllowedList()) is AgentSafety.Verdict.Blocked) return
        val d = Digest.build(snap)
        ChatHub.noteDigest(d)
        val interesting = (d.type == ScreenType.CHAT_LIST && d.unreadChats >= 2) || (d.type == ScreenType.EMAIL_LIST && d.items.count { it.unread > 0 } >= 3)
        if (!interesting) return
        emit(ProCat.DIGEST, key = pkg, keyCooldownMs = 30 * 60_000L) { Pipe(ProCat.DIGEST, ScreenTalk.summary(d), ScreenTalk.followUps(ScreenTalk.Mode.SUMMARY, d).filter { it.id != "roast" } + QuickChip.LATER) }
    }

    private fun scheduleNudge(pkg: String, afterMs: Long) {
        val r = Runnable {
            usageNudge = null
            if (fgPkg != pkg) return@Runnable
            val mins = ((System.currentTimeMillis() - fgSince) / 60_000L).toInt()
            val shown = emit(ProCat.WELLBEING, key = pkg, keyCooldownMs = 25 * 60_000L) {
                Pipe(ProCat.WELLBEING, ProactiveLines.wellbeing(label(pkg), mins), listOf(QuickChip("snooze", "10 more min", pkg), QuickChip("break", "Okay, break 🙆")))
            }
            if (!shown) scheduleNudge(pkg, 5 * 60_000L)   // try again a bit later (one runnable, not a poll loop)
        }
        usageNudge = r; main.postDelayed(r, afterMs)
    }

    /** "10 more min" on a break nudge. */
    fun snooze(pkg: String) {
        if (!started || pkg.isBlank()) return
        main.post { usageNudge?.let { main.removeCallbacks(it) }; scheduleNudge(pkg, 10 * 60_000L) }
    }

    private fun label(pkg: String) = runCatching { app.packageManager.getApplicationLabel(app.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg.substringAfterLast('.'))

    /** A rough screen type from the package (before any read), for context chips. */
    fun typeGuess(pkg: String): ScreenType? = when {
        pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("hotstar") || pkg.contains("primevideo") -> ScreenType.VIDEO_LIST
        pkg.contains("maps") -> ScreenType.MAPS
        pkg == "in.swiggy.android" || pkg == "com.application.zomato" -> ScreenType.FOOD
        pkg.contains("amazon") || pkg.contains("flipkart") || pkg.contains("myntra") || pkg.contains("meesho") || pkg.contains("ajio") -> ScreenType.SHOPPING
        pkg.startsWith("com.whatsapp") || pkg.contains("telegram") || pkg.contains("messaging") -> ScreenType.CHAT_LIST
        else -> null
    }

    // ---------------- unlock, charger ----------------
    private val sysReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_USER_PRESENT -> main.postDelayed({ greet() }, 2_500L)   // after Settai's own wake-up line
                Intent.ACTION_POWER_CONNECTED -> watchLevel(true)
                Intent.ACTION_POWER_DISCONNECTED -> { watchLevel(false); fullSaid = false }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    if (!fullSaid && sc > 0 && lvl * 100 / sc >= 100) {
                        fullSaid = true
                        if (!st.phoneMoment("full")) emit(ProCat.BATTERY) { Pipe(ProCat.BATTERY, ProactiveLines.full(), listOf(QuickChip.OK), excited = true) }
                    }
                }
            }
        }
    }
    private var levelWatch = false
    private var fullSaid = false

    /** BATTERY_CHANGED only while plugged in (it's chatty), to catch 100 %. */
    private fun watchLevel(on: Boolean) {
        if (on == levelWatch) return
        levelWatch = on
        if (on) runCatching { ContextCompat.registerReceiver(app, levelReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED) }
        else runCatching { app.unregisterReceiver(levelReceiver) }
    }
    private val levelReceiver = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) = sysReceiver.onReceive(c, i) }

    private fun plugged() = runCatching { app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0 }.getOrDefault(0) != 0

    private fun greet() {
        val c = Calendar.getInstance(); val h = c.get(Calendar.HOUR_OF_DAY)
        val day = c.get(Calendar.DAY_OF_YEAR)
        val p = app.getSharedPreferences("settai_proactive", Context.MODE_PRIVATE)
        val kind = when (h) { in 5..10 -> "morning"; in 21..23, in 0..1 -> "night"; else -> return }
        val k = "greet_$kind"
        val dayKey = if (kind == "night" && h < 2) day - 1 else day
        if (p.getInt(k, -1) == dayKey) return
        val name = if (st.groupEnabled(CapGroup.MEMORY)) runCatching { com.combo.monkeycompanion.powers.ExtraCaps.memory(app).name() }.getOrNull() else null
        val shown = emit(ProCat.GREETING) {
            if (kind == "morning") {
                val mChips = if (NotifStore.all().isNotEmpty()) listOf(QuickChip.NOTIF_CARD, QuickChip.say("My day?", "what's my day"), QuickChip.OK)
                else listOf(QuickChip.say("My day?", "what's my day"), QuickChip.say("Weather?", "weather today"), QuickChip.OK)
                Pipe(ProCat.GREETING, ProactiveLines.morning(name), mChips, excited = true)
            }
            else Pipe(ProCat.GREETING, ProactiveLines.night(name), listOf(QuickChip.OK, QuickChip.FUNNY))
        }
        if (shown) p.edit().putInt(k, dayKey).apply()
    }
}
