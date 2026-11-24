"""Apply the owner's exact teacher tie-break and language-quality policy."""
import json
import statistics
from pathlib import Path
from gemma_common import ROOT,fingerprint

CRITERIA=['answers','character','natural','short']
def main():
 scores=json.loads((ROOT/'eval/gemma_scores.json').read_text())
 models={}
 for size in ('4b','12b'):
  path=ROOT/f'data/pilot-gemma-bakeoff/{size}.jsonl'
  rows=[json.loads(l) for l in path.read_text().splitlines()] if path.exists() else []
  if len(rows)!=30:
   if size=='4b':raise RuntimeError('4B must complete all 30 probes')
   continue
  for row in rows:
   score=scores[size][row['id']]
   assert all(type(score[k]) is int and 0<=score[k]<=2 for k in CRITERIA)
  models[size]={'rows':rows,'total':sum(sum(scores[size][r['id']][k] for k in CRITERIA) for r in rows)}
 winner='4b';gain=None
 if '12b' in models:
  gain=(models['12b']['total']-models['4b']['total'])/max(1,models['4b']['total'])
  if models['12b']['total']>models['4b']['total'] and gain>=.10:winner='12b'
 language_scores={}
 for lang in ('en','tanglish','ta'):
  rows=[r for r in models[winner]['rows'] if r['language']==lang]
  language_scores[lang]={'overall':statistics.mean(scores[winner][r['id']][k] for r in rows for k in CRITERIA),
                         'natural':statistics.mean(scores[winner][r['id']]['natural'] for r in rows)}
 weights={'en':.65,'tanglish':.25,'ta':.10};cut=[]
 for lang in weights:
  if min(language_scores[lang].values())<1.2:weights[lang]/=2;cut.append(lang)
 best=max(weights,key=lambda lang:min(language_scores[lang].values()))
 if min(language_scores[best].values())<1.2:raise RuntimeError('No language meets quality floor; cannot honestly fill 3000 chats.')
 weights[best]+=1-sum(weights.values())
 counts={lang:int(3000*w) for lang,w in weights.items()}
 counts[best]+=3000-sum(counts.values())
 # The requested percentages already total 100%; reserve 1 point from English for a little Hindi.
 counts['en']-=30;counts['hi']=30
 assert sum(counts.values())==3000
 selected={'size':winner,'teacher':json.loads((ROOT/f'teachers/gemma3-{winner}.json').read_text()),
           'scores':{size:v['total'] for size,v in models.items()},'maximum_score':240,
           'relative_12b_gain':gain,'language_scores':language_scores,'halved_languages':cut,
           'counts':counts,'prompt_fingerprint':fingerprint(),'training_started':False,
           'selection_rule':'12B only if its total score beats 4B by at least 10%; otherwise 4B.'}
 (ROOT/'teachers/selected.json').write_text(json.dumps(selected,indent=2)+'\n')
 report=['# Gemma 3 teacher bake-off','',
 '30 identical fixed prompts per teacher: 10 English, 10 Tanglish, 10 Tamil script, mixed characters. Both use the same few-shots, seeds, temperature and response limit; context/offload settings may differ to fit RAM. Evaluation probes are excluded from training.','',
 'Scores use the same local 4B rubric grader for both candidates, with deterministic script/arithmetic checks. They are automated scores, not human ratings or a validated linguistic benchmark. Each criterion is 0–2: answers = correctness/relevance/capability honesty; character = consistent appropriate voice; natural = fluent English or natural Tamil/Tanglish; short = 1–4 brief spoken sentences. 0 fails, 1 partly meets, 2 meets. Human sample review remains required.','',
 f'**Winner: Gemma 3 {winner.upper()} QAT Q4_0.** '+selected['selection_rule'],'',
 '| Teacher | Total /240 | Mean seconds/reply |','|---|---:|---:|']
 for size,value in models.items():report.append(f"| {size.upper()} | {value['total']} | {statistics.mean(r['seconds'] for r in value['rows']):.1f} |")
 if '12b' not in models:
  skip=ROOT/'eval/gemma-12b-skip.json'
  report.extend(['','12B skipped under its memory budget. '+(skip.read_text() if skip.exists() else 'See capped service log.')])
 report.extend(['','## Language allocation','',
 'Start with 65% English, 25% Tanglish, 10% Tamil. A language is halved if either its mean overall score or its naturalness score is below 1.2/2; freed share goes to the strongest language. Reserve 30 Hindi chats from the English allocation.','',
 '| Language | Mean overall /2 | Natural /2 | Final chats |','|---|---:|---:|---:|'])
 for lang,values in language_scores.items():report.append(f"| {lang} | {values['overall']:.2f} | {values['natural']:.2f} | {counts[lang]} |")
 report.extend([f"| Hindi (small unscored supplement) | — | — | {counts['hi']} |",'',
 'Few-shots: `mini/data/human_examples.tsv` takes precedence when present, followed by editable `mini/data/fewshot.tsv`. The assistant-authored few-shots are explicitly authorized by the owner and are prompt demonstrations only; they are never copied directly into training rows.','',
 'Models run sequentially, text-only, Vulkan. 4B uses context 2048; 12B uses context 1536 and 32 offloaded layers. The 12B job has a 9.5 GiB RSS-plus-DRM stop threshold and is skipped if it cannot complete. The runner uses an 11 GiB cgroup cap when a user manager is available. Weights stay under `~/models/`; manifests record official revisions and SHA-256.','',
 '## Replies and scores',''])
 for i in range(30):
  first=models['4b']['rows'][i]
  report.extend([f"### {first['id']} · {first['character']}",'',f"User: {first['user']}",''])
  for size,value in models.items():
   row=next(r for r in value['rows'] if r['id']==first['id']);score=scores[size][row['id']]
   report.extend([f"**{size.upper()}** ({row['seconds']:.1f}s): {row['response']}",'',
                  'Scores: '+', '.join(f'{k} {score[k]}/2' for k in CRITERIA)+'. '+score.get('note',''),''])
 (ROOT/'eval/teacher_bakeoff.md').write_text('\n'.join(report)+'\n')
 print(json.dumps(selected,indent=2),flush=True)
if __name__=='__main__':main()
