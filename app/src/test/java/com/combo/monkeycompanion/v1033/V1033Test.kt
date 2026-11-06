package com.combo.monkeycompanion.v1033

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.engine.expression.Expressions
import com.combo.monkeycompanion.engine.script.ScriptPlayer
import com.combo.monkeycompanion.overlay.ScriptShowHub
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v10.33: the shared wide-eyed "scream" face, and the 1-second scream beat after the glass cracks. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class V1033Test {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun screamIsWiderThanFearAndShocked() {
        val s = Expressions.byName("scream")!!; val f = Expressions.byName("fear")!!; val k = Expressions.byName("shocked")!!
        assertTrue(s.eye > f.eye && s.eye > k.eye)
        assertTrue(s.mouth > f.mouth && s.mouth > k.mouth)
        assertTrue(s.brow < 0f && s.browY < 0f)
    }

    @Test fun screamAppliesToEveryBuiltInBody() {
        assertEquals(13, CharacterRegistry.all.size)
        for (c in CharacterRegistry.all) {
            val p = Pose(); c.fill(p, MonkeyState.IDLE, 1f, 1f, AnimContext(), CharacterRegistry.wear(c))
            assertTrue(Expressions.apply("scream", p, 0f, 1f))
            assertEquals(c.id, 1.8f, p.eyeOpen, 0.001f)
            assertEquals(c.id, 1.2f, p.mouthOpen, 0.001f)
            assertEquals(c.id, -14f, p.browAngle, 0.001f)
            // it draws on every body
            val bmp = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888); val rig = MonkeyRig()
            rig.layout(p, 100f, 190f, 0.8f, 1f); c.newRenderer().also { it.wear = CharacterRegistry.wear(c) }.draw(Canvas(bmp), rig, p, 0f)
        }
    }

    @Test fun satanScreamDrawsBiggerEyesThanShocked() {
        val devil = CharacterRegistry.byId("devil")!!
        fun ink(face: String): Int {
            val p = Pose(); devil.fill(p, MonkeyState.IDLE, 1f, 1f, AnimContext(), CharacterRegistry.wear(devil)); Expressions.apply(face, p, 0f, 1f)
            p.bodyRot = 0f; p.headRot = 0f
            val bmp = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888); val rig = MonkeyRig(); rig.layout(p, 120f, 230f, 1f, 1f)
            devil.newRenderer().also { it.wear = CharacterRegistry.wear(devil) }.draw(Canvas(bmp), rig, p, 0f)
            var n = 0; for (x in 0 until 240) for (y in 0 until 240) { val c = bmp.getPixel(x, y); if ((c ushr 24) > 200 && (c and 0xFF) < 40 && ((c shr 8) and 0xFF) < 40) n++ }; return n
        }
        assertTrue(ink("scream") > ink("shocked"))
    }

    @Test fun scriptHasOneSecondScreamAfterTheCrack() {
        val text = ctx.assets.open(ScriptShowHub.DEFAULT).bufferedReader().readText()
        assertTrue(text.contains("both face scream for 1"))
        assertFalse(text.contains("AAAAH"))
        val p = ScriptPlayer(ScriptShowHub.load(ctx))
        p.advanceTo(15.6f); assertNotNull(p.crack); assertEquals("shocked", p.actor("satan")!!.faceAt(p.t))
        p.advanceTo(16.9f); for (r in listOf("satan", "angel")) assertEquals("scream", p.actor(r)!!.faceAt(p.t))
        assertNull(p.actor("satan")!!.saying(p.t))
        p.advanceTo(17.5f); assertNotEquals("scream", p.actor("angel")!!.faceAt(p.t))
        p.advanceTo(19.5f); assertEquals("sorry bro", p.actor("angel")!!.saying(p.t))
    }
}
