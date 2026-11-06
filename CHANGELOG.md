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
- Fixed a real bug behind the flaky `RestAndScreenTest`: a timed peek never ended, so the character could get stuck peeking and never rest. Timed peeks now end by themselves.
- The desktop build works with the new theme (`Th.parse` no longer uses Android's `Color.parseColor`).

## v10.17.1 (code 46)
- Buttons are no longer glaring white: soft light grey (#ECECEC) with a subtle border and dark text; red buttons stay red.
- New **Dark mode**: Settings → 🌗 Appearance → Follow phone (System) / Light / Dark (saved). Dark has a near-black background (#0D0D0D), dark cards (#1A1A1A), dark grey buttons (#2A2A2A), light text and red accents. It applies to every app screen; the overlay character is never affected.

## v10.17.2 (code 47)
- **GPU for the on-device brain works now.** The app never asked Android for the phone's GPU driver (OpenCL), so "Use GPU" always failed and showed only "My on-device brain hiccuped". The manifest now requests it.
- If a phone's GPU still can't run the model, the app quietly falls back to the CPU instead of failing.
- Loading errors show their real reason instead of "hiccuped".
- "Test on-device brain" now shows how long the reply took and whether it ran on GPU or CPU.

## v10.18 (code 48)
- **Feel the touch (Brain on):** every touch now gets words written by the AI from what the body felt: tickle, head pat, poke, tail grab, hold, pick-up, throw, wall bump, bad touch, being woken up, and the double-tap power. The words depend on mood, time, energy, friendship and how many times in a row.
- **Instant:** in quiet moments the AI prepares a few lines in advance for each kind of touch, so the reaction appears straight away with the animation. Big moments (throws, wall bumps, a 3rd poke in a row, waking him up) also get a fresh AI line a second later, which can include a body action.
- **Never repeats:** each character remembers his last 300 lines and rejects repeats and near-repeats; the AI is told to vary the style (jokes, drama, questions back, Tanglish, playful revenge…).
- Works for every character and every brain. Animations and powers are unchanged, double-tap is still a power, and Brain off keeps the scripted lines.
- How it works, with a flowchart: [docs/REACTION_ENGINE.md](docs/REACTION_ENGINE.md).

## v10.18.1 (code 49)
- **Your chat goes first:** if the brain is busy with background work (preparing touch reactions, idle lines, quips) when you send a message, that work stops at once and your reply starts straight away.
- **New Settings → Background AI: Off / Gentle / Fast.** Gentle (default) only prepares reactions while the phone is resting (no touch or chat for 30 s, no keyboard, battery ≥ 30 % or charging), at most every 10 minutes, in small batches; idle AI lines at most every 5 minutes. This fixes the phone lagging with a big model in v10.18.
- A quick mood change no longer triggers a whole new batch of lines.
- **Faster on-device replies:** the prompt sent to a local model is capped (about half the old size), so the CPU has much less to read before answering.
- The GPU switch now warns that a big model can make the whole phone lag.

## v10.19 (code 50)
- **Friendship heart:** a small, faint heart next to the character (just under the chat button) fills up red as your friendship grows, with the level number inside. It pulses when you earn points and glows darker with "Friendship Lv N!" when you level up. Friendship comes from patting, chatting, games and coming back each day; the level never goes down.
- **Warmth:** besides the level, he has a feeling for today. It cools a little for each day you stay away and warms up when you spend time together. The AI gets it in a short "your bond" line, together with a few things he remembers about you (name, exams, likes), so he can bring them up naturally.
- **Daily hello:** the first time you open your phone each day he waves and says something that fits the time (AI-written with Brain on). It replaces the buddy's separate "good morning", so you don't get two.
- **Streak with a heart:** miss one day and he uses his one free pass a week to save your streak. Miss more and he sulks a little (arms folded, turned away) until you pat him, then forgives you. After 5+ days away he's just happy you're back. Sulks also fade by themselves after a few hours.
- **Goodnight:** once a night after 10 PM he yawns, says goodnight and curls up to sleep.
- **Settings › 💛 Engagement:** your friendship numbers plus switches for the heart, daily hello, streak sulking and goodnight (all on by default).
- No new permissions; everything stays on the phone. No Random used (lines are picked by date), so the GoldenTrace frame stream is untouched. New `BondTest` (12 tests).

## v10.19.1 (code 51)
- The friendship heart now sits just **above his head** instead of under the chat button, so it never hides the 💬 button. It steps aside while a speech bubble is showing.
- **Tap the heart** to check your friendship: a big dark heart pops up, filled red to your progress, with the level number inside and "Friends · 23/100" under it, then fades away after about 3 seconds.

## v10.19.2 (code 52)
- The friendship heart moved to the **upper corner beside his head** (top-right or top-left, on the same side as the chat button), with the 💬 button lower down, so the two never overlap. Tapping it still pops up the big level heart.

## v10.20 (code 53): the core, touchable cartoon comedy
- **Toss him and he hits things, for every character.** Lift him up high and throw him: he flies across the screen, spinning.
  - Into the **left or right edge**: SPLAT! He goes flat against the glass like a sticker, wobbles, slides down the glass, peels off top-first, falls, sees stars, and shakes it off ("My face is a dosa now 🫓").
  - Up into the **top edge**: THWACK! A head-first squash under the edge, then he drops, dizzy.
  - Down **very hard onto the floor**: BONK! Flat as a chapati, then an accordion spring back up.
  - A comic word pops at the hit, with dust, a buzz and a little crack sound. Catch him in mid-air to stop it.
  - Pushing him sideways from near the floor still makes him duck behind the edge, like before. Flying characters (Kutti Satan, Superboy, Anna…) still fly an arc on a normal fling; throw them really hard to splat them too.
- **The charger shock, for everyone.** The very first time you plug in the charger, any character runs up to the battery icon as usual, and the zap gets him: fur on end, sparks, then a **burnt soot face** with blinking white eyes and smoke puffs that wears off slowly. The next time he remembers: "I remember you, current! Careful this time… 🧤".
- **New poses:** a real sulk (arms folded, head turned away, peeking back now and then) and a big yawn and stretch before goodnight.
- **Daily mischief challenge** (Settings › 💛 Engagement): one per day, like "Toss me at the screen edge 3 times", "Catch me in mid-air 2 times", "Make me crash 4 times" or "Give me 10 pats". He tells you in the morning hello; tap the heart to see your progress. Finishing it gives +25 friendship, with a dance and confetti.
- With Brain on, the AI reacts to every hit (it knows exactly what happened to his body). Brain off uses scripted lines.
- No new permissions. No Random in the new code (GoldenTraceTest safe). New tests: ImpactPlayTest (9), ChallengeTest (6).

## v10.21 (code 54): the Billa & Chittu Show 2.0
Double-tap Billa or Chittu (or pick "show" in the long-press menu) and a real cartoon short plays along the bottom of your screen, 25–40 seconds, with a title card, sound effects, buzzes on the hits and an iris-out "The End".
- **Three episodes, played in turn** (each one a full story: a setup, a chase, a crash, a plan that backfires, a turnaround with heart, a warm ending):
  - **Laddu Thirudan:** Billa naps by a plate of laddus. Chittu tiptoes out of his hole and steals one. One eye opens… a frozen moment… the chase. Billa SPLATs into the wall by the mouse hole. His frying-pan trap boomerangs off the wall into his own face (BONG!). He cries; Chittu stops laughing, hesitates, then shares the laddu. They fall asleep side by side.
  - **Vazhakka Thol:** Billa sneaks up on Chittu, slips on the banana peel Chittu tossed over his shoulder, leaps for him, Chittu leap-frogs over his head and Billa splats into the wall. Chittu brings a plaster for his head. Friends; Chittu rides off on Billa's head.
  - **Pidikka Mudiyaadhu:** Billa overshoots and skids; Chittu runs up the wall to tease him… and slips. Billa hesitates one heartbeat, then dives and catches him. An awkward thank-you, a blush ("Chumma dhaan!"), and the chase restarts the other way round: Chittu chasing Billa with a frying pan.
- **Cartoon timing:** wind-up → zip → dead stop, skids with dust, speed lines, freeze-frame "takes" to you, real pauses before the gag lands.
- **Clear staging:** they're bigger, stay on the floor or one wall, never teleport and never merge into one blob (except on purpose: a catch, a hug, a ride).
- **Speech bubbles above whoever is talking** (no more tiny captions in the middle of the screen).
- **New sounds** (made in code, no samples): pizzicato tiptoes, zip, boing, splat, thud, pan clang and bonk, slide whistle, pop, a sad "wah-wah", a little "ta-da". They follow Settings › Sound effects and stay silent on silent/vibrate. Short buzzes on the big hits (none on silent).
- Drag a character during the show to stop it.
- No new permissions. No frame-loop Random (GoldenTraceTest safe). New test: ShowEpisodeTest (5): no teleports, no overlaps, readable lines, a heart in every story.

## v10.21.1 (code 55): the notification question stops nagging
- "You have N notifications, want a quick summary?" is asked **once**, by one character, not by every character every few minutes.
- It asks again only when **new** notifications arrive (at most every 30 minutes), or, after you tap **Later**, once you've **unlocked the phone again** (and 30 minutes have passed).
- Once you've opened the summary, those notifications are never offered again.
- The summary greeting no longer says "Manik" to people whose name it doesn't know.
- New test: NotifPromptGateTest (4).

## v10.21.2 (code 56): hidden means hidden; solid show
- **Strict rule: a character you hid stays hidden.** If you swipe a character to the edge (or pick "hide"), nothing brings it out by itself any more: not its mind, skits, the charger trip, comfort moments, the YouTube/eye-break watchdog, cricket scores or AI actions. Only your tap or drag on its tab (or something you ask for: the chat, a reminder you set, a power you choose) brings it back. When a character goes to rest by itself, it may still come back as before.
- **The show is no longer see-through.** Android 12+ makes touch-through overlays 80 % transparent, so the show stage now takes touches instead: **tap anywhere to stop the show** (shown on the title card).

## v10.22 (code 57): your own Billa & Chittu play the drama; the surfer really surfs
- **The real characters play it.** No more bigger copies on a separate stage: the drama is played by Billa and Chittu at their own size, **starting from wherever they are** — on the floor, climbing a wall (they climb down), hanging from the top (they let go and drop), on a widget (they hop down), or hidden at an edge (they walk in). At the end they stand where the drama left them. The heart and chat button go away during the drama and come back with them.
- **2,400 different shows.** Each show is made of 5 parts: an opening (snack thief, sneak attack, feather tickle, dance party, ball bonk), a chase (mouse-hole splat, leap-frog, overshoot, wall tease, two holes, spin-out), a plan that backfires on Billa (frying-pan boomerang, banana peel, mousetrap, chilli laddu, bowling ball), a heart moment (shared snack, a plaster, help up, make him laugh) and an ending (sleep side by side, a ride home, "your turn to run!", a bye-bye wave). Every show is different from the one before.
- **No title card, plain English lines** (no Tamil words). Kept: the iris-out ending with "The End". Tap anywhere to stop.
- **Surfing Girl really surfs.** Her big wave is now a real ride along the bottom of the screen: she paddles, pops up, gathers speed, carves up and down the wave face leaning into each turn (the board tilts on its rail, spray off the edge), cuts back, and glides to a soft stop as the water slows her. No more thrusting around the screen edges. The water under her stays still on the screen while she slides over it (it no longer travels with her), fades out at the ends, and leaves a little wake. The spray carve and the aerial (glide up to the lip, then flip) ride the same way.
- New tests: every one of the 2,400 shows is checked (no teleports, no merging into one blob, readable English lines, starts exactly where they are); the surf glide (continuous, carves both ways, soft stop).

## v10.23 (code 58): Angel, Kutti Satan's partner
- **New character: Angel**, a cute little angel girl drawn in the same style as Kutti Satan (round head, big black oval eyes that blink softly), so the two look like a pair. Cream-white body, a gold halo floating above her head, small white feathered wings that flap gently, pink cheeks, a small smile and a little gold trim.
- **Her powers** (fixed): a **healing sparkle** (golden sparkles rise around her with a soft pulse of light) is her main power and always plays first on double-tap; then a **halo spin**, **wing flight** across the screen (fast flaps) and a **gentle float** in a warm glow. No cooldowns.
- She's in the character grid right after Kutti Satan, in the long-press menu and the crew picker, so she can be on screen with him. Soft winged landings, never jets. Her own lines, and with Brain on the AI knows who she is.
- The planned swipe fixes move to the next version.
- No new permissions. No frame-loop Random (her animation is all time-based). New test: V1023AngelTest.


## v10.24 (code 59): every character swipes in its own style; Skater Girl
- **Swipe a character and it travels to the spot you swiped toward, in its own way, and lands there:**
  - **Surfing Girl:** a full surf show on a moving wave with spray: S-turn carves, a tube ride under the curl, an aerial off the lip (a full flip), a cutback with a big spray splash, then a splash landing. No thrust, no jets.
  - **Settai:** hops and dives *into* the screen (shrinks away spinning), then pops back out at the spot with sparkles.
  - **Hollow Man:** moonwalks there with a spin glide in the middle and a freeze pose at the end.
  - **Gajju:** flaps his big ears like wings, fast, and flies there.
  - **Mask Man:** holds up a striped bedsheet, it drops flat… he's gone, then he appears at the spot like a magic trick.
  - **Billa:** butt-wiggle, then a big cat pounce. **Chittu:** dives into a mouse hole and pops out of a new hole at the spot.
  - **Skater Girl:** skates there doing an ollie, a kickflip and a 360 spin, then lands.
  - **Your own Studio characters:** run there.
  - Armour Anna, Superboy and Rocket Girl are unchanged. Kutti Satan's and Angel's black-magic swipes come next.
- **A sideways swipe never splats any more.** Only an upward throw splats. A hard fling at a close edge still hides the character there; a downward swipe works as before.
- **New character: Skater Girl.** A cute curly-haired girl (dark brown curls, lilac jacket over a mint tee, denim shorts, pink high-tops) on a coral skateboard with yellow wheels; she rolls on it and pushes with one foot. Powers (fixed): **kickflip show** (main, first on double-tap), **grind** with sparks, **board spin**. Her own lines and personality. She's last in the crew list, so existing crew picks don't change.
- No new permissions. No frame-loop Random (all trips are time-based). New test: V1024SwipeTest.

## v10.25 (code 60): swipe and flight fixes from the phone test
- **No more jets on characters without a jet power.** Kutti Satan, Angel, Surfing Girl, Gajju and the others fly without thrust or flames; only Armour Anna, Superboy, Rocket Girl (and Kutti Satan with a jet power switched on) show jets. Surfing Girl only surfs.
- **One clear fling rule:** flung toward a side edge (aimed past it, or let go near it) = **hide** there. A long throw from far across the screen = **splat** on that wall. Aimed above the top of the screen = **thwack** on the top: a clear squash under the edge with stars, then the fall and landing. Anything else = the character's own trip to the spot.
- **Mask Man's bedsheet trick** now reappears exactly where you swiped, even up high; then he falls and lands.
- **Hollow Man** and the other swipe trips now play from a real fling (the old toss check used to grab the fling first).
- **Skater Girl's swipe is a full skate show:** push off, ollie, kickflip, manual, heelflip, grind with sparks, wall ride, 360 and a landing pose, with the board flipping and wheels spinning.

## v10.25.1 (code 61): version label, swipe-down and magic hops
- **The app shows its real version** (it read a hard-coded "v10.22"; now it reads the installed version).
- **Swipe down never sends a character up any more.** A hovering character without jets (Surfing Girl, Gajju, Angel, Kutti Satan…) just drops and lands where you let go. Before, every hovering character flew the big curved jet "touch-down" flight, which climbs first.
- **No jets on Kutti Satan or Angel.** No jet landings either (only while Kutti Satan's rocket power is actually firing).
- **No more looping Iron Man arcs.** Only Armour Anna, Superboy and Rocket Girl loop, arc or use jets. Every other character uses its own swipe trip (surf, skate, pounce, bedsheet, dance, ear-flap, smoke hops, run), drops and lands normally, and glides gently on idle patrols. The cause was shared code (the fly-to path, the swipe-down touch-down and the landing picker) that gave every flying body the rocket arc.
- **Kutti Satan and Angel's own swipe:** they vanish in a puff of smoke (red for Kutti Satan, white-gold for Angel), reappear a hop further along the swipe, and repeat 3–5 times (depending on the distance) until they reach the spot.

## v10.26 (code 62): one simple swipe rule, every character with its own power
- **Normal swipe = travel there with its own power.** Swipe any character (not too fast) and it goes to the spot you swiped toward, in its own way, and lands there.
- **Swipe up or down on a flying character = a tour of the screen.** It sweeps up and around the screen in its own style and either lands on the floor (swipe down) or ends at the spot up high (swipe up):
  - **Kutti Satan:** vanishes and teleports in red-smoke hops all along the curve, then lands.
  - **Angel:** graceful wing flight with a golden sparkle trail.
  - **Gajju:** flies like a heavy baby elephant on his first try: frantic flapping, wobbling, a scary dip half way (eyes wide, mouth open), then a happy, relieved landing.
  - **Surfing Girl:** surfs the whole way round.
- **Fast flick = throw.** You feel a small **tick** while dragging when your swipe becomes fast enough to throw. Flicked up = **THWACK** on the top; flicked across from far = **SPLAT** on the wall; flicked down from high up = **PANCAKE** on the floor; flicked at a close side edge = **hide** there. No tick = a normal swipe = travel.
- **Thrust only where it belongs:** jets = **Armour Anna** only. **Superboy** flies with no thrust at all (cape and speed streaks, Superman style). **Rocket Girl** rides her rocket. Nobody else ever shows jets.
- No new permissions. No frame-loop Random (trips and tours are time-based). Tests updated: tours land on the floor / end at the spot, stay on screen, no jets; flick-down pancake; Gajju's dip and wobble; Angel's sparkles.

## v10.26.1 (code 63): brisk power tours; throws switched off for now
- **Faster, Armour-Anna-style moves.** Swipe trips are quicker, and a swipe up or down on a flyer is now a brisk **loop** like Anna's flights: it goes first to the **top middle** of the screen, round a loop, and then **stays up at the top** (swipe up) or comes down and lands (swipe down).
  - **Kutti Satan** does it all by teleporting: he vanishes, appears at the top middle, then pops round the loop in red smoke, and stays at the top.
  - Angel flies the loop with sparkles, Gajju struggles round it, and the surfer surfs it.
- **Throws are off for now**, so you can try every character's powers. A fast flick is just a faster trip with its power. Flicking at a close side edge still hides the character. Turn throws back on in **Settings › "Throw on fast flick"** (SPLAT, THWACK, PANCAKE). The "fast enough" tick only plays when throws are on.

## v10.26.2 (code 64): long-press powers work again
- **Fixed: picking a power from the long-press menu did nothing.** After a swipe trip (above all a flyer resting up at the top after a tour), a power was put on hold "until she lands", and the trip or a grab then cancelled that landing. The held power never ran, and it blocked every later power too.
- Now a power you pick (menu or double-tap) **starts right away from where the character is**: up high, mid-trip, mid-air or on a wall. A held power that loses its landing is dropped, and nothing can block powers for more than 8 seconds.
- New test: every character's menu power starts mid-trip, after a tour and mid-air.

## v10.27 (code 65): Touch Feel — feel them in your hand
Every character now feels like a little body in your hand (the phone's vibration). Switch it off in **Settings › Touch feel**.
- **Weight:** picking one up gives a bump that matches its weight. Gajju is a deep heavy thud, Chittu a tiny tick. While you carry them you feel small ticks (spaced out and deeper for heavy ones).
- **Slingshot:** long-press (the menu opens), then **pull**. A slingshot fork appears where it stood, the band stretches, and you feel a click for every notch. Let go: a snap, and it flies the other way (SPLAT / THWACK / PANCAKE). Rocket Girl's rocket blasts off. A tiny pull just puts it back. Works even with "Throw on fast flick" off.
- **Heartbeat:** long-press and **keep holding still** about a second more. The menu goes, a red heart beats on the chest and you feel "ba-dum". It's slow when asleep and fast when you've just scared them (thrown or shaken). Lift your finger to stop.
- **Purr:** hold and gently stroke back and forth. They stay put, little hearts float up, and the phone purrs (Billa purrs, Chittu squeaks, Kutti Satan pretends he doesn't like it).
- **Tickle:** quick little rubs make them giggle with fluttery buzzes.
- **Shake:** shake one hard and the phone rattles. Let go after a good shake and stars spin round their head (dizzy).
- **Pokes:** belly = soft squish, head / nose = tiny boop, anywhere else = a light tick.
- **Landings:** every landing is a thump: bigger from higher up and for heavier characters (falls, hero landings, power landings, wall splats).

## v10.28 (code 66): built-in cloud brain and each character's own humour
- Give Brain now selects Set-Tai Cloud by default: no key to paste and no Gmail sign-in. Optional own-key/server and on-device brains remain. AI is still strictly opt-in.
- Secret-free Android gateway, anonymous persisted install UUID, short timeouts and local fallback. 40 cloud attempts per UTC day, 8 KB request limit, 200-character replies; only hashed quota records, no chat logs.
- New maintainable Cloudflare Worker source/config/tests in `cloudflare/settai-brain`. Health: https://settai-brain.settai-companion.workers.dev/health . Model is set once in worker.js.
- Fixed monkey humour leaking into other bodies: unoverridden idle/bored pools, shared proactive jokes, banana-break mind fallbacks and generic prompt/error lines. All built-ins get distinct personality-matched jokes; Studio companions use their saved description and relationship. Non-monkey prompts explicitly reject monkey/banana humour.
- Local jokes, dance/movement and existing phone commands bypass AI/network.
- v10.27 Touch Feel, fast-flick throw OFF default, physics, drawing and powers unchanged.
- Deployment caveat: health is green, but the single live Gemini test returned upstream HTTP 400 (gateway 502). Model metadata is reachable; no model was substituted. Android falls back locally while upstream is unavailable.
