package com.combo.monkeycompanion.v1017

import com.combo.monkeycompanion.ai.BodySense
import com.combo.monkeycompanion.ai.BodyVoice
import com.combo.monkeycompanion.theme.Th
import org.junit.Assert.*
import org.junit.Test

class V1017Test {
    @Test fun bodyVoiceRateLimitsAndDropsSlowAnswers() {
        val g = BodyVoice(eventGap = 20f, idleMin = 45f, idleMax = 90f, timeoutSec = 12f)
        assertTrue(g.wantEvent(100f))
        val tok = g.begin(100f)
        assertFalse("one at a time", g.wantEvent(101f))
        assertTrue(g.accept(tok, 105f))
        assertFalse("event gap", g.wantEvent(110f))
        assertTrue(g.wantEvent(121f))
        val slow = g.begin(121f)
        g.expire(140f)
        assertFalse("late answer dropped", g.accept(slow, 141f))
        for (n in 0..200) assertTrue(g.idleGap(n) in 45f..90f)
        val idle = BodyVoice()
        assertFalse("first quiet line waits a full gap", idle.wantIdle(0f))
        assertFalse(idle.wantIdle(44f))
        assertTrue(idle.wantIdle(91f))
    }

    @Test fun bodySenseNewFeelingsStayCompact() {
        val now = 1_000_000L
        val ev = listOf(
            BodySense.Event(now - 5_000, "I bumped into the screen edge", BodySense.Feeling.OUCH),
            BodySense.Event(now - 3_000, "the keyboard pushed me aside", BodySense.Feeling.SQUISHED),
            BodySense.Event(now - 1_000, "you're holding me", BodySense.Feeling.HELD),
        )
        val s = BodySense.Snapshot("Tutty", "bottom-left", "resting, idle", battery = 80, hour = 15, quietMin = 12, restedMin = 5, keyboard = true, app = "chat list")
        val note = BodySense.build(s, ev, now)
        assertTrue(note, note.length <= BodySense.MAX_CHARS)
        assertTrue(note, note.contains("held gently") && note.contains("keyboard"))
        assertFalse("plain feelings, no raw minutes", note.contains("quietMin") || note.contains("12 min"))
        // fades
        val later = BodySense.feelings(s.copy(keyboard = false), ev, now + 120_000)
        assertFalse(later.any { it.contains("ouch") || it.contains("held") })
        assertTrue(BodySense.feelings(s.copy(restedMin = 200, quietMin = 0), emptyList(), now).contains("tired"))
        assertTrue(BodySense.feelings(s.copy(quietMin = 30, restedMin = 30), emptyList(), now).contains("a bit bored"))
    }

    @Test fun themeFoldsColoursIntoRedBlackWhite() {
        assertEquals(Th.WHITE, Th.rgb(0xFF, 0xF8, 0xEE))
        assertEquals(Th.INK, Th.rgb(0x1E, 0x22, 0x40))
        assertEquals(Th.RED, Th.rgb(0x6C, 0x5C, 0xE7))
        assertEquals(0x40, Th.argb(0x40, 0xFF, 0xFF, 0xFF) ushr 24)          // faint stays faint
        assertEquals(0xFF4CAF50.toInt(), Th.rgb(0x4C, 0xAF, 0x50))          // greens keep meaning
    }
}
