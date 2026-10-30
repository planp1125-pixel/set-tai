package com.combo.settai.desktop

import android.graphics.RectF
import javax.swing.Timer

/** The window Settai lives in (same contract as the Android overlay host). Coordinates are work-area px. */
interface OverlayHost {
    fun screenWidth(): Int
    fun screenHeight(): Int
    fun placeWindow(x: Int, y: Int, w: Int, h: Int)
    fun openChat() {}
    fun topInset(): Int = 0
    fun bottomInset(): Int = 0
    fun setWindowFocusable(on: Boolean) {}
    val wallpaper: Boolean get() = false
    fun spots(): List<RectF> = emptyList()
    fun onFrame() {}
}

/** Minimal MotionEvent so the touch code reads the same as on Android (mouse → touch). */
class MotionEvent(val actionMasked: Int, val x: Float, val y: Float, val rawX: Float, val rawY: Float, val eventTime: Long) {
    companion object {
        const val ACTION_DOWN = 0; const val ACTION_UP = 1; const val ACTION_MOVE = 2; const val ACTION_CANCEL = 3; const val ACTION_OUTSIDE = 4
    }
}

/** android.os.Handler stand-in on the Swing thread. */
class UiHandler {
    private val timers = HashMap<Runnable, Timer>()
    fun post(r: Runnable) = postDelayed(r, 0)
    fun postDelayed(r: Runnable, ms: Long): Boolean {
        timers.remove(r)?.stop()
        val t = Timer(ms.toInt().coerceAtLeast(0)) { timers.remove(r); r.run() }
        t.isRepeats = false; timers[r] = t; t.start()
        return true
    }
    fun removeCallbacks(r: Runnable) { timers.remove(r)?.stop() }
    fun removeCallbacksAndMessages(@Suppress("UNUSED_PARAMETER") token: Any?) { timers.values.forEach { it.stop() }; timers.clear() }
}

/** SharedPreferences stand-in backed by [Config] (key prefix "state."). */
class Prefs(private val cfg: Config) {
    private fun k(key: String) = "state.$key"
    fun getBoolean(key: String, def: Boolean) = cfg.bool(k(key), def)
    fun getInt(key: String, def: Int) = cfg.int(k(key), def)
    fun getFloat(key: String, def: Float) = cfg.float(k(key), def)
    fun getLong(key: String, def: Long) = cfg.get(k(key), def.toString()).toLongOrNull() ?: def
    fun edit() = Editor()
    inner class Editor {
        private val m = LinkedHashMap<String, String>()
        fun putBoolean(key: String, v: Boolean) = apply { m[k(key)] = v.toString() }
        fun putInt(key: String, v: Int) = apply { m[k(key)] = v.toString() }
        fun putFloat(key: String, v: Float) = apply { m[k(key)] = v.toString() }
        fun putLong(key: String, v: Long) = apply { m[k(key)] = v.toString() }
        fun apply() { m.forEach { (a, b) -> cfg.set(a, b) }; cfg.save() }
    }
}
