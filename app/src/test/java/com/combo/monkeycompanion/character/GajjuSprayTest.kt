package com.combo.monkeycompanion.character

import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.landing.LandingE2ETest
import com.combo.monkeycompanion.overlay.MonkeyView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v10.2: poke Gajju's belly -> trunk raised, water drops fly across the screen, then it hides behind an edge. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class GajjuSprayTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun bellyPokeSpraysWaterThenFliesOff() {
        ChatHub.init(ctx)
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.gags = false
        CharacterRegistry.select("gajju")
        val v = MonkeyView(ctx, LandingE2ETest.Host())
        v.layout(0, 0, 1080, 2340)
        v.debugFrames(60)
        v.debugSurf("FLOOR", frac = 0.3f)
        v.debugFrames(5)
        v.debugBellySpray()
        assertTrue(v.debugSpraying)
        v.debugFrames(30)
        assertTrue("water drops in the air", v.debugParticles.active)
        v.debugFrames(80)
        assertFalse("spray finished", v.debugSpraying)
        v.debugFrames(40)
        assertTrue("flew off behind the edge", v.debugHidden)
    }
}
