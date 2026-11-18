#!/usr/bin/env python3
"""Merge the four independent feature generators into shared catalogues."""
import json
from pathlib import Path
R=Path(__file__).resolve().parents[1]
A=R/'src/main/resources/assets/create_refinery_n_reactors'
F=R/'tools/data'
def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
for name in ('building_materials_lang','water_treatment_lang','fusion_fuel_lang','industrial_expansion_lang'):
    path=F/(name+'.json')
    if not path.exists():continue
    for lang,strings in json.loads(path.read_text()).items():
        target=A/'lang'/f'{lang}.json'
        data=json.loads(target.read_text()) if target.exists() else {}
        data.update(strings);write(target,data)
manifest=R/'tools/powder_items.json';data=json.loads(manifest.read_text())
for name in ('building_materials_powders','water_treatment_powders'):
    path=F/(name+'.json')
    if path.exists():
        for category,entries in json.loads(path.read_text()).items():data.setdefault(category,{}).update(entries)
write(manifest,data)
target=A/'ponder/tutorials.json';data=json.loads(target.read_text())
for name in ('fusion_fuel_ponder','industrial_expansion_tutorials'):
    path=F/(name+'.json')
    if path.exists():
        for entry in json.loads(path.read_text()):
            data=[old for old in data if old['id']!=entry['id']];data.append(entry)
write(target,data)
for name in ('slabs','stairs'):
    path=F/f'building_{name}_tag.json'
    if path.exists():
        target=R/f'src/main/resources/data/minecraft/tags/block/{name}.json'
        data=json.loads(target.read_text()) if target.exists() else {'replace':False,'values':[]}
        data['values'] += [value for value in json.loads(path.read_text())['values'] if value not in data['values']];write(target,data)
for tool in ('mineable/pickaxe','needs_stone_tool'):
    target=R/f'src/main/resources/data/minecraft/tags/block/{tool}.json'
    data=json.loads(target.read_text()) if target.exists() else {'replace':False,'values':[]}
    data['values'] += [value for value in ('create_refinery_n_reactors:'+part for part in ('industrial_dispatch_board','cryogenic_cold_box_drive','cryogenic_regenerator')) if value not in data['values']];write(target,data)
print('Merged industrial feature translations, powders, tutorials and tags.')
