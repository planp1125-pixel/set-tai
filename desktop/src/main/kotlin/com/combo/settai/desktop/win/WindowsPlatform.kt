package com.combo.settai.desktop.win

import com.combo.settai.desktop.Platform
import com.combo.settai.desktop.SysState
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.win32.StdCallLibrary
import oshi.SystemInfo as Oshi
import oshi.software.os.OSProcess
import java.awt.Desktop
import java.io.File

/** Bits of user32 that jna-platform doesn't wrap the way we need. */
@Suppress("FunctionName")
private interface User32Ex : StdCallLibrary {
    fun OpenInputDesktop(dwFlags: Int, fInherit: Boolean, dwDesiredAccess: Int): Pointer?
    fun SwitchDesktop(hDesktop: Pointer): Boolean
    fun CloseDesktop(hDesktop: Pointer): Boolean
    fun GetWindowLongPtrW(hWnd: Pointer, nIndex: Int): Pointer?
    fun SetWindowLongPtrW(hWnd: Pointer, nIndex: Int, dwNewLong: Pointer?): Pointer?
    fun SetWindowPos(hWnd: Pointer, after: Pointer?, x: Int, y: Int, cx: Int, cy: Int, flags: Int): Boolean

    companion object { val I: User32Ex by lazy { Native.load("user32", User32Ex::class.java) } }
}

/**
 * Windows 10/11: OSHI for CPU/RAM/processes/battery/sensors, JNA for the lock state, the tool-window
 * style (no taskbar button) and autostart (HKCU Run key).
 */
class WindowsPlatform : Platform {
    override val id = "windows"
    override val home = "Windows PC desktop"
    override val configDir = File(System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: (System.getProperty("user.home") + "\\AppData\\Roaming"), "Settai")
    override val keyStoreNote = "Keys are stored in %APPDATA%\\Settai\\settai.properties (inside your Windows profile)."
    override val useWindowShape = false   // per-pixel alpha: clicks already pass through transparent pixels
    override val activeRender = false
    override val alwaysFocusable = true

    private val si by lazy { Oshi() }
    private val hal by lazy { si.hardware }
    private var prevTicks: LongArray? = null
    private var prevProcs: Map<Int, OSProcess> = emptyMap()
    private var loadEma = -1f
    @Volatile private var sensorOk: Boolean? = null

    override val notes: String
        get() = buildList {
            when (sensorOk) {
                false -> add("No CPU temperature sensor is readable on this PC, so his 'heat' (sweating) is estimated from CPU load.")
                true -> add("CPU temperature comes from the PC's sensor.")
                null -> {}
            }
            add("Focus Assist / Do Not Disturb isn't detected on Windows.")
        }.joinToString(" ")

    override fun read(): SysState {
        val cpu = hal.processor
        val ticks = cpu.systemCpuLoadTicks
        val load = prevTicks?.let { (cpu.getSystemCpuLoadBetweenTicks(it) * 100).toFloat() } ?: 0f
        prevTicks = ticks
        loadEma = if (loadEma < 0) load else loadEma * 0.65f + load * 0.35f

        val sensor = runCatching { hal.sensors.cpuTemperature.toFloat() }.getOrDefault(Float.NaN)
        val real = !sensor.isNaN() && sensor in 15f..115f
        if (sensorOk != true) sensorOk = real
        // No sensor: 36°C idle -> ~96°C when pinned at 100% for half a minute or so.
        val temp = if (real) sensor else 36f + 0.6f * loadEma

        val mem = hal.memory
        val ram = if (mem.total > 0) ((mem.total - mem.available) * 100 / mem.total).toInt() else -1
        val home = File(System.getProperty("user.home"))
        val sysDrive = File((System.getenv("SystemDrive") ?: "C:") + "\\")

        val ps = runCatching { hal.powerSources.firstOrNull { it.remainingCapacityPercent > 0 && it.designCapacity >= 0 } }.getOrNull()
        val battery = ps?.let { (it.remainingCapacityPercent * 100).toInt().coerceIn(0, 100) } ?: -1
        val charging = ps?.let { it.isCharging || it.isPowerOnLine } ?: false

        val top = runCatching { topProcess() }.getOrNull()
        return SysState(
            cpuTemp = temp, tempEstimated = !real,
            ramUsedPct = ram,
            diskFreeGb = home.usableSpace / 1e9f,
            diskUsedPct = if (sysDrive.totalSpace > 0) (100 - sysDrive.usableSpace * 100 / sysDrive.totalSpace).toInt() else -1,
            battery = if (hal.powerSources.isEmpty()) -1 else battery, charging = charging,
            topProcess = top?.first, topCpu = top?.second ?: 0f,
            dnd = false, load = load,
        )
    }

    /** Busiest process since the last reading (name, % of one core), ignoring ourselves and system idle. */
    private fun topProcess(): Pair<String, Float>? {
        val os = si.operatingSystem
        val me = os.processId
        val now = os.processes.associateBy { it.processID }
        val prev = prevProcs; prevProcs = now
        if (prev.isEmpty()) return null
        return now.values.asSequence()
            .filter { it.processID != me && it.processID > 4 && it.name !in setOf("Idle", "System", "Settai", "javaw", "java") }
            .map { it.name.removeSuffix(".exe") to (it.getProcessCpuLoadBetweenTicks(prev[it.processID]) * 100).toFloat() }
            .maxByOrNull { it.second }
    }

    /** Locked (or on the secure desktop): the input desktop can't be opened/switched to by us. */
    override fun screenLocked(): Boolean? = runCatching {
        val u = User32Ex.I
        val h = u.OpenInputDesktop(0, false, 0x0100 /* DESKTOP_SWITCHDESKTOP */) ?: return@runCatching true
        val ok = u.SwitchDesktop(h)
        u.CloseDesktop(h)
        !ok
    }.getOrNull()

    /** Tool window: no taskbar button, no Alt+Tab entry. Keeps Java's WS_EX_LAYERED. */
    override fun prepareWindow(frame: java.awt.Window) {
        val hwnd = Native.getComponentPointer(frame) ?: return
        val u = User32Ex.I
        val GWL_EXSTYLE = -20
        val WS_EX_TOOLWINDOW = 0x80L; val WS_EX_APPWINDOW = 0x40000L; val WS_EX_NOACTIVATE = 0x08000000L
        val cur = Pointer.nativeValue(u.GetWindowLongPtrW(hwnd, GWL_EXSTYLE))
        var ex = (cur or WS_EX_TOOLWINDOW) and WS_EX_APPWINDOW.inv()
        if (System.getenv("SETTAI_NOACTIVATE") != null) ex = ex or WS_EX_NOACTIVATE
        u.SetWindowLongPtrW(hwnd, GWL_EXSTYLE, Pointer(ex))
        // SWP_NOSIZE|SWP_NOMOVE|SWP_NOZORDER|SWP_NOACTIVATE|SWP_FRAMECHANGED
        u.SetWindowPos(hwnd, null, 0, 0, 0, 0, 0x0001 or 0x0002 or 0x0004 or 0x0010 or 0x0020)
    }

    private val runKey = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    override fun setAutostart(on: Boolean) {
        if (on) {
            val exe = System.getProperty("settai.exe")
                ?: File(System.getProperty("java.home")).parentFile?.let { File(it, "Settai.exe") }?.takeIf { it.exists() }?.absolutePath
                ?: return
            Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, runKey, "Settai", "\"$exe\"")
        } else if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, runKey, "Settai")) {
            Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, runKey, "Settai")
        }
    }

    /** Start-menu shortcuts (per-user and all-users). */
    private fun shortcuts(): List<File> = listOfNotNull(System.getenv("APPDATA"), System.getenv("ProgramData"))
        .map { File(it, "Microsoft\\Windows\\Start Menu\\Programs") }
        .flatMap { d -> d.walkTopDown().maxDepth(4).filter { it.isFile && (it.name.endsWith(".lnk") || it.name.endsWith(".url")) }.toList() }
        .filter { !it.name.lowercase().contains("uninstall") }

    override fun findApp(name: String): Pair<String, String>? {
        val q = name.lowercase().trim()
        val all = shortcuts().map { it.absolutePath to it.nameWithoutExtension }
        return all.firstOrNull { it.second.lowercase() == q } ?: all.firstOrNull { it.second.lowercase().startsWith(q) }
            ?: all.firstOrNull { it.second.lowercase().contains(q) }
    }

    override fun launchApp(id: String) { Desktop.getDesktop().open(File(id)) }
}
