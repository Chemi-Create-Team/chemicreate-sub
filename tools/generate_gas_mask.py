#!/usr/bin/env python3
"""Toxic gas and the gas mask: the mask's icon and worn texture, the effect icon, the damage type, recipes and translations.
Run from the repository root.

The mask is a dark rubber face piece with two round glass eyepieces in rubber rims and an olive filter canister under the chin, held on
by two straps round the head."""
import json, sys
from PIL import Image
sys.path.insert(0, 'tools')
from minecraft_style import grain
from generate_reactor_assets import A, D, NS, write

RUBBER, RUBBER_DARK, RIM = (58, 62, 66), (36, 40, 44), (28, 30, 34)
GLASS, GLASS_LIGHT = (128, 158, 170), (190, 214, 220)
CANISTER, CANISTER_DARK, CAP = (98, 106, 72), (70, 76, 50), (150, 154, 150)
STRAP = (44, 46, 40)


def shade(color, value=0):
    return tuple(max(0, min(255, c + value)) for c in color) + (255,)


def icon():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); p = im.load()
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 5.2, (y - 7) / 6.2
            if dx * dx + dy * dy <= 1: p[x, y] = shade(RUBBER, grain('gas_mask', x, y, 2) + (10 if x < 7 and y < 6 else -8 if x > 10 else 0))
            elif dx * dx + dy * dy <= 1.25: p[x, y] = shade(RUBBER_DARK)
    for cx in (5, 10):
        for y in range(4, 9):
            for x in range(cx - 2, cx + 3):
                d = (x - cx) ** 2 + (y - 6) ** 2
                if d <= 2: p[x, y] = shade(GLASS_LIGHT if (x - cx, y - 6) == (-1, -1) else GLASS)
                elif d <= 5: p[x, y] = shade(RIM)
    for y in range(10, 15):
        for x in range(6, 10):
            p[x, y] = shade(CAP if y == 10 else CANISTER_DARK if x == 9 or y % 2 == 0 else CANISTER, grain('gas_mask_canister', x, y, 1))
    for y in (6, 7): p[0, y] = shade(STRAP); p[15, y] = shade(STRAP)
    im.save(A / 'textures/item/gas_mask.png')


def armour():
    """The humanoid head on a 64x32 armour layer: sides (0,8) and (16,8), front (8,8), back (24,8), each 8x8."""
    im = Image.new('RGBA', (64, 32), (0, 0, 0, 0)); p = im.load()
    for y in range(9, 16):
        for x in range(8, 16): p[x, y] = shade(RUBBER, grain('gas_mask_face', x, y, 2))
    for cx in (9, 13):
        for x in (cx, cx + 1): p[x, 11] = shade(GLASS_LIGHT if x == cx else GLASS); p[x, 12] = shade(GLASS)
        for x in (cx - 1, cx + 2):
            if 8 <= x < 16: p[x, 11] = shade(RIM); p[x, 12] = shade(RIM)
        for x in (cx, cx + 1): p[x, 10] = shade(RIM); p[x, 13] = shade(RIM)
    for y in (14, 15):
        for x in (11, 12): p[x, y] = shade(CAP if y == 14 else CANISTER)
    for x0 in (0, 16, 24):
        for x in range(x0, x0 + 8):
            for y in (10, 13): p[x, y] = shade(STRAP, grain('gas_mask_strap', x, y, 1))
    for y in range(9, 16): p[7, y] = shade(RUBBER_DARK); p[16, y] = shade(RUBBER_DARK)
    path = A / 'textures/models/armor'; path.mkdir(parents=True, exist_ok=True)
    im.save(path / 'gas_mask_layer_1.png')


def effect_icon():
    im = Image.new('RGBA', (18, 18), (0, 0, 0, 0)); p = im.load()
    for cx, cy, r, tone in ((6, 10, 4.2, (168, 184, 96)), (11, 8, 4.6, (190, 204, 112)), (12, 13, 3.4, (150, 166, 84))):
        for y in range(18):
            for x in range(18):
                d = ((x - cx) ** 2 + (y - cy) ** 2) ** .5
                if d <= r: p[x, y] = shade(tone, round(14 - d * 5) + grain('toxic_gas', x, y, 2))
    path = A / 'textures/mob_effect'; path.mkdir(parents=True, exist_ok=True)
    im.save(path / 'toxic_gas.png')


def data():
    write(A / 'models/item/gas_mask.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/gas_mask'}})
    write(D / 'damage_type/toxic_gas.json', {'message_id': 'create_refinery_n_reactors.toxic_gas', 'scaling': 'never', 'exhaustion': 0.0})
    path = D.parent / 'minecraft/tags/damage_type/bypasses_armor.json'
    tag = json.loads(path.read_text()) if path.exists() else {'replace': False, 'values': []}
    if NS + 'toxic_gas' not in tag['values']: tag['values'].append(NS + 'toxic_gas')
    write(path, tag)
    # Rubber face piece, glass eyepieces, and an iron canister of charcoal.
    write(D / 'recipe/gas_mask.json', {'type': 'minecraft:crafting_shaped', 'category': 'equipment', 'pattern': [' R ', 'GRG', 'ICI'],
                                       'key': {'R': {'item': NS + 'synthetic_rubber'}, 'G': {'item': 'minecraft:glass_pane'}, 'I': {'tag': 'c:plates/iron'}, 'C': {'item': 'minecraft:charcoal'}},
                                       'result': {'id': NS + 'gas_mask', 'count': 1}})
    write(D / 'recipe/gas_mask_regeneration.json', {'type': 'minecraft:crafting_shapeless', 'category': 'equipment',
                                                    'ingredients': [{'item': NS + 'gas_mask'}, {'item': 'minecraft:charcoal'}, {'item': 'minecraft:charcoal'}],
                                                    'result': {'id': NS + 'gas_mask', 'count': 1}})


TEXTS = {
    'item.create_refinery_n_reactors.gas_mask': ('Gas Mask', 'ガスマスク', '防毒面具'),
    'effect.create_refinery_n_reactors.toxic_gas': ('Toxic Gas', '有毒ガス', '有毒气体'),
    'death.attack.create_refinery_n_reactors.toxic_gas': ('%1$s breathed a toxic gas', '%1$sは有毒ガスを吸い込んだ', '%1$s吸入了有毒气体'),
    'death.attack.create_refinery_n_reactors.toxic_gas.player': ('%1$s breathed a toxic gas while fleeing %2$s', '%1$sは%2$sから逃れようとして有毒ガスを吸い込んだ', '%1$s在逃离%2$s时吸入了有毒气体'),
}


def translations():
    for language, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
        path = A / 'lang' / f'{language}.json'; raw = path.read_bytes().decode('utf8'); nl = '\r\n' if '\r\n' in raw else '\n'; strings = json.loads(raw)
        for key, values in TEXTS.items(): strings[key] = values[index]
        path.write_bytes((json.dumps(strings, ensure_ascii=False, indent=2).replace('\n', nl) + nl).encode('utf8'))


if __name__ == '__main__':
    icon(); armour(); effect_icon(); data(); translations()
    print('Generated the gas mask and the toxic gas')
