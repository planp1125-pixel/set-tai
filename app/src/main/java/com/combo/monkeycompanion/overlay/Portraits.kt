package com.combo.monkeycompanion.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.combo.monkeycompanion.brain.MonkeyState
import com.combo.monkeycompanion.rig.AnimContext
import com.combo.monkeycompanion.rig.MonkeyRig
import com.combo.monkeycompanion.rig.Pose

/** Still pictures of Settai (for notifications). */
object Portraits {
    fun sleeping(@Suppress("UNUSED_PARAMETER") ctx: Context, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rig = MonkeyRig(); val p = Pose()
        val who = com.combo.monkeycompanion.character.CharacterRegistry.current
        val wear = com.combo.monkeycompanion.character.CharacterRegistry.wear(who)
        who.fill(p, MonkeyState.SLEEP, 1.2f, 1.2f, AnimContext(), wear)
        rig.layout(p, size / 2f, size * 0.93f - (if (p.bodyY < -5f) p.bodyY * size / 230f else 0f), size / 230f, 1f)
        who.newRenderer().also { it.wear = wear }.draw(c, rig, p, 1.2f)
        return bmp
    }
}
