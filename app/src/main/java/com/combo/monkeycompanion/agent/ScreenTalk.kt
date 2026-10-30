package com.combo.monkeycompanion.agent

/**
 * v4.2: what Settai *says* about a screen. Instead of a dump ("I see: A · B · C ..."), one insight and one
 * suggestion, in character. The brain gets the [Digest] text plus (for small on-device models) a few-shot
 * example; when the brain is missing or its answer still looks like a list ([looksLikeDump]), a templated
 * line is used. Also the quick-chip vocabulary (context-aware chips per screen type). Pure Kotlin.
 */
object ScreenTalk {
    enum class Mode { SUMMARY, CHAT_SUMMARY, REPLY_DRAFT, ROAST, WORTH, CHEAPER, HOW_LONG }

    private fun has(t: String, re: String) = Regex(re).containsMatchIn(t)

    /** Typed / spoken phrases that mean "talk about my screen" (run before the other pre-parsers). */
    fun parse(raw: String): Mode? {
        val t = " " + raw.lowercase().replace(Regex("[?!.,]+"), " ").replace(Regex("\\s+"), " ").trim() + " "
        return when {
            has(t, "^ (read|scan|check) (my |the |this )?screen | what'?s on (my |the |this )?screen | what is on (my |the |this )?screen | screen la enna | what am i looking at | what do you see ") -> Mode.SUMMARY
            has(t, " summari[sz]e (this|the|my) (chat|conversation|thread) | (chat|conversation) summary | what (is|are) (they|we) (talking|saying) about | indha chat (enna|summary) ") -> Mode.CHAT_SUMMARY
            has(t, "^ (reply for me|draft (a )?reply|write (a )?reply|suggest (a )?reply|what should i reply|enna reply (pannalam|panna)) ") -> Mode.REPLY_DRAFT
            has(t, " roast (this|my|the) (screen|feed|chat|page|app) |^ roast (it|this) ") -> Mode.ROAST
            has(t, " (is this|is it) worth (watching|it|a watch) | should i watch (this|it) ") -> Mode.WORTH
            has(t, " find (a )?cheaper | cheaper (price|option|somewhere|elsewhere) | (is this|is it) (a good|the best) price ") -> Mode.CHEAPER
            has(t, "^ how long( will it take| to reach| till i reach| to get there)? $|^ (what'?s|what is) (the|my) eta ") -> Mode.HOW_LONG
            else -> null
        }
    }

    // ---------------- prompts ----------------

    private const val RULES = "Answer as yourself, in 1-2 short sentences: ONE useful insight about this screen and ONE friendly suggestion or question. " +
        "Do NOT list everything, do NOT read out buttons, no bullet points, no numbers in brackets. Never repeat codes or passwords."

    private const val FEW_SHOT = "Example. Screen: App: WhatsApp (chat list) / Unread: 3 chats / - Amma (2 unread, 10:42): \"Did you eat?\" / - Ravi (1 unread, 9:10): \"match tonight?\"\n" +
        "Good answer: 3 unread chats! Amma's is newest, asking if you ate 🍛. Want me to open it?\n" +
        "Bad answer: I see: Amma · Ravi · New chat · Camera · Search\n"

    /** The user turn for the brain: the request, the digest, rules, and (compact) a worked example. */
    fun prompt(mode: Mode, d: ScreenDigest, compact: Boolean, asked: String = ""): String {
        val task = when (mode) {
            Mode.SUMMARY -> "Tell me what's going on on my screen. $RULES"
            Mode.CHAT_SUMMARY -> "Summarise this chat in 2 sentences: who said what that matters, and anything I need to do. No list."
            Mode.REPLY_DRAFT -> "Write ONE short reply I could send in this chat, in my voice (casual, same language mix as the chat). Output ONLY the reply text, no quotes, no explanation."
            Mode.ROAST -> "Roast this screen in ONE or two funny, kind sentences (playful, never mean about real people). No list."
            Mode.WORTH -> "Is this worth watching? Give a quick verdict from the title/channel/length in 1-2 sentences, playful. No list."
            Mode.CHEAPER -> "Is this a good price, and where could it be cheaper? 1-2 sentences."
            Mode.HOW_LONG -> "How long will the trip take? One sentence."
        }
        val sb = StringBuilder()
        if (compact && (mode == Mode.SUMMARY || mode == Mode.ROAST)) sb.append(FEW_SHOT).append('\n')
        sb.append("MY SCREEN NOW:\n").append(Digest.forBrain(d, compact)).append("\n\n")
        if (asked.isNotBlank() && mode == Mode.SUMMARY) sb.append("I asked: \"").append(asked.take(120)).append("\"\n")
        sb.append(task)
        return sb.toString()
    }

    // ---------------- dump detection ----------------

    /**
     * True when a model's answer reads like a raw list instead of a remark: separators (· | ;), bullets or
     * numbered lines, "[12]" indexes, the digest's own headings echoed back, a long comma chain of short bits,
     * most of the on-screen names repeated, or simply too long / empty.
     */
    fun looksLikeDump(reply: String, d: ScreenDigest? = null): Boolean {
        val r = reply.trim()
        if (r.length < 8) return true
        if (r.length > 420) return true
        if (Regex("\\[\\d{1,3}]").findAll(r).count() >= 2) return true
        if (Regex("(?i)\\bI see:").containsMatchIn(r)) return true
        if (Regex("(?im)^(app|buttons|chats|unread|on screen|last messages|videos|items|options)\\s*(\\(|:)").containsMatchIn(r)) return true
        if (Regex(" [·|•] ").findAll(r).count() >= 3) return true
        val lines = r.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.count { Regex("^([-*•]|\\d{1,2}[.)])\\s").containsMatchIn(it) } >= 3) return true
        val commas = r.split(',', ';').map { it.trim() }
        if (commas.size >= 7 && commas.map { it.length }.average() < 16) return true
        if (d != null) {
            val names = (d.items.map { it.title } + d.buttons).map { it.lowercase() }.filter { it.length >= 3 }.distinct()
            val hit = names.count { r.lowercase().contains(it) }
            if (names.size >= 5 && hit >= 5 && hit >= names.size * 0.7) return true
            if (d.buttons.size >= 4 && d.buttons.count { r.contains(it) } >= 4) return true
        }
        return false
    }

    /** Tidies a brain's answer: drops quotes around a draft, trims to two sentences-ish. */
    fun tidy(reply: String, mode: Mode): String {
        var r = reply.trim()
        if (mode == Mode.REPLY_DRAFT) r = r.removePrefix("Reply:").removePrefix("Draft:").trim().trim('"', '“', '”').trim()
        return r
    }

    // ---------------- templated in-character lines ----------------
    private fun q(s: String, max: Int = 60) = "“" + (if (s.length <= max) s else s.take(max - 1).trimEnd() + "…") + "”"

    /** The fallback (and no-brain) answer for [mode]. Always one insight + one suggestion. */
    fun template(mode: Mode, d: ScreenDigest): String = when (mode) {
        Mode.SUMMARY -> summary(d)
        Mode.CHAT_SUMMARY -> chatSummary(d)
        Mode.REPLY_DRAFT -> draftReply(d)
        Mode.ROAST -> roast(d)
        Mode.WORTH -> worth(d)
        Mode.CHEAPER -> d.items.firstOrNull { it.role == "product" }?.let { "${q(it.title, 40)} at ${it.detail}. I can't price-check stores without a web brain, but tap “Find cheaper?” with an online brain on and I'll hunt! 🔍" }
            ?: "I don't see a price here. Open the product page and ask me again! 🛒"
        Mode.HOW_LONG -> d.facts["eta"]?.let { eta -> "About $eta" + (d.facts["distance"]?.let { " ($it)" } ?: "") + (if (d.title.isNotBlank()) " to ${d.title}" else "") + ". " + (d.facts["traffic"]?.let { "$it. " } ?: "") + "Drive safe, no phone-looking! 🚗" }
            ?: "I can't see an ETA. Start directions in Maps and ask me again! 🗺️"
    }

    fun summary(d: ScreenDigest): String = when (d.type) {
        ScreenType.LOCKED -> "Your phone's locked, so I'm not peeking. 🙈 Unlock it and ask again!"
        ScreenType.CHAT_LIST -> {
            val unread = d.items.filter { it.unread > 0 }
            val top = unread.firstOrNull() ?: d.items.firstOrNull()
            when {
                unread.size >= 2 && top != null -> "${unread.size} unread chats! ${top.title}'s is newest" + (if (top.detail.isNotBlank()) ": ${q(top.detail, 50)}" else "") + ". Want me to open it? 👀"
                unread.size == 1 && top != null -> "Just one unread: ${top.title}" + (if (top.detail.isNotBlank()) " says ${q(top.detail, 50)}" else "") + ". Open it?"
                top != null -> "All caught up on ${d.app}, no unread chats! 🍌 Latest was ${top.title}" + (if (top.time.isNotBlank()) " at ${top.time}" else "") + ". Want to text someone?"
                else -> "You're on ${d.app}'s chat list. Want me to open someone's chat?"
            }
        }
        ScreenType.CONVERSATION -> {
            val last = d.items.lastOrNull()
            val them = d.items.lastOrNull { !it.fromMe }
            when {
                last == null -> "You're in a chat with ${d.title.ifBlank { "someone" }}. Want me to draft something?"
                last.fromMe -> "You're chatting with ${d.title.ifBlank { "someone" }}; you had the last word: ${q(last.detail, 45)}. Waiting for their reply 😄"
                else -> "${them?.title ?: d.title} says ${q(them?.detail ?: last.detail, 55)}. Want me to draft a reply?"
            }
        }
        ScreenType.VIDEO -> "You're watching ${q(d.title.ifBlank { "a video" }, 55)}" + (d.facts["channel"]?.let { " by $it" } ?: "") +
            (d.facts["duration"]?.let { " ($it)" } ?: "") + ". Want my verdict on whether it's worth it? 🍿"
        ScreenType.VIDEO_LIST -> d.items.firstOrNull()?.let { v -> "YouTube is tempting you with ${d.items.size} videos; top one is ${q(v.title, 50)}" + (if (v.time.isNotBlank()) " (${v.time})" else "") + ". Shall I judge which is worth it? 😄" }
            ?: "A wall of videos! Pick one and I'll tell you if it's worth it."
        ScreenType.FEED -> d.items.firstOrNull()?.let { p -> "Scrolling ${d.app}: @${p.title}'s post" + (if (p.detail.isNotBlank()) " says ${q(p.detail, 40)}" else "") + ". Don't fall into the scroll hole! 😅" }
            ?: "Feed-scrolling on ${d.app}. Want me to remind you to take a break in 15 minutes?"
        ScreenType.ARTICLE -> "You're reading ${q(d.title.ifBlank { "an article" }, 60)}" + (d.items.firstOrNull()?.let { " — gist: ${q(it.title.substringBefore(". ").take(90), 90)}" } ?: "") + ". Want a 3-line summary?"
        ScreenType.BROWSER -> "You're on ${d.facts["url"] ?: d.title.ifBlank { "a web page" }} in ${d.app}. Want me to read the main bit or search something?"
        ScreenType.EMAIL_LIST -> {
            val unread = d.items.count { it.unread > 0 }
            val top = d.items.firstOrNull { it.unread > 0 } ?: d.items.firstOrNull()
            if (top == null) "Your inbox is open. Want me to find a mail?"
            else (if (unread > 0) "$unread unread mail${if (unread > 1) "s" else ""}; newest from ${top.title}" else "Inbox is calm; latest from ${top.title}") +
                (if (top.detail.isNotBlank()) ": ${q(top.detail, 45)}" else "") + ". Open it?"
        }
        ScreenType.EMAIL -> "A mail" + (d.facts["from"]?.let { " from $it" } ?: "") + ": ${q(d.title.ifBlank { "(no subject)" }, 55)}. Want a summary or a reply draft? ✉️"
        ScreenType.SETTINGS -> "You're in Settings" + (if (d.title.isNotBlank()) " › ${d.title}" else "") + ". Tell me which switch you're hunting for and I'll find it! 🔧"
        ScreenType.SHOPPING -> d.items.firstOrNull()?.let { "Shopping! ${q(it.title, 45)} for ${it.detail}. Want me to check if it's cheaper elsewhere? 🛒" }
            ?: "Window shopping on ${d.app}? Want me to compare prices?"
        ScreenType.FOOD -> d.items.firstOrNull()?.let { "Food time! ${q(it.title, 40)} for ${it.detail} 😋 Bananas not on the menu? Want me to help pick?" }
            ?: "Ordering food on ${d.app}? Get me a banana shake too! 🍌"
        ScreenType.MAPS -> d.facts["eta"]?.let { "Maps says about $it" + (d.facts["distance"]?.let { x -> " ($x)" } ?: "") + (if (d.title.isNotBlank()) " to ${d.title}" else "") + ". Want a reminder to leave on time? 🗺️" }
            ?: "You're in Maps. Where are we going? I'll ride on the dashboard 🐒"
        ScreenType.HOME -> "Just your home screen. Open something fun and I'll tag along! 🐒"
        ScreenType.OTHER -> {
            val main = d.title.ifBlank { d.items.firstOrNull()?.title.orEmpty() }
            if (main.isBlank()) "You're in ${d.app}, but there's not much text I can read here. Want me to try tapping something?"
            else "You're in ${d.app}; the main thing here is ${q(main, 50)}. Want me to read more or tap something?"
        }
    }

    private fun chatSummary(d: ScreenDigest): String {
        if (d.type != ScreenType.CONVERSATION || d.items.isEmpty()) return "Open a chat first, then tap “Summarise this chat”! 💬"
        val them = d.items.filter { !it.fromMe }; val mine = d.items.filter { it.fromMe }
        val sb = StringBuilder("Chat with ${d.title.ifBlank { "them" }}: ")
        them.lastOrNull()?.let { sb.append("they said ${q(it.detail, 50)}") }
        mine.lastOrNull()?.let { sb.append(if (them.isNotEmpty()) ", " else "").append("you said ${q(it.detail, 40)}") }
        sb.append(". ")
        sb.append(if (d.items.last().fromMe) "Ball's in their court! 🏏" else "Your turn to reply. Want a draft?")
        return sb.toString()
    }

    /** No-brain draft: a safe, short, friendly reply to the last incoming message. */
    fun draftReply(d: ScreenDigest): String {
        val last = d.items.lastOrNull { !it.fromMe }?.detail.orEmpty().lowercase()
        return when {
            last.isBlank() -> "Hey! 😊"
            Regex("\\b(eat|ate|saptiya|sapta|lunch|dinner)\\b").containsMatchIn(last) -> "Yes, ate! You? 😊"
            Regex("\\b(where|enga)\\b").containsMatchIn(last) -> "On the way, will update soon!"
            Regex("\\b(call|phone)\\b").containsMatchIn(last) -> "Will call you in a bit! 📞"
            Regex("\\b(thanks|thank you|thx)\\b").containsMatchIn(last) -> "Anytime! 😊"
            last.contains("?") -> "Yes, sounds good! 👍"
            else -> "Okay, noted! 👍"
        }
    }

    private fun roast(d: ScreenDigest): String = when (d.type) {
        ScreenType.CHAT_LIST -> if (d.unreadChats >= 3) "${d.unreadChats} unread chats?! You're not 'busy', you're ghosting with extra steps 👻" else "Your chat list is so calm, even the blue ticks are bored. 😴"
        ScreenType.CONVERSATION -> if (d.items.lastOrNull()?.fromMe == true) "Double-texting ${d.title.ifBlank { "them" }}? Bold. Very bold. 😂" else "${d.title.ifBlank { "They" }} is waiting and you're asking a monkey for help. Iconic. 🐒"
        ScreenType.VIDEO, ScreenType.VIDEO_LIST -> "“Just one more video,” said every human at 2 AM. 📺😂"
        ScreenType.FEED -> "Your thumb has scrolled more kilometres than I've climbed trees. 🌴😂"
        ScreenType.SHOPPING, ScreenType.FOOD -> "Add to cart, remove from cart, add to cart... the cart is getting dizzy! 🛒😵"
        ScreenType.SETTINGS -> "Lost in Settings again? Even I need a map in here. 🗺️"
        ScreenType.EMAIL_LIST -> "Your inbox looks like my banana stash: overflowing and slightly scary. 📬"
        ScreenType.HOME -> "Staring at the home screen like the fridge: hoping something new appears. 😂"
        else -> "This screen is so serious, I'm putting on my reading glasses. 🤓"
    }

    private fun worth(d: ScreenDigest): String {
        val title = d.title.ifBlank { d.items.firstOrNull()?.title.orEmpty() }
        if (title.isBlank()) return "Open a video and I'll give you my expert monkey verdict! 🍿"
        val dur = d.facts["duration"] ?: d.items.firstOrNull()?.time.orEmpty()
        val mins = Regex("^(\\d+):(\\d{2})$").find(dur)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("^(\\d+):(\\d{2}):(\\d{2})$").find(dur)?.let { it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
        val clickbait = Regex("(?i)(shocking|you won'?t believe|gone wrong|!!!|\\?\\?|insane|crazy)").containsMatchIn(title) || title.count { it.isUpperCase() } > title.length / 2
        return when {
            clickbait -> "${q(title, 45)} smells like clickbait 🎣. Watch at 2x or skip!"
            mins != null && mins >= 40 -> "${q(title, 45)} is ${dur} long. Worth it if you've got snacks and no deadlines 🍿"
            mins != null && mins <= 10 -> "${q(title, 45)}, only $dur. Quick watch, go for it! 👍"
            else -> "${q(title, 45)}" + (d.facts["channel"]?.let { " from $it" } ?: "") + " looks decent. Try the first 2 minutes and decide! 🎬"
        }
    }

    // ---------------- quick chips ----------------

    /** Context-aware chips for a screen (shown in the arc / chat panel / after a screen answer). */
    fun contextChips(type: ScreenType?): List<QuickChip> = when (type) {
        ScreenType.VIDEO, ScreenType.VIDEO_LIST -> listOf(QuickChip.WORTH)
        ScreenType.SHOPPING, ScreenType.FOOD -> listOf(QuickChip.CHEAPER)
        ScreenType.MAPS -> listOf(QuickChip.HOW_LONG)
        ScreenType.CONVERSATION -> listOf(QuickChip.SUMMARISE, QuickChip.REPLY_FOR_ME)
        ScreenType.CHAT_LIST, ScreenType.EMAIL_LIST -> listOf(QuickChip.MISS)
        else -> emptyList()
    }

    /** Follow-up chips after an answer about [d] (max 3). */
    fun followUps(mode: Mode, d: ScreenDigest): List<QuickChip> {
        val out = ArrayList<QuickChip>()
        when (d.type) {
            ScreenType.CHAT_LIST, ScreenType.EMAIL_LIST -> (d.items.firstOrNull { it.unread > 0 } ?: d.items.firstOrNull())?.let { out.add(QuickChip.open(it.title)) }
            else -> {}
        }
        out.addAll(contextChips(d.type).filter { it.id != modeChip(mode) })
        if (mode != Mode.ROAST) out.add(QuickChip.ROAST)
        return out.distinctBy { it.id + it.arg }.take(3)
    }

    private fun modeChip(m: Mode) = when (m) {
        Mode.CHAT_SUMMARY -> "summarise"; Mode.REPLY_DRAFT -> "reply_for_me"; Mode.ROAST -> "roast"
        Mode.WORTH -> "worth"; Mode.CHEAPER -> "cheaper"; Mode.HOW_LONG -> "how_long"; Mode.SUMMARY -> "screen"
    }
}

/** A quick-tap chip: [id] says what it does, [arg] carries a target (a chat name to open...). */
data class QuickChip(val id: String, val label: String, val arg: String = "") {
    companion object {
        val MISS = QuickChip("miss", "What did I miss?")
        val SCREEN = QuickChip("screen", "What's on screen?")
        val SUMMARISE = QuickChip("summarise", "Summarise this chat")
        val REPLY_FOR_ME = QuickChip("reply_for_me", "Reply for me")
        val ROAST = QuickChip("roast", "Roast this screen 😂")
        val FUNNY = QuickChip("funny", "Do something funny")
        val THINK = QuickChip("think", "What's Settai thinking?")
        val DIARY = QuickChip("diary", "📖 Settai's diary")
        val FOCUS = QuickChip("focus", "📚 Focus 25m")
        val BREATHE = QuickChip("breathe", "🫧 Breathe 60s")
        val HUG = QuickChip("comfort_hug", "🫂 Screen hug")
        val SIGN = QuickChip("comfort_sign", "🪧 Cheer sign")
        val COFFEE = QuickChip("comfort_coffee", "☕ Filter coffee")
        val CAMPFIRE = QuickChip("campfire", "🔥 Campfire")
        val NOTIF_CARD = QuickChip("notif_card", "Yes, show me 📬")
        val PERM_NOTIFS = QuickChip("perm_notifs", "Grant notification access 🔑")
        val WORTH = QuickChip("worth", "Is this worth watching?")
        val CHEAPER = QuickChip("cheaper", "Find cheaper?")
        val HOW_LONG = QuickChip("how_long", "How long?")
        val TALK = QuickChip("talk", "🎤 Talk")
        val CHAT = QuickChip("chat", "💬 Chat")
        val QUIT = QuickChip("quit", "✕ Quit")
        val LATER = QuickChip("later", "Later")
        val OK = QuickChip("later", "Got it")
        fun open(name: String) = QuickChip("open", "Open ${name.take(14)}", name)
        fun say(label: String, text: String) = QuickChip("say", label, text)

        /** The chat panel's / bubble's standard row (before context chips). */
        val STANDARD = listOf(THINK, MISS, SCREEN, SUMMARISE, REPLY_FOR_ME, ROAST, FUNNY)
    }
}
