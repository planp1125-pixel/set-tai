package com.combo.monkeycompanion.wallpaper

import android.content.Context
import android.graphics.RectF
import org.json.JSONArray

/** Wallpaper settings: background and climbing spots (stored as screen fractions, per orientation). */
object WallpaperPrefs {
    const val BG_JUNGLE = 0
    const val BG_COLOR = 1
    const val BG_IMAGE = 2

    private fun p(ctx: Context) = ctx.getSharedPreferences("settai_wallpaper", Context.MODE_PRIVATE)

    fun bgMode(ctx: Context) = p(ctx).getInt("bgMode", BG_JUNGLE)
    fun bgColor(ctx: Context) = p(ctx).getInt("bgColor", 0xFF2E5E4E.toInt())
    fun bgImage(ctx: Context): String? = p(ctx).getString("bgImage", null)
    /** Bumped on every change so engines know to rebuild. */
    fun version(ctx: Context) = p(ctx).getInt("ver", 0)

    fun setBackground(ctx: Context, mode: Int, color: Int? = null, image: String? = null) {
        val e = p(ctx).edit().putInt("bgMode", mode)
        if (color != null) e.putInt("bgColor", color)
        if (image != null) e.putString("bgImage", image)
        e.putInt("ver", version(ctx) + 1).apply()
    }

    fun guideImage(ctx: Context, landscape: Boolean): String? = p(ctx).getString(if (landscape) "guide_l" else "guide_p", null)
    fun setGuideImage(ctx: Context, landscape: Boolean, uri: String?) {
        p(ctx).edit().putString(if (landscape) "guide_l" else "guide_p", uri).apply()
    }

    /** Spots as fractions (left, top, right, bottom of the full screen). */
    fun spotFractions(ctx: Context, landscape: Boolean): List<RectF> {
        val raw = p(ctx).getString(if (landscape) "spots_l" else "spots_p", null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val r = a.getJSONArray(i)
                RectF(r.getDouble(0).toFloat(), r.getDouble(1).toFloat(), r.getDouble(2).toFloat(), r.getDouble(3).toFloat())
            }
        }.getOrDefault(emptyList())
    }

    fun saveSpotFractions(ctx: Context, landscape: Boolean, spots: List<RectF>) {
        val a = JSONArray()
        for (r in spots) a.put(JSONArray().put(r.left.toDouble()).put(r.top.toDouble()).put(r.right.toDouble()).put(r.bottom.toDouble()))
        p(ctx).edit().putString(if (landscape) "spots_l" else "spots_p", a.toString()).putInt("ver", version(ctx) + 1).apply()
    }

    /** Spots in px for a w x h screen. */
    fun spots(ctx: Context, w: Int, h: Int): List<RectF> =
        spotFractions(ctx, w > h).map { RectF(it.left * w, it.top * h, it.right * w, it.bottom * h) }
}
