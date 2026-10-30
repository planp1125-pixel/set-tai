package com.combo.monkeycompanion.engine

import com.combo.monkeycompanion.brain.Landings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M1: the shared per-frame maths on their own (the golden traces cover them inside the real view). */
class PhysicsBodyTest {
    private val p = PhysicsProfile.DEFAULT
    private val dt = 1f / 60f
    private val d = 3f

    @Test fun tweenEndsExactlyOnTarget() {
        val b = PhysicsBody().apply { fromX = 0f; fromY = 100f; toX = 300f; toY = 100f; x = 0f; y = 100f; tweenDur = 0.3f; tweenT = 0f; tweenArc = 40f }
        var ev = StepEvent.NONE; var n = 0; var minY = 100f
        while (ev != StepEvent.TWEEN_DONE && n < 100) { ev = b.stepTween(dt); minY = minOf(minY, b.y); n++ }
        assertEquals(StepEvent.TWEEN_DONE, ev)
        assertEquals(300f, b.x); assertEquals(100f, b.y); assertEquals(-1f, b.tweenT)
        assertTrue("arcs up on the way", minY < 70f)
    }

    @Test fun coyoteHoldsThenLetsGo() {
        val b = PhysicsBody().apply { coyoteT = 1f; vy = 50f }
        var n = 0
        while (b.stepCoyote(dt) != StepEvent.COYOTE_END) { assertEquals(0f, b.vy); n++ }
        assertTrue("about one second ($n frames)", n in 59..61)
    }

    @Test fun simpleFallLandsOnTheGround() {
        val b = PhysicsBody().apply { y = 100f; falling = true }
        var ev = StepEvent.NONE; var n = 0
        while (ev != StepEvent.LANDED && n < 600) { b.fallSpeed(Landings.Style.SIMPLE, dt, d, 1f, 1500f, p); ev = b.fallMove(dt, 1500f, false, d, p); n++ }
        assertEquals(1500f, b.y); assertFalse(b.falling); assertEquals(0f, b.vy)
    }

    @Test fun umbrellaFloatsAtTerminalSpeed() {
        val b = PhysicsBody().apply { y = 0f; falling = true }
        repeat(120) { b.fallSpeed(Landings.Style.SIMPLE, dt, d, 0.3f, 1e9f, p); b.fallMove(dt, 1e9f, false, d, p) }
        assertEquals(p.floatTerminalDp * d, b.vy)
    }

    @Test fun cannonballBouncesOnceWhenAllowed() {
        val b = PhysicsBody().apply { y = 0f; falling = true }
        var bounced = 0; var ev = StepEvent.NONE; var canBounce = true; var n = 0
        while (ev != StepEvent.LANDED && n < 900) {
            b.fallSpeed(Landings.Style.CANNONBALL, dt, d, 1f, 1500f, p)
            ev = b.fallMove(dt, 1500f, canBounce, d, p)
            if (ev == StepEvent.BOUNCED) { bounced++; canBounce = false; assertTrue("bounces up", b.vy < 0f) }
            n++
        }
        assertEquals(1, bounced); assertEquals(StepEvent.LANDED, ev)
    }

    @Test fun edgeCrashRunsAllPhases() {
        val b = PhysicsBody().apply { x = 500f; y = 600f; crashPhase = 1; crashTimer = 0.42f; thrustMultiplier = 0.18f }
        val seen = ArrayList<StepEvent>(); var t = 0f; var n = 0
        while (b.crashPhase != 0 && n < 1000) {
            t += dt
            val ev = b.stepEdgeCrash(dt, t, 1800f, 900f, 1f, d, p)
            if (ev != StepEvent.NONE) seen += ev
            n++
        }
        assertEquals(listOf(StepEvent.CRASH_PLUNGE, StepEvent.CRASH_TOUCHDOWN, StepEvent.CRASH_RECOVERED), seen)
        assertEquals(1800f, b.y); assertEquals(1f, b.thrustMultiplier); assertEquals(0f, b.crashRot)
        assertTrue("drifted the way it faced, clamped on screen", b.x in 500f..900f)
    }
}
