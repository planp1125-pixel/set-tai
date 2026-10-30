package com.combo.monkeycompanion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/** adb shell am broadcast -a com.combo.monkeycompanion.RENDER_PREVIEW -n com.combo.monkeycompanion/.PreviewRenderReceiver */
class PreviewRenderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val bmp = PreviewSheet.render()
        val out = File(context.filesDir, "monkey-preview.png")
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i("MonkeyPreview", "wrote ${out.absolutePath}")
        resultData = out.absolutePath
    }
}
