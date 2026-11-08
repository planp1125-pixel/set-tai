package com.combo.monkeycompanion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.PhoneMoments
import com.combo.monkeycompanion.character.ArmourAnna
import com.combo.monkeycompanion.character.Character
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
 * Debug-only v6.0 preview sheet:
 *  - Left: real rendered DiaryActivity (character tabs, thinking summary, MoodBarsView, seeded favourites, cute diary entries)
 *  - Right top: "What's Settai thinking?" menu chip + in-character thinking bubble
 *  - Right middle: Explore-new-app 3-step sequence (1. Walk over -> 2. Sniff around -> 3. Peek & comment)
 *  - Right bottom: Real-device fixes (Bolt hover boot/palm jets in own window + Charger plug-in big lightning bolt in own window)
 */
object PreviewV6 {
    private val brown = Color.rgb(0x4A, 0x2C, 0x14)
    private val soft = Color.rgb(0x7A, 0x58, 0x38)
    private val accent = Color.rgb(0xE8, 0x8A, 0x2E)

    fun render(diaryShot: Bitmap): Bitmap {
        val W = 1600
        val H = 1320
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(0xFF, 0xF3, 0xE0))

        val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = brown; textSize = 36f; typeface = Typeface.create("sans-serif", Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        val secP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = brown; textSize = 23f; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        val subP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = soft; textSize = 16.5f; textAlign = Paint.Align.CENTER
        }
        val cardP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val borderP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0xE6, 0xC9, 0xA3); style = Paint.Style.STROKE; strokeWidth = 2.5f
        }

        c.drawText("Settai v6.0 · “Settai mind” — Continuity, Personality, Awareness, Autonomy & Respect", W / 2f, 46f, titleP)

        // ---- Left column: Diary screen + Mood bars ----
        val leftX = 24f; val leftY = 72f; val leftW = 580f; val leftH = H - leftY - 24f
        c.drawText("📖 Settai's diary & mood bars (per character)", leftX + 6f, leftY + 22f, secP)
        val shotRect = RectF(leftX, leftY + 34f, leftX + leftW, leftY + leftH)
        c.drawRoundRect(shotRect, 24f, 24f, cardP)
        c.save()
        val clipPath = Path().apply { addRoundRect(shotRect, 24f, 24f, Path.Direction.CW) }
        c.clipPath(clipPath)
        c.drawBitmap(diaryShot, null, shotRect, Paint(Paint.FILTER_BITMAP_FLAG))
        c.restore()
        c.drawRoundRect(shotRect, 24f, 24f, borderP)

        // ---- Right column ----
        val rx = leftX + leftW + 28f
        val rw = W - rx - 24f

        // 1. Top-right: "What's Settai thinking?" chip & thinking bubble
        var ry = leftY
        c.drawText("💭 “What's Settai thinking?” chip & in-character mood bubble", rx + 6f, ry + 22f, secP)
        val thinkRect = RectF(rx, ry + 34f, rx + rw, ry + 390f)
        c.drawRoundRect(thinkRect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xE8, 0xF3, 0xE6) })
        c.drawRoundRect(thinkRect, 24f, 24f, borderP)
        drawThinkingPanel(c, thinkRect)

        // 2. Middle-right: Explore-new-app 3-step sequence
        ry = thinkRect.bottom + 18f
        c.drawText("🧭 Awareness: explore-new-app sequence (walk over → sniff → peek & comment)", rx + 6f, ry + 22f, secP)
        val expTop = ry + 34f; val expH = 360f
        val gap = 14f; val cellW = (rw - 2 * gap) / 3f
        val steps = listOf(
            Triple("1. Walk over to new app", MonkeyState.WALK, "Saw Swiggy (new!) → exploring…"),
            Triple("2. Sniff around", MonkeyState.PEEK, "*sniff sniff* 👃 Smells like biryani!"),
            Triple("3. Peek & comment", MonkeyState.HAPPY_PAT, "FOOD?! Order me a banana shake 🍌"),
        )
        steps.forEachIndexed { i, (label, st, line) ->
            val cr = RectF(rx + i * (cellW + gap), expTop, rx + i * (cellW + gap) + cellW, expTop + expH)
            drawExploreCell(c, cr, i, label, st, line, subP, borderP)
        }

        // 3. Bottom-right: Real-device fixes (Bolt hover jets + Charger lightning bolt in own window)
        ry = expTop + expH + 18f
        c.drawText("🚀⚡ Real-device fixes: Bolt's hover jets & charger lightning bolt (in own window)", rx + 6f, ry + 22f, secP)
        val fixTop = ry + 34f; val fixH = H - fixTop - 24f
        val halfW = (rw - gap) / 2f
        val rAnna = RectF(rx, fixTop, rx + halfW, fixTop + fixH)
        val rBolt = RectF(rx + halfW + gap, fixTop, rx + rw, fixTop + fixH)
        drawAnnaHoverPanel(c, rAnna, subP, borderP)
        drawChargerBoltPanel(c, rBolt, subP, borderP)

        return bmp
    }

    private fun drawThinkingPanel(c: Canvas, r: RectF) {
        c.save(); c.clipRect(r)
        // Menu pill on the left
        val pill = RectF(r.left + 22f, r.top + 22f, r.left + 320f, r.top + 64f)
        val pillFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x3A, 0x22, 0x10) }
        val pillTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 18f; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        c.drawRoundRect(pill, 21f, 21f, pillFill)
        c.drawText("🧠 What's Settai thinking?", pill.centerX(), pill.centerY() + 6f, pillTxt)

        // Decision log badge underneath
        val logBox = RectF(r.left + 22f, r.top + 78f, r.left + 390f, r.bottom - 22f)
        c.drawRoundRect(logBox, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xF9, 0xEE) })
        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brown; textSize = 14.5f; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD) }
        val ls = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = soft; textSize = 13.5f }
        c.drawText("Transparency · Recent mind decisions:", logBox.left + 14f, logBox.top + 26f, lp)
        val logs = listOf(
            "• Saw WhatsApp (typing) · focus 1.00 → ignored",
            "• Saw HDFC Bank · focus 0.90 → hid respectfully",
            "• Saw Swiggy, loves it → went exploring",
            "• Long reading (8m) → banana break! 🍌",
            "• Unlocked 07:15 → morning stretch ritual",
            "• Mind CPU: 147 µs/decision (0 polling off-screen)",
        )
        logs.forEachIndexed { idx, s -> c.drawText(s, logBox.left + 14f, logBox.top + 52f + idx * 28f, ls) }

        // Settai thinking on the right + CloudBubble
        val rig = MonkeyRig(); val pose = Pose()
        Animations.fill(pose, MonkeyState.THINKING, 1.1f, 1.1f, AnimContext())
        val fx = r.right - 140f; val fy = r.bottom - 26f
        rig.layout(pose, fx, fy, 1.15f, 1f)
        MonkeyCharacter.newRenderer().also { it.wear = Wear(MonkeyCharacter) }.draw(c, rig, pose, 1.1f)
        val ht = FloatArray(2); rig.headTop(ht)

        val bub = CloudBubble(2f, 26f)
        val msg = "💭 Curious what you're up to in Swiggy. Content — a jackfruit would make it perfect.\n(energy 0.78 · bored 0.22 · social 0.48 · curious 0.84)"
        bub.setText(msg, 430, listOf("📖 Settai's diary", "Got it"), reply = false, chips = true)
        val bx = (fx - bub.width - 18f).coerceAtLeast(r.left + 405f)
        val by = (r.top + 18f)
        bub.draw(c, bx, by, ht[0], ht[1], 1f, 1f)
        c.restore()
    }

    private fun drawExploreCell(c: Canvas, r: RectF, step: Int, label: String, st: MonkeyState, line: String, subP: Paint, borderP: Paint) {
        c.drawRoundRect(r, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xE6, 0xF2, 0xFF) })
        c.save(); c.clipRect(r)
        // Fake app header inside cell
        val bar = RectF(r.left + 12f, r.top + 12f, r.right - 12f, r.top + 44f)
        c.drawRoundRect(bar, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0x70, 0x20) })
        val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 14f; typeface = Typeface.DEFAULT_BOLD }
        c.drawText("🍔 Swiggy · Food delivery", bar.left + 12f, bar.centerY() + 5f, bp)

        // Floor
        c.drawRect(r.left, r.bottom - 52f, r.right, r.bottom, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xC9, 0xDE, 0xF4) })

        val rig = MonkeyRig(); val pose = Pose()
        Animations.fill(pose, st, 0.7f + step * 0.3f, 0.7f, AnimContext())
        if (step == 1) { pose.bodyRot = 12f; pose.headRot = -10f; pose.lookX = 1f }
        val fx = r.left + r.width() * (when (step) { 0 -> 0.34f; 1 -> 0.56f; else -> 0.62f })
        val fy = r.bottom - 48f
        rig.layout(pose, fx, fy, 1.0f, 1f)
        MonkeyCharacter.newRenderer().also { it.wear = Wear(MonkeyCharacter) }.draw(c, rig, pose, 0.9f)
        val ht = FloatArray(2); rig.headTop(ht)

        val bub = CloudBubble(1.2f, 16f)
        bub.setText(line, (r.width() - 64f).toInt().coerceAtLeast(80))
        val bx = (r.centerX() - bub.width / 2f).coerceAtLeast(r.left + 6f)
        val by = (ht[1] - bub.height - 10f).coerceAtLeast(bar.bottom + 6f)
        bub.draw(c, bx, by, ht[0], ht[1], 1f, 1f)

        c.drawText(label, r.centerX(), r.bottom - 18f, subP)
        c.restore()
        c.drawRoundRect(r, 20f, 20f, borderP)
    }

    private fun drawAnnaHoverPanel(c: Canvas, r: RectF, subP: Paint, borderP: Paint) {
        c.drawRoundRect(r, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x1E, 0x26, 0x36) })
        c.save(); c.clipRect(r)
        val rig = MonkeyRig(); val pose = Pose()
        Animations.fill(pose, MonkeyState.IDLE, 1.2f, 1.2f, AnimContext())
        pose.shadow = 0f; pose.legLLift = 4f; pose.legRLift = 6f
        val sc = 1.05f
        val fx = r.centerX() - 70f; val fy = r.centerY() + 18f
        rig.layout(pose, fx, fy, sc, 1f)

        // Boot jet flames + cyan palm jets (same gradient & geometry as MonkeyView.drawLandingThrust)
        val thrust = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val path = Path()
        for (side in intArrayOf(-1, 1)) {
            val jx = fx + side * 9f * sc; val jy = fy; val len = 42f * sc; val wdt = 9.5f * sc
            thrust.shader = LinearGradient(jx, jy, jx, jy + len,
                intArrayOf(Color.rgb(0xFF, 0xF4, 0xB0), Color.rgb(0xFF, 0x9A, 0x1F), Color.argb(0, 0xE8, 0x3A, 0x10)),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            path.reset(); path.moveTo(jx - wdt, jy); path.quadTo(jx - wdt * 0.6f, jy + len * 0.6f, jx, jy + len)
            path.quadTo(jx + wdt * 0.6f, jy + len * 0.6f, jx + wdt, jy); path.close()
            c.drawPath(path, thrust)
        }
        val hp = FloatArray(2)
        for (left in booleanArrayOf(true, false)) {
            rig.handPoint(left, hp)
            val hx = hp[0]; val hy = hp[1]; val len = 20f * sc; val wdt = 5f * sc
            thrust.shader = LinearGradient(hx, hy, hx, hy + len,
                intArrayOf(Color.rgb(0xE0, 0xF6, 0xFF), Color.rgb(0x6F, 0xC8, 0xFF), Color.argb(0, 0x30, 0x80, 0xFF)),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            path.reset(); path.moveTo(hx - wdt, hy); path.quadTo(hx - wdt * 0.6f, hy + len * 0.6f, hx, hy + len)
            path.close()
            c.drawCircle(hx, hy + len * 0.35f, wdt, thrust)
        }
        thrust.shader = null
        ArmourAnna.newRenderer().also { it.wear = Wear(ArmourAnna) }.draw(c, rig, pose, 1.2f)

        // Curved swipe-down trajectory preview on the right half of the card
        val arcP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 0xFF, 0xB7, 0x4D); style = Paint.Style.STROKE; strokeWidth = 4f
        }
        val p = Path().apply {
            moveTo(fx, fy - 40f)
            cubicTo(r.right - 30f, r.top + 30f, r.left + 30f, r.centerY(), r.right - 90f, r.bottom - 48f)
        }
        c.drawPath(p, arcP)
        val lblP = Paint(subP).apply { color = Color.rgb(0xE0, 0xEC, 0xFF) }
        c.drawText("Bolt hover jets + big curved swipe-down flight", r.centerX(), r.bottom - 14f, lblP)
        c.restore()
        c.drawRoundRect(r, 20f, 20f, borderP)
    }

    private fun drawChargerBoltPanel(c: Canvas, r: RectF, subP: Paint, borderP: Paint) {
        c.drawRoundRect(r, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x1E, 0x22, 0x30) })
        c.save(); c.clipRect(r)
        // Status bar battery icon at top-right
        val tx = r.right - 54f; val ty = r.top + 28f
        val batP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x80, 0xE8, 0x70) }
        c.drawRoundRect(RectF(tx - 18f, ty - 9f, tx + 18f, ty + 9f), 4f, 4f, batP)

        val rig = MonkeyRig(); val pose = Pose()
        Animations.fill(pose, MonkeyState.HAPPY_PAT, 0.5f, 0.5f, AnimContext())
        val fx = r.left + 130f; val fy = r.bottom - 44f
        rig.layout(pose, fx, fy, 1.05f, 1f)
        val hand = FloatArray(2); rig.handPoint(false, hand)

        MomentFx.drawBigBolt(c, PhoneMoments.ChargeStyle.HEAT_BEAM, 0.5f, hand[0], hand[1], tx, ty, 1.8f, 0.6f)
        Superboy.newRenderer().also { it.wear = Wear(Superboy) }.draw(c, rig, pose, 0.5f)

        val lblP = Paint(subP).apply { color = Color.rgb(0xFF, 0xE6, 0xC8) }
        c.drawText("Charger plugged → own-window bolt to status-bar battery ⚡", r.centerX(), r.bottom - 14f, lblP)
        c.restore()
        c.drawRoundRect(r, 20f, 20f, borderP)
    }
}
