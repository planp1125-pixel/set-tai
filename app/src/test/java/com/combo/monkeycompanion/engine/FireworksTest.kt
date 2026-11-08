package com.combo.monkeycompanion.engine

import com.combo.monkeycompanion.rig.Fireworks
import com.combo.monkeycompanion.rig.FwShape
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v10.8 Skye's sky-burst: different every time, pooled under the cap. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FireworksTest {
    @Test fun twentyBurstsNeverRepeatBackToBackAndStayUnderTheCap() {
        for (seed in listOf(1L, 2L, 77L, 2026L)) {
            val fw = Fireworks(seed)
            var booms = 0
            fw.onBoom = { _, _, _ -> booms++ }
            repeat(20) { i ->
                fw.burstAt(200f + i * 10f, 300f, 3f)
                repeat(12) { fw.step(1f / 60f); assertTrue("live ${fw.count}", fw.count <= Fireworks.CAP) }
            }
            repeat(600) { fw.step(1f / 60f) }
            val h = fw.history
            assertTrue(h.size >= 20)
            for (i in 1 until h.size) {
                assertNotEquals("seed $seed burst $i shape", h[i - 1].shape, h[i].shape)
                assertNotEquals("seed $seed burst $i palette", h[i - 1].palette, h[i].palette)
            }
            assertTrue("peak ${fw.peak}", fw.peak <= Fireworks.CAP)
            assertTrue("varied shapes", h.map { it.shape }.toSet().size >= 6)
            assertTrue("sound per burst", booms >= 20)
            assertTrue("all done", !fw.active)
        }
    }

    @Test fun launchesRiseThenBurstAndFinalesHappenSometimes() {
        val fw = Fireworks(5L)
        var finales = 0
        repeat(200) {
            var booms = 0
            fw.onBoom = { _, _, _ -> booms++ }
            val n = fw.launch(100f, 900f, 300f, 200f, 3f)
            repeat(240) { fw.step(1f / 60f) }
            assertTrue("every shell bursts: $booms / $n", booms >= n)
            if (n == 3) finales++
            assertTrue(fw.peak <= Fireworks.CAP)
        }
        assertTrue("finales ~5%: $finales", finales in 2..30)
        assertTrue(FwShape.values().size >= 11)
    }
}
