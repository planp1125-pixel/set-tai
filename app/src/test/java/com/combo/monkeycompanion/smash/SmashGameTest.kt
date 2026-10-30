package com.combo.monkeycompanion.smash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** v10.5: Screen Smash rules. */
class SmashGameTest {
    private fun game() = SmashGame(1080f, 2340f, 3f, rnd = Random(1))
    private fun run(g: SmashGame, secs: Float) { repeat((secs * 60).toInt()) { g.step(1f / 60f) } }

    @Test fun gridCoversTheScreen() {
        val g = game()
        assertEquals(4, g.cols); assertTrue(g.rows in 7..10)
        assertEquals(0, g.tileAt(1f, 1f)); assertEquals(g.tiles - 1, g.tileAt(1079f, 2339f)); assertEquals(-1, g.tileAt(-1f, 5f))
    }

    @Test fun twoHitsCrackThenShatterIntoShards() {
        val g = game(); val x = 400f; val y = 900f; val t = g.tileAt(x, y)
        assertTrue(g.tap(x, y, 540f, 2200f, Attack.STONE))
        assertEquals("in flight", 2, g.hp[t]); run(g, 0.5f)
        assertEquals("cracked", 1, g.hp[t]); assertTrue(g.score > 0)
        g.tap(x, y, 540f, 2200f, Attack.STONE); run(g, 0.5f)
        assertEquals("shattered", 0, g.hp[t]); assertEquals(8, g.shardCount); assertEquals(1, g.smashed)
        assertFalse("nothing left to hit there", g.tap(x, y, 540f, 2200f, Attack.STONE))
        run(g, 2.5f)
        // v10.6: shards stay, resting in a pile on the floor.
        assertEquals("shards stay", 8, g.shardCount)
        for (i in 0 until g.shardCount) { assertTrue("resting", g.shardRest[i]); assertTrue("on the floor", g.shardY[i] > g.height * 0.9f) }
    }

    @Test fun breakFreezesTheGameBriefly() {
        val g = game(); val x = 300f; val y = 300f
        g.tap(x, y, 0f, 0f, Attack.LASER); assertEquals(0f, g.hitStop, 0f)
        g.tap(x, y, 0f, 0f, Attack.LASER)
        assertEquals(SmashGame.HIT_STOP_BREAK, g.hitStop, 1e-4f)
        val t0 = g.timeLeft; g.step(0.03f)
        assertEquals("timer frozen", t0, g.timeLeft, 0f)
        g.step(0.05f); assertEquals(0f, g.hitStop, 0f); assertTrue(g.timeLeft < t0)
    }

    @Test fun restingPileIsCappedAndRepairClearsIt() {
        val g = game()
        repeat(2) { for (i in 0 until g.tiles) { g.tap(g.tileLeft(i) + 5f, g.tileTop(i) + 5f, 0f, 0f, Attack.LASER); g.step(0.016f) } }
        run(g, 4f)
        val visibleResting = (0 until g.shardCount).count { g.shardRest[it] && g.shardAlpha[it] >= 1f }
        assertTrue("cap ${'$'}visibleResting", visibleResting <= g.restCap)
        assertTrue(g.shardCount > 0)
        g.repair(); run(g, 1.2f)
        assertEquals(SmashGame.State.DONE, g.state); assertEquals(0, g.shardCount)
    }

    @Test fun laserHitsInstantly() {
        val g = game(); val t = g.tileAt(300f, 300f)
        g.tap(300f, 300f, 540f, 2000f, Attack.LASER)
        assertEquals(1, g.hp[t])
    }

    @Test fun comboChargesAMegaSmashThatBreaksA3x3Block() {
        val g = game()
        // Eight quick cracks charge the mega smash.
        for (i in 0 until SmashGame.MEGA_EVERY) { g.tap(g.tileLeft(i) + 10f, g.tileTop(i) + 10f, 0f, 0f, Attack.LASER); g.step(0.1f) }
        assertTrue(g.megaReady)
        val center = 5 * g.cols + 2
        g.tap(g.tileLeft(center) + 20f, g.tileTop(center) + 20f, 0f, 0f, Attack.LASER)
        for (r in 4..6) for (c in 1..3) assertEquals("3x3 smashed", 0, g.hp[r * g.cols + c])
        assertEquals(9, g.smashed)
    }

    @Test fun comboResetsWhenYouPause() {
        val g = game()
        g.tap(10f, 10f, 0f, 0f, Attack.LASER); g.tap(300f, 10f, 0f, 0f, Attack.LASER)
        assertEquals(2, g.combo); run(g, 1.2f); assertEquals(0, g.combo)
    }

    @Test fun timerEndsTheGameThenItRepairsAndFinishes() {
        val g = game()
        run(g, 30.5f)
        assertEquals(SmashGame.State.OVER, g.state)
        assertFalse(g.tap(10f, 10f, 0f, 0f, Attack.LASER))
        g.repair(); run(g, 1f)
        assertEquals(SmashGame.State.DONE, g.state)
    }

    @Test fun smashingEverythingEndsEarlyWithATimeBonus() {
        val g = game()
        repeat(2) { for (i in 0 until g.tiles) { g.tap(g.tileLeft(i) + 5f, g.tileTop(i) + 5f, 0f, 0f, Attack.LASER); g.step(0.016f) } }
        assertEquals(g.tiles, g.smashed)
        assertEquals(SmashGame.State.OVER, g.state)
        assertTrue("time bonus", g.score > g.tiles * 35)
    }

    @Test fun againResetsEverything() {
        val g = game()
        g.tap(10f, 10f, 0f, 0f, Attack.LASER); run(g, 31f)
        g.reset()
        assertEquals(SmashGame.State.PLAYING, g.state); assertEquals(0, g.score); assertTrue(g.hp.all { it == 2 })
    }

    @Test fun eachCharacterHasItsAttack() {
        assertEquals(Attack.LASER, Attack.of("superboy")); assertEquals(Attack.BALL, Attack.of("mask"))
        assertEquals(Attack.WATER, Attack.of("gajju")); assertEquals(Attack.STONE, Attack.of("monkey"))
        assertEquals(Attack.STONE, Attack.of("custom_42"))
    }
}
