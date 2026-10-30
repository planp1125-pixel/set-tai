package com.combo.monkeycompanion.rig

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.combo.monkeycompanion.brain.PowerPhase
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * v4 full-screen show-off effects, drawn procedurally in screen pixels (no per-frame state, no
 * allocations after warm-up): lightning bolts, the fake screen crack, frosty edges with snow,
 * the giant peeking eye, and the slow-motion banana volley. Only drawn while a power runs.
 */
class ScreenFx {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val path = Path()
    private val r = RectF()
    private val banana = Path()

    /** Where the Kovam crack starts (screen px); set when SLAM lands. */
    var crackX = 0f; var crackY = 0f
    private var crackSeed = 1

    /** True when [phase] wants the screen layer. */
    fun wants(phase: PowerPhase?) = phase != null && phase.screenFx

    /**
     * [w],[h] screen size; [cx],[cy] the monkey's feet; [hx],[hy] his raised hand (bolt start);
     * [s] density; [crashed] the comedy variant; [count] for the magician sign (unused here).
     */
    fun draw(c: Canvas, w: Float, h: Float, phase: PowerPhase?, k: Float, t: Float, crashed: Boolean,
             cx: Float, cy: Float, hx: Float, hy: Float, s: Float,
             ex: Float = hx, ey: Float = hy, facing: Int = 1, count: Int = -1) {
        when (phase) {
            PowerPhase.LASER -> laser(c, w, h, k, t, ex, ey, facing, s)
            PowerPhase.BREATH -> breath(c, w, h, k, t, ex, ey, facing, s)
            PowerPhase.LIFT -> liftCard(c, w, k, cx, hy, count, s)
            PowerPhase.BLUR -> blur(c, w, k, t, cx, cy, s)
            PowerPhase.CHARGE -> { fill.color = Color.rgb(0x10, 0x18, 0x40); fill.alpha = (70 * k).toInt(); c.drawRect(0f, 0f, w, h, fill) }
            PowerPhase.STRIKE -> strike(c, w, h, k, t, crashed, hx, hy, s)
            PowerPhase.SLAM -> { if (k >= 0.32f) { if (crackSeed == 1 || k < 0.36f) seedCrack(cx, cy) ; crack(c, w, h, s, min(1f, (k - 0.32f) / 0.12f), 1f) }
                if (k in 0.3f..0.45f) { fill.color = Color.WHITE; fill.alpha = (120 * (1f - (k - 0.3f) / 0.15f)).toInt(); c.drawRect(0f, 0f, w, h, fill) } }
            PowerPhase.SHRINK -> crack(c, w, h, s, 1f, 1f - smooth((k - 0.7f) / 0.3f))
            PowerPhase.FROST -> frost(c, w, h, smooth(k / 0.3f) * (1f - smooth((k - 0.9f) / 0.1f) * 0.5f), t, s)
            PowerPhase.PEEK -> eye(c, w, h, k, t, cx, s)
            PowerPhase.DODGE -> slowMo(c, w, h, k, t, crashed, cx, cy, s)
            else -> Unit
        }
    }

    // ---------------- v4.1 hero effects ----------------

    /** Two heat-vision beams from the eyes sweeping across the screen, with glowing scorch dots where they hit. */
    private fun laser(c: Canvas, w: Float, h: Float, k: Float, t: Float, ex: Float, ey: Float, facing: Int, s: Float) {
        val on = smooth(k / 0.12f) * (1f - smooth((k - 0.9f) / 0.1f))
        if (on <= 0.01f) return
        val ang = (-0.25f + 0.55f * smooth((k - 0.1f) / 0.8f))           // radians below horizontal
        val dx = cos(ang) * facing; val dy = sin(ang)
        val len = (w + h) * 1.2f
        for (o in floatArrayOf(-6f, 6f)) {
            val x0 = ex + o * s; val y0 = ey
            val x1 = x0 + dx * len; val y1 = y0 + dy * len
            stroke.color = Color.rgb(0xFF, 0x3B, 0x2F); stroke.alpha = (140 * on).toInt(); stroke.strokeWidth = (10f + 3f * sin(t * 40f)) * s
            c.drawLine(x0, y0, x1, y1, stroke)
            stroke.color = Color.rgb(0xFF, 0xF2, 0xE0); stroke.alpha = (240 * on).toInt(); stroke.strokeWidth = 3f * s
            c.drawLine(x0, y0, x1, y1, stroke)
        }
        // Where the beam meets the floor (if it does): a little scorch glow.
        if (dy > 0.02f) {
            val gx = ex + dx * (h - ey) / dy
            if (gx in 0f..w) { fill.color = Color.rgb(0xFF, 0x8A, 0x1E); fill.alpha = (180 * on).toInt(); c.drawCircle(gx, h - 4f * s, (14f + 6f * sin(t * 30f)) * s, fill) }
        }
        fill.alpha = 255; stroke.alpha = 255
    }

    /** A frosty gust cone from the mouth, frosting the far edge of the screen. */
    private fun breath(c: Canvas, w: Float, h: Float, k: Float, t: Float, ex: Float, ey: Float, facing: Int, s: Float) {
        val blow = if (k > 0.32f) 1f - smooth((k - 0.9f) / 0.1f) else 0f
        if (blow <= 0.01f) return
        val reach = w * smooth((k - 0.32f) / 0.2f)
        val mx = ex; val my = ey + 20f * s
        path.reset(); path.moveTo(mx, my)
        path.lineTo(mx + facing * reach, my - reach * 0.28f); path.lineTo(mx + facing * reach, my + reach * 0.28f); path.close()
        fill.color = Color.rgb(0xC8, 0xEC, 0xFF); fill.alpha = (150 * blow).toInt(); c.drawPath(path, fill)
        stroke.color = Color.WHITE; stroke.strokeWidth = 3f * s
        for (i in 0 until 9) {
            val f = ((t * 1.6f + i / 7f) % 1f)
            val x = mx + facing * reach * f; val yy = my + (i - 3) * reach * 0.07f * f
            stroke.alpha = (200 * blow * (1f - f)).toInt(); c.drawLine(x, yy, x + facing * 26f * s, yy, stroke)
        }
        fill.alpha = 255; stroke.alpha = 255
        frost(c, w, h, 0.6f * blow * smooth((k - 0.45f) / 0.3f), t, s)
    }

    /** A bus-sized notification card held overhead, showing the real notification count. */
    private fun liftCard(c: Canvas, w: Float, k: Float, cx: Float, hy: Float, count: Int, s: Float) {
        val a = smooth(k / 0.15f)
        if (a <= 0.01f) return
        val cw = minOf(w * 0.86f, 420f * s); val ch = cw * 0.38f
        val left = (cx - cw / 2f).coerceIn(4f * s, w - cw - 4f * s)
        val bottom = hy - 4f * s
        r.set(left, bottom - ch, left + cw, bottom)
        fill.color = Color.argb((235 * a).toInt(), 0xFF, 0xFF, 0xFF); c.drawRoundRect(r, 18f * s, 18f * s, fill)
        stroke.color = Color.rgb(0x2C, 0x32, 0x3A); stroke.alpha = (255 * a).toInt(); stroke.strokeWidth = 3f * s
        c.drawRoundRect(r, 18f * s, 18f * s, stroke)
        fill.color = Color.rgb(0xF2, 0x7A, 0x1C); fill.alpha = (255 * a).toInt()
        c.drawCircle(left + ch * 0.5f, bottom - ch * 0.5f, ch * 0.3f, fill)
        text.color = Color.WHITE; text.alpha = (255 * a).toInt(); text.textSize = ch * 0.34f
        c.drawText(if (count in 0..999) "$count" else "!", left + ch * 0.5f, bottom - ch * 0.5f + ch * 0.12f, text)
        text.color = Color.rgb(0x2C, 0x32, 0x3A); text.textSize = ch * 0.2f; text.textAlign = Paint.Align.LEFT
        c.drawText(if (count == 1) "notification" else "notifications", left + ch, bottom - ch * 0.48f, text)
        text.textSize = ch * 0.13f; text.alpha = (170 * a).toInt()
        c.drawText("(bus-sized, very heavy)", left + ch, bottom - ch * 0.24f, text)
        text.textAlign = Paint.Align.CENTER; text.alpha = 255; fill.alpha = 255; stroke.alpha = 255
    }

    /** Super-speed: horizontal streaks around the runner. */
    private fun blur(c: Canvas, w: Float, k: Float, t: Float, cx: Float, cy: Float, s: Float) {
        stroke.color = Color.WHITE; stroke.strokeWidth = 3f * s
        for (i in 0 until 6) {
            val y = cy - (20f + i * 26f) * s
            val off = ((t * 3f + i * 0.37f) % 1f)
            stroke.alpha = (160 * (1f - off)).toInt()
            val x = cx + (off - 0.5f) * 240f * s
            c.drawLine(x - 70f * s, y, x + 70f * s, y, stroke)
        }
        stroke.alpha = 255
    }

    /** Pin the crack to a point (previews). */
    fun crackAt(x: Float, y: Float) = seedCrack(x, y)

    private fun seedCrack(x: Float, y: Float) { crackX = x; crackY = y; crackSeed = (x * 31 + y).toInt() or 2 }

    private fun strike(c: Canvas, w: Float, h: Float, k: Float, t: Float, crashed: Boolean, hx: Float, hy: Float, s: Float) {
        // Flash, then the bolt flickers and fades.
        if (k < 0.12f) { fill.color = Color.WHITE; fill.alpha = (150 * (1f - k / 0.12f)).toInt(); c.drawRect(0f, 0f, w, h, fill) }
        val a = if (k < 0.6f) 1f else 1f - (k - 0.6f) / 0.4f
        if (a <= 0f) return
        val flick = if ((t * 30f).toInt() % 3 == 0) 0.55f else 1f
        val sx: Float; val sy: Float; val ex: Float; val ey: Float
        if (crashed) { sx = hx + 40f * s; sy = 0f; ex = hx; ey = hy }            // comes down on his own head
        else { sx = hx; sy = hy; ex = if (hx < w / 2f) w * 0.92f else w * 0.08f; ey = h * 0.12f }
        val rnd = Random((t * 20f).toInt())
        path.reset(); path.moveTo(sx, sy)
        val n = 9
        for (i in 1 until n) {
            val f = i / n.toFloat()
            path.lineTo(sx + (ex - sx) * f + (rnd.nextFloat() - 0.5f) * 60f * s, sy + (ey - sy) * f + (rnd.nextFloat() - 0.5f) * 40f * s)
        }
        path.lineTo(ex, ey)
        stroke.color = MonkeyRenderer.BOLT; stroke.alpha = (110 * a * flick).toInt(); stroke.strokeWidth = 14f * s; c.drawPath(path, stroke)
        stroke.color = MonkeyRenderer.BOLT_CORE; stroke.alpha = (255 * a * flick).toInt(); stroke.strokeWidth = 4f * s; c.drawPath(path, stroke)
        // A forked branch.
        path.reset(); val mx = (sx + ex) / 2f; val my = (sy + ey) / 2f; path.moveTo(mx, my)
        path.lineTo(mx + 50f * s, my + 70f * s); path.lineTo(mx + 30f * s, my + 130f * s)
        stroke.strokeWidth = 2.5f * s; c.drawPath(path, stroke)
        if (!crashed) { fill.color = MonkeyRenderer.BOLT; fill.alpha = (160 * a).toInt(); c.drawCircle(ex, ey, 26f * s * flick, fill) }
    }

    /** A spider-web crack from the impact point. [grow] 0..1 how far it has spread, [a] opacity. */
    private fun crack(c: Canvas, w: Float, h: Float, s: Float, grow: Float, a: Float) {
        if (a <= 0.01f) return
        val rnd = Random(crackSeed)
        val rays = 9
        val maxL = min(w, h) * 0.55f
        for (pass in 0..1) {
            stroke.color = if (pass == 0) Color.argb(255, 30, 30, 36) else Color.WHITE
            stroke.strokeWidth = if (pass == 0) 3.2f * s else 1.4f * s
            stroke.alpha = ((if (pass == 0) 150 else 235) * a).toInt()
            val r2 = Random(crackSeed)
            for (i in 0 until rays) {
                var ang = (i / rays.toFloat()) * 2f * PI.toFloat() + (r2.nextFloat() - 0.5f) * 0.5f
                var x = crackX; var y = crackY
                val len = maxL * (0.5f + 0.5f * r2.nextFloat()) * grow
                val segs = 6
                path.reset(); path.moveTo(x, y)
                for (j in 0 until segs) {
                    ang += (r2.nextFloat() - 0.5f) * 0.6f
                    x += cos(ang) * len / segs; y += sin(ang) * len / segs
                    path.lineTo(x, y)
                }
                c.drawPath(path, stroke)
            }
            // Concentric rings joining the rays (the "web").
            for (ring in 1..2) {
                val rr = maxL * 0.16f * ring * grow
                path.reset()
                for (i in 0..rays) {
                    val ang = (i % rays / rays.toFloat()) * 2f * PI.toFloat()
                    val j = 0.85f + 0.3f * ((i * 7 + ring * 3) % 5) / 5f
                    val px = crackX + cos(ang) * rr * j; val py = crackY + sin(ang) * rr * j
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                c.drawPath(path, stroke)
            }
        }
        rnd.nextInt()
        // Impact shards.
        fill.color = Color.WHITE; fill.alpha = (90 * a).toInt(); c.drawCircle(crackX, crackY, 10f * s * grow, fill)
    }

    private fun frost(c: Canvas, w: Float, h: Float, f: Float, t: Float, s: Float) {
        if (f <= 0.01f) return
        // Icy bands creeping in from every edge (stacked translucent rects = soft gradient).
        val depth = 70f * s * f
        val steps = 6
        fill.color = MonkeyRenderer.ICE
        for (i in 0 until steps) {
            val d = depth * (1f - i / steps.toFloat())
            fill.alpha = (34 * f).toInt()
            c.drawRect(0f, 0f, w, d, fill); c.drawRect(0f, h - d, w, h, fill)
            c.drawRect(0f, d, d, h - d, fill); c.drawRect(w - d, d, w, h - d, fill)
        }
        // Frost ferns in the corners.
        stroke.color = Color.WHITE; stroke.alpha = (200 * f).toInt(); stroke.strokeWidth = 1.6f * s
        for (corner in 0 until 4) {
            val ox = if (corner % 2 == 0) 0f else w; val oy = if (corner < 2) 0f else h
            val dx = if (corner % 2 == 0) 1f else -1f; val dy = if (corner < 2) 1f else -1f
            for (b in 0 until 3) {
                val ang = (0.2f + b * 0.55f)
                val len = (110f + 40f * b) * s * f
                val ex = ox + dx * cos(ang) * len; val ey = oy + dy * sin(ang) * len
                c.drawLine(ox, oy, ex, ey, stroke)
                for (j in 1..3) {
                    val px = ox + (ex - ox) * j / 4f; val py = oy + (ey - oy) * j / 4f
                    val l = 18f * s * f * (1f - j / 5f)
                    c.drawLine(px, py, px + dx * l, py - dy * l * 0.3f, stroke)
                    c.drawLine(px, py, px - dx * l * 0.3f, py + dy * l, stroke)
                }
            }
        }
        // Snow across the whole screen (positions from time, no state).
        fill.color = Color.WHITE
        for (i in 0 until 28) {
            val sp = 0.06f + (i % 5) * 0.015f
            val x = ((i * 0.137f + sin(t * 0.8f + i) * 0.02f) % 1f) * w
            val y = (((t * sp) + i * 0.071f) % 1f) * h
            fill.alpha = (210 * f).toInt()
            c.drawCircle(x, y, (2f + (i % 3)) * s, fill)
        }
    }

    /** Giant Mode: one enormous eye peeking in from the nearest side edge. */
    private fun eye(c: Canvas, w: Float, h: Float, k: Float, t: Float, cx: Float, s: Float) {
        val inK = smooth((k - 0.06f) / 0.14f) * (1f - smooth((k - 0.82f) / 0.12f))
        if (inK <= 0.01f) return
        val left = cx < w / 2f
        val R = min(w, h) * 0.32f
        val ex = if (left) -R + R * 1.15f * inK else w + R - R * 1.15f * inK
        val ey = h * 0.42f
        // Furry brow + lid around it.
        fill.color = MonkeyRenderer.FUR; fill.alpha = 255
        c.drawCircle(ex, ey, R * 1.25f, fill)
        fill.color = MonkeyRenderer.FACE; c.drawCircle(ex, ey, R * 1.08f, fill)
        // Eyeball with a blink at ~55%.
        val blink = 1f - bumpK((k - 0.5f) / 0.1f)
        c.save()
        r.set(ex - R, ey - R * blink, ex + R, ey + R * blink); c.clipRect(r)
        fill.color = Color.WHITE; c.drawCircle(ex, ey, R, fill)
        // Iris looks around: at the user, then up, then at the monkey's spot.
        val lx = sin(t * 1.3f) * R * 0.3f + (if (left) R * 0.15f else -R * 0.15f)
        val ly = cos(t * 0.9f) * R * 0.2f
        fill.color = Color.rgb(0x5A, 0x36, 0x18); c.drawCircle(ex + lx, ey + ly, R * 0.46f, fill)
        fill.color = MonkeyRenderer.EYE; c.drawCircle(ex + lx, ey + ly, R * 0.24f, fill)
        fill.color = Color.WHITE; c.drawCircle(ex + lx - R * 0.12f, ey + ly - R * 0.14f, R * 0.08f, fill)
        c.restore()
        stroke.color = MonkeyRenderer.OUTLINE; stroke.alpha = 255; stroke.strokeWidth = 4f * s
        c.drawCircle(ex, ey, R, stroke)
        // Lashes / brow.
        stroke.strokeWidth = 9f * s; stroke.color = MonkeyRenderer.FUR_DARK
        r.set(ex - R * 1.05f, ey - R * 1.35f, ex + R * 1.05f, ey - R * 0.6f)
        c.drawArc(r, 200f, 140f, false, stroke)
    }

    /** Time Freeze: a cool green slow-mo tint, scanlines, and bananas drifting past slowly. */
    private fun slowMo(c: Canvas, w: Float, h: Float, k: Float, t: Float, crashed: Boolean, cx: Float, cy: Float, s: Float) {
        val f = smooth(k / 0.1f) * (1f - smooth((k - 0.88f) / 0.12f))
        if (f <= 0.01f) return
        fill.color = Color.rgb(0x10, 0x40, 0x22); fill.alpha = (60 * f).toInt(); c.drawRect(0f, 0f, w, h, fill)
        stroke.color = Color.rgb(0x7C, 0xFF, 0xA0); stroke.alpha = (22 * f).toInt(); stroke.strokeWidth = 1f * s
        var y = (t * 20f * s) % (6f * s); while (y < h) { c.drawLine(0f, y, w, y, stroke); y += 6f * s }
        text.color = Color.rgb(0x9C, 0xFF, 0xB8); text.alpha = (180 * f).toInt(); text.textSize = 16f * s
        c.drawText("SLOW-MO", w / 2f, 60f * s, text)
        if (banana.isEmpty) { banana.moveTo(-14f, -4f); banana.quadTo(0f, 12f, 14f, -4f); banana.quadTo(0f, 4f, -14f, -4f); banana.close() }
        // Three bananas cross the screen at chest height, slowly, with motion trails.
        for (i in 0 until 3) {
            val start = 0.12f + i * 0.18f
            val p = ((k - start) / 0.45f)
            if (p < 0f || p > 1f) continue
            val bx = w + 30f * s - (w + 60f * s) * p
            val by = cy - (95f + i * 28f) * s
            for (tr in 3 downTo 0) {
                c.save(); c.translate(bx + tr * 16f * s, by); c.rotate(t * 40f + i * 50f); c.scale(1.8f * s, 1.8f * s)
                fill.color = MonkeyRenderer.BANANA; fill.alpha = (if (tr == 0) 255 else 60 - tr * 12) .coerceAtLeast(10)
                c.drawPath(banana, fill); c.restore()
            }
        }
        if (crashed && k > 0.45f && k < 0.62f) {
            text.color = MonkeyRenderer.BANANA; text.alpha = 255; text.textSize = 22f * s
            c.drawText("BONK!", cx, cy - 150f * s, text)
        }
    }

    private fun smooth(k: Float): Float { val c = k.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }
    private fun bumpK(k: Float) = if (k <= 0f || k >= 1f) 0f else sin(PI.toFloat() * k)
    @Suppress("unused") private fun ab(v: Float) = abs(v)
}
