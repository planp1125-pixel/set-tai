package com.combo.monkeycompanion.fun5

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.overlay.MonkeyOverlayService
import com.combo.monkeycompanion.v5.PairProtocol

/**
 * v5 "Two phones": both phones on the same Wi-Fi. One shows a 4-digit code, the other types it,
 * the first one taps Accept. Only character/animation events travel (wave, jump across, fight, dance).
 */
class PairActivity : Activity(), PairHub.Listener {
    private val brown = Color.rgb(0x5A, 0x3A, 0x1E)
    private lateinit var status: TextView
    private lateinit var foundBox: LinearLayout
    private lateinit var events: LinearLayout
    private val d get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()
    private val myName get() = (Build.MODEL ?: "phone").take(24)
    private val myChar get() = CharacterRegistry.current.id

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        title = "Two phones"
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(24)) }
        col.addView(text("Two Settais, two phones 🐒🤝🐒", 22f, true))
        col.addView(text("Both phones on the same Wi-Fi, both with Settai v5 open here. One taps \"Show a code\", the other taps \"Find\" and types that code; then the first phone taps Accept. Only animation events are exchanged (wave, jump, fight, dance) — no messages, no files, nothing else.", 13f))
        status = text("Not paired.", 15f, true)
        col.addView(status)
        col.addView(button("📡  Show a code (this phone waits)") {
            val code = PairProtocol.newCode()
            PairHub.host(applicationContext, myName, myChar, code)
            status.text = "Code: $code — type it on the other phone."
        })
        col.addView(button("🔎  Find the other phone") { foundBox.removeAllViews(); PairHub.discover(applicationContext) })
        foundBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(foundBox)
        events = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(events)
        col.addView(button("Stop / unpair") { PairHub.stop(); refresh() })
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(0xFF, 0xF4, 0xE0)); addView(col)
            setOnApplyWindowInsetsListener { v, i ->
                @Suppress("DEPRECATION") v.setPadding(i.systemWindowInsetLeft, i.systemWindowInsetTop, i.systemWindowInsetRight, i.systemWindowInsetBottom); i
            }
        })
        PairHub.add(this)
        refresh()
    }

    override fun onDestroy() { PairHub.remove(this); super.onDestroy() }

    private fun refresh() {
        events.removeAllViews()
        if (!PairHub.paired) return
        status.text = "Paired with ${PairHub.peerName} (${PairHub.peerCharacter}) ✅"
        events.addView(text("Make them play (Settai must be running on both):", 13f))
        fun ev(label: String, cmd: String) = events.addView(button(label) {
            startService(Intent(this, MonkeyOverlayService::class.java).setAction(MonkeyOverlayService.ACTION_V5).putExtra("cmd", "pair:$cmd"))
        })
        ev("👋  Wave", "wave")
        ev("⬅️  Jump across (other phone is on my LEFT)", "jump:LEFT")
        ev("➡️  Jump across (other phone is on my RIGHT)", "jump:RIGHT")
        ev("🥊  Silly fight", "fight")
        ev("💃  Dance together", "dance")
    }

    override fun onStatus(text: String) { status.text = text }
    override fun onFound(names: List<String>) {
        foundBox.removeAllViews()
        for (n in names) foundBox.addView(button("Join \"$n\"") { askCode(n) })
    }
    private fun askCode(name: String) {
        val et = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; hint = "4-digit code" }
        AlertDialog.Builder(this).setTitle("Code shown on \"$name\"").setView(et)
            .setPositiveButton("Join") { _, _ -> PairHub.join(myName, myChar, name, et.text.toString()); status.text = "Asking \"$name\"…" }
            .setNegativeButton("Cancel", null).show()
    }
    override fun onAsk(peerName: String, peerCharacter: String) {
        AlertDialog.Builder(this).setTitle("Pair with $peerName?")
            .setMessage("$peerName ($peerCharacter) typed the right code. Accept to let your Settais wave, jump across, fight and dance together.")
            .setPositiveButton("Accept") { _, _ -> PairHub.answer(true) }
            .setNegativeButton("Reject") { _, _ -> PairHub.answer(false); status.text = "Rejected." }
            .setCancelable(false).show()
    }
    override fun onPaired(peerName: String, peerCharacter: String) { refresh() }
    override fun onClosed() { status.text = "Not paired."; refresh() }

    private fun text(t: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(brown); if (bold) setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(6), 0, dp(6))
    }
    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.rgb(0xC9, 0x7B, 0x2E)) }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
    }
}
