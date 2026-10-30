package com.combo.monkeycompanion.mind

import android.content.Context
import android.os.SystemClock
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.heart.BreatheBuddy
import com.combo.monkeycompanion.heart.CampfireScene
import com.combo.monkeycompanion.heart.CareCheckIn
import com.combo.monkeycompanion.heart.CareCheckInStore
import com.combo.monkeycompanion.heart.ComfortKind
import com.combo.monkeycompanion.heart.FocusBuddy
import com.combo.monkeycompanion.heart.HeartEngine
import com.combo.monkeycompanion.heart.UserMood
import com.combo.monkeycompanion.powers.ActionLog
import com.combo.monkeycompanion.world.Director
import com.combo.monkeycompanion.world.NightTrace
import com.combo.monkeycompanion.world.Population
import com.combo.monkeycompanion.world.WorldClock
import com.combo.monkeycompanion.world.WorldStore
import java.time.ZoneId
import kotlin.random.Random

/**
 * v6 "Settai mind" runner on the phone: one [Mind] per character (personality seeded once per install, persisted),
 * its diary, batched saving, decision logging (Recent actions) and CPU accounting. No polling: the overlay calls
 * [heartbeat] on a 20-60 s timer only while Settai is visible, the accessibility service calls [perceive] on app
 * changes (debounced), and the overlay reports events. The LLM is never needed here (only optional comment garnish).
 */
object MindHub {
    private const val PREFS = "settai_mind"
    private var app: Context? = null
    private val minds = HashMap<String, Mind>()
    private val rnd = Random(System.nanoTime())
    private var dirty = false
    private var lastSave = 0L
    private var lastIgnoreLog = 0L
    /** CPU spent thinking (ns) and how many thoughts: shown in Settings › Battery and the day-sim. */
    @Volatile var cpuNanos = 0L; private set
    @Volatile var thoughts = 0; private set
    /** The overlay's body: does a decided action (returns true when it actually did it). */
    @Volatile var actor: ((Decision) -> Boolean)? = null
    /** Last few decisions in plain words (for the app's mood card). */
    val recent = ArrayDeque<String>()
    /** v7.0: The Phone as a World — shared lasting conditions, event clock and night traces. */
    val world = WorldClock()
    val director = Director()
    /** v8.0 Heart & Care + Co-Presence state. */
    val care = CareCheckInStore()
    val focusBuddy = FocusBuddy()
    val breatheBuddy = BreatheBuddy()
    val campfire = CampfireScene()
    private val recentSwitches = ArrayDeque<Long>()
    private var lastRhythmComfortAt = 0L
    @Volatile var neighbourMode: String = "DUO"
    @Volatile var customPartner1Id: String? = null
    @Volatile var customPartner2Id: String? = null
    @Volatile var lastNightTrace: NightTrace? = null
    @Volatile var onMorningTrace: ((NightTrace) -> Unit)? = null
    @Volatile var onComfortRequested: ((ComfortKind, UserMood, String, String) -> Unit)? = null
    @Volatile var onCheckInDue: ((CareCheckIn, String) -> Unit)? = null
    @Volatile var banterActor: ((Director.BanterScene) -> Boolean)? = null

    fun setNeighbourMode(ctx: Context, mode: Population.Mode) {
        init(ctx)
        neighbourMode = mode.id
        flush()
    }

    fun setDuoCombo(ctx: Context, leadId: String, partnerId: String) {
        init(ctx)
        CharacterRegistry.select(leadId)
        runCatching { ChatHub.settings.characterId = leadId; ChatHub.settings.applyCharacter() }
        neighbourMode = Population.Mode.DUO.id
        customPartner1Id = partnerId
        flush()
    }

    fun setTrioCombo(ctx: Context, leadId: String, partner1Id: String, partner2Id: String) {
        init(ctx)
        CharacterRegistry.select(leadId)
        runCatching { ChatHub.settings.characterId = leadId; ChatHub.settings.applyCharacter() }
        neighbourMode = Population.Mode.TRIO.id
        customPartner1Id = partner1Id
        customPartner2Id = partner2Id
        flush()
    }

    /** v10.12 free crew picker: the user's exact choice of lead + partners. null partner = auto-fill that slot. */
    fun setCrew(ctx: Context, leadId: String, mode: Population.Mode, partner1Id: String?, partner2Id: String?) {
        init(ctx)
        CharacterRegistry.select(leadId)
        runCatching { ChatHub.settings.characterId = leadId; ChatHub.settings.applyCharacter() }
        neighbourMode = mode.id
        customPartner1Id = partner1Id?.takeIf { it.isNotBlank() && it != leadId }
        customPartner2Id = partner2Id?.takeIf { it.isNotBlank() && it != leadId && it != customPartner1Id }
        flush()
    }

    /** Lead first, then the user's picked partners (never overridden), then auto-filled slots. */
    fun activeCreatures(): List<String> {
        val lead = runCatching { CharacterRegistry.current.id }.getOrDefault("monkey")
        val mode = Population.Mode.of(neighbourMode)
        val crew = com.combo.monkeycompanion.world.CrewRules.resolve(lead, mode, customPartner1Id, customPartner2Id, world.state)
        val g = com.combo.monkeycompanion.overlay.ChaseShowHub.guests
        return if (g.isEmpty()) crew else (crew + g).distinct()
    }

    // ---- v10.12 skit engine (own seeded RNG: the frame-loop / mind RNG order is untouched) ----
    val skits = com.combo.monkeycompanion.engine.skit.SkitConductor(System.nanoTime())
    @Volatile var skitRun: com.combo.monkeycompanion.engine.skit.SkitRun? = null
    private var lastSkitAt = 0L
    private var nextSkitGapMs = 3 * 60_000L

    /** Start a skit now (menu / app button) or periodically. Returns a reason when it can't. */
    fun trySkit(force: Boolean, forceId: String? = null): String? {
        val st = runCatching { ChatHub.settings }.getOrNull()
        if (st != null && !st.skitsEnabled && !force) return "skits are off"
        if (skitRun != null) return "a skit is already playing"
        if (com.combo.monkeycompanion.overlay.ChaseShowHub.show != null) return "a chase show is on"
        val all = activeCreatures()
        if (all.size < 2) return "pick Duo or Trio first"
        // Only characters whose window is really on screen can act.
        val crew = all.filter { com.combo.monkeycompanion.engine.skit.SkitBus.has(it) }
        if (crew.size < 2) return "your friends aren't on screen yet"
        val now = System.currentTimeMillis()
        if (!force) {
            val m = mind() ?: return "not ready"
            val sc = sceneFor(m.scene.pkg, m.scene)
            director.canPlay(now, Population.Mode.of(neighbourMode), sc.keyboard, sc.call, sc.fullscreen, sc.dnd, sc.quietHours, ignoreCooldown = true)?.let { return it }
            if (lastSkitAt == 0L) { lastSkitAt = now; return "warming up" }
            if (now - lastSkitAt < nextSkitGapMs) return "resting"
        }
        // v10.13: Billa & Chittu on screen = the real chase show (every few minutes)
        if (forceId == null && "billa" in crew && "chittu" in crew && showActor != null) {
            lastSkitAt = now; nextSkitGapMs = (3 * 60_000L + skits.rnd.nextLong(2 * 60_000L))
            showActor?.invoke(); return null
        }
        val run = skits.start(crew, forceId) ?: return "no skit fits this crew"
        skitRun = run; lastSkitAt = now
        nextSkitGapMs = (2 * 60_000L + skits.rnd.nextLong(2 * 60_000L))
        val msg = "Skit: ${run.skit.title} (" + run.cast.joinToString(" + ") { Population.displayName(it) } + ")"
        synchronized(recent) { recent.addFirst(msg); while (recent.size > 8) recent.removeLast() }
        app?.let { runCatching { ActionLog.add(it, msg, "funny skit between friends") } }
        return null
    }

    /** Called every frame by the lead window. Sends due cues to each actor's window. */
    fun stepSkit(dt: Float, abort: Boolean) {
        val r = skitRun ?: return
        if (abort) { skitRun = null; return }
        for (c in r.step(dt)) com.combo.monkeycompanion.engine.skit.SkitBus.post(c)
        if (r.done) skitRun = null
    }

    /** Ask the Director for a 2-turn neighbour banter scene (obeys ProactiveGate respect rules). */
    fun tryBanter(forceKind: Director.Kind? = null): Director.BanterScene? {
        val m = mind() ?: return null
        val sc = sceneFor(m.scene.pkg, m.scene)
        val mode = Population.Mode.of(neighbourMode)
        val scene = director.pickScene(
            leadId = m.characterId, mode = mode, state = world.state,
            minuteOfDay = sc.minute, now = System.currentTimeMillis(),
            keyboard = sc.keyboard, inCall = sc.call, fullscreen = sc.fullscreen,
            dnd = sc.dnd, quietHours = sc.quietHours, forceKind = forceKind, rnd = rnd,
            active = activeCreatures()
        ) ?: return null
        val ok = runCatching { banterActor?.invoke(scene) == true }.getOrDefault(false)
        if (ok) {
            val msg = "Neighbours: ${Population.displayName(scene.leadId)} & ${Population.displayName(scene.buddyId)} (${scene.kind.label})"
            synchronized(recent) { recent.addFirst(msg); while (recent.size > 8) recent.removeLast() }
            app?.let { ActionLog.add(it, msg, "${scene.leadLine} / ${scene.buddyLine}") }
        }
        return scene
    }

    fun init(ctx: Context) {
        if (app == null) {
            app = ctx.applicationContext
            runCatching {
                com.combo.monkeycompanion.character.CustomCompanionStore.init(app!!)
                Personality.decodeCustomTraits(prefs().getString("custom_traits", null))
                neighbourMode = WorldStore.decodeJson(world, prefs().getString("world_json", null))
                customPartner1Id = prefs().getString("custom_partner_1", null)
                customPartner2Id = prefs().getString("custom_partner_2", null)
                lastNightTrace = world.recentTraces.firstOrNull()
                care.decode(prefs().getString("care_store", null))
            }
            sampleStorageAndBattery()
        }
    }

    /** Event-driven snapshot of storage & battery on startup / unlock (never polled). */
    fun sampleStorageAndBattery() {
        val c = app ?: return
        val now = System.currentTimeMillis()
        runCatching {
            val bm = c.getSystemService(android.os.BatteryManager::class.java)
            val pct = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (pct in 1..100) world.onBattery(pct, now)
        }
        runCatching {
            val st = android.os.StatFs(c.filesDir.absolutePath)
            val total = st.totalBytes.coerceAtLeast(1L)
            val used = (total - st.availableBytes).toFloat() / total
            val am = c.getSystemService(android.app.ActivityManager::class.java)
            val mi = android.app.ActivityManager.MemoryInfo()
            am?.getMemoryInfo(mi)
            world.onStorage(used, mi.lowMemory, now)
        }
    }

    private fun prefs() = app!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun installSeed(): Long {
        val p = prefs()
        var s = p.getLong("seed", 0L)
        if (s == 0L) { s = System.nanoTime() xor (Random.nextLong() shl 1) or 1L; p.edit().putLong("seed", s).apply() }
        return s
    }

    /** The current character's mind (lazily loaded). */
    fun mind(): Mind? {
        val c = app ?: return null
        val ch = runCatching { CharacterRegistry.current }.getOrNull() ?: return null
        return minds.getOrPut(ch.id) {
            val p = prefs()
            val pers = Personality.decode(p.getString("pers_${ch.id}", null))
                ?: Personality.seed(ch.id, installSeed(), ch.powers.map { it.title }).also { p.edit().putString("pers_${ch.id}", it.encode()).apply() }
            Mind(ch.id, pers, Diary(ZoneId.systemDefault())).also { m ->
                m.decode(p.getString("mind_${ch.id}", null))
                m.diary.decode(p.getString("diary_${ch.id}", null))
            }
        }.also { m ->
            m.intensity = (runCatching { ChatHub.settings.mindIndependence }.getOrDefault(50)) / 100f
            m.friendLevel = runCatching { com.combo.monkeycompanion.v5.Friendship.level(com.combo.monkeycompanion.fun5.Fun5Store.friendship(c).xp) }.getOrDefault(1)
        }
    }

    private inline fun <T> timed(block: () -> T): T {
        val t0 = System.nanoTime()
        try { return block() } finally { cpuNanos += System.nanoTime() - t0; thoughts++ }
    }

    private fun minuteNow(): Int { val c = java.util.Calendar.getInstance(); return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE) }

    /** The current situation, from what the overlay / accessibility service already know (no screen reading). */
    private fun sceneFor(pkg: String, base: Scene?): Scene {
        val sit = runCatching { com.combo.monkeycompanion.agent.Proactive.situation() }.getOrNull()
        val st = runCatching { ChatHub.settings }.getOrNull()
        val minute = minuteNow()
        val quiet = st != null && com.combo.monkeycompanion.agent.QuietHours.inside(minute, st.quietStart, st.quietEnd)
        val low = runCatching {
            val bm = app!!.getSystemService(android.os.BatteryManager::class.java)
            bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) in 0..20
        }.getOrDefault(false)
        return Scene(pkg, AppCat.of(pkg, sit?.game == true), keyboard = sit?.typing == true, fullscreen = sit?.fullscreen == true,
            call = sit?.inCall == true, dnd = sit?.dnd == true, quietHours = quiet, minute = minute, dwellSecs = base?.dwellSecs ?: 0, batteryLow = low)
    }

    /** App in front changed (already debounced by Proactive). */
    /** v10.13: the overlay hears every foreground-app change (Selfie Photobomb looks for camera apps). */
    @Volatile var onAppChanged: ((String) -> Unit)? = null
    /** v10.13: starts the Billa-vs-Chittu chase show (set by the overlay). */
    @Volatile var showActor: (() -> Unit)? = null

    fun perceive(pkg: String) {
        runCatching { onAppChanged?.invoke(pkg) }
        val m = mind() ?: return
        val now = System.currentTimeMillis()
        val sc = sceneFor(pkg, null)
        world.onAppSwitch(pkg, sc.cat == AppCat.GAME, now)
        while (recentSwitches.isNotEmpty() && now - recentSwitches.first() > 5 * 60_000L) recentSwitches.removeFirst()
        recentSwitches.addLast(now)
        val rhythmMood = care.recordRhythm(sc.minute, recentSwitches.size, sc.dwellSecs / 60, now)
        if (rhythmMood != null && now - lastRhythmComfortAt > 30 * 60_000L) {
            lastRhythmComfortAt = now
            triggerComfort(rhythmMood, "")
        }
        val d = timed { m.perceive(sc, now, rnd) }
        deliver(m, d, scene = true)
    }

    /** Coarse heartbeat, only while visible. Returns ms until the next one. */
    fun heartbeat(): Long {
        com.combo.monkeycompanion.agent.ForegroundAppTracker.pollOnHeartbeat(app)
        val m = mind() ?: return 60_000L
        val now = System.currentTimeMillis()
        val min = minuteNow()
        m.visible = true
        val sc = sceneFor(m.scene.pkg, m.scene)
        m.updateScene(sc)
        world.onRinger(sc.dnd, sc.quietHours, now)
        world.onTick(now, min, sc.quietHours)
        val d = timed { m.heartbeat(now, min, rnd) }
        deliver(m, d, scene = false)
        if (d.act == Act.IGNORE && rnd.nextFloat() < 0.28f) tryBanter()
        if (skitRun == null && skits.rnd.nextFloat() < 0.4f) trySkit(force = false)
        maybeSave()
        return m.heartbeatMs()
    }

    /** An old random timer wants to fire: true = go ahead (the mind feels like it). */
    fun permit(p: Proposal): Boolean {
        val m = mind() ?: return true
        // v8.0: while focusing, breathing, or comforting a stressed/sad human, suppress noisy random gags/pipes.
        if ((focusBuddy.active || breatheBuddy.active || care.currentMood.needsComfort) && p.act.loud) return false
        m.updateScene(sceneFor(m.scene.pkg, m.scene))
        val d = timed { m.permit(p, System.currentTimeMillis(), rnd) }
        val ok = d.act != Act.IGNORE
        if (ok) { m.commit(d, System.currentTimeMillis()); dirty = true }
        logDecision(d, forced = ok)
        return ok
    }

    /** Something happened to the creature (poke, shake, game, unlock…). */
    fun event(e: MindEvent, subject: String = "") {
        val m = mind() ?: return
        val now = System.currentTimeMillis()
        val min = minuteNow()
        when (e) {
            MindEvent.SCREEN_OFF -> { m.visible = false; world.onScreenOff(now) }
            MindEvent.UNLOCK -> {
                m.visible = true
                sampleStorageAndBattery()
                val others = CharacterRegistry.all.map { it.id }.filter { it != m.characterId }
                val trace = world.onUnlock(now, min, listOf(m.characterId) + others.take(1), rnd)
                if (trace != null) {
                    lastNightTrace = trace
                    m.diary.add(Diary.Kind.HABIT, trace.diarySubject, now, extra = 0.25f)
                    app?.let { ActionLog.add(it, "Night trace: ${trace.line}", CharacterRegistry.current.name) }
                    onMorningTrace?.invoke(trace)
                }
                val due = care.popDueCheckIn(now, min)
                if (due != null) {
                    val line = HeartEngine.checkInLine(due, m.characterId)
                    m.diary.add(Diary.Kind.HABIT, "checked in on ${due.topic}", now, extra = 0.3f)
                    app?.let { ActionLog.add(it, "Care check-in: ${due.topic}", line) }
                    dirty = true
                    onCheckInDue?.invoke(due, line)
                }
            }
            MindEvent.PET, MindEvent.GAME_WON -> world.onInteraction(gentle = true, kind = "pet", now = now)
            MindEvent.CHAT, MindEvent.FUNNY_CHAT -> world.onInteraction(gentle = true, kind = "chat", now = now)
            MindEvent.POKE -> world.onInteraction(gentle = false, kind = "poke", now = now)
            MindEvent.SHAKE -> world.onInteraction(gentle = false, kind = "shake", now = now)
            MindEvent.HARD_SHAKE -> world.onInteraction(gentle = false, kind = "hard_shake", now = now)
            else -> Unit
        }
        val d = timed { m.onEvent(e, now, min, rnd, subject) }
        dirty = true
        if (d != null) deliver(m, d, scene = false)
        if (e == MindEvent.SCREEN_OFF) flush()
    }

    fun setVisible(v: Boolean) { mind()?.visible = v; if (!v) flush() }

    private fun deliver(m: Mind, d: Decision, scene: Boolean) {
        var done = false
        if (d.act != Act.IGNORE) {
            done = runCatching { actor?.invoke(d) == true }.getOrDefault(false)
            if (done) { m.commit(d, System.currentTimeMillis()); dirty = true }
        }
        logDecision(if (d.act != Act.IGNORE && !done) d.copy(reason = d.reason + " (couldn't right now)") else d, forced = d.act != Act.IGNORE || scene)
    }

    private fun logDecision(d: Decision, forced: Boolean) {
        val c = app ?: return
        val now = SystemClock.elapsedRealtime()
        if (d.act == Act.IGNORE) {
            if (!forced || now - lastIgnoreLog < 120_000L) return      // ignored things are logged at most every 2 min
            lastIgnoreLog = now
        }
        val name = runCatching { CharacterRegistry.current.name }.getOrDefault("Settai")
        val line = d.reason
        synchronized(recent) { recent.addFirst(line); while (recent.size > 8) recent.removeLast() }
        runCatching { ActionLog.add(c, "Mind: $line", name) }
    }

    /** A line for the chat prompt: mood + a few diary memories (keeps conversations continuous). */
    fun promptNote(): String {
        val m = mind() ?: return ""
        val now = System.currentTimeMillis()
        val d = m.drives
        val mood = "Your current mood: energy %.1f, bored %.1f, social %.1f, curious %.1f, grumpy %.1f, content %.1f.".format(
            java.util.Locale.ROOT, d.energy, d.boredom, d.social, d.curiosity, d.annoyance, d.contentment)
        val fav = m.personality.fav
        val likes = "Your favourites: the ${fav.corner.label}, napping at the ${fav.napSpot.label}, ${fav.favApp.label} apps, snack: ${fav.snack}; you dislike ${fav.hatedApp.label} apps."
        val mem = m.diary.promptSnippet(m.characterId, now, 5)
        val wLine = world.state.promptLine()
        val hLine = HeartEngine.promptNote(care.currentMood, care.pending)
        return "$wLine. $mood $likes" +
            (if (hLine.isNotBlank()) " $hLine" else "") +
            (if (mem.isNotBlank()) " Things you remember (mention one only if it fits naturally): $mem." else "")
    }

    fun thinking(): String = mind()?.thinking(System.currentTimeMillis()) ?: "…"

    fun cpuSummary(): String = if (thoughts == 0) "no thoughts yet" else
        "$thoughts thoughts, %.0f µs each on average (%.1f ms total)".format(java.util.Locale.ROOT, cpuNanos / 1000.0 / thoughts, cpuNanos / 1e6)

    private fun maybeSave() {
        if (dirty && System.currentTimeMillis() - lastSave > 60_000L) flush()
    }

    /** Batched persistence: at most once a minute, plus on screen off / hidden / quit. */
    fun flush() {
        val c = app ?: return
        if (minds.isEmpty()) return
        val e = prefs().edit()
        for ((id, m) in minds) {
            m.diary.forget(System.currentTimeMillis())
            e.putString("mind_$id", m.encode()).putString("diary_$id", m.diary.encode()).putString("pers_$id", m.personality.encode())
        }
        e.putString("world_json", WorldStore.encodeJson(world, neighbourMode))
        e.putString("custom_partner_1", customPartner1Id)
        e.putString("custom_partner_2", customPartner2Id)
        e.putString("care_store", care.encode())
        e.putString("custom_traits", Personality.encodeCustomTraits())
        e.apply()
        dirty = false; lastSave = System.currentTimeMillis()
    }

    /** For the app screens (diary / mood) of a given character, even when it's not the one on screen. */
    fun mindFor(characterId: String): Mind? {
        val c = app ?: return null
        minds[characterId]?.let { return it }
        val ch = runCatching { CharacterRegistry.byId(characterId) }.getOrNull() ?: return null
        val p = prefs()
        val pers = Personality.decode(p.getString("pers_$characterId", null))
            ?: Personality.seed(characterId, installSeed(), ch.powers.map { it.title }).also { p.edit().putString("pers_$characterId", it.encode()).apply() }
        return Mind(characterId, pers, Diary(ZoneId.systemDefault())).also { m ->
            m.decode(p.getString("mind_$characterId", null)); m.diary.decode(p.getString("diary_$characterId", null))
            minds[characterId] = m
        }
    }

    /** Tests: forget everything in memory (prefs are cleared by the test). */
    /** Analyses a user chat/voice message for emotional tone & next-day check-in promises. */
    fun onUserMessage(text: String): Pair<UserMood, CareCheckIn?> {
        val now = System.currentTimeMillis()
        val (mood, checkIn) = care.recordMessage(text, now)
        val m = mind()
        if (checkIn != null) {
            m?.diary?.add(Diary.Kind.HABIT, "promised to check in on ${checkIn.topic}", now, extra = 0.3f)
            app?.let { ActionLog.add(it, "Care check-in scheduled: ${checkIn.topic}", checkIn.userQuote) }
        }
        if (mood.needsComfort) {
            triggerComfort(mood, text)
        }
        dirty = true
        maybeSave()
        return mood to checkIn
    }

    fun triggerComfort(mood: UserMood = care.currentMood, userText: String = "", forceKind: ComfortKind? = null) {
        val chId = runCatching { CharacterRegistry.current.id }.getOrDefault("monkey")
        val kind = forceKind ?: HeartEngine.pickComfort(mood, userText, rnd)
        val sign = HeartEngine.signTextFor(mood, rnd)
        val line = HeartEngine.comfortLine(chId, kind, mood, sign)
        app?.let { ActionLog.add(it, "Heart comfort: ${kind.label} (${mood.label})", line) }
        onComfortRequested?.invoke(kind, mood, sign, line)
    }

    fun resetForTest() {
        minds.clear(); cpuNanos = 0L; thoughts = 0; actor = null; recent.clear(); lastIgnoreLog = 0L
        world.recentTraces.clear(); world.screenOffAt = 0L; world.lastTickAt = 0L; lastNightTrace = null; banterActor = null
        care.clear(); focusBuddy.stop(); breatheBuddy.stop(); campfire.stop(); recentSwitches.clear(); customPartner1Id = null; customPartner2Id = null
        onComfortRequested = null; onCheckInDue = null
    }
}
