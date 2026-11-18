#!/usr/bin/env python3
"""Pipe and tank material Ponders: one row per pipe kind and one column per tank kind, with their limits and the wear rule, and the basics of
pressure in plain words;
English/Japanese captions. Run from the repository root."""
import json, sys
from pathlib import Path
sys.path.insert(0, 'tools')
from generate_petrochemical_ponder import Structure, OUT
ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
NS = 'create_refinery_n_reactors:'

ROWS = ('create:fluid_pipe', NS + 'resin_pipe', NS + 'pvc_pipe', NS + 'reinforced_pipe', NS + 'stainless_steel_pipe', NS + 'nickel_alloy_pipe', NS + 'zirconium_pipe',
        NS + 'ceramic_pipe', NS + 'cryogenic_pipe', NS + 'gas_pipe')
s = Structure(11, 3)
for z, pipe in enumerate(ROWS, 1):
    for x in range(2, 9):
        s.block(x, 1, z, pipe, east='true', west='true', north='false', south='false', up='false', down='false', waterlogged='false')
s.save('pipe_materials')
(A / 'ponder/plant/pipe_materials.nbt').write_bytes((OUT / 'pipe_materials.nbt').read_bytes())
(OUT / 'pipe_materials.nbt').unlink()
TANKS = ('create:fluid_tank', NS + 'stainless_steel_tank', NS + 'cryogenic_tank', NS + 'pressure_tank', NS + 'gas_receiver')
t = Structure(11, 4)
for i, tank in enumerate(TANKS):
    for y in (1, 2):
        t.block(1 + 2 * i, y, 4, tank, bottom=str(y == 1).lower(), top=str(y == 2).lower(), shape='plain' if tank == NS + 'gas_receiver' else 'window', **({'axis': 'y'} if tank == NS + 'gas_receiver' else {}))
t.save('tank_materials')
(A / 'ponder/plant/tank_materials.nbt').write_bytes((OUT / 'tank_materials.nbt').read_bytes())
(OUT / 'tank_materials.nbt').unlink()
b = Structure(10, 4)
for y in (1, 2):
    for x, tank in ((1, 'create:fluid_tank'), (6, NS + 'stainless_steel_tank'), (8, NS + 'cryogenic_tank')):
        b.block(x, y, 4, tank, bottom=str(y == 1).lower(), top=str(y == 2).lower(), shape='plain' if tank == NS + 'gas_receiver' else 'window', **({'axis': 'y'} if tank == NS + 'gas_receiver' else {}))
b.block(2, 1, 4, 'create:mechanical_pump', facing='east', waterlogged='false')
for x in (3, 5): b.block(x, 1, 4, NS + 'reinforced_pipe', east='true', west='true', north='false', south='false', up='false', down='false', waterlogged='false')
b.block(4, 1, 4, NS + 'pipe_pressure_gauge', axis='x')
b.save('pressure_basics')
(A / 'ponder/plant/pressure_basics.nbt').write_bytes((OUT / 'pressure_basics.nbt').read_bytes())
(OUT / 'pressure_basics.nbt').unlink()

TEXTS = {
    'title': ('Choosing a pipe for the fluid', '流体に合う配管を選ぶ', '根据流体选择管道'),
    'step_1': ('Limits: copper 450 K/2 bar, resin 400 K/2 bar, PVC 333 K/10 bar. Choose a pipe suited to the fluid.',
        '銅管は450 K・2 bar、樹脂管は400 K・2 bar、塩ビ管は333 K・10 barまでです。流体に合う管を選びます。',
        '上限：铜管450 K、2 bar；树脂管400 K、2 bar；PVC管333 K、10 bar。按流体选择管材。'),
    'step_2': ('For acids early on, use ceramic pipe fired from clay. It also takes hot acids, but only at atmospheric pressure and not fluorides.',
        '序盤の酸には、粘土を焼いたセラミック管を使います。熱い酸にも耐えますが、常圧だけで、フッ化物には使えません。',
        '前期输送酸液使用由黏土烧制的陶瓷管。它也耐热酸，但只能用于常压，不能用于氟化物。'),
    'step_3': ('Reinforced steel pipe supports 800 K and 16 bar. Do not use it for acids.',
        '耐圧鋼管は800 K・16 barまで使えます。酸には使いません。',
        '增强钢管上限800 K、16 bar，不用于酸液。'),
    'step_4': ('Stainless steel pipe resists acids up to 1000 K and 25 bar. Like Create\'s pipe, a wrench gives the glass pipe and casing the encased one.',
        'ステンレス鋼管は酸に耐え、1000 K・25 barまで使えます。Createの配管と同じく、レンチでガラス付き、ケーシングでケース入りになります。',
        '不锈钢管耐酸，可承受1000 K和25 bar。与Create管道相同，可用扳手变成玻璃管，也可加外壳。'),
    'step_5': ('Fluorine, hydrogen fluoride and uranium hexafluoride eat even stainless steel. Only the nickel alloy (Monel) pipe holds them.',
        'フッ素・フッ化水素・六フッ化ウランは、ステンレス鋼も腐食します。耐えられるのはニッケル合金（モネル）管だけです。',
        '氟气、氟化氢和六氟化铀连不锈钢也会腐蚀，只有镍合金（蒙乃尔）管能够承受。'),
    'step_6': ('Use zirconium pipe for hot sulfate solutions, up to 900 K and 25 bar.',
        '高温の硫酸塩液はジルコニウム管で運びます。上限は900 K・25 barです。',
        '高温硫酸盐溶液用锆管，上限900 K、25 bar。'),
    'step_7': ('Use cryogenic pipes for fluids such as liquid oxygen that must stay cold.',
        '液化酸素など、冷たさを保って運ぶ流体には保冷配管を使います。',
        '液氧等需要保持低温的流体使用低温管线。'),
    'step_8': ('Check pipe material when warned. Right-click worn pipes with a matching plate or ingot to repair them.',
        '警告が出たら配管の材質を確認します。傷んだ管は同じ材料の板かインゴットで右クリックして修理します。',
        '出现警告时检查管材，手持同材质板或锭右键修理磨损管道。'),
}
STEPS = [([5, 1, z], {}) for z in (2, 8, 4, 5, 6, 7, 9, 10)]
TANK_TEXTS = {
    'title': ('Choosing a tank for the fluid', '流体に合うタンクを選ぶ', '根据流体选择储罐'),
    'step_1': ('Create tanks support 16 bar. Use a compatible tank material for corrosive fluids.',
        'Createのタンクは16 barまでです。腐食性流体には対応する材質のタンクを使います。',
        'Create储罐上限16 bar，腐蚀性流体须使用适合材质的罐体。'),
    'step_2': ('The stainless steel tank holds any liquid or gas, corrosive ones included, up to 800 °C.',
        'ステンレス鋼タンクは、腐食性のものを含めて、どの液体・気体も800 °Cまで入れられます。',
        '不锈钢储罐可容纳包括腐蚀性流体在内的任何液体或气体，最高800 °C。'),
    'step_3': ('Store liquefied gases in cryogenic tanks. Send them to a normal tank or heat exchanger to turn them back into gas.',
        '液化ガスは保冷タンクへ入れます。気体に戻すときは通常タンクか熱交換器へ送ります。',
        '液化气存入低温罐，需要气化时送入普通罐或换热器。'),
    'step_4': ('Pressure tanks support 50 bar and 500 °C. Use them between the fusion reactor and steam main.',
        '耐圧タンクは50 bar・500 °Cまでです。核融合炉と蒸気主管の間にも使えます。',
        '承压罐上限50 bar、500 °C，也可用于聚变炉与蒸汽主管之间。'),
    'step_5': ('The gas receiver holds gases up to 50 bar. The open cylinder tank refuses corrosive fluids.',
        '気体レシーバーは気体を50 barまで入れられます。開放円筒タンクは腐食性の流体を受け付けません。',
        '气体储罐可储存最高50 bar的气体。开放圆筒储罐拒绝腐蚀性流体。'),
    'step_6': ('Avoid touching hot or cold pipes and tanks. Fire resistance protects from heat; leather armour protects from cold.',
        '高温・低温の管やタンクへ直接触れないようにします。熱には耐火、冷気には革防具が有効です。',
        '避免直接接触过热或过冷的管罐，耐火效果防热，皮革护甲防寒。'),
}
TANK_TEXTS['step_7'] = ("Fit a relief valve to the tank side and set it below the tank's pressure limit.",
    'タンクの側面へ安全弁を付け、限界より低い圧力で開くようにします。',
    '罐体侧面安装安全阀，开启压力设为低于罐体上限。')
TANK_STEPS = [([1 + 2 * i, 2, 4], {}) for i in range(5)] + [([1, 1, 4], {}), ([3, 1, 4], {})]
BASICS_TEXTS = {
    'title': ('Pressure, the basics', '圧力のきほん', '压力基础'),
    'step_1': ('Pressure is shown in bar. Read it with a pressure gauge or goggles.',
        '圧力はbarで表示します。圧力計やゴーグルで確認できます。',
        '压力以bar显示，用压力计或护目镜查看。'),
    'step_2': ('Check pipe and tank pressure ratings: copper pipe 2 bar, Create tank 16 bar.',
        '配管とタンクの耐圧を確認します。銅管は2 bar、Createタンクは16 barです。',
        '检查管罐耐压，铜管为2 bar，Create储罐为16 bar。'),
    'step_3': ('Continued gas input raises pressure. Leave room in the receiving tank.',
        '気体を送り続けると圧力が上がります。回収先に空きを確保します。',
        '持续送气会升压，为接收罐留出空间。'),
    'step_4': ('Watch the pressure gauge while heating. Stop heating if pressure rises too far.',
        '加熱中も圧力計を確認します。上がりすぎたら加熱を止めます。',
        '加热时也要查看压力计，压力过高时停止加热。'),
    'step_5': ("Heat a closed vessel carefully and control pressure with its relief valve.",
        '密閉槽は圧力を見ながら加熱し、安全弁で圧力を管理します。',
        '加热密闭槽时观察压力，并用安全阀控制压力。'),
    'step_6': ('Liquid ammonia can be pressurised even at room temperature. Use cryogenic tanks and pipes for cold liquefied gases.',
        '液体アンモニアなどは常温でも加圧されます。冷たい液化ガスは保冷タンクと保冷配管へ送ります。',
        '液氨在常温下也有压力，低温液化气使用低温储罐与管线。'),
    'step_7': ('Ignoring pressure warnings can cause failure. Stop, lower pressure and repair damaged pipes.',
        '圧力警告を放置すると破損します。停止して圧力を下げ、傷んだ管を修理します。',
        '放任压力警告会导致损坏，应停机降压并修理管道。'),
    'step_8': ('Choose suitably rated pipes and tanks, then add reducing and relief valves.',
        '耐圧に合う配管・タンクを選び、減圧弁と安全弁を組み合わせます。',
        '选择耐压合适的管罐，并配合减压阀与安全阀。'),
}
BASICS_STEPS = [([1, 2, 4], {}), ([4, 1, 4], {}), ([2, 1, 4], {}), ([6, 2, 4], {}), ([6, 1, 4], {'item': 'create:blaze_burner'}), ([8, 2, 4], {}), ([3, 1, 4], {}), ([5, 1, 4], {})]

authored=json.loads((ROOT/'tools/data/ponder_lessons.json').read_text())
for scene,texts in (('pipe_materials',TEXTS),('tank_materials',TANK_TEXTS),('pressure_basics',BASICS_TEXTS)):
    for key in texts:
        if f'guide.{scene}.{key}' in authored:texts[key]=tuple(authored[f'guide.{scene}.{key}'][lang] for lang in ('en_us','ja_jp','zh_cn'))

path = A / 'ponder/tutorials.json'
raw = path.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
data = json.loads(raw)
entries = [
    {'id': 'pipe_materials', 'size': 11, 'height': 3,
     'components': ['stainless_steel_pipe', 'ceramic_pipe', 'resin_pipe', 'pvc_pipe', 'nickel_alloy_pipe', 'zirconium_pipe', 'reinforced_pipe', 'cryogenic_pipe', 'gas_pipe'],
     'steps': [{'target': target, 'key': f'step_{i + 1}', **extra} for i, (target, extra) in enumerate(STEPS)]},
    {'id': 'tank_materials', 'size': 11, 'height': 4, 'components': ['stainless_steel_tank', 'cryogenic_tank', 'pressure_tank', 'gas_receiver'],
     'steps': [{'target': target, 'key': f'step_{i + 1}', **extra} for i, (target, extra) in enumerate(TANK_STEPS)]},
    {'id': 'pressure_basics', 'size': 10, 'height': 4,
     'components': ['reinforced_pipe', 'stainless_steel_pipe', 'pipe_pressure_gauge', 'stainless_steel_tank', 'cryogenic_tank', 'gas_receiver'],
     'steps': [{'target': target, 'key': f'step_{i + 1}', **extra} for i, (target, extra) in enumerate(BASICS_STEPS)]},
]
for entry in entries:
    entry['components']=list(dict.fromkeys(entry['components']))
    for index, tutorial in enumerate(data):
        if tutorial['id'] == entry['id']: data[index] = entry; break
    else: data.append(entry)
path.write_bytes((json.dumps(data, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))
for lang, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
    p = A / 'lang' / f'{lang}.json'
    raw = p.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
    strings = json.loads(raw)
    for scene, texts in (('pipe_materials', TEXTS), ('tank_materials', TANK_TEXTS), ('pressure_basics', BASICS_TEXTS)):
        for key in [k for k in strings if k.startswith(f'create_refinery_n_reactors.ponder.shared.guide.{scene}.')]: del strings[key]
        for key, value in texts.items():
            full = f'create_refinery_n_reactors.ponder.shared.guide.{scene}.{key}'
            strings[full] = value[index] if index < len(value) else strings.get(full, value[0])
        title = texts['title']; full = f'create_refinery_n_reactors.ponder.plant_{scene}.header'
        strings[full] = title[index] if index < len(title) else strings.get(full, title[0])
    p.write_bytes((json.dumps(strings, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))
