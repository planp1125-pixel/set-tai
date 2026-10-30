package com.combo.monkeycompanion.powers

import android.app.ActivityOptions
import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.combo.monkeycompanion.agent.NotifItem
import com.combo.monkeycompanion.agent.NotifStore
import com.combo.monkeycompanion.ai.AppSettings

/**
 * Lets Settai see the active media session ("what's playing?") and, only when the v4 Notification
 * reader power is switched on, keeps the recent notifications IN MEMORY so it can summarise them,
 * show OTPs (to you only), open a notification's exact [Notification.contentIntent] when you tap a
 * row on the "What You Missed" card, and reply / dismiss after you say Yes. Nothing is written to disk.
 */
class SettaiNotificationListener : NotificationListenerService() {
    private fun on() = AppSettings(this).groupEnabled(CapGroup.NOTIFS)

    override fun onListenerConnected() {
        instance = this; NotifStore.connected = true
        if (on()) runCatching { NotifStore.reset(activeNotifications.mapNotNull { item(it) }) }
    }
    override fun onListenerDisconnected() { if (instance === this) instance = null; NotifStore.connected = false }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!on()) return
        item(sbn)?.let {
            NotifStore.put(it)
            com.combo.monkeycompanion.agent.DailyUtilityEngine.onNotificationArrived(this, it)
            com.combo.monkeycompanion.agent.Proactive.onNotif(it)
        }
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification) { NotifStore.remove(sbn.key) }

    private fun item(sbn: StatusBarNotification): NotifItem? {
        if (sbn.packageName == packageName || sbn.isOngoing) return null
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val e = n.extras
        val title = (e.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) ?: e.getCharSequence(Notification.EXTRA_TITLE))?.toString().orEmpty()
        val text = (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        val app = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
        return NotifItem(sbn.key, sbn.packageName, app, title, text, sbn.postTime, replyAction(n) != null)
    }

    private fun replyAction(n: Notification): Notification.Action? =
        n.actions?.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }

    /** Sends [text] through the notification's own reply box (RemoteInput). Only after the user's Yes. */
    fun reply(key: String, text: String): Boolean {
        val sbn = runCatching { activeNotifications.firstOrNull { it.key == key } }.getOrNull() ?: return false
        val a = replyAction(sbn.notification) ?: return false
        val inputs = a.remoteInputs ?: return false
        val i = Intent(); val b = Bundle()
        inputs.forEach { b.putCharSequence(it.resultKey, text) }
        RemoteInput.addResultsToIntent(inputs, i, b)
        return runCatching { a.actionIntent.send(this, 0, i); true }.getOrDefault(false)
    }

    /**
     * Fires the notification's own [Notification.contentIntent] (just like tapping the notification in
     * the system shade), with Android 14/15 (`targetSdk = 35`) background activity start permission bundle.
     * Falls back to launching [pkg] if the notification was already cleared or has no `contentIntent`.
     */
    fun open(key: String, pkg: String): Boolean {
        val sbn = runCatching {
            activeNotifications?.firstOrNull { it.key == key }
                ?: activeNotifications?.firstOrNull { it.packageName == pkg }
        }.getOrNull()
        val ci = sbn?.notification?.contentIntent
        if (ci != null) {
            val sent = runCatching {
                val opts = ActivityOptions.makeBasic().apply {
                    if (Build.VERSION.SDK_INT >= 34) {
                        setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    }
                }.toBundle()
                ci.send(this, 0, null, null, null, null, opts)
                true
            }.getOrElse {
                runCatching { ci.send(); true }.getOrDefault(false)
            }
            if (sent) {
                NotifStore.remove(sbn.key)
                if ((sbn.notification.flags and Notification.FLAG_AUTO_CANCEL) != 0) {
                    runCatching { cancelNotification(sbn.key) }
                }
                ActionLog.add(this, "Opened notification via contentIntent", "${sbn.packageName} ($key)")
                return true
            }
        }
        return launchPackageFallback(this, key, pkg)
    }

    /** Fresh list from the status bar (also refreshes the in-memory cache). */
    fun current(): List<NotifItem> = runCatching { activeNotifications.mapNotNull { item(it) }.also { NotifStore.reset(it) } }.getOrDefault(NotifStore.all())
        .sortedByDescending { it.time }

    fun dismiss(key: String?) {
        if (key == null) {
            NotifStore.reset(emptyList())
            runCatching { cancelAllNotifications() }
        } else {
            NotifStore.remove(key)
            runCatching { cancelNotification(key) }
        }
    }

    companion object {
        @Volatile var instance: SettaiNotificationListener? = null; private set
        /** Test hook recording the last (key, pkg) opened from the What You Missed card. */
        @Volatile var lastOpenedForTest: Pair<String, String>? = null

        fun isAccessGranted(ctx: Context): Boolean = NotifStore.connected || runCatching {
            val flat = ComponentName(ctx, SettaiNotificationListener::class.java).flattenToString()
            (Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: "")
                .split(':').any { it.equals(flat, ignoreCase = true) }
        }.getOrDefault(false)

        fun openNotification(ctx: Context, key: String, pkg: String): Boolean {
            lastOpenedForTest = key to pkg
            val svc = instance
            if (svc != null) return svc.open(key, pkg)
            return launchPackageFallback(ctx, key, pkg)
        }

        private fun launchPackageFallback(ctx: Context, key: String, pkg: String): Boolean {
            NotifStore.remove(key)
            if (pkg.isBlank()) return false
            val launched = runCatching {
                val launch = ctx.packageManager.getLaunchIntentForPackage(pkg)
                    ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg)
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(launch)
                true
            }.getOrDefault(false)
            ActionLog.add(ctx, "Opened notification app fallback", "$pkg ($key, launched=$launched)")
            return launched
        }
    }
}
