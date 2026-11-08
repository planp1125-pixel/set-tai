package com.combo.monkeycompanion.v1017

import com.combo.monkeycompanion.brain.PowerPrefs
import com.combo.monkeycompanion.brain.VanishPortalPower
import com.combo.monkeycompanion.brain.Landings
import com.combo.monkeycompanion.character.CharacterRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class PowerPickerTest {
    @After fun reset() { PowerPrefs.store = null }

    @Test fun devilHasEverythingByDefault() {
        val d = CharacterRegistry.byId("devil")!!
        val ids = PowerPrefs.enabled(d).map { it.id }
        assertEquals("vanish", ids.first())
        for (x in listOf("swoop", "fireball", "devjuggle", "laser", "dancespin", "boost", "devilhop")) assertTrue(x, x in ids)
    }

    @Test fun builtInsAreFixed() {
        PowerPrefs.store = { setOf("jets") }
        val m = CharacterRegistry.byId("monkey")!!
        assertEquals(m.powers, PowerPrefs.enabled(m))
        assertFalse(PowerPrefs.choosable(m))
    }

    @Test fun devilChoiceKeepsMainFirstAndGatesJetLandings() {
        val d = CharacterRegistry.byId("devil")!!
        PowerPrefs.store = { if (it == "devil") setOf("fireball") else null }
        assertEquals(listOf(VanishPortalPower.id, "fireball"), PowerPrefs.enabled(d).map { it.id })
        assertTrue(Landings.pool("devil", false).none { it.first == Landings.Style.JETS || it.first == Landings.Style.ROCKET_ARC })
        PowerPrefs.store = { if (it == "devil") setOf("boost") else null }
        assertTrue(Landings.pool("devil", false).none { it.first == Landings.Style.JETS })   // v10.25.1: no jet landings for Tutty
    }
}
