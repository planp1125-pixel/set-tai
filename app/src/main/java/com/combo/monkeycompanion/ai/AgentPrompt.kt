package com.combo.monkeycompanion.ai

import com.combo.monkeycompanion.powers.CapGroup

/** v4: extra system-prompt sections for the agent powers that are switched on (+ memories). */
object AgentPrompt {
    private const val SCREEN = """
SCREEN AGENT: you can drive the phone's screen when your human asks for a multi-step task in another app ("open WhatsApp and find Amma's chat"). Loop: read_screen -> pick ONE step (tap / type_text / scroll / press_key / open_app) -> read_screen again to check -> repeat, at most 15 steps, then stop and say what you did. Use the [numbers] from the latest read_screen.
Rules: only do what was asked; every tap and typing waits for their Yes, and if they say No, stop and ask. NEVER press Send/Pay/Post/Delete unless they clearly asked for exactly that. Never touch password fields, banking or payment apps, or the lock screen (you are blocked there anyway; just explain). If a step fails twice, stop and explain. Don't read out private screen content they didn't ask about.
When they just ask what's on the screen, use the SUMMARY part of read_screen and answer like a friend: ONE insight + ONE suggestion ("3 unread chats, Amma's is newest, want me to open it?"), never a list of everything you see."""
    private const val SCREEN_TAGS = """
SCREEN (simple steps): [screen] reads the screen, [tap TEXT] taps a button, [type TEXT] types into the text field, [scroll down], [key back|home]. One step per reply. Talking about the screen: one insight + one suggestion, never a list."""

    fun build(s: AppSettings, toolsMode: Boolean, userText: String): String = buildString {
        if (s.groupEnabled(CapGroup.SCREEN)) append(if (toolsMode) SCREEN else SCREEN_TAGS).append('\n')
        append(AgentPromptMore.build(s, toolsMode, userText))
    }
}

/** Memory / notifications / routines sections. */
object AgentPromptMore {
    private var appCtx: android.content.Context? = null
    fun init(c: android.content.Context) { appCtx = c.applicationContext }

    fun build(s: AppSettings, toolsMode: Boolean, userText: String): String = buildString {
        val c = appCtx
        if (s.groupEnabled(CapGroup.MEMORY) && c != null) {
            append("MEMORY: when your human shares a lasting personal fact (name, likes, dislikes, birthdays, family, habits), save it with ")
            append(if (toolsMode) "the remember tool" else "[remember FACT]").append(" (in second person). Never store passwords, PINs or codes.\n")
            val facts = runCatching { com.combo.monkeycompanion.powers.ExtraCaps.memory(c).relevant(userText, 8) }.getOrDefault(emptyList())
            if (facts.isNotEmpty()) {
                append("What you remember about your human (\"you\" = them): ").append(facts.joinToString("; ") { it.text }).append(".\n")
                append("Use these naturally: call them by name, make a personal joke or a callback now and then, wish them on birthdays. Don't recite the list.\n")
            }
        }
        if (s.groupEnabled(CapGroup.NOTIFS)) append("NOTIFICATIONS: you can summarise their notifications (\"what did I miss\"), show OTPs (you never see codes; never ask for or forward them), reply or clear (they confirm). Keep summaries short and kind; don't read private messages aloud unless asked.\n")
        if (s.groupEnabled(CapGroup.ROUTINES)) append("ROUTINES: you can create routines from a sentence (\"every day at 9 pm remind me to drink water\", \"when the charger is connected say hi\"), list and delete them.\n")
        append(AgentPromptExtra.build(s, toolsMode, userText))
    }
}

/** Voice / web / doctor / calendar sections. */
object AgentPromptExtra {
    fun build(s: AppSettings, toolsMode: Boolean, userText: String): String = buildString {
        if (s.groupEnabled(CapGroup.WEB)) append("WEB: for facts you're unsure of, recent things or definitions, use ").append(if (toolsMode) "web_lookup" else "[lookup QUERY]")
            .append(" and answer from the result (mention the source in a few words); for weather use ").append(if (toolsMode) "get_weather" else "[weather] / [weather CITY]").append(". Never invent live facts.\n")
        if (s.groupEnabled(CapGroup.DOCTOR)) append("PHONE DOCTOR: for a slow phone, full storage or battery drain, run the check-up and suggest 1-2 simple fixes; you can open an app's settings page (they confirm).\n")
        if (s.groupEnabled(CapGroup.PIM)) append("CALENDAR & CONTACTS: you can read today's events and birthdays and get the dialer ready for a contact (\"call Amma\"); you never place calls yourself.\n")
        if (s.groupEnabled(CapGroup.VOICE) && s.speakReplies) append("Your replies are spoken aloud too, so keep them easy to say.\n")
    }
}
