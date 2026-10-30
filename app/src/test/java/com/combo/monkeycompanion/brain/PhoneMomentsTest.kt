package com.combo.monkeycompanion.brain

import com.combo.monkeycompanion.brain.PhoneMoments.ChargeStyle
import com.combo.monkeycompanion.brain.PhoneMoments.Moment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v4.4 phone moments: gate (DND, quiet hours, calls, fullscreen, cooldown), saver short version, target, per-character style. */
class PhoneMomentsTest {
    private val never = Long.MAX_VALUE

    @Test fun gate() {
        assertNull(PhoneMoments.check(Moment.CHARGE, true, never, false, false, false, false))
        assertEquals("off", PhoneMoments.check(Moment.CHARGE, false, never, false, false, false, false))
        assertEquals("do not disturb", PhoneMoments.check(Moment.CHARGE, true, never, true, false, false, false))
        assertEquals("quiet hours", PhoneMoments.check(Moment.WIFI, true, never, false, true, false, false))
        assertEquals("in a call", PhoneMoments.check(Moment.HEADPHONES, true, never, false, false, true, false))
        assertEquals("fullscreen", PhoneMoments.check(Moment.LOW, true, never, false, false, false, true))
        assertEquals("cooldown", PhoneMoments.check(Moment.CHARGE, true, 60_000L, false, false, false, false))
        assertNull(PhoneMoments.check(Moment.CHARGE, true, Moment.CHARGE.cooldownMs, false, false, false, false))
        for (m in Moment.entries) assertTrue("${m.id} cooldown", m.cooldownMs >= 60_000L)
        assertFalse(Moment.SCREENSHOT.defaultOn)   // needs photo access Settai never asks for
    }

    @Test fun saverShortVersion() {
        for (m in Moment.entries) {
            assertTrue(PhoneMoments.duration(m, true) < PhoneMoments.duration(m, false))
            assertTrue(PhoneMoments.duration(m, false) <= 3.5f)   // short and sweet
        }
        assertFalse(PhoneMoments.effects(saver = true)); assertTrue(PhoneMoments.effects(saver = false))
    }

    @Test fun batteryTargetRtl() {
        val ltr = PhoneMoments.batteryTarget(1080f, 72f, rtl = false, density = 3f)
        assertEquals(1080f - 102f, ltr[0], 0.01f); assertEquals(36f, ltr[1], 0.01f)
        val rtl = PhoneMoments.batteryTarget(1080f, 72f, rtl = true, density = 3f)
        assertEquals(102f, rtl[0], 0.01f)
        assertEquals(36f, PhoneMoments.batteryTarget(1080f, 0f, false, 3f)[1], 0.01f)   // no inset: 24 dp bar
        assertEquals(1, PhoneMoments.facingToward(300f, ltr[0])); assertEquals(-1, PhoneMoments.facingToward(300f, rtl[0]))
    }

    @Test fun perCharacter() {
        assertEquals(ChargeStyle.ZAP, PhoneMoments.chargeStyle("monkey"))
        assertEquals(ChargeStyle.HEAT_BEAM, PhoneMoments.chargeStyle("superboy"))
        assertEquals(ChargeStyle.CABLE, PhoneMoments.chargeStyle("armour"))
        assertEquals(ChargeStyle.GIANT_PLUG, PhoneMoments.chargeStyle("mask"))
        assertEquals(ChargeStyle.ZAP, PhoneMoments.chargeStyle("newbie"))
        assertTrue(PhoneMoments.line(Moment.CHARGE, "monkey", kotlin.random.Random(0)).contains("machan") ||
            PhoneMoments.line(Moment.CHARGE, "monkey", kotlin.random.Random(1)).contains("machan"))
        for (m in Moment.entries) for (id in listOf("monkey", "superboy", "armour", "mask")) assertTrue(PhoneMoments.line(m, id).isNotBlank())
        assertTrue(PhoneMoments.line(Moment.UNPLUG, "monkey").contains("current", ignoreCase = true))
    }

    @Test fun beamTiming() {
        assertEquals(0f, PhoneMoments.beam(0.1f).second, 0f)
        assertEquals(1f, PhoneMoments.beam(0.5f).first, 0f)
        assertTrue(PhoneMoments.sparking(0.5f))
        assertFalse(PhoneMoments.sparking(0.95f))
    }
}
