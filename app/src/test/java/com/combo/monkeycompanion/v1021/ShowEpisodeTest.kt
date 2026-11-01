package com.combo.monkeycompanion.v1021

import com.combo.monkeycompanion.engine.show.Episode
import com.combo.monkeycompanion.engine.show.Episodes
import com.combo.monkeycompanion.engine.show.Fx2
import com.combo.monkeycompanion.engine.show.Role
import com.combo.monkeycompanion.engine.show.Snap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * v10.21 Show 2.0: the Billa & Chittu episodes play like a real cartoon short.
 * Pure (no Android): the episodes are authored timelines sampled by time.
 */
class ShowEpisodeTest {
    private val eps = Episodes.all.indices.map { Episodes.byIndex(it) }
    private val dt = 1f / 60f

    private fun onScreen(s: Snap) = s.visible && s.x > 0.02f && s.x < 0.98f

    @Test fun everyEpisodeIsAProperShort() {
        assertTrue(eps.size >= 3)
        assertEquals("all different", eps.size, eps.map { it.title }.toSet().size)
        for (e in eps) {
            assertTrue("${e.title}: ${e.total}s is a short, not a blink", e.total in 24f..42f)
            assertTrue("${e.title}: has a crash", e.cues.any { it.kind == Fx2.WORD })
            assertTrue("${e.title}: has a heart moment", e.cues.any { it.kind == Fx2.HEART || it.kind == Fx2.HEARTS })
            assertTrue("${e.title}: both talk", e.lines.any { it.role == Role.CAT } && e.lines.any { it.role == Role.RAT })
            assertTrue("${e.title}: sounds", e.beeps.size >= 10)
            assertTrue("${e.title}: iris after the story", e.irisAt > 20f)
        }
    }

    /** Nobody teleports: every move is a continuous path (a pop out of the hole is the only "jump", and only while hidden). */
    @Test fun nobodyTeleports() {
        for (e in eps) for (id in listOf(Episode.CAT, Episode.RAT)) {
            val tr = e.track(id)!!
            val a = Snap(); val b = Snap()
            var t = 0f
            while (t < e.irisAt + 2f) {
                tr.sample(t, a); tr.sample(t + dt, b)
                if (a.visible && b.visible) {
                    assertTrue("${e.title} $id x jumps at %.2f: %.3f -> %.3f".format(t, a.x, b.x), abs(b.x - a.x) < 0.07f)
                    assertTrue("${e.title} $id y jumps at %.2f".format(t), abs(b.y - a.y) < 0.07f)
                    var dr = abs(b.rot - a.rot); if (dr > 180f) dr = 360f - dr
                    assertTrue("${e.title} $id spins too fast at %.2f (%.0f°)".format(t, dr), dr < 45f)
                }
                t += dt
            }
        }
    }

    /** The cat and the mouse never melt into one blob, except on purpose (a catch, a ride, a shared laddu). */
    @Test fun theyStayApartUnlessTheyMeanToTouch() {
        for (e in eps) {
            val c = Snap(); val r = Snap()
            var t = 0f
            while (t < e.irisAt) {
                e.track(Episode.CAT)!!.sample(t, c); e.track(Episode.RAT)!!.sample(t, r)
                val touching = e.touchWindows.any { t in it }
                if (!touching && onScreen(c) && onScreen(r)) {
                    val apart = abs(c.x - r.x) >= 0.15f
                    val above = r.y >= 0.33f || c.y >= 0.33f
                    val onWall = abs(r.rot) >= 80f && r.y > 0.1f
                    val lying = abs(c.rot) >= 60f   // the cat is flat on the floor; check where his body actually is
                    assertTrue("${e.title}: overlap at %.2f (cat %.2f, mouse %.2f)".format(t, c.x, r.x), apart || above || onWall || lying)
                }
                t += 0.05f
            }
        }
    }

    /** Lines are speech bubbles over a visible speaker, never more than two at once, long enough to read. */
    @Test fun linesAreReadable() {
        for (e in eps) {
            for (l in e.lines) {
                assertTrue("${e.title}: '${l.text}' too short to read", l.dur >= 0.8f)
                assertTrue(l.text.isNotBlank())
                val tr = e.track(if (l.role == Role.CAT) Episode.CAT else Episode.RAT)!!
                val s = Snap()
                tr.sample(l.at + 0.05f, s)
                assertTrue("${e.title}: '${l.text}' spoken by someone off stage", s.visible)
                val busy = e.lines.count { o -> o.at < l.at + l.dur && l.at < o.at + o.dur }
                assertTrue("${e.title}: too many bubbles around '${l.text}'", busy <= 2)
            }
        }
    }

    @Test fun samplingIsPure() {
        val a = Episodes.byIndex(0); val b = Episodes.byIndex(0)
        val s1 = Snap(); val s2 = Snap()
        for (t in listOf(0f, 3.3f, 11.7f, 24.6f, 33f)) {
            a.track(Episode.CAT)!!.sample(t, s1); b.track(Episode.CAT)!!.sample(t, s2)
            assertEquals(s1.x, s2.x, 0f); assertEquals(s1.rot, s2.rot, 0f); assertEquals(s1.state, s2.state)
        }
        assertEquals("rotation wraps", Episodes.byIndex(0).title, Episodes.byIndex(Episodes.all.size).title)
    }
}
