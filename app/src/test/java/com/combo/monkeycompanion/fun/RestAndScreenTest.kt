package com.combo.monkeycompanion.`fun`

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.overlay.EnergyMeter
import com.combo.monkeycompanion.overlay.MonkeyView
import com.combo.monkeycompanion.overlay.OverlayHost
import com.combo.monkeycompanion.powers.SettaiClock
import com.combo.monkeycompanion.ui.RingActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class RestAndScreenTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    private class Host : OverlayHost {
        var x = 0; var y = 0; var w = 0; var h = 0
        var quits = 0
        override fun quitSettai() { quits++ }
        var through = false; var throughOn = 0
        override fun setTouchThrough(on: Boolean) { through = on; if (on) throughOn++ }
        var fx: ((android.graphics.Canvas) -> Unit)? = null; var fxShown = 0; var fxFrames = 0
        override fun showFx(draw: ((android.graphics.Canvas) -> Unit)?): Boolean { fx = draw; if (draw != null) fxShown++; return true }
        override fun invalidateFx() { fxFrames++ }
        override fun screenWidth() = 1080
        override fun screenHeight() = 2340
        override fun placeWindow(x: Int, y: Int, w: Int, h: Int) { this.x = x; this.y = y; this.w = w; this.h = h }
    }

    private fun newView(restSeconds: Int): Pair<MonkeyView, Host> {
        ChatHub.init(ctx)
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.restSeconds = restSeconds
        ChatHub.settings.batterySaver = false
        ChatHub.settings.gags = false
        val host = Host()
        val v = MonkeyView(ctx, host)
        v.layout(0, 0, 1080, 2340)
        return v to host
    }

    @Test fun autoRestHidesAndStopsTheFrameLoop() {
        val (v, host) = newView(30)
        v.debugFrames(30)
        assertFalse(v.debugHidden)
        // Let the real (Choreographer-paced) loop run. Never before the 30 s timeout; after it, as soon as the character
        // is free to rest (its own autonomous bits, e.g. a dance or a power picked by the shared Random stream, finish
        // first, so how long that takes depends on test order: wait in 1 s steps, bounded).
        val looper = shadowOf(android.os.Looper.getMainLooper())
        looper.idleFor(Duration.ofSeconds(28))
        assertFalse("not hidden before the rest timeout", v.debugHidden)
        var waited = 28
        while (!v.debugHidden && waited < 150) { looper.idleFor(Duration.ofSeconds(1)); waited++ }
        assertTrue("hid after rest timeout (waited $waited s) ${v.debugRestBlock}", v.debugHidden)
        assertTrue(v.debugResting)
        v.debugFrames(5)
        assertFalse("loop stopped while resting", v.debugLoopRunning)
        assertTrue(EnergyMeter.mode, EnergyMeter.mode.contains("0 fps"))
        // the window shrank to the little tab on the nearest side (left or right, wherever it wandered)
        assertTrue("tab ${host.x},${host.y} ${host.w}x${host.h}", host.w < 100 && (host.x == 0 || host.x + host.w >= 1080))

        // a peek runs the loop briefly, then stops again
        v.debugTabPeek()
        assertTrue(v.debugLoopRunning)
        repeat(4 * 60) { v.debugFrames(1) }
        assertFalse(v.debugLoopRunning)

        // double-tap the tab: comes back at once (single tap does too, after the double-tap window;
        // not asserted here because Robolectric's paused Choreographer skews the clock while idling)
        val t = SystemClock.uptimeMillis()
        for (k in 0..1) {
            v.onTouchEvent(MotionEvent.obtain(t, t + k * 120, MotionEvent.ACTION_DOWN, 10f, 40f, 0))
            v.onTouchEvent(MotionEvent.obtain(t, t + k * 120 + 50, MotionEvent.ACTION_UP, 10f, 40f, 0))
        }
        assertFalse(v.debugHidden)
        assertFalse(v.debugResting)
        assertTrue(v.debugLoopRunning)
    }

    @Test fun longPressAsksToQuitAndNoKeepsSettai() {
        val (v, host) = newView(0)
        v.debugFrames(5)
        v.debugAskQuit()
        assertTrue(v.debugQuitAsk)
        v.debugAnswerQuit(false)
        assertFalse(v.debugQuitAsk)
        repeat(60 * 6) { v.debugFrames(1) }
        assertEquals(0, host.quits)
    }

    @Test fun everyExitEndsWithQuit() {
        for (e in com.combo.monkeycompanion.brain.Exit.entries) {
            val (v, host) = newView(0)
            v.debugFrames(5)
            v.debugExit(e)
            var n = 0
            while (!v.debugExitDone && n < 60 * 8) { v.debugFrames(1); n++ }
            assertTrue("$e finished", v.debugExitDone)
            assertEquals(1, host.quits)
            assertTrue("$e took ${n / 60f}s", n / 60f in (e.seconds - 0.2f)..(e.seconds + 0.6f))
            if (e == com.combo.monkeycompanion.brain.Exit.ROCKET) assertEquals("rocket gets a tall window from the top", 0, host.y)
        }
    }

    @Test fun superpowerFlightsFlyAroundAndLandCleanly() {
        for (p in com.combo.monkeycompanion.brain.Superpowers.all) for (crash in listOf(false, true)) {
            val (v, host) = newView(0)
            v.debugFrames(10)
            v.debugPower(p, crash)
            assertTrue(v.debugPowerOn)
            var n = 0; var minY = Int.MAX_VALUE; var maxParts = 0; var maxW = 0
            while (v.debugPowerOn && n < 60 * 15) {
                v.debugFrames(1); n++
                minY = minOf(minY, host.y); maxParts = maxOf(maxParts, v.debugParticles.count); maxW = maxOf(maxW, host.w)
                if (host.fx != null && n % 20 == 0) host.fx!!.invoke(android.graphics.Canvas(android.graphics.Bitmap.createBitmap(1080, 2340, android.graphics.Bitmap.Config.ARGB_8888)))
            }
            // v4 fix (faded while flying): the body window is never made untouchable (Android 12+ clamps such a window to
            // 80% alpha); trails go to a separate FX window, opened for the flight and closed after.
            assertEquals("$p body window never made untouchable", 0, host.throughOn)
            assertTrue("$p FX window used", host.fxShown > 0 && host.fxFrames > 0)
            assertTrue("$p flight window stays small ($maxW)", maxW < 1080)
            v.debugFrames(120)
            assertEquals("$p FX window closed after landing", null, host.fx)
            assertFalse("$p crash=$crash ended", v.debugPowerOn)
            val flier = p == com.combo.monkeycompanion.brain.Superpower.RocketMonkey || p == com.combo.monkeycompanion.brain.Superpower.SuperBanana
            if (flier) assertTrue("$p flew up the screen (top=$minY)", minY < 1200)
            assertTrue("$p particles capped ($maxParts)", maxParts <= com.combo.monkeycompanion.rig.Particles.CAP)
            assertEquals("$p particles cleared", 0, v.debugParticles.count)
            assertEquals(MonkeyState.IDLE, v.controller.state)
            assertTrue("back to a small window", host.w < 600 && host.h < 900)
        }
    }

    @Test fun chatAndTagsAskForPowers() {
        assertEquals(com.combo.monkeycompanion.ai.BodyAction.FLY, com.combo.monkeycompanion.ai.ActionParser.requestedByUser("can you fly?"))
        assertEquals(com.combo.monkeycompanion.ai.BodyAction.FLY, com.combo.monkeycompanion.ai.ActionParser.requestedByUser("show your power machan"))
        assertEquals(com.combo.monkeycompanion.ai.BodyAction.CHASE_FLY, com.combo.monkeycompanion.ai.ActionParser.requestedByUser("chase that fly"))
        assertEquals(listOf(com.combo.monkeycompanion.ai.BodyAction.FLY), com.combo.monkeycompanion.ai.ActionParser.parse("[fly] Hold my banana!").actions)
        assertEquals(listOf(com.combo.monkeycompanion.ai.BodyAction.FLY), com.combo.monkeycompanion.ai.ActionParser.parse("[power] Watch!").actions)
        assertTrue(com.combo.monkeycompanion.ai.ActionParser.parse("Charge it (power bank) now").actions.isEmpty())
        assertEquals(null, com.combo.monkeycompanion.ai.ActionParser.requestedByUser("my battery power is low"))
    }

    @Test fun neverRestMeansNever() {
        val (v, _) = newView(0)
        // v10.27: real 16 ms frames. Since v10.26.2 auto-powers no longer get stuck, so over 5 idle minutes the character
        // really flies; at Robolectric's 1 ms "full speed" frame that was ~100k+ frames and timed the CI out.
        v.debugQuiet = true
        v.debugFrames(2)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMinutes(5))
        assertFalse(v.debugHidden)
    }

    @Test fun calmIdleIsCappedAt15fps() {
        val (v, _) = newView(0)
        // play long enough for the entrance-free idle to settle with no bubble
        repeat(60 * 12) { v.debugFrames(1) }
        // (a random hop / walk tween runs at full speed for a moment: wait for it to finish)
        var tries = 0
        do { v.debugPlay(com.combo.monkeycompanion.brain.MonkeyState.IDLE); v.debugFrames(2); tries++ }
        while (!EnergyMeter.mode.contains("fps cap") && tries < 300)
        assertTrue(EnergyMeter.mode, EnergyMeter.mode.contains("fps cap"))
        val m = Regex("(\\d+) fps cap").find(EnergyMeter.mode)!!.groupValues[1].toInt()
        assertTrue(EnergyMeter.mode, m in listOf(8, 15, 24, 30))
    }

    @Test fun ringScreenHoldsScreenOnOnlyWhileRingingAndClosesWhenStoppedElsewhere() {
        val i = Intent(ctx, RingActivity::class.java).putExtra(SettaiClock.EXTRA_ID, 5).putExtra("title", "⏳").putExtra("line", "Ding")
        val ctl = Robolectric.buildActivity(RingActivity::class.java, i).setup()
        val a = ctl.get()
        assertTrue((a.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0)
        // stopped from the notification's Stop button / swipe
        SettaiClock.stop(ctx, 5)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue("ring screen closes when the alarm is stopped elsewhere", a.isFinishing)
        ctl.pause().stop()
        assertEquals(0, a.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    @Test fun ringScreenAutoStops() {
        val i = Intent(ctx, RingActivity::class.java).putExtra(SettaiClock.EXTRA_ID, 6)
        val a = Robolectric.buildActivity(RingActivity::class.java, i).setup().get()
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(RingActivity.AUTO_STOP_MS + 1000))
        assertTrue(a.isFinishing)
    }

    @Test fun ringScreenLeavesWhenBackgrounded() {
        val i = Intent(ctx, RingActivity::class.java).putExtra(SettaiClock.EXTRA_ID, 7)
        val ctl = Robolectric.buildActivity(RingActivity::class.java, i).setup()
        ctl.pause().stop()
        assertTrue(ctl.get().isFinishing)
        assertEquals(0, ctl.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
