package com.combo.monkeycompanion.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v4.3 gesture map: tap = reaction, double-tap = chat, long-press = menu, drag beats long-press. */
class GesturesTest {
    private val slop = 24f

    @Test fun timings() {
        assertTrue(Gestures.DOUBLE_TAP_MS < Gestures.LONG_PRESS_MS)
        assertTrue(Gestures.LONG_PRESS_MS in 400L..500L)
        assertEquals(8000L, Gestures.MENU_MS)
    }

    @Test fun longPressOpensMenu() {
        assertEquals(Gestures.Press.NONE, Gestures.press(300, 0f, slop))
        assertEquals(Gestures.Press.NONE, Gestures.press(449, 5f, slop))
        assertEquals(Gestures.Press.MENU, Gestures.press(450, 0f, slop))
        assertEquals(Gestures.Press.MENU, Gestures.press(3000, slop, slop))   // a long hold is still just the menu (no quit)
        assertEquals(Gestures.Press.NONE, Gestures.press(900, slop + 1f, slop))   // moved = drag, no menu
    }

    @Test fun dragWins() {
        assertFalse(Gestures.isDrag(10f, slop))
        assertTrue(Gestures.isDrag(30f, slop))
    }

    @Test fun tapIsAlwaysAReaction() {
        for (z in TouchZone.entries) assertEquals(z != TouchZone.NONE, Gestures.tap(z))
    }

    @Test fun menuTimeoutAndCoach() {
        assertFalse(Gestures.menuExpired(7999))
        assertTrue(Gestures.menuExpired(8000))
        assertTrue(Gestures.needsCoach(-1, 10))
        assertTrue(Gestures.needsCoach(9, 10))
        assertFalse(Gestures.needsCoach(10, 10))
        assertTrue(Gestures.coachLine(true).contains("Long-press = menu"))
        assertFalse(Gestures.coachLine(false).contains("menu"))
    }
}
