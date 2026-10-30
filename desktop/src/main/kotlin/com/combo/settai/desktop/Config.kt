package com.combo.settai.desktop

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.Properties

/** Settings in ~/.config/settai/settai.properties (mode 0600) or %APPDATA%\\Settai\\settai.properties on Windows. */
class Config {
    val dir: File = Platform.current.configDir
    private val file = File(dir, "settai.properties")
    private val p = Properties()

    init {
        dir.mkdirs()
        runCatching { Files.setPosixFilePermissions(dir.toPath(), PosixFilePermissions.fromString("rwx------")) }
        if (file.exists()) runCatching { file.inputStream().use { p.load(it) } }
    }

    @Synchronized fun get(k: String, def: String = ""): String = p.getProperty(k, def)
    @Synchronized fun set(k: String, v: String) { p.setProperty(k, v) }
    fun bool(k: String, def: Boolean) = get(k, def.toString()).toBoolean()
    fun float(k: String, def: Float) = get(k, def.toString()).toFloatOrNull() ?: def
    fun int(k: String, def: Int) = get(k, def.toString()).toIntOrNull() ?: def

    @Synchronized fun save() {
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "settai.properties.tmp")
            tmp.writeText("")
            runCatching { Files.setPosixFilePermissions(tmp.toPath(), PosixFilePermissions.fromString("rw-------")) }
            tmp.outputStream().use { p.store(it, "Settai desktop settings (contains API keys; keep private)") }
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            runCatching { Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------")) }
        }
    }

    // ---- typed accessors ----
    var brain: String get() = get("brain", "ollama"); set(v) = set("brain", v)          // ollama | online | none
    var ollamaUrl: String get() = get("ollama.url", "http://localhost:11434"); set(v) = set("ollama.url", v)
    var ollamaModel: String get() = get("ollama.model", ""); set(v) = set("ollama.model", v)
    var provider: String get() = get("online.provider", "OPENAI"); set(v) = set("online.provider", v)
    fun key(provider: String) = get("online.key.$provider")
    fun setKey(provider: String, v: String) = set("online.key.$provider", v)
    fun model(provider: String) = get("online.model.$provider", Providers.byId(provider).defaultModel)
    fun setModel(provider: String, v: String) = set("online.model.$provider", v)
    var customUrl: String get() = get("online.customUrl", ""); set(v) = set("online.customUrl", v)
    var roam: Boolean get() = bool("roam", true); set(v) = set("roam", v.toString())
    var quips: Boolean get() = bool("quips", true); set(v) = set("quips", v.toString())
    var scale: Float get() = float("scale", 1.25f).coerceIn(0.7f, 2.5f); set(v) = set("scale", v.toString())
    var autostart: Boolean get() = bool("autostart", false); set(v) = set("autostart", v.toString())

    companion object { const val RECOMMENDED_MODEL = "gemma3:4b" }
}

class Provider(val id: String, val label: String, val defaultModel: String, val baseUrl: String, val keyHint: String)

object Providers {
    val all = listOf(
        Provider("OPENAI", "OpenAI", "gpt-5.4-mini", "https://api.openai.com/v1", "platform.openai.com → API keys"),
        Provider("GEMINI", "Google Gemini", "gemini-3.8-flash", "https://generativelanguage.googleapis.com/v1beta/openai", "aistudio.google.com → Get API key"),
        Provider("XAI", "xAI Grok", "grok-4.3", "https://api.x.ai/v1", "console.x.ai → API keys"),
        Provider("ANTHROPIC", "Anthropic Claude", "claude-haiku-5-5", "https://api.anthropic.com/v1", "console.anthropic.com → API keys"),
        Provider("CUSTOM", "Custom (OpenAI-compatible)", "", "", "Your server's key (leave blank if none)"),
    )
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all[0]
}
