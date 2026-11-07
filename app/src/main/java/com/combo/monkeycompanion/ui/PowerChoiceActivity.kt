package com.combo.monkeycompanion.ui

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.combo.monkeycompanion.ai.AppSettings
import com.combo.monkeycompanion.brain.PowerPrefs
import com.combo.monkeycompanion.character.Character
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.theme.Th

/** v10.17: opened from Kutti Satan's long-press powers ring: pick which of his powers are on (multi-select, saved). */
class PowerChoiceActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val c = CharacterRegistry.byId(intent.getStringExtra("id") ?: "devil") ?: return finish()
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Th.INK)
            addView(LinearLayout(this@PowerChoiceActivity).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16))
                addView(card(this@PowerChoiceActivity, c) { recreate() })
                addView(Button(this@PowerChoiceActivity).apply { text = "Done"; setTextColor(Th.WHITE); background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Th.RED) }; setOnClickListener { finish() } },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
            })
        })
        MaterialScreenPolish.apply(this, dark = true, preserveCustomButtons = true)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        fun card(a: Activity, c: Character, onReset: () -> Unit): View {
            val d = a.resources.displayMetrics.density; fun dp(v: Int) = (v * d).toInt()
            val st = AppSettings(a)
            val card = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Th.WHITE); setStroke(dp(2), Th.RED) }
            }
            card.addView(TextView(a).apply { text = "😈 ${c.name}'s powers"; textSize = 17f; setTypeface(typeface, Typeface.BOLD); setTextColor(Th.INK) })
            card.addView(TextView(a).apply {
                text = "Double-tap fires a random power from the ones switched on. The main power ★ is always on and comes first."
                textSize = 12.5f; setTextColor(Th.GREY); setPadding(0, dp(2), 0, dp(6))
            })
            val sig = c.signaturePower
            for (pw in PowerPrefs.available(c)) card.addView(Switch(a).apply {
                text = (if (pw == sig) "★ " else "") + pw.title; textSize = 14.5f; setTextColor(Th.INK)
                isChecked = PowerPrefs.isOn(c, pw); isEnabled = pw != sig; setPadding(0, dp(5), 0, dp(5))
                setOnCheckedChangeListener { _, on ->
                    val cur = (st.enabledPowers(c.id) ?: PowerPrefs.defaults(c)).toMutableSet()
                    if (on) cur.add(pw.id) else cur.remove(pw.id)
                    sig?.let { cur.add(it.id) }
                    st.setEnabledPowers(c.id, cur)
                }
            })
            card.addView(Button(a).apply { text = "↺  All powers on"; setOnClickListener { st.setEnabledPowers(c.id, null); onReset() } })
            return card
        }
    }
}
