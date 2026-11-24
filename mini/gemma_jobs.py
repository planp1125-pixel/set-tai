"""Capped, nohup-backed user services for sequential teacher work."""
import argparse
import json
from pathlib import Path
import subprocess
import time
from gemma_common import ROOT

def unit(size,task):return f'settai-mini-gemma{size}-{task}'
def start(size,task,variant=''):
 name=unit(size,task)+('-'+variant if variant else '')
 args=['systemd-run','--user',f'--unit={name}','--property=Type=exec',
       '--property=MemoryMax='+('10G' if size=='12b' else '11G'),
       '--property=MemoryHigh='+('9G' if size=='12b' else '10G'),
       '--property=MemorySwapMax=256M',f'--property=StandardOutput=append:{ROOT}/logs/{name}.log',
       '--property=StandardError=inherit',f'--working-directory={ROOT.parent}',
       '/usr/bin/nohup',str(ROOT/'.venv/bin/python'),'-u','mini/limits.py',
       '--gib','5.5','--total-gib','9.5' if size=='12b' else '11.5',
       '--','/usr/bin/bash','mini/run_gemma.sh',size,task]
 if task=='generate':
  args[2:2]=['--property=Restart=on-failure','--property=RestartSec=60s','--property=RestartPreventExitStatus=1 2']
 subprocess.run(args,check=True)
 return name

def wait(name):
 while True:
  r=subprocess.run(['systemctl','--user','show',name,'-p','ActiveState','-p','Result','-p','ExecMainStatus'],capture_output=True,text=True)
  fields=dict(line.split('=',1) for line in r.stdout.splitlines() if '=' in line)
  if fields.get('ActiveState') not in ('active','activating','deactivating'):return fields
  time.sleep(2)

def pipeline():
 for size in ('4b','12b'):
  print('Waiting for verified teacher',size,flush=True)
  while not (ROOT/f'teachers/gemma3-{size}.json').exists():time.sleep(5)
  print('Starting bake-off',size,flush=True)
  name=start(size,'bakeoff');result=wait(name)
  path=ROOT/f'data/pilot-gemma-bakeoff/{size}.jsonl'
  count=len(path.read_text().splitlines()) if path.exists() else 0
  print('Finished',size,count,'/30',result,flush=True)
  if count!=30 or fields_failed(result):
   log=(ROOT/f'logs/{name}.log').read_text()
   record={'size':size,'result':result,'completed_probes':count,
           'reason':'Skipped: did not complete under its capped memory budget; inspect service log.',
           'memory_stop':[line for line in log.splitlines() if 'MEMORY STOP' in line]}
   (ROOT/f'eval/gemma-{size}-skip.json').write_text(json.dumps(record,indent=2)+'\n')
   if size=='4b':raise RuntimeError('4B bake-off failed; inspect log before continuing')
 print('Bake-off inference finished. Awaiting direct 0-2 scoring; no training launched.',flush=True)

def fields_failed(fields):
 return fields.get('Result') not in (None,'','success') or fields.get('ExecMainStatus') not in (None,'','0')

if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('task',choices=['pipeline','bakeoff','generate']);p.add_argument('--size',choices=['4b','12b']);a=p.parse_args()
 if a.task=='pipeline':pipeline()
 else:
  if not a.size:p.error('--size required')
  print(start(a.size,a.task))
