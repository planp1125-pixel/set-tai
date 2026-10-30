package com.combo.monkeycompanion.powers

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.core.app.NotificationManagerCompat
import com.combo.monkeycompanion.ai.AppSettings
import java.util.Calendar
import java.util.Locale

/**
 * Timers and alarms. "auto" (default): the phone's Clock app when Settai is allowed to open it
 * (an app screen of ours or the overlay is visible), otherwise Settai's own [SettaiClock].
 * "clock": always try the Clock app first (falls back to Settai's own if it can't).
 * "settai": always Settai's own. Every result is a sentence for the bubble and goes to [ActionLog].
 */
object ClockPowers {
    /** Seam for tests. */
    var gate: (Context) -> Boolean = { LaunchGate.canStartActivities(it) }
    var starter: (Context, Intent, String) -> LaunchGate.Result = { c, i, w -> LaunchGate.start(c, i, w) }

    fun niceDuration(sec: Int): String = when {
        sec % 3600 == 0 -> "${sec / 3600} h"
        sec >= 3600 -> "${sec / 3600} h ${(sec % 3600) / 60} min"
        sec >= 60 -> "${sec / 60} min" + (if (sec % 60 != 0) " ${sec % 60} s" else "")
        else -> "$sec s"
    }

    fun niceTime(h: Int, m: Int): String = String.format(Locale.US, "%d:%02d %s", if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")

    private fun clockAppIntent(i: Intent, ctx: Context) = i.resolveActivity(ctx.packageManager)

    /** Try the Clock app; null when we should fall back to our own. Adds a note to [why] when skipped. */
    private fun viaClockApp(ctx: Context, s: AppSettings, intent: Intent, what: String, why: StringBuilder): String? {
        if (s.clockMode == "settai") return null
        val handler = clockAppIntent(intent, ctx)
        if (handler == null) { why.append("there's no Clock app on this phone that takes timers/alarms"); return null }
        if (!gate(ctx)) { why.append("Android won't let me open the Clock app from the wallpaper/background"); return null }
        return when (val r = starter(ctx, intent, what)) {
            is LaunchGate.Result.Started -> { ActionLog.add(ctx, what, "sent to Clock app (${handler.packageName})"); "" }
            is LaunchGate.Result.Notified -> { why.append(r.reason); null }
            is LaunchGate.Result.Failed -> { why.append("the Clock app said no (${r.reason})"); null }
        }
    }

    private fun ownNotes(ctx: Context, inexact: Boolean): String = buildString {
        if (inexact) append(" Exact alarms are off for me, so it may ring a little late — allow “Alarms & reminders” for Settai in Android settings.")
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) append(" ⚠️ My notifications are off, so you won't hear it — please turn them on!")
    }

    fun setTimer(ctx: Context, s: AppSettings, sec: Int, label: String): String {
        if (sec <= 0 || sec > 24 * 3600) return "Hmm, a timer has to be between 1 second and 24 hours."
        val nice = niceDuration(sec)
        val what = "Timer $nice" + if (label.isNotBlank()) " ($label)" else ""
        val i = Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, sec).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .apply { if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label) }
        val why = StringBuilder()
        viaClockApp(ctx, s, i, what, why)?.let { return "Timer set in your Clock app: $nice ⏳" }
        val (item, inexact) = SettaiClock.add(ctx, "timer", System.currentTimeMillis() + sec * 1000L, label)
        val note = if (why.isNotEmpty()) " (I'm keeping this one myself: $why.)" else ""
        ActionLog.add(ctx, what, "Settai's own timer #${item.id}${if (inexact) " (inexact)" else ""}${if (why.isNotEmpty()) "; Clock app skipped: $why" else ""}")
        return "Timer set: $nice ⏳ I'll ring and shout when it's done!$note" + ownNotes(ctx, inexact)
    }

    fun nextOccurrence(h: Int, m: Int, now: Calendar = Calendar.getInstance()): Long {
        val c = (now.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        if (c.timeInMillis <= now.timeInMillis) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun setAlarm(ctx: Context, s: AppSettings, h: Int, m: Int, label: String, tomorrow: Boolean = false): String {
        if (h !in 0..23 || m !in 0..59) return "Hmm, that's not a time on any clock I know."
        val t = niceTime(h, m)
        val what = "Alarm $t" + if (label.isNotBlank()) " ($label)" else ""
        val i = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true).apply { if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label) }
        val why = StringBuilder()
        viaClockApp(ctx, s, i, what, why)?.let { return "Alarm set in your Clock app for $t ⏰" + if (tomorrow) " (the Clock app rings at the next $t)" else "" }
        var at = nextOccurrence(h, m)
        if (tomorrow) {
            val tmr = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            if (Calendar.getInstance().apply { timeInMillis = at }.get(Calendar.DAY_OF_YEAR) != tmr.get(Calendar.DAY_OF_YEAR)) at += 24 * 3600_000L
        }
        val (item, inexact) = SettaiClock.add(ctx, "alarm", at, label)
        val inH = (at - System.currentTimeMillis()) / 60_000
        val note = if (why.isNotEmpty()) " (It's my own alarm: $why.)" else ""
        ActionLog.add(ctx, what, "Settai's own alarm #${item.id}${if (inexact) " (inexact)" else ""}${if (why.isNotEmpty()) "; Clock app skipped: $why" else ""}")
        return "Alarm set for $t ⏰ (in ${SettaiClock.fmtLeft(inH * 60)}). I'll ring and jump around!$note" + ownNotes(ctx, inexact)
    }

    fun list(ctx: Context): String {
        val l = SettaiClock.upcoming(ctx)
        val own = if (l.isEmpty()) "I'm not keeping any timers or alarms right now." else "Mine: " + l.joinToString("; ") { it.describe() } + "."
        return "$own Ones in your Clock app are private to it, so I can't see those."
    }

    fun cancel(ctx: Context, kind: String): String {
        val gone = SettaiClock.cancel(ctx, kind)
        gone.forEach { SettaiClock.stop(ctx, it.id) }
        ActionLog.add(ctx, "Cancel $kind", if (gone.isEmpty()) "nothing of Settai's to cancel" else "cancelled ${gone.size}")
        val word = when (kind) { "timer" -> "timer"; "alarm" -> "alarm"; else -> "timer or alarm" }
        return if (gone.isEmpty()) "I don't have a $word of my own running. If it's in your Clock app, stop it there — I'm not allowed to touch those."
        else "Cancelled ${gone.size} $word${if (gone.size > 1) "s" else ""}: " + gone.joinToString("; ") { it.describe() }.replace(" left", " were left") + " 🛑"
    }
}
