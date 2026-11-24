"""CPU baseline comparison and overlap/copy diagnostics on held-out prompts."""
import argparse,gc,json,random,re,time
from difflib import SequenceMatcher
from pathlib import Path
import torch
from transformers import AutoTokenizer,AutoModelForCausalLM

def normalized(s):return re.sub(r'\W+',' ',s.casefold()).strip()
def main():
 p=argparse.ArgumentParser();p.add_argument('--base',type=Path,required=True);p.add_argument('--tuned',type=Path,required=True);p.add_argument('--train',type=Path,required=True);p.add_argument('--test',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--smoke',action='store_true');a=p.parse_args()
 torch.set_num_threads(8)
 train=[json.loads(l) for l in a.train.read_text().splitlines()];held=[json.loads(l) for l in a.test.read_text().splitlines()]
 assert not ({r['id'] for r in train}&{r['id'] for r in held}),'Train/test conversation leakage'
 assert not ({normalized(r['messages'][1]['content']) for r in train}&{normalized(r['messages'][1]['content']) for r in held}),'Repeated opening across splits'
 rng=random.Random(71);rng.shuffle(held);probes=held[:1 if a.smoke else 40]
 if not a.smoke and len(probes)!=40:raise RuntimeError('40 held-out conversations required')
 answers=[normalized(m['content']) for r in train for m in r['messages'] if m['role']=='assistant'];results={}
 for label,path in [('base',a.base),('tuned',a.tuned)]:
  tok=AutoTokenizer.from_pretrained(path,local_files_only=True)
  model=AutoModelForCausalLM.from_pretrained(path,local_files_only=True,torch_dtype=torch.float32 if a.smoke else torch.bfloat16,low_cpu_mem_usage=True,attn_implementation='eager');model.eval()
  batch=[]
  for row in probes:
   messages=row['messages'][:2];inp=tok.apply_chat_template(messages,tokenize=True,add_generation_prompt=True,return_tensors='pt')
   start=time.monotonic()
   with torch.inference_mode():out=model.generate(inp,max_new_tokens=12 if a.smoke else 96,do_sample=False,pad_token_id=tok.pad_token_id)
   text=tok.decode(out[0,inp.shape[1]:],skip_special_tokens=True).strip();elapsed=time.monotonic()-start;words=normalized(text).split()
   longest=max((SequenceMatcher(None,words,x.split(),autojunk=False).find_longest_match().size for x in answers),default=0)
   batch.append({'id':row['id'],'character':row['character'],'language':row['language'],'prompt':messages[-1]['content'],'reply':text,'seconds':elapsed,'longest_training_word_overlap':longest,'copy_flag':longest>=12 and longest/max(1,len(words))>=.8})
  results[label]=batch;del model;gc.collect()
 a.out.mkdir(parents=True,exist_ok=True);(a.out/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n')
 report=['# CPU base versus tuned evaluation','',f'{len(probes)} held-out prompts. Train/test IDs and opening prompts are disjoint. Copy flags mean >=12 consecutive training words covering >=80% of a generated reply; this is a diagnostic, not proof of plagiarism.','',f'Smoke test: {a.smoke}. Times include token generation on this CPU.','']
 for b,t in zip(results['base'],results['tuned']):
  report += [f"## {b['character']} · {b['language']}",'',f"User: {b['prompt']}",'',f"Base ({b['seconds']:.2f}s): {b['reply']}",'',f"Tuned ({t['seconds']:.2f}s): {t['reply']}",'',f"Copy flag: base={b['copy_flag']}, tuned={t['copy_flag']}",'']
 (a.out/'report.md').write_text('\n'.join(report)+'\n')
if __name__=='__main__':main()
