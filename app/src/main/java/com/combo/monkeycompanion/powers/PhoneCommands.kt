package com.combo.monkeycompanion.powers

import java.util.Calendar
import java.util.Locale

/** A capability call: [id] is a [Capability.id], [args] its parameters. */
data class Invocation(val id: String, val args: Map<String, Any?> = emptyMap())

/**
 * Deterministic intent layer that runs before the brain for every brain (cloud, local Gemma, none).
 * Only high-confidence phrasings (English + common Tamil-English); everything else goes to the brain,
 * which has the same powers through tools or tags.
 */
object PhoneCommands {
    fun parse(input: String, now: Calendar = Calendar.getInstance()): Invocation? {
        val raw = input.trim()
        if (raw.isEmpty() || raw.length > 200) return null
        AgentCommands.parse(raw)?.let { return it }
        if (raw.length > 140) return null
        ClockCommands.parse(raw, now)?.let { return clock(it) }
        val t = " " + ClockCommands.normalise(raw).replace(Regex("(?<!\\d)[.]|[.](?!\\d)"), " ").replace(Regex("\\s+"), " ").trim() + " "
        if (Regex("\\b(why|how do|how to|how does|should i|can i|what is a|explain)\\b").containsMatchIn(t) && !Regex("\\b(can you|could you)\\b").containsMatchIn(t)) {
            return query(t)   // a question about a thing, not an order (still answer status questions)
        }
        return torch(t)?.let { Invocation("set_torch", mapOf("on" to it)) }
            ?: media(t) ?: dnd(t) ?: ringer(t) ?: volume(t) ?: brightness(t) ?: timeout(t) ?: rotate(t)
            ?: connection(t) ?: camera(t) ?: dial(raw, t) ?: search(raw, t) ?: settingsPage(t)
            ?: openApp(raw, t)?.let { Invocation("open_app", mapOf("name" to it)) }
            ?: query(t)
    }

    private fun clock(c: ClockCommand) = when (c) {
        is ClockCommand.SetTimer -> Invocation("set_timer", mapOf("seconds" to c.seconds, "label" to c.label))
        is ClockCommand.SetAlarm -> Invocation("set_alarm", mapOf("hour" to c.hour, "minute" to c.minute, "label" to c.label, "tomorrow" to c.tomorrow, "ambiguous" to !c.explicitAmPm))
        is ClockCommand.List -> Invocation("list_timers")
        is ClockCommand.Cancel -> Invocation("cancel_timer", mapOf("which" to c.what))
    }

    private fun has(t: String, re: String) = Regex(re).containsMatchIn(t)
    private const val OFF = "\\b(off|anachidu|anaichidu|anai|anachu|niruthu|stop|disable|kill|deactivate)\\b"
    private const val ON = "\\b(on|enable|activate|start|turn it on|podu)\\b"
    private const val DO = "\\b(turn|switch|put|set|make|change|can you|could you|please|pls|enable|disable|increase|decrease|raise|lower|reduce|keep|go|activate|deactivate)\\b"

    private fun torch(t: String): Boolean? {
        if (!has(t, "\\b(torch|flashlight|flash light|flash)\\b")) return null
        if (has(t, "\\b(what|why|how|which|is it|does)\\b") && !has(t, "\\bturn|switch|put\\b")) return null
        val off = has(t, "\\b(off|anachidu|anaichidu|anai|anachu|niruthu|stop|close|disable|kill)\\b")
        val on = has(t, "\\b(on|set|light up|enable|start|open|need light|turn it on|adi)\\b")
        return when {
            off -> false
            on -> true
            Regex("^\\s*(torch|flashlight)\\s*(please|pls|da|machan)?\\s*$").matches(t) -> true
            else -> null
        }
    }

    private fun level(t: String): String? = when {
        has(t, "\\b(max|maximum|full|fullest|highest|100 ?%?)\\b") -> "max"
        has(t, "\\b(mute|zero|0 ?%?)\\b") -> "mute"
        else -> Regex("\\b(\\d{1,3}) ?(%|percent)?(?=\\s)").find(t)?.groupValues?.get(1)?.takeIf { it.toInt() in 0..100 }
            ?: when {
                has(t, "\\b(up|louder|higher|increase|raise|more|kooduthal|athigam|jaasthi|jasthi|boost)\\b") -> "up"
                has(t, "\\b(down|lower|softer|quieter|decrease|reduce|less|kammi|korai|kurai|korachu)\\b") -> "down"
                else -> null
            }
    }

    private fun volume(t: String): Invocation? {
        val louder = has(t, "^ (louder|too quiet|can't hear|cant hear) ") ; val softer = has(t, "^ (quieter|softer|too loud) ")
        if (!has(t, "\\b(volume|sound|loud|louder|quieter|softer|vol)\\b")) return null
        if (has(t, "\\b(what|how loud|which)\\b") && !has(t, DO)) return null
        val stream = when {
            has(t, "\\b(ring|ringer|ringtone|call ring|incoming)\\b") -> "ring"
            has(t, "\\balarm\\b") -> "alarm"
            has(t, "\\bnotification") -> "notification"
            has(t, "\\b(in-call|call volume|earpiece)\\b") -> "call"
            else -> "media"
        }
        val lv = (if (louder) "up" else if (softer) "down" else null) ?: level(t)
            ?: if (has(t, "\\b(louder|loud)\\b")) "up" else if (has(t, "\\b(quieter|softer)\\b")) "down" else return null
        return Invocation("set_volume", mapOf("stream" to stream, "level" to lv))
    }

    private fun ringer(t: String): Invocation? {
        val mode = when {
            has(t, "\\b(vibrate|vibration|vibrate mode)\\b") -> "vibrate"
            has(t, "\\b(silent mode|silence my phone|silence the phone|put .*on silent|silent|mute (my|the) phone|phone on mute)\\b") -> "silent"
            has(t, "\\b(ringer on|ring mode|normal mode|general mode|sound mode|unmute (my|the)? ?phone|unsilence|turn off silent|off silent)\\b") -> "normal"
            else -> return null
        }
        if (has(t, "\\b(what|is my|is the|why)\\b") && !has(t, DO)) return null
        // "turn off vibrate" -> normal
        val m = if (mode != "normal" && has(t, "\\b(off|disable|stop)\\b") && !has(t, "\\bturn (it )?on\\b")) "normal" else mode
        return Invocation("set_ringer", mapOf("mode" to m))
    }

    private fun dnd(t: String): Invocation? {
        if (!has(t, "\\b(dnd|do not disturb|don't disturb|dont disturb|focus mode)\\b")) return null
        if (has(t, "\\b(what|is it|is dnd|is do)\\b") && !has(t, DO)) return null
        return Invocation("set_dnd", mapOf("on" to !has(t, OFF)))
    }

    private fun brightness(t: String): Invocation? {
        val bright = has(t, "\\b(brightness|bright|brighter|dim|dimmer|darker|screen light|velicham)\\b")
        if (!bright) return null
        if (has(t, "\\b(what|how bright|which)\\b") && !has(t, DO)) return null
        if (has(t, "\\bauto(matic|-brightness| brightness)?\\b|\\badaptive\\b")) return Invocation("set_brightness", mapOf("level" to "auto"))
        val lv = level(t) ?: when {
            has(t, "\\b(brighter|bright|more light|velicham jaasthi)\\b") -> "up"
            has(t, "\\b(dim|dimmer|darker|less light|too bright|velicham kammi)\\b") -> "down"
            else -> return null
        }
        return Invocation("set_brightness", mapOf("level" to if (lv == "mute") "min" else lv))
    }

    private fun timeout(t: String): Invocation? {
        if (!has(t, "\\b(screen timeout|screen time ?out|sleep after|screen off after|turn off the screen after|screen stays? on|display timeout|lock after|keep the screen on)\\b")) return null
        val s = ClockCommands.durationSeconds(t) ?: if (has(t, "\\bkeep the screen on\\b")) 1800 else return null
        return Invocation("set_screen_timeout", mapOf("seconds" to s))
    }

    private fun rotate(t: String): Invocation? {
        if (!has(t, "\\b(auto[ -]?rotate|auto[ -]?rotation|screen rotation|rotation lock|lock rotation|rotate the screen|portrait lock)\\b")) return null
        if (has(t, "\\b(is|what)\\b") && !has(t, DO)) return null
        val lock = has(t, "\\b(rotation lock|lock rotation|portrait lock|lock the rotation)\\b")
        val on = if (lock) has(t, OFF) else !has(t, OFF)
        return Invocation("set_auto_rotate", mapOf("on" to on))
    }

    private fun media(t: String): Invocation? {
        val music = has(t, "\\b(music|song|songs|track|playback|playlist|audio|paatu|paattu|podcast|video)\\b")
        val act = when {
            has(t, "\\b(next|skip)\\b") && (music || has(t, "^ (next|skip)( it| this)?( song| track)? ")) -> "next"
            has(t, "\\b(previous|prev|last song|go back a song|back a track)\\b") && music -> "previous"
            has(t, "\\b(pause|stop|niruthu)\\b") && (music || has(t, "^ pause( it)? ")) -> "pause"
            has(t, "\\b(resume|unpause)\\b") || (has(t, "\\b(play|podu)\\b") && music && !has(t, "\\b(play \\w+ on|play some \\w+|game)\\b")) -> "play"
            else -> return null
        }
        if (has(t, "\\b(what|which)\\b") && !has(t, DO)) return null
        return Invocation("media_control", mapOf("action" to act))
    }

    private val switchWords = linkedMapOf(
        "wifi" to "\\b(wi-?fi|wifi|wireless)\\b", "bluetooth" to "\\b(bluetooth|bt)\\b", "mobile_data" to "\\b(mobile data|cellular data|data connection|4g|5g|internet data|data)\\b",
        "airplane" to "\\b(airplane|aeroplane|flight mode|plane mode)\\b", "location" to "\\b(location|gps)\\b", "nfc" to "\\bnfc\\b", "hotspot" to "\\b(hotspot|tethering)\\b",
    )
    private fun connection(t: String): Invocation? {
        val what = switchWords.entries.firstOrNull { has(t, it.value) }?.key ?: return null
        if (!has(t, "\\b(on|off|turn|switch|enable|disable|activate|deactivate|connect|disconnect|podu|anachidu)\\b")) return null
        if (has(t, "\\b(is|what|which|why)\\b") && !has(t, DO)) return null
        return Invocation("toggle_connection", mapOf("what" to what, "on" to !has(t, "\\b(off|disable|deactivate|disconnect|anachidu|anaichidu)\\b")))
    }

    private fun camera(t: String): Invocation? {
        if (!has(t, "\\b(camera|selfie|take a (photo|picture|pic|video)|record a video|photo edu|photo eduthu)\\b")) return null
        if (has(t, "\\b(what|which|how many|megapixel)\\b") && !has(t, DO)) return null
        if (!has(t, "\\b(open|launch|start|take|click|record|edu|eduthu|selfie)\\b")) return null
        return Invocation("open_camera", mapOf("mode" to if (has(t, "\\b(video|record)\\b")) "video" else "photo"))
    }

    private fun dial(raw: String, t: String): Invocation? {
        val num = Regex("(\\+?\\d[\\d \\-]{4,16}\\d)").find(raw)?.value?.filter { it.isDigit() || it == '+' }
        if (has(t, "^ (call|dial|ring|phone) ") && num != null) return Invocation("dial", mapOf("number" to num))
        if (has(t, "^ (open the dialer|open dialer|open the phone app|dialer) ")) return Invocation("dial", mapOf("number" to ""))
        val sms = Regex("^(?:send an? |send |write an? )?(?:sms|text|message) (?:to )?(\\+?\\d[\\d \\-]{4,16}\\d)(?: (?:saying|that) (.+))?$", RegexOption.IGNORE_CASE).find(raw.trim())
        if (sms != null) return Invocation("compose_sms", mapOf("number" to sms.groupValues[1].filter { it.isDigit() || it == '+' }, "text" to sms.groupValues[2]))
        return null
    }

    private fun search(raw: String, t: String): Invocation? {
        val m = Regex("^(?:please |pls |can you |could you )?(?:search|google|look up|search the web) (?:for |about |on google for )?(.{2,80}?)(?: on google| online| please| for me)?[.!?]*$", RegexOption.IGNORE_CASE).find(raw.trim())
            ?: return null
        val q = m.groupValues[1].trim()
        if (has(" ${q.lowercase()} ", "^ (my|your|it|this|that|me) $")) return null
        return Invocation("web_search", mapOf("query" to q))
    }

    private val pageWords = linkedMapOf(
        "battery_saver" to "battery saver|power saving|power saver", "night_light" to "night light|eye comfort|blue light", "data_usage" to "data usage",
        "wifi" to "wi-?fi|wifi", "bluetooth" to "bluetooth", "display" to "display", "sound" to "sound|ringtone", "battery" to "battery",
        "storage" to "storage", "notifications" to "notification", "location" to "location|gps", "apps" to "apps?|applications",
        "dnd" to "do not disturb|dnd", "date_time" to "date|time", "accessibility" to "accessibility", "security" to "security|lock screen|fingerprint|face unlock",
        "hotspot" to "hotspot|tethering", "vpn" to "vpn", "language" to "language|keyboard", "about" to "about( phone)?|phone info|android version",
        "developer" to "developer( options)?", "cast" to "cast|screen mirroring", "airplane" to "airplane|flight mode", "nfc" to "nfc", "mobile_data" to "mobile network|sim",
    )
    private fun settingsPage(t: String): Invocation? {
        if (!has(t, "\\bsettings?\\b|\\bsetting page\\b|\\boptions\\b")) return null
        if (!has(t, "\\b(open|show|go to|take me to|launch|thira)\\b|^ (\\w+ )?settings? $")) return null
        val page = pageWords.entries.firstOrNull { has(t, "\\b(${it.value}) (settings?|options|page)\\b|\\bsettings? (for|of) (the )?(${it.value})\\b") }?.key
            ?: if (has(t, "^ (open |show |go to )?(the |my )?(phone |system )?settings? $")) "settings" else return null
        return Invocation("open_settings", mapOf("page" to page))
    }

    private val openVerb = Regex("^(?:please |pls |hey settai |settai )?(?:can you |could you |pls |please )?(?:open|launch|start|fire up)(?: up)? (?:the |my )?(.{2,40}?)(?: app| application)?(?: for me| please| pls| da| machan)?$")
    private val openTanglish = Regex("^(?:please )?(.{2,40}?)(?: app)? (?:open pannu|open panu|open pannunga|open panni|thira|thirandhu|thiranthu|open)(?: da| please| pls)?$")

    private fun openApp(raw: String, t: String): String? {
        val s = t.trim()
        val name = (openVerb.find(s) ?: openTanglish.find(s))?.groupValues?.get(1)?.trim() ?: return null
        val bad = Regex("\\b(door|window|the box|a file|link|it|this|that|my mind|up to you|your|heart|mouth|eyes|torch|flashlight|alarm|timer|settings?)\\b")
        if (name.isBlank() || bad.containsMatchIn(name) || name.split(' ').size > 4) return null
        if (name.split(' ').any { com.combo.monkeycompanion.ai.BodyAction.fromWord(it) != null }) return null
        if (Regex("\\b(sleep|bed|school|work|gym|park|party|conversation|chat|game with me|over|again|a new|talking|singing)\\b").containsMatchIn(name)) return null
        val idx = raw.lowercase(Locale.ROOT).indexOf(name)
        return if (idx >= 0) raw.substring(idx, idx + name.length) else name
    }

    private fun query(t: String): Invocation? {
        val ask = has(t, "\\b(how much|how many|what's|whats|what is|what|tell me|check|show|is my|is the|left|remaining|status|level|enna|evlo|evvalavu|ethana|eppadi|current|am i|is it|are we)\\b|\\?") ||
            t.trim().split(' ').size <= 3
        if (!ask) return null
        val id = when {
            has(t, "\\b(battery|charge|charging|batt)\\b") && !has(t, "\\bsaver|saving|health tips\\b") -> "get_battery"
            has(t, "\\b(storage|disk|space left|free space|internal memory)\\b") -> "get_storage"
            has(t, "\\b(ram|memory)\\b") && !has(t, "\\bremember|memories\\b") -> "get_memory"
            has(t, "\\b(phone|mobile|device)\\b.*\\b(temp|temperature|hot|heating|heat|soodu|chuda)\\b|\\b(temp|temperature)\\b.*\\b(phone|mobile|battery)\\b") -> "get_battery"
            has(t, "\\b(cpu|processor|chipset|soc)\\b") -> "get_cpu"
            has(t, "\\b(what's playing|whats playing|what is playing|which song|what song|song name|now playing|currently playing)\\b") -> "get_now_playing"
            has(t, "\\b(wi-?fi|wifi|internet|network|connected|online|offline|signal|bluetooth|airplane|flight mode|gps|location|nfc|mobile data)\\b") &&
                has(t, "\\b(on|off|connected|connection|status|working|signal|am i|is my|is the|enabled|which network|online|offline)\\b|\\?") -> "get_network"
            has(t, "\\b(volume|ringer|silent|vibrate|dnd|do not disturb)\\b") -> "get_sound"
            has(t, "\\b(brightness|screen timeout|auto-?rotate|rotation)\\b") -> "get_display"
            has(t, "\\b(what time|time now|current time|time enna|time ennachu|enna time|time please|the time|what's the date|whats the date|what is the date|today's date|todays date|what day is it|date today|inniku date|innaiku date)\\b") -> "get_time"
            else -> null
        } ?: return null
        return Invocation(id)
    }
}
