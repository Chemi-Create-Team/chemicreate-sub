#!/usr/bin/env python3
"""Two pumped reducer lines; captions are authored in data/ponder_lessons.json."""
import json
import sys
sys.path.insert(0, 'tools')
from generate_reactor_assets import A, NS
from generate_petrochemical_ponder import Structure, OUT

s = Structure(9, 4)
for z in (1, 4):
    for x in (1, 7):
        s.block(x, 1, z, NS + 'pressure_tank', bottom='true', top='true', shape='window')
    s.pump(2, 1, z, 'east')
    for x in (3, 5, 6):
        s.block(x, 1, z, NS + 'stainless_steel_pipe', east='true', west='true', north='false', south='false', up='false', down='false', waterlogged='false')
    s.block(4, 1, z, NS + 'pressure_reducing_valve', facing='east', axis='x')
s.save('pressure_valves')
(A / 'ponder/plant/pressure_valves.nbt').write_bytes((OUT / 'pressure_valves.nbt').read_bytes())
(OUT / 'pressure_valves.nbt').unlink()
path = A / 'ponder/tutorials.json'
data = [t for t in json.loads(path.read_text()) if t['id'] != 'pressure_valves']
data.append({'id': 'pressure_valves', 'size': 9, 'height': 4, 'components': ['pressure_reducing_valve'],
             'steps': [{'target': target, 'key': f'step_{i + 1}', **extra} for i, (target, extra) in enumerate([
                 ([2, 1, 1], {'speed': 64}), ([4, 1, 1], {}), ([7, 1, 1], {}),
                 ([4, 1, 4], {'wrench': True}), ([7, 1, 4], {})])]})
path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
