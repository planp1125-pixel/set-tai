package com.combo.monkeycompanion.overlay

import android.content.Context
import android.os.SystemClock
import com.combo.monkeycompanion.engine.show.ChaseShow
import com.combo.monkeycompanion.engine.show.Episode
import com.combo.monkeycompanion.engine.show.Episodes
import kotlin.random.Random

/** v10.13: the one running Billa-vs-Chittu chase show, shared by both overlay windows (main thread). */
object ChaseShowHub {
    /** Separate RNG: never the frame-loop RNG. */
    private val rnd = Random(System.nanoTime())
    @Volatile private var cur: ChaseShow? = null
    /** The running show (null once it is over, even if no window finished it). */
    val show: ChaseShow? get() { cur ?: return null; if (t() > total() + 3f) { cur = null; curEp = null; guestSet = emptySet() }; return cur }
    /** v10.21 Show 2.0: the cartoon episode actually played on the stage. */
    @Volatile private var curEp: Episode? = null
    val episode get() = if (show == null) null else curEp
    fun total() = curEp?.total ?: cur?.total ?: 0f
    fun done(t: Float = t()) = t >= total()
    private var startMs = 0L
    private var epCounter = 0
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
        // v10.21: the next episode in turn (saved, so every show is a different story)
        val prefs = ctx?.applicationContext?.getSharedPreferences("settai_show", Context.MODE_PRIVATE)
        val n = prefs?.getInt("next_episode", 0) ?: epCounter
        epCounter = n + 1
        runCatching { prefs?.edit()?.putInt("next_episode", n + 1)?.apply() }
        curEp = Episodes.byIndex(n)
        // v10.13.1: both characters are drawn on one stage window, so no guest companion window is spawned
        guestSet = emptySet()
        startMs = SystemClock.uptimeMillis() + 250L
        ctx?.let { c -> runCatching { com.combo.monkeycompanion.audio.Sfx.warm(c) } }
        for (w in wakers.values.toList()) runCatching { w() }
        return s
    }

    fun finish(ctx: Context?) {
        if (cur == null) return
        cur = null; curEp = null
        ShowStageView.close(ctx)
        val had = guestSet.isNotEmpty(); guestSet = emptySet()
        if (had && ctx != null) runCatching { MonkeyOverlayService.syncCrew(ctx) }
    }
}
