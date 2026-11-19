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
        val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (r.resultCode == Activity.RESULT_OK && !text.isNullOrEmpty()) ChatHub.send(text, voice = true)
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
            ChatHub.announce("This phone has no Google voice popup. Type to me instead! ⌨️")
            finish()
        }
    }
}
