package com.combo.monkeycompanion.smash

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import com.combo.monkeycompanion.character.CharacterRegistry
import com.combo.monkeycompanion.overlay.MonkeyOverlayService

/**
 * v10.5 Screen Smash: hides the floating characters, takes one picture of the screen (accessibility screenshot when
 * Settai's screen agent is on, else the screen-capture permission; a cartoon home screen if refused), then the game.
 * The picture only lives in memory while the game runs.
 */
class SmashActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private var shot: Bitmap? = null
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        if (Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.also {
            it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        overlay(hide = true)
        // Give the overlay windows a moment to disappear before the picture is taken.
        ui.postDelayed(::capture, 250)
    }

    private fun capture() {
        val a11y = com.combo.monkeycompanion.agent.SettaiAccessibilityService.instance
        if (Build.VERSION.SDK_INT >= 30 && a11y != null) {
            runCatching {
                a11y.takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                        val hw = Bitmap.wrapHardwareBuffer(r.hardwareBuffer, r.colorSpace)
                        val soft = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        hw?.recycle(); r.hardwareBuffer.close()
                        if (soft != null) start(soft) else askProjection()
                    }
                    override fun onFailure(errorCode: Int) = askProjection()
                })
            }.onFailure { askProjection() }
            return
        }
        askProjection()
    }

    private fun askProjection() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val intent = if (Build.VERSION.SDK_INT >= 34) mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
            else mpm.createScreenCaptureIntent()
        runCatching { startActivityForResult(intent, REQ) }.onFailure { start(null) }
    }

    @Deprecated("Activity result API is fine for a one-off system dialog")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ) return
        if (resultCode == RESULT_OK && data != null) {
            SmashShot.await { bmp -> start(bmp) }
            runCatching { SmashCaptureService.start(this, resultCode, data) }.onFailure { SmashShot.cancel(); start(null) }
        } else {
            Toast.makeText(this, "No screen picture: smashing a cartoon screen instead 💥", Toast.LENGTH_SHORT).show()
            start(null)
        }
    }

    private fun start(bmp: Bitmap?) {
        if (started || isFinishing) { bmp?.recycle(); return }
        started = true
        val (w, h, _) = SmashShot.screenSize(this)
        val pic = bmp ?: SmashShot.fakeScreen(w, h, resources.displayMetrics.density)
        shot = pic
        immersive()
        setContentView(SmashView(this, pic, CharacterRegistry.current) { finish() })
    }

    private fun immersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }

    @Deprecated("Back goes straight to the repair")
    override fun onBackPressed() {
        val v = window.decorView.findViewById<View>(android.R.id.content)?.let { (it as? android.view.ViewGroup)?.getChildAt(0) }
        if (v is SmashView) v.endNow() else { @Suppress("DEPRECATION") super.onBackPressed() }
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        SmashShot.cancel()
        overlay(hide = false)
        shot?.recycle(); shot = null
        super.onDestroy()
    }

    private fun overlay(hide: Boolean) {
        if (!MonkeyOverlayService.running) return
        runCatching {
            startService(Intent(this, MonkeyOverlayService::class.java)
                .setAction(if (hide) MonkeyOverlayService.ACTION_SMASH_HIDE else MonkeyOverlayService.ACTION_SMASH_SHOW))
        }
    }

    companion object {
        private const val REQ = 41

        fun launch(ctx: Context) {
            runCatching { ctx.startActivity(Intent(ctx, SmashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
