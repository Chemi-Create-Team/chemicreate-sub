#!/usr/bin/env python3
"""Pressure vessels, stainless and cryogenic tanks, and the nickel alloy, zirconium, cryogenic and PVC pipes.

Tanks reuse Create's tank geometry through child models; only the 16 px textures and
their RECTANGLE connected-texture sheets are drawn here.
"""
from pathlib import Path
import json, math
from PIL import Image
from generate_uranium_assets import tag

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
D = ROOT / 'src/main/resources/data/create_refinery_n_reactors'
NS = 'create_refinery_n_reactors:'
CREATE_TANK_MODELS = [f'block_{part}{shape}' for part in ('single', 'top', 'middle', 'bottom')
                      for shape in ('', '_window', '_window_ne', '_window_nw', '_window_se', '_window_sw')]
# Alpha masks of Create's window textures; the geometry samples these exact regions.
WINDOW = ['#########......#', '##....###......#'] + ['#......##......#'] * 12 + ['##....###......#', '#########......#']
WINDOW_SINGLE = ['########........', '##....##........'] + ['#......#........'] * 4 + ['##....##........', '########........'] + ['................'] * 8
PALETTES = {
    # Thick carbon-steel 50 bar shell: darker plate, broad bright bands and high-visibility brass fasteners.
    'pressure_tank': dict(plate=(65, 72, 76), seam=(46, 52, 55), frame=(132, 142, 146), hi=(190, 199, 201), shadow=(31, 36, 38), rivet=(196, 151, 61)),
    # Brushed stainless shell for corrosive liquids and gases.
    'stainless_steel_tank': dict(plate=(168, 175, 184), seam=(146, 153, 162), frame=(122, 129, 138), hi=(222, 228, 234), shadow=(92, 98, 106), rivet=(236, 240, 244)),
    # Vacuum-jacketed cryogenic tank: frost-white aluminium outer shell with dark stainless bands.
    'cryogenic_tank': dict(plate=(214, 222, 228), seam=(196, 205, 212), frame=(84, 92, 102), hi=(242, 246, 248), shadow=(62, 68, 76), rivet=(150, 160, 170)),
}
# Material pipes: body colours (light edge, dark edge, face, centre band), the flange texture and the half-width of the body.
PIPES = {
    'nickel_alloy_pipe': ((150, 146, 128), (60, 58, 50), (106, 103, 90), (124, 120, 106), 'block/chemical/cast_iron', 4),
    'zirconium_pipe': ((132, 142, 150), (46, 52, 58), (88, 97, 105), (104, 114, 122), 'block/tank/stainless_steel_tank', 4),
    # The vacuum jacket is wider than the line it carries.
    'cryogenic_pipe': ((236, 242, 246), (150, 160, 170), (206, 214, 220), (222, 228, 234), 'block/tank/cryogenic_tank_top', 5),
    # Dark grey rigid PVC with solvent-welded rubber-lined flanges.
    'pvc_pipe': ((112, 116, 122), (40, 42, 46), (82, 86, 92), (98, 102, 108), 'block/chemical/rubber', 4),
    # Salt-glazed stoneware fired from clay, with terracotta sockets.
    'ceramic_pipe': ((178, 118, 76), (80, 46, 28), (138, 86, 54), (156, 100, 64), 'minecraft:block/terracotta', 4),
}
NAMES = {
    'block.create_refinery_n_reactors.ceramic_pipe': ('Ceramic Pipe', 'セラミック管', '陶瓷管'),
    'item.create_refinery_n_reactors.unfired_ceramic_pipe': ('Unfired Ceramic Pipe', '未焼成のセラミック管', '未烧制陶瓷管'),
}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def grain(x, y, c, amp=2):
    n = ((x * 73856093) ^ (y * 19349663) ^ 0x5bd1e995) % (2 * amp + 1) - amp
    return tuple(max(0, min(255, v + n)) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)


def panel(p, kind, w, h, gx, gy):
    """One pixel of a tank face that spans w x h blocks; frames sit only on the outer edge."""
    W, H = w * 16, h * 16
    edge = min(gx, gy, W - 1 - gx, H - 1 - gy)
    if edge == 0:
        return p['hi'] if gx == 0 or gy == 0 else p['shadow']
    if edge == 1:
        along = gx if gy in (1, H - 2) else gy
        return p['rivet'] if along % 4 == 2 else p['frame']
    if kind == 'side' and 'glass' in p:
        return p['glint'] if (gx + gy) % 23 in (0, 1) and gy % 16 > 3 else p['glass']
    if kind == 'side':
        return p['seam'] if gx % 8 == 7 else p['plate']
    if kind == 'top':
        return p['seam'] if (gx + gy) % 6 == 0 or (gx - gy) % 6 == 0 else p['plate']
    return p['seam']


def tile(p, kind, w, h, c, r):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), grain(c * 16 + x, r * 16 + y, panel(p, kind, w, h, c * 16 + x, r * 16 + y)))
    return im


def connected(p, kind):
    # Create RECTANGLE sheet: column 0 is one block wide, columns 1-3 the left/middle/right of a wider face;
    # row 3 is one block high, rows 0-2 the top/middle/bottom of a taller face.
    sheet = Image.new('RGBA', (64, 64))
    for tx, (w, c) in enumerate(((1, 0), (3, 0), (3, 1), (3, 2))):
        for ty, (h, r) in enumerate(((3, 0), (3, 1), (3, 2), (1, 0))):
            sheet.paste(tile(p, kind, w, h, c, r), (tx * 16, ty * 16))
    return sheet


def window(p, mask):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(mask):
        for x, ch in enumerate(row):
            if ch == '#':
                im.putpixel((x, y), grain(x, y, p['hi'] if x in (0, 8) or y in (0, 15) else p['frame']))
            elif 'glass' in p:
                im.putpixel((x, y), p['glass'])
    return im


def tanks():
    for tank, p in PALETTES.items():
        T = A / 'textures/block/tank'
        T.mkdir(parents=True, exist_ok=True)
        for suffix, kind in (('', 'side'), ('_top', 'top'), ('_inner', 'inner')):
            tile(p, kind, 1, 1, 0, 0).save(T / f'{tank}{suffix}.png')
            connected(p, kind).save(T / f'{tank}{suffix}_connected.png')
        window(p, WINDOW).save(T / f'{tank}_window.png')
        window(p, WINDOW_SINGLE).save(T / f'{tank}_window_single.png')
        tex = lambda s: NS + f'block/tank/{tank}{s}'
        textures = {'0': tex('_top'), '1': tex(''), '3': tex('_window'), '4': tex('_inner'), '5': tex('_window_single'), 'particle': tex('')}
        for name in CREATE_TANK_MODELS:
            write(A / f'models/block/{tank}/{name}.json',
                  {'parent': f'create:block/fluid_tank/{name}', 'render_type': 'minecraft:translucent' if 'glass' in p else 'minecraft:cutout_mipped', 'textures': textures})
        variants = {}
        for top in ('false', 'true'):
            for bottom in ('false', 'true'):
                part = {('true', 'true'): 'single', ('true', 'false'): 'top', ('false', 'true'): 'bottom', ('false', 'false'): 'middle'}[top, bottom]
                for shape in ('plain', 'window', 'window_ne', 'window_nw', 'window_se', 'window_sw'):
                    suffix = '' if shape == 'plain' else '_' + shape
                    variants[f'bottom={bottom},shape={shape},top={top}'] = {'model': NS + f'block/{tank}/block_{part}{suffix}'}
        write(A / f'blockstates/{tank}.json', {'variants': variants})
        write(A / f'models/item/{tank}.json', {'parent': NS + f'block/{tank}/block_single_window'})


def box(f, t, tex, faces=('north', 'south', 'east', 'west', 'up', 'down')):
    x1, y1, z1 = f
    x2, y2, z2 = t
    uv = {'north': [16 - x2, 16 - y2, 16 - x1, 16 - y1], 'south': [x1, 16 - y2, x2, 16 - y1], 'west': [z1, 16 - y2, z2, 16 - y1],
          'east': [16 - z2, 16 - y2, 16 - z1, 16 - y1], 'up': [x1, z1, x2, z2], 'down': [x1, 16 - z2, x2, 16 - z1]}
    return {'from': f, 'to': t, 'faces': {face: {'uv': uv[face], 'texture': '#' + tex} for face in faces}}


def turn(f, t, side):
    """Rotate an arm authored for north (-Z) to another side."""
    def p(v):
        x, y, z = v
        return {'north': (x, y, z), 'south': (16 - x, y, 16 - z), 'east': (16 - z, y, x), 'west': (z, y, 16 - x),
                'up': (x, 16 - z, y), 'down': (x, z, 16 - y)}[side]
    a, b = p(f), p(t)
    return [min(a[i], b[i]) for i in range(3)], [max(a[i], b[i]) for i in range(3)]


def pipe(name, light, dark, face, band, collar, r):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            c = light if x in (0, 1) or y in (0, 1) else dark if x in (14, 15) or y in (14, 15) else face
            if 6 <= y <= 7 and 2 <= x <= 13:
                c = band
            im.putpixel((x, y), grain(x, y, c))
    im.save(A / f'textures/block/transport/{name}.png')
    textures = {'body': NS + f'block/transport/{name}', 'collar': collar if ':' in collar else NS + collar, 'particle': NS + f'block/transport/{name}'}
    a, b = 8 - r, 8 + r
    # Core, plain stub and flange touch without overlapping.
    write(A / f'models/block/{name}/core.json', {'parent': 'minecraft:block/block', 'textures': textures, 'elements': [box([a, a, a], [b, b, b], 'body')]})
    arms = {}
    for side in ('north', 'south', 'east', 'west', 'up', 'down'):
        arms[side] = [box(*turn([a, a, 0], [b, b, 2], side), 'body'), box(*turn([a - 1, a - 1, 2], [b + 1, b + 1, a], side), 'collar')]
        write(A / f'models/block/{name}/{side}.json', {'parent': 'minecraft:block/block', 'textures': textures, 'elements': arms[side]})
    write(A / f'models/block/{name}/inventory.json', {'parent': 'minecraft:block/block', 'textures': textures,
          'elements': [box([a, a, a], [b, b, b], 'body')] + arms['north'] + arms['south']})
    states = json.loads((A / 'blockstates/reinforced_pipe.json').read_text(encoding='utf-8'))
    for part in states['multipart']:
        part['apply']['model'] = part['apply']['model'].replace('reinforced_pipe', name)
    write(A / f'blockstates/{name}.json', states)
    write(A / f'models/item/{name}.json', {'parent': NS + f'block/{name}/inventory'})


def survival():
    recipes = {
        'gas_receiver': (['IBI', 'I I', 'IBI'], {'I': {'tag': 'c:plates/iron'}, 'B': {'tag': 'c:plates/brass'}}, 1),
        'pressure_tank': (['SBS', 'P P', 'SBS'], {'S': {'tag': 'c:plates/stainless_steel'}, 'B': {'tag': 'c:plates/brass'}, 'P': {'item': NS + 'reinforced_pipe'}}, 1),
        # Monel: nickel with copper.
        'nickel_alloy_pipe': ([' N ', 'CPC', ' N '], {'N': {'tag': 'c:ingots/nickel'}, 'C': {'tag': 'c:plates/copper'}, 'P': {'item': 'create:fluid_pipe'}}, 4),
        'zirconium_pipe': ([' Z ', 'SPS', ' Z '], {'Z': {'tag': 'c:ingots/zirconium'}, 'S': {'tag': 'c:plates/stainless_steel'}, 'P': {'item': 'create:fluid_pipe'}}, 4),
        # A stainless line in a stainless vacuum jacket lined with aluminium foil.
        'cryogenic_pipe': (['SAS', 'PPP', 'SAS'], {'S': {'tag': 'c:plates/stainless_steel'}, 'A': {'tag': 'c:ingots/aluminum'}, 'P': {'item': NS + 'stainless_steel_pipe'}}, 3),
        'pvc_pipe': ([' R ', 'RPR', ' R '], {'R': {'item': NS + 'pvc_resin'}, 'P': {'item': 'create:fluid_pipe'}}, 4),
        # Create's fluid tank recipe in stainless steel.
        'stainless_steel_tank': ([' S ', ' B ', ' S '], {'S': {'tag': 'c:plates/stainless_steel'}, 'B': {'item': 'minecraft:barrel'}}, 1),
        'cryogenic_tank': (['SAS', 'ABA', 'SAS'], {'S': {'tag': 'c:plates/stainless_steel'}, 'A': {'tag': 'c:plates/aluminum'}, 'B': {'item': 'minecraft:barrel'}}, 1),
    }
    for name, (pattern, key, count) in recipes.items():
        write(D / f'recipe/{name}.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern, 'key': key,
                                          'result': {'id': NS + name, 'count': count}})
        write(D / f'loot_table/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + name}],
                                                                                          'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    # Clay shaped into pipe and fired in a furnace.
    write(D / 'recipe/unfired_ceramic_pipe.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['CCC'],
                                                  'key': {'C': {'item': 'minecraft:clay_ball'}}, 'result': {'id': NS + 'unfired_ceramic_pipe', 'count': 2}})
    write(D / 'recipe/ceramic_pipe.json', {'type': 'minecraft:smelting', 'category': 'blocks', 'ingredient': {'item': NS + 'unfired_ceramic_pipe'},
                                          'result': {'id': NS + 'ceramic_pipe'}, 'experience': 0.1, 'cookingtime': 200})
    write(D / 'loot_table/blocks/ceramic_pipe.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + 'ceramic_pipe'}],
                                                                                          'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    tag('minecraft/tags/block/mineable/pickaxe.json', [NS + 'ceramic_pipe'])
    clay = 'minecraft:block/clay'
    write(A / 'models/item/unfired_ceramic_pipe.json', {'parent': NS + 'block/ceramic_pipe/inventory', 'textures': {'body': clay, 'collar': clay, 'particle': clay}})
    for language, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
        path = A / 'lang' / f'{language}.json'
        strings = json.loads(path.read_text(encoding='utf-8'))
        strings.update({key: names[index] for key, names in NAMES.items()})
        write(path, strings)


if __name__ == '__main__':
    tanks()
    for name, values in PIPES.items():
        pipe(name, *values)
    survival()
    print('Generated the plant tanks and the material pipes')
