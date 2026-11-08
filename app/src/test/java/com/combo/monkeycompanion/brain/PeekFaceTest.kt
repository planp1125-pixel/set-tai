package com.combo.monkeycompanion.brain

import android.graphics.Bitmap
import android.graphics.Canvas
import com.combo.monkeycompanion.character.ArmourAnna
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.character.MaskMan
import com.combo.monkeycompanion.character.MonkeyCharacter
import com.combo.monkeycompanion.character.Wear
import com.combo.monkeycompanion.rig.ArmourPeek
import com.combo.monkeycompanion.rig.MaskManPeek
import com.combo.monkeycompanion.rig.PeekFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v4.5: every character draws its own edge-tab peek (no more monkey eyes on heroes), wardrobe included. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PeekFaceTest {
    private fun render(f: PeekFace, wear: Wear?, t: Float = 0.5f, bino: Float = 0f): IntArray {
        val b = Bitmap.createBitmap(160, 240, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); c.translate(40f, 0f)
        val s = PeekFace.State().apply { tw = 96f; cy = 110f; this.t = t; this.bino = bino; density = 3f }
        f.draw(c, wear, s)
        return IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
    }

    @Test fun everyCharacterHasItsOwnPeek() {
        val ids = CharacterRegistry.all.map { it.peek.characterId }
        assertEquals(CharacterRegistry.all.map { it.id }, ids)
        assertEquals(CharacterRegistry.all.size, CharacterRegistry.all.map { it.peek }.toSet().size)
        val images = CharacterRegistry.all.map { ch ->
            val px = render(ch.peek, Wear(ch))
            assertTrue("${ch.id} draws something", px.count { (it ushr 24) > 0 } > 2000)
            px.toList()
        }
        for (i in images.indices) for (j in i + 1 until images.size)
            assertNotEquals("${CharacterRegistry.all[i].id} vs ${CharacterRegistry.all[j].id}", images[i], images[j])
    }

    @Test fun wardrobeShowsInThePeek() {
        val w = Wear(MonkeyCharacter); w.allOff()
        val plain = render(MonkeyCharacter.peek, w).toList()
        w.set("shades", true)
        assertNotEquals(plain, render(MonkeyCharacter.peek, w).toList())
        val m = Wear(MaskMan); m.set("hat", false)
        val noHat = render(MaskMan.peek, m).toList()
        m.set("hat", true)
        assertNotEquals(noHat, render(MaskMan.peek, m).toList())
        assertTrue(CharacterRegistry.all.all { ch -> ch.peek.wardrobeShown.all { id -> ch.wardrobe.any { it.id == id } } })
    }

    @Test fun peeksAnimate() {
        // Bolt's visor flips up mid-peek (moustache), down at rest.
        assertEquals(0f, ArmourPeek.visorUp(-1f), 0f)
        assertEquals(1f, ArmourPeek.visorUp(2f), 0f)
        assertNotEquals(render(ArmourAnna.peek, Wear(ArmourAnna), t = 0.2f).toList(), render(ArmourAnna.peek, Wear(ArmourAnna), t = 2f).toList())
        // Masko's eyes change mood during a peek, calm at rest.
        assertEquals(MaskManPeek.Mood.CALM, MaskManPeek.mood(-1f))
        assertEquals(4, (0 until 8).map { MaskManPeek.mood(it * 0.6f) }.toSet().size)
        // Binoculars work for everyone.
        for (ch in CharacterRegistry.all) assertFalse(render(ch.peek, Wear(ch)).contentEquals(render(ch.peek, Wear(ch), bino = 1f)))
    }
}
