package com.combo.monkeycompanion.character

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.combo.monkeycompanion.TestPaths
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.PeekFace
import com.combo.monkeycompanion.rig.Pose
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v10.1: Gajju the flying elephant, drawn in its main poses (writes build/test-out/settai-v10.1-gajju.png). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class GajjuPreviewTest {
    private class Shot(val label: String, val state: MonkeyState, val st: Float, val airborne: Boolean = false, val wear: String? = null, val spray: Boolean = false)

    @Test fun renderGajju() {
        val shots = listOf(
            Shot("Hello! (floating idle)", MonkeyState.IDLE, 1.2f),
            Shot("Flying: wings flapping fast", MonkeyState.IDLE, 0.35f, airborne = true),
            Shot("Mid-air dance", MonkeyState.DANCE, 1.1f, airborne = true),
            Shot("Dance, another move", MonkeyState.DANCE, 3.4f, airborne = true),
            Shot("Head pat: happy (heart!)", MonkeyState.HAPPY_PAT, 0.8f),
            Shot("Tummy tickle: giggles", MonkeyState.GIGGLE, 0.6f),
            Shot("Sleepy float", MonkeyState.SLEEP, 2f),
            Shot("Wardrobe: tiny crown", MonkeyState.WAVE, 0.7f, wear = "crown;"),
            Shot("Wardrobe: pink bow", MonkeyState.TALKING, 0.5f, wear = "bow;"),
            Shot("Belly poke: trunk up, SPLASH", MonkeyState.STARTLED, 0.4f, spray = true),
        )
        val reg = CharacterRegistry
        val cols = 4; val cw = 520; val ch = 640
        val rows = (shots.size + cols - 1) / cols
        val bmp = Bitmap.createBitmap(cols * cw, rows * ch + 260, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(0xF3, 0xF7, 0xFF))
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x2C, 0x4A, 0x72); textSize = 27f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xE3, 0xEE, 0xFF) }
        val rig = MonkeyRig(); val ctx = AnimContext(); val pose = Pose()
        // Same proportions as on the phone: rig scale x drawScale, inside the 96 x 118 dp overlay box (145.5 x 178.8 rig units).
        val unit = 2.6f
        val scale = unit * com.combo.monkeycompanion.engine.CharacterProfiles.GAJJU.drawScale
        val boxW = 145.5f * unit; val boxH = 178.8f * unit
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.rgb(0xF0, 0x80, 0x80); pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 10f), 0f) }
        val wear = reg.wear(Gajju)
        shots.forEachIndexed { i, s ->
            val x0 = (i % cols) * cw.toFloat(); val y0 = (i / cols) * ch.toFloat()
            c.drawRoundRect(RectF(x0 + 10, y0 + 10, x0 + cw - 10, y0 + ch - 10), 36f, 36f, card)
            wear.reset(); if (s.wear != null) { wear.allOff(); wear.decode(s.wear) }
            val r = Gajju.newRenderer().also { it.wear = wear; if (s.spray) it.spray = 1f }
            val t = 1f + s.st
            Gajju.fill(pose, s.state, t, s.st, ctx, wear)
            if (s.airborne) pose.shadow = 0f
            val groundY = y0 + ch - 80f
            rig.layout(pose, x0 + cw / 2f, groundY, scale, 1f)
            r.draw(c, rig, pose, t)
            c.drawRect(x0 + cw / 2f - boxW / 2f, groundY + 7f * unit - boxH, x0 + cw / 2f + boxW / 2f, groundY + 7f * unit, boxPaint)
            c.drawText(s.label, x0 + cw / 2f, y0 + ch - 24f, label)
        }
        // The edge-tab peek face, big.
        val ps = PeekFace.State().apply { tw = 90f; cy = 130f; t = 0.4f; density = 3f }
        c.save(); c.translate(40f, rows * ch.toFloat())
        Gajju.peek.draw(c, wear.also { it.reset() }, ps)
        c.restore()
        c.drawText("Peeking out of the edge tab", 520f, rows * ch + 140f, label)
        TestPaths.out("settai-v10.1-gajju.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(bmp.getPixel(cw / 2, ch - 200) != Color.rgb(0xE3, 0xEE, 0xFF))
    }
}
