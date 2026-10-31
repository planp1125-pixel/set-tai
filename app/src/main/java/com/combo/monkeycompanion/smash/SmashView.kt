package com.combo.monkeycompanion.smash

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.character.Character
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.engine.CharacterProfiles
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * v10.5 Screen Smash game view: the screen picture cut into tiles, the character at the bottom firing its own attack
 * (laser, blasts, juggling balls, water, stones, tawa, cheese) wherever you tap or swipe. Cracks, shards, comic POW
 * bursts, combo and mega smash, a 30 s timer, a score card, then the screen "repairs" itself.
 * v10.6 feel pass: screen shake, wind-up before throws, fewer/timed bursts, combo milestones, debris pile, 60 fps cap.
 */
@SuppressLint("ViewConstructor")
class SmashView(ctx: Context, private val shot: Bitmap, private val who: Character, private val onDone: () -> Unit) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val attack = Attack.of(who.id)
    private var game: SmashGame? = null
    /** Tests drive the clock themselves. */
    var autoStep = true
    private var lastNanos = 0L
    private var t = 0f
    private var doneSent = false
    private val prefs = ctx.getSharedPreferences("settai_v3", Context.MODE_PRIVATE)
    private var best = prefs.getInt("smashBest", 0)
    private var newBest = false

    // Character
    private val renderer = who.newRenderer().also { it.wear = CharacterRegistry.wear(who) }
    private val rig = MonkeyRig(); private val pose = Pose(); private val anim = AnimContext()
    private val charScale get() = 0.66f * d * 1.2f * CharacterProfiles.of(who).drawScale
    private var charX = -1f; private var charTargetX = -1f; private var facing = 1f
    private var throwT = 0f; private var jumpT = 0f
    private var sayText: String? = null; private var sayLeft = 0f
    private var firstTap = false
    // v10.8 polish: hop/fly toward the target, victory / oops reactions, sounds, Rocket Girl's firework rockets.
    private val fxRnd = Random(SystemClock.uptimeMillis() xor 0x5A5AL)   // own RNG for the new bits
    private val flier = who.locomotion.contains(com.combo.monkeycompanion.character.Locomotion.FLY) || CharacterProfiles.of(who).ridesProp
    private var hopT = 0f; private var cheerT = 0f; private var oopsT = 0f
    private val fireworks = com.combo.monkeycompanion.rig.Fireworks(SystemClock.uptimeMillis(), cap = 240).also { fw ->
        fw.onBoom = { pitch, crackle, big ->
            com.combo.monkeycompanion.audio.Sfx.play(context, if (big) com.combo.monkeycompanion.audio.Sfx.S.MEGA else com.combo.monkeycompanion.audio.Sfx.S.FW_BOOM, 0.6f, pitch)
            if (crackle) com.combo.monkeycompanion.audio.Sfx.play(context, com.combo.monkeycompanion.audio.Sfx.S.FW_CRACKLE, 0.4f)
        }
    }

    // Drawing
    private val src = Rect(); private val dst = RectF()
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val tri = Path(); private val tmpPath = Path(); private val r = RectF()
    private val pt = FloatArray(2); private val pt2 = FloatArray(2)
    private var crackPaths: Array<Path?> = emptyArray()
    private var crackBuiltSeed = IntArray(0)
    private val stars = FloatArray(80)
    private var voidShader: Shader? = null

    // v10.6 screen shake (smash layer only; off when the system "remove animations" setting is on)
    private val shakeOn = ValueAnimator.areAnimatorsEnabled()
    private var shakeAmp = 0f; private var shakeDur = 0f; private var shakeLeft = 0f
    private var shakeAng = 0f; private var shakeX = 0f; private var shakeY = 0f

    // v10.6 wind-up: thrown attacks crouch ~100 ms before the projectile leaves
    private val windUpSecs = when (attack) { Attack.LASER -> 0f; else -> 0.1f }
    private val maxPending = 6
    private val pendX = FloatArray(maxPending); private val pendY = FloatArray(maxPending); private val pendT = FloatArray(maxPending) { -1f }
    private var windUpT = 0f

    // v10.6 combo milestone label (x3, x5, x10, then every 10)
    private var comboLabel = 0; private var comboLabelT = -1f

    // v10.6 frame cap: Smash draws at most ~60 fps even on 90/120 Hz screens
    private var frameStartNanos = 0L

    // Comic impact bursts (v10.6: at most 2 on screen, spaced apart; only on breaks and megas)
    private val maxPows = 2
    private val powX = FloatArray(maxPows); private val powY = FloatArray(maxPows); private val powT = FloatArray(maxPows) { -1f }
    private val powWord = arrayOfNulls<String>(maxPows); private val powBig = BooleanArray(maxPows)
    private var nextPow = 0
    private val words = when (attack) {
        Attack.LASER -> listOf("ZAP!", "ZZZT!", "PEW!")
        Attack.WATER -> listOf("SPLASH!", "SPLOOSH!", "PSSHH!")
        Attack.BLAST -> listOf("BOOM!", "KA-BLAM!", "ZOOM!")
        else -> listOf("POW!", "BAM!", "KRAK!", "SMASH!", "BONK!")
    }

    // Buttons (screen px, set while drawing)
    private val closeR = RectF(); private val againR = RectF(); private val doneR = RectF()
    private var lastFireX = 0f; private var lastFireY = 0f; private var lastFireAt = 0f

    private val startLine = when (who.id) {
        "superboy" -> "Heat vision: ON! 👀🔥"
        "armour" -> "Repulsors charged ⚡"
        "mask" -> "Juggling balls… ATTACK! 🤹"
        "gajju" -> "Water cannon ready! 💦🐘"
        "billa" -> "Tawa attack, machan! 🍳"
        "chittu" -> "Cheese missiles armed! 🧀"
        "rocketgirl" -> "Firework rockets, light the fuse! 🚀🎆"
        "devil" -> "Black magic smash! Poof, poof, POOF! 😈✨"
        "surfer" -> "Tsunami smash! Surf the tiles! 🌊🏄‍♀️"
        "hollowman" -> "Invisible kicks! You won't see them coming 🕺"
        else -> "Stone time! Let's smash it! 🪨"
    }

    init { isClickable = true; com.combo.monkeycompanion.audio.Sfx.warm(ctx) }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w <= 0 || h <= 0) return
        val g = SmashGame(w.toFloat(), h.toFloat(), d)
        g.onImpact = ::impacted
        game = g
        crackPaths = arrayOfNulls(g.tiles); crackBuiltSeed = IntArray(g.tiles) { -1 }
        val rnd = Random(7)
        for (i in 0 until stars.size / 2) { stars[2 * i] = rnd.nextFloat() * w; stars[2 * i + 1] = rnd.nextFloat() * h }
        voidShader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.rgb(0x16, 0x10, 0x2E), Color.rgb(0x06, 0x05, 0x10), Shader.TileMode.CLAMP)
        if (charX < 0f) { charX = w / 2f; charTargetX = charX }
        say(startLine, 2.4f)
    }

    private fun say(s: String, secs: Float) { sayText = s; sayLeft = secs }

    private fun impacted(tile: Int, shattered: Boolean, mega: Boolean, x: Float, y: Float) {
        if (mega) shake(14f * d, 0.3f) else if (shattered) shake(6f * d, 0.15f) else shake(2.5f * d, 0.08f)
        val g = game
        if (g != null && (g.combo == 3 || g.combo == 5 || (g.combo >= 10 && g.combo % 10 == 0))) { comboLabel = g.combo; comboLabelT = 0f }
        if (mega || shattered) {
            // Replace a burst that's too close (keeps them spaced), else the oldest.
            var i = -1
            for (k in 0 until maxPows) if (powT[k] >= 0f && hypot(powX[k] - x, powY[k] - y) < 140f * d) i = k
            if (i < 0) { i = nextPow; nextPow = (nextPow + 1) % maxPows }
            powX[i] = x; powY[i] = y; powT[i] = 0f; powBig[i] = mega
            powWord[i] = if (mega) "MEGA SMASH!" else words[Random.nextInt(words.size)]
        }
        performHapticFeedback(if (mega || shattered) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY)
        if (mega) { jumpT = 0.7f; say("MEGA SMASH!!! 💥", 1.6f) }
        // v10.8 sound + victory
        val sfx = com.combo.monkeycompanion.audio.Sfx
        when {
            mega -> sfx.play(context, com.combo.monkeycompanion.audio.Sfx.S.MEGA, 1f)
            shattered -> sfx.play(context, com.combo.monkeycompanion.audio.Sfx.S.SHATTER, 0.8f, 0.9f + fxRnd.nextFloat() * 0.25f)
            else -> sfx.play(context, com.combo.monkeycompanion.audio.Sfx.S.CRACK, 0.7f, 0.9f + fxRnd.nextFloat() * 0.3f)
        }
        if (shattered && !mega) cheerT = 0.45f
        if (attack == Attack.ROCKET) fireworks.burstAt(x, y, charScale * (if (mega) 1.1f else 0.7f), big = mega)
    }

    private fun shake(amp: Float, secs: Float) {
        if (!shakeOn) return
        if (shakeLeft > 0f && amp * secs < shakeAmp * shakeLeft) return
        shakeAmp = amp; shakeDur = secs; shakeLeft = secs; shakeAng = Random.nextFloat() * 6.2832f
    }

    /** Where this character's attack comes from (eyes, trunk or hand) in screen px. */
    private fun launchPoint(out: FloatArray) {
        when (attack) {
            Attack.LASER -> { rig.headTop(out); out[1] += 40f * charScale }
            Attack.WATER -> { rig.headTop(out); out[0] += facing * 26f * charScale; out[1] += 60f * charScale }
            else -> rig.handPoint(facing < 0f, out)
        }
    }

    private fun fire(x: Float, y: Float, tap: Boolean = false) {
        val g = game ?: return
        if (g.state != SmashGame.State.PLAYING) return
        if (tap && (g.tileAt(x, y) < 0 || g.hp[g.tileAt(x, y)] <= 0)) {
            // v10.8 "oops": tapped a hole that's already smashed
            if (oopsT <= 0f) { oopsT = 0.7f; if (fxRnd.nextFloat() < 0.35f) say(listOf("Oops, already smashed! 😅", "Hehe, missed! 🙈", "That one's gone! 😆")[fxRnd.nextInt(3)], 1.2f) }
        }
        if (abs(x - charX) > 4f) facing = if (x > charX) 1f else -1f
        charTargetX = x.coerceIn(60f * d, width - 60f * d)
        if (windUpSecs > 0f) {
            // Crouch first; the throw itself happens in step() when the wind-up ends.
            if (g.tileAt(x, y) < 0 || g.hp[g.tileAt(x, y)] <= 0) return
            val k = (0 until maxPending).firstOrNull { pendT[it] < 0f } ?: return
            pendX[k] = x; pendY[k] = y; pendT[k] = windUpSecs; windUpT = windUpSecs
            firstTap = true; lastFireX = x; lastFireY = y; lastFireAt = t
            return
        }
        launch(x, y)
    }

    private fun launch(x: Float, y: Float) {
        val g = game ?: return
        if (abs(x - charX) > 4f) facing = if (x > charX) 1f else -1f
        layoutChar()
        launchPoint(pt)
        if (g.tap(x, y, pt[0], pt[1], attack)) {
            throwT = 0.22f; firstTap = true
            if (hopT <= 0f) hopT = HOP
            if (attack != Attack.LASER) com.combo.monkeycompanion.audio.Sfx.play(context, com.combo.monkeycompanion.audio.Sfx.S.WHOOSH, 0.35f, 0.9f + fxRnd.nextFloat() * 0.3f)
            lastFireX = x; lastFireY = y; lastFireAt = t
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val g = game ?: return true
        val idx = e.actionIndex
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = e.getX(idx); val y = e.getY(idx)
                when (g.state) {
                    SmashGame.State.PLAYING -> if (closeR.contains(x, y)) endNow() else fire(x, y, tap = true)
                    SmashGame.State.OVER -> when {
                        againR.contains(x, y) -> { g.reset(); newBest = false; firstTap = false; pendT.fill(-1f); windUpT = 0f; comboLabelT = -1f; say(startLine, 2f) }
                        doneR.contains(x, y) || closeR.contains(x, y) -> g.repair()
                    }
                    else -> Unit
                }
            }
            MotionEvent.ACTION_MOVE -> if (g.state == SmashGame.State.PLAYING) {
                // Swipe across the screen to smash a trail of tiles.
                val x = e.getX(0); val y = e.getY(0)
                if (hypot(x - lastFireX, y - lastFireY) > g.tileW * 0.55f && t - lastFireAt > 0.07f) fire(x, y)
            }
        }
        return true
    }

    /** Back / X: straight to the screen repairing itself. */
    fun endNow() { game?.quit() }

    /** One frame of game time. */
    fun step(dt: Float) {
        val g = game ?: return
        val was = g.state
        t += dt
        g.step(dt)
        if (was == SmashGame.State.PLAYING && g.state == SmashGame.State.OVER) {
            if (g.score > best) { best = g.score; newBest = true; prefs.edit().putInt("smashBest", best).apply() }
            say(if (g.smashed == g.tiles) "I smashed EVERYTHING! 🏆" else "That was AWESOME! 😎", 3f)
        }
        if (g.state == SmashGame.State.DONE && !doneSent) { doneSent = true; post { onDone() } }
        for (k in 0 until maxPending) if (pendT[k] >= 0f) {
            pendT[k] -= dt
            if (pendT[k] < 0f) { pendT[k] = -1f; if (g.state == SmashGame.State.PLAYING) launch(pendX[k], pendY[k]) }
        }
        if (windUpT > 0f) windUpT -= dt
        if (shakeLeft > 0f) {
            shakeLeft = max(0f, shakeLeft - dt)
            val k = shakeLeft / shakeDur; val env = k * k
            val osc = sin(t * 95f) * shakeAmp * env
            val wob = sin(t * 61f + 1.3f) * shakeAmp * env * 0.45f
            shakeX = cos(shakeAng) * osc - sin(shakeAng) * wob; shakeY = sin(shakeAng) * osc + cos(shakeAng) * wob
        } else { shakeX = 0f; shakeY = 0f }
        if (comboLabelT >= 0f) { comboLabelT += dt; if (comboLabelT > 0.9f) comboLabelT = -1f }
        if (throwT > 0f) throwT -= dt
        if (jumpT > 0f) jumpT -= dt
        if (hopT > 0f) hopT -= dt
        if (cheerT > 0f) cheerT -= dt
        if (oopsT > 0f) oopsT -= dt
        if (fireworks.active) fireworks.step(dt)
        if (sayLeft > 0f) sayLeft -= dt
        for (i in 0 until maxPows) if (powT[i] >= 0f) { powT[i] += dt; if (powT[i] > 0.6f) powT[i] = -1f }
        val sp = 900f * d * dt
        charX += (charTargetX - charX).coerceIn(-sp, sp)
    }

    private fun layoutChar() {
        val g = game ?: return
        val st = when {
            g.state == SmashGame.State.OVER -> if (g.smashed == g.tiles) MonkeyState.DANCE else MonkeyState.HAPPY_PAT
            jumpT > 0f -> MonkeyState.JUMP
            else -> MonkeyState.IDLE
        }
        who.fill(pose, st, t, if (st == MonkeyState.JUMP) 0.7f - jumpT else t, anim, CharacterRegistry.wear(who))
        if (windUpT > 0f && st == MonkeyState.IDLE && attack != Attack.WATER) {
            val k = windUpK(); pose.armRRaise = -40f * k; pose.armRElbow = -50f * k; pose.mouthOpen = 0.2f
        } else if (throwT > 0f && st == MonkeyState.IDLE) {
            val k = throwT / 0.22f
            if (attack == Attack.LASER || attack == Attack.WATER) { pose.eyeOpen = 1.4f; pose.browAngle = 10f }
            else { pose.armRRaise = 60f + 110f * k; pose.armRElbow = -20f; pose.mouthOpen = 0.5f }
        }
        // v10.8: victory arms / "oops" shrug, a hop (or a fly-up for fliers) on each throw.
        if (st == MonkeyState.IDLE && cheerT > 0f && throwT <= 0f) { val k = sin(cheerT / 0.45f * 3.1416f); pose.armLRaise = 150f * k; pose.mouthOpen = 0.6f * k; pose.eyeOpen = 1.2f }
        if (st == MonkeyState.IDLE && oopsT > 0f) { val k = sin(oopsT / 0.7f * 3.1416f); pose.headRot = -14f * k; pose.armLRaise = 40f * k; pose.browAngle = -8f * k }
        val hk = if (hopT > 0f) sin((1f - hopT / HOP) * 3.1416f) else 0f
        val lift = if (flier) 46f * d + 6f * d * sin(t * 3f) + 26f * d * hk else 22f * d * hk
        if (hk > 0f) pose.squashY = 1f + 0.06f * hk
        rig.layout(pose, charX, height - 26f * d + windUpDip() - lift, charScale, facing)
    }

    /** 0..1 how deep into the wind-up crouch we are (eases in, snaps out on release). */
    private fun windUpK(): Float = if (windUpT <= 0f || windUpSecs <= 0f) 0f else (1f - windUpT / windUpSecs).let { it * it * (3f - 2f * it) }
    private fun windUpDip() = 6f * d * windUpK()

    override fun onDraw(c: Canvas) {
        val g = game ?: return
        if (autoStep) {
            val now = SystemClock.elapsedRealtimeNanos()
            frameStartNanos = now
            val dt = if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1e9f).coerceIn(0f, 0.05f)
            lastNanos = now
            step(dt)
        }
        drawVoid(c)
        c.save(); c.translate(shakeX, shakeY)
        drawHoles(c, g)
        drawTiles(c, g)
        drawShards(c, g)
        c.restore()
        layoutChar()
        val wk = windUpK()
        if (wk > 0f) { c.save(); c.scale(1f + 0.08f * wk, 1f - 0.1f * wk, charX, height - 26f * d) }
        renderer.draw(c, rig, pose, t)
        if (wk > 0f) c.restore()
        c.save(); c.translate(shakeX, shakeY)
        drawShots(c, g)
        if (fireworks.active) fireworks.draw(c)
        drawPows(c)
        c.restore()
        drawSay(c)
        drawHud(c, g)
        if (g.state == SmashGame.State.OVER) drawCard(c, g)
        if (autoStep && !doneSent) {
            // ~60 fps cap: wait out the rest of a 16 ms frame instead of drawing on every 90/120 Hz vsync.
            val spentMs = ((SystemClock.elapsedRealtimeNanos() - frameStartNanos) / 1_000_000L)
            val wait = 16L - spentMs
            if (wait > 1L) postInvalidateDelayed(wait) else postInvalidateOnAnimation()
        }
    }

    /** v10.8 behind the "glass": a cosy cracked wallpaper (cream with saffron diamonds) instead of the bare night. */
    private fun drawVoid(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        fill.color = Color.rgb(0xF6, 0xE7, 0xC8); c.drawRect(0f, 0f, w, h, fill)
        val step = 46f * d
        var row = 0
        var y = 0f
        while (y < h + step) {
            var x = if (row % 2 == 0) 0f else step / 2f
            while (x < w + step) {
                tmpPath.reset(); tmpPath.moveTo(x, y - 9f * d); tmpPath.lineTo(x + 7f * d, y); tmpPath.lineTo(x, y + 9f * d); tmpPath.lineTo(x - 7f * d, y); tmpPath.close()
                fill.color = Color.rgb(0xF0, 0xA8, 0x40); fill.alpha = 120; c.drawPath(tmpPath, fill)
                fill.color = Color.rgb(0xC0, 0x45, 0x2E); fill.alpha = 110; c.drawCircle(x + step / 2f, y + step / 2f, 2.2f * d, fill)
                x += step
            }
            y += step / 2f; row++
        }
        fill.alpha = 255
        // a few thin plaster cracks
        stroke.color = Color.argb(70, 0x6B, 0x4A, 0x2A); stroke.strokeWidth = 1.4f * d
        for (i in 0 until 6) {
            val sx = stars[2 * i]; val sy = stars[2 * i + 1]
            c.drawLine(sx, sy, sx + 30f * d, sy + 18f * d, stroke); c.drawLine(sx + 30f * d, sy + 18f * d, sx + 44f * d, sy + 8f * d, stroke)
        }
    }

    /** v10.8: each smashed tile shows a playful hole in the wallpaper: starry night inside, sometimes a peeking monkey. */
    private fun drawHoles(c: Canvas, g: SmashGame) {
        val repairing = g.state == SmashGame.State.REPAIR || g.state == SmashGame.State.DONE
        for (i in 0 until g.tiles) {
            if (g.hp[i] > 0) continue
            val l = g.tileLeft(i); val tp = g.tileTop(i); val tw = g.tileW; val th = g.tileH
            val a = if (repairing) 1f - g.repairK else 1f
            if (a <= 0.02f) continue
            val cx = l + tw / 2f; val cy = tp + th / 2f
            tmpPath.reset()
            val n = 11
            for (k in 0 until n) {
                val ang = k * 6.2832f / n
                val jag = 0.78f + 0.2f * (((i * 31 + k * 17) % 7) / 6f)
                val px = cx + cos(ang) * tw * 0.5f * jag; val py = cy + sin(ang) * th * 0.5f * jag
                if (k == 0) tmpPath.moveTo(px, py) else tmpPath.lineTo(px, py)
            }
            tmpPath.close()
            fill.shader = voidShader; fill.alpha = (255 * a).toInt(); c.drawPath(tmpPath, fill); fill.shader = null
            c.save(); c.clipPath(tmpPath)
            for (k in 0 until 4) {
                val sx = l + ((i * 37 + k * 53) % 97) / 97f * tw; val sy = tp + ((i * 61 + k * 29) % 89) / 89f * th
                fill.color = Color.WHITE; fill.alpha = ((90 + 80 * sin(t * 2f + i + k)) * a).toInt().coerceIn(10, 255); c.drawCircle(sx, sy, 1.4f * d, fill)
            }
            if (i % 5 == 2 && !repairing) {
                // a little monkey peeking up from the bottom of the hole, blinking
                val r0 = min(tw, th) * 0.22f; val my = tp + th * 0.82f - r0 * 0.4f * sin(min(1f, g.brokenT[i].coerceAtLeast(0f) * 2f) * 1.57f)
                fill.alpha = (255 * a).toInt()
                fill.color = Color.rgb(0x8B, 0x5A, 0x2B); c.drawCircle(cx - r0 * 0.95f, my - r0 * 0.2f, r0 * 0.38f, fill); c.drawCircle(cx + r0 * 0.95f, my - r0 * 0.2f, r0 * 0.38f, fill)
                c.drawCircle(cx, my, r0, fill)
                fill.color = Color.rgb(0xF2, 0xC9, 0x94); c.drawOval(cx - r0 * 0.7f, my - r0 * 0.55f, cx + r0 * 0.7f, my + r0 * 0.55f, fill)
                val blink = ((t * 0.7f + i) % 3f) < 0.12f
                fill.color = Color.rgb(0x2A, 0x1A, 0x10)
                if (blink) { stroke.color = fill.color; stroke.strokeWidth = 1.6f * d; c.drawLine(cx - r0 * 0.42f, my - r0 * 0.15f, cx - r0 * 0.15f, my - r0 * 0.15f, stroke); c.drawLine(cx + r0 * 0.15f, my - r0 * 0.15f, cx + r0 * 0.42f, my - r0 * 0.15f, stroke) }
                else { c.drawCircle(cx - r0 * 0.3f, my - r0 * 0.15f, r0 * 0.13f, fill); c.drawCircle(cx + r0 * 0.3f, my - r0 * 0.15f, r0 * 0.13f, fill) }
            }
            c.restore()
            // torn wallpaper rim
            stroke.color = Color.argb((200 * a).toInt(), 0xE8, 0xD2, 0xA8); stroke.strokeWidth = 3f * d; c.drawPath(tmpPath, stroke)
            stroke.color = Color.argb((120 * a).toInt(), 0x5A, 0x3C, 0x1E); stroke.strokeWidth = 1.2f * d; c.drawPath(tmpPath, stroke)
        }
        fill.alpha = 255; stroke.alpha = 255
    }

    private fun tileSrc(g: SmashGame, i: Int) {
        val sx = shot.width / width.toFloat(); val sy = shot.height / height.toFloat()
        val l = g.tileLeft(i); val tp = g.tileTop(i)
        src.set((l * sx).toInt(), (tp * sy).toInt(), ((l + g.tileW) * sx).toInt(), ((tp + g.tileH) * sy).toInt())
    }

    private fun drawTiles(c: Canvas, g: SmashGame) {
        val repairing = g.state == SmashGame.State.REPAIR || g.state == SmashGame.State.DONE
        for (i in 0 until g.tiles) {
            val l = g.tileLeft(i); val tp = g.tileTop(i)
            dst.set(l, tp, l + g.tileW + 0.5f, tp + g.tileH + 0.5f)
            tileSrc(g, i)
            val a = when {
                g.hp[i] > 0 -> 255
                repairing -> (255 * g.repairK).toInt()
                else -> 0
            }
            if (a > 0) { bmpPaint.alpha = a; c.drawBitmap(shot, src, dst, bmpPaint) }
            if (g.hp[i] == 1) drawCrack(c, g, i, if (repairing) 1f - g.repairK else 1f)
            if (g.hp[i] == 0 && g.brokenT[i] in 0f..0.35f && !repairing) {
                stroke.color = Color.WHITE; stroke.alpha = (220 * (1f - g.brokenT[i] / 0.35f)).toInt(); stroke.strokeWidth = 3f * d
                c.drawRect(dst, stroke); stroke.alpha = 255
            }
        }
        bmpPaint.alpha = 255
    }

    /** Spider-web crack from the hit point: jagged rays to the edges plus a ring. */
    private fun drawCrack(c: Canvas, g: SmashGame, i: Int, alpha: Float) {
        if (alpha <= 0.01f) return
        if (crackBuiltSeed[i] != g.crackSeed[i] || crackPaths[i] == null) {
            val p = crackPaths[i] ?: Path().also { crackPaths[i] = it }
            p.reset()
            val rnd = Random(g.crackSeed[i])
            val hx = g.tileLeft(i) + g.hitU[i] * g.tileW; val hy = g.tileTop(i) + g.hitV[i] * g.tileH
            val len = hypot(g.tileW, g.tileH)
            val ringX = FloatArray(SmashGame.CRACK_RAYS); val ringY = FloatArray(SmashGame.CRACK_RAYS)
            for (k in 0 until SmashGame.CRACK_RAYS) {
                g.rayDir(g.crackSeed[i], k, pt)
                p.moveTo(hx, hy)
                var x = hx; var y = hy
                for (s in 1..4) {
                    val ang = (rnd.nextFloat() - 0.5f) * 0.6f
                    val dx = pt[0] * cos(ang) - pt[1] * sin(ang); val dy = pt[0] * sin(ang) + pt[1] * cos(ang)
                    x += dx * len / 4f; y += dy * len / 4f
                    p.lineTo(x, y)
                    if (s == 1) { ringX[k] = x; ringY[k] = y }
                }
            }
            p.moveTo(ringX[0], ringY[0])
            for (k in 1 until SmashGame.CRACK_RAYS) p.lineTo(ringX[k], ringY[k])
            p.close()
            crackBuiltSeed[i] = g.crackSeed[i]
        }
        c.save()
        c.clipRect(g.tileLeft(i), g.tileTop(i), g.tileLeft(i) + g.tileW, g.tileTop(i) + g.tileH)
        fill.color = Color.BLACK; fill.alpha = (40 * alpha).toInt(); c.drawRect(g.tileLeft(i), g.tileTop(i), g.tileLeft(i) + g.tileW, g.tileTop(i) + g.tileH, fill)
        stroke.color = Color.argb((150 * alpha).toInt(), 0, 0, 0); stroke.strokeWidth = 4f * d; c.drawPath(crackPaths[i]!!, stroke)
        stroke.color = Color.argb((235 * alpha).toInt(), 255, 255, 255); stroke.strokeWidth = 1.8f * d; c.drawPath(crackPaths[i]!!, stroke)
        c.restore()
        fill.alpha = 255; stroke.alpha = 255
    }

    /** Each shard is a triangle of the picture, spinning and falling. */
    private fun drawShards(c: Canvas, g: SmashGame) {
        for (i in 0 until g.shardCount) {
            val t0 = g.shardTile[i]; val o = i * 6
            c.save()
            c.translate(g.shardX[i], g.shardY[i]); c.rotate(g.shardRot[i])
            tri.reset(); tri.moveTo(g.shardTri[o], g.shardTri[o + 1]); tri.lineTo(g.shardTri[o + 2], g.shardTri[o + 3]); tri.lineTo(g.shardTri[o + 4], g.shardTri[o + 5]); tri.close()
            c.save(); c.clipPath(tri)
            // Draw the whole tile so its centre sits where this shard's piece of it belongs: the triangle is stored
            // around its own centroid, and its first corner is the crack point, so the tile centre is corner0 - crack.
            tileSrc(g, t0)
            dst.set(-g.tileW / 2f, -g.tileH / 2f, g.tileW / 2f, g.tileH / 2f)
            val cx = g.shardTri[o] - (g.tileW * (g.hitU[t0] - 0.5f))
            val cy = g.shardTri[o + 1] - (g.tileH * (g.hitV[t0] - 0.5f))
            dst.offset(cx, cy)
            val repairFade = if (g.state == SmashGame.State.REPAIR || g.state == SmashGame.State.DONE) 1f - g.repairK else 1f
            val life = (g.shardAlpha[i] * repairFade).coerceIn(0f, 1f)
            bmpPaint.alpha = (255 * life).toInt()
            c.drawBitmap(shot, src, dst, bmpPaint)
            c.restore()
            stroke.color = Color.WHITE; stroke.alpha = ((if (g.shardRest[i]) 90 else 200) * life).toInt(); stroke.strokeWidth = 1.4f * d
            c.drawPath(tri, stroke)
            c.restore()
        }
        bmpPaint.alpha = 255; stroke.alpha = 255
    }

    private fun drawShots(c: Canvas, g: SmashGame) {
        for (s in 0 until g.maxShots) {
            if (attack == Attack.LASER) {
                if (g.beamLeft[s] <= 0f) continue
                val a = (g.beamLeft[s] / 0.16f).coerceIn(0f, 1f)
                for (eye in intArrayOf(-1, 1)) {
                    val ox = g.shotFromX[s] + eye * 7f * charScale
                    stroke.color = Color.argb((150 * a).toInt(), 255, 40, 30); stroke.strokeWidth = 12f * d
                    c.drawLine(ox, g.shotFromY[s], g.shotToX[s], g.shotToY[s], stroke)
                    stroke.color = Color.argb((255 * a).toInt(), 255, 245, 200); stroke.strokeWidth = 3.5f * d
                    c.drawLine(ox, g.shotFromY[s], g.shotToX[s], g.shotToY[s], stroke)
                }
                continue
            }
            if (!g.shotActive[s]) continue
            g.shotPos(s, attack.arc, pt)
            val spin = g.shotT[s] * 900f
            when (attack) {
                Attack.BLAST -> {
                    fill.color = Color.argb(110, 80, 220, 255); c.drawCircle(pt[0], pt[1], 16f * d, fill)
                    fill.color = Color.rgb(0x9F, 0xEC, 0xFF); c.drawCircle(pt[0], pt[1], 9f * d, fill)
                    fill.color = Color.WHITE; c.drawCircle(pt[0], pt[1], 4.5f * d, fill)
                }
                Attack.WATER -> for (j in 0..4) {
                    val k = (g.shotT[s] - j * 0.025f).coerceAtLeast(0f)
                    val save = g.shotT[s]; g.shotT[s] = k; g.shotPos(s, false, pt2); g.shotT[s] = save
                    fill.color = Color.rgb(0x4F, 0xB3, 0xFF); fill.alpha = 230 - j * 35
                    c.drawCircle(pt2[0], pt2[1], (8f - j) * d, fill)
                    fill.color = Color.WHITE; fill.alpha = 200 - j * 30; c.drawCircle(pt2[0] - 2f * d, pt2[1] - 2f * d, 2.2f * d, fill)
                }
                Attack.BALL -> ball(c, pt[0], pt[1], 10f * d, Color.rgb(0xFF, 0x5A, 0x5F), spin)
                Attack.STONE -> {
                    c.save(); c.translate(pt[0], pt[1]); c.rotate(spin)
                    tmpPath.reset(); tmpPath.moveTo(-11f * d, -3f * d); tmpPath.lineTo(-5f * d, -10f * d); tmpPath.lineTo(7f * d, -9f * d)
                    tmpPath.lineTo(12f * d, 1f * d); tmpPath.lineTo(5f * d, 10f * d); tmpPath.lineTo(-8f * d, 8f * d); tmpPath.close()
                    fill.color = Color.rgb(0x8D, 0x87, 0x82); c.drawPath(tmpPath, fill)
                    fill.color = Color.rgb(0xB5, 0xAF, 0xA8); c.drawCircle(-3f * d, -3f * d, 3.5f * d, fill)
                    stroke.color = Color.rgb(0x3A, 0x34, 0x30); stroke.strokeWidth = 2f * d; c.drawPath(tmpPath, stroke)
                    c.restore()
                }
                Attack.PAN -> {
                    c.save(); c.translate(pt[0], pt[1]); c.rotate(spin)
                    fill.color = Color.rgb(0x26, 0x2A, 0x30); c.drawRect(-2.5f * d, 10f * d, 2.5f * d, 24f * d, fill); c.drawCircle(0f, 0f, 13f * d, fill)
                    fill.color = Color.rgb(0x45, 0x4B, 0x55); c.drawCircle(-3f * d, -3f * d, 6f * d, fill)
                    c.restore()
                }
                Attack.CHEESE -> {
                    c.save(); c.translate(pt[0], pt[1]); c.rotate(spin)
                    tmpPath.reset(); tmpPath.moveTo(-12f * d, 8f * d); tmpPath.lineTo(12f * d, 8f * d); tmpPath.lineTo(6f * d, -10f * d); tmpPath.close()
                    fill.color = Color.rgb(0xFF, 0xD1, 0x3A); c.drawPath(tmpPath, fill)
                    fill.color = Color.rgb(0xE0, 0xA8, 0x10); c.drawCircle(2f * d, 3f * d, 2.4f * d, fill); c.drawCircle(-4f * d, 5f * d, 1.6f * d, fill)
                    stroke.color = Color.rgb(0x8A, 0x60, 0x08); stroke.strokeWidth = 1.8f * d; c.drawPath(tmpPath, stroke)
                    c.restore()
                }
                Attack.ROCKET -> {
                    // v10.8 Rocket Girl: a mini Diwali rocket with a sparkle tail (it bursts into a firework on impact)
                    g.shotPos(s, false, pt2)
                    val k2 = (g.shotT[s] - 0.04f).coerceAtLeast(0f); val sv = g.shotT[s]; g.shotT[s] = k2; g.shotPos(s, attack.arc, pt2); g.shotT[s] = sv
                    val ang = Math.toDegrees(kotlin.math.atan2((pt[1] - pt2[1]).toDouble(), (pt[0] - pt2[0]).toDouble())).toFloat()
                    for (j in 0 until 5) { fill.color = if (j % 2 == 0) Color.rgb(0xFF, 0xD5, 0x4A) else Color.rgb(0xFF, 0x8A, 0x2A); fill.alpha = 220 - j * 40
                        c.drawCircle(pt2[0] + (pt2[0] - pt[0]) * j * 0.5f, pt2[1] + (pt2[1] - pt[1]) * j * 0.5f, (4f - j * 0.6f) * d, fill) }
                    fill.alpha = 255
                    c.save(); c.translate(pt[0], pt[1]); c.rotate(ang)
                    fill.color = Color.rgb(0xD8, 0x2A, 0x2A); c.drawRect(-10f * d, -4f * d, 8f * d, 4f * d, fill)
                    fill.color = Color.rgb(0xFF, 0xC8, 0x3A); c.drawRect(-4f * d, -4f * d, -1f * d, 4f * d, fill); c.drawRect(2f * d, -4f * d, 5f * d, 4f * d, fill)
                    tmpPath.reset(); tmpPath.moveTo(8f * d, -4f * d); tmpPath.lineTo(14f * d, 0f); tmpPath.lineTo(8f * d, 4f * d); tmpPath.close(); c.drawPath(tmpPath, fill)
                    stroke.color = Color.rgb(0x8A, 0x5A, 0x20); stroke.strokeWidth = 1.6f * d; c.drawLine(-10f * d, 0f, -22f * d, 0f, stroke)
                    c.restore()
                }
                Attack.LASER -> Unit
            }
        }
        fill.alpha = 255
    }

    private fun ball(c: Canvas, x: Float, y: Float, rad: Float, color: Int, spin: Float) {
        fill.color = color; c.drawCircle(x, y, rad, fill)
        stroke.color = Color.WHITE; stroke.strokeWidth = rad * 0.3f
        val a = Math.toRadians(spin.toDouble()); val dx = (cos(a) * rad * 0.7).toFloat(); val dy = (sin(a) * rad * 0.7).toFloat()
        c.drawLine(x - dx, y - dy, x + dx, y + dy, stroke)
        stroke.color = Color.rgb(0x2A, 0x22, 0x30); stroke.strokeWidth = rad * 0.18f; c.drawCircle(x, y, rad, stroke)
    }

    /** Comic starburst with a word, popping in and fading. */
    private fun drawPows(c: Canvas) {
        for (i in 0 until maxPows) {
            val k = powT[i]; if (k < 0f) continue
            val word = powWord[i] ?: continue
            val pop = easeOutBack(min(1f, k / 0.16f))
            val a = (255 * (1f - max(0f, k - 0.38f) / 0.22f)).toInt().coerceIn(0, 255)
            val big = if (powBig[i]) 1.8f else 1f
            val rad = 34f * d * pop * big
            tmpPath.reset()
            for (j in 0 until 20) {
                val ang = j * Math.PI / 10; val rr = if (j % 2 == 0) rad else rad * 0.62f
                val x = powX[i] + (cos(ang) * rr).toFloat(); val y = powY[i] + (sin(ang) * rr).toFloat()
                if (j == 0) tmpPath.moveTo(x, y) else tmpPath.lineTo(x, y)
            }
            tmpPath.close()
            fill.color = if (powBig[i]) Color.rgb(0xFF, 0x9F, 0x1C) else Color.rgb(0xFF, 0xE0, 0x3A); fill.alpha = a; c.drawPath(tmpPath, fill)
            stroke.color = Color.rgb(0xC6, 0x28, 0x28); stroke.alpha = a; stroke.strokeWidth = 2.5f * d; c.drawPath(tmpPath, stroke)
            text.textSize = 15f * d * pop * big; text.color = Color.rgb(0xC6, 0x28, 0x28); text.alpha = a
            c.drawText(word, powX[i], powY[i] + text.textSize * 0.35f, text)
        }
        fill.alpha = 255; stroke.alpha = 255; text.alpha = 255
    }

    /** 0 -> 1 with a slight overshoot (~1.1) before settling. */
    private fun easeOutBack(x: Float): Float { val c1 = 1.70158f; val c3 = c1 + 1f; val u = x - 1f; return 1f + c3 * u * u * u + c1 * u * u }

    private fun drawSay(c: Canvas) {
        val s = sayText ?: return
        if (sayLeft <= 0f) return
        rig.headTop(pt)
        text.textSize = 15f * d; text.color = Color.rgb(0x3A, 0x22, 0x10)
        val w = text.measureText(s) + 28f * d
        val cx = pt[0].coerceIn(w / 2f + 8f * d, width - w / 2f - 8f * d); val by = pt[1] - 18f * d
        r.set(cx - w / 2f, by - 40f * d, cx + w / 2f, by)
        fill.color = Color.WHITE; fill.alpha = (255 * min(1f, sayLeft * 3f)).toInt(); c.drawRoundRect(r, 18f * d, 18f * d, fill)
        text.alpha = fill.alpha; c.drawText(s, cx, by - 14f * d, text)
        fill.alpha = 255; text.alpha = 255
    }

    private fun pill(c: Canvas, cx: Float, cy: Float, label: String, bg: Int, textAlpha: Int = 255) {
        text.textSize = 17f * d
        val w = text.measureText(label) + 30f * d
        r.set(cx - w / 2f, cy - 20f * d, cx + w / 2f, cy + 20f * d)
        fill.color = bg; c.drawRoundRect(r, 20f * d, 20f * d, fill)
        text.color = Color.WHITE; text.alpha = textAlpha; c.drawText(label, cx, cy + 6f * d, text); text.alpha = 255
    }

    private fun drawHud(c: Canvas, g: SmashGame) {
        if (g.state == SmashGame.State.REPAIR || g.state == SmashGame.State.DONE) return
        val top = 46f * d
        pill(c, 70f * d, top, "💥 ${g.score}", Color.argb(200, 0x2A, 0x22, 0x48))
        pill(c, width / 2f, top, "⏱ ${g.timeLeft.toInt().coerceAtLeast(0)}", if (g.timeLeft < 6f) Color.argb(220, 0xD3, 0x2F, 0x2F) else Color.argb(200, 0x2A, 0x22, 0x48))
        closeR.set(width - 64f * d, top - 22f * d, width - 20f * d, top + 22f * d)
        fill.color = Color.argb(200, 0x2A, 0x22, 0x48); c.drawOval(closeR, fill)
        stroke.color = Color.WHITE; stroke.strokeWidth = 3f * d
        val cx = closeR.centerX(); val cy = closeR.centerY(); val k = 8f * d
        c.drawLine(cx - k, cy - k, cx + k, cy + k, stroke); c.drawLine(cx - k, cy + k, cx + k, cy - k, stroke)
        if (g.state == SmashGame.State.PLAYING) {
            if (g.megaReady) pill(c, width / 2f, top + 52f * d, "⚡ MEGA READY! ⚡", Color.argb(230, 0xFF, 0x8F, 0x00))
            else if (comboLabelT >= 0f) {
                // Milestones only (x3, x5, x10…): pop in with a little overshoot, then fade.
                val k = comboLabelT; val sc = easeOutBack(min(1f, k / 0.18f)); val a = (255 * (1f - max(0f, k - 0.6f) / 0.3f)).coerceIn(0f, 255f)
                c.save(); c.scale(sc, sc, width / 2f, top + 52f * d)
                pill(c, width / 2f, top + 52f * d, "COMBO ×$comboLabel!", Color.argb((220 * a / 255f).toInt(), 0xE9, 0x1E, 0x63), a.toInt())
                c.restore()
            }
            if (!firstTap) {
                text.textSize = 26f * d; text.color = Color.WHITE
                val a = (160 + 95 * sin(t * 5f)).toInt().coerceIn(0, 255)
                fill.color = Color.argb(150, 0, 0, 0); r.set(width / 2f - 150f * d, height * 0.42f - 34f * d, width / 2f + 150f * d, height * 0.42f + 22f * d)
                c.drawRoundRect(r, 24f * d, 24f * d, fill)
                text.alpha = a; c.drawText("TAP or SWIPE to smash!", width / 2f, height * 0.42f, text); text.alpha = 255
            }
        }
    }

    private fun drawCard(c: Canvas, g: SmashGame) {
        fill.color = Color.argb(140, 0, 0, 0); c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        val cw = min(width - 40f * d, 340f * d); val ch = 300f * d
        val cx = width / 2f; val top = height * 0.3f
        r.set(cx - cw / 2f, top, cx + cw / 2f, top + ch)
        fill.color = Color.WHITE; c.drawRoundRect(r, 28f * d, 28f * d, fill)
        text.color = Color.rgb(0x2A, 0x22, 0x48)
        text.textSize = 26f * d; c.drawText(if (g.smashed == g.tiles) "Total destruction! 🏆" else "Screen smashed! 💥", cx, top + 52f * d, text)
        text.textSize = 18f * d; c.drawText("${g.smashed} / ${g.tiles} tiles smashed", cx, top + 96f * d, text)
        text.textSize = 40f * d; text.color = Color.rgb(0xE9, 0x1E, 0x63); c.drawText("${g.score}", cx, top + 152f * d, text)
        text.textSize = 16f * d; text.color = Color.rgb(0x6A, 0x60, 0x80)
        c.drawText(if (newBest) "New best! 🎉" else "Best: $best", cx, top + 184f * d, text)
        val bw = (cw - 60f * d) / 2f; val by = top + ch - 76f * d
        againR.set(cx - bw - 10f * d, by, cx - 10f * d, by + 52f * d)
        doneR.set(cx + 10f * d, by, cx + bw + 10f * d, by + 52f * d)
        fill.color = Color.rgb(0xFF, 0x8F, 0x00); c.drawRoundRect(againR, 26f * d, 26f * d, fill)
        fill.color = Color.rgb(0x2A, 0x22, 0x48); c.drawRoundRect(doneR, 26f * d, 26f * d, fill)
        text.textSize = 18f * d; text.color = Color.WHITE
        c.drawText("Again", againR.centerX(), againR.centerY() + 6f * d, text)
        c.drawText("Done", doneR.centerX(), doneR.centerY() + 6f * d, text)
    }

    // ---------------- tests ----------------
    val debugGame get() = game
    fun debugTap(x: Float, y: Float) = fire(x, y)
    private companion object { const val HOP = 0.3f }
    fun debugTapAgain() { if (game?.state == SmashGame.State.OVER) onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, againR.centerX(), againR.centerY(), 0)) }
}
