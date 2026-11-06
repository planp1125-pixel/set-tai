package com.combo.monkeycompanion.powers

import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.combo.monkeycompanion.R

/**
 * Starting another app's screen from Settai. Android (10+, tightened in 14/15) silently blocks
 * activity starts from the background. Settai may start them when:
 *  - one of its own screens is in front ([foregroundActivities] > 0), or
 *  - the floating overlay is on screen and "Display over other apps" is granted (Android 15
 *    requires the overlay window to be visible).
 * From the live wallpaper (or with the overlay hidden) it can't, so it posts a notification the
 * user taps instead, and says so in the bubble.
 */
object LaunchGate {
    @Volatile var foregroundActivities = 0
    @Volatile var overlayVisible = false

    fun canStartActivities(ctx: Context): Boolean =
        foregroundActivities > 0 || (overlayVisible && Settings.canDrawOverlays(ctx))

    sealed class Result {
        data object Started : Result()
        data class Notified(val reason: String) : Result()
        data class Failed(val reason: String) : Result()
    }

    /** Start [intent]; falls back to a "tap to open" notification. [what] is e.g. "Clock timer". */
    fun start(ctx: Context, intent: Intent, what: String): Result {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(ctx.packageManager) == null) return Result.Failed("no app on this phone handles it")
        if (canStartActivities(ctx)) {
            return try {
                val pi = PendingIntent.getActivity(ctx, what.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                if (Build.VERSION.SDK_INT >= 34) {
                    val o = ActivityOptions.makeBasic()
                    @Suppress("DEPRECATION")
                    o.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    pi.send(ctx, 0, null, null, null, null, o.toBundle())
                } else pi.send()
                Result.Started
            } catch (e: Exception) {
                runCatching { ctx.startActivity(intent); Result.Started }.getOrElse { Result.Failed(e.message ?: e.javaClass.simpleName) }
            }
        }
        val why = "Android doesn't let me open other apps from ${if (overlayVisible) "here" else "the wallpaper / background"}"
        return if (notifyTapToOpen(ctx, intent, what)) Result.Notified(why) else Result.Failed("$why, and notifications are off")
    }

    private const val CHANNEL = "settai_open"

    private fun notifyTapToOpen(ctx: Context, intent: Intent, what: String): Boolean {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return false
        if (nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Things Settai opens for you", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(ctx, what.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .let { com.combo.monkeycompanion.overlay.CharacterIcon.applyTo(it, ctx) }
            .setContentTitle("Tap to open: $what")
            .setContentText("Settai can't open it from the wallpaper by himself.")
            .setContentIntent(pi).setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        return runCatching { nm.notify(4000 + (what.hashCode() and 0xff), n); true }.getOrDefault(false)
    }

    fun hasNotifPermission(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
