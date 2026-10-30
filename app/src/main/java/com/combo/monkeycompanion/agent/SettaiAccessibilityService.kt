package com.combo.monkeycompanion.agent

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * v4 "screen agent": lets Settai read the current screen and tap / type / scroll / go back when YOU
 * ask it to. It listens only to window changes (cheap), keeps nothing on disk and never acts on its
 * own: every tap or typed text is asked in Settai's bubble first (see [ScreenAgent] / [AgentSafety]).
 */
class SettaiAccessibilityService : AccessibilityService() {
    @Volatile private var lastPkg = ""
    @Volatile private var editableUntil = 0L
    private var refs: List<AccessibilityNodeInfo> = emptyList()

    public override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ev = event ?: return
        val pkg = ev.packageName?.toString().orEmpty()
        when (ev.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (pkg != packageName) {
                    val src = runCatching { ev.source }.getOrNull()
                    val editable = src?.isEditable == true || ev.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                    if (editable) {
                        editableUntil = android.os.SystemClock.uptimeMillis() + 8_000L
                        refreshImeState()
                    } else if (ev.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED && src != null && !src.isEditable) {
                        refreshImeState()
                    }
                }
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> refreshImeState()
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                refreshImeState()
                if (pkg.isEmpty() || pkg == packageName || pkg in IGNORE || isImePkg(pkg)) return
                lastPkg = pkg
                Proactive.onWindow(pkg)   // v4.2: debounced app-switch moments (no polling)
            }
        }
    }

    /** v4.2: the app in front (last real window-state change). */
    val currentPkg: String get() = lastPkg

    private var imes: Set<String>? = null
    private fun imePkgs(): Set<String> = imes ?: runCatching {
        getSystemService(android.view.inputmethod.InputMethodManager::class.java).inputMethodList.map { it.packageName }.toSet()
    }.getOrDefault(emptySet()).also { imes = it }

    private fun isImePkg(pkg: String): Boolean {
        val p = pkg.lowercase()
        return pkg in imePkgs() || "inputmethod" in p || "keyboard" in p || "gboard" in p || "honeyboard" in p || "swiftkey" in p
    }

    /** Inspects interactive windows + editable focus to detect soft keyboard visibility and its top Y in screen px. */
    fun detectIme(): Pair<Boolean, Float> = runCatching {
        val dm = resources.displayMetrics
        val sh = dm.heightPixels.toFloat().coerceAtLeast(1f)
        val ws = windows
        val r = android.graphics.Rect()
        for (w in ws) {
            val wPkg = runCatching { w.root?.packageName?.toString().orEmpty() }.getOrDefault("")
            if (w.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD || (wPkg.isNotEmpty() && isImePkg(wPkg))) {
                w.getBoundsInScreen(r)
                val top = if (r.height() > (60 * dm.density).toInt() && r.top in 1 until dm.heightPixels) r.top.toFloat() else sh * 0.55f
                return@runCatching true to top
            }
        }
        if (android.os.SystemClock.uptimeMillis() < editableUntil) return@runCatching true to (sh * 0.55f)
        false to sh
    }.getOrDefault(false to 0f)

    fun refreshImeState() {
        val (vis, top) = detectIme()
        com.combo.monkeycompanion.fun5.Stage.reportA11yIme(vis, top)
    }

    /** v4.2/v7.0: the keyboard is up (the user is typing somewhere): proactive lines wait and Settai dodges above the keyboard. */
    fun imeVisible(): Boolean {
        val (vis, top) = detectIme()
        com.combo.monkeycompanion.fun5.Stage.reportA11yIme(vis, top)
        return vis || com.combo.monkeycompanion.fun5.Stage.imeVisible
    }

    /** v4.2 (best effort): the app window covers the whole display and no status bar is showing (fullscreen video / game). */
    fun fullscreen(): Boolean = runCatching {
        val ws = windows
        val appWin = ws.firstOrNull { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION && it.root?.packageName?.toString() != packageName } ?: return false
        val r = android.graphics.Rect(); appWin.getBoundsInScreen(r)
        val dm = resources.displayMetrics
        val full = r.top <= 0 && r.width() >= minOf(dm.heightPixels, dm.widthPixels)
        val statusBar = ws.any { w -> w.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM && android.graphics.Rect().also { w.getBoundsInScreen(it) }.let { it.top == 0 && it.height() in 1..(60 * dm.density).toInt() } }
        full && !statusBar
    }.getOrDefault(false)
    override fun onInterrupt() {}
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }
    override fun onUnbind(intent: android.content.Intent?): Boolean { if (instance === this) instance = null; return super.onUnbind(intent) }

    private fun label(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** The app window under Settai (skipping our own overlay), as a [ScreenSnapshot]. */
    /** [keepRefs] false: a look only (proactive digest) that doesn't replace the nodes tap / type act on. */
    @Synchronized fun snapshot(keepRefs: Boolean = true): ScreenSnapshot {
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val root = pickRoot()
        val nodes = ArrayList<UiNode>(); val keep = ArrayList<AccessibilityNodeInfo>()
        val pkg = root?.packageName?.toString() ?: lastPkg
        if (root != null) {
            val queue = ArrayDeque<Triple<AccessibilityNodeInfo, Int, Int>>(); queue.add(Triple(root, 0, 0))
            val r = android.graphics.Rect()
            while (queue.isNotEmpty() && nodes.size < MAX_NODES) {
                val (n, depth, par) = queue.removeFirst()
                if (!n.isVisibleToUser) continue
                val txt = n.text?.toString().orEmpty(); val desc = n.contentDescription?.toString().orEmpty()
                val useful = n.isClickable || n.isEditable || n.isScrollable || txt.isNotBlank() || desc.isNotBlank() || n.isCheckable
                var me = par
                if (useful) {
                    n.getBoundsInScreen(r)
                    // v4.2: hint text (empty fields) helps spot "Message" boxes; never for passwords.
                    val hint = if (n.isEditable && !n.isPassword && txt.isBlank() && android.os.Build.VERSION.SDK_INT >= 26) n.hintText?.toString().orEmpty() else ""
                    nodes.add(UiNode(nodes.size + 1, n.className?.toString().orEmpty(), if (n.isPassword) "" else txt, desc.ifBlank { hint }, n.viewIdResourceName.orEmpty(), n.isClickable || n.isLongClickable,
                        n.isEditable, n.isScrollable, if (n.isCheckable) n.isChecked else null, n.isPassword, n.isFocused, depth, par, r.top, r.left))
                    keep.add(n); me = nodes.size
                }
                for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(Triple(it, depth + 1, me)) }
            }
        }
        if (keepRefs) refs = keep
        return ScreenSnapshot(pkg, label(pkg), nodes, locked)
    }

    private fun pickRoot(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (active != null && active.packageName?.toString() != packageName) return active
        // Our overlay can be the active window: take the top application window instead.
        return runCatching { windows.filter { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION }.mapNotNull { it.root }
            .firstOrNull { it.packageName?.toString() != packageName } }.getOrNull() ?: active
    }

    private fun ref(n: UiNode): AccessibilityNodeInfo? = refs.getOrNull(n.index - 1)

    /** Taps [n] (or its nearest tappable parent). */
    fun tap(n: UiNode): Boolean {
        var x = ref(n) ?: return false
        repeat(6) { if (x.isClickable) return x.performAction(AccessibilityNodeInfo.ACTION_CLICK); x = x.parent ?: return false }
        return false
    }

    fun type(n: UiNode, text: String): Boolean {
        val x = ref(n) ?: return false
        if (x.isPassword) return false
        x.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val b = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return x.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    fun scroll(forward: Boolean, n: UiNode? = null): Boolean {
        val target = n?.let { ref(it) } ?: refs.firstOrNull { it.isScrollable } ?: return false
        return target.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    fun key(which: String): Boolean = performGlobalAction(when (which) {
        "back" -> GLOBAL_ACTION_BACK; "home" -> GLOBAL_ACTION_HOME; "recents" -> GLOBAL_ACTION_RECENTS
        "notifications" -> GLOBAL_ACTION_NOTIFICATIONS; "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
        else -> return false
    })

    companion object {
        private const val MAX_NODES = 300
        private val IGNORE = setOf("com.android.systemui", "android", "com.google.android.inputmethod.latin")
        @Volatile var instance: SettaiAccessibilityService? = null; private set

        fun enabled(ctx: Context): Boolean =
            (Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
                .split(':').any { it.equals(ComponentName(ctx, SettaiAccessibilityService::class.java).flattenToString(), true) }
    }
}
