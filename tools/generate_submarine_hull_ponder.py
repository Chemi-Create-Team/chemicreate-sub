#!/usr/bin/env python3
"""Ponder: the stainless steel and titanium blocks as storage and, with Create: Deep Seas, as submarine pressure hulls; English/Japanese
captions. Run from the repository root."""
import json, sys
sys.path.insert(0, 'tools')
from generate_reactor_assets import A, NS
from generate_petrochemical_ponder import Structure, OUT

ID = 'submarine_hull'

s = Structure(7, 5)
# Two curved hull panels side by side: stainless steel on the left, titanium on the right.
for x0, block in ((1, 'stainless_steel_block'), (4, 'titanium_block')):
    for y in range(1, 4):
        for x in range(x0, x0 + 2):
            s.block(x, y, 4 if y == 2 else 3, NS + block)
s.save(ID)
(A / f'ponder/plant/{ID}.nbt').write_bytes((OUT / f'{ID}.nbt').read_bytes())
(OUT / f'{ID}.nbt').unlink()

TEXTS = {
    'title': ('Stainless steel and titanium hulls', 'ステンレス鋼とチタンの耐圧殻'),
    'step_1': ('Nine ingots make a block of stainless steel or titanium, and a block gives the nine back. Both count as storage blocks.',
        'インゴット9個でステンレス鋼ブロックやチタンブロックができ、ブロックからインゴット9個に戻せます。どちらも保管用のブロックです。'),
    'step_2': ('With Deep Seas installed, use it for pressure hulls. Stainless steel supports a depth of 600 blocks.',
        'Deep Seas導入時は耐圧殻に使えます。ステンレス鋼の最大水深は600マスです。'),
    'step_3': ('A titanium pressure hull supports depths up to 1,200 blocks.',
        'チタン製の耐圧殻なら最大水深1,200マスまで潜れます。'),
    'step_4': ('This integration needs Deep Seas. Settings are in config/submarine_hull.json.',
        'この連携にはDeep Seasが必要です。設定はconfig/submarine_hull.jsonで変更できます。'),
}
STEPS = [([1, 2, 4], {'item': NS + 'stainless_steel_ingot'}), ([1, 2, 4], {}), ([4, 2, 4], {}), ([4, 3, 3], {})]

path = A / 'ponder/tutorials.json'
raw = path.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
data = [t for t in json.loads(raw) if t['id'] != ID]
data.append({'id': ID, 'size': 7, 'height': 5, 'components': ['stainless_steel_block', 'titanium_block'],
    'steps': [{'target': target, 'key': f'step_{i + 1}', **extra} for i, (target, extra) in enumerate(STEPS)]})
path.write_bytes((json.dumps(data, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))
for lang, index in (('en_us', 0), ('ja_jp', 1)):
    p = A / 'lang' / f'{lang}.json'
    raw = p.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
    strings = json.loads(raw)
    for key, text in TEXTS.items(): strings[f'create_refinery_n_reactors.ponder.shared.guide.{ID}.{key}'] = text[index]
    strings[f'create_refinery_n_reactors.ponder.plant_{ID}.header'] = TEXTS['title'][index]
    p.write_bytes((json.dumps(strings, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))
