package com.combo.monkeycompanion.rig

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.combo.monkeycompanion.brain.PhoneMoments.ChargeStyle
import com.combo.monkeycompanion.brain.PhoneMoments.Moment
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/** v4.4 phone moments: body poses (layered on the current animation) and small drawn effects. */
object MomentPoses {
    private fun seg(k: Float, a: Float, b: Float) = ((k - a) / (b - a)).coerceIn(0f, 1f)
    private fun bump(k: Float, a: Float, b: Float) = if (k < a || k > b) 0f else sin(PI.toFloat() * (k - a) / (b - a))

    /** [k] 0..1 through the moment, [st] seconds in. Faces the battery via the renderer's facing (the caller). */
    fun apply(p: Pose, m: Moment, k: Float, st: Float) {
        val inK = seg(k, 0f, 0.12f) * (1f - seg(k, 0.88f, 1f))
        when (m) {
            Moment.CHARGE -> {
                p.bodyY += -70f * bump(k, 0.05f, 0.4f); p.squashY = 1f + 0.06f * bump(k, 0.38f, 0.5f)
                p.armRRaise = 14f + 125f * inK; p.armRElbow = -8f + 8f * inK          // arm thrust up toward the icon
                p.armLRaise = 14f + 40f * inK; p.armLElbow = -8f - 60f * inK
                p.eyeOpen = 1f + 0.35f * inK; p.mouthOpen = 0.35f * inK; p.mouthSmile = 0.6f + 0.4f * inK
                p.lookX = 3f * inK; p.lookY = -3f * inK
            }
            Moment.UNPLUG -> {
                p.mouthSmile = 0.6f - 1.4f * inK; p.browAngle = -12f * inK; p.eyeOpen = 1f - 0.2f * inK
                p.headRot = 8f * inK; p.headY = 2f * inK
                p.armRRaise = 14f + 130f * inK; p.armRElbow = -8f - 25f * inK + 22f * sin(st * 9f) * inK
            }
            Moment.FULL -> {
                p.armLRaise = 14f + 82f * inK; p.armLElbow = -8f - 112f * inK
                p.armRRaise = 14f + 82f * inK; p.armRElbow = -8f - 112f * inK
                p.squashX = 1f + 0.06f * inK * (0.5f + 0.5f * sin(st * 10f)); p.bodyY += -6f * bump(k, 0.1f, 0.3f)
                p.mouthSmile = 1f; p.mouthOpen = 0.25f * inK; p.eyesHappy = 0.8f * inK
            }
            Moment.LOW -> {
                val wob = seg(k, 0.05f, 0.35f) * (1f - seg(k, 0.35f, 0.45f))
                p.bodyRot = 8f * sin(st * 12f) * wob
                val fall = seg(k, 0.35f, 0.55f).let { it * it } * (1f - seg(k, 0.92f, 1f))
                p.bodyRot += -80f * fall; p.bodyY += 34f * fall; p.bodyX += -10f * fall
                p.eyeOpen = 1f - 0.6f * inK; p.mouthOpen = 0.4f * inK; p.mouthSmile = -0.5f * inK; p.browAngle = -10f * inK
                // Lying there, one trembling arm pointing at the battery.
                val point = seg(k, 0.55f, 0.65f) * (1f - seg(k, 0.9f, 1f))
                p.armRRaise = 14f + 150f * point + 4f * sin(st * 30f) * point; p.armRElbow = -8f + 8f * point
            }
            Moment.WIFI -> {
                val reach = bump(k, 0.05f, 0.55f); val hold = seg(k, 0.45f, 0.6f) * (1f - seg(k, 0.9f, 1f))
                p.armLRaise = 14f + 150f * reach + 45f * hold; p.armLElbow = -8f - 10f * reach - 70f * hold
                p.armRRaise = 14f + 150f * reach + 45f * hold; p.armRElbow = -8f - 10f * reach - 70f * hold
                p.bodyY += -30f * bump(k, 0.15f, 0.45f); p.eyeOpen = 1f + 0.3f * reach; p.mouthOpen = 0.3f * reach
                p.mouthSmile = 0.6f + 0.4f * hold; p.eyesHappy = 0.7f * hold
            }
            Moment.HEADPHONES -> {
                p.bodyX += 6f * sin(st * 7f) * inK; p.bodyY += -6f * abs(sin(st * 7f)) * inK; p.headRot = 10f * sin(st * 7f) * inK
                p.armLRaise = 14f + 100f * inK + 30f * sin(st * 7f) * inK; p.armRRaise = 14f + 100f * inK - 30f * sin(st * 7f) * inK
                p.armLElbow = -8f - 60f * inK; p.armRElbow = -8f - 60f * inK
                p.eyesHappy = inK; p.mouthSmile = 1f; p.notes = inK
            }
            Moment.SCREENSHOT -> {
                p.flash = bump(k, 0f, 0.2f)
                p.armRRaise = 14f + 110f * inK; p.armRElbow = -8f - 70f * inK            // peace sign up by the face
                p.mouthSmile = 1f; p.mouthOpen = 0.3f * inK; p.eyesHappy = inK; p.headRot = -8f * inK; p.blush = 0.5f * inK
            }
        }
    }
}

object MomentFx {
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun beam(k: Float): Pair<Float, Float> = com.combo.monkeycompanion.brain.PhoneMoments.beam(k)

    /** Charge effect in screen px from the character ([fx], [fy]) to the battery icon ([tx], [ty]). */
    fun drawCharge(c: Canvas, style: ChargeStyle, k: Float, fx: Float, fy: Float, tx: Float, ty: Float, d: Float, t: Float) {
        val (grow, a) = beam(k)
        if (a <= 0f) return
        val ex = fx + (tx - fx) * grow; val ey = fy + (ty - fy) * grow
        val al = (255 * a).toInt()
        when (style) {
            ChargeStyle.ZAP -> {
                path.reset(); path.moveTo(fx, fy)
                val n = 9; val len = hypot(ex - fx, ey - fy); val nx = -(ey - fy) / (len + 0.01f); val ny = (ex - fx) / (len + 0.01f)
                for (i in 1 until n) {
                    val f = i / n.toFloat()
                    val j = (if (i % 2 == 0) 1f else -1f) * (10f + 8f * sin(t * 40f + i)) * d
                    path.lineTo(fx + (ex - fx) * f + nx * j, fy + (ey - fy) * f + ny * j)
                }
                path.lineTo(ex, ey)
                stroke(c, Color.argb(al * 90 / 255, 0xFF, 0xE0, 0x40), 14f * d); stroke(c, Color.argb(al, 0xFF, 0xE0, 0x40), 6f * d); stroke(c, Color.argb(al, 255, 255, 255), 2.2f * d)
            }
            ChargeStyle.HEAT_BEAM -> {
                path.reset(); path.moveTo(fx - 4f * d, fy); path.lineTo(ex, ey)
                stroke(c, Color.argb(al * 90 / 255, 0xFF, 0x50, 0x20), 16f * d); stroke(c, Color.argb(al, 0xFF, 0x30, 0x10), 5f * d); stroke(c, Color.argb(al, 0xFF, 0xE0, 0xB0), 2f * d)
                path.reset(); path.moveTo(fx + 4f * d, fy); path.lineTo(ex, ey)
                stroke(c, Color.argb(al, 0xFF, 0x30, 0x10), 5f * d); stroke(c, Color.argb(al, 0xFF, 0xE0, 0xB0), 2f * d)
            }
            ChargeStyle.CABLE, ChargeStyle.GIANT_PLUG -> {
                val sag = 60f * d * (1f - 0.4f * grow)
                path.reset(); path.moveTo(fx, fy); path.quadTo((fx + ex) / 2f, maxOf(fy, ey) + sag, ex, ey)
                stroke(c, Color.argb(al, 0x30, 0x30, 0x36), (if (style == ChargeStyle.GIANT_PLUG) 7f else 5f) * d)
                stroke(c, Color.argb(al, 0x60, 0xD0, 0xFF), 1.6f * d)        // glowing core line
                val s = if (style == ChargeStyle.GIANT_PLUG) 2.2f else 1f
                fill.color = Color.argb(al, 0x40, 0x40, 0x48)
                c.drawRoundRect(RectF(ex - 9f * d * s, ey - 7f * d * s, ex + 9f * d * s, ey + 7f * d * s), 3f * d * s, 3f * d * s, fill)
                fill.color = Color.argb(al, 0xD8, 0xD8, 0xD8)
                c.drawRect(ex - 5f * d * s, ey - 14f * d * s, ex - 2f * d * s, ey - 7f * d * s, fill)
                c.drawRect(ex + 2f * d * s, ey - 14f * d * s, ex + 5f * d * s, ey - 7f * d * s, fill)
            }
        }
        if (grow >= 1f) {
            // Impact flash on the battery icon.
            val r = (16f + 6f * sin(t * 30f)) * d
            fill.color = Color.argb(al * 120 / 255, 0xFF, 0xF2, 0x80); c.drawCircle(tx, ty, r, fill)
            fill.color = Color.argb(al, 255, 255, 255); c.drawCircle(tx, ty, r * 0.35f, fill)
        }
    }

    /**
     * v6: a BIG jagged lightning bolt to the battery icon for every character (own colour per variant), with a glow,
     * a flickering second fork and spark rays at the icon. Drawn in Settai's own window (never needs the effects window).
     */
    fun drawBigBolt(c: Canvas, style: ChargeStyle, k: Float, fx: Float, fy: Float, tx: Float, ty: Float, d: Float, t: Float) {
        val (grow, a) = beam(k)
        if (a <= 0f) return
        val ex = fx + (tx - fx) * grow; val ey = fy + (ty - fy) * grow
        val (cr, cg, cb) = when (style) {
            ChargeStyle.ZAP -> Triple(0xFF, 0xE0, 0x30)
            ChargeStyle.HEAT_BEAM -> Triple(0xFF, 0x7A, 0x20)
            ChargeStyle.CABLE -> Triple(0x50, 0xC8, 0xFF)
            ChargeStyle.GIANT_PLUG -> Triple(0xC0, 0x70, 0xFF)
        }
        val flick = if (sin(t * 53f) > -0.6f) 1f else 0.55f
        val al = (255 * a * flick).toInt()
        val len = hypot(ex - fx, ey - fy); val nx = -(ey - fy) / (len + 0.01f); val ny = (ex - fx) / (len + 0.01f)
        for (fork in 0..1) {
            path.reset(); path.moveTo(fx, fy)
            val n = 7
            for (i in 1 until n) {
                val f = i / n.toFloat()
                val j = (if ((i + fork) % 2 == 0) 1f else -1f) * (16f + 10f * sin(t * 31f + i * 1.7f + fork)) * d * (1f - 0.4f * fork)
                path.lineTo(fx + (ex - fx) * f + nx * j, fy + (ey - fy) * f + ny * j)
            }
            path.lineTo(ex, ey)
            if (fork == 0) {
                stroke(c, Color.argb(al * 70 / 255, cr, cg, cb), 24f * d)
                stroke(c, Color.argb(al, cr, cg, cb), 9f * d)
                stroke(c, Color.argb(al, 255, 255, 255), 3.5f * d)
            } else if (sin(t * 19f) > 0f) {
                stroke(c, Color.argb(al * 160 / 255, cr, cg, cb), 4f * d); stroke(c, Color.argb(al, 255, 255, 255), 1.5f * d)
            }
        }
        if (grow >= 1f) {
            val r = (26f + 8f * sin(t * 30f)) * d
            fill.color = Color.argb(al * 110 / 255, cr, cg, cb); c.drawCircle(tx, ty, r, fill)
            fill.color = Color.argb(al, 255, 255, 255); c.drawCircle(tx, ty, r * 0.32f, fill)
            glow.strokeWidth = 3f * d; glow.color = Color.argb(al, cr, cg, cb)
            for (i in 0 until 8) {
                val ang = i * 0.785f + t * 6f; val r0 = r * 0.6f; val r1 = r * (1.2f + 0.4f * sin(t * 40f + i))
                c.drawLine(tx + kotlin.math.cos(ang) * r0, ty + sin(ang) * r0, tx + kotlin.math.cos(ang) * r1, ty + sin(ang) * r1, glow)
            }
        }
    }

    fun sparking(k: Float) = com.combo.monkeycompanion.brain.PhoneMoments.sparking(k)

    /** Headphones over the head: [cx], [top] = head top centre, [r] = head radius in px. */
    fun drawHeadphones(c: Canvas, cx: Float, top: Float, r: Float, a: Float) {
        val al = (255 * a).toInt(); if (al <= 0) return
        glow.color = Color.argb(al, 0x26, 0x26, 0x2E); glow.strokeWidth = r * 0.16f
        c.drawArc(RectF(cx - r * 1.05f, top + r * 0.05f, cx + r * 1.05f, top + r * 2.1f), 195f, 150f, false, glow)
        fill.color = Color.argb(al, 0xE5, 0x39, 0x35)
        c.drawRoundRect(RectF(cx - r * 1.22f, top + r * 0.8f, cx - r * 0.82f, top + r * 1.45f), r * 0.15f, r * 0.15f, fill)
        c.drawRoundRect(RectF(cx + r * 0.82f, top + r * 0.8f, cx + r * 1.22f, top + r * 1.45f), r * 0.15f, r * 0.15f, fill)
    }

    /** Wi-Fi signal arcs coming down into the raised hands at ([x], [y]). */
    fun drawWifi(c: Canvas, x: Float, y: Float, k: Float, d: Float) {
        val a = (1f - ((k - 0.85f) / 0.15f).coerceIn(0f, 1f)) * ((k - 0.05f) / 0.1f).coerceIn(0f, 1f)
        if (a <= 0f) return
        glow.strokeWidth = 4.5f * d
        for (i in 0 until 3) {
            val drop = ((k * 3f + i * 0.33f) % 1f)
            val r = (16f + i * 13f) * d
            val cy = y - 70f * d * (1f - drop)
            glow.color = Color.argb((220 * a * (1f - 0.25f * i)).toInt(), 0x21, 0x96, 0xF3)
            c.drawArc(RectF(x - r, cy - r, x + r, cy + r), 225f, 90f, false, glow)
        }
        fill.color = Color.argb((230 * a).toInt(), 0x21, 0x96, 0xF3); c.drawCircle(x, y - 70f * d + 4f * d, 4f * d, fill)
    }

    private fun stroke(c: Canvas, color: Int, w: Float) { glow.color = color; glow.strokeWidth = w; c.drawPath(path, glow) }
}
