package com.combo.monkeycompanion.agent

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.NotificationCompat
import com.combo.monkeycompanion.R
import com.combo.monkeycompanion.ai.AppSettings
import com.combo.monkeycompanion.ai.BodyAction
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.powers.ActionLog
import com.combo.monkeycompanion.powers.CapGroup
import com.combo.monkeycompanion.powers.Invocation
import com.combo.monkeycompanion.powers.SettaiClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/** Routines on disk (a small JSON file). */
object RoutineStore {
    fun file(ctx: Context) = File(ctx.filesDir, "settai_routines.json")

    fun toJson(r: Routine): JSONObject = JSONObject().put("id", r.id).put("enabled", r.enabled).put("text", r.text).apply {
        r.say?.let { put("say", it) }; r.capId?.let { put("cap", it) }
        put("args", JSONObject().also { o -> r.args.forEach { (k, v) -> if (v != null) o.put(k, v) } })
        when (val t = r.trigger) {
            is Trigger.At -> put("at", JSONObject().put("h", t.hour).put("m", t.minute).put("days", JSONArray(t.days.sorted())).put("once", t.once))
            is Trigger.On -> put("on", t.event.name)
        }
    }

    fun fromJson(o: JSONObject): Routine? = runCatching {
        val trig: Trigger = o.optJSONObject("at")?.let { a ->
            val d = a.optJSONArray("days"); Trigger.At(a.getInt("h"), a.getInt("m"), (0 until (d?.length() ?: 0)).map { d!!.getInt(it) }.toSet(), a.optBoolean("once"))
        } ?: Trigger.On(RoutineEvent.valueOf(o.getString("on")))
        val args = HashMap<String, Any?>(); o.optJSONObject("args")?.let { a -> a.keys().forEach { k -> args[k] = a.get(k) } }
        Routine(o.getInt("id"), trig, o.optString("say").ifEmpty { null }, o.optString("cap").ifEmpty { null }, args, o.optBoolean("enabled", true), o.optString("text"))
    }.getOrNull()

    @Synchronized fun all(ctx: Context): List<Routine> = runCatching {
        val f = file(ctx); if (!f.exists()) return emptyList()
        val a = JSONArray(f.readText()); (0 until a.length()).mapNotNull { fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    @Synchronized fun save(ctx: Context, l: List<Routine>) {
        val a = JSONArray(); l.forEach { a.put(toJson(it)) }
        runCatching { val tmp = File(file(ctx).path + ".tmp"); tmp.writeText(a.toString()); tmp.renameTo(file(ctx)) }
    }

    fun add(ctx: Context, r: Routine): Routine {
        val l = all(ctx); val n = r.copy(id = (l.maxOfOrNull { it.id } ?: 0) + 1)
        save(ctx, l + n); RoutineScheduler.refresh(ctx); return n
    }

    fun remove(ctx: Context, id: Int) { RoutineScheduler.cancel(ctx, id); save(ctx, all(ctx).filter { it.id != id }); RoutineScheduler.refresh(ctx) }
    fun setEnabled(ctx: Context, id: Int, on: Boolean) { save(ctx, all(ctx).map { if (it.id == id) it.copy(enabled = on) else it }); RoutineScheduler.refresh(ctx) }
}

/** Time routines -> AlarmManager (exact when allowed). Event routines -> [RoutineEvents] while Settai runs. */
object RoutineScheduler {
    const val ACTION = "com.combo.monkeycompanion.ROUTINE"
    private fun pi(ctx: Context, id: Int) = PendingIntent.getBroadcast(ctx, 20000 + id, Intent(ctx, RoutineReceiver::class.java).setAction(ACTION).putExtra("id", id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun cancel(ctx: Context, id: Int) = ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx, id))

    fun refresh(ctx: Context) {
        val on = AppSettings(ctx).groupEnabled(CapGroup.ROUTINES)
        val am = ctx.getSystemService(AlarmManager::class.java)
        for (r in RoutineStore.all(ctx)) {
            val t = r.trigger as? Trigger.At ?: continue
            if (!on || !r.enabled) { cancel(ctx, r.id); continue }
            val at = RoutineTime.next(t, Calendar.getInstance())
            runCatching {
                if (SettaiClock.exactAllowed(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx, r.id))
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx, r.id))
            }
        }
        // v4 birthday reminders: one daily check at 9 am while Calendar & contacts is on.
        val bpi = PendingIntent.getBroadcast(ctx, 19999, Intent(ctx, RoutineReceiver::class.java).setAction(ACTION_BIRTHDAYS), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (AppSettings(ctx).groupEnabled(CapGroup.PIM) && com.combo.monkeycompanion.powers.Perm.CONTACTS.granted(ctx))
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, RoutineTime.next(Trigger.At(9, 0), Calendar.getInstance()), bpi) }
        else am.cancel(bpi)
        RoutineEvents.refresh(ctx)
    }
    const val ACTION_BIRTHDAYS = "com.combo.monkeycompanion.BIRTHDAYS"
}

/** Runs a routine: says its line, or uses a power (confirm-first if it changes something). */
object RoutineRunner {
    private const val CH = "settai_routines"

    fun run(ctx: Context, r: Routine) {
        val app = ctx.applicationContext
        val s = AppSettings(app)
        if ((r.id >= 0 && !s.groupEnabled(CapGroup.ROUTINES)) || !r.enabled) return
        ChatHub.init(app)
        val line: String
        if (r.say != null) {
            line = r.say
            ChatHub.alert(line, BodyAction.JUMP)
        } else {
            val h = runCatching { ChatHub.powers.handle(Invocation(r.capId ?: "", r.args)) }.getOrNull()
            line = when {
                h == null -> "Routine failed to run. 😕"
                h.pending != null -> { ChatHub.propose(h.pending); "Routine: ${h.pending.question} (answer in ${com.combo.monkeycompanion.character.CharacterRegistry.current.name}'s bubble)" }
                else -> h.say
            }
            ChatHub.alert(line, BodyAction.WAVE)
        }
        ActionLog.add(app, "Routine: ${r.trigger.describe()}", line)
        notify(app, r.id, line)
        val t = r.trigger
        if (t is Trigger.At && t.once) RoutineStore.remove(app, r.id)
    }

    private fun notify(ctx: Context, id: Int, line: String) = runCatching {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Set-Tai routines", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 3, Intent(ctx, com.combo.monkeycompanion.ui.SettaiDesignActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(30000 + id, NotificationCompat.Builder(ctx, CH).let { com.combo.monkeycompanion.overlay.CharacterIcon.applyTo(it, ctx) }.setContentTitle(com.combo.monkeycompanion.character.CharacterRegistry.current.name.let { if (it == com.combo.monkeycompanion.character.CharacterNames.MONKEY) "🐒 $it" else "✨ $it" }).setContentText(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(line)).setContentIntent(open).setAutoCancel(true).build())
    }

    fun fireEvent(ctx: Context, e: RoutineEvent) {
        RoutineStore.all(ctx).filter { it.enabled && (it.trigger as? Trigger.On)?.event == e }.forEach { run(ctx, it) }
    }
}

class RoutineReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        when (i.action) {
            RoutineScheduler.ACTION -> {
                val id = i.getIntExtra("id", -1)
                RoutineStore.all(ctx).firstOrNull { it.id == id }?.let { RoutineRunner.run(ctx, it) }
                RoutineScheduler.refresh(ctx)
            }
            RoutineScheduler.ACTION_BIRTHDAYS -> {
                val today = runCatching { com.combo.monkeycompanion.powers.MoreCaps.birthdays(ctx, 0) }.getOrDefault(emptyList())
                if (today.isNotEmpty()) RoutineRunner.run(ctx, Routine(-1, Trigger.At(9, 0), say = "🎂 Today is " + today.joinToString(" and ") { it.first } + "'s birthday! Say “call ${today.first().first.split(' ').first()}” and I'll get the dialer ready."))
                RoutineScheduler.refresh(ctx)
            }
            else -> RoutineScheduler.refresh(ctx)   // boot / update / exact-alarm permission
        }
    }
}

/**
 * Event triggers (charger, battery low, Wi-Fi, headphones). Android doesn't deliver these to sleeping
 * apps any more, so they are listened for only while Settai's overlay / wallpaper is running, and only
 * when an event routine exists. Each event fires at most once a minute.
 */
object RoutineEvents {
    private var ctx: Context? = null
    private var receiver: BroadcastReceiver? = null
    private var net: ConnectivityManager.NetworkCallback? = null
    private val last = HashMap<RoutineEvent, Long>()
    private var onWifi = false

    fun start(c: Context) { ctx = c.applicationContext; refresh(c) }
    fun stop() { unregister(); ctx = null }

    private fun fire(e: RoutineEvent) {
        val c = ctx ?: return
        val now = System.currentTimeMillis()
        if (now - (last[e] ?: 0L) < 60_000) return
        last[e] = now
        RoutineRunner.fireEvent(c, e)
    }

    @Synchronized fun refresh(any: Context) {
        val c = ctx ?: return
        val wanted = AppSettings(c).groupEnabled(CapGroup.ROUTINES) &&
            RoutineStore.all(c).any { it.enabled && it.trigger is Trigger.On }
        if (!wanted) { unregister(); return }
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(x: Context, i: Intent) {
                if (isInitialStickyBroadcast) return
                when (i.action) {
                    Intent.ACTION_POWER_CONNECTED -> fire(RoutineEvent.CHARGER)
                    Intent.ACTION_BATTERY_LOW -> fire(RoutineEvent.BATTERY_LOW)
                    Intent.ACTION_HEADSET_PLUG -> if (i.getIntExtra("state", 0) == 1) fire(RoutineEvent.HEADPHONES)
                    "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED" -> if (i.getIntExtra("android.bluetooth.profile.extra.STATE", 0) == 2) fire(RoutineEvent.HEADPHONES)
                }
            }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_BATTERY_LOW); addAction(Intent.ACTION_HEADSET_PLUG)
            addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
        }
        if (Build.VERSION.SDK_INT >= 33) c.registerReceiver(r, f, Context.RECEIVER_EXPORTED) else c.registerReceiver(r, f)
        receiver = r
        val cm = c.getSystemService(ConnectivityManager::class.java)
        onWifi = cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(n: Network, caps: NetworkCapabilities) {
                val w = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                if (w && !onWifi) android.os.Handler(android.os.Looper.getMainLooper()).post { fire(RoutineEvent.WIFI) }
                onWifi = w
            }
            override fun onLost(n: Network) { onWifi = false }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb); net = cb }
    }

    private fun unregister() {
        val c = ctx ?: return
        receiver?.let { runCatching { c.unregisterReceiver(it) } }; receiver = null
        net?.let { runCatching { c.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } }; net = null
    }
}
