package com.combo.monkeycompanion.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import com.combo.monkeycompanion.engine.script.ScriptError
import com.combo.monkeycompanion.overlay.ScriptShowHub
import com.combo.monkeycompanion.overlay.UserShows

/**
 * v10.34 Shows (v10.38.17: Material 3). Play the bundled shows and your own; add a show by pasting the script,
 * picking a .show / .txt file (no storage permission), or an https link. A bad script shows its line number
 * and is not saved.
 */
class ShowsActivity : androidx.activity.ComponentActivity() {
    private val ui = Handler(Looper.getMainLooper())
    /** Tests: the last message shown (errors include the line number). */
    var lastMessage: String? = null; private set
    private var status by mutableStateOf("")
    private var paste by mutableStateOf("")
    private var url by mutableStateOf("")
    private var rev by mutableIntStateOf(0)

    companion object {
        const val PICK = 4711
        /** Play a show script on the overlay stage after this screen closes. Returns an error message or null. */
        fun play(ctx: Context, text: String): String? {
            val dm = ctx.resources.displayMetrics
            return ScriptShowHub.startResult(ctx.applicationContext, text = text, aspect = (dm.heightPixels.toFloat() / dm.widthPixels.coerceAtLeast(1)).coerceIn(1.2f, 3f))
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent { SettaiTheme(this) { Screen(rev) } }
    }

    @androidx.compose.runtime.Composable
    private fun Screen(@Suppress("UNUSED_PARAMETER") r: Int) {
        SettaiScreen("Shows", onBack = { finish() }, subtitle = "Tap ▶ to play. Tap the screen during a show to stop it.", rev = r) {
            SCard("Your shows") {
                for (e in UserShows.all(this@ShowsActivity)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text((if (e.bundled) "⭐ " else "📝 ") + e.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            val m = runCatching { e.text(this@ShowsActivity) }.getOrNull()
                            if (m == null) say("Couldn't read ${e.title}") else playLater(m)
                        }) { Text("▶ Play") }
                        if (!e.bundled) TextButton(onClick = { UserShows.delete(e); say("Deleted ${e.title}"); rev++ }) { Text("Delete") }
                    }
                }
                if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.primary)
            }
            SCard("Add a show", "Paste the whole script, fetch a link, or pick a file.") {
                OutlinedTextField(
                    value = paste, onValueChange = { paste = it }, minLines = 5, modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    placeholder = { Text("title: Wave hello\ncast: hero=monkey, pal=gajju\nat 0: hero enter left to 0.3 0.86 in 1.5\n…") }
                )
                SPrimary("Save & play") { addText(paste, playNow = true) }
                SSecondary("Save") { addText(paste, playNow = false) }
                OutlinedTextField(value = url, onValueChange = { url = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Link (GitHub or any https text file)") },
                    placeholder = { Text("https://github.com/you/repo/blob/main/my.show") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                SSecondary("Fetch link") { fetch(url) }
                SSecondary("Pick a .show / .txt file") {
                    runCatching {
                        @Suppress("DEPRECATION")
                        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                            .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/*", "application/octet-stream")), PICK)
                    }.onFailure { say("No file picker on this phone") }
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(req, res, data)
        if (req != PICK || res != RESULT_OK) return
        val uri = data?.data ?: return
        val t = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes().take(200_000).toByteArray().toString(Charsets.UTF_8) } }.getOrNull()
        if (t == null) { say("Couldn't read that file"); return }
        paste = t
        addText(t, playNow = false)
    }

    /** Saves [t] (if it parses) and optionally plays it. Public for tests. */
    fun addText(t: String, playNow: Boolean): Boolean {
        if (t.isBlank()) { say("Paste a script first"); return false }
        val e = try { UserShows.save(this, t) } catch (x: ScriptError) { say(UserShows.message(x)); return false }
        say("Saved “${e.title}” ✅"); rev++
        if (playNow) playLater(t)
        return true
    }

    private fun fetch(link: String) {
        if (!link.trim().startsWith("https://")) { say("Use an https:// link"); return }
        say("Fetching…")
        Thread {
            val r = runCatching { UserShows.fetch(link) }
            ui.post {
                r.onSuccess { t -> paste = t; addText(t, playNow = false) }
                    .onFailure { say("Couldn't fetch: ${it.message}") }
            }
        }.start()
    }

    private fun playLater(text: String) {
        UserShows.check(text)?.let { say(it); return }
        val app = applicationContext
        finish()
        ui.postDelayed({ play(app, text)?.let { Toast.makeText(app, it, Toast.LENGTH_LONG).show() } }, 350L)
    }

    private fun say(m: String) {
        lastMessage = m
        status = m
        runCatching { Toast.makeText(this, m, Toast.LENGTH_LONG).show() }
    }
}
