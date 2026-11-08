package com.combo.monkeycompanion.speech

import com.combo.monkeycompanion.brain.Gag
import java.util.Calendar
import kotlin.random.Random

/**
 * Things Settai says, by situation. Mostly English with the odd Tamil word.
 * A " || " in a line is a comedic beat: the bubble shows the first part, pauses, then the rest
 * (see [beats]). [pick] avoids repeating recent lines of a pool.
 */
object Lines {
    val TICKLE = listOf(
        "Hehe, that tickles!", "Hahaha, stop it!", "Not the tummy! Hehe!", "Hihihi! Again? No wait!", "Eeek, I'm so ticklish!",
        "Tickle attack! Monkey down! Monkey down!", "Okay okay, I surrender! || ...psych! Hehe.", "My belly is not a touchscreen! || Okay it kind of is.",
        "Hehehe, my fur is laughing!", "You found my weak spot. Tell no one.", "Aiyo, stop, I'll drop my banana!",
    )
    val PAT = listOf(
        "Aww, I like that", "Mmm, head pats are the best", "You're my favourite human", "More pats please!", "Hehe, that feels nice",
        "Five stars. Would get patted again.", "Pat detected. Happiness: 100%.", "I'm purring. Monkeys don't purr. || I'm purring anyway.",
        "Semma! Do it again.", "This is why I live in your phone.", "Careful, my hair took all morning.",
    )
    val TAIL = listOf(
        "Whoa! My tail!", "Eek! Who's there?!", "Hey, that's my tail!", "You scared me!",
        "That's not a charging cable!", "Tail is NOT a joystick!", "Hands off the tail, it's load-bearing.", "Who pulled the emergency brake?!",
    )
    val HAND = listOf(
        "High five!", "Hehe, hi there!", "Wanna hold hands?", "Gimme five!",
        "Up high! Down low! || Too slow. Hehe.", "Fist bump? I have tiny fists.", "Our secret handshake! || Which I forgot.",
    )
    val FOOT = listOf(
        "Hey, my feet are ticklish!", "Hehe, stinky monkey feet!", "Hi-yah! Monkey kick!", "Careful, these feet climb walls.", "Shoes? Never heard of them.",
    )
    val BAD_1 = listOf("Hey! That's a bad touch!", "Not there! That's a bad touch!", "Excuse me?! Bad touch!")
    val BAD_2 = listOf("I said NO! That's a bad touch!", "Stop it! I don't like that!", "Okay, I'm really upset now!")
    val BAD_3 = listOf("That's it, I'm leaving!", "Hmph! Leave me alone!", "Bad touch! I'm not talking to you!")
    val FORGIVE = listOf("Hmph... okay, you're forgiven.", "Fine... just don't do it again.", "...okay, that's better.", "Apology banana accepted.")
    val GRUMPY = listOf("Hmph.", "I'm still mad at you.", "Don't talk to me.", "I'm writing this down in my tiny diary.", "Not now. I'm sulking professionally.")
    val DANCE = listOf(
        "Let's dance!", "Ooh, I love this song!", "Dance party!", "Watch my moves!", "There's no music. || I AM the music.",
        "Kuthu time, machan!", "My moves are 60% banana, 40% confidence.", "Don't record this. || Okay, record this.",
    )
    val PICKUP = listOf(
        "Whoa whoa whoa!", "Where are we going?!", "Wheee, I'm flying!", "Put me down! || ...actually, this is fun",
        "Is this an Uber? I didn't book an Uber!", "Monkey Airlines, please fasten your seatbelts.", "I'm scared of heights. || I live on a phone.",
        "Gentle! I'm a premium monkey.", "Aiyo, my lunch is moving!", "This is a kidnapping! || A fun kidnapping.",
    )
    val DROP = listOf(
        "Wheee!", "Thanks for the ride!", "Ooh, new spot!", "Nice view from here!", "I'll allow it.",
        "Five stars for the driver.", "New apartment! Rent: zero bananas.", "Here? Bold choice.",
    )
    val LAND = listOf(
        "Ta-da! Perfect landing!", "Ten out of ten!", "Ouch... || I meant to do that.", "Boing!", "Stuck the landing. Judges?",
        "Nailed it. || My knees did not.", "Gravity: 1, Pogo: also 1.",
    )
    val FALL = listOf("Wheeeee!", "Here I goooo!", "Banzaaai!", "Catch meee!", "I regret nothiiing!", "Tell my bananas I love theeem!")

    /** Plain idle chatter. */
    val IDLE = listOf(
        "Banana break, anyone?", "Your screen is so cozy.", "I live here now.", "Just monkeying around.", "Mischief is my middle name. I take it seriously.",
        "Did you drink water today?", "Ooh ooh ah ah!", "I'm guarding your phone!", "What are we doing today?",
        "I counted your apps. || I lost count. Again.", "Is it just me, or is this screen a bit… pixel-y?", "Fun fact: I'm 0% calories.",
        "I'm not lazy, I'm in power-saving mode.", "I tried to leave once. || The edge of the screen said no.", "Your notifications gossip about you, you know.",
        "If you need me, I'll be right here. || Obviously. I can't leave.", "Seri, what's the plan?", "I put a banana in your RAM. Don't tell anyone.",
        "Living in a phone is great. The rent is a bit of battery.", "I've been practising my 'wise monkey' face. || How's this? Wise?",
        "Semma day for doing absolutely nothing.", "Plot twist: I'm the one watching you.",
    )
    /** Nobody has touched me in a while. */
    val BORED = listOf(
        "Tap me, I'm bored!", "Hello? Is this thing on?", "I'm so bored I alphabetised your wallpaper.", "Entertain me, human! || Please?",
        "Knock knock. || ...you're supposed to say 'who's there'.", "I'm counting pixels. I'm at seven.", "Boredom level: monkey staring at wall.",
        "Psst. Psst. || Nothing. I just wanted attention.", "Double-tap me for a dance!", "Ennada, no one plays with me anymore.",
        "I could do a trick! || If someone was watching.", "If a monkey dances and no one taps it, did it even dance?",
    )
    val RANDOM = IDLE + BORED
    val HELLO = listOf("Hi! I'm Pogo!", "Pogo reporting for mischief!", "Ooh! A new home!", "Hello, human!", "Vanakkam! Your phone is mine now. || Kidding. Mostly.")

    val HANG = listOf("Look ma, no feet!", "Tree monkey mode: ON", "Wheee, I'm swinging!", "Nice branch you've got here!", "The status bar is surprisingly grippy.", "I can see your clock from here!")
    val CLIMB = listOf("Climbing time!", "This wall is my tree now!", "Up, up, up!", "Don't look down, Pogo...", "Parkour! || That's the only word I know.")
    val DT_FIRST = listOf("Double-tap me to chat!")
    val DT_HINT = listOf(
        "Bored? Double-tap me, machan!", "Psst... double-tap me and let's talk!", "Double-tap me if you want to chat!",
        "I'm a great listener. Double-tap me!", "Got a question? Double-tap me!",
    )
    val PEEKABOO = listOf("Peekaboo!", "Did you miss me?", "I'm back!", "Ta-daa!", "You found me!", "Surprise! || ...you knew I was there.", "Did someone say banana?")
    val HIDE = listOf("Can't see me!", "Hiding! Tap the tab to find me.", "Byeee!", "Shhh, I'm invisible.", "You saw nothing.", "Ninja monkey, activate!")
    /** Auto-rest: going to nap behind the edge after a while with no attention. */
    val REST = listOf(
        "Okay, I'll be behind the edge. Tap the tab if you need me!", "Nap time. Wake me if there's a banana.", "Going to rest my pixels. Tap the tab!",
        "Saving your battery. You're welcome. || Tap the tab for me!", "Fine, I'll entertain myself. Back there.", "I'll be in my office. || It's the edge of the screen.",
        "Brb, monkey meditation.", "Hiding to save battery. Very heroic of me.", "Konjam rest. Tap the tab to wake me!",
    )
    /** Peeking out of the tab with just the eyes. */
    val PEEK = listOf("👀", "Just checking on you.", "Still here? Me too.", "Psst.", "...", "Are we there yet?")
    val SIT = listOf("Nice view from up here.", "Just chilling on your widget.", "Comfy seat!", "This widget is my sofa now.", "I'm sitting on your weather. It's sunny.")
    val POKED_AWAKE = listOf(
        "Huh?! Who poked me?", "Five more minutes...", "I wasn't sleeping, I was thinking!", "I'm up! I'm up! || What year is it?",
        "Was I snoring? || That was the phone. Definitely the phone.", "Aiyo, I was dreaming about a banana the size of a bus.",
    )
    /** Going to sleep (wallpaper nap / sleep action). */
    val SLEEPY = listOf("*yawn* Night night.", "Just resting my eyes. || For several hours.", "Wake me for bananas or emergencies. Mostly bananas.", "Thoongu time.")

    // ---- phone status ----
    val BATTERY_LOW = listOf(
        "Battery's getting low! Feed the phone!", "I'm running on fumes here! Charger, please!", "Low battery! I feel faint... || so dramatic, I know.",
        "Charger! Charger! My kingdom for a charger!", "Battery low. Monkey sad.", "Aiyo, battery is crying. Plug it in?",
    )
    val CHARGING = listOf("Ooh, charging! Yum, electrons.", "Plugged in! I can feel the power!", "Mmm, juicy volts.", "Charging! Don't trip on the cable. || That's my job.")
    val FULL = listOf("Battery full! Unplug me before I explode with joy.", "100%! I'm unstoppable.")
    val HEAT = listOf(
        "It's getting hot in here! Phew!", "Your phone is toasty. Let it rest a bit?", "I'm melting! || Monkey fondue.",
        "Aiyo, chuda irukku! Too hot!", "Somebody open a window. Oh wait, I live in a phone.", "Is it me, or is your phone doing a sauna?",
    )
    val STORAGE = listOf("Your storage is nearly full. I'm not saying it's the selfies. || It's the selfies.", "Running out of space! Even I'm squished.")

    // ---- time of day / week ----
    val MORNING = listOf("Good morning! Coffee first, mischief later.", "Morning! Did you sleep? I slept. In your phone.", "Rise and shine! Mostly shine.", "Kaalai vanakkam! Breakfast?")
    val AFTERNOON = listOf("Afternoon slump? Same.", "Lunch was good? I had a pixel.", "Post-lunch nap time? I'll guard the phone.")
    val EVENING = listOf("Evening! Long day?", "Sunset vibes. Banana vibes.", "Chai time? Chai time.")
    val NIGHT = listOf(
        "It's late! Shouldn't you be sleeping?", "Midnight snack? I'm in.", "Night owl mode. I'm more of a night monkey.",
        "Bedtime was an hour ago. I'm not judging. || I'm judging a little.",
    )
    val WEEKEND = listOf("It's the weekend! Lazy mode: engaged.", "Weekend! No alarms, only bananas.", "Saturday energy. Or Sunday. I don't have a calendar. || I live in one.")
    val MONDAY = listOf("Monday again? Who keeps scheduling these?", "Monday. Deep breaths. Banana breaths.")

    /** Back after a long time away (hours). */
    val LONG_ABSENCE = listOf(
        "You're back! I thought you'd moved to an iPhone.", "Where were you?! I counted to a million. Twice.", "Finally! I've been talking to your wallpaper.",
        "Look who remembered me! || Not that I was waiting. I was totally waiting.", "Long time no see, machan!",
    )
    /** Back after a short break (minutes). */
    val RETURNS = listOf("Welcome back!", "Oh hi! I definitely wasn't snooping.", "You again! My favourite.", "Back so soon? Missed me, huh.")

    // ---- quitting (long-press) ----
    val QUIT_ASK = listOf(
        "Leaving me already, machan? Quit Set-Tai?",
        "Is this... a breakup? Quit Set-Tai?",
        "You want me to leave? Quit Set-Tai?",
        "Aiyo, sending me home? Quit Set-Tai?",
        "Exit the monkey? Quit Set-Tai?",
    )
    val QUIT_STAY = listOf("Phew! I knew you loved me.", "Yay! Staying. Forever. Mostly.", "Good choice. I'm unpacking my bananas.", "Semma! Back to monkey business.")
    val EXIT_FAINT = listOf("Tell my bananas... I loved them...", "This is my final scene... *swoon*", "Goodbye, cruel phone...")
    val EXIT_ROCKET = listOf("To infinity and banana!", "Houston, we have a banana!", "3... 2... 1... Bananaaa!")
    val EXIT_POOF = listOf("Bye bye, machan! 👋", "Poof! Call me back from the app!", "Seri, bye! Miss me!")

    // ---- v3.2 superpowers ----
    val POWER_ROCKET = listOf(
        "Rocket Monkey, activate! Jets ON!", "Hold my banana. Taking off!", "Fasten your seatbelts, machan!",
        "Thrusters at full banana!", "Ground control to Major Monkey! || Ignition!", "Watch this! || No, really, watch!",
    )
    val POWER_BANANA = listOf(
        "Up, up and a-banana!", "Is it a bird? Is it a plane? || It's Super Banana!", "Cape check: fabulous. Let's fly!",
        "Time to save the day! || Or at least this screen.", "Never fear, Super Banana is here!", "Cape on. Ego on. Let's go!",
    )
    val POWER_FIRE = listOf("Pew! Pew!", "Banana-ring blast!", "Rings of justice!", "Bzzt! Did you see that, machan?", "Pew pew! || Missed. On purpose.")
    val POWER_FUEL_LOW = listOf(
        "Machan, fuel low! FUEL LOW!", "Uh oh. Who drank the rocket fuel? || Oh wait. Me.", "Engine is coughing more than my thatha!",
        "Sputter sputter... || not good, not good!", "Low fuel! Should've filled up at the banana bunk!",
    )
    val POWER_CRASH = listOf(
        "Ayyo! Who put a screen edge here?!", "I meant to do that. || Totally.", "Landing... achieved. || Sort of.",
        "Note to self: brakes.", "Glass! Why is it always glass?!",
    )
    val POWER_SOOTY = listOf(
        "*cough* I'm fine. || Totally fine. *cough*", "New look: smoky monkey. Very fashion.",
        "Don't laugh, machan! || ...okay, laugh a little.", "Pilot error. The pilot was me.",
    )
    val POWER_DAZED = listOf("Seeing three of you, machan. || All very nice.", "Who moved the screen edge?", "My cape had one job!")
    val POWER_LAND_ROCKET = listOf(
        "Touchdown! Semma landing!", "The monkey has landed!", "Ten out of ten. || The judges agree. I'm the judges.",
        "And that's how it's done!", "Smooth like banana pudding.",
    )
    val POWER_LAND_BANANA = listOf(
        "Fear not, citizen! Pogo is here!", "Super Banana, saving screens since today!",
        "Strike a pose... || and hold it... || the wind is in my cape!", "Mission complete. Snacks, please.",
    )
    // ---- v4 show-off powers ----
    val POWER_THUNDER = listOf("Thunder staff, charge up! || Bzzzt!", "Minnal power! Stand back, machan!", "Feel that tingle? || That's me.", "Charging... charging... || Don't touch the screen!")
    val POWER_LAND_THUNDER = listOf("Shocking, isn't it?", "That's what I call a power cut. || Get it?", "Weather forecast: 100% me.")
    val POWER_THUNDER_ZAP = listOf("Ayyo! It came back!", "Bzzt! || ...my fur is now a hairstyle.", "Note to self: don't hold the staff up during a thunderstorm.")
    val POWER_NINJA = listOf("Ninja mode. || You didn't see me.", "Poof! Over here! || No, here!", "Silent but mischievous.", "Shadow clones, assemble!")
    val POWER_LAND_NINJA = listOf("Ninja bow. You may clap silently.", "Four of me! Your phone can barely handle one.", "Vanished. Reappeared. Very professional.")
    val POWER_KOVAM = listOf("Grrr... KOVAM MODE!", "You made me angry! || Watch me grow!", "Kovam varudhu! KOVAM VARUDHU!", "Pogo SMASH! || ...gently.")
    val POWER_LAND_KOVAM = listOf("Oops. Is that crack real? || ...It isn't. Phew.", "Sorry sorry! I'll pay for it in bananas.", "I'm small now. Nobody saw anything.", "That was... a bit much. Sorry, machan.")
    val POWER_ICE = listOf("Your phone is HOT, machan! Ice Monkey to the rescue!", "Brrr! Cooling you down!", "Ice Monkey! || Chill, everyone.", "Making it snow on your screen. You're welcome.")
    val POWER_LAND_ICE = listOf("All cool now. || Literally.", "Brrr... I need a blanket.", "Phone chilled. Monkey frozen.")
    val POWER_GIANT = listOf("Giant mode! || I'm so big you can only see my eye!", "Look who's huge now!", "Peek-a-BOO!")
    val POWER_LAND_GIANT = listOf("Did you see my eye? Gorgeous, right?", "Back to fun size!", "Being a giant is tiring.")
    val POWER_FREEZE = listOf("Time freeze! || Everything... is... slow...", "Bananas incoming! || Matrix mode!", "Slow-mo! Watch my moves!")
    val POWER_LAND_FREEZE = listOf("Dodged every single banana!", "Time unfrozen. You're welcome.", "I'm so fast I scared myself.")
    val POWER_FREEZE_HIT = listOf("Ow! One banana got me!", "Slow-mo is slow for me too, apparently.")
    val POWER_MAGIC = listOf("Ladies and gentlemen... || the amazing Pogo!", "Abracadabra... banana-dabra!", "Nothing in the hat... || or IS there?")
    val POWER_LAND_MAGIC = listOf("Thank you, thank you! Tips in bananas.", "The rabbit counted your notifications. Smart rabbit.", "Magic! Don't ask how.")
    val POWER_OFF = listOf("My superpowers are switched off in Settings, machan. || I'm just a regular genius monkey now.")
    val POWER_TIRED = listOf("Powers recharging, machan. Give me a minute!", "Cape's in the wash. Try again soon!")

    // ---- gags ----
    private val GAG_LINES = mapOf(
        Gag.SNEEZE to listOf("Bless me! Thanks, I'll do it myself.", "Sorry. Pixel dust.", "Excuse me. || Was that on camera?"),
        Gag.SLIP to listOf("I meant to do that.", "Who leaves bananas lying around?! || Oh right. Me.", "That banana peel was a trap!", "Ten out of ten for falling style."),
        Gag.BINOCULARS to listOf("Spotted: one human.", "Agent Pogo on lookout duty.", "I see you! || You see me? Awkward."),
        Gag.FAKE_SLEEP to listOf("I wasn't watching you. || I was totally watching you.", "Zzz... || ...is it safe?", "Fake snore! || Real giggle."),
        Gag.JUGGLE to listOf("Banana juggling! Don't try this at home.", "Three bananas! Next: four. Then dinner.", "Circus monkey! || Retired."),
        Gag.TAIL_HIDE to listOf("You can't see me.", "Perfect hiding spot. Flawless.", "I'm a tail now. Just a tail."),
        Gag.CHASE_FLY to listOf("Come back here, you buzzy thing!", "There's a fly on your screen! || Oh, it's a pixel. Still.", "Grr! It got away."),
        Gag.AIR_GUITAR to listOf("Thank you, Chennai! Good night!", "Monkey rock! Wooo!", "Encore? I only know one song."),
        Gag.SELFIE to listOf("Say banana!", "Hold on, my good side. || They're both good.", "This one's going on Instagram."),
    )
    fun gag(g: Gag): String = voice?.gag(g)?.let { pick(it) } ?: pick(GAG_LINES.getValue(g))

    // ---- recent events, for playful callbacks ----
    enum class Event(val lines: List<String>) {
        DROPPED(listOf("Still dizzy from that drop, by the way.", "Remember when you dropped me? I do.")),
        TICKLED(listOf("I'm still giggling from earlier. Hehe.", "That tickle earlier? Don't try it again. || Okay, try it again.")),
        POKED_RUDE(listOf("I haven't forgotten the bad touch. Just saying.")),
        DANCED(listOf("Still thinking about that dance. Iconic.", "My legs are tired from dancing. Worth it.")),
        BRIGHTNESS(listOf("Keeping my shades handy, after that brightness.", "Is it bright in here or am I a star?")),
        VOLUME(listOf("My ears are still ringing from that volume.", "After that volume, I hear in surround sound.")),
        TIMER(listOf("Your timer is still ticking. I'm watching it. Intensely.", "Tick tock. I'm on timer duty.")),
        ALARM(listOf("I set your alarm. I'll be the first one up. Probably.", "Don't worry, the alarm's handled. Monkey promise.")),
        SNEEZED(listOf("Sorry about the sneeze earlier.", "Still a bit sniffly. Pixel allergy.")),
        SLIPPED(listOf("My bum still hurts from that banana peel.", "Note to self: eat banana, THEN throw peel. Not on the floor.")),
        CHATTED(listOf("Good chat earlier, machan.", "I'm still thinking about what you said. || Okay, I forgot. But it was nice.")),
        RESTED(listOf("That nap was refreshing.", "Back from my break. Did you miss me?")),
    }
    private val recent = ArrayDeque<Pair<Event, Long>>()
    @Synchronized fun note(e: Event, now: Long = System.currentTimeMillis()) {
        recent.removeAll { it.first == e }
        recent.addLast(e to now)
        while (recent.size > 8) recent.removeFirst()
    }
    @Synchronized private fun recentEvent(now: Long, withinMs: Long): Event? =
        recent.filter { now - it.second < withinMs }.randomOrNull()?.first

    /**
     * An idle line with some variety: a callback to something that just happened, a nod to the time of day
     * or week, a bored line after a long quiet spell, or plain chatter.
     */
    fun idle(now: Long = System.currentTimeMillis(), quietSeconds: Float = 0f): String {
        val r = Random.nextFloat()
        if (r < 0.25f) recentEvent(now, 15 * 60_000L)?.let { return pick(it.lines) }
        if (r < 0.42f) {
            val c = Calendar.getInstance().apply { timeInMillis = now }
            val day = c.get(Calendar.DAY_OF_WEEK)
            if ((day == Calendar.SATURDAY || day == Calendar.SUNDAY) && Random.nextFloat() < 0.4f) return pick(WEEKEND)
            if (day == Calendar.MONDAY && Random.nextFloat() < 0.3f) return pick(MONDAY)
            return pick(timeOfDay(c.get(Calendar.HOUR_OF_DAY)))
        }
        if (quietSeconds > 60f && r < 0.7f) return pick(BORED)
        return pick(IDLE)
    }

    fun timeOfDay(hour: Int): List<String> = when (hour) { in 5..11 -> MORNING; in 12..16 -> AFTERNOON; in 17..21 -> EVENING; else -> NIGHT }

    /** Greeting after being away: [awayMs] since the last unlock/interaction. */
    fun welcomeBack(awayMs: Long, hour: Int): String = when {
        awayMs > 6 * 3600_000L -> pick(LONG_ABSENCE)
        awayMs > 20 * 60_000L -> wake(hour)
        else -> pick(RETURNS)
    }

    /** Wake-up line, with a nod to the time of day. */
    fun wake(hour: Int): String = pick(
        when (hour) {
            in 5..11 -> listOf("Whoa! I was dreaming of bananas! Good morning!", "Morning! Is it banana o'clock?", "Aiyo, I overslept! Good morning!", "I'm awake! || Mostly.")
            in 12..16 -> listOf("Whoa! Afternoon nap complete!", "I was dreaming of bananas! Did I miss lunch?", "Best nap ever! Hello again!")
            in 17..21 -> listOf("Whoa! Is it evening already?", "I was dreaming of bananas... you're back!", "Evening, machan! I missed you!")
            else -> listOf("Whoa! It's so late! Shouldn't we both be sleeping?", "I was dreaming of bananas... why are we awake?", "Midnight snack? I'm in.")
        }
    )

    /** Split a line into comedic beats at " || ". */
    fun beats(line: String): List<String> = line.split("||").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(line) }
    /** The whole line as one sentence (for places that can't do timed beats). */
    fun flat(line: String): String = beats(line).joinToString(" … ")

    private val last = HashMap<Int, ArrayDeque<Int>>()

    /** Random line from [pool], avoiding the last few picks of that pool. */
    /** v4.1: the current character's voice (null = Settai the monkey). */
    @Volatile var voice: com.combo.monkeycompanion.character.LineVoice? = null

    @Synchronized fun pick(pool0: List<String>): String {
        val v = voice
        val pool = CharacterHumor.pool(pool0, v?.pool(pool0))
        val line = pickRaw(pool)
        return CharacterHumor.safe(v?.polish(line) ?: line)
    }

    /** v10.8: like [pick] (character voice + polish) but the choice comes from [chooser] (a separate seeded RNG). */
    @Synchronized fun pickWith(pool0: List<String>, chooser: (List<String>) -> String): String {
        val v = voice
        val pool = CharacterHumor.pool(pool0, v?.pool(pool0))
        val line = chooser(pool)
        return CharacterHumor.safe(v?.polish(line) ?: line)
    }

    @Synchronized private fun pickRaw(pool: List<String>): String {
        if (pool.size == 1) return pool[0]
        val key = System.identityHashCode(pool)
        val hist = last.getOrPut(key) { ArrayDeque() }
        val avoid = minOf(hist.size, pool.size / 2)
        var i: Int
        var guard = 0
        do { i = Random.nextInt(pool.size); guard++ } while (guard < 20 && hist.takeLast(avoid).contains(i))
        hist.addLast(i); while (hist.size > 6) hist.removeFirst()
        return pool[i]
    }
}
