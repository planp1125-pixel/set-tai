package com.combo.monkeycompanion.v1051

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.ai.ChatMsg
import com.combo.monkeycompanion.ai.Persona
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.Superpowers
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.landing.LandingE2ETest
import com.combo.monkeycompanion.mind.MindHub
import com.combo.monkeycompanion.overlay.MonkeyView
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.speech.SpeechPacer
import com.combo.monkeycompanion.speech.Spoken
import com.combo.monkeycompanion.speech.TalkGesture
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v10.51 human pace: replies come out word by word, the mouth follows the letters, the hands follow the sentence. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class HumanPaceTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    @Before fun clean() { ChatHub.init(ctx); MindHub.init(ctx); MindHub.neighbourMode = "SOLO"; Superpowers.plainFly = false }
    @After fun after() { MindHub.neighbourMode = "DUO"; CharacterRegistry.select("monkey") }

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

    // ---------------- the pacer on its own ----------------

    private fun run(p: SpeechPacer, secs: Float): Int { var n = 0; repeat((secs * 60).toInt()) { if (p.step(1f / 60f)) n++ }; return n }

    @Test fun wordsComeOutOneAtATimeAtTheSpeakingPace() {
        val p = SpeechPacer(); p.wordsPerSec = 5f
        p.feed("one two three four five six seven eight nine ten", false)
        assertTrue(p.step(0f)); assertEquals("one", p.visible)
        val n = run(p, 1f)
        assertTrue("about 5 a second, got $n", n in 3..6)
        assertFalse("the last word may still be arriving", p.visible.endsWith("ten"))
        p.feed("one two three four five six seven eight nine ten", true)
        run(p, 3f)
        assertEquals("one two three four five six seven eight nine ten", p.visible)
        assertTrue(p.finished)
    }

    @Test fun aFullStopPausesLongerThanACommaAndBothLongerThanAWord() {
        fun gapAfter(text: String): Float {
            val p = SpeechPacer(); p.wordsPerSec = 5f
            p.feed(text, true); p.step(0f)
            var t = 0f
            while (!p.step(1f / 120f)) t += 1f / 120f
            return t
        }
        val plain = gapAfter("word next")
        val comma = gapAfter("word, next")
        val stop = gapAfter("word. next")
        assertTrue("$plain < $comma", comma > plain + 0.15f)
        assertTrue("$comma < $stop", stop > comma + 0.15f)
    }

    @Test fun theMouthOpensOnVowelsShutsOnMbpAndClosesInPauses() {
        assertTrue(SpeechPacer.open('a', 0) > 0.8f)
        assertTrue(SpeechPacer.open('m', 0) < 0.1f)
        assertTrue(SpeechPacer.open('b', 0) < 0.1f)
        assertTrue(SpeechPacer.open('வ', 0) != SpeechPacer.open('வ', 1))   // Tamil: syllable rhythm
        val p = SpeechPacer(); p.wordsPerSec = 5f
        p.feed("Mama. Next", true); p.step(0f)
        val seen = ArrayList<Float>()
        repeat(14) { p.step(1f / 60f); seen += p.mouth() }
        assertTrue(seen.toString(), seen.any { it > 0.6f } && seen.any { it < 0.15f })
        repeat(20) { p.step(1f / 60f) }
        assertEquals("pause after the full stop", 0f, p.mouth(), 0.001f)
    }

    @Test fun skipShowsEverythingAndTheGateWaitsForTheVoice() {
        val p = SpeechPacer()
        p.feed("Hello there my friend", true)
        p.gate = 0
        assertFalse("nothing before the voice starts", p.step(1f))
        p.gate = 6
        assertTrue(p.step(1f)); assertEquals("Hello", p.visible)
        assertFalse("'there' starts at 6", p.step(1f))
        p.skip()
        assertEquals("Hello there my friend", p.visible)
    }

    @Test fun aRewrittenStartCarriesOnFromWhatIsShared() {
        val p = SpeechPacer()
        p.feed("Hi **friend** how", false); p.step(0f); run(p, 1f)
        p.feed("Hi friend how are you", true)
        run(p, 3f)
        assertEquals("Hi friend how are you", p.visible)
    }

    @Test fun gesturesFollowTheSentence() {
        assertEquals(TalkGesture.ASK, Spoken.gesture("What did you eat today?", "eat"))
        assertEquals(TalkGesture.EXCLAIM, Spoken.gesture("That is amazing!", "amazing"))
        assertEquals(TalkGesture.LAUGH, Spoken.gesture("haha you got me", "haha"))
        assertEquals(TalkGesture.THINK, Spoken.gesture("Hmm, let me see", "Hmm,"))
        assertEquals(TalkGesture.SAD, Spoken.gesture("I'm so sorry you feel that way", "sorry"))
        assertEquals(TalkGesture.SELF, Spoken.gesture("I hid the remote", "hid"))
        assertEquals(TalkGesture.EXPLAIN, Spoken.gesture("The sun rises in the east", "sun"))
        // the body moves toward the gesture
        val p = Pose(); val before = p.armRRaise
        Spoken.body(p, TalkGesture.EXCLAIM, 1f, 0f)
        assertTrue(p.armRRaise > before + 60f)
    }

    @Test fun markdownBecomesPlainSpeech() {
        assertEquals("Here are tips\nDrink water\nSleep early", Spoken.plain("## Here are tips\n- **Drink** water\n* Sleep early"))
        assertEquals("1, Open the app", Spoken.plain("1. Open the app"))
    }

    @Test fun theChatGptPromptAsksForSpeechAndTheOddFiller() {
        val on = Persona.systemPrompt(false, longReplies = true, spoken = true)
        val off = Persona.systemPrompt(false, longReplies = true)
        assertTrue(on.contains("filler")); assertTrue(on.contains("seri seri"))
        assertFalse(off.contains("filler"))
    }

    // ---------------- the character ----------------

    @Test fun aStreamedReplyIsSaidWordByWordWithTheMouthMoving() {
        val v = view(); frame(v, 3)
        ChatHub.settings.humanPace = true
        ChatHub.settings.talkSpeed = 5f
        ChatHub.memory.add(ChatMsg(true, "how was your day?"))
        v.onThinking(); frame(v)
        val full = "Oh what a day! I hid the remote, then acted innocent. How was yours?"
        // the network delivers it in two big bursts
        v.onReply("Oh what a day! I hid the remote,", false); frame(v)
        assertTrue(v.debugPacing)
        assertEquals("Oh", v.debugBubbleText)
        frame(v, 20)
        val mid = v.debugBubbleText ?: ""
        assertTrue("a few words, not the whole burst: $mid", mid.startsWith("Oh what") && mid.length < "Oh what a day! I hid the remote,".length)
        assertEquals(MonkeyState.TALKING, v.controller.state)
        v.onReply(full, true)
        var maxMouth = 0f; var minMouth = 1f; var gestures = HashSet<TalkGesture>()
        var n = 0
        while (v.debugPacing && n < 60 * 30) { frame(v); maxMouth = maxOf(maxMouth, v.debugTalkMouth); minMouth = minOf(minMouth, v.debugTalkMouth); gestures += v.debugTalkGesture; n++ }
        assertFalse("finished within 30 s", v.debugPacing)
        assertEquals(full, v.debugBubbleText)
        assertTrue("mouth opened ($maxMouth) and closed ($minMouth)", maxMouth > 0.4f && minMouth < 0.1f)
        assertTrue("hands changed with the sentences: $gestures", gestures.size >= 2)
        assertTrue("took a few seconds (${n / 60f})", n > 60 * 2)
        assertEquals("what you said stays above", "how was your day?", v.debugBubbleQuote)
    }

    @Test fun tappingSkipsToTheEndAndANewQuestionStopsTheOldReply() {
        val v = view(); frame(v, 3)
        ChatHub.settings.humanPace = true
        v.onThinking(); frame(v)
        val long = (1..80).joinToString(" ") { "word$it" }
        v.onReply(long, false); frame(v, 5)
        v.onReply(long, true)
        v.debugPacer.skip(); frame(v, 3)
        assertFalse(v.debugPacing)
        assertEquals(long, v.debugBubbleText)
        // a new question while a reply is being said: the old one stops
        v.onThinking(); frame(v)
        v.onReply("New answer coming", false); frame(v)
        v.onThinking(); frame(v)
        assertFalse(v.debugPacing)
    }

    @Test fun humanPaceOffShowsTheReplyAtOnce() {
        val v = view(); frame(v, 3)
        ChatHub.settings.humanPace = false
        v.onThinking(); frame(v)
        v.onReply("All of this at once please", false); frame(v)
        assertFalse(v.debugPacing)
        assertEquals("All of this at once please", v.debugBubbleText)
    }
}
