package com.combo.monkeycompanion.v1016

import com.combo.monkeycompanion.ai.BodyAction
import com.combo.monkeycompanion.brain.*
import com.combo.monkeycompanion.character.*
import com.combo.monkeycompanion.engine.CharacterProfiles
import com.combo.monkeycompanion.engine.skit.SkitConductor
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.world.Population
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class V1016DevilTest {
    @Test fun devilIsRegisteredFirstInPickersAndCrew() {
        assertEquals("devil", CharacterRegistry.all.first().id)
        assertSame(KuttiSatan, CharacterRegistry.byId("devil"))
        assertSame(KuttiSatan, CharacterRegistry.byId("Kutti Satan"))
        assertSame(KuttiSatan, CharacterRegistry.byId("satan"))
        assertTrue("devil" in Population.allIds)
        assertEquals("Kutti Satan", Population.displayName("devil"))
        assertEquals("Kutti Satan", KuttiSatan.name)
        assertNotEquals(MonkeyCharacter.name, KuttiSatan.name)
        assertNotSame(CharacterProfiles.DEFAULT, CharacterProfiles.of(KuttiSatan))
        assertTrue(KuttiSatan.can(Locomotion.FLOAT))
        assertTrue(SkitConductor(3).eligible(listOf("devil", "billa")).isNotEmpty())
        assertTrue(SkitConductor(3).eligible(listOf("devil", "monkey")).isNotEmpty())
    }

    @Test fun powersRegisteredMainFirst() {
        assertSame(VanishPortalPower, KuttiSatan.signaturePower)
        assertSame(VanishPortalPower, KuttiSatan.powers.first())
        val ids = KuttiSatan.powers.map { it.id }.toSet()
        assertEquals(setOf("vanish", "devilhop", "floatbuddy", "spellsparks", "shadowclone", "devilfloat"), ids)
        assertEquals(ids.size, KuttiSatan.powers.size)
    }

    @Test fun powerPlansStayOnScreenAndLastSensibly() {
        for (p in KuttiSatan.powers) for (x0 in listOf(0f, 0.1f, 0.49f, 0.51f, 0.9f, 1f)) for (y0 in listOf(0f, 0.5f, 1f)) {
            val plan = p.plan(x0, y0, false, Random(11))
            assertTrue(p.id, plan.isNotEmpty() && plan.all { it.secs > 0f })
            for (s in plan) {
                if (!s.tx.isNaN()) assertTrue("${p.id} x ${s.tx}", s.tx in 0f..1f)
                if (!s.ty.isNaN()) assertTrue("${p.id} y ${s.ty}", s.ty in 0f..1f)
            }
            val secs = plan.sumOf { it.secs.toDouble() }
            assertTrue("${p.id} lasts $secs s", secs in 2.0..9.0)
        }
    }

    @Test fun vanishReappearsOnTheOppositeSideInsideTheScreen() {
        for (i in 0..20) {
            val x0 = i / 20f
            val y0 = (i % 5) / 5f
            val f = PowerFlight()
            val plan = VanishPortalPower.plan(x0, y0, false, Random(i))
            f.begin(VanishPortalPower, plan, false, x0, y0)
            var vanished = false; var lastX = x0; var lastY = y0
            var guard = 0
            while (f.update(1f / 60f) && guard++ < 2000) {
                if (f.phase == PowerPhase.TELEPORT) vanished = true
                lastX = f.x; lastY = f.y
            }
            assertTrue(vanished)
            assertTrue("x0=$x0 -> $lastX", lastX in 0.05f..0.95f && lastY in 0f..1f)
            if (x0 < 0.5f) assertTrue("x0=$x0 -> $lastX", lastX > 0.5f) else assertTrue("x0=$x0 -> $lastX", lastX < 0.5f)
        }
    }

    private fun pose(s: MonkeyState, t: Float, pw: Superpower? = null, ph: PowerPhase = PowerPhase.POSE, k: Float = 0.5f): Pose {
        val p = Pose(); val c = AnimContext(); c.power = pw; c.powerPhase = ph; c.powerK = k
        KuttiSatan.fill(p, s, t, 0.5f, c, Wear(KuttiSatan)); return p
    }

    @Test fun blinksEveryTwoToSixSecondsWithSomeDoubles() {
        val starts = ArrayList<Float>()
        var was = false
        var t = 0f
        while (t < 400f) {
            val shut = DevilBlink.closed(t) > 0.5f
            if (shut && !was) starts.add(t)
            was = shut; t += 0.01f
        }
        assertTrue(starts.size > 60)
        // group double-blinks (second blink within 0.5 s)
        val groups = ArrayList<Float>(); var doubles = 0
        for (s in starts) if (groups.isEmpty() || s - groups.last() > 0.5f) groups.add(s) else doubles++
        for (i in 1 until groups.size) assertTrue("gap ${groups[i] - groups[i - 1]}", groups[i] - groups[i - 1] in 1.9f..6.1f)
        assertTrue("some double blinks", doubles >= 5)
        assertTrue("mostly single", doubles < groups.size / 2)
        // a blink actually closes the drawn eyes, and they open again right after
        val n = 7; val at = DevilBlink.blinkAt(n)
        assertTrue(pose(MonkeyState.IDLE, at + 0.07f).eyeOpen < 0.2f)
        assertEquals(0f, DevilBlink.closed(at + 0.25f + (if (DevilBlink.isDouble(n)) 0.4f else 0f)), 0.001f)
    }

    @Test fun vanishPoseFadesOutThenPortalOpens() {
        val early = pose(MonkeyState.POWER, 1f, VanishPortalPower, PowerPhase.TELEPORT, 0.3f)
        assertTrue(early.dvSmoke > 0.5f && early.dvPortal < 0.1f)
        val mid = pose(MonkeyState.POWER, 1f, VanishPortalPower, PowerPhase.TELEPORT, 0.5f)
        assertTrue(mid.fade < 0.05f)
        val late = pose(MonkeyState.POWER, 1f, VanishPortalPower, PowerPhase.TELEPORT, 0.7f)
        assertTrue(late.dvPortal > 0.5f && late.puff > 0.01f)
        // anticipation crouch before the poof
        assertTrue(pose(MonkeyState.POWER, 1f, VanishPortalPower, PowerPhase.DROOP, 0.5f).squashY < 0.95f)
        // other magic fx
        assertTrue(pose(MonkeyState.POWER, 1f, SpellSparksPower, PowerPhase.POSE, 0.5f).dvSpell > 0.9f)
        assertTrue(pose(MonkeyState.POWER, 1f, ShadowClonePower, PowerPhase.POSE, 0.5f).dvClone > 0.9f)
        assertTrue(pose(MonkeyState.POWER, 1f, FloatBuddyPower, PowerPhase.POSE, 0.5f).dvAura > 0.9f)
        assertEquals(0f, pose(MonkeyState.IDLE, 1f).dvSmoke, 0f)
    }

    @Test fun floatsAndTailSwishes() {
        val a = pose(MonkeyState.IDLE, 1f); val b = pose(MonkeyState.IDLE, 2.3f)
        assertTrue(a.bodyY < -1f)
        assertNotEquals(a.tailRot, b.tailRot, 1f)
    }

    @Test fun bodySenseAndChargerHooks() {
        assertEquals(BodyAction.JUMP, KuttiSatan.mapAction(BodyAction.CLIMB, Wear(KuttiSatan)))
        assertEquals(BodyAction.DANCE, KuttiSatan.mapAction(BodyAction.DANCE, Wear(KuttiSatan)))
        assertTrue(KuttiSatan.body(Wear(KuttiSatan), true).contains("[power]"))
        assertTrue(KuttiSatan.identity("phone").contains("Kutti Satan"))
        for (m in listOf(PhoneMoments.Moment.CHARGE, PhoneMoments.Moment.UNPLUG, PhoneMoments.Moment.FULL, PhoneMoments.Moment.LOW))
            assertTrue(PhoneMoments.line(m, "devil", Random(1)).contains("😈") || PhoneMoments.line(m, "devil", Random(2)).isNotBlank())
        assertTrue(PhoneMoments.line(PhoneMoments.Moment.CHARGE, "devil", Random(1)).let { it.contains("😈") || it.contains("tail") })
    }
}
