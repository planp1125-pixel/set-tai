package com.combo.monkeycompanion.fun5

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.combo.monkeycompanion.v5.Festivals
import com.combo.monkeycompanion.v5.WeatherMood
import kotlin.math.cos
import kotlin.math.sin

/** v5 drawn props, all in screen pixels around the character ([s] = body scale, 1 rig unit). */
object Props5 {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val r = RectF()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }

    private fun fill(c: Int) { p.style = Paint.Style.FILL; p.color = c; p.shader = null }
    private fun stroke(c: Int, w: Float) { p.style = Paint.Style.STROKE; p.color = c; p.strokeWidth = w; p.strokeCap = Paint.Cap.ROUND; p.shader = null }

    /** Festival prop held up next to the head ([hx], [hy] = head top) or at the feet. */
    fun festival(c: Canvas, prop: Festivals.Prop, hx: Float, hy: Float, fx: Float, fy: Float, s: Float, t: Float) {
        val x = hx + 46f * s; val y = hy + 30f * s
        when (prop) {
            Festivals.Prop.DIYA -> {
                fill(Color.rgb(0xC8, 0x6B, 0x2A)); r.set(x - 16 * s, y, x + 16 * s, y + 14 * s); c.drawArc(r, 0f, 180f, true, p)
                val fl = 1f + 0.15f * sin(t * 18f)
                fill(Color.argb(120, 0xFF, 0xC8, 0x3A)); c.drawCircle(x, y - 6 * s, 12 * s * fl, p)
                fill(Color.rgb(0xFF, 0x9A, 0x1F)); path.reset(); path.moveTo(x, y - 16 * s * fl); path.quadTo(x + 7 * s, y - 2 * s, x, y + 2 * s); path.quadTo(x - 7 * s, y - 2 * s, x, y - 16 * s * fl); c.drawPath(path, p)
                // a second diya at the feet
                fill(Color.rgb(0xC8, 0x6B, 0x2A)); r.set(fx - 60 * s, fy - 10 * s, fx - 36 * s, fy); c.drawArc(r, 0f, 180f, true, p)
                fill(Color.rgb(0xFF, 0xB3, 0x2E)); c.drawCircle(fx - 48 * s, fy - 8 * s, 4 * s * fl, p)
            }
            Festivals.Prop.PONGAL_POT -> {
                val px = fx + 56 * s; val py = fy
                fill(Color.rgb(0xB5, 0x5A, 0x2A)); c.drawCircle(px, py - 18 * s, 18 * s, p)
                fill(Color.rgb(0xF5, 0xE6, 0xC8)); r.set(px - 12 * s, py - 42 * s, px + 12 * s, py - 30 * s); c.drawOval(r, p)
                for (i in 0..2) { val k = ((t * 0.8f + i / 3f) % 1f); fill(Color.argb((200 * (1 - k)).toInt(), 0xFF, 0xF8, 0xE8)); c.drawCircle(px - 8 * s + i * 8 * s, py - 40 * s - k * 30 * s, (4 + 3 * k) * s, p) }
                stroke(Color.rgb(0x2E, 0x8B, 0x57), 3 * s); c.drawLine(px + 26 * s, py, px + 30 * s, py - 70 * s, p)   // sugarcane
            }
            Festivals.Prop.FLAG -> {
                stroke(Color.rgb(0x6B, 0x4A, 0x2A), 2.5f * s); c.drawLine(x, y + 30 * s, x, y - 40 * s, p)
                val w = 3f * s * sin(t * 6f)
                val cols = intArrayOf(Color.rgb(0xFF, 0x99, 0x33), Color.WHITE, Color.rgb(0x13, 0x88, 0x08))
                for (i in 0..2) { fill(cols[i]); path.reset(); val y0 = y - 40 * s + i * 8 * s; path.moveTo(x, y0); path.quadTo(x + 18 * s, y0 + w, x + 36 * s, y0); path.lineTo(x + 36 * s, y0 + 8 * s); path.quadTo(x + 18 * s, y0 + 8 * s + w, x, y0 + 8 * s); path.close(); c.drawPath(path, p) }
                stroke(Color.rgb(0x00, 0x00, 0x80), 1.2f * s); c.drawCircle(x + 18 * s, y - 28 * s + w * 0.5f, 3 * s, p)
            }
            Festivals.Prop.CRESCENT -> {
                fill(Color.rgb(0xFF, 0xD8, 0x5A)); c.drawCircle(x, y - 20 * s, 16 * s, p)
                fill(Color.argb(255, 0x1B, 0x2A, 0x4A)); c.drawCircle(x + 7 * s, y - 24 * s, 14 * s, p)
                fill(Color.rgb(0xFF, 0xF0, 0xA0)); star(c, x + 22 * s, y - 34 * s, 5 * s)
            }
            Festivals.Prop.KOLAM -> {
                stroke(Color.WHITE, 2f * s)
                for (i in 0 until 8) { val a = i / 8f * 6.283f; c.drawCircle(fx + cos(a) * 26 * s, fy + 2 * s + sin(a) * 6 * s, 5 * s, p) }
                stroke(Color.rgb(0xE9, 0x4F, 0x8A), 2f * s); r.set(fx - 40 * s, fy - 6 * s, fx + 40 * s, fy + 10 * s); c.drawOval(r, p)
                fill(Color.rgb(0xFF, 0xC1, 0x07)); c.drawCircle(hx + 16 * s, hy + 6 * s, 5 * s, p)   // flower in the hair
            }
            Festivals.Prop.MODAK -> {
                fill(Color.rgb(0xFF, 0xF3, 0xD6)); path.reset(); path.moveTo(x, y - 22 * s); path.quadTo(x + 16 * s, y, x + 12 * s, y + 8 * s); path.lineTo(x - 12 * s, y + 8 * s); path.quadTo(x - 16 * s, y, x, y - 22 * s); c.drawPath(path, p)
                stroke(Color.rgb(0xD9, 0xB8, 0x7A), 1.5f * s); for (i in -1..1) c.drawLine(x, y - 20 * s, x + i * 9 * s, y + 6 * s, p)
            }
            Festivals.Prop.GARLAND -> garland(c, hx, hy + 70 * s, s)
            Festivals.Prop.STAR -> { fill(Color.rgb(0xFF, 0xD5, 0x4A)); star(c, x, y - 20 * s, 16 * s * (1f + 0.08f * sin(t * 5f))) }
            Festivals.Prop.PARTY -> {
                fill(Color.rgb(0xE9, 0x4F, 0x8A)); path.reset(); path.moveTo(hx - 14 * s, hy + 4 * s); path.lineTo(hx + 14 * s, hy + 4 * s); path.lineTo(hx, hy - 30 * s); path.close(); c.drawPath(path, p)
                fill(Color.rgb(0xFF, 0xD5, 0x4A)); c.drawCircle(hx, hy - 30 * s, 4 * s, p)
            }
        }
    }

    fun garland(c: Canvas, cx: Float, cy: Float, s: Float) {
        val cols = intArrayOf(Color.rgb(0xFF, 0x9A, 0x1F), Color.rgb(0xFF, 0xD5, 0x4A), Color.WHITE)
        for (i in 0..10) { val a = 3.4f - i * 0.25f; fill(cols[i % 3]); c.drawCircle(cx + cos(a) * 26 * s, cy - sin(a) * -14 * s - 10 * s, 5 * s, p) }
    }

    fun star(c: Canvas, x: Float, y: Float, rad: Float) {
        path.reset()
        for (i in 0 until 10) { val a = -Math.PI / 2 + i * Math.PI / 5; val rr = if (i % 2 == 0) rad else rad * 0.45f
            val px = x + (cos(a) * rr).toFloat(); val py = y + (sin(a) * rr).toFloat(); if (i == 0) path.moveTo(px, py) else path.lineTo(px, py) }
        path.close(); c.drawPath(path, p)
    }

    /** Weather prop: umbrella over the head, fan + tender coconut, shawl, cloud. */
    fun weather(c: Canvas, m: WeatherMood.Mood, hx: Float, hy: Float, fx: Float, fy: Float, s: Float, t: Float) {
        when (m) {
            WeatherMood.Mood.RAIN, WeatherMood.Mood.STORM -> {
                stroke(Color.rgb(0x4A, 0x3A, 0x2A), 2.5f * s); c.drawLine(hx + 10 * s, hy - 40 * s, hx + 10 * s, hy + 40 * s, p)
                fill(if (m == WeatherMood.Mood.STORM) Color.rgb(0x5A, 0x4A, 0x9A) else Color.rgb(0xD9, 0x3F, 0x3F))
                r.set(hx - 40 * s, hy - 62 * s, hx + 60 * s, hy - 18 * s); c.drawArc(r, 180f, 180f, true, p)
                stroke(Color.argb(160, 0x7F, 0xB8, 0xE8), 1.6f * s)
                for (i in 0 until 9) { val k = ((t * 1.6f + i * 0.37f) % 1f); val x = hx - 90 * s + i * 22 * s; val y = hy - 120 * s + k * 200 * s
                    if (x < hx - 44 * s || x > hx + 64 * s || y > hy - 18 * s) c.drawLine(x, y, x - 3 * s, y + 10 * s, p) }
            }
            WeatherMood.Mood.HOT -> {
                val a = 25f * sin(t * 10f)
                c.save(); c.rotate(a, hx + 40 * s, hy + 40 * s)
                fill(Color.rgb(0xE8, 0xC2, 0x6A)); r.set(hx + 28 * s, hy - 4 * s, hx + 62 * s, hy + 30 * s); c.drawArc(r, 200f, 140f, true, p)
                stroke(Color.rgb(0x8A, 0x6A, 0x2A), 2 * s); c.drawLine(hx + 45 * s, hy + 13 * s, hx + 40 * s, hy + 40 * s, p)
                c.restore()
                fill(Color.rgb(0x5B, 0x9A, 0x3A)); c.drawCircle(fx + 50 * s, fy - 16 * s, 16 * s, p)            // tender coconut
                stroke(Color.rgb(0xF2, 0x7A, 0x1E), 2 * s); c.drawLine(fx + 50 * s, fy - 30 * s, fx + 40 * s, fy - 54 * s, p)
            }
            WeatherMood.Mood.COLD -> {
                fill(Color.rgb(0xB0, 0x3A, 0x4E)); r.set(hx - 36 * s, hy + 52 * s, hx + 36 * s, hy + 80 * s); c.drawRoundRect(r, 12 * s, 12 * s, p)
                fill(Color.rgb(0xF2, 0xC1, 0x4E)); for (i in 0..4) c.drawCircle(hx - 28 * s + i * 14 * s, hy + 66 * s, 2.5f * s, p)
                stroke(Color.argb(180, 0xC8, 0xE6, 0xFF), 1.5f * s); val sh = 3 * s * sin(t * 40f)
                c.drawLine(hx - 50 * s + sh, hy + 20 * s, hx - 58 * s + sh, hy + 26 * s, p); c.drawLine(hx + 52 * s - sh, hy + 20 * s, hx + 60 * s - sh, hy + 26 * s, p)
            }
            WeatherMood.Mood.CLOUDY, WeatherMood.Mood.FOG -> {
                val dx = 10 * s * sin(t * 0.8f)
                fill(Color.argb(220, 0xE8, 0xEC, 0xF2)); for ((ox, oy, rr) in listOf(Triple(-20f, 0f, 16f), Triple(0f, -8f, 20f), Triple(22f, 0f, 15f))) c.drawCircle(hx + dx + ox * s, hy - 54 * s + oy * s, rr * s, p)
            }
            WeatherMood.Mood.CLEAR -> {
                fill(Color.rgb(0x15, 0x15, 0x1C)); r.set(hx - 22 * s, hy + 22 * s, hx - 2 * s, hy + 34 * s); c.drawRoundRect(r, 4 * s, 4 * s, p)
                r.set(hx + 2 * s, hy + 22 * s, hx + 22 * s, hy + 34 * s); c.drawRoundRect(r, 4 * s, 4 * s, p)
            }
            WeatherMood.Mood.NONE -> Unit
        }
    }

    /** Davara-tumbler filter coffee with steam. */
    fun coffee(c: Canvas, hx: Float, hy: Float, s: Float, t: Float) {
        val x = hx + 40 * s; val y = hy + 50 * s
        fill(Color.rgb(0xC0, 0xC4, 0xCC)); r.set(x - 16 * s, y + 10 * s, x + 16 * s, y + 20 * s); c.drawRoundRect(r, 4 * s, 4 * s, p)   // davara
        fill(Color.rgb(0xD8, 0xDC, 0xE2)); path.reset(); path.moveTo(x - 9 * s, y - 14 * s); path.lineTo(x + 9 * s, y - 14 * s); path.lineTo(x + 7 * s, y + 12 * s); path.lineTo(x - 7 * s, y + 12 * s); path.close(); c.drawPath(path, p)
        fill(Color.rgb(0xC8, 0x9A, 0x6A)); r.set(x - 9 * s, y - 16 * s, x + 9 * s, y - 11 * s); c.drawOval(r, p)
        stroke(Color.argb(170, 255, 255, 255), 2 * s)
        for (i in 0..2) { val k = (t * 0.7f + i / 3f) % 1f; path.reset(); val sx = x - 5 * s + i * 5 * s; path.moveTo(sx, y - 18 * s - k * 30 * s); path.quadTo(sx + 6 * s * sin(t * 3 + i), y - 26 * s - k * 30 * s, sx, y - 34 * s - k * 30 * s); p.alpha = (170 * (1 - k)).toInt(); c.drawPath(path, p) }
    }

    /** Sleep mask over the eyes (face-down). */
    fun sleepMask(c: Canvas, hx: Float, hy: Float, s: Float) {
        fill(Color.rgb(0x6B, 0x3F, 0xA0)); r.set(hx - 30 * s, hy + 18 * s, hx + 30 * s, hy + 38 * s); c.drawRoundRect(r, 10 * s, 10 * s, p)
        stroke(Color.rgb(0xF2, 0xD4, 0x6A), 2 * s); c.drawArc(RectF(hx - 22 * s, hy + 22 * s, hx - 6 * s, hy + 34 * s), 20f, 140f, false, p); c.drawArc(RectF(hx + 6 * s, hy + 22 * s, hx + 22 * s, hy + 34 * s), 20f, 140f, false, p)
    }

    /** Dizzy stars orbiting the head. */
    fun dizzy(c: Canvas, hx: Float, hy: Float, s: Float, t: Float) {
        fill(Color.rgb(0xFF, 0xD5, 0x4A))
        for (i in 0 until 4) { val a = t * 5f + i * 1.57f; star(c, hx + cos(a) * 34 * s, hy + 4 * s + sin(a) * 9 * s, 6 * s) }
    }

    /** Crown (friendship unlock). */
    fun crown(c: Canvas, hx: Float, hy: Float, s: Float) {
        fill(Color.rgb(0xF2, 0xB8, 0x2E)); path.reset(); path.moveTo(hx - 16 * s, hy + 2 * s); path.lineTo(hx - 16 * s, hy - 14 * s); path.lineTo(hx - 8 * s, hy - 6 * s)
        path.lineTo(hx, hy - 18 * s); path.lineTo(hx + 8 * s, hy - 6 * s); path.lineTo(hx + 16 * s, hy - 14 * s); path.lineTo(hx + 16 * s, hy + 2 * s); path.close(); c.drawPath(path, p)
        fill(Color.rgb(0xD9, 0x3F, 0x3F)); c.drawCircle(hx, hy - 4 * s, 2.5f * s, p)
    }

    /** The fake notification banner being eaten: [left] = 0..1 of the card remaining (bites from the right). */
    fun banner(c: Canvas, x: Float, y: Float, w: Float, h: Float, title: String, left: Float, d: Float) {
        if (left <= 0f) return
        c.save(); c.clipRect(x, y - 2, x + w * left, y + h + 2)
        fill(Color.argb(240, 0xFF, 0xFF, 0xFF)); r.set(x, y, x + w, y + h); c.drawRoundRect(r, 14 * d, 14 * d, p)
        stroke(Color.argb(60, 0, 0, 0), 1 * d); c.drawRoundRect(r, 14 * d, 14 * d, p)
        fill(Color.rgb(0x25, 0xD3, 0x66)); c.drawCircle(x + 18 * d, y + h / 2, 9 * d, p)
        text.color = Color.rgb(0x22, 0x22, 0x22); text.textSize = 12 * d
        c.drawText(title.take(30), x + 34 * d, y + h / 2 + 4 * d, text)
        c.restore()
        if (left < 1f) { // bite marks
            val bx = x + w * left
            stroke(Color.argb(90, 0, 0, 0), 1 * d); for (i in 0..2) c.drawArc(RectF(bx - 6 * d, y + i * h / 3, bx + 6 * d, y + (i + 1) * h / 3), 90f, 180f, false, p)
        }
    }

    /** Game HUD text at the top of the screen. */
    fun hud(c: Canvas, w: Float, top: Float, line: String, d: Float) {
        text.textSize = 16 * d; text.color = Color.WHITE
        val tw = text.measureText(line)
        fill(Color.argb(170, 0x3A, 0x26, 0x18)); r.set(w / 2 - tw / 2 - 14 * d, top, w / 2 + tw / 2 + 14 * d, top + 32 * d); c.drawRoundRect(r, 16 * d, 16 * d, p)
        c.drawText(line, w / 2 - tw / 2, top + 22 * d, text)
    }

    /** A banana (or a rotten one) in screen px. */
    fun banana(c: Canvas, x: Float, y: Float, d: Float, rotten: Boolean, spin: Float) {
        c.save(); c.rotate(spin, x, y)
        stroke(if (rotten) Color.rgb(0x5A, 0x4A, 0x2A) else Color.rgb(0xFF, 0xD3, 0x2E), 8 * d)
        r.set(x - 14 * d, y - 14 * d, x + 14 * d, y + 14 * d); c.drawArc(r, 20f, 140f, false, p)
        if (rotten) { fill(Color.rgb(0x3A, 0x2A, 0x1A)); c.drawCircle(x - 4 * d, y + 12 * d, 2 * d, p); c.drawCircle(x + 6 * d, y + 11 * d, 2 * d, p) }
        c.restore()
    }

    /** Staring-contest stare lines from the eyes, and tears when [water] > 0. */
    fun stare(c: Canvas, hx: Float, hy: Float, s: Float, water: Float, t: Float) {
        stroke(Color.argb(150, 0xFF, 0x40, 0x40), 1.5f * s)
        for (i in -1..1 step 2) c.drawLine(hx + i * 10 * s, hy + 28 * s, hx + i * 26 * s, hy + 10 * s + 3 * s * sin(t * 30f), p)
        if (water > 0f) { fill(Color.argb((200 * water).toInt(), 0x7F, 0xC8, 0xFF)); for (i in -1..1 step 2) c.drawCircle(hx + i * 14 * s, hy + 38 * s + water * 10 * s, 3 * s * water + 1, p) }
    }
}
