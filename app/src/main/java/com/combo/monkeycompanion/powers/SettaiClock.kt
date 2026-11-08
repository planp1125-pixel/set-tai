package com.combo.monkeycompanion.powers

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.combo.monkeycompanion.MainActivity
import com.combo.monkeycompanion.R
import com.combo.monkeycompanion.ai.BodyAction
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.ui.RingActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settai's own timers and alarms (used when the phone's Clock app can't be opened, or when chosen
 * in Settings). Scheduled with AlarmManager.setAlarmClock (exact, fires in Doze, shows the alarm
 * icon), kept across reboots, and rung with an insistent alarm-sound notification + a full-screen
 * ring screen over the lock screen. Settai also jumps and announces it.
 */
object SettaiClock {
    data class Item(val id: Int, val kind: String, val at: Long, val label: String) {
        fun describe(now: Long = System.currentTimeMillis()): String {
            val left = ((at - now) / 1000).coerceAtLeast(0)
            val clock = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(at))
            return if (kind == "timer") "⏳ timer${if (label.isNotBlank()) " “$label”" else ""}: ${fmtLeft(left)} left (rings $clock)"
            else "⏰ alarm${if (label.isNotBlank()) " “$label”" else ""} at $clock"
        }
    }

    private const val PREFS = "settai_clock"
    const val CHANNEL = "settai_alarm2"
    const val ACTION_FIRE = "com.combo.monkeycompanion.CLOCK_FIRE"
    const val ACTION_STOP = "com.combo.monkeycompanion.CLOCK_STOP"
    const val EXTRA_ID = "id"

    fun fmtLeft(sec: Long): String = when {
        sec >= 3600 -> "${sec / 3600} h ${(sec % 3600) / 60} min"
        sec >= 60 -> "${sec / 60} min" + if (sec % 60 != 0L) " ${sec % 60} s" else ""
        else -> "$sec s"
    }

    @Synchronized fun items(ctx: Context): List<Item> {
        val arr = runCatching { JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("items", "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { Item(it.getInt("id"), it.getString("kind"), it.getLong("at"), it.optString("label")) }
    }

    @Synchronized private fun save(ctx: Context, l: List<Item>) {
        val arr = JSONArray(); l.forEach { arr.put(JSONObject().put("id", it.id).put("kind", it.kind).put("at", it.at).put("label", it.label)) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("items", arr.toString()).apply()
    }

    /** Upcoming items, soonest first (drops anything long past). */
    fun upcoming(ctx: Context): List<Item> = items(ctx).filter { it.at > System.currentTimeMillis() - 60_000 }.sortedBy { it.at }

    fun exactAllowed(ctx: Context): Boolean = Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Schedules and returns the item; [inexact] tells the caller exact alarms weren't allowed. */
    fun add(ctx: Context, kind: String, at: Long, label: String): Pair<Item, Boolean> {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = p.getInt("next", 1).also { p.edit().putInt("next", it + 1).apply() }
        val item = Item(id, kind, at, label)
        save(ctx, items(ctx).filter { it.at > System.currentTimeMillis() - 60_000 } + item)
        val exact = schedule(ctx, item)
        return item to !exact
    }

    private fun firePi(ctx: Context, id: Int) = PendingIntent.getBroadcast(
        ctx, id, Intent(ctx, ClockReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun schedule(ctx: Context, item: Item): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = firePi(ctx, item.id)
        return if (exactAllowed(ctx)) {
            val show = PendingIntent.getActivity(ctx, 0, Intent(ctx, com.combo.monkeycompanion.ui.SettaiDesignActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            am.setAlarmClock(AlarmManager.AlarmClockInfo(item.at, show), pi)
            true
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.at, pi)
            false
        }
    }

    /** Cancel by kind ("timer", "alarm", "all") or id. Returns what was cancelled. */
    fun cancel(ctx: Context, kind: String = "all", id: Int? = null): List<Item> {
        val all = items(ctx)
        val gone = all.filter { (id == null || it.id == id) && (kind == "all" || it.kind == kind) }
        val am = ctx.getSystemService(AlarmManager::class.java)
        gone.forEach { am.cancel(firePi(ctx, it.id)) }
        save(ctx, all - gone.toSet())
        return gone
    }

    fun rescheduleAll(ctx: Context) {
        val now = System.currentTimeMillis()
        val keep = items(ctx).filter { it.at > now }
        save(ctx, keep)
        keep.forEach { schedule(ctx, it) }
    }

    fun channel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "Settai's timers & alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Rings when a timer or alarm Settai set for you goes off"
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true); vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        nm.createNotificationChannel(ch)
    }

    /** Ring: insistent alarm notification (+ full-screen ring screen when locked), and Settai reacts. */
    fun ring(ctx: Context, id: Int) {
        val item = items(ctx).firstOrNull { it.id == id }
        save(ctx, items(ctx).filter { it.id != id })
        val title = if (item?.kind == "alarm") "⏰ Alarm${item.label.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}"
        else "⏳ Time's up${item?.label?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}!"
        val line = if (item?.kind == "alarm") "Wake up wake up! ${item.label.ifBlank { "Your alarm!" }} 🐒⏰" else "Ding ding! Your ${item?.label?.ifBlank { null } ?: "timer"} is done! 🍌"
        channel(ctx)
        val stop = PendingIntent.getBroadcast(ctx, 9000 + id, Intent(ctx, ClockReceiver::class.java).setAction(ACTION_STOP).putExtra(EXTRA_ID, id), PendingIntent.FLAG_IMMUTABLE)
        val full = PendingIntent.getActivity(ctx, 9500 + id, Intent(ctx, RingActivity::class.java).putExtra(EXTRA_ID, id).putExtra("title", title).putExtra("line", line)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .let { com.combo.monkeycompanion.overlay.CharacterIcon.applyTo(it, ctx) }
            .setContentTitle(title).setContentText(line)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setDeleteIntent(stop)
            .setOngoing(true)
            .setTimeoutAfter(10 * 60_000L)
            .addAction(0, "Stop", stop)
            .build()
        n.flags = n.flags or android.app.Notification.FLAG_INSISTENT
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(NOTIF_BASE + id, n) }
            .onFailure { ActionLog.add(ctx, "Ring $title", "notification failed: ${it.message}", false) }
        ActionLog.add(ctx, "Rang: $title", "notification posted (sound: alarm channel)")
        ChatHub.init(ctx)
        ChatHub.alert(line, BodyAction.JUMP)
    }

    fun stop(ctx: Context, id: Int) {
        ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_BASE + id)
        RingActivity.closeFor(id)   // never leave the full-screen ring (and its keep-screen-on) behind
    }

    const val NOTIF_BASE = 7000
}

class ClockReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        when (i.action) {
            SettaiClock.ACTION_FIRE -> SettaiClock.ring(ctx, i.getIntExtra(SettaiClock.EXTRA_ID, -1))
            SettaiClock.ACTION_STOP -> SettaiClock.stop(ctx, i.getIntExtra(SettaiClock.EXTRA_ID, -1))
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.LOCKED_BOOT_COMPLETED",
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                SettaiClock.rescheduleAll(ctx)
                runCatching { com.combo.monkeycompanion.agent.RoutineScheduler.refresh(ctx) }   // v4 routines
            }
        }
    }
}
