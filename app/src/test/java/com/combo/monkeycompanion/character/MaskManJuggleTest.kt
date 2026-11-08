package com.combo.monkeycompanion.character

import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.engine.JuggleBalls
import com.combo.monkeycompanion.landing.LandingE2ETest
import com.combo.monkeycompanion.overlay.MonkeyView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v10.4 end to end: Masko juggles, drops balls when shaken, and a fast fling sends them all across the screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class MaskManJuggleTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private var clock = 10_000L

    private fun newView(): MonkeyView {
        ChatHub.init(ctx)
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.batterySaver = false; ChatHub.settings.gags = false; ChatHub.settings.roam = false
        CharacterRegistry.select("mask")
        val v = MonkeyView(ctx, LandingE2ETest.Host())
        v.layout(0, 0, 1080, 2340)
        v.debugFrames(60)
        v.debugSurf("FLOOR", frac = 0.5f)
        v.debugFrames(30)
        v.debugQuiet = true
        return v
    }

    private fun gesture(v: MonkeyView, moves: List<Pair<Float, Float>>, stepMs: Long, holdMs: Long) {
        val b = v.debugBox(); val w = v.debugWin()
        val lx = b.centerX() - w[0]; val ly = b.centerY() - w[1]; val rx = b.centerX(); val ry = b.centerY()
        fun send(a: Int, dx: Float, dy: Float) {
            val e = MotionEvent.obtain(clock, clock, a, rx + dx, ry + dy, 0)
            e.offsetLocation(lx - rx, ly - ry); v.dispatchTouchEvent(e); e.recycle()
        }
        send(MotionEvent.ACTION_DOWN, 0f, 0f)
        var last = 0f to 0f
        for (m in moves) { clock += stepMs; send(MotionEvent.ACTION_MOVE, m.first, m.second); last = m; v.debugFrames(1) }
        clock += holdMs
        send(MotionEvent.ACTION_UP, last.first, last.second)
    }

    @Test fun juggleThenShakeThenFling() {
        val v = newView()
        assertTrue("juggling while idle", v.debugJuggling)
        assertEquals(3, v.debugBalls.heldCount)

        // Shake him hard left-right for a while: balls slip out.
        val shake = (1..90).map { i -> (if (i % 6 < 3) 260f else -260f) * ((i % 3) + 1) / 3f to -60f }
        gesture(v, shake, stepMs = 16, holdMs = 300)
        assertTrue("shaking dropped at least one ball (held ${v.debugBalls.heldCount})", v.debugBalls.heldCount < 3)

        // Wait for everything to settle, fade, and fresh balls to come back.
        repeat(60 * 12) { v.debugFrames(1) }
        v.debugSurf("FLOOR", frac = 0.5f); v.debugFrames(60 * 3)
        assertEquals("picked up fresh balls", 3, v.debugBalls.heldCount)

        // A fast fling to the right: every ball flies off that way.
        // v10.14 de-flaked: a ball may already have hit the right wall and bounced (vx < 0) by the time we look,
        // so "flew right" = moving right OR already further right than where it was held.
        val x0 = FloatArray(3) { v.debugBalls.x[it] }
        gesture(v, (1..3).map { i -> 160f * i to -20f * i }, stepMs = 16, holdMs = 5)
        assertEquals("all balls flung", 0, v.debugBalls.heldCount)
        val b = v.debugBalls
        assertTrue("flying right", (0 until 3).all { b.state[it] != JuggleBalls.State.LOOSE || b.vx[it] > 0f || b.x[it] > x0[it] + 1f })
        repeat(60) { v.debugFrames(1) }
        assertTrue("still bouncing around after the fling (loop kept running while hidden)", b.looseActive || b.allGone)
        repeat(60 * 15) { v.debugFrames(1) }
        assertTrue("balls settled and faded", !b.looseActive)
    }
}
