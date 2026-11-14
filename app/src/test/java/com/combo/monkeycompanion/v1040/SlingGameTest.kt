package com.combo.monkeycompanion.v1040

import com.combo.monkeycompanion.engine.game.SlingGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** v10.40 Slingshot game rules (pure: no Android). */
class SlingGameTest {
    private val pool = listOf("devil", "angel", "monkey", "superboy", "armour", "billa")
    private fun game(seed: Long = 7L, sec: Float = 30f) = SlingGame(2.17f, "devil", pool, setOf("angel", "superboy"), seed, sec)
    private fun SlingGame.run(sec: Float) { var left = sec; while (left > 0f) { step(1f / 60f); left -= 1f / 60f } }

    /** Aim where the target will be when the stone gets there, full power. */
    private fun SlingGame.shootAtTarget(): Boolean {
        val tg = target ?: return false
        val sp = SlingGame.launchSpeed(1f)
        var tf = hypot(tg.x - bx, tg.y - by) / sp
        repeat(3) { tf = hypot(tg.x + tg.vx * tf - bx, tg.y - by) / sp }
        val ax = tg.x + tg.vx * tf - bx; val ay = tg.y - by - 0.5f * SlingGame.GRAVITY * tf * tf
        val len = hypot(ax, ay)
        pull(-ax / len * SlingGame.MAX_PULL, -ay / len * SlingGame.MAX_PULL)
        return release()
    }

    @Test fun aTargetComesInAndIsNeverTheStone() {
        val g = game()
        g.step(0.016f)
        assertNotNull(g.target); assertNotEquals("devil", g.target!!.id)
        assertTrue(SlingGame.Event.TARGET_IN in g.drain())
        assertEquals(SlingGame.Phase.AIM, g.phase)
    }

    @Test fun aSmallPullDoesNotFire() {
        val g = game(); g.step(0.016f)
        g.pull(0.01f, 0.02f)
        assertFalse(g.release())
        assertEquals(SlingGame.Phase.AIM, g.phase); assertEquals(0, g.shots)
        assertEquals(g.homeX, g.bx, 1e-4f); assertEquals(g.homeY, g.by, 1e-4f)
    }

    @Test fun thePullIsLimitedAndTheStoneFliesTheOtherWay() {
        val g = game(); g.step(0.016f)
        g.pull(0f, 5f)   // far too much, straight down
        assertEquals(SlingGame.MAX_PULL, hypot(g.pullX, g.pullY), 1e-4f)
        assertEquals(1f, g.tension, 1e-4f)
        assertNotNull(g.aimPoint(0.1f))
        assertTrue(g.release())
        assertEquals(SlingGame.Phase.FLY, g.phase)
        assertTrue("flies up", g.bvy < 0f)
        assertTrue(SlingGame.Event.SNAP in g.drain())
    }

    @Test fun aMissSplatsAloneAndResetsTheStreak() {
        val g = game(); g.run(2f)
        g.pull(-0.3f, 0f)   // pulled left = fires right along the floor, far below the target
        assertTrue(g.release())
        g.run(1f)
        val ev = g.drain()
        assertTrue(ev.toString(), SlingGame.Event.SPLAT in ev)
        assertFalse(SlingGame.Event.DOUBLE_SPLAT in ev)
        assertEquals(0, g.score); assertEquals(0, g.streak)
        assertEquals(SlingGame.Wall.RIGHT, g.wall)
        g.run(1.5f)
        assertEquals("the stone is back home, ready again", SlingGame.Phase.AIM, g.phase)
        assertEquals(g.homeX, g.bx, 1e-4f)
        assertNotNull("the target is still there after a miss", g.target)
    }

    @Test fun aGoodShotCarriesTheTargetToTheWallAndScores() {
        var hit = false
        for (seed in 1L..6L) {
            val g = game(seed)
            repeat(10) {
                if (g.score > 0 || g.over) return@repeat
                g.run(1.6f)
                val tg = g.target
                if (g.phase == SlingGame.Phase.AIM && tg != null && !tg.leaving && tg.x in 0.2f..0.8f) { g.shootAtTarget(); g.run(2.4f) }
            }
            if (g.score > 0) {
                hit = true
                assertTrue(g.streak >= 1); assertTrue(g.bestStreak >= 1)
                break
            }
        }
        assertTrue("a led, full-power shot hits within a few tries", hit)
    }

    @Test fun afterAHitANewTargetComesAndEventsFire() {
        val g = game(3L)
        val seen = ArrayList<SlingGame.Event>()
        var firstId: String? = null
        repeat(14) {
            if (g.score > 0) return@repeat
            g.run(1.6f); seen += g.drain()
            val tg = g.target
            if (g.phase == SlingGame.Phase.AIM && tg != null && !tg.leaving && tg.x in 0.2f..0.8f) {
                firstId = tg.id; g.shootAtTarget(); g.run(0.9f); seen += g.drain()
                if (SlingGame.Event.HIT in seen) { assertTrue(g.target!!.carried || g.phase != SlingGame.Phase.FLY) }
                g.run(1.6f); seen += g.drain()
            }
        }
        if (g.score > 0) {
            assertTrue(SlingGame.Event.HIT in seen); assertTrue(SlingGame.Event.DOUBLE_SPLAT in seen)
            g.run(1f)
            assertNotNull(g.target); assertNotEquals("a different character comes next", firstId, g.target!!.id)
        }
    }

    @Test fun theRoundEndsWhenTimeRunsOut() {
        val g = game(sec = 3f)
        g.run(2.2f)
        g.pull(0f, -0.3f); g.release()   // fired at the floor; still flying when the time ends: the shot finishes first
        g.run(3f)
        assertTrue(g.over); assertEquals(0f, g.timeLeft, 1e-4f)
        assertTrue(SlingGame.Event.OVER in g.drain())
        g.pull(0f, 0.3f); assertFalse("no shooting after the end", g.release())
    }

    // ---------------- v10.41 ----------------
    @Test fun aRoundIsSixtySecondsAndAHitAddsThreeAndLevelsGoUp() {
        assertEquals(60f, SlingGame.ROUND_SEC, 0f)
        var done = false
        for (seed in 1L..8L) {
            val g = game(seed, sec = 60f)
            val seen = ArrayList<SlingGame.Event>()
            repeat(40) {
                if (g.score >= SlingGame.HITS_PER_LEVEL || g.over) return@repeat
                g.run(1.3f); seen += g.drain()
                val tg = g.target
                if (g.phase == SlingGame.Phase.AIM && tg != null && !tg.leaving && tg.trickPhase == 0 && tg.x in 0.2f..0.8f) {
                    val before = g.timeLeft; val s0 = g.score
                    g.shootAtTarget(); g.run(0.2f)
                    val flightStart = g.timeLeft
                    g.run(2.2f); seen += g.drain()
                    if (g.score > s0) assertEquals("a hit gives ${SlingGame.BONUS_SEC} s back", SlingGame.BONUS_SEC, g.bonus - (s0 * SlingGame.BONUS_SEC), 1e-3f)
                    assertTrue(before >= flightStart)
                }
            }
            if (g.score >= SlingGame.HITS_PER_LEVEL) {
                assertEquals(2, g.level); assertTrue(SlingGame.Event.LEVEL_UP in seen)
                assertEquals(g.score * SlingGame.BONUS_SEC, g.bonus, 1e-3f)
                done = true; break
            }
        }
        assertTrue("three hits reach level 2 within a round", done)
    }

    @Test fun anEscapeAlwaysWarnsFirstAndAVanishedTargetCannotBeHit() {
        val g = SlingGame(2.17f, "monkey", listOf("monkey", "devil"), emptySet(), 9L, 60f, mapOf("devil" to SlingGame.Trick.VANISH))
        var warned = false; var vanished = false; var trickEvents = 0
        repeat(60 * 12) {
            g.step(1f / 60f)
            val tg = g.target ?: return@repeat
            if (tg.warning) { warned = true; assertTrue("still solid while warning", tg.hittable) }
            if (tg.trickPhase == 2) assertTrue("the warning came first", warned)
            if (!tg.hittable) vanished = true
            trickEvents += g.drain().count { it == SlingGame.Event.TRICK }
        }
        assertTrue(warned); assertTrue(vanished); assertTrue(trickEvents >= 1)
        assertEquals(SlingGame.TELL_SEC, 0.35f, 0f)
    }

    @Test fun aCharacterWithNoTrickNeverEscapes() {
        val g = SlingGame(2.17f, "monkey", listOf("monkey", "gajju"), emptySet(), 9L, 60f, mapOf("gajju" to SlingGame.Trick.NONE))
        repeat(60 * 7) { g.step(1f / 60f); g.target?.let { assertEquals(0, it.trickPhase); assertEquals(0f, it.yOff, 0f) } }
    }

    @Test fun sameSeedSameGame() {
        val a = game(11L); val b = game(11L)
        a.run(6f); b.run(6f)
        assertEquals(a.target?.id, b.target?.id); assertEquals(a.target?.x ?: 0f, b.target?.x ?: 0f, 1e-5f)
    }

    @Test fun anIgnoredTargetLeavesAndAnotherComes() {
        val g = game(5L)
        g.step(0.016f); val first = g.target!!.id
        g.run(SlingGame.TARGET_LIFE + 4f)
        val ev = g.drain()
        assertTrue(SlingGame.Event.TARGET_OUT in ev)
        assertNotNull(g.target); assertNotEquals(first, g.target!!.id)
    }
}
