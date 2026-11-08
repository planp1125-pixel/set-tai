package com.combo.monkeycompanion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.PhoneMoments
import com.combo.monkeycompanion.character.ArmourAnna
import com.combo.monkeycompanion.character.MonkeyCharacter
import com.combo.monkeycompanion.character.Superboy
import com.combo.monkeycompanion.character.Wear
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.Animations
import com.combo.monkeycompanion.rig.MomentFx
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.speech.CloudBubble

/**
 * Debug-only v7.0 preview sheet ("The Phone as a World"):
 *  1. Home World Weather/Conditions card + Solo/Duo/Trio toggle + Morning Night-Trace card
 *  2. Two neighbours bantering on screen together (Settai & Bolt in one shared window)
 *  3. Lasting Energy States: Hungry low-battery (<20%) huddle vs. Feeding time (charger bolt + eating current)
 *  4. Physical Keyboard (IME) Dodge: Settai relocates above imeTopY so spacebar / bottom keys stay 100% clear
 *  5. Reframed "🛠️ Settai's skills (errands & powers)" screen (real rendered SkillsActivity)
 */
object PreviewV7 {
    private val brown = Color.rgb(0x4A, 0x2C, 0x14)
    private val soft = Color.rgb(0x7A, 0x58, 0x38)
    private val accent = Color.rgb(0xE8, 0x8A, 0x2E)

    fun render(skillsShot: Bitmap): Bitmap {
        val W = 1680
        val H = 1360
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(0xFF, 0xF3, 0xE0))

        val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = brown; textSize = 35f; typeface = Typeface.create("sans-serif", Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        val secP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = brown; textSize = 21.5f; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        val borderP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0xE6, 0xC9, 0xA3); style = Paint.Style.STROKE; strokeWidth = 2.5f
        }

        c.drawText("Settai v7.0 · “The Phone as a World” — Weather, Places, Visitors, Neighbours & Skills", W / 2f, 44f, titleP)

        // Layout: 3 columns
        // Col 1 (x=22..562): Home World Card + Morning Night-Trace Card + Keyboard Dodge
        // Col 2 (x=584..1144): Two Neighbours Bantering (top) + Hungry vs Feeding Time (bottom)
        // Col 3 (x=1166..1658): Reframed "Settai's skills" screen (real SkillsActivity snapshot)
        val y0 = 68f

        // ---- Column 1 Top: World Weather / Conditions + Morning Night Trace Card ----
        val c1x = 22f; val c1w = 540f
        c.drawText("🌍 Home World Card & Morning Night Trace", c1x + 4f, y0 + 20f, secP)
        val worldCard = RectF(c1x, y0 + 30f, c1x + c1w, y0 + 640f)
        drawWorldAndTraceCard(c, worldCard, borderP)

        // ---- Column 1 Bottom: Physical Keyboard (IME) Dodge ----
        val kdY = worldCard.bottom + 18f
        c.drawText("⌨️ Physical Keyboard Dodge (above imeTopY)", c1x + 4f, kdY + 20f, secP)
        val kdCard = RectF(c1x, kdY + 30f, c1x + c1w, H - 22f)
        drawKeyboardDodgeCard(c, kdCard, borderP)

        // ---- Column 2 Top: Two Neighbours Bantering on Screen Together ----
        val c2x = c1x + c1w + 22f; val c2w = 560f
        c.drawText("👥 Neighbours Together (2-turn Director banter)", c2x + 4f, y0 + 20f, secP)
        val banterCard = RectF(c2x, y0 + 30f, c2x + c2w, y0 + 640f)
        drawNeighboursBanterCard(c, banterCard, borderP)

        // ---- Column 2 Bottom: Hungry (<20%) vs Feeding Time (Charging) ----
        val feedY = banterCard.bottom + 18f
        c.drawText("🍌⚡ Lasting Energy: Hungry (<20%) vs Feeding Time", c2x + 4f, feedY + 20f, secP)
        val feedCard = RectF(c2x, feedY + 30f, c2x + c2w, H - 22f)
        drawHungryAndFeedingCard(c, feedCard, borderP)

        // ---- Column 3: Reframed "Settai's skills" screen ----
        val c3x = c2x + c2w + 22f; val c3w = W - c3x - 22f
        c.drawText("🛠️ “Settai's skills” (errands & powers)", c3x + 4f, y0 + 20f, secP)
        val skillsRect = RectF(c3x, y0 + 30f, c3x + c3w, H - 22f)
        c.drawRoundRect(skillsRect, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        c.save()
        val clip = Path().apply { addRoundRect(skillsRect, 22f, 22f, Path.Direction.CW) }
        c.clipPath(clip)
        c.drawBitmap(skillsShot, null, skillsRect, Paint(Paint.FILTER_BITMAP_FLAG))
        c.restore()
        c.drawRoundRect(skillsRect, 22f, 22f, borderP)

        return bmp
    }

    private fun drawWorldAndTraceCard(c: Canvas, r: RectF, borderP: Paint) {
        c.drawRoundRect(r, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        c.save(); c.clipRect(r)
        val hP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brown; textSize = 19f; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD) }
        val bP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brown; textSize = 15f }
        val sP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = soft; textSize = 14f }

        var y = r.top + 30f
        c.drawText("🌍 The Phone as a World · Mind & Mood", r.left + 18f, y, hP)
        y += 28f
        val lines = listOf(
            "🌤️ Morning · 🍌 Well-fed (84%) · 🍃 Cool (30°C)",
            "📍 At the WhatsApp market · 📬 3 visitors at gate",
            "🌉 Bridge up (Wi-Fi) · 🤝 Visitor trust 86%",
            "👥 On screen: Settai & Bolt (Duo)",
        )
        for (l in lines) { c.drawText(l, r.left + 18f, y, bP); y += 23f }

        // Morning Night-Trace highlight box
        y += 6f
        val trBox = RectF(r.left + 16f, y, r.right - 16f, y + 84f)
        c.drawRoundRect(trBox, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x28, 0x32, 0x52) })
        val trTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xE0, 0x82); textSize = 14.5f; typeface = Typeface.DEFAULT_BOLD }
        val trBody = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 13.8f }
        c.drawText("🌙 Morning trace (while the glass was dark, 7h 30m):", trBox.left + 12f, trBox.top + 24f, trTitle)
        c.drawText("“While the glass was dark, Settai & Bolt shared a", trBox.left + 12f, trBox.top + 48f, trBody)
        c.drawText("banana near the battery icon & hid a 3 AM card 🍌🔋”", trBox.left + 12f, trBox.top + 68f, trBody)
        y = trBox.bottom + 18f

        // Neighbours toggle pills: Solo (1) / Duo (2) / Trio (3)
        c.drawText("Neighbours on screen:", r.left + 18f, y + 20f, hP)
        val modes = listOf("Solo (1)" to false, "Duo (2)" to true, "Trio (3)" to false)
        var px = r.left + 225f
        for ((lbl, sel) in modes) {
            val pr = RectF(px, y - 2f, px + 92f, y + 30f)
            c.drawRoundRect(pr, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (sel) accent else Color.rgb(0xFF, 0xF4, 0xE2) })
            val pt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (sel) Color.WHITE else brown; textSize = 13.5f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
            }
            c.drawText(lbl, pr.centerX(), pr.centerY() + 5f, pt)
            px += 98f
        }
        y += 46f

        // World condition bars
        val conds = listOf(
            Triple("⚡ Food / Energy", 0.84f, Color.rgb(0x43, 0xA0, 0x47)),
            Triple("🌡️ Weather / Heat", 0.22f, Color.rgb(0xFB, 0x8C, 0x00)),
            Triple("📬 Gate Busyness", 0.48f, Color.rgb(0x1E, 0x88, 0xE5)),
            Triple("🤝 Visitor Trust", 0.86f, Color.rgb(0x8E, 0x24, 0xAA)),
            Triple("🤫 Quiet / Respect", 0.15f, Color.rgb(0x54, 0x6E, 0x7A)),
        )
        val trackP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xF3, 0xE5, 0xD0) }
        val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
        for ((name, v, col) in conds) {
            c.drawText(name, r.left + 18f, y + 14f, sP)
            val bl = r.left + 165f; val br = r.right - 60f
            val brRect = RectF(bl, y + 3f, br, y + 15f)
            c.drawRoundRect(brRect, 6f, 6f, trackP)
            fillP.color = col
            c.drawRoundRect(RectF(bl, y + 3f, bl + (br - bl) * v, y + 15f), 6f, 6f, fillP)
            c.drawText("${(v * 100).toInt()}%", r.right - 50f, y + 14f, sP)
            y += 26f
        }

        // Prompt injection preview at bottom of card
        y += 8f
        val pBox = RectF(r.left + 16f, y, r.right - 16f, r.bottom - 14f)
        c.drawRoundRect(pBox, 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xF8, 0xEC) })
        c.drawText("AI Prompt World Line:", pBox.left + 10f, pBox.top + 20f, Paint(sP).apply { typeface = Typeface.DEFAULT_BOLD })
        c.drawText("“World: Morning, energy full (84%), breezy weather, 3 visitors", pBox.left + 10f, pBox.top + 40f, sP)
        c.drawText("waiting at the WhatsApp market, bridge up, visitor trust 0.86”", pBox.left + 10f, pBox.top + 58f, sP)

        c.restore()
        c.drawRoundRect(r, 22f, 22f, borderP)
    }

    private fun drawKeyboardDodgeCard(c: Canvas, r: RectF, borderP: Paint) {
        c.drawRoundRect(r, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xE8, 0xF1, 0xFA) })
        c.save(); c.clipRect(r)

        val imeTopY = r.top + r.height() * 0.54f
        // App chat area above keyboard
        val hdr = RectF(r.left + 14f, r.top + 14f, r.right - 14f, r.top + 48f)
        c.drawRoundRect(hdr, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x12, 0x8C, 0x7E) })
        val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 15f; typeface = Typeface.DEFAULT_BOLD }
        c.drawText("🏮 WhatsApp market · typing a message…", hdr.left + 12f, hdr.centerY() + 5f, hp)

        // Dashed imeTopY boundary line
        val lineP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xD3, 0x2F, 0x2F); strokeWidth = 3f }
        c.drawLine(r.left + 12f, imeTopY, r.right - 12f, imeTopY, lineP)
        val tagP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xD3, 0x2F, 0x2F); textSize = 13.5f; typeface = Typeface.DEFAULT_BOLD }
        c.drawText("▲ imeTopY (safe zone above keyboard — Settai climbs here)", r.left + 18f, imeTopY - 8f, tagP)

        // Soft keyboard at bottom (with visible spacebar and b, n, m keys completely unobstructed!)
        val kbRect = RectF(r.left + 8f, imeTopY + 4f, r.right - 8f, r.bottom - 8f)
        c.drawRoundRect(kbRect, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x26, 0x29, 0x30) })
        val keyP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x3C, 0x40, 0x48) }
        val keyTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 14f; textAlign = Paint.Align.CENTER }
        val row1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        val row2 = listOf("z", "x", "c", "v", "b", "n", "m", "⌫")
        val kw = (kbRect.width() - 24f) / 10f
        row1.forEachIndexed { i, k ->
            val kr = RectF(kbRect.left + 12f + i * kw + 2f, kbRect.top + 14f, kbRect.left + 12f + (i + 1) * kw - 2f, kbRect.top + 54f)
            c.drawRoundRect(kr, 6f, 6f, keyP); c.drawText(k, kr.centerX(), kr.centerY() + 5f, keyTxt)
        }
        row2.forEachIndexed { i, k ->
            val kr = RectF(kbRect.left + 42f + i * kw * 1.08f + 2f, kbRect.top + 64f, kbRect.left + 42f + (i + 1) * kw * 1.08f - 2f, kbRect.top + 104f)
            c.drawRoundRect(kr, 6f, 6f, keyP); c.drawText(k, kr.centerX(), kr.centerY() + 5f, keyTxt)
        }
        // Unobstructed spacebar row
        val spRect = RectF(kbRect.left + 110f, kbRect.top + 114f, kbRect.right - 110f, kbRect.bottom - 14f)
        c.drawRoundRect(spRect, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x4E, 0x54, 0x5E) })
        c.drawText("English (India) · spacebar 100% clear ✓", spRect.centerX(), spRect.centerY() + 5f, keyTxt)

        // Ghost outline at bottom showing where Settai moved FROM, with upward arrow to upper side wall
        val arrowP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0x2E, 0x7D, 0x32); style = Paint.Style.STROKE; strokeWidth = 4f
        }
        val arrow = Path().apply {
            moveTo(r.right - 95f, spRect.centerY())
            quadTo(r.right - 35f, imeTopY, r.right - 75f, imeTopY - 40f)
        }
        c.drawPath(arrow, arrowP)

        // Settai perched safely ABOVE imeTopY on the upper right side with mouth cloth (quiet respect)
        val rig = MonkeyRig(); val pose = Pose()
        Animations.fill(pose, MonkeyState.IDLE, 0.8f, 0.8f, AnimContext())
        pose.mouthCloth = 1f
        val fx = r.right - 78f; val fy = imeTopY - 18f
        rig.layout(pose, fx, fy, 0.92f, -1f)
        MonkeyCharacter.newRenderer().also { it.wear = Wear(MonkeyCharacter) }.draw(c, rig, pose, 0.8f)

        c.restore()
        c.drawRoundRect(r, 22f, 22f, borderP)
    }

    private fun drawNeighboursBanterCard(c: Canvas, r: RectF, borderP: Paint) {
        c.drawRoundRect(r, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xE4, 0xF3, 0xDF) })
        c.save(); c.clipRect(r)
        // Ground strip
        c.drawRect(r.left, r.bottom - 64f, r.right, r.bottom, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xBD, 0xDC, 0xB2) })

        val badge = RectF(r.left + 16f, r.top + 14f, r.right - 16f, r.top + 52f)
        c.drawRoundRect(badge, 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x3A, 0x22, 0x10) })
        val bt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 14.5f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        c.drawText("Single shared window · Settai (Scout) + Bolt (Engineer)", badge.centerX(), badge.centerY() + 5f, bt)

        val fy = r.bottom - 56f
        // Settai on the left (Turn 1)
        val rig1 = MonkeyRig(); val p1 = Pose()
        Animations.fill(p1, MonkeyState.GIGGLE, 0.9f, 0.9f, AnimContext())
        val x1 = r.left + 165f
        rig1.layout(p1, x1, fy, 1.12f, 1f)
        MonkeyCharacter.newRenderer().also { it.wear = Wear(MonkeyCharacter) }.draw(c, rig1, p1, 0.9f)
        val h1 = FloatArray(2); rig1.headTop(h1)

        val bub1 = CloudBubble(1.4f, 18f)
        bub1.setText("Ooh, a crunchy WhatsApp visitor card at the gate! *nibble* 📬😋", 240)
        bub1.draw(c, r.left + 18f, r.top + 74f, h1[0], h1[1], 1f, 1f)

        // Bolt on the right (Turn 2 companion response)
        val rig2 = MonkeyRig(); val p2 = Pose()
        Animations.fill(p2, MonkeyState.WAVE, 1.3f, 1.3f, AnimContext())
        val x2 = r.right - 155f
        rig2.layout(p2, x2, fy, 1.05f, -1f)
        ArmourAnna.newRenderer().also { it.wear = Wear(ArmourAnna) }.draw(c, rig2, p2, 1.3f)
        val h2 = FloatArray(2); rig2.headTop(h2)

        val bub2 = CloudBubble(1.4f, 18f)
        bub2.setText("Spit that out, Settai! That's incoming mail, not a mango snack! ⚙️😤", 240)
        bub2.draw(c, r.right - bub2.width - 18f, r.top + 135f, h2[0], h2[1], 1f, 1f)

        val capP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brown; textSize = 15f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
        c.drawText("Director 2-turn scene · obeys ProactiveGate · 0 extra windows", r.centerX(), r.bottom - 22f, capP)

        c.restore()
        c.drawRoundRect(r, 22f, 22f, borderP)
    }

    private fun drawHungryAndFeedingCard(c: Canvas, r: RectF, borderP: Paint) {
        val gap = 12f; val hw = (r.width() - gap) / 2f
        val leftR = RectF(r.left, r.top, r.left + hw, r.bottom)
        val rightR = RectF(r.left + hw + gap, r.top, r.right, r.bottom)

        // Left half: Low battery (<20%) lasting hungry & huddled mood
        c.drawRoundRect(leftR, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x2B, 0x22, 0x28) })
        c.save(); c.clipRect(leftR)
        val tP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xAB, 0x91); textSize = 15f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        c.drawText("🪫 Battery 14% · Hungry & Huddled", leftR.centerX(), leftR.top + 28f, tP)

        val fy = leftR.bottom - 48f
        val rigA = MonkeyRig(); val pA = Pose()
        Animations.fill(pA, MonkeyState.SIT, 1.0f, 1.0f, AnimContext())
        pA.eyeOpen = 0.55f; pA.bodyY += 5f; pA.mouthSmile = -0.3f; pA.armRRaise = 40f; pA.armRElbow = 95f
        rigA.layout(pA, leftR.centerX() - 46f, fy, 0.96f, 1f)
        MonkeyCharacter.newRenderer().also { it.wear = Wear(MonkeyCharacter) }.draw(c, rigA, pA, 1.0f)

        val rigB = MonkeyRig(); val pB = Pose()
        Animations.fill(pB, MonkeyState.SIT, 1.2f, 1.2f, AnimContext())
        pB.eyeOpen = 0.65f
        rigB.layout(pB, leftR.centerX() + 52f, fy, 0.90f, -1f)
        ArmourAnna.newRenderer().also { it.wear = Wear(ArmourAnna) }.draw(c, rigB, pB, 1.2f)

        val ht = FloatArray(2); rigA.headTop(ht)
        val bLow = CloudBubble(1.2f, 15.5f)
        bLow.setText("So hungry (14%)… moving slow & huddling for warmth 🥺🍌", (leftR.width() - 28f).toInt())
        bLow.draw(c, leftR.left + 14f, leftR.top + 44f, ht[0], ht[1], 1f, 1f)

        val subL = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xCC, 0xBC); textSize = 13.5f; textAlign = Paint.Align.CENTER }
        c.drawText("Lasting slow/hungry mood (0.66× speed)", leftR.centerX(), leftR.bottom - 14f, subL)
        c.restore()
        c.drawRoundRect(leftR, 20f, 20f, borderP)

        // Right half: Charger plugged = own-window bolt + Feeding Time
        c.drawRoundRect(rightR, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x1B, 0x28, 0x38) })
        c.save(); c.clipRect(rightR)
        val tx = rightR.right - 42f; val ty = rightR.top + 26f
        c.drawRoundRect(RectF(tx - 16f, ty - 8f, tx + 16f, ty + 8f), 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x76, 0xFF, 0x03) })

        val rigC = MonkeyRig(); val pC = Pose()
        Animations.fill(pC, MonkeyState.HAPPY_PAT, 0.6f, 0.6f, AnimContext())
        val cx = rightR.left + 95f
        rigC.layout(pC, cx, fy, 0.98f, 1f)
        val hand = FloatArray(2); rigC.handPoint(false, hand)
        MomentFx.drawBigBolt(c, PhoneMoments.ChargeStyle.ZAP, 0.5f, hand[0], hand[1], tx, ty, 1.5f, 0.5f)
        MonkeyCharacter.newRenderer().also { it.wear = Wear(MonkeyCharacter) }.draw(c, rigC, pC, 0.6f)

        val rigD = MonkeyRig(); val pD = Pose()
        Animations.fill(pD, MonkeyState.DANCE, 0.8f, 0.8f, AnimContext())
        rigD.layout(pD, rightR.right - 72f, fy, 0.88f, -1f)
        Superboy.newRenderer().also { it.wear = Wear(Superboy) }.draw(c, rigD, pD, 0.8f)

        val subR = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xF5, 0x9D); textSize = 13.5f; textAlign = Paint.Align.CENTER }
        c.drawText("⚡ Feeding time: bolt + eating current!", rightR.centerX(), rightR.bottom - 14f, subR)
        c.restore()
        c.drawRoundRect(rightR, 20f, 20f, borderP)
    }
}
