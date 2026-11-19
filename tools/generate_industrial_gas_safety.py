#!/usr/bin/env python3
"""Generate the industrial gas monitoring and recovery resources."""
import json
from pathlib import Path

from PIL import Image, ImageDraw

import generate_petrochemical_ponder as ponder

ROOT = Path(__file__).resolve().parents[1]
MOD_ID = 'create_refinery_n_reactors'
ASSETS = ROOT / 'src/main/resources/assets' / MOD_ID
DATA = ROOT / 'src/main/resources/data' / MOD_ID
DETECTOR = 'gas_detector'
FILTER = 'gas_mask_filter'
CYLINDER = 'inert_gas_cylinder'
LIQUOR = 'sulfur_scrubber_liquor'
SCENE = 'industrial_gas_monitor'


def id_for(name):
    return f'{MOD_ID}:{name}'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def generated_item(name, texture=None):
    write(ASSETS / f'models/item/{name}.json', {
        'parent': 'minecraft:item/generated',
        'textures': {'layer0': id_for(f'item/{texture or name}')},
    })


def detector_assets():
    for suffix, colour in (('off', '#395550'), ('on', '#f09b41')):
        image = Image.new('RGBA', (16, 16), '#333b44')
        draw = ImageDraw.Draw(image)
        draw.rectangle((1, 1, 14, 14), fill='#59646d', outline='#84909a')
        draw.rectangle((2, 2, 13, 7), fill='#273138', outline='#39474d')
        draw.rectangle((4, 4, 11, 5), fill=colour)
        for y in (9, 11, 13):
            draw.line((4, y, 11, y), fill='#28343a')
            draw.point((12, y), fill='#83939a')
        for x in (2, 13):
            draw.point((x, 8), fill='#d0a873')
        image.save(ASSETS / f'textures/block/{DETECTOR}_{suffix}.png')
        write(ASSETS / f'models/block/{DETECTOR}_{suffix}.json', {
            'parent': 'minecraft:block/cube_all', 'textures': {'all': id_for(f'block/{DETECTOR}_{suffix}')}
        })
    write(ASSETS / f'blockstates/{DETECTOR}.json', {'variants': {
        f'power={power}': {'model': id_for(f'block/{DETECTOR}_{"on" if power else "off"}')}
        for power in range(16)
    }})
    write(ASSETS / f'models/item/{DETECTOR}.json', {'parent': id_for(f'block/{DETECTOR}_off')})
    write(DATA / f'loot_table/blocks/{DETECTOR}.json', {
        'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': id_for(DETECTOR)}],
                                             'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
    })
    pickaxe_tag = DATA.parent / 'minecraft/tags/block/mineable/pickaxe.json'
    tag = json.loads(pickaxe_tag.read_text())
    if id_for(DETECTOR) not in tag['values']:
        tag['values'].append(id_for(DETECTOR))
        write(pickaxe_tag, tag)


def portable_assets():
    filter_image = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(filter_image)
    draw.rectangle((3, 5, 12, 12), fill='#6c655b', outline='#272525')
    draw.rectangle((4, 4, 11, 12), fill='#4d504d', outline='#a69777')
    for x in (5, 7, 9):
        draw.line((x, 6, x, 10), fill='#262b2c')
    filter_image.save(ASSETS / f'textures/item/{FILTER}.png')

    cylinder = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(cylinder)
    draw.rounded_rectangle((4, 3, 11, 14), radius=2, fill='#3c7774', outline='#243437')
    draw.line((6, 5, 6, 12), fill='#71aaa7')
    draw.rectangle((5, 7, 10, 8), fill='#b2bbb4')
    draw.rectangle((6, 2, 9, 3), fill='#647979', outline='#343c3e')
    draw.line((6, 1, 9, 1), fill='#b08b56')
    cylinder.save(ASSETS / f'textures/item/{CYLINDER}.png')

    for item in (FILTER, CYLINDER):
        generated_item(item)
    generated_item(LIQUOR + '_bucket', 'neutralized_wastewater_bucket')
    write(ASSETS / f'blockstates/{LIQUOR}.json', {'variants': {'': {'model': 'minecraft:block/water'}}})


def recipes():
    def shaped(name, pattern, ingredients):
        write(DATA / f'recipe/{name}.json', {
            'type': 'minecraft:crafting_shaped', 'category': 'equipment', 'pattern': pattern,
            'key': {key: {'item': item} for key, item in ingredients.items()}, 'result': {'id': id_for(name), 'count': 1},
        })

    def shapeless(file, result, ingredients):
        write(DATA / f'recipe/{file}.json', {
            'type': 'minecraft:crafting_shapeless', 'category': 'equipment',
            'ingredients': [{'item': item} for item in ingredients], 'result': {'id': id_for(result), 'count': 1},
        })

    shaped(DETECTOR, [' I ', 'IRI', ' C '], {'I': 'create:iron_sheet', 'R': 'minecraft:comparator', 'C': 'create:andesite_casing'})
    shaped(CYLINDER, [' N ', 'I I', 'III'], {'N': 'create:copper_sheet', 'I': 'create:iron_sheet'})
    shapeless(FILTER, FILTER, ['minecraft:paper', 'minecraft:charcoal', 'minecraft:charcoal'])
    shapeless('gas_mask_regeneration', 'gas_mask', [id_for('gas_mask'), id_for(FILTER)])
    write(DATA / 'recipe/mixing/scrubber_gypsum.json', {
        'type': 'create:mixing', 'ingredients': [{'item': 'minecraft:calcite'},
                                                {'type': 'neoforge:single', 'fluid': id_for(LIQUOR), 'amount': 500}],
        'results': [{'id': id_for('gypsum'), 'count': 1}], 'processing_time': 100,
    })


def storyboard():
    scene = ponder.Structure(7, 4)
    scene.block(2, 1, 2, id_for(DETECTOR), power='12')
    for x in (3, 4):
        scene.block(x, 1, 2, 'minecraft:redstone_wire', east='side', west='side', north='none', south='none', power='9')
    scene.block(5, 1, 2, 'minecraft:redstone_lamp', lit='true')
    scene.block(2, 1, 4, id_for('gas_receiver'), axis='x', bottom='true', top='true', shape='plain')
    scene.block(5, 1, 4, id_for('gas_receiver'), axis='x', bottom='true', top='true', shape='plain')
    ponder.OUT = ASSETS / 'ponder/plant'
    scene.save(SCENE)

    path = ASSETS / 'ponder/tutorials.json'
    guides = json.loads(path.read_text())
    examples = [
        {'target': [2, 1, 2], 'key': 'step_1', 'item': id_for(DETECTOR)},
        {'target': [5, 1, 2], 'key': 'step_2', 'smoke': [1, 1, 2]},
        {'target': [2, 1, 4], 'key': 'step_3', 'use': id_for(CYLINDER)},
        {'target': [5, 1, 4], 'key': 'step_4', 'use': id_for(CYLINDER)},
        {'target': [2, 1, 2], 'key': 'step_5', 'item': id_for(FILTER)},
    ]
    entry = {'id': SCENE, 'size': 7, 'height': 4, 'components': [DETECTOR, FILTER, CYLINDER], 'steps': examples}
    for index, guide in enumerate(guides):
        if guide['id'] == SCENE:
            guides[index] = entry
            break
    else:
        guides.append(entry)
    write(path, guides)


if __name__ == '__main__':
    detector_assets()
    portable_assets()
    recipes()
    storyboard()
