package com.combo.monkeycompanion.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.combo.monkeycompanion.agent.RoutineParse
import com.combo.monkeycompanion.agent.RoutineStore
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.powers.CapGroup
import com.combo.monkeycompanion.powers.ExtraCaps
import java.text.DateFormat
import java.util.Date

/** Shared look for the small v4 list screens. */
@SuppressLint("SetTextI18n")
abstract class SimpleListActivity : Activity() {
    protected val d by lazy { resources.displayMetrics.density }
    protected fun dp(v: Int) = (v * d).toInt()
    protected val brown = com.combo.monkeycompanion.theme.Th.rgb(0x1E, 0x22, 0x40)
    protected val soft = com.combo.monkeycompanion.theme.Th.rgb(0x64, 0x6A, 0x8C)

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); ChatHub.init(this) }
    override fun onResume() { super.onResume(); refresh() }
    protected fun refresh() { setContentView(ScrollView(this).apply { setBackgroundColor(com.combo.monkeycompanion.theme.Th.rgb(0xF3, 0xF4, 0xFA)); addView(build()) }) }
    protected abstract fun build(): View

    protected fun text(t: String, size: Float = 14f, bold: Boolean = false, color: Int = brown) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(2), 0, dp(2))
    }
    protected fun button(t: String, f: () -> Unit) = Button(this).apply { text = t; isAllCaps = false; setOnClickListener { f() } }
    protected fun card(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10))
        background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.WHITE) }
        views.forEach { addView(it) }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) }
    }
    protected fun row(main: View, vararg right: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(main, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)); right.forEach { addView(it) }
    }
    protected fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(14), dp(18), dp(28)) }
}

/** v4 Memory screen (v10.38.17: Material 3): everything Settai remembers, with delete. */
class MemoryActivity : androidx.activity.ComponentActivity() {
    private var rev by androidx.compose.runtime.mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); ChatHub.init(this)
        setContent { SettaiTheme(this) { Screen(rev) } }
    }
    override fun onResume() { super.onResume(); rev++ }

    @androidx.compose.runtime.Composable
    private fun Screen(@Suppress("UNUSED_PARAMETER") r: Int) {
        val st = ExtraCaps.memory(this)
        var input by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
        var confirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        val all = st.all().sortedByDescending { it.at }
        SettaiScreen("${NM}'s notebook", onBack = { finish() }, rev = r) {
            SNote("Things you told ${nm} to remember. Kept only in a file on this phone and used to make chats (and jokes) personal. Passwords, PINs and codes are never stored.")
            if (!ChatHub.settings.groupEnabled(CapGroup.MEMORY)) SNote("Memory is OFF: switch it on in Phone powers so ${nm} learns and uses these.")
            SCard("Add") {
                SField(input, { input = it }, "Remember", "e.g. I love filter coffee")
                SPrimary("Remember this") {
                    val t = input.trim()
                    if (t.isNotEmpty()) { st.add(com.combo.monkeycompanion.agent.MemoryParse.toYou(t)); input = ""; rev++ }
                }
            }
            if (all.isEmpty()) SCard("Nothing yet", "Say \"remember that my sister's birthday is 4 May\" in chat.") {}
            for (f in all) SCard(f.text) {
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) {
                        SNote(f.kind.toString() + " · " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(f.at)))
                    }
                    androidx.compose.material3.TextButton(onClick = { st.remove(f.id); rev++ }) { androidx.compose.material3.Text("Delete") }
                }
            }
            if (all.isNotEmpty()) SSecondary("Forget everything") { confirm = true }
        }
        if (confirm) androidx.compose.material3.AlertDialog(onDismissRequest = { confirm = false },
            text = { androidx.compose.material3.Text("Forget all ${all.size} things?") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { st.clear(); confirm = false; rev++ }) { androidx.compose.material3.Text("Forget all") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirm = false }) { androidx.compose.material3.Text("Cancel") } })
    }
}

/** v4 Routines screen (v10.38.17: Material 3): list, pause, delete, add from a sentence. */
class RoutinesActivity : androidx.activity.ComponentActivity() {
    private var rev by androidx.compose.runtime.mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); ChatHub.init(this)
        setContent { SettaiTheme(this) { Screen(rev) } }
    }
    override fun onResume() { super.onResume(); rev++ }

    @androidx.compose.runtime.Composable
    private fun Screen(@Suppress("UNUSED_PARAMETER") r: Int) {
        var input by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
        var preview by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
        SettaiScreen("${NM}'s routines", onBack = { finish() }, rev = r) {
            SNote("Time routines run even when ${nm} is asleep (alarm-clock style). Event routines (charger, battery low, Wi-Fi, headphones) run while ${nm} is on screen or in the wallpaper. Anything that changes the phone still asks Yes/No when it runs.")
            if (!ChatHub.settings.groupEnabled(CapGroup.ROUTINES)) SNote("Routines are OFF: switch them on in Phone powers.")
            SCard("Add a routine", "Examples: \"weekdays at 7:30 say good morning\" · \"every night at 10 turn on do not disturb\" · \"when headphones are plugged in say music time\" · \"every sunday at 6 pm remind me to water the plants\"") {
                SField(input, { input = it }, "Routine", "every day at 9 pm remind me to drink water")
                if (preview.isNotBlank()) SNote(preview)
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton(onClick = {
                        preview = RoutineParse.parse(input, ExtraCaps::routineCommand)?.describe()
                            ?: "I don't understand that one yet. Start with \"every day at …\", \"weekdays at …\" or \"when the charger is connected …\"."
                    }, modifier = androidx.compose.ui.Modifier.weight(1f)) { androidx.compose.material3.Text("Check") }
                    androidx.compose.material3.Button(onClick = {
                        val rt = RoutineParse.parse(input, ExtraCaps::routineCommand)
                        if (rt == null) Toast.makeText(this@RoutinesActivity, "Couldn't understand it", Toast.LENGTH_SHORT).show()
                        else { RoutineStore.add(this@RoutinesActivity, rt); input = ""; preview = ""; rev++ }
                    }, modifier = androidx.compose.ui.Modifier.weight(1f)) { androidx.compose.material3.Text("Save") }
                }
            }
            val l = RoutineStore.all(this@RoutinesActivity)
            if (l.isEmpty()) SNote("No routines yet.")
            for (rt in l) SCard("#${rt.id}") {
                SSwitch(rt.describe(), rt.enabled) { RoutineStore.setEnabled(this@RoutinesActivity, rt.id, it); rev++ }
                androidx.compose.material3.TextButton(onClick = { RoutineStore.remove(this@RoutinesActivity, rt.id); rev++ }) { androidx.compose.material3.Text("Delete") }
            }
        }
    }
}

/** Buttons to the Memory / Routines screens under their Phone powers cards. */
object AgentScreens {
    fun extras(a: Activity, g: CapGroup, col: LinearLayout) {
        fun b(t: String, f: () -> Unit) = Button(a).apply { text = t; isAllCaps = false; setOnClickListener { f() } }
        when (g) {
            CapGroup.MEMORY -> col.addView(b("Open ${nm}'s notebook (view / delete)") { a.startActivity(Intent(a, MemoryActivity::class.java)) })
            CapGroup.ROUTINES -> col.addView(b("Open routines") { a.startActivity(Intent(a, RoutinesActivity::class.java)) })
            else -> AgentScreensMore.extras(a, g, col)
        }
    }
}

/** Voice / web / PIM settings under their cards. */
@SuppressLint("SetTextI18n", "UseSwitchCompatOrMaterialCode")
object AgentScreensMore {
    fun extras(a: Activity, g: CapGroup, col: LinearLayout) {
        val s = ChatHub.settings
        val brown = com.combo.monkeycompanion.theme.Th.rgb(0x1E, 0x22, 0x40); val soft = com.combo.monkeycompanion.theme.Th.rgb(0x64, 0x6A, 0x8C)
        fun note(t: String) = TextView(a).apply { text = t; textSize = 12f; setTextColor(soft) }
        when (g) {
            CapGroup.VOICE -> {
                col.addView(Switch(a).apply { text = "Speak replies out loud"; textSize = 14f; setTextColor(brown); isChecked = s.speakReplies; setOnCheckedChangeListener { _, c -> s.speakReplies = c } })
                col.addView(Button(a).apply { text = "Test voice"; isAllCaps = false; setOnClickListener { com.combo.monkeycompanion.agent.Voice.speak(a, "Hi! I'm ${com.combo.monkeycompanion.character.CharacterRegistry.current.name}. Nice to meet you!") } })
                col.addView(note("The 🎤 button appears in the chat once the mic is granted. ${NM} listens only while you press it — no always-on wake word (maybe a future power)."))
            }
            CapGroup.WEB -> {
                col.addView(note("Lookups use DuckDuckGo Instant Answers + Wikipedia (no key). For full web results, paste a search API URL with {q} where the query goes (e.g. your SearXNG instance: https://searx.example/search?format=json&q={q})."))
                col.addView(EditText(a).apply {
                    hint = "Optional search URL with {q}"; setText(s.searchUrl); textSize = 13f
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                        override fun afterTextChanged(e: android.text.Editable?) { s.searchUrl = e.toString().trim() }
                    })
                })
                col.addView(note("Weather: Open-Meteo (free, no key). Location is only read when you ask for weather without a city."))
            }
            CapGroup.PIM -> col.addView(note("Also gives a 9 am birthday reminder from your contacts. Calls are never placed: ${nm} only opens the dialer."))
            CapGroup.DOCTOR -> col.addView(note("Android doesn't share per-app battery numbers with apps, so “battery hogs” = the apps you used most today (screen-on time)."))
            else -> {}
        }
    }
}

/** v4 Reported replies (v10.38.17: Material 3): AI replies the user flagged. Stored on this phone only; Share is user-initiated. */
class ReportsActivity : androidx.activity.ComponentActivity() {
    private var rev by androidx.compose.runtime.mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); ChatHub.init(this)
        setContent { SettaiTheme(this) { Screen(rev) } }
    }

    @androidx.compose.runtime.Composable
    private fun Screen(@Suppress("UNUSED_PARAMETER") r: Int) {
        val st = com.combo.monkeycompanion.agent.ReplyReports.store(this)
        val all = st.all().sortedByDescending { it.at }
        SettaiScreen("Reported replies", onBack = { finish() }, rev = r) {
            SNote("Replies you flagged with ⚑ Report. They stay in a file on this phone; nothing is uploaded. Use Share to send one yourself.")
            if (all.isEmpty()) SNote("Nothing reported. Tap ⚑ on a reply in the bubble or the chat if ${nm} says something wrong or not OK.")
            for (rp in all) SCard("Reply: " + rp.reply) {
                SNote("You: " + rp.prompt.ifBlank { "(not recorded)" })
                SNote(rp.brain + " · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(rp.at)))
                androidx.compose.foundation.layout.Row {
                    androidx.compose.material3.TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, com.combo.monkeycompanion.agent.ReplyReportStore.shareText(rp))
                        runCatching { startActivity(Intent.createChooser(send, "Share report")) }
                    }) { androidx.compose.material3.Text("Share") }
                    androidx.compose.material3.TextButton(onClick = { st.remove(rp.id); rev++ }) { androidx.compose.material3.Text("Delete") }
                }
            }
            if (all.isNotEmpty()) SSecondary("Delete all") { st.clear(); rev++ }
        }
    }
}

// v10.39.2: settings work for every character, so the screens say "your character", not one name
private val nm: String get() = "your character"
private val NM: String get() = "Your character"
