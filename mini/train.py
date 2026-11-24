"""CPU LoRA SFT with assistant-only labels, resumable checkpoints and merge."""
import argparse,gc,hashlib,json,time
from pathlib import Path
import torch
from torch.utils.data import Dataset
from transformers import AutoTokenizer,AutoModelForCausalLM,Trainer,TrainerCallback,TrainingArguments,DataCollatorForSeq2Seq,set_seed
from transformers.trainer_utils import get_last_checkpoint
from peft import LoraConfig,get_peft_model

class Chats(Dataset):
 def __init__(self,rows,tok,limit):
  self.rows=[]
  for row in rows:
   messages=row['messages'];ids=tok.apply_chat_template(messages,tokenize=True);labels=[-100]*len(ids)
   for i,m in enumerate(messages):
    if m['role']=='assistant':
     start=len(tok.apply_chat_template(messages[:i],tokenize=True,add_generation_prompt=True))
     end=len(tok.apply_chat_template(messages[:i+1],tokenize=True))
     labels[start:end]=ids[start:end]
   ids=ids[:limit];labels=labels[:limit]
   if any(v!=-100 for v in labels):self.rows.append({'input_ids':ids,'attention_mask':[1]*len(ids),'labels':labels})
  if not self.rows:raise RuntimeError('No supervised assistant tokens fit the context')
 def __len__(self):return len(self.rows)
 def __getitem__(self,i):return self.rows[i]

def reply(model,tok,messages,n):
 inp=tok.apply_chat_template(messages,tokenize=True,add_generation_prompt=True,return_tensors='pt')
 with torch.inference_mode():out=model.generate(inp,max_new_tokens=n,do_sample=False,pad_token_id=tok.pad_token_id)
 return tok.decode(out[0,inp.shape[1]:],skip_special_tokens=True).strip()

def main():
 p=argparse.ArgumentParser();p.add_argument('--base',type=Path,required=True);p.add_argument('--data',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--report',type=Path,required=True);p.add_argument('--limit',type=int,default=0);p.add_argument('--epochs',type=int,default=1);p.add_argument('--steps',type=int,default=-1);p.add_argument('--length',type=int,default=512);p.add_argument('--smoke',action='store_true');a=p.parse_args()
 assert torch.version.cuda is None,'CPU PyTorch is required'
 torch.set_num_threads(8);set_seed(20261124);a.out.mkdir(parents=True,exist_ok=True)
 rows=[json.loads(l) for l in a.data.read_text().splitlines()];rows=rows[:a.limit] if a.limit else rows
 digest=hashlib.sha256(a.data.read_bytes()).hexdigest();meta=a.out/'input.json'
 config={'data_sha256':digest,'base':str(a.base),'epochs':a.epochs,'steps':a.steps,'length':a.length}
 if meta.exists() and json.loads(meta.read_text())!=config:raise RuntimeError('Checkpoint input/configuration changed; use a new output directory')
 meta.write_text(json.dumps(config,indent=2)+'\n')
 tok=AutoTokenizer.from_pretrained(a.base,local_files_only=True);tok.padding_side='right'
 dtype=torch.float32 if a.smoke else torch.bfloat16
 model=AutoModelForCausalLM.from_pretrained(a.base,local_files_only=True,torch_dtype=dtype,low_cpu_mem_usage=True,attn_implementation='eager')
 data=Chats(rows,tok,a.length)
 def fixed_loss(m):
  m.eval();loss=[]
  with torch.inference_mode():
   for row in data.rows[:5]:loss.append(float(m(**{k:torch.tensor([v]) for k,v in row.items()},use_cache=False).loss))
  return sum(loss)/len(loss)
 initial_loss=fixed_loss(model)
 probes=[r['messages'][:2] for r in rows[:1 if a.smoke else 10]]
 before=[reply(model,tok,m,12 if a.smoke else 64) for m in probes]
 model.config.use_cache=False
 model=get_peft_model(model,LoraConfig(r=4 if a.smoke else 16,lora_alpha=8 if a.smoke else 32,lora_dropout=.05,
  target_modules=['q_proj','v_proj'] if a.smoke else ['q_proj','k_proj','v_proj','o_proj','gate_proj','up_proj','down_proj'],task_type='CAUSAL_LM'))
 class Progress(TrainerCallback):
  def on_train_begin(self,args,state,control,**kw):self.started=time.time();self.initial=state.global_step
  def on_step_end(self,args,state,control,**kw):
   elapsed=time.time()-self.started;delta=state.global_step-self.initial
   path=Path(__file__).resolve().parent/'.pipeline/training_progress.json'
   path.parent.mkdir(parents=True,exist_ok=True);temp=path.with_suffix('.tmp');temp.write_text(json.dumps({'step':state.global_step,'total':state.max_steps,'eta_seconds':elapsed/max(1,delta)*(state.max_steps-state.global_step)}));temp.replace(path)
 args=TrainingArguments(output_dir=str(a.out/'checkpoints'),use_cpu=True,bf16=not a.smoke,fp16=False,
  per_device_train_batch_size=1,gradient_accumulation_steps=1 if a.smoke else 8,num_train_epochs=a.epochs,
  max_steps=a.steps,learning_rate=2e-4,optim='adamw_torch',logging_steps=5,save_steps=10 if a.smoke else 20,
  save_total_limit=2,report_to=[],remove_unused_columns=False,dataloader_pin_memory=False,
  gradient_checkpointing=not a.smoke,gradient_checkpointing_kwargs={'use_reentrant':False},seed=20261124)
 trainer=Trainer(model=model,args=args,train_dataset=data,data_collator=DataCollatorForSeq2Seq(tok,padding=True,label_pad_token_id=-100),callbacks=[Progress()])
 cp=get_last_checkpoint(str(a.out/'checkpoints')) if (a.out/'checkpoints').exists() else None
 started=time.monotonic();trainer.train(resume_from_checkpoint=cp);elapsed=time.monotonic()-started
 model.save_pretrained(a.out/'adapter');tok.save_pretrained(a.out/'adapter')
 model.eval();model.config.use_cache=True
 final_loss=fixed_loss(model)
 after=[reply(model,tok,m,12 if a.smoke else 64) for m in probes]
 losses=[r['loss'] for r in trainer.state.log_history if 'loss' in r]
 if not final_loss<initial_loss:raise RuntimeError('Loss on the same five training probes did not decrease; checkpoint preserved')
 merged=model.merge_and_unload();merged.save_pretrained(a.out/'merged',safe_serialization=True,max_shard_size='2GB');tok.save_pretrained(a.out/'merged')
 result={**config,'rows':len(data),'seconds_this_run':elapsed,'global_step':trainer.state.global_step,'losses':losses,
  'fixed_probe_loss_before':initial_loss,'fixed_probe_loss_after':final_loss,
  'before_after':[{'prompt':m,'before':b,'after':c} for m,b,c in zip(probes,before,after)],'merged':str(a.out/'merged'),'smoke':a.smoke}
 a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
 print(json.dumps({'training_steps':result['global_step'],'rows':len(data),'seconds':round(elapsed,1),'losses':losses}),flush=True)
if __name__=='__main__':main()
