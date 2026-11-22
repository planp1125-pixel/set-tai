package com.combo.monkeycompanion.v1052

import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.brain.Distribution
import com.combo.monkeycompanion.brain.LongPressMenu
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.landing.LandingE2ETest
import com.combo.monkeycompanion.mind.MindHub
import com.combo.monkeycompanion.overlay.Feel
import com.combo.monkeycompanion.overlay.MonkeyView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v10.52: tilt rolls smoothly, bounces at most twice and then rests, buzzes only on those bumps; Play build has no Screen Smash. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class TiltAndPlayTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun clean() { ChatHub.init(ctx); MindHub.init(ctx); MindHub.neighbourMode = "SOLO" }
    @After fun after() { MindHub.neighbourMode = "DUO"; CharacterRegistry.select("monkey"); Distribution.screenCapture = true }

    private fun view(id: String = "monkey", buzz: Int = 1): MonkeyView {
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.gags = false; ChatHub.settings.batterySaver = false; ChatHub.settings.roam = false
        ChatHub.settings.touchFeel = true; ChatHub.settings.tiltBuzz = buzz
        CharacterRegistry.select(id)
        val v = MonkeyView(ctx, LandingE2ETest.Host())
        v.layout(0, 0, 1080, 2340)
        v.debugFrames(60)
        v.debugSurf("FLOOR", frac = 0.45f)
        v.debugFrames(10)
        v.debugQuiet = true
        synchronized(Feel.log) { Feel.log.clear() }
        return v
    }
    private var tMs = 5_000L
    private fun feed(v: MonkeyView, x: Float, y: Float, n: Int, each: () -> Unit = {}) = repeat(n) {
        v.debugTiltFeed(x, y, 0.3f, tMs, 1f / 60f); v.debugFrames(1); tMs += 16; each()
    }

    @Test fun aHardTiltBouncesAtMostTwiceThenRestsQuietly() {
        val v = view()
        v.debugPlace(400f, onFloor = true)
        feed(v, -9.6f, 0.4f, 240)      // on its side, right side down, 4 s
        assertTrue("at most two bump buzzes, got ${v.debugTiltBuzzes}", v.debugTiltBuzzes in 1..2)
        val x = v.debugBox().left; val b = v.debugTiltBuzzes
        feed(v, -9.6f, 0.4f, 60)
        assertEquals("resting against the edge", x, v.debugBox().left, 0.5f)
        assertEquals("no more buzzing while it rests", b, v.debugTiltBuzzes)
        assertFalse(v.debugHidden)
    }

    @Test fun tiltVibrationOffMeansNoBuzzAtAll() {
        val v = view(buzz = 0)
        v.debugPlace(400f, onFloor = true)
        feed(v, -9.6f, 0.4f, 240)
        assertEquals(0, v.debugTiltBuzzes)
    }

    @Test fun noFrozenMidAirBetweenGentleAndSteepTilt() {
        val v = view()
        v.debugPlace(400f, onFloor = true)
        val floorTop = v.debugXY[2]
        feed(v, 0.2f, -9.8f, 50)       // upside down: it leaves the floor
        assertTrue("left the floor", v.debugBox().top < floorTop - 100f)
        feed(v, 4.1f, 8.9f, 240)       // ~25°: used to freeze in mid-air here
        assertTrue("came back down to the floor (top=${v.debugBox().top}, floor=$floorTop)", v.debugBox().top > floorTop - 60f)
        feed(v, 0.2f, 9.8f, 60)
        assertFalse("upright on the floor: the roll is over", v.debugTiltOwns)
    }

    @Test fun movementIsSmoothFrameToFrame() {
        val v = view()
        v.debugPlace(300f, onFloor = true)
        var last = v.debugBox().left; var maxStep = 0f
        feed(v, -6.9f, 6.9f, 120) { val x = v.debugBox().left; maxStep = maxOf(maxStep, kotlin.math.abs(x - last)); last = x }
        assertTrue("no jumps (largest step ${maxStep}px)", maxStep < 40f)
    }

    @Test fun playBuildHasNoScreenSmash() {
        Distribution.screenCapture = true
        assertTrue("smash" in LongPressMenu.groupItems("g_games"))
        Distribution.screenCapture = false
        assertFalse("smash" in LongPressMenu.groupItems("g_games"))
        assertTrue("sling_game" in LongPressMenu.groupItems("g_games"))
        val ring = LongPressMenu.layout(LongPressMenu.defaults(), emptyList(), emptyList(), false)
        assertFalse("smash" in ring.main + ring.more)
    }
}
