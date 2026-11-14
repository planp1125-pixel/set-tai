package com.combo.monkeycompanion.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Switch
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.combo.monkeycompanion.ai.BrainType
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.ai.ModelStore
import com.combo.monkeycompanion.powers.ActionLog
import com.combo.monkeycompanion.powers.CapGroup
import com.combo.monkeycompanion.powers.Capabilities
import com.combo.monkeycompanion.ai.Provider
import java.io.File
import java.util.Locale

/**
 * Brain & settings (v10.38.16: one Material 3 screen). Same settings and logic as before, grouped in cards.
 * Opened with "settai_settings_section" it shows just that topic (brain, touch, personality, buddy, habits,
 * superpowers, phone, appearance, character).
 */
@SuppressLint("SetTextI18n")
class SettingsActivity : androidx.activity.ComponentActivity() {
    /** v10.38.24: texts use the main character's name instead of "Settai". */
    // v10.39.2: settings work for every character, so the screens say "your character", not one name
    private val nm: String get() = "your character"
    private val NM: String get() = "Your character"


    private val s get() = ChatHub.settings
    private val ui = Handler(Looper.getMainLooper())

    /** Bumped after any change so the screen re-reads the settings. */
    private var rev by mutableIntStateOf(0)
    private fun bump() { rev++ }

    // live texts
    private var onlineResult by mutableStateOf("")
    private var offlineResult by mutableStateOf("")
    private var dlText by mutableStateOf("")
    private var dlFrac by mutableStateOf<Float?>(null)
    private var energyText by mutableStateOf("Measuring…")
    private var clearedText by mutableStateOf("")
    private var powerBlurb by mutableStateOf("")
    private var modelChoices by mutableStateOf<Pair<Provider, List<String>>?>(null)
    private var showLog by mutableStateOf(false)

    // own-key form
    private var shownProvider by mutableStateOf(Provider.OPENAI)
    private var keyText by mutableStateOf("")
    private var modelText by mutableStateOf("")
    private var urlText by mutableStateOf("")
    private var tokenText by mutableStateOf("")
    private var catalogPick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChatHub.init(this)
        shownProvider = s.provider
        loadOnline()
        catalogPick = ModelStore.CATALOG.indexOfFirst { File(s.offlineModelPath).name == it.file }.coerceAtLeast(0)
        val target = intent.getStringExtra("settai_settings_section")
        setContent { SettaiTheme(this) { SettingsScreen(target, rev) } }
    }

    override fun onResume() { super.onResume(); pollDownload(); energyWindow.clear(); refreshEnergy(); bump() }
    override fun onPause() { super.onPause(); ui.removeCallbacksAndMessages(null); saveOnline() }

    private val titles = mapOf("brain" to "Brain & AI", "touch" to "Touch & motion", "personality" to "Personality & memory",
        "buddy" to "Buddy & moments", "habits" to "Daily habits", "superpowers" to "Superpowers", "phone" to "Phone powers",
        "appearance" to "Appearance", "character" to "Character")

    private fun visible(target: String?, id: String) = when (target) {
        null -> true
        "brain" -> id in listOf("brainType", "picker", "brain", "offline", "background")
        "touch" -> id == "rest"
        "personality" -> id == "feel"
        "habits" -> id in listOf("engagement", "fun")
        else -> id == target
    }

    @Composable
    private fun SettingsScreen(target: String?, @Suppress("UNUSED_PARAMETER") r: Int) {
        SettaiScreen(title = titles[target] ?: "Settings", onBack = { finish() }, rev = r,
            subtitle = "Changes apply right away") {
            if (visible(target, "brainType")) BrainPickerCard(r)
            if (visible(target, "character")) CharacterCard(r)
            if (visible(target, "brain") && s.brainType == BrainType.ONLINE) OnlineCard(r)
            if (visible(target, "offline") && s.brainType == BrainType.OFFLINE) OfflineCard(r)
            if (visible(target, "feel")) PersonalityCard(r)
            if (visible(target, "background")) BackgroundAiCard(r)
            if (visible(target, "buddy")) BuddyCard(r)
            if (visible(target, "appearance")) AppearanceCard()
            if (visible(target, "rest")) RestCard(r)
            if (visible(target, "superpowers")) SuperpowersCard(r)
            if (visible(target, "phone")) PhonePowersCard(r)
            if (visible(target, "fun")) Fun5Card(r)
            if (visible(target, "engagement")) EngagementCard(r)
            Spacer(Modifier.height(24.dp))
        }
        modelChoices?.let { (pr, ids) ->
            val best = com.combo.monkeycompanion.ai.ModelList.pickDefault(ids)
            AlertDialog(onDismissRequest = { modelChoices = null }, title = { Text("Models for ${pr.label}") },
                text = {
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        ids.forEach { id ->
                            TextButton(onClick = { s.setModelFor(pr, id); modelText = id; onlineResult = "Model set: $id"; modelChoices = null; bump() },
                                modifier = Modifier.fillMaxWidth()) { Text(if (id == best) "$id  ⭐ recommended" else id, modifier = Modifier.fillMaxWidth()) }
                        }
                    }
                },
                confirmButton = {}, dismissButton = { TextButton(onClick = { modelChoices = null }) { Text("Cancel") } })
        }
        if (showLog) {
            AlertDialog(onDismissRequest = { showLog = false }, title = { Text("Recent actions") },
                text = { Text(ActionLog.format(this).ifBlank { "Nothing yet." }) },
                confirmButton = { TextButton(onClick = { showLog = false }) { Text("OK") } },
                dismissButton = { TextButton(onClick = { ActionLog.clear(this); showLog = false }) { Text("Clear") } })
        }
    }

    // ---------------- brain ----------------

    private fun activate(bt: BrainType) {
        s.brainType = bt
        ChatHub.setBrainEnabled(true)
        if (bt == BrainType.OFFLINE) ChatHub.preloadOffline()
        bump()
    }

    /** v10.38.24: ONE brain list. Tapping a row switches everything together; the matching details show below. */
    @Composable
    private fun BrainPickerCard(@Suppress("UNUSED_PARAMETER") r: Int) {
        val on = s.aiBrainEnabled && s.brainType != BrainType.NONE
        val now = when {
            !on -> "Off: canned lines only"
            s.brainType == BrainType.OFFLINE -> "On-device: " + (File(s.offlineModelPath).name.ifBlank { "no model yet (download one below)" })
            s.useOwnApiKey -> "${s.provider.label} with your own key (${s.modelFor(s.provider)})"
            else -> "Set-Tai Cloud (built in, no key)"
        }
        SCard("Brain", "Pick one. Jokes, dance and phone commands always work on your phone, even with the brain off.") {
            Text("Using: $now  ✓", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            SRadio("☁️ Set-Tai Cloud (recommended)", on && s.brainType == BrainType.ONLINE && !s.useOwnApiKey,
                "Ready now, no key or sign-in. 40 requests a day.") { s.useOwnApiKey = false; activate(BrainType.ONLINE) }
            for (f in ModelStore.installed(this@SettingsActivity)) {
                val title = ModelStore.CATALOG.firstOrNull { it.file == f.name }?.title?.substringBefore(" (") ?: f.name
                SRadio("📱 $title (on this phone)", on && s.brainType == BrainType.OFFLINE && s.offlineModelPath == f.path,
                    "${mb(f.length())} · private, works offline") { s.offlineModelPath = f.path; activate(BrainType.OFFLINE) }
            }
            for (pr in Provider.entries) {
                val ok = if (pr == Provider.CUSTOM) s.customBaseUrl.isNotBlank() else s.apiKeyFor(pr).isNotBlank()
                if (!ok) continue
                SRadio("🔑 ${pr.label} (your key)", on && s.brainType == BrainType.ONLINE && s.useOwnApiKey && s.provider == pr, s.modelFor(pr)) {
                    s.useOwnApiKey = true; s.provider = pr; shownProvider = pr; loadOnline(); activate(BrainType.ONLINE)
                }
            }
            SRadio("🚫 Off", !on, "Canned lines only, no AI.") { s.brainType = BrainType.NONE; ChatHub.setBrainEnabled(false); bump() }
            SSubhead("Add another brain")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = { s.brainType = BrainType.OFFLINE; ChatHub.setBrainEnabled(true); bump() },
                    modifier = Modifier.weight(1f)) { Text("📱 On-device model") }
                androidx.compose.material3.OutlinedButton(onClick = { s.useOwnApiKey = true; s.brainType = BrainType.ONLINE; ChatHub.setBrainEnabled(true); bump() },
                    modifier = Modifier.weight(1f)) { Text("🔑 My own key") }
            }
        }
    }

    private fun loadOnline() {
        val pr = shownProvider
        keyText = ""
        modelText = s.modelFor(pr)
        urlText = s.customBaseUrl
        tokenText = ""
    }

    private fun saveOnline() {
        val pr = shownProvider
        val k = keyText.trim()
        if (k.isNotEmpty()) { s.setApiKeyFor(pr, k); keyText = "" }
        s.setModelFor(pr, modelText)
        if (pr == Provider.CUSTOM) s.customBaseUrl = urlText
    }

    @Composable
    private fun OnlineCard(@Suppress("UNUSED_PARAMETER") r: Int) {
        if (!s.useOwnApiKey) {
            SCard("Set-Tai Cloud", "Built in. No key or Gmail needed. 40 requests per phone per day (the count restarts at 5:30 AM India time). Local commands cost nothing. Your messages and character context go to Gemini; the gateway keeps only a daily count.") {
                ChatHub.cloudLeftToday()?.let { SNote("Left today: $it of 40") }
                SSwitch("Cloud also writes the automatic lines", s.cloudAutoLines,
                    "Touch reactions, falls, idle thoughts and quips. Off = only your own chats use the 40, and ${nm} uses built-in lines for the rest. On = more surprising lines, but the 40 can run out in a few minutes of play.") { s.cloudAutoLines = it; bump() }
                SSecondary("Test the brain") { runTest { onlineResult = it } }
                if (onlineResult.isNotBlank()) Text(onlineResult)
            }
            return
        }
        SCard("Your own API key", "Advanced. Your key is stored encrypted on this phone and used instead of Set-Tai Cloud.") {
            val providers = Provider.entries
            SChoice("Provider", providers.map { it.label }, providers.indexOf(shownProvider).coerceAtLeast(0)) { pos ->
                saveOnline(); shownProvider = providers[pos]; s.provider = shownProvider; loadOnline(); bump()
            }
            val saved = s.apiKeyFor(shownProvider)
            SField(keyText, { keyText = it }, "API key",
                placeholder = if (saved.isNotEmpty()) "Saved ••••${saved.takeLast(4)} (type to replace)" else "Paste API key", secret = true)
            SNote("Get a key: ${shownProvider.keyHint}")
            SField(modelText, { modelText = it }, "Model", placeholder = shownProvider.defaultModel.ifEmpty { "e.g. llama3.1" })
            if (shownProvider == Provider.CUSTOM) SField(urlText, { urlText = it }, "Server base URL", placeholder = "https://my-server/v1")
            SSecondary("Pick model from my account") { saveOnline(); pickModel(auto = false) }
            SPrimary("Save & test") { saveOnline(); s.provider = shownProvider; pickModel(auto = true); bump() }
            if (onlineResult.isNotBlank()) Text(onlineResult)
            androidx.compose.material3.TextButton(onClick = { saveOnline(); s.useOwnApiKey = false; bump() }) { Text("← Back to Set-Tai Cloud") }
        }
    }

    /** v10.13.2: the provider's live model list (only chat models; Gemini: those supporting generateContent). Blocking. */
    private fun fetchModels(pr: Provider): List<String> {
        val key = s.apiKeyFor(pr)
        val b = okhttp3.Request.Builder()
        when (pr) {
            Provider.GEMINI -> b.url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000").header("x-goog-api-key", key)
            Provider.ANTHROPIC -> b.url("https://api.anthropic.com/v1/models?limit=100").header("x-api-key", key).header("anthropic-version", "2023-06-01")
            else -> {
                val base = (if (pr == Provider.CUSTOM) s.customBaseUrl else pr.baseUrl).trimEnd('/')
                b.url("$base/models"); if (key.isNotBlank()) b.header("Authorization", "Bearer $key")
            }
        }
        com.combo.monkeycompanion.ai.Http.client.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) throw com.combo.monkeycompanion.ai.Http.fail(r)
            val body = r.body?.string().orEmpty()
            val ids = if (pr == Provider.GEMINI) com.combo.monkeycompanion.ai.ModelList.parseGemini(body) else com.combo.monkeycompanion.ai.ModelList.parseOpenAi(body)
            return com.combo.monkeycompanion.ai.ModelList.sortForChat(ids)
        }
    }

    /** Fetch the list; [auto] = keep a valid model or switch to the newest flash, then test. Otherwise let the user pick. */
    private fun pickModel(auto: Boolean) {
        val pr = shownProvider
        if (pr != Provider.CUSTOM && s.apiKeyFor(pr).isBlank()) { onlineResult = "Paste your API key first."; return }
        onlineResult = "Checking which models your key can use…"
        Thread {
            val res = runCatching { fetchModels(pr) }
            ui.post {
                val ids = res.getOrNull().orEmpty()
                if (auto) {
                    val cur = s.modelFor(pr)
                    if (ids.isNotEmpty() && cur !in ids) com.combo.monkeycompanion.ai.ModelList.pickDefault(ids)?.let { s.setModelFor(pr, it); modelText = it }
                    runTest { onlineResult = it }
                    return@post
                }
                if (ids.isEmpty()) { onlineResult = "❌ " + (res.exceptionOrNull()?.message ?: "No chat models found for this key."); return@post }
                onlineResult = "${ids.size} models available."
                modelChoices = pr to ids
            }
        }.start()
    }

    private fun runTest(out: (String) -> Unit) {
        out("Testing… (an on-device model can take 10-60 s to load the first time)")
        val b = ChatHub.brain()
        val t0 = android.os.SystemClock.elapsedRealtime()
        ChatHub.test(b) { r ->
            r.onSuccess {
                val secs = (android.os.SystemClock.elapsedRealtime() - t0) / 1000f
                val ob = b as? com.combo.monkeycompanion.ai.OfflineBrain
                val where = ob?.loadedOn?.let { " · ran on $it" } ?: ""
                val fell = if (ob != null && s.offlineUseGpu && ob.loadedOn == "CPU")
                    "\n(GPU didn't work on this phone, so it used the CPU: ${ob.gpuError ?: "unknown reason"})" else ""
                out("✅ ${b.label} says: $it\n(${"%.1f".format(secs)} s$where)$fell")
            }
            r.onFailure { out("❌ ${it.message}") }
        }
    }

    // ---------------- offline ----------------

    @Composable
    private fun OfflineCard(@Suppress("UNUSED_PARAMETER") r: Int) {
        SCard("On-device brain") {
            val path = s.offlineModelPath
            val f = File(path)
            Text(when {
                path.isBlank() -> "No model chosen yet."
                ModelStore.isDownloading(this@SettingsActivity) -> "Downloading ${f.name}…"
                f.exists() -> "Using: ${f.name} (${mb(f.length())})"
                else -> "Chosen model file is missing: ${f.name}"
            })
            SSubhead("Download a model")
            ModelStore.CATALOG.forEachIndexed { i, m ->
                val inUse = File(s.offlineModelPath).name == m.file
                SRadio(m.title + if (inUse) "  ✅ in use" else "", catalogPick == i, m.blurb) {
                    catalogPick = i
                    val inst = ModelStore.installed(this@SettingsActivity).firstOrNull { it.name == m.file }
                    if (inst != null && inst.path != s.offlineModelPath && !ModelStore.isDownloading(this@SettingsActivity)) {
                        s.offlineModelPath = inst.path; ChatHub.preloadOffline()
                    } else if (inst == null) dlText = "${m.title} isn't on this phone yet. Tap Download. Still using: ${File(s.offlineModelPath).name.ifBlank { "none" }}"
                    bump()
                }
            }
            SNote("Gated models: open the model's Hugging Face page, accept the license, then create a Read token at huggingface.co/settings/tokens.")
            val tk = s.hfToken
            SField(tokenText, { tokenText = it }, "Hugging Face token (only for gated models)",
                placeholder = if (tk.isNotEmpty()) "Saved ••••${tk.takeLast(4)} (type to replace)" else null, secret = true)
            SPrimary("Download selected model") { startDownload() }
            SSecondary("Open model page on Hugging Face") {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ModelStore.CATALOG[catalogPick].pageUrl)))
            }
            dlFrac?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
            if (dlText.isNotBlank()) Text(dlText, style = MaterialTheme.typography.bodySmall)
            if (dlFrac != null && ModelStore.isDownloading(this@SettingsActivity)) SSecondary("Cancel download") {
                ModelStore.cancelDownload(this@SettingsActivity); dlText = "Download cancelled."; dlFrac = null
            }
            SSecondary("Import a model file (.litertlm) from storage") { pickFile() }
            SSubhead("Models on this phone")
            val files = ModelStore.installed(this@SettingsActivity)
            val free = ModelStore.freeBytes(this@SettingsActivity)
            if (files.isEmpty()) SNote("None yet. Free space: ${mb(free)}")
            for (file in files) {
                val active = file.path == path
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((if (active) "✅ " else "") + "${file.name}\n${mb(file.length())}", modifier = Modifier.weight(1f))
                    if (!active) TextButton(onClick = { s.offlineModelPath = file.path; ChatHub.preloadOffline(); bump() }) { Text("Use") }
                    TextButton(onClick = { if (active) s.offlineModelPath = ""; file.delete(); bump() }) { Text("Delete") }
                }
            }
            if (files.isNotEmpty()) SNote("Free space: ${mb(free)}")
            SSwitch("Use GPU", s.offlineUseGpu, "Faster replies, but a big model can make the whole phone lag: turn off if it does.") { s.offlineUseGpu = it; bump() }
            SSwitch("Let the on-device brain use phone powers", s.offlineTools, "Tags like [alarm 07:00].") { s.offlineTools = it; bump() }
            SSecondary("Test on-device brain") { runTest { offlineResult = it } }
            if (offlineResult.isNotBlank()) Text(offlineResult)
        }
    }

    private fun mb(b: Long) = if (b >= 1_000_000_000) String.format(Locale.US, "%.2f GB", b / 1e9) else String.format(Locale.US, "%.0f MB", b / 1e6)

    private fun startDownload() {
        val m = ModelStore.CATALOG[catalogPick]
        val typed = tokenText.trim()
        if (typed.isNotEmpty()) { s.hfToken = typed; tokenText = "" }
        val token = s.hfToken
        if (m.gated && token.isBlank()) { dlText = "This model needs a Hugging Face token: accept the license on its page, then paste a Read token above."; return }
        if (ModelStore.isDownloading(this)) { dlText = "A download is already running."; return }
        dlText = "Checking…"
        Thread {
            try {
                val probe = ModelStore.probe(m.url, token)
                val need = if (probe.sizeBytes > 0) probe.sizeBytes else m.approxMb * 1_000_000L
                val free = ModelStore.freeBytes(this)
                if (free < need + 300_000_000L) { ui.post { dlText = "Not enough space: needs ${mb(need)}, only ${mb(free)} free." }; return@Thread }
                val target = ModelStore.startDownload(this, probe, m.file, m.title)
                s.offlineModelPath = target.path
                ui.post { dlText = "Download started (${mb(need)}). You can leave this screen; it continues in the background."; pollDownload() }
            } catch (e: Exception) {
                ui.post { dlText = "❌ ${e.message}" }
            }
        }.start()
    }

    private fun pollDownload() {
        val p = ModelStore.progress(this) ?: run { dlFrac = null; return }
        when (p.status) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                dlFrac = null; dlText = "✅ Download complete!"
                ModelStore.clearDownloadRecord(this)
                s.brainType = BrainType.OFFLINE; ChatHub.setBrainEnabled(true)
                ChatHub.preloadOffline(); bump()
                return
            }
            DownloadManager.STATUS_FAILED -> {
                dlFrac = null
                dlText = "❌ Download failed (code ${p.reason}). Check your connection and space, then try again."
                ModelStore.clearDownloadRecord(this)
                return
            }
            else -> {
                val frac = if (p.total > 0) p.done.toDouble() / p.total else 0.0
                dlFrac = frac.toFloat()
                val state = when (p.status) {
                    DownloadManager.STATUS_PAUSED -> "Paused (waiting for network)"
                    DownloadManager.STATUS_PENDING -> "Waiting"
                    else -> "Downloading"
                }
                dlText = "$state: ${mb(p.done)} / ${if (p.total > 0) mb(p.total) else "?"} (${(frac * 100).toInt()}%)"
            }
        }
        ui.postDelayed({ pollDownload() }, 1000)
    }

    private fun pickFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        @Suppress("DEPRECATION")
        startActivityForResult(i, REQ_PICK)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        var name = "imported.litertlm"; var size = -1L
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
            }
        }
        if (!name.endsWith(".litertlm")) dlText = "⚠ \"$name\" isn't a .litertlm file. This app runs LiteRT-LM models (.litertlm); it may not load."
        val free = ModelStore.freeBytes(this)
        if (size > 0 && free < size + 200_000_000L) { dlText = "Not enough space to import (${mb(size)} needed, ${mb(free)} free)."; return }
        val target = File(ModelStore.modelsDir(this), name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        dlFrac = 0f
        Thread {
            try {
                val part = File(target.path + ".part")
                contentResolver.openInputStream(uri)!!.use { inp ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(1 shl 20)
                        var done = 0L; var last = 0L
                        while (true) {
                            val n = inp.read(buf); if (n < 0) break
                            out.write(buf, 0, n); done += n
                            if (done - last > 20_000_000) {
                                last = done
                                val dd = done
                                ui.post {
                                    dlText = "Importing ${mb(dd)}${if (size > 0) " / " + mb(size) else ""}…"
                                    if (size > 0) dlFrac = dd.toFloat() / size
                                }
                            }
                        }
                    }
                }
                target.delete(); part.renameTo(target)
                s.offlineModelPath = target.path
                ui.post { dlFrac = null; dlText = "✅ Imported ${target.name}"; ChatHub.preloadOffline(); bump() }
            } catch (e: Exception) {
                ui.post { dlFrac = null; dlText = "❌ Import failed: ${e.message}" }
            }
        }.start()
    }

    // ---------------- rest, battery & energy ----------------

    private val energyWindow = ArrayDeque<com.combo.monkeycompanion.overlay.EnergyMeter.Sample>()

    private fun refreshEnergy() {
        val em = com.combo.monkeycompanion.overlay.EnergyMeter
        val now = em.sample()
        energyWindow.addLast(now)
        while (energyWindow.size > 1 && now.wallMs - energyWindow.first().wallMs > 10_000L) energyWindow.removeFirst()
        val first = energyWindow.first()
        energyText = em.describe(if (now.wallMs - first.wallMs >= 1500L) first to now else null, now) +
            "\nMind CPU: " + com.combo.monkeycompanion.mind.MindHub.cpuSummary() +
            "\n(Estimate: ~200 mA per fully busy CPU core; screen and GPU compositing not included. The window includes this Settings screen.)"
        ui.postDelayed({ refreshEnergy() }, 2000L)
    }

    /** v10.17.1: System / Light / Dark app theme (the overlay character is never affected). */
    @Composable
    private fun AppearanceCard() {
        val T = com.combo.monkeycompanion.theme.ThemeSkin
        val modes = listOf(T.SYSTEM, T.LIGHT, T.DARK)
        SCard("Appearance", "App screens only. Your characters on the phone screen never change.") {
            SChoice(null, listOf("Follow phone (System)", "Light", "Dark"), modes.indexOf(T.mode(this@SettingsActivity)).coerceAtLeast(0)) {
                if (modes[it] != T.mode(this@SettingsActivity)) { T.setMode(this@SettingsActivity, modes[it]); recreate() }
            }
        }
    }

    @Composable
    private fun RestCard(@Suppress("UNUSED_PARAMETER") r: Int) {
        val secs = listOf(30, 60, 120, 300, 600, 0)
        val names = listOf("After 30 seconds", "After 1 minute", "After 2 minutes", "After 5 minutes", "After 10 minutes", "Never")
        SCard("Touch, motion & battery", "Auto-rest: if you don't play with ${nm} for a while, it hides behind the nearest edge (just a tab). Tap the tab to call it back, double-tap to chat. It also pops out for low battery, overheating and timers.") {
            SChoice("Auto-rest", names, secs.indexOf(s.restSeconds).let { if (it < 0) 2 else it }) { s.restSeconds = secs[it]; bump() }
            SSwitch("Come back out after I unlock the phone", s.comeOutOnUnlock) { s.comeOutOnUnlock = it; bump() }
            SSwitch("Battery saver", s.batterySaver, "Lower frame rate, fewer random moves and quips, rests within 1 minute.") { s.batterySaver = it; bump() }
            SSwitch("Let ${nm} roam", s.roam, "Climb the sides, hang from the top, drop down.") { s.roam = it; bump() }
            SSwitch("Random comedy bits", s.gags, "Sneezes, juggling, air guitar…") { s.gags = it; bump() }
            SSwitch("Smooth animation (beta)", s.smoothAnimation, "Squishy landings, tails, ears, hair and capes that swing and settle.") { s.smoothAnimation = it; bump() }
            SSwitch("Living reactions (beta)", s.livingReactions, "Every touch gets a fresh reaction, never the same one twice in a row.") { s.livingReactions = it; bump() }
            SSwitch("Sound effects", s.soundEffects, "Smash game, fireworks. Always quiet when your phone is on silent or vibrate.") { s.soundEffects = it; bump() }
            SSwitch("Touch feel", s.touchFeel, "Feel them in your hand: a bump when picked up, a rattle when shaken, a purr when petted, a heartbeat when you keep holding, slingshot clicks, landing thumps.") { s.touchFeel = it; bump() }
            SSwitch("Slingshot starts the game", s.slingGame, "Hold your character a moment, pull back and keep holding through 3, 2, 1: the Slingshot game starts with that pull. Let go before GO for a plain fling. Off = always a plain fling (the game stays in the long-press menu › Games).") { s.slingGame = it; bump() }
            SSwitch("Throw on fast flick", s.flickThrow, "A very fast flick throws the character (SPLAT, THWACK, PANCAKE). Off: any swipe makes it travel with its own power.") { s.flickThrow = it; bump() }
            SSwitch("Double-tap for power", s.doubleTapPower, "Double-tap a character and it fires one of its powers. Off: double-tap opens the chat.") { s.doubleTapPower = it; bump() }
            SSwitch("Chat buttons", s.chatButton, "Faint 💬 on the left and 🎤 on the right of each character.") { s.chatButton = it; bump() }
            SSubhead("Energy")
            SNote(energyText)
            SNote("Every change here applies to your companion right away. No need to save or restart.")
        }
    }

    // ---------------- character ----------------

    @Composable
    private fun CharacterCard(@Suppress("UNUSED_PARAMETER") r: Int) {
        val reg = com.combo.monkeycompanion.character.CharacterRegistry
        val c = reg.current
        SCard("Character", "Switching body keeps your chats, memory, routines and settings. Each character has its own personality, lines and superpowers.") {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (ch in reg.allWithCustom) {
                    val picked = ch.id == c.id
                    Card(
                        onClick = { if (!picked) { s.characterId = ch.id; s.applyCharacter(); bump() } },
                        colors = CardDefaults.cardColors(containerColor = if (picked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.width(96.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(6.dp)) {
                            androidx.compose.ui.viewinterop.AndroidView(factory = { ctx -> CharacterPreviewView(ctx, ch) },
                                update = { it.picked = picked }, modifier = Modifier.fillMaxWidth().height(96.dp))
                            Text(ch.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                }
            }
            Text("${c.name}: ${c.tagline}" + if (c.wardrobe.isNotEmpty()) "  (${c.wardrobe.size} wardrobe item${if (c.wardrobe.size == 1) "" else "s"})" else "")
            if (c.wardrobe.isNotEmpty()) SSecondary("Wardrobe") {
                startActivity(Intent(this@SettingsActivity, WardrobeActivity::class.java).putExtra("character", c.id))
            }
            SSecondary("Character Studio (create from a photo, import)") { startActivity(Intent(this@SettingsActivity, CompanionStudioActivity::class.java)) }
        }
    }

    /** v10.38.23: how often characters use their OWN powers by themselves (which powers = each character's own list). */
    @Composable
    private fun SuperpowersCard(@Suppress("UNUSED_PARAMETER") r: Int) {
        val powers = com.combo.monkeycompanion.brain.Superpowers
        val ids = listOf(powers.OFF, powers.RANDOM, powers.OFTEN)
        val sel = when (s.power) { powers.OFF -> 0; powers.OFTEN -> 2; else -> 1 }
        val who = com.combo.monkeycompanion.character.CharacterRegistry.current
        SCard("Superpowers", "Each character only ever uses its own powers. Double-tap and the long-press ⚡ menu fire one any time.") {
            SChoice("Use powers by themselves", listOf("Off", "Sometimes", "Often"), sel) { s.power = ids[it]; bump() }
            SNote(when (sel) {
                0 -> "Off: characters never use a power on their own (double-tap and the menu still work)."
                2 -> "Often: about every 12 seconds of play, never with Battery saver on or while resting."
                else -> "Sometimes: now and then while you're around, never with Battery saver on or while resting."
            })
            SNote("${who.name}'s powers: " + com.combo.monkeycompanion.brain.PowerPrefs.enabled(who).joinToString { it.title } + ".")
            if (who.id.startsWith("custom_")) SSecondary("Choose ${who.name}'s powers (Creator Studio)") {
                startActivity(Intent(this@SettingsActivity, CompanionStudioActivity::class.java).putExtra("character", who.id))
            } else if (com.combo.monkeycompanion.brain.PowerPrefs.choosable(who)) SSecondary("Choose ${who.name}'s powers") {
                startActivity(Intent(this@SettingsActivity, PowerChoiceActivity::class.java).putExtra("id", who.id))
            }
        }
    }

    @Composable
    private fun BackgroundAiCard(@Suppress("UNUSED_PARAMETER") r: Int) {
        val modes = listOf("off", "gentle", "fast")
        SCard("Background AI", "Optional background intelligence, independent of character animations.") {
            SChoice(null, listOf("Off: only when you interact", "Gentle: occasional background reactions", "Fast: more frequent, more battery"),
                modes.indexOf(s.backgroundAi).coerceAtLeast(0)) { s.backgroundAi = modes[it]; bump() }
        }
    }

    // ---------------- personality & memory ----------------

    @Composable
    private fun PersonalityCard(@Suppress("UNUSED_PARAMETER") r: Int) {
        SCard("Personality & memory") {
            SSlider("How independent", s.mindIndependence, 0..100) { s.mindIndependence = it; bump() }
            SNote("Controls how often ${nm} acts on its own (exploring new apps, habits, gags, powers) and how strongly its traits show. High focus (typing, calls, meetings, banking, DND, quiet hours) always overrides.")
            SSecondary("Open ${nm}'s diary") { startActivity(Intent(this@SettingsActivity, DiaryActivity::class.java)) }
            SSwitch("Random AI quips", s.quipsEnabled, "Only when a real brain is set up (uses a little data and battery).") { s.quipsEnabled = it; bump() }
            val mins = listOf(5, 10, 15, 30, 60)
            SChoice("How often", mins.map { "About every $it minutes" }, mins.indexOf(s.quipMinutes).coerceAtLeast(2)) { s.quipMinutes = mins[it]; bump() }
            val turns = listOf(0, 4, 8, 15)
            SChoice("Conversation memory", turns.map { if (it == 0) "Remember nothing" else "Remember last $it exchanges" }, turns.indexOf(s.memoryTurns).coerceAtLeast(2)) { s.memoryTurns = turns[it]; bump() }
            SSecondary("Clear chat history") { ChatHub.clearMemory(); clearedText = "Forgotten! 🍌" }
            if (clearedText.isNotBlank()) SNote(clearedText)
        }
    }

    /** v4.2: proactive buddy, quiet hours, pipe-up topics, break nudges, phone moments, bubble chat and voice. */
    @Composable
    private fun BuddyCard(@Suppress("UNUSED_PARAMETER") r: Int) {
        val PL = com.combo.monkeycompanion.agent.ProLevel
        val levels = com.combo.monkeycompanion.agent.ProLevel.entries
        val QH = com.combo.monkeycompanion.agent.QuietHours
        var qStart by remember { mutableStateOf(QH.fmt(s.quietStart)) }
        var qEnd by remember { mutableStateOf(QH.fmt(s.quietEnd)) }
        var usage by remember { mutableStateOf(s.usageApps) }
        SCard("Buddy mode", "${NM} pipes up for moments that matter (a burst of messages, someone important, a delivery, an OTP, a long scroll session, full battery, good morning). Never while you type, call, game or watch fullscreen, in Do Not Disturb or in quiet hours.") {
            SChoice("How often it pipes up", levels.map { "${it.label}" + if (it.perHour > 0) " (max ${it.perHour}/hour)" else "" }, levels.indexOf(PL.of(s.proactiveLevel))) {
                s.proactiveLevel = levels[it].name; bump()
            }
            SNote(PL.of(s.proactiveLevel).help)
            // v10.38.25: saved as you type (a time like 23:00 is used as soon as it's complete)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SField(qStart, { qStart = it; QH.parse(it, -1).takeIf { v -> v >= 0 }?.let { v -> s.quietStart = v } }, "Quiet from", "23:00", modifier = Modifier.weight(1f))
                SField(qEnd, { qEnd = it; QH.parse(it, -1).takeIf { v -> v >= 0 }?.let { v -> s.quietEnd = v } }, "to", "07:00", modifier = Modifier.weight(1f))
            }
            SSubhead("What may it pipe up about?")
            for (c in com.combo.monkeycompanion.agent.ProCat.entries) {
                SSwitch(c.label + (if (c.minLevel.ordinal > com.combo.monkeycompanion.agent.ProLevel.CHILL.ordinal) " (Buddy+)" else ""), s.proactiveCat(c)) { s.setProactiveCat(c, it); bump() }
            }
            SNote("Messages, bursts and important people need the Notification reader power; chat-list digests need the Screen agent power. OTPs are shown only to you (never spoken or sent to a brain).")
            val usageMins = listOf(15, 20, 30, 45, 60, 90)
            SChoice("Break nudge", usageMins.map { "After $it min in one go" }, usageMins.indexOf(s.usageMinutes).coerceAtLeast(2)) { s.usageMinutes = usageMins[it]; bump() }
            SField(usage, { usage = it; s.usageApps = it }, "Break-nudge apps", "com.instagram.android, com.google.android.youtube")
            val ents = listOf("off", "sometimes", "often")
            SChoice("Power entrances", listOf("Off", "Sometimes (~30% of pipe-ups)", "Often (~60%)"), ents.indexOf(s.powerEntrances).coerceAtLeast(1)) { s.powerEntrances = ents[it]; bump() }
            SNote("Message summaries fly in, important ones arrive with a power strike, jokes come in on rockets / jets. Never with battery saver or with superpowers Off.")
            SSubhead("Phone moments")
            SNote("Little reactions when your phone does something (like plugging in the charger). Never in Do Not Disturb, quiet hours, calls or fullscreen.")
            for (m in com.combo.monkeycompanion.brain.PhoneMoments.Moment.entries) {
                SSwitch(m.label, s.phoneMoment(m.id)) { s.setPhoneMoment(m.id, it); bump() }
            }
            SSubhead("Bubble chat & voice")
            val tos = listOf(30, 60, 120, 300, 0)
            SChoice("Close the bubble chat", listOf("After 30 s idle", "After 1 min idle", "After 2 min idle (default)", "After 5 min idle", "Never by itself"),
                tos.indexOf(s.chatTimeoutSecs).let { if (it < 0) 2 else it }) { s.chatTimeoutSecs = tos[it]; bump() }
            SSwitch("Speak the answer when I asked by voice", s.speakVoiceReplies) { s.speakVoiceReplies = it; bump() }
            SSwitch("Speak all replies out loud", s.speakReplies) { s.speakReplies = it; bump() }
        }
    }

    /** v10.19: daily habits (friendship heart, daily hello, streak sulking, goodnight). */
    @Composable
    private fun EngagementCard(@Suppress("UNUSED_PARAMETER") r: Int) {
        val S = com.combo.monkeycompanion.fun5.Fun5Store
        val f = com.combo.monkeycompanion.v5.Friendship
        val st = S.friendship(this); val b = S.bond(this)
        SCard("Engagement") {
            Text("Friendship ${com.combo.monkeycompanion.v5.Bond.points(st.xp)}/100 · level ${f.level(st.xp)} (${f.title(f.level(st.xp))}) · " +
                "${st.streak}-day streak · feeling ${com.combo.monkeycompanion.v5.Bond.warmthWord(b.warmth)}")
            SNote("Friendship grows when you pat, chat, play games and come back each day. The level never goes down.")
            for (ft in com.combo.monkeycompanion.fun5.Fun5Store.Feature.entries.filter { it.engagement }) SSwitch(ft.title, S.on(this@SettingsActivity, ft), ft.help) { S.set(this@SettingsActivity, ft, it); bump() }
        }
    }

    /** v5: one on/off per big feature; the characters decide the rest. */
    @Composable
    private fun Fun5Card(@Suppress("UNUSED_PARAMETER") r: Int) {
        val S = com.combo.monkeycompanion.fun5.Fun5Store
        SCard("Things ${nm} does by itself", "Games, clips, two phones and the friendship level are always there. These switches cover what ${nm} does on its own.") {
            for (ft in com.combo.monkeycompanion.fun5.Fun5Store.Feature.entries.filter { !it.engagement }) SSwitch(ft.title, S.on(this@SettingsActivity, ft), ft.help) { S.set(this@SettingsActivity, ft, it); bump() }
            SNote("Weather uses your coarse location only if you already allowed location for ${nm}, and asks Open-Meteo at most every 3 hours. Cricket scores are fetched only when you ask.")
        }
    }

    @Composable
    private fun PhonePowersCard(@Suppress("UNUSED_PARAMETER") r: Int) {
        SCard("Phone powers", "What ${nm} may do, for every brain. Anything that opens or changes something asks Yes/No first.") {
            SSecondary("${NM}'s skills (errands, senses & daily habits)") { startActivity(Intent(this@SettingsActivity, SkillsActivity::class.java)) }
            SSecondary("What can ${nm} do? (powers & permissions)") { startActivity(Intent(this@SettingsActivity, CapabilitiesActivity::class.java)) }
            for (g in CapGroup.entries.filter { it != CapGroup.SCREEN || com.combo.monkeycompanion.brain.Distribution.screenAgent }) {
                val n = Capabilities.all.count { it.group == g }
                SSwitch("${g.label} ($n)", s.groupEnabled(g)) { s.setGroupEnabled(g, it); bump() }
            }
            val modes = listOf("auto", "clock", "settai")
            SChoice("Timers & alarms", listOf("Clock app when possible, else ${nm}'s own", "Always the Clock app", "Always ${nm}'s own (rings with ${nm})"),
                modes.indexOf(s.clockMode).coerceAtLeast(0)) { s.clockMode = modes[it]; bump() }
            SSecondary("Recent actions log") { showLog = true }
        }
    }

    companion object { private const val REQ_PICK = 31 }
}

/**
 * v9.5 / v10.29 Reusable "🧠 Give Brain" Quick-Setup Dialog & Highlighted Card Builder!
 * Exposed on Home, Character Creation (CompanionStudioActivity), and Settings. Set-Tai Cloud is ready
 * by default; the user can still turn Brain off or pick offline / own-key.
 */
object GiveBrainDialog {
    fun show(
        act: Activity,
        onChanged: (() -> Unit)? = null,
        onRunAiPhotoModel: (() -> Unit)? = null,
    ) {
        ChatHub.init(act)
        // v10.38.17: Material 3 version on Compose screens (Studio, Settings, new home); old style elsewhere.
        if (act is androidx.activity.ComponentActivity) { showCompose(act, onChanged, onRunAiPhotoModel); return }
        val s = ChatHub.settings
        val d = act.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val brown = com.combo.monkeycompanion.theme.Th.rgb(0x1E, 0x22, 0x40)
        val soft = com.combo.monkeycompanion.theme.Th.rgb(0x64, 0x6A, 0x8C)
        val accent = com.combo.monkeycompanion.theme.Th.rgb(0x6C, 0x5C, 0xE7)
        val purple = com.combo.monkeycompanion.theme.Th.rgb(0x09, 0x84, 0xE3)

        val root = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(10))
        }

        val statusTv = TextView(act).apply {
            text = ChatHub.giveBrainStatusLine()
            textSize = 13.2f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (ChatHub.isBrainEnabled()) com.combo.monkeycompanion.theme.Th.rgb(0x1B, 0x5E, 0x20) else brown)
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(statusTv)

        val toggleRow = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(10))
        }
        lateinit var onBtn: Button
        lateinit var offBtn: Button
        fun styleToggle() {
            val enabled = ChatHub.isBrainEnabled()
            statusTv.text = ChatHub.giveBrainStatusLine()
            statusTv.setTextColor(if (enabled) com.combo.monkeycompanion.theme.Th.rgb(0x1B, 0x5E, 0x20) else brown)
            onBtn.setTextColor(if (enabled) Color.WHITE else brown)
            onBtn.background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(if (enabled) com.combo.monkeycompanion.theme.Th.rgb(0x2E, 0x7D, 0x32) else com.combo.monkeycompanion.theme.Th.rgb(0xF5, 0xE6, 0xD0))
            }
            offBtn.setTextColor(if (!enabled) Color.WHITE else brown)
            offBtn.background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(if (!enabled) com.combo.monkeycompanion.theme.Th.rgb(0xC6, 0x28, 0x28) else com.combo.monkeycompanion.theme.Th.rgb(0xF5, 0xE6, 0xD0))
            }
        }
        onBtn = Button(act).apply {
            text = "🧠 Enable Brain (ON)"; isAllCaps = false; textSize = 13f
            setOnClickListener {
                ChatHub.setBrainEnabled(true)
                styleToggle()
                onChanged?.invoke()
            }
        }
        offBtn = Button(act).apply {
            text = "🔒 Brain OFF (Local Only)"; isAllCaps = false; textSize = 13f
            setOnClickListener {
                ChatHub.setBrainEnabled(false)
                styleToggle()
                onChanged?.invoke()
            }
        }
        toggleRow.addView(onBtn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
        toggleRow.addView(offBtn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        styleToggle()
        root.addView(toggleRow)

        root.addView(TextView(act).apply {
            text = "Set-Tai Cloud is ready when you enable Brain. No key or Gmail sign-in. 40 requests per UTC day, including background AI. Chats and character context go to Gemini; only anonymous quota counts are stored by the gateway. Local jokes, dance and phone commands stay local."
            textSize = 13f; setTextColor(soft)
        })
        val advanced = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (s.useOwnApiKey) View.VISIBLE else View.GONE
        }
        root.addView(Switch(act).apply {
            text = "Advanced: use my own API key / server"
            isChecked = s.useOwnApiKey
            setOnCheckedChangeListener { _, checked ->
                s.useOwnApiKey = checked
                advanced.visibility = if (checked) View.VISIBLE else View.GONE
                styleToggle(); onChanged?.invoke()
            }
        })
        root.addView(advanced)
        val providers = Provider.entries
        var curProvider = s.provider
        advanced.addView(TextView(act).apply {
            text = "AI Provider (Gemini / OpenRouter / Groq / OpenAI / xAI / Claude):"
            textSize = 12.5f; setTypeface(typeface, Typeface.BOLD); setTextColor(soft)
        })
        val keyInput = EditText(act).apply {
            textSize = 14f; setTextColor(brown); isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val modelInput = EditText(act).apply {
            textSize = 14f; setTextColor(brown); isSingleLine = true
        }
        val hintTv = TextView(act).apply { textSize = 11.8f; setTextColor(soft); setPadding(0, dp(2), 0, dp(6)) }

        fun syncProviderFields() {
            val savedKey = s.apiKeyFor(curProvider)
            keyInput.setText("")
            keyInput.hint = if (savedKey.isNotEmpty()) "Saved key ••••${savedKey.takeLast(4)} (type to replace)" else "Paste ${curProvider.label} API Key"
            modelInput.setText(s.modelFor(curProvider))
            hintTv.text = "Key from: ${curProvider.keyHint}"
        }
        val sp = Spinner(act).apply {
            adapter = ArrayAdapter(act, android.R.layout.simple_spinner_dropdown_item, providers.map { it.label })
            setSelection(providers.indexOf(curProvider).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    curProvider = providers[pos]
                    s.provider = curProvider
                    syncProviderFields()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        syncProviderFields()
        advanced.addView(sp)
        advanced.addView(keyInput)
        advanced.addView(hintTv)
        advanced.addView(TextView(act).apply {
            text = "Model Name:"
            textSize = 12.5f; setTypeface(typeface, Typeface.BOLD); setTextColor(soft)
        })
        advanced.addView(modelInput)

        val testResultTv = TextView(act).apply {
            textSize = 12.5f; setTextColor(soft); setPadding(0, dp(6), 0, dp(4))
        }
        advanced.addView(Button(act).apply {
            text = "⚡ Save Key, Enable Brain & Test Connection"
            isAllCaps = false; textSize = 13.5f; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(purple) }
            setOnClickListener {
                val k = keyInput.text?.toString()?.trim().orEmpty()
                if (k.isNotEmpty()) s.setApiKeyFor(curProvider, k)
                val m = modelInput.text?.toString()?.trim().orEmpty().ifBlank { curProvider.defaultModel }
                s.setModelFor(curProvider, m)
                s.provider = curProvider
                s.useOwnApiKey = true
                s.brainType = BrainType.ONLINE
                ChatHub.setBrainEnabled(true)
                styleToggle()
                onChanged?.invoke()
                testResultTv.text = "Testing ${curProvider.label} ($m)…"
                val b = ChatHub.brain()
                ChatHub.test(b) { res ->
                    res.onSuccess {
                        testResultTv.text = "✅ Connected! ${b.label}: $it"
                        testResultTv.setTextColor(com.combo.monkeycompanion.theme.Th.rgb(0x1B, 0x5E, 0x20))
                    }.onFailure {
                        testResultTv.text = "❌ ${it.message}"
                        testResultTv.setTextColor(com.combo.monkeycompanion.theme.Th.rgb(0xC6, 0x28, 0x28))
                    }
                }
            }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) })
        advanced.addView(testResultTv)

        if (onRunAiPhotoModel != null) {
            root.addView(Button(act).apply {
                text = "🧠✨ AI-Model My Companion Now"
                isAllCaps = false; textSize = 13.5f; setTextColor(Color.WHITE)
                background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(accent) }
                setOnClickListener {
                    val k = keyInput.text?.toString()?.trim().orEmpty()
                    if (k.isNotEmpty()) s.setApiKeyFor(curProvider, k)
                    if (!ChatHub.isBrainEnabled()) {
                        ChatHub.setBrainEnabled(true)
                        styleToggle()
                        onChanged?.invoke()
                    }
                    onRunAiPhotoModel.invoke()
                }
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) })
        }

        android.app.AlertDialog.Builder(act)
            .setTitle("🧠 Give Brain to your characters")
            .setView(ScrollView(act).apply { addView(root) })
            .setNeutralButton("Full Brain Settings") { _, _ ->
                act.startActivity(Intent(act, SettingsActivity::class.java))
            }
            .setPositiveButton("Done") { _, _ ->
                val k = keyInput.text?.toString()?.trim().orEmpty()
                if (s.useOwnApiKey && k.isNotEmpty()) {
                    s.setApiKeyFor(curProvider, k)
                    s.provider = curProvider
                    s.brainType = BrainType.ONLINE
                    ChatHub.setBrainEnabled(true)
                }
                onChanged?.invoke()
            }
            .show()
    }

    private fun showCompose(act: androidx.activity.ComponentActivity, onChanged: (() -> Unit)?, onRunAiPhotoModel: (() -> Unit)?) {
        val dlg = android.app.Dialog(act)
        val cv = androidx.compose.ui.platform.ComposeView(act).apply {
            setViewTreeLifecycleOwner(act)
            setViewTreeSavedStateRegistryOwner(act)
            setViewTreeViewModelStoreOwner(act)
            setContent { SettaiTheme(act) { GiveBrainPanel(act, onChanged, onRunAiPhotoModel) { dlg.dismiss() } } }
        }
        dlg.setContentView(cv)
        dlg.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dlg.window?.decorView?.let {
            it.setViewTreeLifecycleOwner(act); it.setViewTreeSavedStateRegistryOwner(act); it.setViewTreeViewModelStoreOwner(act)
        }
        runCatching { dlg.show() }
    }

    @androidx.compose.runtime.Composable
    private fun GiveBrainPanel(act: Activity, onChanged: (() -> Unit)?, onRunAiPhotoModel: (() -> Unit)?, close: () -> Unit) {
        val s = ChatHub.settings
        var rev by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
        @Suppress("UNUSED_VARIABLE") val r = rev
        var provider by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(s.provider) }
        var key by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
        var model by androidx.compose.runtime.remember(provider) { androidx.compose.runtime.mutableStateOf(s.modelFor(provider)) }
        var result by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
        fun changed() { rev++; onChanged?.invoke() }
        fun saveKey() { val k = key.trim(); if (k.isNotEmpty()) { s.setApiKeyFor(provider, k); key = "" } }
        androidx.compose.material3.Card(androidx.compose.ui.Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(SettaiDesignTokens.cornerCardDp.dp)) {
            androidx.compose.runtime.key(rev) {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.heightIn(max = 620.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.material3.Text("Give Brain", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                androidx.compose.material3.Text(ChatHub.giveBrainStatusLine(), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                SSwitch("Brain on", ChatHub.isBrainEnabled(), "Off = local lines only. Jokes, dance and phone commands always stay on your phone.") {
                    ChatHub.setBrainEnabled(it); changed()
                }
                SNote("Set-Tai Cloud is ready when Brain is on. No key or Gmail sign-in. 40 requests per UTC day, including background AI. Chats and character context go to Gemini; the gateway keeps only anonymous quota counts.")
                SSwitch("Advanced: use my own API key / server", s.useOwnApiKey) { s.useOwnApiKey = it; changed() }
                if (s.useOwnApiKey) {
                    val providers = Provider.entries
                    SChoice("Provider", providers.map { it.label }, providers.indexOf(provider).coerceAtLeast(0)) { provider = providers[it]; s.provider = provider; changed() }
                    val saved = s.apiKeyFor(provider)
                    SField(key, { key = it }, "API key", if (saved.isNotEmpty()) "Saved ••••${saved.takeLast(4)} (type to replace)" else "Paste ${provider.label} key", secret = true)
                    SNote("Key from: ${provider.keyHint}")
                    SField(model, { model = it }, "Model", provider.defaultModel)
                    SPrimary("Save key, turn Brain on & test") {
                        saveKey()
                        val m = model.trim().ifBlank { provider.defaultModel }
                        s.setModelFor(provider, m); s.provider = provider; s.useOwnApiKey = true; s.brainType = BrainType.ONLINE
                        ChatHub.setBrainEnabled(true); changed()
                        result = "Testing ${provider.label} ($m)…"
                        val b = ChatHub.brain()
                        ChatHub.test(b) { res -> res.onSuccess { result = "✅ Connected! ${b.label}: $it" }.onFailure { result = "❌ ${it.message}" } }
                    }
                    if (result.isNotBlank()) androidx.compose.material3.Text(result)
                }
                if (onRunAiPhotoModel != null) SSecondary("AI-model my companion now") {
                    saveKey()
                    if (!ChatHub.isBrainEnabled()) { ChatHub.setBrainEnabled(true); changed() }
                    onRunAiPhotoModel()
                }
                androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                    androidx.compose.material3.TextButton(onClick = { close(); act.startActivity(Intent(act, SettingsActivity::class.java).putExtra("settai_settings_section", "brain")) }) {
                        androidx.compose.material3.Text("All brain settings")
                    }
                    androidx.compose.material3.TextButton(onClick = {
                        if (s.useOwnApiKey && key.isNotBlank()) { saveKey(); s.provider = provider; s.brainType = BrainType.ONLINE; ChatHub.setBrainEnabled(true) }
                        onChanged?.invoke(); close()
                    }) { androidx.compose.material3.Text("Done") }
                }
            }
            }
        }
    }
}

