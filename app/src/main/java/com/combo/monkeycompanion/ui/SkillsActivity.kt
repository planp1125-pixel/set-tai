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
import com.combo.monkeycompanion.agent.SettaiAccessibilityService
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.powers.ActionLog
import com.combo.monkeycompanion.powers.CapGroup
import com.combo.monkeycompanion.powers.Capabilities

/**
 * v7.0 "Settai's skills (errands & powers)": houses all assistant-style powers, screen errands,
 * taught daily habits (routines) and memory in world terms, keeping every capability and permission helper intact.
 */
class SkillsActivity : Activity() {
    private val d by lazy { resources.displayMetrics.density }
    private val cream = Color.rgb(0xFF, 0xF4, 0xE2)
    private val brown = Color.rgb(0x5A, 0x36, 0x18)
    private val soft = Color.rgb(0x8A, 0x6A, 0x4A)
    private val accent = Color.rgb(0xE8, 0x8A, 0x2E)
    private lateinit var a11yStatus: TextView

    private fun dp(v: Int) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChatHub.init(this)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            (if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        setContentView(build())
    }

    override fun onResume() {
        super.onResume()
        refreshA11y()
    }

    private fun refreshA11y() {
        if (!::a11yStatus.isInitialized) return
        if (com.combo.monkeycompanion.agent.ForegroundAppTracker.isPlayStoreBuild(this)) {
            val usage = com.combo.monkeycompanion.agent.ForegroundAppTracker.hasUsageAccess(this)
            a11yStatus.text = if (usage) "✅ Usage Access granted: Settai senses which app place you're visiting and respects your focus."
            else "💡 Optional: grant Usage Access so Settai knows when you walk into WhatsApp, YouTube or Swiggy."
            return
        }
        val on = SettaiAccessibilityService.enabled(this)
        val scr = ChatHub.settings.groupEnabled(CapGroup.SCREEN)
        a11yStatus.text = when {
            on && scr -> "✅ Ready for errands: Settai can read places and run errands when you ask (every tap/type asks Yes/No first)."
            on -> "⚠️ Accessibility is allowed, but 'Screen errands' is switched off below."
            else -> "Off by default. Turn on 'Settai screen agent' in Android Accessibility only if you want Settai to run errands inside other apps."
        }
    }

    private fun text(s: String, size: Float = 14f, bold: Boolean = false, color: Int = brown) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun button(label: String, primary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15.5f
        setTextColor(if (primary) Color.WHITE else brown)
        background = GradientDrawable().apply {
            cornerRadius = dp(24).toFloat()
            setColor(if (primary) accent else Color.WHITE)
            setStroke(dp(2), if (primary) accent else Color.rgb(0xE6, 0xC9, 0xA3))
        }
        setPadding(dp(14), dp(10), dp(14), dp(10))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
        setOnClickListener { onClick() }
    }

    private fun card(title: String, vararg views: View): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(14))
        background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.WHITE) }
        addView(text(title, 16.5f, bold = true).apply { setPadding(0, 0, 0, dp(4)) })
        views.forEach { addView(it) }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
    }

    private fun build(): View {
        val s = ChatHub.settings
        val who = CharacterRegistry.current.name
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(28))
        }
        col.addView(text("🛠️ Settai's skills", 26f, bold = true).apply { gravity = Gravity.CENTER_HORIZONTAL })
        col.addView(text("Settai is a creature living in your phone first — these optional skills let you send $who on errands into other places, sense the phone's world, or teach daily habits.", 13.5f, color = soft).apply {
            gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, dp(4), 0, dp(12))
        })

        // 1. Errands into other places (Play Store Guided Errands vs Full Screen Agent)
        a11yStatus = text("", 13f, color = soft)
        val playBuild = com.combo.monkeycompanion.agent.ForegroundAppTracker.isPlayStoreBuild(this)
        refreshA11y()
        if (playBuild) {
            col.addView(card(
                "🏃 Guided Errands (Settai opens the right screen & guides you)",
                text("Play Store Edition: $who opens the right app or screen for you (alarms, timers, maps, dialer, messages, settings) and guides you step-by-step from its speech bubble — 100% Google Play compliant without AccessibilityService.", 12.5f, color = soft),
                a11yStatus,
                button("Allow Usage Access (so Settai senses which place you visit)", primary = true) {
                    runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                },
                button("All errands, powers & permissions…") {
                    startActivity(Intent(this, CapabilitiesActivity::class.java))
                }
            ))
        } else {
            col.addView(card(
                "🧭 Errands into other places (Screen agent)",
                text("Ask $who to look at the place you're in (“what's on screen?”, “summarise this chat”, “reply for me”) or tap something for you. It never acts on its own: every tap or typed message asks Yes/No in Settai's bubble first.", 12.5f, color = soft),
                a11yStatus,
                button("Open Android Accessibility settings", primary = true) {
                    runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                },
                button("Allow Usage Access (optional place-sense fallback)") {
                    runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                },
                button("All errands, powers & permissions…") {
                    startActivity(Intent(this, CapabilitiesActivity::class.java))
                }
            ))
        }

        // 2. World senses & phone powers
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (g in CapGroup.entries) {
            val n = Capabilities.all.count { it.group == g }
            box.addView(CheckBox(this).apply {
                text = "${g.label} ($n)"
                setTextColor(brown); isChecked = s.groupEnabled(g)
                setOnCheckedChangeListener { _, c -> s.setGroupEnabled(g, c); refreshA11y() }
            })
        }
        val modes = listOf("auto", "clock", "settai")
        val clockSp = Spinner(this).apply {
            adapter = ArrayAdapter(this@SkillsActivity, android.R.layout.simple_spinner_dropdown_item, listOf(
                "Wake-up drumming: Clock app when possible, else Settai drums on the glass",
                "Wake-up drumming: always the Clock app",
                "Wake-up drumming: always Settai drumming on the glass from below"
            ))
            setSelection(modes.indexOf(s.clockMode).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { s.clockMode = modes[pos] }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        col.addView(card(
            "⚡ World senses & phone powers",
            text("Switch on which errands and senses $who may use. Anything that opens a place or changes a setting asks Yes/No first.", 12.5f, color = soft),
            box,
            clockSp
        ))

        // 3. Taught habits & memory
        col.addView(card(
            "📅 Habits you taught Settai & memory",
            text("Teach $who recurring daily habits (morning briefing, reminders, event triggers) or view the facts it remembers about you.", 12.5f, color = soft),
            button("📅  Daily habits & routines…", primary = true) {
                startActivity(Intent(this, RoutinesActivity::class.java))
            },
            button("🧠  What Settai remembers about you…") {
                startActivity(Intent(this, MemoryActivity::class.java))
            },
            button("📖  Settai's own diary…") {
                startActivity(Intent(this, DiaryActivity::class.java))
            },
            button("⚑  Reported replies…") {
                startActivity(Intent(this, ReportsActivity::class.java))
            },
            button("📋  Recent actions & mind log…") {
                AlertDialog.Builder(this).setTitle("Recent actions & mind decisions")
                    .setMessage(ActionLog.format(this).ifBlank { "Nothing yet." })
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Clear") { _, _ -> ActionLog.clear(this) }.show()
            }
        ))

        val scroll = ScrollView(this).apply {
            setBackgroundColor(cream)
            isFillViewport = true
            addView(col)
        }
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        return scroll
    }
}
