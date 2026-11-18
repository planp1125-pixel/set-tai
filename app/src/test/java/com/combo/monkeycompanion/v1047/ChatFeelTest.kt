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
        v.debugSay("Random built-in line!"); frame(v)
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

    @Test fun thinkingShowsTheCharactersOwnWords() {
        val v = view("devil"); frame(v, 3)
        v.onThinking(); frame(v)
        assertTrue(v.debugThinkWord, ThinkingWords.all(CharacterRegistry.current).contains(v.debugThinkWord))
        assertNotEquals("Hmm…", v.debugThinkWord)
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
