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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
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
    private var advancedOpen by mutableStateOf(false)
    private var guideOpen by mutableStateOf(false)
    private var template by mutableStateOf<com.combo.monkeycompanion.engine.script.ShowTemplates.Template?>(null)
    // v10.39.5 "✨ Describe a show" (kept in prefs, so ▶ Preview, which closes this screen, doesn't lose it)
    private val draftPrefs by lazy { getSharedPreferences("shows_draft", MODE_PRIVATE) }
    private var describe by mutableStateOf("")
    private var change by mutableStateOf("")
    private var written by mutableStateOf("")
    private var writeNote by mutableStateOf("")
    private var writing by mutableStateOf(false)

    companion object {
        const val PICK = 4711
        /** v10.39.1: the plain-words guide on the Shows screen. */
        const val GUIDE = "A show is a short cartoon your characters act out on your screen.\n\n" +
            "1. Pick a story under \"Make a show\" (a chase, a duel, a surprise…).\n" +
            "2. Choose who plays each part, any of your characters, even ones you made in the Studio.\n" +
            "3. Type what they say. Emojis are fine.\n" +
            "4. Tap ▶ Play it now. Tap the screen any time to stop.\n" +
            "5. Like it? Save it. It appears in \"Your shows\" to play again.\n\n" +
            "Want more control? Open \"Open in the script editor\": every line is a time and what happens, for example\n" +
            "at 2: billa walk to 0.5 0.86 in 1.5   (x 0 = left … 1 = right, y 0.86 = the floor)\n" +
            "at 3: billa say \"Hello!\" for 2\n" +
            "Moves: enter, exit, walk, fly, hover, vanish, appear, say, face, do, look, turn, hold, drop, throw, ray."
        /** Play a show script on the overlay stage after this screen closes. Returns an error message or null. */
        fun play(ctx: Context, text: String): String? {
            val dm = ctx.resources.displayMetrics
            return ScriptShowHub.startResult(ctx.applicationContext, text = text, aspect = (dm.heightPixels.toFloat() / dm.widthPixels.coerceAtLeast(1)).coerceIn(1.2f, 3f))
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        describe = draftPrefs.getString("describe", "").orEmpty(); written = draftPrefs.getString("script", "").orEmpty()
        if (written.isNotBlank()) writeNote = "Your last written show is still here."
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
            DescribeCard()
            SCard("Make a show", "Pick a story, choose who plays, type what they say. That's it.") {
                for (t in com.combo.monkeycompanion.engine.script.ShowTemplates.all) {
                    androidx.compose.material3.OutlinedCard(onClick = { template = t }, modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.foundation.layout.Column(Modifier.padding(14.dp)) {
                            Text(t.title, style = MaterialTheme.typography.titleSmall)
                            SNote(t.blurb)
                        }
                    }
                }
            }
            SCard("How shows work") {
                androidx.compose.material3.TextButton(onClick = { guideOpen = !guideOpen }) { Text(if (guideOpen) "Hide the guide" else "Show the guide (2 minutes)") }
                if (guideOpen) SNote(GUIDE)
            }
            SCard("Advanced: write the script yourself") {
                androidx.compose.material3.TextButton(onClick = { advancedOpen = !advancedOpen }) { Text(if (advancedOpen) "Hide" else "Open the script editor, links and files") }
                if (advancedOpen) {
                    OutlinedTextField(
                        value = paste, onValueChange = { paste = it }, minLines = 5, modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                        placeholder = { Text("title: Wave hello\ncast: hero=monkey, pal=gajju\nat 0: hero enter left to 0.3 0.86 in 1.5\n…") }
                    )
                    val warns = UserShows.warnings(paste)
                    if (warns.isNotEmpty()) SNote("⚠ " + warns.joinToString("\n⚠ "))
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
        template?.let { TemplateSheet(it) }
    }

    /** v10.39.5: type the show in normal words; the brain writes it; Preview / Save / Change it. */
    @androidx.compose.runtime.Composable
    private fun DescribeCard() {
        SCard("✨ Describe a show", "Say what should happen in normal words. Your character's brain writes the show (Set-Tai Cloud, your own key or on-device Gemma). Each show uses 1 or 2 of the cloud's daily requests.") {
            OutlinedTextField(value = describe, onValueChange = { describe = it.take(400) }, minLines = 2, modifier = Modifier.fillMaxWidth(),
                label = { Text("What happens?") },
                placeholder = { Text("Tutty plays a prank on Angel, Angel gets angry, then they laugh together") })
            if (describe.isBlank()) {
                for (ex in listOf("Billa chases Chittu for the last laddu", "Tutty pranks Angel and Angel forgives her", "Zoom and Bolt race, Bolt's jets win")) {
                    androidx.compose.material3.AssistChip(onClick = { describe = ex }, label = { Text(ex) })
                }
            }
            if (writing) {
                androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                SNote("Writing your show…")
            } else SPrimary(if (written.isBlank()) "✨ Write my show" else "✨ Write a new one") { writeShow(describe) }
            if (writeNote.isNotBlank()) Text(writeNote, color = MaterialTheme.colorScheme.primary)
            if (written.isNotBlank() && !writing) {
                val title = Regex("(?m)^title:\\s*(.*)$").find(written)?.groupValues?.get(1).orEmpty()
                if (title.isNotBlank()) Text("“$title”", style = MaterialTheme.typography.titleMedium)
                SPrimary("▶ Preview") { playLater(written) }
                SSecondary("Save to my shows") { if (addText(written, playNow = false)) { written = ""; writeNote = ""; keepDraft() } }
                SField(change, { change = it.take(200) }, "Change it (for example: make Angel win)")
                SSecondary("Change it") {
                    if (change.isBlank()) say("Type what to change first") else { writeShow("$describe. Change: $change"); change = "" }
                }
                androidx.compose.material3.TextButton(onClick = { paste = written; advancedOpen = true }) { Text("Open in the script editor") }
            }
        }
    }

    private fun writeShow(text: String) {
        writing = true; writeNote = ""
        com.combo.monkeycompanion.ai.ChatHub.init(this)
        com.combo.monkeycompanion.ai.ChatHub.writeShow(text) { script, note ->
            writing = false; writeNote = note
            if (script != null) { written = script; keepDraft() }
        }
    }

    private fun keepDraft() { runCatching { draftPrefs.edit().putString("describe", describe).putString("script", written).apply() } }

    /** Fill in a template: characters as drop-downs, lines as text boxes. Preview plays it; Save keeps it. */
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @androidx.compose.runtime.Composable
    private fun TemplateSheet(t: com.combo.monkeycompanion.engine.script.ShowTemplates.Template) {
        val chars = com.combo.monkeycompanion.character.CharacterRegistry.allWithCustom
        val values = androidx.compose.runtime.remember(t.id) { androidx.compose.runtime.mutableStateMapOf<String, String>().apply { putAll(t.defaults()) } }
        var name by androidx.compose.runtime.remember(t.id) { mutableStateOf(t.title) }
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { template = null }) {
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                Text(t.title, style = MaterialTheme.typography.titleLarge)
                SNote(t.blurb)
                SField(name, { name = it }, "Show name")
                for (f in t.fields) {
                    if (f.kind == com.combo.monkeycompanion.engine.script.ShowTemplates.Kind.CHARACTER) {
                        val idx = chars.indexOfFirst { it.id == values[f.key] }.coerceAtLeast(0)
                        SChoice(f.label, chars.map { it.name }, idx) { values[f.key] = chars[it].id }
                    } else {
                        SField(values[f.key].orEmpty(), { values[f.key] = it }, f.label)
                    }
                }
                fun script() = t.render(values).replaceFirst(Regex("(?m)^title:.*$"), "title: " + name.ifBlank { t.title }.replace('"', '’'))
                SPrimary("▶ Play it now") { template = null; playLater(script()) }
                SSecondary("Save to my shows") { if (addText(script(), playNow = false)) template = null }
                androidx.compose.material3.TextButton(onClick = { paste = script(); advancedOpen = true; template = null }) { Text("Open in the script editor") }
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
