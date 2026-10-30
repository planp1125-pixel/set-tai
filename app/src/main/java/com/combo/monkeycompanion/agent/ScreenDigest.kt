package com.combo.monkeycompanion.agent

/**
 * v4.2 smart screen reading: turns a [ScreenSnapshot] (the accessibility tree) into a short, structured
 * digest: which app, what kind of screen, the few things that matter (chat rows with unread counts, the
 * last messages of a conversation, the video being watched, an article's headline...), and the buttons
 * separately. Pure Kotlin so it is unit-tested with fake trees.
 *
 * Privacy: password fields are never read; OTP-looking codes are masked ([Otp.mask]); blocked apps never get
 * this far (the caller runs [AgentSafety.screen] first).
 */
enum class ScreenType(val label: String) {
    CHAT_LIST("chat list"), CONVERSATION("conversation"), FEED("feed"), VIDEO("video"), VIDEO_LIST("video list"),
    ARTICLE("article"), BROWSER("web page"), EMAIL_LIST("inbox"), EMAIL("email"), SETTINGS("settings"),
    SHOPPING("shopping"), FOOD("food ordering"), MAPS("maps"), HOME("home screen"), LOCKED("lock screen"), OTHER("screen")
}

/** One meaningful thing on screen. [role]: chat, msg, video, post, mail, setting, product, para, text. */
data class DItem(
    val role: String,
    val title: String,
    val detail: String = "",
    val time: String = "",
    val unread: Int = 0,
    val fromMe: Boolean = false,
)

data class ScreenDigest(
    val pkg: String,
    val app: String,
    val type: ScreenType,
    /** Conversation partner, video title, page/article headline, settings page... */
    val title: String = "",
    val items: List<DItem> = emptyList(),
    val buttons: List<String> = emptyList(),
    /** Extra facts: url, channel, duration, views, eta, distance, price, subtitle. */
    val facts: Map<String, String> = emptyMap(),
) {
    val unreadChats get() = items.count { it.unread > 0 }
    val unreadTotal get() = items.sumOf { it.unread }
    val empty get() = items.isEmpty() && title.isBlank()
}

object Digest {
    // ---------------- package families ----------------
    private val WHATSAPP = setOf("com.whatsapp", "com.whatsapp.w4b")
    private val YOUTUBE = setOf("com.google.android.youtube", "app.revanced.android.youtube", "com.google.android.apps.youtube.music")
    private val BROWSERS = setOf("com.android.chrome", "com.chrome.beta", "org.mozilla.firefox", "com.brave.browser", "com.microsoft.emmx", "com.sec.android.app.sbrowser", "com.opera.browser")
    private val INSTAGRAM = setOf("com.instagram.android", "com.instagram.lite")
    private val GMAIL = setOf("com.google.android.gm")
    private val SETTINGS = setOf("com.android.settings", "com.samsung.android.settings", "com.motorola.settings")
    private val SHOPS = setOf("in.amazon.mShop.android.shopping", "com.amazon.mShop.android.shopping", "com.flipkart.android", "com.myntra.android", "com.meesho.supply", "com.ril.ajio", "com.nykaa.app", "com.jiomart.app", "com.grofers.customerapp", "com.zeptoconsumerapp", "com.bigbasket.mobileapp")
    private val FOODS = setOf("in.swiggy.android", "com.application.zomato")
    private val MAPS = setOf("com.google.android.apps.maps", "com.google.android.apps.mapslite")

    fun isHome(pkg: String) = pkg.contains("launcher", true) || pkg == "com.google.android.apps.nexuslauncher" || pkg == "com.sec.android.app.launcher" || pkg == "com.miui.home" || pkg == "com.oppo.launcher"

    // ---------------- text helpers ----------------
    internal val TIME = Regex("(?i)^(\\d{1,2}[:.]\\d{2}\\s?(am|pm|a\\.m\\.|p\\.m\\.)?|yesterday|today|now|just now|\\d+\\s?(m|min|mins|h|hr|hrs|d|w)( ago)?|mon(day)?|tue(sday)?|wed(nesday)?|thu(rsday)?|fri(day)?|sat(urday)?|sun(day)?|\\d{1,2}/\\d{1,2}/\\d{2,4}|\\d{1,2} [a-z]{3})$")
    private val DURATION = Regex("^\\d{1,2}(:\\d{2}){1,2}$")
    private val PRICE = Regex("₹\\s?[\\d,]+(\\.\\d{1,2})?|Rs\\.?\\s?[\\d,]+|\\$\\s?[\\d,]+(\\.\\d{2})?")
    private val UNREAD_DESC = Regex("(?i)(\\d+) unread")
    private val COUNT = Regex("^\\d{1,3}$")
    private val DATE_SEP = Regex("(?i)^(today|yesterday|[a-z]+ \\d{1,2}, \\d{4}|\\d{1,2} [a-z]+ \\d{4}|messages and calls are end-to-end encrypted.*|unread messages?|\\d+ unread messages?)$")
    private val STATUS_DESC = Regex("(?i)^(read|delivered|sent|pending|seen)$")

    private fun clean(s: String) = Otp.mask(s.replace(Regex("\\s+"), " ").trim())
    private fun label(n: UiNode) = if (n.password) "" else clean(n.text.ifBlank { n.desc })
    private fun short(s: String, max: Int) = if (s.length <= max) s else s.take(max - 1).trimEnd() + "…"
    private fun id(n: UiNode) = n.viewId.substringAfterLast('/').lowercase()

    /** Index → node, parent → children (by [UiNode.parent]). */
    private class Tree(val s: ScreenSnapshot) {
        val byIndex = s.nodes.associateBy { it.index }
        val kids: Map<Int, List<UiNode>> = s.nodes.groupBy { it.parent }
        fun descendants(n: UiNode): List<UiNode> {
            val out = ArrayList<UiNode>(); val st = ArrayDeque<UiNode>(); kids[n.index]?.let { st.addAll(it) }
            while (st.isNotEmpty() && out.size < 60) { val x = st.removeFirst(); out.add(x); kids[x.index]?.let { st.addAll(it) } }
            return out.sortedWith(compareBy({ it.top }, { it.left }, { it.index }))
        }
        fun ancestors(n: UiNode): Sequence<UiNode> = generateSequence(byIndex[n.parent]) { byIndex[it.parent] }
        fun hasParents() = s.nodes.any { it.parent != 0 }
    }

    /** A tappable container whose subtree carries 2..8 texts: a list row (chat, mail, video, product...). */
    private class Row(val node: UiNode, val parts: List<UiNode>) {
        val texts get() = parts.map { label(it) }.filter { it.isNotEmpty() }
    }

    private fun rows(t: Tree): List<Row> {
        if (!t.hasParents()) return emptyList()
        val cands = t.s.nodes.filter { it.clickable && !it.editable }.mapNotNull { n ->
            val d = t.descendants(n).filter { !it.password && (it.text.isNotBlank() || it.desc.isNotBlank()) }
            val own = if (n.text.isNotBlank() || n.desc.isNotBlank()) listOf(n) else emptyList()
            val parts = own + d
            if (parts.size in 2..8) Row(n, parts) else null
        }
        // Outermost rows only (a row's inner clickable bits are part of it).
        val chosen = cands.map { it.node.index }.toSet()
        return cands.filter { r -> t.ancestors(r.node).none { it.index in chosen } }
    }

    /** Clickable things with a label that aren't inside a row: the screen's buttons / tabs. */
    private fun buttons(t: Tree, rows: List<Row>, max: Int = 8): List<String> {
        val inRow = HashSet<Int>()
        rows.forEach { r -> inRow.add(r.node.index); r.parts.forEach { inRow.add(it.index) } }
        return t.s.nodes.asSequence().filter { it.clickable && !it.editable && !it.password && it.index !in inRow }
            .map { short(label(it), 28) }.filter { it.length in 2..28 && !TIME.matches(it) }.distinct().take(max).toList()
    }

    // ---------------- entry point ----------------
    fun build(s: ScreenSnapshot): ScreenDigest {
        if (s.locked) return ScreenDigest(s.pkg, s.app, ScreenType.LOCKED)
        val t = Tree(s)
        val d = when {
            s.pkg in WHATSAPP -> whatsapp(t)
            s.pkg in YOUTUBE -> youtube(t)
            s.pkg in INSTAGRAM -> instagram(t)
            s.pkg in GMAIL -> gmail(t)
            s.pkg in BROWSERS -> browser(t)
            s.pkg in SETTINGS -> settings(t)
            s.pkg in MAPS -> maps(t)
            s.pkg in FOODS -> shopping(t, ScreenType.FOOD)
            s.pkg in SHOPS -> shopping(t, ScreenType.SHOPPING)
            isHome(s.pkg) -> ScreenDigest(s.pkg, s.app, ScreenType.HOME, buttons = buttons(t, emptyList(), 6))
            else -> null
        }
        return d?.takeIf { !it.empty || it.type == ScreenType.HOME } ?: generic(t)
    }

    // ---------------- WhatsApp ----------------
    private fun messageBox(t: Tree) = t.s.nodes.firstOrNull { it.editable && !it.password &&
        (id(it) == "entry" || label(it).contains("message", true) || it.desc.contains("message", true)) }

    private fun whatsapp(t: Tree): ScreenDigest {
        val s = t.s
        val box = messageBox(t)
        val header = s.nodes.firstOrNull { id(it).contains("conversation_contact_name") || id(it) == "contact_name" }
        if (box != null || header != null) return whatsConversation(t, header)
        return chatList(t, ScreenType.CHAT_LIST)
    }

    private fun whatsConversation(t: Tree, header: UiNode?): ScreenDigest {
        val s = t.s
        val title = header?.let { label(it) } ?: s.nodes.firstOrNull { !it.editable && label(it).length in 2..40 && !TIME.matches(label(it)) }?.let { label(it) }.orEmpty()
        val sub = s.nodes.firstOrNull { id(it).contains("conversation_contact_status") }?.let { label(it) }.orEmpty()
        var msgs = s.nodes.filter { id(it) == "message_text" || id(it).endsWith("message_text") }
        if (msgs.isEmpty()) {
            // No view ids: text that isn't the header, a time, a date separator, a button or the box.
            msgs = s.nodes.filter { n -> !n.clickable && !n.editable && n.text.isNotBlank() && n != header &&
                label(n).let { l -> l != title && l != sub && !TIME.matches(l) && !DATE_SEP.matches(l) && l.length >= 2 } }
        }
        val items = msgs.map { m ->
            val row = t.byIndex[m.parent]
            val sibs = row?.let { t.descendants(it) }.orEmpty()
            val time = sibs.firstOrNull { TIME.matches(label(it)) && it != m }?.let { label(it) }.orEmpty()
            val me = sibs.any { STATUS_DESC.matches(it.desc.trim()) || id(it) == "status" }
            val sender = sibs.firstOrNull { id(it).contains("name_in_group") }?.let { label(it) }
            DItem("msg", if (me) "You" else (sender ?: title.ifBlank { "Them" }), label(m), time, fromMe = me)
        }.distinctBy { it.title + it.detail + it.time }.takeLast(8)
        val facts = if (sub.isNotBlank()) mapOf("status" to sub) else emptyMap()
        val btns = s.nodes.filter { it.clickable && !it.editable && it != header }.map { short(label(it), 24) }
            .filter { it.length in 2..24 && it != title && items.none { m -> m.detail == it } }.distinct().take(6)
        return ScreenDigest(s.pkg, s.app, ScreenType.CONVERSATION, title, items, btns, facts)
    }

    /** Chat-style rows: name, last message, time, unread count (WhatsApp, Instagram DMs, generic). */
    private fun chatList(t: Tree, type: ScreenType): ScreenDigest {
        val s = t.s
        val rs = rows(t)
        val items = rs.mapNotNull { r -> chatRow(r) }.take(12)
        return ScreenDigest(s.pkg, s.app, type, "", items, buttons(t, rs))
    }

    private fun chatRow(r: Row): DItem? {
        val parts = r.parts
        val name = parts.firstOrNull { id(it).contains("contact_name") || id(it).contains("username") || id(it).contains("thread_title") }?.let { label(it) }
        var unread = 0; var time = ""
        val rest = ArrayList<String>()
        for (p in parts) {
            val l = label(p)
            if (l.isEmpty()) continue
            val ud = UNREAD_DESC.find(p.desc)
            when {
                ud != null -> unread = ud.groupValues[1].toInt()
                p.text.isBlank() && p != r.node -> {}   // icons / avatars ("Profile photo") aren't content
                id(p).contains("message_count") || id(p).contains("unread") -> unread = l.filter { it.isDigit() }.toIntOrNull() ?: 1
                COUNT.matches(l) && unread == 0 -> unread = l.toInt()
                time.isEmpty() && TIME.matches(l) -> time = l
                else -> rest.add(l)
            }
        }
        val who = name ?: rest.firstOrNull() ?: return null
        rest.remove(who)
        val preview = rest.filter { it != who && !STATUS_DESC.matches(it) }.maxByOrNull { it.length }.orEmpty()
        if (preview.isEmpty() && time.isEmpty()) return null
        return DItem("chat", short(who, 40), short(preview.removePrefix("~").trim(), 90), time, unread)
    }

    // ---------------- YouTube ----------------
    /** "Title - 12 minutes, 3 seconds - Go to channel - Channel - 1.2M views - 3 years ago - play video". */
    internal fun parseVideoDesc(desc: String): DItem? {
        val parts = desc.split(" - ", " · ").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 3 || parts.none { it.contains("views", true) || it.contains("play video", true) || it.contains("watching", true) }) return null
        val title = parts[0]
        val dur = parts.drop(1).firstOrNull { it.contains(Regex("(?i)\\b(second|minute|hour)s?\\b")) || DURATION.matches(it) }.orEmpty()
        val ci = parts.indexOfFirst { it.equals("Go to channel", true) }
        val channel = if (ci >= 0) parts.getOrNull(ci + 1).orEmpty() else parts.firstOrNull { it != title && it != dur && !it.contains("views", true) && !it.contains("ago", true) && !it.contains("play video", true) }.orEmpty()
        val views = parts.firstOrNull { it.contains("views", true) }.orEmpty()
        return DItem("video", short(title, 90), listOf(channel, views).filter { it.isNotBlank() }.joinToString(" · "), dur)
    }

    private fun youtube(t: Tree): ScreenDigest {
        val s = t.s
        val feed = s.nodes.mapNotNull { n -> parseVideoDesc(n.desc)?.let { it to n } }.distinctBy { it.first.title }
        val subscribe = s.nodes.any { label(it).equals("Subscribe", true) || label(it).equals("Subscribed", true) || it.desc.startsWith("Subscribe to", true) }
        val viewsNode = s.nodes.firstOrNull { label(it).contains(Regex("(?i)\\b[\\d.,]+[kml]? views\\b")) && label(it).length < 80 }
        val isShorts = s.nodes.any { it.desc.equals("Shorts", true) && it.focused } || s.nodes.any { id(it).contains("reel") }
        if (subscribe && (viewsNode != null || feed.size <= 2) || isShorts && subscribe) {
            // Watch page: title is the first longish line that isn't views / channel / a button.
            val subsNode = s.nodes.firstOrNull { label(it).contains("subscribers", true) }
            val title = s.nodes.firstOrNull { n -> val l = label(n); id(n).contains("title") && l.length >= 6 && !l.contains(" views", true) }?.let { label(it) }
                ?: s.nodes.firstOrNull { n -> val l = label(n); !n.clickable && l.length in 12..150 && !l.contains(" views", true) && !l.contains("subscribers", true) && !TIME.matches(l) }?.let { label(it) }
                ?: feed.firstOrNull()?.first?.title.orEmpty()
            val channel = s.nodes.firstOrNull { it.desc.startsWith("Subscribe to", true) }?.desc?.removePrefix("Subscribe to")?.trim()?.trimEnd('.')
                ?: subsNode?.let { sn -> val i = s.nodes.indexOf(sn); s.nodes.getOrNull(i - 1)?.let { label(it) } }
                ?: s.nodes.firstOrNull { id(it).contains("channel_name") || id(it).contains("owner") }?.let { label(it) }.orEmpty()
            val dur = s.nodes.firstOrNull { val l = label(it); l.contains(" / ") && DURATION.matches(l.substringAfter(" / ").trim()) }?.let { label(it).substringAfter(" / ").trim() }
                ?: s.nodes.firstOrNull { DURATION.matches(label(it)) }?.let { label(it) }.orEmpty()
            val facts = LinkedHashMap<String, String>()
            if (channel.isNotBlank()) facts["channel"] = short(channel, 40)
            if (dur.isNotBlank()) facts["duration"] = dur
            viewsNode?.let { facts["views"] = short(label(it), 60) }
            subsNode?.let { facts["subscribers"] = short(label(it), 30) }
            if (isShorts) facts["format"] = "Short"
            val next = feed.map { it.first }.filter { it.title != title }.take(3)
            return ScreenDigest(s.pkg, s.app, ScreenType.VIDEO, short(title, 120), next, buttons(t, emptyList(), 6).filter { it.length < 20 }, facts)
        }
        if (feed.isNotEmpty()) return ScreenDigest(s.pkg, s.app, ScreenType.VIDEO_LIST, "", feed.map { it.first }.take(8), buttons(t, rows(t), 6))
        return generic(t)
    }

    // ---------------- Instagram ----------------
    private fun instagram(t: Tree): ScreenDigest {
        val s = t.s
        val ids = s.nodes.map { id(it) }
        if (ids.any { it.contains("row_inbox") || it.contains("direct_thread") || it.contains("inbox_refreshable") }) return chatList(t, ScreenType.CHAT_LIST)
        val names = s.nodes.filter { id(it).contains("profile_name") || id(it).contains("row_feed_photo_profile_name") }
        val reel = s.nodes.firstOrNull { it.desc.startsWith("Reel by", true) || id(it).contains("clips_") }
        val captions = s.nodes.filter { id(it).contains("comment_textview") || id(it).contains("caption") }
        if (reel != null && names.isEmpty()) {
            val by = reel.desc.removePrefix("Reel by").substringBefore('.').trim()
            val cap = captions.firstOrNull()?.let { label(it) }.orEmpty()
            return ScreenDigest(s.pkg, s.app, ScreenType.VIDEO, if (by.isNotBlank()) "Reel by $by" else "Reel", emptyList(), emptyList(), mapOf("format" to "Reel") + (if (cap.isNotBlank()) mapOf("caption" to short(cap, 90)) else emptyMap()))
        }
        if (names.isNotEmpty()) {
            val items = names.mapIndexed { i, n -> DItem("post", label(n), short(captions.getOrNull(i)?.let { label(it) }.orEmpty(), 90)) }.distinctBy { it.title + it.detail }.take(4)
            return ScreenDigest(s.pkg, s.app, ScreenType.FEED, "", items, buttons(t, emptyList(), 6))
        }
        return generic(t)
    }

    // ---------------- Gmail ----------------
    private fun gmail(t: Tree): ScreenDigest {
        val s = t.s
        val subjectView = s.nodes.firstOrNull { id(it).contains("subject_and_folder") || id(it) == "conversation_subject" }
        val replyBtn = s.nodes.any { it.clickable && label(it).equals("Reply", true) }
        if (subjectView != null || (replyBtn && s.nodes.none { id(it) == "senders" })) {
            val subject = subjectView?.let { label(it) } ?: s.nodes.firstOrNull { label(it).length in 6..140 && !it.clickable }?.let { label(it) }.orEmpty()
            val from = s.nodes.firstOrNull { id(it).contains("sender_name") || id(it) == "from" }?.let { label(it) }.orEmpty()
            val body = s.nodes.filter { !it.clickable && label(it).length >= 60 }.map { short(label(it), 220) }.take(2)
            return ScreenDigest(s.pkg, s.app, ScreenType.EMAIL, short(subject, 120), body.map { DItem("para", it) }, buttons(t, emptyList(), 6), if (from.isNotBlank()) mapOf("from" to from) else emptyMap())
        }
        val rs = rows(t)
        val items = rs.mapNotNull { r ->
            // Gmail rows usually carry a summary description "Unread, Sender, Subject, snippet, time".
            val sender = r.parts.firstOrNull { id(it) == "senders" }?.let { label(it) }
            val subject = r.parts.firstOrNull { id(it) == "subject" }?.let { label(it) }
            val snippet = r.parts.firstOrNull { id(it) == "snippet" }?.let { label(it) }.orEmpty()
            val time = r.parts.firstOrNull { id(it) == "date" || TIME.matches(label(it)) }?.let { label(it) }.orEmpty()
            val unread = r.node.desc.startsWith("Unread", true) || r.parts.any { it.desc.startsWith("Unread", true) }
            if (sender != null || subject != null) DItem("mail", short(sender ?: "?", 40), short(listOfNotNull(subject, snippet.takeIf { it.isNotBlank() }).joinToString(" — "), 110), time, if (unread) 1 else 0)
            else chatRow(r)?.copy(role = "mail")
        }.take(10)
        if (items.isEmpty()) return generic(t)
        return ScreenDigest(s.pkg, s.app, ScreenType.EMAIL_LIST, "", items, buttons(t, rs))
    }

    // ---------------- browsers ----------------
    private fun browser(t: Tree): ScreenDigest {
        val s = t.s
        val url = s.nodes.firstOrNull { id(it).contains("url_bar") || id(it).contains("url_field") || id(it).contains("mozac_browser_toolbar_url") }?.let { label(it) }.orEmpty()
        val content = s.nodes.filter { !it.editable && it.text.isNotBlank() && label(it) != url }
        val paras = content.filter { label(it).length >= 80 }.map { short(label(it), 260) }.distinct()
        val head = content.firstOrNull { n -> val l = label(n); l.length in 15..160 && !l.contains("http") && (n.cls.contains("Heading", true) || paras.isEmpty() || content.indexOf(n) < content.indexOfFirst { label(it).length >= 80 }) }
            ?.let { label(it) }.orEmpty()
        val facts = if (url.isNotBlank()) mapOf("url" to short(url, 80)) else emptyMap()
        if (paras.isNotEmpty()) return ScreenDigest(s.pkg, s.app, ScreenType.ARTICLE, short(head.ifBlank { paras.first().substringBefore(". ") }, 140), paras.take(3).map { DItem("para", it) }, emptyList(), facts)
        val links = content.filter { it.clickable || t.byIndex[it.parent]?.clickable == true }.map { label(it) }.filter { it.length in 12..120 }.distinct().take(6)
        val prices = priceItems(t)
        if (prices.size >= 2) return ScreenDigest(s.pkg, s.app, ScreenType.SHOPPING, head, prices, emptyList(), facts)
        return ScreenDigest(s.pkg, s.app, ScreenType.BROWSER, head, links.map { DItem("text", it) }, buttons(t, emptyList(), 5), facts)
    }

    // ---------------- Settings ----------------
    private fun settings(t: Tree): ScreenDigest {
        val s = t.s
        val title = s.nodes.firstOrNull { id(it).contains("collapsing_toolbar") || id(it) == "action_bar" || id(it).contains("toolbar_title") || id(it) == "homepage_title" }?.let { label(it) }
            ?: s.nodes.firstOrNull { !it.clickable && label(it).length in 3..40 }?.let { label(it) }.orEmpty()
        val rs = rows(t)
        val items = rs.mapNotNull { r ->
            val name = r.parts.firstOrNull { id(it) == "title" }?.let { label(it) } ?: r.texts.firstOrNull() ?: return@mapNotNull null
            val sum = r.parts.firstOrNull { id(it) == "summary" }?.let { label(it) } ?: r.texts.drop(1).firstOrNull().orEmpty()
            val sw = (listOf(r.node) + r.parts).firstOrNull { it.checked != null }?.checked
            DItem("setting", short(name, 40), short(sum, 70), if (sw == null) "" else if (sw) "on" else "off")
        }.distinctBy { it.title }.take(10)
        return ScreenDigest(s.pkg, s.app, ScreenType.SETTINGS, title, items, emptyList())
    }

    // ---------------- Maps ----------------
    private val ETA = Regex("(?i)\\b(\\d+\\s?(h|hr|hrs|hour|hours)(\\s?\\d+\\s?(m|min|mins))?|\\d+\\s?(min|mins|minutes))\\b")
    private val DIST = Regex("(?i)\\b\\d+(\\.\\d+)?\\s?(km|m|mi)\\b")
    private fun maps(t: Tree): ScreenDigest {
        val s = t.s
        val all = s.nodes.map { label(it) }.filter { it.isNotEmpty() }
        val eta = all.firstNotNullOfOrNull { l -> ETA.find(l)?.value?.takeIf { l.length < 60 } }
        val dist = all.firstNotNullOfOrNull { l -> DIST.find(l)?.value?.takeIf { l.length < 60 } }
        val dest = s.nodes.firstOrNull { it.editable }?.let { label(it) }?.takeIf { it.isNotBlank() && !it.contains("search", true) }
            ?: all.firstOrNull { it.startsWith("to ", true) || it.contains("Directions to", true) }?.removePrefix("Directions to")?.trim().orEmpty()
        val via = all.firstOrNull { it.startsWith("via ", true) }.orEmpty()
        val facts = LinkedHashMap<String, String>()
        eta?.let { facts["eta"] = it }; dist?.let { facts["distance"] = it }
        if (via.isNotBlank()) facts["via"] = short(via, 50)
        all.firstOrNull { it.contains("traffic", true) && it.length < 70 }?.let { facts["traffic"] = it }
        return ScreenDigest(s.pkg, s.app, ScreenType.MAPS, short(dest, 60), emptyList(), buttons(t, emptyList(), 6), facts)
    }

    // ---------------- shopping / food ----------------
    private fun priceItems(t: Tree): List<DItem> {
        val rs = rows(t)
        val fromRows = rs.mapNotNull { r ->
            val texts = r.texts
            val price = texts.firstNotNullOfOrNull { PRICE.find(it)?.value } ?: return@mapNotNull null
            val name = texts.filter { PRICE.find(it) == null && it.length >= 4 }.maxByOrNull { it.length } ?: return@mapNotNull null
            DItem("product", short(name, 80), price)
        }
        if (fromRows.isNotEmpty()) return fromRows.distinctBy { it.title }.take(6)
        // Flat tree: pair each price with the nearest longer text before it.
        val ns = t.s.nodes; val out = ArrayList<DItem>()
        for ((i, n) in ns.withIndex()) {
            val p = PRICE.find(label(n))?.value ?: continue
            val name = (i - 1 downTo maxOf(0, i - 4)).map { label(ns[it]) }.firstOrNull { it.length >= 8 && PRICE.find(it) == null } ?: continue
            out.add(DItem("product", short(name, 80), p))
        }
        return out.distinctBy { it.title }.take(6)
    }

    private fun shopping(t: Tree, type: ScreenType): ScreenDigest {
        val s = t.s
        val items = priceItems(t)
        val title = s.nodes.firstOrNull { id(it).contains("title") && label(it).length >= 8 }?.let { label(it) }.orEmpty()
        val facts = items.firstOrNull()?.let { mapOf("price" to it.detail) } ?: emptyMap()
        if (items.isEmpty() && title.isBlank()) return generic(t).copy(type = type)
        return ScreenDigest(s.pkg, s.app, type, short(title.ifBlank { items.firstOrNull()?.title.orEmpty() }, 100), items, buttons(t, rows(t), 6), facts)
    }

    // ---------------- anything else ----------------
    fun generic(t0: ScreenSnapshot) = generic(Tree(t0))

    private fun generic(t: Tree): ScreenDigest {
        val s = t.s
        val rs = rows(t)
        // Chat-like rows (time + unread) anywhere = a chat list (Telegram, Signal, Messages...).
        val chats = rs.mapNotNull { chatRow(it) }
        if (chats.size >= 2 && chats.count { it.time.isNotEmpty() } >= 2 && (chats.any { it.unread > 0 } || s.pkg.contains("messag", true) || s.pkg.contains("telegram") || s.pkg.contains("signal")))
            return ScreenDigest(s.pkg, s.app, ScreenType.CHAT_LIST, "", chats.take(10), buttons(t, rs))
        if (messageBox(t) != null && s.nodes.count { !it.clickable && it.text.isNotBlank() } >= 3) {
            return whatsConversation(t, null)
        }
        val prices = priceItems(t)
        if (prices.size >= 2) return ScreenDigest(s.pkg, s.app, ScreenType.SHOPPING, "", prices, buttons(t, rs, 6), mapOf("price" to prices[0].detail))
        val texts = s.nodes.filter { !it.clickable && !it.editable && !it.password && it.text.isNotBlank() }.map { label(it) }
            .filter { it.length >= 3 && !TIME.matches(it) }.distinct()
        val paras = texts.filter { it.length >= 80 }
        if (paras.size >= 2) return ScreenDigest(s.pkg, s.app, ScreenType.ARTICLE, short(texts.first { it.length < 160 }, 120), paras.take(3).map { DItem("para", short(it, 240)) })
        val title = texts.firstOrNull { it.length in 3..50 }.orEmpty()
        val items = (rs.map { r -> DItem("text", short(r.texts.first(), 60), short(r.texts.drop(1).joinToString(" · "), 80)) } +
            texts.filter { it != title }.map { DItem("text", short(it, 90)) }).distinctBy { it.title }.take(6)
        return ScreenDigest(s.pkg, s.app, if (isHome(s.pkg)) ScreenType.HOME else ScreenType.OTHER, short(title, 60), items, buttons(t, rs))
    }

    // ---------------- output ----------------

    /**
     * The digest as short text for a model. [compact] (small on-device models): fewer items, shorter lines.
     * Example: `App: WhatsApp (chat list) / Unread: 3 chats, 7 messages / - Amma (2 unread, 10:42): "Did you eat?"`.
     */
    fun forBrain(d: ScreenDigest, compact: Boolean = false): String {
        val n = if (compact) 4 else 8
        val w = if (compact) 70 else 140
        val sb = StringBuilder("App: ${d.app} (${d.type.label})\n")
        if (d.title.isNotBlank()) sb.append(when (d.type) {
            ScreenType.CONVERSATION -> "Chat with: "; ScreenType.VIDEO -> "Video: "; ScreenType.ARTICLE -> "Headline: "
            ScreenType.EMAIL -> "Subject: "; ScreenType.MAPS -> "Destination: "; else -> "Title: "
        }).append(short(d.title, w)).append('\n')
        for ((k, v) in d.facts) sb.append(k.replaceFirstChar { it.uppercase() }).append(": ").append(short(v, w)).append('\n')
        if (d.type == ScreenType.CHAT_LIST || d.type == ScreenType.EMAIL_LIST) sb.append("Unread: ${d.unreadChats} ${if (d.type == ScreenType.EMAIL_LIST) "mails" else "chats"}${if (d.unreadTotal > d.unreadChats) ", ${d.unreadTotal} messages" else ""}\n")
        val items = if (d.type == ScreenType.CONVERSATION) d.items.takeLast(n) else d.items.take(n)
        if (items.isNotEmpty()) sb.append(when (d.type) {
            ScreenType.CHAT_LIST -> "Chats (newest first):"; ScreenType.CONVERSATION -> "Last messages:"; ScreenType.VIDEO -> "Up next:"
            ScreenType.VIDEO_LIST -> "Videos:"; ScreenType.EMAIL_LIST -> "Mails:"; ScreenType.FEED -> "Posts:"; ScreenType.SETTINGS -> "Options:"
            ScreenType.SHOPPING, ScreenType.FOOD -> "Items:"; ScreenType.ARTICLE, ScreenType.EMAIL -> "Text:"; else -> "On screen:"
        }).append('\n')
        for (it in items) sb.append("- ").append(line(it, d.type, w)).append('\n')
        if (d.buttons.isNotEmpty() && !compact) sb.append("Buttons: ").append(d.buttons.take(8).joinToString(", ")).append('\n')
        return sb.toString().trimEnd()
    }

    private fun line(i: DItem, type: ScreenType, w: Int): String = when (i.role) {
        "chat", "mail" -> buildString {
            append(i.title)
            val meta = listOfNotNull(if (i.unread > 0) (if (type == ScreenType.EMAIL_LIST) "unread" else "${i.unread} unread") else null, i.time.ifBlank { null })
            if (meta.isNotEmpty()) append(" (").append(meta.joinToString(", ")).append(')')
            if (i.detail.isNotBlank()) append(": \"").append(short(i.detail, w)).append('"')
        }
        "msg" -> "${i.title}: \"${short(i.detail, w)}\"" + (if (i.time.isNotBlank()) " (${i.time})" else "")
        "video" -> "\"${short(i.title, w)}\"" + listOf(i.detail, i.time).filter { it.isNotBlank() }.joinToString(", ").let { if (it.isEmpty()) "" else " — $it" }
        "setting" -> i.title + (if (i.time.isNotBlank()) " [${i.time}]" else "") + (if (i.detail.isNotBlank()) ": ${short(i.detail, w)}" else "")
        "product" -> "${short(i.title, w)} — ${i.detail}"
        "post" -> "@${i.title}" + (if (i.detail.isNotBlank()) ": \"${short(i.detail, w)}\"" else "")
        else -> short(i.title, w) + (if (i.detail.isNotBlank()) " — ${short(i.detail, w)}" else "")
    }
}
