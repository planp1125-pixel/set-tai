package com.combo.monkeycompanion.ai

import android.content.Context
import com.combo.monkeycompanion.ai.react.MoodKey
import com.combo.monkeycompanion.ai.react.ReactionEngine
import com.combo.monkeycompanion.ai.react.TouchFeel
import java.io.File

/**
 * v10.18 "Feel the touch" (Android side): one [ReactionEngine] per character, saved on the phone
 * (files/reactions/<character>.txt, never synced), talking to the brain through [ChatHub.reactRequest].
 * Only used when Give Brain is on; with Brain off the scripted lines play exactly as before.
 */
object ReactionHub {
    private val engines = HashMap<String, ReactionEngine>()

    private fun dir(c: Context) = File(c.filesDir, "reactions").apply { mkdirs() }
    private fun file(c: Context, id: String) = File(dir(c), id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".txt")

    @Synchronized fun engine(c: Context, id: String): ReactionEngine = engines.getOrPut(id) {
        ReactionEngine().apply { runCatching { file(c, id).takeIf { it.exists() }?.readText()?.let { load(it) } } }
    }

    private fun save(c: Context, id: String) {
        val e = synchronized(this) { engines[id] } ?: return
        runCatching { file(c, id).writeText(e.save()) }
    }

    /**
     * A touch: [show] gets the instant bank line now (if any), and later the AI's fresh thought (if this moment
     * deserves one and it arrives in time). [show] is always called on the main thread.
     */
    fun onTouch(c: Context, id: String, feel: TouchFeel, key: MoodKey, show: (String) -> Unit) {
        val app = c.applicationContext
        val e = engine(app, id)
        val d = e.onTouch(feel, key, System.currentTimeMillis())
        d.line?.let { show(it); save(app, id) }
        val req = d.thoughtRequest ?: return
        ChatHub.reactRequest(req, thought = true) { ans ->
            val line = e.acceptThought(d.thoughtToken, ans, System.currentTimeMillis()) ?: return@reactRequest
            show(line); save(app, id)
        }
    }

    /** Quiet moment: let the AI prepare lines for the coming touches (spaced out inside the engine). */
    fun maybeRefill(c: Context, id: String, key: MoodKey) {
        val app = c.applicationContext
        val e = engine(app, id)
        val req = e.refillRequest(key, System.currentTimeMillis()) ?: return
        ChatHub.reactRequest(req, thought = false) { ans ->
            val kept = e.acceptRefill(ans, System.currentTimeMillis())
            if (kept > 0) save(app, id)
            android.util.Log.d("ReactionHub", "refill for $id kept $kept lines")
        }
    }
}
