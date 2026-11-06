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
import com.combo.monkeycompanion.engine.script.ScriptStageRenderer

/** v10.32: full-screen stage window for scripted shows (same window type / rules as [ShowStageView]). Tap = stop. */
@SuppressLint("ViewConstructor")
class ScriptStageView(context: Context) : View(context), Choreographer.FrameCallback {
    private var on = false
    private var renderer: ScriptStageRenderer? = null

    override fun doFrame(frameTimeNanos: Long) {
        if (!on) return
        val p = ScriptShowHub.player ?: run { ScriptShowHub.finish(context); return }
        p.advanceTo(ScriptShowHub.t())
        if (p.done) { ScriptShowHub.finish(context); return }
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        if (e.actionMasked == android.view.MotionEvent.ACTION_UP) ScriptShowHub.finish(context)
        return true
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); on = true; Choreographer.getInstance().postFrameCallback(this) }
    override fun onDetachedFromWindow() { on = false; Choreographer.getInstance().removeFrameCallback(this); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val p = ScriptShowHub.player ?: return
        val r = renderer?.takeIf { it.isFor(p) } ?: ScriptStageRenderer(p).also { renderer = it }
        r.draw(canvas, width.toFloat(), height.toFloat())
    }

    companion object {
        private var cur: ScriptStageView? = null
        val showing get() = cur != null
        fun open(ctx: Context): Boolean {
            if (cur != null) return true
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
            val v = ScriptStageView(ctx)
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.START; title = "SettaiScriptStage" }
            if (runCatching { wm.addView(v, lp) }.isFailure) return false
            cur = v; return true
        }
        fun close(ctx: Context?) {
            val v = cur ?: return
            cur = null
            runCatching { ((ctx ?: v.context).getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.removeView(v) }
        }
    }
}
