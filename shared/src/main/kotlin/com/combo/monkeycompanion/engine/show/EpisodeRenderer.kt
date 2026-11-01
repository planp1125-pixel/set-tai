package com.combo.monkeycompanion.engine.show

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.Animations
import com.combo.monkeycompanion.rig.MonkeyRenderer
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * v10.21 draws one frame of an [Episode] on the full-screen show stage: a soft floor, the props, Billa and Chittu
 * (big, with their faces acted), comic effects, speech bubbles above whoever is talking, the title card and the
 * iris-out. Everything is a pure function of show time, so it's smooth at any frame rate.
 * Props and effects are drawn with shapes only (no emoji), so they look the same on the phone and the desktop.
 */
class EpisodeRenderer(private val catR: MonkeyRenderer, private val ratR: MonkeyRenderer) {
    private val rigC = MonkeyRig(); private val rigR = MonkeyRig()
    private val pose = Pose()
    private val ctx = AnimContext()
    private val sC = Snap(); private val sR = Snap(); private val sp = Snap(); private val sParent = Snap()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val path = Path()
    private val oval = RectF()
    private val pt = FloatArray(2)

    // where each actor's head / feet ended up this frame (screen px), for effects and bubbles
    private val headC = FloatArray(3); private val headR = FloatArray(3)
    private val feetC = FloatArray(2); private val feetR = FloatArray(2)

    private var w = 1f; private var h = 1f; private var floorY = 1f; private var d = 1f; private var t = 0f
    private var catS = 1f; private var ratS = 1f

    companion object {
        /** Billa's rig scale as a fraction of the screen width (he is about a third of the width tall). */
        const val CAT_K = 0.0021f
        const val RAT_K = 0.62f
        const val STRIDE = 70f
        /** Head centre and head top in head-local rig units. */
        const val HEAD_CY = -38f
        const val HEAD_TOP = -84f
    }

    /** Pixel scale of an actor (for tests and the overlay). */
    fun scaleFor(id: String, width: Float) = if (id == Episode.RAT) width * CAT_K * RAT_K else width * CAT_K

    /**
     * Draw [ep] at time [time]. [floorPad] = px between the stage floor and the bottom of the screen
     * (keeps the feet above the navigation bar). [density] = px per dp.
     */
    fun draw(c: Canvas, ep: Episode, time: Float, width: Float, height: Float, floorPad: Float, density: Float,
             catScale: Float = 0f, ratScale: Float = 0f) {
        w = width; h = height; d = density; t = time
        floorY = h - floorPad
        // v10.22: the real characters' own size when known (so the drama is played by them, not by bigger copies)
        catS = if (catScale > 0f) catScale else w * CAT_K
        ratS = if (ratScale > 0f) ratScale else catS * RAT_K
        val cat = ep.track(Episode.CAT); val rat = ep.track(Episode.RAT)
        cat?.sample(t, sC); rat?.sample(t, sR)

        drawFloor(c, ep)
        // props that live behind the actors
        for ((id, tr) in ep.tracks) if (behind(id)) drawProp(c, ep, id, tr)
        shadow(c, sC, catS); shadow(c, sR, ratS)
        if (cat != null) drawActor(c, catR, rigC, sC, catS, headC, feetC)
        if (rat != null) drawActor(c, ratR, rigR, sR, ratS, headR, feetR)
        for ((id, tr) in ep.tracks) if (id != Episode.CAT && id != Episode.RAT && !behind(id)) drawProp(c, ep, id, tr)
        drawSpeed(c, sC, catS); drawSpeed(c, sR, ratS)
        drawBodyFx(c, sC, headC, catS); drawBodyFx(c, sR, headR, ratS)
        for (q in ep.cues) if (t >= q.at && t <= q.at + q.dur) drawCue(c, q, ((t - q.at) / q.dur).coerceIn(0f, 1f))
        drawBubbles(c, ep)
        drawTitle(c, ep)
        drawIris(c, ep)
    }

    // ───────────────────────── stage ─────────────────────────

    private fun drawFloor(c: Canvas, ep: Episode) {
        val a = min(1f, t / 0.5f) * (1f - ((t - ep.irisAt) / 0.5f).coerceIn(0f, 1f))
        if (a <= 0f) return
        // a soft stage floor so the eye knows where the ground is
        for (i in 0 until 3) {
            fill.color = argb((a * 16).toInt(), 40, 28, 20)
            c.drawRect(0f, floorY + i * 0.006f * w, w, h, fill)
        }
        line.color = argb((a * 90).toInt(), 70, 45, 30); line.strokeWidth = 2f * d
        c.drawLine(0f, floorY, w, floorY, line)
    }

    private fun shadow(c: Canvas, s: Snap, sc: Float) {
        if (!s.visible || s.x < -0.3f || s.x > 1.3f) return
        if (s.edge != 0 || (abs(s.rot) > 60f && abs(s.rot) < 120f && s.y > 0.2f)) return   // on the wall / hanging
        val lift = s.y.coerceAtLeast(0f)
        val k = (1f - lift / 0.6f).coerceIn(0.25f, 1f)
        fill.color = argb((60 * k).toInt(), 0, 0, 0)
        val rx = 34f * sc * k * s.sx.coerceIn(0.6f, 1.6f); val ry = 7f * sc * k
        oval.set(s.x * w - rx, floorY - ry, s.x * w + rx, floorY + ry)
        c.drawOval(oval, fill)
    }

    // ───────────────────────── actors ─────────────────────────

    private fun drawActor(c: Canvas, r: MonkeyRenderer, rig: MonkeyRig, s: Snap, sc: Float, head: FloatArray, feet: FloatArray) {
        head[2] = 0f
        if (!s.visible) return
        val x = s.x * w; val y = floorY - s.y * w
        feet[0] = x; feet[1] = y
        if (x < -0.5f * w || x > 1.5f * w) return
        val running = (s.state == MonkeyState.DASH || s.state == MonkeyState.WALK) && s.anim == t
        val anim = if (running) s.dist * w / (STRIDE * sc) * (2f * PI.toFloat() / 22f) * (if (s.state == MonkeyState.WALK) 0.8f else 1f) else s.anim
        val st = if (running) anim else s.st
        ctx.edge = when (s.edge) { 1 -> AnimContext.TOP; 2 -> AnimContext.WALL; else -> AnimContext.GROUND }
        Animations.fill(pose, s.state, anim, st, ctx)
        ctx.edge = AnimContext.GROUND
        if (running && s.state == MonkeyState.DASH) { pose.bodyRot = 14f; pose.headRot = 5f }
        applyExpr(pose, s.expr, s.face)

        val px = x + s.pvx * sc * s.face; val py = y - s.pvy * sc
        val e = s.expr
        val shake = if (e.shake > 0f) e.shake * 3f * d * sin(t * 61f) else 0f
        c.save()
        if (shake != 0f) c.translate(shake, 0f)
        if (s.rot != 0f) c.rotate(s.rot, px, py)
        if (s.sx != 1f || s.sy != 1f) c.scale(s.sx, s.sy, px, py)
        rig.layout(pose, x, y, sc, s.face)
        r.draw(c, rig, pose, anim)
        c.restore()

        // head centre / top on screen (through the same squash + rotation)
        pt[0] = 0f; pt[1] = HEAD_CY; rig.head.mapPoints(pt)
        val hc = xform(pt[0], pt[1], s, px, py)
        pt[0] = 0f; pt[1] = HEAD_TOP; rig.head.mapPoints(pt)
        val ht = xform(pt[0], pt[1], s, px, py)
        head[0] = hc.first + shake; head[1] = hc.second; head[2] = 1f
        // keep the "top" for bubbles (the highest point, whatever the rotation)
        topY[if (r === catR) 0 else 1] = min(ht.second, hc.second - 40f * sc)
    }

    private val topY = FloatArray(2)

    private fun xform(x0: Float, y0: Float, s: Snap, px: Float, py: Float): Pair<Float, Float> {
        var x = px + (x0 - px) * s.sx; var y = py + (y0 - py) * s.sy
        if (s.rot != 0f) {
            val a = s.rot * PI.toFloat() / 180f; val cs = cos(a); val sn = sin(a)
            val dx = x - px; val dy = y - py
            x = px + dx * cs - dy * sn; y = py + dx * sn + dy * cs
        }
        return x to y
    }

    private fun applyExpr(p: Pose, e: Expr, face: Float) {
        if (!e.eye.isNaN()) p.eyeOpen = e.eye
        if (!e.happy.isNaN()) p.eyesHappy = e.happy
        if (!e.brow.isNaN()) p.browAngle = e.brow * 26f
        if (!e.mouth.isNaN()) p.mouthOpen = e.mouth
        if (!e.smile.isNaN()) p.mouthSmile = e.smile
        if (!e.lookX.isNaN()) p.lookX = e.lookX * 4f * face
        if (!e.lookY.isNaN()) p.lookY = e.lookY * 3f
        if (!e.head.isNaN()) p.headRot = e.head
        if (!e.anger.isNaN()) p.anger = e.anger
        if (!e.blush.isNaN()) p.blush = e.blush
        if (!e.armL.isNaN()) { p.armLRaise = e.armL; p.armLElbow = -10f }
        if (!e.armR.isNaN()) { p.armRRaise = e.armR; p.armRElbow = -10f }
        if (!e.ear.isNaN()) p.earWiggle = e.ear * sin(t * 34f)
        if (!e.zzz.isNaN()) p.zzz = e.zzz
        if (e.munch) { p.mouthOpen = 0.15f + 0.35f * abs(sin(t * 11f)); p.headY = 1.5f * sin(t * 11f) }
        if (e.headShake > 0f) p.headRot = 13f * e.headShake * sin(t * 13f)
    }

    /** Tears and sweat from the face overrides. */
    private fun drawBodyFx(c: Canvas, s: Snap, head: FloatArray, sc: Float) {
        if (head[2] == 0f) return
        val hx = head[0]; val hy = head[1]
        if (s.expr.tear) {
            for (side in -1..1 step 2) {
                for (i in 0 until 3) {
                    val k = ((t * 1.6f + i / 3f) % 1f)
                    val x = hx + side * 15f * sc + side * k * 6f * sc
                    val y = hy + 4f * sc + k * 34f * sc
                    fill.color = argb((220 * (1f - k)).toInt(), 90, 170, 255)
                    drop(c, x, y, (3.4f - k) * sc)
                }
            }
        }
        if (s.expr.sweat) {
            for (i in 0 until 2) {
                val k = ((t * 1.3f + i * 0.5f) % 1f)
                val side = if (i == 0) 1f else -1f
                val x = hx + side * (40f + 18f * k) * sc; val y = hy - (34f - 30f * k * k) * sc
                fill.color = argb((230 * (1f - k)).toInt(), 120, 200, 255)
                drop(c, x, y, 4f * sc)
            }
        }
    }

    private fun drop(c: Canvas, x: Float, y: Float, r: Float) {
        path.reset()
        path.moveTo(x, y - r * 2.2f)
        path.quadTo(x + r * 1.1f, y - r * 0.2f, x, y + r)
        path.quadTo(x - r * 1.1f, y - r * 0.2f, x, y - r * 2.2f)
        path.close()
        c.drawPath(path, fill)
    }

    /** Speed lines behind a fast runner (the "zip"). */
    private fun drawSpeed(c: Canvas, s: Snap, sc: Float) {
        if (!s.visible) return
        val v = abs(s.vx)
        if (v < 0.9f || abs(s.rot) > 45f) return
        val dir = if (s.vx > 0f) 1f else -1f
        val k = ((v - 0.9f) / 1.5f).coerceIn(0.2f, 1f)
        line.strokeWidth = 2.6f * d
        for (i in 0 until 4) {
            val y = floorY - s.y * w - (28f + i * 30f) * sc
            val back = s.x * w - dir * (48f + (i % 2) * 14f) * sc
            val len = (60f + 40f * ((i * 7) % 3)) * sc * k
            line.color = argb((150 * k).toInt(), 60, 60, 70)
            c.drawLine(back, y, back - dir * len, y, line)
        }
    }

    // ───────────────────────── props ─────────────────────────

    private fun propAt(ep: Episode, id: String, tr: Track, out: Snap): Boolean {
        tr.sample(t, out)
        if (!out.visible) return false
        for (cr in tr.carries) {
            if (t < cr.t0 || t >= cr.t1) continue
            val parent = ep.track(cr.parent) ?: continue
            parent.sample(t, sParent)
            if (!sParent.visible) return false
            out.x = sParent.x + cr.dx * sParent.face
            out.y = sParent.y + cr.dy * (if (sParent.state == MonkeyState.SIT || sParent.state == MonkeyState.SLEEP) 0.75f else 1f)
            out.rot = cr.rot * sParent.face
            out.face = sParent.face
            // follow a rotated parent (e.g. a sideways pan) roughly: keep it simple, props ride upright
            return true
        }
        return true
    }

    private fun behind(id: String) = id.startsWith("hole") || id == "plate" || id == "trap"

    private fun drawProp(c: Canvas, ep: Episode, id: String, tr: Track) {
        if (!propAt(ep, id, tr, sp)) return
        val x = sp.x * w; val y = floorY - sp.y * w
        val u = w * 0.01f
        when {
            id.startsWith("hole") -> {
                // a mouse hole in the skirting: dark arch with a wooden rim
                val hw = 5.2f * u; val hh = 7.6f * u
                path.reset()
                path.moveTo(x - hw, floorY); path.lineTo(x - hw, floorY - hh + hw)
                oval.set(x - hw, floorY - hh, x + hw, floorY - hh + 2f * hw)
                path.arcTo(oval, 180f, 180f)
                path.lineTo(x + hw, floorY); path.close()
                fill.color = argb(255, 120, 78, 48); c.drawPath(path, fill)
                c.save(); c.scale(0.8f, 0.86f, x, floorY)
                fill.color = argb(255, 22, 14, 10); c.drawPath(path, fill)
                c.restore()
            }
            id == "plate" -> {
                fill.color = argb(255, 245, 245, 240); oval.set(x - 5f * u, y - 1.3f * u, x + 5f * u, y + 0.6f * u); c.drawOval(oval, fill)
                line.color = argb(255, 60, 60, 70); line.strokeWidth = 1.6f * d; c.drawOval(oval, line)
                line.color = argb(140, 200, 40, 40); oval.inset(1.2f * u, 0.35f * u); c.drawOval(oval, line)
            }
            id.startsWith("laddu") || id.startsWith("snack") || id == "gift" -> laddu(c, x, y, 1.9f * u, full = true)
            id.startsWith("cheese") -> cheese(c, x, y, u)
            id.startsWith("feather") -> feather(c, x, y, sp.rot, u)
            id == "ball" || id == "bowl" -> ball(c, x, y + (if (id == "bowl") -1.2f * u else 0f), sp.rot, if (id == "bowl") 2.6f * u else 2.2f * u, id == "bowl")
            id == "trap" -> mousetrap(c, x, y, u)
            id == "chilli" -> chilli(c, x, y, sp.rot, u)
            id.startsWith("half") -> laddu(c, x, y, 1.9f * u, full = false)
            id == "pan" -> pan(c, x, y, sp.rot, u)
            id == "peel" -> peel(c, x, y, sp.rot, u)
            id == "banana" -> banana(c, x, y, sp.rot, u)
            id == "plaster" -> plaster(c, x, y, sp.rot, u)
        }
    }

    private fun cheese(c: Canvas, x: Float, y: Float, u: Float) {
        path.reset()
        path.moveTo(x - 2.4f * u, y + 0.9f * u); path.lineTo(x + 2.4f * u, y + 0.9f * u); path.lineTo(x + 2.4f * u, y - 0.6f * u)
        path.lineTo(x - 2.4f * u, y - 1.8f * u); path.close()
        fill.color = argb(255, 255, 200, 40); c.drawPath(path, fill)
        line.color = argb(255, 150, 100, 10); line.strokeWidth = 1.2f * d; c.drawPath(path, line)
        fill.color = argb(255, 220, 160, 20)
        c.drawCircle(x - 0.6f * u, y - 0.3f * u, 0.45f * u, fill); c.drawCircle(x + 1.2f * u, y + 0.2f * u, 0.35f * u, fill)
    }

    private fun feather(c: Canvas, x: Float, y: Float, rot: Float, u: Float) {
        c.save(); c.rotate(rot, x, y)
        oval.set(x - 0.9f * u, y - 4f * u, x + 0.9f * u, y + 0.6f * u)
        fill.color = argb(255, 250, 120, 160); c.drawOval(oval, fill)
        line.color = argb(255, 160, 60, 100); line.strokeWidth = 1f * d
        c.drawLine(x, y + 1.6f * u, x, y - 3.8f * u, line)
        c.restore()
    }

    private fun ball(c: Canvas, x: Float, y: Float, rot: Float, r: Float, bowling: Boolean) {
        val cy = y - r
        fill.color = if (bowling) argb(255, 40, 40, 90) else argb(255, 230, 50, 50); c.drawCircle(x, cy, r, fill)
        c.save(); c.rotate(rot, x, cy)
        if (bowling) {
            fill.color = argb(255, 15, 15, 30)
            c.drawCircle(x - r * 0.3f, cy - r * 0.35f, r * 0.13f, fill); c.drawCircle(x + r * 0.05f, cy - r * 0.45f, r * 0.13f, fill); c.drawCircle(x - r * 0.05f, cy - r * 0.1f, r * 0.13f, fill)
        } else {
            line.color = argb(255, 255, 255, 255); line.strokeWidth = r * 0.22f
            c.drawLine(x - r * 0.9f, cy, x + r * 0.9f, cy, line)
        }
        c.restore()
        fill.color = argb(90, 255, 255, 255); c.drawCircle(x - r * 0.35f, cy - r * 0.4f, r * 0.25f, fill)
    }

    private fun mousetrap(c: Canvas, x: Float, y: Float, u: Float) {
        fill.color = argb(255, 205, 150, 90); bubbleBox.set(x - 3.6f * u, y - 1.0f * u, x + 3.6f * u, y + 0.2f * u)
        c.drawRoundRect(bubbleBox, 0.4f * u, 0.4f * u, fill)
        line.color = argb(255, 120, 80, 40); line.strokeWidth = 1.2f * d; c.drawRoundRect(bubbleBox, 0.4f * u, 0.4f * u, line)
        line.color = argb(255, 150, 150, 160); line.strokeWidth = 1.6f * d
        c.drawLine(x - 2.6f * u, y - 1.1f * u, x + 2.6f * u, y - 1.1f * u, line)
        c.drawLine(x - 2.6f * u, y - 1.1f * u, x - 2.6f * u, y - 2.4f * u, line)
        c.drawLine(x + 2.6f * u, y - 1.1f * u, x + 2.6f * u, y - 2.4f * u, line)
    }

    private fun chilli(c: Canvas, x: Float, y: Float, rot: Float, u: Float) {
        c.save(); c.rotate(rot, x, y)
        path.reset()
        path.moveTo(x - 2.8f * u, y - 0.4f * u)
        path.quadTo(x, y - 1.6f * u, x + 2.6f * u, y + 0.6f * u)
        path.quadTo(x, y + 0.2f * u, x - 2.8f * u, y + 0.5f * u)
        path.close()
        fill.color = argb(255, 220, 30, 30); c.drawPath(path, fill)
        line.color = argb(255, 120, 10, 10); line.strokeWidth = 1.1f * d; c.drawPath(path, line)
        line.color = argb(255, 40, 140, 50); line.strokeWidth = 1.6f * d
        c.drawLine(x - 2.8f * u, y, x - 3.6f * u, y - 0.8f * u, line)
        c.restore()
    }

    private fun laddu(c: Canvas, x: Float, y: Float, r: Float, full: Boolean) {
        c.save()
        if (!full) c.clipRect(x - r * 1.2f, y - r * 1.2f, x + r * 1.2f, y + r * 0.15f)
        fill.color = argb(255, 245, 160, 30); c.drawCircle(x, y, r, fill)
        fill.color = argb(255, 255, 205, 90); c.drawCircle(x - r * 0.35f, y - r * 0.35f, r * 0.35f, fill)
        fill.color = argb(255, 205, 110, 20)
        for (i in 0 until 6) { val a = i * 1.1f; c.drawCircle(x + cos(a) * r * 0.55f, y + sin(a) * r * 0.5f, r * 0.11f, fill) }
        line.color = argb(255, 120, 60, 10); line.strokeWidth = 1.4f * d; c.drawCircle(x, y, r, line)
        c.restore()
    }

    private fun pan(c: Canvas, x: Float, y: Float, rot: Float, u: Float) {
        c.save(); c.rotate(rot, x, y)
        val r = 4.6f * u
        line.color = argb(255, 40, 40, 44); line.strokeWidth = 1.5f * u
        c.drawLine(x, y + r * 0.9f, x, y + r * 2.3f, line)                 // handle
        line.color = argb(255, 120, 70, 40); line.strokeWidth = 1.3f * u
        c.drawLine(x, y + r * 1.5f, x, y + r * 2.3f, line)                  // wooden grip
        fill.color = argb(255, 30, 30, 34); c.drawCircle(x, y, r, fill)
        fill.color = argb(255, 62, 62, 70); c.drawCircle(x, y, r * 0.78f, fill)
        fill.color = argb(110, 255, 255, 255); oval.set(x - r * 0.55f, y - r * 0.6f, x - r * 0.05f, y - r * 0.3f); c.drawOval(oval, fill)
        c.restore()
    }

    private fun peel(c: Canvas, x: Float, y: Float, rot: Float, u: Float) {
        c.save(); c.rotate(rot, x, y)
        val r = 2.6f * u
        fill.color = argb(255, 250, 215, 50); line.color = argb(255, 150, 110, 20); line.strokeWidth = 1.2f * d
        for (i in -1..1) {
            c.save(); c.rotate(i * 55f, x, y)
            oval.set(x - r * 0.35f, y - r * 0.1f, x + r * 0.35f, y + r * 1.4f)
            c.drawOval(oval, fill); c.drawOval(oval, line)
            c.restore()
        }
        fill.color = argb(255, 120, 90, 30); oval.set(x - r * 0.3f, y - r * 0.55f, x + r * 0.3f, y + r * 0.1f); c.drawOval(oval, fill)
        c.restore()
    }

    private fun banana(c: Canvas, x: Float, y: Float, rot: Float, u: Float) {
        c.save(); c.rotate(rot, x, y)
        val r = 3.2f * u
        path.reset()
        path.moveTo(x - r, y - r * 0.4f)
        path.quadTo(x, y + r * 0.9f, x + r, y - r * 0.5f)
        path.quadTo(x, y + r * 0.3f, x - r, y - r * 0.4f)
        path.close()
        fill.color = argb(255, 250, 215, 50); c.drawPath(path, fill)
        line.color = argb(255, 150, 110, 20); line.strokeWidth = 1.2f * d; c.drawPath(path, line)
        c.restore()
    }

    private fun plaster(c: Canvas, x: Float, y: Float, rot: Float, u: Float) {
        c.save(); c.rotate(rot, x, y)
        val hw = 3.4f * u; val hh = 1.1f * u
        fill.color = argb(255, 240, 200, 160); oval.set(x - hw, y - hh, x + hw, y + hh); c.drawRoundRect(oval, hh, hh, fill)
        line.color = argb(255, 180, 130, 90); line.strokeWidth = 1.1f * d; c.drawRoundRect(oval, hh, hh, line)
        fill.color = argb(255, 250, 230, 210); oval.set(x - hh, y - hh * 0.8f, x + hh, y + hh * 0.8f); c.drawRect(oval, fill)
        fill.color = argb(255, 180, 130, 90)
        for (i in -1..1) for (j in -1..1 step 2) c.drawCircle(x + i * hh * 0.5f, y + j * hh * 0.35f, 0.9f * d, fill)
        c.restore()
    }

    // ───────────────────────── effects ─────────────────────────

    private fun anchor(q: Cue, k: Float): Pair<Float, Float>? {
        if (!q.x.isNaN()) return q.x * w to (floorY - q.y * w)
        val head = when (q.track) { Episode.CAT -> headC; Episode.RAT -> headR; else -> null }
        if (head != null) return if (head[2] == 0f) null else head[0] to head[1]
        return null
    }

    private fun drawCue(c: Canvas, q: Cue, k: Float) {
        val sc = (if (q.track == Episode.RAT) ratS else catS) * q.big
        when (q.kind) {
            Fx2.DUST -> {
                val f = when (q.track) { Episode.CAT -> feetC; Episode.RAT -> feetR; else -> null }
                val (x, y) = if (!q.x.isNaN()) q.x * w to floorY else if (f != null) f[0] to f[1] else return
                for (i in 0 until 6) {
                    val a = (i / 5f - 0.5f) * 2.6f
                    val dist = (14f + 42f * k) * sc
                    fill.color = argb((170 * (1f - k)).toInt(), 215, 200, 175)
                    c.drawCircle(x + sin(a) * dist, y - abs(cos(a)) * dist * 0.35f - 4f * sc, (8f + 12f * k) * sc, fill)
                }
            }
            Fx2.STARS -> {
                val (hx, hy) = anchor(q, k) ?: return
                val fade = min(1f, (1f - k) * 4f)
                for (i in 0 until 4) {
                    val a = t * 5.5f + i * (2f * PI.toFloat() / 4f)
                    val x = hx + cos(a) * 58f * sc; val y = hy - 52f * sc + sin(a) * 14f * sc
                    if (i == 3) bird(c, x, y, 9f * sc, fade) else star(c, x, y, 11f * sc, fade, argb(255, 255, 214, 40))
                }
            }
            Fx2.WORD -> {
                val (hx, hy) = anchor(q, k) ?: return
                val pop = when { k < 0.12f -> 0.5f + k / 0.12f * 0.75f; k < 0.22f -> 1.25f - (k - 0.12f) / 0.1f * 0.25f; else -> 1f }
                val fade = min(1f, (1f - k) * 5f)
                val y = if (q.x.isNaN()) hy - 120f * catS else hy
                val r0 = 36f * d
                val bx = hx.coerceIn(r0 * 1.7f, w - r0 * 1.7f)
                burst(c, bx, y, r0 * pop, fade)
                text.textSize = 22f * d * pop; text.color = argb((255 * fade).toInt(), 210, 20, 26)
                c.save(); c.rotate(-8f, bx, y)
                c.drawText(q.text, bx, y + 8f * d * pop, text)
                c.restore()
            }
            Fx2.HEART -> {
                val (hx, hy) = anchor(q, k) ?: return
                val size = (10f + 10f * min(1f, k * 3f)) * sc
                heart(c, hx + sin(k * 9f) * 6f * sc, hy - 70f * sc - k * 90f * sc, size, min(1f, (1f - k) * 3f))
            }
            Fx2.HEARTS -> {
                val (hx, hy) = anchor(q, k) ?: return
                for (i in 0 until 3) {
                    val kk = ((k * 1.4f - i * 0.18f)).coerceIn(0f, 1f)
                    if (kk <= 0f) continue
                    heart(c, hx + (i - 1) * 36f * sc + sin(kk * 8f + i) * 6f * sc, hy - 60f * sc - kk * 80f * sc, 8f * sc, min(1f, (1f - kk) * 3f))
                }
            }
            Fx2.BULB -> {
                val (hx, hy) = anchor(q, k) ?: return
                val pop = min(1f, k / 0.12f)
                bulb(c, hx, hy - 120f * sc, 16f * sc * pop, min(1f, (1f - k) * 4f))
            }
            Fx2.EXCLAIM, Fx2.QUESTION -> {
                val (hx, hy) = anchor(q, k) ?: return
                val jump = 10f * sc * sin(min(1f, k * 4f) * PI.toFloat())
                text.textSize = 44f * sc; text.color = argb((255 * min(1f, (1f - k) * 4f)).toInt(), if (q.kind == Fx2.EXCLAIM) 220 else 40, 30, if (q.kind == Fx2.EXCLAIM) 30 else 160)
                c.drawText(if (q.kind == Fx2.EXCLAIM) "!" else "?", hx + 42f * sc, hy - 70f * sc - jump, text)
            }
            Fx2.NOTES -> {
                val (hx, hy) = anchor(q, k) ?: return
                for (i in 0 until 3) {
                    val kk = ((t * 0.7f + i / 3f) % 1f)
                    val fade = min(1f, (1f - kk) * 3f) * min(1f, (1f - k) * 6f)
                    note(c, hx + (30f + 14f * i) * sc + sin(kk * 7f) * 8f * sc, hy - (60f + 70f * kk) * sc, 7f * sc, fade)
                }
            }
            Fx2.TEAR_POOL, Fx2.IMPRINT -> {}
        }
    }

    private fun star(c: Canvas, x: Float, y: Float, r: Float, a: Float, color: Int) {
        path.reset()
        for (i in 0 until 10) {
            val ang = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
            val rr = if (i % 2 == 0) r else r * 0.45f
            if (i == 0) path.moveTo(x + cos(ang) * rr, y + sin(ang) * rr) else path.lineTo(x + cos(ang) * rr, y + sin(ang) * rr)
        }
        path.close()
        fill.color = withAlpha(color, a); c.drawPath(path, fill)
        line.color = argb((255 * a).toInt(), 120, 80, 0); line.strokeWidth = 1.2f * d; c.drawPath(path, line)
    }

    private fun bird(c: Canvas, x: Float, y: Float, r: Float, a: Float) {
        fill.color = argb((255 * a).toInt(), 90, 170, 240); c.drawCircle(x, y, r, fill)
        fill.color = argb((255 * a).toInt(), 255, 170, 40)
        path.reset(); path.moveTo(x + r * 0.8f, y - r * 0.2f); path.lineTo(x + r * 1.6f, y); path.lineTo(x + r * 0.8f, y + r * 0.25f); path.close(); c.drawPath(path, fill)
        fill.color = argb((255 * a).toInt(), 20, 20, 20); c.drawCircle(x + r * 0.35f, y - r * 0.25f, r * 0.16f, fill)
    }

    private fun burst(c: Canvas, x: Float, y: Float, r: Float, a: Float) {
        path.reset()
        val n = 14
        for (i in 0 until n * 2) {
            val ang = i * PI.toFloat() / n
            val rr = if (i % 2 == 0) r else r * 0.62f
            val px = x + cos(ang) * rr * 1.6f; val py = y + sin(ang) * rr
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        fill.color = argb((255 * a).toInt(), 255, 236, 80); c.drawPath(path, fill)
        line.color = argb((255 * a).toInt(), 20, 20, 20); line.strokeWidth = 2.2f * d; c.drawPath(path, line)
    }

    private fun heart(c: Canvas, x: Float, y: Float, r: Float, a: Float) {
        path.reset()
        path.moveTo(x, y + r * 0.9f)
        path.cubicTo(x - r * 1.6f, y - r * 0.2f, x - r * 0.7f, y - r * 1.4f, x, y - r * 0.45f)
        path.cubicTo(x + r * 0.7f, y - r * 1.4f, x + r * 1.6f, y - r * 0.2f, x, y + r * 0.9f)
        path.close()
        fill.color = argb((255 * a).toInt(), 230, 30, 60); c.drawPath(path, fill)
        fill.color = argb((150 * a).toInt(), 255, 255, 255); c.drawCircle(x - r * 0.5f, y - r * 0.45f, r * 0.2f, fill)
    }

    private fun bulb(c: Canvas, x: Float, y: Float, r: Float, a: Float) {
        if (r <= 0.5f) return
        line.color = argb((200 * a).toInt(), 255, 200, 0); line.strokeWidth = 2.4f * d
        for (i in 0 until 8) { val ang = i * PI.toFloat() / 4f; c.drawLine(x + cos(ang) * r * 1.4f, y + sin(ang) * r * 1.4f, x + cos(ang) * r * 2f, y + sin(ang) * r * 2f, line) }
        fill.color = argb((255 * a).toInt(), 255, 236, 90); c.drawCircle(x, y, r, fill)
        fill.color = argb((255 * a).toInt(), 150, 150, 150); oval.set(x - r * 0.45f, y + r * 0.8f, x + r * 0.45f, y + r * 1.35f); c.drawRect(oval, fill)
        line.color = argb((255 * a).toInt(), 60, 60, 60); line.strokeWidth = 1.4f * d; c.drawCircle(x, y, r, line)
    }

    private fun note(c: Canvas, x: Float, y: Float, r: Float, a: Float) {
        fill.color = argb((255 * a).toInt(), 40, 40, 60)
        oval.set(x - r, y - r * 0.7f, x + r, y + r * 0.7f); c.drawOval(oval, fill)
        line.color = fill.color; line.strokeWidth = r * 0.3f
        c.drawLine(x + r * 0.85f, y, x + r * 0.85f, y - r * 3.2f, line)
        c.drawLine(x + r * 0.85f, y - r * 3.2f, x + r * 2f, y - r * 2.4f, line)
    }

    // ───────────────────────── speech ─────────────────────────

    private val bubbleBox = RectF()
    private val placed = ArrayList<RectF>()

    private fun drawBubbles(c: Canvas, ep: Episode) {
        placed.clear()
        for (l in ep.lines) {
            if (t < l.at || t > l.at + l.dur) continue
            val head = if (l.role == Role.CAT) headC else headR
            if (head[2] == 0f) continue
            val k = t - l.at
            val pop = when { k < 0.1f -> 0.6f + k / 0.1f * 0.5f; k < 0.18f -> 1.1f - (k - 0.1f) / 0.08f * 0.1f; else -> 1f }
            val a = min(1f, (l.at + l.dur - t) / 0.15f).coerceIn(0f, 1f)
            text.textSize = 17f * d
            val lines = wrap(l.text, w * 0.6f)
            val tw = lines.maxOf { text.measureText(it) }
            val lh = 22f * d
            val bw = tw + 26f * d; val bh = lines.size * lh + 16f * d
            val top = topY[if (l.role == Role.CAT) 0 else 1]
            var cx = head[0].coerceIn(bw / 2f + 8f * d, w - bw / 2f - 8f * d)
            var by = (top - 16f * d - bh).coerceAtLeast(40f * d)
            bubbleBox.set(cx - bw / 2f, by, cx + bw / 2f, by + bh)
            for (o in placed) if (o.intersects(bubbleBox.left, bubbleBox.top, bubbleBox.right, bubbleBox.bottom)) { by = o.top - bh - 8f * d; bubbleBox.offsetTo(bubbleBox.left, by) }
            placed += RectF(bubbleBox)
            c.save(); c.scale(pop, pop, head[0], bubbleBox.bottom)
            // tail toward the speaker's head
            val tx = head[0].coerceIn(bubbleBox.left + 16f * d, bubbleBox.right - 16f * d)
            path.reset()
            path.moveTo(tx - 9f * d, bubbleBox.bottom - 2f); path.lineTo(head[0] * 0.3f + tx * 0.7f, min(bubbleBox.bottom + 14f * d, top - 2f * d)); path.lineTo(tx + 9f * d, bubbleBox.bottom - 2f); path.close()
            fill.color = argb((255 * a).toInt(), 255, 255, 255)
            line.color = argb((255 * a).toInt(), 20, 20, 20); line.strokeWidth = 2.2f * d
            c.drawRoundRect(bubbleBox, 14f * d, 14f * d, fill); c.drawRoundRect(bubbleBox, 14f * d, 14f * d, line)
            c.drawPath(path, fill); c.drawPath(path, line)
            fill.color = argb((255 * a).toInt(), 255, 255, 255)
            c.drawRect(tx - 7.5f * d, bubbleBox.bottom - 4f * d, tx + 7.5f * d, bubbleBox.bottom - 0.5f, fill)
            text.color = argb((255 * a).toInt(), 20, 20, 20)
            for ((i, s) in lines.withIndex()) c.drawText(s, cx, bubbleBox.top + 8f * d + lh * (i + 0.78f), text)
            c.restore()
        }
    }

    private fun wrap(s: String, max: Float): List<String> {
        if (text.measureText(s) <= max) return listOf(s)
        val words = s.split(' ')
        val out = ArrayList<String>(); var cur = ""
        for (wd in words) {
            val n = if (cur.isEmpty()) wd else "$cur $wd"
            if (text.measureText(n) > max && cur.isNotEmpty()) { out += cur; cur = wd } else cur = n
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }

    // ───────────────────────── title + iris ─────────────────────────

    private fun drawTitle(c: Canvas, ep: Episode) {
        if (t > Episode.TITLE_LEN) return
        if (ep.title.isBlank()) {   // v10.22: no title card; just a small hint that a tap stops it
            val a = (min(1f, t / 0.3f) * min(1f, (Episode.TITLE_LEN - t) / 0.4f)).coerceIn(0f, 1f)
            text.textSize = 12f * d
            val msg = "tap anywhere to stop"
            val tw = text.measureText(msg) + 24f * d
            bubbleBox.set(w / 2f - tw / 2f, 44f * d, w / 2f + tw / 2f, 70f * d)
            fill.color = argb((170 * a).toInt(), 13, 13, 13); c.drawRoundRect(bubbleBox, 13f * d, 13f * d, fill)
            text.color = argb((230 * a).toInt(), 235, 235, 240)
            c.drawText(msg, w / 2f, 61.5f * d, text)
            return
        }
        val inK = min(1f, t / 0.35f)
        val slide = Track.ease(Ease.BACK, inK)
        val a = min(1f, (Episode.TITLE_LEN - t) / 0.4f).coerceIn(0f, 1f)
        val cy = 70f * d + 50f * d * slide - 50f * d
        text.textSize = 15f * d
        val small = "Billa & Chittu in…"
        val sw = text.measureText(small)
        text.textSize = 28f * d
        val big = ep.title
        val bw = max(sw, text.measureText(big)) + 44f * d
        bubbleBox.set(w / 2f - bw / 2f, cy, w / 2f + bw / 2f, cy + 96f * d)
        fill.color = argb((235 * a).toInt(), 13, 13, 13); c.drawRoundRect(bubbleBox, 18f * d, 18f * d, fill)
        line.color = argb((255 * a).toInt(), 224, 16, 26); line.strokeWidth = 3f * d; c.drawRoundRect(bubbleBox, 18f * d, 18f * d, line)
        text.textSize = 15f * d; text.color = argb((255 * a).toInt(), 240, 120, 120)
        c.drawText(small, w / 2f, cy + 24f * d, text)
        text.textSize = 28f * d; text.color = argb((255 * a).toInt(), 255, 255, 255)
        c.drawText(big, w / 2f, cy + 60f * d, text)
        text.textSize = 12f * d; text.color = argb((200 * a).toInt(), 170, 170, 175)
        c.drawText("tap anywhere to stop", w / 2f, cy + 84f * d, text)
    }

    private fun drawIris(c: Canvas, ep: Episode) {
        val e = t - ep.irisAt
        if (e < 0f) return
        val cx = ep.irisX * w; val cy = floorY - ep.irisY * w
        val far = hypot(max(cx, w - cx), max(cy, h - cy)) + 4f
        val small = 0.2f * w
        val c1 = Episode.IRIS_CLOSE; val c2 = c1 + Episode.IRIS_HOLD; val c3 = c2 + Episode.IRIS_SHUT
        val r = when {
            e < c1 -> far + (small - far) * Track.ease(Ease.IN_OUT, e / c1)
            e < c2 -> small
            e < c3 -> small * (1f - Track.ease(Ease.IN, (e - c2) / Episode.IRIS_SHUT))
            else -> 0f
        }
        val fadeOut = (1f - (e - (Episode.IRIS_LEN - 0.45f)) / 0.45f).coerceIn(0f, 1f)
        val black = argb((255 * fadeOut).toInt(), 8, 8, 10)
        if (r > 1f) {
            // everything outside the circle goes black (a thick ring, which works everywhere)
            val ring = far * 2f
            line.color = black; line.strokeWidth = ring
            c.drawCircle(cx, cy, r + ring / 2f, line)
        } else {
            fill.color = black; c.drawRect(0f, 0f, w, h, fill)
            val k = ((e - c3) / 0.35f).coerceIn(0f, 1f)
            text.color = argb((255 * k * fadeOut).toInt(), 255, 255, 255); text.textSize = 40f * d
            c.drawText("The End", w / 2f, h * 0.46f, text)
            text.color = argb((255 * k * fadeOut).toInt(), 224, 16, 26); text.textSize = 16f * d
            c.drawText("Billa & Chittu", w / 2f, h * 0.46f + 34f * d, text)
            heart(c, w / 2f, h * 0.46f + 64f * d, 9f * d, k * fadeOut)
        }
    }

    private fun withAlpha(color: Int, a: Float) = (((color ushr 24) * a).toInt().coerceIn(0, 255) shl 24) or (color and 0xFFFFFF)
    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a.coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b
}
