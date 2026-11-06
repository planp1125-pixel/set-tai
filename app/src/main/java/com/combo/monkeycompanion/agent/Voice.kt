package com.combo.monkeycompanion.agent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.combo.monkeycompanion.ai.AppSettings
import com.combo.monkeycompanion.powers.CapGroup
import com.combo.monkeycompanion.powers.Perm
import java.util.Locale

/**
 * v4 voice (opt-in): push-to-talk with the phone's SpeechRecognizer (only while you hold / tap the mic,
 * no wake word) and spoken replies with TextToSpeech (Indian English voice when the phone has one).
 * The engine is created on first use and shut down after a minute of silence (battery).
 */
object Voice {
    @Volatile var speaking = false; private set
    @Volatile var listening = false; private set
    private var tts: TextToSpeech? = null
    private var ready = false
    private var queued: String? = null
    private val main by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    private val idleShutdown by lazy { Runnable { tts?.shutdown(); tts = null; ready = false } }

    fun enabled(ctx: Context) = AppSettings(ctx).groupEnabled(CapGroup.VOICE)

    /** Strips emoji / tags so the voice doesn't read them out. */
    fun clean(t: String): String = t.replace(Regex("\\[[^\\]]{1,30}\\]"), " ")
        .replace(Regex("[\\p{So}\\p{Cn}\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}]"), "").replace(Regex("\\s+"), " ").trim()

    fun speak(ctx: Context, text: String) {
        val t = clean(text).take(400)
        if (t.isEmpty()) return
        main.post {
            main.removeCallbacks(idleShutdown)
            val e = tts
            if (e != null && ready) say(e, t)
            else {
                queued = t
                if (e == null) tts = TextToSpeech(ctx.applicationContext) { st ->
                    val eng = tts ?: return@TextToSpeech
                    if (st != TextToSpeech.SUCCESS) { tts = null; return@TextToSpeech }
                    val inLoc = Locale("en", "IN")
                    if (eng.isLanguageAvailable(inLoc) >= TextToSpeech.LANG_AVAILABLE) eng.language = inLoc
                    tune(eng, ctx)
                    eng.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) { speaking = true }
                        override fun onDone(id: String?) { speaking = false; main.postDelayed(idleShutdown, 60_000) }
                        @Deprecated("old api") override fun onError(id: String?) { speaking = false }
                    })
                    ready = true
                    queued?.let { q -> queued = null; say(eng, q) }
                }
            }
        }
    }

    private var appCtx: Context? = null
    private fun say(e: TextToSpeech, t: String) { appCtx?.let { tune(e, it) }; speaking = true; e.speak(t, TextToSpeech.QUEUE_FLUSH, null, "settai") }

    /** v4.2: each character has its own voice: (pitch, rate). Settai squeaky, Armour Anna deep, ... */
    fun style(characterId: String): Pair<Float, Float> = when (characterId) {
        "superboy" -> 1.05f to 1.12f
        "armour" -> 0.72f to 0.94f
        "mask" -> 1.18f to 1.16f
        else -> 1.4f to 1.08f   // Settai the monkey
    }

    private fun tune(e: TextToSpeech, ctx: Context) {
        appCtx = ctx.applicationContext
        val id = runCatching { AppSettings(ctx).characterId }.getOrDefault("monkey")
        val (pitch, rate) = style(id)
        e.setPitch(pitch); e.setSpeechRate(rate)
    }

    fun stopSpeaking() { main.post { tts?.stop(); speaking = false } }

    // ---------------- push-to-talk ----------------
    private var rec: SpeechRecognizer? = null

    fun canListen(ctx: Context) = enabled(ctx) && Perm.MIC.granted(ctx) && SpeechRecognizer.isRecognitionAvailable(ctx)

    /** Why a listen ended without words (for a friendly line). */
    enum class Miss { NO_MATCH, NETWORK, NO_PERMISSION, BUSY, UNAVAILABLE, CANCELLED }

    @Volatile var onDevice = false; private set

    /**
     * Starts one listen (main thread). v4.2: prefers the on-device recogniser (Android 12+, works offline when the
     * phone has the language pack), falls back to the default one; en-IN with Tamil allowed as a second language
     * where Android supports switching (14+). [onText] gets the words, or null + a [Miss] reason.
     */
    fun listen(ctx: Context, onPartial: (String) -> Unit = {}, onMiss: (Miss) -> Unit = {}, onText: (String?) -> Unit) {
        stopSpeaking()
        cancelListen()
        // v10.31: one delivery per listen, whatever ends it (result, error, tap-to-send timeout).
        val gen = ++session
        var delivered = false
        val once: (String?) -> Unit = { t -> if (!delivered && gen == session) { delivered = true; main.removeCallbacks(sendTimeout); listenHook?.invoke(false); onText(t) } }
        pendingText = once; pendingMiss = onMiss
        listenHook?.invoke(true)
        testEngine?.let { eng ->
            listening = true; lastPartial = null
            eng.onStart(object : TestSession {
                override fun partial(text: String) { lastPartial = text; onPartial(text) }
                override fun result(text: String?) { done(); lastPartial = null; if (text == null) onMiss(Miss.NO_MATCH); once(text) }
                override fun error(miss: Miss) { done(); onMiss(miss); once(null) }
            })
            return
        }
        start(ctx, preferOnDevice = true, onPartial, onMiss, once)
    }

    /** Tests only: replaces the platform SpeechRecognizer so a listen can be driven from JVM tests. */
    interface TestSession { fun partial(text: String); fun result(text: String?); fun error(miss: Miss) }
    interface TestEngine { fun onStart(s: TestSession); fun onStop() {} }
    @Volatile var testEngine: TestEngine? = null
    /** v10.31: the overlay service raises its foreground type to include the microphone while a listen runs. */
    @Volatile var listenHook: ((Boolean) -> Unit)? = null
    private var session = 0
    private var pendingText: ((String?) -> Unit)? = null
    private var pendingMiss: ((Miss) -> Unit)? = null
    /** Tap-to-send: some recognisers never answer stopListening(); deliver what we heard after a short wait. */
    private val sendTimeout = Runnable {
        val t = lastPartial; lastPartial = null
        val cb = pendingText; val miss = pendingMiss
        if (rec != null || listening) { rec?.let { runCatching { it.cancel() } }; done() }
        if (t.isNullOrBlank()) miss?.invoke(Miss.NO_MATCH)
        cb?.invoke(t?.takeIf { it.isNotBlank() })
    }
    const val SEND_TIMEOUT_MS = 2500L

    private fun start(ctx: Context, preferOnDevice: Boolean, onPartial: (String) -> Unit, onMiss: (Miss) -> Unit, onText: (String?) -> Unit) {
        val useOnDevice = preferOnDevice && android.os.Build.VERSION.SDK_INT >= 31 && runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx) }.getOrDefault(false)
        val r = runCatching { if (useOnDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx) else SpeechRecognizer.createSpeechRecognizer(ctx) }.getOrNull()
            ?: run { onMiss(Miss.UNAVAILABLE); return onText(null) }
        rec = r; listening = true; onDevice = useOnDevice
        var heard = false
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) { level = v }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, b: Bundle?) {}
            override fun onPartialResults(b: Bundle?) { b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { heard = true; lastPartial = it; onPartial(it) } }
            override fun onError(e: Int) {
                done()
                // On-device model missing for this language: try the regular recogniser once.
                if (useOnDevice && !heard && (e == 12 || e == 13 || e == SpeechRecognizer.ERROR_CLIENT || e == SpeechRecognizer.ERROR_SERVER)) {
                    main.post { start(ctx, false, onPartial, onMiss, onText) }; return
                }
                // Released before the final result: keep what we heard.
                val partial = lastPartial
                if (partial != null && partial.isNotBlank() && (e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || e == SpeechRecognizer.ERROR_CLIENT)) { lastPartial = null; return onText(partial) }
                lastPartial = null
                onMiss(when (e) {
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> Miss.NETWORK
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Miss.NO_PERMISSION
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> Miss.BUSY
                    // v10.31: ERROR_CLIENT is NOT a user cancel (our own cancel destroys the recogniser first and
                    // gets no callback). Treating it as "cancelled" silently closed the Listening bubble: a no-op mic.
                    SpeechRecognizer.ERROR_CLIENT -> Miss.NO_MATCH
                    else -> Miss.NO_MATCH
                })
                onText(null)
            }
            override fun onResults(b: Bundle?) {
                done()
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() } ?: lastPartial
                lastPartial = null
                if (t == null) onMiss(Miss.NO_MATCH)
                onText(t)
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Hold-to-talk: the user decides when they're done (release), so be patient with pauses.
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        if (!useOnDevice && !online(ctx)) i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            // Tanglish: let the recogniser switch between Indian English and Tamil mid-sentence where supported.
            i.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
            i.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, arrayListOf("en-IN", "ta-IN"))
        }
        runCatching { r.startListening(i) }.onFailure { done(); onMiss(Miss.UNAVAILABLE); onText(null) }
    }

    @Volatile private var lastPartial: String? = null
    /** Mic loudness (dB-ish, -2..10) for a little meter. */
    @Volatile var level = 0f; private set

    /** Hold released: finish and deliver what was said. */
    fun stopListening() {
        testEngine?.let { if (listening) { it.onStop(); main.removeCallbacks(sendTimeout); main.postDelayed(sendTimeout, SEND_TIMEOUT_MS) }; return }
        rec?.let { runCatching { it.stopListening() } }
        if (listening) { main.removeCallbacks(sendTimeout); main.postDelayed(sendTimeout, SEND_TIMEOUT_MS) }
    }

    fun online(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)

    /** In-character line for a [Miss]. */
    fun missLine(m: Miss): String = when (m) {
        Miss.NO_MATCH -> "Didn't catch that 🙉 Long-press me → 🎤 to try again!"
        Miss.NETWORK -> "My ears need internet on this phone 📶 (no offline speech pack). Type to me instead, or download the offline English (India) pack in Google's speech settings."
        Miss.NO_PERMISSION -> "I need the microphone permission to hear you 🎤"
        Miss.BUSY -> "My ears are busy, try again in a sec! 👂"
        Miss.UNAVAILABLE -> "This phone has no speech recogniser for me 😕 Type to me instead!"
        Miss.CANCELLED -> "Okay, cancelled. 🤐"
    }

    private fun done() { listening = false; level = 0f; rec?.let { runCatching { it.destroy() } }; rec = null }
    fun cancelListen() {
        val was = listening || rec != null
        session++; main.removeCallbacks(sendTimeout); pendingText = null; pendingMiss = null
        rec?.let { runCatching { it.cancel() } }; done()
        if (was) listenHook?.invoke(false)
    }
}
