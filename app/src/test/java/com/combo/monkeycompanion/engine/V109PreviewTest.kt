package com.combo.monkeycompanion.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.TestPaths
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.RocketFlight
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.engine.anim.AnimEngine
import com.combo.monkeycompanion.landing.LandingE2ETest
import com.combo.monkeycompanion.overlay.MonkeyView
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.DiwaliRocketArt
import com.combo.monkeycompanion.rig.Fireworks
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.rig.RocketGeom
import com.combo.monkeycompanion.rig.SkyShow
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.sin

/**
 * v10.9 preview sheet from the real code -> build/test-out/settai-v10.9-preview.png: Skye (face close-up, picker
 * thumbnail, the 45° broom-ride with the flame out of the rocket's tail), a swipe flight, the bottle launch, multi-ring
 * fireworks, the floating chat button, and the reference photos side by side (refs are read only here, never in the app).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class V109PreviewTest {
    private val W = 1980
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x2C, 0x3A, 0x5A); textSize = 22f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val white = Paint(label).apply { color = Color.WHITE }
    private val title = Paint(label).apply { textSize = 34f; textAlign = Paint.Align.LEFT; color = Color.rgb(0xB0, 0x3A, 0x1E) }
    private val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xF4, 0xE2) }
    private val girl = CharacterRegistry.byId("rocketgirl")!!

    private fun navy(c: Canvas, r: RectF) {
        val p = Paint().apply { shader = LinearGradient(0f, r.top, 0f, r.bottom, Color.rgb(0x06, 0x0B, 0x24), Color.rgb(0x16, 0x22, 0x4E), Shader.TileMode.CLAMP) }
        c.drawRect(r, p)
    }

    /** Skye at (x, feetY), [s] px per rig unit; [tweak] edits the pose after fill. */
    private fun drawGirl(c: Canvas, x: Float, feetY: Float, s: Float, state: MonkeyState = MonkeyState.IDLE, st: Float = 1.2f,
                         facing: Float = 1f, tweak: (Pose) -> Unit = {}): MonkeyRig {
        val wear = CharacterRegistry.wear(girl)
        val r = girl.newRenderer().also { it.wear = wear }
        val rig = MonkeyRig(); val p = Pose(); val e = AnimEngine(); e.setTuning(CharacterProfiles.of(girl).anim); r.anim = e
        val dt = 1f / 60f
        for (i in 0 until 40) { girl.fill(p, state, 1f + st + i * dt, st + i * dt, AnimContext(), wear); e.step(p, dt, 0f, 0f, 1f, 1f, false) }
        p.glint = 0.8f
        tweak(p)
        rig.layout(p, x, feetY, s, facing)
        r.draw(c, rig, p, 1f + st + 40 * dt)
        return rig
    }

    private fun panelCard(c: Canvas, r: RectF, text: String) {
        c.drawRoundRect(r, 28f, 28f, card)
        c.drawText(text, r.centerX(), r.bottom - 18f, label)
    }

    @Test fun renderV109() {
        val faceH = 560; val rideH = 520; val flightH = 640; val bottleH = 640; val fwH = 560; val btnH = 560; val refH = 760
        val H = 80 + faceH + 60 + rideH + 60 + flightH + 60 + bottleH + 60 + fwH + 60 + btnH + 60 + refH + 30
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.WHITE)
        c.drawText("Settai v10.9: Skye (slim, cute), her Diwali rocket, the bottle sky show, double-tap power + chat button", 20f, 50f, title)
        var y = 80f

        // ---- 1. face close-up + picker thumbnail + poses ----
        run {
            val r0 = RectF(10f, y, 660f, y + faceH); panelCard(c, r0, "Close-up: oval face, big lashed eyes, pigtails + ribbons")
            c.save(); c.clipRect(r0)
            drawGirl(c, r0.centerX() + 10f, y + faceH + 210f, 5.0f) { it.bodyRot = 0f; it.mouthSmile = 0.9f }
            c.restore()
            run { val tp = Paint(label); val t = "Close-up: oval face, big lashed eyes, pigtails + ribbons"; val tw0 = tp.measureText(t)
                c.drawRoundRect(RectF(r0.centerX() - tw0 / 2f - 12f, r0.bottom - 48f, r0.centerX() + tw0 / 2f + 12f, r0.bottom - 8f), 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEEFFFFFF.toInt() })
                c.drawText(t, r0.centerX(), r0.bottom - 18f, tp) }
            // picker thumbnail (the CharacterPreviewView maths)
            val r1 = RectF(680f, y, 980f, y + faceH); panelCard(c, r1, "Picker thumbnail")
            val th = faceH - 60f; val tw = 300f
            val p = Pose(); val rig = MonkeyRig(); val wear = CharacterRegistry.wear(girl)
            val rr = girl.newRenderer().also { it.wear = wear }
            girl.fill(p, MonkeyState.IDLE, 1f, 1f, AnimContext(), wear)
            var scale = th / 255f * CharacterProfiles.of(girl).drawScale
            var ay = th * 0.90f
            rig.layout(p, tw / 2f, ay, scale, 1f)
            val ht = FloatArray(2); rig.headTop(ht)
            if (ht[1] < 8f) {
                val span = (ay - ht[1]).coerceAtLeast(10f); scale *= ((ay - 8f) / span).coerceIn(0.65f, 1f)
                rig.layout(p, tw / 2f, ay, scale, 1f); rig.headTop(ht)
                if (ht[1] < 8f) { ay += (8f - ht[1]); rig.layout(p, tw / 2f, ay, scale, 1f); rig.headTop(ht) }
            }
            assertTrue("thumbnail head inside: ${ht[1]}", ht[1] >= 0f)
            c.save(); c.translate(680f, y); c.clipRect(0f, 0f, tw, th); rr.draw(c, rig, p, 1f); c.restore()
            // states
            val names = listOf("Waving" to MonkeyState.WAVE, "Giggle" to MonkeyState.GIGGLE, "Sleepy" to MonkeyState.SLEEP)
            names.forEachIndexed { i, (n, stt) ->
                val r2 = RectF(1000f + i * 327f, y, 1000f + (i + 1) * 327f - 10f, y + faceH); panelCard(c, r2, n)
                c.save(); c.clipRect(r2); drawGirl(c, r2.centerX(), r2.bottom - 150f, 2.6f, stt, 0.6f); c.restore()
            }
        }
        y += faceH + 60f

        // ---- 2. the 45° broom-ride with the flame from the rocket's tail ----
        c.drawText("Riding the rocket like a broom (≈45°): both hands on the tube, legs dangling, flame from the tube bottom", 20f, y - 14f, title)
        run {
            val specs = listOf(
                Triple("Hover (idle sparkle exhaust)", 0f, 1f), Triple("Full thrust burst", 1f, 1f),
                Triple("Facing left", 0.6f, -1f), Triple("Hair & dress flutter", 0.8f, 1f))
            specs.forEachIndexed { i, (n, th, fc) ->
                val r = RectF(10f + i * 492f, y, 10f + (i + 1) * 492f - 12f, y + rideH); panelCard(c, r, n)
                c.save(); c.clipRect(r)
                drawGirl(c, r.centerX() + 20f * fc, r.bottom - 140f, 2.7f, if (i == 3) MonkeyState.WALK else MonkeyState.IDLE, 0.8f + i * 0.3f, fc) {
                    it.rbThrust = th; if (i == 3) it.capeWind = 1f
                }
                c.restore()
            }
        }
        y += rideH + 60f

        // ---- 3. a swipe flight ----
        c.drawText("Swipe = launch along the swipe: ignite dip → thrust + gravity + gradual turn + wobble → sputter → glide down", 20f, y - 14f, title)
        run {
            val r = RectF(10f, y, W - 10f, y + flightH)
            c.drawRoundRect(r, 28f, 28f, Paint().apply { color = Color.rgb(0xE8, 0xF1, 0xFB) })
            val f = RocketFlight(11L)
            val s = 1.6f
            f.launch(r.left + 160f, r.bottom - 150f, 1f, -0.55f, 0.75f, s, r.left + 90f, r.right - 90f, r.top + 110f, r.bottom - 110f)
            val xs = ArrayList<Float>(); val ys = ArrayList<Float>(); val noses = ArrayList<Float>(); val thr = ArrayList<Float>(); val vxs = ArrayList<Float>(); val phases = ArrayList<String>()
            var n = 0
            while (f.active && n < 600) { f.step(1f / 60f); xs += f.x; ys += f.y; noses += f.noseDeg; thr += f.thrust; vxs += f.vx; phases += f.phase.name; n++ }
            // smoke / spark trail
            val smoke = Paint(Paint.ANTI_ALIAS_FLAG)
            for (i in xs.indices step 2) {
                val k = thr[i]
                smoke.color = if (k > 0.5f) Color.rgb(0xFF, 0xB3, 0x40) else Color.rgb(0x9A, 0xA4, 0xB4)
                smoke.alpha = (90 + 120 * k).toInt()
                c.drawCircle(xs[i], ys[i] + 30f, 3f + 5f * k, smoke)
            }
            val picks = listOf(4, xs.size / 6, xs.size / 3, xs.size / 2, (xs.size * 0.7f).toInt(), xs.size - 2).map { it.coerceIn(0, xs.size - 1) }
            for (i in picks) {
                val fc = if (vxs[i] >= 0f) 1f else -1f
                val tilt = RocketGeom.TILT + MonkeyView.ROCKET_FLY_LEAN
                val base = if (fc >= 0f) tilt else 180f - tilt
                var rot = noses[i] - base; while (rot > 180f) rot -= 360f; while (rot < -180f) rot += 360f
                rot = rot.coerceIn(-80f, 80f)
                c.save(); c.rotate(rot, xs[i], ys[i])
                drawGirl(c, xs[i], ys[i] + 46f * s, s, MonkeyState.IDLE, 0.4f + i * 0.01f, fc) { p ->
                    p.rbThrust = thr[i]; p.capeWind = 1f; p.shadow = 0f; p.bodyRot = MonkeyView.ROCKET_FLY_LEAN
                    p.legLSwing = 26f + 10f * sin(i * 0.12f); p.legRSwing = 16f + 10f * sin(i * 0.12f + 1.3f)
                    p.armLRaise = 0f; p.armRRaise = 0f; p.mouthSmile = 0.9f; p.mouthOpen = 0.35f
                }
                c.restore()
                c.drawText(phases[i].lowercase(), xs[i], ys[i] + 120f, label)
            }
        }
        y += flightH + 60f

        // ---- 4. the bottle launch ----
        c.drawText("Sky show: she stands a rocket in a green glass bottle, lights the fuse, and 5+ shells whistle up (wobbly)", 20f, y - 14f, title)
        run {
            val times = listOf(0.55f to "Lights the fuse", 1.15f to "Lift-off (whistle)", 1.9f to "Shells climbing", 3.4f to "5 shells, all different")
            val pw = (W - 20f) / 4f
            times.forEachIndexed { i, (tt, n) ->
                val r = RectF(10f + i * pw, y, 10f + (i + 1) * pw - 10f, y + bottleH)
                c.save(); c.clipRect(r); navy(c, r)
                c.drawRect(r.left, r.bottom - 70f, r.right, r.bottom, Paint().apply { color = Color.rgb(0x1E, 0x1A, 0x22) })
                c.translate(r.left, r.top)
                val s = 1.9f
                val gx = 150f; val gy = bottleH - 70f
                val fw = Fireworks(77L)
                val show = SkyShow(5L, fw)
                // place the bottle like the overlay: under her reaching right hand
                var rig = drawGirl(Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)), gx, gy, s) { it.rgReach = 1f }
                val pt = floatArrayOf(com.combo.monkeycompanion.rig.RocketGirlRenderer.FUSE_X, com.combo.monkeycompanion.rig.RocketGirlRenderer.FUSE_Y); rig.hips.mapPoints(pt)
                val drop = ((DiwaliRocketArt.TUBE_B - 3f + SkyShow.STICK_IN) * SkyShow.ROCKET_K + SkyShow.BOTTLE_H) * s
                show.start(pt[0], minOf(pt[1] + drop, gy), s, 1f, 20f, bottleH * 0.62f, pw - 10f, finaleChance = 0f)
                var t = 0f
                while (t < tt) { show.step(1f / 60f); fw.step(1f / 60f); t += 1f / 60f }
                rig = drawGirl(c, gx, gy, s, MonkeyState.IDLE, 1f) { p -> p.rgReach = show.reach; if (show.lookUp > 0f) { p.lookY -= 4f; p.mouthOpen = 0.35f; p.mouthSmile = 0.9f } }
                show.draw(c); fw.draw(c)
                c.restore()
                c.drawText(n, r.centerX(), r.bottom - 22f, white)
            }
        }
        y += bottleH + 60f

        // ---- 5. multi-ring fireworks ----
        c.drawText("Layered multi-ring bursts (58 recipes: rings, crackle core, willow, crossette, strobe, re-bursts)", 20f, y - 14f, title)
        run {
            val r = RectF(10f, y, W - 10f, y + fwH); navy(c, r)
            c.save(); c.clipRect(r)
            val fw = Fireworks(109L)
            val names = ArrayList<String>()
            val xs = listOf(0.14f, 0.38f, 0.62f, 0.86f); val hs = listOf(0.40f, 0.33f, 0.45f, 0.36f)
            for (k in 0 until 4) {
                val sp = fw.burstAt(r.left + r.width() * xs[k], y + fwH * hs[k], 2.3f)
                names += sp.recipe?.name ?: sp.shape.name
                repeat(4) { fw.step(1f / 60f) }
            }
            repeat(22) { fw.step(1f / 60f) }
            fw.draw(c)
            c.restore()
            names.forEachIndexed { k, n -> c.drawText(n, r.left + r.width() * xs[k], r.bottom - 22f, white) }
        }
        y += fwH + 60f

        // ---- 6. the floating chat button (the real overlay view) ----
        c.drawText("Floating 💬 chat button: follows her with a spring, flips sides at the edges, fades when idle (real overlay)", 20f, y - 14f, title)
        run {
            val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
            ChatHub.init(ctx)
            ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
            ChatHub.settings.gags = false; ChatHub.settings.chatButton = true; ChatHub.settings.doubleTapPower = true
            val shots = listOf(Triple("rocketgirl", 0.45f, 60), Triple("monkey", 0.9f, 60), Triple("billa", 0.1f, 60), Triple("rocketgirl", 0.5f, 60 * 7))
            val sw = (W - 20f) / 4f
            shots.forEachIndexed { i, (id, frac, frames) ->
                CharacterRegistry.select(id)
                val v = MonkeyView(ctx, LandingE2ETest.Host())
                v.layout(0, 0, 1080, 2340)
                v.debugFrames(60); v.debugSurf("FLOOR", frac = frac); v.debugQuiet = true
                v.debugFrames(frames)
                val full = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
                val fc = Canvas(full); fc.drawColor(Color.rgb(0xDD, 0xE6, 0xF0)); v.draw(fc)
                val w = v.debugWin(); val b = v.debugBox()
                val cx = (b.centerX() - w[0]).toInt(); val cy = (b.centerY() - w[1]).toInt()
                val half = 230
                val src = Rect((cx - half).coerceIn(0, 1080 - 2 * half), (cy - half).coerceIn(0, 2340 - 2 * half), 0, 0)
                src.right = src.left + 2 * half; src.bottom = src.top + 2 * half
                val r = RectF(10f + i * sw, y, 10f + (i + 1) * sw - 10f, y + btnH - 60f)
                c.drawBitmap(full, src, RectF(r.centerX() - (btnH - 70f) / 2f, r.top, r.centerX() + (btnH - 70f) / 2f, r.bottom - 10f), null)
                val txt = when (i) { 0 -> "Beside her, never on the face"; 1 -> "Right edge: flips to the left"; 2 -> "Left edge: stays inside"; else -> "Idle 7 s: faded to 30%" }
                c.drawText(txt + if (v.debugChatFabVisible) "" else " (hidden)", r.centerX(), y + btnH - 30f, label)
            }
        }
        y += btnH + 60f

        // ---- 7. reference side-by-side ----
        c.drawText("Style check against the references (left: reference photo, right: drawn by the app's code)", 20f, y - 14f, title)
        run {
            val refDir = listOf(File("/workspace/settai-refs"), TestPaths.root("../settai-refs")).firstOrNull { it.isDirectory }
            val rocketRef = refDir?.let { BitmapFactory.decodeFile(File(it, "rocket-ref.jpg").path) }
            val fwRef = refDir?.let { BitmapFactory.decodeFile(File(it, "fireworks-ref.png").path) }
            // rocket ref | our rocket (big, 3/4 vertical) | our bottle + rocket
            val rr = RectF(10f, y, 10f + refH * 0.75f, y + refH)
            if (rocketRef != null) c.drawBitmap(rocketRef, null, rr, null) else c.drawText("(rocket-ref.jpg missing)", rr.centerX(), rr.centerY(), label)
            val mine = RectF(rr.right + 10f, y, rr.right + 10f + 300f, y + refH); navy(c, mine)
            c.save(); c.clipRect(mine); c.translate(mine.centerX(), mine.bottom - 70f); c.rotate(-78f); c.scale(4.4f, 4.4f)
            c.translate(-DiwaliRocketArt.STICK_BACK, 0f)
            DiwaliRocketArt.draw(c, 1.3f, 0f); c.restore()
            val bot = RectF(mine.right + 10f, y, mine.right + 10f + 300f, y + refH); navy(c, bot)
            c.save(); c.clipRect(bot); c.translate(bot.left, bot.top)
            val show = SkyShow(9L, Fireworks(9L)); show.start(150f, refH - 40f, 4.6f, 1f, 10f, 200f, 300f, finaleChance = 0f)
            repeat(30) { show.step(1f / 60f) }
            show.draw(c); c.restore()
            // fireworks ref | ours
            val fr = RectF(bot.right + 20f, y, bot.right + 20f + 520f, y + 347f)
            if (fwRef != null) c.drawBitmap(fwRef, null, fr, null) else c.drawText("(fireworks-ref missing)", fr.centerX(), fr.centerY(), label)
            val ours = RectF(fr.left, fr.bottom + 16f, W - 10f, y + refH); navy(c, ours)
            c.save(); c.clipRect(ours)
            val fw = Fireworks(2026L)
            // many at once + rising glitter trails, like the photo
            for (k in 0 until 6) { fw.burstAt(ours.left + ours.width() * (0.1f + 0.16f * k), ours.top + ours.height() * (0.3f + 0.12f * (k % 3)), 1.5f); repeat(5) { fw.step(1f / 60f) } }
            for (k in 0 until 3) fw.launchShell(fw.nextSpec(ours.left + ours.width() * (0.2f + 0.3f * k), ours.top + 40f, 1.5f), ours.left + ours.width() * (0.2f + 0.3f * k), ours.bottom, 1.5f)
            repeat(16) { fw.step(1f / 60f) }
            fw.draw(c); c.restore()
            val fr2 = RectF(fr.right + 20f, y, W - 10f, y + 347f)
            c.drawRoundRect(fr2, 20f, 20f, card)
            val notes = listOf("Matched to the photos:", "• gold tube ≈4.5× its width", "• green/red bands, stars,", "  round emblem", "• red foil cone + fuse", "• twisted stick ≈2.7× tube", "• green glass bottle,", "  stick down the neck", "• navy sky, tapered streaks", "  bright at the tips", "• dotted rising trails,", "  twinkles, willows, rings")
            val lp = Paint(label).apply { textAlign = Paint.Align.LEFT; textSize = 21f }
            val avail = fr2.width() - 30f
            while (lp.textSize > 10f && notes.any { lp.measureText(it) > avail }) lp.textSize -= 0.5f
            val step = ((fr2.height() - 30f) / notes.size).coerceAtMost(lp.textSize * 1.45f)
            notes.forEachIndexed { k, t -> c.drawText(t, fr2.left + 14f, fr2.top + 14f + step * (k + 1) - 4f, lp) }
        }

        TestPaths.out("settai-v10.9-preview.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
