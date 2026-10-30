package com.combo.monkeycompanion.agent

/** One notification as Settai sees it (kept in memory only, never on disk). */
data class NotifItem(val key: String, val pkg: String, val app: String, val title: String, val text: String, val time: Long, val canReply: Boolean) {
    val otp: String? get() = Otp.find("$title $text")
}

/** Recent notifications (in memory, capped) + pure summarising / matching. */
object NotifStore {
    private const val CAP = 40
    private val items = LinkedHashMap<String, NotifItem>()
    @Volatile var connected = false

    @Synchronized fun put(n: NotifItem) { items.remove(n.key); items[n.key] = n; while (items.size > CAP) items.remove(items.keys.first()) }
    @Synchronized fun remove(key: String) { items.remove(key) }
    @Synchronized fun reset(l: List<NotifItem>) { items.clear(); l.sortedBy { it.time }.forEach { put(it) } }
    @Synchronized fun all(): List<NotifItem> = items.values.sortedByDescending { it.time }
    val count: Int get() = all().size

    /**
     * Android 15+ can hide one-time codes from notification listeners ("Sensitive notification content hidden").
     * True when a recent notification looks like a redacted / OTP-style message without a readable code.
     */
    fun otpHidden(l: List<NotifItem>, now: Long = System.currentTimeMillis()): Boolean = l.any {
        now - it.time < 15 * 60_000L && it.otp == null && REDACTED.containsMatchIn("${it.title} ${it.text}")
    }
    private val REDACTED = Regex("(?i)sensitive (notification )?content hidden|content hidden|\\b(otp|one[- ]time (code|password)|verification code|security code)\\b")

    /** "What did I miss": grouped by app. [forBrain] masks OTP codes (they never go to an online model). */
    fun summary(l: List<NotifItem>, forBrain: Boolean, max: Int = 12): String {
        if (l.isEmpty()) return "No notifications right now."
        val sb = StringBuilder()
        val byApp = l.groupBy { it.app }
        sb.append("${l.size} notification${if (l.size == 1) "" else "s"} from ${byApp.size} app${if (byApp.size == 1) "" else "s"}:")
        var n = 0
        for ((app, list) in byApp) {
            for (x in list) {
                if (n++ >= max) { sb.append("\n…and ${l.size - max} more"); return sb.toString() }
                val body = if (x.otp != null) (if (forBrain) "a one-time code (hidden; shown to the user only)" else "code ${x.otp} 🔐 (don't share it!)")
                    else listOf(x.title, x.text).filter { it.isNotBlank() }.joinToString(": ").take(120)
                sb.append("\n• ").append(app).append(" — ").append(body).append(if (x.canReply) " [can reply]" else "")
            }
        }
        return sb.toString()
    }

    /** Best notification for "reply to Ravi" / "the WhatsApp one" (needs [replyable] for replies). */
    fun match(l: List<NotifItem>, who: String, replyable: Boolean = false): NotifItem? {
        val w = who.lowercase().trim()
        val pool = if (replyable) l.filter { it.canReply } else l
        if (w.isEmpty() || w in setOf("last", "latest", "it", "that", "this")) return pool.maxByOrNull { it.time }
        return pool.filter { it.title.lowercase().contains(w) || it.app.lowercase().contains(w) || it.text.lowercase().contains(w) }
            .maxWithOrNull(compareBy<NotifItem>({ if (it.title.lowercase().contains(w)) 2 else if (it.app.lowercase().contains(w)) 1 else 0 }, { it.time }))
    }

    fun latestOtp(l: List<NotifItem>): NotifItem? = l.filter { it.otp != null }.maxByOrNull { it.time }
}
