package com.combo.monkeycompanion.agent

import com.combo.monkeycompanion.powers.PhoneCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MemoryTest {
    private fun store() = MemoryStore(File.createTempFile("mem", ".json").also { it.delete() })

    @Test fun parsesRememberRequests() {
        assertEquals("you love filter coffee", MemoryParse.remember("remember that I love filter coffee"))
        assertEquals("Your name is Ravi Kumar", MemoryParse.remember("my name is ravi kumar"))
        assertEquals("Your name is Ravi", MemoryParse.remember("en peru Ravi"))
        assertEquals("Your birthday is 5 March", MemoryParse.remember("My birthday is on 5 March"))
        assertEquals("Amma's birthday is June 12", MemoryParse.remember("Amma's birthday is June 12"))
        assertEquals("you go to the gym at 6", MemoryParse.remember("I go to the gym at 6, remember that"))
        assertNull(MemoryParse.remember("what's the weather"))
        assertEquals("you like coffee", MemoryParse.forget("forget that I like coffee"))
        assertEquals("everything", MemoryParse.forget("forget everything"))
    }

    @Test fun storesDedupesAndPersists() {
        val f = File.createTempFile("mem", ".json"); f.delete()
        val a = MemoryStore(f)
        a.add("Your name is Ravi"); a.add("you love filter coffee"); a.add("You love filter coffee.")
        assertEquals(2, a.all().size)
        a.add("Your name is Ravi Kumar")
        assertEquals("a new name replaces the old one", 1, a.all().count { it.kind == "name" })
        assertEquals("Ravi Kumar", a.name())
        val b = MemoryStore(f)
        assertEquals(2, b.all().size)
        assertEquals("Ravi Kumar", b.name())
    }

    @Test fun relevanceAndForget() {
        val s = store()
        s.add("Your name is Meena"); s.add("You love filter coffee"); s.add("Your sister Priya's birthday is 4 May"); s.add("You hate rain"); s.add("You walk every morning")
        val r = s.relevant("make me some coffee", 3)
        assertEquals("Your name is Meena", r[0].text)
        assertEquals("You love filter coffee", r[1].text)
        assertTrue(s.relevant("when is priya's birthday", 2).any { it.text.contains("Priya") })
        assertEquals(1, s.forget("coffee").size)
        assertFalse(s.all().any { it.text.contains("coffee") })
        assertEquals(0, s.forget("bananas").size)
        s.forget("everything"); assertTrue(s.all().isEmpty())
    }

    @Test fun kinds() {
        assertEquals("name", MemoryParse.kindOf("Your name is Ravi"))
        assertEquals("birthday", MemoryParse.kindOf("Amma's birthday is 12 June"))
        assertEquals("like", MemoryParse.kindOf("You love dosa"))
        assertEquals("habit", MemoryParse.kindOf("You wake up at 6 every day"))
    }
}

class RoutineTest {
    private fun cmd(t: String) = PhoneCommands.parse(t)?.let { it.id to it.args }
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = Calendar.getInstance().apply { clear(); set(y, mo - 1, d, h, mi, 0) }

    @Test fun parsesTimeRoutines() {
        val r = RoutineParse.parse("every day at 9 pm remind me to drink water", ::cmd)!!
        assertEquals(Trigger.At(21, 0), r.trigger)
        assertEquals("⏰ Reminder: drink water", r.say)
        val w = RoutineParse.parse("weekdays at 7:30 say good morning machan", ::cmd)!!
        assertEquals(Trigger.At(7, 30, RoutineParse.WEEKDAYS), w.trigger)
        assertEquals("Good morning machan", w.say)
        val n = RoutineParse.parse("every night at 10 turn on do not disturb", ::cmd)!!
        assertEquals(Trigger.At(22, 0), n.trigger)
        assertEquals("set_dnd", n.capId)
        val s = RoutineParse.parse("every sunday at 6 pm remind me to water the plants", ::cmd)!!
        assertEquals(Trigger.At(18, 0, setOf(1)), s.trigger)
        assertTrue(s.describe(), s.describe().startsWith("Every Sun at 6:00 pm"))
    }

    @Test fun parsesEventRoutines() {
        assertEquals(Trigger.On(RoutineEvent.CHARGER), RoutineParse.parse("when the charger is connected say thanks for the food", ::cmd)!!.trigger)
        assertEquals("Thanks for the food", RoutineParse.parse("when the charger is connected say thanks for the food", ::cmd)!!.say)
        assertEquals(Trigger.On(RoutineEvent.BATTERY_LOW), RoutineParse.parse("whenever battery is low remind me to charge", ::cmd)!!.trigger)
        val wifi = RoutineParse.parse("when wifi connects set media volume to 40", ::cmd)!!
        assertEquals(Trigger.On(RoutineEvent.WIFI), wifi.trigger); assertEquals("set_volume", wifi.capId)
        assertEquals(Trigger.On(RoutineEvent.HEADPHONES), RoutineParse.parse("when headphones are plugged in say music time", ::cmd)!!.trigger)
    }

    @Test fun ignoresNonRoutines() {
        assertNull(RoutineParse.parse("set an alarm at 7", ::cmd))
        assertNull(RoutineParse.parse("what's the weather today", ::cmd))
        assertNull(RoutineParse.parse("I eat dosa every day", ::cmd))   // no time, no event: not a routine
    }

    @Test fun schedulesNextOccurrence() {
        val wed = at(2026, 10, 21, 20, 0)   // Wednesday 8 pm
        assertEquals(at(2026, 10, 21, 21, 0).timeInMillis, RoutineTime.next(Trigger.At(21, 0), wed))
        assertEquals("already passed today -> tomorrow", at(2026, 10, 22, 7, 30).timeInMillis, RoutineTime.next(Trigger.At(7, 30), wed))
        val fri = at(2026, 10, 23, 8, 0)
        assertEquals("weekdays from Friday after 7:30 -> Monday", at(2026, 10, 26, 7, 30).timeInMillis, RoutineTime.next(Trigger.At(7, 30, RoutineParse.WEEKDAYS), fri))
        assertEquals("sundays", at(2026, 10, 25, 18, 0).timeInMillis, RoutineTime.next(Trigger.At(18, 0, setOf(1)), wed))
        assertEquals("exactly now is not 'next'", at(2026, 10, 22, 20, 0).timeInMillis, RoutineTime.next(Trigger.At(20, 0), wed))
    }
}

class NotifTest {
    private val l = listOf(
        NotifItem("1", "com.whatsapp", "WhatsApp", "Ravi", "Dinner at 8?", 3000, true),
        NotifItem("2", "com.google.android.apps.messaging", "Messages", "AX-HDFCBK", "483920 is your OTP for login. Do not share.", 2000, false),
        NotifItem("3", "com.whatsapp", "WhatsApp", "Amma", "Call me when free", 1000, true),
    )

    @Test fun summaryMasksOtpForBrain() {
        val b = NotifStore.summary(l, forBrain = true)
        assertTrue(b, b.startsWith("3 notifications from 2 apps"))
        assertFalse(b, b.contains("483920"))
        assertTrue(b, b.contains("Ravi: Dinner at 8?"))
        val u = NotifStore.summary(l, forBrain = false)
        assertTrue(u, u.contains("483920"))
    }

    @Test fun matchesWhoToReply() {
        assertEquals("3", NotifStore.match(l, "amma", replyable = true)?.key)
        assertEquals("1", NotifStore.match(l, "last", replyable = true)?.key)
        assertNull(NotifStore.match(l, "hdfc", replyable = true))
        assertEquals("2", NotifStore.latestOtp(l)?.key)
    }
}
