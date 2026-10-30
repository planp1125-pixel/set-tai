package com.combo.monkeycompanion.v5

import kotlin.random.Random

/**
 * v5 "two phones, two Settais": a tiny line protocol over a local TCP socket found with NSD (same Wi-Fi), no Google
 * Play Services. Only explicit pairing: the inviter shows a 4-digit code, the other phone types the same code and
 * its user taps Accept. After that only whitelisted character / animation events travel: no text, no files, nothing
 * about the phone or its owner.
 *
 *   SETTAI1 HELLO <name> <characterId> <codeHash>
 *   SETTAI1 ACCEPT <name> <characterId>   |  SETTAI1 REJECT
 *   SETTAI1 EV <event> [side]             |  SETTAI1 BYE
 */
object PairProtocol {
    const val SERVICE_TYPE = "_settai._tcp."
    const val MAGIC = "SETTAI1"
    const val MAX_LINE = 120
    const val MIN_EVENT_GAP_MS = 400L

    enum class Event(val id: String) { WAVE("wave"), JUMP_ACROSS("jump"), FIGHT("fight"), DANCE("dance"), ARRIVED("arrived") }
    enum class Side { LEFT, RIGHT }

    sealed class Msg {
        data class Hello(val name: String, val characterId: String, val codeHash: String) : Msg()
        data class Accept(val name: String, val characterId: String) : Msg()
        object Reject : Msg()
        data class Ev(val event: Event, val side: Side? = null) : Msg()
        object Bye : Msg()
    }

    fun newCode(rnd: Random = Random) = (1000 + rnd.nextInt(9000)).toString()

    /** The code itself never travels: a salted hash of it does (salt = both names, so it can't be replayed elsewhere). */
    fun hash(code: String, name: String): String {
        var h = 1125899906842597L
        for (ch in "$MAGIC|$name|$code") h = 31 * h + ch.code
        return java.lang.Long.toHexString(h and 0xFFFFFFFFFFFFL)
    }

    private val SAFE = Regex("[^A-Za-z0-9_\\-]")
    fun clean(s: String) = SAFE.replace(s, "_").take(24).ifEmpty { "_" }
    private val CHARACTERS = setOf("monkey", "superboy", "armour", "mask")

    fun encode(m: Msg): String = when (m) {
        is Msg.Hello -> "$MAGIC HELLO ${clean(m.name)} ${clean(m.characterId)} ${clean(m.codeHash)}"
        is Msg.Accept -> "$MAGIC ACCEPT ${clean(m.name)} ${clean(m.characterId)}"
        Msg.Reject -> "$MAGIC REJECT"
        is Msg.Ev -> "$MAGIC EV ${m.event.id}" + (m.side?.let { " ${it.name}" } ?: "")
        Msg.Bye -> "$MAGIC BYE"
    }

    /** Anything unexpected (too long, wrong magic, unknown event or character) is dropped. */
    fun decode(line: String?): Msg? {
        if (line == null || line.length > MAX_LINE) return null
        val p = line.trim().split(' ')
        if (p.size < 2 || p[0] != MAGIC) return null
        return when (p[1]) {
            "HELLO" -> if (p.size == 5 && p[3] in CHARACTERS) Msg.Hello(clean(p[2]), p[3], clean(p[4])) else null
            "ACCEPT" -> if (p.size == 4 && p[3] in CHARACTERS) Msg.Accept(clean(p[2]), p[3]) else null
            "REJECT" -> Msg.Reject
            "BYE" -> Msg.Bye
            "EV" -> Event.entries.firstOrNull { it.id == p.getOrNull(2) }?.let { e ->
                val side = p.getOrNull(3)?.let { s -> Side.entries.firstOrNull { it.name == s } }
                if (e == Event.JUMP_ACROSS && side == null) null else Msg.Ev(e, side)
            }
            else -> null
        }
    }

    /** Pairing state machine on each phone. */
    class Session(val myName: String, val myCharacter: String) {
        enum class State { IDLE, INVITING, ASKED, PAIRED, CLOSED }
        var state = State.IDLE; private set
        var code: String? = null; private set
        var peerName: String? = null; private set
        var peerCharacter: String? = null; private set
        private var lastEventAt = 0L

        /** Inviter: show this code. */
        fun invite(c: String): String { code = c; state = State.INVITING; return c }

        /** Joiner: user typed [typed] → HELLO to send. */
        fun join(typed: String): Msg.Hello { code = typed; state = State.ASKED; return Msg.Hello(myName, myCharacter, hash(typed, myName)) }

        /** Inviter got a HELLO: true when the code matches (then ask the user to Accept). */
        fun checkHello(h: Msg.Hello): Boolean {
            val c = code ?: return false
            if (state != State.INVITING || h.codeHash != hash(c, h.name)) return false
            peerName = h.name; peerCharacter = h.characterId
            return true
        }
        /** Inviter's user tapped Accept. */
        fun accept(): Msg.Accept { state = State.PAIRED; return Msg.Accept(myName, myCharacter) }
        fun reject(): Msg { state = State.CLOSED; return Msg.Reject }

        /** Joiner got the answer. */
        fun onAnswer(m: Msg) {
            if (state != State.ASKED) return
            state = if (m is Msg.Accept) { peerName = m.name; peerCharacter = m.characterId; State.PAIRED } else State.CLOSED
        }

        /** Events only while paired, rate-limited. */
        fun allowEvent(nowMs: Long): Boolean {
            if (state != State.PAIRED || nowMs - lastEventAt < MIN_EVENT_GAP_MS) return false
            lastEventAt = nowMs; return true
        }
        fun close() { state = State.CLOSED }
    }

    /** Jump across: I leave by [side]; the other phone's visitor enters from the opposite edge. */
    fun enterSide(exitSide: Side) = if (exitSide == Side.LEFT) Side.RIGHT else Side.LEFT
}
