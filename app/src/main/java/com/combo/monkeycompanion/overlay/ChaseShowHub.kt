package com.combo.monkeycompanion.overlay

import android.content.Context
import android.os.SystemClock
import com.combo.monkeycompanion.engine.show.ChaseShow
import kotlin.random.Random

/** v10.13: the one running Billa-vs-Chittu chase show, shared by both overlay windows (main thread). */
object ChaseShowHub {
    /** Separate RNG: never the frame-loop RNG. */
    private val rnd = Random(System.nanoTime())
    @Volatile private var cur: ChaseShow? = null
    /** The running show (null once it is over, even if no window finished it). */
    val show: ChaseShow? get() { cur ?: return null; if (t() > total() + 3f) { cur = null; curRun = null; guestSet = emptySet() }; return cur }
    /** v10.13.1: the full-screen chase actually played (route + ending + lines). */
    @Volatile private var curRun: com.combo.monkeycompanion.engine.show.ChaseRun? = null
    val run get() = if (show == null) null else curRun
    fun total() = curRun?.total ?: cur?.total ?: 0f
    fun done(t: Float = t()) = t >= total()
    private var startMs = 0L
    private var last: ChaseShow? = null
    /** Characters spawned only for the show (removed again afterwards). */
    @Volatile private var guestSet: Set<String> = emptySet()
    val guests: Set<String> get() = if (show == null) emptySet() else guestSet

    /** Windows by character id: woken (frame loop started) when a show begins. */
    val wakers = LinkedHashMap<String, () -> Unit>()

    fun t(now: Long = SystemClock.uptimeMillis()): Float = ((now - startMs) / 1000f).coerceAtLeast(0f)

    fun start(ctx: Context?, onScreen: List<String>): ChaseShow {
        val s = ChaseShow.random(rnd, last)
        last = s; cur = s
        val dm = ctx?.resources?.displayMetrics
        val aspect = if (dm != null && dm.widthPixels > 0) dm.heightPixels.toFloat() / dm.widthPixels else 2.1f
        curRun = com.combo.monkeycompanion.engine.show.ChaseRun.random(rnd, s.ending, aspect.coerceIn(1.2f, 2.6f))
        // v10.13.1: both characters are drawn on one stage window, so no guest companion window is spawned
        guestSet = emptySet()
        startMs = SystemClock.uptimeMillis() + 150L
        for (w in wakers.values.toList()) runCatching { w() }
        return s
    }

    fun finish(ctx: Context?) {
        if (cur == null) return
        cur = null; curRun = null
        ShowStageView.close(ctx)
        val had = guestSet.isNotEmpty(); guestSet = emptySet()
        if (had && ctx != null) runCatching { MonkeyOverlayService.syncCrew(ctx) }
    }
}
