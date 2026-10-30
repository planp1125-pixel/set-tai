package com.combo.monkeycompanion.v5

import kotlin.random.Random

/** v5 mini games on the overlay. Pure game logic (the overlay draws them and feeds input). */
object Games {
    enum class Game(val id: String, val title: String, val icon: String) {
        BANANA("banana", "Catch bananas", "🍌"), STARE("stare", "Staring contest", "👀"), RPS("rps", "Rock paper scissors", "✊")
    }
    fun byId(id: String) = Game.entries.firstOrNull { it.id == id }

    /** Best scores / wins, persisted as "banana=12,stare_w=3,stare_l=1,rps_w=4,rps_l=2,rps_d=1". */
    data class Scores(val m: Map<String, Int> = emptyMap()) {
        operator fun get(k: String) = m[k] ?: 0
        fun with(k: String, v: Int) = Scores(m + (k to v))
        fun inc(k: String) = with(k, get(k) + 1)
        fun best(k: String, v: Int) = if (v > get(k)) with(k, v) else this
        fun encode() = m.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }
        companion object {
            fun decode(s: String?) = Scores(s.orEmpty().split(',').mapNotNull { kv ->
                kv.split('=').takeIf { it.size == 2 }?.let { (k, v) -> v.toIntOrNull()?.let { k to it } } }.toMap())
        }
    }
}

/**
 * Catch the falling bananas: 30 s, the character runs under them (drag it, or tilt the phone). Coordinates are
 * 0..1 across the screen width and 0 (top) .. 1 (ground) down. A banana within [CATCH_W] of the character when it
 * reaches the hands' height is caught; rotten ones cost a point.
 */
class BananaCatch(private val rnd: Random = Random, val seconds: Float = 30f) {
    data class Item(var x: Float, var y: Float, val speed: Float, val rotten: Boolean, var done: Boolean = false)
    val items = ArrayList<Item>()
    var playerX = 0.5f
    var score = 0; private set
    var caught = 0; private set
    var missed = 0; private set
    var t = 0f; private set
    private var spawnIn = 0.3f
    val over get() = t >= seconds
    /** Events since the last update: +1 caught, -1 rotten, 0 missed. */
    val events = ArrayList<Int>()

    companion object { const val CATCH_W = 0.12f; const val CATCH_Y = 0.86f; const val MAX_ITEMS = 8 }

    /** [target] = where the finger / tilt wants the character (0..1); it runs there at a capped speed. */
    fun update(dt: Float, target: Float) {
        events.clear()
        if (over) return
        t += dt
        val maxMove = 1.1f * dt
        playerX += (target.coerceIn(0f, 1f) - playerX).coerceIn(-maxMove, maxMove)
        spawnIn -= dt
        if (spawnIn <= 0f && items.count { !it.done } < MAX_ITEMS) {
            val hard = (t / seconds).coerceIn(0f, 1f)
            items.add(Item(0.06f + rnd.nextFloat() * 0.88f, -0.05f, 0.32f + 0.35f * hard + rnd.nextFloat() * 0.15f, rnd.nextFloat() < 0.15f))
            spawnIn = 0.9f - 0.45f * hard + rnd.nextFloat() * 0.3f
        }
        for (b in items) {
            if (b.done) continue
            val y0 = b.y
            b.y += b.speed * dt
            if (y0 < CATCH_Y && b.y >= CATCH_Y && kotlin.math.abs(b.x - playerX) <= CATCH_W) {
                b.done = true
                if (b.rotten) { score = maxOf(0, score - 1); events.add(-1) } else { score++; caught++; events.add(1) }
            } else if (b.y > 1.05f) { b.done = true; if (!b.rotten) { missed++; events.add(0) } }
        }
        items.removeAll { it.done }
    }
}

/**
 * Staring contest: you and the character stare; first to blink loses. You tap when YOU blink (honour system);
 * the character blinks at a random moment between [minS] and [maxS] (a higher friendship level stares longer).
 */
class StaringContest(rnd: Random = Random, minS: Float = 4f, maxS: Float = 14f) {
    enum class Result { NONE, YOU_WIN, YOU_LOSE }
    val blinkAt = minS + rnd.nextFloat() * (maxS - minS)
    var t = 0f; private set
    var result = Result.NONE; private set
    /** Seconds the character's eyes are watering (just before it blinks) for the drawing. */
    val watering get() = (t - (blinkAt - 1.5f)).coerceIn(0f, 1.5f) / 1.5f

    fun update(dt: Float) {
        if (result != Result.NONE) return
        t += dt
        if (t >= blinkAt) result = Result.YOU_WIN
    }
    /** You blinked (tap). Before the character did: you lose. */
    fun userBlinked() { if (result == Result.NONE) result = Result.YOU_LOSE }
}

/** Rock-paper-scissors. The character throws at random (it doesn't peek, promise). */
object Rps {
    enum class Hand(val icon: String, val label: String) { ROCK("✊", "Rock"), PAPER("✋", "Paper"), SCISSORS("✌️", "Scissors") }
    enum class Outcome { WIN, LOSE, DRAW }
    fun beats(a: Hand, b: Hand) = (a == Hand.ROCK && b == Hand.SCISSORS) || (a == Hand.PAPER && b == Hand.ROCK) || (a == Hand.SCISSORS && b == Hand.PAPER)
    /** Outcome for the user. */
    fun play(user: Hand, settai: Hand) = when { user == settai -> Outcome.DRAW; beats(user, settai) -> Outcome.WIN; else -> Outcome.LOSE }
    fun throwHand(rnd: Random = Random) = Hand.entries[rnd.nextInt(3)]
    fun parse(s: String): Hand? {
        val t = s.lowercase()
        return when {
            "rock" in t || "✊" in t || "kal" in t -> Hand.ROCK
            "paper" in t || "✋" in t || "kaagitham" in t -> Hand.PAPER
            "scissor" in t || "✌" in t || "kathiri" in t -> Hand.SCISSORS
            else -> null
        }
    }
    fun line(o: Outcome, mine: Hand, characterId: String) = when (o) {
        Outcome.WIN -> "${mine.icon} I lost?! Rematch! " + if (characterId == "superboy") "Heroes don't cry… much 😭" else "😤"
        Outcome.LOSE -> "${mine.icon} I WIN! 🎉 " + if (characterId == "mask") "The mask sees all 😎" else "Monkey brain wins!"
        Outcome.DRAW -> "${mine.icon} Same same! Again? 🤝"
    }
}

/** "let's play" / "game" in chat. */
object GameChat {
    fun wantsGame(text: String): Boolean {
        val t = text.lowercase()
        return listOf("let's play", "lets play", "play a game", "game venum", "vilayadalam", "play with me", "mini game").any { it in t }
    }
    fun which(text: String): Games.Game? {
        val t = text.lowercase()
        return when {
            "banana" in t || "catch" in t -> Games.Game.BANANA
            "stare" in t || "staring" in t || "blink" in t -> Games.Game.STARE
            "rock" in t || "paper" in t || "scissor" in t || "rps" in t -> Games.Game.RPS
            else -> null
        }
    }
}
