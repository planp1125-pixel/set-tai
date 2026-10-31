package render
import android.graphics.Canvas
import com.combo.monkeycompanion.engine.show.*
import com.combo.monkeycompanion.rig.*
import java.awt.Color as AC
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Test
import kotlin.random.Random

class StageRenderTest {
    @Test fun render() {
        val out = File(System.getProperty("user.dir") + "/../../")
        val W = 720; val H = 1512; val d = 2f; val scale = 0.8f
        val run = ChaseRun.random(Random(3L), EndKind.CAT_BONKED, H / W.toFloat())
        val st = StageRenderer(BillaRenderer(), ChittuRenderer())
        for ((n, k) in listOf(0.25f, 0.5f, 0.72f).withIndex()) {
            val t = run.intro + run.run * k
            val img = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB); val g = img.createGraphics()
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = AC(235, 240, 248); g.fillRect(0, 0, W, H)
            val p = ChaseRun.Pos(); g.color = AC(220, 150, 150)
            var tt = 0f; while (tt < t) { run.frame(Role.RAT, tt, p); g.fillOval((p.x * W).toInt() - 2, (p.y * W).toInt() - 2, 4, 4); tt += 0.05f }
            st.draw(Canvas(g), run, t, W.toFloat(), scale, d)
            ImageIO.write(img, "png", File(out, "v10131-show-${n + 1}.png"))
        }
    }
}

