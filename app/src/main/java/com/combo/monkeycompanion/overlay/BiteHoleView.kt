package com.combo.monkeycompanion.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** v10.13 Screen Eater: a procedurally drawn cartoon bite hole with crumbs. Tap it to "fix" the screen. */
@SuppressLint("ViewConstructor")
class BiteHoleView(ctx: Context, seed: Long, private val onTap: () -> Unit) : View(ctx) {
    private val rnd = Random(seed)
    private val bites = ArrayList<FloatArray>()      // cx, cy, r (fractions of the view)
    private val crumbs = Array(14) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), 0.012f + rnd.nextFloat() * 0.02f, rnd.nextFloat() * 6.28f) }
    private val hole = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.rgb(250, 240, 220) }
    private val crack = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.argb(200, 40, 30, 25) }
    private val crumb = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    val biteCount get() = bites.size

    fun addBite() {
        val n = bites.size
        val a = n * 1.9f
        bites += floatArrayOf(0.5f + 0.13f * cos(a) * (if (n == 0) 0f else 1f), 0.5f + 0.1f * sin(a) * (if (n == 0) 0f else 1f), 0.2f + rnd.nextFloat() * 0.05f)
        invalidate()
    }

    private fun scallop(p: Path, cx: Float, cy: Float, r: Float) {
        val teeth = 11
        p.moveTo(cx + r, cy)
        for (i in 1..teeth) {
            val a0 = (i - 0.5f) / teeth * 6.2832f; val a1 = i.toFloat() / teeth * 6.2832f
            p.quadTo(cx + cos(a0) * r * 0.86f, cy + sin(a0) * r * 0.86f, cx + cos(a1) * r, cy + sin(a1) * r)
        }
        p.close()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val m = minOf(w, h)
        if (bites.isEmpty()) return
        path.reset()
        for (b in bites) scallop(path, b[0] * w, b[1] * h, b[2] * m)
        hole.shader = RadialGradient(w / 2, h / 2, m * 0.45f, Color.rgb(8, 6, 6), Color.rgb(45, 30, 28), Shader.TileMode.CLAMP)
        c.drawPath(path, hole)
        rim.strokeWidth = m * 0.018f; c.drawPath(path, rim)
        crack.strokeWidth = m * 0.008f
        for (b in bites) for (k in 0..2) {
            val a = b[0] * 9f + k * 2.1f; val r = b[2] * m
            val x0 = b[0] * w + cos(a) * r; val y0 = b[1] * h + sin(a) * r
            c.drawLine(x0, y0, x0 + cos(a + 0.3f) * r * 0.35f, y0 + sin(a + 0.3f) * r * 0.35f, crack)
        }
        for ((i, cr) in crumbs.withIndex()) {
            if (i > bites.size * 5) break
            crumb.color = if (i % 3 == 0) Color.rgb(60, 45, 40) else Color.rgb(200, 190, 175)
            val x = cr[0] * w; val y = 0.62f * h + cr[1] * 0.36f * h; val s = cr[2] * m
            c.save(); c.rotate(cr[3] * 57f, x, y); c.drawRect(x - s, y - s * 0.7f, x + s, y + s * 0.7f, crumb); c.restore()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) onTap()
        return true
    }
}
