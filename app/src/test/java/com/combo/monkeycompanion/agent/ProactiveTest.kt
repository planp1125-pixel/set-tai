package com.combo.monkeycompanion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.combo.monkeycompanion.brain.PowerEntrances as E
import com.combo.monkeycompanion.brain.Superpower as SP
import com.combo.monkeycompanion.brain.HeroSwoop
import com.combo.monkeycompanion.brain.ConfettiRocketsPower
import com.combo.monkeycompanion.character.MonkeyCharacter
import com.combo.monkeycompanion.character.Superboy
import com.combo.monkeycompanion.character.ArmourAnna
import com.combo.monkeycompanion.character.MaskMan

/** v4.2 proactive buddy: rate limiter, quiet hours, suppression, notification kinds. */
class ProactiveTest {
    private val noon = 12 * 60
    private val q0 = 23 * 60; private val q1 = 7 * 60
    private val MIN = 60_000L

    @Test fun quietHoursWrap() {
        assertTrue(QuietHours.inside(23 * 60 + 30, q0, q1))
        assertTrue(QuietHours.inside(3 * 60, q0, q1))
        assertTrue(QuietHours.inside(6 * 60 + 59, q0, q1))
        assertFalse(QuietHours.inside(7 * 60, q0, q1))
        assertFalse(QuietHours.inside(noon, q0, q1))
        assertTrue(QuietHours.inside(14 * 60, 13 * 60, 15 * 60))
        assertFalse(QuietHours.inside(noon, 600, 600))
        assertEquals(22 * 60 + 30, QuietHours.parse("22:30", 0))
        assertEquals(5, QuietHours.parse("25:00", 5))
        assertEquals("07:05", QuietHours.fmt(425))
    }

    @Test fun hourlyLimitPerLevel() {
        val g = ProactiveGate(); var now = 1_000_000_000L
        val cats = listOf(ProCat.BURST, ProCat.APP_QUIP, ProCat.WELLBEING, ProCat.DIGEST, ProCat.IMPORTANT)
        for (i in 0 until 4) {
            assertNull(g.check(cats[i], ProLevel.BUDDY, true, now, noon, q0, q1))
            g.record(cats[i], now); now += MIN
        }
        assertEquals("hourly limit (4)", g.check(ProCat.IMPORTANT, ProLevel.BUDDY, true, now, noon, q0, q1))
        assertNull(g.check(ProCat.IMPORTANT, ProLevel.CHATTERBOX, true, now, noon, q0, q1))
        // OTPs and deliveries don't spend the budget.
        assertNull(g.check(ProCat.OTP, ProLevel.BUDDY, true, now, noon, q0, q1))
        assertNull(g.check(ProCat.DELIVERY, ProLevel.BUDDY, true, now, noon, q0, q1))
        // An hour after the first one, there's room again.
        assertNull(g.check(ProCat.IMPORTANT, ProLevel.BUDDY, true, 1_000_000_000L + 60 * MIN, noon, q0, q1))
        assertEquals(3, g.usedThisHour(1_000_000_000L + 60 * MIN))
    }

    @Test fun levelsAndSwitches() {
        val g = ProactiveGate()
        assertEquals("proactive is off", g.check(ProCat.OTP, ProLevel.OFF, true, 0, noon, q0, q1))
        assertEquals("needs level Buddy", g.check(ProCat.BURST, ProLevel.CHILL, true, 0, noon, q0, q1))
        assertNull(g.check(ProCat.IMPORTANT, ProLevel.CHILL, true, 0, noon, q0, q1))
        assertEquals("category off", g.check(ProCat.IMPORTANT, ProLevel.BUDDY, false, 0, noon, q0, q1))
        assertEquals(2, ProLevel.CHILL.perHour)
        assertEquals(4, ProLevel.BUDDY.perHour)
        assertEquals(ProLevel.BUDDY, ProLevel.of("nonsense"))
    }

    @Test fun quietAndSuppression() {
        val g = ProactiveGate(); val night = 0 * 60 + 30
        assertEquals("quiet hours", g.check(ProCat.IMPORTANT, ProLevel.BUDDY, true, 0, night, q0, q1))
        assertNull(g.check(ProCat.OTP, ProLevel.BUDDY, true, 0, night, q0, q1))
        assertEquals("do not disturb", g.check(ProCat.OTP, ProLevel.BUDDY, true, 0, noon, q0, q1, ProSituation(dnd = true)))
        assertEquals("in a call", g.check(ProCat.OTP, ProLevel.BUDDY, true, 0, noon, q0, q1, ProSituation(inCall = true)))
        assertEquals("you're typing", g.check(ProCat.APP_QUIP, ProLevel.CHATTERBOX, true, 0, noon, q0, q1, ProSituation(typing = true)))
        assertNull(g.check(ProCat.OTP, ProLevel.BUDDY, true, 0, noon, q0, q1, ProSituation(typing = true)))   // the code you're typing in
        assertEquals("fullscreen", g.check(ProCat.BURST, ProLevel.BUDDY, true, 0, noon, q0, q1, ProSituation(fullscreen = true)))
        assertEquals("gaming", g.check(ProCat.BURST, ProLevel.BUDDY, true, 0, noon, q0, q1, ProSituation(game = true)))
    }

    @Test fun cooldowns() {
        val g = ProactiveGate(); val t = 5_000_000L
        g.record(ProCat.APP_QUIP, t, "com.instagram.android")
        assertEquals("cooldown", g.check(ProCat.APP_QUIP, ProLevel.CHATTERBOX, true, t + 5 * MIN, noon, q0, q1))
        assertEquals("cooldown for com.instagram.android", g.check(ProCat.APP_QUIP, ProLevel.CHATTERBOX, true, t + 11 * MIN, noon, q0, q1, key = "com.instagram.android", keyCooldownMs = 180 * MIN))
        assertNull(g.check(ProCat.APP_QUIP, ProLevel.CHATTERBOX, true, t + 11 * MIN, noon, q0, q1, key = "com.google.android.youtube", keyCooldownMs = 180 * MIN))
    }

    @Test fun notificationKinds() {
        assertTrue(NotifKinds.isDelivery("in.swiggy.android", "Your order is out for delivery"))
        assertTrue(NotifKinds.isDelivery("com.ubercab", "Your driver is arriving in 3 min"))
        assertFalse(NotifKinds.isDelivery("com.whatsapp", "Are you arriving today?"))
        assertTrue(NotifKinds.important("Amma", emptyList(), emptyMap()))
        assertTrue(NotifKinds.important("Priya", listOf("Your wife is Priya"), emptyMap()))
        assertTrue(NotifKinds.important("Ravi", emptyList(), mapOf("Ravi" to 12, "Bank" to 2)))
        assertFalse(NotifKinds.important("Bank", emptyList(), mapOf("Ravi" to 12, "Bank" to 2)))
        assertFalse(NotifKinds.important("+91 98400 12345", emptyList(), emptyMap()))
        val items = listOf("Amma", "Ravi", "Amma", "Swiggy").mapIndexed { i, w -> NotifItem("k$i", "com.whatsapp", "WhatsApp", w, "hi", i.toLong(), true) }
        assertEquals("Amma ×2, Ravi, Swiggy", NotifKinds.who(items))
        assertTrue(ProactiveLines.otp("HDFC", "123456").contains("123456"))
    }

    @Test fun powerEntrances() {
        val monkey = MonkeyCharacter.powers
        assertEquals(SP.SuperBanana, E.pick(E.Kind.FLY, "monkey", monkey))
        assertEquals(SP.ThunderMonkey, E.pick(E.Kind.STRIKE, "monkey", monkey))
        assertEquals(SP.RocketMonkey, E.pick(E.Kind.JETS, "monkey", monkey))
        assertEquals(HeroSwoop, E.pick(E.Kind.FLY, "superboy", Superboy.powers))
        assertEquals(ConfettiRocketsPower, E.pick(E.Kind.JETS, "armour", ArmourAnna.powers))
        assertEquals(SP.Magician, E.pick(E.Kind.STRIKE, "mask", MaskMan.powers))
        // Every character gets a power for every kind (its own).
        for (ch in listOf(MonkeyCharacter, Superboy, ArmourAnna, MaskMan))
            for (k in E.Kind.entries) assertTrue("${ch.id} $k", E.pick(k, ch.id, ch.powers) in ch.powers)
        // Unknown character: falls back to one of its own powers.
        assertEquals(SP.IceMonkey, E.pick(E.Kind.STRIKE, "newbie", listOf<SP>(SP.IceMonkey)))
        // Frequency: Off never, saver never, Sometimes ~30 %, Often ~60 %.
        val r = kotlin.random.Random(7)
        assertFalse((0 until 200).any { E.roll("off", false, false, r) })
        assertFalse((0 until 200).any { E.roll("often", true, false, r) })
        assertFalse((0 until 200).any { E.roll("often", false, true, r) })
        val some = (0 until 2000).count { E.roll("sometimes", false, false, r) }
        val often = (0 until 2000).count { E.roll("often", false, false, r) }
        assertTrue("$some", some in 480..720); assertTrue("$often", often in 1080..1320)
        // Which pipes get which entrance.
        assertEquals(E.Kind.FLY, Pipe.entranceFor(ProCat.BURST))
        assertEquals(E.Kind.STRIKE, Pipe.entranceFor(ProCat.IMPORTANT))
        assertEquals(E.Kind.JETS, Pipe.entranceFor(ProCat.JOKE))
        assertNull(Pipe.entranceFor(ProCat.OTP))
    }

    @Test fun powerJokesMaxOnePerHour() {
        val g = ProactiveGate(); val t = 9_000_000L
        assertNull(g.check(ProCat.JOKE, ProLevel.BUDDY, true, t, noon, q0, q1))
        g.record(ProCat.JOKE, t)
        assertEquals("cooldown", g.check(ProCat.JOKE, ProLevel.CHATTERBOX, true, t + 59 * MIN, noon, q0, q1))
        assertNull(g.check(ProCat.JOKE, ProLevel.CHATTERBOX, true, t + 60 * MIN, noon, q0, q1))
        assertEquals("needs level Buddy", g.check(ProCat.JOKE, ProLevel.CHILL, true, t, noon, q0, q1))
        assertEquals("quiet hours", g.check(ProCat.JOKE, ProLevel.BUDDY, true, t + 120 * MIN, 30, q0, q1))
    }
}
