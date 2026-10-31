package com.combo.monkeycompanion.rig

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.combo.monkeycompanion.character.Wear
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.LEG
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.TAIL_SEGS
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * v10.16 Kutti Satan, the app's cute little devil (procedural, no images). Soft round white head and body, two small
 * white horns, tall solid black oval eyes (no highlights), no mouth unless reacting, stubby limbs, a long thin white
 * tail ending in a small red heart. A soft grey-lilac outline keeps him visible on white screens.
 * Magic fx (all pure functions of time + pose slots, no RNG): red smoke puff + sparkles (vanish), a red swirl portal
 * (reappear), spell sparks at the fingers, levitation sparkles and two shadow clones.
 */
class DevilRenderer : HumanRenderer() {
    companion object {
        val WHITE = Color.rgb(0xFF, 0xFF, 0xFF)
        val SHADE = Color.rgb(0xEC, 0xE8, 0xF2)
        val LINE = Color.rgb(0xB4, 0xAE, 0xC4)
        val EYE = Color.rgb(0x10, 0x10, 0x14)
        val HEART = Color.rgb(0xE5, 0x1C, 0x2A)
        val RED = Color.rgb(0xE5, 0x1C, 0x2A)
        val DEEP_RED = Color.rgb(0x9E, 0x0B, 0x18)
        val BLUSH = Color.argb(110, 0xFF, 0x7A, 0x8A)
        val SHADOW_CLONE = Color.rgb(0x3A, 0x10, 0x2A)
        const val LW = 2.2f
        val BOX = floatArrayOf(-50f, -102f, 50f, 6f)
    }
    override val skin = WHITE
    override val skinShade = SHADE
    override val hair = WHITE
    override val glintEye: FloatArray? = null
    override val faceBox: FloatArray get() = BOX

    private val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = LINE; strokeWidth = LW; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val headShader = RadialGradient(-12f, -48f, 60f, intArrayOf(WHITE, WHITE, SHADE), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    private val bodyShader = RadialGradient(-6f, -36f, 40f, intArrayOf(WHITE, WHITE, SHADE), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    private val hp = FloatArray(2)
    private val tailBuf = FloatArray(TAIL_SEGS * 2 + 2)
    private val TAIL_K = 1.32f
    /** Stubby arms: only part of the shared forearm is drawn, the round hand sits there. */
    private val ARM_K = 0.45f
    private fun on(id: String) = wear == null || has(id)

    override fun drawJets(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) = Unit
    override fun drawShadow(c: Canvas, rig: MonkeyRig, p: Pose) {
        val w = 26f * rig.scale
        fill.shader = null; fill.color = Color.argb(38, 60, 30, 80)
        oval.set(rig.anchorX - w, rig.anchorY - 3.5f * rig.scale, rig.anchorX + w, rig.anchorY + 3.5f * rig.scale); c.drawOval(oval, fill)
    }

    override fun drawBehind(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        val e = p.dvClone.coerceIn(0f, 1f)
        if (e > 0.02f) for (side in intArrayOf(-1, 1)) {
            c.save(); c.translate(side * 46f * rig.scale * e, -3f * rig.scale * sin(t * 5f + side))
            silhouette(c, rig, (150 * e).toInt(), t + side * 0.25f)
            c.restore()
        }
        if (p.dvAura > 0.02f) aura(c, rig, p.dvAura.coerceIn(0f, 1f), t, behind = true)
    }

    override fun drawFront(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        if (p.dvAura > 0.02f) aura(c, rig, p.dvAura.coerceIn(0f, 1f), t, behind = false)
        if (p.dvSpell > 0.02f) spell(c, rig, p.dvSpell.coerceIn(0f, 1f), t)
    }

    /** Long thin curvy tail (outline + white) with the red heart tip pointing along the last segment. */
    override fun drawTail(c: Canvas, rig: MonkeyRig) {
        val src = rig.tailPts
        val tp = tailBuf
        for (i in 0..TAIL_SEGS) { tp[2 * i] = src[0] + (src[2 * i] - src[0]) * TAIL_K; tp[2 * i + 1] = src[1] + (src[2 * i + 1] - src[1]) * TAIL_K }   // a long thin tail
        tmp.reset(); tmp.moveTo(tp[0], tp[1])
        for (i in 1 until TAIL_SEGS) {
            val mx = (tp[2 * i] + tp[2 * i + 2]) / 2f; val my = (tp[2 * i + 1] + tp[2 * i + 3]) / 2f
            tmp.quadTo(tp[2 * i], tp[2 * i + 1], mx, my)
        }
        val ex = tp[2 * TAIL_SEGS]; val ey = tp[2 * TAIL_SEGS + 1]
        tmp.lineTo(ex, ey)
        c.save(); c.concat(rig.hips)
        line.shader = null; line.color = LINE; line.strokeWidth = 3f + 2 * LW; c.drawPath(tmp, line)
        line.color = WHITE; line.strokeWidth = 3f; c.drawPath(tmp, line)
        if (on("heart")) {
            val ang = Math.toDegrees(atan2((ey - tp[2 * TAIL_SEGS - 1]).toDouble(), (ex - tp[2 * TAIL_SEGS - 2]).toDouble())).toFloat()
            c.save(); c.translate(ex, ey); c.rotate(ang + 90f)   // heart point (bottom) toward the tail, lobes outward
            tmp.reset()
            tmp.moveTo(0f, 2f)
            tmp.cubicTo(-9f, -4f, -7f, -13f, -2.5f, -12f); tmp.cubicTo(-1f, -11.6f, 0f, -10f, 0f, -9f)
            tmp.cubicTo(0f, -10f, 1f, -11.6f, 2.5f, -12f); tmp.cubicTo(7f, -13f, 9f, -4f, 0f, 2f); tmp.close()
            c.scale(1f, -1f)
            fill.shader = null; fill.color = HEART; c.drawPath(tmp, fill)
            soft.color = DEEP_RED; soft.strokeWidth = 1.4f; c.drawPath(tmp, soft); soft.color = LINE; soft.strokeWidth = LW
            c.restore()
        }
        c.restore()
    }

    override fun drawLegs(c: Canvas, rig: MonkeyRig) {
        for (m in arrayOf(rig.legL, rig.legR)) {
            c.save(); c.concat(m)
            oval.set(-8f, -6f, 8f, LEG - 10f); fill.shader = null; fill.color = WHITE
            c.drawRoundRect(oval, 8f, 8f, fill); c.drawRoundRect(oval, 8f, 8f, soft)
            oval.set(-8.5f, LEG - 17f, 8.5f, LEG - 7f); c.drawOval(oval, fill); c.drawOval(oval, soft)   // round little foot (stubby leg)
            c.restore()
        }
    }

    /** Soft pear body (narrow at the neck, round belly). */
    override fun drawTorso(c: Canvas, rig: MonkeyRig) {
        c.save(); c.concat(rig.hips)
        tmp.reset(); tmp.moveTo(0f, -58f)
        tmp.cubicTo(13f, -58f, 21f, -38f, 21f, -18f); tmp.cubicTo(21f, -2f, 12f, 5f, 0f, 5f)
        tmp.cubicTo(-12f, 5f, -21f, -2f, -21f, -18f); tmp.cubicTo(-21f, -38f, -13f, -58f, 0f, -58f); tmp.close()
        fill.shader = bodyShader; c.drawPath(tmp, fill); fill.shader = null; c.drawPath(tmp, soft)
        c.restore()
    }

    override fun drawArms(c: Canvas, rig: MonkeyRig) {
        for ((u, f) in arrayOf(rig.armLUpper to rig.armLFore, rig.armRUpper to rig.armRFore)) {
            line.shader = null
            for (pass in 0..1) for ((m, len) in arrayOf(u to rig.upperLen * 0.8f, f to rig.foreLen * ARM_K)) {
                c.save(); c.concat(m)
                if (pass == 0) { line.color = LINE; line.strokeWidth = 10f + 2 * LW } else { line.color = WHITE; line.strokeWidth = 10f }
                c.drawLine(0f, 0f, 0f, len, line); c.restore()
            }
            c.save(); c.concat(f); fill.color = WHITE; c.drawCircle(0f, rig.foreLen * ARM_K + 1.5f, 6.5f, fill); c.drawCircle(0f, rig.foreLen * ARM_K + 1.5f, 6.5f, soft); c.restore()
        }
    }

    /** Wide soft round head with two little horns (head space, neck at 0,0). */
    override fun drawHeadBase(c: Canvas, p: Pose, t: Float) {
        // horns first (behind the head outline), slightly curved, pointing up and out
        for (s in intArrayOf(-1, 1)) {
            tmp.reset()
            tmp.moveTo(s * 15f, -63f)
            tmp.cubicTo(s * 16f, -71f, s * 20f, -77f, s * 28f, -80f)   // inner edge sweeps up and out to a soft point
            tmp.cubicTo(s * 25f, -74f, s * 25f, -67f, s * 26f, -60f)   // outer edge back down: a narrow little horn
            tmp.close()
            fill.shader = null; fill.color = WHITE; c.drawPath(tmp, fill); c.drawPath(tmp, soft)
        }
        oval.set(-38f, -68f, 38f, 0f)
        fill.shader = headShader; c.drawRoundRect(oval, 34f, 32f, fill); fill.shader = null
        c.drawRoundRect(oval, 34f, 32f, soft)
    }
    override fun drawNose(c: Canvas, p: Pose) = Unit
    override fun drawBrows(c: Canvas, p: Pose) = Unit
    override fun drawCheeks(c: Canvas, p: Pose) {
        if (!(has("blush") || p.blush > 0.3f)) return
        fill.shader = null; fill.color = BLUSH
        for (s in intArrayOf(-1, 1)) { oval.set(s * 24f - 6f, -22f, s * 24f + 6f, -17f); c.drawOval(oval, fill) }
    }

    /** Tall solid black ovals; closing = squash to a soft curved line (no highlights, ever). */
    override fun drawEyes(c: Canvas, p: Pose) {
        val open = p.eyeOpen.coerceIn(0f, 1.3f)
        val lx = p.lookX * 0.7f; val ly = p.lookY * 0.5f
        for (s in intArrayOf(-1, 1)) {
            val x = s * 13f + lx; val y = -32f + ly
            if (p.eyesHappy > 0.5f) {         // ^ ^ happy arcs
                line.shader = null; line.color = EYE; line.strokeWidth = 2.8f
                tmp.reset(); tmp.moveTo(x - 6f, y + 2f); tmp.quadTo(x, y - 7f, x + 6f, y + 2f); c.drawPath(tmp, line)
            } else if (open < 0.22f || (s < 0 && p.wink > 0.5f)) {   // closed: a soft downward curve
                line.shader = null; line.color = EYE; line.strokeWidth = 2.6f
                tmp.reset(); tmp.moveTo(x - 6.5f, y); tmp.quadTo(x, y + 5f, x + 6.5f, y); c.drawPath(tmp, line)
            } else {
                val ry = 10.5f * minOf(open, 1.15f); val rx = 6.6f * (1f + 0.12f * (1f - minOf(open, 1f)))
                fill.shader = null; fill.color = EYE; oval.set(x - rx, y - ry, x + rx, y + ry); c.drawOval(oval, fill)
            }
        }
    }

    /** No mouth by default; a tiny expressive one only while talking / laughing / shocked. */
    override fun drawMouth(c: Canvas, p: Pose) {
        val o = p.mouthOpen.coerceIn(0f, 1f)
        if (o < 0.12f && p.mouthSmile < 0.85f && p.mouthSmile > -0.4f) return
        val y = -12f
        if (o >= 0.12f) {
            fill.shader = null; fill.color = Color.rgb(0x5A, 0x14, 0x24)
            oval.set(-3.2f - 1.5f * o, y - 1.5f, 3.2f + 1.5f * o, y + 1.5f + 5f * o); c.drawOval(oval, fill)
        } else {
            line.shader = null; line.color = EYE; line.strokeWidth = 1.8f
            val curve = if (p.mouthSmile > 0f) 3.5f else -3f
            tmp.reset(); tmp.moveTo(-4.5f, y); tmp.quadTo(0f, y + curve, 4.5f, y); c.drawPath(tmp, line)
        }
    }
    override fun drawHeadExtras(c: Canvas, p: Pose, t: Float) = Unit

    // ---------------- magic fx ----------------

    private fun star(c: Canvas, x: Float, y: Float, r: Float, color: Int, alpha: Int) {
        fill.shader = null; fill.color = color; fill.alpha = alpha.coerceIn(0, 255)
        tmp.reset()
        tmp.moveTo(x, y - r); tmp.quadTo(x, y, x + r, y); tmp.quadTo(x, y, x, y + r); tmp.quadTo(x, y, x - r, y); tmp.quadTo(x, y, x, y - r); tmp.close()
        c.drawPath(tmp, fill); fill.alpha = 255
    }

    /** Red smoke puff + sparkles (vanish) and the red swirl portal (reappear); drawn even while the body is faded out. */
    override fun drawPuff(c: Canvas, rig: MonkeyRig, p: Pose) {
        val s = rig.scale
        val smoke = p.dvSmoke.coerceIn(0f, 1f); val portal = p.dvPortal.coerceIn(0f, 1f)
        if (smoke <= 0.01f && portal <= 0.01f) { super.drawPuff(c, rig, p); return }
        pts[0] = 0f; pts[1] = -70f; rig.root.mapPoints(pts)
        val cx = pts[0]; val cy = pts[1]
        val tt = now
        if (smoke > 0.01f) {
            val grow = 0.55f + 0.45f * smoke
            for (i in 0 until 10) {
                val ang = i * 0.628f + 0.2f + 0.15f * sin(tt * 2f + i)
                val d = (16f + 34f * grow) * s
                val r = (20f + 9f * sin(i * 1.9f)) * s * grow
                fill.shader = null; fill.color = if (i % 3 == 0) DEEP_RED else RED; fill.alpha = (215 * smoke).toInt()
                c.drawCircle(cx + cos(ang) * d, cy + sin(ang) * d * 0.75f, r, fill)
            }
            fill.color = Color.rgb(0xFF, 0x4A, 0x55); fill.alpha = (200 * smoke).toInt(); c.drawCircle(cx, cy, 30f * s * grow, fill); fill.alpha = 255
            for (i in 0 until 6) {
                val ang = i * 1.047f + tt * 0.8f
                val d = (52f + 26f * smoke) * s
                val tw = 0.6f + 0.4f * sin(tt * 9f + i * 1.7f)
                star(c, cx + cos(ang) * d, cy + sin(ang) * d * 0.8f, 7f * s * tw, if (i % 2 == 0) RED else Color.WHITE, (255 * smoke).toInt())
            }
        }
        if (portal > 0.01f) {
            val px = rig.anchorX; val py = rig.anchorY - 2f * s
            val rx = 44f * s * portal; val ry = 11f * s * portal
            fill.shader = null; fill.color = Color.argb((200 * portal).toInt(), 0x2A, 0x04, 0x0A)
            oval.set(px - rx, py - ry, px + rx, py + ry); c.drawOval(oval, fill)
            line.shader = null; line.strokeWidth = 2.6f * s
            for (k in 0 until 3) {   // spinning swirl arms
                line.color = if (k == 1) DEEP_RED else RED; line.alpha = (255 * portal).toInt()
                val f = 1f - k * 0.28f
                oval.set(px - rx * f, py - ry * f, px + rx * f, py + ry * f)
                c.drawArc(oval, (tt * 260f + k * 120f) % 360f, 220f, false, line)
            }
            line.alpha = 255
            for (i in 0 until 4) {
                val ang = i * 1.571f + tt * 1.5f
                star(c, px + cos(ang) * rx * 1.1f, py - 10f * s - 20f * s * ((tt * 0.9f + i * 0.25f) % 1f), 5f * s, RED, (230 * portal).toInt())
            }
        }
    }

    private fun aura(c: Canvas, rig: MonkeyRig, e: Float, t: Float, behind: Boolean) {
        pts[0] = 0f; pts[1] = -60f; rig.root.mapPoints(pts)
        val s = rig.scale
        for (i in 0 until 7) {
            val ang = t * 1.7f + i * 0.898f
            val front = sin(ang) > 0f
            if (front == behind) continue
            val x = pts[0] + cos(ang) * 46f * s; val y = pts[1] + sin(ang) * 14f * s + 30f * s * sin(t * 1.3f + i)
            star(c, x, y, (4f + 2f * sin(t * 7f + i)) * s, if (i % 3 == 0) Color.WHITE else RED, (230 * e).toInt())
        }
    }

    private fun spell(c: Canvas, rig: MonkeyRig, e: Float, t: Float) {
        val s = rig.scale
        for (f in arrayOf(rig.armLFore, rig.armRFore)) {
            hp[0] = 0f; hp[1] = rig.foreLen * ARM_K + 4f; f.mapPoints(hp)
            for (i in 0 until 5) {
                val ph = (t * 2.2f + i * 0.2f) % 1f
                val ang = i * 1.257f + t * 3f
                val d = (6f + 20f * ph) * s
                star(c, hp[0] + cos(ang) * d, hp[1] + sin(ang) * d - 8f * s * ph, (5f * (1f - ph) + 1.5f) * s, if (i % 2 == 0) RED else Color.rgb(0xFF, 0x9A, 0x3C), (255 * e * (1f - ph)).toInt())
            }
            // a tiny crackle between finger and spark
            line.shader = null; line.color = RED; line.alpha = (200 * e).toInt(); line.strokeWidth = 1.6f * s
            val j = sin(t * 23f) * 4f * s
            c.drawLine(hp[0], hp[1], hp[0] + 6f * s, hp[1] - 6f * s + j, line); c.drawLine(hp[0] + 6f * s, hp[1] - 6f * s + j, hp[0] + 2f * s, hp[1] - 13f * s, line)
            line.alpha = 255
        }
    }

    /** A flat dark copy of him (shadow clone): head, horns, body, white eyes. */
    private fun silhouette(c: Canvas, rig: MonkeyRig, alpha: Int, t: Float) {
        fill.shader = null; fill.color = SHADOW_CLONE; fill.alpha = alpha
        c.save(); c.concat(rig.hips)
        tmp.reset(); tmp.moveTo(0f, -58f)
        tmp.cubicTo(13f, -58f, 21f, -38f, 21f, -18f); tmp.cubicTo(21f, -2f, 12f, 5f, 0f, 5f)
        tmp.cubicTo(-12f, 5f, -21f, -2f, -21f, -18f); tmp.cubicTo(-21f, -38f, -13f, -58f, 0f, -58f); tmp.close()
        c.drawPath(tmp, fill)
        c.restore()
        c.save(); c.concat(rig.head)
        for (s in intArrayOf(-1, 1)) { tmp.reset(); tmp.moveTo(s * 15f, -63f); tmp.cubicTo(s * 16f, -71f, s * 20f, -77f, s * 28f, -80f); tmp.cubicTo(s * 25f, -74f, s * 25f, -67f, s * 26f, -60f); tmp.close(); c.drawPath(tmp, fill) }
        oval.set(-38f, -68f, 38f, 0f); c.drawRoundRect(oval, 34f, 32f, fill)
        fill.color = Color.WHITE; fill.alpha = alpha
        val shut = com.combo.monkeycompanion.character.DevilBlink.closed(t)
        for (s in intArrayOf(-1, 1)) { oval.set(s * 13f - 5.5f, -32f - 9f * (1f - shut) - 0.8f, s * 13f + 5.5f, -32f + 9f * (1f - shut) + 0.8f); c.drawOval(oval, fill) }
        fill.alpha = 255
        c.restore()
    }
}

/** The edge-hide peek: his white round head, a horn and the black oval eyes peeking out. */
object DevilPeek : PeekFace("devil") {
    override fun drawFace(c: Canvas, wear: Wear?, s: State) {
        val tw = s.tw; val cy = s.cy
        val bob = if (s.peeking) sin(s.t * 7f) * 0.05f * tw else 0f
        fill(DevilRenderer.WHITE)
        for (k in intArrayOf(-1, 1)) {   // horns (the head is on its side in the tab)
            path.reset(); path.moveTo(-0.2f * tw, cy + k * 0.42f * tw + bob); path.lineTo(-0.05f * tw, cy + k * 0.95f * tw + bob)
            path.lineTo(0.12f * tw, cy + k * 0.5f * tw + bob); path.close(); c.drawPath(path, paint)
        }
        r.set(-0.7f * tw, cy - 0.68f * tw + bob, 0.62f * tw, cy + 0.68f * tw + bob); c.drawRoundRect(r, 0.6f * tw, 0.6f * tw, paint)
        stroke(DevilRenderer.LINE, 1.2f * s.density); c.drawRoundRect(r, 0.6f * tw, 0.6f * tw, paint)
        for (i in 0..1) {
            val y = eyeY(s, i) + bob
            if (s.blink) { stroke(DevilRenderer.EYE, 1.6f * s.density); c.drawLine(0.22f * tw, y, 0.38f * tw, y, paint) }
            else { fill(DevilRenderer.EYE); r.set(0.2f * tw + s.look, y - 0.09f * tw, 0.42f * tw + s.look, y + 0.09f * tw); c.drawOval(r, paint) }
        }
    }
}
