#!/usr/bin/env python3
"""Square lidded vessels up to 3x3 and four high, their reaction-plant internals, item models, loot, recipes and names.

The walls use the original tank-shaped separator's skin. Run from the repository root.
"""
import json
import math
import random
from copy import deepcopy
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
D = ROOT / 'src/main/resources/data/create_refinery_n_reactors'
NS = 'create_refinery_n_reactors'
SIDES = 'north', 'east', 'south', 'west'
S = f'{NS}:block/reactor/silver/'


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')


SEPARATOR = f'{NS}:block/tank/ammonia_separator'
TEXTURES = {'particle': SEPARATOR, 'floor': SEPARATOR + '_inner', 'bottom': SEPARATOR + '_top',
            'inner': SEPARATOR + '_inner', 'rim': f'{NS}:block/lidded_vessel/internal/steel', 'side': SEPARATOR}


def cell(outer, bottom=True):
    """The outer walls of one block, with the floor in the bottom layer; walls along X run the full length and walls along Z fit between them."""
    tex = dict(TEXTURES)
    elements = [{'from': [0, 0, 0], 'to': [16, 2, 16], 'faces': {'up': {'texture': '#floor'}, 'down': {'texture': '#bottom', 'cullface': 'down'}}}] if bottom else []
    for face, z0, z1, inward in (('north', 0, 2, 'south'), ('south', 14, 16, 'north')):
        if not outer[face]: continue
        faces = {face: {'texture': '#side', 'cullface': face}, inward: {'texture': '#inner'}, 'up': {'texture': '#rim'}}
        if outer['west']: faces['west'] = {'texture': '#side', 'cullface': 'west'}
        if outer['east']: faces['east'] = {'texture': '#side', 'cullface': 'east'}
        elements.append({'from': [0, 0, z0], 'to': [16, 16, z1], 'faces': faces})
    for face, x0, x1, inward in (('west', 0, 2, 'east'), ('east', 14, 16, 'west')):
        if not outer[face]: continue
        z0, z1 = (2 if outer['north'] else 0), (14 if outer['south'] else 16)
        faces = {face: {'texture': '#side', 'cullface': face}, inward: {'texture': '#inner'}, 'up': {'texture': '#rim'}}
        elements.append({'from': [x0, 0, z0], 'to': [x1, 16, z1], 'faces': faces})
    return {'parent': 'minecraft:block/block', 'textures': tex, 'elements': elements}


def name(outer, bottom=True):
    return 'cell_' + (''.join(face[0] for face in SIDES if outer[face]) or 'inside') + ('' if bottom else '_upper')


def ladder():
    """Two rails and a rung every 4 px standing 1 px off the north face, as on the distillation column."""
    boxes = [([4, 0, -1, 6, 16, 0], 'rail'), ([10, 0, -1, 12, 16, 0], 'rail')] + [([6, y, -1, 10, y + 1, 0], 'rung') for y in (2, 6, 10, 14)]
    return {'parent': 'minecraft:block/block', 'textures': {'rail': S + 'frame', 'rung': S + 'trim', 'particle': S + 'frame'},
            'elements': [{'from': b[:3], 'to': b[3:], 'faces': {face: surface(b, face, t)['faces'][face] for face in ('north', 'south', 'west', 'east', 'up', 'down')}} for b, t in boxes]}


def shaft_bearing():
    pieces = (([4, 4, 15], [6, 12, 17]), ([10, 4, 15], [12, 12, 17]),
              ([6, 4, 15], [10, 6, 17]), ([6, 10, 15], [10, 12, 17]))
    elements = []
    for a, b in pieces:
        dx, dy, dz = (b[i] - a[i] for i in range(3))
        sizes = {'north': (dx, dy), 'south': (dx, dy), 'east': (dz, dy), 'west': (dz, dy), 'up': (dx, dz), 'down': (dx, dz)}
        elements.append({'from': a, 'to': b, 'faces': {face: {'texture': '#rim', 'uv': [0, 0, w, h]} for face, (w, h) in sizes.items()}})
    write(A / 'models/block/lidded_vessel/shaft_bearing.json', {'parent': 'minecraft:block/block', 'textures': {'rim': S + 'trim', 'particle': S + 'frame'}, 'elements': elements})


def models():
    parts = []
    for bottom in (True, False):
        for mask in range(16):
            outer = {face: bool(mask >> i & 1) for i, face in enumerate(SIDES)}
            write(A / f'models/block/lidded_vessel/{name(outer, bottom)}.json', cell(outer, bottom))
            when = {'bottom': str(bottom).lower(), **{face: str(outer[face]).lower() for face in SIDES}}
            parts.append({'when': when, 'apply': {'model': f'{NS}:block/lidded_vessel/{name(outer, bottom)}'}})
    # The ladder faces away from the hinge; the model is drawn on the north face.
    write(A / 'models/block/lidded_vessel/ladder.json', ladder())
    for hinge, turn in (('south', 0), ('west', 90), ('north', 180), ('east', 270)):
        parts.append({'when': {'ladder': 'true', 'facing': hinge}, 'apply': {'model': f'{NS}:block/lidded_vessel/ladder', 'y': turn}})
    write(A / 'blockstates/lidded_vessel.json', {'multipart': parts})
    item = cell({face: True for face in SIDES})
    item['parent'] = 'minecraft:block/block'
    item['textures']['lid'] = SEPARATOR + '_top'
    item['elements'].append({'from': [2, 14, 2], 'to': [14, 16, 14], 'faces': {'up': {'texture': '#lid'}, 'down': {'texture': '#inner'},
        **{face: {'texture': '#rim', 'uv': [0, 0, 12, 2]} for face in SIDES}}})
    write(A / 'models/item/lidded_vessel.json', item)


STEEL = (45, 53, 55), (67, 76, 78), (90, 100, 102), (115, 125, 125), (141, 151, 149), (173, 184, 177)
BRASS = (68, 51, 32), (102, 76, 39), (145, 110, 54), (186, 148, 76), (214, 182, 111)
PELLET = (60, 40, 32), (87, 54, 38), (117, 76, 49), (144, 102, 67)


def internal_parts():
    """Installed parts use a local (8, 8) centre. Visible bodies stay between the floor and lid, at Y2..14."""
    parts = {}
    def box(target, bounds, side, ends=None): target.append((bounds, side, ends or side))
    def pixels(target, mask, y0, y1, side, ends=None):
        for x, z in sorted(mask): box(target, [x, y0, z, x + 1, y1, z + 1], side, ends)
    def octagon(x, z, radius): return max(abs(x - 7.5), abs(z - 7.5)) < radius and abs(x - 7.5) + abs(z - 7.5) < radius * 1.7
    def circle(radius):
        return {(x, z) for x in range(-6, 22) for z in range(-6, 22)
                if max((xx - 8) ** 2 + (zz - 8) ** 2 for xx in (x, x + 1) for zz in (z, z + 1)) <= radius ** 2}

    basket = parts['reactor_catalyst_basket'] = []
    rim = {(x, z) for x in range(-4, 20) for z in range(-4, 20) if octagon(x, z, 12) and not octagon(x, z, 10)}
    pixels(basket, rim, 2, 4, 'basket_rim')
    pixels(basket, rim, 12, 14, 'basket_rim')
    for x in (0, 6, 12):
        for z in (0, 6, 12):
            box(basket, [x - 1, 4, z - 1, x + 5, 10 + ((x + z) // 6 % 2), z + 5], 'pellets')
    for at in range(-2, 19, 4):
        box(basket, [at, 4, -3, at + 1, 12, -2], 'basket')
        box(basket, [at, 4, 18, at + 1, 12, 19], 'basket')
        box(basket, [-3, 4, at, -2, 12, at + 1], 'basket')
        box(basket, [18, 4, at, 19, 12, at + 1], 'basket')
    for y in (6, 9):
        box(basket, [-2, y, -3, 18, y + 1, -2], 'basket')
        box(basket, [-2, y, 18, 18, y + 1, 19], 'basket')
        box(basket, [-3, y, -2, -2, y + 1, 18], 'basket')
        box(basket, [18, y, -2, 19, y + 1, 18], 'basket')
    for x in (-2, 16):
        for z in (-2, 16): box(basket, [x, 3, z, x + 2, 13, z + 2], 'basket_rim')
    box(basket, [7, 12, -2, 9, 13, 18], 'basket_rim')
    for z in (2, 12):
        box(basket, [4, 11, z, 5, 14, z + 1], 'steel')
        box(basket, [11, 11, z, 12, 14, z + 1], 'steel')
        box(basket, [5, 13, z, 11, 14, z + 1], 'steel')
    box(basket, [6, 13, 7, 10, 14, 9], 'brass')

    impeller = parts['reactor_impeller'] = []
    box(impeller, [7, 0, 7, 9, 16, 9], 'shaft')
    for y, phase in ((3, 0), (10, math.pi / 6)):
        pixels(impeller, circle(7), y + 1, y + 2, 'steel')
        box(impeller, [5, y, 5, 11, y + 3, 11], 'brass')
        for blade in range(6):
            angle = phase + blade * math.tau / 6
            c, s = math.cos(angle), math.sin(angle)
            mask = set()
            for x, z in circle(13):
                dx, dz = x - 7.5, z - 7.5
                along, across = dx * c + dz * s, -dx * s + dz * c
                if 5 <= along <= 12 and abs(across) <= 1.25: mask.add((x, z))
            pixels(impeller, mask, y, y + 3, 'steel')
            bx, bz = round(7.5 + 4 * math.cos(angle + math.pi / 6)), round(7.5 + 4 * math.sin(angle + math.pi / 6))
            box(impeller, [bx, y + 2, bz, bx + 1, y + 3, bz + 1], 'basket_rim')
    box(impeller, [6, 2, 6, 10, 3, 10], 'basket_rim')
    box(impeller, [6, 13, 6, 10, 14, 10], 'basket_rim')

    return parts


def wide_internal_parts():
    """Three-block vessel internals: 38..44 px bodies inside the 44 px clear bore, keeping 16 px/block UVs."""
    parts = {}
    def box(target, bounds, side, ends=None): target.append((bounds, side, ends or side))
    def pixels(target, mask, y0, y1, side, ends=None):
        for x, z in sorted(mask): box(target, [x, y0, z, x + 1, y1, z + 1], side, ends)
    def octagon(radius):
        return {(x, z) for x in range(-14, 30) for z in range(-14, 30)
                if max(abs(x - 7.5), abs(z - 7.5)) < radius and abs(x - 7.5) + abs(z - 7.5) < radius * 1.7}
    def circle(radius):
        return {(x, z) for x in range(-14, 30) for z in range(-14, 30)
                if max((xx - 8) ** 2 + (zz - 8) ** 2 for xx in (x, x + 1) for zz in (z, z + 1)) <= radius ** 2}

    basket = parts['reactor_catalyst_basket_wide'] = []
    ring = octagon(21) - octagon(19)
    for y in (2, 12): pixels(basket, ring, y, y + 2, 'basket_rim')
    for x in (-8, -1, 6, 13, 20):
        for z in (-8, -1, 6, 13, 20): box(basket, [x, 4, z, x + 5, 10 + ((x + z) % 3), z + 5], 'pellets')
    for at in range(-10, 28, 4):
        for edge in (-12, 27):
            box(basket, [at, 4, edge, at + 1, 12, edge + 1], 'basket')
            box(basket, [edge, 4, at, edge + 1, 12, at + 1], 'basket')
    for y in (6, 9):
        for edge in (-12, 27):
            box(basket, [-11, y, edge, 27, y + 1, edge + 1], 'basket_rim')
            box(basket, [edge, y, -11, edge + 1, y + 1, 27], 'basket_rim')
    box(basket, [7, 12, -11, 9, 14, 27], 'basket_rim')
    box(basket, [5, 13, 6, 11, 14, 10], 'brass')

    impeller = parts['reactor_impeller_wide'] = []
    box(impeller, [7, 0, 7, 9, 16, 9], 'shaft')
    for y, phase in ((3, 0), (10, math.pi / 6)):
        pixels(impeller, circle(8), y + 1, y + 2, 'steel')
        box(impeller, [5, y, 5, 11, y + 3, 11], 'brass')
        for blade in range(6):
            angle = phase + blade * math.tau / 6
            c, s = math.cos(angle), math.sin(angle)
            mask = {(x, z) for x, z in circle(20)
                    if 6 <= (x - 7.5) * c + (z - 7.5) * s <= 19
                    and abs(-(x - 7.5) * s + (z - 7.5) * c) <= 2}
            pixels(impeller, mask, y, y + 3, 'steel')

    return parts


def internal_textures():
    """Machined steel, cast fittings and catalyst grains, all at 16 px per block."""
    out = A / 'textures/block/lidded_vessel/internal'
    out.mkdir(parents=True, exist_ok=True)
    rng = random.Random(41)
    for name in ('steel', 'basket', 'basket_rim', 'pellets', 'shaft', 'brass', 'tray', 'tray_edge'):
        im = Image.new('RGBA', (16, 16))
        palette = PELLET if name == 'pellets' else BRASS if name == 'brass' else STEEL
        patches = [[rng.choice((1, 2, 2, 2, 3)) for _ in range(8)] for _ in range(8)]
        for y in range(16):
            for x in range(16):
                shade = patches[y // 2][x // 2]
                if name == 'pellets':
                    shade = min(3, shade)
                elif name == 'shaft':
                    shade = (1, 2, 3, 5, 4, 3, 2, 1)[x // 2]
                    if y in (0, 15): shade = 1
                elif name in ('basket', 'basket_rim', 'tray_edge'):
                    if x == 0 or y == 0: shade = 4
                    elif x == 15 or y == 15: shade = 0
                elif name == 'tray':
                    if x % 6 in (2, 3) and y % 6 in (2, 3): shade = 1
                    elif x % 6 == 4 and y % 6 in (2, 3): shade = 4
                elif name == 'brass':
                    if x == 0 or y == 0: shade = 4
                    elif x == 15 or y == 15: shade = 0
                im.putpixel((x, y), palette[shade] + (255,))
        im.save(out / f'{name}.png')


def shell_connected():
    """Create's RECTANGLE sheet from the silver plate: where a block of the same vessel is beside it, the plate's dark edge columns and its top or bottom band
    give way to the grooved panel, so the frame and bands run only round the whole face."""
    shell = Image.open(A / 'textures/block/reactor/silver/shell.png').convert('RGBA')
    sheet = Image.new('RGBA', (64, 64))
    def tile(left, right, up, down):
        im = Image.new('RGBA', (16, 16))
        for y in range(16):
            sy = y
            if up and y <= 4: sy = 7 + y % 2
            if down and y >= 11: sy = 7 + y % 2
            for x in range(16):
                sx = x
                if left and x <= 3: sx = 4 + x % 4
                if right and x >= 12: sx = 8 + x % 4
                im.putpixel((x, y), shell.getpixel((sx, sy)))
        return im
    # Columns: one block wide, then the left, middle and right of a wider face; rows: the top, middle and bottom of a taller face, then one block high.
    for tx, (left, right) in enumerate(((False, False), (False, True), (True, True), (True, False))):
        for ty, (up, down) in enumerate(((False, True), (True, True), (True, False), (False, False))):
            sheet.paste(tile(left, right, up, down), (tx * 16, ty * 16))
    (A / 'textures/block/lidded_vessel').mkdir(parents=True, exist_ok=True)
    sheet.save(A / 'textures/block/lidded_vessel/shell_connected.png')


def top_textures():
    """The two-wide joined plate; three-wide caps use the shared tank connected sprites."""
    out = A / 'textures/block/lidded_vessel'
    out.mkdir(parents=True, exist_ok=True)
    field = ((112, 115, 114, 255), (122, 125, 123, 255), (132, 135, 133, 255), (143, 146, 143, 255))
    dark, edge, light, shade = (52, 57, 55, 255), (86, 93, 89, 255), (190, 197, 190, 255), (99, 107, 101, 255)
    patches = ('11212120', '12322111', '22101212', '11213221', '21122113', '12321221', '22111212', '11232121')
    for width in (2,):
        size = width * 16
        h0, h1 = size // 2 - 8, size // 2 + 8
        image = Image.new('RGBA', (size, size))
        for y in range(size):
            for x in range(size):
                colour = field[int(patches[(y // 2) % 8][(x // 2) % 8])]
                if x in (2, size - 3) or y in (2, size - 3): colour = dark
                elif x == 3 or y == 3: colour = light
                elif x == size - 4 or y == size - 4: colour = shade
                if h0 - 2 <= x <= h1 + 1 and h0 - 2 <= y <= h1 + 1:
                    if x in (h0 - 2, h1 + 1) or y in (h0 - 2, h1 + 1): colour = dark
                    elif x == h0 - 1 or y == h0 - 1: colour = light
                    elif x == h1 or y == h1: colour = edge
                image.putpixel((x, y), colour)
        image.save(out / f'top_{width}.png')


def window_texture():
    out = A / 'textures/block/lidded_vessel/window_frost.png'
    out.parent.mkdir(parents=True, exist_ok=True)
    frost = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge < 2: frost.putpixel((x, y), (183, 211, 215, 160 if edge == 0 else 95))
            elif (x + y) % 9 == 0 or (x - y) % 11 == 0: frost.putpixel((x, y), (214, 231, 228, 90))
    frost.save(out.with_name('window_frost.png'))
    sludge = Image.new('RGBA', (16, 16), (65, 59, 44, 255))
    palette = ((54, 51, 41, 255), (76, 66, 47, 255), (87, 76, 52, 255), (61, 60, 47, 255))
    rng = random.Random(2906)
    for y in range(0, 16, 2):
        for x in range(0, 16, 2):
            colour = rng.choice(palette)
            for dy in range(2):
                for dx in range(2): sludge.putpixel((x + dx, y + dy), colour)
    sludge.save(out.with_name('sludge.png'))
    atlas_path = ROOT / 'src/main/resources/assets/minecraft/atlases/blocks.json'
    atlas = json.loads(atlas_path.read_text())
    for name in ('window_frost', 'sludge'):
        source = {'type': 'single', 'resource': f'{NS}:block/lidded_vessel/{name}'}
        if source not in atlas['sources']: atlas['sources'].append(source)
    write(atlas_path, atlas)


def surface(bounds, face, material):
    x, y, z, X, Y, Z = bounds
    uv = {'north': [16 - X, 16 - Y, 16 - x, 16 - y], 'south': [x, 16 - Y, X, 16 - y], 'west': [z, 16 - Y, Z, 16 - y], 'east': [16 - Z, 16 - Y, 16 - z, 16 - y],
          'up': [x, z, X, Z], 'down': [x, 16 - Z, X, 16 - z]}[face]
    shift = -16 * (min(uv[0], uv[2]) // 16)
    uv = [v + shift if i % 2 == 0 else v for i, v in enumerate(uv)]
    if face in ('up', 'down'):
        shift = -16 * (min(uv[1], uv[3]) // 16)
        uv = [v + shift if i % 2 == 1 else v for i, v in enumerate(uv)]
    return {'from': [x, y, z], 'to': [X, Y, Z], 'faces': {face: {'uv': uv, 'texture': '#' + material}}}


def internal_model(parts):
    """Merge exposed voxel surfaces, split at texture tiles; no buried faces or stretched UVs."""
    voxels = {}
    for bounds, side, ends in parts:
        x0, y0, z0, x1, y1, z1 = bounds
        for x in range(x0, x1):
            for y in range(y0, y1):
                for z in range(z0, z1): voxels[x, y, z] = side, ends
    directions = {'west': (-1, 0, 0), 'east': (1, 0, 0), 'down': (0, -1, 0),
                  'up': (0, 1, 0), 'north': (0, 0, -1), 'south': (0, 0, 1)}
    planes = {}
    for (x, y, z), (side, ends) in voxels.items():
        for face, (dx, dy, dz) in directions.items():
            if (x + dx, y + dy, z + dz) in voxels: continue
            if dx: plane, u, v = x + (dx > 0), z, y
            elif dy: plane, u, v = y + (dy > 0), x, z
            else: plane, u, v = z + (dz > 0), x, y
            material = ends if dy else side
            planes.setdefault((face, plane, material, u // 16, v // 16), set()).add((u, v))
    elements, textures = [], {}
    for (face, plane, material, _, _), pixels in sorted(planes.items()):
        textures[material] = f'{NS}:block/lidded_vessel/internal/{material}'
        while pixels:
            u, v = min(pixels, key=lambda p: (p[1], p[0]))
            width, height = 1, 1
            while (u + width, v) in pixels: width += 1
            while all((uu, v + height) in pixels for uu in range(u, u + width)): height += 1
            pixels.difference_update((uu, vv) for uu in range(u, u + width) for vv in range(v, v + height))
            if face in ('west', 'east'): bounds = [plane, v, u, plane, v + height, u + width]
            elif face in ('down', 'up'): bounds = [u, plane, v, u + width, plane, v + height]
            else: bounds = [u, v, plane, u + width, v + height, plane]
            elements.append(surface(bounds, face, material))
    return {'parent': 'minecraft:block/block', 'textures': {**textures, 'particle': f'{NS}:block/lidded_vessel/internal/steel'}, 'elements': elements}


def internals():
    from generate_uranium_assets import rotor_model
    rotor_model()
    models = {}
    for item, parts in (internal_parts() | wide_internal_parts()).items():
        model = models[item] = internal_model(parts)
        write(A / f'models/block/lidded_vessel/internal/{item}.json', model)
    display = {
        'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [.22, .22, .22]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [.15, .15, .15]},
        'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [.27, .27, .27]},
    }
    for item in models:
        if item.endswith('_wide'): continue
        installed = item + '_wide'
        view = {'parent': f'{NS}:block/lidded_vessel/internal/{installed}', 'display': display}
        write(A / f'models/item/{item}.json', view)


def data():
    write(D / 'loot_table/blocks/lidded_vessel.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1,
        'entries': [{'type': 'minecraft:item', 'name': f'{NS}:lidded_vessel'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    write(D / 'recipe/lidded_vessel.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['ITI', 'IBI'],
        'key': {'I': {'tag': 'c:plates/stainless_steel'}, 'T': {'item': 'minecraft:iron_trapdoor'}, 'B': {'item': 'create:basin'}},
        'result': {'id': f'{NS}:lidded_vessel', 'count': 1}})
    for item, pattern, key in INTERNAL_RECIPES:
        recipe = {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern, 'key': key, 'result': {'id': f'{NS}:{item}', 'count': 1}}
        if len(pattern) > 3: recipe |= {'type': 'create:mechanical_crafting', 'accept_mirrored': True, 'show_notification': True}
        write(D / f'recipe/{item}.json', recipe)
    tag = ROOT / 'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json'
    raw = tag.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
    values = json.loads(raw)
    if f'{NS}:lidded_vessel' not in values['values']:
        values['values'].append(f'{NS}:lidded_vessel')
        tag.write_bytes((json.dumps(values, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))


INTERNAL_RECIPES = (
    ('reactor_catalyst_basket', ['PIP', 'I I', 'PIP'], {'P': {'item': 'create:iron_sheet'}, 'I': {'item': 'minecraft:iron_bars'}}),
    ('reactor_impeller', [' S ', 'PWP', ' S '], {'S': {'item': 'create:shaft'}, 'P': {'item': 'create:iron_sheet'}, 'W': {'item': 'create:whisk'}}),
    # The rotor column is precision work like the other big machines: only the mechanical crafter makes it.
)


NAMES = {
    f'block.{NS}.lidded_vessel': ('Stainless Tank', 'ステンレスタンク'),
    f'lidded_vessel.{NS}.lid.open': ('Hatch open', 'ハッチ：開'),
    f'lidded_vessel.{NS}.lid.closed': ('Hatch closed', 'ハッチ：閉'),
    f'item.{NS}.reactor_catalyst_basket': ('Catalyst Basket', '触媒充填バスケット'),
    f'item.{NS}.reactor_impeller': ('Impeller', '撹拌翼'),
    f'item.{NS}.reactor_separator_core': ('Reaction Trays', '反応棚段'),
    f'reactor.{NS}.status.vessel': ('Use a 1x1 centrifuge vessel or a 3x3 reaction vessel', '遠心分離は1×1、反応槽は3×3で組み立ててください'),
    f'reactor.{NS}.status.contents': ('Empty the vessel first: it cannot hold anything of its own', '槽の中身を空にしてください'),
    f'reactor.{NS}.status.lid': ('Close the vessel hatch', '槽のハッチを閉じてください'),
    f'reactor.{NS}.status.core': ('Missing functional internals in one or more layers', '内部部品が不足している段があります'),
    f'reactor.{NS}.status.structure': ('Assemble a lidded vessel with matching internals', 'ふた付き槽を組み立てて各段に同じ内部部品を入れてください'),
}


def names():
    for lang, index in (('en_us', 0), ('ja_jp', 1)):
        path = A / 'lang' / f'{lang}.json'
        raw = path.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
        strings = json.loads(raw)
        for key, text in NAMES.items(): strings[key] = text[index]
        path.write_bytes((json.dumps(strings, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))


if __name__ == '__main__':
    models()
    shaft_bearing()
    shell_connected()
    top_textures()
    window_texture()
    internal_textures()
    internals()
    data()
    names()
