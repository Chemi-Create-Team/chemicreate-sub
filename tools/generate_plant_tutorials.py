#!/usr/bin/env python3
"""Author the additive Ponder catalogue, bilingual captions and model-scale structures.

The catalogue is also read by the client and checked by server GameTests. This
keeps registration, explanation targets and shipped structures in agreement.
"""
from pathlib import Path
import json
from generate_petrochemical_ponder import Structure

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
NS = 'create_refinery_n_reactors:'
PREFIX = 'ponder.create_refinery_n_reactors.guide.'
TUTORIALS = []
TRANSLATIONS = {'en_us': {}, 'ja_jp': {}}


def add(identifier, layout, components, english, japanese, steps):
    tutorial = dict(id=identifier, layout=layout, components=components.split(), steps=[])
    for language, title in [('en_us', english), ('ja_jp', japanese)]:
        TRANSLATIONS[language][PREFIX + identifier + '.title'] = title
    for index, step in enumerate(steps):
        target, en, ja, *extra = step
        entry = dict(target=target, key='step_' + str(index + 1))
        if extra:
            entry.update(extra[0])
        tutorial['steps'].append(entry)
        TRANSLATIONS['en_us'][PREFIX + identifier + '.' + entry['key']] = en
        TRANSLATIONS['ja_jp'][PREFIX + identifier + '.' + entry['key']] = ja
    TUTORIALS.append(tutorial)


add('flywheel_storage', 'flywheel', 'large_flywheel white_large_flywheel orange_large_flywheel magenta_large_flywheel light_blue_large_flywheel lime_large_flywheel pink_large_flywheel gray_large_flywheel light_gray_large_flywheel cyan_large_flywheel purple_large_flywheel blue_large_flywheel brown_large_flywheel green_large_flywheel red_large_flywheel black_large_flywheel', 'Coupling and inspecting large flywheels', '大型フライホイールを連結する', [
([1,1,3], 'Align the flywheel axis with Create shafts and provide a powered kinetic network.', 'フライホイールの軸をCreateのシャフトに合わせ、回転動力を供給します。', {'speed':64}),
([3,1,3], 'Adjacent flywheels on the same axis couple together. Charge them with external rotation first.', '同じ軸に隣接させると連結します。外部動力で回してから使います。'),
([5,1,3], 'Use goggles to check stored energy and power transfer.', '蓄えた動力と出入力量はゴーグルで確認します。'),
([3,1,3], 'Dye changes the colour. Stop the flywheels before rearranging them.', '染料で色を変えられます。組み替える前に回転を止めます。', {'item':'minecraft:blue_dye'})])

ZH_TRANSLATIONS = {

}

add('open_tank', 'tank', 'tank_casing', 'Building an open cylinder tank', '開放円筒形タンクを組み立てる', [
  ([5,1,6], 'Start with a completely filled pixel-circle floor made from tank casing blocks. The supported diameter is 4 to 32 blocks.', 'タンク構成ブロックでピクセル円の床を隙間なく埋めます。対応する直径は4〜32ブロックです。', {'reveal':[3,1,4,7,1,8]}),
  ([6,2,5], 'Raise the casing wall at least 2 blocks high. Keep the interior hollow and the top open.', 'タンク構成ブロックで側壁を2段以上積みます。内部は空洞、上面は開放します。', {'reveal':[3,2,4,6,3,7]}),
([3,3,5], 'Placing the last casing completes the tank. It forms automatically with a chime and sparkles along the rim; no controller is needed.', '最後のタンク構成ブロックを置くと自動で成立し、音とともに縁が光ります。コントローラーは不要です。'),
([2,3,5], 'Connect Create pipes and pumps to the floor or walls to fill and drain the tank.', '床や壁へCreateのパイプとポンプをつなぎ、流体を出し入れします。', {'reveal':[0,3,5,2,3,5]}),
([3,3,5], 'Empty-hand right-click shows capacity and contents. Repair any broken wall sections.', '空手で右クリックすると容量と中身を確認できます。壊れた壁は補修します。')])
TUTORIALS[-1].update(size=13, scale=0.5, height=5)
for diameter in range(4, 33):
    add(f'open_tank_size_{diameter}', f'tank_pattern_{diameter}', 'tank_casing',
        f'Tank footprint pattern: diameter {diameter}', f'タンク底面パターン：直径{diameter}', [
            ([1 + diameter // 2, 1, 1 + diameter // 2],
             f'Build the floor in this pattern for diameter {diameter}.',
             f'直径{diameter}マスの床を、この形に敷きます。'),
            ([1 + diameter // 2, 1, 1 + diameter // 2],
             'Leave the outside of the circle empty.',
             '円の外側には置かず、図の形にそろえます。'),
            ([diameter, 3, 1 + (diameter - 1) // 2],
             'Raise the wall at least two layers to form the tank automatically.',
             '壁を2段以上にすると、自動でタンクになります。')])
    TUTORIALS[-1].update(size=diameter + 2, scale=0.5 if diameter <= 12 else 0.25, height=5)
    centre=1+diameter//2
    for entry,region in zip(TUTORIALS[-1]['steps'],([1,1,1,centre,1,diameter],[centre+1,1,1,diameter,1,diameter],[1,2,1,diameter,3,diameter])):
        entry['reveal']=region
for tutorial in TUTORIALS:
    if tutorial["layout"].startswith("module_"): tutorial["size"] = 15 if tutorial["layout"] in ("module_cooling", "module_compression", "module_compression_large") else 11

def layout(name):
    if name.startswith('tank_pattern_'):
        diameter = int(name.rsplit('_', 1)[1])
        s = Structure(diameter + 2, 5)
        for x in range(diameter):
            for z in range(diameter):
                dx = 2 * x - (diameter - 1)
                dz = 2 * z - (diameter - 1)
                if dx * dx + dz * dz > diameter * diameter:
                    continue
                s.block(x + 1, 1, z + 1, NS+'tank_casing')
                if not any((2 * (x + ox) - (diameter - 1)) ** 2
                           + (2 * (z + oz) - (diameter - 1)) ** 2 > diameter * diameter
                           for ox, oz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    continue
                s.block(x + 1, 2, z + 1, NS+'tank_casing')
                s.block(x + 1, 3, z + 1, NS+'tank_casing')
        return s
    if name == 'tank':
        s=Structure(13,5)
        diameter=4
        origin_x,origin_z=3,4
        for x in range(diameter):
            for z in range(diameter):
                dx=2*x-(diameter-1); dz=2*z-(diameter-1)
                if dx*dx+dz*dz>diameter*diameter: continue
                s.block(origin_x+x,1,origin_z+z,NS+'tank_casing')
                wall=any((2*(x+ox)-(diameter-1))**2+(2*(z+oz)-(diameter-1))**2>diameter*diameter
                    for ox,oz in ((1,0),(-1,0),(0,1),(0,-1)))
                if wall:
                    for y in (2,3):
                        s.block(origin_x+x,y,origin_z+z,NS+'tank_casing')
        inlet_x,inlet_z=origin_x,origin_z+(diameter-1)//2
        s.block(inlet_x-1,3,inlet_z,'create:fluid_pipe',north='false',south='false',east='true',west='true',up='false',down='false',waterlogged='false')
        s.pump(inlet_x-2,3,inlet_z,'east')
        s.block(inlet_x-3,3,inlet_z,'create:fluid_tank')
        return s
    s = Structure(7, 7)
    # Save these tutorials in their own directory instead of the refinery folder.
    if name == 'flywheel':
        for x in (2,3,4): s.block(x,1,3,NS+'large_flywheel',axis='x')
        for x in (0,1,5,6): s.block(x,1,3,'create:shaft',axis='x')
    else: raise ValueError(name)
    return s


def generate(only=None):
    """Writes the given tutorials, or all of this script's, into the shared catalogue without touching other scripts' scenes."""
    import generate_petrochemical_ponder as serializer
    serializer.OUT = ASSETS / 'ponder/plant'
    chosen = [t for t in TUTORIALS if not only or t['id'] in only]
    if only and len(chosen) != len(set(only)): raise ValueError('Unknown tutorial in ' + ' '.join(only))
    for tutorial in chosen:
        layout(tutorial['layout']).save(tutorial['id'])
    path = ASSETS/'ponder/tutorials.json'
    raw = path.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
    catalogue = json.loads(raw); known = {t['id']: i for i, t in enumerate(catalogue)}
    for tutorial in chosen:
        entry = {k: v for k, v in tutorial.items() if k != 'layout'}
        if tutorial['id'] in known: catalogue[known[tutorial['id']]] = entry
        else: catalogue.append(entry)
    path.write_bytes((json.dumps(catalogue, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))
    ids = {PREFIX + t['id'] + '.' for t in chosen}
    for language, additions in TRANSLATIONS.items():
        path = ASSETS/'lang'/(language+'.json')
        strings = json.loads(path.read_text()); strings.update({k: v for k, v in additions.items() if any(k.startswith(i) for i in ids)})
        path.write_text(json.dumps(strings,ensure_ascii=False,indent=2)+'\n')
    zh_path = ASSETS/'lang'/'zh_cn.json'; zh = json.loads(zh_path.read_text())
    for tutorial in chosen:
        captions = ZH_TRANSLATIONS.get(tutorial['id'])
        if not captions: continue
        base = 'create_refinery_n_reactors.ponder.shared.guide.' + tutorial['id']
        zh[base + '.title'] = captions[0]
        zh['create_refinery_n_reactors.ponder.plant_' + tutorial['id'] + '.header'] = captions[0]
        for index, caption in enumerate(captions[1:], 1): zh[base + '.step_' + str(index)] = caption
    zh_path.write_text(json.dumps(zh,ensure_ascii=False,indent=2)+'\n')
    from generate_ponder_language import generate as language_keys
    language_keys()
    print(f'Generated {len(chosen)} tutorials')


if __name__ == '__main__':
    import sys
    generate(sys.argv[1:] or None)
