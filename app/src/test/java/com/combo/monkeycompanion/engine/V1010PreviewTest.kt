package com.combo.monkeycompanion.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.TestPaths
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.engine.anim.AnimEngine
import com.combo.monkeycompanion.landing.LandingE2ETest
import com.combo.monkeycompanion.overlay.MonkeyView
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.DiwaliRocketArt
import com.combo.monkeycompanion.rig.Fireworks
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.rig.SkyShow
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v10.10 preview from the real code -> build/test-out/settai-v10.10-preview.png: Skye riding ASTRIDE her rocket
 * (side and 3/4 close-ups), the new spark-fountain exhaust (idle / swiped / the bottle lift-off / a real overlay flight),
 * and the chat button idle vs pressed (real overlay).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class V1010PreviewTest {
    private val W = 1980
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x2C, 0x3A, 0x5A); textSize = 24f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val white = Paint(label).apply { color = Color.WHITE }
    private val title = Paint(label).apply { textSize = 34f; textAlign = Paint.Align.LEFT; color = Color.rgb(0xB0, 0x3A, 0x1E) }
    private val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xF4, 0xE2) }
    private val girl = CharacterRegistry.byId("rocketgirl")!!

    private fun navy(c: Canvas, r: RectF) {
        val p = Paint().apply { shader = LinearGradient(0f, r.top, 0f, r.bottom, Color.rgb(0x06, 0x0B, 0x24), Color.rgb(0x16, 0x22, 0x4E), Shader.TileMode.CLAMP) }
        c.drawRoundRect(r, 20f, 20f, p)
    }

    private fun drawGirl(c: Canvas, x: Float, feetY: Float, s: Float, state: MonkeyState = MonkeyState.IDLE, st: Float = 1.2f,
                         facing: Float = 1f, t: Float = 2.7f, tweak: (Pose) -> Unit = {}): MonkeyRig {
        val wear = CharacterRegistry.wear(girl)
        val r = girl.newRenderer().also { it.wear = wear }
        val rig = MonkeyRig(); val p = Pose(); val e = AnimEngine(); e.setTuning(CharacterProfiles.of(girl).anim); r.anim = e
        val dt = 1f / 60f
        for (i in 0 until 40) { girl.fill(p, state, 1f + st + i * dt, st + i * dt, AnimContext(), wear); e.step(p, dt, 0f, 0f, 1f, 1f, false) }
        p.glint = 0.8f
        tweak(p)
        rig.layout(p, x, feetY, s, facing)
        r.draw(c, rig, p, t)
        return rig
    }

    private fun panel(c: Canvas, r: RectF, text: String, dark: Boolean = false) {
        if (dark) navy(c, r) else c.drawRoundRect(r, 28f, 28f, card)
        c.drawText(text, r.centerX(), r.bottom - 18f, if (dark) white else label)
    }

    @Test fun renderV1010() {
        val rowA = 760; val rowB = 560; val rowC = 600; val rowD = 520
        val H = 80 + rowA + 60 + rowB + 60 + rowC + 60 + rowD + 30
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.WHITE)
        c.drawText("Settai v10.10: Skye rides ASTRIDE her rocket, sparkle exhaust (no blast), chat button idle vs pressed", 20f, 50f, title)
        var y = 80f

        // ---- A. riding close-ups: side + 3/4 + thumbnail ----
        run {
            val r0 = RectF(10f, y, 800f, y + rowA); panel(c, r0, "Side: sits on the stick, legs astride, leans in, both hands on the tube")
            c.save(); c.clipRect(r0); drawGirl(c, r0.centerX() - 10f, r0.bottom - 175f, 4.3f, t = 3.1f) { it.rbThrust = 1f; it.capeWind = 1f }; c.restore()
            val r1 = RectF(820f, y, 1610f, y + rowA); panel(c, r1, "3/4 (hovering): far leg tucked behind the rocket, dress draped over it")
            c.save(); c.clipRect(r1); drawGirl(c, r1.centerX() - 10f, r1.bottom - 175f, 4.3f, t = 2.2f) { it.rbThrust = 0f }; c.restore()
            // picker thumbnail (the CharacterPreviewView maths)
            val r2 = RectF(1630f, y, W - 10f, y + rowA); panel(c, r2, "Picker thumbnail")
            val th = rowA - 80f; val tw = r2.width()
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
            c.save(); c.translate(r2.left, y); c.clipRect(0f, 0f, tw, th); rr.draw(c, rig, p, 1f); c.restore()
        }
        y += rowA + 60f

        // ---- B. the ride in motion ----
        c.drawText("Riding like a broomstick: hovering, swiped (more sparks), facing left, waving, lighting a fuse", 20f, y - 14f, title)
        run {
            val specs = listOf("Hover", "Swiped: full sparks", "Facing left", "Waving", "Lighting the fuse")
            val pw = (W - 20f) / specs.size
            specs.forEachIndexed { i, n ->
                val r = RectF(10f + i * pw, y, 10f + (i + 1) * pw - 12f, y + rowB); panel(c, r, n)
                c.save(); c.clipRect(r)
                val st = when (i) { 3 -> MonkeyState.WAVE; else -> MonkeyState.IDLE }
                drawGirl(c, r.centerX() + (if (i == 2) 20f else -20f), r.bottom - 150f, 3.0f, st, 0.8f + i * 0.3f, if (i == 2) -1f else 1f, t = 2f + i * 0.37f) {
                    it.rbThrust = if (i == 1) 1f else if (i == 2) 0.5f else 0f; if (i == 1) it.capeWind = 1f; if (i == 4) it.rgReach = 1f
                }
                c.restore()
            }
        }
        y += rowB + 60f

        // ---- C. the exhaust: a spark fountain, not a blast ----
        c.drawText("Exhaust = a real Diwali rocket: a narrow fountain of tiny falling sparks, a thin smoke wisp, a small nozzle glow", 20f, y - 14f, title)
        run {
            val pw = (W - 20f) / 4f
            // 1-2: the rocket art itself, idle and swiped (nozzle in the middle of the panel)
            for (i in 0 until 2) {
                val r = RectF(10f + i * pw, y, 10f + (i + 1) * pw - 12f, y + rowC); panel(c, r, if (i == 0) "Idle (k = 0.35)" else "Swiped (k = 1.2): denser, longer", dark = true)
                c.save(); c.clipRect(r); c.translate(r.left + r.width() * 0.5f, r.top + rowC * 0.42f); c.rotate(-38f); c.scale(5.5f, 5.5f)
                c.translate(-DiwaliRocketArt.TUBE_B, 0f)
                val ga = Math.toRadians(-38.0)
                DiwaliRocketArt.draw(c, 1.37f, if (i == 0) 0.35f else 1.2f, stickBack = -20f, ow = 1.6f, gx = kotlin.math.sin(ga).toFloat(), gy = kotlin.math.cos(ga).toFloat())
                c.restore()
            }
            // 3: the bottle lift-off fountain
            run {
                val r = RectF(10f + 2 * pw, y, 10f + 3 * pw - 12f, y + rowC); panel(c, r, "Bottle lift-off: sparks spray + fall", dark = true)
                c.save(); c.clipRect(r); c.translate(r.left, r.top)
                val fw = Fireworks(31L); val show = SkyShow(3L, fw)
                show.start(r.width() / 2f, rowC - 70f, 3.6f, 1f, 10f, 200f, r.width(), finaleChance = 0f)
                var t = 0f
                while (t < SkyShow.LIGHT_END + 0.12f) { show.step(1f / 60f); fw.step(1f / 60f); t += 1f / 60f }
                show.draw(c); fw.draw(c); c.restore()
            }
            // 4: riding at full thrust against the night sky
            run {
                val r = RectF(10f + 3 * pw, y, W - 10f, y + rowC); panel(c, r, "Swiped ride at night: sparks, no blast", dark = true)
                c.save(); c.clipRect(r)
                drawGirl(c, r.centerX() + 10f, r.bottom - 150f, 2.5f, t = 4.05f) { it.rbThrust = 1.2f; it.capeWind = 1f }
                c.restore()
            }
        }
        y += rowC + 60f

        // ---- D. chat button idle vs pressed (real overlay) ----
        c.drawText("Chat button (real overlay): faint when idle (18%), dark + solid while your finger is on it, chat opens on release", 20f, y - 14f, title)
        run {
            val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
            val shots = listOf("rocketgirl" to "Just moved (v10.11: faint)", "rocketgirl" to "Idle 7 s: 18%", "rocketgirl" to "Pressed: dark + solid", "monkey" to "Pressed (Settai)")
            val pw = (W - 20f) / shots.size
            shots.forEachIndexed { i, (id, n) ->
                ChatHub.init(ctx); ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
                ChatHub.settings.gags = false; ChatHub.settings.chatButton = true; ChatHub.settings.doubleTapPower = true
                CharacterRegistry.select(id)
                val v = MonkeyView(ctx, LandingE2ETest.Host())
                v.layout(0, 0, 1080, 2340)
                v.debugFrames(60); v.debugSurf("FLOOR", frac = 0.45f); v.debugQuiet = true
                v.debugFrames(if (i == 0) 30 else 60 * 7)
                if (i >= 2) {
                    v.draw(Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)))
                    val pt = FloatArray(2); v.debugChatFab(pt); val w = v.debugWin()
                    val e = MotionEvent.obtain(1000L, 1000L, MotionEvent.ACTION_DOWN, pt[0] + w[0], pt[1] + w[1], 0)
                    e.offsetLocation(-w[0].toFloat(), -w[1].toFloat()); v.dispatchTouchEvent(e); e.recycle()
                    v.debugFrames(2)
                }
                val full = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
                val fc = Canvas(full); fc.drawColor(Color.rgb(0xDD, 0xE6, 0xF0)); v.draw(fc)
                val w = v.debugWin(); val b = v.debugBox()
                val cx = (b.centerX() - w[0]).toInt(); val cy = (b.centerY() - w[1]).toInt()
                val half = 220
                val src = Rect((cx - half).coerceIn(0, 1080 - 2 * half), (cy - half).coerceIn(0, 2340 - 2 * half), 0, 0)
                src.right = src.left + 2 * half; src.bottom = src.top + 2 * half
                val r = RectF(10f + i * pw, y, 10f + (i + 1) * pw - 12f, y + rowD - 60f)
                c.drawBitmap(full, src, RectF(r.centerX() - (rowD - 70f) / 2f, r.top, r.centerX() + (rowD - 70f) / 2f, r.bottom - 10f), null)
                c.drawText(n + "  (alpha ${"%.2f".format(v.debugChatFabAlpha)})", r.centerX(), y + rowD - 30f, label)
            }
        }

        TestPaths.out("settai-v10.10-preview.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
