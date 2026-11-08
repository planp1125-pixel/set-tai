package com.combo.monkeycompanion.ui

import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.graphics.Color
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.combo.monkeycompanion.agent.AgentSafety
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.powers.CapGroup

/** Per-group settings shown under each v4 agent power card. */
@SuppressLint("SetTextI18n", "UseSwitchCompatOrMaterialCode")
object AgentExtras {
    private val brown = com.combo.monkeycompanion.theme.Th.rgb(0x1E, 0x22, 0x40)
    private val soft = com.combo.monkeycompanion.theme.Th.rgb(0x64, 0x6A, 0x8C)

    fun extras(a: Activity, g: CapGroup): View? {
        val s = ChatHub.settings
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        fun sw(t: String, v: Boolean, set: (Boolean) -> Unit) = Switch(a).apply { text = t; textSize = 14f; setTextColor(brown); isChecked = v; setOnCheckedChangeListener { _, c -> set(c) } }
        fun note(t: String) = TextView(a).apply { text = t; textSize = 12f; setTextColor(soft) }
        fun field(hint: String, v: String, set: (String) -> Unit) = EditText(a).apply {
            this.hint = hint; setText(v); textSize = 13f; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setOnFocusChangeListener { _, f -> if (!f) set(text.toString()) }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                override fun afterTextChanged(e: android.text.Editable?) { set(e.toString()) }
            })
        }
        when (g) {
            CapGroup.SCREEN -> {
                col.addView(sw("Ask before every tap", s.agentConfirmEach) { s.agentConfirmEach = it })
                col.addView(note("Typing always asks. Buttons like Send / Pay / Delete / Post / Buy always ask, even with this off."))
                col.addView(note("Never driven: lock screen, password fields, Settai itself, and banking / UPI / wallet apps (${AgentSafety.DEFAULT_BLOCKED.size} built in, plus any package with bank / upi / wallet in its name)."))
                col.addView(field("More apps to block (package names, comma separated)", s.agentBlocked) { s.agentBlocked = it })
                col.addView(field("Allow anyway (package names that were wrongly blocked)", s.agentAllowed) { s.agentAllowed = it })
            }
            else -> AgentScreens.extras(a, g, col)
        }
        return if (col.childCount == 0) null else col
    }
}


/** v10.38.20: the same per-group extras as [AgentExtras.extras], in Material 3 (used on "What can Set-Tai do?"). */
@androidx.compose.runtime.Composable
internal fun AgentExtrasPanel(a: Activity, g: CapGroup) {
    val s = ChatHub.settings
    var rev by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val r = rev
    when (g) {
        CapGroup.SCREEN -> {
            var blocked by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(s.agentBlocked) }
            var allowed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(s.agentAllowed) }
            SSwitch("Ask before every tap", s.agentConfirmEach) { s.agentConfirmEach = it; rev++ }
            SNote("Typing always asks. Buttons like Send / Pay / Delete / Post / Buy always ask, even with this off.")
            SNote("Never driven: lock screen, password fields, Settai itself, and banking / UPI / wallet apps (${AgentSafety.DEFAULT_BLOCKED.size} built in, plus any package with bank / upi / wallet in its name).")
            SField(blocked, { blocked = it; s.agentBlocked = it }, "More apps to block", "package names, comma separated")
            SField(allowed, { allowed = it; s.agentAllowed = it }, "Allow anyway", "package names that were wrongly blocked")
        }
        CapGroup.MEMORY -> SSecondary("Open Settai's notebook (view / delete)") { a.startActivity(android.content.Intent(a, MemoryActivity::class.java)) }
        CapGroup.ROUTINES -> SSecondary("Open routines") { a.startActivity(android.content.Intent(a, RoutinesActivity::class.java)) }
        CapGroup.VOICE -> {
            SSwitch("Speak replies out loud", s.speakReplies) { s.speakReplies = it; rev++ }
            SSecondary("Test voice") { com.combo.monkeycompanion.agent.Voice.speak(a, "Vanakkam! I'm Settai, your naughty monkey. Banana time?") }
            SNote("The 🎤 button appears in the chat once the mic is granted. Settai listens only while you press it. No always-on wake word.")
        }
        CapGroup.WEB -> {
            var url by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(s.searchUrl) }
            SNote("Lookups use DuckDuckGo Instant Answers + Wikipedia (no key). For full web results, paste a search API URL with {q} where the query goes (e.g. https://searx.example/search?format=json&q={q}).")
            SField(url, { url = it; s.searchUrl = it.trim() }, "Optional search URL with {q}")
            SNote("Weather: Open-Meteo (free, no key). Location is only read when you ask for weather without a city.")
        }
        CapGroup.PIM -> SNote("Also gives a 9 am birthday reminder from your contacts. Calls are never placed: Settai only opens the dialer.")
        CapGroup.DOCTOR -> SNote("Android doesn't share per-app battery numbers with apps, so \"battery hogs\" = the apps you used most today (screen-on time).")
        else -> {}
    }
}
