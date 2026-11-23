package com.combo.monkeycompanion.v1053

import com.combo.monkeycompanion.mind.RustMind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v10.53: the Kotlin side of the offline Rust brain. On CI the same Rust library is built for the computer and
 * passed in (settai.mind.lib), so these calls go through the real JNI bridge; without it, everything returns null.
 */
class RustMindTest {
    private val hasLib = !System.getProperty("settai.mind.lib").isNullOrBlank() &&
        // a JVM can load a native library into only one class loader; if a Robolectric test got there first, skip
        (RustMind.available || !(RustMind.problem ?: "").contains("another class", ignoreCase = true))

    @Test fun answersInstantlyThroughTheRealBridge() {
        if (!hasLib) {
            if (System.getProperty("settai.mind.lib").isNullOrBlank()) {
                assertFalse(RustMind.available)
                assertNull(RustMind.respond("devil", "Tutty", "", "hi"))
            }
            return
        }
        assertTrue(RustMind.problem ?: "", RustMind.available)
        assertTrue(RustMind.version!!.startsWith("settai-mind"))
        val a = RustMind.respond("devil", "Tutty", "Mani", "Hiii da!")
        assertNotNull(a)
        assertEquals("greeting", a!!.reading.intent)
        assertEquals("wave", a.reading.act)
        assertTrue(a.line, a.line.isNotBlank() && !a.line.contains("{"))
        assertEquals("sad", RustMind.understand("I feel so sad 😢")!!.mood)
        assertEquals("greeting", RustMind.understand("வணக்கம்")!!.intent)
        assertTrue(RustMind.understand("fly da!")!!.power)
        // a character added in the future still gets an answer
        assertTrue(RustMind.respond("dragon_2027", "Dragu", "", "tell me a joke")!!.line.isNotBlank())
    }

    @Test fun doesNotRepeatItselfAndIsFast() {
        if (!hasLib) return
        val seen = HashSet<String>()
        repeat(4) { seen += RustMind.respond("devil", "Tutty", "", "tell me a joke", seed = 42L)!!.line }
        assertTrue("the same seed still gives fresh lines: $seen", seen.size >= 3)
        val us = RustMind.benchMicros()!!
        assertTrue("$us µs per message", us < 2000f)
    }
}
