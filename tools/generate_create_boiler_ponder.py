#!/usr/bin/env python3
"""Ponder for Create's standing tank boiler: a tank on a Blaze Burner, a pump on its roof and a pipe to a gas receiver.
Writes the structure, the catalogue entry after the horizontal tank boilers and the captions. Run from the repository root,
then run tools/generate_ponder_language.py."""
import json, sys
sys.path.insert(0, 'tools')
from generate_reactor_assets import A, NS, ROOT
from generate_petrochemical_ponder import Structure, OUT

IDENT = 'create_boiler_steam'
s = Structure(9, 6)
s.burner(4, 1, 4)
s.block(4, 2, 4, 'create:fluid_tank', bottom='true', top='true', shape='window')
s.block(5, 2, 4, 'create:fluid_pipe', east='true', west='true', north='false', south='false', up='false', down='false', waterlogged='false')
s.pump(4, 3, 4, 'up')
s.block(4, 4, 4, 'create:fluid_pipe', down='true', west='true', north='false', south='false', up='false', east='false', waterlogged='false')
for x in (2, 3):
    s.block(x, 4, 4, 'create:fluid_pipe', east='true', west='true', north='false', south='false', up='false', down='false', waterlogged='false')
s.block(1, 4, 4, NS + 'gas_receiver', axis='x', bottom='true', top='true', shape='plain')
s.save(IDENT)
(A / f'ponder/plant/{IDENT}.nbt').write_bytes((OUT / f'{IDENT}.nbt').read_bytes())
(OUT / f'{IDENT}.nbt').unlink()

catalogue_path = A / 'ponder/tutorials.json'
catalogue = [t for t in json.loads(catalogue_path.read_text(encoding='utf-8')) if t['id'] != IDENT]
entry = {'id': IDENT, 'size': 9, 'height': 6, 'components': ['create:fluid_tank'], 'steps': [
    {'target': [4, 2, 4], 'key': 'step_1', 'reveal': [4, 1, 4, 5, 2, 4]},
    {'target': [4, 1, 4], 'key': 'step_2'},
    {'target': [4, 3, 4], 'key': 'step_3', 'reveal': [4, 3, 4, 4, 3, 4]},
    {'target': [1, 4, 4], 'key': 'step_4', 'reveal': [1, 4, 4, 4, 4, 4]}]}
catalogue.insert(next(i for i, t in enumerate(catalogue) if t['id'] == 'tubular_boiler_3x3') + 1, entry)
catalogue_path.write_text(json.dumps(catalogue, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')

TEXTS = {
    'title': ('Boil steam in a Create tank', 'Createのタンクで蒸気を作る', '用机械动力储罐产生蒸汽'),
    'step_1': ('A Create fluid tank of water standing on lit Blaze Burners boils it into ordinary steam: one mB of steam for each mB of water.',
        '水を入れたCreateの液体タンクを点火したブレイズバーナーの上に置くと、水が通常蒸気になります。水1 mBから蒸気1 mBです。',
        '装水的机械动力流体储罐放在点燃的烈焰人燃烧室上，会把水煮成普通蒸汽：1 mB水生成1 mB蒸汽。'),
    'step_2': ('Each heat level under the tank gives 4,000 J/t. A steam engine or whistle on the tank makes it Create\'s boiler instead, which makes no steam for pipes.',
        'タンクの下の加熱段階1あたり4,000 J/tです。蒸気エンジンか汽笛を付けるとCreateのボイラーとして働き、配管へ出す蒸気は作りません。',
        '储罐下方每级加热提供4,000 J/t。安装蒸汽引擎或汽笛后改为机械动力锅炉，不再向管道输出蒸汽。'),
    'step_3': ('While it boils, the roof gives the steam: draw it with a pump on top, like any other fluid. The other faces still take feed water.',
        '沸騰中は天面から蒸気が出ます。ほかの流体と同じく、上に置いたポンプで取り出します。ほかの面からはこれまでどおり給水できます。',
        '沸腾时顶面输出蒸汽，与其他流体一样用顶部的泵抽出。其他面仍可供水。'),
    'step_4': ('Any pipe carries it, even one lying on the tank. Send it to a turbine, a gas tank or a condenser.',
        'どの配管でも運べ、タンクに接して通しても構いません。タービン、気体タンク、復水器へ送ります。',
        '任何管道都能输送，贴着储罐铺设也可以。送往汽轮机、气体储罐或冷凝器。'),
}
lessons_path = ROOT / 'tools/data/ponder_lessons.json'
lessons = json.loads(lessons_path.read_text(encoding='utf-8'))
for key, (en, ja, zh) in TEXTS.items(): lessons[f'guide.{IDENT}.{key}'] = {'en_us': en, 'ja_jp': ja, 'zh_cn': zh}
lessons_path.write_text(json.dumps(lessons, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
