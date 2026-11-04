package com.combo.monkeycompanion.v1024

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.TestPaths
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.brain.*
import com.combo.monkeycompanion.character.*
import com.combo.monkeycompanion.engine.CharacterProfiles
import com.combo.monkeycompanion.engine.anim.AnimEngine
import com.combo.monkeycompanion.engine.swipe.SwipeTravel
import com.combo.monkeycompanion.overlay.MonkeyView
import com.combo.monkeycompanion.overlay.OverlayHost
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.rig.SwipeFx
import com.combo.monkeycompanion.world.Population
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class V1024SwipeTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    private class Host : OverlayHost {
        override fun quitSettai() {}
        override fun setTouchThrough(on: Boolean) {}
        override fun showFx(draw: ((Canvas) -> Unit)?): Boolean = true
        override fun invalidateFx() {}
        override fun screenWidth() = 1080
        override fun screenHeight() = 2340
        override fun placeWindow(x: Int, y: Int, w: Int, h: Int) {}
    }

    @After fun back() { CharacterRegistry.select("monkey") }

    private fun view(id: String): MonkeyView {
        ChatHub.init(ctx)
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.characterId = id; ChatHub.settings.applyCharacter()
        ChatHub.settings.batterySaver = false; ChatHub.settings.gags = false; ChatHub.settings.restSeconds = 600
        val v = MonkeyView(ctx, Host()); v.layout(0, 0, 1080, 2340)
        v.debugFrames(5)
        return v
    }

    @Test fun styles() {
        assertEquals(SwipeTravel.Style.SURF, SwipeTravel.styleFor("surfer", false))
        assertEquals(SwipeTravel.Style.SKATE, SwipeTravel.styleFor("skater", false))
        for (id in listOf("armour", "superboy", "rocketgirl")) assertNull(id, SwipeTravel.styleFor(id, false))
        assertEquals(SwipeTravel.Style.MAGIC_HOPS, SwipeTravel.styleFor("devil", false)); assertEquals(SwipeTravel.Style.MAGIC_HOPS, SwipeTravel.styleFor("angel", false))
        for (id in listOf("surfer", "gajju", "angel", "devil", "skater", "hollowman")) assertTrue(id, Landings.pool(id, false).none { it.first == Landings.Style.JETS || it.first == Landings.Style.SCREAM_JETS || it.first == Landings.Style.ROCKET_ARC })
        assertEquals(SwipeTravel.Style.RUN, SwipeTravel.styleFor("custom_x", true))
        val ids = listOf("monkey", "hollowman", "gajju", "mask", "billa", "chittu", "surfer", "skater").map { SwipeTravel.styleFor(it, false) }
        assertEquals(ids.size, ids.toSet().size)   // each one unique
    }

    @Test fun everyTripEndsExactlyAtTheTargetAndIsDeterministic() {
        for (st in SwipeTravel.Style.entries) for ((x0, x1) in listOf(100f to 800f, 800f to 60f)) {
            val tr = SwipeTravel.Trip(st, x0, 1500f, x1, 1900f, 2.6f)
            var n = 0; val f = SwipeTravel.Frame()
            while (!tr.done && n++ < 2000) { tr.step(1f / 60f); tr.frame(f) }
            assertTrue(tr.done); assertEquals(st.name, x1, f.x, 0.5f); assertEquals(st.name, 1900f, f.y, 0.5f)
            assertEquals(1f, f.scale, 0.02f); assertEquals(1f, f.alpha, 0.01f); assertEquals(0f, f.sink, 0.01f)
            val a = SwipeTravel.Trip(st, x0, 1500f, x1, 1900f, 2.6f).also { it.seek(0.4f) }.frame()
            val b = SwipeTravel.Trip(st, x0, 1500f, x1, 1900f, 2.6f).also { it.seek(0.4f) }.frame()
            assertEquals(a.x, b.x, 0f); assertEquals(a.y, b.y, 0f)
        }
        // surf show really has its parts: S-turns, a tube, an aerial and a cutback splash
        val tr = SwipeTravel.Trip(SwipeTravel.Style.SURF, 100f, 1500f, 800f, 1500f, 2.6f)
        fun at(k: Float) = tr.also { it.seek(k) }.frame()
        assertTrue(at(0.4f).curl > 0.9f); assertTrue(at(0.59f).y < 1500f - 100f); assertTrue(at(0.76f).splash > 0.9f)
        assertTrue(abs(at(0.083f).y - at(0.25f).y) > 20f)
    }

    @Test fun floorCharactersTravelSidewaysAndLandOnTheFloorWithoutSplat() {
        for (id in listOf("monkey", "hollowman", "billa", "chittu", "skater")) {
            val v = view(id)
            val xy0 = v.debugXY
            v.debugPlace(40f, onFloor = false, y = xy0[2] - 700f)   // high up: a sideways fling used to SPLAT on the wall
            v.debugRelease(2300f, -200f)
            assertFalse("$id splat", v.debugImpact)
            val trip = v.debugSwipeTrip
            assertNotNull("$id trip xy=${v.debugXY.toList()} hid=${v.debugHidden} imp=${v.debugImpact}", trip)
            val tx = trip!!.x1
            var n = 0
            while (v.debugSwipeTrip != null && n++ < 400) v.debugFrames(1)
            val xy = v.debugXY
            assertEquals(id, tx, xy[0], 1.5f)
            assertEquals("$id on floor", xy[2], xy[1], 1.5f)
            assertEquals(id, "FLOOR", v.debugSurfKind)
        }
    }

    @Test fun hoveringCharactersEndWhereSwiped() {
        for (id in listOf("surfer", "gajju")) {
            val v = view(id)
            v.debugPlace(40f, onFloor = false, y = 900f)
            v.debugRelease(2250f, -400f)
            val trip = v.debugSwipeTrip ?: error("$id no trip")
            var n = 0
            while (v.debugSwipeTrip != null && n++ < 400) v.debugFrames(1)
            assertEquals(id, trip.x1, v.debugXY[0], 1.5f); assertEquals(id, trip.y1, v.debugXY[1], 1.5f)
        }
    }

    @Test fun flingRules() {
        // aimed past the side edge = hide, not splat
        var v = view("billa"); v.debugPlace(600f, onFloor = true); v.debugRelease(4000f, 0f)
        assertFalse(v.debugImpact); assertNull(v.debugSwipeTrip); v.debugFrames(60); assertTrue("hidden", v.debugHidden)
        // released near the left edge, flung left = hide
        v = view("hollowman"); v.debugPlace(40f, onFloor = true); v.debugRelease(-2500f, 0f)
        assertFalse(v.debugImpact); v.debugFrames(60); assertTrue(v.debugHidden)
        // aimed above the top = thwack on the top
        v = view("mask"); v.debugPlace(400f, onFloor = true); v.debugRelease(0f, -6000f)
        assertTrue(v.debugImpact)
        // Mask Man's sheet trick reappears exactly at a HIGH target, then falls and lands
        v = view("mask"); v.debugPlace(200f, onFloor = true); val g = v.debugXY[2]; v.debugRelease(900f, -2500f)
        val trip = v.debugSwipeTrip ?: error("no mask trip")
        assertTrue("high target", trip.y1 < g - 300f)
        var n = 0; while (v.debugSwipeTrip != null && n++ < 400) v.debugFrames(1)
        assertEquals(trip.x1, v.debugXY[0], 1.5f); assertEquals(trip.y1, v.debugXY[1], 30f)
        n = 0; while (v.debugXY[1] < g - 2f && n++ < 600) v.debugFrames(1)
        assertEquals(g, v.debugXY[1], 3f)
    }

    @Test fun magicHopsAndSwipeDown() {
        for (id in listOf("devil", "angel")) {
            val v = view(id); v.debugPlace(40f, onFloor = false, y = 900f); v.debugRelease(2250f, -300f)
            val trip = v.debugSwipeTrip ?: error("$id no hop trip")
            assertEquals(SwipeTravel.Style.MAGIC_HOPS, trip.style); assertTrue(trip.hops in 3..5)
            var puffs = 0; var n = 0; var was = false
            while (v.debugSwipeTrip != null && n++ < 400) { v.debugFrames(1); val p = trip.frame().puff > 0.5f; if (p && !was) puffs++; was = p }
            assertTrue("$id puffs $puffs", puffs >= 3)
            assertEquals(trip.x1, v.debugXY[0], 1.5f)
            assertEquals(0, v.debugLandFlames)
        }
        // swipe DOWN on a hovering non-jet character: drops and lands, never flies up
        for (id in listOf("surfer", "angel", "devil")) {
            val v = view(id); v.debugPlace(300f, onFloor = false, y = 900f)
            v.debugSwipeDown(3000f)
            var minY = 900f; var n = 0
            while (n++ < 1200 && v.debugXY[1] < v.debugXY[2] - 2f) { v.debugFrames(1); minY = minOf(minY, v.debugXY[1]) }
            assertTrue("$id went up to $minY", minY > 900f - 5f)
            assertEquals(id, v.debugXY[2], v.debugXY[1], 4f)
            assertEquals(0, v.debugLandFlames)
        }
    }

    @Test fun noCharacterButTheJetHeroesEverLoopsWhenSwipedFromTheBottom() {
        for (c in CharacterRegistry.all.filter { it.id !in Landings.ARC_IDS }) for ((vx, vy) in listOf(2400f to -600f, 600f to -2600f, -2400f to -300f, 1500f to -1500f)) {
            val v = view(c.id)
            v.debugPlace(390f, onFloor = true)
            v.debugRelease(vx, vy)
            var n = 0
            while (n++ < 420) {
                v.debugFrames(1)
                assertNull("${c.id} arc ($vx,$vy)", v.debugArc)
                assertNotEquals("${c.id} rocket arc ($vx,$vy)", Landings.Style.ROCKET_ARC, v.debugLandStyle)
                assertEquals("${c.id} jets ($vx,$vy)", 0, v.debugLandFlames)
            }
        }
    }

    @Test fun upwardThrowStillSplats() {
        val v = view("billa")
        v.debugPlace(400f, onFloor = false, y = v.debugXY[2] - 800f)
        v.debugRelease(300f, -5200f)
        assertTrue(v.debugImpact || v.debugSwipeTrip != null)
    }

    @Test fun skaterRegistered() {
        assertSame(SkaterGirl, CharacterRegistry.byId("skater"))
        assertEquals("skater", CharacterRegistry.all.last().id)
        assertEquals("skater", Population.allIds.last())
        assertEquals("Skater Girl", Population.displayName("skater"))
        assertSame(KickflipShowPower, SkaterGirl.signaturePower); assertSame(KickflipShowPower, SkaterGirl.powers.first())
        for (p in SkaterGirl.powers) assertTrue(p.plan(0.5f, 0.5f, false, Random(1)).sumOf { it.secs.toDouble() } in 2.0..9.0)
        assertSame(CharacterProfiles.SKATER, CharacterProfiles.of(SkaterGirl))
    }

    // ---------------- previews ----------------
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x40, 0x30, 0x50); textSize = 20f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    private fun drawAt(c: Canvas, who: Character, x: Float, feet: Float, sc: Float, s: MonkeyState, t: Float, ctx: AnimContext = AnimContext(), tune: (Pose) -> Unit = {}) {
        val r = who.newRenderer().also { it.wear = Wear(who) }
        val e = AnimEngine(); e.setTuning(CharacterProfiles.of(who).anim); r.anim = e
        val rig = MonkeyRig(); val p = Pose()
        for (i in 0 until 12) { who.fill(p, s, t + i / 60f, 0.5f + i / 60f, ctx, Wear(who)); tune(p); e.step(p, 1f / 60f, 0f, 0f, 1f, 1f, false) }
        rig.layout(p, x, feet, sc * CharacterProfiles.of(who).drawScale, 1f)
        r.draw(c, rig, p, t + 0.2f)
    }

    @Test fun renderSkaterPreview() {
        val w = 280; val cols = 5
        val bmp = Bitmap.createBitmap(w * cols, 440, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.WHITE)
        val shots = listOf("Idle" to (MonkeyState.IDLE to null), "Rolling" to (MonkeyState.WALK to null), "Kickflip" to (MonkeyState.POWER to KickflipShowPower),
            "Grind" to (MonkeyState.POWER to GrindPower), "Board spin" to (MonkeyState.POWER to BoardSpinPower))
        shots.forEachIndexed { i, (name, sp) ->
            val ac = AnimContext(); ac.power = sp.second; ac.powerPhase = PowerPhase.POSE; ac.powerK = if (sp.second === KickflipShowPower) 0.48f else 0.5f
            drawAt(c, SkaterGirl, i * w + w / 2f, 360f, 1.6f, sp.first, 1.1f, ac)
            c.drawText(name, i * w + w / 2f, 420f, label)
        }
        TestPaths.out("skater-preview.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        var colored = 0
        for (y in 0 until 440 step 4) for (x in 0 until w step 4) if (bmp.getPixel(x, y) != Color.WHITE) colored++
        assertTrue(colored > 200)
    }

    @Test fun renderSwipeStrip() {
        val rows = listOf("skater", "surfer")
        val ks = floatArrayOf(0.05f, 0.16f, 0.31f, 0.45f, 0.55f, 0.65f, 0.76f, 0.87f, 1f)
        val cw = 220; val rh = 300
        val bmp = Bitmap.createBitmap(160 + cw * ks.size, rh * rows.size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.rgb(0xF7, 0xF5, 0xFB))
        val fx = SwipeFx()
        val left = Paint(label).apply { textAlign = Paint.Align.LEFT; textSize = 22f }
        rows.forEachIndexed { row, id ->
            val who = CharacterRegistry.byId(id)!!
            val style = SwipeTravel.styleFor(id, false)!!
            c.drawText(who.name, 10f, row * rh + 40f, left); c.drawText(style.label, 10f, row * rh + 66f, Paint(left).apply { textSize = 15f })
            val floorY = row * rh + rh - 40f
            ks.forEachIndexed { i, k ->
                val trip = SwipeTravel.Trip(style, 0f, 0f, 400f, 0f, 1.3f).also { it.seek(k) }
                val f = trip.frame()
                val cx = 160 + i * cw + cw / 2f
                val dy = f.y   // offsets from the start line (target y = start y here)
                val bodyH = 150f
                fx.drawBehind(c, f, cx, floorY, bodyH, 1.3f, k * trip.style.secs)
                c.save()
                if (f.sink > 0f) { c.clipRect(0f, 0f, 100000f, floorY + 2f); c.translate(0f, f.sink) }
                c.translate(0f, dy)
                if (f.rot != 0f) c.rotate(f.rot, cx, floorY - bodyH * 0.4f)
                if (f.scale != 1f) c.scale(f.scale, f.scale, cx, floorY)
                if (f.alpha < 0.999f) c.saveLayerAlpha(0f, 0f, 100000f, 100000f, (255 * f.alpha).toInt())
                drawAt(c, who, cx, floorY, 1.15f, MonkeyState.IDLE, 1f + k * 3f) { p -> trip.applyPose(p, 1f + k * 3f) }
                if (f.alpha < 0.999f) c.restore()
                c.restore()
                c.save(); c.translate(0f, dy); fx.drawFront(c, f, cx, floorY, bodyH, 1.3f, k * trip.style.secs, 1f); c.restore()
                c.drawText("k=${"%.2f".format(k)}", cx, row * rh + 22f, Paint(label).apply { textSize = 14f })
            }
        }
        TestPaths.out("swipes-preview.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
