package com.combo.settai.desktop

import com.combo.monkeycompanion.ai.*
import org.json.JSONArray
import org.json.JSONObject
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import javax.swing.SwingUtilities

class PendingAction(val question: String, val doneText: String, val run: () -> Unit)

/** Chat brain for the desktop: same Persona, tag protocol and do_action tool as the phone. */
class DesktopChat(private val cfg: Config) {
    interface Listener {
        fun onThinking() {}
        fun onReply(text: String, done: Boolean) {}
        fun onAction(a: BodyAction) {}
        fun onPending(p: PendingAction?) {}
        fun onAnnounce(text: String) {}
    }

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<Listener>()
    var listener: Listener?
        get() = listeners.firstOrNull()
        set(v) { if (v != null) listeners.addIfAbsent(v) }
    fun addListener(l: Listener) { listeners.addIfAbsent(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }
    private fun fire(f: (Listener) -> Unit) = listeners.forEach(f)
    /** Chat log for the history window. */
    fun history(): List<ChatMsg> = synchronized(memory) { memory.toList() }
    var bodyState: () -> String = { "" }
    var sys: () -> SysState = { SysState() }

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "settai-brain").apply { isDaemon = true } }
    private val memFile = File(cfg.dir, "chat.json")
    private val memory = ArrayList<ChatMsg>()
    @Volatile var busy = false; private set
    @Volatile var pending: PendingAction? = null; private set
    private var brain: Brain? = null
    private var sig = ""
    @Volatile private var toolActed = false

    init {
        runCatching {
            val a = JSONArray(memFile.readText())
            for (i in 0 until a.length()) a.getJSONObject(i).let { memory.add(ChatMsg(it.getBoolean("u"), it.getString("t"), it.optLong("w"), it.optBoolean("n"))) }
        }
    }

    private fun ui(f: () -> Unit) = SwingUtilities.invokeLater(f)
    private fun saveMemory() = runCatching {
        val a = JSONArray(); synchronized(memory) { memory.takeLast(60).forEach { a.put(JSONObject().put("u", it.fromUser).put("t", it.text).put("w", it.time).put("n", it.note)) } }
        memFile.writeText(a.toString())
        runCatching { java.nio.file.Files.setPosixFilePermissions(memFile.toPath(), java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")) }
    }
    fun clearMemory() { synchronized(memory) { memory.clear() }; saveMemory() }

    val isLocal get() = cfg.brain == "ollama"

    @Synchronized fun brain(): Brain {
        val s = listOf(cfg.brain, cfg.ollamaUrl, cfg.ollamaModel, cfg.provider, cfg.key(cfg.provider), cfg.model(cfg.provider), cfg.customUrl).joinToString("|")
        brain?.let { if (s == sig) return it }
        brain?.close()
        val b = when (cfg.brain) {
            "ollama" -> if (cfg.ollamaModel.isBlank()) CannedBrain("My local brain needs a model. Right-click me → Settings → Pull recommended model (${Config.RECOMMENDED_MODEL}).")
                else OpenAiCompatBrain("Ollama (${cfg.ollamaModel})", cfg.ollamaUrl.trimEnd('/') + "/v1", "ollama", cfg.ollamaModel)
            "online" -> {
                val pr = Providers.byId(cfg.provider); val key = cfg.key(pr.id); val model = cfg.model(pr.id)
                when {
                    pr.id != "CUSTOM" && key.isBlank() -> CannedBrain("My online brain needs an API key. Right-click me → Settings!")
                    pr.id == "CUSTOM" && cfg.customUrl.isBlank() -> CannedBrain("Set the custom server URL in Settings first!")
                    model.isBlank() -> CannedBrain("Pick a model name in Settings!")
                    pr.id == "ANTHROPIC" -> AnthropicBrain(key, model)
                    else -> OpenAiCompatBrain("${pr.label} ($model)", if (pr.id == "CUSTOM") cfg.customUrl else pr.baseUrl, key, model)
                }
            }
            else -> CannedBrain("I don't have a brain yet! Right-click me → Settings to give me one.")
        }
        brain = b; sig = s
        return b
    }

    // ---------- tools (online brains; local models use tags only) ----------
    private fun schema(vararg props: Pair<String, String>) = JSONObject().put("type", "object").put("properties", JSONObject().apply {
        props.forEach { put(it.first, JSONObject().put("type", "string").put("description", it.second)) }
    }).put("required", JSONArray(props.map { it.first }))

    private val specs = listOf(
        ToolSpec("do_action", "Move your monkey body. Use when your human asks you to move, or to express yourself.",
            JSONObject().put("type", "object").put("properties", JSONObject().put("action",
                JSONObject().put("type", "string").put("enum", JSONArray(BodyAction.entries.map { it.tag })))).put("required", JSONArray().put("action"))),
        ToolSpec("get_time", "Current local date and time on the laptop.", JSONObject().put("type", "object").put("properties", JSONObject())),
        ToolSpec("get_system_status", "Laptop status: CPU temperature, RAM, disk, battery, busiest app, Do Not Disturb.", JSONObject().put("type", "object").put("properties", JSONObject())),
        ToolSpec("web_search", "Open a web search in the browser (asks your human to confirm first).", schema("query" to "What to search for")),
        ToolSpec("open_app", "Open an installed desktop app by name (asks your human to confirm first).", schema("name" to "App name, e.g. Firefox, Files, Terminal")),
    )

    private val tools = object : ToolHost {
        override val specs get() = this@DesktopChat.specs
        override fun call(name: String, argsJson: String): String {
            val a = runCatching { JSONObject(argsJson) }.getOrDefault(JSONObject())
            return when (name) {
                "do_action" -> {
                    val act = a.optString("action").let { BodyAction.fromWord(it) ?: BodyAction.entries.firstOrNull { e -> e.tag == it.lowercase() } }
                        ?: return "unknown action; use one of " + BodyAction.entries.joinToString { it.tag }
                    toolActed = true; ui { fire { it.onAction(act) } }; "done: you are doing '${act.tag}' now"
                }
                "get_time" -> SimpleDateFormat("EEEE d MMMM yyyy, h:mm a z", Locale.getDefault()).format(Date())
                "get_system_status" -> describe(sys())
                "web_search" -> {
                    val q = a.optString("query").trim().ifEmpty { return "need a query" }
                    ask(PendingAction("Search the web for “$q”?", "Opened your browser 🔍") { browse("https://duckduckgo.com/?q=" + URLEncoder.encode(q, "UTF-8")) })
                }
                "open_app" -> {
                    val n = a.optString("name").trim().ifEmpty { return "need a name" }
                    val app = Platform.current.findApp(n) ?: return "No installed app matches '$n'."
                    ask(PendingAction("Open ${app.second}?", "Opened ${app.second}! 🚀") { Platform.current.launchApp(app.first) })
                }
                else -> "unknown tool"
            }
        }
    }

    private fun ask(p: PendingAction): String {
        ui { pending = p; fire { it.onPending(p) } }
        return "Asked your human to confirm with the Yes/No buttons in your bubble. Tell them briefly; it hasn't happened yet."
    }

    fun confirmPending(yes: Boolean) {
        val a = pending ?: return
        pending = null; fire { it.onPending(null) }
        val text = if (!yes) "Okay, I won't. 🙈" else runCatching { a.run() }.fold({ a.doneText }, { "Hmm, that didn't work: ${it.message}" })
        synchronized(memory) { memory.add(ChatMsg(false, text, note = true)) }; saveMemory()
        fire { it.onAnnounce(text) }
    }

    private fun act(raw: String, userText: String?): String {
        val p = ActionParser.parse(raw)
        val acts = p.actions.take(2).toMutableList()
        if (acts.isEmpty() && !toolActed && userText != null) ActionParser.requestedByUser(userText)?.let { acts.add(it) }
        if (acts.isNotEmpty()) ui { acts.forEach { a -> fire { it.onAction(a) } } }
        return p.text
    }

    fun send(text: String, replyingTo: String? = null) {
        val typed = text.trim()
        if (typed.isEmpty() || busy) return
        busy = true
        val local = isLocal
        val history: List<ChatMsg>
        val said = replyingTo?.trim()?.takeIf { it.isNotEmpty() && synchronized(memory) { memory.lastOrNull { m -> !m.fromUser }?.text?.trim() } != it }
        synchronized(memory) {
            history = memory.filter { it.text.isNotBlank() }.takeLast(if (local) 8 else 16)
            if (said != null) memory.add(ChatMsg(false, said, note = true))
            memory.add(ChatMsg(true, typed))
        }
        val msg = if (said != null) "(You had just said to me: \"$said\")\n$typed" else typed
        fire { it.onThinking() }
        worker.execute {
            var last = ""
            try {
                val b = brain()
                val th = if (b.supportsTools && !local) tools else null
                toolActed = false
                val sysPrompt = Persona.systemPrompt(th != null, bodyNow(), compact = local, actionTool = th != null)
                val reply = b.chat(sysPrompt, history, msg, th) { raw ->
                    val t = ActionParser.visible(raw); last = t
                    ui { fire { it.onReply(t, false) } }
                }
                val shown = act(reply.ifBlank { last }, typed).ifBlank { if (reply.isNotBlank()) "Ta-da!" else "" }
                    .ifBlank { "Ooh ooh... I lost my train of thought. Try again?" }
                synchronized(memory) { memory.add(ChatMsg(false, shown)) }; saveMemory()
                ui { busy = false; fire { it.onReply(shown, true) } }
            } catch (t: Throwable) {
                // Still honour "dance!" etc. when the brain is down.
                ActionParser.requestedByUser(typed)?.let { a -> ui { fire { it.onAction(a) } } }
                val m = friendly(t)
                val shown = if (last.isNotBlank()) "$last …" else "Oops, my brain slipped on a banana peel: $m"
                synchronized(memory) { memory.add(ChatMsg(false, shown)) }; saveMemory()
                ui { busy = false; fire { it.onReply(shown, true) } }
            }
        }
    }

    private fun friendly(t: Throwable): String {
        val m = t.message ?: t.javaClass.simpleName
        return when {
            isLocal && (t is java.net.ConnectException || m.contains("Failed to connect", true)) -> "I can't reach Ollama at ${cfg.ollamaUrl}. Is it running? (systemctl status ollama)"
            isLocal && m.contains("not found", true) -> "Ollama doesn't have “${cfg.ollamaModel}”. Right-click me → Settings → Pull it."
            else -> m
        }
    }

    fun cancel() { runCatching { brain?.cancel() } }

    fun quip(context: String, cb: (String?) -> Unit) {
        if (busy) return cb(null)
        worker.execute {
            val r = runCatching {
                val b = brain()
                if (!b.isReal) null else act(cleanReply(b.chat(Persona.systemPrompt(false, bodyNow(), compact = isLocal), emptyList(),
                    Persona.QUIP_REQUEST + context, null) {}), null).takeIf { it.isNotBlank() && it.length < 200 }
            }.getOrNull()
            ui { cb(r) }
        }
    }

    fun test(cb: (Result<String>) -> Unit) {
        worker.execute {
            val r = runCatching { synchronized(this) { sig = "" }; ActionParser.parse(brain().chat(Persona.systemPrompt(false), emptyList(), "Say hello to me in under 12 words.", null) {}).text }
            ui { cb(r) }
        }
    }

    private fun bodyNow() = runCatching { bodyState() }.getOrDefault("")

    companion object {
        fun describe(s: SysState): String = buildList {
            if (!s.cpuTemp.isNaN()) add(if (s.tempEstimated) "CPU load %.0f%% (no temperature sensor)".format(s.load) else "CPU ${s.cpuTemp.toInt()}°C")
            if (s.ramUsedPct >= 0) add("RAM ${s.ramUsedPct}% used")
            if (s.diskFreeGb >= 0) add("disk %.0f GB free".format(s.diskFreeGb))
            if (s.battery >= 0) add("battery ${s.battery}%" + if (s.charging) " (charging)" else "")
            s.topProcess?.let { add("busiest app: $it (${s.topCpu.toInt()}% CPU)") }
            add("Do Not Disturb " + if (s.dnd) "on" else "off")
        }.joinToString(", ")

        fun browse(url: String) {
            Platform.current.browse(url)
        }
    }
}
