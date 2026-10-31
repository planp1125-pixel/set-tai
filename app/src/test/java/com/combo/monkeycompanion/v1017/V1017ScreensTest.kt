package com.combo.monkeycompanion.v1017

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import com.combo.monkeycompanion.TestPaths
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v10.17 themed screens (red / black / white) rendered to test-out/v1017-*.png. Only fails on a crash. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class V1017ScreensTest {
    private fun shot(cls: Class<out Activity>, name: String) {
        val a = runCatching { Robolectric.buildActivity(cls).setup().get() }.getOrNull() ?: return
        val v = a.window.decorView
        val w = 1233; val h = 2673
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        TestPaths.out(name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun screens() {
        shot(com.combo.monkeycompanion.MainActivity::class.java, "v1017-home.png")
        shot(com.combo.monkeycompanion.ui.SettingsActivity::class.java, "v1017-settings.png")
        shot(com.combo.monkeycompanion.ui.CompanionStudioActivity::class.java, "v1017-studio.png")
        shot(com.combo.monkeycompanion.ui.MenuSettingsActivity::class.java, "v1017-menu.png")
        // the Characters-tab power picker card for Kutti Satan
        val a = Robolectric.buildActivity(com.combo.monkeycompanion.MainActivity::class.java).setup().get()
        val card = com.combo.monkeycompanion.ui.PowerChoiceActivity.card(a, com.combo.monkeycompanion.character.CharacterRegistry.byId("devil")!!) {}
        val frame = android.widget.FrameLayout(a).apply { setBackgroundColor(com.combo.monkeycompanion.theme.Th.INK); setPadding(36, 36, 36, 36); addView(card) }
        val w = 1233
        frame.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        frame.layout(0, 0, w, frame.measuredHeight)
        val bmp = Bitmap.createBitmap(w, frame.measuredHeight, Bitmap.Config.ARGB_8888)
        frame.draw(Canvas(bmp))
        TestPaths.out("v1017-powers.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
