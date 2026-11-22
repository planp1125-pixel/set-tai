package com.combo.monkeycompanion.speech

import com.combo.monkeycompanion.rig.Pose
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * v10.51 human pace: a streamed reply comes out word by word at a speaking pace, with a short pause at a
 * comma and a longer one at a full stop, instead of in network bursts. It never knows the end of the reply
 * in advance (like a person thinking while talking): it only lets out words that have fully arrived.
 *
 * Pure and deterministic (no Random, no clock): the overlay steps it from its frame loop with dt.
 */
class SpeechPacer {
    /** Words a second at normal speed (text only ~5; while the voice reads aloud the voice leads, see [gate]). */
    var wordsPerSec = 5f
    /** Everything received so far (plain text). */
    var target = ""; private set
    /** The final reply has arrived (no more words will come). */
    var done = false; private set
    /** Characters of [target] let out so far. */
    var shown = 0; private set
    /** While >= 0: never let out a word that starts at or after this character (the voice has not got there yet). */
    var gate = -1
    /** Sentences finished so far (a full stop, ! ? … or a new line). */
    var sentences = 0; private set

    private var wait = 0f
    private var wordT = 0f
    private var wordDur = 0f
    private var curStart = 0
    private var curEnd = 0

    val visible: String get() = target.substring(0, shown)
    /** Waiting for the network: everything that arrived is out, more is coming. */
    val starved: Boolean get() = !done && shown >= target.length
    /** The whole final reply is out and its last word has been said. */
    val finished: Boolean get() = done && shown >= target.length && wait <= 0f
    /** The word being said now ("" in a pause). */
    val word: String get() = if (speakingWord) target.substring(curStart, curEnd) else ""
    val speakingWord: Boolean get() = curEnd > curStart && wordT < wordDur
    /** The sentence the current word belongs to (for the hands). */
    val sentence: String get() {
        if (curEnd <= curStart) return ""
        var a = curStart
        while (a > 0 && !isStop(target[a - 1])) a--
        var b = curEnd
        while (b < target.length && !isStop(target[b - 1])) b++
        return target.substring(a, min(b, target.length)).trim()
    }

    fun reset() {
        target = ""; done = false; shown = 0; gate = -1; sentences = 0
        wait = 0f; wordT = 0f; wordDur = 0f; curStart = 0; curEnd = 0
    }

    /** New text from the stream (the whole reply so far). If the start changed, carry on from what both share. */
    fun feed(text: String, isDone: Boolean) {
        if (!text.startsWith(target.substring(0, shown))) {
            var i = 0
            val n = min(shown, text.length)
            while (i < n && text[i] == target[i]) i++
            shown = i; curStart = min(curStart, i); curEnd = min(curEnd, i)
        }
        target = text
        done = isDone
    }

    /** Tap: show the rest at once. */
    fun skip() { shown = target.length; wait = 0f; wordT = wordDur; gate = -1 }

    /** Advance by [dt] seconds. True when a new word came out. */
    fun step(dt: Float): Boolean {
        wait -= dt; wordT += dt
        if (wait > 0f) return false
        wait = 0f
        if (shown >= target.length) return false
        var i = shown
        while (i < target.length && target[i].isWhitespace()) i++
        var j = i
        while (j < target.length && !target[j].isWhitespace()) j++
        if (i >= target.length) { if (done) shown = target.length; return false }
        if (j >= target.length && !done) return false          // the last word may still be arriving
        if (gate >= 0 && i >= gate) return false              // the voice hasn't said it yet
        val w = target.substring(i, j)
        val left = wordsAfter(j)
        val hurry = when { left > 60 -> 1.8f; left > 25 -> 1.35f; else -> 1f }
        val speed = max(0.5f, wordsPerSec) * hurry
        wordDur = (0.55f + 0.09f * min(w.length, 12)) / speed
        val nl = target.indexOf('\n', j).let { it >= 0 && target.substring(j, it).isBlank() }
        val last = w.last()
        val pause = when {
            nl -> 0.6f
            last == '.' || last == '!' || last == '?' || last == '…' -> 0.5f
            last == ',' || last == ';' || last == ':' || last == '—' -> 0.25f
            else -> 0f
        } / hurry
        if (nl || isStop(last)) sentences++
        curStart = i; curEnd = j; wordT = 0f
        wait = wordDur + pause
        shown = j
        return true
    }

    /** 0..1 how open the mouth should be now: open on vowels, shut on m b p, closed in a pause. */
    fun mouth(): Float {
        if (!speakingWord) return 0f
        val w = target.substring(curStart, curEnd)
        val k = (wordT / wordDur).coerceIn(0f, 0.999f)
        val idx = (k * w.length).toInt().coerceIn(0, w.length - 1)
        val c = w[idx]
        // a little jaw bounce inside each letter so long vowels don't look frozen
        val bounce = 0.85f + 0.15f * sin(k * w.length * PI.toFloat())
        return open(c, idx) * bounce
    }

    private fun wordsAfter(j: Int): Int {
        var n = 0; var inWord = false
        for (x in j until target.length) {
            val ws = target[x].isWhitespace()
            if (!ws && !inWord) n++
            inWord = !ws
        }
        return n
    }

    companion object {
        fun isStop(c: Char) = c == '.' || c == '!' || c == '?' || c == '…' || c == '\n'

        fun open(c: Char, i: Int): Float = when (c.lowercaseChar()) {
            'a', 'o' -> 0.9f
            'e', 'i' -> 0.62f
            'u', 'w' -> 0.45f
            'y' -> 0.5f
            'm', 'b', 'p' -> 0.05f
            'f', 'v' -> 0.22f
            in 'a'..'z' -> 0.32f
            else -> if (c.isLetter()) (if (i % 2 == 0) 0.75f else 0.35f)   // Tamil, Hindi...: syllable rhythm
                else 0.3f
        }
    }
}

/** v10.51: what the hands and face do while a sentence is said. */
enum class TalkGesture { EXPLAIN, ASK, EXCLAIM, SELF, LAUGH, SAD, THINK }

object Spoken {
    private val laugh = Regex("(?i)^(ha(ha)+|he(he)+|hi(hi)+|lol+|lmao|rofl)\\W*$|😂|🤣|😆|😹")
    private val filler = Regex("(?i)^(hmm+|umm*|uh+|er+m*|aah*|actually|wait|well)\\W*$")
    private val sad = Regex("(?i)\\b(sad|sorry|miss(ing)?|cry|crying|hurt|lonely|tired|upset|alone|kashtam|kavalai)\\b|😢|😭|😔|💔")
    private val self = Regex("(?i)\\b(i|i'm|im|i've|i'll|me|my|mine|myself|naan|enakku|ennoda|en)\\b")

    /** Which gesture fits [word] inside [sentence]. Laughs and thinking fillers win for their own word. */
    fun gesture(sentence: String, word: String): TalkGesture = when {
        laugh.containsMatchIn(word) -> TalkGesture.LAUGH
        filler.matches(word) -> TalkGesture.THINK
        sentence.trimEnd().endsWith("?") -> TalkGesture.ASK
        sentence.trimEnd().let { it.endsWith("!") || it.endsWith("!!") } -> TalkGesture.EXCLAIM
        sad.containsMatchIn(sentence) -> TalkGesture.SAD
        self.containsMatchIn(sentence) -> TalkGesture.SELF
        else -> TalkGesture.EXPLAIN
    }

    /**
     * Markdown that slipped through becomes plain speech: **bold** / __bold__ / `code` / # headings lose their marks,
     * "- item" / "* item" / "1. item" lose the bullet (each stays on its own line).
     */
    fun plain(t: String): String {
        if (t.isEmpty()) return t
        return t.lines().joinToString("\n") { line ->
            line.replace(Regex("^\\s{0,3}#{1,6}\\s+"), "")
                .replace(Regex("^\\s*[-*•]\\s+"), "")
                .replace(Regex("^\\s*(\\d{1,2})[.)]\\s+"), "$1, ")
                .replace("**", "").replace("__", "").replace("`", "")
        }
    }

    private fun mix(a: Float, b: Float, k: Float) = a + (b - a) * k

    /**
     * Blend the talking body toward [g] by [k] (0..1). [t] = clock for small loops. Only changes arms, head,
     * eyes and brows: the mouth is set separately from [SpeechPacer.mouth].
     */
    fun body(p: Pose, g: TalkGesture, k: Float, t: Float) {
        if (k <= 0.001f) return
        when (g) {
            TalkGesture.EXPLAIN -> Unit
            TalkGesture.ASK -> {
                p.headRot = mix(p.headRot, 11f, k)
                p.armLRaise = mix(p.armLRaise, 42f, k); p.armLElbow = mix(p.armLElbow, -72f, k)
                p.armRRaise = mix(p.armRRaise, 42f, k); p.armRElbow = mix(p.armRElbow, -72f, k)
                p.browY = mix(p.browY, -2.5f, k); p.lookY = mix(p.lookY, -0.8f, k)
            }
            TalkGesture.EXCLAIM -> {
                p.armLRaise = mix(p.armLRaise, 118f + 8f * sin(t * 7f), k)
                p.armRRaise = mix(p.armRRaise, 118f - 8f * sin(t * 7f), k)
                p.armLElbow = mix(p.armLElbow, -20f, k); p.armRElbow = mix(p.armRElbow, -20f, k)
                p.eyeOpen = mix(p.eyeOpen, 1.25f, k); p.mouthSmile = mix(p.mouthSmile, 0.9f, k)
                p.bodyY = mix(p.bodyY, -2.5f * abs(sin(t * 7f)), k)
            }
            TalkGesture.SELF -> {
                p.armRRaise = mix(p.armRRaise, 62f, k); p.armRElbow = mix(p.armRElbow, -125f, k)
                p.headRot = mix(p.headRot, -4f, k)
            }
            TalkGesture.LAUGH -> {
                p.eyesHappy = mix(p.eyesHappy, 1f, k); p.mouthSmile = mix(p.mouthSmile, 1f, k)
                p.bodyY = mix(p.bodyY, -2f * abs(sin(t * 16f)), k)
                p.armLRaise = mix(p.armLRaise, 30f, k); p.armRRaise = mix(p.armRRaise, 30f, k)
                p.headRot = mix(p.headRot, 6f * sin(t * 8f), k)
            }
            TalkGesture.SAD -> {
                p.browAngle = mix(p.browAngle, -12f, k); p.mouthSmile = mix(p.mouthSmile, -0.3f, k)
                p.armLRaise = mix(p.armLRaise, 8f, k); p.armRRaise = mix(p.armRRaise, 8f, k)
                p.lookY = mix(p.lookY, 2f, k); p.headY = mix(p.headY, 2.5f, k)
            }
            TalkGesture.THINK -> {
                p.armRRaise = mix(p.armRRaise, 72f, k); p.armRElbow = mix(p.armRElbow, -140f, k)
                p.lookY = mix(p.lookY, -3f, k); p.lookX = mix(p.lookX, 3f, k)
                p.browAngle = mix(p.browAngle, -5f, k)
            }
        }
    }
}
