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
import com.combo.monkeycompanion.engine.show.StageRenderer
import com.combo.monkeycompanion.rig.BillaRenderer
import com.combo.monkeycompanion.rig.ChittuRenderer

/**
 * v10.13.1: ONE full-screen, touch-through stage window for the Billa-vs-Chittu chase. The two companion windows
 * stay still (and invisible) during the show; this view draws both characters at the display's frame rate from a
 * pure function of show time, so there is no per-frame window move, no fighting systems and no jitter.
 */
@SuppressLint("ViewConstructor")
class ShowStageView(context: Context, private val scale: Float) : View(context), Choreographer.FrameCallback {
    private val stage = StageRenderer(BillaRenderer(), ChittuRenderer())
    private val d = resources.displayMetrics.density
    private var on = false

    override fun doFrame(frameTimeNanos: Long) {
        if (!on) return
        val run = ChaseShowHub.run
        if (run == null || ChaseShowHub.t() > run.total) { ChaseShowHub.finish(context); return }
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); on = true; Choreographer.getInstance().postFrameCallback(this) }
    override fun onDetachedFromWindow() { on = false; Choreographer.getInstance().removeFrameCallback(this); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val run = ChaseShowHub.run ?: return
        stage.draw(canvas, run, ChaseShowHub.t(), width.toFloat(), scale, d)
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
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
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
