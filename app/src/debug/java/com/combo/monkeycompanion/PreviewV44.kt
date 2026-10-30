package com.combo.monkeycompanion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.combo.monkeycompanion.brain.LongPressMenu
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.PhoneMoments
import com.combo.monkeycompanion.brain.PhoneMoments.ChargeStyle
import com.combo.monkeycompanion.brain.PhoneMoments.Moment
import com.combo.monkeycompanion.brain.Superpowers
import com.combo.monkeycompanion.character.MonkeyCharacter
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.Animations
import com.combo.monkeycompanion.rig.MonkeyRenderer
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.speech.CloudBubble

/** Debug-only v4.4 preview: main ring, powers ring (one page, with a recharge inset), bubble chat ✕, customise screen. */
object PreviewV44 {
    private val brown = Color.rgb(0x5A, 0x36, 0x18)

    private fun entry(id: String, cooling: Int = 0): Triple<String, String, Boolean> = when {
        id == LongPressMenu.MORE -> Triple("⋯", "More", false)
        id == LongPressMenu.BACK -> Triple("↩️", "Back", false)
        id == LongPressMenu.NEXT -> Triple("➡️", "Next", false)
        id == LongPressMenu.RANDOM -> Triple("🎲", if (cooling > 0) "${cooling}s" else "Random", cooling > 0)
        id == "quit" -> Triple("✕", "✕", false)
        LongPressMenu.isPower(id) -> {
            val pid = LongPressMenu.powerId(id)
            Triple(LongPressMenu.powerIcon(pid), if (cooling > 0) "${cooling}s" else LongPressMenu.shortName(pid, Superpowers.byId(pid)?.title ?: pid), cooling > 0)
        }
        else -> LongPressMenu.Item.of(id)!!.let { Triple(it.icon, it.label, false) }
    }

    private fun arc(b: CloudBubble, ids: List<String>, title: String?, cooling: Int = 0) {
        val e = ids.map { entry(it, cooling) }
        b.setArc(e.map { it.first }, e.map { it.second }, e.indices.filter { e[it].third }.toSet(), title)
    }

    fun render(customise: Bitmap?): Bitmap {
        val cols = 2; val cw = 760; val ch = 1100
        val bmp = Bitmap.createBitmap(cols * cw, 2 * ch + 110 + 2 * MROW + 150 + LROW, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(0xFF, 0xF4, 0xE2))
        val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brown; textSize = 46f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        c.drawText("Settai v4.4 · menu, powers, easy-close chat, moments, landings", bmp.width / 2f, 72f, head)
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brown; textSize = 34f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x8A, 0x6A, 0x4A); textSize = 22f; textAlign = Paint.Align.CENTER }
        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xE7, 0xC4) }
        val density = 1.9f
        val panels = listOf(
            "1 · Long-press: the main ring" to "Chat · Talk · Missed? · Screen · ⚡ Powers · … More (Funny, App tip, Quit)",
            "2 · ⚡ Powers: this character's powers" to "4 per page + Random · Next · Back · greyed with a countdown while recharging",
            "3 · Bubble chat: easy to close" to "✕ · swipe it away · tap outside · Back · idle timeout (2 min default)",
            "4 · Settings › Long-press menu" to "On/off · drag ≡ to reorder · max 6 on the ring · pin powers · live preview",
        )
        val menu = LongPressMenu.layout(LongPressMenu.defaults(), emptyList(), MonkeyCharacter.powers.map { it.id }, true)
        panels.forEachIndexed { i, (t, s) ->
            val x0 = (i % cols) * cw.toFloat(); val y0 = 110f + (i / cols) * ch.toFloat()
            c.drawRoundRect(RectF(x0 + 12, y0 + 12, x0 + cw - 12, y0 + ch - 12), 40f, 40f, card)
            val phone = RectF(x0 + 70, y0 + 40, x0 + cw - 70, y0 + ch - 130)
            if (i == 3 && customise != null) {
                val clip = RectF(phone)
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x22, 0x22, 0x22) }
                c.drawRoundRect(RectF(clip.left - 10, clip.top - 10, clip.right + 10, clip.bottom + 10), 46f, 46f, p)
                c.save(); c.clipRect(clip)
                val sc = clip.width() / customise.width
                c.translate(clip.left, clip.top); c.scale(sc, sc)
                c.drawBitmap(customise, 0f, 0f, null)
                c.restore()
            } else {
                PreviewV42.drawPhone(c, phone, if (i == 2) 3 else 1)
                val mx = x0 + cw / 2f; val my = phone.bottom - 30f
                val pose = Pose(); val ctx = AnimContext(); val rig = MonkeyRig()
                Animations.fill(pose, if (i == 2) MonkeyState.TALKING else MonkeyState.WAVE, 0.5f, 0.5f, ctx)
                rig.layout(pose, mx, my, 1.9f, 1f)
                MonkeyRenderer().draw(c, rig, pose, 0.9f)
                val ht = FloatArray(2); rig.headTop(ht)
                val b = CloudBubble(density, 15f * density)
                when (i) {
                    0 -> arc(b, menu.main, "Tap one · tap outside to close · edit in Settings")
                    1 -> arc(b, LongPressMenu.page(LongPressMenu.powerRing(MonkeyCharacter.powers).map { LongPressMenu.pin(it) }, 0, random = true), "Settai's powers (1/3)")
                    else -> { b.setText("What's up? 🐒 Chat's open! Close it with ✕, a swipe, or a tap outside 👋", (210 * density).toInt(), reply = true); b.fieldText = "is it going to rain tod" }
                }
                val left = if (b.width > cw - 40f) x0 + (cw - b.width) / 2f else (ht[0] - b.width / 2f).coerceIn(x0 + 20f, x0 + cw - 20f - b.width)
                val top = if (i < 2) ht[1] - b.height + 30f else (ht[1] + 8f - b.trailSpace - b.height)
                b.draw(c, left, top.coerceAtLeast(y0 + 30f), ht[0], ht[1], 1f, 1f)
                if (i == 2) {
                    // Swipe hint arrow.
                    val a = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 0x5A, 0x36, 0x18); strokeWidth = 7f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
                    val yy = top.coerceAtLeast(y0 + 30f) + b.height + 18f
                    c.drawLine(left + b.width * 0.35f, yy, left + b.width * 0.85f, yy, a)
                    c.drawLine(left + b.width * 0.85f, yy, left + b.width * 0.85f - 22f, yy - 16f, a)
                    c.drawLine(left + b.width * 0.85f, yy, left + b.width * 0.85f - 22f, yy + 16f, a)
                }
            }
            c.drawText(t, x0 + cw / 2f, y0 + ch - 76f, title)
            c.drawText(s, x0 + cw / 2f, y0 + ch - 40f, sub)
        }
        drawMoments(c, 110f + 2 * ch, bmp.width.toFloat())
        drawLandings(c, 110f + 2 * ch + 2 * MROW + 90, bmp.width.toFloat())
        return bmp
    }

    private const val MROW = 760
    private class Tile(val who: com.combo.monkeycompanion.character.Character, val m: Moment, val k: Float, val title: String)

    /** Rows 3-4: phone moments (charger zap per character; full, low, Wi-Fi, headphones). */
    private fun drawMoments(c: Canvas, y0: Float, w: Float) {
        val R = com.combo.monkeycompanion.character.CharacterRegistry
        val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brown; textSize = 38f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        c.drawText("5 · Phone moments: charger in → zap the battery icon, each in their own way", w / 2f, y0 + 60f, head)
        val tiles = listOf(
            Tile(MonkeyCharacter, Moment.CHARGE, 0.62f, "Settai: lightning zap"),
            Tile(R.byId("superboy")!!, Moment.CHARGE, 0.62f, "Superboy: heat beam"),
            Tile(R.byId("armour")!!, Moment.CHARGE, 0.62f, "Anna: core cable"),
            Tile(R.byId("mask")!!, Moment.CHARGE, 0.62f, "Mask Man: giant plug"),
            Tile(MonkeyCharacter, Moment.FULL, 0.5f, "100% → flex 💪"),
            Tile(MonkeyCharacter, Moment.LOW, 0.75f, "15% → dramatic collapse"),
            Tile(MonkeyCharacter, Moment.WIFI, 0.62f, "Wi-Fi → catches the signal"),
            Tile(MonkeyCharacter, Moment.HEADPHONES, 0.5f, "Headphones → dance"),
        )
        val tw = w / 4f; val th = MROW.toFloat()
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brown; textSize = 28f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x8A, 0x6A, 0x4A); textSize = 19f; textAlign = Paint.Align.CENTER }
        val d = 1.9f
        tiles.forEachIndexed { i, tl ->
            val x0 = (i % 4) * tw; val ty0 = y0 + 90f + (i / 4) * th
            val card = RectF(x0 + 12, ty0 + 6, x0 + tw - 12, ty0 + th - 16)
            c.drawRoundRect(card, 34f, 34f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xDD, 0xEE, 0xFF) })
            c.save(); c.clipRect(card)
            // Status bar with the battery icon at the far end.
            val bar = 26f * d
            c.drawRect(card.left, card.top, card.right, card.top + bar, Paint().apply { color = Color.rgb(0x20, 0x20, 0x28) })
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 13f * d }
            c.drawText("7:27", card.left + 14f, card.top + bar * 0.68f, tp)
            val target = PhoneMoments.batteryTarget(card.width(), bar, false, d).let { floatArrayOf(card.left + it[0], card.top + it[1]) }
            val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2.5f }
            c.drawRoundRect(RectF(target[0] - 11f * d, target[1] - 5.5f * d, target[0] + 9f * d, target[1] + 5.5f * d), 4f, 4f, bp)
            bp.style = Paint.Style.FILL
            val lvl = if (tl.m == Moment.LOW) 0.15f else if (tl.m == Moment.FULL) 1f else 0.4f
            bp.color = if (tl.m == Moment.LOW) Color.rgb(0xF4, 0x43, 0x36) else Color.rgb(0x66, 0xFF, 0x88)
            c.drawRect(target[0] - 9.5f * d, target[1] - 4f * d, target[0] - 9.5f * d + 17f * d * lvl, target[1] + 4f * d, bp)
            if (tl.m == Moment.WIFI) c.drawText("📶", card.right - 150f, card.top + bar * 0.75f, tp)
            // The character.
            val rig = MonkeyRig(); val pose = Pose(); val ctx = AnimContext()
            val wear = R.wear(tl.who); val renderer = tl.who.newRenderer().also { it.wear = wear }
            val st = tl.k * PhoneMoments.duration(tl.m, false)
            tl.who.fill(pose, MonkeyState.IDLE, 1f + st, st, ctx, wear)
            com.combo.monkeycompanion.rig.MomentPoses.apply(pose, tl.m, tl.k, st)
            val ax = card.left + card.width() * 0.4f; val ay = card.bottom - 120f
            val scale = 1.35f
            rig.layout(pose, ax, ay, scale, 1f)
            if (tl.m == Moment.CHARGE) {
                val style = PhoneMoments.chargeStyle(tl.who.id)
                val from = when (style) {
                    ChargeStyle.HEAT_BEAM -> floatArrayOf(0f, -41f).also { rig.head.mapPoints(it) }
                    ChargeStyle.CABLE -> floatArrayOf(ax, ay - 70f * scale)
                    ChargeStyle.GIANT_PLUG -> floatArrayOf(ax, ay - 46f * scale)
                    else -> FloatArray(2).also { rig.handPoint(false, it) }
                }
                if (style == ChargeStyle.CABLE || style == ChargeStyle.GIANT_PLUG) {
                    renderer.draw(c, rig, pose, 1f + st)
                    com.combo.monkeycompanion.rig.MomentFx.drawCharge(c, style, tl.k, from[0], from[1], target[0], target[1], d, 1.3f)
                } else {
                    com.combo.monkeycompanion.rig.MomentFx.drawCharge(c, style, tl.k, from[0], from[1], target[0], target[1], d, 1.3f)
                    renderer.draw(c, rig, pose, 1f + st)
                }
                // Capped sparks at the icon.
                val parts = com.combo.monkeycompanion.rig.Particles()
                val rnd = kotlin.random.Random(i)
                repeat(14) { val a = rnd.nextFloat() * 6.283f; val sp = (180f + rnd.nextFloat() * 260f) * d
                    parts.spawn(com.combo.monkeycompanion.rig.Particles.SPARK, target[0], target[1], kotlin.math.cos(a) * sp, kotlin.math.sin(a) * sp, 0.35f, 6f * d) }
                parts.update(0.08f); parts.draw(c)
            } else {
                renderer.draw(c, rig, pose, 1f + st)
                val ht = FloatArray(2); rig.headTop(ht)
                if (tl.m == Moment.HEADPHONES) com.combo.monkeycompanion.rig.MomentFx.drawHeadphones(c, ht[0], ht[1] + 14f * scale, com.combo.monkeycompanion.rig.MonkeyRig.HEAD_R * scale, 1f)
                if (tl.m == Moment.WIFI) com.combo.monkeycompanion.rig.MomentFx.drawWifi(c, ht[0], ht[1] + 6f * scale, tl.k, d)
            }
            // The line.
            val b = CloudBubble(d * 0.8f, 14f * d * 0.8f)
            b.setText(PhoneMoments.line(tl.m, tl.who.id, kotlin.random.Random(1)), (150 * d).toInt())
            val bl = (card.left + 10f).coerceAtMost(card.right - b.width - 10f)
            val bt = card.top + bar + (if (tl.m == Moment.CHARGE) 150f else 30f)
            val ht = FloatArray(2); rig.headTop(ht)
            b.draw(c, bl, bt, ht[0], ht[1], 1f, 1f)
            c.restore()
            c.drawText(tl.title, x0 + tw / 2f, ty0 + th - 70f, title)
            c.drawText(if (tl.m == Moment.CHARGE) "beam → battery icon · sparks · then the line" else Moment.entries.first { it == tl.m }.label.substringAfter(": "), x0 + tw / 2f, ty0 + th - 40f, sub)
        }
    }

    private const val LROW = 840

    /** Row 6: smart landings (decision beat, then the character's own pick). */
    private fun drawLandings(c: Canvas, y0: Float, w: Float) {
        val S = com.combo.monkeycompanion.brain.Landings
        val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brown; textSize = 38f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        c.drawText("6 · Smart landings: drop it mid-air and it picks its own way down", w / 2f, y0 + 50f, head)
        data class L(val style: com.combo.monkeycompanion.brain.Landings.Style?, val title: String, val sub: String, val st: Float)
        val tiles = listOf(
            L(null, "1. The decision beat", "looks down · scratches head", 0.3f),
            L(com.combo.monkeycompanion.brain.Landings.Style.CAPE_FLOAT, "Cape float, fist first", "Superboy: float or speed-dive", 0.4f),
            L(com.combo.monkeycompanion.brain.Landings.Style.PARACHUTE, "Leaf parachute", "Mask Man: his own umbrella", 0.5f),
            L(com.combo.monkeycompanion.brain.Landings.Style.SCREAM_JETS, "Scream… then jets!", "falls, then jets at the last second", 0.4f),
        )
        val tw = w / 4f; val th = LROW - 90f; val d = 1.9f; val scale = 1.35f
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brown; textSize = 28f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x8A, 0x6A, 0x4A); textSize = 19f; textAlign = Paint.Align.CENTER }
        tiles.forEachIndexed { i, tl ->
            val x0 = i * tw; val ty0 = y0 + 80f
            val card = RectF(x0 + 12, ty0, x0 + tw - 12, ty0 + th - 20)
            c.drawRoundRect(card, 34f, 34f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xDD, 0xEE, 0xFF) })
            c.save(); c.clipRect(card)
            val rig = MonkeyRig(); val pose = Pose(); val ctx = AnimContext()
            Animations.fill(pose, MonkeyState.FALLING, 1f + tl.st, tl.st, ctx)
            if (tl.style == null) com.combo.monkeycompanion.rig.LandingPoses.decide(pose, 0.5f, 1.1f)
            else com.combo.monkeycompanion.rig.LandingPoses.fall(pose, tl.style, tl.st)
            val ax = card.centerX(); val ay = card.top + th * 0.55f
            rig.layout(pose, ax, ay, scale, 1f)
            val ht = FloatArray(2); rig.headTop(ht)
            if (tl.style == com.combo.monkeycompanion.brain.Landings.Style.CAPE_FLOAT)
                com.combo.monkeycompanion.rig.LandingFx.drawCape(c, ht[0], ht[1] + (2 * MonkeyRig.HEAD_R + 16f) * scale, scale, 0.7f)
            val parts = com.combo.monkeycompanion.rig.Particles(); val rnd = kotlin.random.Random(i)
            val fx = ax; val fy = ay - 4f
            if (tl.style == com.combo.monkeycompanion.brain.Landings.Style.SCREAM_JETS) repeat(16) {
                parts.spawn(com.combo.monkeycompanion.rig.Particles.FLAME, fx + (rnd.nextFloat() - 0.5f) * 30f, fy + rnd.nextFloat() * 20f, (rnd.nextFloat() - 0.5f) * 60f * d, 300f * d, 0.3f, 8f * d) }
            if (tl.style == com.combo.monkeycompanion.brain.Landings.Style.CAPE_FLOAT) repeat(10) {
                parts.spawn(com.combo.monkeycompanion.rig.Particles.SPEED, fx + (rnd.nextFloat() - 0.5f) * 120f, fy - 150f - rnd.nextFloat() * 60f, 0f, -500f * d, 0.25f, 4f * d) }
            parts.update(0.03f); parts.draw(c)
            MonkeyRenderer().draw(c, rig, pose, 1f + tl.st)
            if (tl.style == com.combo.monkeycompanion.brain.Landings.Style.PARACHUTE) {
                val a = FloatArray(2); val b = FloatArray(2); rig.handPoint(false, a); rig.handPoint(true, b)
                com.combo.monkeycompanion.rig.LandingFx.drawLeaf(c, (a[0] + b[0]) / 2f, minOf(a[1], b[1]), scale, 0.5f)
            }
            // Ground line + where it will land.
            val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x9E, 0xB8, 0xD0); strokeWidth = 4f }
            c.drawLine(card.left, card.bottom - 40f, card.right, card.bottom - 40f, gp)
            val bubble = CloudBubble(d * 0.8f, 14f * d * 0.8f)
            bubble.setText(if (tl.style == null) "Hmm… how do I get down? 🤔" else tl.style.lines.first(), (150 * d).toInt())
            bubble.draw(c, (card.centerX() - bubble.width / 2f).coerceAtLeast(card.left + 8f), card.top + 16f, ht[0], ht[1], 1f, 1f)
            c.restore()
            c.drawText(tl.title, x0 + tw / 2f, ty0 + th + 20f, title)
            c.drawText(tl.sub, x0 + tw / 2f, ty0 + th + 50f, sub)
        }
        S.MIN_DROP_DP
    }
}
