package com.combo.monkeycompanion.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v10.4: the juggling balls' physics on their own. */
class JuggleBallsTest {
    private val dt = 1f / 60f
    private val d = 3f
    private val r = 15f

    @Test fun cascadeStaysBetweenTheHandsAndAboveThem() {
        val b = JuggleBalls(); val out = FloatArray(2)
        var minY = Float.MAX_VALUE
        repeat(300) {
            b.juggleT += dt
            for (i in 0 until 3) {
                b.heldPosition(i, 100f, 500f, 200f, 500f, 120f, 0f, out)
                assertTrue(out[0] in 99f..201f); assertTrue(out[1] <= 500.1f); minY = minOf(minY, out[1])
            }
        }
        assertTrue("balls go up high", minY < 400f)
    }

    @Test fun aFlungBallBouncesInsideTheScreenThenRestsAndFades() {
        val b = JuggleBalls()
        b.drop(0, 500f, 900f, 3000f, -2500f)
        var hitRight = false; var n = 0
        while (b.state[0] == JuggleBalls.State.LOOSE && n < 60 * 20) {
            b.stepLoose(dt, 0f, 0f, 1080f, 2200f, r, d)
            assertTrue("inside the screen", b.x[0] in r - 0.01f..1080f - r + 0.01f && b.y[0] in r - 0.01f..2200f - r + 0.01f)
            if (b.x[0] >= 1080f - r - 0.01f) hitRight = true
            n++
        }
        assertTrue("bounced off the right edge", hitRight)
        assertEquals(JuggleBalls.State.GONE, b.state[0])
        assertEquals(2200f - r, b.y[0])
        assertTrue("settled and faded within ~20 s ($n frames)", n < 60 * 20)
    }

    @Test fun heldCountsAndRefill() {
        val b = JuggleBalls()
        assertEquals(3, b.heldCount); assertFalse(b.looseActive)
        b.drop(1, 0f, 0f, 0f, 0f)
        assertEquals(2, b.heldCount); assertTrue(b.looseActive)
        b.drop(1, 0f, 0f, 0f, 0f)      // already loose: ignored
        assertEquals(2, b.heldCount)
        b.refill()
        assertEquals(3, b.heldCount); assertFalse(b.looseActive)
    }
}
