package com.combo.monkeycompanion.brain

import com.combo.monkeycompanion.brain.Landings.Style
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** v4.4 smart landings: weighted no-repeat picker, per-character pools, saver / powers-off filters. */
class LandingsTest {
    @Test fun neverRepeatsTheLastOne() {
        val rnd = Random(42)
        val opts = Landings.allowed("monkey", false, powersOn = true, saver = false, canClimb = true)
        var last: Style? = null
        repeat(500) { val s = Landings.pick(opts, last, rnd); assertNotEquals(last, s); last = s }
        // Only one option: it may repeat.
        assertEquals(Style.JETS, Landings.pick(listOf(Style.JETS to 3), Style.JETS, rnd))
    }

    @Test fun weightsRoughlyHold() {
        val rnd = Random(7)
        val opts = listOf(Style.CAPE_FLOAT to 3, Style.SPEED_DIVE to 1)
        val n = (0 until 4000).count { Landings.pick(opts, null, rnd) == Style.CAPE_FLOAT }
        assertTrue("$n", n in 2800..3200)   // ~75 %
        assertEquals(Style.SIMPLE, Landings.pick(emptyList(), null, rnd))
        assertEquals(Style.SPEED_DIVE, Landings.pick(listOf(Style.CAPE_FLOAT to 0, Style.SPEED_DIVE to 1), null, rnd))
    }

    @Test fun everyMonkeyStyleShowsUp() {
        val rnd = Random(1)
        val opts = Landings.allowed("monkey", false, true, false, true)
        var last: Style? = null
        val seen = HashSet<Style>()
        repeat(800) { last = Landings.pick(opts, last, rnd); seen.add(last!!) }
        assertEquals(opts.map { it.first }.toSet(), seen)
        assertEquals(7, seen.size)   // v10.25.1: no jet / rocket-arc landings for Settai
    }

    @Test fun perCharacter() {
        assertEquals(setOf(Style.ROCKET_ARC, Style.CAPE_FLOAT, Style.SPEED_DIVE, Style.CANNONBALL), Landings.allowed("superboy", false, true, false, false).map { it.first }.toSet())
        assertTrue(Landings.allowed("armour", false, true, false, false).map { it.first }.containsAll(listOf(Style.JETS, Style.CANNONBALL)))
        assertTrue(Style.PARACHUTE in Landings.allowed("mask", umbrella = true, powersOn = true, saver = false, canClimb = false).map { it.first })
        val noUmbrella = Landings.allowed("mask", umbrella = false, powersOn = true, saver = false, canClimb = false).map { it.first }
        assertTrue(Style.CHICKEN_FLAP in noUmbrella); assertFalse(Style.PARACHUTE in noUmbrella)
        // No vine swing for characters that can't climb.
        assertFalse(Style.VINE_SWING in Landings.allowed("monkey", false, true, false, canClimb = false).map { it.first })
    }

    @Test fun powersOffAndSaver() {
        val off = Landings.allowed("monkey", false, powersOn = false, saver = false, canClimb = true).map { it.first }
        assertTrue(off.none { it.power }); assertTrue(Style.PARACHUTE in off && Style.CANNONBALL in off)
        // Superboy without powers: a heroic thud, never a flight.
        assertEquals(listOf(Style.CANNONBALL), Landings.allowed("superboy", false, false, false, false).map { it.first })
        // Battery saver: short, effect-free variants only, no decision beat.
        for (id in listOf("monkey", "superboy", "armour", "mask")) {
            val s = Landings.allowed(id, false, true, saver = true, canClimb = true).map { it.first }
            assertTrue("$id $s", s.none { it.fx }); assertTrue(Style.SIMPLE in s)
        }
        assertEquals(0f, Landings.beat(Style.JETS, saver = true), 0f)
        assertTrue(Landings.beat(Style.JETS, saver = false) in 0.3f..0.8f)
        assertEquals(0f, Landings.beat(Style.SIMPLE, saver = false), 0f)
    }

    @Test fun shortDropsJustFall() {
        assertEquals(Style.SIMPLE, Landings.choose("monkey", false, true, false, true, dropPx = 100f * 3f, density = 3f, last = null))
        assertNotEquals(Style.SIMPLE, Landings.choose("monkey", false, true, false, true, dropPx = 900f * 3f, density = 3f, last = null, rnd = Random(3)))
    }

    @Test fun physics() {
        val d = 3f
        // Float styles cap their speed; cannonball doesn't.
        var v = 0f; repeat(200) { v = Landings.fallStep(Style.PARACHUTE, v, 2600f * d, 1f / 60f, d) }
        assertEquals(Style.PARACHUTE.terminalDp * d, v, 0.01f)
        var c = 0f; repeat(60) { c = Landings.fallStep(Style.CANNONBALL, c, 2600f * d, 1f / 60f, d) }
        assertTrue(c > 2000f * d)
        // Scream-jets: free fall high up, braking near the ground.
        assertEquals(3000f, Landings.screamBrake(3000f, 400f * d, d, 1f / 60f), 0f)
        assertTrue(Landings.screamBrake(3000f, 100f * d, d, 1f / 60f) < 3000f)
        assertTrue(Landings.screamBrake(200f, 10f * d, d, 1f / 60f) >= 120f * d)
        assertTrue(Landings.bounce(1000f) < 0f)
    }

    /** v4.5 bug: power landings never showed. Now about half of all landings are powered. */
    @Test fun powerLandingsAreFrequent() {
        for ((id, umb, min) in listOf(Triple("superboy", false, 0.7f), Triple("armour", false, 0.6f))) {
            val rnd = Random(11); var last: Style? = null; var power = 0
            val n = 3000
            repeat(n) { last = Landings.pick(Landings.allowed(id, umb, true, false, id == "monkey"), last, rnd); if (last!!.power) power++ }
            assertTrue("$id ${power.toFloat() / n}", power.toFloat() / n >= min)
        }
        // v5: Mask Man can't really fly, he *tries*: crow taxi / flaps / umbrella / ninja flip make up most of his drops.
        run {
            val rnd = Random(11); var last: Style? = null; var show = 0
            repeat(3000) { last = Landings.pick(Landings.allowed("mask", true, true, false, false), last, rnd); if (last != Style.CANNONBALL && last != Style.SNORE_FLOAT) show++ }
            assertTrue(show / 3000f >= 0.7f)
        }
        // Mask Man's own drops include his ninja flip, not only umbrella / snore / bounce.
        assertTrue(Style.NINJA_FLIP in Landings.allowed("mask", true, true, false, false).map { it.first })
    }

    @Test fun anyDropAboveAQuarterScreenGetsTheShow() {
        val d = 3f; val screen = 1200f    // small screen: 25 % = 300 px < 140 dp = 420 px
        assertEquals(300f, Landings.minDropPx(d, screen), 0.01f)
        assertNotEquals(Style.SIMPLE, Landings.decide("monkey", false, true, false, true, 0.26f * screen, d, null, screen, rnd = Random(2)).style)
        assertEquals(Style.SIMPLE, Landings.decide("monkey", false, true, false, true, 0.2f * screen, d, null, screen).style)
        // Big phone: 140 dp is well under a quarter, so even shorter drops get a show.
        assertEquals(420f, Landings.minDropPx(d, 2400f), 0.01f)
    }

    @Test fun whyIsExplained() {
        val p = Landings.decide("monkey", false, powersOn = false, saver = true, canClimb = true, dropPx = 2000f, density = 3f, last = Style.JETS, screenPx = 2400f, rnd = Random(4))
        assertTrue(p.why, p.why.contains("powers Off") && p.why.contains("battery saver") && p.why.contains("% of screen"))
        assertTrue(Landings.decide("monkey", false, true, false, true, 2000f, 3f, null, rushed = true).why.contains("waiting"))
        assertTrue(Landings.decide("monkey", false, true, false, true, 30f, 3f, null).why.startsWith("short drop"))
        assertEquals("scream jets", Landings.label(Style.SCREAM_JETS))
    }

    /** Landings don't look at power cooldowns: the only inputs are the setting, Settai's saver and the last style. */
    @Test fun notBlockedByCooldowns() {
        val rnd = Random(9); var last: Style? = null
        val seen = HashSet<Style>()
        repeat(400) { last = Landings.decide("monkey", false, true, false, true, 2000f, 3f, last, 2400f, rnd = rnd).style; seen.add(last!!) }
        assertTrue(seen.containsAll(listOf(Style.CAPE_FLOAT, Style.NINJA_FLIP)))
        assertTrue(seen.none { it == Style.JETS || it == Style.SCREAM_JETS || it == Style.ROCKET_ARC })   // v10.25.1
    }
}
