package com.combo.monkeycompanion.overlay

import android.content.Context
import android.os.SystemClock
import com.combo.monkeycompanion.engine.script.ScriptPlayer
import com.combo.monkeycompanion.engine.script.ShowScript

/**
 * v10.32: the one running scripted show (any cast), played on [ScriptStageView]. Scripts live in
 * files in `assets/shows/` (`.show`). The companion windows stay still and hidden while it plays; a tap stops it.
 */
object ScriptShowHub {
    const val DEFAULT = "shows/satan_vs_angel.show"
    @Volatile var player: ScriptPlayer? = null; private set
    private var startMs = 0L
    val running get() = player != null

    fun load(ctx: Context, asset: String = DEFAULT): ShowScript =
        ShowScript.parse(ctx.assets.open(asset).bufferedReader().use { it.readText() })

    /** Starts [asset] (true = playing). */
    fun start(ctx: Context, asset: String = DEFAULT, aspect: Float = 2.17f): Boolean {
        if (running || ChaseShowHub.show != null) return false
        val sc = runCatching { load(ctx, asset) }.getOrElse { android.util.Log.w("ScriptShow", "bad script", it); return false }
        player = ScriptPlayer(sc, aspect)
        startMs = SystemClock.uptimeMillis()
        if (!ScriptStageView.open(ctx)) { player = null; return false }
        runCatching { com.combo.monkeycompanion.audio.Sfx.warm(ctx) }
        return true
    }

    /** Show seconds since start. */
    fun t(now: Long = SystemClock.uptimeMillis()) = ((now - startMs) / 1000f).coerceAtLeast(0f)

    fun finish(ctx: Context?) {
        if (player == null) return
        player = null
        ScriptStageView.close(ctx)
    }
}
