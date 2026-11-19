package com.combo.monkeycompanion.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.combo.monkeycompanion.ai.ChatHub

/**
 * v10.50.1 fallback ears: Google's own voice popup (the one keyboards use). It runs as a normal screen, so it
 * works even when a phone won't let an app that floats over others listen. What it hears goes to the chat.
 */
class GoogleVoiceActivity : ComponentActivity() {
    private val ask = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()?.takeIf { r.resultCode == Activity.RESULT_OK && it.isNotEmpty() }
        // v10.50.2: the overlay that opened us handles the words (voice chat turns); else straight to the chat
        val cb = onResult; onResult = null
        if (cb != null) cb(text) else if (text != null) ChatHub.send(text, voice = true)
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to ${com.combo.monkeycompanion.character.CharacterRegistry.current.name}")
        runCatching { ask.launch(i) }.onFailure {
            val cb = onResult; onResult = null
            if (cb != null) cb(null) else ChatHub.announce("This phone has no Google voice popup. Type to me instead! ⌨️")
            finish()
        }
    }

    companion object {
        /** v10.50.2: who gets the words (null = cancelled / nothing said). Set just before starting this screen. */
        @Volatile var onResult: ((String?) -> Unit)? = null

        /** True when Google's (or any) voice popup is on the phone. */
        fun available(ctx: android.content.Context): Boolean = runCatching {
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(ctx.packageManager) != null
        }.getOrDefault(false)
    }
}
