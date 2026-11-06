package com.combo.monkeycompanion.v1037

import android.graphics.*
import com.combo.monkeycompanion.rig.*
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

/** v10.37: readable independent metal, window containment and swipe/tilt coexistence. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class V1037Test {
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
        ChatHub.settings.roam = false // isolate user swipes from the pre-existing random idle patrol / edge-crash gag
        ChatHub.settings.power = com.combo.monkeycompanion.brain.Superpowers.RANDOM
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

    private fun rig(x: Float = 550f, y: Float = 1100f, sc: Float = 1f): MonkeyRig {
        val p = Pose()
        Animations.fill(p, com.combo.monkeycompanion.brain.MonkeyState.IDLE, 1f, 1f, AnimContext())
        return MonkeyRig().apply { layout(p, x, y, sc, 1f) }
    }
    private val screen = RectF(0f, 0f, 1080f, 2340f)
    private fun scatter(r: MonkeyRig = rig()) = ArmorScatter().apply { assertTrue(start(r, 0f, 0f, screen)) }

    @Test fun thirteenIndependentPiecesAroundTheirOwnAttachmentCenters() {
        val r = rig(); val a = scatter(r)
        val attached = a.attachments(r, 0f, 0f)
        val f = a.frame(r, 0f, 0f)
        assertEquals(13, f.size); assertEquals(13, f.map { it.id }.toSet().size)
        f.zip(attached).forEach { (p, t) -> assertEquals(t.x, p.x, 0.01f); assertEquals(t.y, p.y, 0.01f) }
        a.step(0.2f, true)
        val moved = a.frame(r, 0f, 0f).zip(f).map { (p, f0) -> Pair(p.x - f0.x, p.y - f0.y) }
        assertEquals("distinct plate trajectories, not three body groups", 13, moved.toSet().size)
        // Moving the rig AND the window origin cannot drag already-captured metal around.
        val old = a.frame(r, 0f, 0f)
        val other = a.frame(rig(700f, 1500f), 50f, 120f)
        old.zip(other).forEach { (p, q) -> assertEquals(p.x, q.x, 0.01f); assertEquals(p.y, q.y, 0.01f) }
    }

    @Test fun scatterDistanceScalesWithRigNotViewOrigin() {
        val r1 = rig(); val r2 = rig(sc = 1.5f)
        val a = scatter(r1); val b = scatter(r2)
        a.step(0.2f, true); b.step(0.2f, true)
        a.frame(r1, 0f, 0f).zip(b.frame(r2, 0f, 0f)).forEachIndexed { i, (p, q) ->
            val t1 = a.attachments(r1, 0f, 0f)[i]; val t2 = b.attachments(r2, 0f, 0f)[i]
            assertEquals((p.x-t1.x)*1.5f, q.x-t2.x, 0.05f)
            assertEquals((p.y-t1.y)*1.5f, q.y-t2.y, 0.05f)
        }
    }

    @Test fun outwardFlightLastsPointFourSecondsAndIgnoresRedundantHits() {
        val r = rig(); val a = scatter(r)
        repeat(18) { a.step(1f/60f, true) }
        assertEquals("still flying outward at 0.3 s", 1, a.phase)
        val before = a.elapsed
        assertFalse(a.start(r, 0f, 0f, screen)); assertEquals(before, a.elapsed, 0f)
        repeat(8) { a.step(1f/60f, true) }
        assertEquals(2, a.phase)
        repeat(180) { a.step(1f/60f, true) }
        assertEquals("no recovery hold spent while bouncing", 0f, a.recovery, 0f)
        a.step(0.4f, false); assertEquals(2, a.phase)
        a.step(0.21f, false); assertEquals(3, a.phase)
        a.step(0.65f, false)
        val f = a.frame(r, 0f, 0f)
        assertTrue("staggered docking", f.any { it.docked } && f.any { !it.docked })
        val newRig = rig(620f, 1400f)
        val target = a.attachments(newRig, 0f, 0f)[0]
        val p = a.frame(newRig, 0f, 0f)[0]
        assertEquals("docked plate follows his CURRENT attachment", target.x, p.x, 0.01f)
        assertEquals(target.y, p.y, 0.01f)
        a.step(0.14f, false); assertTrue(a.active)
        assertTrue(a.step(0.02f, false)); assertFalse(a.active)
    }

    @Test fun nearScreenEdgesMetalScattersInwardAndStaysVisible() {
        for (r in listOf(rig(70f, 2300f), rig(1010f, 180f))) {
            val a = scatter(r)
            repeat(25) {
                a.step(1f/60f, true)
                for (p in a.frame(r, 0f, 0f)) {
                    val radius = kotlin.math.hypot(p.w, p.h)/2f
                    assertTrue(p.x-radius >= 0f && p.x+radius <= screen.right)
                    assertTrue(p.y-radius >= 0f && p.y+radius <= screen.bottom)
                }
            }
        }
    }

    private fun marble(v: MonkeyView, strong: Boolean = false) {
        val f = Finger(v); longPress(f)
        f.move((1..14).map { (if (strong) -42f else -14f) * it to (if (strong) 0f else 16f) * it })
        f.up()
    }
    private fun contain(v: MonkeyView) {
        val w = v.debugWin()
        for (p in v.debugArmorPieces) {
            val radius = kotlin.math.hypot(p.w, p.h)/2f
            assertTrue("${p.id} outside window left $w", p.x-radius >= w[0]-1f)
            assertTrue("${p.id} outside window right", p.x+radius <= w[0]+w[2]+1f)
            assertTrue("${p.id} outside window top win=${w.toList()} piece=$p body=${v.debugBox()} phase=${v.debugArmorShedPhase}", p.y-radius >= w[1]-1f)
            assertTrue("${p.id} outside window bottom", p.y+radius <= w[1]+w[3]+1f)
        }
    }
    @Test fun actualMarbleFirstHitTriggersOnceAndWindowContainsWholeLifecycle() {
        val v = view("armour")
        v.debugPlace(400f, false, 900f); v.debugFrames(1)
        marble(v)
        assertTrue(v.debugMarbleBounce)
        assertEquals("not at release", 0, v.debugArmorShedPhase)
        var saw = false; var sawHold = false; var sawHome = false; var maxBounces = 0
        var previousPhase = 0
        repeat(60*14) {
            v.debugFrames(1)
            if (v.debugArmorShedPhase != 0) {
                saw = true; assertTrue("must have hit first", v.debugMarbleBounces > 0)
                contain(v)
                if (v.debugArmorShedPhase == 2) sawHold = true
                if (v.debugArmorShedPhase == 3) sawHome = true
                assertFalse("later bounce restarted scatter", previousPhase == 2 && v.debugArmorShedPhase == 1)
                maxBounces = maxOf(maxBounces, v.debugMarbleBounces)
                assertTrue("extra scatter padding", v.debugWin()[2] > v.debugBox().width())
                previousPhase = v.debugArmorShedPhase
            }
        }
        assertTrue("saw=$saw hold=$sawHold home=$sawHome phase=${v.debugArmorShedPhase} marble=${v.debugMarbleBounce}", saw && sawHold && sawHome)
        assertTrue("bounce count=$maxBounces", maxBounces > 1)
        assertEquals(0, v.debugArmorShedPhase); assertFalse(v.debugHidden)
    }

    @Test fun comicSplatAlsoShedsAtHitAndUsesRecoveryHold() {
        val v = view("armour")
        v.debugPlace(v.debugXY[3] - v.debugXY[4] - 20f, false, 800f); v.debugFrames(1)
        marble(v, strong = true)
        assertTrue("strong sling is comic impact", v.debugImpact)
        assertEquals(0, v.debugArmorShedPhase)
        var hit = false; var sawRecovery = false
        repeat(60*12) {
            v.debugFrames(1)
            if (v.debugArmorShedPhase != 0) { hit = true; contain(v) }
            if (v.debugImpact && v.debugArmorShedPhase == 2) assertEquals(0f, v.debugArmorRecovery, 0f)
            if (!v.debugImpact && v.debugArmorShedPhase == 2) sawRecovery = true
        }
        assertTrue(hit); assertTrue(sawRecovery); assertEquals(0, v.debugArmorShedPhase)
    }

    @Test fun detachedMetalPassIsNotSquashedWithHisInnerBody() {
        val r = rig(); val a = scatter(r); a.step(0.2f, true)
        val bitmap = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        // Production drawMonkey restores the body's squash before ArmorScatter.draw.
        c.save(); c.scale(0.03f, 1f, 550f, 1000f); c.restore()
        a.draw(c, r, 0f, 0f)
        for (p in a.frame(r, 0f, 0f)) {
            val cx = p.x.toInt(); val cy = p.y.toInt()
            assertTrue("readable full-size plate center ${p.id}", Color.alpha(bitmap.getPixel(cx, cy)) > 0)
        }
    }

    @Test fun liveSplatDrawsFullMetalAfterRestoringHisSquashLayer() {
        val v = view("armour")
        v.debugPlace(v.debugXY[3] - v.debugXY[4] - 20f, false, 800f); v.debugFrames(1)
        marble(v, true)
        var found = false
        repeat(90) {
            v.debugFrames(1)
            if (!found && v.debugImpactPhase == "HOLD" && v.debugArmorShedPhase != 0) {
                assertEquals("SPLAT", v.debugImpactKind)
                val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
                v.draw(Canvas(bmp))
                val w = v.debugWin()
                // Interior center and several nearby points must remain metal colored, even though
                // his body is a thin splat on the edge. This exercises the actual production draw pass.
                var readable = 0
                for (p in v.debugArmorPieces) {
                    val cx = (p.x - w[0]).toInt(); val cy = (p.y - w[1]).toInt()
                    if (cx in 6 until 1074 && cy in 6 until 2334) {
                        val color = bmp.getPixel(cx, cy)
                        if (color == ArmourRenderer.SAFFRON || color == ArmourRenderer.CORE || color == ArmourRenderer.GUN) readable++
                    }
                }
                assertTrue("visible metal at independent screen-space centers ($readable)", readable >= 10)
                found = true
            }
        }
        assertTrue("tested during real squash hold", found)
    }

    @Test fun shedPaddingSurvivesBubbleAndArcEarlyReturnWithoutMovingBody() {
        val v = view("armour"); v.debugPlace(400f, false, 900f); v.debugFrames(1)
        marble(v)
        repeat(60*10) { if (v.debugMarbleBounce) v.debugFrames(1) }
        assertTrue("shed recovery still active", v.debugArmorShedPhase != 0)
        val before = v.debugBox()
        v.debugSay("His plates are flying home, not clipped by this bubble.")
        assertEquals(before.left, v.debugBox().left, 0.01f)
        assertEquals(before.top, v.debugBox().top, 0.01f)
        v.debugSwipeDown(6500f)
        assertNotNull("arc missing char=${CharacterRegistry.current.id} power=${ChatHub.settings.power} edgeCrash=${v.isEdgeCrashLanding} marble=${v.debugMarbleBounce} fall=${v.debugFalling}", v.debugArcPlan)
        repeat(60) {
            v.debugFrames(1)
            if (v.debugArmorShedPhase != 0) contain(v)
        }
        assertFalse(v.debugHidden)
    }

    @Test fun floorRollStillWorksAndSettledSideCanRollBack() {
        val v = view("armour"); v.debugPlace(400f, true); v.debugFrames(1)
        var t = 4000L
        repeat(120) { v.debugTiltFeed(-9.6f, 0.4f, 0.3f, t); t+=16; v.debugFrames(1) }
        val edgeX = v.debugBox().left
        assertTrue(edgeX > 500f); assertTrue(v.debugTiltSliding); assertFalse(v.debugFalling)
        repeat(120) { v.debugTiltFeed(9.6f, 0.4f, 0.3f, t); t+=16; v.debugFrames(1) }
        assertTrue("reverse away from settled right edge", v.debugBox().left < edgeX-100f)
        assertFalse(v.debugHidden)
    }

    @Test fun settledScreenWallCanStartATiltRoll() {
        val v = view("monkey"); v.debugSurf("WALL_L", frac=0.5f); v.debugFrames(60)
        val x0 = v.debugBox().left
        var t=5000L
        repeat(90) { v.debugTiltFeed(-9.6f, 0.4f, 0.3f, t); t+=16; v.debugFrames(1) }
        assertTrue("roll from a settled left wall grip toward the right", v.debugBox().left > x0+100f)
        assertTrue(v.debugTiltSliding); assertFalse(v.debugHidden)
    }

    @Test fun idleHoverAndUnrelatedFallingAreNotGrabbedByTilt() {
        for (id in listOf("armour", "superboy")) {
            val v = view(id); v.debugPlace(400f, false, 800f); v.debugFrames(1)
            val b = v.debugBox()
            var t = 5000L
            repeat(60) { v.debugTiltFeed(-9.6f, 0.4f, 0.3f, t); t+=16; v.debugFrames(1) }
            assertEquals(b.left, v.debugBox().left, 0.1f); assertEquals(b.top, v.debugBox().top, 0.1f)
            assertFalse(v.debugTiltSliding)
            v.triggerMidScreenCoyoteFall(400f, 800f)
            repeat(20) {
                v.debugTiltFeed(9.6f, 0.4f, 0.3f, t); t+=16; v.debugFrames(1)
                assertTrue("tilt leaves existing fall alone", v.debugFalling); assertFalse(v.debugTiltSliding)
            }
        }
    }

    @Test fun uprightAfterOurOwnUpsideDownFloorRollLandsAgain() {
        val v = view("monkey"); v.debugPlace(400f, true); v.debugFrames(1)
        var t=5000L
        repeat(50) { v.debugTiltFeed(0.2f, -9.8f, 0.3f, t); t+=16; v.debugFrames(1) }
        assertTrue("our own floor-originated roll can leave floor", v.debugBox().top < v.debugXY[2]-100f)
        repeat(300) { v.debugTiltFeed(0.2f, 9.8f, 0.3f, t); t+=16; v.debugFrames(1) }
        assertFalse(v.debugTiltSliding)
        assertTrue("lands after returning upright", v.debugBox().top > v.debugXY[2]-60f)
    }

    private fun arcWithTilt(id: String, down: Boolean, gx: Float, gy: Float) {
        val v = view(id); v.debugPlace(400f, false, 800f); v.debugFrames(1)
        if (down) v.debugSwipeDown(6500f) else v.debugRelease(0f, -6500f, 300f)
        val plan = v.debugArcPlan
        assertNotNull("$id started ${if(down) "down" else "up"} arc", plan)
        var minX = v.debugBox().left; var maxX = minX; var minY = v.debugBox().top; var maxY = minY
        var t = 5000L
        repeat(30) {
            v.debugTiltFeed(gx, gy, 0.3f, t); t += 16; v.debugFrames(1)
            assertTrue("tilt cannot cancel $id arc", v.debugArcPlan === plan)
            minX = minOf(minX, v.debugBox().left); maxX = maxOf(maxX, v.debugBox().left)
            minY = minOf(minY, v.debugBox().top); maxY = maxOf(maxY, v.debugBox().top)
        }
        assertTrue("meaningful travel", maxX-minX > 40f || maxY-minY > 40f)
        // Keep feeding the tilt only while arc owns motion; no manual re-creation of the path.
        var done = false
        repeat(60*15) {
            if (v.debugArcPlan != null) { v.debugTiltFeed(gx, gy, 0.3f, t); t += 16; v.debugFrames(1) }
            else done = true
        }
        assertTrue("$id flight finishes", done); assertFalse(v.debugHidden)
    }
    @Test fun annaUpArcSurvivesGentleTilt() = arcWithTilt("armour", false, 6.9f, 6.9f)
    @Test fun annaDownArcSurvivesSteepTilt() = arcWithTilt("armour", true, 9.6f, 0.4f)
    @Test fun annaUpArcSurvivesSteepTilt() = arcWithTilt("armour", false, -9.6f, 0.4f)
    @Test fun annaDownArcSurvivesGentleTilt() = arcWithTilt("armour", true, -6.9f, 6.9f)
    @Test fun superboyUpArcSurvivesGentleTilt() = arcWithTilt("superboy", false, 6.9f, 6.9f)
    @Test fun superboyArcSurvivesTilt() = arcWithTilt("superboy", true, 9.6f, 0.4f)
    @Test fun rocketGirlRocketStillExcludesTilt() {
        val rocket = view("rocketgirl")
        rocket.debugPlace(400f, false, 800f); rocket.debugFrames(1)
        rocket.debugRocketLaunch(3000f, -5000f)
        assertTrue(rocket.debugRocketFlyOn)
        val x0 = rocket.debugBox().left; val y0 = rocket.debugBox().top
        var t = 3000L
        repeat(30) { rocket.debugTiltFeed(-9.6f, 0.4f, 0.3f, t); t +=16; rocket.debugFrames(1); assertFalse(rocket.debugTiltSliding) }
        assertFalse(rocket.debugHidden)
        assertTrue("rocket travels", kotlin.math.hypot(rocket.debugBox().left-x0, rocket.debugBox().top-y0) > 40f)
        repeat(60*15) { if (rocket.debugRocketFlyOn) rocket.debugFrames(1) }
        assertFalse("rocket flight completes", rocket.debugRocketFlyOn)
    }
}
