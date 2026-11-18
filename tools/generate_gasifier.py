#!/usr/bin/env python3
"""Assets, recipes and the teaching structure for the steam gasifier, one block laid 3x3 and six layers high."""
import json
from pathlib import Path
from PIL import Image, ImageDraw
import generate_petrochemical_ponder as ponder

ROOT = Path(__file__).resolve().parents[1]
NS = 'create_refinery_n_reactors:'
A = ROOT / ('src/main/resources/assets/' + NS[:-1])
D = ROOT / ('src/main/resources/data/' + NS[:-1])
BLOCK = 'steam_gasifier'
RETIRED = ('gasifier_casing', 'water_cooled_refractory_wall', 'entrained_burner_nozzle', 'slag_tap_hearth', 'quench_sight_glass')
TEX = A / 'textures/block/steam_gasifier'
REF = A / 'textures/block/tank'
# Layers, counted from 0 at the base: the base band, the brick-lined reaction bed, and the lid band.
LAYERS, BED = 6, (1, 4)
VIEWPORT_LAYERS = (2, 3)
SIDES = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}
OPPOSITE = {'up': 'down', 'down': 'up', 'north': 'south', 'south': 'north', 'west': 'east', 'east': 'west'}
# The direction in which a face's left-to-right coordinate grows, seen from outside.
ACROSS = {'north': 'west', 'south': 'east', 'west': 'south', 'east': 'north'}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')


def read(path):
    return json.loads(path.read_text())


def cell(layer, dx, dz):
    """GasifierBlock.CELL: 0 for a loose block, otherwise the position in the formed furnace."""
    return 1 + layer * 9 + (dx + 1) * 3 + dz + 1


def whiten(image):
    """The reaction tower's dark plate repainted as a pale, slightly warm iron."""
    dark, light = (96, 99, 97), (238, 239, 235)
    out = image.convert('RGBA').copy()
    pixels = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = pixels[x, y]
            t = min(1, max(0, ((.299 * r + .587 * g + .114 * b) - 18) / 120)) ** .8
            pixels[x, y] = tuple(round(lo + (hi - lo) * t) for lo, hi in zip(dark, light)) + (a,)
    return out


def tiles(sheet, columns, rows):
    out = Image.new('RGBA', (16 * len(columns), 16 * len(rows)))
    for i, column in enumerate(columns):
        for j, row in enumerate(rows):
            out.paste(sheet.crop((16 * column, 16 * row, 16 * column + 16, 16 * row + 16)), (16 * i, 16 * j))
    return out


def lining():
    """Firebrick behind a riveted iron frame, with an iron hoop at each block joint, for the four reaction-bed layers."""
    img = Image.new('RGBA', (48, 64), '#7c6d61')
    pen = ImageDraw.Draw(img)
    shades = ('#9a4b33', '#a5563a', '#8c432f', '#a04f36')
    for row in range(16):
        y = 4 * row
        for column in range(-1, 7):
            x = 8 * column + (4 if row % 2 else 0)
            shade = shades[(row * 3 + column * 5) % len(shades)]
            pen.rectangle((x + 1, y + 1, x + 7, y + 3), fill=shade)
            pen.line((x + 1, y + 1, x + 6, y + 1), fill='#b8674a')
            pen.line((x + 2, y + 3, x + 7, y + 3), fill='#7a3a29')
    frame, edge, rivet = '#d8dad6', '#a3a7a3', '#8a8e8b'

    def strap(box, horizontal):
        pen.rectangle(box, fill=frame)
        x0, y0, x1, y1 = box
        pen.line((x0, y1, x1, y1) if horizontal else (x1, y0, x1, y1), fill=edge)
        span = range(x0 + 3, x1, 6) if horizontal else range(y0 + 3, y1, 6)
        for k in span:
            point = (k, (y0 + y1) // 2) if horizontal else ((x0 + x1) // 2, k)
            pen.point(point, fill=rivet)
            pen.point((point[0] - 1, point[1] - 1), fill='#f4f5f2')

    for y in (16, 32, 48):
        strap((0, y - 1, 47, y), True)
    strap((0, 0, 47, 2), True)
    strap((0, 61, 47, 63), True)
    strap((0, 0, 2, 63), False)
    strap((45, 0, 47, 63), False)
    return img


def port():
    """A bolted flange around a dark bore, for the steam inlets, the gas outlet and the sight-glass frames."""
    img = Image.new('RGBA', (16, 16), '#c9ccc8')
    pen = ImageDraw.Draw(img)
    pen.rectangle((0, 0, 15, 15), outline='#8f9390')
    pen.rectangle((4, 4, 11, 11), fill='#2a2826')
    pen.rectangle((5, 5, 10, 10), fill='#1b1a19')
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13), (7, 1), (1, 7), (14, 8), (8, 14)):
        pen.point((x, y), fill='#6f7370')
    return img


def coke(hot):
    """The coke bed seen through a sight glass: red-hot lumps while the furnace runs, dull grey once cold."""
    base, lump, glint = (('#c23a10', '#ff8a2a', '#ffd27a') if hot else ('#2b2826', '#4a4542', '#6a6461'))
    img = Image.new('RGBA', (16, 16), base)
    pen = ImageDraw.Draw(img)
    for x, y, r in ((3, 4, 2), (9, 3, 2), (12, 9, 2), (5, 11, 3), (10, 13, 1), (1, 9, 1), (7, 7, 1)):
        pen.ellipse((x - r, y - r, x + r, y + r), fill=lump)
        pen.point((x - r // 2, y - r // 2), fill=glint)
    pen.line((2, 14, 7, 9), fill='#ffffff60' if hot else '#ffffff30')
    return img


def textures():
    TEX.mkdir(parents=True, exist_ok=True)
    side = whiten(Image.open(REF / 'reaction_tower_connected.png'))
    top = whiten(Image.open(REF / 'reaction_tower_top_connected.png'))
    whiten(Image.open(REF / 'reaction_tower.png')).save(TEX / 'shell.png')
    whiten(Image.open(REF / 'reaction_tower_top.png')).save(TEX / 'plate.png')
    tiles(side, (1, 2, 3), (3,)).save(TEX / 'band.png')
    tiles(top, (1, 2, 3), (0, 1, 2)).save(TEX / 'lid.png')
    lining().save(TEX / 'lining.png')
    port().save(TEX / 'port.png')
    coke(True).save(TEX / 'coke_hot.png')
    coke(False).save(TEX / 'coke_cold.png')


SECTION = {'band': (48, 16), 'lining': (48, 64), 'lid': (48, 48)}


def side_uv(texture, face, layer, dx, dz, s0, s1, y0, y1):
    """UV in a section texture for the part of an outside face between s0 and s1 (left to right, seen from outside) and y0 and y1."""
    width, height = SECTION[texture]
    column = {'north': 1 - dx, 'south': dx + 1, 'east': 1 - dz, 'west': dz + 1}[face]
    top_layer = BED[1] if texture == 'lining' else layer
    u = lambda s: (16 * column + s) * 16 / width
    v = lambda y: (16 * (top_layer - layer) + 16 - y) * 16 / height
    return [u(s0), v(y1), u(s1), v(y0)]


def lid_uv(face, dx, dz):
    z = (dz + 1) * 16 / 3
    if face == 'down':
        z = (1 - dz) * 16 / 3
    x = (dx + 1) * 16 / 3
    return [x, z, x + 16 / 3, z + 16 / 3]


def element(lo, hi, faces):
    return {'from': list(lo), 'to': list(hi), 'faces': faces}


def section(layer):
    return 'band' if layer in (0, LAYERS - 1) else 'lining'


def to_block(face, s, depth):
    """A point given across an outside face (s, seen from outside) and its depth into the block, as block x and z."""
    return {'north': (16 - s, depth), 'south': (s, 16 - depth), 'west': (depth, s), 'east': (16 - depth, 16 - s)}[face]


def box(face, s0, s1, y0, y1, d0, d1):
    (ax, az), (bx, bz) = to_block(face, s0, d0), to_block(face, s1, d1)
    return [min(ax, bx), y0, min(az, bz)], [max(ax, bx), y1, max(az, bz)]


def outside_faces(layer, dx, dz):
    faces = [name for name, (x, z) in SIDES.items() if (x and x == dx) or (z and z == dz)]
    if layer == LAYERS - 1: faces.append('up')
    if layer == 0: faces.append('down')
    return faces


def cell_model(layer, dx, dz):
    """A formed cell: a full block drawing only its outside faces, each cut from the furnace-wide section textures."""
    faces = outside_faces(layer, dx, dz)
    elements = []
    window = layer in VIEWPORT_LAYERS and abs(dx) + abs(dz) == 1
    full = {}
    for face in faces:
        if face in ('up', 'down'):
            full[face] = {'texture': '#lid', 'uv': lid_uv(face, dx, dz), 'cullface': face}
        elif not window:
            texture = section(layer)
            full[face] = {'texture': '#' + texture, 'uv': side_uv(texture, face, layer, dx, dz, 0, 16, 0, 16), 'cullface': face}
    if full: elements.append(element((0, 0, 0), (16, 16, 16), full))
    if window:
        # The sight glass: an opening 6 px across through the lining, framed outside; the coke bed 2 px in is the overlay's.
        face = faces[0]
        for s0, s1, y0, y1 in ((0, 5, 0, 16), (11, 16, 0, 16), (5, 11, 0, 5), (5, 11, 11, 16)):
            lo, hi = box(face, s0, s1, y0, y1, 0, 16)
            elements.append(element(lo, hi, {face: {'texture': '#lining', 'uv': side_uv('lining', face, layer, dx, dz, s0, s1, y0, y1), 'cullface': face}}))
        across = ACROSS[face]
        for s0, s1, y0, y1, toward in ((5, 11, 4, 5, 'up'), (5, 11, 11, 12, 'down'), (4, 5, 5, 11, across), (11, 12, 5, 11, OPPOSITE[across])):
            lo, hi = box(face, s0, s1, y0, y1, 0, 2)
            elements.append(element(lo, hi, {toward: {'texture': '#port', 'uv': [5, 5, 11, 7]}}))
        elements.extend(ring(face, 4, 12, 5, 11, -1, 0))
    if layer == 0 and abs(dx) + abs(dz) == 1:
        elements.extend(ring(faces[0], 3, 13, 5, 11, -1, 0))
    if layer == LAYERS - 1 and dx == dz == 0:
        # The charging hatch: the reaction tower's 22 px lid, raised on the roof, with a handle across it.
        elements.append(element((-3, 16, -3), (19, 17, 19), {
            'up': {'texture': '#plate', 'uv': [0, 0, 16, 16]}, 'north': {'texture': '#port', 'uv': [0, 0, 16, 1]},
            'south': {'texture': '#port', 'uv': [0, 0, 16, 1]}, 'west': {'texture': '#port', 'uv': [0, 0, 16, 1]},
            'east': {'texture': '#port', 'uv': [0, 0, 16, 1]}}))
        handle = {'texture': '#port', 'uv': [0, 0, 8, 2]}
        elements.append(element((4, 17, 7), (12, 18, 9), {name: handle for name in ('up', 'north', 'south', 'west', 'east')}))
    textures = {'lid': NS + 'block/steam_gasifier/lid', 'band': NS + 'block/steam_gasifier/band',
                'lining': NS + 'block/steam_gasifier/lining', 'port': NS + 'block/steam_gasifier/port',
                'plate': NS + 'block/steam_gasifier/plate', 'particle': NS + 'block/steam_gasifier/shell'}
    used = {face['texture'][1:] for part in elements for face in part['faces'].values()}
    return {'parent': 'minecraft:block/block', 'textures': {key: value for key, value in textures.items() if key in used or key == 'particle'},
            'elements': elements}


def ring(face, outer0, outer1, inner0, inner1, d0, d1):
    """A flange standing proud of a face: a square ring between the outer and inner spans."""
    parts = []
    for s0, s1, y0, y1 in ((outer0, inner0, outer0, outer1), (inner1, outer1, outer0, outer1),
                           (inner0, inner1, outer0, inner0), (inner0, inner1, inner1, outer1)):
        lo, hi = box(face, s0, s1, y0, y1, d0, d1)
        tex = {'texture': '#port', 'uv': [s0, 16 - y1, s1, 16 - y0]}
        parts.append(element(lo, hi, {name: dict(tex) for name in ('north', 'south', 'west', 'east', 'up', 'down')}))
    return parts


def outlet_model(face):
    """The gas outlet flange on the roof of the lid band's middle block on the furnace's front."""
    return {'parent': 'minecraft:block/block', 'textures': {'port': NS + 'block/steam_gasifier/port', 'particle': NS + 'block/steam_gasifier/port'},
            'elements': ring_up(3, 13, 5, 11) + [element((5, 15.99, 5), (11, 16.01, 11), {'up': {'texture': '#port', 'uv': [5, 5, 11, 11]}})]}


def ring_up(outer0, outer1, inner0, inner1):
    parts = []
    for x0, x1, z0, z1 in ((outer0, inner0, outer0, outer1), (inner1, outer1, outer0, outer1),
                           (inner0, inner1, outer0, inner0), (inner0, inner1, inner1, outer1)):
        tex = {'texture': '#port', 'uv': [x0, z0, x1, z1]}
        parts.append(element((x0, 16, z0), (x1, 17, z1), {name: dict(tex) for name in ('north', 'south', 'west', 'east', 'up')}))
    return parts


def coke_model(face, hot):
    lo, hi = box(face, 5, 11, 5, 11, 2, 3)
    part = element(lo, hi, {face: {'texture': '#coke', 'uv': [5, 5, 11, 11]}})
    if hot:
        part['shade'] = False
        part['neoforge_data'] = {'block_light': 15, 'sky_light': 15}
    texture = NS + 'block/steam_gasifier/coke_' + ('hot' if hot else 'cold')
    return {'parent': 'minecraft:block/block', 'textures': {'coke': texture, 'particle': texture}, 'elements': [part]}


def assets():
    textures()
    models = A / 'models/block/steam_gasifier'
    write(models / 'loose.json', {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
        'side': NS + 'block/steam_gasifier/shell', 'top': NS + 'block/steam_gasifier/plate', 'bottom': NS + 'block/steam_gasifier/plate'}})
    parts = [{'when': {'cell': '0'}, 'apply': {'model': NS + 'block/steam_gasifier/loose'}}]
    for layer in range(LAYERS):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                index = cell(layer, dx, dz)
                write(models / f'cell_{index}.json', cell_model(layer, dx, dz))
                parts.append({'when': {'cell': str(index)}, 'apply': {'model': NS + f'block/steam_gasifier/cell_{index}'}})
    for face, (dx, dz) in SIDES.items():
        write(models / f'outlet_{face}.json', outlet_model(face))
        parts.append({'when': {'cell': str(cell(LAYERS - 1, dx, dz)), 'facing': face}, 'apply': {'model': NS + f'block/steam_gasifier/outlet_{face}'}})
        for hot in (True, False):
            name = f'coke_{"hot" if hot else "cold"}_{face}'
            write(models / f'{name}.json', coke_model(face, hot))
            parts.append({'when': {'cell': '|'.join(str(cell(layer, dx, dz)) for layer in VIEWPORT_LAYERS), 'lit': str(hot).lower()},
                          'apply': {'model': NS + 'block/steam_gasifier/' + name}})
    write(A / f'blockstates/{BLOCK}.json', {'multipart': parts})
    write(A / f'models/item/{BLOCK}.json', {'parent': NS + 'block/steam_gasifier/loose'})
    write(D / f'loot_table/blocks/{BLOCK}.json', {'type': 'minecraft:block', 'pools': [
        {'rolls': 1, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
         'entries': [{'type': 'minecraft:item', 'name': NS + BLOCK}]}]})
    for name in RETIRED:
        for path in (A / f'blockstates/{name}.json', A / f'models/block/gasifier/{name}.json', A / f'models/item/{name}.json',
                     D / f'loot_table/blocks/{name}.json', D / f'recipe/{name}.json'):
            path.unlink(missing_ok=True)
    for path in (A / 'models/block/gasifier', A / 'textures/block/gasifier'):
        if path.exists():
            for child in path.iterdir(): child.unlink()
            path.rmdir()
    for tag in ('mineable/pickaxe', 'needs_iron_tool'):
        path = ROOT / f'src/main/resources/data/minecraft/tags/block/{tag}.json'
        data = read(path)
        data['values'] = list(dict.fromkeys([value for value in data['values'] if value[len(NS):] not in RETIRED] + [NS + BLOCK]))
        write(path, data)


def recipes():
    write(D / f'recipe/{BLOCK}.json', {'type': 'minecraft:crafting_shaped', 'pattern': ['SSS', 'BBB', 'SSS'],
        'key': {'S': {'item': NS + 'stainless_steel_sheet'}, 'B': {'item': 'minecraft:bricks'}}, 'result': {'id': NS + BLOCK, 'count': 6}})
    write(D / 'tags/fluid/gasifier_steam.json', {'values': [NS + name for name in ('steam', 'high_pressure_steam', 'superheated_steam')]})
    # Carbon powder from before the furnace took lump fuel stays usable.
    write(D / 'tags/item/gasifier_fuels.json', {'values': ['minecraft:coal', 'minecraft:charcoal', NS + 'carbon_powder',
        {'id': '#c:coal_coke', 'required': False}]})
    write(D / 'recipe/entrained_gasification/carbon_steam.json', {
        'type': NS + 'entrained_gasification', 'ingredient': {'tag': NS + 'gasifier_fuels'},
        'steam': {'tag': NS + 'gasifier_steam', 'amount': 250},
        'duration': 40, 'gas_result': {'fluid': NS + 'water_gas', 'amount': 500}})


def lesson():
    scene = ponder.Structure(11, 10)
    for layer in range(LAYERS):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                scene.block(5 + dx, 2 + layer, 5 + dz, NS + BLOCK, cell=str(cell(layer, dx, dz)), facing='south', lit='false')
    scene.block(5, 1, 5, 'create:blaze_burner', blaze='kindled', facing='north')
    scene.block(5, 1, 3, NS + 'stainless_steel_pump', facing='south')
    scene.block(6, 1, 3, 'create:cogwheel', axis='z')
    scene.block(6, 1, 2, 'create:creative_motor', facing='south')
    scene.block(4, 8, 4, 'create:andesite_funnel', facing='up', extracting='false', powered='false')
    pipe = NS + 'stainless_steel_pipe'
    for x, y, z, axis in ((5, 1, 4, 'z'), (3, 2, 5, 'x'), (2, 2, 5, 'x'), (5, 8, 6, 'y')):
        directions = {'x': ('east', 'west'), 'z': ('north', 'south'), 'y': ('up', 'down')}[axis]
        scene.block(x, y, z, pipe, waterlogged='false', **{face: str(face in directions).lower() for face in ponder.DIRECTIONS})
    scene.block(5, 9, 6, NS + 'stainless_steel_pump', facing='up')
    scene.block(6, 9, 6, 'create:cogwheel', axis='y')
    scene.block(6, 8, 6, 'create:creative_motor', facing='up')
    ponder.OUT = A / 'ponder/plant'
    scene.save('entrained_gasifier')


if __name__ == '__main__':
    assets()
    recipes()
    lesson()
