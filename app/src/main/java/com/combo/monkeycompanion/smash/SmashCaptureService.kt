package com.combo.monkeycompanion.smash

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import com.combo.monkeycompanion.R

/**
 * v10.5 Screen Smash: takes ONE picture of the screen with the user's screen-capture permission, hands it to
 * [SmashShot] (memory only, never saved or sent) and stops recording straight away.
 */
class SmashCaptureService : Service() {
    private val ui = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var tries = 0
    private var w = 0; private var h = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        @Suppress("DEPRECATION") val data: Intent? = intent?.getParcelableExtra(EXTRA_DATA)
        createChannel()
        ServiceCompat.startForeground(this, NOTIF_ID, notification(),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0)
        if (data == null) { deliver(null); return START_NOT_STICKY }
        runCatching {
            val mp = getSystemService(MediaProjectionManager::class.java).getMediaProjection(code, data) ?: error("no projection")
            projection = mp
            mp.registerCallback(object : MediaProjection.Callback() { override fun onStop() {} }, ui)
            val (sw, sh, dpi) = SmashShot.screenSize(this)
            w = sw; h = sh
            val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            reader = r
            display = mp.createVirtualDisplay("settai-smash", w, h, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, ui)
            // Let the permission dialog finish closing before taking the picture.
            ui.postDelayed(::grab, 450)
        }.onFailure { deliver(null) }
        return START_NOT_STICKY
    }

    private fun grab() {
        val img = runCatching { reader?.acquireLatestImage() }.getOrNull()
        if (img == null) {
            if (++tries < 20) ui.postDelayed(::grab, 100) else deliver(null)
            return
        }
        val bmp = runCatching {
            val plane = img.planes[0]
            val px = plane.pixelStride; val row = plane.rowStride
            val full = Bitmap.createBitmap(row / px, h, Bitmap.Config.ARGB_8888)
            full.copyPixelsFromBuffer(plane.buffer)
            val out = if (full.width == w) full else Bitmap.createBitmap(full, 0, 0, w, h).also { full.recycle() }
            out
        }.getOrNull()
        img.close()
        deliver(bmp)
    }

    private fun deliver(bmp: Bitmap?) {
        runCatching { display?.release() }; display = null
        runCatching { projection?.stop() }; projection = null
        runCatching { reader?.close() }; reader = null
        SmashShot.deliver(bmp)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        runCatching { display?.release() }; runCatching { projection?.stop() }; runCatching { reader?.close() }
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Screen Smash", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(): Notification = Notification.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_monkey)
        .setContentTitle("Screen Smash")
        .setContentText("Taking one picture of the screen for the game…")
        .build()

    companion object {
        private const val CHANNEL = "smash"
        private const val NOTIF_ID = 77
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"

        fun start(ctx: Context, resultCode: Int, data: Intent) {
            val i = Intent(ctx, SmashCaptureService::class.java).putExtra(EXTRA_CODE, resultCode).putExtra(EXTRA_DATA, data)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }
}
