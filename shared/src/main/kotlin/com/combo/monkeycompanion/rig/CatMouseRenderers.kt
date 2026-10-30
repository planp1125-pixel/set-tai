package com.combo.monkeycompanion.rig

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.combo.monkeycompanion.character.Wear
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.HEAD_CY
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.HEAD_R
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.LEG
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.TAIL_SEGS
import kotlin.math.sin

/**
 * v8.0 Original Cat-and-Mouse Duo Renderers & PeekFaces (zero Warner Bros / Tom & Jerry IP):
 *  - [BillaRenderer] & [BillaPeek]: "Billa", the plotting teal-grey street tabby cat with a notched left ear,
 *    dark tabby stripes, yellow slit-pupil eyes, red rowdy bandana, and cast-iron tawa prop.
 *  - [ChittuRenderer] & [ChittuPeek]: "Chittu", the clever cinnamon-brown tiny mouse with wide round pink-lined ears,
 *    bright red vest, golden masala cheese wedge, and fiery Guntur chilli prop.
 */
class BillaRenderer : MonkeyRenderer() {
    companion object {
        val CAT_FUR = Color.rgb(0x54, 0x6E, 0x7A)
        val CAT_LIGHT = Color.rgb(0x78, 0x90, 0x9C)
        val CAT_DARK = Color.rgb(0x37, 0x47, 0x4F)
        val STRIPE = Color.rgb(0x26, 0x32, 0x38)
        val CREAM = Color.rgb(0xEC, 0xEF, 0xF1)
        val PINK_NOSE = Color.rgb(0xF4, 0x8F, 0xB1)
        val YELLOW_EYE = Color.rgb(0xFF, 0xEB, 0x3B)
        val BANDANA = Color.rgb(0xD3, 0x2F, 0x2F)
    }

    private val catHeadShader = RadialGradient(-12f, -50f, 52f, intArrayOf(CAT_LIGHT, CAT_FUR, CAT_DARK), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    private val catTorso = Path().apply {
        moveTo(0f, -61f)
        cubicTo(16f, -61f, 25f, -44f, 26f, -22f)
        cubicTo(27f, -2f, 16f, 6f, 0f, 6f)
        cubicTo(-16f, 6f, -27f, -2f, -26f, -22f)
        cubicTo(-25f, -44f, -16f, -61f, 0f, -61f)
        close()
    }
    // Clean symmetric pointed cat ears from y=-52f to y=-78f, with a subtle outer nick on the left ear
    private val earL = Path().apply {
        moveTo(-29f, -50f)
        lineTo(-33f, -63f)
        lineTo(-30f, -65f) // subtle outer street nick (never sticks above ear tip)
        lineTo(-34f, -68f)
        lineTo(-36f, -78f)
        lineTo(-13f, -59f)
        close()
    }
    private val earLInner = Path().apply {
        moveTo(-26f, -53f)
        lineTo(-32f, -72f)
        lineTo(-16f, -58f)
        close()
    }
    private val earR = Path().apply {
        moveTo(13f, -59f)
        lineTo(36f, -78f)
        lineTo(29f, -50f)
        close()
    }
    private val earRInner = Path().apply {
        moveTo(16f, -58f)
        lineTo(32f, -72f)
        lineTo(26f, -53f)
        close()
    }

    override fun drawLegs(c: Canvas, rig: MonkeyRig) {
        limb(c, rig.legL, LEG, 10.5f, CAT_FUR, true); limb(c, rig.legR, LEG, 10.5f, CAT_FUR, true)
        limb(c, rig.legL, LEG, 10.5f, CAT_FUR, false); limb(c, rig.legR, LEG, 10.5f, CAT_FUR, false)
        // White cat paws
        blob(c, rig.legL, -2f, LEG + 2f, 8.5f, 5.5f, CREAM)
        blob(c, rig.legR, 2f, LEG + 2f, 8.5f, 5.5f, CREAM)
    }

    override fun drawTail(c: Canvas, rig: MonkeyRig) {
        tmp.reset()
        tmp.moveTo(rig.tailPts[0], rig.tailPts[1])
        for (i in 1..TAIL_SEGS) {
            val ix = i * 2
            tmp.lineTo(rig.tailPts[ix], rig.tailPts[ix + 1])
        }
        stroke.shader = null
        stroke.color = OUTLINE; stroke.strokeWidth = 12f + 2 * OW; c.drawPath(tmp, stroke)
        stroke.color = CAT_FUR; stroke.strokeWidth = 12f; c.drawPath(tmp, stroke)
        // Tabby tail rings
        fill.shader = null; fill.color = STRIPE
        for (i in 2 until TAIL_SEGS step 2) {
            c.drawCircle(rig.tailPts[i * 2], rig.tailPts[i * 2 + 1], 4.5f, fill)
        }
    }

    override fun drawTorso(c: Canvas, rig: MonkeyRig) {
        c.save(); c.concat(rig.hips)
        fill.shader = null; fill.color = CAT_FUR
        c.drawPath(catTorso, fill); c.drawPath(catTorso, outline)
        // Cream belly patch
        fill.color = CREAM
        oval.set(-15f, -46f, 15f, 0f)
        c.drawOval(oval, fill)
        // Tabby side stripes
        stroke.color = STRIPE; stroke.strokeWidth = 3f
        for (i in 0..2) {
            val y = -44f + i * 11f
            c.drawLine(-24f, y, -16f, y + 2f, stroke)
            c.drawLine(16f, y + 2f, 24f, y, stroke)
        }
        c.restore()
    }

    override fun drawTorsoExtras(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        if (has("bandana")) {
            paintScarf(c, rig, BANDANA, t)
        }
    }

    override fun drawArms(c: Canvas, rig: MonkeyRig) {
        limb(c, rig.armLUpper, rig.upperLen, 9.5f, CAT_FUR, true); limb(c, rig.armLFore, rig.foreLen, 8.8f, CAT_FUR, true)
        limb(c, rig.armRUpper, rig.upperLen, 9.5f, CAT_FUR, true); limb(c, rig.armRFore, rig.foreLen, 8.8f, CAT_FUR, true)
        limb(c, rig.armLUpper, rig.upperLen, 9.5f, CAT_FUR, false); limb(c, rig.armLFore, rig.foreLen, 8.8f, CAT_FUR, false)
        limb(c, rig.armRUpper, rig.upperLen, 9.5f, CAT_FUR, false); limb(c, rig.armRFore, rig.foreLen, 8.8f, CAT_FUR, false)
        blob(c, rig.armLFore, 0f, rig.foreLen + 2f, 7.5f, 7f, CREAM)
        blob(c, rig.armRFore, 0f, rig.foreLen + 2f, 7.5f, 7f, CREAM)
        // Only draw the tawa skillet when the right arm is not raised above shoulder/head height (< 75 deg)
        if (has("pan") && pose.armRRaise < 75f) {
            c.save(); c.concat(rig.armRFore); c.translate(0f, rig.foreLen + 4f)
            fill.shader = null; fill.color = Color.rgb(0x26, 0x2A, 0x30)
            c.drawRect(-3f, 0f, 3f, 16f, fill)
            c.drawCircle(0f, 26f, 13f, fill); c.drawCircle(0f, 26f, 13f, outline)
            c.restore()
        }
    }

    override fun drawBack(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        drawLegs(c, rig)
        c.save(); c.concat(rig.hips)
        fill.shader = null; fill.color = CAT_FUR
        c.drawPath(catTorso, fill); c.drawPath(catTorso, outline)
        stroke.shader = null; stroke.color = STRIPE; stroke.strokeWidth = 3f
        c.drawLine(0f, -44f, 0f, -12f, stroke)
        c.restore()
        c.save(); c.concat(rig.head)
        fill.shader = null; fill.color = CAT_FUR
        c.drawPath(earL, fill); c.drawPath(earL, outline)
        c.drawPath(earR, fill); c.drawPath(earR, outline)
        fill.shader = catHeadShader
        c.drawCircle(0f, HEAD_CY, HEAD_R, fill); fill.shader = null
        c.drawCircle(0f, HEAD_CY, HEAD_R, outline)
        c.restore()
        drawArms(c, rig)
        drawTail(c, rig)
    }

    var lastRenderedNeckHeight: Float = 0f; private set

    override fun drawHeadBase(c: Canvas, p: Pose, t: Float) {
        // Visible Cat Neck connecting torso collar (+4.5f) to chin (-5.5f)
        lastRenderedNeckHeight = 11.0f
        fill.shader = null; fill.color = CAT_FUR
        oval.set(-8.0f, -8.5f, 8.0f, 4.8f)
        c.drawRoundRect(oval, 3.5f, 3.5f, fill)
        fill.color = CREAM
        oval.set(-4.6f, -7.5f, 4.6f, 4.2f)
        c.drawRoundRect(oval, 2.5f, 2.5f, fill)
        outline.strokeWidth = 2.0f
        c.drawLine(-8.0f, -5.5f, -8.0f, 3.8f, outline)
        c.drawLine(8.0f, -5.5f, 8.0f, 3.8f, outline)
        outline.strokeWidth = OW

        // Pointed cat ears with pink inner triangles on both sides
        fill.shader = null; fill.color = CAT_FUR
        c.drawPath(earL, fill); c.drawPath(earL, outline)
        c.drawPath(earR, fill); c.drawPath(earR, outline)
        fill.color = PINK_NOSE
        c.drawPath(earLInner, fill)
        c.drawPath(earRInner, fill)

        // Head circle
        fill.shader = catHeadShader
        c.drawCircle(0f, HEAD_CY, HEAD_R, fill); fill.shader = null
        c.drawCircle(0f, HEAD_CY, HEAD_R, outline)

        // Subtle forehead tabby stripes neatly above the brows (y = -58f..-64f)
        stroke.color = STRIPE; stroke.strokeWidth = 2.4f
        c.drawLine(-9f, -58f, -5f, -64f, stroke)
        c.drawLine(0f, -57f, 0f, -64f, stroke)
        c.drawLine(9f, -58f, 5f, -64f, stroke)

        // Cream muzzle pad
        fill.color = CREAM
        oval.set(-22f, -32f, 22f, -8f)
        c.drawOval(oval, fill)

        // Whiskers (3 per side)
        stroke.color = Color.WHITE; stroke.strokeWidth = 1.8f
        val wag = 2f * sin(t * 7f)
        for (i in -1..1) {
            val wy = -23f + i * 4f
            c.drawLine(-16f, wy, -38f, wy + i * 3f + wag, stroke)
            c.drawLine(16f, wy, 38f, wy + i * 3f - wag, stroke)
        }
    }

    override fun drawEyes(c: Canvas, p: Pose) {
        for (s in intArrayOf(-1, 1)) {
            val ex = s * 12f + p.lookX
            val ey = -41f + p.lookY
            if (s < 0 && p.wink > 0.5f) {
                stroke.shader = null; stroke.color = EYE; stroke.strokeWidth = 2.4f
                c.drawLine(ex - 5f, ey, ex + 5f, ey, stroke)
                continue
            }
            if (p.eyesHappy > 0.5f) {
                stroke.shader = null; stroke.color = EYE; stroke.strokeWidth = 2.8f
                tmp.reset(); tmp.moveTo(ex - 6f, ey + 2f); tmp.quadTo(ex, ey - 7f, ex + 6f, ey + 2f)
                c.drawPath(tmp, stroke)
                continue
            }
            val open = p.eyeOpen
            val ry = 7f * open
            if (ry < 1.3f) {
                stroke.shader = null; stroke.color = EYE; stroke.strokeWidth = 2.4f
                c.drawLine(ex - 5f, ey, ex + 5f, ey, stroke)
                continue
            }
            val rx = 6.2f * (if (open > 1f) 1f + (open - 1f) * 0.45f else 1f)
            // Golden-yellow cat iris
            fill.shader = null; fill.color = YELLOW_EYE
            oval.set(ex - rx, ey - ry, ex + rx, ey + ry)
            c.drawOval(oval, fill)
            outline.strokeWidth = 1.5f; c.drawOval(oval, outline); outline.strokeWidth = OW
            // Vertical dark slit pupil
            fill.color = STRIPE
            val pw = if (open > 1.2f) 3.2f else 2.1f
            oval.set(ex - pw, ey - ry * 0.82f, ex + pw, ey + ry * 0.82f)
            c.drawOval(oval, fill)
            // Eye highlight glint
            fill.color = Color.WHITE
            c.drawCircle(ex + 1.8f, ey - ry * 0.38f, 1.8f, fill)
        }
    }

    override fun drawNose(c: Canvas, p: Pose) {
        fill.shader = null; fill.color = PINK_NOSE
        val nx = p.lookX * 0.8f
        tmp.reset()
        tmp.moveTo(nx - 5f, -29f)
        tmp.lineTo(nx + 5f, -29f)
        tmp.lineTo(nx, -23f)
        tmp.close()
        c.drawPath(tmp, fill)
    }
}

class ChittuRenderer : MonkeyRenderer() {
    companion object {
        val MOUSE_FUR = Color.rgb(0xA1, 0x66, 0x3E)
        val MOUSE_LIGHT = Color.rgb(0xC2, 0x85, 0x5A)
        val MOUSE_DARK = Color.rgb(0x6E, 0x42, 0x24)
        val CREAM = Color.rgb(0xFF, 0xEC, 0xD2)
        val VEST_RED = Color.rgb(0xE5, 0x39, 0x35)
        val CHEESE = Color.rgb(0xFF, 0xCA, 0x28)
        val CHEESE_HOLE = Color.rgb(0xF5, 0x7F, 0x17)
        val CHILLI = Color.rgb(0xD5, 0x00, 0x00)
    }

    private val mouseHeadShader = RadialGradient(-10f, -48f, 48f, intArrayOf(MOUSE_LIGHT, MOUSE_FUR, MOUSE_DARK), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)

    override fun drawLegs(c: Canvas, rig: MonkeyRig) {
        limb(c, rig.legL, LEG * 0.85f, 8.5f, MOUSE_FUR, true); limb(c, rig.legR, LEG * 0.85f, 8.5f, MOUSE_FUR, true)
        limb(c, rig.legL, LEG * 0.85f, 8.5f, MOUSE_FUR, false); limb(c, rig.legR, LEG * 0.85f, 8.5f, MOUSE_FUR, false)
        blob(c, rig.legL, -2f, LEG * 0.85f + 2f, 7.5f, 5f, CREAM)
        blob(c, rig.legR, 2f, LEG * 0.85f + 2f, 7.5f, 5f, CREAM)
    }

    override fun drawTail(c: Canvas, rig: MonkeyRig) {
        tmp.reset()
        tmp.moveTo(rig.tailPts[0], rig.tailPts[1])
        for (i in 1..TAIL_SEGS) {
            val ix = i * 2
            tmp.lineTo(rig.tailPts[ix], rig.tailPts[ix + 1])
        }
        stroke.shader = null
        stroke.color = OUTLINE; stroke.strokeWidth = 6f + 2 * OW; c.drawPath(tmp, stroke)
        stroke.color = Color.rgb(0xD7, 0x99, 0x82); stroke.strokeWidth = 6f; c.drawPath(tmp, stroke)
    }

    override fun drawTorso(c: Canvas, rig: MonkeyRig) {
        c.save(); c.concat(rig.hips); c.scale(0.88f, 0.88f)
        fill.shader = null; fill.color = MOUSE_FUR
        oval.set(-22f, -58f, 22f, 5f)
        c.drawOval(oval, fill); c.drawOval(oval, outline)
        fill.color = CREAM
        oval.set(-14f, -46f, 14f, 1f)
        c.drawOval(oval, fill)
        if (has("vest")) {
            fill.color = VEST_RED
            c.drawRoundRect(RectF(-21f, -52f, -7f, -12f), 6f, 6f, fill)
            c.drawRoundRect(RectF(7f, -52f, 21f, -12f), 6f, 6f, fill)
            fill.color = BADGE_GOLD
            c.drawCircle(-11f, -28f, 2.5f, fill)
        }
        c.restore()
    }

    override fun drawArms(c: Canvas, rig: MonkeyRig) {
        limb(c, rig.armLUpper, rig.upperLen, 8f, MOUSE_FUR, true); limb(c, rig.armLFore, rig.foreLen, 7.5f, MOUSE_FUR, true)
        limb(c, rig.armRUpper, rig.upperLen, 8f, MOUSE_FUR, true); limb(c, rig.armRFore, rig.foreLen, 7.5f, MOUSE_FUR, true)
        limb(c, rig.armLUpper, rig.upperLen, 8f, MOUSE_FUR, false); limb(c, rig.armLFore, rig.foreLen, 7.5f, MOUSE_FUR, false)
        limb(c, rig.armRUpper, rig.upperLen, 8f, MOUSE_FUR, false); limb(c, rig.armRFore, rig.foreLen, 7.5f, MOUSE_FUR, false)
        blob(c, rig.armLFore, 0f, rig.foreLen + 2f, 6.5f, 6f, CREAM)
        blob(c, rig.armRFore, 0f, rig.foreLen + 2f, 6.5f, 6f, CREAM)
        // Only draw hand props when that arm is not raised above shoulder/head height (< 75 deg)
        if (has("cheese") && pose.armRRaise < 75f) {
            c.save(); c.concat(rig.armRFore); c.translate(0f, rig.foreLen + 6f)
            fill.shader = null; fill.color = CHEESE
            tmp.reset(); tmp.moveTo(-11f, 8f); tmp.lineTo(12f, 8f); tmp.lineTo(2f, -9f); tmp.close()
            c.drawPath(tmp, fill); c.drawPath(tmp, outline)
            fill.color = CHEESE_HOLE
            c.drawCircle(-2f, 3f, 2.2f, fill); c.drawCircle(4f, 4f, 1.8f, fill)
            c.restore()
        }
        if (has("chilli") && pose.armLRaise < 75f) {
            c.save(); c.concat(rig.armLFore); c.translate(0f, rig.foreLen + 5f)
            fill.shader = null; fill.color = CHILLI
            tmp.reset(); tmp.moveTo(-3f, -2f); tmp.quadTo(12f, 4f, 14f, 16f); tmp.quadTo(2f, 10f, -3f, -2f); tmp.close()
            c.drawPath(tmp, fill)
            stroke.color = Color.rgb(0x2E, 0x7D, 0x32); stroke.strokeWidth = 2.2f
            c.drawLine(-3f, -2f, -6f, -7f, stroke)
            c.restore()
        }
    }

    override fun drawBack(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        drawLegs(c, rig)
        drawTorso(c, rig)
        c.save(); c.concat(rig.head)
        fill.shader = null
        for (s in intArrayOf(-1, 1)) {
            val ex = s * 30f; val ey = -48f
            fill.color = MOUSE_FUR
            c.drawCircle(ex, ey, 16.5f, fill); c.drawCircle(ex, ey, 16.5f, outline)
        }
        fill.shader = mouseHeadShader
        c.drawCircle(0f, HEAD_CY, HEAD_R * 0.92f, fill); fill.shader = null
        c.drawCircle(0f, HEAD_CY, HEAD_R * 0.92f, outline)
        c.restore()
        drawArms(c, rig)
        drawTail(c, rig)
    }

    var lastRenderedNeckHeight: Float = 0f; private set

    override fun drawHeadBase(c: Canvas, p: Pose, t: Float) {
        // Clear Visible Cylindrical Neck for Chittu (Chottu) connecting scaled torso (+6.5f) up to chin (-6.5f)
        lastRenderedNeckHeight = 12.5f
        fill.shader = null
        fill.color = MOUSE_FUR
        oval.set(-7.0f, -9.5f, 7.0f, 7.2f)
        c.drawRoundRect(oval, 3.5f, 3.5f, fill)
        fill.color = CREAM
        oval.set(-4.2f, -8.5f, 4.2f, 6.6f)
        c.drawRoundRect(oval, 2.5f, 2.5f, fill)
        // Soft curved sub-mental chin shadow on top of the neck
        fill.color = MOUSE_DARK
        oval.set(-7.0f, -9.5f, 7.0f, -3.8f)
        c.drawRoundRect(oval, 2.5f, 2.5f, fill)
        // Crisp left & right neck outline strokes + neat red collar rim at base
        outline.strokeWidth = 2.0f
        c.drawLine(-7.0f, -5.8f, -7.0f, 5.8f, outline)
        c.drawLine(7.0f, -5.8f, 7.0f, 5.8f, outline)
        outline.strokeWidth = OW
        if (has("vest")) {
            fill.color = VEST_RED
            oval.set(-8.5f, 3.8f, 8.5f, 7.5f)
            c.drawRoundRect(oval, 2.5f, 2.5f, fill)
            fill.color = BADGE_GOLD
            c.drawCircle(0f, 5.6f, 2.1f, fill)
        }

        // Cute round mouse ears positioned cleanly on the sides of the head (ex = +/-30f, ey = -50f, r = 16.5f)
        fill.shader = null
        for (s in intArrayOf(-1, 1)) {
            val ex = s * 30f; val ey = -50f
            fill.color = MOUSE_FUR
            c.drawCircle(ex, ey, 16.5f, fill); c.drawCircle(ex, ey, 16.5f, outline)
            fill.color = Color.rgb(0xF4, 0xA8, 0xB8)
            c.drawCircle(ex, ey, 10.5f, fill)
        }
        // Head circle raised slightly (-36.5f, r = 30.2f -> bottom at -6.3f) so the neck (-6.3f..+5.8f) is clearly visible!
        fill.shader = mouseHeadShader
        c.drawCircle(0f, -36.5f, HEAD_R * 0.89f, fill); fill.shader = null
        c.drawCircle(0f, -36.5f, HEAD_R * 0.89f, outline)

        // Cream cheeks & snout
        fill.color = CREAM
        oval.set(-20f, -34f, 20f, -8f)
        c.drawOval(oval, fill)

        // Twitching mouse whiskers
        stroke.color = OUTLINE; stroke.strokeWidth = 1.6f
        val tw = 3f * sin(t * 14f)
        for (i in -1..1) {
            val wy = -23f + i * 3.5f
            c.drawLine(-14f, wy, -33f, wy + i * 2.5f + tw, stroke)
            c.drawLine(14f, wy, 33f, wy + i * 2.5f - tw, stroke)
        }
    }

    override fun drawNose(c: Canvas, p: Pose) {
        fill.shader = null; fill.color = Color.rgb(0xE9, 0x1E, 0x63)
        c.drawCircle(p.lookX * 1.1f, -26.5f, 4.2f, fill)
        fill.color = Color.WHITE
        c.drawCircle(p.lookX * 1.1f - 1.2f, -27.8f, 1.2f, fill)
    }
}

object BillaPeek : PeekFace("billa") {
    override val wardrobeShown = setOf("shades")
    override fun drawFace(c: Canvas, wear: Wear?, s: State) {
        val tw = s.tw; val cy = s.cy
        // Pointed cat ear
        fill(BillaRenderer.CAT_FUR)
        path.reset()
        path.moveTo(-0.15f * tw, cy - 0.5f * tw)
        path.lineTo(-0.05f * tw, cy - 0.98f * tw)
        path.lineTo(0.18f * tw, cy - 0.55f * tw)
        path.close()
        c.drawPath(path, paint)
        // Tabby head
        c.drawCircle(0.02f * tw, cy, tw * 0.62f, paint)
        // Tabby stripes
        stroke(BillaRenderer.STRIPE, 0.06f * tw)
        c.drawLine(0.08f * tw, cy - 0.35f * tw, 0.22f * tw, cy - 0.25f * tw, paint)
        c.drawLine(0.08f * tw, cy + 0.35f * tw, 0.22f * tw, cy + 0.25f * tw, paint)
        // Glowing yellow cat eyes + dark slit pupils
        fill(BillaRenderer.YELLOW_EYE)
        for (i in 0..1) c.drawCircle(tw * 0.28f, eyeY(s, i), tw * 0.11f, paint)
        dotEyes(c, s, tw * 0.30f, tw * 0.055f, BillaRenderer.STRIPE)
        if (wear?.has("shades") == true) shades(c, s, tw * 0.28f)
    }
}

object ChittuPeek : PeekFace("chittu") {
    override val wardrobeShown = setOf("vest")
    override fun drawFace(c: Canvas, wear: Wear?, s: State) {
        val tw = s.tw; val cy = s.cy
        // Round mouse ear
        fill(ChittuRenderer.MOUSE_FUR)
        c.drawCircle(-0.02f * tw, cy - 0.72f * tw, tw * 0.32f, paint)
        fill(Color.rgb(0xF4, 0xA8, 0xB8))
        c.drawCircle(-0.02f * tw, cy - 0.72f * tw, tw * 0.20f, paint)
        // Mouse head + cream cheek
        fill(ChittuRenderer.MOUSE_FUR)
        c.drawCircle(0f, cy, tw * 0.56f, paint)
        fill(ChittuRenderer.CREAM)
        c.drawCircle(0.12f * tw, cy, tw * 0.38f, paint)
        if (wear?.has("vest") == true) {
            fill(ChittuRenderer.VEST_RED)
            c.drawRect(-0.1f * tw, cy + 0.38f * tw, 0.18f * tw, cy + 0.6f * tw, paint)
        }
        dotEyes(c, s, tw * 0.26f, tw * 0.08f, Color.rgb(0x24, 0x16, 0x0C))
        // Pink button nose at tip
        fill(Color.rgb(0xE9, 0x1E, 0x63))
        c.drawCircle(tw * 0.48f, cy, tw * 0.09f, paint)
    }
}
