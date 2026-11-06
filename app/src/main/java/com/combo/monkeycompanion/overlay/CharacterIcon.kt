package com.combo.monkeycompanion.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.drawable.IconCompat
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.character.Character
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose

/**
 * v10.31: status-bar / always-on-display icon of the CURRENT character, drawn by the same procedural renderer
 * as the overlay (no bitmap assets). Android tints small icons by their alpha, so the AOD shows the character's
 * silhouette (Kutti Satan's horns + tail) instead of the old static monkey vector. Cached per character id.
 */
object CharacterIcon {
    private var cacheId: String? = null
    private var cacheSize = 0
    private var cache: Bitmap? = null

    /** Which character the icon shows right now (tests). */
    fun currentId(): String = CharacterRegistry.current.id

    @Synchronized fun bitmap(ctx: Context, who: Character = CharacterRegistry.current, sizePx: Int = defaultSize(ctx)): Bitmap {
        cache?.let { if (cacheId == who.id && cacheSize == sizePx && !it.isRecycled) return it }
        val bmp = render(who, sizePx)
        cache = bmp; cacheId = who.id; cacheSize = sizePx
        return bmp
    }

    /** The small icon for notifications (falls back to the vector if drawing ever fails). */
    fun small(ctx: Context): IconCompat? = runCatching { IconCompat.createWithBitmap(bitmap(ctx)) }.getOrNull()

    /** v10.31: every Set-Tai notification uses the current character's icon (vector monkey only as a fallback). */
    fun applyTo(b: androidx.core.app.NotificationCompat.Builder, ctx: Context): androidx.core.app.NotificationCompat.Builder {
        val icon = small(ctx)
        return if (icon != null) b.setSmallIcon(icon) else b.setSmallIcon(com.combo.monkeycompanion.R.drawable.ic_stat_monkey)
    }

    fun defaultSize(ctx: Context): Int = (24 * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(24) * 2

    fun render(who: Character, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rig = MonkeyRig(); val p = Pose()
        val wear = CharacterRegistry.wear(who)
        who.fill(p, MonkeyState.IDLE, 0.4f, 0.4f, AnimContext(), wear)
        p.shadow = 0f
        // Fit the whole body (~180 rig units tall incl. horns / halo) into the square.
        rig.layout(p, size / 2f, size * 0.97f, size / 200f, 1f)
        who.newRenderer().also { it.wear = wear }.draw(c, rig, p, 0.4f)
        return bmp
    }
}

