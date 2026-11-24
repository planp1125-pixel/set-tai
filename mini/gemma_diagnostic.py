"""Small cold-context diagnostics; never training data or bake-off scores."""
import json,time
from urllib.request import Request,urlopen
from gemma_common import ROOT,prompt,append
cases=[('bare-ta',[{'role':'user','content':'தமிழில் சுருக்கமாக பதில் சொல்: நீ எப்படி இருக்கிறாய்?'}]),
       ('bare-tanglish',[{'role':'system','content':'Reply naturally in Tamil words written in Latin letters. Be short and helpful.'},{'role':'user','content':'Innikki romba tired-aa irukken. Konjam pesalama?'}]),
       ('cold-persona',[{'role':'system','content':prompt('hollowman','tanglish')},{'role':'user','content':'Unakku peru enna? Nee enga thangirukka?'}])]
for name,messages in cases:
 payload={'model':'local-settai-teacher','messages':messages,'temperature':0,'seed':20261134,'max_tokens':200,'cache_prompt':False}
 start=time.monotonic()
 with urlopen(Request('http://127.0.0.1:18081/v1/chat/completions',json.dumps(payload).encode(),{'Content-Type':'application/json'}),timeout=90) as r:obj=json.load(r)
 text=obj['choices'][0]['message']['content']
 row={'case':name,'response':text,'seconds':time.monotonic()-start,'training_eligible':False,'teacher':'gemma3-4b-qat','cache_prompt':False}
 append(ROOT/'data/pilot-gemma-diagnostic/isolated.jsonl',row)
 print(name,text,flush=True)
