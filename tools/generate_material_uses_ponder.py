#!/usr/bin/env python3
"""Ponder for PUREX-style spent-fuel recovery, with English, Japanese and Chinese captions."""
import json
import sys

sys.path.insert(0, 'tools')
from generate_petrochemical_ponder import Structure
from generate_reactor_assets import A, NS
from generate_uranium_assets import plant, save

ID = 'spent_fuel_reprocessing'

s = Structure(9, 7)
plant(s, 4, 2, 4, 'stirred', 1, ((1, 0), (-1, 0)))
for y in (1, 2):
    s.block(1, y, 4, NS + 'stainless_steel_tank', bottom=str(y == 1).lower(), top=str(y == 2).lower(), shape='window')
s.block(2, 1, 4, 'create:andesite_casing')
s.pump(2, 2, 4, 'east')
save(s, ID)

TEXTS = {
    'title': ('Reprocessing spent fuel', '使用済み燃料を再処理する', '后处理乏燃料'),
    'step_1': ('Cool spent rods in the pool, shear them with a mechanical saw, then send the chopped fuel to this heated reaction plant.',
        '使用済み燃料をプールで冷却し、メカニカルソーでせん断してから、この加熱反応プラントへ送ります。',
        '冷却乏燃料后用机械锯剪切，将碎料送入加热反应装置。'),
    'step_2': ('One chopped fuel batch and 4,000 mB nitric acid produce 4,000 mB dissolved spent fuel. Send it to the pulse extraction column.',
        'せん断燃料1個と硝酸4,000 mBから燃料溶解液4,000 mBを作り、パルス抽出塔へ送ります。',
        '碎料和4,000 mB硝酸产生4,000 mB溶解液，送往脉冲萃取塔。'),
    'step_3': ('The extraction column separates U, Pu and high-level waste. Recycle the solvent; fabricate MOX and vitrify the waste. See the fuel-cycle Ponder.',
        '抽出塔でU・Pu・高レベル廃液を分離。溶媒を循環し、MOX製造と廃液ガラス固化へ進みます。燃料サイクルPonderを参照。',
        '萃取塔分离铀、钚和高放废液，循环溶剂，制造MOX并进行玻璃固化。请参阅燃料循环Ponder。'),
    'step_4': ('A spent control rod and 500 mB nitric acid return one reactor graphite. Its coal blocks and metal sheets are not recovered.',
        '使用済み制御棒1本と硝酸500 mBから、原子炉用黒鉛1個を回収できます。石炭ブロックと鉄板は戻りません。',
        '1根废控制棒与500 mB硝酸可回收1个反应堆石墨；煤炭块和金属板不会回收。'),
}
STEPS = [([1, 2, 4], {'item': NS + 'spent_fuel_rod'}), ([4, 2, 4], {'speed': 32, 'item': NS + 'nitric_acid_bucket'}),
         ([5, 3, 4], {'item': NS + 'uranium_trioxide'}), ([4, 2, 4], {'item': NS + 'spent_control_rod'})]

path = A / 'ponder/tutorials.json'
raw = path.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
catalog = json.loads(raw)
entry = {'id': ID, 'size': 9, 'height': 7, 'components': ['spent_fuel_rod', 'spent_control_rod'],
    'steps': [{'target': target, 'key': f'step_{i + 1}', **extra} for i, (target, extra) in enumerate(STEPS)]}
for index, tutorial in enumerate(catalog):
    if tutorial['id'] == ID: catalog[index] = entry; break
else: catalog.append(entry)
path.write_bytes((json.dumps(catalog, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))

for language, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
    path = A / 'lang' / f'{language}.json'; raw = path.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'; strings = json.loads(raw)
    prefix = f'create_refinery_n_reactors.ponder.shared.guide.{ID}.'
    for key in [key for key in strings if key.startswith(prefix)]: del strings[key]
    for key, values in TEXTS.items(): strings[prefix + key] = values[index]
    strings[f'create_refinery_n_reactors.ponder.plant_{ID}.header'] = TEXTS['title'][index]
    path.write_bytes((json.dumps(strings, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))

print('Generated the spent-fuel reprocessing Ponder')
