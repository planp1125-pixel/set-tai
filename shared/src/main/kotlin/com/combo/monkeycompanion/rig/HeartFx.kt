package com.combo.monkeycompanion.rig

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.combo.monkeycompanion.heart.ComfortKind
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * v8.0 Heart & Care + Co-Presence visual props, drawn directly inside Settai's own window
 * (never dependent on the external FX window):
 *  - [drawComfort]: SIGN_CARD (wooden/cardboard cheer sign), GLASS_KNOCK_HUG (glass tap ripples + paw prints + hearts),
 *    OFFER_COFFEE (steaming brass davara-tumbler of filter coffee).
 *  - [drawFocusDesk]: wooden study desk, warm desk lamp, open notebook, headband & Pomodoro countdown badge.
 *  - [drawBreathingAura]: expanding/contracting 4-4-6 calm breathing aura rings around the belly/chest.
 *  - [drawCampfire]: crackling late-night campfire with crossed logs, flickering flames and rising embers.
 */
object HeartFx {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val r = RectF()

    private fun fill(col: Int) { p.style = Paint.Style.FILL; p.color = col; p.shader = null }
    private fun stroke(col: Int, w: Float) { p.style = Paint.Style.STROKE; p.color = col; p.strokeWidth = w; p.strokeCap = Paint.Cap.ROUND; p.shader = null }

    /** Applies warm body posture adjustments during a physical comfort animation. */
    fun applyComfortPose(pose: Pose, kind: ComfortKind, t: Float) {
        when (kind) {
            ComfortKind.SIGN_CARD -> {
                pose.armLRaise = 135f; pose.armRRaise = 135f
                pose.armLElbow = -25f; pose.armRElbow = -25f
                pose.mouthSmile = 0.9f; pose.bodyRot = 3f * sin(t * 3.2f)
            }
            ComfortKind.GLASS_KNOCK_HUG -> {
                val tapPhase = (t % 2.4f)
                if (tapPhase < 0.7f) {
                    // Gently knocking on the inside of the glass (*tap-tap*)
                    pose.armRRaise = 95f + 25f * sin(t * 22f)
                    pose.armRElbow = 40f
                } else {
                    // Pressing both paws and cheek warmly against the glass for a screen hug
                    pose.armLRaise = 85f; pose.armRRaise = 85f
                    pose.armLElbow = 20f; pose.armRElbow = 20f
                    pose.squashX = 1.08f; pose.squashY = 0.95f
                    pose.headRot = -8f
                }
                pose.mouthSmile = 1.0f; pose.eyeOpen = 0.25f
            }
            ComfortKind.OFFER_COFFEE -> {
                pose.armRRaise = 75f; pose.armRElbow = 35f
                pose.mouthSmile = 0.85f; pose.headRot = 4f * sin(t * 2.5f)
            }
        }
    }

    /** Draws the active physical comfort animation in Settai's own window. */
    fun drawComfort(c: Canvas, kind: ComfortKind, signText: String, hx: Float, hy: Float, fx: Float, fy: Float, s: Float, t: Float) {
        when (kind) {
            ComfortKind.SIGN_CARD -> {
                val bob = 3f * s * sin(t * 4f)
                val sw = 156f * s
                val sh = 46f * s
                val cx = fx
                val cy = (hy - 28f * s + bob).coerceAtLeast(sh / 2f + 6f * s)
                // Wooden sticks held up from hands
                stroke(Color.rgb(0x8D, 0x5B, 0x32), 4.5f * s)
                c.drawLine(cx - 32f * s, cy + sh / 2f, cx - 22f * s, hy + 28f * s, p)
                c.drawLine(cx + 32f * s, cy + sh / 2f, cx + 22f * s, hy + 28f * s, p)
                // Warm cardboard sign board
                r.set(cx - sw / 2f, cy - sh / 2f, cx + sw / 2f, cy + sh / 2f)
                fill(Color.rgb(0xFF, 0xF3, 0xD6)); c.drawRoundRect(r, 10f * s, 10f * s, p)
                stroke(Color.rgb(0xA6, 0x6E, 0x38), 3f * s); c.drawRoundRect(r, 10f * s, 10f * s, p)
                // Corner tape / heart accents
                drawHeart(c, r.left + 14f * s, r.top + 12f * s, 6f * s, Color.rgb(0xE5, 0x39, 0x35))
                drawHeart(c, r.right - 14f * s, r.top + 12f * s, 6f * s, Color.rgb(0xE5, 0x39, 0x35))
                // Hand-lettered sign text
                textP.color = Color.rgb(0x4A, 0x2C, 0x14)
                textP.textSize = 13.5f * s
                val clean = signText.take(24)
                c.drawText(clean, cx, cy + 5f * s, textP)
            }

            ComfortKind.GLASS_KNOCK_HUG -> {
                val cy = (hy + fy) * 0.48f
                // Concentric glass knock ripples (*tap-tap*)
                for (i in 0..1) {
                    val phase = ((t * 1.4f + i * 0.5f) % 1f)
                    val rad = (14f + 46f * phase) * s
                    val alpha = ((1f - phase) * 190).toInt().coerceIn(0, 255)
                    stroke(Color.argb(alpha, 0xFF, 0xE0, 0x82), 2.8f * s)
                    c.drawCircle(fx, cy, rad, p)
                }
                // Warm paws pressed against the inside of the glass
                for (side in intArrayOf(-1, 1)) {
                    val px = fx + side * 34f * s
                    val py = cy + 6f * s
                    fill(Color.argb(215, 0xFF, 0xCC, 0xBC))
                    c.drawCircle(px, py, 9f * s, p)
                    for (toe in -1..1) {
                        c.drawCircle(px + toe * 6.5f * s, py - 9.5f * s, 3.6f * s, p)
                    }
                }
                // Floating hearts rising around Settai
                for (i in 0..2) {
                    val k = ((t * 0.7f + i * 0.33f) % 1f)
                    val hx0 = fx + (i - 1) * 32f * s + 6f * s * sin(t * 5f + i)
                    val hy0 = hy + 10f * s - k * 48f * s
                    val a = ((1f - k) * 240).toInt().coerceIn(0, 255)
                    drawHeart(c, hx0, hy0, (7f + 3f * (1f - k)) * s, Color.argb(a, 0xF4, 0x43, 0x36))
                }
            }

            ComfortKind.OFFER_COFFEE -> {
                val cx = fx + 44f * s
                val cy = fy - 24f * s
                // Brass davara (saucer bowl)
                fill(Color.rgb(0xD4, 0x9B, 0x27))
                r.set(cx - 20f * s, cy + 6f * s, cx + 20f * s, cy + 18f * s)
                c.drawRoundRect(r, 6f * s, 6f * s, p)
                // Brass tumbler
                fill(Color.rgb(0xE6, 0xB4, 0x3C))
                r.set(cx - 12f * s, cy - 14f * s, cx + 12f * s, cy + 10f * s)
                c.drawRoundRect(r, 4f * s, 4f * s, p)
                // Frothy filter coffee top
                fill(Color.rgb(0xFF, 0xF8, 0xE7))
                r.set(cx - 11f * s, cy - 16f * s, cx + 11f * s, cy - 10f * s)
                c.drawOval(r, p)
                // Rising steam curls + warm heart
                stroke(Color.argb(190, 0xFF, 0xF8, 0xE7), 2.4f * s)
                for (i in -1..1) {
                    val sx = cx + i * 6f * s
                    val w = 3f * s * sin(t * 5f + i)
                    path.reset()
                    path.moveTo(sx, cy - 18f * s)
                    path.quadTo(sx + w, cy - 28f * s, sx - w, cy - 38f * s)
                    c.drawPath(path, p)
                }
                drawHeart(c, cx, cy - 46f * s + 3f * s * sin(t * 4f), 6.5f * s, Color.rgb(0xE5, 0x39, 0x35))
            }
        }
    }

    /** Co-Presence 1: Focus / Study Desk Buddy (wooden desk, warm lamp, open notebook, headband & timer badge). */
    fun drawFocusDesk(c: Canvas, fx: Float, fy: Float, hx: Float, hy: Float, s: Float, t: Float, timerLabel: String) {
        // Focus headband on head
        fill(Color.rgb(0xE5, 0x39, 0x35))
        r.set(hx - 24f * s, hy + 8f * s, hx + 24f * s, hy + 15f * s)
        c.drawRoundRect(r, 4f * s, 4f * s, p)

        // Wooden study desk in front of Settai
        val dw = 128f * s
        val dh = 26f * s
        val topY = fy - 28f * s
        // Desk legs
        fill(Color.rgb(0x6D, 0x43, 0x23))
        c.drawRect(fx - dw * 0.44f, topY + dh * 0.5f, fx - dw * 0.36f, fy + 2f * s, p)
        c.drawRect(fx + dw * 0.36f, topY + dh * 0.5f, fx + dw * 0.44f, fy + 2f * s, p)
        // Desk tabletop
        fill(Color.rgb(0x9C, 0x64, 0x34))
        r.set(fx - dw / 2f, topY, fx + dw / 2f, topY + dh)
        c.drawRoundRect(r, 6f * s, 6f * s, p)
        stroke(Color.rgb(0x5A, 0x36, 0x18), 2f * s)
        c.drawRoundRect(r, 6f * s, 6f * s, p)

        // Open notebook on center of desk + animated scribbling pen
        val nbX = fx - 6f * s
        val nbY = topY - 4f * s
        fill(Color.rgb(0xFF, 0xFA, 0xF0))
        r.set(nbX - 24f * s, nbY - 6f * s, nbX + 24f * s, nbY + 8f * s)
        c.drawRoundRect(r, 3f * s, 3f * s, p)
        stroke(Color.rgb(0x90, 0xA4, 0xAE), 1.4f * s)
        c.drawLine(nbX, nbY - 6f * s, nbX, nbY + 8f * s, p)
        val penX = nbX + 8f * s * sin(t * 9f)
        stroke(Color.rgb(0x1E, 0x88, 0xE5), 2.5f * s)
        c.drawLine(penX, nbY, penX + 10f * s, nbY - 14f * s, p)

        // Warm study lamp on left corner of desk
        val lx = fx - dw * 0.36f
        val ly = topY
        stroke(Color.rgb(0x37, 0x47, 0x4F), 3f * s)
        c.drawLine(lx, ly, lx - 6f * s, ly - 26f * s, p)
        c.drawLine(lx - 6f * s, ly - 26f * s, lx + 8f * s, ly - 32f * s, p)
        // Warm lamp cone glow
        fill(Color.argb(75, 0xFF, 0xE0, 0x82))
        path.reset()
        path.moveTo(lx + 8f * s, ly - 30f * s)
        path.lineTo(lx - 10f * s, ly + 4f * s)
        path.lineTo(lx + 36f * s, ly + 4f * s)
        path.close()
        c.drawPath(path, p)
        // Lamp shade
        fill(Color.rgb(0xFF, 0xB3, 0x00))
        c.drawCircle(lx + 8f * s, ly - 31f * s, 6.5f * s, p)

        // Countdown timer pill badge on front of desk
        val pillW = 78f * s
        val pillH = 18f * s
        r.set(fx - pillW / 2f, topY + 5f * s, fx + pillW / 2f, topY + 5f * s + pillH)
        fill(Color.rgb(0x3A, 0x22, 0x10))
        c.drawRoundRect(r, pillH / 2f, pillH / 2f, p)
        textP.color = Color.rgb(0xFF, 0xF3, 0xD6)
        textP.textSize = 11f * s
        c.drawText(timerLabel, fx, r.centerY() + 4f * s, textP)
    }

    /** Co-Presence 2: 60-Second Calm Breathing Aura (4s inhale · 4s hold · 6s exhale). */
    fun drawBreathingAura(c: Canvas, cx: Float, cy: Float, s: Float, radiusFrac: Float, phaseLabel: String, secsLeft: Int) {
        val baseR = (30f + 42f * radiusFrac.coerceIn(0.3f, 1f)) * s
        // Outer soft aura ring
        fill(Color.argb(55, 0x64, 0xB5, 0xF6))
        c.drawCircle(cx, cy, baseR * 1.22f, p)
        // Middle glowing ring
        fill(Color.argb(90, 0x80, 0xDE, 0xEA))
        c.drawCircle(cx, cy, baseR, p)
        stroke(Color.argb(210, 0xE0, 0xF7, 0xFA), 3f * s)
        c.drawCircle(cx, cy, baseR, p)

        // Phase cue pill above/below
        val label = "$phaseLabel (${secsLeft}s)"
        val pw = 136f * s
        val ph = 22f * s
        r.set(cx - pw / 2f, cy - baseR - ph - 6f * s, cx + pw / 2f, cy - baseR - 6f * s)
        if (r.top < 4f) r.offset(0f, baseR * 2f + ph + 12f * s)
        fill(Color.argb(220, 0x1B, 0x36, 0x44))
        c.drawRoundRect(r, ph / 2f, ph / 2f, p)
        textP.color = Color.rgb(0xE0, 0xF7, 0xFA)
        textP.textSize = 11.5f * s
        c.drawText(label, r.centerX(), r.centerY() + 4f * s, textP)
    }

    /** Co-Presence 3: Crackling Night Campfire at the bottom edge (between lead and neighbour when in Duo/Trio). */
    fun drawCampfire(c: Canvas, fx: Float, fy: Float, s: Float, t: Float) {
        // Warm ambient firelight glow
        val flicker = 1f + 0.08f * sin(t * 17f) + 0.05f * cos(t * 29f)
        fill(Color.argb(65, 0xFF, 0x98, 0x00))
        c.drawCircle(fx, fy - 14f * s, 46f * s * flicker, p)

        // Crossed wooden logs + stones
        stroke(Color.rgb(0x5D, 0x3A, 0x1A), 6f * s)
        c.drawLine(fx - 20f * s, fy - 2f * s, fx + 20f * s, fy - 10f * s, p)
        c.drawLine(fx - 20f * s, fy - 10f * s, fx + 20f * s, fy - 2f * s, p)
        fill(Color.rgb(0x78, 0x90, 0x9C))
        for (i in -2..2) c.drawCircle(fx + i * 9f * s, fy, 4.2f * s, p)

        // 3 layered flame tongues (outer deep orange, middle amber, inner cream)
        val sway = 4f * s * sin(t * 13f)
        drawFlameTongue(c, fx, fy - 5f * s, 18f * s, 36f * s * flicker, sway, Color.rgb(0xFF, 0x57, 0x22))
        drawFlameTongue(c, fx, fy - 5f * s, 12f * s, 26f * s * flicker, -sway * 0.6f, Color.rgb(0xFF, 0xB3, 0x00))
        drawFlameTongue(c, fx, fy - 5f * s, 6.5f * s, 15f * s * flicker, sway * 0.3f, Color.rgb(0xFF, 0xF8, 0xE1))

        // Rising ember sparks
        for (i in 0..2) {
            val k = ((t * 0.9f + i * 0.33f) % 1f)
            val ex = fx + (i - 1) * 8f * s + 5f * s * sin(t * 8f + i)
            val ey = fy - 18f * s - k * 34f * s
            val a = ((1f - k) * 230).toInt().coerceIn(0, 255)
            fill(Color.argb(a, 0xFF, 0xD5, 0x4F))
            c.drawCircle(ex, ey, (2.8f - 1.5f * k) * s, p)
        }
    }

    private fun drawFlameTongue(c: Canvas, x: Float, baseY: Float, halfW: Float, h: Float, tipDx: Float, col: Int) {
        fill(col)
        path.reset()
        path.moveTo(x - halfW, baseY)
        path.quadTo(x - halfW * 0.9f, baseY - h * 0.55f, x + tipDx, baseY - h)
        path.quadTo(x + halfW * 0.9f, baseY - h * 0.55f, x + halfW, baseY)
        path.close()
        c.drawPath(path, p)
    }

    fun drawHeart(c: Canvas, cx: Float, cy: Float, r0: Float, col: Int) {
        fill(col)
        path.reset()
        path.moveTo(cx, cy + r0 * 0.7f)
        path.cubicTo(cx - r0 * 1.2f, cy - r0 * 0.1f, cx - r0 * 0.6f, cy - r0 * 1.1f, cx, cy - r0 * 0.35f)
        path.cubicTo(cx + r0 * 0.6f, cy - r0 * 1.1f, cx + r0 * 1.2f, cy - r0 * 0.1f, cx, cy + r0 * 0.7f)
        path.close()
        c.drawPath(path, p)
    }
}
