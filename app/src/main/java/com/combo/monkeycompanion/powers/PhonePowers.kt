package com.combo.monkeycompanion.powers

import android.content.Context
import com.combo.monkeycompanion.ai.AppSettings
import com.combo.monkeycompanion.ai.BodyAction
import com.combo.monkeycompanion.ai.ToolHost
import com.combo.monkeycompanion.ai.ToolSpec
import org.json.JSONArray
import org.json.JSONObject

/** Something the monkey wants to do that the user must approve first. */
/** [run] returns the sentence to show when it finished (null = use [doneText]). */
class PendingAction(val question: String, val doneText: String, val onNo: (() -> Unit)? = null,
                    /** v4: the brain narrates the result itself (blocking agent step): don't announce it in the bubble. */
                    val quiet: Boolean = false, val run: () -> String?)

/**
 * The monkey's phone powers ([Capabilities]) for every brain: tools for brains with tool calling,
 * [handle] for the deterministic pre-parser ([PhoneCommands]) and for tags ([PowerTags]).
 * Readings answer at once; anything that opens or changes something becomes a [PendingAction] (Yes/No).
 * [react] plays the matching body animation / prop after a change is done.
 */
class PhonePowers(
    private val ctx: Context,
    private val settings: AppSettings,
    private val propose: (PendingAction) -> Unit,
    /** Takes a pending action back off the bubble (blocking step timed out). */
    private val withdraw: (PendingAction) -> Unit = {},
    private val react: (BodyAction?, Prop) -> Unit = { _, _ -> },
) : ToolHost {

    /** v4: the screen agent needs many observe -> act rounds. */
    override val maxRounds: Int get() = if (settings.groupEnabled(CapGroup.SCREEN)) 16 else 5

    private fun schema(c: Capability): JSONObject {
        val props = JSONObject()
        for (p in c.params) {
            val o = JSONObject().put("type", p.type).put("description", p.desc)
            p.options?.let { o.put("enum", JSONArray(it)) }
            props.put(p.name, o)
        }
        return JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(c.params.filter { it.required }.map { it.name }))
    }

    override val specs: List<ToolSpec>
        get() = Capabilities.enabled(settings).map { ToolSpec(it.id, it.desc + if (it.change) " (the user confirms first)" else "", schema(it)) }

    override fun call(name: String, argsJson: String): String {
        val j = runCatching { JSONObject(argsJson) }.getOrElse { JSONObject() }
        val args = HashMap<String, Any?>().also { m -> j.keys().forEach { k -> m[k] = j.opt(k)?.takeIf { it != JSONObject.NULL } } }
        args["_from"] = "brain"
        val cap = Capabilities.byId(name)
        val h = handle(Invocation(name, args), fromBrain = true)
        val p = h.pending ?: return h.say
        if (cap?.blocking != true && !com.combo.monkeycompanion.agent.ScreenAgent.driving) { propose(p); return asked(p.question) }
        // Screen agent: wait for the user's Yes/No so the model learns what really happened.
        val latch = java.util.concurrent.CountDownLatch(1)
        var result: String? = null
        val wait = PendingAction(p.question, p.doneText, onNo = {
            result = "The user said NO. Do not do it; ask what they want instead."; ActionLog.add(ctx, p.question, "user said no", false); latch.countDown()
        }, quiet = true) {
            val r = runCatching { p.run() }.getOrElse { "failed: ${it.message}" }
            result = r ?: p.doneText; latch.countDown(); r
        }
        propose(wait)
        if (!latch.await(BLOCK_WAIT_S, java.util.concurrent.TimeUnit.SECONDS)) {
            withdraw(wait)
            return "No answer from the user in $BLOCK_WAIT_S s. Stop and ask them."
        }
        return result ?: "done"
    }

    /** Result of a recognised command: a line to say, and maybe a Yes/No action. */
    class Handled(val say: String, val pending: PendingAction? = null, val ok: Boolean = true)

    fun handle(inv: Invocation, fromBrain: Boolean = false): Handled {
        val cap = Capabilities.byId(inv.id) ?: return Handled("error: unknown power ${inv.id}", ok = false)
        if (!settings.groupEnabled(cap.group)) return Handled(
            if (cap.group.optIn) "That's one of my agent powers: switch on “${cap.group.label}” in Phone powers first (it's off until you say so). 🔑"
            else "You've switched off my “${cap.group.label}” powers in Settings, so I won't. 🙈", ok = false)
        val perm = cap.perm(inv.args)
        if (!perm.granted(ctx)) {
            val q = "For that I need the “${perm.label}” permission. Shall I open the switch? Flip it, then ask me again. 🔑"
            return Handled(q, PendingAction(q, "") {
                ActionLog.add(ctx, "Grant ${perm.label}", "opening settings")
                Capabilities.open(ctx, perm.grantIntent(ctx), "the ${perm.label} switch", "Flip the switch for Settai, then come back and ask me again! 🔑")
            })
        }
        return try {
            when (val p = cap.plan(ctx, settings, inv.args)) {
                is Plan.Answer -> {
                    ActionLog.add(ctx, cap.title, p.text.take(300))
                    if (cap.answerProp != Prop.NONE) react(null, cap.answerProp)
                    Handled(p.text)
                }
                is Plan.Fail -> { ActionLog.add(ctx, cap.title, p.text, false); Handled(if (fromBrain) "error: ${p.text}" else p.text, ok = false) }
                is Plan.Act -> actOn(cap, p)
                is Plan.Later -> {
                    if (cap.answerProp != Prop.NONE) react(null, cap.answerProp)
                    val run = {
                        val r = runCatching { p.work() }.getOrElse { "That didn't work: ${it.message ?: it.javaClass.simpleName} 😕" }
                        ActionLog.add(ctx, cap.title, r.take(300)); r
                    }
                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                        Thread { val r = run(); com.combo.monkeycompanion.ai.ChatHub.announce(r); com.combo.monkeycompanion.ai.ChatHub.spoke(r) }.start()
                        Handled(p.placeholder)
                    } else Handled(run())
                }
            }
        } catch (e: Exception) {
            ActionLog.add(ctx, cap.title, "crashed: ${e.message}", false)
            Handled("Oops, that went wrong: ${e.message ?: e.javaClass.simpleName} 😵", ok = false)
        }
    }

    private fun actOn(cap: Capability, p: Plan.Act): Handled {
        val pa = PendingAction(p.question, "Done!") {
                    try {
                        val r = p.exec()
                        val ok = !r.startsWith("I couldn't") && !r.contains("notification instead")
                        ActionLog.add(ctx, cap.title, r, ok)
                        if (ok) react(p.body, p.prop)
                        r
                    } catch (e: SecurityException) {
                        ActionLog.add(ctx, cap.title, "blocked: ${e.message}", false)
                        "Android blocked that (${e.message ?: "missing permission"}). 😕"
                    } catch (e: Exception) {
                        ActionLog.add(ctx, cap.title, "failed: ${e.message}", false)
                        "That didn't work: ${e.message ?: e.javaClass.simpleName} 😕"
                    }
        }
        if (p.auto) { val r = pa.run() ?: pa.doneText; return Handled(r) }
        return Handled(p.question, pa)
    }

    /** One line about the phone now, for the system prompt. */
    fun state(): String = Capabilities.stateSummary(ctx)

    /** Tag list for brains without tools. */
    fun tagHelp(): String = Capabilities.tagHelp(settings)

    companion object { const val BLOCK_WAIT_S = 60L }

    private fun asked(q: String) =
        "Asked the user to confirm: \"$q\". It is NOT done yet; tell them briefly to tap Yes in your bubble."
}
