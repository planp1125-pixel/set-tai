package com.combo.monkeycompanion.react

import com.combo.monkeycompanion.ai.react.LineHistory
import com.combo.monkeycompanion.ai.react.MoodKey
import com.combo.monkeycompanion.ai.react.ReactKind
import com.combo.monkeycompanion.ai.react.ReactionEngine
import com.combo.monkeycompanion.ai.react.ReactionPrompts
import com.combo.monkeycompanion.ai.react.Strength
import com.combo.monkeycompanion.ai.react.TouchFeel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v10.18 "Feel the touch" reaction engine (pure). */
class ReactionEngineTest {
    private val key = MoodKey.of(hour = 21, grumpy = 0f, sleepy = false, energy = 0.9f, friendLevel = 3)

    @Test fun classifiesTouches() {
        assertEquals(ReactKind.BAD, ReactKind.classify("PRIVATE", 0.2f, hold = false, asleep = true))
        assertEquals(ReactKind.WAKE, ReactKind.classify("HEAD", 0.2f, hold = false, asleep = true))
        assertEquals(ReactKind.HOLD, ReactKind.classify("BELLY", 0.2f, hold = true, asleep = false))
        assertEquals(ReactKind.TAIL, ReactKind.classify("TAIL", 0.9f, hold = false, asleep = false))
        assertEquals(ReactKind.PAT, ReactKind.classify("HEAD", 0.3f, hold = false, asleep = false))
        assertEquals(ReactKind.POKE, ReactKind.classify("HEAD", 0.9f, hold = false, asleep = false))
        assertEquals(ReactKind.TICKLE, ReactKind.classify("BELLY", 0.3f, hold = false, asleep = false))
        assertEquals(ReactKind.POKE, ReactKind.classify("FOOT", 0.3f, hold = false, asleep = false))
    }

    @Test fun describesTheTouchInPlainWords() {
        val d = TouchFeel(ReactKind.TICKLE, "belly", Strength.GENTLE, streak = 3, doing = "half asleep").describe()
        assertEquals("Your belly was just tickled gently, 3 times in a row, while you were half asleep.", d)
        val t = TouchFeel(ReactKind.THROW, extra = "flung up-left").describe()
        assertEquals("You were just thrown across the screen (flung up-left).", t)
    }

    @Test fun historyCatchesRepeatsAndNearRepeats() {
        val h = LineHistory()
        h.add("Hehe stop, that tickles! 😆")
        assertFalse(h.isFresh("hehe STOP that tickles"))
        assertFalse("same opening", h.isFresh("Hehe, stop that, you rascal!"))
        assertTrue(h.isFresh("Oi! My belly is not a drum, machan 🥁"))
        assertFalse("duplicate inside one batch", h.isFresh("Not a drum!", listOf("not a drum")))
        assertFalse("empty / emoji only", h.isFresh("😆😆"))
    }

    @Test fun parsesRefillAnswersTolerantly() {
        val ans = """
            Sure! Here you go:
            TICKLE | Hehe— not the belly, I'm ticklish! [giggle]
            2. PAT: Aww, head pats? Fine, you may continue 😌
            - **POKE** – Ow! Do I look like a doorbell?
            THROW | "Wheee— wait, where's the floor?!"
            DANCE | not a kind
            Note: hope you like them
        """.trimIndent()
        val rows = ReactionPrompts.parseRefill(ans, ReactKind.values().toList())
        assertEquals(listOf(ReactKind.TICKLE, ReactKind.PAT, ReactKind.POKE, ReactKind.THROW), rows.map { it.first })
        assertEquals("Hehe— not the belly, I'm ticklish!", rows[0].second)   // action tag stripped for the bank
        assertEquals("Wheee— wait, where's the floor?!", rows[3].second)      // quotes stripped
    }

    @Test fun bankGivesInstantLinesThatNeverRepeat() {
        val e = ReactionEngine()
        val t0 = 1_000_000L
        val req = e.refillRequest(key, t0)
        assertNotNull(req)
        assertTrue(req!!.contains("|"))
        val kept = e.acceptRefill("POKE | Ow, do I look like a doorbell?\nPOKE | Poke me again and I'll poke back 😈\nPOKE | Ow, do I look like a doorbell?!", t0 + 5_000)
        assertEquals("the duplicate is dropped", 2, kept)

        val d1 = e.onTouch(TouchFeel(ReactKind.POKE, "head", Strength.FIRM), key, t0 + 10_000)
        val d2 = e.onTouch(TouchFeel(ReactKind.POKE, "head", Strength.FIRM, streak = 2), key, t0 + 11_000)
        assertNotNull(d1.line); assertNotNull(d2.line)
        assertTrue(d1.line != d2.line)
        val d3 = e.onTouch(TouchFeel(ReactKind.POKE, "head", Strength.FIRM, streak = 3), key, t0 + 12_000)
        assertNull("bank empty for this kind", d3.line)
        // the same lines can never come back, even if the AI writes them again later
        assertEquals(0, e.acceptRefillAfterRequest("POKE | Ow, do I look like a doorbell?", t0 + 200_000))
    }

    @Test fun thoughtsForBigMomentsAreSpacedAndTimedOut() {
        val e = ReactionEngine()
        val t0 = 5_000_000L
        val d = e.onTouch(TouchFeel(ReactKind.THROW, extra = "flung up"), key, t0)
        assertNotNull("a throw asks for a fresh thought", d.thoughtRequest)
        assertTrue(d.thoughtRequest!!.contains("thrown across the screen"))
        val again = e.onTouch(TouchFeel(ReactKind.BUMP), key, t0 + 2_000)
        assertNull("one at a time, spaced out", again.thoughtRequest)
        assertEquals("Whee— who threw me?! [angry]", e.acceptThought(d.thoughtToken, "\"Whee— who threw me?! [angry]\"", t0 + 3_000))
        val late = e.onTouch(TouchFeel(ReactKind.THROW), key, t0 + 20_000)
        assertNull("too slow: dropped", e.acceptThought(late.thoughtToken, "Finally an answer", t0 + 40_000))
        val rep = e.onTouch(TouchFeel(ReactKind.THROW), key, t0 + 50_000)
        assertNull("a repeat is dropped", e.acceptThought(rep.thoughtToken, "Whee who threw me", t0 + 51_000))
    }

    @Test fun smallTouchesWithLinesDontCallTheAi() {
        val e = ReactionEngine()
        val t0 = 9_000_000L
        e.refillRequest(key, t0)
        e.acceptRefill("PAT | Mmm, more head pats please 😌", t0 + 1_000)
        val d = e.onTouch(TouchFeel(ReactKind.PAT, "head"), key, t0 + 2_000)
        assertEquals("Mmm, more head pats please 😌", d.line)
        assertNull("instant line, no AI call", d.thoughtRequest)
    }

    @Test fun moodChangeFallsBackToSameTimeAndFriendship() {
        val e = ReactionEngine()
        val t0 = 12_000_000L
        e.refillRequest(key, t0)
        e.acceptRefill("TICKLE | Hehe, my belly is off duty!", t0)
        val grumpy = MoodKey.of(hour = 21, grumpy = 0.7f, sleepy = false, energy = 0.9f, friendLevel = 3)
        assertEquals("Hehe, my belly is off duty!", e.onTouch(TouchFeel(ReactKind.TICKLE, "belly"), grumpy, t0 + 1_000).line)
    }

    @Test fun refillsAreSpacedAndAskForMostUsedKindsFirst() {
        val e = ReactionEngine()
        val t0 = 20_000_000L
        repeat(5) { e.onTouch(TouchFeel(ReactKind.TAIL, "tail"), key, t0) }
        val r = e.refillRequest(key, t0 + 1_000)!!
        assertTrue("most-used kind first: $r", r.indexOf("- TAIL") in 0 until r.indexOf("- TICKLE").let { if (it < 0) Int.MAX_VALUE else it })
        assertNull("one at a time", e.refillRequest(key, t0 + 2_000))
        e.acceptRefill(null, t0 + 3_000)
        assertNull("spaced out", e.refillRequest(key, t0 + 10_000))
        assertNotNull(e.refillRequest(key, t0 + 60_000))
    }

    @Test fun savesAndLoads() {
        val e = ReactionEngine()
        val t0 = 30_000_000L
        e.refillRequest(key, t0)
        e.acceptRefill("TAIL | Hands off the tail, it's handmade!\nHOLD | Okay, carry me like a prince 👑", t0)
        e.onTouch(TouchFeel(ReactKind.TAIL, "tail"), key, t0 + 1_000)
        val copy = ReactionEngine().apply { load(e.save()) }
        assertEquals(1, copy.history.size)
        assertEquals(1, copy.total())
        assertEquals("Okay, carry me like a prince 👑", copy.onTouch(TouchFeel(ReactKind.HOLD), key, t0 + 2_000).line)
        assertFalse(copy.history.isFresh("Hands off the tail, it's handmade!"))
    }

    /** Test helper: a refill request then its answer. */
    private fun ReactionEngine.acceptRefillAfterRequest(answer: String, now: Long): Int {
        refillRequest(key, now)
        return acceptRefill(answer, now + 1)
    }
}
