#!/usr/bin/env python3
"""Check real Ponder runtime keys, not just authoring strings in the language files."""
import json,re,sys
from pathlib import Path
from generate_ponder_language import ASSETS,HEADERS,SHARED
headers=dict(HEADERS)
recipes=set()
for t in json.loads((ASSETS/'ponder/tutorials.json').read_text()):
 headers['plant_'+t['id']]='guide.'+t['id']+'.title'
 for step in t['steps']:
  if step.get('process',{}).get('recipe'):recipes.add(SHARED+'guide.'+t['id']+'.'+step['key'])
manufacturing_only='--manufacturing' in sys.argv
if manufacturing_only:headers={scene:author for scene,author in headers.items() if scene.startswith('plant_manufacturing_')}

def valid_format(key,value):
 if key.endswith('.recipe'):return key.removesuffix('.recipe') in recipes and value.count('%s')==2 and value.count('\n')==1
 if key in recipes:return value.endswith('%s → %s') and not re.search(r'%(?:[0-9]+\$)?[sdf]',value[:-len('%s → %s')])
 return not re.search(r'%(?:[0-9]+\$)?[sdf]',value)

for language in ['en_us','ja_jp']:
 s=json.loads((ASSETS/'lang'/(language+'.json')).read_text())
 for scene,author in headers.items():assert s['create_refinery_n_reactors.ponder.'+scene+'.header']==s[SHARED+author]
 for k,v in s.items():
  if manufacturing_only and not k.startswith(SHARED+'guide.manufacturing_'):continue
  if k.startswith('ponder.create_refinery_n_reactors.'):
   assert s.get(SHARED+k[len('ponder.create_refinery_n_reactors.'):])==v,f'{k}: missing runtime translation'
  if k.startswith(SHARED):
   assert v and not v.startswith('ponder.') and not v.startswith('create_refinery_n_reactors.ponder.')
   assert valid_format(k,v),f'{k}: unsupported Ponder format arguments'
# Chinese captions use the same complete runtime key set.
en=json.loads((ASSETS/'lang/en_us.json').read_text());zh=json.loads((ASSETS/'lang/zh_cn.json').read_text());chinese=0
for scene,author in headers.items():
 guide=SHARED+author.rsplit('.',1)[0]+'.'
 keys=['create_refinery_n_reactors.ponder.'+scene+'.header']+[k for k in en if k.startswith(guide)]
 present=[k for k in keys if k in zh]
 assert len(present)==len(keys),f'{scene}: Chinese has {len(present)} of {len(keys)} texts; missing {sorted(set(keys)-set(present))}'
 if present:
  chinese+=1
  for k in present:assert not k.startswith(SHARED) or valid_format(k,zh[k]),f'{k}: unsupported Chinese format arguments'
for source in (ASSETS.parents[2]/'java/net/createplant/ponder').glob('*Scenes.java'):
 assert '.text(' not in source.read_text(), f'{source.name}: sequential .text fallbacks are ignored at runtime; use sharedText'
lessons=json.loads((Path(__file__).parent/'data/ponder_lessons.json').read_text())
coverage=[]
for scene,title in headers.items():
 stem=title.removesuffix('title')
 entries={key:value for key,value in lessons.items() if key.startswith(stem) and key!=title}
 assert entries and title in lessons,f'{scene}: missing authored lesson'
 totals={}
 for language in ('en_us','ja_jp','zh_cn'):
  strings=json.loads((ASSETS/'lang'/(language+'.json')).read_text())
  total=0
  for key,texts in entries.items():
   assert strings[SHARED+key]==texts[language],f'{key}: generated text differs from the lesson source'
   runtime=SHARED+key+('.action' if texts[language].endswith('\n%s → %s') else '')
   count=int(strings[runtime+'.lesson_pages.count'])
   assert 1<=count<=3,f'{key}: keep explanations short'
   for page in range(count):
    text=strings[runtime+'.lesson_pages.'+str(page)]
    assert text.strip() and len(re.findall(r'[。！？]|[.!?](?:\s|$)',text))<=2,f'{key}: each page explains one or two sentences'
   total+=count
  totals[language]=total
 coverage.append({'scene':scene,'captions':len(entries),'pages':totals})
if '--report' in sys.argv:
 output=Path(sys.argv[sys.argv.index('--report')+1])
 output.parent.mkdir(parents=True,exist_ok=True)
 output.write_text(json.dumps(coverage,ensure_ascii=False,indent=2)+'\n')
print('PASS:',len(headers),'runtime Ponder headers and all shared captions in Japanese and English;',chinese,'scenes complete in Chinese')
print('PASS: every scene has authored, paged captions with at most two sentences per page')
