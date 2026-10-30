package com.combo.monkeycompanion.mind

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

/** What the creature can choose to do. [loud] = noticeable (strongly held back while the human focuses). */
enum class Act(val verb: String, val loud: Boolean, val cooldownMs: Long) {
    IGNORE("ignored it", false, 0L),
    GLANCE("glanced over", false, 30_000L),
    EXPLORE("went exploring", true, 3 * 60_000L),
    COMMENT("made a comment", true, 4 * 60_000L),
    NAP("took a nap", false, 20 * 60_000L),
    GAG("played a gag", true, 3 * 60_000L),
    POWER("used a power", true, 10 * 60_000L),
    LEAVE("left for a while", false, 40 * 60_000L),
    COME_BACK("came back", false, 0L),
    SEEK_ATTENTION("asked for attention", true, 12 * 60_000L),
    GROOM("groomed quietly", false, 2 * 60_000L),
    HABIT("did a habit", false, 0L),
    RECALL("remembered something", true, 90 * 60_000L),
    WANDER("wandered around", false, 30_000L),
    HIDE("hid respectfully", false, 2 * 60_000L),
}

/** Things that happen to the creature. */
enum class MindEvent { POKE, PET, SHAKE, HARD_SHAKE, CHAT, FUNNY_CHAT, GAME_WON, GAME_LOST, UNLOCK, SCREEN_OFF, SWIPE_AWAY, CRASH_LANDING, POWER_USED, FESTIVAL, NAP_START, WOKE }

/** Old random timers now *propose*; the mind approves or vetoes (the old safety gates / rate limits still apply first). */
enum class Proposal(val act: Act) { DANCE(Act.GAG), GAG(Act.GAG), QUIP(Act.COMMENT), CHATTER(Act.COMMENT), PIPE(Act.COMMENT), MOMENT(Act.GLANCE), POWER(Act.POWER), ENTRANCE(Act.POWER), WANDER(Act.WANDER) }

/** Rituals that emerge from repetition (the morning stretch is innate). */
enum class Habit(val label: String, val from: Int, val to: Int, val act: Act, val needDays: Int) {
    MORNING_STRETCH("morning stretch", 6 * 60, 11 * 60, Act.GROOM, 0),
    AFTER_LUNCH_NAP("after-lunch nap", 13 * 60, 15 * 60 + 30, Act.NAP, 2),
    EVENING_SNACK("evening snack", 17 * 60, 19 * 60 + 30, Act.GAG, 2),
}

data class Decision(
    val act: Act,
    val scores: Map<Act, Float>,
    val reason: String,
    val scene: Scene? = null,
    val line: String? = null,
    val recall: Diary.Entry? = null,
    val habit: Habit? = null,
    /** Worth asking a real brain for a one-liner (novel + interesting + chatty mood); the template [line] is the fallback. */
    val wantsLlm: Boolean = false,
    /** For LEAVE: how long (ms) and the excuse. */
    val awayMs: Long = 0L,
) {
    val acting get() = act != Act.IGNORE
}

/**
 * Settai's mind: a small, event-driven utility AI. No LLM, no polling: it thinks when something happens (app change,
 * poke, unlock…) and on a coarse heartbeat only while it's on screen. Most of the time the best choice is to ignore,
 * so the reactions it does choose feel rare and meaningful. Pure Kotlin, deterministic with a seeded [Random].
 */
class Mind(val characterId: String, var personality: Personality, val diary: Diary = Diary()) {
    private companion object { val QUIET_OK = setOf(Act.IGNORE, Act.LEAVE, Act.HIDE, Act.NAP, Act.COME_BACK) }
    val drives = Drives(curiosity = personality.traits.curiosity * 0.6f)
    val habituation = Habituation()
    /** 0..1 from the single "How independent" slider. */
    var intensity = 0.5f
    var friendLevel = 1
    var visible = true
    var napping = false
    var scene = Scene(); private set
    var awayUntil = 0L; private set
    val away get() = awayUntil > 0L
    private var lastTick = 0L
    private var sceneSince = 0L
    private var focusSince = 0L
    private var screenOffAt = 0L
    private val lastDone = HashMap<Act, Long>()
    private val recent = ArrayDeque<Act>()
    private val swipePenalty = HashMap<String, Pair<Float, Long>>()
    private val habitDays = HashMap<Habit, MutableSet<String>>()
    private val habitDone = HashMap<Habit, String>()

    val traits get() = personality.at(friendLevel, intensity)

    // ---------------- time ----------------

    /** Let time pass up to [now] (lazy: called on every event / heartbeat; a long gap is one exponential step). */
    fun advance(now: Long, minute: Int) {
        if (lastTick == 0L) lastTick = now
        val dt = ((now - lastTick).coerceIn(0L, 12 * 3600_000L)) / 1000f
        lastTick = now
        drives.tick(dt, minute, visible && !away, napping, traits.curiosity * 0.6f, scene.batteryLow)
        if (focus(now) < 0.55f) focusSince = 0L else if (focusSince == 0L) focusSince = now
    }

    /** Seconds to the next heartbeat while visible (sleepy = slower). Zero heartbeats when hidden / screen off. */
    fun heartbeatMs(): Long = (20_000L + (40_000L * (1f - drives.energy))).toLong()

    // ---------------- respect ----------------

    private fun bucket(cat: AppCat, minute: Int) = "${cat.name}@${minute / 180}"

    private fun learned(s: Scene, now: Long): Float {
        val (v, at) = swipePenalty[bucket(s.cat, s.minute)] ?: return 0f
        return v * 0.5f.pow((now - at).coerceAtLeast(0L) / (7 * 86_400_000f))
    }

    /** How much the human needs to be left alone right now, 0..1. */
    fun focus(now: Long, s: Scene = scene): Float {
        if (s.keyboard || s.typingBursts > 0 || s.call || s.cat == AppCat.MEETING) return 1f
        var f = 0f
        if (s.dnd) f = maxOf(f, 0.95f)
        if (s.quietHours) f = maxOf(f, 0.9f)
        if (s.cat == AppCat.BANKING) f = maxOf(f, 0.9f)
        if (s.fullscreen) f = maxOf(f, if (s.cat == AppCat.VIDEO || s.cat == AppCat.GAME) 0.85f else 0.7f)
        if ((s.cat == AppCat.READING || s.cat == AppCat.EMAIL) && s.dwellSecs > 45) f = maxOf(f, 0.6f + minOf(0.3f, s.dwellSecs / 1200f))
        if (s.cat == AppCat.BROWSER && s.dwellSecs > 90) f = maxOf(f, 0.55f)
        if (s.hour < 6) f = maxOf(f, 0.5f)
        return (f + learned(s, now)).coerceIn(0f, 1f)
    }

    /** Typing, calls, meetings, DND, quiet hours: only quiet choices are allowed. */
    fun hardQuiet(s: Scene = scene) = s.keyboard || s.typingBursts > 0 || s.call || s.cat == AppCat.MEETING || s.dnd || s.quietHours

    // ---------------- perception ----------------

    /** The app in front changed (debounced by the caller). */
    fun perceive(s: Scene, now: Long, rnd: Random): Decision {
        if (s.pkg != scene.pkg) sceneSince = now
        scene = s.copy(dwellSecs = ((now - sceneSince) / 1000).toInt())
        advance(now, s.minute)
        val nov = habituation.novelty(s.pkg, now)
        habituation.expose(s.pkg, now)
        drives.curiosity = (drives.curiosity + 0.35f * nov * traits.curiosity).coerceAtMost(1f)
        val d = choose(Trigger.SCENE, now, rnd, nov)
        return d
    }

    /** The coarse heartbeat (only while visible). */
    fun heartbeat(now: Long, minute: Int, rnd: Random): Decision {
        scene = scene.copy(minute = minute, dwellSecs = if (sceneSince == 0L) 0 else ((now - sceneSince) / 1000).toInt())
        advance(now, minute)
        return choose(Trigger.HEARTBEAT, now, rnd, 0f)
    }

    /** An old random timer wants to fire: does the creature feel like it? */
    fun permit(p: Proposal, now: Long, rnd: Random): Decision {
        advance(now, scene.minute)
        return choose(Trigger.PROPOSAL, now, rnd, 0f, p)
    }

    /** Context updates without an app switch (keyboard up, call, DND…). */
    fun updateScene(s: Scene) { scene = s.copy(pkg = scene.pkg, cat = scene.cat, dwellSecs = scene.dwellSecs) }

    // ---------------- events ----------------

    /** Something happened to it. Returns an immediate reaction (rarely) or null. [subject] = app / game / line / power. */
    fun onEvent(e: MindEvent, now: Long, minute: Int, rnd: Random, subject: String = ""): Decision? {
        advance(now, minute)
        val d = drives
        when (e) {
            MindEvent.POKE -> { d.annoyance += 0.12f; d.social -= 0.1f; d.boredom -= 0.1f; diary.add(Diary.Kind.POKED, scene.app, now) }
            MindEvent.PET -> { d.contentment += 0.15f; d.social -= 0.25f; d.annoyance -= 0.1f; diary.add(Diary.Kind.PETTED, scene.app, now) }
            MindEvent.SHAKE -> { d.annoyance += 0.3f; d.energy -= 0.05f; diary.add(Diary.Kind.SHAKEN, scene.app, now) }
            MindEvent.HARD_SHAKE -> { d.annoyance += 0.5f; d.energy -= 0.08f; diary.add(Diary.Kind.SHAKEN, scene.app, now, extra = 0.2f) }
            MindEvent.CHAT -> { d.social -= 0.4f; d.boredom -= 0.2f; d.contentment += 0.05f }
            MindEvent.FUNNY_CHAT -> { d.social -= 0.4f; d.boredom -= 0.3f; d.contentment += 0.12f; if (subject.isNotBlank()) diary.add(Diary.Kind.CHAT_LINE, subject, now) }
            MindEvent.GAME_WON -> { d.contentment += 0.2f; d.boredom -= 0.5f; d.social -= 0.3f; diary.add(Diary.Kind.GAME_WON, subject, now) }
            MindEvent.GAME_LOST -> { d.annoyance += 0.05f; d.boredom -= 0.4f; d.social -= 0.3f; diary.add(Diary.Kind.GAME_LOST, subject, now) }
            MindEvent.SCREEN_OFF -> screenOffAt = now
            MindEvent.UNLOCK -> {
                val gone = if (screenOffAt > 0L) now - screenOffAt else 0L
                if (gone > 3 * 3600_000L) diary.add(Diary.Kind.USER_AWAY, "${gone / 3600_000L} hours", now)
                if (gone > 3600_000L) d.social += 0.2f
            }
            MindEvent.SWIPE_AWAY -> {
                // Strong signal: fewer interruptions in this app at this time of day (fades over a week).
                val k = bucket(scene.cat, scene.minute)
                swipePenalty[k] = (learned(scene, now) + 0.15f).coerceAtMost(0.5f) to now
                d.social -= 0.2f; d.annoyance += 0.03f
            }
            MindEvent.CRASH_LANDING -> diary.add(Diary.Kind.CRASH_LANDING, scene.app, now)
            MindEvent.POWER_USED -> diary.add(Diary.Kind.POWER, subject, now)
            MindEvent.FESTIVAL -> diary.add(Diary.Kind.FESTIVAL, subject, now)
            MindEvent.NAP_START -> napping = true
            MindEvent.WOKE -> napping = false
        }
        d.clamp()
        // Immediate reactions: rare on purpose.
        return when {
            e == MindEvent.HARD_SHAKE || (e == MindEvent.SHAKE && d.annoyance > 0.7f) ->
                if (canDo(Act.LEAVE, now)) decide(Act.LEAVE, now, "shaken too much: annoyance ${f(d.annoyance)}", rnd) else null
            e == MindEvent.UNLOCK -> habitDue(now, minute)?.let { h ->
                if (h == Habit.MORNING_STRETCH && !hardQuiet()) decide(Act.HABIT, now, "unlocked in the morning: ${h.label}", rnd, habit = h) else null
            }
            else -> null
        }
    }

    // ---------------- choice ----------------

    private enum class Trigger { SCENE, HEARTBEAT, PROPOSAL }

    private fun canDo(a: Act, now: Long) = now - (lastDone[a] ?: Long.MIN_VALUE / 2) >= a.cooldownMs

    fun habitDue(now: Long, minute: Int): Habit? {
        val today = diary.day(now).toString()
        return Habit.entries.firstOrNull { h ->
            minute in h.from..h.to && habitDone[h] != today && (habitDays[h]?.size ?: 0) >= h.needDays
        }
    }

    /** Utility of every candidate (for tests and the log). */
    fun scores(trigger: String, now: Long, nov: Float = 0f, proposal: Proposal? = null): Map<Act, Float> =
        rawScores(Trigger.valueOf(trigger), now, nov, proposal)

    private fun rawScores(trigger: Trigger, now: Long, nov: Float, proposal: Proposal?): Map<Act, Float> {
        val t = traits; val d = drives; val s = scene
        val fo = focus(now)
        val aff = personality.likes(s.cat)
        val out = LinkedHashMap<Act, Float>()
        out[Act.IGNORE] = 0.62f + 0.7f * fo
        when (trigger) {
            Trigger.SCENE -> if (away) {
                // left / hiding: a new app may be a reason to come back (not if it's another money app)
                if (s.cat != AppCat.BANKING) out[Act.COME_BACK] = 0.35f + 0.4f * d.social + 0.2f * nov - 0.5f * fo + (if (now >= awayUntil) 0.6f else 0f)
            } else {
                out[Act.GLANCE] = 0.3f + 0.35f * d.curiosity + 0.25f * nov + 0.15f * abs(aff)
                out[Act.EXPLORE] = 0.15f + 0.6f * nov * t.curiosity + 0.25f * maxOf(0f, aff) + 0.2f * d.curiosity + 0.15f * d.boredom - 0.25f * t.laziness - 0.3f * (1f - d.energy)
                out[Act.COMMENT] = 0.1f + 0.35f * t.chattiness + 0.35f * abs(aff) + 0.2f * nov + 0.15f * d.social
                out[Act.HIDE] = when { s.cat == AppCat.BANKING -> 1.6f; aff <= -0.5f -> 0.45f + 0.5f * -aff; else -> 0f }
                diary.all.lastOrNull { it.kind == Diary.Kind.EXPLORED && it.subject == s.app }?.let { e ->
                    out[Act.RECALL] = 0.25f + 0.4f * diary.effective(e, now) * t.chattiness
                }
            }
            Trigger.HEARTBEAT -> {
                if (away) {
                    val over = now >= awayUntil
                    out[Act.COME_BACK] = 0.3f + 0.6f * d.social + 0.3f * d.boredom - 0.5f * fo +
                        (if (over) 0.9f + minOf(1f, (now - awayUntil) / 60_000f * 0.05f) else 0f)
                } else {
                    val late = s.hour >= 23 || s.hour < 6
                    out[Act.NAP] = 0.1f + 0.9f * (1f - d.energy) + 0.25f * t.laziness - 0.3f * d.social - 0.2f * d.boredom + (if (late) 0.15f else 0f)
                    out[Act.GAG] = 0.15f + 0.45f * d.boredom + 0.3f * t.playfulness - 0.3f * (1f - d.energy)
                    out[Act.POWER] = 0.05f + 0.3f * t.boldness + 0.3f * d.boredom + 0.2f * t.drama - 0.35f * (1f - d.energy)
                    val longFocus = focusSince > 0L && now - focusSince > 5 * 60_000L
                    out[Act.LEAVE] = 0.6f * d.annoyance + 0.25f * (1f - d.social) - 0.2f * d.contentment + (if (longFocus) 0.75f + 0.6f * fo + minOf(0.3f, (now - focusSince - 5 * 60_000L) / (30 * 60_000f)) else 0f)
                    out[Act.SEEK_ATTENTION] = 0.1f + 0.8f * d.social - 0.2f * t.laziness
                    out[Act.GROOM] = 0.2f + 0.3f * d.contentment + 0.2f * t.laziness
                    out[Act.WANDER] = 0.2f + 0.35f * d.boredom + 0.2f * (1f - t.laziness) - 0.2f * (1f - d.energy)
                    habitDue(now, s.minute)?.let { out[Act.HABIT] = 1.15f }
                    if (diary.all.isNotEmpty()) out[Act.RECALL] = 0.1f + 0.35f * t.chattiness + 0.2f * d.social
                }
            }
            Trigger.PROPOSAL -> {
                val a = proposal!!.act
                out[a] = when (a) {
                    Act.GAG -> 0.2f + 0.45f * d.boredom + 0.35f * t.playfulness - 0.3f * (1f - d.energy)
                    Act.COMMENT -> 0.25f + 0.4f * t.chattiness + 0.3f * d.social
                    Act.POWER -> 0.15f + 0.35f * t.boldness + 0.3f * d.boredom + 0.2f * t.drama - 0.3f * (1f - d.energy)
                    Act.GLANCE -> 0.45f + 0.3f * d.curiosity
                    else -> 0.35f + 0.35f * d.boredom
                } + 0.15f   // a proposal already passed its own timer
            }
        }
        // Personality intensity / independence: how often it acts on its own at all.
        val indep = 0.75f + 0.5f * intensity
        for (k in out.keys.toList()) if (k != Act.IGNORE) {
            val loudPenalty = if (k.loud) 1.0f else 0.25f
            val repeat = recent.count { it == k } * 0.12f
            out[k] = out[k]!! * indep - fo * loudPenalty * (if (k == Act.HIDE || k == Act.LEAVE) 0f else 1f) - repeat
        }
        return out
    }

    private fun choose(trigger: Trigger, now: Long, rnd: Random, nov: Float, proposal: Proposal? = null): Decision {
        val sc = rawScores(trigger, now, nov, proposal)
        val quiet = hardQuiet()
        val allowed = sc.filterKeys { a ->
            canDo(a, now) && when {
                scene.keyboard || scene.typingBursts > 0 -> a == Act.IGNORE                 // never interrupt typing
                quiet -> a in QUIET_OK && (a != Act.COME_BACK || now >= awayUntil)
                else -> true
            }
        }
        // a little randomness so it's never a vending machine
        val noisy = allowed.mapValues { (a, v) -> if (a == Act.IGNORE) v + rnd.nextFloat() * 0.1f else v + rnd.nextFloat() * 0.2f }
        val best = noisy.maxByOrNull { it.value }?.key ?: Act.IGNORE
        val why = explain(trigger, best, now, nov, proposal)
        return decide(best, now, why, rnd, scores = noisy)
    }

    private fun f(v: Float) = "%.2f".format(Locale.ROOT, v)

    private fun explain(trigger: Trigger, a: Act, now: Long, nov: Float, p: Proposal?): String {
        val d = drives; val fo = focus(now)
        val place = com.combo.monkeycompanion.world.WorldPlaces.of(scene.pkg, scene.cat == AppCat.GAME)
        val what = when (trigger) {
            Trigger.SCENE -> "Visited ${place.worldName} (${scene.app}, ${scene.cat.label})" + if (nov > 0.6f) ", new to me" else ""
            Trigger.HEARTBEAT -> if (away) "Away" else "Idle check"
            Trigger.PROPOSAL -> "Felt like a ${p!!.name.lowercase()}"
        }
        val top = listOf("curious" to d.curiosity, "bored" to d.boredom, "social" to d.social, "energy" to d.energy, "annoyed" to d.annoyance)
            .sortedByDescending { if (it.first == "energy") 1f - it.second else it.second }.take(2)
            .joinToString(", ") { "${it.first.replaceFirstChar { c -> c.uppercase() }} ${f(it.second)}" }
        val aff = personality.likes(scene.cat)
        val likes = if (trigger == Trigger.SCENE && abs(aff) >= 0.5f) (if (aff > 0) ", loves it" else ", dislikes it") else ""
        return "$what. $top, focus ${f(fo)}$likes → ${a.verb}"
    }

    private fun decide(a: Act, now: Long, why: String, rnd: Random, scores: Map<Act, Float> = emptyMap(), habit: Habit? = null): Decision {
        var line: String? = null; var rec: Diary.Entry? = null; var away = 0L; var llm = false
        var h = habit
        when (a) {
            Act.COMMENT -> { line = MindLines.comment(characterId, scene, personality, rnd); llm = habituation.count(scene.pkg, now) <= 1.01f && traits.chattiness > 0.5f && drives.curiosity > 0.5f }
            Act.RECALL -> {
                rec = diary.recall(now, rnd, subject = if (scene.pkg.isNotBlank()) scene.app else null) ?: diary.recall(now, rnd)
                if (rec == null) return Decision(Act.IGNORE, scores, "$why (nothing worth remembering) → ignored", scene)
                line = diary.recallLine(rec, characterId, now)
            }
            Act.LEAVE -> { away = (10 + rnd.nextInt(16)) * 60_000L; line = MindLines.leave(characterId) }
            Act.COME_BACK -> line = MindLines.back(characterId)
            Act.SEEK_ATTENTION -> line = MindLines.attention(characterId, personality, rnd)
            Act.HIDE -> line = if (scene.cat == AppCat.BANKING) MindLines.respectHide(characterId) else MindLines.dislike(characterId, scene)
            Act.NAP -> line = null
            Act.HABIT -> { if (h == null) h = habitDue(now, scene.minute); line = MindLines.habit(characterId, h, personality) }
            else -> Unit
        }
        return Decision(a, scores, why, scene, line, rec, h, llm, away)
    }

    /** The host actually did it (or tried): remember for cooldowns, novelty, habits and the diary. */
    fun commit(d: Decision, now: Long) {
        val a = d.act
        if (a == Act.IGNORE) return
        lastDone[a] = now
        recent.addLast(a); while (recent.size > 8) recent.removeFirst()
        val today = diary.day(now).toString()
        Habit.entries.filter { it.act == a && scene.minute in it.from..it.to }.forEach { habitDays.getOrPut(it) { HashSet() }.add(today) }
        when (a) {
            Act.EXPLORE -> { diary.add(Diary.Kind.EXPLORED, scene.app, now); drives.curiosity -= 0.4f; drives.boredom -= 0.3f }
            Act.GLANCE -> drives.curiosity -= 0.15f
            Act.COMMENT -> drives.social -= 0.1f
            Act.GAG -> { drives.boredom -= 0.4f; drives.contentment += 0.05f }
            Act.POWER -> { drives.boredom -= 0.35f; drives.energy -= 0.05f }
            Act.NAP -> { napping = true; diary.add(Diary.Kind.SLEPT, personality.fav.napSpot.label, now) }
            Act.LEAVE -> { awayUntil = now + d.awayMs.coerceAtLeast(60_000L); drives.annoyance *= 0.5f; diary.add(Diary.Kind.LEFT, MindLines.excuse(characterId), now) }
            Act.COME_BACK -> { awayUntil = 0L; drives.boredom *= 0.6f }
            Act.SEEK_ATTENTION -> drives.social -= 0.1f
            Act.GROOM -> drives.contentment += 0.05f
            Act.WANDER -> drives.boredom -= 0.12f
            Act.HABIT -> d.habit?.let { habitDone[it] = today; diary.add(Diary.Kind.HABIT, it.label, now); if (it.act == Act.NAP) napping = true }
            Act.RECALL -> d.recall?.let { diary.markRecalled(it, now) }
            Act.HIDE -> awayUntil = now + 3 * 60_000L      // looks away from the money stuff for a bit
            Act.IGNORE -> Unit
        }
        drives.clamp()
    }

    /** "What's Settai thinking?" — its mood, explained in character. */
    fun thinking(now: Long): String {
        val d = drives; val fo = focus(now)
        val fav = personality.fav
        val bits = ArrayList<Pair<Float, String>>()
        bits += (1f - d.energy) to "Sleepy… dreaming of my nap spot (the ${fav.napSpot.label})."
        bits += d.boredom to "Bored. Plotting mischief 😈"
        bits += d.social to "Kinda missing you. A pet would be nice."
        bits += d.annoyance * 1.3f to "Still grumpy about earlier. Hmph."
        val placeName = com.combo.monkeycompanion.world.WorldPlaces.of(scene.pkg, scene.cat == AppCat.GAME).worldName
        bits += d.curiosity to "Curious what you're up to in $placeName (${scene.app})."
        bits += d.contentment * 0.9f to "Content. Life is good; a ${fav.snack} would make it perfect."
        bits += fo * 1.1f to "You look busy, so I'm being quiet 🤫"
        if (away) bits += 2f to "I'm on my ${MindLines.excuse(characterId)}. Back soon!"
        val top = bits.sortedByDescending { it.first }.take(2).map { it.second }
        val nums = "energy ${f(d.energy)} · bored ${f(d.boredom)} · social ${f(d.social)} · curious ${f(d.curiosity)} · grumpy ${f(d.annoyance)} · content ${f(d.contentment)}"
        return Diary.voice(characterId, top.joinToString(" ")) + "\n($nums)"
    }

    // ---------------- persistence (small text blob) ----------------

    fun encode(): String = buildString {
        appendLine("drives=${drives.encode()}")
        appendLine("tick=$lastTick")
        appendLine("away=$awayUntil")
        appendLine("off=$screenOffAt")
        appendLine("hab=${habituation.encode()}")
        appendLine("swipe=" + swipePenalty.entries.joinToString(";") { "${it.key}=${f(it.value.first)}@${it.value.second}" })
        appendLine("done=" + lastDone.entries.joinToString(";") { "${it.key.name}=${it.value}" })
        appendLine("hdays=" + habitDays.entries.joinToString(";") { "${it.key.name}=${it.value.sorted().takeLast(14).joinToString(",")}" })
        appendLine("hdone=" + habitDone.entries.joinToString(";") { "${it.key.name}=${it.value}" })
    }

    fun decode(s: String?) {
        if (s.isNullOrBlank()) return
        val m = s.lines().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()
        Drives.decode(m["drives"]).let { drives.energy = it.energy; drives.boredom = it.boredom; drives.curiosity = it.curiosity; drives.social = it.social; drives.annoyance = it.annoyance; drives.contentment = it.contentment }
        lastTick = m["tick"]?.toLongOrNull() ?: 0L
        awayUntil = m["away"]?.toLongOrNull() ?: 0L
        screenOffAt = m["off"]?.toLongOrNull() ?: 0L
        habituation.decode(m["hab"])
        fun pairs(k: String) = m[k].orEmpty().split(';').mapNotNull { e -> e.indexOf('=').takeIf { it > 0 }?.let { e.substring(0, it) to e.substring(it + 1) } }
        swipePenalty.clear(); pairs("swipe").forEach { (k, v) -> runCatching { val (a, b) = v.split('@'); swipePenalty[k] = a.toFloat() to b.toLong() } }
        lastDone.clear(); pairs("done").forEach { (k, v) -> runCatching { lastDone[Act.valueOf(k)] = v.toLong() } }
        habitDays.clear(); pairs("hdays").forEach { (k, v) -> runCatching { habitDays[Habit.valueOf(k)] = v.split(',').filter { it.isNotBlank() }.toMutableSet() } }
        habitDone.clear(); pairs("hdone").forEach { (k, v) -> runCatching { habitDone[Habit.valueOf(k)] = v } }
    }
}
