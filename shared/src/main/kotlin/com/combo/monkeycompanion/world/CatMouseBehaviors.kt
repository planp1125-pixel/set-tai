package com.combo.monkeycompanion.world

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.rig.Pose
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * v8.0 Modular Cat-and-Mouse (`Billa` & `Chittu`) Slapstick Chase & Truce Behaviour Table.
 * Designed as a clean, self-contained table so new chase gags and props can be added or customized anytime.
 * All props sit strictly on the ground (fy - 6f * s) and arm angles stay <= 55° so nothing ever floats over their heads.
 */
object CatMouseBehaviors {
    enum class GagId(
        val title: String,
        val billaLine: String,
        val chittuLine: String,
        val billaStartState: MonkeyState,
        val chittuEndState: MonkeyState,
        val durSec: Float = 6.6f,
    ) {
        TIPTOE_CHILLI(
            title = "Tiptoe sneak & Guntur chilli backfire",
            billaLine = "*tiptoe… tiptoe…* Gotcha now, Chittu! Hand over that snack! 😼🐾",
            chittuLine = "Sure thing, Billa-anna! Try this fresh red Guntur chilli! 🌶️🐭💨",
            billaStartState = MonkeyState.WALK,
            chittuEndState = MonkeyState.GIGGLE,
        ),
        CHEESE_ACCORDION(
            title = "Cheese bait & accordion flatten",
            billaLine = "*rubbing paws* Free masala cheese on the floor?! Don't mind if I pounce— 🐱🧀",
            chittuLine = "*WHISTLE!* Watch the banana peel, Billa! Accordion cat mode! 😂🍌",
            billaStartState = MonkeyState.WALK,
            chittuEndState = MonkeyState.DANCE,
        ),
        TAWA_WHISTLE_ZIP(
            title = "Tawa ambush & whistle zip",
            billaLine = "Hiding behind the corner with my tawa… 3, 2, 1— POUNCE! 🍳😼",
            chittuLine = "*Psst!* Over here behind you, Billa! Too slow! 🐭👋",
            billaStartState = MonkeyState.PEEK,
            chittuEndState = MonkeyState.WAVE,
        ),
        TRUCE_NAP(
            title = "Peaceful battery/campfire truce",
            billaLine = "*yawns* Time-out, Chittu. Truce until the battery warms up… scoot over 🐱😴",
            chittuLine = "Deal! Sharing half my cheese wedge with you, Billa 🧀💛",
            billaStartState = MonkeyState.SIT,
            chittuEndState = MonkeyState.HAPPY_PAT,
        );
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val r = RectF()

    fun isCatMousePair(id1: String, id2: String): Boolean =
        (id1 == "billa" && id2 == "chittu") || (id1 == "chittu" && id2 == "billa")

    fun pickGag(state: WorldState, last: GagId? = null, rnd: Random = Random): GagId {
        if (state.hungry || state.timeOfDay.isDark) return GagId.TRUCE_NAP
        val pool = GagId.entries.filter { it != GagId.TRUCE_NAP && it != last }.ifEmpty { listOf(GagId.TIPTOE_CHILLI) }
        return pool[rnd.nextInt(pool.size)]
    }

    /**
     * Computes slapstick pose & horizontal chase offset (`-1f..1f`) at normalized progress [k] (`0f..1f`)
     * for [billaPose] and [chittuPose]. Arms stay `<= 55f` (out to the sides, never above the head).
     */
    fun applySlapstick(gag: GagId, k: Float, t: Float, billaPose: Pose, chittuPose: Pose): Float {
        return when (gag) {
            GagId.TIPTOE_CHILLI -> {
                if (k < 0.45f) {
                    // Billa tiptoe-sneaking toward Chittu
                    billaPose.bodyRot = 10f
                    billaPose.armLRaise = 35f; billaPose.armRRaise = 35f
                    billaPose.legLLift = 12f * kotlin.math.abs(sin(t * 14f))
                    billaPose.legRLift = 12f * kotlin.math.abs(cos(t * 14f))
                    (k / 0.45f) * 0.35f
                } else {
                    // Billa ate the Guntur chilli -> wide-eyed hop with arms out to the sides (<= 55f)
                    billaPose.bodyY -= 12f * kotlin.math.abs(sin(t * 18f))
                    billaPose.eyeOpen = 1.45f; billaPose.mouthOpen = 0.85f
                    billaPose.armLRaise = 52f; billaPose.armRRaise = 52f
                    chittuPose.mouthSmile = 1f
                    0.25f
                }
            }
            GagId.CHEESE_ACCORDION -> {
                if (k < 0.45f) {
                    billaPose.armLRaise = 45f; billaPose.armRRaise = 45f
                    (k / 0.45f) * 0.30f
                } else {
                    // Flattened like a cartoon accordion!
                    val wob = 0.08f * sin(t * 20f)
                    billaPose.squashY = 0.54f + wob
                    billaPose.squashX = 1.35f - wob
                    billaPose.bodyY += 15f
                    billaPose.armLRaise = 40f; billaPose.armRRaise = 40f
                    billaPose.eyeOpen = 1.35f
                    chittuPose.mouthSmile = 1f
                    0.20f
                }
            }
            GagId.TAWA_WHISTLE_ZIP -> {
                if (k < 0.45f) {
                    billaPose.armLRaise = 30f; billaPose.armRRaise = 50f
                    0.15f
                } else {
                    // Chittu zips to the other side while Billa looks confused
                    billaPose.headRot = 16f * sin(t * 10f)
                    billaPose.armLRaise = 35f; billaPose.armRRaise = 35f
                    -0.35f
                }
            }
            GagId.TRUCE_NAP -> {
                billaPose.eyeOpen = 0.25f; billaPose.mouthSmile = 0.7f
                billaPose.armLRaise = 20f; billaPose.armRRaise = 20f
                chittuPose.eyeOpen = 0.35f; chittuPose.mouthSmile = 0.9f
                chittuPose.armLRaise = 20f; chittuPose.armRRaise = 20f
                0.28f
            }
        }
    }

    /** Draws slapstick props strictly on the ground (`fy - 6f * s`) between Billa and Chittu (never at head height). */
    fun drawProps(c: Canvas, gag: GagId, k: Float, midX: Float, fy: Float, s: Float, t: Float) {
        val cy = fy - 6f * s
        when (gag) {
            GagId.TIPTOE_CHILLI -> {
                // Red Guntur chilli sitting on the floor between them
                val cx = midX
                p.style = Paint.Style.FILL; p.color = Color.rgb(0xD5, 0x00, 0x00)
                path.reset()
                path.moveTo(cx - 10f * s, cy - 4f * s)
                path.quadTo(cx + 12f * s, cy - 2f * s, cx + 16f * s, cy + 6f * s)
                path.quadTo(cx - 2f * s, cy + 5f * s, cx - 10f * s, cy - 4f * s)
                path.close()
                c.drawPath(path, p)
                p.style = Paint.Style.STROKE; p.color = Color.rgb(0x2E, 0x7D, 0x32); p.strokeWidth = 2.6f * s
                c.drawLine(cx - 10f * s, cy - 4f * s, cx - 15f * s, cy - 8f * s, p)
            }
            GagId.CHEESE_ACCORDION, GagId.TRUCE_NAP -> {
                // Golden cheese wedge sitting on the floor between them
                val cx = midX
                p.style = Paint.Style.FILL; p.color = Color.rgb(0xFF, 0xCA, 0x28)
                path.reset()
                path.moveTo(cx - 14f * s, cy + 4f * s)
                path.lineTo(cx + 14f * s, cy + 4f * s)
                path.lineTo(cx + 3f * s, cy - 12f * s)
                path.close()
                c.drawPath(path, p)
                p.color = Color.rgb(0xF5, 0x7F, 0x17)
                c.drawCircle(cx - 2f * s, cy - 1f * s, 2.6f * s, p)
                c.drawCircle(cx + 5f * s, cy + 1f * s, 2.0f * s, p)
            }
            GagId.TAWA_WHISTLE_ZIP -> {
                // Ground dust/speed lines near feet only (fy - 8f * s)
                if (k >= 0.45f) {
                    p.style = Paint.Style.STROKE; p.color = Color.argb(180, 0xFF, 0xEB, 0x3B); p.strokeWidth = 2.4f * s
                    for (i in 0..1) {
                        val ly = fy - (4f + i * 6f) * s
                        c.drawLine(midX - 24f * s, ly, midX + 24f * s, ly, p)
                    }
                }
            }
        }
    }
}
