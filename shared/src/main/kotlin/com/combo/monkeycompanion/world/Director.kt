package com.combo.monkeycompanion.world

import com.combo.monkeycompanion.brain.MonkeyState
import kotlin.random.Random

/**
 * Directs short 2-turn templated banter & interactions between two creatures on screen together
 * (e.g., Anna scolding Settai for eating a notification card, Superboy & Settai arguing over who guards the top edge,
 * sharing filter coffee at 4 PM, huddling when battery < 20%, cheering when the charger plugs in).
 *
 * Strictly obeys ProactiveGate respect rules: NEVER plays during typing, calls, fullscreen, DND or quiet hours.
 */
class Director(private val cooldownMs: Long = 3 * 60_000L) {
    enum class Kind(val label: String) {
        NOTIF_SCOLD("notification card scolding"),
        GUARD_TOP_EDGE("top-edge watchtower debate"),
        COFFEE_4PM("4 PM filter coffee break"),
        LOW_BATTERY_HUDDLE("low-battery huddle (<20%)"),
        CHARGER_CHEER("feeding-time charger cheer"),
        HEAT_FANNING("warm-weather fanning"),
        VISITOR_FLOOD("visitor-flood gate defence"),
        PLACE_GOSSIP("place gossip"),
        CAT_MOUSE_CHASE("Billa & Chittu cat-and-mouse chase"),
    }

    data class BanterScene(
        val kind: Kind,
        val leadId: String,
        val buddyId: String,
        val leadLine: String,
        val buddyLine: String,
        val leadState: MonkeyState = MonkeyState.TALKING,
        val buddyState: MonkeyState = MonkeyState.WAVE,
        val durSec: Float = 6.4f,
        val catMouseGag: CatMouseBehaviors.GagId? = null,
    )

    private var lastPlayedAt = 0L
    private var lastKind: Kind? = null
    private var lastCatMouseGag: CatMouseBehaviors.GagId? = null

    fun canPlay(
        now: Long,
        mode: Population.Mode,
        keyboard: Boolean,
        inCall: Boolean,
        fullscreen: Boolean,
        dnd: Boolean,
        quietHours: Boolean,
        ignoreCooldown: Boolean = false,
    ): String? = when {
        mode == Population.Mode.SOLO -> "solo mode"
        keyboard -> "you're typing"
        inCall -> "in a call"
        fullscreen -> "fullscreen"
        dnd -> "do not disturb"
        quietHours -> "quiet hours"
        !ignoreCooldown && lastPlayedAt > 0L && now - lastPlayedAt < cooldownMs -> "cooldown"
        else -> null
    }

    fun pickScene(
        leadId: String,
        mode: Population.Mode,
        state: WorldState,
        minuteOfDay: Int,
        now: Long,
        keyboard: Boolean = false,
        inCall: Boolean = false,
        fullscreen: Boolean = false,
        dnd: Boolean = false,
        quietHours: Boolean = false,
        forceKind: Kind? = null,
        rnd: Random = Random,
        /** v10.12: the real on-screen crew (lead first). Buddies come ONLY from here. */
        active: List<String>? = null,
    ): BanterScene? {
        if (canPlay(now, mode, keyboard, inCall, fullscreen, dnd, quietHours, ignoreCooldown = forceKind != null) != null) return null
        val crew = active ?: Population.activeIds(leadId, mode, state)
        val buddyId = crew.drop(1).firstOrNull { it != leadId } ?: return null
        val kind0 = forceKind ?: if (CatMouseBehaviors.isCatMousePair(leadId, buddyId)) Kind.CAT_MOUSE_CHASE else chooseKind(state, minuteOfDay, rnd)
        // Cat-and-mouse needs BOTH Billa and Chittu on screen.
        val kind = if (kind0 == Kind.CAT_MOUSE_CHASE && !CatMouseBehaviors.isCatMousePair(leadId, buddyId)) Kind.PLACE_GOSSIP else kind0
        val scene = buildScene(kind, leadId, buddyId, state)
        lastPlayedAt = now
        lastKind = kind
        return scene
    }


    private fun chooseKind(state: WorldState, minuteOfDay: Int, rnd: Random): Kind {
        val candidates = ArrayList<Kind>()
        if (state.feeding) candidates += Kind.CHARGER_CHEER
        if (state.hungry || state.batteryPct < 20) candidates += Kind.LOW_BATTERY_HUDDLE
        if (state.hot) candidates += Kind.HEAT_FANNING
        if (state.flooded) candidates += Kind.VISITOR_FLOOD
        if (minuteOfDay in (15 * 60 + 50)..(16 * 60 + 40)) candidates += Kind.COFFEE_4PM
        if (state.visitorsWaiting > 0) candidates += Kind.NOTIF_SCOLD
        candidates += Kind.GUARD_TOP_EDGE
        candidates += Kind.PLACE_GOSSIP
        val filtered = candidates.filter { it != lastKind }.ifEmpty { candidates }
        return filtered[rnd.nextInt(filtered.size)]
    }

    fun buildScene(kind: Kind, leadId: String, buddyId: String, state: WorldState): BanterScene {
        val place = state.currentPlace.worldName
        return when (kind) {
            Kind.NOTIF_SCOLD -> BanterScene(
                kind, leadId, buddyId,
                leadLine = if (leadId == "armour") "${Population.displayName(buddyId)}! Did you just bite the corner off that visitor card?!" else "Ooh, a crunchy visitor card at the gate! *munch* 📬😋",
                buddyLine = if (leadId == "armour") "It smelled like mango, ${Population.displayName(leadId)}! Just one tiny nibble 🙈🍌" else "Spit that out, ${Population.displayName(leadId)}! That's incoming mail, not a snack! ⚙️😤",
                leadState = MonkeyState.GIGGLE, buddyState = MonkeyState.ANGRY
            )
            Kind.GUARD_TOP_EDGE -> BanterScene(
                kind, leadId, buddyId,
                leadLine = if (leadId == "superboy") "I've got the top-edge watchtower today. Clear view of the whole screen 🦸" else "Hey ${Population.displayName(buddyId)}, it's MY turn to hang from the top edge!",
                buddyLine = if (leadId == "superboy") "Fine! I'll guard the bottom edge… where the snacks fall 🍌😏" else "You don't guard it, you just drop on people's thumbs! I've got the sky 🦸",
                leadState = MonkeyState.WAVE, buddyState = MonkeyState.PLAYFUL
            )
            Kind.COFFEE_4PM -> BanterScene(
                kind, leadId, buddyId,
                leadLine = "4 PM filter coffee time! Pouring a steaming cup for my neighbour ☕✨",
                buddyLine = "Aah, strong decoction! Best break in the phone ☕💛",
                leadState = MonkeyState.HAPPY_PAT, buddyState = MonkeyState.HAPPY_PAT
            )
            Kind.LOW_BATTERY_HUDDLE -> BanterScene(
                kind, leadId, buddyId,
                leadLine = "Brr… only ${state.batteryPct}% energy left in the tank. Huddle close to save warmth 🪫🥺",
                buddyLine = "Dimming non-essential servos. Hang in there — feeding time soon 🛡️🔋",
                leadState = MonkeyState.SIT, buddyState = MonkeyState.SIT
            )
            Kind.CHARGER_CHEER -> BanterScene(
                kind, leadId, buddyId,
                leadLine = "Current is flowing!! Feeding time at the bottom edge ⚡🍌",
                buddyLine = "Save some voltage for me! Warm electrons for everyone ⚡🙌",
                leadState = MonkeyState.DANCE, buddyState = MonkeyState.HAPPY_PAT
            )
            Kind.HEAT_FANNING -> BanterScene(
                kind, leadId, buddyId,
                leadLine = "Whew, ${state.tempC.toInt()}°C under the glass! Fan me, machan 🥵🌿",
                buddyLine = "Opening cooling baffles… stand next to my vents! ❄️⚙️",
                leadState = MonkeyState.SIT, buddyState = MonkeyState.WAVE
            )
            Kind.VISITOR_FLOOD -> BanterScene(
                kind, leadId, buddyId,
                leadLine = "Whoa — ${state.visitorsWaiting.coerceAtLeast(4)} visitors knocking at the gate at once! 📬🔔",
                buddyLine = "Shields up and single file at the gate! We've got this 🦸🛡️",
                leadState = MonkeyState.STARTLED, buddyState = MonkeyState.WAVE
            )
            Kind.PLACE_GOSSIP -> BanterScene(
                kind, leadId, buddyId,
                leadLine = "Psst, look — our human walked into $place 👀",
                buddyLine = "Act natural! I'm keeping an eye on the corners 🎭✨",
                leadState = MonkeyState.PEEK, buddyState = MonkeyState.WAVE
            )
            Kind.CAT_MOUSE_CHASE -> {
                val gag = CatMouseBehaviors.pickGag(state, lastCatMouseGag)
                lastCatMouseGag = gag
                val bFirst = leadId != "chittu"
                val lId = if (bFirst) "billa" else "chittu"
                val rId = if (bFirst) "chittu" else "billa"
                BanterScene(
                    kind = Kind.CAT_MOUSE_CHASE,
                    leadId = lId,
                    buddyId = rId,
                    leadLine = if (bFirst) gag.billaLine else gag.chittuLine,
                    buddyLine = if (bFirst) gag.chittuLine else gag.billaLine,
                    leadState = if (bFirst) gag.billaStartState else gag.chittuEndState,
                    buddyState = if (bFirst) gag.chittuEndState else gag.billaStartState,
                    durSec = gag.durSec,
                    catMouseGag = gag,
                )
            }
        }
    }

    /** When the human chats with the lead creature, the neighbour reacts with a pose and a short side-quip. */
    fun sideReaction(buddyId: String, userText: String, replyText: String, state: WorldState, rnd: Random = Random): Pair<MonkeyState, String> {
        val u = userText.lowercase()
        return when {
            "banana" in u || "food" in u || "eat" in u -> MonkeyState.HAPPY_PAT to when (buddyId) {
                "armour" -> "Save a byte for me! 🔋"
                "superboy" -> "Hero rations! 🍌"
                "mask" -> "Did someone say snacks?! 🎩"
                else -> "Me too, me too!! 🍌"
            }
            state.hungry -> MonkeyState.SIT to "So hungry… (${state.batteryPct}%) 🪫"
            state.feeding -> MonkeyState.DANCE to "Mmm, fresh current ⚡"
            else -> listOf(
                MonkeyState.WAVE to "Hi from the side! 👋",
                MonkeyState.GIGGLE to "Hehe, true! 😄",
                MonkeyState.PEEK to "Listening in 👀",
                MonkeyState.HAPPY_PAT to "Agreed! ✨",
            )[rnd.nextInt(4)]
        }
    }
}
