"""Resumable parallel HTTP fallback for the stalled 4B transfer; HF login stays private."""
from concurrent.futures import ThreadPoolExecutor,as_completed
import hashlib
import math
from pathlib import Path
import time
import requests
from huggingface_hub.utils import build_hf_headers
from download_gemma_teachers import MODELS

size='4b';revision,expected_size,digest=MODELS[size]
dest=Path.home()/'models/gemma-3-4b-it-qat-q4_0-gguf';name='gemma-3-4b-it-q4_0.gguf'
parts=dest/'.http-parts';parts.mkdir(exist_ok=True)
chunk_size=64*1024*1024
# Preserve completed sequential HTTP prefix blocks from the interrupted transfer.
for old in (dest/'.cache/huggingface/download').glob('*.incomplete'):
 with old.open('rb') as stream:
  for i in range(old.stat().st_size//chunk_size):
   out=parts/f'{i:04d}.done'
   if out.exists():stream.seek(chunk_size,1)
   else:out.write_bytes(stream.read(chunk_size))
headers=build_hf_headers()
def part(i):
 start=i*chunk_size;end=min(expected_size,start+chunk_size)-1
 out=parts/f'{i:04d}.done';amount=end-start+1
 if out.exists() and out.stat().st_size==amount:return i
 url=f'https://huggingface.co/google/gemma-3-4b-it-qat-q4_0-gguf/resolve/{revision}/{name}?download=true&settai_part={i}'
 for attempt in range(6):
  try:
   with requests.get(url,headers={**headers,'Range':f'bytes={start}-{end}'},stream=True,timeout=(30,90)) as response:
    if response.status_code!=206:raise RuntimeError('HTTP status '+str(response.status_code))
    if response.headers.get('Content-Range')!=f'bytes {start}-{end}/{expected_size}':raise RuntimeError('Unexpected HTTP range')
    temp=out.with_suffix('.partial')
    with temp.open('wb') as f:
     for data in response.iter_content(1024*1024):f.write(data)
    if temp.stat().st_size!=amount:raise RuntimeError('Incomplete HTTP range')
    temp.replace(out)
   return i
  except Exception as e:
   print('Retry part',i,'attempt',attempt+1,type(e).__name__,flush=True);time.sleep(min(30,2**attempt))
 raise RuntimeError(f'Failed HTTP part {i}; complete parts retained') from None
with ThreadPoolExecutor(max_workers=4) as pool:
 futures=[pool.submit(part,i) for i in range(math.ceil(expected_size/chunk_size))]
 for n,f in enumerate(as_completed(futures),1):print('HTTP part complete',f.result(),f'({n}/{len(futures)})',flush=True)
temp=dest/(name+'.http-complete')
with temp.open('wb') as target:
 for i in range(math.ceil(expected_size/chunk_size)):
  with (parts/f'{i:04d}.done').open('rb') as source:
   while data:=source.read(4*1024*1024):target.write(data)
with temp.open('rb') as f:actual=hashlib.file_digest(f,'sha256').hexdigest()
assert temp.stat().st_size==expected_size and actual==digest,'GGUF verification failed'
temp.replace(dest/name)
print('Verified official 4B GGUF via HTTP.',flush=True)
