"""Extract grounded sheets from Kotlin. No generated conversations or replies."""
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SRC = ROOT.parent / 'shared/src/main/kotlin/com/combo/monkeycompanion'
# Voice vocabulary is taken from existing character lines, not invented dialogue.
VOCAB = {
 'devil': ('Tutty', 'Hee-hee; psst; magic trick; vanakkam; mischief', 'Scary threats, cruel tricks, real-world magic claims.'),
 'angel': ('Angel', 'sparkle; halo; kind; glow; hi hi', 'Claims of curing illness, religious preaching, blaming the human.'),
 'monkey': ('Pogo', 'machan; semma; aiyo; seri; bananas', 'A banana joke in every answer or jokes at someone’s distress.'),
 'superboy': ('Zoom', 'hero; cape; emergency; machan; aiyo', 'Claims to be a famous superhero or genuinely invulnerable.'),
 'armour': ('Bolt', 'filter coffee; suit; jets; core; seri', 'Claims to be Iron Man or to have changed phone settings without tools.'),
 'mask': ('Masko', 'machan; semma; whistle podu; climax', 'Claims to be a famous film character or cruel put-downs.'),
 'billa': ('Billa', 'meow; purr; machan; scheme; catnap', 'Claims to be Tom, real harm to Chittu, monkey or banana jokes.'),
 'chittu': ('Chittu', 'squeak; semma; tiny paws; big brains; masala cheese', 'Claims to be Jerry, dangerous pranks, real harm to Billa.'),
 'gajju': ('Gajju', 'paaawoo; flap flap; tiny wings; big heart; sugarcane', 'Claims of real elephant powers, banana/monkey identity jokes.'),
 'rocketgirl': ('Skye', 'whoosh; hi hi; giggles; laddoos; fireworks', 'Encouraging unsafe fireworks, fire near people or animals.'),
 'hollowman': ('Echo', 'hee-hee; moonwalk; hat; shadow; dancing', 'Claims to watch the human through a camera or spy invisibly.'),
 'surfer': ('Wavy', 'surf’s up; aloha-vanakkam; waves; Thengai; coconut water', 'Encouraging unsafe swimming or surfing without supervision.'),
 'skater': ('Rollie', 'heyy; yo; kickflip; wheels down; Vadai', 'Encouraging real tricks without protective gear or unsafe stunts.'),
}

def main():
    manifest = {}
    for file in sorted((SRC / 'character').glob('*.kt')):
        text = file.read_text()
        starts = list(re.finditer(r'object\s+(\w+)\s*:\s*Character\("([a-z]+)"\)', text))
        for i, match in enumerate(starts):
            cid = match[2]
            if cid not in VOCAB:
                raise ValueError(f'Unhandled built-in {cid}')
            section = text[match.start():starts[i+1].start() if i+1 < len(starts) else len(text)]
            name, words, never = VOCAB[cid]
            identity = re.search(r'override fun identity\(screen: String\)\s*=\s*"""(.*?)"""', section, re.S)
            if cid == 'monkey':
                persona = (SRC / 'ai/Persona.kt').read_text()
                identity = re.search(r'fun monkeyIdentity\(screen: String\)\s*=\s*"""(.*?)"""', persona, re.S)
            if not identity:
                raise ValueError(f'Missing identity for {cid}')
            ident = identity[1].strip()
            ident = re.sub(r'\$\{CharacterNames\.\w+\}', name, ident).replace('$screen', 'phone screen')
            personality = re.search(r'Personality: (.*)', ident)
            # Preserve actual Kotlin voice lines as evidence only; not conversation training rows.
            evidence = [line.strip() for line in section.splitlines()
                        if re.search(r'private val (HELLO|PAT|RANDOM)', line)]
            if cid in ('superboy', 'armour', 'mask'):
                prefix = {'superboy': 'SB', 'armour': 'AA', 'mask': 'MM'}[cid]
                hero_lines = (SRC / 'speech/HeroLines.kt').read_text()
                evidence = [m.group(0).strip() for m in re.finditer(
                    rf'val {prefix}_(?:HELLO|PAT|RANDOM) = listOf\(.*?\)', hero_lines, re.S)]
                evidence.insert(0, 'Source: shared/.../speech/HeroLines.kt (referenced by Heroes.kt).')
            if cid == 'monkey':
                lines = (SRC / 'speech/Lines.kt').read_text()
                hello = re.search(r'val HELLO = listOf\(.*?\)', lines, re.S)
                evidence = ['Source: Persona.monkeyIdentity and speech/Lines.kt.', hello[0]]
            source = str(file.relative_to(ROOT.parent))
            sheet = f'''# {name} ({cid})

Source: `{source}`. Persona rules: `shared/src/main/kotlin/com/combo/monkeycompanion/ai/Persona.kt`.

## Personality
{personality[1] if personality else ident}

## How it talks
Short, warm, spoken replies: one to four short sentences. Answer the actual message first.
Keep character identity, without forcing a catchphrase into every reply. Match the user's
English, Tanglish, Tamil script, or Hindi. English replies may occasionally use one light
Tamil word. Drop teasing when the person is upset. Be honest about uncertainty.

## Favourite words
{words}. Use sparingly and naturally; translate where needed.

## What it never says
{never}
Never cruel, sexual, hateful, threatening, or mocking personal worries. No claims of accessing
private data, seeing the user, changing settings, sending messages, or performing real-world
actions. On-screen animation is pretend play, not real-world power. Never give dangerous
instructions. Do not claim another character's identity. Monkey/banana humour is Pogo-only.
If asked for unsupported phone actions, explain the limit and offer a simple manual step.

## Existing identity (source evidence)
{ident}

## Existing lines (source evidence, not generated training replies)
''' + '\n'.join(evidence) + '\n'
            (ROOT / 'characters' / f'{cid}.md').write_text(sheet)
            manifest[cid] = {'name': name, 'source': source,
                             'source_sha256': hashlib.sha256(file.read_bytes()).hexdigest(),
                             'sheet_sha256': hashlib.sha256(sheet.encode()).hexdigest()}
    assert set(manifest) == set(VOCAB), (set(VOCAB)-set(manifest))
    (ROOT/'characters/manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
    print(f'Wrote {len(manifest)} grounded character sheets.')

if __name__ == '__main__':
    main()
