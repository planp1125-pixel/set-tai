package com.combo.monkeycompanion.character

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import com.combo.monkeycompanion.rig.ChibiFaceBridge
import java.io.File

/**
 * Persists user-created [CustomCompanionSpec]s on the phone, wires the Android `Bitmap` portrait
 * renderer into [ChibiFaceBridge], and supports `.settai.json` character export & import.
 */
object CustomCompanionStore {
    private const val PREFS = "settai_custom_companions_v9_2"
    private val bitmapCache = HashMap<String, Pair<Int, Bitmap>>()
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var initialized = false

    init {
        installBridge()
    }

    fun installBridge() {
        ChibiFaceBridge.androidBitmapDrawer = { spec, canvas, dst, alpha ->
            val b64 = spec.facePortraitBase64
            if (b64.isBlank()) {
                false
            } else {
                val bmp = getOrDecodeBitmap(spec.id, b64)
                if (bmp == null) {
                    false
                } else {
                    bmpPaint.alpha = alpha
                    canvas.drawBitmap(bmp, null, dst, bmpPaint)
                    bmpPaint.alpha = 255
                    true
                }
            }
        }
    }

    @Synchronized
    fun getOrDecodeBitmap(id: String, b64: String): Bitmap? {
        if (b64.isBlank()) return null
        val hash = b64.hashCode()
        val cached = bitmapCache[id]
        if (cached != null && cached.first == hash && !cached.second.isRecycled) return cached.second
        val decoded = PhotoCompanionAnalyzer.decodePngBase64(b64) ?: return null
        bitmapCache[id] = hash to decoded
        return decoded
    }

    fun init(ctx: Context) {
        installBridge()
        if (initialized) return
        initialized = true
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = p.getStringSet("ids", null) ?: emptySet()
        if (ids.isEmpty()) {
            p.getString("spec_custom_1", null)?.let { json ->
                CustomCompanionSpec.fromJson(json)?.let { CustomCompanionRegistry.register(it) }
            }
        } else {
            for (id in ids.sorted()) {
                p.getString("spec_$id", null)?.let { json ->
                    CustomCompanionSpec.fromJson(json)?.let { CustomCompanionRegistry.register(it) }
                }
            }
        }
    }

    fun save(ctx: Context, spec: CustomCompanionSpec): CustomCharacter {
        installBridge()
        val ch = CustomCompanionRegistry.register(spec)
        val app = ctx.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = (p.getStringSet("ids", emptySet()) ?: emptySet()).toMutableSet()
        ids.add(spec.id)
        val json = spec.toJson()
        p.edit()
            .putStringSet("ids", ids)
            .putString("spec_${spec.id}", json)
            .putString("active_custom_id", spec.id)
            .apply()

        runCatching {
            val dir = File(app.filesDir, "companions").apply { mkdirs() }
            File(dir, "${spec.id}.settai.json").writeText(json)
        }
        return ch
    }

    fun saveAsNewCompanion(ctx: Context, baseSpec: CustomCompanionSpec): CustomCharacter {
        init(ctx)
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = (p.getStringSet("ids", emptySet()) ?: emptySet()).toMutableSet()
        var nextIdx = 2
        while ("custom_$nextIdx" in ids && nextIdx < 12) nextIdx++
        val newSpec = baseSpec.copy(id = "custom_$nextIdx")
        return save(ctx, newSpec)
    }

    fun activeSpec(ctx: Context): CustomCompanionSpec {
        init(ctx)
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val activeId = p.getString("active_custom_id", "custom_1") ?: "custom_1"
        return CustomCompanionRegistry.specById(activeId) ?: CustomCompanionRegistry.primary().spec
    }

    fun exportToJson(spec: CustomCompanionSpec): String = spec.toJson()

    fun importFromJson(ctx: Context, json: String): CustomCharacter? {
        val parsed = CustomCompanionSpec.fromJson(json) ?: return null
        return save(ctx, parsed)
    }
}
