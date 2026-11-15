package com.combo.monkeycompanion.overlay

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.os.SystemClock
import android.provider.Settings
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.character.Locomotion
import com.combo.monkeycompanion.engine.game.SlingGame

/**
 * v10.40: the one running Slingshot game. The user starts it (long-press menu › Games, or the Home tile); it never
 * starts by itself. While it runs the live companion windows are hidden and paused, exactly like Screen Smash.
 */
object SlingGameHub {
    private const val PREFS = "sling_game"
    val running get() = SlingGameView.showing
    private var screenOff: BroadcastReceiver? = null

    /** A fresh round with [bulletId] (default: the character in use) as the stone and every other character as targets. */
    fun newGame(ctx: Context, bulletId: String? = null, stage: Int = level(ctx)): SlingGame? {
        val all = CharacterRegistry.allWithCustom
        val stone = bulletId?.takeIf { id -> all.any { it.id == id } } ?: CharacterRegistry.current.id
        val dm = ctx.resources.displayMetrics
        val aspect = (dm.heightPixels.toFloat() / dm.widthPixels.coerceAtLeast(1)).coerceIn(1.2f, 2.6f)
        val fliers = all.filter { it.can(Locomotion.FLY) || it.can(Locomotion.FLOAT) }.map { it.id }.toSet()
        return SlingGame(aspect, stone, all.map { it.id }, fliers, seed = SystemClock.uptimeMillis(), tricks = all.associate { it.id to trickFor(it) },
            stage = stage.coerceIn(1, SlingGame.MAX_STAGE))
    }

    /** v10.41 each character's own escape (Studio characters hop). */
    fun trickFor(c: com.combo.monkeycompanion.character.Character): SlingGame.Trick = when (c.id) {
        "devil", "angel", "hollowman", "mask" -> SlingGame.Trick.VANISH
        "superboy", "armour", "rocketgirl" -> SlingGame.Trick.FLY_UP
        "chittu", "billa", "skater", "surfer" -> SlingGame.Trick.DASH
        "gajju" -> SlingGame.Trick.NONE
        else -> SlingGame.Trick.HOP
    }

    /**
     * Starts a round. null = playing; otherwise a plain message saying why not.
     * [deferHide] (hold-and-pull start): the live character's window must stay until the finger lifts, because it is
     * still sending that finger to the game; the caller then calls [hideCompanionsNow].
     */
    fun start(ctx: Context, bulletId: String? = null, deferHide: Boolean = false): String? {
        val app = ctx.applicationContext
        if (running) return null
        if (!Settings.canDrawOverlays(app)) return "Allow \"Display over other apps\" first"
        if (ScriptShowHub.running || ChaseShowHub.show != null) return "A show is playing. Try again when it ends."
        val game = newGame(app, bulletId) ?: return "Couldn't start the game"
        lastFinish = null
        if (!SlingGameView.open(app, game)) return "The game couldn't open (is Set-Tai allowed to draw over apps?)"
        if (!deferHide) hideCompanions(app, true)
        runCatching { com.combo.monkeycompanion.audio.Sfx.warm(app) }
        runCatching {
            val r = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { if (i.action == Intent.ACTION_SCREEN_OFF) finish(c, "screen off") } }
            app.registerReceiver(r, IntentFilter(Intent.ACTION_SCREEN_OFF)); screenOff = r
        }
        return null
    }

    /** Why the last game closed (for tests and bug reports). */
    @Volatile var lastFinish: String? = null; private set

    fun finish(ctx: Context?, why: String = "asked") {
        if (!running) return
        lastFinish = why
        val app = (ctx ?: SlingGameView.current?.context)?.applicationContext
        SlingGameView.close(app)
        screenOff?.let { r -> runCatching { app?.unregisterReceiver(r) } }; screenOff = null
        if (app != null) hideCompanions(app, false)
    }

    fun hideCompanionsNow(ctx: Context) { if (running) hideCompanions(ctx.applicationContext, true) }

    private fun hideCompanions(app: Context, hide: Boolean) {
        if (!MonkeyOverlayService.running) return
        runCatching {
            app.startService(Intent(app, MonkeyOverlayService::class.java)
                .setAction(if (hide) MonkeyOverlayService.ACTION_SMASH_HIDE else MonkeyOverlayService.ACTION_SMASH_SHOW))
        }
    }

    // ---------------- v10.44 saved levels ----------------
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    /** The highest level the player may play (1 at first; one star on a level opens the next). */
    fun unlocked(ctx: Context): Int = prefs(ctx).getInt("unlocked", 1).coerceIn(1, SlingGame.MAX_STAGE)
    /** The level the next round is played on (any unlocked one; the player can go back to an earlier level). */
    fun level(ctx: Context): Int = prefs(ctx).getInt("level", unlocked(ctx)).coerceIn(1, unlocked(ctx))
    fun setLevel(ctx: Context, n: Int): Int { val v = n.coerceIn(1, unlocked(ctx)); prefs(ctx).edit().putInt("level", v).apply(); return v }
    /** Best stars (0..3) and best hits ever on [level]. */
    fun stars(ctx: Context, level: Int): Int = prefs(ctx).getInt("stars_$level", 0)
    fun bestOn(ctx: Context, level: Int): Int = prefs(ctx).getInt("best_$level", 0)

    /** What a finished round earned. [unlockedNow] = the level it has just opened (null = none). */
    class RoundResult(val stars: Int, val bestStars: Int, val unlockedNow: Int?)

    /** Saves a finished round on [level]: best hits, best stars, and opens the next level at one star. */
    fun finishRound(ctx: Context, level: Int, score: Int): RoundResult {
        val p = prefs(ctx)
        val st = SlingGame.starsFor(score)
        val bestStars = maxOf(st, p.getInt("stars_$level", 0))
        val e = p.edit().putInt("stars_$level", bestStars).putInt("best_$level", maxOf(score, p.getInt("best_$level", 0)))
        var opened: Int? = null
        if (st >= 1 && level >= unlocked(ctx) && level < SlingGame.MAX_STAGE) { opened = level + 1; e.putInt("unlocked", opened) }
        e.apply()
        return RoundResult(st, bestStars, opened)
    }

    /** Saves [score] for today. Returns (today's best, this round is a new best). */
    fun record(ctx: Context, score: Int, day: Long = System.currentTimeMillis() / 86_400_000L): Pair<Int, Boolean> {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = if (p.getLong("day", -1L) == day) p.getInt("best", 0) else 0
        val isNew = score > old
        if (isNew || p.getLong("day", -1L) != day) p.edit().putLong("day", day).putInt("best", maxOf(old, score)).putInt("ever", maxOf(p.getInt("ever", 0), score)).apply()
        return maxOf(old, score) to isNew
    }

    fun bestToday(ctx: Context, day: Long = System.currentTimeMillis() / 86_400_000L): Int {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (p.getLong("day", -1L) == day) p.getInt("best", 0) else 0
    }
}
