package render
import android.graphics.Canvas
import com.combo.monkeycompanion.engine.show.*
import com.combo.monkeycompanion.rig.*
import java.awt.Color as AC
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Test

/** v10.21 Show 2.0: renders a few frames of every episode to build/test-out (and proves the renderer runs). */
class EpisodeRenderTest {
    @Test fun render() {
        val out = File(System.getProperty("user.dir"), "build/test-out").apply { mkdirs() }
        val W = 540; val H = 1170; val d = 1.35f
        val r = EpisodeRenderer(BillaRenderer(), ChittuRenderer())
        for (i in Episodes.all.indices) {
            val ep = Episodes.byIndex(i)
            for ((n, k) in listOf(0.05f, 0.3f, 0.55f, 0.8f).withIndex()) {
                val img = BufferedImage(W, H, BufferedImage.TYPE_INT_RGB); val g = img.createGraphics()
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
                g.color = AC(225, 232, 242); g.fillRect(0, 0, W, H)
                r.draw(Canvas(g), ep, ep.irisAt * k, W.toFloat(), H.toFloat(), 48f * d, d)
                ImageIO.write(img, "png", File(out, "v1021-ep${i + 1}-${n + 1}.png"))
            }
        }
    }
}
