package com.combo.monkeycompanion.v1040

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.brain.LongPressMenu
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.character.Locomotion
import com.combo.monkeycompanion.engine.game.SlingGame
import com.combo.monkeycompanion.overlay.SlingGameHub
import com.combo.monkeycompanion.overlay.SlingGameView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v10.40 Slingshot game on the phone: the game window, drawing every character, the menu entry, today's best. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SlingGameViewTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    @After fun after() { SlingGameView.close(ctx) }

    private fun view(stone: String, pool: List<String>): SlingGameView {
        val fliers = CharacterRegistry.all.filter { it.can(Locomotion.FLY) || it.can(Locomotion.FLOAT) }.map { it.id }.toSet()
        val v = SlingGameView(ctx, SlingGame(2.17f, stone, pool, fliers, 4L))
        v.layout(0, 0, 1080, 2340)
        return v
    }

    @Test fun everyCharacterDrawsAsStoneAndAsTargetThroughAWholeShot() {
        val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888); val c = Canvas(bmp)
        val ids = CharacterRegistry.all.map { it.id }
        for ((i, id) in ids.withIndex()) {
            val other = ids[(i + 1) % ids.size]
            val v = view(id, listOf(id, other))
            repeat(90) { v.advance(1f / 60f) }; v.draw(c)                       // aiming, target walking in
            assertEquals(other, v.game.target?.id)
            v.game.pull(0.05f, 0.25f); v.draw(c)                                 // pulled: bands + aim dots
            assertTrue(v.debugShoot(0.05f, 0.25f))
            repeat(8) { v.advance(1f / 60f) }; v.draw(c)                         // flying
            repeat(60) { v.advance(1f / 60f) }; v.draw(c)                        // splat
            assertTrue("$id has shot and splatted or returned", v.game.shots == 1 && v.game.phase != SlingGame.Phase.FLY)
            repeat(120) { v.advance(1f / 60f) }; v.draw(c)                       // back home
            assertEquals(SlingGame.Phase.AIM, v.game.phase)
        }
    }

    @Test fun theEndCardDrawsAndTodaysBestIsKept() {
        val v = SlingGameView(ctx, SlingGame(2.17f, "devil", listOf("devil", "angel"), emptySet(), 2L, roundSec = 1f))
        v.layout(0, 0, 1080, 2340)
        repeat(120) { v.advance(1f / 60f) }
        assertTrue(v.game.over)
        v.draw(Canvas(Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)))
        assertEquals(5 to true, SlingGameHub.record(ctx, 5, day = 100L))
        assertEquals(5 to false, SlingGameHub.record(ctx, 3, day = 100L))
        assertEquals(5, SlingGameHub.bestToday(ctx, day = 100L))
        assertEquals("a new day starts from zero", 2 to true, SlingGameHub.record(ctx, 2, day = 101L))
    }

    @Test fun theGameIsInTheGamesMenuAndUsesTheCurrentCharacterAsTheStone() {
        assertEquals("sling_game", LongPressMenu.groupItems("g_games").first())
        assertNotNull(LongPressMenu.groupedEntry("sling_game"))
        CharacterRegistry.select("armour")
        val g = SlingGameHub.newGame(ctx)!!
        assertEquals("armour", g.bulletId)
        repeat(5) { g.step(0.016f) }
        assertFalse("the target is another character", g.target!!.id == "armour")
        assertTrue(CharacterRegistry.all.any { it.id == g.target!!.id })
        CharacterRegistry.select("monkey")
    }
}
