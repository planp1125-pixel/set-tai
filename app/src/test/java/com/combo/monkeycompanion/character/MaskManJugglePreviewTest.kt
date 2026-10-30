package com.combo.monkeycompanion.character

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.TestPaths
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.landing.LandingE2ETest
import com.combo.monkeycompanion.overlay.MonkeyView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v10.4: what the phone shows (overlay window + full-screen FX), written to build/test-out/settai-v10.4-juggle.png. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class MaskManJugglePreviewTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private var clock = 10_000L

    private fun screen(v: MonkeyView, host: LandingE2ETest.Host): Bitmap {
        val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.rgb(0xEE, 0xF1, 0xF6))
        host.fx?.invoke(c)
        c.save(); c.translate(host.x.toFloat(), host.y.toFloat()); c.clipRect(0, 0, host.w, host.h); v.draw(c); c.restore()
        return bmp
    }

    private fun move(v: MonkeyView, a: Int, x: Float, y: Float) {
        val w = v.debugWin()
        val e = MotionEvent.obtain(clock, clock, a, x, y, 0); e.offsetLocation(-w[0].toFloat(), -w[1].toFloat())
        v.dispatchTouchEvent(e); e.recycle()
    }

    @Test fun renderJuggle() {
        ChatHub.init(ctx)
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.batterySaver = false; ChatHub.settings.gags = false; ChatHub.settings.roam = false
        CharacterRegistry.select("mask")
        val host = LandingE2ETest.Host()
        val v = MonkeyView(ctx, host)
        v.layout(0, 0, 1080, 2340)
        v.debugFrames(60); v.debugSurf("FLOOR", frac = 0.45f); v.debugFrames(40); v.debugQuiet = true
        val shots = ArrayList<Pair<String, Bitmap>>()
        val cx0 = host.x + host.w / 2
        shots += "Juggling (idle)" to screen(v, host)
        v.debugFrames(9); shots += "...still juggling" to screen(v, host)
        // Drag him around: wobble + a ball slips
        val b = v.debugBox(); var x = b.centerX(); var y = b.centerY()
        move(v, MotionEvent.ACTION_DOWN, x, y)
        y -= 400f
        for (i in 1..40) { clock += 16; x += if (i % 6 < 3) 70f else -70f; move(v, MotionEvent.ACTION_MOVE, x, y - i * 4f); v.debugFrames(1) }
        shots += "Dragged around: wobbly, balls slipping" to screen(v, host)
        clock += 300; move(v, MotionEvent.ACTION_UP, x, y - 160f)
        repeat(60 * 14) { v.debugFrames(1) }
        v.debugSurf("FLOOR", frac = 0.3f); repeat(60 * 3) { v.debugFrames(1) }
        // Fast fling to the right
        val b2 = v.debugBox(); x = b2.centerX(); y = b2.centerY()
        move(v, MotionEvent.ACTION_DOWN, x, y)
        for (i in 1..3) { clock += 16; move(v, MotionEvent.ACTION_MOVE, x + 160f * i, y - 30f * i); v.debugFrames(1) }
        clock += 5; move(v, MotionEvent.ACTION_UP, x + 480f, y - 90f)
        val bb = v.debugBalls
        println("FLING balls: " + (0 until 3).joinToString { "${bb.state[it]} x=${bb.x[it]} y=${bb.y[it]} vx=${bb.vx[it]} vy=${bb.vy[it]}" })
        v.debugFrames(8); shots += "Fast fling: balls fly off" to screen(v, host)
        println("FLING+14: " + (0 until 3).joinToString { "${bb.state[it]} x=${bb.x[it]} y=${bb.y[it]}" })
        v.debugFrames(22); shots += "...bouncing off the screen edges" to screen(v, host)
        v.debugFrames(90); shots += "...rolling on the floor" to screen(v, host)

        val sw = 360; val sh = 780
        val out = Bitmap.createBitmap(sw * shots.size, sh + 60, Bitmap.Config.ARGB_8888)
        val oc = Canvas(out); oc.drawColor(Color.WHITE)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Color.rgb(0x30, 0x30, 0x40) }
        shots.forEachIndexed { i, (name, bmp) ->
            oc.drawBitmap(Bitmap.createScaledBitmap(bmp, sw - 8, sh, true), i * sw + 4f, 0f, null)
            oc.drawText(name, i * sw + sw / 2f, sh + 38f, label)
        }
        TestPaths.out("settai-v10.4-juggle.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Close-up of the juggling
        val crop = Bitmap.createBitmap(shots[0].second, (cx0 - 300).coerceIn(0, 1080 - 600), 1500, 600, 840)
        TestPaths.out("settai-v10.4-juggle-closeup.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
