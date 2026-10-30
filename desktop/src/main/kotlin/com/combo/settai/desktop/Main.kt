package com.combo.settai.desktop

import com.combo.monkeycompanion.ai.BodyAction
import com.combo.monkeycompanion.ai.Persona
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.Animations
import com.combo.monkeycompanion.rig.MonkeyRenderer
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose
import java.awt.*
import java.awt.event.*
import java.awt.geom.Area
import java.awt.image.BufferedImage
import java.io.File
import java.io.RandomAccessFile
import javax.imageio.ImageIO
import javax.swing.*
import kotlin.system.exitProcess

const val VERSION = "3.0"

fun main(args: Array<String>) {
    System.setProperty("awt.useSystemAAFontSettings", "on")
    System.setProperty("sun.java2d.xrender", "true")
    if (args.firstOrNull() == "--render-icon") { renderIcon(File(args[1]), args.getOrNull(2)?.toInt() ?: 256); return }
    if (args.firstOrNull() == "--version") { println("Settai $VERSION"); return }
    if (args.firstOrNull() == "--help") {
        println("Settai $VERSION — desktop monkey companion\n  settai               start\n  settai --screenshot FILE [--delay S] [--demo]   save a screen capture\n  settai --render-icon FILE [SIZE]"); return
    }
    // Nice WM_CLASS ("Settai") so GNOME matches the window to settai.desktop.
    runCatching {
        val tk = Toolkit.getDefaultToolkit()
        tk.javaClass.getDeclaredField("awtAppClassName").apply { isAccessible = true }.set(tk, "Settai")
    }
    val cfg = Config()
    // One Settai at a time.
    val lock = runCatching { RandomAccessFile(File(cfg.dir, "lock"), "rw").channel.tryLock() }.getOrNull()
    if (lock == null) { System.err.println("Settai is already running."); exitProcess(0) }
    if (GraphicsEnvironment.isHeadless()) { System.err.println("Settai needs a graphical session (DISPLAY)."); exitProcess(1) }
    JPopupMenu.setDefaultLightWeightPopupEnabled(false)
    Persona.screen = Platform.current.home + " (you float on top of their windows)"
    SwingUtilities.invokeLater { App(cfg, args).start() }
}

/** Renders Settai's face for icons. */
fun renderIcon(out: File, size: Int) {
    val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    val c = android.graphics.Canvas(g)
    val rig = MonkeyRig(); val p = Pose()
    Animations.fill(p, MonkeyState.IDLE, 0.3f, 0.3f, AnimContext())
    val s = size / 115f
    // Head fills the icon: lay the rig out so the head is centred.
    rig.layout(p, size / 2f, size * 1.62f, s * 1.0f, 1f)
    MonkeyRenderer().draw(c, rig, p, 0.3f)
    g.dispose()
    out.parentFile?.mkdirs(); ImageIO.write(img, "png", out)
}

class App(private val cfg: Config, private val args: Array<String>) : OverlayHost {
    private val frame = JFrame("Settai")
    private val chat = DesktopChat(cfg)
    private lateinit var stage: Stage
    private var wa: Rectangle = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    private val platform = Platform.current
    private var shapeOk = System.getenv("SETTAI_NOSHAPE") == null && platform.useWindowShape
    private var lastShape: List<Rectangle> = emptyList()
    private var settings: SettingsWindow? = null
    private var chatWin: ChatWindow? = null
    private var tray: TrayIcon? = null

    fun start() {
        stage = Stage(this, chat, cfg)
        chat.sys = { stage.sys }
        if (System.getenv("SETTAI_DEBUG") != null) chat.addListener(object : DesktopChat.Listener {
            override fun onAction(a: BodyAction) { System.err.println("ACTION ${a.tag}") }
            override fun onReply(text: String, done: Boolean) { if (done) System.err.println("REPLY $text") }
        })
        frame.apply {
            isUndecorated = true
            type = Window.Type.UTILITY
            background = Color(0, 0, 0, 0)
            isAlwaysOnTop = true
            setAutoRequestFocus(false)
            focusableWindowState = platform.alwaysFocusable
            defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
            contentPane = stage
            iconImages = listOf(64, 128).map { iconImage(it) }
        }
        stage.isOpaque = false
        stage.isFocusable = true
        stage.focusTraversalKeysEnabled = false
        installInput()
        stage.goLive()
        frame.addNotify()
        runCatching { platform.prepareWindow(frame) }.onFailure { System.err.println("Window tweaks failed: $it") }
        frame.isVisible = true
        installTray()
        startMonitors()
        syncAutostart()
        if ("--demo" in args) demo()
        if (System.getenv("SETTAI_DEBUG") != null) Timer(2000) {
            System.err.println("STATUS state=${stage.controller.state} hidden=${stage.isHiddenAtEdge} vis=${frame.isVisible} bounds=${frame.bounds} regions=${stage.hitRegions()} shapeOk=$shapeOk")
        }.start()
        args.indexOf("--screenshot").takeIf { it >= 0 }?.let { i ->
            val path = args[i + 1]
            val delay = args.indexOf("--delay").takeIf { it >= 0 }?.let { args[it + 1].toFloat() } ?: 6f
            Timer((delay * 1000).toInt()) { screenshot(File(path)); if ("--keep" !in args) quit() }.apply { isRepeats = false }.start()
        }
    }

    // ---------- OverlayHost ----------
    override fun screenWidth() = wa.width
    override fun screenHeight() = wa.height
    override fun placeWindow(x: Int, y: Int, w: Int, h: Int) {
        val b = Rectangle(wa.x + x, wa.y + y, w.coerceAtLeast(1), h.coerceAtLeast(1))
        if (frame.bounds != b) frame.bounds = b
        if (System.getenv("SETTAI_DEBUG") == "2") System.err.println("place $b actual=${frame.bounds} regions=${stage.hitRegions()}")
    }
    override fun openChat() { (chatWin ?: ChatWindow(chat).also { chatWin = it }).open() }
    override fun setWindowFocusable(on: Boolean) {
        if (on) {
            // Toggling focusableWindowState on a mapped X11 window remaps it (a ~1s blink),
            // so switch it on once, the first time he's asked to type, and leave it on.
            if (!frame.focusableWindowState) frame.focusableWindowState = true
            frame.toFront(); frame.requestFocus(); stage.requestFocusInWindow()
            // Some window managers ignore the focus request the first time.
            Timer(120) { if (!stage.isFocusOwner && stage.isTyping) { frame.toFront(); frame.requestFocus(); stage.requestFocusInWindow() } }.apply { isRepeats = false }.start()
        }
    }
    override fun onFrame() {
        if (!shapeOk) return
        // Click-through outside Settai and the bubble (X Shape extension).
        val r = stage.hitRegions()
        if (r == lastShape) return
        lastShape = r
        runCatching {
            val a = Area(); r.forEach { a.add(Area(it)) }
            frame.shape = if (a.isEmpty) Rectangle(0, 0, 1, 1) else a
        }.onFailure { shapeOk = false; System.err.println("Window shaping unavailable: ${it.message}") }
    }

    // ---------- input ----------
    private fun ev(action: Int, e: MouseEvent): MotionEvent {
        if (System.getenv("SETTAI_DEBUG") != null && action != MotionEvent.ACTION_MOVE) System.err.println("MOUSE $action ${e.x},${e.y} t=${e.`when`} state=${stage.controller.state} zone=${stage.debugZone(e.x.toFloat(), e.y.toFloat())}")
        return MotionEvent(action, e.x.toFloat(), e.y.toFloat(), (e.xOnScreen - wa.x).toFloat(), (e.yOnScreen - wa.y).toFloat(), e.`when`)
    }

    private fun installInput() {
        val m = object : MouseAdapter() {
            var down = false
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger) { menu().show(stage, e.x, e.y); return }
                if (!SwingUtilities.isLeftMouseButton(e)) return
                down = true; stage.onTouchEvent(ev(MotionEvent.ACTION_DOWN, e))
            }
            override fun mouseDragged(e: MouseEvent) { if (down) stage.onTouchEvent(ev(MotionEvent.ACTION_MOVE, e)) }
            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) { menu().show(stage, e.x, e.y); return }
                if (!down) return
                down = false; stage.onTouchEvent(ev(MotionEvent.ACTION_UP, e))
            }
        }
        stage.addMouseListener(m); stage.addMouseMotionListener(m)
        stage.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) { if (stage.onKey(e)) e.consume() }
            override fun keyTyped(e: KeyEvent) { if (stage.onKey(e)) e.consume() }
        })
        frame.addWindowFocusListener(object : WindowAdapter() {
            override fun windowLostFocus(e: WindowEvent) { if (stage.isTyping) stage.onOutsideOrBack() }
        })
    }

    private fun menu() = JPopupMenu().apply {
        isLightWeightPopupEnabled = false   // its own window: Settai's window is tiny and shaped
        fun item(label: String, f: () -> Unit) = add(JMenuItem(label).apply { addActionListener { f() } })
        item("Chat in bubble") { stage.openBubbleChat() }
        item("Chat window…") { openChat() }
        addSeparator()
        item("Dance!") { stage.act(BodyAction.DANCE) }
        item("Wave") { stage.act(BodyAction.WAVE) }
        item("Climb") { stage.act(BodyAction.CLIMB) }
        item(if (stage.isHiddenAtEdge) "Come back" else "Hide at the edge") { stage.hideNow() }
        addSeparator()
        item("Settings…") { openSettings() }
        item("Quit Settai") { quit() }
    }

    private fun installTray() {
        if (!SystemTray.isSupported()) return
        runCatching {
            val pm = PopupMenu()
            fun item(label: String, f: () -> Unit) = pm.add(MenuItem(label).apply { addActionListener { SwingUtilities.invokeLater(f) } })
            item("Show / hide Settai") { toggleShown() }
            item("Chat") { if (!frame.isVisible) toggleShown(); stage.openBubbleChat() }
            item("Dance!") { if (!frame.isVisible) toggleShown(); stage.act(BodyAction.DANCE) }
            item("Settings…") { openSettings() }
            pm.addSeparator()
            item("Quit") { quit() }
            val sz = SystemTray.getSystemTray().trayIconSize
            val icon = TrayIcon(iconImage(maxOf(sz.width, sz.height, 22)), "Settai", pm).apply { isImageAutoSize = true }
            icon.addActionListener { SwingUtilities.invokeLater { toggleShown() } }
            SystemTray.getSystemTray().add(icon)
            tray = icon
        }.onFailure { System.err.println("Tray unavailable: ${it.message}") }
    }

    private fun toggleShown() {
        if (frame.isVisible) { stage.setPaused(true); frame.isVisible = false }
        else { frame.isVisible = true; stage.setPaused(false) }
    }

    fun openSettings() {
        (settings ?: SettingsWindow(cfg, chat) { syncAutostart() }.also { settings = it }).open()
    }

    private fun quit() {
        runCatching { tray?.let { SystemTray.getSystemTray().remove(it) } }
        cfg.save(); stage.goDead(); frame.dispose(); exitProcess(0)
    }

    // ---------- monitors ----------
    private fun startMonitors() {
        // Work area (panels / screen resolution changes).
        Timer(4000) {
            val n = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
            if (n != wa) { wa = n; stage.onScreenChanged() }
        }.start()

        // Screen lock (platform-specific polling).
        Thread({
            var locked = false
            val fake = System.getenv("SETTAI_FAKE_LOCK")   // smoke tests: lock = this file exists
            while (true) {
                val now: Boolean? = if (fake != null) File(fake).exists() else runCatching { platform.screenLocked() }.getOrNull()
                if (now != null && now != locked) {
                    locked = now
                    SwingUtilities.invokeLater { if (now) stage.goToSleep() else stage.onUserPresent() }
                }
                Thread.sleep(platform.lockPollMs)
            }
        }, "settai-lock").apply { isDaemon = true }.start()

        // Laptop vitals.
        Thread({
            val last = HashMap<String, Long>()
            fun due(k: String, minutes: Int): Boolean {
                val t = System.currentTimeMillis()
                if (t - (last[k] ?: 0L) < minutes * 60_000L) return false
                last[k] = t; return true
            }
            var hogCount = 0
            Thread.sleep(3000)
            while (true) {
                val s = runCatching { platform.read() }.getOrNull()
                if (s != null) SwingUtilities.invokeLater {
                    stage.sys = s
                    val warn: Pair<String, String>? = when {
                        s.battery in 0..10 && !s.charging -> "bat" to "Battery's at ${s.battery}%! Plug me in before I faint!"
                        s.battery in 11..20 && !s.charging -> "bat" to "Battery ${s.battery}%… my bananas are running low. Charger?"
                        !s.cpuTemp.isNaN() && s.cpuTemp >= 92f -> "hot" to (if (s.tempEstimated) "Phew, the CPU's been flat out at ${s.load.toInt()}% for a while! I'm sweating for it."
                            else "Phew, CPU at ${s.cpuTemp.toInt()}°C! It's a sauna in here.")
                        s.ramUsedPct >= 92 -> "ram" to "RAM is ${s.ramUsedPct}% full. Stuffed like my cheeks! Close something?"
                        s.diskFreeGb in 0f..5f -> "disk" to "Only %.1f GB of disk left! Time to clean up?".format(s.diskFreeGb)
                        hogCount >= 3 && s.topProcess != null -> "hog" to "${s.topProcess} is hogging the CPU (${s.topCpu.toInt()}%). Naughty!"
                        else -> null
                    }
                    hogCount = if (s.topCpu >= 85f) hogCount + 1 else 0
                    if (warn != null) {
                        val minutes = mapOf("bat" to 10, "hot" to 10, "ram" to 15, "disk" to 60, "hog" to 20)[warn.first] ?: 15
                        if ((last[warn.first] ?: 0L) + minutes * 60_000L < System.currentTimeMillis() && stage.remark(warn.second)) last[warn.first] = System.currentTimeMillis()
                    }
                }
                Thread.sleep(10_000)
            }
        }, "settai-sys").apply { isDaemon = true }.start()
    }

    private fun syncAutostart() {
        runCatching { platform.setAutostart(cfg.autostart) }.onFailure { System.err.println("Autostart: $it") }
    }

    private fun screenshot(out: File) {
        runCatching {
            val all = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.first().defaultConfiguration.bounds
            val img = Robot().createScreenCapture(all)
            out.parentFile?.mkdirs(); ImageIO.write(img, "png", out)
            println("screenshot: ${out.absolutePath}")
        }.onFailure { System.err.println("screenshot failed: $it") }
    }

    /** For smoke tests: say hi and show off. */
    private fun demo() {
        val scene = args.indexOf("--scene").takeIf { it >= 0 }?.let { args[it + 1] } ?: "hello"
        val steps = when (scene) {
            "wall" -> listOf<Pair<Int, () -> Unit>>(1200 to { stage.debugSurf("WALL_R", -1, 0.45f) }, 1600 to { stage.debugWalk(0.2f, 0.6f) })
            "ceil" -> listOf<Pair<Int, () -> Unit>>(1200 to { stage.debugSurf("CEIL", -1, 0.55f) }, 1500 to { stage.debugWalk(0.35f, 0.7f) })
            "tab" -> listOf<Pair<Int, () -> Unit>>(1200 to { stage.debugHide(1) })
            "sweat" -> listOf<Pair<Int, () -> Unit>>(3600 to { stage.sys = stage.sys.copy(cpuTemp = 95f, dnd = false); stage.debugSay("Phew, CPU at 95°C! It's a sauna in here.") })
            else -> listOf<Pair<Int, () -> Unit>>(1500 to { stage.debugSay("Hi! I'm Settai. Double-click me to chat!") })
        }
        for ((ms, f) in steps) Timer(ms) { f() }.apply { isRepeats = false }.start()
    }
}

private val iconCache = HashMap<Int, Image>()
fun iconImage(size: Int): Image = iconCache.getOrPut(size) {
    val f = File.createTempFile("settai-icon", ".png"); renderIcon(f, size)
    ImageIO.read(f).also { f.delete() }
}
