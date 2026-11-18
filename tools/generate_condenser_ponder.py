#!/usr/bin/env python3
"""RBMK terminal-turbine condensate, pressure-rated receiver and feedwater mixing Ponder."""
import json, sys
sys.path.insert(0, 'tools')
from generate_reactor_assets import A, NS
from generate_petrochemical_ponder import Structure, OUT

SIDES = ('east', 'west', 'north', 'south', 'up', 'down')


def pipe(s, x, y, z, *sides):
    s.block(x, y, z, NS + 'stainless_steel_pipe', **{side: str(side in sides).lower() for side in SIDES}, waterlogged='false')


s = Structure(14, 7)
# The terminal turbine facility's 30 C liquid outlet enters at the open end on the left.
pipe(s, 0, 3, 3, 'east', 'west')
s.pump(1, 3, 3, 'east')
pipe(s, 2, 3, 3, 'east', 'west')
# A pressure-rated condensate receiver and its feedwater pump isolate the low-pressure side.
for y in range(2, 5):
    s.block(3, y, 3, NS + 'stainless_steel_tank')
pipe(s, 4, 3, 3, 'east', 'west')
s.pump(5, 3, 3, 'east')
pipe(s, 6, 3, 3, 'east', 'west')
# The 284 C separator-water line joins immediately upstream of the main circulation pump.
pipe(s, 7, 5, 3, 'up', 'down')
pipe(s, 7, 4, 3, 'up', 'down')
pipe(s, 7, 3, 3, 'west', 'east', 'up')
s.pump(8, 3, 3, 'east')
for x in range(9, 12):
    pipe(s, x, 3, 3, 'east', 'west')
# A stretch of the reactor's lower structure with one exposed feed-tube bundle.
for y in range(1, 6):
    for z in range(2, 5):
        if (y, z) != (3, 3):
            s.block(12, y, z, NS + 'biological_shield')
s.block(12, 3, 3, NS + 'reactor_feed_bundle')
s.save('steam_condenser')
(A / 'ponder/plant/steam_condenser.nbt').write_bytes((OUT / 'steam_condenser.nbt').read_bytes())
(OUT / 'steam_condenser.nbt').unlink()

texts = {
    'title': ('RBMK surface condenser and feedwater return', 'RBMKの表面復水器と給水循環'),
    'step_1': ('Collect 30 °C water from the LP turbine. No separate radiator is needed.',
        '低圧タービンから30 °Cの水を回収します。別のラジエーターは不要です。'),
    'step_2': ('Pump condensate into a pressure-rated tank, then use a second pump for feedwater.',
        '復水をポンプで耐圧タンクへ送り、別のポンプで給水側へ送ります。'),
    'step_3': ('Return 284 C liquid from the gas-liquid separator directly to the main circulation pump suction; do not pass it through the condensate tank.',
        '気液分離器の284 °C液体水は復水タンクを通さず、主循環ポンプの吸込ヘッダーへ直接戻します。'),
    'step_4': ('Mix about 5.512% condensate with separator water and return it at 270 °C through ultra-high-pressure pipes.',
        '復水約5.512%と分離水を混ぜ、270 °Cで給水細管束へ戻します。超高圧配管を使います。'),
}
targets = [[0, 3, 3], [3, 3, 3], [7, 4, 3], [12, 3, 3]]
extras = [{}, {'speed': 64}, {}, {'speed': 64}]
catalog = A / 'ponder/tutorials.json'
raw = catalog.read_bytes().decode('utf-8')
newline = '\r\n' if '\r\n' in raw else '\n'
data = json.loads(raw)
index = next((i for i, tutorial in enumerate(data) if tutorial['id'] == 'steam_condenser'), len(data))
data = [t for t in data if t['id'] != 'steam_condenser']
entry = {'id': 'steam_condenser', 'size': 14, 'height': 7, 'scale': 0.6, 'components': ['low_pressure_turbine_end', 'stainless_steel_tank', 'ammonia_separator', 'stainless_steel_pipe', 'reactor_feed_bundle'],
    'steps': [{'target': pos, 'key': f'step_{i + 1}', **extra} for i, (pos, extra) in enumerate(zip(targets, extras))]}
data.insert(index, entry)
catalog.write_bytes((json.dumps(data, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))
for lang, index in (('en_us', 0), ('ja_jp', 1)):
    p = A / 'lang' / f'{lang}.json'
    raw = p.read_bytes().decode('utf-8')
    newline = '\r\n' if '\r\n' in raw else '\n'
    strings = json.loads(raw)
    for key, text in texts.items():
        strings[f'create_refinery_n_reactors.ponder.shared.guide.steam_condenser.{key}'] = text[index]
    strings['create_refinery_n_reactors.ponder.plant_steam_condenser.header'] = texts['title'][index]
    p.write_bytes((json.dumps(strings, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))
