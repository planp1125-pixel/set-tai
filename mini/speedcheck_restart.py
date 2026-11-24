#!/usr/bin/env python3
"""Run the finite 30-chat speed check, report it, then restart production."""
import json, os, re, shutil, statistics, subprocess, sys, time, traceback
from pathlib import Path

ROOT=Path(__file__).resolve().parent
PY=ROOT/'.venv/bin/python'
BENCH=ROOT/'.pipeline/speedcheck-30-v2'
REPORT=ROOT/'logs/speedcheck-30.md'
STATUS=ROOT/'.pipeline/status.json'

def write_json(path,obj):
 path.parent.mkdir(parents=True,exist_ok=True);tmp=path.with_suffix('.tmp');tmp.write_text(json.dumps(obj,indent=2)+'\n');tmp.replace(path)

def main():
 ROOT.joinpath('logs').mkdir(exist_ok=True)
 previous=json.loads(STATUS.read_text()) if STATUS.exists() else {}
 source=Path(previous.get('production_workspace',previous.get('workspace','')))
 if not (source/'teachers/selected.json').exists():raise RuntimeError('Cannot find the saved teacher selection for the active run')
 production_runid=previous.get('run_id') or source.name
 for folder in ('teachers','eval','logs','data'): (BENCH/folder).mkdir(parents=True,exist_ok=True)
 for src,dst in ((source/'teachers/selected.json',BENCH/'teachers/selected.json'),(ROOT/'eval/bakeoff_prompts.json',BENCH/'eval/bakeoff_prompts.json')):
  if not dst.exists():shutil.copy2(src,dst)
 started=BENCH/'started_epoch'
 if not started.exists():
  meta=BENCH/'data/raw/metadata.json';started.write_text(str(meta.stat().st_ctime if meta.exists() else time.time())+'\n')
 failed=ROOT/'logs/FAILED.txt'
 if failed.exists():failed.replace(failed.with_name('FAILED.previous.txt'))
 write_json(STATUS,{'step':'speedcheck_30','percent':0,'eta':'measuring 30 chats','workspace':str(BENCH),'production_workspace':str(source),'run_id':production_runid,'dry_run':False,'started':time.time()})
 cmd=[str(PY),str(ROOT/'limits.py'),'--gib','8.5','--total-gib','11.5','--',str(PY),str(ROOT/'teacher_worker.py'),'generate','--size','4b','--workspace',str(BENCH),'--benchmark','30']
 with (ROOT/'logs/speedcheck-30-run.log').open('a') as log:code=subprocess.run(cmd,cwd=ROOT.parent,stdout=log,stderr=subprocess.STDOUT).returncode
 if code:raise RuntimeError(f'30-chat measurement exited {code}; resumable benchmark artifacts are in {BENCH}')
 accepted=[json.loads(x) for x in (BENCH/'data/raw/accepted.jsonl').read_text().splitlines() if x.strip()]
 rejected=[json.loads(x) for x in (BENCH/'data/raw/rejections.jsonl').read_text().splitlines() if x.strip()]
 if len(accepted)!=30:raise RuntimeError(f'Expected 30 measured chats; found {len(accepted)}')
 rate=len(rejected)/max(1,len(rejected)+len(accepted))
 log=(BENCH/'logs/teacher-4b-generate.log').read_text(errors='replace')
 prompt=[(int(n),float(t)) for n,t in re.findall(r'prompt eval time =\s*[\d.]+ ms /\s*(\d+) tokens.*?([\d.]+) tokens per second',log)]
 generation=[(int(n),float(t)) for n,t in re.findall(r'(?<!prompt )eval time =\s*[\d.]+ ms /\s*(\d+) tokens.*?([\d.]+) tokens per second',log)]
 elapsed=max(1,time.time()-float(started.read_text().strip()));eta_h=elapsed/len(accepted)*3000/3600
 device=subprocess.run([str(ROOT/'vendor/llama.cpp/build/bin/llama-server'),'--list-devices'],capture_output=True,text=True).stdout.strip().replace('\n','; ')
 prompt_tps=statistics.mean(t for _,t in prompt) if prompt else 0;gen_tps=statistics.mean(t for _,t in generation) if generation else 0
 prompt_tokens=statistics.mean(n for n,_ in prompt) if prompt else 0
 target=1500 if eta_h>10 else 3000
 lines=[
  f'GPU: {device}; Vulkan0/-ngl99, all layers requested; -np4 continuous batching, 8192 context (2048/slot), 1024 MiB prompt cache.',
  f'Throughput: prompt {prompt_tps:.1f} tokens/s; generation {gen_tps:.1f} tokens/s.',
  f'Average prompt: {prompt_tokens:.0f} tokens/request (cap 560 estimated, hard ceiling 590); filter reject rate {rate:.1%} ({len(rejected)} rejects / 30 accepted).',
  f'Measured 30-chat elapsed: {elapsed/60:.1f} min; projected 3,000-chat ETA: {eta_h:.1f} h; selected target: {target}.',
  'Prompt/cache reuse: requests grouped by character and language; llama.cpp prompt caching enabled; replies capped at 120 tokenizer tokens.'
 ]
 REPORT.write_text('\n'.join(lines)+'\n')
 print('\n'.join(lines),flush=True)
 if rate>.40:raise RuntimeError(f'Filter rejects {rate:.1%}; review prompt/filter changes before generating more chats')
 launcher=ROOT/'run_pipeline.sh';out=(ROOT/'logs/pipeline.log').open('a')
 subprocess.Popen(['nohup','bash',str(launcher),'--chats',str(target)],cwd=ROOT.parent,stdin=subprocess.DEVNULL,stdout=out,stderr=subprocess.STDOUT,start_new_session=True,close_fds=True)
 print('Production pipeline restarted in background with target '+str(target)+'.',flush=True)

if __name__=='__main__':
 try:main()
 except BaseException:
  (ROOT/'logs/FAILED.txt').write_text(traceback.format_exc())
  write_json(STATUS,{'step':'FAILED: speedcheck_30','percent':0,'eta':'stopped','workspace':str(BENCH)})
  raise
