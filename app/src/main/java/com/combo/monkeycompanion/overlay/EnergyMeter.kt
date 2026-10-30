package com.combo.monkeycompanion.overlay

import android.os.Process
import android.os.SystemClock

/**
 * Settai's own energy bookkeeping (v3.2).
 *
 * - Process CPU: [Process.getElapsedCpuTime] (user+system CPU of the whole app process: overlay,
 *   render thread, AI networking...) against wall time.
 * - Render loop: main-thread CPU spent in the frame callback + onDraw, measured with
 *   [android.os.Debug.threadCpuTimeNanos], and the number of frames drawn.
 *
 * Battery is an estimate: a phone CPU core that is 100% busy at a moderate clock draws very roughly
 * 150-250 mA; we use 200 mA per busy core. Display compositing of the overlay is not included.
 */
object EnergyMeter {
    /** mA drawn by one fully busy core (rough mid-range phone figure). */
    const val MA_PER_CORE = 200f

    @Volatile var frames = 0L; private set
    @Volatile var frameCpuNs = 0L; private set
    /** What the loop is doing right now, for the settings line ("30 fps active", "stopped (resting)"...). */
    @Volatile var mode = "not running"

    fun addFrame(cpuNs: Long) { frames++; frameCpuNs += cpuNs }
    fun addDraw(cpuNs: Long) { frameCpuNs += cpuNs }

    data class Sample(val wallMs: Long, val procCpuMs: Long, val frames: Long, val frameCpuNs: Long)

    fun sample() = Sample(SystemClock.elapsedRealtime(), Process.getElapsedCpuTime(), frames, frameCpuNs)

    /** Average since the process started. */
    fun sinceStart(): Sample = Sample(Process.getStartElapsedRealtime(), 0L, 0L, 0L)

    /** CPU % of one core between two samples (whole process). */
    fun cpuPercent(a: Sample, b: Sample): Float {
        val wall = (b.wallMs - a.wallMs).coerceAtLeast(1L)
        return 100f * (b.procCpuMs - a.procCpuMs) / wall
    }

    /** Render-loop CPU in ms per second between two samples. */
    fun renderMsPerSec(a: Sample, b: Sample): Float {
        val wall = (b.wallMs - a.wallMs).coerceAtLeast(1L)
        return (b.frameCpuNs - a.frameCpuNs) / 1e6f * 1000f / wall
    }

    fun fps(a: Sample, b: Sample): Float {
        val wall = (b.wallMs - a.wallMs).coerceAtLeast(1L)
        return (b.frames - a.frames) * 1000f / wall
    }

    /** Estimated mAh per hour for a CPU percentage of one core. */
    fun mAhPerHour(cpuPercent: Float) = cpuPercent / 100f * MA_PER_CORE

    /** One human line for Settings. */
    fun describe(window: Pair<Sample, Sample>?, now: Sample = sample()): String {
        val start = sinceStart()
        val avg = cpuPercent(start, now)
        val mins = (now.wallMs - start.wallMs) / 60000f
        return buildString {
            append("Now: ").append(mode).append('\n')
            if (window != null) {
                val (a, b) = window
                val cpu = cpuPercent(a, b)
                append("Last %.0f s: %.1f%% of one CPU core (app process), render %.1f fps using %.1f ms CPU per second → ≈ %.0f mAh/hour"
                    .format((b.wallMs - a.wallMs) / 1000f, cpu, fps(a, b), renderMsPerSec(a, b), mAhPerHour(cpu)))
                append('\n')
            }
            append("Since start (%.0f min): %.2f%% CPU average → ≈ %.1f mAh/hour".format(mins, avg, mAhPerHour(avg)))
        }
    }
}
