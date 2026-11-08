package com.combo.monkeycompanion.fun5

import android.app.Activity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
 * v5 "Two phones" (v10.38.17: Material 3): both phones on the same Wi-Fi. One shows a 4-digit code, the other
 * types it, the first one taps Accept. Only character / animation events travel (wave, jump across, fight, dance).
 */
class PairActivity : androidx.activity.ComponentActivity(), PairHub.Listener {
    private var status by androidx.compose.runtime.mutableStateOf("Not paired.")
    private var found by androidx.compose.runtime.mutableStateOf<List<String>>(emptyList())
    private var paired by androidx.compose.runtime.mutableStateOf(false)
    private var askingCodeFor by androidx.compose.runtime.mutableStateOf<String?>(null)
    private var askPeer by androidx.compose.runtime.mutableStateOf<Pair<String, String>?>(null)
    private val myName get() = (Build.MODEL ?: "phone").take(24)
    private val myChar get() = CharacterRegistry.current.id

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        PairHub.add(this)
        refresh()
        setContent { com.combo.monkeycompanion.ui.SettaiTheme(this) { Screen() } }
    }

    override fun onDestroy() { PairHub.remove(this); super.onDestroy() }

    @androidx.compose.runtime.Composable
    private fun Screen() {
        com.combo.monkeycompanion.ui.SettaiScreen("Two phones", onBack = { finish() }) {
            com.combo.monkeycompanion.ui.SNote("Both phones on the same Wi-Fi, both with Set-Tai open here. One taps \"Show a code\", the other taps \"Find\" and types that code; then the first phone taps Accept. Only animation events are exchanged (wave, jump, fight, dance). No messages, no files, nothing else.")
            com.combo.monkeycompanion.ui.SCard(status) {
                com.combo.monkeycompanion.ui.SPrimary("Show a code (this phone waits)") {
                    val code = PairProtocol.newCode()
                    PairHub.host(applicationContext, myName, myChar, code)
                    status = "Code: $code. Type it on the other phone."
                }
                com.combo.monkeycompanion.ui.SSecondary("Find the other phone") { found = emptyList(); PairHub.discover(applicationContext) }
                for (n in found) com.combo.monkeycompanion.ui.SSecondary("Join \"$n\"") { askingCodeFor = n }
                com.combo.monkeycompanion.ui.SSecondary("Stop / unpair") { PairHub.stop(); refresh() }
            }
            if (paired) com.combo.monkeycompanion.ui.SCard("Make them play", "Set-Tai must be running on both phones.") {
                fun ev(cmd: String) = startService(Intent(this@PairActivity, MonkeyOverlayService::class.java).setAction(MonkeyOverlayService.ACTION_V5).putExtra("cmd", "pair:$cmd"))
                com.combo.monkeycompanion.ui.SSecondary("👋  Wave") { ev("wave") }
                com.combo.monkeycompanion.ui.SSecondary("⬅️  Jump across (other phone is on my LEFT)") { ev("jump:LEFT") }
                com.combo.monkeycompanion.ui.SSecondary("➡️  Jump across (other phone is on my RIGHT)") { ev("jump:RIGHT") }
                com.combo.monkeycompanion.ui.SSecondary("🥊  Silly fight") { ev("fight") }
                com.combo.monkeycompanion.ui.SSecondary("💃  Dance together") { ev("dance") }
            }
        }
        askingCodeFor?.let { name ->
            var code by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
            androidx.compose.material3.AlertDialog(onDismissRequest = { askingCodeFor = null },
                title = { androidx.compose.material3.Text("Code shown on \"$name\"") },
                text = {
                    androidx.compose.material3.OutlinedTextField(code, { code = it.filter(Char::isDigit).take(4) }, singleLine = true,
                        label = { androidx.compose.material3.Text("4-digit code") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { PairHub.join(myName, myChar, name, code); status = "Asking \"$name\"…"; askingCodeFor = null }) { androidx.compose.material3.Text("Join") } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { askingCodeFor = null }) { androidx.compose.material3.Text("Cancel") } })
        }
        askPeer?.let { (peerName, peerCharacter) ->
            androidx.compose.material3.AlertDialog(onDismissRequest = {},
                title = { androidx.compose.material3.Text("Pair with $peerName?") },
                text = { androidx.compose.material3.Text("$peerName ($peerCharacter) typed the right code. Accept to let your characters wave, jump across, fight and dance together.") },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { PairHub.answer(true); askPeer = null }) { androidx.compose.material3.Text("Accept") } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { PairHub.answer(false); status = "Rejected."; askPeer = null }) { androidx.compose.material3.Text("Reject") } })
        }
    }

    private fun refresh() {
        paired = PairHub.paired
        if (paired) status = "Paired with ${PairHub.peerName} (${PairHub.peerCharacter}) ✅"
    }

    override fun onStatus(text: String) { status = text }
    override fun onFound(names: List<String>) { found = names }
    override fun onAsk(peerName: String, peerCharacter: String) { askPeer = peerName to peerCharacter }
    override fun onPaired(peerName: String, peerCharacter: String) { refresh() }
    override fun onClosed() { status = "Not paired."; refresh() }
}
