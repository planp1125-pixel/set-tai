package com.combo.monkeycompanion.smash

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.TestPaths
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.character.CharacterRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v10.5/v10.6: renders the real game view (cartoon screen) mid-game for a few characters -> build/test-out/settai-v10.6-smash.png. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class SmashPreviewTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun view(id: String): SmashView {
        ChatHub.init(ctx)
        val shot = SmashShot.fakeScreen(1080, 2340, 2.625f)
        val v = SmashView(ctx, shot, CharacterRegistry.byId(id)!!) {}
        v.autoStep = false
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1080, 2340)
        return v
    }
    private fun frames(v: SmashView, n: Int) = repeat(n) { v.step(1f / 60f) }
    private fun snap(v: SmashView): Bitmap { val b = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888); v.draw(Canvas(b)); return b }

    @Test fun renderSmash() {
        val shots = ArrayList<Pair<String, Bitmap>>()
        val sb = view("superboy")
        shots += "Start: your screen (pretend copy)" to snap(sb)
        val g = sb.debugGame!!
        // Crack a few, shatter some.
        for (i in listOf(4, 5, 9, 10, 13)) { sb.debugTap(g.tileLeft(i) + g.tileW * 0.5f, g.tileTop(i) + g.tileH * 0.5f); frames(sb, 3) }
        for (i in listOf(5, 9)) { sb.debugTap(g.tileLeft(i) + g.tileW * 0.4f, g.tileTop(i) + g.tileH * 0.6f); frames(sb, 2) }
        sb.debugTap(g.tileLeft(17) + 60f, g.tileTop(17) + 80f)
        frames(sb, 4)
        shots += "Zoom: heat-vision laser" to snap(sb)

        val mm = view("mask"); val gm = mm.debugGame!!
        for (i in listOf(8, 9, 12)) { mm.debugTap(gm.tileLeft(i) + 100f, gm.tileTop(i) + 100f); frames(mm, 16) }
        for (i in listOf(8, 9)) { mm.debugTap(gm.tileLeft(i) + 120f, gm.tileTop(i) + 90f); frames(mm, 16) }
        mm.debugTap(gm.tileLeft(21) + 120f, gm.tileTop(21) + 90f); frames(mm, 8)
        shots += "Masko: juggling balls" to snap(mm)

        val gj = view("gajju"); val gg = gj.debugGame!!
        for (i in listOf(14, 15, 18)) { gj.debugTap(gg.tileLeft(i) + 100f, gg.tileTop(i) + 100f); frames(gj, 14) }
        gj.debugTap(gg.tileLeft(14) + 100f, gg.tileTop(14) + 120f); frames(gj, 14)
        gj.debugTap(gg.tileLeft(6) + 100f, gg.tileTop(6) + 120f); frames(gj, 6)
        shots += "Gajju: water cannon" to snap(gj)

        val mk = view("monkey"); val gk = mk.debugGame!!
        // A combo up to a mega smash
        for (i in 0 until SmashGame.MEGA_EVERY) { mk.debugTap(gk.tileLeft(i + 8) + 80f, gk.tileTop(i + 8) + 80f); frames(mk, 18) }
        mk.debugTap(gk.tileLeft(21) + 130f, gk.tileTop(21) + 130f); frames(mk, 22)
        shots += "Settai: stones, MEGA SMASH" to snap(mk)
        frames(mk, 100)
        shots += "v10.6: debris piles up on the floor" to snap(mk)
        frames(mk, 60 * 31)
        assertEquals(SmashGame.State.OVER, gk.state)
        shots += "Time up: score card" to snap(mk)

        val sw = 360; val sh = 780
        val out = Bitmap.createBitmap(sw * shots.size, sh + 60, Bitmap.Config.ARGB_8888)
        val oc = Canvas(out); oc.drawColor(Color.WHITE)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 21f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Color.rgb(0x30, 0x30, 0x40) }
        shots.forEachIndexed { i, (name, bmp) ->
            oc.drawBitmap(Bitmap.createScaledBitmap(bmp, sw - 8, sh, true), i * sw + 4f, 0f, null)
            oc.drawText(name, i * sw + sw / 2f, sh + 38f, label)
        }
        TestPaths.out("settai-v10.6-smash.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        TestPaths.out("settai-v10.6-smash-big.png").outputStream().use { shots[1].second.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
