package com.combo.monkeycompanion.v1049

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.agent.Voice
import com.combo.monkeycompanion.ai.*
import com.combo.monkeycompanion.brain.Superpowers
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.landing.LandingE2ETest
import com.combo.monkeycompanion.mind.MindHub
import com.combo.monkeycompanion.overlay.MonkeyView
import com.combo.monkeycompanion.speech.CloudBubble
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
import java.util.concurrent.TimeUnit

/** v10.49: the last chats faded + scrollable in the bubble, hands-free voice chat, and fly / power always happen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ChatMemoryVoiceTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    @Before fun clean() {
        ChatHub.init(ctx); MindHub.init(ctx); MindHub.neighbourMode = "SOLO"
        shadowOf(ctx).grantPermissions(Manifest.permission.RECORD_AUDIO)
        ChatHub.memory.clear()
    }
    @After fun after() {
        views.forEach { ChatHub.removeListener(it) }; views.clear()
        Voice.cancelListen(); Voice.testEngine = null; ChatHub.testBrain = null
        MindHub.neighbourMode = "DUO"; CharacterRegistry.select("monkey"); Superpowers.plainFly = false
    }

    private fun view(id: String = "devil"): MonkeyView {
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.gags = false; ChatHub.settings.batterySaver = false
        CharacterRegistry.select(id)
        val v = MonkeyView(ctx, LandingE2ETest.Host())
        views.add(v)
        v.attachReplyField(android.widget.EditText(ctx).apply { layoutParams = android.widget.FrameLayout.LayoutParams(1, 1) })
        v.debugNoNightCampfire = true
        v.layout(0, 0, 1080, 2340)
        v.debugFrames(60); v.debugSurf("FLOOR", frac = 0.5f); v.debugFrames(10)
        v.debugQuiet = true
        return v
    }
    private val views = ArrayList<MonkeyView>()
    private fun frame(v: MonkeyView, n: Int = 1) { v.debugFrames(n); v.draw(Canvas(bmp)) }

    private class Says(val answer: String) : Brain {
        override val label = "says"; override val supportsTools = false
        override fun chat(system: String, history: List<ChatMsg>, user: String, tools: ToolHost?, onText: (String) -> Unit): String { onText(answer); return answer }
    }
    private fun waitIdle(v: MonkeyView? = null) {
        val end = System.currentTimeMillis() + 10_000
        while (ChatHub.busy && System.currentTimeMillis() < end) { Thread.sleep(10); ShadowLooper.idleMainLooper(); v?.let { frame(it) } }
        ShadowLooper.idleMainLooper(); v?.let { frame(it, 2) }
    }
    private fun twoOldChats() {
        ChatHub.memory.add(ChatMsg(true, "what's your favourite food?")); ChatHub.memory.add(ChatMsg(false, "Bananas, obviously!"))
        ChatHub.memory.add(ChatMsg(true, "and drink?")); ChatHub.memory.add(ChatMsg(false, "Coconut water, boss."))
        ChatHub.memory.add(ChatMsg(true, "older one?")); ChatHub.memory.add(ChatMsg(false, "should not show"))
        ChatHub.memory.add(ChatMsg(true, "really old"))
    }

    // ---------------- 1. the last chats, faded, scrollable ----------------

    @Test fun openingTheBubbleChatShowsTheLastTwoChatsFaded() {
        twoOldChats(); ChatHub.memory.add(ChatMsg(false, "Old answer"))
        val v = view(); frame(v, 3)
        v.openBubbleChat(); frame(v, 2)
        val h = v.debugBubbleHistory
        assertEquals("v10.51: the whole saved chat", 8, h.size)
        assertEquals(true to "what's your favourite food?", h[0]); assertEquals(false to "Old answer", h[7])
        // it starts at the bottom (now), with the faded chats above to drag down to
        val (sy, max) = v.debugBubbleScroll
        assertTrue(max > 0f); assertEquals(max, sy, 0.5f)
        assertTrue(v.debugBubbleScrollBy(-10_000f))
        assertEquals(0f, v.debugBubbleScroll.first, 0.5f)
        v.endTyping()
    }

    @Test fun aReplyShowsTheChatsBeforeItAndScrollsInsideTheBubble() {
        twoOldChats()
        val long = (1..60).joinToString(" ") { "word$it" }
        ChatHub.testBrain = Says(long)
        val v = view(); frame(v, 3)
        ChatHub.send("tell me about the mischief from earlier"); waitIdle(v)
        assertEquals(long, v.debugBubbleText)                      // the whole reply, one bubble (no pages)
        assertEquals("tell me about the mischief from earlier", v.debugBubbleQuote)
        val h = v.debugBubbleHistory
        assertEquals(7, h.size)
        assertEquals(listOf(true to "what's your favourite food?", false to "Bananas, obviously!", true to "and drink?",
            false to "Coconut water, boss.", true to "older one?", false to "should not show", true to "really old"), h)
        assertFalse("never the current message in the faded part", h.any { it.second == "tell me about the mischief from earlier" })
        // starts at the top of your message + reply; drag up reads on, drag down shows the faded chats
        val (sy, max) = v.debugBubbleScroll
        assertTrue(sy > 0f && sy < max)
        assertTrue(v.debugBubbleScrollBy(10_000f)); assertEquals(max, v.debugBubbleScroll.first, 0.5f)
        assertTrue(v.debugBubbleScrollBy(-10_000f)); assertEquals(0f, v.debugBubbleScroll.first, 0.5f)
    }

    @Test fun bubbleWithoutHistoryIsUnchanged() {
        val b = CloudBubble(3f, 42f)
        b.setText("Hello there", 700)
        assertFalse(b.scrollable); assertFalse(b.hasHistory)
        val h0 = b.height
        b.setText("Hello there", 700, history = listOf(true to "hi", false to "hey!"), maxViewH = 2000f)
        assertTrue(b.hasHistory); assertTrue(b.height > h0)
        val bm = Bitmap.createBitmap(1400, 1400, Bitmap.Config.ARGB_8888)
        b.draw(Canvas(bm), 50f, 50f, 600f, 1300f, 1f, 1f)
        b.setText("Hello there", 700, history = (1..8).map { (it % 2 == 0) to "line $it with some words in it" }, maxViewH = 300f)
        assertTrue(b.scrollable); assertTrue(b.historyShown < 1f)
        b.scrollBy(-10_000f); assertEquals(1f, b.historyShown, 0.01f)
        b.draw(Canvas(bm), 50f, 50f, 600f, 1300f, 1f, 1f)
    }

    // ---------------- 2. voice chat ----------------

    private class FakeEngine(val finalOnStop: String?) : Voice.TestEngine {
        var s: Voice.TestSession? = null; var starts = 0; var stops = 0
        override fun onStart(s: Voice.TestSession) { this.s = s; starts++ }
        override fun onStop() { stops++; finalOnStop?.let { s?.result(it) } }
    }

    @Test fun itSendsByItselfWhenYouStopTalking() {
        val eng = FakeEngine("hello there buddy"); Voice.testEngine = eng
        ChatHub.testBrain = Says("Hi!")
        val v = view(); frame(v, 3)
        v.debugStartListen(); frame(v)
        eng.s!!.partial("hello there buddy"); frame(v)
        assertTrue(v.debugBubbleText!!, v.debugBubbleText!!.contains("hello there buddy"))
        ShadowLooper.idleMainLooper(Voice.AUTO_SEND_MS + 200, TimeUnit.MILLISECONDS)
        assertEquals(1, eng.stops)
        assertEquals("hello there buddy", v.debugLastHeard)
        waitIdle(v)
    }

    @Test fun voiceChatListensAgainAfterTheAnswerAndPausesWhenYouAreQuiet() {
        val eng = FakeEngine(null); Voice.testEngine = eng
        ChatHub.testBrain = Says("Oh that was a wild one, let me tell you!")
        val v = view(); frame(v, 3)
        v.debugStartListen(); frame(v)
        assertTrue(v.debugVoiceChat)
        eng.s!!.result("tell me about the mischief from earlier")
        waitIdle(v)
        ShadowLooper.idleMainLooper(1500, TimeUnit.MILLISECONDS); frame(v, 2)
        assertEquals("listens again by itself", 2, eng.starts)
        assertTrue(Voice.listening); assertTrue(v.debugListenTap)
        assertTrue(v.debugBubbleText!!, v.debugBubbleText!!.contains("Oh that was a wild one") && v.debugBubbleText!!.contains("Your turn"))
        // you say nothing: the call pauses, the answer stays on screen
        eng.s!!.error(Voice.Miss.NO_MATCH); frame(v, 2)
        assertFalse(v.debugVoiceChat)
        assertTrue(v.debugBubbleText!!, v.debugBubbleText!!.contains("Oh that was a wild one") && v.debugBubbleText!!.contains("Voice chat paused"))
        ShadowLooper.idleMainLooper(3000, TimeUnit.MILLISECONDS)
        assertEquals("no more listening after the pause", 2, eng.starts)
    }

    @Test fun typingEndsTheVoiceChat() {
        val eng = FakeEngine(null); Voice.testEngine = eng
        val v = view(); frame(v, 3)
        v.debugStartListen(); frame(v)
        assertTrue(v.debugVoiceChat)
        Voice.cancelListen(); v.openBubbleChat(); frame(v)
        assertFalse(v.debugVoiceChat)
        v.endTyping()
    }

    // ---------------- 3. fly / power always happen ----------------

    private fun actionsFor(said: String, brainSays: String): List<BodyAction> {
        val got = ArrayList<BodyAction>()
        val l = object : ChatHub.Listener { override fun onAction(action: BodyAction) { got.add(action) } }
        ChatHub.addListener(l)
        try {
            ChatHub.testBrain = Says(brainSays)
            ChatHub.send(said); waitIdle()
        } finally { ChatHub.removeListener(l) }
        return got
    }

    @Test fun flyAndPowerHappenEvenWhenTheAiPicksAnotherMove() {
        CharacterRegistry.select("armour")
        assertEquals(listOf(BodyAction.FLY), actionsFor("fly", "[dance] Up, up and away!"))
        assertEquals(listOf(BodyAction.FLY), actionsFor("power", "Watch this!"))
        assertEquals(listOf(BodyAction.FLY), actionsFor("Bolt use your power", "Here goes!"))
    }

    @Test fun boltFliesWhenAsked() {
        val v = view("armour"); frame(v, 3)
        Superpowers.plainFly = true
        v.onAction(BodyAction.FLY)
        frame(v, 30)
        assertTrue("Bolt takes off", v.debugFalling || v.debugLandStyle.toString() == "ROCKET_ARC")
    }
}

class BubbleHistoryCapTest {
    @Test fun capIsAHundred() { assertEquals(100, MonkeyView.PAST_MAX) }
}
