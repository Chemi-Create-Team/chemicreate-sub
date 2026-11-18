#!/usr/bin/env python3
"""Flow regulator Ponder: a valve in a straight pipe along each axis, with English/Japanese captions."""
import json, sys
sys.path.insert(0, 'tools')
from generate_reactor_assets import A, NS
from generate_petrochemical_ponder import Structure

SIDES = ('east', 'west', 'north', 'south', 'up', 'down')


def pipe(s, x, y, z, *sides):
    s.block(x, y, z, 'create:fluid_pipe', **{side: str(side in sides).lower() for side in SIDES}, waterlogged='false')


def tank(s, x, y, z):
    s.block(x, y, z, 'create:fluid_tank', bottom='true', top='true', shape='plain')


s = Structure(11, 7)
tank(s, 1, 2, 4)
s.pump(2, 2, 4, 'east')
s.block(3, 2, 4, NS + 'flow_regulator', axis='x')
pipe(s, 4, 2, 4, 'east', 'west')
tank(s, 5, 2, 4)
tank(s, 8, 2, 1)
s.pump(8, 2, 2, 'south')
s.block(8, 2, 3, NS + 'flow_regulator', axis='z')
pipe(s, 8, 2, 4, 'north', 'south')
tank(s, 8, 2, 5)
tank(s, 8, 1, 8)
s.pump(8, 2, 8, 'up')
s.block(8, 3, 8, NS + 'flow_regulator', axis='y')
pipe(s, 8, 4, 8, 'up', 'down')
tank(s, 8, 5, 8)
s.save('flow_regulation')
source = A / 'ponder/chemistry/flow_regulation.nbt'
(A / 'ponder/plant/flow_regulation.nbt').write_bytes(source.read_bytes())
source.unlink()

texts = {
    'title': ('Flow regulator', '流量調節器'),
    'step_0': ('Place the regulator in a pipe and use a pump to move the fluid.',
        '流量調節器を配管の途中に置き、ポンプで流します。'),
    'step_1': ('Hold a wrench to set flow from 0 to 256 mB/t in 16 mB/t steps.',
        'レンチ長押しで流量を設定します。0～256 mB/tを16 mB/t刻みで選べます。'),
    'step_2': ('The limit is shared by every pipe branch behind the regulator. Engineer\'s Goggles show the set flow.',
        '上限は調節器の先の配管すべてで共有されます。ゴーグルで設定した流量を確認できます。'),
}
targets = [[3, 2, 4], [3, 2, 4], [8, 2, 3]]
extras = [{}, {'wrench': True}, {}]
catalog = A / 'ponder/tutorials.json'
raw = catalog.read_bytes().decode('utf-8')
newline = '\r\n' if '\r\n' in raw else '\n'
data = [t for t in json.loads(raw) if t['id'] != 'flow_regulation']
data.append({'id': 'flow_regulation', 'size': 11, 'height': 7, 'scale': 0.58, 'components': ['flow_regulator'],
    'steps': [{'target': pos, 'key': f'step_{i}', **extra} for i, (pos, extra) in enumerate(zip(targets, extras))]})
catalog.write_bytes((json.dumps(data, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))
for lang, index in (('en_us', 0), ('ja_jp', 1)):
    p = A / 'lang' / f'{lang}.json'
    raw = p.read_bytes().decode('utf-8')
    newline = '\r\n' if '\r\n' in raw else '\n'
    strings = json.loads(raw)
    for key, text in texts.items():
        strings[f'create_refinery_n_reactors.ponder.shared.guide.flow_regulation.{key}'] = text[index]
    strings['create_refinery_n_reactors.ponder.plant_flow_regulation.header'] = texts['title'][index]
    p.write_bytes((json.dumps(strings, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))
