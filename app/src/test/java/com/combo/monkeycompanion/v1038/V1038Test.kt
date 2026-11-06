package com.combo.monkeycompanion.v1038

import android.graphics.*
import com.combo.monkeycompanion.rig.*
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.agent.ActivityScheduler
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.brain.Gestures
import com.combo.monkeycompanion.character.*
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

/** v10.38: his helmet and plates drop under gravity; return waits for both him and the metal. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class V1038Test {
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

    private fun advance(a: ArmorScatter, seconds: Float, busy: Boolean) {
        repeat((seconds*120).toInt()) { a.step(1f/120f, busy) }
    }
    @Test fun helmetIsFourteenthPieceAttachedToHisActualHead() {
        val r = rig(); val a = scatter(r)
        val p = a.frame(r,0f,0f).single { it.id=="helmet" }
        val xy = floatArrayOf(0f,MonkeyRig.HEAD_CY); r.head.mapPoints(xy)
        assertEquals(14,a.frame(r,0f,0f).size)
        assertEquals(xy[0],p.x,0.01f); assertEquals(xy[1],p.y,0.01f)
        advance(a,0.2f,true)
        val flown = a.frame(r,0f,0f).single { it.id=="helmet" }
        assertTrue(kotlin.math.hypot(flown.x-p.x,flown.y-p.y)>20f)
    }

    @Test fun hisBareHeadUsesHisExistingSkinHairAndMoustacheNotHelmetOrVisor() {
        fun headColors(shed: Float): List<Int> {
            val r=rig(250f,450f,2f)
            val p=Pose(); Animations.fill(p,com.combo.monkeycompanion.brain.MonkeyState.IDLE,1f,1f,AnimContext())
            val renderer=ArmourRenderer().apply { this.shed=shed }
            val b=Bitmap.createBitmap(500,600,Bitmap.Config.ARGB_8888)
            renderer.draw(Canvas(b),r,p,0f)
            val xy=floatArrayOf(0f,MonkeyRig.HEAD_CY); r.head.mapPoints(xy)
            val colors=mutableListOf<Int>()
            for(y in (xy[1]-76).toInt()..(xy[1]+76).toInt()) for(x in (xy[0]-90).toInt()..(xy[0]+90).toInt())
                if(x in 0 until b.width && y in 0 until b.height) colors.add(b.getPixel(x,y))
            return colors
        }
        val on=headColors(0f); val off=headColors(1f)
        assertTrue(on.count { it==ArmourRenderer.GUN }>100)
        assertEquals("no attached gunmetal helmet",0,off.count { it==ArmourRenderer.GUN })
        assertEquals("no attached visor",0,off.count { it==ArmourRenderer.VISOR })
        assertTrue("his brown uncle hair",off.count { it==Color.rgb(0x40,0x3A,0x36) }>30)
        assertTrue("his own moustache",off.count { it==Color.rgb(0x3A,0x30,0x2C) }>20)
        assertTrue("his uncovered skin/ears",off.count { it==Color.rgb(0xB8,0x7A,0x50) }>20)
    }

    @Test fun gravityAcceleratesPiecesDownwardInsteadOfFreezingAtFlyOutApex() {
        val r=rig(y=650f); val a=scatter(r)
        val y0=a.frame(r,0f,0f).single { it.id=="bootR" }.y
        advance(a,0.1f,true); val y1=a.frame(r,0f,0f).single { it.id=="bootR" }.y
        advance(a,0.1f,true); val y2=a.frame(r,0f,0f).single { it.id=="bootR" }.y
        assertTrue("downward acceleration",y2-y1 > y1-y0+5f)
        advance(a,0.25f,true); val at45=a.frame(r,0f,0f)
        advance(a,0.2f,true); val at65=a.frame(r,0f,0f)
        assertTrue("past old 0.4s fly-out: still falling, never a frozen hold",at45.zip(at65).all { (p,q) -> q.y>p.y+15f })
        val saved=a.frame(r,0f,0f)
        val movedBody=a.frame(rig(850f,1600f),100f,150f)
        assertEquals("falling debris remains screen-space",saved,movedBody)
    }

    @Test fun allPiecesIncludingHelmetBounceThenRestOnTheVisualGroundWhileHeIsBusy() {
        val r=rig(y=700f); val a=scatter(r)
        var sawBounce=false; var prior=a.frame(r,0f,0f).single { it.id=="bootR" }.y
        var wasDescending=false
        repeat(600) {
            a.step(1f/120f,true)
            val p=a.frame(r,0f,0f).single { it.id=="bootR" }
            if(p.y>prior+0.01f) wasDescending=true
            if(wasDescending && !p.settled && p.y<prior-0.01f) sawBounce=true
            prior=p.y
        }
        assertTrue("a small floor bounce reads like dropped metal",sawBounce)
        assertTrue(a.allSettled); assertEquals(2,a.phase)
        for(p in a.frame(r,0f,0f)) {
            assertTrue(p.settled)
            assertEquals("${p.id} bottom at real floor",a.floorY-1f,p.y+p.h/2f,0.1f)
        }
        val atRest=a.frame(r,0f,0f)
        advance(a,2f,true)
        assertEquals("rest on ground, NOT mid-air",atRest,a.frame(r,0f,0f))
        assertEquals(0f,a.recovery,0f); assertEquals(2,a.phase)
    }

    @Test fun ifHeLandsFirstTheMetalMustFinishFallingBeforeMagneticReturn() {
        val r=rig(y=500f); val a=scatter(r)
        advance(a,0.5f,false) // he is already ready; debris is not
        assertEquals(1,a.phase); assertFalse(a.allSettled); assertEquals(0f,a.recovery,0f)
        repeat(600) { if(!a.allSettled) a.step(1f/120f,false) }
        assertTrue(a.allSettled); assertEquals(2,a.phase)
        a.step(0.1f,false); assertEquals(2,a.phase)
        a.step(0.09f,false); assertEquals(3,a.phase)
    }

    @Test fun ifMetalLandsFirstItWaitsForHimThenHelmetDocksLastWithSuit() {
        val r=rig(); val a=scatter(r)
        advance(a,5f,true)
        assertEquals(2,a.phase)
        advance(a,0.2f,false); assertEquals(3,a.phase)
        a.step(0.65f,false)
        val f=a.frame(r,0f,0f)
        assertTrue(f.first().docked); assertFalse(f.single { it.id=="helmet" }.docked)
        val moving=rig(650f,1200f)
        val targets=a.attachments(moving,0f,0f)
        val headTarget=targets.single { it.id=="helmet" }
        a.step(0.799f-a.elapsed,false)
        val helmet=a.frame(moving,0f,0f).single { it.id=="helmet" }
        assertEquals(headTarget.x,helmet.x,1f); assertEquals(headTarget.y,helmet.y,1f)
        assertTrue(a.step(0.002f,false)); assertFalse(a.active)
        assertTrue(a.frame(moving,0f,0f).isEmpty())
    }

    @Test fun physicalEdgesKeepFallingHelmetAndPlatesInsideTheirStableEnvelope() {
        for(r in listOf(rig(80f,200f,1.8f),rig(1000f,2300f,1.8f))) {
            val a=scatter(r)
            repeat(600) {
                a.step(1f/120f,true)
                for(p in a.frame(r,0f,0f)) {
                    val rad=Math.toRadians(p.angle.toDouble());val c=kotlin.math.abs(kotlin.math.cos(rad)).toFloat();val ss=kotlin.math.abs(kotlin.math.sin(rad)).toFloat()
                    val rx=(c*p.w+ss*p.h)/2f;val ry=(ss*p.w+c*p.h)/2f
                    assertTrue("screen edge clipped ${p.id}",p.x-rx>=-1f && p.x+rx<=screen.right+1f && p.y-ry>=-1f && p.y+ry<=a.floorY+1f)
                    assertTrue("stable launch envelope clipped ${p.id}",p.x-rx>=a.envelope.left-1f && p.x+rx<=a.envelope.right+1f && p.y-ry>=a.envelope.top-1f && p.y+ry<=a.envelope.bottom+1f)
                }
            }
            assertTrue(a.allSettled)
        }
    }

    private fun marble(v: MonkeyView,strong: Boolean=false) {
        val f=Finger(v); longPress(f)
        f.move((1..14).map { (if(strong) -42f else -14f)*it to (if(strong) 0f else 16f)*it })
        f.up()
    }
    private fun contains(v: MonkeyView) {
        val w=v.debugWin()
        for(p in v.debugArmorPieces) {
            val rad=Math.toRadians(p.angle.toDouble()); val c=kotlin.math.abs(kotlin.math.cos(rad)).toFloat(); val ss=kotlin.math.abs(kotlin.math.sin(rad)).toFloat()
            val rx=(c*p.w+ss*p.h)/2f; val ry=(ss*p.w+c*p.h)/2f
            assertTrue("${p.id} clipped: $p win=${w.toList()}",p.x-rx>=w[0]-1f && p.x+rx<=w[0]+w[2]+1f && p.y-ry>=w[1]-1f && p.y+ry<=w[1]+w[3]+1f)
        }
    }
    @Test fun realMarbleDoesNotShedAtReleaseAndFallenHelmetStaysInOverlayUntilDocking() {
        val v=view("armour"); v.debugPlace(400f,false,900f);v.debugFrames(1);marble(v)
        assertTrue(v.debugMarbleBounce); assertEquals(0,v.debugArmorShedPhase)
        var saw=false; var sawSettled=false; var sawHome=false; var phase=0
        repeat(900) {
            v.debugFrames(1)
            if(v.debugArmorShedPhase!=0) {
                saw=true; assertTrue(v.debugMarbleBounces>0); contains(v)
                assertEquals(14,v.debugArmorPieces.size)
                assertTrue(v.debugArmorPieces.any { it.id=="helmet" })
                assertFalse("no restart on later bounce",phase==2 && v.debugArmorShedPhase==1)
                phase=v.debugArmorShedPhase
                if(phase==2) { sawSettled=true; assertTrue(v.debugArmorAllSettled) }
                if(phase==3 && !sawHome) {
                    sawHome=true; assertFalse(v.debugMarbleBounce);assertFalse(v.debugImpact);assertFalse(v.debugFalling)
                    assertTrue("he is on floor before attraction",v.debugBox().top>=v.debugXY[2]-6f)
                }
            }
        }
        assertTrue(saw && sawSettled && sawHome); assertEquals(0,v.debugArmorShedPhase)
    }

    @Test fun realComicSplatShedsHelmetAndWaitsForBothLandingConditions() {
        val v=view("armour"); v.debugPlace(v.debugXY[3]-v.debugXY[4]-20f,false,800f);v.debugFrames(1);marble(v,true)
        assertTrue(v.debugImpact);assertEquals(0,v.debugArmorShedPhase)
        var hit=false; var attracted=false
        repeat(1000) {
            v.debugFrames(1)
            if(v.debugArmorShedPhase!=0) { hit=true;contains(v) }
            if(v.debugArmorShedPhase==2) assertTrue(v.debugArmorPieces.all { it.settled })
            if(v.debugArmorShedPhase==3 && !attracted) {
                attracted=true;assertFalse(v.debugImpact);assertFalse(v.debugFalling)
                assertTrue(v.debugBox().top>=v.debugXY[2]-6f)
            }
        }
        assertTrue(hit && attracted);assertEquals(0,v.debugArmorShedPhase)
    }

    @Test fun customIronboyAndOtherCharactersNeverShed() {
        val original=CustomCompanionRegistry.primary().spec
        try {
            val iron=original.copy(outfitStyle=OutfitStyle.IRON_ARMOUR_SUIT,flightStyle=FlightStyle.IRONBOY_JET_FLY)
            CustomCompanionRegistry.register(iron)
            for(id in listOf(iron.id,"superboy","monkey")) {
                val v=view(id);v.debugPlace(400f,false,900f);v.debugFrames(1);marble(v)
                repeat(240) { v.debugFrames(1);assertEquals(0,v.debugArmorShedPhase);assertTrue(v.debugArmorPieces.isEmpty()) }
                assertTrue("actually bounced $id",v.debugMarbleBounces>0)
            }
        } finally { CustomCompanionRegistry.register(original) }
    }

    @Test fun fallingMetalDoesNotChangeSettledBoundaryOnlyTiltRule() {
        val v=view("armour");v.debugPlace(400f,false,800f);v.debugFrames(1)
        var t=5000L
        repeat(60) { v.debugTiltFeed(-9.6f,0.4f,0.3f,t);t+=16;v.debugFrames(1) }
        assertEquals(400f,v.debugBox().left,0.1f);assertFalse(v.debugTiltSliding)
        v.debugPlace(400f,true);v.debugFrames(1)
        repeat(90) { v.debugTiltFeed(-9.6f,0.4f,0.3f,t);t+=16;v.debugFrames(1) }
        assertTrue(v.debugBox().left>500f);assertTrue(v.debugTiltSliding)
    }
}
