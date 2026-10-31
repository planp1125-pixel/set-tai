package render
import android.graphics.Canvas
import com.combo.monkeycompanion.brain.*
import com.combo.monkeycompanion.character.*
import com.combo.monkeycompanion.rig.*
import java.awt.Color as AC
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertTrue
import org.junit.Test

/** v10.16 Kutti Satan preview sheets (/workspace/v1016-devil-*.png) + "the drawing stays inside the overlay box" check. */
class V1016PreviewTest {
    private class Shot(val label: String, val s: MonkeyState, val t: Float, val power: Superpower? = null, val phase: PowerPhase = PowerPhase.POSE, val k: Float = 0.5f)

    private fun render(ch: Character, s: Shot, g: java.awt.Graphics2D, ax: Float, ay: Float, scale: Float) {
        val canvas = Canvas(g)
        val r = ch.newRenderer(); r.wear = Wear(ch)
        val rig = MonkeyRig(); val p = Pose(); val c = AnimContext()
        c.power = s.power; c.powerPhase = s.phase; c.powerK = s.k
        p.reset(); ch.fill(p, s.s, s.t, 0.5f, c, Wear(ch))
        rig.layout(p, ax, ay, scale, 1f)
        r.draw(canvas, rig, p, s.t)
    }

    private fun sheet(shots: List<Shot>, name: String, bg: AC = AC(236, 241, 248)) {
        val out = File(System.getProperty("user.dir") + "/../../")
        val cw = 300; val chh = 400; val W = cw * shots.size
        val img = BufferedImage(W, chh, BufferedImage.TYPE_INT_ARGB); val g = img.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = bg; g.fillRect(0, 0, W, chh)
        for ((i, s) in shots.withIndex()) {
            render(KuttiSatan, s, g, i * cw + cw / 2f, chh - 90f, 1.35f)
            g.color = if (bg.red < 100) AC(230, 230, 240) else AC(40, 40, 60); g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 18)
            g.drawString(s.label, i * cw + 16, 28)
        }
        ImageIO.write(img, "png", File(out, name))
    }

    private val blinkT = DevilBlink.blinkAt(3) + 0.075f

    @Test fun idleAndBlink() = sheet(listOf(
        Shot("floating", MonkeyState.IDLE, 1.0f),
        Shot("blink", MonkeyState.IDLE, blinkT),
        Shot("talking", MonkeyState.TALKING, 2.1f),
        Shot("dance", MonkeyState.DANCE, 1.4f),
        Shot("sleep", MonkeyState.SLEEP, 3f),
        Shot("giggle", MonkeyState.GIGGLE, 1.2f),
    ), "v1016-devil-idle.png")

    @Test fun vanishPortal() = sheet(listOf(
        Shot("1 crouch", MonkeyState.POWER, 1f, VanishPortalPower, PowerPhase.DROOP, 0.55f),
        Shot("2 poof!", MonkeyState.POWER, 1.2f, VanishPortalPower, PowerPhase.TELEPORT, 0.28f),
        Shot("3 gone", MonkeyState.POWER, 1.4f, VanishPortalPower, PowerPhase.TELEPORT, 0.46f),
        Shot("4 portal", MonkeyState.POWER, 1.6f, VanishPortalPower, PowerPhase.TELEPORT, 0.62f),
        Shot("5 pop out", MonkeyState.POWER, 1.8f, VanishPortalPower, PowerPhase.TELEPORT, 0.8f),
        Shot("6 ta-daa", MonkeyState.POWER, 2f, VanishPortalPower, PowerPhase.POSE, 0.3f),
    ), "v1016-devil-vanish.png")

    @Test fun magic() = sheet(listOf(
        Shot("spell sparks", MonkeyState.POWER, 2f, SpellSparksPower, PowerPhase.POSE, 0.5f),
        Shot("shadow clones", MonkeyState.POWER, 2f, ShadowClonePower, PowerPhase.POSE, 0.5f),
        Shot("float a buddy", MonkeyState.POWER, 2f, FloatBuddyPower, PowerPhase.POSE, 0.5f),
        Shot("levitate", MonkeyState.POWER, 2f, DevilLevitatePower, PowerPhase.POSE, 0.5f),
        Shot("teleport hop", MonkeyState.POWER, 2f, TeleportHopPower, PowerPhase.TELEPORT, 0.75f),
    ), "v1016-devil-magic.png")

    @Test fun onWhiteAndDark() {
        sheet(listOf(Shot("on white", MonkeyState.IDLE, 1.0f), Shot("blink", MonkeyState.IDLE, blinkT)), "v1016-devil-onwhite.png", AC(255, 255, 255))
        sheet(listOf(Shot("on dark", MonkeyState.IDLE, 1.0f), Shot("magic", MonkeyState.POWER, 2f, SpellSparksPower, PowerPhase.POSE, 0.5f)), "v1016-devil-ondark.png", AC(24, 24, 30))
    }

    /** Body poses (no screen fx) stay inside the same rig-unit box the monkey's overlay window gives every character. */
    @Test fun drawingStaysInBounds() {
        val shots = listOf(
            Shot("idle", MonkeyState.IDLE, 1.0f), Shot("idle2", MonkeyState.IDLE, 2.7f), Shot("blink", MonkeyState.IDLE, blinkT),
            Shot("walk", MonkeyState.WALK, 1.3f), Shot("dance", MonkeyState.DANCE, 1.4f), Shot("dance2", MonkeyState.DANCE, 2.9f),
            Shot("giggle", MonkeyState.GIGGLE, 1.2f), Shot("startled", MonkeyState.STARTLED, 0.4f),
            Shot("crouch", MonkeyState.POWER, 1f, VanishPortalPower, PowerPhase.DROOP, 0.55f),
            Shot("tada", MonkeyState.POWER, 2f, VanishPortalPower, PowerPhase.POSE, 0.3f),
        )
        fun bbox(ch: Character, s: Shot): IntArray {
            val S = 600; val img = BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB); val g = img.createGraphics()
            render(ch, s, g, S / 2f, S * 0.75f, 1f)
            var x0 = S; var y0 = S; var x1 = -1; var y1 = -1
            for (y in 0 until S) for (x in 0 until S) if ((img.getRGB(x, y) ushr 24) > 16) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y }
            return intArrayOf(x0 - S / 2, y0 - (S * 3 / 4), x1 - S / 2, y1 - (S * 3 / 4))
        }
        // overlay window: 96 x 118 dp at 0.66 dp per rig unit = about 145 x 179 rig units; the other floaters for reference
        for (o in listOf(MonkeyCharacter, SurferGirl, RocketGirl, ArmourAnna)) println("${o.id} idle bbox ${bbox(o, Shot("idle", MonkeyState.IDLE, 1f)).toList()}")
        for (s in shots) {
            val b = bbox(KuttiSatan, s)
            println("devil ${s.label} bbox ${b.toList()}")
            assertTrue("${s.label} ${b.toList()}", b[2] >= b[0] && b[0] >= -72 && b[2] <= 72)
            // never taller than the monkey doing the same thing (+ a few units for the horn tips), never below the floor
            val ref = if (s.power == null) bbox(MonkeyCharacter, s)[1] - 12 else -182
            assertTrue("${s.label} ${b.toList()} ref $ref", b[1] >= minOf(ref, -182) && b[3] <= 10)
        }
    }
}
