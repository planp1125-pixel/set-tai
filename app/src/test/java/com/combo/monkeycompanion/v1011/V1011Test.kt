package com.combo.monkeycompanion.v1011

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.agent.ActivityScheduler
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.brain.ChatBubbleSpot
import com.combo.monkeycompanion.brain.FloatButton
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.landing.LandingE2ETest
import com.combo.monkeycompanion.mind.MindHub
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
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/** v10.11: the chat button is always faint (dark only under a finger) and never sits over a face. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class V1011Test {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private val d get() = ctx.resources.displayMetrics.density
    private val faint = FloatButton.IDLE_ALPHA

    @Before fun clean() { ChatHub.init(ctx); MindHub.init(ctx); ActivityScheduler.resetForTest(ctx); MindHub.focusBuddy.stop(); MindHub.campfire.stop(); MindHub.neighbourMode = "SOLO" }
    @After fun after() { MindHub.focusBuddy.stop(); MindHub.campfire.stop(); ActivityScheduler.resetForTest(ctx); MindHub.neighbourMode = "DUO" }

    private fun view(id: String, frac: Float = 0.45f): MonkeyView {
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.gags = false
        ChatHub.settings.livingReactions = true
        ChatHub.settings.batterySaver = false
        ChatHub.settings.doubleTapPower = true
        ChatHub.settings.chatButton = true
        CharacterRegistry.select(id)
        val v = MonkeyView(ctx, LandingE2ETest.Host())
        v.debugNoNightCampfire = true
        v.layout(0, 0, 1080, 2340)
        v.debugFrames(60)
        v.debugSurf("FLOOR", frac = frac)
        v.debugFrames(10)
        v.debugQuiet = true
        return v
    }

    private var clock = 80_000L
    private fun send(v: MonkeyView, a: Int, rx: Float, ry: Float) {
        val w = v.debugWin()
        val e = MotionEvent.obtain(clock, clock, a, rx, ry, 0)
        e.offsetLocation(-w[0].toFloat(), -w[1].toFloat())
        v.dispatchTouchEvent(e); e.recycle()
    }
    private fun advance(ms: Long) { ShadowSystemClock.advanceBy(Duration.ofMillis(ms)); clock += ms }
    private fun frame(v: MonkeyView, n: Int = 1) { v.debugFrames(n); v.draw(Canvas(bmp)) }
    private fun fabScreen(v: MonkeyView): FloatArray { val p = FloatArray(2); v.debugChatFab(p); val w = v.debugWin(); return floatArrayOf(p[0] + w[0], p[1] + w[1]) }

    private fun assertFaint(v: MonkeyView, what: String) {
        if (!v.debugChatFabVisible) return
        faintChecked++
        assertEquals("faint $what (alpha ${v.debugChatFabAlpha})", faint, v.debugChatFabAlpha, 0.001f)
        assertFalse(v.debugChatFabPressed)
    }

    /** The button circle never touches the head box, and sits fully inside its window and the screen. */
    private var checked = 0
    private var faintChecked = 0
    private fun assertClear(v: MonkeyView, what: String) {
        frame(v)
        if (!v.debugChatFabVisible) return
        checked++
        val p = FloatArray(2); v.debugChatFab(p); val w = v.debugWin()
        val face = RectF(); v.debugFaceRect(face)
        val r = v.debugChatFabRadius
        val dist = ChatBubbleSpot.distToBox(p[0], p[1], face.left, face.top, face.right, face.bottom)
        assertTrue("$what: button (${p[0]},${p[1]}) r=$r over the face $face (dist $dist)", dist >= r)
        assertTrue("$what: below the face (button top ${p[1] - r} vs chin ${face.bottom})", p[1] - r >= face.bottom || dist >= r)
        assertTrue("$what: inside the window x", p[0] - r >= -0.5f && p[0] + r <= w[2] + 0.5f)
        assertTrue("$what: inside the window y", p[1] - r >= -0.5f && p[1] + r <= w[3] + 0.5f)
        val sx = p[0] + w[0]; val sy = p[1] + w[1]
        assertTrue("$what: on screen ($sx,$sy)", sx - r >= -0.5f && sx + r <= 1080.5f && sy - r >= -0.5f && sy + r <= 2340.5f)
    }

    // ---------------- always faint ----------------

    @Test fun faintRightAwayAndAfterAWalk() {
        val v = view("monkey")
        frame(v); assertTrue(v.debugChatFabVisible); assertFaint(v, "on appearing")
        v.debugWalk(0.1f)
        repeat(40) { frame(v); assertFaint(v, "while walking") }
        repeat(200) { frame(v) }
        assertFaint(v, "right after the walk")
    }

    @Test fun faintDuringAndAfterADragAndTheLanding() {
        val v = view("monkey")
        frame(v)
        val b = v.debugBox()
        send(v, MotionEvent.ACTION_DOWN, b.centerX(), b.top + b.height() * 0.6f); advance(30)
        for (i in 1..12) { send(v, MotionEvent.ACTION_MOVE, b.centerX() + i * 10f, b.top + b.height() * 0.6f - i * 40f); advance(16); frame(v); assertFaint(v, "while dragged") }
        advance(400); frame(v)   // hold still, then let go: she drops and lands
        send(v, MotionEvent.ACTION_UP, b.centerX() + 120f, b.top + b.height() * 0.6f - 480f)
        var seen = 0
        repeat(300) { frame(v); if (v.debugChatFabVisible) seen++; assertFaint(v, "falling / landing") }
        println("V1011 drag: visible after release $seen frames, surf ${v.debugSurfKind}, falling ${v.debugFalling}")
        assertTrue("shown again after the landing", seen > 30)
        assertFaint(v, "right after landing")
        assertTrue("faint checks $faintChecked", faintChecked > 100)
    }

    @Test fun faintAfterTappingTheBodyAndAfterARocketFlight() {
        val v = view("rocketgirl")
        frame(v)
        val b = v.debugBox()
        send(v, MotionEvent.ACTION_DOWN, b.centerX(), b.top + b.height() * 0.6f); advance(30)
        send(v, MotionEvent.ACTION_UP, b.centerX(), b.top + b.height() * 0.6f); advance(400)
        repeat(3) { frame(v); assertFaint(v, "right after a tap") }
        v.debugRocketLaunch(-900f, -1400f)
        repeat(300) { frame(v); assertFaint(v, "flying / landing") }
        assertTrue("faint checks $faintChecked", faintChecked > 100)
    }

    @Test fun darkOnlyWhileTheFingerIsOnTheButton() {
        val v = view("monkey")
        frame(v, 5)
        assertFaint(v, "before")
        val pt = fabScreen(v)
        send(v, MotionEvent.ACTION_DOWN, pt[0], pt[1]); frame(v, 2)
        assertTrue(v.debugChatFabPressed)
        assertEquals("dark + solid while held", 1f, v.debugChatFabAlpha, 0.001f)
        repeat(30) { frame(v) }
        assertEquals("stays dark the whole time it is held", 1f, v.debugChatFabAlpha, 0.001f)
        assertEquals(0, v.debugChatOpened)
        advance(60); send(v, MotionEvent.ACTION_UP, pt[0], pt[1])
        assertEquals("opens chat on release", 1, v.debugChatOpened)
        assertFalse(v.debugChatFabPressed)
        assertEquals("faint again the moment the finger lifts", faint, v.debugChatFabAlpha, 0.001f)
    }

    @Test fun cancelledPressGoesStraightBackToFaint() {
        val v = view("monkey")
        frame(v, 5)
        val pt = fabScreen(v)
        send(v, MotionEvent.ACTION_DOWN, pt[0], pt[1]); frame(v)
        assertEquals(1f, v.debugChatFabAlpha, 0.001f)
        send(v, MotionEvent.ACTION_CANCEL, pt[0], pt[1]); frame(v)
        assertFaint(v, "after cancel")
        assertEquals(0, v.debugChatOpened)
    }

    @Test fun floatButtonHasNoTimers() {
        val f = FloatButton()
        f.place(10f, 10f, 1)
        f.step(0.016f); assertEquals(faint, f.alpha, 0f)
        f.wake(); f.step(0.016f); assertEquals("touching the body doesn't light it", faint, f.alpha, 0f)
        f.hold(true); assertEquals(1f, f.alpha, 0f); f.step(5f); assertEquals(1f, f.alpha, 0f)
        f.hold(false); assertEquals(faint, f.alpha, 0f)
        f.pressed(); f.step(0.016f); assertEquals(faint, f.alpha, 0f)
    }

    @Test fun touchTargetIsAtLeast40dp() {
        val v = view("monkey")
        assertTrue(2f * v.debugChatFabTouchRadius >= 40f * d)
        frame(v, 5)
        val pt = fabScreen(v)
        // 19 dp from the centre still presses it
        send(v, MotionEvent.ACTION_DOWN, pt[0] + 19f * d, pt[1]); frame(v)
        assertTrue(v.debugChatFabPressed)
        send(v, MotionEvent.ACTION_CANCEL, pt[0], pt[1])
    }

    // ---------------- never over the face ----------------

    @Test fun neverOverTheFaceForEveryCharacterAcrossTheScreen() {
        for (ch in CharacterRegistry.all) {
            for (frac in floatArrayOf(0f, 0.08f, 0.5f, 0.92f, 1f)) {
                val v = view(ch.id, frac)
                assertClear(v, "${ch.id} idle @ $frac")
                v.debugPlay(MonkeyState.WAVE, 1.5f); repeat(20) { v.debugFrames(1) }; assertClear(v, "${ch.id} wave @ $frac")
                v.debugPlay(MonkeyState.DANCE, 1.5f); repeat(25) { v.debugFrames(1) }; assertClear(v, "${ch.id} dance @ $frac")
                v.debugPlay(MonkeyState.HAPPY_PAT, 1.2f); repeat(15) { v.debugFrames(1) }; assertClear(v, "${ch.id} pat @ $frac")
            }
            // facing left and right while walking
            val v = view(ch.id, 0.5f)
            v.debugWalk(0.05f); repeat(30) { v.debugFrames(1); if (it % 5 == 0) assertClear(v, "${ch.id} walking left") }
            v.debugWalk(0.95f); repeat(60) { v.debugFrames(1); if (it % 5 == 0) assertClear(v, "${ch.id} walking right") }
            repeat(240) { v.debugFrames(1) }
            assertClear(v, "${ch.id} after walking")
        }
        assertTrue("checked $checked spots", checked > CharacterRegistry.all.size * 20)
        println("V1011 face checks: $checked")
    }

    @Test fun neverOverRocketGirlsFaceWhileRidingOrFlying() {
        for (frac in floatArrayOf(0f, 0.5f, 1f)) {
            val v = view("rocketgirl", frac)
            assertClear(v, "riding idle @ $frac")
            v.debugRocketLaunch(if (frac > 0.5f) -900f else 900f, -1600f)
            repeat(300) { v.debugFrames(1); if (it % 6 == 0) assertClear(v, "flying @ $frac frame $it") }
        }
        assertTrue("checked $checked", checked > 60)
        println("V1011 rocket checks: $checked")
    }

    @Test fun duoTrioPutsItAwayFromThePartnersAndOffTheFace() {
        var dChecked = 0
        for (mode in listOf("DUO", "TRIO")) for (lead in listOf("billa", "monkey", "rocketgirl")) for (frac in floatArrayOf(0.1f, 0.9f)) {
            MindHub.neighbourMode = mode
            val v = view(lead, frac)
            if (lead == "monkey") v.startNeighbourBanter(com.combo.monkeycompanion.world.Director.BanterScene(com.combo.monkeycompanion.world.Director.Kind.PLACE_GOSSIP, lead, "mask", "Tea?", "Always!"))
            else v.startCampfire(manual = true)
            repeat(if (lead == "monkey") 200 else 10) { v.debugFrames(1) }
            assertClear(v, "$mode $lead @ $frac")
            if (!v.debugChatFabVisible) continue
            val p = fabScreen(v); val b = v.debugBox()
            val partnersRight = b.centerX() <= 540f
            assertTrue("$mode $lead @ $frac: away from the partners", if (partnersRight) p[0] < b.centerX() else p[0] > b.centerX())
            dChecked++
            MindHub.campfire.stop()
        }
        assertTrue("duo/trio checked $dChecked", dChecked >= 8)
    }

    @Test fun spotPickerSkipsAnythingTouchingTheFace() {
        val face = floatArrayOf(0f, 0f, 100f, 100f); val area = floatArrayOf(0f, 0f, 400f, 400f)
        // first candidate overlaps the face, second is clear
        assertEquals(1, ChatBubbleSpot.choose(floatArrayOf(90f, 90f, 150f, 150f), face, area, 20f, 2f))
        // first is off the area, second is clear
        assertEquals(1, ChatBubbleSpot.choose(floatArrayOf(395f, 200f, 200f, 200f), face, area, 20f, 2f))
        // nothing clear: the farthest
        assertEquals(1, ChatBubbleSpot.choose(floatArrayOf(50f, 50f, 105f, 50f), face, area, 20f, 2f))
    }
}
