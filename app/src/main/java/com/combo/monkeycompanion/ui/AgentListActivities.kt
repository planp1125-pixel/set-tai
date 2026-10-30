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
    protected val brown = Color.rgb(0x5A, 0x36, 0x18)
    protected val soft = Color.rgb(0x8A, 0x63, 0x3E)

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); ChatHub.init(this) }
    override fun onResume() { super.onResume(); refresh() }
    protected fun refresh() { setContentView(ScrollView(this).apply { setBackgroundColor(Color.rgb(0xFF, 0xF4, 0xE2)); addView(build()) }) }
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

/** v4 Memory screen: everything Settai remembers, with delete. */
@SuppressLint("SetTextI18n")
class MemoryActivity : SimpleListActivity() {
    override fun build(): View {
        val st = ExtraCaps.memory(this)
        val col = column()
        col.addView(text("Settai's notebook 📒", 26f, true))
        col.addView(text("Things you told Settai to remember. Kept only in a file on this phone. Used to make chats (and jokes) personal. Passwords, PINs and codes are never stored." +
            if (!ChatHub.settings.groupEnabled(CapGroup.MEMORY)) "\n\nMemory is OFF: switch it on in Phone powers so Settai learns and uses these." else "", 13f, color = soft))
        val input = EditText(this).apply { hint = "Add: e.g. I love filter coffee"; textSize = 14f }
        col.addView(card(input, button("Remember this") {
            val t = input.text.toString().trim()
            if (t.isNotEmpty()) { st.add(com.combo.monkeycompanion.agent.MemoryParse.toYou(t)); refresh() }
        }))
        val all = st.all().sortedByDescending { it.at }
        if (all.isEmpty()) col.addView(card(text("Nothing yet. Say “remember that my sister's birthday is 4 May” in chat.", 14f, color = soft)))
        for (f in all) col.addView(card(row(text(f.text + "\n" + f.kind + " · " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(f.at)), 14f),
            button("Delete") { st.remove(f.id); refresh() })))
        if (all.isNotEmpty()) col.addView(card(button("Forget everything") {
            android.app.AlertDialog.Builder(this).setMessage("Forget all ${all.size} things?").setPositiveButton("Forget all") { _, _ -> st.clear(); refresh() }.setNegativeButton("Cancel", null).show()
        }))
        return col
    }
}

/** v4 Routines screen: list, pause, delete, add from a sentence. */
@SuppressLint("SetTextI18n", "UseSwitchCompatOrMaterialCode")
class RoutinesActivity : SimpleListActivity() {
    override fun build(): View {
        val col = column()
        col.addView(text("Settai's routines 🗓️", 26f, true))
        col.addView(text("Time routines run even when Settai is asleep (alarm-clock style). Event routines (charger, battery low, Wi-Fi, headphones) run while Settai is on screen or in the wallpaper. Anything that changes the phone still asks Yes/No when it runs." +
            if (!ChatHub.settings.groupEnabled(CapGroup.ROUTINES)) "\n\nRoutines are OFF: switch them on in Phone powers." else "", 13f, color = soft))
        val input = EditText(this).apply { hint = "e.g. every day at 9 pm remind me to drink water"; textSize = 14f }
        val preview = text("", 13f, color = soft)
        col.addView(card(input, preview, row(button("Check") {
            preview.text = RoutineParse.parse(input.text.toString(), ExtraCaps::routineCommand)?.describe() ?: "I don't understand that one yet. Start with “every day at …”, “weekdays at …” or “when the charger is connected …”."
        }, button("Save") {
            val r = RoutineParse.parse(input.text.toString(), ExtraCaps::routineCommand)
            if (r == null) Toast.makeText(this, "Couldn't understand it", Toast.LENGTH_SHORT).show() else { RoutineStore.add(this, r); refresh() }
        })))
        col.addView(text("Examples: “weekdays at 7:30 say good morning machan” · “every night at 10 turn on do not disturb” · “when headphones are plugged in say music time” · “every sunday at 6 pm remind me to water the plants”", 12f, color = soft))
        val l = RoutineStore.all(this)
        if (l.isEmpty()) col.addView(card(text("No routines yet.", 14f, color = soft)))
        for (r in l) {
            val sw = Switch(this).apply { isChecked = r.enabled; setOnCheckedChangeListener { _, c -> RoutineStore.setEnabled(this@RoutinesActivity, r.id, c) } }
            col.addView(card(row(text("#${r.id}  ${r.describe()}", 14f), sw), row(View(this), button("Delete") { RoutineStore.remove(this, r.id); refresh() })))
        }
        return col
    }
}

/** Buttons to the Memory / Routines screens under their Phone powers cards. */
object AgentScreens {
    fun extras(a: Activity, g: CapGroup, col: LinearLayout) {
        fun b(t: String, f: () -> Unit) = Button(a).apply { text = t; isAllCaps = false; setOnClickListener { f() } }
        when (g) {
            CapGroup.MEMORY -> col.addView(b("Open Settai's notebook (view / delete)") { a.startActivity(Intent(a, MemoryActivity::class.java)) })
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
        val brown = Color.rgb(0x5A, 0x36, 0x18); val soft = Color.rgb(0x8A, 0x63, 0x3E)
        fun note(t: String) = TextView(a).apply { text = t; textSize = 12f; setTextColor(soft) }
        when (g) {
            CapGroup.VOICE -> {
                col.addView(Switch(a).apply { text = "Speak replies out loud"; textSize = 14f; setTextColor(brown); isChecked = s.speakReplies; setOnCheckedChangeListener { _, c -> s.speakReplies = c } })
                col.addView(Button(a).apply { text = "Test voice"; isAllCaps = false; setOnClickListener { com.combo.monkeycompanion.agent.Voice.speak(a, "Vanakkam! I'm Settai, your naughty monkey. Banana time?") } })
                col.addView(note("The 🎤 button appears in the chat once the mic is granted. Settai listens only while you press it — no always-on wake word (maybe a future power)."))
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
            CapGroup.PIM -> col.addView(note("Also gives a 9 am birthday reminder from your contacts. Calls are never placed: Settai only opens the dialer."))
            CapGroup.DOCTOR -> col.addView(note("Android doesn't share per-app battery numbers with apps, so “battery hogs” = the apps you used most today (screen-on time)."))
            else -> {}
        }
    }
}

/** v4 Reported replies: AI replies the user flagged. Stored on this phone only; Share is user-initiated. */
class ReportsActivity : SimpleListActivity() {
    override fun build(): View {
        val st = com.combo.monkeycompanion.agent.ReplyReports.store(this)
        val col = column()
        col.addView(text("Reported replies ⚑", 26f, true))
        col.addView(text("Replies you flagged with ⚑ Report. They stay in a file on this phone; nothing is uploaded. Use Share to send one yourself (e.g. to the developer or your AI provider's feedback form).", 13f, color = soft))
        val all = st.all().sortedByDescending { it.at }
        if (all.isEmpty()) col.addView(card(text("Nothing reported. Tap ⚑ on a reply in the bubble or the chat if Settai says something wrong or not OK.", 14f, color = soft)))
        for (r in all) col.addView(card(
            text("You: " + r.prompt.ifBlank { "(not recorded)" }, 13f, color = soft),
            text("Settai: " + r.reply, 14f),
            text(r.brain + " · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(r.at)), 12f, color = soft),
            row(button("Share…") {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, com.combo.monkeycompanion.agent.ReplyReportStore.shareText(r))
                runCatching { startActivity(Intent.createChooser(send, "Share report")) }
            }, button("Delete") { st.remove(r.id); refresh() })))
        if (all.isNotEmpty()) col.addView(card(button("Delete all") { st.clear(); refresh() }))
        return col
    }
}
