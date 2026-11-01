package com.combo.monkeycompanion.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.combo.monkeycompanion.agent.ScreenTalk
import com.combo.monkeycompanion.powers.PendingAction
import com.combo.monkeycompanion.powers.PhonePowers
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Central chat coordinator shared by the overlay monkey, the floating chat panel and
 * the in-app chat screen. Runs the brain on a background thread and reports to
 * listeners on the main thread.
 */
object ChatHub {
    interface Listener {
        fun onThinking() {}
        /** Streaming text so far; [done] on the final call. */
        fun onReply(text: String, done: Boolean) {}
        fun onError(message: String) {}
        fun onPending(action: PendingAction?) {}
        fun onHistoryChanged() {}
        /** Something to say that isn't part of the conversation (quips, confirmations). */
        fun onAnnounce(text: String) {}
        /** The AI (or the user's request) wants Settai's body to do something. */
        fun onAction(action: BodyAction) {}
        /** A timer/alarm went off: wake up if asleep. */
        fun onAlert(text: String) {}
        /** A visual gag after a phone power (sunglasses, paws over ears, yawn). */
        fun onProp(prop: com.combo.monkeycompanion.powers.Prop) {}
        /** v4.2: quick-reply chips to show under the last reply (empty = none). */
        fun onChips(chips: List<com.combo.monkeycompanion.agent.QuickChip>) {}
        /** v4.2: a proactive pipe-up (the bubble shows it with its chips, if the moment is right). */
        fun onPipe(pipe: com.combo.monkeycompanion.agent.Pipe) {}
        /** v4.2: a chip only the overlay can do (do something funny, talk, quit, open the bubble chat). */
        fun onLocalChip(chip: com.combo.monkeycompanion.agent.QuickChip) {}
    }

    /** v5: set by the overlay; true = it handled the message (games, clips). */
    @Volatile var localIntent: ((String) -> Boolean)? = null

    private fun quickLocal(typed: String, reply: String) {
        memory.add(ChatMsg(true, typed)); memory.add(ChatMsg(false, reply)); memory.commit()
        fire { it.onHistoryChanged() }
    }

    /** v5 "score enna?": one fetch of the public Cricinfo live-score RSS (no key, no polling), then react. */
    private fun cricket(typed: String) {
        busy = true; yieldToHuman()
        memory.add(ChatMsg(true, typed)); fire { it.onHistoryChanged() }; fire { it.onThinking() }
        worker.execute {
            val C = com.combo.monkeycompanion.v5.Cricket
            val (text, feel) = runCatching {
                val xml = com.combo.monkeycompanion.agent.Web.get(C.FEED)
                val m = C.pick(C.parse(xml))
                val fresh = C.pubDate(xml)?.let { System.currentTimeMillis() - it < C.STALE_MS } ?: false
                C.reply(m, fresh) to (if (m != null && fresh) C.feeling(m) else com.combo.monkeycompanion.v5.Cricket.Feeling.NEUTRAL)
            }.getOrElse { "Couldn't reach the live score feed right now 🏏📵" to com.combo.monkeycompanion.v5.Cricket.Feeling.NEUTRAL }
            com.combo.monkeycompanion.powers.ActionLog.add(app, "Cricket score", text)
            main.post {
                busy = false
                memory.add(ChatMsg(false, text)); memory.commit()
                fire { it.onHistoryChanged() }; fire { it.onReply(text, true) }
                when (feel) {
                    com.combo.monkeycompanion.v5.Cricket.Feeling.CELEBRATE -> fire { it.onAction(BodyAction.DANCE) }
                    com.combo.monkeycompanion.v5.Cricket.Feeling.CRY -> fire { it.onAction(BodyAction.SAD) }
                    com.combo.monkeycompanion.v5.Cricket.Feeling.NERVOUS -> fire { it.onAction(BodyAction.THINK) }
                    else -> Unit
                }
            }
        }
    }

    /** Live description of Settai's body for the system prompt; set by whichever view hosts Settai. */
    @Volatile var bodyState: () -> String = { "" }

    private val actionSpec = ToolSpec(
        "do_action",
        "Move your monkey body. Use when your human asks you to move, or to express yourself.",
        org.json.JSONObject().put("type", "object").put("properties", org.json.JSONObject().put("action",
            org.json.JSONObject().put("type", "string").put("enum", org.json.JSONArray(BodyAction.entries.map { it.tag }))
                .put("description", "What to do"))).put("required", org.json.JSONArray().put("action"))
    )
    @Volatile private var toolActed = false

    /** Phone powers + the do_action body tool. */
    private val tools = object : ToolHost {
        override val specs: List<ToolSpec> get() = powers.specs + actionSpec
        override fun call(name: String, argsJson: String): String {
            if (name != actionSpec.name) return powers.call(name, argsJson)
            val a = runCatching { org.json.JSONObject(argsJson).optString("action") }.getOrDefault("")
                .let { BodyAction.fromWord(it) ?: BodyAction.entries.firstOrNull { e -> e.tag == it.lowercase() } }
                ?: return "unknown action; use one of " + BodyAction.entries.joinToString { it.tag }
            toolActed = true
            fire { it.onAction(a) }
            return "done: you are doing '${a.tag}' now"
        }
    }

    private fun bodyNow(facts: Boolean = true): String = runCatching {
        listOf(bodyState(), com.combo.monkeycompanion.mind.MindHub.promptNote(), bondNote(facts)).filter { it.isNotBlank() }.joinToString("\n")
    }.getOrDefault("")

    /**
     * v10.19: friendship level, today's feeling, the streak, a sulk, plus a few things he remembers about you
     * ([facts] = false when the chat prompt already lists the memories). Its own short line, so it never pushes
     * the Body Sense note out.
     */
    private fun bondNote(facts: Boolean): String = runCatching {
        val f = com.combo.monkeycompanion.fun5.Fun5Store.friendship(app)
        val b = com.combo.monkeycompanion.fun5.Fun5Store.bond(app)
        val known = if (facts && settings.groupEnabled(com.combo.monkeycompanion.powers.CapGroup.MEMORY))
            com.combo.monkeycompanion.powers.ExtraCaps.memory(app).relevant("", 4).map { it.text } else emptyList()
        com.combo.monkeycompanion.v5.Bond.promptLine(f.xp, b.warmth, f.streak, b.sulking, known)
    }.getOrDefault("")

    /** Strip tags, fire actions, fallback to the user's request. Returns the text to show. */
    private fun act(raw0: String, userText: String?): String {
        // Power tags ([alarm 07:00], [volume media 60], [battery]...) -> answers / confirm-first actions.
        val pt = com.combo.monkeycompanion.powers.PowerTags.parse(raw0)
        var extra = ""
        if (userText != null) for (c in pt.calls) {
            val h = runCatching { powers.handle(c) }.getOrNull() ?: continue
            com.combo.monkeycompanion.powers.ActionLog.add(app, "Brain tag", "$c")
            if (h.pending != null) { main.post { setPending(h.pending) }; break }
            extra = (extra + " " + h.say).trim()
        }
        val raw = if (extra.isNotEmpty()) pt.text + " " + extra else pt.text
        val p = ActionParser.parse(raw)
        // v10.15 Body Sense: AI tags only from this character's safe allow-list, rate-limited; unknown ones are dropped.
        val who = com.combo.monkeycompanion.character.CharacterRegistry.current
        val allowed = runCatching { BodySense.allowList(who, com.combo.monkeycompanion.character.CharacterRegistry.wear(who)) }.getOrDefault(BodySense.SAFE)
        val acts = BodySense.gate(p.actions, allowed).filter { BodySense.limiter.allow() }.toMutableList()
        if (acts.isEmpty() && !toolActed && userText != null) ActionParser.requestedByUser(userText)?.let { acts.add(it) }
        if (BodyAction.FLY in acts && userText != null && com.combo.monkeycompanion.brain.Superpowers.requested == null)
            com.combo.monkeycompanion.brain.Superpowers.requested = com.combo.monkeycompanion.brain.Superpowers.named(userText)
        if (acts.isNotEmpty()) main.post { acts.forEach { a -> fire { it.onAction(a) } } }
        return p.text
    }

    private lateinit var app: Context
    lateinit var settings: AppSettings; private set
    lateinit var memory: ChatMemory; private set
    lateinit var powers: PhonePowers; private set

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "monkey-brain").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val listeners = CopyOnWriteArrayList<Listener>()

    @Volatile var busy = false; private set

    // ---- v10.18.1: background AI (quips, Body Sense lines, preparing touch reactions) always yields to the human ----
    @Volatile private var bgRunning = false

    /** Runs a background brain job on the worker, unless a chat started meanwhile (then [cb] gets null at once). */
    private fun background(cb: (String?) -> Unit, job: () -> Unit) {
        worker.execute {
            if (busy) { main.post { cb(null) }; return@execute }
            bgRunning = true
            try { job() } finally { bgRunning = false }
        }
    }

    /** The human just asked for something: stop the background thinking now so their reply starts straight away. */
    private fun yieldToHuman() {
        if (bgRunning) runCatching { brain?.cancel() }
    }
    @Volatile var pending: PendingAction? = null; private set

    private var brain: Brain? = null
    private var brainSig = ""

    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        settings = AppSettings(app)
        com.combo.monkeycompanion.mind.MindHub.init(app)
        AgentPromptMore.init(app)
        memory = ChatMemory(app)
        powers = PhonePowers(app, settings, { action -> main.post { setPending(action) } }, withdraw = { a -> main.post { if (pending === a) setPending(null) } }, react = { body, prop ->
            main.post {
                body?.let { a -> fire { it.onAction(a) } }
                if (prop != com.combo.monkeycompanion.powers.Prop.NONE) fire { it.onProp(prop) }
            }
        })
        com.combo.monkeycompanion.agent.ScreenAgent.addListener { on -> main.post { com.combo.monkeycompanion.agent.DrivingNotice.update(app, on) } }
    }

    fun addListener(l: Listener) { listeners.addIfAbsent(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    /** Current brain (rebuilt when settings change). */
    @Synchronized fun brain(): Brain {
        val sig = settings.brainSignature() + "|" + (settings.brainType == BrainType.OFFLINE && ModelStore.isDownloading(app))
        brain?.let { if (sig == brainSig) return it }
        brain?.close()
        val b = build()
        brain = b; brainSig = sig
        return b
    }

    private fun build(): Brain {
        val s = settings
        if (!s.aiBrainEnabled) return CannedBrain()
        return when (s.brainType) {
            BrainType.NONE -> CannedBrain()
            BrainType.ONLINE -> {
                val pr = s.provider
                val key = s.apiKeyFor(pr)
                val model = s.modelFor(pr)
                when {
                    pr != Provider.CUSTOM && key.isBlank() -> CannedBrain("My online brain needs an API key. Add one in Brain & settings!")
                    pr == Provider.CUSTOM && s.customBaseUrl.isBlank() -> CannedBrain("Set the custom server URL in Brain & settings first!")
                    model.isBlank() -> CannedBrain("Pick a model name in Brain & settings!")
                    pr == Provider.ANTHROPIC -> AnthropicBrain(key, model)
                    else -> OpenAiCompatBrain(
                        "${pr.label} ($model)",
                        if (pr == Provider.CUSTOM) s.customBaseUrl else pr.baseUrl, key, model
                    )
                }
            }
            BrainType.OFFLINE -> {
                val path = s.offlineModelPath
                if (path.isBlank() || !java.io.File(path).exists() || ModelStore.isDownloading(app))
                    CannedBrain("My on-device brain isn't downloaded yet. Finish it in Brain & settings!")
                else OfflineBrain(path, s.offlineUseGpu, app.cacheDir.path, false) // small models: powers via tags (see Persona.POWER_TAGS), more reliable than native tool calls
            }
        }
    }

    fun brainLabel(): String = runCatching { brain().label }.getOrDefault("?")
    fun hasRealBrain(): Boolean = runCatching { settings.aiBrainEnabled && brain().isReal }.getOrDefault(false)
    fun isBrainEnabled(): Boolean = runCatching { settings.aiBrainEnabled && settings.brainType != BrainType.NONE }.getOrDefault(false)

    fun setBrainEnabled(on: Boolean) {
        settings.aiBrainEnabled = on
        synchronized(this) { brainSig = "" }
    }

    fun giveBrainStatusLine(): String = when {
        !isBrainEnabled() -> "🔒 AI Brain: OFF (Local Mode Only — Tap 'Give Brain' to enable AI model)"
        hasRealBrain() -> "✅ AI Brain: ON · ${brainLabel()} (Active for AI Modeling, Chat & Recaps)"
        settings.brainType == BrainType.ONLINE -> "⚠️ AI Brain: ON (${settings.provider.label}) — Tap 'Give Brain' to paste API key"
        else -> "⚠️ AI Brain: ON (On-Device) — Tap 'Give Brain' to finish model setup"
    }

    /** Send a user message. Ignored while a reply is in progress. */
    /**
     * Send a user message. [replyingTo] is a line Settai said that isn't in the conversation yet
     * (a canned line or a quip shown in the bubble); it's shown in the log and given to the model
     * as context so a bubble reply makes sense.
     */
    fun send(text: String, replyingTo: String? = null, voice: Boolean = false) {
        val typed = text.trim()
        if (typed.isEmpty() || busy) return
        speakThis = voice
        runCatching { com.combo.monkeycompanion.fun5.Fun5Store.addXp(app, com.combo.monkeycompanion.v5.Friendship.Source.CHAT) }
        runCatching {
            val funny = typed.length >= 10 && (typed.contains("😂") || typed.contains("🤣") || typed.contains("haha", true) || typed.contains("lol", true))
            com.combo.monkeycompanion.mind.MindHub.event(if (funny) com.combo.monkeycompanion.mind.MindEvent.FUNNY_CHAT else com.combo.monkeycompanion.mind.MindEvent.CHAT, typed)
        }
        runCatching { com.combo.monkeycompanion.mind.MindHub.onUserMessage(typed) }
        // v5: overlay-only intents ("let's play", "make a clip") and the on-demand cricket score.
        localIntent?.let { f -> if (f(typed)) { quickLocal(typed, "On it! 🎮"); return } }
        if (com.combo.monkeycompanion.v5.Cricket.asks(typed)) { cricket(typed); return }
        // v4.2: "what's on my screen", "summarise this chat", "reply for me", "roast this screen"...: a digest + an in-character answer.
        com.combo.monkeycompanion.agent.ScreenTalk.parse(typed)?.let { screenTalk(it, typed); return }
        if (handlePhoneCommand(typed, replyingTo)) return
        busy = true; yieldToHuman()
        val offline = settings.brainType == BrainType.OFFLINE
        // Small on-device models have a short context: keep only the last few turns so nothing gets truncated.
        val history = memory.recent(if (offline) minOf(settings.memoryTurns, 4) else settings.memoryTurns)
        val said = replyingTo?.trim()?.takeIf { it.isNotEmpty() && memory.all().lastOrNull { m -> !m.fromUser }?.text?.trim() != it }
        if (said != null) memory.add(ChatMsg(false, said, note = true))
        val msg = if (said != null) "(You had just said to me: \"$said\")\n$typed" else typed
        memory.add(ChatMsg(true, typed))
        memory.add(ChatMsg(false, ""))
        fire { it.onHistoryChanged() }
        fire { it.onThinking() }
        worker.execute {
            var last = ""
            try {
                val b = brain()
                val th = if (b.supportsTools) tools else null
                toolActed = false
                val usePowers = powers.specs.isNotEmpty() && (!offline || settings.offlineTools)
                val sys = Persona.systemPrompt(th != null && usePowers, bodyNow(facts = !usePowers), compact = offline, actionTool = th != null,
                    powerTags = if (usePowers && (th == null || offline)) powers.tagHelp() else "",
                    phoneState = if (usePowers) powers.state() else "",
                    extra = if (usePowers) AgentPrompt.build(settings, th != null && !offline, typed) else "")
                val reply = b.chat(sys, history, msg, th) { raw ->
                    val t = com.combo.monkeycompanion.powers.PowerTags.visible(ActionParser.visible(raw))
                    last = t
                    memory.updateLast(t)
                    main.post { fire { l -> l.onReply(t, false) } }
                }
                val shownReply = act(reply.ifBlank { last }, typed)
                val final = shownReply.ifBlank { if (reply.isNotBlank()) "Ta-da!" else "" }
                    .ifBlank { "Ooh ooh... I lost my train of thought. Try again?" }
                memory.updateLast(final); memory.commit()
                com.combo.monkeycompanion.agent.ScreenAgent.end()
                spoke(final)
                main.post { busy = false; fire { it.onReply(final, true) }; fire { it.onHistoryChanged() } }
            } catch (t: Throwable) {
                // v10.13.1: never show raw runtime errors (token ids, stack text) to the user
                android.util.Log.w("ChatHub", "reply failed", t)
                val m = if (t is BrainException && t.message?.let { com.combo.monkeycompanion.brain.PromptBudget.isTooLong(it) || it.length > 160 } != true) t.message ?: "" else com.combo.monkeycompanion.brain.PromptBudget.FRIENDLY
                val shown = if (last.isNotBlank()) "$last …" else m.ifBlank { "Oops, my brain slipped on a banana peel 🍌 Try again?" }
                memory.updateLast(shown); memory.commit()
                com.combo.monkeycompanion.agent.ScreenAgent.end()
                main.post { busy = false; fire { it.onError(m) }; fire { it.onReply(shown, true) }; fire { it.onHistoryChanged() } }
            }
        }
    }

    /**
     * Deterministic intent layer, before any brain (cloud, on-device Gemma or none): timers,
     * alarms, volume, ringer, DND, brightness, media, switches, apps, status questions... Small models often refuse
     * or tell the user to do it themselves; this never does. Actions still need Yes in the bubble.
     * Returns true when handled.
     */
    private fun handlePhoneCommand(typed: String, replyingTo: String?): Boolean {
        val cmd = com.combo.monkeycompanion.powers.PhoneCommands.parse(typed) ?: return false
        val cap = com.combo.monkeycompanion.powers.Capabilities.byId(cmd.id)
        // v4: a fact said in passing ("my name is Ravi"): note it quietly (if Memory is on) and let the brain reply.
        if (cmd.args["_soft"] == true) {
            if (cap != null && settings.groupEnabled(cap.group)) runCatching { powers.handle(cmd) }
            return false
        }
        val h = runCatching { powers.handle(cmd) }.getOrElse { e ->
            com.combo.monkeycompanion.powers.ActionLog.add(app, "Command: $typed", "crashed: ${e.message}", false)
            com.combo.monkeycompanion.powers.PhonePowers.Handled("Oops, that went wrong: ${e.message ?: e.javaClass.simpleName} 😵")
        }
        // "open <something that isn't an app>": let the brain answer instead
        if (cmd.id == "open_app" && !h.ok) return false
        com.combo.monkeycompanion.powers.ActionLog.add(app, "Understood: “$typed”", "$cmd")
        replyingTo?.trim()?.takeIf { it.isNotEmpty() }?.let { memory.add(ChatMsg(false, it, note = true)) }
        memory.add(ChatMsg(true, typed))
        memory.add(ChatMsg(false, h.say)); memory.commit()
        if (h.pending != null) setPending(h.pending)
        com.combo.monkeycompanion.agent.ScreenAgent.end()
        fire { it.onHistoryChanged() }
        fire { it.onReply(h.say, true) }
        spoke(h.say)
        return true
    }

    /** v4: show a Yes/No from outside a chat turn (routines). */
    fun propose(a: PendingAction) { if (Looper.myLooper() == Looper.getMainLooper()) setPending(a) else main.post { setPending(a) } }

    /** v4.2: the current turn came from hold-to-talk, so its answer is spoken even with "Speak replies" off. */
    @Volatile private var speakThis = false

    /** v4: say a reply out loud when the Voice power + "speak replies" are on (or the user just spoke to us). */
    fun spoke(text: String) {
        val voiceTurn = speakThis; speakThis = false
        if (!settings.groupEnabled(com.combo.monkeycompanion.powers.CapGroup.VOICE)) return
        if (settings.speakReplies || (voiceTurn && settings.speakVoiceReplies)) com.combo.monkeycompanion.agent.Voice.speak(app, text)
    }

    // ---------------- v4.2 smart screen talk + quick chips ----------------

    private const val NO_SVC = "My screen agent is off: switch on “Settai screen agent” in Android's Accessibility settings (Phone powers → Grant). 👓"

    /** Last digest (for context chips / proactive); null when unknown. */
    @Volatile var lastDigest: com.combo.monkeycompanion.agent.ScreenDigest? = null; private set
    fun noteDigest(d: com.combo.monkeycompanion.agent.ScreenDigest?) { lastDigest = d }

    /** Context chips for whatever app is on screen now (from the last digest of that app). */
    fun contextChips(): List<com.combo.monkeycompanion.agent.QuickChip> {
        val pkg = com.combo.monkeycompanion.agent.SettaiAccessibilityService.instance?.currentPkg ?: return emptyList()
        val d = lastDigest?.takeIf { it.pkg == pkg }
        return com.combo.monkeycompanion.agent.ScreenTalk.contextChips(d?.type ?: com.combo.monkeycompanion.agent.Proactive.typeGuess(pkg))
    }

    /** Reads the screen, builds a digest and answers in character (brain + few-shot, dump check, template fallback). */
    fun screenTalk(mode: com.combo.monkeycompanion.agent.ScreenTalk.Mode, said: String) {
        if (busy) return
        val ST = com.combo.monkeycompanion.agent.ScreenTalk
        fun quick(text: String) {
            memory.add(ChatMsg(true, said)); memory.add(ChatMsg(false, text)); memory.commit()
            fire { it.onHistoryChanged() }; fire { it.onReply(text, true) }; spoke(text)
        }
        val grp = com.combo.monkeycompanion.powers.CapGroup.SCREEN
        if (!settings.groupEnabled(grp)) return quick("Reading your screen is one of my agent powers: switch on “${grp.label}” in Phone powers first (it's off until you say so). 🔑")
        val sv = com.combo.monkeycompanion.agent.SettaiAccessibilityService.instance ?: return quick(NO_SVC)
        busy = true; yieldToHuman()
        memory.add(ChatMsg(true, said)); memory.add(ChatMsg(false, ""))
        fire { it.onHistoryChanged() }; fire { it.onThinking() }
        worker.execute {
            var chips = emptyList<com.combo.monkeycompanion.agent.QuickChip>()
            var draftFor: com.combo.monkeycompanion.agent.ScreenSnapshot? = null
            val final = try {
                val snap = sv.snapshot(); com.combo.monkeycompanion.agent.ScreenAgent.last = snap
                val verdict = com.combo.monkeycompanion.agent.AgentSafety.screen(snap, app.packageName, settings.agentBlockedList(), settings.agentAllowedList())
                if (verdict is com.combo.monkeycompanion.agent.AgentSafety.Verdict.Blocked) {
                    com.combo.monkeycompanion.powers.ActionLog.add(app, "Screen read blocked", verdict.reason, false)
                    "I won't peek at that one: ${verdict.reason}. 🙈"
                } else {
                    val d = com.combo.monkeycompanion.agent.Digest.build(snap)
                    lastDigest = d
                    chips = ST.followUps(mode, d)
                    var text = ""
                    val b = brain()
                    val offline = settings.brainType == BrainType.OFFLINE
                    if (b.isReal && d.type != com.combo.monkeycompanion.agent.ScreenType.LOCKED && !(mode == ScreenTalk.Mode.HOW_LONG && d.facts["eta"] != null)) {
                        var extra = ""
                        if (mode == ScreenTalk.Mode.CHEAPER && settings.groupEnabled(com.combo.monkeycompanion.powers.CapGroup.WEB)) {
                            val item = d.items.firstOrNull { it.role == "product" }?.title ?: d.title
                            if (item.isNotBlank()) extra = runCatching { powers.handle(com.combo.monkeycompanion.powers.Invocation("web_lookup", mapOf("query" to "$item price India"))).say }.getOrDefault("").take(1200)
                        }
                        val msg = ST.prompt(mode, d, offline, said) + (if (extra.isNotBlank()) "\n\nWEB RESULTS:\n$extra" else "")
                        val raw = runCatching { b.chat(Persona.systemPrompt(false, bodyNow(), compact = offline), emptyList(), msg, null) {} }.getOrDefault("")
                        text = ST.tidy(com.combo.monkeycompanion.powers.PowerTags.visible(ActionParser.visible(act(cleanReply(raw), null))), mode)
                        val bad = text.isBlank() || (if (mode == ScreenTalk.Mode.REPLY_DRAFT) text.length > 280 else ST.looksLikeDump(text, d))
                        if (bad) {
                            com.combo.monkeycompanion.powers.ActionLog.add(app, "Screen talk", "brain answer looked like a dump; used the template", false)
                            text = ""
                        }
                    }
                    if (text.isBlank()) text = ST.template(mode, d)
                    com.combo.monkeycompanion.powers.ActionLog.add(app, "Screen talk (${mode.name.lowercase()})", "${d.app} · ${d.type.label}")
                    if (mode == ScreenTalk.Mode.REPLY_DRAFT) {
                        if (d.type == com.combo.monkeycompanion.agent.ScreenType.CONVERSATION && snap.nodes.any { it.editable && !it.password }) {
                            draftFor = snap
                            pendingDraft = text
                            "Draft for ${d.title.ifBlank { "them" }}: “$text”"
                        } else "Open the chat you want to answer first, then tap “Reply for me”! 💬"
                    } else text
                }
            } catch (t: Throwable) { "Oops, my goggles fogged up: ${t.message ?: t.javaClass.simpleName} 🥽" }
            memory.updateLast(final); memory.commit()
            com.combo.monkeycompanion.agent.ScreenAgent.end()
            spoke(final)
            val draft = draftFor
            main.post {
                busy = false
                fire { it.onReply(final, true) }; fire { it.onHistoryChanged() }
                if (draft != null) proposeDraft(sv, draft)
                else if (chips.isNotEmpty()) fire { it.onChips(chips) }
            }
        }
    }

    @Volatile private var pendingDraft: String? = null

    /** "Reply for me": ask before typing the draft into the message box. Never presses send. */
    private fun proposeDraft(sv: com.combo.monkeycompanion.agent.SettaiAccessibilityService, snap: com.combo.monkeycompanion.agent.ScreenSnapshot) {
        val text = pendingDraft ?: return
        pendingDraft = null
        setPending(PendingAction("Type it into the message box? ⌨️ (I won't press send)", "Typed it. Check it and tap Send yourself! ✅") {
            // Fresh look (the screen may have changed since the draft): same app, a non-password text box.
            val now = sv.snapshot()
            if (now.pkg != snap.pkg) return@PendingAction "You left ${snap.app}, so I didn't type anything. 🙈"
            val box = now.nodes.firstOrNull { it.editable && !it.password && it.focused } ?: now.nodes.firstOrNull { it.editable && !it.password }
            val v = box?.let { com.combo.monkeycompanion.agent.AgentSafety.action(now, it, typing = true) }
            when {
                box == null -> "I lost the message box. 😕"
                v is com.combo.monkeycompanion.agent.AgentSafety.Verdict.Blocked -> "I won't: ${v.reason}."
                sv.type(box, text) -> { com.combo.monkeycompanion.powers.ActionLog.add(app, "Reply for me", "typed a draft (not sent)"); null }
                else -> "I couldn't type there. 😕"
            }
        })
    }

    /** A quick-tap chip from the bubble, the arc or the chat panel. */
    fun quick(chip: com.combo.monkeycompanion.agent.QuickChip) {
        if (busy && chip.id != "later") return
        val ST = com.combo.monkeycompanion.agent.ScreenTalk
        when (chip.id) {
            "think" -> {
                val t = com.combo.monkeycompanion.mind.MindHub.thinking()
                memory.add(ChatMsg(true, chip.label)); memory.add(ChatMsg(false, t)); memory.commit()
                fire { it.onHistoryChanged() }; fire { it.onReply(t, true) }; spoke(t)
            }
            "miss" -> if (listeners.any { it is com.combo.monkeycompanion.overlay.MonkeyView }) fire { it.onLocalChip(com.combo.monkeycompanion.agent.QuickChip.NOTIF_CARD) } else send("what did I miss")
            "screen" -> screenTalk(ScreenTalk.Mode.SUMMARY, chip.label)
            "summarise" -> screenTalk(ScreenTalk.Mode.CHAT_SUMMARY, chip.label)
            "reply_for_me" -> screenTalk(ScreenTalk.Mode.REPLY_DRAFT, chip.label)
            "roast" -> screenTalk(ScreenTalk.Mode.ROAST, chip.label)
            "worth" -> screenTalk(ScreenTalk.Mode.WORTH, chip.label)
            "cheaper" -> screenTalk(ScreenTalk.Mode.CHEAPER, chip.label)
            "how_long" -> screenTalk(ScreenTalk.Mode.HOW_LONG, chip.label)
            "open" -> openOnScreen(chip.arg)
            "say" -> send(chip.arg)
            "notifs", "notif_card" -> if (listeners.any { it is com.combo.monkeycompanion.overlay.MonkeyView }) fire { it.onLocalChip(com.combo.monkeycompanion.agent.QuickChip.NOTIF_CARD) } else send("read my notifications")
            "otp" -> send("show my otp")
            "snooze" -> com.combo.monkeycompanion.agent.Proactive.snooze(chip.arg)
            "later" -> {}
            else -> fire { it.onLocalChip(chip) }   // funny, talk, quit, chat: the overlay does these
        }
    }

    /** "Open Amma" after a chat-list answer: a tap on that row, asked first like any screen tap. */
    private fun openOnScreen(name: String) {
        if (name.isBlank()) return
        val h = runCatching { powers.handle(com.combo.monkeycompanion.powers.Invocation("tap", mapOf("target" to name))) }.getOrNull() ?: return
        if (h.pending != null) setPending(h.pending) else fire { it.onAnnounce(h.say) }
    }

    /** v4.2: a proactive pipe-up, from [com.combo.monkeycompanion.agent.Proactive] (main thread). */
    fun pipe(p: com.combo.monkeycompanion.agent.Pipe) = fire { it.onPipe(p) }

    /** v4: a line in the bubble that isn't a reply (agent powers, OTPs). */
    fun announce(text: String) = fire { it.onAnnounce(text) }

    /** Something rang (timer/alarm): wake Settai, make him jump and say it. */
    fun alert(line: String, action: BodyAction) {
        fire { it.onAlert(line) }
        fire { it.onAction(action) }
        fire { it.onAnnounce(line) }
    }

    fun cancel() { runCatching { brain?.cancel() } }

    /** v4: the user pressed Stop (notification) or tapped Settai while it drives the screen. */
    fun stopDriving() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { stopDriving() }; return }
        val wasDriving = com.combo.monkeycompanion.agent.ScreenAgent.driving
        com.combo.monkeycompanion.agent.ScreenAgent.stop()
        if (pending?.quiet == true) confirmPending(false)
        if (wasDriving) {
            com.combo.monkeycompanion.powers.ActionLog.add(app, "Screen agent", "stopped by you", false)
            fire { it.onAnnounce("Okay okay, hands off the wheel! 🛑") }
        }
    }

    /** One-off unprompted remark (not stored in memory). [cb] gets null on failure. */
    fun quip(context: String, cb: (String?) -> Unit) {
        if (busy) return cb(null)
        background(cb) {
            val r = runCatching {
                val b = brain()
                if (!b.isReal) null
                else act(cleanReply(b.chat(Persona.systemPrompt(false, bodyNow(), compact = settings.brainType == BrainType.OFFLINE),
                    emptyList(), Persona.QUIP_REQUEST + context, null) {}), null)
                    .takeIf { it.isNotBlank() && it.length < 200 }
            }.getOrNull()
            main.post { cb(r) }
        }
    }

    /**
     * v10.17: one spontaneous line from Body Sense (Give Brain ON). Off the main thread; [cb] gets null on any failure.
     * The caller drops answers that come back too late (no canned fallback).
     */
    fun spontaneous(event: String, cb: (String?) -> Unit) {
        if (busy) return cb(null)
        background(cb) {
            val r = runCatching {
                val b = brain()
                if (!b.isReal) null
                else com.combo.monkeycompanion.powers.PowerTags.visible(ActionParser.visible(act(cleanReply(b.chat(
                    Persona.systemPrompt(false, bodyNow(), compact = settings.brainType == BrainType.OFFLINE),
                    emptyList(), BodyVoice.request(event), null) {}), null))).trim()
                    .takeIf { it.isNotBlank() && it.length < 180 }
            }.getOrNull()
            r?.let { spoke(it) }
            main.post { cb(r) }
        }
    }

    /**
     * v10.18 reaction engine request (same system prompt as [spontaneous]: persona + live body note).
     * [thought] = a line to show now: body action tags fire and it may be spoken aloud.
     * Otherwise it's a hidden bank refill: the raw text comes back, nothing fires, nothing is spoken.
     */
    fun reactRequest(request: String, thought: Boolean, cb: (String?) -> Unit) {
        if (busy) return cb(null)
        background(cb) {
            val r = runCatching {
                val b = brain()
                if (!b.isReal) null
                else {
                    val raw = cleanReply(b.chat(Persona.systemPrompt(false, bodyNow(), compact = settings.brainType == BrainType.OFFLINE),
                        emptyList(), request, null) {})
                    if (thought) com.combo.monkeycompanion.powers.PowerTags.visible(ActionParser.visible(act(raw, null))).trim()
                        .takeIf { it.isNotBlank() && it.length < 180 }
                    else raw.takeIf { it.isNotBlank() }
                }
            }.getOrNull()
            if (thought) r?.let { spoke(it) }
            main.post { cb(r) }
        }
    }

    /** Ask a brain a quick test question (used by Settings). */
    fun test(b: Brain, cb: (Result<String>) -> Unit) {
        worker.execute {
            val r = runCatching { b.chat(Persona.systemPrompt(false), emptyList(), "Say hello to me in under 12 words.", null) {} }
            main.post { cb(r) }
        }
    }

    fun preloadOffline() {
        worker.execute { (runCatching { brain() }.getOrNull() as? OfflineBrain)?.preload() }
    }

    private fun setPending(a: PendingAction?) {
        pending = a
        fire { it.onPending(a) }
    }

    fun confirmPending(yes: Boolean) {
        val a = pending ?: return
        setPending(null)
        if (yes) {
            val ok = runCatching { a.run() }
            val text = if (ok.isSuccess) ok.getOrNull() ?: a.doneText else "Hmm, that didn't work: ${ok.exceptionOrNull()?.message}"
            if (ok.isFailure) com.combo.monkeycompanion.powers.ActionLog.add(app, a.question, "failed: ${ok.exceptionOrNull()?.message}", false)
            if (!a.quiet) {
                memory.add(ChatMsg(false, text, note = true))
                fire { it.onAnnounce(text) }
            }
        } else {
            runCatching { a.onNo?.invoke() }
            if (!a.quiet) {
                memory.add(ChatMsg(false, "Okay, I won't. 🙈", note = true))
                fire { it.onAnnounce("Okay, I won't. 🙈") }
            }
        }
        fire { it.onHistoryChanged() }
    }

    fun clearMemory() { memory.clear(); fire { it.onHistoryChanged() } }

    private inline fun fire(crossinline f: (Listener) -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) listeners.forEach { f(it) }
        else main.post { listeners.forEach { f(it) } }
    }
}
