#!/usr/bin/env python3
"""Generate the formed 9x9x11 converter Ponder and its complete three-language lesson."""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import generate_petrochemical_ponder as serializer
from generate_petrochemical_ponder import Structure

A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
NS = 'create_refinery_n_reactors:'
ID = 'converter'

TEXTS = {
    'title': ('Making steel in a converter', '転炉で鋼を作る', '用转炉炼钢'),
    'step_1': (
        'Build the 9 by 9, eleven-block-high Create girder frame. Fit the powered trunnion bearing on the left and the passive bearing on the opposite side.',
        '底面9×9・高さ11ブロックのCreate鉄骨架台を組み、左に動力付きトラニオン軸受、反対側に従動軸受を取り付けます。',
        '搭建占地9×9、高11格的Create金属梁框架，在左侧安装动力耳轴轴承，在另一侧安装从动轴承。'),
    'step_2': (
        'Build the stepped vessel from steel blocks. Line its four inner courses and mouth with zirconia refractory bricks; the centre remains hollow.',
        '段付きの炉体外殻を鋼鉄ブロックで組み、内側4段と炉口をジルコニア耐火レンガで覆います。中央は空洞のままです。',
        '用钢块搭建阶梯状炉壳，再用氧化锆耐火砖铺设四层内衬和炉口，中央保持中空。'),
    'step_3': (
        'Use Super Glue to connect every vessel block, including both steel trunnions. Missing, incorrect, unglued or extra attached blocks prevent assembly.',
        '両側の鋼製トラニオンを含む炉体すべてを超粘着剤で接着します。不足・素材違い・未接着・余計な接着ブロックがあると組み立てられません。',
        '用强力胶连接炉体的全部方块，包括两侧钢制耳轴。缺块、材料错误、未粘合或粘连多余方块都会阻止组装。'),
    'step_4': (
        'While upright, feed 48 iron ingots and 24 raw iron through the chute and 64,000 mB of oxygen through the lance. An 800-tick blow makes 64 steel ingots and 128,000 mB of converter gas.',
        '直立中にシュートから鉄インゴット48個と鉄の原石24個、ランスから酸素64,000 mBを入れます。800 tickの吹錬で鋼鉄インゴット64個と転炉ガス128,000 mBを作ります。',
        '炉体直立时，从溜槽加入48个铁锭和24个粗铁，并从氧枪输入64,000 mB氧气。吹炼800 tick后得到64个钢锭和128,000 mB转炉煤气。'),
    'step_5': (
        'Turn the shaft forward to assemble and tilt the glued vessel to 60 degrees. Only then can steel leave the tapping port. Reverse the shaft to return to zero and disassemble.',
        'シャフトを正転すると接着した炉体を組み立てて60度まで傾けます。この間だけ出鋼口から製品を取り出せます。逆転すると0度へ戻って分解します。',
        '正转传动轴会组装已粘合的炉体并倾动至60度，此时才能从出钢口取出产品。反转后炉体回到0度并拆解。'),
    'step_6': (
        'Route excess converter gas from the rear to an ignited flare tip.',
        '背面の転炉ガスを着火したフレアスタック先端部へ送ります。',
        '将后侧多余转炉煤气送往已点火的火炬头。'),
    'step_7': (
        'For AOD refining, return upright and replace oxygen with 1,600 mB of argon. Iron, chromium and nickel become 192 stainless steel ingots in 800 ticks.',
        'AOD精錬では直立へ戻し、酸素の代わりにアルゴン1,600 mBを送ります。鉄・クロム・ニッケルから800 tickでステンレス鋼インゴット192個を作ります。',
        '进行AOD精炼时先恢复直立，并改用1,600 mB氩气。铁、铬和镍会在800 tick内炼成192个不锈钢锭。'),
}


def put(cells, local_x, local_y, local_z, block, **properties):
    cells[(local_x, local_y, local_z)] = (block, properties)


def girder(axis, x=False, z=False, bottom=False):
    return {'axis': axis, 'x': str(x).lower(), 'z': str(z).lower(), 'top': 'false',
            'bottom': str(bottom).lower(), 'waterlogged': 'false'}


def converter_cells():
    cells = {}
    for x in (-4, 4):
        for z in (-4, 4): put(cells, x, 0, z, 'minecraft:polished_blackstone_bricks')
    for y in range(1, 10):
        for x in (-4, 4):
            for z in (-4, 4): put(cells, x, y, z, 'create:metal_girder', **girder('y'))
    for x in range(-4, 5):
        for z in (-4, 4):
            corner = x in (-4, 4)
            put(cells, x, 10, z, 'create:metal_girder', **girder('x', True, corner, corner))
    for z in range(-3, 4):
        put(cells, -4, 10, z, 'create:metal_girder', **girder('z', z == 0, True))
        put(cells, 4, 10, z, 'create:metal_girder', **girder('z', z == 0, True))
    for x in range(-3, 4):
        if x != 0: put(cells, x, 10, 0, 'create:metal_girder', **girder('x', True, False))
    for z in range(-3, 4):
        if z != 0: put(cells, 0, 10, z, 'create:metal_girder', **girder('z', False, True))
    for x in (-4, 4):
        for z in range(-3, 4):
            if z != 0: put(cells, x, 5, z, 'create:metal_girder', **girder('z', False, True))
    put(cells, -3, 5, 0, NS + 'steel_block')
    put(cells, 3, 5, 0, NS + 'steel_block')
    for x in range(-1, 2):
        for z in range(-1, 2): put(cells, x, 2, z, NS + 'steel_block')
    for y in range(3, 7):
        for x in range(-2, 3):
            for z in range(-2, 3):
                if abs(x) == 2 or abs(z) == 2:
                    put(cells, x, y, z, NS + 'steel_block')
        for x in range(-1, 2):
            for z in range(-1, 2):
                if abs(x) == 1 or abs(z) == 1:
                    put(cells, x, y, z, NS + 'zirconia_refractory_bricks')
    put(cells, -4, 5, 0, NS + 'converter_trunnion_bearing', facing='east')
    put(cells, 4, 5, 0, NS + 'converter_shell', facing='north', part='bearing')
    put(cells, 0, 7, 0, NS + 'converter_shell', facing='north', part='lance')
    put(cells, 2, 7, 1, NS + 'converter_shell', facing='north', part='raw_port')
    pipe = {side: 'false' for side in serializer.DIRECTIONS}
    pipe.update(up='true', down='true', waterlogged='false')
    for y in range(8, 11):
        put(cells, 0, y, 0, 'create:fluid_pipe', **pipe)
        put(cells, 2, y, 1, 'create:chute', facing='down', shape='normal', waterlogged='false')
    put(cells, 0, 9, -4, NS + 'converter_shell', facing='north', part='gas_port')
    put(cells, 0, 3, 3, NS + 'converter_shell', facing='north', part='tap')
    return cells


def structure():
    s = Structure(16, 13)
    ox, oy, oz = 5, 1, 6
    for (x, y, z), (block, properties) in converter_cells().items():
        # Facing north maps positive local Z towards decreasing world Z.
        s.block(ox + x, oy + y, oz - z, block, **properties)
    # Route the rear offgas port upward to an ignited flare tip.
    for x in range(5, 14):
        props = {side: 'false' for side in serializer.DIRECTIONS}
        props['west' if x > 5 else 'north'] = 'true'
        props['east' if x < 13 else 'up'] = 'true'
        props['waterlogged'] = 'false'
        s.block(x, 10, 11, NS + 'stainless_steel_pipe', **props)
    s.block(13, 11, 11, NS + 'flare_tip', pilot='true', waterlogged='false')
    return s


def preserve(path, data):
    raw = path.read_bytes().decode('utf-8') if path.exists() else ''
    newline = '\r\n' if '\r\n' in raw else '\n'
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))


def generate():
    serializer.OUT = A / 'ponder/plant'
    structure().save(ID)
    catalog_path = A / 'ponder/tutorials.json'
    catalog = json.loads(catalog_path.read_text(encoding='utf-8'))
    entry = {
        'id': ID,
        'size': 16,
        'height': 13,
        'scale': .34,
        'formed': 'step_1',
        'components': ['converter_trunnion_bearing', 'zirconia_refractory_bricks', 'converter_shell', 'steel_block'],
        'steps': [
            {'target': [1, 6, 6], 'key': 'step_1', 'reveal': [1, 1, 2, 9, 11, 10]},
            {'target': [6, 5, 6], 'key': 'step_2', 'item': NS + 'zirconia_refractory_bricks'},
            {'target': [7, 5, 6], 'key': 'step_3', 'item': 'create:super_glue'},
            {'target': [7, 8, 5], 'key': 'step_4', 'item': NS + 'oxygen_gas_bucket'},
            {'target': [1, 6, 6], 'key': 'step_5', 'speed': 32, 'item': NS + 'steel_ingot'},
            {'target': [5, 10, 10], 'key': 'step_6', 'reveal': [5, 1, 10, 13, 10, 11], 'item': NS + 'converter_gas_bucket'},
            {'target': [5, 11, 6], 'key': 'step_7', 'item': NS + 'argon_gas_bucket'},
        ],
    }
    for index, tutorial in enumerate(catalog):
        if tutorial['id'] == ID:
            catalog[index] = entry
            break
    else:
        catalog.append(entry)
    preserve(catalog_path, catalog)

    prefix = f'create_refinery_n_reactors.ponder.shared.guide.{ID}.'
    for language, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
        path = A / 'lang' / f'{language}.json'
        strings = json.loads(path.read_text(encoding='utf-8'))
        for key in [key for key in strings if key.startswith(prefix)]:
            del strings[key]
        for key, translations in TEXTS.items():
            strings[prefix + key] = translations[index]
        strings[f'create_refinery_n_reactors.ponder.plant_{ID}.header'] = TEXTS['title'][index]
        preserve(path, strings)
    print('Generated the oxygen converter Ponder')


if __name__ == '__main__':
    generate()
