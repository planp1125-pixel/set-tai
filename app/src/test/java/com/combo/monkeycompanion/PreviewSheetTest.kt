package com.combo.monkeycompanion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.combo.monkeycompanion.ai.ChatHub
import com.combo.monkeycompanion.ai.ChatMsg
import com.combo.monkeycompanion.ui.ChatPanelView
import com.combo.monkeycompanion.ui.SettingsActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Renders preview images with real Android graphics on the JVM (Robolectric native graphics). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class PreviewSheetTest {
    private val outDir = File(System.getProperty("preview.out") ?: "build/monkey-preview.png").parentFile!!

    private fun save(bmp: Bitmap, name: String) {
        outDir.mkdirs()
        val f = File(outDir, name)
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("PREVIEW_WRITTEN ${f.absolutePath}")
    }

    @Test
    fun renderPreview() {
        save(PreviewSheet.render(), "monkey-preview.png")
        save(PreviewSheet.renderV2(), "monkey-v2-poses.png")
        save(PreviewSheet.renderEdges(), "monkey-v21-edges.png")
    }

    @Test
    fun renderV42() {
        val full = PreviewV42.render()
        save(full, "settai-v42-preview-full.png")
        save(Bitmap.createScaledBitmap(full, full.width * 2 / 3, full.height * 2 / 3, true), "settai-v42-preview.png")
    }

    @Test
    fun renderV44() {
        val act = Robolectric.buildActivity(com.combo.monkeycompanion.ui.MenuSettingsActivity::class.java).setup().get()
        val scroll = act.window.decorView.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
        val col = scroll.getChildAt(0)
        col.measure(View.MeasureSpec.makeMeasureSpec(1020, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val h = col.measuredHeight.coerceIn(1800, 2700)
        val shot = snapshot(scroll, 1020, h)
        save(shot, "settai-v44-customise.png")
        val full = PreviewV44.render(shot)
        save(full, "settai-v44-menu-preview-full.png")
        save(Bitmap.createScaledBitmap(full, full.width * 2 / 3, full.height * 2 / 3, true), "settai-v44-menu-preview.png")
    }

    @Test
    fun renderV5() {
        val full = PreviewV5.render()
        save(full, "settai-v5-preview.png")
    }

    @Test
    fun renderV6() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        ChatHub.init(ctx)
        com.combo.monkeycompanion.mind.MindHub.init(ctx)
        com.combo.monkeycompanion.mind.MindHub.resetForTest()
        val m = com.combo.monkeycompanion.mind.MindHub.mindFor("monkey")!!
        val now = System.currentTimeMillis()
        m.drives.energy = 0.78f; m.drives.curiosity = 0.84f; m.drives.social = 0.48f
        m.drives.boredom = 0.22f; m.drives.contentment = 0.72f; m.drives.annoyance = 0.05f
        m.diary.add(com.combo.monkeycompanion.mind.Diary.Kind.FESTIVAL, "Deepavali", now - 2 * 86_400_000L)
        m.diary.add(com.combo.monkeycompanion.mind.Diary.Kind.SHAKEN, "YouTube", now - 86_400_000L)
        m.diary.add(com.combo.monkeycompanion.mind.Diary.Kind.HABIT, "morning stretch", now - 5 * 3600_000L)
        m.diary.add(com.combo.monkeycompanion.mind.Diary.Kind.EXPLORED, "Swiggy", now - 3 * 3600_000L)
        m.diary.add(com.combo.monkeycompanion.mind.Diary.Kind.GAME_WON, "banana catch", now - 2 * 3600_000L)
        m.diary.add(com.combo.monkeycompanion.mind.Diary.Kind.CHAT_LINE, "you eat more bananas than my uncle 😂", now - 3600_000L)
        m.diary.add(com.combo.monkeycompanion.mind.Diary.Kind.PETTED, "Home screen", now - 1200_000L)
        com.combo.monkeycompanion.mind.MindHub.flush()
        val act = Robolectric.buildActivity(com.combo.monkeycompanion.ui.DiaryActivity::class.java).setup().get()
        val scroll = act.window.decorView.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
        val col = scroll.getChildAt(0)
        col.measure(View.MeasureSpec.makeMeasureSpec(1020, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val h = col.measuredHeight.coerceIn(1800, 2700)
        val shot = snapshot(scroll, 1020, h)
        val full = PreviewV6.render(shot)
        save(full, "settai-v6-preview.png")
        com.combo.monkeycompanion.TestPaths.out("settai-v6-preview.png").outputStream().use { full.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun renderV7() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        ChatHub.init(ctx)
        com.combo.monkeycompanion.mind.MindHub.init(ctx)
        val act = Robolectric.buildActivity(com.combo.monkeycompanion.ui.SkillsActivity::class.java).setup().get()
        val scroll = act.window.decorView.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
        val col = scroll.getChildAt(0)
        col.measure(View.MeasureSpec.makeMeasureSpec(920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val h = col.measuredHeight.coerceIn(1800, 2500)
        val shot = snapshot(scroll, 920, h)
        val full = PreviewV7.render(shot)
        save(full, "settai-v7-preview.png")
        com.combo.monkeycompanion.TestPaths.out("settai-v7-preview.png").outputStream().use { full.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun renderV8() {
        val full = PreviewV8.render()
        save(full, "settai-v8-preview.png")
        com.combo.monkeycompanion.TestPaths.out("settai-v8-preview.png").outputStream().use { full.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val closeUp = PreviewV8.renderCatMouseCloseUp()
        save(closeUp, "settai-v8-cat-mouse-fixed.png")
        com.combo.monkeycompanion.TestPaths.out("settai-v8-cat-mouse-fixed.png").outputStream().use { closeUp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun renderV45() {
        val full = PreviewV45.render()
        save(full, "settai-v45-peek-preview.png")
    }

    @Test
    fun renderGags() {
        val full = PreviewSheet.renderGags()
        save(full, "settai-v32-gags-full.png")
        // Half size for sharing (keeps the PNG small).
        save(Bitmap.createScaledBitmap(full, full.width / 2, full.height / 2, true), "settai-v32-gags.png")
    }

    @Test
    fun renderPowers() {
        val full = PreviewSheet.renderPowers()
        save(full, "settai-v32-powers-full.png")
        save(Bitmap.createScaledBitmap(full, full.width / 2, full.height / 2, true), "settai-v32-powers.png")
    }

    @Test
    fun renderCharacters() {
        val full = PreviewSheet.renderCharacters()
        save(full, "settai-v41-characters-full.png")
        save(Bitmap.createScaledBitmap(full, full.width / 2, full.height / 2, true), "settai-v41-characters.png")
    }

    @Test
    fun renderShowOff() {
        val full = PreviewSheet.renderShowOff()
        save(full, "settai-v4-showoff-full.png")
        save(Bitmap.createScaledBitmap(full, full.width / 2, full.height / 2, true), "settai-v4-showoff.png")
    }

    @Test
    fun renderV3() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        ChatHub.init(ctx)
        val dm = ctx.resources.displayMetrics
        val w = dm.widthPixels; val h = dm.heightPixels
        val dir = File(outDir, "v3"); dir.mkdirs()
        for ((name, bmp) in PreviewV3.renderAll(ctx, w, h)) {
            FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        // Spots editor (real activity) with two spots lined up.
        val fr = PreviewV3.spotsFor(1, 1)
        com.combo.monkeycompanion.wallpaper.WallpaperPrefs.saveSpotFractions(ctx, false, fr)
        val act = Robolectric.buildActivity(com.combo.monkeycompanion.wallpaper.SpotsEditorActivity::class.java).setup().get()
        val root = act.window.decorView.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
        (root.getChildAt(0) as com.combo.monkeycompanion.wallpaper.SpotsEditorView).select(1)
        save(snapshot(root, w, h), "v3/editor.png")
        println("V3_DONE")
    }

    private fun snapshot(v: View, w: Int, h: Int): Bitmap {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.rgb(0x30, 0x30, 0x38))
        v.draw(c)
        return bmp
    }

    @Test
    fun renderChatPanel() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        ChatHub.init(ctx)
        ChatHub.memory.clear()
        ChatHub.memory.add(ChatMsg(true, "Who are you?"))
        ChatHub.memory.add(ChatMsg(false, "I'm Settai! It means mischievous in Tamil, so hide your bananas. 🍌"))
        ChatHub.memory.add(ChatMsg(true, "Set an alarm for 7am"))
        ChatHub.memory.add(ChatMsg(false, "Ooh, early Settai! Tap Yes and I'll set it."))
        val act = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val panel = ChatPanelView(act, floating = true, onClose = {})
        val host = FrameLayout(act).apply { setPadding(30, 30, 30, 30); addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)) }
        act.setContentView(host)
        save(snapshot(host, 1080, 1100), "chat-panel.png")
    }

    @Test
    fun renderSettings() {
        val act = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val root = act.window.decorView.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        save(snapshot(root, 1080, 2400), "settings.png")
    }
}
