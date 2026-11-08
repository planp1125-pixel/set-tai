package com.combo.monkeycompanion.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.combo.monkeycompanion.TestPaths
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.character.Wear
import com.combo.monkeycompanion.engine.anim.AnimEngine
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v10.7: before/after sheet of the Disney part 1 engine -> build/test-out/settai-v10.7-preview.png (real renderers). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class AnimPreviewTest {
    private val cw = 300; private val ch = 330

    private fun draw(c: Canvas, id: String, col: Int, row: Int, smooth: Boolean, landing: Boolean) {
        val ch0 = CharacterRegistry.byId(id)!!
        val r = ch0.newRenderer().also { it.wear = Wear(ch0) }
        val rig = MonkeyRig(); val p = Pose()
        val e = AnimEngine(); e.setTuning(CharacterProfiles.of(ch0).anim)
        val dt = 1f / 60f
        if (landing) {
            // fall fast, then 3 frames after touch-down (authored land pose: 0.78 x 1.20)
            var y = 0f
            repeat(25) { ch0.fill(p, MonkeyState.FALLING, it * dt, it * dt, AnimContext(), Wear(ch0)); y += 25f; if (smooth) e.step(p, dt, 0f, y, 1f, 1f, true) }
            repeat(3) { ch0.fill(p, MonkeyState.IDLE, 1f, 0.05f, AnimContext(), Wear(ch0)); p.squashY = 0.78f; p.squashX = 1.20f; if (smooth) e.step(p, dt, 0f, y, 1f, 1f, false) }
        } else {
            // run right ~720 dp/s for 0.6 s, then stop dead; frame 7 after the stop
            var x = 0f
            repeat(36) { ch0.fill(p, MonkeyState.WALK, it * dt, it * dt, AnimContext(), Wear(ch0)); x += 12f; if (smooth) e.step(p, dt, x, 0f, 1f, 1f, false) }
            repeat(7) { ch0.fill(p, MonkeyState.IDLE, 1f, 0.1f, AnimContext(), Wear(ch0)); if (smooth) e.step(p, dt, x, 0f, 1f, 1f, false) }
        }
        if (id == "superboy") { p.cape = 1f }
        r.anim = if (smooth) e else null
        rig.squashPivot = if (smooth) e.squashPivot else 0f
        val sc = 1.1f * CharacterProfiles.of(ch0).drawScale
        c.save(); c.clipRect(col * cw.toFloat(), row * ch.toFloat(), (col + 1) * cw.toFloat(), (row + 1) * ch.toFloat())
        c.drawColor(if (smooth) Color.rgb(0xEE, 0xF8, 0xEE) else Color.rgb(0xF4, 0xF0, 0xEA))
        val fx = col * cw + cw / 2f; val fy = row * ch + ch - 40f
        val ln = Paint().apply { color = Color.rgb(0xB0, 0xA8, 0x9A); strokeWidth = 2f }
        c.drawLine(col * cw + 10f, fy, (col + 1) * cw - 10f, fy, ln)
        rig.layout(p, fx, fy, sc, 1f)
        r.draw(c, rig, p, 1f)
        c.restore()
    }

    @Test fun renderBeforeAfter() {
        val ids = listOf("monkey" to "Settai", "billa" to "Billa (cat)", "superboy" to "Zoom", "gajju" to "Gajju")
        val top = 70; val left = 0
        val out = Bitmap.createBitmap(cw * 4, top + ch * ids.size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out); c.drawColor(Color.WHITE)
        val lbl = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Color.rgb(0x30, 0x30, 0x40) }
        val heads = listOf("Landing: classic (v10.6)", "Landing: smooth (v10.7)", "Run then stop: classic", "Run then stop: smooth")
        heads.forEachIndexed { i, h -> c.drawText(h, i * cw + cw / 2f, 30f, lbl) }
        val sub = Paint(lbl).apply { textSize = 16f; typeface = Typeface.DEFAULT; color = Color.rgb(0x60, 0x60, 0x70) }
        c.drawText("squash keeps volume (wider when flatter)", cw * 1.5f, 55f, sub)
        c.drawText("tail / ears / cape / trunk lag and swing", cw * 3.5f, 55f, sub)
        c.save(); c.translate(left.toFloat(), top.toFloat())
        ids.forEachIndexed { row, (id, name) ->
            draw(c, id, 0, row, false, true); draw(c, id, 1, row, true, true)
            draw(c, id, 2, row, false, false); draw(c, id, 3, row, true, false)
            c.drawText(name, 60f, row * ch + 26f, Paint(lbl).apply { textAlign = Paint.Align.LEFT; textSize = 20f })
        }
        c.restore()
        TestPaths.out("settai-v10.7-preview.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
