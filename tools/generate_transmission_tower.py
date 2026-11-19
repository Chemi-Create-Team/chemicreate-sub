#!/usr/bin/env python3
"""Build the 24-block single-block lattice tower, variants and teaching scene."""
import json
from pathlib import Path
from random import Random
from PIL import Image, ImageDraw
import generate_petrochemical_ponder as ponder

ROOT = Path(__file__).resolve().parents[1]
NS = 'create_refinery_n_reactors'
ASSETS = ROOT / 'src/main/resources/assets' / NS
DATA = ROOT / 'src/main/resources/data' / NS
NAME = 'transmission_tower'


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def model():
    beams = []

    def beam(a, b, width, material='zinc', depth=None):
        value = {'from': list(a), 'to': list(b), 'width': width, 'texture': material}
        if depth is not None:
            value['depth'] = depth
        beams.append(value)

    def box(x, bottom, z, width, height, depth, material):
        beam((x, bottom, z), (x, bottom + height, z), width, material, depth)

    def ring(height, radius):
        corners = [(0.5 + x * radius, height, 0.5 + z * radius)
                   for x, z in [(-1, -1), (1, -1), (1, 1), (-1, 1)]]
        return corners, zip(corners, corners[1:] + corners[:1])

    bands = [(0.25, 2.27), (1.0, 2.25), (3.4, 2.03), (6.0, 1.69), (8.7, 1.24),
             (11.4, .84), (14.0, .50), (16.6, .50), (19.1, .50), (21.6, .50), (23.35, .50)]
    previous = None
    for i, (height, radius) in enumerate(bands):
        corners, edges = ring(height, radius)
        for a, b in edges:
            beam(a, b, .10 if i > 6 else .13, 'zinc')
        if previous:
            for j in range(4):
                k = (j + 1) % 4
                beam(previous[j], corners[j], .17 if i < 7 else .128)
                beam(previous[j], corners[k], .085 if i < 7 else .065, 'braces')
                if i > 1:
                    beam(previous[k], corners[j], .072 if i < 7 else .056, 'braces')
        previous = corners
    for x in (-1, 1):
        for z in (-1, 1):
            box(.5 + x * 2.25, 0, .5 + z * 2.25, .50, .28, .50, 'concrete')
            box(.5 + x * 2.25, .28, .5 + z * 2.25, .32, .10, .32, 'dark')
    for z in (-1, 1):
        beam((-1.48, 6, .5 + z * 1.94), (2.48, 6, .5 + z * 1.94), .12, 'dark', .16)
        beam((-1.44, 6.17, .5 + z * 1.86), (2.44, 6.17, .5 + z * 1.86), .12, 'zinc')
        beam((-1.42, 6.03, .5 + z * 1.86), (2.42, 6.03, .5 + z * 1.86), .15, 'grate', .22)
    for x in (-1, 1):
        beam((.5 + x * 1.94, 6, -1.48), (.5 + x * 1.94, 6, 2.48), .12, 'dark', .17)
        for z in (-1, 1):
            beam((.5 + x * 1.83, 6.03, .5 + z * 1.83), (.5 + x * 1.83, 6.69, .5 + z * 1.83), .055, 'braces')
        beam((.5 + x * 1.83, 6.69, -1.33), (.5 + x * 1.83, 6.69, 2.33), .06, 'braces')
    beam((-.23, 6.58, -1.34), (1.23, 6.58, -1.34), .085, 'warning', .49)

    for height, span in ((17, 3.42), (20, 4.42), (23, 3.42)):
        for side in (-1, 1):
            outer = .5 + span * side
            join = .5 + .48 * side
            for z in (.10, .90):
                tip = (outer, height, .5)
                beam((join, height, z), tip, .12, 'zinc')
                beam((join, min(23.95, height + 1.14), z), tip, .10, 'zinc')
                for ratio in (.24, .51, .77):
                    x = join + (outer - join) * ratio
                    y = height + (min(23.95, height + 1.14) - height) * (1 - ratio)
                    inner_z = z + (.5 - z) * ratio
                    beam((x, height, inner_z), (x, y, inner_z), .069, 'braces')
                    beam((x, y, inner_z), (x + (outer - join) * .20, height, inner_z + (.5 - z) * .20), .051, 'braces')
            beam((outer, height - .10, .5), (outer, height, .5), .17, 'dark')
            beam((outer, height - 1.18, .5), (outer, height - .10, .5), .085, 'dark')
            for rib in range(7):
                box(outer, height - .98 + rib * .111, .5, .218 + (.031 if rib % 2 == 0 else 0), .036,
                    .218 + (.031 if rib % 2 == 0 else 0), 'ceramic')
            box(outer, height - 1.19, .5, .17, .11, .17, 'dark')

    for x, z in [(-1, -1), (1, -1), (1, 1), (-1, 1)]:
        beam((.5 + x * .5, 23.35, .5 + z * .5), (.5, 23.96, .5), .081, 'zinc')
    box(.5, 23.88, .5, .14, .12, .14, 'dark')

    box(.5, 0, .5, 1, .15, 1, 'concrete')
    box(.5, .15, .5, .92, .62, .92, 'dark')
    box(.5, .77, .5, 1, .105, 1, 'zinc')
    for row, z in enumerate((.265, .735)):
        for col, x in enumerate((.17, .50, .83)):
            box(x, .876, z, .265, .027, .37, f'terminal_{row * 3 + col + 1}')

    textures = {
        'particle': f'{NS}:block/transmission_tower/zinc',
        'zinc': f'{NS}:block/transmission_tower/zinc',
        'braces': f'{NS}:block/transmission_tower/braces',
        'dark': f'{NS}:block/transmission_tower/fittings',
        'ceramic': f'{NS}:block/transmission_tower/ceramic',
        'concrete': 'minecraft:block/smooth_stone',
        'grate': f'{NS}:block/transmission_tower/grate',
        'warning': f'{NS}:block/transmission_tower/warning',
        **{f'terminal_{n}': f'{NS}:block/transmission_tower/terminal_{n}' for n in range(1, 7)},
    }
    body = {'loader': f'{NS}:lattice_beams', 'ambientocclusion': False, 'textures': textures, 'beams': beams}
    save(ASSETS / f'models/block/{NAME}.json', body)
    # The inventory preview fits a 16-pixel item cell and uses the same silhouette with readable bracing.
    mini = []
    for beam_data in beams:
        value = {**beam_data}
        for point in ('from', 'to'):
            x, y, z = value[point]
            value[point] = [round(.5 + (x - .5) / 20, 5), round(.02 + y / 24, 5), round(.5 + (z - .5) / 20, 5)]
        value['width'] = max(.013, value['width'] / 20)
        value['depth'] = max(.013, value.get('depth', beam_data['width']) / 20)
        mini.append(value)
    save(ASSETS / f'models/item/{NAME}.json', {**body, 'parent': 'minecraft:block/block', 'beams': mini})
    variants = {f'facing={facing}': {'model': f'{NS}:block/{NAME}', **({'y': angle} if angle else {})}
                for facing, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]}
    save(ASSETS / f'blockstates/{NAME}.json', {'variants': variants})


def mottled_tile(color, seed, contrast):
    rng = Random(seed)
    patches = [[rng.choice((-2, -1, 0, 0, 1, 2)) for _ in range(4)] for _ in range(6)]
    bands = [rng.choice((-1, 0, 0, 1)) for _ in range(8)]
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            patch = patches[(y + (x // 4) % 2) // 3][x // 4]
            grain = rng.choice((-1, 0, 0, 0, 1))
            shift = (patch + bands[y // 2]) * contrast + grain * max(2, contrast // 2)
            image.putpixel((x, y), tuple(max(0, min(255, channel + shift)) for channel in color))
    return image


def cable(path):
    image = mottled_tile((51, 58, 62), seed=83, contrast=6)
    for y in range(16):
        for x in range(16):
            strand = (x + y // 2) % 7
            relief = 13 if strand < 2 else -10 if strand == 6 else 0
            image.putpixel((x, y), tuple(max(0, value + relief) for value in image.getpixel((x, y))))
    image.save(path / 'cable.png')

    # A half-block segment is the unit used by Electro Energetics' cable renderer.
    texture = f'{NS}:block/transmission_tower/cable'
    faces = {
        'north': {'uv': [0, 12, 3, 15]},
        'south': {'uv': [0, 12, 3, 15]},
        'east': {'uv': [0, 1, 16, 4]},
        'west': {'uv': [16, 5, 0, 8]},
        'up': {'uv': [0, 8, 16, 11], 'rotation': 90},
        'down': {'uv': [16, 12, 0, 15], 'rotation': 90},
    }
    for face in faces.values():
        face['texture'] = '#cable'
    save(ASSETS / 'models/block/transmission_tower/cable_segment.json', {
        'textures': {'cable': texture, 'particle': texture},
        'elements': [{'from': [-1.5, -1.5, 0], 'to': [1.5, 1.5, 8], 'faces': faces}],
    })


def textures():
    path = ASSETS / 'textures/block/transmission_tower'
    path.mkdir(parents=True, exist_ok=True)
    surfaces = (
        ('zinc', (118, 125, 128), 11),
        ('braces', (83, 92, 98), 9),
        ('ceramic', (54, 67, 65), 7),
        ('grate', (94, 101, 103), 9),
        ('fittings', (62, 70, 75), 8),
    )
    for seed, (name, color, contrast) in enumerate(surfaces, 44):
        image = mottled_tile(color, seed, contrast)
        for y in range(16):
            for x in range(16):
                relief = 17 if x < 2 else -15 if x > 13 else 0
                if name == 'grate' and x % 4 == 1:
                    relief -= 28
                if name == 'ceramic' and y in (3, 7, 12):
                    relief += 20
                image.putpixel((x, y), tuple(max(0, min(255, value + relief)) for value in image.getpixel((x, y))))
        image.save(path / f'{name}.png')

    cable(path)

    warning = mottled_tile((210, 157, 48), seed=24, contrast=5)
    draw = ImageDraw.Draw(warning)
    draw.rectangle((0, 0, 15, 15), outline='#40392b')
    draw.polygon([(9, 2), (5, 8), (8, 8), (6, 13), (12, 6), (9, 6)], fill='#433522')
    draw.line((2, 13, 13, 13), fill='#f3c95a')
    warning.save(path / 'warning.png')
    glyphs = ('010110010010111', '110001010100111', '110001010001110',
              '101101111001001', '111100110001110', '011100110101010')
    for n, glyph in enumerate(glyphs, 1):
        image = mottled_tile((39, 56, 69) if n <= 3 else (103, 80, 49), seed=n, contrast=4)
        draw = ImageDraw.Draw(image)
        for i, pixel in enumerate(glyph):
            if pixel == '1':
                x, y = i % 3 * 3 + 4, i // 3 * 2 + 3
                draw.rectangle((x, y, x + 2, y + 1), fill='#e8d7a4')
        image.save(path / f'terminal_{n}.png')

    items = ASSETS / 'textures/item'
    for name in ('high_voltage_cable', 'high_voltage_conductor'):
        image = Image.new('RGBA', (16, 16))
        draw = ImageDraw.Draw(image)
        draw.ellipse((1, 2, 14, 13), fill='#252e36', outline='#1a2027')
        draw.ellipse((3, 3, 12, 11), fill='#647685', outline='#95a5ac')
        draw.ellipse((5, 5, 10, 10), fill='#273039')
        if name == 'high_voltage_cable':
            draw.polygon([(5, 3), (7, 1), (13, 4), (11, 6), (5, 6)], fill='#c6ad6c', outline='#60492e')
            draw.line((2, 12, 12, 12), fill='#a88a50', width=2)
        else:
            draw.line((11, 10, 13, 13, 15, 14), fill='#aabcca', width=2)
        image.save(items / f'{name}.png')
        save(ASSETS / f'models/item/{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{name}'}})


def resources():
    def craft(name, pattern, key, count=1):
        save(DATA / f'recipe/{name}.json', {
            'type': 'minecraft:crafting_shaped', 'pattern': pattern, 'key': key,
            'result': {'id': f'{NS}:{name}', 'count': count},
        })
    craft(NAME, ['SIS', 'CRC', 'BSB'],
          {'S': {'tag': 'c:plates/stainless_steel'}, 'I': {'item': 'electroenergetics:glass_insulator_segment'},
           'C': {'item': 'minecraft:iron_bars'}, 'R': {'item': 'create:precision_mechanism'},
           'B': {'item': 'minecraft:smooth_stone'}})
    craft('high_voltage_conductor', ['CAC', ' R '],
          {'C': {'item': 'electroenergetics:heavily_insulated_wire'}, 'A': {'tag': 'c:ingots/aluminum'},
           'R': {'tag': f'{NS}:industrial_gaskets'}}, 4)
    craft('high_voltage_cable', [' C ', 'CSC', ' C '],
          {'C': {'item': f'{NS}:high_voltage_conductor'}, 'S': {'item': 'electroenergetics:empty_spool'}})
    save(DATA / f'loot_table/blocks/{NAME}.json', {
        'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{NAME}'}],
                                           'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
    })


def lesson():
    scene = ponder.Structure(19, 27)
    for z in (4, 14):
        scene.block(9, 1, z, f'{NS}:{NAME}', facing='north')
    ponder.OUT = ASSETS / 'ponder/plant'
    scene.save(NAME)


if __name__ == '__main__':
    model()
    textures()
    resources()
    lesson()
