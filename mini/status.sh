#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
exec mini/.venv/bin/python - <<'PY'
from pathlib import Path
import json
p=Path('mini/.pipeline/status.json')
s=json.loads(p.read_text()) if p.exists() else {}
print('Step: '+s.get('step','not started'))
percent=s.get('percent',0);eta=s.get('eta','unknown')
if s.get('step')=='generate':
 q=Path(s['workspace'])/'logs/gemma-generation-progress.json'
 if q.exists():
  r=json.loads(q.read_text());percent=round(100*r['accepted']/r['target'],1)
  import time
  if r['accepted']:eta=str(round((time.time()-s['started'])/r['accepted']*(r['target']-r['accepted'])/3600,1))+' h (generation estimate)'
if s.get('step') in ('practice_270m','train_1b'):
 q=Path('mini/.pipeline/training_progress.json')
 if q.exists():
  r=json.loads(q.read_text());percent=round(100*r['step']/max(1,r['total']),1);eta=str(round(r['eta_seconds']/60,1))+' min'
print('Done: '+str(percent)+'%')
print('ETA: '+eta)
e=Path('mini/logs/FAILED.txt')
lines=[x for x in e.read_text().splitlines() if x.strip()] if e.exists() else []
print('Last error: '+(lines[-1][:240] if lines else 'none'))
PY
