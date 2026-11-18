#!/usr/bin/env python3
"""ANFO and the blasting charge: textures, models, blockstate, loot, recipes and translations. Run from the repository root.

The charge is four kraft-paper cartridges with a printed red band, strapped together with a steel band under a wooden cap, and a copper
detonator on top whose tip glows while the fuse burns. ANFO is the white, slightly oily prill of ammonium nitrate and fuel oil."""
import json, sys
from PIL import Image
sys.path.insert(0, 'tools')
from minecraft_style import grain
from powder_assets import write_powder_item
from generate_reactor_assets import A, D, NS, write
from generate_uranium_assets import icon, PILE, pile, granules

PAPER, PAPER_DARK, BAND_RED = (184, 150, 104), (140, 110, 72), (128, 50, 40)
STRAP, STRAP_DARK, WOOD, WOOD_DARK = (120, 126, 132), (78, 82, 88), (150, 112, 68), (112, 80, 46)
COPPER, LEAD, GLOW = (190, 118, 78), (54, 56, 60), (250, 178, 74)
TEX = A / 'textures/block/blasting_charge'


def shade(color, value):
    return tuple(max(0, min(255, c + value)) for c in color) + (255,)


def image(pixel):
    im = Image.new('RGBA', (16, 16)); p = im.load()
    for y in range(16):
        for x in range(16): p[x, y] = pixel(x, y)
    return im


def cartridge(x, y):
    # A cylinder seen from the side: lit left of centre, dark at the edges; a red printed band below the top, and the paper's seam.
    column = (x % 5)
    light = (8, 14, 6, -4, -16)[column]
    color = BAND_RED if 3 <= y <= 5 else PAPER_DARK if column == 4 and y % 3 == 0 else PAPER
    return shade(color, grain('anfo_cartridge', x, y, 2) + light)


def cartridge_top(x, y):
    cx, cy = x % 5 - 2, y % 5 - 2
    ring = max(abs(cx), abs(cy))
    return shade(PAPER_DARK if ring == 2 else PAPER if ring == 1 else (122, 94, 60), grain('anfo_crimp', x, y, 2))


def strap(x, y):
    return shade(STRAP_DARK if y in (0, 1) and x % 6 == 2 else STRAP, grain('anfo_strap', x, y, 1) + (10 if y == 0 else -8 if y == 1 else 0))


def cap(x, y):
    return shade(WOOD_DARK if y % 4 == 0 or x in (0, 15) else WOOD, grain('anfo_cap', x, y, 3))


def detonator(tip):
    def pixel(x, y):
        if y < 2: return shade(tip, grain('anfo_tip', x, y, 1))
        if x in (0, 15): return shade(LEAD, 0)
        return shade(COPPER, grain('anfo_detonator', x, y, 2) + (12 if x < 6 else 0))
    return pixel


def textures():
    TEX.mkdir(parents=True, exist_ok=True)
    for name, pixel in (('cartridge', cartridge), ('cartridge_top', cartridge_top), ('strap', strap), ('cap', cap),
                        ('detonator', detonator((96, 66, 50))), ('detonator_lit', detonator(GLOW))):
        image(pixel).save(TEX / f'{name}.png')
    write_powder_item('anfo')
    write(A / 'models/item/anfo.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/anfo'}})


def box(a, b, faces):
    return {'from': a, 'to': b, 'faces': faces}


def side_faces(texture, uv, up=None, down=None):
    faces = {s: {'texture': texture, 'uv': uv} for s in ('north', 'east', 'south', 'west')}
    if up: faces['up'] = {'texture': up[0], 'uv': up[1]}
    if down: faces['down'] = {'texture': down[0], 'uv': down[1], 'cullface': 'down'}
    return faces


def models():
    elements = []
    for x, z in ((2, 2), (9, 2), (2, 9), (9, 9)):
        elements.append(box([x, 0, z], [x + 5, 12, z + 5], side_faces('#cartridge', [0, 4, 5, 16], up=('#cartridge_top', [0, 0, 5, 5]), down=('#cartridge_top', [0, 0, 5, 5]))))
    elements.append(box([1.9, 7, 1.9], [14.1, 8.5, 14.1], side_faces('#strap', [0, 0, 16, 1.5])))
    elements.append(box([4, 12, 4], [12, 13, 12], side_faces('#cap', [4, 0, 12, 1], up=('#cap', [4, 4, 12, 12]))))
    elements.append(box([7, 13, 7], [9, 15, 9], side_faces('#detonator', [6, 0, 8, 2], up=('#detonator', [6, 0, 8, 2]))))
    for lit in (False, True):
        name = 'blasting_charge' + ('_lit' if lit else '')
        tex = {n: NS + f'block/blasting_charge/{n}' for n in ('cartridge', 'cartridge_top', 'strap', 'cap')}
        tex['detonator'] = NS + 'block/blasting_charge/' + ('detonator_lit' if lit else 'detonator')
        write(A / f'models/block/{name}.json', {'parent': 'minecraft:block/block', 'textures': {'particle': tex['cartridge'], **tex}, 'elements': elements})
    write(A / 'blockstates/blasting_charge.json', {'variants': {'lit=false': {'model': NS + 'block/blasting_charge'}, 'lit=true': {'model': NS + 'block/blasting_charge_lit'}}})
    write(A / 'models/item/blasting_charge.json', {'parent': NS + 'block/blasting_charge'})


def data():
    write(D / 'loot_table/blocks/blasting_charge.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + 'blasting_charge'}],
                                                                                            'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    # ANFO: 94 parts ammonium nitrate to 6 of fuel oil.
    write(D / 'recipe/mixing/anfo.json', {'type': 'create:mixing', 'ingredients': [{'tag': 'c:fertilizers/ammonium_nitrate'}] * 3 + [{'type': 'neoforge:tag', 'amount': 25, 'tag': 'c:diesel'}],
                                          'results': [{'id': NS + 'anfo', 'count': 3}]})
    write(D / 'recipe/blasting_charge.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['PAP', 'ARA', 'PAP'],
                                              'key': {'P': {'item': 'minecraft:paper'}, 'A': {'item': NS + 'anfo'}, 'R': {'tag': 'c:dusts/redstone'}},
                                              'result': {'id': NS + 'blasting_charge', 'count': 1}})


TEXTS = {
    'item.create_refinery_n_reactors.anfo': ('ANFO', 'ANFO爆薬', '铵油炸药'),
    'block.create_refinery_n_reactors.blasting_charge': ('Blasting Charge', '発破薬', '爆破药包'),
}


def translations():
    for language, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
        path = A / 'lang' / f'{language}.json'; raw = path.read_bytes().decode('utf8'); nl = '\r\n' if '\r\n' in raw else '\n'; strings = json.loads(raw)
        for key, values in TEXTS.items(): strings[key] = values[index]
        path.write_bytes((json.dumps(strings, ensure_ascii=False, indent=2).replace('\n', nl) + nl).encode('utf8'))


if __name__ == '__main__':
    textures(); models(); data(); translations()
    print('Generated ANFO and the blasting charge')
