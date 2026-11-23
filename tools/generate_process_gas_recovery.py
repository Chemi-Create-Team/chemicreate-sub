#!/usr/bin/env python3
"""Build the recovery duct's casing and the model-scale recovery line."""
import json
from math import cos, exp, sin
from random import Random

from PIL import Image, ImageDraw

import generate_petrochemical_ponder as ponder
from generate_industrial_gas_safety import ASSETS, DATA, id_for, write
from generate_world_liquids import bucket_image

DUCT = 'gas_recovery_duct'
SCENE = 'process_gas_recovery'


def plate():
    image = Image.new('RGB', (16, 16))
    noise = Random(29)
    for y in range(16):
        for x in range(16):
            shade = noise.randrange(-5, 6)
            image.putpixel((x, y), tuple(channel + shade for channel in (74, 88, 91)))
    draw = ImageDraw.Draw(image)
    draw.line((0, 15, 0, 0, 15, 0), fill='#94a6a5')
    draw.line((1, 15, 15, 15, 15, 1), fill='#283235')
    for x in (2, 13):
        for y in (2, 13):
            draw.point((x, y), fill='#b3c2bf')
            draw.point((x, y + 1), fill='#323e40')
    return image, draw


def save_texture(image, face):
    image.save(ASSETS / f'textures/block/{DUCT}_{face}.png')


def duct_assets():
    image, draw = plate()
    draw.rectangle((1, 6, 14, 9), fill='#364449')
    draw.line((2, 6, 13, 6), fill='#a09a79')
    draw.line((2, 9, 13, 9), fill='#212e31')
    save_texture(image, 'side')

    image, draw = plate()
    draw.ellipse((2, 2, 13, 13), fill='#837d69', outline='#c3b89b')
    draw.ellipse((4, 4, 11, 11), fill='#202b2e', outline='#444748')
    save_texture(image, 'top')

    image, draw = plate()
    draw.polygon([(6, 2), (9, 2), (13, 6), (13, 9), (9, 13), (6, 13), (2, 9), (2, 6)],
                 fill='#c48e59', outline='#69543e')
    draw.ellipse((4, 4, 11, 11), fill='#392d26', outline='#e4b17a')
    draw.ellipse((6, 6, 9, 9), fill='#1b2326')
    save_texture(image, 'outlet')

    for active, lamp in ((False, '#4c625b'), (True, '#a5e6a6')):
        image, draw = plate()
        draw.rectangle((2, 4, 13, 11), fill='#212d30', outline='#9caeab')
        for x in range(4, 13, 2):
            draw.line((x, 6, x, 10), fill='#607778')
            draw.point((x, 6), fill='#aabcb7')
        draw.line((6, 13, 9, 13), fill=lamp)
        state = 'on' if active else 'off'
        save_texture(image, f'intake_{state}')
        faces = {'north': 'outlet', 'south': f'intake_{state}', 'east': 'side',
                 'west': 'side', 'up': 'top', 'down': 'side', 'particle': 'side'}
        write(ASSETS / f'models/block/{DUCT}_{state}.json', {
            'parent': 'minecraft:block/cube',
            'textures': {key: id_for(f'block/{DUCT}_{face}') for key, face in faces.items()},
        })
    write(ASSETS / f'blockstates/{DUCT}.json', {'variants': {
        f'active={str(active).lower()},facing={facing}': {
            'model': id_for(f'block/{DUCT}_{"on" if active else "off"}'), **({'y': turn} if turn else {}),
        }
        for active in (False, True)
        for facing, turn in (('north', 0), ('east', 90), ('south', 180), ('west', 270))
    }})
    write(ASSETS / f'models/item/{DUCT}.json', {'parent': id_for(f'block/{DUCT}_off')})


def duct_data():
    write(DATA / f'recipe/{DUCT}.json', {
        'type': 'minecraft:crafting_shaped', 'category': 'equipment', 'pattern': [' I ', 'BCP', ' I '],
        'key': {'I': {'item': 'create:iron_sheet'}, 'B': {'item': 'minecraft:iron_bars'},
                'C': {'item': 'create:andesite_casing'}, 'P': {'item': 'create:fluid_pipe'}},
        'result': {'id': id_for(DUCT), 'count': 1},
    })
    loot = json.loads((DATA / 'loot_table/blocks/relief_scrubber.json').read_text())
    loot['pools'][0]['entries'][0]['name'] = id_for(DUCT)
    write(DATA / f'loot_table/blocks/{DUCT}.json', loot)
    tag_path = DATA.parent / 'minecraft/tags/block/mineable/pickaxe.json'
    tag = json.loads(tag_path.read_text())
    if id_for(DUCT) not in tag['values']:
        tag['values'].append(id_for(DUCT))
        write(tag_path, tag)


def plume_texture():
    size = 32
    image = Image.new('RGBA', (size, size))
    lobes = ((-.24, .08, .5, .69), (.28, -.05, .49, .63), (-.31, -.35, .42, .58),
             (.16, .42, .40, .5), (.48, -.38, .33, .36), (-.43, .38, .36, .42))
    for y in range(size):
        v = 2 * y / (size - 1) - 1
        for x in range(size):
            u = 2 * x / (size - 1) - 1
            cloud = sum(weight * exp(-((u - dx) ** 2 + (v - dy) ** 2) / width ** 2) for dx, dy, width, weight in lobes)
            texture = .92 + .08 * cos(10 * u + 7 * v) + .065 * sin(15 * u - 11 * v)
            edge = max(0, 1 - u * u - v * v)
            alpha = round(235 * min(1, cloud * texture) * edge ** .55)
            image.putpixel((x, y), (255, 255, 255, alpha))
    image.save(ASSETS / 'textures/entity/gas_plume.png')


def mist_texture():
    """The ground mist's density pattern, baked from the same value noise the old ground shader evaluated per pixel, so it looks the same with or without a shader pack.

    One tile covers 32 blocks and wraps. Alpha is the pattern's density factor (never below 0.1, which the entity program discards), and the grey level follows it.
    """
    import numpy as np
    size, y = 256, 3.17
    u = (np.arange(size) + .5) / size
    gx, gz = np.meshgrid(u, u)

    def hash3(x, yy, z):
        px, py, pz = (np.mod(v * k, 1) for v, k in ((x, .1031), (yy, .1030), (z, .0973)))
        d = px * (py + 33.33) + py * (pz + 33.33) + pz * (px + 33.33)
        px, py, pz = px + d, py + d, pz + d
        return np.mod((px + py) * pz, 1)

    def noise(x, yy, z, period):
        ix, iy, iz = np.floor(x), np.floor(yy), np.floor(z)
        fx, fy, fz = (v - np.floor(v) for v in (x, yy, z))
        fx, fy, fz = (f * f * (3 - 2 * f) for f in (fx, fy, fz))
        lx, hx = np.mod(ix, period[0]), np.mod(ix + 1, period[0])
        lz, hz = np.mod(iz, period[1]), np.mod(iz + 1, period[1])
        def plane(yo):
            a = hash3(lx, iy + yo, lz) * (1 - fx) + hash3(hx, iy + yo, lz) * fx
            b = hash3(lx, iy + yo, hz) * (1 - fx) + hash3(hx, iy + yo, hz) * fx
            return a * (1 - fz) + b * fz
        return plane(0) * (1 - fy) + plane(1) * fy

    px, pz = gx * 21, gz * 21
    drift = noise(px * .52, y * .52, pz * .52, (11, 11))
    px, pz, py = px + (drift - .5) * .64, pz + (drift - .5) * .50, y + (drift - .5) * .24
    broad = noise(px, py, pz, (21, 21))
    fine = noise(px * 49 / 21 + 4, py * 2.35 + 2, pz * 49 / 21 + 7, (49, 49))
    folds = broad * .67 + fine * .33
    t = np.clip((folds - .2) / .58, 0, 1)
    density = .12 + 1.8 * t * t * (3 - 2 * t)
    alpha = np.clip(density / 1.92, .11, 1)
    grey = np.clip(.81 + .21 * folds, 0, 1)
    pixels = np.dstack([np.round(grey * 255)] * 3 + [np.round(alpha * 255)]).astype(np.uint8)
    Image.fromarray(pixels, 'RGBA').save(ASSETS / 'textures/entity/gas_mist.png')


def storyboard():
    scene = ponder.Structure(11, 5)
    for x in range(1, 6):
        for y in range(1, 5):
            for z in range(3, 8):
                if (x, y, z) in ((5, 1, 4), (5, 1, 5), (5, 2, 5), (5, 3, 5), (5, 1, 6), (3, 1, 5), (3, 2, 5)):
                    continue
                if x in (1, 3, 5) or z in (3, 7) or y == 4:
                    scene.block(x, y, z, 'minecraft:glass')
    for y, half in ((1, 'lower'), (2, 'upper')):
        scene.block(3, y, 5, 'minecraft:oak_door', facing='east', half=half, hinge='left', open='false', powered='false')
    for y in (2, 3):
        scene.block(2, y, 5, 'create:fluid_pipe', up='true', down='true')
    scene.block(5, 1, 4, id_for('gas_detector'), power='0')
    scene.block(6, 1, 4, 'minecraft:comparator', facing='west', mode='compare', powered='false')
    scene.block(7, 1, 4, 'minecraft:redstone_lamp', lit='false')
    scene.block(5, 1, 5, id_for(DUCT), facing='east', active='false')
    scene.block(5, 2, 5, 'create:encased_fan', facing='down')
    scene.block(5, 3, 5, 'create:creative_motor', facing='down')
    scene.pump(6, 1, 5, 'east')
    scene.block(6, 1, 6, 'create:cogwheel', axis='x', waterlogged='false')
    scene.block(5, 1, 6, 'create:creative_motor', facing='east')
    scene.block(7, 1, 5, 'create:fluid_pipe', east='true', west='true')
    scene.block(8, 1, 5, id_for('gas_receiver'), axis='x', bottom='true', top='true', shape='plain')
    ponder.OUT = ASSETS / 'ponder/plant'
    scene.save(SCENE)

    path = ASSETS / 'ponder/tutorials.json'
    guides = json.loads(path.read_text())
    steps = [
        {'target': [2, 2, 5], 'key': 'step_1'},
        {'target': [3, 1, 5], 'key': 'step_2'},
        {'target': [3, 1, 5], 'key': 'step_3'},
        {'target': [7, 1, 4], 'key': 'step_4'},
        {'target': [5, 2, 5], 'key': 'step_5', 'speed': 64},
        {'target': [2, 4, 5], 'key': 'step_6'},
    ]
    guides = [guide for guide in guides if guide['id'] != SCENE]
    guides.append({'id': SCENE, 'size': 11, 'height': 5, 'scale': .67,
                   'components': [DUCT, 'gas_detector'], 'steps': steps})
    write(path, guides)


if __name__ == '__main__':
    duct_assets()
    duct_data()
    bucket_image('raw_process_gas_bucket', 0xFFBFCAD0)
    plume_texture()
    mist_texture()
    storyboard()
