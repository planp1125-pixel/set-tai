"""Sequential resumable Plan B runner. The only wait loop is the requested GO gate."""
import argparse,hashlib,json,os,shutil,subprocess,sys,time,traceback
from pathlib import Path
from gemma_common import ROOT,fingerprint

PY=ROOT/'.venv/bin/python'
STEPS=['setup','bakeoff','pick_teacher','generate','filter_split','review','wait_GO','practice_270m','train_1b','eval','convert','README']
def digest(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def write_json(path,obj):
 path.parent.mkdir(parents=True,exist_ok=True);temp=path.with_suffix('.tmp');temp.write_text(json.dumps(obj,indent=2)+'\n');temp.replace(path)

def main():
 p=argparse.ArgumentParser();p.add_argument('--dry-run',action='store_true');p.add_argument('--chats',type=int);p.add_argument('--steps',type=int,default=20);a=p.parse_args()
 count=a.chats or (5 if a.dry_run else 3000)
 if (a.dry_run and count!=5) or (not a.dry_run and count not in (1500,3000)):p.error('Use 5 chats for dry run or 1500/3000 for production')
 conf={'smoke':a.dry_run,'chats':count,'smoke_steps':a.steps if a.dry_run else None,'prompt_fingerprint':fingerprint()}
 state=ROOT/'.pipeline/status.json'
 prior=json.loads(state.read_text()) if state.exists() else {}
 prior_runid=prior.get('run_id')
 if not prior_runid and not a.dry_run and not prior.get('dry_run',False) and prior.get('workspace'):
  prior_workspace=Path(prior['workspace'])
  try:
   if prior_workspace.resolve().is_relative_to((ROOT/'.pipeline/runs').resolve()) and (prior_workspace/'teachers/selected.json').exists():prior_runid=prior_workspace.name
  except OSError:pass
 runid=(prior_runid if not a.dry_run and not prior.get('dry_run',False) else None) or hashlib.sha256(json.dumps(conf,sort_keys=True).encode()).hexdigest()[:16]
 w=ROOT/('.dry-run' if a.dry_run else '.pipeline/runs/'+runid)
 for name in ['logs','data','eval','teachers','done']:(w/name).mkdir(parents=True,exist_ok=True)
 weights=Path.home()/'models/settai-mini'/('smoke/'+runid if a.dry_run else 'runs/'+runid);weights.mkdir(parents=True,exist_ok=True)
 failed=ROOT/'logs/FAILED.txt'
 if failed.exists():failed.replace(failed.with_name('FAILED.previous.txt'))
 started=time.time();current='setup'
 def status(step,percent=None,eta='unknown'):
  write_json(state,{'step':step,'percent':round(percent if percent is not None else STEPS.index(step)*100/len(STEPS),1),'eta':eta,'workspace':str(w),'started':time.time(),'dry_run':a.dry_run,'run_id':runid})
 def command(args,limited=False,total='11.5',host='10.5'):
  args=[str(x) for x in args]
  if limited:args=[str(PY),str(ROOT/'limits.py'),'--gib',host,'--total-gib',total,'--']+args
  subprocess.run(args,cwd=ROOT.parent,check=True)
 def teacher(action,size):
  command([PY,ROOT/'teacher_worker.py',action,'--size',size,'--workspace',w],True,'9.5' if size=='12b' else '11.5','5.5' if size=='12b' else '8.5')
 def stage(name,fn,outputs=(),extra=''):
  nonlocal current
  current=name;status(name)
  marker=w/'done'/f'{name}.json';key_conf=dict(conf)
  if not a.dry_run and name in ('setup','bakeoff','pick_teacher'):key_conf.pop('chats',None)
  key=hashlib.sha256((json.dumps(key_conf,sort_keys=True)+extra).encode()).hexdigest()
  accepted_keys={key}
  if not a.dry_run and name in ('setup','bakeoff','pick_teacher'):
   legacy=dict(conf);legacy['chats']=3000
   accepted_keys.add(hashlib.sha256((json.dumps(legacy,sort_keys=True)+extra).encode()).hexdigest())
  if marker.exists() and json.loads(marker.read_text()).get('key') in accepted_keys and all(Path(x).exists() for x in outputs):return
  fn()
  if not all(Path(x).exists() for x in outputs):raise RuntimeError(name+' did not produce its required outputs')
  write_json(marker,{'key':key,'completed':time.time(),'outputs':[str(x) for x in outputs]})
 def setup():
  for size in (['4b'] if a.dry_run else ['4b','12b']):
   command([PY,ROOT/'download_gemma_teachers.py','--size',size]);shutil.copy2(ROOT/f'teachers/gemma3-{size}.json',w/'teachers')
  if not (ROOT/'vendor/llama.cpp/build/bin/llama-server').exists():
   vendor=ROOT/'vendor';vendor.mkdir(exist_ok=True)
   if not (vendor/'llama.cpp').exists():
    command(['git','clone','https://github.com/ggml-org/llama.cpp.git',vendor/'llama.cpp'])
    revision=json.loads((ROOT/'tools.json').read_text())['llama_cpp_revision']
    command(['git','-C',vendor/'llama.cpp','checkout','--detach',revision])
   subprocess.run(['apt-get','download','glslc','libshaderc1','spirv-headers','vulkan-tools'],cwd=vendor,check=True)
   command(['bash',ROOT/'build_teacher.sh'],True)
  for size in ['270m','1b']:
   if not (Path.home()/f'models/gemma-3-{size}-it/model.safetensors').exists():raise RuntimeError('Missing local Gemma base: '+size)
 def bakeoff():
  if a.dry_run:
   write_json(w/'eval/smoke-bakeoff.json',{'dry_run':True,'full_comparison_executed':False});return
  teacher('bakeoff','4b')
  try:teacher('bakeoff','12b')
  except subprocess.CalledProcessError as e:
   if e.returncode!=75:raise
   write_json(w/'eval/gemma-12b-skip.json',{'reason':'12B exceeded the approximately 10 GB capped configuration','exit_code':75})
 def select():
  if a.dry_run:
   write_json(w/'teachers/selected.json',{'size':'4b','teacher':json.loads((w/'teachers/gemma3-4b.json').read_text()),'counts':{'en':5},'prompt_fingerprint':fingerprint(),'dry_run':True})
   (w/'eval/teacher_bakeoff.md').write_text('# SMOKE ONLY\n\nFull 30-prompt comparison is not executed in dry-run mode. 4B is the smoke fixture.\n');return
  teacher('score','4b')
  import select_gemma_teacher as s
  s.ROOT=w;s.main()
 def adapt_target():
  if a.dry_run:return
  path=w/'teachers/selected.json';selected=json.loads(path.read_text());old=sum(selected['counts'].values())
  if old==count:return
  raw={k:v*count/old for k,v in selected['counts'].items()};scaled={k:int(v) for k,v in raw.items()}
  for key in sorted(raw,key=lambda k:raw[k]-scaled[k],reverse=True)[:count-sum(scaled.values())]:scaled[key]+=1
  selected['counts']=scaled;selected['target_total']=count;write_json(path,selected)
 def generate():
  selection=json.loads((w/'teachers/selected.json').read_text());teacher('smoke-generate' if a.dry_run else 'generate',selection['size'])
 def split():
  import gen_gemma_chats as g
  g.ROOT=w;rows=g.load_rows(w/'data/raw/accepted.jsonl');selection=json.loads((w/'teachers/selected.json').read_text())
  if len(rows)!=count:raise RuntimeError(f'Expected {count} filtered conversations; found {len(rows)}')
  if not a.dry_run:
   from tokenizers import Tokenizer
   tok=Tokenizer.from_file(str(Path.home()/'models/gemma-3-270m-it/tokenizer.json'))
   for r in rows:
    why=g.local_filter(r['messages'],r['language'],r['character'],tok)
    if why:raise RuntimeError('Final filter failed: '+r['id']+' '+why)
  g.finalize(rows,selection)
 def review():
  text=(w/'data/review_samples.md').read_text()
  if a.dry_run:text='# DRY RUN — 5 isolated test chats, not the production review\n\n'+text
  text+='\n\n## Teacher bake-off summary\n\n'+(w/'eval/teacher_bakeoff.md').read_text().split('## Replies and scores')[0]
  (w/'data/samples_for_review.md').write_text(text)
  if not a.dry_run:
   for name in ['chats.jsonl','test.jsonl','stats.json','samples_for_review.md','review_samples.jsonl']:shutil.copy2(w/'data'/name,ROOT/'data'/name)
   shutil.copy2(w/'eval/teacher_bakeoff.md',ROOT/'eval/teacher_bakeoff.md')
 def gate():
  go=w/'data/GO' if a.dry_run else ROOT/'data/GO'
  if a.dry_run:go.touch() # Explicitly authorized isolated smoke test; never creates mini/data/GO.
  status('wait_GO',eta='waiting for mini/data/GO')
  while not go.exists():time.sleep(60)
  write_json(w/'approval.json',{'review_sha256':digest(w/'data/samples_for_review.md'),'GO':str(go),'observed':time.time()})
 def training(practice):
  out=weights/('practice' if practice else 'real');base=Path.home()/('models/gemma-3-270m-it' if practice or a.dry_run else 'models/gemma-3-1b-it')
  if a.dry_run and not practice:
   write_json(w/'eval/real.json',{'dry_run':True,'trained_1b':False,'merged':str(weights/'practice/merged')});return
  args=[PY,ROOT/'train.py','--base',base,'--data',w/'data/chats.jsonl','--out',out,'--report',w/('eval/practice.json' if practice else 'eval/real.json'),'--epochs','1' if practice else '2']
  if practice:args+=['--limit','500']
  if a.dry_run:args+=['--smoke','--steps',str(a.steps),'--length','128']
  command(args,True)
  if not practice:
   final=Path.home()/'models/settai-mini/gemma-3-1b-settai'
   if final.is_symlink():final.unlink()
   elif final.exists():raise RuntimeError('Final model path already exists as a directory; preserve it before replacing')
   final.symlink_to(out/'merged',target_is_directory=True)
 def evaluation():
  tuned=weights/('practice/merged' if a.dry_run else 'real/merged')
  command([PY,ROOT/'eval.py','--base',Path.home()/('models/gemma-3-270m-it' if a.dry_run else 'models/gemma-3-1b-it'),'--tuned',tuned,'--train',w/'data/chats.jsonl','--test',w/'data/test.jsonl','--out',w/'eval/student']+(['--smoke'] if a.dry_run else []),True)
  if not a.dry_run:shutil.copy2(w/'eval/student/report.md',ROOT/'eval/report.md')
 def conversion():
  if a.dry_run:write_json(w/'eval/conversion.json',{'dry_run':True,'conversion_executed':False});return
  command([PY,ROOT/'convert.py','--model',weights/'real/merged','--out',Path.home()/'models/settai-mini','--report',w/'eval/conversion.json'],True)
 def readme():
  stats=json.loads((w/'data/stats.json').read_text()) if (w/'data/stats.json').exists() else {}
  summaries={name:json.loads((w/f'eval/{name}.json').read_text()) for name in ['practice','real','conversion'] if (w/f'eval/{name}.json').exists()}
  text=f'''# Set-Tai Mini Plan B pipeline

Run from the repo root:
```bash
nohup bash mini/run_pipeline.sh >/tmp/settai-mini-launch.log 2>&1 &
bash mini/status.sh
```
Each stage checkpoints and skips validated completed outputs. Logs: `mini/logs/pipeline.log`; failures: `mini/logs/FAILED.txt`. No log polling is needed. `status.sh` prints four lines. The only idle check is the requested silent GO check every 60 seconds.

Review `mini/data/samples_for_review.md` (30 samples: 10 English, 10 Tanglish, 10 Tamil; bake-off summary included), then authorize training:
```bash
touch mini/data/GO
```
The GO file is the training authorization. Remove it before intentionally starting a fresh corpus review. CPU training: 270M/500 chats/1 epoch, then 1B/all training chats/2 epochs, LoRA rank 16, lr 2e-4, context 512. Checkpoints/merged weights stay under `~/models/settai-mini/`. Models, tokens, GO, caches and logs are never committed. Never merge into main or change tags.

Gemma 3 4B and 12B QAT Q4_0 are downloaded using the existing HF login. Models run sequentially; 12B is skipped on its approximately 10 GB memory guard. A shared local rubric grades the same 30 prompts (not human-validated scores). 12B must beat 4B by >=10%; otherwise 4B wins. Few-shot TSVs are editable; `human_examples.tsv` takes precedence. A language below 1.2/2 overall or naturalness gets half its share; spare share goes to the strongest language. Baseline mix 65/25/10; 30 Hindi chats are reserved from English. All actual conversations are locally generated; the explicitly authorized authored few-shots are context only.

Assistant-only SFT masks user/system tokens, saves resumable optimizer checkpoints and merges LoRA. Evaluation compares base/tuned CPU replies on 40 held-out prompts and flags large training-text overlaps. Google LiteRT Torch 0.10.0 exports int4/int8 with context 1024 in an isolated CPU environment. Exact conversion errors are recorded; GGUF is exported as fallback before the pipeline fails. Nothing is claimed phone-tested.

Quick isolated test:
```bash
bash mini/run_pipeline.sh --dry-run --chats 5 --steps 20
```
This generates five local English chats and runs 20 real 270M CPU steps in an ignored workspace. It auto-creates only its isolated GO file, substitutes 270M for the downstream evaluation fixture, and does not execute 1B training or real Android conversion. Production GO/data are untouched.

Latest run: `{runid}`; dry run: `{a.dry_run}`; elapsed so far: {time.time()-started:.1f} seconds.
Counts: `{json.dumps(stats.get('languages',{}))}`; train/test: {stats.get('train','pending')}/{stats.get('test','pending')}.
Run artifacts: `{w}`. Weight root: `{weights}`.
'''
  text+='\nMeasured stage summaries:\n```json\n'+json.dumps(summaries,ensure_ascii=False,indent=2)+'\n```\n'
  target=w/'README.md' if a.dry_run else ROOT/'README.md';target.write_text(text)
 try:
  stage('setup',setup,[w/'teachers/gemma3-4b.json'])
  stage('bakeoff',bakeoff,[w/'eval/smoke-bakeoff.json'] if a.dry_run else [w/'data/pilot-gemma-bakeoff/4b.jsonl'])
  stage('pick_teacher',select,[w/'teachers/selected.json',w/'eval/teacher_bakeoff.md'])
  adapt_target()
  stage('generate',generate,[w/'data/raw/accepted.jsonl'])
  stage('filter_split',split,[w/'data/chats.jsonl',w/'data/test.jsonl',w/'data/stats.json'])
  stage('review',review,[w/'data/samples_for_review.md'])
  current='wait_GO';gate() # Never skip the authorization predicate merely because an earlier run passed it.
  data_key=digest(w/'data/chats.jsonl')
  stage('practice_270m',lambda:training(True),[w/'eval/practice.json',weights/'practice/merged/config.json'],data_key+digest(ROOT/'train.py'))
  stage('train_1b',lambda:training(False),[w/'eval/real.json'],data_key+digest(ROOT/'train.py'))
  stage('eval',evaluation,[w/'eval/student/report.md'],data_key+digest(ROOT/'eval.py'))
  stage('convert',conversion,[w/'eval/conversion.json'],digest(ROOT/'convert.py'))
  stage('README',readme,[w/'README.md' if a.dry_run else ROOT/'README.md'])
  status('complete',100,eta='done');write_json(w/'SUCCESS.json',{'dry_run':a.dry_run,'chats':count,'training_steps':a.steps if a.dry_run else None,'run_id':runid});print('Pipeline complete.',flush=True)
 except BaseException:
  failed.write_text('Step: '+current+'\n'+traceback.format_exc());write_json(state,{'step':'FAILED: '+current,'percent':STEPS.index(current)*100/len(STEPS),'eta':'stopped','workspace':str(w)})
  try:readme()
  except Exception:pass
  raise
if __name__=='__main__':main()
