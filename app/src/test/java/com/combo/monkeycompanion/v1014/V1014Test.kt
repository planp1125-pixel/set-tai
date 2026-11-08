package com.combo.monkeycompanion.v1014

import com.combo.monkeycompanion.brain.*
import com.combo.monkeycompanion.character.*
import com.combo.monkeycompanion.engine.CharacterProfiles
import com.combo.monkeycompanion.engine.anim.AnimEngine
import com.combo.monkeycompanion.engine.anim.LivingLayer
import com.combo.monkeycompanion.engine.skit.SkitLibrary
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.world.Population
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class V1014Test {
    @Test fun bothNewCharactersAreInThePickerAndCrew() {
        for (id in listOf("hollowman", "surfer")) {
            assertNotNull(id, CharacterRegistry.all.firstOrNull { it.id == id })
            assertTrue(id, id in Population.allIds)
            assertNotEquals("Pogo", Population.displayName(id))
            assertNotSame(CharacterProfiles.DEFAULT, CharacterProfiles.of(CharacterRegistry.byId(id)!!))
        }
        assertEquals("hollowman", CharacterRegistry.byId("Echo")!!.id)
        assertEquals("surfer", CharacterRegistry.byId("surfing girl")!!.id)
    }

    @Test fun mainPowerFirstIsMoonwalkAndBigWave() {
        assertSame(MoonwalkPower, HollowMan.signaturePower)
        assertSame(BigWavePower, SurferGirl.signaturePower)
        assertTrue(HollowMan.powers.size >= 4); assertTrue(SurferGirl.powers.size >= 3)
    }

    @Test fun newPowerPlansStayOnScreenAndAreOrdered() {
        val all = HollowMan.powers + SurferGirl.powers
        for (p in all) for (x0 in listOf(0.1f, 0.5f, 0.9f)) for (crash in listOf(false, true)) {
            val plan = p.plan(x0, 0f, crash, Random(7))
            assertTrue(p.id, plan.isNotEmpty() && plan.all { it.secs > 0f })
            for (s in plan) {
                if (!s.tx.isNaN()) assertTrue("${p.id} x ${s.tx}", s.tx in 0f..1f)
                if (!s.ty.isNaN()) assertTrue("${p.id} y ${s.ty}", s.ty in 0f..1f)
            }
            val secs = plan.sumOf { it.secs.toDouble() }
            assertTrue("${p.id} lasts $secs s", secs in 2.0..(if (p === BigWavePower) 11.5 else 9.0))   // v10.22: a real surf ride takes a bit longer
        }
        // moonwalk crosses the screen
        val mw = MoonwalkPower.plan(0.15f, 0f, false, Random(1)).first { it.phase == PowerPhase.FLY }
        assertTrue(abs(mw.tx - 0.15f) > 0.6f)
        // v10.22 big wave: a real surf ride on the water (low on the screen), gliding across and back, carving
        val bw = BigWavePower.plan(0.3f, 0f, false, Random(1)).filter { !it.ty.isNaN() }
        assertTrue(bw.all { it.ty <= 0.2f } && bw.any { it.tx > 0.85f } && bw.count { it.glide > 0f } >= 2)
    }

    private fun pose(ch: Character, s: MonkeyState, t: Float, pw: Superpower?, ph: PowerPhase, k: Float, crashed: Boolean = false): Pose {
        val p = Pose(); val c = AnimContext(); c.power = pw; c.powerPhase = ph; c.powerK = k; c.crashed = crashed
        ch.fill(p, s, t, 0.5f, c, Wear(ch)); return p
    }

    @Test fun moonwalkFacesAwayWhileGlidingAndHeelPopsAlternate() {
        val p = pose(HollowMan, MonkeyState.POWER, 1f, MoonwalkPower, PowerPhase.FLY, 0.5f)
        assertEquals(1f, p.hmMoon, 0f)
        var lUp = 0; var rUp = 0
        for (i in 0 until 60) {
            val q = pose(HollowMan, MonkeyState.POWER, i / 30f, MoonwalkPower, PowerPhase.FLY, 0.5f)
            if (q.legLLift > 4f) lUp++; if (q.legRLift > 4f) rUp++
            assertFalse("never both feet up", q.legLLift > 4f && q.legRLift > 4f)
        }
        assertTrue(lUp > 5 && rUp > 5)
        assertEquals(0f, pose(HollowMan, MonkeyState.IDLE, 1f, null, PowerPhase.POSE, 0f).hmMoon, 0f)
    }

    @Test fun danceCyclesThroughAllFiveMoves() {
        val seen = (0 until 50).map { HollowMan.danceMove(it * 0.4f) }.toSet()
        assertEquals(setOf(0, 1, 2, 3, 4), seen)
        // spin: x scale goes through the back (negative) and returns to the front
        val xs = (0..40).map { pose(HollowMan, MonkeyState.POWER, 1f, DanceSpinPower, PowerPhase.POSE, it / 40f).spinX }
        assertTrue(xs.any { it < -0.9f }); assertEquals(1f, xs.last(), 0.02f)
    }

    @Test fun hatTipLiftsTheHat() {
        assertTrue(pose(HollowMan, MonkeyState.POWER, 1f, HatTipPower, PowerPhase.POSE, 0.5f).hmHatTip > 0.9f)
    }

    @Test fun aerialFlipIsContinuousAndLandsUpright() {
        var lastA = Double.NaN
        for (i in 0..60) {
            val p = pose(SurferGirl, MonkeyState.POWER, 1f, AerialFlipPower, PowerPhase.FLY, i / 60f)
            assertEquals(1f, hypot(p.flipS, p.flipC), 1e-3f)
            val a = Math.atan2(p.flipS.toDouble(), p.flipC.toDouble())
            if (!lastA.isNaN()) { var d = a - lastA; while (d > Math.PI) d -= 2 * Math.PI; while (d < -Math.PI) d += 2 * Math.PI; assertTrue("no snap at $i", abs(d) < 0.6) }
            lastA = a
        }
        val end = pose(SurferGirl, MonkeyState.POWER, 1f, AerialFlipPower, PowerPhase.FLY, 1f)
        assertEquals(0f, end.flipS, 1e-3f); assertEquals(1f, end.flipC, 1e-3f)
        val wipe = pose(SurferGirl, MonkeyState.POWER, 1f, AerialFlipPower, PowerPhase.LAND, 0.4f, crashed = true)
        assertTrue(wipe.surfSpray > 0.9f && wipe.eyeOpen < 0.5f)
    }

    @Test fun surferAlwaysStandsOnHerWave() {
        for (s in listOf(MonkeyState.IDLE, MonkeyState.WALK, MonkeyState.DANCE, MonkeyState.TALKING)) assertEquals(s.name, 1f, pose(SurferGirl, s, 1f, null, PowerPhase.POSE, 0f).surf, 0f)
    }

    @Test fun skitsAndBanterForBoth() {
        assertTrue(SkitLibrary.all.any { sk -> sk.cast.first().fits("hollowman") && !sk.cast.first().fits("monkey") })
        assertTrue(SkitLibrary.all.any { sk -> sk.cast.first().fits("surfer") && !sk.cast.first().fits("monkey") })
        for (c in listOf(HollowMan, SurferGirl)) {
            assertNotNull(c.voice)
            assertTrue(c.identity("phone").contains("original"))
        }
    }

    @Test fun squashStretchStaysWithin085And12ForEveryCharacter() {
        for (c in CharacterRegistry.all) {
            val e = AnimEngine(); e.setTuning(CharacterProfiles.of(c).anim)
            val p = Pose(); var y = 0f
            for (i in 0 until 240) {
                p.squashX = if (i % 2 == 0) 1.6f else 0.6f; p.squashY = if (i % 2 == 0) 0.5f else 1.7f
                y += if (i < 120) 40f else 0f
                e.step(p, 1f / 60f, 500f + i * 30f, y, 1f, 1f, i < 120)
                assertTrue("${c.id} sy ${p.squashY}", p.squashY in 0.849f..1.201f)
            }
        }
    }

    @Test fun livingLayerGetsBoredWatchesTheFingerAndNoticesPartners() {
        val l = LivingLayer(3L); val p = Pose()
        var bits = 0
        for (i in 0 until 60 * 60) { p.reset(); l.step(p, 1f / 60f, null, true, false, 0f, 1f, 0f); if (l.idleBit >= 0) bits++ }
        assertTrue("an idle-variety bit plays within a minute of calm", bits > 0)
        // finger to the right: eyes go right
        l.reset()
        repeat(30) { p.reset(); l.watchFinger(1f, 0f); l.step(p, 1f / 60f, null, true, false, 0f, 1f, 0f) }
        assertTrue("eyes follow the finger (${p.lookX})", p.lookX > 2f)
        l.fingerUp()
        // partner takes the stage: one notice pop
        val n0 = l.notices
        repeat(10) { p.reset(); l.step(p, 1f / 60f, null, true, true, 1f, 1f, 0f) }
        assertEquals(n0 + 1, l.notices)
    }

    @Test fun livingLayerNeverTouchesTheSharedRandom() {
        // its own seeded RNG: two layers with the same seed produce identical poses
        val a = LivingLayer(9L); val b = LivingLayer(9L); val pa = Pose(); val pb = Pose()
        for (i in 0 until 2000) {
            pa.reset(); pb.reset()
            a.step(pa, 1f / 60f, null, true, false, 0f, 1f, 0f); b.step(pb, 1f / 60f, null, true, false, 0f, 1f, 0f)
            assertArrayEquals(pa.v, pb.v, 0f)
        }
    }

    /** v10.22 the surf ride glides: one continuous path, carves up and down the face, slows to a soft stop. */
    @Test fun bigWaveGlidesAndCarves() {
        val f = PowerFlight()
        f.begin(BigWavePower, BigWavePower.plan(0.2f, 0.03f, false, Random(2)), false, 0.2f, 0.03f)
        var lx = f.x; var ly = f.y; var leanPos = false; var leanNeg = false; var maxY = 0f
        var slowAtEnd = 1f
        while (f.update(1f / 60f)) {
            assertTrue("no jumps", abs(f.x - lx) < 0.03f && abs(f.y - ly) < 0.03f)
            lx = f.x; ly = f.y; maxY = maxOf(maxY, f.y)
            if (f.phase == PowerPhase.FLY) { if (f.glideLean > 5f) leanPos = true; if (f.glideLean < -5f) leanNeg = true
                if (f.index == 1 && f.k > 0.98f) slowAtEnd = f.glideSpeed }
        }
        assertTrue("carves both ways", leanPos && leanNeg)
        assertTrue("stays on the water: $maxY", maxY < 0.25f)
        assertTrue("glides to a soft stop: $slowAtEnd", slowAtEnd < 0.1f)
    }
}
