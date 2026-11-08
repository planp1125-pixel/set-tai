package com.combo.monkeycompanion.skit

import com.combo.monkeycompanion.brain.LongPressMenu
import com.combo.monkeycompanion.engine.skit.SkitBus
import com.combo.monkeycompanion.engine.skit.SkitConductor
import com.combo.monkeycompanion.engine.skit.SkitCue
import com.combo.monkeycompanion.engine.skit.SkitLibrary
import com.combo.monkeycompanion.world.CrewRules
import com.combo.monkeycompanion.world.Director
import com.combo.monkeycompanion.world.Population
import com.combo.monkeycompanion.world.WorldState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class V1012CrewAndSkitTest {
    private val st = WorldState()
    private val D = Population.Mode.DUO
    private val T = Population.Mode.TRIO

    @Test fun rosterHasEveryCharacter() {
        val r = CrewRules.roster
        for (id in listOf("monkey", "armour", "superboy", "mask", "billa", "chittu", "gajju", "rocketgirl")) assertTrue(id, id in r)
        assertTrue("gajju" in Population.allIds && "rocketgirl" in Population.allIds)
    }

    @Test fun gajjuAndRocketGirlCanBePartners() {
        assertEquals(listOf("monkey", "gajju"), CrewRules.resolve("monkey", D, "gajju", null, st))
        assertEquals(listOf("superboy", "rocketgirl", "gajju"), CrewRules.resolve("superboy", T, "rocketgirl", "gajju", st))
    }

    @Test fun pickedPartnerIsNeverOverriddenByAutoPairing() {
        // Billa used to force Chittu; now the user's pick wins.
        assertEquals(listOf("billa", "superboy"), CrewRules.resolve("billa", D, "superboy", null, st))
        assertEquals(listOf("chittu", "mask", "gajju"), CrewRules.resolve("chittu", T, "mask", "gajju", st))
    }

    @Test fun pinsSurviveModeSwitchAndAutoFillsEmptySlots() {
        assertEquals(listOf("billa"), CrewRules.resolve("billa", Population.Mode.SOLO, "gajju", "mask", st))
        assertEquals(listOf("billa", "gajju"), CrewRules.resolve("billa", D, "gajju", "mask", st))
        assertEquals(listOf("billa", "gajju", "mask"), CrewRules.resolve("billa", T, "gajju", "mask", st))
        val auto = CrewRules.resolve("billa", T, null, null, st)
        assertEquals(3, auto.size); assertEquals("billa", auto[0]); assertEquals(3, auto.distinct().size)
        assertEquals("chittu", auto[1]) // auto still prefers Chittu when the slot is free
    }

    @Test fun leadOrUnknownPartnerIgnored() {
        assertEquals(2, CrewRules.resolve("monkey", D, "monkey", null, st).distinct().size)
        assertFalse("ghost" in CrewRules.resolve("monkey", D, "ghost", null, st))
    }

    @Test fun directorBuddyOnlyFromOnScreenCrew() {
        for (k in Director.Kind.entries) {
            val d = Director(cooldownMs = 0L)
            val s = d.pickScene("gajju", D, st.copy(visitorsWaiting = 3), 600, 1000L, forceKind = k, rnd = Random(1), active = listOf("gajju", "rocketgirl"))
            assertNotNull(k.name, s)
            assertTrue("${k.name} buddy ${s!!.buddyId}", s.buddyId == "rocketgirl")
            assertFalse(s.leadLine.contains("Pogo!"))
        }
        assertNull(Director(0L).pickScene("gajju", D, st, 600, 1000L, active = listOf("gajju")))
    }

    @Test fun sixPlusSkitsAndCastingOnlyFromCrew() {
        assertTrue(SkitLibrary.all.size >= 6)
        val c = SkitConductor(7)
        val run = c.start(listOf("gajju", "mask"))!!
        assertTrue(run.cast.all { it in setOf("gajju", "mask") })
        assertNull(c.start(listOf("gajju")))
        assertTrue(c.eligible(listOf("billa", "chittu")).any { it.id == "cheese_heist" })
        assertFalse(c.eligible(listOf("billa", "mask")).any { it.id == "cheese_heist" })
        assertTrue(c.eligible(listOf("rocketgirl", "armour")).any { it.id == "firework_prank" })
        // every pair of built-ins has at least one skit
        val ids = Population.allIds
        for (a in ids) for (b in ids) if (a != b) assertTrue("$a+$b", c.eligible(listOf(a, b)).isNotEmpty())
    }

    @Test fun noRepeatWithinFourWhenChoicesExist() {
        val c = SkitConductor(42)
        val crew = listOf("monkey", "billa", "chittu")
        val seen = (1..40).map { c.start(crew)!!.skit.id }
        val n = c.eligible(crew).size
        assertTrue(n >= 3)
        for (i in 1 until seen.size) assertTrue(seen.toString(), seen[i] != seen[i - 1])
    }

    @Test fun deterministicWithSeedAndSeparateRng() {
        val a = SkitConductor(5).start(listOf("billa", "chittu"))!!
        val b = SkitConductor(5).start(listOf("billa", "chittu"))!!
        assertEquals(a.cues.map { it.line }, b.cues.map { it.line })
        // The global Random stream is untouched by skits.
        val g1 = Random(9).nextInt(); SkitConductor(1).start(listOf("monkey", "mask")); val g2 = Random(9).nextInt()
        assertEquals(g1, g2)
    }

    @Test fun endingsBranchByWeight() {
        val c = SkitConductor(11)
        val counts = HashMap<String, Int>()
        repeat(3000) { val r = c.start(listOf("billa", "chittu"), "cheese_heist")!!; val k = r.cues.last().line; counts[k] = (counts[k] ?: 0) + 1 }
        assertEquals(3, counts.size)
        val pancake = counts.entries.first { "pancake" in it.key }.value
        assertTrue("pancake $pancake", pancake in 1350..1950)
    }

    @Test fun runEmitsCuesInOrderAndFinishes() {
        val r = SkitConductor(3).start(listOf("monkey", "superboy"), "banana_heist")!!
        assertTrue(r.cues.none { "{" in it.line })
        val got = ArrayList<SkitCue>()
        var guard = 0
        while (!r.done && guard++ < 10_000) got += r.step(1 / 60f)
        assertEquals(r.cues, got)
        assertTrue(r.totalSec in 5f..30f)
    }

    @Test fun busDeliversOnlyToRegisteredActor() {
        val got = ArrayList<String>()
        SkitBus.register("billa") { got += it.line }
        val r = SkitConductor(2).start(listOf("billa", "chittu"))!!
        val delivered = r.cues.count { SkitBus.post(it) }
        SkitBus.unregister("billa")
        assertEquals(r.cues.count { it.actorId == "billa" }, delivered)
        assertEquals(delivered, got.size)
    }

    @Test fun skitMenuItemExists() { assertNotNull(LongPressMenu.Item.of("skit")) }
}
