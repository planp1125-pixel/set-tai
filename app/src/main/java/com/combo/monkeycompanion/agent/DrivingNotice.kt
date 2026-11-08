package com.combo.monkeycompanion.agent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.combo.monkeycompanion.R

/** "Settai is driving your phone" ongoing notification with a Stop button, while the screen agent works. */
object DrivingNotice {
    private const val CH = "settai_agent"; private const val ID = 4711

    fun update(ctx: Context, driving: Boolean) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (!driving) { nm.cancel(ID); return }
        runCatching {
            nm.createNotificationChannel(NotificationChannel(CH, "Set-Tai screen driving", NotificationManager.IMPORTANCE_LOW))
            val stop = PendingIntent.getBroadcast(ctx, 7, Intent(ctx, StopAgentReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            nm.notify(ID, NotificationCompat.Builder(ctx, CH).let { com.combo.monkeycompanion.overlay.CharacterIcon.applyTo(it, ctx) }
                .setContentTitle("🛞 ${com.combo.monkeycompanion.character.CharacterRegistry.current.name} is driving your phone").setContentText("Tap Stop (or tap ${com.combo.monkeycompanion.character.CharacterRegistry.current.name}) to take the wheel back.")
                .setOngoing(true).setOnlyAlertOnce(true).addAction(0, "Stop", stop).setContentIntent(stop).build())
        }
    }
}

class StopAgentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ScreenAgent.stop()
        com.combo.monkeycompanion.ai.ChatHub.stopDriving()
    }
}
