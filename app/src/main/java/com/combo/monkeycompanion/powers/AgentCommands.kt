package com.combo.monkeycompanion.powers

import com.combo.monkeycompanion.agent.MemoryParse
import com.combo.monkeycompanion.agent.RoutineParse

/**
 * v4 pre-parser for the agent powers (English + Tanglish), run before [PhoneCommands]' own rules.
 * "_soft" = something said in passing ("my name is Ravi"): stored quietly if Memory is on, and the brain
 * still answers.
 */
object AgentCommands {
    private fun has(t: String, re: String) = Regex(re).containsMatchIn(t)

    fun parse(raw: String): Invocation? {
        val t = " " + raw.lowercase().replace(Regex("[?!.,]+"), " ").replace(Regex("\\s+"), " ").trim() + " "
        // ---- routines first ("every day at 9 remind me..." must not become an alarm)
        if (has(t, "^ (list|show)( my| all)? routines | my routines | what routines ")) return Invocation("list_routines")
        Regex("^ (delete|remove|cancel|stop) (the )?routine #?(\\w[\\w ]*) $").find(t)?.let { return Invocation("delete_routine", mapOf("which" to it.groupValues[3].trim())) }
        if (RoutineParse.looksLikeRoutine(raw) && RoutineParse.parse(raw) { null } != null) return Invocation("add_routine", mapOf("text" to raw.trim()))
        // ---- memory
        if (has(t, "^ (what do you (remember|know) about me|what do you remember|show (me )?(your )?(memory|memories|notebook)|unakku enna (nyabagam|gyabagam|theriyum) ) ")) return Invocation("list_memories")
        MemoryParse.forget(raw)?.takeIf { !has(t, "^ forget it $") }?.let { return Invocation("forget", mapOf("what" to it)) }
        MemoryParse.remember(raw)?.let { f ->
            val explicit = has(t, "\\b(remember|note|nyabagam|gyabagam|ninaivu)\\b")
            return Invocation("remember", if (explicit) mapOf("fact" to f) else mapOf("fact" to f, "_soft" to true))
        }
        // ---- notifications
        if (has(t, "\\b(otp|one time password|verification code|login code)\\b") && has(t, "\\b(show|what|whats|what's|read|tell|enna|sollu|latest|new|my|get)\\b") && !has(t, "\\b(send|share|forward)\\b"))
            return Invocation("show_otp")
        if (has(t, " what did i miss | what have i missed | did i miss anything | any (new )?(notifications|messages|notifs) | (read|check|summari[sz]e|show)( me)?( my| the)? (notifications|notifs) | notifications? (enna|sollu|check pannu) | enna miss (panniten|pannen|panninen) "))
            return Invocation("read_notifications")
        Regex("^ reply to (.+?) (saying|say|with|that|:) (.+) $").find(" " + raw.trim().trimEnd('.') + " ")?.let { return Invocation("reply_notification", mapOf("who" to it.groupValues[1].trim(), "text" to it.groupValues[3].trim())) }
        if (has(t, "^ (clear|dismiss|remove) (all )?(my |the )?notifications ")) return Invocation("dismiss_notification", mapOf("who" to "all"))
        // ---- screen agent (single steps; multi-step tasks go to the brain)
        if (has(t, "^ (read|scan|check) (my |the |this )?screen | what'?s on (my |the )?screen | what is on (my |the )?screen | screen la enna ")) return Invocation("read_screen")
        if (has(t, "^ (press|hit|tap) (the )?back( button)? $")) return Invocation("press_key", mapOf("key" to "back"))
        if (has(t, "^ (press|hit|tap) (the )?home( button)? $|^ go to (the )?home ?screen $")) return Invocation("press_key", mapOf("key" to "home"))
        if (has(t, "^ (show |open )?(recent apps|recents) $")) return Invocation("press_key", mapOf("key" to "recents"))
        Regex("^ scroll (down|up) $").find(t)?.let { return Invocation("scroll", mapOf("direction" to it.groupValues[1])) }
        return MoreCommands.parse(raw, t)
    }
}

/** Voice / web / doctor / calendar-contacts phrases. */
object MoreCommands {
    private fun has(t: String, re: String) = Regex(re).containsMatchIn(t)

    fun parse(raw: String, t: String): Invocation? {
        // weather ("weather in Chennai", "will it rain today", "mazhai varuma")
        if (has(t, "\\b(weather|forecast|temperature outside|will it rain|is it raining|going to rain|mazhai|mazha|veyil)\\b") && !has(t, "\\b(app|widget|setting)\\b")) {
            val place = Regex("\\b(?:in|at|for) ([a-z][a-z .'-]{1,40}?)(?: today| tomorrow| now| please)?\\s*$").find(t.trim())?.groupValues?.get(1)?.trim()
                ?.takeIf { it !in setOf("the morning", "the evening", "today", "tomorrow", "here", "my area") }
            return Invocation("get_weather", mapOf("place" to (place ?: "")))
        }
        if (has(t, " what'?s my day | what is my day | my day today | (today'?s|my) (schedule|agenda|calendar|plan) | what'?s on (my calendar|today) | inniku enna (plan|irukku|schedule) ")) return Invocation("my_day")
        if (has(t, "\\b(upcoming )?birthdays\\b") && !has(t, "\\b(remember|my birthday is)\\b")) return Invocation("upcoming_birthdays")
        Regex("^ (?:please )?(?:call|ring|phone|dial) ([a-z][a-z .'-]{1,30}?)(?: please| now| for me)? $").find(t)?.let { return Invocation("call_contact", mapOf("name" to it.groupValues[1].trim())) }
        Regex("^ ([a-z][a-z .'-]{1,30}?) (?:ku|kku) (?:call|phone) (?:pannu|podu|panni kudu) $").find(t)?.let { return Invocation("call_contact", mapOf("name" to it.groupValues[1].trim())) }
        if (has(t, " (phone|mobile) (check ?up|doctor|health) | why is my phone (so )?slow | storage (is )?full | (battery|battery-eating) hogs | which apps? (use|eat|drain)s? (the )?(most|battery) | clean ?up my phone ")) return Invocation("phone_checkup")
        Regex("^ (?:say|speak|read) (?:this |it )?(?:out loud|aloud)[: ]+(.+) $").find(" " + raw.trim() + " ")?.let { return Invocation("say_aloud", mapOf("text" to it.groupValues[1].trim())) }
        return null
    }
}
