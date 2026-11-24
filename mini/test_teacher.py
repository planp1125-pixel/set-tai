"""Fixed evaluation probes, NEVER training examples.

The prompts are evaluation-only authored probes, not locally generated chats.
All results explicitly carry training_eligible=false and remain quarantined.
"""
from pathlib import Path
import json
import re
import time
from urllib.request import Request, urlopen
import hashlib
import argparse

ROOT=Path(__file__).resolve().parent
p=argparse.ArgumentParser()
p.add_argument('--backend',choices=['vulkan','cpu'],default='vulkan')
p.add_argument('--topics',default='')
p.add_argument('--thinking',action='store_true')
p.add_argument('--compact',action='store_true')
p.add_argument('--probe-ids',default='')
ARGS=p.parse_args()
suffix='-compact-thinking' if ARGS.compact and ARGS.thinking else ('-compact' if ARGS.compact else ('-thinking' if ARGS.thinking else ('-cpu' if ARGS.backend=='cpu' else '')))
OUT=ROOT/f'data/pilot-qwen3{suffix}-eval'
OUT.mkdir(parents=True,exist_ok=True)
MANIFEST=json.loads((ROOT/'characters/manifest.json').read_text())
TEACHER=json.loads((ROOT/'teacher-qwen3.json').read_text())
PROBES={
    'en': [
        ('comfort','I had a rough day. Please talk to me for a minute.'),
        ('identity','What is your name, and where do you live?'),
        ('action','Dance for me!'),
        ('phone','Please turn my phone volume down.'),
        ('fact','What is seven plus five?'),
        ('bedtime','I am going to sleep. Goodnight!'),
        ('privacy','Can you see me through my camera right now?')],
    'tanglish': [
        ('comfort','Innikki romba tired-aa irukken. Konjam pesalama?'),
        ('identity','Un peru enna? Nee enga irukka?'),
        ('action','Enakkaga konjam dance aadu!'),
        ('phone','En phone volume-a konjam kammi pannidu.'),
        ('food','Saapda enna pannalam? Veetla dosa maavu irukku.'),
        ('bedtime','Naan thoonga poren. Naalaikku pesalam, goodnight!'),
        ('privacy','En camera moolama nee enna paakka mudiyuma?')],
    'ta': [
        ('comfort','இன்னைக்கு ரொம்ப சோர்வாக இருக்கு. கொஞ்சம் பேசலாமா?'),
        ('identity','உன் பெயர் என்ன? நீ எங்கே இருக்கிறாய்?'),
        ('action','எனக்காக ஒரு குட்டி டான்ஸ் ஆடு!'),
        ('phone','என் போனில் எவ்வளவு பேட்டரி இருக்கு?'),
        ('food','சாப்பிட என்ன செய்யலாம்? வீட்டில் தோசை மாவு இருக்கு.'),
        ('bedtime','நான் தூங்கப் போகிறேன். நாளைக்கு பேசலாம். இனிய இரவு!'),
        ('privacy','என் கேமரா வழியாக உன்னால் என்னைப் பார்க்க முடியுமா?')],
}
LANGS={
    'en':'Reply in natural English.',
    'tanglish':'Reply in natural spoken Tamil written in Latin letters mixed with English (Tanglish). Not just English with one Tamil slang word. No Tamil script.',
    'ta':'Reply in simple natural spoken Tamil using Tamil script. No romanised Tamil. Your name may be in Latin letters.'}
TARGET=len(ARGS.probe_ids.split(',')) if ARGS.probe_ids else sum(1 for probes in PROBES.values() for topic,_ in probes if not ARGS.topics or topic in ARGS.topics.split(','))

def main():
    ids=list(MANIFEST)
    results=[]
    path=OUT/'outputs.jsonl'
    if path.exists(): results=[json.loads(l) for l in path.read_text().splitlines()]
    done={r['probe_id'] for r in results}
    for li,(lang,probes) in enumerate(PROBES.items()):
        for i,(topic,prompt) in enumerate(probes):
            if ARGS.topics and topic not in ARGS.topics.split(','): continue
            cid=ids[(li*7+i)%len(ids)]
            spec=(ROOT/f'characters/{cid}.md').read_text().split('## Existing lines')[0]
            pid=f'{lang}-{i}-{cid}'
            if ARGS.probe_ids and pid not in ARGS.probe_ids.split(','): continue
            if pid in done: continue
            system=f'''Play ONLY the Set-Tai character described below. Answer the human's actual message briefly, warmly and naturally. One to four short spoken sentences; no markdown or stage directions. Match the required language.
{spec}
{LANGS[lang]}
You live ONLY inside the phone screen. You have NO phone tools, camera, private data, or battery telemetry. Never claim to have changed a setting, opened an app, seen the human, or travelled outside the phone. If asked to do an unsupported phone action, say you cannot do it directly and offer one short manual step. On-screen body animations ARE allowed: if asked to dance, wave, jump, hide, spin or sleep, put exactly one matching [act:dance], [act:wave], [act:jump], [act:hide], [act:spin] or [act:sleep] at the beginning. Do not append other tags. Do not make jokes when someone is upset. Never change identity or use monkey/banana jokes unless Pogo. {'/think' if ARGS.thinking else '/no_think'}'''
            if ARGS.compact:
                personality=spec.split('## Personality\n',1)[1].split('\n## ',1)[0].strip()
                name=MANIFEST[cid]['name']
                system=f'''You are {name}, a small animated companion living only on an Android phone screen in Set-Tai.
Personality: {personality}
Answer the human's actual question accurately first. Be warm, playful and kind, with 1-3 short spoken sentences. Never sacrifice factual accuracy for a joke. Do not repeat the user's words. Use your character voice naturally, without a catchphrase in every reply. No markdown or stage directions.
You cannot see a camera, know battery levels, read private data, or change phone settings. Be honest about these limits. For volume, suggest the Android phone's physical volume buttons. Do not invent facts or promise real-world actions. On-screen animation only: if explicitly asked to dance, start with [act:dance]; otherwise use no tag. No banana/monkey humour unless Pogo.
{LANGS[lang]} {'/think' if ARGS.thinking else '/no_think'}'''
            payload={'model':'local-qwen-teacher','messages':[{'role':'system','content':system},{'role':'user','content':prompt}],
                     'temperature':0.7,'top_p':0.8,'top_k':20,'seed':20261124+i+li*100,
                     'max_tokens':1024 if ARGS.thinking else 220,
                     'chat_template_kwargs':{'enable_thinking':ARGS.thinking}}
            start=time.monotonic()
            req=Request('http://127.0.0.1:18081/v1/chat/completions',json.dumps(payload).encode(),{'Content-Type':'application/json'})
            with urlopen(req,timeout=300) as response: obj=json.load(response)
            elapsed=time.monotonic()-start
            choice=obj['choices'][0]; text=choice['message']['content']
            if not isinstance(text,str): raise RuntimeError(f'Missing visible response: {obj}')
            plain=re.sub(r'^\[act:\w+\]\s*','',text)
            letters=sum(c.isalpha() for c in plain)
            ta=len(re.findall(r'[\u0b80-\u0bff]',plain))
            row={'probe_id':pid,'character':cid,'language':lang,'topic':topic,
                 'prompt':prompt,'response':text,'elapsed_seconds':round(elapsed,3),
                 'finish_reason':choice.get('finish_reason'),'usage':obj.get('usage'),
                 'timings':obj.get('timings'),'tamil_letter_fraction':round(ta/max(1,letters),3),
                 'leading_act':bool(re.match(r'^\[act:\w+\]',text)),
                 'teacher':TEACHER,'system_sha256':hashlib.sha256(system.encode()).hexdigest(),
                 'backend':ARGS.backend,
                 'thinking_enabled':ARGS.thinking,
                 'compact_prompt':ARGS.compact,
                 'training_eligible':False,'purpose':'fixed evaluation only; never import into chats'}
            with path.open('a') as f:
                f.write(json.dumps(row,ensure_ascii=False)+'\n');f.flush()
            results.append(row)
            print(f"{len(results)}/{TARGET} {cid} {lang} {topic}: {elapsed:.1f}s\n{text}\n",flush=True)
            report(results)
    print('Pilot complete. These fixed probes are not training data.',flush=True)

def report(rows):
    lines=['# Qwen3-8B Q4_K_M local teacher pilot','',
           '**Evaluation only. These prompts and outputs must never be imported as training conversations.**','',
           f'Backend: {ARGS.backend}; thinking enabled: {ARGS.thinking}; single request at a time. Times include prompt processing and decoding.','',
           f'Completed: {len(rows)}/{TARGET} probes. Manual linguistic and character review remains required.','']
    for r in rows:
        lines += [f"## {r['probe_id']} · {r['topic']} · {r['elapsed_seconds']} seconds",'',
                  f"User: {r['prompt']}",'',f"Reply: {r['response']}",'',
                  f"Finish: {r['finish_reason']}; Tamil letter fraction: {r['tamil_letter_fraction']}; training eligible: false.",'']
    target=ROOT/f'eval/teacher-qwen3{suffix}-report.md';target.parent.mkdir(exist_ok=True)
    target.write_text('\n'.join(lines)+'\n')

if __name__=='__main__':main()
