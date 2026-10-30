package com.combo.monkeycompanion.v1010

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.agent.ActivityScheduler
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.brain.FloatButton
import com.combo.monkeycompanion.brain.PowerShortcut
import com.combo.monkeycompanion.brain.SkyBurstPower
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.character.RocketGirl
import com.combo.monkeycompanion.landing.LandingE2ETest
import com.combo.monkeycompanion.mind.MindHub
import com.combo.monkeycompanion.overlay.MonkeyView
import com.combo.monkeycompanion.rig.SkyShow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/** v10.10: one double-tap = one whole power (Rocket Girl's sky show first), taps never launch her, the chat button's pressed look. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class V1010Test {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    @Before fun clean() { ChatHub.init(ctx); MindHub.init(ctx); ActivityScheduler.resetForTest(ctx); MindHub.focusBuddy.stop() }
    @After fun after() { MindHub.focusBuddy.stop(); ActivityScheduler.resetForTest(ctx) }

    private fun view(id: String, clearPrefs: Boolean = true, quiet: Boolean = true): MonkeyView {
        if (clearPrefs) ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.gags = false
        ChatHub.settings.livingReactions = true
        ChatHub.settings.batterySaver = false
        ChatHub.settings.doubleTapPower = true
        ChatHub.settings.chatButton = true
        CharacterRegistry.select(id)
        val v = MonkeyView(ctx, LandingE2ETest.Host())
        v.layout(0, 0, 1080, 2340)
        v.debugFrames(60)
        v.debugSurf("FLOOR", frac = 0.45f)
        v.debugFrames(10)
        v.debugQuiet = quiet
        return v
    }

    private var clock = 50_000L
    private fun send(v: MonkeyView, a: Int, rx: Float, ry: Float) {
        val w = v.debugWin()
        val e = MotionEvent.obtain(clock, clock, a, rx, ry, 0)
        e.offsetLocation(-w[0].toFloat(), -w[1].toFloat())
        v.dispatchTouchEvent(e); e.recycle()
    }
    private fun tapAt(v: MonkeyView, rx: Float, ry: Float, jitter: Float = 0f) {
        send(v, MotionEvent.ACTION_DOWN, rx, ry); clock += 20
        if (jitter != 0f) { send(v, MotionEvent.ACTION_MOVE, rx + jitter, ry - jitter); clock += 20 }
        send(v, MotionEvent.ACTION_UP, rx + jitter, ry - jitter)
    }
    private fun tapBody(v: MonkeyView, jitter: Float = 0f) { val b = v.debugBox(); tapAt(v, b.centerX(), b.top + b.height() * 0.62f, jitter) }
    private fun advance(ms: Long) { ShadowSystemClock.advanceBy(Duration.ofMillis(ms)); clock += ms }
    private fun doubleTap(v: MonkeyView) { tapBody(v); advance(90); tapBody(v) }

    @Test fun oneDoubleTapPlaysTheWholeSkyShowWithFiveBursts() {
        val v = view("rocketgirl")
        doubleTap(v)
        assertSame("first double-tap on Rocket Girl = her sky show", SkyBurstPower, v.debugLastShortcut)
        assertTrue(v.debugPowerOn)
        var flew = false; var frames = 0
        // no more taps: the one double-tap has to carry the show all the way to 5 bursts
        while (frames < 60 * 20 && (v.debugPowerOn || v.debugSkyShows.any { it.active })) {
            v.debugFrames(1); v.draw(Canvas(bmp)); frames++
            if (v.debugRocketFlyOn) flew = true
        }
        assertFalse("she never launched off on a flight", flew)
        assertTrue("shells launched: ${v.debugSkyShows.sumOf { it.launched }}", v.debugSkyShows.sumOf { it.launched } >= SkyShow.SHELLS)
        repeat(60 * 3) { v.debugFrames(1) }
        assertTrue("5 bursts from one double-tap (${v.debugBooms})", v.debugBooms >= SkyShow.SHELLS)
        assertSame("still exactly one power fired", SkyBurstPower, v.debugLastShortcut)
    }

    @Test fun firstDoubleTapOnRocketGirlIsAlwaysTheSkyShowThenItIsFavoured() {
        repeat(4) {
            val v = view("rocketgirl")
            doubleTap(v)
            assertSame("fresh install #$it", SkyBurstPower, v.debugLastShortcut)
        }
        // the picker itself: signature first, then about half the picks (and never twice in a row)
        val girl = CharacterRegistry.byId("rocketgirl")!!
        assertSame(SkyBurstPower, girl.signaturePower)
        val ps = PowerShortcut(11L)
        assertSame(SkyBurstPower, ps.pick(girl.powers, girl.signaturePower, seen = false))
        val monkey = CharacterRegistry.byId("monkey")!!
        val mp = PowerShortcut(3L); var sig = 0; var eligible = 0; var last: String? = null
        repeat(4000) {
            val canSig = last != monkey.signaturePower!!.id
            val p = mp.pick(monkey.powers, monkey.signaturePower, seen = true)!!
            assertNotEquals("no back-to-back repeat", last, p.id)
            if (canSig) { eligible++; if (p === monkey.signaturePower) sig++ }
            last = p.id
        }
        val share = sig.toFloat() / eligible
        assertTrue("signature weighted ~50% (was $share)", share in 0.42f..0.58f)
    }

    @Test fun otherCharactersAlsoPlayTheirSignaturePowerFirst() {
        for (id in listOf("monkey", "superboy")) {
            val ch = CharacterRegistry.byId(id) ?: continue
            val v = view(id)
            doubleTap(v)
            assertSame("$id first double-tap", ch.signaturePower, v.debugLastShortcut)
        }
        // v10.13: double-tap Billa starts the Billa-vs-Chittu chase show instead of a power
        val b = view("billa")
        doubleTap(b)
        org.junit.Assert.assertNotNull("billa double-tap starts the chase show", com.combo.monkeycompanion.overlay.ChaseShowHub.show)
        com.combo.monkeycompanion.overlay.ChaseShowHub.finish(null)
    }

    @Test fun singleTapsNeverLaunchAFlight() {
        val v = view("rocketgirl")
        repeat(80) { i ->
            tapBody(v, jitter = if (i % 3 == 0) 6f else 0f)   // some taps wobble a little, like a real finger
            repeat(25) { v.debugFrames(1); assertFalse("tap #$i launched a flight", v.debugRocketFlyOn); assertFalse("tap #$i started a power", v.debugPowerOn) }
            advance(700)
        }
    }

    @Test fun firstSelectionShowsTheOneTimeTip() {
        val v = view("rocketgirl", quiet = false)
        repeat(60 * 6) { v.debugFrames(1) }
        assertEquals(RocketGirl.TIP, v.debugTipShown)
        assertEquals("Double-tap me for fireworks! Swipe me to fly!", RocketGirl.TIP)
        val v2 = view("rocketgirl", clearPrefs = false, quiet = false)
        repeat(60 * 6) { v2.debugFrames(1) }
        assertEquals("only once", null, v2.debugTipShown)
    }

    @Test fun chatButtonIsFaintWhenIdleAndDarkWhilePressed() {
        assertTrue(FloatButton.IDLE_ALPHA in 0.15f..0.2f)
        val v = view("monkey")
        v.draw(Canvas(bmp))
        assertEquals("v10.11: faint from the very first frame (no 'just moved' solid state)", FloatButton.IDLE_ALPHA, v.debugChatFabAlpha, 0.001f)
        repeat(60 * 7) { v.debugFrames(1) }
        v.draw(Canvas(bmp))
        assertTrue("faded when idle (${v.debugChatFabAlpha})", v.debugChatFabAlpha < 0.25f)
        val pt = FloatArray(2); v.debugChatFab(pt); val w = v.debugWin()
        send(v, MotionEvent.ACTION_DOWN, pt[0] + w[0], pt[1] + w[1])
        v.debugFrames(2)
        assertTrue("pressed look on touch-down", v.debugChatFabPressed)
        assertEquals("fully opaque while pressed", 1f, v.debugChatFabAlpha, 0.01f)
        assertEquals("chat waits for the release", 0, v.debugChatOpened)
        clock += 60
        send(v, MotionEvent.ACTION_UP, pt[0] + w[0], pt[1] + w[1])
        assertFalse(v.debugChatFabPressed)
        assertEquals("opens chat on release", 1, v.debugChatOpened)
        assertEquals("v10.11: faint again on release", FloatButton.IDLE_ALPHA, v.debugChatFabAlpha, 0.001f)
        assertFalse(v.debugPowerOn)
    }

    @Test fun pressingTheChatButtonAndSlidingOffCancels() {
        val v = view("monkey")
        v.debugFrames(30); v.draw(Canvas(bmp))
        val pt = FloatArray(2); v.debugChatFab(pt); val w = v.debugWin()
        send(v, MotionEvent.ACTION_DOWN, pt[0] + w[0], pt[1] + w[1]); clock += 60
        send(v, MotionEvent.ACTION_UP, pt[0] + w[0] + 200f, pt[1] + w[1] + 200f)
        assertEquals(0, v.debugChatOpened)
        assertFalse(v.debugChatFabPressed)
    }
}
