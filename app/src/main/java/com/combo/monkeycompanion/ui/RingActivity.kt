package com.combo.monkeycompanion.ui

import android.app.Activity
import android.app.KeyguardManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.combo.monkeycompanion.overlay.MonkeyView
import com.combo.monkeycompanion.powers.SettaiClock

/**
 * Full-screen "time's up" screen (shows over the lock screen) with Settai bouncing and a Stop button.
 *
 * v3.2 screen-timeout fix: this is the only place Settai ever keeps the screen on. Before, the
 * activity stayed alive with FLAG_KEEP_SCREEN_ON + turnScreenOn when the alarm was stopped from the
 * notification (or never stopped at all), so the screen never timed out. Now it: finishes whenever
 * the alarm is stopped anywhere, auto-stops after [AUTO_STOP_MS], drops KEEP_SCREEN_ON as soon as
 * it's not in the foreground, and turns the screen on only for the first show.
 */
class RingActivity : Activity() {
    private var id = -1
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private val autoStop = Runnable { SettaiClock.stop(this, id); finish() }
    private var shownOnce = false

    companion object {
        /** Ring screens up right now (to close them when the alarm is stopped from the notification). */
        private val open = java.util.Collections.synchronizedSet(HashSet<RingActivity>())
        /** Stop ringing on its own after 3 minutes, like the stock Clock app's silence-after. */
        const val AUTO_STOP_MS = 3 * 60_000L

        /** Called by [SettaiClock.stop]: close the ring screen for [id] (or all, if id < 0). */
        fun closeFor(id: Int) {
            val list = synchronized(open) { open.toList() }
            for (a in list) if (id < 0 || a.id == id || a.id < 0) a.runOnUiThread { a.finish() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        id = intent.getIntExtra(SettaiClock.EXTRA_ID, -1)
        open.add(this)
        ui.postDelayed(autoStop, AUTO_STOP_MS)
        val d = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(com.combo.monkeycompanion.theme.Th.rgb(46, 92, 52)); setPadding((24 * d).toInt(), (48 * d).toInt(), (24 * d).toInt(), (48 * d).toInt())
        }
        root.addView(TextView(this).apply {
            text = intent.getStringExtra("title") ?: "⏰"; textSize = 30f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        })
        val rawLine = intent.getStringExtra("line").orEmpty()
        root.addView(TextView(this).apply {
            text = (if (rawLine.isNotBlank()) "$rawLine\n" else "") + "🥁 *Tap-tap-thump!* Settai is drumming on the glass from below to wake you up!"
            textSize = 18f; setTextColor(com.combo.monkeycompanion.theme.Th.rgb(255, 240, 210)); gravity = Gravity.CENTER
            setPadding(0, (12 * d).toInt(), 0, (12 * d).toInt())
        })
        val stage = FrameLayout(this)
        val monkey = MonkeyView(this, null)
        stage.addView(monkey, FrameLayout.LayoutParams((220 * d).toInt(), (240 * d).toInt(), Gravity.CENTER))
        root.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply {
            text = "I'm awake! (Stop drumming)"; textSize = 20f
            setOnClickListener { stopAndClose() }
        }, LinearLayout.LayoutParams(-1, (64 * d).toInt()))
        setContentView(root)
        monkey.post { runCatching { monkey.onAction(com.combo.monkeycompanion.ai.BodyAction.DANCE) } }
    }

    override fun onResume() {
        super.onResume()
        // Keep the screen on only while this ring screen is actually in front of the user.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (shownOnce && Build.VERSION.SDK_INT >= 27) setTurnScreenOn(false)
        shownOnce = true
    }

    override fun onStop() {
        super.onStop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 27) setTurnScreenOn(false)
        else @Suppress("DEPRECATION") window.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        if (!isChangingConfigurations) finish()   // no lingering ring screen in the background
    }

    override fun onDestroy() {
        ui.removeCallbacks(autoStop)
        open.remove(this)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    private fun stopAndClose() {
        SettaiClock.stop(this, id)
        if (Build.VERSION.SDK_INT >= 26) getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)
        finish()
    }
}
