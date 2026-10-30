package com.combo.monkeycompanion.engine

import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.TouchZone
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.engine.anim.AnimEngine
import com.combo.monkeycompanion.engine.anim.AnimTuning
import com.combo.monkeycompanion.engine.anim.Antic
import com.combo.monkeycompanion.engine.anim.LivingLayer
import com.combo.monkeycompanion.engine.touch.BodyPart
import com.combo.monkeycompanion.engine.touch.MoodSnapshot
import com.combo.monkeycompanion.engine.touch.ReactionLayer
import com.combo.monkeycompanion.engine.touch.ReactionPicker
import com.combo.monkeycompanion.engine.touch.TouchKind
import com.combo.monkeycompanion.engine.touch.TouchTracker
import com.combo.monkeycompanion.engine.touch.Verb
import com.combo.monkeycompanion.mind.Personality
import com.combo.monkeycompanion.rig.Pose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v10.8 M2-M4 touch engine + Disney part 2 layer: no immediate repeats, seeded determinism, legacy kept. */
class LivingReactionsTest {
    private val parts = listOf(BodyPart.HEAD, BodyPart.BELLY, BodyPart.TAIL, BodyPart.HAND, BodyPart.FOOT, BodyPart.EAR, BodyPart.TRUNK, BodyPart.PROP)
    private fun zoneOf(p: BodyPart) = when (p) {
        BodyPart.HEAD, BodyPart.EAR, BodyPart.TRUNK -> TouchZone.HEAD
        BodyPart.TAIL -> TouchZone.TAIL; BodyPart.HAND -> TouchZone.HAND; BodyPart.FOOT -> TouchZone.FOOT
        else -> TouchZone.BELLY
    }

    private fun run(id: String, seed: Long, n: Int, part: BodyPart? = null, kind: TouchKind = TouchKind.TAP): List<Verb> {
        val ch = CharacterRegistry.byId(id)!!
        val prof = CharacterProfiles.of(ch)
        val picker = ReactionPicker(seed); val tracker = TouchTracker()
        val out = ArrayList<Verb>()
        var now = 10f
        val r = java.util.Random(seed)   // only to choose test inputs
        repeat(n) {
            val p = part ?: parts[r.nextInt(parts.size)]
            val inp = tracker.build(p, zoneOf(p), if (r.nextBoolean()) 1 else -1, kind, r.nextFloat() * 1500f, 1f, 0f, r.nextFloat(), 0.08f + r.nextFloat() * 0.1f, now)
            val pick = picker.pick(prof.reactionTable, inp, MoodSnapshot(0f, 1f), Personality.base(id), prof.anim.expressiveness, prof.anim.tempo, now)
            if (pick != null) out += pick.verb
            now += 0.4f + r.nextFloat() * 3f
        }
        return out
    }

    @Test fun neverTheSameVerbTwiceInARow() {
        for (c in CharacterRegistry.all) for (p in parts) {
            val v = run(c.id, 42L + p.ordinal, 120, part = p)
            for (i in 1 until v.size) assertNotEquals("${c.id} ${p}: ${v.subList(maxOf(0, i - 3), i + 1)}", v[i - 1], v[i])
        }
    }

    @Test fun notTheLastTwoWhenThereIsAChoice() {
        val v = run("monkey", 7L, 300, part = BodyPart.HEAD)
        val distinctHead = v.toSet().size
        assertTrue("head library should be rich: $distinctHead", distinctHead >= 4)
        for (i in 2 until v.size) assertTrue("repeat of last two at $i: ${v.subList(i - 2, i + 1)}", v[i] != v[i - 1] && v[i] != v[i - 2])
    }

    @Test fun sameSeedSameReactions() {
        assertEquals(run("gajju", 1234L, 80), run("gajju", 1234L, 80))
        assertNotEquals(run("gajju", 1234L, 80), run("gajju", 999L, 80))
    }

    @Test fun legacyReactionsAreStillInTheLibrary() {
        for (c in CharacterRegistry.all) {
            val lib = CharacterProfiles.of(c).reactionTable
            for (p in listOf(BodyPart.HEAD, BodyPart.BELLY, BodyPart.HAND)) assertTrue("${c.id} $p legacy", lib.any { it.verb == Verb.LEGACY && p in it.parts })
            assertTrue("${c.id}: legacy shows up sometimes", run(c.id, 5L, 200).contains(Verb.LEGACY))
        }
    }

    @Test fun tailAndEarReactionsForEveryoneWhoHasThem() {
        for (c in CharacterRegistry.all) {
            val b = CharacterProfiles.of(c).body
            val lib = CharacterProfiles.of(c).reactionTable
            if (b.tail) assertTrue(c.id, lib.any { BodyPart.TAIL in it.parts && it.verb.name.startsWith("TAIL") })
            if (b.ears) assertTrue(c.id, lib.any { BodyPart.EAR in it.parts && it.verb.name.startsWith("EAR") })
            if (b.trunk) assertTrue(c.id, lib.any { it.verb == Verb.TRUNK_BOOP })
        }
    }

    @Test fun rocketGirlReactions() {
        assertTrue(run("rocketgirl", 3L, 40, part = BodyPart.HEAD).contains(Verb.GIGGLE))
        assertTrue(run("rocketgirl", 3L, 40, part = BodyPart.PROP).contains(Verb.ROCKET_SPUTTER))
        assertTrue(run("rocketgirl", 3L, 40, part = BodyPart.BELLY, kind = TouchKind.FLICK).contains(Verb.LOOP))
        assertTrue(run("rocketgirl", 3L, 40, part = BodyPart.HEAD, kind = TouchKind.HOLD).contains(Verb.SPARKLER))
        val many = run("rocketgirl", 11L, 4000)
        val boosts = many.count { it == Verb.BOOST }
        assertTrue("boost is rare: $boosts / ${many.size}", boosts in 1..(many.size / 20))
    }

    @Test fun reactionLayerFadesAndStaysAdditive() {
        val ch = CharacterRegistry.byId("monkey")!!
        val prof = CharacterProfiles.of(ch)
        val picker = ReactionPicker(1L)
        val inp = TouchTracker().build(BodyPart.HEAD, TouchZone.HEAD, 1, TouchKind.TAP, 0f, 1f, 0f, 0.5f, 0.1f, 1f)
        val r = picker.pick(prof.reactionTable.filter { it.verb != Verb.LEGACY }, inp, MoodSnapshot(), Personality.base("monkey"), 1f, 1f, 1f)!!
        val layer = ReactionLayer(); layer.play(r)
        var t = 0f
        while (layer.busy && t < 5f) {
            val p = Pose(); layer.step(1f / 60f); layer.apply(p, 1f, AnimEngine())
            assertTrue(kotlin.math.abs(p.headRot) <= 40.01f)
            t += 1f / 60f
        }
        assertTrue("clip ends: $t", t < 5f)
        val p0 = Pose(); val p1 = Pose(); layer.apply(p1, 1f, null)
        assertEquals(p0.headRot, p1.headRot, 1e-4f)   // nothing left over once finished
    }

    @Test fun livingLayerIsDeterministicAndAnticipates() {
        fun trace(seed: Long): List<Float> {
            val l = LivingLayer(seed); l.tuning = AnimTuning.MONKEY
            val e = AnimEngine(); e.setTuning(AnimTuning.MONKEY)
            val out = ArrayList<Float>()
            for (i in 0 until 900) {
                val p = Pose()
                if (i == 300) l.anticipate(Antic.CROUCH, 1f)
                l.step(p, 1f / 60f, e, true, false, 0f, 1f, 0f)
                out += p.headRot; out += p.bodyY; out += p.squashY
            }
            return out
        }
        assertEquals(trace(9L), trace(9L))
        val tr = trace(9L)
        // a crouch shows up right after anticipate(): body dips / squashes within ~150 ms
        val k = (301..309).map { tr[it * 3 + 2] }
        assertTrue("squash during crouch: $k", k.any { it < 0.995f })
        // and the frame-loop RNG is untouched
        val before = kotlin.random.Random(5).nextInt()
        kotlin.random.Random(5).let { trace(9L); assertEquals(before, it.nextInt()) }
    }
}
