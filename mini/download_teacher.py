"""Download only official, pinned Apache-2.0 Qwen GGUF weights outside Git."""
import json
import argparse
import hashlib
from pathlib import Path
from huggingface_hub import hf_hub_download
p=argparse.ArgumentParser()
p.add_argument('--qwen3',action='store_true')
a=p.parse_args()
REPO = 'Qwen/Qwen3-8B-GGUF' if a.qwen3 else 'Qwen/Qwen2.5-7B-Instruct-GGUF'
REV = '7c41481f57cb95916b40956ab2f0b139b296d974' if a.qwen3 else 'bb5d59e06d9551d752d08b292a50eb208b07ab1f'
FILES = ['Qwen3-8B-Q4_K_M.gguf'] if a.qwen3 else [f'qwen2.5-7b-instruct-q4_k_m-{i:05d}-of-00002.gguf' for i in (1, 2)]
OUT = Path.home()/'models'/REPO.split('/')[1]
OUT.mkdir(parents=True, exist_ok=True)
for name in FILES:
    print('Downloading', name, flush=True)
    hf_hub_download(REPO, name, revision=REV, local_dir=OUT, token=False)
files=[]
for name in FILES:
    with (OUT/name).open('rb') as stream:
        digest=hashlib.file_digest(stream,'sha256').hexdigest()
    if a.qwen3 and digest!='d98cdcbd03e17ce47681435b5150e34c1417f50b5c0019dd560e4882c5745785':
        raise RuntimeError('Qwen3 GGUF does not match the pinned official LFS SHA-256')
    files.append({'name':name,'bytes':(OUT/name).stat().st_size,'sha256':digest})
record = {'repo':REPO, 'revision':REV, 'license':'apache-2.0','files':files}
Path(__file__).with_name('teacher-qwen3.json' if a.qwen3 else 'teacher.json').write_text(json.dumps(record, indent=2)+'\n')
print('Teacher download complete:',record,flush=True)
