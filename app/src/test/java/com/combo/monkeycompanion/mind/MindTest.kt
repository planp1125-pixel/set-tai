package com.combo.monkeycompanion.mind

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

private val Z: ZoneId = ZoneId.of("Asia/Kolkata")
private fun at(h: Int, m: Int = 0, day: Int = 1): Long = LocalDateTime.of(2026, 10, day, h, m).atZone(Z).toInstant().toEpochMilli()
private fun mind(id: String = "monkey", seed: Long = 7L) = Mind(id, Personality.seed(id, seed, listOf("banana", "zoom")), Diary(Z))
private fun scene(pkg: String, h: Int, m: Int = 0, kb: Boolean = false, fs: Boolean = false, dnd: Boolean = false) =
    Scene(pkg, AppCat.of(pkg), keyboard = kb, fullscreen = fs, dnd = dnd, minute = h * 60 + m)

class DrivesTest {
    @Test fun energyFollowsTheDay() {
        assertTrue(Drives.energyTarget(3 * 60, false) < 0.2f)
        assertTrue(Drives.energyTarget(11 * 60, false) > 0.8f)
        assertTrue(Drives.energyTarget(14 * 60, false) < Drives.energyTarget(11 * 60, false))
        assertTrue(Drives.energyTarget(11 * 60, true) <= 0.4f)
        val d = Drives(energy = 0.9f)
        repeat(60) { d.tick(60f, 2 * 60, visible = true, napping = false, curiosityBase = 0.5f, batteryLow = false) }
        assertTrue("night drains energy: ${d.energy}", d.energy < 0.7f)
    }
    @Test fun boredomGrowsOnlyWhileVisibleAndAnnoyanceFades() {
        val a = Drives(boredom = 0.2f, annoyance = 0.8f); val b = Drives(boredom = 0.6f)
        a.tick(1200f, 11 * 60, true, false, 0.5f, false); b.tick(1200f, 11 * 60, false, false, 0.5f, false)
        assertTrue(a.boredom > 0.4f); assertTrue(b.boredom < 0.6f)
        assertTrue("annoyance fades: ${a.annoyance}", a.annoyance < 0.15f)
        val e = Drives.decode(a.encode()); assertEquals(a.boredom, e.boredom, 0.002f)
    }
}

class UtilityTest {
    private fun run(seed: Long): List<Act> {
        val m = mind(); val r = Random(seed); val out = ArrayList<Act>()
        var t = at(10)
        for (pkg in listOf("com.google.android.youtube", "com.whatsapp", "com.application.zomato", "com.android.chrome")) {
            t += 120_000L; val d = m.perceive(scene(pkg, 10, (t / 60_000 % 60).toInt()), t, r); m.commit(d, t); out += d.act
            repeat(5) { t += 40_000L; val h = m.heartbeat(t, 10 * 60, r); m.commit(h, t); out += h.act }
        }
        return out
    }
    @Test fun deterministicWithSeed() {
        assertEquals(run(42), run(42))
        assertNotEquals((1L..6L).map { run(it) }.toSet().size, 1)
    }
    @Test fun mostlyIgnores() {
        val m = mind(); val r = Random(3); var t = at(11)
        m.perceive(scene("com.android.launcher3", 11), t, r)
        var ignored = 0; val n = 300
        repeat(n) { t += 40_000L; val d = m.heartbeat(t, 11 * 60 + (it * 40 / 60) % 60, r); if (d.act == Act.IGNORE) ignored++; m.commit(d, t); if (d.act == Act.NAP) m.onEvent(MindEvent.WOKE, t, 660, r) ; if (d.act == Act.LEAVE) Unit }
        assertTrue("ignored $ignored/$n", ignored > n * 0.6)
        assertTrue("but not always: $ignored/$n", ignored < n)
    }
    @Test fun affinityShapesReactions() {
        fun rate(pkg: String): Int { var k = 0
            for (s in 0 until 300) { val m = mind(seed = 5); val d = m.perceive(scene(pkg, 11), at(11), Random(s.toLong())); if (d.act in setOf(Act.COMMENT, Act.EXPLORE)) k++ }
            return k }
        val yt = rate("com.google.android.youtube"); val st = rate("com.android.settings"); assertTrue("yt $yt vs settings $st", yt > st)
        // Settai hides respectfully from banking
        val d = mind().perceive(scene("net.one97.paytm", 11), at(11), Random(1))
        assertEquals(d.reason, Act.HIDE, d.act)
    }
    @Test fun noveltyPenaltyAvoidsRepeats() {
        val m = mind(); val r = Random(9); var t = at(16)
        m.perceive(scene("com.android.launcher3", 16), t, r)
        m.drives.boredom = 1f
        val acts = ArrayList<Act>()
        repeat(200) { t += 40_000L; val d = m.heartbeat(t, 16 * 60, r); m.commit(d, t); if (d.act != Act.IGNORE) acts += d.act; if (d.act == Act.NAP) m.onEvent(MindEvent.WOKE, t, 960, r) }
        val longest = acts.fold(0 to (null as Act? to 0)) { (best, cur), a -> val n = if (cur.first == a) cur.second + 1 else 1; maxOf(best, n) to (a to n) }.first
        assertTrue("variety: $acts", acts.toSet().size >= 3)
        assertTrue("no long streaks ($longest)", longest <= 4)
    }
}

class HabituationTest {
    @Test fun gettingUsedToThingsAndForgetting() {
        val h = Habituation(); val t = at(9)
        assertEquals(1f, h.novelty("yt", t), 0.001f)
        repeat(5) { h.expose("yt", t) }
        assertTrue(h.novelty("yt", t) < 0.1f)
        assertTrue("recovers after a week: ${h.novelty("yt", t + 7 * 86_400_000L)}", h.novelty("yt", t + 7 * 86_400_000L) > 0.7f)
        val h2 = Habituation(); h2.decode(h.encode()); assertEquals(h.count("yt", t), h2.count("yt", t), 0.01f)
    }
    @Test fun secondVisitIsLessExciting() {
        val m = mind(); val t = at(10)
        val n1 = m.habituation.novelty("com.google.android.apps.maps", t)
        m.perceive(scene("com.google.android.apps.maps", 10), t, Random(1))
        assertTrue(m.habituation.novelty("com.google.android.apps.maps", t + 1000) < n1)
    }
}

class RespectTest {
    @Test fun neverInterruptsTyping() {
        for (s in 0 until 500) {
            val m = mind(seed = s.toLong()); val r = Random(s.toLong())
            m.drives.boredom = 1f; m.drives.social = 1f; m.drives.curiosity = 1f
            val d1 = m.perceive(scene("com.whatsapp", 12, kb = true), at(12), r)
            val d2 = m.heartbeat(at(12) + 60_000, 12 * 60 + 1, r)
            val d3 = m.permit(Proposal.GAG, at(12) + 70_000, r)
            assertEquals(Act.IGNORE, d1.act); assertEquals(Act.IGNORE, d2.act); assertEquals(Act.IGNORE, d3.act)
        }
    }
    @Test fun meetingsCallsAndDndOnlyAllowQuietChoices() {
        val loud = Act.entries.filter { it.loud }.toSet()
        for (s in 0 until 300) {
            val m = mind(seed = s.toLong()); val r = Random(s.toLong()); m.drives.boredom = 1f; m.drives.social = 1f
            val d = m.perceive(scene("us.zoom.videomeetings", 15), at(15), r)
            assertFalse(d.act in loud)
            m.updateScene(m.scene.copy(dnd = true))
            assertFalse(m.permit(Proposal.POWER, at(15) + 1000, r).act in loud)
            assertFalse(m.heartbeat(at(15) + 60_000, 900, r).act in loud)
        }
    }
    @Test fun fullscreenVideoIsMostlyLeftAlone() {
        var loud = 0
        for (s in 0 until 300) { val m = mind(seed = s.toLong()); val d = m.permit(Proposal.QUIP, at(20), Random(s.toLong()).also { m.perceive(scene("com.netflix.mediaclient", 20, fs = true), at(20) - 1000, it) }); if (d.act.loud) loud++ }
        assertTrue("loud under fullscreen: $loud/300", loud < 30)
    }
    @Test fun swipeAwayTeachesFewerInterruptions() {
        fun rate(swipes: Int): Int { var k = 0
            for (s in 0 until 400) {
                val m = mind(seed = 11); val r = Random(s.toLong())
                m.perceive(scene("com.instagram.android", 15), at(15), r)
                repeat(swipes) { m.onEvent(MindEvent.SWIPE_AWAY, at(15) + it * 1000L, 900, r) }
                if (m.permit(Proposal.QUIP, at(15) + 60_000, r).act == Act.COMMENT) k++
            }
            return k }
        val before = rate(0); val after = rate(3)
        assertTrue("swipes reduce comments: $before -> $after", after < before * 0.6)
        // and it's per app & time: another time of day is unaffected
        val m = mind(seed = 11); val r = Random(1)
        m.perceive(scene("com.instagram.android", 15), at(15), r)
        repeat(3) { m.onEvent(MindEvent.SWIPE_AWAY, at(15), 900, r) }
        val f15 = m.focus(at(15) + 1000)
        m.perceive(scene("com.instagram.android", 21), at(21), r)
        assertTrue(m.focus(at(21) + 1000) < f15)
    }
    @Test fun leavesDuringLongFocusAndComesBack() {
        val m = mind(seed = 2); val r = Random(4); var t = at(10)
        m.perceive(scene("com.google.android.gm", 10), t, r)
        var left = -1L; var back = -1L
        for (i in 0 until 240) {
            t += 40_000L
            val d = m.heartbeat(t, 10 * 60 + i * 40 / 60, r); m.commit(d, t)
            if (d.act == Act.LEAVE && left < 0) left = t
            if (d.act == Act.COME_BACK && left > 0) { back = t; break }
        }
        assertTrue("left during long reading " + m.heartbeat(t, 700, r).let { it.reason + " " + it.scores }, left > 0)
        assertTrue("came back", back > left)
    }
}

class DiaryTest {
    @Test fun mergesRepeatsDecaysAndForgets() {
        val d = Diary(Z); val t = at(10)
        repeat(4) { d.add(Diary.Kind.POKED, "WhatsApp", t + it * 60_000L) }
        assertEquals(1, d.all.size); assertEquals(4, d.all[0].count)
        d.add(Diary.Kind.FESTIVAL, "Deepavali", t)
        val e = d.all.first { it.kind == Diary.Kind.POKED }; val f = d.all.first { it.kind == Diary.Kind.FESTIVAL }
        assertTrue(d.effective(e, t + 10 * 86_400_000L) < d.effective(f, t + 10 * 86_400_000L))
        d.forget(t + 20 * 86_400_000L)
        assertTrue("pokes forgotten", d.all.none { it.kind == Diary.Kind.POKED })
        assertTrue("festival remembered", d.all.any { it.kind == Diary.Kind.FESTIVAL })
    }
    @Test fun capAndRollup() {
        val d = Diary(Z, cap = 50)
        for (day in 1..5) for (i in 0 until 30) d.add(Diary.Kind.EXPLORED, "App$i", at(9, i, day))
        assertTrue(d.all.size <= 50)
        d.rollup(at(12, 0, 6))
        assertTrue(d.all.any { it.kind == Diary.Kind.ROLLUP })
    }
    @Test fun recallAndRoundTrip() {
        val d = Diary(Z); val t = at(18)
        assertNull(d.recall(t, Random(1)))
        d.add(Diary.Kind.SHAKEN, "YouTube", at(18, 0, 0 + 1) - 86_400_000L)
        d.add(Diary.Kind.CHAT_LINE, "my boss thinks Excel is a vegetable", t - 3_600_000L)
        val e = d.recall(t, Random(2)); assertNotNull(e)
        val line = d.recallLine(e!!, "monkey", t); assertTrue(line, line.isNotBlank())
        d.markRecalled(e, t); val again = d.recall(t + 60_000, Random(2), kinds = setOf(e.kind)); assertNull(again)
        val shaken = d.all.first { it.kind == Diary.Kind.SHAKEN }
        assertTrue(d.recallLine(shaken, "monkey", t).contains("yesterday"))
        val d2 = Diary(Z); d2.decode(d.encode()); assertEquals(d.all, d2.all)
        assertTrue(d.page("superboy", t).first().second.startsWith("Hero log"))
        assertTrue(d.promptSnippet("monkey", t).contains("Excel"))
    }
}

class PersonalityTest {
    @Test fun seededOncePersistedAndDistinct() {
        val a = Personality.seed("monkey", 123, listOf("p1", "p2"))
        val b = Personality.decode(a.encode())!!
        assertEquals(a.traits, b.traits); assertEquals(a.fav, b.fav); assertEquals(a.encode(), b.encode())
        assertEquals(a.encode(), Personality.seed("monkey", 123, listOf("p1", "p2")).encode())
        assertTrue((1L..20L).map { Personality.seed("monkey", it, listOf("p1", "p2")).fav }.toSet().size > 3)
        // built-in tastes
        assertTrue(Personality.affinity("monkey", AppCat.FOOD) > 0.5f); assertTrue(Personality.affinity("monkey", AppCat.CALCULATOR) < 0f)
        assertTrue(Personality.affinity("superboy", AppCat.MAPS) > 0.5f); assertTrue(Personality.affinity("mask", AppCat.CAMERA) > 0.5f)
        assertTrue(Personality.affinity("armour", AppCat.GAME) < 0f)
    }
    @Test fun driftAndIntensity() {
        val p = Personality.seed("superboy", 1, emptyList())
        assertTrue(p.at(8, 0.5f).chattiness > p.at(1, 0.5f).chattiness)
        val calm = p.at(1, 0f); val wild = p.at(1, 1f)
        assertTrue(Math.abs(wild.boldness - 0.5f) > Math.abs(calm.boldness - 0.5f))
    }
    @Test fun mindStateRoundTrip() {
        val m = mind(); val r = Random(1); val t = at(13)
        m.perceive(scene("com.google.android.youtube", 13), t, r)
        m.onEvent(MindEvent.SWIPE_AWAY, t, 780, r); m.commit(m.heartbeat(t + 40_000, 780, r), t + 40_000)
        val m2 = mind(); m2.decode(m.encode())
        assertEquals(m.encode(), m2.encode())
    }
}

class OfflinePathTest {
    @Test fun everythingWorksWithoutABrain() {
        for (id in listOf("monkey", "superboy", "armour", "mask")) {
            val m = mind(id); val r = Random(3); val t = at(11)
            m.perceive(scene("com.google.android.youtube", 11), t, r)
            assertTrue(m.thinking(t).isNotBlank())
            val p = m.personality
            for (cat in AppCat.entries) assertTrue(MindLines.comment(id, Scene("x.$cat", cat), p, r).isNotBlank())
            assertTrue(MindLines.leave(id).isNotBlank() && MindLines.back(id).isNotBlank())
            assertNotNull(MindLines.habit(id, Habit.MORNING_STRETCH, p))
        }
        // a comment decision always carries a template line (the LLM is optional)
        var seen = 0
        for (s in 0 until 200) { val m = mind(seed = s.toLong()); val d = m.permit(Proposal.QUIP, at(11), Random(s.toLong())); if (d.act == Act.COMMENT) { seen++; assertNotNull(d.line) } }
        assertTrue(seen > 0)
    }
    @Test fun morningStretchOnUnlockAndHabitsEmerge() {
        val m = mind(); val r = Random(5)
        m.onEvent(MindEvent.SCREEN_OFF, at(23, 0, 1), 23 * 60, r)
        val d = m.onEvent(MindEvent.UNLOCK, at(7, 30, 2), 450, r)
        assertEquals(Habit.MORNING_STRETCH, d?.habit); m.commit(d!!, at(7, 30, 2))
        assertNull("once a day", m.onEvent(MindEvent.UNLOCK, at(8, 0, 2), 480, r)?.habit)
        assertTrue(m.diary.all.any { it.kind == Diary.Kind.USER_AWAY })
        // napping after lunch two days in a row makes it a ritual
        assertNull(m.habitDue(at(13, 30, 4), 810))
        for (day in 2..3) m.commit(Decision(Act.NAP, emptyMap(), "", Scene(minute = 810)), at(13, 30, day).also { m.heartbeat(it, 810, r) })
        assertEquals(Habit.AFTER_LUNCH_NAP, m.habitDue(at(13, 30, 4), 810))
    }
}
