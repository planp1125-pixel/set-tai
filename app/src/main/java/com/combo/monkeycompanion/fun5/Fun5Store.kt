package com.combo.monkeycompanion.fun5

import android.content.Context
import com.combo.monkeycompanion.v5.Friendship
import com.combo.monkeycompanion.v5.Games
import java.time.LocalDate

/** v5 persisted bits: one on/off per big feature, friendship XP, game scores, once-a-day markers. */
object Fun5Store {
    private const val PREFS = "settai_v5"
    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The only v5 settings: one switch per big feature. */
    enum class Feature(val key: String, val title: String, val help: String, val defaultOn: Boolean) {
        MUNCH("munch", "Eats notifications (just for show)", "Now and then grabs a fake banner, munches it and burps a 1-line summary. Real notifications are never touched.", true),
        MOTION("motion", "Shake & tilt", "Shake = dizzy, hard shake = angry, tilt = slides to the low side, face-down = sleep mask. Sensor only while Settai is on screen.", true),
        FESTIVALS("festivals", "Festivals & coffee break", "Festival moods (Pongal, Deepavali, …) and a 4 PM filter-coffee break.", true),
        SLEEP_NAG("sleepnag", "Late-night sleep nag", "After 11:30 PM with the screen on for 10+ minutes: yawns and gently nags. Never in Do Not Disturb.", true),
        WEATHER("weather", "Weather reactions", "Umbrella in rain, fanning in heat, shawl in cold. Checks at most every 3 hours, on unlock.", true),
    }
    fun on(c: Context, f: Feature) = p(c).getBoolean("f_" + f.key, f.defaultOn)
    fun set(c: Context, f: Feature, v: Boolean) = p(c).edit().putBoolean("f_" + f.key, v).apply()

    // ---- friendship ----
    fun friendship(c: Context) = Friendship.State.decode(p(c).getString("friend", null))
    /** Adds XP; returns the new level when it went up. Thread-safe enough (main thread callers). */
    @Synchronized fun addXp(c: Context, src: Friendship.Source): Int? {
        val today = LocalDate.now()
        val r = Friendship.add(friendship(c), src, today.toString(), today.minusDays(1).toString())
        p(c).edit().putString("friend", r.state.encode()).apply()
        r.levelUp?.let { lvl -> android.os.Handler(android.os.Looper.getMainLooper()).post { onLevelUp?.invoke(lvl) } }
        return r.levelUp
    }
    /** The overlay celebrates level-ups (set while it's running). */
    @Volatile var onLevelUp: ((Int) -> Unit)? = null

    // ---- games ----
    fun scores(c: Context) = Games.Scores.decode(p(c).getString("scores", null))
    fun saveScores(c: Context, s: Games.Scores) = p(c).edit().putString("scores", s.encode()).apply()

    // ---- once-a-day / timestamps ----
    fun mark(c: Context, key: String): String? = p(c).getString("m_$key", null)
    fun setMark(c: Context, key: String, v: String) = p(c).edit().putString("m_$key", v).apply()
    fun time(c: Context, key: String) = p(c).getLong("t_$key", 0L)
    fun setTime(c: Context, key: String, v: Long) = p(c).edit().putLong("t_$key", v).apply()
}
