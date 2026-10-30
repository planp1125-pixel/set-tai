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
    val show: ChaseShow? get() { val s = cur ?: return null; if (t() > s.total + 3f) { cur = null; guestSet = emptySet() }; return cur }
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
        guestSet = setOf("billa", "chittu") - onScreen.toSet()
        startMs = SystemClock.uptimeMillis() + if (guestSet.isEmpty()) 150L else 700L
        if (guestSet.isNotEmpty() && ctx != null) runCatching { MonkeyOverlayService.syncCrew(ctx) }
        for (w in wakers.values.toList()) runCatching { w() }
        return s
    }

    fun finish(ctx: Context?) {
        if (cur == null) return
        cur = null
        val had = guestSet.isNotEmpty(); guestSet = emptySet()
        if (had && ctx != null) runCatching { MonkeyOverlayService.syncCrew(ctx) }
    }
}
