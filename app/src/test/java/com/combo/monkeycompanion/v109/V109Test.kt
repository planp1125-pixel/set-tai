package com.combo.monkeycompanion.v109

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.agent.ActivityScheduler
import com.combo.monkeycompanion.agent.DailyUtilityEngine
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.PowerShortcut
import com.combo.monkeycompanion.brain.RocketFlight
import com.combo.monkeycompanion.brain.SkyBurstPower
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.landing.LandingE2ETest
import com.combo.monkeycompanion.mind.MindHub
import com.combo.monkeycompanion.overlay.MonkeyView
import com.combo.monkeycompanion.rig.FireworkRecipes
import com.combo.monkeycompanion.rig.Fireworks
import com.combo.monkeycompanion.rig.SkyShow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import kotlin.math.abs
import kotlin.math.atan2

/** v10.9: no power cooldowns, the bottle sky show, rocket flight, recipes, double-tap power, chat button, timers that survive interruptions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class V109Test {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    @Before fun clean() {
        ChatHub.init(ctx)
        MindHub.init(ctx)
        ActivityScheduler.resetForTest(ctx)
        MindHub.focusBuddy.stop()
    }
    @After fun after() { MindHub.focusBuddy.stop(); ActivityScheduler.resetForTest(ctx) }

    private fun view(id: String, dtPower: Boolean = true, chatBtn: Boolean = true): MonkeyView {
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.gags = false
        ChatHub.settings.livingReactions = true
        ChatHub.settings.batterySaver = false
        ChatHub.settings.doubleTapPower = dtPower
        ChatHub.settings.chatButton = chatBtn
        CharacterRegistry.select(id)
        val v = MonkeyView(ctx, LandingE2ETest.Host())
        v.layout(0, 0, 1080, 2340)
        v.debugFrames(60)
        v.debugSurf("FLOOR", frac = 0.45f)
        v.debugFrames(10)
        v.debugQuiet = true
        return v
    }

    private var clock = 50_000L
    private fun tapAt(v: MonkeyView, rx: Float, ry: Float) {
        val w = v.debugWin()
        fun send(a: Int) {
            val e = MotionEvent.obtain(clock, clock, a, rx, ry, 0)
            e.offsetLocation(-w[0].toFloat(), -w[1].toFloat())
            v.dispatchTouchEvent(e); e.recycle()
        }
        send(MotionEvent.ACTION_DOWN); clock += 40; send(MotionEvent.ACTION_UP)
    }
    /** A tap on the body (middle of the box, a bit low: the belly). */
    private fun tapBody(v: MonkeyView) { val b = v.debugBox(); tapAt(v, b.centerX(), b.top + b.height() * 0.62f) }
    private fun advance(ms: Long) { ShadowSystemClock.advanceBy(Duration.ofMillis(ms)); clock += ms }

    // ---------------- recipes ----------------

    @Test fun atLeastFiftyDistinctRecipesAndNoBackToBackRepeats() {
        val all = FireworkRecipes.ALL
        assertTrue("recipes: ${all.size}", all.size >= 50)
        assertEquals("unique names", all.size, all.map { it.name }.toSet().size)
        assertEquals("unique recipes", all.size, all.map { it.copy(name = "") }.toSet().size)
        assertTrue("every recipe is layered (multi-ring)", all.all { it.rings >= 2 })
        for (seed in listOf(1L, 9L, 2026L)) {
            val fw = Fireworks(seed)
            var last: Any? = null
            val seen = HashSet<String>()
            repeat(400) {
                val sp = fw.nextSpec(100f, 100f, 3f)
                assertNotNull(sp.recipe)
                assertNotEquals("seed $seed draw $it repeats", last, sp.recipe)
                last = sp.recipe; seen += sp.recipe!!.name
            }
            assertTrue("most recipes get used: ${seen.size}", seen.size >= 45)
        }
    }

    // ---------------- the bottle sky show ----------------

    @Test fun bottleShowLaunchesFiveDifferentMultiRingShellsAtDifferentHeights() {
        for (seed in listOf(3L, 4L, 5L, 6L)) {
            val fw = Fireworks(seed * 11)
            val show = SkyShow(seed, fw)
            var booms = 0
            var rings: Int
            fw.onBoom = { _, _, _ -> booms++ }
            show.start(300f, 2000f, 3f, 1f, 150f, 1300f, 1080f)
            var sawReach = false
            var f = 0
            while ((show.active || fw.active) && f < 60 * 12) {
                show.step(1f / 60f); fw.step(1f / 60f); f++
                if (show.reach > 0.9f) sawReach = true
                assertTrue(fw.count <= Fireworks.CAP)
                show.draw(Canvas(bmp)); fw.draw(Canvas(bmp))
            }
            assertFalse("show finished", show.active)
            assertTrue("she lit the fuse first", sawReach)
            assertTrue("launched ${show.launched}", show.launched >= SkyShow.SHELLS)
            assertTrue("burst $booms", booms >= 5)
            val hist = fw.history.takeLast(show.launched)
            assertEquals("5 different recipes per show", hist.size, hist.map { it.recipe }.toSet().size)
            rings = hist.minOf { it.rings }
            assertTrue("multi-ring bursts: $rings", rings >= 2)
            val heights = hist.map { it.y }.sorted()
            assertTrue("different heights", heights.zipWithNext().count { (a, b) -> b - a > 20f } >= 3)
        }
    }

    @Test fun skyBurstPowerInTheOverlayRunsTheBottleShow() {
        val v = view("rocketgirl")
        assertTrue(v.debugPower(SkyBurstPower))
        var started = false; var peak = 0
        repeat(60 * 8) {
            v.debugFrames(1); v.draw(Canvas(bmp))
            if (v.debugSkyShows.any { it.active }) started = true
            peak = maxOf(peak, v.debugFireworkCount)
        }
        assertTrue("bottle show started", started)
        assertTrue("shells launched", v.debugSkyShows.sumOf { it.launched } >= SkyShow.SHELLS)
        assertTrue(peak in 1..Fireworks.CAP)
    }

    // ---------------- no cooldowns + the cap under rapid powers ----------------

    @Test fun powersWorkBackToBackWithNoCooldownAndTheCapHolds() {
        val v = view("rocketgirl")
        var peak = 0
        var fired = 0
        repeat(12) {
            if (v.debugPower(SkyBurstPower)) fired++
            repeat(25) { v.debugFrames(1); peak = maxOf(peak, v.debugFireworkCount) }
        }
        assertEquals("every press fired (no cooldown)", 12, fired)
        repeat(60 * 10) { v.debugFrames(1); peak = maxOf(peak, v.debugFireworkCount) }
        assertTrue("cap holds under rapid powers: $peak", peak <= Fireworks.CAP)
        // other characters too: two different powers right after each other
        val m = view("monkey")
        val pws = CharacterRegistry.current.powers
        assertTrue(m.debugPower(pws.first())); m.debugFrames(5)
        assertTrue("second power immediately", m.debugPower(pws.last())); m.debugFrames(5)
        assertTrue(m.debugPowerOn)
    }

    // ---------------- rocket flight ----------------

    @Test fun rocketFlightNoseFollowsVelocityAndHeadingTurnsGradually() {
        val f = RocketFlight(42L)
        f.launch(300f, 1500f, 1f, -0.3f, 0.8f, 2.625f, 100f, 980f, 200f, 2000f)
        var prevHeading = f.heading
        var maxTurn = 0f; var burn = 0; var sawSputter = false; var sawGlide = false
        var steps = 0
        while (f.active && steps < 60 * 10) {
            f.step(1f / 60f); steps++
            if (f.speed > 30f && f.phase != RocketFlight.Phase.IGNITE) {   // (during the ignite dip the nose keeps its heading)
                val vel = Math.toDegrees(atan2(f.vy, f.vx).toDouble()).toFloat()
                var d = f.noseDeg - vel; while (d > 180f) d -= 360f; while (d < -180f) d += 360f
                assertTrue("nose along velocity: $d", abs(d) < 0.5f)
            }
            var dh = f.heading - prevHeading; while (dh > Math.PI) dh -= (2 * Math.PI).toFloat(); while (dh < -Math.PI) dh += (2 * Math.PI).toFloat()
            maxTurn = maxOf(maxTurn, abs(dh)); prevHeading = f.heading
            if (f.phase == RocketFlight.Phase.BURN) burn++
            if (f.phase == RocketFlight.Phase.SPUTTER) sawSputter = true
            if (f.phase == RocketFlight.Phase.GLIDE) sawGlide = true
            assertTrue(f.x in 100f..980f && f.y in 200f..2000f)
        }
        assertFalse("flight ends", f.active)
        assertTrue("burned", burn > 20)
        assertTrue("sputter then glide", sawSputter && sawGlide)
        assertTrue("heading turns gradually: $maxTurn rad/frame", maxTurn < 0.12f)
    }

    @Test fun swipeLaunchFliesTheOverlayAlongTheSwipeWithExhaustFromTheRocket() {
        val v = view("rocketgirl")
        val b0 = v.debugBox()
        v.debugRocketLaunch(2600f, -900f)
        assertTrue(v.debugRocketFlyOn)
        var maxDx = 0f; var exhaust = false; var f = 0
        while (v.debugRocketFlyOn && f < 60 * 10) {
            v.debugFrames(1); v.draw(Canvas(bmp)); f++
            maxDx = maxOf(maxDx, v.debugBox().centerX() - b0.centerX())
            if (v.debugParticles.active) exhaust = true
        }
        assertFalse("landed / hovering again", v.debugRocketFlyOn)
        assertTrue("flew right with the swipe: $maxDx", maxDx > 150f)
        assertTrue("exhaust particles", exhaust)
    }

    // ---------------- double-tap power ----------------

    @Test fun doubleTapFiresRandomPowersNeverTheSameTwiceInARow() {
        val ps = PowerShortcut(7L)
        val pws = CharacterRegistry.byId("monkey")!!.powers
        assertTrue(pws.size >= 2)
        var last: Any? = null
        val seen = HashSet<Any>()
        repeat(200) { val p = ps.pick(pws)!!; assertNotEquals(last, p); last = p; seen += p }
        assertTrue("random: uses several powers (${seen.size})", seen.size >= minOf(3, pws.size))
        val one = listOf(pws.first())
        repeat(5) { assertEquals(pws.first(), ps.pick(one)) }

        val v = view("monkey")
        val fired = ArrayList<String>()
        repeat(6) {
            tapBody(v); advance(90); tapBody(v)
            assertTrue("double-tap fired a power", v.debugPowerOn)
            fired += v.debugLastShortcut!!.id
            repeat(60 * 12) { if (v.debugPowerOn) v.debugFrames(1) }
            advance(1000); v.debugFrames(30)
        }
        for (i in 1 until fired.size) assertNotEquals("no back-to-back repeat: $fired", fired[i - 1], fired[i])
        assertEquals("double-tap no longer opens the chat", 0, v.debugChatOpened)
    }

    @Test fun singleTapStillReactsAndSlowTapsAreNotADoubleTap() {
        val v = view("monkey")
        tapBody(v); advance(600); v.debugFrames(20); tapBody(v)
        assertFalse(v.debugPowerOn)
    }

    // ---------------- chat button ----------------

    @Test fun chatButtonOpensChatAndHidesWhenItShould() {
        val v = view("monkey")
        v.debugFrames(30); v.draw(Canvas(bmp))
        assertTrue("chat button shown", v.debugChatFabVisible)
        val pt = FloatArray(2); v.debugChatFab(pt)
        val w = v.debugWin()
        // inside the window and not on the face
        assertTrue(pt[0] in 0f..w[2].toFloat() && pt[1] in 0f..w[3].toFloat())
        tapAt(v, pt[0] + w[0], pt[1] + w[1])
        assertEquals("tap opens chat", 1, v.debugChatOpened)
        assertFalse("tapping the button is not a power", v.debugPowerOn)
        // hidden during a power, back afterwards
        v.debugPower(CharacterRegistry.current.powers.first()); v.debugFrames(2)
        assertFalse(v.debugChatFabVisible)
        // hidden while tucked away
        repeat(60 * 12) { if (v.debugPowerOn) v.debugFrames(1) }
        v.debugHide(1); v.debugFrames(90)
        assertFalse(v.debugChatFabVisible)
    }

    @Test fun togglesRestoreTheOldBehaviour() {
        val v = view("monkey", dtPower = false, chatBtn = false)
        v.debugFrames(30); v.draw(Canvas(bmp))
        assertFalse("no chat button", v.debugChatFabVisible)
        tapBody(v); advance(90); tapBody(v)
        assertFalse("double-tap is not a power", v.debugPowerOn)
        assertEquals("double-tap opens chat as before", 1, v.debugChatOpened)
    }

    // ---------------- the Pomodoro survives every interruption ----------------

    @Test fun pomodoroSurvivesPowerChatSmashAndCharacterSwitch() {
        val v = view("monkey")
        v.startFocusBuddy(25)
        val end = MindHub.focusBuddy.endAtMs
        assertEquals("saved to prefs", end, ActivityScheduler.savedFocusEnd(ctx))
        v.debugFrames(120)
        assertTrue("study pose", v.debugStudyPose)
        assertEquals(MonkeyState.SIT, v.debugState)

        // 1. a power (the old bug: this stopped the timer)
        assertTrue(v.debugPower(CharacterRegistry.current.powers.first()))
        repeat(60 * 12) { if (v.debugPowerOn) v.debugFrames(1) }
        assertTrue("timer still running after a power", MindHub.focusBuddy.active)
        // 2. a double-tap power, then a drag-like interruption
        advance(500); tapBody(v); advance(80); tapBody(v)
        repeat(60 * 12) { if (v.debugPowerOn) v.debugFrames(1) }
        assertTrue(MindHub.focusBuddy.active)
        // 3. chat open / close
        v.debugSay("hi"); v.debugFrames(60)
        v.onOutsideOrBack(); v.debugFrames(30)
        // 4. Smash: the overlay is hidden and shown again
        v.setPaused(true); v.debugFrames(5); v.setPaused(false); v.debugFrames(30)
        // 5. switch character (a new overlay view, like the service does)
        CharacterRegistry.select("billa")
        val v2 = view("billa")
        // 6. wall time passes: 3 minutes
        val wall = System.currentTimeMillis()
        val expected = (end - wall) / 1000f
        assertTrue(MindHub.focusBuddy.active)
        assertEquals("correct remaining time", expected, v2.debugFocusRemaining, 2f)
        assertTrue("remaining < 25 min", v2.debugFocusRemaining < 25 * 60f)
        // the study visual resumes
        v2.debugFrames(150)
        assertTrue("study pose resumes", v2.debugStudyPose)
        assertEquals(MonkeyState.SIT, v2.debugState)
        // 7. service restart / app killed: memory lost, prefs remain
        MindHub.focusBuddy.onChange = null
        val total = MindHub.focusBuddy.totalSecs
        MindHub.focusBuddy.restore(0L, 0f)   // no-op
        com.combo.monkeycompanion.heart.FocusBuddy().let { fresh ->
            assertTrue("restorable from prefs", fresh.restore(ActivityScheduler.savedFocusEnd(ctx), total))
            assertEquals(v2.debugFocusRemaining, fresh.remainingSecs, 2f)
        }
    }

    @Test fun pomodoroRestoresAfterAKilledAppAndCelebratesIfItEndedMeanwhile() {
        // the session is in prefs; the in-memory timer is gone (process death)
        val v = view("monkey")
        v.startFocusBuddy(25)
        val end = MindHub.focusBuddy.endAtMs
        MindHub.focusBuddy.onChange = null
        MindHub.focusBuddy.stop()
        ActivityScheduler.resetForTestKeepPrefs()
        ActivityScheduler.attach(ctx)
        assertTrue("restored", MindHub.focusBuddy.active)
        assertEquals(end, MindHub.focusBuddy.endAtMs)
        // it ends while Settai is away -> celebrate once on the next start
        MindHub.focusBuddy.onChange = null
        MindHub.focusBuddy.stop()
        ctx.getSharedPreferences("settai_activity_timers_v109", 0).edit().putLong("focus_end", System.currentTimeMillis() - 5000L).putFloat("focus_total", 1500f).commit()
        ActivityScheduler.resetForTestKeepPrefs()
        ActivityScheduler.attach(ctx)
        assertFalse(MindHub.focusBuddy.active)
        assertTrue(ActivityScheduler.consumeFocusDoneUnseen(ctx))
        assertFalse("only once", ActivityScheduler.consumeFocusDoneUnseen(ctx))
    }

    @Test fun duoDoesNotCountTheTimerTwiceAndEyeBreakIsWallClock() {
        val a = view("monkey")
        a.startFocusBuddy(25)
        val end = MindHub.focusBuddy.endAtMs
        val b = MonkeyView(ctx, LandingE2ETest.Host()).apply { forcedCharacterId = "billa" }
        b.layout(0, 0, 1080, 2340)
        repeat(300) { a.debugFrames(1); b.debugFrames(1) }
        assertEquals("frames never move the end time", end, MindHub.focusBuddy.endAtMs)
        DailyUtilityEngine.triggerEyeBreakNow(20f)
        val e1 = DailyUtilityEngine.eyeBreakEndsAt
        a.debugHide(1); repeat(120) { a.debugFrames(1) }
        assertEquals(e1, DailyUtilityEngine.eyeBreakEndsAt)
        assertTrue(DailyUtilityEngine.eyeBreakRemainingSec in 15f..20.01f)
    }
}
