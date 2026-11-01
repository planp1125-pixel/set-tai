package com.combo.monkeycompanion.v1021

import com.combo.monkeycompanion.engine.show.ActorStart
import com.combo.monkeycompanion.engine.show.Drama
import com.combo.monkeycompanion.engine.show.Episode
import com.combo.monkeycompanion.engine.show.Fx2
import com.combo.monkeycompanion.engine.show.Role
import com.combo.monkeycompanion.engine.show.Snap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * v10.22 the Billa & Chittu drama: 2,400 mix-and-match shows, played by the real characters from wherever they are.
 * Every combination is checked: a proper short, no teleports, no melting into one blob, readable English lines.
 * Pure (no Android).
 */
class ShowEpisodeTest {
    private val floor = ActorStart.floor(0.3f, 1f) to ActorStart.floor(0.75f, -1f)
    /** Real places the characters can be when you start the show. */
    private val starts = listOf(
        floor,
        ActorStart.floor(0.8f, -1f) to ActorStart.floor(0.2f, 1f),                         // the other way round
        ActorStart.floor(0.5f, 1f) to ActorStart.floor(0.52f, 1f),                         // on top of each other
        ActorStart(ActorStart.WALL_R, 0.93f, 0.9f, 1f) to ActorStart.floor(0.3f, 1f),      // Billa high on the right wall
        ActorStart.floor(0.6f) to ActorStart(ActorStart.TOP, 0.4f, 1.6f, 1f),               // Chittu on the top edge
        ActorStart(ActorStart.WALL_L, 0.07f, 0.4f, -1f) to ActorStart(ActorStart.OFF, 1.1f, 0f, -1f),   // Chittu hidden
        ActorStart(ActorStart.FLOOR, 0.45f, 0.3f, 1f) to ActorStart(ActorStart.WALL_R, 0.94f, 0.2f, 1f), // on a widget ledge
    )
    private val tamil = Regex("(?i)\\b(machan|da|aiyo|ayyo|romba|seri|kadi|amma|enna|vaa|odu|thol|semma|vanakkam|innum|nillu|keezha|mela|chumma|paadhi|inda)\\b")

    private fun each(step: Int = 1, block: (Int, Pair<ActorStart, ActorStart>, Episode) -> Unit) {
        var n = 0
        while (n < Drama.COMBOS) {
            val st = starts[n % starts.size]
            block(n, st, Drama.build(n, st.first, st.second))
            n += step
        }
    }

    @Test fun twoThousandFourHundredDifferentShows() {
        assertEquals(2400, Drama.COMBOS)
        val seen = HashSet<String>()
        var prev: IntArray? = null
        for (n in 0 until Drama.COMBOS) {
            val p = Drama.parts(n)
            assertTrue(seen.add(p.joinToString(",")))
            if (prev != null) assertTrue("show $n repeats the last one's opening or ending", p[0] != prev[0] && p[4] != prev[4])
            prev = p
        }
    }

    @Test fun everyShowIsAProperShortInPlainEnglish() = each { n, _, e ->
        assertTrue("$n: ${e.total}s", e.total in 20f..46f)
        assertTrue("$n: no title card", e.title.isBlank())
        assertTrue("$n: a crash", e.cues.any { it.kind == Fx2.WORD })
        assertTrue("$n: a heart", e.cues.any { it.kind == Fx2.HEART || it.kind == Fx2.HEARTS })
        assertTrue("$n: both talk", e.lines.any { it.role == Role.CAT } && e.lines.any { it.role == Role.RAT })
        assertTrue("$n: sounds", e.beeps.size >= 8)
        for (l in e.lines) assertTrue("$n: not plain English: '${l.text}'", !tamil.containsMatchIn(l.text))
    }

    /** Nobody teleports: every move is a continuous path (popping out of a hole only happens while hidden). */
    @Test fun nobodyTeleports() = each(step = 3) { n, _, e ->
        val dt = 1f / 60f
        for (id in listOf(Episode.CAT, Episode.RAT)) {
            val tr = e.track(id)!!
            val a = Snap(); val b = Snap()
            var t = 0f
            while (t < e.irisAt + 1f) {
                tr.sample(t, a); tr.sample(t + dt, b)
                if (a.visible && b.visible) {
                    assertTrue("$n $id x jumps at %.2f: %.3f -> %.3f".format(t, a.x, b.x), abs(b.x - a.x) < 0.07f)
                    assertTrue("$n $id y jumps at %.2f: %.3f -> %.3f".format(t, a.y, b.y), abs(b.y - a.y) < 0.07f)
                    var dr = abs(b.rot - a.rot) % 360f; if (dr > 180f) dr = 360f - dr
                    assertTrue("$n $id spins too fast at %.2f (%.0f°)".format(t, dr), dr < 45f)
                }
                t += dt
            }
        }
    }

    /** The cat and the mouse never melt into one blob, except on purpose (a catch, a hug, a ride, a shared snack). */
    @Test fun theyStayApartUnlessTheyMeanToTouch() = each { n, _, e ->
        val c = Snap(); val r = Snap()
        var t = 0f
        while (t < e.irisAt) {
            e.track(Episode.CAT)!!.sample(t, c); e.track(Episode.RAT)!!.sample(t, r)
            val on = c.visible && r.visible && c.x in 0.02f..0.98f && r.x in 0.02f..0.98f
            if (on && e.touchWindows.none { t in it }) {
                val apart = abs(c.x - r.x) >= 0.15f
                val above = r.y >= 0.33f || c.y >= 0.33f
                val onWall = (r.edge == 2 && r.y > 0.1f) || (c.edge == 2 && c.y > 0.05f) || r.edge == 1 || c.edge == 1
                val lying = abs(c.rot) >= 60f && abs(c.rot) <= 120f && c.y <= 0.02f
                assertTrue("$n ${Drama.parts(n).toList()}: overlap at %.2f (cat %.2f, mouse %.2f)".format(t, c.x, r.x), apart || above || onWall || lying)
            }
            t += 0.05f
        }
    }

    /** Lines are speech bubbles over a visible speaker, never more than two at once, long enough to read. */
    @Test fun linesAreReadable() = each(step = 2) { n, _, e ->
        for (l in e.lines) {
            assertTrue("$n: '${l.text}' too short", l.dur >= 0.8f)
            val s = Snap()
            e.track(if (l.role == Role.CAT) Episode.CAT else Episode.RAT)!!.sample(l.at + 0.05f, s)
            assertTrue("$n: '${l.text}' said off stage", s.visible)
            val busy = e.lines.count { o -> o.at < l.at + l.dur && l.at < o.at + o.dur }
            assertTrue("$n: too many bubbles around '${l.text}'", busy <= 2)
        }
    }

    /** It starts exactly where they really are (no jump at the start). */
    @Test fun startsWhereTheyAre() {
        val wall = ActorStart(ActorStart.WALL_R, 0.93f, 0.9f, 1f)
        val e = Drama.build(5, wall, ActorStart.floor(0.3f, 1f))
        val c = Snap(); e.track(Episode.CAT)!!.sample(0f, c)
        assertEquals(0.93f, c.x, 0.001f); assertEquals(0.9f, c.y, 0.001f); assertEquals("climbing pose, like the app", 2, c.edge)
        val r = Snap(); e.track(Episode.RAT)!!.sample(0f, r)
        assertEquals(0.3f, r.x, 0.001f)
    }
}
