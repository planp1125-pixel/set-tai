package com.combo.monkeycompanion.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File

/**
 * On-device brain using Google LiteRT-LM (.litertlm models such as Gemma 4 E2B,
 * Gemma 3 1B or Qwen3 0.6B). Loading takes a few seconds and is done lazily on the
 * calling (background) thread; the engine is reused between messages.
 */
class OfflineBrain(
    private val modelPath: String,
    private val useGpu: Boolean,
    private val cacheDir: String,
    private val allowTools: Boolean,
) : Brain {
    override val label = "On-device: " + (ModelStore.CATALOG.firstOrNull { it.file == File(modelPath).name }?.title?.substringBefore(" (") ?: File(modelPath).name)
    override val supportsTools get() = allowTools

    private var engine: Engine? = null
    @Volatile private var active: Conversation? = null
    private val lock = Any()

    /** v10.17.2: which backend actually loaded ("GPU" / "CPU"), or null before the first load. */
    @Volatile var loadedOn: String? = null; private set
    /** v10.17.2: why the GPU didn't load, when we fell back to CPU (shown in Settings). */
    @Volatile var gpuError: String? = null; private set
    /** v10.17.2: set once the GPU failed while replying; from then on this brain uses the CPU. */
    @Volatile private var forceCpu = false
    @Volatile private var cancelled = false

    private fun tryLoad(gpu: Boolean): Engine {
        val cfg = EngineConfig(
            modelPath = modelPath,
            backend = if (gpu) Backend.GPU() else Backend.CPU(),
            maxNumTokens = contextTokens(),
            cacheDir = cacheDir,
        )
        val e = Engine(cfg)
        try { e.initialize() } catch (t: Throwable) { runCatching { e.close() }; throw t }
        return e
    }

    private fun engine(): Engine = synchronized(lock) {
        engine?.let { return it }
        if (!File(modelPath).exists()) throw BrainException("Model file is missing. Download or import it again in Settings.")
        // v10.17.2: GPU first when asked; if this phone's GPU can't run it, quietly use the CPU instead of failing.
        if (useGpu && !forceCpu) {
            try {
                val e = tryLoad(gpu = true)
                engine = e; loadedOn = "GPU"; gpuError = null
                return e
            } catch (t: Throwable) {
                gpuError = t.message ?: t.javaClass.simpleName
                android.util.Log.w("OfflineBrain", "GPU load failed, falling back to CPU", t)
            }
        }
        val e = try { tryLoad(gpu = false) } catch (t: Throwable) {
            throw BrainException(LOAD_FAIL + " (${t.message ?: t.javaClass.simpleName}). Is it a .litertlm file?")
        }
        engine = e; loadedOn = "CPU"
        e
    }

    /**
     * Context size: as big as the model file was built for (e.g. "..._ekv1280.litertlm" -> 1280),
     * otherwise 4096, so the system prompt + a few turns + the reply (with its action tag) fit.
     */
    private fun contextTokens(): Int {
        return com.combo.monkeycompanion.brain.PromptBudget.contextFromName(File(modelPath).name).coerceIn(512, 4096)
    }

    /** Warm up in the background so the first reply is quicker. */
    fun preload() = runCatching { engine() }

    override fun cancel() { cancelled = true; runCatching { active?.cancelProcess() } }

    override fun chat(system: String, history: List<ChatMsg>, user: String, tools: ToolHost?, onText: (String) -> Unit): String {
        cancelled = false
        try {
            return chatFitted(system, history, user, tools, onText)
        } catch (t: BrainException) {
            if (cancelled) throw t   // the user pressed Stop: not a GPU problem
            // v10.17.2: the GPU loaded but failed while replying -> drop it, switch this brain to CPU, try once more.
            if (loadedOn != "GPU" || forceCpu) throw t
            android.util.Log.w("OfflineBrain", "GPU reply failed, retrying on CPU", t)
            gpuError = t.message
            forceCpu = true
            synchronized(lock) { runCatching { engine?.close() }; engine = null; loadedOn = null }
            return chatFitted(system, history, user, tools, onText)
        }
    }

    private fun chatFitted(system: String, history: List<ChatMsg>, user: String, tools: ToolHost?, onText: (String) -> Unit): String {
        // v10.13.1: fit the prompt into this model's context (e.g. 1280 for Qwen3 0.6B); retry minimal on overflow;
        // never show the raw runtime error.
        val B = com.combo.monkeycompanion.brain.PromptBudget
        val ctx = contextTokens()
        val turns = history.map { com.combo.monkeycompanion.brain.PromptBudget.Turn(it.fromUser, it.text) }
        for (minimal in listOf(false, true)) {
            val f = B.fit(system, turns, user, ctx, minimal)
            try {
                return chatRaw(f.system, f.history.map { ChatMsg(it.fromUser, it.text) }, f.user, tools, onText)
            } catch (t: Throwable) {
                // v10.17.2: loading problems keep their real message (it used to show only "hiccuped").
                if (t is BrainException && (t.message?.startsWith("Model file") == true || t.message?.startsWith(LOAD_FAIL) == true)) throw t
                if (minimal || !B.isTooLong(t.message)) {
                    android.util.Log.w("OfflineBrain", "chat failed", t)
                    throw BrainException(if (B.isTooLong(t.message)) B.FRIENDLY else "My on-device brain hiccuped 🙈 Try again?")
                }
            }
        }
        throw BrainException(B.FRIENDLY)
    }

    private fun chatRaw(system: String, history: List<ChatMsg>, user: String, tools: ToolHost?, onText: (String) -> Unit): String {
        val e = engine()
        val initial = history.map { if (it.fromUser) Message.user(it.text) else Message.model(it.text) }
        val providers: List<ToolProvider> =
            if (allowTools && tools != null) tools.specs.map { spec -> tool(SpecTool(spec, tools)) } else emptyList()
        val cfg = ConversationConfig(
            systemInstruction = Contents.of(system),
            initialMessages = initial,
            tools = providers,
            samplerConfig = SamplerConfig(40, 0.95, 0.8, 0),
            automaticToolCalling = true,
        )
        val sb = StringBuilder()
        synchronized(lock) {
            e.createConversation(cfg).use { conv ->
                active = conv
                try {
                    runBlocking {
                        conv.sendMessageAsync(user).collect { msg ->
                            val piece = msg.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                            if (piece.isEmpty()) return@collect
                            // Chunks are deltas; guard against runtimes that resend the whole text.
                            if (sb.isNotEmpty() && piece.length > sb.length && piece.startsWith(sb)) { sb.setLength(0) }
                            sb.append(piece)
                            onText(cleanReply(sb.toString()))
                        }
                    }
                } finally { active = null }
            }
        }
        return cleanReply(sb.toString())
    }

    override fun close() {
        // v10.13.1: never block the caller (often the UI thread) while a reply is still being generated
        cancel()
        Thread({ synchronized(lock) { runCatching { engine?.close() }; engine = null } }, "offline-brain-close").start()
    }

    companion object {
        const val LOAD_FAIL = "Couldn't load the on-device model"
    }

    /** Bridges our ToolSpec to LiteRT-LM's OpenAPI-style tool interface. */
    private class SpecTool(private val spec: ToolSpec, private val host: ToolHost) : OpenApiTool {
        override fun getToolDescriptionJsonString(): String =
            JSONObject().put("name", spec.name).put("description", spec.description).put("parameters", spec.parameters).toString()

        override fun execute(paramsJsonString: String): String = host.call(spec.name, paramsJsonString)
    }
}
