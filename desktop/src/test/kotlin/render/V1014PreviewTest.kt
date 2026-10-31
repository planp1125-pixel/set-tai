package render
import android.graphics.Canvas
import com.combo.monkeycompanion.brain.*
import com.combo.monkeycompanion.character.*
import com.combo.monkeycompanion.rig.*
import java.awt.Color as AC
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Test

/** v10.14 preview sheets (plain background, no campfire): /workspace/v1014-hollowman.png and v1014-surfer.png. */
class V1014PreviewTest {
    private class Shot(val label: String, val s: MonkeyState, val t: Float, val power: Superpower? = null, val phase: PowerPhase = PowerPhase.POSE, val k: Float = 0.5f, val crashed: Boolean = false)

    private fun sheet(ch: Character, shots: List<Shot>, name: String) {
        val out = File(System.getProperty("user.dir") + "/../../")
        val cw = 300; val chh = 380; val W = cw * shots.size; val H = chh
        val img = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB); val g = img.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = AC(236, 241, 248); g.fillRect(0, 0, W, H)
        val canvas = Canvas(g)
        for ((i, s) in shots.withIndex()) {
            val r = ch.newRenderer(); r.wear = Wear(ch)
            val rig = MonkeyRig(); val p = Pose(); val c = AnimContext()
            c.power = s.power; c.powerPhase = s.phase; c.powerK = s.k; c.crashed = s.crashed
            p.reset(); ch.fill(p, s.s, s.t, 0.5f, c, Wear(ch))
            rig.layout(p, i * cw + cw / 2f, chh - 110f, 1.35f, 1f)
            r.draw(canvas, rig, p, s.t)
            g.color = AC(40, 40, 60); g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 18)
            g.drawString(s.label, i * cw + 16, 28)
        }
        ImageIO.write(img, "png", File(out, name))
    }

    @Test fun hollowMan() = sheet(HollowMan, listOf(
        Shot("idle groove", MonkeyState.IDLE, 1.0f),
        Shot("moonwalk", MonkeyState.POWER, 2.2f, MoonwalkPower, PowerPhase.FLY, 0.5f),
        Shot("toe stand", MonkeyState.POWER, 3f, MoonwalkPower, PowerPhase.POSE, 0.5f),
        Shot("spin", MonkeyState.POWER, 3f, DanceSpinPower, PowerPhase.POSE, 0.3f),
        Shot("hat tip", MonkeyState.POWER, 3f, HatTipPower, PowerPhase.POSE, 0.5f),
        Shot("lean", MonkeyState.POWER, 3f, GravityLeanPower, PowerPhase.POSE, 0.55f),
    ), "v1014-hollowman.png")

    @Test fun surfer() = sheet(SurferGirl, listOf(
        Shot("riding", MonkeyState.IDLE, 1.0f),
        Shot("carving", MonkeyState.WALK, 2.0f),
        Shot("big wave", MonkeyState.POWER, 2.4f, BigWavePower, PowerPhase.FLY, 0.5f),
        Shot("aerial flip", MonkeyState.POWER, 2.4f, AerialFlipPower, PowerPhase.FLY, 0.45f),
        Shot("wipeout", MonkeyState.POWER, 2.7f, AerialFlipPower, PowerPhase.LAND, 0.4f, crashed = true),
        Shot("stuck it!", MonkeyState.POWER, 3f, AerialFlipPower, PowerPhase.POSE, 0.5f),
    ), "v1014-surfer.png")
}
