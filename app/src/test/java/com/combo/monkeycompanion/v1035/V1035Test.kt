package com.combo.monkeycompanion.v1035

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

/** v10.35: medium slingshot marble bounce stays a visible solid body for every character (Armour Anna fix). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class V1035Test {
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

    private fun marble(id: String) {
        val v = view(id); val f = Finger(v)
        longPress(f)
        f.move((1..14).map { -14f * it to 16f * it })
        assertTrue("$id sling", v.debugSling)
        f.up()
        assertTrue("$id marble mode", v.debugMarbleBounce)
        assertFalse("$id hover logic must be off the moment the marble launches", v.debugHovering)
        val h0 = v.debugWin()[3]
        var bounced = false; var maxH = h0; var hovered = false
        repeat(60 * 8) {
            v.debugFrames(1)
            if (v.debugMarbleBounce) { if (v.debugHovering) hovered = true; maxH = maxOf(maxH, v.debugWin()[3]) }
            if (v.debugMarbleBounces >= 1) bounced = true
        }
        assertFalse("$id must not use hover logic while marble-bouncing", hovered)
        assertTrue("$id bounced (count=${v.debugMarbleBounces})", bounced)
        assertFalse("$id must not hide", v.debugHidden)
        assertTrue("$id window must not grow by the jet pad (h0=$h0 max=$maxH)", maxH - h0 < 40)
    }

    @Test fun armourAnnaMarbleBouncesVisiblyWithoutHoverJets() = marble("armour")
    @Test fun superboyMarbleBouncesWithoutHover() = marble("superboy")
    @Test fun kuttiSatanMarbleBouncesWithoutHover() = marble("devil")
    @Test fun monkeyMarbleBouncesWithoutHover() = marble("monkey")
}
