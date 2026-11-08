package com.combo.monkeycompanion.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.TestPaths
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.TouchZone
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.character.Wear
import com.combo.monkeycompanion.engine.anim.AnimEngine
import com.combo.monkeycompanion.engine.touch.BodyPart
import com.combo.monkeycompanion.engine.touch.ReactionInstance
import com.combo.monkeycompanion.engine.touch.ReactionLayer
import com.combo.monkeycompanion.engine.touch.TouchKind
import com.combo.monkeycompanion.engine.touch.TouchTracker
import com.combo.monkeycompanion.engine.touch.Verb
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.Fireworks
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.smash.SmashShot
import com.combo.monkeycompanion.smash.SmashView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v10.8 preview sheet from the real code -> build/test-out/settai-v10.8-preview.png:
 * Skye's poses and touch reactions, living reactions on the others, every character's picker thumbnail
 * (head checked inside the card), 4 different sky-burst fireworks, and Skye in the Smash game.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class V108PreviewTest {
    private val cw = 330; private val ch = 380
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x2C, 0x3A, 0x5A); textSize = 21f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val title = Paint(label).apply { textSize = 34f; textAlign = Paint.Align.LEFT; color = Color.rgb(0xB0, 0x3A, 0x1E) }
    private val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xF4, 0xE2) }

    private fun reaction(id: String, verb: Verb, part: BodyPart, kind: TouchKind = TouchKind.TAP): ReactionInstance {
        val e = CharacterProfiles.of(CharacterRegistry.byId(id)!!).reactionTable.first { it.verb == verb }
        val inp = TouchTracker().build(part, TouchZone.HEAD, 1, kind, 0f, 1f, 0f, 0.6f, 0.1f, 1f)
        return ReactionInstance(e, (e.amp.start + e.amp.endInclusive) / 2f, (e.dur.start + e.dur.endInclusive) / 2f, 1f, 3, inp, false)
    }

    private fun panel(c: Canvas, col: Int, row: Int, y0: Float, id: String, text: String, state: MonkeyState, st: Float,
                      verb: Pair<Verb, BodyPart>? = null, at: Float = 0.45f, kind: TouchKind = TouchKind.TAP, wear: String? = null,
                      festive: Boolean = false, lean: Float = 0f) {
        val who = CharacterRegistry.byId(id)!!
        val x0 = col * cw.toFloat(); val yy = y0 + row * ch
        c.drawRoundRect(RectF(x0 + 8, yy + 8, x0 + cw - 8, yy + ch - 8), 30f, 30f, card)
        val w = Wear(who); if (wear != null) { w.allOff(); w.decode(wear) }
        val r = who.newRenderer().also { it.wear = w }
        val rig = MonkeyRig(); val p = Pose(); val e = AnimEngine(); e.setTuning(CharacterProfiles.of(who).anim)
        r.anim = e
        val layer = ReactionLayer()
        val inst = verb?.let { reaction(id, it.first, it.second, kind) }
        if (inst != null) layer.play(inst)
        val dt = 1f / 60f
        val frames = if (inst != null) (inst.dur * at / dt).toInt().coerceAtLeast(1) else 30
        val t0 = 1f + st
        for (i in 0 until frames) {
            who.fill(p, state, t0 + i * dt, st + i * dt, AnimContext(), w)
            e.step(p, dt, 0f, 0f, 1f, 1f, false)
            if (inst != null) { layer.step(dt); layer.apply(p, 1f, e) }
        }
        p.glint = 0.8f
        if (festive) p.festive = 1f
        p.bodyRot += lean
        rig.layout(p, x0 + cw / 2f, yy + ch - 70f, 1.55f * CharacterProfiles.of(who).drawScale, 1f)
        r.draw(c, rig, p, t0 + frames * dt)
        c.drawText(text, x0 + cw / 2f, yy + ch - 26f, label)
    }

    @Test fun renderV108() {
        val cols = 6
        val W = cols * cw; val rowsTop = 3
        val fwH = 520; val thumbH = 300; val smashH = 820
        val H = 90 + rowsTop * ch + 60 + thumbH + 60 + fwH + 60 + smashH + 20
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.WHITE)
        c.drawText("Settai v10.8: Skye, living reactions, Disney part 2", 20f, 46f, title)
        val y0 = 90f
        // Row 0: Skye
        panel(c, 0, 0, y0, "rocketgirl", "Hover: bob + sparkle exhaust", MonkeyState.IDLE, 1.3f)
        panel(c, 1, 0, y0, "rocketgirl", "Flying: leans into the turn", MonkeyState.WALK, 0.6f, lean = 14f)
        panel(c, 2, 0, y0, "rocketgirl", "Tap head: giggle", MonkeyState.GIGGLE, 0.3f, Verb.GIGGLE to BodyPart.HEAD, 0.4f)
        panel(c, 3, 0, y0, "rocketgirl", "Tap rocket: sputter + spin", MonkeyState.STARTLED, 0.2f, Verb.ROCKET_SPUTTER to BodyPart.PROP, 0.5f)
        panel(c, 4, 0, y0, "rocketgirl", "Flick: loop-de-loop", MonkeyState.IDLE, 0.4f, Verb.LOOP to BodyPart.BELLY, 0.42f, TouchKind.FLICK)
        panel(c, 5, 0, y0, "rocketgirl", "Hold: lights a phuljhadi", MonkeyState.HAPPY_PAT, 0.5f, Verb.SPARKLER to BodyPart.HEAD, 0.55f, TouchKind.HOLD)
        // Row 1: living reactions on the others
        panel(c, 0, 1, y0, "monkey", "Settai: tail swat", MonkeyState.IDLE, 0.5f, Verb.TAIL_SWAT to BodyPart.TAIL, 0.4f)
        panel(c, 1, 1, y0, "billa", "Billa: ear flick", MonkeyState.IDLE, 0.5f, Verb.EAR_FLICK to BodyPart.EAR, 0.35f)
        panel(c, 2, 1, y0, "gajju", "Gajju: trunk boop", MonkeyState.IDLE, 0.5f, Verb.TRUNK_BOOP to BodyPart.TRUNK, 0.4f)
        panel(c, 3, 1, y0, "chittu", "Chittu: dodge", MonkeyState.IDLE, 0.5f, Verb.DODGE to BodyPart.BELLY, 0.35f)
        panel(c, 4, 1, y0, "superboy", "Zoom: show-off flip", MonkeyState.IDLE, 0.5f, Verb.SHOW_OFF to BodyPart.HEAD, 0.5f)
        panel(c, 5, 1, y0, "armour", "Bolt: lean in (happy squint)", MonkeyState.HAPPY_PAT, 0.5f, Verb.LEAN_IN to BodyPart.HEAD, 0.5f)
        // Row 2: Diwali accents + more
        panel(c, 0, 2, y0, "monkey", "Diwali season accents", MonkeyState.IDLE, 0.9f, festive = true)
        panel(c, 1, 2, y0, "rocketgirl", "Skye at Diwali", MonkeyState.IDLE, 2.1f, festive = true)
        panel(c, 2, 2, y0, "mask", "Masko: spin look", MonkeyState.IDLE, 0.5f, Verb.SPIN_LOOK to BodyPart.HEAD, 0.5f)
        panel(c, 3, 2, y0, "rocketgirl", "Wardrobe: goggles off", MonkeyState.WAVE, 0.6f, wear = "")
        panel(c, 4, 2, y0, "rocketgirl", "Sleepy hover", MonkeyState.SLEEP, 2f)
        panel(c, 5, 2, y0, "gajju", "Gajju: tail curl", MonkeyState.IDLE, 0.5f, Verb.TAIL_CURL to BodyPart.TAIL, 0.5f)

        // Picker thumbnails: the CharacterPreviewView fitting code, checking every head is inside the card.
        var ty = y0 + rowsTop * ch + 50f
        c.drawText("Character picker thumbnails (head must be inside the card)", 20f, ty - 10f, title)
        val all = CharacterRegistry.all
        val tw = W / all.size.toFloat(); val th = thumbH - 20f
        all.forEachIndexed { i, who ->
            val x0 = i * tw
            val cardR = RectF(x0 + 6, ty, x0 + tw - 6, ty + th)
            c.drawRoundRect(cardR, 24f, 24f, card)
            val p = Pose(); val rig = MonkeyRig(); val wear = CharacterRegistry.wear(who)
            val r = who.newRenderer().also { it.wear = wear }
            who.fill(p, MonkeyState.IDLE, 1f, 1f, AnimContext(), wear)
            // same maths as ui/CharacterViews.CharacterPreviewView.onDraw (view height = th)
            var scale = th / 255f * CharacterProfiles.of(who).drawScale
            var ay = th * 0.90f
            rig.layout(p, tw / 2f, ay, scale, 1f)
            val ht = FloatArray(2); rig.headTop(ht)
            if (ht[1] < 8f) {
                val span = (ay - ht[1]).coerceAtLeast(10f); scale *= ((ay - 8f) / span).coerceIn(0.65f, 1f)
                rig.layout(p, tw / 2f, ay, scale, 1f); rig.headTop(ht)
                if (ht[1] < 8f) { ay += (8f - ht[1]); rig.layout(p, tw / 2f, ay, scale, 1f); rig.headTop(ht) }
            }
            assertTrue("${who.id} head inside thumbnail: ${ht[1]}", ht[1] >= 0f)
            c.save(); c.translate(x0, ty); c.clipRect(6f, 0f, tw - 6f, th); r.draw(c, rig, p, 1f); c.restore()
            c.drawText(who.name, x0 + tw / 2f, ty + th + 18f, label)
        }

        // Fireworks: 4 bursts, all different
        ty += thumbH + 60f
        c.drawText("Skye's sky-burst: a new firework every time", 20f, ty - 10f, title)
        val sky = Paint().apply { shader = LinearGradient(0f, ty, 0f, ty + fwH, Color.rgb(0x10, 0x0C, 0x2E), Color.rgb(0x2A, 0x16, 0x40), Shader.TileMode.CLAMP) }
        c.drawRect(0f, ty, W.toFloat(), ty + fwH, sky)
        val fw = Fireworks(108L)
        val names = ArrayList<String>()
        for (k in 0 until 4) {
            val sp = fw.burstAt(W * (0.14f + 0.24f * k), ty + fwH * 0.42f, 3.2f)
            names += "${sp.shape.name.lowercase()} · ${sp.palette.name.lowercase()}"
        }
        repeat(26) { fw.step(1f / 60f) }
        fw.draw(c)
        val lw = Paint(label).apply { color = Color.WHITE }
        names.forEachIndexed { k, n -> c.drawText(n, W * (0.14f + 0.24f * k), ty + fwH - 22f, lw) }
        assertTrue(fw.history.zipWithNext().all { (a, b) -> a.shape != b.shape && a.palette != b.palette })

        // Smash: Skye's rockets + the new cracked-wallpaper reveal
        ty += fwH + 60f
        c.drawText("Smash: Skye's firework rockets + the peeking-monkey wallpaper holes", 20f, ty - 10f, title)
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        ChatHub.init(ctx)
        val snaps = ArrayList<Bitmap>()
        for (id in listOf("rocketgirl", "monkey")) {
            val v = SmashView(ctx, SmashShot.fakeScreen(1080, 2340, 2.625f), CharacterRegistry.byId(id)!!) {}
            v.autoStep = false
            v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
            v.layout(0, 0, 1080, 2340)
            val g = v.debugGame!!
            for (i in listOf(7, 12, 13, 17)) { repeat(2) { v.debugTap(g.tileLeft(i) + g.tileW * 0.5f, g.tileTop(i) + g.tileH * 0.5f); repeat(20) { v.step(1f / 60f) } } }
            v.debugTap(g.tileLeft(9) + 100f, g.tileTop(9) + 100f); repeat(14) { v.step(1f / 60f) }
            val b = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888); v.draw(Canvas(b)); snaps += b
        }
        val sw = (smashH - 10) * 1080 / 2340
        snaps.forEachIndexed { i, b -> c.drawBitmap(Bitmap.createScaledBitmap(b, sw, smashH - 10, true), 20f + i * (sw + 30f), ty, null) }

        TestPaths.out("settai-v10.8-preview.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
