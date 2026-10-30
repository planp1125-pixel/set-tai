package com.combo.monkeycompanion.character

import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.brain.SkyBurstPower
import com.combo.monkeycompanion.engine.touch.BodyPart
import com.combo.monkeycompanion.engine.touch.TouchKind
import com.combo.monkeycompanion.engine.touch.Verb
import com.combo.monkeycompanion.landing.LandingE2ETest
import com.combo.monkeycompanion.overlay.MonkeyView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v10.8: Rocket Girl in the real overlay view: living touch reactions play, never twice in a row, and the sky-burst launches fireworks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class RocketGirlOverlayTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun view(id: String): MonkeyView {
        ChatHub.init(ctx)
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.gags = false
        ChatHub.settings.livingReactions = true
        CharacterRegistry.select(id)
        val v = MonkeyView(ctx, LandingE2ETest.Host())
        v.layout(0, 0, 1080, 2340)
        v.debugFrames(60)
        v.debugSurf("FLOOR", frac = 0.4f)
        v.debugFrames(5)
        return v
    }

    @Test fun livingReactionsInTheOverlay() {
        for (id in listOf("rocketgirl", "monkey", "gajju", "billa")) {
            val v = view(id)
            var last: Verb? = null
            var living = 0
            for (i in 0 until 14) {
                val part = listOf(BodyPart.HEAD, BodyPart.BELLY, BodyPart.TAIL, BodyPart.PROP, BodyPart.EAR)[i % 5]
                val verb = v.debugLivingTap(part)
                if (verb != null && verb != Verb.LEGACY) { living++; assertNotEquals("$id repeat", last, verb); last = verb } else last = null
                v.debugFrames(70)
            }
            assertTrue("$id: living reactions played ($living)", living >= 5)
        }
    }

    @Test fun rocketGirlFlickLoopsAndSkyBurstLaunchesFireworks() {
        val v = view("rocketgirl")
        val flick = v.debugLivingTap(BodyPart.BELLY, TouchKind.FLICK)
        assertTrue("flick: $flick", flick == Verb.LOOP || flick == Verb.DIZZY)
        v.debugFrames(80)
        assertFalse(v.debugReacting)
        assertTrue(v.debugPower(SkyBurstPower))
        var sawFw = false; var maxSparks = 0
        repeat(420) { v.debugFrames(1); if (v.debugFireworks) sawFw = true; maxSparks = maxOf(maxSparks, v.debugFireworkCount) }
        assertTrue("fireworks launched", sawFw)
        assertTrue("sparks under cap: $maxSparks", maxSparks in 1..com.combo.monkeycompanion.rig.Fireworks.CAP)
    }

    @Test fun switchOffGivesTheClassicTaps() {
        val v = view("monkey")
        ChatHub.settings.livingReactions = false
        v.debugRefreshSettings()
        repeat(5) { assertTrue(v.debugLivingTap(BodyPart.HEAD) == Verb.LEGACY); v.debugFrames(60) }
    }
}
