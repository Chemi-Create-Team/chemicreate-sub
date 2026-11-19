#!/usr/bin/env python3
"""Build the recovery duct's casing and the model-scale recovery line."""
import json
from math import cos, exp, pi, sin, tau
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


def mist_texture():
    width, height = 64, 32
    image = Image.new('RGBA', (width, height))
    random = Random(83)
    waves = [(fx, fy, random.uniform(0, tau), weight) for fx, fy, weight in
             ((2, 1, .18), (3, 2, .12), (5, -2, .09), (7, 4, .06), (11, -5, .035))]
    for y in range(height):
        v = y / (height - 1)
        for x in range(width):
            u = x / (width - 1)
            center = .54 + .1 * sin(tau * u + .5) + .038 * sin(2 * tau * u)
            main = exp(-((v - center) / (.18 + .05 * sin(pi * u))) ** 2)
            trailing = .38 * exp(-((v - center + .19) / .13) ** 2)
            detail = .69 + sum(cos(tau * (u * fx + v * fy) + phase) * weight for fx, fy, phase, weight in waves)
            envelope = sin(pi * u) ** 1.5 * sin(pi * v)
            alpha = round(255 * envelope * min(1, (main + trailing) * detail))
            image.putpixel((x, y), (255, 255, 255, alpha))
    path = ASSETS / 'textures/entity/gas_mist.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


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
    mist_texture()
    plume_texture()
    storyboard()
