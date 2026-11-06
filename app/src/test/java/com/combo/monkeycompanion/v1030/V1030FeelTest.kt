package com.combo.monkeycompanion.v1030

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.agent.ActivityScheduler
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.brain.Gestures
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.engine.feel.TouchFeel
import com.combo.monkeycompanion.landing.LandingE2ETest
import com.combo.monkeycompanion.mind.MindHub
import com.combo.monkeycompanion.overlay.Feel
import com.combo.monkeycompanion.overlay.MonkeyView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** v10.30 physical toys: sharp wall hit, marble bounce, pinch-and-pop. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class V1030FeelTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun clean() {
        ChatHub.init(ctx); MindHub.init(ctx); ActivityScheduler.resetForTest(ctx); MindHub.focusBuddy.stop()
        ChatHub.settings.touchFeel = true; ChatHub.settings.flickThrow = false
    }
    @After fun after() {
        MindHub.focusBuddy.stop(); ActivityScheduler.resetForTest(ctx)
        CharacterRegistry.select("monkey"); ChatHub.settings.touchFeel = true; ChatHub.settings.flickThrow = false
    }

    private fun view(id: String): MonkeyView {
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.gags = false; ChatHub.settings.livingReactions = true; ChatHub.settings.batterySaver = false
        ChatHub.settings.touchFeel = true; ChatHub.settings.flickThrow = false
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

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun felt(name: String) = synchronized(Feel.log) { Feel.log.contains(name) }
    private fun lastLog(): List<String> = synchronized(Feel.log) { Feel.log.toList() }

    private class Finger(val v: MonkeyView) {
        val b = v.debugBox(); val w = v.debugWin()
        val lx = b.centerX() - w[0]; val ly = b.centerY() - w[1]
        val rx = b.centerX(); val ry = b.centerY()
        var clock = SystemClock.uptimeMillis()
        var last = 0f to 0f
        fun send(a: Int, dx: Float, dy: Float) {
            val e = MotionEvent.obtain(clock, clock, a, rx + dx, ry + dy, 0)
            e.offsetLocation(lx - rx, ly - ry)
            v.dispatchTouchEvent(e); e.recycle()
        }
        fun down() = send(MotionEvent.ACTION_DOWN, 0f, 0f)
        fun move(m: List<Pair<Float, Float>>, stepMs: Long = 16) {
            for (p in m) { clock += stepMs; send(MotionEvent.ACTION_MOVE, p.first, p.second); last = p; v.debugFrames(1) }
        }
        fun up(holdMs: Long = 5) { clock += holdMs; send(MotionEvent.ACTION_UP, last.first, last.second) }
    }

    private fun longPress(f: Finger, extraMs: Long = 0L) {
        f.down(); f.v.debugFrames(1)
        f.clock += Gestures.LONG_PRESS_MS + 60 + extraMs
        var left = Gestures.LONG_PRESS_MS + 60 + extraMs
        while (left > 0) { val s = minOf(100L, left); idle(s); f.v.debugFrames(1); left -= s }
    }

    /** Multi-touch helper: two fingers on the body (view coords via window offset). */
    private fun pinchEvent(v: MonkeyView, action: Int, x0: Float, y0: Float, x1: Float, y1: Float, clock: Long, pointerIndex: Int = 0): MotionEvent {
        val w = v.debugWin()
        val props = arrayOf(
            MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER },
            MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER }
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply { x = x0 - w[0]; y = y0 - w[1]; pressure = 1f; size = 1f },
            MotionEvent.PointerCoords().apply { x = x1 - w[0]; y = y1 - w[1]; pressure = 1f; size = 1f }
        )
        val encoded = action or (pointerIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        return MotionEvent.obtain(clock, clock, encoded, 2, props, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
    }

    @Test fun wallHitIsShortSharpNotThump() {
        val soft = TouchFeel.wallHit(0.3f, 0.08f)
        val hard = TouchFeel.wallHit(1f, 1f)
        assertEquals("wall_hit", soft.name)
        assertEquals("wall_hit", hard.name)
        assertTrue("short knock ${soft.totalMs}", soft.totalMs in 8..18)
        assertTrue("short knock ${hard.totalMs}", hard.totalMs in 8..18)
        assertTrue(hard.pulses[0].amp >= soft.pulses[0].amp)
        assertTrue(TouchFeel.thump(0.9f, 1f).totalMs > hard.totalMs)
        assertEquals("pop", TouchFeel.pop().name)
        assertTrue(TouchFeel.MARBLE_MAX_TENSION < 1f)
        assertTrue(TouchFeel.marbleRestitution(TouchFeel.weight("chittu")) >
            TouchFeel.marbleRestitution(TouchFeel.weight("gajju")))
    }

    @Test fun sharpHitLogsWallHitAfterCancelNotThump() {
        Feel.play(ctx, TouchFeel.slingSnap(0.85f))
        assertTrue(felt("sling_snap"))
        Feel.playSharpHit(ctx, 1f, 1f)
        val log = lastLog()
        assertTrue("wall_hit in $log", log.contains("wall_hit"))
        assertFalse("must not log thump for a glass slap: $log", log.contains("thump"))
        assertTrue(log.indexOf("wall_hit") > log.indexOf("sling_snap"))
    }

    @Test fun mediumSlingshotMarblesAndBouncesWithoutHideOrSplat() {
        val v = view("monkey"); val f = Finger(v)
        longPress(f)
        // ~99 dp pull → tension ~0.45 < 0.72 = marble
        f.move((1..14).map { -14f * it to 16f * it })
        assertTrue(v.debugSling)
        f.up()
        assertTrue("snap felt", felt("sling_snap"))
        assertTrue("marble mode", v.debugMarbleBounce)
        assertFalse("not comic impact yet", v.debugImpactKind != null && v.debugImpactPhase != "FLIGHT" && !v.debugMarbleBounce)
        var sawBounce = false
        repeat(60 * 8) {
            v.debugFrames(1)
            if (v.debugMarbleBounces >= 1) sawBounce = true
        }
        assertTrue("bounced at least once (count=${v.debugMarbleBounces})", sawBounce || v.debugMarbleBounces >= 1)
        assertFalse("must not hide", v.debugHidden)
        assertTrue("wall_hit on bounce ${lastLog()}", felt("wall_hit"))
    }

    @Test fun fullPowerSlingshotStillSplatsWithSharpHit() {
        // mid-screen so a full upward pull is not clamped back under the marble ceiling
        val v = view("monkey")
        v.debugPlace(400f, onFloor = false, y = 1400f)
        v.debugFrames(5)
        val f = Finger(v)
        longPress(f)
        // 22 * 30px = 660px ≈ SLING_MAX at xxhdpi → tension 1.0 (above MARBLE_MAX_TENSION 0.72)
        f.move((1..22).map { 0f to -30f * it })
        assertTrue("in sling after full pull", v.debugSling)
        f.up()
        assertTrue(felt("sling_snap"))
        assertFalse("full power is not marble (bounces=${v.debugMarbleBounces} impact=${v.debugImpactKind})", v.debugMarbleBounce)
        var splatted = false
        repeat(60 * 10) {
            v.debugFrames(1)
            if (v.debugImpactKind == "SPLAT" || v.debugImpactKind == "THWACK" || v.debugImpactKind == "PANCAKE") splatted = true
        }
        assertTrue("comic smash still happens (kind=${v.debugImpactKind} phase=${v.debugImpactPhase})", splatted)
        assertTrue("sharp glass slap ${lastLog()}", felt("wall_hit"))
    }

    @Test fun marbleTensionThresholdIsBelowFull() {
        assertTrue(TouchFeel.tension(100f) < TouchFeel.MARBLE_MAX_TENSION)
        assertTrue(TouchFeel.tension(150f) < TouchFeel.MARBLE_MAX_TENSION)   // 150/220 ≈ 0.68
        assertTrue(TouchFeel.tension(170f) > TouchFeel.MARBLE_MAX_TENSION)   // 170/220 ≈ 0.77
        assertEquals(1f, TouchFeel.tension(TouchFeel.SLING_MAX_DP), 0f)
    }

    @Test fun hideAimedFlickStillHidesAndDoesNotMarble() {
        val v = view("billa")
        v.debugPlace(40f, onFloor = true)
        v.debugRelease(-6500f, 0f)
        assertFalse("no marble", v.debugMarbleBounce)
        assertFalse("no comic splat", v.debugImpact)
        v.debugFrames(60)   // hideAnim must finish before hiddenTab flips
        assertTrue("hides at the edge", v.debugHidden)
        assertFalse("still no marble after hide", v.debugMarbleBounce)
    }

    @Test fun pinchSquashThenPopOnRelease() {
        val v = view("chittu")
        val b = v.debugBox()
        var clock = SystemClock.uptimeMillis()
        // finger 0 down on body
        run {
            val e = MotionEvent.obtain(clock, clock, MotionEvent.ACTION_DOWN, b.centerX(), b.centerY(), 0)
            val w = v.debugWin(); e.offsetLocation(-w[0].toFloat(), -w[1].toFloat())
            v.dispatchTouchEvent(e); e.recycle()
        }
        v.debugFrames(1)
        // second finger down (spread)
        clock += 30
        run {
            val e = pinchEvent(v, MotionEvent.ACTION_POINTER_DOWN, b.centerX() - 40f, b.centerY(), b.centerX() + 80f, b.centerY(), clock, 1)
            v.dispatchTouchEvent(e); e.recycle()
        }
        assertTrue("pinch started", v.debugPinch)
        // pinch in
        clock += 30
        run {
            val e = pinchEvent(v, MotionEvent.ACTION_MOVE, b.centerX() - 10f, b.centerY(), b.centerX() + 18f, b.centerY(), clock)
            v.dispatchTouchEvent(e); e.recycle()
        }
        v.debugFrames(2)
        assertTrue("squashed sx=${v.debugPinchSx} sy=${v.debugPinchSy}", v.debugPinchSx != 1f || v.debugPinchSy != 1f)
        assertTrue(v.debugPinchSy < 1f || v.debugPinchSx > 1f)
        // release second finger → pop
        clock += 30
        run {
            val e = pinchEvent(v, MotionEvent.ACTION_POINTER_UP, b.centerX() - 10f, b.centerY(), b.centerX() + 18f, b.centerY(), clock, 1)
            v.dispatchTouchEvent(e); e.recycle()
        }
        assertTrue("pop buzz ${lastLog()}", felt("pop"))
        assertFalse("pinch gesture ended", v.debugPinch)
        // spring settles toward 1
        repeat(40) { v.debugFrames(1) }
        assertEquals(1f, v.debugPinchSx, 0.05f)
        assertEquals(1f, v.debugPinchSy, 0.05f)
    }

    @Test fun oneFingerLongPressStillOpensSlingshot() {
        val v = view("monkey"); val f = Finger(v)
        longPress(f)
        f.move((1..14).map { -14f * it to 16f * it })
        assertTrue("slingshot still works one-finger", v.debugSling)
        assertFalse(v.debugPinch)
        f.up()
        assertTrue(felt("sling_snap"))
    }


    @Test fun tiltSlideMovesTowardDownhillEdge() {
        val v = view("monkey")
        v.debugPlace(500f, onFloor = true)
        val x0 = v.debugBox().left
        // ~45° left-side-low: gx≈6.9, gy≈6.9 — feed many samples so the filter settles
        var tMs = 2_000L
        repeat(90) {
            v.debugTiltFeed(6.9f, 6.9f, 0.4f, tMs, 1f / 60f)
            v.debugFrames(1)
            tMs += 16
        }
        assertTrue("slid left toward downhill edge (x0=$x0 now=${v.debugBox().left})", v.debugBox().left < x0 - 8f)
        assertFalse("must not hide from tilt slide", v.debugHidden)
        assertTrue("leaning/sliding", v.debugTiltSliding || v.debugTiltLean != 0f)
        assertFalse("not falling at ~45°", v.debugFalling)
    }

    @Test fun steepTiltStartsAFallAndLevelStopsSlide() {
        val v = view("gajju")
        v.debugPlace(400f, onFloor = true)
        val x0 = v.debugBox().left
        var tMs = 3_000L
        // ~90° on side
        repeat(50) {
            v.debugTiltFeed(9.6f, 0.4f, 0.3f, tMs, 1f / 60f)
            v.debugFrames(1)
            tMs += 16
        }
        // v10.36: on its side the character rolls toward the low (left) edge like a ball
        assertTrue("rolls left when tipped on side (x0=$x0 now=${v.debugBox().left})", v.debugBox().left < x0 - 40f)
        assertTrue("side roll is observable", v.debugTiltSliding)
        assertFalse("must not hide from tilt", v.debugHidden)
        // bring back upright
        repeat(40) {
            v.debugTiltFeed(0.2f, 9.8f, 0.3f, tMs, 1f / 60f)
            v.debugFrames(1)
            tMs += 16
        }
        // after landing / level, slide flag clears (may still be recovering fall)
        assertFalse("level clears tilt slide", v.debugTiltSliding)
    }

    @Test fun tableFaceDownIsNotAMidAirFall() {
        val v = view("chittu")
        v.debugPlace(400f, onFloor = true)
        val y0 = v.debugBox().top
        var tMs = 4_000L
        // screen flat on table: gz strongly negative — sleep-mask path, not tip-over fall
        repeat(100) {
            v.debugTiltFeed(0.1f, 0.1f, -9.8f, tMs, 1f / 60f)
            v.debugFrames(1)
            tMs += 16
        }
        assertFalse("table face-down must not start a tip-over fall", v.debugFalling && v.debugBox().top < y0 - 20f)
    }

    @Test fun onboardingFiveStepsIncludeSlingAndPinchAndSkip() {
        val r = com.combo.monkeycompanion.brain.Onboarding.Run()
        assertEquals(5, com.combo.monkeycompanion.brain.Onboarding.steps.size)
        assertNotNull(r.onGesture(com.combo.monkeycompanion.brain.Onboarding.Gesture.TAP))
        assertNotNull(r.onGesture(com.combo.monkeycompanion.brain.Onboarding.Gesture.DOUBLE_TAP))
        assertNotNull(r.onGesture(com.combo.monkeycompanion.brain.Onboarding.Gesture.HOLD))
        assertEquals(com.combo.monkeycompanion.brain.Onboarding.Gesture.SLING, r.current!!.expect)
        assertNotNull(r.onGesture(com.combo.monkeycompanion.brain.Onboarding.Gesture.SLING))
        assertEquals(com.combo.monkeycompanion.brain.Onboarding.Gesture.PINCH, r.current!!.expect)
        val skip = com.combo.monkeycompanion.brain.Onboarding.Run()
        skip.onGesture(com.combo.monkeycompanion.brain.Onboarding.Gesture.TAP)
        skip.skip()
        assertTrue(skip.done)
    }

}
