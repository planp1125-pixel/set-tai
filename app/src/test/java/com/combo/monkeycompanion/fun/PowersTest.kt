package com.combo.monkeycompanion.`fun`

import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.PowerFlight
import com.combo.monkeycompanion.brain.PowerKit
import com.combo.monkeycompanion.brain.PowerPhase
import com.combo.monkeycompanion.brain.Superpower
import com.combo.monkeycompanion.brain.Superpowers
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.Animations
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Particles
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.rig.PowerEmitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PowersTest {
    @Test fun registryIsDataDriven() {
        assertTrue(Superpowers.all.size >= 2)
        assertEquals(Superpowers.all.size, Superpowers.all.map { it.id }.toSet().size)
        assertNull(Superpowers.choose(Superpowers.OFF))
        assertEquals(Superpower.RocketMonkey, Superpowers.choose("rocket"))
        assertEquals(Superpower.SuperBanana, Superpowers.choose("banana"))
        repeat(20) { assertTrue(Superpowers.choose(Superpowers.RANDOM) in Superpowers.all) }
    }

    @Test fun everyPlanFliesAndEndsOnTheFloor() {
        for (p in Superpowers.all) for (crash in listOf(false, true)) for (x0 in listOf(0.1f, 0.5f, 0.9f)) {
            val f = PowerFlight()
            val plan = p.plan(x0, 0f, crash, Random(3))
            f.begin(p, plan, crash, x0, 0f)
            var maxY = 0f; var n = 0; var sawCrash = false
            val phases = HashSet<PowerPhase>()
            while (f.update(1f / 60f)) {
                n++; maxY = maxOf(maxY, f.y); phases.add(f.phase)
                assertTrue(f.x in 0f..1f && f.y in 0f..1f)
                if (f.crashed) sawCrash = true
            }
            val flier = p == Superpower.RocketMonkey || p == Superpower.SuperBanana
            if (flier) assertTrue("$p flies high", maxY > 0.4f)
            assertTrue("$p lasts 5-12 s: ${n / 60f}", n / 60f in 5f..12f)
            if (p.crashChance > 0f) assertEquals("$p crash=$crash", crash, sawCrash) else assertTrue("$p never crashes", !sawCrash)
            assertTrue("ends on the floor (${f.y})", f.y < 0.01f)
            if (crash && p.has(PowerKit.FOOT_JETS)) assertTrue(PowerPhase.SPUTTER in phases)
            if (p.has(PowerKit.PALM_RINGS)) assertTrue(PowerPhase.FIRE in phases)
        }
    }

    @Test fun showOffPowersAreRegisteredAndNamed() {
        val ids = Superpowers.all.map { it.id }.toSet()
        for (id in listOf("thunder", "ninja", "kovam", "ice", "giant", "freeze", "magic")) assertTrue(id, id in ids)
        assertEquals(9, Superpowers.all.size)
        assertEquals(Superpower.NinjaSettai, Superpowers.named("do the ninja thing"))
        assertEquals(Superpower.KovamMode, Superpowers.named("kovam mode please"))
        assertEquals(Superpower.TimeFreeze, Superpowers.named("matrix dodge"))
        assertEquals(Superpower.Magician, Superpowers.named("show me a magic trick"))
        assertEquals(Superpower.ThunderMonkey, Superpowers.named("thunder power!"))
        assertEquals(Superpower.IceMonkey, Superpowers.named("cool the phone down with ice"))
        assertEquals(Superpower.GiantMode, Superpowers.named("giant mode"))
        assertNull(Superpowers.named("hello there"))
        // Random selection reaches the new ones too.
        val seen = HashSet<Superpower>(); val r = Random(5)
        repeat(400) { Superpowers.choose(Superpowers.RANDOM, r)?.let { seen.add(it) } }
        assertEquals(Superpowers.all.toSet(), seen)
        // A named request is used once, and Off still wins.
        Superpowers.requested = Superpower.GiantMode
        assertEquals(Superpower.GiantMode, Superpowers.choose("rocket"))
        assertEquals(Superpower.RocketMonkey, Superpowers.choose("rocket"))
        Superpowers.requested = Superpower.GiantMode
        assertNull(Superpowers.choose(Superpowers.OFF)); assertNull(Superpowers.requested)
        // Chat: asking by name picks that power.
        assertEquals(com.combo.monkeycompanion.ai.BodyAction.FLY, com.combo.monkeycompanion.ai.ActionParser.requestedByUser("ninja mode da"))
        assertEquals(Superpower.NinjaSettai, Superpowers.requested); Superpowers.requested = null
    }

    @Test fun showOffPosesDoTheirThing() {
        val pose = Pose(); val c = AnimContext()
        fun at(p: Superpower, ph: PowerPhase, k: Float): Pose { c.power = p; c.powerPhase = ph; c.powerK = k; c.crashed = false
            Animations.fill(pose, MonkeyState.POWER, 1f, 1f, c); return pose }
        assertTrue(at(Superpower.KovamMode, PowerPhase.GROW, 1f).size > 1.7f)
        assertTrue(at(Superpower.KovamMode, PowerPhase.SHRINK, 1f).size < 0.7f)
        assertTrue(at(Superpower.KovamMode, PowerPhase.POSE, 1f).size in 0.99f..1.01f)
        assertTrue(at(Superpower.GiantMode, PowerPhase.PEEK, 0.5f).fade < 0.01f)
        assertTrue(at(Superpower.NinjaSettai, PowerPhase.CLONES, 0.5f).clones > 0.9f)
        assertTrue(at(Superpower.NinjaSettai, PowerPhase.TELEPORT, 0.5f).fade < 0.01f)
        assertTrue(at(Superpower.IceMonkey, PowerPhase.FROST, 0.8f).frost > 0.9f)
        assertTrue(at(Superpower.Magician, PowerPhase.MAGIC, 0.9f).rabbit > 0.9f)
        assertTrue(at(Superpower.ThunderMonkey, PowerPhase.CHARGE, 0.9f).staffGlow > 0.5f)
        assertTrue(at(Superpower.TimeFreeze, PowerPhase.DODGE, 0.5f).bodyRot < -40f)
        // Everything resets outside the power.
        Animations.fill(pose, MonkeyState.IDLE, 1f, 1f, c)
        assertEquals(1f, pose.size); assertEquals(0f, pose.clones); assertEquals(0f, pose.frost)
        for (ph in PowerPhase.values()) assertTrue(ph.name, ph == PowerPhase.TELEPORT || ph.screenFx || ph.ordinal <= PowerPhase.POSE.ordinal || ph == PowerPhase.CLONES || ph == PowerPhase.GROW || ph == PowerPhase.MAGIC ||
            ph == PowerPhase.CONFETTI || ph == PowerPhase.SHIELD || ph == PowerPhase.DROOP)
    }

    @Test fun particlesStayCappedAndDrain() {
        val parts = Particles(); val em = PowerEmitter(parts)
        val rig = MonkeyRig(); val pose = Pose(); val c = AnimContext()
        var rings = 0
        for (p in Superpowers.all) {
            val f = PowerFlight(); f.begin(p, p.plan(0.5f, 0f, true, Random(1)), true, 0.5f, 0f)
            c.power = p
            var t = 0f
            while (f.update(1f / 60f)) {
                t += 1f / 60f
                c.powerPhase = f.phase; c.powerK = f.k; c.speed = 1f; c.heading = 90f; c.crashed = f.crashed
                Animations.fill(pose, MonkeyState.POWER, t, t, c)
                for (i in pose.v.indices) assertTrue(pose.v[i].isFinite())
                rig.layout(pose, 500f, 800f, 2.5f, 1f)
                val before = parts.count
                em.frame(rig, pose, p, f.phase, f.index, f.k, 1400f, -300f, 1f, 2.5f, 1f / 60f)
                if (f.phase == PowerPhase.FIRE && parts.count > before) rings++
                assertTrue(parts.count <= Particles.CAP)
            }
        }
        assertTrue("rings fired: $rings", rings > 0)
        repeat(120) { parts.update(1f / 60f) }
        assertEquals("drains when the power ends", 0, parts.count)
    }
}
