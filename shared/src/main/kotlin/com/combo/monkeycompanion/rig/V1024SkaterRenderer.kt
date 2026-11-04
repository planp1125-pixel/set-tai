package com.combo.monkeycompanion.rig

import android.graphics.Canvas
import android.graphics.Color
import com.combo.monkeycompanion.character.Wear
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.LEG
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * v10.24 Skater Girl: an ORIGINAL cute chibi girl (same human style as Surfing Girl) with dark brown curly hair, a
 * mint-green tee under an open lilac hoodie-jacket, denim shorts, pink-and-white high-top sneakers, on a coral
 * skateboard with yellow wheels. Pose slots she reads: dvAura = ride on the board (0 = carrying it), dvSpell = kickflip
 * roll (0..1 = one full turn of the board), surfSpray = grind sparks. No RNG.
 */
class SkaterRenderer : V1014BodyRenderer() {
    companion object {
        val SKIN = Color.rgb(0xE8, 0xB0, 0x86)
        val SKIN_SHADE = Color.rgb(0xC2, 0x86, 0x60)
        val HAIR = Color.rgb(0x3B, 0x22, 0x14)
        val HAIR_LIGHT = Color.rgb(0x5A, 0x36, 0x22)
        val TEE = Color.rgb(0x7E, 0xE0, 0xC0)
        val JACKET = Color.rgb(0xB6, 0x8C, 0xF0)
        val JACKET_DARK = Color.rgb(0x8A, 0x62, 0xC8)
        val DENIM = Color.rgb(0x4C, 0x78, 0xC8)
        val SHOE = Color.rgb(0xFF, 0x7E, 0xA8)
        val DECK = Color.rgb(0xFF, 0x6B, 0x5A)
        val DECK_DARK = Color.rgb(0xC9, 0x44, 0x38)
        val WHEEL = Color.rgb(0xFF, 0xD2, 0x3F)
        val CHEEK = Color.rgb(0xFF, 0x80, 0x8A)
        /** Curls on her head (x, y, r) in head space: a big soft cloud of ringlets. */
        val CURLS = floatArrayOf(-30f, -50f, 11f, -22f, -64f, 12f, -9f, -71f, 12f, 6f, -71f, 12f, 20f, -65f, 12f, 30f, -52f, 11f,
            -36f, -36f, 9f, 36f, -37f, 9f, -38f, -22f, 8f, 38f, -23f, 8f, -14f, -60f, 9f, 13f, -60f, 9f)
    }
    override val skin = SKIN
    override val skinShade = SKIN_SHADE
    override val hair = HAIR

    override fun drawJets(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) = Unit

    override fun drawBehind(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        // curls at the back of the head (behind the body)
        c.save(); c.concat(rig.head)
        fill.shader = null; fill.color = HAIR
        for (i in 0 until 4) { val x = CURLS[i * 3 + 18]; val y = CURLS[i * 3 + 19]; c.drawCircle(x, y + 8f, CURLS[i * 3 + 20] + 2f, fill) }
        c.restore()
        pushBody(c, rig, p)
        if (p.dvAura > 0.05f) drawBoard(c, rig, p, t)
    }

    /** Board under her feet in hips space; a kickflip rolls it (squash across its width) and spins it under her. */
    private fun drawBoard(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        c.save(); c.concat(rig.hips)
        val y = -MonkeyRig.HIP_Y + 6f
        val roll = p.dvSpell.coerceIn(0f, 1f) * 2f * Math.PI.toFloat()
        val face = cos(roll)                       // 1 = grip tape up, -1 = graphic up
        val lift = 10f * sin(roll / 2f)            // the board pops up under her feet while it flips
        c.translate(0f, y - lift)
        c.scale(1f, if (abs(face) < 0.15f) 0.15f else abs(face))
        // deck
        fill.shader = null; fill.color = if (face >= 0f) Color.rgb(0x2E, 0x2A, 0x34) else DECK
        tmp.reset(); tmp.moveTo(-44f, -3f); tmp.quadTo(-52f, -9f, -50f, -1f); tmp.quadTo(-48f, 4f, -40f, 4f)
        tmp.lineTo(40f, 4f); tmp.quadTo(48f, 4f, 50f, -1f); tmp.quadTo(52f, -9f, 44f, -3f); tmp.close()
        c.drawPath(tmp, fill); c.drawPath(tmp, outline)
        fill.color = DECK_DARK; c.drawRect(-40f, 2f, 40f, 4.5f, fill)   // deck edge (coral side)
        if (face < 0f) { fill.color = WHEEL; c.drawCircle(0f, 0f, 3f, fill) }   // a star on the graphic side
        // trucks + wheels (spin with time while riding)
        fill.color = Color.rgb(0x9A, 0x9A, 0xA6)
        c.drawRect(-30f, 4f, -20f, 7f, fill); c.drawRect(20f, 4f, 30f, 7f, fill)
        for (x in floatArrayOf(-25f, 25f)) {
            fill.color = WHEEL; c.drawCircle(x, 10f, 4.6f, fill); c.drawCircle(x, 10f, 4.6f, outline)
            stroke.shader = null; stroke.color = Color.rgb(0xC9, 0x9A, 0x10); stroke.strokeWidth = 1.2f
            val a = t * 18f; c.drawLine(x, 10f, x + 3.4f * cos(a), 10f + 3.4f * sin(a), stroke)
        }
        c.restore()
        if (p.surfSpray > 0.05f) {   // grind sparks off the back truck
            c.save(); c.concat(rig.hips)
            fill.color = Color.rgb(0xFF, 0xB0, 0x30)
            for (i in 0 until 6) {
                val ph = (t * 3f + i / 6f) % 1f
                fill.alpha = (255 * p.surfSpray * (1f - ph)).toInt()
                c.drawCircle(-30f - 26f * ph, -MonkeyRig.HIP_Y + 14f - 12f * ph * sin(i * 1.3f + 1f), 2.2f * (1f - ph) + 0.8f, fill)
            }
            fill.alpha = 255; c.restore()
        }
    }

    override fun drawLegs(c: Canvas, rig: MonkeyRig) {
        limb(c, rig.legL, LEG, 9f, SKIN, true); limb(c, rig.legR, LEG, 9f, SKIN, true)
        limb(c, rig.legL, LEG, 9f, SKIN, false); limb(c, rig.legR, LEG, 9f, SKIN, false)
        for (m in arrayOf(rig.legL, rig.legR)) {   // high-top sneakers
            blob(c, m, 3f, LEG + 1f, 9f, 6f, SHOE)
            c.save(); c.concat(m); fill.shader = null; fill.color = Color.WHITE; c.drawRect(-6f, LEG + 4f, 12f, LEG + 7f, fill)
            stroke.shader = null; stroke.color = Color.WHITE; stroke.strokeWidth = 1.4f; c.drawLine(-2f, LEG - 3f, 6f, LEG - 3f, stroke); c.restore()
        }
    }

    override fun drawTorso(c: Canvas, rig: MonkeyRig) {
        c.save(); c.concat(rig.hips); c.scale(0.84f, 1f)
        fill.shader = null; fill.color = TEE; c.drawPath(suit, fill)
        fill.color = DENIM; c.drawRect(-23f, -12f, 23f, 7f, fill); c.drawRect(-23f, -12f, 23f, 7f, outline)
        // open hoodie-jacket panels on both sides
        fill.color = JACKET
        tmp.reset(); tmp.moveTo(-8f, -58f); tmp.cubicTo(-18f, -57f, -24f, -44f, -24f, -26f); tmp.lineTo(-23f, -10f); tmp.lineTo(-9f, -10f); tmp.close(); c.drawPath(tmp, fill)
        tmp.reset(); tmp.moveTo(8f, -58f); tmp.cubicTo(18f, -57f, 24f, -44f, 24f, -26f); tmp.lineTo(23f, -10f); tmp.lineTo(9f, -10f); tmp.close(); c.drawPath(tmp, fill)
        c.drawPath(suit, outline)
        stroke.shader = null; stroke.color = JACKET_DARK; stroke.strokeWidth = 2f; c.drawLine(-9f, -56f, -9f, -11f, stroke); c.drawLine(9f, -56f, 9f, -11f, stroke)
        fill.color = Color.rgb(0xFF, 0xD2, 0x3F); c.drawCircle(0f, -32f, 4f, fill)   // a little star print on the tee
        c.restore()
    }

    override fun drawArms(c: Canvas, rig: MonkeyRig) {
        limb(c, rig.armLUpper, rig.upperLen, 8.5f, JACKET, true); limb(c, rig.armLFore, rig.foreLen, 7f, SKIN, true)
        limb(c, rig.armRUpper, rig.upperLen, 8.5f, JACKET, true); limb(c, rig.armRFore, rig.foreLen, 7f, SKIN, true)
        limb(c, rig.armLUpper, rig.upperLen, 8.5f, JACKET, false); limb(c, rig.armLFore, rig.foreLen, 7f, SKIN, false)
        limb(c, rig.armRUpper, rig.upperLen, 8.5f, JACKET, false); limb(c, rig.armRFore, rig.foreLen, 7f, SKIN, false)
        blob(c, rig.armLFore, 0f, rig.foreLen + 1.5f, 5f, 5f, SKIN)
        blob(c, rig.armRFore, 0f, rig.foreLen + 1.5f, 5f, 5f, SKIN)
    }

    /** Round face + a cloud of dark brown curls (each ringlet has a lighter curl line). */
    override fun drawHeadBase(c: Canvas, p: Pose, t: Float) {
        humanHead(c, withHair = false)
        fill.shader = null
        var i = 0
        while (i < CURLS.size) {
            val x = CURLS[i]; val y = CURLS[i + 1]; val r = CURLS[i + 2]
            if (y < -40f || i < 18) {
                fill.color = HAIR; c.drawCircle(x, y, r, fill)
                stroke.shader = null; stroke.color = HAIR_LIGHT; stroke.strokeWidth = 1.6f
                oval.set(x - r * 0.55f, y - r * 0.55f, x + r * 0.55f, y + r * 0.55f); c.drawArc(oval, 200f, 220f, false, stroke)
            }
            i += 3
        }
        outline.strokeWidth = 1.4f
        for (j in 0 until 6) { val x = CURLS[j * 3]; val y = CURLS[j * 3 + 1]; oval.set(x - CURLS[j * 3 + 2], y - CURLS[j * 3 + 2], x + CURLS[j * 3 + 2], y + CURLS[j * 3 + 2]); c.drawArc(oval, 150f, 240f, false, outline) }
        outline.strokeWidth = OW
    }
    override fun drawCheeks(c: Canvas, p: Pose) {
        fill.shader = null; fill.color = CHEEK; fill.alpha = 150
        c.drawCircle(-20f, -27f, 5f, fill); c.drawCircle(20f, -27f, 5f, fill); fill.alpha = 255
    }

    override fun drawFront(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) { popBody(c) }
}

object SkaterPeek : PeekFace("skater") {
    override fun drawFace(c: Canvas, wear: Wear?, s: State) {
        val tw = s.tw; val cy = s.cy
        val bob = if (s.peeking) sin(s.t * 6f) * 0.08f * tw else 0f
        fill(SkaterRenderer.HAIR)
        for (k in -2..2) c.drawCircle(-0.42f * tw, cy + k * 0.22f * tw + bob, 0.2f * tw, paint)
        fill(SkaterRenderer.SKIN); r.set(-0.44f * tw, cy - 0.5f * tw + bob, 0.44f * tw, cy + 0.45f * tw + bob); c.drawOval(r, paint)
        for (i in 0..1) {
            val y = eyeY(s, i) + bob
            fill(Color.rgb(0x2A, 0x18, 0x10)); c.drawCircle(0.25f * tw + s.look * 0.5f, y, if (s.blink) 0.02f * tw else 0.08f * tw, paint)
        }
    }
}
