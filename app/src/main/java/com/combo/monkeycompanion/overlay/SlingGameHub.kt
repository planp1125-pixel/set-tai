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
    fun newGame(ctx: Context, bulletId: String? = null): SlingGame? {
        val all = CharacterRegistry.allWithCustom
        val stone = bulletId?.takeIf { id -> all.any { it.id == id } } ?: CharacterRegistry.current.id
        val dm = ctx.resources.displayMetrics
        val aspect = (dm.heightPixels.toFloat() / dm.widthPixels.coerceAtLeast(1)).coerceIn(1.2f, 2.6f)
        val fliers = all.filter { it.can(Locomotion.FLY) || it.can(Locomotion.FLOAT) }.map { it.id }.toSet()
        return SlingGame(aspect, stone, all.map { it.id }, fliers, seed = SystemClock.uptimeMillis())
    }

    /** Starts a round. null = playing; otherwise a plain message saying why not. */
    fun start(ctx: Context, bulletId: String? = null): String? {
        val app = ctx.applicationContext
        if (running) return null
        if (!Settings.canDrawOverlays(app)) return "Allow \"Display over other apps\" first"
        if (ScriptShowHub.running || ChaseShowHub.show != null) return "A show is playing. Try again when it ends."
        val game = newGame(app, bulletId) ?: return "Couldn't start the game"
        if (!SlingGameView.open(app, game)) return "The game couldn't open (is Set-Tai allowed to draw over apps?)"
        hideCompanions(app, true)
        runCatching { com.combo.monkeycompanion.audio.Sfx.warm(app) }
        runCatching {
            val r = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { finish(c) } }
            app.registerReceiver(r, IntentFilter(Intent.ACTION_SCREEN_OFF)); screenOff = r
        }
        return null
    }

    fun finish(ctx: Context?) {
        if (!running) return
        val app = (ctx ?: SlingGameView.current?.context)?.applicationContext
        SlingGameView.close(app)
        screenOff?.let { r -> runCatching { app?.unregisterReceiver(r) } }; screenOff = null
        if (app != null) hideCompanions(app, false)
    }

    private fun hideCompanions(app: Context, hide: Boolean) {
        if (!MonkeyOverlayService.running) return
        runCatching {
            app.startService(Intent(app, MonkeyOverlayService::class.java)
                .setAction(if (hide) MonkeyOverlayService.ACTION_SMASH_HIDE else MonkeyOverlayService.ACTION_SMASH_SHOW))
        }
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
