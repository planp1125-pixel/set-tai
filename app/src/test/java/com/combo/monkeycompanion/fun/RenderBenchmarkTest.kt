package com.combo.monkeycompanion.`fun`

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Picture
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.MoodController
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.Animations
import com.combo.monkeycompanion.rig.MonkeyRenderer
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.speech.Speech
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.lang.management.ManagementFactory

/**
 * Measures the CPU cost of one Settai frame on this machine (JVM + Robolectric native graphics):
 *  1. "update": brain + animation + blend + rig layout (what doFrame does besides drawing)
 *  2. "record": renderer.draw into a Picture (closest to Android's hardware-accelerated
 *     display-list recording on the UI thread)
 *  3. "raster": renderer.draw into a software bitmap the size of the overlay window
 *     (an upper bound: on a phone the GPU does this part)
 * Results are printed and written to build/settai-bench.txt.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RenderBenchmarkTest {
    private val mx = ManagementFactory.getThreadMXBean()
    private fun cpu() = mx.currentThreadCpuTime

    private class Quiet : Speech { override fun say(text: String, durationMs: Long) {}; override fun hush() {}; override val isSpeaking = false }

    private inline fun measure(frames: Int, block: (Int) -> Unit): Double {
        repeat(frames / 3) { block(it) }          // warm-up (JIT)
        val c0 = cpu()
        repeat(frames) { block(it) }
        return (cpu() - c0) / 1e6 / frames      // ms CPU per frame
    }

    @Test fun benchmark() {
        val density = 2.625f                       // a typical 1080p phone (420 dpi)
        val boxW = (136 * density).toInt(); val boxH = (150 * density).toInt()
        val rigScale = 0.66f * density
        val mc = MoodController(Quiet(), null)
        val target = Pose(); val current = Pose(); val rig = MonkeyRig(); val renderer = MonkeyRenderer()
        val ctx = AnimContext()
        val dt = 1f / 30f
        var t = 0f
        fun step(state: MonkeyState?) {
            t += dt
            mc.update(dt)
            Animations.fill(target, state ?: mc.state, t, if (state != null) t % 4f else mc.stateTime, if (state != null) ctx else mc.anim)
            current.blendToward(target, 0.37f)
            rig.layout(current, boxW / 2f, boxH - 7 * density, rigScale, 1f)
        }
        val n = 3000
        val updIdle = measure(n) { step(MonkeyState.IDLE) }
        ctx.danceMoves = intArrayOf(0, 3, 5)
        val updDance = measure(n) { step(MonkeyState.DANCE) }

        val bmp = Bitmap.createBitmap(boxW, boxH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        fun raster() { bmp.eraseColor(Color.TRANSPARENT); renderer.draw(canvas, rig, current, t) }
        step(MonkeyState.IDLE)
        val rasterIdle = measure(600) { raster() }
        step(MonkeyState.DANCE)
        val rasterDance = measure(600) { raster() }
        ctx.gag = com.combo.monkeycompanion.brain.Gag.JUGGLE
        Animations.fill(current, MonkeyState.GAG, 1.3f, 1.3f, ctx); rig.layout(current, boxW / 2f, boxH - 7 * density, rigScale, 1f)
        val rasterGag = measure(600) { raster() }

        val pic = Picture()
        val record = runCatching { measure(1500) { val c = pic.beginRecording(boxW, boxH); renderer.draw(c, rig, current, t); pic.endRecording() } }.getOrDefault(Double.NaN)

        // Allocation check: bytes allocated per frame by update + draw (should be ~0 after the v3.2 fixes).
        val sun = mx as? com.sun.management.ThreadMXBean
        val tid = Thread.currentThread().id
        val a0 = sun?.getThreadAllocatedBytes(tid) ?: -1L
        repeat(1000) { step(MonkeyState.IDLE); renderer.draw(canvas, rig, current, t) }
        val allocPerFrame = if (sun != null) (sun.getThreadAllocatedBytes(tid) - a0) / 1000.0 else Double.NaN

        // v3.2 superpower flight: flight + pose + rig + particles + cape, recorded (HW path) and rastered
        // into the padded flight window (box + 0.9 box on each side).
        val parts = com.combo.monkeycompanion.rig.Particles(); val em = com.combo.monkeycompanion.rig.PowerEmitter(parts)
        val fw = (boxW * 2.8f).toInt(); val fh = (boxH + 1.8f * boxW).toInt()
        val fbmp = Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888); val fc = Canvas(fbmp)
        val flight = com.combo.monkeycompanion.brain.PowerFlight()
        var flightFrames = 0; var maxParts = 0
        fun flyFrame(raster: Boolean) {
            if (!flight.active) {
                val pw = com.combo.monkeycompanion.brain.Superpowers.all[flightFrames % 2]
                flight.begin(pw, pw.plan(0.5f, 0f, flightFrames % 3 == 0, kotlin.random.Random(flightFrames)), flightFrames % 3 == 0, 0.5f, 0f)
                ctx.power = pw; renderer.resetCape(); em.reset(); parts.clear()
            }
            flight.update(1f / 60f); flightFrames++; t += 1f / 60f
            ctx.powerPhase = flight.phase; ctx.powerK = flight.k; ctx.speed = 0.8f; ctx.heading = 70f; ctx.crashed = flight.crashed
            Animations.fill(target, MonkeyState.POWER, t, t, ctx)
            current.blendToward(target, 0.2f)
            rig.layout(current, fw / 2f, fh - 0.9f * boxW - 7 * density, rigScale, 1f)
            em.frame(rig, current, flight.power ?: ctx.power!!, flight.phase, flight.index, flight.k, 900f, -300f, 1f, rigScale, 1f / 60f)
            maxParts = maxOf(maxParts, parts.count)
            if (raster) { fbmp.eraseColor(Color.TRANSPARENT); parts.draw(fc); renderer.draw(fc, rig, current, t) }
            else { val c = pic.beginRecording(fw, fh); parts.draw(c); renderer.draw(c, rig, current, t); pic.endRecording() }
        }
        val flyRecord = measure(1800) { flyFrame(false) }
        val flyRaster = measure(600) { flyFrame(true) }

        fun perSec(msPerFrame: Double, fps: Int) = msPerFrame * fps
        val report = buildString {
            appendLine("Settai v3.2 frame cost on the build box (JVM ${System.getProperty("java.version")}, ${Runtime.getRuntime().availableProcessors()} cores)")
            appendLine("overlay window ${boxW}x${boxH}px (136x150 dp @ 2.625x)")
            appendLine("update (brain+anim+rig) idle : %.4f ms/frame".format(updIdle))
            appendLine("update (brain+anim+rig) dance: %.4f ms/frame".format(updDance))
            appendLine("record display list (Picture): %.4f ms/frame".format(record))
            appendLine("software raster idle         : %.3f ms/frame".format(rasterIdle))
            appendLine("software raster dance        : %.3f ms/frame".format(rasterDance))
            appendLine("software raster juggling gag : %.3f ms/frame".format(rasterGag))
            appendLine("allocated per frame (update+draw): %.0f bytes".format(allocPerFrame))
            val hw = updDance + (if (record.isNaN()) 0.0 else record)
            val sw = updDance + rasterGag
            appendLine("CPU ms per second, HW path (update+record): 30fps %.2f | 15fps %.2f | 10fps %.2f | v3.1 (60fps) %.2f".format(perSec(hw, 30), perSec(hw, 15), perSec(hw, 10), perSec(hw, 60)))
            appendLine("CPU ms per second, SW raster upper bound  : 30fps %.2f | 15fps %.2f | 10fps %.2f | v3.1 (60fps) %.2f".format(perSec(sw, 30), perSec(sw, 15), perSec(sw, 10), perSec(sw, 60)))
            appendLine("superpower flight (update+particles+cape+record): %.4f ms/frame -> %.2f ms CPU/s at 60fps; SW raster of the %dx%d flight window %.3f ms/frame (upper bound %.1f ms/s); max particles %d (cap %d)".format(
                flyRecord, perSec(flyRecord, 60), fw, fh, flyRaster, perSec(flyRaster, 60), maxParts, com.combo.monkeycompanion.rig.Particles.CAP))
        }
        println(report)
        File(System.getProperty("preview.out") ?: "build/x.png").parentFile!!.let { it.mkdirs(); File(it, "settai-bench.txt").writeText(report) }
    }
}
