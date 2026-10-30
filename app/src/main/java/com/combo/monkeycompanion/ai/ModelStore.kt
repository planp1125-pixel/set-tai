package com.combo.monkeycompanion.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** A downloadable on-device model. */
class ModelEntry(
    val title: String,
    val blurb: String,
    val repo: String,
    val file: String,
    val approxMb: Int,
    val gated: Boolean,
) {
    val url get() = "https://huggingface.co/$repo/resolve/main/$file"
    val pageUrl get() = "https://huggingface.co/$repo"
}

/** Model catalog, downloads (Android DownloadManager, survives app restarts) and file import. */
object ModelStore {
    val CATALOG = listOf(
        ModelEntry(
            "Gemma 4 E2B (best, ~2.6 GB)",
            "Google's newest small Gemma. Smartest offline option; needs ~4 GB free RAM. No login needed.",
            "litert-community/gemma-4-E2B-it-litert-lm", "gemma-4-E2B-it.litertlm", 2588, false
        ),
        ModelEntry(
            "Gemma 3 1B (~0.6 GB, needs Hugging Face token)",
            "Good and light. Gated: accept the Gemma license on its Hugging Face page, then paste a read token below.",
            "litert-community/Gemma3-1B-IT", "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm", 584, true
        ),
        ModelEntry(
            "Qwen3 0.6B (tiny & fast, ~0.35 GB)",
            "Very small and quick, but a bit silly. No login needed.",
            "litert-community/Qwen3-0.6B-int4", "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm", 347, false
        ),
    )

    fun modelsDir(ctx: Context): File = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "models").apply { mkdirs() }

    fun freeBytes(ctx: Context): Long = StatFs(modelsDir(ctx).path).availableBytes

    fun installed(ctx: Context): List<File> =
        modelsDir(ctx).listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }?.sortedBy { it.name } ?: emptyList()

    /** Result of checking a URL before downloading. */
    class Probe(val directUrl: String, val sizeBytes: Long)

    /**
     * Resolve the Hugging Face URL (with optional token) to the signed CDN URL so the
     * DownloadManager doesn't need to send the token itself. Blocking: call off main thread.
     */
    fun probe(url: String, token: String): Probe {
        val c = OkHttpClient.Builder().followRedirects(false).connectTimeout(20, TimeUnit.SECONDS).build()
        var current = url
        var size = -1L
        repeat(5) {
            val rb = Request.Builder().url(current).head()
            if (token.isNotBlank() && Uri.parse(current).host?.endsWith("huggingface.co") == true) rb.header("Authorization", "Bearer $token")
            c.newCall(rb.build()).execute().use { r ->
                r.header("X-Linked-Size")?.toLongOrNull()?.let { size = it }
                when (r.code) {
                    in 300..399 -> {
                        val loc = r.header("Location") ?: throw BrainException("Redirect without location")
                        current = r.request.url.resolve(loc)?.toString() ?: loc
                        // Signed CDN link reached: that's what we hand to the DownloadManager
                        // (CDNs often reject HEAD on GET-signed URLs, so stop here).
                        if (Uri.parse(current).host?.endsWith("huggingface.co") != true) return Probe(current, size)
                    }
                    in 200..299 -> {
                        if (size < 0) size = r.header("Content-Length")?.toLongOrNull() ?: -1
                        return Probe(current, size)
                    }
                    401, 403 -> throw BrainException(
                        if (token.isBlank()) "This model is gated. Accept its license on Hugging Face and paste a token."
                        else "Hugging Face refused the token (HTTP ${r.code}). Did you accept the model's license with that account?"
                    )
                    404 -> throw BrainException("File not found (HTTP 404). Check the link.")
                    else -> throw BrainException("Download check failed: HTTP ${r.code}")
                }
            }
        }
        throw BrainException("Too many redirects")
    }

    /** Starts the download; returns the target file. */
    fun startDownload(ctx: Context, probe: Probe, fileName: String, title: String): File {
        val dm = ctx.getSystemService(DownloadManager::class.java)
        val target = File(modelsDir(ctx), fileName)
        if (target.exists()) target.delete()
        val req = DownloadManager.Request(Uri.parse(probe.directUrl))
            .setTitle("Settai's brain: $title")
            .setDescription("Downloading the on-device AI model")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationUri(Uri.fromFile(target))
        val id = dm.enqueue(req)
        val s = AppSettings(ctx)
        s.downloadId = id
        s.downloadTarget = target.path
        return target
    }

    class Progress(val status: Int, val done: Long, val total: Long, val reason: Int)

    fun progress(ctx: Context): Progress? {
        val s = AppSettings(ctx)
        val id = s.downloadId
        if (id < 0) return null
        val dm = ctx.getSystemService(DownloadManager::class.java)
        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) return null
            return Progress(
                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
            )
        }
    }

    fun isDownloading(ctx: Context): Boolean {
        val p = progress(ctx) ?: return false
        return p.status == DownloadManager.STATUS_RUNNING || p.status == DownloadManager.STATUS_PENDING || p.status == DownloadManager.STATUS_PAUSED
    }

    fun cancelDownload(ctx: Context) {
        val s = AppSettings(ctx)
        if (s.downloadId >= 0) ctx.getSystemService(DownloadManager::class.java).remove(s.downloadId)
        s.downloadId = -1
    }

    /** Forget the finished download record (after it succeeded or failed). */
    fun clearDownloadRecord(ctx: Context) { AppSettings(ctx).downloadId = -1 }
}
