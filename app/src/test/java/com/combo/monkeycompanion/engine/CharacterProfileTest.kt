package com.combo.monkeycompanion.engine

import com.combo.monkeycompanion.character.CharacterRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** M1.3: the profile table must give exactly the v9.5 `character.id == "…"` answers. */
class CharacterProfileTest {
    @Test fun profilesMatchTheV95IdChecks() {
        for (c in CharacterRegistry.all) {
            val p = CharacterProfiles.of(c)
            val catMouse = c.id == "billa" || c.id == "chittu"
            assertEquals(c.id, c.id == "monkey", p.climbsWithIK)
            assertEquals(c.id, catMouse, p.walksScreenEdges)
            assertEquals(c.id, catMouse, p.coyoteOnMidScreenDrop)
            assertEquals(c.id, if (catMouse) 0.55f else null, p.wallClimbChance)
            assertEquals(c.id, c.id == "mask", p.clumsyClimber)
            assertEquals(c.id, c.id == "superboy" || c.id == "armour", p.edgeCrashOnFlight)
            assertEquals(c.id, when (c.id) { "armour" -> 42f; "rocketgirl" -> 30f; else -> 18f }, p.hoverJetPadRig)   // v10.8 Rocket Girl rides a rocket
            assertSame(c.id, PhysicsProfile.DEFAULT, p.physics)
        }
    }

    @Test fun unknownCharactersGetTheDefault() {
        val custom = object : com.combo.monkeycompanion.character.Character("custom_123") {
            override val name = "X"; override val noun = "person"; override val tagline = ""
            override val locomotion = setOf(com.combo.monkeycompanion.character.Locomotion.WALK)
            override fun newRenderer() = throw UnsupportedOperationException()
            override val powers = emptyList<com.combo.monkeycompanion.brain.Superpower>()
            override fun identity(screen: String) = ""
            override fun body(w: com.combo.monkeycompanion.character.Wear, compact: Boolean) = ""
        }
        assertSame(CharacterProfiles.DEFAULT, CharacterProfiles.of(custom))
    }
}
