package com.combo.monkeycompanion.v1013

import com.combo.monkeycompanion.brain.CareTools
import com.combo.monkeycompanion.brain.LongPressMenu
import com.combo.monkeycompanion.engine.show.BeatKind
import com.combo.monkeycompanion.engine.show.ChaseShow
import com.combo.monkeycompanion.engine.show.EndKind
import com.combo.monkeycompanion.engine.show.Frame
import com.combo.monkeycompanion.engine.show.FxKind
import com.combo.monkeycompanion.engine.show.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class V1013Test {
    @Test fun popupHasExactlyTheGroups() {
        assertEquals(listOf("g_powers", "g_games", "g_show", "g_modes", "g_tools", "g_access", "hide", "quit"), LongPressMenu.groupedMain())
        assertFalse("chat" in LongPressMenu.groupedMain()); assertFalse("settings" in LongPressMenu.groupedMain())
        assertEquals(listOf("smash", "banana_game", "eater"), LongPressMenu.groupItems("g_games"))
        assertEquals(listOf("show", "clip", "selfie"), LongPressMenu.groupItems("g_show"))
        assertEquals(6, LongPressMenu.groupItems("g_modes").size)
        assertEquals(5, LongPressMenu.groupItems("g_tools").size)
        assertEquals(listOf("chat", "talk", "screen", "missed"), LongPressMenu.groupItems("g_access"))
        for (g in LongPressMenu.groupedMain()) assertNotNull(g, LongPressMenu.groupedEntry(g))
        for (g in LongPressMenu.GROUPS) for (i in LongPressMenu.groupItems(g.id)) assertNotNull(i, LongPressMenu.groupedEntry(i))
    }

    @Test fun toolsFireAndPersistByTimestamps() {
        val T = CareTools.Tool.WATER
        var s = CareTools.toggle(T, CareTools.default(T), true, 0L)
        assertFalse(CareTools.due(T, s, 59 * 60_000L, 0, 1, -1))
        assertTrue(CareTools.due(T, s, 60 * 60_000L, 0, 1, -1))
        s = CareTools.fired(T, s, 60 * 60_000L)
        assertFalse(CareTools.due(T, s, 61 * 60_000L, 0, 1, -1))
        val tab = CareTools.toggle(CareTools.Tool.TABLET, CareTools.default(CareTools.Tool.TABLET), true, 0L)
        assertFalse(CareTools.due(CareTools.Tool.TABLET, tab, 0L, 8 * 60, 5, 4))
        assertTrue(CareTools.due(CareTools.Tool.TABLET, tab, 0L, 9 * 60, 5, 4))
        assertFalse(CareTools.due(CareTools.Tool.TABLET, tab, 0L, 10 * 60, 5, 5))
        val c = CareTools.toggle(CareTools.Tool.CUSTOM, CareTools.default(CareTools.Tool.CUSTOM).copy(value = 15, text = "Call Amma"), true, 1000L)
        assertTrue(CareTools.due(CareTools.Tool.CUSTOM, c, 1000L + 15 * 60_000L, 0, 0, -1))
        assertFalse(CareTools.fired(CareTools.Tool.CUSTOM, c, 0L).on)
        assertEquals("⏰ Call Amma", CareTools.line(CareTools.Tool.CUSTOM, c))
        assertFalse(CareTools.due(T, CareTools.default(T), Long.MAX_VALUE / 2, 0, 0, -1))
    }

    @Test fun showHasIntroBeatsEndingAndLength() {
        val rnd = Random(4)
        var last: ChaseShow? = null
        repeat(300) {
            val s = ChaseShow.random(rnd, last)
            assertTrue(s.beats.size in 3..4); assertEquals(s.beats.size, s.beats.distinct().size)
            assertFalse(BeatKind.INTRO in s.beats)
            assertTrue("len ${s.total}", s.total in 10f..20f)
            assertEquals(BeatKind.INTRO, s.segments.first().kind)
            assertEquals(s.ending, s.segments.last().end)
            if (last != null) { assertTrue(s.signature != last!!.signature); assertTrue(s.beats.first() != last!!.beats.first()) }
            last = s
        }
        assertTrue(ChaseShow.comboCount() >= 200)
    }

    @Test fun everyBeatAndEndingIsPlayableAndMoves() {
        for (b in ChaseShow.middleBeats) for (e in EndKind.entries) {
            val s = ChaseShow.of(listOf(b, ChaseShow.middleBeats.first { it != b }, ChaseShow.middleBeats.last { it != b }), e)
            val f = Frame()
            var minX = 9f; var maxX = -9f
            var t = 0f
            while (t < s.total) {
                for (r in Role.entries) { s.frame(r, t, f); assertFalse(f.x.isNaN() || f.lift.isNaN()); assertTrue(f.lift in -0.2f..0.6f) }
                s.frame(Role.CAT, t, f); minX = minOf(minX, f.x); maxX = maxOf(maxX, f.x)
                t += 1 / 30f
            }
            assertTrue("$b/$e cat moves ${maxX - minX}", maxX - minX > 0.2f)
            assertTrue(s.lines.isNotEmpty() && s.lines.all { it.text.isNotBlank() })
            assertTrue(s.fx.any { it.kind == FxKind.BANG || it.kind == FxKind.STARS || it.kind == FxKind.DUST })
        }
    }

    @Test fun edgeWrapRatLeavesRightAndReentersLeft() {
        val s = ChaseShow.of(listOf(BeatKind.EDGE_WRAP, BeatKind.SLIP, BeatKind.TIPTOE), EndKind.FRIENDS)
        val seg = s.segments[1]; val f = Frame()
        s.frame(Role.RAT, seg.t0 + seg.dur * 0.4f, f); assertTrue(f.x > 1f)
        s.frame(Role.RAT, seg.t0 + seg.dur * 0.6f, f); assertTrue(f.x < 0.2f)
        s.frame(Role.CAT, seg.t0 + seg.dur * 0.5f, f); assertTrue(f.sx < 0.9f && f.sy > 1.1f)   // wall bonk squash
    }

    @Test fun holeAndTailBite() {
        val s = ChaseShow.of(listOf(BeatKind.MOUSE_HOLE, BeatKind.TAIL_BITE, BeatKind.WALL_CLIMB), EndKind.CAT_BALL)
        assertTrue(s.fx.any { it.kind == FxKind.HOLE && it.x == 0.5f })
        val hole = s.segments[1]; val f = Frame()
        s.frame(Role.RAT, hole.t0 + hole.dur * 0.6f, f); assertFalse(f.visible)
        assertTrue(s.lines.any { "AAAAAH" in it.text })
        val wc = s.segments[3]
        s.frame(Role.RAT, wc.t0 + wc.dur * 0.5f, f); assertTrue(f.lift > 0.1f)
    }

    @Test fun showRngIsSeparate() {
        val g1 = Random(9).nextInt(); ChaseShow.random(Random(1)); assertEquals(g1, Random(9).nextInt())
        val a = ChaseShow.random(Random(77)); val b = ChaseShow.random(Random(77))
        assertEquals(a.signature, b.signature)
    }
}

class V1013OnboardingTest {
    private val O = com.combo.monkeycompanion.brain.Onboarding
    @Test fun demoGoesTapDoubleHoldAndCanSkip() {
        val r = com.combo.monkeycompanion.brain.Onboarding.Run()
        assertEquals(null, r.onGesture(com.combo.monkeycompanion.brain.Onboarding.Gesture.HOLD))          // wrong gesture: ignored
        assertNotNull(r.onGesture(com.combo.monkeycompanion.brain.Onboarding.Gesture.TAP))
        assertNotNull(r.onGesture(com.combo.monkeycompanion.brain.Onboarding.Gesture.DOUBLE_TAP))
        assertFalse(r.done)
        assertNotNull(r.onGesture(com.combo.monkeycompanion.brain.Onboarding.Gesture.HOLD))
        assertFalse("v10.30: sling + pinch still remain", r.done)
        assertNotNull(r.onGesture(com.combo.monkeycompanion.brain.Onboarding.Gesture.SLING))
        assertNotNull(r.onGesture(com.combo.monkeycompanion.brain.Onboarding.Gesture.PINCH))
        assertTrue(r.done)
        val s = com.combo.monkeycompanion.brain.Onboarding.Run(); s.skip(); assertTrue(s.done)
        val t = com.combo.monkeycompanion.brain.Onboarding.Run()
        repeat(com.combo.monkeycompanion.brain.Onboarding.MAX_SECS.toInt() + 1) { t.step(1f) }
        assertTrue("times out after MAX_SECS", t.done)
        assertEquals(5, com.combo.monkeycompanion.brain.Onboarding.steps.size)
    }
    @Test fun sevenDailyTipsOncePerDayRespectingBlocks() {
        assertEquals(7, O.tips.size)
        assertNotNull(O.tipFor(0, -1, false))
        assertEquals(null, O.tipFor(0, 0, false))     // already today
        assertEquals(null, O.tipFor(3, 2, true))      // quiet / typing / fullscreen
        assertEquals(null, O.tipFor(7, 6, false))     // after a week
        assertTrue(O.tips.any { "bite" in it })
    }
    @Test fun newDotsUntilUsed() {
        assertTrue(O.isNew("eater", emptySet())); assertFalse(O.isNew("eater", setOf("eater")))
        assertFalse(O.isNew("quit", emptySet()))
        assertEquals("Games •", O.label("Games", true))
    }
}

class V1013ComboAndBatteryNoteTest {
    @Test fun billaAndChittuAreOneComboInTheCrew() {
        val st = com.combo.monkeycompanion.world.WorldState()
        val crew = com.combo.monkeycompanion.world.CrewRules.resolve("billa", com.combo.monkeycompanion.world.Population.Mode.DUO, "chittu", null, st)
        assertEquals(listOf("billa", "chittu"), crew)
    }
}
