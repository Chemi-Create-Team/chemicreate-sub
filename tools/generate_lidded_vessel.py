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
LINING = f'{NS}:block/lidded_vessel/internal/lining'
TEXTURES = {'particle': SEPARATOR, 'floor': LINING, 'bottom': SEPARATOR + '_top',
            'inner': LINING, 'rim': f'{NS}:block/lidded_vessel/internal/steel', 'side': SEPARATOR}
STIRRED = f'{NS}:block/tank/stirred_tank'


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
    pieces = (([4, 2, 4], [7, 4, 12]), ([9, 2, 4], [12, 4, 12]),
              ([7, 2, 4], [9, 4, 7]), ([7, 2, 9], [9, 4, 12]))
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
    # The north-facing model turns to the wall left of the front (opposite to the hinge).
    write(A / 'models/block/lidded_vessel/ladder.json', ladder())
    for hinge, turn in (('east', 0), ('south', 90), ('west', 180), ('north', 270)):
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


def impeller_parts(y, shaft_start, shaft_end):
    """A 10 px disk and four radial plates standing across its rim."""
    parts = [
        ([7, shaft_start, 7, 9, shaft_end, 9], 'shaft', 'shaft'),
        ([6, y - 2, 6, 10, y + 2, 10], 'brass', 'brass'),
    ]
    for z in range(3, 13):
        limit = max(abs(z - 8), abs(z + 1 - 8))
        half_width = 1 if limit == 5 else math.isqrt(25 - limit ** 2)
        parts.insert(0, ([8 - half_width, y, z, 8 + half_width, y + 1, z + 1], 'steel', 'steel'))
    for x, z, X, Z in ((11, 7, 15, 8), (8, 11, 9, 15), (1, 8, 5, 9), (7, 1, 8, 5)):
        parts.append(([x, y - 2, z, X, y + 2, Z], 'steel', 'steel'))
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
    target = A / 'textures/block/lidded_vessel/shell_connected.png'
    if target.exists():
        with Image.open(target) as previous:
            if previous.size == sheet.size and previous.convert('RGBA').tobytes() == sheet.tobytes():
                return
    target.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(target)


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
    for name, parts in (('reactor_impeller', impeller_parts(8, 3, 13)),
                        ('reactor_impeller_wide', impeller_parts(12, 10, 16))):
        write(A / f'models/block/lidded_vessel/internal/{name}.json', internal_model(parts))
    display = {
        'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [.8, .8, .8]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [.65, .65, .65]},
        'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [.8, .8, .8]},
    }
    write(A / 'models/item/reactor_impeller.json', {'parent': f'{NS}:block/lidded_vessel/internal/reactor_impeller', 'display': display})


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
    ('reactor_impeller', [' S ', 'PWP', ' S '], {'S': {'item': 'create:shaft'}, 'P': {'item': 'create:iron_sheet'}, 'W': {'item': 'create:whisk'}}),
    # The rotor column is precision work like the other big machines: only the mechanical crafter makes it.
)


NAMES = {
    f'block.{NS}.lidded_vessel': ('Stainless Tank', 'ステンレスタンク'),
    f'block.{NS}.stirred_tank': ('Stirred Tank', '撹拌槽'),
    f'lidded_vessel.{NS}.lid.open': ('Hatch open', 'ハッチ：開'),
    f'lidded_vessel.{NS}.lid.closed': ('Hatch closed', 'ハッチ：閉'),
    f'item.{NS}.reactor_impeller': ('Impeller', '撹拌翼'),
    f'item.{NS}.reactor_separator_core': ('Reaction Trays', '反応棚段'),
    f'reactor.{NS}.status.vessel': ('Use a 1×1 centrifuge vessel or a 3×3 reaction vessel', '遠心分離は1×1、反応槽は3×3で組み立ててください'),
    f'reactor.{NS}.status.contents': ('Empty the vessel first: it cannot hold anything of its own', '槽の中身を空にしてください'),
    f'reactor.{NS}.status.lid': ('Close the vessel hatch', '槽のハッチを閉じてください'),
    f'reactor.{NS}.status.structure': ('Build a 3×3 stirred tank at least two layers high and fit a stirrer on the roof', '3×3・2段以上の撹拌槽を組み、天面中央に撹拌機を置いてください'),
}


def names():
    for lang, index in (('en_us', 0), ('ja_jp', 1)):
        path = A / 'lang' / f'{lang}.json'
        raw = path.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
        strings = json.loads(raw)
        for key, text in NAMES.items(): strings[key] = text[index]
        path.write_bytes((json.dumps(strings, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))


# The separator sheet's grey levels, from its darkest groove to its brightest highlight.
GREY = 37, 51, 80, 101, 121, 142, 160, 224


def lining_texture():
    """The vessel's inside: plain brushed stainless with a girth weld along the foot, seamless all round so it wraps the round bore."""
    rng = random.Random(23)
    im = Image.new('RGBA', (16, 16))
    for y in range(15):
        x = 0
        while x < 16:
            run, level = rng.randint(3, 8), rng.choice((4, 4, 4, 5, 3))
            for k in range(min(run, 16 - x)):
                grey = GREY[level] + rng.choice((0, 0, 2, -2))
                im.putpixel((x + k, y), (grey, grey, grey + 2, 255))
            x += run
    for x in range(16):
        im.putpixel((x, 15), (GREY[2], GREY[2], GREY[2] + 2, 255))
        im.putpixel((x, 0), (GREY[6], GREY[6], GREY[6] + 2, 255))
    out = A / 'textures/block/lidded_vessel/internal/lining.png'
    out.parent.mkdir(parents=True, exist_ok=True)
    im.save(out)


def stirred_tank():
    """The stirred tank: the stainless tank's cells and hatch with a side that stretches the separator's middle panel across the whole face.

    The Create RECTANGLE sheet has columns single, left, middle, right and rows top, middle, bottom, single. Across a face the panel's own
    7 px frame and rounded corner sit at both ends and its striped plate repeats every block between them."""
    src = Image.open(A / 'textures/block/tank/ammonia_separator_connected.png').convert('RGBA')
    def source_column(x, width=48):
        # Column in a three-wide face of the original sheet: the panel frame starts 9 px in and ends 9 px before the right edge.
        return 9 + x if x < 7 else 38 - (width - 1 - x) if x >= width - 7 else 16 + (x - 7) % 16
    sheet = Image.new('RGBA', (64, 64))
    for tx in range(4):
        for ty in range(4):
            for y in range(16):
                for x in range(16):
                    if tx == 0: face_x = x if x < 7 else 32 + x if x >= 9 else x
                    else: face_x = (tx - 1) * 16 + x
                    sx = source_column(face_x)
                    tile = 1 if sx < 16 else 2 if sx < 32 else 3
                    sheet.putpixel((tx * 16 + x, ty * 16 + y), src.getpixel((tile * 16 + sx % 16, ty * 16 + y)))
    sheet.save(A / 'textures/block/tank/stirred_tank_connected.png')
    sheet.crop((0, 48, 16, 64)).save(A / 'textures/block/tank/stirred_tank.png')
    # Same cells, ladder and states as the stainless tank; each cell model only swaps the side texture.
    state = json.loads((A / 'blockstates/lidded_vessel.json').read_text(encoding='utf-8'))
    for part in state['multipart']:
        model = part['apply']['model']
        if '/cell_' not in model: continue
        cell = model.split('/')[-1]
        write(A / f'models/block/stirred_tank/{cell}.json', {'parent': model, 'textures': {'side': STIRRED, 'particle': STIRRED}})
        part['apply']['model'] = f'{NS}:block/stirred_tank/{cell}'
    write(A / 'blockstates/stirred_tank.json', state)
    write(A / 'models/item/stirred_tank.json', {'parent': f'{NS}:item/lidded_vessel', 'textures': {'side': STIRRED, 'particle': STIRRED}})
    write(D / 'loot_table/blocks/stirred_tank.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1,
        'entries': [{'type': 'minecraft:item', 'name': f'{NS}:stirred_tank'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    write(D / 'recipe/stirred_tank.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['ISI', 'ITI', 'III'],
        'key': {'I': {'tag': 'c:ingots/steel'}, 'S': {'item': 'create:shaft'}, 'T': {'item': f'{NS}:lidded_vessel'}},
        'result': {'id': f'{NS}:stirred_tank', 'count': 1}})
    tag = ROOT / 'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json'
    raw = tag.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
    values = json.loads(raw)
    if f'{NS}:stirred_tank' not in values['values']:
        values['values'].append(f'{NS}:stirred_tank')
        tag.write_bytes((json.dumps(values, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))


def stirred_cap():
    """The 3x3 stirred tank's roof: the stainless cap sheet with its hatch panel and braces swapped for plain plate around the 40 px bore.

    Only the 48 px joined cap (sheet x 16..64, y 0..48) changes; the riveted outer frame stays, a rolled rim rings the bore
    and a rivet sits in each corner left between the rim and the frame."""
    rng = random.Random(41)
    sheet = Image.open(ROOT / 'tools/data/lidded_vessel_cap_connected.png').convert('RGBA')
    def grey(level, shift=0):
        g = GREY[level] + shift
        return g, g + 4, g + 4, 255
    for y in range(48):
        x = 0
        while x < 48:
            run, level = rng.randint(3, 8), rng.choice((4, 4, 4, 5, 3))
            for k in range(min(run, 48 - x)):
                px, r = x + k, math.hypot(x + k + .5 - 24, y + .5 - 24)
                if min(px, y, 47 - px, 47 - y) <= 4 and r >= 22: continue  # outer frame
                colour = (grey(0) if r < 21 else grey(7, -48) if r < 22 else grey(2) if r < 23
                          else grey(level, rng.choice((0, 0, 2, -2))))
                sheet.putpixel((16 + px, y), colour)
            x += run
    for cx, cy in (7, 7), (40, 7), (7, 40), (40, 40):
        sheet.putpixel((16 + cx, cy), grey(7)); sheet.putpixel((17 + cx, cy), grey(5))
        sheet.putpixel((16 + cx, cy + 1), grey(2)); sheet.putpixel((17 + cx, cy + 1), grey(0))
    sheet.save(A / 'textures/block/lidded_vessel/stirred_cap.png')


if __name__ == '__main__':
    models()
    shaft_bearing()
    shell_connected()
    top_textures()
    window_texture()
    internal_textures()
    internals()
    data()
    lining_texture()
    stirred_tank()
    stirred_cap()
    names()
