package com.combo.monkeycompanion.agent

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * One grouped row on Settai's compact "What You Missed" notification summary card.
 * Tapping the row fires the newest notification's `contentIntent` (via [key] / [pkg]).
 */
data class NotifCardGroup(
    val key: String,
    val pkg: String,
    val app: String,
    val title: String,
    val summaryText: String,
    val count: Int,
    val time: Long,
    val badgeColor: Int,
    val badgeGlyph: String,
)

/**
 * Smart instant notification grouper & summary builder for the "What You Missed" overlay card.
 * Groups multiple notifications by conversation/app (up to 25 groups with smooth vertical scrolling),
 * formats 1-2 line summaries, and provides layout, rendering, scrolling, and hit-testing inside `MonkeyView`.
 */
object NotifSummary {

    fun group(items: List<NotifItem>, maxGroups: Int = 25): List<NotifCardGroup> {
        if (items.isEmpty()) return emptyList()
        val sorted = items.sortedByDescending { it.time }
        val map = LinkedHashMap<String, MutableList<NotifItem>>()
        for (n in sorted) {
            val cleanSender = n.title.substringBefore(" (").substringBefore(":").trim()
            val groupKey = if (cleanSender.isNotBlank() && !cleanSender.equals(n.app, ignoreCase = true) && NotifKinds.isMessage(n.pkg)) {
                "${n.pkg}|${cleanSender.lowercase(Locale.ROOT)}"
            } else if (n.title.contains("File Sharing", ignoreCase = true) || n.title.contains("Software", ignoreCase = true)) {
                "${n.pkg}|${n.title.trim().lowercase(Locale.ROOT)}"
            } else if (n.title.isNotBlank()) {
                "${n.pkg}|${n.title.trim().lowercase(Locale.ROOT)}"
            } else {
                n.pkg
            }
            map.getOrPut(groupKey) { ArrayList() }.add(n)
        }

        return map.values.take(maxGroups).map { list ->
            val newest = list.first()
            val count = list.size
            val rawTitle = newest.title.trim()
            val baseTitle = when {
                rawTitle.isBlank() || rawTitle.equals(newest.app, ignoreCase = true) -> newest.app
                rawTitle.startsWith(newest.app, ignoreCase = true) ||
                    rawTitle.contains("File Sharing", ignoreCase = true) ||
                    rawTitle.contains("Software", ignoreCase = true) ||
                    rawTitle.contains("Update", ignoreCase = true) -> rawTitle
                NotifKinds.isMessage(newest.pkg) -> "${newest.app} · ${rawTitle.substringBefore(" (")}"
                else -> rawTitle
            }
            val displayTitle = if (count > 1 && !baseTitle.endsWith(")")) "$baseTitle ($count)" else baseTitle

            val otpItem = list.firstOrNull { it.otp != null }
            val summary = when {
                otpItem != null -> "🔐 One-time code ${otpItem.otp} from ${newest.app}. Tap to open."
                count == 1 -> newest.text.trim().ifBlank { rawTitle.ifBlank { "New notification from ${newest.app}" } }.take(115)
                else -> {
                    val snippets = list.map { it.text.trim().ifBlank { it.title.trim() } }.filter { it.isNotBlank() }.distinct().take(3)
                    if (snippets.size == 1) "${count} updates: ${snippets.first()}".take(115)
                    else "$count messages: " + snippets.joinToString(" · ").take(100)
                }
            }
            val (col, glyph) = badgeStyleFor(newest.pkg, newest.app, baseTitle)
            NotifCardGroup(
                key = newest.key,
                pkg = newest.pkg,
                app = newest.app,
                title = displayTitle,
                summaryText = summary,
                count = count,
                time = newest.time,
                badgeColor = col,
                badgeGlyph = glyph,
            )
        }
    }

    fun badgeStyleFor(pkg: String, app: String, title: String): Pair<Int, String> {
        val p = pkg.lowercase(Locale.ROOT)
        val t = title.lowercase(Locale.ROOT)
        return when {
            "whatsapp" in p -> Color.rgb(0x25, 0xD3, 0x66) to "W"
            "share" in t || "nearby" in p || "files" in p -> Color.rgb(0x7C, 0x4D, 0xFF) to "⇄"
            "update" in t || "software" in t || "system" in p || "settings" in p -> Color.rgb(0xEC, 0x40, 0x7A) to "⚙"
            "youtube" in p -> Color.rgb(0xE5, 0x39, 0x35) to "▶"
            "swiggy" in p || "zomato" in p -> Color.rgb(0xFB, 0x8C, 0x00) to "🍱"
            "gmail" in p || "mail" in p -> Color.rgb(0xD9, 0x30, 0x25) to "✉"
            "instagram" in p -> Color.rgb(0xC1, 0x35, 0x84) to "📸"
            "telegram" in p || "messaging" in p -> Color.rgb(0x1E, 0x88, 0xE5) to "💬"
            else -> Color.rgb(0x5E, 0x35, 0xB1) to (app.firstOrNull()?.uppercaseChar()?.toString() ?: "🔔")
        }
    }

    fun greeting(hourOfDay: Int, userName: String? = null): String {
        val who = userName?.trim()?.takeIf { it.isNotEmpty() }
        val tod = when (hourOfDay) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Hey there"
        }
        return if (who != null) "$tod, $who!" else "$tod!"
    }

    fun edgePrompt(items: List<NotifItem>, hourOfDay: Int): String {
        val n = items.size
        val apps = items.map { it.app }.distinct().take(2).joinToString(" & ")
        val greet = when (hourOfDay) { in 5..11 -> "Good morning machan!"; in 12..16 -> "Hey machan!"; else -> "Evening machan!" }
        return "$greet You have $n notification${if (n == 1) "" else "s"} from $apps — want a quick summary?"
    }

    fun localRecapLine(groups: List<NotifCardGroup>): String {
        if (groups.isEmpty()) return "All caught up, machan! Zero pending notifications."
        val total = groups.sumOf { it.count }
        val names = groups.take(3).joinToString(", ") { it.title.substringBefore(" (") }
        return "✨ Settai recap: $total update${if (total == 1) "" else "s"} across $names — tap any row to jump right in!"
    }

    fun demoItems(now: Long = System.currentTimeMillis()): List<NotifItem> = listOf(
        NotifItem(
            key = "demo_share_1",
            pkg = "com.google.android.gms",
            app = "Quick Share",
            title = "File Sharing with Bharath",
            text = "Bharath shared the file settai-v8-play.apk with you.",
            time = now - 60_000L,
            canReply = false
        ),
        NotifItem(
            key = "demo_wa_1",
            pkg = "com.whatsapp",
            app = "WhatsApp",
            title = "Rakesh",
            text = "Machan did you test the new Billa & Chittu chase on your Moto?",
            time = now - 120_000L,
            canReply = true
        ),
        NotifItem(
            key = "demo_wa_2",
            pkg = "com.whatsapp",
            app = "WhatsApp",
            title = "Rakesh",
            text = "Call me when you're free!",
            time = now - 180_000L,
            canReply = true
        ),
        NotifItem(
            key = "demo_sys_1",
            pkg = "com.motorola.ccc.ota",
            app = "System Updates",
            title = "Software and Security Updates",
            text = "Hello UI security patch is ready. Tap to review update details.",
            time = now - 300_000L,
            canReply = false
        ),
    )

    /** Rich 10-item demo set (9 groups) so the 'What You Missed' card is always smoothly scrollable out-of-the-box! */
    fun richDemoItems(now: Long = System.currentTimeMillis()): List<NotifItem> = demoItems(now) + listOf(
        NotifItem(
            key = "demo_wa_amma",
            pkg = "com.whatsapp",
            app = "WhatsApp",
            title = "Amma ❤️",
            text = "Dinner is ready da! Hot idiyappam & coconut milk waiting.",
            time = now - 360_000L,
            canReply = true
        ),
        NotifItem(
            key = "demo_swiggy_1",
            pkg = "in.swiggy.android",
            app = "Swiggy",
            title = "Order Out for Delivery 🍱",
            text = "Surya is 4 mins away with your Chicken Biryani & Filter Coffee.",
            time = now - 420_000L,
            canReply = false
        ),
        NotifItem(
            key = "demo_ig_1",
            pkg = "com.instagram.android",
            app = "Instagram",
            title = "Priya sent a reel",
            text = "Look at this cute cat & mouse cartoon animation 😂✨",
            time = now - 540_000L,
            canReply = true
        ),
        NotifItem(
            key = "demo_gmail_1",
            pkg = "com.google.android.gm",
            app = "Gmail",
            title = "GitHub · Build Passed",
            text = "[settai-v9.3] All unit & UI tests passed in 18s ✓",
            time = now - 660_000L,
            canReply = false
        ),
        NotifItem(
            key = "demo_yt_1",
            pkg = "com.google.android.youtube",
            app = "YouTube",
            title = "New upload from Madan Gowri",
            text = "AI Companions on Android in 2026 — Full Breakdown!",
            time = now - 780_000L,
            canReply = false
        ),
        NotifItem(
            key = "demo_tg_1",
            pkg = "org.telegram.messenger",
            app = "Telegram",
            title = "Chennai Tech Club",
            text = "Karthik: Saturday meetup at Anna Nagar Tower Park at 5 PM!",
            time = now - 900_000L,
            canReply = true
        ),
    )
}

/**
 * Renders, scrolls, and hit-tests the compact dark-plum "What You Missed" summary card (`#14131C` with `#3D3556` border)
 * directly inside Settai's own opaque overlay window (`MonkeyView`).
 * Supports smooth vertical touch scrolling (`scrollY` / `scrollBy`) across up to 25 grouped notification rows,
 * with a pinned Header at the top, a clipped scrollable list viewport in the middle, a visible vertical scrollbar thumb,
 * and pinned Footer action pills at the bottom.
 */
class NotifSummaryCard(private val density: Float) {
    sealed class Hit {
        data class Row(val index: Int, val group: NotifCardGroup) : Hit()
        object Close : Hit()
        object AiRecap : Hit()
        object ClearAll : Hit()
        object ScrollUp : Hit()
        object ScrollDown : Hit()
        object InsideCard : Hit()
    }

    var groups: List<NotifCardGroup> = emptyList(); private set
    var greetingText: String = "Good morning, Manik!"
    var subtitleText: String = "Here's what you missed..."
    var width: Float = 328f * density; private set
    var height: Float = 240f * density; private set
    var scrollY: Float = 0f; private set
    var maxScrollY: Float = 0f; private set
    val listViewportRect = RectF()

    private val rowRects = ArrayList<RectF>()
    private val closeRect = RectF()
    private val scrollUpRect = RectF()
    private val scrollDownRect = RectF()
    private val aiBtnRect = RectF()
    private val clearBtnRect = RectF()
    private val doneBtnRect = RectF()
    private val cardRect = RectF()
    private val tmpRect = RectF()
    private val chevPath = Path()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.rgb(0x14, 0x13, 0x1C) }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f * density; color = Color.rgb(0x3D, 0x35, 0x56) }
    private val headerGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(42, 0x9C, 0x4D, 0xFF) }
    private val greetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); textSize = 16.5f * density }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xC7, 0x92, 0xEA); typeface = Typeface.create("sans-serif", Typeface.NORMAL); textSize = 12.0f * density }
    private val rowTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); textSize = 13.5f * density }
    private val rowSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xB8, 0xB5, 0xC8); typeface = Typeface.create("sans-serif", Typeface.NORMAL); textSize = 11.8f * density }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; textSize = 15f * density }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f * density; color = Color.argb(45, 255, 255, 255) }
    private val chevPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.2f * density; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = Color.rgb(0x9E, 0x9A, 0xB4) }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); textAlign = Paint.Align.CENTER; textSize = 11.5f * density }
    private val scrollTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(50, 255, 255, 255) }
    private val scrollThumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.rgb(0xBA, 0x68, 0xC8) }

    fun setData(
        groups: List<NotifCardGroup>,
        greeting: String,
        subtitle: String = "Here's what you missed...",
        maxWidthPx: Int = (336 * density).toInt(),
    ) {
        this.groups = groups.take(25)
        this.greetingText = greeting
        val d = density
        width = min(maxWidthPx.toFloat(), 336f * d).coerceAtLeast(250f * d)
        val headerH = 58f * d
        val rowH = 58f * d
        val visibleRows = min(3, max(1, this.groups.size))
        val viewportH = visibleRows * rowH
        val contentH = max(1, this.groups.size) * rowH
        val footerH = 44f * d
        height = headerH + viewportH + footerH + 8f * d
        cardRect.set(0f, 0f, width, height)
        listViewportRect.set(4f * d, headerH, width - 4f * d, headerH + viewportH)

        maxScrollY = max(0f, contentH - viewportH)
        scrollY = 0f
        this.subtitleText = if (maxScrollY > 0f && subtitle == "Here's what you missed...") {
            "Here's what you missed (${this.groups.size} items · ⇅ swipe to scroll)"
        } else subtitle

        val cr = 15f * d
        val cx = width - 22f * d
        val cy = 24f * d
        closeRect.set(cx - cr, cy - cr, cx + cr, cy + cr)
        if (maxScrollY > 0f) {
            scrollUpRect.set(width - 88f * d, 10f * d, width - 64f * d, 38f * d)
            scrollDownRect.set(width - 62f * d, 10f * d, width - 38f * d, 38f * d)
        } else {
            scrollUpRect.setEmpty()
            scrollDownRect.setEmpty()
        }

        // Row rects in un-scrolled content coordinates (y starts at headerH)
        rowRects.clear()
        var ry = headerH
        for (i in 0 until this.groups.size) {
            rowRects.add(RectF(8f * d, ry, width - 10f * d, ry + rowH))
            ry += rowH
        }

        // Pinned footer pills below listViewportRect
        val py = listViewportRect.bottom + 6f * d
        val ph = 30f * d
        val pad = 12f * d
        val gap = 8f * d
        val w1 = (width - 2 * pad - 2 * gap) * 0.42f
        val w2 = (width - 2 * pad - 2 * gap) * 0.31f
        val w3 = (width - 2 * pad - 2 * gap) * 0.27f
        aiBtnRect.set(pad, py, pad + w1, py + ph)
        clearBtnRect.set(aiBtnRect.right + gap, py, aiBtnRect.right + gap + w2, py + ph)
        doneBtnRect.set(clearBtnRect.right + gap, py, clearBtnRect.right + gap + w3, py + ph)
    }

    /** Scrolls the list vertically by [deltaY] (positive = scroll down toward older items). Returns true if scrollY changed. */
    fun scrollBy(deltaY: Float): Boolean {
        if (maxScrollY <= 0f) return false
        val next = (scrollY + deltaY).coerceIn(0f, maxScrollY)
        val changed = kotlin.math.abs(next - scrollY) > 0.1f
        scrollY = next
        return changed
    }

    fun draw(c: Canvas, left: Float, top: Float) {
        val d = density
        c.save()
        c.translate(left, top)

        c.drawRoundRect(cardRect, 22f * d, 22f * d, bgPaint)
        tmpRect.set(0f, 0f, width, 56f * d)
        c.drawRoundRect(tmpRect, 22f * d, 22f * d, headerGlow)
        c.drawRoundRect(cardRect, 22f * d, 22f * d, borderPaint)

        // Pinned Header
        val titleMaxW = if (maxScrollY > 0f) width - 104f * d else width - 56f * d
        c.drawText(ellipsize(greetingText, greetPaint, titleMaxW), 16f * d, 25f * d, greetPaint)
        c.drawText(ellipsize(subtitleText, subPaint, width - 32f * d), 16f * d, 44f * d, subPaint)

        if (maxScrollY > 0f) {
            drawPill(c, scrollUpRect, "▲", if (scrollY > 2f) Color.rgb(0x4A, 0x2C, 0x82) else Color.rgb(0x28, 0x24, 0x38))
            drawPill(c, scrollDownRect, "▼", if (scrollY < maxScrollY - 2f) Color.rgb(0x4A, 0x2C, 0x82) else Color.rgb(0x28, 0x24, 0x38))
        }

        pillPaint.color = Color.rgb(0x28, 0x24, 0x38)
        c.drawCircle(closeRect.centerX(), closeRect.centerY(), 12f * d, pillPaint)
        val o = 4.2f * d
        chevPaint.color = Color.rgb(0xD0, 0xCC, 0xE0)
        c.drawLine(closeRect.centerX() - o, closeRect.centerY() - o, closeRect.centerX() + o, closeRect.centerY() + o, chevPaint)
        c.drawLine(closeRect.centerX() + o, closeRect.centerY() - o, closeRect.centerX() - o, closeRect.centerY() + o, chevPaint)

        c.drawLine(14f * d, 56f * d, width - 14f * d, 56f * d, dividerPaint)

        // Clipped Scrollable Middle List Viewport
        if (groups.isEmpty()) {
            c.drawText("No unread notifications right now ✨", 18f * d, 92f * d, rowSubPaint)
        } else {
            c.save()
            c.clipRect(listViewportRect)
            c.translate(0f, -scrollY)
            for (i in groups.indices) {
                val g = groups[i]
                val rr = rowRects[i]
                if (rr.bottom - scrollY < listViewportRect.top || rr.top - scrollY > listViewportRect.bottom) continue

                val bs = 36f * d
                val bx = 16f * d
                val by = rr.centerY() - bs / 2f
                tmpRect.set(bx, by, bx + bs, by + bs)
                badgePaint.color = g.badgeColor
                c.drawRoundRect(tmpRect, 10f * d, 10f * d, badgePaint)
                c.drawText(g.badgeGlyph, tmpRect.centerX(), tmpRect.centerY() - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f, badgeTextPaint)

                badgePaint.color = Color.rgb(0xFF, 0x40, 0x81)
                c.drawCircle(tmpRect.right - 2f * d, tmpRect.top + 2f * d, 4.5f * d, badgePaint)

                val textLeft = bx + bs + 12f * d
                val textMaxW = width - textLeft - 32f * d
                c.drawText(ellipsize(g.title, rowTitlePaint, textMaxW), textLeft, rr.top + 21f * d, rowTitlePaint)

                val (line1, line2) = splitTwoLines(g.summaryText, rowSubPaint, textMaxW)
                c.drawText(line1, textLeft, rr.top + 37f * d, rowSubPaint)
                if (line2.isNotEmpty()) {
                    c.drawText(line2, textLeft, rr.top + 51f * d, rowSubPaint)
                }

                val cx = width - 20f * d
                val cy = rr.centerY()
                val cs = 5f * d
                chevPaint.color = Color.rgb(0x9E, 0x9A, 0xB4)
                chevPath.reset()
                chevPath.moveTo(cx - cs * 0.6f, cy - cs)
                chevPath.lineTo(cx + cs * 0.6f, cy)
                chevPath.lineTo(cx - cs * 0.6f, cy + cs)
                c.drawPath(chevPath, chevPaint)

                if (i < groups.lastIndex) {
                    c.drawLine(textLeft, rr.bottom, width - 16f * d, rr.bottom, dividerPaint)
                }
            }
            c.restore()

            // Vertical Scrollbar Track & Thumb on the right edge when maxScrollY > 0
            if (maxScrollY > 0f) {
                val sbLeft = width - 7.5f * d
                val sbRight = width - 4.0f * d
                val trackTop = listViewportRect.top + 6f * d
                val trackBot = listViewportRect.bottom - 6f * d
                val trackH = (trackBot - trackTop).coerceAtLeast(20f * d)
                tmpRect.set(sbLeft, trackTop, sbRight, trackBot)
                c.drawRoundRect(tmpRect, 2f * d, 2f * d, scrollTrackPaint)

                val viewFrac = (listViewportRect.height() / (listViewportRect.height() + maxScrollY)).coerceIn(0.18f, 0.85f)
                val thumbH = (trackH * viewFrac).coerceAtLeast(18f * d)
                val scrollFrac = (scrollY / maxScrollY).coerceIn(0f, 1f)
                val thumbTop = trackTop + (trackH - thumbH) * scrollFrac
                tmpRect.set(sbLeft, thumbTop, sbRight, thumbTop + thumbH)
                c.drawRoundRect(tmpRect, 2f * d, 2f * d, scrollThumbPaint)
            }
        }

        // Pinned Footer pills
        drawPill(c, aiBtnRect, "✨ AI Recap", Color.rgb(0x4A, 0x2C, 0x82))
        drawPill(c, clearBtnRect, "Clear all", Color.rgb(0x2A, 0x26, 0x3B))
        drawPill(c, doneBtnRect, "Done", Color.rgb(0x2E, 0x7D, 0x32))

        c.restore()
    }

    private fun drawPill(c: Canvas, r: RectF, label: String, color: Int) {
        pillPaint.color = color
        c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, pillPaint)
        c.drawText(label, r.centerX(), r.centerY() - (pillTextPaint.descent() + pillTextPaint.ascent()) / 2f, pillTextPaint)
    }

    /** Hit-test in card-local coordinates (`0..width`, `0..height`), accounting for vertical [scrollY]. */
    fun hitTest(lx: Float, ly: Float): Hit? {
        if (lx < 0f || lx > width || ly < 0f || ly > height) return null
        val slop = 6f * density
        fun RectF.containsWithSlop() = lx >= left - slop && lx <= right + slop && ly >= top - slop && ly <= bottom + slop
        if (closeRect.containsWithSlop() || doneBtnRect.containsWithSlop()) return Hit.Close
        if (maxScrollY > 0f && !scrollUpRect.isEmpty && scrollUpRect.containsWithSlop()) return Hit.ScrollUp
        if (maxScrollY > 0f && !scrollDownRect.isEmpty && scrollDownRect.containsWithSlop()) return Hit.ScrollDown
        if (aiBtnRect.containsWithSlop()) return Hit.AiRecap
        if (clearBtnRect.containsWithSlop()) return Hit.ClearAll
        if (listViewportRect.contains(lx, ly)) {
            val contentY = ly + scrollY
            for (i in rowRects.indices) {
                if (rowRects[i].contains(lx, contentY)) {
                    return Hit.Row(i, groups[i])
                }
            }
        }
        return Hit.InsideCard
    }

    private fun ellipsize(s: String, p: Paint, maxW: Float): String {
        if (p.measureText(s) <= maxW) return s
        var t = s
        while (t.length > 3 && p.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    private fun splitTwoLines(s: String, p: Paint, maxW: Float): Pair<String, String> {
        if (p.measureText(s) <= maxW) return s to ""
        val words = s.split(' ')
        val l1 = StringBuilder()
        var idx = 0
        while (idx < words.size) {
            val cand = if (l1.isEmpty()) words[idx] else "$l1 ${words[idx]}"
            if (p.measureText(cand) <= maxW || l1.isEmpty()) {
                l1.clear(); l1.append(cand); idx++
            } else break
        }
        val rem = words.drop(idx).joinToString(" ")
        return l1.toString() to if (rem.isBlank()) "" else ellipsize(rem, p, maxW)
    }
}
