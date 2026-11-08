package com.combo.monkeycompanion.rig

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/**
 * v10.23 Angel, Tutty's partner and opposite (procedural, no images). Same chibi body as the devil (it reuses his
 * renderer), but cream-white, no horns and no tail; a floating gold halo, small white feathered wings, pink cheeks,
 * a small smile, a gold trim on the dress hem. All fx are pure functions of time + pose slots (no RNG).
 * Pose slots she borrows from the devil (he never shares a body with her): dvSpell = healing sparkles, dvAura = soft
 * light pulse, dvClone = halo spin, dvFire = wing flap speed (0 calm .. 1 flying).
 */
class AngelRenderer : DevilRenderer() {
    companion object {
        val CREAM = Color.rgb(0xFF, 0xFC, 0xF4)
        val CREAM_SHADE = Color.rgb(0xF3, 0xEC, 0xDC)
        val GOLD = Color.rgb(0xF5, 0xC2, 0x42)
        val GOLD_DEEP = Color.rgb(0xC9, 0x8E, 0x14)
        val GOLD_LIGHT = Color.rgb(0xFF, 0xEB, 0x9C)
        val PINK = Color.argb(130, 0xFF, 0x86, 0x9C)
        val ANGEL_BOX = floatArrayOf(-50f, -114f, 50f, 6f)
        val FEATHER_LINE = Color.rgb(0xC9, 0xC0, 0xD6)

        /** Wing flap angle (degrees) at [t]: a slow gentle flutter, faster and bigger while flying ([fly] 0..1). */
        fun flap(t: Float, fly: Float): Float = (8f + 22f * fly) * sin(t * (3.2f + 9f * fly))
        /** Halo float height above the head at [t]. */
        fun haloBob(t: Float): Float = 2f * sin(t * 1.7f + 0.5f)
    }
    override val skin = CREAM
    override val skinShade = CREAM_SHADE
    override val hair = CREAM
    /** Taller than the devil's box so the floating halo (and its spin lift) is never clipped. */
    override val faceBox: FloatArray get() = ANGEL_BOX
    private val headShaderA = RadialGradient(-12f, -48f, 60f, intArrayOf(CREAM, CREAM, CREAM_SHADE), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    private val bodyShaderA = RadialGradient(-6f, -36f, 40f, intArrayOf(CREAM, CREAM, CREAM_SHADE), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)

    override fun drawTail(c: Canvas, rig: MonkeyRig) = Unit

    override fun drawBehind(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        if (p.dvAura > 0.02f) glow(c, rig, p.dvAura.coerceIn(0f, 1f), t)
        wings(c, rig, p, t)
    }

    override fun drawFront(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        if (p.dvSpell > 0.02f) heal(c, rig, p.dvSpell.coerceIn(0f, 1f), t)
    }

    /** Two small feathered wings at the shoulder blades, flapping gently (rotated around their root). */
    private fun wings(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        val a = flap(t, p.dvFire.coerceIn(0f, 1f))
        c.save(); c.concat(rig.hips)
        for (s in intArrayOf(-1, 1)) {
            c.save(); c.translate(s * 12f, -44f); c.rotate(-s * a)
            tmp.reset(); tmp.moveTo(0f, 0f)
            tmp.cubicTo(s * 14f, -22f, s * 36f, -26f, s * 42f, -14f)    // top edge sweeps out
            tmp.cubicTo(s * 40f, -8f, s * 36f, -6f, s * 34f, -4f)         // scalloped feather tips
            tmp.cubicTo(s * 34f, 2f, s * 28f, 3f, s * 26f, 2f)
            tmp.cubicTo(s * 25f, 8f, s * 18f, 9f, s * 16f, 7f)
            tmp.cubicTo(s * 12f, 12f, s * 5f, 9f, 0f, 4f); tmp.close()
            fill.shader = null; fill.color = Color.WHITE; c.drawPath(tmp, fill)
            soft.color = FEATHER_LINE; c.drawPath(tmp, soft)
            line.shader = null; line.color = FEATHER_LINE; line.strokeWidth = 1.2f   // two inner feather lines
            c.drawLine(s * 8f, -3f, s * 30f, -10f, line); c.drawLine(s * 6f, 1f, s * 20f, 2f, line)
            soft.color = LINE
            c.restore()
        }
        c.restore()
    }

    /** Same pear body as the devil, cream, with a little gold trim at the hem. */
    override fun drawTorso(c: Canvas, rig: MonkeyRig) {
        c.save(); c.concat(rig.hips)
        tmp.reset(); tmp.moveTo(0f, -58f)
        tmp.cubicTo(13f, -58f, 21f, -38f, 21f, -18f); tmp.cubicTo(21f, -2f, 12f, 5f, 0f, 5f)
        tmp.cubicTo(-12f, 5f, -21f, -2f, -21f, -18f); tmp.cubicTo(-21f, -38f, -13f, -58f, 0f, -58f); tmp.close()
        fill.shader = bodyShaderA; c.drawPath(tmp, fill); fill.shader = null; c.drawPath(tmp, soft)
        line.shader = null; line.color = GOLD; line.strokeWidth = 2.6f
        tmp.reset(); tmp.moveTo(-19f, -6f); tmp.quadTo(0f, 4f, 19f, -6f); c.drawPath(tmp, line)
        fill.color = GOLD; c.drawCircle(0f, -44f, 2.6f, fill)   // a tiny gold clasp at the collar
        c.restore()
    }

    override fun drawLegs(c: Canvas, rig: MonkeyRig) {
        for (m in arrayOf(rig.legL, rig.legR)) {
            c.save(); c.concat(m)
            oval.set(-8f, -6f, 8f, MonkeyRig.LEG - 10f); fill.shader = null; fill.color = CREAM
            c.drawRoundRect(oval, 8f, 8f, fill); c.drawRoundRect(oval, 8f, 8f, soft)
            oval.set(-8.5f, MonkeyRig.LEG - 17f, 8.5f, MonkeyRig.LEG - 7f); c.drawOval(oval, fill); c.drawOval(oval, soft)
            c.restore()
        }
    }

    override fun drawArms(c: Canvas, rig: MonkeyRig) {
        for ((u, f) in arrayOf(rig.armLUpper to rig.armLFore, rig.armRUpper to rig.armRFore)) {
            line.shader = null
            for (pass in 0..1) for ((m, len) in arrayOf(u to rig.upperLen * 0.8f, f to rig.foreLen * ARM_K)) {
                c.save(); c.concat(m)
                if (pass == 0) { line.color = LINE; line.strokeWidth = 10f + 2 * LW } else { line.color = CREAM; line.strokeWidth = 10f }
                c.drawLine(0f, 0f, 0f, len, line); c.restore()
            }
            c.save(); c.concat(f); fill.shader = null; fill.color = CREAM; c.drawCircle(0f, rig.foreLen * ARM_K + 1.5f, 6.5f, fill); c.drawCircle(0f, rig.foreLen * ARM_K + 1.5f, 6.5f, soft); c.restore()
        }
    }

    /** Round head, no horns; a soft little curl on the forehead. */
    override fun drawHeadBase(c: Canvas, p: Pose, t: Float) {
        oval.set(-38f, -68f, 38f, 0f)
        fill.shader = headShaderA; c.drawRoundRect(oval, 34f, 32f, fill); fill.shader = null
        c.drawRoundRect(oval, 34f, 32f, soft)
        tmp.reset(); tmp.moveTo(-2f, -67f); tmp.cubicTo(-8f, -76f, 4f, -80f, 5f, -73f); tmp.cubicTo(5f, -70f, 1f, -69f, 0f, -71f)
        line.shader = null; line.color = LINE; line.strokeWidth = 1.8f; c.drawPath(tmp, line)
    }

    /** Always-on soft pink cheeks. */
    override fun drawCheeks(c: Canvas, p: Pose) {
        fill.shader = null; fill.color = PINK
        for (s in intArrayOf(-1, 1)) { oval.set(s * 24f - 6.5f, -22f, s * 24f + 6.5f, -16.5f); c.drawOval(oval, fill) }
    }

    /** A small sweet smile by default (open mouth while talking / laughing). */
    override fun drawMouth(c: Canvas, p: Pose) {
        val o = p.mouthOpen.coerceIn(0f, 1f)
        val y = -13f
        if (o >= 0.12f) {
            fill.shader = null; fill.color = Color.rgb(0xB0, 0x4A, 0x5C)
            oval.set(-3f - 1.5f * o, y - 1.5f, 3f + 1.5f * o, y + 1.5f + 5f * o); c.drawOval(oval, fill)
        } else {
            line.shader = null; line.color = EYE; line.strokeWidth = 1.7f
            val curve = if (p.mouthSmile < -0.4f) -2.5f else 3f
            tmp.reset(); tmp.moveTo(-3.6f, y); tmp.quadTo(0f, y + curve, 3.6f, y); c.drawPath(tmp, line)
        }
    }

    /** The gold halo floating above her head (it spins edge-on during the halo spin). */
    override fun drawHeadExtras(c: Canvas, p: Pose, t: Float) {
        val spin = p.dvClone.coerceIn(0f, 1f)
        val y = -84f + haloBob(t) - 10f * spin
        val squash = if (spin > 0.01f) 0.25f + 0.75f * kotlin.math.abs(cos(t * 9f * spin)) else 1f
        val rx = 22f; val ry = 6.5f
        // soft glow
        fill.shader = null; fill.color = Color.argb((60 + 60 * spin).toInt(), 0xFF, 0xE0, 0x70)
        oval.set(-rx - 6f, y - ry * squash - 5f, rx + 6f, y + ry * squash + 5f); c.drawOval(oval, fill)
        line.shader = null; line.color = GOLD_DEEP; line.strokeWidth = 6f
        oval.set(-rx, y - ry * squash, rx, y + ry * squash); c.drawOval(oval, line)
        line.color = GOLD; line.strokeWidth = 4f; c.drawOval(oval, line)
        line.color = GOLD_LIGHT; line.strokeWidth = 1.4f
        c.drawArc(oval, 200f, 100f, false, line)
        if (spin > 0.05f) for (i in 0 until 4) {
            val a = t * 6f + i * 1.571f
            star(c, cos(a) * (rx + 6f), y + sin(a) * (ry + 3f), 4f, GOLD_LIGHT, (255 * spin).toInt())
        }
    }

    /** Healing: golden sparkles rise in a spiral from her hands plus a soft light pulse ring. */
    private fun heal(c: Canvas, rig: MonkeyRig, e: Float, t: Float) {
        val s = rig.scale
        pts[0] = 0f; pts[1] = -40f; rig.root.mapPoints(pts)
        val cx = pts[0]; val cy = pts[1]
        val pulse = (t * 0.9f) % 1f
        line.shader = null; line.color = GOLD_LIGHT; line.alpha = (180 * e * (1f - pulse)).toInt(); line.strokeWidth = 3f * s
        c.drawCircle(cx, cy, (30f + 60f * pulse) * s, line); line.alpha = 255
        for (i in 0 until 10) {
            val ph = (t * 0.7f + i * 0.1f) % 1f
            val ang = i * 0.628f + t * 2.2f
            val r = (24f + 18f * sin(ph * 3.1f)) * s
            star(c, cx + cos(ang) * r, cy + 20f * s - 90f * s * ph, (5f * (1f - ph) + 2f) * s, if (i % 3 == 0) Color.WHITE else GOLD, (255 * e * (1f - ph)).toInt())
        }
        for (f in arrayOf(rig.armLFore, rig.armRFore)) {
            hp[0] = 0f; hp[1] = rig.foreLen * ARM_K + 4f; f.mapPoints(hp)
            fill.shader = null; fill.color = GOLD_LIGHT; fill.alpha = (140 * e * (0.7f + 0.3f * sin(t * 8f))).toInt()
            c.drawCircle(hp[0], hp[1], 9f * s, fill); fill.alpha = 255
        }
    }

    /** Soft warm light around her (float / glow): a big faint radial disc and a few slow sparkles. */
    private fun glow(c: Canvas, rig: MonkeyRig, e: Float, t: Float) {
        val s = rig.scale
        pts[0] = 0f; pts[1] = -55f; rig.root.mapPoints(pts)
        val r = (70f + 6f * sin(t * 2.4f)) * s
        fill.shader = RadialGradient(pts[0], pts[1], r, intArrayOf(Color.argb((150 * e).toInt(), 0xFF, 0xEC, 0xA0), Color.argb(0, 0xFF, 0xEC, 0xA0)), null, Shader.TileMode.CLAMP)
        c.drawCircle(pts[0], pts[1], r, fill); fill.shader = null
        for (i in 0 until 5) {
            val ang = t * 1.1f + i * 1.257f
            star(c, pts[0] + cos(ang) * 50f * s, pts[1] + sin(ang) * 30f * s, (3f + 1.5f * sin(t * 5f + i)) * s, GOLD, (220 * e).toInt())
        }
    }
}

/** The edge-hide peek: her cream round head, the gold halo edge and the black oval eyes. */
object AngelPeek : PeekFace("angel") {
    override fun drawFace(c: Canvas, wear: com.combo.monkeycompanion.character.Wear?, s: State) {
        val tw = s.tw; val cy = s.cy
        val bob = if (s.peeking) sin(s.t * 6f) * 0.05f * tw else 0f
        stroke(AngelRenderer.GOLD, 0.12f * tw)   // halo seen edge-on beside the head (head lies on its side in the tab)
        c.drawLine(-0.95f * tw, cy - 0.5f * tw + bob, -0.95f * tw, cy + 0.5f * tw + bob, paint)
        fill(AngelRenderer.CREAM)
        r.set(-0.7f * tw, cy - 0.68f * tw + bob, 0.62f * tw, cy + 0.68f * tw + bob); c.drawRoundRect(r, 0.6f * tw, 0.6f * tw, paint)
        stroke(DevilRenderer.LINE, 1.2f * s.density); c.drawRoundRect(r, 0.6f * tw, 0.6f * tw, paint)
        for (i in 0..1) {
            val y = eyeY(s, i) + bob
            if (s.blink) { stroke(DevilRenderer.EYE, 1.6f * s.density); c.drawLine(0.22f * tw, y, 0.38f * tw, y, paint) }
            else { fill(DevilRenderer.EYE); r.set(0.2f * tw + s.look, y - 0.09f * tw, 0.42f * tw + s.look, y + 0.09f * tw); c.drawOval(r, paint) }
        }
    }
}
