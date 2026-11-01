package com.combo.monkeycompanion.v5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** v10.20 daily mischief challenge: picked by date, counted by events, completed once. Pure. */
class ChallengeTest {
    private val day = LocalDate.of(2026, 11, 2)

    @Test fun pickedByDateSameAllDayAndNeighbouringDaysDiffer() {
        assertEquals(Challenge.kindFor(day), Challenge.kindFor(day))
        val week = (0L until 6L).map { Challenge.kindFor(day.plusDays(it)) }
        assertEquals("every kind comes up within 6 days", Challenge.Kind.values().toSet(), week.toSet())
        assertTrue((0L until 30L).all { Challenge.kindFor(day.plusDays(it)) != Challenge.kindFor(day.plusDays(it + 1)) })
    }

    @Test fun aNewDayStartsFresh() {
        val old = Challenge.State(day.minusDays(1).toString(), Challenge.Kind.PET, 7, true)
        val s = Challenge.today(old, day)
        assertEquals(day.toString(), s.day); assertEquals(0, s.progress); assertFalse(s.done)
        assertEquals(Challenge.kindFor(day), s.kind)
    }

    @Test fun countsOnlyMatchingEventsAndCompletesOnce() {
        var s = Challenge.State(day.toString(), Challenge.Kind.SPLAT)
        var completions = 0
        for (e in listOf(Challenge.Event.PET, Challenge.Event.SPLAT, Challenge.Event.THWACK, Challenge.Event.SPLAT, Challenge.Event.SPLAT, Challenge.Event.SPLAT)) {
            val (n, done) = Challenge.note(s, e, day); s = n; if (done) completions++
        }
        assertTrue(s.done); assertEquals(3, s.progress); assertEquals("only once", 1, completions)
        assertEquals("done ✅", s.status())
    }

    @Test fun crashCountsEveryKindOfImpact() {
        var s = Challenge.State(day.toString(), Challenge.Kind.CRASH)
        for (e in listOf(Challenge.Event.SPLAT, Challenge.Event.THWACK, Challenge.Event.PANCAKE)) s = Challenge.note(s, e, day).first
        assertEquals("3/4 crashes", s.status())
        val (fin, done) = Challenge.note(s, Challenge.Event.SPLAT, day)
        assertTrue(done); assertTrue(fin.done)
    }

    @Test fun catchesAndPatsAndChats() {
        assertTrue(Challenge.counts(Challenge.Kind.CATCH, Challenge.Event.CATCH))
        assertFalse(Challenge.counts(Challenge.Kind.CATCH, Challenge.Event.SPLAT))
        assertTrue(Challenge.counts(Challenge.Kind.PET, Challenge.Event.PET))
        assertTrue(Challenge.counts(Challenge.Kind.CHAT, Challenge.Event.CHAT))
    }

    @Test fun roundTripsAndRewardsXp() {
        val s = Challenge.State(day.toString(), Challenge.Kind.THWACK, 1, false)
        assertEquals(s, Challenge.State.decode(s.encode()))
        assertEquals(Challenge.State(), Challenge.State.decode(null))
        val r = Friendship.add(Friendship.State(), Friendship.Source.CHALLENGE, day.toString(), day.minusDays(1).toString())
        assertTrue("a finished challenge gives real XP: ${r.gained}", r.gained >= Friendship.Source.CHALLENGE.xp)
        assertTrue(Challenge.doneLine(Challenge.Kind.SPLAT, 2).isNotBlank())
    }
}
