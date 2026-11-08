package com.combo.monkeycompanion.brain

import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.ai.AppSettings
import com.combo.monkeycompanion.brain.LongPressMenu as M
import com.combo.monkeycompanion.character.ArmourAnna
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.character.MaskMan
import com.combo.monkeycompanion.character.MonkeyCharacter
import com.combo.monkeycompanion.character.Superboy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v4.4 long-press menu: defaults, max-6 overflow into More, Quit always reachable, persistence, powers ring, cooldown. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LongPressMenuTest {
    private val monkeyPowers = MonkeyCharacter.powers.map { it.id }

    @Test fun defaultsOverflowIntoMore() {
        val r = M.layout(M.defaults(), emptyList(), monkeyPowers, context = true)
        assertEquals(listOf("chat", "talk", "missed", "screen", "powers", M.MORE), r.main)
        assertEquals(listOf("funny", "games", "smash", "clip", "context", "quit"), r.more)
        val noCtx = M.layout(M.defaults(), emptyList(), monkeyPowers, context = false)
        assertEquals(listOf("funny", "games", "smash", "clip", "quit"), noCtx.more)
        assertTrue(r.main.size <= M.MAX_MAIN)
    }

    @Test fun maxSixAndQuitAlwaysReachable() {
        // Exactly 6 on (incl. Quit): no More.
        var c = M.defaults()
        for (id in listOf("context", "funny", "games", "smash", "clip")) c = M.toggle(c, id, false)
        var r = M.layout(c, emptyList(), monkeyPowers, true)
        assertEquals(6, r.main.size); assertTrue("quit" in r.main); assertTrue(r.more.isEmpty())
        // Quit off: it waits in More.
        c = M.toggle(c, "quit", false)
        r = M.layout(c, emptyList(), monkeyPowers, true)
        assertEquals(listOf("chat", "talk", "missed", "screen", "powers", M.MORE), r.main)
        assertEquals(listOf("quit"), r.more)
        // Everything on: 5 + More, the rest in More, never more than 6 on the ring.
        var all = M.defaults(); for (it in M.Item.entries) all = M.toggle(all, it.id, true)
        r = M.layout(all, emptyList(), monkeyPowers, true)
        assertEquals(6, r.main.size); assertEquals(M.MORE, r.main.last())
        assertEquals(M.Item.entries.size - 5, r.more.size)
        // Almost everything off: Chat + More(Quit).
        var few = M.defaults(); for (it in M.Item.entries) few = M.toggle(few, it.id, it == M.Item.CHAT)
        assertEquals(listOf("chat", M.MORE), M.layout(few, emptyList(), monkeyPowers, true).main)
    }

    @Test fun powerPinsPerCharacter() {
        var c = M.defaults(); for (id in listOf("context", "funny", "games", "smash", "clip", "screen", "missed")) c = M.toggle(c, id, false)
        // Monkey pins Thunder: it shows; Zoom has no Thunder: the stale pin drops.
        val r = M.layout(c, listOf("thunder"), monkeyPowers, true)
        assertEquals(listOf("chat", "talk", "powers", "quit", M.pin("thunder")), r.main)
        val sb = M.layout(c, listOf("thunder"), Superboy.powers.map { it.id }, true)
        assertFalse(M.pin("thunder") in sb.main)
    }

    @Test fun reorderKeepsHiddenSlots() {
        val c = M.Config(listOf("chat", "power:swoop", "talk", "power:thunder", "quit"), emptySet())
        val visible = listOf("chat", "talk", "power:thunder", "quit")   // the monkey editor doesn't show Zoom's swoop
        val moved = M.move(c, visible, 2, 0)
        assertEquals(listOf("power:thunder", "power:swoop", "chat", "talk", "quit"), moved.order)
        assertEquals(c, M.move(c, visible, 1, 1))
    }

    @Test fun encodeDecode() {
        var c = M.toggle(M.defaults(), "dance", true)
        c = M.move(c, c.order, 7, 0)   // quit first
        val back = M.decode(M.encodeOrder(c), M.encodeOff(c))
        assertEquals(c.order, back.order); assertEquals(c.off, back.off)
        // Junk dropped, missing items appended with their defaults.
        val d = M.decode("talk,bogus,chat,talk,power:ice", "dance,nope")
        assertEquals(listOf("talk", "chat", "power:ice"), d.order.take(3))
        assertTrue(d.order.containsAll(M.Item.entries.map { it.id }))
        assertTrue("dance" in d.off && "settings" in d.off && "chat" !in d.off)
        assertEquals(M.defaults(), M.decode(null, null))
    }

    @Test fun persistsInSettings() {
        val s = AppSettings(ApplicationProvider.getApplicationContext())
        var c = M.toggle(s.menuConfig, "sleep", true)
        c = M.move(c, c.order, 0, 3)
        s.menuConfig = c
        s.setMenuPins("monkey", listOf("thunder", "ice")); s.setMenuPins("superboy", listOf("laser"))
        val s2 = AppSettings(ApplicationProvider.getApplicationContext())
        assertEquals(c.order, s2.menuConfig.order); assertEquals(c.off, s2.menuConfig.off)
        assertEquals(listOf("thunder", "ice"), s2.menuPins("monkey"))
        assertEquals(listOf("laser"), s2.menuPins("superboy"))
        assertEquals(emptyList<String>(), s2.menuPins("armour"))
        s2.resetMenu()
        assertEquals(M.defaults(), s2.menuConfig); assertEquals(emptyList<String>(), s2.menuPins("monkey"))
        assertEquals(120, s2.chatTimeoutSecs)
    }

    @Test fun powersRingPerCharacter() {
        for (ch in CharacterRegistry.all) {
            val ids = M.powerRing(ch.powers)
            assertEquals(ch.powers.map { it.id }, ids)
            // Every power appears on exactly one page; every page has Back + Random, Next except on the last.
            val pages = M.pageCount(ids)
            val seen = (0 until pages).flatMap { p ->
                val pg = M.page(ids, p, random = true)
                assertEquals(M.BACK, pg.first()); assertTrue(M.RANDOM in pg)
                assertEquals(p < pages - 1, M.NEXT in pg)
                assertTrue(pg.size <= 7)
                pg.filter { it !in setOf(M.BACK, M.RANDOM, M.NEXT) }
            }
            assertEquals(ids, seen)
            for (id in ids) assertTrue(M.shortName(id, id).length <= 10)
        }
        assertEquals(3, M.pageCount(MonkeyCharacter.powers.map { it.id }))   // 9 powers, 4 per page
        assertEquals(0, M.nextPage(List(9) { "p$it" }, 2))
        assertEquals(listOf("swoop", "laser", "breath", "lift", "blur"), M.powerRing(Superboy.powers))
        assertTrue("jets" in M.powerRing(ArmourAnna.powers) && "magic" in M.powerRing(MaskMan.powers))
    }

    @Test fun cooldownState() {
        assertEquals(M.PowerState.Ready, M.powerState(0f))
        assertEquals(M.PowerState.Cooling(13), M.powerState(12.2f))
        assertEquals(M.PowerState.Cooling(1), M.powerState(0.01f))
        assertEquals("13s", M.powerLabel("Thunder", M.powerState(12.2f)))
        assertEquals("Thunder", M.powerLabel("Thunder", M.PowerState.Ready))
        assertTrue(M.saverWarn(saver = true, warnedBefore = false))
        assertFalse(M.saverWarn(saver = true, warnedBefore = true))
        assertFalse(M.saverWarn(saver = false, warnedBefore = false))
    }
}
