package com.combo.settai.desktop

import java.io.File
import java.util.concurrent.TimeUnit

/** What Settai knows about the laptop (all reads are cheap; runs on a background thread). */
data class SysState(
    val cpuTemp: Float = Float.NaN,
    val ramUsedPct: Int = -1,
    val diskFreeGb: Float = -1f,
    val diskUsedPct: Int = -1,
    val battery: Int = -1,
    val charging: Boolean = false,
    val topProcess: String? = null,
    val topCpu: Float = 0f,
    val dnd: Boolean = false,
    val load: Float = 0f,
    /** cpuTemp is an estimate from CPU load (no sensor available, e.g. most Windows PCs). */
    val tempEstimated: Boolean = false,
)

object SystemInfo {
    fun read(): SysState = SysState(
        cpuTemp = cpuTemp(),
        ramUsedPct = ramUsedPct(),
        diskFreeGb = File(System.getProperty("user.home")).usableSpace / 1e9f,
        diskUsedPct = File("/").let { if (it.totalSpace > 0) (100 - it.usableSpace * 100 / it.totalSpace).toInt() else -1 },
        battery = battery().first,
        charging = battery().second,
        topProcess = topProcess()?.first,
        topCpu = topProcess()?.second ?: 0f,
        dnd = dnd(),
        load = runCatching { File("/proc/loadavg").readText().split(" ")[0].toFloat() }.getOrDefault(0f),
    )

    /** AMD k10temp (Tctl) first, then any CPU-ish hwmon, then thermal zones. */
    fun cpuTemp(): Float {
        val hw = File("/sys/class/hwmon").listFiles().orEmpty()
        val pref = listOf("k10temp", "zenpower", "coretemp", "cpu_thermal", "acpitz")
        for (name in pref) for (h in hw) {
            if (runCatching { File(h, "name").readText().trim() }.getOrNull() == name) {
                val t = runCatching { File(h, "temp1_input").readText().trim().toFloat() / 1000f }.getOrNull()
                if (t != null && t > 0) return t
            }
        }
        val zones = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }.orEmpty()
        return zones.mapNotNull { runCatching { File(it, "temp").readText().trim().toFloat() / 1000f }.getOrNull() }.maxOrNull() ?: Float.NaN
    }

    fun ramUsedPct(): Int = runCatching {
        val m = File("/proc/meminfo").readLines().associate { l -> l.substringBefore(':') to l.substringAfter(':').trim().split(" ")[0].toLong() }
        val total = m["MemTotal"] ?: return -1
        val avail = m["MemAvailable"] ?: return -1
        ((total - avail) * 100 / total).toInt()
    }.getOrDefault(-1)

    fun battery(): Pair<Int, Boolean> {
        val bats = File("/sys/class/power_supply").listFiles { f -> f.name.startsWith("BAT") }.orEmpty().sortedBy { it.name }
        val b = bats.firstOrNull() ?: return -1 to false
        val cap = runCatching { File(b, "capacity").readText().trim().toInt() }.getOrDefault(-1)
        val st = runCatching { File(b, "status").readText().trim() }.getOrDefault("")
        val ac = File("/sys/class/power_supply").listFiles { f -> f.name.startsWith("AC") || f.name.startsWith("ADP") }.orEmpty()
            .any { runCatching { File(it, "online").readText().trim() == "1" }.getOrDefault(false) }
        return cap to (st == "Charging" || st == "Full" || ac)
    }

    /** Busiest process right now (name, %CPU), ignoring ourselves. */
    fun topProcess(): Pair<String, Float>? = runCatching {
        val out = run("ps", "-eo", "comm=,pcpu=", "--sort=-pcpu") ?: return null
        out.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.map { l ->
            val i = l.lastIndexOf(' '); l.substring(0, i).trim() to l.substring(i + 1).toFloat()
        }.firstOrNull { it.first != "java" && it.first != "settai" && it.first != "Settai" && it.first != "ps" }
    }.getOrNull()

    /** GNOME "Do Not Disturb" = notification banners turned off. */
    fun dnd(): Boolean = run("gsettings", "get", "org.gnome.desktop.notifications", "show-banners")?.trim() == "false"

    /** GNOME screen lock (org.gnome.ScreenSaver.GetActive); null if unknown. */
    fun screenLocked(): Boolean? {
        val out = run("gdbus", "call", "--session", "--dest", "org.gnome.ScreenSaver", "--object-path", "/org/gnome/ScreenSaver",
            "--method", "org.gnome.ScreenSaver.GetActive") ?: return null
        return when { out.contains("true") -> true; out.contains("false") -> false; else -> null }
    }

    fun run(vararg cmd: String, timeoutSec: Long = 3): String? = runCatching {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) { p.destroyForcibly(); return null }
        if (p.exitValue() != 0) null else text
    }.getOrNull()
}
