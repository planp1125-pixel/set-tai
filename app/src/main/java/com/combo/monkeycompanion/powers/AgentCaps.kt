package com.combo.monkeycompanion.powers

import android.content.Context
import com.combo.monkeycompanion.agent.AgentSafety
import com.combo.monkeycompanion.agent.ScreenAgent
import com.combo.monkeycompanion.agent.ScreenSnapshot
import com.combo.monkeycompanion.agent.ScreenText
import com.combo.monkeycompanion.agent.SettaiAccessibilityService
import com.combo.monkeycompanion.ai.AppSettings

/** v4 agent powers (opt-in groups). Each part lives in its own file; this joins them. */
object AgentCaps {
    internal fun Map<String, Any?>.s(k: String) = this[k]?.toString()?.trim().orEmpty()
    internal fun Map<String, Any?>.n(k: String): Int? = when (val v = this[k]) { is Number -> v.toInt(); is String -> v.trim().toIntOrNull(); else -> null }
    internal fun Map<String, Any?>.fromBrain() = this["_from"] == "brain"

    fun build(): List<Capability> = screen() + ExtraCaps.build()

    // ---------------- Screen agent ----------------
    private fun svc() = SettaiAccessibilityService.instance

    private fun guard(c: Context, st: AppSettings, s: ScreenSnapshot): String? =
        (AgentSafety.screen(s, c.packageName, st.agentBlockedList(), st.agentAllowedList()) as? AgentSafety.Verdict.Blocked)?.let {
            ActionLog.add(c, "Screen agent blocked", it.reason, false); "I won't: ${it.reason}. 🙅"
        }

    /** Waits a moment after a tap so the next read sees the new screen (worker thread only). */
    private fun settle() {
        val wait = 750 - (System.currentTimeMillis() - lastActAt)
        if (wait > 0 && android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) Thread.sleep(wait)
    }
    @Volatile private var lastActAt = 0L

    private fun fresh(target: String): ScreenSnapshot? {
        val sv = svc() ?: return null
        // A bare number refers to the numbers of the last read; words are matched on a fresh read.
        val last = ScreenAgent.last
        if (last != null && target.trim().removePrefix("[").removeSuffix("]").trim().toIntOrNull() != null) return last
        settle()
        return sv.snapshot().also { ScreenAgent.last = it }
    }

    private const val NO_SVC = "My screen agent is off: switch on “Settai screen agent” in Android's Accessibility settings (Phone powers → Grant). 👓"

    private fun screen(): List<Capability> = listOf(
        Capability("read_screen", CapGroup.SCREEN, "Read the screen",
            "Read what's on the phone screen now: a short summary (screen type, chats with unread counts, messages, video, headline...) and a numbered list of buttons, text fields and text. Use the numbers with tap / type_text. Call it again after every action. When telling the user about the screen, give ONE insight + ONE suggestion, never a list.",
            change = false, perm = { Perm.ACCESSIBILITY }, needs = listOf(Perm.ACCESSIBILITY), answerProp = Prop.GOGGLES,
            tag = "screen", tagHelp = "[screen] (read the screen)", fromTag = { emptyMap() }) { c, st, a ->
            val sv = svc() ?: return@Capability Plan.Fail(NO_SVC)
            ScreenAgent.step()?.let { return@Capability Plan.Fail(it) }
            settle()
            val snap = sv.snapshot(); ScreenAgent.last = snap
            guard(c, st, snap)?.let { return@Capability Plan.Fail(it) }
            // v4.2: the brain gets the digest (what matters) on top of the numbered list (for tap / type);
            // a person gets one in-character insight + suggestion, never a raw dump.
            val dg = com.combo.monkeycompanion.agent.Digest.build(snap)
            com.combo.monkeycompanion.ai.ChatHub.noteDigest(dg)
            if (a.fromBrain()) Plan.Answer("SUMMARY:\n" + com.combo.monkeycompanion.agent.Digest.forBrain(dg, compact = true) + "\n\nELEMENTS:\n" + ScreenText.compact(snap, 3000))
            else Plan.Answer("👓 " + com.combo.monkeycompanion.agent.ScreenTalk.summary(dg))
        },
        Capability("screen_insight", CapGroup.SCREEN, "Smart screen talk (quick chips)",
            "What's on screen / Summarise this chat / Reply for me (drafts, asks before typing, never sends) / Roast this screen / Is this worth watching? / Find cheaper? / How long?: reads a short digest of the screen (chats + unread counts, messages, video, article, prices, ETA) and answers in character with one insight + one suggestion.",
            listOf(Param("mode", "string", "summary, chat_summary, reply_draft, roast, worth, cheaper or how_long", required = false)), change = false,
            perm = { Perm.ACCESSIBILITY }, needs = listOf(Perm.ACCESSIBILITY), answerProp = Prop.GOGGLES) { c, st, a ->
            val sv = svc() ?: return@Capability Plan.Fail(NO_SVC)
            val snap = sv.snapshot(); ScreenAgent.last = snap
            guard(c, st, snap)?.let { return@Capability Plan.Fail(it) }
            val mode = runCatching { com.combo.monkeycompanion.agent.ScreenTalk.Mode.valueOf(a.s("mode").uppercase()) }.getOrDefault(com.combo.monkeycompanion.agent.ScreenTalk.Mode.SUMMARY)
            val dg = com.combo.monkeycompanion.agent.Digest.build(snap)
            com.combo.monkeycompanion.ai.ChatHub.noteDigest(dg)
            Plan.Answer(com.combo.monkeycompanion.agent.ScreenTalk.template(mode, dg))
        },
        Capability("tap", CapGroup.SCREEN, "Tap something on screen",
            "Tap a button / item on the current screen: target = its [number] from read_screen, or its visible text. The user confirms first.",
            listOf(Param("target", "string", "The [number] from read_screen, or the button's text")), change = true, blocking = true,
            perm = { Perm.ACCESSIBILITY }, needs = listOf(Perm.ACCESSIBILITY),
            tag = "tap", tagHelp = "[tap BUTTON TEXT]", fromTag = { t -> t.takeIf { it.isNotBlank() }?.let { mapOf("target" to it) } }) { c, st, a ->
            val sv = svc() ?: return@Capability Plan.Fail(NO_SVC)
            val target = a.s("target").ifBlank { return@Capability Plan.Fail("Tap what?") }
            ScreenAgent.step()?.let { return@Capability Plan.Fail(it) }
            val snap = fresh(target) ?: return@Capability Plan.Fail(NO_SVC)
            guard(c, st, snap)?.let { return@Capability Plan.Fail(it) }
            val node = ScreenText.find(snap, target) ?: return@Capability Plan.Fail("There's no “$target” on the screen (${snap.app}). Read the screen again.")
            (AgentSafety.action(snap, node, typing = false) as? AgentSafety.Verdict.Blocked)?.let { ActionLog.add(c, "Screen agent blocked", it.reason, false); return@Capability Plan.Fail("I won't: ${it.reason}.") }
            val label = node.text.ifBlank { node.desc }.ifBlank { "item ${node.index}" }.take(40)
            Plan.Act("Tap “$label” in ${snap.app}? 👆", auto = !st.agentConfirmEach && !AgentSafety.risky(label)) {
                lastActAt = System.currentTimeMillis()
                if (sv.tap(node)) "Tapped “$label”. 👆" else "I couldn't tap “$label” (it isn't tappable)."
            }
        },
        Capability("type_text", CapGroup.SCREEN, "Type into a text field",
            "Type text into a text field on screen (target = its [number] or label; blank = the focused field). Replaces what's in it. Never for passwords. The user confirms first; it does NOT press send.",
            listOf(Param("text", "string", "What to type"), Param("target", "string", "Field [number] or label", required = false)), change = true, blocking = true,
            perm = { Perm.ACCESSIBILITY }, needs = listOf(Perm.ACCESSIBILITY),
            tag = "type", tagHelp = "[type TEXT]", fromTag = { t -> t.takeIf { it.isNotBlank() }?.let { mapOf("text" to it) } }) { c, st, a ->
            val sv = svc() ?: return@Capability Plan.Fail(NO_SVC)
            val text = a.s("text").ifEmpty { return@Capability Plan.Fail("Type what?") }
            ScreenAgent.step()?.let { return@Capability Plan.Fail(it) }
            val target = a.s("target")
            val snap = fresh(target.ifBlank { "x" }) ?: return@Capability Plan.Fail(NO_SVC)
            guard(c, st, snap)?.let { return@Capability Plan.Fail(it) }
            val node = (if (target.isNotBlank()) ScreenText.find(snap, target, wantEditable = true) else null)
                ?: snap.nodes.firstOrNull { it.editable && it.focused } ?: snap.nodes.firstOrNull { it.editable }
                ?: return@Capability Plan.Fail("I don't see a text field on ${snap.app}.")
            (AgentSafety.action(snap, node, typing = true) as? AgentSafety.Verdict.Blocked)?.let { ActionLog.add(c, "Screen agent blocked", it.reason, false); return@Capability Plan.Fail("I won't: ${it.reason}.") }
            val field = node.text.ifBlank { node.desc }.ifBlank { "the text box" }.take(30)
            Plan.Act("Type “${text.take(80)}” into “$field” in ${snap.app}? ⌨️ (I won't press send)") {
                lastActAt = System.currentTimeMillis()
                if (sv.type(node, text)) "Typed it. ⌨️" else "I couldn't type there."
            }
        },
        Capability("scroll", CapGroup.SCREEN, "Scroll", "Scroll the screen down (more) or up.",
            listOf(Param("direction", "string", "down or up", options = listOf("down", "up"))), change = false,
            perm = { Perm.ACCESSIBILITY }, needs = listOf(Perm.ACCESSIBILITY),
            tag = "scroll", tagHelp = "[scroll down]", fromTag = { t -> mapOf("direction" to (if (t.contains("up", true)) "up" else "down")) }) { c, st, a ->
            val sv = svc() ?: return@Capability Plan.Fail(NO_SVC)
            ScreenAgent.step()?.let { return@Capability Plan.Fail(it) }
            val snap = ScreenAgent.last ?: sv.snapshot().also { ScreenAgent.last = it }
            guard(c, st, snap)?.let { return@Capability Plan.Fail(it) }
            lastActAt = System.currentTimeMillis()
            if (sv.scroll(a.s("direction") != "up")) Plan.Answer("Scrolled ${a.s("direction").ifBlank { "down" }}. 📜") else Plan.Fail("Nothing to scroll here.")
        },
        Capability("press_key", CapGroup.SCREEN, "Back / Home / Recents", "Press a system key: back, home, recents or notifications (pull down the shade).",
            listOf(Param("key", "string", "Which key", options = listOf("back", "home", "recents", "notifications"))), change = false,
            perm = { Perm.ACCESSIBILITY }, needs = listOf(Perm.ACCESSIBILITY),
            tag = "key", tagHelp = "[key back|home|recents]", fromTag = { t -> t.trim().lowercase().takeIf { it in setOf("back", "home", "recents", "notifications") }?.let { mapOf("key" to it) } }) { _, _, a ->
            val sv = svc() ?: return@Capability Plan.Fail(NO_SVC)
            ScreenAgent.step()?.let { return@Capability Plan.Fail(it) }
            val k = a.s("key").lowercase()
            lastActAt = System.currentTimeMillis(); ScreenAgent.last = null
            if (sv.key(k)) Plan.Answer("Pressed $k.") else Plan.Fail("Unknown key “$k”.")
        },
    )
}
