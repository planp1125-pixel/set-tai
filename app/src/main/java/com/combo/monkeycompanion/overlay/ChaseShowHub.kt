package com.combo.monkeycompanion.overlay

import android.content.Context
import android.os.SystemClock
import com.combo.monkeycompanion.engine.show.ActorStart
import com.combo.monkeycompanion.engine.show.ChaseShow
import com.combo.monkeycompanion.engine.show.Episode
import com.combo.monkeycompanion.engine.show.Snap
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

    /** v10.22 where a real character window is right now (for the drama to start from there). */
    class Who(val start: ActorStart, val scalePx: Float, val floorLineY: Float)
    val where = LinkedHashMap<String, () -> Who?>()
    /** The real characters' size and the floor line (screen px) for the stage. */
    var catScale = 0f; private set
    var ratScale = 0f; private set
    var floorLineY = 0f; private set
    private val finals = HashMap<String, Pair<Float, Float>>()
    /** Where [id] was when the drama ended (x as a screen fraction, facing), so the real window stands there. */
    fun finalOf(id: String): Pair<Float, Float>? = finals[id]

    fun t(now: Long = SystemClock.uptimeMillis()): Float = ((now - startMs) / 1000f).coerceAtLeast(0f)

    fun start(ctx: Context?, onScreen: List<String>): ChaseShow {
        val s = ChaseShow.random(rnd, last)
        last = s; cur = s
        // v10.21: the next episode in turn (saved, so every show is a different story)
        val prefs = ctx?.applicationContext?.getSharedPreferences("settai_show", Context.MODE_PRIVATE)
        val n = prefs?.getInt("next_episode", 0) ?: epCounter
        epCounter = n + 1
        runCatching { prefs?.edit()?.putInt("next_episode", n + 1)?.apply() }
        val off = ActorStart(ActorStart.OFF, 0f, 0f, 1f)
        val c = runCatching { where["billa"]?.invoke() }.getOrNull()
        val r = runCatching { where["chittu"]?.invoke() }.getOrNull()
        catScale = c?.scalePx ?: r?.scalePx ?: 0f
        ratScale = r?.scalePx ?: catScale
        floorLineY = c?.floorLineY ?: r?.floorLineY ?: 0f
        finals.clear()
        curEp = Episodes.build(n, c?.start ?: off, r?.start ?: off)
        // v10.13.1: both characters are drawn on one stage window, so no guest companion window is spawned
        guestSet = emptySet()
        startMs = SystemClock.uptimeMillis() + 250L
        ctx?.let { c -> runCatching { com.combo.monkeycompanion.audio.Sfx.warm(c) } }
        for (w in wakers.values.toList()) runCatching { w() }
        return s
    }

    fun finish(ctx: Context?) {
        if (cur == null) return
        curEp?.let { ep ->
            val tt = t().coerceAtMost(ep.irisAt)
            val s = Snap()
            for ((id, track) in listOf("billa" to Episode.CAT, "chittu" to Episode.RAT)) {
                ep.track(track)?.sample(tt, s)
                if (s.visible && s.x in 0f..1f) finals[id] = s.x to s.face
            }
        }
        cur = null; curEp = null
        ShowStageView.close(ctx)
        val had = guestSet.isNotEmpty(); guestSet = emptySet()
        if (had && ctx != null) runCatching { MonkeyOverlayService.syncCrew(ctx) }
    }
}
