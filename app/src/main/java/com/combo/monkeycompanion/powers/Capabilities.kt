package com.combo.monkeycompanion.powers

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.nfc.NfcManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import com.combo.monkeycompanion.ai.AppSettings
import com.combo.monkeycompanion.ai.BodyAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Groups the user can switch on/off in Settings. v4 agent groups ([optIn]) start OFF and explain themselves. */
enum class CapGroup(val label: String, val optIn: Boolean = false, val explain: String = "") {
    INFO("Read phone status (battery, storage, RAM, CPU, network, sound, display, time)"),
    CLOCK("Timers & alarms"),
    SOUND("Volume, ringer mode & Do Not Disturb"),
    DISPLAY("Brightness, screen timeout & auto-rotate"),
    CONNECT("Wi-Fi, Bluetooth, mobile data, airplane, location & NFC (opens the switch)"),
    MEDIA("Music: play / pause / next, what's playing"),
    TORCH("Torch"),
    APPS("Open apps, camera & Settings pages"),
    COMMS("Web search, dialer, SMS composer, calendar, share, maps"),
    SCREEN("Screen agent: read the screen, tap, type, scroll", true,
        "Uses Android Accessibility so your character can read what's on screen and tap / type / scroll for you, step by step, only when you ask. Every tap and every bit of typing asks Yes/No in the bubble first. Never touches the lock screen, password fields or banking / payment apps. Tap your character (or the notification) to stop. Works best with an online brain."),
    NOTIFS("Notification reader: what did I miss, OTPs, reply", true,
        "Reads the notifications on your phone to summarise them when you ask. With an online brain, the summary text (app, title, message) is sent to that provider when you ask; OTP codes are masked and never sent. Replies and dismissals ask Yes/No first."),
    MEMORY("Memory: things you tell your character to remember", true,
        "Remembers small facts you tell it (your name, likes, birthdays, habits) in a file on this phone only, and uses them in chats and jokes. See or delete them under Memory."),
    ROUTINES("Routines: timed & event reminders / actions", true,
        "Lets you make routines like 'every day at 9 pm remind me to drink water' or 'when the charger is connected say hi'. Actions that change something still ask Yes/No when they run."),
    VOICE("Voice: push-to-talk and spoken replies", true,
        "A mic button in the chat (you press it; no always-on listening) and your character can say replies out loud with a moving mouth. Uses your phone's speech engines."),
    WEB("Web brain: quick lookups & weather", true,
        "Looks things up on the web (DuckDuckGo / Wikipedia or your own search URL) and gets the weather from Open-Meteo using your rough location. Needs internet."),
    DOCTOR("Phone doctor: storage & battery hogs", true,
        "Looks at storage use and which apps you used most (Usage access) and suggests clean-ups. Opening an app's settings page asks first."),
    PIM("Calendar & contacts", true,
        "Reads today's calendar and your contacts so 'what's my day', birthdays and 'call Amma' work. Calls are never placed: your character only fills the dialer."),
}

/** Special access some powers need, with the one-tap grant screen. [runtime] = an Android runtime permission (asked in-app). */
enum class Perm(val label: String, val runtime: String? = null) {
    NONE(""),
    WRITE_SETTINGS("Modify system settings"),
    NOTIF_POLICY("Do Not Disturb access"),
    NOTIF_LISTENER("Notification access (what's playing, notification reader)"),
    POST_NOTIF("Notifications (so alarms can ring)"),
    ACCESSIBILITY("Accessibility: Set-Tai screen agent"),
    USAGE_STATS("Usage access (phone doctor)"),
    LOCATION("Approximate location (weather)", android.Manifest.permission.ACCESS_COARSE_LOCATION),
    CALENDAR("Read calendar", android.Manifest.permission.READ_CALENDAR),
    CONTACTS("Read contacts", android.Manifest.permission.READ_CONTACTS),
    MIC("Microphone (push-to-talk)", android.Manifest.permission.RECORD_AUDIO);

    fun granted(ctx: Context): Boolean = when (this) {
        NONE -> true
        WRITE_SETTINGS -> Settings.System.canWrite(ctx)
        NOTIF_POLICY -> ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        NOTIF_LISTENER -> (Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: "")
            .contains(ComponentName(ctx, SettaiNotificationListener::class.java).flattenToString())
        POST_NOTIF -> LaunchGate.hasNotifPermission(ctx) && ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        ACCESSIBILITY -> com.combo.monkeycompanion.agent.SettaiAccessibilityService.enabled(ctx)
        USAGE_STATS -> runCatching {
            val ops = ctx.getSystemService(android.app.AppOpsManager::class.java)
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName) == android.app.AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
        LOCATION, CALENDAR, CONTACTS, MIC -> ctx.checkSelfPermission(runtime!!) == PackageManager.PERMISSION_GRANTED
    }

    fun grantIntent(ctx: Context): Intent = when (this) {
        WRITE_SETTINGS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName))
        NOTIF_POLICY -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        NOTIF_LISTENER -> if (Build.VERSION.SDK_INT >= 30)
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(ctx, SettaiNotificationListener::class.java).flattenToString())
        else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        POST_NOTIF -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        USAGE_STATS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        // Runtime permissions are asked in-app (Phone powers screen); from chat we open Settai's app page.
        LOCATION, CALENDAR, CONTACTS, MIC -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
        NONE -> Intent(Settings.ACTION_SETTINGS)
    }
}

/** A little visual gag Settai does after a power (drawn by the renderer). */
enum class Prop { NONE, SHADES, EAR_PAWS, YAWN,
    /** v4 agent props: reading the screen, notifications, memory, phone doctor, web/weather. (Steering wheel = while driving.) */
    GOGGLES, MAGNIFIER, NOTEBOOK, STETHOSCOPE, GLOBE }

class Param(val name: String, val type: String, val desc: String, val required: Boolean = true, val options: List<String>? = null)

sealed class Plan {
    /** A reading: say it now. */
    class Answer(val text: String) : Plan()
    /** A change: ask [question]; on Yes run [exec], which returns what to say. */
    class Act(val question: String, val body: BodyAction? = null, val prop: Prop = Prop.NONE,
              /** v4: run without asking (only for harmless agent steps the user chose not to confirm). */
              val auto: Boolean = false, val exec: () -> String) : Plan()
    class Fail(val text: String) : Plan()
    /** v4: a slow reading (network): from chat it says [placeholder] now and the answer when ready; tools wait for it. */
    class Later(val placeholder: String, val work: () -> String) : Plan()
}

class Capability(
    val id: String,
    val group: CapGroup,
    val title: String,
    /** For the AI tool description. */
    val desc: String,
    val params: List<Param> = emptyList(),
    val change: Boolean,
    val perm: (Map<String, Any?>) -> Perm = { Perm.NONE },
    /** Tag for brains without tools, e.g. "volume" -> [volume media 60]. */
    val tag: String? = null,
    val tagHelp: String? = null,
    val fromTag: ((String) -> Map<String, Any?>?)? = null,
    /** Which [Perm]s it can need (for the "What can your character do?" screen). */
    val needs: List<Perm> = emptyList(),
    /** v4: brain tool calls wait for the user's Yes/No and get the real result (screen agent). */
    val blocking: Boolean = false,
    /** v4: prop shown when a reading is answered (goggles, magnifier...). */
    val answerProp: Prop = Prop.NONE,
    val plan: (Context, AppSettings, Map<String, Any?>) -> Plan,
)

/** Everything Settai can do on the phone, data-driven: tools, tags, Settings and the capability screen all come from [all]. */
object Capabilities {
    internal fun Map<String, Any?>.str(k: String) = this[k]?.toString()?.trim().orEmpty()
    internal fun Map<String, Any?>.int(k: String): Int? = when (val v = this[k]) { is Number -> v.toInt(); is String -> v.trim().removeSuffix("%").toIntOrNull(); else -> null }
    internal fun Map<String, Any?>.bool(k: String): Boolean? = when (val v = this[k]) {
        is Boolean -> v; is String -> when (v.trim().lowercase()) { "on", "true", "yes", "enable", "1" -> true; "off", "false", "no", "disable", "0" -> false; else -> null }; else -> null
    }
    private fun onOff(a: String): Map<String, Any?>? = when (a.trim().lowercase()) { "on", "" -> mapOf("on" to true); "off" -> mapOf("on" to false); else -> null }

    private fun audio(c: Context) = c.getSystemService(AudioManager::class.java)
    val streams = linkedMapOf("media" to AudioManager.STREAM_MUSIC, "ring" to AudioManager.STREAM_RING, "alarm" to AudioManager.STREAM_ALARM,
        "notification" to AudioManager.STREAM_NOTIFICATION, "call" to AudioManager.STREAM_VOICE_CALL)
    private fun pct(c: Context, s: Int): Int { val a = audio(c); val mx = a.getStreamMaxVolume(s); return if (mx > 0) a.getStreamVolume(s) * 100 / mx else 0 }

    private fun brightnessPct(c: Context): Int = runCatching { Settings.System.getInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS) * 100 / 255 }.getOrDefault(-1)
    private fun autoBrightness(c: Context) = runCatching { Settings.System.getInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC }.getOrDefault(false)
    private fun timeoutSec(c: Context) = runCatching { Settings.System.getInt(c.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT) / 1000 }.getOrDefault(-1)
    private fun autoRotate(c: Context) = runCatching { Settings.System.getInt(c.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 1 }.getOrDefault(false)
    private fun airplane(c: Context) = Settings.Global.getInt(c.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    private fun locationOn(c: Context) = runCatching { if (Build.VERSION.SDK_INT >= 28) c.getSystemService(LocationManager::class.java).isLocationEnabled else true }.getOrDefault(false)
    private fun nfcOn(c: Context): Boolean? = runCatching { c.getSystemService(NfcManager::class.java)?.defaultAdapter?.isEnabled }.getOrNull()
    private fun btOn(c: Context): Boolean? = runCatching { c.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled }.getOrNull()
    private fun dndOn(c: Context) = c.getSystemService(NotificationManager::class.java).currentInterruptionFilter.let { it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN }
    private fun ringer(c: Context) = when (audio(c).ringerMode) { AudioManager.RINGER_MODE_SILENT -> "silent"; AudioManager.RINGER_MODE_VIBRATE -> "vibrate"; else -> "normal" }
    private fun network(c: Context): String {
        val cm = c.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "offline"
        val kind = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "connected"
        }
        val net = if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "internet OK" else "no internet yet"
        return "$kind ($net${if (!cm.isActiveNetworkMetered) ", unmetered" else ""})"
    }
    private fun battery(c: Context): Triple<Int, Boolean, Double> {
        val i = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return Triple(if (level >= 0) level * 100 / scale else -1, st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL,
            (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0)
    }
    private fun gb(b: Long) = Math.round(b / 1e9 * 10) / 10.0

    fun open(c: Context, i: Intent, what: String, okText: String): String = when (val r = LaunchGate.start(c, i, what)) {
        is LaunchGate.Result.Started -> { ActionLog.add(c, what, "opened"); okText }
        is LaunchGate.Result.Notified -> { ActionLog.add(c, what, "notification instead: ${r.reason}", false); "${r.reason}, so I put up a notification — tap it to open $what. 📲" }
        is LaunchGate.Result.Failed -> { ActionLog.add(c, what, "failed: ${r.reason}", false); "I couldn't open $what: ${r.reason}. 😕" }
    }

    /** Settings pages Settai can open. */
    val settingsPages = linkedMapOf(
        "wifi" to Settings.ACTION_WIFI_SETTINGS, "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS, "mobile_data" to Settings.ACTION_DATA_ROAMING_SETTINGS,
        "airplane" to Settings.ACTION_AIRPLANE_MODE_SETTINGS, "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS, "nfc" to Settings.ACTION_NFC_SETTINGS,
        "display" to Settings.ACTION_DISPLAY_SETTINGS, "sound" to Settings.ACTION_SOUND_SETTINGS, "battery" to Intent.ACTION_POWER_USAGE_SUMMARY,
        "battery_saver" to Settings.ACTION_BATTERY_SAVER_SETTINGS, "storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "apps" to Settings.ACTION_APPLICATION_SETTINGS,
        "notifications" to "android.settings.ALL_APPS_NOTIFICATION_SETTINGS", "dnd" to Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS,
        "date_time" to Settings.ACTION_DATE_SETTINGS, "accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS, "security" to Settings.ACTION_SECURITY_SETTINGS,
        "hotspot" to "android.settings.TETHER_SETTINGS", "vpn" to Settings.ACTION_VPN_SETTINGS, "language" to Settings.ACTION_LOCALE_SETTINGS,
        "about" to Settings.ACTION_DEVICE_INFO_SETTINGS, "developer" to Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, "settings" to Settings.ACTION_SETTINGS,
        "data_usage" to Settings.ACTION_DATA_USAGE_SETTINGS, "cast" to Settings.ACTION_CAST_SETTINGS, "night_light" to "android.settings.NIGHT_DISPLAY_SETTINGS",
    )
    private val panels = mapOf("internet" to Settings.Panel.ACTION_INTERNET_CONNECTIVITY, "wifi" to Settings.Panel.ACTION_WIFI,
        "volume" to Settings.Panel.ACTION_VOLUME, "nfc" to Settings.Panel.ACTION_NFC)

    /** Things apps may not switch themselves: open the quick panel or the Settings page instead. */
    private val switchTargets = mapOf(
        "wifi" to ("wifi" to "Android doesn't let apps flip Wi-Fi themselves since Android 10"),
        "bluetooth" to (null to "Android doesn't let apps switch Bluetooth since Android 13"),
        "mobile_data" to ("internet" to "Only the system can switch mobile data"),
        "airplane" to (null to "Only the system can switch airplane mode"),
        "location" to (null to "Only the system can switch location"),
        "nfc" to ("nfc" to "Only the system can switch NFC"),
        "hotspot" to (null to "Only the system can switch the hotspot"),
    )

    val all: List<Capability> by lazy { build() + AgentCaps.build() }
    fun byId(id: String) = all.firstOrNull { it.id == id }
    fun byTag(tag: String) = all.firstOrNull { it.tag == tag.lowercase() }

    private fun build(): List<Capability> = listOf(
        // ---------------- reading ----------------
        Capability("get_battery", CapGroup.INFO, "Battery level, charging & temperature", "Battery %, charging and battery temperature.", change = false, tag = "battery", tagHelp = "[battery]", fromTag = { emptyMap() }) { c, _, _ ->
            val (lv, ch, t) = battery(c)
            Plan.Answer("Battery's at $lv%${if (ch) " and charging ⚡" else " 🔋"}, ${t}°C" + when { t >= 42 -> " — toasty! 🥵"; t >= 37 -> " — a bit warm."; else -> "." })
        },
        Capability("get_storage", CapGroup.INFO, "Free storage", "Free and total internal storage.", change = false, tag = "storage", tagHelp = "[storage]", fromTag = { emptyMap() }) { _, _, _ ->
            val s = StatFs(Environment.getDataDirectory().path); Plan.Answer("${gb(s.availableBytes)} GB free of ${gb(s.totalBytes)} GB. 📦")
        },
        Capability("get_memory", CapGroup.INFO, "Free RAM", "Free and total RAM.", change = false, tag = "ram", tagHelp = "[ram]", fromTag = { emptyMap() }) { c, _, _ ->
            val am = c.getSystemService(android.app.ActivityManager::class.java); val mi = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            Plan.Answer("RAM: ${gb(mi.availMem)} GB free of ${gb(mi.totalMem)} GB${if (mi.lowMemory) " — running low!" else "."}")
        },
        Capability("get_cpu", CapGroup.INFO, "CPU info", "Processor: cores, chip and max speed (Android hides live CPU load from apps).", change = false, tag = "cpu", tagHelp = "[cpu]", fromTag = { emptyMap() }) { _, _, _ ->
            val cores = Runtime.getRuntime().availableProcessors()
            val maxMhz = (0 until cores).mapNotNull { runCatching { java.io.File("/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq").readText().trim().toLong() / 1000 }.getOrNull() }.maxOrNull()
            val soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim() else Build.HARDWARE
            Plan.Answer("$cores cores, $soc${maxMhz?.let { ", up to %.1f GHz".format(it / 1000.0) } ?: ""}. 🧠")
        },
        Capability("get_network", CapGroup.INFO, "Network status", "Current connection (Wi-Fi / mobile data / offline), internet reachability, Bluetooth, airplane mode, location & NFC on/off.", change = false, tag = "network", tagHelp = "[network]", fromTag = { emptyMap() }) { c, _, _ ->
            Plan.Answer("Network: ${network(c)}. Bluetooth ${on(btOn(c))}, airplane mode ${on(airplane(c))}, location ${on(locationOn(c))}${nfcOn(c)?.let { ", NFC ${on(it)}" } ?: ""}.")
        },
        Capability("get_sound", CapGroup.INFO, "Sound status", "Ringer mode, Do Not Disturb and volume levels.", change = false, tag = "sound", tagHelp = "[sound]", fromTag = { emptyMap() }) { c, _, _ ->
            Plan.Answer("Ringer ${ringer(c)}, Do Not Disturb ${on(dndOn(c))}. Volume: media ${pct(c, AudioManager.STREAM_MUSIC)}%, ring ${pct(c, AudioManager.STREAM_RING)}%, alarm ${pct(c, AudioManager.STREAM_ALARM)}%. 🔊")
        },
        Capability("get_display", CapGroup.INFO, "Display status", "Brightness, auto-brightness, screen timeout, auto-rotate.", change = false, tag = "display", tagHelp = "[display]", fromTag = { emptyMap() }) { c, _, _ ->
            Plan.Answer("Brightness ${brightnessPct(c)}%${if (autoBrightness(c)) " (auto)" else ""}, screen timeout ${niceSec(timeoutSec(c))}, auto-rotate ${on(autoRotate(c))}. 📱")
        },
        Capability("get_time", CapGroup.INFO, "Time & date", "Local date, time and time zone.", change = false, tag = "time", tagHelp = "[time]", fromTag = { emptyMap() }) { _, _, _ ->
            Plan.Answer("It's ${SimpleDateFormat("h:mm a, EEEE d MMMM yyyy", Locale.getDefault()).format(Date())}. 🕒")
        },
        Capability("list_apps", CapGroup.INFO, "Installed apps", "List installed apps (optionally those matching a word).",
            listOf(Param("filter", "string", "Optional word to filter by", false)), change = false) { c, _, a ->
            val pm = c.packageManager
            val f = a.str("filter").lowercase()
            val names = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
                .map { it.loadLabel(pm).toString() }.distinct().sorted().filter { f.isEmpty() || it.lowercase().contains(f) }
            Plan.Answer(if (names.isEmpty()) "No apps match “$f”." else "${names.size} apps: " + names.take(40).joinToString(", ") + if (names.size > 40) "…" else "")
        },
        Capability("get_now_playing", CapGroup.MEDIA, "What's playing", "The song/video playing now (needs notification access).", change = false,
            perm = { Perm.NOTIF_LISTENER }, needs = listOf(Perm.NOTIF_LISTENER), tag = "playing", tagHelp = "[playing]", fromTag = { emptyMap() }) { c, _, _ ->
            val sessions = c.getSystemService(MediaSessionManager::class.java).getActiveSessions(ComponentName(c, SettaiNotificationListener::class.java))
            val s = sessions.firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
            val md = s?.metadata
            if (s == null || md == null) Plan.Answer("Nothing's playing right now. 🎧")
            else {
                val title = md.getString(android.media.MediaMetadata.METADATA_KEY_TITLE).orEmpty()
                val artist = md.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
                val app = runCatching { c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(s.packageName, 0)) }.getOrDefault(s.packageName)
                val playing = s.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
                Plan.Answer("${if (playing) "Playing" else "Paused"}: “$title”${if (artist.isNotBlank()) " by $artist" else ""} on $app. 🎶")
            }
        },

        // ---------------- clock ----------------
        Capability("set_timer", CapGroup.CLOCK, "Set a timer", "Start a countdown timer.",
            listOf(Param("seconds", "integer", "Timer length in seconds"), Param("label", "string", "Optional label", false)), change = true,
            tag = "timer", tagHelp = "[timer SECONDS]", fromTag = { a -> (a.toIntOrNull() ?: ClockCommands.durationSeconds(a))?.let { mapOf("seconds" to it) } }) { c, s, a ->
            val sec = a.int("seconds") ?: return@Capability Plan.Fail("How long should the timer be?")
            if (sec !in 1..86400) return@Capability Plan.Fail("A timer has to be between 1 second and 24 hours.")
            val label = a.str("label")
            Plan.Act("Start a ${ClockPowers.niceDuration(sec)} timer${if (label.isNotBlank()) " for $label" else ""}? ⏳", BodyAction.HAPPY) { ClockPowers.setTimer(c, s, sec, label) }
        },
        Capability("set_alarm", CapGroup.CLOCK, "Set an alarm", "Set an alarm (24-hour time).",
            listOf(Param("hour", "integer", "Hour 0-23"), Param("minute", "integer", "Minute 0-59"), Param("label", "string", "Optional label", false),
                Param("tomorrow", "boolean", "True if the user said tomorrow", false)), change = true,
            tag = "alarm", tagHelp = "[alarm HH:MM]", fromTag = { a ->
                ClockCommands.clockTime(if (a.trim().matches(Regex("\\d{1,2}"))) "at ${a.trim()}" else a)?.let { (h, m, _) -> mapOf("hour" to h, "minute" to m, "tomorrow" to a.contains("tomorrow", true)) }
            }) { c, s, a ->
            val h = a.int("hour") ?: -1; val m = a.int("minute") ?: 0
            if (h !in 0..23 || m !in 0..59) return@Capability Plan.Fail("That's not a time on any clock I know.")
            val label = a.str("label"); val tmr = a.bool("tomorrow") == true
            val note = if (a["ambiguous"] == true) " (Say am or pm if you meant the other one!)" else ""
            Plan.Act("Set an alarm for ${ClockPowers.niceTime(h, m)}${if (tmr) " tomorrow" else ""}${if (label.isNotBlank()) " ($label)" else ""}? ⏰", BodyAction.HAPPY) {
                ClockPowers.setAlarm(c, s, h, m, label, tmr) + note
            }
        },
        Capability("list_timers", CapGroup.CLOCK, "List timers & alarms", "List the timers and alarms Set-Tai is keeping.", change = false, tag = "timers", tagHelp = "[timers]", fromTag = { emptyMap() }) { c, _, _ ->
            Plan.Answer(ClockPowers.list(c))
        },
        Capability("cancel_timer", CapGroup.CLOCK, "Cancel timers & alarms", "Cancel Set-Tai's own timers and/or alarms.",
            listOf(Param("which", "string", "timer, alarm or all", false, listOf("timer", "alarm", "all"))), change = false,
            tag = "cancel", tagHelp = "[cancel timer|alarm|all]", fromTag = { a -> mapOf("which" to a.ifBlank { "all" }) }) { c, _, a ->
            Plan.Answer(ClockPowers.cancel(c, a.str("which").ifBlank { "all" }))
        },

        // ---------------- sound ----------------
        Capability("set_volume", CapGroup.SOUND, "Change volume", "Set a volume: stream media/ring/alarm/notification/call; level 0-100, or up, down, mute, max.",
            listOf(Param("stream", "string", "Which volume", true, streams.keys.toList()), Param("level", "string", "0-100, up, down, mute or max")), change = true,
            perm = { a -> if (a.str("stream") in setOf("ring", "notification")) Perm.NOTIF_POLICY else Perm.NONE }, needs = listOf(Perm.NOTIF_POLICY),
            tag = "volume", tagHelp = "[volume media|ring|alarm 0-100|up|down|mute]", fromTag = { a ->
                val p = a.trim().split(Regex("\\s+")); if (p.size >= 2) mapOf("stream" to p[0], "level" to p[1]) else if (p.size == 1 && p[0].isNotEmpty()) mapOf("stream" to "media", "level" to p[0]) else null
            }) { c, _, a ->
            val name = a.str("stream").ifBlank { "media" }.lowercase().let { if (it == "music") "media" else if (it == "ringer" || it == "ringtone") "ring" else it }
            val st = streams[name] ?: return@Capability Plan.Fail("I only know media, ring, alarm, notification and call volume.")
            val am = audio(c); val mx = am.getStreamMaxVolume(st); val cur = am.getStreamVolume(st)
            val lv = a.str("level").lowercase()
            val target = when (lv) {
                "up", "louder", "increase" -> (cur + maxOf(1, mx / 5)).coerceAtMost(mx)
                "down", "lower", "decrease", "softer" -> (cur - maxOf(1, mx / 5)).coerceAtLeast(0)
                "mute", "0", "off", "silent" -> 0
                "max", "full", "100" -> mx
                else -> lv.removeSuffix("%").toIntOrNull()?.coerceIn(0, 100)?.let { Math.round(it * mx / 100f) } ?: return@Capability Plan.Fail("What volume level? (0-100, up, down or mute)")
            }
            val p = if (mx > 0) target * 100 / mx else 0
            Plan.Act("Set $name volume to $p%? ${if (p == 0) "🔇" else "🔊"}", if (target > cur) BodyAction.JUMP else null, if (p >= 70 && target > cur) Prop.EAR_PAWS else Prop.NONE) {
                am.setStreamVolume(st, target, AudioManager.FLAG_SHOW_UI)
                "${name.replaceFirstChar { it.uppercase() }} volume is $p% now. " + if (p >= 70) "Aiyo, my ears! 🙉" else if (p == 0) "Shhh… 🤫" else "🎚️"
            }
        },
        Capability("set_ringer", CapGroup.SOUND, "Ringer mode", "Set ringer to normal, vibrate or silent.",
            listOf(Param("mode", "string", "normal, vibrate or silent", true, listOf("normal", "vibrate", "silent"))), change = true,
            perm = { Perm.NOTIF_POLICY }, needs = listOf(Perm.NOTIF_POLICY), tag = "ringer", tagHelp = "[ringer normal|vibrate|silent]", fromTag = { a -> mapOf("mode" to a) }) { c, _, a ->
            val mode = when (a.str("mode").lowercase()) { "normal", "ring", "loud", "sound" -> AudioManager.RINGER_MODE_NORMAL; "vibrate", "vibration" -> AudioManager.RINGER_MODE_VIBRATE; "silent", "mute" -> AudioManager.RINGER_MODE_SILENT; else -> return@Capability Plan.Fail("Normal, vibrate or silent?") }
            val n = a.str("mode").lowercase()
            Plan.Act("Switch the ringer to $n? ${if (mode == AudioManager.RINGER_MODE_NORMAL) "🔔" else "🔕"}", if (mode == AudioManager.RINGER_MODE_NORMAL) BodyAction.WAVE else BodyAction.HIDE) {
                audio(c).ringerMode = mode; "Ringer is on $n now. ${if (mode == AudioManager.RINGER_MODE_NORMAL) "Ring ring! 🔔" else "Quiet as a sleeping monkey. 🤫"}"
            }
        },
        Capability("set_dnd", CapGroup.SOUND, "Do Not Disturb", "Turn Do Not Disturb on or off.",
            listOf(Param("on", "boolean", "true = on")), change = true, perm = { Perm.NOTIF_POLICY }, needs = listOf(Perm.NOTIF_POLICY),
            tag = "dnd", tagHelp = "[dnd on|off]", fromTag = ::onOff) { c, _, a ->
            val on = a.bool("on") ?: true
            Plan.Act(if (on) "Turn on Do Not Disturb? 🤐" else "Turn off Do Not Disturb?", if (on) BodyAction.SLEEP else BodyAction.HAPPY) {
                c.getSystemService(NotificationManager::class.java).setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
                if (on) "Do Not Disturb is on — my mouth is tied. 🤐" else "Do Not Disturb is off. Back to chatter! 🐒"
            }
        },

        // ---------------- display ----------------
        Capability("set_brightness", CapGroup.DISPLAY, "Brightness", "Screen brightness 0-100, up, down, max, min, or auto.",
            listOf(Param("level", "string", "0-100, up, down, max, min or auto")), change = true, perm = { Perm.WRITE_SETTINGS }, needs = listOf(Perm.WRITE_SETTINGS),
            tag = "brightness", tagHelp = "[brightness 0-100|up|down|auto]", fromTag = { a -> mapOf("level" to a) }) { c, _, a ->
            val lv = a.str("level").lowercase(); val cur = brightnessPct(c).coerceAtLeast(0)
            if (lv == "auto") return@Capability Plan.Act("Turn on auto-brightness? 🌗", BodyAction.THINK) {
                Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC); "Auto-brightness is on. 🌗"
            }
            val p = when (lv) { "up", "brighter", "increase" -> (cur + 25).coerceAtMost(100); "down", "dimmer", "dim", "decrease" -> (cur - 25).coerceAtLeast(3); "max", "full" -> 100; "min", "lowest" -> 3
                else -> lv.removeSuffix("%").toIntOrNull()?.coerceIn(1, 100) ?: return@Capability Plan.Fail("How bright? (0-100, up, down or auto)") }
            Plan.Act("Set brightness to $p%? ${if (p >= cur) "☀️" else "🌙"}", if (p >= cur) BodyAction.HAPPY else null, if (p >= 70) Prop.SHADES else Prop.NONE) {
                Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (p * 255 / 100).coerceIn(1, 255))
                if (p >= 70) "Brightness $p%. Shades on! 😎" else "Brightness $p%. Nice and cosy. 🌙"
            }
        },
        Capability("set_screen_timeout", CapGroup.DISPLAY, "Screen timeout", "How long before the screen turns off, in seconds (15-1800).",
            listOf(Param("seconds", "integer", "15 to 1800")), change = true, perm = { Perm.WRITE_SETTINGS }, needs = listOf(Perm.WRITE_SETTINGS),
            tag = "timeout", tagHelp = "[timeout SECONDS]", fromTag = { a -> (a.toIntOrNull() ?: ClockCommands.durationSeconds(a))?.let { mapOf("seconds" to it) } }) { c, _, a ->
            val sec = a.int("seconds")?.coerceIn(15, 1800) ?: return@Capability Plan.Fail("How long should the screen stay on?")
            Plan.Act("Set the screen timeout to ${niceSec(sec)}? 😴", null, Prop.YAWN) {
                Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, sec * 1000); "Screen now sleeps after ${niceSec(sec)}. *yaaawn* 😴"
            }
        },
        Capability("set_auto_rotate", CapGroup.DISPLAY, "Auto-rotate", "Turn auto-rotate on or off.",
            listOf(Param("on", "boolean", "true = on")), change = true, perm = { Perm.WRITE_SETTINGS }, needs = listOf(Perm.WRITE_SETTINGS),
            tag = "rotate", tagHelp = "[rotate on|off]", fromTag = ::onOff) { c, _, a ->
            val on = a.bool("on") ?: true
            Plan.Act(if (on) "Turn on auto-rotate? 🔄" else "Lock the rotation?", if (on) BodyAction.SPIN else null) {
                Settings.System.putInt(c.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0); if (on) "Auto-rotate on. Wheee! 🔄" else "Rotation locked. 🔒"
            }
        },

        // ---------------- connectivity (open the switch) ----------------
        Capability("toggle_connection", CapGroup.CONNECT, "Wi-Fi / Bluetooth / data / airplane / location / NFC / hotspot",
            "Turn wifi, bluetooth, mobile_data, airplane, location, nfc or hotspot on/off. Android doesn't let apps flip these directly, so this opens the system switch (quick panel or Settings page) for one tap.",
            listOf(Param("what", "string", "Which one", true, switchTargets.keys.toList()), Param("on", "boolean", "Desired state", false)), change = true,
            tag = "switch", tagHelp = "[switch wifi|bluetooth|mobile_data|airplane|location|nfc|hotspot on|off]", fromTag = { a ->
                val p = a.trim().lowercase().split(Regex("\\s+")); if (p.isEmpty() || p[0].isEmpty()) null else mapOf("what" to p[0], "on" to (p.getOrNull(1) != "off"))
            }) { c, _, a ->
            val what = a.str("what").lowercase().replace(' ', '_').replace("-", "").let { if (it == "data") "mobile_data" else it }
            val (panel, why) = switchTargets[what] ?: return@Capability Plan.Fail("I can open the switch for Wi-Fi, Bluetooth, mobile data, airplane mode, location, NFC or hotspot.")
            val want = a.bool("on")
            val nice = what.replace('_', ' ')
            Plan.Act("$why, so shall I open the $nice switch for you${want?.let { if (it) " (to turn it on)" else " (to turn it off)" } ?: ""}?", BodyAction.WAVE) {
                val i = if (panel != null && Build.VERSION.SDK_INT >= 29) Intent(panels[panel]) else Intent(settingsPages[what] ?: Settings.ACTION_WIRELESS_SETTINGS)
                open(c, i, "$nice switch", "Here's the $nice switch — one tap and done! 👆")
            }
        },

        // ---------------- media ----------------
        Capability("media_control", CapGroup.MEDIA, "Play / pause / skip", "Control whatever music/video app is playing: play, pause, toggle, next, previous, stop.",
            listOf(Param("action", "string", "What to do", true, listOf("play", "pause", "toggle", "next", "previous", "stop"))), change = true,
            tag = "media", tagHelp = "[media play|pause|next|previous]", fromTag = { a -> mapOf("action" to a.ifBlank { "toggle" }) }) { c, _, a ->
            val act = a.str("action").lowercase().ifBlank { "toggle" }
            val code = when (act) { "play", "resume" -> KeyEvent.KEYCODE_MEDIA_PLAY; "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE; "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                "next", "skip" -> KeyEvent.KEYCODE_MEDIA_NEXT; "previous", "prev", "back" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS; "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
                else -> return@Capability Plan.Fail("Play, pause, next or previous?") }
            Plan.Act(when (act) { "next", "skip" -> "Skip to the next track? ⏭️"; "previous", "prev", "back" -> "Go back a track? ⏮️"; "pause", "stop" -> "Pause the music? ⏸️"; else -> "Play the music? ▶️" },
                if (act in setOf("play", "resume", "toggle", "next", "skip")) BodyAction.DANCE else null) {
                val am = audio(c)
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
                when (act) { "pause", "stop" -> "Paused. ⏸️"; "next", "skip" -> "Next one! 🎶"; "previous", "prev", "back" -> "Rewind! ⏮️"; else -> "Music on — dance time! 💃" }
            }
        },

        // ---------------- torch ----------------
        Capability("set_torch", CapGroup.TORCH, "Torch", "Turn the flashlight on or off.", listOf(Param("on", "boolean", "true = on")), change = true,
            tag = "torch", tagHelp = "[torch on|off]", fromTag = ::onOff) { c, _, a ->
            val on = a.bool("on") ?: true
            val cm = c.getSystemService(CameraManager::class.java)
            val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                ?: return@Capability Plan.Fail("This phone has no flashlight I can reach. 🔦❓")
            Plan.Act(if (on) "Turn the torch on? 🔦" else "Turn the torch off?", if (on) BodyAction.SPIN else null, if (on) Prop.SHADES else Prop.NONE) {
                cm.setTorchMode(id, on); if (on) "Let there be light! 🔦" else "Torch off. 🌑"
            }
        },

        // ---------------- apps ----------------
        Capability("open_app", CapGroup.APPS, "Open an app", "Open an installed app by name, e.g. 'YouTube'.",
            listOf(Param("name", "string", "App name as shown on the home screen")), change = true,
            tag = "open", tagHelp = "[open APP NAME]", fromTag = { a -> a.takeIf { it.length in 2..40 }?.let { mapOf("name" to it) } }) { c, _, a ->
            val (pkg, label) = findApp(c, a.str("name")) ?: return@Capability Plan.Fail("I couldn't find an app called “${a.str("name")}” on this phone.")
            Plan.Act("Open $label? 🚀", BodyAction.WAVE) {
                val li = c.packageManager.getLaunchIntentForPackage(pkg) ?: return@Act "I couldn't find how to open $label."
                open(c, li, label, "Opening $label! 🚀")
            }
        },
        Capability("open_camera", CapGroup.APPS, "Camera", "Open the camera for a photo or video.",
            listOf(Param("mode", "string", "photo or video", false, listOf("photo", "video"))), change = true,
            tag = "camera", tagHelp = "[camera]", fromTag = { a -> mapOf("mode" to a.ifBlank { "photo" }) }) { c, _, a ->
            val video = a.str("mode").lowercase().startsWith("v")
            Plan.Act(if (video) "Open the camera for a video? 🎥" else "Open the camera? 📸", BodyAction.HAPPY) {
                open(c, Intent(if (video) MediaStore.INTENT_ACTION_VIDEO_CAMERA else MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "the camera", "Say cheese! 📸🐒")
            }
        },
        Capability("open_settings", CapGroup.APPS, "Open a Settings page", "Open a system Settings page: " + settingsPages.keys.joinToString(", ") + ".",
            listOf(Param("page", "string", "Which page", true, settingsPages.keys.toList())), change = true,
            tag = "settings", tagHelp = "[settings wifi|bluetooth|display|sound|battery|location|nfc|storage|apps|...]", fromTag = { a -> mapOf("page" to a.ifBlank { "settings" }) }) { c, _, a ->
            val page = a.str("page").lowercase().replace(' ', '_').replace("-", "").let { if (it == "wi_fi") "wifi" else it }
            val action = settingsPages[page] ?: settingsPages.entries.firstOrNull { page.contains(it.key) || it.key.contains(page) }?.value ?: Settings.ACTION_SETTINGS
            Plan.Act("Open ${page.replace('_', ' ')} settings? ⚙️", BodyAction.THINK) { open(c, Intent(action), "${page.replace('_', ' ')} settings", "Here you go! ⚙️") }
        },

        // ---------------- comms (open composers, never send) ----------------
        Capability("web_search", CapGroup.COMMS, "Web search", "Open a web search in the browser.", listOf(Param("query", "string", "What to search for")), change = true,
            tag = "search", tagHelp = "[search QUERY]", fromTag = { a -> a.takeIf { it.isNotBlank() }?.let { mapOf("query" to it) } }) { c, _, a ->
            val q = a.str("query").ifBlank { return@Capability Plan.Fail("Search for what?") }
            Plan.Act("Search the web for “$q”? 🔎", BodyAction.THINK) { open(c, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))), "your browser", "Searching for “$q” 🔎") }
        },
        Capability("dial", CapGroup.COMMS, "Dialer", "Open the phone dialer with a number filled in (the user presses call).",
            listOf(Param("number", "string", "Phone number", false)), change = true, tag = "dial", tagHelp = "[dial NUMBER]", fromTag = { a -> mapOf("number" to a) }) { c, _, a ->
            val n = a.str("number").filter { it.isDigit() || it == '+' }
            Plan.Act(if (n.isEmpty()) "Open the dialer? 📞" else "Open the dialer with $n? 📞 (You press call.)", BodyAction.WAVE) {
                open(c, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n")), "the dialer", "Dialer's open — you press the green button. 📞")
            }
        },
        Capability("compose_sms", CapGroup.COMMS, "SMS composer", "Open the messages app with a number and text filled in. It is never sent automatically.",
            listOf(Param("number", "string", "Phone number", false), Param("text", "string", "Message text", false)), change = true,
            tag = "sms", tagHelp = "[sms NUMBER]", fromTag = { a -> mapOf("number" to a) }) { c, _, a ->
            val n = a.str("number").filter { it.isDigit() || it == '+' }; val t = a.str("text")
            Plan.Act("Open a message${if (n.isNotEmpty()) " to $n" else ""}${if (t.isNotEmpty()) " saying “$t”" else ""}? ✉️ (You press send.)", BodyAction.WAVE) {
                open(c, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$n")).putExtra("sms_body", t), "messages", "Message ready — you press send. ✉️")
            }
        },
        Capability("add_calendar_event", CapGroup.COMMS, "Calendar event", "Open the calendar with a new event filled in (the user saves it).",
            listOf(Param("title", "string", "Event title"), Param("start", "string", "Start, 'YYYY-MM-DD HH:MM' local time"), Param("minutes", "integer", "Length in minutes", false)), change = true) { c, _, a ->
            val title = a.str("title").ifBlank { "Event" }
            val start = runCatching { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(a.str("start"))!!.time }.getOrNull()
                ?: return@Capability Plan.Fail("When is it? (date and time)")
            val mins = a.int("minutes") ?: 60
            Plan.Act("Add “$title” on ${SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(Date(start))} to your calendar? 📅", BodyAction.HAPPY) {
                open(c, Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE, title)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + mins * 60_000L), "your calendar", "Event ready — tap save! 📅")
            }
        },
        Capability("share_text", CapGroup.COMMS, "Share text", "Open the share sheet with some text (the user picks where).",
            listOf(Param("text", "string", "Text to share")), change = true) { c, _, a ->
            val t = a.str("text").ifBlank { return@Capability Plan.Fail("Share what?") }
            Plan.Act("Share “${t.take(60)}”? 📤", BodyAction.WAVE) {
                open(c, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), "Share"), "the share sheet", "Pick where to send it! 📤")
            }
        },
        Capability("open_maps", CapGroup.COMMS, "Maps", "Open maps searching for a place.", listOf(Param("query", "string", "Place or address")), change = true,
            tag = "map", tagHelp = "[map PLACE]", fromTag = { a -> a.takeIf { it.isNotBlank() }?.let { mapOf("query" to it) } }) { c, _, a ->
            val q = a.str("query").ifBlank { return@Capability Plan.Fail("Where to?") }
            Plan.Act("Find “$q” on the map? 🗺️", BodyAction.CLIMB) { open(c, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q))), "maps", "Map's open! 🗺️") }
        },
    )

    private fun on(b: Boolean?) = when (b) { true -> "on"; false -> "off"; null -> "unknown" }
    fun niceSec(s: Int): String = when { s < 0 -> "?"; s >= 3600 -> "${s / 3600} h"; s >= 60 -> "${s / 60} min" + if (s % 60 != 0) " ${s % 60} s" else ""; else -> "$s s" }

    fun findApp(c: Context, name: String): Pair<String, String>? {
        val pm = c.packageManager
        val want = name.lowercase(Locale.getDefault()).trim()
        if (want.isEmpty()) return null
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL).map { ri ->
            val label = ri.loadLabel(pm).toString(); val l = label.lowercase(Locale.getDefault())
            Triple(ri.activityInfo.packageName, label, when { l == want -> 3; l.startsWith(want) -> 2; want.length >= 3 && l.contains(want) -> 1; else -> 0 })
        }.filter { it.third > 0 }.maxByOrNull { it.third }?.let { it.first to it.second }
    }

    /** One line about the phone right now, for the system prompt (cheap reads only). */
    fun stateSummary(c: Context): String = runCatching {
        val (lv, ch, t) = battery(c)
        buildList {
            add("battery $lv%${if (ch) " charging" else ""} ${t}°C")
            add("network ${network(c)}")
            add("Bluetooth ${on(btOn(c))}")
            if (airplane(c)) add("airplane mode ON")
            add("ringer ${ringer(c)}"); add("DND ${on(dndOn(c))}")
            add("media volume ${pct(c, AudioManager.STREAM_MUSIC)}%")
            add("brightness ${brightnessPct(c)}%${if (autoBrightness(c)) " auto" else ""}")
            add("screen timeout ${niceSec(timeoutSec(c))}")
            add("auto-rotate ${on(autoRotate(c))}")
            add("location ${on(locationOn(c))}")
            if (c.getSystemService(PowerManager::class.java).isPowerSaveMode) add("battery saver ON")
            add("time ${SimpleDateFormat("EEE h:mm a", Locale.getDefault()).format(Date())}")
        }.joinToString(", ")
    }.getOrDefault("")

    /** Enabled capabilities (by group switch in Settings). */
    fun enabled(s: AppSettings) = all.filter { s.groupEnabled(it.group) }

    /** Tag list for brains without tool calling. */
    fun tagHelp(s: AppSettings): String = enabled(s).mapNotNull { it.tagHelp }.joinToString(" ")

}
