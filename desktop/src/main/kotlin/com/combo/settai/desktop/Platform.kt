package com.combo.settai.desktop

import java.awt.Desktop
import java.io.File
import java.net.URI

/**
 * Everything OS-specific lives behind this interface: system readings, lock detection, autostart,
 * launching apps, window tweaks. Linux is [LinuxPlatform]; Windows is
 * com.combo.settai.desktop.win.WindowsPlatform (loaded by reflection so the Linux build doesn't need
 * OSHI/JNA on its classpath).
 */
interface Platform {
    val id: String
    /** How Settai describes where he lives (persona / body state). */
    val home: String
    val configDir: File
    /** Shown in Settings under Behaviour; null for none. */
    val notes: String?
    /** Settings label for where keys are stored. */
    val keyStoreNote: String
    /** X Shape click-through (Linux). On Windows the per-pixel-alpha layered window already lets clicks through. */
    val useWindowShape: Boolean
    /** Draw frames straight to the window (avoids X11 translucent-window flicker); Windows uses normal repaint. */
    val activeRender: Boolean
    val lockPollMs: Long get() = 2_500L
    /** Window can take keyboard focus from the start (Windows). On X11 it's switched on lazily (toggling remaps). */
    val alwaysFocusable: Boolean get() = false

    fun read(): SysState
    /** true/false, or null when unknown. */
    fun screenLocked(): Boolean?
    /** Called once after the frame is displayable and before it's shown. */
    fun prepareWindow(frame: java.awt.Window) {}
    fun setAutostart(on: Boolean)
    fun browse(url: String) { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url)) }
    /** (launch id, display name) of the installed app best matching [name]. */
    fun findApp(name: String): Pair<String, String>?
    fun launchApp(id: String)

    companion object {
        val isWindows = System.getProperty("os.name").orEmpty().lowercase().startsWith("windows")
        val current: Platform by lazy {
            if (isWindows) Class.forName("com.combo.settai.desktop.win.WindowsPlatform").getDeclaredConstructor().newInstance() as Platform
            else LinuxPlatform()
        }
    }
}

class LinuxPlatform : Platform {
    override val id = "linux"
    override val home = "Ubuntu laptop desktop"
    override val configDir = File(System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() } ?: (System.getProperty("user.home") + "/.config"), "settai")
    override val notes: String? = null
    override val keyStoreNote = "Keys are stored in ~/.config/settai/settai.properties (permissions 0600)."
    override val useWindowShape = true
    override val activeRender = true
    override fun read() = SystemInfo.read()

    // GNOME ScreenSaver over D-Bus, else logind's LockedHint, else unknown.
    private var source = 0
    private val sid = System.getenv("XDG_SESSION_ID")
    override val lockPollMs get() = if (source == 2) 30_000L else 2_500L
    override fun screenLocked(): Boolean? = when (source) {
        0 -> SystemInfo.screenLocked() ?: run { source = 1; null }
        1 -> if (sid == null) { source = 2; null } else SystemInfo.run("loginctl", "show-session", sid, "-p", "LockedHint", "--value")?.trim()
            ?.let { it == "yes" } ?: run { source = 2; null }
        else -> null
    }

    override fun setAutostart(on: Boolean) {
        val f = File(System.getProperty("user.home"), ".config/autostart/settai.desktop")
        if (on) {
            val exe = System.getProperty("jpackage.app-path") ?: "/opt/settai/bin/Settai"
            f.parentFile.mkdirs()
            f.writeText("[Desktop Entry]\nType=Application\nName=Settai\nComment=Your cheeky monkey desktop companion\nExec=$exe\nIcon=settai\nX-GNOME-Autostart-enabled=true\nX-GNOME-Autostart-Delay=5\n")
        } else if (f.exists()) f.delete()
    }

    override fun browse(url: String) {
        if (SystemInfo.run("xdg-open", url) == null) super.browse(url)
    }

    private fun dirs() = listOf("/usr/share/applications", "/usr/local/share/applications", "/var/lib/snapd/desktop/applications",
        "/var/lib/flatpak/exports/share/applications", System.getProperty("user.home") + "/.local/share/applications").map(::File)

    override fun findApp(name: String): Pair<String, String>? {
        val q = name.lowercase().removeSuffix(".desktop")
        val all = dirs().flatMap { d -> d.listFiles { f -> f.name.endsWith(".desktop") }.orEmpty().toList() }.mapNotNull { f ->
            val lines = runCatching { f.readLines() }.getOrNull() ?: return@mapNotNull null
            if (lines.any { it.trim() == "NoDisplay=true" }) return@mapNotNull null
            val n = lines.firstOrNull { it.startsWith("Name=") }?.substringAfter("=") ?: return@mapNotNull null
            Triple(f.name.removeSuffix(".desktop"), n, lines.firstOrNull { it.startsWith("Keywords=") }.orEmpty().lowercase())
        }
        return (all.firstOrNull { it.second.lowercase() == q } ?: all.firstOrNull { it.first.lowercase().endsWith(q) }
            ?: all.firstOrNull { it.second.lowercase().contains(q) } ?: all.firstOrNull { it.third.contains(q) })?.let { it.first to it.second }
    }

    override fun launchApp(id: String) { ProcessBuilder("gtk-launch", id).redirectErrorStream(true).start() }
}
