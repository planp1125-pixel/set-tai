package com.combo.monkeycompanion.ui

import android.app.Activity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
class RingActivity : androidx.activity.ComponentActivity() {
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
        val title = intent.getStringExtra("title") ?: "⏰"
        val rawLine = intent.getStringExtra("line").orEmpty()
        // v10.38.17: Material 3 ring screen; the dancing character is the real overlay view.
        setContent {
            androidx.compose.material3.MaterialTheme(colorScheme = SettaiDesignTokens.scheme(true)) {
                androidx.compose.material3.Surface(color = androidx.compose.ui.graphics.Color(0xFF2E5C34), modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Column(
                        androidx.compose.ui.Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                    ) {
                        androidx.compose.material3.Text(title, fontSize = 30.sp, color = androidx.compose.ui.graphics.Color.White)
                        androidx.compose.material3.Text((if (rawLine.isNotBlank()) "$rawLine\n" else "") + "🥁 Tap-tap-thump! ${com.combo.monkeycompanion.character.CharacterRegistry.current.name} is drumming on the glass from below to wake you up!",
                            fontSize = 18.sp, color = androidx.compose.ui.graphics.Color(0xFFFFF0D2),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxWidth().weight(1f), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { ctx -> MonkeyView(ctx, null).also { m -> m.post { runCatching { m.onAction(com.combo.monkeycompanion.ai.BodyAction.DANCE) } } } },
                                modifier = androidx.compose.ui.Modifier.size(220.dp, 240.dp)
                            )
                        }
                        androidx.compose.material3.Button(onClick = { stopAndClose() },
                            modifier = androidx.compose.ui.Modifier.fillMaxWidth().height(64.dp)) {
                            androidx.compose.material3.Text("I'm awake! (Stop drumming)", fontSize = 20.sp)
                        }
                    }
                }
            }
        }
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
