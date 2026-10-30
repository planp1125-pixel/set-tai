package com.combo.monkeycompanion.powers

import android.content.Context
import com.combo.monkeycompanion.agent.MemoryParse
import com.combo.monkeycompanion.agent.MemoryStore
import com.combo.monkeycompanion.agent.NotifStore
import com.combo.monkeycompanion.agent.RoutineParse
import com.combo.monkeycompanion.agent.RoutineStore
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.powers.AgentCaps.fromBrain
import com.combo.monkeycompanion.powers.AgentCaps.s
import java.io.File

/** v4 agent powers: notifications, memory, routines (+ [MoreCaps]: voice, web, doctor, calendar/contacts). */
object ExtraCaps {
    fun build(): List<Capability> = notifs() + memory() + routines() + MoreCaps.build()

    @Volatile private var mem: MemoryStore? = null
    fun memory(c: Context): MemoryStore = mem ?: synchronized(this) { mem ?: MemoryStore(File(c.applicationContext.filesDir, "settai_memory.json")).also { mem = it } }

    // ---------------- Notifications ----------------
    private fun listener() = SettaiNotificationListener.instance
    private const val NO_LISTENER = "I can't see notifications yet: give me Notification access (Phone powers → Notification reader → Grant). 🔍"

    private fun notifs(): List<Capability> = listOf(
        Capability("read_notifications", CapGroup.NOTIFS, "What did I miss?", "Summarise the notifications on the phone now (grouped by app). OTP codes are hidden from you; they're shown to the user directly.",
            listOf(Param("app", "string", "Only this app or person (optional)", required = false)), change = false,
            perm = { Perm.NOTIF_LISTENER }, needs = listOf(Perm.NOTIF_LISTENER), answerProp = Prop.MAGNIFIER,
            tag = "notifs", tagHelp = "[notifs] (what did I miss)", fromTag = { t -> if (t.isBlank()) emptyMap() else mapOf("app" to t) }) { _, _, a ->
            val l = (listener() ?: return@Capability Plan.Fail(NO_LISTENER)).current()
            val f = a.s("app").lowercase().let { w -> if (w.isEmpty()) l else l.filter { it.app.lowercase().contains(w) || it.title.lowercase().contains(w) } }
            val brain = a.fromBrain()
            if (brain) NotifStore.latestOtp(f)?.let { o -> ChatHub.announce("🔐 ${o.app} code: ${o.otp} — only for you, don't share it!") }
            Plan.Answer((if (brain) "" else "🔍 ") + NotifStore.summary(f, forBrain = brain))
        },
        Capability("show_otp", CapGroup.NOTIFS, "Show the latest OTP", "Show the newest one-time code to the user in the bubble (you never see the code; never send it anywhere).",
            change = false, perm = { Perm.NOTIF_LISTENER }, needs = listOf(Perm.NOTIF_LISTENER), answerProp = Prop.MAGNIFIER,
            tag = "otp", tagHelp = "[otp]", fromTag = { emptyMap() }) { _, _, a ->
            val l = (listener() ?: return@Capability Plan.Fail(NO_LISTENER)).current()
            val o = NotifStore.latestOtp(l) ?: return@Capability Plan.Answer(
                if (NotifStore.otpHidden(l)) "I can see a code message, but Android hid the code from me (Android 15+ protects OTPs from apps like me). Open the notification to read it. 🔒"
                else "No OTP in your notifications right now. 🔍")
            val line = "🔐 ${o.app} code: ${o.otp} — only for you, don't share it with anyone!"
            if (a.fromBrain()) { ChatHub.announce(line); Plan.Answer("Shown the code to the user in the bubble. Do not repeat or guess it.") } else Plan.Answer(line)
        },
        Capability("reply_notification", CapGroup.NOTIFS, "Reply to a notification",
            "Reply to a message notification (WhatsApp, SMS...) through its own reply box. who = sender, chat or app. The user sees the exact text and confirms first; never send without being asked.",
            listOf(Param("who", "string", "Sender / chat / app"), Param("text", "string", "Reply text")), change = true, blocking = true,
            perm = { Perm.NOTIF_LISTENER }, needs = listOf(Perm.NOTIF_LISTENER), tag = "reply", tagHelp = "[reply WHO: TEXT]",
            fromTag = { t -> t.split(':', limit = 2).takeIf { it.size == 2 }?.let { mapOf("who" to it[0].trim(), "text" to it[1].trim()) } }) { c, _, a ->
            val ls = listener() ?: return@Capability Plan.Fail(NO_LISTENER)
            val text = a.s("text").ifBlank { return@Capability Plan.Fail("Reply with what?") }
            val n = NotifStore.match(ls.current(), a.s("who"), replyable = true) ?: return@Capability Plan.Fail("I don't see a message from “${a.s("who")}” that I can reply to.")
            Plan.Act("Send “${text.take(100)}” to ${n.title.ifBlank { n.app }} on ${n.app}? 📨") {
                if (ls.reply(n.key, text)) "Sent to ${n.title.ifBlank { n.app }}! 📨" else "I couldn't reply there (that app's reply box didn't take it)."
            }
        },
        Capability("dismiss_notification", CapGroup.NOTIFS, "Clear notifications", "Dismiss one notification (who = app / sender) or all of them (who = all). Asks first.",
            listOf(Param("who", "string", "App / sender, or all")), change = true,
            perm = { Perm.NOTIF_LISTENER }, needs = listOf(Perm.NOTIF_LISTENER), tag = "dismiss", tagHelp = "[dismiss all|APP]",
            fromTag = { t -> mapOf("who" to t.ifBlank { "all" }) }) { _, _, a ->
            val ls = listener() ?: return@Capability Plan.Fail(NO_LISTENER)
            val who = a.s("who").ifBlank { "all" }
            if (who.equals("all", true) || who.equals("everything", true)) {
                val n = ls.current().size
                if (n == 0) return@Capability Plan.Answer("Nothing to clear. Clean slate! ✨")
                Plan.Act("Clear all $n notifications? 🧹") { ls.dismiss(null); "Swept them all away! 🧹" }
            } else {
                val l = ls.current().filter { it.app.contains(who, true) || it.title.contains(who, true) }
                if (l.isEmpty()) return@Capability Plan.Fail("No notifications from “$who”.")
                Plan.Act("Clear ${l.size} notification${if (l.size == 1) "" else "s"} from $who? 🧹") { l.forEach { ls.dismiss(it.key) }; "Cleared! 🧹" }
            }
        },
    )

    // ---------------- Memory ----------------
    private fun memory(): List<Capability> = listOf(
        Capability("remember", CapGroup.MEMORY, "Remember a fact", "Save a lasting fact about your human (name, likes, birthdays, habits, people) so you can use it later. Write it in second person, e.g. 'You love filter coffee'. Only facts they shared; nothing secret like passwords or OTPs.",
            listOf(Param("fact", "string", "The fact, e.g. 'Your sister Priya's birthday is 4 May'")), change = false, answerProp = Prop.NOTEBOOK,
            tag = "remember", tagHelp = "[remember FACT]", fromTag = { t -> t.takeIf { it.isNotBlank() }?.let { mapOf("fact" to it) } }) { c, _, a ->
            val f = a.s("fact").ifBlank { return@Capability Plan.Fail("Remember what?") }
            if (com.combo.monkeycompanion.agent.Otp.find(f) != null || Regex("(?i)\\b(password|passcode|pin|cvv|otp)\\b").containsMatchIn(f))
                return@Capability Plan.Fail("I don't keep passwords, PINs or codes, even in my notebook. 🙅")
            val fact = memory(c).add(MemoryParse.toYou(f))
            Plan.Answer("📒 Noted: ${fact.text}")
        },
        Capability("forget", CapGroup.MEMORY, "Forget a fact", "Forget what you remember about something (or 'everything').",
            listOf(Param("what", "string", "Words from the fact, or everything")), change = false, answerProp = Prop.NOTEBOOK,
            tag = "forget", tagHelp = "[forget WORDS]", fromTag = { t -> t.takeIf { it.isNotBlank() }?.let { mapOf("what" to it) } }) { c, _, a ->
            val gone = memory(c).forget(a.s("what"))
            if (gone.isEmpty()) Plan.Answer("I don't remember anything about “${a.s("what")}”. Already forgot it, probably. 🙈")
            else Plan.Answer("🧽 Forgot ${gone.size} thing${if (gone.size == 1) "" else "s"}: " + gone.joinToString("; ") { it.text }.take(200))
        },
        Capability("list_memories", CapGroup.MEMORY, "What do you remember?", "List what you remember about your human.", change = false, answerProp = Prop.NOTEBOOK,
            tag = "memories", tagHelp = "[memories]", fromTag = { emptyMap() }) { c, _, _ ->
            val all = memory(c).all()
            Plan.Answer(if (all.isEmpty()) "My notebook is empty! Tell me “remember that …”. 📒" else "📒 I remember: " + all.takeLast(15).joinToString("; ") { it.text })
        },
    )

    // ---------------- Routines ----------------
    /** Routine action words -> a power call (same deterministic parser as chat). */
    fun routineCommand(text: String): Pair<String, Map<String, Any?>>? =
        PhoneCommands.parse(text)?.takeIf { Capabilities.byId(it.id)?.let { c -> !c.group.optIn || c.group == CapGroup.NOTIFS } == true }?.let { it.id to it.args }

    private fun routines(): List<Capability> = listOf(
        Capability("add_routine", CapGroup.ROUTINES, "Make a routine",
            "Create a routine from a sentence, e.g. 'every day at 9 pm remind me to drink water', 'weekdays at 7 say good morning', 'when the charger is connected say thanks', 'every night at 10 turn on do not disturb'. The user confirms.",
            listOf(Param("text", "string", "The routine in plain words")), change = true,
            tag = "routine", tagHelp = "[routine every day at 9pm remind me to X]", fromTag = { t -> t.takeIf { it.isNotBlank() }?.let { mapOf("text" to it) } }) { c, _, a ->
            val r = RoutineParse.parse(a.s("text"), ::routineCommand)
                ?: return@Capability Plan.Fail("I couldn't turn that into a routine. Try “every day at 9 pm remind me to …” or “when the charger is connected say …”.")
            Plan.Act("New routine: ${r.describe()}. Save it? 🗓️") {
                val n = RoutineStore.add(c, r)
                "Routine #${n.id} saved: ${n.describe()} 🗓️" + if (n.trigger is com.combo.monkeycompanion.agent.Trigger.On) " (works while I'm on screen)" else ""
            }
        },
        Capability("list_routines", CapGroup.ROUTINES, "List routines", "List the routines.", change = false,
            tag = "routines", tagHelp = "[routines]", fromTag = { emptyMap() }) { c, _, _ ->
            val l = RoutineStore.all(c)
            Plan.Answer(if (l.isEmpty()) "No routines yet. Try “every day at 9 pm remind me to drink water”. 🗓️" else l.joinToString("\n") { "#${it.id} ${it.describe()}" + if (!it.enabled) " (paused)" else "" })
        },
        Capability("delete_routine", CapGroup.ROUTINES, "Delete a routine", "Delete a routine by its #number or words from it. Asks first.",
            listOf(Param("which", "string", "#number or words")), change = true,
            tag = "delroutine", tagHelp = "[delroutine N]", fromTag = { t -> t.takeIf { it.isNotBlank() }?.let { mapOf("which" to it) } }) { c, _, a ->
            val w = a.s("which").removePrefix("#").trim()
            val l = RoutineStore.all(c)
            val r = w.toIntOrNull()?.let { id -> l.firstOrNull { it.id == id } } ?: l.firstOrNull { it.describe().contains(w, true) || it.text.contains(w, true) }
                ?: return@Capability Plan.Fail("No routine matches “$w”.")
            Plan.Act("Delete routine #${r.id} (${r.describe()})? 🗑️") { RoutineStore.remove(c, r.id); "Routine #${r.id} deleted." }
        },
    )
}

