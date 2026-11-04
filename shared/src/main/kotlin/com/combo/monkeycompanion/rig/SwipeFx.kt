package com.combo.monkeycompanion.rig

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.combo.monkeycompanion.engine.swipe.SwipeTravel
import kotlin.math.cos
import kotlin.math.sin

/**
 * v10.24 the props of the swipe trips (procedural): Mask Man's bedsheet, Chittu's mouse holes, the surf curl and spray,
 * landing dust, magic sparkles. Drawn around the character's feet ([cx], [fy]); [u] = px per dp. No RNG.
 */
class SwipeFx {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    private val r = RectF()

    /** Behind the body: holes, the far side of the curl. */
    fun drawBehind(c: Canvas, f: SwipeTravel.Frame, cx: Float, fy: Float, bodyH: Float, u: Float, t: Float) {
        for (h in floatArrayOf(f.holeA, f.holeB)) if (h > 0.01f) {
            val w = 40f * u * h
            fill.color = Color.rgb(0x1A, 0x10, 0x0C); r.set(cx - w, fy - 11f * u * h, cx + w, fy + 11f * u * h); c.drawOval(r, fill)
            line.color = Color.rgb(0x6B, 0x4A, 0x2E); line.strokeWidth = 3f * u; c.drawOval(r, line)
        }
        if (f.curl > 0.01f) {   // the tube: a tall wall of water arching over her from behind
            val a = f.curl
            fill.color = Color.argb((200 * a).toInt(), 0x1A, 0x6C, 0xC4)
            path.reset(); path.moveTo(cx - 70f * u, fy)
            path.cubicTo(cx - 80f * u, fy - bodyH * 1.1f * a, cx + 30f * u, fy - bodyH * 1.35f * a, cx + 60f * u, fy - bodyH * 0.8f * a)
            path.cubicTo(cx + 40f * u, fy - bodyH * 0.95f * a, cx - 30f * u, fy - bodyH * 0.9f * a, cx - 40f * u, fy)
            path.close(); c.drawPath(path, fill)
        }
    }

    /** In front of the body: sheet, curl lip, spray, sparkles, dust. */
    /** v10.25.1 smoke puff colour for MAGIC_HOPS (red = Kutti Satan, white/gold = Angel). */
    var puffRed = true
    fun drawFront(c: Canvas, f: SwipeTravel.Frame, cx: Float, fy: Float, bodyH: Float, u: Float, t: Float, dir: Float) {
        if (f.puff > 0.01f) {
            val a = f.puff; val cy = fy - bodyH * 0.45f
            for (i in 0 until 9) {
                val ang = i * 0.698f + t * 1.5f; val d = (14f + 26f * a) * u
                fill.color = if (puffRed) (if (i % 3 == 0) Color.rgb(0x9E, 0x0B, 0x18) else Color.rgb(0xE5, 0x1C, 0x2A)) else (if (i % 3 == 0) Color.rgb(0xF5, 0xC2, 0x42) else Color.rgb(0xFF, 0xFC, 0xF4))
                fill.alpha = (215 * a).toInt(); c.drawCircle(cx + cos(ang) * d, cy + sin(ang) * d * 0.75f, (16f + 6f * sin(i * 1.9f)) * u * (0.6f + 0.4f * a), fill)
            }
            fill.alpha = 255
            for (i in 0 until 5) { val ang = i * 1.257f + t * 2f; star(c, cx + cos(ang) * 46f * u * a, cy + sin(ang) * 36f * u * a, 5f * u * a, if (puffRed) Color.rgb(0xE5, 0x1C, 0x2A) else Color.rgb(0xF5, 0xC2, 0x42)) }
        }
        if (f.curl > 0.01f) {
            val a = f.curl
            line.color = Color.argb((235 * a).toInt(), 0xF2, 0xFB, 0xFF); line.strokeWidth = 4f * u
            path.reset(); path.moveTo(cx - 70f * u, fy - bodyH * 1.0f * a)
            path.cubicTo(cx - 30f * u, fy - bodyH * 1.4f * a, cx + 40f * u, fy - bodyH * 1.2f * a, cx + 60f * u, fy - bodyH * 0.8f * a)
            c.drawPath(path, line)
            fill.color = Color.argb((230 * a).toInt(), 0xF2, 0xFB, 0xFF)
            for (i in 0 until 6) { val ph = (t * 1.8f + i / 6f) % 1f; c.drawCircle(cx + (60f - 20f * ph) * u, fy - bodyH * (0.8f - 0.5f * ph) * a, (3.5f - 2f * ph) * u, fill) }
        }
        if (f.splash > 0.01f) {   // a fan of spray / dust thrown out behind
            val a = f.splash
            for (i in 0 until 14) {
                val ang = (-150f + i * 9f) * (Math.PI / 180f).toFloat()
                val d = (20f + 50f * a + 8f * sin(i * 2.1f)) * u
                fill.color = if (i % 3 == 0) Color.argb((230 * a).toInt(), 0xFF, 0xFF, 0xFF) else Color.argb((210 * a).toInt(), 0x6C, 0xC8, 0xF2)
                c.drawCircle(cx - dir * cos(ang) * d * -1f, fy + sin(ang) * d * 0.9f, (2.5f + 2.5f * (1f - a)) * u, fill)
            }
        }
        if (f.sheet > 0.01f || (f.sheetFall > 0.01f && f.sheetFall < 1f) || (f.sheetFall >= 1f && !f.atTarget)) {
            val fall = f.sheetFall
            val top = fy - bodyH * 1.05f * f.sheet * (1f - fall)
            val sag = 6f * u * sin(t * 5f)
            fill.color = Color.rgb(0xF4, 0xF1, 0xEA)
            if (fall < 0.97f) {
                path.reset(); path.moveTo(cx - 46f * u, top); path.quadTo(cx, top + 10f * u + sag, cx + 46f * u, top)
                path.lineTo(cx + 50f * u, fy); path.quadTo(cx, fy + 5f * u, cx - 50f * u, fy); path.close()
            } else {   // flat on the floor
                path.reset(); r.set(cx - 56f * u, fy - 6f * u, cx + 56f * u, fy + 4f * u); path.addOval(r, Path.Direction.CW)
            }
            c.drawPath(path, fill)
            line.color = Color.rgb(0xB8, 0xB0, 0xA4); line.strokeWidth = 1.8f * u; c.drawPath(path, line)
            line.color = Color.rgb(0x6A, 0x9B, 0xE0); line.strokeWidth = 2f * u   // blue stripes on the sheet
            if (fall < 0.97f) for (i in -1..1) c.drawLine(cx + i * 22f * u, top + 8f * u, cx + i * 24f * u, fy - 4f * u, line)
        }
        if (f.sparkle > 0.01f) {
            for (i in 0 until 8) {
                val ang = i * 0.785f + t * 2f
                val d = (26f + 30f * f.sparkle) * u
                star(c, cx + cos(ang) * d, fy - bodyH * 0.5f + sin(ang) * d * 0.8f, 5f * u * f.sparkle, if (i % 2 == 0) Color.rgb(0xFF, 0xD2, 0x3F) else Color.WHITE)
            }
        }
    }

    private fun star(c: Canvas, x: Float, y: Float, r: Float, col: Int) {
        fill.color = col
        path.reset(); path.moveTo(x, y - r); path.quadTo(x, y, x + r, y); path.quadTo(x, y, x, y + r); path.quadTo(x, y, x - r, y); path.quadTo(x, y, x, y - r); path.close()
        c.drawPath(path, fill)
    }
}
