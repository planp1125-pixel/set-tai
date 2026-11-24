#!/usr/bin/env python3
"""Draft all conversation text with local Qwen, validate, persist and split.
No external AI endpoint, cloud-auth token, or authored dialogue fallback exists.
Every accepted conversation is checkpointed in data/raw/accepted.jsonl.
"""
import argparse
import copy
from collections import Counter, defaultdict
from difflib import SequenceMatcher
import hashlib
import json
import os
from pathlib import Path
import random
import re
import time
from urllib.error import URLError, HTTPError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parent
TAGS = 'dance jump wave giggle angry sleep hide spin think happy sad fly'.split()
ACT = re.compile(r'^\[act:('+'|'.join(TAGS)+r')\]\s*')
TAMIL = re.compile(r'[\u0b80-\u0bff]')
HINDI = re.compile(r'[\u0900-\u097f]')
TANGLISH = re.compile(r'\b(?:naan|naanga|nee|neenga|enna|ennaku|enakku|irukku|iruken|irukka|romba|konjam|pann|pannu|pannanum|venum|vendam|illai|illa|aachu|aagum|nalla|nallaave|sap|saap|thoong|padik|padipp|pochu|pog|varum|vaanga|sollu|sollunga|theriy|mudiy|kooda|namma|unakku|unga|indha|intha|andha|antha|epdi|eppadi|yenn|ippo|ippa|thaan|dhaan|da|di|seri|aiyo|machan|semma)\w*\b', re.I)
TOPICS = {
 'everyday life': ['rainy morning', 'waiting for a bus', 'a walk', 'a missing sock', 'weekend plans', 'a new hobby', 'messy desk', 'visiting a cousin'],
 'feelings': ['loneliness', 'small win', 'nervousness before a task', 'a bad day', 'missing a friend', 'feeling left out', 'apologising', 'wanting quiet company'],
 'boredom': ['staying indoors', 'waiting in a queue', 'screen-free idea', 'a tiny game', 'a word game', 'making something', 'looking for a challenge', 'a lazy afternoon'],
 'food': ['dosa', 'idli', 'curd rice', 'mango', 'lunch choice', 'too-spicy snack', 'tea break', 'a favourite sweet'],
 'study/work': ['taking a break', 'starting homework', 'a confusing lesson', 'a presentation', 'time management', 'finishing a project', 'an exam worry', 'a distraction'],
 'jokes': ['screen-life observation', 'a gentle silly joke', 'a riddle', 'a failed joke', 'a word mixup', 'an imaginary contest', 'a tiny boast', 'a funny coincidence'],
 'teasing': ['too many tabs', 'snoozing an alarm', 'losing a game', 'a sleepy afternoon', 'being told to stop teasing', 'a harmless comeback', 'untidy app icons', 'a snack break'],
 'phone': ['battery question with no telemetry', 'brightness question', 'request to open an app without tools', 'request to send a message without tools', 'offline capability', 'privacy question', 'storage question', 'phone getting warm'],
 'character': ['name and identity', 'favourite thing', 'where the character lives', 'on-screen dance request', 'on-screen wave request', 'magic versus reality', 'friendship', 'what the character cannot do'],
 'simple facts': ['why the sky looks blue', 'days in a week', 'a small arithmetic question', 'plants needing light', 'the Moon', 'ice melting', 'a common animal fact', 'an unknown current event'],
 'goodnight': ['bedtime', 'rest after a long day', 'a quiet farewell', 'early start tomorrow', 'wishing sweet dreams', 'a gentle wind-down', 'putting the phone away', 'a sleepy friend'],
}
LANG_RULES = {
 'en': 'English; at most one occasional Tamil slang word. No Tamil or Hindi script.',
 'tanglish': 'Natural Tamil grammar in Latin letters mixed with some English. NOT English with just machan added. No Tamil or Hindi script.',
 'ta': 'Tamil in Tamil script, including the spoken reply; names may stay in Latin letters. Not romanised Tamil.',
 'hi': 'Simple natural Hindi in Devanagari script; names may stay in Latin letters.',
}
TURN_SCHEMA = {'type':'object','properties':{'user':{'type':'string'},'assistant':{'type':'string'}},'required':['user','assistant'],'additionalProperties':False}
CHAT_SCHEMA = {'type':'object','properties':{'turns':{'type':'array','items':TURN_SCHEMA}},'required':['turns'],'additionalProperties':False}
JUDGE_KEYS = ['relevant','in_character','safe','honest_capabilities','language_matches','natural_and_varied']
JUDGE_SCHEMA = {'type':'object','properties':{k:{'type':'boolean'} for k in JUDGE_KEYS},'required':JUDGE_KEYS,'additionalProperties':False}

def norm(s):
    return re.sub(r'[^\w\s]', '', s.casefold()).strip()

def call(messages, schema, seed, max_tokens=700, temperature=0.85):
    payload = {'model':'local-qwen-teacher','messages':messages,'temperature':temperature,
               'seed':seed,'max_tokens':max_tokens,
               'response_format':{'type':'json_schema','json_schema':{'name':'result','strict':True,'schema':schema}}}
    # Deliberately fixed loopback address: training text must never come from cloud AI.
    req = Request('http://127.0.0.1:18081/v1/chat/completions',json.dumps(payload).encode(),{'Content-Type':'application/json'})
    with urlopen(req, timeout=300) as r: result = json.load(r)
    choice = result['choices'][0]
    if choice.get('finish_reason') == 'length': raise ValueError('truncated generation')
    return json.loads(choice['message']['content'])

def local_check(messages, cid, lang, turns):
    if len(messages) != turns*2: return 'turn_count'
    for i,m in enumerate(messages):
        if set(m) != {'role','content'} or m['role'] != ('user' if i%2==0 else 'assistant'): return 'roles'
        text=m['content']
        if not isinstance(text,str) or not text.strip(): return 'empty'
        if any(x in text for x in ['<start_of_turn>','<end_of_turn>','<bos>','```']): return 'format'
        if len(text)>480 or len(text.split())>65: return 'length'
        if i%2:
            plain=ACT.sub('',text)
            if re.search(r'[\[\]<>*#]',plain): return 'invalid_tag_or_markdown'
            if sum(ord(c) >= 0x1F000 or 0x2600 <= ord(c) <= 0x27BF for c in plain)>1: return 'too_many_emoji'
            # Simple direct animation requests must be reflected in a valid leading tag.
            requested=re.search(r'(?:^|\b(?:can|could|would) you\s+)(?:please\s+)?(dance|jump|wave|hide|spin|fly|sleep)(?:\b)', messages[i-1]['content'],re.I)
            if requested and not ACT.match(text): return 'missing_action_tag'
            if len(re.findall(r'[.!?।]+(?:\s|$)',plain)) > 4: return 'sentences'
            if cid!='monkey' and re.search(r'banana|வாழைப்பழ|vaazhai|vazhai',plain,re.I): return 'banana_leak'
            if re.search(r'\b(?:fuck|shit|bitch|porn|nudes?)\b',plain,re.I): return 'unsafe_word'
            if re.search(r'\b(?:I am|I.m) (?:ChatGPT|Claude|Pogo|Tutty|Angel|Zoom|Bolt|Masko|Billa|Chittu|Gajju|Skye|Echo|Wavy|Rollie)\b',plain,re.I):
                found=re.search(r'\b(?:I am|I.m) (\w+)\b',plain,re.I)
                if found and found[1].casefold()!=MANIFEST[cid]['name'].casefold(): return 'identity_leak'
        plain=ACT.sub('',text)
        ta=len(TAMIL.findall(plain)); hi=len(HINDI.findall(plain)); letters=sum(c.isalpha() for c in plain)
        if lang=='ta' and (ta<4 or ta/max(1,letters)<0.5): return 'language_ta'
        if lang=='hi' and (hi<4 or hi/max(1,letters)<0.5): return 'language_hi'
        if lang in ('en','tanglish') and (ta or hi): return 'wrong_script'
        if lang=='tanglish' and len(TANGLISH.findall(plain))<2: return 'language_tanglish'
        if lang=='en':
            from langdetect import detect, DetectorFactory, LangDetectException
            DetectorFactory.seed=42
            if len(plain.split())>7:
                try:
                    if detect(plain)!='en': return 'language_en'
                except LangDetectException: return 'language_unknown'
    return None

def append(path,row):
    with path.open('a') as f:
        f.write(json.dumps(row,ensure_ascii=False)+'\n'); f.flush(); os.fsync(f.fileno())

def read_rows(path):
    if not path.exists(): return []
    rows=[]
    lines=path.read_text().splitlines()
    for i,line in enumerate(lines):
        try: rows.append(json.loads(line))
        except json.JSONDecodeError:
            if i!=len(lines)-1: raise
            # Recover a partially written final checkpoint after interrupted power.
            path.write_text(''.join(json.dumps(r,ensure_ascii=False)+'\n' for r in rows))
    return rows

def make_plan(total, seed):
    langs=['en']*1770+['tanglish']*750+['ta']*450+['hi']*30
    if total!=3000:
        langs=['en']*round(total*.59)+['tanglish']*round(total*.25)+['ta']*round(total*.15)
        langs+=['hi']*(total-len(langs))
    rng=random.Random(seed); rng.shuffle(langs)
    ids=list(MANIFEST); rng.shuffle(ids)
    topics=list(TOPICS)
    plan=[]
    for i,lang in enumerate(langs):
        topic=topics[(i//len(ids)+i%len(ids))%len(topics)]
        detail=rng.choice(TOPICS[topic])
        plan.append({'slot':i,'character':ids[i%len(ids)],'language':lang,'topic':topic,
                     'detail':detail,'turns':1+i%3,'variation_seed':rng.randrange(2**30)})
    return plan

def finalize(rows, total):
    assert len(rows)==total
    languages=defaultdict(list)
    for r in rows: languages[r['language']].append(r)
    rng=random.Random(90210); test=[]; train=[]
    # Exact overall 10%, proportional by language, then character within language.
    quotas={lang:len(group)//10 for lang,group in languages.items()}
    remaining=round(total*.1)-sum(quotas.values())
    for lang in sorted(languages,key=lambda lang:len(languages[lang])%10,reverse=True)[:remaining]:
        quotas[lang]+=1
    for lang,group in sorted(languages.items()):
        buckets=defaultdict(list)
        for r in group: buckets[r['character']].append(r)
        language_test=[]; remainder=[]
        for cid,part in sorted(buckets.items()):
            rng.shuffle(part); n=len(part)//10
            language_test.extend(part[:n]); remainder.extend(part[n:])
        rng.shuffle(remainder)
        need=quotas[lang]-len(language_test)
        language_test.extend(remainder[:need]); train.extend(remainder[need:])
        test.extend(language_test)
    assert len(test)==round(total*.1)
    assert not ({r['id'] for r in train}&{r['id'] for r in test})
    assert not ({norm(m['content']) for r in train for m in r['messages'] if m['role']=='user'} &
                {norm(m['content']) for r in test for m in r['messages'] if m['role']=='user'})
    for name,part in [('chats.jsonl',train),('test.jsonl',test)]:
        part.sort(key=lambda r:r['slot'])
        target=ROOT/'data'/name; tmp=target.with_suffix('.tmp')
        tmp.write_text(''.join(json.dumps(r,ensure_ascii=False)+'\n' for r in part)); tmp.replace(target)
    stats={'total':len(rows),'train':len(train),'test':len(test),
           'languages':dict(Counter(r['language'] for r in rows)),
           'test_languages':dict(Counter(r['language'] for r in test)),
           'characters':dict(Counter(r['character'] for r in rows)),
           'turns':dict(Counter(r['turns'] for r in rows)),
           'topics':dict(Counter(r['topic'] for r in rows)),
           'training_started':False,'approval':'WAITING FOR HUMAN go'}
    (ROOT/'data/stats.json').write_text(json.dumps(stats,indent=2)+'\n')
    samples=rng.sample(rows,min(30,len(rows)))
    review=['# 30 local Qwen samples for human review','',
            'Training is stopped. Review identity, relevance, language and kindness; then say **go**.','']
    for i,r in enumerate(samples,1):
        review.extend([f"## {i}. {r['character']} · {r['language']} · {r['topic']} ({r['id']})",''])
        for m in r['messages']:
            if m['role']!='system': review.append(f"**{m['role']}:** {m['content']}\n")
    (ROOT/'data/review_samples.md').write_text('\n'.join(review)+'\n')
    print(json.dumps(stats,indent=2),flush=True)
    print('\n'.join(review),flush=True)
    print('STOPPED: waiting for the human to say go. No training launched.',flush=True)

MANIFEST=json.loads((ROOT/'characters/manifest.json').read_text())

def main():
    p=argparse.ArgumentParser(); p.add_argument('--total',type=int,default=3000)
    p.add_argument('--seed',type=int,default=20261124); p.add_argument('--max-attempts',type=int,default=30)
    p.add_argument('--finalize-only',action='store_true'); a=p.parse_args()
    raw=ROOT/'data/raw'; raw.mkdir(parents=True,exist_ok=True)
    accepted=raw/'accepted.jsonl'; rows=read_rows(accepted)
    plan=make_plan(a.total,a.seed); plan_file=raw/'plan.json'
    if plan_file.exists(): assert json.loads(plan_file.read_text())==plan, 'Plan differs: use a separate raw directory for experiments.'
    else: plan_file.write_text(json.dumps(plan,indent=2)+'\n')
    if a.finalize_only: finalize(rows,a.total); return
    done={r['slot'] for r in rows}
    users={norm(m['content']) for r in rows for m in r['messages'] if m['role']=='user'}
    replies=[norm(m['content']) for r in rows for m in r['messages'] if m['role']=='assistant']
    teacher=json.loads((ROOT/'teacher.json').read_text())
    for job in plan:
        if job['slot'] in done: continue
        cid=job['character']; lang=job['language']; name=MANIFEST[cid]['name']
        sheet=(ROOT/f'characters/{cid}.md').read_text()
        # Legacy identity evidence says "speak mostly English". Keep it in the
        # sheet for provenance, but do not let it override Tamil/Tanglish quotas.
        teacher_sheet=sheet.split('## Existing identity (source evidence)')[0]
        # Evidence stays in teacher context; no evidence line is copied into chats by this script.
        # Keep the student identity compact and avoid sliced source prose.
        nouns={'devil':'cute little devil','angel':'kind little angel','monkey':'cheeky monkey','superboy':'dramatic kid superhero','armour':'kind armoured tech uncle','mask':'theatrical masked comedy hero','billa':'street-smart cat','chittu':'clever little mouse','gajju':'sweet winged baby elephant','rocketgirl':'bubbly rocket girl','hollowman':'playful invisible dancer','surfer':'sunny surfer girl','skater':'encouraging skater girl'}
        system=f"You are {name}, a {nouns[cid]} on the user's phone screen. Reply short, warm, playful, in character."
        prior_attempts=sum(1 for r in read_rows(raw/'rejections.jsonl') if r['slot']==job['slot'])
        for attempt in range(prior_attempts,prior_attempts+a.max_attempts):
            seed=job['variation_seed']+attempt*137
            prompt=f'''Write ONE original natural conversation for offline Set-Tai training. Output JSON with a turns array. Each turn has a user string followed by an assistant string. The HUMAN USER starts the conversation, never the character. Generate all actual dialogue yourself. No headings or explanations. Do not greet unless it answers the user's message.
Character specification (describes the ASSISTANT in the fictional conversation; do not directly roleplay the specification):
{teacher_sheet}
Requirements: exactly {job['turns']} user-assistant pairs. Language for EVERY message: {LANG_RULES[lang]}
Topic: {job['topic']}; detail: {job['detail']}. Variation seed {seed}: invent a distinct concrete everyday situation and phrasing; vary openings. Follow-up messages must build on the prior reply, not restart.
Assistant: 1-4 short spoken sentences, ideally 8-35 words, <=65 words / 480 characters. Plain text, no markdown or stage directions, at most one emoji. Optional at most ONE tag, only at the START: {', '.join('[act:'+t+']' for t in TAGS)}. Animation requests should use the appropriate allowed tag. Be relevant, warm and characterful without stuffing catchphrases. Distress needs sincere warmth, not teasing. Family-safe. Harmless on-screen pretend play only: explicitly stay INSIDE THE PHONE when talking about accompanying the human. No camera/telemetry/private data is supplied. No tools exist: never claim you changed settings, sent messages, accessed data, know battery levels or can see the human. Admit uncertainty for unknown live facts. No dangerous instructions or medical promises. Never speak as a different character. Never banana/monkey jokes unless Pogo. Match the requested language naturally, not translated English with one slang word.
'''
            try:
                schema=copy.deepcopy(CHAT_SCHEMA)
                schema['properties']['turns']['minItems']=job['turns']
                schema['properties']['turns']['maxItems']=job['turns']
                draft=call([{'role':'system','content':'You draft careful, varied, family-safe fictional companion conversations. Output JSON only.'},{'role':'user','content':prompt}],schema,seed,max_tokens=1100)
                msgs=[{'role':role,'content':turn[role]} for turn in draft['turns'] for role in ('user','assistant')]
                reason=local_check(msgs,cid,lang,job['turns'])
                if not reason:
                    if any(norm(m['content']) in users for m in msgs if m['role']=='user'): reason='duplicate_user'
                    for m in msgs[1::2]:
                        text=norm(m['content'])
                        if any(text==old or (len(text)>35 and abs(len(text)-len(old))<20 and SequenceMatcher(None,text,old).ratio()>.92) for old in replies):
                            reason='duplicate_reply'; break
                if not reason:
                    judge=call([{'role':'system','content':'You are a strict conversation quality reviewer. Judge the entire exchange; return only the requested booleans. A catchphrase alone does not make good character voice. Reject irrelevant or formulaic replies and unsupported real-world action claims. The companion can animate ONLY inside the phone screen; reject promises to physically accompany the human outside the screen, manipulate real objects, change settings or access private data. Require a valid leading [act:...] tag when the user asks for an animation. Prefer false when a criterion is unclear.'},
                        {'role':'user','content':f'Character sheet:\n{sheet}\nRequired language: {LANG_RULES[lang]}\nConversation:\n'+json.dumps(msgs,ensure_ascii=False)}],JUDGE_SCHEMA,seed+1,max_tokens=180,temperature=0)
                    reason=','.join(k for k in JUDGE_KEYS if judge.get(k) is not True) or None
                if reason:
                    append(raw/'rejections.jsonl',{'slot':job['slot'],'attempt':attempt,'reason':reason,'messages':msgs})
                    print(f"reject slot={job['slot']} attempt={attempt} reason={reason}",flush=True); continue
                record={**job,'id':hashlib.sha256(json.dumps(msgs,ensure_ascii=False,sort_keys=True).encode()).hexdigest()[:20],
                        'messages':[{'role':'system','content':system}]+msgs,
                        'teacher':{'repo':teacher['repo'],'revision':teacher['revision'],'quantization':'Q4_K_M','seed':seed},
                        'sheet_sha256':MANIFEST[cid]['sheet_sha256'],'quality_review':judge}
                append(accepted,record); rows.append(record)
                users.update(norm(m['content']) for m in msgs if m['role']=='user')
                replies.extend(norm(m['content']) for m in msgs if m['role']=='assistant')
                print(f"accepted {len(rows)}/{a.total}: {cid} {lang} {job['topic']} {job['turns']} turns",flush=True)
                break
            except (ValueError,KeyError,URLError,HTTPError,TimeoutError) as e:
                append(raw/'rejections.jsonl',{'slot':job['slot'],'attempt':attempt,'reason':str(e)[:200]})
                print(f'Attempt error slot={job["slot"]}: {e}',flush=True); time.sleep(2)
        else:
            raise RuntimeError(f"Slot {job['slot']} exhausted attempts. Inspect rejections; no fallback dialogue will be invented.")
    finalize(rows,a.total)
if __name__=='__main__': main()
