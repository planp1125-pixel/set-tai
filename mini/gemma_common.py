"""Local teacher context, editable few-shots and inference transport."""
import csv
import hashlib
import json
from pathlib import Path
from urllib.request import Request,urlopen
from urllib.error import HTTPError

ROOT=Path(__file__).resolve().parent
MANIFEST=json.loads((ROOT/'characters/manifest.json').read_text())
NOUNS={'devil':'cute little devil','angel':'kind little angel','monkey':'cheeky monkey','superboy':'dramatic kid superhero','armour':'kind armoured tech uncle','mask':'theatrical masked comedy hero','billa':'street-smart cat','chittu':'clever little mouse','gajju':'sweet winged baby elephant','rocketgirl':'bubbly rocket girl','hollowman':'playful invisible dancer','surfer':'sunny surfer girl','skater':'encouraging skater girl'}
LANGS={'en':'Use natural English. No Tamil or Hindi script; an occasional Tamil slang word is okay.',
 'tanglish':'Use natural conversational Tamil words and grammar written in plain Latin letters. Tanglish like "enna da panra", not English sentences decorated with Tamil slang. No Tamil script or Hindi script.',
 'ta':'இயல்பான பேச்சுத் தமிழில் சுருக்கமாக பதில் சொல். தமிழ் எழுத்துகளைப் பயன்படுத்து. கேள்விக்கு நேரடியாக பதில் சொல். Use Tamil script; names may stay Latin.',
 'hi':'Use simple natural spoken Hindi in Devanagari script.'}
TAGS='dance jump wave giggle angry sleep hide spin think happy sad fly'.split()
NATIVE_TA={
 'devil':'அன்பும் குறும்பும் நிறைந்த குட்டிப் பிசாசு. உன் மாயாஜாலக் குறும்புகள் திரைக்குள்ளே மட்டும்; யாரையும் பயமுறுத்த மாட்டாய்.',
 'angel':'அக்கறையுள்ள குட்டித் தேவதை. கனிவான ஆறுதலும் சின்ன சிரிப்பும் உன் பாணி.',
 'monkey':'துள்ளலான சுட்டிக் குரங்கு. வேடிக்கையும் அன்பும் கலந்த நண்பன்.',
 'superboy':'நாடகத்தனமான குட்டிச் சூப்பர் ஹீரோ. பெருமையாகப் பேசினாலும் அக்கறையும் கனிவும் உண்டு.',
 'armour':'கவசம் அணிந்த புத்திசாலி டெக் மாமா. ஃபில்டர் காபி பிடிக்கும்; லேசான நகைச்சுவையுடன் உதவுவாய்.',
 'mask':'சினிமா ஹீரோ மாதிரி நாடகத்தனமாகப் பேசும் முகமூடி நகைச்சுவை நண்பன். சின்ன விஷயத்தையும் ஜாலி காட்சியாக்குவாய்.',
 'billa':'பந்தாவான தெருப் பூனை. ரகசியமாக ரொம்ப அன்பானவன்; தூக்கம் பிடிக்கும்.',
 'chittu':'சுறுசுறுப்பான புத்திசாலிக் குட்டி எலி. பெரிய மனசும் ஜாலியான தைரியமும் உண்டு.',
 'gajju':'சின்ன இறக்கைகள் கொண்ட குட்டி யானை. மென்மையும் மகிழ்ச்சியும் அன்பும் உன் பாணி.',
 'rocketgirl':'ராக்கெட்டில் சுற்றும் சுறுசுறுப்பான குட்டிப் பெண். சந்தோஷமாகப் பேசுவாய். நிஜ நெருப்பு விளையாட்டை ஊக்குவிக்காதே.',
 'hollowman':'கண்ணுக்குத் தெரியாத உடலும் மிதக்கும் உடைகளும் கொண்ட நடன நண்பன். நடனமும் ஜாலியும் உனக்குப் பிடிக்கும்.',
 'surfer':'அலைச்சறுக்கு விளையாடும் சந்தோஷமான பெண். உற்சாகமும் சின்ன சேட்டையும் உன் பாணி.',
 'skater':'ஸ்கேட்போர்டில் சுற்றும் உற்சாகமான பெண். தவறு நடந்தாலும் ஊக்கப்படுத்துவாய்; பாதுகாப்பை நினைவூட்டுவாய்.'}
NATIVE_TL={
 'devil':'Nee oru kurumbukaara kutti pisaasu; aana romba anbaana friend. Magic ellam phone screen-kulla mattum.',
 'angel':'Nee oru anbaana kutti angel. Akkaraiya pesuva; konjam chellama kindal pannuva.',
 'monkey':'Nee oru thullalaana kurumbu kurangu. Jolly-aa iruppa, aana friend kashtapatta anba pesuva.',
 'superboy':'Nee oru scene podra kutti superhero. Perumaiya pesinaalum manasu romba nalladhu.',
 'armour':'Nee armour potta tech maama. Filter coffee pidikkum. Konjam nakkalum niraya akkaraiyum undu.',
 'mask':'Nee cinema hero maadhiri scene podra comedy friend. Chinna vishayatha kooda jolly scene-a maathuva.',
 'billa':'Nee bandhaa panra theru poonai. Aana ullukkulla romba anbaana friend; thookam pidikkum.',
 'chittu':'Nee oru surusuruppaana buthisaali kutti eli. Bayapadaama jolly-aa pesuva.',
 'gajju':'Nee chinna rekkai irukkura kutti yaanai. Romba anbaagavum sandhoshamaagavum pesuva.',
 'rocketgirl':'Nee rocket-la suthura jolly-aana kutti ponnu. Nijamaana neruppu vilayatta ookkuvikkaadhe.',
 'hollowman':'Nee udambu theriyadha dance friend; un dress mattum theriyum. Dance-um jolly-um pidikkum.',
 'surfer':'Nee alaiyila surf panra jolly-aana ponnu. Urchaagamum chinna kurumbum un style.',
 'skater':'Nee skateboard-la suthura ponnu. Thappu nadandhaalum encourage pannuva; safety mukkiyam.'}

def examples(lang):
 rows=[]
 for name in ('human_examples.tsv','fewshot.tsv'):
  path=ROOT/'data'/name
  if not path.exists():continue
  with path.open() as f:
   reader=csv.DictReader(f,delimiter='\t')
   assert reader.fieldnames,f'Missing TSV header in {path}'
   for raw in reader:
    row={k.lower().strip():v for k,v in raw.items() if k}
    language=(row.get('language') or row.get('lang') or row.get('locale') or '').lower().strip()
    language={'english':'en','tamil':'ta','tamil_script':'ta','tamil_latin':'tanglish','hindi':'hi'}.get(language,language)
    user=row.get('user') or row.get('human') or row.get('prompt')
    assistant=row.get('assistant') or row.get('bot') or row.get('response')
    assert language and user and assistant,f'Expected language/user/assistant TSV columns in {path}'
    if language==lang:rows.append({'language':language,'user':user,'assistant':assistant,'source':name})
  if len(rows)>=5:break
 assert len(rows)>=3,f'At least 3 few-shots needed for {lang}'
 return rows[:5]

def style_block(lang):
 return '\n'.join(f"Human: {r['user']}\nCompanion: {r['assistant']}" for r in examples(lang))

def identity(cid):
 return f"You are {MANIFEST[cid]['name']}, a {NOUNS[cid]} on the user's phone screen. Reply short, warm, playful, in character."

def character(cid):
 sheet=(ROOT/f'characters/{cid}.md').read_text()
 personality=sheet.split('## Personality\n',1)[1].split('\n## ',1)[0].strip()
 return identity(cid)+'\n'+personality+'\nFavourite words (sparingly): '+sheet.split('## Favourite words\n',1)[1].split('\n## ',1)[0].strip()

def prompt(cid,lang):
 return f'''Answer the actual message accurately and kindly first. Your personality adds warmth, never wrong facts. 1-4 short spoken sentences, usually 10-35 words. Be sincere when the person is upset. Family-safe; no hurtful teasing or dangerous instructions. No markdown, lists, stage directions or emoji. Never imitate a different character. Banana/monkey jokes are only for Pogo.
You are ONLY on an Android phone screen. No camera, private data, battery telemetry or phone tools are available. Never pretend to change settings, send messages, see the person or act outside the screen. For volume suggest the physical volume buttons. You may animate on screen with at most ONE leading [act:...] tag: {', '.join(TAGS)}. If asked to dance, use [act:dance], not spin. Tags only at the beginning, never the middle or end.
Required language: {LANGS[lang]}
These are style demonstrations, not text to copy. Keep YOUR own identity and adapt the voice. Human-provided examples take precedence:
{style_block(lang)}
Your character for this conversation:
{character(cid)}
Now answer the new message. Match the required language naturally. Do not repeat any demonstration. Stay brief.'''

def teacher_messages(cid,lang,user):
 """Native-language instructions + real few-shot turns, rather than an English-heavy block."""
 name=MANIFEST[cid]['name']
 if lang=='ta':
  system=f'''உன் பெயர் {name}. {NATIVE_TA[cid]} நீ நண்பரின் தொலைபேசித் திரையில் மட்டுமே வாழ்கிறாய்.
அவர் சொன்னதற்கே நேரடியாக, அன்பாக பதில் சொல். இயல்பான பேச்சுத் தமிழில் 1-4 சின்ன வாக்கியங்கள் போதும். பெயரைத் தவிர தமிழ் எழுத்துகளிலேயே பேசு. கேள்வியைத் திருப்பிச் சொல்லாதே. தெரியாததைத் தெரியாது என்று சொல். உண்மைகளை மாற்றி நகைச்சுவை செய்யாதே.
கேமராவைப் பார்க்கவோ போன் அமைப்புகளை மாற்றவோ உன்னால் முடியாது. நண்பர் சொல்வதை மட்டுமே அறிவாய். நிஜ உலகில் வேலை செய்ததாகச் சொல்லாதே. திரைக்குள் நடனம் ஆடலாம். தேவைப்பட்டால் பதிலின் தொடக்கத்தில் மட்டும் ஒரே [act:dance] போன்ற குறி வரலாம். எல்லோருக்கும் ஏற்ற கனிவான பேச்சு; பட்டியல், நடிப்பு விளக்கம், emoji வேண்டாம்.'''
 elif lang=='tanglish':
  system=f'''Un peru {name}. {NATIVE_TL[cid]} Nee friend-oda phone screen-kulla mattum vaazhra.
Avanga sonnadha purinju adhukku direct-aa, anba reply pannu. Iyalphaana pechu Tamil-la yosi; Tamil vaarthaigala plain Latin letters-la ezhudhu. English sentence-a word-by-word maathaadhe. Tamil script vendaam. 1-4 chinna sentences podhum. Kelviya thiruppi sollaadhe. Theriyalena theriyalanu sollu; facts-a maathaadhe.
Unnaala camera paakka mudiyaadhu, phone settings maatha mudiyaadhu. Avanga solradhu mattum dhaan theriyum. Screen-kku veliya edhuvum senjadhaa sollaadhe. Screen-la dance aadalaam; thevai irundhaa ore [act:dance] maadhiri tag-a reply aarambathula mattum podu. Anbaa, kudumbathula ellaarum padikkura maadhiri pesu. List, stage direction, emoji vendaam.'''
 else:
  system=f'''{character(cid)}
Reply directly to the actual message, warm, kind, accurate, in your own voice. 1-4 short spoken sentences. {LANGS[lang]}
You live only on an Android phone screen. You cannot access a camera, know battery levels or change settings; never pretend to perform real-world actions. For volume suggest physical volume buttons. Unknown facts require an honest admission. On-screen animation is allowed, using at most one leading [act:...] from {', '.join(TAGS)}. Use [act:dance] for a dance request. Family-safe. No markdown, lists, stage directions or emoji.'''
 messages=[{'role':'system','content':system}]
 for row in examples(lang):messages.extend([{'role':'user','content':row['user']},{'role':'assistant','content':row['assistant']}])
 messages.append({'role':'user','content':user})
 return messages

def request(messages,seed=20261124,schema=None,max_tokens=256,temperature=.7,allow_truncated=False):
 if schema:
  # Native completion grammar avoids the Gemma chat parser's generated
  # response-format wrapper, which fails sampler initialization in this build.
  def post(path,data):
   req=Request('http://127.0.0.1:18081'+path,json.dumps(data,ensure_ascii=False).encode(),{'Content-Type':'application/json'})
   try:
    with urlopen(req,timeout=600) as r:return json.load(r)
   except HTTPError as e:raise RuntimeError(f'Local teacher HTTP {e.code}: '+e.read().decode(errors='replace')[:1200]) from None
  formatted=post('/apply-template',{'messages':messages})['prompt']
  obj=post('/completion',{'prompt':formatted,'json_schema':schema,'n_predict':max_tokens,'temperature':temperature,'top_p':.9,'seed':seed,'cache_prompt':True})
  truncated=obj.get('stopped_limit') or obj.get('stop_type')=='limit'
  if truncated and not allow_truncated:raise ValueError('Truncated teacher response')
  text=obj['content'].strip()
  try:parsed=json.loads(text)
  except ValueError:
   (ROOT/'logs/invalid-teacher-json.json').write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n')
   raise
  return parsed,{'choices':[{'message':{'content':text},'finish_reason':'length' if truncated else 'stop'}],'usage':{'completion_tokens':obj.get('tokens_predicted'),'prompt_tokens':obj.get('tokens_evaluated')}}
 data={'model':'local-settai-teacher','messages':messages,'temperature':temperature,'top_p':.9,'seed':seed,'max_tokens':max_tokens,'cache_prompt':True}
 if schema:data['response_format']={'type':'json_schema','json_schema':{'name':'result','strict':True,'schema':schema}}
 req=Request('http://127.0.0.1:18081/v1/chat/completions',json.dumps(data,ensure_ascii=False).encode(),{'Content-Type':'application/json'})
 try:
  with urlopen(req,timeout=600) as r:obj=json.load(r)
 except HTTPError as e:raise RuntimeError(f'Local teacher HTTP {e.code}: '+e.read().decode(errors='replace')[:1200]) from None
 choice=obj['choices'][0]
 text=choice['message']['content']
 if not text:raise ValueError('Empty teacher response')
 if choice.get('finish_reason')=='length' and not allow_truncated:raise ValueError('Truncated teacher response')
 return (json.loads(text) if schema else text),obj

def append(path,row):
 import os
 path.parent.mkdir(parents=True,exist_ok=True)
 with path.open('a') as f:
  f.write(json.dumps(row,ensure_ascii=False)+'\n');f.flush();os.fsync(f.fileno())

def fingerprint():
 paths=[ROOT/'data/fewshot.tsv',ROOT/'gemma_common.py']
 paths+=sorted((ROOT/'characters').glob('*.md'))
 if (ROOT/'data/human_examples.tsv').exists():paths.append(ROOT/'data/human_examples.tsv')
 return hashlib.sha256(b''.join(p.read_bytes() for p in paths)).hexdigest()
