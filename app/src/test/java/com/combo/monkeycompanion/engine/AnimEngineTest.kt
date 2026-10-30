package com.combo.monkeycompanion.engine

import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.engine.anim.AnimEngine
import com.combo.monkeycompanion.engine.anim.AnimTuning
import com.combo.monkeycompanion.engine.anim.Appendage
import com.combo.monkeycompanion.engine.anim.Easing
import com.combo.monkeycompanion.engine.anim.Spring
import com.combo.monkeycompanion.engine.anim.SpringChain
import com.combo.monkeycompanion.engine.anim.SquashStretch
import com.combo.monkeycompanion.rig.Pose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** v10.7 Disney-principles part 1 engine. */
class AnimEngineTest {
    @Test fun springOvershootsThenSettles() {
        val s = Spring(150f, 0.4f)
        var peak = 0f
        repeat(120) { peak = maxOf(peak, s.step(1f / 60f, 10f)) }   // 2 s
        assertTrue("overshoots (follow-through)", peak > 10.5f)
        assertEquals(10f, s.value, 0.05f)
        assertTrue(s.isSettled)
    }

    @Test fun springIsDeterministicAndFrameRateIndependent() {
        fun run(fps: Int): Float { val s = Spring(160f, 0.42f); repeat(fps * 2) { s.step(1f / fps, 20f) }; return s.value }
        assertEquals(run(60), run(60), 0f)
        assertEquals(run(60), run(120), 0.05f)
        assertEquals(run(60), run(8), 0.2f)
        val a = SpringChain(3); val b = SpringChain(3)
        repeat(50) { assertEquals(a.step(0.016f, 15f), b.step(0.016f, 15f), 0f) }
    }

    @Test fun squashPreservesVolumeAndClamps() {
        val pairs = listOf(0.84f to 1.1f, 1.06f to 0.95f, 0.54f to 1.35f, 1.2f to 0.8f, 1f to 1f)
        for ((sy, sx) in pairs) {
            val y = SquashStretch.preserveY(sx, sy, 0.7f, 1.3f)
            val x = 1f / y
            assertEquals("area for $sx,$sy", 1f, SquashStretch.area(x, y), 1e-4f)
            assertTrue(y in 0.7f..1.3f)
            if (sy < sx) assertTrue(y < 1f) else if (sy > sx) assertTrue(y > 1f)
        }
        assertTrue(SquashStretch.velocityStretch(5000f, 0.12f) <= 1.12f + 1e-6f)
        assertEquals(1f, SquashStretch.velocityStretch(100f, 0.12f), 0f)
    }

    @Test fun engineKeepsAreaAndTailLagsAfterAStop() {
        val e = AnimEngine(); e.setTuning(AnimTuning.MONKEY)
        val p = Pose()
        var x = 0f
        // run right at ~600 dp/s for 0.5 s, then stop dead
        repeat(30) { p.squashX = 1.1f; p.squashY = 0.84f; x += 10f; e.step(p, 1f / 60f, x, 500f, 1f, 1f, false)
            assertEquals(1f, p.squashX * p.squashY, 1e-3f) }
        val moving = e.appendageAngle(Appendage.TAIL)
        assertTrue("tail streams while running", abs(moving) > 2f)
        var crossed = false
        repeat(60) { e.step(p, 1f / 60f, x, 500f, 1f, 1f, false); if (e.appendageAngle(Appendage.TAIL) * moving < 0f) crossed = true }
        assertTrue("tail overshoots past rest after the stop", crossed)
        repeat(60) { e.step(p, 1f / 60f, x, 500f, 1f, 1f, false) }
        assertEquals(0f, e.appendageAngle(Appendage.TAIL), 0.5f)
    }

    @Test fun easingEnds() {
        for (f in listOf<(Float) -> Float>(Easing::smoothstep, Easing::easeInOutCubic, Easing::easeInOutSine, { Easing.easeOutBack(it) })) {
            assertEquals(0f, f(0f), 1e-5f); assertEquals(1f, f(1f), 1e-5f)
        }
        assertTrue(Easing.easeOutBack(0.7f) > 1f)
    }

    @Test fun everyCharacterHasTuning() {
        for (c in CharacterRegistry.all) assertTrue(c.id, CharacterProfiles.of(c).anim.enabled)
        assertTrue(CharacterProfiles.GAJJU.anim.trunk.stiffness < CharacterProfiles.BILLA.anim.tail.stiffness)
    }
}
