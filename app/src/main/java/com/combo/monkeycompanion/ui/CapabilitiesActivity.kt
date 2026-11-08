package com.combo.monkeycompanion.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.powers.CapGroup
import com.combo.monkeycompanion.powers.Capabilities
import com.combo.monkeycompanion.powers.Perm

/** "What can Settai do?" (v10.38.17: Material 3): every phone power, grouped, with on/off switches and permission status + one-tap grant. */
@SuppressLint("SetTextI18n")
class CapabilitiesActivity : androidx.activity.ComponentActivity() {
    private var rev by androidx.compose.runtime.mutableIntStateOf(0)
    private var a11yAsk by androidx.compose.runtime.mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChatHub.init(this)
        setContent { SettaiTheme(this) { Screen(rev) } }
    }
    override fun onResume() { super.onResume(); rev++ }

    private val good = androidx.compose.ui.graphics.Color(0xFF2E7D32)
    private val warn = androidx.compose.ui.graphics.Color(0xFFC62828)

    @androidx.compose.runtime.Composable
    private fun PermRow(label: String, ok: Boolean, okWord: String = "granted", action: String = "Grant", onGrant: () -> Unit) {
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.material3.Text("$label  " + if (ok) "✓ $okWord" else "✗ not $okWord",
                color = if (ok) good else warn, modifier = androidx.compose.ui.Modifier.weight(1f))
            if (!ok) androidx.compose.material3.TextButton(onClick = onGrant) { androidx.compose.material3.Text(action) }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Screen(@Suppress("UNUSED_PARAMETER") r: Int) {
        val s = ChatHub.settings
        SettaiScreen("What can Set-Tai do?", onBack = { finish() }) {
            SNote("Every brain (online, on-device or none) can use these. Anything that changes or opens something asks Yes/No in Settai's bubble first. Just ask in your own words: \"volume 40\", \"brightness up\", \"silent mode\", \"wake me at 6\", \"next song\", \"turn on wifi\".")
            SCard("Special access") {
                for (p in Perm.entries.filter { it != Perm.NONE }) PermRow(p.label, p.granted(this@CapabilitiesActivity)) {
                    runCatching { startActivity(p.grantIntent(this@CapabilitiesActivity).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
            SSubhead("Agent powers")
            SNote("Bigger powers that let Settai act more like a phone assistant. All are OFF until you switch them on. Anything that changes something in another app or sends anything asks Yes/No first. Settai never sends on its own.")
            for (g in CapGroup.entries.filter { it.optIn }) AgentCard(g, r)
            SSecondary("Reported replies") { startActivity(android.content.Intent(this@CapabilitiesActivity, ReportsActivity::class.java)) }
            SSubhead("Everyday powers")
            for (g in CapGroup.entries.filter { !it.optIn }) {
                val caps = Capabilities.all.filter { it.group == g }
                SCard(g.label) {
                    SSwitch("Allowed", s.groupEnabled(g)) { s.setGroupEnabled(g, it); rev++ }
                    for (c in caps) {
                        val needs = c.needs.filter { it != Perm.NONE }
                        val missing = needs.filter { !it.granted(this@CapabilitiesActivity) }
                        val line = "• ${c.title}" + (if (c.change) "  (asks first)" else "  (reads)") +
                            if (needs.isNotEmpty()) "\n   needs: " + needs.joinToString { it.label + if (it.granted(this@CapabilitiesActivity)) " ✓" else " ✗" } else ""
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            androidx.compose.material3.Text(line, color = if (missing.isEmpty()) androidx.compose.material3.MaterialTheme.colorScheme.onSurface else warn,
                                modifier = androidx.compose.ui.Modifier.weight(1f))
                            missing.firstOrNull()?.let { p ->
                                androidx.compose.material3.TextButton(onClick = { runCatching { startActivity(p.grantIntent(this@CapabilitiesActivity)) } }) { androidx.compose.material3.Text("Grant") }
                            }
                        }
                    }
                }
            }
            SCard("What Android won't let any app do") {
                SNote("• Flip Wi-Fi (Android 10+), Bluetooth (13+), mobile data, airplane mode, location, NFC or hotspot directly. Settai opens the system switch for one tap instead.\n" +
                    "• Show Settai's overlay on top of the lock screen. The live wallpaper shows sleeping Settai there instead, plus the lock-screen notification.\n" +
                    "• Send SMS, place calls, or save calendar events silently. Settai fills them in and you press send / call / save.\n" +
                    "• Read live CPU load or temperatures other than the battery's.\n" +
                    "• Open other apps while Settai's not visible on screen (Android blocks background launches). Settai posts a tap-to-open notification instead.")
            }
        }
        if (a11yAsk) {
            androidx.compose.material3.AlertDialog(onDismissRequest = { a11yAsk = false },
                title = { androidx.compose.material3.Text("Let Settai drive your screen?") },
                text = {
                    androidx.compose.material3.Text(getString(com.combo.monkeycompanion.R.string.a11y_description) + "\n\nOn the next screen pick \"Settai screen agent\" and switch it on." +
                        "\n\nGreyed out / \"Restricted setting\"? Android 13+ blocks this for apps installed from an APK file. Open App info > ⋮ (top right) > Allow restricted settings, then come back.")
                },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { a11yAsk = false; runCatching { startActivity(Perm.ACCESSIBILITY.grantIntent(this)) } }) { androidx.compose.material3.Text("I agree, open settings") } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { a11yAsk = false }) { androidx.compose.material3.Text("Not now") } })
        }
    }

    /** Which special access each agent group needs (each gets its own Grant button). */
    private fun groupPerms(g: CapGroup): List<Perm> = when (g) {
        CapGroup.SCREEN -> listOf(Perm.ACCESSIBILITY)
        CapGroup.NOTIFS -> listOf(Perm.NOTIF_LISTENER)
        CapGroup.ROUTINES -> listOf(Perm.POST_NOTIF)
        CapGroup.VOICE -> listOf(Perm.MIC)
        CapGroup.WEB -> listOf(Perm.LOCATION)
        CapGroup.DOCTOR -> listOf(Perm.USAGE_STATS)
        CapGroup.PIM -> listOf(Perm.CALENDAR, Perm.CONTACTS)
        else -> emptyList()
    }

    private fun grant(p: Perm) {
        val rt = p.runtime
        if (rt != null) { requestPermissions(arrayOf(rt), 41); return }
        if (p == Perm.ACCESSIBILITY) { a11yAsk = true; return }   // prominent disclosure first (Play policy + plain honesty)
        runCatching { startActivity(p.grantIntent(this).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        rev++
    }

    @androidx.compose.runtime.Composable
    private fun AgentCard(g: CapGroup, @Suppress("UNUSED_PARAMETER") r: Int) {
        val s = ChatHub.settings
        SCard(g.label, g.explain) {
            SSwitch("Allowed", s.groupEnabled(g)) { on ->
                s.setGroupEnabled(g, on)
                if (g == CapGroup.ROUTINES) com.combo.monkeycompanion.agent.RoutineScheduler.refresh(this@CapabilitiesActivity)
                rev++
            }
            for (p in groupPerms(g)) PermRow(p.label, p.granted(this@CapabilitiesActivity)) { grant(p) }
            if ((g == CapGroup.SCREEN || g == CapGroup.NOTIFS) && android.os.Build.VERSION.SDK_INT >= 33) {
                SNote("Switch greyed out (\"Restricted setting\")? Sideloaded APKs need: App info > ⋮ > Allow restricted settings, then grant again.")
                androidx.compose.material3.TextButton(onClick = { runCatching { startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:$packageName"))) } }) { androidx.compose.material3.Text("Open App info") }
            }
            if (g == CapGroup.NOTIFS && android.os.Build.VERSION.SDK_INT >= 35)
                SNote("Android 15+ may hide one-time codes from notification readers. If so, Settai says the code is hidden instead of showing it.")
            if (g == CapGroup.ROUTINES && android.os.Build.VERSION.SDK_INT >= 31) {
                val ok = com.combo.monkeycompanion.powers.SettaiClock.exactAllowed(this@CapabilitiesActivity)
                PermRow("Exact alarms", ok, okWord = "allowed", action = "Allow") {
                    runCatching { startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:$packageName"))) }
                }
                if (!ok) SNote("Without exact alarms, timed routines may run a few minutes late.")
            }
            val caps = Capabilities.all.filter { it.group == g }
            if (caps.isNotEmpty()) androidx.compose.material3.Text(caps.joinToString("\n") { "• " + it.title + if (it.change) "  (asks first)" else "" })
            // Group-specific extras (blocked apps, VIP list, …) keep their existing controls for now.
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { AgentExtras.extras(this@CapabilitiesActivity, g) ?: android.view.View(it) },
                modifier = androidx.compose.ui.Modifier.fillMaxWidth()
            )
        }
    }
}
