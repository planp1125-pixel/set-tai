package com.combo.monkeycompanion.v1031

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.agent.ActivityScheduler
import com.combo.monkeycompanion.agent.Voice
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.engine.action.ActionLibrary
import com.combo.monkeycompanion.landing.LandingE2ETest
import com.combo.monkeycompanion.mind.MindHub
import com.combo.monkeycompanion.overlay.CharacterIcon
import com.combo.monkeycompanion.overlay.MonkeyView
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.Pose
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/** v10.31: real listening UX, shared listen-at-ear library action, character AOD / notification icon. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class V1031Test {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    /** Fake recogniser: records the session; [onStop] = what a tap-to-send does. */
    private class FakeEngine(val finalOnStop: String?) : Voice.TestEngine {
        var s: Voice.TestSession? = null
        var stops = 0
        override fun onStart(s: Voice.TestSession) { this.s = s }
        override fun onStop() { stops++; finalOnStop?.let { s?.result(it) } }
    }

    @Before fun clean() {
        ChatHub.init(ctx); MindHub.init(ctx); ActivityScheduler.resetForTest(ctx)
        MindHub.focusBuddy.stop(); MindHub.campfire.stop(); MindHub.neighbourMode = "SOLO"
        shadowOf(ctx).grantPermissions(Manifest.permission.RECORD_AUDIO)
    }
    @After fun after() {
        Voice.cancelListen(); Voice.testEngine = null
        MindHub.focusBuddy.stop(); MindHub.campfire.stop()
        ActivityScheduler.resetForTest(ctx); MindHub.neighbourMode = "DUO"
        CharacterRegistry.select("monkey")
    }

    private fun view(id: String = "devil"): MonkeyView {
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.gags = false
        ChatHub.settings.batterySaver = false
        ChatHub.settings.chatButton = true
        CharacterRegistry.select(id)
        val v = MonkeyView(ctx, LandingE2ETest.Host())
        v.debugNoNightCampfire = true
        v.layout(0, 0, 1080, 2340)
        v.debugFrames(60)
        v.debugSurf("FLOOR", frac = 0.5f)
        v.debugFrames(10)
        v.debugQuiet = true
        return v
    }

    private var clock = 90_000L
    private fun send(v: MonkeyView, a: Int, rx: Float, ry: Float) {
        val w = v.debugWin()
        val e = MotionEvent.obtain(clock, clock, a, rx, ry, 0)
        e.offsetLocation(-w[0].toFloat(), -w[1].toFloat())
        v.dispatchTouchEvent(e); e.recycle()
    }
    private fun advance(ms: Long) { ShadowSystemClock.advanceBy(Duration.ofMillis(ms)); clock += ms }
    private fun frame(v: MonkeyView, n: Int = 1) { v.debugFrames(n); v.draw(Canvas(bmp)) }
    private fun tapMic(v: MonkeyView) {
        val w = v.debugWin(); val mic = FloatArray(2); v.debugMicFab(mic)
        send(v, MotionEvent.ACTION_DOWN, mic[0] + w[0], mic[1] + w[1]); advance(60)
        send(v, MotionEvent.ACTION_UP, mic[0] + w[0], mic[1] + w[1])
    }
    private fun tapBody(v: MonkeyView) {
        val b = v.debugBox()
        send(v, MotionEvent.ACTION_DOWN, b.centerX(), b.centerY()); advance(50)
        send(v, MotionEvent.ACTION_UP, b.centerX(), b.centerY())
    }

    @Test fun micFabShowsListeningPromptAndPartials() {
        val eng = FakeEngine(null); Voice.testEngine = eng
        val v = view(); frame(v, 5)
        tapMic(v); frame(v, 3)
        assertNotNull("mic tap must start a real listen", eng.s)
        assertTrue(Voice.listening)
        assertTrue(v.debugListenTap)
        assertEquals("🎤 Listening… tap me to send", v.debugBubbleText)
        eng.s!!.partial("hello satan")
        frame(v)
        val shown = v.debugBubbleText ?: ""
        assertTrue(shown, shown.contains("hello satan"))
        assertTrue(shown, shown.contains("tap me to send"))
    }

    @Test fun tapToSendDeliversTheWords() {
        val eng = FakeEngine("what is the time")
        Voice.testEngine = eng
        val v = view(); frame(v, 5)
        tapMic(v); frame(v, 2)
        eng.s!!.partial("what is")
        tapBody(v); frame(v, 2)
        assertEquals(1, eng.stops)
        assertFalse(Voice.listening)
        assertEquals("what is the time", v.debugLastHeard)
    }

    @Test fun tapToSendKeepsPartialWhenRecogniserNeverAnswers() {
        val eng = FakeEngine(null); Voice.testEngine = eng
        val v = view(); frame(v, 5)
        tapMic(v); frame(v, 2)
        eng.s!!.partial("tell me a joke")
        tapBody(v)
        ShadowLooper.idleMainLooper(Voice.SEND_TIMEOUT_MS + 100, java.util.concurrent.TimeUnit.MILLISECONDS)
        frame(v, 2)
        assertFalse(Voice.listening)
        assertEquals("tell me a joke", v.debugLastHeard)
    }

    @Test fun aiChatterCannotCoverTheListeningBubble() {
        val eng = FakeEngine(null); Voice.testEngine = eng
        val v = view(); frame(v, 5)
        tapMic(v); frame(v, 2)
        v.debugQuiet = false
        v.say("Wheee— slidiiing!", 1800L)
        v.sayInfo("some info", 1500L)
        frame(v, 2)
        assertEquals("🎤 Listening… tap me to send", v.debugBubbleText)
        v.debugQuiet = true
    }

    @Test fun listeningPlaysSharedEarPoseOnAnyCharacter() {
        for (id in listOf("devil", "angel", "monkey", "gajju")) {
            val eng = FakeEngine(null); Voice.testEngine = eng
            val v = view(id); frame(v, 5)
            val idleArm = v.debugLastArmR
            tapMic(v); frame(v, 40)
            assertTrue("$id pose weight", v.debugListenPoseK > 0.9f)
            assertTrue("$id hand at ear (was $idleArm, now ${v.debugLastArmR})", v.debugLastArmR > 140f)
            Voice.cancelListen()
        }
    }

    @Test fun libraryActionIsOneSharedObjectAppliedToEveryBody() {
        val a = ActionLibrary.byId("listen_ear")
        assertSame(ActionLibrary.ListenEar, a)
        assertEquals(1, ActionLibrary.all.count { it.id == "listen_ear" })
        for (c in CharacterRegistry.all) {
            val p = Pose(); c.fill(p, MonkeyState.IDLE, 1f, 1f, AnimContext(), CharacterRegistry.wear(c))
            assertTrue(ActionLibrary.apply("listen_ear", p, 0f, 1f))
            assertEquals(c.id, ActionLibrary.ListenEar.ARM_RAISE, p.armRRaise, 0.5f)
            assertEquals(c.id, ActionLibrary.ListenEar.ARM_ELBOW, p.armRElbow, 0.5f)
        }
        val p = Pose(); val before = p.armRRaise
        ActionLibrary.apply("listen_ear", p, 0f, 0f)
        assertEquals(before, p.armRRaise, 0f)
        assertFalse(ActionLibrary.apply("nope", Pose(), 0f, 1f))
    }

    @Test fun notificationIconFollowsSelectedCharacter() {
        CharacterRegistry.select("devil")
        assertEquals("devil", CharacterIcon.currentId())
        val satan = CharacterIcon.bitmap(ctx).copy(Bitmap.Config.ARGB_8888, false)
        assertTrue(opaque(satan) > 40)
        CharacterRegistry.select("monkey")
        assertEquals("monkey", CharacterIcon.currentId())
        val monkey = CharacterIcon.bitmap(ctx)
        assertFalse("icon must change with the character", satan.sameAs(monkey))
        var told: String? = null
        CharacterRegistry.onSelect = { told = it.id }
        CharacterRegistry.select("devil")
        assertEquals("devil", told)
        CharacterRegistry.onSelect = null
        assertNotNull(CharacterIcon.small(ctx))
    }

    private fun opaque(b: Bitmap): Int { var n = 0; for (y in 0 until b.height) for (x in 0 until b.width) if ((b.getPixel(x, y) ushr 24) > 128) n++; return n }
}
