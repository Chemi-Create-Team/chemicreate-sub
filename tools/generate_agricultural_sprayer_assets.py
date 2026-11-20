#!/usr/bin/env python3
"""Block models, 16 px textures and native Create farm demonstration structures."""
from PIL import Image, ImageDraw

import generate_petrochemical_ponder as ponder
from generate_world_liquids import ASSETS, NS, bucket_image, fluid_entries, write


def metal(name, colors, detail):
    deep, edge, light, plate, seam = colors
    image = Image.new('RGB', (16, 16), deep)
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 14, 14), fill=edge)
    draw.line((1, 1, 13, 1), fill=light)
    draw.line((1, 2, 1, 13), fill=light)
    draw.rectangle((2, 2, 13, 13), fill=plate)
    draw.rectangle((2, 12, 13, 13), fill=seam)
    for left, top, right, bottom in detail:
        draw.rectangle((left, top, right, bottom), fill=edge)
    image.save(ASSETS / 'textures/block' / (name + '.png'))


def casing_panel(draw, x, y, width, height):
    right, bottom = x + width - 1, y + height - 1
    draw.rectangle((x, y, right, bottom), fill='#626762')
    draw.line((x, bottom, right, bottom), fill='#494e4b')
    draw.line((right, y, right, bottom), fill='#494e4b')
    draw.line((x+1, y+1, right-1, y+1), fill='#a1aba3')
    draw.line((x+1, y+2, x+1, bottom-1), fill='#909a92')
    draw.line((right-1, y+2, right-1, bottom-1), fill='#747d76')
    draw.line((x+2, bottom-1, right-1, bottom-1), fill='#747d76')

    left, top = x + 2, y + 2
    draw.rectangle((left, top, right-2, bottom-2), fill='#725434')
    seam = left + (right - left - 1) // 2
    draw.rectangle((seam+1, top, right-2, bottom-2), fill='#815e37')
    draw.line((seam, top, seam, bottom-2), fill='#5f4528')
    draw.line((left, top, right-2, top), fill='#60492d')
    draw.line((left+1, top+1, left+1, bottom-2), fill='#89663e')
    draw.line((right-2, top+2, right-2, bottom-2), fill='#735433')
    draw.point((left, bottom-2), fill='#58452b')


def casing_texture():
    image = Image.new('RGB', (16, 16), '#494e4b')
    draw = ImageDraw.Draw(image)
    # The side's bottom edge is also the first row of the top cap's frame.
    casing_panel(draw, 0, 7, 12, 9)
    casing_panel(draw, 0, 0, 10, 8)
    image.save(ASSETS / 'textures/block/sprayer_casing.png')


def cuboid(lo, hi, material):
    x, y, z = lo
    xx, yy, zz = hi
    uv = {'north': [16-xx, 16-yy, 16-x, 16-y], 'south': [x, 16-yy, xx, 16-y],
          'up': [x, z, xx, zz], 'down': [x, 16-zz, xx, 16-z],
          'east': [16-zz, 16-yy, 16-z, 16-y], 'west': [z, 16-yy, zz, 16-y]}
    return {'from': lo, 'to': hi, 'faces': {side: {'uv': bounds, 'texture': '#' + material} for side, bounds in uv.items()}}


def models():
    body = cuboid([3, 4, 4], [13, 12, 13], 'iron')
    for side in ('north', 'east', 'south', 'west'):
        body['faces'][side] = {'uv': [0, 0, 10, 8], 'texture': '#casing'}
    cap = cuboid([2, 12, 5], [14, 13, 14], 'iron')
    cap['faces']['up'] = {'uv': [0, 7, 12, 16], 'texture': '#casing'}
    elements = [
        cuboid([2, 2, 5], [14, 4, 14], 'iron'),
        body,
        cap,
        cuboid([3, 5, 3], [13, 11, 5], 'brass'),
        cuboid([4, 5, 2], [7, 10, 3], 'brass'),
        cuboid([9, 5, 2], [12, 10, 3], 'brass'),
        cuboid([4, 6, 1], [7, 9, 2], 'brass'),
        cuboid([9, 6, 1], [12, 9, 2], 'brass'),
        cuboid([5, 7, 0.95], [6, 8, 1], 'port'),
        cuboid([10, 7, 0.95], [11, 8, 1], 'port'),
        cuboid([5, 5, 13], [11, 11, 16], 'brass'),
        cuboid([6, 6, 15.5], [10, 10, 16], 'port'),
    ]
    name = 'agricultural_sprayer'
    write(ASSETS / f'models/block/{name}.json', {
        'parent': 'minecraft:block/block',
        'textures': {'particle': NS+'block/sprayer_casing', 'casing': NS+'block/sprayer_casing', 'iron': NS+'block/sprayer_iron',
                     'brass': NS+'block/sprayer_brass', 'port': NS+'block/sprayer_port'},
        'elements': elements,
    })
    write(ASSETS / f'models/item/{name}.json', {'parent': NS+'block/'+name})
    write(ASSETS / f'blockstates/{name}.json', {'variants': {
        'facing='+side: {'model': NS+'block/'+name, **({'y': angle} if angle else {})}
        for side, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]
    }})
    for row in fluid_entries():
        if row['name'] not in ('crop_protectant', 'diluted_pesticide'):
            continue
        bucket_image(row['name']+'_bucket', row['color'])
        write(ASSETS / f"blockstates/{row['name']}.json", {'variants': {'': {'model': 'minecraft:block/water'}}})


def crop(scene, x, z, y=3):
    if y > 2:
        scene.block(x, y-2, z, 'minecraft:dirt')
    scene.block(x, y-1, z, 'minecraft:farmland', moisture='7')
    scene.block(x, y, z, 'minecraft:wheat', age='0')


def scenes():
    ponder.OUT = ASSETS / 'ponder/plant'
    bearing = ponder.Structure(11, 6)
    bearing.block(4, 1, 4, 'create:creative_motor', facing='up')
    bearing.block(4, 2, 4, 'create:mechanical_bearing', facing='up')
    bearing.block(4, 3, 4, 'create:andesite_casing')
    bearing.block(5, 3, 4, 'create:andesite_casing')
    bearing.block(5, 4, 4, 'create:fluid_tank')
    bearing.block(6, 3, 4, NS+'agricultural_sprayer', facing='east')
    for point in [(7,4),(8,4),(9,4),(7,5),(8,5),(7,6),(8,6),(6,7),(5,7),(4,7),(4,8),(4,9),
                  (7,3),(8,3),(7,2),(8,2),(6,1),(5,1),(4,1),(4,2),(5,2)]:
        crop(bearing, *point)
    bearing.save('agricultural_sprayer_bearing')

    gantry = ponder.Structure(11, 6)
    gantry.block(1, 2, 7, 'create:creative_motor', facing='east')
    for x in range(2, 9):
        gantry.block(x, 2, 7, 'create:gantry_shaft', facing='east', powered='false',
                     part='start' if x == 2 else 'end' if x == 8 else 'middle')
    gantry.block(2, 3, 7, 'create:gantry_carriage', facing='up', axis_along_first='true')
    gantry.block(2, 3, 6, 'create:andesite_casing')
    gantry.block(2, 4, 6, 'create:fluid_tank')
    gantry.block(2, 3, 5, NS+'agricultural_sprayer', facing='north')
    for x in range(2, 9):
        for z in range(2, 5):
            crop(gantry, x, z)
    gantry.save('agricultural_sprayer_gantry')

    stationary = ponder.Structure(9, 4)
    stationary.block(4, 2, 4, NS+'agricultural_sprayer', facing='north')
    stationary.block(4, 2, 5, 'create:mechanical_pump', facing='north', waterlogged='false')
    stationary.block(4, 2, 6, 'create:fluid_tank')
    stationary.block(3, 2, 5, 'create:cogwheel', axis='z', waterlogged='false')
    stationary.block(3, 2, 6, 'create:creative_motor', facing='north')
    stationary.block(5, 2, 4, 'minecraft:lever', face='floor', facing='east', powered='false')
    stationary.block(5, 1, 4, 'minecraft:smooth_stone')
    stationary.block(3, 1, 2, 'minecraft:water', level='0')
    for z in range(1, 4):
        crop(stationary, 4, z, 2)
    stationary.save('agricultural_sprayer_stationary')


if __name__ == '__main__':
    casing_texture()
    metal('sprayer_iron', ('#494e4b', '#626762', '#9aa59d', '#828b84', '#626b64'),
          [(3, 4, 12, 4), (3, 12, 12, 12)])
    metal('sprayer_brass', ('#48361e', '#81633b', '#c7a575', '#a08050', '#705533'),
          [(3, 4, 12, 4), (3, 10, 12, 10), (4, 6, 5, 8), (10, 6, 11, 8)])
    Image.new('RGB', (16, 16), '#29282a').save(ASSETS / 'textures/block/sprayer_port.png')
    models()
    scenes()
