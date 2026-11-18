#!/usr/bin/env python3
"""Ponder: the fuel cell, fed hydrogen and oxygen from gas receivers, wired to the circuit and giving warm water; English/Japanese captions.
Run from the repository root."""
import json, sys
sys.path.insert(0, 'tools')
from generate_reactor_assets import A, NS
from generate_petrochemical_ponder import Structure, OUT, DIRECTIONS

ID = 'fuel_cell'


def pipe(s, name, x, y, z, *sides):
    s.block(x, y, z, name, **{side: str(side in sides).lower() for side in DIRECTIONS}, waterlogged='false')


s = Structure(7, 4)
s.block(3, 1, 3, NS + 'fuel_cell', facing='north')
for x in (1, 5):
    s.block(x, 1, 3, NS + 'gas_receiver', axis='x', bottom='true', top='true', shape='plain')
pipe(s, NS + 'stainless_steel_pipe', 2, 1, 3, 'east', 'west'); pipe(s, NS + 'stainless_steel_pipe', 4, 1, 3, 'east', 'west')
pipe(s, 'create:fluid_pipe', 3, 1, 4, 'north', 'south')
s.block(3, 1, 5, 'create:fluid_tank', bottom='true', top='true', shape='window')
s.save(ID)
(A / f'ponder/plant/{ID}.nbt').write_bytes((OUT / f'{ID}.nbt').read_bytes())
(OUT / f'{ID}.nbt').unlink()

TEXTS = {
    'title': ('Power from a fuel cell', '燃料電池で発電する'),
    'step_1': ('The fuel cell turns hydrogen and oxygen back into electricity on nickel electrodes. Pipe them in at any face, each from its own line.',
        '燃料電池は、ニッケルの電極で水素と酸素から電気を作ります。水素と酸素を別々の配管で、どの面からでも入れます。'),
    'step_2': ('Use hydrogen and oxygen from the filter-press electrolysis cells. Each 1 mB of hydrogen takes 0.5 mB of oxygen.',
        'フィルタープレス式電解セルで作った水素と酸素を使います。水素1 mBにつき酸素0.5 mBを消費します。'),
    'step_3': ('Wire the two top terminals to CEE: red is positive, black is negative. Output is 400 V, up to 2 kW.',
        '上の2端子をCEEへ配線します。赤が＋、黒が－で、400 V・最大2 kWです。'),
    'step_4': ('Each 1 mB of hydrogen leaves 1.5 mB of water at 80 °C. Take it out at any face; when its tank is full the cell stops.',
        '水素1 mBにつき、80 °Cの水が1.5 mBできます。水はどの面からでも抜けます。満杯になると発電が止まります。'),
    'step_5': ('If not generating, check gas, water drainage and the circuit. Circuit voltage above 400 V stops output.',
        '発電しないときはガス・排水口・回路を確認します。回路が400 Vを超えると発電を止めます。'),
}
STEPS = [([3, 1, 3], {'item': NS + 'fuel_cell'}), ([1, 1, 3], {'item': NS + 'hydrogen_gas_bucket'}), ([3, 1, 3], {}), ([3, 1, 5], {}), ([3, 1, 3], {})]

path = A / 'ponder/tutorials.json'
raw = path.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
data = [t for t in json.loads(raw) if t['id'] != ID]
data.append({'id': ID, 'size': 7, 'height': 4, 'components': ['fuel_cell'],
    'steps': [{'target': target, 'key': f'step_{i + 1}', **extra} for i, (target, extra) in enumerate(STEPS)]})
path.write_bytes((json.dumps(data, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))
for lang, index in (('en_us', 0), ('ja_jp', 1)):
    p = A / 'lang' / f'{lang}.json'
    raw = p.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
    strings = json.loads(raw)
    for key, text in TEXTS.items(): strings[f'create_refinery_n_reactors.ponder.shared.guide.{ID}.{key}'] = text[index]
    strings[f'create_refinery_n_reactors.ponder.plant_{ID}.header'] = TEXTS['title'][index]
    p.write_bytes((json.dumps(strings, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))
