package com.combo.monkeycompanion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.character.Wear
import com.combo.monkeycompanion.rig.PeekFace

/** Debug-only v4.5 preview: each character's own edge-tab peek (rest, peek, binoculars, wardrobe). */
object PreviewV45 {
    private val brown = Color.rgb(0x5A, 0x3A, 0x1E)

    fun render(): Bitmap {
        val chars = CharacterRegistry.all
        val cw = 330; val ch = 420; val top = 120; val rows = 4
        val bmp = Bitmap.createBitmap(cw * chars.size + 40, top + rows * (ch + 20) + 30, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.rgb(0xFF, 0xF3, 0xE0))
        val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brown; textSize = 40f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        c.drawText("Settai v4.5 · every character peeks out of the edge as itself", bmp.width / 2f, 64f, head)
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brown; textSize = 26f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x8A, 0x6A, 0x4A); textSize = 20f; textAlign = Paint.Align.CENTER }
        val rowNames = listOf("Resting in the tab", "Auto-rest peek", "Binocular peek", "With wardrobe")
        chars.forEachIndexed { col, chr ->
            for (row in 0 until rows) {
                val x0 = 20f + col * cw; val y0 = top + row * (ch + 20f)
                val wear = Wear(chr)
                val t = when (row) { 0 -> -1f; 1 -> if (chr.id == "armour") 2f else 0.7f; 2 -> 0.5f; else -> 1.4f }
                if (row == 3) when (chr.id) {
                    "monkey" -> { wear.set("shades", true); wear.set("hat", true); wear.setOption("hat", "party") }
                    "superboy", "armour" -> wear.set("scarf", true)
                    "mask" -> { wear.set("hat", true); wear.set("crow", true) }
                }
                card(c, RectF(x0 + 8, y0, x0 + cw - 8, y0 + ch - 60), chr.peek, wear, t, peekOut = if (row == 0) 0f else 1f, bino = if (row == 2) 1f else 0f)
                c.drawText(chr.name, x0 + cw / 2f, y0 + ch - 30f, title)
                c.drawText(rowNames[row] + when {
                    row == 3 && chr.id == "monkey" -> ": party hat + shades"
                    row == 3 && chr.id == "mask" -> ": hat + crow"
                    row == 3 -> ": scarf"
                    row == 1 && chr.id == "armour" -> ": visor up"
                    row == 1 && chr.id == "mask" -> ": eyes change mood"
                    row == 1 && chr.id == "superboy" -> ": cape flapping"
                    else -> ""
                }, x0 + cw / 2f, y0 + ch - 6f, sub)
            }
        }
        return bmp
    }

    /** A strip of phone screen with the tab on its left edge, drawn like MonkeyView.drawTab at 2.6x. */
    private fun card(c: Canvas, r: RectF, face: PeekFace, wear: Wear, t: Float, peekOut: Float, bino: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.rgb(0xE6, 0xEE, 0xF8); c.drawRoundRect(r, 28f, 28f, p)
        p.color = Color.rgb(0xC9, 0xD6, 0xE6)
        for (i in 0 until 5) c.drawRoundRect(RectF(r.left + 110f, r.top + 40f + i * 62f, r.right - 24f, r.top + 70f + i * 62f), 10f, 10f, p)
        c.save(); c.clipRect(r)
        val d = 2.6f; val tw = 24f * d; val tl = 66f * d * 1.6f
        c.translate(r.left, r.top + (r.height() - tl) / 2f)
        p.color = Color.argb(150, 0x3A, 0x26, 0x18)
        c.drawRoundRect(RectF(-tw, 0f, tw * 0.95f, tl), tw * 0.6f, tw * 0.6f, p)
        c.translate(peekOut * tw * 0.95f, 0f)
        val s = PeekFace.State().apply { this.tw = tw; cy = tl * 0.36f; this.t = t; density = d; this.bino = bino
            look = if (t >= 0f) 0.05f * tw else 0.02f * tw }
        face.draw(c, wear, s)
        c.restore()
    }
}
