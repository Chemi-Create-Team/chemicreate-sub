#!/usr/bin/env python3
"""One-wide knockout drum: octagonal shell, stepped heads, real port seats and a sight glass.

The two original ammonia_separator plates remain the uranium asset generator's inputs.
The lidded vessel's shared cap sheet is owned by tools/data, independently of this drum.
"""
import math
from statistics import median
from pathlib import Path
from PIL import Image
from generate_plant_tank_assets import A, NS, write

ID = 'ammonia_separator'
ROOT = Path(__file__).resolve().parents[1]
TEXTURES = {'shell': NS + f'block/{ID}/shell', 'cap': NS + f'block/{ID}/cap',
            'frame': NS + f'block/{ID}/frame', 'glass': NS + f'block/{ID}/glass',
            'recess': NS + f'block/{ID}/recess', 'particle': NS + f'block/{ID}/shell'}


def textures():
    source = Image.open(A / f'textures/block/tank/{ID}.png').convert('RGB')
    samples = list(source.crop((3, 3, 13, 13)).getdata())
    base = tuple(int(median(pixel[channel] for pixel in samples)) for channel in range(3))
    palette = lambda d: tuple(max(0, min(255, c + d)) for c in base) + (255,)
    folder = A / f'textures/block/{ID}'
    folder.mkdir(parents=True, exist_ok=True)
    for kind in ('shell', 'cap', 'frame', 'glass', 'recess'):
        image = Image.new('RGBA', (16, 16))
        for y in range(16):
            for x in range(16):
                grain = (x * 3 + y * 7) % 3 - 1
                value = palette(grain)
                if kind == 'shell':
                    if x == 0: value = palette(32)
                    elif x == 15: value = palette(-35)
                    elif x in (1, 14): value = palette(-14)
                    elif x in (2, 13) and y in (2, 13): value = palette(53)
                elif kind == 'cap':
                    if min(x, y, 15-x, 15-y) == 0: value = palette(32 if x == 0 or y == 0 else -35)
                elif kind == 'frame':
                    value = palette(22 if y == 0 or x == 0 else -26 if y == 15 or x == 15 else -8)
                elif kind == 'recess': value = palette(-58)
                elif kind == 'glass':
                    value = (0, 0, 0, 0)
                    if (x, y) in ((7, 3), (7, 8), (7, 13), (8, 5), (8, 10)): value = (201, 227, 231, 255)
                image.putpixel((x, y), value)
        image.save(folder / f'{kind}.png')
    shared = ROOT / 'tools/data/lidded_vessel_cap_connected.png'
    target = A / 'textures/block/lidded_vessel/cap_connected.png'
    target.write_bytes(shared.read_bytes())


def box(lo, hi, texture, faces=('north', 'south', 'east', 'west', 'up', 'down'), angle=0):
    element = {'from': lo, 'to': hi, 'faces': {side: {'texture': '#' + texture} for side in faces}}
    if angle: element['rotation'] = {'origin': [8, 8, 8], 'axis': 'y', 'angle': angle, 'rescale': False}
    return element


def octagon(radius, low, high, texture, caps=True):
    """Four intersecting rectangles have the eight vertices of a regular octagon.

    Only the short ends are outer walls. Subpixel cap offsets avoid coplanar overdraw.
    """
    thin = radius * math.tan(math.pi / 8)
    result = []
    for index, angle in enumerate((0, 90, 45, -45)):
        if angle == 90:
            lo, hi, sides, rotation = [8-thin, low, 8-radius], [8+thin, high, 8+radius], ('north', 'south'), 0
        else:
            lo, hi, sides, rotation = [8-radius, low, 8-thin], [8+radius, high, 8+thin], ('east', 'west'), angle
        result.append(box(lo, hi, texture, sides, rotation))
        if caps: result.append(box([lo[0], low+.0001*index, lo[2]], [hi[0], high-.0001*index, hi[2]], texture, ('up', 'down'), rotation))
    return result


def flange(top):
    low, high = (15, 16) if top else (0, 1)
    result = []
    for a, b, c, d in ((4, 12, 4, 5), (4, 12, 11, 12), (4, 5, 5, 11), (11, 12, 5, 11)):
        result.append(box([a, low, c], [b, high, d], 'frame'))
    low, high = (14, 16) if top else (0, 2)
    for a, b, c, d in ((5, 11, 5, 6), (5, 11, 10, 11), (5, 6, 6, 10), (10, 11, 6, 10)):
        result.append(box([a, low, c], [b, high, d], 'frame'))
    return result


def model(bottom, top):
    low, high = (4 if bottom else 0), (12 if top else 16)
    elements = octagon(7, low, high, 'shell', caps=False)
    elements += octagon(8, low, low+1, 'frame') + octagon(8, high-1, high, 'frame')
    for end, present in ((False, bottom), (True, top)):
        if not present: continue
        for radius, a, b in ((5.5, 12, 13.5), (4, 13.5, 15)):
            elements += octagon(radius, a if end else 16-b, b if end else 16-a, 'cap')
        elements += flange(end)
    if top:
        for lo, hi in (([4, 6, 0], [12, 14, 1]), ([4, 6, 15], [12, 14, 16]), ([0, 6, 4], [1, 14, 12]), ([15, 6, 4], [16, 14, 12])):
            elements.append(box(lo, hi, 'cap'))
    elements += [box([6, 0, 0], [7, 16, 1], 'frame'), box([9, 0, 0], [10, 16, 1], 'frame'),
                 box([7, 0, 0], [9, 1, 1], 'frame'), box([7, 15, 0], [9, 16, 1], 'frame'),
                 box([7, 1, .94], [9, 15, 1], 'recess'), box([7, 1, 0], [9, 15, .94], 'glass')]
    return {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout_mipped', 'textures': TEXTURES, 'elements': elements}


def generate():
    folder = A / f'models/block/{ID}'
    for path in folder.glob('*.json'): path.unlink()
    variants = {}
    for bottom, top, name in ((True, True, 'single'), (True, False, 'bottom'), (False, False, 'middle'), (False, True, 'top')):
        write(folder / f'{name}.json', model(bottom, top))
        for direction, angle in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
            variants[f'bottom={str(bottom).lower()},top={str(top).lower()},facing={direction}'] = {'model': NS + f'block/{ID}/{name}', 'y': angle}
    write(A / f'blockstates/{ID}.json', {'variants': variants})
    write(A / f'models/item/{ID}.json', {'parent': NS + f'block/{ID}/single'})
    for suffix in ('_inner', '_window', '_window_single', '_connected', '_top_connected', '_inner_connected'):
        (A / f'textures/block/tank/{ID}{suffix}.png').unlink(missing_ok=True)
    textures()
    print('Generated 1x1 separator drum: four end models, four fronts and sight glass')


if __name__ == '__main__': generate()
