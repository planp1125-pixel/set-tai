package com.combo.monkeycompanion.powers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ClockCommandsTest {
    private fun at(h: Int, m: Int = 0): Calendar = Calendar.getInstance().apply { set(2026, 9, 22, h, m, 0) }
    private val morning = at(10)

    private fun timer(s: String) = (ClockCommands.parse(s, morning) as? ClockCommand.SetTimer)?.seconds
    private fun alarm(s: String, now: Calendar = morning) = ClockCommands.parse(s, now) as? ClockCommand.SetAlarm

    @Test fun durations() {
        val cases = mapOf(
            "set a timer for 5 minutes" to 300, "timer 5 min" to 300, "5 minute timer" to 300, "timer for 90 seconds" to 90,
            "set timer 1 hour" to 3600, "timer for half an hour" to 1800, "an hour timer" to 3600, "timer 1h 30m" to 5400,
            "timer for 2 and a half minutes" to 150, "timer for five minutes" to 300, "set a timer for ten mins" to 600,
            "timer 5" to 300, "1:30 timer" to 90, "remind me in 20 min" to 1200, "countdown 45 seconds" to 45,
            "Set a timer for 25 minutes for pasta" to 1500, "timer for an hour and a half" to 5400, "timer 3 hrs" to 10800,
            "timer for 15 secs" to 15, "timer 10 nimisham vei" to 600, "10 nimisham timer podu" to 600, "pls set a 7 min timer" to 420,
        )
        for ((phrase, sec) in cases) assertEquals(phrase, sec, timer(phrase))
    }

    @Test fun timerLabel() {
        val c = ClockCommands.parse("set a timer for 10 minutes for the rice", morning) as ClockCommand.SetTimer
        assertEquals(600, c.seconds); assertEquals("rice", c.label)
    }

    @Test fun timerWithoutLengthGoesToBrain() {
        assertNull(ClockCommands.parse("set a timer", morning))
        assertNull(ClockCommands.parse("I love timers", morning))
    }

    @Test fun alarms() {
        val cases = listOf(
            Triple("set an alarm for 6:30 am", 6, 30), Triple("alarm at 7 pm", 19, 0), Triple("wake me up at 6", 6, 0),
            Triple("alarm 18:45", 18, 45), Triple("set alarm for 5.15 am", 5, 15), Triple("alarm at noon", 12, 0),
            Triple("alarm at midnight", 0, 0), Triple("wake me at 7 in the morning", 7, 0), Triple("set an alarm at 9 pm tonight", 21, 0),
            Triple("kaalaila 6 manikku alarm vei", 6, 0), Triple("naalaikku kaalai 5 manikku ezhuppu", 5, 0), Triple("alarm 6:00 PM", 18, 0),
            Triple("raathiri 10 manikku alarm podu", 22, 0), Triple("set an alarm for 11:59 pm", 23, 59),
        )
        for ((p, h, m) in cases) {
            val a = alarm(p) ?: throw AssertionError("no alarm for: $p")
            assertEquals(p, h, a.hour); assertEquals(p, m, a.minute)
        }
    }

    @Test fun alarmTomorrow() {
        val a = alarm("set an alarm for tomorrow 5 am")!!
        assertEquals(5, a.hour); assertTrue(a.tomorrow)
        val b = alarm("naalaikku 6 manikku alarm vei")!!
        assertEquals(6, b.hour); assertTrue(b.tomorrow)
    }

    @Test fun alarmInDuration() {
        val a = alarm("set an alarm in 20 minutes", at(10, 0))!!
        assertEquals(10, a.hour); assertEquals(20, a.minute)
    }

    @Test fun listAndCancel() {
        assertEquals(ClockCommand.List, ClockCommands.parse("what timers do I have", morning))
        assertEquals(ClockCommand.List, ClockCommands.parse("show my alarms", morning))
        assertEquals(ClockCommand.Cancel("timer"), ClockCommands.parse("cancel my timer", morning))
        assertEquals(ClockCommand.Cancel("alarm"), ClockCommands.parse("delete the alarm", morning))
    }

    @Test fun notCommands() {
        for (p in listOf("what is an alarm clock", "hello settai", "my alarm didn't ring yesterday lol", "dance for me")) {
            val c = ClockCommands.parse(p, morning)
            assertTrue("$p -> $c", c == null || c is ClockCommand.List)
        }
    }

    @Test fun niceStrings() {
        assertEquals("5 min", ClockPowers.niceDuration(300))
        assertEquals("1 h 30 min", ClockPowers.niceDuration(5400))
        assertEquals("1 min 30 s", ClockPowers.niceDuration(90))
        assertEquals("6:30 AM", ClockPowers.niceTime(6, 30))
        assertEquals("12:00 AM", ClockPowers.niceTime(0, 0))
        assertEquals("7:05 PM", ClockPowers.niceTime(19, 5))
    }
}
