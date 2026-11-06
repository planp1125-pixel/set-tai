package com.combo.monkeycompanion.engine.stagefx

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * v10.32 shared stage-effect primitives any script can use (no per-character drawing):
 *  - [ray]: a coloured beam from a point (eyes / hands) to a point, with a hot core and a flicker;
 *  - [clashBurst]: sparks where two rays meet;
 *  - [Crack]: a cracked-glass overlay grown from an impact point (its shape comes from its own seed, never the
 *    frame-loop RNG);
 *  - [smoke]: a vanish puff in a body's [VanishStyle] colours (Satan red smoke, Angel white-gold, default grey poof),
 *    the same colours as their swipe smoke hops.
 * All motion is a pure function of time.
 */
enum class VanishStyle(val main: Int, val accent: Int) {
    POOF(Color.rgb(0xDD, 0xDD, 0xE4), Color.rgb(0xB8, 0xB8, 0xC4)),
    RED_SMOKE(Color.rgb(0xE5, 0x1C, 0x2A), Color.rgb(0x9E, 0x0B, 0x18)),
    WHITE_SMOKE(Color.rgb(0xFF, 0xFC, 0xF4), Color.rgb(0xF5, 0xC2, 0x42)),
}

object RayColors {
    val byName: Map<String, Int> = mapOf(
        "red" to Color.rgb(0xFF, 0x2A, 0x1A), "yellow" to Color.rgb(0xFF, 0xD8, 0x2A), "gold" to Color.rgb(0xF5, 0xC2, 0x42),
        "blue" to Color.rgb(0x3A, 0xA8, 0xFF), "green" to Color.rgb(0x3A, 0xE0, 0x6A), "white" to Color.WHITE, "purple" to Color.rgb(0xA0, 0x4A, 0xFF),
    )
    fun of(name: String): Int = byName[name.lowercase()] ?: Color.rgb(0xFF, 0x2A, 0x1A)
}

object StageFx {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** A beam from (x0,y0) to (x1,y1) px, [width] px, [k] 0..1 strength (fade in/out), [t] seconds. */
    fun ray(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, color: Int, width: Float, k: Float, t: Float) {
        if (k <= 0.01f) return
        val fl = 0.85f + 0.15f * sin(t * 53f)
        p.color = color; p.alpha = (90 * k).toInt(); p.strokeWidth = width * 2.4f * fl; c.drawLine(x0, y0, x1, y1, p)
        p.alpha = (230 * k).toInt(); p.strokeWidth = width * fl; c.drawLine(x0, y0, x1, y1, p)
        p.color = Color.WHITE; p.alpha = (220 * k).toInt(); p.strokeWidth = width * 0.35f; c.drawLine(x0, y0, x1, y1, p)
        f.color = color; f.alpha = (160 * k).toInt(); c.drawCircle(x0, y0, width * 1.2f * fl, f)
    }

    /** Sparks bursting where two rays meet at (x, y) px; [size] px. */
    fun clashBurst(c: Canvas, x: Float, y: Float, size: Float, a: Int, b: Int, k: Float, t: Float) {
        if (k <= 0.01f) return
        f.color = Color.WHITE; f.alpha = (230 * k).toInt(); c.drawCircle(x, y, size * (0.55f + 0.15f * sin(t * 37f)), f)
        for (i in 0 until 12) {
            val an = i * 0.5236f + t * 3f
            val len = size * (1.1f + 0.6f * sin(t * 23f + i * 1.7f))
            p.color = if (i % 2 == 0) a else b; p.alpha = (230 * k).toInt(); p.strokeWidth = size * 0.12f
            c.drawLine(x + cos(an) * size * 0.4f, y + sin(an) * size * 0.4f, x + cos(an) * len, y + sin(an) * len, p)
        }
    }

    /** A vanish / appear puff at (x, y) px. [k] 0..1 (peak in the middle), [size] px. */
    fun smoke(c: Canvas, x: Float, y: Float, size: Float, style: VanishStyle, k: Float, t: Float) {
        if (k <= 0.01f) return
        for (i in 0 until 9) {
            val an = i * 0.698f + t * 1.3f
            val r = size * (0.35f + 0.65f * k)
            f.color = if (i % 3 == 0) style.accent else style.main; f.alpha = (200 * k).toInt()
            c.drawCircle(x + cos(an) * r * 0.8f, y + sin(an) * r * 0.55f, size * 0.32f * (0.6f + k), f)
        }
    }

    /** Cracked glass grown from (cx, cy) (screen fractions); [seed] fixes its shape. */
    class Crack(val cx: Float, val cy: Float, seed: Long) {
        private class Seg(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val at: Float)
        private val segs = ArrayList<Seg>()
        init {
            val r = java.util.Random(seed)   // its own seed: never the frame-loop RNG
            val arms = 9
            for (i in 0 until arms) {
                var an = (i / arms.toFloat()) * 6.2832f + (r.nextFloat() - 0.5f) * 0.4f
                var x = 0f; var y = 0f; var d = 0f
                val len = 0.25f + r.nextFloat() * 0.35f
                while (d < len) {
                    val step = 0.03f + r.nextFloat() * 0.05f
                    an += (r.nextFloat() - 0.5f) * 0.6f
                    val nx = x + cos(an) * step; val ny = y + sin(an) * step
                    segs.add(Seg(x, y, nx, ny, d / len))
                    if (r.nextFloat() < 0.25f) { val ba = an + (if (r.nextBoolean()) 0.8f else -0.8f); segs.add(Seg(nx, ny, nx + cos(ba) * step * 1.5f, ny + sin(ba) * step * 1.5f, d / len)) }
                    x = nx; y = ny; d += step
                }
            }
            for (ring in 1..3) {
                val rr = 0.05f * ring
                for (j in 0 until 14) {
                    val a0 = j * 0.4488f; val a1 = a0 + 0.4488f
                    segs.add(Seg(cos(a0) * rr, sin(a0) * rr, cos(a1) * rr * 1.04f, sin(a1) * rr * 1.04f, ring * 0.2f))
                }
            }
        }
        val pieces get() = segs.size
        /** [k] 0..1 how far it has spread; [w] / [h] screen px. */
        fun draw(c: Canvas, w: Float, h: Float, k: Float) {
            if (k <= 0f) return
            val ox = cx * w; val oy = cy * h; val s = w
            p.strokeWidth = 2.6f * w / 1080f
            for (sg in segs) {
                if (sg.at > k) continue
                p.color = Color.argb(200, 30, 30, 40); c.drawLine(ox + sg.x0 * s, oy + sg.y0 * s, ox + sg.x1 * s, oy + sg.y1 * s, p)
                p.color = Color.argb(170, 255, 255, 255); c.drawLine(ox + sg.x0 * s + 1.5f, oy + sg.y0 * s + 1.5f, ox + sg.x1 * s + 1.5f, oy + sg.y1 * s + 1.5f, p)
            }
            f.color = Color.argb(150, 255, 255, 255); c.drawCircle(ox, oy, 10f * w / 1080f, f)
        }
    }

    fun dist(x0: Float, y0: Float, x1: Float, y1: Float) = hypot(x1 - x0, y1 - y0)
}
