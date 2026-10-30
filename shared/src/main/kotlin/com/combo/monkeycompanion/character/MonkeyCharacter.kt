package com.combo.monkeycompanion.character

import com.combo.monkeycompanion.ai.BodyAction
import com.combo.monkeycompanion.ai.Persona
import com.combo.monkeycompanion.brain.Superpowers
import com.combo.monkeycompanion.rig.MonkeyRenderer

/** The original: Settai the cheeky monkey (climbs, hangs, walks). Wardrobe: hat, sunglasses, scarf. */
object MonkeyCharacter : Character("monkey") {
    override val name get() = CharacterNames.MONKEY
    override val noun get() = "monkey"
    override val tagline get() = "The original cheeky monkey: climbs, hangs, juggles bananas."
    override val locomotion = setOf(Locomotion.WALK, Locomotion.CLIMB, Locomotion.HANG, Locomotion.FLY)
    override val wardrobe = listOf(
        WardrobeItem("hat", "Hat", defaultOn = false, options = listOf("top", "party", "lungi")),
        WardrobeItem("shades", "Sunglasses", defaultOn = false),
        WardrobeItem("scarf", "Scarf", defaultOn = false),
    )
    override fun newRenderer() = MonkeyRenderer()
    override val powers get() = Superpowers.monkey
    override fun identity(screen: String) = Persona.monkeyIdentity(screen)
    override fun body(w: Wear, compact: Boolean) = if (compact) Persona.BODY_SHORT else Persona.BODY
    override fun mapAction(a: BodyAction, w: Wear): BodyAction? = when (a) {
        BodyAction.SKATE, BodyAction.CHICKEN, BodyAction.GADGET -> null
        else -> a
    }
}
