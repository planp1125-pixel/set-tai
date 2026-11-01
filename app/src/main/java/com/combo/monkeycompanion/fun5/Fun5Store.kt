package com.combo.monkeycompanion.fun5

import android.content.Context
import com.combo.monkeycompanion.v5.Bond
import com.combo.monkeycompanion.v5.Friendship
import com.combo.monkeycompanion.v5.Games
import java.time.LocalDate

/** v5 persisted bits: one on/off per big feature, friendship XP, game scores, once-a-day markers. */
object Fun5Store {
    private const val PREFS = "settai_v5"
    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The only v5 settings: one switch per big feature. */
    /** [engagement] = shown in Settings › Engagement (v10.19) instead of the v5 fun card. */
    enum class Feature(val key: String, val title: String, val help: String, val defaultOn: Boolean, val engagement: Boolean = false) {
        MUNCH("munch", "Eats notifications (just for show)", "Now and then grabs a fake banner, munches it and burps a 1-line summary. Real notifications are never touched.", true),
        MOTION("motion", "Shake & tilt", "Shake = dizzy, hard shake = angry, tilt = slides to the low side, face-down = sleep mask. Sensor only while Settai is on screen.", true),
        FESTIVALS("festivals", "Festivals & coffee break", "Festival moods (Pongal, Deepavali, …) and a 4 PM filter-coffee break.", true),
        SLEEP_NAG("sleepnag", "Late-night sleep nag", "After 11:30 PM with the screen on for 10+ minutes: yawns and gently nags. Never in Do Not Disturb.", true),
        WEATHER("weather", "Weather reactions", "Umbrella in rain, fanning in heat, shawl in cold. Checks at most every 3 hours, on unlock.", true),
        // v10.19 Engagement
        HEART("heart", "Friendship heart", "A small faint heart next to him fills up as your friendship grows, and glows when it goes up a level.", true, true),
        HELLO("hello", "Daily hello", "The first time you open your phone each day, he waves and says something about the day.", true, true),
        SULK("sulk", "Streak sulking", "If you skip days and break your streak, he sulks a little (arms folded) until you pat him. One free missed day a week, and after a long break he's just happy you're back.", true, true),
        GOODNIGHT("goodnight", "Goodnight", "After 10 PM he yawns, says goodnight once and curls up to sleep.", true, true),
    }
    fun on(c: Context, f: Feature) = p(c).getBoolean("f_" + f.key, f.defaultOn)
    fun set(c: Context, f: Feature, v: Boolean) = p(c).edit().putBoolean("f_" + f.key, v).apply()

    // ---- friendship ----
    fun friendship(c: Context) = Friendship.State.decode(p(c).getString("friend", null))
    /** Adds XP; returns the new level when it went up. Thread-safe enough (main thread callers). */
    @Synchronized fun addXp(c: Context, src: Friendship.Source): Int? {
        val today = LocalDate.now()
        checkIn(c, today)   // v10.19: the day's first visit is judged before today's XP touches the streak
        val r = Friendship.add(friendship(c), src, today.toString(), today.minusDays(1).toString())
        p(c).edit().putString("friend", r.state.encode()).putString("bond", Bond.warmUp(bond(c), src).encode()).apply()
        r.levelUp?.let { lvl -> android.os.Handler(android.os.Looper.getMainLooper()).post { onLevelUp?.invoke(lvl) } }
        return r.levelUp
    }
    /** The overlay celebrates level-ups (set while it's running). */
    @Volatile var onLevelUp: ((Int) -> Unit)? = null

    // ---- v10.19 bond: warmth, daily check-in, sulk, goodnight (logic in shared v5/Bond.kt) ----
    fun bond(c: Context) = Bond.State.decode(p(c).getString("bond", null))
    fun saveBond(c: Context, s: Bond.State) = p(c).edit().putString("bond", s.encode()).apply()

    /**
     * The first visit of [today] (idempotent): decides hello / grace / sulk / welcome back, keeps the streak on a
     * grace day, and leaves the moment for the overlay to play ([takeMoment]). Returns the moment, or null if done.
     */
    @Synchronized fun checkIn(c: Context, today: LocalDate = LocalDate.now()): Bond.CheckIn? {
        val f = friendship(c)
        val ci = Bond.checkIn(bond(c), today, f.streak, System.currentTimeMillis()) ?: return null
        val e = p(c).edit().putString("bond", ci.state.encode())
            .putString("bond_moment", "${ci.moment.name}|${ci.gapDays}|$today")
        // Grace: pretend yesterday was played so today's first XP continues the streak instead of resetting it.
        if (ci.saveStreak && f.day == today.minusDays(2).toString()) e.putString("friend", f.copy(day = today.minusDays(1).toString()).encode())
        e.apply()
        return ci
    }

    /** Today's moment, once (null if already played or from another day). */
    @Synchronized fun takeMoment(c: Context, today: LocalDate = LocalDate.now()): Pair<Bond.Moment, Int>? {
        val raw = p(c).getString("bond_moment", null) ?: return null
        p(c).edit().remove("bond_moment").apply()
        val parts = raw.split('|')
        if (parts.getOrNull(2) != today.toString()) return null
        val m = runCatching { Bond.Moment.valueOf(parts[0]) }.getOrNull() ?: return null
        return m to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    /** A pat: ends a sulk (true = it just ended). Old sulks fade by themselves. */
    @Synchronized fun patBond(c: Context): Boolean {
        val (s, ended) = Bond.pat(Bond.fade(bond(c), System.currentTimeMillis()))
        saveBond(c, s)
        return ended
    }

    // ---- games ----
    fun scores(c: Context) = Games.Scores.decode(p(c).getString("scores", null))
    fun saveScores(c: Context, s: Games.Scores) = p(c).edit().putString("scores", s.encode()).apply()

    // ---- once-a-day / timestamps ----
    fun mark(c: Context, key: String): String? = p(c).getString("m_$key", null)
    fun setMark(c: Context, key: String, v: String) = p(c).edit().putString("m_$key", v).apply()
    fun time(c: Context, key: String) = p(c).getLong("t_$key", 0L)
    fun setTime(c: Context, key: String, v: Long) = p(c).edit().putLong("t_$key", v).apply()
}
