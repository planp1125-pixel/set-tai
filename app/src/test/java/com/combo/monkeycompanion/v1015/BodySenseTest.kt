package com.combo.monkeycompanion.v1015

import com.combo.monkeycompanion.ai.*
import com.combo.monkeycompanion.brain.PromptBudget
import com.combo.monkeycompanion.character.CharacterRegistry
import org.junit.Assert.*
import org.junit.Test

class BodySenseTest {
    private val now = 1_000_000_000L
    private fun snap(bat: Int = 80, ch: Boolean = false, hour: Int = 14) =
        BodySense.Snapshot("Rocket Girl", "hanging from the top edge of the screen", "resting, idle", battery = bat, charging = ch, hour = hour, mood = "happy", friendship = "Thick friends")

    @Test fun buildsCompactBlockWithEventsAndFeelings() {
        val ev = listOf(BodySense.Event(now - 5_000, "you flung me up", BodySense.Feeling.DIZZY))
        val b = BodySense.build(snap(), ev, now)
        assertTrue(b.startsWith(BodySense.MARKER))
        assertTrue(b.contains("Rocket Girl") && b.contains("top edge") && b.contains("flung me up") && b.contains("5s ago"))
        assertTrue(b.contains("dizzy")); assertTrue(b.contains("80%")); assertTrue(b.contains("afternoon"))
        assertTrue(b.length <= BodySense.MAX_CHARS)
        assertFalse(b.contains("rocket launch"))
    }

    @Test fun feelingsFadeAndComeFromState() {
        val ev = listOf(BodySense.Event(now - 200_000, "old wipeout", BodySense.Feeling.SORE))
        assertTrue(BodySense.feelings(snap(), ev, now).isEmpty())
        val mid = listOf(BodySense.Event(now - 60_000, "wipeout", BodySense.Feeling.SORE))
        assertTrue(BodySense.feelings(snap(), mid, now).first().startsWith("still slightly"))
        assertTrue(BodySense.feelings(snap(bat = 10), emptyList(), now).any { it.contains("tired") })
        assertTrue(BodySense.feelings(snap(ch = true), emptyList(), now).contains("energised"))
        assertTrue(BodySense.feelings(snap(hour = 2), emptyList(), now).contains("sleepy"))
    }

    @Test fun memoryWindowAndCap() {
        BodySense.clear()
        BodySense.record("old", now = now - 200_000); BodySense.record("new", now = now)
        assertEquals(listOf("new"), BodySense.recent(now).map { it.text })
        val many = (1..20).map { BodySense.Event(now - it * 1000L, "a long long event number $it happened to my body", null) }
        assertTrue(BodySense.build(snap(), many, now, 300).length <= 300)
        BodySense.clear()
    }

    @Test fun directionWords() {
        assertEquals("up", BodySense.direction(0f, -900f)); assertEquals("left", BodySense.direction(-900f, 10f))
        assertEquals("down-right", BodySense.direction(500f, 500f))
    }

    @Test fun bodyBeatsHistoryOnTinyContext() {
        val body = BodySense.build(snap(), listOf(BodySense.Event(now - 3000, "the charger zapped me", BodySense.Feeling.ZAPPED)), now)
        val sys = Persona.systemPrompt(false, body, compact = true)
        assertTrue(sys.contains(BodySense.MARKER) && sys.contains(BodySense.RULES))
        val hist = (1..30).map { PromptBudget.Turn(it % 2 == 0, "some old chat message number $it with plenty of words in it") }
        for (ctx in listOf(1280, 4096)) {
            val f = PromptBudget.fit(sys, hist, "how are you?", ctx)
            assertTrue("ctx $ctx", f.system.contains("charger zapped me"))
            assertTrue(f.history.size < hist.size || ctx > 1280)
            val m = PromptBudget.fit(sys, hist, "how are you?", ctx, minimal = true)
            assertTrue("minimal $ctx", m.system.contains(BodySense.MARKER))
        }
        assertTrue(PromptBudget.compactSystem(sys, 200).contains(BodySense.MARKER))
    }

    @Test fun tagsParsedStrippedAndGated() {
        val p = ActionParser.parse("Wheee, that fling made me dizzy! [spin] [teleport] [fly]")
        assertEquals("Wheee, that fling made me dizzy! [teleport]", p.text)
        val g = BodySense.gate(p.actions, BodySense.SAFE)
        assertEquals(listOf(BodyAction.SPIN), g)
        assertFalse(BodyAction.FLY in BodySense.SAFE)
    }

    @Test fun allowListPerCharacter() {
        for (c in CharacterRegistry.all) {
            val a = BodySense.allowList(c, CharacterRegistry.wear(c))
            assertTrue(c.id, a.isNotEmpty() && a.all { it in BodySense.SAFE })
        }
    }

    @Test fun limiter() {
        val l = BodySense.Limiter(4000, 3)
        assertTrue(l.allow(0)); assertFalse(l.allow(1000)); assertTrue(l.allow(5000)); assertTrue(l.allow(10000)); assertFalse(l.allow(20000))
        assertTrue(l.allow(70000))
    }
}
