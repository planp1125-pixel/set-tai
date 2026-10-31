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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.combo.monkeycompanion.ai.BrainType
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.ai.ModelStore
import com.combo.monkeycompanion.powers.ActionLog
import com.combo.monkeycompanion.powers.CapGroup
import com.combo.monkeycompanion.powers.Capabilities
import com.combo.monkeycompanion.ai.Provider
import java.io.File
import java.util.Locale

/** Brain & settings: choose offline / online brain, keys, models, quips, memory and phone powers. */
@SuppressLint("SetTextI18n")
class SettingsActivity : Activity() {

    private val s get() = ChatHub.settings
    private val d by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * d).toInt()
    private val brown = com.combo.monkeycompanion.theme.Th.rgb(0x1E, 0x22, 0x40)
    private val soft = com.combo.monkeycompanion.theme.Th.rgb(0x64, 0x6A, 0x8C)
    private val accent = com.combo.monkeycompanion.theme.Th.rgb(0x6C, 0x5C, 0xE7)
    private val cream = com.combo.monkeycompanion.theme.Th.rgb(0xF3, 0xF4, 0xFA)
    private val ui = Handler(Looper.getMainLooper())

    private lateinit var onlineCard: View
    private lateinit var offlineCard: View
    private lateinit var keyField: EditText
    private lateinit var keyHint: TextView
    private lateinit var modelField: EditText
    private lateinit var urlField: EditText
    private lateinit var urlRow: View
    private lateinit var onlineResult: TextView
    private lateinit var offlineStatus: TextView
    private lateinit var dlBar: ProgressBar
    private lateinit var dlText: TextView
    private lateinit var dlCancel: Button
    private lateinit var tokenField: EditText
    private lateinit var installedBox: LinearLayout
    private lateinit var offlineResult: TextView
    private var catalogPick = 0
    private var shownProvider = Provider.OPENAI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChatHub.init(this)
        setContentView(build())
    }

    override fun onResume() { super.onResume(); refreshOffline(); pollDownload(); energyWindow.clear(); refreshEnergy() }
    override fun onPause() { super.onPause(); ui.removeCallbacksAndMessages(null); saveOnline() }

    // ---------------- UI helpers ----------------

    private fun text(t: String, size: Float = 14f, bold: Boolean = false, color: Int = brown) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(3), 0, dp(3))
    }

    private fun button(label: String, primary: Boolean = false, click: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f
        setTextColor(if (primary) Color.WHITE else brown)
        background = GradientDrawable().apply {
            cornerRadius = dp(24).toFloat(); setColor(if (primary) accent else Color.WHITE)
            setStroke(dp(2), if (primary) accent else com.combo.monkeycompanion.theme.Th.rgb(0xDD, 0xE0, 0xF0))
        }
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
    }

    private fun field(hint: String, secret: Boolean = false) = EditText(this).apply {
        this.hint = hint; textSize = 15f; setTextColor(brown); isSingleLine = true
        inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = GradientDrawable().apply { setColor(com.combo.monkeycompanion.theme.Th.rgb(0xFF, 0xFB, 0xF5)); cornerRadius = dp(12).toFloat(); setStroke(dp(1), com.combo.monkeycompanion.theme.Th.rgb(0xDD, 0xE0, 0xF0)) }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4) }
    }

    private fun card(title: String, vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(14))
        background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.WHITE) }
        addView(text(title, 17f, true))
        views.forEach { addView(it) }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
    }

    private fun spinner(items: List<String>, selected: Int, onPick: (Int) -> Unit) = Spinner(this).apply {
        adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, items)
        setSelection(selected)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = onPick(pos)
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    // ---------------- build ----------------

    private fun build(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(14), dp(18), dp(28)) }
        col.addView(text("Set-Tai brain & settings", 26f, true).apply { gravity = Gravity.CENTER_HORIZONTAL })
        col.addView(text("Choose how Settai thinks. On-device is private and free; online is smarter and needs an API key.", 14f, color = soft))

        // Brain type
        val group = RadioGroup(this)
        val opts = listOf(
            BrainType.NONE to "No brain (canned Settai lines)",
            BrainType.OFFLINE to "On-device AI (offline, private)",
            BrainType.ONLINE to "Online AI (your API key)",
        )
        opts.forEachIndexed { i, (bt, label) ->
            group.addView(RadioButton(this).apply { id = 100 + i; text = label; textSize = 15f; setTextColor(brown); isChecked = s.brainType == bt })
        }
        group.setOnCheckedChangeListener { _, id ->
            s.brainType = opts[id - 100].first
            updateCards()
            if (s.brainType == BrainType.OFFLINE) ChatHub.preloadOffline()
        }
        col.addView(card("Settai's brain", group))
        brainGroup = group
        pickerBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(card("🧠 Active brain (tap to switch)", text("Every downloaded model and every API you've set up. Tap one to use it; switch any time.", 12f, color = soft), pickerBox!!))
        refreshPicker()
        mainCol = col
        col.addView(buildCharacter())

        col.addView(buildOnline().also { onlineCard = it })
        col.addView(buildOffline().also { offlineCard = it })
        col.addView(buildPersonality())
        col.addView(buildBuddy())
        col.addView(buildAppearance())
        col.addView(buildRest())
        col.addView(buildSuperpowers().also { powersCard = it })
        col.addView(buildPowers())
        col.addView(buildFun5())
        updateCards()

        val scroll = ScrollView(this).apply { setBackgroundColor(cream); addView(col) }
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        return scroll
    }

    private fun updateCards() {
        onlineCard.visibility = if (s.brainType == BrainType.ONLINE) View.VISIBLE else View.GONE
        offlineCard.visibility = if (s.brainType == BrainType.OFFLINE) View.VISIBLE else View.GONE
    }

    // ---------------- online ----------------

    private fun buildOnline(): View {
        val providers = Provider.entries
        shownProvider = s.provider
        keyField = field("Paste API key", secret = true)
        keyHint = text("", 12f, color = soft)
        modelField = field("Model name")
        urlField = field("Base URL, e.g. https://my-server/v1")
        urlRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("Server base URL (OpenAI-compatible /chat/completions)", 13f, color = soft)); addView(urlField)
        }
        onlineResult = text("", 13f, color = soft)
        val sp = spinner(providers.map { it.label }, providers.indexOf(shownProvider)) { pos ->
            saveOnline()
            shownProvider = providers[pos]
            s.provider = shownProvider
            loadOnline()
        }
        loadOnline()
        return card(
            "Online brain",
            text("Provider", 13f, color = soft), sp,
            text("API key (stored encrypted on this phone)", 13f, color = soft), keyField, keyHint,
            text("Model", 13f, color = soft), modelField,
            urlRow,
            button("📋 Pick model from my account") { saveOnline(); pickModel(auto = false) },
            button("Save & test", primary = true) { saveOnline(); pickModel(auto = true) },
            onlineResult,
        )
    }

    private fun loadOnline() {
        val pr = shownProvider
        keyField.setText("")
        val k = s.apiKeyFor(pr)
        keyField.hint = if (k.isNotEmpty()) "Saved ••••${k.takeLast(4)} (type to replace)" else "Paste API key"
        keyHint.text = "Get a key: ${pr.keyHint}"
        modelField.setText(s.modelFor(pr))
        modelField.hint = pr.defaultModel.ifEmpty { "e.g. llama3.1" }
        urlField.setText(s.customBaseUrl)
        urlRow.visibility = if (pr == Provider.CUSTOM) View.VISIBLE else View.GONE
    }

    private fun saveOnline() {
        if (!::keyField.isInitialized) return
        val pr = shownProvider
        val k = keyField.text.toString().trim()
        if (k.isNotEmpty()) { s.setApiKeyFor(pr, k); keyField.setText(""); keyField.hint = "Saved ••••${k.takeLast(4)} (type to replace)" }
        s.setModelFor(pr, modelField.text.toString())
        if (pr == Provider.CUSTOM) s.customBaseUrl = urlField.text.toString()
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
        if (pr != Provider.CUSTOM && s.apiKeyFor(pr).isBlank()) { onlineResult.text = "Paste your API key first."; return }
        onlineResult.text = "Checking which models your key can use…"; onlineResult.setTextColor(soft)
        Thread {
            val res = runCatching { fetchModels(pr) }
            ui.post {
                val ids = res.getOrNull().orEmpty()
                if (auto) {
                    val cur = s.modelFor(pr)
                    if (ids.isNotEmpty() && cur !in ids) {
                        com.combo.monkeycompanion.ai.ModelList.pickDefault(ids)?.let { s.setModelFor(pr, it); modelField.setText(it) }
                    }
                    runTest(onlineResult)
                    return@post
                }
                if (ids.isEmpty()) {
                    onlineResult.text = "\u274C " + (res.exceptionOrNull()?.message ?: "No chat models found for this key."); onlineResult.setTextColor(com.combo.monkeycompanion.theme.Th.rgb(0xC6, 0x28, 0x28)); return@post
                }
                val best = com.combo.monkeycompanion.ai.ModelList.pickDefault(ids)
                val labels = ids.map { if (it == best) "$it  ⭐ recommended" else it }.toTypedArray()
                android.app.AlertDialog.Builder(this).setTitle("Models for ${pr.label}")
                    .setItems(labels) { _, i -> s.setModelFor(pr, ids[i]); modelField.setText(ids[i]); onlineResult.text = "Model set: ${ids[i]}" }
                    .setNegativeButton("Cancel", null).show()
                onlineResult.text = "${ids.size} models available."
            }
        }.start()
    }

    private fun runTest(out: TextView) {
        out.text = "Testing… (an on-device model can take 10-60 s to load the first time)"
        out.setTextColor(soft)
        val b = ChatHub.brain()
        val t0 = android.os.SystemClock.elapsedRealtime()
        ChatHub.test(b) { r ->
            r.onSuccess {
                // v10.17.2: show where it ran and how long it took, and say so if the GPU fell back to CPU.
                val secs = (android.os.SystemClock.elapsedRealtime() - t0) / 1000f
                val ob = b as? com.combo.monkeycompanion.ai.OfflineBrain
                val where = ob?.loadedOn?.let { " \u00b7 ran on $it" } ?: ""
                val fell = if (ob != null && s.offlineUseGpu && ob.loadedOn == "CPU")
                    "\n(GPU didn't work on this phone, so it used the CPU: ${ob.gpuError ?: "unknown reason"})" else ""
                out.text = "\u2705 ${b.label} says: $it\n(${"%.1f".format(secs)} s$where)$fell"
                out.setTextColor(com.combo.monkeycompanion.theme.Th.rgb(0x2E, 0x7D, 0x32))
            }
            r.onFailure { out.text = "\u274C ${it.message}"; out.setTextColor(com.combo.monkeycompanion.theme.Th.rgb(0xC6, 0x28, 0x28)) }
        }
    }

    // ---------------- offline ----------------

    private var brainGroup: RadioGroup? = null
    private var pickerBox: LinearLayout? = null

    /** v10.13.1 brain picker: local models + configured API providers; the active one is marked. */
    private fun refreshPicker() {
        val box = pickerBox ?: return
        box.removeAllViews()
        fun row(label: String, active: Boolean, onUse: () -> Unit, onDelete: (() -> Unit)? = null) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            r.addView(text((if (active) "✅ " else "⚪ ") + label, 14f, active).apply { setOnClickListener { onUse() } }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            if (!active) r.addView(Button(this).apply { text = "Use"; isAllCaps = false; setOnClickListener { onUse() } })
            if (onDelete != null) r.addView(Button(this).apply { text = "Delete"; isAllCaps = false; setOnClickListener { onDelete() } })
            box.addView(r)
        }
        fun activate(bt: BrainType) {
            s.brainType = bt
            ChatHub.setBrainEnabled(true)
            brainGroup?.check(100 + listOf(BrainType.NONE, BrainType.OFFLINE, BrainType.ONLINE).indexOf(bt))
            updateCards(); refreshPicker(); refreshOffline()
            if (bt == BrainType.OFFLINE) ChatHub.preloadOffline()
        }
        val on = s.aiBrainEnabled
        row("No AI (canned lines)", !on || s.brainType == BrainType.NONE, { s.brainType = BrainType.NONE; ChatHub.setBrainEnabled(false); brainGroup?.check(100); updateCards(); refreshPicker() })
        for (f in ModelStore.installed(this)) {
            val title = ModelStore.CATALOG.firstOrNull { it.file == f.name }?.title?.substringBefore(" (") ?: f.name
            row("📱 $title  ·  ${mb(f.length())}", on && s.brainType == BrainType.OFFLINE && s.offlineModelPath == f.path,
                { s.offlineModelPath = f.path; activate(BrainType.OFFLINE) },
                { if (s.offlineModelPath == f.path) s.offlineModelPath = ""; f.delete(); ChatHub.setBrainEnabled(s.aiBrainEnabled); refreshPicker(); refreshOffline() })
        }
        for (pr in Provider.values()) {
            val ok = if (pr == Provider.CUSTOM) s.customBaseUrl.isNotBlank() else s.apiKeyFor(pr).isNotBlank()
            if (!ok) continue
            row("☁️ ${pr.label} (${s.modelFor(pr)})", on && s.brainType == BrainType.ONLINE && s.provider == pr, { s.provider = pr; activate(BrainType.ONLINE) })
        }
        if (box.childCount == 1) box.addView(text("No models or API keys yet. Download a model or add a key below.", 12f, color = soft))
    }

    private fun buildOffline(): View {
        offlineStatus = text("", 14f)
        val catGroup = RadioGroup(this)
        ModelStore.CATALOG.forEachIndexed { i, m ->
            val inUse = File(s.offlineModelPath).name == m.file
            if (inUse) catalogPick = i
            catGroup.addView(RadioButton(this).apply { id = 200 + i; text = m.title + if (inUse) "  ✅ in use" else ""; textSize = 15f; setTextColor(brown); isChecked = if (s.offlineModelPath.isBlank()) i == 0 else inUse })
            catGroup.addView(text(m.blurb, 12f, color = soft).apply { setPadding(dp(32), 0, 0, dp(4)) })
        }
        catGroup.setOnCheckedChangeListener { _, id ->
            catalogPick = id - 200
            // v10.13.1: picking a model that is already on the phone USES it (it used to only pick what to download,
            // so the chat kept running the old model, e.g. Qwen, while Gemma looked selected).
            val m = ModelStore.CATALOG.getOrNull(catalogPick) ?: return@setOnCheckedChangeListener
            val f = ModelStore.installed(this).firstOrNull { it.name == m.file }
            if (f != null && f.path != s.offlineModelPath && !ModelStore.isDownloading(this)) { s.offlineModelPath = f.path; refreshOffline(); ChatHub.preloadOffline() }
            else if (f == null && ::offlineStatus.isInitialized) offlineStatus.text = "${m.title} isn't on this phone yet — tap Download. Still using: ${File(s.offlineModelPath).name.ifBlank { "none" }}"
        }
        tokenField = field("Hugging Face token (only for gated models)", secret = true)
        val tk = s.hfToken
        if (tk.isNotEmpty()) tokenField.hint = "Saved ••••${tk.takeLast(4)} (type to replace)"
        dlBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000; visibility = View.GONE }
        dlText = text("", 13f, color = soft)
        dlCancel = button("Cancel download") {
            ModelStore.cancelDownload(this); dlText.text = "Download cancelled."; dlBar.visibility = View.GONE; dlCancel.visibility = View.GONE
        }.apply { visibility = View.GONE }
        installedBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        offlineResult = text("", 13f, color = soft)
        val gpu = CheckBox(this).apply {
            text = "Use GPU (faster on many phones; turn off if loading fails)"; setTextColor(brown); isChecked = s.offlineUseGpu
            setOnCheckedChangeListener { _, c -> s.offlineUseGpu = c }
        }
        val tools = CheckBox(this).apply {
            text = "Let the on-device brain use phone powers (tags like [alarm 07:00])"; setTextColor(brown); isChecked = s.offlineTools
            setOnCheckedChangeListener { _, c -> s.offlineTools = c }
        }
        return card(
            "On-device brain",
            offlineStatus,
            text("Download a model", 15f, true), catGroup,
            text("Gated models: open the model's Hugging Face page, accept the license, then create a Read token at huggingface.co/settings/tokens.", 12f, color = soft),
            tokenField,
            button("Download selected model", primary = true) { startDownload() },
            button("Open model page on Hugging Face") {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ModelStore.CATALOG[catalogPick].pageUrl)))
            },
            dlBar, dlText, dlCancel,
            button("Import a model file (.litertlm) from storage") { pickFile() },
            text("Models on this phone", 15f, true), installedBox,
            gpu, tools,
            button("Test on-device brain") { runTest(offlineResult) },
            offlineResult,
        )
    }

    private fun refreshOffline() {
        refreshPicker()
        if (!::offlineStatus.isInitialized) return
        val path = s.offlineModelPath
        val f = File(path)
        offlineStatus.text = when {
            path.isBlank() -> "No model chosen yet."
            ModelStore.isDownloading(this) -> "Downloading ${f.name}…"
            f.exists() -> "Using: ${f.name} (${mb(f.length())})"
            else -> "Chosen model file is missing: ${f.name}"
        }
        val free = ModelStore.freeBytes(this)
        installedBox.removeAllViews()
        val files = ModelStore.installed(this)
        if (files.isEmpty()) installedBox.addView(text("None yet. Free space: ${mb(free)}", 13f, color = soft))
        for (file in files) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val active = file.path == path
            row.addView(text((if (active) "\u2705 " else "") + "${file.name}\n${mb(file.length())}", 13f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            if (!active) row.addView(Button(this).apply {
                text = "Use"; isAllCaps = false
                setOnClickListener { s.offlineModelPath = file.path; refreshOffline(); ChatHub.preloadOffline() }
            })
            row.addView(Button(this).apply {
                text = "Delete"; isAllCaps = false
                setOnClickListener {
                    if (active) s.offlineModelPath = ""
                    file.delete(); refreshOffline()
                }
            })
            installedBox.addView(row)
        }
        if (files.isNotEmpty()) installedBox.addView(text("Free space: ${mb(free)}", 12f, color = soft))
    }

    private fun mb(b: Long) = if (b >= 1_000_000_000) String.format(Locale.US, "%.2f GB", b / 1e9) else String.format(Locale.US, "%.0f MB", b / 1e6)

    private fun startDownload() {
        val m = ModelStore.CATALOG[catalogPick]
        val typed = tokenField.text.toString().trim()
        if (typed.isNotEmpty()) { s.hfToken = typed; tokenField.setText(""); tokenField.hint = "Saved ••••${typed.takeLast(4)}" }
        val token = s.hfToken
        if (m.gated && token.isBlank()) {
            dlText.text = "This model needs a Hugging Face token: accept the license on its page, then paste a Read token above."
            return
        }
        if (ModelStore.isDownloading(this)) { dlText.text = "A download is already running."; return }
        dlText.text = "Checking…"
        Thread {
            try {
                val probe = ModelStore.probe(m.url, token)
                val need = if (probe.sizeBytes > 0) probe.sizeBytes else m.approxMb * 1_000_000L
                val free = ModelStore.freeBytes(this)
                if (free < need + 300_000_000L) {
                    ui.post { dlText.text = "Not enough space: needs ${mb(need)}, only ${mb(free)} free." }
                    return@Thread
                }
                val target = ModelStore.startDownload(this, probe, m.file, m.title)
                s.offlineModelPath = target.path
                ui.post { dlText.text = "Download started (${mb(need)}). You can leave this screen; it continues in the background."; pollDownload() }
            } catch (e: Exception) {
                ui.post { dlText.text = "\u274C ${e.message}" }
            }
        }.start()
    }

    private fun pollDownload() {
        val p = ModelStore.progress(this) ?: run { dlBar.visibility = View.GONE; dlCancel.visibility = View.GONE; return }
        when (p.status) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                dlBar.visibility = View.GONE; dlCancel.visibility = View.GONE
                dlText.text = "\u2705 Download complete!"
                ModelStore.clearDownloadRecord(this)
                // v10.13.1: a freshly downloaded model becomes the active brain (switch back any time in the picker)
                s.brainType = BrainType.OFFLINE; ChatHub.setBrainEnabled(true); brainGroup?.check(101); updateCards()
                refreshOffline()
                ChatHub.preloadOffline()
                return
            }
            DownloadManager.STATUS_FAILED -> {
                dlBar.visibility = View.GONE; dlCancel.visibility = View.GONE
                dlText.text = "\u274C Download failed (code ${p.reason}). Check your connection and space, then try again."
                ModelStore.clearDownloadRecord(this)
                return
            }
            else -> {
                dlBar.visibility = View.VISIBLE; dlCancel.visibility = View.VISIBLE
                val frac = if (p.total > 0) p.done.toDouble() / p.total else 0.0
                dlBar.progress = (frac * 1000).toInt()
                val state = when (p.status) {
                    DownloadManager.STATUS_PAUSED -> "Paused (waiting for network)"
                    DownloadManager.STATUS_PENDING -> "Waiting"
                    else -> "Downloading"
                }
                dlText.text = "$state: ${mb(p.done)} / ${if (p.total > 0) mb(p.total) else "?"} (${(frac * 100).toInt()}%)"
                refreshOffline()
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
        if (!name.endsWith(".litertlm")) {
            dlText.text = "\u26A0 \"$name\" isn't a .litertlm file. This app runs LiteRT-LM models (.litertlm); it may not load."
        }
        val free = ModelStore.freeBytes(this)
        if (size > 0 && free < size + 200_000_000L) { dlText.text = "Not enough space to import (${mb(size)} needed, ${mb(free)} free)."; return }
        val target = File(ModelStore.modelsDir(this), name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        dlBar.visibility = View.VISIBLE
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
                                    dlText.text = "Importing ${mb(dd)}${if (size > 0) " / " + mb(size) else ""}…"
                                    if (size > 0) dlBar.progress = (dd * 1000 / size).toInt()
                                }
                            }
                        }
                    }
                }
                target.delete(); part.renameTo(target)
                s.offlineModelPath = target.path
                ui.post { dlBar.visibility = View.GONE; dlText.text = "\u2705 Imported ${target.name}"; refreshOffline(); ChatHub.preloadOffline() }
            } catch (e: Exception) {
                ui.post { dlBar.visibility = View.GONE; dlText.text = "\u274C Import failed: ${e.message}" }
            }
        }.start()
    }

    // ---------------- auto-rest, battery saver & energy (v3.2) ----------------

    private var energyText: TextView? = null
    private val energyWindow = ArrayDeque<com.combo.monkeycompanion.overlay.EnergyMeter.Sample>()

    private fun refreshEnergy() {
        val tv = energyText ?: return
        val em = com.combo.monkeycompanion.overlay.EnergyMeter
        val now = em.sample()
        energyWindow.addLast(now)
        while (energyWindow.size > 1 && now.wallMs - energyWindow.first().wallMs > 10_000L) energyWindow.removeFirst()
        val first = energyWindow.first()
        tv.text = em.describe(if (now.wallMs - first.wallMs >= 1500L) first to now else null, now) +
            "\nMind CPU: " + com.combo.monkeycompanion.mind.MindHub.cpuSummary() +
            "\n(Estimate: ~200 mA per fully busy CPU core; screen and GPU compositing not included. The window includes this Settings screen.)"
        ui.postDelayed({ refreshEnergy() }, 2000L)
    }

    /** v10.17.1: System / Light / Dark app theme (the overlay character is never affected). */
    private fun buildAppearance(): View {
        val T = com.combo.monkeycompanion.theme.ThemeSkin
        val modes = listOf(T.SYSTEM, T.LIGHT, T.DARK)
        val sp = spinner(listOf("Follow phone (System)", "Light", "Dark"), modes.indexOf(T.mode(this)).coerceAtLeast(0)) {
            if (modes[it] != T.mode(this)) { T.setMode(this, modes[it]); recreate() }
        }
        return card("🌗 Appearance (app screens)", sp)
    }

    private fun buildRest(): View {
        val secs = listOf(30, 60, 120, 300, 600, 0)
        val names = listOf("After 30 seconds", "After 1 minute", "After 2 minutes", "After 5 minutes", "After 10 minutes", "Never")
        val restSp = spinner(names, secs.indexOf(s.restSeconds).let { if (it < 0) 2 else it }) { s.restSeconds = secs[it] }
        val saver = CheckBox(this).apply {
            text = "Battery saver (lower frame rate, fewer random moves and quips, rests within 1 minute)"; setTextColor(brown); isChecked = s.batterySaver
            setOnCheckedChangeListener { _, c -> s.batterySaver = c }
        }
        val unlock = CheckBox(this).apply {
            text = "When resting, come back out after I unlock the phone"; setTextColor(brown); isChecked = s.comeOutOnUnlock
            setOnCheckedChangeListener { _, c -> s.comeOutOnUnlock = c }
        }
        val gags = CheckBox(this).apply {
            text = "Random comedy bits (sneezes, juggling, air guitar...)"; setTextColor(brown); isChecked = s.gags
            setOnCheckedChangeListener { _, c -> s.gags = c }
        }
        val smooth = CheckBox(this).apply {
            text = "Smooth animation (beta): squishy landings, tails, ears, hair and capes that swing and settle. Turn off for the classic feel."
            setTextColor(brown); isChecked = s.smoothAnimation
            setOnCheckedChangeListener { _, c -> s.smoothAnimation = c }
        }
        val living = CheckBox(this).apply {
            text = "Living reactions (beta): every touch gets a fresh reaction (head, belly, tail, ears, rocket…), never the same one twice in a row. Turn off for the classic taps."
            setTextColor(brown); isChecked = s.livingReactions
            setOnCheckedChangeListener { _, c -> s.livingReactions = c }
        }
        val sfx = CheckBox(this).apply {
            text = "Sound effects (Smash game, fireworks). Always quiet when your phone is on silent or vibrate."
            setTextColor(brown); isChecked = s.soundEffects
            setOnCheckedChangeListener { _, c -> s.soundEffects = c }
        }
        val dtPower = CheckBox(this).apply {
            text = "Double-tap for power: double-tap a character and it fires one of its powers right away (a different one each time). Turn off to make double-tap open the chat like before."
            setTextColor(brown); isChecked = s.doubleTapPower
            setOnCheckedChangeListener { _, c -> s.doubleTapPower = c }
        }
        val chatBtn = CheckBox(this).apply {
            text = "Chat button: a small 💬 button floats beside each character. Tap it to chat."
            setTextColor(brown); isChecked = s.chatButton
            setOnCheckedChangeListener { _, c -> s.chatButton = c }
        }
        val fabMode = CheckBox(this).apply {
            text = "Chat button opens 🎤 Speak (mic) instead of 💬 Chat (type). The button icon changes to match; one tap starts it."
            setTextColor(brown); isChecked = s.fabSpeak
            setOnCheckedChangeListener { _, c -> s.fabSpeak = c }
        }
        val energy = text("Measuring…", 12f, color = soft).also { energyText = it }
        return card(
            "Rest & battery",
            text("Auto-rest: if you don't play with Settai for a while, it hides behind the nearest edge (just a tab). Tap the tab to call it back, double-tap to chat. It also pops out for low battery, overheating and timers.", 12f, color = soft),
            restSp, unlock, saver, gags, smooth, living, sfx, dtPower, chatBtn, fabMode,
            text("Energy", 13f, bold = true), energy,
            text("Changes apply within a minute.", 12f, color = soft),
        )
    }

    // ---------------- superpowers (v3.2) ----------------

    // ---------------- v4.1 character ----------------
    private lateinit var mainCol: LinearLayout
    private var powersCard: View? = null

    /** Character picker: live animated thumbnails; switching keeps memory, chat and settings. */
    private fun buildCharacter(): View {
        val reg = com.combo.monkeycompanion.character.CharacterRegistry
        val info = text("", 12f, color = soft)
        val thumbs = ArrayList<CharacterPreviewView>()
        val wardrobe = button("\uD83D\uDC57  Wardrobe…") {
            startActivity(Intent(this, WardrobeActivity::class.java).putExtra("character", reg.current.id))
        }
        fun show() {
            val c = reg.current
            thumbs.forEach { it.picked = it.who === c }
            info.text = "${c.name}: ${c.tagline}" + if (c.wardrobe.isNotEmpty()) "  (${c.wardrobe.size} wardrobe item${if (c.wardrobe.size == 1) "" else "s"})" else ""
            wardrobe.visibility = if (c.wardrobe.isEmpty()) View.GONE else View.VISIBLE
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (c in reg.allWithCustom) {
            val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) }
            val v = CharacterPreviewView(this, c).apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(118)) }
            thumbs.add(v)
            cell.addView(v); cell.addView(text(c.name, 12f, true).apply { gravity = Gravity.CENTER_HORIZONTAL })
            cell.setOnClickListener {
                if (reg.current === c) return@setOnClickListener
                s.characterId = c.id; s.applyCharacter(); show()
                // Superpowers differ per body: rebuild that card.
                powersCard?.let { old -> val i = mainCol.indexOfChild(old); if (i >= 0) { mainCol.removeViewAt(i); mainCol.addView(buildSuperpowers().also { powersCard = it }, i) } }
            }
            row.addView(cell)
        }
        show()
        val studioBtn = button("📸✨  Photo-to-Look-Alike Studio (Create / Import)…") { startActivity(Intent(this, CompanionStudioActivity::class.java)) }
        return card("Character", row, info, wardrobe, studioBtn,
            text("Switching body keeps your chats, memory, routines and settings. Each character has its own personality, lines and superpowers.", 12f, color = soft))
    }

    private fun buildSuperpowers(): View {
        val powers = com.combo.monkeycompanion.brain.Superpowers
        // Data-driven: every power in Superpowers.all shows up here.
        val ids = listOf(powers.OFF, powers.RANDOM) + powers.all.map { it.id }
        val names = listOf("Off", "Random (surprise me)") + powers.all.map { it.title }
        val blurb = text("", 12f, color = soft)
        fun describe(i: Int) {
            blurb.text = when (ids[i]) {
                powers.OFF -> "No superpowers: ${com.combo.monkeycompanion.character.CharacterRegistry.current.name} stays a regular genius ${com.combo.monkeycompanion.character.CharacterRegistry.current.noun}."
                powers.RANDOM -> "A random power each time: " + powers.all.joinToString("; ") { "${it.title}: ${it.blurb}" } + "."
                else -> powers.byId(ids[i])?.blurb ?: ""
            }
        }
        val sel = ids.indexOf(s.power).let { if (it < 0) 1 else it }
        val sp = spinner(names, sel) { s.power = ids[it]; describe(it) }
        describe(sel)
        val status = text("", 12f, color = soft)
        val use = button("\uD83D\uDE80  Use power now") {
            if (!com.combo.monkeycompanion.overlay.MonkeyOverlayService.running) { status.text = "Start Settai first (overlay mode), then try again."; return@button }
            startService(Intent(this, com.combo.monkeycompanion.overlay.MonkeyOverlayService::class.java)
                .setAction(com.combo.monkeycompanion.overlay.MonkeyOverlayService.ACTION_POWER))
            status.text = if (s.power == powers.OFF) "Powers are Off: Settai will tell you so." else "Look at your screen!"
        }
        return card(
            "Superpowers",
            sp, blurb, use, status,
            text("Settai also uses its power when you ask in chat (\"fly!\", \"show your power\") and now and then on its own while you're around (never with Battery saver on or while resting). Flights last about 6-9 seconds; the extra effects only run during the flight.", 12f, color = soft),
        )
    }

    // ---------------- personality & memory ----------------

    private fun buildPersonality(): View {
        val indepLabel = text("How independent: ${s.mindIndependence}%", 14f, bold = true)
        val indepSeek = android.widget.SeekBar(this).apply {
            max = 100; progress = s.mindIndependence
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                    s.mindIndependence = p
                    indepLabel.text = "How independent: $p%"
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
            })
        }
        val diaryBtn = button("📖  Open Settai's diary…") {
            startActivity(Intent(this, DiaryActivity::class.java))
        }
        val quips = CheckBox(this).apply {
            text = "Let Settai say random AI quips on its own"; setTextColor(brown); isChecked = s.quipsEnabled
            setOnCheckedChangeListener { _, c -> s.quipsEnabled = c }
        }
        val mins = listOf(5, 10, 15, 30, 60)
        val quipSp = spinner(mins.map { "About every $it minutes" }, mins.indexOf(s.quipMinutes).coerceAtLeast(2)) { s.quipMinutes = mins[it] }
        val turns = listOf(0, 4, 8, 15)
        val memSp = spinner(turns.map { if (it == 0) "Remember nothing" else "Remember last $it exchanges" }, turns.indexOf(s.memoryTurns).coerceAtLeast(2)) { s.memoryTurns = turns[it] }
        val cleared = text("", 12f, color = soft)
        val roam = CheckBox(this).apply {
            text = "Let Settai roam (climb the sides, hang from the top, drop down)"; setTextColor(brown); isChecked = s.roam
            setOnCheckedChangeListener { _, c -> s.roam = c }
        }
        return card(
            "Personality & memory",
            indepLabel, indepSeek,
            text("Controls how often Settai acts on its own (exploring new apps, habits, gags, powers) and how strongly its traits show. High focus (typing, calls, meetings, banking, DND, quiet hours) always overrides.", 12f, color = soft),
            diaryBtn,
            roam,
            text("Off: Settai only walks along the edge it's on and stays where you drop it (it still grabs an edge if you drop it near one).", 12f, color = soft),
            quips, quipSp,
            text("Quips only happen when a real brain is set up (they use a little data/battery).", 12f, color = soft),
            text("Conversation memory", 13f, color = soft), memSp,
            button("Clear chat history") { ChatHub.clearMemory(); cleared.text = "Forgotten! 🍌" }, cleared,
        )
    }

    /** v4.2: proactive buddy, quick-tap arc, hold-to-talk and spoken replies. */
    private fun buildBuddy(): View {
        val levels = com.combo.monkeycompanion.agent.ProLevel.entries
        val levelHelp = text(com.combo.monkeycompanion.agent.ProLevel.of(s.proactiveLevel).help, 12f, color = soft)
        val levelSp = spinner(levels.map { "Proactive: ${it.label}" + if (it.perHour > 0) " (max ${it.perHour}/hour)" else "" }, levels.indexOf(com.combo.monkeycompanion.agent.ProLevel.of(s.proactiveLevel))) {
            s.proactiveLevel = levels[it].name; levelHelp.text = levels[it].help
        }
        val QH = com.combo.monkeycompanion.agent.QuietHours
        val qStart = field("23:00").apply { setText(QH.fmt(s.quietStart)) }
        val qEnd = field("07:00").apply { setText(QH.fmt(s.quietEnd)) }
        val qRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(text("Quiet hours ", 14f)); addView(qStart, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(text(" to ", 14f)); addView(qEnd, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        val qSave = button("Save quiet hours") {
            s.quietStart = QH.parse(qStart.text.toString(), s.quietStart); s.quietEnd = QH.parse(qEnd.text.toString(), s.quietEnd)
            qStart.setText(QH.fmt(s.quietStart)); qEnd.setText(QH.fmt(s.quietEnd))
        }
        val cats = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (c in com.combo.monkeycompanion.agent.ProCat.entries) cats.addView(CheckBox(this).apply {
            text = c.label + (if (c.minLevel.ordinal > com.combo.monkeycompanion.agent.ProLevel.CHILL.ordinal) " (Buddy+)" else ""); setTextColor(brown); isChecked = s.proactiveCat(c)
            setOnCheckedChangeListener { _, on -> s.setProactiveCat(c, on) }
        })
        val usage = field("com.instagram.android, com.google.android.youtube").apply { setText(s.usageApps) }
        val usageMins = listOf(15, 20, 30, 45, 60, 90)
        val usageSp = spinner(usageMins.map { "Break nudge after $it min in one go" }, usageMins.indexOf(s.usageMinutes).coerceAtLeast(2)) { s.usageMinutes = usageMins[it] }
        val usageSave = button("Save break-nudge apps") { s.usageApps = usage.text.toString() }

        val ents = listOf("off", "sometimes", "often")
        val entSp = spinner(listOf("Power entrances: Off", "Power entrances: Sometimes (~30% of pipe-ups)", "Power entrances: Often (~60%)"),
            ents.indexOf(s.powerEntrances).coerceAtLeast(1)) { s.powerEntrances = ents[it] }
        val speakAll = CheckBox(this).apply {
            text = "Speak all replies out loud"; setTextColor(brown); isChecked = s.speakReplies
            setOnCheckedChangeListener { _, c -> s.speakReplies = c }
        }
        val speakVoice = CheckBox(this).apply {
            text = "Speak the answer when I asked by voice"; setTextColor(brown); isChecked = s.speakVoiceReplies
            setOnCheckedChangeListener { _, c -> s.speakVoiceReplies = c }
        }
        val tos = listOf(30, 60, 120, 300, 0)
        val toSp = spinner(listOf("Close the bubble chat after 30 s idle", "… after 1 min idle", "… after 2 min idle (default)", "… after 5 min idle", "Never close it by itself"),
            tos.indexOf(s.chatTimeoutSecs).let { if (it < 0) 2 else it }) { s.chatTimeoutSecs = tos[it] }
        val menuBtn = button("Long-press menu: choose & order items…") {
            startActivity(android.content.Intent(this, MenuSettingsActivity::class.java))
        }.apply { visibility = android.view.View.GONE }   // v10.13.1: the popup is 8 fixed groups now; arranging items no longer applies
        val moments = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            for (m in com.combo.monkeycompanion.brain.PhoneMoments.Moment.entries) addView(CheckBox(this@SettingsActivity).apply {
                text = m.label; setTextColor(brown); isChecked = s.phoneMoment(m.id); minimumHeight = dp(44)
                setOnCheckedChangeListener { _, c -> s.setPhoneMoment(m.id, c) }
            })
        }
        return card(
            "Buddy mode: pipe-ups, gestures & voice",
            text("Settai pipes up for moments that matter (a burst of messages, someone important, a delivery, an OTP, a long scroll session, full battery, good morning). It never pipes up while you type, call, game or watch fullscreen, in Do Not Disturb (mouth cloth) or in quiet hours. Lines are templated, so no battery-hungry AI unless you tap a chip.", 12f, color = soft),
            levelSp, levelHelp, qRow, qSave,
            text("What may it pipe up about?", 13f, color = soft), cats,
            text("Messages / bursts / important people need the Notification reader power; chat-list digests need the Screen agent power. OTPs are shown only to you (never spoken or sent to a brain).", 12f, color = soft),
            usageSp, usage, usageSave,
            entSp,
            text("Power entrances: message summaries fly in (cape), important ones arrive with a power strike, jokes come in on rockets / jets, each character with its own power. Never with battery saver or with superpowers Off; still counted in the hourly limit.", 12f, color = soft),
            text("Gestures (every character): Tap = tickle (head, belly, hands, tail, feet react) · Double-tap = chat · Long-press = menu (Chat · Talk · Missed? · Screen · ⚡ Powers · … More, customisable below) · Drag = move · Fling to an edge = hide. 🎤 Talk stops by itself when you pause, or tap Settai to send. The mic is also in the chat panels.", 12f, color = soft),
            text("Phone moments", 15f, bold = true),
            text("Little reactions when your phone does something: plug in the charger and ${'$'}{com.combo.monkeycompanion.character.CharacterRegistry.current.name} zaps the battery icon (each character in their own way). Never in Do Not Disturb, quiet hours, calls or fullscreen; each has a cooldown; battery saver plays a short version without effects.", 12f, color = soft),
            moments,
            menuBtn,
            text("Bubble chat: close it with ✕, a swipe, a tap outside or Back. It never closes while you type or while I'm answering.", 12f, color = soft),
            toSp,
            speakVoice, speakAll,
        )
    }

    /** v5: one on/off per big feature; the characters decide the rest. */
    private fun buildFun5(): View {
        val S = com.combo.monkeycompanion.fun5.Fun5Store
        val views = ArrayList<View>()
        views += text("Games, clips, two phones and the friendship level are always there (long-press › More, or the app home). These switches cover the things Settai does by itself.", 12f, color = soft)
        for (f in com.combo.monkeycompanion.fun5.Fun5Store.Feature.entries) {
            views += CheckBox(this).apply {
                text = f.title; setTextColor(brown); isChecked = S.on(this@SettingsActivity, f)
                setOnCheckedChangeListener { _, c -> S.set(this@SettingsActivity, f, c) }
            }
            views += text(f.help, 11.5f, color = soft)
        }
        views += text("Weather uses your coarse location only if you already allowed location for Settai, and asks Open-Meteo at most every 3 hours. Cricket scores are fetched only when you ask (\"score enna?\").", 11.5f, color = soft)
        return card("v5 fun: things Settai does by itself", *views.toTypedArray())
    }

    private fun buildPowers(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (g in CapGroup.entries) {
            val n = Capabilities.all.count { it.group == g }
            box.addView(CheckBox(this).apply {
                text = "${g.label} ($n)"
                setTextColor(brown); isChecked = s.groupEnabled(g)
                setOnCheckedChangeListener { _, c -> s.setGroupEnabled(g, c) }
            })
        }
        val modes = listOf("auto", "clock", "settai")
        val clockSp = spinner(listOf("Timers & alarms: Clock app when possible, else Settai's own", "Timers & alarms: always the Clock app", "Timers & alarms: always Settai's own (rings with Settai)"),
            modes.indexOf(s.clockMode).coerceAtLeast(0)) { s.clockMode = modes[it] }
        return card(
            "Phone powers",
            text("What Settai may do, for every brain. Anything that opens or changes something shows a Yes/No question first.", 12f, color = soft),
            button("🛠️  Settai's skills (errands, senses & daily habits)…") { startActivity(android.content.Intent(this, SkillsActivity::class.java)) },
            button("What can Settai do? (powers & permissions)") { startActivity(android.content.Intent(this, CapabilitiesActivity::class.java)) },
            box,
            clockSp,
            button("Recent actions log") {
                android.app.AlertDialog.Builder(this).setTitle("Recent actions")
                    .setMessage(ActionLog.format(this).ifBlank { "Nothing yet." })
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Clear") { _, _ -> ActionLog.clear(this) }.show()
            },
        )
    }

    companion object { private const val REQ_PICK = 31 }
}

/**
 * v9.5 Reusable "🧠 Give Brain" Quick-Setup Dialog & Highlighted Card Builder!
 * Exposed on Home, Character Creation (CompanionStudioActivity), and Settings so the AI model is
 * strictly OFF by default and only used when the user explicitly enables & configures "Give Brain".
 */
object GiveBrainDialog {
    fun show(
        act: Activity,
        onChanged: (() -> Unit)? = null,
        onRunAiPhotoModel: (() -> Unit)? = null,
    ) {
        ChatHub.init(act)
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

        val providers = Provider.entries
        var curProvider = s.provider
        root.addView(TextView(act).apply {
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
        root.addView(sp)
        root.addView(keyInput)
        root.addView(hintTv)
        root.addView(TextView(act).apply {
            text = "Model Name:"
            textSize = 12.5f; setTypeface(typeface, Typeface.BOLD); setTextColor(soft)
        })
        root.addView(modelInput)

        val testResultTv = TextView(act).apply {
            textSize = 12.5f; setTextColor(soft); setPadding(0, dp(6), 0, dp(4))
        }
        root.addView(Button(act).apply {
            text = "⚡ Save Key, Enable Brain & Test Connection"
            isAllCaps = false; textSize = 13.5f; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(purple) }
            setOnClickListener {
                val k = keyInput.text?.toString()?.trim().orEmpty()
                if (k.isNotEmpty()) s.setApiKeyFor(curProvider, k)
                val m = modelInput.text?.toString()?.trim().orEmpty().ifBlank { curProvider.defaultModel }
                s.setModelFor(curProvider, m)
                s.provider = curProvider
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
        root.addView(testResultTv)

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
            .setTitle("🧠 Give Brain to Settai (AI Opt-In)")
            .setView(ScrollView(act).apply { addView(root) })
            .setNeutralButton("Full Brain Settings") { _, _ ->
                act.startActivity(Intent(act, SettingsActivity::class.java))
            }
            .setPositiveButton("Done") { _, _ ->
                val k = keyInput.text?.toString()?.trim().orEmpty()
                if (k.isNotEmpty()) {
                    s.setApiKeyFor(curProvider, k)
                    s.provider = curProvider
                    s.brainType = BrainType.ONLINE
                    ChatHub.setBrainEnabled(true)
                }
                onChanged?.invoke()
            }
            .show()
    }
}
