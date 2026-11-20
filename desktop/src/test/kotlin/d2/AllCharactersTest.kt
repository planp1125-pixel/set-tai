package d2

import android.graphics.Canvas
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.speech.CloudBubble
import com.combo.monkeycompanion.speech.ThinkingWords
import org.junit.Assert.*
import org.junit.Test
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** D2: every phone character draws on the desktop (same code), plus the new chat bubble. Writes a preview sheet. */
class AllCharactersTest {
    private fun opaquePixels(img: BufferedImage): Int {
        var n = 0
        for (y in 0 until img.height step 2) for (x in 0 until img.width step 2) if ((img.getRGB(x, y) ushr 24) > 40) n++
        return n
    }

    @Test fun all13DrawInEveryMainPose() {
        val chars = CharacterRegistry.all
        assertEquals(13, chars.size)
        val cell = 240; val cols = 5; val rows = (chars.size + cols - 1) / cols
        val sheet = BufferedImage(cell * cols, cell * rows + 60, BufferedImage.TYPE_INT_ARGB)
        val sg = sheet.createGraphics()
        sg.color = java.awt.Color(0xF4, 0xF1, 0xEA); sg.fillRect(0, 0, sheet.width, sheet.height)
        for ((i, c) in chars.withIndex()) {
            val wear = CharacterRegistry.wear(c)
            val r = c.newRenderer().also { it.wear = wear }
            for (st in listOf(MonkeyState.IDLE, MonkeyState.WALK, MonkeyState.WAVE, MonkeyState.DANCE, MonkeyState.SLEEP, MonkeyState.THINKING)) {
                val img = BufferedImage(cell, cell, BufferedImage.TYPE_INT_ARGB)
                val g = img.createGraphics(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val rig = MonkeyRig(); val p = Pose()
                c.fill(p, st, 0.6f, 0.6f, AnimContext(), wear)
                rig.layout(p, cell / 2f, cell * 0.9f, 1.4f, 1f)
                r.draw(Canvas(g), rig, p, 0.6f)
                g.dispose()
                assertTrue("${c.id} draws something in $st", opaquePixels(img) > 50)
                if (st == MonkeyState.WAVE) sg.drawImage(img, (i % cols) * cell, (i / cols) * cell, null)
            }
            sg.color = java.awt.Color(0x3A, 0x26, 0x18); sg.drawString(c.name, (i % cols) * cell + 10, (i / cols) * cell + 18)
            assertTrue(c.id, ThinkingWords.pick(c, 0).isNotBlank())
        }
        sg.dispose()
        val out = File("build/d2"); out.mkdirs()
        ImageIO.write(sheet, "png", File(out, "desktop-characters.png"))
    }

    @Test fun chatBubbleWithYourMessageAndFadedHistoryDraws() {
        val b = CloudBubble(2f, 30f)
        b.setText("Sure! Here's a long answer that keeps going so the bubble needs to scroll a little bit to show it all. " .repeat(3), 520,
            quote = "can you help me?", history = (1..10).map { (it % 2 == 1) to "earlier message number $it" }, maxViewH = 360f)
        assertTrue(b.scrollable && b.hasHistory)
        val img = BufferedImage(900, 900, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        b.draw(Canvas(g), 40f, 40f, 300f, 860f, 1f, 1f)
        b.scrollBy(-10_000f); b.draw(Canvas(g), 460f, 40f, 700f, 860f, 1f, 1f)
        g.dispose()
        assertTrue(opaquePixels(img) > 500)
        val out = File("build/d2"); out.mkdirs()
        ImageIO.write(img, "png", File(out, "desktop-bubble.png"))
    }
}
