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
    'title': ('Toxic gas and the gas mask', '有毒ガスとガスマスク', '有毒气体与防毒面具'),
    'step_1': ('Use suitable pipes and tanks for toxic gases such as chlorine and hydrogen sulfide.',
        '塩素や硫化水素などの有毒ガスは、対応する配管とタンクで扱います。',
        '氯气、硫化氢等有毒气体须使用适合的管道与储罐。'),
    'step_2': ('Leaked gas clouds cause damage. Keep clear and stop the leak.',
        '漏れたガスの雲に触れるとダメージを受けます。近づかず、漏れを止めます。',
        '接触泄漏气云会受伤，保持距离并停止泄漏。'),
    'step_3': ('Connect the relief outlet to a collection tank to keep gas out of the surroundings.',
        '安全弁の出口を回収タンクへつなぐと、周囲へガスを漏らさずに済みます。',
        '安全阀出口接回收罐，可避免向周围释放气体。'),
    'step_4': ('A gas mask on the head keeps the gas out. Its filter lasts 1,200 seconds in the gas; two charcoal at a crafting table renew it.',
        'ガスマスクを頭に着けると、ガスを防げます。吸収缶はガスの中で1,200秒もちます。作業台で木炭2個を足すと新しくなります。',
        '头戴防毒面具可隔绝气体。滤罐在气云中可使用1,200秒，在工作台加入2个木炭即可更换。'),
    'step_5': ('Place the scrubber above the relief valve and feed caustic soda at the side. Gas passes bottom to top; keep the top clear.',
        '安全弁の真上へスクラバーを置き、側面へ苛性ソーダ液を入れます。ガスは下から上へ通るので、上面を空けます。',
        '安全阀正上方放洗涤器，侧面供苛性钠溶液，气体自下向上通过，顶部须畅通。'),
    'step_6': ('Each 1 mB caustic neutralises 1 mB of the listed acidic gases. A shortage still makes a cloud and alkaline ammonia passes through. Goggles and a Display Link show its connection, wash state and caustic amount.',
        '苛性ソーダ液1 mBで対象の酸性ガス1 mBを中和します。不足分は雲になり、塩基性のアンモニアは通過します。接続・洗浄状態・残量はゴーグルとディスプレイリンクで確認できます。',
        '每1 mB烧碱中和1 mB目标酸性气体。不足部分仍形成气云，碱性的氨会直接通过。可用工程师护目镜和显示链接查看连接、洗涤状态与余量。'),
}
STEPS = [([2, 2, 3], {'item': NS + 'stainless_steel_tank'}), ([3, 2, 3], {}), ([4, 1, 3], {}), ([2, 2, 3], {'item': NS + 'gas_mask'}),
    ([3, 3, 3], {'item': NS + 'relief_scrubber'}), ([3, 3, 3], {})]

path = A / 'ponder/tutorials.json'
raw = path.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
data = json.loads(raw)
entry = {'id': ID, 'size': 7, 'height': 4, 'components': ['gas_mask', 'relief_scrubber'],
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
