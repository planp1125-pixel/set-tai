package com.combo.monkeycompanion.v5

import android.graphics.Bitmap
import android.graphics.Color
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.character.Wear
import com.combo.monkeycompanion.fun5.ClipRenderer
import com.combo.monkeycompanion.fun5.Yuv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Clip "encoder" side that can run on the JVM: offscreen frame generation (MediaCodec itself needs a device). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ClipRendererTest {
    private fun distinct(b: Bitmap): Int {
        val s = HashSet<Int>(); var y = 0
        while (y < b.height) { var x = 0; while (x < b.width) { s += b.getPixel(x, y); x += 7 }; y += 7 }
        return s.size
    }
    private fun diff(a: Bitmap, b: Bitmap): Int {
        var n = 0; var y = 0
        while (y < a.height) { var x = 0; while (x < a.width) { if (a.getPixel(x, y) != b.getPixel(x, y)) n++; x += 5 }; y += 5 }
        return n
    }

    @Test fun everyCharacterRendersMovingFrames() {
        assertEquals(0, ClipScript.W % 16); assertEquals(0, ClipScript.H % 16)
        for (ch in CharacterRegistry.all) {
            val r = ClipRenderer(ch, Wear(ch), ClipScript.Bit.DANCE)
            val f0 = r.frame(10); val f1 = r.frame(40); val f2 = r.frame(80)
            assertEquals(ClipScript.W, f0.width); assertEquals(ClipScript.H, f0.height)
            assertTrue("${ch.id} frame is busy", distinct(f1) > 40)
            assertTrue("${ch.id} moves 10→40", diff(f0, f1) > 50)
            assertTrue("${ch.id} moves 40→80", diff(f1, f2) > 50)
        }
    }

    @Test fun yuvConversionIsSane() {
        val b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888); b.eraseColor(Color.WHITE)
        val px = IntArray(32 * 32); b.getPixels(px, 0, 32, 0, 0, 32, 32)
        val yPlane = ByteArray(32 * 32); val u = ByteArray(16 * 16); val v = ByteArray(16 * 16)
        Yuv.toI420(px, 32, 32, yPlane, u, v)
        assertTrue((yPlane[0].toInt() and 0xFF) > 230)
        assertTrue(kotlin.math.abs((u[0].toInt() and 0xFF) - 128) < 6)
    }

    /** CLIP_FRAMES=1 dumps all frames of the sample clip (the box has no MediaCodec; ffmpeg encodes them). */
    @Test fun dumpSampleFrames() {
        if (System.getProperty("clip.frames").isNullOrEmpty()) return
        val dir = com.combo.monkeycompanion.TestPaths.out("clipframes").apply { deleteRecursively(); mkdirs() }
        val ch = CharacterRegistry.all.first()
        val r = ClipRenderer(ch, Wear(ch).apply { set("shades", true) }, ClipScript.Bit.DANCE)
        for (i in 0 until ClipScript.FRAMES) FileOutputStream(File(dir, "f%03d.png".format(i))).use { r.frame(i).compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("CLIP_FRAMES_WRITTEN ${dir.absolutePath}")
    }
}
