"""Foreground teacher tasks; startup is event-driven, never log-tail polling."""
import argparse,json,os,signal,subprocess,sys,threading
from pathlib import Path
from gemma_common import ROOT,request,teacher_messages,fingerprint,append

def main():
 p=argparse.ArgumentParser();p.add_argument('action',choices=['bakeoff','score','generate','smoke-generate']);p.add_argument('--size',choices=['4b','12b'],required=True);p.add_argument('--workspace',type=Path,required=True);a=p.parse_args();w=a.workspace.resolve()
 manifest=json.loads((ROOT/f'teachers/gemma3-{a.size}.json').read_text());ready=threading.Event()
 args=[str(ROOT/'vendor/llama.cpp/build/bin/llama-server'),'--model',manifest['path'],'--alias','local-settai-teacher','--host','127.0.0.1','--port','18081','--device','Vulkan0','--n-gpu-layers','32' if a.size=='12b' else '99','--ctx-size','1536' if a.size=='12b' else '2048','--parallel','1','--batch-size','64','--ubatch-size','32','--cache-ram','0','--fit','off','--jinja']
 log=(w/'logs'/f'teacher-{a.size}-{a.action}.log').open('a');env=os.environ.copy();env['LD_LIBRARY_PATH']=str(ROOT/'vendor/vulkan/usr/lib/x86_64-linux-gnu')+':'+env.get('LD_LIBRARY_PATH','')
 server=subprocess.Popen(args,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1,env=env)
 def drain():
  for line in server.stdout:
   log.write(line);log.flush()
   if 'listening on' in line:ready.set()
  ready.set()
 thread=threading.Thread(target=drain,daemon=True);thread.start()
 try:
  if not ready.wait(180) or server.poll() is not None:raise RuntimeError('Teacher startup failed; see isolated teacher log')
  if a.action=='bakeoff':
   os.environ['SETTAI_TEACHER_GPU_LAYERS']='32' if a.size=='12b' else '99'
   os.environ['SETTAI_TEACHER_CONTEXT']='1536' if a.size=='12b' else '2048'
   import bakeoff_gemma as b
   b.ROOT=w;sys.argv=['bakeoff','--size',a.size];b.main()
  elif a.action=='generate':
   import gen_gemma_chats as g
   sys.argv=['generate','--workspace',str(w),'--size',a.size];g.main()
  elif a.action=='smoke-generate':
   schema={'type':'object','properties':{'conversations':{'type':'array','minItems':5,'maxItems':5,'items':{'type':'object','properties':{'user':{'type':'string','minLength':1,'maxLength':80},'assistant':{'type':'string','minLength':1,'maxLength':140}},'required':['user','assistant'],'additionalProperties':False}}},'required':['conversations'],'additionalProperties':False}
   task='For a local test, draft FIVE different original English user/assistant chats with Tutty. One pair each, short everyday topics. Each user message 4-12 words; each reply 8-18 words. No tags, emoji or headings. JSON only. Do not copy demonstrations.'
   obj,_=request(teacher_messages('devil','en',task),schema=schema,max_tokens=1100,temperature=.8)
   import hashlib
   from gemma_common import identity
   rows=[]
   import re
   for i,c in enumerate(obj['conversations']):
    speech=re.sub(r'\[act:[^\]]+\]\s*','',c['assistant'])
    rows.append({'id':hashlib.sha256(json.dumps(c).encode()).hexdigest()[:20],'slot':i,'character':'devil','language':'en','turns':1,'messages':[{'role':'system','content':identity('devil')},{'role':'user','content':c['user']},{'role':'assistant','content':speech}],'teacher':manifest,'purpose':'isolated local-model smoke data; animation tags removed only for harness test'})
   (w/'data/raw').mkdir(exist_ok=True,parents=True);(w/'data/raw/accepted.jsonl').write_text(''.join(json.dumps(r,ensure_ascii=False)+'\n' for r in rows))
  else:
   import bakeoff_gemma as b
   scores_path=w/'eval/gemma_scores.json';scores=json.loads(scores_path.read_text()) if scores_path.exists() else {}
   schema={'type':'object','properties':{**{k:{'type':'integer','enum':[0,1,2]} for k in ['answers','character','natural','short']},'note':{'type':'string'}},'required':['answers','character','natural','short','note'],'additionalProperties':False}
   for size in ('4b','12b'):
    path=w/f'data/pilot-gemma-bakeoff/{size}.jsonl'
    if not path.exists():continue
    rows=[json.loads(l) for l in path.read_text().splitlines()]
    if len(rows)!=30:continue
    scores.setdefault(size,{})
    for r in rows:
     if r['id'] in scores[size]:continue
     text=f"Grade this reply independently: 0 fail, 1 partial, 2 meets. answers: relevant, factually correct and honest about no phone tools/camera. character: matches {r['character']}. natural: fluent {r['language']}, not gibberish, English-only Tanglish, or literal translation. short: 1-4 short sentences. Return JSON. User: {r['user']}\nReply: {r['response']}"
     score,_=request([{'role':'system','content':'Strict dialogue quality reviewer. Grade actual meaning, not catchphrase matching.'},{'role':'user','content':text}],schema=schema,max_tokens=180,temperature=0)
     import re
     if r['language']=='tanglish' and re.search(r'[\u0b80-\u0bff]',r['response']):score['natural']=0
     if r['language']=='ta' and len(re.findall(r'[\u0b80-\u0bff]',r['response']))<4:score['natural']=0
     if r['id']=='en-05' and not re.search(r'\b(12|twelve)\b',r['response'],re.I):score['answers']=0
     score['note']='Local automatic rubric score; not human-verified. '+score['note'];scores[size][r['id']]=score
     scores_path.write_text(json.dumps(scores,ensure_ascii=False,indent=2)+'\n')
 finally:
  server.send_signal(signal.SIGINT) if server.poll() is None else None
  try:server.wait(timeout=10)
  except subprocess.TimeoutExpired:server.kill();server.wait()
  thread.join(timeout=2);log.close()
if __name__=='__main__':main()
