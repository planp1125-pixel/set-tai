package com.combo.monkeycompanion.overlay

import com.combo.monkeycompanion.ai.BodySense as BSN

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import android.content.BroadcastReceiver
import android.app.NotificationManager
import android.media.AudioManager
import android.util.TypedValue
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import com.combo.monkeycompanion.ai.BodyAction
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.brain.Edge
import com.combo.monkeycompanion.brain.Gestures
import com.combo.monkeycompanion.brain.LongPressMenu
import com.combo.monkeycompanion.brain.PhoneMoments
import com.combo.monkeycompanion.brain.Landings
import com.combo.monkeycompanion.brain.LandingFlight
import com.combo.monkeycompanion.rig.LandingFx
import com.combo.monkeycompanion.rig.LandingPoses
import com.combo.monkeycompanion.rig.MomentFx
import com.combo.monkeycompanion.rig.MomentPoses
import com.combo.monkeycompanion.brain.MonkeyBody
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.brain.MoodController
import com.combo.monkeycompanion.brain.TouchZone
import com.combo.monkeycompanion.brain.PowerFlight
import com.combo.monkeycompanion.brain.PowerKit
import com.combo.monkeycompanion.brain.PowerPhase
import com.combo.monkeycompanion.brain.Superpower
import com.combo.monkeycompanion.brain.Superpowers
import com.combo.monkeycompanion.rig.ScreenFx
import com.combo.monkeycompanion.rig.Particles
import com.combo.monkeycompanion.rig.PowerEmitter
import com.combo.monkeycompanion.powers.PendingAction
import com.combo.monkeycompanion.rig.Animations
import com.combo.monkeycompanion.rig.Animations.Ent
import com.combo.monkeycompanion.rig.Climber
import com.combo.monkeycompanion.rig.MonkeyRenderer
import com.combo.monkeycompanion.engine.impact.ImpactPlay
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import com.combo.monkeycompanion.speech.CloudBubble
import com.combo.monkeycompanion.speech.Lines
import com.combo.monkeycompanion.speech.Speech
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import com.combo.monkeycompanion.engine.CharacterProfiles
import com.combo.monkeycompanion.engine.PhysicsBody
import com.combo.monkeycompanion.engine.StepEvent
import com.combo.monkeycompanion.engine.Surface
import com.combo.monkeycompanion.engine.SurfaceKind

/** v10 (M1): the surface types moved to shared engine/; the short names stay for this file. */
private typealias SK = SurfaceKind
private typealias Surf = Surface

/** v4: full-screen effects (show-off powers) drawn into the FX window; set by MonkeyView's power code. */
object ScreenFxHook {
    @Volatile var draw: ((Canvas) -> Unit)? = null
    @Volatile var activeFn: () -> Boolean = { false }
    fun active() = activeFn()
}

/** Whoever shows Settai: the overlay service (a small moving window) or the live wallpaper (whole screen). */
interface OverlayHost {
    fun screenWidth(): Int
    fun screenHeight(): Int
    fun placeWindow(x: Int, y: Int, w: Int, h: Int)
    fun openChat() {}
    /** Status-bar / navigation-bar heights, so Settai hangs below the status bar and walks above the nav bar. */
    fun topInset(): Int = 0
    fun bottomInset(): Int = 0
    /** Make the overlay window focusable (keyboard) or not (rest of the phone usable). */
    fun setWindowFocusable(on: Boolean) {}
    /**
     * v3.2 used this for flight; v4 never calls it: on Android 12+ a FLAG_NOT_TOUCHABLE overlay gets its window alpha
     * clamped to 0.8 by the system, which made Settai look faded while flying. Kept for old hosts / tests.
     */
    fun setTouchThrough(on: Boolean) {}
    /**
     * v4: a separate full-screen, untouchable effects window (particles, screen FX) drawn by [draw], or null to remove it.
     * Android caps its alpha at 0.8 (touch pass-through rule) — fine for effects, never used for Settai's body.
     * Returns false when the host has none (the wallpaper draws effects itself).
     */
    fun showFx(draw: ((Canvas) -> Unit)?): Boolean = false
    fun invalidateFx() {}
    /** Live wallpaper: the view covers the whole screen, there is no window to move and no dragging. */
    val wallpaper: Boolean get() = false
    /** v7.0 Settai Launcher home screen: full-screen with live widget/icon spots, dual Z-layers (behind/front), dragging & Mind/World. */
    val launcher: Boolean get() = false
    /** Climbing spots in screen px (wallpaper mode). */
    fun spots(): List<RectF> = emptyList()
    /** Called after every frame (the wallpaper redraws its surface). */
    fun onFrame() {}
    /** v3.2: the user chose "Quit Settai" and the exit animation finished: remove Settai for good. */
    fun quitSettai() {}
}

/**
 * Hosts the living monkey: frame loop, pose blending, touch handling, the cloud speech bubble,
 * window geometry (overlay) and, since v3:
 * - surfaces: screen edges and (wallpaper) climbing spots; walls and ceilings use the profile
 *   [Climber] with IK hands/feet, floors use the front [MonkeyRig];
 * - edge hide: fling Settai at a screen edge and it ducks behind it, leaving a little tab;
 * - entrances: on wake / un-hide it peeks out of an edge, grips it and pulls itself up;
 * - body actions requested by the AI ([dance] tags, do_action tool) or by the user.
 */
@SuppressLint("ViewConstructor")
class MonkeyView(
    context: Context,
    private val host: OverlayHost?,
    private val onChatRequest: (() -> Unit)? = null,
) : View(context), Speech, Choreographer.FrameCallback, ChatHub.Listener {

    private val d = resources.displayMetrics.density
    /** v10.13: the window grows for Large / XL characters so nothing is clipped (1.0 when every size is Normal). */
    private val sizeBox = if (host == null) 1f else runCatching { CompanionSize.max(context) }.getOrDefault(1f)
    val boxW = (96 * d * sizeBox).toInt()
    val boxH = (118 * d * sizeBox).toInt()
    private val rigScale = 0.66f * d
    private val groundPad = 7 * d
    private val overlap = 22 * d
    private val wp = host?.wallpaper == true
    private val ln = host?.launcher == true

    private val rig = MonkeyRig()
    /** Optional fixed character ID for secondary Duo/Trio companion windows in MonkeyOverlayService. */
    var forcedCharacterId: String? = null
        set(value) {
            field = value
            val c = if (value != null) com.combo.monkeycompanion.character.CharacterRegistry.byId(value) else com.combo.monkeycompanion.character.CharacterRegistry.current
            if (c != null) {
                character = c
                renderer = c.newRenderer().also { it.wear = com.combo.monkeycompanion.character.CharacterRegistry.wear(c) }
            }
        }
    /** Active partner views when running in Duo (2) or Trio (3) mode. */
    var partnerViews: List<MonkeyView> = emptyList()
    // v4.1: the body being drawn (swappable character + its wardrobe).
    private var character: com.combo.monkeycompanion.character.Character = com.combo.monkeycompanion.character.CharacterRegistry.current
    // ---------------- v5 fun stage (fun5/Stage.kt) ----------------
    private val stageHost = object : com.combo.monkeycompanion.fun5.StageHost {
        override val ctx: Context get() = context
        override val density get() = d
        override val character get() = this@MonkeyView.character
        override val wear get() = this@MonkeyView.wear
        override val bodyScale get() = rigScale
        override fun screenW() = sw()
        override fun screenH() = (host?.screenHeight() ?: height).toFloat()
        override fun headTop(out: FloatArray) { rig.headTop(out); out[0] += winX + sinkX; out[1] += winY }
        override fun feet(out: FloatArray) { feetScreen(out) }
        override fun say(text: String, ms: Long) = this@MonkeyView.say(text, ms)
        override fun sayInfo(text: String, ms: Long) = this@MonkeyView.sayInfo(text, ms)
        override fun chips(text: String, chips: List<com.combo.monkeycompanion.agent.QuickChip>) { showBubble(text, 14f, chips = chips, reply = false) }
        override fun play(s: MonkeyState, secs: Float) { if (!controller.isAsleep || s == MonkeyState.SLEEP) controller.play(s, secs) }
        override fun gag(g: com.combo.monkeycompanion.brain.Gag) = controller.gag(g)
        override fun dance() { controller.danceNow() }
        override fun particles() = particles
        override fun wake() { if (!running && live && !paused) start(); syncFx() }
        override fun free() = host != null && !wp && live && !paused && !exiting && !quitAsk && !powerOn && !dragging && !typing && !hiddenTab &&
            !controller.isAsleep && controller.state != MonkeyState.ENTRANCE && moment == null
        override fun quiet(): Boolean {
            val st = ChatHub.settings
            val sit = runCatching { com.combo.monkeycompanion.agent.Proactive.situation() }.getOrNull()
            val cal = java.util.Calendar.getInstance()
            return silent || sit?.inCall == true || sit?.fullscreen == true ||
                com.combo.monkeycompanion.agent.QuietHours.inside(cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE), st.quietStart, st.quietEnd)
        }
        override fun dnd() = silent
        override fun saver() = saver
        override fun visible() = live && !paused && !hiddenTab
        override fun floorX(frac: Float) {
            if (dragging || powerOn) return
            val nx = frac.coerceIn(0f, 1f) * max(0f, sw() - boxW)
            if (surf.kind != SK.FLOOR || climber.active) { surf = screenSurf(SK.FLOOR); climber.deactivate(); my = ground() }
            if (abs(nx - mx) > 0.5f) { controller.face(if (nx > mx) 1f else -1f); if (controller.state != MonkeyState.WALK) controller.play(MonkeyState.WALK, 0.4f) }
            mx = nx; falling = false; tweenT = -1f
        }
        override fun charX() = (mx / max(1f, sw() - boxW)).coerceIn(0f, 1f)
        override fun slideToEdge(side: Int) {
            if (dragging || powerOn || falling || hiddenTab) return
            controller.play(MonkeyState.STARTLED, 0.9f)
            if (climbs) goSurf(screenSurf(if (side < 0) SK.WALL_L else SK.WALL_R), 0.7f, 20f * d)
            else { val fx = mx; val fy = my; surf = screenSurf(SK.FLOOR); mx = if (side < 0) 0f else max(0f, sw() - boxW); my = ground(); slideFrom(fx, fy, 0.7f) }
            say(listOf("Wheee— slidiiing! 🛝", "Hold on, it's tilting! 😵").random(), 1800L)
        }
        override fun sleepNow(on: Boolean) { if (on) { endTyping(); controller.sleep() } else controller.wakeUp(Lines.pick(Lines.POKED_AWAKE)) }
        override fun leaveBy(side: Int) { hideToEdge(if (side < 0) -1 else 1, rest = true); ui.postDelayed({ if (hiddenTab) autoUnhide() }, 13_000L) }
        override fun comeBack() { if (hiddenTab) autoUnhide() }
        override fun log(what: String, result: String) { runCatching { com.combo.monkeycompanion.powers.ActionLog.add(context, what, result) } }
        override fun yawn(secs: Float) { yawnFor = secs; yawnLen = secs; controller.play(MonkeyState.IDLE, secs) }
        /** v10.19 daily moments: Brain ON = the AI writes the line (no canned fallback); Brain OFF = the scripted line. */
        override fun sayAi(request: String, fallback: String, ms: Long) {
            if (!aiVoiceOn()) { this@MonkeyView.say(fallback, ms); return }
            bodyVoice.note(nowSec())
            ChatHub.reactRequest(request, thought = true) { line ->
                if (line.isNullOrBlank() || !live || !aiVoiceOn()) return@reactRequest
                bodyVoice.note(nowSec())
                speak(line, com.combo.monkeycompanion.speech.Speech.defaultDuration(line).coerceIn(2500L, 5000L), Voice.AI)
            }
        }
    }
    private val stage by lazy { com.combo.monkeycompanion.fun5.Stage(stageHost) }
    private val v5On get() = host != null && !wp

    /** v5: games / clip from the app or chat. */
    fun v5Command(cmd: String) {
        if (!v5On) return
        when {
            cmd == "games" -> stage.showGames()
            cmd == "focus" -> startFocusBuddy(25)
            cmd == "focus_20" -> startFocusBuddy(20)
            cmd == "demo_otp" -> {
                com.combo.monkeycompanion.agent.DailyUtilityEngine.triggerDemoOtp(context, "482910", "HDFC Bank")
                sayInfo("🔐 Caught OTP 482910 from HDFC Bank! Tap the green pill on me to copy it in 1 tap 📋", 4500L)
                invalidate()
            }
            cmd == "demo_delivery" -> {
                com.combo.monkeycompanion.agent.DailyUtilityEngine.triggerDemoDelivery(context, "🛵 Swiggy: Order arriving in 4 mins")
                sayInfo("🛵 Live Delivery Tracker pinned: Swiggy order arriving in 4 mins!", 4000L)
                invalidate()
            }
            cmd == "eye_break" -> {
                com.combo.monkeycompanion.agent.DailyUtilityEngine.triggerEyeBreakNow(20f)
                sayInfo("🧘 20-20-20 Eye & Neck Break! Look 20ft away for 20 seconds, machan 👀🌿", 4500L)
                invalidate()
            }
            cmd == "breathe" -> startBreatheBuddy()
            cmd == "campfire" -> startCampfire(manual = true)
            cmd == "notif_card" -> openNotifSummaryCard(useDemoIfEmpty = false)
            cmd == "notif_card_demo" -> openNotifSummaryCard(useDemoIfEmpty = true)
            cmd == "skit" -> startSkitNow()
            cmd == "onboard" -> startOnboarding(force = true)
            cmd == "cat_mouse" -> {
                com.combo.monkeycompanion.mind.MindHub.tryBanter(com.combo.monkeycompanion.world.Director.Kind.CAT_MOUSE_CHASE)?.let {
                    startNeighbourBanter(it)
                }
            }
            cmd == "comfort_sign" -> com.combo.monkeycompanion.mind.MindHub.triggerComfort(forceKind = com.combo.monkeycompanion.heart.ComfortKind.SIGN_CARD)
            cmd == "comfort_hug" -> com.combo.monkeycompanion.mind.MindHub.triggerComfort(forceKind = com.combo.monkeycompanion.heart.ComfortKind.GLASS_KNOCK_HUG)
            cmd == "comfort_coffee" -> com.combo.monkeycompanion.mind.MindHub.triggerComfort(forceKind = com.combo.monkeycompanion.heart.ComfortKind.OFFER_COFFEE)
            cmd.startsWith("game_") -> stage.onChip(cmd)
            cmd.startsWith("clip") -> makeClip(cmd.removePrefix("clip").trim())
            cmd.startsWith("pair:") -> runCatching {
                val parts = cmd.removePrefix("pair:").split(':')
                val ev = com.combo.monkeycompanion.v5.PairProtocol.Event.entries.first { it.id == parts[0] }
                stage.sendToPeer(ev, parts.getOrNull(1)?.let { com.combo.monkeycompanion.v5.PairProtocol.Side.valueOf(it) })
            }
        }
    }

    private var clipBusy = false
    /** v5: render a ~5 s clip offscreen and open the share sheet. */
    private fun makeClip(hint: String) {
        if (clipBusy) return
        clipBusy = true
        val bit = com.combo.monkeycompanion.v5.ClipScript.pick(hint)
        sayInfo("🎬 Lights, camera… ${bit.title}! Making a 5 s clip…", 3000L)
        val r = com.combo.monkeycompanion.fun5.ClipRenderer(character, wear, bit)
        Thread {
            val res = runCatching { com.combo.monkeycompanion.fun5.ClipMaker.encode(context, r) }
            ui.post {
                clipBusy = false
                res.onSuccess { f ->
                    stage.xp(com.combo.monkeycompanion.v5.Friendship.Source.CLIP)
                    sayInfo("Clip ready! Pick WhatsApp status or anyone 📤 (you press post)", 3500L)
                    runCatching { com.combo.monkeycompanion.fun5.ClipMaker.share(context, f, com.combo.monkeycompanion.v5.ClipScript.caption(bit, character.name)) }
                    stageHost.log("Clip: ${bit.title}", "${f.length() / 1024} KB, share sheet opened")
                }.onFailure { e -> sayInfo("Camera jammed 🎬😵 (${e.message?.take(40)})", 3500L); stageHost.log("Clip failed", "${e.message}") }
            }
        }.start()
    }

    private var renderer: MonkeyRenderer = character.newRenderer().also { it.wear = com.combo.monkeycompanion.character.CharacterRegistry.wear(character) }
    private val wear get() = com.combo.monkeycompanion.character.CharacterRegistry.wear(character)
    /** Can this body use the monkey IK climber? */
    private val climbs get() = profile.climbsWithIK && character.can(com.combo.monkeycompanion.character.Locomotion.CLIMB)
    /**
     * v9.3: 4-Edge Zero-Gravity Perimeter Walking!
     * Cat (Billa), Mouse (Chittu), Settai, and any character when No Flight Powers are active
     * can walk horizontally AND vertically around all 4 edges of the screen (FLOOR, WALL_L, WALL_R, CEIL) with zero gravity!
     */
    var zeroGravityEdgeWalkEnabled: Boolean = true
    val canWalkScreenEdges: Boolean
        get() = host != null && !wp && (
            profile.walksScreenEdges ||
                climbs ||
                (!hovers && zeroGravityEdgeWalkEnabled && (
                    powerSetting() == Superpowers.OFF ||
                        (character is com.combo.monkeycompanion.character.CustomCharacter &&
                            (character as com.combo.monkeycompanion.character.CustomCharacter).spec.flightStyle in setOf(
                                com.combo.monkeycompanion.character.FlightStyle.WALK_AND_HOP,
                                com.combo.monkeycompanion.character.FlightStyle.CLIMB_WALLS
                            ))
                    ))
            )

    /** v10 (M1): motion state and per-frame maths live in the shared [PhysicsBody]; the old field names delegate to it. */
    private val pb = PhysicsBody()
    /** v10 (M1): how this character moves (was `character.id == "…"` checks); cached, no allocation. */
    private val profile get() = CharacterProfiles.of(character)
    private val physics get() = profile.physics

    /**
     * v9.3: Tom & Jerry 1.0-Second Mid-Air Scare Hang (`coyoteScareTimer`) before falling from mid-screen!
     */
    var coyoteScareTimer: Float get() = pb.coyoteT; private set(v) { pb.coyoteT = v }
    var coyoteScareTriggeredCount: Int = 0; private set

    /**
     * v9.3: Superboy & Armour Anna (Ironman) Random Edge-Collision Thrust Loss & Crash-Landing!
     * Phases: 0 = normal, 1 = edge impact & thrust sputter, 2 = plunging to bottom display, 3 = crash-landed at bottom with stars (1.8s).
     */
    var edgeThrustFailurePhase: Int get() = pb.crashPhase; private set(v) { pb.crashPhase = v }
    var edgeThrustFailureTimer: Float get() = pb.crashTimer; private set(v) { pb.crashTimer = v }
    var edgeThrustMultiplier: Float get() = pb.thrustMultiplier; private set(v) { pb.thrustMultiplier = v }
    var edgeCrashRot: Float get() = pb.crashRot; private set(v) { pb.crashRot = v }
    var edgeCrashCooldown: Float = 0f; private set
    var edgeCrashLandingCount: Int = 0; private set
    val isEdgeCrashLanding: Boolean get() = edgeThrustFailurePhase != 0

    /** v5: Superboy and Armour Anna hover where you let go (overlay only); they land when flung down. */
    private val hovers get() = host != null && !wp && !isEdgeCrashLanding && character.can(com.combo.monkeycompanion.character.Locomotion.FLOAT) &&
        character.can(com.combo.monkeycompanion.character.Locomotion.FLY) && powerSetting() != Superpowers.OFF
    private val isMask get() = profile.clumsyClimber && host != null && !wp
    private val hoverOn get() = hovers && !falling && surf.spot == -2 && surf.kind == SK.FLOOR && !dragging && impact == null
    private var heavyShake = 0f
    private val climber = Climber(rigScale)
    private val current = Pose()
    private val target = Pose()
    private val drawPose = Pose()
    private val bubble = CloudBubble(d, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15f, resources.displayMetrics))
    private val prefs = context.getSharedPreferences("settai_v3", Context.MODE_PRIVATE)
    /** Main-thread handler (View.postDelayed never runs for the wallpaper's window-less view). */
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())

    /** Monkey box top-left in screen px. */
    private var mx: Float get() = pb.x; set(v) { pb.x = v }
    private var my: Float get() = pb.y; set(v) { pb.y = v }

    // ---------------- Surfaces ----------------

    private var spots: List<RectF> = emptyList()
    private var surf: Surf get() = pb.surface; set(v) { pb.surface = v }

    /** v7.0 keyboard dodge: never sit on or below the soft keyboard when the user is typing in another app. */
    var keyboardDodge = false; private set
    private var preDodgeX = 0f; private var preDodgeY = 0f; private var preDodgeSurf: Surf? = null

    private fun externalImeOpen(): Boolean =
        host != null && !wp && !typing && com.combo.monkeycompanion.fun5.Stage.imeVisible

    private fun realGround(): Float = host?.let { (it.screenHeight() - it.bottomInset() - boxH).toFloat() } ?: 0f

    fun imeSafeBottomY(): Float {
        val sh = (host?.screenHeight() ?: height).toFloat().coerceAtLeast(400f)
        val top = com.combo.monkeycompanion.fun5.Stage.imeTopY(sh)
        return (top - boxH - 12 * d).coerceAtLeast(topY() + 4 * d)
    }

    private fun ground(): Float = if (externalImeOpen()) min(realGround(), imeSafeBottomY()) else realGround()

    /** Relocates Settai above the keyboard immediately when the IME opens, and restores when it closes. */
    fun syncImeDodge() {
        val h = host ?: return
        if (wp) return
        if (externalImeOpen()) {
            if (notifCardOpen) closeNotifSummaryCard()
            if (bubbleOn && !pendingShown) { closeMenu(); hush() }
            val safeY = imeSafeBottomY()
            if (hiddenTab) {
                if (hideSide == 2) hideSide = if (tabPos < sw() / 2f) -1 else 1
                clampTab(); updateGeometry(); invalidate()
                return
            }
            val effectiveY = if (tweenT >= 0f) tToY else my
            if (!keyboardDodge || effectiveY > safeY + 2f) {
                val first = !keyboardDodge
                if (first) { keyboardDodge = true; preDodgeX = mx; preDodgeY = my; preDodgeSurf = surf; BSN.record("the keyboard pushed me aside", BSN.Feeling.SQUISHED) }
                if (falling) { falling = false; vy = 0f; arc = null; landStyle = Landings.Style.SIMPLE }
                val fx = mx; val fy = my
                val left = mx + boxW / 2f < sw() / 2f
                val targetY = (safeY - 14 * d).coerceIn(topY() + 6 * d, safeY)
                if (climbs) {
                    val wall = screenSurf(if (left) SK.WALL_L else SK.WALL_R)
                    surf = wall
                    mx = wall.fix; my = targetY.coerceIn(wall.a, wall.b)
                    startClimber(wall)
                } else {
                    val targetX = if (left) 8 * d else max(8 * d, sw() - boxW - 8 * d)
                    surf = freeSurf(targetY)
                    mx = targetX; my = targetY
                }
                if (abs(mx - fx) > 1f || abs(my - fy) > 1f) slideFrom(fx, fy, 0.18f)
                updateGeometry(); invalidate()
                if (first) {
                    val sh = h.screenHeight().toFloat()
                    val imeTop = com.combo.monkeycompanion.fun5.Stage.imeTopY(sh).toInt()
                    runCatching { com.combo.monkeycompanion.powers.ActionLog.add(context, "Keyboard open -> dodged above IME (top=$imeTop px)", character.name) }
                }
                if (!running && live && !paused) start()
            }
        } else if (keyboardDodge) {
            keyboardDodge = false
            val prev = preDodgeSurf; preDodgeSurf = null
            if (!dragging && !falling && !hiddenTab) {
                val fx = mx; val fy = my
                surf = if (prev != null && prev.spot == -2) freeSurf(preDodgeY.coerceIn(topY(), realGround())) else screenSurf(SK.FLOOR)
                mx = preDodgeX.coerceIn(0f, max(0f, sw() - boxW))
                my = if (surf.spot == -2) surf.line else realGround()
                climber.deactivate()
                if (abs(mx - fx) > 1f || abs(my - fy) > 1f) slideFrom(fx, fy, 0.24f)
                updateGeometry(); invalidate()
                if (!running && live && !paused) start()
            }
        }
    }
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

    private var falling: Boolean get() = pb.falling; set(v) { pb.falling = v }
    private var vy: Float get() = pb.vy; set(v) { pb.vy = v }
    private var dragging = false
    private var tweenT: Float get() = pb.tweenT; set(v) { pb.tweenT = v }
    private var tweenDur: Float get() = pb.tweenDur; set(v) { pb.tweenDur = v }
    private var tweenArc: Float get() = pb.tweenArc; set(v) { pb.tweenArc = v }
    private var tweenDone: (() -> Unit)? = null
    private var tFromX: Float get() = pb.fromX; set(v) { pb.fromX = v }
    private var tFromY: Float get() = pb.fromY; set(v) { pb.fromY = v }
    private var tToX: Float get() = pb.toX; set(v) { pb.toX = v }
    private var tToY: Float get() = pb.toY; set(v) { pb.toY = v }
    private val tmpPt = FloatArray(2)
    /** v10.7 Disney-principles part 1 (visual pose post-pass only; never touches physics or Random). */
    private val animEngine = com.combo.monkeycompanion.engine.anim.AnimEngine()
    private val animPt = FloatArray(2)
    /** Settings › "Smooth animation (beta)"; off = exact v10.6 drawing. */
    private var smoothAnim = runCatching { ChatHub.settings.smoothAnimation }.getOrDefault(true)
    // ---- v10.8 living layers: visual only, each with its OWN Random (never the frame-loop RNG) ----
    /** Settings › "Living reactions (beta)"; off = v10.7 tap reactions. */
    private var livingOn = runCatching { ChatHub.settings.livingReactions }.getOrDefault(true)
    private val living = com.combo.monkeycompanion.engine.anim.LivingLayer(0x5E77A1L xor System.nanoTime())
    private val reactionLayer = com.combo.monkeycompanion.engine.touch.ReactionLayer()
    private var picker = com.combo.monkeycompanion.engine.touch.ReactionPicker(1L)
    private var pickerFor = ""
    private val touchTracker = com.combo.monkeycompanion.engine.touch.TouchTracker()
    private val stageId = System.identityHashCode(this) or 1
    private var livingLastState = MonkeyState.IDLE
    private var livingLastArc = false
    private val fireworks = com.combo.monkeycompanion.rig.Fireworks(System.nanoTime())
    /** v10.9 Rocket Girl's bottle-rocket sky shows (two, so a new one can start while the last one is still in the sky). */
    private val skyShows = arrayOf(com.combo.monkeycompanion.rig.SkyShow(System.nanoTime() xor 0x5C1L, fireworks), com.combo.monkeycompanion.rig.SkyShow(System.nanoTime() xor 0x5C2L, fireworks))
    private var skyShowNext = 0
    private var skyShowStarted = false
    private val skyActive get() = skyShows[0].active || skyShows[1].active
    val debugSkyShows get() = skyShows
    private val fxRnd = kotlin.random.Random(System.nanoTime() xor 0x10C8L)
    private var downPart = com.combo.monkeycompanion.engine.touch.BodyPart.NONE
    private var downAt = 0L
    private var downPressure = 0.5f
    private var festiveK = 0f
    private var festiveCheckAt = -1L
    private var exhaustAcc = 0f
    private var boostWhistled = false
    private val livPt = FloatArray(2)
    init {
        fireworks.onBoom = { pitch, crackle, big ->
            debugBooms++
            com.combo.monkeycompanion.audio.Sfx.play(context, if (big) com.combo.monkeycompanion.audio.Sfx.S.MEGA else com.combo.monkeycompanion.audio.Sfx.S.FW_BOOM, if (big) 0.9f else 0.7f, pitch)
            if (crackle) com.combo.monkeycompanion.audio.Sfx.play(context, com.combo.monkeycompanion.audio.Sfx.S.FW_CRACKLE, 0.5f, 0.85f + fxRnd.nextFloat() * 0.3f)
        }
        fireworks.onLaunch = { com.combo.monkeycompanion.audio.Sfx.play(context, com.combo.monkeycompanion.audio.Sfx.S.WHISTLE, 0.25f, 1.2f + fxRnd.nextFloat() * 0.4f) }
    }
    private val tmpA = FloatArray(2)
    private val tmpB = FloatArray(2)
    /** v10.9: a window point rotated with the rider (arcRot around the box centre), like the drawing. */
    private fun rotArc(pt: FloatArray) {
        if (arcRot == 0f) return
        val cx = ox + boxW / 2f; val cy = oy + boxH / 2f
        val a = Math.toRadians(arcRot.toDouble()); val c = kotlin.math.cos(a).toFloat(); val sn = kotlin.math.sin(a).toFloat()
        val dx = pt[0] - cx; val dy = pt[1] - cy
        pt[0] = cx + dx * c - dy * sn; pt[1] = cy + dx * sn + dy * c
    }
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
        override fun startFall() {
            if (chargeTrip) return            // v6: perched under the battery icon for the charger moment
            when {
                hovers && selfLand -> { selfLand = false; beginSmartFall(false, if (character.id in Landings.ARC_IDS) Landings.Style.ROCKET_ARC else null, "long idle: lands by itself") }
                hovers -> patrolFlight()          // v5: flying heroes don't drop, they float somewhere else
                else -> beginSmartFall(byUser = false)   // v4.5: its own drops get smart landings too
            }
        }
        override fun roam(r: Float, roamOn: Boolean): Boolean = this@MonkeyView.roam(r, roamOn)
    }

    private fun beginFall() {
        surf = screenSurf(SK.FLOOR)
        climber.deactivate()
        tweenT = -1f; tweenDone = null
        falling = true; vy = 0f
        landStyle = Landings.Style.SIMPLE; landBeat = 0f; landBounced = false
        BSN.record("I'm falling", BSN.Feeling.WHOOSH)   // v10.17 motion feeling
    }

    /** Animate the box from (fx, fy) to its current (mx, my); [arc] = leap height in px. */
    private fun slideFrom(fx: Float, fy: Float, dur: Float, arc: Float = 0f, done: (() -> Unit)? = null) {
        tToX = mx; tToY = my; tFromX = fx; tFromY = fy
        mx = fx; my = fy
        tweenDur = dur; tweenT = 0f; tweenArc = arc; tweenDone = done
    }

    /** Move onto another surface (corner, reach across, or leap); climber limbs re-grip on the way. */
    private fun goSurf(n0: Surf, dur: Float = 0.38f, arc: Float = 0f, done: (() -> Unit)? = null) {
        val n = if (n0.climb && !climbs && !canWalkScreenEdges) screenSurf(SK.FLOOR) else n0
        val fx = mx; val fy = my
        surf = n
        clampToSurf()
        if (n.climb && climbs) {
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

    // ---------------- v3.2: auto-rest, frame pacing, energy ----------------

    /** elapsedRealtime (ms) of the last interaction: touch, chat, AI action. */
    private var lastInteract = SystemClock.elapsedRealtime()
    /** Hidden by auto-rest (not by a swipe): comes back on unlock / important events. */
    private var resting = false
    /** Seconds until we dash off after the rest line (-1 = not going). */
    private var restGoIn = -1f
    private var restCheckIn = 1f
    private var restSecs = 120
    private var saver = false
    private var comeOutOnUnlock = true
    /** Wallpaper auto-rest: a nap; the frame loop stops once settled. */
    private var restNap = false
    /** Tab peek (just the eyes, maybe binoculars) time in s; -1 = none. */
    private var tabPeekT = -1f
    private var tabPeekBino = false
    /** Double-tapped the tab: open the bubble chat when the entrance finishes. */
    private var chatAfterEntrance = false
    private var screenOffAt = 0L
    /** A line for an event that happened while the screen was off, said on unlock. */
    private var pendingEventLine: String? = null
    /** Comedic beats (" || " in a line) still to show, and when the next one is due. */
    private val beats = ArrayDeque<String>()
    private var beatAt = 0f
    private var beatDoubleTake = false
    private var batteryLow = false
    private var hot = false

    /** The user did something with Settai: restart the idle clock. */
    fun noteInteraction() {
        val nowE = SystemClock.elapsedRealtime()
        if (lastInteract > 0L && nowE - lastInteract > 20 * 60_000L) BSN.record("you came back after a long while", BSN.Feeling.EXCITED)   // v10.17
        lastInteract = nowE
        controller.touched()
        restGoIn = -1f
    }

    init {
        // v4: four rude pokes in 20 s -> Kovam Mode (if powers are on), shortly after the grumpy line.
        controller.onFurious = {
            if (host != null && powerSetting() != Superpowers.OFF && !saver) ui.postDelayed({ usePower(explicit = false, force = Superpower.KovamMode) }, 1200L)
        }
        if (host != null) {
            hiddenTab = !wp && prefs.getBoolean("hidden", false)
            resting = hiddenTab && prefs.getBoolean("resting", false)
            hideSide = prefs.getInt("hideSide", 1)
            if (wp || ln) spots = host.spots()
            surf = screenSurf(SK.FLOOR)
            mx = host.screenWidth() - boxW - 12 * d
            my = ground()
            clampToSurf()
            tabPos = prefs.getFloat("tabPos", host.screenHeight() * 0.45f)
            if (hiddenTab) { controller.play(MonkeyState.HIDDEN); clampTab() }
        }
        character.fill(current, MonkeyState.IDLE, 0f, 0f, controller.anim, wear)
        controller.doubleTapAction = { onDoubleTapChat() }
    }

    // ---------------- Speech ----------------

    /** Remaining pages of a long reply, shown one after another. */
    private val pages = ArrayDeque<String>()
    private var thinkingBubble = false
    private var thinkDots = 0f
    private var pendingShown = false
    private var replyStartT = -1f

    /** v4: the AI reply on screen (for ⚑ Report); null for canned lines. */
    private var aiReply: String? = null

    // ---- v10.14 speech only when settled: lines (and chat replies) that arrive mid-flight wait for the landing ----
    private class HeldLine(val text: String, val ms: Long, val at: Float, val voice: Voice = Voice.SCRIPT)
    private val heldLines = ArrayDeque<HeldLine>()
    private var heldReply: String? = null; private var heldReplyDone = false
    private var settledFor = 0f
    private var heldAction: BodyAction? = null
    /** Moving right now (flung, flying, falling, dragged, dashing, a moving power): bubbles wait. */
    private val movingNow: Boolean get() = rocketFlyOn || falling || dragging || arc != null || controller.state == MonkeyState.FALLING ||
        controller.state == MonkeyState.DRAGGED || (powerOn && flight.active && flight.phase != com.combo.monkeycompanion.brain.PowerPhase.POSE)
    val debugHeldLines get() = heldLines.size
    val debugHeldReply get() = heldReply
    private fun stepHeldSpeech(dt: Float) {
        if (movingNow) { settledFor = 0f; return }
        settledFor += dt
        if (settledFor < SETTLE_SECS) return
        heldAction?.let { a -> heldAction = null; onAction(a) }   // v10.15: body actions also wait until settled
        heldReply?.let { r -> heldReply = null; onReply(r, heldReplyDone); return }
        while (heldLines.isNotEmpty() && t - heldLines.first().at > HELD_STALE_SECS) heldLines.removeFirst()   // stale: drop
        heldLines.removeFirstOrNull()?.let { h -> heldLines.clear(); speak(h.text, h.ms, h.voice) }
        stepBodyVoice()
    }

    /** v10.17: who wrote a line: a scripted (canned) line, an info/utility message, or the AI's own body-felt line. */
    private enum class Voice { SCRIPT, INFO, AI }
    /** Scripted lines: with Give Brain ON they are not shown; the moment asks the AI for its own line instead. */
    override fun say(text: String, durationMs: Long) = speak(text, durationMs, Voice.SCRIPT)
    /** Reminders, permissions, errors, instructions: always shown as written. */
    fun sayInfo(text: String, durationMs: Long = 3500L) = speak(text, durationMs, Voice.INFO)

    // ---- v10.17 AI speech from Body Sense (Give Brain ON): no canned lines; short spontaneous lines at real moments ----
    private val bodyVoice = com.combo.monkeycompanion.ai.BodyVoice()
    private var lastRestAt = 0L
    init {
        // v10.17: every body event (fall, bump, hold, keyboard push, charger, fling...) is a moment the AI may speak about
        BSN.onEvent = { what -> post { if (live && aiVoiceOn()) bodyMoment(what) } }
    }
    private var aiVoiceCache = false
    private var aiVoiceAt = -1L
    private fun aiVoiceOn(): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - aiVoiceAt > 1000L || aiVoiceAt < 0L) { aiVoiceAt = now; aiVoiceCache = runCatching { ChatHub.isBrainEnabled() }.getOrDefault(false) }
        return aiVoiceCache
    }
    private fun nowSec() = android.os.SystemClock.uptimeMillis() / 1000f
    /** A scripted line would have played here: something happened. Ask the AI (rate-limited) instead. */
    private fun bodyMoment(kind: String) {
        // v10.18: touches are voiced by the reaction engine (feelTouch); don't also ask for a generic line about them
        if (TOUCH_EVENTS.any { kind.startsWith(it) }) return
        val now = nowSec()
        if (!bodyVoice.wantEvent(now)) return
        askBodyVoice(com.combo.monkeycompanion.ai.BodyVoice.eventText(kind), now)
    }
    /** Per frame (cheap, no RNG): a long quiet stretch gets one spontaneous line now and then. */
    private fun stepBodyVoice() {
        if (debugQuiet || hiddenTab || bubbleOn || pendingShown || thinkingBubble || typing) return
        if (controller.state != MonkeyState.IDLE) return
        stepReactionRefill()   // v10.18
        val now = nowSec()
        bodyVoice.expire(now)
        if (!bodyVoice.wantIdle(now) || !aiVoiceOn()) return
        if (!idleLineAllowed()) return   // v10.18.1
        idleAskAt = SystemClock.elapsedRealtime()
        askBodyVoice(com.combo.monkeycompanion.ai.BodyVoice.IDLE_EVENT, now)
    }
    // ---- v10.18 "Feel the touch": every touch gets an AI-written line (Brain ON only; see docs/REACTION_ENGINE.md) ----
    private var feelLastKind: com.combo.monkeycompanion.ai.react.ReactKind? = null
    private var feelLastAt = 0L
    private var feelStreak = 0
    private var friendLevelCache = 1
    private var friendLevelAt = -1L
    private var refillCheckAt = 0L

    /** Coarse mood for the reaction bank (no Random: the frame-loop random stream stays untouched). */
    private fun reactMoodKey(): com.combo.monkeycompanion.ai.react.MoodKey {
        val nowE = SystemClock.elapsedRealtime()
        if (friendLevelAt < 0L || nowE - friendLevelAt > 30_000L) {
            friendLevelAt = nowE
            friendLevelCache = runCatching { com.combo.monkeycompanion.v5.Friendship.level(com.combo.monkeycompanion.fun5.Fun5Store.friendship(context).xp) }.getOrDefault(1)
        }
        val energy = runCatching { com.combo.monkeycompanion.mind.MindHub.world.state.energy }.getOrDefault(-1f)
        return com.combo.monkeycompanion.ai.react.MoodKey.of(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
            controller.grumpyLevel, controller.isAsleep, energy, friendLevelCache)
    }

    /** What he was doing when touched, in plain words. */
    private fun feelDoing(): String = when {
        controller.isAsleep -> "sleeping"
        powerOn -> "using a superpower"
        rocketFlyOn || arc != null -> "flying"
        falling -> "falling"
        controller.state == MonkeyState.DANCE -> "dancing"
        bubbleOn -> "talking"
        else -> ""
    }

    /** One touch -> the reaction engine: instant line from the bank, a fresh AI thought for big moments / streaks. */
    private fun feelTouch(kind: com.combo.monkeycompanion.ai.react.ReactKind, zone: String = "", force: Float = 0.5f, extra: String = "", doing: String? = null) {
        if (!live || debugQuiet || !aiVoiceOn()) return
        val nowE = SystemClock.elapsedRealtime()
        feelStreak = if (kind == feelLastKind && nowE - feelLastAt < 3_000L) feelStreak + 1 else 1
        feelLastKind = kind; feelLastAt = nowE
        bodyVoice.note(nowSec())   // the touch has its own voice: no generic "something happened" line on top
        val feel = com.combo.monkeycompanion.ai.react.TouchFeel(kind, com.combo.monkeycompanion.ai.react.TouchFeel.partWords(zone),
            com.combo.monkeycompanion.ai.react.Strength.of(force), feelStreak, doing ?: feelDoing(), extra)
        val id = character.id
        com.combo.monkeycompanion.ai.ReactionHub.onTouch(context, id, feel, reactMoodKey()) { line ->
            if (!live || character.id != id || !aiVoiceOn()) return@onTouch
            bodyVoice.note(nowSec())
            speak(line, com.combo.monkeycompanion.speech.Speech.defaultDuration(line).coerceIn(2000L, 4500L), Voice.AI)
        }
    }

    /** v10.18.1 Background AI setting: "off" / "gentle" / "fast" (re-read every few seconds). */
    private var bgMode = "gentle"
    private var bgModeAt = -1L
    private fun bgAiMode(): String {
        val nowE = SystemClock.elapsedRealtime()
        if (bgModeAt < 0L || nowE - bgModeAt > 5_000L) { bgModeAt = nowE; bgMode = runCatching { com.combo.monkeycompanion.ai.AppSettings(context).backgroundAi }.getOrDefault("gentle") }
        return bgMode
    }

    /**
     * v10.18.1 "is the phone resting?": nobody touched or chatted for 30 s, no keyboard, no call/fullscreen app,
     * and the battery is above 30 % (or charging). Gentle background AI only runs then, so the phone stays smooth.
     */
    private fun phoneResting(): Boolean {
        if (typing || keyboardDodge || ChatHub.busy) return false
        if (SystemClock.elapsedRealtime() - lastInteract < 30_000L) return false
        val bi = runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull() ?: return true
        val lvl = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val scale = bi.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val plugged = bi.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        return plugged || lvl < 0 || lvl * 100 / scale >= 30
    }

    /** Quiet moments: let the AI prepare reaction lines (checked every 5 s; the engine spaces real requests out). */
    private fun stepReactionRefill() {
        val nowE = SystemClock.elapsedRealtime()
        if (nowE - refillCheckAt < 5_000L) return
        refillCheckAt = nowE
        if (!aiVoiceOn() || bodyVoice.inFlight || ChatHub.busy) return
        when (bgAiMode()) {
            "off" -> return
            "fast" -> com.combo.monkeycompanion.ai.ReactionHub.maybeRefill(context, character.id, reactMoodKey(), 45_000L, 4)
            else -> if (phoneResting()) com.combo.monkeycompanion.ai.ReactionHub.maybeRefill(context, character.id, reactMoodKey(), 10 * 60_000L, 3)
        }
    }

    /** v10.18.1: idle Body Sense lines follow the Background AI setting too (off: none; gentle: rare and only at rest). */
    private var idleAskAt = 0L
    private fun idleLineAllowed(): Boolean = when (bgAiMode()) {
        "off" -> false
        "fast" -> true
        else -> phoneResting() && SystemClock.elapsedRealtime() - idleAskAt >= 5 * 60_000L
    }

    private fun askBodyVoice(event: String, now: Float) {
        if (ChatHub.busy) return
        val tok = bodyVoice.begin(now)
        ChatHub.spontaneous(event) { line ->
            if (!bodyVoice.accept(tok, nowSec())) return@spontaneous   // too slow / superseded: dropped, never canned text
            if (line.isNullOrBlank() || !aiVoiceOn() || !live) return@spontaneous
            speak(line, com.combo.monkeycompanion.speech.Speech.defaultDuration(line).coerceIn(2200L, 5000L), Voice.AI)
        }
    }

    private fun speak(text: String, durationMs: Long, voice: Voice) {
        if (debugQuiet) return
        if (voice == Voice.SCRIPT && aiVoiceOn()) { bodyMoment(controller.state.name.lowercase().replace('_', ' ')); return }
        if (movingNow || (settledFor < SETTLE_SECS && (heldLines.isNotEmpty() || heldReply != null))) {
            if (heldLines.size >= 4) heldLines.removeFirst()
            heldLines.addLast(HeldLine(text, durationMs, t, voice)); return
        }
        aiReply = if (voice == Voice.AI) text else null
        if (hiddenTab || controller.state == MonkeyState.HIDDEN) return  // nobody to see the bubble
        if (pendingShown || thinkingBubble) return  // don't cover a question or the thinking cloud
        if (typing) return  // don't swap the line the user is replying to
        pages.clear(); beats.clear()
        val parts = Lines.beats(text)
        lastLine = Lines.flat(text)
        if (parts.size > 1) {
            // Comic timing: first half, a pause, then the punchline (sometimes with a double-take).
            showBubble(parts[0], 2.6f)
            for (i in 1 until parts.size) beats.addLast(parts[i])
            beatAt = t + 1.4f + parts[0].length * 0.025f
            beatDoubleTake = Random.nextFloat() < 0.35f
        } else showBubble(text, durationMs / 1000f)
    }

    private fun stepBeats() {
        if (beats.isEmpty() || t < beatAt) return
        if (!bubbleOn || pendingShown || thinkingBubble || typing) { beats.clear(); return }
        val nx = beats.removeFirst()
        showBubble(nx, Speech.defaultDuration(nx) / 1000f + 0.6f)
        beatAt = t + 1.4f + nx.length * 0.025f
        if (beatDoubleTake && controller.state == MonkeyState.IDLE) controller.play(MonkeyState.STARTLED, 0.5f)
        beatDoubleTake = false
    }

    /** A line Settai said that isn't in the chat history yet (canned line / quip); context for a bubble reply. */
    private var lastLine: String? = null

    /**
     * [reply]: show the reply field. By default only while a conversation is going (double-tap /
     * long-press 💬, or within ~60 s of the last message); plain lines (quips, warnings, reactions) are text only.
     */
    private fun showBubble(text: String, life: Float, buttons: List<String> = emptyList(), talk: Boolean = true, reply: Boolean? = null,
                           chips: List<com.combo.monkeycompanion.agent.QuickChip> = emptyList()) {
        val withReply = (reply ?: convoActive) && replyField != null && buttons.isEmpty() && chips.isEmpty()
        bubbleChips = chips; arcOn = false; lastShown = text
        if (chips.isNotEmpty()) bubble.setText(text, (230 * d).toInt(), chips.map { it.label }, false, chips = true)
        else bubble.setText(text, (210 * d).toInt(), buttons, withReply)
        bubble.reportable = aiReply != null && buttons.isEmpty()
        val alreadyUp = bubbleOn && bubbleAge in POP..bubbleLife
        bubbleOn = true
        bubbleAge = if (alreadyUp) POP else 0f
        // Leave time to tap the reply field; longer while a bubble conversation is going.
        val extra = if (!withReply) 0f else 8f
        bubbleLife = (if (alreadyUp) POP else 0f) + life + extra
        bubbleDragX = 0f; bubbleFlyV = 0f
        // v4.4: an open bubble chat stays until ✕ / swipe / tap outside / the inactivity timeout (Settings).
        chatOpen = withReply && pages.isEmpty()
        if (chatOpen) {
            bubbleLife = 1e6f; chatActiveAt = SystemClock.elapsedRealtime()
            ui.removeCallbacks(chatCheck); ui.postDelayed(chatCheck, 1000L)
        }
        talkFor = if (talk) (if (alreadyUp) POP else 0f) + min(2.5f, 0.4f + text.length * 0.05f) else 0f
    }

    // ---------------- v4.4 bubble chat dismissal ----------------
    private var chatOpen = false
    private var chatActiveAt = 0L
    private var bubbleSwipe = false
    private var swipeDownX = 0f
    /** Bubble offset while it's swiped (follows the finger) and its fly-away speed once let go. */
    private var bubbleDragX = 0f
    private var bubbleFlyV = 0f
    private val chatCheck = object : Runnable {
        override fun run() {
            if (!chatOpen || !bubbleOn) { chatOpen = false; return }
            val now = SystemClock.elapsedRealtime()
            // Never closes while typing (keyboard up) or while a reply is thinking / streaming: those reset the timer.
            if (typing || thinkingBubble || ChatHub.busy || pages.isNotEmpty()) chatActiveAt = now
            val limit = runCatching { ChatHub.settings.chatTimeoutSecs }.getOrDefault(120)
            if (limit > 0 && now - chatActiveAt >= limit * 1000L) { closeChat(); return }
            ui.postDelayed(this, 1000L)
        }
    }

    /** Close the bubble chat (✕, swipe, tap outside, back, timeout). Keeps a typed draft in the field. */
    private fun closeChat(flyDir: Int = 0) {
        chatOpen = false; ui.removeCallbacks(chatCheck)
        endTyping()
        convoUntil = -1f
        bubbleChips = emptyList()
        if (flyDir != 0) bubbleFlyV = flyDir * 1800f * d / 2.6f
        if (bubbleOn) bubbleLife = bubbleAge
    }

    override fun hush() {
        beats.clear()
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
        noteInteraction()
        if (hiddenTab) return
        pages.clear(); beats.clear()
        thinkingBubble = true
        replyStartT = -1f
        controller.startThinking()
        showBubble("Hmm… thinking", 600f, talk = false, reply = false, chips = listOf(com.combo.monkeycompanion.agent.QuickChip("menu", "✕ Stop", "think_stop")))
    }

    override fun onReply(text: String, done: Boolean) {
        if (hiddenTab) return
        if (movingNow && text.isNotBlank()) { heldReply = text; heldReplyDone = done; settledFor = 0f; return }   // v10.14: show it once settled
        if (text.isBlank() && !done) return
        if (replyStartT < 0f) replyStartT = t
        thinkingBubble = false
        lastLine = null
        beats.clear()
        if (done) { noteInteraction(); Lines.note(Lines.Event.CHATTED) }
        if (!done) {
            controller.startTalking()
            if (!pendingShown) showBubble(paginate(text).last(), 600f, reply = false)
            return
        }
        if (convoActive) touchConvo()
        aiReply = text.takeIf { it.isNotBlank() }
        val all = paginate(text)
        val quick = t - replyStartT < 1.5f  // arrived all at once: page from the start
        controller.endChat(3f)
        if (pendingShown) return
        if (com.combo.monkeycompanion.mind.MindHub.neighbourMode != "SOLO") {
            val buddy = com.combo.monkeycompanion.mind.MindHub.activeCreatures().drop(1).firstOrNull()
            if (buddy != null) {
                val (bst, _) = com.combo.monkeycompanion.mind.MindHub.director.sideReaction(buddy, "", text, com.combo.monkeycompanion.mind.MindHub.world.state)
                sideQuipBuddyId = buddy; sideQuipLine = null; sideQuipState = bst; sideQuipUntil = t + 3.8f
            }
        }
        pages.clear()
        if (all.size > 1 && quick) {
            pages.addAll(all)
            nextPage()
        } else {
            val last = all.last()
            showBubble(last, Speech.defaultDuration(last) / 1000f + 1.5f)
        }
        ChatHub.pending?.let { onPending(it) }
    }

    override fun onError(message: String) {
        thinkingBubble = false
        controller.endChat(2f)
    }

    override fun onPending(action: PendingAction?) {
        if (action == null) {
            if (pendingShown) { pendingShown = false; bubbleLife = bubbleAge }
            return
        }
        noteInteraction()
        quitAsk = false
        if (exiting) return
        if (hiddenTab) unhide()
        thinkingBubble = false
        pages.clear(); beats.clear()
        pendingShown = false
        showBubble(action.question, 25f, listOf("Yes", "No"))
        pendingShown = true
    }

    /** A timer/alarm rang: wake up (if the phone is unlocked) so the jump + line are seen. */
    override fun onAlert(text: String) {
        if (host == null) return
        sleepFrames = 0
        noteInteraction()
        setPaused(false)
        if (hiddenTab) unhide()
        restNap = false
        val km = context.getSystemService(KeyguardManager::class.java)
        if (controller.isAsleep && (km == null || !km.isKeyguardLocked)) onUserPresent(force = true)
    }

    /** Visual gags after phone powers: sunglasses (brighter), paws over ears (louder), a big yawn (screen timeout). */
    override fun onProp(prop: com.combo.monkeycompanion.powers.Prop) {
        when (prop) {
            com.combo.monkeycompanion.powers.Prop.SHADES -> shadesT = 6f
            com.combo.monkeycompanion.powers.Prop.EAR_PAWS -> earsT = 3.5f
            com.combo.monkeycompanion.powers.Prop.YAWN -> yawnT = 2.4f
            com.combo.monkeycompanion.powers.Prop.GOGGLES -> gogglesT = 4f
            com.combo.monkeycompanion.powers.Prop.MAGNIFIER -> magT = 5f
            com.combo.monkeycompanion.powers.Prop.NOTEBOOK -> noteT = 4f
            com.combo.monkeycompanion.powers.Prop.STETHOSCOPE -> stethT = 5f
            com.combo.monkeycompanion.powers.Prop.GLOBE -> globeT = 4.5f
            com.combo.monkeycompanion.powers.Prop.NONE -> Unit
        }
        if (prop != com.combo.monkeycompanion.powers.Prop.NONE) { sleepFrames = 0; setPaused(false) }
    }
    private var shadesT = 0f
    private var gogglesT = 0f; private var magT = 0f; private var noteT = 0f; private var stethT = 0f; private var globeT = 0f
    /** v4: 0..1 steering wheel while the screen agent drives; [micT] while push-to-talk listens. */
    private var wheelK = 0f
    private var micK = 0f
    private val drivingListener: (Boolean) -> Unit = { on -> ui.post { if (on) { sleepFrames = 0; setPaused(false); if (hiddenTab && !autoUnhide()) return@post; showBubble("🛞 ${character.name} is driving! Tap me to stop.", 4f) } } }
    private var earsT = 0f
    private var yawnT = 0f
    /** 0..1 ramp in/out over [total] seconds for a prop timer [left]. */
    private fun envelope(left: Float, total: Float): Float {
        val e = if (left <= 0f) 0f else min(1f, min(left / 0.4f, (total - left) / 0.3f + 0.05f)).coerceIn(0f, 1f)
        // v10.7 slow in & slow out on prop envelopes (sunglasses, goggles, notebook...)
        return if (smoothAnim) com.combo.monkeycompanion.engine.anim.Easing.apply(profile.anim.easeStyle.let {
            if (it == com.combo.monkeycompanion.engine.anim.EaseStyle.BACK) com.combo.monkeycompanion.engine.anim.EaseStyle.SMOOTHSTEP else it }, e) else e
    }

    override fun onAnnounce(text: String) { aiReply = null; pendingShown = false; pages.clear(); beats.clear(); showBubble(text, 3.5f) }

    // ---------------- v4.2: chips, pipes, quick-action arc, hold-to-talk ----------------

    /** Chips on the bubble now (pipe / follow-ups / the arc); tapping one runs [tapChip]. */
    private var bubbleChips: List<com.combo.monkeycompanion.agent.QuickChip> = emptyList()
    private var arcOn = false
    private var lastShown: String? = null

    /** Proactive lines only when Settai is out, awake and not in the middle of something with you. */
    private fun canShowPipe(): Boolean = host != null && !wp && live && !paused && !hiddenTab && !exiting && !powerOn && !dragging &&
        !controller.isAsleep && controller.state != MonkeyState.HIDDEN && controller.state != MonkeyState.ENTRANCE &&
        !pendingShown && !thinkingBubble && !typing && !listenTap && !com.combo.monkeycompanion.agent.Voice.listening &&
        !(bubbleOn && bubbleAge < bubbleLife) && !com.combo.monkeycompanion.agent.ScreenAgent.driving

    /** v4.2 power entrance in flight: shown (with its chips) once the power lands. */
    private var entrancePipe: com.combo.monkeycompanion.agent.Pipe? = null
    private var entranceAt = 0L

    override fun onPipe(pipe: com.combo.monkeycompanion.agent.Pipe) {
        if (!canShowPipe()) return
        val kind = pipe.entrance
        val mode = ChatHub.settings.powerEntrances
        val powersOff = powerSetting() == Superpowers.OFF
        if (kind != null && !powerOn && autoPowerGap <= 0f &&
            (if (pipe.alwaysPower) mode != "off" && !saver && !powersOff else com.combo.monkeycompanion.brain.PowerEntrances.roll(mode, saver, powersOff))) {
            val pw = com.combo.monkeycompanion.brain.PowerEntrances.pick(kind, character.id, character.powers)
            if (pw != null) {
                entrancePipe = pipe; entranceAt = SystemClock.elapsedRealtime(); entranceKind = kind
                if (usePower(explicit = true, force = pw)) return
                entrancePipe = null
            }
        }
        if (v5On && stage.tryMunch(pipe.cat.label, pipe.text.lineSequence().firstOrNull().orEmpty()) { if (canShowPipe()) showPipe(pipe) }) return
        showPipe(pipe)
    }
    private var entranceKind = com.combo.monkeycompanion.brain.PowerEntrances.Kind.FLY

    private fun showPipe(pipe: com.combo.monkeycompanion.agent.Pipe) {
        aiReply = null; pages.clear(); beats.clear(); lastLine = pipe.text
        showBubble(pipe.text, 9f + pipe.text.length * 0.02f, chips = pipe.chips, reply = false)
        if (pipe.excited && controller.state == MonkeyState.IDLE) controller.play(MonkeyState.STARTLED, 0.5f)
        if (!running && live && !paused) start()
    }

    override fun onChips(chips: List<com.combo.monkeycompanion.agent.QuickChip>) {
        val text = lastShown ?: return
        if (chips.isEmpty() || !bubbleOn || pendingShown || thinkingBubble || typing || pages.isNotEmpty() || hiddenTab) return
        val keep = aiReply
        showBubble(text, max(6f, bubbleLife - bubbleAge) + 4f, chips = chips, reply = false)
        aiReply = keep; bubble.reportable = false
    }

    override fun onLocalChip(chip: com.combo.monkeycompanion.agent.QuickChip) = tapChip(chip)

    private fun tapChip(c: com.combo.monkeycompanion.agent.QuickChip) {
        noteInteraction()
        bubbleChips = emptyList(); arcOn = false
        if (bubbleOn) bubbleLife = bubbleAge
        when (c.id) {
            "menu" -> onMenuItem(c.arg)
            "talk" -> startListen(hold = false)
            "quit" -> askQuit()
            "chat" -> openBubbleChat()
            "funny" -> doSomethingFunny()
            "think" -> showThinking()
            "focus" -> startFocusBuddy(25)
            "breathe" -> startBreatheBuddy()
            "campfire" -> startCampfire(manual = true)
            "comfort_hug" -> com.combo.monkeycompanion.mind.MindHub.triggerComfort(forceKind = com.combo.monkeycompanion.heart.ComfortKind.GLASS_KNOCK_HUG)
            "comfort_sign" -> com.combo.monkeycompanion.mind.MindHub.triggerComfort(forceKind = com.combo.monkeycompanion.heart.ComfortKind.SIGN_CARD)
            "comfort_coffee" -> com.combo.monkeycompanion.mind.MindHub.triggerComfort(forceKind = com.combo.monkeycompanion.heart.ComfortKind.OFFER_COFFEE)
            "diary" -> runCatching { context.startActivity(Intent(context, com.combo.monkeycompanion.ui.DiaryActivity::class.java).putExtra("character", character.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            "v5" -> if (!stage.onChip(c.arg)) ChatHub.quick(c)
            "prefill" -> { openBubbleChat(); replyField?.let { it.setText(c.arg); it.setSelection(it.text.length) } }
            "break" -> { say(listOf("Proud of you! 🙌 Go stretch, I'll guard the phone.", "Yay! Eyes on something far away for 20 seconds 👀🌳").random(), 3500L); controller.play(MonkeyState.HAPPY_PAT, 1.8f) }
            "retuck_edge" -> {
                com.combo.monkeycompanion.agent.DailyUtilityEngine.onOverlayPillTapped(context)
                wasTuckedBeforeUtilityAlert = false
                autoReTuckTimer = 0f
                hideToEdge(preAlertHideSide, rest = true)
            }
            "snooze_doomscroll" -> {
                com.combo.monkeycompanion.agent.DailyUtilityEngine.snoozeDoomscrollMinutes(5)
                wasTuckedBeforeUtilityAlert = false
                autoReTuckTimer = 0f
                sayInfo("⏰ Snoozed for 5 minutes! Tucking back beside the screen 👈", 1800L)
                hideToEdge(preAlertHideSide, rest = true)
            }
            "notif_card", "notifs", "miss" -> openNotifSummaryCard(useDemoIfEmpty = false)
            "notif_card_demo" -> openNotifSummaryCard(useDemoIfEmpty = true)
            "perm_notifs" -> runCatching {
                context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                sayInfo("Switch on 'Settai' in Notification Access, then tap me again! 📬", 4800L)
            }
            "perm_mic" -> runCatching {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + context.packageName)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                sayInfo("Tap Permissions → Microphone → Allow, then long-press me → 🎤 Talk!", 5000L)
            }
            else -> ChatHub.quick(c)
        }
    }

    /** "Do something funny": a random gag, a dance or a superpower. */
    private fun doSomethingFunny() {
        val r = Random.nextFloat()
        val ok = when {
            r < 0.5f -> controller.gag(com.combo.monkeycompanion.brain.Gag.entries.random())
            r < 0.75f -> { controller.danceNow(); true }
            else -> usePower(explicit = true)
        }
        if (!ok) { controller.danceNow() }
    }

    /** v4.3: close the options menu (tap outside / on Settai / an item / drag). */
    private fun closeMenu(): Boolean {
        if (!(arcOn && bubbleOn)) { arcOn = false; return false }
        arcOn = false; bubbleChips = emptyList(); bubbleLife = bubbleAge
        return true
    }

    // ---------------- v4.4 long-press menu: main ring, More, Powers (see brain/LongPressMenu) ----------------

    private enum class RingKind { MAIN, MORE, POWERS, GROUP }
    private var ringGroup = ""
    private var ringKind = RingKind.MAIN
    private var ringPage = 0
    private val ringTick = object : Runnable {
        override fun run() {
            if (arcOn && bubbleOn && ringKind == RingKind.POWERS) { showRing(RingKind.POWERS, ringPage, refresh = true); ui.postDelayed(this, 1000L) }
        }
    }

    /** Long-press: the main ring pops up (haptic tick); Settai gestures at it. Stays ~8 s or until a tap. */
    private fun openMenu() {
        if (onbGesture(com.combo.monkeycompanion.brain.Onboarding.Gesture.HOLD)) { ui.postDelayed({ openMenu() }, 1600L); return }
        // v10.13.1: the popup works while a reply is loading (the thinking cloud steps aside; the reply still arrives)
        if (pendingShown || typing) return
        if (thinkingBubble) { thinkingBubble = false; bubbleOn = false }
        if (listenTap) cancelListen(null)
        pages.clear(); beats.clear(); aiReply = null
        showRing(RingKind.MAIN, 0)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        if (controller.state == MonkeyState.IDLE || controller.state == MonkeyState.SIT) controller.play(MonkeyState.WAVE, 1.1f)
    }

    private fun menuLayout(): LongPressMenu.Ring {
        val st = ChatHub.settings
        return LongPressMenu.layout(st.menuConfig, st.menuPins(character.id), character.powers.map { it.id }, ChatHub.contextChips().isNotEmpty())
    }

    /** One ring button: icon, label, greyed. */
    /** v10.13.1: Access items show their live permission state (re-checked every time the popup opens). */
    fun accessGranted(id: String): Boolean? = when (id) {
        "missed" -> com.combo.monkeycompanion.powers.SettaiNotificationListener.isAccessGranted(context)
        "talk" -> context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        "screen" -> runCatching { com.combo.monkeycompanion.agent.SettaiAccessibilityService.enabled(context) }.getOrDefault(false)
        "chat" -> true
        else -> null
    }

    private fun accessLabel(id: String, lb: String): String {
        if (ringKind != RingKind.GROUP || ringGroup != "g_access") return lb
        return when (accessGranted(id)) { true -> "$lb ✅"; false -> "$lb 🔑"; null -> lb }
    }

    private fun ringEntry(id: String): Triple<String, String, Boolean> {
        // v10.9: no power cooldown for the user: powers are always ready (no greyed buttons, no countdown)
        val cool = LongPressMenu.PowerState.Ready
        val cooling = false
        if (LongPressMenu.isPower(id)) {
            val pid = LongPressMenu.powerId(id)
            val pw = Superpowers.byId(pid)
            return Triple(LongPressMenu.powerIcon(pid), LongPressMenu.powerLabel(LongPressMenu.shortName(pid, pw?.title ?: pid), cool), cooling)
        }
        LongPressMenu.groupedEntry(id)?.let { (ic, lb) -> return Triple(ic, accessLabel(id, lb), false) }
        return when (id) {
            LongPressMenu.MORE -> Triple("⋯", "More", false)
            LongPressMenu.BACK -> Triple("↩️", "Back", false)
            LongPressMenu.NEXT -> Triple("➡️", "Next", false)
            LongPressMenu.RANDOM -> Triple("🎲", LongPressMenu.powerLabel("Random", cool), cooling)
            "quit" -> Triple("✕", "✕", false)
            "choose_powers" -> Triple("☑️", "Choose", false)
            "context" -> when (ChatHub.contextChips().firstOrNull()?.id) {
                "worth" -> Triple("🍿", "Worth it?", false)
                "cheaper" -> Triple("🏷️", "Cheaper?", false)
                "how_long" -> Triple("⏱️", "How long?", false)
                "summarise" -> Triple("📝", "Summary", false)
                else -> Triple("💡", "App tip", false)
            }
            else -> LongPressMenu.Item.of(id)?.let { Triple(it.icon, it.label, false) } ?: Triple("❔", id, false)
        }
    }

    /** Show a ring; [refresh] only updates the labels (power countdowns) without a new pop or timeout. */
    private fun showRing(kind: RingKind, page: Int, refresh: Boolean = false) {
        ringKind = kind; ringPage = page
        val ids = when (kind) {
            RingKind.MAIN -> LongPressMenu.groupedMain()
            RingKind.GROUP -> listOf(LongPressMenu.BACK) + LongPressMenu.groupItems(ringGroup)
            RingKind.MORE -> LongPressMenu.page(menuLayout().more, page)
            RingKind.POWERS -> LongPressMenu.page(LongPressMenu.powerRing(com.combo.monkeycompanion.brain.PowerPrefs.enabled(character)).map { LongPressMenu.pin(it) }, page, random = true).let { if (com.combo.monkeycompanion.brain.PowerPrefs.choosable(character)) it + "choose_powers" else it }
        }
        val used = prefs.getStringSet("usedItems", emptySet()) ?: emptySet()
        val entries = ids.map { id -> ringEntry(id).let { e -> if (LongPressMenu.groupedEntry(id) != null && com.combo.monkeycompanion.brain.Onboarding.isNew(id, used)) Triple(e.first, com.combo.monkeycompanion.brain.Onboarding.label(e.second, true), e.third) else e } }
        val firstOpen = kind == RingKind.MAIN && !prefs.getBoolean("menuHint44", false)
        val title = when {
            firstOpen -> "Tap a group · tap outside to close"
            kind == RingKind.GROUP -> LongPressMenu.GROUPS.firstOrNull { it.id == ringGroup }?.let { "${it.icon} ${it.label}" } ?: ""
            kind == RingKind.POWERS -> "${character.name}'s powers" + if (LongPressMenu.pageCount(LongPressMenu.powerRing(com.combo.monkeycompanion.brain.PowerPrefs.enabled(character))) > 1) " (${page + 1}/${LongPressMenu.pageCount(LongPressMenu.powerRing(com.combo.monkeycompanion.brain.PowerPrefs.enabled(character)))})" else ""
            kind == RingKind.MORE -> "More"
            else -> "🧠 What's ${character.name} thinking?"
        }
        if (firstOpen && !refresh) prefs.edit().putBoolean("menuHint44", true).apply()
        bubble.setArc(entries.map { it.first }, entries.map { it.second }, entries.indices.filter { entries[it].third }.toSet(), title)
        bubbleChips = ids.map { com.combo.monkeycompanion.agent.QuickChip("menu", it, it) }
        arcOn = true; lastShown = null
        if (!refresh) {
            bubbleOn = true; bubbleAge = 0f; bubbleLife = Gestures.MENU_MS / 1000f + (if (firstOpen) 3f else 0f); talkFor = 0f
            ui.removeCallbacks(ringTick)
        }
        updateGeometry()
        if (!running && live && !paused) start()
    }

    private fun onMenuItem(id: String) {
        ui.removeCallbacks(ringTick)
        if (id == "onb_skip") { endOnboarding(); return }
        if (id == "think_stop") { ChatHub.cancel(); thinkingBubble = false; sayInfo("Okay, stopped 🤐", 1500L); return }
        run { val u = prefs.getStringSet("usedItems", emptySet()) ?: emptySet(); if (id !in u) prefs.edit().putStringSet("usedItems", u + id).apply() }
        val Q = com.combo.monkeycompanion.agent.QuickChip
        when {
            id == "g_powers" -> showRing(RingKind.POWERS, 0)
            LongPressMenu.isGroup(id) -> { ringGroup = id; showRing(RingKind.GROUP, 0) }
            com.combo.monkeycompanion.brain.CareTools.Tool.of(id) != null -> onToolItem(com.combo.monkeycompanion.brain.CareTools.Tool.of(id)!!)
            id.startsWith("tool_custom:") -> setCustomReminder(id.substringAfter(':').toIntOrNull() ?: 30)
            id == LongPressMenu.MORE -> showRing(RingKind.MORE, 0)
            id == LongPressMenu.BACK -> showRing(RingKind.MAIN, 0)
            id == LongPressMenu.NEXT -> showRing(ringKind, LongPressMenu.nextPage(
                if (ringKind == RingKind.POWERS) LongPressMenu.powerRing(com.combo.monkeycompanion.brain.PowerPrefs.enabled(character)) else menuLayout().more, ringPage))
            id == LongPressMenu.RANDOM -> firePower(null)
            id.startsWith("force:") -> {
                val pw = Superpowers.byId(id.removePrefix("force:")) ?: Superpowers.choose(Superpowers.RANDOM)
                if (pw != null) usePower(explicit = true, force = pw)
            }
            LongPressMenu.isPower(id) -> firePower(Superpowers.byId(LongPressMenu.powerId(id)))
            else -> when (id) {
                "chat" -> openBubbleChat()
                "talk" -> if (com.combo.monkeycompanion.agent.Voice.enabled(context)) startListen(hold = false)
                    else if (accessGranted("talk") == true) {   // v10.13.1: mic already allowed → just switch Voice on, no re-prompt
                        com.combo.monkeycompanion.ai.AppSettings(context).setGroupEnabled(com.combo.monkeycompanion.powers.CapGroup.VOICE, true); startListen(hold = false)
                    } else sayInfo("Switch on the Voice power (Settings › Phone powers) and I'll listen 🎤", 4000L)
                "missed" -> openNotifSummaryCard(useDemoIfEmpty = false)
                "screen" -> ChatHub.quick(Q.SCREEN)
                "powers" -> showRing(RingKind.POWERS, 0)
                "choose_powers" -> runCatching { context.startActivity(Intent(context, com.combo.monkeycompanion.ui.PowerChoiceActivity::class.java).putExtra("id", character.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                "funny" -> doSomethingFunny()
                "skit" -> startSkitNow()
                "show" -> startChaseShow()
                "banana_game" -> stage.startGame(com.combo.monkeycompanion.v5.Games.Game.BANANA)
                "eater" -> startScreenEater()
                "selfie" -> startPhotobomb(manual = true)
                "selfie_snap" -> snapSelfie()
                "comfort_hug" -> com.combo.monkeycompanion.mind.MindHub.triggerComfort(forceKind = com.combo.monkeycompanion.heart.ComfortKind.GLASS_KNOCK_HUG)
                "comfort_sign" -> com.combo.monkeycompanion.mind.MindHub.triggerComfort(forceKind = com.combo.monkeycompanion.heart.ComfortKind.SIGN_CARD)
                "comfort_coffee" -> com.combo.monkeycompanion.mind.MindHub.triggerComfort(forceKind = com.combo.monkeycompanion.heart.ComfortKind.OFFER_COFFEE)
                "games" -> stage.showGames()
                "smash" -> com.combo.monkeycompanion.smash.SmashActivity.launch(context)
                "clip" -> makeClip("")
                "context" -> ChatHub.contextChips().firstOrNull()?.let { tapChip(it) } ?: doSomethingFunny()
                "quit" -> askQuit()
                "settings" -> runCatching { context.startActivity(Intent(context, com.combo.monkeycompanion.ui.SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                "hide" -> hideToEdge(if (mx + boxW / 2f < sw() / 2f) -1 else 1)
                "sleep" -> onAction(BodyAction.SLEEP)
                "dance" -> onAction(BodyAction.DANCE)
                "think" -> showThinking()
                "focus" -> startFocusBuddy(25)
                "breathe" -> startBreatheBuddy()
                "comfort" -> com.combo.monkeycompanion.mind.MindHub.triggerComfort()
                "wardrobe" -> runCatching { context.startActivity(Intent(context, com.combo.monkeycompanion.ui.WardrobeActivity::class.java).putExtra("character", character.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                "switch" -> {
                    val all = com.combo.monkeycompanion.character.CharacterRegistry.all
                    val next = all[(all.indexOf(character) + 1).mod(all.size)]
                    ChatHub.settings.characterId = next.id; ChatHub.settings.applyCharacter()
                }
            }
        }
    }

    /** A power from the menu: greyed while recharging; with battery saver on, warn once, then allow. */
    private fun firePower(pw0: Superpower?) {
        val pw = pw0 ?: Superpowers.choose(Superpowers.RANDOM) ?: character.powers.firstOrNull() ?: return
        if (LongPressMenu.saverWarn(saver, prefs.getBoolean("saverPowerWarned", false))) {
            prefs.edit().putBoolean("saverPowerWarned", true).apply()
            showBubble("🔋 Battery saver is on. Powers use a little extra battery. Do it anyway?", 9f,
                chips = listOf(com.combo.monkeycompanion.agent.QuickChip("menu", "Do it ⚡", "force:" + pw.id), com.combo.monkeycompanion.agent.QuickChip.LATER), reply = false)
            return
        }
        usePower(explicit = true, force = pw)
    }

    // Tap-to-talk (v4.3: from the menu's 🎤; stops on silence, or tap Settai / the bubble to send now)
    private var listenTap = false
    private var perk = 0f
    private var perkTarget = 0f

    /** Listen once; it ends on its own after a pause, or when you tap Settai or the bubble. */
    private fun startListen(@Suppress("UNUSED_PARAMETER") hold: Boolean) {
        val V = com.combo.monkeycompanion.agent.Voice
        endTyping(); pages.clear(); beats.clear(); aiReply = null
        if (!com.combo.monkeycompanion.powers.Perm.MIC.granted(context)) {
            listenTap = false
            showBubble(V.missLine(com.combo.monkeycompanion.agent.Voice.Miss.NO_PERMISSION), 8f, chips = listOf(com.combo.monkeycompanion.agent.QuickChip("perm_mic", "Allow mic"), com.combo.monkeycompanion.agent.QuickChip.LATER), reply = false)
            return
        }
        if (ChatHub.busy) { listenTap = false; sayInfo("Wait wait, I'm still thinking! 🤔", 2500L); return }
        perkTarget = 1f; listenTap = true
        showBubble("🎤 Listening… tap me to send", 30f, talk = false, reply = false)
        var miss: com.combo.monkeycompanion.agent.Voice.Miss? = null
        V.listen(context, onPartial = { p -> if (perkTarget > 0f) showBubble("🎤 “${p.takeLast(120)}”", 30f, talk = false, reply = false) },
            onMiss = { miss = it }) { heard ->
            perkTarget = 0f; listenTap = false
            if (heard != null) {
                aiReply = null
                showBubble("🗣️ “${heard.take(120)}”", 2f, talk = false, reply = false)
                touchConvo()
                ChatHub.send(heard, lastLine, voice = true)
            } else {
                val m = miss ?: com.combo.monkeycompanion.agent.Voice.Miss.NO_MATCH
                if (m == com.combo.monkeycompanion.agent.Voice.Miss.NO_PERMISSION) showBubble(V.missLine(m), 8f, chips = listOf(com.combo.monkeycompanion.agent.QuickChip("perm_mic", "Allow mic")), reply = false)
                else if (m == com.combo.monkeycompanion.agent.Voice.Miss.CANCELLED) { if (bubbleOn) bubbleLife = bubbleAge }
                else showBubble(V.missLine(m), 4.5f, reply = false)
            }
        }
        if (!running && live && !paused) start()
    }

    private fun cancelListen(line: String?) {
        listenTap = false; perkTarget = 0f
        com.combo.monkeycompanion.agent.Voice.cancelListen()
        if (line != null) showBubble(line, 2f, talk = false, reply = false) else if (bubbleOn) bubbleLife = bubbleAge
    }

    // ---------------- In-bubble reply ----------------

    private var replyField: EditText? = null
    private var typing = false
    /** Bubble conversation in progress until this time (seconds of [t]): replies keep the reply field. */
    private var convoUntil = -1f
    private val convoActive get() = t < convoUntil
    private fun touchConvo() { convoUntil = t + CONVO_SECS }
    /** Window raised above the keyboard while typing in the bubble. */
    private var typeLift = 0f
    private val imm get() = context.getSystemService(InputMethodManager::class.java)

    /** Overlay mode: the host gives us a real text box that we lay over the field drawn in the cloud. */
    fun attachReplyField(edit: EditText) {
        replyField = edit
        edit.visibility = GONE
        edit.setTextSize(TypedValue.COMPLEX_UNIT_PX, bubble.fieldTextSize)
        edit.setPadding(bubble.fieldPadX.toInt(), 0, (4 * d).toInt(), 0)
        edit.setOnEditorActionListener { _, _, _ -> sendReply(); true }
        edit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { chatActiveAt = SystemClock.elapsedRealtime() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    /** Long-press 💬: open a bubble chat with an empty reply field, ready to type. */
    fun openBubbleChat() {
        if (replyField == null) { host?.openChat() ?: onChatRequest?.invoke(); return }
        if (hiddenTab) unhide()
        if (pendingShown) return
        thinkingBubble = false
        pages.clear()
        lastLine = null
        touchConvo()
        val coach = !prefs.getBoolean("chatCoach44", false)
        if (coach) prefs.edit().putBoolean("chatCoach44", true).apply()
        showBubble(if (coach) "Chat's open! Close it with ✕, a swipe, or a tap outside 👋"
            else listOf("What's up? \uD83D\uDC12", "Talk to me!", "Yes, boss? \uD83C\uDF4C", "Settai is listening…").random(), 12f, talk = true)
        startTyping()
    }

    private fun startTyping() {
        noteInteraction()
        val e = replyField ?: return
        if (typing) { e.requestFocus(); return }
        typing = true
        bubble.fieldText = ""
        // Keep the field above the keyboard: hop Settai up if the bubble is low on the screen.
        val h = host
        if (h != null) {
            val fieldBottom = winY + bubbleTop + bubble.fieldRect.bottom
            val limit = h.screenHeight() * 0.42f
            if (fieldBottom > limit) typeLift = fieldBottom - limit
        }
        e.visibility = VISIBLE
        positionReplyField()
        h?.setWindowFocusable(true)
        e.requestFocus()
        e.postDelayed({ if (typing) imm?.showSoftInput(e, 0) }, 150)
    }

    /** Drop focus so the rest of the phone is usable again. */
    fun endTyping() {
        val e = replyField ?: return
        if (!typing) return
        typing = false
        bubble.fieldText = null
        imm?.hideSoftInputFromWindow(e.windowToken, 0)
        e.clearFocus()
        e.visibility = GONE
        host?.setWindowFocusable(false)
        typeLift = 0f
        // Fade out a little later rather than vanishing under the finger.
        if (bubbleOn && bubbleAge < bubbleLife) bubbleLife = max(bubbleLife, bubbleAge + 2.5f)
    }

    private fun sendReply() {
        val e = replyField ?: return
        val txt = e.text?.toString()?.trim().orEmpty()
        if (txt.isEmpty()) { endTyping(); return }
        if (ChatHub.busy) return  // still answering; keep what they typed
        val said = lastLine
        e.setText("")
        touchConvo()
        endTyping()
        ChatHub.send(txt, said)
    }

    private fun positionReplyField() {
        val e = replyField ?: return
        if (!typing) return
        val r = bubble.fieldRect
        val lp = e.layoutParams as? FrameLayout.LayoutParams ?: return
        val l = (bubbleLeft + r.left).toInt(); val tp = (bubbleTop + r.top).toInt()
        val w = r.width().toInt(); val hh = r.height().toInt()
        if (lp.leftMargin != l || lp.topMargin != tp || lp.width != w || lp.height != hh) {
            lp.leftMargin = l; lp.topMargin = tp; lp.width = w; lp.height = hh
            lp.gravity = android.view.Gravity.TOP or android.view.Gravity.START
            e.layoutParams = lp
        }
    }

    /** Back or a tap outside the overlay while typing. */
    fun onOutsideOrBack(): Boolean {
        if (notifCardOpen) { closeNotifSummaryCard(); return true }
        if (closeMenu()) return true
        if (typing) {
            // Typing with a draft: only drop the keyboard (never lose / close mid-sentence). Empty: close the chat.
            if (!replyField?.text.isNullOrBlank()) endTyping() else closeChat()
            return true
        }
        if (chatOpen && bubbleOn) { closeChat(); return true }
        return false
    }

    private var quipCheckIn = 0f

    /**
     * Re-read settings now and then (once a minute while animating; they can change in Settings) and
     * the phone temperature from the sticky battery broadcast (no polling while resting: events wake us).
     */
    private fun refreshQuipSettings() {
        val st = ChatHub.settings
        sysSaver = runCatching { (context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isPowerSaveMode }.getOrDefault(false)
        saver = st.batterySaver
        restSecs = st.effectiveRestSeconds
        comeOutOnUnlock = st.comeOutOnUnlock
        controller.liveliness = if (saver) 0.45f else 1f
        controller.gagsOn = st.gags
        controller.configureQuips(if (st.quipsEnabled) st.quipMinutes * 60f * (if (saver) 2f else 1f) else 0f)
        controller.roam = st.roam
        if (smoothAnim != st.smoothAnimation) { smoothAnim = st.smoothAnimation; animEngine.reset(); living.reset() }
        if (livingOn != st.livingReactions) { livingOn = st.livingReactions; reactionLayer.clear() }
        dtPowerSetting = st.doubleTapPower; chatButtonSetting = st.chatButton
        checkHeat()
    }

    /** Sticky battery read: sweat level, and a "too hot" event on crossing ~44 °C. */
    private fun checkHeat() {
        runCatching {
            val bi = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
            val tmp = bi.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
            heat = max(((tmp - 40f) / 5f).coerceIn(0f, 1f), if (thermalHot) 0.8f else 0f)
            if (!hot && tmp >= 44f) {
                hot = true
                if (heatIce()) Unit else importantEvent(Lines.pick(Lines.HEAT))
            }
            else if (hot && tmp < 41f && !thermalHot) hot = false
            val lvl = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val plugged = bi.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            if (host != null && !wp) {
                val now = System.currentTimeMillis()
                if (lvl in 0..100) com.combo.monkeycompanion.mind.MindHub.world.onBattery(lvl, now)
                com.combo.monkeycompanion.mind.MindHub.world.onCharging(plugged, now)
                if (tmp > 5f) com.combo.monkeycompanion.mind.MindHub.world.onTemp(tmp, now)
            }
            if (lvl in 0..15 && !plugged) { if (!batteryLow) { batteryLow = true; onBatteryLow() } }
            else if (lvl > 20 || plugged) batteryLow = false
        }
    }

    /** Ringer / Do-not-disturb -> cloth over the mouth. Read on change (broadcast), not polled. */
    private fun readSilent() {
        runCatching {
            val am = context.getSystemService(AudioManager::class.java)
            val nm = context.getSystemService(NotificationManager::class.java)
            silent = am?.ringerMode == AudioManager.RINGER_MODE_SILENT ||
                (nm != null && nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL)
            if (host != null && !wp) {
                com.combo.monkeycompanion.agent.Proactive.silent = silent
                com.combo.monkeycompanion.mind.MindHub.world.onRinger(silent, false, System.currentTimeMillis())
            }
        }
        if (!running) invalidate()
    }
    private var thermalHot = false

    /** Something worth coming out for (battery low, overheating): resting Settai pops back out to say it. */
    private fun importantEvent(line: String) {
        if (host == null || !live) return
        if (paused) { pendingEventLine = line; return }
        if (hiddenTab && !wp) {
            if (resting) { unhide(); entLine = line }   // a swipe-hide is respected: it stays put
            return
        }
        if (controller.isAsleep) {
            if (restNap) { restNap = false; controller.wakeUp(line); start() }
            return
        }
        sayInfo(line, 4500L); start()
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_BATTERY_LOW -> if (!batteryLow) { batteryLow = true; onBatteryLow() }
                Intent.ACTION_BATTERY_OKAY -> batteryLow = false
                Intent.ACTION_POWER_CONNECTED -> {
                    if (host != null && !wp) com.combo.monkeycompanion.mind.MindHub.world.onCharging(true, System.currentTimeMillis())
                    onChargerPlugged()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    if (host != null && !wp) com.combo.monkeycompanion.mind.MindHub.world.onCharging(false, System.currentTimeMillis())
                    watchFull(false); tryMoment(PhoneMoments.Moment.UNPLUG)
                }
                else -> readSilent()
            }
        }
    }
    private var statusRegistered = false
    private var thermalListener: Any? = null

    private fun registerStatus() {
        if (statusRegistered || host == null) return
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_LOW); addAction(Intent.ACTION_BATTERY_OKAY); addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION); addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(statusReceiver, f, Context.RECEIVER_EXPORTED) else context.registerReceiver(statusReceiver, f)
            statusRegistered = true
        }
        if (Build.VERSION.SDK_INT >= 29) runCatching {
            val pm = context.getSystemService(PowerManager::class.java)
            val l = PowerManager.OnThermalStatusChangedListener { status ->
                val was = thermalHot
                thermalHot = status >= PowerManager.THERMAL_STATUS_MODERATE
                if (thermalHot) heat = max(heat, 0.8f)
                if (thermalHot && !was && !hot) { hot = true; importantEvent(Lines.pick(Lines.HEAT)) }
                if (!thermalHot && was) hot = false
            }
            pm.addThermalStatusListener(l); thermalListener = l
        }
        readSilent()
        registerMomentSources()
    }

    private fun unregisterStatus() {
        unregisterMomentSources()
        if (statusRegistered) { runCatching { context.unregisterReceiver(statusReceiver) }; statusRegistered = false }
        if (Build.VERSION.SDK_INT >= 29) (thermalListener as? PowerManager.OnThermalStatusChangedListener)?.let { l ->
            runCatching { context.getSystemService(PowerManager::class.java).removeThermalStatusListener(l) }
        }
        thermalListener = null
    }

    // ---- resting behind the edge: occasional peeks and (pre-Android 10) a slow heat check ----

    private val peekRunnable = Runnable {
        if (hiddenTab && live && !paused && !wp) {
            tabPeekT = 0f; tabPeekBino = Random.nextFloat() < 0.45f
            start()
        }
        schedulePeek()
    }
    private val heatRunnable = object : Runnable {
        override fun run() { if (hiddenTab && live && !paused) { checkHeat(); ui.postDelayed(this, 180_000L) } }
    }
    private fun schedulePeek() {
        ui.removeCallbacks(peekRunnable)
        if (!hiddenTab || !live || wp) return
        val base = if (saver) 240_000L else 100_000L
        ui.postDelayed(peekRunnable, base + Random.nextLong(base * 3 / 2))
    }
    var wasTuckedBeforeUtilityAlert: Boolean = false; private set
    private var preAlertHideSide: Int = 1
    var autoReTuckTimer: Float = 0f; private set

    private val edgeWatchdogRunnable = object : Runnable {
        override fun run() {
            if (!hiddenTab || !live || paused || wp) return
            val curPkg = com.combo.monkeycompanion.agent.SettaiAccessibilityService.instance?.currentPkg.orEmpty()
            val alert = com.combo.monkeycompanion.agent.DailyUtilityEngine.tickEdgeWatchdog(1.0f, curPkg)
            if (alert != null) {
                wakeFromEdgeTabForUtilityAlert(alert)
                return
            }
            ui.postDelayed(this, 1000L)
        }
    }

    /**
     * v9.5: Wakes Settai up from the `<` side-edge arrow tab when YouTube/Reels screen-time limit (or OTP/Study timer)
     * triggers while Settai is sitting paused beside the screen! Shows `[✓ OK, Tuck Back 👈]` and `[⏰ +5m]`,
     * and automatically tucks back into the edge tab after 20s!
     */
    fun wakeFromEdgeTabForUtilityAlert(alertLine: String, autoReTuckSecs: Float = 20f) {
        if (host == null || wp) return
        val wasHidden = hiddenTab
        if (wasHidden) {
            wasTuckedBeforeUtilityAlert = true
            preAlertHideSide = if (hideSide == 2) 1 else hideSide
            if (!autoUnhide()) { wasTuckedBeforeUtilityAlert = false; return }   // v10.21.1: you hid him, he stays hidden
        }
        autoReTuckTimer = autoReTuckSecs
        entLine = null
        controller.play(MonkeyState.WAVE, 2.2f)
        val chips = listOf(
            com.combo.monkeycompanion.agent.QuickChip("retuck_edge", "✓ OK, Tuck Back 👈"),
            com.combo.monkeycompanion.agent.QuickChip("snooze_doomscroll", "⏰ +5m"),
        )
        showBubble(alertLine, autoReTuckSecs, chips = chips, reply = false)
        updateGeometry(); invalidate()
        if (!running && live && !paused) start()
    }

    /** Test/simulation helper: advances the edge-tucked watchdog by [seconds] while watching [pkg]. */
    fun simulateEdgeWatchdogSeconds(seconds: Float, pkg: String = "com.google.android.youtube"): Boolean {
        val alert = com.combo.monkeycompanion.agent.DailyUtilityEngine.tickEdgeWatchdog(seconds, pkg)
        if (alert != null) {
            wakeFromEdgeTabForUtilityAlert(alert)
            return true
        }
        return false
    }

    private fun scheduleRestTimers() {
        schedulePeek()
        ui.removeCallbacks(heatRunnable)
        ui.removeCallbacks(edgeWatchdogRunnable)
        if (hiddenTab && live && !paused && !wp) {
            ui.postDelayed(edgeWatchdogRunnable, 1000L)
        }
        if (Build.VERSION.SDK_INT < 29 && hiddenTab && live) ui.postDelayed(heatRunnable, 180_000L)
    }
    private fun cancelRestTimers() {
        ui.removeCallbacks(peekRunnable)
        ui.removeCallbacks(heatRunnable)
        ui.removeCallbacks(edgeWatchdogRunnable)
        tabPeekT = -1f
    }

    /** Auto-rest: after [restSecs] with no interaction, say a line, then dash behind the nearest side (or nap, on the wallpaper). */
    private fun stepRest(dt: Float) {
        if (host == null || exiting || quitAsk || powerOn) return
        if (restGoIn >= 0f) {
            restGoIn -= dt
            if (restGoIn < 0f) {
                if (!restAllowed()) { restGoIn = 0f; return }   // busy (dancing, walking...): go as soon as it's free
                restGoIn = -1f
                if (wp) napNow()
                else { val c = mx + boxW / 2f; hideToEdge(if (c < sw() / 2f) -1 else 1, rest = true) }
            }
            return
        }
        restCheckIn -= dt
        if (restCheckIn > 0f) return
        restCheckIn = 1f
        if (restSecs <= 0 || hiddenTab || restNap || controller.isAsleep) return
        val quiet = (SystemClock.elapsedRealtime() - lastInteract) / 1000f
        if (quiet < restSecs || !restAllowed()) return
        say(Lines.pick(if (wp) Lines.SLEEPY else Lines.REST), 2600L)
        restGoIn = 2.2f
        lastRestAt = SystemClock.elapsedRealtime(); BSN.record("I curled up to rest", BSN.Feeling.COZY)   // v10.17
    }

    private fun restAllowed(): Boolean {
        val st = controller.state
        return !dragging && !tracking && !falling && tweenT < 0f && hideAnim < 0f && entKind == EntKind.NONE &&
            !typing && !pendingShown && !thinkingBubble && !ChatHub.busy && !convoActive && ChatHub.pending == null &&
            (st == MonkeyState.IDLE || st == MonkeyState.WALK || st == MonkeyState.SIT)
    }

    /** Wallpaper auto-rest: curl up for a nap where we are (dropping to a floor first if needed). */
    private fun napNow() {
        restNap = true
        if (host != null && !wp) com.combo.monkeycompanion.mind.MindHub.event(com.combo.monkeycompanion.mind.MindEvent.NAP_START)
        if (surf.kind == SK.FLOOR && !climber.active) controller.sleep()
        else afterLandThen { controller.sleep() }
    }

    /** Frame interval: every vsync while dragging/flying, 30 fps when active, 15 fps calm idle (battery saver: 20 / 10). */
    /**
     * Smart Adaptive Frame-Rate Pacing (v9.1):
     * - 0fps (-1L): when screen is off (`paused`) or resting in the hidden edge tab
     * - 60fps (0L): when dragging, flying, falling, dancing, walking, blinking, or scrolling the notification card
     * - 8fps (125L): when asleep (`MonkeyState.SLEEP`)
     * - 24fps (41L): when calmly standing/hovering in `IDLE` with no bubble or active gesture
     */
    fun adaptiveFrameDelayMs(calm: Boolean = false): Long {
        if (!live || !running || paused || (hiddenTab && tabPeekT < 0f && !tabTracking)) return -1L
        if (powerOn) return if (saver) 33L else 0L
        if (fireworks.active || skyActive || reactionLayer.busy || living.anticipating) return 0L   // v10.8: smooth reactions / fireworks
        if (dragging || tracking || falling || impact != null || swipeTrip != null || coyoteScareTimer > 0f || isEdgeCrashLanding || abs(notifFlingVy) > 5f ||
            tweenT >= 0f || hideAnim >= 0f || typing || exiting || notifCardOpen || blinkT >= 0f ||
            controller.state == MonkeyState.WALK || controller.state == MonkeyState.DANCE || controller.state == MonkeyState.WAVE ||
            balls.looseActive
        ) return 0L
        if (controller.isAsleep || controller.state == MonkeyState.SLEEP) return 125L
        val fps = if (calm) (if (saver) 10 else 24) else (if (saver) 20 else 30)
        return 1000L / fps
    }

    /**
     * v10.13 battery: after 15 s of being completely still (calm idle, nothing on screen moving), drop to 15 fps;
     * phone Battery Saver on = 15 fps for any calm frame. Any touch / move / bubble resets to the normal pace.
     */
    private var calmFor = 0f
    private var sysSaver = false
    val debugDeepIdle get() = calmFor >= 15f || sysSaver
    private fun frameIntervalMs(calm: Boolean): Long {
        val base = adaptiveFrameDelayMs(calm).coerceAtLeast(0L)
        if (!calm || base == 0L) return base
        return if (calmFor >= 15f || sysSaver) max(base, 66L) else base
    }

    private fun postNext(wallStartNanos: Long, intervalMs: Long) {
        val ch = Choreographer.getInstance()
        if (intervalMs <= 0L) {
            if (debugQuiet) ch.postFrameCallbackDelayed(this, 16L) else ch.postFrameCallback(this)
            return
        }
        val spentMs = ((System.nanoTime() - wallStartNanos) / 1_000_000L).coerceAtLeast(0L)
        // Aim a little early: the callback runs on the first vsync after the delay.
        val delay = (intervalMs - spentMs - 4L).coerceIn(4L, intervalMs)
        ch.postFrameCallbackDelayed(this, delay)
    }
    private var heat = 0f
    private var silent = false

    private fun requestQuip(): Boolean {
        if (!ChatHub.settings.quipsEnabled || ChatHub.busy || !ChatHub.hasRealBrain()) return false
        val bi = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val tmp = (bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val ctxText = "local time ${java.text.SimpleDateFormat("EEE HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}, " +
            "battery $lvl%, phone temperature ${tmp}°C."
        ChatHub.quip(ctxText) { q ->
            if (q != null && controller.state == MonkeyState.IDLE && !bubbleOn && !hiddenTab) say(q)
        }
        return true
    }

    override val isSpeaking: Boolean get() = (bubbleOn && bubbleAge < talkFor) || com.combo.monkeycompanion.agent.Voice.speaking

    // ---------------- Frame loop ----------------

    private var paused = false
    /** Wallpaper: no window, the host drives attach/detach. */
    private var live = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        goLive()
        if (host != null && !wp) {
            skitActorId = forcedCharacterId ?: character.id
            skitActorId?.let { id -> ChaseShowHub.wakers[id] = { ui.post { if (!running && live && !paused) start() } } }
            skitActorId?.let { id -> ChaseShowHub.where[id] = { showWho() } }
            skitActorId?.let { id -> com.combo.monkeycompanion.engine.skit.SkitBus.register(id) { c -> if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) playSkitCue(c) else ui.post { playSkitCue(c) } } }
        }
    }
    override fun onDetachedFromWindow() {
        skitActorId?.let { com.combo.monkeycompanion.engine.skit.SkitBus.unregister(it); ChaseShowHub.wakers.remove(it); ChaseShowHub.where.remove(it) }; skitActorId = null
        goDead(); super.onDetachedFromWindow()
    }

    // ---- v10.12 skits: this window acts out cues addressed to its character ----
    private var skitActorId: String? = null
    private var skitProp: String? = null
    private var skitPropUntil = 0f
    private val skitPropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private fun playSkitCue(c: com.combo.monkeycompanion.engine.skit.SkitCue) {
        if (dragging || falling || powerOn || hiddenTab || controller.isAsleep) return
        controller.play(c.state, c.durSec)
        val name = com.combo.monkeycompanion.world.Population.displayName(c.actorId)
        say(if (forcedCharacterId != null) "$name: ${c.line}" else c.line, (c.durSec * 1000).toLong().coerceAtLeast(1400L))
        skitProp = c.prop; skitPropUntil = t + c.durSec
        if (!running && live && !paused) start()
        invalidate()
    }

    // ---- v10.13 Billa vs Chittu chase show: this window really moves across the screen ----
    private val showFrame = com.combo.monkeycompanion.engine.show.Frame()
    private var showOn = false
    private var showRef: com.combo.monkeycompanion.engine.show.ChaseShow? = null
    private var showLineIdx = 0
    private var showLastState: MonkeyState? = null
    private var showT = 0f
    private val showFxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val showDustPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val debugShowOn get() = showOn
    val debugShowX get() = mx

    private fun showRole(): com.combo.monkeycompanion.engine.show.Role? = when (character.id) {
        "billa" -> com.combo.monkeycompanion.engine.show.Role.CAT
        "chittu" -> com.combo.monkeycompanion.engine.show.Role.RAT
        else -> null
    }

    fun startChaseShow() {
        if (ChaseShowHub.show != null) return
        com.combo.monkeycompanion.ai.BodySense.record("I ran in the chase show with my friends", com.combo.monkeycompanion.ai.BodySense.Feeling.PUFFED)
        com.combo.monkeycompanion.mind.MindHub.skitRun = null
        ChaseShowHub.start(context, com.combo.monkeycompanion.mind.MindHub.activeCreatures())
        if (!running && live && !paused) start()
    }

    /** true = the show drives this window this frame (physics skipped). */
    private fun stepChaseShow(): Boolean {
        val sh = ChaseShowHub.show
        val role = showRole()
        if (sh == null || role == null || hiddenTab || wp) { if (showOn) endShowLocal(); return false }
        if (dragging) { ChaseShowHub.finish(context); endShowLocal(); return false }
        if (showRef !== sh) { showRef = sh; showLineIdx = 0; showLastState = null }
        val t = ChaseShowHub.t()
        showT = t
        if (ChaseShowHub.done(t)) { ChaseShowHub.finish(context); endShowLocal(); return false }
        // v10.13.1: the chase plays on one full-screen stage window; this window stays still and draws nothing
        // (the old per-frame window moves + state restarts were what made Billa shake).
        if (!showOn) { showOn = true; if (!ShowStageView.open(context, effectiveRigScale())) { ChaseShowHub.finish(context); showOn = false; return false } }
        vy = 0f
        return true
    }

    private fun endShowLocal() {
        showOn = false; showRef = null; showLastState = null
        if (host != null) {
            climber.deactivate(); surf = screenSurf(SK.FLOOR)
            // v10.22: stand exactly where the drama left this character
            ChaseShowHub.finalOf(character.id)?.let { (fx, face) -> mx = fx * sw() - boxW / 2f; controller.face(face) }
            mx = mx.coerceIn(0f, max(0f, sw() - boxW)); my = ground(); falling = false; controller.play(MonkeyState.IDLE, 0.6f); updateGeometry()
        }
    }

    /** v10.22 where this real character is right now, so the drama starts from here (floor, wall, top, hiding). */
    private fun showWho(): ChaseShowHub.Who? {
        val h = host ?: return null
        if (wp) return null
        val w = sw().coerceAtLeast(1f)
        val floorLine = realGround() + boxH - groundPad
        val sc = effectiveRigScale()
        if (hiddenTab) return ChaseShowHub.Who(com.combo.monkeycompanion.engine.show.ActorStart(com.combo.monkeycompanion.engine.show.ActorStart.OFF, if (hideSide < 0) 0f else 1f, 0f, 1f), sc, floorLine)
        val fp = feetScreen(FloatArray(2))
        val edge = when (surf.kind) {
            SK.WALL_L -> com.combo.monkeycompanion.engine.show.ActorStart.WALL_L
            SK.WALL_R -> com.combo.monkeycompanion.engine.show.ActorStart.WALL_R
            SK.CEIL -> com.combo.monkeycompanion.engine.show.ActorStart.TOP
            else -> com.combo.monkeycompanion.engine.show.ActorStart.FLOOR
        }
        val lift = ((floorLine - fp[1]) / w).coerceAtLeast(0f)
        if (h.screenWidth() <= 0) return null
        return ChaseShowHub.Who(com.combo.monkeycompanion.engine.show.ActorStart(edge, (fp[0] / w).coerceIn(0f, 1f), lift, controller.facing), sc, floorLine)
    }

    private fun drawShowFx(canvas: Canvas) {
        val sh = showRef ?: return
        val role = showRole() ?: return
        val t = showT
        val cx = ox + boxW / 2f; val feet = oy + boxH - groundPad; val head = oy + boxH * 0.18f
        for (f in sh.fx) {
            if (f.role != role || t < f.at || t > f.at + f.dur) continue
            val k = ((t - f.at) / f.dur).coerceIn(0f, 1f)
            val fx = if (f.x.isNaN()) cx else f.x * (sw() - boxW) + boxW / 2f - winX
            when (f.kind) {
                com.combo.monkeycompanion.engine.show.FxKind.DUST -> {
                    showDustPaint.color = Color.argb((170 * (1f - k)).toInt(), 200, 180, 150)
                    for (i in 0..4) { val a = i * 1.3f; canvas.drawCircle(fx + kotlin.math.cos(a) * boxW * (0.12f + 0.25f * k), feet - boxW * 0.05f * (1 + i % 2) * (1 + k), boxW * (0.05f + 0.06f * k), showDustPaint) }
                }
                com.combo.monkeycompanion.engine.show.FxKind.BANG -> {
                    val s = 1f + 0.4f * kotlin.math.sin(k * 3.14f)
                    showFxPaint.textSize = boxW * 0.2f * s; showFxPaint.color = Color.rgb(230, 40, 30); showFxPaint.isFakeBoldText = true
                    canvas.drawText(f.text, cx + boxW * 0.28f, head - boxW * 0.05f * k, showFxPaint)
                }
                com.combo.monkeycompanion.engine.show.FxKind.STARS -> {
                    showFxPaint.textSize = boxW * 0.12f; showFxPaint.color = Color.BLACK
                    for (i in 0..2) { val a = t * 6f + i * 2.09f; canvas.drawText(if (i == 1) "🐦" else "⭐", cx + kotlin.math.cos(a) * boxW * 0.25f, head + kotlin.math.sin(a) * boxW * 0.07f, showFxPaint) }
                }
                com.combo.monkeycompanion.engine.show.FxKind.HOLE -> {
                    showDustPaint.color = Color.argb(235, 25, 18, 12)
                    canvas.drawOval(fx - boxW * 0.28f, feet - boxW * 0.07f, fx + boxW * 0.28f, feet + boxW * 0.05f, showDustPaint)
                }
                com.combo.monkeycompanion.engine.show.FxKind.PROP -> {
                    showFxPaint.textSize = boxW * 0.2f; showFxPaint.color = Color.BLACK
                    val px = if (f.x.isNaN()) cx + boxW * 0.3f * showFrame.facing else fx
                    canvas.drawText(f.text, px, if (f.x.isNaN()) oy + boxH * 0.6f else feet, showFxPaint)
                }
                com.combo.monkeycompanion.engine.show.FxKind.HEARTS -> {
                    showFxPaint.textSize = boxW * 0.13f; showFxPaint.color = Color.RED
                    for (i in 0..2) canvas.drawText("❤️", cx + (i - 1) * boxW * 0.2f, head - boxW * 0.25f * k - i * 6f, showFxPaint)
                }
                com.combo.monkeycompanion.engine.show.FxKind.STEAM -> {
                    showDustPaint.color = Color.argb((180 * (1f - k)).toInt(), 240, 240, 240)
                    for (i in 0..2) canvas.drawCircle(cx + (i - 1) * boxW * 0.12f, head - boxW * 0.3f * k - i * 4f, boxW * 0.05f * (1 + k), showDustPaint)
                }
            }
        }
    }

    // ---- v10.13 ⏰ Tools (water, tablet, eye break, neck, custom reminder) ----
    private val toolTick = object : Runnable {
        override fun run() {
            if (!live) return
            val due = runCatching { com.combo.monkeycompanion.agent.ToolStore.collectDue(context) }.getOrDefault(emptyList())
            for ((i, line) in due.withIndex()) ui.postDelayed({
                sayInfo(line, 6000L); if (!running && live && !paused) start()
            }, i * 6500L)
            maybeDailyTip()
            ui.postDelayed(this, 30_000L)
        }
    }

    // ---- v10.13 first-time demo + 7 daily tips ----
    private var onb: com.combo.monkeycompanion.brain.Onboarding.Run? = null
    val debugOnboardingStep get() = onb?.index ?: -1
    private val onbSkip get() = com.combo.monkeycompanion.agent.QuickChip("menu", "Skip ✕", "onb_skip")
    private val onbTick = object : Runnable {
        override fun run() {
            val r = onb ?: return
            r.step(1f)
            if (r.done) { endOnboarding(); return }
            ui.postDelayed(this, 1000L)
        }
    }

    fun startOnboarding(force: Boolean) {
        if (host == null || wp || forcedCharacterId != null) return
        if (!force && prefs.getBoolean("onbDone", false)) return
        prefs.edit().putBoolean("onbDone", true).apply()   // runs once, even if skipped or interrupted
        if (prefs.getLong("installDay", -1L) < 0) prefs.edit().putLong("installDay", java.time.LocalDate.now().toEpochDay()).apply()
        onb = com.combo.monkeycompanion.brain.Onboarding.Run()
        showOnbStep()
        ui.removeCallbacks(onbTick); ui.postDelayed(onbTick, 1000L)
    }

    private fun showOnbStep() {
        val st = onb?.current ?: return
        showBubble(st.line, 30f, reply = false, chips = listOf(onbSkip))
        if (!running && live && !paused) start()
    }

    /** true = the gesture was part of the demo (it advanced). */
    private fun onbGesture(g: com.combo.monkeycompanion.brain.Onboarding.Gesture): Boolean {
        val r = onb ?: return false
        val praise = r.onGesture(g) ?: return false
        if (r.done) { onb = null; ui.removeCallbacks(onbTick); ui.postDelayed({ sayInfo("$praise\nYou're ready! Have fun 🙌", 3500L) }, 350L) }
        else ui.postDelayed({ sayInfo(praise, 1500L); ui.postDelayed({ showOnbStep() }, 1600L) }, 350L)
        return g == com.combo.monkeycompanion.brain.Onboarding.Gesture.HOLD && r.done
    }

    private fun endOnboarding() {
        if (onb == null) return
        onb = null; ui.removeCallbacks(onbTick)
        sayInfo("Okay! Hold me any time to see everything 😉", 2500L)
    }

    private fun maybeDailyTip() {
        if (host == null || wp || forcedCharacterId != null || onb != null) return
        val inst = prefs.getLong("installDay", -1L).takeIf { it >= 0 } ?: return
        val today = java.time.LocalDate.now().toEpochDay()
        val sit = runCatching { com.combo.monkeycompanion.agent.Proactive.situation() }.getOrNull()
        val st = ChatHub.settings
        val t = java.time.LocalTime.now(); val mod = t.hour * 60 + t.minute
        val quiet = runCatching { com.combo.monkeycompanion.agent.QuietHours.inside(mod, st.quietStart, st.quietEnd) }.getOrDefault(false)
        val blocked = quiet || sit?.typing == true || sit?.fullscreen == true || sit?.inCall == true || hiddenTab || bubbleOn || mod < 9 * 60
        val tip = com.combo.monkeycompanion.brain.Onboarding.tipFor(today - inst, prefs.getLong("lastTipDay", -1L) - inst, blocked) ?: return
        prefs.edit().putLong("lastTipDay", today).apply()
        sayInfo("💡 $tip", 6000L)
    }

    private fun onToolItem(t: com.combo.monkeycompanion.brain.CareTools.Tool) {
        val T = com.combo.monkeycompanion.brain.CareTools
        val S = com.combo.monkeycompanion.agent.ToolStore
        if (t == com.combo.monkeycompanion.brain.CareTools.Tool.CUSTOM) {
            val st = S.get(context, t)
            showBubble("📝 Remind you" + (if (st.text.isNotBlank()) " “${st.text}”" else "") + " in…", 9f, reply = false,
                chips = listOf(15, 30, 60).map { com.combo.monkeycompanion.agent.QuickChip("menu", if (it < 60) "$it min" else "1 h", "tool_custom:$it") } + com.combo.monkeycompanion.agent.QuickChip.LATER)
            return
        }
        val now = System.currentTimeMillis()
        val st = T.toggle(t, S.get(context, t), !S.get(context, t).on, now)
        S.put(context, t, st)
        sayInfo(if (st.on) "${t.icon} ${t.label} reminder ON · ${T.describe(t, st)}" else "${t.icon} ${t.label} reminder OFF", 3500L)
    }

    private fun setCustomReminder(mins: Int) {
        val T = com.combo.monkeycompanion.brain.CareTools
        val S = com.combo.monkeycompanion.agent.ToolStore
        val st = T.toggle(com.combo.monkeycompanion.brain.CareTools.Tool.CUSTOM, S.get(context, com.combo.monkeycompanion.brain.CareTools.Tool.CUSTOM).copy(value = mins), true, System.currentTimeMillis())
        S.put(context, com.combo.monkeycompanion.brain.CareTools.Tool.CUSTOM, st)
        sayInfo("⏰ Okay! I'll remind you in ${if (mins < 60) "$mins min" else "${mins / 60} h"} 👍", 3500L)
    }

    // ---- v10.13 🎮 Screen Eater: bites a cartoon hole in the screen; tap the hole to fix it ----
    private var biteView: BiteHoleView? = null
    val debugBiteCount get() = biteView?.biteCount ?: 0

    fun startScreenEater() {
        val h = host ?: return
        if (wp) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager ?: return
        removeBiteHole()
        val size = (boxW * 1.3f).toInt().coerceAtLeast((120 * d).toInt())
        val left = mx + boxW / 2f < sw() / 2f
        val x = (if (left) mx + boxW * 0.75f else mx - size + boxW * 0.25f).toInt().coerceIn(0, max(0, h.screenWidth() - size))
        val y = (my + boxH * 0.1f - size * 0.3f).toInt().coerceIn(0, max(0, h.screenHeight() - size))
        val v = BiteHoleView(context, System.nanoTime()) { removeBiteHole(); say("Sari sari, screen fixed! 😇🔧", 2500L) }
        val lp = android.view.WindowManager.LayoutParams(size, size, x, y,
            if (android.os.Build.VERSION.SDK_INT >= 26) android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") android.view.WindowManager.LayoutParams.TYPE_PHONE,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT).apply { gravity = android.view.Gravity.TOP or android.view.Gravity.START; title = "SettaiBite" }
        if (runCatching { wm.addView(v, lp) }.isFailure) { sayInfo("Can't bite the screen right now 😅", 2500L); return }
        biteView = v
        controller.play(MonkeyState.GAG, 2.4f)
        say("Nom nom nom! Screen romba crunchy 😋", 2600L)
        for (i in 0..2) ui.postDelayed({ biteView?.addBite(); performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }, 300L + i * 550L)
        ui.postDelayed({ if (biteView != null) sayInfo("Tap the hole to fix it 🙈", 2500L) }, 2400L)
    }

    private fun removeBiteHole() {
        val v = biteView ?: return
        biteView = null
        runCatching { (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).removeView(v) }
    }

    // ---- v10.13 🤳 Selfie Photobomb: peeks in when a camera app opens ----
    private var lastBombAt = 0L
    private fun isCameraPkg(pkg: String) = pkg.contains("camera", true) || pkg.contains(".cam", true) || pkg == "com.motorola.camera3" || pkg.contains("snapcam", true)

    private fun onForegroundApp(pkg: String) {
        if (!isCameraPkg(pkg) || !ChatHub.settings.photobomb) return
        val now = System.currentTimeMillis()
        if (now - lastBombAt < 5 * 60_000L) return
        startPhotobomb(manual = false)
    }

    fun startPhotobomb(manual: Boolean) {
        if (host == null || wp || dragging || powerOn) return
        lastBombAt = System.currentTimeMillis()
        val left = mx + boxW / 2f < sw() / 2f
        mx = if (left) -boxW * 0.18f else sw() - boxW * 0.82f
        updateGeometry()
        controller.play(MonkeyState.PEEK, 1.4f)
        ui.postDelayed({
            controller.play(listOf(MonkeyState.WAVE, MonkeyState.DANCE, MonkeyState.PLAYFUL).random(kotlin.random.Random(System.nanoTime())), 4f)
            showBubble(if (manual) "🤳 Photobomb pose! Snap the screen with me?" else "🤳 Camera-ah? Naanum photo-la varen! 😎", 9f, reply = false,
                chips = listOf(com.combo.monkeycompanion.agent.QuickChip("menu", "📸 Snap with me", "selfie_snap"), com.combo.monkeycompanion.agent.QuickChip.LATER))
        }, 1400L)
        if (!running && live && !paused) start()
    }

    private fun snapSelfie() {
        bubbleOn = false; updateGeometry(); invalidate()
        controller.play(MonkeyState.HAPPY_PAT, 3f)
        runCatching { context.startActivity(Intent(context, com.combo.monkeycompanion.ui.SelfieShotActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { sayInfo("Screen capture isn't available here 😅", 2500L) }
    }

    fun startSkitNow() {
        val why = com.combo.monkeycompanion.mind.MindHub.trySkit(force = true)
        if (why != null) sayInfo("No skit right now: $why 🎬", 3200L)
    }

    private fun drawSkitProp(canvas: Canvas) {
        val pr = skitProp ?: return
        if (t > skitPropUntil || hiddenTab) { skitProp = null; return }
        skitPropPaint.textSize = boxW * 0.22f
        val bob = sin(t * 6f) * boxW * 0.02f
        canvas.drawText(pr, ox + boxW * 0.82f, oy + boxH * 0.62f + bob, skitPropPaint)
    }

    /** Start listening and animating (called on attach, or by the wallpaper engine). */
    fun goLive() {
        if (live) return
        live = true
        ChatHub.addListener(this)
        if (host != null && !wp) com.combo.monkeycompanion.agent.Proactive.canShow = { canShowPipe() }
        com.combo.monkeycompanion.agent.ScreenAgent.addListener(drivingListener)
        ChatHub.bodyState = { bodyState() }
        controller.quipRequester = { requestQuip() }
        refreshQuipSettings()
        registerStatus()
        if (hiddenTab) scheduleRestTimers()
        if (v5On) { stage.start(); stage.setVisible(!paused && !hiddenTab); ChatHub.localIntent = { t -> v5Chat(t) } }
        com.combo.monkeycompanion.agent.DailyUtilityEngine.init(context)
        // v10.9: timers live in the persistent scheduler (wall clock + alarm), not in this view
        runCatching { com.combo.monkeycompanion.mind.MindHub.init(context); com.combo.monkeycompanion.agent.ActivityScheduler.attach(context) }
        if (host != null && !wp && forcedCharacterId == null &&
            runCatching { com.combo.monkeycompanion.agent.ActivityScheduler.consumeFocusDoneUnseen(context) }.getOrDefault(false))
            ui.postDelayed({ celebrateFocusDone() }, 2500L)
        com.combo.monkeycompanion.agent.DailyUtilityEngine.onUtilityBadgeChanged = {
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) invalidate() else ui.post { invalidate() }
        }
        if (host != null && !wp) {
            com.combo.monkeycompanion.fun5.Stage.onImeChanged = { if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) syncImeDodge() else ui.post { syncImeDodge() } }
            com.combo.monkeycompanion.mind.MindHub.init(context)
            com.combo.monkeycompanion.mind.MindHub.actor = { d -> executeMind(d) }
            com.combo.monkeycompanion.mind.MindHub.banterActor = { b -> startNeighbourBanter(b) }
            com.combo.monkeycompanion.mind.MindHub.onAppChanged = { pkg -> ui.post { onForegroundApp(pkg) } }
            com.combo.monkeycompanion.mind.MindHub.showActor = { ui.post { startChaseShow() } }
            ui.removeCallbacks(toolTick); ui.postDelayed(toolTick, 20_000L)
            if (!prefs.getBoolean("onbDone", false) && android.os.Build.FINGERPRINT != "robolectric") ui.postDelayed({ startOnboarding(force = false) }, 3500L)
            com.combo.monkeycompanion.mind.MindHub.onMorningTrace = { tr ->
                ui.postDelayed({ if (!paused && !hiddenTab && !dragging) say("🌙 ${tr.line}", 5000L) }, 3600L)
            }
            com.combo.monkeycompanion.mind.MindHub.onComfortRequested = { kind, _, sign, line ->
                ui.post { playComfort(kind, sign, line) }
            }
            com.combo.monkeycompanion.mind.MindHub.onCheckInDue = { _, line ->
                ui.postDelayed({
                    if (!paused && !hiddenTab && !dragging) {
                        val Q = com.combo.monkeycompanion.agent.QuickChip
                        showBubble(line, 11f, chips = listOf(
                            Q.say("Went great! 🎉", "It went really well!"),
                            Q.say("Tough day 🥺", "It was pretty tough honestly"),
                            Q.HUG
                        ), reply = false)
                    }
                }, 4200L)
            }
            controller.mindGate = { pr -> com.combo.monkeycompanion.mind.MindHub.permit(pr) }
            com.combo.monkeycompanion.mind.MindHub.setVisible(!paused && !hiddenTab)
            scheduleMindBeat()
        }
        if (!paused) start()
    }

    // ---------------- v6 Settai mind: coarse heartbeat (only while visible) + autonomous actions ----------------
    private val mindBeatRunnable = Runnable { runMindBeat() }

    private fun scheduleMindBeat() {
        ui.removeCallbacks(mindBeatRunnable)
        if (host == null || wp || !live || paused) return
        val m = com.combo.monkeycompanion.mind.MindHub.mind() ?: return
        if (hiddenTab) {
            // Zero heartbeats while hidden, except a single wake-up when its "banana break" ends so it can come back.
            if (m.away) {
                val wait = (m.awayUntil - System.currentTimeMillis()).coerceIn(30_000L, 30 * 60_000L)
                ui.postDelayed(mindBeatRunnable, wait)
            }
            return
        }
        ui.postDelayed(mindBeatRunnable, m.heartbeatMs())
    }

    private fun runMindBeat() {
        if (host == null || wp || !live || paused) return
        val m = com.combo.monkeycompanion.mind.MindHub.mind() ?: return
        if (hiddenTab && !m.away) return
        com.combo.monkeycompanion.mind.MindHub.heartbeat()
        scheduleMindBeat()
    }

    /** "What's Settai thinking?" — shows the creature's current mood & top thoughts in character. */
    fun showThinking() {
        val line = com.combo.monkeycompanion.mind.MindHub.thinking()
        controller.play(MonkeyState.THINKING, 2.4f)
        showBubble("💭 $line", 8f, chips = listOf(com.combo.monkeycompanion.agent.QuickChip.DIARY, com.combo.monkeycompanion.agent.QuickChip.OK), reply = false)
    }

    /** Walk / float over to a new app, sniff around and peek curiously. */
    fun exploreApp(appName: String, customLine: String? = null) {
        val targetX = if (mx + boxW / 2f < sw() / 2f) sw() * 0.72f else sw() * 0.28f
        val line = customLine ?: "Ooh, $appName! *sniff sniff* 👃👀"
        if (hovers) {
            flyTo(targetX.coerceIn(boxW / 2f + 10 * d, sw() - boxW / 2f - 10 * d),
                (my + boxH / 2f).coerceIn(hoverMinY() + boxH / 2f, ground() + boxH / 2f - 60 * d), 1.25f, "exploring $appName")
            ui.postDelayed({ if (!paused && !hiddenTab && !dragging) { controller.play(MonkeyState.PEEK, 2.2f); say(line, 3400L) } }, 1800L)
        } else {
            val dest = targetX.coerceIn(min(body.minCenterX(), body.maxCenterX()), max(body.minCenterX(), body.maxCenterX()))
            controller.walkTo(dest, 1.05f) {
                if (!paused && !hiddenTab && !dragging) {
                    controller.play(MonkeyState.PEEK, 2.2f)
                    say(line, 3400L)
                }
            }
        }
    }

    /** Execute a decision chosen by [com.combo.monkeycompanion.mind.MindHub]. Returns true when performed. */
    private fun executeMind(dec: com.combo.monkeycompanion.mind.Decision): Boolean {
        if (dec.act == com.combo.monkeycompanion.mind.Act.COME_BACK) {
            if (!hiddenTab || paused || host == null || wp) return false
            if (!autoUnhide()) return false
            entLine = dec.line ?: com.combo.monkeycompanion.mind.MindLines.back(character.id)
            return true
        }
        if (!canShowPipe() || !restAllowed()) return false
        return when (dec.act) {
            com.combo.monkeycompanion.mind.Act.GLANCE -> {
                controller.face(if (mx + boxW / 2f < sw() / 2f) 1f else -1f)
                controller.play(MonkeyState.PEEK, 1.8f)
                true
            }
            com.combo.monkeycompanion.mind.Act.EXPLORE -> { exploreApp(dec.scene?.app ?: "this app", dec.line); true }
            com.combo.monkeycompanion.mind.Act.COMMENT -> {
                val fallback = dec.line ?: "Ooh, ${dec.scene?.app ?: "interesting"}!"
                if (dec.wantsLlm && ChatHub.hasRealBrain() && ChatHub.settings.quipsEnabled) {
                    val ctxText = "User opened ${dec.scene?.app} (${dec.scene?.cat?.label}). ${com.combo.monkeycompanion.mind.MindHub.promptNote()}"
                    ChatHub.quip(ctxText) { q -> if (!paused && !hiddenTab && !dragging) say(q ?: fallback, 3800L) }
                } else say(fallback, 3800L)
                true
            }
            com.combo.monkeycompanion.mind.Act.NAP -> {
                val corner = com.combo.monkeycompanion.mind.MindHub.mind()?.personality?.fav?.napSpot
                val targetX = when (corner) {
                    com.combo.monkeycompanion.mind.Corner.TOP_LEFT, com.combo.monkeycompanion.mind.Corner.BOTTOM_LEFT, com.combo.monkeycompanion.mind.Corner.LEFT_EDGE -> body.minCenterX() + 12 * d
                    else -> body.maxCenterX() - 12 * d
                }
                if (!hovers && surf.kind == SK.FLOOR) controller.walkTo(targetX.coerceIn(min(body.minCenterX(), body.maxCenterX()), max(body.minCenterX(), body.maxCenterX())), 0.9f) { napNow() }
                else napNow()
                true
            }
            com.combo.monkeycompanion.mind.Act.GAG -> {
                val ws = com.combo.monkeycompanion.mind.MindHub.world.state
                val g = if (ws.flooded) com.combo.monkeycompanion.brain.Gag.JUGGLE else com.combo.monkeycompanion.brain.Gag.entries.random()
                controller.gag(g, talk = true)
            }
            com.combo.monkeycompanion.mind.Act.POWER -> {
                if (powerSetting() == Superpowers.OFF || autoPowerGap > 0f || saver) false
                else {
                    val ws = com.combo.monkeycompanion.mind.MindHub.world.state
                    val worldPw = when {
                        ws.hot -> character.powers.firstOrNull { it.id == "ice" || it.id == "breath" }
                        ws.flooded -> character.powers.firstOrNull { it.id == "shield" || it.id == "swoop" || it.id == "ninja" }
                        ws.wellFed -> character.powers.firstOrNull { it.id == "jets" || it.id == "rocket" || it.id == "blur" }
                        else -> null
                    }
                    val fav = com.combo.monkeycompanion.mind.MindHub.mind()?.personality?.fav?.power.orEmpty()
                    val pw = worldPw ?: character.powers.firstOrNull { it.title == fav || it.id == fav }
                    usePower(explicit = false, force = if (pw != null && Random.nextFloat() < 0.7f) pw else null)
                }
            }
            com.combo.monkeycompanion.mind.Act.LEAVE -> {
                say(dec.line ?: com.combo.monkeycompanion.mind.MindLines.leave(character.id), 2400L)
                restGoIn = 2.0f
                true
            }
            com.combo.monkeycompanion.mind.Act.HIDE -> {
                say(dec.line ?: com.combo.monkeycompanion.mind.MindLines.respectHide(character.id), 2000L)
                restGoIn = 1.5f
                true
            }
            com.combo.monkeycompanion.mind.Act.SEEK_ATTENTION -> {
                controller.play(MonkeyState.WAVE, 2.0f)
                say(dec.line ?: "Hey! 👋", 3500L)
                true
            }
            com.combo.monkeycompanion.mind.Act.GROOM -> { controller.play(MonkeyState.SIT, 3.0f); true }
            com.combo.monkeycompanion.mind.Act.WANDER -> {
                if (hovers) { controller.play(MonkeyState.FALLING); patrolFlight() }
                else controller.walkTo(body.minCenterX() + Random.nextFloat() * (body.maxCenterX() - body.minCenterX()), 1f)
                true
            }
            com.combo.monkeycompanion.mind.Act.HABIT -> when (dec.habit) {
                com.combo.monkeycompanion.mind.Habit.MORNING_STRETCH -> { controller.play(MonkeyState.WAKE, 2.6f); dec.line?.let { say(it, 3600L) }; true }
                com.combo.monkeycompanion.mind.Habit.AFTER_LUNCH_NAP -> { dec.line?.let { say(it, 2600L) }; ui.postDelayed({ if (!paused && !dragging) napNow() }, 2200L); true }
                com.combo.monkeycompanion.mind.Habit.EVENING_SNACK -> { controller.play(MonkeyState.HAPPY_PAT, 2.2f); dec.line?.let { say(it, 3400L) }; true }
                null -> false
            }
            com.combo.monkeycompanion.mind.Act.RECALL -> dec.line?.let { say(it, 4200L); true } ?: false
            com.combo.monkeycompanion.mind.Act.IGNORE, com.combo.monkeycompanion.mind.Act.COME_BACK -> false
        }
    }

    /** v5 chat intents only the overlay can do: "let's play", "make a clip". */
    private fun v5Chat(text: String): Boolean {
        val tl = text.lowercase()
        if ("what did i miss" in tl || "what i missed" in tl || "notification summary" in tl || "missed notifications" in tl) {
            openNotifSummaryCard(useDemoIfEmpty = false)
            return true
        }
        com.combo.monkeycompanion.heart.FocusBuddy.parseMinutes(text)?.let { mins -> startFocusBuddy(mins); return true }
        if (com.combo.monkeycompanion.heart.BreatheBuddy.wantsBreathe(text)) { startBreatheBuddy(); return true }
        if (com.combo.monkeycompanion.heart.CampfireScene.wantsCampfire(text)) { startCampfire(manual = true); return true }
        val G = com.combo.monkeycompanion.v5.GameChat
        if (G.wantsGame(text)) { G.which(text)?.let { stage.startGame(it) } ?: stage.showGames(); return true }
        if (com.combo.monkeycompanion.v5.ClipScript.wants(text)) { makeClip(text); return true }
        com.combo.monkeycompanion.v5.Rps.parse(text)?.let { if (text.trim().length <= 12) { stage.playRps(it); return true } }
        return false
    }

    fun goDead() {
        if (!live) return
        live = false
        ui.removeCallbacksAndMessages(null)
        ChatHub.removeListener(this)
        com.combo.monkeycompanion.agent.ScreenAgent.removeListener(drivingListener)
        if (v5On) { stage.stop(); ChatHub.localIntent = null }
        if (host != null && !wp) {
            com.combo.monkeycompanion.fun5.Stage.onImeChanged = null
            ui.removeCallbacks(mindBeatRunnable)
            com.combo.monkeycompanion.mind.MindHub.actor = null
            com.combo.monkeycompanion.mind.MindHub.banterActor = null
            com.combo.monkeycompanion.mind.MindHub.onAppChanged = null
            com.combo.monkeycompanion.mind.MindHub.showActor = null
            ui.removeCallbacks(toolTick); removeBiteHole()
            com.combo.monkeycompanion.mind.MindHub.onMorningTrace = null
            com.combo.monkeycompanion.mind.MindHub.onComfortRequested = null
            com.combo.monkeycompanion.mind.MindHub.onCheckInDue = null
            com.combo.monkeycompanion.mind.MindHub.flush()
        }
        unregisterStatus()
        stop()
        EnergyMeter.mode = "not running"
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (wp) return
        if (visibility == VISIBLE && !paused && isAttachedToWindow) start() else if (visibility != VISIBLE) stop()
    }

    /** Freeze the frame loop (screen off / wallpaper hidden) to save battery. */
    fun setPaused(p: Boolean) {
        paused = p
        if (v5On) { stage.setVisible(!p && !hiddenTab); if (p) stage.screenOff() }
        if (host != null && !wp) {
            com.combo.monkeycompanion.mind.MindHub.setVisible(!p && !hiddenTab)
            scheduleMindBeat()
        }
        if (p) { stop(); EnergyMeter.mode = "paused (screen off / not visible): 0 fps" }
        else if (live) { start(); if (hiddenTab) scheduleRestTimers() }
    }

    private fun start() {
        if (running) return
        running = true; lastNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    /** Wallpaper: climbing spots were edited. */
    fun spotsChanged() {
        spots = host?.spots() ?: emptyList()
        if (surf.spot >= 0 && surf.spot >= spots.size) { surf = screenSurf(SK.FLOOR); climber.deactivate(); clampToSurf() }
        refreshSurf()
    }

    /** True when Settai's body should be rendered in the BehindWidgetsLayer so the real widget occludes its torso. */
    val isBehindWidget: Boolean get() =
        (wp || ln) && surf.spot in spots.indices && surf.kind == SK.FLOOR &&
            (behindHoldT > 0f || entKind == EntKind.DUCK || (entKind == EntKind.UNHIDE && controller.stateTime < Ent.PULL) || controller.state == MonkeyState.HIDDEN)

    private var behindHoldT = 0f
    private val gripPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Draw Settai's body in the BehindWidgetsLayer (below widgets/icons in the launcher ViewGroup). */
    fun drawBehindWidgets(canvas: Canvas) {
        if (!isBehindWidget) return
        drawMonkey(canvas, clipSink = false)
    }

    /** Draw front gripping paws over the top edge of the widget while Settai peeks from behind it. */
    private fun drawWidgetGripPaws(canvas: Canvas) {
        val r = spots.getOrNull(surf.spot) ?: return
        val cx = ox + boxW / 2f
        val edgeY = r.top
        gripPaint.style = Paint.Style.FILL
        gripPaint.color = when (character.id) {
            "armour" -> Color.rgb(0x45, 0x5A, 0x64)
            "superboy" -> Color.rgb(0xD2, 0x99, 0x6C)
            "mask" -> Color.rgb(0xF5, 0xF5, 0xF5)
            else -> Color.rgb(0x6D, 0x43, 0x23)
        }
        for (side in intArrayOf(-1, 1)) {
            val px = cx + side * 26f * rigScale
            for (f in -1..1) {
                canvas.drawRoundRect(
                    RectF(px + f * 5.5f * rigScale - 2.4f * rigScale, edgeY - 3f * rigScale, px + f * 5.5f * rigScale + 2.4f * rigScale, edgeY + 6.5f * rigScale),
                    3f * rigScale, 3f * rigScale, gripPaint
                )
            }
        }
    }

    /** Launcher: perch/sit on top of widget/icon spot [index]. */
    fun perchOnSpot(index: Int, sit: Boolean = true) {
        spots = host?.spots() ?: spots
        val dest = spotSurf(index, SK.FLOOR) ?: return
        behindHoldT = 0f; endEntrance(); climber.deactivate(); falling = false
        surf = dest; mx = (dest.a + dest.b) / 2f; clampToSurf()
        controller.play(if (sit) MonkeyState.SIT else MonkeyState.IDLE, if (sit) 8f else 3f)
        updateGeometry(); invalidate(); host?.onFrame()
    }

    /** Launcher: duck behind widget/icon spot [index] and peek over its top edge (renders in BehindWidgetsLayer). */
    fun peekBehindSpot(index: Int, holdSecs: Float = 4.5f) {
        spots = host?.spots() ?: spots
        val dest = spotSurf(index, SK.FLOOR) ?: return
        climber.deactivate(); falling = false
        surf = dest; mx = (dest.a + dest.b) / 2f; clampToSurf()
        behindHoldT = holdSecs
        entKind = EntKind.UNHIDE; entSide = 0; entRelocated = true
        controller.play(MonkeyState.ENTRANCE, 0f, Ent.PEEK)
        layoutRig()
        sinkY = (peekDy - 12f * rigScale).coerceAtLeast(36f * rigScale)
        sinkClipY = oy + boxH - groundPad
        updateGeometry(); invalidate(); host?.onFrame()
    }

    /** Launcher: climb the left or right vertical side of widget [index]. */
    fun climbSpotSide(index: Int, leftSide: Boolean = true) {
        spots = host?.spots() ?: spots
        val dest = spotSurf(index, if (leftSide) SK.WALL_R else SK.WALL_L) ?: return
        behindHoldT = 0f; endEntrance(); falling = false
        surf = dest; my = (dest.a + dest.b) / 2f; clampToSurf()
        startClimberAtBox(dest)
        controller.idleNow(4f)
        updateGeometry(); invalidate(); host?.onFrame()
    }

    /** Launcher: hang from the bottom edge of widget [index]. */
    fun hangUnderSpot(index: Int) {
        spots = host?.spots() ?: spots
        val dest = spotSurf(index, SK.CEIL) ?: return
        behindHoldT = 0f; endEntrance(); falling = false
        surf = dest; mx = (dest.a + dest.b) / 2f; clampToSurf()
        startClimberAtBox(dest)
        controller.idleNow(4f)
        updateGeometry(); invalidate(); host?.onFrame()
    }

    /** Launcher: hop from app icon to app icon as stepping stones across [iconSpotIndices]. */
    fun hopAcrossIcons(iconSpotIndices: List<Int>, onDone: (() -> Unit)? = null) {
        spots = host?.spots() ?: spots
        val valid = iconSpotIndices.filter { it in spots.indices }
        if (valid.isEmpty()) { onDone?.invoke(); return }
        behindHoldT = 0f; endEntrance(); climber.deactivate(); falling = false
        fun stepHop(idx: Int) {
            if (idx >= valid.size) {
                controller.play(MonkeyState.HAPPY_PAT, 1.8f)
                onDone?.invoke()
                return
            }
            val spotIdx = valid[idx]
            val dest = spotSurf(spotIdx, SK.FLOOR) ?: return stepHop(idx + 1)
            val fx = mx; val fy = my
            val tx = (dest.a + dest.b) / 2f; val ty = dest.fix
            controller.face(if (tx >= fx) 1f else -1f)
            controller.play(MonkeyState.JUMP, 0.36f)
            surf = freeSurf(fy)
            mx = tx; my = ty
            slideFrom(fx, fy, 0.34f, 44f * d) {
                surf = dest; clampToSurf()
                updateGeometry(); invalidate(); host?.onFrame()
                ui.postDelayed({ stepHop(idx + 1) }, 140L)
            }
        }
        stepHop(0)
    }

    /** Launcher interactive physics: user started dragging a widget or icon on the home screen. */
    fun onWidgetDragStarted(spotIndex: Int, label: String) {
        behindHoldT = 0f; endEntrance()
        val onIt = surf.spot == spotIndex || spots.getOrNull(spotIndex)?.let { r ->
            RectF.intersects(RectF(mx, my, mx + boxW, my + boxH), RectF(r.left - 20 * d, r.top - 40 * d, r.right + 20 * d, r.bottom + 20 * d))
        } == true
        if (onIt || surf.spot >= 0) {
            climber.deactivate()
            val fx = mx; val fy = my
            surf = screenSurf(SK.FLOOR)
            mx = (mx + (if (mx < sw() / 2f) 90 * d else -90 * d)).coerceIn(0f, max(0f, sw() - boxW))
            my = ground()
            slideFrom(fx, fy, 0.32f, 52f * d)
        }
        controller.play(MonkeyState.STARTLED, 1.8f)
        say("Whoa! Moving $label?! Watch my tail! 🐒📦", 2600L)
        if (host != null) runCatching { com.combo.monkeycompanion.powers.ActionLog.add(context, "Widget dragged: $label", "Settai jumped clear & watched") }
        updateGeometry(); invalidate(); host?.onFrame()
    }

    /** Launcher interactive physics: user placed a dragged widget or icon at its new spot [spotIndex]. */
    fun onWidgetDragEnded(spotIndex: Int, label: String) {
        spotsChanged()
        val dest = spotSurf(spotIndex, SK.FLOOR)
        if (dest != null) {
            val fx = mx; val fy = my
            val tx = (dest.a + dest.b) / 2f; val ty = dest.fix
            controller.face(if (tx >= fx) 1f else -1f)
            controller.play(MonkeyState.JUMP, 0.45f)
            surf = freeSurf(fy)
            mx = tx; my = ty
            slideFrom(fx, fy, 0.42f, 54f * d) {
                surf = dest; clampToSurf()
                controller.play(MonkeyState.PEEK, 2.0f)
                say("Ooh, new ledge for $label! Solid footing ✓", 2800L)
                updateGeometry(); invalidate(); host?.onFrame()
            }
        }
        if (host != null) runCatching { com.combo.monkeycompanion.powers.ActionLog.add(context, "Widget placed: $label", "Settai explored new ledge #$spotIndex") }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val wall0 = System.nanoTime()
        val cpu0 = Debug.threadCpuTimeNanos()
        // dt up to 0.12 s so 10-15 fps idle frames keep real time.
        val dt = if (lastNanos == 0L || frameTimeNanos <= lastNanos) 0.016f else ((frameTimeNanos - lastNanos) / 1e9f).coerceIn(0f, 0.12f)
        lastNanos = frameTimeNanos
        t += dt

        // Resting behind the edge: a static tab, no frame loop (only a short peek now and then).
        if (hiddenTab && !wp && hideAnim < 0f) {
            if (tabPeekT >= 0f) { tabPeekT += dt; if (tabPeekT > TAB_PEEK_SECS) tabPeekT = -1f }
            updateGeometry()
            invalidate()
            // v10.4: balls flung away before ducking out keep bouncing until they settle (then the loop stops again).
            if (balls.looseActive) { balls.stepLoose(dt, 0f, topY(), sw(), ballFloor(), ballR(), d); syncFx(); postNext(wall0, 0L); return }
            syncFx()
            EnergyMeter.addFrame(Debug.threadCpuTimeNanos() - cpu0)
            if (tabPeekT < 0f) { running = false; EnergyMeter.mode = "stopped: resting behind the edge (static tab, 0 fps)"; return }
            EnergyMeter.mode = "tab peek"
            if (running) postNext(wall0, 1000L / (if (saver) 15 else 24))
            return
        }

        syncCharacter()
        // v10.13: attention — while the user touches me, or my popup/chat is open, I stop and stay put.
        val att = attending() && !falling
        if (att && !attWas && (controller.state == MonkeyState.WALK || controller.state == MonkeyState.DASH)) controller.idleNow(ATTEND_GRACE + 1f)
        attWas = att
        val keepX = mx; val keepY = my
        controller.update(dt)
        if (att) { mx = keepX; my = keepY }
        if (stepExit()) return
        if (!stepPower(dt) && !att) stepPhysics(dt)
        stepHide(dt)
        stepEntrance(dt)
        stepHints(dt)
        if (sleepFrames > 0 && --sleepFrames == 0) { paused = true; stop(); return }
        if (bubbleOn) {
            bubbleAge += dt
            if (bubbleFlyV != 0f && bubbleAge > bubbleLife) bubbleDragX += bubbleFlyV * dt
            // Never auto-hide (or page away) while the user is typing in the bubble.
            if (typing && bubbleAge > bubbleLife - 0.3f) bubbleLife = bubbleAge + 0.3f
            if (bubbleAge > bubbleLife && pages.isNotEmpty()) nextPage()
            else if (bubbleAge > bubbleLife + FADE) { bubbleOn = false; pendingShown = false; quitAsk = false }
        }
        stepBeats()
        stepHeldSpeech(dt)
        if (thinkingBubble) {
            thinkDots += dt
            if (thinkDots > 0.35f) {
                thinkDots = 0f
                val n = ((t * 2.85f).toInt() % 3) + 1
                bubble.setText("Hmm" + ".".repeat(n), (210 * d).toInt()); bubble.reportable = false
            }
        }
        if (chatBtnUntil > 0f) chatBtnUntil -= dt
        if (hintUntil > 0f) hintUntil -= dt
        quipCheckIn -= dt
        if (quipCheckIn <= 0f) { quipCheckIn = 60f; refreshQuipSettings() }
        stepRest(dt)
        stepRandomPower(dt)
        if (restNap && !controller.isAsleep && controller.state != MonkeyState.FALLING && controller.state != MonkeyState.LANDING && afterLand == null) restNap = false
        facingAnim += (controller.facing - facingAnim) * min(1f, dt * 12f)
        if (showOn) facingAnim += (showFrame.facing - facingAnim) * min(1f, dt * 18f)

        val ws = com.combo.monkeycompanion.mind.MindHub.world.state
        controller.worldSpeed = ws.speedMultiplier
        character.fill(target, controller.state, t, controller.stateTime, controller.anim, wear)
        if (smoothAnim) {
            animEngine.setTuning(profile.anim)
            animEngine.smoothPose(current, target, dt, 1f - exp(-dt * 14f))
        } else current.blendToward(target, 1f - exp(-dt * 14f))
        drawPose.set(current)
        drawPose.sweat = max(heat, if (ws.hot) ws.heat else 0f); drawPose.mouthCloth = if (silent || ws.hushed) 1f else 0f
        if (ws.hungry && controller.state == MonkeyState.IDLE && moment == null) {
            // Lasting low-energy posture: sleepy eyes, slight hunch, occasional belly rub
            drawPose.eyeOpen *= 0.80f
            drawPose.bodyY += 4f
            drawPose.mouthSmile = min(drawPose.mouthSmile, -0.15f)
            drawPose.armRRaise = 35f + 15f * sin(t * 3.2f)
            drawPose.armRElbow = 95f
        } else if (ws.feeding && controller.state == MonkeyState.IDLE && moment == null) {
            // Feeding time: happily absorbing current at the bottom edge
            drawPose.mouthSmile = max(drawPose.mouthSmile, 0.85f)
            if (!saver && Random.nextFloat() < 0.08f) {
                val f = feetScreen(tmpPt)
                particles.spawn(com.combo.monkeycompanion.rig.Particles.SPARK, f[0] + (Random.nextFloat() - 0.5f) * 28f * d, f[1] - 4f * d,
                    0f, -45f * d, 0.35f, 4.5f * d)
            }
        }
        if (shadesT > 0f) { shadesT -= dt; drawPose.shades = envelope(shadesT, 6f) }
        if (earsT > 0f) { earsT -= dt; drawPose.earPaws = envelope(earsT, 3.5f) }
        perk += (perkTarget - perk) * min(1f, dt * 10f)
        if (perk > 0.01f) { drawPose.earPerk = perk; drawPose.earWiggle += kotlin.math.sin(t * 9f) * 0.25f * perk * (0.4f + (com.combo.monkeycompanion.agent.Voice.level.coerceIn(0f, 10f) / 10f)) }
        if (gogglesT > 0f) { gogglesT -= dt; drawPose.goggles = envelope(gogglesT, 4f) }
        if (magT > 0f) { magT -= dt; drawPose.magnifier = envelope(magT, 5f) }
        if (noteT > 0f) { noteT -= dt; drawPose.notebook = envelope(noteT, 4f) }
        if (stethT > 0f) { stethT -= dt; drawPose.stethoscope = envelope(stethT, 5f) }
        if (globeT > 0f) { globeT -= dt; drawPose.globe = envelope(globeT, 4.5f) }
        val driving = com.combo.monkeycompanion.agent.ScreenAgent.driving
        wheelK += ((if (driving) 1f else 0f) - wheelK) * min(1f, dt * 5f)
        if (wheelK > 0.02f) { drawPose.wheel = wheelK; if (driving) drawPose.goggles = max(drawPose.goggles, wheelK) }
        micK += ((if (com.combo.monkeycompanion.agent.Voice.listening) 1f else 0f) - micK) * min(1f, dt * 6f)
        if (micK > 0.02f) { drawPose.micGlow = micK; drawPose.earWiggle += 0.5f * micK * sin(t * 9f) }
        if (yawnT > 0f) {
            yawnT -= dt
            val y = envelope(yawnT, 2.4f)
            drawPose.mouthOpen = max(drawPose.mouthOpen, y); drawPose.eyeOpen *= 1f - 0.85f * y; drawPose.mouthSmile *= 1f - y
        }
        // Blinking (not while eyes are squeezed shut with joy).
        blinkIn -= dt
        if (blinkIn <= 0f) { blinkT = 0f; blinkIn = 2f + Random.nextFloat() * 4f }
        if (blinkT >= 0f) {
            blinkT += dt
            val k = blinkT / 0.16f
            if (k >= 1f) blinkT = -1f else drawPose.eyeOpen *= abs(1f - 2f * k).coerceAtLeast(0.05f)
        }

        stepMoment(dt)
        stepTrunkSpray(dt)
        stepHeartAndCoPresence(dt)
        stepNeighbours(dt)
        if (host != null && !wp && forcedCharacterId == null) com.combo.monkeycompanion.mind.MindHub.stepSkit(dt, abort = dragging)
        if (v5On) {
            stage.step(dt)
            stage.setVisible(!paused && !hiddenTab && live)
            if (stage.staring) { drawPose.eyeOpen = max(drawPose.eyeOpen, 1.15f); blinkT = -1f }
        }
        if (notifCardOpen && abs(notifFlingVy) > 8f) {
            if (notifCard.scrollBy(notifFlingVy * dt)) invalidate()
            notifFlingVy *= exp(-dt * 6.5f)
            if (abs(notifFlingVy) <= 8f) notifFlingVy = 0f
        }
        if (falling && coyoteScareTimer > 0f) {
            // v9.3: 1.0-Second Tom & Jerry Mid-Air Scare Hold Pose!
            drawPose.shadow = 0f
            drawPose.eyeOpen = 1.65f
            drawPose.eyePop = 0.85f
            drawPose.mouthOpen = 0.92f
            drawPose.mouthSmile = -0.65f
            drawPose.lookY = 4.5f
            drawPose.headRot = 11f * sin(t * 38f)
            drawPose.bodyX = 3.2f * sin(t * 58f)
            drawPose.armLRaise = 138f + 24f * sin(t * 44f)
            drawPose.armRRaise = 138f - 24f * sin(t * 44f)
            drawPose.legLSwing = 34f * sin(t * 50f)
            drawPose.legRSwing = -34f * sin(t * 50f)
            drawPose.legLLift = 14f
            drawPose.legRLift = 14f
        } else if (isEdgeCrashLanding) {
            applyEdgeCrashPose(drawPose)
        } else if (falling && landBeat > 0f) LandingPoses.decide(drawPose, 1f - landBeat / Landings.beat(Landings.Style.CANNONBALL, false), t)
        else if (falling && arc != null) arcPose(drawPose)
        else if (falling && landStyle != Landings.Style.SIMPLE) {
            LandingPoses.fall(drawPose, landStyle, landT)
            if (landStyle == Landings.Style.CANNONBALL && landBounced) { drawPose.squashY = 0.9f; drawPose.squashX = 1.1f }
        } else if (vineOn) LandingPoses.fall(drawPose, Landings.Style.VINE_SWING, t)
        if (hoverOn && !isEdgeCrashLanding) hoverPose(drawPose)
        if (rocketFlyOn) rocketFlyPose(drawPose)
        if (mcOn) maskClimbPose(drawPose)
        juggleOn = computeJuggleOn()
        if (juggleOn) jugglePose(drawPose)
        applyDailyPoses(drawPose, dt)   // v10.20 sulk (arms folded, turned away) and the goodnight yawn
        impact?.applyPose(drawPose, t)   // v10.20 physical comedy wins over everything else
        swipeTrip?.applyPose(drawPose, t)   // v10.24 the character's own swipe trip
        syncFx()
        updateGeometry()
        if (smoothAnim && profile.anim.enabled && !climber.active) {
            val fp = feetScreen(animPt)
            animEngine.step(drawPose, dt, fp[0], fp[1], d, controller.facing, falling || hoverOn || powerOn || dragging)
            renderer.anim = animEngine; rig.squashPivot = animEngine.squashPivot
        } else { renderer.anim = null; rig.squashPivot = 0f }
        stepLiving(dt)
        stepChatFab(dt)
        stepFriendHeart(dt)   // v10.19
        positionReplyField()
        if (typing && !bubbleOn) endTyping()
        layoutRig()
        stepJuggle(dt)
        if (climber.active) climberStep(dt)
        if (powerOn) flight.power?.let { pw ->
            emitter.frame(rig, drawPose, pw, flight.phase, flight.index, flight.k, powerVx, powerVy, controller.facing, rigScale, dt,
                winX.toFloat(), winY.toFloat())
        } else if (particles.active) particles.update(dt)

        val st = controller.state
        val calm = (st == MonkeyState.IDLE || st == MonkeyState.HIDDEN || st == MonkeyState.SIT || st == MonkeyState.SLEEP) && !bubbleOn &&
            chatBtnUntil <= 0f && hintUntil <= 0f && !climber.active && blinkT < 0f && !(v5On && stage.active) && !juggleOn
        invalidate(); host?.onFrame()
        EnergyMeter.addFrame(Debug.threadCpuTimeNanos() - cpu0)
        if (!running) return
        // Wallpaper nap: once curled up, stop drawing entirely (a touch or an event restarts the loop).
        if (restNap && controller.isAsleep && controller.stateTime > 1.5f && entKind == EntKind.NONE) {
            running = false; EnergyMeter.mode = "stopped: napping (auto-rest, 0 fps)"; return
        }
        calmFor = if (calm && !showOn) calmFor + dt else 0f
        // v10.13: phone Battery Saver on and nothing happening for a while: curl up asleep (sleep = low fps cap)
        if (sysSaver && calmFor > 45f && !controller.isAsleep && host != null && !wp && !hiddenTab) { calmFor = 0f; onAction(BodyAction.SLEEP) }
        val iv = frameIntervalMs(calm)
        EnergyMeter.mode = when {
            iv == 0L -> "full speed (dragging / moving)"
            controller.isAsleep -> "sleep: ${1000 / iv} fps cap"
            calm -> "calm idle: ${1000 / iv} fps cap"
            else -> "active: ${1000 / iv} fps cap"
        }
        postNext(wall0, iv)
    }

    private fun stepPhysics(dt: Float) {
        if (edgeCrashCooldown > 0f) edgeCrashCooldown = (edgeCrashCooldown - dt).coerceAtLeast(0f)
        if (mcOn && !dragging) stepMaskClimb(dt)
        if (host == null) return
        if (stepChaseShow()) return
        syncImeDodge()
        if (rotEase != 0f) { rotEase *= exp(-dt * 9f); if (abs(rotEase) < 0.3f) rotEase = 0f }
        if (rocketFlyOn) { if (dragging) { rocketFlyOn = false; rocketFly.cancel(); arcRot = 0f } else { stepRocketFly(dt); return } }
        if (swipeTrip != null) { stepSwipeTrip(dt); return }   // v10.24
        if (impact != null) { stepImpact(dt); return }   // v10.20
        if (isEdgeCrashLanding) {
            stepEdgeCrashLanding(dt)
            return
        }
        if (tweenT >= 0f && pb.stepTween(dt) == StepEvent.TWEEN_DONE) {
            if (!dragging && !falling) clampToSurf()
            val dn = tweenDone; tweenDone = null; dn?.invoke()
        }
        if (falling && coyoteScareTimer > 0f) {
            // v9.3: Tom & Jerry 1-Second Mid-Air Scare Hold (frozen in mid-air with zero gravity for 1.0s before falling!)
            if (pb.stepCoyote(dt) == StepEvent.COYOTE_END) {
                landBeat = 0f
                if (character.id == "billa") say("MEOWWW— GOING DOWN!! 🙀⬇️", 1600L)
                else if (character.id == "chittu") say("SQUEAK!! Gravity remembered me!! 🐭⬇️", 1600L)
            }
            return
        }
        if (falling && landBeat > 0f) {
            // v4.4 smart landing: hover a moment, look down, decide.
            landBeat -= dt
            if (landBeat <= 0f) onLandDecided()
        } else if (falling && arc != null) {
            stepArc(dt)
        } else if (falling && landStyle == Landings.Style.CROW_CARRY) {
            landT += dt
            if (landT < crowSecs) {   // the crow flaps him along by the collar, a little up and sideways
                my = max(topY() + 10 * d, my - 26f * d * dt * (if (landT < 0.6f) 1f else -0.4f))
                mx = (mx + landDir * 150f * d * dt).coerceIn(0f, max(0f, sw() - boxW))
                controller.face(landDir)
            } else {
                landStyle = Landings.Style.CHICKEN_FLAP; vy = 0f   // ...and lets go
                say(listOf("Crow, NO!! 🐦‍⬛", "That's not where I said!", "Worst. Taxi. Ever.").random(), 1800L)
            }
        } else if (falling) {
            landT += dt
            val st0 = landStyle
            pb.fallSpeed(st0, dt, d, character.fallGravity(wear), ground(), physics)   // umbrella: gentle terminal speed
            if (st0 != Landings.Style.SIMPLE) {
                if (st0 == Landings.Style.PEEL_SLIDE) { mx = (mx + landDir * 340f * d * dt).coerceIn(0f, max(0f, sw() - boxW)) }
                landParticles(st0)
            }
            val ev = pb.fallMove(dt, ground(), st0 == Landings.Style.CANNONBALL && !landBounced, d, physics)
            if (ev == StepEvent.BOUNCED) {
                // Cannonball: one big squashy bounce first.
                landBounced = true
                landPuff(Landings.Style.CANNONBALL); performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            } else if (ev == StepEvent.LANDED) {
                surf = screenSurf(SK.FLOOR); clampToSurf()
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (st0 != Landings.Style.SIMPLE) landPuff(st0)
                if (landPicked != null) logPlayed(null)
                landStyle = Landings.Style.SIMPLE
                controller.onLanded()
                if (st0 == Landings.Style.SNORE_FLOAT) say("Huh? Did I land? 😴", 2200L)
                afterLand?.let { a -> afterLand = null; ui.postDelayed({ a() }, 480) }
            }
        }
        // On a ceiling the body turns to face the way it travels.
        if (surf.kind == SK.CEIL && climber.active && climber.face != faceOn(surf)) climber.attach(modeOf(surf), surf.line, faceOn(surf), surf.spanA, surf.spanB)
    }

    private var afterLand: (() -> Unit)? = null
    private val crowSecs = 1.7f

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
    private fun effectiveRigScale(): Float {
        val ch = character
        val m = sizeCache(ch.id)
        return if (ch is com.combo.monkeycompanion.character.CustomCharacter) {
            rigScale * 0.95f * 1.25f * m
        } else rigScale * profile.drawScale * m
    }

    private var sizeId = ""; private var sizeVal = 1f; private var sizeReadAt = 0L
    /** Per-character size, re-read at most once a second (no prefs read per frame). */
    private fun sizeCache(id: String): Float {
        val now = SystemClock.uptimeMillis()
        if (id != sizeId || now - sizeReadAt > 1000L) { sizeId = id; sizeReadAt = now; sizeVal = runCatching { CompanionSize.get(context, id) }.getOrDefault(1f).let { if (host != null) it.coerceAtMost(sizeBox.coerceAtLeast(1f)) else it } }
        return sizeVal
    }

    private fun layoutRig() {
        val groundY = oy + boxH - groundPad
        val cx = ox + boxW / 2f
        val f = renderFacing()
        val sc = effectiveRigScale()
        rig.layout(drawPose, cx, groundY, sc, f)
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
                rig.layout(drawPose, cx, groundY + sinkY, sc, f)
                for (left in booleanArrayOf(true, false)) {
                    rig.handPoint(left, tmpA)
                    val side = if (tmpA[0] < cx) -1f else 1f
                    rig.solveArm(drawPose, left, cx + side * 40f * rigScale, reachY + sinkY)
                }
            }
        }
        var ay = groundY + sinkY
        var finalSc = sc
        rig.layout(drawPose, cx, ay, finalSc, f)
        if (controller.state != MonkeyState.ENTRANCE && hideAnim < 0f && !climber.active && sinkY == 0f) {
            rig.headTop(tmpPt)
            val minTopY = 4f * d
            if (tmpPt[1] < minTopY) {
                val span = (ay - tmpPt[1]).coerceAtLeast(10f)
                val avail = (ay - minTopY).coerceAtLeast(20f * d)
                finalSc = (sc * (avail / span)).coerceIn(rigScale * 0.62f, sc)
                rig.layout(drawPose, cx, ay, finalSc, f)
                rig.headTop(tmpPt)
                if (tmpPt[1] < minTopY) {
                    ay += (minTopY - tmpPt[1])
                    rig.layout(drawPose, cx, ay, finalSc, f)
                }
            }
        }
    }

    private fun ease(k: Float): Float { val c = k.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }

    private fun renderFacing(): Float =
        if (abs(facingAnim) < 0.06f) 0.06f * (if (facingAnim < 0) -1f else 1f) else facingAnim

    /** Hit test in view coordinates. */
    private fun hitTest(x: Float, y: Float): TouchZone {
        if (hiddenTab || controller.state == MonkeyState.HIDDEN) return TouchZone.NONE
        if (sinkY > boxH * 0.55f || abs(sinkX) > boxW * 0.5f) return TouchZone.NONE
        if (climber.active) return climber.hitTest(x + winX - sinkX, y + winY - sinkY)
        val z = rig.hitTest(x - sinkX, y)
        // v10.8: Rocket Girl's rocket is part of him (so a tap on it counts).
        if (z == TouchZone.NONE && profile.ridesProp && rig.propHit(x - sinkX, y)) return TouchZone.BELLY
        return z
    }

    /** v10.8: big living reactions (loop, backflip, hop, sparkler…) get the same small headroom as the comfort bits. */
    private fun reactionNeedsRoom(): Boolean = livingOn && when (reactionLayer.playing) {
        com.combo.monkeycompanion.engine.touch.Verb.LOOP, com.combo.monkeycompanion.engine.touch.Verb.SHOW_OFF, com.combo.monkeycompanion.engine.touch.Verb.HOP,
        com.combo.monkeycompanion.engine.touch.Verb.DODGE, com.combo.monkeycompanion.engine.touch.Verb.TAIL_YELP, com.combo.monkeycompanion.engine.touch.Verb.SPARKLER,
        com.combo.monkeycompanion.engine.touch.Verb.ROCKET_SPUTTER, com.combo.monkeycompanion.engine.touch.Verb.DIZZY -> true
        else -> false
    }

    /** v10.8: the finer body part under (x, y) for living reactions (ears, trunk, the rocket). */
    private fun partAt(x: Float, y: Float, z: TouchZone): com.combo.monkeycompanion.engine.touch.BodyPart {
        val bp = com.combo.monkeycompanion.engine.touch.BodyPart.of(z)
        if (climber.active || z == TouchZone.NONE) return bp
        val rx = x - sinkX; val b = profile.body
        if (b.prop && z != TouchZone.HEAD && z != TouchZone.HAND && rig.propHit(rx, y)) return com.combo.monkeycompanion.engine.touch.BodyPart.PROP
        if (z == TouchZone.HEAD || z == TouchZone.NECK) {
            if (b.trunk && rig.trunkHit(rx, y)) return com.combo.monkeycompanion.engine.touch.BodyPart.TRUNK
            if (b.ears && z == TouchZone.HEAD && rig.earHit(rx, y)) return com.combo.monkeycompanion.engine.touch.BodyPart.EAR
        }
        return bp
    }

    /**
     * v10.8 living reaction for a touch: picks a fresh verb (never one of the last two) and plays it as an additive
     * layer. Returns false when the classic v10.7 reaction should run instead (asleep, grumpy, private, busy states,
     * or the picker chose a LEGACY entry).
     */
    private fun livingTap(part: com.combo.monkeycompanion.engine.touch.BodyPart, zone: TouchZone, side: Int,
                          kind: com.combo.monkeycompanion.engine.touch.TouchKind, speedDp: Float, dirX: Float, dirY: Float,
                          pressure: Float, holdSec: Float): Boolean {
        if (!livingOn || part == com.combo.monkeycompanion.engine.touch.BodyPart.PRIVATE || part == com.combo.monkeycompanion.engine.touch.BodyPart.NONE) return false
        if (controller.isAsleep || controller.grumpyLevel > 0.5f || powerOn || climber.active || hiddenTab || exiting || falling) return false
        when (controller.state) {
            MonkeyState.DRAGGED, MonkeyState.FALLING, MonkeyState.ENTRANCE, MonkeyState.HIDDEN, MonkeyState.SLEEP,
            MonkeyState.EXIT, MonkeyState.POWER, MonkeyState.LANDING, MonkeyState.WAKE -> return false
            else -> Unit
        }
        val id = character.id
        if (pickerFor != id) { pickerFor = id; picker = com.combo.monkeycompanion.engine.touch.ReactionPicker(id.hashCode().toLong() * 7919L + System.currentTimeMillis()) }
        val now = SystemClock.uptimeMillis() / 1000f
        val inp = touchTracker.build(part, zone, side, kind, speedDp, dirX, dirY, pressure, holdSec, now)
        val energy = runCatching { com.combo.monkeycompanion.mind.MindHub.world.state.energy }.getOrDefault(0.8f)
        val r = picker.pick(profile.reactionTable, inp, com.combo.monkeycompanion.engine.touch.MoodSnapshot(controller.grumpyLevel, energy),
            com.combo.monkeycompanion.mind.Personality.base(id), profile.anim.expressiveness, profile.anim.tempo, now) ?: return false
        com.combo.monkeycompanion.engine.anim.StageBus.announce(stageId, mx + boxW / 2f, now, min(1.6f, r.dur))
        if (r.verb == com.combo.monkeycompanion.engine.touch.Verb.LEGACY) return false
        controller.touched()
        r.entry.baseState?.let { controller.play(it, r.dur) }
        reactionLayer.play(r)
        if (r.verb == com.combo.monkeycompanion.engine.touch.Verb.LEAN_IN || r.verb == com.combo.monkeycompanion.engine.touch.Verb.NOD) living.happySquint(r.dur * 0.9f)
        if (r.talk) r.entry.lines?.let { pool -> say(Lines.pickWith(pool) { picker.line(it) }, 2200L) }
        when (r.verb) {
            // v10.10: a tap never launches a flight (it confused double-tappers); BOOST is just a whoosh + sparkle now
            com.combo.monkeycompanion.engine.touch.Verb.BOOST -> com.combo.monkeycompanion.audio.Sfx.play(context, com.combo.monkeycompanion.audio.Sfx.S.WHOOSH, 0.45f)
            com.combo.monkeycompanion.engine.touch.Verb.SPARKLER -> com.combo.monkeycompanion.audio.Sfx.play(context, com.combo.monkeycompanion.audio.Sfx.S.FIZZ, 0.45f)
            com.combo.monkeycompanion.engine.touch.Verb.LOOP -> com.combo.monkeycompanion.audio.Sfx.play(context, com.combo.monkeycompanion.audio.Sfx.S.WHOOSH, 0.5f)
            else -> Unit
        }
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        if (live && !running && !paused) start()
        return true
    }

    /** v10.8: Disney secondary action / staging / anticipation, touch reactions, fireworks, Rocket Girl exhaust, Diwali accents. */
    private fun stepLiving(dt: Float) {
        val st = controller.state
        val arcNow = arc != null
        if (smoothAnim && profile.anim.enabled) {
            if (arcNow && !livingLastArc) arc?.let { a -> living.anticipate(com.combo.monkeycompanion.engine.anim.Antic.CROUCH, if (a.x1 >= a.x0) 1f else -1f) }
            else if (st != livingLastState) when (st) {
                MonkeyState.POWER -> living.anticipate(com.combo.monkeycompanion.engine.anim.Antic.LEAN_BACK, controller.facing)
                MonkeyState.DANCE, MonkeyState.PLAYFUL -> living.anticipate(com.combo.monkeycompanion.engine.anim.Antic.CROUCH, 0f)
                MonkeyState.WALK -> living.anticipate(com.combo.monkeycompanion.engine.anim.Antic.EYES, controller.facing)
                else -> Unit
            }
        }
        livingLastState = st; livingLastArc = arcNow
        val now = SystemClock.uptimeMillis() / 1000f
        if (smoothAnim && profile.anim.enabled && !climber.active && !hiddenTab) {
            living.tuning = profile.anim
            val calm = (st == MonkeyState.IDLE || st == MonkeyState.TALKING || st == MonkeyState.SIT) && !dragging && !falling && !powerOn && !hoverOn
            val others = com.combo.monkeycompanion.engine.anim.StageBus.othersHaveStage(stageId, now)
            val stageDir = if (others) (if (com.combo.monkeycompanion.engine.anim.StageBus.screenX < mx + boxW / 2f) -1f else 1f) else 0f
            living.step(drawPose, dt, animEngine, calm, others || reactionLayer.busy, stageDir, controller.facing, animEngine.velocityX / max(0.5f, d))
        }
        if (reactionLayer.busy) {
            reactionLayer.step(dt)
            if (dragging || falling || powerOn || climber.active) reactionLayer.clear()
            else reactionLayer.apply(drawPose, controller.facing, if (smoothAnim && profile.anim.enabled) animEngine else null)
        }
        // Diwali season accents (behind Settings › Festivals), checked once a minute.
        val nowMs = SystemClock.uptimeMillis()
        if (festiveCheckAt < 0L || nowMs - festiveCheckAt > 60_000L) {
            festiveCheckAt = nowMs
            festiveK = if (host != null && runCatching { com.combo.monkeycompanion.fun5.Fun5Store.on(context, com.combo.monkeycompanion.fun5.Fun5Store.Feature.FESTIVALS) }.getOrDefault(false) &&
                com.combo.monkeycompanion.v5.Festivals.diwaliSeason(java.time.LocalDate.now())) 1f else 0f
        }
        drawPose.festive = festiveK
        // Rocket Girl: sky-burst fireworks, the boost whistle, a sparkle exhaust trail while flying.
        val pw = if (powerOn) flight.power else null
        if (pw === com.combo.monkeycompanion.brain.SkyBurstPower && flight.phase == com.combo.monkeycompanion.brain.PowerPhase.CONFETTI) {
            if (!skyShowStarted && host != null) {
                skyShowStarted = true
                // the bottle stands where her right hand reaches down (hips (40, 34) = RocketGirlRenderer's fuse reach)
                livPt[0] = com.combo.monkeycompanion.rig.RocketGirlRenderer.FUSE_X; livPt[1] = com.combo.monkeycompanion.rig.RocketGirlRenderer.FUSE_Y; rig.hips.mapPoints(livPt)
                val fx = livPt[0] + sinkX + winX; val fy = livPt[1] + winY
                val k = com.combo.monkeycompanion.rig.SkyShow
                val drop = ((com.combo.monkeycompanion.rig.DiwaliRocketArt.TUBE_B - 3f + k.STICK_IN) * k.ROCKET_K + k.BOTTLE_H) * rigScale
                val floor = (ground() + boxH - groundPad).toFloat()
                val baseY = min(fy + drop, floor)
                val show = skyShows.firstOrNull { !it.active } ?: skyShows[skyShowNext].also { it.cancel() }
                skyShowNext = (skyShows.indexOf(show) + 1) % skyShows.size
                show.start(fx, baseY, rigScale, controller.facing, topY().toFloat(), ground().toFloat(), sw().toFloat())
            }
        } else skyShowStarted = false
        // her hand lights the fuse, then she looks up at the sky
        val show = skyShows.maxByOrNull { if (it.active) it.t.let { t0 -> 100f - t0 } else -1f }
        if (show != null && show.active && powerOn) {
            drawPose.rgReach = show.reach
            if (show.lookUp > 0f) { drawPose.lookY -= 4f; drawPose.mouthOpen = max(drawPose.mouthOpen, 0.35f); drawPose.mouthSmile = max(drawPose.mouthSmile, 0.8f) }
        }
        for (sh in skyShows) sh.step(dt)
        if (pw === com.combo.monkeycompanion.brain.RocketBoostPower) {
            if (flight.index == 2 && !boostWhistled) { boostWhistled = true; com.combo.monkeycompanion.audio.Sfx.play(context, com.combo.monkeycompanion.audio.Sfx.S.WHISTLE, 0.6f) }
        } else boostWhistled = false
        if (profile.ridesProp && !saver && host != null && !wp && (powerOn || arcNow || hoverOn || falling)) {
            // v10.10: a real sky-rocket exhaust: a narrow fountain of tiny falling sparks + a thin smoke wisp (never a blast)
            val thrust = if (rocketFlyOn) rocketFly.thrust else 0f
            exhaustAcc += dt * (22f + 70f * thrust)
            while (exhaustAcc >= 1f) {
                exhaustAcc -= 1f
                // out of the ROCKET's tube bottom (never from her), back along the stick, rotated with the ride
                com.combo.monkeycompanion.rig.RocketGeom.exhaust(livPt); rig.hips.mapPoints(livPt)
                com.combo.monkeycompanion.rig.RocketGeom.exhaust(tmpB); tmpB[0] -= com.combo.monkeycompanion.rig.RocketGeom.noseX * 10f; tmpB[1] -= com.combo.monkeycompanion.rig.RocketGeom.noseY * 10f
                rig.hips.mapPoints(tmpB)
                rotArc(livPt); rotArc(tmpB)
                var dx = tmpB[0] - livPt[0]; var dy = tmpB[1] - livPt[1]
                val dl = max(0.001f, hypot(dx, dy)); dx /= dl; dy /= dl
                val v = (55f + 150f * thrust) * d * (0.6f + 0.4f * fxRnd.nextFloat())
                val spread = (fxRnd.nextFloat() - 0.5f) * 0.35f
                val cs = kotlin.math.cos(spread); val sn = kotlin.math.sin(spread)
                val ex = dx * cs - dy * sn; val ey = dx * sn + dy * cs
                particles.spawn(com.combo.monkeycompanion.rig.Particles.EMBER, livPt[0] + sinkX + winX, livPt[1] + winY,
                    ex * v, ey * v, 0.35f + fxRnd.nextFloat() * 0.25f + 0.3f * thrust, (1.6f + fxRnd.nextFloat() * 1.0f + 0.6f * thrust) * d)
                if (fxRnd.nextFloat() < 0.18f + 0.25f * thrust)   // a thin grey smoke trail behind the rocket
                    particles.spawn(com.combo.monkeycompanion.rig.Particles.WISP, livPt[0] + sinkX + winX + dx * 6f * d, livPt[1] + winY + dy * 6f * d,
                        dx * 20f * d, dy * 20f * d - 6f * d, 0.8f + 0.4f * thrust, 3.2f * d)
            }
        }
        if (fireworks.active) fireworks.step(dt)
    }

    // ---------------- Edge hide (overlay) ----------------

    private val tabW get() = 24 * d
    /** 0..1: how far the face pokes out of the tab during a peek. */
    private fun tabPeekAmount(): Float {
        val k = tabPeekT
        if (k < 0f) return 0f
        return min(ease(k / 0.45f), ease((TAB_PEEK_SECS - k) / 0.5f))
    }
    private val tabL get() = 66 * d

    private fun clampTab() {
        val h = host ?: return
        val maxSideY = if (externalImeOpen()) (imeSafeBottomY() + boxH - tabL / 2).coerceAtLeast(topY() + tabL / 2 + 8 * d)
        else h.screenHeight() - h.bottomInset() - tabL / 2 - 8 * d
        tabPos = if (hideSide == 2) tabPos.coerceIn(tabL / 2 + 4 * d, h.screenWidth() - tabL / 2 - 4 * d)
        else tabPos.coerceIn(topY() + tabL / 2 + 8 * d, maxSideY)
    }

    /** Dash at the edge and duck behind it. [side] -1 left, 1 right, 2 bottom. */
    fun hideToEdge(side: Int, rest: Boolean = false) {
        if (host == null || wp || hiddenTab || hideAnim >= 0f) return
        if (notifCardOpen) closeNotifSummaryCard()
        resting = rest; beats.clear()
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
            scheduleRestTimers()
            if (host != null && !wp) { com.combo.monkeycompanion.mind.MindHub.setVisible(false); scheduleMindBeat() }
        }
    }

    private fun saveHidden() {
        prefs.edit().putBoolean("hidden", hiddenTab).putBoolean("resting", hiddenTab && resting).putInt("hideSide", hideSide).putFloat("tabPos", tabPos).apply()
    }

    /**
     * v10.21.1 STRICT RULE: a character YOU hid (swipe to the edge, or "hide" in the menu) never comes out by
     * itself: not for the mind, skits, the charger, comfort moments, utility alerts, cricket or AI actions. Only your
     * tap / drag on its tab (or something you ask for, like opening the chat) brings it back.
     * Rest hides (it went to rest by itself, screen-off, night) may still come back as before.
     */
    val userHidden get() = hiddenTab && !resting

    /** Come out on our own (not asked by you). Returns false (and stays hidden) if you hid it. */
    private fun autoUnhide(): Boolean {
        if (!hiddenTab) return true
        if (userHidden) return false
        unhide(); return true
    }

    /** Tab tapped / dragged out: peek and climb back onto the screen. */
    fun unhide() {
        if (!hiddenTab || host == null) return
        hiddenTab = false
        // v10.21.1: ask about notifications only once (one character, new ones, or after "Later" + an unlock)
        val hasUnread = com.combo.monkeycompanion.agent.NotifPromptGate.shouldAsk(newestNotif(), System.currentTimeMillis())
        if (hasUnread && !chatAfterEntrance) promptNotifsAfterUnhide = true
        if (resting) Lines.note(Lines.Event.RESTED)
        resting = false
        cancelRestTimers()
        if (host != null && !wp) { com.combo.monkeycompanion.mind.MindHub.setVisible(true); scheduleMindBeat() }
        lastInteract = SystemClock.elapsedRealtime(); restGoIn = -1f
        saveHidden()
        if (!paused && live) start()
        if (hideSide == 2 || !climbs) {
            surf = screenSurf(SK.FLOOR)
            mx = if (hideSide == 2) tabPos - boxW / 2f else if (hideSide < 0) 0f else sw() - boxW; clampToSurf()
            beginEntrance(EntKind.UNHIDE, 0, Ent.HIDDEN)
        } else {
            val n = screenSurf(if (hideSide < 0) SK.WALL_L else SK.WALL_R)
            surf = n
            my = tabPos - boxH / 2f; clampToSurf()
            startClimberAtBox(n)
            beginEntrance(EntKind.UNHIDE, hideSide, Ent.HIDDEN)
        }
        if (promptNotifsAfterUnhide) {
            promptUnreadNotifications()
        }
    }

    // ---------------- Entrances ----------------

    private fun beginEntrance(kind: EntKind, side: Int, startAt: Float = 0f) {
        entKind = kind; entSide = side; entRelocated = startAt > 0f
        if (kind == EntKind.UNHIDE) entLine = if (promptNotifsAfterUnhide) null else Lines.pick(Lines.PEEKABOO)
        controller.play(MonkeyState.ENTRANCE, 0f, startAt)
        if (side == 0) { sinkX = 0f; sinkY = if (startAt > 0f) boxH.toFloat() else 0f }
        else { sinkY = 0f; sinkX = side * sideHideX() }
        tweenT = -1f; falling = false
    }

    private fun sideHideX() = 112f * rigScale

    private fun stepEntrance(dt: Float) {
        if (behindHoldT > 0f) {
            behindHoldT -= dt
            if (behindHoldT > 0f) {
                sinkY = (peekDy - 12f * rigScale).coerceAtLeast(36f * rigScale)
                sinkClipY = oy + boxH - groundPad
                return
            }
        }
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
                if (entKind == EntKind.UNHIDE) { controller.idleNow(3f); endEntrance() }
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
                controller.idleNow(3f); endEntrance()
                entLine?.let { l -> entLine = null; say(l, 4000L) }
            }
        }
    }

    private fun endEntrance() {
        entKind = EntKind.NONE
        sinkX = 0f; sinkY = 0f; sinkClipY = Float.NaN
        if (chatAfterEntrance) { chatAfterEntrance = false; ui.post { onDoubleTapChat() } }
        if (promptNotifsAfterUnhide) {
            promptNotifsAfterUnhide = false
            promptUnreadNotifications()
        }
    }

    /** Wake: after ducking at the bottom, maybe come out of a side edge or from behind a widget instead. */
    private fun relocateForWake() {
        if ((wp || ln) && spots.isNotEmpty() && Random.nextFloat() < 0.55f) {
            val i = Random.nextInt(spots.size)
            spotSurf(i, SK.FLOOR)?.let { s -> surf = s; mx = s.a + Random.nextFloat() * (s.b - s.a); clampToSurf() }
            return
        }
        if (climbs && Random.nextFloat() < 0.3f) {
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
        notifCardOpen = false
        bubbleOn = false; chatBtnUntil = 0f; hintUntil = 0f
        tweenT = -1f; tweenDone = null; falling = false; dragging = false; tracking = false; afterLand = null
        ui.removeCallbacks(longPress)
        endEntrance(); hideAnim = -1f
        beats.clear(); restGoIn = -1f; tabPeekT = -1f
        screenOffAt = System.currentTimeMillis()
        if (v5On) { stage.setVisible(false); stage.screenOff(); stage.endGames(false) }
        if (host != null && !wp) {
            ui.removeCallbacks(mindBeatRunnable)
            com.combo.monkeycompanion.mind.MindHub.event(com.combo.monkeycompanion.mind.MindEvent.SCREEN_OFF)
        }
        paused = false
        start()
        sleepFrames = 4   // draw a few frames, then stop the frame loop
        if (hiddenTab) { invalidate(); return }  // stays hidden: just the tab
        climber.deactivate()
        surf = screenSurf(SK.FLOOR); clampToSurf()
        controller.sleep()
        character.fill(current, MonkeyState.SLEEP, t, 0f, controller.anim, wear)
        invalidate()
    }

    /** Screen on (maybe still locked): show Settai snoozing; wake now if there's no lock screen. */
    fun onScreenOn() {
        sleepFrames = 0
        if (v5On) stage.screenOn()
        setPaused(false)
        val km = context.getSystemService(KeyguardManager::class.java)
        if (km == null || !km.isKeyguardLocked) ui.postDelayed({ onUserPresent() }, 700)
    }

    /** Unlocked: duck below the edge, peek out, grip, pull up, then the crazy wake routine. */
    fun onUserPresent(force: Boolean = false) {
        sleepFrames = 0
        if (chargePendingAt > 0L) ui.postDelayed({ tryCharge() }, 2500L)
        if (v5On) ui.postDelayed({ stage.onUnlock() }, 6000L)
        if (host != null && !wp) {
            com.combo.monkeycompanion.mind.MindHub.event(com.combo.monkeycompanion.mind.MindEvent.UNLOCK)
            scheduleMindBeat()
        }
        setPaused(false)
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val away = if (screenOffAt > 0L) System.currentTimeMillis() - screenOffAt else 0L
        val event = pendingEventLine; pendingEventLine = null
        if (hiddenTab) {
            // Resting behind the edge: come back out on unlock if the user wants that (or there's news).
            if (!wp && resting && (comeOutOnUnlock || event != null || force)) {
                unhide(); entLine = event ?: Lines.welcomeBack(away, hour)
            }
            return
        }
        if (!controller.isAsleep) { event?.let { say(it, 4500L) }; return }
        if (restNap && !comeOutOnUnlock && !force && event == null) return   // keep napping
        restNap = false
        lastInteract = SystemClock.elapsedRealtime()
        entLine = event ?: Lines.welcomeBack(away, hour)
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
            if (notifCardOpen) {
                notifCardLeft = (ox + boxW / 2f - notifCard.width / 2f).coerceIn(0f, max(0f, width - notifCard.width))
                notifCardTop = max(4f * d, oy - notifCard.height - 8f * d)
            }
            if (showBubble) {
                bubbleLeft = (ox + boxW / 2f - bubble.width / 2f).coerceIn(0f, max(0f, width - bubble.width))
                bubbleTop = max(0f, oy + bubbleOverlap() - bubble.trailSpace - bubble.height)
            }
            return
        }
        val sw = host.screenWidth(); val sh = host.screenHeight()
        if (hiddenTab && !wp) {
            // Only the little tab: a tiny window on the edge.
            clampTab()
            val thick = (tabW + tabPeekAmount() * tabW).toInt()
            if (hideSide == 2) { winW = tabL.toInt(); winH = thick; winX = (tabPos - tabL / 2).toInt(); winY = sh - host.bottomInset() - winH }
            else { winW = thick; winH = tabL.toInt(); winY = (tabPos - tabL / 2).toInt(); winX = if (hideSide < 0) 0 else sw - winW }
            ox = 0f; oy = 0f
            host.placeWindow(winX, winY, winW, winH)
            return
        }
        val boxY = my - typeLift
        if (moment == PhoneMoments.Moment.CHARGE && chargeTrip && !(wp || ln)) {
            // v6 charger moment: one window from the top of the screen (battery icon) down to Settai, so the bolt is ours.
            val pad = (24f * d).toInt()
            val l = min(mx - pad, momentTarget[0] - 50f * d).toInt().coerceIn(0, sw)
            val r = max(mx + boxW + pad, momentTarget[0] + 50f * d).toInt().coerceIn(l + 1, sw)
            val bubH = if (bubbleOn) (bubble.height + bubble.trailSpace).toInt() else 0
            winX = l; winW = r - l; winY = 0; winH = min(sh, (boxY + boxH).toInt() + pad + bubH)
            ox = mx - winX; oy = boxY - winY
            if (bubbleOn) {
                val bw = bubble.width
                bubbleLeft = (ox + boxW / 2f - bw / 2f).coerceIn(0f, max(0f, winW - bw))
                bubbleTop = oy + boxH + bubble.trailSpace
            }
            host.placeWindow(winX, winY, winW, winH)
            return
        }
        if (powerOn && !(wp || ln)) {
            // Flying: a window that follows the monkey with room for the cape / jets / palm rings. It stays TOUCHABLE and
            // fully opaque (v4 fix: FLAG_NOT_TOUCHABLE made Android 12+ clamp it to 80% alpha); trails go to the FX window.
            val big = flight.power.let { it == Superpower.KovamMode || it == Superpower.NinjaSettai || it == Superpower.TimeFreeze }
            val pad = (boxW * (if (big) 0.6f else if (fxHosted) 0.35f else 0.9f)).toInt()
            winW = min(sw, boxW + 2 * pad); winH = min(sh, boxH + 2 * pad)
            winX = (mx - pad).toInt().coerceIn(0, max(0, sw - winW)); winY = (boxY - pad).toInt().coerceIn(0, max(0, sh - winH))
            ox = mx - winX; oy = boxY - winY
            if (bubbleOn) {
                val bw = bubble.width
                bubbleLeft = (ox + boxW / 2f - bw / 2f).coerceIn(0f, max(0f, winW - bw))
                bubbleTop = max(0f, oy + bubbleOverlap() - bubble.trailSpace - bubble.height)
            }
            host.placeWindow(winX, winY, winW, winH)
            return
        }
        if ((arc != null || (falling && landStyle.fx && (landStyle.power || landStyle == Landings.Style.CROW_CARRY))) && !(wp || ln)) {
            // v5 power landing in flight: a window with room for the banked body and its flames (drawn in our own
            // window, so they show even when the effects window is unavailable).
            val pad = (max(boxW, boxH) * 0.32f).toInt()      // v6: snug, so the window itself travels along the path
            winW = min(sw, boxW + 2 * pad); winH = min(sh, boxH + 2 * pad)
            winX = (mx - pad).toInt().coerceIn(0, max(0, sw - winW)); winY = (boxY - pad).toInt().coerceIn(0, max(0, sh - winH))
            ox = mx - winX; oy = boxY - winY
            if (bubbleOn) {
                val bw = bubble.width
                bubbleLeft = (ox + boxW / 2f - bw / 2f).coerceIn(0f, max(0f, winW - bw))
                bubbleTop = max(0f, oy + bubbleOverlap() - bubble.trailSpace - bubble.height)
            }
            host.placeWindow(winX, winY, winW, winH)
            return
        }
        if (exiting && exitKind == com.combo.monkeycompanion.brain.Exit.ROCKET && controller.state == MonkeyState.EXIT && controller.stateTime > 1.2f && !(wp || ln)) {
            // Lift-off: a tall, narrow window from the top of the screen down to the launch spot.
            winX = mx.toInt(); winW = boxW; winY = 0; winH = (boxY + boxH).toInt().coerceAtLeast(boxH)
            ox = 0f; oy = boxY - winY
            host.placeWindow(winX, winY, winW, winH)
            return
        }
        if (notifCardOpen && !wp) {
            val cw = notifCard.width
            val ch = notifCard.height
            val gap = 8f * d
            winW = max(boxW.toFloat(), cw).toInt().coerceAtMost(sw)
            winX = (mx + boxW / 2f - winW / 2f).toInt().coerceIn(0, max(0, sw - winW))
            val minTop = topY()
            if (boxY - ch - gap >= minTop) {
                winH = (boxH + ch + gap).toInt().coerceAtMost(sh)
                winY = (boxY - ch - gap).toInt().coerceIn(0, max(0, sh - winH))
                notifCardTop = 0f
            } else {
                winH = (boxH + ch + gap).toInt().coerceAtMost(sh)
                winY = boxY.toInt().coerceIn(0, max(0, sh - winH))
                notifCardTop = boxH + gap
            }
            oy = boxY - winY
            ox = mx - winX
            notifCardLeft = (ox + boxW / 2f - cw / 2f).coerceIn(0f, max(0f, winW - cw))
            host.placeWindow(winX, winY, winW, winH)
            return
        }
        if (!showBubble) {
            winX = mx.toInt(); winY = boxY.toInt(); winW = boxW; winH = boxH
            ox = 0f; oy = 0f
        } else {
            val bw = bubble.width
            winW = max(boxW.toFloat(), bw).toInt()
            winX = (mx + boxW / 2f - winW / 2f).toInt().coerceIn(0, max(0, sw - winW))
            val above = bubble.height + bubble.trailSpace - bubbleOverlap()
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
        if (!wp && hoverOn && character.usesHoverJets) {
            // Room below the boots for hover jets (compact 18*rigScale for custom companions so it never blocks touches below).
            val jetPad = profile.hoverJetPadRig * rigScale
            val need = (boxY - winY + boxH + jetPad).toInt()
            if (winH < need) winH = min(need, max(winH, sh - winY))
        }
        val jh = if (!wp) juggleHeadroom() else 0
        if (jh > 0 && winY > 0) {
            // v10.4: room above the hat for the juggling balls.
            val lift = min(winY, jh)
            winY -= lift; winH = min(sh - winY, winH + lift); oy += lift; if (showBubble) bubbleTop += lift
        }
        if (!wp && (reactionNeedsRoom() || comfortKind != null || com.combo.monkeycompanion.mind.MindHub.focusBuddy.active || com.combo.monkeycompanion.mind.MindHub.breatheBuddy.active || (com.combo.monkeycompanion.mind.MindHub.campfire.active && com.combo.monkeycompanion.mind.MindHub.campfire.manual))) {
            val topExtra = (56f * rigScale).toInt()
            val sideExtra = (36f * rigScale).toInt()
            if (oy < topExtra && winY > 0) {
                val lift = min(winY, topExtra - oy.toInt())
                winY -= lift; winH = min(sh - winY, winH + lift); oy += lift; if (showBubble) bubbleTop += lift
            }
            val shiftL = min(winX, sideExtra)
            winX -= shiftL; ox += shiftL; if (showBubble) bubbleLeft += shiftL
            winW = min(sw - winX, max(winW + shiftL, (ox + boxW + sideExtra).toInt()))
        }
        if (!wp && neighbourOverlayVisible()) {
            // v7.0: widen this single window so the visiting neighbour draws inside the same window & frame loop.
            val extra = (boxW * 0.78f).toInt()
            val putRight = mx + boxW / 2f <= sw / 2f
            if (putRight) {
                winW = min(sw - winX, max(winW, (ox + boxW + extra).toInt()))
            } else {
                val shift = min(winX, extra)
                winX -= shift; winW = min(sw - winX, winW + shift)
                ox += shift; if (showBubble) bubbleLeft += shift
            }
        }
        if (wp || ln) {
            // Whole-screen view: same layout, in screen coordinates.
            ox += winX; oy += winY; bubbleLeft += winX; bubbleTop += winY
            winX = 0; winY = 0; winW = sw; winH = sh
            return
        }
        host.placeWindow(winX, winY, winW, winH)
    }

    private fun clampPosition() {
        val h = host ?: return
        mx = mx.coerceIn(0f, max(0f, (h.screenWidth() - boxW).toFloat()))
        my = my.coerceIn(min(topY(), ground()), ground())
        if (falling || dragging || tweenT >= 0f) return
        clampToSurf()
    }

    /** Released after a drag: fling at an edge hides; near an edge grabs it; else fall (roaming) / stay put. */
    private var upSwipeDown = -1f

    /** v10.10: how far the finger really travelled in the drag being released (a jittery tap is never a launch). */
    private var dragTravel = Float.MAX_VALUE
    private fun settleAfterDrag(fvx: Float, fvy: Float): Boolean {
        val g = ground()
        val fx = mx; val fy = my
        val near = 40 * d
        val fast = 1400 * d
        if (upSwipeDown <= 0f && host != null && !wp && !hiddenTab && !profile.ridesProp && hypot(fvx, fvy) > SWIPE_TRIP_DP * d && dragTravel >= 30 * d) {
            // v10.25 one clear rule for a fling: aimed at / released near a side edge = HIDE; a long throw from far = SPLAT on
            // that wall; aimed above the top of the screen = THWACK on the top; otherwise the character's own trip there.
            val cx = mx + boxW / 2f; val px = cx + fvx * 0.38f; val py = my + fvy * 0.38f
            val side = if (fvx < 0) -1 else 1
            val horiz = abs(fvx) >= abs(fvy)
            if (horiz) {
                val nearEdge = if (side < 0) cx < sw() * 0.22f else cx > sw() * 0.78f
                val past = if (side < 0) -px else px - sw()
                val ownTrip = com.combo.monkeycompanion.engine.swipe.SwipeTravel.styleFor(character.id,
                    com.combo.monkeycompanion.character.CustomCompanionRegistry.byId(character.id) != null) != null
                if (nearEdge || (ownTrip && past > 0f)) {   // Anna / Superboy / Rocket Girl / Satan / Angel keep their old fly-off arcs
                    val far = if (side < 0) cx > sw() * 0.55f else cx < sw() * 0.45f
                    if (far && past > sw() * 0.5f && tryToss(fvx, fvy, force = true)) return true
                    hideToEdge(side); return false
                }
            } else if (fvy < 0f && py < topY() - boxH * 0.2f) {
                if (tryToss(fvx, fvy, force = true)) return true
            }
            if (fvy <= 0f || abs(fvy) <= abs(fvx) * 1.2f) { if (trySwipeTrip(fvx, fvy)) return true }
        }
        if (hovers) {
            val sp = hypot(fvx, fvy)
            if (upSwipeDown > 0f) {          // swipe down = touch down, with a big curved rocket flight (v6 real-device fix)
                rocketLanding(upSwipeDown); return true
            }
            if (profile.ridesProp && livingOn && sp > ROCKET_SWIPE_DP * d && dragTravel >= ROCKET_SWIPE_MIN_TRAVEL_DP * d) {
                // v10.9 Rocket Girl: the rocket ignites and flies her along the swipe (a real rocket path)
                if (sp > fast && abs(fvx) >= abs(fvy)) {
                    val cx = mx + boxW / 2f
                    val nearEdge = if (fvx < 0) cx < sw() * 0.22f else cx > sw() * 0.78f
                    if (nearEdge) { hideToEdge(if (fvx < 0) -1 else 1); return false }
                }
                startRocketFly(fvx, fvy, sp); return true
            }
            if (sp > fast && abs(fvx) >= abs(fvy)) {
                val side = if (fvx < 0) -1 else 1
                val cx = mx + boxW / 2f
                val nearEdge = if (side < 0) cx < sw() * 0.22f else cx > sw() * 0.78f      // flung at a close edge: hide there
                if (nearEdge) { hideToEdge(side); return false }      // edge-hide still works
                val tx = (mx + boxW / 2f + side * sw() * (0.35f + 0.2f * Random.nextFloat())).coerceIn(boxW / 2f + 10 * d, sw() - boxW / 2f - 10 * d)
                val ty = (my + boxH / 2f + (Random.nextFloat() - 0.5f) * 300f * d).coerceIn(hoverMinY() + boxH / 2f, ground() + boxH / 2f - 60 * d)
                if (swipeTripInsteadOfArc(tx, ty)) return true   // v10.25.1: no looping jet arc for non-jet bodies
                flyTo(tx, ty, 1f, "you flung me ${if (side < 0) "left" else "right"}"); return true
            }
            if (sp > fast && fvy < 0) {
                val ux = boxW / 2f + Random.nextFloat() * (sw() - boxW); val uy = hoverMinY() + boxH / 2f + Random.nextFloat() * 200f * d
                if (swipeTripInsteadOfArc(ux, uy)) return true
                flyTo(ux, uy, 1f, "you flung me up"); return true
            }
            if (my < g - 64 * d) { hoverHere(); return false }
        }
        if (isMask) {
            if (my <= topY() + near * 2f) { startMaskHang(); return false }
            if (mx <= near * 0.6f || mx >= sw() - boxW - near * 0.6f) { startMaskClimb(if (mx < sw() / 2f) -1 else 1); return false }
        }
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
        val spotHit: Surf? = if ((wp || ln) && spots.isNotEmpty()) {
            val cx = mx + boxW / 2f
            val feetY = my + boxH - groundPad
            spots.indices.firstNotNullOfOrNull { idx ->
                val sr = spots[idx]
                when {
                    cx in (sr.left - 18 * d)..(sr.right + 18 * d) && abs(feetY - sr.top) <= 42 * d -> spotSurf(idx, SK.FLOOR)
                    climbs && cx in sr.left..sr.right && abs(my - sr.bottom) <= 36 * d -> spotSurf(idx, SK.CEIL)
                    climbs && sr.height() >= 70 * d && my + boxH * 0.5f in sr.top..sr.bottom && abs(cx - sr.left) <= 36 * d -> spotSurf(idx, SK.WALL_R)
                    climbs && sr.height() >= 70 * d && my + boxH * 0.5f in sr.top..sr.bottom && abs(cx - sr.right) <= 36 * d -> spotSurf(idx, SK.WALL_L)
                    else -> null
                }
            }
        } else null
        val edgeW = climbs || canWalkScreenEdges
        val n = spotHit ?: when {
            edgeW && my <= topY() + near * 1.15f -> screenSurf(SK.CEIL)
            my >= g - 64 * d -> screenSurf(SK.FLOOR)
            edgeW && mx <= near * 0.95f -> screenSurf(SK.WALL_L)
            edgeW && mx >= sw() - boxW - near * 0.95f -> screenSurf(SK.WALL_R)
            profile.coyoteOnMidScreenDrop -> {
                triggerMidScreenCoyoteFall(mx, my)
                null
            }
            controller.roam -> { beginSmartFall(); null }
            else -> freeSurf(my)
        } ?: return true
        surf = n
        clampToSurf()
        if (n.climb && climbs) startClimber(n) else climber.deactivate()
        if (abs(mx - fx) > 1f || abs(my - fy) > 1f) slideFrom(fx, fy, 0.18f)
        return false
    }

    /** Screen rotated / resized. */
    fun onScreenChanged() {
        if (wp || ln) spots = host?.spots() ?: emptyList()
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
    private val peekState = com.combo.monkeycompanion.rig.PeekFace.State()
    private val tabPath = Path()
    private fun chatBtnX() = ox + boxW - chatBtnR - 4 * d
    private fun chatBtnY() = oy + chatBtnR + 14 * d

    // ---------------- v4.4 smart landings (see brain/Landings) ----------------
    private var landStyle = Landings.Style.SIMPLE
    private var lastLandStyle: Landings.Style? = null
    private var landBeat = 0f
    private var landT = 0f
    private var landBounced = false
    private var landDir = 1f
    private var vineOn = false
    private var vineTopX = 0f

    /** Dropped mid-screen: fall, but first decide how (the character's own random choice). */
    /**
     * v4.5: every fall (dropped by you, or Settai letting go of a wall / ceiling by itself) picks a landing.
     * Only Settai's own Battery saver (Settings) simplifies it; the phone's power-save mode and power cooldowns don't.
     */
    /** Triggers the 1.0-second Tom & Jerry mid-air scare hang at ([x], [y]) followed by a fall to the bottom edge. */
    fun triggerMidScreenCoyoteFall(x: Float = mx, y: Float = my) {
        mx = x.coerceIn(0f, max(0f, sw() - boxW))
        my = y.coerceIn(topY(), max(topY(), ground() - 80 * d))
        beginFall()
        mcOn = false
        coyoteScareTimer = 1.0f
        coyoteScareTriggeredCount++
        landBeat = 0f
        landStyle = Landings.Style.CANNONBALL
        vy = 0f
        say(
            when (character.id) {
                "billa" -> "🙀 *looks down* Uh-oh… no floor!! *gulp*"
                "chittu" -> "🐭😱 YIKES!! Wait a second— nothing under my paws!!"
                else -> "😱 Whoa— hanging in mid-air!!"
            },
            1800L
        )
        if (!running && live && !paused) start()
    }

    private fun beginSmartFall(byUser: Boolean = true, force: Landings.Style? = null, forceWhy: String = "") {
        if (profile.coyoteOnMidScreenDrop && ground() - my > 48 * d && force == null) {
            triggerMidScreenCoyoteFall(mx, my)
            return
        }
        beginFall()
        mcOn = false
        val powersOn = powerSetting() != Superpowers.OFF
        val umbrella = character.fallGravity(wear) < 1f
        val screenH = host?.screenHeight()?.toFloat() ?: Float.MAX_VALUE
        val pick = if (force != null && !saver && ground() - my > 40 * d) Landings.Pick(force, forceWhy)
        else Landings.decide(character.id, umbrella, powersOn, saver, climbs && host != null && !wp, ground() - my, d,
            lastLandStyle, screenH, rushed = afterLand != null).let { if (force != null) it.copy(why = "$forceWhy · ${it.why}") else it }
        val s = pick.style
        landStyle = s; if (s != Landings.Style.SIMPLE) lastLandStyle = s
        landBeat = Landings.beat(s, saver); landT = 0f; landBounced = false
        landDir = if (mx + boxW / 2f < sw() / 2f) 1f else -1f
        landPicked = s; landWhy = pick.why; landBy = if (byUser) "dropped by you" else "let go by itself"
        landFlames = 0; landFxSeen = false
        if (host != null) runCatching {
            com.combo.monkeycompanion.powers.ActionLog.add(context, "Landing picked: ${Landings.label(s)} (${pick.why})",
                "${character.name}, $landBy")
        }
        if (landBeat <= 0f) onLandDecided()
    }

    private fun onLandDecided() {
        landBeat = 0f
        val s = landStyle
        if (s.lines.isNotEmpty() && Random.nextFloat() < 0.7f) say(s.lines.random(), 1800L)
        if (s == Landings.Style.PEEL_SLIDE) controller.face(landDir)
        if (s == Landings.Style.ROCKET_ARC) startArc()
        if (s == Landings.Style.VINE_SWING) {
            // Grab an imaginary vine, swing to the nearer side edge, hold on there.
            falling = false; vy = 0f; landStyle = Landings.Style.SIMPLE
            val left = mx + boxW / 2f < sw() / 2f
            vineTopX = if (left) sw() * 0.18f else sw() * 0.82f
            vineOn = true
            goSurf(screenSurf(if (left) SK.WALL_L else SK.WALL_R), 0.75f, 40f * d) {
                vineOn = false
                controller.onDragEnd(false)
            }
        }
    }

    private fun feetScreen(out: FloatArray): FloatArray { out[0] = winX + ox + boxW / 2f; out[1] = winY + oy + boxH - groundPad; return out }

    /** A few capped particles on the way down (effects window). */
    private fun landParticles(s: Landings.Style) {
        if (saver || !s.fx || Random.nextFloat() > 0.6f) return
        val f = feetScreen(tmpPt)
        val P = com.combo.monkeycompanion.rig.Particles
        when (s) {
            Landings.Style.JETS -> particles.spawn(P.FLAME, f[0] + (Random.nextFloat() - 0.5f) * 14f * d, f[1], (Random.nextFloat() - 0.5f) * 40f * d, 260f * d, 0.3f, 7f * d)
            Landings.Style.SCREAM_JETS -> if (ground() - my < Landings.SAVE_DP * d) particles.spawn(P.FLAME, f[0], f[1], (Random.nextFloat() - 0.5f) * 50f * d, 320f * d, 0.3f, 8f * d)
            Landings.Style.CAPE_FLOAT, Landings.Style.SPEED_DIVE -> particles.spawn(P.SPEED, f[0] + (Random.nextFloat() - 0.5f) * boxW * 0.6f, f[1] - boxH, 0f, -500f * d, 0.25f, 4f * d)
            else -> Unit
        }
    }

    /** Touchdown puff: dust for thuds, smoke for the ninja. */
    private fun landPuff(s: Landings.Style) {
        if (saver) return
        val f = feetScreen(tmpPt)
        val P = com.combo.monkeycompanion.rig.Particles
        val kind = if (s == Landings.Style.NINJA_FLIP) P.SMOKE else P.DUST
        val n = when (s) { Landings.Style.JETS, Landings.Style.CANNONBALL, Landings.Style.SPEED_DIVE, Landings.Style.SCREAM_JETS, Landings.Style.NINJA_FLIP -> 10; else -> 4 }
        repeat(n) {
            val dir = if (it % 2 == 0) 1f else -1f
            particles.spawn(kind, f[0] + dir * Random.nextFloat() * 18f * d, f[1] - Random.nextFloat() * 6f * d,
                dir * (120f + Random.nextFloat() * 240f) * d, -(20f + Random.nextFloat() * 60f) * d, if (kind == P.SMOKE) 0.7f else 0.45f, (6f + Random.nextFloat() * 5f) * d)
        }
    }

    // ---------------- v5 curved power landings (brain/LandingFlight) ----------------
    private var arc: LandingFlight.Plan? = null
    private var arcT = 0f
    private var arcPhase = 0          // 0 fly, 1 hover (Anna), 2 drop (Anna), 3 touchdown pose
    private var arcPhaseT = 0f
    private var arcRot = 0f
    /** v10.14 leftover tilt after a rocket flight, decaying to upright (visual only). */
    private var rotEase = 0f
    private var lastArcPath: LandingFlight.Path? = null
    private var landPicked: Landings.Style? = null
    private var landWhy = ""
    private var landBy = ""
    private var landFlames = 0
    private var landFxSeen = false
    private val arcPt = FloatArray(2)
    private val thrust = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thrustPath = android.graphics.Path()

    private var arcHover = false
    private var arcBank = 1f
    private var selfLand = false
    private var hoverFlames = 0

    /** Fly along a random curve to the box centre ([tx], [ty]) and hover there ([slow] > 1 = lazy patrol). */
    private fun flyTo(tx: Float, ty: Float, slow: Float, why: String?) {
        if (why == null && character.id !in Landings.ARC_IDS) {   // v10.25.1 idle patrol: a gentle glide, no loop, no jets
            val fx = mx; val fy = my
            mx = (tx - boxW / 2f).coerceIn(10 * d, max(10 * d, sw() - boxW - 10 * d)); my = (ty - boxH / 2f).coerceIn(hoverMinY(), ground())
            surf = freeSurf(my); slideFrom(fx, fy, 1.8f); return
        }
        if (swipeTripInsteadOfArc(tx, ty)) return   // v10.25.1: only Anna / Superboy / Rocket Girl fly the looping arc
        val fx = mx; val fy = my
        beginFall()
        mx = fx; my = fy
        landStyle = Landings.Style.ROCKET_ARC; landBeat = 0f; landT = 0f
        val w = sw(); val sh = (host?.screenHeight() ?: height).toFloat()
        val path = LandingFlight.pickPath(lastArcPath).let { if (slow > 1f && (it == LandingFlight.Path.LOOP || it == LandingFlight.Path.SPIRAL)) LandingFlight.Path.SWOOP else it }
        lastArcPath = path
        arc = LandingFlight.plan(path, LandingFlight.finish(character.id), mx + boxW / 2f, my + boxH / 2f, tx, ty, w, sh,
            topY() + boxH / 2f + 4 * d, boxW / 2f, slow = slow)
        arcT = 0f; arcPhase = 0; arcPhaseT = 0f; arcRot = 0f; vy = 0f
        arcHover = true; arcBank = if (slow > 1f) 0.35f else 1f
        if (why != null && host != null) runCatching { com.combo.monkeycompanion.powers.ActionLog.add(context, "Flight: ${path.label}, then hover", "${character.name}, $why") }
    }

    /**
     * v6: a swipe down on a hovering hero ALWAYS flies a big curved path across the screen and then lands (Superboy fist
     * first, Anna jet-brake + thud). Battery saver only trims particles; the flight and the flames stay.
     */
    private fun rocketLanding(v: Float) {
        if (character.id !in Landings.ARC_IDS) { beginSmartFall(); return }   // v10.25.1: no looping arc for the others
        val fx = mx; val fy = my
        beginFall()
        mx = fx; my = fy
        mcOn = false
        landStyle = Landings.Style.ROCKET_ARC; lastLandStyle = landStyle
        landBeat = 0f; landT = 0f; landBounced = false; landFlames = 0; landFxSeen = false
        landPicked = landStyle; landWhy = "you swiped down"; landBy = "dropped by you"
        val w = sw(); val sh = (host?.screenHeight() ?: height).toFloat()
        val fin = LandingFlight.finish(character.id)
        val path = LandingFlight.pickPath(lastArcPath); lastArcPath = path
        val ey = ground() + boxH / 2f - (if (fin == LandingFlight.Finish.HOVER_THUD) LandingFlight.HOVER_DP * d else 0f)
        arc = LandingFlight.planBig(path, fin, mx + boxW / 2f, my + boxH / 2f, ey, w, sh, topY() + boxH / 2f + 4 * d, boxW / 2f)
        arcT = 0f; arcPhase = 0; arcPhaseT = 0f; arcRot = 0f; vy = 0f; arcHover = false; arcBank = 1f
        if (Random.nextFloat() < 0.6f) say(Landings.Style.ROCKET_ARC.lines.randomOrNull() ?: "Touching down!", 1800L)
        if (host != null) runCatching {
            com.combo.monkeycompanion.powers.ActionLog.add(context, "Landing picked: rocket arc (you swiped down: touch down)", "${character.name}, dropped by you")
        }
        if (host != null) runCatching {
            val span = arc?.let { (LandingFlight.xSpan(it) * 100f / w).toInt() } ?: 0
            com.combo.monkeycompanion.powers.ActionLog.add(context, "Swipe down v=${(v / d).toInt()} dp/s -> rocket path ${path.label}",
                "${character.name}: ${"%.1f".format(java.util.Locale.ROOT, arc?.dur ?: 0f)} s, spans $span% of the screen, then ${fin.label}")
        }
    }
    val debugArcPlan get() = arc

    private fun endHoverArc() {
        arc = null; arcRot = 0f; arcHover = false; arcBank = 1f
        falling = false; vy = 0f; landStyle = Landings.Style.SIMPLE
        surf = freeSurf(my.coerceIn(hoverMinY(), ground())); clampToSurf()
        controller.idleNow(2f)
    }

    private fun hoverMinY() = topY() + boxH * 0.12f

    // ---------------- v10.9 Rocket Girl's swipe launch (brain/RocketFlight) ----------------
    private val rocketFly = com.combo.monkeycompanion.brain.RocketFlight(System.nanoTime() xor 0x5EED_0A9L)
    private var rocketFlyOn = false
    private var rocketFaceHold = 0f
    val debugRocketFly get() = rocketFly
    val debugRocketFlyOn get() = rocketFlyOn

    private fun startRocketFly(fvx: Float, fvy: Float, sp: Float) {
        val fx = mx; val fy = my
        beginFall()
        mx = fx; my = fy
        arc = null; arcRot = 0f; landStyle = Landings.Style.SIMPLE; landBeat = 0f
        val power = ((sp / d - ROCKET_SWIPE_DP) / 2600f).coerceIn(0f, 1f)
        rocketFly.launch(mx + boxW / 2f, my + boxH / 2f, fvx, fvy, power, d,
            boxW / 2f + 6 * d, sw() - boxW / 2f - 6 * d, hoverMinY() + boxH / 2f, ground() + boxH / 2f - 30 * d)
        rocketFlyOn = true
        com.combo.monkeycompanion.audio.Sfx.play(context, com.combo.monkeycompanion.audio.Sfx.S.WHOOSH, 0.6f, 0.9f + 0.3f * power)
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if (host != null) runCatching { com.combo.monkeycompanion.powers.ActionLog.add(context, "Rocket launch: ${(sp / d).toInt()} dp/s swipe, power ${"%.2f".format(java.util.Locale.ROOT, power)}", "${character.name}, swiped by you") }
    }

    /** Tests / previews: the same launch a fast swipe makes ([fvx], [fvy] px/s). */
    fun debugRocketLaunch(fvx: Float, fvy: Float) = startRocketFly(fvx, fvy, hypot(fvx, fvy))

    private fun stepRocketFly(dt: Float) {
        rocketFly.step(dt)
        mx = rocketFly.x - boxW / 2f; my = rocketFly.y - boxH / 2f
        // v10.14 no jitter: turn only on a clear, sustained sideways move (hysteresis + 0.15 s hold), never per frame
        val wantFace = if (rocketFly.vx > 140f * d) 1f else if (rocketFly.vx < -140f * d) -1f else controller.facing
        var flipped = false
        if (wantFace != controller.facing) { rocketFaceHold += dt; if (rocketFaceHold > 0.15f) { controller.face(wantFace); rocketFaceHold = 0f; flipped = true } } else rocketFaceHold = 0f
        // the rocket's nose follows the velocity: rotate the whole rider so the tilted rocket lines up with it
        val tilt = com.combo.monkeycompanion.rig.RocketGeom.TILT + ROCKET_FLY_LEAN
        val base = if (controller.facing >= 0f) tilt else 180f - tilt
        // v10.14: unwrap relative to the CURRENT tilt (straight-up flight sat on the +-180 seam and flipped -80 <-> +80 every frame)
        var r = rocketFly.noseDeg - base - arcRot
        while (r > 180f) r -= 360f
        while (r < -180f) r += 360f
        r += arcRot
        // (clamped so a steep dive never turns her upside down; within it the rocket's nose is along the velocity)
        val want = r.coerceIn(-80f, 80f)
        // v10.14: eased and rate-limited (no per-frame snaps); on a facing flip the mirrored rider takes the new angle at once
        arcRot = if (flipped) want else arcRot + ((want - arcRot) * min(1f, dt * 14f)).coerceIn(-ROT_STEP_MAX * dt * 60f, ROT_STEP_MAX * dt * 60f)
        if (!rocketFly.active) endRocketFly()
    }

    private fun endRocketFly() {
        rocketFlyOn = false; rocketFly.cancel()
        rotEase = arcRot   // v10.14: the tilt eases back upright after landing instead of snapping
        arcRot = 0f; falling = false; vy = 0f; landStyle = Landings.Style.SIMPLE
        surf = freeSurf(my.coerceIn(hoverMinY(), ground())); clampToSurf()
        controller.idleNow(2f)
    }

    private fun rocketFlyPose(p: Pose) {
        val th = rocketFly.thrust
        p.rbThrust = th
        p.shadow = 0f; p.bodyRot = ROCKET_FLY_LEAN; p.bodyY += -6f
        p.armLRaise = 0f; p.armRRaise = 0f; p.sparkler = 0f
        p.legLSwing = 26f + 10f * sin(t * 7f); p.legRSwing = 16f + 10f * sin(t * 7f + 1.3f)
        p.capeWind = 1f
        if (rocketFly.phase == com.combo.monkeycompanion.brain.RocketFlight.Phase.GLIDE) { p.eyesHappy = 1f; p.mouthSmile = 1f; p.mouthOpen = 0.2f }
        else { p.eyeOpen = max(p.eyeOpen, 1.15f); p.mouthSmile = 0.9f; p.mouthOpen = 0.35f + 0.3f * th }
    }

    /** Released mid-air: stay there (drifting back on-screen if it was let go too close to an edge). */
    private fun hoverHere() {
        val fx = mx; val fy = my
        surf = freeSurf(my.coerceIn(hoverMinY(), ground()))
        mx = mx.coerceIn(10 * d, max(10 * d, sw() - boxW - 10 * d)); my = surf.line
        if (abs(mx - fx) > 1f || abs(my - fy) > 1f) slideFrom(fx, fy, 0.45f)
        if (Random.nextFloat() < 0.35f) say(listOf("Hovering ✓", "I'll just float here 😎", "Swipe me down to land 👇").random(), 1600L)
    }

    /** Idle roaming for hovering heroes: a slow floating patrol to somewhere else on the screen. */
    private fun patrolFlight() {
        val w = sw()
        val tx = boxW / 2f + 10 * d + Random.nextFloat() * (w - boxW - 20 * d)
        val ty = hoverMinY() + boxH / 2f + Random.nextFloat() * (ground() - hoverMinY()) * 0.85f
        flyTo(tx, ty, 1.9f, null)
    }

    private fun hoverRoam(r: Float): Boolean {
        val idleLong = SystemClock.elapsedRealtime() - lastInteract > 4 * 60_000L
        return when {
            hoverOn && idleLong && r < 0.15f -> { selfLand = true; controller.drop(); true }
            r < profile.airPatrolChance -> { controller.play(MonkeyState.FALLING); patrolFlight(); true }   // quiet take-off, no "I'm falling" line
            r < profile.airPatrolChance + profile.airDanceChance -> { controller.danceNow(Random.nextFloat() < 0.5f); true }   // v10.1: mid-air dance
            else -> false                                  // glide along at this height
        }
    }

    private fun hoverPose(p: Pose) {
        p.shadow = 0f
        p.bodyY += 3f * sin(t * 2.4f)
        p.legLSwing *= 0.25f; p.legRSwing *= 0.25f; p.legLLift = max(p.legLLift, 3f); p.legRLift = max(p.legRLift, 5f)
        if (character.usesCapeFlight) { p.cape = max(p.cape, 1f); p.capeWind = max(p.capeWind, 0.3f + 0.15f * sin(t * 3.1f)) }
        if (character.usesHoverJets) {
            p.jets = max(p.jets, 0.75f)
            p.palmGlow = max(p.palmGlow, 0.85f)
            p.armLRaise = max(p.armLRaise, 28f); p.armRRaise = max(p.armRRaise, 28f)
            p.armLElbow = -6f; p.armRElbow = -6f
        }
    }

    /**
     * v9.3: Superboy & Armour Anna (Ironman) Edge-Collision Thrust Loss & Crash-Landing!
     * When hitting the screen border while flying, thrust drops (`edgeThrustMultiplier = 0.18f`),
     * sparks sputter out, and they plummet to the bottom display for a comic crash-landing with spinning stars!
     */
    fun triggerEdgeCollisionWhileFlying(forceRandomPass: Boolean = true): Boolean {
        val isFlyingHero = profile.edgeCrashOnFlight || character.usesHoverJets || character.usesCapeFlight
        if (!isFlyingHero) return false
        if (!forceRandomPass && (edgeCrashCooldown > 0f || Random.nextFloat() > 0.45f)) return false

        if (powerOn) {
            powerOn = false
            flight.cancel()
        }
        arc = null
        arcRot = 0f
        arcHover = false
        falling = false
        climber.deactivate()
        tweenT = -1f

        if (my > ground() - 60 * d) {
            my = (ground() * 0.35f).coerceAtLeast(topY() + 24 * d)
        }
        edgeThrustFailurePhase = 1
        BSN.record("I bumped into the screen edge", BSN.Feeling.OUCH)   // v10.17 small ouch
        feelTouch(com.combo.monkeycompanion.ai.react.ReactKind.BUMP, force = 0.8f,
            extra = if (mx + boxW / 2f < sw() / 2f) "hit the left edge" else "hit the right edge", doing = "flying")   // v10.18
        edgeThrustFailureTimer = 0.42f
        edgeThrustMultiplier = 0.18f
        edgeCrashRot = if (mx + boxW / 2f < sw() / 2f) -22f else 22f
        edgeCrashCooldown = 9.0f
        edgeCrashLandingCount++
        vy = 0f

        if (!saver) {
            val cx = winX + ox + boxW / 2f
            val cy = winY + oy + boxH / 2f
            repeat(10) {
                val ang = Random.nextFloat() * 6.283f
                val sp = (140f + Random.nextFloat() * 240f) * d
                particles.spawn(com.combo.monkeycompanion.rig.Particles.SPARK, cx, cy, kotlin.math.cos(ang) * sp, sin(ang) * sp, 0.45f, 5.5f * d)
            }
        }
        say(
            when (character.id) {
                "armour" -> listOf(
                    "⚡💥 Ouch! Clipped the screen edge — repulsor thrust dropping to 15%!!",
                    "🚨 Suit border impact! Thrusters sputtering — brace for crash-landing!!"
                ).random()
                "superboy" -> listOf(
                    "💥 Who put the screen edge there?! Cape stall — losing altitude!!",
                    "⚡ Bonked the screen border! Power dropping — going down!!"
                ).random()
                else -> "⚡💥 Edge impact! Flight thrusters sputtering — going down!!"
            },
            2400L
        )
        if (!running && live && !paused) start()
        return true
    }

    /** Phase 1: sputtering at the edge as thrust drops; 2: tumbling plunge to the bottom; 3: crash-landed with stars (1.6 s). */
    private fun stepEdgeCrashLanding(dt: Float) {
        val plunging = edgeThrustFailurePhase == 2
        val ev = pb.stepEdgeCrash(dt, t, ground(), sw() - boxW, controller.facing, d, physics)
        if (plunging && !saver && Random.nextFloat() < 0.65f) {
            val f = feetScreen(tmpPt)
            particles.spawn(com.combo.monkeycompanion.rig.Particles.SMOKE, f[0], f[1], 0f, -60f * d, 0.55f, 7f * d)
        }
        when (ev) {
            StepEvent.CRASH_TOUCHDOWN -> {
                surf = screenSurf(SK.FLOOR)
                clampToSurf()
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                landPuff(Landings.Style.CANNONBALL)
                say(
                    when (character.id) {
                        "armour" -> "💫🤕 *THUD!* Crash-landed at the bottom! Rebooting suit thrusters…"
                        "superboy" -> "💫🤕 *BOOM!* Okay… the floor won that round! Shaking off the stars…"
                        else -> "💫🤕 Crash-landed! Rebooting flight power…"
                    },
                    2200L
                )
            }
            StepEvent.CRASH_RECOVERED -> {
                surf = screenSurf(SK.FLOOR)
                clampToSurf()
                controller.play(MonkeyState.HAPPY_PAT, 1.2f)
            }
            else -> Unit
        }
    }

    private fun applyEdgeCrashPose(p: Pose) {
        when (edgeThrustFailurePhase) {
            1, 2 -> {
                p.shadow = 0f
                p.eyeOpen = 1.5f
                p.mouthOpen = 0.85f
                p.mouthSmile = -0.5f
                p.armLRaise = 145f + 20f * sin(t * 36f)
                p.armRRaise = 145f - 20f * sin(t * 36f)
                p.legLLift = 14f
                p.legRLift = 14f
                p.jets = if (character.usesHoverJets) edgeThrustMultiplier else 0f
                p.palmGlow = if (character.usesHoverJets) edgeThrustMultiplier else 0f
                p.cape = if (character.usesCapeFlight) 1f else 0f
                p.capeWind = 0.9f
            }
            3 -> {
                // Funny slumped crash-landed crater pose with dizzy head sway
                p.squashY = 0.78f
                p.squashX = 1.20f
                p.bodyY = 11f
                p.bodyRot = 24f * sin(t * 6f)
                p.headRot = 18f * sin(t * 9f)
                p.eyeOpen = 0.45f
                p.mouthOpen = 0.4f
                p.mouthSmile = -0.35f
                p.armLRaise = 42f
                p.armRRaise = 55f
                p.legLSwing = 22f
                p.legRSwing = -22f
                p.soot = 0.75f
                p.jets = 0f
                p.palmGlow = 0f
            }
        }
    }

    private fun startArc() {
        val h = host
        val w = sw(); val sh = (h?.screenHeight() ?: height).toFloat()
        val fin = LandingFlight.finish(character.id)
        val path = LandingFlight.pickPath(lastArcPath); lastArcPath = path
        val cx = mx + boxW / 2f; val cy = my + boxH / 2f
        // Land somewhere else across the screen (random side and distance).
        val lo = boxW / 2f + 8 * d; val hi = w - boxW / 2f - 8 * d
        var ex = lo + Random.nextFloat() * (hi - lo)
        if (abs(ex - cx) < w * 0.3f) ex = if (cx < w / 2f) (cx + w * (0.3f + Random.nextFloat() * 0.3f)).coerceAtMost(hi) else (cx - w * (0.3f + Random.nextFloat() * 0.3f)).coerceAtLeast(lo)
        val ey = ground() + boxH / 2f - (if (fin == LandingFlight.Finish.HOVER_THUD) LandingFlight.HOVER_DP * d else 0f)
        arc = LandingFlight.plan(path, fin, cx, cy, ex, ey, w, sh, topY() + boxH / 2f + 4 * d, boxW / 2f)
        arcT = 0f; arcPhase = 0; arcPhaseT = 0f; arcRot = 0f; vy = 0f
    }

    private fun stepArc(dt: Float) {
        val p = arc ?: return
        landT += dt
        when (arcPhase) {
            0 -> {
                arcT += dt
                val k = arcT / p.dur
                val u = LandingFlight.ease(p.finish, k)
                LandingFlight.at(p, u, arcPt)
                val nx = arcPt[0] - boxW / 2f
                if (abs(nx - mx) > 0.3f) controller.face(if (nx > mx) 1f else -1f)
                mx = nx; my = arcPt[1] - boxH / 2f
                arcRot = LandingFlight.bodyRot(p, u) * arcBank
                arcTrail()
                // v9.3: If Superboy or Armour Anna hits the screen edge while flying in roam mode, random thrust drop & crash-landing!
                if (arcHover && controller.roam && edgeCrashCooldown <= 0f && u in 0.18f..0.85f &&
                    (mx <= 10 * d || mx >= sw() - boxW - 10 * d || my <= topY() + 14 * d)
                ) {
                    if (triggerEdgeCollisionWhileFlying(forceRandomPass = false)) return
                }
                if (k >= 1f) { arcRot = 0f; if (arcHover) endHoverArc() else if (p.finish == LandingFlight.Finish.HOVER_THUD) arcPhase(1) else touchdown() }
            }
            1 -> {   // Anna: jets braking, hovering with a little bob
                arcPhaseT += dt
                my = p.y1 - boxH / 2f + 4f * d * sin(arcPhaseT * 14f)
                arcTrail()
                if (arcPhaseT >= LandingFlight.HOVER_SECS) { arcPhase(2); vy = 0f }
            }
            2 -> {   // jets off: drop like a suit of armour
                vy += 4200f * d * dt; my += vy * dt
                if (my >= ground()) touchdown()
            }
            3 -> {
                arcPhaseT += dt
                if (p.finish == LandingFlight.Finish.CRASH) {
                    // Tumbling skid that slows down, then sits up dizzy.
                    val k = (arcPhaseT / 0.6f).coerceIn(0f, 1f)
                    mx = (mx + p.dir * 520f * d * (1f - k) * dt).coerceIn(0f, max(0f, sw() - boxW))
                    arcRot = if (k < 1f) p.dir * 720f * k else 0f
                }
                if (arcPhaseT >= LandingFlight.touchSecs(p.finish)) finishArc()
            }
        }
    }

    private fun arcPhase(n: Int) { arcPhase = n; arcPhaseT = 0f }

    private fun touchdown() {
        val p = arc ?: return
        my = ground(); vy = 0f; arcRot = 0f
        arcPhase(3)
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if (!saver) {
            val f = feetScreen(tmpPt)
            val P = com.combo.monkeycompanion.rig.Particles
            val n = if (p.finish == LandingFlight.Finish.FIST) 16 else 12
            repeat(n) {
                val dir = if (it % 2 == 0) 1f else -1f
                particles.spawn(P.DUST, f[0] + dir * Random.nextFloat() * 20f * d, f[1] - Random.nextFloat() * 6f * d,
                    dir * (160f + Random.nextFloat() * 320f) * d, -(30f + Random.nextFloat() * 80f) * d, 0.5f, (7f + Random.nextFloat() * 6f) * d)
            }
            if (p.finish == LandingFlight.Finish.FIST) particles.spawn(P.RING, f[0], f[1], 0f, 0f, 0.5f, 30f * d)
        }
        when (p.finish) {
            LandingFlight.Finish.FIST -> if (Random.nextFloat() < 0.7f) say(listOf("Hero landing! 👊", "Nailed it 💥", "Superhero pose: ✓").random(), 1800L)
            LandingFlight.Finish.HOVER_THUD -> if (Random.nextFloat() < 0.7f) say(listOf("THUD. Suit's fine 🛡️", "Touchdown confirmed 🛬", "Gentle as a 200 kg feather").random(), 1800L)
            LandingFlight.Finish.CRASH -> {
                if (host != null && !wp) com.combo.monkeycompanion.mind.MindHub.event(com.combo.monkeycompanion.mind.MindEvent.CRASH_LANDING)
                say(listOf("I'm okay!! 🤕", "Landing… mostly 😵‍💫", "Who moved the floor?!", "Ta-da! (ow) 🤕").random(), 2000L)
            }
        }
        syncFx()
    }

    private fun finishArc() {
        arc = null; arcRot = 0f
        falling = false; vy = 0f
        surf = screenSurf(SK.FLOOR); my = ground(); clampToSurf()
        logPlayed(null)
        landStyle = Landings.Style.SIMPLE
        controller.onLanded()
        afterLand?.let { a -> afterLand = null; ui.postDelayed({ a() }, 480) }
    }

    /** Exhaust / speed-streak particles in the effects window (extra; the flames themselves are drawn in our window). */
    private fun arcTrail() {
        if (saver || Random.nextFloat() > 0.7f) return
        val P = com.combo.monkeycompanion.rig.Particles
        val cx = winX + ox + boxW / 2f; val cy = winY + oy + boxH / 2f
        val back = Math.toRadians((arcRot + 90f).toDouble())
        val bx = cx + (kotlin.math.cos(back) * boxH * 0.45f).toFloat(); val by = cy + (kotlin.math.sin(back) * boxH * 0.45f).toFloat()
        if (character.usesCapeFlight && !character.usesHoverJets) particles.spawn(P.SPEED, bx, by, 0f, 0f, 0.25f, 4f * d)
        else { particles.spawn(P.FLAME, bx, by, 0f, 0f, 0.3f, 8f * d); if (Random.nextFloat() < 0.4f) particles.spawn(P.SMOKE, bx, by, 0f, -30f * d, 0.8f, 9f * d) }
    }

    private fun arcPose(p: Pose) {
        val a = arc ?: return
        when (arcPhase) {
            0 -> LandingPoses.fall(p, Landings.Style.ROCKET_ARC, landT)
            1 -> { LandingPoses.fall(p, Landings.Style.JETS, landT); p.shadow = 0.4f }
            2 -> { LandingPoses.fall(p, Landings.Style.CANNONBALL, landT); p.eyeOpen = 1.3f }
            else -> when (a.finish) {
                LandingFlight.Finish.FIST -> {   // one knee down, fist on the floor, other arm back
                    val k = (arcPhaseT / 0.25f).coerceIn(0f, 1f)
                    p.squashY = 1f - 0.16f * k; p.squashX = 1f + 0.08f * k; p.bodyY = 8f * k
                    p.armRRaise = 8f; p.armRElbow = 0f; p.armLRaise = 120f; p.armLElbow = -30f
                    p.legLLift = 16f * k; p.legRSwing = 18f * k; p.mouthSmile = 1f; p.browAngle = 6f; p.lookY = 1f
                }
                LandingFlight.Finish.HOVER_THUD -> { val k = 1f - (arcPhaseT / 0.55f).coerceIn(0f, 1f); p.squashY = 1f - 0.2f * k; p.squashX = 1f + 0.12f * k; p.legLLift = 10f * k; p.legRLift = 10f * k }
                LandingFlight.Finish.CRASH -> {
                    if (arcPhaseT < 0.6f) { p.legLLift = 18f; p.legRLift = 18f; p.armLRaise = 120f; p.armRRaise = 120f; p.mouthOpen = 0.8f; p.eyeOpen = 1.5f }
                    else { p.eyeOpen = 0.35f; p.mouthOpen = 0.3f; p.mouthSmile = -0.2f; p.headRot = 10f * sin(arcPhaseT * 9f); p.bodyRot = 4f * sin(arcPhaseT * 9f) }
                }
            }
        }
    }

    /**
     * Power-landing thrust drawn in Settai's own window (rotated with the body): leg-fire / boot jets for the monkey and
     * Anna, speed streaks for Superboy. Always visible, effects window or not (v5 fix: on some phones the power landings
     * looked like plain falls because their only visible part lived in the effects window).
     */
    /** v10.25: only bodies with a jet / rocket power ever show jets or thrust (Anna, Superboy's landing kit, Rocket Girl, Kutti Satan if a jet power is on). */
    private fun hasJetPower(): Boolean = character.id == "armour" || character.id == "superboy" || character.id == "rocketgirl" ||
        // v10.25.1: Kutti Satan only while his rocket / jet power is actually firing (Angel never)
        (character.id == "devil" && powerOn && controller.anim.power?.id.let { it == "jets" || it == "rocket" || it == "boost" }) ||
        (com.combo.monkeycompanion.character.CustomCompanionRegistry.byId(character.id) != null && character.usesHoverJets)
    private fun drawLandingThrust(c: Canvas) {
        if (saver && !hovers) return
        val s = landStyle
        val ph = arcPhase
        val hoverJets = hoverOn && character.usesHoverJets
        val jets = hoverJets || (falling && s == Landings.Style.ROCKET_ARC && arc != null && ph <= 1 && (character.usesHoverJets || !character.usesCapeFlight)) ||
            s == Landings.Style.JETS || (s == Landings.Style.SCREAM_JETS && ground() - my < Landings.SAVE_DP * d)
        val streaks = falling && ((s == Landings.Style.ROCKET_ARC && arc != null && ph == 0 && character.usesCapeFlight) || s == Landings.Style.SPEED_DIVE || s == Landings.Style.CAPE_FLOAT)
        val cx = ox + boxW / 2f; val cy = oy + boxH / 2f
        if (arcRot + rotEase + shockSpin != 0f) c.rotate(arcRot + rotEase + shockSpin, cx, cy)      // the body is drawn in the same rotated frame (restored by drawMonkey)
        if (!jets && !streaks) return
        if (jets && !hasJetPower()) return   // v10.25: no thrust for non-jet characters (surfer, Angel, Kutti Satan…)
        if (jets && profile.ridesProp) return   // v10.10 Rocket Girl: no boot jets / blast cones; her rocket's spark fountain is the exhaust
        if (!hoverJets) landFlames++ else hoverFlames++
        if (fxHosted) landFxSeen = true
        val fy = oy + boxH - groundPad
        if (jets) {
            val boost = if (hoverJets) 0.72f + 0.14f * sin(t * 23f) + 0.06f * sin(t * 61f) else if (ph == 1 || s == Landings.Style.SCREAM_JETS) 1.7f else 1.3f
            for (side in intArrayOf(-1, 1)) {
                val fx = cx + side * 9f * rigScale
                val len = (34f + 12f * sin(t * 40f + side)) * rigScale * boost
                val wdt = 9f * rigScale
                thrust.style = Paint.Style.FILL
                thrust.shader = android.graphics.LinearGradient(fx, fy, fx, fy + len,
                    intArrayOf(Color.rgb(0xFF, 0xF4, 0xB0), Color.rgb(0xFF, 0x9A, 0x1F), Color.argb(0, 0xE8, 0x3A, 0x10)), floatArrayOf(0f, 0.45f, 1f),
                    android.graphics.Shader.TileMode.CLAMP)
                thrustPath.reset(); thrustPath.moveTo(fx - wdt, fy); thrustPath.quadTo(fx - wdt * 0.6f, fy + len * 0.6f, fx, fy + len)
                thrustPath.quadTo(fx + wdt * 0.6f, fy + len * 0.6f, fx + wdt, fy); thrustPath.close()
                c.drawPath(thrustPath, thrust)
                thrust.shader = null
            }
            if (character.usesHoverJets) {
                // 1. Hand Repulsor Thruster Plumes + Palm Discs (both hands, Iron-style flight stabilization)
                for (left in booleanArrayOf(true, false)) {
                    rig.handPoint(left, tmpA)
                    val hx = tmpA[0]; val hy = tmpA[1]
                    val len = (24f + 9f * sin(t * 47f + (if (left) 1f else 2f))) * rigScale * boost
                    val wdt = 6.2f * rigScale
                    thrust.style = Paint.Style.FILL
                    thrust.shader = android.graphics.LinearGradient(hx, hy, hx, hy + len,
                        intArrayOf(Color.WHITE, Color.rgb(0x3D, 0xF5, 0xFF), Color.argb(0, 0xFF, 0x98, 0x00)), floatArrayOf(0f, 0.5f, 1f),
                        android.graphics.Shader.TileMode.CLAMP)
                    thrustPath.reset(); thrustPath.moveTo(hx - wdt, hy); thrustPath.quadTo(hx - wdt * 0.6f, hy + len * 0.6f, hx, hy + len)
                    thrustPath.quadTo(hx + wdt * 0.6f, hy + len * 0.6f, hx + wdt, hy); thrustPath.close()
                    c.drawPath(thrustPath, thrust)
                    thrust.shader = null
                    thrust.color = Color.argb(210, 0x80, 0xF8, 0xFF)
                    c.drawCircle(hx, hy, 5.2f * rigScale, thrust)
                }
                // 2. Chest Arc-Reactor / Unibeam Thrust Plume & Glow
                tmpA[0] = 0f; tmpA[1] = -32f
                rig.hips.mapPoints(tmpA)
                val chX = tmpA[0]; val chY = tmpA[1]
                val cLen = (26f + 8f * sin(t * 53f)) * rigScale * boost
                val cWdt = 7.5f * rigScale
                thrust.style = Paint.Style.FILL
                thrust.shader = android.graphics.LinearGradient(chX, chY, chX, chY + cLen,
                    intArrayOf(Color.WHITE, Color.rgb(0x3D, 0xF5, 0xC8), Color.argb(0, 0x00, 0xB0, 0xFF)), floatArrayOf(0f, 0.45f, 1f),
                    android.graphics.Shader.TileMode.CLAMP)
                thrustPath.reset(); thrustPath.moveTo(chX - cWdt, chY); thrustPath.quadTo(chX - cWdt * 0.5f, chY + cLen * 0.6f, chX, chY + cLen)
                thrustPath.quadTo(chX + cWdt * 0.5f, chY + cLen * 0.6f, chX + cWdt, chY); thrustPath.close()
                c.drawPath(thrustPath, thrust)
                thrust.shader = null
                thrust.color = Color.argb(170, 0x3D, 0xF5, 0xFF)
                c.drawCircle(chX, chY, 10f * rigScale, thrust)
            }
        } else {
            thrust.shader = null; thrust.style = Paint.Style.STROKE; thrust.strokeCap = Paint.Cap.ROUND
            thrust.strokeWidth = 3f * d; thrust.color = Color.argb(170, 0xFF, 0xFF, 0xFF)
            for (k in 0 until 5) {
                val x = cx + (k - 2) * 10f * rigScale; val y0 = fy + (6f + (k % 2) * 10f) * rigScale + ((t * 900f + k * 37f) % 30f)
                c.drawLine(x, y0, x, y0 + 40f * rigScale, thrust)
            }
        }
    }

    /** Settings › Recent actions: what actually played (vs the pick), with the reason when they differ. */
    private fun logPlayed(fallback: String?) {
        val picked = landPicked ?: return
        landPicked = null
        if (host == null) return
        val a = arc
        val played = when {
            fallback != null -> "nothing (${fallback})"
            picked == Landings.Style.ROCKET_ARC && a != null -> "rocket arc · ${a.path.label}, ${"%.1f".format(a.dur)} s, ${a.finish.label}"
            else -> Landings.label(picked)
        }
        val shown = when {
            picked == Landings.Style.SIMPLE -> ""
            saver -> " · Settai battery saver: no flames"
            landFlames > 0 -> " · flames/streaks drawn in Settai's window ($landFlames frames)" + if (landFxSeen) ", effects window on" else ", effects window off"
            picked.power -> " · no thrust drawn"
            else -> ""
        }
        runCatching { com.combo.monkeycompanion.powers.ActionLog.add(context, "Landing played: $played", "${character.name}, picked ${Landings.label(picked)}$shown") }
    }

    // ---------------- v5 Mask Man: clumsy climbing and hanging (front view; he has no climbing profile) ----------------
    private var mcOn = false
    private var mcSide = -1
    private var mcHang = false
    private var mcT = 0f
    private var mcSlipT = -1f
    private var mcHangFor = 0f
    private var mcNextLine = 0f

    private fun startMaskClimb(side: Int) {
        mcOn = true; mcSide = side; mcHang = false; mcT = 0f; mcSlipT = -1f; mcNextLine = 1.2f
        falling = false; climber.deactivate(); tweenT = -1f
        val fx = mx; val fy = my
        mx = if (side < 0) 0f else max(0f, sw() - boxW)
        surf = freeSurf(my); slideFrom(fx, fy, 0.25f)
        controller.idleNow(999f); controller.face(side.toFloat())
        if (Random.nextFloat() < 0.6f) say(listOf("Climbing! How hard can it be?", "Like a ninja. A tired ninja.", "Don't watch, it's a secret technique").random(), 1800L)
    }

    private fun startMaskHang() {
        mcOn = true; mcHang = true; mcT = 0f; mcSlipT = -1f; mcNextLine = 0.8f
        mcHangFor = 4f + Random.nextFloat() * 3f
        falling = false; climber.deactivate(); tweenT = -1f
        val fx = mx; val fy = my
        my = topY() + boxH * 0.22f; surf = freeSurf(my); slideFrom(fx, fy, 0.25f)
        controller.idleNow(999f)
    }

    private fun stepMaskClimb(dt: Float) {
        if (!mcOn) return
        mcT += dt
        mcNextLine -= dt
        if (mcHang) {
            if (mcNextLine <= 0f) {
                mcNextLine = 2.6f
                say(if (character.fallGravity(wear) < 1f) listOf("Umbrella handle: best invention ever ☂️", "Hanging in there. Literally.", "Who built this ceiling so high?").random()
                    else listOf("One hand… arm… dying 😩", "My arm! My beautiful arm!", "Somebody bring a ladder!", "I meant to do this. Totally.").random(), 2200L)
            }
            surf = freeSurf(my)
            if (mcT >= mcHangFor) { mcOn = false; controller.drop() }
            return
        }
        if (mcSlipT >= 0f) {                     // slipping down, then a scramble to recover
            mcSlipT += dt
            if (mcSlipT < 0.45f) my = min(ground(), my + 420f * d * dt)
            if (mcSlipT > 0.9f) mcSlipT = -1f
        } else {
            my -= (if (sin(mcT * 7f) > 0f) 70f else 12f) * d * dt          // shimmy, pause, shimmy
            if (mcNextLine <= 0f) {
                mcNextLine = 1.1f + Random.nextFloat()
                val r = Random.nextFloat()
                if (r < 0.35f) { mcSlipT = 0f; say(listOf("Whoa whoa WHOA—", "Slippery wall!! 😱", "Nope— okay— got it!").random(), 1500L) }
                else if (r < 0.45f) { mcOn = false; say("I'm a lover, not a climber 😅", 1600L); controller.drop(); return }
            }
        }
        surf = freeSurf(my)
        if (my <= topY() + boxH * 0.25f) startMaskHang()
    }

    private fun maskClimbPose(p: Pose) {
        p.shadow = 0f
        if (mcHang) {
            val umb = character.fallGravity(wear) < 1f
            p.armRRaise = 178f; p.armRElbow = 0f
            p.armLRaise = if (umb) 20f + 10f * sin(t * 2f) else 120f + 40f * sin(t * 5f); p.armLElbow = -30f
            p.legLSwing = 10f * sin(t * 2.2f); p.legRSwing = 10f * sin(t * 2.2f + 0.4f)
            p.bodyRot = 5f * sin(t * 2.2f); p.mouthOpen = if (umb) 0.2f else 0.5f; p.mouthSmile = if (umb) 0.5f else -0.5f
            p.browAngle = if (umb) 0f else -10f
            return
        }
        val k = sin(mcT * 7f)
        p.bodyRot = -mcSide * 8f
        p.armLRaise = 150f + 25f * k; p.armRRaise = 150f - 25f * k; p.armLElbow = -40f; p.armRElbow = -40f
        p.legLLift = 10f + 8f * k; p.legRLift = 10f - 8f * k
        if (mcSlipT >= 0f) { p.eyeOpen = 1.6f; p.mouthOpen = 0.9f; p.armLRaise = 175f; p.armRRaise = 175f; p.bodyRot = -mcSide * 16f }
    }

    /** The umbrella handle hooked over the top edge (or nothing: he hangs by one hand). */
    private fun drawMaskHang(c: Canvas) {
        if (!mcOn || !mcHang || character.fallGravity(wear) >= 1f) return
        rig.handPoint(true, tmpA)
        val top = topY() - winY
        if (tmpA[1] <= top) return
        thrust.shader = null; thrust.style = Paint.Style.STROKE; thrust.strokeCap = Paint.Cap.ROUND
        thrust.color = Color.rgb(0x5A, 0x3A, 0x1E); thrust.strokeWidth = 4f * d
        c.drawLine(tmpA[0], tmpA[1], tmpA[0], top + 8 * d, thrust)
        c.drawArc(tmpA[0], top + 2 * d, tmpA[0] + 14 * d, top + 14 * d, 180f, 180f, false, thrust)
    }

    /** Mask Man's crow carrying him by the collar. */
    private fun drawCrow(c: Canvas) {
        if (!falling || landStyle != Landings.Style.CROW_CARRY) return
        rig.headTop(tmpA)
        val x = tmpA[0]; val y = tmpA[1] - 6 * rigScale
        val s = rigScale; val flap = sin(t * 26f)
        thrust.shader = null; thrust.style = Paint.Style.FILL; thrust.color = Color.rgb(0x22, 0x22, 0x2A)
        c.drawOval(x - 16 * s, y - 12 * s, x + 16 * s, y + 6 * s, thrust)                     // body
        c.drawCircle(x + 15 * s * controller.facing, y - 10 * s, 8 * s, thrust)               // head
        thrustPath.reset(); thrustPath.moveTo(x - 6 * s, y - 6 * s); thrustPath.lineTo(x - 38 * s, y - 6 * s - 26 * s * flap); thrustPath.lineTo(x - 2 * s, y - 2 * s); thrustPath.close()
        c.drawPath(thrustPath, thrust)
        thrustPath.reset(); thrustPath.moveTo(x + 6 * s, y - 6 * s); thrustPath.lineTo(x + 38 * s, y - 6 * s - 26 * s * flap); thrustPath.lineTo(x + 2 * s, y - 2 * s); thrustPath.close()
        c.drawPath(thrustPath, thrust)
        thrust.color = Color.rgb(0xF2, 0xB2, 0x2E)
        val bx = x + 22 * s * controller.facing
        thrustPath.reset(); thrustPath.moveTo(bx, y - 12 * s); thrustPath.lineTo(bx + 9 * s * controller.facing, y - 9 * s); thrustPath.lineTo(bx, y - 7 * s); thrustPath.close(); c.drawPath(thrustPath, thrust)
        thrust.color = Color.WHITE; c.drawCircle(x + 17 * s * controller.facing, y - 12 * s, 2.2f * s, thrust)
        thrust.color = Color.rgb(0x5A, 0x3A, 0x1E); thrust.style = Paint.Style.STROKE; thrust.strokeWidth = 2.5f * s       // feet gripping the collar
        c.drawLine(x - 4 * s, y + 4 * s, x - 6 * s, y + 14 * s, thrust); c.drawLine(x + 4 * s, y + 4 * s, x + 6 * s, y + 14 * s, thrust)
    }

    private fun drawVine(c: Canvas) {
        rig.handPoint(false, tmpA)
        LandingFx.drawVine(c, vineTopX, topY(), tmpA[0] + winX, tmpA[1] + winY, rigScale)
    }

    /** Cape behind the body (the monkey's Super Banana style; heroes wear their own). */
    private fun drawLandingBack(c: Canvas) {
        if (!falling || landBeat > 0f || saver) return
        if (landStyle == Landings.Style.CAPE_FLOAT && character === com.combo.monkeycompanion.character.MonkeyCharacter) {
            rig.headTop(head)
            LandingFx.drawCape(c, head[0], head[1] + (2 * com.combo.monkeycompanion.rig.MonkeyRig.HEAD_R + 16f) * rigScale, rigScale, t)
        }
    }

    /** Leaf parachute above the hands (the Mask Man's umbrella is part of his outfit). */
    private fun drawLandingFront(c: Canvas) {
        drawMaskHang(c)
        if (!saver) drawCrow(c)
        if (!falling || landBeat > 0f || saver) return
        if (landStyle == Landings.Style.PARACHUTE && character.fallGravity(wear) >= 1f) {
            rig.handPoint(false, tmpA); rig.handPoint(true, tmpB)
            LandingFx.drawLeaf(c, (tmpA[0] + tmpB[0]) / 2f, minOf(tmpA[1], tmpB[1]), rigScale, t)
        }
    }

    // ---------------- v4.4 phone moments (see brain/PhoneMoments) ----------------
    private var moment: PhoneMoments.Moment? = null
    private var momentT = 0f
    private var momentLen = 1f
    private var momentFx = false
    private var momentStyle = PhoneMoments.ChargeStyle.ZAP
    private var momentTarget = floatArrayOf(0f, 0f)
    private val momentLast = HashMap<PhoneMoments.Moment, Long>()

    private fun momentFxOn() = moment == PhoneMoments.Moment.CHARGE && momentFx && host != null && !wp

    // ---- v6: the charger moment ALWAYS plays (user-initiated): only DND and calls block it; busy states defer it. ----
    private var chargePendingAt = 0L
    private var chargeDeferWhy = ""
    private var chargeRetryT = 0f
    private var chargeTrip = false
    private var chargeBack = false
    private var chargeHomeX = 0f; private var chargeHomeY = 0f
    private var chargeHomeSurf: Surf? = null
    private var chargePerchX = 0f; private var chargePerchY = 0f

    /** Public for the real-path test (the broadcast receiver calls this). */
    fun onChargerPlugged() {
        batteryLow = false; watchFull(true)
        chargePendingAt = System.currentTimeMillis(); chargeDeferWhy = ""
        tryCharge()
    }

    private fun chargeLog(res: String) {
        if (host == null) return
        runCatching { com.combo.monkeycompanion.powers.ActionLog.add(context, "Charger plugged -> moment $res", character.name) }
    }

    private fun dndOn(): Boolean = runCatching {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm != null && nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
    }.getOrDefault(false)

    /** Play the pending charger moment now if possible; true when it started. */
    private fun tryCharge(): Boolean {
        if (chargePendingAt == 0L) return false
        val now = System.currentTimeMillis()
        if (now - chargePendingAt > 3 * 60_000L) { chargePendingAt = 0L; chargeLog("blocked (screen stayed off / busy for 3 min: $chargeDeferWhy)"); return false }
        val sit = runCatching { com.combo.monkeycompanion.agent.Proactive.situation() }.getOrNull()
        val hard = when {
            host == null || wp -> "no overlay"
            !ChatHub.settings.phoneMoment(PhoneMoments.Moment.CHARGE.id) -> "turned off in Settings › Phone moments"
            dndOn() -> "Do Not Disturb"
            sit?.inCall == true -> "in a call"
            else -> null
        }
        if (hard != null) { chargePendingAt = 0L; chargeLog("blocked ($hard)"); return false }
        val locked = runCatching { context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true }.getOrDefault(false)
        val wait = when {
            !live -> "not on screen yet"
            paused || sleepFrames > 0 -> "screen off"
            locked -> "lock screen"
            exiting || quitAsk -> "leaving"
            moment != null -> "another moment playing"
            dragging || tracking -> "you're holding me"
            powerOn -> "a power is running"
            falling || arc != null || tweenT >= 0f -> "mid-flight"
            controller.state == MonkeyState.ENTRANCE || entKind != EntKind.NONE -> "entrance"
            else -> null
        }
        if (wait != null) {
            if (hiddenTab && wait == "entrance") Unit
            if (chargeDeferWhy != wait) { chargeDeferWhy = wait; chargeLog("deferred ($wait), will play when I can") }
            return false
        }
        if (hiddenTab && userHidden) { chargeDeferWhy = "hidden by you"; return false }
        if (hiddenTab) { autoUnhide(); chargeDeferWhy = "coming out of hiding"; chargeLog("deferred (coming out of hiding)"); return false }
        if (typing) endTyping()
        pendingShown = false
        if (controller.isAsleep) controller.idleNow(0.5f)          // plugging in wakes it (it's your action)
        chargePendingAt = 0L
        playMoment(PhoneMoments.Moment.CHARGE)
        chargeLog("played (${momentStyle.name.lowercase().replace('_', ' ')})")
        return true
    }

    /** Fly / leap up under the status-bar battery icon so the bolt is drawn in Settai's own window. */
    private fun startChargeTrip() {
        if (host == null || wp) return
        chargeHomeX = mx; chargeHomeY = my; chargeHomeSurf = surf
        climber.deactivate()
        chargePerchX = (momentTarget[0] - boxW * 0.8f).coerceIn(0f, max(0f, sw() - boxW))
        chargePerchY = (host?.topInset() ?: 0).toFloat() + 120f * d
        surf = freeSurf(chargePerchY)
        mx = chargePerchX; my = chargePerchY
        slideFrom(chargeHomeX, chargeHomeY, 0.6f, 60f * d)
        chargeTrip = true; chargeBack = false
    }

    private fun endChargeTrip(goHome: Boolean) {
        if (!chargeTrip) return
        chargeTrip = false
        val home = chargeHomeSurf ?: return
        chargeHomeSurf = null
        if (!goHome) { surf = freeSurf(my.coerceIn(topY(), ground())); return }
        chargeTrip = true                       // stays "on a trip" (no drops) until it's home again
        val fx = mx; val fy = my
        surf = home; mx = chargeHomeX; my = chargeHomeY
        slideFrom(fx, fy, 0.5f, 40f * d) {
            chargeTrip = false
            if (surf.climb && climbs) startClimber(surf)
            controller.idleNow(2f)
        }
    }
    val debugChargeTrip get() = chargeTrip
    val debugMoment get() = moment

    /** Play a phone moment if Settings, DND, quiet hours, calls, fullscreen, cooldowns and Settai's own state allow it. */
    private fun tryMoment(m: PhoneMoments.Moment): Boolean {
        if (m == PhoneMoments.Moment.CHARGE) { chargePendingAt = System.currentTimeMillis(); return tryCharge() }
        if (host == null || wp || !live || paused || exiting || quitAsk || powerOn || dragging || typing || hiddenTab ||
            controller.isAsleep || pendingShown || moment != null || controller.state == MonkeyState.ENTRANCE) return false
        val st = ChatHub.settings
        val now = System.currentTimeMillis()
        val sit = runCatching { com.combo.monkeycompanion.agent.Proactive.situation() }.getOrNull()
        val cal = java.util.Calendar.getInstance()
        val quiet = com.combo.monkeycompanion.agent.QuietHours.inside(cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE), st.quietStart, st.quietEnd)
        val why = PhoneMoments.check(m, st.phoneMoment(m.id), now - (momentLast[m] ?: 0L), silent, quiet, sit?.inCall == true, sit?.fullscreen == true)
        if (why != null) return false
        playMoment(m)
        return true
    }

    private fun playMoment(m: PhoneMoments.Moment) {
        val now = System.currentTimeMillis()
        momentLast[m] = now
        val ownShock = m == PhoneMoments.Moment.CHARGE && PhoneMoments.chargeStyle(character.id) == PhoneMoments.ChargeStyle.SHOCK
        // v10.20: the very first plug-in shocks EVERY character (burnt soot face); after that he remembers and is careful
        val shocks = if (m == PhoneMoments.Moment.CHARGE) runCatching { com.combo.monkeycompanion.fun5.Fun5Store.shocks(context, character.id) }.getOrDefault(1) else 1
        // the first time he still runs up to the battery icon, and the zap gets HIM (shock + soot mid-trip)
        val bigShock = m == PhoneMoments.Moment.CHARGE && !saver && shocks == 0 && !ownShock
        val shock = ownShock
        if (m == PhoneMoments.Moment.CHARGE) runCatching { com.combo.monkeycompanion.fun5.Fun5Store.addShock(context, character.id) }
        sootShock = (shock || bigShock) && !saver
        bigShockNow = bigShock
        when {
            shock -> com.combo.monkeycompanion.ai.BodySense.record("the charger was plugged in and it zapped me", com.combo.monkeycompanion.ai.BodySense.Feeling.ZAPPED)
            m == PhoneMoments.Moment.CHARGE -> com.combo.monkeycompanion.ai.BodySense.record("the charger was plugged in and I ran to the battery", com.combo.monkeycompanion.ai.BodySense.Feeling.ENERGISED)
            else -> com.combo.monkeycompanion.ai.BodySense.record("phone moment: " + m.name.lowercase())
        }
        val charge = m == PhoneMoments.Moment.CHARGE && !shock
        moment = m; momentT = 0f
        momentLen = if (charge) PhoneMoments.CHARGE_TRIP_SECS else if (shock) SHOCK_SECS else PhoneMoments.duration(m, saver)
        momentFx = !shock && (charge || PhoneMoments.effects(saver))
        momentStyle = PhoneMoments.chargeStyle(character.id)
        val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        momentTarget = PhoneMoments.batteryTarget(sw(), (host?.topInset() ?: 0).toFloat(), rtl, d)
        if (m == PhoneMoments.Moment.CHARGE || m == PhoneMoments.Moment.LOW) controller.face(PhoneMoments.facingToward(mx + boxW / 2f, momentTarget[0]).toFloat())
        controller.idleNow(momentLen + 0.6f)
        aiReply = null; pages.clear(); beats.clear()
        val line = when {
            bigShock -> com.combo.monkeycompanion.engine.impact.ShockLines.first(character.id)
            m == PhoneMoments.Moment.CHARGE && shocks == 1 && !ownShock -> com.combo.monkeycompanion.engine.impact.ShockLines.remember(character.id) + " " + PhoneMoments.line(m, character.id)
            else -> PhoneMoments.line(m, character.id)
        }
        if (shock) ui.postDelayed({ say(line, 3600L) }, (momentLen * 1000).toLong() + 300L)   // v10.14: speech after the shock settles
        else if (m == PhoneMoments.Moment.CHARGE && momentFx) ui.postDelayed({ if (moment == m) say(line, 3800L) }, (momentLen * 450).toLong())
        else say(line, (momentLen * 1000).toLong() + 2200L)
        if (m == PhoneMoments.Moment.CHARGE || m == PhoneMoments.Moment.FULL) performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        if (charge) startChargeTrip()
        if (!running) start()
    }

    // ---- v10.20 soot: a burnt face for ANY character (darkens whatever the renderer drew, then white blinking eyes) ----
    private var sootShock = false
    /** v10.20: the first plug-in ever: he runs to the battery as usual, and mid-zap the current gets him. */
    private var bigShockNow = false
    private var sootFadeT = 0f
    private var sootSmokeIn = 0f
    private val sootPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP) }
    private val sootEye = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sootPt = FloatArray(4)
    val debugSoot get() = sootAmount()
    /** 0..1 how burnt he looks right now. */
    private fun sootAmount(): Float = when {
        sootShock && moment == PhoneMoments.Moment.CHARGE -> ((momentT / momentLen - (if (bigShockNow) BIG_SHOCK_FROM + 0.08f else 0.3f)) / 0.15f).coerceIn(0f, 1f)
        sootFadeT > 0f -> min(1f, sootFadeT / (SOOT_FADE_SECS * 0.6f))
        else -> 0f
    }
    private fun stepSoot(dt: Float) {
        if (sootFadeT > 0f) sootFadeT = max(0f, sootFadeT - dt)
        val a = sootAmount()
        if (a < 0.5f || saver) return
        sootSmokeIn -= dt
        if (sootSmokeIn <= 0f) {   // a puff of smoke from the top of the head now and then (deterministic, no Random)
            sootSmokeIn = 0.22f
            rig.headTop(head)
            val wob = sin(t * 7f) * 10f * d
            particles.spawn(com.combo.monkeycompanion.rig.Particles.SMOKE, head[0] + winX + sinkX + wob, head[1] + winY, wob * 2f, -60f * d, 1.1f, 9f * d)
        }
    }
    /** Called inside drawMonkey around the renderer: [body] draws the character. */
    private fun drawWithSoot(canvas: Canvas, body: () -> Unit) {
        val a = sootAmount()
        if (a <= 0.01f) { body(); return }
        val layer = canvas.saveLayer(null, null)
        body()
        sootPaint.color = Color.rgb(0x14, 0x12, 0x10); sootPaint.alpha = (a * 0.88f * 255).toInt()
        canvas.drawRect(-10000f, -10000f, 10000f, 10000f, sootPaint)
        canvas.restoreToCount(layer)
        // two white eyes blinking out of the soot (rig head space: eye level is about y = -41)
        sootPt[0] = -13f; sootPt[1] = -41f; sootPt[2] = 13f; sootPt[3] = -41f
        rig.head.mapPoints(sootPt)
        val blink = (t % 2.2f) in 1.0f..1.12f
        val r = 4.2f * rigScale / 0.66f
        sootEye.style = Paint.Style.FILL
        for (i in 0..1) {
            val ex = sootPt[i * 2]; val ey = sootPt[i * 2 + 1]
            sootEye.color = Color.WHITE; sootEye.alpha = (a * 255).toInt()
            if (blink) canvas.drawRect(ex - r, ey - r * 0.15f, ex + r, ey + r * 0.15f, sootEye)
            else {
                tmpR.set(ex - r, ey - r * 1.25f, ex + r, ey + r * 1.25f); canvas.drawOval(tmpR, sootEye)
                sootEye.color = Color.BLACK; sootEye.alpha = (a * 255).toInt(); canvas.drawCircle(ex, ey + r * 0.2f, r * 0.45f, sootEye)
            }
        }
    }

    /** v10.14 Billa & Chittu plug-in shock: a jolt (eased in/out, no snaps), Billa's fur spikes, Chittu spins twice. */
    private var shockSpin = 0f
    val debugShockSpin get() = shockSpin
    val debugDrawPose get() = drawPose
    val debugMomentStyle get() = momentStyle
    fun debugPlayMoment(m: PhoneMoments.Moment) = playMoment(m)
    private fun shockPose(k: Float) {
        val p = drawPose
        val e = (k / 0.12f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) } * (1f - ((k - 0.8f) / 0.2f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) })
        p.frizz = maxOf(p.frizz, e)
        p.eyeOpen = 1f + 0.45f * e; p.mouthOpen = maxOf(p.mouthOpen, 0.7f * e); p.browAngle = -10f * e
        p.armLRaise = maxOf(p.armLRaise, 120f * e); p.armRRaise = maxOf(p.armRRaise, 120f * e)
        p.bodyY -= 10f * e                                       // a hop of fright
        p.tailRot += 30f * e
        if (character.id == "chittu") {
            val u = ((k - 0.1f) / 0.7f).coerceIn(0f, 1f); shockSpin = 720f * u * u * (3f - 2f * u)   // two eased turns, ends upright
            if (shockSpin >= 719.9f) shockSpin = 0f
        } else shockSpin = 0f
        if (!saver && Random.nextFloat() < 0.35f * e) {
            val ang = Random.nextFloat() * 6.283f; val sp = (120f + Random.nextFloat() * 160f) * d
            particles.spawn(com.combo.monkeycompanion.rig.Particles.SPARK, winX + ox + boxW / 2f, winY + oy + boxH * 0.4f, kotlin.math.cos(ang) * sp, kotlin.math.sin(ang) * sp, 0.3f, 5f * d)
        }
    }

    private fun stepMoment(dt: Float) {
        stepSoot(dt)   // v10.20
        if (chargePendingAt > 0L) { chargeRetryT += dt; if (chargeRetryT > 0.4f) { chargeRetryT = 0f; tryCharge() } }
        val m = moment ?: return
        momentT += dt
        val k = momentT / momentLen
        if (k >= 1f || powerOn || dragging || exiting) {
            if (sootShock) { sootShock = false; sootFadeT = SOOT_FADE_SECS }   // v10.20: the soot wears off slowly
            bigShockNow = false
            moment = null; shockSpin = 0f
            val interrupted = dragging || powerOn || exiting
            if (interrupted && chargeTrip) { chargeTrip = false; chargeHomeSurf = null; if (!dragging) surf = freeSurf(my.coerceIn(topY(), ground())) }
            else if (!chargeBack) endChargeTrip(true)
            chargeBack = false; return
        }
        if (chargeTrip && !chargeBack && k >= 0.86f) { chargeBack = true; endChargeTrip(true) }
        if (m == PhoneMoments.Moment.CHARGE && momentStyle == PhoneMoments.ChargeStyle.SHOCK) { shockPose(k); return }
        if (m == PhoneMoments.Moment.CHARGE) controller.face(PhoneMoments.facingToward(mx + boxW / 2f, momentTarget[0]).toFloat())
        MomentPoses.apply(drawPose, m, k, momentT)
        if (bigShockNow && m == PhoneMoments.Moment.CHARGE && k in BIG_SHOCK_FROM..BIG_SHOCK_TO) shockPose((k - BIG_SHOCK_FROM) / (BIG_SHOCK_TO - BIG_SHOCK_FROM))   // v10.20: the zap gets him
        if (momentFxOn() && !saver && MomentFx.sparking(k)) {
            // A few capped sparks at the battery icon (the particle pool holds its cap).
            repeat(2) {
                val ang = Random.nextFloat() * 6.283f; val sp = (180f + Random.nextFloat() * 260f) * d
                particles.spawn(com.combo.monkeycompanion.rig.Particles.SPARK, momentTarget[0], momentTarget[1], kotlin.math.cos(ang) * sp, kotlin.math.sin(ang) * sp, 0.35f, 6f * d)
            }
        }
    }

    /** Charge beam / cable from the character to the battery icon (screen px). */
    private fun drawMomentFx(c: Canvas) {
        if (!momentFxOn()) return
        val from = when (momentStyle) {
            PhoneMoments.ChargeStyle.HEAT_BEAM -> { tmpB[0] = 0f; tmpB[1] = -41f; rig.head.mapPoints(tmpB); tmpB }
            PhoneMoments.ChargeStyle.CABLE -> floatArrayOf(ox + boxW / 2f, oy + boxH * 0.55f)
            PhoneMoments.ChargeStyle.GIANT_PLUG -> floatArrayOf(ox + boxW / 2f, oy + boxH * 0.68f)
            else -> { rig.handPoint(false, tmpA); tmpA }
        }
        val k = momentT / momentLen
        MomentFx.drawCharge(c, momentStyle, k, from[0] + winX, from[1] + winY, momentTarget[0], momentTarget[1], d, t)
        MomentFx.drawBigBolt(c, momentStyle, k, from[0] + winX, from[1] + winY, momentTarget[0], momentTarget[1], d, t)
    }

    /** Headphones / Wi-Fi waves on the body (Settai's own window). */
    private fun drawMomentProps(c: Canvas) {
        val m = moment ?: return
        val k = momentT / momentLen
        when (m) {
            PhoneMoments.Moment.HEADPHONES -> {
                rig.headTop(head)
                val r = com.combo.monkeycompanion.rig.MonkeyRig.HEAD_R * rigScale
                val a = (min(k, 1f - k) * 8f).coerceIn(0f, 1f)
                MomentFx.drawHeadphones(c, head[0], head[1] + 14f * rigScale, r, a)
            }
            PhoneMoments.Moment.WIFI -> if (!saver) { rig.headTop(head); MomentFx.drawWifi(c, head[0], head[1] + 6f * rigScale, k, d) }
            else -> Unit
        }
    }

    // Event sources: charger level (only while plugged), headphones, Wi-Fi, screenshots (only with photo access).
    private var fullWatch = false
    private var fullSaid = false
    private val levelReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (host != null && !wp && sc > 0 && lvl >= 0) com.combo.monkeycompanion.mind.MindHub.world.onBattery(lvl * 100 / sc, System.currentTimeMillis())
            val full = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0) == BatteryManager.BATTERY_STATUS_FULL || (sc > 0 && lvl * 100 / sc >= 100)
            if (full && !fullSaid) { fullSaid = tryMoment(PhoneMoments.Moment.FULL) || fullSaid }
            if (!full && sc > 0 && lvl * 100 / sc < 95) fullSaid = false
        }
    }
    private fun watchFull(on: Boolean) {
        if (on == fullWatch || host == null || wp) return
        fullWatch = on
        if (on) runCatching {
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(levelReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
            else context.registerReceiver(levelReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } else { runCatching { context.unregisterReceiver(levelReceiver) }; fullSaid = false }
    }

    private var audioCb: android.media.AudioDeviceCallback? = null
    private var netCb: android.net.ConnectivityManager.NetworkCallback? = null
    private var shotObserver: android.database.ContentObserver? = null

    private fun registerMomentSources() {
        if (host == null || wp) return
        val plugged = runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0 }.getOrDefault(0) != 0
        if (plugged) watchFull(true)
        runCatching {
            val am = context.getSystemService(AudioManager::class.java)
            val since = SystemClock.elapsedRealtime()
            val cb = object : android.media.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(added: Array<out android.media.AudioDeviceInfo>?) {
                    if (SystemClock.elapsedRealtime() - since < 2000L) return      // the initial list, not a new plug
                    val phones = added.orEmpty().any { it.isSink && it.type in HEADPHONE_TYPES }
                    if (phones) tryMoment(PhoneMoments.Moment.HEADPHONES)
                }
            }
            am.registerAudioDeviceCallback(cb, ui); audioCb = cb
        }
        runCatching {
            val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
            var wifi: Boolean? = null
            val cb = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(n: android.net.Network, caps: android.net.NetworkCapabilities) {
                    val now = caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    val was = wifi; wifi = now
                    com.combo.monkeycompanion.mind.MindHub.world.onConnectivity(true, System.currentTimeMillis())
                    if (was == false && now) ui.post { tryMoment(PhoneMoments.Moment.WIFI) }   // first callback = current state
                }
                override fun onLost(n: android.net.Network) {
                    wifi = false
                    com.combo.monkeycompanion.mind.MindHub.world.onConnectivity(false, System.currentTimeMillis())
                }
            }
            cm.registerDefaultNetworkCallback(cb); netCb = cb
        }
        registerScreenshots()
    }

    /** Optional: screenshots, only when Settai already has photo access (it never asks for it) and the moment is on. */
    private fun registerScreenshots() {
        if (shotObserver != null || !ChatHub.settings.phoneMoment(PhoneMoments.Moment.SCREENSHOT.id)) return
        val perm = if (Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_IMAGES" else android.Manifest.permission.READ_EXTERNAL_STORAGE
        if (context.checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val obs = object : android.database.ContentObserver(ui) {
            override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
                val isShot = runCatching {
                    context.contentResolver.query(uri ?: return@runCatching false, arrayOf(android.provider.MediaStore.Images.Media.DISPLAY_NAME, android.provider.MediaStore.Images.Media.DATE_ADDED), null, null, null)?.use { cur ->
                        cur.moveToFirst() && cur.getString(0).orEmpty().contains("screenshot", true) && System.currentTimeMillis() / 1000 - cur.getLong(1) < 15
                    } ?: false
                }.getOrDefault(false)
                if (isShot) tryMoment(PhoneMoments.Moment.SCREENSHOT)
            }
        }
        runCatching { context.contentResolver.registerContentObserver(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, obs); shotObserver = obs }
    }

    private fun unregisterMomentSources() {
        watchFull(false)
        audioCb?.let { cb -> runCatching { context.getSystemService(AudioManager::class.java).unregisterAudioDeviceCallback(cb) } }; audioCb = null
        netCb?.let { cb -> runCatching { context.getSystemService(android.net.ConnectivityManager::class.java).unregisterNetworkCallback(cb) } }; netCb = null
        shotObserver?.let { o -> runCatching { context.contentResolver.unregisterContentObserver(o) } }; shotObserver = null
        moment = null
    }

    // ---------------- v4 FX window ----------------
    private var fxHosted = false
    private val fxDraw: (Canvas) -> Unit = { c ->
        if (fireworks.active || skyActive) {
            c.save()
            if (!hiddenTab) c.clipOutRect(winX.toFloat(), winY.toFloat(), (winX + winW).toFloat(), (winY + winH).toFloat())
            for (sh in skyShows) sh.draw(c)
            fireworks.draw(c)
            c.restore()
        }
        if (balls.looseActive) {
            c.save()
            if (!hiddenTab) c.clipOutRect(winX.toFloat(), winY.toFloat(), (winX + winW).toFloat(), (winY + winH).toFloat())
            drawLooseBalls(c)
            c.restore()
        }
        if (particles.active) {
            c.save()
            // Inside Settai's own window the particles are drawn there (behind the body); here only outside it.
            if (!hiddenTab) c.clipOutRect(winX.toFloat(), winY.toFloat(), (winX + winW).toFloat(), (winY + winH).toFloat())
            particles.draw(c)
            c.restore()
        }
        if (screenFxOn()) {
            c.save()
            if (!hiddenTab) c.clipOutRect(winX.toFloat(), winY.toFloat(), (winX + winW).toFloat(), (winY + winH).toFloat())
            drawScreenFx(c)
            c.restore()
        }
        if (vineOn) {
            c.save()
            if (!hiddenTab) c.clipOutRect(winX.toFloat(), winY.toFloat(), (winX + winW).toFloat(), (winY + winH).toFloat())
            drawVine(c)
            c.restore()
        }
        if (v5On && stage.active) {
            c.save()
            if (!hiddenTab) c.clipOutRect(winX.toFloat(), winY.toFloat(), (winX + winW).toFloat(), (winY + winH).toFloat())
            stage.draw(c, front = false); stage.draw(c, front = true)
            c.restore()
        }
        ScreenFxHook.draw?.invoke(c)
    }

    // ---------------- v4 show-off screen effects ----------------
    private val screenFx = ScreenFx()
    private var liftCount = -1
    private fun screenFxOn() = powerOn && screenFx.wants(flight.phase)

    /** Screen-space effects (lightning, crack, frost, giant eye, slow-mo). [c] is in screen pixels. */
    private fun drawScreenFx(c: Canvas) {
        val h = host ?: return
        val feetX = winX + ox + boxW / 2f; val feetY = winY + oy + boxH - groundPad
        rig.handPoint(false, tmpA)
        tmpB[0] = 0f; tmpB[1] = -41f; rig.head.mapPoints(tmpB)     // eyes (heat vision / breath)
        val handY = if (flight.phase == PowerPhase.LIFT) { rig.headTop(tmpPt); tmpPt[1] } else tmpA[1]   // the card rests on the raised gloves
        screenFx.draw(c, h.screenWidth().toFloat(), h.screenHeight().toFloat(), flight.phase, flight.k, t, flight.crashed,
            feetX, feetY, tmpA[0] + winX, handY + winY, d, tmpB[0] + winX, tmpB[1] + winY, if (renderFacing() < 0f) -1 else 1, liftCount)
    }

    /** v4.1: Settings switched the body: new renderer, off any wall (heroes don't climb). Memory and settings stay. */
    private fun syncCharacter() {
        val c = forcedCharacterId?.let { com.combo.monkeycompanion.character.CharacterRegistry.byId(it) }
            ?: com.combo.monkeycompanion.character.CharacterRegistry.current
        if (!climbs && climber.active) climber.deactivate()
        if (!climbs && !canWalkScreenEdges && surf.climb) { surf = screenSurf(SK.FLOOR); my = ground(); clampToSurf() }
        if (c === character) return
        if (powerOn) return                                  // finish the current power first
        character = c
        renderer = c.newRenderer().also { it.wear = com.combo.monkeycompanion.character.CharacterRegistry.wear(c) }
        animEngine.reset()
        if (!climbs && climber.active) climber.deactivate()
        if (!climbs && !canWalkScreenEdges && surf.climb) { surf = screenSurf(SK.FLOOR); my = ground(); clampToSurf() }
        if (host != null && !hiddenTab && !controller.isAsleep) { controller.play(MonkeyState.WAVE, 2.2f); say(Lines.pick(Lines.HELLO), 3500L) }
    }

    /** Phone battery low: a line, or (Armour Anna) the suit's low-power droop with the real percentage. */
    private fun onBatteryLow() {
        val pct = runCatching { context.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1 }.getOrDefault(-1)
        (renderer as? com.combo.monkeycompanion.rig.ArmourRenderer)?.batteryPct = pct
        if (character === com.combo.monkeycompanion.character.ArmourAnna && host != null && live && !hiddenTab && !controller.isAsleep &&
            usePower(explicit = false, force = com.combo.monkeycompanion.brain.LowPowerPower)) return
        if (tryMoment(PhoneMoments.Moment.LOW)) return
        importantEvent(Lines.pick(Lines.BATTERY_LOW).replace("{pct}", if (pct >= 0) "$pct" else "low"))
    }

    /** Ice Monkey cools a hot phone (Settings: heat gag + powers on). True = it went. */
    private fun heatIce(): Boolean {
        val on = runCatching { ChatHub.settings.heatGag }.getOrDefault(true)
        if (!on || powerSetting() == Superpowers.OFF || saver || host == null || !live) return false
        val cool = when (character) {
            com.combo.monkeycompanion.character.MonkeyCharacter -> Superpower.IceMonkey
            com.combo.monkeycompanion.character.Superboy -> com.combo.monkeycompanion.brain.SuperBreathPower
            else -> return false
        }
        return usePower(explicit = false, force = cool)
    }

    /** Shadow clones (Ninja) or slow-mo after-images (Time Freeze), drawn around the real body. */
    private fun drawClones(canvas: Canvas) {
        val cl = drawPose.clones
        if (cl <= 0.02f || !powerOn) return
        val ph = flight.phase
        val gx = ox + boxW / 2f; val gy = oy + boxH - groundPad
        if (ph == com.combo.monkeycompanion.brain.PowerPhase.DODGE || ph == com.combo.monkeycompanion.brain.PowerPhase.BLUR) {
            val dir = -renderFacing()
            for (i in 3 downTo 1) {
                canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (70 * cl / i).toInt())
                canvas.translate(dir * i * 14f * rigScale, 0f)
                renderer.draw(canvas, rig, drawPose, t - i * 0.08f)
                canvas.restore()
            }
            return
        }
        // Three dark clones dancing in sync: left, right, and a smaller one behind.
        val spread = 120f * rigScale * cl
        for (i in 0 until 3) {
            canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (150 * cl).toInt())
            when (i) {
                0 -> canvas.translate(-spread, 0f)
                1 -> canvas.translate(spread, 0f)
                else -> { canvas.translate(gx, gy - 26f * rigScale * cl); canvas.scale(0.78f, 0.78f); canvas.translate(-gx, -gy) }
            }
            renderer.draw(canvas, rig, drawPose, t + i * 0.05f)
            canvas.restore()
        }
    }

    /** Opens the effects window while particles / screen FX are alive, closes it right after (battery). */
    private fun syncFx() {
        val h = host ?: return
        if (wp) return
        val need = powerOn || particles.active || fireworks.active || skyActive || balls.looseActive || ScreenFxHook.active() || momentFxOn() || vineOn || (v5On && stage.active)
        if (need && !fxHosted) fxHosted = h.showFx(fxDraw)
        else if (!need && fxHosted) { h.showFx(null); fxHosted = false }
        if (fxHosted) h.invalidateFx()
    }

    val debugFxOn: Boolean get() = fxHosted
    // v10.8 test hooks
    fun debugLivingTap(part: com.combo.monkeycompanion.engine.touch.BodyPart, kind: com.combo.monkeycompanion.engine.touch.TouchKind = com.combo.monkeycompanion.engine.touch.TouchKind.TAP): com.combo.monkeycompanion.engine.touch.Verb? {
        val zone = when (part) {
            com.combo.monkeycompanion.engine.touch.BodyPart.HEAD, com.combo.monkeycompanion.engine.touch.BodyPart.EAR, com.combo.monkeycompanion.engine.touch.BodyPart.TRUNK -> TouchZone.HEAD
            com.combo.monkeycompanion.engine.touch.BodyPart.TAIL -> TouchZone.TAIL
            com.combo.monkeycompanion.engine.touch.BodyPart.HAND -> TouchZone.HAND
            com.combo.monkeycompanion.engine.touch.BodyPart.FOOT -> TouchZone.FOOT
            else -> TouchZone.BELLY
        }
        if (!livingTap(part, zone, 1, kind, if (kind == com.combo.monkeycompanion.engine.touch.TouchKind.FLICK) 1500f else 0f, 1f, 0f, 0.5f, if (kind == com.combo.monkeycompanion.engine.touch.TouchKind.HOLD) 0.4f else 0.1f)) {
            controller.onTap(zone, 1); return com.combo.monkeycompanion.engine.touch.Verb.LEGACY
        }
        return reactionLayer.playing
    }
    val debugReacting: Boolean get() = reactionLayer.busy
    val debugFireworks: Boolean get() = fireworks.active
    val debugFireworkCount: Int get() = fireworks.count
    /** v10.10: firework bursts so far (tests). */
    var debugBooms = 0; private set
    fun debugPower(pw: Superpower) = usePower(true, pw)
    fun debugRefreshSettings() = refreshQuipSettings()

    override fun onDraw(canvas: Canvas) {
        val c0 = Debug.threadCpuTimeNanos()
        drawAll(canvas)
        EnergyMeter.addDraw(Debug.threadCpuTimeNanos() - c0)
    }

    private fun drawAll(canvas: Canvas) {
        if (hiddenTab) { if (!wp) drawTab(canvas); return }
        if (particles.active) {
            // Particles live in screen pixels; the window may be anywhere. With an FX window they're drawn at the same
            // 0.8 opacity here (behind the body) and there (outside this window) so trails look continuous.
            if (fxHosted) canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), FX_ALPHA) else canvas.save()
            canvas.translate(-winX.toFloat(), -winY.toFloat()); particles.draw(canvas); canvas.restore()
        }
        if (screenFxOn() && (fxHosted || wp)) {
            // Same effect inside Settai's own window (the FX window clips this rect out), at the same opacity.
            if (fxHosted) canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), FX_ALPHA) else canvas.save()
            canvas.translate(-winX.toFloat(), -winY.toFloat()); drawScreenFx(canvas); canvas.restore()
        }
        if (fireworks.active || skyActive) { canvas.save(); canvas.translate(-winX.toFloat(), -winY.toFloat()); for (sh in skyShows) sh.draw(canvas); fireworks.draw(canvas); canvas.restore() }
        if (balls.looseActive) {
            if (fxHosted) canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), FX_ALPHA) else canvas.save()
            canvas.translate(-winX.toFloat(), -winY.toFloat()); drawLooseBalls(canvas); canvas.restore()
        }
        if (drawPose.clones > 0.02f && !climber.active) drawClones(canvas)
        if (momentFxOn()) {
            // v6: the charge bolt lives in Settai's own (fully opaque) window, which reaches up to the battery icon.
            canvas.save(); canvas.translate(-winX.toFloat(), -winY.toFloat()); drawMomentFx(canvas); canvas.restore()
        }
        if (vineOn) { canvas.save(); canvas.translate(-winX.toFloat(), -winY.toFloat()); drawVine(canvas); canvas.restore() }
        if (v5On && stage.active) { canvas.save(); canvas.translate(-winX.toFloat(), -winY.toFloat()); stage.draw(canvas, front = false); canvas.restore() }
        drawLandingBack(canvas)
        drawNeighbours(canvas)
        drawSkitProp(canvas)
        if (ln && isBehindWidget) {
            drawWidgetGripPaws(canvas)
        } else if (showOn) {
            // v10.13.1: drawn by ShowStageView
        } else if (false) {
            if (showFrame.visible) {
                val px = ox + boxW / 2f; val py = oy + boxH - groundPad
                // v10.13.1: gentler squash (readable, never pancaked) and a smaller cartoon mouse next to the cat
                val small = if (showRole() == com.combo.monkeycompanion.engine.show.Role.RAT) SHOW_RAT_SCALE else 1f
                canvas.save(); canvas.rotate(showFrame.rot, px, py - boxH * 0.3f)
                canvas.scale(showFrame.sx.coerceIn(0.8f, 1.25f) * small, showFrame.sy.coerceIn(0.8f, 1.25f) * small, px, py)
                drawMonkey(canvas); canvas.restore()
            }
            drawShowFx(canvas)
        } else {
            drawMonkey(canvas)
        }
        drawMomentProps(canvas)
        drawHeartAndCoPresence(canvas)
        drawLandingFront(canvas)
        if (v5On && stage.active) { canvas.save(); canvas.translate(-winX.toFloat(), -winY.toFloat()); stage.draw(canvas, front = true); canvas.restore() }
        if (!showOn) {   // v10.22: during the drama the heart and chat button go with the character (hidden)
            if ((chatBtnUntil > 0f || hintUntil > 0f) && !chatFabOn) drawChatBtn(canvas)
            drawChatFab(canvas)
            drawFriendHeart(canvas)   // v10.19
        }
        if (notifCardOpen) {
            notifCard.draw(canvas, notifCardLeft, notifCardTop)
        }
        if (bubbleOn) {
            if (climber.active) {
                climber.headTop(head); head[0] += sinkX - winX; head[1] += sinkY - winY
                if (bubbleTop > oy + boxH / 2f) head[1] = climber.headC.y - winY + 34f * rigScale
            } else {
                rig.headTop(head); head[0] += sinkX
                if (bubbleTop > oy + boxH / 2f) head[1] += (2 * MonkeyRig.HEAD_R + 30f) * rigScale  // bubble below: point at the chin
            }
            val k = (bubbleAge / POP).coerceIn(0f, 1f)
            val c1 = if (smoothAnim) profile.anim.overshoot else 1.70158f; val c3 = c1 + 1f
            val pop = 1f + c3 * (k - 1f).pow(3) + c1 * (k - 1f).pow(2)
            var alpha = if (bubbleAge > bubbleLife) 1f - (bubbleAge - bubbleLife) / FADE else min(1f, k * 3f)
            if (bubbleDragX != 0f) alpha *= (1f - abs(bubbleDragX) / (bubble.width * 1.2f)).coerceIn(0.15f, 1f)
            bubble.draw(canvas, bubbleLeft + bubbleDragX, bubbleTop, head[0], head[1] - 2 * d, max(0.01f, pop), alpha * (if (typing || arcOn) 1f else 0.9f))
        }
    }

    // ---------------- v8.0 "What You Missed" Compact Notification Summary Card ----------------
    val notifCard = com.combo.monkeycompanion.agent.NotifSummaryCard(d)
    var notifCardOpen = false; private set
    var notifCardLeft = 0f; private set
    var notifCardTop = 0f; private set
    private var promptNotifsAfterUnhide = false
    var debugTabBadgeCount = 0; private set
    val debugBubbleChipIds: List<String> get() = bubbleChips.map { it.id }
    fun debugOpenMenu() = openMenu()
    val debugThinking get() = thinkingBubble
    val debugAttending get() = attendingNow()

    /** Prompts the user with a "[Yes, show me 📬] [Later]" bubble when unhiding with unread notifications. */
    fun promptUnreadNotifications(): Boolean {
        val items = com.combo.monkeycompanion.agent.NotifStore.all()
        if (items.isEmpty()) return false
        val now = System.currentTimeMillis(); val newest = newestNotif()
        if (!com.combo.monkeycompanion.agent.NotifPromptGate.shouldAsk(newest, now)) return false
        com.combo.monkeycompanion.agent.NotifPromptGate.asked(newest, now)
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val line = com.combo.monkeycompanion.agent.NotifSummary.edgePrompt(items, hour)
        val Q = com.combo.monkeycompanion.agent.QuickChip
        showBubble(line, 12f, chips = listOf(Q.NOTIF_CARD, Q.LATER), reply = false)
        updateGeometry(); invalidate()
        return true
    }

    /** Time of the newest notification we hold (at least 1 when there is any, 0 when none). */
    private fun newestNotif(): Long = com.combo.monkeycompanion.agent.NotifStore.all().maxOfOrNull { it.time.coerceAtLeast(1L) } ?: 0L

    /** Opens the compact Motorola-style "What You Missed" dark summary card right above Settai. */
    fun openNotifSummaryCard(useDemoIfEmpty: Boolean = false): Boolean {
        com.combo.monkeycompanion.agent.NotifPromptGate.opened(newestNotif())
        if (hiddenTab && host != null && !wp) unhide()
        com.combo.monkeycompanion.powers.SettaiNotificationListener.instance?.current()
        val liveItems = com.combo.monkeycompanion.agent.NotifStore.all()
        if (liveItems.isEmpty() && !useDemoIfEmpty) {
            val granted = com.combo.monkeycompanion.powers.SettaiNotificationListener.isAccessGranted(context)
            val Q = com.combo.monkeycompanion.agent.QuickChip
            if (!granted) {
                showBubble(
                    "📬 Allow Settai in Notification Access so I can group what you missed (or tap Preview to see the card)!",
                    11f,
                    chips = listOf(Q.PERM_NOTIFS, com.combo.monkeycompanion.agent.QuickChip("notif_card_demo", "Preview card ✨"), Q.LATER),
                    reply = false
                )
                return false
            }
        }
        val items = when {
            useDemoIfEmpty && liveItems.size < 5 -> liveItems + com.combo.monkeycompanion.agent.NotifSummary.richDemoItems()
            liveItems.isNotEmpty() -> liveItems
            useDemoIfEmpty -> com.combo.monkeycompanion.agent.NotifSummary.richDemoItems()
            else -> emptyList()
        }
        val groups = com.combo.monkeycompanion.agent.NotifSummary.group(items, maxGroups = 25)
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val userName = runCatching {
            if (ChatHub.settings.groupEnabled(com.combo.monkeycompanion.powers.CapGroup.MEMORY))
                com.combo.monkeycompanion.powers.ExtraCaps.memory(context).name()
            else null
        }.getOrNull()
        val greet = com.combo.monkeycompanion.agent.NotifSummary.greeting(hour, userName)
        val maxW = ((host?.screenWidth() ?: width.takeIf { it > 0 } ?: 1080) - (24 * d).toInt()).coerceAtLeast((260 * d).toInt())
        notifCard.setData(groups, greet, "Here's what you missed...", maxW)
        endTyping()
        bubbleOn = false; arcOn = false; bubbleChips = emptyList(); pages.clear(); beats.clear()
        notifCardOpen = true
        controller.play(MonkeyState.HAPPY_PAT, 1.5f)
        updateGeometry(); invalidate()
        if (host != null) runCatching { com.combo.monkeycompanion.powers.ActionLog.add(context, "What You Missed card opened (${groups.size} groups)", character.name) }
        if (!running && live && !paused) start()
        return true
    }

    private var notifTouchActive = false
    private var notifScrolling = false
    private var notifDownRawY = 0f
    private var notifLastRawY = 0f
    private var notifLastMoveMs = 0L
    private var notifFlingVy = 0f

    fun closeNotifSummaryCard() {
        if (!notifCardOpen) return
        notifCardOpen = false
        notifTouchActive = false
        notifScrolling = false
        updateGeometry(); invalidate()
    }

    private fun handleNotifCardHit(hit: com.combo.monkeycompanion.agent.NotifSummaryCard.Hit) {
        when (hit) {
            is com.combo.monkeycompanion.agent.NotifSummaryCard.Hit.Row -> {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                controller.play(MonkeyState.HAPPY_PAT, 1.6f)
                closeNotifSummaryCard()
                com.combo.monkeycompanion.powers.SettaiNotificationListener.openNotification(context, hit.group.key, hit.group.pkg)
            }
            com.combo.monkeycompanion.agent.NotifSummaryCard.Hit.Close -> closeNotifSummaryCard()
            com.combo.monkeycompanion.agent.NotifSummaryCard.Hit.ClearAll -> {
                com.combo.monkeycompanion.powers.SettaiNotificationListener.instance?.dismiss(null)
                    ?: com.combo.monkeycompanion.agent.NotifStore.reset(emptyList())
                closeNotifSummaryCard()
                say("Cleared all notifications! ✨", 2400L)
            }
            com.combo.monkeycompanion.agent.NotifSummaryCard.Hit.AiRecap -> {
                notifCard.subtitleText = com.combo.monkeycompanion.agent.NotifSummary.localRecapLine(notifCard.groups)
                invalidate()
                if (ChatHub.hasRealBrain()) {
                    val prompt = "Summarise these notifications in one short Tamil Nadu friend sentence: " +
                        notifCard.groups.joinToString("; ") { "${it.title}: ${it.summaryText}" }
                    ChatHub.quip(prompt) { q ->
                        if (!q.isNullOrBlank() && notifCardOpen) {
                            notifCard.subtitleText = "✨ " + q.lineSequence().first().take(72)
                            invalidate()
                        }
                    }
                }
            }
            com.combo.monkeycompanion.agent.NotifSummaryCard.Hit.ScrollUp -> {
                notifFlingVy = 0f
                notifCard.scrollBy(-116f * d)
                invalidate()
            }
            com.combo.monkeycompanion.agent.NotifSummaryCard.Hit.ScrollDown -> {
                notifFlingVy = 0f
                notifCard.scrollBy(116f * d)
                invalidate()
            }
            com.combo.monkeycompanion.agent.NotifSummaryCard.Hit.InsideCard -> Unit
        }
    }

    // ---------------- v8.0 Heart & Care + Co-Presence Rituals (Focus Desk, 60s Breathe, Night Campfire) ----------------
        private var comfortKind: com.combo.monkeycompanion.heart.ComfortKind? = null
    private var comfortT = 0f
    private var comfortSignText = "You got this, machan ❤️"
    private val utilityPillRect = RectF()

    val debugComfortKind get() = comfortKind

    /** Plays a warm physical comfort animation on the glass (SIGN_CARD, GLASS_KNOCK_HUG, OFFER_COFFEE). */
    fun playComfort(kind: com.combo.monkeycompanion.heart.ComfortKind, signText: String = "You got this, machan ❤️", line: String? = null) {
        if (hiddenTab && host != null && !wp && !autoUnhide()) return
        comfortKind = kind
        comfortT = 0f
        if (kind == com.combo.monkeycompanion.heart.ComfortKind.GLASS_KNOCK_HUG) com.combo.monkeycompanion.ai.BodySense.record("we hugged through the glass", com.combo.monkeycompanion.ai.BodySense.Feeling.WARM)
        else com.combo.monkeycompanion.ai.BodySense.record("I comforted you")
        comfortSignText = signText
        controller.play(if (kind == com.combo.monkeycompanion.heart.ComfortKind.OFFER_COFFEE) MonkeyState.SIT else MonkeyState.HAPPY_PAT, kind.secs)
        val Q = com.combo.monkeycompanion.agent.QuickChip
        val chips = listOf(Q.HUG, Q.SIGN, Q.BREATHE, Q.COFFEE, Q.OK)
        if (!line.isNullOrBlank()) showBubble(line, kind.secs + 2f, chips = chips, reply = false)
        updateGeometry(); invalidate()
        if (!running && live && !paused) start()
    }

    private var focusIdleT = 0f
    private val focusResumeStates = setOf(MonkeyState.IDLE, MonkeyState.WALK, MonkeyState.LANDING)
    /** v10.9 (tests): the study timer as this view sees it. */
    val debugFocusRemaining get() = com.combo.monkeycompanion.mind.MindHub.focusBuddy.remainingSecs
    val debugStudyPose get() = drawPose.notebook > 0.5f && com.combo.monkeycompanion.mind.MindHub.focusBuddy.active

    /** Focus session finished -> confetti, dance, XP & diary entry! */
    private fun celebrateFocusDone() {
        if (host == null) return
        controller.danceNow(talk = false)
        say("🎉 Focus session complete!! Proud of you, machan — time for a stretch & water break! 🏆🍌", 5000L)
        if (!saver) {
            val f = feetScreen(tmpPt)
            repeat(16) {
                val ang = fxRnd.nextFloat() * 6.283f; val sp = (140f + fxRnd.nextFloat() * 240f) * d
                particles.spawn(com.combo.monkeycompanion.rig.Particles.SPARKLE, f[0], f[1] - 40 * d, kotlin.math.cos(ang) * sp, sin(ang) * sp - 80 * d, 0.7f, 5 * d)
            }
        }
        runCatching {
            com.combo.monkeycompanion.fun5.Fun5Store.addXp(context, com.combo.monkeycompanion.v5.Friendship.Source.GAME_WIN)
            com.combo.monkeycompanion.mind.MindHub.mind()?.diary?.add(com.combo.monkeycompanion.mind.Diary.Kind.HABIT, "studied together at the focus desk", System.currentTimeMillis(), extra = 0.25f)
            com.combo.monkeycompanion.powers.ActionLog.add(context, "Focus Desk Buddy completed!", character.name)
        }
    }

    /** Starts the 25-min (or [minutes]) Pomodoro Focus / Study Desk Buddy. */
    fun startFocusBuddy(minutes: Int = 25) {
        com.combo.monkeycompanion.mind.MindHub.breatheBuddy.stop()
        runCatching { com.combo.monkeycompanion.agent.ActivityScheduler.attach(context) }
        com.combo.monkeycompanion.mind.MindHub.focusBuddy.start(minutes)
        focusIdleT = 0f
        controller.play(MonkeyState.SIT, minutes * 60f)
        say("📚 Pulling out my study desk for $minutes min! I'll work quietly right here with you, machan ✍️☕", 4200L)
        if (host != null) runCatching { com.combo.monkeycompanion.powers.ActionLog.add(context, "Focus Desk Buddy started (${minutes}m)", character.name) }
        updateGeometry(); invalidate()
        if (!running && live && !paused) start()
    }

    /** Starts the 60-second 4-4-6 guided calm breathing exercise. */
    fun startBreatheBuddy() {
        com.combo.monkeycompanion.mind.MindHub.focusBuddy.stop()
        com.combo.monkeycompanion.mind.MindHub.breatheBuddy.start()
        controller.play(MonkeyState.SIT, 60f)
        say("🫧 Sit with me for 60 seconds, machan. Follow the glowing ring: 4s breathe in · 4s hold · 6s breathe out 🌿", 4800L)
        if (host != null) runCatching { com.combo.monkeycompanion.powers.ActionLog.add(context, "60s Calm Breathing Buddy started", character.name) }
        updateGeometry(); invalidate()
        if (!running && live && !paused) start()
    }

    /** Lights the cozy crackling campfire at the bottom edge. */
    fun startCampfire(manual: Boolean = true) {
        com.combo.monkeycompanion.mind.MindHub.campfire.start(byUser = manual)
        controller.play(MonkeyState.SIT, 30f)
        if (manual) say("🔥 Lit a cozy campfire at the bottom edge. Warm your hands with us, machan 🌙✨", 4000L)
        if (host != null) runCatching { com.combo.monkeycompanion.powers.ActionLog.add(context, "Night Campfire lit", character.name) }
        updateGeometry(); invalidate()
        if (!running && live && !paused) start()
    }

    private fun stepHeartAndCoPresence(dt: Float) {
        com.combo.monkeycompanion.agent.DailyUtilityEngine.step(dt)
        if (wasTuckedBeforeUtilityAlert && autoReTuckTimer > 0f && !hiddenTab && !dragging && !typing) {
            autoReTuckTimer -= dt
            if (autoReTuckTimer <= 0f) {
                wasTuckedBeforeUtilityAlert = false
                autoReTuckTimer = 0f
                hideToEdge(preAlertHideSide, rest = true)
            }
        }
        comfortKind?.let { ck ->
            comfortT += dt
            if (comfortT >= ck.secs || dragging || falling || powerOn) {
                comfortKind = null
            } else {
                com.combo.monkeycompanion.rig.HeartFx.applyComfortPose(drawPose, ck, t)
            }
        }
        // v10.9: the study timer is a wall-clock end time owned by agent/ActivityScheduler. Powers, drags, chat, Smash,
        // switches and Duo/Trio no longer stop (or double-count) it; this view only reads it and resumes the study pose.
        val fb = com.combo.monkeycompanion.mind.MindHub.focusBuddy
        if (fb.active) {
            val owner = forcedCharacterId == null
            if (owner && fb.tick()) celebrateFocusDone()
            else {
                val busy = dragging || powerOn || falling || rocketFlyOn || hoverOn || climber.active || exiting
                if (!busy) {
                    drawPose.notebook = max(drawPose.notebook, 0.8f)
                    focusIdleT += dt
                    // back to the desk once the interruption is over (power, chat, Smash, a touch reaction…)
                    if (focusIdleT > 1.2f && controller.state in focusResumeStates && !reactionLayer.busy) {
                        controller.play(MonkeyState.SIT, min(fb.remainingSecs, 600f).coerceAtLeast(5f))
                        focusIdleT = 0f
                    }
                } else focusIdleT = 0f
            }
        }
        val bb = com.combo.monkeycompanion.mind.MindHub.breatheBuddy
        if (bb.active) {
            // v10.9: a power or drag pauses the 60 s breathing (it used to cancel it); only the main view counts it down
            if (dragging || powerOn || forcedCharacterId != null) Unit
            else {
                val rf = bb.radiusFraction()
                drawPose.squashX = 0.96f + 0.08f * rf
                drawPose.squashY = 0.96f + 0.08f * rf
                drawPose.mouthSmile = 0.7f
                if (bb.step(dt)) {
                    com.combo.monkeycompanion.mind.MindHub.mind()?.drives?.let { it.contentment = (it.contentment + 0.25f).coerceAtMost(1f); it.annoyance *= 0.3f }
                    com.combo.monkeycompanion.mind.MindHub.care.currentMood = com.combo.monkeycompanion.heart.UserMood.NEUTRAL
                    controller.play(MonkeyState.HAPPY_PAT, 2.2f)
                    say("✨ 60 seconds done! Hope your chest feels lighter, machan. I'm always here 💛", 4200L)
                }
            }
        }
        val cf = com.combo.monkeycompanion.mind.MindHub.campfire
        if (cf.active) {
            if (dragging || falling || powerOn) cf.stop() else cf.step(dt)
        } else if (host != null && !wp && !hiddenTab && controller.state == MonkeyState.IDLE && !fb.active && !bb.active) {
            val cal = java.util.Calendar.getInstance()
            val minNow = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            if (!debugNoNightCampfire && com.combo.monkeycompanion.heart.CampfireScene.isNightCampfireHour(minNow) && surf.kind == SK.FLOOR) {
                cf.start(byUser = false)
            }
        }
    }

    /** Tests / previews: don't auto-light the 23:00-05:00 night campfire (the box clock is UTC, so it is "night" there). */
    var debugNoNightCampfire = false

    private fun drawHeartAndCoPresence(canvas: Canvas) {
        if (hiddenTab || controller.state == MonkeyState.HIDDEN) return
        rig.headTop(head)
        val hx = head[0]; val hy = head[1]
        val fx = ox + boxW / 2f; val fy = oy + boxH - groundPad
        comfortKind?.let { ck ->
            com.combo.monkeycompanion.rig.HeartFx.drawComfort(canvas, ck, comfortSignText, hx, hy, fx, fy, rigScale, t)
        }
        val fb = com.combo.monkeycompanion.mind.MindHub.focusBuddy
        if (fb.active) {
            com.combo.monkeycompanion.rig.HeartFx.drawFocusDesk(canvas, fx, fy, hx, hy, rigScale, t, fb.timerLabel())
        }
        val bb = com.combo.monkeycompanion.mind.MindHub.breatheBuddy
        if (bb.active) {
            val ph = bb.phaseAt()
            com.combo.monkeycompanion.rig.HeartFx.drawBreathingAura(canvas, fx, (hy + fy) * 0.52f, rigScale, bb.radiusFraction(), ph.label, bb.secondsLeft)
        }
        val cf = com.combo.monkeycompanion.mind.MindHub.campfire
        if (cf.active) {
            val putRight = mx + boxW / 2f <= sw() / 2f
            val fireX = if (neighbourOverlayVisible()) fx + (if (putRight) 1 else -1) * boxW * 0.34f else fx + 34f * rigScale
            com.combo.monkeycompanion.rig.HeartFx.drawCampfire(canvas, fireX, fy, rigScale, t)
        }
        if (forcedCharacterId == null && SHOW_UTILITY_PILL) {
            com.combo.monkeycompanion.agent.DailyUtilityEngine.drawOverlayPill(
                canvas,
                fx,
                oy + 2f * d,
                (if (wp) sw() else width.toFloat()).coerceAtLeast(boxW.toFloat()),
                d,
                utilityPillRect
            )
        }
    }

    // ---------------- v7.0 Neighbours (same window, same frame loop, zero off-screen CPU) ----------------
    private inner class NeighbourSlot(var id: String) {
        val rig = MonkeyRig()
        val pose = Pose()
        val anim = com.combo.monkeycompanion.rig.AnimContext()
        private var cachedId = ""
        private var cachedRenderer: MonkeyRenderer? = null
        fun draw(c: Canvas, chId: String, state: MonkeyState, cx: Float, fy: Float, scale: Float, facing: Float, time: Float) {
            val ch = com.combo.monkeycompanion.character.CharacterRegistry.byId(chId) ?: return
            if (cachedId != ch.id || cachedRenderer == null) {
                cachedId = ch.id
                cachedRenderer = ch.newRenderer().also { it.wear = com.combo.monkeycompanion.character.CharacterRegistry.wear(ch) }
            }
            val w = com.combo.monkeycompanion.character.CharacterRegistry.wear(ch)
            cachedRenderer?.wear = w
            ch.fill(pose, state, time, time, anim, w)
            rig.layout(pose, cx, fy, scale, facing)
            cachedRenderer?.draw(c, rig, pose, time)
        }
    }

    private val nSlot1 = NeighbourSlot("armour")
    private val nSlot2 = NeighbourSlot("superboy")
    private var banter: com.combo.monkeycompanion.world.Director.BanterScene? = null
    private var banterT = 0f
    private var banterTurn2Said = false
    private var sideQuipBuddyId: String? = null
    private var sideQuipLine: String? = null
    private var sideQuipState: MonkeyState = MonkeyState.WAVE
    private var sideQuipUntil = 0f
    private val nPillFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = com.combo.monkeycompanion.theme.Th.rgb(0xFF, 0xF8, 0xE8) }
    private val nPillStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = com.combo.monkeycompanion.theme.Th.rgb(0x5A, 0x36, 0x18); style = Paint.Style.STROKE; strokeWidth = 2f * d }
    private val nPillText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = com.combo.monkeycompanion.theme.Th.rgb(0x3A, 0x22, 0x10); textSize = 11.5f * d; textAlign = Paint.Align.CENTER; isFakeBoldText = true }

    val debugBanter get() = banter

    /** Returns the active companion IDs for Solo (1), Duo (2), or Trio (3) mode. */
    fun activeScreenCompanionIds(): List<String> = com.combo.monkeycompanion.mind.MindHub.activeCreatures()

    val activeScreenCompanionCount: Int
        get() = activeScreenCompanionIds().size

    private fun neighbourOverlayVisible(): Boolean {
        if (hiddenTab || dragging || falling || powerOn || forcedCharacterId != null) return false
        val mode = com.combo.monkeycompanion.mind.MindHub.neighbourMode
        return banter != null || sideQuipUntil > t || (com.combo.monkeycompanion.mind.MindHub.campfire.active && com.combo.monkeycompanion.mind.MindHub.campfire.manual && mode != "SOLO")
    }

    /** Start a 2-turn neighbour banter scene inside this window. */
    fun startNeighbourBanter(scene: com.combo.monkeycompanion.world.Director.BanterScene): Boolean {
        if (host != null && (!canShowPipe() || !restAllowed())) return false
        banter = scene; banterT = 0f; banterTurn2Said = false
        controller.play(scene.leadState, scene.durSec * 0.45f)
        say(scene.leadLine, (scene.durSec * 450).toLong().coerceAtLeast(1800L))
        updateGeometry(); invalidate()
        if (!running && live && !paused) start()
        return true
    }

    private fun stepNeighbours(dt: Float) {
        val b = banter ?: return
        banterT += dt
        if (!banterTurn2Said && banterT >= b.durSec * 0.45f) {
            banterTurn2Said = true
            sideQuipBuddyId = b.buddyId
            sideQuipLine = null
            sideQuipState = b.buddyState
            sideQuipUntil = t + b.durSec * 0.55f
            say("${com.combo.monkeycompanion.world.Population.displayName(b.buddyId)}: ${b.buddyLine}", (b.durSec * 550).toLong().coerceAtLeast(2200L))
        }
        if (banterT >= b.durSec || dragging || falling || powerOn || hiddenTab) {
            banter = null
            updateGeometry()
        }
    }

    private fun drawNeighbours(canvas: Canvas) {
        if (hiddenTab || controller.state == MonkeyState.HIDDEN || forcedCharacterId != null) return
        val fy = oy + boxH - groundPad
        val nScale = rigScale * 0.88f
        val ids = com.combo.monkeycompanion.mind.MindHub.activeCreatures().filter { it != character.id }
        if (ids.isEmpty()) return

        if (host == null || (ln && banter == null)) {
            // In-app home preview or Settai Launcher home screen: show 1 (Solo), 2 (Duo), or 3 (Trio) companions clearly!
            val viewW = (if (ln) sw() else width.toFloat()).coerceAtLeast(boxW * 2.4f)
            val cxLead = ox + boxW / 2f
            val stepX = max(boxW * 0.82f, viewW * 0.26f)
            val isCatMouse = (character.id == "billa" && ids[0] == "chittu") || (character.id == "chittu" && ids[0] == "billa")
            val chaseWave = if (isCatMouse) sin(t * 3.8f) * 18f * d else 0f
            val n1X = (cxLead + stepX + chaseWave).coerceIn(boxW * 0.42f, viewW - boxW * 0.42f)
            val f1 = if (isCatMouse) (if (kotlin.math.cos(t * 3.8f) >= 0f) 1f else -1f) else -1f
            val st1 = if (isCatMouse) MonkeyState.WALK else MonkeyState.WAVE
            nSlot1.draw(canvas, ids[0], st1, n1X, fy, nScale, f1, t + 0.4f)
            if (isCatMouse) {
                val midX = (cxLead + n1X) * 0.5f
                com.combo.monkeycompanion.world.CatMouseBehaviors.drawProps(
                    canvas,
                    com.combo.monkeycompanion.world.CatMouseBehaviors.GagId.CHEESE_ACCORDION,
                    0.3f, midX, fy, rigScale, t
                )
            }
            if (ids.size >= 2) {
                val n2X = (cxLead - stepX).coerceIn(boxW * 0.42f, viewW - boxW * 0.42f)
                nSlot2.draw(canvas, ids[1], MonkeyState.DANCE, n2X, fy, nScale, 1f, t + 0.8f)
            }
            return
        }
        if (!neighbourOverlayVisible()) return
        val b = banter
        val camp = com.combo.monkeycompanion.mind.MindHub.campfire.active
        val buddyId = b?.buddyId ?: sideQuipBuddyId ?: ids.firstOrNull() ?: return
        val isCatMouse = (character.id == "billa" && buddyId == "chittu") || (character.id == "chittu" && buddyId == "billa")
        val st = when {
            camp && b == null && sideQuipUntil <= t -> MonkeyState.SIT
            b != null && !banterTurn2Said -> MonkeyState.IDLE
            isCatMouse -> MonkeyState.WALK
            else -> sideQuipState
        }
        val putRight = mx + boxW / 2f <= sw() / 2f
        val gag = b?.catMouseGag ?: (if (isCatMouse) com.combo.monkeycompanion.world.CatMouseBehaviors.GagId.CHEESE_ACCORDION else null)
        val kGag = if (b != null && b.durSec > 0f) (banterT / b.durSec).coerceIn(0f, 1f) else ((t * 0.25f) % 1f)
        val shiftFrac = if (gag != null) {
            if (character.id == "billa") com.combo.monkeycompanion.world.CatMouseBehaviors.applySlapstick(gag, kGag, t, drawPose, nSlot1.pose)
            else com.combo.monkeycompanion.world.CatMouseBehaviors.applySlapstick(gag, kGag, t, nSlot1.pose, drawPose)
        } else 0f
        val nCx = ox + boxW / 2f + (if (putRight) 1 else -1) * boxW * (0.68f - shiftFrac * 0.22f)
        val facing = if (putRight) -1f else 1f
        nSlot1.draw(canvas, buddyId, st, nCx, fy, nScale, facing, t + 0.3f)
        if (gag != null) {
            val midX = (ox + boxW / 2f + nCx) * 0.5f
            com.combo.monkeycompanion.world.CatMouseBehaviors.drawProps(canvas, gag, kGag, midX, fy, rigScale, t)
        }
        if (ids.size >= 2) {
            val buddy2Id = ids[1]
            val n2Cx = ox + boxW / 2f + (if (putRight) 1 else -1) * boxW * 1.28f
            nSlot2.draw(canvas, buddy2Id, MonkeyState.WAVE, n2Cx, fy, nScale * 0.94f, facing, t + 0.6f)
        }
    }

    // ---------------- v10.20 sulk and yawn poses (every character: they only set shared Pose values) ----------------
    private var yawnFor = 0f
    private var yawnLen = 1f
    val debugYawning get() = yawnFor > 0f
    val debugSulkPose get() = sulkPoseOn
    private var sulkPoseOn = false
    private fun applyDailyPoses(p: com.combo.monkeycompanion.rig.Pose, dt: Float) {
        if (yawnFor > 0f) {
            yawnFor = max(0f, yawnFor - dt)
            val k = 1f - yawnFor / yawnLen                      // 0..1 through the yawn
            val big = sin(PI.toFloat() * k)                     // opens, holds, closes
            p.mouthOpen = max(p.mouthOpen, 0.95f * big); p.eyeOpen = 1f - 0.85f * big; p.browAngle = -6f * big
            p.armLRaise = 14f + 150f * big; p.armRRaise = 14f + 150f * big   // a big stretch, arms up
            p.armLElbow = -8f - 20f * big; p.armRElbow = -8f - 20f * big
            p.bodyY -= 5f * big; p.squashY = 1f + 0.06f * big; p.headRot = -6f * big
            p.zzz = max(p.zzz, 0.4f * k)
        }
        sulkPoseOn = v5On && stage.sulkingNow && controller.state == MonkeyState.SAD && !dragging && !falling && impact == null
        if (sulkPoseOn) {   // arms folded across the chest, head turned away, an annoyed little pout
            p.armLRaise = 62f; p.armLElbow = 128f; p.armRRaise = 62f; p.armRElbow = 128f
            p.headRot = -14f * controller.facing; p.lookX = -6f * controller.facing; p.lookY = 1f
            p.browAngle = 16f; p.mouthSmile = -0.55f; p.mouthOpen = 0f; p.eyeOpen = 0.75f
            p.bodyRot = -5f * controller.facing
            if ((t % 4.5f) in 3.6f..4.2f) { p.lookX = 4f * controller.facing; p.headRot = 4f * controller.facing }   // peeks back now and then
        }
    }

    // ---------------- v10.20 the core: tossed -> splat on the glass / thwack the top / pancake (engine/impact) ----------------
    private var impact: com.combo.monkeycompanion.engine.impact.ImpactPlay? = null
    private var ivx = 0f; private var ivy = 0f
    private var impactN = 0
    private var impactLine: String? = null
    private var impactWord = ""
    private var impactWordX = 0f; private var impactWordY = 0f
    private val impactWordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    val debugImpactPhase get() = impact?.phase?.name
    val debugImpactKind get() = impact?.kind?.name

    /** Released high up and fast (or a flyer thrown very hard): it flies under gravity and hits whatever it reaches. */
    // ---------------- v10.24 each character's own swipe trip (engine/swipe/SwipeTravel, no RNG) ----------------
    private var swipeTrip: com.combo.monkeycompanion.engine.swipe.SwipeTravel.Trip? = null
    private val swipeFrame = com.combo.monkeycompanion.engine.swipe.SwipeTravel.Frame()
    private val swipeFx = com.combo.monkeycompanion.rig.SwipeFx()

    private fun trySwipeTrip(fvx: Float, fvy: Float): Boolean {
        if (host == null || wp || hiddenTab || profile.ridesProp) return false
        val custom = com.combo.monkeycompanion.character.CustomCompanionRegistry.byId(character.id) != null
        val style = com.combo.monkeycompanion.engine.swipe.SwipeTravel.styleFor(character.id, custom) ?: return false
        val sp = hypot(fvx, fvy)
        if (sp < SWIPE_TRIP_DP * d || dragTravel < 30 * d) return false
        if (fvy > 0f && abs(fvy) > abs(fvx) * 1.2f) return false          // mostly down: the old drop / land / bottom-hide
        if (abs(fvx) >= abs(fvy) && sp > 1400 * d) {                         // flung hard at a CLOSE edge: edge-hide still works
            val cx = mx + boxW / 2f
            if (if (fvx < 0) cx < sw() * 0.22f else cx > sw() * 0.78f) return false
        }
        val minX = 10 * d; val maxX = max(minX, sw() - boxW - 10 * d)
        val tg = com.combo.monkeycompanion.engine.swipe.SwipeTravel.target(style, mx, my, fvx, fvy, minX, maxX, hoverMinY(), ground())
        startSwipeTrip(style, tg[0], tg[1])
        return true
    }

    /** Tests / previews: start [style] to box position ([tx], [ty]). */
    fun startSwipeTrip(style: com.combo.monkeycompanion.engine.swipe.SwipeTravel.Style, tx: Float, ty: Float) {
        falling = false; vy = 0f; tweenT = -1f; tweenDone = null; arc = null; afterLand = null; impact = null
        climber.deactivate()
        val trip = com.combo.monkeycompanion.engine.swipe.SwipeTravel.Trip(style, mx, my, tx, ty, d)
        swipeTrip = trip
        controller.face(trip.dir)
        reactionLayer.clear()
        runCatching { com.combo.monkeycompanion.powers.ActionLog.add(context, "Swipe trip: ${style.label}", "${character.name}, swiped by you") }
    }

    val debugSwipeTrip get() = swipeTrip
    /** v10.25.1: a body without jets never flies the looping rocket arc when swiped: it plays its own trip to ([cx], [cy]) (centre). */
    private fun swipeTripInsteadOfArc(cx: Float, cy: Float): Boolean {
        if (character.id in Landings.ARC_IDS) return false
        val style = com.combo.monkeycompanion.engine.swipe.SwipeTravel.styleFor(character.id,
            com.combo.monkeycompanion.character.CustomCompanionRegistry.byId(character.id) != null) ?: return false
        val tx = (cx - boxW / 2f).coerceIn(10 * d, max(10 * d, sw() - boxW - 10 * d))
        val ty = if (style.hovers) (cy - boxH / 2f).coerceIn(hoverMinY(), ground()) else ground()
        startSwipeTrip(style, tx, ty); return true
    }

    private fun stepSwipeTrip(dt: Float) {
        val trip = swipeTrip ?: return
        if (dragging) { swipeTrip = null; return }
        trip.step(dt)
        trip.frame(swipeFrame)
        mx = swipeFrame.x; my = swipeFrame.y
        if (trip.done) {
            swipeTrip = null
            mx = trip.x1; my = trip.y1
            if (trip.style.hovers) { surf = freeSurf(my); clampToSurf() }
            else if (my < ground() - 4 * d) { surf = freeSurf(my); beginSmartFall() }   // v10.25: appeared up high -> falls and lands
            else { surf = screenSurf(SK.FLOOR); clampToSurf(); my = ground() }
        }
    }

    private fun tryToss(fvx: Float, fvy: Float, force: Boolean = false): Boolean {
        if (host == null || wp || profile.ridesProp || hiddenTab) return false
        if (!force) return false   // v10.25: only the fling rule in settleAfterDrag decides a splat / thwack
        val spDp = hypot(fvx, fvy) / d
        val screenH = host?.screenHeight()?.toFloat() ?: return false
        if (fvy > 0f && abs(fvy) > abs(fvx) * 1.2f) return false   // mostly downward: the old drop / bottom-hide
        impact = com.combo.monkeycompanion.engine.impact.ImpactPlay().also { it.spin = (fvx / d * 0.3f).coerceIn(-720f, 720f) }
        ivx = fvx; ivy = fvy
        if (fvy < 0f && abs(fvy) > abs(fvx)) {   // v10.25 aimed above the top: make sure it really reaches the top edge
            val need = kotlin.math.sqrt(2f * com.combo.monkeycompanion.engine.impact.ImpactPlay.GRAVITY_DP * d * max(1f, my - topY())) * 1.15f
            if (-ivy < need) ivy = -need
        }
        falling = false; vy = 0f; tweenT = -1f; tweenDone = null; arc = null; afterLand = null
        climber.deactivate(); surf = freeSurf(my)
        reactionLayer.clear()
        runCatching { com.combo.monkeycompanion.powers.ActionLog.add(context, "Toss", "${spDp.toInt()} dp/s from ${((ground() - my) / screenH * 100).toInt()}% up") }
        return true
    }

    private fun stepImpact(dt: Float) {
        val ip = impact ?: return
        if (dragging) { impact = null; return }
        val g = com.combo.monkeycompanion.engine.impact.ImpactPlay.GRAVITY_DP * d
        val maxX = max(0f, sw() - boxW)
        when (ip.phase) {
            com.combo.monkeycompanion.engine.impact.ImpactPlay.Phase.FLIGHT -> {
                ivy += g * dt; mx += ivx * dt; my += ivy * dt
                when {
                    mx <= 0f -> { mx = 0f; impactHit(ip, ImpactPlay.Kind.SPLAT, -1) }
                    mx >= maxX -> { mx = maxX; impactHit(ip, ImpactPlay.Kind.SPLAT, 1) }
                    my <= topY() -> { my = topY(); impactHit(ip, ImpactPlay.Kind.THWACK, 0) }
                    my >= ground() -> {
                        my = ground()
                        if (ivy / d >= com.combo.monkeycompanion.engine.impact.ImpactPlay.PANCAKE_SPEED_DP) impactHit(ip, ImpactPlay.Kind.PANCAKE, 0)
                        else { softImpactLanding(); return }
                    }
                    ip.total > 4f -> { softImpactLanding(); return }
                }
            }
            com.combo.monkeycompanion.engine.impact.ImpactPlay.Phase.SLIDE -> {
                my = min(ground(), my + ip.slideDp * d * dt)
                if (my >= ground()) ip.slideAtFloor()
            }
            com.combo.monkeycompanion.engine.impact.ImpactPlay.Phase.FALL -> {
                ivy += g * dt; my += ivy * dt; mx = (mx + ivx * dt).coerceIn(0f, maxX)
                if (my >= ground()) {
                    my = ground(); ip.landed()
                    feetScreen(tmpPt); impactPuff(tmpPt[0], tmpPt[1], 0f, 6)
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            }
            else -> Unit
        }
        when (ip.step(dt)) {
            com.combo.monkeycompanion.engine.impact.ImpactPlay.Event.START_FALL -> {
                ivy = if (ip.kind == ImpactPlay.Kind.THWACK) 0f else -90f * d
                ivx = if (ip.kind == ImpactPlay.Kind.SPLAT) -ip.side * 110f * d else 0f
            }
            com.combo.monkeycompanion.engine.impact.ImpactPlay.Event.DONE -> finishImpact()
            else -> Unit
        }
    }

    private fun impactHit(ip: com.combo.monkeycompanion.engine.impact.ImpactPlay, k: com.combo.monkeycompanion.engine.impact.ImpactPlay.Kind, side: Int) {
        if (!ip.hit(k, side)) return
        ivx = 0f; ivy = 0f
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        if (!silent) runCatching { com.combo.monkeycompanion.audio.Sfx.play(context, com.combo.monkeycompanion.audio.Sfx.S.CRACK, 0.35f) }
        // contact point in screen pixels (for the dust) and in view pixels (for the comic word)
        val (vx0, vy0) = when (k) {
            ImpactPlay.Kind.SPLAT -> (if (side < 0) ox else ox + boxW) to oy + boxH * 0.5f
            ImpactPlay.Kind.THWACK -> ox + boxW / 2f to oy
            ImpactPlay.Kind.PANCAKE -> ox + boxW / 2f to oy + boxH - groundPad
        }
        impactPuff(vx0 + winX, vy0 + winY, if (k == ImpactPlay.Kind.SPLAT) -side.toFloat() else 0f, 10)
        impactWord = when (k) { ImpactPlay.Kind.SPLAT -> "SPLAT!"; ImpactPlay.Kind.THWACK -> "THWACK!"; ImpactPlay.Kind.PANCAKE -> "BONK!" }
        impactWordX = vx0 + (if (k == ImpactPlay.Kind.SPLAT) -side * 60f * d else 0f); impactWordY = vy0 - 40f * d
        impactLine = com.combo.monkeycompanion.engine.impact.ImpactPlay.line(k, impactN++)
        val what = when (k) {
            ImpactPlay.Kind.SPLAT -> "splatted flat against the ${if (side < 0) "left" else "right"} edge of the screen, now sliding down the glass"
            ImpactPlay.Kind.THWACK -> "smacked head-first into the top edge of the screen"
            ImpactPlay.Kind.PANCAKE -> "slammed into the floor and got squashed flat like a chapati"
        }
        BSN.record("I $what", BSN.Feeling.OUCH)
        feelTouch(com.combo.monkeycompanion.ai.react.ReactKind.BUMP, force = 1f, extra = what, doing = "flying through the air after you tossed me")
        runCatching { com.combo.monkeycompanion.powers.ActionLog.add(context, "Impact", k.name.lowercase()) }
        runCatching { com.combo.monkeycompanion.fun5.Fun5Store.noteImpact(context, k.name) }   // daily challenge progress
    }

    /** Dust from the contact point; deterministic offsets (no Random). [dirX] pushes it away from a wall. */
    private fun impactPuff(sx: Float, sy: Float, dirX: Float, n: Int) {
        if (saver) return
        val P = com.combo.monkeycompanion.rig.Particles
        for (i in 0 until n) {
            val a = i / n.toFloat()
            val spreadX = if (dirX != 0f) dirX * (90f + 180f * a) else (if (i % 2 == 0) 1f else -1f) * (100f + 200f * a)
            val spreadY = if (dirX != 0f) (a - 0.5f) * 360f else -(30f + 80f * a)
            particles.spawn(P.DUST, sx, sy + (a - 0.5f) * 20f * d, spreadX * d, spreadY * d, 0.5f, (5f + 4f * a) * d)
        }
    }

    /** Thrown too gently to hit anything: a normal landing. */
    private fun softImpactLanding() {
        impact = null
        surf = screenSurf(SK.FLOOR); clampToSurf()
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        controller.onLanded()
    }

    private fun finishImpact() {
        impact = null
        surf = screenSurf(SK.FLOOR); clampToSurf()
        controller.idleNow(2.5f)
        controller.touched()
        val line = impactLine; impactLine = null
        if (line != null && !aiVoiceOn()) say(line, 2600L)   // Brain on: the reaction engine already spoke
    }

    /** The comic word ("SPLAT!") pops at the contact point and fades out. */
    private fun drawImpactWord(c: Canvas) {
        val ip = impact ?: return
        val k = ip.bang
        if (k <= 0f || impactWord.isEmpty()) return
        val pop = if (k > 0.85f) 0.6f + (1f - k) / 0.15f * 0.6f else 1.2f - (0.85f - k) * 0.2f
        val maxW = (if (wp) sw() else winW.toFloat())
        impactWordPaint.textSize = 26f * d * pop
        val x = impactWordX.coerceIn(60f * d, max(60f * d, maxW - 60f * d)); val y = max(30f * d, impactWordY)
        impactWordPaint.style = Paint.Style.STROKE; impactWordPaint.strokeWidth = 5f * d; impactWordPaint.color = Color.rgb(0x2A, 0x16, 0x00)
        impactWordPaint.alpha = (255 * min(1f, k * 2f)).toInt()
        c.drawText(impactWord, x, y, impactWordPaint)
        impactWordPaint.style = Paint.Style.FILL; impactWordPaint.color = Color.rgb(0xFF, 0xD2, 0x3F)
        impactWordPaint.alpha = (255 * min(1f, k * 2f)).toInt()
        c.drawText(impactWord, x, y, impactWordPaint)
    }

    /** How the body is turned on a wall / the ceiling / an edge crash (drawMonkey and the chat button share it). */
    private fun bodyEdgeRot(): Float = when {
        impact != null -> 0f   // v10.20: the impact draws its own spin / tip
        isEdgeCrashLanding && edgeThrustFailurePhase in 1..2 -> edgeCrashRot
        !climber.active && !falling && surf.kind == SK.WALL_L -> 90f
        !climber.active && !falling && surf.kind == SK.WALL_R -> -90f
        !climber.active && !falling && surf.kind == SK.CEIL -> 180f
        else -> 0f
    }

    /** Settai only (no bubble), e.g. for the wallpaper preview. */
    /** v10.16: lifted by a friend's magic (Kutti Satan's float-a-buddy); expires by itself if the caster stops. */
    private var levAmt = 0f
    private var levAt = 0L
    private val levPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    fun levitateBy(a: Float) { levAmt = a.coerceIn(0f, 1f); levAt = android.os.SystemClock.uptimeMillis(); postInvalidateOnAnimation() }

    fun drawMonkey(canvas: Canvas, clipSink: Boolean = true) {
        if (hiddenTab || (controller.state == MonkeyState.HIDDEN && clipSink)) return
        canvas.save()
        val lev = if (android.os.SystemClock.uptimeMillis() - levAt < 400L) levAmt else 0f
        if (lev > 0.01f) {
            canvas.translate(0f, -lev * (12f * d + 3f * d * sin(t * 3f)))
            val cx = ox + boxW / 2f; val cy = oy + boxH * 0.62f
            for (i in 0 until 7) {
                val ang = t * 1.8f + i * 0.9f
                levPaint.color = if (i % 3 == 0) Color.WHITE else Color.rgb(0xE5, 0x1C, 0x2A)
                levPaint.alpha = (220 * lev).toInt()
                canvas.drawCircle(cx + kotlin.math.cos(ang) * boxW * 0.32f, cy + sin(ang) * 8f * d + boxH * 0.18f * sin(t * 1.3f + i) * 0.5f, (2f + 1.2f * sin(t * 7f + i)) * d, levPaint)
            }
        }
        if (clipSink && !sinkClipY.isNaN() && sinkY > 0f) canvas.clipRect(-10000f, -10000f, 100000f, sinkClipY)
        // Armour Anna's heavy footsteps: a tiny jolt on each footfall.
        heavyShake = if (character.heavy && controller.state == MonkeyState.WALK && !climber.active && !hoverOn) {
            val ph = abs(sin(controller.stateTime * 6.2f * controller.anim.walkSpeed)); if (ph < 0.18f) (1f - ph / 0.18f) * 1.8f * d else 0f
        } else 0f
        canvas.translate(sinkX, (if (climber.active) sinkY else 0f) + heavyShake)
        val edgeRot = bodyEdgeRot()
        if (edgeRot != 0f) {
            canvas.rotate(edgeRot, ox + boxW / 2f, oy + boxH / 2f)
        }
        impact?.let { ip ->   // v10.20: squash flat against the glass / under the top edge / on the floor
            if (ip.rot != 0f) canvas.rotate(ip.rot, ox + boxW / 2f, oy + boxH / 2f)
            if (ip.sx != 1f || ip.sy != 1f) {
                val (px, py) = when (ip.pivot) {
                    com.combo.monkeycompanion.engine.impact.ImpactPlay.Pivot.WALL_LEFT -> ox to oy + boxH * 0.55f
                    com.combo.monkeycompanion.engine.impact.ImpactPlay.Pivot.WALL_RIGHT -> ox + boxW to oy + boxH * 0.55f
                    com.combo.monkeycompanion.engine.impact.ImpactPlay.Pivot.TOP -> ox + boxW / 2f to oy
                    com.combo.monkeycompanion.engine.impact.ImpactPlay.Pivot.FEET -> ox + boxW / 2f to oy + boxH - groundPad
                    com.combo.monkeycompanion.engine.impact.ImpactPlay.Pivot.CENTER -> ox + boxW / 2f to oy + boxH / 2f
                }
                canvas.scale(ip.sx, ip.sy, px, py)
            }
        }
        val trip = swipeTrip
        var tripLayer = false
        if (trip != null) {   // v10.24 swipe trip: scale / spin / fade about the feet, props behind
            val fr = swipeFrame
            val fx = ox + boxW / 2f; val fy = oy + boxH - groundPad
            swipeFx.puffRed = character.id != "angel"
            swipeFx.drawBehind(canvas, fr, fx, fy, boxH * 0.7f, d, t)
            if (fr.sink > 0f) { canvas.clipRect(-10000f, -10000f, 100000f, fy + 2f * d); canvas.translate(0f, fr.sink) }
            if (fr.rot != 0f) canvas.rotate(fr.rot, fx, fy - boxH * 0.3f)
            if (fr.scale != 1f) canvas.scale(fr.scale, fr.scale, fx, fy)
            if (fr.alpha < 0.999f) { canvas.saveLayerAlpha(-10000f, -10000f, 100000f, 100000f, (255 * fr.alpha).toInt()); tripLayer = true }
        }
        if ((falling && landBeat <= 0f && coyoteScareTimer <= 0f || hoverOn) && !climber.active) drawLandingThrust(canvas)
        if (climber.active && character.id == "monkey") {
            canvas.translate(-winX.toFloat(), -winY.toFloat())
            drawWithSoot(canvas) { climber.draw(canvas, drawPose, t, renderer) }
        } else drawWithSoot(canvas) { renderer.draw(canvas, rig, drawPose, t) }
        drawHeldBalls(canvas)
        if (tripLayer) canvas.restore()
        if (trip != null) {
            val fr = swipeFrame
            canvas.save()
            if (fr.sink > 0f) canvas.translate(0f, -fr.sink)
            if (fr.scale != 1f) canvas.scale(1f / fr.scale, 1f / fr.scale, ox + boxW / 2f, oy + boxH - groundPad)
            if (fr.rot != 0f) canvas.rotate(-fr.rot, ox + boxW / 2f, oy + boxH - groundPad - boxH * 0.3f)
            swipeFx.drawFront(canvas, fr, ox + boxW / 2f, oy + boxH - groundPad, boxH * 0.7f, d, t, trip.dir)
            canvas.restore()
        }
        if (falling && coyoteScareTimer > 0f) {
            drawCoyoteScareOverlay(canvas)
        }
        if (isEdgeCrashLanding && edgeThrustFailurePhase == 3) {
            drawEdgeCrashStarsOverlay(canvas)
        }
        if (impact?.stars == true) drawEdgeCrashStarsOverlay(canvas)   // v10.20 dizzy
        canvas.restore()
        drawImpactWord(canvas)
    }

    private fun drawCoyoteScareOverlay(c: Canvas) {
        rig.headTop(head)
        val hx = head[0]
        val hy = (head[1] - 6f * d).coerceAtLeast(18f * d)
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = Color.argb(225, 0xFF, 0xEB, 0x3B)
        tmpR.set(hx - 34f * d, hy - 14f * d, hx + 34f * d, hy + 6f * d)
        c.drawRoundRect(tmpR, 8f * d, 8f * d, btnPaint)
        btnPaint.style = Paint.Style.STROKE; btnPaint.strokeWidth = 1.6f * d; btnPaint.color = Color.rgb(0xD5, 0x00, 0x00)
        c.drawRoundRect(tmpR, 8f * d, 8f * d, btnPaint)
        btnText.color = Color.rgb(0xB7, 0x1C, 0x1C); btnText.textSize = 10.5f * d; btnText.isFakeBoldText = true
        c.drawText("😱 YIKES! 1s", hx, hy - 1f * d, btnText)
    }

    private fun drawEdgeCrashStarsOverlay(c: Canvas) {
        rig.headTop(head)
        val hx = head[0]
        val hy = (head[1] + 4f * d).coerceAtLeast(20f * d)
        btnText.color = Color.rgb(0xFF, 0xD5, 0x4F); btnText.textSize = 12.5f * d; btnText.isFakeBoldText = true
        for (i in 0 until 3) {
            val ang = t * 5.5f + i * (2f * PI.toFloat() / 3f)
            val sx = hx + kotlin.math.cos(ang) * 24f * d
            val sy = hy + sin(ang) * 7f * d
            c.drawText(if (i % 2 == 0) "⭐" else "💫", sx, sy, btnText)
        }
    }

    private fun drawChatBtn(canvas: Canvas) {
        val hint = hintUntil > 0f && chatBtnUntil <= 0f
        val left = if (hint) hintUntil else chatBtnUntil
        val a = (min(1f, left * 3f) * 255).toInt()
        val pop = if (hint) 1f + 0.12f * sin(t * 7f)
            else if (smoothAnim) 0.2f + 0.8f * com.combo.monkeycompanion.engine.anim.Easing.easeOutBack((4f - chatBtnUntil) * 4f, profile.anim.overshoot)
            else min(1f, (4f - chatBtnUntil) * 6f).coerceIn(0.2f, 1f)
        val r = chatBtnR * pop
        var bx = chatBtnX(); var by = chatBtnY()
        if (climber.active) { climber.headTop(tmpPt); bx = tmpPt[0] - winX + sinkX + (if (surf.kind == SK.WALL_R) -40f else 40f) * d; by = tmpPt[1] - winY + 6 * d }
        bx = bx.coerceIn(r + 2 * d, max(r + 2 * d, (if (wp) sw() else winW.toFloat()) - r - 2 * d))
        by = by.coerceIn(r + 2 * d, max(r + 2 * d, (if (wp) (host?.screenHeight() ?: height).toFloat() else winH.toFloat()) - r - 2 * d))
        lastBtnX = bx; lastBtnY = by
        if (hint) {  // soft pulsing ring
            btnPaint.style = Paint.Style.STROKE; btnPaint.strokeWidth = 3 * d; btnPaint.color = com.combo.monkeycompanion.theme.Th.rgb(0xE8, 0x8A, 0x2E)
            val ring = (t * 1.3f) % 1f
            btnPaint.alpha = ((1f - ring) * 0.8f * a).toInt()
            canvas.drawCircle(bx, by, r * (1f + ring * 0.8f), btnPaint)
        }
        btnPaint.style = Paint.Style.FILL; btnPaint.color = com.combo.monkeycompanion.theme.Th.rgb(0xE8, 0x8A, 0x2E); btnPaint.alpha = a
        canvas.drawCircle(bx, by, r, btnPaint)
        btnPaint.style = Paint.Style.STROKE; btnPaint.strokeWidth = 2 * d; btnPaint.color = Color.WHITE; btnPaint.alpha = a
        canvas.drawCircle(bx, by, r, btnPaint)
        btnText.alpha = a; btnText.textSize = 17 * d * pop
        canvas.drawText("\uD83D\uDCAC", bx, by - (btnText.descent() + btnText.ascent()) / 2, btnText)
    }
    private var lastBtnX = 0f; private var lastBtnY = 0f

    /** The edge tab: a translucent handle with an arrow, and a little ear + eyes peeking over it. */
    private fun drawTab(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.save()
        // Rotate so the tab always "points" into the screen along +x in local space.
        when (hideSide) {
            -1 -> Unit
            1 -> { c.translate(w, 0f); c.scale(-1f, 1f) }
            else -> { c.translate(0f, h); c.rotate(-90f) }
        }
        val tw = tabW                           // thickness (the window grows while peeking)
        val tl = if (hideSide == 2) w else h   // length along the edge
        tabPaint.style = Paint.Style.FILL
        tabPaint.color = com.combo.monkeycompanion.theme.Th.argb(150, 0x3A, 0x26, 0x18)
        tmpR.set(-tw, 0f, tw * 0.95f, tl); c.drawRoundRect(tmpR, tw * 0.6f, tw * 0.6f, tabPaint)
        // peeking face: half a head with ear and two eyes (slides further out during a peek)
        val peek = tabPeekAmount()
        c.translate(peek * tw * 0.95f, 0f)
        // v4.5: each character draws its own peek (wardrobe included).
        peekState.tw = tw; peekState.cy = tl * 0.36f; peekState.t = tabPeekT; peekState.density = d
        peekState.blink = tabPeekT >= 0f && (tabPeekT % 1.6f) in 0.9f..1.0f
        peekState.look = if (tabPeekT >= 0f) 0.07f * tw * sin(tabPeekT * 3.1f) else 0.02f * tw
        peekState.bino = if (tabPeekBino && peek > 0.3f) ((peek - 0.3f) / 0.7f).coerceIn(0f, 1f) else 0f
        character.peek.draw(c, wear, peekState)
        c.translate(-peek * tw * 0.95f, 0f)
        // arrow chevron pointing into the screen
        tabPaint.style = Paint.Style.STROKE; tabPaint.strokeWidth = 2.6f * d; tabPaint.strokeCap = Paint.Cap.ROUND
        tabPaint.color = com.combo.monkeycompanion.theme.Th.argb(220, 255, 255, 255)
        val ay = tl * 0.74f
        tabPath.reset(); tabPath.moveTo(tw * 0.28f, ay - tw * 0.3f); tabPath.lineTo(tw * 0.6f, ay); tabPath.lineTo(tw * 0.28f, ay + tw * 0.3f)
        c.drawPath(tabPath, tabPaint)
        c.restore()
        // Unread notification badge on the edge tab (drawn in un-flipped coordinates so the count is never mirrored)
        val unread = com.combo.monkeycompanion.agent.NotifStore.count
        debugTabBadgeCount = unread
        if (unread > 0) {
            val br = 9.5f * d
            val bx = if (hideSide == 2) w * 0.78f else w * 0.5f
            val by = if (hideSide == 2) h * 0.5f else h * 0.78f
            tabPaint.style = Paint.Style.FILL; tabPaint.color = com.combo.monkeycompanion.theme.Th.rgb(0x8E, 0x24, 0xAA)
            c.drawCircle(bx, by, br, tabPaint)
            tabPaint.style = Paint.Style.STROKE; tabPaint.strokeWidth = 1.6f * d; tabPaint.color = Color.WHITE
            c.drawCircle(bx, by, br, tabPaint)
            btnText.color = Color.WHITE; btnText.textSize = 10.5f * d; btnText.isFakeBoldText = true
            val lbl = if (unread > 9) "9+" else "$unread"
            c.drawText(lbl, bx, by - (btnText.descent() + btnText.ascent()) / 2f, btnText)
        }
    }

    // ---------------- Touch ----------------

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val doubleTapTimeout = 280L
    private var downRawX = 0f; private var downRawY = 0f
    private var downMx = 0f; private var downMy = 0f
    private var downZone = TouchZone.NONE
    private var downSide = 1
    private var tracking = false
    private var lastMoveRawX = 0f; private var lastMoveRawY = 0f; private var lastMoveTime = 0L
    private var vx = 0f; private var vyDrag = 0f
    /** v6: recent finger samples (time ms, raw x, raw y) for a robust release velocity / swipe distance. */
    private val gHist = LongArray(16); private val gHx = FloatArray(16); private val gHy = FloatArray(16); private var gN = 0
    private fun gPush(tm: Long, x: Float, y: Float) { val i = gN % 16; gHist[i] = tm; gHx[i] = x; gHy[i] = y; gN++ }
    /** Finger motion over the last [ms] before [now]: (dx, dy, dtMs) in screen px. */
    private fun gWindow(now: Long, ms: Long, out: FloatArray): FloatArray {
        out[0] = 0f; out[1] = 0f; out[2] = 0f
        if (gN < 2) return out
        val last = (gN - 1) % 16
        var j = last
        for (k in 1 until minOf(gN, 16)) { val i = (gN - 1 - k) % 16; if (now - gHist[i] > ms) break; j = i }
        out[0] = gHx[last] - gHx[j]; out[1] = gHy[last] - gHy[j]; out[2] = (gHist[last] - gHist[j]).toFloat()
        return out
    }
    private val gTmp = FloatArray(3)
    /** v6: swipe-down test that works on real phones: >= 600 dp/s downward, or a downward swipe of > 80 dp. */
    private fun swipeDownNow(upTime: Long, fvx: Float, fvy: Float): Float {
        gWindow(upTime, 120L, gTmp)
        val stale = gN == 0 || upTime - gHist[(gN - 1) % 16] > 150L
        val wvx = if (gTmp[2] > 0f && !stale) gTmp[0] * 1000f / gTmp[2] else 0f
        val wvy = if (gTmp[2] > 0f && !stale) gTmp[1] * 1000f / gTmp[2] else 0f
        val vyBest = maxOf(fvy, wvy); val vxAbs = maxOf(abs(fvx), abs(wvx))
        if (vyBest >= 600f * d && vyBest > vxAbs * 0.8f) return vyBest
        gWindow(upTime, 300L, gTmp)
        if (!stale && gTmp[1] > 80f * d && gTmp[1] > abs(gTmp[0])) return maxOf(vyBest, gTmp[1] * 1000f / gTmp[2].coerceAtLeast(16f))
        return -1f
    }
    private var pendingTap: Runnable? = null
    private var lastTapUp = 0L
    private var lastTapX = 0f; private var lastTapY = 0f
    private var tabTracking = false
    private var tabMoved = false
    private var downTabPos = 0f

    /**
     * Long-press without moving (v4.3): the options menu (overlay; ✕ = quit). Chat stays on double-tap, the bubble's
     * reply field and the app. Wallpaper / in-app preview: just a reminder that double-tap chats.
     */
    private val longPress = Runnable {
        if (tracking && !dragging) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            longPressed = true
            BSN.record("you're holding me", BSN.Feeling.HELD)
            if (host != null && !wp) openMenu()   // v4.3: long-press = options menu (✕ Quit lives there)
            else { say(com.combo.monkeycompanion.brain.Gestures.coachLine(false), 3000L); hintUntil = 4f }
        }
    }

    // ---------------- Quit (v3.2) ----------------

    private var quitAsk = false
    private var exiting = false
    private var exitDone = false
    private var exitKind = com.combo.monkeycompanion.brain.Exit.POOF

    /** The "Quit Settai?" question with Yes / No in the bubble. Mentions Settai's own timers/alarms only if there are any. */
    private fun askQuit() {
        if (exiting || (pendingShown && !quitAsk)) return
        endTyping(); pages.clear(); beats.clear(); thinkingBubble = false; chatBtnUntil = 0f; hintUntil = 0f
        val items = runCatching { com.combo.monkeycompanion.powers.SettaiClock.items(context) }.getOrDefault(emptyList())
        val alarms = items.count { it.kind == "alarm" }; val timers = items.size - alarms
        val parts = ArrayList<String>()
        if (timers > 0) parts.add(if (timers == 1) "your timer" else "your $timers timers")
        if (alarms > 0) parts.add(if (alarms == 1) "your alarm" else "your $alarms alarms")
        val note = if (parts.isEmpty()) "" else " (Don't worry, ${parts.joinToString(" and ")} will still ring.)"
        showBubble(Lines.pick(Lines.QUIT_ASK) + note, 20f, listOf("Yes", "No"), reply = false)
        pendingShown = true; quitAsk = true
        val st = controller.state
        if (st == MonkeyState.IDLE || st == MonkeyState.WALK || st == MonkeyState.SIT) controller.play(MonkeyState.SAD, 2.5f)
    }

    private fun answerQuit(yes: Boolean) {
        quitAsk = false; pendingShown = false
        if (yes) { startExit(); return }
        bubbleLife = bubbleAge
        say(Lines.pick(Lines.QUIT_STAY), 2600L)
        controller.play(MonkeyState.HAPPY_PAT, 1.8f)
    }

    /** Yes: a random exit routine, then the host removes Settai (no restart until the app brings it back). */
    private fun startExit() {
        if (exiting || host == null) return
        exiting = true
        endTyping(); pages.clear(); beats.clear(); thinkingBubble = false; pendingShown = false
        chatBtnUntil = 0f; hintUntil = 0f; restGoIn = -1f
        cancelRestTimers(); ui.removeCallbacks(longPress)
        endEntrance()
        exitKind = com.combo.monkeycompanion.brain.Exit.entries.random()
        if (hiddenTab) { finishExit(); return }
        if (surf.kind != SK.FLOOR || climber.active || falling) afterLandThen { beginExit() } else beginExit()
        if (!paused) start()
    }

    private fun beginExit() {
        controller.anim.exit = exitKind
        controller.play(MonkeyState.EXIT)
        val lines = when (exitKind) {
            com.combo.monkeycompanion.brain.Exit.FAINT -> Lines.EXIT_FAINT
            com.combo.monkeycompanion.brain.Exit.ROCKET -> Lines.EXIT_ROCKET
            com.combo.monkeycompanion.brain.Exit.POOF -> Lines.EXIT_POOF
        }
        say(Lines.pick(lines), 2600L)
    }

    private fun finishExit() {
        if (exitDone) return
        exitDone = true
        stop()
        EnergyMeter.mode = "quit"
        host?.quitSettai()
    }

    /** Exit in progress: rocket gets a tall window (it flies to the top of the screen); done -> quit. */
    private fun stepExit(): Boolean {
        if (!exiting || controller.state != MonkeyState.EXIT) return false
        val st = controller.stateTime
        if (exitKind == com.combo.monkeycompanion.brain.Exit.ROCKET && st > 1.3f) bubbleOn = false
        if (st >= exitKind.seconds + 0.15f) { finishExit(); return true }
        return false
    }

    // ---------------- Superpowers (v3.2) ----------------

    private val flight = PowerFlight()
    private val particles = Particles()
    private val emitter = PowerEmitter(particles)
    private var powerOn = false
    private var powerVx = 0f
    private var powerVy = 0f
    private var powerLineAt = -1
    private var crashSaid = false
    private var landSaid = false
    /** v10.9: only paces AUTOMATIC powers (entrances, idle, the mind); the user is never blocked (no cooldown). */
    private var autoPowerGap = 0f
    private var powerIn = nextRandomPower()
    private var powerQueued = false

    private fun nextRandomPower() = 420f + Random.nextFloat() * 480f      // 7-15 min of awake idling

    /** "off", "random" or a power id (Settings). */
    private fun powerSetting() = runCatching { ChatHub.settings.power }.getOrDefault(Superpowers.RANDOM)

    /**
     * Use a superpower now. [explicit] = the user (chat keyword, AI tag, Settings "Use power") asked:
     * answers with a line when powers are Off or still recharging. [force] = this power.
     */
    fun usePower(explicit: Boolean, force: Superpower? = null): Boolean {
        if (explicit && powerOn && !exiting && !quitAsk && host != null && !wp) {
            // v10.9: no cooldown, whenever you want: chain straight into the next power from where she is now
            val pw = force ?: Superpowers.choose(powerSetting()) ?: character.powers.firstOrNull() ?: return false
            powerOn = false; flight.cancel()
            com.combo.monkeycompanion.mind.MindHub.event(com.combo.monkeycompanion.mind.MindEvent.POWER_USED, pw.title)
            beginPower(pw)
            return true
        }
        if (exiting || quitAsk || powerOn || powerQueued) return false
        val pw = force ?: Superpowers.choose(powerSetting())
        if (pw == null) { if (explicit) say(Lines.pick(Lines.POWER_OFF), 4000L); return false }
        if (autoPowerGap > 0f && !explicit) return false
        if (host == null) { say(Lines.pick(pw.startLines), 2500L); controller.play(MonkeyState.JUMP, 0.75f); return true }   // in-app preview
        if (hiddenTab) { if (explicit) unhide() else if (!autoUnhide()) return false; powerQueued = true; ui.postDelayed({ powerQueued = false; usePower(explicit, pw) }, 1700L); return true }
        if (controller.isAsleep) {
            if (!explicit) return false
            controller.wakeUp(Lines.pick(Lines.POKED_AWAKE)); powerQueued = true
            ui.postDelayed({ powerQueued = false; usePower(true, pw) }, 6200L); return true
        }
        if (dragging || typing || entKind != EntKind.NONE || hideAnim >= 0f) return false
        if (surf.kind != SK.FLOOR || climber.active || falling || tweenT >= 0f) { powerQueued = true; afterLandThen { powerQueued = false; beginPower(pw) }; return true }
        if (host != null && !wp) com.combo.monkeycompanion.mind.MindHub.event(com.combo.monkeycompanion.mind.MindEvent.POWER_USED, pw.title)
        beginPower(pw)
        return true
    }

    private fun fracX(px: Float) = (px / max(1f, sw() - boxW)).coerceIn(0f, 1f)
    private fun fracY(py: Float) = ((ground() - py) / max(1f, ground() - topY())).coerceIn(0f, 1f)
    private fun pxX(f: Float) = f * max(0f, sw() - boxW)
    private fun pxY(f: Float) = ground() - f * max(0f, ground() - topY())

    private fun beginPower(pw: Superpower) {
        if (powerOn || exiting || host == null) return
        val crash = Random.nextFloat() < pw.crashChance
        com.combo.monkeycompanion.ai.BodySense.record("I used my power " + pw.title, com.combo.monkeycompanion.ai.BodySense.Feeling.PUFFED)
        val x0 = fracX(mx); val y0 = fracY(my)
        flight.begin(pw, pw.plan(x0, y0, crash, Random), crash, x0, y0)
        powerOn = true; powerLineAt = -1; crashSaid = false; landSaid = false
        powerVx = 0f; powerVy = 0f
        emitter.reset(); particles.clear()
        climber.deactivate(); tweenT = -1f; falling = false; afterLand = null
        endTyping(); pages.clear(); beats.clear(); thinkingBubble = false; chatBtnUntil = 0f; hintUntil = 0f
        restGoIn = -1f
        val a = controller.anim
        a.power = pw; a.crashed = false; a.powerPhase = flight.phase; a.powerK = 0f; a.speed = 0f; a.heading = 0f
        controller.play(MonkeyState.POWER)
        if (pw == Superpower.Magician) {
            val n = runCatching { com.combo.monkeycompanion.agent.NotifStore.count }.getOrDefault(0)
            renderer.rabbitSign = if (n > 99) "99+" else n.toString()
        }
        if (pw == com.combo.monkeycompanion.brain.SuperStrengthPower) liftCount = runCatching { com.combo.monkeycompanion.agent.NotifStore.count }.getOrDefault(0)
        val pct = (renderer as? com.combo.monkeycompanion.rig.ArmourRenderer)?.batteryPct ?: -1
        if (entrancePipe != null) { say(com.combo.monkeycompanion.brain.PowerEntrances.herald(entranceKind), 2200L); if (!paused) start(); return }
        say(Lines.pick(pw.startLines).replace("{pct}", if (pct >= 0) "$pct" else "low"), 2200L)
        if (!paused) start()
    }

    /** One flight frame: move along the plan, steer the pose, say the lines. False = not flying. */
    private fun stepPower(dt: Float): Boolean {
        if (autoPowerGap > 0f) autoPowerGap -= dt
        if (!powerOn) return false
        val pw = flight.power ?: run { endPower(); return false }
        if (controller.state != MonkeyState.POWER) controller.play(MonkeyState.POWER)
        if (!flight.update(dt)) { endPower(); return false }
        val nx = pxX(flight.x); val ny = pxY(flight.y)
        if (dt > 0f) {
            val f = min(1f, dt * 10f)
            powerVx += ((nx - mx) / dt - powerVx) * f; powerVy += ((ny - my) / dt - powerVy) * f
        }
        mx = nx; my = ny
        val a = controller.anim
        val ph = flight.phase; val k = flight.k
        a.powerPhase = ph; a.powerK = k; a.crashed = flight.crashed
        a.carveLean = flight.glideLean; a.glideSpeed = flight.glideSpeed; a.gliding = (flight.step?.glide ?: 0f) > 0f
        // v10.16 Kutti Satan's "float a buddy": the nearest crew buddy rises on red sparkles (visual only, self-expiring)
        if (pw.id == "floatbuddy") partnerViews.firstOrNull()?.levitateBy(min(1f, k / 0.15f) * min(1f, (1f - k) / 0.15f))
        a.sinceRing = emitter.sinceRing; a.ringLeft = emitter.leftHand
        val sp = hypot(powerVx, powerVy)
        a.speed = (sp / (900f * d)).coerceIn(0f, 1f)
        val moving = ph == PowerPhase.FLY || ph == PowerPhase.SPUTTER || ph == PowerPhase.TAKEOFF ||
            (ph == PowerPhase.CRASH && k < PowerFlight.CRASH_HIT)
        if (moving && sp > 40f * d) {
            a.heading = Math.toDegrees(kotlin.math.atan2(abs(powerVx).toDouble(), (-powerVy).toDouble())).toFloat()
            if (abs(powerVx) > 60f * d) controller.face(if (powerVx > 0f) 1f else -1f)
        }
        if (flight.index != powerLineAt) {
            powerLineAt = flight.index
            if (entrancePipe == null) when (ph) {
                PowerPhase.FIRE -> if (Random.nextFloat() < 0.7f) say(Lines.pick(pw.fireLines), 1500L)
                PowerPhase.SPUTTER -> say(Lines.pick(Lines.POWER_FUEL_LOW), 2600L)
                PowerPhase.POSE -> say(Lines.pick(when {
                    flight.crashed && pw.has(PowerKit.FOOT_JETS) -> Lines.POWER_SOOTY
                    flight.crashed -> Lines.POWER_DAZED
                    else -> pw.landLines
                }), 3200L)
                else -> Unit
            }
        }
        if (ph == PowerPhase.CRASH && flight.crashed && !crashSaid) {
            crashSaid = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            say(Lines.pick(pw.crashLines), 2400L); com.combo.monkeycompanion.ai.BodySense.record("I crashed / wiped out during " + pw.title, com.combo.monkeycompanion.ai.BodySense.Feeling.SORE)
        }
        if (ph == PowerPhase.LAND && k >= 0.5f && !landSaid) {
            landSaid = true
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)   // thud
            if (flight.lastPhase == PowerPhase.LAND && entrancePipe == null) say(Lines.pick(pw.landLines), 2600L)
        }
        return true
    }

    private fun endPower() {
        powerOn = false
        flight.cancel()
        particles.clear()
        surf = screenSurf(SK.FLOOR); my = ground(); clampToSurf()
        controller.idleNow(2f)
        autoPowerGap = 25f
        powerIn = nextRandomPower()
        updateGeometry()
        // v4.2: a power entrance has landed: now the message, with its chips (the finger can tap again).
        entrancePipe?.let { p ->
            entrancePipe = null
            if (SystemClock.elapsedRealtime() - entranceAt < 30_000L && !hiddenTab && !exiting) { bubbleOn = false; showPipe(p) }
        }
    }

    /** Now and then while awake and idle (never with battery saver, while resting or with powers Off). */
    private fun stepRandomPower(dt: Float) {
        if (powerOn || powerQueued || saver || hiddenTab || exiting || powerSetting() == Superpowers.OFF) return
        if (controller.state != MonkeyState.IDLE || bubbleOn || !restAllowed()) return
        powerIn -= dt * controller.liveliness
        if (powerIn > 0f) return
        powerIn = nextRandomPower()
        // Don't take off right before auto-rest would hide anyway.
        val quiet = (SystemClock.elapsedRealtime() - lastInteract) / 1000f
        if (restSecs > 0 && quiet > restSecs - 15) return
        usePower(explicit = false)
    }

    val debugPowerOn get() = powerOn
    val debugFlight get() = flight
    val debugParticles get() = particles
    fun debugPower(p: Superpower, crash: Boolean) {
        if (!live) goLive()
        val x0 = fracX(mx); val y0 = fracY(my)
        beginPower(p)
        flight.begin(p, p.plan(x0, y0, crash, Random), crash, x0, y0)
    }

    fun debugExit(e: com.combo.monkeycompanion.brain.Exit) { exitKind = e; exiting = true; beginExit() }
    val debugExitDone get() = exitDone
    fun debugAskQuit() = askQuit()
    fun debugAnswerQuit(yes: Boolean) = answerQuit(yes)
    val debugQuitAsk get() = quitAsk
    private var longPressed = false

    private fun btnHit(x: Float, y: Float) = !chatFabOn && (chatBtnUntil > 0f || hintUntil > 0f) && hypot(x - lastBtnX, y - lastBtnY) <= chatBtnR + 10 * d

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { fingerDown = true; fingerDownT = t; gestureDragged = false; attDownX = e.rawX; attDownY = e.rawY }
            MotionEvent.ACTION_MOVE -> if (hypot(e.rawX - attDownX, e.rawY - attDownY) > 10 * d) gestureDragged = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { fingerDown = false; if (!gestureDragged && !dragging) attendUntil = t + ATTEND_GRACE }
        }
        // v10.14 eyes follow the user's finger while it is down (visual only, the LivingLayer's own state)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> if (livingOn && smoothAnim) runCatching {
                faceRectView(fingerFace)
                val r = 120f * d
                living.watchFinger((e.x - fingerFace.centerX()) / r, (e.y - fingerFace.centerY()) / r)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> living.fingerUp()
        }
        if (exiting) return true   // leaving: hands off
        if (powerOn && !wp) {
            // flying: the (small, opaque) window ignores touches until landing, except v10.9's double-tap = the next power
            if (dtPowerOn()) when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> powerTapDownAt = e.eventTime
                MotionEvent.ACTION_UP -> if (e.eventTime - powerTapDownAt < 300L) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastPowerTapUp < com.combo.monkeycompanion.brain.PowerShortcut.WINDOW_MS && hypot(e.rawX - lastPowerTapX, e.rawY - lastPowerTapY) < 60 * d) {
                        lastPowerTapUp = 0L; doubleTapPower()
                    } else { lastPowerTapUp = now; lastPowerTapX = e.rawX; lastPowerTapY = e.rawY }
                }
            }
            return true
        }
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            // The frame loop may be stopped (resting tab / nap): wake it for anything aimed at Settai.
            val mine = !(wp || ln) || hitTest(e.x, e.y) != TouchZone.NONE || btnHit(e.x, e.y) || notifCardOpen ||
                (bubbleOn && e.x >= bubbleLeft && e.x <= bubbleLeft + bubble.width && e.y >= bubbleTop && e.y <= bubbleTop + bubble.height)
            if (mine) { noteInteraction(); chatFab.wake(); if (!running && live && !paused) start() }
        }
        if (hiddenTab && !wp) return onTabTouch(e)
        if (fabPressing && e.actionMasked != MotionEvent.ACTION_DOWN) {
            when (e.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    fabPressing = false; chatFab.hold(false)
                    if (hypot(e.x - fabLX, e.y - fabLY) <= chatFabR + 18 * d) { if (fabSpeakMode()) speakChatFab() else tapChatFab() }
                }
                MotionEvent.ACTION_CANCEL -> { fabPressing = false; chatFab.hold(false) }
            }
            invalidate(); return true
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> { onOutsideOrBack(); return true }
            MotionEvent.ACTION_DOWN -> {
                if (notifCardOpen) {
                    val hit = notifCard.hitTest(e.x - notifCardLeft, e.y - notifCardTop)
                    if (hit != null) {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        tracking = false
                        dragging = false
                        notifFlingVy = 0f
                        if (notifCard.maxScrollY > 0f && (hit is com.combo.monkeycompanion.agent.NotifSummaryCard.Hit.Row || hit == com.combo.monkeycompanion.agent.NotifSummaryCard.Hit.InsideCard)) {
                            notifTouchActive = true
                            notifScrolling = false
                            notifDownRawY = e.y
                            notifLastRawY = e.y
                            notifLastMoveMs = e.eventTime
                        } else {
                            notifTouchActive = false
                            notifScrolling = false
                            handleNotifCardHit(hit)
                        }
                        return true
                    } else {
                        closeNotifSummaryCard()
                    }
                }
                if (listenTap && com.combo.monkeycompanion.agent.Voice.listening) {
                    // Listening: a tap on Settai or the bubble sends what was heard so far.
                    listenTap = false; tracking = false
                    com.combo.monkeycompanion.agent.Voice.stopListening()
                    return true
                }
                if (bubbleOn && e.x >= bubbleLeft && e.x <= bubbleLeft + bubble.width &&
                    e.y >= bubbleTop && e.y <= bubbleTop + bubble.height
                ) {
                    tracking = false
                    val lx = e.x - bubbleLeft; val ly = e.y - bubbleTop
                    if (!pendingShown && bubble.reportAt(lx, ly)) {
                        aiReply?.let { com.combo.monkeycompanion.agent.ReplyReports.report(context, it) }
                        aiReply = null; endTyping()
                        showBubble("⚑ Reported, thanks! Saved on this phone only (Phone powers > Reported replies).", 3.5f, reply = false)
                        return true
                    }
                    if (bubble.closeAt(lx, ly) && !thinkingBubble) { closeChat(); return true }
                    when (if (bubble.hasReply && !thinkingBubble) bubble.replyAt(lx, ly) else -1) {
                        0 -> { startTyping(); return true }
                        1 -> { if (typing) sendReply() else startTyping(); return true }
                        2 -> { endTyping(); host?.openChat(); return true }
                    }
                    if (typing) return true
                    if (bubbleChips.isNotEmpty() && !pendingShown) {
                        val ci = bubble.buttonAt(lx, ly)
                        if (ci >= 0) { val c = bubbleChips.getOrNull(ci); if (c != null) tapChip(c); return true }
                        if (arcOn && bubble.titleAt(lx, ly)) { tapChip(com.combo.monkeycompanion.agent.QuickChip.THINK); return true }
                        if (arcOn) { arcOn = false; bubbleChips = emptyList(); bubbleLife = bubbleAge; return true }
                    }
                    val b = if (pendingShown) bubble.buttonAt(lx, ly) else -1
                    if (b >= 0 && quitAsk) answerQuit(b == 0)
                    else if (b >= 0) { pendingShown = false; ChatHub.confirmPending(b == 0) }
                    else if (!pendingShown) { bubbleSwipe = true; swipeDownX = e.rawX; bubbleDragX = 0f }   // tap or swipe: decided on move / up
                    return true
                }
                if (!utilityPillRect.isEmpty && utilityPillRect.contains(e.x, e.y)) {
                    tracking = false
                    val msg = com.combo.monkeycompanion.agent.DailyUtilityEngine.onOverlayPillTapped(context)
                    if (!msg.isNullOrBlank()) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        controller.play(MonkeyState.HAPPY_PAT, 1.5f)
                        say(msg, 3000L)
                        invalidate()
                    }
                    return true
                }
                if (heartHit(e.x, e.y)) {   // v10.19.1: show the friendship level
                    tracking = false
                    heartShowFor = HEART_POPUP_SECS
                    challengeLine = runCatching {
                        if (!com.combo.monkeycompanion.fun5.Fun5Store.on(context, com.combo.monkeycompanion.fun5.Fun5Store.Feature.CHALLENGE)) ""
                        else com.combo.monkeycompanion.fun5.Fun5Store.challenge(context).let { "🎯 ${it.kind.title.substringBefore(" (")} · ${it.status()}" }
                    }.getOrDefault("")
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    if (!running && live && !paused) start()
                    invalidate()
                    return true
                }
                if (chatFabHit(e.x, e.y)) {
                    // v10.10: pressed state on touch-down (opaque + darker); the chat opens on release
                    tracking = false; chatBtnUntil = 0f; fabPressing = true; fabDownAt = SystemClock.uptimeMillis(); fabSpeakBuzzed = false
                    chatFab.hold(true)
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    if (!running && live && !paused) start()
                    invalidate()
                    return true
                }
                if (btnHit(e.x, e.y)) {
                    tracking = false; chatBtnUntil = 0f
                    if (hintUntil > 0f) { hintUntil = 0f; countChatOpen() }
                    if (host != null) openBubbleChat() else onChatRequest?.invoke()
                    return true
                }
                downZone = hitTest(e.x, e.y)
                if (downZone == TouchZone.NONE) { tracking = false; closeMenu(); return !(wp || ln) }
                tracking = true; dragging = false; longPressed = false
                downRawX = e.rawX; downRawY = e.rawY
                downMx = mx; downMy = my
                downSide = if (e.x < ox + boxW / 2f) -1 else 1
                downAt = e.eventTime; downPressure = e.pressure
                downPart = if (livingOn) partAt(e.x, e.y, downZone) else com.combo.monkeycompanion.engine.touch.BodyPart.of(downZone)
                if (livingOn && smoothAnim && !controller.isAsleep) living.glance(if (e.x < ox + boxW / 2f) -1f else 1f, 0.5f)   // eyes first
                lastMoveRawX = e.rawX; lastMoveRawY = e.rawY; lastMoveTime = e.eventTime; vx = 0f; vyDrag = 0f
                gN = 0; gPush(e.eventTime, e.rawX, e.rawY)
                // v4.3 gesture map (see brain/Gestures): long-press = options menu; moving past the slop cancels it.
                ui.postDelayed(longPress, com.combo.monkeycompanion.brain.Gestures.LONG_PRESS_MS)
            }
            MotionEvent.ACTION_MOVE -> {
                if (notifTouchActive) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val totalDy = e.y - notifDownRawY
                    val stepDy = notifLastRawY - e.y
                    val dtMs = (e.eventTime - notifLastMoveMs).coerceAtLeast(4L)
                    notifFlingVy = notifFlingVy * 0.4f + 0.6f * (stepDy * 1000f / dtMs)
                    notifLastRawY = e.y
                    notifLastMoveMs = e.eventTime
                    if (!notifScrolling && abs(totalDy) > max(4f, slop * 0.35f)) notifScrolling = true
                    if (notifScrolling && notifCard.scrollBy(stepDy)) invalidate()
                    return true
                }
                if (bubbleSwipe) {
                    val dx = e.rawX - swipeDownX
                    if (abs(dx) > slop) bubbleDragX = dx
                    return true
                }
                if (!tracking) return true
                if (v5On && stage.onFinger(e.rawX)) return true   // v5 banana game: drag steers
                val dx = e.rawX - downRawX; val dy = e.rawY - downRawY
                if (!dragging && com.combo.monkeycompanion.brain.Gestures.isDrag(hypot(dx, dy), slop.toFloat())) {
                    ui.removeCallbacks(longPress)
                    if (host != null && !wp) beginDrag()
                }
                if (dragging) {
                    mx = downMx + dx; my = downMy + dy
                    clampPosition()
                    val dtMs = (e.eventTime - lastMoveTime).coerceAtLeast(1L)
                    vx = vx * 0.5f + 0.5f * ((e.rawX - lastMoveRawX) * 1000f / dtMs)
                    vyDrag = vyDrag * 0.5f + 0.5f * ((e.rawY - lastMoveRawY) * 1000f / dtMs)
                    lastMoveRawX = e.rawX; lastMoveRawY = e.rawY; lastMoveTime = e.eventTime
                    gPush(e.eventTime, e.rawX, e.rawY)
                    controller.onDragMove(vx)
                }
            }
            MotionEvent.ACTION_UP -> {
                ui.removeCallbacks(longPress)
                if (notifTouchActive) {
                    val wasScrolling = notifScrolling
                    notifTouchActive = false
                    notifScrolling = false
                    if (!wasScrolling && notifCardOpen) {
                        notifCard.hitTest(e.x - notifCardLeft, e.y - notifCardTop)?.let { handleNotifCardHit(it) }
                    }
                    return true
                }
                if (bubbleSwipe) {
                    bubbleSwipe = false
                    val dx = e.rawX - swipeDownX
                    if (abs(dx) > 56 * d) {   // swiped away
                        if (host != null && !wp) com.combo.monkeycompanion.mind.MindHub.event(com.combo.monkeycompanion.mind.MindEvent.SWIPE_AWAY)
                        if (chatOpen) closeChat(if (dx > 0) 1 else -1)
                        else { bubbleFlyV = (if (dx > 0) 1 else -1) * 1800f * d / 2.6f; hush(); if (bubbleOn) bubbleLife = bubbleAge }
                    } else {
                        bubbleDragX = 0f
                        if (abs(dx) <= slop) {
                            if (wp) host?.openChat()   // wallpaper: tap the bubble to chat
                            else if (chatOpen) closeChat()
                            else { if (pages.isEmpty()) convoUntil = -1f; hush() }
                        }
                    }
                    return true
                }
                if (!tracking) return true
                tracking = false
                if (dragging) {
                    dragging = false
                    // A stale velocity (finger stopped before lifting) isn't a fling.
                    val fresh = e.eventTime - lastMoveTime < 80L
                    if (e.rawX != lastMoveRawX || e.rawY != lastMoveRawY) gPush(e.eventTime, e.rawX, e.rawY)
                    upSwipeDown = swipeDownNow(e.eventTime, if (fresh) vx else 0f, if (fresh) vyDrag else 0f)
                    if (fresh) flingBalls(vx, vyDrag)
                    if (fresh && hypot(vx, vyDrag) / d > 600f) {
                        val dir = com.combo.monkeycompanion.ai.BodySense.direction(vx, vyDrag)
                        com.combo.monkeycompanion.ai.BodySense.record("you flung me $dir", com.combo.monkeycompanion.ai.BodySense.Feeling.DIZZY)
                        val spdDp = hypot(vx, vyDrag) / d
                        feelTouch(com.combo.monkeycompanion.ai.react.ReactKind.THROW, force = (spdDp / 2500f).coerceIn(0f, 1f),
                            extra = "flung $dir" + if (spdDp > 1800f) " really fast" else "")   // v10.18
                    }
                    else com.combo.monkeycompanion.ai.BodySense.record("you dragged me to a new spot")
                    dragTravel = hypot(e.rawX - downRawX, e.rawY - downRawY)
                    val fall = settleAfterDrag(if (fresh) vx else 0f, if (fresh) vyDrag else 0f)
                    upSwipeDown = -1f
                    if (hideAnim < 0f) controller.onDragEnd(fall)
                    // v10.8: a quick flick (Rocket Girl: loop-de-loop) layered over the normal fling.
                    val spd = hypot(vx, vyDrag) / d
                    if (livingOn && fresh && hideAnim < 0f && impact == null && spd > 900f && e.eventTime - downAt < 450L && !rocketFlyOn &&
                        profile.reactionTable.any { com.combo.monkeycompanion.engine.touch.TouchKind.FLICK in it.kinds }) {
                        val inv = 1f / max(1f, hypot(vx, vyDrag))
                        livingTap(downPart, downZone, downSide, com.combo.monkeycompanion.engine.touch.TouchKind.FLICK, spd, vx * inv, vyDrag * inv, downPressure, (e.eventTime - downAt) / 1000f)
                    }
                    return true
                }
                if (longPressed) return true
                handleTap(e)
            }
            MotionEvent.ACTION_CANCEL -> {
                ui.removeCallbacks(longPress)
                notifTouchActive = false; notifScrolling = false
                bubbleSwipe = false; bubbleDragX = 0f
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
                if (tabMoved) saveHidden() else tabTap()
            }
            MotionEvent.ACTION_CANCEL -> { tabTracking = false; if (tabMoved) saveHidden() }
        }
        return true
    }

    private var tabTapPending: Runnable? = null

    /** Tab: one tap brings Settai back; a double-tap brings it back and opens the bubble chat. */
    private fun tabTap() {
        val p = tabTapPending
        if (p != null) {
            ui.removeCallbacks(p); tabTapPending = null
            chatAfterEntrance = true
            unhide(); entLine = null
            return
        }
        val r = Runnable { tabTapPending = null; unhide() }
        tabTapPending = r
        ui.postDelayed(r, doubleTapTimeout)
    }

    private fun beginDrag() {
        val doingBefore = feelDoing()   // v10.18: what he was doing when you grabbed him
        // v10.20: caught while flying / falling = a mid-air catch (daily challenge); grabbing also ends an impact
        if (impact?.phase == com.combo.monkeycompanion.engine.impact.ImpactPlay.Phase.FLIGHT || impact?.phase == com.combo.monkeycompanion.engine.impact.ImpactPlay.Phase.FALL ||
            falling || arc != null || rocketFlyOn) runCatching { com.combo.monkeycompanion.fun5.Fun5Store.noteChallenge(context, com.combo.monkeycompanion.v5.Challenge.Event.CATCH) }
        impact = null
        ui.removeCallbacks(longPress)
        if (longPressed) closeMenu()   // long-press then drag: the menu goes, the drag wins
        pendingTap?.let { ui.removeCallbacks(it) }; pendingTap = null
        dragging = true
        if (arc != null || (falling && landStyle != Landings.Style.SIMPLE)) logPlayed("interrupted: picked up mid-air")
        arc = null; arcRot = 0f; arcHover = false; arcBank = 1f
        if (rocketFlyOn) { rocketFlyOn = false; rocketFly.cancel() }
        mcOn = false
        falling = false; tweenT = -1f; tweenDone = null; afterLand = null
        if (entKind != EntKind.NONE) endEntrance()
        climber.deactivate()
        chatBtnUntil = 0f
        if (!longPressed) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        controller.onDragStart()
        feelTouch(com.combo.monkeycompanion.ai.react.ReactKind.PICKUP, doing = doingBefore)   // v10.18
    }

    private fun handleTap(e: MotionEvent) {
        // v10.17 touch strength: a firm press reads as a poke, a light one as a gentle tap
        BSN.record(if (e.pressure > 0.6f || e.size > 0.35f) "you poked me firmly" else "you tapped me gently", BSN.Feeling.POKED)
        onbGesture(com.combo.monkeycompanion.brain.Onboarding.Gesture.TAP)
        if (com.combo.monkeycompanion.agent.ScreenAgent.driving) { ChatHub.stopDriving(); return }   // v4: tap = take the wheel back
        if (v5On && stage.onTap()) return   // v5 games: staring contest "I blinked", banana game taps
        val now = SystemClock.uptimeMillis()
        val pending = pendingTap
        val dtPower = dtPowerOn()
        if (dtPower && now - lastTapUp < com.combo.monkeycompanion.brain.PowerShortcut.WINDOW_MS && hypot(e.rawX - lastTapX, e.rawY - lastTapY) < 48 * d) {
            // v10.9: double-tap = a power right now (the first tap's reaction already played; the power blends in)
            pending?.let { ui.removeCallbacks(it) }; pendingTap = null; lastTapUp = 0L
            doubleTapPower()
            return
        }
        if (pending != null && now - lastTapUp < doubleTapTimeout &&
            hypot(e.rawX - lastTapX, e.rawY - lastTapY) < 48 * d
        ) {
            ui.removeCallbacks(pending); pendingTap = null
            controller.onDoubleTap()
            return
        }
        val zone = downZone; val side = downSide
        val part = downPart; val pressure = downPressure
        val holdSec = (e.eventTime - downAt) / 1000f
        val kind = if (holdSec >= HOLD_SEC) com.combo.monkeycompanion.engine.touch.TouchKind.HOLD else com.combo.monkeycompanion.engine.touch.TouchKind.TAP
        val dirX = Math.signum(e.rawX - downRawX)
        val r = Runnable {
            pendingTap = null
            if (closeMenu()) return@Runnable   // tap Settai while the menu is up: just close it
            // v10.18 Feel the touch (Brain ON): an AI-written line for exactly this touch, before the body reacts.
            // Many phones report pressure as a constant 1.0, so a quick jab (short press) counts more than pressure.
            run {
                val pr = if (pressure > 0.01f && pressure < 0.98f) pressure else 0.4f
                val jab = when { holdSec < 0.08f -> 1f; holdSec < 0.2f -> 0.5f; else -> 0.2f }
                val force = (0.6f * pr + 0.4f * jab).coerceIn(0f, 1f)
                val asleep = controller.isAsleep
                feelTouch(com.combo.monkeycompanion.ai.react.ReactKind.classify(zone.name, force, kind == com.combo.monkeycompanion.engine.touch.TouchKind.HOLD, asleep),
                    zone.name, force, doing = if (asleep) "sleeping" else null)
            }
            if (controller.isAsleep && host != null && !wp) com.combo.monkeycompanion.mind.MindHub.event(com.combo.monkeycompanion.mind.MindEvent.WOKE)
            if (profile.bellySpray && zone == TouchZone.BELLY && host != null && !wp && !controller.isAsleep && sprayT < 0f) {
                startTrunkSpray(); return@Runnable   // v10.2 Gajju: belly poke = water spray, then off in a huff
            }
            // v4.3: a single tap is always a touch-zone reaction (v10.8: a living one when that's on)
            if (!livingTap(part, zone, side, kind, 0f, dirX, 0f, pressure, holdSec)) controller.onTap(zone, side)
            if (v5On) stage.onPet()
            if (host != null && !wp) com.combo.monkeycompanion.mind.MindHub.event(if (zone == TouchZone.HEAD) com.combo.monkeycompanion.mind.MindEvent.PET else com.combo.monkeycompanion.mind.MindEvent.POKE)
        }
        lastTapUp = now; lastTapX = e.rawX; lastTapY = e.rawY
        if (dtPower) { pendingTap = null; r.run(); return }   // v10.9: single taps react immediately (no double-tap wait)
        pendingTap = r
        ui.postDelayed(r, doubleTapTimeout)
    }

    // ---------------- v10.9 double-tap power + the floating chat button ----------------
    private val powerShortcut = com.combo.monkeycompanion.brain.PowerShortcut(System.nanoTime() xor 0xD0B1EL)
    /** v10.9 settings (cached in refreshQuipSettings). */
    private var dtPowerSetting = true
    private var chatButtonSetting = true
    private fun dtPowerOn() = host != null && !wp && dtPowerSetting
    /** v10.10: the first double-tap on each character always plays its signature power (remembered in prefs). */
    private fun sigKey() = "sig_seen_" + character.id
    private fun signatureSeen(): Boolean = runCatching { context.getSharedPreferences("settai_v3", 0).getBoolean(sigKey(), false) }.getOrDefault(true)
    private fun markSignatureSeen() { runCatching { context.getSharedPreferences("settai_v3", 0).edit().putBoolean(sigKey(), true).apply() } }
    private var lastPowerTapUp = 0L
    private var lastPowerTapX = 0f; private var lastPowerTapY = 0f
    private var powerTapDownAt = 0L
    /** The power the last double-tap fired (tests). */
    var debugLastShortcut: Superpower? = null; private set

    /** Double-tap: a random one of this character's powers (never the same twice in a row), no cooldown. */
    private fun doubleTapPower() {
        onbGesture(com.combo.monkeycompanion.brain.Onboarding.Gesture.DOUBLE_TAP)
        // v10.13: double-tap Billa = the Tom-and-Jerry chase show with Chittu (spawned for the show if needed).
        if ((character.id == "billa" || character.id == "chittu") && host != null && !wp) { startChaseShow(); return }
        val pw = powerShortcut.pick(com.combo.monkeycompanion.brain.PowerPrefs.enabled(character), character.signaturePower, signatureSeen()) ?: return
        markSignatureSeen()
        feelTouch(com.combo.monkeycompanion.ai.react.ReactKind.POWER, extra = "about to use ${pw.title}")   // v10.18
        debugLastShortcut = pw
        reactionLayer.clear()
        if (smoothAnim && profile.anim.enabled) living.anticipate(com.combo.monkeycompanion.engine.anim.Antic.CROUCH, 0f)
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        hintUntil = 0f
        usePower(explicit = true, force = pw)
    }

    private val chatFab = com.combo.monkeycompanion.brain.FloatButton()
    private val chatFabR get() = 15f * d
    /** v10.11: finger target radius (21 dp = a 42 dp touch target, bigger than the 30 dp button). */
    private val chatFabTouchR get() = chatFabR + 6f * d
    private var chatFabOn = false
    private var fingerDown = false
    private var attendUntil = 0f
    private var attWas = false
    private var fingerDownT = 0f
    private var gestureDragged = false
    private var attDownX = 0f; private var attDownY = 0f
    /** v10.13: the user is interacting with me (finger down, short grace after a tap, popup / chat / chips open). */
    fun attending(): Boolean { if (dragging) gestureDragged = true; return attendingNow() }
    private fun attendingNow(): Boolean = host != null && !wp && !dragging && !showOn &&
        ((fingerDown && t - fingerDownT > 0.25f && !gestureDragged) || t < attendUntil || (bubbleOn && (arcOn || typing || bubbleChips.isNotEmpty())))
    private var fabLX = 0f; private var fabLY = 0f
    private var fabSitAt = -1L
    private var fabQuiet = false
    /** v10.11: the head box (view px) the chat button was placed against this frame. */
    private val fabFace = RectF()
    private val fabFaceLocal = RectF()
    val debugChatFabRadius get() = chatFabR
    val debugChatFabTouchRadius get() = chatFabTouchR
    /** Tests: the face/head box in this view (the same one the chat button keeps clear of). */
    fun debugFaceRect(out: RectF) { faceRectView(out) }
    val debugChatFabVisible get() = chatFabOn
    /** The chat button's centre in this view (tests). */
    fun debugChatFab(out: FloatArray) { out[0] = fabLX; out[1] = fabLY }
    var debugChatOpened = 0; private set

    private fun chatFabWanted(): Boolean {
        if (host == null || wp || hiddenTab || exiting || powerOn || silent || typing || chatOpen || quitAsk || notifCardOpen || hideAnim >= 0f) return false
        if (controller.state == MonkeyState.ENTRANCE || controller.state == MonkeyState.HIDDEN || climber.active) return false
        if (!chatButtonSetting) return false
        val nowMs = SystemClock.uptimeMillis()
        if (fabSitAt < 0L || nowMs - fabSitAt > 1000L) {   // fullscreen / call / quiet state, checked once a second
            fabSitAt = nowMs
            fabQuiet = runCatching { com.combo.monkeycompanion.agent.Proactive.situation().let { it.fullscreen || it.inCall } }.getOrDefault(false)
        }
        return !fabQuiet
    }

    private fun stepChatFab(dt: Float) {
        chatFabOn = chatFabWanted()
        if (!chatFabOn) { chatFab.reset(); return }
        // v10.11: always faint; dark + solid only while the finger is on it (no "just moved" state, no fade timer)
        chatFab.step(dt)
    }

    private val fabM = Matrix()
    private val fabPts = FloatArray(8)
    private val fabCand = FloatArray(12)
    private val fabFaceArr = FloatArray(4)
    private val fabArea = FloatArray(4)

    /** v10.11: the character's head box in this view, through the same transform drawMonkey draws with. */
    private val bubFace = RectF()
    private val fingerFace = RectF()
    /**
     * v10.13: how far the bubble may dip into the top of the box: never past the top of the face/head
     * (the same head box the 💬 button keeps clear of), so a speech bubble never covers the face.
     */
    private fun bubbleOverlap(): Float {
        if (host == null) return overlap
        val top = runCatching { faceRectView(bubFace); bubFace.top - oy }.getOrDefault(overlap)
        if (top.isNaN() || bubFace.isEmpty) return overlap
        return (top - 3f * d).coerceIn(0f, overlap)
    }

    private fun faceRectView(out: RectF) {
        renderer.faceRect(rig, out)
        fabM.reset(); fabM.postTranslate(sinkX, heavyShake)
        val rot = bodyEdgeRot()
        if (rot != 0f) fabM.preRotate(rot, ox + boxW / 2f, oy + boxH / 2f)
        if (!fabM.isIdentity) fabM.mapRect(out)
    }

    /**
     * v10.11: put the chat button low beside the body (by the feet), on the side away from the face (away from Duo/Trio
     * partners too), flipping sides near a screen edge, always fully on screen and never over the head.
     */
    private fun placeChatFab() {
        val r = chatFabR
        faceRectView(fabFace)
        val cx = ox + boxW / 2f; val feetY = oy + boxH - groundPad
        renderer.faceRect(rig, fabFaceLocal)
        val faceMid = fabFaceLocal.centerX()
        val pref = when {
            neighbourOverlayVisible() -> if (mx + boxW / 2f <= sw() / 2f) -1 else 1    // partners stand on the other side
            abs(faceMid - cx) > 3f * d -> if (faceMid > cx) -1 else 1                // the face leans one way: go the other
            else -> -rig.facingSign                                                   // behind where she looks
        }
        val inX = boxW / 2f - r - 1f * d
        val sides = intArrayOf(pref, -pref)
        var k = 0
        for (s in sides) {
            fabCand[k++] = cx + s * inX; fabCand[k++] = feetY - r                     // beside the feet
            fabCand[k++] = cx + s * inX; fabCand[k++] = oy + boxH - r - 1f * d          // as low as the box goes
            fabCand[k++] = cx + s * (boxW / 2f + r * 0.4f); fabCand[k++] = feetY - r   // a little further out (wide windows)
        }
        // into this view (wall / ceiling turns and the duck-behind-an-edge offset), then into the window
        fabM.reset(); fabM.postTranslate(sinkX, heavyShake)
        val rot = bodyEdgeRot()
        if (rot != 0f) fabM.preRotate(rot, ox + boxW / 2f, oy + boxH / 2f)
        if (!fabM.isIdentity) fabM.mapPoints(fabCand)
        val sw = sw(); val sh = (host?.screenHeight() ?: height).toFloat()
        // allowed: inside this window AND on the screen
        fabArea[0] = max(0f, -winX.toFloat()); fabArea[1] = max(0f, -winY.toFloat())
        fabArea[2] = min(winW.toFloat(), sw - winX); fabArea[3] = min(winH.toFloat(), sh - winY)
        if (wp || winW <= 0) { fabArea[0] = 0f; fabArea[1] = 0f; fabArea[2] = width.toFloat(); fabArea[3] = height.toFloat() }
        for (i in 0 until 6) {
            fabCand[2 * i] = fabCand[2 * i].coerceIn(fabArea[0] + r + 1f, max(fabArea[0] + r + 1f, fabArea[2] - r - 1f))
            fabCand[2 * i + 1] = fabCand[2 * i + 1].coerceIn(fabArea[1] + r + 1f, max(fabArea[1] + r + 1f, fabArea[3] - r - 1f))
        }
        fabFaceArr[0] = fabFace.left; fabFaceArr[1] = fabFace.top; fabFaceArr[2] = fabFace.right; fabFaceArr[3] = fabFace.bottom
        val i = com.combo.monkeycompanion.brain.ChatBubbleSpot.choose(fabCand, fabFaceArr, fabArea, r, 2f * d)
        fabLX = fabCand[2 * i]; fabLY = fabCand[2 * i + 1]
        chatFab.place(fabLX + winX, fabLY + winY, if (i < 3) pref else -pref)
    }

    private fun drawChatFab(canvas: Canvas) {
        if (!chatFabOn) return
        // v10.11: no lag (a lagging button used to drift over the face mid-move); a press doesn't move it
        if (!fabPressing || chatFab.x.isNaN()) placeChatFab()
        val r = chatFabR * (1f - 0.18f * chatFab.press)
        val lx = fabLX; val ly = fabLY
        val a = (255 * chatFab.alpha).toInt()
        // v10.10: held down = fully opaque and darker
        val pk = if (fabPressing) 1f else 0f
        val fill = Color.rgb((0xE8 + (0x9A - 0xE8) * pk).toInt(), (0x8A + (0x4A - 0x8A) * pk).toInt(), (0x2E + (0x0C - 0x2E) * pk).toInt())
        btnPaint.style = Paint.Style.FILL; btnPaint.color = fill; btnPaint.alpha = (a * 0.25f).toInt()
        canvas.drawCircle(lx, ly, r * 1.35f, btnPaint)   // soft glow
        btnPaint.alpha = a; canvas.drawCircle(lx, ly, r, btnPaint)
        btnPaint.style = Paint.Style.STROKE; btnPaint.strokeWidth = 1.6f * d; btnPaint.color = Color.WHITE; btnPaint.alpha = a
        canvas.drawCircle(lx, ly, r, btnPaint)
        btnText.alpha = a; btnText.textSize = 14 * d * (1f - 0.18f * chatFab.press)
        // v10.13: one-tap shortcut for the mode chosen in Settings: 💬 type or 🎤 speak
        val speak = fabSpeakMode()
        canvas.drawText(if (speak) "\uD83C\uDFA4" else "\uD83D\uDCAC", lx, ly - (btnText.descent() + btnText.ascent()) / 2, btnText)
        btnText.alpha = 255; btnText.textSize = 17 * d; btnPaint.alpha = 255
    }

    // ---- v10.19 friendship heart: faint like the chat button, fills red with the level, glows on a level-up ----
    private var heartOn = false
    private var heartXp = -1
    private var heartLevel = 1
    private var heartFill = 0f
    private var heartPulse = 0f
    private var heartFlash = 0f
    private var heartPollIn = 0f
    private var heartFeatureOn = true
    private var heartLX = 0f; private var heartLY = 0f; private var heartLR = 0f
    /** v10.19.1: tapping the heart pops up a big dark heart with the level for a moment. */
    private var heartShowFor = 0f
    private val heartHead = FloatArray(2)
    val debugHeartPopup get() = heartShowFor > 0f
    private val heartPath = Path()
    private val heartPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val heartText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    val debugHeartLevel get() = heartLevel
    val debugHeartOn get() = heartOn

    /** No Random here: reads the saved friendship every 2 s (cheap) and animates the pulse / glow. */
    private fun stepFriendHeart(dt: Float) {
        if (heartPulse > 0f) heartPulse = max(0f, heartPulse - dt)
        if (heartFlash > 0f) heartFlash = max(0f, heartFlash - dt)
        if (heartShowFor > 0f) heartShowFor = max(0f, heartShowFor - dt)
        heartPollIn -= dt
        if (heartPollIn <= 0f) {
            heartPollIn = 2f
            heartFeatureOn = runCatching { com.combo.monkeycompanion.fun5.Fun5Store.on(context, com.combo.monkeycompanion.fun5.Fun5Store.Feature.HEART) }.getOrDefault(true)
            val xp = runCatching { com.combo.monkeycompanion.fun5.Fun5Store.friendship(context).xp }.getOrDefault(0)
            val lvl = com.combo.monkeycompanion.v5.Friendship.level(xp)
            if (heartXp >= 0 && xp > heartXp) heartPulse = 1.2f
            if (heartXp >= 0 && lvl > heartLevel) heartFlash = 4f
            heartXp = xp; heartLevel = lvl
            heartFill = if (lvl >= 10) 1f else com.combo.monkeycompanion.v5.Friendship.progress(xp)
        }
        heartOn = heartFeatureOn && host != null && !wp && !hiddenTab && !exiting && !powerOn && !typing && !chatOpen && !quitAsk &&
            !notifCardOpen && hideAnim < 0f && !climber.active && controller.state != MonkeyState.ENTRANCE && controller.state != MonkeyState.HIDDEN &&
            (chatFabOn || !fabQuiet)
    }

    private fun heartShape(cx: Float, cy: Float, r: Float) {
        heartPath.reset()
        heartPath.moveTo(cx, cy + r * 0.95f)
        heartPath.cubicTo(cx - r * 1.7f, cy - r * 0.15f, cx - r * 0.75f, cy - r * 1.35f, cx, cy - r * 0.45f)
        heartPath.cubicTo(cx + r * 0.75f, cy - r * 1.35f, cx + r * 1.7f, cy - r * 0.15f, cx, cy + r * 0.95f)
        heartPath.close()
    }

    private fun drawFriendHeart(canvas: Canvas) {
        if (!heartOn) return
        val glow = if (heartFlash > 0f) min(1f, heartFlash) else 0f
        val pulse = if (heartPulse > 0f) sin(heartPulse / 1.2f * Math.PI.toFloat()) else 0f
        val r = 9.5f * d * (1f + 0.25f * pulse + 0.35f * glow * (0.5f + 0.5f * sin(t * 9f)))
        // v10.19.1: just above his head (the chat button keeps its own spot); the speech bubble takes this place while it's up
        if (bubbleOn && heartShowFor <= 0f) return
        if (climber.active) { climber.headTop(heartHead); heartHead[0] += sinkX - winX; heartHead[1] += sinkY - winY }
        else { rig.headTop(heartHead); heartHead[0] += sinkX }
        val maxW = (if (wp) sw() else winW.toFloat()); val maxH = (if (wp) (host?.screenHeight() ?: height).toFloat() else winH.toFloat())
        // v10.19.2: beside the top of the head (upper corner), on the chat button's side; the button stays lower down
        val side = if (chatFabOn && !chatFab.x.isNaN() && abs(fabLX - heartHead[0]) > 1f) Math.signum(fabLX - heartHead[0]) else 1f
        var cx = heartHead[0] + side * (MonkeyRig.HEAD_R * rigScale + r * 0.5f)
        var cy = heartHead[1] + r * 0.6f
        if (chatFabOn && !chatFab.x.isNaN() && abs(cx - fabLX) < r + chatFabR + 6f * d) cy = min(cy, fabLY - chatFabR - r - 6f * d)
        cx = cx.coerceIn(r * 1.8f, max(r * 1.8f, maxW - r * 1.8f)); cy = cy.coerceIn(r * 1.6f, max(r * 1.6f, maxH - r * 1.6f))
        heartLX = cx; heartLY = cy; heartLR = r
        if (heartShowFor > 0f) { drawHeartPopup(canvas, cx, cy, maxW, maxH); return }
        // faint like the chat button; darker and solid while it pulses / glows
        val strong = max(pulse, glow)
        val a = ((0.42f + 0.58f * strong) * 255).toInt()
        heartShape(cx, cy, r)
        heartPaint.style = Paint.Style.FILL; heartPaint.color = Color.WHITE; heartPaint.alpha = (a * 0.55f).toInt()
        canvas.drawPath(heartPath, heartPaint)
        // the red level bar fills the heart from the bottom
        val top = cy - r * 1.05f; val bottom = cy + r * 0.95f
        val fillTop = bottom - (bottom - top) * heartFill.coerceIn(0.06f, 1f)
        canvas.save(); canvas.clipRect(cx - r * 2f, fillTop, cx + r * 2f, bottom + 1f)
        val red = if (strong > 0.3f) com.combo.monkeycompanion.theme.Th.rgb(0xA8, 0x00, 0x0C) else com.combo.monkeycompanion.theme.Th.rgb(0xE0, 0x10, 0x1A)
        heartPaint.color = red; heartPaint.alpha = a
        canvas.drawPath(heartPath, heartPaint)
        canvas.restore()
        heartPaint.style = Paint.Style.STROKE; heartPaint.strokeWidth = 1.4f * d; heartPaint.color = Color.WHITE; heartPaint.alpha = a
        canvas.drawPath(heartPath, heartPaint)
        heartText.color = Color.WHITE; heartText.alpha = a; heartText.textSize = 8.5f * d * (r / (9.5f * d))
        canvas.drawText(heartLevel.toString(), cx, cy + heartText.textSize * 0.15f, heartText)
        if (glow > 0f) {   // "Level up!" label floats above the heart while it glows
            heartText.textSize = 11f * d; heartText.alpha = (255 * min(1f, heartFlash / 1.2f)).toInt()
            heartText.color = com.combo.monkeycompanion.theme.Th.rgb(0xE0, 0x10, 0x1A)
            val ly = cy - r * 1.6f - (4f - heartFlash) * 4f * d
            canvas.drawText("Friendship Lv $heartLevel!", cx.coerceIn(48f * d, max(48f * d, maxW - 48f * d)), max(12f * d, ly), heartText)
        }
        heartPaint.alpha = 255; heartText.alpha = 255
    }

    /** v10.19.1: the big dark heart with the level number, popped up by a tap, then it fades away. */
    private fun drawHeartPopup(canvas: Canvas, sx: Float, sy: Float, maxW: Float, maxH: Float) {
        val life = heartShowFor
        val inK = min(1f, (HEART_POPUP_SECS - life) / 0.25f)
        val a = (255 * min(1f, life / 0.5f)).toInt()
        val r = 24f * d * (0.5f + 0.5f * com.combo.monkeycompanion.engine.anim.Easing.easeOutBack(inK, 1.6f))
        val cx = sx.coerceIn(r * 1.9f, max(r * 1.9f, maxW - r * 1.9f))
        val cy = (sy - 18f * d).coerceIn(r * 1.4f, max(r * 1.4f, maxH - r * 1.4f - 18f * d))
        heartShape(cx, cy, r)
        heartPaint.style = Paint.Style.FILL; heartPaint.color = com.combo.monkeycompanion.theme.Th.rgb(0x2A, 0x2A, 0x2A); heartPaint.alpha = (a * 0.85f).toInt()
        canvas.drawPath(heartPath, heartPaint)
        val top = cy - r * 1.05f; val bottom = cy + r * 0.95f
        canvas.save(); canvas.clipRect(cx - r * 2f, bottom - (bottom - top) * heartFill.coerceIn(0.06f, 1f), cx + r * 2f, bottom + 1f)
        heartPaint.color = com.combo.monkeycompanion.theme.Th.rgb(0xB0, 0x00, 0x0E); heartPaint.alpha = a
        canvas.drawPath(heartPath, heartPaint)
        canvas.restore()
        heartPaint.style = Paint.Style.STROKE; heartPaint.strokeWidth = 2.2f * d; heartPaint.color = Color.WHITE; heartPaint.alpha = a
        canvas.drawPath(heartPath, heartPaint)
        heartText.color = Color.WHITE; heartText.alpha = a; heartText.textSize = 20f * d * (r / (24f * d))
        canvas.drawText(heartLevel.toString(), cx, cy + heartText.textSize * 0.2f, heartText)
        // caption under it: level name and points out of 100
        val lvlName = com.combo.monkeycompanion.v5.Friendship.title(heartLevel)
        val pts = com.combo.monkeycompanion.v5.Bond.points(max(0, heartXp))
        heartText.textSize = 11f * d; heartText.color = com.combo.monkeycompanion.theme.Th.rgb(0xB0, 0x00, 0x0E)
        val cap = "$lvlName · $pts/100"
        val half = heartText.measureText(cap) / 2f + 6f * d
        val tx = cx.coerceIn(half, max(half, maxW - half)); val ty = min(cy + r + 16f * d, maxH - 4f * d)
        heartPaint.style = Paint.Style.FILL; heartPaint.color = Color.WHITE; heartPaint.alpha = (a * 0.9f).toInt()
        tmpR.set(tx - half, ty - 12f * d, tx + half, ty + 4f * d); canvas.drawRoundRect(tmpR, 8f * d, 8f * d, heartPaint)
        heartText.alpha = a
        canvas.drawText(cap, tx, ty, heartText)
        // v10.20: today's challenge, one short line
        if (challengeLine.isNotEmpty()) {
            heartText.textSize = 10f * d
            val half2 = heartText.measureText(challengeLine) / 2f + 6f * d
            val tx2 = cx.coerceIn(half2, max(half2, maxW - half2)); val ty2 = min(ty + 17f * d, maxH - 4f * d)
            heartPaint.style = Paint.Style.FILL; heartPaint.color = Color.WHITE; heartPaint.alpha = (a * 0.9f).toInt()
            tmpR.set(tx2 - half2, ty2 - 11f * d, tx2 + half2, ty2 + 4f * d); canvas.drawRoundRect(tmpR, 8f * d, 8f * d, heartPaint)
            heartText.color = com.combo.monkeycompanion.theme.Th.rgb(0x2A, 0x2A, 0x2A); heartText.alpha = a
            canvas.drawText(challengeLine, tx2, ty2, heartText)
        }
        heartPaint.alpha = 255; heartText.alpha = 255
    }
    private var challengeLine = ""

    private fun heartHit(x: Float, y: Float) = heartOn && heartLR > 0f && heartShowFor <= 0f && !(bubbleOn) && hypot(x - heartLX, y - heartLY) <= heartLR + 12f * d

    private fun chatFabHit(x: Float, y: Float) = chatFabOn && !chatFab.x.isNaN() && hypot(x - fabLX, y - fabLY) <= chatFabTouchR

    /** The chat button: opens the chat exactly like double-tap used to (the AI stays off unless Give Brain is on). */
    private var fabPressing = false
    /** Tests: the chat button is held down right now (pressed look). */
    val debugChatFabPressed get() = fabPressing
    val debugChatFabAlpha get() = chatFab.alpha
    private var fabDownAt = 0L
    private var fabSpeakBuzzed = false
    private fun fabSpeakMode() = ChatHub.settings.fabSpeak
    private fun speakChatFab() {
        chatFab.pressed()
        if (com.combo.monkeycompanion.agent.Voice.enabled(context)) startListen(hold = false)
        else say("Switch on the Voice power (Settings › Phone powers) and I'll listen 🎤 · tap 💬 to type", 4000L)
    }
    private fun tapChatFab() {
        chatFab.pressed()
        onDoubleTapChat()
    }

    // ---------------- Double-tap to chat + discoverability ----------------

    private fun onDoubleTapChat() {
        if (controller.state == MonkeyState.ENTRANCE || controller.state == MonkeyState.HIDDEN) return
        countChatOpen()
        debugChatOpened++
        hintUntil = 0f
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if (wp || replyField == null) { if (host != null) host.openChat() else onChatRequest?.invoke() }
        else openBubbleChat()
    }

    private fun countChatOpen() {
        prefs.edit().putInt("dtChats", prefs.getInt("dtChats", 0) + 1).apply()
    }

    private var hintCheckIn = 4f
    /** v10.10: the last one-time character tip shown (tests). */
    var debugTipShown: String? = null; private set

    /** First appearance after install/update: "Double-tap me to chat!"; then gentle reminders until it's been used ~3 times. */
    private fun stepHints(dt: Float) {
        if (host == null) return
        hintCheckIn -= dt
        if (hintCheckIn > 0f) return
        hintCheckIn = 15f
        if (controller.state != MonkeyState.IDLE || bubbleOn || hiddenTab || ChatHub.busy) return
        // v10.10: a one-time tip the first time each character is picked ("Double-tap me for fireworks! Swipe me to fly!")
        if (dtPowerOn() && !prefs.getBoolean("tip_seen_" + character.id, false)) {
            prefs.edit().putBoolean("tip_seen_" + character.id, true).apply()
            debugTipShown = character.doubleTapTip
            showHint(character.doubleTapTip); hintCheckIn = 8f
            return
        }
        val ver = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode }.getOrDefault(0L)
        val now = System.currentTimeMillis()
        if (prefs.getLong("hintVer", -1L) != ver) {
            prefs.edit().putLong("hintVer", ver).putLong("hintAt", now).apply()
            showHint(if (dtPowerOn()) DT_POWER_HINTS[0] else com.combo.monkeycompanion.brain.Gestures.coachLine(host != null && !wp))   // v4.3 coach marks
            return
        }
        if (prefs.getInt("dtChats", 0) >= 3) return
        if (now - prefs.getLong("hintAt", 0L) < 20 * 60_000L) return
        if (Random.nextFloat() > 0.35f) return
        prefs.edit().putLong("hintAt", now).apply()
        showHint(if (dtPowerOn()) DT_POWER_HINTS[fxRnd.nextInt(DT_POWER_HINTS.size)] else Lines.pick(Lines.DT_HINT))
    }

    private fun showHint(line: String) {
        say(line, 5000L)
        hintUntil = 6f
        controller.play(MonkeyState.WAVE, 1.8f)
    }

    // ---------------- Body actions (AI tags / do_action / user's request) ----------------

    override fun onAction(action0: BodyAction) {
        if (exiting) return
        // v4.1: the same action on this body (fallbacks: no skateboard -> walk, can't climb -> hop...).
        val action = character.mapAction(action0, wear) ?: run { if (host != null && !controller.isAsleep) hopInPlace(); return }
        noteInteraction()
        if (host == null) {  // in-app preview: just the simple ones
            when (action) {
                BodyAction.DANCE -> controller.danceNow(false)
                BodyAction.SPIN -> controller.spinDance()
                else -> action.gag?.let { controller.gag(it) }
            }
            return
        }
        if (controller.isAsleep && action != BodyAction.SLEEP) { controller.wakeUp(Lines.pick(Lines.POKED_AWAKE)); return }
        if (hiddenTab) { if (action != BodyAction.HIDE && action != BodyAction.SLEEP) autoUnhide(); return }
        if (controller.state == MonkeyState.ENTRANCE || dragging) return
        if (action != BodyAction.FLY && (movingNow || settledFor < SETTLE_SECS)) { heldAction = action; return }   // v10.15: act once settled
        if (action == BodyAction.FLY) { usePower(explicit = true); return }
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
            BodyAction.SKATE -> onGround {
                val far = if (mx + boxW / 2f < sw() / 2f) body.maxCenterX() else body.minCenterX()
                controller.walkTo(far, 1.5f); if (wear.has("skate")) say(Lines.pick(com.combo.monkeycompanion.speech.HeroLines.MM_SKATE), 2200L)
            }
            BodyAction.CHICKEN -> { controller.play(MonkeyState.GIGGLE, 1.8f); say(Lines.pick(com.combo.monkeycompanion.speech.HeroLines.MM_SQUEAK), 1800L) }
            BodyAction.GADGET -> onGround { controller.gag(com.combo.monkeycompanion.brain.Gag.JUGGLE) }
            else -> action.gag?.let { g -> onGround { controller.gag(g) } }
        }
    }

    // ---------------- v10.4 Mask Man: always juggling; moving him makes him wobble and drop the balls ----------------
    private val balls = com.combo.monkeycompanion.engine.JuggleBalls()
    private val juggles get() = profile.juggles && host != null && !wp && !saver
    private var juggleOn = false
    /** 0..1: how shaken he is (drag speed), smoothed. */
    private var wobble = 0f
    private val heldX = FloatArray(3); private val heldY = FloatArray(3)
    private val heldPrevX = FloatArray(3); private val heldPrevY = FloatArray(3)
    private var heldPosValid = false
    private var droppedThisDrag = false
    private var refillIn = -1f
    private val ballPt = FloatArray(2)
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ballLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val ballColors = intArrayOf(Color.rgb(0xFF, 0x5A, 0x5F), Color.rgb(0xFF, 0xC9, 0x3C), Color.rgb(0x3F, 0xA9, 0xF5))
    private val juggleStates = setOf(MonkeyState.IDLE, MonkeyState.WALK, MonkeyState.TALKING, MonkeyState.THINKING, MonkeyState.DRAGGED, MonkeyState.LANDING)

    private fun ballR() = 10f * rigScale
    private fun ballFloor() = realGround() + boxH - groundPad
    /** Throw height (rig units): up around and above his head, so the window grows upward while he juggles. */
    private val JUGGLE_ARC = 150f
    private fun juggleHeadroom() = if (juggleOn && balls.heldCount > 0) (JUGGLE_ARC * 0.62f * rigScale).toInt() else 0

    private fun computeJuggleOn() = juggles && !hiddenTab && hideAnim < 0f && !controller.isAsleep && !mcOn && !powerOn &&
        !climber.active && !falling && surf.kind == SK.FLOOR && entKind == EntKind.NONE && moment == null &&
        controller.state in juggleStates

    /** Arms keep the cascade going; while being dragged he flails and sways to keep his balance. */
    private fun jugglePose(p: Pose) {
        if (balls.heldCount == 0 || balls.heldAlpha < 0.05f) return
        val ph = balls.juggleT * 6.3f
        p.armLRaise = 40f + 26f * sin(ph); p.armLElbow = -95f
        p.armRRaise = 40f - 26f * sin(ph); p.armRElbow = -95f
        if (controller.state != MonkeyState.WALK) { p.lookY = -3.5f; p.lookX = 2.5f * sin(ph * 0.5f) }
        if (wobble > 0.02f) {
            p.armLRaise += 45f * wobble * sin(t * 11f); p.armRRaise -= 45f * wobble * sin(t * 12.5f + 1f)
            p.bodyRot += 14f * wobble * sin(t * 9f); p.headRot += 9f * wobble * sin(t * 7f + 0.5f)
            p.eyeOpen = max(p.eyeOpen, 1f + 0.45f * wobble); p.mouthOpen = max(p.mouthOpen, 0.6f * wobble)
        }
    }

    /** Let go of held ball [i] with the velocity it really has on screen (so a fast drag throws it). */
    private fun dropHeld(i: Int, extraVx: Float, extraVy: Float, dt: Float) {
        if (!heldPosValid) return
        val sx = heldX[i] + winX + sinkX; val sy = heldY[i] + winY
        val k = if (dt > 0f) 1f / dt else 0f
        val lim = 2600f * d
        val bvx = ((sx - heldPrevX[i]) * k).coerceIn(-lim, lim); val bvy = ((sy - heldPrevY[i]) * k).coerceIn(-lim, lim)
        balls.drop(i, sx, sy, bvx + extraVx, bvy + extraVy)
    }

    /** One juggling frame (after [layoutRig], so the hands are where they're drawn). */
    private fun stepJuggle(dt: Float) {
        if (!juggles && !balls.looseActive) return
        val target = if (dragging) ((hypot(vx, vyDrag) - 250f * d) / (1100f * d)).coerceIn(0f, 1f) else 0f
        wobble += (target - wobble) * min(1f, dt * 8f)
        balls.heldAlpha = if (juggleOn && balls.heldCount > 0) min(1f, balls.heldAlpha + dt / 0.3f) else max(0f, balls.heldAlpha - dt / 0.15f)
        if (juggleOn) balls.juggleT += dt * (1f + 0.5f * wobble)
        // Where the held balls are now (view px) and a frame ago (screen px) for their throw velocity.
        if (balls.heldCount > 0) {
            rig.handPoint(true, tmpA); rig.handPoint(false, tmpB)
            for (i in 0 until balls.count) {
                if (heldPosValid) { heldPrevX[i] = heldX[i] + winX + sinkX; heldPrevY[i] = heldY[i] + winY }
                balls.heldPosition(i, tmpA[0], tmpA[1], tmpB[0], tmpB[1], JUGGLE_ARC * rigScale, wobble, ballPt)
                heldX[i] = ballPt[0]; heldY[i] = ballPt[1]
                if (!heldPosValid) { heldPrevX[i] = heldX[i] + winX + sinkX; heldPrevY[i] = heldY[i] + winY }
            }
            heldPosValid = true
        }
        if (juggles && balls.heldAlpha > 0.5f) {
            if (dragging && wobble > 0.05f) {
                // The harder you shake him, the faster the balls slip out.
                for (i in 0 until balls.count) {
                    if (balls.state[i] == com.combo.monkeycompanion.engine.JuggleBalls.State.HELD && Random.nextFloat() < wobble * wobble * 3f * dt) {
                        dropHeld(i, (Random.nextFloat() - 0.5f) * 160f * d, -120f * d, dt)
                        if (!droppedThisDrag) { droppedThisDrag = true; say(com.combo.monkeycompanion.character.MaskMan.JUGGLE_DROP.random(), 1800L) }
                    }
                }
            } else if (falling || controller.state == MonkeyState.FALLING) {
                for (i in 0 until balls.count) dropHeld(i, (Random.nextFloat() - 0.5f) * 220f * d, -(180f + Random.nextFloat() * 200f) * d, dt)
            }
        }
        if (!dragging) droppedThisDrag = false
        balls.stepLoose(dt, 0f, topY(), sw(), ballFloor(), ballR(), d)
        // Lost balls (rolled away and faded) are replaced a moment after the last one settles.
        if (balls.anyGone && !balls.looseActive) {
            if (refillIn < 0f) refillIn = 1.5f
            refillIn -= dt
            if (refillIn <= 0f && juggleOn) {
                refillIn = -1f
                val wasEmpty = balls.heldCount == 0
                balls.refillGone()
                if (wasEmpty) { balls.heldAlpha = 0f; heldPosValid = false }
                if (Random.nextFloat() < 0.6f) say(com.combo.monkeycompanion.character.MaskMan.JUGGLE_REFILL.random(), 1800L)
            }
        } else refillIn = -1f
    }

    /** Released with a fast swipe: every ball in his hands flies off that way and bounces around the screen. */
    private fun flingBalls(fvx: Float, fvy: Float) {
        if (!juggles || balls.heldCount == 0 || balls.heldAlpha < 0.5f || hypot(fvx, fvy) < 1400f * d) return
        // A finger flick can be 8000+ px/s: cap it so you can actually watch the balls fly across.
        val sp = hypot(fvx, fvy); val cap = 1500f * d
        val kx = if (sp > cap) fvx * cap / sp else fvx; val ky = if (sp > cap) fvy * cap / sp else fvy
        for (i in 0 until balls.count) {
            if (balls.state[i] != com.combo.monkeycompanion.engine.JuggleBalls.State.HELD) continue
            dropHeld(i, 0f, 0f, 0f)
            balls.vx[i] = kx * (0.75f + 0.25f * Random.nextFloat()) + (Random.nextFloat() - 0.5f) * 300f * d
            balls.vy[i] = ky * (0.75f + 0.25f * Random.nextFloat()) - (300f + Random.nextFloat() * 500f) * d
        }
        say(com.combo.monkeycompanion.character.MaskMan.JUGGLE_FLING.random(), 2000L)
        if (!running && live && !paused) start()
    }

    /** A juggling ball: colour, a darker underside, a white stripe that rolls, a glint and an outline. */
    private fun drawBall(c: Canvas, x: Float, y: Float, r: Float, color: Int, alpha: Float, spin: Float) {
        val a = (255 * alpha.coerceIn(0f, 1f)).toInt()
        ballPaint.style = Paint.Style.FILL
        ballPaint.color = color; ballPaint.alpha = a; c.drawCircle(x, y, r, ballPaint)
        ballPaint.color = Color.BLACK; ballPaint.alpha = (a * 0.18f).toInt(); c.drawCircle(x + r * 0.15f, y + r * 0.2f, r * 0.8f, ballPaint)
        ballPaint.color = color; ballPaint.alpha = a; c.drawCircle(x - r * 0.1f, y - r * 0.1f, r * 0.78f, ballPaint)
        ballLine.color = Color.WHITE; ballLine.alpha = (a * 0.85f).toInt(); ballLine.strokeWidth = r * 0.28f
        val cx = kotlin.math.cos(spin) * r * 0.75f; val cy = sin(spin) * r * 0.75f
        c.drawLine(x - cx, y - cy, x + cx, y + cy, ballLine)
        ballPaint.color = Color.WHITE; ballPaint.alpha = (a * 0.9f).toInt(); c.drawCircle(x - r * 0.35f, y - r * 0.4f, r * 0.22f, ballPaint)
        ballLine.color = Color.rgb(0x2A, 0x22, 0x30); ballLine.alpha = a; ballLine.strokeWidth = r * 0.18f
        c.drawCircle(x, y, r, ballLine)
    }

    private fun drawHeldBalls(c: Canvas) {
        if (!juggles || balls.heldAlpha < 0.01f || !heldPosValid) return
        for (i in 0 until balls.count) if (balls.state[i] == com.combo.monkeycompanion.engine.JuggleBalls.State.HELD)
            drawBall(c, heldX[i], heldY[i], ballR(), ballColors[i], balls.heldAlpha, balls.juggleT * 6f + i)
    }

    /** Loose balls in screen px (drawn in the FX window outside ours, and in ours with the same offset). */
    private fun drawLooseBalls(c: Canvas) {
        for (i in 0 until balls.count) if (balls.state[i] == com.combo.monkeycompanion.engine.JuggleBalls.State.LOOSE)
            drawBall(c, balls.x[i], balls.y[i], ballR(), ballColors[i], balls.alpha[i], balls.spin[i])
    }
    val debugBalls get() = balls
    val debugJuggling get() = juggleOn

    // ---------------- v10.2 Gajju: belly poke -> trunk water spray across the screen ----------------
    private var sprayT = -1f
    private val sprayPt = FloatArray(2)
    private val SPRAY_SECS = 1.1f

    private fun startTrunkSpray() {
        sprayT = 0f
        controller.play(MonkeyState.STARTLED, 1.6f)
        say(com.combo.monkeycompanion.character.Gajju.SPRAY.random(), 2200L)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        if (!running && live && !paused) start()
    }

    /** Raise the trunk, fire water drops from its tip across the display for [SPRAY_SECS], then duck behind an edge. */
    private fun stepTrunkSpray(dt: Float) {
        if (sprayT < 0f) return
        sprayT += dt
        val gr = renderer as? com.combo.monkeycompanion.rig.GajjuRenderer
        val raise = (sprayT / 0.18f).coerceAtMost(1f) * ((SPRAY_SECS + 0.35f - sprayT) / 0.25f).coerceIn(0f, 1f)
        gr?.spray = raise
        if (sprayT < SPRAY_SECS && sprayT > 0.12f) {
            val hs = com.combo.monkeycompanion.rig.GajjuRenderer.HEAD_SCALE
            sprayPt[0] = com.combo.monkeycompanion.rig.GajjuRenderer.SPRAY_TIP_X * hs
            sprayPt[1] = com.combo.monkeycompanion.rig.GajjuRenderer.SPRAY_TIP_Y * hs
            rig.head.mapPoints(sprayPt)
            val sx = sprayPt[0] + winX; val sy = sprayPt[1] + winY
            val dir = if (sx < sw() / 2f) 1f else -1f          // spray toward the middle of the screen
            repeat(if (saver) 1 else 3) {
                val ang = Math.toRadians((15.0 + Random.nextDouble() * 70.0)).toFloat()
                val sp = (380f + Random.nextFloat() * 520f) * d
                particles.spawn(com.combo.monkeycompanion.rig.Particles.WATER, sx, sy,
                    dir * kotlin.math.cos(ang) * sp, -sin(ang) * sp, 1.6f + Random.nextFloat() * 0.8f, (4f + Random.nextFloat() * 3f) * d)
            }
        }
        if (sprayT >= SPRAY_SECS + 0.35f) {
            sprayT = -1f; gr?.spray = 0f
            // ...and off it goes in a huff, peeking back out a little later.
            hideToEdge(if (mx + boxW / 2f < sw() / 2f) -1 else 1, rest = true)
            ui.postDelayed({ if (hiddenTab) autoUnhide() }, 10_000L)
        }
    }

    private fun hopInPlace() {
        controller.play(MonkeyState.JUMP, 0.75f)
        val fx = mx; val fy = my
        slideFrom(fx, fy, 0.6f, 60 * d)
    }

    private fun climbSomething() {
        if (!climbs) { climbScreenWall(); return }
        if ((wp || ln) && spots.isNotEmpty() && Random.nextFloat() < 0.7f) { goToSpot(Random.nextInt(spots.size), 2); return }
        if (surf.climb) { controller.walkTo(Random.nextFloat() * (body.maxCenterX() - body.minCenterX()) + body.minCenterX(), 1.2f); return }
        climbScreenWall()
    }

    private fun hideSomewhere() {
        if (!(wp || ln)) {
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
        if (mcOn) return true
        if (hovers && roamOn) return hoverRoam(r)
        val s = surf
        if (!roamOn) {
            if (s.kind == SK.FLOOR) return false
            if (r < 0.5f) controller.walkTo(randAlong(), 1f) else controller.idleNow(3f)
            return true
        }
        if ((wp || ln) && s.spot >= 0) return roamSpot(r, s)
        return when (s.kind) {
            SK.FLOOR -> when {
                (wp || ln) && spots.isNotEmpty() && r < 0.38f -> { goToSpot(Random.nextInt(spots.size), Random.nextInt(4)); true }
                r < (profile.wallClimbChance ?: if (wp || ln) 0.46f else if (canWalkScreenEdges) 0.28f else 0.12f) -> { climbScreenWall(); true }
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
        if (isMask) {
            controller.walkTo(if (left) body.minCenterX() else body.maxCenterX(), 1.1f) { startMaskClimb(if (left) -1 else 1) }
            return
        }
        if (!climbs && !canWalkScreenEdges) {
            // Flying heroes float / stomp over to the edge and hover there a moment.
            controller.walkTo(if (left) body.minCenterX() else body.maxCenterX(), 1.1f) { controller.idleNow(2.5f) }
            return
        }
        controller.walkTo(if (left) body.minCenterX() else body.maxCenterX(), 1.15f) {
            goSurf(screenSurf(if (left) SK.WALL_L else SK.WALL_R), 0.32f)
            controller.walkTo(randAlong(), 1.15f)
            if (Random.nextFloat() < 0.5f) {
                say(
                    when (character.id) {
                        "billa" -> "🐾 Zero-gravity cat walk up the screen wall! Where did Chittu go?!"
                        "chittu" -> "🐭💨 Scurrying vertically up the screen edge! Can't catch me, Billa!"
                        else -> Lines.pick(Lines.CLIMB)
                    }
                )
            }
        }
    }

    /** Wallpaper: go play on spot [i]. [what] 0 = hide behind & peek, 1 = sit on top, 2 = climb its side, 3 = hang underneath. */
    private fun goToSpot(i: Int, what0: Int) {
        val what = if (!climbs && what0 >= 2) 1 else what0       // no climbing / hanging: sit on top instead
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
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
        val bi = runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
        val lvl = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val temp = (bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val plugged = (bi?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val state = when {
            dragging || controller.state == MonkeyState.DRAGGED -> "being dragged by your finger"
            falling || controller.state == MonkeyState.FALLING -> "falling"
            powerOn && flight.active -> "flying mid-power"
            rocketFlyOn || arc != null -> "flying through the air"
            controller.isAsleep -> "sleeping"
            controller.state == MonkeyState.DANCE -> "dancing"
            else -> "resting, idle"
        }
        val fr = runCatching { com.combo.monkeycompanion.v5.Friendship.title(com.combo.monkeycompanion.v5.Friendship.level(com.combo.monkeycompanion.fun5.Fun5Store.friendship(context).xp)) }.getOrDefault("")
        val snap = com.combo.monkeycompanion.ai.BodySense.Snapshot(name = character.name, position = where, state = state,
            power = if (powerOn) flight.power?.title else null, battery = lvl, charging = plugged,
            hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY), mood = mood, friendship = fr, asleep = controller.isAsleep,
            quietMin = if (lastInteract > 0L) ((SystemClock.elapsedRealtime() - lastInteract) / 60_000L).toInt() else -1,
            restedMin = if (lastRestAt > 0L) ((SystemClock.elapsedRealtime() - lastRestAt) / 60_000L).toInt() else -1,
            keyboard = keyboardDodge, energy = runCatching { com.combo.monkeycompanion.mind.MindHub.world.state.energy }.getOrDefault(-1f),
            app = runCatching { com.combo.monkeycompanion.agent.SettaiAccessibilityService.instance?.currentPkg?.let { com.combo.monkeycompanion.agent.Proactive.typeGuess(it)?.label } }.getOrNull() ?: "")
        return buildString {
            append("you live in the ").append(if (wp) "live wallpaper (behind the home-screen icons)" else "floating overlay on top of apps")
            if (temp >= 40f) append("; phone feels hot (").append(temp).append("°C)")
            append("\n").append(com.combo.monkeycompanion.ai.BodySense.build(snap))
        }
    }

    private fun sideName(s: Int) = when (s) { -1 -> "left"; 1 -> "right"; else -> "bottom" }

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

    fun debugHide(side: Int, rest: Boolean = false) = hideToEdge(side, rest)
    /** v10.24 tests: put the box at ([x], [y]) (top-left px) standing on the floor or free. */
    fun debugPlace(x: Float, onFloor: Boolean, y: Float = 0f) { mx = x; my = if (onFloor) ground() else y; surf = if (onFloor) screenSurf(SK.FLOOR) else freeSurf(my); falling = false }
    /** v10.24 tests: release after a drag of [travel] px with fling velocity ([fvx], [fvy]) px/s. */
    fun debugRelease(fvx: Float, fvy: Float, travel: Float = 200f) { upSwipeDown = -1f; dragTravel = travel; settleAfterDrag(fvx, fvy) }
    val debugXY get() = floatArrayOf(mx, my, ground(), sw(), boxW.toFloat(), boxH.toFloat())
    val debugImpact get() = impact != null
    /** v10.25.1 tests: the phone's swipe-down release (upSwipeDown set, as onTouchEvent does). */
    fun debugSwipeDown(v: Float) { upSwipeDown = v; dragTravel = 300f; settleAfterDrag(0f, v); upSwipeDown = -1f }
    fun debugBellySpray() = startTrunkSpray()
    val debugSpraying get() = sprayT >= 0f
    val debugHidden get() = hiddenTab
    val debugRestBlock get() = "drag=$dragging track=$tracking fall=$falling tween=$tweenT hide=$hideAnim ent=$entKind typing=$typing pend=$pendingShown think=$thinkingBubble busy=${ChatHub.busy} convo=$convoActive hubPend=${ChatHub.pending} st=${controller.state} power=$powerOn exiting=$exiting quit=$quitAsk restGo=$restGoIn nap=$restNap asleep=${controller.isAsleep}"
    val debugResting get() = resting
    val debugLoopRunning get() = running
    fun debugTabPeek() { tabPeekT = 0f; start() }
    fun debugPlay(s: MonkeyState, dur: Float = 0f) = controller.play(s, dur)
    fun debugSay(text: String) = showBubble(text, 30f)

    /** Tests / contact sheets: no speech bubbles. */
    var debugQuiet = false
    private val ATTEND_GRACE = 2.5f
    /** v10.13: the delivery/utility test pill is hidden from the UI (code kept). It sat on top of the lead character. */
    private val SHOW_UTILITY_PILL = false
    private val SHOW_RAT_SCALE = 0.72f
    val debugState get() = controller.state
    val debugLandStyle get() = landStyle
    val debugArc get() = arc
    val debugHovering get() = hoverOn
    val debugMaskClimb get() = if (!mcOn) "" else if (mcHang) "hang" else "climb"
    fun debugMaskHang() = startMaskHang()
    fun debugMaskClimb(side: Int) = startMaskClimb(side)
    val debugHoverFlames get() = hoverFlames
    val debugArcRot get() = arcRot + rotEase
    val debugArcPhase get() = arcPhase
    val debugLandFlames get() = landFlames
    val debugFalling get() = falling
    val debugSpotIndex: Int get() = surf.spot
    val debugSurfKind: String get() = surf.kind.name
    val debugLandBeat get() = landBeat
    /** Screen-space box origin + window origin (for touch simulation). */
    fun debugWin() = intArrayOf(winX, winY, winW, winH)
    fun debugFrames(n: Int, dt: Float = 1f / 60f) {
        if (!live) goLive()
        running = true
        if (debugNanos < lastNanos) debugNanos = lastNanos
        repeat(n) { debugNanos += (dt * 1e9f).toLong(); doFrame(debugNanos) }
    }

    companion object {
        /** v10.19.1 how long the tapped friendship heart stays up. */
        const val HEART_POPUP_SECS = 2.8f
        /** v10.18 Body Sense event texts that come from touches (voiced by the reaction engine instead). */
        private val TOUCH_EVENTS = listOf("you poked me", "you tapped me", "you're holding me", "you flung me", "you dragged me", "I bumped into the screen edge")
        /** v10.14 a held bubble waits this long after landing, and is dropped after this long (chat replies never). */
        const val SETTLE_SECS = 0.45f
        /** v10.14 the Billa & Chittu plug-in shock length. */
        const val SHOCK_SECS = 2.4f
        /** v10.20 how long the burnt soot face takes to wear off after the shock. */
        const val SOOT_FADE_SECS = 5f
        /** v10.20 the part of the charge trip (0..1) where the first-ever zap shocks him. */
        const val BIG_SHOCK_FROM = 0.45f
        const val BIG_SHOCK_TO = 0.75f
        /** v10.14 max rider tilt change per 60 Hz frame (degrees). */
        const val ROT_STEP_MAX = 9f
        const val HELD_STALE_SECS = 4f

        /** v10.8: a press this long (but shorter than the long-press menu) is a HOLD touch. */
        const val HOLD_SEC = 0.28f
        /** Opacity of effects (= Android 12+'s max for an untouchable overlay, so the FX window and in-window trails match). */
        const val FX_ALPHA = 204
        private const val POP = 0.32f
        private const val FADE = 0.25f
        private const val CONVO_SECS = 60f
        private val HEADPHONE_TYPES = setOf(android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET, android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, android.media.AudioDeviceInfo.TYPE_USB_HEADSET, 26 /* BLE_HEADSET */)
        private const val TAB_PEEK_SECS = 3.2f
        /** v10.24: a swipe faster than this (dp/s) sends a character on its own swipe trip. */
        const val SWIPE_TRIP_DP = 700f
        /** v10.9: a swipe faster than this (dp/s) launches Rocket Girl's rocket. */
        const val ROCKET_SWIPE_DP = 380f
        /** v10.10: a rocket launch needs a real swipe (at least this far), never a tap. */
        const val ROCKET_SWIPE_MIN_TRAVEL_DP = 36f
        /** Her body lean while flying (deg): part of the rocket's on-screen angle. */
        const val ROCKET_FLY_LEAN = 4f
        /** v10.9 hints when double-tap = power and chat lives on the 💬 button. */
        val DT_POWER_HINTS = listOf("Tap the 💬 button to chat with me! Double-tap me for a power ⚡", "Psst… 💬 to talk, double-tap me for a surprise power! ⚡")
    }
}
