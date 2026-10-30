package com.combo.monkeycompanion.rig

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.combo.monkeycompanion.brain.Landings.Style
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** v4.4 smart landings: poses layered on the fall, and the drawn props (cape, leaf, vine). */
object LandingPoses {
    /** The "decision" beat: looks down, scratches head. [k] 0..1. */
    fun decide(p: Pose, k: Float, t: Float) {
        p.lookY = 4f; p.lookX = -1f; p.headRot = -10f * sin(PI.toFloat() * k)
        p.armRRaise = 150f; p.armRElbow = -120f + 14f * sin(t * 26f)      // scratching
        p.browAngle = -8f; p.mouthSmile = 0.1f; p.mouthOpen = 0.08f
        p.legLLift = 4f; p.legRLift = 4f
    }

    /** Falling with [s]; [st] seconds into the fall. */
    fun fall(p: Pose, s: Style, st: Float) {
        when (s) {
            Style.CAPE_FLOAT, Style.SPEED_DIVE -> {                       // fist-first
                p.armRRaise = 170f; p.armRElbow = 0f; p.armLRaise = 150f; p.armLElbow = -40f
                p.legLSwing = -8f; p.legRSwing = 10f; p.mouthSmile = 1f; p.lookY = 2f
                if (s == Style.SPEED_DIVE) { p.squashY = 1.08f; p.squashX = 0.94f; p.eyeOpen = 0.8f; p.browAngle = 8f }
            }
            Style.JETS -> { p.armLRaise = 40f; p.armRRaise = 40f; p.armLElbow = -40f; p.armRElbow = -40f; p.legLSwing = 6f + 3f * abs(sin(st * 30f)); p.legRSwing = 6f }
            Style.SCREAM_JETS -> {
                p.armLRaise = 160f + 15f * sin(st * 24f); p.armRRaise = 160f - 15f * sin(st * 24f); p.armLElbow = 0f; p.armRElbow = 0f
                p.mouthOpen = 0.9f; p.eyeOpen = 1.5f; p.mouthSmile = -0.6f
                p.legLSwing = 20f * sin(st * 20f); p.legRSwing = -20f * sin(st * 20f)
            }
            Style.PARACHUTE -> { p.armLRaise = 165f; p.armRRaise = 165f; p.armLElbow = -10f; p.armRElbow = -10f; p.mouthSmile = 0.9f; p.eyesHappy = 0.6f; p.bodyX = 4f * sin(st * 3f); p.legLSwing = 6f * sin(st * 3f); p.legRSwing = -6f * sin(st * 3f) }
            Style.PEEL_SLIDE -> { p.bodyRot = -18f; p.armLRaise = 100f; p.armRRaise = 100f; p.legLSwing = 30f; p.legRSwing = 30f; p.mouthOpen = 0.4f; p.mouthSmile = 1f; p.peel = 1f; p.peelX = 0f }
            Style.CANNONBALL -> { p.armLRaise = 60f; p.armRRaise = 60f; p.armLElbow = -110f; p.armRElbow = -110f; p.legLLift = 18f; p.legRLift = 18f; p.squashX = 1.08f; p.squashY = 0.9f; p.eyeOpen = 0.2f; p.mouthSmile = 1f }
            Style.NINJA_FLIP -> { p.bodyRot = (st * 720f) % 360f; p.legLLift = 14f; p.legRLift = 14f; p.armLRaise = 80f; p.armRRaise = 80f; p.armLElbow = -90f; p.armRElbow = -90f }
            Style.SNORE_FLOAT -> { p.eyeOpen = 0f; p.zzz = 1f; p.headRot = 12f; p.armLRaise = 30f; p.armRRaise = 30f; p.mouthOpen = 0.15f + 0.1f * sin(st * 3f); p.bodyRot = 6f * sin(st * 1.5f) }
            Style.CHICKEN_FLAP -> { val f = sin(st * 22f); p.armLRaise = 90f + 50f * f; p.armRRaise = 90f + 50f * f; p.armLElbow = -30f; p.armRElbow = -30f; p.mouthOpen = 0.5f; p.eyeOpen = 1.4f }
            Style.VINE_SWING -> { p.armRRaise = 175f; p.armRElbow = 0f; p.armLRaise = 120f; p.legLSwing = 20f; p.legRSwing = 30f; p.mouthOpen = 0.6f; p.mouthSmile = 1f }
            Style.ROCKET_ARC -> {                                           // rocket pose: fists ahead, legs together
                p.armRRaise = 172f; p.armRElbow = 0f; p.armLRaise = 166f; p.armLElbow = -6f
                p.legLSwing = 4f; p.legRSwing = -4f; p.legLLift = 4f; p.legRLift = 4f
                p.mouthSmile = 1f; p.mouthOpen = 0.35f; p.eyeOpen = 1.2f; p.shadow = 0f
                p.tailSway = sin(st * 18f)
            }
            Style.CROW_CARRY -> {                                           // dangling from the collar, legs pedalling
                p.armLRaise = 40f + 20f * sin(st * 9f); p.armRRaise = 40f - 20f * sin(st * 9f); p.armLElbow = -20f; p.armRElbow = -20f
                p.legLSwing = 22f * sin(st * 14f); p.legRSwing = -22f * sin(st * 14f); p.headRot = -8f
                p.mouthOpen = 0.6f; p.eyeOpen = 1.4f; p.mouthSmile = -0.2f; p.shadow = 0f
            }
            Style.SIMPLE -> Unit
        }
    }
}

object LandingFx {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val path = Path()

    /** A red cape flowing up behind the body (falling = the wind blows it up). [neckX/neckY] in px, [s] = rig scale. */
    fun drawCape(c: Canvas, neckX: Float, neckY: Float, s: Float, t: Float) {
        val w = 48f * s; val h = 125f * s
        val flap = 10f * s * sin(t * 14f)
        path.reset()
        path.moveTo(neckX - w * 0.45f, neckY)
        path.quadTo(neckX - w - flap, neckY - h * 0.5f, neckX - w * 0.8f + flap, neckY - h)
        path.lineTo(neckX + w * 0.8f - flap, neckY - h * 0.95f)
        path.quadTo(neckX + w + flap, neckY - h * 0.5f, neckX + w * 0.45f, neckY)
        path.close()
        fill.color = Color.rgb(0xD3, 0x2F, 0x2F); c.drawPath(path, fill)
        line.color = Color.rgb(0x7F, 0x1D, 0x1D); line.strokeWidth = 2f * s; c.drawPath(path, line)
    }

    /** A big leaf held up as a parachute; [x], [y] = hands. */
    fun drawLeaf(c: Canvas, x: Float, y: Float, s: Float, t: Float) {
        val w = 62f * s; val h = 26f * s; val tilt = 6f * sin(t * 3f)
        @Suppress("NAME_SHADOWING") val y = y - 34f * s   // canopy sits above the raised hands, stems reach down
        c.save(); c.rotate(tilt, x, y)
        path.reset()
        path.moveTo(x - w, y - h * 0.2f)
        path.quadTo(x, y - h * 2.2f, x + w, y - h * 0.2f)
        path.quadTo(x, y - h * 0.9f, x - w, y - h * 0.2f)
        fill.color = Color.rgb(0x43, 0xA0, 0x47); c.drawPath(path, fill)
        line.color = Color.rgb(0x1B, 0x5E, 0x20); line.strokeWidth = 2.5f * s
        c.drawLine(x - w * 0.9f, y - h * 0.3f, x + w * 0.9f, y - h * 0.3f, line)
        c.drawLine(x, y - h * 0.3f, x, y, line)
        c.restore()
    }

    /** The imaginary vine from the top of the screen to the hand. */
    fun drawVine(c: Canvas, topX: Float, topY: Float, handX: Float, handY: Float, s: Float) {
        line.color = Color.rgb(0x55, 0x8B, 0x2F); line.strokeWidth = 5f * s
        path.reset(); path.moveTo(topX, topY); path.quadTo((topX + handX) / 2f + 20f * s, (topY + handY) / 2f, handX, handY)
        c.drawPath(path, line)
        fill.color = Color.rgb(0x7C, 0xB3, 0x42)
        for (i in 1..4) {
            val f = i / 5f
            val lx = topX + (handX - topX) * f + 10f * s; val ly = topY + (handY - topY) * f
            c.drawOval(RectF(lx - 7f * s, ly - 4f * s, lx + 7f * s, ly + 4f * s), fill)
        }
    }
}
