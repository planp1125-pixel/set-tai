package com.combo.monkeycompanion.ui

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import com.combo.monkeycompanion.smash.SmashCaptureService
import com.combo.monkeycompanion.smash.SmashShot

/**
 * v10.13 Selfie Photobomb: one screen picture (with the companion in it) saved to Pictures/Set-Tai.
 * Uses Android's normal screen-capture consent; if refused or unavailable it just says so (no crash).
 */
class SelfieShotActivity : Activity() {
    private val REQ = 7713
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext
        runCatching {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            val i = if (Build.VERSION.SDK_INT >= 34) mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()) else mpm.createScreenCaptureIntent()
            @Suppress("DEPRECATION") startActivityForResult(i, REQ)
        }.onFailure { Toast.makeText(app, "Screen capture isn't available, skipping the photo 😅", Toast.LENGTH_SHORT).show(); finish() }
    }

    @Deprecated("one-off system dialog")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        val app = applicationContext
        if (requestCode == REQ && resultCode == RESULT_OK && data != null) {
            SmashShot.await { bmp -> save(app, bmp) }
            runCatching { SmashCaptureService.start(app, resultCode, data) }.onFailure { SmashShot.cancel(); Toast.makeText(app, "No photo this time 😅", Toast.LENGTH_SHORT).show() }
        } else Toast.makeText(app, "No photo this time 🙂", Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        fun save(ctx: android.content.Context, bmp: Bitmap?) {
            if (bmp == null) { Toast.makeText(ctx, "No photo this time 😅", Toast.LENGTH_SHORT).show(); return }
            val ok = runCatching {
                val cv = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "Set-Tai-photobomb-${System.currentTimeMillis()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Set-Tai")
                }
                val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: error("no uri")
                ctx.contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }.isSuccess
            Toast.makeText(ctx, if (ok) "📸 Saved to Pictures/Set-Tai!" else "Couldn't save the photo 😅", Toast.LENGTH_SHORT).show()
        }
    }
}
