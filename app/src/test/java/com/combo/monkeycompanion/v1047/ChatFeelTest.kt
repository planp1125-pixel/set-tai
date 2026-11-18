package com.combo.monkeycompanion.v1047

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.ai.ChatMsg
import com.combo.monkeycompanion.ai.Persona
import com.combo.monkeycompanion.brain.Superpowers
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.landing.LandingE2ETest
import com.combo.monkeycompanion.mind.MindHub
import com.combo.monkeycompanion.overlay.MonkeyView
import com.combo.monkeycompanion.speech.CloudBubble
import com.combo.monkeycompanion.speech.ThinkingWords
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/** v10.47: quiet while chatting, the user's message above the reply, thinking words, plain "fly", friend-length replies. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ChatFeelTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    @Before fun clean() { ChatHub.init(ctx); MindHub.init(ctx); MindHub.neighbourMode = "SOLO"; Superpowers.plainFly = false }
    @After fun after() { MindHub.neighbourMode = "DUO"; CharacterRegistry.select("monkey"); Superpowers.plainFly = false }

    private fun view(id: String = "devil"): MonkeyView {
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.gags = false
        ChatHub.settings.batterySaver = false
        CharacterRegistry.select(id)
        val v = MonkeyView(ctx, LandingE2ETest.Host())
        v.debugNoNightCampfire = true
        v.layout(0, 0, 1080, 2340)
        v.debugFrames(60)
        v.debugSurf("FLOOR", frac = 0.5f)
        v.debugFrames(10)
        return v
    }
    private fun frame(v: MonkeyView, n: Int = 1) { v.debugFrames(n); v.draw(Canvas(bmp)) }

    @Test fun builtInLinesStayQuietDuringAndAfterAChat() {
        val v = view(); frame(v, 3)
        ChatHub.memory.add(ChatMsg(true, "how was your day?"))
        v.onThinking(); frame(v)
        assertTrue(v.debugChatting)
        v.onReply("Busy plotting, as always. And yours?", true); frame(v, 2)
        assertEquals("Busy plotting, as always. And yours?", v.debugBubbleText)
        // a built-in line now must not replace the reply
        v.debugSpeak("Random built-in line!"); frame(v)
        assertEquals("Busy plotting, as always. And yours?", v.debugBubbleText)
        assertTrue(v.debugChatting)
        // two minutes later the character may talk on its own again
        ShadowSystemClock.advanceBy(Duration.ofMillis(MonkeyView.CHAT_QUIET_MS + 1000))
        assertFalse(v.debugChatting)
    }

    @Test fun replyBubbleShowsTheUsersMessageAbove() {
        val v = view(); frame(v, 3)
        ChatHub.memory.add(ChatMsg(true, "tell me a joke"))
        v.onThinking(); frame(v)
        v.onReply("Why did the devil cross the road? To stir trouble on the other side!", true); frame(v, 2)
        assertTrue(v.debugBubbleQuoted)
    }

    @Test fun thinkingShowsOneOfTheCharactersOwnWords() {
        val v = view("devil"); frame(v, 3)
        v.onThinking(); frame(v)
        val w = v.debugThinkWord
        assertTrue(w, ThinkingWords.all(CharacterRegistry.current).contains(w))
        assertNotEquals("Hmm…", w)
        // v10.47.1: one word per question, it does not keep changing
        frame(v, 240)
        assertEquals(w, v.debugThinkWord)
        assertEquals(w, v.debugBubbleText)
    }

    /** A brain that streams its answer in pieces, like ChatGPT. */
    private class StreamBrain(val answer: String) : com.combo.monkeycompanion.ai.Brain {
        override val label = "test"
        override val supportsTools = false
        override fun cancel() {}
        override fun chat(system: String, history: List<ChatMsg>, user: String, tools: com.combo.monkeycompanion.ai.ToolHost?, onText: (String) -> Unit): String {
            var shown = ""
            for (w in answer.split(" ")) { shown = if (shown.isEmpty()) w else "$shown $w"; onText(shown); Thread.sleep(2) }
            return answer
        }
    }

    private fun waitReply(v: MonkeyView) {
        val end = System.currentTimeMillis() + 10_000
        while ((ChatHub.busy || v.debugBubbleText == null || v.debugThinkWord == v.debugBubbleText) && System.currentTimeMillis() < end) {
            Thread.sleep(20); org.robolectric.shadows.ShadowLooper.idleMainLooper(); frame(v)
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper(); frame(v, 2)
    }

    @Test fun realChatKeepsYourMessageAboveEveryPageOfTheReply() {
        val long = "Oh I love that question! Honestly my day was a whirlwind of mischief and snacks. I hid the remote, " +
            "swapped the sugar for salt, and then pretended to be very innocent when everyone looked at me. " +
            "But enough about my crimes. What did you do today? Anything fun, or was it all meetings and boring stuff?"
        ChatHub.testBrain = StreamBrain(long)
        try {
            ChatHub.settings.aiBrainEnabled = true
            val v = view(); frame(v, 3)
            ChatHub.send("tell me about the mischief from earlier")
            waitReply(v)
            assertFalse(ChatHub.busy)
            assertEquals("tell me about the mischief from earlier", v.debugBubbleQuote)
            assertTrue(v.debugBubbleText!!, v.debugBubbleText!!.startsWith("Oh I love that question"))
            // keep watching for 8 seconds: pages turn, the quote stays, nothing else replaces the reply
            repeat(8) {
                frame(v, 60)
                v.debugBubbleText?.let { t -> assertTrue(t, long.contains(t.removeSuffix(" …").trim())); assertEquals("tell me about the mischief from earlier", v.debugBubbleQuote) }
            }
        } finally { ChatHub.testBrain = null }
    }

    @Test fun longPressPastesIntoTheBubbleField() {
        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
        cm.setPrimaryClip(android.content.ClipData.newPlainText("x", "pasted\ntext"))
        val e = com.combo.monkeycompanion.overlay.ReplyEditText(ctx) { false }
        e.setText("hi "); e.setSelection(3)
        assertTrue(com.combo.monkeycompanion.overlay.pasteClip(e))
        assertEquals("hi pasted text", e.text.toString())
        cm.setPrimaryClip(android.content.ClipData.newPlainText("x", "  "))
        assertFalse(com.combo.monkeycompanion.overlay.pasteClip(e))
    }

    @Test fun everyCharacterHasThinkingWords() {
        for (c in CharacterRegistry.all) {
            val words = ThinkingWords.all(c)
            assertTrue(c.id, words.size >= 3)
            for (w in words) assertTrue("${c.id}: $w", w.length in 3..40)
            assertEquals(words[1], ThinkingWords.pick(c, 1))
        }
    }

    @Test fun flyMeansFlyAndPowerWordsMeanPower() {
        for (s in listOf("fly", "Fly around!", "can you fly?", "take off", "go flying"))
            assertTrue(s, Superpowers.isPlainFly(s))
        for (s in listOf("use your power", "fly and use your laser", "fire your superpower", "hello there", "do some magic"))
            assertFalse(s, Superpowers.isPlainFly(s))
    }

    @Test fun replyLengthIsFriendlyButCloudStaysShort() {
        val long = Persona.systemPrompt(false, longReplies = true)
        assertTrue(long.contains("40-80 words"))
        assertTrue(long.contains("150 words"))
        assertTrue(long.contains("Never write long essays"))
        val short = Persona.systemPrompt(false)
        assertTrue(short.contains("30-40 words"))
        assertFalse(short.contains("40-80 words"))
    }

    @Test fun quotedBubbleDrawsAndIsTaller() {
        val b = CloudBubble(3f, 42f)
        b.setText("Sure thing, buddy!", 700, emptyList(), false)
        val plain = b.height
        b.setText("Sure thing, buddy!", 700, emptyList(), false, quote = "can you help me with something?")
        assertTrue(b.height > plain)
        val bm = Bitmap.createBitmap(1200, 1200, Bitmap.Config.ARGB_8888)
        b.draw(Canvas(bm), 50f, 50f, 600f, 1100f, 1f, 1f)
    }
}
