#!/usr/bin/env python3
"""Bridge authoring captions to Ponder's actual namespace-first runtime keys."""
from pathlib import Path
import json
import re
ASSETS=Path(__file__).resolve().parents[1]/'src/main/resources/assets/create_refinery_n_reactors'
SHARED='create_refinery_n_reactors.ponder.shared.'
HEADERS={'pulsation_tube':'pulsation_tube.title','turbine_multiblock': 'turbine_multiblock.title', 'ammonia_separator': 'ammonia_separator.title', 'reciprocating_compressor': 'reciprocating_compressor.title', 'world_liquids': 'world_liquids.title', 'gas_bell': 'gas_bell.title', 'fractionating_column': 'fractionating_column.title'}

HEADERS.update({scene: scene + '.title' for scene in (
    'entrained_gasifier', 'wet_processing', 'chemical_mixtures', 'seasonal_thermal', 'ore_recovery', 'electrowinning', 'material_pump', 'vault_hatch', 'portable_sprayers', 'electric_pump', 'steel_forging', 'fluorite_sources', 'radiation_measurement', 'radiation_decontamination', 'rbmk_core', 'rbmk_control_rods', 'gantry_crane', 'fuel_pool', 'crane_refuelling', 'rbmk_control_room',
    'pipe_gauges', 'quarry_frame', 'horizontal_tank', 'fusion_tokamak', 'fusion_console', 'fusion_motor_generator',
    'natural_draft_tower', 'condenser_loop', 'process_pid', 'fuel_cycle', 'fusion_maintenance', 'lidded_vessel',
    'lidded_vessel_2x2', 'vessel_internals', 'large_absorption_tower', 'reaction_tower', 'reaction_tower_separator', 'vessel_basin', 'vessel_maintenance', 'diorite_casing', 'rbmk_compact_core',
    'flare_stack', 'gas_centrifuge', 'cubic_press', 'facility_constructor', 'electrolysis_cell', 'fractionating_column_binary', 'fractionating_column_air', 'helium_cold_box')})

def generate():
 lessons=json.loads((Path(__file__).parent/'data/ponder_lessons.json').read_text())
 headers=dict(HEADERS)
 for t in json.loads((ASSETS/'ponder/tutorials.json').read_text()):headers['plant_'+t['id']]='guide.'+t['id']+'.title'
 for language in ['en_us','ja_jp','zh_cn']:
  p=ASSETS/'lang'/(language+'.json');s=json.loads(p.read_text());prefix='ponder.create_refinery_n_reactors.'
  for key in list(s):
   if key.startswith(SHARED) and ('.lesson_pages.' in key or key.endswith(('.action','.recipe'))):s.pop(key);continue
   if key.startswith(prefix):s[SHARED+key[len(prefix):]]=s.pop(key)
  for key,texts in lessons.items():
   s[SHARED+key]=texts[language]
   if key.endswith('.title'):continue
   # Recipe quantities are shown with the product, after the processing animation.
   action=texts[language].removesuffix('\n%s → %s')
   if texts[language].endswith('\n%s → %s'):
    s[SHARED+key+'.action']=action
    caption_pages(s,SHARED+key+'.action',action)
    s[SHARED+key+'.recipe']={'en_us':'Feed: %s\nProduct: %s','ja_jp':'原料：%s\n製品：%s','zh_cn':'原料：%s\n产物：%s'}[language]
   else:caption_pages(s,SHARED+key,action)
  # Keep the same resource keys in every locale, even when sentence counts differ.
  for key,texts in lessons.items():
   if key.endswith('.title'):continue
   count=max(len(pages(t.removesuffix('\n%s → %s'))) for t in texts.values())
   for suffix in ('.action',) if texts[language].endswith('\n%s → %s') else ('',):
    stem=SHARED+key+suffix
    local=pages(texts[language].removesuffix('\n%s → %s'))
    for i in range(len(local),count):s[stem+'.lesson_pages.'+str(i)]=local[-1]
  for scene,author in headers.items():s['create_refinery_n_reactors.ponder.'+scene+'.header']=s[SHARED+author]
  p.write_text(json.dumps(s,ensure_ascii=False,indent=2)+'\n')
 print('Generated',len(headers),'Ponder runtime titles and shared translations for all three languages')

def pages(text):
 parts=re.split(r'(?<=[。！？])|(?<=[.!?])\s+(?=[A-Z])',text)
 parts=[part.strip() for part in parts if part.strip()]
 result=[]
 for part in parts:
  if result and len(result[-1])+len(part)<=85 and len(re.findall(r'[。！？]|[.!?](?:\s|$)',result[-1]))<2:
   result[-1]+=(' ' if part[0].isascii() else '')+part
  else:result.append(part)
 return result or [text]

def caption_pages(strings,key,text):
 values=pages(text)
 strings[key+'.lesson_pages.count']=str(len(values))
 for i,value in enumerate(values):strings[key+'.lesson_pages.'+str(i)]=value
if __name__=='__main__':generate()
