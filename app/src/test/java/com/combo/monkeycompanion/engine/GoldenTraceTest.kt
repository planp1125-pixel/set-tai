package com.combo.monkeycompanion.engine

import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.TestPaths
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.fun5.Stage
import com.combo.monkeycompanion.landing.LandingE2ETest
import com.combo.monkeycompanion.overlay.MonkeyView
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M1 safety net: frame-by-frame motion traces of the real overlay view, recorded on v9.5 and compared exactly
 * (Float.toString, no rounding). Any physics refactor must reproduce them bit for bit.
 *
 * Scenarios avoid random branches (short drops always land SIMPLE; walks end in idleNow(999); traces stop before
 * the controller's next random decision), and each one is run twice to prove it's deterministic.
 * Re-record (only when a behaviour change is intended and approved): GOLDEN_RECORD=1 ./gradlew :app:testFullDebugUnitTest
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class GoldenTraceTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val record = !System.getProperty("golden.record").isNullOrEmpty()

    private fun newView(charId: String, frac: Float = 0.5f): MonkeyView {
        ChatHub.init(ctx)
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.batterySaver = false
        ChatHub.settings.gags = false
        ChatHub.settings.roam = true
        CharacterRegistry.select(charId)
        val v = MonkeyView(ctx, LandingE2ETest.Host())
        v.layout(0, 0, 1080, 2340)
        v.debugFrames(60)
        v.debugSurf("FLOOR", frac = frac)
        v.debugFrames(10)
        v.debugQuiet = true
        return v
    }

    private var clock = 10_000L

    /** Press the middle of the box, move through cumulative (dx, dy) screen px every [stepMs], wait [holdMs], lift. */
    private fun gesture(v: MonkeyView, moves: List<Pair<Float, Float>>, stepMs: Long = 16, holdMs: Long = 300) {
        val b = v.debugBox(); val w = v.debugWin()
        val lx = b.centerX() - w[0]; val ly = b.centerY() - w[1]
        val rx = b.centerX(); val ry = b.centerY()
        fun send(a: Int, dx: Float, dy: Float) {
            val e = MotionEvent.obtain(clock, clock, a, rx + dx, ry + dy, 0)
            e.offsetLocation(lx - rx, ly - ry)
            v.dispatchTouchEvent(e); e.recycle()
        }
        send(MotionEvent.ACTION_DOWN, 0f, 0f)
        var last = 0f to 0f
        for (m in moves) { clock += stepMs; send(MotionEvent.ACTION_MOVE, m.first, m.second); last = m; v.debugFrames(1) }
        clock += holdMs
        send(MotionEvent.ACTION_UP, last.first, last.second)
    }
    private fun line(dx: Float, dy: Float, n: Int = 12) = (1..n).map { dx * it / n to dy * it / n }

    private class Trace(val v: MonkeyView) {
        val lines = ArrayList<String>()
        fun snap(tag: String = "") {
            val b = v.debugBox()
            lines += "${lines.size} ${b.left} ${b.top} f=${v.debugFalling} s=${v.debugSurfKind}/${v.debugSpotIndex} st=${v.controller.state} " +
                "face=${v.controller.facing} land=${v.debugLandStyle} beat=${v.debugLandBeat} coy=${v.coyoteScareTimer} " +
                "crash=${v.edgeThrustFailurePhase}/${v.edgeThrustMultiplier}/${v.edgeCrashRot} hid=${v.debugHidden} ime=${v.keyboardDodge} " +
                "win=${v.debugWin().joinToString(",")}$tag"
        }
        fun run(n: Int) = repeat(n) { v.debugFrames(1); snap() }
        fun runWhile(max: Int, cond: () -> Boolean) { var i = 0; while (cond() && i < max) { v.debugFrames(1); snap(); i++ } }
    }

    private fun check(name: String, scenario: () -> List<String>) {
        val a = scenario()
        val b = scenario()
        assertEquals("$name is not deterministic (two runs differ)", a, b)
        val file = TestPaths.root("app/src/test/resources/golden/$name.trace")
        if (record) { file.parentFile?.mkdirs(); file.writeText(a.joinToString("\n", postfix = "\n")); return }
        if (!file.exists()) fail("$name: no golden trace at $file (record it on the v9.5 baseline with GOLDEN_RECORD=1)")
        val want = file.readLines().filter { it.isNotEmpty() }
        val n = minOf(want.size, a.size)
        for (i in 0 until n) if (want[i] != a[i]) fail("$name: first difference at frame $i\n  v9.5: ${want[i]}\n  now : ${a[i]}")
        assertEquals("$name: trace length", want.size, a.size)
    }

    /** Lifted 330 px (< 140 dp, so always a SIMPLE landing) and let go mid-screen. */
    private fun shortDrop(id: String) = check("drop_short_$id") {
        val v = newView(id); val t = Trace(v)
        gesture(v, line(0f, -330f)); t.snap(" released")
        t.runWhile(400) { v.debugFalling }; t.run(20)
        t.lines
    }

    @Test fun dropShortMonkey() = shortDrop("monkey")
    @Test fun dropShortMaskUmbrella() = shortDrop("mask")

    /** Billa / Chittu let go mid-screen: 1.0 s Tom & Jerry hold, cannonball fall with one bounce. */
    private fun coyote(id: String) = check("coyote_$id") {
        val v = newView(id); val t = Trace(v)
        gesture(v, line(0f, -1000f)); t.snap(" released")
        t.runWhile(500) { v.debugFalling }; t.run(20)
        t.lines
    }

    @Test fun coyoteBilla() = coyote("billa")
    @Test fun coyoteChittu() = coyote("chittu")

    /** Flying heroes: edge collision, sputter, plunge, crash-landed stars, recovery. */
    private fun edgeCrash(id: String) = check("edge_crash_$id") {
        val v = newView(id, frac = 0.3f); val t = Trace(v)
        v.triggerEdgeCollisionWhileFlying(forceRandomPass = true); t.snap(" hit")
        t.runWhile(500) { v.isEdgeCrashLanding }; t.run(20)
        t.lines
    }

    @Test fun edgeCrashArmour() = edgeCrash("armour")
    @Test fun edgeCrashSuperboy() = edgeCrash("superboy")

    /** Walking along a surface (MoodController.stepWalk -> MonkeyBody.moveCenterTo). */
    private fun walk(id: String, kind: String, from: Float, to: Float) = check("walk_${id}_$kind") {
        val v = newView(id); val t = Trace(v)
        v.debugSurf(kind, frac = from); v.debugFrames(2); t.snap(" placed")
        v.debugWalk(to); t.runWhile(1500) { v.controller.state == MonkeyState.WALK }; t.run(10)
        t.lines
    }

    @Test fun walkMonkeyFloor() = walk("monkey", "FLOOR", 0.1f, 0.9f)
    @Test fun walkMonkeyWallL() = walk("monkey", "WALL_L", 0.2f, 0.9f)
    @Test fun walkMonkeyCeil() = walk("monkey", "CEIL", 0.8f, 0.2f)
    @Test fun walkBillaWallR() = walk("billa", "WALL_R", 0.9f, 0.1f)

    /** Fast fling at the right edge: dash and duck behind it, then come back out of the tab. */
    @Test fun hideAndUnhideMonkey() = check("hide_unhide_monkey") {
        val v = newView("monkey"); val t = Trace(v)
        gesture(v, line(420f, 0f, 3), holdMs = 5); t.snap(" flung")
        t.run(40)
        v.unhide(); t.snap(" unhide")
        t.run(150)
        t.lines
    }

    /** Keyboard opens in another app: dodge above it; closes: go back. */
    private fun imeDodge(id: String) = check("ime_dodge_$id") {
        val v = newView(id, frac = 0.7f); val t = Trace(v)
        try {
            Stage.reportProbeIme(true, 1300f); t.snap(" ime open")
            t.run(40)
            Stage.reportProbeIme(false, 0f); t.snap(" ime closed")
            t.run(40)
        } finally { Stage.reportProbeIme(false, 0f) }
        t.lines
    }

    @Test fun imeDodgeMonkey() = imeDodge("monkey")
    @Test fun imeDodgeMask() = imeDodge("mask")
}
