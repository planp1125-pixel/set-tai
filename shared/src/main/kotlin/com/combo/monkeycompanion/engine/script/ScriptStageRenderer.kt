package com.combo.monkeycompanion.engine.script

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.engine.action.ActionLibrary
import com.combo.monkeycompanion.engine.expression.Expressions
import com.combo.monkeycompanion.engine.props.Props
import com.combo.monkeycompanion.engine.stagefx.StageFx
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.MonkeyRenderer
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import kotlin.math.min
import kotlin.math.sin

/**
 * v10.32 draws one frame of a [ScriptPlayer] on a full-screen stage: every cast member through its OWN renderer but
 * the SHARED pose pipeline (state animation → library action → expression → fade), held props at the hand, flying
 * projectiles, rays, the clash, vanish smoke in each body's [com.combo.monkeycompanion.character.Character.vanishStyle],
 * speech bubbles and the cracked-glass overlay on top. Same code for any pair of characters.
 */
class ScriptStageRenderer(private val player: ScriptPlayer) {
    private class Slot(val renderer: MonkeyRenderer) { val rig = MonkeyRig(); val pose = Pose(); val ctx = AnimContext() }
    private val slots = player.actors.mapValues { (_, a) -> Slot(a.character.newRenderer().also { it.wear = CharacterRegistry.wear(a.character) }) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true; color = Color.rgb(0x22, 0x22, 0x2A) }
    private val bub = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }
    private val bubLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.rgb(0x88, 0x88, 0x99) }
    private val rect = RectF()
    private val pt = FloatArray(2)
    /** Screen px points per role this frame: eyes, right hand, left hand. */
    private val eyes = HashMap<String, FloatArray>(); private val handR = HashMap<String, FloatArray>(); private val handL = HashMap<String, FloatArray>()

    fun isFor(p: ScriptPlayer) = p === player

    /** Rig scale: px per rig unit for a screen [w] px wide. */
    fun scaleFor(w: Float) = w * 0.0017f

    /** Pose of [role] at the player's current time (tests read it; the draw uses the same). */
    fun poseOf(role: String): Pose? { val a = player.actor(role) ?: return null; val s = slots[role] ?: return null; buildPose(a, s); return s.pose }

    private fun buildPose(a: ScriptPlayer.Actor, s: Slot) {
        val t = player.t
        val walking = a.mover.moving && !a.mover.canFly
        s.ctx.talking = a.saying(t) != null
        a.character.fill(s.pose, if (walking) MonkeyState.WALK else MonkeyState.IDLE, t, t, s.ctx, CharacterRegistry.wear(a.character))
        val p = s.pose
        a.actionAt(t)?.let { ActionLibrary.apply(it, p, t - a.actionT0, min(1f, (t - a.actionT0) / 0.2f)) }
        a.lookDir()?.let { ActionLibrary.apply(ActionLibrary.LookAt.id, p, t, 1f, it * a.mover.facing) }
        a.faceAt(t)?.let { Expressions.apply(it, p, t, min(1f, (t - a.faceT0) / 0.25f)) }
        p.fade = a.mover.alpha
        p.shadow = if (a.mover.y >= 0.85f) 1f else 0f
    }

    fun draw(c: Canvas, w: Float, h: Float) {
        val t = player.t
        val scale = scaleFor(w)
        c.save()
        if (t < player.shakeUntil) c.translate(6f * sin(t * 70f) * w / 1080f, 4f * sin(t * 83f) * w / 1080f)
        // actors
        for ((role, a) in player.actors) {
            val s = slots[role] ?: continue
            buildPose(a, s)
            val x = a.mover.x * w; val y = (a.mover.y + a.mover.bob(t)) * h
            s.rig.layout(s.pose, x, y, scale, a.mover.facing)
            pt[0] = 0f; pt[1] = MonkeyRig.HEAD_CY; s.rig.head.mapPoints(pt); eyes.getOrPut(role) { FloatArray(2) }.let { it[0] = pt[0]; it[1] = pt[1] }
            s.rig.handPoint(false, pt); handR.getOrPut(role) { FloatArray(2) }.let { it[0] = pt[0]; it[1] = pt[1] }
            s.rig.handPoint(true, pt); handL.getOrPut(role) { FloatArray(2) }.let { it[0] = pt[0]; it[1] = pt[1] }
            if (a.mover.alpha > 0.02f) {
                val sv = c.saveLayerAlpha(null, (255 * a.mover.alpha.coerceIn(0f, 1f)).toInt())
                s.renderer.draw(c, s.rig, s.pose, t)
                c.restoreToCount(sv)
                a.held?.let { k -> val hp = handL[role]!!; Props.draw(c, k, hp[0] + a.mover.facing * k.radius * w * 0.6f, hp[1], w, t, a.mover.alpha) }
            }
            if (a.mover.puff > 0.02f) StageFx.smoke(c, x, y - 70f * scale, 80f * scale, a.character.vanishStyle, a.mover.puff, t)
        }
        // projectiles
        for (p in player.projectiles) Props.draw(c, p.kind, p.x * w, p.y * h, w, t)
        // rays
        for (r in player.rays) {
            val pts = when (r.src) { "eyes" -> eyes; "left" -> handL; else -> handR }[r.from] ?: continue
            val (ex, ey) = player.rayEnd(r) ?: continue
            val k = min(1f, (t - r.t0) / 0.15f) * min(1f, (r.t1 - t) / 0.2f)
            if (r.src == "eyes") for (dx in floatArrayOf(-9f, 9f)) StageFx.ray(c, pts[0] + dx * scale, pts[1], ex * w, ey * h, r.color, 5f * scale, k, t)
            else StageFx.ray(c, pts[0], pts[1], ex * w, ey * h, r.color, 9f * scale, k, t)
        }
        if (player.clashOn) {
            val cols = player.rays.filter { it.from == player.clashA || it.from == player.clashB }.map { it.color }
            StageFx.clashBurst(c, player.clashX * w, player.clashY * h, 34f * scale, cols.getOrElse(0) { Color.RED }, cols.getOrElse(1) { Color.YELLOW }, 1f, t)
        }
        // speech
        for ((role, a) in player.actors) {
            val line = a.saying(t) ?: continue
            if (a.mover.alpha < 0.3f) continue
            val e = eyes[role] ?: continue
            text.textSize = 15f * w / 411f
            val tw = text.measureText(line); val pad = 10f * w / 411f
            val bx = e[0].coerceIn(tw / 2 + pad * 2, w - tw / 2 - pad * 2); val by = e[1] - 70f * scale
            rect.set(bx - tw / 2 - pad, by - text.textSize - pad * 0.6f, bx + tw / 2 + pad, by + pad * 0.8f)
            c.drawRoundRect(rect, pad, pad, bub); bubLine.strokeWidth = 1.5f * w / 411f; c.drawRoundRect(rect, pad, pad, bubLine)
            c.drawText(line, bx, by, text)
        }
        c.restore()
        player.crack?.draw(c, w, h, min(1f, (t - player.crackT0) / 0.35f))
    }
}
