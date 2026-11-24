"""Authenticated, resumable official QAT teacher downloads; weights outside Git."""
import argparse
import hashlib
import json
from pathlib import Path
from huggingface_hub import hf_hub_download

MODELS={
 '4b': ('15f73f5eee9c28f53afefef5723e29680c2fc78a',3155051328,'76aed0a8285b83102f18b5d60e53c70d09eb4e9917a20ce8956bd546452b56e2'),
 '12b': ('7929e34ac7c459bbfd1f38d7358e6839f204578b',8074473920,'dd53172ff3a7b1b16c8fb3d944b87f42a6228ff2de3825b8813ae90d988434cd'),
}
def main():
 p=argparse.ArgumentParser();p.add_argument('--size',choices=MODELS,required=True);a=p.parse_args()
 revision,size,digest=MODELS[a.size]
 repo=f'google/gemma-3-{a.size}-it-qat-q4_0-gguf';name=f'gemma-3-{a.size}-it-q4_0.gguf'
 dest=Path.home()/'models'/repo.split('/')[1];dest.mkdir(parents=True,exist_ok=True)
 print('Downloading official text-only teacher:',repo,flush=True)
 path=dest/name
 if not path.exists() or path.stat().st_size!=size:
  path=Path(hf_hub_download(repo,name,revision=revision,local_dir=dest))
 assert path.stat().st_size==size
 with path.open('rb') as f: actual=hashlib.file_digest(f,'sha256').hexdigest()
 assert actual==digest, 'Official LFS SHA-256 mismatch'
 record={'id':f'gemma3-{a.size}-qat','repo':repo,'revision':revision,'license':'gemma',
         'quantization':'QAT Q4_0','path':str(path),'bytes':size,'sha256':digest}
 out=Path(__file__).resolve().parent/'teachers';out.mkdir(exist_ok=True)
 (out/f'gemma3-{a.size}.json').write_text(json.dumps(record,indent=2)+'\n')
 print('Verified teacher:',a.size,size,'bytes',flush=True)
if __name__=='__main__':main()
