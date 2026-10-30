package com.combo.monkeycompanion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.combo.monkeycompanion.agent.Digest
import com.combo.monkeycompanion.agent.QuickChip
import com.combo.monkeycompanion.agent.ScreenSnapshot
import com.combo.monkeycompanion.agent.ScreenTalk
import com.combo.monkeycompanion.agent.UiNode
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.Animations
import com.combo.monkeycompanion.rig.MonkeyRenderer
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.speech.CloudBubble

/** Debug-only v4.2 preview: proactive pipe with chips, the quick-action arc, hold-to-talk, smart WhatsApp summary. */
object PreviewV42 {
    private val brown = Color.rgb(0x5A, 0x36, 0x18)

    /** The same fake WhatsApp tree the unit test uses, so the bubble text is the real template output. */
    fun fakeWhatsApp(): ScreenSnapshot {
        val pkg = "com.whatsapp"; val nodes = ArrayList<UiNode>()
        fun n(parent: Int, text: String = "", desc: String = "", id: String = "", click: Boolean = false, top: Int = 0, left: Int = 0): Int {
            val i = nodes.size + 1
            nodes.add(UiNode(i, "android.widget.TextView", text, desc, if (id.isEmpty()) "" else "$pkg:id/$id", click, parent = parent, top = top, left = left)); return i
        }
        n(0, "WhatsApp", top = 40)
        val list = n(0, top = 200)
        for ((k, r) in ROWS.withIndex()) {
            val top = 300 + k * 150
            val row = n(list, click = true, top = top)
            n(row, r[0], id = "conversations_row_contact_name", top = top, left = 150)
            n(row, r[1], id = "conversations_row_date", top = top, left = 900)
            n(row, r[2], id = "single_msg_tv", top = top + 50, left = 150)
            if (r[3] != "0") n(row, r[3], desc = "${r[3]} unread messages", id = "conversations_row_message_count", top = top + 50, left = 950)
        }
        n(0, desc = "New chat", click = true, top = 1800)
        return ScreenSnapshot(pkg, "WhatsApp", nodes)
    }
    private val ROWS = listOf(
        listOf("Amma", "10:42", "Did you eat? Call me when free", "2"),
        listOf("Ravi", "9:10", "match tonight? 🏏", "1"),
        listOf("College Gang", "Yesterday", "Priya: photos pls", "0"),
        listOf("Swiggy", "Yesterday", "Your order was delivered", "0"),
    )

    private class Panel(val title: String, val sub: String)

    fun render(): Bitmap {
        val panels = listOf(
            Panel("1 · Proactive pipe-up with chips", "Buddy level · max 4/hour · never while typing / calls / fullscreen / DND"),
            Panel("2 · Tap the belly: quick-action arc", "Screen · Missed? · Talk · Funny (or context: Worth it? / Cheaper?) · ✕ Quit"),
            Panel("3 · Hold 0.4 s: hold-to-talk", "Ears perk · live transcript · let go = send · slide away = cancel"),
            Panel("4 · Smart WhatsApp summary", "Digest from the accessibility tree → one insight + one suggestion"),
        )
        val cols = 2; val cw = 760; val ch = 1060
        val bmp = Bitmap.createBitmap(cols * cw, 2 * ch + 110 + ROW3, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(0xFF, 0xF4, 0xE2))
        val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brown; textSize = 46f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        c.drawText("Settai v4.2 · smart screen, proactive buddy, quick taps, hold-to-talk", bmp.width / 2f, 72f, head)
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brown; textSize = 34f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x8A, 0x6A, 0x4A); textSize = 22f; textAlign = Paint.Align.CENTER }
        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xE7, 0xC4) }
        val density = 2.1f
        panels.forEachIndexed { i, p ->
            val x0 = (i % cols) * cw.toFloat(); val y0 = 110f + (i / cols) * ch.toFloat()
            c.drawRoundRect(RectF(x0 + 12, y0 + 12, x0 + cw - 12, y0 + ch - 12), 40f, 40f, card)
            // A phone screen behind Settai.
            val phone = RectF(x0 + 70, y0 + 40, x0 + cw - 70, y0 + ch - 130)
            drawPhone(c, phone, i)
            val mx = x0 + cw / 2f + (if (i == 3) 120f else 0f); val my = phone.bottom - 30f
            val pose = Pose(); val ctx = AnimContext(); val rig = MonkeyRig()
            val state = when (i) { 0 -> MonkeyState.TALKING; 1 -> MonkeyState.IDLE; 2 -> MonkeyState.IDLE; else -> MonkeyState.TALKING }
            Animations.fill(pose, state, 0.6f, 0.6f, ctx)
            if (i == 2) { pose.earPerk = 1f; pose.earWiggle = 0.4f; pose.mouthOpen = 0.1f }
            val scale = 1.9f
            rig.layout(pose, mx, my, scale, if (i == 3) -1f else 1f)
            MonkeyRenderer().draw(c, rig, pose, 0.9f)
            val ht = FloatArray(2); rig.headTop(ht)
            val b = CloudBubble(density, 15f * density)
            when (i) {
                0 -> b.setText("📬 5 new messages: Amma ×2, Ravi, Swiggy. Want me to read them?", (230 * density).toInt(),
                    listOf(QuickChip("notifs", "Read them"), QuickChip("prefill", "Reply"), QuickChip.LATER).map { it.label }, chips = true)
                1 -> b.setArc(listOf("👀", "🔔", "🎤", "😂", "✕"), listOf("Screen", "Missed?", "Talk", "Funny", "✕"))
                2 -> b.setText("🎤 “set an alarm for 6 am and remind me to…”", (210 * density).toInt())
                else -> {
                    val d = Digest.build(fakeWhatsApp())
                    b.setText(ScreenTalk.summary(d), (230 * density).toInt(), ScreenTalk.followUps(ScreenTalk.Mode.SUMMARY, d).map { it.label }, chips = true)
                }
            }
            val left = (ht[0] - b.width / 2f).coerceIn(x0 + 20f, x0 + cw - 20f - b.width)
            val top = if (i == 1) ht[1] - b.height + 30f else (ht[1] + 8f - b.trailSpace - b.height)
            b.draw(c, left, top.coerceAtLeast(y0 + 30f), ht[0], ht[1], 1f, 1f)
            if (i == 2) {
                // The finger holding Settai.
                val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 0x40, 0x40, 0x40) }
                c.drawCircle(mx + 10f, my - 120f, 46f, f)
                f.color = Color.argb(140, 0xFF, 0xFF, 0xFF); f.style = Paint.Style.STROKE; f.strokeWidth = 4f
                c.drawCircle(mx + 10f, my - 120f, 46f, f)
            }
            c.drawText(p.title, x0 + cw / 2f, y0 + ch - 76f, title)
            c.drawText(p.sub, x0 + cw / 2f, y0 + ch - 40f, sub)
        }
        drawEntrances(c, 110f + 2 * ch, cols * cw.toFloat())
        return bmp
    }

    private const val ROW3 = 820
    private class Ent(val label: String, val sub: String, val power: com.combo.monkeycompanion.brain.Superpower, val phase: com.combo.monkeycompanion.brain.PowerPhase,
                      val k: Float, val vx: Float, val vy: Float, val lift: Float, val say: String, val sim: Float = 0.5f)

    /** Row 3: power entrances (the pipe's message follows after landing, with its chips). */
    private fun drawEntrances(c: Canvas, y0: Float, w: Float) {
        val shots = listOf(
            Ent("5 · Power entrance: summaries fly in", "Banana / hero flight → lands → summary", com.combo.monkeycompanion.brain.Superpower.SuperBanana, com.combo.monkeycompanion.brain.PowerPhase.FLY, 0.5f, 1400f, -80f, 230f, "📣 Special delivery, incoming!"),
            Ent("6 · Important: power strike first", "Thunder / heat vision / shield → message", com.combo.monkeycompanion.brain.Superpower.ThunderMonkey, com.combo.monkeycompanion.brain.PowerPhase.STRIKE, 0.5f, 0f, 0f, 60f, "⚡ ALERT! ALERT!", 0.7f),
            Ent("7 · Jokes ride in on jets", "Rocket / armour jets / confetti → joke", com.combo.monkeycompanion.brain.Superpower.RocketMonkey, com.combo.monkeycompanion.brain.PowerPhase.FLY, 0.5f, 900f, -520f, 230f, "🚀 Joke delivery in 3, 2, 1..."),
        )
        val cw = w / shots.size; val ch = ROW3.toFloat() - 20f
        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xDD, 0xEE, 0xFF) }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brown; textSize = 30f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x6A, 0x5A, 0x4A); textSize = 20f; textAlign = Paint.Align.CENTER }
        val rig = MonkeyRig(); val renderer = MonkeyRenderer(); val ctx = AnimContext(); val pose = Pose()
        val parts = com.combo.monkeycompanion.rig.Particles()
        val emitter = com.combo.monkeycompanion.rig.PowerEmitter(parts)
        val scale = 1.9f; val density = 2.1f
        val scratch = Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
        shots.forEachIndexed { i, s ->
            val x0 = i * cw
            val cardR = RectF(x0 + 12, y0 + 6, x0 + cw - 12, y0 + ch)
            c.drawRoundRect(cardR, 36f, 36f, card)
            val facing = if (s.vx < 0f) -1f else 1f
            val sp = kotlin.math.hypot(s.vx, s.vy)
            ctx.power = s.power; ctx.powerPhase = s.phase; ctx.crashed = false
            ctx.speed = ((sp / (900f * density)).coerceIn(0f, 1f) + (if (sp > 0f) 0.5f else 0f)).coerceAtMost(1f)
            ctx.heading = if (sp > 0f) Math.toDegrees(kotlin.math.atan2(kotlin.math.abs(s.vx).toDouble(), (-s.vy).toDouble())).toFloat() else 0f
            val ax = x0 + cw / 2f; val ay = y0 + ch - 110f - s.lift
            parts.clear(); emitter.reset(); renderer.resetCape()
            val n = (s.sim * 60f).toInt().coerceAtLeast(1); val dt = 1f / 60f
            for (f in 0..n) {
                val back = (n - f) * dt
                val kk = (s.k - back * 0.5f).coerceIn(0f, 1f)
                ctx.powerK = kk
                val t = 1f + f * dt
                Animations.fill(pose, MonkeyState.POWER, t, t, ctx)
                rig.layout(pose, ax - s.vx * scale / density * back * 0.5f, ay - s.vy * scale / density * back * 0.5f, scale, facing)
                emitter.frame(rig, pose, s.power, s.phase, 0, kk, s.vx * scale / density, s.vy * scale / density, facing, scale, dt)
                renderer.draw(scratch, rig, pose, t)
            }
            c.save(); c.clipRect(cardR)
            if (s.phase == com.combo.monkeycompanion.brain.PowerPhase.STRIKE) {
                // The bolt (drawn by the screen-effects window on the phone).
                val bolt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xE0, 0x40); strokeWidth = 10f; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
                val path = android.graphics.Path().apply { moveTo(ax + 40f, ay - 260f); lineTo(ax + 120f, ay - 200f); lineTo(ax + 90f, ay - 170f); lineTo(ax + 200f, ay - 90f); lineTo(ax + 160f, ay - 70f); lineTo(ax + 240f, ay + 10f) }
                c.drawPath(path, bolt); bolt.color = Color.WHITE; bolt.strokeWidth = 4f; c.drawPath(path, bolt)
            }
            parts.draw(c)
            rig.layout(pose, ax, ay, scale, facing)
            renderer.draw(c, rig, pose, 1f + n * dt)
            c.restore()
            val b = CloudBubble(density, 15f * density)
            b.setText(s.say, (150 * density).toInt())
            val head = FloatArray(2); rig.headTop(head)
            val left = (ax - b.width / 2f).coerceIn(x0 + 16f, x0 + cw - 16f - b.width)
            val top = (head[1] + 8f - b.trailSpace - b.height).coerceAtLeast(y0 + 20f)
            b.draw(c, left, top, head[0], head[1], 1f, 1f)
            c.drawText(s.label, x0 + cw / 2f, y0 + ch - 56f, title)
            c.drawText(s.sub, x0 + cw / 2f, y0 + ch - 24f, sub)
        }
    }

    internal fun drawPhone(c: Canvas, r: RectF, kind: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.rgb(0x22, 0x22, 0x22); c.drawRoundRect(RectF(r.left - 10, r.top - 10, r.right + 10, r.bottom + 10), 48f, 48f, p)
        p.color = Color.WHITE; c.drawRoundRect(r, 40f, 40f, p)
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f; color = Color.rgb(0x22, 0x22, 0x22) }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 21f; color = Color.rgb(0x70, 0x70, 0x70) }
        c.save(); c.clipRect(r)
        when (kind) {
            1 -> { // YouTube-ish feed
                p.color = Color.rgb(0x20, 0x20, 0x20); c.drawRect(r.left, r.top, r.right, r.top + 90, p)
                t.color = Color.WHITE; c.drawText("▶ YouTube", r.left + 30, r.top + 58, t)
                for (k in 0..1) {
                    val y = r.top + 120 + k * 300f
                    p.color = Color.rgb(0xDD, 0xDD, 0xDD); c.drawRoundRect(RectF(r.left + 24, y, r.right - 24, y + 200), 16f, 16f, p)
                    t.color = Color.rgb(0x22, 0x22, 0x22)
                    c.drawText(if (k == 0) "Chennai street food tour | Best dosa" else "Learn Kotlin in 10 minutes", r.left + 30, y + 240, t)
                }
            }
            2 -> { // home screen
                p.color = Color.rgb(0xCF, 0xE8, 0xFF); c.drawRect(r, p)
                t.textSize = 70f; t.color = Color.rgb(0x2A, 0x4A, 0x6A); c.drawText("4:58", r.left + 40, r.top + 120, t)
                small.color = Color.rgb(0x2A, 0x4A, 0x6A); c.drawText("Thursday, 22 Oct", r.left + 44, r.top + 160, small)
                for (k in 0 until 8) { p.color = Color.rgb(0xFF, 0xFF, 0xFF); c.drawRoundRect(RectF(r.left + 40 + (k % 4) * 140f, r.top + 220 + (k / 4) * 150f, r.left + 140 + (k % 4) * 140f, r.top + 320 + (k / 4) * 150f), 26f, 26f, p) }
            }
            else -> { // WhatsApp chat list
                p.color = Color.rgb(0x07, 0x5E, 0x54); c.drawRect(r.left, r.top, r.right, r.top + 100, p)
                t.color = Color.WHITE; t.typeface = Typeface.DEFAULT_BOLD; c.drawText("WhatsApp", r.left + 30, r.top + 64, t); t.typeface = Typeface.DEFAULT
                for ((k, row) in ROWS.withIndex()) {
                    val y = r.top + 130 + k * 120f
                    p.color = Color.rgb(0xC8, 0xD6, 0xD2); c.drawCircle(r.left + 70, y + 40, 38f, p)
                    t.color = Color.rgb(0x22, 0x22, 0x22); c.drawText(row[0], r.left + 130, y + 28, t)
                    c.drawText(row[2].take(26), r.left + 130, y + 68, small)
                    small.color = if (row[3] != "0") Color.rgb(0x25, 0xD3, 0x66) else Color.rgb(0x70, 0x70, 0x70)
                    c.drawText(row[1], r.right - 120, y + 28, small); small.color = Color.rgb(0x70, 0x70, 0x70)
                    if (row[3] != "0") {
                        p.color = Color.rgb(0x25, 0xD3, 0x66); c.drawCircle(r.right - 70, y + 62, 18f, p)
                        val w = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 22f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
                        c.drawText(row[3], r.right - 70, y + 70, w)
                    }
                }
                if (kind == 0) {
                    // A notification shade peeking from the top.
                    p.color = Color.argb(235, 0x30, 0x30, 0x30); c.drawRoundRect(RectF(r.left + 20, r.top + 20, r.right - 20, r.top + 120), 24f, 24f, p)
                    t.color = Color.WHITE; c.drawText("Amma · 2 new messages", r.left + 50, r.top + 64, t)
                    small.color = Color.rgb(0xCC, 0xCC, 0xCC); c.drawText("Did you eat? Call me when free", r.left + 50, r.top + 100, small)
                }
            }
        }
        c.restore()
    }
}
