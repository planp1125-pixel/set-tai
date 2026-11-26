# Set-Tai: future characters (plan)

Added 26 Nov 2026, after Meenu the mermaid (v10.55). Plan only: **nothing here is built yet**, and each character
needs the owner's go-ahead before work starts.

Five new characters, in build order: **Panda, Ghost, Robot, Alien, Astronaut**. All have round, simple shapes, so they
are easy to draw with code and easy to make cute. Names are suggestions in the same style as Meenu, Gajju and Chittu.

## How every character is built (the behaviour set)

Same recipe as Meenu (see `docs/LIBRARIES-AND-SHOWS.md` § 5 and the v10.55 notes in `HANDOVER.md`):

1. `shared/.../character/<Name>.kt`: `object <Name> : Character("<id>")` with name, noun, tagline, locomotion, wardrobe,
   powers, `adapt()` (poses), `mapAction()` / `mapGag()` fallbacks, voice lines, `identity()` and `body()` prompts.
2. `shared/.../rig/<Name>Renderer.kt`: procedural drawing on the shared rig + its `PeekFace`. No pictures, no frame-loop Random.
3. `shared/.../brain/<Name>Powers.kt`: three powers (first = double-tap signature). No cooldowns.
4. Register the id everywhere: `CharacterRegistry` (+ aliases), `CharacterNames`, `PairProtocol`, `Population`,
   `TouchFeel`, `CharacterProfiles`, `CharacterHumor` (jokes + rest), `ThinkingWords`, `Personality` (traits + snacks),
   `Landings`, sling game (`SlingGameView` / `SlingGameHub`), Rust `mind/settai-mind/lines/<id>.tsv` + `reply.rs` + `lib.rs` test.
5. Tests: update the character counts (app + desktop), add a desktop preview test that writes
   `build/d4/power-<id>-preview.png` (attached to the test build, so the owner can see every pose before installing).

**Cute rules learned from Meenu:** child proportions, Skye's shared face (`HumanRenderer.cuteGirl*`) or big round eyes,
pink cheeks, clothes that cover the body, soft colours, no heavy dark shapes around the face, effects (bubbles,
sparkles) never covering the face.

## 1. Panda ("Bubbu")

A lazy, sleepy baby panda who loves snacks and naps. The easiest one to make.

- **Look**: round white body, black ears, eye patches, arms and legs, pink cheeks. Wardrobe: bamboo stick, little red scarf.
- **Movement**: waddles slowly on the floor (WALK), rolls like a ball when tilted or swiped, sits down to nap often.
- **Personality**: lazy, sweet, always hungry; says "five more minutes" to everything.
- **Main power, Bamboo snack**: pulls out a bamboo stick and munches it happily, crumbs flying.
- **Power 2, Panda roll**: curls into a ball and rolls across the screen, bouncing off the edges.
- **Power 3, Sleepy cloud**: falls asleep on a little cloud that floats up and drifts back down.
- **Special**: yawns when the phone is used late at night; grumpy if poked awake.
- **Snacks**: bamboo shoots, steamed idli, honey.

## 2. Ghost ("Boo-bu")

A tiny, friendly ghost who wants to be scary but is far too cute. Not a horror ghost: soft, round and giggly.

- **Look**: white rounded blob with a wavy bottom edge, big black eyes, pink cheeks, slightly see-through. Wardrobe: little bow, tiny lantern.
- **Movement**: floats everywhere with a slow wobble (FLOAT, FLY), never touches the floor, fades a little when idle.
- **Personality**: shy, playful; says "BOO!" and then giggles; secretly afraid of the dark.
- **Main power, Peekaboo**: vanishes, then pops up somewhere else on the screen with a "BOO!".
- **Power 2, Glow lantern**: lights a soft lantern; floating sparkles drift around it.
- **Power 3, Pass-through**: floats through one screen edge and peeks back in from the other side.
- **Special**: more see-through at night; hides behind the edge when shouted at.
- **Snacks**: marshmallows, vanilla ice cream, cotton candy.

## 3. Robot ("Bolu")

A small, friendly helper robot who loves your phone's battery and beeps now and then.

- **Look**: rounded square head with a screen face (eyes drawn as shapes on the screen), antenna with a blinking light, round body with a battery meter on the chest. Wardrobe: antenna light colour, little bow tie.
- **Movement**: rolls on one wheel on the floor, or hovers with small foot jets; little stop-start machine moves.
- **Personality**: helpful, curious, a bit literal; "Beep boop, computing…" while thinking.
- **Main power, Charging dance**: plugs into a cable and dances while its chest battery fills with a glow.
- **Power 2, Scan mode**: a scanner beam sweeps the screen and shows a fun "report".
- **Power 3, Screen face**: its face screen shows emojis, hearts or a big smile.
- **Special**: the chest battery shows the real phone battery level; the face screen changes with its mood.
- **Snacks**: "battery juice" (filter coffee), murukku "bolts", jalebi "springs".

## 4. Alien ("Zuzu")

A curious little alien who just landed in your phone and wants to learn everything about humans.

- **Look**: round mint-lavender head, three big eyes (or two big eyes + antennae with glowing balls), small body in a silver suit. Wardrobe: antennae glow colour, tiny UFO.
- **Movement**: floats and flies; arrives in a small UFO that beams it down; little hops when excited.
- **Personality**: curious, funny, gets human things wrong ("Why do humans drink hot leaf water?").
- **Main power, UFO beam**: the UFO comes down and its light beam lifts Zuzu (or a sparkly object) up and back.
- **Power 2, Mind sparkle**: antennae glow and send rings of sparkles out across the screen.
- **Power 3, Gravity flip**: floats upside down for a moment, everything wobbles, then flips back.
- **Special**: "studies" the app you open (a funny line about it); waves at the moon at night.
- **Snacks**: anything new to try: pani puri, gulab jamun, "Earth crisps".

## 5. Astronaut ("Chandru")

A brave little astronaut kid in a puffy space suit, for whom your screen is outer space.

- **Look**: white puffy suit with orange patches, round glass helmet showing a cute face, a small backpack. Wardrobe: helmet visor, mission patch, little flag.
- **Movement**: floats slowly in zero gravity (FLOAT, FLY), slow-motion spins, tiny jetpack puffs to change direction.
- **Personality**: brave, dreamy, loves stars and planets; counts down "3, 2, 1…" before everything.
- **Main power, Jetpack zoom**: a jetpack blast zooms it around the screen in a big loop.
- **Power 2, Moon walk**: big slow bouncy steps along the floor, like walking on the moon.
- **Power 3, Planet show**: small planets and stars pop out and orbit around it.
- **Special**: zero gravity when the phone is tilted (drifts slowly instead of rolling); shows stars at night.
- **Snacks**: "space food" (dried mango), rose milk, a tiny packet of chips.

## Build order and effort

| Order | Character | Locomotion | Why this order | Rough effort |
| --- | --- | --- | --- | --- |
| 1 | Panda | Walk + roll | Simplest shapes, reuses floor walking and rolling | 1 release |
| 2 | Ghost | Float | Simple blob body; vanish/reappear already exists (stage fx) | 1 release |
| 3 | Robot | Wheel / hover | Screen face is new drawing work; battery meter uses real battery | 1–2 releases |
| 4 | Alien | Float + fly | New UFO prop and beam | 1–2 releases |
| 5 | Astronaut | Float + fly | Helmet glass over the face, jetpack flight, zero-gravity tilt | 2 releases |

Each character ships as its own release, with the preview picture checked before the APK link is shared.
