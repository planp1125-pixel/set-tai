package com.combo.monkeycompanion.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.combo.monkeycompanion.ai.Attachment
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.overlay.MonkeyOverlayService
import java.io.ByteArrayOutputStream

/**
 * v10.48 📎: an invisible screen that opens Android's own photo picker or file picker (no storage
 * permission needed: the user picks one file and only that file is shared with us), turns the pick into
 * an [Attachment] and gives it to [ChatHub], then reopens the chat.
 */
class AttachActivity : ComponentActivity() {
    private val photo = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { done(it) }
    private val doc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { done(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return   // the picker is already open (rotation)
        runCatching {
            if (intent.getStringExtra(EXTRA_KIND) == KIND_DOC) doc.launch(DOC_TYPES)
            else photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }.onFailure { finishBack(null) }
    }

    private fun done(uri: Uri?) {
        if (uri == null) { finishBack(null); return }
        // read it now, while we hold the permission for this one file
        Thread {
            val r = runCatching { read(uri) }.getOrElse { Result(null, "I couldn't open that file. 😕") }
            runOnUiThread {
                r.attachment?.let { ChatHub.attach(it) }
                finishBack(r.problem)
            }
        }.start()
    }

    private class Result(val attachment: Attachment?, val problem: String? = null)

    private fun read(uri: Uri): Result {
        val cr = contentResolver
        val mime = cr.getType(uri) ?: "application/octet-stream"
        var name = "file"; var size = -1L
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { i -> c.getString(i)?.let { name = it } }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { i -> size = c.getLong(i) }
            }
        }
        name = name.take(80)
        return when (Attachment.kindFor(mime, name)) {
            Attachment.Kind.IMAGE -> {
                val jpeg = shrink(uri) ?: return Result(null, "I couldn't read that photo. 😕")
                Result(Attachment(Attachment.Kind.IMAGE, name, "image/jpeg", "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)))
            }
            Attachment.Kind.PDF -> {
                if (size > Attachment.MAX_PDF_BYTES) return Result(null, "That PDF is too big (max 10 MB). 📄")
                val bytes = cr.openInputStream(uri)?.use { readCapped(it, Attachment.MAX_PDF_BYTES) } ?: return Result(null, "I couldn't read that PDF. 😕")
                    ?: return Result(null, "That PDF is too big (max 10 MB). 📄")
                Result(Attachment(Attachment.Kind.PDF, name, "application/pdf", "data:application/pdf;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)))
            }
            Attachment.Kind.TEXT -> {
                val bytes = cr.openInputStream(uri)?.use { readCapped(it, Attachment.MAX_TEXT_CHARS * 4, cut = true) } ?: return Result(null, "I couldn't read that file. 😕")
                val text = String(bytes, Charsets.UTF_8).take(Attachment.MAX_TEXT_CHARS)
                Result(Attachment(Attachment.Kind.TEXT, name, mime, text = text))
            }
            null -> Result(null, "I can read photos, PDFs and text files. That one is a ${mime.substringAfter('/').take(20)}. 📎")
        }
    }

    /** At most [max] bytes; null when bigger (unless [cut], which keeps the first [max]). */
    private fun readCapped(input: java.io.InputStream, max: Int, cut: Boolean = false): ByteArray? {
        val out = ByteArrayOutputStream(); val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf); if (n < 0) break
            if (out.size() + n > max) { if (cut) { out.write(buf, 0, max - out.size()); break } else return null }
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** The photo as a JPEG with its long side at most [Attachment.MAX_IMAGE_SIDE]. */
    private fun shrink(uri: Uri): ByteArray? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= Attachment.MAX_IMAGE_SIDE) sample *= 2
        val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val rot = exifRotation(uri)
        val scale = Attachment.MAX_IMAGE_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
        val m = android.graphics.Matrix().apply { if (scale < 1f) postScale(scale, scale); if (rot != 0) postRotate(rot.toFloat()) }
        val fixed = if (scale < 1f || rot != 0) Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true) else bmp
        return ByteArrayOutputStream().also { fixed.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }

    private fun exifRotation(uri: Uri): Int = runCatching {
        contentResolver.openInputStream(uri)?.use { s ->
            when (android.media.ExifInterface(s).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    }.getOrDefault(0)

    /** Close this invisible screen and bring the chat back (with [problem] said by the character). */
    private fun finishBack(problem: String?) {
        if (problem != null) ChatHub.announce(problem)
        if (intent.getBooleanExtra(EXTRA_REOPEN, false) && MonkeyOverlayService.running) runCatching {
            startService(android.content.Intent(this, MonkeyOverlayService::class.java).setAction(MonkeyOverlayService.ACTION_CHAT))
        }
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_KIND = "kind"
        /** Reopen the floating chat afterwards (it closes so the picker isn't hidden under it). */
        const val EXTRA_REOPEN = "reopen"
        const val KIND_PHOTO = "photo"
        const val KIND_DOC = "doc"
        val DOC_TYPES = arrayOf("application/pdf", "text/*", "application/json", "image/*")
    }
}
