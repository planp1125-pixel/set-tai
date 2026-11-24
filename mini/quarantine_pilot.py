"""Preserve unsuccessful pilot checkpoints without admitting them to a corpus."""
from datetime import datetime
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parent
active=subprocess.run(['systemctl','--user','is-active','--quiet','settai-mini-draft']).returncode==0
if active: raise SystemExit('Stop settai-mini-draft before quarantining its pilot checkpoints.')
raw=ROOT/'data/raw'
if not raw.exists(): raise SystemExit('No raw pilot checkpoints to move.')
if (ROOT/'data/chats.jsonl').exists():
    raise SystemExit('Final chats already exist; review the dataset explicitly before moving checkpoints.')
target=ROOT/'data'/('pilot-'+datetime.now().strftime('%Y%m%d-%H%M%S'))
raw.rename(target)
print('Pilot preserved outside final corpus:',target)
