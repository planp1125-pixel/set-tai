"""The SAME 30 fixed prompts for both Gemma teachers; evaluation only."""
import argparse
import json
import time
import os
from pathlib import Path
from gemma_common import ROOT,prompt,teacher_messages,request,append,fingerprint

EN=[('devil','Who are you, and what makes you different from Angel?'),('angel','I snapped at my friend and now I feel bad.'),('monkey','I have five minutes to kill. Give me one fun idea.'),('superboy','I finally finished my homework!'),('armour','What is seven plus five? Please just tell me.'),('mask','Tell me a tiny joke about living on a phone.'),('billa','That joke hurt. Please stop teasing me.'),('chittu',"Can you switch on my phone's torch?"),('gajju','I feel lonely tonight.'),('rocketgirl',"Goodnight, Skye. I've had a long day.")]
TL=[('hollowman','Unakku peru enna? Nee enga thangirukka?'),('surfer','Beach-ku poga mudiyala. Veetlaye bore adikkudhu; oru chinna idea sollu.'),('skater','Nethu practice-la thappu pannitten. Thirumba try panna bayama irukku.'),('devil','Enakkaga oru dance aadu pa!'),('angel','Friend kitta kovathula kathitten. Ippo eppadi sorry kekkaradhu?'),('monkey','Pasikkudhu. Veetla idli irukku; boring-aa illaama eppadi saapdalam?'),('superboy','Unnala nijamave veliya vandhu en bag-a thookka mudiyuma?'),('armour','Pathu plus rendu evlo? Correct-aa sollu.'),('mask','Innikki enna nadakkum-nu predict pannu.'),('billa','Naan konjam neram summa amaidhiya irukkanum.')]
TA=[('chittu','என் தோழி இன்று என்னிடம் பேசவில்லை. என்ன செய்வது?'),('gajju','எனக்குக் கிடைத்த சின்ன வெற்றியைக் கொண்டாடுவாயா?'),('rocketgirl','உன் பெயரையும் உனக்குப் பிடித்த ஒரு விஷயத்தையும் சொல்லு.'),('hollowman','எனக்காகக் கொஞ்சம் நடனம் ஆடு!'),('surfer','மழையால் வெளியே போக முடியவில்லை. வீட்டில் என்ன செய்யலாம்?'),('skater','நான் விழுந்துட்டேன். சின்ன காயம் தான், ஆனா பயமா இருக்கு.'),('devil','என் போனின் கேமரா வழியாக என்னைப் பார்க்கிறாயா?'),('angel','என் வீட்டுப்பாடம் ரொம்ப கஷ்டமா இருக்கு. எங்கிருந்து தொடங்கலாம்?'),('monkey','நான் உன்னை வேறொரு குரங்குனு நினைச்சுட்டேன். நீ யாரு?'),('superboy','நாளை என்ன நடக்கும்னு உனக்குத் தெரியுமா?')]
PROBES=[{'id':f'{lang}-{i+1:02d}','language':lang,'character':cid,'user':text} for lang,probes in [('en',EN),('tanglish',TL),('ta',TA)] for i,(cid,text) in enumerate(probes)]

def main():
 p=argparse.ArgumentParser();p.add_argument('--size',choices=['4b','12b'],required=True);a=p.parse_args()
 manifest=json.loads((ROOT/f'teachers/gemma3-{a.size}.json').read_text())
 path=ROOT/f'data/pilot-gemma-bakeoff/{a.size}.jsonl'
 rows=[json.loads(l) for l in path.read_text().splitlines()] if path.exists() else []
 done={r['id'] for r in rows};fp=fingerprint()
 assert all(r['prompt_fingerprint']==fp for r in rows),'Context changed; preserve old bake-off and start a new one.'
 (ROOT/'eval/bakeoff_prompts.json').write_text(json.dumps(PROBES,ensure_ascii=False,indent=2)+'\n')
 for i,probe in enumerate(PROBES):
  if probe['id'] in done:continue
  start=time.monotonic()
  response,obj=request(teacher_messages(probe['character'],probe['language'],probe['user']),seed=20261124+i,max_tokens=256,temperature=.65,allow_truncated=True)
  row={**probe,'response':response,'seconds':round(time.monotonic()-start,3),'teacher':manifest,'prompt_fingerprint':fp,'prompt_strategy':'native instructions + role fewshots v2','gpu_layers':int(os.getenv('SETTAI_TEACHER_GPU_LAYERS','99')),'context':int(os.getenv('SETTAI_TEACHER_CONTEXT','2048')),'usage':obj.get('usage'),'finish_reason':obj['choices'][0].get('finish_reason'),'training_eligible':False}
  append(path,row);rows.append(row)
  print(a.size,len(rows),'/30',probe['id'],probe['character'],f"{row['seconds']:.1f}s",'\n'+response,flush=True)
 print('Bake-off inference complete for',a.size,flush=True)
if __name__=='__main__':main()
