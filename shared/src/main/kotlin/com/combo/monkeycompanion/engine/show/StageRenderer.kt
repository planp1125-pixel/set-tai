package com.combo.monkeycompanion.engine.show

import android.graphics.Canvas
import android.graphics.Paint
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.Animations
import com.combo.monkeycompanion.rig.MonkeyRenderer
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * v10.13.1: draws a [ChaseRun] on ONE full-screen stage (no window moves, so no jitter). Every value is a pure
 * function of show time, so frames are smooth at any fps and nothing can flip-flop.
 * Legs follow real ground speed, the runner leans INTO the run, a dust trail follows the feet,
 * lines go in a caption strip in the middle of the screen.
 */
class StageRenderer(private val catR: MonkeyRenderer, private val ratR: MonkeyRenderer) {
    private val rigC = MonkeyRig(); private val rigR = MonkeyRig()
    private val pC = Pose(); private val pR = Pose()
    private val ctx = AnimContext()
    private val fC = ChaseRun.Pos(); private val fR = ChaseRun.Pos(); private val tmp = ChaseRun.Pos()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    var catX = 0f; private set
    var catY = 0f; private set
    var ratX = 0f; private set
    var ratY = 0f; private set

    companion object {
        const val RAT_SCALE = 0.72f
        const val CHAR_H = 165f
        const val STRIDE = 70f
        const val LEAN = 12f
    }

    fun draw(c: Canvas, run: ChaseRun, t: Float, w: Float, scale: Float, d: Float) {
        run.frame(Role.CAT, t, fC); run.frame(Role.RAT, t, fR)
        catX = fC.x * w; catY = fC.y * w; ratX = fR.x * w; ratY = fR.y * w
        drawDust(c, run, Role.RAT, t, w, scale * RAT_SCALE)
        drawDust(c, run, Role.CAT, t, w, scale)
        drawChar(c, ratR, rigR, pR, fR, t, w, scale * RAT_SCALE)
        drawChar(c, catR, rigC, pC, fC, t, w, scale)
        drawEndFx(c, run, t, scale)
        drawCaption(c, run, t, w, run.aspect * w, d)
    }

    private fun drawChar(c: Canvas, r: MonkeyRenderer, rig: MonkeyRig, p: Pose, f: ChaseRun.Pos, t: Float, w: Float, s: Float) {
        if (!f.visible) return
        val running = f.state == MonkeyState.DASH
        val anim = if (running) f.dist * w / (STRIDE * s) * (2f * PI.toFloat() / 22f) else t
        Animations.fill(p, f.state, anim, anim, ctx)
        if (running) { p.bodyRot = LEAN; p.headRot = LEAN * 0.3f }
        val x = f.x * w; val y = f.y * w
        c.save()
        c.rotate(f.ang, x, y)
        if (f.sx != 1f || f.sy != 1f) c.scale(f.sx, f.sy, x, y)
        rig.layout(p, x, y, s, f.facing)
        r.draw(c, rig, p, anim)
        c.restore()
    }

    private fun drawDust(c: Canvas, run: ChaseRun, role: Role, t: Float, w: Float, s: Float) {
        val step = 0.07f
        val last = floor(t / step) * step
        fill.style = Paint.Style.FILL
        for (i in 0 until 6) {
            val tk = last - i * step
            if (tk < run.intro || tk > run.intro + run.run) continue
            run.frame(role, tk, tmp)
            if (tmp.state != MonkeyState.DASH) continue
            val age = ((t - tk) / (6 * step)).coerceIn(0f, 1f)
            fill.color = argb(((1f - age) * 150).toInt(), 205, 190, 165)
            c.drawCircle(tmp.x * w, tmp.y * w, (7f + 10f * age) * s, fill)
        }
    }

    private fun drawEndFx(c: Canvas, run: ChaseRun, t: Float, s: Float) {
        val e = t - run.intro - run.run
        if (e < 0f) return
        val hx = catX; val hy = catY - CHAR_H * s
        text.textSize = 22f * s; text.color = argb(255, 0, 0, 0); text.isFakeBoldText = false
        when (run.ending) {
            EndKind.CAT_BONKED, EndKind.CAT_BALL -> for (i in 0..2) { val a = t * 6f + i * 2.09f; c.drawText(if (i == 1) "🐦" else "⭐", hx + cos(a) * 40f * s, hy + sin(a) * 10f * s, text) }
            EndKind.FRIENDS -> for (i in 0..2) c.drawText("❤️", (catX + ratX) / 2f + (i - 1) * 30f * s, hy - 30f * s * (e % 1f) - i * 6f, text)
            EndKind.RAT_WINS -> c.drawText("🏆", ratX, ratY - CHAR_H * s * RAT_SCALE - 10f * s, text)
        }
    }

    private fun drawCaption(c: Canvas, run: ChaseRun, t: Float, w: Float, h: Float, d: Float) {
        var y = h * 0.42f
        for (l in run.lines) {
            if (t < l.at || t > l.at + l.dur) continue
            val a = (minOf((t - l.at) / 0.15f, (l.at + l.dur - t) / 0.2f, 1f).coerceIn(0f, 1f) * 255).toInt()
            text.textSize = 16f * d; text.isFakeBoldText = true
            val label = (if (l.role == Role.CAT) "🐱 Billa: " else "🐭 Chittu: ") + l.text
            val tw = text.measureText(label) + 28f * d
            val cx = w / 2f
            fill.style = Paint.Style.FILL; fill.color = argb((a * 0.82f).toInt(), 28, 30, 44)
            c.drawRoundRect(cx - tw / 2f, y, cx + tw / 2f, y + 30f * d, 15f * d, 15f * d, fill)
            text.color = argb(a, 255, 255, 255)
            c.drawText(label, cx, y + 21f * d, text)
            y += 36f * d
        }
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a.coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b
}
