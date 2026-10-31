package com.combo.monkeycompanion.v1014

import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.landing.LandingE2ETest
import com.combo.monkeycompanion.mind.MindHub
import com.combo.monkeycompanion.overlay.MonkeyView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class V1014OverlayTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun view(id: String): MonkeyView {
        ChatHub.init(ctx); MindHub.init(ctx); MindHub.campfire.stop(); MindHub.neighbourMode = "SOLO"
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.gags = false; ChatHub.settings.batterySaver = false
        CharacterRegistry.select(id)
        val v = MonkeyView(ctx, LandingE2ETest.Host())
        v.debugNoNightCampfire = true
        v.layout(0, 0, 1080, 2340)
        v.debugFrames(60); v.debugSurf("FLOOR", frac = 0.5f); v.debugQuiet = true
        v.debugFrames(10)
        return v
    }

    /** (a) swiped straight up (and steep diagonals): the rider's tilt and facing never flip-flop frame to frame. */
    @Test fun rocketGirlFlingFlightHasNoJitter() {
        for ((fvx, fvy) in listOf(0f to -2600f, 30f to -2400f, -40f to -2800f, 400f to -2600f)) {
            val v = view("rocketgirl")
            v.debugRocketLaunch(fvx, fvy)
            var lastRot = v.debugArcRot; var lastFace = v.controller.facing
            var lastD = 0f; var reversals = 0; var faceFlips = 0
            var lastX = v.debugBox().left; var lastDx = 0f; var xRev = 0
            var n = 0
            while (v.debugRocketFlyOn && n < 600) {
                v.debugFrames(1); n++
                val r = v.debugArcRot; val dr = r - lastRot
                val turned = v.controller.facing != lastFace
                if (!turned) assertTrue("tilt jump ${dr} at frame $n ($fvx,$fvy)", abs(dr) < 14f)
                if (abs(dr) > 0.5f && abs(lastD) > 0.5f && (dr > 0f) != (lastD > 0f)) reversals++
                if (abs(dr) > 0.5f) lastD = dr
                if (v.controller.facing != lastFace) faceFlips++
                lastFace = v.controller.facing; lastRot = r
                val x = v.debugBox().left; val dx = x - lastX
                if (abs(dx) > 0.5f && abs(lastDx) > 0.5f && (dx > 0f) != (lastDx > 0f)) xRev++
                if (abs(dx) > 0.5f) lastDx = dx
                lastX = x
            }
            assertTrue("tilt oscillates ($reversals reversals) for ($fvx,$fvy)", reversals <= 6)
            assertTrue("facing flip-flops ($faceFlips) for ($fvx,$fvy)", faceFlips <= 2)
            repeat(40) { v.debugFrames(1); val r = v.debugArcRot; assertTrue("upright eases back, no snap", abs(r - lastRot) < 14f); lastRot = r }
            assertTrue("x shakes ($xRev reversals) for ($fvx,$fvy)", xRev <= 4)
        }
    }

    /** (b) a line said mid-flight waits for the landing; (c) a chat reply that arrives mid-flight too. */
    @Test fun speechWaitsUntilSettled() {
        val v = view("rocketgirl")
        v.debugRocketLaunch(500f, -2200f); v.debugFrames(3)
        v.debugQuiet = false
        assertTrue(v.debugRocketFlyOn)
        v.say("Wheee!", 1500L)
        assertEquals("held while flying", 1, v.debugHeldLines)
        v.onReply("Here is your answer", true)
        assertEquals("reply held while flying", "Here is your answer", v.debugHeldReply)
        var n = 0
        while (v.debugRocketFlyOn && n < 900) { v.debugFrames(1); n++ }
        v.debugFrames(60)
        assertNull("reply shown after settling", v.debugHeldReply)
    }

    @Test fun staleHeldLinesAreDropped() {
        val v = view("monkey")
        v.debugQuiet = false
        // nothing moving: a line goes straight out
        v.say("Hello", 1200L)
        assertEquals(0, v.debugHeldLines)
    }
}
