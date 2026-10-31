package com.combo.monkeycompanion

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.powers.LaunchGate
import com.combo.monkeycompanion.powers.SettaiClock

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        com.combo.monkeycompanion.ai.AppSettings(this).applyCharacter()
        ChatHub.init(this)
        val st = com.combo.monkeycompanion.ai.AppSettings(this)
        com.combo.monkeycompanion.brain.PowerPrefs.store = { id -> st.enabledPowers(id) }   // v10.17 per-character power picker
        runCatching { SettaiClock.channel(this) }
        // Our own screens in front = we may open other apps (Android background-launch rules).
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) { LaunchGate.foregroundActivities++ }
            override fun onActivityPaused(a: Activity) { LaunchGate.foregroundActivities = maxOf(0, LaunchGate.foregroundActivities - 1) }
            override fun onActivityCreated(a: Activity, b: Bundle?) {
                // v10.17 red / black / white theme: near-black status + navigation bars with light icons on every screen
                runCatching {
                    @Suppress("DEPRECATION")
                    a.window.statusBarColor = com.combo.monkeycompanion.theme.Th.INK
                    @Suppress("DEPRECATION")
                    a.window.navigationBarColor = com.combo.monkeycompanion.theme.Th.INK
                    @Suppress("DEPRECATION")
                    a.window.decorView.systemUiVisibility = a.window.decorView.systemUiVisibility and
                        (android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR).inv()
                }
            }
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }
}
