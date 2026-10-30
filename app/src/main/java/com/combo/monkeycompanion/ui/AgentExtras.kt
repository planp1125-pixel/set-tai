package com.combo.monkeycompanion.ui

import android.annotation.SuppressLint
import android.app.Activity
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
    private val brown = Color.rgb(0x5A, 0x36, 0x18)
    private val soft = Color.rgb(0x8A, 0x63, 0x3E)

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

