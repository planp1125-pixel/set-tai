package com.combo.monkeycompanion.agent

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.combo.monkeycompanion.MainActivity
import com.combo.monkeycompanion.R
import com.combo.monkeycompanion.mind.MindHub

/**
 * v10.9 persistent owner of every timed activity (see docs/INTERRUPTION_AUDIT.md).
 *
 * Before v10.9 the study / focus Pomodoro was a float counted down by the overlay's frame loop and STOPPED by any
 * power or drag, so "use a power" = "forget the 25-minute study timer". Now:
 *  - every timer is a WALL-CLOCK end time saved in prefs the moment it changes;
 *  - the overlay only *reads* it each frame (powers, chat, Smash, switching characters, Duo/Trio, hide/show
 *    can't change it, and Duo/Trio no longer counts it down 2–3× too fast);
 *  - an AlarmManager alarm fires at the end even if the overlay or the whole process is gone, and a session
 *    that ended while Settai was away is celebrated once on the next start;
 *  - the overlay re-applies the study pose (desk + notebook + sitting) whenever nothing else is using the body.
 */
object ActivityScheduler {
    private const val PREFS = "settai_activity_timers_v109"
    const val ACTION_FOCUS_DONE = "com.combo.monkeycompanion.FOCUS_DONE"
    private const val REQ_FOCUS = 41090
    private const val CH = "settai_timers"
    private const val NOTE_ID = 41091

    private var app: Context? = null

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Idempotent: hooks the focus timer to prefs + alarm and restores anything that was running. */
    @Synchronized
    fun attach(ctx: Context) {
        val a = ctx.applicationContext
        DailyUtilityEngine.init(a)
        val fb = MindHub.focusBuddy
        if (app == null || fb.onChange == null) {
            app = a
            restoreFocus(a)
            fb.onChange = { f -> saveFocus(a, f.active, f.endAtMs, f.totalSecs) }
        }
    }

    // ---------------- focus / study Pomodoro ----------------

    private fun restoreFocus(ctx: Context) {
        val p = prefs(ctx)
        val end = p.getLong("focus_end", 0L)
        val total = p.getFloat("focus_total", 0f)
        if (end <= 0L) return
        val fb = MindHub.focusBuddy
        if (fb.active) return
        if (!fb.restore(end, total)) {
            // it ended while Settai was away: celebrate once on the next frame, then forget it
            p.edit().putLong("focus_end", 0L).putBoolean("focus_done_unseen", true).apply()
            cancelAlarm(ctx)
        }
    }

    private fun saveFocus(ctx: Context, active: Boolean, endAt: Long, total: Float) {
        val e = prefs(ctx).edit()
        if (active) {
            e.putLong("focus_end", endAt).putFloat("focus_total", total).putBoolean("focus_done_unseen", false)
            scheduleAlarm(ctx, endAt)
        } else {
            e.putLong("focus_end", 0L)
            cancelAlarm(ctx)
        }
        e.commit()
    }

    /** True once when a session finished while no overlay was ticking it (process killed, overlay off). */
    fun consumeFocusDoneUnseen(ctx: Context): Boolean {
        val p = prefs(ctx)
        if (!p.getBoolean("focus_done_unseen", false)) return false
        p.edit().putBoolean("focus_done_unseen", false).apply()
        return true
    }

    /** The saved end time (tests / audit). */
    fun savedFocusEnd(ctx: Context): Long = prefs(ctx).getLong("focus_end", 0L)

    private fun pi(ctx: Context) = PendingIntent.getBroadcast(ctx, REQ_FOCUS,
        Intent(ctx, ActivityAlarmReceiver::class.java).setAction(ACTION_FOCUS_DONE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun scheduleAlarm(ctx: Context, at: Long) = runCatching {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return@runCatching
        if (com.combo.monkeycompanion.powers.SettaiClock.exactAllowed(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx))
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx))
    }

    private fun cancelAlarm(ctx: Context) = runCatching { ctx.getSystemService(AlarmManager::class.java)?.cancel(pi(ctx)) }

    /** The alarm: if no overlay finished the session in time, leave a notification and a celebrate-later flag. */
    internal fun onAlarm(ctx: Context) {
        val p = prefs(ctx)
        val end = p.getLong("focus_end", 0L)
        if (end <= 0L || end > System.currentTimeMillis() + 1500L) return   // already handled, or moved
        val fb = MindHub.focusBuddy
        if (fb.active && fb.endAtMs == end) return   // the overlay is alive: it finishes (and celebrates) on its next frame
        p.edit().putLong("focus_end", 0L).putBoolean("focus_done_unseen", true).apply()
        notifyDone(ctx)
    }

    private fun notifyDone(ctx: Context) = runCatching {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return@runCatching
        nm.createNotificationChannel(NotificationChannel(CH, "Settai study & focus timers", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 4, Intent(ctx, com.combo.monkeycompanion.ui.SettaiDesignActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(NOTE_ID, NotificationCompat.Builder(ctx, CH).let { com.combo.monkeycompanion.overlay.CharacterIcon.applyTo(it, ctx) }
            .setContentTitle("🍅 Study session complete!")
            .setContentText("Proud of you! Time for a stretch & water break 🏆")
            .setContentIntent(open).setAutoCancel(true).build())
    }

    // ---------------- sleep nag (v5 Stage) ----------------

    /** Wall-clock now minus elapsedRealtime: converts the Stage's elapsed-realtime stamps to storable wall times. */
    private fun bootWall() = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    /** Saves the nag's snooze / last-nag (elapsedRealtime stamps) so a character switch or restart keeps them. */
    fun saveSleepNag(ctx: Context, snoozedUntilElapsed: Long, lastNagAtElapsed: Long, lastLevel: Int) {
        val b = bootWall()
        prefs(ctx).edit()
            .putLong("nag_snooze_wall", if (snoozedUntilElapsed > 0L) snoozedUntilElapsed + b else 0L)
            .putLong("nag_last_wall", if (lastNagAtElapsed > 0L) lastNagAtElapsed + b else 0L)
            .putInt("nag_level", lastLevel).apply()
    }

    /** Returns (snoozedUntil, lastNagAt) as elapsedRealtime stamps (0 = none) and the last level. */
    fun loadSleepNag(ctx: Context): Triple<Long, Long, Int> {
        val p = prefs(ctx); val b = bootWall()
        val s = p.getLong("nag_snooze_wall", 0L).let { if (it > 0L) (it - b).coerceAtLeast(1L) else 0L }
        val l = p.getLong("nag_last_wall", 0L).let { if (it > 0L) (it - b).coerceAtLeast(1L) else 0L }
        return Triple(s, l, p.getInt("nag_level", 0))
    }

    /** Screen-on start as a wall time (the 10-minute screen-on rule survives a character switch). */
    fun saveScreenOnSince(ctx: Context, elapsed: Long) { prefs(ctx).edit().putLong("screen_on_wall", if (elapsed > 0L) elapsed + bootWall() else 0L).apply() }
    fun loadScreenOnSince(ctx: Context): Long = prefs(ctx).getLong("screen_on_wall", 0L).let { if (it > 0L) (it - bootWall()).coerceAtLeast(1L) else 0L }

    /** Tests: forget the in-memory hookup (like a killed process) but keep what's saved. */
    fun resetForTestKeepPrefs() { MindHub.focusBuddy.onChange = null; app = null }

    fun resetForTest(ctx: Context) {
        cancelAlarm(ctx)
        prefs(ctx).edit().clear().commit()
        MindHub.focusBuddy.onChange = null
        app = null
    }
}

/** Fires at the end of a study / focus session (see [ActivityScheduler]). */
class ActivityAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ActivityScheduler.ACTION_FOCUS_DONE) ActivityScheduler.onAlarm(context.applicationContext)
    }
}
