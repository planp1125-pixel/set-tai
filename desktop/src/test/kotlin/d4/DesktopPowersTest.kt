package d4

import android.graphics.Canvas
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.settai.desktop.Config
import com.combo.settai.desktop.DesktopChat
import com.combo.settai.desktop.OverlayHost
import com.combo.settai.desktop.Stage
import org.junit.Assert.*
import org.junit.Test
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** D4: every character's powers run on the desktop (same flight plans and effects as the phone) and land again. */
class DesktopPowersTest {
    private class Host : OverlayHost {
        var placed = IntArray(4)
        override fun screenWidth() = 1600
        override fun screenHeight() = 900
        override fun placeWindow(x: Int, y: Int, w: Int, h: Int) { placed = intArrayOf(x, y, w, h) }
    }

    /** Runs on the Swing thread, like the app (the frame timer then can't run alongside the test's own frames). */
    @Test fun everyPowerFliesAndLands() {
        var err: Throwable? = null
        javax.swing.SwingUtilities.invokeAndWait { try { run() } catch (e: Throwable) { err = e } }
        err?.let { throw it }
    }

    private fun run() {
        val cfg = Config()
        val host = Host()
        val stage = Stage(host, DesktopChat(cfg), cfg)
        stage.goLive()
        var t = 1_000_000_000L
        fun frames(n: Int) { repeat(n) { t += 16_666_667L; stage.doFrame(t) } }
        frames(400)   // settle (entrance, idle)
        assertNotNull("the faint 💬 is always there, like the phone", stage.debugChatFab)
        val out = File("build/d4"); out.mkdirs()
        var shot = 0
        for (c in CharacterRegistry.all) {
            stage.setCharacter(c.id); frames(120)
            for (p in stage.powers) {
                assertTrue("${c.id} ${p.id} starts", stage.usePower(p))
                assertTrue(stage.debugPowerOn)
                frames(1)   // the window is placed on the next frame
                assertNull("no 💬 during a power", stage.debugChatFab)
                assertArrayEquals("a power uses the whole screen", intArrayOf(0, 0, 1600, 900), host.placed)
                var n = 0
                while (stage.debugPowerOn && n < 60 * 40) {
                    frames(1); n++
                    if (n == 90 && shot < 12 && (c.id == "armour" || c.id == "devil" || c.id == "superboy")) {
                        val img = BufferedImage(1600, 900, BufferedImage.TYPE_INT_ARGB)
                        val g = img.createGraphics(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                        g.color = java.awt.Color(0x2B, 0x3A, 0x55); g.fillRect(0, 0, 1600, 900)
                        stage.debugDraw(Canvas(g)); g.dispose()
                        ImageIO.write(img, "png", File(out, "power-${c.id}-${p.id}.png")); shot++
                    }
                }
                assertFalse("${c.id} ${p.id} lands within 40 s", stage.debugPowerOn)
                frames(200)
            }
        }
        stage.goDead()
    }
}
