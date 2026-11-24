"""Readable status without model loading or exposing HF credentials."""
from pathlib import Path
import json
import subprocess
ROOT=Path(__file__).resolve().parent
for size in ('4b','12b'):
 manifest=ROOT/f'teachers/gemma3-{size}.json'
 print(size,'download verified' if manifest.exists() else 'download pending')
 path=ROOT/f'data/pilot-gemma-bakeoff/{size}.jsonl'
 print(' bake-off replies:',len(path.read_text().splitlines()) if path.exists() else 0,'/30')
 for task in ('bakeoff','generate'):
  name=f'settai-mini-gemma{size}-{task}'
  r=subprocess.run(['systemctl','--user','show',name,'-p','ActiveState','-p','MemoryCurrent','-p','Result'],capture_output=True,text=True)
  print(' ',name, r.stdout.strip().replace('\n','; '))
 selected=ROOT/'teachers/selected.json'
 if selected.exists() and size==json.loads(selected.read_text())['size']:print(' SELECTED TEACHER')
progress=ROOT/'logs/gemma-generation-progress.json'
if progress.exists():print('Generation:',progress.read_text())
review=ROOT/'data/review_samples.md'
print('30-sample review:',str(review) if review.exists() else 'not ready; no training started')
