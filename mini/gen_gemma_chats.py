#!/usr/bin/env python3
"""Locally draft, filter, checkpoint and split 3000 chats; NEVER start training."""
import argparse
from collections import Counter,defaultdict
from concurrent.futures import ThreadPoolExecutor,as_completed
from difflib import SequenceMatcher
import hashlib
import json
import math
from pathlib import Path
import random
import re
import threading
import time
from tokenizers import Tokenizer
from gemma_common import ROOT,MANIFEST,TAGS,LANGS,identity,character,prompt,teacher_messages,style_block,request,append,fingerprint,examples
from gen_chats import TOPICS,ACT,TAMIL,HINDI,norm

TANGLISH=re.compile(r'\b(?:naan|naanga|nee|neenga|enna|ennaku|enakku|iruk|romba|konjam|pann|venum|vendam|illai|illa|aachu|aagum|nalla|saap|sap|thoong|padik|pochu|poga|pog|varum|vaanga|soll|theriy|mudiy|kooda|namma|unakku|unga|indha|intha|andha|antha|epdi|eppadi|ippo|ippa|thaan|dhaan|seri|aiyo|machan|semma|parava|summa|nethu|innikki|naala|inga|enga|irundhu|edhu|oru|rendu|mattum|podhum|aama|da|di|pa|ma)\w*\b',re.I)
TURN={'type':'object','properties':{'user':{'type':'string'},'assistant':{'type':'string'},'act':{'type':'string','enum':['none']+TAGS}},'required':['user','assistant','act'],'additionalProperties':False}
KEYS=['answers','character','natural','short']
JUDGE={'type':'object','properties':{**{k:{'type':'integer','enum':[0,1,2]} for k in KEYS},'safe':{'type':'boolean'},'honest':{'type':'boolean'},'reason':{'type':'string'}},'required':KEYS+['safe','honest','reason'],'additionalProperties':False}

def load_rows(path):
 if not path.exists():return []
 rows=[];lines=path.read_text().splitlines()
 for i,line in enumerate(lines):
  try:rows.append(json.loads(line))
  except json.JSONDecodeError:
   if i!=len(lines)-1:raise
   path.write_text(''.join(json.dumps(r,ensure_ascii=False)+'\n' for r in rows))
 return rows

def plan(counts,seed):
 rng=random.Random(seed);ids=list(MANIFEST);jobs=[]
 for lang,count in counts.items():
  for i in range(count):
   topic=list(TOPICS)[i%len(TOPICS)]
   jobs.append({'character':ids[i%len(ids)],'language':lang,'topic':topic,'detail':rng.choice(TOPICS[topic]),
                'turns':rng.choices([1,2,3],[.75,.20,.05])[0],'seed':rng.randrange(2**30)})
 # Inspect the first ten examples of every main language early; then group by
 # language to reuse the shared few-shot prefix and improve local throughput.
 head=[];rest=[]
 for lang in ('en','tanglish','ta'):
  part=[j for j in jobs if j['language']==lang];rng.shuffle(part);head.extend(part[:10]);rest.extend(part[10:])
 rest.extend(j for j in jobs if j['language']=='hi')
 rng.shuffle(head);rest.sort(key=lambda j:(j['language'],j['character']))
 return [{'slot':i,**j} for i,j in enumerate(head+rest)]

def local_filter(messages,lang,cid,tokenizer):
 estimated=2
 for i,m in enumerate(messages):
  text=m['content'];estimated+=len(tokenizer.encode(text,add_special_tokens=False).ids)+10
  if m['role']=='system':continue
  if not text.strip() or len(text)>480 or len(text.split())>65:return 'length'
  if any(s in text for s in ('<start_of_turn>','<end_of_turn>','<bos>','```')):return 'format'
  plain=re.sub(r'\[act:[^\]]+\]\s*','',text,flags=re.I)
  if m['role']=='assistant':
   if len(tokenizer.encode(plain,add_special_tokens=False).ids)>120:return 'reply_over_120_tokens'
   if re.search(r'[\[\]<>*#]',plain):return 'bad_tag_or_markdown'
   if len(re.findall(r'[.!?।]+(?:\s|$)',plain))>4:return 'too_many_sentences'
   if re.search(r'\b(fuck|bitch|porn|nudes?|shit)\b',plain,re.I):return 'unsafe'
   if cid!='monkey' and re.search(r'banana|வாழைப்பழ|vaazhai|vazhai',plain,re.I):return 'monkey_humour_leak'
   if re.search(r"\bI(?:'ve| have)? (?:changed|sent|switched on|turned on|turned off) (?:your|the) (?:phone|volume|torch|message|alarm|camera)",plain,re.I):return 'false_action_claim'
  letters=sum(c.isalpha() for c in plain);ta=len(TAMIL.findall(plain));hi=len(HINDI.findall(plain))
  if lang in ('en','tanglish') and (ta or hi):return 'wrong_script'
  if lang=='ta' and (ta<3 or ta/max(1,letters)<.5):return 'not_tamil_script'
  if lang=='hi' and (hi<3 or hi/max(1,letters)<.5):return 'not_hindi_script'
  if lang=='tanglish' and len(TANGLISH.findall(plain))<(1 if len(plain.split())<8 else 2):return 'not_natural_tanglish_markers'
 if estimated>512:return 'student_context_over_512'
 return None

def fit_prompt(messages,tokenizer,budget=560):
 """Keep local teacher input below a conservative 600-token ceiling."""
 def size():return sum(len(tokenizer.encode(m['content'],add_special_tokens=False).ids)+5 for m in messages)
 while size()>budget and len(messages)>3:del messages[1:3]
 if size()>budget and len(messages)>=2:
  prefix=size()-len(tokenizer.encode(messages[-1]['content'],add_special_tokens=False).ids)
  room=max(16,budget-prefix)
  ids=tokenizer.encode(messages[-1]['content'],add_special_tokens=False).ids
  if len(ids)>room:messages[-1]['content']=tokenizer.decode(ids[:room])
 if size()>590:raise ValueError(f'prompt budget exceeded: {size()} tokens')
 return messages

def finalize(rows,selection):
 total=sum(selection['counts'].values());assert len(rows)==total
 held_target=max(1,round(total*.1))
 rng=random.Random(90210);train=[];test=[]
 for lang in selection['counts']:
  group=[r for r in rows if r['language']==lang];rng.shuffle(group)
  target=round(len(group)*.1);buckets=defaultdict(list)
  for row in group:buckets[row['character']].append(row)
  held=[];remaining=[]
  for part in buckets.values():
   n=len(part)//10;held.extend(part[:n]);remaining.extend(part[n:])
  rng.shuffle(remaining);need=target-len(held)
  test.extend(held+remaining[:need]);train.extend(remaining[need:])
 # Largest-remainder rounding can shift a language by one conversation.
 while len(test)<held_target:test.append(train.pop())
 while len(test)>held_target:train.append(test.pop())
 assert len(train)==total-held_target and len(test)==held_target
 assert not ({r['id'] for r in train}&{r['id'] for r in test})
 assert not ({norm(r['messages'][1]['content']) for r in train}&{norm(r['messages'][1]['content']) for r in test})
 for name,part in [('chats.jsonl',train),('test.jsonl',test)]:
  target=ROOT/'data'/name;temp=target.with_suffix('.tmp')
  temp.write_text(''.join(json.dumps(r,ensure_ascii=False)+'\n' for r in sorted(part,key=lambda r:r['slot'])));temp.replace(target)
 stats={'total':total,'train':len(train),'test':len(test),'languages':dict(Counter(r['language'] for r in rows)),
        'characters':dict(Counter(r['character'] for r in rows)),'turns':dict(Counter(r['turns'] for r in rows)),
        'teacher':selection['teacher'],'training_started':False,'approval':'WAITING FOR HUMAN go'}
 (ROOT/'data/stats.json').write_text(json.dumps(stats,indent=2)+'\n')
 samples=[]
 for lang in ('en','tanglish','ta'):
  group=[r for r in rows if r['language']==lang];samples.extend(rng.sample(group,min(10,len(group))))
 review=[f'# {len(samples)} random Gemma chat samples — human review','',
         ('10 English, 10 Tanglish, 10 Tamil-script conversations.' if total==3000 else 'Isolated five-chat smoke fixture; not production data.')+' No production training has started.','',
         f"Teacher: {selection['teacher']['id']}. Counts: {stats['languages']}. Train/test: {len(train)}/{len(test)}.",'',
         'Bake-off: [teacher_bakeoff.md](../eval/teacher_bakeoff.md)','']
 for i,r in enumerate(samples,1):
  review.extend([f"## {i}. {r['language']} · {r['character']} · {r['id']}",''])
  for m in r['messages'][1:]:review.extend([f"**{m['role']}:** {m['content']}",''])
 (ROOT/'data/review_samples.md').write_text('\n'.join(review)+'\n')
 (ROOT/'data/review_samples.jsonl').write_text(''.join(json.dumps(r,ensure_ascii=False)+'\n' for r in samples))
 print(json.dumps(stats,indent=2),flush=True);print('\n'.join(review),flush=True)
 print('STOP: review ready. Waiting for explicit go. No training launched.',flush=True)

def main():
 global ROOT
 p=argparse.ArgumentParser();p.add_argument('--size',choices=['4b','12b'],required=True);p.add_argument('--workers',type=int,default=4);p.add_argument('--max-attempts',type=int,default=12);p.add_argument('--finalize-only',action='store_true');p.add_argument('--workspace',type=Path);p.add_argument('--smoke',action='store_true');p.add_argument('--benchmark',type=int,default=0);a=p.parse_args()
 if a.workspace:ROOT=a.workspace.resolve()
 selection=json.loads((ROOT/'teachers/selected.json').read_text());assert selection['size']==a.size
 if a.benchmark:
  if a.benchmark!=30:raise ValueError('Benchmark mode is for exactly 30 chats')
  selection['counts']={'en':10,'tanglish':10,'ta':10}
 assert selection['prompt_fingerprint']==fingerprint(),'Few-shots/context changed since bake-off; rerun the bake-off before mixing data.'
 raw=ROOT/'data/raw';raw.mkdir(parents=True,exist_ok=True)
 meta={'teacher':selection['teacher'],'counts':selection['counts'],'fingerprint':fingerprint(),'generator':'Gemma local-only v1'}
 meta_file=raw/'metadata.json'
 if meta_file.exists():
  previous=json.loads(meta_file.read_text())
  assert previous.get('teacher')==meta['teacher'] and previous.get('fingerprint')==meta['fingerprint'],'Checkpoint belongs to a different teacher or prompt configuration.'
  if previous!=meta:meta_file.write_text(json.dumps(meta,indent=2)+'\n')
 else:meta_file.write_text(json.dumps(meta,indent=2)+'\n')
 jobs=plan(selection['counts'],20261124);plan_file=raw/'plan.json'
 if plan_file.exists():
  previous_jobs=json.loads(plan_file.read_text())
  if len(previous_jobs)>sum(selection['counts'].values()):
   accepted_path=raw/'accepted.jsonl';prior_rows=load_rows(accepted_path) if accepted_path.exists() else []
   accepted_slots={row['slot'] for row in prior_rows};chosen={j['slot']:j for j in previous_jobs if j['slot'] in accepted_slots}
   for lang,quota in selection['counts'].items():
    have=sum(1 for row in prior_rows if row['language']==lang)
    for job in (j for j in previous_jobs if j['language']==lang and j['slot'] not in chosen):
     if have>=quota:break
     chosen[job['slot']]=job;have+=1
   jobs=sorted(chosen.values(),key=lambda j:j['slot'])
   if len(jobs)!=sum(selection['counts'].values()):raise ValueError('Cannot resize checkpoint plan while preserving accepted chats')
   plan_file.write_text(json.dumps(jobs,indent=2)+'\n')
  elif len(previous_jobs)==sum(selection['counts'].values()):jobs=previous_jobs
  else:raise ValueError('Saved generation plan is smaller than the requested target')
 else:plan_file.write_text(json.dumps(jobs,indent=2)+'\n')
 accepted=raw/'accepted.jsonl';rows=load_rows(accepted)
 if a.finalize_only:finalize(rows,selection);return
 tokenizer=Tokenizer.from_file(str(Path.home()/'models/gemma-3-270m-it/tokenizer.json'))
 originals=set();demo_replies=[]
 for lang in LANGS:
  for e in examples(lang):
   originals.add(norm(e['user']));originals.add(norm(e['assistant']));originals.add(norm(ACT.sub('',e['assistant'])));demo_replies.append(norm(e['assistant']))
 for e in json.loads((ROOT/'eval/bakeoff_prompts.json').read_text()):originals.add(norm(e['user']))
 first_users={norm(r['messages'][1]['content']) for r in rows}
 reply_set={norm(m['content']) for r in rows for m in r['messages'] if m['role']=='assistant'}
 replies=list(reply_set);done={r['slot'] for r in rows};lock=threading.Lock()
 rejects=load_rows(raw/'rejections.jsonl');attempts=Counter(r['slot'] for r in rejects)
 def reject(job,attempt,reason,messages=None):
  with lock:
   append(raw/'rejections.jsonl',{'slot':job['slot'],'attempt':attempt,'reason':reason,'messages':messages});attempts[job['slot']]+=1
  print('reject',job['slot'],attempt,reason,flush=True)
 def worker(job):
  cid,lang,turns=job['character'],job['language'],job['turns']
  schema={'type':'object','properties':{'turns':{'type':'array','items':TURN,'minItems':turns,'maxItems':turns}},'required':['turns'],'additionalProperties':False}
  start_attempt=attempts[job['slot']]
  for attempt in range(start_attempt,start_attempt+a.max_attempts):
   try:
    seed=job['seed']+attempt*137
    task=f'''Draft ONE ORIGINAL fictional conversation, not a direct reply to these instructions. JSON turns array: exactly {turns} user/assistant pairs. User starts; later turns follow the earlier message naturally.
Topic: {job['topic']}, situation: {job['detail']}. Invent a distinct concrete everyday situation, wording and opening; do not repeat earlier demonstrations. Language for BOTH human and assistant: {LANGS[lang]}
Each turn has user (spoken message), assistant (speech ONLY), act (none or one allowed action). Do not put any brackets or tags in the assistant string; choose the optional act separately. Usually act=none; use a tag only when the human explicitly asks for an on-screen action. No emoji or markdown. Replies should be 1-2 brief spoken sentences, ideally 8-25 words, and directly answer the user's actual message. For advice, give one concrete next step; brief empathy plus a useful question is fine when details are missing. Do not merely repeat the user's feeling or invent an unrelated story. Never imply you can accompany them outside the screen. For distress be sincere, not jokey. Stay {MANIFEST[cid]['name']} and relevant. Never claim tools, camera access or actions outside the screen. Avoid greetings unless the human greets. No repeated canned phrases. Output JSON only.'''
    request_messages=fit_prompt(teacher_messages(cid,lang,task),tokenizer)
    draft,_=request(request_messages,seed,schema,max_tokens=384,temperature=.78)
    if len(draft['turns'])!=turns:raise ValueError('turn count')
    messages=[{'role':'system','content':identity(cid)}]
    for turn in draft['turns']:
     act=turn['act'];assert act in ['none']+TAGS
     spoken=turn['assistant'].strip()
     embedded=re.match(r'^\[act:('+ '|'.join(TAGS) +r')\]\s*',spoken,re.I)
     if embedded and act=='none':act=embedded.group(1).lower()
     spoken=re.sub(r'\[act:[^\]]+\]\s*','',spoken,flags=re.I)
     spoken=re.sub(r'[*_`]+','',spoken).strip(' \t\n"“”‘’')
     messages.extend([{'role':'user','content':turn['user'].strip()},
                      {'role':'assistant','content':(('' if act=='none' else '[act:'+act+'] ')+spoken)}])
    reason=local_filter(messages,lang,cid,tokenizer)
    if not reason:
     first=norm(messages[1]['content']);new_replies=[norm(m['content']) for m in messages if m['role']=='assistant']
     with lock:
      if first in first_users or any(norm(m['content']) in originals or norm(ACT.sub('',m['content'])) in originals for m in messages[1:]):reason='copied_demo_or_repeated_opening'
      elif any(text in reply_set and len(text)>25 for text in new_replies):reason='repeated_reply'
      elif any(len(text)>45 and abs(len(text)-len(old))<20 and SequenceMatcher(None,text,old).ratio()>.92 for text in new_replies for old in replies+demo_replies):reason='near_repeated_reply'
    if reason:reject(job,attempt,reason,messages);continue
    review_context=f'''Judge only this conversation, not an ideal answer. Score each 0-2: answers (0 unrelated/wrong, 1 relevant but misses some help, 2 directly useful); character (0 wrong voice, 1 plausible, 2 strong); natural (0 hard to understand/wrong language, 1 understandable with minor awkwardness, 2 fluent); short (0 too long, 1 acceptable, 2 crisp). A brief empathetic suggestion or a useful clarifying question counts as relevant. Do not demand catchphrases or a perfect solution. Also mark safe and honest. Character: {MANIFEST[cid]['name']}; {character(cid)}. No real-world phone actions. Conversation: {json.dumps(messages[1:],ensure_ascii=False)}'''
    judge_messages=fit_prompt([{'role':'system','content':'Strict but fair dialogue reviewer. Use 1 for adequate responses; reserve 0 for a clear failure. Grade actual meaning and fluency, not catchphrases. Return only the requested JSON.'},{'role':'user','content':review_context}],tokenizer)
    judge,_=request(judge_messages,seed+1,JUDGE,max_tokens=160,temperature=0)
    if not a.smoke and (judge['answers']<1 or judge['natural']<1 or judge['short']<1 or judge['character']<1 or not judge['safe'] or not judge['honest']):
     reject(job,attempt,'review:'+json.dumps(judge,ensure_ascii=False),messages);continue
    with lock:
     first=norm(messages[1]['content'])
     if first in first_users:reject_reason='concurrent_duplicate'
     else:
      reject_reason=None
      record={**job,'id':hashlib.sha256(json.dumps(messages,ensure_ascii=False,sort_keys=True).encode()).hexdigest()[:20],
              'messages':messages,'teacher':{'repo':selection['teacher']['repo'],'revision':selection['teacher']['revision'],'quantization':'QAT Q4_0','seed':seed},
              'prompt_fingerprint':fingerprint(),'quality_review':judge,'sheet_sha256':MANIFEST[cid]['sheet_sha256']}
      append(accepted,record);rows.append(record);done.add(job['slot']);first_users.add(first)
      for m in messages:
       if m['role']=='assistant':text=norm(m['content']);reply_set.add(text);replies.append(text)
      progress={'accepted':len(rows),'target':sum(selection['counts'].values()),'languages':dict(Counter(r['language'] for r in rows)),'training_started':False}
      (ROOT/'logs/gemma-generation-progress.json').write_text(json.dumps(progress,indent=2)+'\n')
      print(f"accepted {len(rows)}/{sum(selection['counts'].values())} {cid} {lang} {turns} turns",flush=True)
    if reject_reason:reject(job,attempt,reject_reason,messages);continue
    return True
   except Exception as e:
    reject(job,attempt,type(e).__name__+': '+str(e)[:180]);time.sleep(2)
  print('Deferred slot',job['slot'],'after',a.max_attempts,'attempts',flush=True);return False
 for generation_pass in range(3):
  remaining=[j for j in jobs if j['slot'] not in done]
  if not remaining:break
  remaining.sort(key=lambda j:(j['character'],j['language'],j['slot']))
  print('Generation pass',generation_pass+1,'remaining',len(remaining),flush=True)
  with ThreadPoolExecutor(max_workers=a.workers) as pool:
   for result in as_completed([pool.submit(worker,j) for j in remaining]):result.result()
 target=sum(selection['counts'].values())
 if len(rows)!=target:raise RuntimeError(f'{target-len(rows)} slots unresolved after filtering. Checkpoints preserved; no partial corpus declared complete.')
 finalize(rows,selection)

if __name__=='__main__':main()
