#!/usr/bin/env python3
"""Ponder: toxic gas let out by a relief valve or a failed tank or pipe, how its cloud fades, and the gas mask; English/Japanese captions.
Run from the repository root."""
import json, sys
from pathlib import Path
sys.path.insert(0, 'tools')
from generate_petrochemical_ponder import Structure, OUT

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
NS = 'create_refinery_n_reactors:'

ID = 'toxic_gas'

s = Structure(7, 4)
# A stainless tank of chlorine with a relief valve, its dedicated scrubber and a pipe that could fail.
for y in (1, 2): s.block(2, y, 3, NS + 'stainless_steel_tank', bottom=str(y == 1).lower(), top=str(y == 2).lower(), shape='window')
s.block(3, 2, 3, 'create:fluid_valve', facing='up', axis_along_first='false', enabled='true', waterlogged='false')
s.block(3, 3, 3, NS + 'relief_scrubber', facing='north', active='true')
s.block(3, 3, 2, 'create:fluid_pipe', north='true', south='true', east='false', west='false', up='false', down='false', waterlogged='false')
s.pump(3, 3, 1, 'south')
s.tank(3, 3, 0)
s.block(4, 1, 3, NS + 'pvc_pipe', east='false', west='false', north='false', south='false', up='false', down='false', waterlogged='false')
s.save(ID)
(A / f'ponder/plant/{ID}.nbt').write_bytes((OUT / f'{ID}.nbt').read_bytes())
(OUT / f'{ID}.nbt').unlink()

TEXTS = {
    'title': ('Toxic gas and the gas mask',
        '有毒ガスとガスマスク',
        '有毒气体与防毒面具'),
    'step_1': ('Use suitable pipes and tanks for toxic gases such as chlorine and hydrogen sulfide.',
        '塩素や硫化水素などの有毒ガスは、対応する配管とタンクで扱います。',
        '氯气、硫化氢等有毒气体须使用适合的管道与储罐。'),
    'step_2': ('Leaked gas becomes a cloud in the space it can reach. Heavy gases like chlorine settle on the floor, light ones like ammonia under the ceiling, and walls and closed doors hold them back.',
        '漏れたガスは、通れるマスに広がる雲になります。塩素など重いガスは床に、アンモニアなど軽いガスは天井側に溜まり、壁や閉じたドアで止まります。',
        '泄漏的气体会在可通过的空间形成气云。氯气等重气体沉在地面，氨等轻气体聚在顶部，墙壁和关闭的门会将其挡住。'),
    'step_3': ('Breathing inside a toxic cloud poisons you. Coloured gases show as mist; colourless ones such as carbon monoxide are unseen, so check with a gas detector.',
        '有毒な雲の中で息をすると毒を受けます。色のあるガスはもやとして見えますが、一酸化炭素のような無色のガスは見えないので、ガス検知器で確かめます。',
        '在有毒气云中呼吸会中毒。有颜色的气体呈雾状可见，一氧化碳等无色气体看不见，请用气体检测器确认。'),
    'step_4': ('A gas mask in the head slot protects against inhalation. Craft it with a fresh carbon filter to restore the filter.',
        '頭にガスマスクを装備すると吸入を防げます。作業台で新品の活性炭フィルターと組み合わせると残量が回復します。',
        '头戴防毒面具可防止吸入。将面具与新的活性炭滤芯合成可恢复剩余滤量。'),
    'step_5': ('A cloud of flammable gas such as hydrogen or carbon monoxide explodes when a flame or a burning creature is inside it or next to it.',
        '水素や一酸化炭素など燃えるガスの雲は、範囲の中か隣に火や燃えている生き物があると爆発します。',
        '氢气、一氧化碳等可燃气体的气云，若其中或旁边有火焰或燃烧的生物，就会爆炸。'),
    'step_6': ('Pipe toxic gas directly into a collection system. Keep its product outlet clear.',
        '有毒ガスを直接配管で回収設備へ送ります。生成物の出口を空けておきます。',
        '把有毒气体直接接管送入回收设备。保持产物出口畅通。'),
    'step_7': ('Pump toxic gas into the scrubber from below and feed caustic soda at the side. Keep the top open for exhaust.',
        '除害装置の下から有毒ガス、側面から苛性ソーダを送ります。上面は排気用に空けます。',
        '从洗涤器底部泵入有毒气体，侧面供应烧碱。顶部保持排气空间。'),
    'step_8': ('Supply caustic soda and check the scrubber with goggles. It removes acid gases; ammonia requires separate handling.',
        'スクラバーへ苛性ソーダを供給し、ゴーグルで残量を確認します。酸性ガス用なので、アンモニアは別に扱います。',
        '向洗涤器供给烧碱，用护目镜检查余量。它处理酸性气体，氨需另行处理。'),
}
# Step 2 lets chlorine out of the failed PVC pipe and step 5 clears the cloud (PlantPonderScenes.toxicGas).
STEPS = [([2, 2, 3], {'item': NS + 'stainless_steel_tank'}), ([4, 1, 3], {}), ([4, 1, 3], {'item': NS + 'gas_detector'}),
    ([2, 2, 3], {'item': NS + 'gas_mask'}), ([4, 1, 3], {'item': 'minecraft:torch'}), ([3, 2, 3], {}),
    ([3, 3, 3], {'item': NS + 'relief_scrubber'}), ([3, 3, 3], {})]

path = A / 'ponder/tutorials.json'
raw = path.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
data = json.loads(raw)
entry = {'id': ID, 'size': 7, 'height': 4, 'components': ['gas_mask', 'relief_scrubber', 'gas_detector'],
    'steps': [{'target': target, 'key': f'step_{i + 1}', **extra} for i, (target, extra) in enumerate(STEPS)]}
for index, tutorial in enumerate(data):
    if tutorial['id'] == ID: data[index] = entry; break
else: data.append(entry)
path.write_bytes((json.dumps(data, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))
for lang, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
    p = A / 'lang' / f'{lang}.json'
    raw = p.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
    strings = json.loads(raw)
    for key, text in TEXTS.items():
        full = f'create_refinery_n_reactors.ponder.shared.guide.{ID}.{key}'
        strings[full] = text[index] if index < len(text) else strings.get(full, text[0])
    title = TEXTS['title']; full = f'create_refinery_n_reactors.ponder.plant_{ID}.header'
    strings[full] = title[index] if index < len(title) else strings.get(full, title[0])
    p.write_bytes((json.dumps(strings, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))
