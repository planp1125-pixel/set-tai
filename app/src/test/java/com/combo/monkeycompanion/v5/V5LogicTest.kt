package com.combo.monkeycompanion.v5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class FestivalsTest {
    @Test fun knownDates() {
        assertEquals(Festivals.Kind.DEEPAVALI, Festivals.on(LocalDate.of(2026, 11, 8))?.kind)
        assertEquals(Festivals.Kind.PONGAL, Festivals.on(LocalDate.of(2026, 1, 15))?.kind)
        assertEquals(Festivals.Kind.PONGAL, Festivals.on(LocalDate.of(2027, 1, 15))?.kind)
        assertEquals(Festivals.Kind.VINAYAGAR, Festivals.on(LocalDate.of(2026, 9, 14))?.kind)
        assertEquals(Festivals.Kind.VINAYAGAR, Festivals.on(LocalDate.of(2027, 9, 4))?.kind)
        assertEquals(Festivals.Kind.AYUDHA_POOJA, Festivals.on(LocalDate.of(2027, 10, 8))?.kind)
        assertEquals(Festivals.Kind.DEEPAVALI, Festivals.on(LocalDate.of(2027, 10, 28))?.kind)
        assertEquals(Festivals.Kind.DIWALI, Festivals.on(LocalDate.of(2027, 10, 29))?.kind)
        assertEquals(Festivals.Kind.TAMIL_NEW_YEAR, Festivals.on(LocalDate.of(2027, 4, 14))?.kind)
        assertNull(Festivals.on(LocalDate.of(2026, 10, 22)))
    }
    @Test fun lunarOnesAreMarkedApproximate() {
        for (d in Festivals.days) assertEquals(d.kind.name, d.kind == Festivals.Kind.RAMZAN || d.kind == Festivals.Kind.BAKRID, d.approximate)
        assertTrue(Festivals.days.all { Festivals.covers(it.date.year) })
    }
    @Test fun nextAndLines() {
        assertEquals(LocalDate.of(2026, 11, 8), Festivals.next(LocalDate.of(2026, 10, 22))?.date)
        assertNull(Festivals.next(LocalDate.of(2028, 1, 2)))
        for (k in Festivals.Kind.entries) for (c in listOf("monkey", "superboy", "armour", "mask"))
            assertTrue(Festivals.lines(k, c).size >= 3)
    }
    @Test fun coffee() {
        assertTrue(CoffeeBreak.due(16 * 60 + 5, "2026-10-22", "2026-10-21", false))
        assertFalse(CoffeeBreak.due(16 * 60 + 5, "2026-10-22", "2026-10-22", false))
        assertFalse(CoffeeBreak.due(16 * 60 + 5, "2026-10-22", null, blocked = true))
        assertFalse(CoffeeBreak.due(15 * 60 + 59, "2026-10-22", null, false))
        assertEquals(60_000L, CoffeeBreak.msUntilNext(15 * 60 + 59))
        assertEquals(24 * 3600_000L - 60_000L, CoffeeBreak.msUntilNext(16 * 60 + 1))
    }
}

class FriendshipTest {
    @Test fun levels() {
        assertEquals(1, Friendship.level(0)); assertEquals(1, Friendship.level(59)); assertEquals(2, Friendship.level(60))
        assertEquals(3, Friendship.level(160)); assertTrue(Friendship.xpFor(10) > Friendship.xpFor(9))
        assertEquals(0.5f, Friendship.progress(110), 0.01f)
    }
    @Test fun streakAndCaps() {
        var s = Friendship.State()
        var r = Friendship.add(s, Friendship.Source.PET, "2026-10-22", "2026-10-21")
        assertEquals(15 + 2, r.gained); assertEquals(1, r.state.streak); s = r.state
        repeat(100) { s = Friendship.add(s, Friendship.Source.PET, "2026-10-22", "2026-10-21").state }
        assertEquals(15 + 60, s.xp)                                   // daily cap on pets
        r = Friendship.add(s, Friendship.Source.CHAT, "2026-10-23", "2026-10-22")
        assertEquals(2, r.state.streak); assertEquals(20 + 5, r.gained)
        r = Friendship.add(r.state, Friendship.Source.CHAT, "2026-10-26", "2026-10-25")
        assertEquals(1, r.state.streak)                               // streak broken
        assertEquals(r.state, Friendship.State.decode(r.state.encode()))
    }
    @Test fun levelUpAndUnlocks() {
        var s = Friendship.State(xp = 55, day = "2026-10-22")
        val r = Friendship.add(s, Friendship.Source.GAME, "2026-10-22", "2026-10-21")
        assertEquals(2, r.levelUp)
        assertTrue(Friendship.has(r.state.xp, "lines_close")); assertFalse(Friendship.has(r.state.xp, "wear_crown"))
        assertTrue(Friendship.levelUpLine(5, "monkey").contains("crown"))
        assertTrue(Friendship.unlocks.map { it.level }.zipWithNext().all { (a, b) -> a <= b })
    }
}

class GamesTest {
    @Test fun bananaCatch() {
        val g = BananaCatch(Random(3))
        var t = 0f
        while (!g.over) { val target = g.items.filter { !it.rotten }.maxByOrNull { it.y }?.x ?: 0.5f; g.update(1f / 30f, target); t += 1f / 30f }
        assertTrue(g.score > 10); assertTrue(g.caught >= g.score); assertTrue(t in 29.9f..30.2f)
        val lazy = BananaCatch(Random(3)); while (!lazy.over) lazy.update(1f / 30f, 0f)
        assertTrue(lazy.missed > 5); assertTrue(lazy.score < g.score)
        assertTrue(g.items.size <= BananaCatch.MAX_ITEMS)
    }
    @Test fun staring() {
        val s = StaringContest(Random(1), 4f, 6f)
        repeat(30) { s.update(0.1f) }
        assertEquals(StaringContest.Result.NONE, s.result); s.userBlinked()
        assertEquals(StaringContest.Result.YOU_LOSE, s.result)
        val w = StaringContest(Random(1), 4f, 6f); repeat(70) { w.update(0.1f) }
        assertEquals(StaringContest.Result.YOU_WIN, w.result); w.userBlinked()
        assertEquals(StaringContest.Result.YOU_WIN, w.result)      // too late to lose
    }
    @Test fun rps() {
        assertEquals(Rps.Outcome.WIN, Rps.play(Rps.Hand.ROCK, Rps.Hand.SCISSORS))
        assertEquals(Rps.Outcome.LOSE, Rps.play(Rps.Hand.ROCK, Rps.Hand.PAPER))
        assertEquals(Rps.Outcome.DRAW, Rps.play(Rps.Hand.PAPER, Rps.Hand.PAPER))
        for (a in Rps.Hand.entries) assertEquals(1, Rps.Hand.entries.count { Rps.beats(a, it) })
        assertEquals(Rps.Hand.SCISSORS, Rps.parse("scissors!")); assertNull(Rps.parse("hello"))
        val seen = (0 until 60).map { Rps.throwHand(Random(it)) }.toSet(); assertEquals(3, seen.size)
    }
    @Test fun scoresAndChat() {
        val s = Games.Scores().best("banana", 12).best("banana", 7).inc("rps_w").inc("rps_w")
        assertEquals(12, s["banana"]); assertEquals(2, s["rps_w"])
        assertEquals(s, Games.Scores.decode(s.encode()))
        assertTrue(GameChat.wantsGame("Let's play machan")); assertEquals(Games.Game.STARE, GameChat.which("staring contest"))
    }
}

class SleepNagTest {
    private val H = 3600_000L; private val M = 60_000L
    @Test fun timing() {
        val now = 100 * H
        assertEquals(0, SleepNag.level(now, 22 * 60, now - 20 * M, 0, 0, 0, false))            // too early
        assertEquals(0, SleepNag.level(now, 23 * 60 + 40, now - 5 * M, 0, 0, 0, false))       // screen on < 10 min
        assertEquals(1, SleepNag.level(now, 23 * 60 + 40, now - 11 * M, 0, 0, 0, false))
        assertEquals(0, SleepNag.level(now, 23 * 60 + 40, now - 11 * M, 0, 0, 0, dnd = true))
        assertEquals(0, SleepNag.level(now, 23 * 60 + 52, now - 30 * M, now - 5 * M, 1, 0, false))  // not yet the next step
        assertEquals(2, SleepNag.level(now, 23 * 60 + 52, now - 30 * M, now - 13 * M, 1, 0, false))
        assertEquals(4, SleepNag.level(now, 1 * 60, now - 90 * M, now - 13 * M, 4, 0, false))
        assertEquals(0, SleepNag.level(now, 0, now - 90 * M, now - 13 * M, 2, now + 5 * M, false))   // snoozed
        assertEquals(0, SleepNag.level(now, 5 * 60 + 10, now - 90 * M, 0, 0, 0, false))            // morning
        assertEquals(1, SleepNag.level(now, 23 * 60 + 45, now - 11 * M, now - 20 * H, 4, 0, false))  // new night starts over
    }
    @Test fun checksOnlyWhenNeeded() {
        assertEquals(30 * 60_000L, SleepNag.nextCheckMs(23 * 60, 0))
        assertEquals(10 * 60_000L, SleepNag.nextCheckMs(23 * 60 + 40, 0))
        assertEquals(60_000L, SleepNag.nextCheckMs(23 * 60 + 40, 20 * 60_000L))
        assertTrue(SleepNag.line(3, "armour").contains("night mode"))
    }
}

class MotionTest {
    private fun run(d: MotionDetector, samples: List<Triple<Float, Float, Float>>, t0: Long = 0, step: Long = 60): List<MotionDetector.Event> {
        var t = t0; val out = ArrayList<MotionDetector.Event>()
        for ((x, y, z) in samples) { d.feed(x, y, z, t)?.let { out.add(it) }; t += step }
        return out
    }
    @Test fun shakeTiltFace() {
        val d = MotionDetector()
        val still = List(20) { Triple(0f, 9.8f, 0.5f) }
        assertTrue(run(d, still).isEmpty())
        val shake = List(12) { i -> if (i % 2 == 0) Triple(22f, 9.8f, 0f) else Triple(-22f, 9.8f, 0f) }
        assertTrue(run(d, shake, 2000).contains(MotionDetector.Event.SHAKE))
        val d2 = MotionDetector(); run(d2, still)
        val hard = List(12) { i -> if (i % 2 == 0) Triple(45f, 9.8f, 0f) else Triple(-45f, 9.8f, 0f) }
        assertTrue(run(d2, hard, 2000).contains(MotionDetector.Event.HARD_SHAKE))
        val d3 = MotionDetector(); run(d3, still)
        assertEquals(MotionDetector.Event.TILT_LEFT, run(d3, List(60) { Triple(6.5f, 7.5f, 0.5f) }, 2000).firstOrNull())
        val d4 = MotionDetector(); run(d4, List(5) { Triple(0f, 0f, 9.8f) })
        val face = run(d4, List(80) { Triple(0f, 0f, -9.8f) }, 1000)
        assertEquals(listOf(MotionDetector.Event.FACE_DOWN), face)
        assertEquals(MotionDetector.Event.FACE_UP, run(d4, List(40) { Triple(0f, 0f, 9.8f) }, 7000).firstOrNull())
    }
}

class WeatherCricketMunchTest {
    @Test fun weather() {
        assertEquals(WeatherMood.Mood.RAIN, WeatherMood.mood(63, 27f))
        assertEquals(WeatherMood.Mood.HOT, WeatherMood.mood(0, 38f))
        assertEquals(WeatherMood.Mood.COLD, WeatherMood.mood(1, 12f))
        assertEquals(WeatherMood.Mood.STORM, WeatherMood.mood(95, 30f))
        assertEquals(63 to 27.5f, WeatherMood.parse("""{"current":{"time":"x","temperature_2m":27.5,"weather_code":63}}"""))
        assertFalse(WeatherMood.due(10_000L, 0L)); assertTrue(WeatherMood.due(3 * 3600_000L, 0L))
        assertTrue(WeatherMood.url(13.08271, 80.2707).contains("latitude=13.08"))
    }
    @Test fun cricket() {
        val xml = """<rss><channel><pubDate>Thu, 22 Oct 2026 10:00:00 +0000</pubDate>
            <item><title>Kowloon CC v HK 60/3 *</title></item>
            <item><title>India 245/6 * v Australia 240</title></item></channel></rss>"""
        val ms = Cricket.parse(xml); assertEquals(2, ms.size)
        val m = Cricket.pick(ms); assertNotNull(m); assertTrue(m!!.battingA)
        assertEquals(Cricket.Feeling.NERVOUS, Cricket.feeling(m))
        assertEquals(Cricket.Feeling.CELEBRATE, Cricket.feeling(Cricket.Match("India won by 5 wkts", "India won by 5 wkts", "", false, false)))
        assertNotNull(Cricket.pubDate(xml))
        assertTrue(Cricket.reply(null, true).contains("Couldn't")); assertTrue(Cricket.reply(m, false).contains("old"))
        assertTrue(Cricket.asks("Score enna machan?"))
    }
    @Test fun munch() {
        assertFalse(NotifMunch.should(0L, 0L, false, 0.1f))
        assertTrue(NotifMunch.should(NotifMunch.COOLDOWN_MS, 0L, false, 0.1f))
        assertFalse(NotifMunch.should(NotifMunch.COOLDOWN_MS, 0L, saver = true, rnd = 0.1f))
        assertEquals(NotifMunch.Phase.MUNCH, NotifMunch.phase(1.5f))
        assertTrue(NotifMunch.cardLeft(0.5f) == 1f && NotifMunch.cardLeft(3f) == 0f && NotifMunch.cardLeft(1.6f) < 1f)
        assertTrue(NotifMunch.burp("x".repeat(200), "monkey").length < 95)
    }
}

class PairProtocolTest {
    @Test fun roundTrip() {
        val msgs = listOf(PairProtocol.Msg.Hello("Ravi", "mask", "abc123"), PairProtocol.Msg.Accept("Anu", "monkey"),
            PairProtocol.Msg.Reject, PairProtocol.Msg.Bye, PairProtocol.Msg.Ev(PairProtocol.Event.WAVE),
            PairProtocol.Msg.Ev(PairProtocol.Event.JUMP_ACROSS, PairProtocol.Side.LEFT))
        for (m in msgs) assertEquals(m, PairProtocol.decode(PairProtocol.encode(m)))
    }
    @Test fun rejectsJunk() {
        assertNull(PairProtocol.decode("HELLO there")); assertNull(PairProtocol.decode("SETTAI1 EV rm -rf"))
        assertNull(PairProtocol.decode("SETTAI1 EV jump"))                                // jump needs a side
        assertNull(PairProtocol.decode("SETTAI1 HELLO a hacker x")); assertNull(PairProtocol.decode("SETTAI1 " + "x".repeat(200)))
        assertEquals("a_b__c", PairProtocol.clean("a b;'c").take(6))
    }
    @Test fun pairing() {
        val inviter = PairProtocol.Session("Anu", "monkey"); val joiner = PairProtocol.Session("Ravi", "mask")
        val code = inviter.invite(PairProtocol.newCode(Random(5)))
        assertEquals(4, code.length)
        assertFalse(inviter.checkHello(PairProtocol.Session("Eve", "monkey").join("0000")))
        val hello = joiner.join(code)
        assertFalse(PairProtocol.encode(hello).contains(code))          // the code never travels
        assertTrue(inviter.checkHello(hello)); assertEquals("mask", inviter.peerCharacter)
        assertFalse(inviter.allowEvent(1000))                             // not before Accept
        joiner.onAnswer(PairProtocol.decode(PairProtocol.encode(inviter.accept()))!!)
        assertEquals(PairProtocol.Session.State.PAIRED, joiner.state); assertEquals("monkey", joiner.peerCharacter)
        assertTrue(joiner.allowEvent(1000)); assertFalse(joiner.allowEvent(1200)); assertTrue(joiner.allowEvent(1500))
        assertEquals(PairProtocol.Side.RIGHT, PairProtocol.enterSide(PairProtocol.Side.LEFT))
        val r = PairProtocol.Session("A", "monkey"); r.join("1234"); r.onAnswer(PairProtocol.Msg.Reject)
        assertEquals(PairProtocol.Session.State.CLOSED, r.state)
    }
}

class ClipScriptTest {
    @Test fun timeline() {
        assertEquals(120, ClipScript.frames)
        val f0 = ClipScript.frame(0); val fl = ClipScript.frame(ClipScript.frames - 1)
        assertTrue(f0.walkX < 0f && f0.captionAlpha == 0f && !f0.showLogo)
        assertTrue(fl.walkX == 0f && fl.captionAlpha == 1f && fl.showLogo)
        assertEquals(ClipScript.Bit.SLIP, ClipScript.pick("banana slip please"))
        assertTrue(ClipScript.wants("Make a clip da"))
    }
}
