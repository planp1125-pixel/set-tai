package com.combo.monkeycompanion.overlay

import android.content.Context
import com.combo.monkeycompanion.engine.script.ScriptError
import com.combo.monkeycompanion.engine.script.ShowScript
import java.io.File

/**
 * v10.34: the user's own show scripts, saved in app-private storage (`filesDir/user-shows/<slug>.show`).
 * Bundled shows stay in `assets/shows/`. A show is only saved if it parses, so a bad script is reported
 * (with its line number) instead of being stored.
 */
object UserShows {
    /** One playable show: bundled (asset path) or the user's (file). */
    class Entry(val title: String, val asset: String? = null, val file: File? = null) {
        val bundled get() = asset != null
        fun text(ctx: Context): String = asset?.let { a -> ctx.assets.open(a).bufferedReader().use { it.readText() } } ?: file!!.readText()
    }

    fun dir(ctx: Context): File = File(ctx.filesDir, "user-shows").apply { mkdirs() }

    /** File-safe name from a title: "Wave hello!" → "wave-hello". */
    fun slug(title: String): String =
        title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(48).ifEmpty { "my-show" }

    /** Checks [text]; null = OK, else a plain message with the line number. */
    fun check(text: String): String? = try { validate(text); null } catch (e: ScriptError) { message(e) }

    /** Parses and checks every cast id exists (throws [ScriptError]). */
    fun validate(text: String): ShowScript { val sc = ShowScript.parse(text); com.combo.monkeycompanion.engine.script.ScriptPlayer(sc); return sc }

    /** v10.39.1: plain words with a hint (still says "line N"). */
    fun message(e: ScriptError): String = com.combo.monkeycompanion.engine.script.ShowHelp.friendly(e)

    /** Things that would silently do nothing (unknown face / action / prop, say without quotes). */
    fun warnings(text: String): List<String> = com.combo.monkeycompanion.engine.script.ShowHelp.lint(text).map { "Line ${it.line}: ${it.text}" }

    /** Parses and saves [text] (same title = overwrite). Throws [ScriptError] if it doesn't parse. */
    fun save(ctx: Context, text: String): Entry {
        val sc = validate(text)
        val f = File(dir(ctx), slug(sc.title) + ".show")
        f.writeText(text)
        return Entry(sc.title, file = f)
    }

    fun delete(e: Entry): Boolean = e.file?.delete() == true

    fun bundled(ctx: Context): List<Entry> =
        (runCatching { ctx.assets.list("shows")?.toList() }.getOrNull() ?: emptyList()).filter { it.endsWith(".show") }.sorted().map { n ->
            val path = "shows/$n"
            val title = runCatching { ShowScript.parse(ctx.assets.open(path).bufferedReader().use { it.readText() }).title }.getOrDefault(n.removeSuffix(".show"))
            Entry(title, asset = path)
        }

    fun user(ctx: Context): List<Entry> = (dir(ctx).listFiles { f -> f.name.endsWith(".show") } ?: emptyArray()).sortedBy { it.name }.map { f ->
        Entry(runCatching { ShowScript.parse(f.readText()).title }.getOrDefault(f.name.removeSuffix(".show")), file = f)
    }

    fun all(ctx: Context): List<Entry> = bundled(ctx) + user(ctx)

    /** Fetch a script from an https URL (user-initiated). GitHub "blob" links become raw links. Call off the main thread. */
    fun fetch(url: String): String {
        var u = url.trim()
        Regex("^https://github\\.com/([^/]+)/([^/]+)/blob/(.+)$").find(u)?.let { m -> u = "https://raw.githubusercontent.com/${m.groupValues[1]}/${m.groupValues[2]}/${m.groupValues[3]}" }
        require(u.startsWith("https://")) { "Use an https:// link" }
        val c = java.net.URL(u).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000; c.instanceFollowRedirects = true
        try {
            if (c.responseCode !in 200..299) throw java.io.IOException("The link answered ${c.responseCode}")
            val bytes = c.inputStream.use { it.readBytes() }
            require(bytes.size < 200_000) { "That file is too big for a show script" }
            return String(bytes, Charsets.UTF_8)
        } finally { c.disconnect() }
    }

    /** Normalises a fetched GitHub link (tests). */
    fun rawUrl(url: String): String = Regex("^https://github\\.com/([^/]+)/([^/]+)/blob/(.+)$").find(url.trim())?.let { m ->
        "https://raw.githubusercontent.com/${m.groupValues[1]}/${m.groupValues[2]}/${m.groupValues[3]}" } ?: url.trim()
}
