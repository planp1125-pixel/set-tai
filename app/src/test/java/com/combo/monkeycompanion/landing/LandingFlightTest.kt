package com.combo.monkeycompanion.landing

import com.combo.monkeycompanion.brain.LandingFlight
import com.combo.monkeycompanion.brain.LandingFlight.Finish
import com.combo.monkeycompanion.brain.LandingFlight.Path
import com.combo.monkeycompanion.brain.Landings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LandingFlightTest {
    private val w = 1080f; private val h = 2340f

    @Test fun everyPathIsACurveThatStaysOnScreenAndEndsExactly() {
        val rnd = Random(7)
        for (path in Path.entries) repeat(20) {
            val p = LandingFlight.plan(path, Finish.CRASH, 300f, 900f, 800f, 2100f, w, h, top = 120f, margin = 100f, rnd = rnd)
            assertTrue("$path curved: ${LandingFlight.maxDeviation(p)}", LandingFlight.maxDeviation(p) > 120f)
            val o = FloatArray(2)
            for (i in 0..100) {
                LandingFlight.at(p, i / 100f, o)
                assertTrue("$path x ${o[0]}", o[0] in 100f..980f); assertTrue("$path y ${o[1]}", o[1] in 120f..2100f)
            }
            LandingFlight.at(p, 1f, o); assertEquals(800f, o[0], 0.01f); assertEquals(2100f, o[1], 0.01f)
            assertEquals(0f, LandingFlight.bodyRot(p, 1f), 0.01f)          // upright for the touchdown
            assertTrue(p.dur in 1.5f..3.3f)
        }
    }

    @Test fun banksAlongThePathAndLoopsActuallyLoop() {
        val p = LandingFlight.plan(Path.LOOP, Finish.FIST, 200f, 1500f, 900f, 2100f, w, h, 120f, 100f, Random(1))
        val rots = (0..100).map { LandingFlight.bodyRot(p, it / 100f) }
        assertTrue("banks far: ${rots.maxOf { kotlin.math.abs(it) }}", rots.maxOf { kotlin.math.abs(it) } > 120f)
        // heading sweeps through all four quadrants during the loop
        val hd = (30..66).map { LandingFlight.heading(p, it / 100f) }
        assertTrue(hd.any { it in -180f..-90f } && hd.any { it in -90f..0f } && hd.any { it in 0f..90f } && hd.any { it in 90f..180f })
    }

    @Test fun randomPathEveryTimeNeverTheSameTwiceInARow() {
        val rnd = Random(3); var last: Path? = null; val seen = HashSet<Path>()
        repeat(200) { val p = LandingFlight.pickPath(last, rnd); assertNotEquals(last, p); seen += p; last = p }
        assertEquals(Path.entries.toSet(), seen)
        val a = LandingFlight.plan(Path.SWOOP, Finish.FIST, 300f, 900f, 800f, 2100f, w, h, 120f, 100f, Random(1))
        val b = LandingFlight.plan(Path.SWOOP, Finish.FIST, 300f, 900f, 800f, 2100f, w, h, 120f, 100f, Random(2))
        assertNotEquals(a.c1x, b.c1x)
    }

    @Test fun eachCharacterHasItsOwnFinishAndRocketArcsAreFrequent() {
        assertEquals(Finish.FIST, LandingFlight.finish("superboy"))
        assertEquals(Finish.HOVER_THUD, LandingFlight.finish("armour"))
        assertEquals(Finish.CRASH, LandingFlight.finish("monkey"))
        for (id in listOf("superboy", "armour")) {
            val pool = Landings.allowed(id, false, true, false, true)
            val arc = pool.first { it.first == Landings.Style.ROCKET_ARC }.second
            assertTrue("$id rocket arcs carry real weight", arc * 5 >= pool.sumOf { it.second })
        }
        // the monkey keeps its beloved leg-fire jets (scream-then-jets) with good weight
        assertTrue(Landings.pool("monkey", false).none { it.first == Landings.Style.SCREAM_JETS })   // v10.25.1: no jets for Settai
        // power landings ~half or more for the flyers, no repeats in a row
        val rnd = Random(9); var last: Landings.Style? = null
        repeat(300) {
            val s = Landings.decide("superboy", false, true, false, false, 1500f, 3f, last, 2340f, rnd = rnd).style
            assertNotEquals(last, s); last = s
        }
        // battery saver still simplifies (only Settai's own saver)
        assertTrue(Landings.allowed("armour", false, true, true, false).none { it.first == Landings.Style.ROCKET_ARC })
    }
}
