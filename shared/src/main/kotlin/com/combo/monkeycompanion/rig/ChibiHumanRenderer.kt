package com.combo.monkeycompanion.rig

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.combo.monkeycompanion.character.AgeGroup
import com.combo.monkeycompanion.character.CompanionGender
import com.combo.monkeycompanion.character.BodyBuild
import com.combo.monkeycompanion.character.CustomCharacter
import com.combo.monkeycompanion.character.CustomCompanionSpec
import com.combo.monkeycompanion.character.EyeStyle
import com.combo.monkeycompanion.character.FaceMode
import com.combo.monkeycompanion.character.FaceShape
import com.combo.monkeycompanion.character.FacialHairStyle
import com.combo.monkeycompanion.character.HairStyle
import com.combo.monkeycompanion.character.OutfitStyle
import com.combo.monkeycompanion.character.Wear
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.HEAD_CY
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.HEAD_R
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.LEG
import com.combo.monkeycompanion.rig.MonkeyRig.Companion.SHOULDER_Y
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object ChibiFaceBridge {
    @Volatile
    var androidBitmapDrawer: ((spec: CustomCompanionSpec, c: Canvas, dst: RectF, alpha: Int) -> Boolean)? = null

    fun drawPortrait(spec: CustomCompanionSpec, c: Canvas, dst: RectF, p: Paint, clipPath: Path, alpha: Int = 255): Boolean {
        val drewNative = androidBitmapDrawer?.invoke(spec, c, dst, alpha) == true
        if (drewNative) return true
        return drawColorGrid(spec.faceGridColors, c, dst, p, clipPath, alpha)
    }

    fun drawColorGrid(grid: List<Int>, c: Canvas, dst: RectF, p: Paint, clipPath: Path, alpha: Int = 255): Boolean {
        if (grid.isEmpty()) return false
        val n = sqrt(grid.size.toDouble()).toInt().coerceAtLeast(1)
        if (n * n > grid.size) return false
        c.save()
        c.clipPath(clipPath)
        val cw = dst.width() / n
        val ch = dst.height() / n
        p.style = Paint.Style.FILL
        p.shader = null
        for (gy in 0 until n) {
            for (gx in 0 until n) {
                p.color = grid[gy * n + gx]
                p.alpha = alpha
                c.drawRect(
                    dst.left + gx * cw - 0.4f,
                    dst.top + gy * ch - 0.4f,
                    dst.left + (gx + 1) * cw + 0.4f,
                    dst.top + (gy + 1) * ch + 0.4f,
                    p
                )
            }
        }
        p.alpha = 255
        c.restore()
        return true
    }
}

class ChibiHumanRenderer(private val customChar: CustomCharacter) : MonkeyRenderer() {
    val spec: CustomCompanionSpec get() = customChar.spec

    // Observable metrics for unit tests
    var lastHairSwingAngle: Float = 0f; private set
    var lastHairBounceY: Float = 0f; private set
    var lastHemWave: Float = 0f; private set
    var lastBlinkAmount: Float = 1f; private set
    var lastRenderedFaceMode: FaceMode = FaceMode.CHIBI_TWIN; private set
    var realFaceCutoutDrawnCount: Int = 0; private set
    var lastRenderedTorsoWidth: Float = 0f; private set
    var lastRenderedShoulderWidth: Float = 0f; private set
    var lastRenderedFacialHair: FacialHairStyle = FacialHairStyle.NONE; private set
    var lastRenderedNeckHeight: Float = 0f; private set
    var lastRenderedGender: CompanionGender = CompanionGender.MALE; private set
    var lastRenderedEyeY: Float = -39.5f; private set
    var lastRenderedMouthY: Float = -17.5f; private set
    var lastRenderedChinY: Float = -6.8f; private set
    var lastRenderedFaceRx: Float = 25.8f; private set
    var lastRenderedHairlineY: Float = -57.0f; private set

    private var prevRootX = Float.NaN
    private var prevRootY = Float.NaN
    private var prevT = Float.NaN
    private var hairVelAngle = 0f
    private var hairVelY = 0f

    private var cachedSkinColor = 0
    private var skinShader: RadialGradient? = null
    private val faceClipPath = Path()
    private val faceDstRect = RectF()
    private val headShapePath = Path()
    private val torsoPath = Path()
    private val beardPath = Path()
    private val stachePath = Path()
    private val browPath = Path()
    private val eyePath = Path()
    private val hairPath = Path()
    private val wingPath = Path()

    override val capeFill: Int
        get() = when (spec.outfitStyle) {
            OutfitStyle.SUPER_SUIT -> spec.accentColor
            OutfitStyle.IRON_ARMOUR_SUIT -> Color.rgb(0xC6, 0x28, 0x28)
            else -> Color.rgb(0xE5, 0x39, 0x35)
        }
    override val capeShade: Int
        get() = darken(capeFill, 0.65f)

    override fun drawTail(c: Canvas, rig: MonkeyRig) {}

    private fun showAcc(id: String, specDefault: Boolean): Boolean = specDefault

    private fun hasRealHeadPortrait(): Boolean =
        spec.faceMode != FaceMode.CHIBI_TWIN &&
            (spec.facePortraitBase64.isNotBlank() || spec.faceGridColors.isNotEmpty())

    private fun limbThick(): Float =
        spec.bodyBuild.limbThicknessScale * (0.7f + 0.3f * spec.bodyWidthScale).coerceIn(0.75f, 1.45f)

    private fun stepSecondaryPhysics(rig: MonkeyRig, p: Pose, t: Float) {
        pts[0] = 0f; pts[1] = 0f
        rig.hips.mapPoints(pts)
        val rx = pts[0]; val ry = pts[1]
        val dt = if (prevT.isNaN() || t <= prevT) 0.016f else (t - prevT).coerceIn(0.004f, 0.05f)
        val vx = if (prevRootX.isNaN()) 0f else ((rx - prevRootX) / dt).coerceIn(-900f, 900f)
        val vy = if (prevRootY.isNaN()) 0f else ((ry - prevRootY) / dt).coerceIn(-900f, 900f)
        prevRootX = rx; prevRootY = ry; prevT = t

        val stride = abs(p.legLSwing - p.legRSwing)
        val wind = p.capeWind + (if (p.jets > 0.05f || p.rocket > 0.05f) 0.8f else 0f)
        val kovamLift = if (p.anger > 0.2f || p.size > 1.05f) -14f else 0f

        val targetSwing = (-vx * 0.028f - p.headRot * 0.45f - p.bodyRot * 0.35f +
            sin(t * 5.2f) * (3.5f + stride * 0.22f + wind * 14f) +
            (anim?.appendageAngle(com.combo.monkeycompanion.engine.anim.Appendage.HAIR) ?: 0f)).coerceIn(-36f, 36f)
        val targetBounce = (-vy * 0.016f + sin(t * 9.5f) * (1.2f + stride * 0.14f) + kovamLift).coerceIn(-16f, 14f)

        val stiffness = 140f
        val damping = 14f
        hairVelAngle += ((targetSwing - lastHairSwingAngle) * stiffness - hairVelAngle * damping) * dt
        lastHairSwingAngle = (lastHairSwingAngle + hairVelAngle * dt).coerceIn(-38f, 38f)

        hairVelY += ((targetBounce - lastHairBounceY) * stiffness - hairVelY * damping) * dt
        lastHairBounceY = (lastHairBounceY + hairVelY * dt).coerceIn(-18f, 16f)

        lastHemWave = sin(t * 6.8f) * (2.2f + stride * 0.18f + wind * 6f) + (vx * 0.008f).coerceIn(-6f, 6f)
    }

    override fun drawBehind(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        stepSecondaryPhysics(rig, p, t)

        // Draw back hair BEHIND torso, neck, and head so long hair NEVER covers the neck, collar, chin, or jaw!
        if (!hasRealHeadPortrait() && p.backView <= 0.5f) {
            c.save()
            c.concat(rig.head)
            drawBackHair(c, p, t)
            c.restore()
        }

        if (p.anger > 0.15f || p.size > 1.02f) {
            val intensity = max(p.anger, ((p.size - 1f) * 3f).coerceIn(0f, 1f)).coerceIn(0.2f, 1f)
            val bw = spec.bodyWidthScale.coerceIn(0.8f, 1.5f)
            c.save(); c.concat(rig.hips)
            for (layer in 0..1) {
                fill.shader = null
                fill.color = if (layer == 0) Color.argb((155 * intensity).toInt(), 0xFF, 0x57, 0x22)
                else Color.argb((195 * intensity).toInt(), 0xFF, 0xEB, 0x3B)
                val sc = (if (layer == 0) 1.22f else 0.90f) * bw
                hairPath.reset()
                hairPath.moveTo(-30f * sc, 10f)
                for (i in 0..6) {
                    val ang = Math.PI + (i / 6.0) * Math.PI
                    val r = (45f + 10f * sin(t * 22f + i * 1.7f + layer)) * sc
                    val px = (cos(ang) * r).toFloat()
                    val py = -32f + (sin(ang) * r * 1.25f).toFloat()
                    hairPath.lineTo(px, py)
                }
                hairPath.lineTo(30f * sc, 10f)
                hairPath.close()
                c.drawPath(hairPath, fill)
            }
            fill.alpha = 255
            c.restore()
        }

        if (spec.outfitStyle == OutfitStyle.NINJA_SHINOBI_SUIT || p.ninja > 0.05f) {
            c.save(); c.concat(rig.hips)
            stroke.shader = null; stroke.color = Color.rgb(0x21, 0x21, 0x21); stroke.strokeWidth = 5.5f
            c.drawLine(-14f, -66f, 22f, -12f, stroke)
            stroke.color = spec.accentColor; stroke.strokeWidth = 3f
            c.drawLine(-20f, -75f, -12f, -63f, stroke)
            c.restore()
        }

        if (showAcc("wings", spec.wings)) {
            c.save(); c.concat(rig.hips)
            val flap = 0.72f + 0.28f * sin(t * 11f)
            for (side in intArrayOf(-1, 1)) {
                c.save()
                c.translate(side * 10f * spec.shoulderScale, SHOULDER_Y + 4f)
                c.scale(side * flap, 1f)
                wingPath.reset()
                wingPath.moveTo(0f, 0f)
                wingPath.cubicTo(22f, -28f, 52f, -24f, 46f, 2f)
                wingPath.cubicTo(42f, 16f, 18f, 12f, 0f, 6f)
                wingPath.close()
                fill.shader = null
                fill.color = Color.argb(185, Color.red(spec.accentColor), Color.green(spec.accentColor), Color.blue(spec.accentColor))
                c.drawPath(wingPath, fill)
                stroke.shader = null; stroke.color = Color.WHITE; stroke.strokeWidth = 1.6f
                c.drawPath(wingPath, stroke)
                c.restore()
            }
            c.restore()
        }
    }

    override fun drawLegs(c: Canvas, rig: MonkeyRig) {
        val skin = spec.skinColor
        val outCol = spec.outfitColor
        val pantsCol = spec.pantsColor
        val accCol = spec.accentColor
        val lt = 10.5f * limbThick()
        val footRx = (8.8f * limbThick()).coerceIn(7.5f, 12.5f)

        when (spec.outfitStyle) {
            OutfitStyle.CASUAL_SHIRT_JEANS, OutfitStyle.TSHIRT_CARGO, OutfitStyle.HOODIE_JEANS -> {
                limb(c, rig.legL, LEG, lt, pantsCol, true); limb(c, rig.legR, LEG, lt, pantsCol, true)
                limb(c, rig.legL, LEG, lt, pantsCol, false); limb(c, rig.legR, LEG, lt, pantsCol, false)
                val shoeCol = if (spec.outfitStyle == OutfitStyle.CASUAL_SHIRT_JEANS) Color.rgb(0x3E, 0x27, 0x23) else Color.rgb(0x37, 0x47, 0x4F)
                blob(c, rig.legL, -2f, LEG + 2.5f, footRx, 6f, shoeCol)
                blob(c, rig.legR, 2f, LEG + 2.5f, footRx, 6f, shoeCol)
            }
            OutfitStyle.VESHTI_SHIRT -> {
                limb(c, rig.legL, LEG, lt * 0.92f, skin, true); limb(c, rig.legR, LEG, lt * 0.92f, skin, true)
                limb(c, rig.legL, LEG, lt * 0.92f, skin, false); limb(c, rig.legR, LEG, lt * 0.92f, skin, false)
                blob(c, rig.legL, -2f, LEG + 2.2f, footRx, 5.5f, Color.rgb(0x5D, 0x40, 0x37))
                blob(c, rig.legR, 2f, LEG + 2.2f, footRx, 5.5f, Color.rgb(0x5D, 0x40, 0x37))
            }
            OutfitStyle.SUPER_SUIT -> {
                limb(c, rig.legL, LEG, lt, outCol, true); limb(c, rig.legR, LEG, lt, outCol, true)
                limb(c, rig.legL, LEG, lt, outCol, false); limb(c, rig.legR, LEG, lt, outCol, false)
                blob(c, rig.legL, -2f, LEG + 2.5f, footRx, 6.5f, accCol)
                blob(c, rig.legR, 2f, LEG + 2.5f, footRx, 6.5f, accCol)
            }
            OutfitStyle.IRON_ARMOUR_SUIT -> {
                limb(c, rig.legL, LEG, lt * 1.08f, outCol, true); limb(c, rig.legR, LEG, lt * 1.08f, outCol, true)
                limb(c, rig.legL, LEG, lt * 1.08f, outCol, false); limb(c, rig.legR, LEG, lt * 1.08f, outCol, false)
                for (m in arrayOf(rig.legL, rig.legR)) {
                    c.save(); c.concat(m)
                    fill.shader = null; fill.color = accCol
                    oval.set(-5.5f, 8f, 5.5f, 15f); c.drawRoundRect(oval, 2f, 2f, fill)
                    c.restore()
                }
                blob(c, rig.legL, -2f, LEG + 2.8f, footRx * 1.06f, 6.8f, accCol)
                blob(c, rig.legR, 2f, LEG + 2.8f, footRx * 1.06f, 6.8f, accCol)
            }
            OutfitStyle.NINJA_SHINOBI_SUIT -> {
                limb(c, rig.legL, LEG, lt, outCol, true); limb(c, rig.legR, LEG, lt, outCol, true)
                limb(c, rig.legL, LEG, lt, outCol, false); limb(c, rig.legR, LEG, lt, outCol, false)
                blob(c, rig.legL, -2f, LEG + 2.2f, footRx, 5.8f, accCol)
                blob(c, rig.legR, 2f, LEG + 2.2f, footRx, 5.8f, accCol)
            }
            OutfitStyle.KURTA_SET -> {
                limb(c, rig.legL, LEG, lt * 0.95f, pantsCol, true); limb(c, rig.legR, LEG, lt * 0.95f, pantsCol, true)
                limb(c, rig.legL, LEG, lt * 0.95f, pantsCol, false); limb(c, rig.legR, LEG, lt * 0.95f, pantsCol, false)
                blob(c, rig.legL, -2f, LEG + 2f, footRx, 5.5f, accCol)
                blob(c, rig.legR, 2f, LEG + 2f, footRx, 5.5f, accCol)
            }
            OutfitStyle.PATTU_PAVADAI, OutfitStyle.FROCK_DRESS -> {
                limb(c, rig.legL, LEG, lt * 0.92f, skin, true); limb(c, rig.legR, LEG, lt * 0.92f, skin, true)
                limb(c, rig.legL, LEG, lt * 0.92f, skin, false); limb(c, rig.legR, LEG, lt * 0.92f, skin, false)
                for (m in arrayOf(rig.legL, rig.legR)) {
                    c.save(); c.concat(m)
                    stroke.shader = null; stroke.color = accCol; stroke.strokeWidth = 2.5f
                    c.drawLine(-5f, LEG - 3f, 5f, LEG - 3f, stroke)
                    c.restore()
                }
                val shoeCol = if (spec.outfitStyle == OutfitStyle.PATTU_PAVADAI) accCol else darken(outCol, 0.75f)
                blob(c, rig.legL, -2f, LEG + 2.2f, footRx * 0.95f, 5.5f, shoeCol)
                blob(c, rig.legR, 2f, LEG + 2.2f, footRx * 0.95f, 5.5f, shoeCol)
            }
        }
    }

    override fun drawTorso(c: Canvas, rig: MonkeyRig) {
        val outCol = spec.outfitColor
        val outDark = darken(outCol, 0.76f)
        val outLight = lighten(outCol, 38)
        val accCol = spec.accentColor
        val wv = lastHemWave

        val shW = (20f * spec.shoulderScale).coerceIn(14f, 34f)
        val wW = (19f * spec.bodyWidthScale).coerceIn(13f, 36f)
        val belly = spec.bodyBuild.bellyCurve * 12f + (spec.bodyWidthScale - 1f).coerceAtLeast(0f) * 14f
        lastRenderedShoulderWidth = shW * 2f
        lastRenderedTorsoWidth = (wW + belly * 0.5f) * 2f

        c.save(); c.concat(rig.hips)
        fill.shader = null

        fun buildShapedTorsoPath(bottomY: Float = 6f) {
            torsoPath.reset()
            torsoPath.moveTo(0f, -60f)
            torsoPath.cubicTo(shW * 0.85f, -60f, shW + 3f, -48f, shW, -34f)
            torsoPath.cubicTo(wW + belly, -18f, wW + belly * 0.7f, -2f, wW * 0.88f, bottomY)
            torsoPath.quadTo(0f, bottomY + 3f + belly * 0.18f, -wW * 0.88f, bottomY)
            torsoPath.cubicTo(-wW - belly * 0.7f, -2f, -wW - belly, -18f, -shW, -34f)
            torsoPath.cubicTo(-shW - 3f, -48f, -shW * 0.85f, -60f, 0f, -60f)
            torsoPath.close()
        }

        when (spec.outfitStyle) {
            OutfitStyle.TSHIRT_CARGO -> {
                buildShapedTorsoPath(bottomY = 6f)
                fill.color = outCol
                c.drawPath(torsoPath, fill)
                if (spec.faceMode == FaceMode.FULL_PHOTO_PUPPET && spec.torsoGridColors.isNotEmpty()) {
                    oval.set(-wW - belly, -58f, wW + belly, 4f)
                    ChibiFaceBridge.drawColorGrid(spec.torsoGridColors, c, oval, fill, torsoPath, 210)
                }
                fill.color = spec.skinColor
                oval.set(-9f, -62f, 9f, -52f); c.drawArc(oval, 0f, 180f, true, fill)
                stroke.shader = null; stroke.color = outDark; stroke.strokeWidth = 3.2f
                oval.set(-10.5f, -62f, 10.5f, -50.5f); c.drawArc(oval, 12f, 156f, false, stroke)
                c.drawPath(torsoPath, outline)
            }

            OutfitStyle.CASUAL_SHIRT_JEANS -> {
                buildShapedTorsoPath(bottomY = 6f)
                fill.color = outCol
                c.drawPath(torsoPath, fill)
                if (spec.faceMode == FaceMode.FULL_PHOTO_PUPPET && spec.torsoGridColors.isNotEmpty()) {
                    oval.set(-wW - belly, -58f, wW + belly, 4f)
                    ChibiFaceBridge.drawColorGrid(spec.torsoGridColors, c, oval, fill, torsoPath, 210)
                }
                fill.color = spec.skinColor
                tmp.reset(); tmp.moveTo(-6f, -59f); tmp.lineTo(0f, -49f); tmp.lineTo(6f, -59f); tmp.close()
                c.drawPath(tmp, fill)
                fill.color = outDark
                tmp.reset(); tmp.moveTo(-12f, -59f); tmp.lineTo(-5f, -49f); tmp.lineTo(-1f, -53f); tmp.close(); c.drawPath(tmp, fill)
                tmp.reset(); tmp.moveTo(12f, -59f); tmp.lineTo(5f, -49f); tmp.lineTo(1f, -53f); tmp.close(); c.drawPath(tmp, fill)
                stroke.shader = null; stroke.color = outLight; stroke.strokeWidth = 1.6f
                c.drawLine(0f, -49f, 0f, -6f, stroke)
                fill.color = Color.rgb(0x3E, 0x27, 0x23)
                oval.set(-wW * 0.95f, -6f, wW * 0.95f, 1f); c.drawRoundRect(oval, 2f, 2f, fill)
                fill.color = accCol
                oval.set(-4.5f, -6.5f, 4.5f, 1.5f); c.drawRoundRect(oval, 1.5f, 1.5f, fill)
                c.drawPath(torsoPath, outline)
            }

            OutfitStyle.VESHTI_SHIRT -> {
                buildShapedTorsoPath(bottomY = -18f)
                fill.color = outCol
                c.drawPath(torsoPath, fill); c.drawPath(torsoPath, outline)
                val vW = wW + belly * 0.6f + 3f
                val bottomY = 15f
                torsoPath.reset()
                torsoPath.moveTo(-vW * 0.92f, -22f)
                torsoPath.lineTo(-vW + wv * 0.4f, bottomY)
                torsoPath.quadTo(0f, bottomY + 3f, vW + wv * 0.4f, bottomY)
                torsoPath.lineTo(vW * 0.92f, -22f)
                torsoPath.close()
                fill.color = Color.rgb(0xFA, 0xF8, 0xF0)
                c.drawPath(torsoPath, fill)
                fill.color = accCol
                oval.set(-vW + 2f + wv * 0.4f, bottomY - 6f, vW - 2f + wv * 0.4f, bottomY)
                c.drawRect(oval, fill)
                c.drawRect(vW * 0.45f, -21f, vW * 0.62f, bottomY - 5f, fill)
                c.drawPath(torsoPath, outline)
            }

            OutfitStyle.SUPER_SUIT -> {
                buildShapedTorsoPath(bottomY = 6f)
                fill.color = outCol
                c.drawPath(torsoPath, fill); c.drawPath(torsoPath, outline)
                fill.color = accCol
                oval.set(-wW * 0.96f, -9f, wW * 0.96f, -2f); c.drawRoundRect(oval, 3f, 3f, fill); c.drawRoundRect(oval, 3f, 3f, outline)
                c.drawCircle(0f, -33f, 11.5f, fill); c.drawCircle(0f, -33f, 11.5f, outline)
                fill.color = Color.WHITE
                tmp.reset()
                tmp.moveTo(2f, -41f); tmp.lineTo(-5f, -32f); tmp.lineTo(0f, -32f); tmp.lineTo(-2f, -24f)
                tmp.lineTo(6f, -34f); tmp.lineTo(1f, -34f); tmp.close()
                c.drawPath(tmp, fill)
            }

            OutfitStyle.IRON_ARMOUR_SUIT -> {
                buildShapedTorsoPath(bottomY = 6f)
                fill.color = outCol
                c.drawPath(torsoPath, fill)
                fill.color = accCol
                oval.set(-shW * 0.78f, -52f, -4f, -36f); c.drawRoundRect(oval, 4f, 4f, fill)
                oval.set(4f, -52f, shW * 0.78f, -36f); c.drawRoundRect(oval, 4f, 4f, fill)
                oval.set(-wW * 0.65f, -20f, wW * 0.65f, -6f); c.drawRoundRect(oval, 4f, 4f, fill)
                c.drawPath(torsoPath, outline)
                // Chest Arc-Reactor Unibeam Core (blazes with thrust plume & star flare when flying/hovering!)
                val flyBoost = maxOf(pose.jets, pose.palmGlow).coerceIn(0f, 1f)
                if (flyBoost > 0.05f) {
                    val pulse = 1f + 0.22f * sin(now * 32f)
                    fill.color = Color.rgb(0x00, 0xE5, 0xFF); fill.alpha = (105 * flyBoost).toInt()
                    c.drawCircle(0f, -36f, 20f * pulse, fill)
                    tmp.reset()
                    tmp.moveTo(-6.5f, -34f); tmp.quadTo(-4f, -8f, 0f, 12f * pulse); tmp.quadTo(4f, -8f, 6.5f, -34f); tmp.close()
                    fill.alpha = (150 * flyBoost).toInt()
                    c.drawPath(tmp, fill)
                    stroke.shader = null; stroke.color = Color.WHITE; stroke.strokeWidth = 2.2f; stroke.alpha = (220 * flyBoost).toInt()
                    c.drawLine(-17f * pulse, -36f, 17f * pulse, -36f, stroke)
                    c.drawLine(0f, -52f * pulse, 0f, -20f * pulse, stroke)
                    stroke.alpha = 255
                }
                fill.color = Color.rgb(0x00, 0xE5, 0xFF); fill.alpha = 255
                c.drawCircle(0f, -36f, 9.5f, fill); c.drawCircle(0f, -36f, 9.5f, outline)
                fill.color = Color.WHITE
                c.drawCircle(0f, -36f, 5.5f, fill)
            }

            OutfitStyle.NINJA_SHINOBI_SUIT -> {
                buildShapedTorsoPath(bottomY = 6f)
                fill.color = outCol
                c.drawPath(torsoPath, fill)
                stroke.shader = null; stroke.color = accCol; stroke.strokeWidth = 2.4f
                c.drawLine(-10f, -58f, 8f, -14f, stroke)
                c.drawLine(10f, -58f, -4f, -30f, stroke)
                fill.color = accCol
                oval.set(-wW * 0.96f, -12f, wW * 0.96f, -4f); c.drawRoundRect(oval, 3f, 3f, fill); c.drawRoundRect(oval, 3f, 3f, outline)
                c.drawPath(torsoPath, outline)
            }

            OutfitStyle.HOODIE_JEANS -> {
                buildShapedTorsoPath(bottomY = 6f)
                fill.color = outCol
                c.drawPath(torsoPath, fill); c.drawPath(torsoPath, outline)
                fill.color = outLight
                oval.set(-wW * 0.68f, -22f, wW * 0.68f, -6f); c.drawRoundRect(oval, 5f, 5f, fill); c.drawRoundRect(oval, 5f, 5f, outline)
                stroke.shader = null; stroke.color = Color.WHITE; stroke.strokeWidth = 2f
                c.drawLine(-5f, -54f, -5f, -40f, stroke); c.drawLine(5f, -54f, 5f, -40f, stroke)
            }

            OutfitStyle.KURTA_SET -> {
                val flare = wW + belly * 0.5f + 5f
                torsoPath.reset()
                torsoPath.moveTo(-shW * 0.9f, -59f)
                torsoPath.lineTo(-flare + wv * 0.4f, 9f)
                torsoPath.quadTo(0f, 12f, flare + wv * 0.4f, 9f)
                torsoPath.lineTo(shW * 0.9f, -59f)
                torsoPath.close()
                fill.color = outCol
                c.drawPath(torsoPath, fill)
                stroke.shader = null; stroke.color = accCol; stroke.strokeWidth = 2.5f
                c.drawLine(0f, -58f, 0f, -32f, stroke)
                c.drawPath(torsoPath, outline)
            }

            OutfitStyle.PATTU_PAVADAI -> {
                torsoPath.reset()
                torsoPath.moveTo(-shW * 0.9f, -58f)
                torsoPath.lineTo(-wW * 0.92f, -27f); torsoPath.lineTo(wW * 0.92f, -27f); torsoPath.lineTo(shW * 0.9f, -58f)
                torsoPath.close()
                fill.color = outDark
                c.drawPath(torsoPath, fill); c.drawPath(torsoPath, outline)

                val flare = (wW * 1.48f).coerceAtLeast(28f) + abs(wv) * 0.6f
                val bottomY = 14f
                torsoPath.reset()
                torsoPath.moveTo(-wW * 0.92f, -28f)
                torsoPath.cubicTo(-flare * 0.8f, -14f, -flare + wv * 0.5f, 2f, -flare + wv, bottomY)
                torsoPath.quadTo(0f, bottomY + 3.5f, flare + wv, bottomY)
                torsoPath.cubicTo(flare + wv * 0.5f, 2f, flare * 0.8f, -14f, wW * 0.92f, -28f)
                torsoPath.close()
                fill.color = outCol
                c.drawPath(torsoPath, fill)
                fill.color = accCol
                oval.set(-flare + 2f + wv * 0.8f, bottomY - 7.5f, flare - 2f + wv * 0.8f, bottomY + 1f)
                c.drawRoundRect(oval, 3f, 3f, fill)
                oval.set(-wW, -30f, wW, -25f); c.drawRoundRect(oval, 2.5f, 2.5f, fill); c.drawRoundRect(oval, 2.5f, 2.5f, outline)
                c.drawPath(torsoPath, outline)
            }

            OutfitStyle.FROCK_DRESS -> {
                val flare = (wW * 1.42f).coerceAtLeast(27f) + abs(wv) * 0.5f
                val bottomY = 10f
                torsoPath.reset()
                torsoPath.moveTo(-shW * 0.88f, -58f)
                torsoPath.cubicTo(-flare * 0.75f, -42f, -flare + wv * 0.5f, -8f, -flare + wv, bottomY)
                torsoPath.quadTo(0f, bottomY + 4f, flare + wv, bottomY)
                torsoPath.cubicTo(flare + wv * 0.5f, -8f, flare * 0.75f, -42f, shW * 0.88f, -58f)
                torsoPath.close()
                fill.color = outCol
                c.drawPath(torsoPath, fill)
                fill.color = accCol
                oval.set(-wW * 1.05f, -29f, wW * 1.05f, -23f); c.drawRoundRect(oval, 3f, 3f, fill)
                c.drawPath(torsoPath, outline)
            }
        }
        c.restore()
    }

    override fun drawArms(c: Canvas, rig: MonkeyRig) {
        val skin = spec.skinColor
        val outCol = spec.outfitColor
        val accCol = spec.accentColor
        val at = 9.6f * limbThick()
        val fullSleeve = spec.outfitStyle in setOf(
            OutfitStyle.HOODIE_JEANS, OutfitStyle.SUPER_SUIT, OutfitStyle.IRON_ARMOUR_SUIT, OutfitStyle.NINJA_SHINOBI_SUIT
        )
        val upperCol = outCol
        val foreCol = if (fullSleeve) outCol else skin

        limb(c, rig.armLUpper, rig.upperLen, at, upperCol, true); limb(c, rig.armLFore, rig.foreLen, at * 0.9f, foreCol, true)
        limb(c, rig.armRUpper, rig.upperLen, at, upperCol, true); limb(c, rig.armRFore, rig.foreLen, at * 0.9f, foreCol, true)
        limb(c, rig.armLUpper, rig.upperLen, at, upperCol, false); limb(c, rig.armLFore, rig.foreLen, at * 0.9f, foreCol, false)
        limb(c, rig.armRUpper, rig.upperLen, at, upperCol, false); limb(c, rig.armRFore, rig.foreLen, at * 0.9f, foreCol, false)

        if (spec.outfitStyle == OutfitStyle.IRON_ARMOUR_SUIT || spec.outfitStyle == OutfitStyle.NINJA_SHINOBI_SUIT) {
            for (m in arrayOf(rig.armLFore, rig.armRFore)) {
                c.save(); c.concat(m)
                stroke.shader = null; stroke.color = accCol; stroke.strokeWidth = 3.5f
                c.drawLine(-4.5f, rig.foreLen - 4f, 4.5f, rig.foreLen - 4f, stroke)
                c.restore()
            }
        }

        val handCol = if (spec.outfitStyle == OutfitStyle.SUPER_SUIT || spec.outfitStyle == OutfitStyle.IRON_ARMOUR_SUIT) accCol else skin
        val hRad = (6.8f * limbThick()).coerceIn(5.8f, 9.2f)
        blob(c, rig.armLFore, 0f, rig.foreLen + 2f, hRad, hRad, handCol)
        blob(c, rig.armRFore, 0f, rig.foreLen + 2f, hRad, hRad, handCol)

        if (pose.ninja > 0.05f || spec.outfitStyle == OutfitStyle.NINJA_SHINOBI_SUIT) {
            c.save(); c.concat(rig.armRFore); c.translate(0f, rig.foreLen + 6f); c.rotate(now * 540f)
            fill.shader = null; fill.color = Color.rgb(0xCF, 0xD8, 0xDC)
            for (i in 0 until 4) {
                c.save(); c.rotate(i * 90f)
                tmp.reset(); tmp.moveTo(0f, -3f); tmp.lineTo(13f, 0f); tmp.lineTo(0f, 3f); tmp.close()
                c.drawPath(tmp, fill); c.drawPath(tmp, outline)
                c.restore()
            }
            fill.color = Color.rgb(0x26, 0x32, 0x38); c.drawCircle(0f, 0f, 2.5f, fill)
            c.restore()
        }
    }

    /** Computes the clean adult/child head half-width `rx` and vertical bounds `(topY, botY)` in head space, driven 1:1 by `faceMesh`. */
    fun headBounds(): Triple<Float, Float, Float> {
        val mesh = spec.faceMesh
        val jawScale = (0.45f + 0.55f * mesh.jawWidthRatio).coerceIn(0.80f, 1.24f)
        val chinShift = ((mesh.chinLengthRatio - 1.0f) * 14f).coerceIn(-4.5f, 5.5f)
        val fhShift = ((mesh.foreheadHeightRatio - 1.08f) * 14f).coerceIn(-5.5f, 6.5f)
        val isChild = spec.ageGroup == AgeGroup.CHILD_KUTTY || spec.gender.isChildDefault
        if (isChild) {
            val rx = 28.5f * jawScale
            val topY = -70.5f - fhShift
            val botY = (-7.2f + chinShift).coerceIn(-11.5f, -3.5f)
            lastRenderedFaceRx = rx
            lastRenderedChinY = botY
            lastRenderedHairlineY = topY + 12.2f
            return Triple(rx, topY, botY)
        }
        val fw = (spec.bodyBuild.faceWidthScale * when (spec.faceShape) {
            FaceShape.ROUND_FULL -> 1.06f
            FaceShape.SQUARE_JAW -> 0.98f
            FaceShape.HEART_CHEEKS -> 0.92f
            FaceShape.OVAL_BALANCED -> 0.95f
        } * (if (spec.gender.isFemale) 0.94f else 1.0f) * jawScale).coerceIn(0.78f, 1.26f)
        val rx = 25.8f * fw
        val topY = -71.5f - fhShift
        val botY = (-6.8f + chinShift).coerceIn(-11.0f, -3.2f)
        lastRenderedFaceRx = rx
        lastRenderedChinY = botY
        lastRenderedHairlineY = topY + 14.5f
        return Triple(rx, topY, botY)
    }

    override fun drawHeadBase(c: Canvas, p: Pose, t: Float) {
        val skin = spec.skinColor
        val skinLight = lighten(skin, 28)
        val skinShade = darken(skin, 0.80f)
        if (cachedSkinColor != skin || skinShader == null) {
            skinShader = RadialGradient(-6f, -48f, 48f, intArrayOf(skinLight, skin, skinShade), floatArrayOf(0f, 0.68f, 1f), Shader.TileMode.CLAMP)
            cachedSkinColor = skin
        }

        lastRenderedFaceMode = spec.faceMode
        lastRenderedFacialHair = spec.facialHair
        lastRenderedGender = spec.gender

        val isChild = spec.ageGroup == AgeGroup.CHILD_KUTTY || spec.gender.isChildDefault
        val isFem = spec.gender.isFemale
        val nw = if (isChild) 6.6f else if (isFem) 7.2f else 8.6f

        // 1. Clear Anatomical Cylindrical Neck connecting shirt collar (+4.5f) up to chin (-8.5f)
        lastRenderedNeckHeight = 12.5f
        fill.shader = null
        fill.color = skin
        oval.set(-nw, -10.5f, nw, 4.2f)
        c.drawRoundRect(oval, 3.5f, 3.5f, fill)
        // Soft curved sub-mental cast shadow under the chin
        fill.color = darken(skin, 0.76f)
        oval.set(-nw, -10.5f, nw, -3.5f)
        c.drawRoundRect(oval, 3f, 3f, fill)
        // Crisp vertical neck outline strokes on left and right sides (like Billa!)
        outline.strokeWidth = 2.0f
        c.drawLine(-nw, -7.5f, -nw, 3.0f, outline)
        c.drawLine(nw, -7.5f, nw, 3.0f, outline)
        outline.strokeWidth = OW

        // Optional Photo/Toon Cutout Modes
        if (hasRealHeadPortrait()) {
            val halfSize = HEAD_R * 1.48f * spec.faceZoomScale.coerceIn(0.75f, 1.45f)
            val cx = spec.faceOffsetX
            val cy = HEAD_CY - 6f + spec.faceOffsetY
            val jawStretch = if (p.mouthOpen > 0.25f) 1f + p.mouthOpen.coerceIn(0f, 0.8f) * 0.04f else 1f

            c.save()
            if (jawStretch > 1.001f) c.scale(1f, jawStretch, cx, cy)
            faceDstRect.set(cx - halfSize, cy - halfSize, cx + halfSize, cy + halfSize)
            faceClipPath.reset()
            faceClipPath.addRect(faceDstRect, Path.Direction.CW)
            val drew = ChibiFaceBridge.drawPortrait(spec, c, faceDstRect, fill, faceClipPath, 255)
            if (drew) realFaceCutoutDrawnCount++
            c.restore()
            return
        }

        // =========================================================================================
        // MODELED 2D LOOK-ALIKE FACE (`FaceMode.CHIBI_TWIN` — Default, 100% Clean 2D Vector Model)
        // =========================================================================================
        val (rx, topY, botY) = headBounds()

        // (Back hair is drawn in drawBehind() behind the torso & neck so the neck and jawline stay 100% clean and uncovered!)

        // 3. Proportionate Human Ears tucked neatly against the head in matching warm skin tone
        fill.shader = null
        val earX = rx - 0.6f
        val ey = -36.5f
        for (side in intArrayOf(-1, 1)) {
            val ex = side * earX
            fill.color = skin
            oval.set(ex - 4.2f, ey - 6.8f, ex + 4.2f, ey + 6.8f)
            c.drawOval(oval, fill)
            outline.strokeWidth = 1.8f
            c.drawOval(oval, outline)
            outline.strokeWidth = OW
            // Subtle inner ear crease in warm skin shade (never dark brown!)
            stroke.shader = null; stroke.color = skinShade; stroke.strokeWidth = 1.3f
            c.drawLine(ex + side * 0.8f, ey - 3.2f, ex + side * 0.8f, ey + 2.8f, stroke)

            // Gold stud / hoop earrings for Female & Girl companions
            if (isFem || spec.outfitStyle == OutfitStyle.PATTU_PAVADAI || showAcc("jasmine", spec.jasmine)) {
                c.save()
                c.translate(ex + side * 1.2f, ey + 6.2f)
                c.rotate(lastHairSwingAngle * 0.35f)
                fill.color = Color.rgb(0xFF, 0xD5, 0x4F)
                c.drawCircle(0f, 0f, 2.3f, fill)
                stroke.color = Color.rgb(0xF5, 0x7F, 0x17); stroke.strokeWidth = 1.2f
                c.drawCircle(0f, 2.4f, 2.2f, stroke)
                c.restore()
            }
        }

        // 4. Clean Sculpted Facecut Silhouette (Feminine Heart/Oval, Cute Child Round-Oval, or Masculine Oblong-Oval)
        headShapePath.reset()
        val midCy = -38.5f
        val jawW = when {
            isChild -> rx * 0.88f
            isFem -> rx * 0.80f
            else -> rx * 0.85f
        }
        val chinW = when {
            isChild -> rx * 0.44f
            isFem -> rx * 0.32f
            else -> rx * 0.40f
        }
        headShapePath.moveTo(0f, topY)
        headShapePath.cubicTo(rx * 0.88f, topY, rx, topY + 14f, rx, midCy)
        headShapePath.cubicTo(rx, midCy + 12f, jawW + 1.6f, botY - 11f, jawW, botY - 7.5f)
        headShapePath.cubicTo(jawW * 0.70f, botY - 1.2f, chinW, botY, 0f, botY)
        headShapePath.cubicTo(-chinW, botY, -jawW * 0.70f, botY - 1.2f, -jawW, botY - 7.5f)
        headShapePath.cubicTo(-jawW - 1.6f, botY - 11f, -rx, midCy + 12f, -rx, midCy)
        headShapePath.cubicTo(-rx, topY + 14f, -rx * 0.88f, topY, 0f, topY)
        headShapePath.close()

        fill.shader = skinShader
        c.drawPath(headShapePath, fill)
        fill.shader = null
        outline.strokeWidth = 2.0f
        c.drawPath(headShapePath, outline)
        outline.strokeWidth = OW

        // 5. Compact Sculpted Circle Beard & Subtle Jawline Shadow (keeping cheeks 100% clean warm skin!)
        drawJawFacialHair(c, p, rx, botY)
    }

    override fun drawCheeks(c: Canvas, p: Pose) {
        if (hasRealHeadPortrait()) return
        val isChild = spec.ageGroup == AgeGroup.CHILD_KUTTY || spec.gender.isChildDefault
        if (spec.gender.isFemale || isChild || spec.eyeStyle == EyeStyle.SPARKLE_CUTE) {
            fill.shader = null
            fill.color = Color.argb(if (isChild) 68 else 52, 0xFF, 0x80, 0x96)
            oval.set(-20.5f, -34.5f, -11.5f, -28.5f); c.drawOval(oval, fill)
            oval.set(11.5f, -34.5f, 20.5f, -28.5f); c.drawOval(oval, fill)
            fill.alpha = 255
        }
    }

    override fun drawBrows(c: Canvas, p: Pose) {
        if (hasRealHeadPortrait()) return
        val mesh = spec.faceMesh
        val isChild = spec.ageGroup == AgeGroup.CHILD_KUTTY || spec.gender.isChildDefault
        val isFem = spec.gender.isFemale
        val exBase = (28.5f * mesh.eyeSpacingRatio).coerceIn(9.4f, 14.2f)
        val thick = (when {
            isChild -> 2.3f
            isFem -> 2.4f
            else -> (3.0f * mesh.browThicknessRatio).coerceIn(2.2f, 4.2f)
        } * (0.75f + 0.25f * mesh.browThicknessRatio)).coerceIn(1.8f, 4.4f)
        val arch = (if (isFem) 2.0f else if (isChild) 1.2f else 0.8f) + mesh.browArchRatio * 4.2f

        fill.shader = null
        fill.color = spec.hairColor
        for (s in intArrayOf(-1, 1)) {
            c.save()
            val bx = s * exBase + p.lookX * 0.6f
            val by = -47.2f + (mesh.eyeYOffset + mesh.browYOffset) * 42f + p.browY
            c.translate(bx, by)
            c.rotate(-s * p.browAngle)
            browPath.reset()
            browPath.moveTo(-s * 6.5f, 0.5f)
            browPath.quadTo(0f, -arch - thick * 0.52f, s * 7.4f, 0.3f)
            browPath.quadTo(0f, -arch + thick * 0.45f, -s * 6.5f, 0.5f + thick * 0.48f)
            browPath.close()
            c.drawPath(browPath, fill)
            c.restore()
        }
    }

    override fun drawMouth(c: Canvas, p: Pose) {
        if (hasRealHeadPortrait()) return
        val mesh = spec.faceMesh
        val mx = p.lookX * 0.5f + mesh.mouthXOffset * 28f
        val my = -17.5f + mesh.mouthYOffset * 42f
        lastRenderedMouthY = my
        val isChild = spec.ageGroup == AgeGroup.CHILD_KUTTY || spec.gender.isChildDefault
        val isFem = spec.gender.isFemale
        val baseMw = if (isChild) 5.6f else if (isFem) 6.0f else 6.6f
        val mw = (baseMw * mesh.mouthWidthRatio).coerceIn(4.4f, 9.4f)
        val lipH = mesh.lipFullnessRatio.coerceIn(0.70f, 1.50f)
        if (p.mouthOpen > 0.25f) {
            c.save()
            c.translate(0f, -3.5f)
            super.drawMouth(c, p)
            c.restore()
        } else if (isFem || isChild) {
            // Warm rose-coral two-tone lips with gentle smile curve
            fill.shader = null
            fill.color = if (isFem && !isChild) Color.rgb(0xC2, 0x52, 0x64) else Color.rgb(0xCA, 0x66, 0x6A)
            oval.set(mx - mw, my - 1.8f * lipH, mx + mw, my + 2.2f * lipH)
            c.drawRoundRect(oval, 2.2f, 2.2f, fill)
            stroke.shader = null
            stroke.color = Color.rgb(0x6D, 0x24, 0x2C)
            stroke.strokeWidth = 1.5f
            val smile = p.mouthSmile.coerceIn(-0.3f, 0.9f) * 1.6f
            tmp.reset()
            tmp.moveTo(mx - mw, my - 0.2f)
            tmp.quadTo(mx, my + smile, mx + mw, my - 0.2f)
            c.drawPath(tmp, stroke)
        } else {
            // Clean natural two-tone male lips inside the moustache/beard frame
            fill.shader = null
            fill.color = Color.rgb(0x96, 0x5E, 0x50)
            oval.set(mx - mw, my - 1.9f * lipH, mx + mw, my + 2.1f * lipH)
            c.drawRoundRect(oval, 2f, 2f, fill)
            stroke.shader = null
            stroke.color = Color.rgb(0x4A, 0x26, 0x1E)
            stroke.strokeWidth = 1.6f
            val smile = p.mouthSmile.coerceIn(-0.5f, 0.8f) * 1.3f
            tmp.reset()
            tmp.moveTo(mx - mw, my)
            tmp.quadTo(mx, my + smile, mx + mw, my)
            c.drawPath(tmp, stroke)
        }
    }

    private fun drawJawFacialHair(c: Canvas, p: Pose, rx: Float, botY: Float) {
        if (spec.gender.isFemale || spec.ageGroup == AgeGroup.CHILD_KUTTY || spec.gender.isChildDefault || spec.hairStyle.isFemaleStyle) return
        if (spec.facialHair == FacialHairStyle.NONE || spec.facialHair == FacialHairStyle.MOUSTACHE_CLASSIC) return

        val hCol = spec.hairColor
        val openDrop = p.mouthOpen.coerceIn(0f, 1f) * 2.5f
        val jawW = rx * 0.85f
        val chinW = rx * 0.40f

        when (spec.facialHair) {
            FacialHairStyle.STUBBLE_3DAY -> {
                fill.shader = null
                fill.color = Color.argb(65, Color.red(hCol), Color.green(hCol), Color.blue(hCol))
                oval.set(-12.5f, -20.5f, 12.5f, botY + 0.5f + openDrop)
                c.drawOval(oval, fill)
            }
            FacialHairStyle.SHORT_BOXED_BEARD -> {
                // 1. Subtle thin jawline contour shadow along the lower jaw edge (keeps cheeks 100% clean warm skin!)
                stroke.shader = null
                stroke.color = Color.argb(145, Color.red(hCol), Color.green(hCol), Color.blue(hCol))
                stroke.strokeWidth = 2.1f
                beardPath.reset()
                beardPath.moveTo(-jawW * 0.92f, -24f)
                beardPath.cubicTo(-jawW * 0.80f, botY - 4f, -chinW, botY - 0.3f, 0f, botY - 0.2f + openDrop)
                beardPath.cubicTo(chinW, botY - 0.3f, jawW * 0.80f, botY - 4f, jawW * 0.92f, -24f)
                c.drawPath(beardPath, stroke)

                // 2. Compact, neat Circle Beard / Chin Goatee (±11.8f width around mouth & chin, NOT a wide black U-bowl!)
                fill.shader = null; fill.color = hCol
                beardPath.reset()
                val gw = 11.8f
                beardPath.moveTo(-gw, -15.5f)
                beardPath.quadTo(-gw * 0.72f, -12.2f + openDrop * 0.5f, 0f, -11.8f + openDrop * 0.5f)
                beardPath.quadTo(gw * 0.72f, -12.2f + openDrop * 0.5f, gw, -15.5f)
                beardPath.cubicTo(gw + 0.8f, botY - 4.5f, chinW * 0.88f, botY + 0.8f + openDrop, 0f, botY + 1.0f + openDrop)
                beardPath.cubicTo(-chinW * 0.88f, botY + 0.8f + openDrop, -gw - 0.8f, botY - 4.5f, -gw, -15.5f)
                beardPath.close()
                c.drawPath(beardPath, fill)

                // 3. Central Soul Patch right under the lower lip
                oval.set(-3.0f, -15.0f + openDrop * 0.4f, 3.0f, -10.5f + openDrop)
                c.drawOval(oval, fill)
            }
            FacialHairStyle.FULL_BEARD_MADRAS -> {
                fill.shader = null; fill.color = hCol
                val fullBottom = botY + 7.5f + openDrop
                beardPath.reset()
                beardPath.moveTo(-rx * 0.92f, -34f)
                beardPath.cubicTo(-rx * 0.72f, -19f, -13.5f, -18f, -11.5f, -12.5f)
                beardPath.quadTo(0f, -9.5f + openDrop, 11.5f, -12.5f)
                beardPath.cubicTo(13.5f, -18f, rx * 0.72f, -19f, rx * 0.92f, -34f)
                beardPath.cubicTo(rx * 0.96f, -6f, rx * 0.70f, fullBottom, 0f, fullBottom)
                beardPath.cubicTo(-rx * 0.70f, fullBottom, -rx * 0.96f, -6f, -rx * 0.92f, -34f)
                beardPath.close()
                c.drawPath(beardPath, fill)
                c.drawPath(beardPath, outline)
                oval.set(-3.8f, -15.0f + openDrop * 0.4f, 3.8f, -9.5f + openDrop)
                c.drawOval(oval, fill)
            }
            FacialHairStyle.GOATEE_SOUL -> {
                fill.shader = null; fill.color = hCol
                oval.set(-10.2f, -13.5f + openDrop * 0.5f, 10.2f, botY + 0.8f + openDrop)
                c.drawOval(oval, fill)
                oval.set(-3.0f, -15.0f + openDrop * 0.4f, 3.0f, -10.5f + openDrop)
                c.drawOval(oval, fill)
            }
            else -> Unit
        }
    }

    private fun drawBackHair(c: Canvas, p: Pose, t: Float) {
        val hCol = spec.hairColor
        val accCol = spec.accentColor
        val sw = lastHairSwingAngle
        val by = lastHairBounceY * 0.45f
        val (rx, _, _) = headBounds()

        fill.shader = null
        when (spec.hairStyle) {
            HairStyle.PIGTAILS_RIBBONS -> {
                for (side in intArrayOf(-1, 1)) {
                    c.save()
                    c.translate(side * (rx + 1.5f), -52f + by)
                    c.rotate(side * 10f + sw * 0.75f)
                    if (showAcc("bow", spec.hairBow)) {
                        fill.color = Color.rgb(0xFF, 0x40, 0x81)
                        oval.set(-6.5f, -5.5f, 0f, 2.5f); c.drawOval(oval, fill); c.drawOval(oval, outline)
                        oval.set(0f, -5.5f, 6.5f, 2.5f); c.drawOval(oval, fill); c.drawOval(oval, outline)
                    }
                    hairPath.reset()
                    hairPath.moveTo(0f, 0f)
                    hairPath.cubicTo(side * 16f, -5f, side * 22f, 16f, side * 12f, 32f)
                    hairPath.cubicTo(side * 3f, 25f, side * 3f, 9f, 0f, 2f)
                    hairPath.close()
                    fill.color = hCol
                    c.drawPath(hairPath, fill); c.drawPath(hairPath, outline)
                    c.restore()
                }
            }
            HairStyle.TWO_BRAIDS_JASMINE -> {
                for (side in intArrayOf(-1, 1)) {
                    c.save()
                    c.translate(side * (rx - 1f), -28f + by)
                    c.rotate(sw * 0.65f)
                    for (seg in 0..3) {
                        val sy = seg * 8.0f
                        val sx = sin(seg * 0.9f + sw * 0.05f) * 1.8f
                        fill.color = hCol
                        oval.set(sx - 5.0f, sy, sx + 5.0f, sy + 9.8f)
                        c.drawOval(oval, fill); c.drawOval(oval, outline)
                        if (showAcc("jasmine", spec.jasmine) && seg < 3) {
                            fill.color = Color.WHITE
                            c.drawCircle(sx - 3.8f, sy + 4.8f, 2.1f, fill)
                            c.drawCircle(sx, sy + 5.2f, 2.3f, fill)
                            c.drawCircle(sx + 3.8f, sy + 4.8f, 2.1f, fill)
                        }
                    }
                    c.restore()
                }
            }
            HairStyle.LONG_WAVY -> {
                c.save()
                c.translate(0f, by)
                c.rotate(sw * 0.25f, 0f, -52f)
                // Sleek feminine flowing hair behind the upper back/shoulders (tucked narrower than the face at jaw/neck level!)
                hairPath.reset()
                hairPath.moveTo(-rx * 0.88f, -34f)
                hairPath.cubicTo(-rx * 0.96f, -14f, -rx * 0.92f, 8f, -rx * 0.76f, 22f)
                hairPath.quadTo(0f, 26f, rx * 0.76f, 22f)
                hairPath.cubicTo(rx * 0.92f, 8f, rx * 0.96f, -14f, rx * 0.88f, -34f)
                hairPath.close()
                fill.color = hCol
                c.drawPath(hairPath, fill)
                outline.strokeWidth = 1.8f
                c.drawPath(hairPath, outline)
                outline.strokeWidth = OW
                c.restore()
            }
            HairStyle.CURLY_PUFF -> {
                for (side in intArrayOf(-1, 1)) {
                    c.save()
                    c.translate(side * (rx - 2f), -60f + by)
                    c.rotate(sw * 0.45f)
                    fill.color = hCol
                    c.drawCircle(0f, 0f, 13.5f, fill); c.drawCircle(0f, 0f, 13.5f, outline)
                    c.restore()
                }
            }
            HairStyle.BOB_CUTE -> {
                hairPath.reset()
                hairPath.moveTo(-rx * 0.92f, -38f)
                hairPath.cubicTo(-rx * 1.02f, -22f, -rx * 0.94f, -8f, -rx * 0.74f, -4f)
                hairPath.lineTo(rx * 0.74f, -4f)
                hairPath.cubicTo(rx * 0.94f, -8f, rx * 1.02f, -22f, rx * 0.92f, -38f)
                hairPath.close()
                fill.color = hCol
                c.drawPath(hairPath, fill); c.drawPath(hairPath, outline)
            }
            else -> Unit
        }
    }

    override val glintEye: FloatArray? = null

    override fun drawEyes(c: Canvas, p: Pose) {
        val open = p.eyeOpen
        lastBlinkAmount = open

        if (hasRealHeadPortrait()) {
            val halfSize = HEAD_R * 1.48f * spec.faceZoomScale.coerceIn(0.75f, 1.45f)
            val cx = spec.faceOffsetX
            val cy = HEAD_CY - 6f + spec.faceOffsetY
            val skin = spec.skinColor
            val skinDark = darken(skin, 0.72f)
            for (s in intArrayOf(-1, 1)) {
                val ex = cx + s * halfSize * 0.22f + p.lookX * 0.35f
                val ey = cy - halfSize * 0.065f + p.lookY * 0.35f
                if ((s < 0 && p.wink > 0.5f) || open < 0.45f) {
                    fill.shader = null; fill.color = skin
                    oval.set(ex - 7.5f, ey - 4.8f, ex + 7.5f, ey + 4.8f)
                    c.drawOval(oval, fill)
                    stroke.shader = null; stroke.color = skinDark; stroke.strokeWidth = 2.1f
                    tmp.reset(); tmp.moveTo(ex - 6.5f, ey + 0.8f); tmp.quadTo(ex, ey + 3.5f, ex + 6.5f, ey + 0.8f)
                    c.drawPath(tmp, stroke)
                }
            }
            return
        }

        val isChild = spec.ageGroup == AgeGroup.CHILD_KUTTY || spec.gender.isChildDefault
        val isFem = spec.gender.isFemale
        val mesh = spec.faceMesh
        val exBase = (28.5f * mesh.eyeSpacingRatio).coerceIn(9.4f, 14.2f)
        val irisOuter = Color.rgb(0x28, 0x16, 0x0C)
        val irisInner = Color.rgb(0x4E, 0x2E, 0x1B)
        val eyeYShift = mesh.eyeYOffset * 42f
        lastRenderedEyeY = -39.5f + eyeYShift

        for (s in intArrayOf(-1, 1)) {
            val ex = s * exBase + p.lookX
            val ey = -39.5f + eyeYShift + p.lookY
            if (s < 0 && p.wink > 0.5f) {
                stroke.shader = null; stroke.color = OUTLINE; stroke.strokeWidth = 2.4f
                tmp.reset(); tmp.moveTo(ex - 5.8f, ey + 0.8f); tmp.quadTo(ex, ey - 3.5f, ex + 5.8f, ey + 0.8f)
                c.drawPath(tmp, stroke)
                continue
            }
            if (open < 0.22f) {
                stroke.shader = null; stroke.color = OUTLINE; stroke.strokeWidth = 2.4f
                c.drawLine(ex - 5.8f, ey, ex + 5.8f, ey, stroke)
                continue
            }

            if (isChild || isFem) {
                // Big, bright expressive almond-round eyes for Children & Women (always open, with rich irises & lashes!)
                val ew = if (isChild) 6.6f else 6.4f
                val eh = (if (isChild) 5.2f else 4.2f) * open.coerceIn(0.65f, 1.1f)
                eyePath.reset()
                eyePath.moveTo(ex - ew, ey + 0.4f)
                eyePath.quadTo(ex, ey - eh * 1.35f, ex + ew, ey - 0.1f)
                eyePath.quadTo(ex, ey + eh * 1.25f, ex - ew, ey + 0.4f)
                eyePath.close()

                fill.shader = null; fill.color = Color.rgb(0xFA, 0xF8, 0xF5)
                c.drawPath(eyePath, fill)

                c.save()
                c.clipPath(eyePath)
                val ix = ex + p.lookX * 0.22f
                val iy = ey
                val ir = if (isChild) 4.4f else 3.9f
                fill.color = irisOuter
                c.drawCircle(ix, iy, ir, fill)
                fill.color = irisInner
                c.drawCircle(ix, iy + 0.3f, ir * 0.72f, fill)
                fill.color = Color.rgb(0x10, 0x08, 0x05)
                c.drawCircle(ix, iy, ir * 0.46f, fill)
                // Dual catchlight sparkles
                fill.color = Color.WHITE
                c.drawCircle(ix + 1.4f, iy - 1.4f, 1.35f, fill)
                c.drawCircle(ix - 1.2f, iy + 1.1f, 0.65f, fill)
                c.restore()

                // Crisp upper lash line
                stroke.shader = null; stroke.color = Color.rgb(0x18, 0x0E, 0x0A); stroke.strokeWidth = 2.3f
                tmp.reset()
                tmp.moveTo(ex - ew - 0.4f, ey + 0.4f)
                tmp.quadTo(ex, ey - eh * 1.38f, ex + ew + 0.4f, ey - 0.1f)
                c.drawPath(tmp, stroke)

                // Curved outer eyelashes for Girl & Woman
                if (isFem) {
                    stroke.strokeWidth = 1.6f
                    val outerX = ex + s * (ew - 0.3f)
                    val outerY = ey - 0.2f
                    c.drawLine(outerX, outerY, outerX + s * 2.6f, outerY - 2.2f, stroke)
                    c.drawLine(outerX - s * 1.2f, outerY - 1.4f, outerX + s * 1.6f, outerY - 3.6f, stroke)
                }
            } else {
                // Clean, Expressive Masculine Almond Eyes
                val ew = 6.2f
                val eh = (ew * mesh.eyeAspectRatio.coerceIn(0.42f, 0.54f) * open.coerceIn(0.65f, 1.1f))
                eyePath.reset()
                eyePath.moveTo(ex - ew, ey + 0.3f)
                eyePath.quadTo(ex, ey - eh * 1.45f, ex + ew, ey - 0.1f)
                eyePath.quadTo(ex, ey + eh * 1.25f, ex - ew, ey + 0.3f)
                eyePath.close()

                fill.shader = null; fill.color = Color.rgb(0xFA, 0xF7, 0xF2)
                c.drawPath(eyePath, fill)

                c.save()
                c.clipPath(eyePath)
                val ix = ex + p.lookX * 0.2f
                fill.color = irisOuter
                c.drawCircle(ix, ey - 0.1f, 3.6f, fill)
                fill.color = irisInner
                c.drawCircle(ix, ey + 0.1f, 2.5f, fill)
                fill.color = Color.rgb(0x12, 0x0A, 0x06)
                c.drawCircle(ix, ey - 0.1f, 1.75f, fill)
                fill.color = Color.WHITE
                c.drawCircle(ix + 1.15f, ey - 1.25f, 1.1f, fill)
                c.drawCircle(ix - 0.9f, ey + 0.8f, 0.55f, fill)
                c.restore()

                stroke.shader = null; stroke.color = Color.rgb(0x1C, 0x12, 0x0E); stroke.strokeWidth = 2.2f
                tmp.reset()
                tmp.moveTo(ex - ew - 0.4f, ey + 0.3f)
                tmp.quadTo(ex, ey - eh * 1.48f, ex + ew + 0.4f, ey - 0.1f)
                c.drawPath(tmp, stroke)

                stroke.color = darken(spec.skinColor, 0.72f); stroke.strokeWidth = 1.0f
                tmp.reset()
                tmp.moveTo(ex - ew * 0.80f, ey - eh * 1.22f)
                tmp.quadTo(ex, ey - eh * 1.90f, ex + ew * 0.80f, ey - eh * 1.22f)
                c.drawPath(tmp, stroke)
            }
        }
    }

    override fun drawNose(c: Canvas, p: Pose) {
        if (hasRealHeadPortrait()) return
        val mesh = spec.faceMesh
        val isChild = spec.ageGroup == AgeGroup.CHILD_KUTTY || spec.gender.isChildDefault
        val isFem = spec.gender.isFemale
        val baseNw = if (isChild) 3.1f else if (isFem) 3.4f else 4.1f
        val nw = (baseNw * mesh.noseWidthRatio).coerceIn(2.6f, 5.8f)
        val nx = p.lookX * 0.55f + mesh.noseXOffset * 28f
        val nyTip = -28.0f + mesh.noseYOffset * 42f + (mesh.noseLengthRatio - 1.06f) * 14f
        val skinDark = darken(spec.skinColor, 0.70f)
        val skinHighlight = lighten(spec.skinColor, 22)

        // Clean soft nose bridge highlight
        stroke.shader = null; stroke.color = skinHighlight; stroke.strokeWidth = 1.9f
        c.drawLine(nx, -37.5f + mesh.eyeYOffset * 25f, nx, nyTip - 1.2f, stroke)

        // Clean minimalist rounded nose tip & wings
        stroke.color = skinDark; stroke.strokeWidth = 1.7f
        tmp.reset()
        tmp.moveTo(nx - nw, nyTip)
        tmp.quadTo(nx, nyTip + 2.6f, nx + nw, nyTip)
        c.drawPath(tmp, stroke)
    }

    override fun drawHeadExtras(c: Canvas, p: Pose, t: Float) {
        val hCol = spec.hairColor
        val hLight = lighten(hCol, 42)
        val accCol = spec.accentColor
        val sw = lastHairSwingAngle
        val (rx, topY, _) = headBounds()
        val isChild = spec.ageGroup == AgeGroup.CHILD_KUTTY || spec.gender.isChildDefault
        val isFem = spec.gender.isFemale

        if (!hasRealHeadPortrait()) {
            // 1. Clean Sculpted Chevron Moustache (only for adult/senior males with facialHair != NONE)
            if (!isFem && !isChild && !spec.hairStyle.isFemaleStyle && spec.facialHair != FacialHairStyle.NONE) {
                val mx = p.lookX * 0.5f + spec.faceMesh.mouthXOffset * 28f
                val my = -22.2f + spec.faceMesh.mouthYOffset * 42f - p.mouthSmile.coerceIn(-0.5f, 1f) * 0.8f
                val w = if (spec.facialHair == FacialHairStyle.FULL_BEARD_MADRAS) 14.5f else 12.2f
                stachePath.reset()
                stachePath.moveTo(mx, my - 2.0f)
                stachePath.cubicTo(mx - 4.5f, my - 4.2f, mx - w * 0.82f, my - 2.8f, mx - w, my + 2.5f)
                if (spec.facialHair == FacialHairStyle.SHORT_BOXED_BEARD || spec.facialHair == FacialHairStyle.FULL_BEARD_MADRAS) {
                    stachePath.lineTo(mx - w + 1.2f, my + 8.2f)
                    stachePath.lineTo(mx - w + 3.4f, my + 7.5f)
                }
                stachePath.cubicTo(mx - w * 0.60f, my + 0.6f, mx - 4.0f, my + 1.5f, mx, my + 0.6f)
                stachePath.cubicTo(mx + 4.0f, my + 1.5f, mx + w * 0.60f, my + 0.6f, mx + w - 3.4f, my + 7.5f)
                if (spec.facialHair == FacialHairStyle.SHORT_BOXED_BEARD || spec.facialHair == FacialHairStyle.FULL_BEARD_MADRAS) {
                    stachePath.lineTo(mx + w - 1.2f, my + 8.2f)
                    stachePath.lineTo(mx + w, my + 2.5f)
                }
                stachePath.cubicTo(mx + w * 0.82f, my - 2.8f, mx + 4.5f, my - 4.2f, mx, my - 2.0f)
                stachePath.close()
                fill.shader = null; fill.color = hCol
                c.drawPath(stachePath, fill)
            }

            // 2. Full-Crown Haircut Silhouettes (Anchored above `topY` so NEVER a bald skin cap above the hair!)
            val lift = if (p.anger > 0.2f || p.size > 1.02f) 6f else 0f
            val crownControlY = topY - 17f - lift
            hairPath.reset()
            when (spec.hairStyle) {
                HairStyle.BALD_OR_BUZZ -> {
                    fill.shader = null; fill.color = Color.argb(65, Color.red(hCol), Color.green(hCol), Color.blue(hCol))
                    oval.set(-rx * 0.92f, topY - 1f, rx * 0.92f, -50f)
                    c.drawArc(oval, 180f, 180f, false, fill)
                }
                HairStyle.CURLY_WAVY_CROP -> {
                    // Natural Organic Curved/Peaked Hairline (NOT a ruler-straight horizontal cut!) + Tapered Temples + Curly Crown
                    val hlY = topY + 14.5f // ~ -57.0f
                    hairPath.moveTo(-rx * 0.96f, -38f)
                    hairPath.lineTo(-rx * 1.03f, -54f)
                    hairPath.cubicTo(-rx * 1.02f, crownControlY, rx * 1.02f, crownControlY, rx * 1.03f, -54f)
                    hairPath.lineTo(rx * 0.96f, -38f)
                    // Inner tapered temple & natural wavy M-arch hairline
                    hairPath.lineTo(rx * 0.88f, -38f)
                    hairPath.cubicTo(rx * 0.88f, -49f, rx * 0.76f, hlY + 3.2f, rx * 0.44f, hlY + 1.2f)
                    hairPath.cubicTo(rx * 0.18f, hlY + 3.4f, 0f, hlY - 2.0f, -rx * 0.28f, hlY + 1.5f)
                    hairPath.cubicTo(-rx * 0.58f, hlY - 1.2f, -rx * 0.86f, hlY + 2.8f, -rx * 0.88f, -49f)
                    hairPath.lineTo(-rx * 0.88f, -38f)
                    hairPath.close()
                    fill.shader = null; fill.color = hCol
                    c.drawPath(hairPath, fill)

                    // 5 rounded curl domes along the top crown
                    for (i in -2..2) {
                        val cx = i * 7.6f + sw * 0.10f
                        val cy = topY + 2.5f - (2 - abs(i)) * 1.8f - lift
                        c.drawCircle(cx, cy, 7.6f, fill)
                    }
                    // Wavy highlight arcs
                    stroke.shader = null; stroke.color = hLight; stroke.strokeWidth = 1.7f; stroke.alpha = 130
                    for (i in -2..2) {
                        val cx = i * 7.6f + sw * 0.10f
                        val cy = topY + 3.5f - (2 - abs(i)) * 1.5f - lift
                        oval.set(cx - 4.4f, cy - 4.0f, cx + 4.4f, cy + 4.0f)
                        c.drawArc(oval, 200f, 140f, false, stroke)
                    }
                    stroke.alpha = 255
                }
                HairStyle.SIDE_PART_CLASSIC -> {
                    val hlY = topY + 14.5f
                    hairPath.moveTo(-rx * 0.96f, -38f)
                    hairPath.lineTo(-rx * 1.02f, -53f)
                    hairPath.cubicTo(-rx, crownControlY, rx, crownControlY, rx * 1.02f, -53f)
                    hairPath.lineTo(rx * 0.96f, -38f)
                    hairPath.lineTo(rx * 0.87f, -38f)
                    hairPath.cubicTo(rx * 0.86f, -49f, 10f, hlY - 0.5f, -10f, hlY - 3.2f)
                    hairPath.quadTo(-19f, hlY + 1.5f, -rx * 0.87f, -48f)
                    hairPath.lineTo(-rx * 0.87f, -38f)
                    hairPath.close()
                    fill.shader = null; fill.color = hCol
                    c.drawPath(hairPath, fill)
                }
                HairStyle.MODERN_QUIF_FADE -> {
                    val hlY = topY + 14.0f
                    hairPath.moveTo(-rx * 0.92f, -46f)
                    hairPath.cubicTo(-rx * 0.96f, crownControlY - 2f, 12f, crownControlY - 6f, rx * 0.94f + sw * 0.15f, topY + 10f - lift)
                    hairPath.quadTo(rx * 0.86f, -46f, rx * 0.84f, -45f)
                    hairPath.cubicTo(10f, hlY, -10f, hlY, -rx * 0.92f, -46f)
                    hairPath.close()
                    fill.shader = null; fill.color = hCol
                    c.drawPath(hairPath, fill)
                }
                HairStyle.SHORT_SPIKY -> {
                    val hlY = topY + 15.0f
                    hairPath.moveTo(-rx * 0.98f, -40f)
                    hairPath.cubicTo(-rx * 1.05f, crownControlY - 3f, rx * 1.05f, crownControlY - 3f, rx * 0.98f, -40f)
                    hairPath.lineTo(rx * 0.86f, -40f)
                    hairPath.lineTo(18f + sw * 0.12f, hlY + 2f - lift)
                    hairPath.lineTo(9f, hlY - 3f)
                    hairPath.lineTo(2f + sw * 0.15f, hlY + 2.5f - lift)
                    hairPath.lineTo(-7f, hlY - 3.2f)
                    hairPath.lineTo(-15f + sw * 0.12f, hlY + 2f - lift)
                    hairPath.lineTo(-rx * 0.86f, -40f)
                    hairPath.close()
                    fill.shader = null; fill.color = hCol
                    c.drawPath(hairPath, fill)
                }
                else -> {
                    // Female & Child Parted Crown Hair (LONG_WAVY, PIGTAILS_RIBBONS, TWO_BRAIDS_JASMINE, BOB_CUTE, CURLY_PUFF)
                    // Outer dome uses crownControlY = topY - 17f so it 100% covers topY with ZERO bald skin gap above the hair!
                    val partX = -2.5f
                    val partY = topY + 12.2f
                    hairPath.moveTo(-rx * 1.02f, -36f)
                    hairPath.cubicTo(-rx * 1.08f, -55f, -rx * 0.95f, crownControlY, 0f, topY - 5.5f - lift)
                    hairPath.cubicTo(rx * 0.95f, crownControlY, rx * 1.08f, -55f, rx * 1.02f, -36f)
                    hairPath.lineTo(rx * 0.88f, -36f)
                    // Graceful parted curtain arch framing the forehead & temples (never a flat horizontal bar!)
                    hairPath.cubicTo(rx * 0.84f, -49f, rx * 0.45f, partY + 3.5f, partX, partY)
                    hairPath.cubicTo(-rx * 0.45f, partY + 3.5f, -rx * 0.84f, -49f, -rx * 0.88f, -36f)
                    hairPath.close()
                    fill.shader = null; fill.color = hCol
                    c.drawPath(hairPath, fill)

                    // Center-part hair shine line & silky temple highlight arcs
                    stroke.shader = null; stroke.color = hLight; stroke.strokeWidth = 1.6f; stroke.alpha = 135
                    c.drawLine(partX, topY - 3.5f, partX, partY - 0.5f, stroke)
                    oval.set(-rx * 0.78f, topY + 2f, -4f, topY + 14f)
                    c.drawArc(oval, 200f, 110f, false, stroke)
                    oval.set(4f, topY + 2f, rx * 0.78f, topY + 14f)
                    c.drawArc(oval, 230f, 110f, false, stroke)
                    stroke.alpha = 255

                    // Cute pink hair clip on the upper-left side of parted hair for Girl Child / hairBow
                    if (isChild && isFem || showAcc("bow", spec.hairBow)) {
                        c.save()
                        c.translate(-rx * 0.56f, topY + 11.5f)
                        c.rotate(-18f)
                        fill.color = Color.rgb(0xFF, 0x40, 0x81)
                        oval.set(-5.2f, -2.2f, 5.2f, 2.2f)
                        c.drawRoundRect(oval, 2f, 2f, fill)
                        fill.color = Color.rgb(0xFF, 0xC1, 0x07)
                        c.drawCircle(-2.2f, 0f, 1.2f, fill)
                        c.restore()
                    }
                }
            }
        }

        if (spec.hasBindi) {
            fill.shader = null
            fill.color = Color.rgb(0x88, 0x0E, 0x2F)
            c.drawCircle(p.lookX * 0.5f, -47.8f, 1.9f, fill)
            fill.color = Color.rgb(0xFF, 0xD5, 0x4F)
            c.drawCircle(p.lookX * 0.5f, -47.8f, 0.7f, fill)
        }

        if (showAcc("jasmine", spec.jasmine) && spec.hairStyle != HairStyle.TWO_BRAIDS_JASMINE) {
            for (i in -3..3) {
                val ang = (i * 14f - 90f) * (Math.PI.toFloat() / 180f)
                val jx = cos(ang) * 31f
                val jy = HEAD_CY + sin(ang) * 31f
                fill.color = Color.WHITE; c.drawCircle(jx, jy, 3.1f, fill)
                fill.color = Color.rgb(0xFF, 0xF5, 0x9D); c.drawCircle(jx, jy, 1.2f, fill)
            }
        }
        if (showAcc("bow", spec.hairBow) && spec.hairStyle != HairStyle.PIGTAILS_RIBBONS && !(isChild && isFem)) {
            c.save(); c.translate(18f, -64f); c.rotate(14f + sw * 0.25f)
            fill.color = accCol
            oval.set(-9f, -5f, -1f, 5f); c.drawOval(oval, fill); c.drawOval(oval, outline)
            oval.set(1f, -5f, 9f, 5f); c.drawOval(oval, fill); c.drawOval(oval, outline)
            c.drawCircle(0f, 0f, 3f, fill); c.drawCircle(0f, 0f, 3f, outline)
            c.restore()
        }

        if (showAcc("glasses", spec.glasses)) {
            stroke.shader = null; stroke.color = Color.rgb(0x26, 0x32, 0x38); stroke.strokeWidth = 2.2f
            oval.set(-20.5f, -45.5f, -3.5f, -34.5f); c.drawRoundRect(oval, 3.5f, 3.5f, stroke)
            oval.set(3.5f, -45.5f, 20.5f, -34.5f); c.drawRoundRect(oval, 3.5f, 3.5f, stroke)
            c.drawLine(-3.5f, -40f, 3.5f, -40f, stroke)
            c.drawLine(-20.5f, -41f, -rx, -39f, stroke)
            c.drawLine(20.5f, -41f, rx, -39f, stroke)
        }

        if (p.laser > 0.02f || p.anger > 0.55f) {
            val a = max(p.laser, (p.anger - 0.5f) * 2f).coerceIn(0f, 1f)
            val ey = -39.5f
            fill.shader = null; fill.color = Color.rgb(0xFF, 0x3B, 0x2F); fill.alpha = (210 * a).toInt()
            c.drawCircle(-11.2f + p.lookX, ey, 7.2f + 2.5f * abs(sin(t * 28f)), fill)
            c.drawCircle(11.2f + p.lookX, ey, 7.2f + 2.5f * abs(sin(t * 28f)), fill)
            fill.color = Color.WHITE; fill.alpha = (255 * a).toInt()
            c.drawCircle(-11.2f + p.lookX, ey, 2.8f, fill)
            c.drawCircle(11.2f + p.lookX, ey, 2.8f, fill)
            fill.alpha = 255
        }
    }

    override fun drawBack(c: Canvas, rig: MonkeyRig, p: Pose, t: Float) {
        stepSecondaryPhysics(rig, p, t)
        drawBehind(c, rig, p, t)
        drawLegs(c, rig)
        drawTorso(c, rig)
        c.save(); c.concat(rig.head)
        drawBackHair(c, p, t)
        fill.shader = null
        fill.color = if (spec.hairStyle == HairStyle.BALD_OR_BUZZ) spec.skinColor else spec.hairColor
        c.drawCircle(0f, HEAD_CY, HEAD_R, fill)
        c.drawCircle(0f, HEAD_CY, HEAD_R, outline)
        c.restore()
        drawArms(c, rig)
    }

    companion object {
        fun lighten(col: Int, delta: Int): Int = Color.rgb(
            (Color.red(col) + delta).coerceIn(0, 255),
            (Color.green(col) + delta).coerceIn(0, 255),
            (Color.blue(col) + delta).coerceIn(0, 255),
        )

        fun darken(col: Int, factor: Float): Int = Color.rgb(
            (Color.red(col) * factor).toInt().coerceIn(0, 255),
            (Color.green(col) * factor).toInt().coerceIn(0, 255),
            (Color.blue(col) * factor).toInt().coerceIn(0, 255),
        )
    }
}

class ChibiPeekFace(private val customChar: CustomCharacter) : PeekFace(customChar.id) {
    override val wardrobeShown: Set<String> = setOf("bow", "jasmine", "glasses")

    override fun drawFace(c: Canvas, wear: Wear?, s: State) {
        val sp = customChar.spec
        val tw = s.tw; val cy = s.cy
        if (sp.hairStyle != HairStyle.BALD_OR_BUZZ) {
            fill(sp.hairColor)
            c.drawCircle(-0.08f * tw, cy, tw * 0.66f, paint)
        }
        fill(sp.skinColor)
        c.drawCircle(0.05f * tw, cy, tw * 0.56f, paint)
        if (sp.hairStyle != HairStyle.BALD_OR_BUZZ) {
            fill(sp.hairColor)
            c.drawCircle(-0.12f * tw, cy - 0.35f * tw, tw * 0.36f, paint)
        }
        if (sp.facialHair != FacialHairStyle.NONE) {
            stroke(sp.hairColor, 0.08f * tw)
            c.drawLine(0.38f * tw, cy - 0.14f * tw, 0.38f * tw, cy + 0.24f * tw, paint)
        }
        dotEyes(c, s, tw * 0.28f, tw * 0.085f, Color.rgb(0x2B, 0x1A, 0x10))
        if (sp.glasses) {
            shades(c, s, tw * 0.28f)
        }
    }
}
