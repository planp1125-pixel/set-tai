package com.combo.monkeycompanion.ai

import android.content.Context
import android.content.SharedPreferences

enum class BrainType { NONE, OFFLINE, ONLINE }

enum class Provider(
    val label: String,
    val defaultModel: String,
    val baseUrl: String,
    val keyHint: String,
) {
    GEMINI("Google Gemini", "gemini-3.8-flash", "https://generativelanguage.googleapis.com/v1beta/openai", "aistudio.google.com → Get API key"),
    OPENROUTER("OpenRouter", "openrouter/auto", "https://openrouter.ai/api/v1", "openrouter.ai → Keys"),
    GROQ("Groq Cloud", "llama-3.3-70b-versatile", "https://api.groq.com/openai/v1", "console.groq.com → API Keys"),
    OPENAI("OpenAI", "gpt-5.4-mini", "https://api.openai.com/v1", "platform.openai.com → API keys"),
    XAI("xAI Grok", "grok-4.3", "https://api.x.ai/v1", "console.x.ai → API keys"),
    ANTHROPIC("Anthropic Claude", "claude-haiku-5-5", "https://api.anthropic.com/v1", "console.anthropic.com → API keys"),
    CUSTOM("Custom (OpenAI-compatible)", "", "", "Your server's key (leave blank if none)");
}


/** All user settings. Secrets are stored encrypted via [SecureStore]. */
class AppSettings(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("monkey_settings", Context.MODE_PRIVATE)
    /** v10.16: true on a fresh install (never updated from an older version). */
    private val freshInstall: Boolean by lazy {
        runCatching { val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0); pi.firstInstallTime > 0L && pi.firstInstallTime == pi.lastUpdateTime }.getOrDefault(false)
    }

    /**
     * v9.5 Strict Opt-In Gate for "Give Brain":
     * Defaults to FALSE (and brainType = NONE). Until the user explicitly enables "Give Brain",
     * no AI model is ever used anywhere in the app.
     */
    /** v10.12: funny Tom-and-Jerry style skits between Duo/Trio friends (no AI used). */
    var skitsEnabled: Boolean
        get() = p.getBoolean("skits_enabled", true)
        set(v) { p.edit().putBoolean("skits_enabled", v).apply() }

    /** v10.13: peek in with a pose when a camera app opens. */
    /** v10.13: the faint chat button opens 💬 typing (false) or 🎤 speaking (true). */
    var fabSpeak: Boolean
        get() = p.getBoolean("fabSpeak", false)
        set(v) { p.edit().putBoolean("fabSpeak", v).apply() }
    var photobomb: Boolean
        get() = p.getBoolean("photobomb", true)
        set(v) { p.edit().putBoolean("photobomb", v).apply() }

    var aiBrainEnabled: Boolean
        get() = p.getBoolean("ai_brain_enabled", p.getString("brain", "NONE") != "NONE")
        set(v) {
            val e = p.edit().putBoolean("ai_brain_enabled", v)
            if (!v) e.putString("brain", BrainType.NONE.name)
            else if (p.getString("brain", "NONE") == "NONE") e.putString("brain", BrainType.ONLINE.name)
            e.apply()
        }

    var brainType: BrainType
        get() = runCatching { BrainType.valueOf(p.getString("brain", "NONE")!!) }.getOrDefault(BrainType.NONE)
        set(v) {
            p.edit()
                .putString("brain", v.name)
                .putBoolean("ai_brain_enabled", v != BrainType.NONE)
                .apply()
        }

    var provider: Provider
        get() = runCatching { Provider.valueOf(p.getString("provider", "OPENAI")!!) }.getOrDefault(Provider.OPENAI)
        set(v) = p.edit().putString("provider", v.name).apply()

    /** v10.13.2: old built-in defaults that new accounts can't use any more (404) fall back to the current default. */
    private val staleDefaults = setOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash", "google/gemini-2.5-flash")
    fun modelFor(pr: Provider): String = p.getString("model_${pr.name}", null)?.takeIf { it.isNotBlank() && it !in staleDefaults } ?: pr.defaultModel
    fun setModelFor(pr: Provider, m: String) = p.edit().putString("model_${pr.name}", m.trim()).apply()

    var customBaseUrl: String
        get() = p.getString("custom_url", "") ?: ""
        set(v) = p.edit().putString("custom_url", v.trim()).apply()

    fun apiKeyFor(pr: Provider): String = p.getString("key_${pr.name}", null)?.let { SecureStore.decrypt(it) } ?: ""
    fun setApiKeyFor(pr: Provider, key: String) {
        if (key.isBlank()) p.edit().remove("key_${pr.name}").apply()
        else p.edit().putString("key_${pr.name}", SecureStore.encrypt(key.trim())).apply()
    }

    var hfToken: String
        get() = p.getString("hf_token", null)?.let { SecureStore.decrypt(it) } ?: ""
        set(v) {
            if (v.isBlank()) p.edit().remove("hf_token").apply()
            else p.edit().putString("hf_token", SecureStore.encrypt(v.trim())).apply()
        }

    var offlineModelPath: String
        get() = p.getString("offline_path", "") ?: ""
        set(v) = p.edit().putString("offline_path", v).apply()

    var offlineUseGpu: Boolean
        get() = p.getBoolean("offline_gpu", false)
        set(v) = p.edit().putBoolean("offline_gpu", v).apply()

    var offlineTools: Boolean
        get() = p.getBoolean("offline_tools_v31", true)   // v3.1: on by default (old "offline_tools" off-default is ignored)
        set(v) = p.edit().putBoolean("offline_tools_v31", v).apply()

    var downloadId: Long
        get() = p.getLong("download_id", -1L)
        set(v) = p.edit().putLong("download_id", v).apply()

    var downloadTarget: String
        get() = p.getString("download_target", "") ?: ""
        set(v) = p.edit().putString("download_target", v).apply()

    /** Roam the screen edges: climb the sides, hang from the top, drop down. */
    var roam: Boolean
        get() = p.getBoolean("roam", true)
        set(v) = p.edit().putBoolean("roam", v).apply()

    /**
     * v10.18.1 how much the brain may run when nobody is talking to it (idle Body Sense lines + preparing touch
     * reactions): "off", "gentle" (default: only while the phone rests, a few times an hour) or "fast".
     */
    var backgroundAi: String
        get() = p.getString("bg_ai", "gentle") ?: "gentle"
        set(v) = p.edit().putString("bg_ai", v).apply()

    var quipsEnabled: Boolean
        get() = p.getBoolean("quips", true)
        set(v) = p.edit().putBoolean("quips", v).apply()

    /** Average minutes between unprompted AI quips. */
    var quipMinutes: Int
        get() = p.getInt("quip_minutes", 15)
        set(v) = p.edit().putInt("quip_minutes", v.coerceIn(3, 120)).apply()

    /**
     * Auto-rest: seconds without any interaction before Settai hides behind the nearest edge
     * (0 = never). Battery saver caps it at 60 s.
     */
    var restSeconds: Int
        get() = p.getInt("rest_seconds", 120)
        set(v) = p.edit().putInt("rest_seconds", v.coerceIn(0, 3600)).apply()

    /** The auto-rest delay actually used (battery saver makes it sooner). 0 = never. */
    val effectiveRestSeconds: Int get() {
        val r = restSeconds
        return if (batterySaver && r > 0) minOf(r, 60) else r
    }

    /** Battery saver: lower frame rate, fewer autonomous moves / gags / quips, rest sooner. */
    var batterySaver: Boolean
        get() = p.getBoolean("battery_saver", false)
        set(v) = p.edit().putBoolean("battery_saver", v).apply()

    /** v10.7 Settings › "Smooth animation (beta)": volume-preserving squash, follow-through springs, eased poses. Off = v10.6 drawing. */
    var smoothAnimation: Boolean
        get() = p.getBoolean("smooth_animation", true)
        set(v) = p.edit().putBoolean("smooth_animation", v).apply()

    /** v10.8 Settings › "Living reactions (beta)": touch zones + a big reaction library with a no-repeat picker. Off = v10.7 tap reactions. */
    var livingReactions: Boolean
        get() = p.getBoolean("living_reactions", true)
        set(v) = p.edit().putBoolean("living_reactions", v).apply()

    /** v10.8 Settings › "Sound effects": Smash and firework sounds (always silent when the phone is on silent / vibrate). */
    var soundEffects: Boolean
        get() = p.getBoolean("sound_effects", true)
        set(v) = p.edit().putBoolean("sound_effects", v).apply()

    /** v10.9 Settings › "Double-tap for power": double-tap a character = one of its powers (random, never the same twice in a row). Off = double-tap opens chat (v10.8). */
    var doubleTapPower: Boolean
        get() = p.getBoolean("double_tap_power", true)
        set(v) = p.edit().putBoolean("double_tap_power", v).apply()

    /** v10.9 Settings › "Chat button": a small floating 💬 button beside each character opens the chat. */
    var chatButton: Boolean
        get() = p.getBoolean("chat_button", true)
        set(v) = p.edit().putBoolean("chat_button", v).apply()

    /** When resting behind the edge, come back out (wake-up routine) after the phone is unlocked. */
    var comeOutOnUnlock: Boolean
        get() = p.getBoolean("come_out_on_unlock", true)
        set(v) = p.edit().putBoolean("come_out_on_unlock", v).apply()

    /** v3.2 superpower: "off", "random" or a [com.combo.monkeycompanion.brain.Superpowers] id. */
    var power: String
        get() = p.getString("power", com.combo.monkeycompanion.brain.Superpowers.RANDOM) ?: com.combo.monkeycompanion.brain.Superpowers.RANDOM
        set(v) = p.edit().putString("power", v).apply()

    /** v4.1: which body ([com.combo.monkeycompanion.character.CharacterRegistry] id). */
    var characterId: String
        get() = (p.getString("character", null) ?: defaultCharacter()).let { if (it == "rocketboy") "rocketgirl" else it }   // v10.9 rename
        set(v) = p.edit().putString("character", v).apply()

    /**
     * v10.16: new installs start with Kutti Satan (the little devil). An older install that never picked a body keeps
     * the monkey it has always shown (it already has other settings saved); the choice is saved once so it never flips.
     */
    private fun defaultCharacter(): String {
        val id = if (freshInstall) "devil" else "monkey"
        p.edit().putString("character", id).apply()
        return id
    }

    /** v10.17: power ids switched on for a character (null = never changed = its own powers). */
    fun enabledPowers(id: String): Set<String>? = p.getString("powers_on_$id", null)?.split(',')?.filter { it.isNotBlank() }?.toSet()
    fun setEnabledPowers(id: String, ids: Set<String>?) {
        if (ids == null) p.edit().remove("powers_on_$id").apply() else p.edit().putString("powers_on_$id", ids.joinToString(",")).apply()
    }

    /** v4.1: a character's wardrobe ([com.combo.monkeycompanion.character.Wear.encode]); null = defaults. */
    fun wardrobeFor(id: String): String? = p.getString("wear_$id", null)
    fun setWardrobeFor(id: String, v: String) = p.edit().putString("wear_$id", v).apply()

    /** Load the chosen body and every character's wardrobe into the registry (cheap; call on start / after Settings). */
    fun applyCharacter() {
        val reg = com.combo.monkeycompanion.character.CharacterRegistry
        for (c in reg.all) wardrobeFor(c.id)?.let { reg.wear(c).decode(it) }
        reg.select(characterId)
    }

    /** Random comedy gags while idle. */
    var gags: Boolean
        get() = p.getBoolean("gags", true)
        set(v) = p.edit().putBoolean("gags", v).apply()

    /** Conversation turns (user + monkey pairs) remembered. */
    var memoryTurns: Int
        get() = p.getInt("memory_turns", 8)
        set(v) = p.edit().putInt("memory_turns", v.coerceIn(0, 30)).apply()

    /** Timers/alarms: "auto" (Clock app when allowed, else Settai's own), "clock", or "settai". */
    var clockMode: String
        get() = p.getString("clock_mode", "auto") ?: "auto"
        set(v) = p.edit().putString("clock_mode", v).apply()

    /** Phone-power groups (see [com.combo.monkeycompanion.powers.CapGroup]); all on by default. */
    fun groupEnabled(g: com.combo.monkeycompanion.powers.CapGroup) = p.getBoolean("capgroup_${g.name}", !g.optIn)

    // ---- v4 agent settings ----
    /** Screen agent: ask before every tap/scroll-free step (on by default). Risky buttons always ask. */
    var agentConfirmEach: Boolean
        get() = p.getBoolean("agent_confirm_each", true)
        set(v) = p.edit().putBoolean("agent_confirm_each", v).apply()
    /** Extra packages the screen agent must never drive (comma/space separated), on top of the built-in bank/payment list. */
    var agentBlocked: String
        get() = p.getString("agent_blocked", "")!!
        set(v) = p.edit().putString("agent_blocked", v).apply()
    /** Packages the user explicitly allows even though they look like money apps. */
    var agentAllowed: String
        get() = p.getString("agent_allowed", "")!!
        set(v) = p.edit().putString("agent_allowed", v).apply()
    fun agentBlockedList() = agentBlocked.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
    fun agentAllowedList() = agentAllowed.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
    /** Speak replies aloud (needs the VOICE group on). */
    var speakReplies: Boolean
        get() = p.getBoolean("speak_replies", false)
        set(v) = p.edit().putBoolean("speak_replies", v).apply()
    /** Web lookups: URL template with {q}; blank = DuckDuckGo Instant Answer + Wikipedia. */
    var searchUrl: String
        get() = p.getString("search_url", "")!!
        set(v) = p.edit().putString("search_url", v).apply()
    /** Ice Monkey cooling gag when the battery is hot. */
    var heatGag: Boolean
        get() = p.getBoolean("heat_gag", true)
        set(v) = p.edit().putBoolean("heat_gag", v).apply()
    // ---- v4.2 ----
    /** Speak the answer when the question came by voice (hold-to-talk), even with "Speak replies" off. */
    var speakVoiceReplies: Boolean
        get() = p.getBoolean("speak_voice_replies", true)
        set(v) = p.edit().putBoolean("speak_voice_replies", v).apply()
    /** Proactive buddy level: OFF / CHILL / BUDDY / CHATTERBOX (see ProLevel). */
    var proactiveLevel: String
        get() = p.getString("proactive_level", "BUDDY")!!
        set(v) = p.edit().putString("proactive_level", v).apply()
    fun proactiveCat(c: com.combo.monkeycompanion.agent.ProCat) = p.getBoolean("procat_${c.name}", true)
    fun setProactiveCat(c: com.combo.monkeycompanion.agent.ProCat, on: Boolean) = p.edit().putBoolean("procat_${c.name}", on).apply()
    /** v6 Settai mind: "How independent" 0..100 (how often it does its own thing; 0 = mostly sits quietly). */
    var mindIndependence: Int
        get() = p.getInt("mind_independence", 50)
        set(v) = p.edit().putInt("mind_independence", v.coerceIn(0, 100)).apply()
    /** Quiet hours, minutes since midnight (default 23:00-07:00). Equal = no quiet hours. */
    var quietStart: Int
        get() = p.getInt("quiet_start", 23 * 60)
        set(v) = p.edit().putInt("quiet_start", v.coerceIn(0, 1439)).apply()
    var quietEnd: Int
        get() = p.getInt("quiet_end", 7 * 60)
        set(v) = p.edit().putInt("quiet_end", v.coerceIn(0, 1439)).apply()
    /** Apps that get a break nudge after [usageMinutes] in one go. */
    var usageApps: String
        get() = p.getString("usage_apps", "com.instagram.android, com.google.android.youtube, com.facebook.katana, com.snapchat.android, com.twitter.android, com.zhiliaoapp.musically, in.mohalla.sharechat, com.netflix.mediaclient")!!
        set(v) = p.edit().putString("usage_apps", v).apply()
    fun usageAppList() = usageApps.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    var usageMinutes: Int
        get() = p.getInt("usage_minutes", 30)
        set(v) = p.edit().putInt("usage_minutes", v.coerceIn(5, 240)).apply()
    /** Proactive pipes sometimes arrive with a superpower first: "off" / "sometimes" (~30 %) / "often" (~60 %). */
    var powerEntrances: String
        get() = p.getString("power_entrances", "sometimes")!!
        set(v) = p.edit().putString("power_entrances", v).apply()
    // v4.3: the quick-tap zone and hold-to-talk settings were removed (one fixed gesture map, see brain/Gestures).

    /** v4.4 long-press menu (global order / off set; power pins per character). See brain/LongPressMenu. */
    var menuConfig: com.combo.monkeycompanion.brain.LongPressMenu.Config
        get() = com.combo.monkeycompanion.brain.LongPressMenu.decode(p.getString("menu_order", null), p.getString("menu_off", null))
        set(v) = p.edit().putString("menu_order", com.combo.monkeycompanion.brain.LongPressMenu.encodeOrder(v))
            .putString("menu_off", com.combo.monkeycompanion.brain.LongPressMenu.encodeOff(v)).apply()
    fun menuPins(characterId: String): List<String> = com.combo.monkeycompanion.brain.LongPressMenu.decodePins(p.getString("menu_pins_$characterId", null))
    fun setMenuPins(characterId: String, pins: List<String>) = p.edit().putString("menu_pins_$characterId", com.combo.monkeycompanion.brain.LongPressMenu.encodePins(pins)).apply()
    fun resetMenu() { val e = p.edit().remove("menu_order").remove("menu_off"); p.all.keys.filter { it.startsWith("menu_pins_") }.forEach { e.remove(it) }; e.apply() }

    /** v4.4 phone moments (charger zap, unplug wave, full flex, low collapse, Wi-Fi, headphones, screenshot). */
    fun phoneMoment(id: String): Boolean = p.getBoolean("moment_$id", com.combo.monkeycompanion.brain.PhoneMoments.Moment.of(id)?.defaultOn ?: false)
    fun setPhoneMoment(id: String, on: Boolean) = p.edit().putBoolean("moment_$id", on).apply()

    /** v4.4 bubble chat: close after this many seconds without activity (0 = never). Default 2 min. */
    var chatTimeoutSecs: Int
        get() = p.getInt("chat_timeout", 120)
        set(v) = p.edit().putInt("chat_timeout", v).apply()
    fun setGroupEnabled(g: com.combo.monkeycompanion.powers.CapGroup, on: Boolean) = p.edit().putBoolean("capgroup_${g.name}", on).apply()

    /** Changes whenever anything that affects the brain changes (to rebuild it). */
    fun brainSignature(): String = listOf(
        aiBrainEnabled, brainType, provider, modelFor(provider), customBaseUrl, apiKeyFor(provider).hashCode(),
        offlineModelPath, offlineUseGpu
    ).joinToString("|")
}
