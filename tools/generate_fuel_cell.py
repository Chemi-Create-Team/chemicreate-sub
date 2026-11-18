#!/usr/bin/env python3
"""The alkaline fuel cell: textures, model, blockstate, loot, recipe and translations. Run from the repository root.

The stack of nickel electrode plates and black gaskets stands between two stainless end plates held by four tie rods; the positive
(dark red insulator) and negative (black insulator) copper terminal posts stand on the top plate, and a brass nameplate is on the front."""
import json, sys
from PIL import Image
sys.path.insert(0, 'tools')
from minecraft_style import grain
from generate_reactor_assets import A, D, NS, write

NICKEL, NICKEL_LIGHT, GASKET = (150, 148, 136), (178, 176, 162), (46, 50, 54)
STEEL, STEEL_LIGHT, STEEL_DARK = (168, 175, 184), (214, 220, 226), (106, 112, 120)
BRASS, BRASS_DARK, COPPER = (186, 150, 70), (122, 94, 40), (190, 118, 78)
RED, BLACK = (120, 52, 44), (44, 46, 50)
TEX = A / 'textures/block/fuel_cell'


def shade(color, value):
    return tuple(max(0, min(255, c + value)) for c in color) + (255,)


def image(pixel):
    im = Image.new('RGBA', (16, 16)); p = im.load()
    for y in range(16):
        for x in range(16): p[x, y] = pixel(x, y)
    return im


def stack(x, y):
    # Three-pixel cells: a lit plate edge, the plate, then the gasket between cells; the ends of each plate are darker.
    row = y % 3
    color = GASKET if row == 2 else NICKEL_LIGHT if row == 0 else NICKEL
    edge = -18 if x in (0, 15) else 0
    return shade(color, grain('fuel_cell_stack', x, y, 2) + edge)


def front(x, y):
    if 5 <= x <= 10 and 6 <= y <= 9:
        border = x in (5, 10) or y in (6, 9)
        return shade(BRASS_DARK if border else BRASS, grain('fuel_cell_plate', x, y, 1) + (8 if y == 7 and not border else 0))
    return stack(x, y)


def plate(x, y):
    edge = x in (0, 15) or y in (0, 15)
    bolt = (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13))
    color = STEEL_DARK if edge else STEEL_LIGHT if bolt else STEEL
    return shade(color, grain('fuel_cell_end', x, y, 2) + (10 if y == 1 and not edge else 0))


def rod(x, y):
    return shade(STEEL_DARK if x % 4 == 3 else STEEL, grain('fuel_cell_rod', x, y, 1))


def terminal(insulator):
    def pixel(x, y):
        if y < 4: return shade(COPPER, grain('fuel_cell_post', x, y, 2) + (16 if y == 0 else 0))
        return shade(insulator, grain('fuel_cell_insulator', x, y, 1) + (-14 if y % 4 == 3 else 0))
    return pixel


def textures():
    TEX.mkdir(parents=True, exist_ok=True)
    for name, pixel in (('stack', stack), ('front', front), ('plate', plate), ('rod', rod), ('positive', terminal(RED)), ('negative', terminal(BLACK))):
        image(pixel).save(TEX / f'{name}.png')


def box(a, b, faces):
    return {'from': a, 'to': b, 'faces': faces}


def faces(texture, uv=None, sides='north east south west up down', front=None):
    result = {}
    for side in sides.split():
        tex = front if front and side == 'north' else texture
        result[side] = {'texture': tex, **({'uv': uv[side]} if uv and side in uv else {})}
    return result


def model():
    side = {s: [1, 4, 15, 14] for s in ('north', 'east', 'south', 'west')}
    end_side = {s: [0, 0, 16, 2] for s in ('north', 'east', 'south', 'west')}
    elements = [
        box([0, 0, 0], [16, 2, 16], faces('#plate', end_side)),
        box([1, 2, 1], [15, 12, 15], faces('#stack', side, 'north east south west', '#front')),
        box([0, 12, 0], [16, 14, 16], faces('#plate', end_side)),
    ]
    for x, z in ((0, 0), (15, 0), (0, 15), (15, 15)):
        elements.append(box([x, 2, z], [x + 1, 12, z + 1], faces('#rod', {s: [x, 2, x + 1, 12] for s in ('north', 'east', 'south', 'west')}, 'north east south west')))
    for x0, tex in ((3, '#positive'), (11, '#negative')):
        elements.append(box([x0, 14, 7], [x0 + 2, 17, 9], faces(tex, {**{s: [x0, 3, x0 + 2, 6] for s in ('north', 'east', 'south', 'west')}, 'up': [x0, 0, x0 + 2, 2]}, 'north east south west up')))
    textures_ = {name: NS + f'block/fuel_cell/{name}' for name in ('stack', 'front', 'plate', 'rod', 'positive', 'negative')}
    write(A / 'models/block/fuel_cell.json', {'parent': 'minecraft:block/block', 'textures': {'particle': textures_['plate'], **textures_}, 'elements': elements})
    write(A / 'blockstates/fuel_cell.json', {'variants': {f'facing={f}': {'model': NS + 'block/fuel_cell', **({'y': y} if y else {})}
                                                          for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}})
    write(A / 'models/item/fuel_cell.json', {'parent': NS + 'block/fuel_cell'})


def data():
    write(D / 'loot_table/blocks/fuel_cell.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + 'fuel_cell',
                                                                                                                 'functions': [{'function': 'minecraft:copy_components', 'source': 'block_entity', 'include': ['minecraft:block_entity_data']}]}],
                                                                                      'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    # Nickel electrodes and a resin electrolyte membrane in a stainless frame, with copper collectors and a gas/water pipe.
    write(D / 'recipe/fuel_cell.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['SCS', 'NPN', 'SRS'],
                                        'key': {'S': {'tag': 'c:plates/stainless_steel'}, 'C': {'tag': 'c:plates/copper'}, 'N': {'tag': 'c:plates/nickel'},
                                                'P': {'item': 'create:fluid_pipe'}, 'R': {'item': NS + 'industrial_resin'}},
                                        'result': {'id': NS + 'fuel_cell', 'count': 1}})
    path = D.parent / 'minecraft/tags/block/mineable/pickaxe.json'
    tag = json.loads(path.read_text())
    if NS + 'fuel_cell' not in tag['values']: tag['values'].append(NS + 'fuel_cell'); write(path, tag)


TEXTS = {
    'block.create_refinery_n_reactors.fuel_cell': ('Fuel Cell', '燃料電池', '燃料电池'),
    'fuel_cell.create_refinery_n_reactors.terminal.positive': ('Positive terminal', '正極（＋）', '正极（+）'),
    'fuel_cell.create_refinery_n_reactors.terminal.negative': ('Negative terminal', '負極（－）', '负极（-）'),
    'fuel_cell.create_refinery_n_reactors.status.unwired': ('Not in a circuit: wire both terminals', '回路につながっていません：2つの端子に電線をつないでください', '未接入电路：请给两个端子接线'),
    'fuel_cell.create_refinery_n_reactors.status.running': ('Generating', '発電中', '发电中'),
    'fuel_cell.create_refinery_n_reactors.status.idle': ('Ready: no load on the terminals', '待機：端子に負荷がつながっていません', '待机：端子未接负载'),
    'fuel_cell.create_refinery_n_reactors.status.fuel': ('No hydrogen', '水素がありません', '没有氢气'),
    'fuel_cell.create_refinery_n_reactors.status.oxygen': ('No oxygen', '酸素がありません', '没有氧气'),
    'fuel_cell.create_refinery_n_reactors.status.water': ('Water outlet full', '水の出口が満杯です', '出水口已满'),
    'fuel_cell.create_refinery_n_reactors.status.backfed': ('Disconnected: the circuit is above 400 V', '切り離し中：回路の電圧が400 Vを超えています', '已断开：电路电压高于400 V'),
    'fuel_cell.create_refinery_n_reactors.power': ('%s kW (%s V, %s A)', '%s kW（%s V・%s A）', '%s kW（%s V，%s A）'),
    'fuel_cell.create_refinery_n_reactors.tanks': ('Hydrogen %s mB, oxygen %s mB, water %s mB', '水素 %s mB・酸素 %s mB・水 %s mB', '氢气 %s mB，氧气 %s mB，水 %s mB'),
}


def translations():
    for language, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
        path = A / 'lang' / f'{language}.json'; raw = path.read_bytes().decode('utf8'); nl = '\r\n' if '\r\n' in raw else '\n'; strings = json.loads(raw)
        for key, values in TEXTS.items(): strings[key] = values[index]
        path.write_bytes((json.dumps(strings, ensure_ascii=False, indent=2).replace('\n', nl) + nl).encode('utf8'))


if __name__ == '__main__':
    textures(); model(); data(); translations()
    print('Generated the fuel cell')
