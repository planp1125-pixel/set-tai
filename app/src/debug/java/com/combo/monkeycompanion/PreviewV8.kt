package com.combo.monkeycompanion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.character.ArmourAnna
import com.combo.monkeycompanion.character.Billa
import com.combo.monkeycompanion.character.Chittu
import com.combo.monkeycompanion.character.MonkeyCharacter
import com.combo.monkeycompanion.character.Wear
import com.combo.monkeycompanion.heart.ComfortKind
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.Animations
import com.combo.monkeycompanion.rig.HeartFx
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.speech.CloudBubble
import com.combo.monkeycompanion.world.CatMouseBehaviors

/**
 * Debug-only v8.0 preview sheet:
 *  1. Top-Left: Heart & Care Mode (SIGN_CARD + GLASS_KNOCK_HUG screen hug + OFFER_COFFEE brass davara)
 *  2. Top-Right: Co-Presence Rituals (25m Focus Study Desk + 60s 4-4-6 Breathing Aura + Night Campfire with Duo neighbours)
 *  3. Bottom-Left: Original Cat-and-Mouse Chase Duo (`Billa` the street tabby & `Chittu` the tiny mouse in red vest)
 *  4. Bottom-Right: Next-Day Check-In Promise Card + Gemma 2B Empathetic Prompt Note + Dual APK (`full` & `play`) Summary
 */
object PreviewV8 {
    private val brown = Color.rgb(0x4A, 0x2C, 0x14)
    private val soft = Color.rgb(0x7A, 0x58, 0x38)

    fun render(): Bitmap {
        val W = 1680
        val H = 1360
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(0xFF, 0xF3, 0xE0))

        val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = brown; textSize = 34f; typeface = Typeface.create("sans-serif", Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        val secP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = brown; textSize = 21.5f; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        val borderP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0xE6, 0xC9, 0xA3); style = Paint.Style.STROKE; strokeWidth = 2.5f
        }

        c.drawText("Settai v8.0 · Heart & Care, Co-Presence Rituals, Billa & Chittu Chase Duo & Play Store Edition", W / 2f, 44f, titleP)

        val gap = 22f
        val colW = (W - gap * 3) / 2f
        val yTop = 66f
        val rowH = 590f

        // 1. Top-Left: Heart & Care Physical Comfort Animations
        val r1 = RectF(gap, yTop + 28f, gap + colW, yTop + 28f + rowH)
        c.drawText("❤️ 1. Heart & Care Mode: Physical Comfort on the Glass", r1.left + 4f, yTop + 20f, secP)
        drawHeartComfortPanel(c, r1, borderP)

        // 2. Top-Right: Co-Presence Rituals (Focus Desk, 60s Breathing Aura, Night Campfire)
        val r2 = RectF(r1.right + gap, yTop + 28f, r1.right + gap + colW, yTop + 28f + rowH)
        c.drawText("📚🫧🔥 2. Co-Presence Rituals: Focus Desk, 60s Breathe & Night Campfire", r2.left + 4f, yTop + 20f, secP)
        drawCoPresencePanel(c, r2, borderP)

        // 3. Bottom-Left: Original Cat & Mouse Chase Duo (Billa & Chittu)
        val yBot = r1.bottom + 20f
        val r3 = RectF(gap, yBot + 28f, gap + colW, H - 22f)
        c.drawText("🐱🐭 3. Original Cat-and-Mouse Chase Duo: Billa & Chittu (Single Window)", r3.left + 4f, yBot + 20f, secP)
        drawCatMousePanel(c, r3, borderP)

        // 4. Bottom-Right: Next-Day Check-In Promise Card & Play Store + Full Dual APK Card
        val r4 = RectF(r3.right + gap, yBot + 28f, r3.right + gap + colW, H - 22f)
        c.drawText("🌅🛡️ 4. Next-Day Check-In Promises & Dual Build (Full + Play Store APK)", r4.left + 4f, yBot + 20f, secP)
        drawCheckInAndFlavorsPanel(c, r4, borderP)

        return bmp
    }

    private fun drawHeartComfortPanel(c: Canvas, r: RectF, borderP: Paint) {
        c.drawRoundRect(r, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xFA, 0xF2) })
        c.save(); c.clipRect(r)

        val g = 12f
        val cw = (r.width() - g * 4) / 3f
        val top = r.top + 14f
        val bot = r.bottom - 14f
        val subP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brown; textSize = 14.5f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

        val cards = listOf(
            Triple("🪧 SIGN_CARD", ComfortKind.SIGN_CARD, "1 step at a time ❤️"),
            Triple("🫂 GLASS_KNOCK_HUG", ComfortKind.GLASS_KNOCK_HUG, "*tap-tap* Screen hug!"),
            Triple("☕ OFFER_COFFEE", ComfortKind.OFFER_COFFEE, "Brass davara coffee"),
        )
        cards.forEachIndexed { i, (title, kind, sign) ->
            val cr = RectF(r.left + g + i * (cw + g), top, r.left + g + i * (cw + g) + cw, bot)
            c.drawRoundRect(cr, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFD, 0xED, 0xD8) })
            c.drawRect(cr.left, cr.bottom - 52f, cr.right, cr.bottom, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xE8, 0xCE, 0xAD) })

            val rig = MonkeyRig(); val pose = Pose()
            Animations.fill(pose, MonkeyState.HAPPY_PAT, 1.1f, 1.1f, AnimContext())
            HeartFx.applyComfortPose(pose, kind, 1.1f)
            val fx = cr.centerX() - (if (kind == ComfortKind.OFFER_COFFEE) 16f else 0f)
            val fy = cr.bottom - 48f
            val sc = 1.02f
            rig.layout(pose, fx, fy, sc, 1f)
            MonkeyCharacter.newRenderer().also { it.wear = Wear(MonkeyCharacter) }.draw(c, rig, pose, 1.1f)
            val ht = FloatArray(2); rig.headTop(ht)
            HeartFx.drawComfort(c, kind, sign, ht[0], ht[1], fx, fy, sc, 1.1f)

            val bub = CloudBubble(1.15f, 15f)
            val line = when (kind) {
                ComfortKind.SIGN_CARD -> "Ayyo machan, breathe da—naan irukken ❤️"
                ComfortKind.GLASS_KNOCK_HUG -> "*tap-tap* Pressing paws on the glass for a hug 🫂"
                ComfortKind.OFFER_COFFEE -> "Hot filter coffee in a brass davara ☕💛"
            }
            bub.setText(line, (cr.width() - 20f).toInt())
            bub.draw(c, cr.left + 10f, cr.top + 14f, ht[0], ht[1], 1f, 1f)

            c.drawText(title, cr.centerX(), cr.bottom - 18f, subP)
            c.drawRoundRect(cr, 18f, 18f, borderP)
        }

        c.restore()
        c.drawRoundRect(r, 22f, 22f, borderP)
    }

    private fun drawCoPresencePanel(c: Canvas, r: RectF, borderP: Paint) {
        c.drawRoundRect(r, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x1E, 0x28, 0x38) })
        c.save(); c.clipRect(r)

        val g = 12f
        val cw = (r.width() - g * 4) / 3f
        val top = r.top + 14f
        val bot = r.bottom - 14f
        val subP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xF3, 0xD6); textSize = 14f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

        // Cell 1: Focus / Study Desk Buddy (25m Pomodoro)
        val c1 = RectF(r.left + g, top, r.left + g + cw, bot)
        c.drawRoundRect(c1, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x2B, 0x38, 0x4E) })
        run {
            val rig = MonkeyRig(); val pose = Pose()
            Animations.fill(pose, MonkeyState.SIT, 1.2f, 1.2f, AnimContext())
            val fx = c1.centerX(); val fy = c1.bottom - 56f; val sc = 1.0f
            rig.layout(pose, fx, fy, sc, 1f)
            MonkeyCharacter.newRenderer().also { it.wear = Wear(MonkeyCharacter) }.draw(c, rig, pose, 1.2f)
            val ht = FloatArray(2); rig.headTop(ht)
            HeartFx.drawFocusDesk(c, fx, fy, ht[0], ht[1], sc, 1.2f, "📚 24:18")
            val bub = CloudBubble(1.1f, 14.5f)
            bub.setText("Studying quietly with you… zero noisy interruptions ✍️☕", (c1.width() - 20f).toInt())
            bub.draw(c, c1.left + 10f, c1.top + 16f, ht[0], ht[1], 1f, 1f)
            c.drawText("1. Focus Study Desk (25m)", c1.centerX(), c1.bottom - 16f, subP)
        }

        // Cell 2: 60-Second Calm Breathing Buddy (4-4-6 aura)
        val c2 = RectF(c1.right + g, top, c1.right + g + cw, bot)
        c.drawRoundRect(c2, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x1B, 0x3A, 0x4B) })
        run {
            val rig = MonkeyRig(); val pose = Pose()
            Animations.fill(pose, MonkeyState.SIT, 2.0f, 2.0f, AnimContext())
            pose.mouthSmile = 0.85f; pose.eyeOpen = 0.3f
            val fx = c2.centerX(); val fy = c2.bottom - 56f; val sc = 1.02f
            rig.layout(pose, fx, fy, sc, 1f)
            val ht = FloatArray(2); rig.headTop(ht)
            HeartFx.drawBreathingAura(c, fx, (ht[1] + fy) * 0.52f, sc, 0.88f, "Breathe in…", 42)
            MonkeyCharacter.newRenderer().also { it.wear = Wear(MonkeyCharacter) }.draw(c, rig, pose, 2.0f)
            val bub = CloudBubble(1.1f, 14.5f)
            bub.setText("4s inhale · 4s hold · 6s exhale 🫧🌿", (c2.width() - 20f).toInt())
            bub.draw(c, c2.left + 10f, c2.top + 16f, ht[0], ht[1], 1f, 1f)
            c.drawText("2. 60s Calm Breathing Aura", c2.centerX(), c2.bottom - 16f, subP)
        }

        // Cell 3: Night Campfire with Duo Neighbour warming hands
        val c3 = RectF(c2.right + g, top, c2.right + g + cw, bot)
        c.drawRoundRect(c3, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x16, 0x1D, 0x30) })
        run {
            val fy = c3.bottom - 56f
            val rigL = MonkeyRig(); val pL = Pose()
            Animations.fill(pL, MonkeyState.SIT, 1.5f, 1.5f, AnimContext())
            rigL.layout(pL, c3.left + 68f, fy, 0.90f, 1f)
            MonkeyCharacter.newRenderer().also { it.wear = Wear(MonkeyCharacter) }.draw(c, rigL, pL, 1.5f)

            val rigR = MonkeyRig(); val pR = Pose()
            Animations.fill(pR, MonkeyState.SIT, 1.8f, 1.8f, AnimContext())
            rigR.layout(pR, c3.right - 68f, fy, 0.86f, -1f)
            ArmourAnna.newRenderer().also { it.wear = Wear(ArmourAnna) }.draw(c, rigR, pR, 1.8f)

            HeartFx.drawCampfire(c, c3.centerX(), fy, 1.0f, 1.6f)
            val ht = FloatArray(2); rigL.headTop(ht)
            val bub = CloudBubble(1.1f, 14.5f)
            bub.setText("Late-night campfire with Anna 🔥🌙", (c3.width() - 20f).toInt())
            bub.draw(c, c3.left + 10f, c3.top + 16f, ht[0], ht[1], 1f, 1f)
            c.drawText("3. Night Campfire (Duo)", c3.centerX(), c3.bottom - 16f, subP)
        }

        c.restore()
        c.drawRoundRect(r, 22f, 22f, borderP)
    }

    private fun drawCatMousePanel(c: Canvas, r: RectF, borderP: Paint) {
        c.drawRoundRect(r, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xE8, 0xF4, 0xE5) })
        c.save(); c.clipRect(r)
        c.drawRect(r.left, r.bottom - 62f, r.right, r.bottom, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xC2, 0xDF, 0xB8) })

        val badge = RectF(r.left + 16f, r.top + 14f, r.right - 16f, r.top + 50f)
        c.drawRoundRect(badge, 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x37, 0x47, 0x4F) })
        val bt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 14.5f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        c.drawText("100% Original Duo · Billa (Notched-Ear Street Tabby) vs. Chittu (Red-Vest Mouse)", badge.centerX(), badge.centerY() + 5f, bt)

        val fy = r.bottom - 54f
        val gag = CatMouseBehaviors.GagId.TIPTOE_CHILLI
        val pBilla = Pose(); val pChittu = Pose()
        Animations.fill(pBilla, MonkeyState.STARTLED, 1.4f, 1.4f, AnimContext())
        Animations.fill(pChittu, MonkeyState.GIGGLE, 1.4f, 1.4f, AnimContext())
        CatMouseBehaviors.applySlapstick(gag, 0.65f, 1.4f, pBilla, pChittu)

        val xBilla = r.left + 210f
        val xChittu = r.right - 200f
        val rigB = MonkeyRig()
        rigB.layout(pBilla, xBilla, fy, 1.15f, 1f)
        val wB = Wear(Billa).apply { set("bandana", true); set("pan", true) }
        Billa.newRenderer().also { it.wear = wB }.draw(c, rigB, pBilla, 1.4f)
        val hB = FloatArray(2); rigB.headTop(hB)

        val rigC = MonkeyRig()
        rigC.layout(pChittu, xChittu, fy, 1.05f, -1f)
        val wC = Wear(Chittu).apply { set("vest", true); set("cheese", true); set("chilli", true) }
        Chittu.newRenderer().also { it.wear = wC }.draw(c, rigC, pChittu, 1.4f)
        val hC = FloatArray(2); rigC.headTop(hC)

        CatMouseBehaviors.drawProps(c, gag, 0.65f, (xBilla + xChittu) * 0.5f, fy, 1.15f, 1.4f)

        val bub1 = CloudBubble(1.3f, 17f)
        bub1.setText(gag.billaLine, 320)
        bub1.draw(c, r.left + 18f, r.top + 64f, hB[0], hB[1], 1f, 1f)

        val bub2 = CloudBubble(1.3f, 17f)
        bub2.setText(gag.chittuLine, 320)
        bub2.draw(c, r.right - bub2.width - 18f, r.top + 115f, hC[0], hC[1], 1f, 1f)

        val capP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brown; textSize = 14.5f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        c.drawText("Modular CatMouseBehaviors table: Tiptoe Chilli · Cheese Accordion · Tawa Whistle Zip · Truce Nap", r.centerX(), r.bottom - 20f, capP)

        c.restore()
        c.drawRoundRect(r, 22f, 22f, borderP)
    }

    private fun drawCheckInAndFlavorsPanel(c: Canvas, r: RectF, borderP: Paint) {
        c.drawRoundRect(r, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        c.save(); c.clipRect(r)

        val hP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brown; textSize = 18.5f; typeface = Typeface.DEFAULT_BOLD }
        val bP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brown; textSize = 14.5f }
        val sP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = soft; textSize = 13.8f }

        // Top Box: Next-Day Check-In Promise on Morning Unlock
        val box1 = RectF(r.left + 16f, r.top + 16f, r.right - 16f, r.top + 255f)
        c.drawRoundRect(box1, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xF8, 0xEB) })
        c.drawText("🌅 Next-Day Check-In Promise (CareCheckInStore)", box1.left + 14f, box1.top + 28f, hP)
        c.drawText("• Yesterday 21:40 user said: “I have a big coding interview tomorrow, romba bayama iruku”", box1.left + 14f, box1.top + 56f, sP)
        c.drawText("• Next morning unlock (08:15) Settai pops out & asks:", box1.left + 14f, box1.top + 80f, sP)

        val quoteBox = RectF(box1.left + 14f, box1.top + 92f, box1.right - 14f, box1.top + 168f)
        c.drawRoundRect(quoteBox, 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xEC, 0xCE) })
        c.drawText("“Machan, yesterday you had that big interview—epdi pochu (how did it go)?", quoteBox.left + 12f, quoteBox.top + 28f, Paint(bP).apply { typeface = Typeface.DEFAULT_BOLD })
        c.drawText("I kept my fingers crossed all night! 🤞💛”   [Went great! 🎉] [Tough day 🥺] [🫂 Screen hug]", quoteBox.left + 12f, quoteBox.top + 54f, bP)

        c.drawText("• Gemma 2B Prompt Injection: includes detected mood + 1-line warm Tamil Nadu friend example.", box1.left + 14f, box1.top + 198f, sP)
        c.drawText("• 1–4 AM Rhythm Sensor: detects restless late-night app switching & offers gentle comfort.", box1.left + 14f, box1.top + 222f, sP)

        // Bottom Box: Dual APK Build (Full APK vs. Google Play Store APK)
        val box2 = RectF(r.left + 16f, box1.bottom + 14f, r.right - 16f, r.bottom - 16f)
        c.drawRoundRect(box2, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xEC, 0xF5, 0xEE) })
        c.drawText("🛡️ Dual APK Build: Full Edition + Google Play Store Edition", box2.left + 14f, box2.top + 30f, hP)
        val rows = listOf(
            "1. settai-v8.apk (full flavor, v8.0 / code 16): 100% of features including SettaiAccessibilityService.",
            "2. settai-v8-play.apk (play flavor, v8.0 / code 16): zero AccessibilityService in merged manifest ✓",
            "   • ForegroundAppTracker uses UsageStatsManager on visible heartbeats (0 background polling).",
            "   • Retains World places, Mind awareness, 1px IME keyboard dodge, Notification reader & Guided Errands.",
        )
        rows.forEachIndexed { idx, line ->
            c.drawText(line, box2.left + 14f, box2.top + 60f + idx * 30f, bP)
        }

        c.restore()
        c.drawRoundRect(r, 22f, 22f, borderP)
    }

    /** Crisp close-up contact sheet of Billa & Chittu on the overlay showing their fixed clean heads, ears, slit eyes, and CloudBubble banter. */
    fun renderCatMouseCloseUp(): Bitmap {
        val W = 1280
        val H = 680
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(0xFF, 0xF3, 0xE0))

        val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = brown; textSize = 28f; typeface = Typeface.create("sans-serif", Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        val subP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = brown; textSize = 16.5f; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        val borderP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0xE6, 0xC9, 0xA3); style = Paint.Style.STROKE; strokeWidth = 2.5f
        }

        c.drawText("Billa & Chittu Overlay Fix · Clean Heads/Ears/Slit-Eyes, Ground Props & CloudBubble Banter", W / 2f, 38f, titleP)

        // Left Card: Close-up of Billa & Chittu front + raised-arm wave (no pan/cheese/tuft over heads)
        val rLeft = RectF(22f, 58f, 628f, H - 22f)
        c.drawRoundRect(rLeft, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xFA, 0xF2) })
        c.save(); c.clipRect(rLeft)
        c.drawRect(rLeft.left, rLeft.bottom - 70f, rLeft.right, rLeft.bottom, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xE4, 0xD2, 0xB8) })
        val fy = rLeft.bottom - 62f

        val rigB = MonkeyRig(); val pB = Pose()
        Animations.fill(pB, MonkeyState.IDLE, 1.0f, 1.0f, AnimContext())
        rigB.layout(pB, rLeft.left + 185f, fy, 1.55f, 1f)
        val wB = Wear(Billa).apply { set("bandana", true); set("pan", true) }
        Billa.newRenderer().also { it.wear = wB }.draw(c, rigB, pB, 1.0f)

        val rigC = MonkeyRig(); val pC = Pose()
        Animations.fill(pC, MonkeyState.WAVE, 1.0f, 1.0f, AnimContext())
        rigC.layout(pC, rLeft.right - 185f, fy, 1.45f, -1f)
        val wC = Wear(Chittu).apply { set("vest", true); set("cheese", true); set("chilli", true) }
        Chittu.newRenderer().also { it.wear = wC }.draw(c, rigC, pC, 1.0f)

        c.drawText("Clean pointed cat ears + slit pupils · Wide side mouse ears · No head artifacts", rLeft.centerX(), rLeft.bottom - 22f, subP)
        c.restore()
        c.drawRoundRect(rLeft, 22f, 22f, borderP)

        // Right Card: Overlay Turn-2 Banter using real CloudBubble (no clamped beige pill box over heads) + ground chilli prop
        val rRight = RectF(652f, 58f, W - 22f, H - 22f)
        c.drawRoundRect(rRight, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xE8, 0xF4, 0xE5) })
        c.save(); c.clipRect(rRight)
        c.drawRect(rRight.left, rRight.bottom - 70f, rRight.right, rRight.bottom, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xC2, 0xDF, 0xB8) })

        val gag = CatMouseBehaviors.GagId.TIPTOE_CHILLI
        val pB2 = Pose(); val pC2 = Pose()
        Animations.fill(pB2, MonkeyState.WALK, 1.4f, 1.4f, AnimContext())
        Animations.fill(pC2, MonkeyState.GIGGLE, 1.4f, 1.4f, AnimContext())
        CatMouseBehaviors.applySlapstick(gag, 0.65f, 1.4f, pB2, pC2)

        val x1 = rRight.left + 175f; val x2 = rRight.right - 175f
        val rigB2 = MonkeyRig(); rigB2.layout(pB2, x1, fy, 1.35f, 1f)
        Billa.newRenderer().also { it.wear = wB }.draw(c, rigB2, pB2, 1.4f)

        val rigC2 = MonkeyRig(); rigC2.layout(pC2, x2, fy, 1.25f, -1f)
        Chittu.newRenderer().also { it.wear = wC }.draw(c, rigC2, pC2, 1.4f)
        val hC2 = FloatArray(2); rigC2.headTop(hC2)

        CatMouseBehaviors.drawProps(c, gag, 0.65f, (x1 + x2) * 0.5f, fy, 1.35f, 1.4f)

        val bub = CloudBubble(1.45f, 18f)
        bub.setText("Chittu: ${gag.chittuLine}", 460)
        bub.draw(c, (rRight.centerX() - bub.width / 2f).coerceAtLeast(rRight.left + 16f), rRight.top + 24f, hC2[0], hC2[1], 1f, 1f)

        c.drawText("Turn-2 uses CloudBubble above heads · Chilli prop sits on ground", rRight.centerX(), rRight.bottom - 22f, subP)
        c.restore()
        c.drawRoundRect(rRight, 22f, 22f, borderP)

        return bmp
    }
}
