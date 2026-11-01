package com.combo.monkeycompanion.agent

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.character.MonkeyCharacter
import com.combo.monkeycompanion.character.Wear
import com.combo.monkeycompanion.overlay.MonkeyView
import com.combo.monkeycompanion.powers.SettaiNotificationListener
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.Animations
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.speech.CloudBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotifSummaryCardTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    @org.junit.After fun cleanUp() { NotifStore.reset(emptyList()) }

    private class TestHost : com.combo.monkeycompanion.overlay.OverlayHost {
        var x = 0; var y = 0; var w = 0; var h = 0
        override fun screenWidth() = 1080
        override fun screenHeight() = 2340
        override fun placeWindow(x: Int, y: Int, w: Int, h: Int) {
            this.x = x; this.y = y; this.w = w; this.h = h
        }
    }

    @Test fun groupsNotificationsBySenderAndApp() {
        val now = 1_000_000_000L
        val raw = NotifSummary.demoItems(now)
        val groups = NotifSummary.group(raw, maxGroups = 4)

        // 4 raw items (2 from Rakesh on WhatsApp) -> 3 grouped rows
        assertEquals(3, groups.size)
        assertEquals("File Sharing with Bharath", groups[0].title)
        assertEquals("demo_share_1", groups[0].key)
        assertTrue(groups[0].summaryText.contains("settai-v8-play.apk"))

        assertEquals("WhatsApp · Rakesh (2)", groups[1].title)
        assertEquals(2, groups[1].count)
        assertEquals("demo_wa_1", groups[1].key)
        assertEquals("com.whatsapp", groups[1].pkg)
        assertTrue(groups[1].summaryText.startsWith("2 messages:"))

        assertEquals("Software and Security Updates", groups[2].title)
        assertEquals("com.motorola.ccc.ota", groups[2].pkg)
    }

    @Test fun cardGeometryAndHitTestingReturnsCorrectRowAndButtons() {
        val groups = NotifSummary.group(NotifSummary.demoItems(1_000_000L))
        val card = NotifSummaryCard(density = 3f)
        card.setData(groups, "Good morning, Manik!", "Here's what you missed...", maxWidthPx = 1000)

        assertTrue(card.width > 700f && card.height > 600f)

        // Hit-test Row 0 (File Sharing with Bharath)
        val hit0 = card.hitTest(card.width * 0.5f, 58f * 3f + 25f * 3f)
        assertTrue("expected Row 0 hit, got $hit0", hit0 is NotifSummaryCard.Hit.Row)
        val row0 = hit0 as NotifSummaryCard.Hit.Row
        assertEquals(0, row0.index)
        assertEquals("demo_share_1", row0.group.key)
        assertEquals("com.google.android.gms", row0.group.pkg)

        // Hit-test Row 1 (WhatsApp · Rakesh (2))
        val hit1 = card.hitTest(card.width * 0.5f, 58f * 3f + 58f * 3f + 25f * 3f)
        assertTrue("expected Row 1 hit, got $hit1", hit1 is NotifSummaryCard.Hit.Row)
        val row1 = hit1 as NotifSummaryCard.Hit.Row
        assertEquals(1, row1.index)
        assertEquals("demo_wa_1", row1.group.key)
        assertEquals("com.whatsapp", row1.group.pkg)

        // Hit-test top-right Close '✕' button
        val hitClose = card.hitTest(card.width - 22f * 3f, 24f * 3f)
        assertEquals(NotifSummaryCard.Hit.Close, hitClose)

        // Hit-test footer [✨ AI Recap] button
        val footerY = 58f * 3f + 3 * 58f * 3f + 20f * 3f
        val hitAi = card.hitTest(60f * 3f, footerY)
        assertEquals(NotifSummaryCard.Hit.AiRecap, hitAi)
    }

    @Test fun edgeTabBadgeUnhidePromptAndRowTapFiresOpenNotification() {
        ChatHub.init(ctx)
        CharacterRegistry.select("monkey")
        NotifStore.reset(NotifSummary.demoItems())
        SettaiNotificationListener.lastOpenedForTest = null

        val host = TestHost()
        val v = MonkeyView(ctx, host)
        v.layout(0, 0, 1080, 2340)
        v.debugFrames(30)
        v.debugSurf("FLOOR", frac = 0.5f)
        v.debugFrames(5)

        // 1. Hide Settai to right edge -> drawTab renders unread badge with count 4
        v.debugHide(1)
        v.debugFrames(40)
        assertTrue(v.debugHidden)
        val tabBmp = Bitmap.createBitmap(host.w.coerceAtLeast(40), host.h.coerceAtLeast(80), Bitmap.Config.ARGB_8888)
        v.draw(Canvas(tabBmp))
        assertEquals(4, v.debugTabBadgeCount)

        // 2. Unhide from edge tab -> prompts with [Yes, show me 📬] ("notif_card") chip
        NotifPromptGate.reset()
        v.unhide()
        v.debugFrames(180)
        assertFalse(v.debugHidden)
        assertTrue("bubble has notif_card chip: ${v.debugBubbleChipIds}", "notif_card" in v.debugBubbleChipIds)

        // 3. Open the "What You Missed" summary card -> window expands & card is open
        assertTrue(v.openNotifSummaryCard(useDemoIfEmpty = false))
        assertTrue(v.notifCardOpen)
        assertTrue("window expanded for card: ${host.w}x${host.h}", host.w >= 750 && host.h >= 700)

        // 4. Tap Row 1 (WhatsApp · Rakesh) on the card -> closes card and calls openNotification("demo_wa_1", "com.whatsapp")
        val tapX = v.notifCardLeft + v.notifCard.width * 0.5f
        val tapY = v.notifCardTop + (58f + 58f + 25f) * 3f
        val down = MotionEvent.obtain(1000L, 1000L, MotionEvent.ACTION_DOWN, tapX + host.x, tapY + host.y, 0)
        down.offsetLocation(-host.x.toFloat(), -host.y.toFloat())
        v.dispatchTouchEvent(down)
        down.recycle()

        assertFalse("card closed after row tap", v.notifCardOpen)
        assertEquals("demo_wa_1" to "com.whatsapp", SettaiNotificationListener.lastOpenedForTest)
    }

    @Test fun renderNotifSummaryCardPreview() {
        val W = 1480
        val H = 860
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(0xFF, 0xF3, 0xE0))

        val brown = Color.rgb(0x4A, 0x2C, 0x14)
        val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = brown; textSize = 31f; typeface = Typeface.create("sans-serif", Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        val secP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = brown; textSize = 20f; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        val borderP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0xE6, 0xC9, 0xA3); style = Paint.Style.STROKE; strokeWidth = 2.5f
        }
        val capP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0xE0, 0xE0, 0xF0); textSize = 15f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }

        c.drawText("Settai v8.0 · Compact “What You Missed” Notification Summary Card & Direct contentIntent Open", W / 2f, 42f, titleP)

        val gap = 24f
        val colW = (W - gap * 3) / 2f
        val topY = 66f

        // ---- Left Panel: Edge Tab with Unread Badge + Unhide Prompt Bubble [Yes, show me 📬] ----
        val rLeft = RectF(gap, topY + 26f, gap + colW, H - 22f)
        c.drawText("1. Edge Tab Unread Badge → Tap Tab → “[Yes, show me 📬]” Prompt", rLeft.left + 4f, topY + 18f, secP)
        c.drawRoundRect(rLeft, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x1E, 0x24, 0x33) })
        c.save(); c.clipRect(rLeft)

        // Step A (top-right of left panel): Edge Tab with peeking face + purple unread badge "3"
        val tabBox = RectF(rLeft.right - 72f, rLeft.top + 56f, rLeft.right, rLeft.top + 256f)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 0x3A, 0x26, 0x18) }
        c.drawRoundRect(RectF(tabBox.left, tabBox.top, tabBox.right + 20f, tabBox.bottom), 28f, 28f, tp)
        // Purple unread badge circle on the tab
        val bx = tabBox.centerX(); val by = tabBox.bottom - 44f
        c.drawCircle(bx, by, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x8E, 0x24, 0xAA) })
        c.drawCircle(bx, by, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3.5f })
        c.drawText("3", bx, by + 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER })
        val tabLbl = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xE0, 0x82); textSize = 15f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
        c.drawText("Edge tab shows unread badge (3) ──▶", tabBox.left - 14f, by + 6f, tabLbl)

        // Step B (bottom of left panel): Settai unhidden + CloudBubble asking "want a quick summary?" with [Yes, show me 📬] [Later]
        val fy = rLeft.bottom - 68f
        val rig = MonkeyRig(); val pose = Pose()
        Animations.fill(pose, MonkeyState.WAVE, 1.0f, 1.0f, AnimContext())
        val fx = rLeft.centerX() + 90f
        rig.layout(pose, fx, fy, 1.35f, -1f)
        MonkeyCharacter.newRenderer().also { it.wear = Wear(MonkeyCharacter) }.draw(c, rig, pose, 1.0f)
        val ht = FloatArray(2); rig.headTop(ht)

        val bub = CloudBubble(1.65f, 20f)
        val promptText = "Good morning machan! You have 3 notifications from Quick Share & WhatsApp — want a quick summary?"
        bub.setText(promptText, 460, listOf("Yes, show me 📬", "Later"), reply = false, chips = true)
        bub.draw(c, rLeft.left + 26f, rLeft.top + 270f, ht[0], ht[1], 1f, 1f)

        c.drawText("Tap the edge tab or “Missed?” in menu → tap “Yes, show me 📬”", rLeft.centerX(), rLeft.bottom - 20f, capP)
        c.restore()
        c.drawRoundRect(rLeft, 22f, 22f, borderP)

        // ---- Right Panel: Compact "What You Missed" Dark Card Above Settai (Tapping a row fires contentIntent) ----
        val rRight = RectF(rLeft.right + gap, topY + 26f, W - gap, H - 22f)
        c.drawText("2. Compact “What You Missed” Card → Tap Any Row to Open App Directly", rRight.left + 4f, topY + 18f, secP)
        c.drawRoundRect(rRight, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x22, 0x28, 0x36) })
        c.save(); c.clipRect(rRight)

        val groups = NotifSummary.group(NotifSummary.demoItems())
        val card = NotifSummaryCard(density = 1.68f)
        card.setData(groups, "Good morning, Manik!", "Here's what you missed...", maxWidthPx = (rRight.width() - 48f).toInt())
        val cardLeft = rRight.centerX() - card.width / 2f
        val cardTop = rRight.top + 22f
        card.draw(c, cardLeft, cardTop)

        // Settai perched cheerfully right below the card
        val rig2 = MonkeyRig(); val pose2 = Pose()
        Animations.fill(pose2, MonkeyState.HAPPY_PAT, 1.2f, 1.2f, AnimContext())
        rig2.layout(pose2, rRight.centerX(), fy, 1.22f, 1f)
        MonkeyCharacter.newRenderer().also { it.wear = Wear(MonkeyCharacter) }.draw(c, rig2, pose2, 1.2f)

        c.drawText("Tapping any row fires its Notification.contentIntent (opens exact chat/screen)", rRight.centerX(), rRight.bottom - 20f, capP)
        c.restore()
        c.drawRoundRect(rRight, 22f, 22f, borderP)

        val out = com.combo.monkeycompanion.TestPaths.out("settai-v8-notif-card-preview.png")
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(out.exists() && out.length() > 20_000)
    }
}
