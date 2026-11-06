package com.combo.monkeycompanion.v1032

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.brain.LongPressMenu
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.engine.action.ActionLibrary
import com.combo.monkeycompanion.engine.expression.Expressions
import com.combo.monkeycompanion.engine.movement.Edge
import com.combo.monkeycompanion.engine.movement.Mover
import com.combo.monkeycompanion.engine.props.Props
import com.combo.monkeycompanion.engine.script.ScriptError
import com.combo.monkeycompanion.engine.script.ScriptPlayer
import com.combo.monkeycompanion.engine.script.ScriptStageRenderer
import com.combo.monkeycompanion.engine.script.ShowScript
import com.combo.monkeycompanion.engine.stagefx.VanishStyle
import com.combo.monkeycompanion.overlay.ScriptShowHub
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.Pose
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** v10.32: Expression / Movement / Props / StageFx engines + the script player, with the Satan vs Angel show. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class V1032Test {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private fun show() = ScriptShowHub.load(ctx)
    @After fun after() { ScriptShowHub.finish(ctx) }

    @Test fun userScriptParses() {
        val s = show()
        assertEquals("Satan vs Angel", s.title)
        assertEquals(mapOf("angel" to "angel", "satan" to "devil"), s.cast)
        assertEquals(1, s.rules.size)
        assertEquals("fireball", s.rules[0].prop); assertEquals("angel", s.rules[0].target)
        assertEquals("vanish", s.rules[0].cmds[0].verb)
        assertTrue(s.beats.any { b -> b.cmds.any { it.verb == "say" && it.args[0] == "sorry bro" } })
        assertEquals(21.6f, s.end, 0.01f)
    }

    @Test fun badScriptsSayWhichLine() {
        val e = assertThrows(ScriptError::class.java) { ShowScript.parse("cast: a=angel\nat 1: a dance-wildly") }
        assertEquals(2, e.line)
        assertThrows(ScriptError::class.java) { ShowScript.parse("cast: a=angel\nwhen fireball near zed: a vanish") }
    }

    @Test fun angelFliesInThenHoversAndSatanEntersFromTheEdge() {
        val p = ScriptPlayer(show())
        val angel = p.actor("angel")!!; val satan = p.actor("satan")!!
        p.advanceTo(0.05f); assertTrue("angel starts off-screen left", angel.mover.x < 0f)
        p.advanceTo(3.0f)
        assertTrue(angel.mover.hovering); assertFalse(angel.mover.moving)
        assertEquals(0.68f, angel.mover.x, 0.01f); assertEquals(0.40f, angel.mover.y, 0.01f)
        val y0 = angel.mover.bob(3.0f); val y1 = angel.mover.bob(3.6f); assertNotEquals("hover bob", y0, y1)
        p.advanceTo(2.85f.coerceAtLeast(p.t)); p.advanceTo(2.9f)
        assertTrue("satan comes from the left edge", satan.mover.x < 0.05f)
        p.advanceTo(4.3f)
        assertEquals(0.22f, satan.mover.x, 0.01f); assertTrue(satan.visible)
    }

    @Test fun fireballNearAngelMakesHerVanishAndTheRestPlays() {
        val p = ScriptPlayer(show())
        val angel = p.actor("angel")!!; val satan = p.actor("satan")!!
        p.advanceTo(4.7f); assertTrue("fireballs fly", p.projectiles.isNotEmpty())
        assertTrue(angel.visible)
        p.advanceTo(7.2f)
        assertTrue("proximity rule fired", 0 in p.fired)
        assertTrue(angel.mover.gone); assertTrue(satan.visible)
        p.advanceTo(8.6f); assertTrue("angel back", angel.visible); assertEquals("wake", angel.faceAt(p.t))
        p.advanceTo(10f); assertTrue(p.rays.any { it.from == "satan" && it.src == "eyes" }); assertEquals("shield", angel.held?.id)
        p.advanceTo(13f); assertTrue(p.clashOn); assertEquals(2, p.rays.count { it.src == "hands" })
        assertEquals(p.clashX to p.clashY, p.rayEnd(p.rays.first { it.from == "angel" }))
        p.advanceTo(15.6f); assertNotNull(p.crack); assertTrue(p.crack!!.cy > Mover.FLOOR)
        p.advanceTo(18.5f); assertEquals("sorry bro", satan.saying(p.t)); assertEquals("sorry bro", angel.saying(p.t)); assertEquals("fear", satan.faceAt(p.t))
        p.advanceTo(21.3f); assertTrue(satan.mover.gone && angel.mover.gone)
        assertEquals(VanishStyle.RED_SMOKE, satan.character.vanishStyle); assertEquals(VanishStyle.WHITE_SMOKE, angel.character.vanishStyle)
        p.advanceTo(21.7f); assertTrue(p.done)
    }

    @Test fun sameVerbsWorkForAnyCastAndAnyProp() {
        val sc = ShowScript.parse("""
            cast: hero=gajju, rival=billa
            at 0: hero place 0.75 0.86; rival enter right to 0.2 0.86 in 1
            at 1.2: rival throw dosa_pan at hero every 0.4 x3 speed 1.2; rival do throw for 1.2
            when dosa_pan near hero: hero vanish in 0.3; rival face smug
            end at 4
        """.trimIndent())
        val p = ScriptPlayer(sc)
        p.advanceTo(0.5f); assertTrue("billa walks on the floor", p.actor("rival")!!.mover.moving); assertEquals(Mover.FLOOR, p.actor("rival")!!.mover.y, 0.001f)
        p.advanceTo(3f)
        assertTrue(0 in p.fired); assertTrue(p.actor("hero")!!.mover.gone); assertEquals("smug", p.actor("rival")!!.faceAt(p.t))
        // no character ids hard-wired in the engines
        for (f in listOf("engine/script/ScriptPlayer.kt", "engine/script/ShowScript.kt", "engine/script/ScriptStageRenderer.kt", "engine/movement/Movement.kt", "engine/props/Props.kt", "engine/expression/Expressions.kt")) {
            val src = File(sharedSrc(), f).readText()
            for (id in listOf("\"devil\"", "\"angel\"", "\"satan\"", "\"monkey\"", "\"billa\"")) assertFalse("$f hardcodes $id", src.contains(id))
        }
    }

    @Test fun walkersStayOnTheFloorFliersCanHover() {
        val walker = Mover.of(CharacterRegistry.byId("billa")!!); val flier = Mover.of(CharacterRegistry.byId("angel")!!)
        walker.enterFrom(0f, Edge.LEFT, 0.5f, 0.3f, 1f); flier.enterFrom(0f, Edge.LEFT, 0.5f, 0.3f, 1f)
        walker.step(1.1f); flier.step(1.1f)
        assertEquals(Mover.FLOOR, walker.y, 0.001f); assertEquals(0.3f, flier.y, 0.001f)
        assertTrue(flier.hovering); assertFalse(walker.hovering)
    }

    @Test fun expressionsActionsAndPropsGoThroughTheSharedPose() {
        for (c in CharacterRegistry.all) {
            val p = Pose(); c.fill(p, MonkeyState.IDLE, 1f, 1f, AnimContext(), CharacterRegistry.wear(c))
            assertTrue(Expressions.apply("fear", p, 0f, 1f)); assertEquals(c.id, 1.4f, p.eyeOpen, 0.01f); assertEquals(1f, p.sweat, 0.01f)
            assertTrue(ActionLibrary.apply("raise_shield", p, 0f, 1f)); assertEquals(95f, p.armLRaise, 0.5f)
            ActionLibrary.apply("look_at", p, 0f, 1f, -1f); assertEquals(-0.9f, p.lookX, 0.01f)
        }
        for (id in listOf("fireball", "shield", "dosa_pan", "shock")) assertNotNull(Props.byId(id))
        assertNull(Props.byId("piano"))
        // the renderer builds the same pose for the actor
        val pl = ScriptPlayer(show()); pl.advanceTo(18.3f)
        val pose = ScriptStageRenderer(pl).poseOf("angel")!!
        assertTrue(pose.eyeOpen > 1.2f)
    }

    @Test fun showRendersFramesAndMenuHasIt() {
        assertTrue("story" in LongPressMenu.groupItems("g_show"))
        assertTrue("show" in LongPressMenu.groupItems("g_show"))
        val pl = ScriptPlayer(show(), 2340f / 1080f); val r = ScriptStageRenderer(pl)
        val times = floatArrayOf(3f, 5.4f, 10f, 13.5f, 16.2f, 18.5f, 20.6f)
        val W = 360; val H = 780
        val sheet = Bitmap.createBitmap(W * times.size, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet); c.drawColor(Color.rgb(0xD8, 0xE4, 0xF0))
        times.forEachIndexed { i, tt -> pl.advanceTo(tt); c.save(); c.translate((i * W).toFloat(), 0f); c.clipRect(0, 0, W, H); r.draw(c, W.toFloat(), H.toFloat()); c.restore() }
        val out = File("build/test-out"); out.mkdirs()
        File(out, "v1032-satan-vs-angel.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        var ink = 0; for (x in 0 until sheet.width step 7) for (y in 0 until H step 7) if (sheet.getPixel(x, y) != Color.rgb(0xD8, 0xE4, 0xF0)) ink++
        assertTrue(ink > 200)
    }

    private fun sharedSrc(): File {
        var d = File("").absoluteFile
        while (d.parentFile != null && !File(d, "shared").exists()) d = d.parentFile
        return File(d, "shared/src/main/kotlin/com/combo/monkeycompanion")
    }
}
