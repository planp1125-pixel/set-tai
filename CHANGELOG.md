# Set-Tai changelog

Plain-words history. Older builds (v9.2 – v10.11) are described in detail in [docs/CHANGELOG.md](docs/CHANGELOG.md). Below is a short summary of those, then every tagged release in this repo.

## Before this repo (summary)
- **v9.2 – v9.5 (codes 19–22):** the overlay companion grows up; v9.5 is the official baseline.
- **v10.0 (23):** physics moved into `PhysicsBody` with golden-trace tests. No behaviour change.
- **v10.1 – v10.3 (24–26):** Gajju the flying baby elephant; poking his belly sprays water.
- **v10.4 (27):** Mask Man juggles; shaking him drops the balls.
- **v10.5 – v10.6 (28–29):** the Screen Smash game and its "feel" pass.
- **v10.7 – v10.8 (30–31):** Disney animation principles, living touch reactions, Rocket Boy, fireworks.
- **v10.9 (32):** Rocket Girl, real rocket flight, no power cooldowns, double-tap power, timers that survive interruptions.
- **v10.10 (33):** Rocket Girl rides astride; one double-tap = one whole power.

## v10.11 (code 34)
The chat button stays faint and never covers the face. First version in this repo, plus the CI build workflow (tests, signed APKs, Play AAB).

## v10.12 (code 35)
App renamed **Set-Tai**. Free Duo/Trio crew picker. A skit engine with 7 skits that don't repeat, start late and only use characters that are on screen.

## v10.13 (code 36)
A grouped long-press popup, a chat/speak button (mode chosen in Settings), size options for every character, speech bubbles that avoid faces, a modern look. A real Billa-vs-Chittu chase show, Screen Eater, Selfie Photobomb, Tools. Cute Billa/Chittu redesign, deep-idle 15 fps and a battery-saver nap, a first-time demo and daily tips.

## v10.13.1 / v10.13.1-r2 (code 37)
A smooth full-screen chase stage (no window jumps). Attention only for taps and holds. The on-device brain fits its token budget. A Brain picker (local models + APIs). The Access popup shows live permission state. Popup and chat stay usable while a reply loads (thinking cloud + Stop). `-r2` was a re-tag of the fixed build (tags are never moved).

## v10.13.2 (code 38)
Live model lists for every AI provider, with clear error hints for 404/401.

## v10.14 / v10.14.1 (codes 39–40)
New characters **Hollow Man** (invisible dancer) and **Surfing Girl**. Smoother movement (squash/stretch, arcs, idle variety, finger watching). Rocket Girl's swipe-up jitter fixed. Speech waits until the character has landed. Charger shock for Billa & Chittu, plus phone-moment lines for the new characters.

## v10.15 / v10.15.1 (code 41)
**Body Sense**: the AI gets the character's live body state (position, action, mood, battery, time, friendship) and fading feelings, and can ask for small actions (jump, wave, dance, sleep). It works for local and online models. CI unit-test limit set to 28 min.

## v10.16 (code 42)
New app icon (red, peeking white devil, black oval eyes). New main character **Kutti Satan**, the little devil: vanish in red smoke and pop out of a portal, teleport hops, float a buddy, spell sparks, shadow clones, levitate, soft blinking, a swishing heart-tipped tail. He is the default only on fresh installs.

## v10.16.1 (code 43)
The launcher icon traced from the owner's reference image (foreground + monochrome).

## v10.16.2 (code 44)
The devil's horns curve inward like the icon.

## v10.17 (code 45)
- **Red / black / white theme** across the app (screens, dialogs, buttons, chat bubbles, status bars).
- **Brain on: the AI does the talking.** Canned lines are suppressed, and short spontaneous lines come from Body Sense events (landing, charger, drag, idle, time, mood). They are rate-limited and dropped if the AI is slow. Brain off keeps the scripted lines.
- **More Body Sense feelings:** motion (whoosh), pain/comfort (ouch, cozy), needs (bored, tired, fresh), touch strength (gentle tap vs firm poke, being held), energy, surroundings (keyboard, current app).
- **Kutti Satan gets all powers:** his own magic plus flight, a new fireball throw, juggling, laser eyes, a spin dance and a rocket ride. Choose them from his long-press Powers ring → ☑️ Choose (saved). Double-tap = a random enabled power, vanish first.
- **Why he used to fire Iron Man jets:** his landings came from the shared default landing list, which includes jet landings. He now has his own landings, with jets only when a rocket or flying power is on.
- Built-in characters keep their fixed powers; Studio characters keep their own picker.
- The flaky `RestAndScreenTest` now waits in bounded 1 s steps.
