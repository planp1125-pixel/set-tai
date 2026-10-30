package com.combo.monkeycompanion.powers

import android.app.usage.UsageStatsManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import com.combo.monkeycompanion.agent.Voice
import com.combo.monkeycompanion.agent.Web
import com.combo.monkeycompanion.powers.AgentCaps.s
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** v4: voice, web brain, phone doctor, calendar & contacts. */
object MoreCaps {
    fun build(): List<Capability> = voice() + web() + doctor() + pim()

    // ---------------- Voice ----------------
    private fun voice() = listOf(
        Capability("say_aloud", CapGroup.VOICE, "Say it out loud", "Speak a short sentence out loud with the phone's voice.",
            listOf(Param("text", "string", "What to say")), change = false,
            tag = "speak", tagHelp = "[speak TEXT]", fromTag = { t -> t.takeIf { it.isNotBlank() }?.let { mapOf("text" to it) } }) { c, _, a ->
            val t = a.s("text").ifBlank { return@Capability Plan.Fail("Say what?") }
            Voice.speak(c, t); Plan.Answer("🔊 $t")
        },
    )

    // ---------------- Web brain ----------------
    private fun web() = listOf(
        Capability("web_lookup", CapGroup.WEB, "Look something up", "Look something up on the web (facts, definitions, people, places; uses DuckDuckGo + Wikipedia, or the user's search URL) and return text to answer from. Cite the source briefly.",
            listOf(Param("query", "string", "What to look up")), change = false, answerProp = Prop.GLOBE,
            tag = "lookup", tagHelp = "[lookup QUERY]", fromTag = { t -> t.takeIf { it.isNotBlank() }?.let { mapOf("query" to it) } }) { _, st, a ->
            val q = a.s("query").ifBlank { return@Capability Plan.Fail("Look up what?") }
            Plan.Later("🌍 Looking up “$q”…") { Web.lookup(q, st.searchUrl) }
        },
        Capability("get_weather", CapGroup.WEB, "Weather", "Current weather and today's forecast (Open-Meteo). place = a city, or blank for here (rough location).",
            listOf(Param("place", "string", "City (blank = where the phone is)", required = false)), change = false, answerProp = Prop.GLOBE,
            perm = { a -> if (a.s("place").isBlank()) Perm.LOCATION else Perm.NONE }, needs = listOf(Perm.LOCATION),
            tag = "weather", tagHelp = "[weather] or [weather CITY]", fromTag = { t -> mapOf("place" to t.trim()) }) { c, _, a ->
            val place = a.s("place")
            Plan.Later("⛅ Checking the sky…") {
                if (place.isNotBlank()) {
                    val g = Web.geocode(place) ?: return@Later "I couldn't find “$place” on the map. 🗺️"
                    Web.weather(g.first, g.second, g.third)
                } else {
                    val loc = lastLocation(c) ?: return@Later "I don't know where you are (no recent location). Tell me a city, like “weather in Chennai”."
                    Web.weather(loc.first, loc.second, "Where you are")
                }
            }
        },
    )

    @Suppress("MissingPermission")
    private fun lastLocation(c: Context): Pair<Double, Double>? = runCatching {
        val lm = c.getSystemService(LocationManager::class.java)
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }.maxByOrNull { it.time }?.let { it.latitude to it.longitude }
    }.getOrNull()

    // ---------------- Phone doctor ----------------
    private fun gb(b: Long) = "%.1f GB".format(b / 1e9)

    private fun doctor() = listOf(
        Capability("phone_checkup", CapGroup.DOCTOR, "Phone check-up", "Storage use, the apps used most (likely battery hogs) and apps not used for a month (clean-up ideas).",
            change = false, answerProp = Prop.STETHOSCOPE, perm = { Perm.USAGE_STATS }, needs = listOf(Perm.USAGE_STATS),
            tag = "checkup", tagHelp = "[checkup]", fromTag = { emptyMap() }) { c, _, _ ->
            val sf = StatFs(Environment.getDataDirectory().path)
            val total = sf.totalBytes; val free = sf.availableBytes
            val usedPct = ((total - free) * 100 / total.coerceAtLeast(1)).toInt()
            val sb = StringBuilder("🩺 Storage: ${gb(free)} free of ${gb(total)} ($usedPct% used${if (usedPct > 90) " — tight!" else ""}).")
            val usm = c.getSystemService(UsageStatsManager::class.java)
            val now = System.currentTimeMillis()
            val day = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 86_400_000L, now).orEmpty()
            val pm = c.packageManager
            fun label(p: String) = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() }.getOrDefault(p)
            val top = day.filter { it.totalTimeInForeground > 60_000 && it.packageName != c.packageName }.groupBy { it.packageName }
                .mapValues { e -> e.value.sumOf { it.totalTimeInForeground } }.entries.sortedByDescending { it.value }.take(5)
            if (top.isNotEmpty()) sb.append(" Most used today (biggest battery users, roughly): ").append(top.joinToString(", ") { "${label(it.key)} ${it.value / 60_000} min" }).append('.')
            val month = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 60L * 86_400_000L, now).orEmpty()
            val lastUsed = month.groupBy { it.packageName }.mapValues { e -> e.value.maxOf { it.lastTimeUsed } }
            val launchable = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { it.activityInfo.packageName }.toSet()
            val idle = launchable.filter { p ->
                val info = runCatching { pm.getApplicationInfo(p, 0) }.getOrNull() ?: return@filter false
                (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 && p != c.packageName && (lastUsed[p] ?: 0L) < now - 30L * 86_400_000L &&
                    runCatching { pm.getPackageInfo(p, 0).firstInstallTime }.getOrDefault(now) < now - 30L * 86_400_000L
            }.take(6)
            if (idle.isNotEmpty()) sb.append(" Not opened in a month (uninstall candidates): ").append(idle.joinToString(", ") { label(it) }).append('.')
            if (day.isEmpty()) sb.append(" (No usage data yet: give me Usage access.)")
            Plan.Answer(sb.toString())
        },
        Capability("open_app_info", CapGroup.DOCTOR, "Open an app's settings", "Open an app's system info page (to clear cache, force stop, uninstall, check battery use). Asks first.",
            listOf(Param("name", "string", "App name")), change = true, answerProp = Prop.STETHOSCOPE,
            tag = "appinfo", tagHelp = "[appinfo APP]", fromTag = { t -> t.takeIf { it.isNotBlank() }?.let { mapOf("name" to it) } }) { c, _, a ->
            val (pkg, label) = Capabilities.findApp(c, a.s("name")) ?: return@Capability Plan.Fail("I can't find an app called “${a.s("name")}”.")
            Plan.Act("Open $label's app settings? 🩺", prop = Prop.STETHOSCOPE) {
                Capabilities.open(c, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")), "$label settings", "Here's $label's settings page: Storage → Clear cache helps. 🩺")
            }
        },
    )

    // ---------------- Calendar & contacts ----------------
    private val time = SimpleDateFormat("h:mm a", Locale.getDefault())

    private fun todayEvents(c: Context, days: Int = 1): List<String> {
        val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
        val end = start + days * 86_400_000L
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, start); ContentUris.appendId(it, end) }.build()
        val out = ArrayList<String>()
        c.contentResolver.query(uri, arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION),
            null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { cur ->
            while (cur.moveToNext() && out.size < 12) {
                val title = cur.getString(0) ?: "(no title)"; val begin = cur.getLong(1); val allDay = cur.getInt(2) == 1; val loc = cur.getString(3).orEmpty()
                out.add((if (allDay) "all day" else time.format(Date(begin))) + " " + title + if (loc.isNotBlank()) " @ $loc" else "")
            }
        }
        return out
    }

    /** Contact birthdays in the next [days] days: (name, "MM-dd", daysAway). */
    fun birthdays(c: Context, days: Int = 7): List<Triple<String, String, Int>> {
        if (!Perm.CONTACTS.granted(c)) return emptyList()
        val out = ArrayList<Triple<String, String, Int>>()
        val today = Calendar.getInstance()
        c.contentResolver.query(ContactsContract.Data.CONTENT_URI, arrayOf(ContactsContract.Data.DISPLAY_NAME, ContactsContract.CommonDataKinds.Event.START_DATE),
            "${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.CommonDataKinds.Event.TYPE}=?",
            arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()), null)?.use { cur ->
            while (cur.moveToNext()) {
                val name = cur.getString(0) ?: continue; val d = cur.getString(1) ?: continue
                val m = Regex("(\\d{2})-(\\d{2})$").find(d) ?: continue
                val away = BirthdayMath.daysUntil(m.groupValues[1].toInt(), m.groupValues[2].toInt(), today)
                if (away in 0..days) out.add(Triple(name, "${m.groupValues[1]}-${m.groupValues[2]}", away))
            }
        }
        return out.sortedBy { it.third }
    }

    private fun findContacts(c: Context, name: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        c.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"), ContactsContract.CommonDataKinds.Phone.TIMES_CONTACTED + " DESC")?.use { cur ->
            while (cur.moveToNext() && out.size < 5) out.add((cur.getString(0) ?: "") to (cur.getString(1) ?: ""))
        }
        return out.distinctBy { it.second.filter { ch -> ch.isDigit() }.takeLast(10) }
            .sortedBy { if (it.first.equals(name, true)) 0 else if (it.first.startsWith(name, true)) 1 else 2 }
    }

    private fun pim() = listOf(
        Capability("my_day", CapGroup.PIM, "What's my day?", "Today's calendar events plus birthdays coming up this week.", change = false, answerProp = Prop.NOTEBOOK,
            perm = { Perm.CALENDAR }, needs = listOf(Perm.CALENDAR), tag = "myday", tagHelp = "[myday]", fromTag = { emptyMap() }) { c, _, _ ->
            val ev = todayEvents(c)
            val bd = birthdays(c, 7)
            val sb = StringBuilder(if (ev.isEmpty()) "📅 Nothing on your calendar today. Free monkey day!" else "📅 Today: " + ev.joinToString("; "))
            if (bd.isNotEmpty()) sb.append(" 🎂 Birthdays: ").append(bd.joinToString(", ") { (n, _, d) -> n + when (d) { 0 -> " (today!)"; 1 -> " (tomorrow)"; else -> " (in $d days)" } })
            Plan.Answer(sb.toString())
        },
        Capability("upcoming_birthdays", CapGroup.PIM, "Birthdays", "Birthdays in the next 30 days (from contacts).", change = false,
            perm = { Perm.CONTACTS }, needs = listOf(Perm.CONTACTS), tag = "birthdays", tagHelp = "[birthdays]", fromTag = { emptyMap() }) { c, _, _ ->
            val bd = birthdays(c, 30)
            Plan.Answer(if (bd.isEmpty()) "No contact birthdays in the next 30 days. 🎂" else "🎂 " + bd.joinToString(", ") { (n, _, d) -> n + when (d) { 0 -> " today!"; 1 -> " tomorrow"; else -> " in $d days" } })
        },
        Capability("call_contact", CapGroup.PIM, "Call someone", "Find a contact by name and open the dialer with their number (the user presses call; never placed automatically).",
            listOf(Param("name", "string", "Contact name, e.g. Amma")), change = true,
            perm = { Perm.CONTACTS }, needs = listOf(Perm.CONTACTS), tag = "call", tagHelp = "[call NAME]", fromTag = { t -> t.takeIf { it.isNotBlank() }?.let { mapOf("name" to it) } }) { c, _, a ->
            val n = a.s("name").ifBlank { return@Capability Plan.Fail("Call who?") }
            val hits = findContacts(c, n)
            val (who, num) = hits.firstOrNull() ?: return@Capability Plan.Fail("I can't find “$n” in your contacts. 📇")
            Plan.Act("Open the dialer for $who ($num)? You press call. 📞") {
                Capabilities.open(c, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(num))), "the dialer", "Dialer's ready with $who. Press the green button! 📞")
            }
        },
    )
}

object BirthdayMath {
    /** Days from [today] to the next [month]-[day] (0 = today). */
    fun daysUntil(month: Int, day: Int, today: Calendar): Int {
        val t = (today.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val b = (t.clone() as Calendar).apply { set(Calendar.MONTH, month - 1); set(Calendar.DAY_OF_MONTH, minOf(day, getActualMaximum(Calendar.DAY_OF_MONTH))) }
        if (b.before(t)) b.add(Calendar.YEAR, 1)
        return ((b.timeInMillis - t.timeInMillis + 3_600_000L) / 86_400_000L).toInt()
    }
}
