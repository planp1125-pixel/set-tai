package com.combo.monkeycompanion.v1029

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.agent.ActivityScheduler
import com.combo.monkeycompanion.agent.QuickChip
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.ai.Persona
import com.combo.monkeycompanion.brain.FloatButton
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.landing.LandingE2ETest
import com.combo.monkeycompanion.mind.MindHub
import com.combo.monkeycompanion.overlay.MonkeyView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/** v10.29: dual faint FABs (💬 left, 🎤 right) + Explain-more chip phrasing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class V1029Test {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private val faint = FloatButton.IDLE_ALPHA

    @Before fun clean() {
        ChatHub.init(ctx); MindHub.init(ctx); ActivityScheduler.resetForTest(ctx)
        MindHub.focusBuddy.stop(); MindHub.campfire.stop(); MindHub.neighbourMode = "SOLO"
    }
    @After fun after() {
        MindHub.focusBuddy.stop(); MindHub.campfire.stop()
        ActivityScheduler.resetForTest(ctx); MindHub.neighbourMode = "DUO"
    }

    private fun view(id: String = "monkey", frac: Float = 0.5f, chatBtn: Boolean = true): MonkeyView {
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.gags = false
        ChatHub.settings.livingReactions = true
        ChatHub.settings.batterySaver = false
        ChatHub.settings.doubleTapPower = true
        ChatHub.settings.chatButton = chatBtn
        CharacterRegistry.select(id)
        val v = MonkeyView(ctx, LandingE2ETest.Host())
        v.debugNoNightCampfire = true
        v.layout(0, 0, 1080, 2340)
        v.debugFrames(60)
        v.debugSurf("FLOOR", frac = frac)
        v.debugFrames(10)
        v.debugQuiet = true
        return v
    }

    private var clock = 80_000L
    private fun send(v: MonkeyView, a: Int, rx: Float, ry: Float) {
        val w = v.debugWin()
        val e = MotionEvent.obtain(clock, clock, a, rx, ry, 0)
        e.offsetLocation(-w[0].toFloat(), -w[1].toFloat())
        v.dispatchTouchEvent(e); e.recycle()
    }
    private fun advance(ms: Long) { ShadowSystemClock.advanceBy(Duration.ofMillis(ms)); clock += ms }
    private fun frame(v: MonkeyView, n: Int = 1) { v.debugFrames(n); v.draw(Canvas(bmp)) }

    @Test fun bothFabsVisibleFaintAndOnCorrectSides() {
        val v = view("monkey", 0.5f)
        frame(v, 5)
        assertTrue(v.debugChatFabVisible)
        assertTrue(v.debugMicFabVisible)
        assertEquals(faint, v.debugChatFabAlpha, 0.001f)
        assertEquals(faint, v.debugMicFabAlpha, 0.001f)
        val w = v.debugWin()
        val chat = FloatArray(2); v.debugChatFab(chat)
        val mic = FloatArray(2); v.debugMicFab(mic)
        // debugChatFab/MicFab are view-local; debugBox is screen — compare in screen space
        val chatSx = chat[0] + w[0]; val micSx = mic[0] + w[0]
        val box = v.debugBox()
        assertTrue("chat left of body ($chatSx vs ${box.centerX()})", chatSx < box.centerX())
        assertTrue("mic right of body ($micSx vs ${box.centerX()})", micSx > box.centerX())
        assertTrue("not overlapping", kotlin.math.hypot((mic[0] - chat[0]).toDouble(), (mic[1] - chat[1]).toDouble()) > v.debugChatFabRadius)
    }

    @Test fun chatButtonSettingHidesBoth() {
        val v = view(chatBtn = false)
        frame(v, 5)
        assertFalse(v.debugChatFabVisible)
        assertFalse(v.debugMicFabVisible)
    }

    @Test fun eachFabSolidsOnlyWhileThatOneIsPressed() {
        val v = view()
        frame(v, 5)
        val chat = FloatArray(2); v.debugChatFab(chat); val w = v.debugWin()
        send(v, MotionEvent.ACTION_DOWN, chat[0] + w[0], chat[1] + w[1]); frame(v, 2)
        assertTrue(v.debugChatFabPressed)
        assertFalse(v.debugMicFabPressed)
        assertEquals(1f, v.debugChatFabAlpha, 0.001f)
        assertEquals(faint, v.debugMicFabAlpha, 0.001f)
        advance(60); send(v, MotionEvent.ACTION_UP, chat[0] + w[0], chat[1] + w[1])
        assertEquals(1, v.debugChatOpened)
        assertFalse(v.debugChatFabPressed)

        frame(v, 5)
        val mic = FloatArray(2); v.debugMicFab(mic)
        send(v, MotionEvent.ACTION_DOWN, mic[0] + w[0], mic[1] + w[1]); frame(v, 2)
        assertTrue(v.debugMicFabPressed)
        assertFalse(v.debugChatFabPressed)
        assertEquals(1f, v.debugMicFabAlpha, 0.001f)
        assertEquals(faint, v.debugChatFabAlpha, 0.001f)
        send(v, MotionEvent.ACTION_CANCEL, mic[0] + w[0], mic[1] + w[1]); frame(v)
        assertFalse(v.debugMicFabPressed)
        assertEquals(faint, v.debugMicFabAlpha, 0.001f)
    }

    @Test fun explainMoreChipMatchesWorkerPhraseAndPersonaLengthGuidance() {
        assertEquals("say", QuickChip.EXPLAIN_MORE.id)
        assertEquals("Please explain that more fully", QuickChip.EXPLAIN_MORE.arg)
        assertTrue(QuickChip.EXPLAIN_MORE.label.contains("Explain", ignoreCase = true))
        val prompt = Persona.systemPrompt(false)
        assertTrue(prompt.contains("30-40 words"))
        assertTrue(prompt.contains("90 words"))
        assertFalse(prompt.contains("under 35 words"))
    }
}
