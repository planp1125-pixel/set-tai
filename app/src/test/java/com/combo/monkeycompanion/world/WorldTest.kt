package com.combo.monkeycompanion.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WorldTest {
    @Test fun gradualEnergyAndWeatherTransitions() {
        val clock = WorldClock()
        val t0 = 1_000_000L
        clock.onBattery(15, t0)
        assertTrue("low battery makes world hungry", clock.state.hungry)
        assertTrue("hungry slows movement: ${clock.state.speedMultiplier}", clock.state.speedMultiplier < 0.75f)

        // Plugging in starts feeding time; energy rises gradually over ticks
        val eBefore = clock.state.energy
        clock.onCharging(true, t0 + 10_000L)
        assertTrue(clock.state.feeding)
        assertFalse(clock.state.hungry)
        clock.onTick(t0 + 130_000L, 14 * 60)
        assertTrue("energy rises gradually while feeding: $eBefore -> ${clock.state.energy}", clock.state.energy > eBefore)

        // Temperature transitions gradually to hot
        clock.onTemp(42f, t0 + 140_000L)
        clock.onTick(t0 + 380_000L, 14 * 60 + 4)
        assertTrue("42C makes world hot: ${clock.state.heat}", clock.state.hot)
    }

    @Test fun placesAndVisitorsDictionary() {
        assertEquals(WorldPlaces.Kind.MARKET, WorldPlaces.of("com.whatsapp").kind)
        assertEquals("the Whatsapp market", WorldPlaces.of("com.whatsapp").worldName)
        assertEquals(WorldPlaces.Kind.CINEMA, WorldPlaces.of("com.google.android.youtube").kind)
        assertEquals(WorldPlaces.Kind.MIRROR_STUDIO, WorldPlaces.of("com.google.android.GoogleCamera").kind)
        assertEquals(WorldPlaces.Kind.PLAYGROUND, WorldPlaces.of("com.supercell.clashofclans", isGame = true).kind)
        assertEquals(WorldPlaces.Kind.WATCHTOWER, WorldPlaces.of("com.google.android.apps.maps").kind)
        assertEquals(WorldPlaces.Kind.BAZAAR, WorldPlaces.of("in.swiggy.android").kind)
        assertEquals(WorldPlaces.Kind.VAULT, WorldPlaces.of("com.snapwork.hdfc").kind)
        assertEquals(WorldPlaces.Kind.ENGINE_ROOM, WorldPlaces.of("com.android.settings").kind)
        assertEquals(WorldPlaces.Kind.LIBRARY, WorldPlaces.of("com.android.chrome").kind)

        for (id in listOf("monkey", "superboy", "armour", "mask")) {
            for (k in WorldPlaces.Kind.entries) {
                val p = WorldPlaces.Place("pkg.${k.name}", "App", com.combo.monkeycompanion.mind.AppCat.OTHER, k)
                assertTrue(WorldPlaces.placeLine(id, p, Random(1)).isNotBlank())
            }
            assertTrue(WorldPlaces.visitorLine(id, "com.whatsapp", "Amma: dinner ready", 1).isNotBlank())
            assertTrue(WorldPlaces.visitorLine(id, "com.whatsapp", " Amma", 6).isNotBlank())
            assertTrue(WorldPlaces.bridgeLine(id, true).isNotBlank())
            assertTrue(WorldPlaces.bridgeLine(id, false).isNotBlank())
        }
    }

    @Test fun nightActivityGeneratesMorningTraceAndJsonRoundTrip() {
        val clock = WorldClock()
        val tOff = 10_000_000L
        clock.onScreenOff(tOff)
        // Unlock after only 5 minutes: no night trace
        assertNull(clock.onUnlock(tOff + 5 * 60_000L, 22 * 60, listOf("monkey", "armour"), Random(7)))

        // Screen off for 7 hours overnight -> morning trace generated
        clock.onScreenOff(tOff)
        val tMorning = tOff + 7 * 3600_000L
        val trace = clock.onUnlock(tMorning, 7 * 60 + 30, listOf("monkey", "armour"), Random(7))
        assertNotNull(trace)
        assertTrue(trace!!.awayMinutes >= 420)
        assertTrue(trace.line.isNotBlank())
        assertEquals(1, clock.recentTraces.size)

        // Trust grows with gentle touches and drops with rough shakes
        val tr0 = clock.state.visitorTrust
        clock.onInteraction(gentle = true, kind = "pet", now = tMorning + 1000L)
        assertTrue(clock.state.visitorTrust > tr0)
        val tr1 = clock.state.visitorTrust
        clock.onInteraction(gentle = false, kind = "hard_shake", now = tMorning + 2000L)
        assertTrue(clock.state.visitorTrust < tr1)

        // Prompt summary and JSON persistence
        clock.onAppSwitch("com.whatsapp", false, tMorning + 3000L)
        clock.onNotification("com.whatsapp", tMorning + 4000L)
        val prompt = clock.state.promptLine()
        assertTrue(prompt, prompt.startsWith("World:") && prompt.contains("Whatsapp market") && prompt.contains("visitor trust"))

        val json = WorldStore.encodeJson(clock, "TRIO")
        assertTrue(json.startsWith("{") && json.endsWith("}"))
        val restored = WorldClock()
        val mode = WorldStore.decodeJson(restored, json)
        assertEquals("TRIO", mode)
        assertEquals(clock.state.energy, restored.state.energy, 0.002f)
        assertEquals(clock.state.visitorTrust, restored.state.visitorTrust, 0.002f)
        assertEquals(clock.recentTraces.first().line, restored.recentTraces.first().line)
    }
}

class PopulationAndDirectorTest {
    @Test fun populationRolesRespondToWorldConditions() {
        val st = WorldState()
        assertEquals(listOf("monkey"), Population.activeIds("monkey", Population.Mode.SOLO, st))

        // Low battery / hungry -> Bolt (ENGINEER) joins Settai
        st.batteryPct = 14; st.energy = 0.14f; st.charging = false
        val hungryDuo = Population.activeIds("monkey", Population.Mode.DUO, st)
        assertEquals(listOf("monkey", "armour"), hungryDuo)

        // Notification flood -> Zoom (GUARD) joins Settai
        st.batteryPct = 80; st.energy = 0.8f; st.visitorsWaiting = 6; st.busyness = 0.85f
        val floodDuo = Population.activeIds("monkey", Population.Mode.DUO, st)
        assertEquals(listOf("monkey", "superboy"), floodDuo)

        // Trio mode returns 3 distinct creatures
        val trio = Population.activeIds("monkey", Population.Mode.TRIO, st)
        assertEquals(3, trio.size)
        assertEquals(3, trio.toSet().size)
    }

    @Test fun directorBanterRespectsGatesAndCoversAllScenes() {
        val dir = Director(cooldownMs = 60_000L)
        val st = WorldState(batteryPct = 15, energy = 0.15f)
        val t0 = 5_000_000L

        // Blocked by typing, calls, fullscreen, DND, quiet hours, or SOLO mode
        assertNull(dir.pickScene("monkey", Population.Mode.SOLO, st, 600, t0))
        assertNull(dir.pickScene("monkey", Population.Mode.DUO, st, 600, t0, keyboard = true))
        assertNull(dir.pickScene("monkey", Population.Mode.DUO, st, 600, t0, inCall = true))
        assertNull(dir.pickScene("monkey", Population.Mode.DUO, st, 600, t0, fullscreen = true))
        assertNull(dir.pickScene("monkey", Population.Mode.DUO, st, 600, t0, dnd = true))
        assertNull(dir.pickScene("monkey", Population.Mode.DUO, st, 600, t0, quietHours = true))

        // Allowed when calm in DUO mode -> picks low-battery huddle
        val s1 = dir.pickScene("monkey", Population.Mode.DUO, st, 600, t0, rnd = Random(1))
        assertNotNull(s1)
        assertEquals(Director.Kind.LOW_BATTERY_HUDDLE, s1!!.kind)
        assertTrue(s1.leadLine.isNotBlank() && s1.buddyLine.isNotBlank())

        // Cooldown blocks immediate repeat unless forced
        assertNull(dir.pickScene("monkey", Population.Mode.DUO, st, 600, t0 + 10_000L))
        assertNotNull(dir.pickScene("monkey", Population.Mode.DUO, st, 600, t0 + 70_000L))

        // Every banter kind produces valid 2-turn lines
        for (k in Director.Kind.entries) {
            val sc = dir.buildScene(k, "monkey", "armour", st)
            assertTrue("$k lead", sc.leadLine.isNotBlank())
            assertTrue("$k buddy", sc.buddyLine.isNotBlank())
        }

        val (reactState, reactQuip) = dir.sideReaction("armour", "want a banana?", "yes!", st)
        assertNotNull(reactState)
        assertTrue(reactQuip.isNotBlank())
    }
}
