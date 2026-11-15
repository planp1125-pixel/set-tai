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
    // v10.44 levels: the arrows that pick a level (before the first shot, and on the end card)
    private val prevBtn = RectF(); private val nextBtn = RectF()
    private var selLevel = game0.stage
    private var result: SlingGameHub.RoundResult? = null
    private var gainText = ""; private var gainT = -9f
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
        // real elapsed time, not the frame counter: game time can never run ahead of the clock
        val now = SystemClock.uptimeMillis() * 1_000_000L
        val dt = if (lastNs == 0L) 0f else ((now - lastNs) / 1e9f).coerceIn(0f, 0.05f)
        lastNs = now
        advance(dt)
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    /** One step of game time (tests call this directly). */
    fun advance(dt: Float) {
        clock += dt
        game.step(dt)
        for (e in game.drain()) onEvent(e)
        if (game.phase == SlingGame.Phase.FLY && !game.stoneDown) trail.add(floatArrayOf(px(game.bx), px(game.by - SlingGame.BODY_H / 2f), 0f))
        val tr = trail.iterator()
        while (tr.hasNext()) { val q = tr.next(); q[2] += dt; if (q[2] > 0.25f) tr.remove() }
        if (crack != null && clock - crackT > 1.6f) crack = null
        val it = puffs.iterator()
        while (it.hasNext()) { val p = it.next(); p[4] += dt; p[0] += p[2] * dt; p[1] += p[3] * dt; p[3] += 900f * d * dt; if (p[4] > 0.7f) it.remove() }
        for (b in bodies.values) if (b.scatter.active) {
            b.scatter.step(dt, game.phase == SlingGame.Phase.SPLAT || game.phase == SlingGame.Phase.FLY)
            (b.renderer as? ArmourRenderer)?.shed = if (b.scatter.active) 1f else 0f
        }
        if (game.over && overAt >= 0f && clock - overAt > END_CARD_SEC) SlingGameHub.finish(context, "end card timed out")
    }

    private fun onEvent(e: SlingGame.Event) {
        when (e) {
            SlingGame.Event.SNAP -> { sfx(Sfx.S.WHOOSH, 0.5f); buzz { Feel.play(context, TouchFeel.slingSnap(lastTension)) } }
            SlingGame.Event.HIT -> {   // v10.42 the knock itself: no freeze, a sharp click you feel, sparks where they meet
                sfx(Sfx.S.BONK, 0.5f + 0.4f * game.hitForce)
                buzz { Feel.playSharpHit(context, game.hitForce, TouchFeel.weight(game.lastKnocked?.id ?: game.bulletId)) }
                say(if (game.banked) "BANK SHOT!" else "BONK!", game.hitX, game.hitY - SlingGame.BODY_H * 0.5f)
                sparks(px(game.hitX), px(game.hitY), 10 + (10 * game.hitForce).toInt())
                shakeUntil = clock + 0.12f
                gainText = "+${game.lastGain.toInt()}"; gainT = clock   // shown beside the timer, so a banner can never hide it
            }
            SlingGame.Event.COMBO -> { banner = "COMBO x${game.shotHits}!"; bannerT = clock; sfx(Sfx.S.TADA, 0.4f) }
            SlingGame.Event.BOUNCE_STONE, SlingGame.Event.BOUNCE_TARGET -> {   // v10.43 off the edge like a carrom coin
                sfx(Sfx.S.BOING, 0.45f)
                buzz { Feel.play(context, TouchFeel.slingTick(1f)) }
                burstAt(px(game.bounceX), px(game.bounceY), 5)
            }
            SlingGame.Event.WALL_STONE -> slam(game.bx, game.by, game.bulletId, game.slamForce)
            SlingGame.Event.WALL_TARGET -> game.lastSlammed?.let { slam(it.x, it.y, it.id, game.slamForce) }
            SlingGame.Event.DOUBLE_SPLAT -> {
                val tg = game.lastSlammed
                val x = tg?.x ?: game.bx; val y = tg?.y ?: game.by
                sfx(Sfx.S.CRACK, 0.35f)
                say(when { game.streak >= 3 -> "ON FIRE! 🔥"; game.streak == 2 -> "2 in a row!"; else -> "DOUBLE SPLAT!" }, x, y - SlingGame.BODY_H * 1.1f)
                shakeUntil = clock + 0.35f
                crack = com.combo.monkeycompanion.engine.stagefx.StageFx.Crack(x, (y - SlingGame.BODY_H / 2f) / game.aspect, 7L + game.score); crackT = clock
            }
            SlingGame.Event.SPLAT -> {
                sfx(Sfx.S.SPLAT, 0.6f); buzz { Feel.playSharpHit(context, 0.6f, 0.5f) }
                postDelayed({ if (isAttachedToWindow) buzz { Feel.play(context, TouchFeel.thump(game.slamForce, TouchFeel.weight(game.bulletId))) } }, 45L)
                say("MISS!", game.bx, game.by - SlingGame.BODY_H * 1.1f); burst(8); shed(game.bulletId)
            }
            SlingGame.Event.POWER_FIRE -> {   // v10.45 the character throws its own power at the first bounce
                sfx(Sfx.S.ZIP, 0.5f); buzz { Feel.playPop(context) }
                burstAt(px(game.bx), px(game.by - SlingGame.BODY_H / 2f), 6)
            }
            SlingGame.Event.POWER_HIT -> {
                sfx(Sfx.S.BONK, 0.7f); sfx(Sfx.S.POP, 0.4f)
                buzz { Feel.playSharpHit(context, 0.7f, TouchFeel.weight(game.lastKnocked?.id ?: game.bulletId)) }
                say("ZAP!", game.hitX, game.hitY - SlingGame.BODY_H * 0.5f)
                sparks(px(game.hitX), px(game.hitY), 14)
                shakeUntil = clock + 0.12f
                gainText = "+${game.lastGain.toInt()}"; gainT = clock
            }
            SlingGame.Event.POWER_BOUNCE -> { sfx(Sfx.S.BOING, 0.2f); burstAt(px(game.bounceX), px(game.bounceY), 4) }
            SlingGame.Event.MISS -> say("MISS!", game.bx, game.by - SlingGame.BODY_H * 1.1f)
            SlingGame.Event.TARGET_IN -> sfx(Sfx.S.POP, 0.4f)
            SlingGame.Event.TARGET_OUT -> Unit
            SlingGame.Event.FASTER -> { banner = "Faster! 💨"; bannerT = clock }
            SlingGame.Event.TRICK -> sfx(Sfx.S.ZIP, 0.35f)
            SlingGame.Event.LAST_SECONDS -> sfx(Sfx.S.PLUCK, 0.5f)
            SlingGame.Event.OVER -> {
                overAt = clock
                val r = SlingGameHub.record(context, game.score)
                best = r.first; newBest = r.second
                val rr = SlingGameHub.finishRound(context, game.stage, game.score); result = rr
                rr.unlockedNow?.let { selLevel = SlingGameHub.setLevel(context, it) }   // the new level is offered first
                sfx(if (rr.stars > 0 || (newBest && game.score > 0)) Sfx.S.TADA else Sfx.S.WAHWAH, 0.6f)
            }
        }
    }

    /** v10.42 one body slams into an edge: sound, a sharp knock then a short rumble (heavier = stronger), dust, armour. */
    private fun slam(x: Float, y: Float, id: String, force: Float) {
        val wt = TouchFeel.weight(id)
        sfx(Sfx.S.SPLAT, 0.55f + 0.4f * force)
        buzz { Feel.playSharpHit(context, force, wt) }
        postDelayed({ if (isAttachedToWindow) buzz { Feel.play(context, TouchFeel.thump(force, wt)) } }, 45L)
        burstAt(px(x), px(y - SlingGame.BODY_H / 2f), 8 + (10 * force).toInt())
        shakeUntil = maxOf(shakeUntil, clock + 0.18f)
        shed(id)
    }
    /** Little stars flying out from where the two met. */
    private fun sparks(x: Float, y: Float, n: Int) {
        for (i in 0 until n) { val a = i * 6.283f / n + 0.3f; val sp = (320f + 90f * (i % 3)) * d; puffs.add(floatArrayOf(x, y, cos(a) * sp, sin(a) * sp - 60f * d, 0.25f)) }
    }
    private fun burstAt(x: Float, y: Float, n: Int) {
        for (i in 0 until n) { val a = i * 6.283f / n; val sp = (220f + 60f * (i % 3)) * d; puffs.add(floatArrayOf(x, y, cos(a) * sp, sin(a) * sp - 120f * d, 0f)) }
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
                grabbing = !game.over && !closeBtn.contains(e.x, e.y) && !(levelRowOn() && (prevBtn.contains(e.x, e.y) || nextBtn.contains(e.x, e.y))) &&
                    game.phase == SlingGame.Phase.AIM && e.y > topPad + 60f * d
                notch = 0; lastPower = null
            }
            MotionEvent.ACTION_MOVE -> if (grabbing) {
                game.pull((e.x - downX) / w, (e.y - downY) / w)
                lastTension = game.tension
                val n = (game.tension * 8f).toInt()
                if (n > notch) buzz { Feel.play(context, TouchFeel.slingTick(game.tension)) }
                notch = n
                powerClick()
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (grabbing) { grabbing = false; lastTension = game.tension; game.release() }
                else if (e.actionMasked == MotionEvent.ACTION_UP) {
                    when {
                        closeBtn.contains(e.x, e.y) && closeBtn.contains(downX, downY) -> SlingGameHub.finish(context, "close button")
                        levelRowOn() && prevBtn.contains(e.x, e.y) && prevBtn.contains(downX, downY) -> changeLevel(-1)
                        levelRowOn() && nextBtn.contains(e.x, e.y) && nextBtn.contains(downX, downY) -> changeLevel(1)
                        game.over && againBtn.contains(e.x, e.y) -> restart()
                        game.over && doneBtn.contains(e.x, e.y) -> SlingGameHub.finish(context, "done button")
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
        powerClick()
        invalidate()
    }
    private var lastPower: SlingGame.Power? = null
    /** A clear click when the pull crosses from soft to medium to hard (or back), so the hand knows too. */
    private fun powerClick() {
        val p = game.power
        if (lastPower != null && p != lastPower) buzz { Feel.playPop(context) }
        lastPower = p
    }
    fun externalRelease() { lastPower = null; if (game.phase == SlingGame.Phase.AIM) { lastTension = game.tension; game.release() } }

    /** The level arrows show before the first shot of a round and on the end card, once a second level is open. */
    private fun levelRowOn(): Boolean = (game.over || (game.shots == 0 && game.phase == SlingGame.Phase.AIM && !game.pulling)) && SlingGameHub.unlocked(context) > 1
    private fun changeLevel(by: Int) {
        val n = SlingGameHub.setLevel(context, selLevel + by)
        if (n == selLevel) return
        selLevel = n
        sfx(Sfx.S.POP, 0.3f)
        if (!game.over) restart() else invalidate()   // before the first shot: switch at once; on the end card: Play uses it
    }
    val debugLevel: Int get() = selLevel
    fun debugChangeLevel(by: Int) = changeLevel(by)

    private fun restart() {
        game = SlingGameHub.newGame(context, game.bulletId, selLevel) ?: return
        result = null; gainText = ""
        overAt = -1f; word = ""; puffs.clear(); newBest = false; trail.clear(); crack = null; banner = ""; freeze = 0f
        for (b in bodies.values) { b.scatter.clear(); (b.renderer as? ArmourRenderer)?.shed = 0f }
    }

    /** Test hook: pull by ([dx], [dy]) screen widths and let go. */
    fun debugShoot(dx: Float, dy: Float): Boolean { game.pull(dx, dy); lastTension = game.tension; return game.release() }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); on = !debugNoLoop; lastNs = 0L; if (on) Choreographer.getInstance().postFrameCallback(this) }
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
        for (k in g.targets.indices) drawTarget(c, g, g.targets[k])
        drawSlingBack(c, g)
        drawTrail(c)
        drawStone(c, g)
        drawSlingFront(c, g)
        for (k in g.powerShots.indices) { val sh = g.powerShots[k]; if (sh.alive) drawPowerShot(c, g, sh) }
        if (g.stoneDown && (g.phase == SlingGame.Phase.SPLAT || g.phase == SlingGame.Phase.FLY)) stars(c, px(g.bx), px(g.by - SlingGame.BODY_H * 1.05f))
        for (k in g.targets.indices) { val it = g.targets[k]; if (it.carried && it.down) stars(c, px(it.x), px(it.y - SlingGame.BODY_H * 1.05f)) }
        c.restore()
        drawBanner(c)
        if (g.pulling) { drawAim(c, g); drawPower(c, g) }
        for (b in bodies.values) if (b.scatter.active) b.scatter.draw(c, b.rig, 0f, 0f)
        fill.color = Color.WHITE
        for (p in puffs) { fill.alpha = (200 * (1f - p[4] / 0.7f)).toInt().coerceIn(0, 255); c.drawCircle(p[0], p[1], (5f + 9f * p[4]) * d, fill) }
        fill.alpha = 255
        drawWord(c)
        if (g.shots == 0 && !g.over && !g.pulling) hint(c, "Pull back anywhere and let go! 🎯", h * 0.93f)
        else if (g.shots < 3 && !g.over && g.pulling) hint(c, "Every shot bounces off the edges · a harder pull bounces more", h * 0.93f)
        else if (g.shots in 1..4 && !g.over && g.targets.any { it.warning }) hint(c, "! means it is about to escape", h * 0.93f)
        else if (g.powersOn && g.shots < 4 && !g.over && !g.pulling) hint(c, "Your power fires at the first bounce ⚡", h * 0.93f)
        if (levelRowOn() && !g.over) drawLevelRow(c, topPad + 62f * d)
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
    private fun splatTransform(c: Canvas, wall: SlingGame.Wall, age: Float, x: Float, y: Float) {
        // slam flat in two frames, spring back past normal, then settle (a damped bounce)
        val hit = min(1f, age / 0.05f)
        val spring = kotlin.math.exp(-age * 7f) * cos(age * 26f)
        val flat = 0.62f * hit * (0.55f + 0.45f * spring.coerceIn(-1f, 1f))
        val thin = 1f - flat; val wide = 1f + 0.45f * flat
        val cy = y - px(SlingGame.BODY_H) / 2f
        when (wall) {
            SlingGame.Wall.LEFT -> c.scale(thin, wide, x - px(SlingGame.EDGE), cy)
            SlingGame.Wall.RIGHT -> c.scale(thin, wide, x + px(SlingGame.EDGE), cy)
            SlingGame.Wall.TOP -> c.scale(wide, thin, x, y - px(SlingGame.BODY_H))
            SlingGame.Wall.FLOOR -> c.scale(wide, thin, x, y)
        }
    }

    /** In the air: turned to [deg] and stretched a little along the way it is going (faster = longer). */
    private fun flightTransform(c: Canvas, deg: Float, speed: Float, x: Float, y: Float) {
        val cy = y - px(SlingGame.BODY_H) / 2f
        c.rotate(deg, x, cy)
        val k = (speed / SlingGame.SPEED_MAX).coerceIn(0f, 1f)
        c.scale(1f - 0.10f * k, 1f + 0.16f * k, x, cy)
    }

    private fun drawStone(c: Canvas, g: SlingGame) {
        val b = body(g.bulletId) ?: return
        val x = px(g.bx); val y = px(g.by)
        c.save()
        when (g.phase) {
            SlingGame.Phase.AIM, SlingGame.Phase.OVER -> { pose(b, if (g.pulling) MonkeyState.DRAGGED else MonkeyState.IDLE, if (g.pulling) "fear" else if (g.over) "happy" else "determined"); drawBody(c, b, x, y, 1f) }
            SlingGame.Phase.FLY -> if (g.stoneDown) { pose(b, MonkeyState.STARTLED, "shocked"); splatTransform(c, g.wall, g.stoneSplatT, x, y); drawBody(c, b, x, y, 1f) } else {
                pose(b, MonkeyState.FALLING, "scream")
                flightTransform(c, g.stoneSpin, hypot(g.bvx, g.bvy), x, y)
                drawBody(c, b, x, y, 1f)
            }
            SlingGame.Phase.SPLAT -> { pose(b, MonkeyState.STARTLED, "shocked"); splatTransform(c, g.wall, g.stoneSplatT, x, y); drawBody(c, b, x, y, 1f) }
            SlingGame.Phase.RETURN -> { pose(b, MonkeyState.IDLE, "happy"); drawBody(c, b, x, y, (g.returnT / SlingGame.RETURN_SEC).coerceIn(0f, 1f)) }
        }
        c.restore()
    }

    /** Dizzy stars circling a head after a splat. */
    private fun stars(c: Canvas, x: Float, y: Float) {
        text.textSize = 15f * d; text.color = Color.rgb(0xFF, 0xD5, 0x4A)
        for (i in 0 until 3) { val a = clock * 5f + i * 2.094f; c.drawText("★", x + cos(a) * 26f * d, y + sin(a) * 8f * d, text) }
    }

    // v10.45 each character flies in its own style (from level SlingGame.STYLE_STAGE): what streams out behind it
    private enum class Fly { PLAIN, FLAME, CAPE, SMOKE, SPARK, WAVE, GLOW, LINES, DUST }
    private fun flyStyle(g: SlingGame): Fly = if (g.stage < SlingGame.STYLE_STAGE) Fly.PLAIN else when (g.bulletId) {
        "armour", "rocketgirl" -> Fly.FLAME          // thrusters / rocket
        "superboy" -> Fly.CAPE                        // the cape streams behind
        "devil", "mask", "hollowman" -> Fly.SMOKE     // a wisp of smoke
        "skater" -> Fly.SPARK                         // sparks off the wheels
        "surfer" -> Fly.WAVE                          // rides a wave
        "angel" -> Fly.GLOW                           // a soft golden glow
        "gajju" -> Fly.DUST                           // a heavy dust cloud
        else -> Fly.LINES                             // speed lines
    }

    /** What streams out behind the flying stone. */
    private fun drawTrail(c: Canvas) {
        if (trail.isEmpty()) return
        val life = 0.25f
        when (flyStyle(game)) {
            Fly.PLAIN -> { fill.color = Color.WHITE
                for (q in trail) { val k = 1f - q[2] / life; fill.alpha = (110 * k).toInt().coerceIn(0, 255); c.drawCircle(q[0], q[1], (16f * k + 3f) * d, fill) } }
            Fly.FLAME -> for (q in trail) { val k = 1f - q[2] / life
                fill.color = Color.rgb(0xFF, 0x7A, 0x1A); fill.alpha = (200 * k).toInt().coerceIn(0, 255); c.drawCircle(q[0], q[1], (20f * k + 4f) * d, fill)
                fill.color = Color.rgb(0xFF, 0xE0, 0x66); fill.alpha = (230 * k).toInt().coerceIn(0, 255); c.drawCircle(q[0], q[1], (10f * k + 1f) * d, fill) }
            Fly.CAPE -> { line.color = Color.rgb(0xD3, 0x2F, 0x2F)
                for (n in 1 until trail.size) { val a = trail[n - 1]; val b = trail[n]; val k = 1f - a[2] / life
                    line.strokeWidth = (26f * k + 4f) * d; line.alpha = (220 * k).toInt().coerceIn(0, 255); c.drawLine(a[0], a[1], b[0], b[1], line) }
                line.alpha = 255 }
            Fly.SMOKE -> { fill.color = if (game.bulletId == "devil") Color.rgb(0x8E, 0x44, 0xAD) else if (game.bulletId == "mask") Color.rgb(0x44, 0x48, 0x55) else Color.rgb(0xD8, 0xE4, 0xEE)
                for (q in trail) { val k = 1f - q[2] / life; fill.alpha = (150 * k).toInt().coerceIn(0, 255); c.drawCircle(q[0] + sin(q[2] * 40f) * 6f * d, q[1], (10f + 22f * (1f - k)) * d, fill) } }
            Fly.SPARK -> { fill.color = Color.rgb(0xFF, 0xE0, 0x66)
                for ((n, q) in trail.withIndex()) { val k = 1f - q[2] / life; fill.alpha = (255 * k).toInt().coerceIn(0, 255)
                    c.drawCircle(q[0] + (if (n % 2 == 0) 14f else -14f) * d * (1f - k), q[1] + 16f * d * (1f - k), (4.5f * k + 1f) * d, fill) } }
            Fly.WAVE -> { line.color = Color.rgb(0x3F, 0xA9, 0xF5)
                for (q in trail) { val k = 1f - q[2] / life; line.strokeWidth = (7f * k + 1f) * d; line.alpha = (210 * k).toInt().coerceIn(0, 255)
                    rect.set(q[0] - 24f * d, q[1] - 6f * d, q[0] + 24f * d, q[1] + 26f * d); c.drawArc(rect, 20f, 140f, false, line) }
                line.alpha = 255 }
            Fly.GLOW -> { fill.color = Color.rgb(0xFF, 0xE9, 0xA8)
                for (q in trail) { val k = 1f - q[2] / life; fill.alpha = (90 * k).toInt().coerceIn(0, 255); c.drawCircle(q[0], q[1], (30f * k + 8f) * d, fill) } }
            Fly.DUST -> { fill.color = Color.rgb(0xD9, 0xC7, 0xA8)
                for (q in trail) { val k = 1f - q[2] / life; fill.alpha = (170 * k).toInt().coerceIn(0, 255); c.drawCircle(q[0], q[1] + 10f * d, (14f + 26f * (1f - k)) * d, fill) } }
            Fly.LINES -> { line.color = Color.WHITE
                for (n in 1 until trail.size) { val a = trail[n - 1]; val b = trail[n]; val k = 1f - a[2] / life
                    line.strokeWidth = 3f * d; line.alpha = (200 * k).toInt().coerceIn(0, 255)
                    for (o in -1..1) c.drawLine(a[0] + o * 14f * d, a[1], b[0] + o * 14f * d, b[1], line) }
                line.alpha = 255 }
        }
        fill.alpha = 255
    }

    /** v10.45 the thrown power, in the character's own look (all drawn here, no pictures). */
    private fun drawPowerShot(c: Canvas, g: SlingGame, sh: SlingGame.Shot) {
        val x = px(sh.x); val y = px(sh.y)
        val sp = hypot(sh.vx, sh.vy).coerceAtLeast(1e-4f); val ux = sh.vx / sp; val uy = sh.vy / sp
        val r = px(SlingGame.SHOT_R)
        val (main, core) = when (g.bulletId) {
            "armour" -> Color.rgb(0xFF, 0x7A, 0x1A) to Color.rgb(0xFF, 0xF0, 0xA0)        // fire blast from the hand
            "superboy" -> Color.rgb(0xE5, 0x1C, 0x23) to Color.rgb(0xFF, 0xC4, 0xC4)      // red beam from the eyes
            "devil" -> Color.rgb(0xE5, 0x39, 0x1E) to Color.rgb(0xFF, 0xB3, 0x3D)         // fireball
            "gajju", "surfer" -> Color.rgb(0x3F, 0xA9, 0xF5) to Color.rgb(0xD6, 0xF0, 0xFF)   // water splash
            "angel" -> Color.rgb(0xFF, 0xD8, 0x6B) to Color.WHITE                         // ring of light
            "mask" -> Color.rgb(0x9A, 0xA3, 0xB5) to Color.WHITE                          // throwing star
            "monkey", "chittu" -> Color.rgb(0xFF, 0xD9, 0x3D) to Color.rgb(0xFF, 0xF4, 0xB0)  // banana / cheese
            "rocketgirl" -> Color.rgb(0xFF, 0xC1, 0x07) to Color.WHITE                    // rocket spark
            "hollowman" -> Color.rgb(0xCF, 0xE3, 0xEE) to Color.WHITE                     // ghost wisp
            "billa" -> Color.rgb(0xFF, 0x8F, 0xB1) to Color.rgb(0xFF, 0xDD, 0xE8)         // yarn ball
            "skater" -> Color.rgb(0x55, 0x5B, 0x6E) to Color.rgb(0xFF, 0xD5, 0x4A)        // a wheel
            else -> Color.WHITE to Color.rgb(0xFF, 0xF4, 0xB0)                            // a star
        }
        // tail
        fill.color = main
        for (n in 1..4) { fill.alpha = 150 - n * 30; c.drawCircle(x - ux * r * 0.9f * n, y - uy * r * 0.9f * n, r * (1f - n * 0.17f), fill) }
        fill.alpha = 255
        when (g.bulletId) {
            "superboy" -> {   // a beam: a thick red line with a bright core
                line.color = main; line.strokeWidth = r * 1.1f; c.drawLine(x - ux * r * 3.2f, y - uy * r * 3.2f, x + ux * r, y + uy * r, line)
                line.color = core; line.strokeWidth = r * 0.4f; c.drawLine(x - ux * r * 3.2f, y - uy * r * 3.2f, x + ux * r, y + uy * r, line)
            }
            "angel", "skater" -> {   // a ring
                line.color = main; line.strokeWidth = r * 0.45f; c.drawCircle(x, y, r, line)
                fill.color = core; c.drawCircle(x, y, r * 0.3f, fill)
            }
            "mask", "rocketgirl" -> {   // a spinning four-point star
                line.color = main; line.strokeWidth = r * 0.5f
                val a = clock * 14f
                c.drawLine(x - cos(a) * r * 1.4f, y - sin(a) * r * 1.4f, x + cos(a) * r * 1.4f, y + sin(a) * r * 1.4f, line)
                c.drawLine(x + sin(a) * r * 1.4f, y - cos(a) * r * 1.4f, x - sin(a) * r * 1.4f, y + cos(a) * r * 1.4f, line)
                fill.color = core; c.drawCircle(x, y, r * 0.35f, fill)
            }
            "monkey" -> {   // a banana: a thick curved stroke
                line.color = main; line.strokeWidth = r * 0.7f
                rect.set(x - r * 1.3f, y - r * 1.3f, x + r * 1.3f, y + r * 1.3f); c.drawArc(rect, clock * 400f % 360f, 150f, false, line)
            }
            else -> {   // a glowing ball (fire blast, fireball, water, wisp, yarn, cheese)
                fill.color = main; c.drawCircle(x, y, r * (if (g.bulletId == "devil") 1.25f else 1f), fill)
                fill.color = core; c.drawCircle(x + ux * r * 0.2f, y + uy * r * 0.2f, r * 0.5f, fill)
            }
        }
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
        val outer = c.save()
        if (g.targetScale != 1f) c.scale(g.targetScale, g.targetScale, x, y)
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
            tg.carried && tg.down -> { pose(b, MonkeyState.STARTLED, "shocked"); splatTransform(c, tg.wall, tg.splatT, x, y) }
            tg.carried -> { pose(b, MonkeyState.FALLING, "scream"); flightTransform(c, tg.spin, hypot(tg.kvx, tg.kvy), x, y) }
            g.t < tg.laughUntil -> pose(b, MonkeyState.GIGGLE, "happy")
            tg.warning -> pose(b, MonkeyState.STARTLED, "fear")
            else -> pose(b, if (tg.flies) MonkeyState.IDLE else MonkeyState.WALK, "smug")
        }
        drawBody(c, b, x, y, tg.facing, tg.alpha)
        c.restore()
        c.restoreToCount(outer)
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
        line.color = if (tension < SlingGame.MIN_PULL / SlingGame.MAX_PULL) Color.rgb(0x2A, 0x1E, 0x1A) else powerColor(SlingGame.powerFor(tension))
        line.strokeWidth = (6f - 2.5f * tension) * d
        c.drawLine(x0, y0, x1, y1, line)
    }

    private fun powerColor(p: SlingGame.Power) = when (p) {
        SlingGame.Power.SOFT -> Color.rgb(0x3D, 0xC9, 0x6B)
        SlingGame.Power.MEDIUM -> Color.rgb(0xF5, 0xB7, 0x1F)
        SlingGame.Power.HARD -> Color.rgb(0xF0, 0x4E, 0x37)
    }
    private fun powerLabel(p: SlingGame.Power) = when (p) {
        SlingGame.Power.SOFT -> "SOFT · 1 bounce"
        SlingGame.Power.MEDIUM -> "MEDIUM · 2 bounces"
        SlingGame.Power.HARD -> "HARD · 3 bounces"
    }

    /** Aim dots in the power colour; a soft or medium pull shows the path on past its edge bounces. */
    private fun drawAim(c: Canvas, g: SlingGame) {
        val p = g.power
        fill.color = powerColor(p)
        val n = 14
        for (i in 1..n) {
            val q = g.aimPoint(i * 0.035f) ?: break
            fill.alpha = (235 - i * (if (n == 6) 28 else 13)).coerceAtLeast(40)
            c.drawCircle(px(q.first), px(q.second - SlingGame.BODY_H * 0.45f), (5.5f - i * 0.22f).coerceAtLeast(2.5f) * d, fill)
        }
        fill.alpha = 255
    }

    /** v10.43 the power of the pull in words, on a pill under the fork: the player always knows bounce or splat. */
    private fun drawPower(c: Canvas, g: SlingGame) {
        if (g.tension < SlingGame.MIN_PULL / SlingGame.MAX_PULL) return
        val p = g.power; val label = powerLabel(p)
        text.textSize = 17f * d
        val tw = text.measureText(label) + 30f * d; val th = 36f * d
        val cx = w / 2f; val cy = (px(g.homeY) - px(SlingGame.BODY_H) - 78f * d).coerceAtLeast(topPad + 130f * d)
        rect.set(cx - tw / 2f, cy - th / 2f, cx + tw / 2f, cy + th / 2f)
        fill.color = powerColor(p); c.drawRoundRect(rect, th / 2f, th / 2f, fill)
        text.color = Color.WHITE; c.drawText(label, cx, cy + text.textSize * 0.36f, text)
    }

    private fun drawHud(c: Canvas, g: SlingGame) {
        val hh = 44f * d; val y0 = topPad
        rect.set(w * 0.05f, y0, w * 0.82f, y0 + hh)
        fill.color = Color.argb(235, 0xFF, 0xFF, 0xFF); c.drawRoundRect(rect, hh / 2, hh / 2, fill)
        text.textSize = 17f * d
        val secs = kotlin.math.ceil(g.timeLeft.toDouble()).toInt()
        text.color = if (secs <= 5 && !g.over) Color.rgb(0xD3, 0x2F, 0x2F) else Color.rgb(0x22, 0x22, 0x2A)
        val ty = y0 + hh / 2 + text.textSize * 0.36f
        val tx = rect.left + rect.width() * 0.15f
        c.drawText("⏱ $secs", tx, ty, text)
        val ga = clock - gainT   // "+3" floats up beside the timer
        if (gainText.isNotEmpty() && ga < 1.0f) {
            text.color = Color.rgb(0x1E, 0x9E, 0x4A); text.alpha = (255 * (1f - ga)).toInt().coerceIn(0, 255)
            c.drawText(gainText, tx + 44f * d, ty - ga * 16f * d, text); text.alpha = 255
        }
        text.color = Color.rgb(0x22, 0x22, 0x2A)
        // level, hits and the next star goal: "L2 🎯 4/8"
        val goal = g.nextGoal
        c.drawText("L${g.stage} 🎯 ${g.score}${if (goal != null) "/$goal" else ""}", rect.left + rect.width() * 0.52f, ty, text)
        c.drawText("🔥 ${g.streak}", rect.left + rect.width() * 0.87f, ty, text)
        closeBtn.set(w * 0.85f, y0, w * 0.85f + hh, y0 + hh)
        fill.color = Color.argb(235, 0xFF, 0xFF, 0xFF); c.drawOval(closeBtn, fill)
        text.color = Color.rgb(0x44, 0x44, 0x55); c.drawText("✕", closeBtn.centerX(), ty, text)
    }

    private fun starText(n: Int) = "★".repeat(n.coerceIn(0, 3)) + "☆".repeat(3 - n.coerceIn(0, 3))

    /** "◀  Level 2 ★★☆  ▶": pick any level that is open (replay an easier one, or go back up). */
    private fun drawLevelRow(c: Canvas, cy: Float, dark: Boolean = false) {
        val open = SlingGameHub.unlocked(context)
        val bh = 40f * d
        text.textSize = 16f * d
        val label = "Level $selLevel  ${starText(SlingGameHub.stars(context, selLevel))}"
        val tw = text.measureText(label) + 28f * d
        rect.set(w / 2f - tw / 2f, cy - bh / 2f, w / 2f + tw / 2f, cy + bh / 2f)
        fill.color = if (dark) Color.rgb(0xF1, 0xF0, 0xFA) else Color.argb(235, 0xFF, 0xFF, 0xFF); c.drawRoundRect(rect, bh / 2f, bh / 2f, fill)
        text.color = Color.rgb(0x33, 0x2B, 0x7A); c.drawText(label, w / 2f, cy + text.textSize * 0.36f, text)
        prevBtn.set(rect.left - bh - 8f * d, cy - bh / 2f, rect.left - 8f * d, cy + bh / 2f)
        nextBtn.set(rect.right + 8f * d, cy - bh / 2f, rect.right + 8f * d + bh, cy + bh / 2f)
        for ((btn, on, ch) in listOf(Triple(prevBtn, selLevel > 1, "◀"), Triple(nextBtn, selLevel < open, "▶"))) {
            fill.color = if (on) Color.rgb(0x5B, 0x4B, 0xD6) else Color.argb(90, 0x99, 0x99, 0xAA); c.drawOval(btn, fill)
            text.color = Color.WHITE; c.drawText(ch, btn.centerX(), cy + text.textSize * 0.36f, text)
        }
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
        val rr = result
        val cw = w * 0.84f; val ch = 340f * d
        val x0 = (w - cw) / 2f; val y0 = height * 0.26f
        rect.set(x0, y0, x0 + cw, y0 + ch)
        fill.color = Color.WHITE; c.drawRoundRect(rect, 26f * d, 26f * d, fill)
        text.color = Color.rgb(0x77, 0x77, 0x88); text.textSize = 14f * d
        c.drawText("Level ${g.stage}", w / 2f, y0 + 30f * d, text)
        text.color = Color.rgb(0xF5, 0xB7, 0x1F); text.textSize = 40f * d
        c.drawText(starText(rr?.stars ?: g.stars), w / 2f, y0 + 76f * d, text)
        text.color = Color.rgb(0x22, 0x22, 0x2A); text.textSize = 24f * d
        c.drawText(if (g.score == 0) "Time's up!" else "${g.score} ${if (g.score == 1) "hit" else "hits"}! 🎯", w / 2f, y0 + 116f * d, text)
        text.textSize = 15f * d
        val opened = rr?.unlockedNow
        val goal = SlingGame.STAR_GOALS.firstOrNull { it > g.score }
        text.color = if (opened != null) Color.rgb(0x1E, 0x9E, 0x4A) else Color.rgb(0x55, 0x55, 0x66)
        c.drawText(when {
            opened != null -> "🔓 Level $opened is open!"
            goal != null -> "${goal - g.score} more ${if (goal - g.score == 1) "hit" else "hits"} for the next star"
            else -> "All three stars! 🏆"
        }, w / 2f, y0 + 146f * d, text)
        text.color = Color.rgb(0x55, 0x55, 0x66)
        c.drawText("Best streak ${g.bestStreak} 🔥   ·   " + (if (newBest && g.score > 0) "new best today!" else "today's best $best"), w / 2f, y0 + 172f * d, text)
        if (SlingGameHub.unlocked(context) > 1) drawLevelRow(c, y0 + 216f * d, dark = true)
        val bw = cw * 0.42f; val bh = 50f * d; val by = y0 + ch - bh - 22f * d
        againBtn.set(x0 + cw * 0.06f, by, x0 + cw * 0.06f + bw, by + bh)
        doneBtn.set(x0 + cw * 0.94f - bw, by, x0 + cw * 0.94f, by + bh)
        fill.color = Color.rgb(0x5B, 0x4B, 0xD6); c.drawRoundRect(againBtn, bh / 2, bh / 2, fill)
        fill.color = Color.rgb(0xE8, 0xE8, 0xF0); c.drawRoundRect(doneBtn, bh / 2, bh / 2, fill)
        text.textSize = 16f * d
        text.color = Color.WHITE; c.drawText(if (selLevel == g.stage) "▶ Play again" else "▶ Play level $selLevel", againBtn.centerX(), againBtn.centerY() + 6f * d, text)
        text.color = Color.rgb(0x33, 0x33, 0x44); c.drawText("Done", doneBtn.centerX(), doneBtn.centerY() + 6f * d, text)
    }

    companion object {
        /** The end card closes by itself after this long. */
        const val END_CARD_SEC = 25f
        /**
         * Tests only: don't run the frame loop (the test steps the game with [advance]). Robolectric runs a
         * self-reposting frame callback in a tight loop and pushes its clock forward, which plays a whole round at once.
         */
        @Volatile var debugNoLoop = false
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
