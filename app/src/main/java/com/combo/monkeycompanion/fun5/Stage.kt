package com.combo.monkeycompanion.fun5

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.combo.monkeycompanion.agent.QuickChip
import com.combo.monkeycompanion.brain.Gag
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.character.Character
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.character.Wear
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.Animations
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Particles
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.v5.BananaCatch
import com.combo.monkeycompanion.v5.CoffeeBreak
import com.combo.monkeycompanion.v5.Festivals
import com.combo.monkeycompanion.v5.Friendship
import com.combo.monkeycompanion.v5.Games
import com.combo.monkeycompanion.v5.MotionDetector
import com.combo.monkeycompanion.v5.NotifMunch
import com.combo.monkeycompanion.v5.PairProtocol
import com.combo.monkeycompanion.v5.Rps
import com.combo.monkeycompanion.v5.SleepNag
import com.combo.monkeycompanion.v5.StaringContest
import com.combo.monkeycompanion.v5.WeatherMood
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

/** What the v5 stage needs from the overlay (MonkeyView implements it). All coordinates in screen pixels. */
interface StageHost {
    val ctx: Context
    val density: Float
    val character: Character
    val wear: Wear?
    val bodyScale: Float
    fun screenW(): Float
    fun screenH(): Float
    fun headTop(out: FloatArray)
    fun feet(out: FloatArray)
    fun say(text: String, ms: Long)
    /** v10.17: instructions / game rules: shown even when the AI speaks for the character. */
    fun sayInfo(text: String, ms: Long) = say(text, ms)
    fun chips(text: String, chips: List<QuickChip>)
    fun play(s: MonkeyState, secs: Float)
    fun gag(g: Gag): Boolean
    fun dance()
    fun particles(): Particles
    fun wake()
    /** Free to do a bit now: on screen, not hidden / flying / dragged / typing / leaving / asleep. */
    fun free(): Boolean
    /** DND, quiet hours, a call or fullscreen. */
    fun quiet(): Boolean
    fun dnd(): Boolean
    fun saver(): Boolean
    fun visible(): Boolean
    fun floorX(frac: Float)
    fun charX(): Float
    fun slideToEdge(side: Int)
    fun sleepNow(on: Boolean)
    fun leaveBy(side: Int)
    fun comeBack()
    fun log(what: String, result: String)
}

/**
 * v5 features on the overlay: eats notifications, shake / tilt / face-down, festival moods, coffee break, sleep nag,
 * weather props, mini games, friendship level-ups, the visiting character from a paired phone. Event-driven: one
 * Handler timer per feature, the sensor only while visible; nothing runs when it's all idle.
 */
class Stage(private val h: StageHost) : PairHub.Listener {
    companion object {
        @Volatile var current: Stage? = null
        @Volatile private var probeIme = false
        @Volatile private var probeTopY = 0f
        @Volatile private var a11yIme = false
        @Volatile private var a11yTopY = 0f
        @Volatile var onImeChanged: (() -> Unit)? = null

        /** True whenever Path A (overlay probe/insets) or Path B (Accessibility IME window/editable focus) detects the soft keyboard. */
        val imeVisible: Boolean get() = probeIme || a11yIme

        /** Top Y of the soft keyboard in screen px (defaults to 55% of screen height when IME is open but exact top isn't reported). */
        fun imeTopY(screenH: Float = 2340f): Float {
            val raw = when {
                probeIme && probeTopY > 0f -> probeTopY
                a11yIme && a11yTopY > 0f -> a11yTopY
                else -> 0f
            }
            return if (raw in (screenH * 0.15f)..(screenH * 0.90f)) raw else screenH * 0.55f
        }

        val imeTopY: Float get() = imeTopY(2340f)

        fun reportProbeIme(visible: Boolean, topY: Float) {
            val was = imeVisible
            val oldTop = if (probeIme) probeTopY else 0f
            probeIme = visible; probeTopY = if (visible) topY else 0f
            if (imeVisible != was || kotlin.math.abs(oldTop - probeTopY) > 8f) onImeChanged?.invoke()
        }

        fun reportA11yIme(visible: Boolean, topY: Float) {
            val was = imeVisible
            val oldTop = if (a11yIme) a11yTopY else 0f
            a11yIme = visible; a11yTopY = if (visible) topY else 0f
            if (imeVisible != was || kotlin.math.abs(oldTop - a11yTopY) > 8f) onImeChanged?.invoke()
        }

        fun resetImeForTest() {
            probeIme = false; probeTopY = 0f; a11yIme = false; a11yTopY = 0f
        }
    }
    private val ui = Handler(Looper.getMainLooper())
    private val c get() = h.ctx
    private val d get() = h.density
    private var t = 0f
    private val tmpA = FloatArray(2)
    private val tmpB = FloatArray(2)

    // ---------------- props ----------------
    private enum class PropKind { NONE, FESTIVAL, WEATHER, COFFEE, CROWN, GARLAND }
    private var prop = PropKind.NONE
    private var propUntil = 0f
    private var festival: Festivals.Kind? = null
    private var weather = WeatherMood.Mood.NONE
    private var weatherTemp = 0f
    private var dizzyUntil = 0f
    private var faceDownMask = false
    private fun showProp(k: PropKind, secs: Float) { prop = k; propUntil = t + secs; h.wake() }

    // ---------------- munch ----------------
    private var munchT = -1f
    private var munchTitle = ""
    private var munchSummary = ""
    private var munchDone: (() -> Unit)? = null

    // ---------------- games ----------------
    private var banana: BananaCatch? = null
    private var stare: StaringContest? = null
    private var bananaTarget = 0.5f
    private var fingerAt = 0L
    val playing get() = banana != null || stare != null

    // ---------------- visitor (paired phone) ----------------
    private var visitor: Visitor? = null

    val active get() = munchT >= 0f || playing || visitor != null || dizzyUntil > t || faceDownMask || (prop != PropKind.NONE && propUntil > t)
    /** Hold the character still (games, munching, face-down). */
    val holdsBody get() = munchT in 0f..NotifMunch.SECS || banana != null || faceDownMask

    private val motion = MotionSensor(c) { onMotion(it) }
    private var live = false

    // ======================= lifecycle =======================
    fun start() {
        if (live) return
        live = true
        PairHub.add(this)
        current = this
        Fun5Store.onLevelUp = { celebrate(it) }
        scheduleCoffee()
        ui.postDelayed({ festivalCheck(); weatherCheck() }, 4000L)
        resumeNag()
    }
    fun stop() {
        if (!live) return
        live = false
        PairHub.remove(this)
        if (current === this) current = null
        Fun5Store.onLevelUp = null
        ui.removeCallbacksAndMessages(null)
        motion.setActive(false)
        endGames(false)
    }
    /** The overlay tells us when it's visible on screen (not hidden / resting / paused / screen off). */
    fun setVisible(v: Boolean) {
        motion.setActive(v && live && Fun5Store.on(c, Fun5Store.Feature.MOTION))
        if (!v && faceDownMask) faceDownMask = false
    }
    val sensorOn get() = motion.active

    /** Phone unlocked (the overlay forwards it). */
    fun onUnlock() { festivalCheck(); weatherCheck() }
    val staring get() = stare != null

    // ======================= frame =======================
    fun step(dt: Float) {
        t += dt
        if (prop != PropKind.NONE && t > propUntil) prop = PropKind.NONE
        stepMunch(dt)
        stepGames(dt)
        visitor?.let { v -> if (!v.step(dt)) visitor = null }
    }

    /** [front] = drawn over the body (masks, props); else behind (banners, bananas, the visitor). Screen space. */
    fun draw(cv: Canvas, front: Boolean) {
        h.headTop(tmpA); h.feet(tmpB)
        val s = h.bodyScale
        val hx = tmpA[0]; val hy = tmpA[1]
        if (!front) {
            if (munchT >= 0f) drawMunch(cv)
            banana?.let { drawBananas(cv, it) }
            visitor?.draw(cv)
            if (prop == PropKind.WEATHER && (weather == WeatherMood.Mood.RAIN || weather == WeatherMood.Mood.STORM)) Props5.weather(cv, weather, hx, hy, tmpB[0], tmpB[1], s, t)
            return
        }
        when (prop) {
            PropKind.FESTIVAL -> festival?.let { Props5.festival(cv, it.prop, hx, hy, tmpB[0], tmpB[1], s, t) }
            PropKind.WEATHER -> if (weather != WeatherMood.Mood.RAIN && weather != WeatherMood.Mood.STORM) Props5.weather(cv, weather, hx, hy, tmpB[0], tmpB[1], s, t)
            PropKind.COFFEE -> Props5.coffee(cv, hx, hy, s, t)
            PropKind.CROWN -> Props5.crown(cv, hx, hy + 4 * s, s)
            PropKind.GARLAND -> Props5.garland(cv, hx, hy + 80 * s, s)
            PropKind.NONE -> Unit
        }
        if (dizzyUntil > t) Props5.dizzy(cv, hx, hy, s, t)
        if (faceDownMask) Props5.sleepMask(cv, hx, hy, s)
        stare?.let { Props5.stare(cv, hx, hy, s, it.watering, t) }
        if (banana != null || stare != null) Props5.hud(cv, h.screenW(), 40 * d, hudLine(), d)
    }

    // ======================= 1. eats notifications =======================
    /** A pipe is about to show: maybe munch a fake banner first. True = [then] runs after the munch. */
    fun tryMunch(title: String, summary: String, then: () -> Unit): Boolean {
        if (!Fun5Store.on(c, Fun5Store.Feature.MUNCH) || !h.free() || playing || munchT >= 0f) return false
        val now = System.currentTimeMillis()
        if (!NotifMunch.should(now, Fun5Store.time(c, "munch"), h.saver())) return false
        Fun5Store.setTime(c, "munch", now)
        munchT = 0f; munchTitle = title; munchSummary = summary; munchDone = then
        h.play(MonkeyState.PLAYFUL, NotifMunch.SECS)
        h.wake()
        return true
    }
    private var burped = false
    private fun stepMunch(dt: Float) {
        if (munchT < 0f) return
        val before = munchT
        munchT += dt
        if (before < 0.8f && munchT >= 0.8f) { h.play(MonkeyState.GIGGLE, 1.6f); burped = false }
        if (NotifMunch.phase(munchT) == NotifMunch.Phase.MUNCH && ((munchT * 6).toInt() != (before * 6).toInt())) {
            h.headTop(tmpA); h.particles().spawn(Particles.CONFETTI, tmpA[0] + 24 * d, tmpA[1] + 40 * d, (Random.nextFloat() - 0.5f) * 120 * d, -80 * d, 0.5f, 3 * d)
        }
        if (!burped && munchT >= 2.4f) { burped = true; h.say(NotifMunch.burp(munchSummary, h.character.id), 2600L) }
        if (munchT >= NotifMunch.SECS) { munchT = -1f; val f = munchDone; munchDone = null; ui.postDelayed({ f?.invoke() }, 900L) }
    }
    private fun drawMunch(cv: Canvas) {
        h.headTop(tmpA)
        val w = 200 * d; val bh = 44 * d
        val k = (munchT / 0.8f).coerceIn(0f, 1f)
        val tx = tmpA[0] + 6 * d; val ty = tmpA[1] + 38 * d - bh / 2
        val sx = h.screenW() / 2 - w / 2; val sy = 30 * d
        val x = sx + (tx - sx) * k; val y = sy + (ty - sy) * k
        Props5.banner(cv, x, y, w * (1f - 0.4f * k), bh * (1f - 0.3f * k), munchTitle, NotifMunch.cardLeft(munchT), d)
    }

    // ======================= 2. shake / tilt / face-down =======================
    private fun onMotion(e: MotionDetector.Event) {
        if (banana != null) return   // tilt steers the game instead
        when (e) {
            MotionDetector.Event.FACE_DOWN -> { faceDownMask = true; h.sleepNow(true); h.wake() }
            MotionDetector.Event.FACE_UP -> if (faceDownMask) { faceDownMask = false; h.sleepNow(false); h.say(listOf("Huh? Morning already? 😴", "Who turned the lights on?!").random(), 2500L) }
            else -> {
                if (!h.free() || faceDownMask) return
                when (e) {
                    MotionDetector.Event.SHAKE -> { dizzyUntil = t + 3.2f; com.combo.monkeycompanion.ai.BodySense.record("you shook the phone", com.combo.monkeycompanion.ai.BodySense.Feeling.DIZZY); h.play(MonkeyState.STARTLED, 1.4f); h.say(shakeLine(false), 2400L); h.wake(); com.combo.monkeycompanion.mind.MindHub.event(com.combo.monkeycompanion.mind.MindEvent.SHAKE) }
                    MotionDetector.Event.HARD_SHAKE -> { dizzyUntil = t + 2f; com.combo.monkeycompanion.ai.BodySense.record("you shook the phone hard", com.combo.monkeycompanion.ai.BodySense.Feeling.DIZZY); h.play(MonkeyState.ANGRY, 2.4f); h.say(shakeLine(true), 2600L); h.wake(); com.combo.monkeycompanion.mind.MindHub.event(com.combo.monkeycompanion.mind.MindEvent.HARD_SHAKE) }
                    MotionDetector.Event.TILT_LEFT -> h.slideToEdge(-1)
                    MotionDetector.Event.TILT_RIGHT -> h.slideToEdge(1)
                    else -> Unit
                }
            }
        }
    }
    private fun shakeLine(hard: Boolean) = when (h.character.id) {
        "superboy" -> if (hard) "HEY! Not cool, citizen! 😠" else "Whoa… the world is spinning ⭐"
        "armour" -> if (hard) "Suit stabilisers OVERLOADED! 😤" else "Gyros wobbling… 🌀"
        "mask" -> if (hard) "Mask almost fell off! Stop it! 😠" else "Everything's… doubled… ⭐⭐"
        else -> if (hard) "Dei! Stop shaking me! 😡" else listOf("Wheee— I'm dizzy 😵‍💫", "Stars… I see stars ⭐").random()
    }

    // ======================= 3. festivals, coffee =======================
    private fun festivalCheck() {
        if (!Fun5Store.on(c, Fun5Store.Feature.FESTIVALS)) return
        val today = LocalDate.now()
        val f = Festivals.on(today) ?: return
        if (Fun5Store.mark(c, "festival") == today.toString()) return
        if (!h.free() || h.quiet()) { ui.postDelayed({ festivalCheck() }, 15 * 60_000L); return }
        Fun5Store.setMark(c, "festival", today.toString())
        festival = f.kind
        showProp(PropKind.FESTIVAL, if (h.saver()) 8f else 16f)
        h.say(Festivals.lines(f.kind, h.character.id).random(), 4200L)
        if (f.kind == Festivals.Kind.DEEPAVALI || f.kind == Festivals.Kind.DIWALI || f.kind == Festivals.Kind.NEW_YEAR) burst(Particles.SPARKLE, 18)
        else h.dance()
        h.log("Festival: ${f.kind.title}", if (f.approximate) "date approximate (moon sighting)" else "shown")
        com.combo.monkeycompanion.mind.MindHub.event(com.combo.monkeycompanion.mind.MindEvent.FESTIVAL, f.kind.title)
    }
    /** Festival of the day (for the preview / clips). */
    fun festivalToday() = Festivals.on(LocalDate.now())

    private fun scheduleCoffee() {
        val n = LocalTime.now()
        ui.postDelayed(coffee, CoffeeBreak.msUntilNext(n.hour * 60 + n.minute, n.second) + 1000L)
    }
    private val coffee = Runnable {
        val n = LocalTime.now(); val today = LocalDate.now().toString()
        if (Fun5Store.on(c, Fun5Store.Feature.FESTIVALS) &&
            CoffeeBreak.due(n.hour * 60 + n.minute, today, Fun5Store.mark(c, "coffee"), h.quiet() || !h.free() || playing)) {
            Fun5Store.setMark(c, "coffee", today)
            showProp(PropKind.COFFEE, 12f); h.play(MonkeyState.HAPPY_PAT, 2f)
            h.say(CoffeeBreak.line(h.character.id), 4000L)
        } else if (Fun5Store.mark(c, "coffee") != today && n.hour == 16 && n.minute < 40) { ui.postDelayed({ coffeeRetry() }, 5 * 60_000L) }
        scheduleCoffee()
    }
    private fun coffeeRetry() { ui.removeCallbacks(coffee); coffee.run() }

    // ======================= 6. sleep nag =======================
    private var screenOnSince = SystemClock.elapsedRealtime()
    private var lastNagAt = 0L
    private var lastNagLevel = 0
    private var snoozedUntil = 0L
    /** Screen came on. */
    fun screenOn() {
        screenOnSince = SystemClock.elapsedRealtime()
        runCatching { com.combo.monkeycompanion.agent.ActivityScheduler.saveScreenOnSince(c, screenOnSince) }
        ui.removeCallbacks(nagCheck)
        scheduleNag()
    }
    /** v10.9: Settai (re)started, e.g. a character switch or Duo/Trio change: keep the screen-on time, snooze and last nag. */
    private fun resumeNag() {
        runCatching {
            val (snz, last, lvl) = com.combo.monkeycompanion.agent.ActivityScheduler.loadSleepNag(c)
            if (snz > snoozedUntil) snoozedUntil = snz
            if (last > lastNagAt) { lastNagAt = last; lastNagLevel = lvl }
            val since = com.combo.monkeycompanion.agent.ActivityScheduler.loadScreenOnSince(c)
            if (since > 0L && since <= SystemClock.elapsedRealtime()) screenOnSince = since
            else com.combo.monkeycompanion.agent.ActivityScheduler.saveScreenOnSince(c, screenOnSince)
        }
        ui.removeCallbacks(nagCheck)
        scheduleNag()
    }
    private fun saveNag() = runCatching { com.combo.monkeycompanion.agent.ActivityScheduler.saveSleepNag(c, snoozedUntil, lastNagAt, lastNagLevel) }
    fun screenOff() {
        ui.removeCallbacks(nagCheck)
        runCatching { com.combo.monkeycompanion.agent.ActivityScheduler.saveScreenOnSince(c, 0L) }
    }
    private fun scheduleNag() {
        if (!live) return
        val n = LocalTime.now()
        ui.postDelayed(nagCheck, SleepNag.nextCheckMs(n.hour * 60 + n.minute, SystemClock.elapsedRealtime() - screenOnSince))
    }
    private val nagCheck = Runnable {
        val n = LocalTime.now(); val now = SystemClock.elapsedRealtime()
        if (Fun5Store.on(c, Fun5Store.Feature.SLEEP_NAG)) {
            val lvl = SleepNag.level(now, n.hour * 60 + n.minute, screenOnSince, lastNagAt, lastNagLevel, snoozedUntil, h.dnd())
            if (lvl > 0 && h.free() && !playing) {
                lastNagAt = now; lastNagLevel = lvl; saveNag()
                h.play(if (lvl >= 3) MonkeyState.SAD else MonkeyState.SLEEP, if (lvl >= 3) 2.5f else 3.5f)
                h.chips(SleepNag.line(lvl, h.character.id), listOf(QuickChip("v5", "😴 Okay, sleeping", "nag_ok"), QuickChip("v5", "⏰ Snooze 20 min", "nag_snooze")))
            }
        }
        scheduleNag()
    }

    // ======================= 7. weather =======================
    private fun weatherCheck() {
        if (!Fun5Store.on(c, Fun5Store.Feature.WEATHER)) return
        val now = System.currentTimeMillis()
        if (!WeatherMood.due(now, Fun5Store.time(c, "weather"))) return
        val loc = lastLocation() ?: return
        Fun5Store.setTime(c, "weather", now)
        Thread {
            val r = runCatching { WeatherMood.parse(com.combo.monkeycompanion.agent.Web.get(WeatherMood.url(loc.first, loc.second))) }.getOrNull() ?: return@Thread
            ui.post { onWeather(WeatherMood.mood(r.first, r.second), r.second) }
        }.start()
    }
    fun onWeather(m: WeatherMood.Mood, temp: Float) {
        weather = m; weatherTemp = temp
        val line = WeatherMood.line(m, temp, h.character.id) ?: return
        if (!h.free() || h.quiet() || playing) return
        showProp(PropKind.WEATHER, if (h.saver()) 6f else 12f)
        if (m == WeatherMood.Mood.COLD) h.play(MonkeyState.STARTLED, 1.2f)
        h.say(line, 3800L)
        h.log("Weather: ${m.name.lowercase()}", "${temp.toInt()}°C")
    }
    private fun lastLocation(): Pair<Double, Double>? = runCatching {
        if (c.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return null
        val lm = c.getSystemService(android.location.LocationManager::class.java)
        listOf(android.location.LocationManager.NETWORK_PROVIDER, android.location.LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }.maxByOrNull { it.time }?.let { it.latitude to it.longitude }
    }.getOrNull()

    // ======================= 4. games =======================
    fun showGames() {
        val sc = Fun5Store.scores(c)
        h.chips("Let's play! 🎮 Best bananas: ${sc["banana"]} · Stare wins: ${sc["stare_w"]} · RPS ${sc["rps_w"]}-${sc["rps_l"]}",
            Games.Game.entries.map { QuickChip("v5", "${it.icon} ${it.title}", "game_" + it.id) })
    }
    fun startGame(g: Games.Game) {
        endGames(false)
        when (g) {
            Games.Game.BANANA -> { banana = BananaCatch(); bananaTarget = h.charX(); h.sayInfo(if (motion.active) "Catch the bananas! Tilt the phone or drag me 🍌" else "Catch the bananas! Drag me left and right 🍌", 2500L) }
            Games.Game.STARE -> {
                val lvl = Friendship.level(Fun5Store.friendship(c).xp)
                stare = StaringContest(Random, 4f, 10f + lvl.coerceAtMost(8))
                h.sayInfo("Staring contest! 👀 Tap me the moment YOU blink. Go!", 2600L)
            }
            Games.Game.RPS -> h.chips("Rock, paper, scissors… shoot! ✊✋✌️", Rps.Hand.entries.map { QuickChip("v5", "${it.icon} ${it.label}", "rps_" + it.name) })
        }
        h.wake()
    }
    fun playRps(user: Rps.Hand) {
        val mine = Rps.throwHand(); val o = Rps.play(user, mine)
        var sc = Fun5Store.scores(c)
        sc = when (o) { Rps.Outcome.WIN -> sc.inc("rps_w"); Rps.Outcome.LOSE -> sc.inc("rps_l"); Rps.Outcome.DRAW -> sc.inc("rps_d") }
        Fun5Store.saveScores(c, sc)
        h.play(when (o) { Rps.Outcome.WIN -> MonkeyState.SAD; Rps.Outcome.LOSE -> MonkeyState.DANCE; else -> MonkeyState.GIGGLE }, 2.2f)
        h.chips(Rps.line(o, mine, h.character.id) + "  (you ${sc["rps_w"]} – me ${sc["rps_l"]})",
            listOf(QuickChip("v5", "Again!", "game_rps"), QuickChip("later", "Enough")))
        xp(Friendship.Source.GAME); if (o == Rps.Outcome.WIN) xp(Friendship.Source.GAME_WIN)
        if (o != Rps.Outcome.DRAW) com.combo.monkeycompanion.mind.MindHub.event(if (o == Rps.Outcome.LOSE) com.combo.monkeycompanion.mind.MindEvent.GAME_WON else com.combo.monkeycompanion.mind.MindEvent.GAME_LOST, "rock-paper-scissors")
    }
    /** Finger x while dragging during the banana game (screen px). True = consumed. */
    fun onFinger(rawX: Float): Boolean {
        val b = banana ?: return false
        bananaTarget = (rawX / h.screenW()).coerceIn(0f, 1f); fingerAt = SystemClock.uptimeMillis()
        return b.over.not()
    }
    /** A tap on the character during a game. True = consumed. */
    fun onTap(): Boolean {
        stare?.let { if (it.result == StaringContest.Result.NONE) { it.userBlinked(); return true } }
        return banana != null
    }
    private fun stepGames(dt: Float) {
        banana?.let { b ->
            val tiltOk = motion.active && SystemClock.uptimeMillis() - fingerAt > 1500L
            val target = if (tiltOk) 0.5f + motion.detector.steer() * 0.5f else bananaTarget
            b.update(dt, target)
            h.floorX(b.playerX)
            for (e in b.events) when (e) {
                1 -> { h.feet(tmpB); h.particles().spawn(Particles.SPARKLE, tmpB[0], tmpB[1] - 60 * d, 0f, -60 * d, 0.4f, 4 * d) }
                -1 -> h.say(listOf("Yuck, rotten! 🤢", "Eww eww eww 🤮").random(), 1200L)
            }
            if (b.over) {
                var sc = Fun5Store.scores(c); val best = b.score > sc["banana"]; sc = sc.best("banana", b.score); Fun5Store.saveScores(c, sc)
                banana = null
                h.play(if (b.score >= 10) MonkeyState.DANCE else MonkeyState.HAPPY_PAT, 2.5f)
                h.chips("🍌 ${b.score} bananas!" + if (best) " New record! 🏆" else " (best ${sc["banana"]})",
                    listOf(QuickChip("v5", "Again!", "game_banana"), QuickChip("later", "Enough")))
                xp(Friendship.Source.GAME)
                com.combo.monkeycompanion.mind.MindHub.event(if (b.score >= 10) com.combo.monkeycompanion.mind.MindEvent.GAME_WON else com.combo.monkeycompanion.mind.MindEvent.GAME_LOST, "banana catch")
            }
        }
        stare?.let { s ->
            s.update(dt)
            if (s.result != StaringContest.Result.NONE) {
                stare = null
                var sc = Fun5Store.scores(c)
                if (s.result == StaringContest.Result.YOU_WIN) {
                    sc = sc.inc("stare_w"); h.play(MonkeyState.SAD, 2f)
                    h.chips("*blinks* NOOO I blinked! You win 😭 (${"%.1f".format(s.t)} s)", listOf(QuickChip("v5", "Again!", "game_stare"), QuickChip("later", "Enough")))
                    xp(Friendship.Source.GAME_WIN)
                } else {
                    sc = sc.inc("stare_l"); h.play(MonkeyState.DANCE, 2f)
                    h.chips("HA! You blinked first! 😎 (${"%.1f".format(s.t)} s)", listOf(QuickChip("v5", "Again!", "game_stare"), QuickChip("later", "Enough")))
                }
                Fun5Store.saveScores(c, sc); xp(Friendship.Source.GAME)
                com.combo.monkeycompanion.mind.MindHub.event(if (s.result == StaringContest.Result.YOU_WIN) com.combo.monkeycompanion.mind.MindEvent.GAME_LOST else com.combo.monkeycompanion.mind.MindEvent.GAME_WON, "staring contest")
            }
        }
    }
    fun endGames(say: Boolean) { if (say && playing) h.sayInfo("Game over! 👋", 1500L); banana = null; stare = null }
    private fun hudLine() = banana?.let { "🍌 ${it.score}   ⏱ ${(it.seconds - it.t).toInt().coerceAtLeast(0)}s" } ?: stare?.let { "👀 Don't blink!  ${"%.1f".format(it.t)} s" } ?: ""
    private fun drawBananas(cv: Canvas, b: BananaCatch) {
        val w = h.screenW(); h.feet(tmpB); val ground = tmpB[1]
        for (it in b.items) Props5.banana(cv, it.x * w, 60 * d + it.y * (ground - 60 * d - 50 * d), d, it.rotten, it.y * 400f)
    }

    // ======================= chips from the bubble =======================
    /** "v5" chips. True = handled. */
    fun onChip(arg: String): Boolean {
        when {
            arg.startsWith("game_") -> Games.byId(arg.removePrefix("game_"))?.let { startGame(it) }
            arg.startsWith("rps_") -> playRps(Rps.Hand.valueOf(arg.removePrefix("rps_")))
            arg == "nag_ok" -> { h.sayInfo("Goodnight! 🌙 I'll guard the phone.", 2500L); h.play(MonkeyState.SLEEP, 4f) }
            arg == "nag_snooze" -> { snoozedUntil = SystemClock.elapsedRealtime() + SleepNag.SNOOZE_MS; saveNag(); h.sayInfo("Fine, 20 more minutes. I'm counting ⏰", 2500L) }
            else -> return false
        }
        return true
    }

    // ======================= 5. friendship =======================
    fun xp(src: Friendship.Source) { Fun5Store.addXp(c, src) }   // a level-up comes back through Fun5Store.onLevelUp
    fun celebrate(level: Int) {
        h.dance(); burst(Particles.CONFETTI, 28)
        h.say(Friendship.levelUpLine(level, h.character.id), 5000L)
        val unlock = Friendship.newAt(level).firstOrNull { it.kind == "wardrobe" }
        if (unlock?.id == "wear_crown") showProp(PropKind.CROWN, 20f) else if (unlock?.id == "wear_garland") showProp(PropKind.GARLAND, 20f)
        h.log("Friendship level $level", Friendship.newAt(level).joinToString { it.title }.ifEmpty { "level up" })
    }
    /** Pets: XP, and with the crown unlocked the character sometimes puts it on. */
    fun onPet() {
        xp(Friendship.Source.PET)
        val xp = Fun5Store.friendship(c).xp
        if (prop == PropKind.NONE && Random.nextFloat() < 0.12f) {
            if (Friendship.has(xp, "wear_crown")) showProp(PropKind.CROWN, 10f) else if (Friendship.has(xp, "wear_garland")) showProp(PropKind.GARLAND, 10f)
        }
    }
    private fun burst(kind: Int, n: Int) {
        if (h.saver()) return
        h.headTop(tmpA)
        repeat(n) { h.particles().spawn(kind, tmpA[0], tmpA[1], (Random.nextFloat() - 0.5f) * 500 * d, -(200 + Random.nextFloat() * 300) * d, 1.2f, 4 * d) }
        h.wake()
    }

    // ======================= 8. paired phone =======================
    override fun onPaired(peerName: String, peerCharacter: String) {
        h.say("Paired with $peerName's ${CharacterRegistry.byId(peerCharacter)?.name ?: "Settai"}! 👋", 3000L); h.play(MonkeyState.WAVE, 2f)
        xp(Friendship.Source.PAIR)
    }
    override fun onClosed() { if (visitor != null) visitor = null }
    override fun onEvent(ev: PairProtocol.Event, side: PairProtocol.Side?) {
        val peer = PairHub.peerName ?: "friend"
        when (ev) {
            PairProtocol.Event.WAVE -> { h.play(MonkeyState.WAVE, 2.2f); h.say("$peer's Settai says hi from the other phone 👋", 2600L) }
            PairProtocol.Event.DANCE -> { h.dance(); h.say("Dance-off with $peer! 💃🕺", 2600L) }
            PairProtocol.Event.FIGHT -> { h.play(MonkeyState.ANGRY, 2.6f); dizzyUntil = t + 2.5f; h.say("Pillow fight with $peer! 🥊 (no one gets hurt)", 2600L) }
            PairProtocol.Event.JUMP_ACROSS -> {
                val from = PairProtocol.enterSide(side ?: PairProtocol.Side.LEFT)
                val ch = CharacterRegistry.byId(PairHub.peerCharacter) ?: CharacterRegistry.all.first()
                visitor = Visitor(ch, if (from == PairProtocol.Side.LEFT) -1 else 1)
                h.say("Whoa, $peer's ${ch.name} jumped over! 😲", 2600L); h.wake()
            }
            PairProtocol.Event.ARRIVED -> { ui.postDelayed({ h.comeBack() }, 7000L) }
        }
    }
    /** From the Two-phones screen / menu: my character does something on both phones. */
    fun sendToPeer(ev: PairProtocol.Event, side: PairProtocol.Side? = null): Boolean {
        if (!PairHub.paired) { h.say("Pair with another phone first (Settai app → Two phones) 📱📱", 3000L); return false }
        if (!PairHub.sendEvent(ev, side)) return false
        when (ev) {
            PairProtocol.Event.JUMP_ACROSS -> h.leaveBy(if (side == PairProtocol.Side.LEFT) -1 else 1)
            PairProtocol.Event.WAVE -> h.play(MonkeyState.WAVE, 2f)
            PairProtocol.Event.DANCE -> h.dance()
            PairProtocol.Event.FIGHT -> h.play(MonkeyState.ANGRY, 2.4f)
            else -> Unit
        }
        return true
    }

    /** The paired phone's character, visiting: walks in from a side, waves, dances, walks back out. */
    private inner class Visitor(val ch: Character, val fromSide: Int) {
        private val rig = MonkeyRig(); private val pose = Pose(); private val ac = AnimContext()
        private val renderer = ch.newRenderer().also { it.wear = CharacterRegistry.wear(ch) }
        private var vt = 0f
        fun step(dt: Float): Boolean {
            vt += dt
            if (vt > 10f) { PairHub.sendEvent(PairProtocol.Event.ARRIVED); return false }
            return true
        }
        fun draw(cv: Canvas) {
            val w = h.screenW(); h.feet(tmpB)
            val edge = if (fromSide < 0) -60 * d else w + 60 * d
            val stand = if (fromSide < 0) w * 0.22f else w * 0.78f
            val x = when { vt < 2f -> edge + (stand - edge) * (vt / 2f); vt < 8f -> stand; else -> stand + (edge - stand) * ((vt - 8f) / 2f) }
            val st = when { vt < 2f || vt >= 8f -> MonkeyState.WALK; vt < 4.5f -> MonkeyState.WAVE; else -> MonkeyState.DANCE }
            Animations.fill(pose, st, vt, vt, ac)
            val facing = if (vt < 8f) -fromSide.toFloat() else fromSide.toFloat()
            rig.layout(pose, x, tmpB[1], h.bodyScale, facing)
            renderer.draw(cv, rig, pose, vt)
        }
    }
}
