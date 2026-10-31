package com.combo.monkeycompanion.brain

import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.Animations
import com.combo.monkeycompanion.speech.Lines
import com.combo.monkeycompanion.speech.Speech
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** What the brain needs from the world to move the monkey around horizontally. */
interface MonkeyBody {
    val canMove: Boolean
    /** Monkey's horizontal centre in screen px. */
    fun centerX(): Float
    fun minCenterX(): Float
    fun maxCenterX(): Float
    fun moveCenterTo(x: Float)
    val density: Float
    /** The edge the monkey is on; [centerX] etc. measure along it (vertical on side walls). */
    val edge: Edge get() = Edge.BOTTOM
    /** Hop onto another edge at the nearest corner. */
    fun transfer(to: Edge) {}
    /** Let go and fall to the bottom; the body calls [MoodController.onLanded] on touchdown. */
    fun startFall() {}
    /** The world may start its own adventure (climb a wall, hide behind a widget...). Return true if it did. */
    fun roam(r: Float, roamOn: Boolean): Boolean = false
}

/**
 * The monkey's "brain": owns the current [MonkeyState], mood (anger memory),
 * autonomous behaviour and reactions to touch. Later versions plug phone
 * sensors (heat, battery, DND) and the AI chat in here.
 */
class MoodController(private val speech: Speech, private val body: MonkeyBody?) {
    /** v4: the 4th rude poke within 20 s (the host may answer with Kovam Mode). */
    var onFurious: (() -> Unit)? = null
    var state = MonkeyState.IDLE; private set
    var stateTime = 0f; private set
    /** +1 faces right, -1 faces left. */
    var facing = 1f; private set
    val anim = AnimContext()

    private var stateDuration = 0f
    private var nextDecision = 2.5f
    private var walkTarget = 0f
    private var walkSpeedDp = 70f

    /** Times (seconds, monotonic) of recent bad touches. */
    private val badTouches = ArrayDeque<Float>()
    private var clock = 0f
    private var grumpy = 0f            // 0..1 decays over time
    private var lastTickle = -10f
    private var tickleStreak = 0
    private var pendingFleeFrom: Float? = null

    /** Seconds (of screen-on life) until the next spontaneous dance: every 1-3 minutes. */
    private var danceIn = 60f + Random.nextFloat() * 120f
    private var spinFlip = 0f
    /** True while a chat reply is being thought about / spoken: no wandering off. */
    var chatHold = false; private set

    /** Asked for an unprompted AI quip; returns false if none is possible right now. */
    var quipRequester: (() -> Boolean)? = null
    /** Average seconds between quips (0 = off). */
    var quipEverySec = 0f
    private var quipIn = 120f
    /**
     * v6 Settai mind: the old random timers only *propose* (dance / gag / quip / idle chatter); the mind decides whether
     * it actually feels like it right now. Null = always yes (wallpaper / previews / tests without a mind).
     */
    var mindGate: ((com.combo.monkeycompanion.mind.Proposal) -> Boolean)? = null
    private fun mindOk(p: com.combo.monkeycompanion.mind.Proposal) = mindGate?.invoke(p) != false

    fun greet() = speech.say(Lines.pick(Lines.HELLO))

    /**
     * 1 = normal; lower (battery saver ~0.45) = fewer autonomous moves, dances, gags and quips.
     * Scales the random timers, not reactions to the user.
     */
    var liveliness = 1f
    /** v7.0: movement speed multiplier from lasting world conditions (hungry/cramped = slower). */
    var worldSpeed = 1f
        set(v) { field = v.coerceIn(0.2f, 1.5f) }
    /** Random gags while idle on the floor. */
    var gagsOn = true
    /** Seconds until the next spontaneous gag (2.5-6 min at normal liveliness). */
    private var gagIn = 150f + Random.nextFloat() * 210f
    private var gagLine: String? = null
    private var gagLineAt = 0f
    private var lastGag: Gag? = null
    /** Seconds since the user last touched / talked to me (for bored lines). */
    var quietFor = 0f; private set
    fun touched() { quietFor = 0f }
    /** v10.8: 0..1 grumpiness (living reactions read it; legacy taps handle grumpy moods). */
    val grumpyLevel: Float get() = grumpy
    val isGagging get() = state == MonkeyState.GAG

    /** Play a gag. Returns false if it can't be done right now (dragged, asleep, on a wall...). */
    fun gag(gIn: Gag, talk: Boolean = true): Boolean {
        val g = com.combo.monkeycompanion.character.CharacterRegistry.current.let { it.mapGag(gIn, com.combo.monkeycompanion.character.CharacterRegistry.wear(it)) }
        if (state == MonkeyState.DRAGGED || state == MonkeyState.FALLING || state == MonkeyState.SLEEP ||
            state == MonkeyState.HIDDEN || state == MonkeyState.ENTRANCE || state == MonkeyState.DASH) return false
        if (edge != Edge.BOTTOM && edge != Edge.FREE) return false
        afterWalk = null; anim.angryWalk = false
        anim.gag = g; lastGag = g
        enter(MonkeyState.GAG, g.seconds)
        gagIn = max(gagIn, 60f / liveliness)
        gagLine = if (talk) Lines.gag(g) else null
        gagLineAt = when (g) {
            Gag.SNEEZE -> 1.75f; Gag.SLIP -> 2.0f; Gag.FAKE_SLEEP -> 2.6f; Gag.SELFIE -> 0.5f
            Gag.CHASE_FLY -> 3.4f; Gag.AIR_GUITAR -> 2.8f; else -> 0.6f
        }
        when (g) { Gag.SNEEZE -> Lines.note(Lines.Event.SNEEZED); Gag.SLIP -> Lines.note(Lines.Event.SLIPPED); else -> Unit }
        return true
    }

    private fun randomGag(): Gag {
        var g: Gag
        do { g = Gag.entries.random() } while (g == lastGag)
        return g
    }

    /** Settings: roam the screen edges (climb walls, hang from the top, drop down). */
    var roam = true
    private var afterWalk: (() -> Unit)? = null
    private var wakeLine: String? = null
    private val edge get() = body?.edge ?: Edge.BOTTOM

    /** v3: walls and ceilings are drawn by the profile climber, so front poses are always "ground" poses. */
    private fun syncEdge() { anim.edge = AnimContext.GROUND }

    /** Double-tap handler (v3: opens chat). Null = dance like before. */
    var doubleTapAction: (() -> Unit)? = null

    /** Play a state chosen by the world / an AI action. [duration] 0 = until changed. */
    fun play(s: MonkeyState, duration: Float = 0f, startAt: Float = 0f) {
        afterWalk = null; anim.angryWalk = false; chatHold = false
        enter(s, duration)
        stateTime = startAt
        if (duration > 0f) nextDecision = max(nextDecision, 1.5f)
    }

    /** Back to idle (e.g. the world finished a move). */
    fun idleNow(decideIn: Float = 2.5f) { enter(MonkeyState.IDLE, 0f); nextDecision = decideIn }

    /** Walk / climb along the current surface, then run [then] (or idle). */
    fun walkTo(along: Float, speed: Float = 1f, then: (() -> Unit)? = null) {
        startWalkTo(along, speed)
        if (state == MonkeyState.WALK) afterWalk = then else then?.invoke()
    }

    fun spinDance() = startDance(intArrayOf(Animations.MOVE_SPIN, Random.nextInt(Animations.MOVE_COUNT)), talk = false)
    fun danceNow(talk: Boolean = true) = startDance(randomRoutine(), talk)

    // ---------- sleep / wake ----------

    /** Screen going off: curl up asleep (the body moves to the bottom first). */
    fun sleep() {
        afterWalk = null; pendingFleeFrom = null; anim.angryWalk = false; chatHold = false
        syncEdge()
        enter(MonkeyState.SLEEP, 0f)
    }

    val isAsleep get() = state == MonkeyState.SLEEP

    /** User unlocked the phone: crazy wake-up routine then a wake-up line. */
    fun wakeUp(line: String) {
        if (state != MonkeyState.SLEEP) return
        wakeLine = line
        syncEdge()
        enter(MonkeyState.WAKE, Animations.WAKE_SECONDS)
        nextDecision = 5f
    }

    /** Touched down after a fall. */
    fun onLanded() {
        syncEdge()
        enter(MonkeyState.LANDING, 0.45f)
        if (Random.nextFloat() < 0.6f) speech.say(Lines.pick(Lines.LAND))
    }

    fun update(dt: Float) {
        clock += dt
        stateTime += dt
        quietFor += dt
        grumpy = max(0f, grumpy - dt / 9f)
        anim.anger = if (state == MonkeyState.ANGRY) 1f else grumpy
        anim.talking = speech.isSpeaking
        syncEdge()

        if (state == MonkeyState.IDLE || state == MonkeyState.WALK) { danceIn -= dt; gagIn -= dt }
        if (quipEverySec > 0f && state == MonkeyState.IDLE && !chatHold && !speech.isSpeaking) quipIn -= dt

        when (state) {
            MonkeyState.IDLE -> {
                nextDecision -= dt
                if (chatHold) Unit
                else if (danceIn <= 0f) { danceIn = (60f + Random.nextFloat() * 120f) / liveliness; if (mindOk(com.combo.monkeycompanion.mind.Proposal.DANCE)) startDance(randomRoutine(), Random.nextFloat() < 0.5f) }
                else if (gagIn <= 0f) {
                    gagIn = (150f + Random.nextFloat() * 210f) / liveliness
                    if (gagsOn && !speech.isSpeaking && mindOk(com.combo.monkeycompanion.mind.Proposal.GAG)) gag(randomGag(), talk = Random.nextFloat() < 0.75f)
                }
                else if (quipEverySec > 0f && quipIn <= 0f) {
                    quipIn = quipEverySec * (0.6f + Random.nextFloat() * 0.8f)
                    if (mindOk(com.combo.monkeycompanion.mind.Proposal.QUIP)) quipRequester?.invoke()
                }
                else if (nextDecision <= 0f) decide()
            }
            MonkeyState.THINKING, MonkeyState.TALKING -> Unit
            MonkeyState.DANCE -> {
                if (Animations.danceMove(stateTime, anim) == Animations.MOVE_SPIN) {
                    spinFlip -= dt
                    if (spinFlip <= 0f) { facing = -facing; spinFlip = 0.22f }
                }
                if (stateTime >= stateDuration) toIdle()
            }
            MonkeyState.WALK -> stepWalk(dt)
            MonkeyState.GAG -> {
                gagLine?.let { if (stateTime >= gagLineAt) { gagLine = null; speech.say(it) } }
                if (stateTime >= stateDuration) toIdle()
            }
            MonkeyState.DRAGGED, MonkeyState.FALLING, MonkeyState.SLEEP,
            MonkeyState.ENTRANCE, MonkeyState.HIDDEN, MonkeyState.DASH -> Unit
            // v10.17: a timed peek (look-around, notification peek) ends by itself; before, it could stay stuck in PEEK
            // forever, which blocked auto-rest (the old "flaky" RestAndScreenTest) and froze the character
            MonkeyState.PEEK -> if (stateDuration > 0f && stateTime >= stateDuration) toIdle()
            MonkeyState.WAKE -> {
                if (stateTime in 3.4f..4.7f) {
                    spinFlip -= dt
                    if (spinFlip <= 0f) { facing = -facing; spinFlip = 0.16f }
                } else if (stateTime > 4.7f) {
                    facing = 1f
                    wakeLine?.let { wakeLine = null; speech.say(it, 4500L) }
                }
                if (stateTime >= stateDuration) { wakeLine?.let { speech.say(it) }; wakeLine = null; toIdle() }
            }
            else -> if (stateDuration > 0f && stateTime >= stateDuration) {
                val flee = pendingFleeFrom
                pendingFleeFrom = null
                if (state == MonkeyState.ANGRY && flee != null) fleeFrom(flee) else toIdle()
            }
        }
    }

    // ---------- autonomous life ----------

    private fun decide() {
        val r = Random.nextFloat()
        val b = body
        val canWalk = b?.canMove == true
        if (canWalk && b != null && b.roam(r, roam)) return
        when {
            grumpy > 0.4f && r < 0.25f -> { if (mindOk(com.combo.monkeycompanion.mind.Proposal.CHATTER)) speech.say(Lines.pick(Lines.GRUMPY)); toIdle() }
            canWalk && r < 0.6f -> startWalkTo(randomSpot(), 1f)
            r < 0.66f -> if (mindOk(com.combo.monkeycompanion.mind.Proposal.DANCE)) startDance(randomRoutine(), talk = Random.nextFloat() < 0.4f) else toIdle()
            r < 0.90f -> { if (mindOk(com.combo.monkeycompanion.mind.Proposal.CHATTER)) speech.say(Lines.idle(quietSeconds = quietFor)); toIdle() }
            else -> toIdle()
        }
    }

    private fun hopTo(e: Edge, lines: List<String>?) {
        val b = body ?: return toIdle()
        b.transfer(e)
        syncEdge()
        enter(MonkeyState.LANDING, 0.4f)
        nextDecision = 1.5f
        if (lines != null && Random.nextFloat() < 0.6f) speech.say(Lines.pick(lines))
    }

    fun face(f: Float) { if (f != 0f) facing = if (f > 0f) 1f else -1f }

    fun drop() {
        val b = body ?: return toIdle()
        afterWalk = null
        enter(MonkeyState.FALLING, 0f)
        if (Random.nextFloat() < 0.7f) speech.say(Lines.pick(Lines.FALL))
        b.startFall()
    }

    private fun randomSpot(): Float {
        val b = body ?: return 0f
        val lo = b.minCenterX(); val hi = b.maxCenterX()
        val cur = b.centerX()
        repeat(6) {
            val x = lo + Random.nextFloat() * (hi - lo)
            if (abs(x - cur) > 80f * b.density) return x
        }
        return lo + Random.nextFloat() * (hi - lo)
    }

    private fun startWalkTo(x: Float, speedFactor: Float, angry: Boolean = false) {
        val b = body ?: return toIdle()
        walkTarget = x.coerceIn(b.minCenterX(), b.maxCenterX())
        if (abs(walkTarget - b.centerX()) < 4f) {
            val next = afterWalk; afterWalk = null
            return if (next != null) next() else toIdle()
        }
        facing = if (walkTarget > b.centerX()) 1f else -1f
        walkSpeedDp = when { edge.isWall -> 42f; edge == Edge.TOP -> 50f; else -> 70f } * speedFactor
        anim.walkSpeed = speedFactor
        anim.angryWalk = angry
        enter(MonkeyState.WALK, 0f)
        if (!angry && Random.nextFloat() < 0.12f * liveliness) speech.say(Lines.idle(quietSeconds = quietFor))
    }

    private fun stepWalk(dt: Float) {
        val b = body ?: return toIdle()
        val cur = b.centerX()
        val step = walkSpeedDp * worldSpeed * b.density * dt
        val d = walkTarget - cur
        if (abs(d) <= step) {
            b.moveCenterTo(walkTarget)
            anim.angryWalk = false
            val next = afterWalk; afterWalk = null
            if (next != null) next() else toIdle()
        } else {
            b.moveCenterTo(cur + step * if (d > 0) 1f else -1f)
        }
    }

    /** 2-3 different moves, each played for [Animations.MOVE_SECONDS]. */
    private fun randomRoutine(): IntArray {
        val moves = (0 until Animations.MOVE_COUNT).shuffled().take(2 + Random.nextInt(2))
        return moves.toIntArray()
    }

    private fun startDance(moves: IntArray, talk: Boolean) {
        anim.danceMoves = moves
        danceIn = max(danceIn, 45f)
        enter(MonkeyState.DANCE, moves.size * Animations.MOVE_SECONDS)
        if (talk) speech.say(Lines.pick(Lines.DANCE))
        Lines.note(Lines.Event.DANCED)
    }

    // ---------- chat ----------

    fun startThinking() {
        chatHold = true; touched()
        anim.angryWalk = false
        if (state != MonkeyState.DRAGGED) enter(MonkeyState.THINKING, 0f)
    }

    fun startTalking() {
        chatHold = true
        if (state != MonkeyState.DRAGGED && state != MonkeyState.TALKING) enter(MonkeyState.TALKING, 0f)
    }

    /** Reply finished (or failed); stay put a moment so it can be read. */
    fun endChat(holdSeconds: Float) {
        chatHold = false
        if (state == MonkeyState.THINKING || state == MonkeyState.TALKING) {
            enter(MonkeyState.IDLE, 0f)
            nextDecision = max(4f, holdSeconds)
        }
    }

    /** Settings for spontaneous quips changed. */
    fun configureQuips(everySec: Float) {
        quipEverySec = everySec
        if (everySec > 0f) quipIn = min(quipIn, everySec)
    }

    private fun toIdle() {
        enter(MonkeyState.IDLE, 0f)
        nextDecision = (2.5f + Random.nextFloat() * 5f) / liveliness
    }

    private fun enter(s: MonkeyState, duration: Float) {
        state = s; stateTime = 0f; stateDuration = duration
    }

    // ---------- touch ----------

    /** @param side -1 if the finger is left of the monkey's centre, +1 if right. */
    fun onTap(zone: TouchZone, side: Int) {
        if (state == MonkeyState.DRAGGED || state == MonkeyState.FALLING || state == MonkeyState.ENTRANCE ||
            state == MonkeyState.HIDDEN || state == MonkeyState.DASH || state == MonkeyState.JUMP) return
        if (state == MonkeyState.SLEEP) { wakeUp(Lines.pick(Lines.POKED_AWAKE)); return }
        afterWalk = null
        anim.angryWalk = false
        touched()
        when (zone) {
            TouchZone.PRIVATE -> badTouch(side)
            TouchZone.NECK, TouchZone.BELLY -> {
                if (clock - lastTickle < 6f) tickleStreak++ else tickleStreak = 0
                lastTickle = clock
                if (grumpy > 0.5f) { grumpyReply(); return }
                enter(MonkeyState.GIGGLE, 1.8f + 0.2f * tickleStreak.coerceAtMost(4))
                speech.say(Lines.pick(Lines.TICKLE))
                Lines.note(Lines.Event.TICKLED)
            }
            TouchZone.HEAD -> {
                enter(MonkeyState.HAPPY_PAT, 2.2f)
                if (grumpy > 0.3f) {
                    grumpy = max(0f, grumpy - 0.5f)
                    badTouches.clear()
                    speech.say(Lines.pick(Lines.FORGIVE))
                } else speech.say(Lines.pick(Lines.PAT))
            }
            TouchZone.TAIL -> {
                enter(MonkeyState.STARTLED, 1.0f)
                speech.say(Lines.pick(Lines.TAIL))
            }
            TouchZone.HAND, TouchZone.FOOT -> {
                if (grumpy > 0.5f) { grumpyReply(); return }
                anim.playfulFoot = zone == TouchZone.FOOT
                anim.playfulSide = if (side * facing.toInt() >= 0) 1 else -1
                enter(MonkeyState.PLAYFUL, 1.8f)
                speech.say(Lines.pick(if (zone == TouchZone.FOOT) Lines.FOOT else Lines.HAND))
            }
            TouchZone.NONE -> Unit
        }
    }

    private fun grumpyReply() {
        enter(MonkeyState.ANGRY, 0.8f)
        speech.say(Lines.pick(Lines.GRUMPY))
    }

    private fun badTouch(side: Int) {
        badTouches.addLast(clock)
        while (badTouches.isNotEmpty() && clock - badTouches.first() > 20f) badTouches.removeFirst()
        val level = badTouches.size
        grumpy = (grumpy + 0.45f).coerceAtMost(1f)
        val line = when {
            level >= 3 -> Lines.pick(Lines.BAD_3)
            level == 2 -> Lines.pick(Lines.BAD_2)
            else -> Lines.pick(Lines.BAD_1)
        }
        speech.say(line)
        Lines.note(Lines.Event.POKED_RUDE)
        enter(MonkeyState.ANGRY, if (level >= 2) 1.2f else 1.8f)
        if (level >= 4) { badTouches.clear(); onFurious?.let { it(); return } }
        // From the 2nd bad touch on, it storms off away from the finger.
        val b = body
        pendingFleeFrom = if (level >= 2 && b != null && b.canMove) b.centerX() + side * 10f else null
    }

    private fun fleeFrom(fingerX: Float) {
        val b = body ?: return toIdle()
        val dir = if (b.centerX() >= fingerX) 1f else -1f
        var target = b.centerX() + dir * 170f * b.density
        // Cornered? Run the other way.
        if (target > b.maxCenterX() || target < b.minCenterX()) target = b.centerX() - dir * 170f * b.density
        startWalkTo(target, 2.1f, angry = true)
    }

    fun onDoubleTap() {
        if (state == MonkeyState.DRAGGED || state == MonkeyState.FALLING || state == MonkeyState.SLEEP) return
        touched()
        doubleTapAction?.let { it(); return }
        afterWalk = null
        if (grumpy > 0.6f) { grumpyReply(); return }
        startDance(randomRoutine(), talk = true)
    }

    fun onDragStart() {
        anim.angryWalk = false
        afterWalk = null; wakeLine = null
        pendingFleeFrom = null; gagLine = null
        touched()
        enter(MonkeyState.DRAGGED, 0f)
        if (Random.nextFloat() < 0.6f) speech.say(Lines.pick(Lines.PICKUP))
    }

    fun onDragMove(vx: Float) { anim.dragVx = vx }

    /** Released. [falling]: the body will drop to the bottom and call [onLanded]. */
    fun onDragEnd(falling: Boolean = false) {
        anim.dragVx = 0f
        syncEdge()
        Lines.note(Lines.Event.DROPPED)
        if (falling) { enter(MonkeyState.FALLING, 0f); return }
        enter(MonkeyState.LANDING, 0.45f)
        val lines = when (edge) { Edge.TOP -> Lines.HANG; Edge.LEFT, Edge.RIGHT -> Lines.CLIMB; else -> Lines.DROP }
        if (Random.nextFloat() < 0.6f) speech.say(Lines.pick(lines))
    }

    /** Remote command (e.g. notification button). */
    fun dance() {
        if (state == MonkeyState.DRAGGED || state == MonkeyState.FALLING || state == MonkeyState.SLEEP ||
            state == MonkeyState.HIDDEN || state == MonkeyState.ENTRANCE) return
        startDance(randomRoutine(), talk = true)
    }
}
