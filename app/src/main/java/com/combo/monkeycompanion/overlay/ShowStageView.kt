package com.combo.monkeycompanion.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Build
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.combo.monkeycompanion.engine.show.EpisodeRenderer
import com.combo.monkeycompanion.rig.BillaRenderer
import com.combo.monkeycompanion.rig.ChittuRenderer

/**
 * v10.13.1: ONE full-screen, touch-through stage window for the Billa-vs-Chittu chase. The two companion windows
 * stay still (and invisible) during the show; this view draws both characters at the display's frame rate from a
 * pure function of show time, so there is no per-frame window move, no fighting systems and no jitter.
 * v10.21 Show 2.0: plays a cartoon [com.combo.monkeycompanion.engine.show.Episode] with its sounds and buzzes.
 */
@SuppressLint("ViewConstructor")
class ShowStageView(context: Context, private val scale: Float) : View(context), Choreographer.FrameCallback {
    private val stage = EpisodeRenderer(BillaRenderer(), ChittuRenderer())
    private val d = resources.displayMetrics.density
    private var on = false
    private var lastT = -1f
    /** Keeps the floor above the navigation bar. */
    private val floorPad: Float = run {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val nav = if (id > 0) runCatching { resources.getDimensionPixelSize(id) }.getOrDefault(0) else 0
        nav + 14f * d
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!on) return
        val ep = ChaseShowHub.episode
        val t = ChaseShowHub.t()
        if (ep == null || t > ep.total) { ChaseShowHub.finish(context); return }
        // sounds + buzzes that fell between the last frame and this one
        if (lastT >= 0f) for (b in ep.beeps) if (b.at > lastT && b.at <= t) beep(b)
        lastT = t
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun beep(b: com.combo.monkeycompanion.engine.show.Beep) {
        com.combo.monkeycompanion.audio.Sfx.byName(b.sfx)?.let { runCatching { com.combo.monkeycompanion.audio.Sfx.play(context, it, b.vol, b.pitch) } }
        if (b.buzzMs > 0) runCatching {
            val am = context.getSystemService(android.media.AudioManager::class.java)
            if (am != null && am.ringerMode == android.media.AudioManager.RINGER_MODE_SILENT) return
            val v = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
                else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            v?.vibrate(android.os.VibrationEffect.createOneShot(b.buzzMs.toLong().coerceIn(10L, 120L), 90))
        }
    }

    /** v10.21.1: any tap stops the show and gives the screen back. */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        if (e.actionMasked == android.view.MotionEvent.ACTION_UP) ChaseShowHub.finish(context)
        return true
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); on = true; Choreographer.getInstance().postFrameCallback(this) }
    override fun onDetachedFromWindow() { on = false; Choreographer.getInstance().removeFrameCallback(this); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val ep = ChaseShowHub.episode ?: return
        stage.draw(canvas, ep, ChaseShowHub.t(), width.toFloat(), height.toFloat(), floorPad, d)
    }

    companion object {
        private var cur: ShowStageView? = null
        val showing get() = cur != null

        fun open(ctx: Context, scale: Float): Boolean {
            if (cur != null) return true
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
            val v = ShowStageView(ctx, scale)
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                // v10.21.1: TOUCHABLE on purpose. Android 12+ forces a touch-through (NOT_TOUCHABLE) overlay to 80 %
                // opacity, which made Billa and Chittu look see-through. The show now catches touches: tap = stop.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.START; title = "SettaiShowStage" }
            if (runCatching { wm.addView(v, lp) }.isFailure) return false
            cur = v
            return true
        }

        fun close(ctx: Context?) {
            val v = cur ?: return
            cur = null
            val wm = (ctx ?: v.context).getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            runCatching { wm?.removeView(v) }
        }
    }
}
