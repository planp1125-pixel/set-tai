package com.combo.monkeycompanion.impact

import com.combo.monkeycompanion.engine.impact.ImpactPlay
import com.combo.monkeycompanion.engine.impact.ImpactPlay.Event
import com.combo.monkeycompanion.engine.impact.ImpactPlay.Kind
import com.combo.monkeycompanion.engine.impact.ImpactPlay.Phase
import com.combo.monkeycompanion.engine.impact.ImpactPlay.Pivot
import com.combo.monkeycompanion.rig.Pose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v10.20 the core physical comedy: splat / thwack / pancake timelines (pure, no Random). */
class ImpactPlayTest {
    private val dt = 1f / 60f

    /** Runs until [stop] or the impact is done; returns the events seen in order. */
    private fun run(p: ImpactPlay, maxS: Float = 10f, stop: (ImpactPlay) -> Boolean = { false }): List<Event> {
        val ev = ArrayList<Event>()
        var t = 0f
        while (t < maxS && !p.done && !stop(p)) { p.step(dt)?.let { ev += it }; t += dt }
        return ev
    }

    @Test fun splatFlattensAgainstTheGlassThenSlidesPeelsFallsAndRecovers() {
        val p = ImpactPlay()
        p.spin = 300f
        p.step(0.1f)
        assertEquals("spins in flight", 30f, p.rot, 0.01f)
        assertTrue(p.hit(Kind.SPLAT, wallSide = 1))
        assertEquals(Pivot.WALL_RIGHT, p.pivot)
        assertEquals(0f, p.rot, 0f)
        assertFalse("only one hit", p.hit(Kind.THWACK))
        assertTrue("comic word shows right away", p.bang > 0.9f)
        run(p, 0.5f)
        assertEquals(Phase.HOLD, p.phase)
        assertTrue("flat as a decal: ${p.sx}", p.sx < 0.42f)
        assertTrue("taller: ${p.sy}", p.sy > 1.15f)
        val ev = run(p) { it.phase == Phase.SLIDE }
        assertEquals(listOf(Event.START_SLIDE), ev)
        assertTrue("slides faster and faster", p.slideDp >= ImpactPlay.SLIDE_START_DP)
        // the overlay reports the floor while sliding: peel right away
        p.step(0.2f); p.slideAtFloor()
        assertEquals(Phase.PEEL, p.phase)
        val peel = run(p) { it.phase == Phase.FALL }
        assertEquals(listOf(Event.START_FALL), peel)
        assertEquals(1f, p.sx, 0.001f); assertEquals(Pivot.CENTER, p.pivot)
        assertTrue("tipped away from the right wall: ${p.rot}", p.rot < -10f)
        run(p, 0.3f)
        assertEquals("keeps falling until the overlay says it landed", Phase.FALL, p.phase)
        p.landed()
        assertTrue(p.stars)
        val rest = run(p)
        assertEquals(listOf(Event.DONE), rest)
        assertTrue(p.done)
        assertEquals(1f, p.sx, 0f); assertEquals(1f, p.sy, 0f); assertEquals(0f, p.rot, 0f)
    }

    @Test fun splatOnTheLeftWallTipsTheOtherWay() {
        val p = ImpactPlay(); p.hit(Kind.SPLAT, -1)
        assertEquals(Pivot.WALL_LEFT, p.pivot)
        run(p) { it.phase == Phase.SLIDE }
        run(p) { it.phase == Phase.FALL }
        assertTrue("tips away from the left wall: ${p.rot}", p.rot > 10f)
    }

    @Test fun slideEndsByItselfIfTheFloorIsFar() {
        val p = ImpactPlay(); p.hit(Kind.SPLAT, 1)
        run(p) { it.phase == Phase.SLIDE }
        run(p, ImpactPlay.SLIDE_MAX_SECS + 0.1f) { it.phase != Phase.SLIDE }
        assertEquals(Phase.PEEL, p.phase)
    }

    @Test fun thwackSquashesUnderTheTopEdgeThenDrops() {
        val p = ImpactPlay(); p.hit(Kind.THWACK)
        assertEquals(Pivot.TOP, p.pivot)
        run(p, 0.3f)
        assertTrue("squashed flat under the edge: ${p.sy}", p.sy < 0.6f && p.sx > 1.2f)
        val ev = run(p) { it.phase == Phase.FALL }
        assertEquals(listOf(Event.START_FALL), ev)
        p.landed()
        assertEquals(listOf(Event.DONE), run(p))
    }

    @Test fun pancakeFlattensThenSpringsBackWithoutAFall() {
        val p = ImpactPlay(); p.hit(Kind.PANCAKE)
        assertEquals(Pivot.FEET, p.pivot)
        run(p, 0.4f)
        assertTrue("flat as a chapati: ${p.sy}", p.sy < 0.4f && p.sx > 1.6f)
        var minSy = 9f; var maxSy = 0f
        val ev = run(p) { it.phase == Phase.DIZZY }.also { }
        assertTrue("no slide or fall: $ev", ev.isEmpty())
        // the spring overshoots (accordion) before settling
        val q = ImpactPlay(); q.hit(Kind.PANCAKE)
        run(q) { it.phase == Phase.SPRING }
        while (q.phase == Phase.SPRING) { q.step(dt); minSy = minOf(minSy, q.sy); maxSy = maxOf(maxSy, q.sy) }
        assertTrue("springs above normal: $maxSy", maxSy > 1.05f)
        assertEquals(listOf(Event.DONE), run(p))
    }

    @Test fun deterministicNoRandom() {
        fun trace(): List<Float> { val p = ImpactPlay(); p.hit(Kind.SPLAT, 1); val out = ArrayList<Float>(); repeat(200) { p.step(dt); out += p.sx; out += p.sy; out += p.rot }; return out }
        assertEquals(trace(), trace())
    }

    @Test fun posesForEveryPhaseAreSafe() {
        val p = ImpactPlay(); p.spin = 200f
        val pose = Pose()
        p.applyPose(pose, 0.3f)
        assertTrue("arms up in flight", pose.armLRaise > 120f)
        p.hit(Kind.SPLAT, -1)
        var t = 0f
        while (!p.done && t < 10f) {
            p.step(dt); t += dt
            if (p.phase == Phase.SLIDE && p.pt > 0.5f) p.slideAtFloor()
            if (p.phase == Phase.FALL && p.pt > 0.3f) p.landed()
            p.applyPose(pose, t)
            assertFalse(pose.eyeOpen.isNaN()); assertFalse(p.sx.isNaN()); assertFalse(p.sy.isNaN())
        }
        assertTrue(p.done)
    }

    @Test fun tossOrPush() {
        val h = 2000f
        assertTrue("tossed from high up", ImpactPlay.isToss(1600f, 900f, h, flyer = false))
        assertFalse("pushed along the floor keeps the edge-hide", ImpactPlay.isToss(3000f, 100f, h, flyer = false))
        assertFalse("too slow", ImpactPlay.isToss(900f, 900f, h, flyer = false))
        assertFalse("a normal fling makes a flyer fly an arc", ImpactPlay.isToss(2100f, 900f, h, flyer = true))
        assertTrue("a very hard throw splats a flyer too", ImpactPlay.isToss(3000f, 50f, h, flyer = true))
    }

    @Test fun linesRotateByCounter() {
        assertEquals(ImpactPlay.line(Kind.SPLAT, 0), ImpactPlay.line(Kind.SPLAT, 5))
        assertFalse(ImpactPlay.line(Kind.SPLAT, 0) == ImpactPlay.line(Kind.SPLAT, 1))
        assertNull(null)
    }
}
