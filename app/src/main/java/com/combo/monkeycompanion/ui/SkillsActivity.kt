package com.combo.monkeycompanion.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.combo.monkeycompanion.agent.SettaiAccessibilityService
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.powers.ActionLog
import com.combo.monkeycompanion.powers.CapGroup
import com.combo.monkeycompanion.powers.Capabilities

/**
 * v7.0 "Settai's skills (errands & powers)" (v10.38.17: Material 3): screen errands, world senses & phone powers,
 * taught daily habits and memory. Every capability and permission helper is kept.
 */
class SkillsActivity : androidx.activity.ComponentActivity() {
    /** v10.38.24: texts use the main character's name instead of "Settai". */
    private val nm: String get() = com.combo.monkeycompanion.character.CharacterRegistry.current.name

    private var rev by androidx.compose.runtime.mutableIntStateOf(0)
    private var showLog by androidx.compose.runtime.mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChatHub.init(this)
        setContent { SettaiTheme(this) { Screen(rev) } }
    }

    override fun onResume() { super.onResume(); rev++ }

    private fun a11yLine(): String {
        if (com.combo.monkeycompanion.agent.ForegroundAppTracker.isPlayStoreBuild(this)) {
            val usage = com.combo.monkeycompanion.agent.ForegroundAppTracker.hasUsageAccess(this)
            return if (usage) "✅ Usage Access granted: ${nm} senses which app you're in and respects your focus."
            else "Optional: grant Usage Access so ${nm} knows when you open WhatsApp, YouTube or Swiggy."
        }
        val on = SettaiAccessibilityService.enabled(this)
        val scr = ChatHub.settings.groupEnabled(CapGroup.SCREEN)
        return when {
            on && scr -> "✅ Ready for errands: ${nm} can read screens and run errands when you ask (every tap or type asks Yes/No first)."
            on -> "⚠️ Accessibility is allowed, but 'Screen errands' is switched off below."
            else -> "Off by default. Turn on 'Settai screen agent' in Android Accessibility only if you want ${nm} to run errands inside other apps."
        }
    }

    @androidx.compose.runtime.Composable
    private fun Screen(@Suppress("UNUSED_PARAMETER") r: Int) {
        val s = ChatHub.settings
        val who = CharacterRegistry.current.name
        val playBuild = com.combo.monkeycompanion.agent.ForegroundAppTracker.isPlayStoreBuild(this)
        SettaiScreen("Skills", onBack = { finish() }, subtitle = "Optional errands, senses and habits for $who", rev = r) {
            if (playBuild) {
                SCard("Guided errands", "$who opens the right app or screen for you (alarms, timers, maps, dialer, messages, settings) and guides you step by step from its speech bubble.") {
                    SNote(a11yLine())
                    SPrimary("Allow Usage Access") { runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } }
                    SSecondary("All errands, powers & permissions") { startActivity(Intent(this@SkillsActivity, CapabilitiesActivity::class.java)) }
                }
            } else {
                SCard("Errands in other apps (screen agent)", "Ask $who to look at the app you're in (\"what's on screen?\", \"summarise this chat\", \"reply for me\") or tap something for you. It never acts on its own: every tap or typed message asks Yes/No first.") {
                    SNote(a11yLine())
                    SPrimary("Open Android Accessibility settings") { runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }
                    SSecondary("Allow Usage Access (optional)") { runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } }
                    SSecondary("All errands, powers & permissions") { startActivity(Intent(this@SkillsActivity, CapabilitiesActivity::class.java)) }
                }
            }
            SCard("World senses & phone powers", "Switch on which errands and senses $who may use. Anything that opens an app or changes a setting asks Yes/No first.") {
                for (g in CapGroup.entries) {
                    val n = Capabilities.all.count { it.group == g }
                    SSwitch("${g.label} ($n)", s.groupEnabled(g)) { s.setGroupEnabled(g, it); rev++ }
                }
                val modes = listOf("auto", "clock", "settai")
                SChoice("Wake-up alarm", listOf("Clock app when possible, else ${nm} drums on the glass", "Always the Clock app", "Always ${nm} drumming on the glass"),
                    modes.indexOf(s.clockMode).coerceAtLeast(0)) { s.clockMode = modes[it]; rev++ }
            }
            SCard("Habits & memory", "Teach $who daily habits (morning briefing, reminders, event triggers) or see what it remembers about you.") {
                SPrimary("Daily habits & routines") { startActivity(Intent(this@SkillsActivity, RoutinesActivity::class.java)) }
                SSecondary("What ${nm} remembers about you") { startActivity(Intent(this@SkillsActivity, MemoryActivity::class.java)) }
                SSecondary("Settai's own diary") { startActivity(Intent(this@SkillsActivity, DiaryActivity::class.java)) }
                SSecondary("Reported replies") { startActivity(Intent(this@SkillsActivity, ReportsActivity::class.java)) }
            }
            SCard("More (advanced)", "Behind-the-scenes logs for the curious.") {
                SSecondary("Recent actions & mind log") { showLog = true }
            }
        }
        if (showLog) {
            androidx.compose.material3.AlertDialog(onDismissRequest = { showLog = false },
                title = { androidx.compose.material3.Text("Recent actions & mind decisions") },
                text = {
                    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.heightIn(max = 420.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                        androidx.compose.material3.Text(ActionLog.format(this@SkillsActivity).ifBlank { "Nothing yet." })
                    }
                },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { showLog = false }) { androidx.compose.material3.Text("OK") } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { ActionLog.clear(this@SkillsActivity); showLog = false }) { androidx.compose.material3.Text("Clear") } })
        }
    }
}
