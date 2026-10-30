# Settai (sett'AI') v7.0 — The Phone as a World

*Settai's creatures live under the display, and the phone is their world.*

Settai (Tamil for *mischievous*) is an animated 2D habitat that lives inside your Android screen (as a floating overlay or live wallpaper) and on your Ubuntu desktop. Instead of acting like a notification-bar widget or a generic chatbot, Settai's creatures treat the real state of your phone—battery, temperature, apps, notifications, Wi-Fi, storage, and time of day—as their weather, food, places, and visitors, while you are the human visitor looking in through the glass.

* **Android:** package `com.combo.monkeycompanion` (`versionCode = 15`, `versionName = "7.0"`), `minSdk = 26`, `targetSdk = 35`, `isAccessibilityTool = false`.
* **Desktop:** `:desktop` (Kotlin/JVM + Swing/Java2D shim), sharing the exact same `shared/` rig, mind, and world code.

---

## 1. The World Dictionary

Every phone signal has one consistent meaning inside the creatures' habitat:

| Phone Signal | Meaning in Settai's World | How Creatures React |
|---|---|---|
| **Time of day** | Sun & moon (`Dawn`, `Morning`, `Afternoon`, `Evening`, `Night`, `Late night`) | Morning stretches on unlock, 4 PM filter-coffee breaks, post-lunch naps at their favourite corner, sleepy yawns at night |
| **Screen off ($\ge 30\text{ min}$)** | Night / the glass goes dark | Zero-CPU sleep; while you are away, creatures leave **morning traces** in their diary & wake bubble (*"While the glass was dark, Settai & Anna shared a banana near the battery icon"*) |
| **Battery level** | Food & lasting energy (`energy 0..1`) | Full = bold, lively, rocket powers; low ($<25\%$) = lasting hungry, slower walk, droopy eyes, belly-rubbing, huddling together |
| **Charger plugged in** | Feeding time | Leaps up to zap the status-bar battery icon with a big lightning bolt in its own window, then happily munches/absorbs current at the bottom edge |
| **Temperature** | Weather (`heat 0..1`) | Warm/hot weather triggers fanning, seeking shade, and cooling powers (`Ice Monkey` / `Super Breath`) |
| **Apps** | Places the visitor walks into | `WhatsApp` = the noisy market, `YouTube` = the cinema tent, `Swiggy/Zomato/Amazon` = the spice & parcel bazaar, `Camera` = the mirror studio, `Maps` = the watchtower, `Games` = the playground, `Settings` = the engine room, `Chrome/Docs` = the quiet library, `Banking/UPI` = the locked vault (creatures politely turn away & hide) |
| **Notifications** | Visitors & couriers at the gate (`busyness 0..1`) | Couriers arriving with cards; a notification burst is a crowd at the gate (Superboy patrols, Anna raises a shield bubble, Settai juggles cards) |
| **Soft keyboard (IME)** | Visitor typing | **Hard respect override + physical dodge:** never interrupts typing, closes any open bubble, and physically scurries/flies above `imeTopY` so the spacebar and bottom keys are never blocked |
| **Silent / DND / Calls** | Hushed hours (`quiet 0..1`) | Cloth over mouth, tiptoeing, zero loud actions |
| **Wi-Fi / Mobile data** | Bridge to the outside world | Bridge up = messengers cross freely; offline = quiet island mode |
| **Storage / RAM pressure** | Cluttered rooms (`clutter 0..1`) | Slower movement and world-flavoured grumbles about stepping over boxes |
| **Alarms & timers** | Wake-up drumming | Settai drums on the glass from below (*Tap-tap-thump!*) to wake you up |

---

## 2. Population, Characters & Roles

Four procedural 2D characters share the habitat, each with its own rig, locomotion, wardrobe, and world role:

1. **Settai (`monkey`) — Mischief-Maker & Scout:** Climbs walls and ceilings with 2-bone IK limbs and a Verlet tail, sniffs out new apps, juggles visitor cards, and hunts for snacks.
2. **Armour Anna (`armour`) — Chief Engineer:** Hovers on continuous boot and palm jet flames (drawn in her own window), monitors the battery gauge and heat pipes, scolds Settai for nibbling visitor cards, and lands with a jet-brake hover + heavy thud.
3. **Superboy (`superboy`) — Gate & Sky Guard:** Floats and patrols with a fluttering cape, flies sweeping curved loops across 50–90% of the screen on a downward swipe before a fist-first hero touchdown, and guards the gate during notification floods.
4. **Mask Man (`mask`) — Theatrical Wildcard & Jester:** Tries to fly with a flapping umbrella or a crow taxi (`CROW_CARRY`), climbs walls clumsily with comedy slips, hangs by his umbrella handle, and poses in the Camera mirror studio.

### Neighbours on Screen (`Solo / Duo / Trio`)
* Toggle **Solo (1)**, **Duo (2, default)**, or **Trio (3)** directly from the home card.
* **Single-window, single-loop rendering:** Visiting neighbours are drawn inside the **same overlay window and frame loop** as the lead creature—never spawning extra overlay windows or background loops, and costing **0 CPU** when hidden or when the screen is off.
* **`Director` 2-Turn Banter:** When conditions are calm (never during typing, calls, fullscreen, DND, or quiet hours), the Director stages short 2-turn exchanges between neighbours (Anna scolding Settai over a notification card, Superboy & Settai debating top-edge watchtower duty, 4 PM filter coffee, low-battery huddles, charger cheers). When you chat with the lead creature, the neighbour reacts with a pose and a short side-quip.

---

## 3. Lightweight Mind + World Architecture

Settai **never** runs an LLM continuously in the background:

```
shared/src/main/kotlin/com/combo/monkeycompanion/
├── world/     WorldState, WorldClock, WorldPlaces, WorldStore, Population, Director
├── mind/      Drives, Scene, Personality, Habituation, Diary, Mind (utility AI), MindLines
├── brain/     MoodController, Landings, LandingFlight, PhoneMoments, LongPressMenu, Superpowers
├── character/ Character, MonkeyCharacter, Heroes (Superboy, ArmourAnna, MaskMan), Wardrobe
├── rig/       Pose, Animations, MonkeyRig, Climber, MonkeyRenderer, LandingFx, MomentFx, Particles
├── v5/        Festivals, WeatherMood, CoffeeBreak, BananaCatch, StaringContest, Rps, Friendship
└── ai/        Persona, Actions, Brain (Canned, Offline LiteRT-LM, OpenAI, Gemini, Grok, Claude)
```

* **Event-Driven Utility AI (`Mind.kt` + `MindHub.kt`):** Evaluates 15 candidate actions (`IGNORE`, `GLANCE`, `EXPLORE`, `COMMENT`, `NAP`, `GAG`, `POWER`, `LEAVE`, `COME_BACK`, `SEEK_ATTENTION`, `GROOM`, `HABIT`, `RECALL`, `WANDER`, `HIDE`) on app switches, user touches, and a coarse `20–60 s` visible-only heartbeat. Averages **~150 µs per decision** (< 7 ms across an entire day).
* **Continuity & Per-Character Diary (`Diary.kt`, `DiaryActivity.kt`):** Each character keeps a capped on-device diary with exponential salience decay, daily rollups, contextual recall, and cute in-character entries (*Dear diary* / *Hero log* / *Suit log* / *Secret mask diary*).
* **Prompt Injection:** Whenever you open a real AI chat, `ChatHub` injects a one-line `WorldState.promptLine()` summary plus current drives, seeded favourites, and top diary memories so the conversation naturally reflects what happened in the world.

---

## 4. Optional Skills (`Settai's skills`)

Assistant-style powers live under **🛠️ Settai's skills (`SkillsActivity`)**, framed as errands you can ask your creature to run:
* **Errands into other places (Screen agent):** Opt-in Accessibility service (`isAccessibilityTool = false`) for *"what's on screen?"*, *"summarise this chat"*, or *"reply for me"*. Every tap or typed text asks **Yes / No** in Settai's bubble first.
* **World senses & phone powers:** Battery/temp, storage/RAM, alarms & timers (with wake-up glass drumming), torch, app launcher, calendar, contacts, voice talk, and web search.
* **Daily habits (`RoutinesActivity`) & Memory (`MemoryActivity`):** Teach recurring habits or inspect what Settai remembers about you.

---

## 5. Build & Test

```bash
. /workspace/tools/env.sh

# Compile & run the full unit + Robolectric test suite (240+ tests)
./gradlew :app:testDebugUnitTest

# Build Android debug APK (app/build/outputs/apk/debug/app-debug.apk)
./gradlew :app:assembleDebug

# Compile Ubuntu desktop companion
./gradlew :desktop:compileKotlin
```
