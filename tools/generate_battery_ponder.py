#!/usr/bin/env python3
"""Ponder: building a lead-acid or lithium-ion battery bank, filling it with electrolyte and wiring it. Run from the repository root."""
import json, sys
sys.path.insert(0, 'tools')
from generate_reactor_assets import A, NS
from generate_petrochemical_ponder import Structure, OUT, DIRECTIONS
import generate_ponder_language as language

ID = 'battery_bank'
SHARED = 'create_refinery_n_reactors.ponder.shared.guide.'
LANGS = ('en_us', 'ja_jp', 'zh_cn')

s = Structure(7, 4)
for x in range(1, 4):
    for z in range(2, 5):
        for y in (1, 2): s.block(x, y, z, NS + 'lead_acid_battery_casing')
s.block(2, 3, 3, NS + 'lead_acid_battery_terminal', facing='north')
for x in (3, 4): s.block(x, 3, 3, 'create:fluid_pipe', **{side: str(side in ('east', 'west')).lower() for side in DIRECTIONS}, waterlogged='false')
for y, (bottom, top) in zip((1, 2, 3), (('true', 'false'), ('false', 'false'), ('false', 'true'))): s.block(5, y, 3, 'create:fluid_tank', bottom=bottom, top=top, shape='window' if y == 2 else 'plain')
s.save(ID)
(A / f'ponder/plant/{ID}.nbt').write_bytes((OUT / f'{ID}.nbt').read_bytes())
(OUT / f'{ID}.nbt').unlink()

TEXTS = {
    'title': ('Lead-acid and lithium-ion battery banks', '鉛蓄電池とリチウムイオン電池を作る', '搭建铅酸与锂离子蓄电池组'),
    'step_1': ('Stack casings of one battery type into a solid box, at least 2 blocks on every side. Each casing stores charge.',
        '同じ種類のケーシングを、各辺2ブロック以上の中身の詰まった直方体に積みます。ケーシング1個ごとに電気を蓄えます。',
        '把同一类型的外壳堆成每边至少2格的实心长方体。每个外壳都能储存电量。'),
    'step_2': ('Put the terminal on the top face of the box. Its two posts are the positive and negative poles.',
        '直方体の上面に端子を置きます。2本の端子が正極と負極です。',
        '把端子放在长方体的顶面。两个端子柱分别是正极和负极。'),
    'step_3': ('Pipe in electrolyte until it is full: 250 mB per casing. Lead-acid takes dilute sulfuric acid, lithium-ion takes acetone.',
        '電解液を配管で満たします。ケーシング1個につき250 mBで、鉛蓄電池は希硫酸、リチウムイオン電池はアセトンです。',
        '用管道注满电解液，每个外壳250 mB。铅酸电池用稀硫酸，锂离子电池用丙酮。'),
    'step_4': ('Wire the terminals to CEE. Lead-acid gives 200 V and lithium-ion 300 V when full. More casings mean more capacity and less resistance.',
        '端子をCEEへ配線します。満充電で鉛蓄電池は200 V、リチウムイオン電池は300 Vです。ケーシングが多いほど容量が増え、抵抗が下がります。',
        '把端子接入CEE电路。充满时铅酸为200 V，锂离子为300 V。外壳越多，容量越大、电阻越小。'),
    'step_5': ('A higher circuit voltage charges the bank; a full bank leaves the charging circuit. If it stops, check the box, the electrolyte and the wiring.',
        '回路の電圧が高いと充電され、満充電になると充電回路から切れます。止まったときは直方体・電解液・配線を確認します。',
        '电路电压更高时会充电，充满后会断开充电电路。若停止工作，请检查长方体、电解液和接线。'),
}
STEPS = [([2, 1, 3], {'item': NS + 'lead_acid_battery_casing'}), ([2, 3, 3], {'item': NS + 'lead_acid_battery_terminal'}), ([4, 3, 3], {'item': 'minecraft:bucket'}), ([2, 3, 3], {}), ([2, 3, 3], {})]
COMPONENTS = ['lead_acid_battery_casing', 'lead_acid_battery_terminal', 'lithium_ion_battery_casing', 'lithium_ion_battery_terminal']


def edit(path, change):
    raw = path.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
    data = change(json.loads(raw))
    path.write_bytes((json.dumps(data, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))


edit(A / 'ponder/tutorials.json', lambda data: [t for t in data if t['id'] != ID] + [{'id': ID, 'size': 7, 'height': 4, 'components': COMPONENTS,
    'steps': [{'target': target, 'key': f'step_{i + 1}', **extra} for i, (target, extra) in enumerate(STEPS)]}])
ROOT = A.parents[4]
edit(ROOT / 'tools/data/ponder_lessons.json', lambda data: {**data, **{f'guide.{ID}.{key}': dict(zip(LANGS, texts)) for key, texts in TEXTS.items()}})
for index, lang in enumerate(LANGS):
    def change(strings):
        for key, texts in TEXTS.items():
            stem = SHARED + f'{ID}.{key}'
            strings[stem] = texts[index]
            if key == 'title': continue
            count = max(len(language.pages(t)) for t in texts)
            local = language.pages(texts[index])
            strings[stem + '.lesson_pages.count'] = str(len(local))
            for i in range(count): strings[f'{stem}.lesson_pages.{i}'] = local[min(i, len(local) - 1)]
        strings[f'create_refinery_n_reactors.ponder.plant_{ID}.header'] = TEXTS['title'][index]
        return strings
    edit(A / 'lang' / f'{lang}.json', change)
print('Generated the battery bank Ponder')
