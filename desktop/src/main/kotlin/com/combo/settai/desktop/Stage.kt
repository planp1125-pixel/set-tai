package com.combo.settai.desktop

// Desktop port of the Android MonkeyView (app/.../overlay/MonkeyView.kt): same surfaces, climber,
// edge hide, entrances, roaming and AI body actions; Android View/touch/IME bits swapped for Swing.

import java.awt.Toolkit
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.combo.monkeycompanion.ai.BodyAction
import com.combo.monkeycompanion.brain.Edge
import com.combo.monkeycompanion.brain.MonkeyBody
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.MoodController
import com.combo.monkeycompanion.brain.TouchZone
import com.combo.monkeycompanion.rig.Animations
import com.combo.monkeycompanion.rig.Animations.Ent
import com.combo.monkeycompanion.rig.Climber
import com.combo.monkeycompanion.rig.MonkeyRenderer
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.speech.CloudBubble
import com.combo.monkeycompanion.speech.Lines
import com.combo.monkeycompanion.speech.Speech
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.AlphaComposite
import javax.swing.JComponent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Hosts the living monkey: frame loop, pose blending, touch handling, the cloud speech bubble,
 * window geometry (overlay) and, since v3:
 * - surfaces: screen edges and (wallpaper) climbing spots; walls and ceilings use the profile
 *   [Climber] with IK hands/feet, floors use the front [MonkeyRig];
 * - edge hide: fling Settai at a screen edge and it ducks behind it, leaving a little tab;
 * - entrances: on wake / un-hide it peeks out of an edge, grips it and pulls itself up;
 * - body actions requested by the AI ([dance] tags, do_action tool) or by the user.
 */
class Stage(
    private val host: OverlayHost?,
    private val chat: DesktopChat,
    private val cfg: Config,
    private val onChatRequest: (() -> Unit)? = null,
) : JComponent(), Speech, DesktopChat.Listener {

    private val d = cfg.scale
    val boxW = (136 * d).toInt()
    val boxH = (150 * d).toInt()
    private val rigScale = 0.66f * d
    private val groundPad = 7 * d
    private val overlap = 22 * d
    private val wp = host?.wallpaper == true

    private val rig = MonkeyRig()
    private val renderer = MonkeyRenderer()
    private val climber = Climber(rigScale)
    private val current = Pose()
    private val target = Pose()
    private val drawPose = Pose()
    private val bubble = CloudBubble(d, 15f * d)
    private val prefs = Prefs(cfg)
    /** Main-thread handler (View.postDelayed never runs for the wallpaper's window-less view). */
    private val ui = UiHandler()

    /** Monkey box top-left in screen px. */
    private var mx = 0f
    private var my = 0f

    // ---------------- Surfaces ----------------

    private enum class SK { FLOOR, CEIL, WALL_L, WALL_R }

    /**
     * Something to stand on, hang from or climb. [fix] is the box's fixed coordinate (my for floors
     * and ceilings, mx for walls), [a]..[b] the range of the other one. [line] is the surface in
     * screen px (wall x / ceiling y / floor y) and [spanA]..[spanB] where hands and feet may grip.
     * [spot] -1 = screen edge, -2 = parked mid-screen, else a climbing-spot index.
     */
    private class Surf(val kind: SK, val fix: Float, a: Float, b: Float, val line: Float, val spanA: Float, val spanB: Float, val spot: Int = -1) {
        val a = min(a, (a + b) / 2f)
        val b = max(b, (a + b) / 2f)
        val wall get() = kind == SK.WALL_L || kind == SK.WALL_R
        val climb get() = kind != SK.FLOOR
        val edge get() = when (kind) { SK.FLOOR -> if (spot == -2) Edge.FREE else Edge.BOTTOM; SK.CEIL -> Edge.TOP; SK.WALL_L -> Edge.LEFT; SK.WALL_R -> Edge.RIGHT }
        val face get() = if (kind == SK.WALL_L) -1f else 1f
        fun same(o: Surf) = o.kind == kind && o.spot == spot
    }

    private var spots: List<RectF> = emptyList()
    private var surf = Surf(SK.FLOOR, 0f, 0f, 0f, 0f, 0f, 0f)

    private fun ground(): Float = host?.let { (it.screenHeight() - it.bottomInset() - boxH).toFloat() } ?: 0f
    private fun topY(): Float = (host?.topInset() ?: 0).toFloat()
    private fun sw(): Float = (host?.screenWidth() ?: boxW).toFloat()
    private fun wallMinY(): Float = topY() + 2 * d
    /** Wall climbing: hip sits at this fraction of the box height. */
    private val wallHip = 0.5f

    private fun screenSurf(k: SK): Surf {
        val g = ground(); val tY = topY(); val w = sw()
        val floorLine = g + boxH - groundPad
        return when (k) {
            SK.FLOOR -> Surf(k, g, 0f, max(0f, w - boxW), floorLine, 0f, w)
            SK.CEIL -> Surf(k, tY, 0f, max(0f, w - boxW), tY + 5 * d, 8 * d, w - 8 * d)
            SK.WALL_L -> Surf(k, 0f, wallMinY(), g, 2 * d, tY + 6 * d, floorLine)
            SK.WALL_R -> Surf(k, max(0f, w - boxW), wallMinY(), g, w - 2 * d, tY + 6 * d, floorLine)
        }
    }

    private fun spotSurf(i: Int, k: SK): Surf? {
        val r = spots.getOrNull(i) ?: return null
        val inX = 10 * d
        return when (k) {
            SK.FLOOR -> Surf(k, r.top - boxH + groundPad, r.left - boxW / 2f + inX, r.right - boxW / 2f - inX, r.top, r.left, r.right, i)
            SK.CEIL -> Surf(k, r.bottom - 5 * d, r.left - boxW / 2f + inX, r.right - boxW / 2f - inX, r.bottom, r.left + 4 * d, r.right - 4 * d, i)
            SK.WALL_R -> Surf(k, r.left + 2 * d - boxW, r.top - boxH * wallHip + 14 * d, r.bottom - boxH * wallHip - 8 * d, r.left, r.top, r.bottom, i)
            SK.WALL_L -> Surf(k, r.right - 2 * d, r.top - boxH * wallHip + 14 * d, r.bottom - boxH * wallHip - 8 * d, r.right, r.top, r.bottom, i)
        }
    }

    /** Re-derive the current surface (screen rotated, spots edited). */
    private fun refreshSurf() {
        val s = surf
        surf = when {
            s.spot == -2 -> Surf(SK.FLOOR, my, 0f, max(0f, sw() - boxW), my + boxH - groundPad, 0f, sw(), -2)
            s.spot >= 0 -> spotSurf(s.spot, s.kind) ?: screenSurf(SK.FLOOR)
            else -> screenSurf(s.kind)
        }
        if (surf.spot == -1 && s.spot >= 0) climber.deactivate()
        if (surf.climb && climber.active) climber.attach(modeOf(surf), surf.line, faceOn(surf), surf.spanA, surf.spanB)
    }

    private fun modeOf(s: Surf) = if (s.wall) Climber.Mode.WALL else Climber.Mode.CEIL
    private fun faceOn(s: Surf) = if (s.wall) s.face else (if (controller.facing >= 0f) 1f else -1f)

    private fun clampToSurf() {
        val s = surf
        if (s.wall) { mx = s.fix; my = my.coerceIn(s.a, s.b) } else { my = s.fix; mx = mx.coerceIn(s.a, s.b) }
    }

    // ---------------- Motion ----------------

    private var falling = false
    private var vy = 0f
    private var dragging = false
    private var tweenT = -1f
    private var tweenDur = 0.3f
    private var tweenArc = 0f
    private var tweenDone: (() -> Unit)? = null
    private var tFromX = 0f; private var tFromY = 0f; private var tToX = 0f; private var tToY = 0f
    private val tmpPt = FloatArray(2)
    private val tmpA = FloatArray(2)
    private val tmpB = FloatArray(2)
    private val tmpR = RectF()

    private val body = object : MonkeyBody {
        override val canMove get() = host != null && !hiddenTab
        override val edge get() = surf.edge
        override fun centerX() = if (surf.wall) my + boxH / 2f else mx + boxW / 2f
        override fun minCenterX() = surf.a + (if (surf.wall) boxH else boxW) / 2f
        override fun maxCenterX() = surf.b + (if (surf.wall) boxH else boxW) / 2f
        override fun moveCenterTo(x: Float) {
            if (surf.wall) my = x - boxH / 2f else mx = x - boxW / 2f
            clampToSurf()
        }
        override val density get() = d
        override fun transfer(to: Edge) {
            goSurf(screenSurf(when (to) { Edge.TOP -> SK.CEIL; Edge.LEFT -> SK.WALL_L; Edge.RIGHT -> SK.WALL_R; else -> SK.FLOOR }))
        }
        override fun startFall() = beginFall()
        override fun roam(r: Float, roamOn: Boolean): Boolean = this@Stage.roam(r, roamOn)
    }

    private fun beginFall() {
        surf = screenSurf(SK.FLOOR)
        climber.deactivate()
        tweenT = -1f; tweenDone = null
        falling = true; vy = 0f
    }

    /** Animate the box from (fx, fy) to its current (mx, my); [arc] = leap height in px. */
    private fun slideFrom(fx: Float, fy: Float, dur: Float, arc: Float = 0f, done: (() -> Unit)? = null) {
        tToX = mx; tToY = my; tFromX = fx; tFromY = fy
        mx = fx; my = fy
        tweenDur = dur; tweenT = 0f; tweenArc = arc; tweenDone = done
    }

    /** Move onto another surface (corner, reach across, or leap); climber limbs re-grip on the way. */
    private fun goSurf(n: Surf, dur: Float = 0.38f, arc: Float = 0f, done: (() -> Unit)? = null) {
        val fx = mx; val fy = my
        surf = n
        clampToSurf()
        if (n.climb) {
            if (climber.active) climber.attach(modeOf(n), n.line, faceOn(n), n.spanA, n.spanB) else startClimber(n)
        } else if (climber.active) {
            climber.deactivate()
            controller.face(if (mx + boxW / 2f > sw() / 2f) -1f else 1f)
        }
        slideFrom(fx, fy, dur, arc, done)
    }

    /** Begin climbing from wherever the front rig's hips and hands are now. */
    private fun startClimber(n: Surf) {
        rig.hipPoint(tmpPt); val hx = tmpPt[0] + winX; val hy = tmpPt[1] + winY
        rig.handPoint(true, tmpA); rig.handPoint(false, tmpB)
        val hands = floatArrayOf(tmpA[0] + winX, tmpA[1] + winY, tmpB[0] + winX, tmpB[1] + winY)
        val gy = oy + boxH - groundPad + winY
        val feet = floatArrayOf(hx - 8 * d, gy, hx + 8 * d, gy)
        climber.activate(modeOf(n), n.line, faceOn(n), n.spanA, n.spanB, hx, hy, hands, feet)
    }

    /** Start the climber fresh at the current box position (no front pose to start from). */
    private fun startClimberAtBox(n: Surf) {
        val ax = if (n.wall) n.line - n.face * 44f * rigScale else mx + boxW / 2f
        val ay = if (n.wall) my + boxH * wallHip else n.line + 45f * rigScale + 44f * rigScale
        val hands: FloatArray; val feet: FloatArray
        if (n.wall) {
            hands = floatArrayOf(n.line, ay - 60f * rigScale, n.line, ay - 50f * rigScale)
            feet = floatArrayOf(n.line, ay + 20f * rigScale, n.line, ay + 26f * rigScale)
        } else {
            hands = floatArrayOf(ax + 7f * rigScale, n.line, ax - 7f * rigScale, n.line)
            feet = floatArrayOf(ax, ay + 30f * rigScale, ax + 4f * rigScale, ay + 30f * rigScale)
        }
        climber.activate(modeOf(n), n.line, faceOn(n), n.spanA, n.spanB, ax, ay, hands, feet)
        repeat(30) { climberStep(1f / 60f) }
    }

    val controller = MoodController(this, if (host != null) body else null)

    // Window geometry (screen px) and where things sit inside the window.
    private var winX = 0; private var winY = 0; private var winW = 0; private var winH = 0
    private var ox = 0f; private var oy = 0f
    private var bubbleLeft = 0f; private var bubbleTop = 0f

    // Time
    private var t = 0f
    private var lastNanos = 0L
    private var running = false
    private var facingAnim = 1f
    private var blinkIn = 2f
    private var blinkT = -1f
    private var oddFrame = false

    // Bubble
    private var bubbleOn = false
    private var bubbleAge = 0f
    private var bubbleLife = 0f
    private var talkFor = 0f

    // ---------------- Edge hide / entrances ----------------

    /** Hidden behind a screen edge with only the tab showing (overlay). Persists. */
    private var hiddenTab = false
    /** -1 left, 1 right, 2 bottom. */
    private var hideSide = 1
    /** Tab centre along its edge (y for sides, x for the bottom), screen px. */
    private var tabPos = 0f
    /** Dash-and-duck animation time (>= 0 while hiding). */
    private var hideAnim = -1f
    /** Drawing offset that slides Settai behind an edge. */
    private var sinkX = 0f
    private var sinkY = 0f
    /** Where the edge Settai is ducking behind is (view y); everything below is clipped. */
    private var sinkClipY = Float.NaN

    private enum class EntKind { NONE, WAKE, UNHIDE, DUCK }
    private var entKind = EntKind.NONE
    /** 0 = rising from a floor (screen bottom or a spot's top), else -1/1 = sliding out of a side edge. */
    private var entSide = 0
    private var entLine: String? = null
    private var entRelocated = false
    private var hiddenFor = 0f
    private var peekDy = 0f

    init {
        if (host != null) {
            hiddenTab = !wp && prefs.getBoolean("hidden", false)
            hideSide = prefs.getInt("hideSide", 1)
            if (wp) spots = host.spots()
            surf = screenSurf(SK.FLOOR)
            mx = host.screenWidth() - boxW - 12 * d
            my = ground()
            clampToSurf()
            tabPos = prefs.getFloat("tabPos", host.screenHeight() * 0.45f)
            if (hiddenTab) { controller.play(MonkeyState.HIDDEN); clampTab() }
        }
        Animations.fill(current, MonkeyState.IDLE, 0f, 0f, controller.anim)
        controller.doubleTapAction = { onDoubleTapChat() }
    }

    // ---------------- Speech ----------------

    /** Remaining pages of a long reply, shown one after another. */
    private val pages = ArrayDeque<String>()
    private var thinkingBubble = false
    private var thinkDots = 0f
    private var pendingShown = false
    private var replyStartT = -1f

    override fun say(text: String, durationMs: Long) {
        if (hiddenTab || controller.state == MonkeyState.HIDDEN) return  // nobody to see the bubble
        if (pendingShown || thinkingBubble) return  // don't cover a question or the thinking cloud
        if (typing) return  // don't swap the line the user is replying to
        pages.clear()
        // v3.2 lines may carry a comic beat (" || "); the desktop shows it as a pause "…".
        val flat = Lines.flat(text)
        lastLine = flat
        showBubble(flat, durationMs / 1000f)
    }

    /** A line Settai said that isn't in the chat history yet (canned line / quip); context for a bubble reply. */
    private var lastLine: String? = null

    private fun showBubble(text: String, life: Float, buttons: List<String> = emptyList(), talk: Boolean = true, reply: Boolean = true) {
        @Suppress("NAME_SHADOWING") val text = text.replace(Regex("\\bphone\\b"), "laptop").replace(Regex("\\bPhone\\b"), "Laptop")
        val withReply = reply && replyField != null && buttons.isEmpty()
        bubble.setText(text, (210 * d).toInt(), buttons, withReply)
        val alreadyUp = bubbleOn && bubbleAge in POP..bubbleLife
        bubbleOn = true
        bubbleAge = if (alreadyUp) POP else 0f
        // Leave time to tap the reply field; longer while a bubble conversation is going.
        val extra = if (!withReply) 0f else if (convo) 8f else 3f
        bubbleLife = (if (alreadyUp) POP else 0f) + life + extra
        talkFor = if (talk) (if (alreadyUp) POP else 0f) + min(2.5f, 0.4f + text.length * 0.05f) else 0f
    }

    override fun hush() {
        if (pendingShown) return
        if (pages.isNotEmpty()) { nextPage(); return }
        if (bubbleOn && bubbleAge < bubbleLife) bubbleLife = bubbleAge
    }

    private fun nextPage() {
        val p = pages.removeFirstOrNull() ?: return
        showBubble(if (pages.isNotEmpty()) "$p …" else p, Speech.defaultDuration(p) / 1000f + 1f)
    }

    /** Split a reply into bubble-sized pages at sentence (or word) boundaries. */
    private fun paginate(text: String, max: Int = 130): List<String> {
        val out = ArrayList<String>()
        val sentences = text.trim().split(Regex("(?<=[.!?…])\\s+"))
        val cur = StringBuilder()
        fun flush() { if (cur.isNotBlank()) out.add(cur.toString().trim()); cur.setLength(0) }
        for (sn in sentences) {
            if (sn.length > max) {
                flush()
                for (w in sn.split(" ")) {
                    if (cur.length + w.length + 1 > max) flush()
                    cur.append(w).append(' ')
                }
                continue
            }
            if (cur.length + sn.length + 1 > max) flush()
            cur.append(sn).append(' ')
        }
        flush()
        return if (out.isEmpty()) listOf(text) else out
    }

    // ---------------- Chat events ----------------

    override fun onThinking() {
        if (hiddenTab) return
        pages.clear()
        thinkingBubble = true
        replyStartT = -1f
        controller.startThinking()
        showBubble("Hmm", 600f, talk = false, reply = false)
    }

    override fun onReply(text: String, done: Boolean) {
        if (hiddenTab) return
        if (text.isBlank() && !done) return
        if (replyStartT < 0f) replyStartT = t
        thinkingBubble = false
        lastLine = null
        if (!done) {
            controller.startTalking()
            if (!pendingShown) showBubble(paginate(text).last(), 600f, reply = false)
            return
        }
        val all = paginate(text)
        val quick = t - replyStartT < 1.5f  // arrived all at once: page from the start
        controller.endChat(3f)
        if (pendingShown) return
        pages.clear()
        if (all.size > 1 && quick) {
            pages.addAll(all)
            nextPage()
        } else {
            val last = all.last()
            showBubble(last, Speech.defaultDuration(last) / 1000f + 1.5f)
        }
        chat.pending?.let { onPending(it) }
    }

    fun onError(message: String) {
        thinkingBubble = false
        controller.endChat(2f)
    }

    override fun onPending(p: PendingAction?) {
        val action = p
        if (action == null) {
            if (pendingShown) { pendingShown = false; bubbleLife = bubbleAge }
            return
        }
        if (hiddenTab) unhide()
        thinkingBubble = false
        pages.clear()
        pendingShown = false
        showBubble(action.question, 25f, listOf("Yes", "No"))
        pendingShown = true
    }

    override fun onAnnounce(text: String) { pendingShown = false; pages.clear(); showBubble(text, 3.5f) }

    // ---------------- In-bubble reply ----------------

    /** Desktop: the reply field is drawn in the cloud and fed by key events. */
    private val replyField: Any? = Unit
    private val typed = StringBuilder()
    private var typing = false
    /** Bubble conversation in progress: keep replies up longer. */
    private var convo = false
    private var typeLift = 0f

    /** Double-click / "Chat": open a bubble chat with an empty reply field, ready to type. */
    fun openBubbleChat() {
        if (hiddenTab) unhide()
        if (thinkingBubble || pendingShown) return
        pages.clear()
        lastLine = null
        convo = true
        showBubble(listOf("What's up?", "Talk to me!", "Yes, boss?", "Settai is listening…").random(), 12f, talk = true)
        startTyping()
    }

    private fun startTyping() {
        if (typing) { host?.setWindowFocusable(true); return }
        typing = true
        typed.setLength(0)
        bubble.fieldText = ""
        host?.setWindowFocusable(true)
    }

    /** Drop focus so the rest of the desktop is usable again. */
    fun endTyping() {
        if (!typing) return
        typing = false
        bubble.fieldText = null
        host?.setWindowFocusable(false)
        if (bubbleOn && bubbleAge < bubbleLife) bubbleLife = max(bubbleLife, bubbleAge + 2.5f)
    }

    private fun sendReply() {
        val txt = typed.toString().trim()
        if (txt.isEmpty()) { endTyping(); return }
        if (chat.busy) return  // still answering; keep what they typed
        val said = lastLine
        typed.setLength(0)
        convo = true
        endTyping()
        chat.send(txt, said)
    }

    private fun positionReplyField() {
        if (!typing) return
        val caret = if ((t * 2f).toInt() % 2 == 0) "|" else " "
        bubble.fieldText = typed.toString() + caret
    }

    /** Keyboard while typing in the bubble. Returns true if used. */
    fun onKey(e: java.awt.event.KeyEvent): Boolean {
        if (!typing) return false
        when (e.id) {
            java.awt.event.KeyEvent.KEY_PRESSED -> when {
                e.keyCode == java.awt.event.KeyEvent.VK_ENTER -> sendReply()
                e.keyCode == java.awt.event.KeyEvent.VK_ESCAPE -> endTyping()
                e.keyCode == java.awt.event.KeyEvent.VK_BACK_SPACE -> if (typed.isNotEmpty()) {
                    if (e.isControlDown) { val i = typed.trimEnd().lastIndexOf(' '); typed.setLength(max(0, i + 1)) } else typed.setLength(typed.length - 1)
                }
                e.keyCode == java.awt.event.KeyEvent.VK_V && e.isControlDown -> runCatching {
                    val clip = java.awt.Toolkit.getDefaultToolkit().systemClipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as String
                    typed.append(clip.replace('\n', ' ').take(500))
                }
            }
            java.awt.event.KeyEvent.KEY_TYPED -> {
                val c = e.keyChar
                if (c != java.awt.event.KeyEvent.CHAR_UNDEFINED && !c.isISOControl() && !e.isControlDown && typed.length < 500) typed.append(c)
            }
        }
        bubbleLife = max(bubbleLife, bubbleAge + 8f)
        positionReplyField()
        present()
        return true
    }

    /** Click elsewhere on the desktop (window lost focus) while typing. */
    fun onOutsideOrBack(): Boolean {
        if (!typing) return false
        endTyping()
        return true
    }

    private var quipCheckIn = 0f

    /** Re-read quip settings now and then (they can change in Settings). */
    private fun refreshQuipSettings() {
        controller.configureQuips(if (cfg.quips) cfg.int("quipMinutes", 20) * 60f else 0f)
        controller.roam = cfg.roam
    }
    private var heat = 0f
    private var silent = false
    /** Latest laptop readings (from the system monitor thread). */
    var sys = SysState()
        set(v) {
            field = v
            // Hot CPU -> sweat (Ryzen Tctl idles 45-60°C, sustained load 85°C+); GNOME Do Not Disturb -> cloth over the mouth.
            heat = if (v.cpuTemp.isNaN()) 0f else ((v.cpuTemp - 78f) / 12f).coerceIn(0f, 1f)
            silent = v.dnd
        }

    private fun requestQuip(): Boolean {
        if (!cfg.quips || chat.busy || !runCatching { chat.brain().isReal }.getOrDefault(false)) return false
        val ctxText = "local time ${java.text.SimpleDateFormat("EEE HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}, " +
            DesktopChat.describe(sys) + "."
        chat.quip(ctxText) { q ->
            if (q != null && controller.state == MonkeyState.IDLE && !bubbleOn && !hiddenTab) say(q)
        }
        return true
    }

    override val isSpeaking: Boolean get() = bubbleOn && bubbleAge < talkFor

    // ---------------- Frame loop ----------------

    private var paused = false
    /** Wallpaper: no window, the host drives attach/detach. */
    private var live = false

    private val frameTimer = javax.swing.Timer(15) { doFrame(System.nanoTime()) }.apply { isCoalesce = true }

    /** Start listening and animating. */
    fun goLive() {
        if (live) return
        live = true
        chat.listener = this
        chat.bodyState = { bodyState() }
        controller.quipRequester = { requestQuip() }
        refreshQuipSettings()
        if (!paused) start()
    }

    fun goDead() {
        if (!live) return
        live = false
        ui.removeCallbacksAndMessages(null)
        stop()
    }

    /** Freeze the frame loop (screen locked) to save battery. */
    fun setPaused(p: Boolean) {
        paused = p
        if (p) stop() else if (live) start()
    }

    private fun start() {
        if (running) return
        running = true; lastNanos = 0L
        frameTimer.start()
    }

    private fun stop() {
        running = false
        frameTimer.stop()
    }

    /** Wallpaper: climbing spots were edited. */
    fun spotsChanged() {
        spots = host?.spots() ?: emptyList()
        if (surf.spot >= 0 && surf.spot >= spots.size) { surf = screenSurf(SK.FLOOR); climber.deactivate(); clampToSurf() }
        refreshSurf()
    }

    fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val dt = if (lastNanos == 0L) 0.016f else ((frameTimeNanos - lastNanos) / 1e9f).coerceIn(0f, 0.05f)
        lastNanos = frameTimeNanos
        t += dt

        controller.update(dt)
        stepPhysics(dt)
        stepHide(dt)
        stepEntrance(dt)
        stepHints(dt)
        if (sleepFrames > 0 && --sleepFrames == 0) { paused = true; stop(); return }
        if (bubbleOn) {
            bubbleAge += dt
            // Never auto-hide (or page away) while the user is typing in the bubble.
            if (typing && bubbleAge > bubbleLife - 0.3f) bubbleLife = bubbleAge + 0.3f
            if (bubbleAge > bubbleLife && pages.isNotEmpty()) nextPage()
            else if (bubbleAge > bubbleLife + FADE) { bubbleOn = false; pendingShown = false; convo = false }
        }
        if (thinkingBubble) {
            thinkDots += dt
            if (thinkDots > 0.35f) {
                thinkDots = 0f
                val n = ((t * 2.85f).toInt() % 3) + 1
                bubble.setText("Hmm" + ".".repeat(n), (210 * d).toInt())
            }
        }
        if (chatBtnUntil > 0f) chatBtnUntil -= dt
        if (hintUntil > 0f) hintUntil -= dt
        quipCheckIn -= dt
        if (quipCheckIn <= 0f) { quipCheckIn = 30f; refreshQuipSettings() }
        facingAnim += (controller.facing - facingAnim) * min(1f, dt * 12f)

        Animations.fill(target, controller.state, t, controller.stateTime, controller.anim)
        current.blendToward(target, 1f - exp(-dt * 14f))
        drawPose.set(current)
        drawPose.sweat = heat; drawPose.mouthCloth = if (silent) 1f else 0f
        // Blinking (not while eyes are squeezed shut with joy).
        blinkIn -= dt
        if (blinkIn <= 0f) { blinkT = 0f; blinkIn = 2f + Random.nextFloat() * 4f }
        if (blinkT >= 0f) {
            blinkT += dt
            val k = blinkT / 0.16f
            if (k >= 1f) blinkT = -1f else drawPose.eyeOpen *= abs(1f - 2f * k).coerceAtLeast(0.05f)
        }

        updateGeometry()
        positionReplyField()
        if (typing && !bubbleOn) endTyping()
        layoutRig()
        if (climber.active) climberStep(dt)

        // Calm idle without a bubble only needs ~30 fps; a hidden tab even less.
        oddFrame = !oddFrame
        val st = controller.state
        val calm = (st == MonkeyState.IDLE || st == MonkeyState.HIDDEN || st == MonkeyState.SIT) && !bubbleOn &&
            chatBtnUntil <= 0f && hintUntil <= 0f && !climber.active
        if (!calm || oddFrame) { present(); host?.onFrame() }
    }

    private fun stepPhysics(dt: Float) {
        if (host == null) return
        if (tweenT >= 0f) {
            tweenT += dt
            val k = (tweenT / tweenDur).coerceIn(0f, 1f)
            val e = k * k * (3f - 2f * k)
            mx = tFromX + (tToX - tFromX) * e
            my = tFromY + (tToY - tFromY) * e - tweenArc * sin(k * PI.toFloat())
            if (k >= 1f) {
                tweenT = -1f; mx = tToX; my = tToY
                if (!dragging && !falling) clampToSurf()
                val dn = tweenDone; tweenDone = null; dn?.invoke()
            }
        }
        if (falling) {
            vy += 2600f * d * dt
            my += vy * dt
            val g = ground()
            if (my >= g) {
                my = g; falling = false; vy = 0f
                surf = screenSurf(SK.FLOOR); clampToSurf()
                haptic()
                controller.onLanded()
                afterLand?.let { a -> afterLand = null; ui.postDelayed({ a() }, 480) }
            }
        }
        // On a ceiling the body turns to face the way it travels.
        if (surf.kind == SK.CEIL && climber.active && climber.face != faceOn(surf)) climber.attach(modeOf(surf), surf.line, faceOn(surf), surf.spanA, surf.spanB)
    }

    private var afterLand: (() -> Unit)? = null

    /** Profile climber: anchor follows the box; head turns to the user for reactions / talking. */
    private fun climberStep(dt: Float) {
        val s = surf
        if (!s.climb) { climber.deactivate(); return }
        val ax = if (s.wall) s.line else mx + boxW / 2f
        val ay = if (s.wall) my + boxH * wallHip else s.line
        val st = controller.state
        climber.lookAtUser = when (st) {
            MonkeyState.IDLE -> (t % 9f) > 6.8f            // looks back at the user now and then
            MonkeyState.WALK, MonkeyState.FALLING, MonkeyState.DRAGGED -> false
            MonkeyState.ENTRANCE -> controller.stateTime < Ent.PEEK
            else -> true
        }
        climber.update(dt, ax, ay, t, drawPose)
    }

    /** Pose the front rig in the box (standing, entrance rising out of an edge). */
    private fun layoutRig() {
        val groundY = oy + boxH - groundPad
        val cx = ox + boxW / 2f
        val f = renderFacing()
        rig.layout(drawPose, cx, groundY, rigScale, f)
        if (controller.state == MonkeyState.ENTRANCE && entSide == 0) {
            rig.headTop(tmpPt)
            val hTop = groundY - tmpPt[1]
            peekDy = hTop - 50f * rigScale
            val st = controller.stateTime
            if (st >= Ent.PEEK && st < Ent.PULL) {
                // Hands reach up and grip the edge; IK keeps them planted while the body pulls up.
                val edgeY = groundY + (if (surf.spot >= 0) 0f else groundPad) - 1f * d
                val k = ((st - Ent.PEEK) / ((Ent.GRIP - Ent.PEEK) * 0.6f)).coerceIn(0f, 1f)
                val reachY = edgeY + (1f - ease(k)) * 34f * rigScale - sinkY
                rig.layout(drawPose, cx, groundY + sinkY, rigScale, f)
                for (left in booleanArrayOf(true, false)) {
                    rig.handPoint(left, tmpA)
                    val side = if (tmpA[0] < cx) -1f else 1f
                    rig.solveArm(drawPose, left, cx + side * 40f * rigScale, reachY + sinkY)
                }
            }
        }
        rig.layout(drawPose, cx, groundY + sinkY, rigScale, f)
    }

    private fun ease(k: Float): Float { val c = k.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }

    private fun renderFacing(): Float =
        if (abs(facingAnim) < 0.06f) 0.06f * (if (facingAnim < 0) -1f else 1f) else facingAnim

    /** Hit test in view coordinates. */
    private fun hitTest(x: Float, y: Float): TouchZone {
        if (hiddenTab || controller.state == MonkeyState.HIDDEN) return TouchZone.NONE
        if (sinkY > boxH * 0.55f || abs(sinkX) > boxW * 0.5f) return TouchZone.NONE
        if (climber.active) return climber.hitTest(x + winX - sinkX, y + winY - sinkY)
        return rig.hitTest(x - sinkX, y)
    }

    // ---------------- Edge hide (overlay) ----------------

    private val tabW get() = 24 * d
    private val tabL get() = 66 * d

    private fun clampTab() {
        val h = host ?: return
        tabPos = if (hideSide == 2) tabPos.coerceIn(tabL / 2 + 4 * d, h.screenWidth() - tabL / 2 - 4 * d)
        else tabPos.coerceIn(topY() + tabL / 2 + 8 * d, h.screenHeight() - h.bottomInset() - tabL / 2 - 8 * d)
    }

    /** Dash at the edge and duck behind it. [side] -1 left, 1 right, 2 bottom. */
    fun hideToEdge(side: Int) {
        if (host == null || wp || hiddenTab || hideAnim >= 0f) return
        endTyping(); pages.clear(); bubbleOn = false; thinkingBubble = false
        hideSide = side
        climber.deactivate(); falling = false
        val fx = mx; val fy = my
        if (side == 2) { surf = screenSurf(SK.FLOOR); my = ground(); mx = mx.coerceIn(0f, sw() - boxW) }
        else { mx = if (side < 0) 0f else sw() - boxW; my = my.coerceIn(wallMinY(), ground()); surf = freeSurf(my) }
        slideFrom(fx, fy, 0.2f)
        controller.play(MonkeyState.DASH)
        controller.face(if (side == 2) (if (mx > sw() / 2) 1f else -1f) else side.toFloat())
        hideAnim = 0f
    }

    private fun stepHide(dt: Float) {
        if (hideAnim < 0f) return
        hideAnim += dt
        val k = ease((hideAnim - 0.14f) / 0.24f)
        if (hideSide == 2) sinkY = k * boxH else sinkX = hideSide * k * boxW * 0.75f
        if (hideAnim > 0.42f) {
            hideAnim = -1f
            hiddenTab = true
            tabPos = if (hideSide == 2) mx + boxW / 2f else my + boxH / 2f
            clampTab()
            saveHidden()
            controller.play(MonkeyState.HIDDEN)
            sinkX = 0f; sinkY = 0f
        }
    }

    private fun saveHidden() {
        prefs.edit().putBoolean("hidden", hiddenTab).putInt("hideSide", hideSide).putFloat("tabPos", tabPos).apply()
    }

    /** Tab tapped / dragged out: peek and climb back onto the screen. */
    fun unhide() {
        if (!hiddenTab || host == null) return
        hiddenTab = false
        saveHidden()
        if (hideSide == 2) {
            surf = screenSurf(SK.FLOOR)
            mx = tabPos - boxW / 2f; clampToSurf()
            beginEntrance(EntKind.UNHIDE, 0, Ent.HIDDEN)
        } else {
            val n = screenSurf(if (hideSide < 0) SK.WALL_L else SK.WALL_R)
            surf = n
            my = tabPos - boxH / 2f; clampToSurf()
            startClimberAtBox(n)
            beginEntrance(EntKind.UNHIDE, hideSide, Ent.HIDDEN)
        }
    }

    // ---------------- Entrances ----------------

    private fun beginEntrance(kind: EntKind, side: Int, startAt: Float = 0f) {
        entKind = kind; entSide = side; entRelocated = startAt > 0f
        if (kind == EntKind.UNHIDE) entLine = Lines.pick(Lines.PEEKABOO)
        controller.play(MonkeyState.ENTRANCE, 0f, startAt)
        if (side == 0) { sinkX = 0f; sinkY = if (startAt > 0f) boxH.toFloat() else 0f }
        else { sinkY = 0f; sinkX = side * sideHideX() }
        tweenT = -1f; falling = false
    }

    private fun sideHideX() = 112f * rigScale

    private fun stepEntrance(dt: Float) {
        if (entKind == EntKind.NONE) return
        val st = controller.stateTime
        if (controller.state == MonkeyState.HIDDEN && entKind == EntKind.DUCK) {
            hiddenFor -= dt
            if (hiddenFor <= 0f) beginEntrance(EntKind.UNHIDE, 0, Ent.HIDDEN)
            return
        }
        if (controller.state != MonkeyState.ENTRANCE) { endEntrance(); return }
        if (entKind == EntKind.WAKE && !entRelocated && st >= Ent.DUCK) { entRelocated = true; relocateForWake() }
        if (entSide == 0) {
            val full = boxH.toFloat()
            sinkY = when {
                st < Ent.DUCK -> full * ease(st / Ent.DUCK)
                st < Ent.HIDDEN -> full
                st < Ent.PEEK -> full + (peekDy - full) * ease((st - Ent.HIDDEN) / 0.35f)
                st < Ent.GRIP -> peekDy - 28f * rigScale * ease((st - Ent.PEEK) / (Ent.GRIP - Ent.PEEK))
                st < Ent.PULL -> (peekDy - 28f * rigScale) * (1f - ease((st - Ent.GRIP) / (Ent.PULL - Ent.GRIP)))
                st < Ent.HOP -> -sin((st - Ent.PULL) / (Ent.HOP - Ent.PULL) * PI.toFloat()) * 14f * d
                else -> 0f
            }
            sinkClipY = oy + boxH - groundPad + (if (surf.spot >= 0) 0f else groundPad)
            if (entKind == EntKind.DUCK && st >= Ent.HIDDEN) {
                controller.play(MonkeyState.HIDDEN); sinkY = full
                return
            }
            if (st >= Ent.HOP) {
                entLine?.let { l -> entLine = null; say(l, 4000L) }
                if (entKind == EntKind.UNHIDE) { endEntrance(); controller.idleNow(3f) }
            }
            if (st >= Ent.END) { endEntrance(); controller.idleNow(4f) }
        } else {
            val full = entSide * sideHideX()
            val peek = entSide * 30f * rigScale
            sinkX = when {
                st < Ent.HIDDEN -> full
                st < Ent.PEEK -> full + (peek - full) * ease((st - Ent.HIDDEN) / 0.4f)
                st < Ent.GRIP -> peek * (1f - 0.4f * ease((st - Ent.PEEK) / (Ent.GRIP - Ent.PEEK)))
                st < Ent.PULL -> peek * 0.6f * (1f - ease((st - Ent.GRIP) / (Ent.PULL - Ent.GRIP)))
                else -> 0f
            }
            if (st >= Ent.PULL) {
                endEntrance(); controller.idleNow(3f)
                entLine?.let { l -> entLine = null; say(l, 4000L) }
            }
        }
    }

    private fun endEntrance() {
        entKind = EntKind.NONE
        sinkX = 0f; sinkY = 0f; sinkClipY = Float.NaN
    }

    /** Wake: after ducking at the bottom, maybe come out of a side edge or from behind a widget instead. */
    private fun relocateForWake() {
        if (wp && spots.isNotEmpty() && Random.nextFloat() < 0.55f) {
            val i = Random.nextInt(spots.size)
            spotSurf(i, SK.FLOOR)?.let { s -> surf = s; mx = s.a + Random.nextFloat() * (s.b - s.a); clampToSurf() }
            return
        }
        if (Random.nextFloat() < 0.3f) {
            val side = if (Random.nextBoolean()) -1 else 1
            val n = screenSurf(if (side < 0) SK.WALL_L else SK.WALL_R)
            surf = n
            my = ground() - boxH * (0.4f + Random.nextFloat() * 0.8f); clampToSurf()
            startClimberAtBox(n)
            entSide = side
            sinkY = 0f; sinkX = side * sideHideX()
            controller.play(MonkeyState.ENTRANCE, 0f, Ent.HIDDEN)
            return
        }
        mx = (mx + (Random.nextFloat() - 0.5f) * sw() * 0.6f).coerceIn(0f, sw() - boxW)
        clampToSurf()
    }

    // ---------------- Sleep / wake ----------------

    private var sleepFrames = 0

    /** Screen turning off: settle at the bottom curled up asleep, then stop drawing to save battery. */
    fun goToSleep() {
        if (host == null) return
        endTyping()
        pages.clear(); thinkingBubble = false; pendingShown = false
        bubbleOn = false; chatBtnUntil = 0f; hintUntil = 0f
        tweenT = -1f; tweenDone = null; falling = false; dragging = false; tracking = false; afterLand = null
        ui.removeCallbacks(longPress)
        endEntrance(); hideAnim = -1f
        paused = false
        start()
        sleepFrames = 4   // draw a few frames, then stop the frame loop
        if (hiddenTab) { present(); return }  // stays hidden: just the tab
        climber.deactivate()
        surf = screenSurf(SK.FLOOR); clampToSurf()
        controller.sleep()
        Animations.fill(current, MonkeyState.SLEEP, t, 0f, controller.anim)
        present()
    }

    /** Screen on (maybe still locked): show Settai snoozing; wake now if there's no lock screen. */
    fun onScreenOn() {
        sleepFrames = 0
        setPaused(false)
        ui.postDelayed({ onUserPresent() }, 700)
    }

    /** Unlocked: duck below the edge, peek out, grip, pull up, then the crazy wake routine. */
    fun onUserPresent() {
        sleepFrames = 0
        setPaused(false)
        if (hiddenTab || !controller.isAsleep) return
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        entLine = Lines.wake(hour)
        beginEntrance(EntKind.WAKE, 0)
    }

    private fun freeSurf(y: Float) = Surf(SK.FLOOR, y, 0f, max(0f, sw() - boxW), y + boxH - groundPad, 0f, sw(), -2)

    // ---------------- Geometry ----------------

    private fun updateGeometry() {
        val showBubble = bubbleOn
        if (host == null) {
            // In-app preview: monkey at bottom-centre of this view, bubble above.
            ox = (width - boxW) / 2f
            oy = (height - boxH).toFloat()
            if (showBubble) {
                bubbleLeft = (ox + boxW / 2f - bubble.width / 2f).coerceIn(0f, max(0f, width - bubble.width))
                bubbleTop = max(0f, oy + overlap - bubble.trailSpace - bubble.height)
            }
            return
        }
        val sw = host.screenWidth(); val sh = host.screenHeight()
        if (hiddenTab && !wp) {
            // Only the little tab: a tiny window on the edge.
            clampTab()
            if (hideSide == 2) { winW = tabL.toInt(); winH = tabW.toInt(); winX = (tabPos - tabL / 2).toInt(); winY = sh - host.bottomInset() - winH }
            else { winW = tabW.toInt(); winH = tabL.toInt(); winY = (tabPos - tabL / 2).toInt(); winX = if (hideSide < 0) 0 else sw - winW }
            ox = 0f; oy = 0f
            tabRect.set(winX.toFloat(), winY.toFloat(), (winX + winW).toFloat(), (winY + winH).toFloat())
            placeViewport()
            tabRect.offset(-winX.toFloat(), -winY.toFloat())
            return
        }
        val boxY = my - typeLift
        if (!showBubble) {
            winX = mx.toInt(); winY = boxY.toInt(); winW = boxW; winH = boxH
            ox = 0f; oy = 0f
        } else {
            val bw = bubble.width
            winW = max(boxW.toFloat(), bw).toInt()
            winX = (mx + boxW / 2f - winW / 2f).toInt().coerceIn(0, max(0, sw - winW))
            val above = bubble.height + bubble.trailSpace - overlap
            val minTop = if (wp) topY() else 0f
            if (boxY - above >= minTop) {
                winH = (boxH + above).toInt()
                winY = (boxY - above).toInt()
                bubbleTop = 0f
            } else {
                val below = bubble.height + bubble.trailSpace
                winH = (boxH + below).toInt()
                winY = boxY.toInt()
                bubbleTop = boxH + bubble.trailSpace
            }
            oy = boxY - winY
            ox = mx - winX
            bubbleLeft = (ox + boxW / 2f - bw / 2f).coerceIn(0f, max(0f, winW - bw))
        }
        if (wp) {
            // Whole-screen view: same layout, in screen coordinates.
            ox += winX; oy += winY; bubbleLeft += winX; bubbleTop += winY
            winX = 0; winY = 0; winW = sw; winH = sh
            return
        }
        placeViewport()
    }

    /** Tab rectangle in window px (hidden at the edge). */
    private val tabRect = RectF()
    private var vpW = 0; private var vpH = 0

    /**
     * Desktop: the X window keeps one size (resizing it every time the bubble changes raced with the
     * window manager and left Settai drawn outside his window). It is a viewport that follows him,
     * clamped to the screen; everything is drawn offset into it and the X Shape makes the rest click-through.
     */
    private fun placeViewport() {
        val h = host ?: return
        val sw = h.screenWidth(); val sh = h.screenHeight()
        vpW = max(vpW, max((330 * d).toInt(), winW)).coerceAtMost(sw)
        vpH = max(vpH, max((boxH + 300 * d).toInt(), winH)).coerceAtMost(sh)
        var vx = winX + winW / 2 - vpW / 2
        var vy = winY + winH - vpH + (if (bubbleOn && bubbleTop > oy) 0 else 0)
        if (bubbleOn && bubbleTop > oy + boxH / 2f) vy = winY           // bubble below Settai: viewport hangs down
        vx = vx.coerceIn(min(winX, winX + winW - vpW), max(winX, winX + winW - vpW)).coerceIn(0, max(0, sw - vpW))
        vy = vy.coerceIn(min(winY, winY + winH - vpH), max(winY, winY + winH - vpH)).coerceIn(0, max(0, sh - vpH))
        val dx = winX - vx; val dy = winY - vy
        ox += dx; oy += dy; bubbleLeft += dx; bubbleTop += dy
        winX = vx; winY = vy; winW = vpW; winH = vpH
        h.placeWindow(vx, vy, vpW, vpH)
    }

    private fun clampPosition() {
        val h = host ?: return
        mx = mx.coerceIn(0f, max(0f, (h.screenWidth() - boxW).toFloat()))
        my = my.coerceIn(min(topY(), ground()), ground())
        if (falling || dragging || tweenT >= 0f) return
        clampToSurf()
    }

    /** Released after a drag: fling at an edge hides; near an edge grabs it; else fall (roaming) / stay put. */
    private fun settleAfterDrag(fvx: Float, fvy: Float): Boolean {
        val g = ground()
        val fx = mx; val fy = my
        val near = 40 * d
        val fast = 1400 * d
        if (!wp && hypot(fvx, fvy) > fast) {
            val side = when {
                abs(fvx) > abs(fvy) -> if (fvx < 0) -1 else 1
                fvy > 0 -> 2
                mx < near -> -1                       // fast swipe along the left edge
                mx > sw() - boxW - near -> 1
                else -> 0
            }
            if (side != 0) { hideToEdge(side); return false }
        }
        val n = when {
            my <= topY() + near -> screenSurf(SK.CEIL)
            my >= g - 64 * d -> screenSurf(SK.FLOOR)
            mx <= near * 0.6f -> screenSurf(SK.WALL_L)
            mx >= sw() - boxW - near * 0.6f -> screenSurf(SK.WALL_R)
            controller.roam -> { beginFall(); null }
            else -> freeSurf(my)
        } ?: return true
        surf = n
        clampToSurf()
        if (n.climb) startClimber(n) else climber.deactivate()
        if (abs(mx - fx) > 1f || abs(my - fy) > 1f) slideFrom(fx, fy, 0.18f)
        return false
    }

    /** Screen rotated / resized. */
    fun onScreenChanged() {
        if (wp) spots = host?.spots() ?: emptyList()
        refreshSurf(); clampPosition(); clampTab()
    }

    // ---------------- Drawing ----------------

    private val head = FloatArray(2)

    private var chatBtnUntil = 0f
    /** Pulsing 💬 discoverability hint ("Double-tap me to chat!"). */
    private var hintUntil = 0f
    private val chatBtnR = 17 * d
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val btnText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 17 * d }
    private val tabPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tabPath = Path()
    private fun chatBtnX() = ox + boxW - chatBtnR - 4 * d
    private fun chatBtnY() = oy + chatBtnR + 14 * d

    private fun haptic() {}

    // Frames are rendered into our own ARGB buffer and copied to the window in one Src blit.
    // Swing's normal repaint clears the translucent X11 window first, and compositors
    // sometimes show that empty frame (Settai blinking out for a frame).
    private var buf: java.awt.image.BufferedImage? = null
    private fun render(): java.awt.image.BufferedImage? {
        if (width <= 0 || height <= 0) return null
        var b = buf
        if (b == null || b.width != width || b.height != height) {
            b = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE); buf = b
        }
        val g = b.createGraphics()
        g.composite = AlphaComposite.Clear; g.fillRect(0, 0, width, height); g.composite = AlphaComposite.SrcOver
        drawAll(Canvas(g))
        g.dispose()
        return b
    }
    private fun blit(g0: Graphics, b: java.awt.image.BufferedImage) {
        val g = g0.create() as Graphics2D
        g.composite = AlphaComposite.Src; g.drawImage(b, 0, 0, null); g.dispose()
    }
    /** Draw a new frame straight to the window (falls back to repaint when not showing). */
    private fun present() {
        if (!Platform.current.activeRender) { repaint(); return }
        val b = render() ?: return
        val g = if (isShowing) graphics else null
        if (g == null) { repaint(); return }
        try { blit(g, b) } finally { g.dispose() }
        Toolkit.getDefaultToolkit().sync()
    }
    override fun paintComponent(g0: Graphics) { render()?.let { blit(g0, it) } }

    private fun drawAll(canvas: Canvas) {
        if (hiddenTab) { if (!wp) drawTab(canvas); return }
        drawMonkey(canvas)
        if (chatBtnUntil > 0f || hintUntil > 0f) drawChatBtn(canvas)
        if (bubbleOn) {
            if (climber.active) {
                climber.headTop(head); head[0] += sinkX - winX; head[1] += sinkY - winY
                if (bubbleTop > oy + boxH / 2f) head[1] = climber.headC.y - winY + 34f * rigScale
            } else {
                rig.headTop(head); head[0] += sinkX
                if (bubbleTop > oy + boxH / 2f) head[1] += (2 * MonkeyRig.HEAD_R + 30f) * rigScale  // bubble below: point at the chin
            }
            val k = (bubbleAge / POP).coerceIn(0f, 1f)
            val c1 = 1.70158f; val c3 = c1 + 1f
            val pop = 1f + c3 * (k - 1f).pow(3) + c1 * (k - 1f).pow(2)
            val alpha = if (bubbleAge > bubbleLife) 1f - (bubbleAge - bubbleLife) / FADE else min(1f, k * 3f)
            bubble.draw(canvas, bubbleLeft, bubbleTop, head[0], head[1] - 2 * d, max(0.01f, pop), alpha)
        }
    }

    /** Settai only (no bubble), e.g. for the wallpaper preview. */
    fun drawMonkey(canvas: Canvas) {
        if (hiddenTab || controller.state == MonkeyState.HIDDEN) return
        canvas.save()
        if (!sinkClipY.isNaN() && sinkY > 0f) canvas.clipRect(-10000f, -10000f, 100000f, sinkClipY)
        canvas.translate(sinkX, if (climber.active) sinkY else 0f)
        if (climber.active) {
            canvas.translate(-winX.toFloat(), -winY.toFloat())
            climber.draw(canvas, drawPose, t, renderer)
        } else renderer.draw(canvas, rig, drawPose, t)
        canvas.restore()
    }

    private fun drawChatBtn(canvas: Canvas) {
        val hint = hintUntil > 0f && chatBtnUntil <= 0f
        val left = if (hint) hintUntil else chatBtnUntil
        val a = (min(1f, left * 3f) * 255).toInt()
        val pop = if (hint) 1f + 0.12f * sin(t * 7f) else min(1f, (4f - chatBtnUntil) * 6f).coerceIn(0.2f, 1f)
        val r = chatBtnR * pop
        var bx = chatBtnX(); var by = chatBtnY()
        if (climber.active) { climber.headTop(tmpPt); bx = tmpPt[0] - winX + sinkX + (if (surf.kind == SK.WALL_R) -40f else 40f) * d; by = tmpPt[1] - winY + 6 * d }
        bx = bx.coerceIn(r + 2 * d, max(r + 2 * d, (if (wp) sw() else winW.toFloat()) - r - 2 * d))
        by = by.coerceIn(r + 2 * d, max(r + 2 * d, (if (wp) (host?.screenHeight() ?: height).toFloat() else winH.toFloat()) - r - 2 * d))
        lastBtnX = bx; lastBtnY = by
        if (hint) {  // soft pulsing ring
            btnPaint.style = Paint.Style.STROKE; btnPaint.strokeWidth = 3 * d; btnPaint.color = Color.rgb(0xE8, 0x8A, 0x2E)
            val ring = (t * 1.3f) % 1f
            btnPaint.alpha = ((1f - ring) * 0.8f * a).toInt()
            canvas.drawCircle(bx, by, r * (1f + ring * 0.8f), btnPaint)
        }
        btnPaint.style = Paint.Style.FILL; btnPaint.color = Color.rgb(0xE8, 0x8A, 0x2E); btnPaint.alpha = a
        canvas.drawCircle(bx, by, r, btnPaint)
        btnPaint.style = Paint.Style.STROKE; btnPaint.strokeWidth = 2 * d; btnPaint.color = Color.WHITE; btnPaint.alpha = a
        canvas.drawCircle(bx, by, r, btnPaint)
        btnText.alpha = a; btnText.textSize = 17 * d * pop
        // speech-balloon glyph (no colour emoji in Java2D)
        btnPaint.style = Paint.Style.FILL; btnPaint.color = Color.WHITE; btnPaint.alpha = a
        val u = r / 17f
        tmpR.set(bx - 9 * u, by - 7 * u, bx + 9 * u, by + 5 * u); canvas.drawRoundRect(tmpR, 5 * u, 5 * u, btnPaint)
        tabPath.reset(); tabPath.moveTo(bx - 4 * u, by + 4 * u); tabPath.lineTo(bx - 6 * u, by + 10 * u); tabPath.lineTo(bx + 2 * u, by + 4 * u); tabPath.close()
        canvas.drawPath(tabPath, btnPaint)
        btnPaint.color = Color.rgb(0xE8, 0x8A, 0x2E); btnPaint.alpha = a
        for (k in -1..1) canvas.drawCircle(bx + k * 4.5f * u, by - 1 * u, 1.4f * u, btnPaint)
    }
    private var lastBtnX = 0f; private var lastBtnY = 0f

    /** The edge tab: a translucent handle with an arrow, and a little ear + eyes peeking over it. */
    private fun drawTab(c: Canvas) {
        val w = tabRect.width(); val h = tabRect.height()
        c.save()
        c.translate(tabRect.left, tabRect.top)
        // Rotate so the tab always "points" into the screen along +x in local space.
        when (hideSide) {
            -1 -> Unit
            1 -> { c.translate(w, 0f); c.scale(-1f, 1f) }
            else -> { c.translate(0f, h); c.rotate(-90f) }
        }
        val tw = if (hideSide == 2) h else w   // thickness
        val tl = if (hideSide == 2) w else h   // length along the edge
        tabPaint.style = Paint.Style.FILL
        tabPaint.color = Color.argb(150, 0x3A, 0x26, 0x18)
        tmpR.set(-tw, 0f, tw * 0.95f, tl); c.drawRoundRect(tmpR, tw * 0.6f, tw * 0.6f, tabPaint)
        // peeking face: half a head with ear and two eyes
        val cy = tl * 0.36f
        tabPaint.color = Color.argb(235, 0x8B, 0x5A, 0x2B)
        c.drawCircle(0f, cy, tw * 0.62f, tabPaint)
        c.drawCircle(-tw * 0.05f, cy - tw * 0.7f, tw * 0.26f, tabPaint)          // ear
        tabPaint.color = Color.argb(235, 0xF0, 0xC8, 0x96)
        c.drawCircle(tw * 0.08f, cy + tw * 0.05f, tw * 0.42f, tabPaint)          // face
        tabPaint.color = Color.argb(235, 0xE8, 0x9E, 0x8A)
        c.drawCircle(-tw * 0.05f, cy - tw * 0.7f, tw * 0.13f, tabPaint)          // inner ear
        val blink = (t % 4f) > 3.85f
        tabPaint.color = Color.rgb(0x2B, 0x1A, 0x10)
        val look = 0.04f * tw * sin(t * 1.3f)
        for (dy in floatArrayOf(-0.17f, 0.2f)) {
            if (blink) c.drawRect(tw * 0.2f, cy + dy * tw - 1f * d, tw * 0.34f, cy + dy * tw + 1f * d, tabPaint)
            else c.drawCircle(tw * 0.27f + look, cy + dy * tw, tw * 0.085f, tabPaint)
        }
        // arrow chevron pointing into the screen
        tabPaint.style = Paint.Style.STROKE; tabPaint.strokeWidth = 2.6f * d; tabPaint.strokeCap = Paint.Cap.ROUND
        tabPaint.color = Color.argb(220, 255, 255, 255)
        val ay = tl * 0.74f
        tabPath.reset(); tabPath.moveTo(tw * 0.28f, ay - tw * 0.3f); tabPath.lineTo(tw * 0.6f, ay); tabPath.lineTo(tw * 0.28f, ay + tw * 0.3f)
        c.drawPath(tabPath, tabPaint)
        c.restore()
    }

    // ---------------- Touch ----------------

    private val slop = (5 * d).toInt()
    private val doubleTapTimeout = 360L
    private var downRawX = 0f; private var downRawY = 0f
    private var downMx = 0f; private var downMy = 0f
    private var downZone = TouchZone.NONE
    private var downSide = 1
    private var tracking = false
    private var lastMoveRawX = 0f; private var lastMoveRawY = 0f; private var lastMoveTime = 0L
    private var vx = 0f; private var vyDrag = 0f
    private var pendingTap: Runnable? = null
    private var lastTapUp = 0L
    private var lastTapX = 0f; private var lastTapY = 0f
    private var tabTracking = false
    private var tabMoved = false
    private var downTabPos = 0f

    /** Long-press without moving: show the little chat button beside the monkey. */
    private val longPress = Runnable {
        if (tracking && !dragging) {
            chatBtnUntil = 4f; hintUntil = 0f
            haptic()
            longPressed = true
        }
    }
    private var longPressed = false

    private fun btnHit(x: Float, y: Float) = (chatBtnUntil > 0f || hintUntil > 0f) && hypot(x - lastBtnX, y - lastBtnY) <= chatBtnR + 10 * d

    fun onTouchEvent(e: MotionEvent): Boolean {
        if (hiddenTab && !wp) return onTabTouch(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> { onOutsideOrBack(); return true }
            MotionEvent.ACTION_DOWN -> {
                if (bubbleOn && e.x >= bubbleLeft && e.x <= bubbleLeft + bubble.width &&
                    e.y >= bubbleTop && e.y <= bubbleTop + bubble.height
                ) {
                    tracking = false
                    val lx = e.x - bubbleLeft; val ly = e.y - bubbleTop
                    when (if (bubble.hasReply && !thinkingBubble) bubble.replyAt(lx, ly) else -1) {
                        0 -> { startTyping(); return true }
                        1 -> { if (typing) sendReply() else startTyping(); return true }
                        2 -> { endTyping(); host?.openChat(); return true }
                    }
                    if (typing) return true
                    val b = if (pendingShown) bubble.buttonAt(lx, ly) else -1
                    if (b >= 0) { pendingShown = false; chat.confirmPending(b == 0) }
                    else if (wp && !pendingShown) host?.openChat()   // wallpaper: tap the bubble to chat
                    else hush()
                    return true
                }
                if (btnHit(e.x, e.y)) {
                    tracking = false; chatBtnUntil = 0f
                    if (hintUntil > 0f) { hintUntil = 0f; countChatOpen() }
                    if (host != null) openBubbleChat() else onChatRequest?.invoke()
                    return true
                }
                downZone = hitTest(e.x, e.y)
                if (downZone == TouchZone.NONE) { tracking = false; if (typing) onOutsideOrBack(); return wp.not() }
                tracking = true; dragging = false; longPressed = false
                downRawX = e.rawX; downRawY = e.rawY
                downMx = mx; downMy = my
                downSide = if (e.x < ox + boxW / 2f) -1 else 1
                lastMoveRawX = e.rawX; lastMoveRawY = e.rawY; lastMoveTime = e.eventTime; vx = 0f; vyDrag = 0f
                ui.postDelayed(longPress, 420L)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return true
                val dx = e.rawX - downRawX; val dy = e.rawY - downRawY
                if (!dragging && hypot(dx, dy) > slop && host != null && !wp) beginDrag()
                if (dragging) {
                    mx = downMx + dx; my = downMy + dy
                    clampPosition()
                    val dtMs = (e.eventTime - lastMoveTime).coerceAtLeast(1L)
                    vx = vx * 0.5f + 0.5f * ((e.rawX - lastMoveRawX) * 1000f / dtMs)
                    vyDrag = vyDrag * 0.5f + 0.5f * ((e.rawY - lastMoveRawY) * 1000f / dtMs)
                    lastMoveRawX = e.rawX; lastMoveRawY = e.rawY; lastMoveTime = e.eventTime
                    controller.onDragMove(vx)
                }
            }
            MotionEvent.ACTION_UP -> {
                ui.removeCallbacks(longPress)
                if (!tracking) return true
                tracking = false
                if (dragging) {
                    dragging = false
                    // A stale velocity (finger stopped before lifting) isn't a fling.
                    val fresh = e.eventTime - lastMoveTime < 80L
                    val fall = settleAfterDrag(if (fresh) vx else 0f, if (fresh) vyDrag else 0f)
                    if (hideAnim < 0f) controller.onDragEnd(fall)
                    return true
                }
                if (longPressed) return true
                handleTap(e)
            }
            MotionEvent.ACTION_CANCEL -> {
                ui.removeCallbacks(longPress)
                if (dragging) { dragging = false; controller.onDragEnd(settleAfterDrag(0f, 0f)) }
                tracking = false; dragging = false
            }
        }
        return true
    }

    /** Hidden: tap the tab to bring Settai back, drag along the edge to move it, pull it out to unhide. */
    private fun onTabTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tabTracking = true; tabMoved = false
                downRawX = e.rawX; downRawY = e.rawY; downTabPos = tabPos
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tabTracking) return true
                val along = if (hideSide == 2) e.rawX - downRawX else e.rawY - downRawY
                val out = when (hideSide) { -1 -> e.rawX - downRawX; 1 -> downRawX - e.rawX; else -> downRawY - e.rawY }
                if (out > 46 * d) { tabTracking = false; unhide(); return true }
                if (abs(along) > slop) tabMoved = true
                if (tabMoved) { tabPos = downTabPos + along; clampTab(); updateGeometry() }
            }
            MotionEvent.ACTION_UP -> {
                if (!tabTracking) return true
                tabTracking = false
                if (tabMoved) saveHidden() else unhide()
            }
            MotionEvent.ACTION_CANCEL -> { tabTracking = false; if (tabMoved) saveHidden() }
        }
        return true
    }

    private fun beginDrag() {
        ui.removeCallbacks(longPress)
        pendingTap?.let { ui.removeCallbacks(it) }; pendingTap = null
        dragging = true
        falling = false; tweenT = -1f; tweenDone = null; afterLand = null
        if (entKind != EntKind.NONE) endEntrance()
        climber.deactivate()
        chatBtnUntil = 0f
        if (!longPressed) haptic()
        controller.onDragStart()
    }

    private fun handleTap(e: MotionEvent) {
        val now = System.currentTimeMillis()
        val pending = pendingTap
        if (pending != null && now - lastTapUp < doubleTapTimeout &&
            hypot(e.rawX - lastTapX, e.rawY - lastTapY) < 48 * d
        ) {
            ui.removeCallbacks(pending); pendingTap = null
            controller.onDoubleTap()
            return
        }
        if (System.getenv("SETTAI_DEBUG") != null) System.err.println("TAP zone=$downZone pending=${pending != null} dt=${now - lastTapUp}")
        val zone = downZone; val side = downSide
        val r = Runnable { pendingTap = null; controller.onTap(zone, side) }
        pendingTap = r
        lastTapUp = now; lastTapX = e.rawX; lastTapY = e.rawY
        ui.postDelayed(r, doubleTapTimeout)
    }

    // ---------------- Double-tap to chat + discoverability ----------------

    private fun onDoubleTapChat() {
        if (controller.state == MonkeyState.ENTRANCE || controller.state == MonkeyState.HIDDEN) return
        countChatOpen()
        hintUntil = 0f
        haptic()
        if (wp) { if (host != null) host.openChat() else onChatRequest?.invoke() }
        else openBubbleChat()
    }

    private fun countChatOpen() {
        prefs.edit().putInt("dtChats", prefs.getInt("dtChats", 0) + 1).apply()
    }

    private var hintCheckIn = 4f

    /** First appearance after install/update: "Double-tap me to chat!"; then gentle reminders until it's been used ~3 times. */
    private fun stepHints(dt: Float) {
        if (host == null) return
        hintCheckIn -= dt
        if (hintCheckIn > 0f) return
        hintCheckIn = 15f
        if (controller.state != MonkeyState.IDLE || bubbleOn || hiddenTab || chat.busy) return
        val ver = 30L
        val now = System.currentTimeMillis()
        if (prefs.getLong("hintVer", -1L) != ver) {
            prefs.edit().putLong("hintVer", ver).putLong("hintAt", now).apply()
            showHint(Lines.DT_FIRST.first())
            return
        }
        if (prefs.getInt("dtChats", 0) >= 3) return
        if (now - prefs.getLong("hintAt", 0L) < 20 * 60_000L) return
        if (Random.nextFloat() > 0.35f) return
        prefs.edit().putLong("hintAt", now).apply()
        showHint(Lines.pick(Lines.DT_HINT))
    }

    private fun showHint(line: String) {
        say(line, 5000L)
        hintUntil = 6f
        controller.play(MonkeyState.WAVE, 1.8f)
    }

    // ---------------- Body actions (AI tags / do_action / user's request) ----------------

    override fun onAction(action: BodyAction) {
        if (host == null) {  // in-app preview: just the simple ones
            when (action) {
                BodyAction.DANCE -> controller.danceNow(false)
                BodyAction.SPIN -> controller.spinDance()
                else -> action.gag?.let { controller.gag(it) }
            }
            return
        }
        if (controller.isAsleep && action != BodyAction.SLEEP) { controller.wakeUp(Lines.pick(Lines.POKED_AWAKE)); return }
        if (hiddenTab) { if (action != BodyAction.HIDE && action != BodyAction.SLEEP) unhide(); return }
        if (controller.state == MonkeyState.ENTRANCE || dragging) return
        val onFloor = surf.kind == SK.FLOOR && !falling
        fun onGround(f: () -> Unit) {
            if (onFloor) f() else { afterLand = f; controller.drop() }
        }
        when (action) {
            BodyAction.DANCE -> onGround { controller.danceNow(false) }
            BodyAction.SPIN -> onGround { controller.spinDance() }
            BodyAction.JUMP -> if (onFloor) hopInPlace() else controller.drop()
            BodyAction.WAVE -> controller.play(MonkeyState.WAVE, 2.2f)
            BodyAction.GIGGLE -> controller.play(MonkeyState.GIGGLE, 1.8f)
            BodyAction.HAPPY -> controller.play(MonkeyState.HAPPY_PAT, 2f)
            BodyAction.ANGRY -> controller.play(MonkeyState.ANGRY, 1.6f)
            BodyAction.SAD -> controller.play(MonkeyState.SAD, 3f)
            BodyAction.THINK -> controller.play(MonkeyState.THINKING, 2.4f)
            BodyAction.SLEEP -> onGround {
                controller.sleep()
                ui.postDelayed({ if (controller.isAsleep && !paused) controller.wakeUp(Lines.pick(Lines.POKED_AWAKE)) }, 12_000)
            }
            BodyAction.CLIMB -> climbSomething()
            BodyAction.HIDE -> hideSomewhere()
            else -> action.gag?.let { g -> onGround { controller.gag(g) } }
        }
    }

    private fun hopInPlace() {
        controller.play(MonkeyState.JUMP, 0.75f)
        val fx = mx; val fy = my
        slideFrom(fx, fy, 0.6f, 60 * d)
    }

    private fun climbSomething() {
        if (wp && spots.isNotEmpty() && Random.nextFloat() < 0.7f) { goToSpot(Random.nextInt(spots.size), 2); return }
        if (surf.climb) { controller.walkTo(Random.nextFloat() * (body.maxCenterX() - body.minCenterX()) + body.minCenterX(), 1.2f); return }
        climbScreenWall()
    }

    private fun hideSomewhere() {
        if (!wp) {
            val c = mx + boxW / 2f
            hideToEdge(if (c < sw() / 2f) -1 else 1)
            say(Lines.pick(Lines.HIDE), 2500L)
            return
        }
        if (spots.isNotEmpty()) { goToSpot(Random.nextInt(spots.size), 0); return }
        if (surf.kind == SK.FLOOR && surf.spot == -1) duckBehind() else afterLandThen { duckBehind() }
    }

    private fun afterLandThen(f: () -> Unit) { afterLand = f; controller.drop() }

    /** Duck below the current floor's edge, stay hidden a while, then peek out and climb back up. */
    private fun duckBehind(hideSeconds: Float = 3f + Random.nextFloat() * 5f) {
        hiddenFor = hideSeconds
        beginEntrance(EntKind.DUCK, 0)
    }

    // ---------------- Roaming adventures ----------------

    /** Called by the controller when it's time to decide what to do next. Returns true if handled. */
    private fun roam(r: Float, roamOn: Boolean): Boolean {
        if (hiddenTab || host == null) return true
        val s = surf
        if (!roamOn) {
            if (s.kind == SK.FLOOR) return false
            if (r < 0.5f) controller.walkTo(randAlong(), 1f) else controller.idleNow(3f)
            return true
        }
        if (wp && s.spot >= 0) return roamSpot(r, s)
        return when (s.kind) {
            SK.FLOOR -> when {
                wp && spots.isNotEmpty() && r < 0.22f -> { goToSpot(Random.nextInt(spots.size), Random.nextInt(4)); true }
                r < (if (wp) 0.3f else 0.12f) -> { climbScreenWall(); true }
                else -> false
            }
            SK.WALL_L, SK.WALL_R -> {
                when {
                    r < 0.3f -> controller.walkTo(body.minCenterX(), 1f) { goSurf(screenSurf(SK.CEIL)); controller.idleNow(1.5f) }
                    r < 0.55f -> controller.walkTo(body.maxCenterX(), 1f) { goSurf(screenSurf(SK.FLOOR), 0.3f); controller.play(MonkeyState.LANDING, 0.45f) }
                    r < 0.68f -> controller.drop()
                    r < 0.9f -> controller.walkTo(randAlong(), 1f)
                    else -> controller.idleNow(3f)
                }
                true
            }
            SK.CEIL -> {
                when {
                    r < 0.3f -> {
                        val left = Random.nextBoolean()
                        controller.walkTo(if (left) body.minCenterX() else body.maxCenterX(), 1f) {
                            goSurf(screenSurf(if (left) SK.WALL_L else SK.WALL_R).also { n -> my = n.a }); controller.idleNow(1.5f)
                        }
                    }
                    r < 0.48f -> controller.drop()
                    r < 0.85f -> controller.walkTo(randAlong(), 1f)
                    else -> { controller.idleNow(3f); if (Random.nextFloat() < 0.5f) say(Lines.pick(Lines.HANG)) }
                }
                true
            }
        }
    }

    private fun randAlong() = body.minCenterX() + Random.nextFloat() * (body.maxCenterX() - body.minCenterX())

    private fun climbScreenWall() {
        if (surf.kind != SK.FLOOR) { controller.drop(); return }
        val left = mx + boxW / 2f < sw() / 2f
        controller.walkTo(if (left) body.minCenterX() else body.maxCenterX(), 1.1f) {
            goSurf(screenSurf(if (left) SK.WALL_L else SK.WALL_R), 0.35f)
            controller.walkTo(randAlong(), 1f)
            if (Random.nextFloat() < 0.5f) say(Lines.pick(Lines.CLIMB))
        }
    }

    /** Wallpaper: go play on spot [i]. [what] 0 = hide behind & peek, 1 = sit on top, 2 = climb its side, 3 = hang underneath. */
    private fun goToSpot(i: Int, what: Int) {
        val r = spots.getOrNull(i) ?: return
        if (surf.kind != SK.FLOOR || surf.spot != -1) { afterLandThen { goToSpot(i, what) }; return }
        val leftSide = mx + boxW / 2f < r.centerX()
        val dest: Surf = when (what) {
            2 -> spotSurf(i, if (leftSide) SK.WALL_R else SK.WALL_L)
            3 -> spotSurf(i, SK.CEIL)
            else -> spotSurf(i, SK.FLOOR)
        } ?: return
        // Walk to just beside / under the spot, then leap.
        val standX = when (what) {
            2 -> if (leftSide) r.left - boxW * 0.6f else r.right + boxW * 0.6f
            else -> if (leftSide) r.left - boxW * 0.2f else r.right + boxW * 0.2f
        }
        controller.walkTo(standX.coerceIn(body.minCenterX(), body.maxCenterX()), 1.2f) { leapTo(dest, what) }
    }

    private fun leapTo(dest: Surf, what: Int) {
        val fx = mx; val fy = my
        surf = dest
        when (what) { 0, 1 -> mx = (mx).coerceIn(dest.a, dest.b) }
        clampToSurf()
        val dist = hypot(mx - fx, my - fy)
        val tx = mx; val ty = my
        mx = fx; my = fy
        controller.play(MonkeyState.JUMP, 0f)
        controller.face(if (tx >= fx) 1f else -1f)
        surf = freeSurf(fy)  // in the air
        mx = tx; my = ty
        slideFrom(fx, fy, (0.45f + dist / (1400f * d)).coerceAtMost(1.1f), 40 * d + dist * 0.15f) {
            surf = dest; clampToSurf()
            haptic()
            if (dest.climb) startClimber(dest) else climber.deactivate()
            when (what) {
                0 -> duckBehind()
                1 -> { controller.play(MonkeyState.SIT, 7f + Random.nextFloat() * 8f); if (Random.nextFloat() < 0.5f) say(Lines.pick(Lines.SIT)) }
                2 -> { controller.idleNow(0.6f); controller.walkTo(body.minCenterX(), 0.9f) }
                else -> { controller.idleNow(2f); if (Random.nextFloat() < 0.5f) say(Lines.pick(Lines.HANG)) }
            }
        }
    }

    private fun roamSpot(r: Float, s: Surf): Boolean {
        val i = s.spot
        when (s.kind) {
            SK.FLOOR -> when {
                r < 0.25f -> { controller.play(MonkeyState.SIT, 6f + Random.nextFloat() * 8f) }
                r < 0.42f -> controller.walkTo(randAlong(), 0.9f)
                r < 0.58f -> duckBehind()
                r < 0.72f -> controller.drop()
                r < 0.88f -> {
                    // climb down a side: walk to an end, swing round the corner onto the side
                    val left = Random.nextBoolean()
                    controller.walkTo(if (left) body.minCenterX() else body.maxCenterX(), 1f) {
                        spotSurf(i, if (left) SK.WALL_R else SK.WALL_L)?.let { n -> goSurf(n.also { my = n.a }, 0.4f) }
                        controller.walkTo(body.maxCenterX(), 0.9f)
                    }
                }
                else -> controller.idleNow(3f)
            }
            SK.WALL_L, SK.WALL_R -> when {
                r < 0.35f -> controller.walkTo(body.minCenterX(), 1f) {
                    // pull up over the top
                    spotSurf(i, SK.FLOOR)?.let { n ->
                        mx = if (s.kind == SK.WALL_R) n.a else n.b
                        goSurf(n, 0.35f, 10 * d)
                    }
                    controller.play(MonkeyState.LANDING, 0.45f)
                }
                r < 0.55f -> controller.walkTo(body.maxCenterX(), 1f) {
                    spotSurf(i, SK.CEIL)?.let { n -> mx = if (s.kind == SK.WALL_R) n.a else n.b; goSurf(n, 0.4f) }
                    controller.idleNow(1.5f)
                }
                r < 0.75f -> controller.drop()
                else -> controller.walkTo(randAlong(), 0.9f)
            }
            SK.CEIL -> when {
                r < 0.45f -> controller.walkTo(randAlong(), 0.9f)
                r < 0.75f -> controller.drop()
                else -> controller.idleNow(3f)
            }
        }
        return true
    }

    // ---------------- Body state for the AI ----------------

    private fun bodyState(): String {
        val where = when {
            hiddenTab -> "hidden behind the ${sideName(hideSide)} edge of the screen (only a little tab shows)"
            controller.state == MonkeyState.HIDDEN -> if (surf.spot >= 0) "hiding behind a widget" else "hiding below the bottom edge"
            controller.isAsleep -> "curled up asleep at the bottom of the screen"
            surf.spot >= 0 -> when (surf.kind) {
                SK.FLOOR -> if (controller.state == MonkeyState.SIT) "sitting on top of a widget" else "standing on top of a widget"
                SK.CEIL -> "hanging under a widget"
                else -> "climbing the side of a widget"
            }
            surf.spot == -2 -> "standing in the middle of the screen"
            surf.kind == SK.FLOOR -> "standing on the bottom edge of the screen"
            surf.kind == SK.CEIL -> "hanging from the top edge of the screen like a branch"
            surf.kind == SK.WALL_L -> "climbing the left edge of the screen"
            else -> "climbing the right edge of the screen"
        }
        val mood = when (controller.state) {
            MonkeyState.ANGRY -> "grumpy"; MonkeyState.DANCE -> "dancing"; MonkeyState.SAD -> "sad"
            MonkeyState.GIGGLE, MonkeyState.HAPPY_PAT, MonkeyState.PLAYFUL -> "happy"
            MonkeyState.SLEEP -> "sleepy"; else -> "cheerful"
        }
        val sy = sys
        return buildString {
            append("you live on your human's ").append(Platform.current.home).append(", floating on top of their windows")
            append("; position: ").append(where)
            append("; mood: ").append(mood)
            append("; ").append(if (controller.isAsleep) "asleep" else "awake")
            if (sy.battery >= 0) append("; laptop battery ").append(sy.battery).append('%').append(if (sy.charging) " (charging)" else "")
            if (!sy.cpuTemp.isNaN() && sy.cpuTemp >= 80f) append("; the CPU is hot (").append(sy.cpuTemp.toInt()).append("°C), you're sweating")
            if (sy.dnd) append("; Do Not Disturb is on (cloth over your mouth, keep it short)")
        }
    }

    private fun sideName(s: Int) = when (s) { -1 -> "left"; 1 -> "right"; else -> "bottom" }

    // ---------------- Desktop extras ----------------

    val isHiddenAtEdge get() = hiddenTab
    fun debugZone(x: Float, y: Float) = hitTest(x, y)
    val isAsleep get() = controller.isAsleep
    val isTyping get() = typing
    /** A system remark (battery, RAM...) only when Settai is free to talk. */
    fun remark(text: String, wave: Boolean = false): Boolean {
        if (hiddenTab || bubbleOn || chat.busy || controller.isAsleep || controller.state == MonkeyState.ENTRANCE) return false
        say(text, Speech.defaultDuration(text) + 1500L)
        if (wave) controller.play(MonkeyState.WAVE, 1.6f)
        return true
    }
    fun act(a: BodyAction) = onAction(a)
    fun hideNow() = if (hiddenTab) unhide() else hideToEdge(if (mx + boxW / 2f < sw() / 2f) -1 else 1)

    /** Parts of the window that take clicks (everything else is click-through), window px. */
    fun hitRegions(): List<java.awt.Rectangle> {
        if (hiddenTab) return listOf(java.awt.Rectangle(tabRect.left.toInt(), tabRect.top.toInt(), tabRect.width().toInt(), tabRect.height().toInt()))
        val out = ArrayList<java.awt.Rectangle>()
        val pad = (6 * d).toInt()
        if (controller.state != MonkeyState.HIDDEN) {
            if (climber.active) {
                climber.headTop(tmpPt)
                val cx = (tmpPt[0] - winX + sinkX).toInt(); val cy = (tmpPt[1] - winY + sinkY).toInt()
                val r = (95f * rigScale).toInt()
                out.add(java.awt.Rectangle(cx - r, cy - pad, 2 * r, (150f * rigScale).toInt() + 2 * pad))
            }
            out.add(java.awt.Rectangle((ox + sinkX).toInt() - pad, (oy + sinkY).toInt() - pad, boxW + 2 * pad, boxH + 2 * pad))
        }
        if (bubbleOn) out.add(java.awt.Rectangle(bubbleLeft.toInt() - pad, bubbleTop.toInt() - pad, bubble.width.toInt() + 2 * pad, (bubble.height + bubble.trailSpace).toInt() + 2 * pad))
        if (chatBtnUntil > 0f || hintUntil > 0f) { val r = (chatBtnR * 1.6f).toInt(); out.add(java.awt.Rectangle(lastBtnX.toInt() - r, lastBtnY.toInt() - r, 2 * r, 2 * r)) }
        return out
    }

    // ---------------- Preview / test hooks ----------------

    private var debugNanos = 1_000_000_000L

    /** Settai's box in screen px. */
    fun debugBox(): RectF = RectF(mx, my, mx + boxW, my + boxH)

    /** Put Settai on a surface: kind FLOOR/CEIL/WALL_L/WALL_R, spot -1 = screen edge. */
    fun debugSurf(kind: String, spot: Int = -1, frac: Float = 0.5f) {
        val k = SK.valueOf(kind)
        val n = (if (spot >= 0) spotSurf(spot, k) else screenSurf(k)) ?: return
        surf = n
        if (n.wall) my = n.a + (n.b - n.a) * frac else mx = n.a + (n.b - n.a) * frac
        clampToSurf(); tweenT = -1f
        controller.idleNow(999f)
        if (n.climb) startClimberAtBox(n) else climber.deactivate()
    }

    fun debugWalk(toFrac: Float, speed: Float = 1f) {
        controller.walkTo(body.minCenterX() + (body.maxCenterX() - body.minCenterX()) * toFrac, speed) { controller.idleNow(999f) }
    }

    fun debugCorner(kind: String) { goSurf(screenSurf(SK.valueOf(kind))); controller.idleNow(999f) }

    /** Entrance frame: side 0 = rising from the floor / spot top, -1/1 = out of a side edge. */
    fun debugEntrance(side: Int, st: Float) {
        if (side != 0) {
            val n = screenSurf(if (side < 0) SK.WALL_L else SK.WALL_R); surf = n; my = n.a + (n.b - n.a) * 0.7f; clampToSurf()
            startClimberAtBox(n)
        }
        val from = max(0f, st - 0.6f)
        beginEntrance(EntKind.WAKE, side, from)
        entRelocated = true
        debugFrames(((st - from) * 60f).toInt().coerceAtLeast(1))
    }

    fun debugHide(side: Int) = hideToEdge(side)
    fun debugPlay(s: MonkeyState, dur: Float = 0f) = controller.play(s, dur)
    fun debugSay(text: String) = showBubble(text, 30f)

    fun debugFrames(n: Int, dt: Float = 1f / 60f) {
        if (!live) goLive()
        running = true
        repeat(n) { debugNanos += (dt * 1e9f).toLong(); doFrame(debugNanos) }
    }

    companion object {
        private const val POP = 0.32f
        private const val FADE = 0.25f
    }
}
