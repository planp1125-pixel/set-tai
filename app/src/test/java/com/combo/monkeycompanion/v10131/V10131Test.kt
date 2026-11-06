package com.combo.monkeycompanion.v10131

import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.Onboarding
import com.combo.monkeycompanion.engine.show.ChaseRun
import com.combo.monkeycompanion.engine.show.EndKind
import com.combo.monkeycompanion.engine.show.Role
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/** v10.13.1: the full-screen chase is smooth, close, never flip-flops and uses the whole screen. */
class V10131Test {
    private fun runs() = (1..40).map { ChaseRun.random(Random(it.toLong()), EndKind.entries[it % 4], 2.1f) }

    @Test fun lengthIs12To15Seconds() { for (r in runs()) assertTrue("len ${r.total}", r.total in 12f..15f) }

    @Test fun noTeleportNoShakeNoFlipFlop() {
        for (r in runs()) for (role in Role.entries) {
            val a = ChaseRun.Pos(); val b = ChaseRun.Pos()
            r.frame(role, 0f, a)
            var t = 0f; var facingFlips = 0; var stateFlips = 0
            while (t < r.total) {
                t += 1f / 60f
                r.frame(role, t, b)
                assertTrue("$role jumped ${hypot(b.x - a.x, b.y - a.y)} at $t", hypot(b.x - a.x, b.y - a.y) < 0.03f)
                var da = abs(b.ang - a.ang) % 360f; if (da > 180f) da = 360f - da
                assertTrue("$role spun $da° in one frame at $t", da < 15f)
                if (b.facing != a.facing) facingFlips++
                if (b.state != a.state) stateFlips++
                a.x = b.x; a.y = b.y; a.ang = b.ang; a.facing = b.facing; a.state = b.state
            }
            assertTrue("facing flip-flops: $facingFlips", facingFlips <= r.legs.size)
            assertTrue("state flip-flops: $stateFlips", stateFlips <= r.legs.size + 4)
        }
    }

    @Test fun catRunsCloseBehindTheMouse() {
        for (r in runs()) {
            val c = ChaseRun.Pos(); val m = ChaseRun.Pos()
            var t = r.intro + 1f
            while (t < r.intro + r.run) {
                r.frame(Role.CAT, t, c); r.frame(Role.RAT, t, m)
                assertTrue("cat ahead at $t", c.dist < m.dist)
                assertTrue("cat too far behind at $t: ${m.dist - c.dist}", m.dist - c.dist < 0.3f)
                assertTrue(c.state == MonkeyState.DASH || c.state == MonkeyState.JUMP)
                t += 0.1f
            }
        }
    }

    @Test fun usesTheWholeScreen() {
        for (r in runs()) {
            val p = ChaseRun.Pos(); var minY = 9f; var minX = 9f; var maxX = -9f; var t = 0f
            while (t < r.total) { r.frame(Role.RAT, t, p); minY = minOf(minY, p.y); minX = minOf(minX, p.x); maxX = maxOf(maxX, p.x); t += 0.05f }
            assertTrue("never goes up high: $minY", minY < 2.1f * 0.5f)
            assertTrue("doesn't reach a wall", minX < 0.05f || maxX > 0.95f)
        }
    }

    @Test fun onboardingStillFiveSteps() { assertTrue(Onboarding.steps.size == 5) }
}
