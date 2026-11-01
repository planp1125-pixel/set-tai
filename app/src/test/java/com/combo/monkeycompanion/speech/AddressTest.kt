package com.combo.monkeycompanion.speech

import org.junit.Assert.assertEquals
import org.junit.Test

/** v10.21 characters call you by your name, never a default "machan". */
class AddressTest {
    @Test fun usesTheNameWhenKnown() {
        assertEquals("Bye bye, Priya! 👋", Address.personalize("Bye bye, machan! 👋", "Priya"))
        assertEquals("Priya, fuel low! FUEL LOW!", Address.personalize("Machan, fuel low! FUEL LOW!", "Priya"))
        assertEquals("Good morning Priya", Address.personalize("Good morning machan", "priya"))
    }

    @Test fun onlyOnceInALine() {
        assertEquals("Hey Ravi! Drink water!", Address.personalize("Hey machan! Drink water machan!", "Ravi"))
    }

    @Test fun dropsItCleanlyWhenTheNameIsUnknown() {
        assertEquals("Bye bye! 👋", Address.personalize("Bye bye, machan! 👋", null))
        assertEquals("Fuel low! FUEL LOW!", Address.personalize("Machan, fuel low! FUEL LOW!", ""))
        assertEquals("Look 20ft away for 20 seconds 👀🌿", Address.personalize("Look 20ft away for 20 seconds, machan 👀🌿", null))
        assertEquals("Up at 6:30! Tap Yes.", Address.personalize("Up at 6:30, machan! Tap Yes.", null))
        assertEquals("Agreed 👍", Address.personalize("Agreed, machan 👍", null))
        assertEquals("Don't laugh! || ...okay, laugh a little.", Address.personalize("Don't laugh, machan! || ...okay, laugh a little.", null))
    }

    @Test fun leavesOtherLinesAlone() {
        assertEquals("Semma! Party time 🎉", Address.personalize("Semma! Party time 🎉", "Ravi"))
    }
}
