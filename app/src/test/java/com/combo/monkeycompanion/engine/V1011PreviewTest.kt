package com.combo.monkeycompanion.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.TestPaths
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.landing.LandingE2ETest
import com.combo.monkeycompanion.mind.MindHub
import com.combo.monkeycompanion.overlay.MonkeyView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v10.11 preview from the real overlay -> build/test-out/settai-v10.11-preview.png: the chat button faint right after a
 * move, dark only while pressed, and where it sits beside every character (low, by the feet, away from the face).
 * Set env SETTAI_FACEBOXES=1 to also outline the head box it keeps clear of (for checking, not for the release image).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class V1011PreviewTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val W = 1980
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x2C, 0x3A, 0x5A); textSize = 24f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val small = Paint(label).apply { textSize = 20f; typeface = Typeface.DEFAULT; color = Color.rgb(0x55, 0x60, 0x78) }
    private val title = Paint(label).apply { textSize = 34f; textAlign = Paint.Align.LEFT; color = Color.rgb(0xB0, 0x3A, 0x1E) }
    private val boxes = System.getenv("SETTAI_FACEBOXES") == "1"
    private var clock = 100_000L

    private fun view(id: String, frac: Float, mode: String = "SOLO"): MonkeyView {
        ChatHub.init(ctx); MindHub.init(ctx); MindHub.campfire.stop(); MindHub.neighbourMode = mode
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.gags = false; ChatHub.settings.chatButton = true; ChatHub.settings.doubleTapPower = true; ChatHub.settings.batterySaver = false
        CharacterRegistry.select(id)
        val v = MonkeyView(ctx, LandingE2ETest.Host())
        v.debugNoNightCampfire = true
        v.layout(0, 0, 1080, 2340)
        v.debugFrames(60); v.debugSurf("FLOOR", frac = frac); v.debugQuiet = true
        v.debugFrames(10)
        return v
    }

    private fun send(v: MonkeyView, a: Int, rx: Float, ry: Float) {
        val w = v.debugWin()
        val e = MotionEvent.obtain(clock, clock, a, rx, ry, 0)
        e.offsetLocation(-w[0].toFloat(), -w[1].toFloat()); v.dispatchTouchEvent(e); e.recycle(); clock += 16
    }

    private fun press(v: MonkeyView) {
        v.draw(Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)))
        val p = FloatArray(2); v.debugChatFab(p); val w = v.debugWin()
        send(v, MotionEvent.ACTION_DOWN, p[0] + w[0], p[1] + w[1]); v.debugFrames(2)
    }

    /** Draw the real overlay and paste a square crop around the character into [dst]. */
    private fun shot(c: Canvas, v: MonkeyView, dst: RectF, bg: Int, half: Int = 200, name: String, note: String) {
        val full = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        val fc = Canvas(full); fc.drawColor(bg); v.draw(fc)
        val w = v.debugWin(); val b = v.debugBox()
        if (boxes) {
            val f = RectF(); v.debugFaceRect(f)
            fc.drawRect(f, Paint().apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.GREEN })
        }
        val cx = (b.centerX() - w[0]).toInt(); val cy = (b.centerY() - w[1]).toInt()
        val l = (cx - half).coerceIn(0, 1080 - 2 * half); val t = (cy - half).coerceIn(0, 2340 - 2 * half)
        val size = dst.height() - 70f
        val img = RectF(dst.centerX() - size / 2f, dst.top, dst.centerX() + size / 2f, dst.top + size)
        c.drawRoundRect(RectF(img.left - 4f, img.top - 4f, img.right + 4f, img.bottom + 4f), 14f, 14f, Paint().apply { color = Color.rgb(0xC9, 0xD3, 0xE0) })
        c.drawBitmap(full, Rect(l, t, l + 2 * half, t + 2 * half), img, null)
        c.drawText(name, dst.centerX(), dst.bottom - 38f, label)
        c.drawText(note, dst.centerX(), dst.bottom - 10f, small)
    }

    @Test fun renderPreview() {
        val rowH = 520f
        val H = (90 + 3 * (rowH + 70) + 20).toInt()
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.rgb(0xFF, 0xF8, 0xEE))
        c.drawText("Settai v10.11 - the chat button: always faint, dark only under your finger, never over the face", 20f, 48f, title.apply { textSize = 38f })
        title.textSize = 30f
        val light = Color.rgb(0xDD, 0xE6, 0xF0)
        var y = 120f

        // ---- A. faint right after moving ----
        c.drawText("1. Right after she moves: still faint (18%)", 20f, y, title); y += 20f
        run {
            val n = 5; val pw = (W - 20f) / n
            val list = listOf<Triple<String, String, (MonkeyView) -> Unit>>(
                Triple("rocketgirl", "Just landed after flying") { v -> v.debugRocketLaunch(900f, -1500f); repeat(420) { v.debugFrames(1) } },
                Triple("monkey", "Just walked") { v -> v.debugWalk(0.2f); repeat(150) { v.debugFrames(1) } },
                Triple("superboy", "Just dropped from a drag") { v ->
                    val b = v.debugBox(); send(v, MotionEvent.ACTION_DOWN, b.centerX(), b.top + b.height() * 0.6f)
                    for (i in 1..10) { send(v, MotionEvent.ACTION_MOVE, b.centerX() + i * 8f, b.top + b.height() * 0.6f - i * 40f); v.debugFrames(1) }
                    clock += 400; send(v, MotionEvent.ACTION_UP, b.centerX() + 80f, b.top + b.height() * 0.6f - 400f); repeat(150) { v.debugFrames(1) }
                },
                Triple("rocketgirl", "Just tapped") { v -> val b = v.debugBox(); send(v, MotionEvent.ACTION_DOWN, b.centerX(), b.top + b.height() * 0.6f); send(v, MotionEvent.ACTION_UP, b.centerX(), b.top + b.height() * 0.6f); v.debugFrames(3) },
                Triple("rocketgirl", "Flying right now") { v -> v.debugRocketLaunch(-700f, -1500f); repeat(40) { v.debugFrames(1) } },
            )
            list.forEachIndexed { i, (id, what, act) ->
                val v = view(id, 0.5f); act(v)
                shot(c, v, RectF(10f + i * pw, y, 10f + (i + 1) * pw - 12f, y + rowH), light, name = what, note = "alpha ${"%.2f".format(v.debugChatFabAlpha)}${if (v.debugChatFabVisible) "" else " (hidden in flight)"}")
            }
        }
        y += rowH + 70f

        // ---- B. pressed ----
        c.drawText("2. Finger on the button: dark + solid (chat opens when you lift)  vs  not touched: faint", 20f, y, title); y += 20f
        run {
            val n = 4; val pw = (W - 20f) / n
            val list = listOf("rocketgirl" to true, "rocketgirl" to false, "monkey" to true, "gajju" to true)
            list.forEachIndexed { i, (id, pressed) ->
                val v = view(id, 0.5f)
                if (pressed) press(v) else v.debugFrames(5)
                shot(c, v, RectF(10f + i * pw, y, 10f + (i + 1) * pw - 12f, y + rowH), light,
                    name = if (pressed) "Pressed" else "Not touched", note = "alpha ${"%.2f".format(v.debugChatFabAlpha)}")
            }
        }
        y += rowH + 70f

        // ---- C. positions ----
        c.drawText("3. Where it sits: low beside the feet, away from the face, fully on screen even at the edges", 20f, y, title); y += 20f
        run {
            val list = listOf<Triple<String, Float, String>>(
                Triple("rocketgirl", 0f, "Skye, left edge"), Triple("rocketgirl", 1f, "Skye, right edge"),
                Triple("billa", 0.5f, "Billa"), Triple("chittu", 0.5f, "Chittu"), Triple("mask", 0.5f, "Masko"),
                Triple("armour", 0.5f, "Bolt"), Triple("monkey", 0.15f, "Settai + Duo buddy"),
            )
            val n = list.size; val pw = (W - 20f) / n
            list.forEachIndexed { i, (id, frac, what) ->
                val duo = what.contains("Duo")
                val v = view(if (CharacterRegistry.byId(id) != null) id else "monkey", frac, if (duo) "DUO" else "SOLO")
                if (duo) {
                    v.startNeighbourBanter(com.combo.monkeycompanion.world.Director.BanterScene(com.combo.monkeycompanion.world.Director.Kind.PLACE_GOSSIP, "monkey", "mask", "Machan, tea?", "Always!"))
                    v.debugFrames(200)
                } else { v.debugPlay(MonkeyState.WAVE, 2f); v.debugFrames(20) }
                shot(c, v, RectF(10f + i * pw, y, 10f + (i + 1) * pw - 12f, y + rowH), light, half = if (duo) 260 else 200, name = what, note = "face clear")
                MindHub.campfire.stop()
            }
        }
        MindHub.neighbourMode = "DUO"
        TestPaths.out("settai-v10.11-preview.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
