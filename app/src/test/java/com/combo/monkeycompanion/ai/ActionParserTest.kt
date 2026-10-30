package com.combo.monkeycompanion.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionParserTest {
    @Test fun bracketTag() {
        val r = ActionParser.parse("Sure, watch this! [dance]")
        assertEquals("Sure, watch this!", r.text)
        assertEquals(listOf(BodyAction.DANCE), r.actions)
    }

    @Test fun caseAndVariants() {
        val r = ActionParser.parse("[DANCE!] Let's go! (spins) <wave>")
        assertEquals("Let's go!", r.text)
        assertEquals(listOf(BodyAction.DANCE, BodyAction.SPIN, BodyAction.WAVE), r.actions)
    }

    @Test fun starsStageDirection() {
        val r = ActionParser.parse("*does a little dance* Ta-da! *giggles*")
        assertEquals("Ta-da!", r.text)
        assertEquals(listOf(BodyAction.DANCE, BodyAction.GIGGLE), r.actions)
    }

    @Test fun actionPrefix() {
        val r = ActionParser.parse("Okay okay. [action: jump] Wheee!")
        assertEquals("Okay okay. Wheee!", r.text)
        assertEquals(listOf(BodyAction.JUMP), r.actions)
    }

    @Test fun unknownBracketsKept() {
        val r = ActionParser.parse("Your battery is at 80% (pretty good) [1]")
        assertEquals("Your battery is at 80% (pretty good) [1]", r.text)
        assertEquals(emptyList<BodyAction>(), r.actions)
    }

    @Test fun unknownStarsRemovedButNoAction() {
        val r = ActionParser.parse("*adjusts tiny hat* Hello!")
        assertEquals("Hello!", r.text)
        assertEquals(emptyList<BodyAction>(), r.actions)
    }

    @Test fun duplicatesCollapsed() {
        val r = ActionParser.parse("[dance] Yay! [dance] [happy]")
        assertEquals(listOf(BodyAction.DANCE, BodyAction.HAPPY), r.actions)
        assertEquals("Yay!", r.text)
    }

    @Test fun streamingHidesHalfTag() {
        assertEquals("Sure!", ActionParser.visible("Sure! [dan"))
        assertEquals("Sure!", ActionParser.visible("Sure! *danc"))
        assertEquals("I'm sad :(", ActionParser.visible("I'm sad :("))
    }

    @Test fun userFallback() {
        assertEquals(BodyAction.DANCE, ActionParser.requestedByUser("Settai, dance for me!"))
        assertEquals(BodyAction.DANCE, ActionParser.requestedByUser("naach na"))
        assertEquals(BodyAction.DANCE, ActionParser.requestedByUser("konjam aadu da"))
        assertEquals(BodyAction.HIDE, ActionParser.requestedByUser("go hide"))
        assertEquals(BodyAction.SPIN, ActionParser.requestedByUser("do a spin"))
        assertNull(ActionParser.requestedByUser("what's the weather like?"))
    }
}
