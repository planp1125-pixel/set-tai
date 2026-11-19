package com.combo.monkeycompanion.v1050

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.agent.Voice
import com.combo.monkeycompanion.ai.*
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/** v10.50: the listening bubble shows your words live with a sound meter, and what you said stays while it thinks. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class LiveListeningTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private val views = ArrayList<MonkeyView>()

    private class FakeEngine : Voice.TestEngine {
        var s: Voice.TestSession? = null
        override fun onStart(s: Voice.TestSession) { this.s = s }
    }
    /** Thinks for a while so the thinking bubble can be checked. */
    private class Slow : Brain {
        override val label = "slow"; override val supportsTools = false
        override fun chat(system: String, history: List<ChatMsg>, user: String, tools: ToolHost?, onText: (String) -> Unit): String { Thread.sleep(400); return "Done!" }
    }

    @Before fun clean() { ChatHub.init(ctx); MindHub.init(ctx); MindHub.neighbourMode = "SOLO"; shadowOf(ctx).grantPermissions(Manifest.permission.RECORD_AUDIO); ChatHub.memory.clear() }
    @After fun after() {
        views.forEach { ChatHub.removeListener(it) }; views.clear()
        Voice.cancelListen(); Voice.testEngine = null; ChatHub.testBrain = null
        MindHub.neighbourMode = "DUO"; CharacterRegistry.select("monkey")
    }

    private fun view(): MonkeyView {
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.gags = false; ChatHub.settings.batterySaver = false
        CharacterRegistry.select("devil")
        val v = MonkeyView(ctx, LandingE2ETest.Host()); views.add(v)
        v.debugNoNightCampfire = true
        v.layout(0, 0, 1080, 2340); v.debugFrames(60); v.debugSurf("FLOOR", frac = 0.5f); v.debugFrames(10); v.debugQuiet = true
        return v
    }
    private fun frame(v: MonkeyView, n: Int = 1) { v.debugFrames(n); v.draw(Canvas(bmp)) }

    @Test fun listeningBubbleHasAMeterAndShowsWordsLive() {
        val eng = FakeEngine(); Voice.testEngine = eng
        val v = view(); frame(v, 3)
        v.debugStartListen(); frame(v, 24)   // the meter refreshes every 0.25 s
        val t0 = v.debugBubbleText ?: ""
        assertTrue(t0, t0.startsWith(MonkeyView.LISTEN_PROMPT))
        assertTrue(t0, (v.debugListenMeterLine ?: "").contains("speak now"))
        assertTrue(t0, (v.debugListenMeterLine ?: "").startsWith("▁"))
        eng.s!!.partial("hello my"); frame(v)
        assertTrue(v.debugBubbleText!!, v.debugBubbleText!!.contains("hello my"))
        eng.s!!.partial("hello my friend"); frame(v, 12)   // the meter refresh keeps the newest words
        assertTrue(v.debugBubbleText!!, v.debugBubbleText!!.contains("hello my friend"))
    }

    @Test fun listeningUpdatesInPlaceNotBubbleAfterBubble() {
        val eng = FakeEngine(); Voice.testEngine = eng
        val v = view(); frame(v, 3)
        v.debugStartListen(); frame(v)
        val shows = v.debugShowCount
        repeat(10) { i -> eng.s!!.partial("one two three ${"word ".repeat(i)}"); frame(v, 8) }
        assertEquals("one bubble that updates, not a new pop each time", shows, v.debugShowCount)
        assertTrue(v.debugBubbleText!!, v.debugBubbleText!!.contains("word word word"))
    }

    @Test fun aMissSaysWhyAndOffersGoogleVoice() {
        val eng = FakeEngine(); Voice.testEngine = eng
        val v = view(); frame(v, 3)
        v.debugStartListen(); frame(v)
        eng.s!!.error(Voice.Miss.NO_MATCH); frame(v, 2)
        val t = v.debugBubbleText ?: ""
        assertTrue(t, t.contains("Didn't catch that") && t.contains("(test ·") && t.contains("mic "))
        assertTrue(v.debugBubbleChips.any { it.id == "voice_google" })
    }

    @Test fun micOpensGoogleVoicePopupByDefaultAndVoiceChatCarriesOn() {
        // a phone with Google's voice popup
        val ri = android.content.pm.ResolveInfo().apply { activityInfo = android.content.pm.ActivityInfo().apply { packageName = "com.google.android.googlequicksearchbox"; name = "VoicePopup" } }
        shadowOf(ctx.packageManager).addResolveInfoForIntent(android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH), ri)
        Voice.testEngine = null
        ChatHub.testBrain = object : Brain {
            override val label = "b"; override val supportsTools = false
            override fun chat(system: String, history: List<ChatMsg>, user: String, tools: ToolHost?, onText: (String) -> Unit) = "Sure, here you go!"
        }
        assertFalse("off by default: no popup over the character", ChatHub.settings.voicePopup)
        ChatHub.settings.voicePopup = true
        val v = view(); frame(v, 3)
        v.debugStartListen(); frame(v)
        assertEquals(1, v.debugPopupStarts)
        assertEquals(com.combo.monkeycompanion.ui.GoogleVoiceActivity::class.java.name, shadowOf(ctx).nextStartedActivity.component?.className)
        v.debugPopupHeard("tell me about the mischief from earlier")
        assertEquals("tell me about the mischief from earlier", v.debugLastHeard)
        val end = System.currentTimeMillis() + 10_000
        while (ChatHub.busy && System.currentTimeMillis() < end) { Thread.sleep(10); ShadowLooper.idleMainLooper(); frame(v) }
        ShadowLooper.idleMainLooper(1500, java.util.concurrent.TimeUnit.MILLISECONDS); frame(v, 2)
        assertEquals("voice chat: the popup opens again for your turn", 2, v.debugPopupStarts)
        v.debugPopupHeard(null); frame(v)
        assertFalse(v.debugVoiceChat)
        assertTrue(v.debugBubbleText ?: "", (v.debugBubbleText ?: "").contains("Voice chat paused"))
        ChatHub.settings.voicePopup = false
        v.debugStartListen(); frame(v)
        assertEquals("setting off: the floating mic", 3 - 1, v.debugPopupStarts)
    }

    @Test fun whatYouSaidStaysAboveWhileItThinks() {
        val eng = FakeEngine(); Voice.testEngine = eng
        ChatHub.testBrain = Slow()
        val v = view(); frame(v, 3)
        v.debugStartListen(); frame(v)
        eng.s!!.result("tell me about the mischief from earlier")
        ShadowLooper.idleMainLooper(); frame(v, 2)
        assertTrue(ChatHub.busy)
        assertEquals("tell me about the mischief from earlier", v.debugBubbleQuote)
        val end = System.currentTimeMillis() + 10_000
        while (ChatHub.busy && System.currentTimeMillis() < end) { Thread.sleep(10); ShadowLooper.idleMainLooper(); frame(v) }
        ShadowLooper.idleMainLooper(); frame(v, 2)
    }
}
