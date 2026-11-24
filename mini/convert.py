"""Official CPU LiteRT export; record exact failures and export GGUF fallback."""
import argparse,json,subprocess,sys,venv
from pathlib import Path
ROOT=Path(__file__).resolve().parent
def main():
 p=argparse.ArgumentParser();p.add_argument('--model',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--report',type=Path,required=True);a=p.parse_args();a.out.mkdir(parents=True,exist_ok=True)
 env=ROOT/'.convert-venv';py=env/'bin/python';cli=env/'bin/litert-torch';results={};setup_error=None
 try:
  if not py.exists():venv.create(env,with_pip=True)
  if not cli.exists():
   subprocess.run([str(py),'-m','pip','install','torch','--index-url','https://download.pytorch.org/whl/cpu'],check=True)
   version=subprocess.check_output([str(py),'-c','import torch;assert torch.version.cuda is None;print(torch.__version__)'],text=True).strip()
   constraint=env/'cpu-constraint.txt';constraint.write_text('torch=='+version+'\n')
   subprocess.run([str(py),'-m','pip','install','-c',str(constraint),'litert-torch==0.10.0'],check=True)
   (ROOT/'conversion-requirements.lock.txt').write_text(subprocess.check_output([str(py),'-m','pip','freeze'],text=True))
 except Exception as e:setup_error=str(e)
 for bits,recipe in [(4,'dynamic_wi4b32_afp32'),(8,'dynamic_wi8_afp32')]:
  dest=a.out/f'litert-int{bits}';log=a.report.parent/f'conversion-int{bits}.log'
  try:
   if setup_error:raise RuntimeError(setup_error)
   with log.open('w') as f:subprocess.run([str(cli),'export_hf','--model='+str(a.model),'--output_dir='+str(dest),'--cache_length=1024','--prefill_lengths=128','--quantization_recipe='+recipe],stdout=f,stderr=subprocess.STDOUT,check=True)
   files=list(dest.rglob('*.litertlm'));assert files,'No .litertlm output produced'
   results[str(bits)]={'status':'success','files':[{ 'path':str(x),'bytes':x.stat().st_size} for x in files]}
  except Exception as e:results[str(bits)]={'status':'failed','error':str(e),'details':str(log)}
 a.report.write_text(json.dumps(results,indent=2)+'\n')
 if any(v['status']=='failed' for v in results.values()):
  f16=a.out/'settai-mini-f16.gguf';q4=a.out/'settai-mini-q4_k_m.gguf'
  try:
   subprocess.run([sys.executable,str(ROOT/'vendor/llama.cpp/convert_hf_to_gguf.py'),str(a.model),'--outfile',str(f16),'--outtype','f16'],check=True)
   subprocess.run([str(ROOT/'vendor/llama.cpp/build/bin/llama-quantize'),str(f16),str(q4),'Q4_K_M'],check=True)
   results['gguf_fallback']={'path':str(q4),'bytes':q4.stat().st_size}
  except Exception as e:results['gguf_fallback']={'status':'failed','error':str(e)}
  a.report.write_text(json.dumps(results,indent=2)+'\n')
 if any(v.get('status')=='failed' for v in results.values()):raise RuntimeError('LiteRT conversion failed; GGUF fallback exported. See '+str(a.report))
if __name__=='__main__':main()
