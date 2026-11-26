package d5

import android.graphics.Canvas
import com.combo.monkeycompanion.brain.BubbleShowPower
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.PearlGlowPower
import com.combo.monkeycompanion.brain.PowerPhase
import com.combo.monkeycompanion.brain.Superpower
import com.combo.monkeycompanion.brain.TailSplashPower
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.character.Mermaid
import com.combo.monkeycompanion.engine.CharacterProfiles
import com.combo.monkeycompanion.engine.anim.AnimEngine
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import org.junit.Assert.*
import org.junit.Test
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.random.Random

/** v10.55 Meenu the mermaid: registered, her powers, and a preview sheet (attached to the test build as a picture). */
class MermaidPreviewTest {
    @Test fun registeredWithHerOwnPowers() {
        assertSame(Mermaid, CharacterRegistry.byId("mermaid"))
        assertSame(Mermaid, CharacterRegistry.byId("Meenu"))
        assertSame(Mermaid, CharacterRegistry.byId("kadal kanni"))
        assertEquals("mermaid", CharacterRegistry.all.last().id)
        assertSame(BubbleShowPower, Mermaid.signaturePower)
        assertEquals(listOf("bubbleshow", "tailsplash", "pearlglow"), Mermaid.powers.map { it.id })
        for (p in Mermaid.powers) assertTrue(p.plan(0.5f, 0.5f, false, Random(1)).sumOf { it.secs.toDouble() } in 2.0..9.0)
        assertSame(CharacterProfiles.MERMAID, CharacterProfiles.of(Mermaid))
        assertFalse("she swims, never walks", Mermaid.can(com.combo.monkeycompanion.character.Locomotion.WALK))
    }

    /** Draws one pose. [swimVx] > 0 feeds the animation engine a moving position, so she swims (leans, beats, bubble trail). */
    private fun shot(st: MonkeyState, power: Superpower? = null, k: Float = 0.5f, swimVx: Float = 0f, t0: Float = 1.3f): BufferedImage {
        val cell = 300
        val img = BufferedImage(cell, cell, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = java.awt.Color(0xCF, 0xEC, 0xFF); g.fillRect(0, 0, cell, cell)
        val wear = CharacterRegistry.wear(Mermaid)
        val r = Mermaid.newRenderer().also { it.wear = wear }
        val e = AnimEngine(); e.setTuning(CharacterProfiles.of(Mermaid).anim); r.anim = e
        val ac = AnimContext(); ac.power = power; ac.powerPhase = PowerPhase.POSE; ac.powerK = k
        val rig = MonkeyRig(); val p = Pose()
        var x = 150f
        // ~1.5 s of frames so the swim lean, bubbles and springs settle
        for (i in 0 until 90) {
            val t = t0 + i / 60f
            p.reset(); Mermaid.fill(p, st, t, t, ac, wear)
            x += swimVx / 60f
            e.step(p, 1f / 60f, x, 250f, 1f, 1f, true)
            rig.layout(p, 150f, 250f, 1.25f, 1f)
            if (i < 89) r.draw(Canvas(BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB).createGraphics()), rig, p, t)
            else r.draw(Canvas(g), rig, p, t)
        }
        g.dispose()
        return img
    }

    @Test fun previewSheet() {
        val shots = listOf(
            "Floating" to shot(MonkeyState.IDLE),
            "Swimming" to shot(MonkeyState.WALK, swimVx = 500f),
            "Sleeping" to shot(MonkeyState.SLEEP),
            "Bubble show (blow)" to shot(MonkeyState.POWER, BubbleShowPower, 0.45f),
            "Bubble show (pop)" to shot(MonkeyState.POWER, BubbleShowPower, 0.75f),
            "Tail splash" to shot(MonkeyState.POWER, TailSplashPower, 0.55f),
            "Pearl glow" to shot(MonkeyState.POWER, PearlGlowPower, 0.5f),
            "Dancing" to shot(MonkeyState.DANCE),
        )
        val cols = 4; val cell = 300
        val sheet = BufferedImage(cell * cols, (cell + 30) * ((shots.size + cols - 1) / cols), BufferedImage.TYPE_INT_ARGB)
        val sg = sheet.createGraphics()
        sg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        sg.color = java.awt.Color.WHITE; sg.fillRect(0, 0, sheet.width, sheet.height)
        for ((i, s) in shots.withIndex()) {
            val (name, img) = s
            var ink = 0
            for (y in 0 until cell step 3) for (xx in 0 until cell step 3) if (img.getRGB(xx, y) != java.awt.Color(0xCF, 0xEC, 0xFF).rgb) ink++
            assertTrue("$name draws something", ink > 300)
            val x = (i % cols) * cell; val y = (i / cols) * (cell + 30)
            sg.drawImage(img, x, y, null)
            sg.color = java.awt.Color(0x30, 0x24, 0x40); sg.font = sg.font.deriveFont(java.awt.Font.BOLD, 18f)
            sg.drawString(name, x + 12, y + cell + 22)
        }
        sg.dispose()
        val out = File("build/d4"); out.mkdirs()
        ImageIO.write(sheet, "png", File(out, "power-mermaid-preview.png"))
    }
}
