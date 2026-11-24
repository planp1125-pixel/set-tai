"""Show checkpoints without loading either language model."""
from collections import Counter
import json
from pathlib import Path
import argparse

ROOT=Path(__file__).resolve().parent
p=argparse.ArgumentParser()
p.add_argument('--inspect',action='store_true')
p.add_argument('--review',action='store_true')
a=p.parse_args()
if a.review:
    report=ROOT/'data/review_samples.md'
    if not report.exists():
        raise SystemExit('The full dataset and 30-sample review are not ready. Training remains stopped.')
    print(report.read_text()); raise SystemExit(0)

def rows(path):
    if not path.exists(): return []
    result=[]
    for line in path.read_text().splitlines():
        try: result.append(json.loads(line))
        except json.JSONDecodeError: pass
    return result

accepted=rows(ROOT/'data/raw/accepted.jsonl')
rejected=rows(ROOT/'data/raw/rejections.jsonl')
print(f'Accepted conversations: {len(accepted)}/3000; rejected attempts: {len(rejected)}')
print('Languages:',dict(Counter(r['language'] for r in accepted)))
print('Characters:',dict(Counter(r['character'] for r in accepted)))
print('No training is launched by any drafting script.')
if a.inspect:
    for r in rejected[-3:]:
        print('REJECTED',r.get('slot'),r.get('reason'),json.dumps(r.get('messages'),ensure_ascii=False))
    for r in accepted[-3:]:
        print('ACCEPTED',r['character'],r['language'],json.dumps(r['messages'],ensure_ascii=False))
log=ROOT/'logs/generate.log'
if log.exists(): print('\nRecent job log:\n'+'\n'.join(log.read_text().splitlines()[-6:]))
server=ROOT/'logs/teacher-server.log'
if server.exists(): print('\nRecent server log:\n'+'\n'.join(server.read_text().splitlines()[-5:]))
