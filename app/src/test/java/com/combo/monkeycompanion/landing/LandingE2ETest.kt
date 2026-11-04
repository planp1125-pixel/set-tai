package com.combo.monkeycompanion.landing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.brain.Landings
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.overlay.MonkeyView
import com.combo.monkeycompanion.overlay.OverlayHost
import com.combo.monkeycompanion.powers.ActionLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.hypot

/** End-to-end: the real overlay view (MonkeyView with an overlay host), real touch events, the real frame loop. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class LandingE2ETest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    class Host : OverlayHost {
        var x = 0; var y = 0; var w = 0; var h = 0
        var fx: ((Canvas) -> Unit)? = null
        override fun showFx(draw: ((Canvas) -> Unit)?): Boolean { fx = draw; return true }
        override fun screenWidth() = 1080
        override fun screenHeight() = 2340
        override fun placeWindow(x: Int, y: Int, w: Int, h: Int) { this.x = x; this.y = y; this.w = w; this.h = h }
    }

    private fun newView(charId: String, roam: Boolean = true, frac: Float = 0.5f): Pair<MonkeyView, Host> {
        ChatHub.init(ctx)
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.batterySaver = false
        ChatHub.settings.gags = false
        ChatHub.settings.roam = roam
        CharacterRegistry.select(charId)
        val host = Host()
        val v = MonkeyView(ctx, host)
        v.layout(0, 0, 1080, 2340)
        v.debugFrames(60)
        v.debugSurf("FLOOR", frac = frac)
        v.debugFrames(10)
        v.debugQuiet = true
        ActionLog.clear(ctx)
        return v to host
    }

    private var clock = 10_000L

    /**
     * A finger gesture on Settai: press its middle, move through [moves] (cumulative dx, dy in screen px, one event
     * every [stepMs]), wait [holdMs] (>80 ms = no fling), lift.
     */
    private fun gesture(v: MonkeyView, moves: List<Pair<Float, Float>>, stepMs: Long = 16, holdMs: Long = 300, frames: Boolean = true) {
        val b = v.debugBox(); val w = v.debugWin()
        val lx = b.centerX() - w[0]; val ly = b.centerY() - w[1]
        val rx = b.centerX(); val ry = b.centerY()
        fun send(a: Int, dx: Float, dy: Float) {
            val e = MotionEvent.obtain(clock, clock, a, rx + dx, ry + dy, 0)
            e.offsetLocation(lx - rx, ly - ry)          // local = window coords, raw = screen coords
            v.dispatchTouchEvent(e); e.recycle()
        }
        send(MotionEvent.ACTION_DOWN, 0f, 0f)
        var last = 0f to 0f
        for (m in moves) { clock += stepMs; send(MotionEvent.ACTION_MOVE, m.first, m.second); last = m; if (frames) v.debugFrames(1) }
        clock += holdMs
        send(MotionEvent.ACTION_UP, last.first, last.second)
    }
    private fun line(dx: Float, dy: Float, n: Int = 12, from: Pair<Float, Float> = 0f to 0f) =
        (1..n).map { from.first + dx * it / n to from.second + dy * it / n }

    private fun dragAndRelease(v: MonkeyView, up: Float) = gesture(v, line(0f, -up))
    /** Lift Settai up by [up], then swipe down fast and let go mid-swipe. */
    private fun flingDown(v: MonkeyView, up: Float) = gesture(v, line(0f, -up) + line(0f, 330f, 3, 0f to -up), holdMs = 5)
    private fun flingSide(v: MonkeyView, up: Float, dx: Float) = gesture(v, line(0f, -up) + line(dx, 0f, 3, 0f to -up), holdMs = 5)

    private val bmp1 = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    data class Track(val xs: List<Float>, val ys: List<Float>, val maxRot: Float, val frames: Int, val fx: Boolean) {
        fun deviation(): Float {
            val x0 = xs.first(); val y0 = ys.first(); val x1 = xs.last(); val y1 = ys.last()
            val len = hypot(x1 - x0, y1 - y0).coerceAtLeast(1f)
            return xs.indices.maxOf { abs((xs[it] - x0) * (y1 - y0) - (ys[it] - y0) * (x1 - x0)) / len }
        }
    }

    /** Runs real frames (and draws them) while Settai is in the air. */
    private fun fly(v: MonkeyView, max: Int = 900): Track {
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>(); var rot = 0f; var f = 0; var fx = false
        while (v.debugFalling && f < max) {
            v.debugFrames(1); f++
            v.draw(Canvas(bmp1))
            val b = v.debugBox(); xs += b.centerX(); ys += b.centerY()
            rot = maxOf(rot, abs(v.debugArcRot)); if (v.debugParticles.active) fx = true
        }
        return Track(xs, ys, rot, f, fx)
    }

    private fun log() = ActionLog.all(ctx).map { it.what + " | " + it.result }

    @Test fun heroesHoverWhereReleasedAndNeverFall() {
        for (id in listOf("superboy", "armour")) {
            val (v, _) = newView(id, roam = false)
            dragAndRelease(v, 1000f)
            val y0 = v.debugBox().top
            v.debugFrames(5)
            assertFalse("$id doesn't fall on release", v.debugFalling)
            repeat(240) { v.debugFrames(1); v.draw(Canvas(bmp1)) }
            assertTrue("$id hovering", v.debugHovering)
            assertFalse(v.debugFalling)
            assertTrue("$id stays up there (${v.debugBox().top} vs $y0)", abs(v.debugBox().top - y0) < 60f && v.debugBox().bottom < 1900f)
            if (id == "armour") assertTrue("Anna's hover thrusters flicker", v.debugHoverFlames > 100)
        }
    }

    @Test fun heroesNearAnEdgeDriftBackOnScreen() {
        val (v, _) = newView("superboy", roam = false, frac = 0.98f)
        gesture(v, line(200f, -900f))          // dragged right off the edge
        v.debugFrames(60)
        val b = v.debugBox()
        assertTrue(v.debugHovering); assertTrue("on screen: ${b.right}", b.right <= 1080f && b.left >= 0f)
    }

    @Test fun swipeDownIsTouchDownWithACurvedRocketLanding() {
        for (id in listOf("superboy", "armour")) {
            val (v, _) = newView(id, roam = false)
            flingDown(v, 1300f)
            v.debugFrames(1)
            assertTrue("$id falling after the swipe", v.debugFalling)
            assertEquals(Landings.Style.ROCKET_ARC, v.debugLandStyle)
            val tr = fly(v)
            assertFalse("$id landed", v.debugFalling)
            assertFalse(v.debugHovering)
            assertTrue("$id on the floor", v.debugBox().bottom > 2000f)
            assertTrue("$id banks (${tr.maxRot})", tr.maxRot > 30f)
            assertTrue("$id curved (${tr.deviation()})", tr.deviation() > 150f)
            assertTrue("$id thrust drawn", v.debugLandFlames > 20)
            assertTrue(tr.fx)
            val l = log()
            assertTrue("$l", l.any { it.startsWith("Landing picked: rocket arc (you swiped down: touch down)") })
            assertTrue("$l", l.any { it.startsWith("Landing played: rocket arc") && "flames/streaks drawn" in it })
        }
    }

    @Test fun sidewaysFlingFliesAnArcAndHoversAgain() {
        val (v, _) = newView("armour", roam = false, frac = 0.3f)
        val x0 = v.debugBox().centerX()
        flingSide(v, 900f, 300f)
        v.debugFrames(1)
        assertTrue("falling ${v.debugFalling} hidden ${v.debugHidden} hovering ${v.debugHovering} box ${v.debugBox()} ${log()}", v.debugFalling)
        assertTrue(v.debugArc != null)
        val tr = fly(v)
        assertFalse(v.debugFalling)
        v.debugFrames(5)
        assertTrue("hovering again", v.debugHovering)
        assertTrue("flew right: ${v.debugBox().centerX()} vs $x0", v.debugBox().centerX() - x0 > 200f)
        assertTrue("curved", tr.deviation() > 60f)
        assertTrue(log().any { it.startsWith("Flight:") && "flung me right" in it })
    }

    @Test fun monkeyStillDropsWithCurvedJetLandings() {
        val (v, _) = newView("monkey")
        var got = false
        repeat(30) {
            if (got) return@repeat
            v.debugSurf("FLOOR", frac = 0.2f + 0.1f * (it % 3)); v.debugFrames(5)
            gesture(v, line(0f, -1100f))
            v.debugFrames(1)
            if (v.debugFalling && v.debugLandStyle == Landings.Style.ROCKET_ARC) got = true else v.debugFrames(400)
        }
        assertFalse("v10.25.1: Settai never flies the looping jet arc", got)
    }

    @Test fun maskManTries() {
        // Released mid-air: one of his "tries" (flap / umbrella, crow taxi, ninja flip, plop) — never a rocket.
        val (v, _) = newView("mask")
        val seen = HashSet<Landings.Style>()
        repeat(16) {
            v.debugSurf("FLOOR", frac = 0.5f); v.debugFrames(5)
            dragAndRelease(v, 900f); v.debugFrames(1)
            if (v.debugFalling) seen += v.debugLandStyle
            if (v.debugLandStyle == Landings.Style.CROW_CARRY) {
                v.debugFrames(40)   // decision beat + carry
                val x0 = v.debugBox().centerX(); v.debugFrames(40)
                assertTrue("crow carries him sideways", abs(v.debugBox().centerX() - x0) > 40f)
            }
            fly(v)
            assertFalse(v.debugFalling)
        }
        assertTrue("$seen", Landings.Style.CROW_CARRY in seen)
        assertFalse(Landings.Style.ROCKET_ARC in seen)
        // Hangs from the top (complaining), then drops.
        v.debugMaskHang(); v.debugFrames(30)
        assertEquals("hang", v.debugMaskClimb)
        assertTrue(v.debugBox().top < 400f)
        v.debugFrames(60 * 8)
        assertEquals("", v.debugMaskClimb)
        // Clumsy wall climb: goes up the edge (slips allowed), ends hanging or dropping.
        v.debugSurf("FLOOR", frac = 0.5f); v.debugFrames(5)
        v.debugMaskClimb(-1); v.debugFrames(20)
        val y0 = v.debugBox().top
        assertEquals(0f, v.debugBox().left, 1f)
        // slips are random: within 4 s he gets higher than he started, or the climb ends (hang / drop)
        var ok = false
        for (i in 0 until 240) { v.debugFrames(1); if (v.debugMaskClimb.isEmpty() || v.debugBox().top < y0 - 5f || v.debugFalling) { ok = true; break } }
        assertTrue(ok)
    }

    @Test fun pickedUpMidAirIsLoggedAsInterrupted() {
        val (v, _) = newView("armour", roam = false)
        flingDown(v, 1300f)
        v.debugFrames(60)
        assertTrue(v.debugFalling)
        dragAndRelease(v, 200f)
        assertTrue("${log()}", log().any { it.contains("interrupted") })
    }

    // ---------------- contact sheet ----------------

    private class Panel(val title: String, val sub: String, val bmp: Bitmap)

    /** Onion skin of the real view on a phone-sized canvas while [run] drives it; returns the screen bitmap. */
    private fun record(v: MonkeyView, h: Host, sub: StringBuilder, setup: () -> Unit, minFrames: Int, maxFrames: Int, every: Int = 6): Bitmap {
        val screen = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        val sc = Canvas(screen)
        sc.drawColor(Color.rgb(0xE6, 0xEE, 0xF8))
        val grid = Paint().apply { color = Color.rgb(0xC9, 0xD6, 0xE6) }
        for (r in 0 until 6) for (k in 0 until 4) sc.drawRoundRect(80f + k * 240f, 300f + r * 300f, 240f + k * 240f, 460f + r * 300f, 40f, 40f, grid)
        val b0 = v.debugBox()
        setup()
        val b1 = v.debugBox()
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f) }
        // the finger's drag (blue)
        if (hypot(b1.centerX() - b0.centerX(), b1.centerY() - b0.centerY()) > 20f) {
            dash.color = Color.argb(200, 0x2E, 0x6F, 0xE8); sc.drawLine(b0.centerX(), b0.centerY(), b1.centerX(), b1.centerY(), dash)
        }
        val track = Path(); track.moveTo(b1.centerX(), b1.centerY())
        val layer = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888); val lc = Canvas(layer)
        val ghost = Paint()
        var f = 0
        var plan: String? = null
        while (f < maxFrames && (f < minFrames || v.debugFalling)) {
            v.debugFrames(1); f++
            if (plan == null) v.debugArc?.let { plan = "${it.path.label}" }
            val b = v.debugBox(); track.lineTo(b.centerX(), b.centerY())
            val end = !(f < minFrames || v.debugFalling) || f == maxFrames
            if (f % every == 1 || end) {
                val w = v.debugWin()
                layer.eraseColor(Color.TRANSPARENT)
                h.fx?.invoke(lc)
                lc.save(); lc.translate(w[0].toFloat(), w[1].toFloat()); lc.clipRect(0, 0, w[2], w[3]); v.draw(lc); lc.restore()
                ghost.alpha = if (end) 255 else (60 + 150 * f / maxFrames).coerceAtMost(200)
                sc.drawBitmap(layer, 0f, 0f, ghost)
            }
        }
        dash.color = Color.argb(200, 0xE8, 0x6A, 0x1E); sc.drawPath(track, dash)
        sub.append(plan?.let { "$it · " } ?: "").append("${"%.1f".format(f / 60f)} s")
        return screen
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun contactSheet() {
        val panels = ArrayList<Panel>()
        fun panel(title: String, id: String, roam: Boolean, frac: Float, sub0: String, min: Int, max: Int, every: Int = 6, setup: (MonkeyView) -> Unit) {
            val (v, h) = newView(id, roam, frac)
            val sb = StringBuilder(sub0)
            val bmp = record(v, h, sb, { setup(v) }, min, max, every)
            panels += Panel(title, sb.toString(), bmp)
        }
        panel("Superboy: let go mid-air", "superboy", false, 0.4f, "hovers (bob + cape) · ", 150, 150, every = 25) { v -> dragAndRelease(v, 1000f) }
        panel("Superboy: swipe down", "superboy", false, 0.4f, "rocket arc, fist-first · ", 30, 700) { v -> flingDown(v, 1300f) }
        panel("Armour Anna: fling sideways", "armour", false, 0.2f, "arc, then hovers again · ", 30, 700) { v -> flingSide(v, 900f, 300f) }
        panel("Armour Anna: swipe down", "armour", false, 0.6f, "jet-brake hover, thud · ", 30, 700) { v -> flingDown(v, 1300f) }
        run {   // the monkey: drop until a rocket arc comes up
            val (v, h) = newView("monkey")
            var tries = 0
            while (tries < 30) {
                v.debugSurf("FLOOR", frac = 0.2f + 0.1f * (tries % 3)); v.debugFrames(5)
                gesture(v, line(0f, -1100f)); v.debugFrames(1)
                if (v.debugFalling && v.debugLandStyle == Landings.Style.ROCKET_ARC) break
                v.debugFrames(400); tries++
            }
            val sb = StringBuilder("leg-fire rocket arc, crash · ")
            val bmp = record(v, h, sb, {}, 30, 700)
            panels += Panel("Settai: let go mid-air", sb.toString(), bmp)
        }
        run {   // Mask Man: release until the crow taxi
            val (v, h) = newView("mask")
            var tries = 0
            while (tries < 30) {
                v.debugSurf("FLOOR", frac = 0.3f); v.debugFrames(5)
                dragAndRelease(v, 1000f); v.debugFrames(1)
                if (v.debugFalling && v.debugLandStyle == Landings.Style.CROW_CARRY) break
                v.debugFrames(400); tries++
            }
            val sb = StringBuilder("crow taxi, then dropped · ")
            val bmp = record(v, h, sb, {}, 30, 700)
            panels += Panel("Mask Man: tries to fly", sb.toString(), bmp)
        }
        run {
            CharacterRegistry.select("mask"); CharacterRegistry.wear().set("umbrella", true)
            val (v, h) = newView("mask")
            val sb = StringBuilder("hangs by the umbrella handle · ")
            val bmp = record(v, h, sb, { v.debugMaskHang() }, 120, 120, every = 40)
            panels += Panel("Mask Man: dragged to the top", sb.toString(), bmp)
        }
        val sc = 0.3f; val pw = (1080 * sc).toInt(); val ph = (2340 * sc).toInt()
        val cols = 4; val rows = (panels.size + cols - 1) / cols
        val sheet = Bitmap.createBitmap(cols * (pw + 30) + 30, 70 + rows * (ph + 110), Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet); c.drawColor(Color.rgb(0xFF, 0xF3, 0xE0))
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; color = Color.rgb(0x5A, 0x3A, 0x1E); typeface = Typeface.DEFAULT_BOLD }
        val sp = Paint(tp).apply { textSize = 16f; typeface = Typeface.DEFAULT }
        c.drawText("Settai v5 · locomotion & power landings from the real overlay path · blue = your drag, orange = flight path, onion skin every 6 frames", 30f, 42f, tp)
        panels.forEachIndexed { i, p ->
            val x0 = 30f + (i % cols) * (pw + 30); val y0 = 60f + (i / cols) * (ph + 110)
            c.drawBitmap(p.bmp, null, RectF(x0, y0, x0 + pw, y0 + ph), null)
            c.drawText(p.title, x0, y0 + ph + 28f, tp)
            c.drawText(p.sub, x0, y0 + ph + 52f, sp)
        }
        val out = com.combo.monkeycompanion.TestPaths.out("settai-landings-contact.png")
        java.io.FileOutputStream(out).use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("CONTACT_WRITTEN ${out.absolutePath}")
    }
}
