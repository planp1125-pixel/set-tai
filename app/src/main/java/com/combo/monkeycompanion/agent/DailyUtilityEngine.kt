package com.combo.monkeycompanion.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.BatteryManager
import com.combo.monkeycompanion.mind.MindHub
import com.combo.monkeycompanion.powers.ActionLog
import java.util.Locale

/**
 * v9.5 Purposeful Daily Life Utilities Suite (`DailyUtilityEngine`):
 * Turns Settai from a fun screen companion into a purposeful daily life assistant:
 * 1. 🔐 1-Tap Smart OTP & Live Delivery / Cab / Train Catcher
 * 2. 🎒 5-Slot Pocket Clipboard Backpack (pin & 1-tap copy links, numbers, notes)
 * 3. 🧘 Doomscroll Breaker & 20-20-20 Eye / Neck Care Guard
 * 4. 📌 Sticky Chest Reminder Badge & Hourly Water / Medicine Nudge
 * 5. 🍅 20-Min Kids' Study / Focus Pomodoro Timer
 * 6. 🔋 Proactive Battery 85% Charge Guard & Overheat (>40°C) Watchdog
 */
object DailyUtilityEngine {
    private const val PREFS = "settai_daily_utilities_v95"
    private const val MAX_BACKPACK = 5

    data class BatteryHealthStatus(
        val levelPct: Int,
        val tempCelsius: Float,
        val charging: Boolean,
        val statusLine: String,
        val alertLine: String?,
    )

    private val extraOtpRegex = Regex("(?:OTP|code|verification|pin|password|passcode)[^0-9]{0,20}([0-9]{4,8})", RegexOption.IGNORE_CASE)
    private val deliveryApps = setOf(
        "swiggy", "zomato", "zepto", "blinkit", "bigbasket", "dunzo",
        "amazon", "flipkart", "uber", "ola", "rapido", "irctc", "whereismytrain", "redbus"
    )
    private val doomscrollPkgs = setOf(
        "com.instagram.android", "com.google.android.youtube", "com.twitter.android",
        "com.facebook.katana", "com.zhiliaoapp.musically", "com.reddit.frontpage", "in.mohalla.sharechat"
    )

    @Volatile var activeOtpCode: String? = null; private set
    @Volatile var activeOtpSource: String = ""; private set
    @Volatile var activeOtpExpiresAt: Long = 0L; private set

    @Volatile var activeDeliveryBadge: String? = null; private set
    @Volatile var activeDeliveryExpiresAt: Long = 0L; private set

    @Volatile var stickyReminderText: String = ""; private set
    /** v10.9: the eye break is a wall-clock end time (it no longer freezes while Settai is hidden or busy). */
    @Volatile var eyeBreakEndsAt: Long = 0L; private set
    val eyeBreakRemainingSec: Float get() = ((eyeBreakEndsAt - System.currentTimeMillis()) / 1000f).coerceAtLeast(0f)
    private var appCtx: Context? = null
    private var watchSavedAt = 0L
    @Volatile var doomscrollGuardEnabled: Boolean = true
    @Volatile var doomscrollLimitMinutes: Int = 20
    @Volatile var continuousWatchSeconds: Float = 0f; private set
    @Volatile var lastWatchedAppLabel: String = "YouTube / Reels"; private set
    @Volatile var pendingEdgeWakeAlert: String? = null; private set
    @Volatile var hourlyHydrationEnabled: Boolean = true
    @Volatile var batteryGuard85Enabled: Boolean = true

    private val backpackItems = ArrayList<String>()
    private var initialized = false

    @Volatile var onUtilityBadgeChanged: (() -> Unit)? = null

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun init(ctx: Context) {
        if (initialized) return
        initialized = true
        appCtx = ctx.applicationContext
        val p = prefs(ctx)
        // v10.9: timers survive a service restart / killed app (wall-clock, see docs/INTERRUPTION_AUDIT.md)
        val now = System.currentTimeMillis()
        eyeBreakEndsAt = p.getLong("eye_end", 0L).takeIf { it > now } ?: 0L
        val dExp = p.getLong("delivery_exp", 0L)
        if (dExp > now && activeDeliveryBadge == null) { activeDeliveryBadge = p.getString("delivery_badge", null); activeDeliveryExpiresAt = dExp }
        if (now - p.getLong("watch_saved_at", 0L) < 3 * 60_000L) continuousWatchSeconds = p.getFloat("watch_secs", 0f)
        stickyReminderText = p.getString("sticky_reminder", "") ?: ""
        doomscrollGuardEnabled = p.getBoolean("doomscroll_on", true)
        doomscrollLimitMinutes = p.getInt("doomscroll_mins", 20).coerceIn(5, 120)
        hourlyHydrationEnabled = p.getBoolean("hydration_on", true)
        batteryGuard85Enabled = p.getBoolean("battery_85_on", true)
        backpackItems.clear()
        val rawBackpack = p.getString("backpack_list", null)
        if (!rawBackpack.isNullOrBlank()) {
            rawBackpack.split("").map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_BACKPACK).forEach {
                backpackItems.add(it)
            }
        }
        if (backpackItems.isEmpty()) {
            backpackItems.add("📌 Tap 'Pin Clipboard' to save up to 5 copied links, numbers, or notes here!")
        }
    }

    private fun savePrefs(ctx: Context) {
        prefs(ctx).edit()
            .putString("sticky_reminder", stickyReminderText)
            .putBoolean("doomscroll_on", doomscrollGuardEnabled)
            .putInt("doomscroll_mins", doomscrollLimitMinutes)
            .putBoolean("hydration_on", hourlyHydrationEnabled)
            .putBoolean("battery_85_on", batteryGuard85Enabled)
            .putString("backpack_list", backpackItems.joinToString(""))
            .apply()
    }

    /** v10.9: saves the running timers (eye break end, delivery badge, Reels watch time) as wall-clock values. */
    private fun saveTimers() {
        val ctx = appCtx ?: return
        runCatching {
            prefs(ctx).edit()
                .putLong("eye_end", eyeBreakEndsAt)
                .putString("delivery_badge", activeDeliveryBadge).putLong("delivery_exp", if (activeDeliveryBadge != null) activeDeliveryExpiresAt else 0L)
                .putFloat("watch_secs", continuousWatchSeconds).putLong("watch_saved_at", System.currentTimeMillis())
                .apply()
        }
    }

    // ---------------- 1. Smart OTP & Live Delivery Catcher ----------------

    fun extractOtp(text: String): String? {
        Otp.find(text)?.let { return it }
        extraOtpRegex.find(text)?.groupValues?.getOrNull(1)?.let { return it }
        return null
    }

    fun extractDeliveryOrTransitStatus(pkg: String, app: String, title: String, text: String): String? {
        val combined = "$pkg $app $title $text".lowercase(Locale.ROOT)
        val matchedApp = deliveryApps.firstOrNull { it in combined }
        val hasKeywords = Regex("\b(out for delivery|arriving in|mins away|minutes away|at the gate|driver|rider|order picked|train|platform|pnr|coach)\b", RegexOption.IGNORE_CASE)
            .containsMatchIn("$title $text")
        if (matchedApp == null && !hasKeywords) return null
        val icon = when {
            "uber" in combined || "ola" in combined || "rapido" in combined || "cab" in combined -> "🚕"
            "irctc" in combined || "train" in combined || "pnr" in combined -> "🚆"
            else -> "🛵"
        }
        val cleanSnippet = text.ifBlank { title }.replace("\n", " ").trim().take(44)
        val appLabel = app.ifBlank { matchedApp?.replaceFirstChar { it.uppercase() } ?: "Delivery" }
        return "$icon $appLabel: $cleanSnippet"
    }

    fun onNotificationArrived(ctx: Context?, item: NotifItem, nowMs: Long = System.currentTimeMillis()) {
        val fullText = "${item.title} ${item.text}".trim()
        val otp = item.otp ?: extractOtp(fullText)
        if (otp != null) {
            activeOtpCode = otp
            activeOtpSource = item.app.ifBlank { "Message" }
            activeOtpExpiresAt = nowMs + 30_000L
            pendingEdgeWakeAlert = "🔐 OTP $otp from $activeOtpSource! Tap the green pill above me to copy it in 1 tap 📋"
            ctx?.let { ActionLog.add(it, "OTP caught: $otp from $activeOtpSource", "30s 1-tap copy pill shown") }
            onUtilityBadgeChanged?.invoke()
            return
        }
        val delivery = extractDeliveryOrTransitStatus(item.pkg, item.app, item.title, item.text)
        if (delivery != null) {
            activeDeliveryBadge = delivery
            activeDeliveryExpiresAt = nowMs + 90_000L
            saveTimers()
            ctx?.let { ActionLog.add(it, "Live Delivery/Transit caught", delivery) }
            onUtilityBadgeChanged?.invoke()
        }
    }

    fun triggerDemoOtp(ctx: Context? = null, code: String = "482910", source: String = "HDFC Bank", nowMs: Long = System.currentTimeMillis()) {
        activeOtpCode = code
        activeOtpSource = source
        activeOtpExpiresAt = nowMs + 30_000L
        ctx?.let { ActionLog.add(it, "Demo OTP triggered: $code ($source)", "1-tap copy pill active") }
        onUtilityBadgeChanged?.invoke()
    }

    fun triggerDemoDelivery(ctx: Context? = null, badge: String = "🛵 Swiggy: Order arriving in 4 mins", nowMs: Long = System.currentTimeMillis()) {
        activeDeliveryBadge = badge
        activeDeliveryExpiresAt = nowMs + 60_000L
        ctx?.let { ActionLog.add(it, "Demo Delivery tracker triggered", badge) }
        onUtilityBadgeChanged?.invoke()
    }

    fun copyActiveOtp(ctx: Context): String? {
        val code = activeOtpCode ?: return null
        copyToSystemClipboard(ctx, "OTP", code)
        activeOtpCode = null
        activeOtpExpiresAt = 0L
        ActionLog.add(ctx, "Copied OTP via 1-tap pill", code)
        onUtilityBadgeChanged?.invoke()
        return code
    }

    fun clearDeliveryBadge() {
        activeDeliveryBadge = null
        activeDeliveryExpiresAt = 0L
        saveTimers()
        onUtilityBadgeChanged?.invoke()
    }

    // ---------------- 2. Pocket Clipboard Backpack (5 Slots) ----------------

    @Synchronized
    fun clipboardBackpack(ctx: Context): List<String> {
        init(ctx)
        return backpackItems.toList()
    }

    @Synchronized
    fun pinToBackpack(ctx: Context, text: String): List<String> {
        init(ctx)
        val clean = text.trim()
        if (clean.isEmpty()) return backpackItems.toList()
        backpackItems.removeAll { it.equals(clean, ignoreCase = true) || it.startsWith("📌 Tap 'Pin Clipboard'") }
        backpackItems.add(0, clean.take(180))
        while (backpackItems.size > MAX_BACKPACK) {
            backpackItems.removeAt(backpackItems.lastIndex)
        }
        savePrefs(ctx)
        onUtilityBadgeChanged?.invoke()
        return backpackItems.toList()
    }

    fun pinCurrentSystemClipboard(ctx: Context): String? {
        init(ctx)
        val cm = ctx.getSystemService(ClipboardManager::class.java)
        val clip = cm?.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(ctx)?.toString()?.trim().orEmpty()
        } else ""
        if (text.isBlank()) return null
        pinToBackpack(ctx, text)
        return text
    }

    @Synchronized
    fun copyBackpackItem(ctx: Context, index: Int): String? {
        init(ctx)
        val item = backpackItems.getOrNull(index) ?: return null
        copyToSystemClipboard(ctx, "Set-Tai Backpack", item)
        return item
    }

    @Synchronized
    fun removeBackpackItem(ctx: Context, index: Int): List<String> {
        init(ctx)
        if (index in backpackItems.indices) {
            backpackItems.removeAt(index)
            savePrefs(ctx)
        }
        return backpackItems.toList()
    }

    fun copyToSystemClipboard(ctx: Context, label: String, text: String) {
        runCatching {
            val cm = ctx.getSystemService(ClipboardManager::class.java)
            cm?.setPrimaryClip(ClipData.newPlainText(label, text))
        }
    }

    // ---------------- 3. Doomscroll Breaker & 20-20-20 Eye Care ----------------

    fun triggerEyeBreakNow(seconds: Float = 20f) {
        eyeBreakEndsAt = System.currentTimeMillis() + (seconds.coerceIn(5f, 60f) * 1000f).toLong()
        saveTimers()
        onUtilityBadgeChanged?.invoke()
    }

    fun checkContinuousAppSession(pkg: String, continuousMinutes: Int): Boolean {
        if (!doomscrollGuardEnabled) return false
        val isDoomApp = pkg.lowercase(Locale.ROOT) in doomscrollPkgs
        if ((isDoomApp || continuousMinutes >= doomscrollLimitMinutes) && continuousMinutes >= doomscrollLimitMinutes && eyeBreakRemainingSec <= 0f) {
            lastWatchedAppLabel = appLabelForPkg(pkg)
            continuousWatchSeconds = 0f
            triggerEyeBreakNow(20f)
            pendingEdgeWakeAlert = "🎬 ${continuousMinutes}m on $lastWatchedAppLabel! Popped out from the edge for a 20s Eye & Neck Break 👀🌿"
            onUtilityBadgeChanged?.invoke()
            return true
        }
        return false
    }

    fun appLabelForPkg(pkg: String): String {
        val p = pkg.lowercase(Locale.ROOT)
        return when {
            "youtube" in p -> "YouTube"
            "instagram" in p -> "Instagram Reels"
            "twitter" in p || "x.android" in p -> "X / Twitter"
            "facebook" in p -> "Facebook Video"
            "reddit" in p -> "Reddit"
            else -> "Screen / Video"
        }
    }

    /**
     * v9.5 Edge-Tucked Screen-Time & YouTube/Reels Watchdog!
     * Runs at 1 Hz even when Settai is sitting paused beside the screen edge with the `<` arrow (`hiddenTab == true`).
     * Tracks continuous watching time on YouTube/Reels/screen and returns a wake-up alert line when the limit is reached!
     */
    fun tickEdgeWatchdog(dtSec: Float, currentPkg: String = ""): String? {
        pendingEdgeWakeAlert?.let { alert ->
            pendingEdgeWakeAlert = null
            return alert
        }
        if (currentPkg.isNotBlank()) {
            lastWatchedAppLabel = appLabelForPkg(currentPkg)
        }
        if (doomscrollGuardEnabled) {
            continuousWatchSeconds += dtSec
            if (System.currentTimeMillis() - watchSavedAt > 15_000L) { watchSavedAt = System.currentTimeMillis(); saveTimers() }
            val limitSec = (doomscrollLimitMinutes * 60f).coerceAtLeast(60f)
            if (continuousWatchSeconds >= limitSec) {
                continuousWatchSeconds = 0f
                triggerEyeBreakNow(20f)
                return "🎬 ${doomscrollLimitMinutes}m watching $lastWatchedAppLabel! I popped out from the edge for a 20s Eye & Neck Break 👀🌿"
            }
        }
        return null
    }

    fun snoozeDoomscrollMinutes(extraMins: Int = 5) {
        continuousWatchSeconds = -extraMins * 60f
        eyeBreakEndsAt = 0L
        saveTimers()
        onUtilityBadgeChanged?.invoke()
    }

    // ---------------- 4. Sticky Chest Reminder & Hydration Buddy ----------------

    fun setStickyReminder(ctx: Context, text: String) {
        init(ctx)
        stickyReminderText = text.trim().take(48)
        savePrefs(ctx)
        onUtilityBadgeChanged?.invoke()
    }

    fun clearStickyReminder(ctx: Context) {
        init(ctx)
        stickyReminderText = ""
        savePrefs(ctx)
        onUtilityBadgeChanged?.invoke()
    }

    // ---------------- 5. 20-Min Focus / Kids' Study Pomodoro Timer ----------------

    fun startFocusTimer(ctx: Context, minutes: Int = 20) {
        MindHub.init(ctx)
        ActivityScheduler.attach(ctx)
        MindHub.focusBuddy.start(minutes.coerceIn(1, 90))
        onUtilityBadgeChanged?.invoke()
    }

    fun stopFocusTimer() {
        MindHub.focusBuddy.stop()
        onUtilityBadgeChanged?.invoke()
    }

    // ---------------- 6. Proactive Battery 85% Charge Guard & Heat Watchdog ----------------

    fun readBatteryHealth(ctx: Context): BatteryHealthStatus {
        init(ctx)
        val bi = runCatching {
            ctx.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, 78)?.takeIf { it >= 0 } ?: 78
        val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.takeIf { it > 0 } ?: 100
        val pct = ((level * 100f) / scale).toInt().coerceIn(0, 100)
        val rawTemp = bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 315)?.takeIf { it > 0 } ?: 315
        val tempC = rawTemp / 10f
        val plugged = (bi?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

        val alert = when {
            tempC >= 40.0f -> "🌡️ Overheat Alert (${"%.1f".format(Locale.ROOT, tempC)}°C)! Let phone cool down."
            plugged && pct >= 85 && batteryGuard85Enabled -> "🔌 Battery Charge Guard: ${pct}% reached — unplug to protect battery health!"
            else -> null
        }
        val status = "🔋 Battery: ${pct}% (${if (plugged) "⚡ Charging" else "On Battery"}) · 🌡️ Temp: ${"%.1f".format(Locale.ROOT, tempC)}°C" +
            (if (batteryGuard85Enabled) " · 85% Guard ON ✅" else "")
        return BatteryHealthStatus(pct, tempC, plugged, status, alert)
    }

    // ---------------- Overlay Step, Drawing & Hit-Testing ----------------

    fun step(dt: Float, nowMs: Long = System.currentTimeMillis()): Boolean {
        var active = false
        if (activeOtpCode != null) {
            if (nowMs >= activeOtpExpiresAt) activeOtpCode = null else active = true
        }
        if (activeDeliveryBadge != null) {
            if (nowMs >= activeDeliveryExpiresAt) activeDeliveryBadge = null else active = true
        }
        if (eyeBreakEndsAt > 0L) {
            if (nowMs >= eyeBreakEndsAt) { eyeBreakEndsAt = 0L; saveTimers() } else active = true
        }
        if (stickyReminderText.isNotBlank()) active = true
        if (MindHub.focusBuddy.active) active = true
        return active
    }

    fun hasActiveOverlayPill(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (activeOtpCode != null && nowMs < activeOtpExpiresAt) return true
        if (activeDeliveryBadge != null && nowMs < activeDeliveryExpiresAt) return true
        if (eyeBreakRemainingSec > 0f) return true
        if (stickyReminderText.isNotBlank()) return true
        return false
    }

    fun activePrimaryPillLabel(nowMs: Long = System.currentTimeMillis()): String? = when {
        activeOtpCode != null && nowMs < activeOtpExpiresAt -> "🔐 OTP: $activeOtpCode [📋 Copy]"
        eyeBreakRemainingSec > 0f -> "🧘 Eye Break: ${eyeBreakRemainingSec.toInt()}s 👀"
        activeDeliveryBadge != null && nowMs < activeDeliveryExpiresAt -> activeDeliveryBadge
        stickyReminderText.isNotBlank() -> "📌 $stickyReminderText [✓]"
        else -> null
    }

    private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    /**
     * Draws the active purposeful utility pill (OTP 1-Tap Copy, Eye-Break Timer, Live Delivery Tracker, or Sticky Reminder)
     * cleanly above/across the companion inside `MonkeyView`, populating [outPillRect] for 1-tap hit testing!
     */
    fun drawOverlayPill(
        c: Canvas,
        cx: Float,
        topAnchorY: Float,
        viewWidth: Float,
        density: Float,
        outPillRect: RectF,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val label = activePrimaryPillLabel(nowMs) ?: run {
            outPillRect.setEmpty()
            return false
        }
        pillTextPaint.textSize = 10.2f * density
        val padH = 8f * density
        val pillH = 22f * density
        val maxW = (viewWidth - 6f * density).coerceAtLeast(78f * density)
        var display = label
        while (display.length > 8 && pillTextPaint.measureText(display) + padH * 2 > maxW) {
            display = display.dropLast(2) + "…"
        }
        val pillW = (pillTextPaint.measureText(display) + padH * 2).coerceIn(68f * density, maxW)
        val left = (cx - pillW / 2f).coerceIn(2f * density, (viewWidth - pillW - 2f * density).coerceAtLeast(2f * density))
        val top = topAnchorY.coerceAtLeast(3f * density)
        outPillRect.set(left, top, left + pillW, top + pillH)

        val isOtp = activeOtpCode != null && nowMs < activeOtpExpiresAt
        val isEye = eyeBreakRemainingSec > 0f
        pillBgPaint.color = when {
            isOtp -> Color.rgb(0x1B, 0x5E, 0x20)
            isEye -> Color.rgb(0x00, 0x60, 0x64)
            activeDeliveryBadge != null -> Color.rgb(0xE6, 0x51, 0x00)
            else -> Color.rgb(0x31, 0x1B, 0x92)
        }
        pillStrokePaint.strokeWidth = 1.5f * density
        pillStrokePaint.color = Color.rgb(0xFF, 0xD5, 0x4F)

        c.drawRoundRect(outPillRect, pillH / 2f, pillH / 2f, pillBgPaint)
        c.drawRoundRect(outPillRect, pillH / 2f, pillH / 2f, pillStrokePaint)
        val ty = outPillRect.centerY() - (pillTextPaint.descent() + pillTextPaint.ascent()) / 2f
        c.drawText(display, outPillRect.centerX(), ty, pillTextPaint)
        return true
    }

    /** Handles a tap on [outPillRect] inside `MonkeyView`. Returns a short confirmation message if handled. */
    fun onOverlayPillTapped(ctx: Context, nowMs: Long = System.currentTimeMillis()): String? {
        if (activeOtpCode != null && nowMs < activeOtpExpiresAt) {
            val copied = copyActiveOtp(ctx)
            return "✅ Copied OTP $copied to clipboard! 📋"
        }
        if (eyeBreakRemainingSec > 0f) {
            eyeBreakEndsAt = 0L
            saveTimers()
            onUtilityBadgeChanged?.invoke()
            return "👀 Eye & neck stretch complete! Proud of you, machan ✨"
        }
        if (activeDeliveryBadge != null && nowMs < activeDeliveryExpiresAt) {
            val msg = activeDeliveryBadge
            clearDeliveryBadge()
            return "✅ Dismissed tracker: $msg"
        }
        if (stickyReminderText.isNotBlank()) {
            val done = stickyReminderText
            clearStickyReminder(ctx)
            return "✅ Completed sticky reminder: “$done” 🎉"
        }
        return null
    }

    fun resetForTest(ctx: Context? = null) {
        activeOtpCode = null
        activeOtpSource = ""
        activeOtpExpiresAt = 0L
        activeDeliveryBadge = null
        activeDeliveryExpiresAt = 0L
        stickyReminderText = ""
        eyeBreakEndsAt = 0L
        appCtx = null
        continuousWatchSeconds = 0f
        pendingEdgeWakeAlert = null
        backpackItems.clear()
        ctx?.let { prefs(it).edit().clear().commit() }
        initialized = false
    }
}
