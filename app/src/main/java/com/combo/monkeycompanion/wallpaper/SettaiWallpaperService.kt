package com.combo.monkeycompanion.wallpaper

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.overlay.MonkeyOverlayService
import com.combo.monkeycompanion.overlay.MonkeyView
import com.combo.monkeycompanion.overlay.OverlayHost
import com.combo.monkeycompanion.ui.ChatActivity

/**
 * Settai as a live wallpaper: icons and widgets sit in front of it. It roams, sleeps, wakes,
 * dances and plays with the user's "climbing spots" (rectangles lined up with widgets).
 * Settai stays in screen space (page offsets are ignored). Rendering stops when not visible.
 * If the floating overlay is running, the wallpaper shows only the background (one Settai at a time).
 */
class SettaiWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = SettaiEngine()

    inner class SettaiEngine : Engine(), OverlayHost {
        private val ctx: Context get() = this@SettaiWallpaperService
        private val bg = WallpaperBackground(this@SettaiWallpaperService)
        private val main = Handler(Looper.getMainLooper())
        private var view: MonkeyView? = null
        private var w = 0; private var h = 0
        private var visible = false
        private var shown = true
        private var ver = -1
        private var lastTouch = 0L
        private var asleep = false
        /** Keyguard showing: the overlay can't be drawn there, so the wallpaper shows Settai asleep. */
        private var locked = false
        private fun keyguardLocked() = runCatching { ctx.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked }.getOrDefault(false)
        private fun wantShown() = !MonkeyOverlayService.running || locked

        private val screen = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val v = view ?: return
                when (i.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        asleep = true; locked = true
                        if (!shown) { shown = true; applyShown() }
                        v.goToSleep(); drawFrame()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        locked = keyguardLocked()
                        if (shown) v.onScreenOn()
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        asleep = false; locked = false
                        val s = wantShown()
                        if (s != shown) { shown = s; applyShown() }   // overlay running: it does the wake entrance
                        if (shown) v.onUserPresent()
                    }
                }
            }
        }

        /** Notice the overlay starting / stopping, and edited spots or background. */
        private val watch = object : Runnable {
            override fun run() {
                if (!visible) return
                if (locked && !keyguardLocked()) locked = false
                val s = wantShown()
                if (s != shown) { shown = s; applyShown() }
                val v = WallpaperPrefs.version(ctx)
                if (v != ver) { ver = v; view?.spotsChanged(); if (!shown) drawFrame() }
                main.postDelayed(this, 5000)   // light check; was 2 s
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            com.combo.monkeycompanion.agent.RoutineEvents.start(this@SettaiWallpaperService)
            super.onCreate(surfaceHolder)
            ChatHub.init(ctx)
            setTouchEventsEnabled(true)
            setOffsetNotificationsEnabled(false)
            val f = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT) }
            if (android.os.Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(screen, f, Context.RECEIVER_NOT_EXPORTED) else ctx.registerReceiver(screen, f)
            ver = WallpaperPrefs.version(ctx)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            w = width; h = height
            val v = view ?: MonkeyView(ctx, this).also { view = it }
            v.layout(0, 0, w, h)
            v.onScreenChanged()
            drawFrame()
        }

        override fun onVisibilityChanged(vis: Boolean) {
            visible = vis
            if (vis) locked = keyguardLocked() || (asleep && locked)
            shown = wantShown()
            applyShown()
            main.removeCallbacks(watch)
            if (vis) main.postDelayed(watch, 2000)
        }

        private fun applyShown() {
            val v = view ?: return
            if (visible && shown) { v.goLive(); if (!asleep) v.setPaused(false) }
            else { v.setPaused(true); if (!shown) v.goDead() }
            drawFrame()
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (!shown) return
            lastTouch = SystemClock.uptimeMillis()
            view?.onTouchEvent(event)
        }

        /** Some launchers only send tap commands; turn them into a quick touch. */
        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean): Bundle? {
            if (action == WallpaperManager.COMMAND_TAP && shown && SystemClock.uptimeMillis() - lastTouch > 600) {
                val v = view
                if (v != null) {
                    val t = SystemClock.uptimeMillis()
                    val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0)
                    val up = MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), 0)
                    v.onTouchEvent(down); v.onTouchEvent(up)
                    down.recycle(); up.recycle()
                }
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            view?.setPaused(true)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            main.removeCallbacksAndMessages(null)
            runCatching { ctx.unregisterReceiver(screen) }
            view?.goDead(); view = null
            bg.release()
            super.onDestroy()
        }

        private fun drawFrame() {
            if (w <= 0 || h <= 0) return
            val holder = surfaceHolder
            var c: Canvas? = null
            try {
                c = if (android.os.Build.VERSION.SDK_INT >= 26) holder.lockHardwareCanvas() else holder.lockCanvas()
                if (c != null) {
                    bg.draw(c, w, h)
                    if (shown) view?.draw(c)
                }
            } catch (_: Throwable) {
            } finally {
                if (c != null) runCatching { holder.unlockCanvasAndPost(c) }
            }
        }

        // ---------------- OverlayHost ----------------
        override val wallpaper get() = true
        override fun screenWidth() = w
        override fun screenHeight() = h
        override fun placeWindow(x: Int, y: Int, w: Int, h: Int) {}
        override fun onFrame() { if (visible) drawFrame() }
        override fun openChat() {
            ctx.startActivity(Intent(ctx, ChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        override fun topInset(): Int = dimen("status_bar_height")
        override fun bottomInset(): Int = dimen("navigation_bar_height") + (16 * ctx.resources.displayMetrics.density).toInt()
        override fun spots(): List<RectF> = WallpaperPrefs.spots(ctx, w, h)

        @android.annotation.SuppressLint("DiscouragedApi", "InternalInsetResource")
        private fun dimen(name: String): Int {
            val id = ctx.resources.getIdentifier(name, "dimen", "android")
            return if (id > 0) ctx.resources.getDimensionPixelSize(id) else 0
        }
    }

    companion object {
        fun component(ctx: Context) = ComponentName(ctx, SettaiWallpaperService::class.java)

        /** Is our live wallpaper the current one? */
        fun isActive(ctx: Context): Boolean = runCatching {
            WallpaperManager.getInstance(ctx).wallpaperInfo?.serviceName == SettaiWallpaperService::class.java.name
        }.getOrDefault(false)

        /** Opens the system "set live wallpaper" screen pointing at Settai. */
        fun chooserIntent(ctx: Context): Intent =
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component(ctx))
    }
}
