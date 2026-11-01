package com.combo.monkeycompanion.v5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** v10.19 Bond: warmth, daily check-in, streak grace, sulk, goodnight, heart points, AI line. Pure. */
class BondTest {
    private val mon = LocalDate.of(2026, 11, 2)   // a Monday

    @Test fun firstEverVisitIsFriendlyAndOnlyOncePerDay() {
        val c = Bond.checkIn(Bond.State(), mon, streak = 0, nowMs = 0)!!
        assertEquals(Bond.Moment.FIRST, c.moment)
        assertNull("same day again: nothing", Bond.checkIn(c.state, mon, 0, 0))
    }

    @Test fun nextDayIsAHelloAndWarmsUp() {
        val s = Bond.State(warmth = 50, lastDay = mon.toString())
        val c = Bond.checkIn(s, mon.plusDays(1), streak = 3, nowMs = 0)!!
        assertEquals(Bond.Moment.HELLO, c.moment)
        assertEquals(60, c.state.warmth)
        assertFalse(c.saveStreak)
    }

    @Test fun oneMissedDayUsesTheWeeklyFreePassOnce() {
        val s = Bond.State(lastDay = mon.toString())
        val g = Bond.checkIn(s, mon.plusDays(2), streak = 4, nowMs = 0)!!   // Wednesday, missed Tuesday
        assertEquals(Bond.Moment.GRACE, g.moment)
        assertTrue("the friendship streak is kept", g.saveStreak)
        assertEquals(Bond.weekKey(mon), g.state.graceWeek)
        // same week, another missed day: no second pass -> a (playful) sulk
        val again = Bond.checkIn(g.state, mon.plusDays(4), streak = 5, nowMs = 1_000)!!   // Friday, missed Thursday
        assertEquals(Bond.Moment.SULK, again.moment)
        assertTrue(again.state.sulking)
        // next week the pass is back
        val nextWeek = Bond.checkIn(again.state.copy(sulking = false, lastDay = mon.plusDays(7).toString()), mon.plusDays(9), streak = 3, nowMs = 0)!!   // next Mon, then Wed after skipping Tue
        assertEquals(Bond.Moment.GRACE, nextWeek.moment)
    }

    @Test fun noSulkWithoutAStreakAndNeverAfterALongBreak() {
        val s = Bond.State(lastDay = mon.toString(), warmth = 80)
        assertEquals("no streak to protect", Bond.Moment.HELLO, Bond.checkIn(s, mon.plusDays(3), streak = 1, nowMs = 0)!!.moment)
        val back = Bond.checkIn(s, mon.plusDays(9), streak = 20, nowMs = 0)!!
        assertEquals("5+ days away: just happy you're back", Bond.Moment.WELCOME_BACK, back.moment)
        assertFalse(back.state.sulking)
        assertTrue("warmth cooled but never below the floor", back.state.warmth in Bond.MIN_WARMTH..80)
    }

    @Test fun warmthCoolsPerMissedDayButNotBelowTheFloor() {
        val s = Bond.State(lastDay = mon.toString(), warmth = 70)
        assertEquals(70 - 15 * 2 + 10, Bond.checkIn(s, mon.plusDays(3), 1, 0)!!.state.warmth)
        val cold = Bond.State(lastDay = mon.toString(), warmth = 12)
        assertEquals(Bond.MIN_WARMTH + Bond.CHECKIN_WARMTH, Bond.checkIn(cold, mon.plusDays(4), 1, 0)!!.state.warmth)
    }

    @Test fun aPatEndsTheSulkAndSulksFadeByThemselves() {
        val sulking = Bond.State(sulking = true, sulkSince = 1_000L)
        val (after, ended) = Bond.pat(sulking)
        assertTrue(ended); assertFalse(after.sulking)
        assertFalse("a normal pat doesn't 'end' anything", Bond.pat(after).second)
        assertTrue("still sulking after an hour", Bond.fade(sulking, 1_000L + 3_600_000L).sulking)
        assertFalse("faded after a few hours", Bond.fade(sulking, 1_000L + Bond.SULK_FADES_MS + 1).sulking)
    }

    @Test fun warmthGoesUpWithTimeTogetherAndCapsAt100() {
        var s = Bond.State(warmth = 95)
        s = Bond.warmUp(s, Friendship.Source.CHAT)
        assertEquals(99, s.warmth)
        s = Bond.warmUp(s, Friendship.Source.GAME)
        assertEquals(100, s.warmth)
        assertEquals(100, Bond.warmUp(s, Friendship.Source.PET).warmth)
    }

    @Test fun goodnightOncePerNightAfterTen() {
        val s = Bond.State()
        assertFalse(Bond.goodnightDue(s, LocalDateTime.of(2026, 11, 2, 21, 59)))
        assertTrue(Bond.goodnightDue(s, LocalDateTime.of(2026, 11, 2, 22, 0)))
        val said = s.copy(nightDay = Bond.nightKey(LocalDateTime.of(2026, 11, 2, 22, 30))!!)
        assertFalse("after midnight it's still the same night", Bond.goodnightDue(said, LocalDateTime.of(2026, 11, 3, 1, 15)))
        assertFalse(Bond.goodnightDue(said, LocalDateTime.of(2026, 11, 3, 9, 0)))
        assertTrue("the next night", Bond.goodnightDue(said, LocalDateTime.of(2026, 11, 3, 23, 0)))
    }

    @Test fun heartPointsFollowTheFriendshipLevelUpTo100() {
        assertEquals(0, Bond.points(0))
        assertTrue(Bond.points(Friendship.xpFor(2)) in 10..12)
        assertTrue("monotonic", (0..3000 step 37).map { Bond.points(it) }.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(100, Bond.points(Friendship.xpFor(10)))
        assertEquals(100, Bond.points(Friendship.xpFor(14)))
    }

    @Test fun stateRoundTrips() {
        val s = Bond.State(77, "2026-11-02", "2026-11-02", true, 12345L, "2026-11-01")
        assertEquals(s, Bond.State.decode(s.encode()))
        assertEquals(Bond.State(), Bond.State.decode(null))
        assertEquals(Bond.State(), Bond.State.decode(""))
    }

    @Test fun promptLineStaysShortAndUsesMemories() {
        val line = Bond.promptLine(Friendship.xpFor(4), warmth = 90, streak = 6, sulking = false,
            facts = listOf("Your name is Manik", "You have an exam on Monday", "You like filter coffee", "A".repeat(200)))
        assertTrue(line, line.length <= 220)
        assertTrue(line, line.contains("level 4") && line.contains("glowing") && line.contains("6-day streak"))
        assertTrue(line, line.contains("Manik") && line.contains("exam"))
        assertFalse("too-long facts are left out", line.contains("AAAA"))
    }

    @Test fun linesArePickedByDateNotRandom() {
        assertEquals(Bond.hello(8, "Manik", 3), Bond.hello(8, "Manik", 3))
        assertTrue(Bond.hello(8, "Manik", 0).contains("Manik"))
        assertFalse(Bond.hello(13, null, 1).contains(", "))
        assertTrue(Bond.graceLine(5).contains("5-day"))
    }
}
