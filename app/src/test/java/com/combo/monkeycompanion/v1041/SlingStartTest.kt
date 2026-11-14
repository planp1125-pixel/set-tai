package com.combo.monkeycompanion.v1041

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.agent.ActivityScheduler
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.engine.game.SlingGame
import com.combo.monkeycompanion.landing.LandingE2ETest
import com.combo.monkeycompanion.mind.MindHub
import com.combo.monkeycompanion.overlay.MonkeyView
import com.combo.monkeycompanion.overlay.SlingGameHub
import com.combo.monkeycompanion.overlay.SlingGameView
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
import org.robolectric.shadows.ShadowSettings
import java.time.Duration

/** v10.41: hold a moment and pull = slingshot; keep holding through 3-2-1 = the Slingshot game starts with that pull. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class SlingStartTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun clean() { ChatHub.init(ctx); MindHub.init(ctx); ActivityScheduler.resetForTest(ctx); ChatHub.settings.slingGame = true; ShadowSettings.setCanDrawOverlays(true) }
    @After fun after() { SlingGameHub.finish(ctx); SlingGameView.close(ctx); CharacterRegistry.select("monkey"); ChatHub.settings.slingGame = true; ShadowSettings.setCanDrawOverlays(false) }

    private fun view(id: String = "devil"): MonkeyView {
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.gags = false; ChatHub.settings.batterySaver = false
        CharacterRegistry.select(id)
        val v = MonkeyView(ctx, LandingE2ETest.Host())
        v.layout(0, 0, 1080, 2340)
        v.debugFrames(60); v.debugSurf("FLOOR", frac = 0.45f); v.debugFrames(10)
        v.debugQuiet = true
        return v
    }
    private fun idle(v: MonkeyView, ms: Long) { var left = ms; while (left > 0) { val s = minOf(100L, left); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(s)); v.debugFrames(1); left -= s } }

    private class Finger(val v: MonkeyView) {
        val b = v.debugBox(); val w = v.debugWin()
        val lx = b.centerX() - w[0]; val ly = b.centerY() - w[1]; val rx = b.centerX(); val ry = b.centerY()
        var last = 0f to 0f
        fun send(a: Int, dx: Float, dy: Float) {
            val now = SystemClock.uptimeMillis()
            val e = MotionEvent.obtain(now, now, a, rx + dx, ry + dy, 0)
            e.offsetLocation(lx - rx, ly - ry); v.dispatchTouchEvent(e); e.recycle()
        }
        fun down() = send(MotionEvent.ACTION_DOWN, 0f, 0f)
        fun move(m: List<Pair<Float, Float>>) { for (p in m) { send(MotionEvent.ACTION_MOVE, p.first, p.second); last = p; v.debugFrames(1) } }
        fun up() = send(MotionEvent.ACTION_UP, last.first, last.second)
    }
    private val pullDown = (1..12).map { 0f to 22f * it }

    @Test fun aQuickDragIsStillAPlainDrag() {
        val v = view(); val f = Finger(v)
        f.down(); f.move(pullDown)
        assertFalse("no hold = no slingshot", v.debugSling)
        f.up()
        assertFalse(SlingGameView.showing)
    }

    @Test fun aShortHoldThenPullIsTheSlingshotAndLettingGoEarlyIsAPlainFling() {
        val v = view(); val f = Finger(v)
        f.down(); idle(v, MonkeyView.SLING_HOLD_MS + 30)
        assertTrue(v.debugSlingArmed)
        f.move(pullDown)
        assertTrue("held a moment, then pulled = slingshot (before the long-press menu time)", v.debugSling)
        idle(v, 600)   // "3" has turned to "2": still before GO
        assertFalse(SlingGameView.showing)
        f.up()
        assertFalse(v.debugSling); assertFalse("let go before GO: no game", SlingGameView.showing)
        idle(v, 2000)
        assertFalse("and it does not start later either", SlingGameView.showing)
    }

    @Test fun holdingThroughTheCountdownStartsTheGameWithThisPull() {
        val v = view("armour"); val f = Finger(v)
        f.down(); idle(v, MonkeyView.SLING_HOLD_MS + 30)
        f.move(pullDown)
        idle(v, MonkeyView.SLING_COUNT_MS + 150)
        assertTrue("GO: the game is open", SlingGameView.showing)
        val gv = SlingGameView.current; assertNotNull(gv); gv!!.layout(0, 0, 1080, 2340)
        assertEquals("the character you pulled is the stone", "armour", gv.game.bulletId)
        assertTrue(v.debugSlingGameLive); assertFalse("the live band is gone", v.debugSling)
        f.move(listOf(0f to 300f, 10f to 320f))   // the same finger now pulls the game's band
        assertTrue(gv.game.pulling); assertTrue(gv.game.pullY > 0f)
        f.up()
        assertEquals("letting go fires the first shot", SlingGame.Phase.FLY, gv.game.phase)
        assertEquals(1, gv.game.shots)
        assertFalse(v.debugSlingGameLive)
    }

    @Test fun theSwitchOffKeepsItAPlainSlingshot() {
        ChatHub.settings.slingGame = false
        val v = view(); val f = Finger(v)
        f.down(); idle(v, MonkeyView.SLING_HOLD_MS + 30)
        f.move(pullDown)
        assertTrue(v.debugSling)
        idle(v, MonkeyView.SLING_COUNT_MS + 300)
        assertFalse(SlingGameView.showing); assertTrue("still holding the plain band", v.debugSling)
        f.up()
    }

    @Test fun everyCharacterHasItsOwnEscape() {
        fun t(id: String) = SlingGameHub.trickFor(CharacterRegistry.byId(id)!!)
        assertEquals(SlingGame.Trick.VANISH, t("devil")); assertEquals(SlingGame.Trick.VANISH, t("angel"))
        assertEquals(SlingGame.Trick.FLY_UP, t("superboy")); assertEquals(SlingGame.Trick.FLY_UP, t("armour"))
        assertEquals(SlingGame.Trick.DASH, t("chittu")); assertEquals(SlingGame.Trick.NONE, t("gajju"))
        assertEquals(SlingGame.Trick.HOP, t("monkey"))
    }
}
