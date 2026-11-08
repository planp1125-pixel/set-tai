package com.combo.monkeycompanion.v1023

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.combo.monkeycompanion.TestPaths
import com.combo.monkeycompanion.brain.*
import com.combo.monkeycompanion.character.*
import com.combo.monkeycompanion.engine.CharacterProfiles
import com.combo.monkeycompanion.engine.anim.AnimEngine
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.world.Population
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class V1023AngelTest {
    @Test fun registeredNextToKuttiSatan() {
        assertSame(Angel, CharacterRegistry.byId("angel"))
        assertSame(Angel, CharacterRegistry.byId("Angel"))
        assertEquals("angel", CharacterRegistry.all[1].id)
        assertTrue("angel" in Population.allIds)
        assertEquals("Angel", Population.displayName("angel"))
        assertSame(CharacterProfiles.ANGEL, CharacterProfiles.of(Angel))
        assertTrue(Angel.can(Locomotion.FLY))
        assertFalse(PowerPrefs.choosable(Angel))   // fixed built-in powers
    }

    @Test fun healingIsMainPowerAndPlansAreSane() {
        assertSame(HealingGlowPower, Angel.signaturePower)
        assertSame(HealingGlowPower, Angel.powers.first())
        assertEquals(setOf("angelheal", "halospin", "angelfly", "angelfloat"), Angel.powers.map { it.id }.toSet())
        for (p in Angel.powers) for (x0 in listOf(0f, 0.3f, 0.7f, 1f)) for (y0 in listOf(0f, 0.5f, 1f)) {
            val plan = p.plan(x0, y0, false, Random(3))
            for (s in plan) {
                if (!s.tx.isNaN()) assertTrue(s.tx in 0f..1f)
                if (!s.ty.isNaN()) assertTrue(s.ty in 0f..1f)
            }
            assertTrue(plan.sumOf { it.secs.toDouble() } in 2.0..9.0)
        }
        assertTrue(Landings.pool("angel", false).none { it.first == Landings.Style.JETS || it.first == Landings.Style.ROCKET_ARC })
    }

    private fun pose(who: Character, s: MonkeyState, t: Float, pw: Superpower? = null, ph: PowerPhase = PowerPhase.POSE, k: Float = 0.5f): Pose {
        val p = Pose(); val c = AnimContext(); c.power = pw; c.powerPhase = ph; c.powerK = k
        who.fill(p, s, t, 0.5f, c, Wear(who)); return p
    }

    @Test fun animationIsDeterministicAndAlive() {
        val a = pose(Angel, MonkeyState.IDLE, 2.2f); val b = pose(Angel, MonkeyState.IDLE, 2.2f)
        assertEquals(a.bodyY, b.bodyY, 0f)
        assertNotEquals(pose(Angel, MonkeyState.IDLE, 1f).bodyY, pose(Angel, MonkeyState.IDLE, 1.8f).bodyY)
        assertTrue(pose(Angel, MonkeyState.POWER, 1f, HealingGlowPower).dvSpell > 0.9f)
        assertTrue(pose(Angel, MonkeyState.POWER, 1f, HaloSpinPower).dvClone > 0.9f)
        assertEquals(1f, pose(Angel, MonkeyState.POWER, 1f, AngelFlightPower, PowerPhase.FLY).dvFire, 0f)
        assertEquals(0f, pose(Angel, MonkeyState.IDLE, 1f).dvSpell, 0f)
    }

    private fun draw(c: Canvas, who: Character, x: Float, y: Float, scale: Float, s: MonkeyState, t: Float, pw: Superpower? = null, ph: PowerPhase = PowerPhase.POSE, k: Float = 0.5f) {
        val r = who.newRenderer().also { it.wear = Wear(who) }
        val e = AnimEngine(); e.setTuning(CharacterProfiles.of(who).anim); r.anim = e
        val rig = MonkeyRig(); val p = Pose(); val ctx = AnimContext(); ctx.power = pw; ctx.powerPhase = ph; ctx.powerK = k
        for (i in 0 until 20) { who.fill(p, s, t + i / 60f, 0.5f + i / 60f, ctx, Wear(who)); e.step(p, 1f / 60f, 0f, 0f, 1f, 1f, false) }
        rig.layout(p, x, y, scale, 1f)
        r.draw(c, rig, p, t + 20 / 60f)
    }

    @Test fun renderPreviews() {
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x40, 0x30, 0x50); textSize = 22f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val shots = listOf<Triple<String, MonkeyState, Superpower?>>(Triple("Idle", MonkeyState.IDLE, null), Triple("Healing sparkle", MonkeyState.POWER, HealingGlowPower),
            Triple("Halo spin", MonkeyState.POWER, HaloSpinPower), Triple("Wing flight", MonkeyState.POWER, AngelFlightPower), Triple("Gentle float", MonkeyState.POWER, AngelFloatPower))
        val w = 300
        val bmp = Bitmap.createBitmap(w * shots.size, 420, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.WHITE)
        shots.forEachIndexed { i, (name, s, pw) ->
            draw(c, Angel, i * w + w / 2f, 340f, 1.7f, s, 1.3f, pw, if (pw === AngelFlightPower || pw === AngelFloatPower) PowerPhase.FLY else PowerPhase.POSE)
            c.drawText(name, i * w + w / 2f, 400f, label)
        }
        TestPaths.out("angel-preview.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        var colored = 0
        for (y in 0 until 420 step 4) for (x in 0 until w step 4) if (bmp.getPixel(x, y) != Color.WHITE) colored++
        assertTrue("angel drew something ($colored)", colored > 200)

        val pair = Bitmap.createBitmap(760, 520, Bitmap.Config.ARGB_8888)
        val c2 = Canvas(pair); c2.drawColor(Color.rgb(0xF6, 0xF4, 0xFA))
        draw(c2, KuttiSatan, 230f, 440f, 2f, MonkeyState.IDLE, 2.1f)
        draw(c2, Angel, 530f, 440f, 2f, MonkeyState.IDLE, 2.1f)
        c2.drawText("Tutty", 230f, 500f, label); c2.drawText("Angel", 530f, 500f, label)
        TestPaths.out("angel-with-satan.png").outputStream().use { pair.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
