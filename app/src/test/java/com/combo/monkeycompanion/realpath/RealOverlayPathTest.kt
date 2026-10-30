package com.combo.monkeycompanion.realpath

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.overlay.MonkeyOverlayService
import com.combo.monkeycompanion.overlay.MonkeyView
import com.combo.monkeycompanion.powers.ActionLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSettings
import java.io.File
import java.io.FileOutputStream

/**
 * v6 "real path" tests: the actual overlay service (real WindowManager.LayoutParams updated through
 * MonkeyOverlayService.placeWindow -> wm.updateViewLayout), the real MonkeyView, real touch events and a real
 * ACTION_POWER_CONNECTED broadcast. We record the overlay window's position every frame like the phone would see it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")   // a Motorola-like 1080x2340 px phone at 3x density
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RealOverlayPathTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun <T> field(o: Any, name: String): T {
        val f = o.javaClass.getDeclaredField(name); f.isAccessible = true
        @Suppress("UNCHECKED_CAST") return f.get(o) as T
    }

    /** The "WindowManager mock": the real one, recording every window move the overlay asks for. */
    class RecWm(private val real: WindowManager) : WindowManager by real {
        val moves = ArrayList<IntArray>()
        override fun updateViewLayout(view: android.view.View, params: android.view.ViewGroup.LayoutParams) {
            val p = params as WindowManager.LayoutParams
            moves += intArrayOf(p.x, p.y, p.width, p.height)
            real.updateViewLayout(view, params)
        }
    }

    private class Rig(val svc: MonkeyOverlayService, val view: MonkeyView, val wm: RecWm, val lp: () -> WindowManager.LayoutParams)

    private fun start(charId: String, saver: Boolean = false, roam: Boolean = true): Rig {
        com.combo.monkeycompanion.fun5.Stage.resetImeForTest()
        ChatHub.init(ctx)
        ctx.getSharedPreferences("settai_v3", 0).edit().clear().commit()
        ChatHub.settings.batterySaver = saver
        ChatHub.settings.gags = false
        ChatHub.settings.roam = roam
        CharacterRegistry.select(charId)
        ShadowSettings.setCanDrawOverlays(true)
        val svc = Robolectric.buildService(MonkeyOverlayService::class.java).create().get()
        val v: MonkeyView = field(svc, "view")
        val rec = RecWm(field(svc, "wm"))
        svc.javaClass.getDeclaredField("wm").apply { isAccessible = true }.set(svc, rec)
        shadowOf(Looper.getMainLooper()).idle()
        v.layout(0, 0, svc.screenWidth(), svc.screenHeight())
        v.debugQuiet = true
        v.unhide()
        v.debugFrames(400)
        v.debugSurf("FLOOR", frac = 0.5f)
        v.debugFrames(20)
        ActionLog.clear(ctx)
        rec.moves.clear()
        return Rig(svc, v, rec) { field(svc, "params") }
    }

    private var clock = 50_000L
    private fun touch(r: Rig, a: Int, rx: Float, ry: Float) {
        val lp = r.lp()
        val e = MotionEvent.obtain(clock, clock, a, rx, ry, 0)
        e.offsetLocation(-lp.x.toFloat(), -lp.y.toFloat())   // window-local coords, raw = screen coords
        r.view.dispatchTouchEvent(e); e.recycle()
    }

    /** A real-phone-like swipe: grab, small lift, then a moderate downward swipe (~700 dp/s) and release. */
    private fun swipeDown(r: Rig, liftPx: Float = 500f, swipePx: Float = 330f, stepMs: Long = 16) {
        val b = r.view.debugBox()
        val x = b.centerX(); val y = b.centerY()
        touch(r, MotionEvent.ACTION_DOWN, x, y)
        for (i in 1..14) { clock += stepMs; touch(r, MotionEvent.ACTION_MOVE, x, y - liftPx * i / 14); r.view.debugFrames(1) }
        clock += 250; touch(r, MotionEvent.ACTION_MOVE, x, y - liftPx); r.view.debugFrames(1)
        val n = 10
        for (i in 1..n) { clock += stepMs; touch(r, MotionEvent.ACTION_MOVE, x + 6f * i / n, y - liftPx + swipePx * i / n); r.view.debugFrames(1) }
        clock += 10; touch(r, MotionEvent.ACTION_UP, x + 6f, y - liftPx + swipePx)
    }

    @Test fun swipeDownMovesTheRealWindowAlongACurve() {
        for (id in listOf("superboy", "armour")) {
            for (saver in listOf(false, true)) {
                val r = start(id, saver)
                // let go mid-air first: it hovers
                swipeDown(r, swipePx = 0f)
                r.view.debugFrames(30)
                assertTrue("$id hovers", r.view.debugHovering)
                val before = "box ${r.view.debugBox()} lp ${r.lp().x},${r.lp().y} ${r.lp().width}x${r.lp().height}"
                swipeDown(r)
                val xs = ArrayList<Int>(); val ys = ArrayList<Int>(); var dbg = ""; val bx = ArrayList<Int>()
                var f = 0
                while ((r.view.debugFalling || r.view.debugArcPlan != null) && f < 400) { r.view.debugFrames(1); r.view.draw(Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))); val lp = r.lp(); xs += lp.x + lp.width / 2; ys += lp.y + lp.height / 2; bx += r.view.debugBox().centerX().toInt(); if (f % 20 == 0) dbg += "[${lp.x},${lp.y} ${lp.width}x${lp.height} box ${r.view.debugBox().left.toInt()},${r.view.debugBox().top.toInt()}]"; f++ }
                val log = ActionLog.all(ctx).map { it.what + " | " + it.result }
                assertTrue("$id saver=$saver logs the swipe: $log; $before", log.any { it.startsWith("Swipe down v=") && it.contains("-> rocket path") })
                assertTrue("$id flew ($f frames)", f > 60)
                val wmXs = r.wm.moves.map { it[0] + it[2] / 2 }
                assertTrue("$id: WindowManager.updateViewLayout moved the window sideways (${wmXs.size} moves, x ${wmXs.minOrNull()}..${wmXs.maxOrNull()})",
                    wmXs.size > 60 && wmXs.max() - wmXs.min() > r.svc.screenWidth() * 0.3)
                val span = xs.max() - xs.min()
                val bodySpan = bx.max() - bx.min()
                assertTrue("$id saver=$saver: window x varies a lot (span $span px of ${r.svc.screenWidth()}) frames $f $dbg", span > r.svc.screenWidth() * 0.3)
                val covered = bodySpan + r.view.boxW
                assertTrue("$id saver=$saver: the body sweeps >= half the screen (centre span $bodySpan, covered $covered) $dbg",
                    bodySpan >= r.svc.screenWidth() * 0.4 && covered >= r.svc.screenWidth() * 0.5)
                // not a straight line: max deviation from the chord
                val x0 = xs.first().toFloat(); val y0 = ys.first().toFloat(); val x1 = xs.last().toFloat(); val y1 = ys.last().toFloat()
                val len = kotlin.math.hypot(x1 - x0, y1 - y0).coerceAtLeast(1f)
                val dev = xs.indices.maxOf { kotlin.math.abs((xs[it] - x0) * (y1 - y0) - (ys[it] - y0) * (x1 - x0)) / len }
                assertTrue("$id curved (deviation $dev px)", dev > 120f)
                println("REALPATH $id saver=$saver bodySpan=$bodySpan covered=${bodySpan + r.view.boxW} windowSpan=$span px of ${r.svc.screenWidth()} dev=$dev frames=$f ${log.first { it.startsWith("Swipe") }}")
                r.svc.onDestroy()
            }
        }
    }

    @Test fun annaHoverFramesHaveJetFlames() {
        for (saver in listOf(false, true)) {
            val r = start("armour", saver)
            swipeDown(r, swipePx = 0f)
            r.view.debugFrames(40)
            assertTrue(r.view.debugHovering)
            val lp = r.lp()
            val bmp = Bitmap.createBitmap(lp.width, lp.height, Bitmap.Config.ARGB_8888)
            r.view.draw(Canvas(bmp))
            // flames = orange/yellow pixels below the box
            val b = r.view.debugBox()
            val boxBottom = (b.bottom - lp.y).toInt()
            var flame = 0
            for (y in (boxBottom - 20).coerceAtLeast(0) until bmp.height) for (x in 0 until bmp.width) {
                val c = bmp.getPixel(x, y)
                if (Color.alpha(c) > 120 && Color.red(c) > 200 && Color.green(c) in 90..250 && Color.blue(c) < 140) flame++
            }
            if (!saver) FileOutputStream(File("build/anna-hover-flames.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue("saver=$saver flame pixels below Anna's boots: $flame (window ${lp.width}x${lp.height}, box bottom $boxBottom)", flame > 60)
            r.svc.onDestroy()
        }
    }

    @Test fun chargerBroadcastAlwaysPlaysForEveryCharacter() {
        for (id in listOf("monkey", "superboy", "armour", "mask")) {
            val r = start(id, saver = id == "mask", roam = false)   // no wandering drops of its own, to isolate the moment
            // even while asleep, and twice in a row (no cooldown for a plug-in)
            for (round in 0..1) {
                r.view.controller.sleep()
                r.wm.moves.clear(); ActionLog.clear(ctx)
                ctx.sendBroadcast(Intent(Intent.ACTION_POWER_CONNECTED))
                shadowOf(Looper.getMainLooper()).idle()        // the real receiver runs; the frame loop runs the moment
                r.view.debugFrames(400)
                val log = ActionLog.all(ctx).map { it.what + " | " + it.result }
                assertTrue("$id round $round logged: $log", log.any { it.startsWith("Charger plugged -> moment played") })
                val minY = r.wm.moves.minOfOrNull { it[1] } ?: -1
                val maxRight = r.wm.moves.maxOfOrNull { it[0] + it[2] } ?: -1
                assertEquals("$id window reached the status bar", 0, minY)
                assertTrue("$id window reached the battery icon (right $maxRight)", maxRight >= r.svc.screenWidth() - 40)
                assertFalse("$id home again, not falling", r.view.debugFalling || r.view.debugChargeTrip)
                assertTrue("$id no drop afterwards: $log", log.none { it.startsWith("Landing picked") })
            }
            r.svc.onDestroy()
        }
    }

    @Test fun chargeBoltIsDrawnInSettaisOwnWindow() {
        for (id in listOf("monkey", "superboy", "armour", "mask")) {
            val r = start(id, saver = true)                 // battery saver: still a big bolt (only particles are dropped)
            r.view.onChargerPlugged()                       // what the receiver calls; frames driven by hand here
            var boltPixels = 0
            for (f in 0 until 260) {
                r.view.debugFrames(1)
                if (f == 120) {
                    val lp = r.lp()
                    assertEquals(0, lp.y)
                    val bmp = Bitmap.createBitmap(lp.width, lp.height, Bitmap.Config.ARGB_8888)
                    r.view.draw(Canvas(bmp))
                    for (y in 0 until minOf(bmp.height, 160)) for (x in 0 until bmp.width) if (Color.alpha(bmp.getPixel(x, y)) > 150) boltPixels++
                    FileOutputStream(File("build/charge-$id.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            assertTrue("$id bolt drawn near the battery in its own window: $boltPixels px", boltPixels > 1500)
            r.svc.onDestroy()
        }
    }

    @Test fun chargerBlockedOnlyByDndOrCall() {
        val r = start("monkey")
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        shadowOf(nm).setNotificationPolicyAccessGranted(true); nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        r.view.onChargerPlugged()
        val log = ActionLog.all(ctx).map { it.what }
        assertTrue("$log", log.any { it == "Charger plugged -> moment blocked (Do Not Disturb)" })
        nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
        // quiet hours all day: still plays (you plugged it in)
        ChatHub.settings.quietStart = 0; ChatHub.settings.quietEnd = 24 * 60 - 1
        ActionLog.clear(ctx); r.view.onChargerPlugged()
        assertTrue(ActionLog.all(ctx).any { it.what.startsWith("Charger plugged -> moment played") })
        r.svc.onDestroy()
    }

    @Test fun chargerDuringScreenOffIsDeferredThenPlays() {
        val r = start("monkey")
        r.view.goToSleep(); r.view.debugFrames(10)
        ctx.sendBroadcast(Intent(Intent.ACTION_POWER_CONNECTED)); shadowOf(Looper.getMainLooper()).idle()
        val log1 = ActionLog.all(ctx).map { it.what }
        assertTrue("deferred: $log1", log1.any { it.contains("deferred") })
        r.view.onUserPresent()
        var played = false
        for (i in 0 until 600) { r.view.debugFrames(1); if (i % 30 == 0) shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500)); if (ActionLog.all(ctx).any { it.what.startsWith("Charger plugged -> moment played") }) { played = true; break } }
        assertTrue("played after unlock: ${ActionLog.all(ctx).map { it.what }}", played)
        r.svc.onDestroy()
    }

    @Test fun keyboardDodgeRelocatesAboveImeAcrossAllPaths() {
        for (id in listOf("monkey", "superboy", "armour")) {
            val r = start(id, roam = false)
            // Start at the bottom of the screen where the spacebar / b,n,m keys sit
            r.view.debugSurf("FLOOR", frac = 0.5f)
            r.view.debugFrames(10)
            assertTrue("$id starts near bottom (${r.view.debugBox().bottom})", r.view.debugBox().bottom > 2000f)

            // Path A: IME opens with top at y = 1320 px
            val imeTop = 1320f
            com.combo.monkeycompanion.fun5.Stage.reportProbeIme(true, imeTop)
            r.view.debugFrames(25)

            assertTrue("$id entered keyboardDodge", r.view.keyboardDodge)
            val boxAfter = r.view.debugBox()
            val lpAfter = r.lp()
            assertTrue("$id body moved above keyboard: bottom=${boxAfter.bottom} <= $imeTop", boxAfter.bottom <= imeTop)
            assertTrue("$id window moved above keyboard: winBottom=${lpAfter.y + lpAfter.height} <= ${imeTop.toInt() + 20}", lpAfter.y + lpAfter.height <= imeTop.toInt() + 20)
            val logs = ActionLog.all(ctx).map { it.what }
            assertTrue("$id logged dodge: $logs", logs.any { it.startsWith("Keyboard open -> dodged above IME") })

            // Even if dropped or sleeping while IME is open, never falls into the keyboard zone
            r.view.controller.sleep()
            r.view.debugFrames(20)
            assertTrue("$id stays above keyboard while sleeping: ${r.view.debugBox().bottom}", r.view.debugBox().bottom <= imeTop)

            // Keyboard closes -> returns to bottom floor
            com.combo.monkeycompanion.fun5.Stage.reportProbeIme(false, 0f)
            r.view.debugFrames(30)
            assertFalse("$id cleared keyboardDodge", r.view.keyboardDodge)
            assertTrue("$id returned to bottom floor: ${r.view.debugBox().bottom}", r.view.debugBox().bottom > 2000f)

            // Path B: Accessibility editable focus / text changed event also triggers dodge
            val a11y = Robolectric.setupService(com.combo.monkeycompanion.agent.SettaiAccessibilityService::class.java)
            a11y.onServiceConnected()
            val ev = android.view.accessibility.AccessibilityEvent.obtain(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED)
            ev.packageName = "com.whatsapp"
            a11y.onAccessibilityEvent(ev)
            r.view.debugFrames(25)
            val defTop = com.combo.monkeycompanion.fun5.Stage.imeTopY(r.svc.screenHeight().toFloat())
            assertTrue("$id dodged on Accessibility typing: bottom=${r.view.debugBox().bottom} <= $defTop", r.view.debugBox().bottom <= defTop)
            a11y.onDestroy()
            com.combo.monkeycompanion.fun5.Stage.resetImeForTest()
            r.svc.onDestroy()
        }
    }
}
