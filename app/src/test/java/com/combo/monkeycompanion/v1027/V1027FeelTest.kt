package com.combo.monkeycompanion.v1027

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.agent.ActivityScheduler
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.brain.Gestures
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.engine.feel.RubTracker
import com.combo.monkeycompanion.engine.feel.TouchFeel
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import kotlin.math.PI
import kotlin.math.sin

/** v10.27 Touch Feel: feel them in your hand (weight, slingshot, heartbeat, purr, tickle, shake, pokes, landings). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class V1027FeelTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun clean() { ChatHub.init(ctx); MindHub.init(ctx); ActivityScheduler.resetForTest(ctx); MindHub.focusBuddy.stop() }
    @After fun after() { MindHub.focusBuddy.stop(); ActivityScheduler.resetForTest(ctx); CharacterRegistry.select("monkey"); ChatHub.settings.touchFeel = true }

    private fun view(id: String): MonkeyView {
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.gags = false; ChatHub.settings.livingReactions = true; ChatHub.settings.batterySaver = false
        ChatHub.settings.touchFeel = true
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

    /** Finger on the middle of the body: [hold] ms still first (long-press), then [moves] (dx, dy screen px), then lift. */
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
        fun move(m: List<Pair<Float, Float>>, stepMs: Long = 16) { for (p in m) { clock += stepMs; send(MotionEvent.ACTION_MOVE, p.first, p.second); last = p; v.debugFrames(1) } }
        fun up(holdMs: Long = 5) { clock += holdMs; send(MotionEvent.ACTION_UP, last.first, last.second) }
    }

    private fun longPress(f: Finger, extraMs: Long = 0L) {
        f.down(); f.v.debugFrames(1)
        f.clock += Gestures.LONG_PRESS_MS + 60 + extraMs
        // keep the system clock with the finger (Robolectric idles the delayed long-press / heartbeat runnables)
        val wait = Gestures.LONG_PRESS_MS + 60 + extraMs
        var left = wait; while (left > 0) { val s = minOf(100L, left); idle(s); f.v.debugFrames(1); left -= s }
    }

    @Test fun weightsAndBuzzes() {
        assertTrue(TouchFeel.weight("gajju") > TouchFeel.weight("monkey"))
        assertTrue(TouchFeel.weight("monkey") > TouchFeel.weight("chittu"))
        assertEquals("grab_light", TouchFeel.grab(TouchFeel.weight("chittu")).name)
        assertEquals("grab_heavy", TouchFeel.grab(TouchFeel.weight("gajju")).name)
        assertTrue(TouchFeel.grab(1f).pulses[0].amp > TouchFeel.grab(0.5f).pulses[0].amp)
        assertTrue("gentle landing = a soft bump", TouchFeel.thump(0.05f, 1f).pulses[0].amp < TouchFeel.thump(0.9f, 1f).pulses[0].amp)
        assertTrue("heavier lands harder", TouchFeel.thump(0.8f, 1f).pulses[0].amp > TouchFeel.thump(0.8f, 0.08f).pulses[0].amp)
        assertTrue(TouchFeel.bpm(asleep = false, scared = true, w = 0.5f) > TouchFeel.bpm(asleep = false, scared = false, w = 0.5f))
        assertTrue(TouchFeel.bpm(asleep = true, scared = false, w = 0.5f) < TouchFeel.bpm(asleep = false, scared = false, w = 0.5f))
        val hb = TouchFeel.heartbeat()
        assertEquals(hb.timings.size, hb.amps.size)
        // the rub reader: petting, tickling, shaking and a plain drag
        fun kinds(pts: List<Triple<Float, Float, Float>>): RubTracker {
            val r = RubTracker(); r.reset(pts[0].first, pts[0].second, pts[0].third)
            for (p in pts.drop(1)) r.feed(p.first, p.second, p.third); return r
        }
        val pet = kinds((0..150).map { i -> val t = i / 60f; Triple(30f * sin(t * PI.toFloat() / 0.35f), 0f, t) })
        assertTrue("pet strokes ${pet.strokes}", pet.strokes >= 4 && pet.shakes == 0)
        val shake = kinds((0..90).map { i -> val t = i / 60f; Triple(120f * sin(t * PI.toFloat() / 0.12f), 0f, t) })
        assertTrue("shakes ${shake.shakes}", shake.shakes >= 6 && shake.strokes == 0)
        val tickle = kinds((0..60).map { i -> val t = i / 60f; Triple(20f * sin(t * PI.toFloat() / 0.07f), 0f, t) })
        assertTrue("tickles ${tickle.tickles}", tickle.tickles >= 4)
        val drag = kinds((0..60).map { i -> Triple(0f, -8f * i, i / 60f) })
        assertEquals(0, drag.strokes + drag.shakes + drag.tickles)
    }

    @Test fun grabFeelsHeavyOrLight() {
        for ((id, want) in listOf("gajju" to "grab_heavy", "chittu" to "grab_light")) {
            val v = view(id); val f = Finger(v)
            f.down(); f.move((1..12).map { 0f to -60f * it }); f.up(300)
            assertTrue("$id: $want felt ${Feel.log}", felt(want))
            assertTrue("$id: drag ticks", felt("drag_tick"))
        }
    }

    @Test fun slingshotFiresEveryCharacter() {
        for (c in CharacterRegistry.all) {
            val v = view(c.id); val f = Finger(v)
            val y0 = v.debugBox().top
            longPress(f)
            f.move((1..14).map { -14f * it to 16f * it })   // pull down-left
            assertTrue("${c.id}: in the slingshot", v.debugSling)
            assertTrue("${c.id}: notches clicked", felt("sling_tick"))
            f.up()
            assertFalse("${c.id}: band let go", v.debugSling)
            assertTrue("${c.id}: snap felt", felt("sling_snap"))
            assertTrue("${c.id}: flies off", v.debugImpactOn || v.debugBox().top < y0 - 5f)
            repeat(60 * 6) { v.debugFrames(1) }
        }
    }

    @Test fun smallPullJustPutsItBack() {
        val v = view("monkey"); val f = Finger(v)
        val b0 = v.debugBox()
        longPress(f)
        f.move((1..5).map { -20f * it to 0f })   // ~38 dp: past the touch slop, short of a shot
        assertTrue(v.debugSling)
        f.up()
        assertFalse(v.debugSling)
        assertFalse("not fired", felt("sling_snap"))
        assertEquals(b0.left, v.debugBox().left, 4f)
    }

    @Test fun keepHoldingFeelsTheHeartbeat() {
        val v = view("billa"); val f = Finger(v)
        longPress(f, extraMs = TouchFeel.HEART_HOLD_MS + 200)
        assertTrue("heartbeat on", v.debugHeartbeat)
        assertTrue("heartbeat felt ${Feel.log}", felt("heartbeat"))
        idle(2000)
        assertTrue("keeps beating", synchronized(Feel.log) { Feel.log.count { it == "heartbeat" } } >= 2)
        f.up(0)
        assertFalse("stops when you let go", v.debugHeartbeat)
    }

    @Test fun pettingPurrsAndStaysPut() {
        val v = view("billa"); val f = Finger(v)
        val b0 = v.debugBox()
        f.down()
        // gentle strokes: 90 px (≈34 dp) back and forth, slowly
        f.move((1..150).map { i -> val t = i / 60f; 90f * sin(t * PI.toFloat() / 0.4f) to 0f })
        assertTrue("petting", v.debugPetting)
        assertTrue("purr felt ${Feel.log}", felt("purr"))
        f.up(200)
        v.debugFrames(5)
        assertEquals("stays where it was", b0.left, v.debugBox().left, 6f)
    }

    @Test fun tickleGiggles() {
        val v = view("chittu"); val f = Finger(v)
        f.down()
        f.move((1..60).map { i -> val t = i / 60f; 60f * sin(t * PI.toFloat() / 0.07f) to 0f })
        assertTrue("tickling", v.debugTickling)
        assertTrue("tickle felt", felt("tickle"))
        f.up(200)
    }

    @Test fun shakingRattlesThenDizzy() {
        val v = view("gajju"); val f = Finger(v)
        f.down()
        f.move((1..90).map { i -> val t = i / 60f; 330f * sin(t * PI.toFloat() / 0.12f) to -150f })
        assertTrue("rattle felt", felt("rattle"))
        f.up(300)
        assertTrue("dizzy stars", v.debugDizzy)
    }

    @Test fun pokesAreFelt() {
        val v = view("monkey")
        val b = v.debugBox(); val w = v.debugWin()
        var clock = SystemClock.uptimeMillis()
        fun tap(x: Float, y: Float) {
            for (a in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val e = MotionEvent.obtain(clock, clock, a, x, y, 0); e.offsetLocation(-w[0].toFloat(), -w[1].toFloat())
                v.dispatchTouchEvent(e); e.recycle(); clock += 40
            }
            clock += 900; idle(900); v.debugFrames(30)
        }
        tap(b.centerX(), b.top + b.height() * 0.62f)
        assertTrue("a poke is felt ${Feel.log}", felt("squish") || felt("tap") || felt("boop"))
    }

    @Test fun touchFeelOffIsSilent() {
        val v = view("gajju"); ChatHub.settings.touchFeel = false
        val f = Finger(v); f.down(); f.move((1..10).map { 0f to -30f * it }); f.up(300)
        repeat(60 * 4) { v.debugFrames(1) }
        assertTrue("no buzz ${Feel.log}", synchronized(Feel.log) { Feel.log.isEmpty() })
    }
}
