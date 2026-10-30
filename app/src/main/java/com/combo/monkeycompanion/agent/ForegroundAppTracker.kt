package com.combo.monkeycompanion.agent

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process

/**
 * v8.0 Play Store-compliant foreground app & place detector:
 *  - On the `full` build when Accessibility is enabled, uses [SettaiAccessibilityService.instance].
 *  - On the `play` build (where `SettaiAccessibilityService` is stripped from the manifest) or whenever
 *    Accessibility is off, uses `UsageStatsManager` (`PACKAGE_USAGE_STATS`), piggybacking on `MindHub`'s
 *    existing visible-only heartbeat (zero background polling!).
 */
object ForegroundAppTracker {
    @Volatile var forcePlayModeForTest: Boolean? = null
    @Volatile var lastKnownPkg: String = ""; private set

    private val IGNORE = setOf("android", "com.android.systemui", "com.google.android.inputmethod.latin")

    /**
     * True when running the `play` flavor APK (where `SettaiAccessibilityService` is removed from `AndroidManifest.xml`).
     */
    fun isPlayStoreBuild(ctx: Context? = null): Boolean {
        forcePlayModeForTest?.let { return it }
        if (ctx == null) return false
        return runCatching {
            val comp = ComponentName(ctx, SettaiAccessibilityService::class.java)
            @Suppress("DEPRECATION")
            ctx.packageManager.getServiceInfo(comp, 0)
            false
        }.getOrDefault(true)
    }

    /** True when the user has granted Usage Access (`PACKAGE_USAGE_STATS`) in Android Settings. */
    fun hasUsageAccess(ctx: Context): Boolean = runCatching {
        val ops = ctx.getSystemService(AppOpsManager::class.java) ?: return@runCatching false
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /**
     * Queries the current foreground app package via `SettaiAccessibilityService` (if active) or
     * `UsageStatsManager.queryEvents` (Play Store-compliant fallback).
     */
    fun queryForegroundPkg(ctx: Context, now: Long = System.currentTimeMillis()): String? {
        SettaiAccessibilityService.instance?.currentPkg?.takeIf { it.isNotBlank() }?.let {
            lastKnownPkg = it
            return it
        }
        return runCatching {
            val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return@runCatching null
            val events = usm.queryEvents(now - 180_000L, now)
            val ev = UsageEvents.Event()
            var latestPkg: String? = null
            var latestTs = 0L
            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(ev)
                if (ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED && ev.timeStamp >= latestTs) {
                    val p = ev.packageName.orEmpty()
                    if (p.isNotBlank() && p != ctx.packageName && p !in IGNORE && !p.contains("inputmethod", true) && !p.contains("keyboard", true)) {
                        latestPkg = p
                        latestTs = ev.timeStamp
                    }
                }
            }
            if (latestPkg != null) {
                lastKnownPkg = latestPkg
                return@runCatching latestPkg
            }
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 600_000L, now).orEmpty()
            val best = stats.filter { s ->
                val p = s.packageName.orEmpty()
                p.isNotBlank() && p != ctx.packageName && p !in IGNORE && s.lastTimeUsed > 0L
            }.maxByOrNull { it.lastTimeUsed }?.packageName
            if (best != null) lastKnownPkg = best
            best
        }.getOrNull()
    }

    /** Piggybacks on `MindHub.heartbeat()` (only while Settai is visible on screen) to detect app switches via UsageStats. */
    fun pollOnHeartbeat(ctx: Context?) {
        val c = ctx ?: return
        if (SettaiAccessibilityService.instance != null) return
        val pkg = queryForegroundPkg(c) ?: return
        if (pkg.isNotBlank()) Proactive.onWindow(pkg)
    }
}
