#!/usr/bin/env python3
"""Recipes and item sprites for the mineral-filled resin and wash-water loop."""
from generate_water_treatment import ASSETS, NS, RECIPES, fluid, item
from generate_world_liquids import bucket_image, fluid_entries, png, write


def sprite(name, rows, palette):
    pixels = [(0, 0, 0, 0)] * 256
    for y, row in enumerate(rows, start=2):
        for x, shade in enumerate(row, start=2):
            if shade != '.':
                pixels[y * 16 + x] = (*palette[shade], 255)
    png(ASSETS / 'textures/item' / (name + '.png'), pixels)
    write(ASSETS / 'models/item' / (name + '.json'), {
        'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/' + name}})


def main():
    pellet = 'flame_retardant_pellet'
    gasket = 'heat_resistant_gasket'
    vapor = 'resin_process_vapor'
    write(RECIPES / 'pressure_reaction' / (pellet + '.json'), {
        'type': NS + 'pressure_reaction',
        'ingredients': [item('polypropylene'), item('polypropylene'), item('minecraft:quartz'),
                        item('minecraft:calcite'), fluid('minecraft:water', 250)],
        'results': [{'id': NS + pellet, 'count': 4}, {'id': NS + vapor, 'amount': 250}],
        'heat_requirement': 'heated', 'processing_time': 120})
    write(RECIPES / 'pressing' / (gasket + '.json'), {
        'type': 'create:pressing', 'ingredients': [item(pellet)],
        'results': [{'id': NS + gasket, 'count': 2}]})
    write(RECIPES.parent / 'tags/item/industrial_gaskets.json', {
        'replace': False, 'values': [NS + gasket, NS + 'rubber_gasket']})

    sprite(pellet, (
        '.....DD.....',
        '....DLHD....',
        '....DMMD....',
        '..DDDMSDDD..',
        '.DLHDDDHLMD.',
        '.DLMDDMDMSD.',
        'DDMSDHLDSD..',
        'DLHDDLMDDSD.',
        'DLMDMDMSDMD.',
        '.DMSDDDDDDSD',
        '..DD..DMSDD.',
        '.......DD...',
    ), {'D': (34, 49, 55), 'S': (52, 78, 84), 'M': (76, 109, 114),
        'L': (121, 154, 157), 'H': (171, 189, 179)})
    sprite(gasket, (
        '............',
        '....DDDD....',
        '..DDLLHMDD..',
        '.DLHLMMMSSD.',
        'DLHLDDDDMSSD',
        'DLMDB..BDMSD',
        'DMMD....DMSD',
        'DMMD....DMSD',
        'DSMDB..BDMSD',
        'DMSSDDDDMSSD',
        '.DMSSMSSSDD.',
        '..DDSSSSDD..',
        '....DDDD....',
    ), {'D': (38, 35, 32), 'S': (67, 60, 52), 'M': (94, 84, 71),
        'L': (131, 119, 98), 'H': (167, 149, 116), 'B': (180, 140, 88)})
    colour = next(entry['color'] for entry in fluid_entries() if entry['name'] == vapor)
    bucket_image(vapor + '_bucket', colour)


if __name__ == '__main__':
    main()
