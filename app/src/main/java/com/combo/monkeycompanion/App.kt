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
        runCatching { SettaiClock.channel(this) }
        // Our own screens in front = we may open other apps (Android background-launch rules).
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) { LaunchGate.foregroundActivities++ }
            override fun onActivityPaused(a: Activity) { LaunchGate.foregroundActivities = maxOf(0, LaunchGate.foregroundActivities - 1) }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }
}
