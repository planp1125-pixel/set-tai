package com.combo.monkeycompanion.v1036

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

/** v10.36: tilt rolls the character like a ball in screen space; edge buzz stops once settled. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class V1036Test {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun clean() {
        ChatHub.init(ctx); MindHub.init(ctx); ActivityScheduler.resetForTest(ctx); MindHub.focusBuddy.stop()
        ChatHub.settings.touchFeel = true; ChatHub.settings.flickThrow = false
    }
    @After fun after() {
        MindHub.focusBuddy.stop(); ActivityScheduler.resetForTest(ctx)
        ChatHub.settings.roam = true
        CharacterRegistry.select("monkey"); ChatHub.settings.touchFeel = true; ChatHub.settings.flickThrow = false
    }

    private fun view(id: String): MonkeyView {
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.gags = false; ChatHub.settings.livingReactions = true; ChatHub.settings.batterySaver = false
        ChatHub.settings.touchFeel = true; ChatHub.settings.flickThrow = false
        ChatHub.settings.roam = false
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

    private var tMs = 5_000L
    private fun feed(v: MonkeyView, x: Float, y: Float, n: Int) = repeat(n) {
        v.debugTiltFeed(x, y, 0.3f, tMs, 1f / 60f); v.debugFrames(1); tMs += 16
    }
    private fun ticks() = synchronized(Feel.log) { Feel.log.count { it == "drag_tick" } }

    @Test fun steepRightTiltDoesNotGrabAnAlreadyMidAirCharacter() {
        val v = view("monkey")
        v.debugPlace(300f, onFloor = false, y = 800f)
        val b0 = v.debugBox(); val floorTop = v.debugXY[2]
        feed(v, -9.6f, 0.4f, 90)
        val b = v.debugBox()
        assertEquals("v10.37 clarification: mid-air is not acquired by tilt", b0.left, b.left, 0.1f)
        assertTrue("not slammed to floor (top=${b.top} floor=$floorTop)", b.top < floorTop - 200f)
        assertFalse(v.debugFalling); assertFalse(v.debugHidden); assertFalse(v.debugTiltSliding)
    }

    @Test fun steepRightTiltLeavesCenterMidAirAlone() {
        val v = view("gajju")
        v.debugPlace(450f, onFloor = false, y = 900f)
        val x0 = v.debugBox().left
        feed(v, -9.6f, 0.4f, 120)
        assertEquals(x0, v.debugBox().left, 0.1f); assertFalse(v.debugHidden)
        assertFalse(v.debugTiltSliding)
    }

    @Test fun steepRightTiltFromFloorTravelsRight() {
        val v = view("chittu")
        v.debugPlace(400f, onFloor = true)
        val x0 = v.debugBox().left
        feed(v, -9.6f, 0.4f, 90)
        assertTrue("travels toward right edge (x0=$x0 now=${v.debugBox().left})", v.debugBox().left > x0 + 100f)
        assertFalse(v.debugHidden)
    }

    @Test fun uprightAgainDoesNotTakeOwnershipOfUnrelatedMidAirCharacter() {
        val v = view("monkey")
        v.debugPlace(300f, onFloor = false, y = 800f)
        feed(v, -9.6f, 0.4f, 40)
        feed(v, 0.2f, 9.8f, 240)
        assertFalse(v.debugTiltSliding)
        assertEquals("tilt never owned him, so must not drop him", 800f, v.debugBox().top, 0.1f)
    }

    @Test fun slidingTicksButSettledAtEdgeIsSilent() {
        val v = view("monkey")
        v.debugPlace(600f, onFloor = true)
        synchronized(Feel.log) { Feel.log.clear() }
        feed(v, 6.9f, 6.9f, 30)
        assertTrue("ticks while sliding", ticks() >= 1)
        v.debugPlace(0f, onFloor = true)
        feed(v, 6.9f, 6.9f, 90)
        synchronized(Feel.log) { Feel.log.clear() }
        feed(v, 6.9f, 6.9f, 150)   // ~2.4 s, many 480 ms windows
        assertEquals("no drag_tick once settled at edge ${synchronized(Feel.log){Feel.log.toList()}}", 0, ticks())
        assertTrue(v.debugBox().left <= 1f)
    }

    @Test fun armorAnnaShedsPlatesOnMarbleThenSnapsBack() {
        val v = view("armour"); val f = Finger(v)
        longPress(f)
        f.move((1..14).map { -14f * it to 16f * it })
        f.up()
        assertTrue(v.debugMarbleBounce)
        var peak = 0f
        repeat(30) { v.debugFrames(1); peak = maxOf(peak, v.debugArmorShed) }
        assertTrue("plates blew off (peak=$peak)", peak >= 0.99f)
        assertFalse("still visible while unsuited", v.debugHidden)
        repeat(60 * 12) { v.debugFrames(1) }
        assertEquals("suit snapped back", 0f, v.debugArmorShed, 0.001f)
        assertEquals(0, v.debugArmorShedPhase)
        assertTrue("clank on snap-back", felt("wall_hit"))
    }

    @Test fun otherCharactersNeverShed() {
        val v = view("superboy"); val f = Finger(v)
        longPress(f)
        f.move((1..14).map { -14f * it to 16f * it })
        f.up()
        repeat(60) { v.debugFrames(1); assertEquals(0f, v.debugArmorShed, 0f) }
    }

    @Test fun armourRendererDrawsInnerBodyWhenShed() {
        CharacterRegistry.select("armour"); val char = CharacterRegistry.current
        fun render(shed: Float): IntArray {
            val r = char.newRenderer() as com.combo.monkeycompanion.rig.ArmourRenderer
            r.shed = shed
            val bmp = android.graphics.Bitmap.createBitmap(300, 400, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            val rig = com.combo.monkeycompanion.rig.MonkeyRig()
            val pose = com.combo.monkeycompanion.rig.Pose()
            com.combo.monkeycompanion.rig.Animations.fill(pose, com.combo.monkeycompanion.brain.MonkeyState.IDLE, 1f, 1f, com.combo.monkeycompanion.rig.AnimContext())
            rig.layout(pose, 150f, 330f, 1f, 1f); r.draw(c, rig, pose, 0f)
            return IntArray(300 * 400).also { bmp.getPixels(it, 0, 300, 0, 0, 300, 400) }
        }
        val a = render(0f); val b = render(1f)
        val diff = a.indices.count { a[it] != b[it] }
        assertTrue("shed frame looks different ($diff px)", diff > 2000)
    }
}
