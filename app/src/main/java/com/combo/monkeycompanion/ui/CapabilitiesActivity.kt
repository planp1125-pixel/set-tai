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
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.powers.CapGroup
import com.combo.monkeycompanion.powers.Capabilities
import com.combo.monkeycompanion.powers.Perm

/** "What can Settai do?": every phone power, grouped, with on/off switches and permission status + one-tap grant. */
@SuppressLint("SetTextI18n", "UseSwitchCompatOrMaterialCode")
class CapabilitiesActivity : Activity() {
    private val d by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * d).toInt()
    private val brown = com.combo.monkeycompanion.theme.Th.rgb(0x1E, 0x22, 0x40)
    private val soft = com.combo.monkeycompanion.theme.Th.rgb(0x64, 0x6A, 0x8C)
    private val good = com.combo.monkeycompanion.theme.Th.rgb(0x2E, 0x7D, 0x32)
    private val warn = com.combo.monkeycompanion.theme.Th.rgb(0xC6, 0x28, 0x28)

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); ChatHub.init(this) }
    override fun onResume() { super.onResume(); setContentView(build()) }

    private fun text(t: String, size: Float = 14f, bold: Boolean = false, color: Int = brown) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(2), 0, dp(2))
    }

    private fun build(): View {
        val s = ChatHub.settings
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(14), dp(18), dp(28)) }
        col.addView(text("What can Set-Tai do?", 26f, true))
        col.addView(text("Every brain (online, on-device or none) can use these. Anything that changes or opens something asks Yes/No in Settai's bubble first. Just ask in your own words — e.g. “volume 40”, “brightness up”, “silent mode”, “wake me at 6”, “next song”, “turn on wifi”.", 13f, color = soft))

        val perms = Perm.entries.filter { it != Perm.NONE }
        col.addView(card(text("Special access", 17f, true), *perms.map { p ->
            row(p.label + if (p.granted(this)) "  ✓ granted" else "  ✗ not granted", if (p.granted(this)) good else warn,
                if (p.granted(this)) null else "Grant" to { runCatching { startActivity(p.grantIntent(this).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } })
        }.toTypedArray()))

        // ---- v4 agent powers: each off until switched on, explained, with its own Grant buttons ----
        col.addView(text("Agent powers (v4)", 22f, true).apply { setPadding(0, dp(18), 0, 0) })
        col.addView(text("Bigger powers that let Settai act more like a phone assistant. All are OFF until you switch them on. Anything that changes something in another app or sends anything asks Yes/No in the bubble first — Settai never sends on its own.", 13f, color = soft))
        for (g in CapGroup.entries.filter { it.optIn }) col.addView(agentCard(g))
        col.addView(android.widget.Button(this).apply {
            text = "⚑ Reported replies"; isAllCaps = false
            setOnClickListener { startActivity(android.content.Intent(this@CapabilitiesActivity, ReportsActivity::class.java)) }
        })

        col.addView(text("Everyday powers", 22f, true).apply { setPadding(0, dp(18), 0, 0) })
        for (g in CapGroup.entries.filter { !it.optIn }) {
            val caps = Capabilities.all.filter { it.group == g }
            val sw = Switch(this).apply {
                text = g.label; textSize = 16f; setTextColor(brown); setTypeface(typeface, Typeface.BOLD); isChecked = s.groupEnabled(g)
                setOnCheckedChangeListener { _, c -> s.setGroupEnabled(g, c) }
            }
            col.addView(card(sw, *caps.map { c ->
                val needs = c.needs.filter { it != Perm.NONE }
                val missing = needs.filter { !it.granted(this) }
                val line = "• ${c.title}" + (if (c.change) "  (asks first)" else "  (reads)") +
                    if (needs.isNotEmpty()) "\n   needs: " + needs.joinToString { it.label + if (it.granted(this)) " ✓" else " ✗" } else ""
                row(line, if (missing.isEmpty()) brown else warn, missing.firstOrNull()?.let { p -> "Grant" to { runCatching { startActivity(p.grantIntent(this)) } } })
            }.toTypedArray()))
        }

        col.addView(card(text("What Android won't let any app do", 17f, true),
            text("• Flip Wi-Fi (Android 10+), Bluetooth (13+), mobile data, airplane mode, location, NFC or hotspot directly — Settai opens the system switch for one tap instead.\n" +
                "• Show Settai's overlay on top of the lock screen — the live wallpaper (set for Home and lock screens) shows sleeping Settai there instead, plus the lock-screen notification.\n" +
                "• Send SMS, place calls, or save calendar events silently — Settai fills them in and you press send/call/save.\n" +
                "• Read live CPU load or temperatures other than the battery's.\n" +
                "• Open other apps while Settai's not visible on screen (Android blocks background launches) — Settai posts a tap-to-open notification instead.", 13f, color = soft)))
        return ScrollView(this).apply { setBackgroundColor(com.combo.monkeycompanion.theme.Th.rgb(0xF3, 0xF4, 0xFA)); addView(col) }
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
        if (p == Perm.ACCESSIBILITY) {
            // Prominent disclosure before the Accessibility switch (Play policy + plain honesty).
            android.app.AlertDialog.Builder(this).setTitle("Let Settai drive your screen?")
                .setMessage(getString(com.combo.monkeycompanion.R.string.a11y_description) + "\n\nOn the next screen pick “Settai screen agent” and switch it on." +
                    "\n\nGreyed out / “Restricted setting”? Android 13+ blocks this for apps installed from an APK file. Open App info > ⋮ (top right) > Allow restricted settings, then come back.")
                .setPositiveButton("I agree, open settings") { _, _ -> runCatching { startActivity(p.grantIntent(this)) } }
                .setNegativeButton("Not now", null).show()
            return
        }
        runCatching { startActivity(p.grantIntent(this).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        setContentView(build())
    }

    private fun agentCard(g: CapGroup): View {
        val s = ChatHub.settings
        val sw = Switch(this).apply {
            text = g.label; textSize = 16f; setTextColor(brown); setTypeface(typeface, Typeface.BOLD); isChecked = s.groupEnabled(g)
            setOnCheckedChangeListener { _, c ->
                s.setGroupEnabled(g, c)
                if (g == CapGroup.ROUTINES) com.combo.monkeycompanion.agent.RoutineScheduler.refresh(this@CapabilitiesActivity)
                setContentView(build())
            }
        }
        val views = ArrayList<View>()
        views.add(text(g.explain, 13f, color = soft))
        for (p in groupPerms(g)) {
            val ok = p.granted(this)
            views.add(row(p.label + if (ok) "  ✓ granted" else "  ✗ not granted", if (ok) good else warn, if (ok) null else "Grant" to { grant(p) }))
        }
        if ((g == CapGroup.SCREEN || g == CapGroup.NOTIFS) && android.os.Build.VERSION.SDK_INT >= 33)
            views.add(row("Switch greyed out (“Restricted setting”)? Sideloaded APKs need: App info > ⋮ > Allow restricted settings, then grant again.", soft,
                "App info" to { runCatching { startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:$packageName"))) } }))
        if (g == CapGroup.NOTIFS && android.os.Build.VERSION.SDK_INT >= 35)
            views.add(text("Note: Android 15+ may hide one-time codes from notification readers. If so, Settai says the code is hidden instead of showing it.", 12f, color = soft))
        if (g == CapGroup.ROUTINES && android.os.Build.VERSION.SDK_INT >= 31) {
            val ok = com.combo.monkeycompanion.powers.SettaiClock.exactAllowed(this)
            views.add(row("Exact alarms" + if (ok) "  ✓ allowed" else "  ✗ off (timed routines may run a few minutes late)", if (ok) good else warn,
                if (ok) null else "Allow" to { runCatching { startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    android.net.Uri.parse("package:$packageName"))) } }))
        }
        val caps = Capabilities.all.filter { it.group == g }
        if (caps.isNotEmpty()) views.add(text(caps.joinToString("\n") { "• " + it.title + if (it.change) "  (asks first)" else "" }, 13f))
        AgentExtras.extras(this, g)?.let { views.add(it) }
        return card(sw, *views.toTypedArray())
    }

    private fun row(t: String, color: Int, action: Pair<String, () -> Unit>?) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(text(t, 14f, color = color), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (action != null) addView(Button(this@CapabilitiesActivity).apply { text = action.first; isAllCaps = false; setOnClickListener { action.second() } })
    }

    private fun card(title: View, vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(14))
        background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.WHITE) }
        addView(title); views.forEach { addView(it) }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) }
    }
}
