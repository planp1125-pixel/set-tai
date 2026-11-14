package com.combo.monkeycompanion.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.combo.monkeycompanion.audio.Sfx
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.character.Character
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.engine.expression.Expressions
import com.combo.monkeycompanion.engine.feel.TouchFeel
import com.combo.monkeycompanion.engine.game.SlingGame
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.ArmorScatter
import com.combo.monkeycompanion.rig.ArmourRenderer
import com.combo.monkeycompanion.rig.MonkeyRenderer
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * v10.40 Slingshot game: ONE full-screen game window (same window type and rules as [ScriptStageView]). It draws the
 * stone character and the target through their own renderers (procedural, no images), takes the pull-and-release
 * touch, and shows the score bar and the end card. The rules live in [SlingGame]; the live companion windows are
 * hidden and paused while it is open ([SlingGameHub]).
 */
@SuppressLint("ViewConstructor")
class SlingGameView(context: Context, game0: SlingGame) : View(context), Choreographer.FrameCallback {
    var game: SlingGame = game0; private set

    /** One character drawn through its own renderer and the shared pose pipeline. */
    private class Body(val ch: Character) {
        val renderer: MonkeyRenderer = ch.newRenderer().also { it.wear = CharacterRegistry.wear(ch) }
        val rig = MonkeyRig(); val pose = Pose(); val ctx = AnimContext()
        val scatter = ArmorScatter()
    }
    private val bodies = HashMap<String, Body>()
    private fun body(id: String): Body? = bodies[id] ?: (CharacterRegistry.byId(id) ?: CharacterRegistry.allWithCustom.firstOrNull { it.id == id })?.let { Body(it).also { b -> bodies[id] = b } }

    private val d = resources.displayMetrics.density
    private var on = false
    private var lastNs = 0L
    private var clock = 0f
    private var downX = 0f; private var downY = 0f; private var grabbing = false
    private var notch = 0
    private var word = ""; private var wordT = -9f; private var wordX = 0f; private var wordY = 0f
    private var best = 0; private var newBest = false; private var overAt = -1f
    private val puffs = ArrayList<FloatArray>()   // x, y, vx, vy, age
    // v10.41 effects
    private var freeze = 0f                        // hit-stop: game time stands still for a blink
    private var shakeUntil = -1f
    private var crack: com.combo.monkeycompanion.engine.stagefx.StageFx.Crack? = null
    private var crackT = -9f
    private var banner = ""; private var bannerT = -9f
    private val trail = ArrayList<FloatArray>()   // x, y, age
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val rect = RectF()
    private val closeBtn = RectF(); private val againBtn = RectF(); private val doneBtn = RectF()
    private val topPad: Float = run {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        (if (id > 0) runCatching { resources.getDimensionPixelSize(id) }.getOrDefault(0) else 0) + 10f * d
    }

    private val w get() = width.toFloat().coerceAtLeast(1f)
    private fun px(v: Float) = v * w
    /** Rig scale: the character is [SlingGame.BODY_H] screen widths tall. */
    private val rigScale get() = w * SlingGame.BODY_H / MonkeyRig.HEIGHT

    // ---------------- frame loop ----------------
    override fun doFrame(frameTimeNanos: Long) {
        if (!on) return
        val dt = if (lastNs == 0L) 0f else ((frameTimeNanos - lastNs) / 1e9f).coerceIn(0f, 0.05f)
        lastNs = frameTimeNanos
        advance(dt)
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    /** One step of game time (tests call this directly). */
    fun advance(dt: Float) {
        clock += dt
        if (freeze > 0f) freeze -= dt else game.step(dt)
        for (e in game.drain()) onEvent(e)
        if (game.phase == SlingGame.Phase.FLY && freeze <= 0f) trail.add(floatArrayOf(px(game.bx), px(game.by - SlingGame.BODY_H / 2f), 0f))
        val tr = trail.iterator()
        while (tr.hasNext()) { val q = tr.next(); q[2] += dt; if (q[2] > 0.25f) tr.remove() }
        if (crack != null && clock - crackT > 1.6f) crack = null
        val it = puffs.iterator()
        while (it.hasNext()) { val p = it.next(); p[4] += dt; p[0] += p[2] * dt; p[1] += p[3] * dt; p[3] += 900f * d * dt; if (p[4] > 0.7f) it.remove() }
        for (b in bodies.values) if (b.scatter.active) {
            b.scatter.step(dt, game.phase == SlingGame.Phase.SPLAT || game.phase == SlingGame.Phase.FLY)
            (b.renderer as? ArmourRenderer)?.shed = if (b.scatter.active) 1f else 0f
        }
        if (game.over && overAt >= 0f && clock - overAt > END_CARD_SEC) SlingGameHub.finish(context)
    }

    private fun onEvent(e: SlingGame.Event) {
        when (e) {
            SlingGame.Event.SNAP -> { sfx(Sfx.S.WHOOSH, 0.5f); buzz { Feel.play(context, TouchFeel.slingSnap(lastTension)) } }
            SlingGame.Event.HIT -> { sfx(Sfx.S.BONK, 0.7f); say("BONK!", game.bx, game.by - SlingGame.BODY_H); freeze = 0.07f }
            SlingGame.Event.DOUBLE_SPLAT -> {
                sfx(Sfx.S.SPLAT, 0.9f); sfx(Sfx.S.CRACK, 0.35f)
                buzz { Feel.playSharpHit(context, 1f, 0.8f) }
                say(when { game.streak >= 3 -> "ON FIRE! 🔥"; game.streak == 2 -> "2 in a row!"; else -> "DOUBLE SPLAT!" }, game.bx, game.by - SlingGame.BODY_H * 1.1f)
                burst(18); shed(game.bulletId); game.target?.let { shed(it.id) }
                shakeUntil = clock + 0.35f
                crack = com.combo.monkeycompanion.engine.stagefx.StageFx.Crack(game.bx, (game.by - SlingGame.BODY_H / 2f) / game.aspect, 7L + game.score); crackT = clock
                banner = "+${SlingGame.BONUS_SEC.toInt()} s ⏱"; bannerT = clock
            }
            SlingGame.Event.SPLAT -> {
                sfx(Sfx.S.SPLAT, 0.6f); buzz { Feel.playSharpHit(context, 0.6f, 0.5f) }
                say("MISS!", game.bx, game.by - SlingGame.BODY_H * 1.1f); burst(8); shed(game.bulletId)
            }
            SlingGame.Event.TARGET_IN -> sfx(Sfx.S.POP, 0.4f)
            SlingGame.Event.TARGET_OUT -> Unit
            SlingGame.Event.LEVEL_UP -> { banner = "LEVEL ${game.level}!"; bannerT = clock; sfx(Sfx.S.TADA, 0.5f) }
            SlingGame.Event.TRICK -> sfx(if (game.target?.trick == SlingGame.Trick.VANISH) Sfx.S.POP else Sfx.S.ZIP, 0.35f)
            SlingGame.Event.LAST_SECONDS -> sfx(Sfx.S.PLUCK, 0.5f)
            SlingGame.Event.OVER -> {
                overAt = clock
                val r = SlingGameHub.record(context, game.score)
                best = r.first; newBest = r.second
                sfx(if (newBest && game.score > 0) Sfx.S.TADA else Sfx.S.WAHWAH, 0.6f)
            }
        }
    }

    private var lastTension = 0f
    private fun sfx(s: Sfx.S, vol: Float) { runCatching { Sfx.play(context, s, vol) } }
    private inline fun buzz(f: () -> Unit) { runCatching { f() } }
    private fun say(s: String, x: Float, y: Float) { word = s; wordT = clock; wordX = px(x).coerceIn(w * 0.2f, w * 0.8f); wordY = px(y).coerceAtLeast(topPad + 90f * d) }
    private fun burst(n: Int) {
        val x = px(game.bx); val y = px(game.by - SlingGame.BODY_H / 2f)
        for (i in 0 until n) { val a = i * 6.283f / n; val s = (220f + 60f * (i % 3)) * d; puffs.add(floatArrayOf(x, y, cos(a) * s, sin(a) * s - 120f * d, 0f)) }
    }
    /** Bolt's armour plates scatter on a splat, as they do outside the game. */
    private fun shed(id: String) {
        val b = body(id) ?: return
        val r = b.renderer as? ArmourRenderer ?: return
        if (b.scatter.active) return
        if (b.scatter.start(b.rig, 0f, 0f, RectF(0f, 0f, w, height.toFloat()), height - 40f * d)) r.shed = 1f
    }

    // ---------------- touch ----------------
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y
                grabbing = !game.over && !closeBtn.contains(e.x, e.y) && game.phase == SlingGame.Phase.AIM && e.y > topPad + 60f * d
                notch = 0
            }
            MotionEvent.ACTION_MOVE -> if (grabbing) {
                game.pull((e.x - downX) / w, (e.y - downY) / w)
                lastTension = game.tension
                val n = (game.tension * 8f).toInt()
                if (n > notch) buzz { Feel.play(context, TouchFeel.slingTick(game.tension)) }
                notch = n
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (grabbing) { grabbing = false; lastTension = game.tension; game.release() }
                else if (e.actionMasked == MotionEvent.ACTION_UP) {
                    when {
                        closeBtn.contains(e.x, e.y) && closeBtn.contains(downX, downY) -> SlingGameHub.finish(context)
                        game.over && againBtn.contains(e.x, e.y) -> restart()
                        game.over && doneBtn.contains(e.x, e.y) -> SlingGameHub.finish(context)
                    }
                }
            }
        }
        return true
    }

    /** v10.41: the pull started on the live character (hold and pull); its window keeps sending the finger here. */
    fun externalPull(dxPx: Float, dyPx: Float) {
        if (game.over || game.phase != SlingGame.Phase.AIM) return
        game.pull(dxPx / w, dyPx / w)
        lastTension = game.tension
        val n = (game.tension * 8f).toInt()
        if (n > notch) buzz { Feel.play(context, TouchFeel.slingTick(game.tension)) }
        notch = n
        invalidate()
    }
    fun externalRelease() { if (game.phase == SlingGame.Phase.AIM) { lastTension = game.tension; game.release() } }

    private fun restart() {
        game = SlingGameHub.newGame(context, game.bulletId) ?: return
        overAt = -1f; word = ""; puffs.clear(); newBest = false; trail.clear(); crack = null; banner = ""; freeze = 0f
        for (b in bodies.values) { b.scatter.clear(); (b.renderer as? ArmourRenderer)?.shed = 0f }
    }

    /** Test hook: pull by ([dx], [dy]) screen widths and let go. */
    fun debugShoot(dx: Float, dy: Float): Boolean { game.pull(dx, dy); lastTension = game.tension; return game.release() }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); on = true; lastNs = 0L; Choreographer.getInstance().postFrameCallback(this) }
    override fun onDetachedFromWindow() { on = false; Choreographer.getInstance().removeFrameCallback(this); super.onDetachedFromWindow() }

    // ---------------- drawing ----------------
    override fun onDraw(c: Canvas) {
        val g = game
        val h = height.toFloat()
        c.drawColor(Color.argb(170, 0x12, 0x14, 0x22))
        drawHud(c, g)
        c.save()
        if (clock < shakeUntil) c.translate(7f * d * sin(clock * 70f), 5f * d * sin(clock * 83f))
        crack?.draw(c, w, h, min(1f, (clock - crackT) / 0.3f) * 0.55f)
        g.target?.let { drawTarget(c, g, it) }
        drawSlingBack(c, g)
        drawTrail(c)
        drawStone(c, g)
        drawSlingFront(c, g)
        if (g.phase == SlingGame.Phase.SPLAT) {
            stars(c, px(g.bx), px(g.by - SlingGame.BODY_H * 1.05f))
            g.target?.takeIf { it.carried }?.let { stars(c, px(it.x), px(it.y - SlingGame.BODY_H * 1.05f)) }
        }
        c.restore()
        drawBanner(c)
        if (g.pulling) drawAim(c, g)
        for (b in bodies.values) if (b.scatter.active) b.scatter.draw(c, b.rig, 0f, 0f)
        fill.color = Color.WHITE
        for (p in puffs) { fill.alpha = (200 * (1f - p[4] / 0.7f)).toInt().coerceIn(0, 255); c.drawCircle(p[0], p[1], (5f + 9f * p[4]) * d, fill) }
        fill.alpha = 255
        drawWord(c)
        if (g.shots == 0 && !g.over && !g.pulling) hint(c, "Pull back anywhere and let go! 🎯", h * 0.93f)
        else if (g.shots in 1..2 && !g.over && g.target?.warning == true) hint(c, "! means it is about to escape", h * 0.93f)
        if (g.over) drawEndCard(c, g)
    }

    private fun pose(b: Body, state: MonkeyState, face: String?) {
        b.ch.fill(b.pose, state, clock, clock, b.ctx, CharacterRegistry.wear(b.ch))
        if (face != null) Expressions.apply(face, b.pose, clock, 1f)
        b.pose.shadow = 0f
    }

    private fun drawBody(c: Canvas, b: Body, x: Float, y: Float, facing: Float, alpha: Float = 1f) {
        b.rig.layout(b.pose, x, y, rigScale, facing)
        if (alpha >= 0.99f) b.renderer.draw(c, b.rig, b.pose, clock)
        else if (alpha > 0.02f) { val sv = c.saveLayerAlpha(null, (255 * alpha).toInt()); b.renderer.draw(c, b.rig, b.pose, clock); c.restoreToCount(sv) }
    }

    /** Squash flat against the wall that was hit (quick in, small wobble). */
    private fun splatTransform(c: Canvas, g: SlingGame, x: Float, y: Float) {
        val k = min(1f, g.splatT / 0.08f)
        val wob = 1f + 0.06f * sin(g.splatT * 30f) * (1f - min(1f, g.splatT / 0.6f))
        val thin = (1f - 0.62f * k) * wob; val wide = 1f + 0.28f * k
        val cy = y - px(SlingGame.BODY_H) / 2f
        when (g.wall) {
            SlingGame.Wall.LEFT -> c.scale(thin, wide, x - px(SlingGame.EDGE), cy)
            SlingGame.Wall.RIGHT -> c.scale(thin, wide, x + px(SlingGame.EDGE), cy)
            SlingGame.Wall.TOP -> c.scale(wide, thin, x, y - px(SlingGame.BODY_H))
            SlingGame.Wall.FLOOR -> c.scale(wide, thin, x, y)
        }
    }

    private fun drawStone(c: Canvas, g: SlingGame) {
        val b = body(g.bulletId) ?: return
        val x = px(g.bx); val y = px(g.by)
        c.save()
        when (g.phase) {
            SlingGame.Phase.AIM, SlingGame.Phase.OVER -> { pose(b, if (g.pulling) MonkeyState.DRAGGED else MonkeyState.IDLE, if (g.pulling) "fear" else if (g.over) "happy" else "determined"); drawBody(c, b, x, y, 1f) }
            SlingGame.Phase.FLY -> {
                pose(b, MonkeyState.FALLING, "scream")
                c.rotate(Math.toDegrees(atan2(g.bvy, g.bvx).toDouble()).toFloat() + 90f, x, y - px(SlingGame.BODY_H) / 2f)
                drawBody(c, b, x, y, 1f)
            }
            SlingGame.Phase.SPLAT -> { pose(b, MonkeyState.STARTLED, "shocked"); splatTransform(c, g, x, y); drawBody(c, b, x, y, 1f) }
            SlingGame.Phase.RETURN -> { pose(b, MonkeyState.IDLE, "happy"); drawBody(c, b, x, y, (g.returnT / SlingGame.RETURN_SEC).coerceIn(0f, 1f)) }
        }
        c.restore()
    }

    /** Dizzy stars circling a head after a splat. */
    private fun stars(c: Canvas, x: Float, y: Float) {
        text.textSize = 15f * d; text.color = Color.rgb(0xFF, 0xD5, 0x4A)
        for (i in 0 until 3) { val a = clock * 5f + i * 2.094f; c.drawText("★", x + cos(a) * 26f * d, y + sin(a) * 8f * d, text) }
    }

    /** Speed streak behind the flying stone. */
    private fun drawTrail(c: Canvas) {
        fill.color = Color.WHITE
        for (q in trail) { val k = 1f - q[2] / 0.25f; fill.alpha = (110 * k).toInt().coerceIn(0, 255); c.drawCircle(q[0], q[1], (16f * k + 3f) * d, fill) }
        fill.alpha = 255
    }

    private fun drawBanner(c: Canvas) {
        val age = clock - bannerT
        if (banner.isEmpty() || age > 1.1f) return
        text.textSize = 26f * d * (1f + 0.4f * (1f - min(1f, age / 0.15f)))
        text.color = Color.WHITE; text.alpha = (255 * (1f - (age / 1.1f) * (age / 1.1f))).toInt()
        c.drawText(banner, w / 2f, topPad + 100f * d, text)
        text.alpha = 255
    }

    private fun drawTarget(c: Canvas, g: SlingGame, tg: SlingGame.Target) {
        val b = body(tg.id) ?: return
        val bob = if (tg.flies && !tg.carried) sin(clock * 3f) * 0.012f else 0f
        val x = px(tg.x); val y = px(tg.y + tg.yOff + bob)
        if (tg.alpha < 0.98f) com.combo.monkeycompanion.engine.stagefx.StageFx.smoke(c, x, y - px(SlingGame.BODY_H) / 2f, px(0.10f), b.ch.vanishStyle, 1f - tg.alpha, clock)
        if (tg.warning) {   // the warning before an escape
            text.textSize = 24f * d; text.color = Color.rgb(0xFF, 0x5A, 0x1F)
            c.drawText("!", x, y - px(SlingGame.BODY_H) - 8f * d, text)
        }
        if (!tg.flies && !tg.carried && tg.yOff > -0.01f) {   // walkers ride a little cloud so they can cross the sky
            fill.color = Color.argb(230, 0xF4, 0xF6, 0xFF)
            val r = px(0.045f)
            c.drawCircle(x - r * 1.3f, y + r * 0.5f, r * 0.8f, fill); c.drawCircle(x, y + r * 0.35f, r, fill); c.drawCircle(x + r * 1.3f, y + r * 0.5f, r * 0.8f, fill)
        }
        c.save()
        when {
            tg.carried && g.phase == SlingGame.Phase.SPLAT -> { pose(b, MonkeyState.STARTLED, "shocked"); splatTransform(c, g, x, y) }
            tg.carried -> { pose(b, MonkeyState.FALLING, "scream"); c.rotate(clock * 540f % 360f, x, y - px(SlingGame.BODY_H) / 2f) }
            g.t < tg.laughUntil -> pose(b, MonkeyState.GIGGLE, "happy")
            tg.warning -> pose(b, MonkeyState.STARTLED, "fear")
            else -> pose(b, if (tg.flies) MonkeyState.IDLE else MonkeyState.WALK, "smug")
        }
        drawBody(c, b, x, y, tg.facing, tg.alpha)
        c.restore()
    }

    // The slingshot: a wooden fork at home, one band behind the stone and one in front.
    private fun forkTips(g: SlingGame): FloatArray {
        val hx = px(g.homeX); val hy = px(g.homeY - SlingGame.BODY_H * 0.45f); val s = px(0.11f)
        return floatArrayOf(hx - s, hy, hx + s, hy)
    }
    private fun drawSlingBack(c: Canvas, g: SlingGame) {
        val t = forkTips(g); val hx = px(g.homeX); val base = px(g.homeY + 0.12f)
        line.color = Color.rgb(0x6B, 0x44, 0x23); line.strokeWidth = 9f * d
        c.drawLine(hx, base, hx, px(g.homeY - 0.02f), line)
        c.drawLine(hx, px(g.homeY - 0.02f), t[0], t[1], line); c.drawLine(hx, px(g.homeY - 0.02f), t[2], t[3], line)
        if (g.phase == SlingGame.Phase.AIM) band(c, t[2], t[3], px(g.bx), px(g.by - SlingGame.BODY_H * 0.45f), g.tension)
    }
    private fun drawSlingFront(c: Canvas, g: SlingGame) {
        val t = forkTips(g)
        if (g.phase == SlingGame.Phase.AIM) band(c, t[0], t[1], px(g.bx), px(g.by - SlingGame.BODY_H * 0.45f), g.tension)
        else { line.color = Color.rgb(0x2A, 0x1E, 0x1A); line.strokeWidth = 5f * d; c.drawLine(t[0], t[1], t[2], t[3], line) }
    }
    private fun band(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, tension: Float) {
        line.color = Color.rgb((0x2A + 150 * tension).toInt().coerceAtMost(255), 0x1E, 0x1A)
        line.strokeWidth = (6f - 2.5f * tension) * d
        c.drawLine(x0, y0, x1, y1, line)
    }

    private fun drawAim(c: Canvas, g: SlingGame) {
        fill.color = Color.WHITE
        for (i in 1..6) {
            val p = g.aimPoint(i * 0.035f) ?: return
            fill.alpha = 230 - i * 28
            c.drawCircle(px(p.first), px(p.second - SlingGame.BODY_H * 0.45f), (5.5f - i * 0.5f) * d, fill)
        }
        fill.alpha = 255
    }

    private fun drawHud(c: Canvas, g: SlingGame) {
        val hh = 44f * d; val y0 = topPad
        rect.set(w * 0.08f, y0, w * 0.80f, y0 + hh)
        fill.color = Color.argb(235, 0xFF, 0xFF, 0xFF); c.drawRoundRect(rect, hh / 2, hh / 2, fill)
        text.textSize = 18f * d
        val secs = kotlin.math.ceil(g.timeLeft.toDouble()).toInt()
        text.color = if (secs <= 5 && !g.over) Color.rgb(0xD3, 0x2F, 0x2F) else Color.rgb(0x22, 0x22, 0x2A)
        val ty = y0 + hh / 2 + text.textSize * 0.36f
        c.drawText("⏱ $secs", rect.left + rect.width() * 0.18f, ty, text)
        text.color = Color.rgb(0x22, 0x22, 0x2A)
        c.drawText("🎯 ${g.score}", rect.centerX(), ty, text)
        c.drawText("🔥 ${g.streak}", rect.left + rect.width() * 0.82f, ty, text)
        closeBtn.set(w * 0.84f, y0, w * 0.84f + hh, y0 + hh)
        fill.color = Color.argb(235, 0xFF, 0xFF, 0xFF); c.drawOval(closeBtn, fill)
        text.color = Color.rgb(0x44, 0x44, 0x55); c.drawText("✕", closeBtn.centerX(), ty, text)
    }

    private fun drawWord(c: Canvas) {
        val age = clock - wordT
        if (word.isEmpty() || age > 0.9f) return
        val pop = 1f + 0.5f * (1f - min(1f, age / 0.15f))
        text.textSize = 30f * d * pop
        text.color = Color.rgb(0x22, 0x18, 0x10); text.alpha = (255 * (1f - (age / 0.9f) * (age / 0.9f))).toInt()
        c.drawText(word, wordX + 2f * d, wordY - age * 40f * d + 2f * d, text)
        text.color = Color.rgb(0xFF, 0xD5, 0x4A); text.alpha = (255 * (1f - (age / 0.9f) * (age / 0.9f))).toInt()
        c.drawText(word, wordX, wordY - age * 40f * d, text)
        text.alpha = 255
    }

    private fun hint(c: Canvas, s: String, y: Float) {
        text.textSize = 15f * d; text.color = Color.WHITE
        c.drawText(s, w / 2f, y, text)
    }

    private fun drawEndCard(c: Canvas, g: SlingGame) {
        val cw = w * 0.78f; val ch = 250f * d
        val x0 = (w - cw) / 2f; val y0 = height * 0.30f
        rect.set(x0, y0, x0 + cw, y0 + ch)
        fill.color = Color.WHITE; c.drawRoundRect(rect, 26f * d, 26f * d, fill)
        text.color = Color.rgb(0x22, 0x22, 0x2A); text.textSize = 24f * d
        c.drawText(if (g.score == 0) "Time's up!" else "${g.score} ${if (g.score == 1) "hit" else "hits"}! 🎯", w / 2f, y0 + 50f * d, text)
        text.textSize = 15f * d; text.color = Color.rgb(0x55, 0x55, 0x66)
        c.drawText("Best streak: ${g.bestStreak} 🔥", w / 2f, y0 + 84f * d, text)
        text.color = if (newBest && g.score > 0) Color.rgb(0xE6, 0x7E, 0x00) else Color.rgb(0x55, 0x55, 0x66)
        c.drawText(if (newBest && g.score > 0) "🏆 New best today!" else "Today's best: $best", w / 2f, y0 + 110f * d, text)
        val bw = cw * 0.40f; val bh = 50f * d; val by = y0 + ch - bh - 26f * d
        againBtn.set(x0 + cw * 0.07f, by, x0 + cw * 0.07f + bw, by + bh)
        doneBtn.set(x0 + cw * 0.93f - bw, by, x0 + cw * 0.93f, by + bh)
        fill.color = Color.rgb(0x5B, 0x4B, 0xD6); c.drawRoundRect(againBtn, bh / 2, bh / 2, fill)
        fill.color = Color.rgb(0xE8, 0xE8, 0xF0); c.drawRoundRect(doneBtn, bh / 2, bh / 2, fill)
        text.textSize = 16f * d
        text.color = Color.WHITE; c.drawText("▶ Play again", againBtn.centerX(), againBtn.centerY() + 6f * d, text)
        text.color = Color.rgb(0x33, 0x33, 0x44); c.drawText("Done", doneBtn.centerX(), doneBtn.centerY() + 6f * d, text)
    }

    companion object {
        /** The end card closes by itself after this long. */
        const val END_CARD_SEC = 25f
        private var cur: SlingGameView? = null
        val showing get() = cur != null
        val current: SlingGameView? get() = cur

        fun open(ctx: Context, game: SlingGame): Boolean {
            if (cur != null) return true
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
            val v = SlingGameView(ctx, game)
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                // touchable on purpose (the game needs the finger; a touch-through overlay is also forced to 80 % opacity)
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.START; title = "SettaiSlingGame" }
            if (runCatching { wm.addView(v, lp) }.isFailure) return false
            cur = v; return true
        }

        fun close(ctx: Context?) {
            val v = cur ?: return
            cur = null
            runCatching { ((ctx ?: v.context).getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.removeView(v) }
        }
    }
}
