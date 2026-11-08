package com.combo.monkeycompanion.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import com.combo.monkeycompanion.ui.ChatPanelView
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.combo.monkeycompanion.powers.LaunchGate
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.combo.monkeycompanion.R

/**
 * Foreground service that keeps the monkey alive on top of other apps in a small
 * TYPE_APPLICATION_OVERLAY window (only monkey-sized, not focusable), so the rest
 * of the phone stays fully usable.
 */
class MonkeyOverlayService : Service(), OverlayHost {

    private lateinit var wm: WindowManager
    private lateinit var uiContext: Context
    private var screenW = 1080
    private var screenH = 2400
    private var view: MonkeyView? = null
    /** Window root: the monkey view plus the in-bubble reply text box on top of it. */
    private var root: OverlayRoot? = null
    private var params: WindowManager.LayoutParams? = null

    private inner class SecondarySlot(val charId: String, val slotIndex: Int) : OverlayHost {
        var slotView: MonkeyView? = null
        var slotRoot: OverlayRoot? = null
        var slotParams: WindowManager.LayoutParams? = null

        override fun screenWidth(): Int = screenW
        override fun screenHeight(): Int = screenH
        override fun topInset(): Int = insetTop
        override fun bottomInset(): Int = insetBottom
        override fun openChat() = this@MonkeyOverlayService.openChat()
        override fun placeWindow(x: Int, y: Int, w: Int, h: Int) {
            val lp = slotParams ?: return
            val r = slotRoot ?: return
            if (lp.x == x && lp.y == y && lp.width == w && lp.height == h) return
            lp.x = x; lp.y = y; lp.width = w; lp.height = h
            if (r.isAttachedToWindow) runCatching { wm.updateViewLayout(r, lp) }
        }
    }

    private val secondarySlots = ArrayList<SecondarySlot>()
    /** Path A IME detector: 1px transparent non-touchable probe window with FLAG_ALT_FOCUSABLE_IM + ADJUST_RESIZE. */
    private var imeProbe: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_QUIT, false).apply()
        running = true
        com.combo.monkeycompanion.agent.RoutineEvents.start(this)   // v4: event routines while Settai is on screen
        // Overlays should use a window context (API 30+) so WindowManager & metrics are "visual".
        uiContext = if (Build.VERSION.SDK_INT >= 30) {
            val dm = getSystemService(DisplayManager::class.java)
            createDisplayContext(dm.getDisplay(Display.DEFAULT_DISPLAY))
                .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else this
        wm = uiContext.getSystemService(WindowManager::class.java)
        refreshScreenSize()
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        // v10.31: while the mic listens, the service is also a microphone foreground service (Android 11+ only lets a
        // visible-overlay app use the mic "while in use" this way); back to special-use when the listen ends.
        com.combo.monkeycompanion.agent.Voice.listenHook = { on -> micForeground(on) }
        // v10.31: the notification (status bar + always-on display) icon follows the chosen character.
        com.combo.monkeycompanion.character.CharacterRegistry.onSelect = { android.os.Handler(mainLooper).post { sleepyNotification(false) } }
        addOverlay()
        // v10.38.25: every setting applies the moment you change it (no Save, no restart)
        getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(settingsListener)
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenReceiverOn = true
        com.combo.monkeycompanion.agent.Proactive.start(this)   // v4.2 proactive buddy (event-driven)
    }

    private var screenReceiverOn = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> { closeChat(); view?.goToSleep(); secondarySlots.forEach { it.slotView?.goToSleep() }; LaunchGate.overlayVisible = false; sleepyNotification(true) }
                Intent.ACTION_SCREEN_ON -> { view?.onScreenOn(); secondarySlots.forEach { it.slotView?.onScreenOn() } }
                Intent.ACTION_USER_PRESENT -> { com.combo.monkeycompanion.agent.NotifPromptGate.unlocked(); view?.onUserPresent(); secondarySlots.forEach { it.slotView?.onUserPresent() }; LaunchGate.overlayVisible = true; sleepyNotification(false) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_DANCE -> { view?.controller?.dance(); secondarySlots.forEach { it.slotView?.controller?.dance() } }
            ACTION_POWER -> { view?.usePower(explicit = true); secondarySlots.forEach { it.slotView?.usePower(explicit = true) } }
            ACTION_CHAT -> openChat()
            ACTION_SYNC_CREW -> syncCompanionWindows()
            ACTION_SMASH_HIDE -> setSmashHidden(true)
            ACTION_SMASH_SHOW -> setSmashHidden(false)
            ACTION_V5 -> {
                syncCompanionWindows()
                intent.getStringExtra("cmd")?.let { view?.v5Command(it) }
            }
        }
        return START_STICKY
    }

    private fun addOverlay() {
        val v = MonkeyView(uiContext, this)
        val r = OverlayRoot(uiContext) { v.onOutsideOrBack() }
        r.addView(v, android.widget.FrameLayout.LayoutParams(-1, -1))
        // Never hold the screen on from the overlay (normal screen timeout must work in every mode).
        r.keepScreenOn = false; v.keepScreenOn = false
        val edit = ReplyEditText(uiContext) { v.onOutsideOrBack() }.apply {
            background = null
            setTextColor(android.graphics.Color.rgb(0x3A, 0x22, 0x10))
            setHintTextColor(android.graphics.Color.rgb(0xA8, 0x84, 0x60))
            isSingleLine = true
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND or android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
        }
        r.addView(edit, android.widget.FrameLayout.LayoutParams(1, 1))
        v.attachReplyField(edit)
        val lp = WindowManager.LayoutParams(
            v.boxW, v.boxH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "MonkeyCompanion"
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= 30) fitInsetsTypes = 0
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }
        params = lp
        view = v
        root = r
        wm.addView(r, lp)
        attachImeProbe()
        LaunchGate.overlayVisible = true
        syncCompanionWindows()
        v.postDelayed({ v.controller.greet() }, 600)
    }

    /**
     * v9.3: Synchronizes live independent overlay companion windows with Solo (1), Duo (2), or Trio (3) mode!
     * - SOLO: 1 MonkeyView window on screen
     * - DUO:  2 independent MonkeyView windows on screen (e.g. Billa + Chittu chasing around all 4 screen edges!)
     * - TRIO: 3 independent MonkeyView windows on screen!
     */
    /** Kept as a field: SharedPreferences holds listeners weakly. */
    private val settingsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        settingsHandler.removeCallbacks(applySettingsRun)
        if (key == "character" || key?.startsWith("wear_") == true) characterChanged = true
        settingsHandler.postDelayed(applySettingsRun, 150L)   // a burst of changes = one refresh
    }
    private val settingsHandler = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var characterChanged = false
    private val applySettingsRun = Runnable {
        if (!running) return@Runnable
        if (characterChanged) {
            characterChanged = false
            runCatching { com.combo.monkeycompanion.ai.ChatHub.settings.applyCharacter() }
            runCatching { syncCompanionWindows() }
        }
        view?.applySettingsNow()
        secondarySlots.forEach { it.slotView?.applySettingsNow() }
    }

    fun syncCompanionWindows() {
        val primary = view ?: return
        com.combo.monkeycompanion.mind.MindHub.init(this)
        val desiredIds = com.combo.monkeycompanion.mind.MindHub.activeCreatures()
        val extraIds = desiredIds.drop(1)

        // Remove any secondary slots that are no longer in extraIds
        val toRemove = secondarySlots.filter { it.charId !in extraIds }
        for (slot in toRemove) {
            slot.slotRoot?.let { if (it.isAttachedToWindow) runCatching { wm.removeView(it) } }
            secondarySlots.remove(slot)
        }

        // Add any missing secondary slots
        for ((idx, cid) in extraIds.withIndex()) {
            if (secondarySlots.none { it.charId == cid }) {
                val slot = SecondarySlot(cid, idx + 1)
                val sv = MonkeyView(uiContext, slot).apply {
                    forcedCharacterId = cid
                    keepScreenOn = false
                }
                val sr = OverlayRoot(uiContext) { sv.onOutsideOrBack() }.apply {
                    keepScreenOn = false
                    addView(sv, android.widget.FrameLayout.LayoutParams(-1, -1))
                }
                val slp = WindowManager.LayoutParams(
                    sv.boxW, sv.boxH,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    title = "SettaiPartner_${cid}"
                    if (Build.VERSION.SDK_INT >= 28) {
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                    if (Build.VERSION.SDK_INT >= 30) fitInsetsTypes = 0
                }
                slot.slotView = sv
                slot.slotRoot = sr
                slot.slotParams = slp
                runCatching {
                    wm.addView(sr, slp)
                    secondarySlots.add(slot)
                    // Start partner #1 at left/right wall or opposite side so Duo/Trio spread out across the screen!
                    sv.post {
                        if (cid == "chittu" || cid == "billa") {
                            sv.debugSurf(if (idx % 2 == 0) "WALL_L" else "WALL_R", frac = 0.55f)
                            sv.controller.walkTo(sv.boxH * 1.5f, 1.2f)
                        } else {
                            sv.debugSurf("FLOOR", frac = if (idx == 0) 0.22f else 0.78f)
                        }
                    }
                }
            }
        }

        val allViews = listOf(primary) + secondarySlots.mapNotNull { it.slotView }
        for (v in allViews) {
            v.partnerViews = allViews.filter { it !== v }
        }
    }

    /** v10.5 Screen Smash: the characters step aside (invisible, frame loop paused) while the game owns the screen. */
    private fun setSmashHidden(hidden: Boolean) {
        val vis = if (hidden) View.INVISIBLE else View.VISIBLE
        root?.visibility = vis; view?.setPaused(hidden)
        for (s in secondarySlots) { s.slotRoot?.visibility = vis; s.slotView?.setPaused(hidden) }
    }

    private fun attachImeProbe() {
        val dens = resources.displayMetrics.density
        val p = View(uiContext)
        val insetListener = View.OnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val vis = insets.isVisible(WindowInsets.Type.ime())
                val b = insets.getInsets(WindowInsets.Type.ime()).bottom
                if (vis && b > (60 * dens).toInt()) com.combo.monkeycompanion.fun5.Stage.reportProbeIme(true, (screenH - b).toFloat())
                else if (!vis && b == 0) com.combo.monkeycompanion.fun5.Stage.reportProbeIme(false, 0f)
            }
            insets
        }
        p.setOnApplyWindowInsetsListener(insetListener)
        root?.setOnApplyWindowInsetsListener(insetListener)
        var maxSeenH = 0
        p.viewTreeObserver.addOnGlobalLayoutListener {
            val ph = p.height
            if (ph > maxSeenH) maxSeenH = ph
            if (maxSeenH >= screenH - insetBottom - (80 * dens).toInt() && ph > (80 * dens).toInt()) {
                val shrink = maxSeenH - ph
                if (shrink > (100 * dens).toInt()) {
                    com.combo.monkeycompanion.fun5.Stage.reportProbeIme(true, ph.toFloat())
                } else if (shrink <= (40 * dens).toInt()) {
                    com.combo.monkeycompanion.fun5.Stage.reportProbeIme(false, 0f)
                }
            }
        }
        val plp = WindowManager.LayoutParams(
            1, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "SettaiImeProbe"
            alpha = 0f
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        runCatching { wm.addView(p, plp); imeProbe = p }
    }

    // ---- Floating chat panel ----

    private var chatPanel: ChatPanelView? = null

    /** A focusable overlay window (so the keyboard works) docked at the bottom, above the keyboard. */
    override fun openChat() {
        view?.endTyping()
        if (chatPanel != null) { chatPanel?.focusInput(); return }
        if (!Settings.canDrawOverlays(this)) return
        val panel = ChatPanelView(uiContext, floating = true, onClose = { closeChat() })
        val margin = (10 * resources.displayMetrics.density).toInt()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            title = "MonkeyChat"
            horizontalMargin = 0f
            y = margin
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            if (Build.VERSION.SDK_INT >= 30) {
                // Keep the panel above the keyboard and the navigation bar.
                fitInsetsTypes = WindowInsets.Type.ime() or WindowInsets.Type.systemBars()
            }
        }
        panel.setPadding(0, 0, 0, 0)
        val wrap = android.widget.FrameLayout(uiContext).apply {
            setPadding(margin, 0, margin, 0)
            addView(panel)
        }
        wm.addView(wrap, lp)
        chatWrap = wrap
        chatPanel = panel
        panel.focusInput()
    }

    private var chatWrap: View? = null

    fun closeChat() {
        chatWrap?.let { if (it.isAttachedToWindow) wm.removeView(it) }
        chatWrap = null; chatPanel = null
    }

    // ---- OverlayHost ----

    override fun screenWidth(): Int = screenW
    override fun screenHeight(): Int = screenH

    private var insetTop = 0
    private var insetBottom = 0
    override fun topInset(): Int = insetTop
    override fun bottomInset(): Int = insetBottom

    private fun refreshScreenSize() {
        val s = screenSize()
        screenW = s[0]; screenH = s[1]
        val dens = resources.displayMetrics.density
        if (Build.VERSION.SDK_INT >= 30) {
            val ins = wm.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            insetTop = ins.top; insetBottom = ins.bottom
        } else {
            insetTop = (24 * dens).toInt(); insetBottom = (48 * dens).toInt()
        }
    }

    private fun screenSize(): IntArray {
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            intArrayOf(b.width(), b.height())
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
            intArrayOf(dm.widthPixels, dm.heightPixels)
        }
    }

    override fun placeWindow(x: Int, y: Int, w: Int, h: Int) {
        val lp = params ?: return
        val r = root ?: return
        if (lp.x == x && lp.y == y && lp.width == w && lp.height == h) return
        lp.x = x; lp.y = y; lp.width = w; lp.height = h
        if (r.isAttachedToWindow) wm.updateViewLayout(r, lp)
    }

    // ---- v4 effects window (particles / screen FX): full screen, untouchable, only while effects run ----
    private var fxView: FxView? = null

    private class FxView(c: android.content.Context) : android.view.View(c) {
        var drawer: ((android.graphics.Canvas) -> Unit)? = null
        override fun onDraw(canvas: android.graphics.Canvas) { drawer?.invoke(canvas) }
    }

    override fun showFx(draw: ((android.graphics.Canvas) -> Unit)?): Boolean {
        if (draw == null) {
            fxView?.let { v -> v.drawer = null; runCatching { if (v.isAttachedToWindow) wm.removeView(v) } }
            fxView = null
            return false
        }
        fxView?.let { it.drawer = draw; return true }
        if (!Settings.canDrawOverlays(this)) return false
        val v = FxView(uiContext).apply { drawer = draw }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "SettaiFx"
            // Android 12+ only lets touches through an untouchable overlay at <= 0.8 opacity; say so explicitly.
            alpha = MonkeyView.FX_ALPHA / 255f
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            if (Build.VERSION.SDK_INT >= 30) fitInsetsTypes = 0
        }
        return runCatching { wm.addView(v, lp); fxView = v; true }.getOrDefault(false)
    }

    override fun invalidateFx() { fxView?.invalidate() }

    override fun setTouchThrough(on: Boolean) {
        val lp = params ?: return
        val r = root ?: return
        val f = if (on) lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        else lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        if (f == lp.flags) return
        lp.flags = f
        if (r.isAttachedToWindow) wm.updateViewLayout(r, lp)
    }

    override fun setWindowFocusable(on: Boolean) {
        val lp = params ?: return
        val r = root ?: return
        val f = if (on) lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        else lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (f == lp.flags) return
        lp.flags = f
        if (r.isAttachedToWindow) wm.updateViewLayout(r, lp)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshScreenSize()
        view?.onScreenChanged()
    }

    /**
     * v3.2 "Quit Settai" (long-press -> Yes -> exit animation): stop for good. No restart until the user
     * opens the app (Bring Settai back) or uses the launcher shortcut. Settai's own timers/alarms are
     * AlarmManager alarms handled by ClockReceiver, so they keep working without this service.
     */
    override fun quitSettai() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_QUIT, true).apply()
        closeChat()
        imeProbe?.let { if (it.isAttachedToWindow) runCatching { wm.removeView(it) } }
        imeProbe = null
        com.combo.monkeycompanion.fun5.Stage.reportProbeIme(false, 0f)
        secondarySlots.forEach { s -> s.slotRoot?.let { if (it.isAttachedToWindow) runCatching { wm.removeView(it) } } }
        secondarySlots.clear()
        root?.let { if (it.isAttachedToWindow) runCatching { wm.removeView(it) } }
        root = null; view = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(settingsListener) }
        com.combo.monkeycompanion.agent.Voice.listenHook = null
        com.combo.monkeycompanion.character.CharacterRegistry.onSelect = null
        com.combo.monkeycompanion.agent.RoutineEvents.stop()
        com.combo.monkeycompanion.agent.Proactive.stop()
        showFx(null)
        closeChat()
        if (screenReceiverOn) { unregisterReceiver(screenReceiver); screenReceiverOn = false }
        imeProbe?.let { if (it.isAttachedToWindow) runCatching { wm.removeView(it) } }
        imeProbe = null
        com.combo.monkeycompanion.fun5.Stage.reportProbeIme(false, 0f)
        secondarySlots.forEach { s -> s.slotRoot?.let { if (it.isAttachedToWindow) runCatching { wm.removeView(it) } } }
        secondarySlots.clear()
        root?.let { if (it.isAttachedToWindow) wm.removeView(it) }
        view = null; root = null
        running = false
        LaunchGate.overlayVisible = false
        super.onDestroy()
    }

    // ---- Notification ----

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                    description = getString(R.string.channel_desc)
                    setShowBadge(false)
                }
            )
        }
    }

    /**
     * Overlays can't be drawn over the lock screen (Android hides TYPE_APPLICATION_OVERLAY windows
     * on the keyguard), so while locked the service notification shows Settai sleeping instead.
     */
    private fun sleepyNotification(asleep: Boolean) {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(asleep))
        }
    }

    private fun micForeground(on: Boolean) {
        if (Build.VERSION.SDK_INT < 30) return
        val type = if (on && com.combo.monkeycompanion.powers.Perm.MIC.granted(this))
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        else ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        runCatching { ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), if (Build.VERSION.SDK_INT >= 34) type else 0) }
    }

    // v10.31: per character (was a lazy val: stuck on whoever was picked first)
    private var sleepyFor: String? = null
    private var sleepyBmp: android.graphics.Bitmap? = null
    private val sleepyPortrait: android.graphics.Bitmap? get() {
        val id = com.combo.monkeycompanion.character.CharacterRegistry.current.id
        if (sleepyFor != id || sleepyBmp == null) { sleepyBmp = runCatching { Portraits.sleeping(this, (96 * resources.displayMetrics.density).toInt()) }.getOrNull(); sleepyFor = id }
        return sleepyBmp
    }

    private fun buildNotification(asleep: Boolean = false): Notification {
        val pi = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(this, 0, Intent(this, com.combo.monkeycompanion.ui.SettaiDesignActivity::class.java), pi)
        val stop = PendingIntent.getService(this, 1, Intent(this, MonkeyOverlayService::class.java).setAction(ACTION_STOP), pi)
        val dance = PendingIntent.getService(this, 2, Intent(this, MonkeyOverlayService::class.java).setAction(ACTION_DANCE), pi)
        val chat = PendingIntent.getService(this, 3, Intent(this, MonkeyOverlayService::class.java).setAction(ACTION_CHAT), pi)
        return NotificationCompat.Builder(this, CHANNEL)
            .let { CharacterIcon.applyTo(it, this) }
            .setContentTitle(com.combo.monkeycompanion.character.CharacterRegistry.current.name.let { n -> if (asleep) "$n is asleep 💤" else if (n == "Settai") getString(R.string.notif_title) else "$n is awake ✨" })
            .setContentText(if (asleep) (if (com.combo.monkeycompanion.character.CharacterRegistry.current.can(com.combo.monkeycompanion.character.Locomotion.CLIMB)) "Zzz… unlock and I'll climb back up!" else "Zzz… unlock and I'll be right back!") else getString(R.string.notif_text))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .apply { if (asleep) sleepyPortrait?.let { setLargeIcon(it) } }
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.action_chat), chat)
            .addAction(0, getString(R.string.action_dance), dance)
            .addAction(0, getString(R.string.action_stop), stop)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.combo.monkeycompanion.STOP"
        const val ACTION_DANCE = "com.combo.monkeycompanion.DANCE"
        const val ACTION_POWER = "com.combo.monkeycompanion.POWER"
        const val ACTION_CHAT = "com.combo.monkeycompanion.CHAT"
        const val ACTION_V5 = "com.combo.monkeycompanion.V5"
        const val ACTION_SYNC_CREW = "com.combo.monkeycompanion.SYNC_CREW"
        const val ACTION_SMASH_HIDE = "com.combo.monkeycompanion.SMASH_HIDE"
        const val ACTION_SMASH_SHOW = "com.combo.monkeycompanion.SMASH_SHOW"

        fun syncCrew(ctx: Context) {
            if (running) {
                runCatching {
                    ctx.startService(Intent(ctx, MonkeyOverlayService::class.java).setAction(ACTION_SYNC_CREW))
                }
            } else if (Settings.canDrawOverlays(ctx)) {
                start(ctx)
            }
        }
        const val PREFS = "settai_v3"
        /** [com.combo.monkeycompanion.ai.AppSettings] file. */
        const val SETTINGS_PREFS = "monkey_settings"
        /** Set when the user quit Settai with the long-press popup; cleared when it's brought back. */
        const val KEY_QUIT = "quitByUser"

        fun quitByUser(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_QUIT, false)
        private const val CHANNEL = "monkey"
        private const val NOTIF_ID = 42

        @Volatile var running = false; private set

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, MonkeyOverlayService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, MonkeyOverlayService::class.java))
        }
    }
}

/** Overlay window root: reports taps outside the window and BACK, so typing can end. */
@android.annotation.SuppressLint("ViewConstructor")
class OverlayRoot(ctx: Context, private val onOutsideOrBack: () -> Boolean) : android.widget.FrameLayout(ctx) {
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_OUTSIDE) { onOutsideOrBack(); return true }
        return super.dispatchTouchEvent(ev)
    }
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            if (event.action == android.view.KeyEvent.ACTION_UP) onOutsideOrBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

/** The in-bubble text box; BACK (even while the keyboard is up) ends typing. */
@android.annotation.SuppressLint("ViewConstructor", "AppCompatCustomView")
class ReplyEditText(ctx: Context, private val onBack: () -> Boolean) : android.widget.EditText(ctx) {
    override fun onKeyPreIme(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_DOWN && onBack()) return true
        return super.onKeyPreIme(keyCode, event)
    }
}
