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
    fun start(ctx: Context, asset: String = DEFAULT, aspect: Float = 2.17f): Boolean = startResult(ctx, asset = asset, aspect = aspect) == null

    /** v10.34: start from raw script text (user shows). */
    fun startText(ctx: Context, text: String, aspect: Float = 2.17f): Boolean = startResult(ctx, text = text, aspect = aspect) == null

    /** Starts a show from [asset] or [text]. null = playing; otherwise a plain message (bad script: with its line number). */
    fun startResult(ctx: Context, asset: String? = null, text: String? = null, aspect: Float = 2.17f): String? {
        if (running) return "A show is already playing"
        if (ChaseShowHub.show != null) return "The chase show is playing"
        val sc = try {
            ShowScript.parse(text ?: ctx.assets.open(asset ?: DEFAULT).bufferedReader().use { it.readText() })
        } catch (e: com.combo.monkeycompanion.engine.script.ScriptError) { return UserShows.message(e) }
        catch (e: Exception) { return "Couldn't read the show: ${e.message}" }
        val pl = try { ScriptPlayer(sc, aspect) } catch (e: com.combo.monkeycompanion.engine.script.ScriptError) { return UserShows.message(e) }
        player = pl
        startMs = SystemClock.uptimeMillis()
        if (!ScriptStageView.open(ctx.applicationContext)) { player = null; return "The show stage couldn't open (is Set-Tai allowed to draw over apps?)" }
        runCatching { com.combo.monkeycompanion.audio.Sfx.warm(ctx) }
        return null
    }

    /** Show seconds since start. */
    fun t(now: Long = SystemClock.uptimeMillis()) = ((now - startMs) / 1000f).coerceAtLeast(0f)

    fun finish(ctx: Context?) {
        if (player == null) return
        player = null
        ScriptStageView.close(ctx)
    }
}
